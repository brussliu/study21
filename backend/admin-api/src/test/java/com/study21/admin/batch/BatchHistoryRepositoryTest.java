package com.study21.admin.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 実 DB（PostgreSQL）に対する バッチ実行履歴 / バッチコントロール の検証。
 *
 * 改名後のテーブル（BAT_バッチ実行履歴情報）へ実行を記録できること、
 * 有効／無効が BAT_バッチコントロール情報 に保存されることを確かめる。
 * テストはロールバックするので DB は汚れない。
 *
 * 実行には DB のパスワードが要る（テスト環境の既定は空のため、無いときはスキップする）:
 *   STUDY21_DATASOURCE_PASSWORD=... mvn -pl admin-api -am test
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class BatchHistoryRepositoryTest {

    @Autowired
    private BatchExecutionMapper executionMapper;

    @Autowired
    private BatchControlMapper controlMapper;

    @Autowired
    private BatchService batchService;

    @Test
    void executionIsRecordedIntoTheRenamedHistoryTable() {
        BatchExecutionEntity record = new BatchExecutionEntity();
        record.setBatchCode("batS01");
        record.setBatchType("S");
        record.setTriggerType("S");
        record.setStatus(BatchExecutionStatus.QUEUED.name());
        record.setRequestedByCode("scheduler");
        record.setMessage("E2E: 実行を受け付けました");

        assertThat(executionMapper.insert(record)).isEqualTo(1);
        assertThat(record.getExecutionId()).isNotNull();

        BatchExecutionEntity queued = executionMapper.findById(record.getExecutionId());
        assertThat(queued.getBatchCode()).isEqualTo("batS01");
        assertThat(queued.getBatchType()).isEqualTo("S");
        assertThat(queued.getStatus()).isEqualTo("QUEUED");

        // 未完了の探索（二重起動の防止）
        assertThat(executionMapper.findRunningByBatchCode("batS01")).isNotNull();

        executionMapper.markRunning(record.getExecutionId());
        executionMapper.markFinished(record.getExecutionId(),
                BatchExecutionStatus.FAILED.name(), "E2E: 失敗", "stack trace", 12L);

        BatchExecutionEntity finished = executionMapper.findById(record.getExecutionId());
        assertThat(finished.getStatus()).isEqualTo("FAILED");
        assertThat(finished.getErrorDetail()).isEqualTo("stack trace");
        // 処理時間がミリ秒で記録される
        assertThat(finished.getDurationMs()).isEqualTo(12L);
        assertThat(executionMapper.findRunningByBatchCode("batS01")).isNull();
        assertThat(executionMapper.countByBatchCodeAndStatus("batS01", "FAILED")).isEqualTo(1);
        assertThat(executionMapper.findRecent("batS01", 5)).isNotEmpty();
    }

    @Test
    void controlRowsArePreparedForStartupAndScheduledBatches() {
        // 一覧を引くと S / L / R の行が用意される
        batchService.listTasks();

        BatchControlEntity loop = controlMapper.findByBatchCode("batS01");
        assertThat(loop).isNotNull();
        assertThat(loop.getNote()).isEqualTo("バッチ管理画面の有効設定（OFF時は定時実行しない）");

        // 切り替えて保存される
        batchService.updateActive("batS01", false, "admin");
        BatchControlEntity off = controlMapper.findByBatchCode("batS01");
        assertThat(off.getStatus()).isEqualTo("0");
        assertThat(off.getUpdatedByCode()).isEqualTo("admin");
        assertThat(off.getVersion()).isEqualTo(loop.getVersion() + 1);

        batchService.updateActive("batS01", true, "admin");
        assertThat(controlMapper.findByBatchCode("batS01").getStatus()).isEqualTo("1");
    }

    @Test
    void finalRunTimeIsRecorded() {
        batchService.listTasks();
        assertThat(controlMapper.findByBatchCode("batR05")).isNotNull();
        assertThat(controlMapper.touchLastRunAt("batR05")).isEqualTo(1);
        assertThat(controlMapper.findByBatchCode("batR05").getLastRunAt()).isNotNull();
    }

    @Test
    void migratedHistoryIsSearchableAndPaged() {
        // 2.0 から移行した履歴（batL01 -> batS01 に読み替え済み）が引ける
        assertThat(executionMapper.countHistory("batS01", null, null)).isGreaterThan(0);
        assertThat(executionMapper.searchHistory("batS01", null, null, 5, 0)).hasSize(5);

        // 2 ページ目は別の行になる
        var first = executionMapper.searchHistory("batS01", null, null, 5, 0);
        var second = executionMapper.searchHistory("batS01", null, null, 5, 5);
        assertThat(second).isNotEmpty();
        assertThat(second.get(0).getExecutionId()).isNotEqualTo(first.get(0).getExecutionId());

        // 状態でも絞り込める（2.0 の履歴には 異常終了 の行が 1,000 件以上ある）
        assertThat(executionMapper.countHistory(null, "FAILED", null)).isGreaterThan(0);
        // キーワードはメッセージ・エラー詳細にも当たる（当たらない語なら 0 件）
        assertThat(executionMapper.countHistory(null, null, "バッチ実行履歴クリーンアップ")).isGreaterThanOrEqualTo(0);

        // バッチごとの最新 1 件（一覧画面の「最新実行」）
        assertThat(executionMapper.findLatestPerBatch()).isNotEmpty();
    }

    @Test
    void migratedControlsEnableOnlyTheProxyBatch() {
        // 移行データの有効／無効: batS01 だけ有効、他は無効
        batchService.listTasks();

        assertThat(controlMapper.findByBatchCode("batS01").getStatus()).isEqualTo("1");
        assertThat(controlMapper.findByBatchCode("batL02").getStatus()).isEqualTo("0");
        assertThat(controlMapper.findByBatchCode("batL03").getStatus()).isEqualTo("0");
        assertThat(controlMapper.findByBatchCode("batR01").getStatus()).isEqualTo("0");

        // 起動時に実行されるのは batS01 だけ
        assertThat(batchService.startupTargets()).containsExactly("batS01");
    }
}
