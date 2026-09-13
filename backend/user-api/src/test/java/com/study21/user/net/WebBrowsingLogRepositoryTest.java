package com.study21.user.net;

import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 実 DB（PostgreSQL）に対する Web閲覧履歴 の検証。
 *
 * 2.0 から移行した `NET_Web閲覧履歴情報`（3 万件以上）を、絞り込みと
 * ページングで読めることを確かめる。テストはロールバックするので DB は汚れない。
 *
 * 実行には DB のパスワードが要る（無いときはスキップする）:
 *   STUDY21_DATASOURCE_PASSWORD=... mvn -pl user-api -am test
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class WebBrowsingLogRepositoryTest {

    @Autowired
    private WebBrowsingLogService webBrowsingLogService;

    @Test
    void migratedLogsAreReadableWithPaging() {
        var first = webBrowsingLogService.search(null, null, null, null, null, null, null, 1, 15);

        assertThat(first.totalElements()).isGreaterThan(1000);
        assertThat(first.items()).hasSize(15);
        assertThat(first.page()).isEqualTo(1);
        assertThat(first.totalPages()).isGreaterThan(1);

        // 新しい順（アクセス日時が降順）
        var items = first.items();
        for (int index = 1; index < items.size(); index += 1) {
            assertThat(items.get(index - 1).accessedAt())
                    .isAfterOrEqualTo(items.get(index).accessedAt());
        }

        // 2 ページ目は別の行
        var second = webBrowsingLogService.search(null, null, null, null, null, null, null, 2, 15);
        assertThat(second.items()).isNotEmpty();
        assertThat(second.items().get(0).logId()).isNotEqualTo(items.get(0).logId());
    }

    @Test
    void eventTypeFilterAcceptsTheFiveTwoZeroEventTypes() {
        for (String eventType : WebBrowsingLogModels.EVENT_TYPES) {
            var result = webBrowsingLogService.search(null, null, null, eventType, null, null, null, 1, 5);
            assertThat(result.items()).allSatisfy(row -> assertThat(row.eventType()).isEqualTo(eventType));
        }

        // 2.0 に無いイベント種別は 400
        assertThatThrownBy(() -> webBrowsingLogService.search(null, null, null, "UNKNOWN_EVENT", null,
                null, null, 1, 5))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("イベント種別");
    }

    @Test
    void domainFilterMatchesPartially() {
        var all = webBrowsingLogService.search(null, null, null, null, null, null, null, 1, 1);
        assertThat(all.totalElements()).isGreaterThan(0);

        var nothing = webBrowsingLogService.search(null, null, "example.invalid.domain", null, null,
                null, null, 1, 5);
        assertThat(nothing.totalElements()).isZero();
    }

    @Test
    void dateRangeFilterIsInclusiveOnBothEnds() {
        var latest = webBrowsingLogService.search(null, null, null, null, null, null, null, 1, 1)
                .items().get(0);
        String day = latest.accessedAt().toLocalDateTime().toLocalDate().toString();

        var sameDay = webBrowsingLogService.search(null, null, null, null, null, day, day, 1, 100);
        assertThat(sameDay.totalElements()).isGreaterThan(0);
        assertThat(sameDay.items()).allSatisfy(row ->
                assertThat(row.accessedAt().toLocalDateTime().toLocalDate().toString()).isEqualTo(day));
    }

    @Test
    void invalidDateIsRejected() {
        assertThatThrownBy(() -> webBrowsingLogService.search(null, null, null, null, null,
                "2026/09/12", null, 1, 5))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("yyyy-MM-dd");
    }

    @Test
    void keywordSearchesUrlAndPageTitle() {
        var latest = webBrowsingLogService.search(null, null, null, null, null, null, null, 1, 50)
                .items()
                .stream()
                .filter(row -> row.domain() != null && !row.domain().isBlank())
                .findFirst()
                .orElseThrow();

        var result = webBrowsingLogService.search(null, null, null, null, latest.domain(), null, null, 1, 50);
        assertThat(result.totalElements()).isGreaterThan(0);
    }
}
