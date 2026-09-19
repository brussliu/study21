package com.study21.admin.schedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
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
            /** 設定の適用時刻（DB に保存されている値の代役）。 */
            Map<String, LocalDateTime> effectiveFrom = new LinkedHashMap<>();
            /** 計画バージョン（設定値の保存と同じトランザクションで進む版の代役）。 */
            Map<String, Long> planVersions = new LinkedHashMap<>();
        }

        final Box box = new Box();
        final AtomicInteger loads = new AtomicInteger();
        RuntimeException failure;
        CountDownLatch gate;
        /** 読み込みごとに次の候補を返す（並行保存のテストで「最後に読んだ値」を決定的にする）。 */
        volatile int[] intervalChoices;
        volatile String lastIntervalRead;

        /**
         * **設定保存トランザクション**が一緒に書く値の代役（適用時刻＋計画バージョン）。
         *
         * <p>キャッシュの読み込み（{@code load}）は何も書かない。書くのは保存の側だけ。</p>
         */
        void recordTimingChange(String taskCode, LocalDateTime effectiveFrom) {
            box.effectiveFrom.put(taskCode, effectiveFrom);
            box.planVersions.merge(taskCode, 1L, Long::sum);
        }

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
            return new ScheduleSourceData(settings, box.enabled, new LinkedHashMap<>(box.effectiveFrom),
                    new LinkedHashMap<>(box.planVersions));
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

        // 起動時の読み込みでも「設定が無い」ことが分かっているので、すぐには読み直さない
        assertThat(service.remainingFallbackBackoff("batR03")).isPresent();
        service.ensureUsableConfig();
        assertThat(loader.loads).hasValue(loadsAfterStartup);

        // 退避が明けたら 1 回だけ試す
        clock.advance(Duration.ofSeconds(31));
        service.ensureUsableConfig();
        assertThat(loader.loads).hasValue(loadsAfterStartup + 1);

        // そのあとは再び退避中なので DB を引かない（30 秒ごとの検査で毎回引かない）
        service.ensureUsableConfig();
        assertThat(loader.loads).hasValue(loadsAfterStartup + 1);
        assertThat(service.remainingFallbackBackoff("batR03")).isPresent();

        // 設定を保存すれば退避は消えて、すぐ反映される
        loader.box.settings.put("NET_CONTROL_END_TIME", "23:30");
        service.refresh("設定保存");
        assertThat(service.snapshot().statusOf("batR03")).isEqualTo(TaskConfigStatus.LOADED);
        assertThat(service.remainingFallbackBackoff("batR03")).isEmpty();
        assertThat(service.snapshot().taskOf("batR03").orElseThrow().dailyTime())
                .isEqualTo(java.time.LocalTime.of(23, 30));
    }

    @Test
    @DisplayName("設定が無いままなら退避は 30→60→120→240→300 秒と伸びる（成功しても消えない）")
    void missingConfigBackoffGrowsAndIsKeptAfterSuccessfulLoads() {
        loader.box.settings.remove("NET_CONTROL_END_TIME");
        service.loadOnStartup();   // 1 回目（起動時）
        Duration[] expected = {Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(120),
                Duration.ofSeconds(240), Duration.ofSeconds(300), Duration.ofSeconds(300)};
        for (int attempt = 0; attempt < expected.length; attempt++) {
            Duration wait = expected[attempt];
            assertThat(service.remainingFallbackBackoff("batR03"))
                    .as("attempt=%s", attempt + 1)
                    .hasValueSatisfying(remaining ->
                            assertThat(remaining).isBetween(wait.minusSeconds(1), wait));
            clock.advance(wait);
            int before = loader.loads.get();
            service.ensureUsableConfig();   // DB は読めるが設定が無い → 退避を進める
            assertThat(loader.loads.get()).as("attempt=%s", attempt + 1).isEqualTo(before + 1);
        }
        // 5 回を超えたら上限 300 秒で頭打ち（回数は増え続ける）
        assertThat(service.report().taskStatus("batR03").fallbackFailures()).isGreaterThan(5);
        assertThat(service.remainingFallbackBackoff("batR03")).hasValueSatisfying(remaining ->
                assertThat(remaining).isBetween(Duration.ofSeconds(299), Duration.ofSeconds(300)));
        // 画面には「動かない理由と次に確認する時刻」を出す
        assertThat(service.report().taskStatus("batR03").fallbackMessage())
                .contains("設定が無い").contains("回連続").contains("再確認");
        assertThat(service.report().configMissingMessage()).contains("batR03");
    }

    @Test
    @DisplayName("設定が無いタスクが複数あっても、托底の読み込みは 1 回にまとめる")
    void fallbackLoadsOnceForSeveralMissingTasks() {
        loader.box.settings.remove("NET_CONTROL_END_TIME");            // batR03
        loader.box.settings.remove("STUDY_MONITOR_L02_INTERVAL_MINUTES");   // batL02
        service.loadOnStartup();
        int afterStartup = loader.loads.get();
        assertThat(service.snapshot().statusOf("batR03")).isEqualTo(TaskConfigStatus.MISSING);
        assertThat(service.snapshot().statusOf("batL02")).isEqualTo(TaskConfigStatus.MISSING);

        clock.advance(Duration.ofSeconds(31));
        service.ensureUsableConfig();   // 1 回の読み込みで**全タスクの状態**が更新される
        assertThat(loader.loads).hasValue(afterStartup + 1);
        assertThat(service.snapshot().statusOf("batR04")).isEqualTo(TaskConfigStatus.LOADED);
        // 両方とも退避が進む（次は 60 秒）
        assertThat(service.remainingFallbackBackoff("batR03")).hasValueSatisfying(remaining ->
                assertThat(remaining).isBetween(Duration.ofSeconds(59), Duration.ofSeconds(60)));
        assertThat(service.remainingFallbackBackoff("batL02")).hasValueSatisfying(remaining ->
                assertThat(remaining).isBetween(Duration.ofSeconds(59), Duration.ofSeconds(60)));

        // 片方だけ直ったら、直った方の退避だけ消える
        loader.box.settings.put("NET_CONTROL_END_TIME", "23:30");
        service.refresh("設定保存");
        assertThat(service.snapshot().statusOf("batR03")).isEqualTo(TaskConfigStatus.LOADED);
        assertThat(service.remainingFallbackBackoff("batR03")).isEmpty();
        assertThat(service.remainingFallbackBackoff("batL02")).isPresent();
    }

    @Test
    @DisplayName("DB を読めないときの退避と、設定が無いときの退避は別物（片方で他方を消さない）")
    void readFailureBackoffIsSeparateFromMissingConfig() {
        loader.box.settings.remove("NET_CONTROL_END_TIME");
        service.loadOnStartup();   // batR03 が「設定なし」で記録される
        assertThat(service.report().taskStatus("batR03").fallbackFailures()).isEqualTo(1);

        // DB が読めない → 全体の退避（30 秒）。タスクごとの回数は動かない
        loader.failure = new ScheduleConfigLoader.ScheduleConfigLoadException("接続できません");
        service.refresh("失敗");
        assertThat(service.report().taskStatus("batR03").fallbackFailures()).isEqualTo(1);
        assertThat(service.remainingBackoff()).isPresent();
        assertThat(service.remainingFallbackBackoff("batR03")).isPresent();

        // DB が読めるようになったら、全体の退避だけ消える（設定が無いタスクの回数は残る）
        loader.failure = null;
        clock.advance(Duration.ofSeconds(31));
        service.refresh("復帰");
        assertThat(service.remainingBackoff()).isEmpty();
        assertThat(service.report().taskStatus("batR03").fallbackFailures()).isEqualTo(2);
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

    // ------------------------------------------------------------------ 設定変更と適用時刻

    @Test
    @DisplayName("利用者が時刻を変えたら、その時刻より前の計画実行点は実行しない（22:00 に 23:30→21:00）")
    void changingTheTimeOnlyAffectsFuturePoints() {
        service.loadOnStartup();
        // 起動時（＝まだ変更していない設定）は制限なし
        assertThat(service.snapshot().taskOf("batR03").orElseThrow().effectiveFrom()).isNull();

        // 22:00 に「停止 23:30 → 21:00」へ変更した。適用時刻と計画バージョンは
        // **設定値の保存と同じトランザクション**で書かれている（ここではその代役を置く）
        clock.advance(Duration.ofHours(22));
        loader.box.settings.put("NET_CONTROL_END_TIME", "21:00");
        loader.recordTimingChange("batR03", LocalDateTime.of(2026, 9, 19, 22, 0));
        assertThat(service.refresh("設定保存").published()).isTrue();

        TaskSchedule schedule = service.snapshot().taskOf("batR03").orElseThrow();
        assertThat(schedule.dailyTime()).isEqualTo(LocalTime.of(21, 0));
        assertThat(schedule.effectiveFrom()).isNotNull();
        // 今日の 21:00 は「設定を変えた時刻（22:00）」より前 → 実行しない（すぐ止まらない）
        assertThat(schedule.previousRunnablePointAtOrBefore(LocalDateTime.of(2026, 9, 19, 22, 0)))
                .isEmpty();
        // 次に実行するのは明日の 21:00（画面の「次回実行時刻」も同じ）
        assertThat(schedule.nextRunnablePointAfter(LocalDateTime.of(2026, 9, 19, 22, 0)))
                .isEqualTo(LocalDateTime.of(2026, 9, 20, 21, 0));
        assertThat(service.report().taskStatus("batR03").nextRunLabel()).isEqualTo("2026-09-20 21:00");
    }

    @Test
    @DisplayName("キャッシュの読み込みは**書かない**（適用時刻は保存トランザクションだけが書く）")
    void loadingTheConfigNeverWritesTheEffectiveTime() {
        service.loadOnStartup();
        clock.advance(Duration.ofHours(22));
        // 保存経路を通さずに設定値だけが変わった（＝アプリ以外の経路で書き換えられた）
        loader.box.settings.put("NET_CONTROL_END_TIME", "21:00");
        assertThat(service.refresh("外部変更").published()).isTrue();

        // DB には書いていない（読み込みで補って書かない）。メモリだけ「いまから」になる
        assertThat(loader.box.effectiveFrom).doesNotContainKey("batR03");
        assertThat(loader.box.planVersions).doesNotContainKey("batR03");
        TaskSchedule schedule = service.snapshot().taskOf("batR03").orElseThrow();
        // 時計を 22 時間進めた時点（2026-09-20 07:00 JST）が「いまから」になる
        LocalDateTime nowLocal = LocalDateTime.of(2026, 9, 20, 7, 0);
        assertThat(schedule.effectiveFrom()).isEqualTo(nowLocal);
        // 直近の 21:00（前日）は「変わったと気づいた時刻」より前 → 実行しない（安全側）
        assertThat(schedule.previousRunnablePointAtOrBefore(nowLocal)).isEmpty();
    }

    @Test
    @DisplayName("適用時刻は再起動でも引き継ぐ（メモリが空でも過去の点を実行しない）")
    void effectiveFromSurvivesRestart() {
        service.loadOnStartup();
        clock.advance(Duration.ofHours(22));
        loader.box.settings.put("NET_CONTROL_END_TIME", "21:00");
        LocalDateTime saved = LocalDateTime.of(2026, 9, 19, 22, 0);
        loader.recordTimingChange("batR03", saved);
        service.refresh("設定保存");

        // 再起動: 新しいサービス（メモリは空）が DB の適用時刻を読む
        ScheduleConfigService restarted = new ScheduleConfigService(catalog, loader, clock);
        restarted.loadOnStartup();

        TaskSchedule schedule = restarted.snapshot().taskOf("batR03").orElseThrow();
        assertThat(schedule.effectiveFrom()).isEqualTo(saved);
        assertThat(schedule.previousRunnablePointAtOrBefore(LocalDateTime.of(2026, 9, 19, 22, 30)))
                .isEmpty();   // 今日の 21:00 は適用時刻より前 → 実行しない
        // 変更していないタスク（batR04）は制限なし＝再起動の補執行は今までどおり
        assertThat(restarted.snapshot().taskOf("batR04").orElseThrow().effectiveFrom()).isNull();
        assertThat(restarted.snapshot().taskOf("batR04").orElseThrow()
                .previousRunnablePointAtOrBefore(LocalDateTime.of(2026, 9, 20, 9, 0)))
                .contains(LocalDateTime.of(2026, 9, 20, 6, 30));
    }

    @Test
    @DisplayName("設定が変わっていなければ適用時刻は動かない（無関係な設定の保存で過去の補償を止めない）")
    void unchangedScheduleKeepsEffectiveFrom() {
        service.loadOnStartup();
        clock.advance(Duration.ofHours(22));
        loader.box.settings.put("NET_CONTROL_END_TIME", "21:00");
        loader.recordTimingChange("batR03", LocalDateTime.of(2026, 9, 19, 22, 0));
        service.refresh("設定保存");
        LocalDateTime first = service.snapshot().taskOf("batR03").orElseThrow().effectiveFrom();

        // 実行設定は変えずに、別の設定（AI モデルなど）を保存した
        clock.advance(Duration.ofMinutes(5));
        loader.box.settings.put("AI_QWEN_MODEL", "qwen-max");
        service.refresh("設定保存");

        assertThat(service.snapshot().taskOf("batR03").orElseThrow().effectiveFrom()).isEqualTo(first);
    }

    // ------------------------------------------------------------------ 並行刷新

    @Test
    @DisplayName("並行刷新: 先に要求した更新が後続の更新を「発行済み」にして捨てない（最新の設定が失われない）")
    void concurrentRefreshDoesNotLoseTheLatestRequest() throws Exception {
        service.loadOnStartup();   // loads=1
        loader.gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // A がロックを取って読み込みで止まる（その間に B が要求する）
            Future<ScheduleConfigService.RefreshResult> first = pool.submit(() -> service.refresh("保存A"));
            Thread.sleep(100);
            Future<ScheduleConfigService.RefreshResult> second = pool.submit(() -> service.refresh("保存B"));
            Thread.sleep(100);
            loader.gate.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS).published()).isTrue();
            // B は捨てられず、実際に読み込んで発行される（ここが従来の不具合）
            assertThat(second.get(5, TimeUnit.SECONDS).published()).isTrue();
            assertThat(loader.loads).hasValue(3);   // 起動時 1 + A 1 + B 1
            assertThat(service.snapshot().version()).isEqualTo(3);
        } finally {
            pool.shutdownNow();
        }
    }
}
