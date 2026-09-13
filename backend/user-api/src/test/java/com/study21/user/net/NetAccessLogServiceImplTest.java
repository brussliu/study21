package com.study21.user.net;

import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * サイトアクセス履歴（インターネット利用履歴）の照会ルール。
 *
 * 絞り込みの正規化（空文字は無視・結果コードは大文字化）、ページングの丸め、
 * 応答状態コードからの結果コード（ALLOW / DENY）の決定を固定する。
 */
class NetAccessLogServiceImplTest {

    private NetAccessLogMapper mapper;
    private NetAccessLogServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(NetAccessLogMapper.class);
        service = new NetAccessLogServiceImpl(mapper);
    }

    private NetAccessLogEntity entity(Integer statusCode) {
        NetAccessLogEntity e = new NetAccessLogEntity();
        e.setLogId(1L);
        e.setReceivedAt(new Timestamp(System.currentTimeMillis()));
        e.setClientIp("192.168.0.92");
        e.setTerminalName("勉強用PC");
        e.setHttpMethod("CONNECT");
        e.setHost("spclient.wg.spotify.com");
        e.setUrl("spclient.wg.spotify.com:443");
        e.setStatusCode(statusCode);
        e.setErrorDetail(statusCode == null ? null : "停止モードのためアクセス不可");
        return e;
    }

    @Test
    void searchNormalizesFiltersAndPaging() {
        when(mapper.count("spotify", "勉強用PC", "DENY")).thenReturn(40L);
        when(mapper.search(eq("spotify"), eq("勉強用PC"), eq("DENY"), anyInt(), anyInt()))
                .thenReturn(List.of(entity(403)));

        var result = service.search("  spotify ", " 勉強用PC ", "deny", 2, 20);

        assertThat(result.totalElements()).isEqualTo(40L);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalPages()).isEqualTo(2);
        // 2 ページ目は offset 20
        verify(mapper).search("spotify", "勉強用PC", "DENY", 20, 20);
    }

    @Test
    void blankFiltersBecomeNull() {
        when(mapper.count(isNull(), isNull(), isNull())).thenReturn(0L);
        when(mapper.search(isNull(), isNull(), isNull(), anyInt(), anyInt())).thenReturn(List.of());

        service.search("", "   ", null, 1, 20);

        verify(mapper).count(null, null, null);
    }

    @Test
    void sizeAndPageAreClamped() {
        when(mapper.count(isNull(), isNull(), isNull())).thenReturn(0L);
        when(mapper.search(isNull(), isNull(), isNull(), anyInt(), anyInt())).thenReturn(List.of());

        var tooLarge = service.search(null, null, null, 0, 500);
        assertThat(tooLarge.size()).isEqualTo(100);
        assertThat(tooLarge.page()).isEqualTo(1);

        var zeroSize = service.search(null, null, null, 1, 0);
        assertThat(zeroSize.size()).isEqualTo(20);
    }

    @Test
    void unknownResultIsRejected() {
        assertThatThrownBy(() -> service.search(null, null, "UNKNOWN", 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ALLOW / DENY");
    }

    @Test
    void resultCodeComesFromStatusCode() {
        when(mapper.count(isNull(), isNull(), isNull())).thenReturn(2L);
        when(mapper.search(isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(List.of(entity(null), entity(403)));

        var result = service.search(null, null, null, 1, 20);

        // 応答状態コードが無い = 許可、入っている = 拒否
        assertThat(result.items().get(0).result()).isEqualTo("ALLOW");
        assertThat(result.items().get(0).errorDetail()).isNull();
        assertThat(result.items().get(1).result()).isEqualTo("DENY");
        assertThat(result.items().get(1).errorDetail()).isEqualTo("停止モードのためアクセス不可");
    }


    /* ---------- 最近3日 上網状況（時間帯別） ---------- */

    private NetAccessLogModels.HourlyUsageBucket bucket(LocalDateTime at, long count) {
        return new NetAccessLogModels.HourlyUsageBucket(Timestamp.valueOf(at), count);
    }

    @Test
    void hourlyUsageBuildsThreeDaysByHour() {
        LocalDate today = LocalDate.now();
        when(mapper.hourlySummary(isNull(), isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        bucket(today.atTime(9, 0), 10),
                        bucket(today.atTime(9, 30), 5),          // 同じ時間帯の行が複数来ても足し込む
                        bucket(today.minusDays(1).atTime(20, 0), 7),
                        bucket(today.minusDays(2).atTime(0, 0), 3),
                        bucket(today.minusDays(5).atTime(12, 0), 999)   // 3 日の外は無視
                ));

        NetAccessLogModels.HourlyUsageSummary summary = service.hourlyUsage(null, null);

        // 新しい順（[0] が今日）
        assertThat(summary.days()).containsExactly(
                today.toString(), today.minusDays(1).toString(), today.minusDays(2).toString());
        assertThat(summary.buckets()).hasSize(3);
        assertThat(summary.buckets().get(0)).hasSize(24);
        assertThat(summary.buckets().get(0).get(9)).isEqualTo(15L);
        assertThat(summary.buckets().get(1).get(20)).isEqualTo(7L);
        assertThat(summary.buckets().get(2).get(0)).isEqualTo(3L);
        // 3 日の外は数えない
        assertThat(summary.totalCount()).isEqualTo(15 + 7 + 3);
        assertThat(summary.buckets().get(0).stream().mapToLong(Long::longValue).sum()
                + summary.buckets().get(1).stream().mapToLong(Long::longValue).sum()
                + summary.buckets().get(2).stream().mapToLong(Long::longValue).sum())
                .isEqualTo(summary.totalCount());
        assertThat(summary.terminalName()).isNull();
        assertThat(summary.kind()).isNull();
    }

    @Test
    void hourlyUsageNormalizesFiltersAndRejectsUnknownKind() {
        when(mapper.hourlySummary(org.mockito.ArgumentMatchers.eq("勉強用PC"),
                org.mockito.ArgumentMatchers.eq("BREAK"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        NetAccessLogModels.HourlyUsageSummary summary = service.hourlyUsage("  勉強用PC ", " break ");

        assertThat(summary.terminalName()).isEqualTo("勉強用PC");
        assertThat(summary.kind()).isEqualTo("BREAK");
        assertThat(summary.totalCount()).isZero();

        assertThatThrownBy(() -> service.hourlyUsage(null, "STUDY2"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("区分");
    }
}
