package com.study21.admin.geometryai;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchTaskHandler;
import org.springframework.stereotype.Component;

/**
 * batC52「AI画図助手 生成」のハンドラ。
 *
 * <p>実体は {@link AiAssistGenerateStep}（**AI を呼ぶ**工程）。番号 batC52 は旧「AI 生図 AI 生成」
 * から**番号を再利用**した（旧 batC52 は batC51 に改名済み）。画図助手の依頼は「その場で即時実行」する
 * （`AiAssistPipelineService` が同期で 1 回だけ呼ぶ。待たない・並ばない）。</p>
 */
@Component
public class AiAssistGenerateBatchHandler implements BatchTaskHandler {

    public static final String BATCH_CODE = "batC52";

    private final AiAssistGenerateStep step;

    public AiAssistGenerateBatchHandler(AiAssistGenerateStep step) {
        this.step = step;
    }

    @Override
    public String taskCode() {
        return BATCH_CODE;
    }

    @Override
    public String execute(BatchExecutionEntity execution) {
        return String.valueOf(step.run(execution).get("message"));
    }
}
