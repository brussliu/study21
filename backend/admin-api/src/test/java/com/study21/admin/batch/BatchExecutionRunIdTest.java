package com.study21.admin.batch;

import com.study21.admin.schedule.ScheduledTriggerStore;
import com.study21.admin.testing.BatchTestExecutionCleanup;
import com.study21.admin.testing.StubBatchTaskHandler;
import com.study21.admin.testing.TestSqlMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **実行記録の帰属（起動識別子）**（実 DB・専用の使い捨て DB で実行）。
 *
 * <p>見るもの: 実行記録の**すべての入口**（自動スケジューラの確保・起動時バッチ・画面の手動実行・
 * 復旧のやり直し）で起動識別子が刻まれ、**現在のプロセスの記録が遺留（復旧の対象）に出ない**こと。</p>
 *
 * <p>環境: {@code STUDY21_TEST_DATASOURCE_URL} で渡された**専用のテスト DB**だけで動く
 * （日常の開発 DB には繋がない。渡されなければスキップする）。
 * 起動時の副作用（スケジューラ・起動時バッチ・実業務のハンドラ）は止めてある。</p>
 *
 * <p>後始末は {@link BatchTestExecutionCleanup} で**このテストが作った実行IDだけ**を閉じる
 * （「最近の N 件」をまとめて触らない）。</p>
 */
@SpringBootTest(properties = "study21.proxy.port=17777")
@ActiveProfiles("testdb")
@EnabledIfEnvironmentVariable(named = "STUDY21_TEST_DATASOURCE_URL", matches = ".+",
        disabledReason = "専用のテスト DB（STUDY21_TEST_DATASOURCE_URL）が未設定のためスキップします"
                + "（tmp/tools/study21-batchtestdb.sh start で用意できます）")
class BatchExecutionRunIdTest {

    /** 実データと衝突しないコード（テストの後始末は自分が作った ID だけを閉じる）。 */
    private static final String TEST_CODE = "batT" + Long.toString(System.nanoTime() % 1_000_000_000L);

    /** 前のプロセスの起動識別子（遺留の目印）。 */
    private static final String OLD_RUN_ID = "20260919T230000-bbbbbbbb";

    @Autowired
    private BatchExecutionMapper executionMapper;

    @Autowired
    private ScheduledTriggerStore triggerStore;

    @Autowired
    private ProcessRunId processRunId;

    @Autowired
    private TestSqlMapper sql;

    @Autowired
    private BatchTaskRegistry registry;

    @Autowired
    private com.study21.admin.setting.SettingsService settingsService;

    @Autowired
    private BatchControlMapper controlMapper;

    @Autowired
    private AiCallLogMapper aiCallLogMapper;

    @Autowired
    private com.study21.admin.schedule.ScheduleTimingRecorder timingRecorder;

    /**
     * テストが組み立てたバッチサービス（**代役ハンドラだけ**を持つ）。
     * 実ハンドラ（プロキシ起動など）を動かさないため、Spring の Bean ではなく自前で作る。
     */
    private BatchServiceImpl testBatchService;
    private StubBatchTaskHandler stubHandler;

    private BatchTestExecutionCleanup cleanup;

    @BeforeEach
    void setUp() {
        cleanup = new BatchTestExecutionCleanup(executionMapper);
        // 実ハンドラの代わりに「記録だけ返す」代役を使う（外部への副作用を出さない）
        stubHandler = new StubBatchTaskHandler("batS01", "テスト: 何もしません");
        testBatchService = new BatchServiceImpl(registry, settingsService, executionMapper, controlMapper,
                aiCallLogMapper, List.of(stubHandler), List.of(), timingRecorder);
    }

    @AfterEach
    void closeOnlyOwnRows() {
        // アサーションが失敗しても必ず通る。**このテストが作った ID だけ**を閉じる
        cleanup.closeAll();
    }

    private List<Long> leftoverIds(String runId) {
        return executionMapper.findLeftoversExceptRunId(runId).stream()
                .map(BatchExecutionEntity::getExecutionId).toList();
    }

    /** テスト用の記録を 1 件作る（自分で作ったものとして登録する）。 */
    private BatchExecutionEntity insert(String runId, String status) {
        BatchExecutionEntity record = new BatchExecutionEntity();
        record.setBatchCode(TEST_CODE);
        record.setBatchType("R");
        record.setTriggerType("R");
        record.setStatus(BatchExecutionStatus.QUEUED.name());
        record.setRequestedByCode("TEST");
        record.setRunId(runId);
        record.setMessage("テスト用");
        executionMapper.insert(record);
        cleanup.register(record);
        if (BatchExecutionStatus.RUNNING.name().equals(status)) {
            executionMapper.markRunning(record.getExecutionId());
        }
        return record;
    }

