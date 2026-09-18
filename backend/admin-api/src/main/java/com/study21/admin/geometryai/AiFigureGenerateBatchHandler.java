package com.study21.admin.geometryai;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchTaskHandler;
import org.springframework.stereotype.Component;

/**
 * batC51「AI生図 AI生成（GeoGebra コマンド生成）」のハンドラ。
 *
 * <p>実体は {@link AiFigureGenerateStep}（**AI を呼ぶ**工程）。AI 生図の流水線は
 * 「前処理（通常コード）→ batC51（AI）→ 検証（通常コード）」の 3 工程で、バッチとして
 * 実行するのはこの batC51 だけ（前処理・検証はバッチではない）。</p>
 */
@Component
public class AiFigureGenerateBatchHandler implements BatchTaskHandler {

    public static final String BATCH_CODE = "batC51";

    private final AiFigureGenerateStep step;

    public AiFigureGenerateBatchHandler(AiFigureGenerateStep step) {
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
