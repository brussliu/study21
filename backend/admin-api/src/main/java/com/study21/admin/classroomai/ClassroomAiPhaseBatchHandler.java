package com.study21.admin.classroomai;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchTaskHandler;
import org.springframework.stereotype.Component;

/**
 * batC61「授業ノート フェーズ分析」のハンドラ。
 *
 * <p>実体は {@link ClassroomAiNoteStep}（**AI を呼ぶ**工程。種別 PHASE）。</p>
 */
@Component
public class ClassroomAiPhaseBatchHandler implements BatchTaskHandler {

    public static final String BATCH_CODE = "batC61";

    private final ClassroomAiNoteStep step;

    public ClassroomAiPhaseBatchHandler(ClassroomAiNoteStep step) {
        this.step = step;
    }

    @Override
    public String taskCode() {
        return BATCH_CODE;
    }

    @Override
    public String execute(BatchExecutionEntity execution) {
        return String.valueOf(step.run(execution, "PHASE").get("message"));
    }
}
