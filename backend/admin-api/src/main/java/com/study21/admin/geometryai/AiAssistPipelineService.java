package com.study21.admin.geometryai;

import com.study21.admin.batch.BatchService;
import com.study21.common.core.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 画図助手（batC52）の**起動の薄い入口**（画面は依頼作成後に 1 回だけ呼ぶ）。
 *
 * <p>**オンデマンド即時実行**：依頼（`生成状態=PENDING`）ができた直後に、画面（ブラウザ）がこの入口を
 * 1 回叩き、**その場で batC52 を同期実行して最後まで進める**。定期ポーリングや后台队列に入れない。
 * {@code ?async=true} の非同期 202 も付けない（「待ち」を作らないため）。PENDING を拾うモードは
 * バッチ管理画面から単体再実行するときのフォールバックで、通常の経路では使わない。</p>
 *
 * <p>user-api と admin-api はサービス間で API を呼ばない方針なので、user-api はこの入口を叩かず、
 * トリガは画面（ブラウザ）が行う（AI 生図と同じ流儀）。</p>
 */
@Service
public class AiAssistPipelineService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistPipelineService.class);

    /** 実行するバッチ（AI 画図助手 生成）。 */
    private static final String BATCH_CODE = "batC52";
    private static final String OPERATOR_FALLBACK = "geometry-assist";

    private final BatchService batchService;
    private final GeometryAiAssistMapper assistMapper;

    public AiAssistPipelineService(BatchService batchService, GeometryAiAssistMapper assistMapper) {
        this.batchService = batchService;
        this.assistMapper = assistMapper;
    }

    /** 1 件の依頼を batC52 で同期実行し、最後まで進めて結果を返す。 */
    public Map<String, Object> run(long assistId, String operator) {
        GeometryAiAssistEntity assist = assistMapper.findById(assistId);
        if (assist == null) {
            throw new NotFoundException("AI 画図助手の依頼が見つかりません: " + assistId);
        }
        String operatorCode = operator == null || operator.isBlank() ? OPERATOR_FALLBACK : operator.trim();

        Map<String, Object> step = batchService.rerunStep(BATCH_CODE, operatorCode, AiAssistPayload.of(assistId));
        GeometryAiAssistEntity saved = assistMapper.findById(assistId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assistId", assistId);
        result.put("status", saved == null ? null : saved.getStatus());
        result.put("step", step);
        log.info("geometry assist pipeline finished. assistId={} status={}",
                assistId, saved == null ? null : saved.getStatus());
        return result;
    }
}
