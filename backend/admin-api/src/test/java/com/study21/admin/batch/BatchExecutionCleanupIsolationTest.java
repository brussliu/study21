package com.study21.admin.batch;

import com.study21.admin.testing.BatchTestExecutionCleanup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **テストの後始末が、自分が作った記録だけを触ること**（実 DB・専用 DB）。
 *
 * <p>「最近の N 件」をまとめて閉じる片付け方は、他のテストや業務が作った未完了の記録まで
 * 書き換えてしまう。ここでは自分のものとして登録した記録と、**登録していない**記録を用意し、
 * 後始末（{@link BatchTestExecutionCleanup#closeAll()}）のあとで
 * **登録していない記録の状態も中身も変わらない**ことを確かめる。
 * 検証用の記録は、このテストが最後に自分で片付ける。</p>
 */
@SpringBootTest
@ActiveProfiles("testdb")
@EnabledIfEnvironmentVariable(named = "STUDY21_TEST_DATASOURCE_URL", matches = ".+",
        disabledReason = "専用のテスト DB（STUDY21_TEST_DATASOURCE_URL）が未設定のためスキップします")
class BatchExecutionCleanupIsolationTest {

    @Autowired
    private BatchExecutionMapper executionMapper;

    @MockitoBean
    private BatchStartupRunner startupRunner;

    /** 検証用に作った「後始末の対象ではない」記録（このテストが最後に片付ける）。 */
    private final List<Long> foreignIds = new ArrayList<>();

    @AfterEach
    void closeForeignRows() {
        // 検証の最後に、このテストが作った記録だけを片付ける（他の経路の後始末には任せない）
        for (Long executionId : foreignIds) {
            BatchExecutionEntity row = executionMapper.findById(executionId);
            if (row != null && (BatchExecutionStatus.QUEUED.name().equals(row.getStatus())
                    || BatchExecutionStatus.RUNNING.name().equals(row.getStatus()))) {
                executionMapper.markFinished(executionId, BatchExecutionStatus.SKIPPED.name(),
                        "検証用の記録をこのテストが片付ける", null, 0L);
            }
        }
        foreignIds.clear();
    }

    private BatchExecutionEntity insert(String code, String message) {
        BatchExecutionEntity record = new BatchExecutionEntity();
        record.setBatchCode(code);
        record.setBatchType("R");
        record.setTriggerType("R");
        record.setStatus(BatchExecutionStatus.QUEUED.name());
        record.setRequestedByCode("TEST");
        record.setRunId("20260920T000000-test0001");
        record.setMessage(message);
        executionMapper.insert(record);
        return record;
    }

    @Test
    @DisplayName("後始末は自分が登録した記録だけを閉じ、登録していない記録は状態も中身も変えない")
    void cleanupTouchesOnlyRegisteredRows() {
        // 衝突しないコードを使う（何度実行しても一意キーで衝突しない）
        String suffix = Long.toString(System.nanoTime() % 1_000_000_000L);
        BatchExecutionEntity mine = insert("batT" + suffix + "a", "このテストが作った記録");
        BatchExecutionEntity foreign = insert("batT" + suffix + "b", "このテストの後始末対象ではない記録");
        foreignIds.add(foreign.getExecutionId());

        BatchExecutionEntity before = executionMapper.findById(foreign.getExecutionId());
        BatchTestExecutionCleanup cleanup = new BatchTestExecutionCleanup(executionMapper);
        cleanup.register(mine);

        cleanup.closeAll();

        // 自分の記録は閉じられる
        assertThat(executionMapper.findById(mine.getExecutionId()).getStatus())
                .isEqualTo(BatchExecutionStatus.SKIPPED.name());
        // 登録していない記録は状態も中身（メッセージ・更新日時）も変わらない
        BatchExecutionEntity after = executionMapper.findById(foreign.getExecutionId());
        assertThat(after.getStatus()).isEqualTo(BatchExecutionStatus.QUEUED.name());
        assertThat(after.getMessage()).isEqualTo(before.getMessage());
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
    }
}
