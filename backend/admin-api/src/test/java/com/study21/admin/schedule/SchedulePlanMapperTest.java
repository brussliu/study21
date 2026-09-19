package com.study21.admin.schedule;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchExecutionMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 実 DB に対する**計画実行点の確保（claim）**の検証。
 *
 * <p>「同じ計画実行点は 1 回だけ」を保証するのはこの SQL なので、実 DB の
 * {@code INSERT ... ON CONFLICT DO UPDATE ... WHERE} の挙動を固定する
 * （メモリのロックでは保証しない）。既存の実行履歴には同じ予定時刻の重複行があるため、
 * **実行履歴に一意制約を張るのではなく専用の表**を使っていることもここで確かめる。</p>
 *
 * <p>テストはロールバックするので DB は汚れない。</p>
 */
@SpringBootTest
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class SchedulePlanMapperTest {

    /**
     * 実データと衝突しないコード（テストはロールバックする）。
     *
     * <p>**クラスごとに一意**にする。同じ実 DB に対して別のビルド（他のセッションや同時実行）が
     * 同じテストを走らせても、確保（claim）が互いの行にぶつからないようにするため。</p>
     */
    private static final String TEST_CODE = "batT" + Long.toString(System.nanoTime() % 1_000_000_000L);

    @Autowired
    private SchedulePlanMapper planMapper;

    @Autowired
    private BatchExecutionMapper executionMapper;

    /**
     * スケジュール状態の表がまだ無い環境（移行前）ではこのテストを飛ばす
     * （DDL は {@code database/バッチ/TBL_BAT_スケジュール状態情報.sql} を先に実行する決まり）。
     */
    @BeforeAll
    void requireScheduleTable() {
        try {
            planMapper.findPlans(List.of("__probe__"));
        } catch (RuntimeException cause) {
            Assumptions.abort("BAT_スケジュール状態情報 がまだありません（"
                    + "database/バッチ/TBL_BAT_スケジュール状態情報.sql を先に実行してください）: "
                    + cause.getMessage());
        }
    }

    @Test
    void firstClaimSucceedsAndTheSamePointIsRejected() {
        String planned = "2026-09-19T23:30:00";

        assertThat(planMapper.claim(TEST_CODE, planned)).isNotNull();
        // 同じ計画実行点は 2 回確保できない（再起動・多重起動・ポーリングのゆらぎ対策）
        assertThat(planMapper.claim(TEST_CODE, planned)).isNull();
    }

    @Test
    void olderPointIsRejectedAndNewerPointIsAccepted() {
        assertThat(planMapper.claim(TEST_CODE, "2026-09-19T23:30:00")).isNotNull();
        // 古い点は確保できない（遅れて来た検査が過去の点を実行しない）
        assertThat(planMapper.claim(TEST_CODE, "2026-09-19T23:25:00")).isNull();
        // 先の点は確保できる
        assertThat(planMapper.claim(TEST_CODE, "2026-09-19T23:35:00")).isNotNull();

        Map<String, Object> plan = planMapper.findPlan(TEST_CODE);
        assertThat(plan).isNotNull();
        assertThat(String.valueOf(plan.get("lastPlannedAt"))).startsWith("2026-09-19 23:35");
    }

    @Test
    void planIsListedAndExecutionIdIsAttached() {
        planMapper.claim(TEST_CODE, "2026-09-20T06:30:00");
        planMapper.attachExecution(TEST_CODE, 987654L);

        List<Map<String, Object>> plans = planMapper.findPlans(List.of(TEST_CODE, "batR03"));
        Map<String, Object> row = plans.stream()
                .filter(entry -> TEST_CODE.equals(entry.get("batchCode")))
                .findFirst()
                .orElseThrow();
        assertThat(String.valueOf(row.get("lastExecutionId"))).isEqualTo("987654");

        // 計画の無いタスクは返らない（一覧で 0 件を許す）
        assertThat(plans.stream().map(entry -> entry.get("batchCode")))
                .doesNotContain("batL02");
    }

    @Test
    void unfinishedExecutionsAreClosedOnStartup() {
        // 再起動の復旧: 未完了（待機中・実行中）の実行を失敗として閉じる
        // （残すと、そのタスクの次の実行が「前回が実行中」と見なされて永久に走らない）
        BatchExecutionEntity record = new BatchExecutionEntity();
        record.setBatchCode(TEST_CODE);
        record.setBatchType("R");
        record.setTriggerType("R");
        record.setStatus(com.study21.admin.batch.BatchExecutionStatus.QUEUED.name());
        record.setRequestedByCode("SCHEDULER");
        record.setMessage("E2E: スケジュール実行");
        executionMapper.insert(record);

        int closed = executionMapper.markUnfinishedAsFailed("サービス再起動のため中断しました（自動では再開しません）。");
        assertThat(closed).isGreaterThanOrEqualTo(1);

        BatchExecutionEntity after = executionMapper.findById(record.getExecutionId());
        assertThat(after.getStatus()).isEqualTo(com.study21.admin.batch.BatchExecutionStatus.FAILED.name());
        assertThat(after.getMessage()).contains("サービス再起動のため中断しました");
        // 未完了の実行が残っていない（次の実行がブロックされない）
        assertThat(executionMapper.findRunningByBatchCodeExcept(TEST_CODE, record.getExecutionId())).isNull();
    }
}
