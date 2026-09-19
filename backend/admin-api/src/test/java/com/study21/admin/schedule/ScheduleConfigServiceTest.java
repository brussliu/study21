package com.study21.admin.schedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * スケジュール設定のメモリキャッシュ（{@link ScheduleConfigService}）の振る舞い。
 *
 * <p>利用者の指示のうち、ここで固定するもの:</p>
 * <ol>
 *   <li>読み込み後はメモリだけで判断する（スケジュール検査で設定テーブルを引かない）</li>
 *   <li>保存後の反映で再起動なしに変わる</li>
 *   <li>DB を読めないときは**前の有効な設定を残す**（消さない）。画面には「反映待ち」として見せる</li>
 *   <li>托底の失敗は退避（30/60/120/240/300 秒）で再試行し、その間は DB を引かない</li>
 *   <li>メモリに無いときだけ DB 托底。多スレッド同時でも読み込みは 1 回（ロック内で再確認）</li>
 *   <li>設定が無い・不正なタスクは自動実行しない（隠れた既定値を使わない）</li>
 *   <li>並行に更新しても、古い読み込み結果が新しい設定を上書きしない</li>
 * </ol>
 */
class ScheduleConfigServiceTest {

    private static final ZoneId ZONE = ScheduleConfigService.ZONE;

    private ScheduleRuleCatalog catalog;
    private FakeLoader loader;
    private MutableClock clock;
    private ScheduleConfigService service;

    /** テスト用の時計（進められる）。 */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-19T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    /** DB 読み込みの代役（呼ばれた回数と、返す値・投げる例外を制御する）。 */
    private static final class FakeLoader implements ScheduleConfigLoader {
        static final class Box {
            Map<String, String> settings = new LinkedHashMap<>();
            Map<String, Boolean> enabled = new LinkedHashMap<>();
        }

        final Box box = new Box();
        final AtomicInteger loads = new AtomicInteger();
        RuntimeException failure;
        CountDownLatch gate;
        /** 読み込みごとに次の候補を返す（並行保存のテストで「最後に読んだ値」を決定的にする）。 */
        volatile int[] intervalChoices;
        volatile String lastIntervalRead;

        @Override
        public ScheduleSourceData load(List<String> settingKeys, List<String> taskCodes) {
            loads.incrementAndGet();
            if (gate != null) {
                try {
                    gate.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException cause) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failure != null) {
                throw failure;
            }
            Map<String, String> settings = new LinkedHashMap<>(box.settings);
            int[] choices = intervalChoices;
            if (choices != null) {
                String value = String.valueOf(choices[(loads.get() - 1) % choices.length]);
                settings.put("STUDY_MONITOR_L02_INTERVAL_MINUTES", value);
                lastIntervalRead = value;
            }
            return new ScheduleSourceData(settings, box.enabled);
        }
    }

    @BeforeEach
    void setUp() {
        catalog = new ScheduleRuleCatalog();
        loader = new FakeLoader();
        clock = new MutableClock();
        // 既定: 4 タスクとも設定があり、batR03 / batR04 だけ有効
        loader.box.settings.put("NET_CONTROL_START_TIME", "06:30");
        loader.box.settings.put("NET_CONTROL_END_TIME", "23:30");
        loader.box.settings.put("STUDY_MONITOR_L02_INTERVAL_MINUTES", "5");
        loader.box.settings.put("STUDY_MONITOR_L02_OFFSET_MINUTES", "1");
        loader.box.settings.put("STUDY_MONITOR_L03_INTERVAL_MINUTES", "5");
        loader.box.settings.put("STUDY_MONITOR_L03_OFFSET_MINUTES", "0");
        loader.box.enabled.put("batR03", true);
        loader.box.enabled.put("batR04", true);
        loader.box.enabled.put("batL02", false);
        loader.box.enabled.put("batL03", false);
        service = new ScheduleConfigService(catalog, loader, clock);
    }

    @Test
    @DisplayName("起動時に読み込み、以後の検査はメモリだけで判断する（設定テーブルを引かない）")
    void loadsOnceAndThenServesFromMemory() {
        service.loadOnStartup();
        assertThat(loader.loads).hasValue(1);
        assertThat(service.snapshot().version()).isEqualTo(1);

        for (int i = 0; i < 100; i++) {
            assertThat(service.ensureTaskConfig("batR03").statusOf("batR03")).isEqualTo(TaskConfigStatus.LOADED);
            service.snapshot();
            service.report();
        }
        assertThat(loader.loads).hasValue(1);
    }

