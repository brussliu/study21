package com.study21.admin.batch;

import com.study21.admin.migration.MigrationTestMapper;
import com.study21.admin.schedule.ScheduledTriggerStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **実行記録の帰属（起動識別子）**（実 DB）。
 *
 * <p>再起動の復旧は「前のプロセスが残した実行」だけを扱う。その判定に使う起動識別子が、
 * **すべての実行記録の入口**（自動スケジューラの確保・起動時バッチ・画面の手動実行・
 * 復旧のやり直し）で必ず刻まれること、そして**現在のプロセスの記録が遺留に出ない**ことを
 * 実 DB で確かめる。</p>
 *
 * <p>テストが作った記録は最後に閉じる（未完了を残さない）。</p>
 */
@SpringBootTest(properties = "study21.proxy.port=17777")
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class BatchExecutionRunIdTest {

    /** 実データと衝突しないコード（テストの前後で自分で閉じる）。 */
    private static final String TEST_CODE = "batT" + Long.toString(System.nanoTime() % 1_000_000_000L);

    /** 前のプロセスの起動識別子（遺留の目印）。 */
    private static final String OLD_RUN_ID = "20260919T230000-bbbbbbbb";

    @Autowired
    private BatchExecutionMapper executionMapper;

    @Autowired
    private ScheduledTriggerStore triggerStore;

    @Autowired
    private BatchService batchService;

    @Autowired
    private ProcessRunId processRunId;

    @Autowired
    private MigrationTestMapper sql;

    @AfterEach
    void closeUnfinishedRows() {
        for (BatchExecutionEntity row : executionMapper.findRecent(TEST_CODE, 50)) {
            closeIfUnfinished(row);
        }
        for (String code : List.of("batS01")) {
            for (BatchExecutionEntity row : executionMapper.findRecent(code, 10)) {
                closeIfUnfinished(row);
            }
        }
    }

    private void closeIfUnfinished(BatchExecutionEntity row) {
        if (BatchExecutionStatus.QUEUED.name().equals(row.getStatus())
                || BatchExecutionStatus.RUNNING.name().equals(row.getStatus())) {
            executionMapper.markFinished(row.getExecutionId(), BatchExecutionStatus.SKIPPED.name(),
                    "テストの後始末（未完了を残さない）", null, 0L);
        }
    }

    private List<Long> leftoverIds(String runId) {
        return executionMapper.findLeftoversExceptRunId(runId).stream()
                .map(BatchExecutionEntity::getExecutionId).toList();
    }

    /** テスト用の記録を 1 件作る（RUNNING にするなら開始時刻も入れる）。 */
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
        if (BatchExecutionStatus.RUNNING.name().equals(status)) {
            executionMapper.markRunning(record.getExecutionId());
        }
        return record;
    }

    @Test
    @DisplayName("自動スケジューラの確保で作った記録に、現在の起動識別子が刻まれる")
    void scheduledClaimIsStampedWithTheCurrentRunId() {
        Long executionId = triggerStore.claimAndRecord(TEST_CODE, LocalDateTime.of(2026, 9, 20, 6, 30), "L");

        assertThat(executionId).isNotNull();
        BatchExecutionEntity row = executionMapper.findById(executionId);
        assertThat(row.getRunId()).isEqualTo(processRunId.value());
        // 自分の記録は遺留に出ない（＝復旧が触らない）
        assertThat(leftoverIds(processRunId.value())).doesNotContain(executionId);
        // 別のプロセスの識別子では遺留に出る（＝次の起動が拾える）
        assertThat(leftoverIds(OLD_RUN_ID)).contains(executionId);
    }

    @Test
    @DisplayName("起動時バッチの記録にも、現在の起動識別子が刻まれる")
    void startupRunIsStampedWithTheCurrentRunId() {
        var result = batchService.runOnStartup("batS01");

        Long executionId = (Long) result.get("executionId");
        assertThat(executionId).isNotNull();
        assertThat(executionMapper.findById(executionId).getRunId()).isEqualTo(processRunId.value());
        assertThat(leftoverIds(processRunId.value())).doesNotContain(executionId);
    }

    @Test
    @DisplayName("画面の手動実行（【再実行】）の記録にも、現在の起動識別子が刻まれる")
    void manualRerunIsStampedWithTheCurrentRunId() {
        var result = batchService.rerun("batS01", "tester");

        Long executionId = (Long) result.get("executionId");
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
        BatchExecutionEntity retry = executionMapper.findById(result.retryExecutionId());
        assertThat(retry.getRunId()).isEqualTo(processRunId.value());
        assertThat(leftoverIds(processRunId.value())).doesNotContain(retry.getExecutionId());
    }

    @Test
    @DisplayName("起動識別子が NULL の古い記録は、互換規則で遺留として扱う")
    void legacyRowsWithoutRunIdAreLeftovers() {
        BatchExecutionEntity row = insert(processRunId.value(), BatchExecutionStatus.QUEUED.name());
        // この列が無かった頃の記録を再現する（列は NULL）
        sql.execute("UPDATE " + "\"BAT_バッチ実行履歴情報\"" + " SET \"起動識別子\" = NULL"
                + " WHERE \"実行ID\" = " + row.getExecutionId());

        assertThat(leftoverIds(processRunId.value())).contains(row.getExecutionId());
    }

    @Test
    @DisplayName("いま実行中の記録（現在のプロセスの帰属）は、復旧の遺留に決して入らない")
    void runningRowsOwnedByThisProcessAreNeverLeftovers() {
        BatchExecutionEntity running = insert(processRunId.value(), BatchExecutionStatus.RUNNING.name());

        assertThat(leftoverIds(processRunId.value())).doesNotContain(running.getExecutionId());
        // 実行ID が最大でも（＝ID の大小では判定しない）遺留にならない
        assertThat(executionMapper.findById(running.getExecutionId()).getStatus())
                .isEqualTo(BatchExecutionStatus.RUNNING.name());
    }
}
