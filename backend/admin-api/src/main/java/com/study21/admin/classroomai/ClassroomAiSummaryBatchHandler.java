package com.study21.admin.classroomai;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchTaskHandler;
import org.springframework.stereotype.Component;

/**
 * batC62「授業ノート 最終まとめ生成」のハンドラ。
 *
 * <p>実体は {@link ClassroomAiNoteStep}（**AI を呼ぶ**工程。種別 FINAL）。終了時に 1 回だけ走る。</p>
 */
@Component
public class ClassroomAiSummaryBatchHandler implements BatchTaskHandler {

    public static final String BATCH_CODE = "batC62";

    private final ClassroomAiNoteStep step;

    public ClassroomAiSummaryBatchHandler(ClassroomAiNoteStep step) {
        this.step = step;
    }

    @Override
    public String taskCode() {
        return BATCH_CODE;
    }

    @Override
    public String execute(BatchExecutionEntity execution) {
        return String.valueOf(step.run(execution, "FINAL").get("message"));
    }
}