    @Test
    @DisplayName("保存後の反映で再起動なしに新しい間隔になる")
    void refreshPicksUpNewValues() {
        service.loadOnStartup();
        assertThat(service.snapshot().taskOf("batL02").orElseThrow().intervalMinutes()).isEqualTo(5);

        loader.box.settings.put("STUDY_MONITOR_L02_INTERVAL_MINUTES", "15");
        loader.box.settings.put("STUDY_MONITOR_L02_OFFSET_MINUTES", "2");
        ScheduleConfigService.RefreshResult result = service.refresh("設定保存後");

        assertThat(result.published()).isTrue();
        TaskSchedule schedule = service.snapshot().taskOf("batL02").orElseThrow();
        assertThat(schedule.intervalMinutes()).isEqualTo(15);
        assertThat(schedule.offsetMinutes()).isEqualTo(2);
        assertThat(schedule.pointsOfDay(java.time.LocalDate.now()))
                .containsExactly(LocalTime.of(0, 2), LocalTime.of(0, 17), LocalTime.of(0, 32), LocalTime.of(0, 47));
        assertThat(service.report().activeVersion()).isEqualTo(2);
        assertThat(service.report().pendingRefresh()).isFalse();
    }

    @Test
    @DisplayName("DB を読めないときは前の設定を残し、反映待ちとして見せる（無限に即時再試行しない）")
    void keepsPreviousSnapshotWhenRefreshFails() {
        service.loadOnStartup();
        long version = service.snapshot().version();
        loader.failure = new ScheduleConfigLoader.ScheduleConfigLoadException("接続できません");

        ScheduleConfigService.RefreshResult result = service.refresh("設定保存後");

        assertThat(result.published()).isFalse();
        assertThat(result.error()).contains("接続できません");
        // 前の設定はそのまま（消さない）
        assertThat(service.snapshot().version()).isEqualTo(version);
        assertThat(service.snapshot().taskOf("batR03").orElseThrow().dailyTime()).isEqualTo(LocalTime.of(23, 30));

        ScheduleConfigReport report = service.report();
        assertThat(report.pendingRefresh()).isTrue();
        assertThat(report.pendingMessage()).contains("保存済み・実行設定への反映待ち");
        assertThat(report.lastRefreshError()).contains("接続できません");
        assertThat(report.nextRetryAt()).isNotNull();
        assertThat(service.remainingBackoff()).isPresent();

        // 退避の間は DB を引かない（托底も自動再試行もしない）
        int loadsAfterFailure = loader.loads.get();
        clock.advance(Duration.ofSeconds(10));
        assertThat(service.retryPendingIfDue()).isFalse();
        service.ensureTaskConfig("batL03");
        assertThat(loader.loads).hasValue(loadsAfterFailure);

        // 退避が明けたら自動で再試行し、成功したら「反映待ち」が消える
        clock.advance(Duration.ofSeconds(25));
        loader.failure = null;
        assertThat(service.retryPendingIfDue()).isTrue();
        assertThat(service.report().pendingRefresh()).isFalse();
        assertThat(service.report().lastRefreshError()).isNull();
    }

