package com.study21.admin.testing;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchTaskHandler;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * **テスト専用**の業務ハンドラの代役。
 *
 * <p>実業務（動画取込・AI 分析・ネット切替・プロキシ起動）を**一切動かさず**に、
 * 「呼ばれた回数」と「呼ばれ始めたこと」だけをテストから観察できるようにする。
 * {@code BatchServiceImpl} はハンドラを**生成時に**タスクコードで引くため、
 * テストでは Spring の Bean を差し替えるのではなく、
 * **この代役だけを持つ {@code BatchServiceImpl} をテストが組み立てて**使う
 * （＝実ハンドラが混ざらない）。</p>
 */
public final class StubBatchTaskHandler implements BatchTaskHandler {

    private final String taskCode;
    private final AtomicInteger calls = new AtomicInteger();
    /** 実行された実行ID（どの記録が走ったかをテストから確かめる）。 */
    private final Queue<Long> executedIds = new ConcurrentLinkedQueue<>();
    private final CountDownLatch started;
    private final String summary;

    public StubBatchTaskHandler(String taskCode, String summary) {
        this(taskCode, summary, new CountDownLatch(1));
    }

    public StubBatchTaskHandler(String taskCode, String summary, CountDownLatch started) {
        this.taskCode = taskCode;
        this.summary = summary;
        this.started = started;
    }

    @Override
    public String taskCode() {
        return taskCode;
    }

    @Override
    public String execute(BatchExecutionEntity execution) {
        calls.incrementAndGet();
        executedIds.add(execution.getExecutionId());
        started.countDown();
        return summary;
    }

    /** 実行された実行ID（呼ばれた順）。 */
    public List<Long> executedIds() {
        return List.copyOf(executedIds);
    }

    /** 呼ばれた回数。 */
    public int calls() {
        return calls.get();
    }

    /** 「呼ばれ始めた」ことを待つためのラッチ。 */
    public CountDownLatch started() {
        return started;
    }
}
