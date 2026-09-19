package com.study21.admin.schedule;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchExecutionMapper;
import com.study21.admin.batch.BatchExecutionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 再起動の復旧の**トランザクション**（実 DB）。
 *
 * <p>利用者の指摘: 「旧実行を閉じる」と「やり直しの実行記録を作る」を別々に行うと、
 * 間で失敗したときに**その計画実行点が永久に実行されない**（漏執行）。同じトランザクションで
 * 行い、どちらか一方だけが残らないようにする。二重のやり直しは
 * **元実行ID の部分一意索引**で守る（メモリのロックに依存しない）。</p>
 *
 * <p>テストは自分で作った行（テスト専用のバッチコード）だけを使い、最後に消す。</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class ScheduleRecoveryTransactionTest {

    /** 実データと衝突しないコード（テストの前後で自分で消す）。 */
    private static final String TEST_CODE = "batT" + Long.toString(System.nanoTime() % 1_000_000_000L);

    private static final LocalDateTime PLANNED = LocalDateTime.of(2026, 9, 20, 6, 30);

    @Autowired
    private ScheduledTriggerStore triggerStore;

    @Autowired
    private BatchExecutionMapper executionMapper;

    /**
     * 後始末: テストが作った行のうち**未完了のまま残ったもの**を閉じる（実データには触らない）。
     *
     * <p>履歴の行そのものは残す（実行履歴は消さない運用のため）。未完了で残すと、
     * 他のテストや復旧が「中断した実行」として拾ってしまうので、そこだけ閉じる。</p>
     */
    @AfterEach
    void closeUnfinishedTestRows() {
        for (BatchExecutionEntity row : executionMapper.findRecent(TEST_CODE, 50)) {
            if (BatchExecutionStatus.QUEUED.name().equals(row.getStatus())
                    || BatchExecutionStatus.RUNNING.name().equals(row.getStatus())) {
                executionMapper.markFinished(row.getExecutionId(), BatchExecutionStatus.SKIPPED.name(),
                        "テストの後始末（未完了を残さない）", null, 0L);
            }
        }
    }

    /** 中断した実行（実行中）を 1 件作る。 */
    private BatchExecutionEntity insertRunning() {
        BatchExecutionEntity record = new BatchExecutionEntity();
        record.setBatchCode(TEST_CODE);
        record.setBatchType("R");
        record.setTriggerType("R");
        // 挿入は QUEUED で行い、開始時刻を入れてから RUNNING にする（状態と時刻の CHECK 制約）
        record.setStatus(BatchExecutionStatus.QUEUED.name());
        record.setRequestedByCode("TEST");
        record.setScheduleTime(PLANNED.toString());
        record.setMessage("テスト: 中断した実行");
        executionMapper.insert(record);
        executionMapper.markRunning(record.getExecutionId());
        return record;
    }

    @Test
    @DisplayName("やり直しの挿入が失敗したら、旧実行を閉じたことも巻き戻る（片方だけ残らない）")
    void insertFailureRollsBackTheClose() {
        BatchExecutionEntity running = insertRunning();

        // バッチコードが長すぎて挿入が失敗する（＝「閉じたあとに失敗した」状況を作る）
        String tooLong = "x".repeat(64);
        assertThatThrownBy(() -> triggerStore.recoverRunningExecution(running.getExecutionId(), tooLong,
                "R", PLANNED, "テスト: 失敗させる", "RECOVERY"))
                .isInstanceOf(RuntimeException.class);

        // 旧実行は**閉じられていない**（未完了のまま）。やり直しも作られていない
        BatchExecutionEntity after = executionMapper.findById(running.getExecutionId());
        assertThat(after.getStatus()).isEqualTo(BatchExecutionStatus.RUNNING.name());
        assertThat(executionMapper.findBySourceExecutionId(running.getExecutionId())).isNull();
    }

    @Test
    @DisplayName("閉じるのとやり直しの作成が 1 トランザクションで行われ、やり直しは 1 つだけ")
    void closesAndCreatesTheRetryInOneTransaction() {
        BatchExecutionEntity running = insertRunning();

        ScheduledTriggerStore.RecoveryResult result = triggerStore.recoverRunningExecution(
                running.getExecutionId(), TEST_CODE, "R", PLANNED, "テスト: やり直し", "RECOVERY");

        assertThat(result.handled()).isTrue();
        assertThat(result.created()).isTrue();
        assertThat(result.retryExecutionId()).isNotNull();

        BatchExecutionEntity closed = executionMapper.findById(running.getExecutionId());
        assertThat(closed.getStatus()).isEqualTo(BatchExecutionStatus.FAILED.name());

        BatchExecutionEntity retry = executionMapper.findById(result.retryExecutionId());
        assertThat(retry.getStatus()).isEqualTo(BatchExecutionStatus.QUEUED.name());
        assertThat(retry.getSourceExecutionId()).isEqualTo(running.getExecutionId());
        assertThat(retry.getScheduleTime()).startsWith("2026-09-20 06:30");   // DB は TIMESTAMP の表記で返す
    }

    @Test
    @DisplayName("2 つの復旧が同時に走っても、やり直しは 1 つだけ（DB の一意性で守る）")
    void concurrentRecoveriesCreateOnlyOneRetry() throws Exception {
        BatchExecutionEntity running = insertRunning();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ScheduledTriggerStore.RecoveryResult>> futures = List.of(
                    pool.submit(() -> {
                        start.await(5, TimeUnit.SECONDS);
                        return triggerStore.recoverRunningExecution(running.getExecutionId(), TEST_CODE,
                                "R", PLANNED, "テスト: 同時 1", "RECOVERY");
                    }),
                    pool.submit(() -> {
                        start.await(5, TimeUnit.SECONDS);
                        return triggerStore.recoverRunningExecution(running.getExecutionId(), TEST_CODE,
                                "R", PLANNED, "テスト: 同時 2", "RECOVERY");
                    }));
            start.countDown();
            ScheduledTriggerStore.RecoveryResult first = futures.get(0).get(20, TimeUnit.SECONDS);
            ScheduledTriggerStore.RecoveryResult second = futures.get(1).get(20, TimeUnit.SECONDS);

            List<BatchExecutionEntity> retries = executionMapper.findRecent(TEST_CODE, 50).stream()
                    .filter(row -> running.getExecutionId().equals(row.getSourceExecutionId()))
                    .toList();
            assertThat(retries).hasSize(1);   // やり直しは 1 つだけ
            Long retryId = retries.get(0).getExecutionId();
            // どちらの呼び出しも**同じ 1 件**を指す
            assertThat(List.of(first.retryExecutionId(), second.retryExecutionId()))
                    .allMatch(id -> id == null || id.equals(retryId));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("コミット後・投入前に落ちても、やり直しの記録は残るので次の復旧が拾える")
    void retryRowSurvivesBeforeBeingDispatched() {
        BatchExecutionEntity running = insertRunning();
        ScheduledTriggerStore.RecoveryResult result = triggerStore.recoverRunningExecution(
                running.getExecutionId(), TEST_CODE, "R", PLANNED, "テスト: 投入前に落ちた", "RECOVERY");
        Long retryId = result.retryExecutionId();

        // 何も投入しないまま（＝落ちた）時間が進んだとして、**次のプロセス**の復旧が探す形で読める。
        // 復旧は「起動識別子が自分と違う行」を遺留として引くので、別の識別子で引いて確かめる
        List<BatchExecutionEntity> leftovers =
                executionMapper.findLeftoversExceptRunId("20260920T999999-ffffffff");
        assertThat(leftovers.stream().map(BatchExecutionEntity::getExecutionId)).contains(retryId);
        assertThat(executionMapper.findById(retryId).getStatus())
                .isEqualTo(BatchExecutionStatus.QUEUED.name());
        // 逆に、**同じ識別子**（＝作ったプロセス自身）では遺留に出ない
        String ownRunId = executionMapper.findById(retryId).getRunId();
        assertThat(ownRunId).isNotNull();
        assertThat(executionMapper.findLeftoversExceptRunId(ownRunId)
                .stream().map(BatchExecutionEntity::getExecutionId)).doesNotContain(retryId);
    }

    @Test
    @DisplayName("既に終わっている実行はやり直しを作らない（結果が分かっているものを再実行しない）")
    void finishedExecutionIsNotReverted() {
        BatchExecutionEntity running = insertRunning();
        executionMapper.markFinished(running.getExecutionId(), BatchExecutionStatus.SUCCESS.name(),
                "テスト: 正常終了", null, 10L);

        ScheduledTriggerStore.RecoveryResult result = triggerStore.recoverRunningExecution(
                running.getExecutionId(), TEST_CODE, "R", PLANNED, "テスト: 既に終了", "RECOVERY");

        assertThat(result.retryExecutionId()).isNull();
        assertThat(executionMapper.findById(running.getExecutionId()).getStatus())
                .isEqualTo(BatchExecutionStatus.SUCCESS.name());
        assertThat(executionMapper.findBySourceExecutionId(running.getExecutionId())).isNull();
    }
}
