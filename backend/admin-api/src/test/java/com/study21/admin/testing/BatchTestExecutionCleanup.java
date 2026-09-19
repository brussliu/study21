package com.study21.admin.testing;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchExecutionMapper;
import com.study21.admin.batch.BatchExecutionStatus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * **テストが自分で作った実行記録だけ**を後始末する（テスト専用の道具）。
 *
 * <p>「最近の N 件をまとめて閉じる」ような片付け方はしない
 * （他のテストや業務が作った記録を書き換えてしまう）。テストは記録を作った直後に
 * {@link #register(long)} で自分のものとして登録し、{@link #closeAll()} は
 * **登録された ID と、そこから派生したやり直し（元実行ID が自分のもの）だけ**を閉じる。</p>
 *
 * <p>未完了（待機中・実行中）のまま残すと、次の起動の復旧や他のテストが拾ってしまうので、
 * そこだけをスキップとして閉じる（**終了済みの記録には触らない**）。</p>
 */
public final class BatchTestExecutionCleanup {

    private static final String CLEANUP_MESSAGE = "テストの後始末（このテストが作った記録だけを閉じる）";

    private final BatchExecutionMapper executionMapper;
    private final Set<Long> ownedIds = new LinkedHashSet<>();

    public BatchTestExecutionCleanup(BatchExecutionMapper executionMapper) {
        this.executionMapper = executionMapper;
    }

    /** このテストが作った実行記録として登録する。 */
    public void register(Long executionId) {
        if (executionId != null) {
            ownedIds.add(executionId);
        }
    }

    /** このテストが作った実行記録として登録する（エンティティから）。 */
    public void register(BatchExecutionEntity row) {
        if (row != null) {
            register(row.getExecutionId());
        }
    }

    /** いま登録されている ID（アサーション用）。 */
    public List<Long> owned() {
        return List.copyOf(ownedIds);
    }

    /**
     * 登録された記録（と、そこから派生したやり直し）のうち**未完了のものだけ**を閉じる。
     *
     * <p>アサーションが失敗したときも実行できるよう、{@code @AfterEach} から呼ぶ。</p>
     */
    public void closeAll() {
        for (Long executionId : new ArrayList<>(ownedIds)) {
            collectDerived(executionId);
        }
        for (Long executionId : new ArrayList<>(ownedIds)) {
            closeIfUnfinished(executionId);
        }
    }

    /** 元実行ID が自分のものをつないで、やり直しも自分のものとして登録する。 */
    private void collectDerived(long executionId) {
        BatchExecutionEntity derived = executionMapper.findBySourceExecutionId(executionId);
        while (derived != null && ownedIds.add(derived.getExecutionId())) {
            derived = executionMapper.findBySourceExecutionId(derived.getExecutionId());
        }
    }

    private void closeIfUnfinished(long executionId) {
        BatchExecutionEntity row = executionMapper.findById(executionId);
        if (row == null) {
            return;
        }
        if (BatchExecutionStatus.QUEUED.name().equals(row.getStatus())
                || BatchExecutionStatus.RUNNING.name().equals(row.getStatus())) {
            executionMapper.markFinished(executionId, BatchExecutionStatus.SKIPPED.name(),
                    CLEANUP_MESSAGE, null, 0L);
        }
    }
}