    @Test
    @DisplayName("自動スケジューラの確保で作った記録に、現在の起動識別子が刻まれる")
    void scheduledClaimIsStampedWithTheCurrentRunId() {
        Long executionId = triggerStore.claimAndRecord(TEST_CODE, LocalDateTime.of(2026, 9, 20, 6, 30), "L");
        cleanup.register(executionId);

        assertThat(executionId).isNotNull();
        assertThat(executionMapper.findById(executionId).getRunId()).isEqualTo(processRunId.value());
        // 自分の記録は遺留に出ない（＝復旧が触らない）
        assertThat(leftoverIds(processRunId.value())).doesNotContain(executionId);
        // 別のプロセスの識別子では遺留に出る（＝次の起動が拾える）
        assertThat(leftoverIds(OLD_RUN_ID)).contains(executionId);
    }

    @Test
    @DisplayName("起動時バッチの記録にも、現在の起動識別子が刻まれる")
    void startupRunIsStampedWithTheCurrentRunId() {
        var result = testBatchService.runOnStartup("batS01");

        Long executionId = (Long) result.get("executionId");
        cleanup.register(executionId);
        assertThat(executionId).isNotNull();
        assertThat(executionMapper.findById(executionId).getRunId()).isEqualTo(processRunId.value());
        assertThat(leftoverIds(processRunId.value())).doesNotContain(executionId);
    }

    @Test
    @DisplayName("画面の手動実行（【再実行】）の記録にも、現在の起動識別子が刻まれる")
    void manualRerunIsStampedWithTheCurrentRunId() {
        var result = testBatchService.rerun("batS01", "tester");

        Long executionId = (Long) result.get("executionId");
        cleanup.register(executionId);
        assertThat(executionId).isNotNull();
        assertThat(executionMapper.findById(executionId).getRunId()).isEqualTo(processRunId.value());
        assertThat(leftoverIds(processRunId.value())).doesNotContain(executionId);
    }

    @Test
    @DisplayName("復旧のやり直しの記録にも、現在の起動識別子が刻まれる")
    void recoveryRetryIsStampedWithTheCurrentRunId() {
        // 前のプロセスが残した実行中（結果不明）の記録
        BatchExecutionEntity running = insert(OLD_RUN_ID, BatchExecutionStatus.RUNNING.name());

        ScheduledTriggerStore.RecoveryResult result = triggerStore.recoverRunningExecution(
                running.getExecutionId(), TEST_CODE, "R", LocalDateTime.of(2026, 9, 20, 6, 30),
                "テスト: やり直し", "RECOVERY");

        assertThat(result.retryExecutionId()).isNotNull();
        // やり直しは元実行ID でつながっているので、後始末でも自分のものとして扱える
        assertThat(executionMapper.findById(result.retryExecutionId()).getRunId())
                .isEqualTo(processRunId.value());
        assertThat(leftoverIds(processRunId.value())).doesNotContain(result.retryExecutionId());
    }

    @Test
    @DisplayName("起動識別子が NULL の古い記録は、互換規則で遺留として扱う")
    void legacyRowsWithoutRunIdAreLeftovers() {
        BatchExecutionEntity row = insert(processRunId.value(), BatchExecutionStatus.QUEUED.name());
        // この列が無かった頃の記録を再現する（列は NULL）
        sql.execute("UPDATE public.\"BAT_バッチ実行履歴情報\" SET \"起動識別子\" = NULL"
                + " WHERE \"実行ID\" = " + row.getExecutionId());

        assertThat(leftoverIds(processRunId.value())).contains(row.getExecutionId());
    }

    @Test
    @DisplayName("いま実行中の記録（現在のプロセスの帰属）は、復旧の遺留に決して入らない")
    void runningRowsOwnedByThisProcessAreNeverLeftovers() {
        BatchExecutionEntity running = insert(processRunId.value(), BatchExecutionStatus.RUNNING.name());

        assertThat(leftoverIds(processRunId.value())).doesNotContain(running.getExecutionId());
        assertThat(executionMapper.findById(running.getExecutionId()).getStatus())
                .isEqualTo(BatchExecutionStatus.RUNNING.name());
    }
}
