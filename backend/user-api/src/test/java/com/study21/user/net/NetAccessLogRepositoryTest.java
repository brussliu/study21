package com.study21.user.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 実 DB（PostgreSQL）に対する サイトアクセス履歴 の検証。
 *
 * 2.0 から移行した `NET_プロキシ通信履歴情報`（13 万件以上）を、絞り込みと
 * ページングで読めることを確かめる。テストはロールバックするので DB は汚れない。
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class NetAccessLogRepositoryTest {

    @Autowired
    private NetAccessLogService netAccessLogService;

    @Test
    void migratedAccessLogsAreReadableWithPaging() {
        var first = netAccessLogService.search(null, null, null, 1, 15);
        assertThat(first.totalElements()).isGreaterThan(1000);
        assertThat(first.items()).hasSize(15);
        assertThat(first.page()).isEqualTo(1);
        assertThat(first.totalPages()).isGreaterThan(1);

        // 新しい順（受付日時が降順）
        var firstPage = first.items();
        for (int index = 1; index < firstPage.size(); index += 1) {
            assertThat(firstPage.get(index - 1).receivedAt())
                    .isAfterOrEqualTo(firstPage.get(index).receivedAt());
        }

        // 2 ページ目は別の行
        var second = netAccessLogService.search(null, null, null, 2, 15);
        assertThat(second.items()).isNotEmpty();
        assertThat(second.items().get(0).logId()).isNotEqualTo(firstPage.get(0).logId());
    }

    @Test
    void resultFilterSeparatesAllowedAndDenied() {
        var denied = netAccessLogService.search(null, null, "DENY", 1, 10);
        assertThat(denied.totalElements()).isGreaterThan(0);
        assertThat(denied.items()).allSatisfy(row -> {
            assertThat(row.result()).isEqualTo("DENY");
            assertThat(row.statusCode()).isNotNull();
            assertThat(row.errorDetail()).isNotBlank();
        });

        var allowed = netAccessLogService.search(null, null, "ALLOW", 1, 10);
        assertThat(allowed.items()).allSatisfy(row -> {
            assertThat(row.result()).isEqualTo("ALLOW");
            assertThat(row.statusCode()).isNull();
        });
    }

    @Test
    void hostFilterMatchesPartially() {
        var spotify = netAccessLogService.search("spotify", null, null, 1, 10);
        assertThat(spotify.totalElements()).isGreaterThan(0);
        assertThat(spotify.items()).allSatisfy(row ->
                assertThat(row.host() == null ? "" : row.host().toLowerCase()).contains("spotify"));

        var nothing = netAccessLogService.search("example.invalid.host", null, null, 1, 10);
        assertThat(nothing.totalElements()).isZero();
    }

    @Test
    void terminalNameFilterUsesTerminalControlTable() {
        // 端末名称は NET_端末コントロール情報 から引く（該当が無ければ 0 件）
        var unknown = netAccessLogService.search(null, "存在しない端末名", null, 1, 10);
        assertThat(unknown.totalElements()).isZero();
    }


    /* ---------- 最近3日 上網状況（時間帯別） ---------- */

    @Test
    void hourlyUsageSummaryMatchesTheRawCounts() {
        var summary = netAccessLogService.hourlyUsage(null, null);

        // 今日・昨日・一昨日（新しい順）で 24 時間ずつ
        LocalDate today = LocalDate.now();
        assertThat(summary.days()).containsExactly(
                today.toString(), today.minusDays(1).toString(), today.minusDays(2).toString());
        assertThat(summary.buckets()).hasSize(3);
        assertThat(summary.buckets()).allSatisfy(hours -> assertThat(hours).hasSize(24));

        // buckets の合計と totalCount が一致する
        long sum = summary.buckets().stream()
                .flatMap(List::stream)
                .mapToLong(Long::longValue)
                .sum();
        assertThat(sum).isEqualTo(summary.totalCount());
        assertThat(summary.totalCount()).isGreaterThan(0L);

        // 「通過」した通信だけを数えている（拒否は含めない）
        var allowed = netAccessLogService.search(null, null, "ALLOW", 1, 1);
        assertThat(summary.totalCount()).isLessThanOrEqualTo(allowed.totalElements());
    }

    @Test
    void hourlyUsageSummaryFiltersByTerminalNameAndKind() {
        var all = netAccessLogService.hourlyUsage(null, null);
        var terminal = netAccessLogService.hourlyUsage("勉強用PC", null);
        var breakKind = netAccessLogService.hourlyUsage(null, "BREAK");

        // 端末で絞ると全体以下になる（部分集合なので、時間帯ごとの件数もそれぞれ全体以下）
        assertThat(terminal.totalCount()).isLessThanOrEqualTo(all.totalCount());
        assertThat(terminal.terminalName()).isEqualTo("勉強用PC");
        for (int day = 0; day < all.buckets().size(); day += 1) {
            for (int hour = 0; hour < all.buckets().get(day).size(); hour += 1) {
                assertThat(terminal.buckets().get(day).get(hour))
                        .isLessThanOrEqualTo(all.buckets().get(day).get(hour));
            }
        }

        // 区分（休憩サイト）でも絞れて、区分コードが返る
        assertThat(breakKind.kind()).isEqualTo("BREAK");
        assertThat(breakKind.totalCount()).isLessThanOrEqualTo(all.totalCount());

        // 存在しない端末名は 0 件
        assertThat(netAccessLogService.hourlyUsage("E2E 存在しない端末", null).totalCount()).isZero();
    }
}
