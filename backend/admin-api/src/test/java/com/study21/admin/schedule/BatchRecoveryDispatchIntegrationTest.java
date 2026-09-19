package com.study21.admin.schedule;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchExecutionMapper;
import com.study21.admin.batch.BatchExecutionStatus;
import com.study21.admin.batch.BatchServiceImpl;
import com.study21.admin.batch.ProcessRunId;
import com.study21.admin.testing.BatchTestExecutionCleanup;
import com.study21.admin.testing.StubBatchTaskHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **復旧の「整理してから投入」と、実行側の「前回が未完了ならスキップ」の通しの検証**（実 DB・専用 DB）。
 *
 * <p>直したい不具合: 復旧が判定しながら順に投入すると、同じタスクの**古い記録が未完了のうちに**
 * 新しい記録が走り出し、{@code BatchServiceImpl#runQueued} の「前回の実行が終わっていない」に
 * 当たって**本来実行すべき記録がスキップされ、そのあと古い記録が閉じられる**（＝実行が失われる）。
 * ここでは本物の復旧・本物の実行器・本物の {@code runQueued} を通し、
 * **業務ハンドラの呼び出し回数**と**最終的な実行記録の状態**で確かめる。</p>
 *
 * <p>環境: {@code STUDY21_TEST_DATASOURCE_URL} の専用テスト DB だけで動く（無ければスキップ）。
 * 実業務（動画取込）は代役に置き換え、外部への副作用は出さない。</p>
 */
@SpringBootTest(properties = "study21.proxy.port=17777")
@ActiveProfiles("testdb")
@EnabledIfEnvironmentVariable(named = "STUDY21_TEST_DATASOURCE_URL", matches = ".+",
        disabledReason = "専用のテスト DB（STUDY21_TEST_DATASOURCE_URL）が未設定のためスキップします")
class BatchRecoveryDispatchIntegrationTest {

    /** 前のプロセスの起動識別子（遺留の目印）。 */
    private static final String OLD_RUN_ID = "20260919T230000-cccccccc";

    @Autowired
    private BatchExecutionMapper executionMapper;

    @Autowired
    private ScheduledTriggerStore triggerStore;

    @Autowired
    private ScheduleRuleCatalog catalog;

    @Autowired
    private SchedulePlanGuard planGuard;

    @Autowired
    private ScheduleConfigService configService;

    @Autowired
    private ProcessRunId processRunId;

    @Autowired
    private com.study21.admin.batch.BatchTaskRegistry registry;

    @Autowired
    private com.study21.admin.setting.SettingsService settingsService;

    @Autowired
    private com.study21.admin.batch.BatchControlMapper controlMapper;

    @Autowired
    private com.study21.admin.batch.AiCallLogMapper aiCallLogMapper;

    @Autowired
    private ScheduleTimingRecorder timingRecorder;

    @Autowired
    private com.study21.admin.testing.TestSqlMapper sql;

    @Autowired
    private org.springframework.context.ConfigurableApplicationContext applicationContext;

    /** testdb では自動運転が無効（3 つのスイッチ）。値そのものを確かめる。 */
    @org.springframework.beans.factory.annotation.Value("${study21.batch.auto-run.schedule-enabled:true}")
    private boolean scheduleAutoEnabled;

    @org.springframework.beans.factory.annotation.Value("${study21.batch.auto-run.startup-enabled:true}")
    private boolean startupAutoEnabled;

    @org.springframework.beans.factory.annotation.Value("${study21.batch.auto-run.recovery-enabled:true}")
    private boolean recoveryAutoEnabled;

    /** 実業務（動画取込など）の代役。呼ばれた回数と開始をテストから観察する。 */
    private StubBatchTaskHandler stubHandler;
    private BatchExecutionRecovery recovery;
    private BatchTestExecutionCleanup cleanup;
    /** テストが組み立てた実行器（後始末の前に必ず止める＝片付け中の書き込みを防ぐ）。 */
    private BatchScheduleExecutor manualExecutor;

    @BeforeEach
    void setUp() {
        cleanup = new BatchTestExecutionCleanup(executionMapper);
        // この試験で使う batL02 を有効にし、**設定の適用時刻を古い値**にしておく
        // （専用テスト DB の状態は試験が自分で整える。設定の適用時刻が「いま」だと、
        //   試験が作る計画実行点（過去）は「適用時刻より前」として正しく弾かれてしまう）。
        // 保存トランザクションと同じ形（適用時刻＋計画バージョンを一緒に進める）にして、
        // 設定サービスが「外部から直接変更された」と見なさないようにする。
        sql.execute("INSERT INTO public.\"BAT_バッチコントロール情報\""
                + " (\"バッチコード\", \"状態\", \"登録日時\", \"更新日時\")"
                + " VALUES ('batL02', '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"
                + " ON CONFLICT (\"バッチコード\") DO UPDATE SET \"状態\" = '1'");
        sql.execute("INSERT INTO public.\"BAT_スケジュール状態情報\""
                + " (\"バッチコード\", \"設定適用日時\", \"設定版\", \"バージョン\", \"登録日時\", \"更新日時\")"
                + " VALUES ('batL02', '2026-09-01 00:00:00', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"
                + " ON CONFLICT (\"バッチコード\") DO UPDATE SET"
                + " \"設定適用日時\" = '2026-09-01 00:00:00',"
                + " \"設定版\" = public.\"BAT_スケジュール状態情報\".\"設定版\" + 1");
        // 実ハンドラ（動画取込）を混ぜないよう、**代役だけを持つ**サービスをテストが組み立てる
        stubHandler = new StubBatchTaskHandler("batL02", "テスト: 実行しました");
        BatchServiceImpl testBatchService = new BatchServiceImpl(registry, settingsService, executionMapper,
                controlMapper, aiCallLogMapper, List.of(stubHandler), List.of(), timingRecorder);
        manualExecutor = new BatchScheduleExecutor(testBatchService, planGuard,
                configService, 1, BatchScheduleExecutor.DEFAULT_QUEUE_CAPACITY,
                Clock.system(ScheduleConfigService.ZONE));
        BatchScheduleExecutor testExecutor = manualExecutor;
        // 設定を読み直しておく（この経路はスケジューラと同じ）
        configService.refresh("テスト");
        // テストは自動の入口を使わず、必要なときに明示的に呼ぶ（自動運転は testdb で無効）
        recovery = new BatchExecutionRecovery(executionMapper, triggerStore, testExecutor, catalog,
                planGuard, configService, processRunId, true, Clock.system(ScheduleConfigService.ZONE));
    }

    @AfterEach
    void closeOnlyOwnRows() {
        // 手動で作った実行器を先に止める（片付けの間に業務が書き込まないように）
        if (manualExecutor != null) {
            manualExecutor.shutdown();
        }
        cleanup.closeAll();
        // この試験が作った記録（前のプロセスの識別子で印を付けたもの）のうち、
        // 未完了のまま残ったものを閉じる（次の実行が拾ってしまわないように）
        executionMapper.findLeftoversExceptRunId(processRunId.value()).stream()
                .filter(row -> OLD_RUN_ID.equals(row.getRunId()))
                .forEach(row -> cleanup.register(row.getExecutionId()));
        cleanup.closeAll();
    }

    /** 前のプロセスが残した実行記録を作る（起動識別子は前のプロセスの値）。 */
    private BatchExecutionEntity insertLeftover(String taskCode, LocalDateTime plannedAt, String status) {
        BatchExecutionEntity record = new BatchExecutionEntity();
        record.setBatchCode(taskCode);
        record.setBatchType("L");
        record.setTriggerType("L");
        record.setStatus(BatchExecutionStatus.QUEUED.name());
        record.setRequestedByCode("SCHEDULER");
        record.setScheduleTime(plannedAt.toString());
        record.setRunId(OLD_RUN_ID);
        record.setMessage("テスト: 前のプロセスの遺留");
        executionMapper.insert(record);
        cleanup.register(record);
        if (BatchExecutionStatus.RUNNING.name().equals(status)) {
            executionMapper.markRunning(record.getExecutionId());
        }
        return record;
    }

    /** このプロセスの起動識別子で作られた起動時バッチ（batS01）の記録数。 */
    private long ownStartupBatchRows() {
        List<java.util.Map<String, Object>> rows = sql.query(
                "SELECT COUNT(*) AS c FROM public.\"BAT_バッチ実行履歴情報\""
                        + " WHERE \"起動識別子\" = '" + processRunId.value() + "'"
                        + " AND \"バッチコード\" = 'batS01'");
        return ((Number) rows.get(0).get("c")).longValue();
    }

    /** 終了状態になるまで待つ（業務は別スレッドで走る）。 */
    private BatchExecutionEntity awaitTerminal(long executionId, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        BatchExecutionEntity row = executionMapper.findById(executionId);
        while (row != null && !BatchExecutionStatus.SUCCESS.name().equals(row.getStatus())
                && !BatchExecutionStatus.FAILED.name().equals(row.getStatus())
                && Instant.now().isBefore(deadline)) {
            Thread.sleep(50);
            row = executionMapper.findById(executionId);
        }
        return row;
    }

    @Test
    @DisplayName("testdb では自動運転のスイッチが 3 つとも無効（定期実行・起動時バッチ・自動復旧）")
    void autoRunSwitchesAreDisabledInTestDb() {
        assertThat(scheduleAutoEnabled).isFalse();
        assertThat(startupAutoEnabled).isFalse();
        assertThat(recoveryAutoEnabled).isFalse();
    }

    @Test
    @DisplayName("起動イベントでは自動復旧しない（スイッチ無効）。明示的に呼べば復旧する")
    void applicationReadyEventDoesNotRecoverButExplicitCallDoes() throws Exception {
        TaskSchedule schedule = TaskSchedule.interval("batL02", true, 5, 1);
        LocalDateTime newest = schedule.previousRunnablePointAtOrBefore(LocalDateTime.now(ScheduleConfigService.ZONE))
                .orElseThrow();
        BatchExecutionEntity latest = insertLeftover("batL02", newest, BatchExecutionStatus.QUEUED.name());

        // 起動イベントをそのまま流す（本物のリスナーが動く）
        long startupRowsBefore = ownStartupBatchRows();
        applicationContext.publishEvent(new org.springframework.boot.context.event.ApplicationReadyEvent(
                new org.springframework.boot.SpringApplication(), new String[0], applicationContext,
                java.time.Duration.ZERO));
        // 待たない: 自動の入口はどちらも**同期**に動く（動いていればこの時点で記録が変わっている）。
        // 起動時バッチも自動復旧も走らない（スイッチが無効）
        assertThat(ownStartupBatchRows()).isEqualTo(startupRowsBefore);
        assertThat(executionMapper.findById(latest.getExecutionId()).getStatus())
                .isEqualTo(BatchExecutionStatus.QUEUED.name());
        assertThat(stubHandler.calls()).isZero();

        // 明示的に呼べば（業務の入口はスイッチの影響を受けない）復旧して実行される
        recovery.recoverInterruptedExecutions();
        assertThat(stubHandler.started().await(20, TimeUnit.SECONDS)).isTrue();
        assertThat(awaitTerminal(latest.getExecutionId(), Duration.ofSeconds(20)).getStatus())
                .isEqualTo(BatchExecutionStatus.SUCCESS.name());
        assertThat(stubHandler.executedIds()).containsExactly(latest.getExecutionId());
    }

    @Test
    @DisplayName("古い記録と最新の記録: 古い方を閉じてから投入するので、最新はスキップされず 1 回だけ実行される")
    void theNewestRecordRunsOnceInsteadOfBeingSkipped() throws Exception {
        TaskSchedule schedule = TaskSchedule.interval("batL02", true, 5, 1);
        LocalDateTime newest = schedule.previousRunnablePointAtOrBefore(LocalDateTime.now(ScheduleConfigService.ZONE))
                .orElseThrow();
        BatchExecutionEntity older = insertLeftover("batL02", newest.minusMinutes(5), BatchExecutionStatus.QUEUED.name());
        BatchExecutionEntity latest = insertLeftover("batL02", newest, BatchExecutionStatus.QUEUED.name());

        recovery.recoverInterruptedExecutions();

        // 業務が始まった（＝「前回が終わっていない」でスキップされていない）
        assertThat(stubHandler.started().await(20, TimeUnit.SECONDS))
                .as("最新の記録が実行される（古い記録にブロックされない）").isTrue();
        BatchExecutionEntity finished = awaitTerminal(latest.getExecutionId(), Duration.ofSeconds(20));

        assertThat(finished.getStatus()).isEqualTo(BatchExecutionStatus.SUCCESS.name());
        assertThat(finished.getMessage()).contains("テスト: 実行しました");
        // **この記録が** 1 回だけ業務を実行した（他の記録が混ざっていても判定は変わらない）
        assertThat(stubHandler.executedIds()).containsExactly(latest.getExecutionId());
        assertThat(executionMapper.findById(older.getExecutionId()).getStatus())
                .isEqualTo(BatchExecutionStatus.SKIPPED.name());   // 古い方は閉じている
        assertThat(executionMapper.findById(older.getExecutionId()).getMessage()).contains("計画が有効でなかった");
    }

    @Test
    @DisplayName("古い記録が実行中（RUNNING）でも、先に閉じてから最新を実行する（1 回だけ）")
    void theNewestRecordRunsOnceEvenWhenTheOlderOneWasRunning() throws Exception {
        TaskSchedule schedule = TaskSchedule.interval("batL02", true, 5, 1);
        LocalDateTime newest = schedule.previousRunnablePointAtOrBefore(LocalDateTime.now(ScheduleConfigService.ZONE))
                .orElseThrow();
        BatchExecutionEntity older = insertLeftover("batL02", newest.minusMinutes(5), BatchExecutionStatus.RUNNING.name());
        BatchExecutionEntity latest = insertLeftover("batL02", newest, BatchExecutionStatus.QUEUED.name());

        recovery.recoverInterruptedExecutions();

        assertThat(stubHandler.started().await(20, TimeUnit.SECONDS)).isTrue();
        BatchExecutionEntity finished = awaitTerminal(latest.getExecutionId(), Duration.ofSeconds(20));

        assertThat(finished.getStatus()).isEqualTo(BatchExecutionStatus.SUCCESS.name());
        assertThat(stubHandler.executedIds()).containsExactly(latest.getExecutionId());
        // 実行中だった古い記録は「結果不明」として閉じる（やり直しは作らない＝計画が古いため）
        BatchExecutionEntity closed = executionMapper.findById(older.getExecutionId());
        assertThat(closed.getStatus()).isEqualTo(BatchExecutionStatus.FAILED.name());
        assertThat(executionMapper.findBySourceExecutionId(older.getExecutionId())).isNull();
    }
}