    @Test
    @DisplayName("退避は 30→60→120→240→300 秒（上限）。失敗が続いてもログは抑止する")
    void backoffGrowsAndIsCapped() {
        loader.failure = new ScheduleConfigLoader.ScheduleConfigLoadException("接続できません");
        Duration[] expected = {Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(120),
                Duration.ofSeconds(240), Duration.ofSeconds(300), Duration.ofSeconds(300)};
        for (int attempt = 0; attempt < expected.length; attempt++) {
            Duration wait = expected[attempt];
            service.refresh("失敗");
            assertThat(loader.loads.get()).as("attempt=%s", attempt + 1).isEqualTo(attempt + 1);
            assertThat(service.remainingBackoff()).as("attempt=%s", attempt + 1).hasValueSatisfying(remaining ->
                    assertThat(remaining).isBetween(wait.minusSeconds(1), wait));
            clock.advance(wait);
        }
        assertThat(service.report().suppressedCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("メモリに無いタスクだけ DB 托底し、多スレッド同時でも読み込みは 1 回")
    void fallbackLoadsOnceForConcurrentMisses() throws Exception {
        loader.gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<TaskConfigStatus>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> service.ensureTaskConfig("batL02").statusOf("batL02")));
            }
            // ロックを取った 1 スレッドだけが DB を読む（他はロック内の再確認で待たされる）
            Thread.sleep(100);
            loader.gate.countDown();
            for (Future<TaskConfigStatus> future : futures) {
                assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(TaskConfigStatus.LOADED);
            }
            assertThat(loader.loads).hasValue(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("設定が無いタスクの托底は退避する（毎回 DB を引きに行かない）")
    void fallbackForMissingConfigBacksOff() {
        loader.box.settings.remove("NET_CONTROL_END_TIME");   // batR03 の設定だけ無い
        service.loadOnStartup();
        int loadsAfterStartup = loader.loads.get();
        assertThat(service.snapshot().statusOf("batR03")).isEqualTo(TaskConfigStatus.MISSING);

        // 1 回目の托底はすぐ試す
        assertThat(service.ensureTaskConfig("batR03").statusOf("batR03")).isEqualTo(TaskConfigStatus.MISSING);
        assertThat(loader.loads).hasValue(loadsAfterStartup + 1);
        assertThat(service.remainingFallbackBackoff("batR03")).isPresent();

        // 2 回目以降は退避中なので DB を引かない（30 秒ごとの検査で毎回引かない）
        assertThat(service.ensureTaskConfig("batR03").statusOf("batR03")).isEqualTo(TaskConfigStatus.MISSING);
        assertThat(loader.loads).hasValue(loadsAfterStartup + 1);

        // 設定を保存すれば退避は消えて、すぐ反映される
        loader.box.settings.put("NET_CONTROL_END_TIME", "23:30");
        service.refresh("設定保存");
        assertThat(service.snapshot().statusOf("batR03")).isEqualTo(TaskConfigStatus.LOADED);
        assertThat(service.remainingFallbackBackoff("batR03")).isEmpty();
        assertThat(service.snapshot().taskOf("batR03").orElseThrow().dailyTime())
                .isEqualTo(java.time.LocalTime.of(23, 30));
    }

    @Test
    @DisplayName("設定が無い・不正なタスクは自動実行しない（隠れた既定値を使わない）")
    void missingOrInvalidConfigIsNotRunnable() {
        loader.box.settings.remove("NET_CONTROL_END_TIME");
        loader.box.settings.put("STUDY_MONITOR_L02_INTERVAL_MINUTES", "7");   // 候補に無い
        service.loadOnStartup();

        ScheduleConfigSnapshot snapshot = service.snapshot();
        assertThat(snapshot.statusOf("batR03")).isEqualTo(TaskConfigStatus.MISSING);
        assertThat(snapshot.statusOf("batL02")).isEqualTo(TaskConfigStatus.INVALID);
        assertThat(snapshot.taskOf("batR03")).isEmpty();
        assertThat(snapshot.taskOf("batL02")).isEmpty();
        assertThat(snapshot.usableTaskCount()).isEqualTo(2);   // batR04 と batL03

        ScheduleConfigReport report = service.report();
        assertThat(report.taskStatus("batR03").statusLabel()).isEqualTo("未設定");
        assertThat(report.taskStatus("batR04").nextRunAt()).isNotNull();
        assertThat(report.taskStatus("batR03").nextRunAt()).isNull();
    }

    @Test
    @DisplayName("有効／無効は BAT_バッチコントロール情報 の値がそのまま入る（2 つ目のスイッチを作らない）")
    void enabledComesFromControlTable() {
        service.loadOnStartup();
        assertThat(service.snapshot().taskOf("batR03").orElseThrow().enabled()).isTrue();
        assertThat(service.snapshot().taskOf("batL02").orElseThrow().enabled()).isFalse();
    }

    @Test
    @DisplayName("並行に更新しても、古い読み込み結果が新しい設定を上書きしない")
    void concurrentRefreshesDoNotOverwriteWithOlderData() throws Exception {
        service.loadOnStartup();
        int[] choices = {5, 10, 15, 30, 60};
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            loader.intervalChoices = choices;
            List<Future<ScheduleConfigService.RefreshResult>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 20; i++) {
                futures.add(pool.submit(() -> service.refresh("並行保存")));
            }
            int published = 0;
            for (Future<ScheduleConfigService.RefreshResult> future : futures) {
                if (future.get(5, TimeUnit.SECONDS).published()) {
                    published++;
                }
            }
            // 古い要求（先に番号を取ったが後にロックを取った）は破棄される＝読み込みもしない。
            // つまり「発行できた回数＝読み込み回数-1」で、破棄された要求は DB を引いていない
            assertThat(published).isBetween(1, 20);
            assertThat(loader.loads).hasValue(published + 1);
            // 直列化しているので、最後に読んだ値（＝いちばん新しい設定）が残る（古い結果で上書きしない）
            assertThat(service.snapshot().taskOf("batL02").orElseThrow().intervalMinutes())
                    .isEqualTo(Integer.parseInt(loader.lastIntervalRead));
            assertThat(service.snapshot().version()).isEqualTo(1 + published);
        } finally {
            pool.shutdownNow();
        }
    }
}
