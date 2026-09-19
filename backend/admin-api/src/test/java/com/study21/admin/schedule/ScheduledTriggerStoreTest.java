package com.study21.admin.schedule;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchExecutionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 計画実行点の確保（{@link ScheduledTriggerStore}）と**トランザクション**の関係。
 *
 * <p>利用者の指摘: トランザクションが巻き戻ったのに「確保した」とメモリに残すと、
 * その計画実行点は二度と確保されず**実行が抜ける**。メモリの早見は**コミット後にだけ**更新する。</p>
 */
class ScheduledTriggerStoreTest {

    /** 何もしないトランザクション管理（実 DB を使わずに commit/rollback の順序だけ見る）。 */
    private static final class NoopTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    private SchedulePlanMapper planMapper;
    private BatchExecutionMapper executionMapper;
    private ScheduledTriggerStore store;

    private static final String TASK = "batR03";
    private static final LocalDateTime PLANNED = LocalDateTime.of(2026, 9, 19, 23, 30);

    @BeforeEach
    void setUp() {
        planMapper = mock(SchedulePlanMapper.class);
        executionMapper = mock(BatchExecutionMapper.class);
        PlatformTransactionManager transactionManager = new NoopTransactionManager();
        store = new ScheduledTriggerStore(planMapper, executionMapper, new ScheduleRuleCatalog(),
                transactionManager);
        when(planMapper.claim(anyString(), anyString())).thenReturn("2026-09-19 23:30:00");
    }

    @Test
    @DisplayName("確保できたら、コミット後にメモリの早見が進む（実行記録も作る）")
    void memoryIsUpdatedAfterCommit() {
        when(executionMapper.insert(any(BatchExecutionEntity.class))).thenAnswer(invocation -> {
            BatchExecutionEntity entity = invocation.getArgument(0);
            entity.setExecutionId(700L);
            return 1;
        });

        Long executionId = store.claimAndRecord(TASK, PLANNED, "R");

        assertThat(executionId).isEqualTo(700L);
        assertThat(store.lastClaimedAt(TASK)).isEqualTo(PLANNED);
        assertThat(store.alreadyClaimed(TASK, PLANNED)).isTrue();
        verify(planMapper).attachExecution(TASK, 700L);
    }

    @Test
    @DisplayName("実行記録の作成で失敗したら、メモリを「確保済み」にしない（その点を実行し直せる）")
    void rolledBackClaimIsNotRememberedInMemory() {
        when(executionMapper.insert(any(BatchExecutionEntity.class)))
                .thenThrow(new IllegalStateException("DB が落ちた"));

        assertThatThrownBy(() -> store.claimAndRecord(TASK, PLANNED, "R"))
                .isInstanceOf(IllegalStateException.class);

        // メモリは進んでいない → 次の検査で同じ点をもう一度確保しようとする（ここが従来の不具合）
        assertThat(store.lastClaimedAt(TASK)).isNull();
        assertThat(store.alreadyClaimed(TASK, PLANNED)).isFalse();

        // 復旧してもう一度確保できる（点が失われない）
        when(executionMapper.insert(any(BatchExecutionEntity.class))).thenAnswer(invocation -> {
            BatchExecutionEntity entity = invocation.getArgument(0);
            entity.setExecutionId(701L);
            return 1;
        });
        assertThat(store.claimAndRecord(TASK, PLANNED, "R")).isEqualTo(701L);
        verify(planMapper, times(2)).claim(anyString(), anyString());
    }

    @Test
    @DisplayName("実行せずに計画を進める場合も、コミット後にだけメモリを進める")
    void advanceWithoutRunUpdatesMemoryAfterCommit() {
        assertThat(store.advanceWithoutRun(TASK, PLANNED)).isTrue();
        assertThat(store.lastClaimedAt(TASK)).isEqualTo(PLANNED);
        // 既に同じ点まで進んでいれば DB を叩かない
        assertThat(store.advanceWithoutRun(TASK, PLANNED)).isFalse();
        assertThat(store.advanceWithoutRun(TASK, PLANNED.minusMinutes(5))).isFalse();
        verify(planMapper, times(1)).claim(anyString(), anyString());
    }

    @Test
    @DisplayName("やり直しの実行記録は計画を触らずに作る（再起動の復旧で使う）")
    void retryRowDoesNotTouchThePlan() {
        when(executionMapper.insert(any(BatchExecutionEntity.class))).thenAnswer(invocation -> {
            BatchExecutionEntity entity = invocation.getArgument(0);
            entity.setExecutionId(702L);
            return 1;
        });

        Long retryId = store.insertRetryRow(TASK, PLANNED, "R", BatchExecutionRecovery.RETRY_CODE);

        assertThat(retryId).isEqualTo(702L);
        verify(planMapper, times(0)).claim(anyString(), anyString());
        verify(planMapper).attachExecution(TASK, 702L);
    }

    @Test
    @DisplayName("起動時に計画状態を読む（読めないときは空のままで動く）")
    void loadsPlanStateOnStartup() {
        when(planMapper.findPlans(any())).thenReturn(List.of(Map.of(
                "batchCode", TASK,
                "lastPlannedAt", java.sql.Timestamp.valueOf(PLANNED))));

        store.loadPlansOnStartup();

        assertThat(store.lastClaimedAt(TASK)).isEqualTo(PLANNED);
    }
}
