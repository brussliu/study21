package com.study21.admin.geometryai;

import com.study21.admin.batch.BatchService;
import com.study21.admin.geometryai.dto.FigureMode;
import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 生図の**起動の薄い入口**（画面は 1 回だけ呼ぶ）。
 *
 * <p>利用者の指示で、流水線は「前処理（通常コード）→ **AI 生成（モード別のバッチ）** → 検証（通常コード）」の
 * 3 工程になった。**バッチとして実行し、`BAT_バッチ実行履歴情報` に残るのは AI 生成の 1 行だけ**
 * （モード A〜D で batC51-A〜batC51-D。歴史的な要求は batC51＝モード A として実行する）。
 * 前処理・検証はバッチではなく、このサービスの通常コードとして直接実行する（履歴には残らない）。</p>
 *
 * <p>失敗した工程で止める。`要求内容` に {@code {"aiRequestId": N, "requestNo": "...", "stage": "GENERATE"}}
 * を入れるので、どの実行がどの要求を処理したかが分かる（設計 §2.2）。</p>
 *
 * <p><strong>止まったら必ず要求行へ理由を書く</strong>（{@link #ensureFailed}）。設定の不足・
 * プロンプトの変数展開の失敗・接続の解決の失敗などは、工程の途中で例外になることがある。
 * そのまま処理中の状態（`PREPROCESSED` など）で残すと、働き手が**同じ行を拾い続けて
 * 後ろの要求がまったく進まなくなる**（お金も時間も無駄になる）。</p>
 *
 * <p>**画面から見れば 1 操作 = 1 ボタン**のまま。AI の再課金を最小化するため、batC51 だけを
 * 単体で【再実行】できる。</p>
 */
@Service
public class AiFigurePipelineService {

    private static final Logger log = LoggerFactory.getLogger(AiFigurePipelineService.class);

    /** バッチとして実行する工程（AI 生成だけ）。モードごとに 1 つ（batC51-A〜D）。 */
    private static final String LEGACY_BATCH_CODE = "batC51";

    private static final String OPERATOR_FALLBACK = "geometry-ai";

    /** 工程が想定外で止まったときのエラーコード（理由は エラーメッセージ に入る）。 */
    private static final String UNEXPECTED_CODE = "STAGE_FAILED";

    private final BatchService batchService;
    private final GeometryAiRequestMapper requestMapper;
    private final GeometryAiRequestRecorder recorder;
    private final AiFigurePreprocessStep preprocessStep;
    private final AiFigureValidateStep validateStep;

    public AiFigurePipelineService(BatchService batchService,
                                   GeometryAiRequestMapper requestMapper,
                                   GeometryAiRequestRecorder recorder,
                                   AiFigurePreprocessStep preprocessStep,
                                   AiFigureValidateStep validateStep) {
        this.batchService = batchService;
        this.requestMapper = requestMapper;
        this.recorder = recorder;
        this.preprocessStep = preprocessStep;
        this.validateStep = validateStep;
    }

    /**
     * 1 件の AI 生図を前処理（コード）→ batC51（AI・バッチ）→ 検証（コード）の順に進める。
     *
     * @return バッチで実行した工程の結果（`steps`。batC51 の 1 件だけ）と、止まった工程
     *         （`stoppedAt`。PREPROCESS / GENERATE / VALIDATE。最後まで進めば null）
     */
    public Map<String, Object> run(long aiRequestId, String operator) {
        GeometryAiRequestEntity entity = requestMapper.findById(aiRequestId);
        if (entity == null) {
            throw new NotFoundException("AI 生図の要求が見つかりません: " + aiRequestId);
        }
        String operatorCode = operator == null || operator.isBlank() ? OPERATOR_FALLBACK : operator.trim();

        List<Map<String, Object>> steps = new ArrayList<>();
        String stoppedAt = null;

        // 1. 前処理（バッチではない。通常コードで直接実行）
        String reason = null;
        try {
            preprocessStep.run(aiRequestId);
        } catch (AiFigurePreprocessStep.GeometryAiStepException cause) {
            stoppedAt = "PREPROCESS";
            reason = cause.getMessage();
        } catch (Exception cause) {
            stoppedAt = "PREPROCESS";
            reason = messageOf(cause);
        }
        if (stoppedAt != null) {
            ensureFailed(aiRequestId, "PREPROCESS", reason);
        }

        // 2. AI 生成（モード別のバッチ。唯一のバッチ工程）
        if (stoppedAt == null) {
            String batchCode = FigureMode.of(entity.getMode()).orElse(FigureMode.A).taskCode();
            boolean writeFailure = true;
            try {
                Map<String, Object> stepResult = batchService.rerunStep(batchCode, operatorCode,
                        AiFigurePayload.of(aiRequestId, entity.getRequestNo(), "GENERATE"));
                steps.add(stepResult);
                if (!Boolean.TRUE.equals(stepResult.get("success"))) {
                    stoppedAt = "GENERATE";
                    reason = stepResult.get("message") == null ? null : String.valueOf(stepResult.get("message"));
                }
            } catch (ConflictException cause) {
                // 「既に実行中」は失敗ではない（走っている実行がこの要求を進める）。
                // FAILED を書くと、成功した結果をあとから失敗で上書きしてしまう
                stoppedAt = "GENERATE";
                reason = cause.getMessage();
                writeFailure = false;
            } catch (Exception cause) {
                stoppedAt = "GENERATE";
                reason = messageOf(cause);
            }
            if (stoppedAt != null) {
                if (writeFailure) {
                    ensureFailed(aiRequestId, "GENERATE", reason);
                } else {
                    log.info("AI 生図の生成は他の実行が担当しています。requestId={} reason={}", aiRequestId, reason);
                }
            }
        }

        // 3. 検証（バッチではない。通常コードで直接実行）
        if (stoppedAt == null) {
            try {
                validateStep.run(aiRequestId);
            } catch (AiFigurePreprocessStep.GeometryAiStepException cause) {
                stoppedAt = "VALIDATE";
                reason = cause.getMessage();
            } catch (Exception cause) {
                stoppedAt = "VALIDATE";
                reason = messageOf(cause);
            }
            if (stoppedAt != null) {
                ensureFailed(aiRequestId, "VALIDATE", reason);
            }
        }

        GeometryAiRequestEntity saved = requestMapper.findById(aiRequestId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aiRequestId", aiRequestId);
        result.put("requestNo", entity.getRequestNo());
        result.put("status", saved == null ? null : saved.getStatusCode());
        result.put("stoppedAt", stoppedAt);
        result.put("steps", steps);
        result.put("completed", stoppedAt == null);
        log.info("ai figure pipeline finished. requestId={} status={} stoppedAt={}",
                aiRequestId, saved == null ? null : saved.getStatusCode(), stoppedAt);
        return result;
    }

    /**
     * **検証だけ**を進める（AI を呼び直さない）。
     *
     * <p>`GENERATED`（生成済み・検証待ち）や、落ちたままの `VALIDATING` を働き手が拾ったときに使う。
     * ここで生成からやり直すと **AI をもう一度呼んでしまう**（お金が二重にかかる）。</p>
     */
    public Map<String, Object> resumeValidation(long aiRequestId, String operator) {
        GeometryAiRequestEntity entity = requestMapper.findById(aiRequestId);
        if (entity == null) {
            throw new NotFoundException("AI 生図の要求が見つかりません: " + aiRequestId);
        }
        List<Map<String, Object>> steps = new ArrayList<>();
        String stoppedAt = null;
        String reason = null;
        try {
            validateStep.run(aiRequestId);
        } catch (AiFigurePreprocessStep.GeometryAiStepException cause) {
            stoppedAt = "VALIDATE";
            reason = cause.getMessage();
        } catch (Exception cause) {
            stoppedAt = "VALIDATE";
            reason = messageOf(cause);
        }
        if (stoppedAt != null) {
            ensureFailed(aiRequestId, "VALIDATE", reason);
        }
        GeometryAiRequestEntity saved = requestMapper.findById(aiRequestId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aiRequestId", aiRequestId);
        result.put("requestNo", entity.getRequestNo());
        result.put("status", saved == null ? null : saved.getStatusCode());
        result.put("stoppedAt", stoppedAt);
        result.put("steps", steps);
        result.put("completed", stoppedAt == null);
        result.put("skippedPreprocessAndGenerate", true);
        log.info("ai figure validation resumed. requestId={} status={} stoppedAt={}",
                aiRequestId, saved == null ? null : saved.getStatusCode(), stoppedAt);
        return result;
    }

    /**
     * 止まった工程を要求行へ書く（**処理中のまま残さない**）。
     *
     * <p>既に `FAILED`（工程の中で理由を書いた）・`NEEDS_INPUT`・`READY`・`REGISTERED`・`CANCELLED`
     * なら何もしない。それ以外（`QUEUED` / `PREPROCESSING` / `PREPROCESSED` / `GENERATING` /
     * `GENERATED` / `VALIDATING`）は**失敗として理由を残す**。これで働き手が同じ行を拾い続けず、
     * 後ろに並んだ要求が進む。</p>
     */
    private void ensureFailed(long aiRequestId, String stage, String reason) {
        GeometryAiRequestEntity row = requestMapper.findById(aiRequestId);
        if (row == null) {
            return;
        }
        String status = row.getStatusCode();
        if ("FAILED".equals(status) || "CANCELLED".equals(status) || "READY".equals(status)
                || "REGISTERED".equals(status) || "NEEDS_INPUT".equals(status)) {
            return;
        }
        String message = reason == null || reason.isBlank()
                ? "AI 生図の " + stage + " 工程で失敗しました（理由が取得できませんでした）。"
                : reason;
        row.setFailedStage(stage);
        row.setErrorCode(UNEXPECTED_CODE);
        row.setErrorMessage(message);
        try {
            recorder.updateFailed(row);
            log.warn("AI 生図の工程で止まったため FAILED にしました。requestId={} stage={} reason={}",
                    aiRequestId, stage, message);
        } catch (Exception cause) {
            // ここで落ちても働き手は止めない（次に拾ったときに同じ判断をする）
            log.error("AI 生図の失敗を書き込めませんでした。requestId={} stage={}", aiRequestId, stage, cause);
        }
    }

    private static String messageOf(Throwable cause) {
        if (cause == null) {
            return null;
        }
        String message = cause.getMessage();
        if (message != null && !message.isBlank()) {
            return message.strip();
        }
        Throwable root = cause.getCause();
        return root != null && root.getMessage() != null && !root.getMessage().isBlank()
                ? root.getMessage().strip() : cause.getClass().getSimpleName();
    }

    /** 工程ごとの実行 ID・状態・処理時間（`BAT_バッチ実行履歴情報` を `要求内容` で引く）。 */
    public List<Map<String, Object>> status(long aiRequestId) {
        GeometryAiRequestEntity entity = requestMapper.findById(aiRequestId);
        if (entity == null) {
            throw new NotFoundException("AI 生図の要求が見つかりません: " + aiRequestId);
        }
        return batchService.executionsOfRequest(aiRequestId);
    }
}
