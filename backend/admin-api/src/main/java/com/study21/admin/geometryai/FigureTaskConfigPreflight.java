package com.study21.admin.geometryai;

import com.study21.admin.batch.BatchSettingsPreflight;
import com.study21.admin.batch.BatchTaskRegistry;
import com.study21.admin.geometryai.processor.FigureProcessor;
import com.study21.admin.geometryai.processor.FigureProcessorRegistry;
import com.study21.admin.setting.SettingRequirement;
import com.study21.common.core.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 生図のバッチ（batC51-A〜D）の**実行前の設定検証**。
 *
 * <p>検証するのは「いまの設定」ではなく、**その要求に固定した設定**（スナップショット）。
 * 提出後に共用プロンプトを消したり書き換えたりしても、並んでいるタスクは受付時の条件のまま走る。
 * 固定が無い歴史的な要求のときだけ、いまの設定を読んで検証する（実行する工程が
 * AI を呼ぶ前にその内容を固定する）。</p>
 *
 * <p>固定した設定が壊れている・足りない・モードが食い違うときは {@link AiFigureConfigException} を
 * そのまま投げる（{@code BatchServiceImpl} は実行記録を作らずに拒否し、流水線が要求行へ
 * `FAILED` と理由を書く）。**黙っていまの設定へは切り替えない。**</p>
 */
@Component
public class FigureTaskConfigPreflight implements BatchSettingsPreflight {

    private static final Logger log = LoggerFactory.getLogger(FigureTaskConfigPreflight.class);

    private final BatchTaskRegistry taskRegistry;
    private final FigureProcessorRegistry processorRegistry;
    private final GeometryAiRequestMapper requestMapper;
    private final AiFigureTaskConfigResolver taskConfigResolver;

    public FigureTaskConfigPreflight(BatchTaskRegistry taskRegistry,
                                     FigureProcessorRegistry processorRegistry,
                                     GeometryAiRequestMapper requestMapper,
                                     AiFigureTaskConfigResolver taskConfigResolver) {
        this.taskRegistry = taskRegistry;
        this.processorRegistry = processorRegistry;
        this.requestMapper = requestMapper;
        this.taskConfigResolver = taskConfigResolver;
    }

    @Override
    public boolean supports(String batchCode) {
        if (batchCode == null) {
            return false;
        }
        // 定義に「このバッチは AI 生図の生成か」を書かせず、モードのバッチコードから判断する
        // （モード A〜D が増えても、ここを直し忘れない）
        return FigureProcessorRegistry.modeOfTaskCode(batchCode).isPresent()
                && taskRegistry.findByCode(batchCode) != null;
    }

    @Override
    public void verify(String batchCode, List<SettingRequirement> requiredSettings, String requestPayloadJson) {
        Long requestId = AiFigurePayload.requestId(requestPayloadJson);
        FigureProcessor processor = FigureProcessorRegistry.modeOfTaskCode(batchCode)
                .map(processorRegistry::of)
                .orElse(null);
        if (processor == null) {
            return;
        }
        if (requestId == null) {
            // どの要求を処理するかはハンドラが決める（未処理の最古の 1 件）。
            // その要求の設定はハンドラの入口（同じ Resolver）が同じ規則で検証する
            log.debug("AI 生図の要求が指定されていないため、実行前の設定検証はハンドラに任せます。batchCode={}",
                    batchCode);
            return;
        }
        GeometryAiRequestEntity entity = requestMapper.findById(requestId);
        if (entity == null) {
            throw new ValidationException("AI 生図の要求が見つかりません: " + requestId);
        }
        // 固定した設定（無ければいまの設定）を解決して検証する。壊れていれば例外で止まる
        AiFigureTaskConfigResolver.AiFigureTaskConfig taskConfig =
                taskConfigResolver.resolveConfig(processor, entity);
        log.debug("AI 生図の実行前の設定検証に通りました。batchCode={} requestId={} fromSnapshot={} revision={}",
                batchCode, requestId, taskConfig.fromSnapshot(), taskConfig.config().revision());
    }
}
