package com.study21.admin.geometryai;

import com.study21.admin.setting.SettingRequirement;
import com.study21.admin.setting.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI生図 画像取込・前処理（**AI を呼ばない**・決定的・1〜3 秒）。
 *
 * <p>利用者の指示で、前処理は**バッチではなく通常コード**になった（AI生図の流水線
 * `AiFigurePipelineService` から直接呼ぶ）。バッチとして実行するのは AI 生成（batC51）だけ。</p>
 *
 * <ol>
 *   <li>元画像の存在・形式を確かめ、切り抜き範囲（正規化 0..1）を適用して切り抜く</li>
 *   <li>AI へ送る画像（最大辺 `GEOMETRY_AI_MAX_IMAGE_PIXELS`）へ縮小して保存する</li>
 *   <li>状態を `PREPROCESSED` にし、画像パス・寸法を記録する</li>
 * </ol>
 *
 * <p>**冪等**: 既に `PREPROCESSED` 以降なら何もせず成功で終わる（出力ファイルも同じパスに上書き）。
 * 画像のデコード失敗はリトライしても無駄なので `FAILED(PREPROCESS)` / `IMAGE_DECODE_ERROR` で止める。</p>
 */
@Component
public class AiFigurePreprocessStep {

    private static final Logger log = LoggerFactory.getLogger(AiFigurePreprocessStep.class);

    /** バッチの必須設定（この工程だけの宣言。バッチ一覧の missingSettings に出る）。 */
    public static final List<SettingRequirement> REQUIRED_SETTINGS = List.of(
            new SettingRequirement("GEOMETRY_AI", "GEOMETRY_AI_ENABLED"),
            new SettingRequirement("GEOMETRY_AI", "GEOMETRY_AI_MAX_IMAGE_MB"),
            new SettingRequirement("GEOMETRY_AI", "GEOMETRY_AI_MAX_IMAGE_PIXELS"),
            new SettingRequirement("GEOMETRY_AI", "GEOMETRY_AI_DEFAULT_CROP"),
            new SettingRequirement("GEOMETRY_AI", "GEOMETRY_AI_IMAGE_RETENTION_DAYS"));

    private final GeometryAiRequestMapper requestMapper;
    private final GeometryAiRequestRecorder recorder;
    private final GeometryAiImageStorage storage;
    private final SettingsService settingsService;

    public AiFigurePreprocessStep(GeometryAiRequestMapper requestMapper,
                                  GeometryAiRequestRecorder recorder,
                                  GeometryAiImageStorage storage,
                                  SettingsService settingsService) {
        this.requestMapper = requestMapper;
        this.recorder = recorder;
        this.storage = storage;
        this.settingsService = settingsService;
    }

    /** 1 件を前処理する（処理した要求番号を返す）。 */
    public Map<String, Object> run(long aiRequestId) {
        GeometryAiRequestEntity entity = requestMapper.findById(aiRequestId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (entity == null) {
            result.put("skipped", true);
            result.put("message", "対象がありませんでした（AI 生図が見つかりません）。");
            return result;
        }
        result.put("aiRequestId", entity.getRequestId());
        result.put("requestNo", entity.getRequestNo());

        if (!isPreprocessTarget(entity)) {
            result.put("skipped", true);
            result.put("message", "既に前処理済みのためスキップしました。（" + entity.getRequestNo() + "）");
            return result;
        }

        // 読み取り中にする（段階が画面に見えるように。短時間でも「どこまで進んだか」を残す）
        entity.setPreprocessExecutionId(null);
        recorder.markPreprocessing(entity);

        int maxImageMb = intSetting("GEOMETRY_AI_MAX_IMAGE_MB");
        int maxImagePixels = intSetting("GEOMETRY_AI_MAX_IMAGE_PIXELS");
        if (entity.getOriginalSize() != null && entity.getOriginalSize() > (long) maxImageMb * 1024L * 1024L) {
            fail(entity, "IMAGE_TOO_LARGE", "画像が大きすぎます（" + maxImageMb + " MB まで）。");
        }
        if (storage.read(entity.getOriginalPath(), entity.getOriginalName()) == null) {
            // 置き場のずれ（user-api と admin-api で別の場所）を一目で分かるように絶対パスを出す
            fail(entity, "NO_IMAGE", storage.missingImageMessage(entity.getOriginalPath(), entity.getOriginalName()));
        }

        GeometryAiImageStorage.StoredImage cropped;
        try {
            cropped = storage.cropAndResize(entity.getOriginalPath(), entity.getOriginalName(),
                    GeometryAiImageStorage.croppedName(entity.getRequestNo(), entity.getOriginalName()),
                    entity.getCropX(), entity.getCropY(), entity.getCropW(), entity.getCropH(),
                    maxImagePixels);
        } catch (GeometryAiImageStorage.DecodeException cause) {
            // デコード失敗は再試行しても無駄（設計 §6.4）
            fail(entity, "IMAGE_DECODE_ERROR", cause.getMessage());
            return result;
        }

        entity.setCroppedPath(cropped.relativePath());
        entity.setCroppedName(cropped.fileName());
        entity.setCroppedSize(cropped.size());
        entity.setCroppedWidth(cropped.width());
        entity.setCroppedHeight(cropped.height());
        entity.setPreprocessExecutionId(null);
        recorder.updatePreprocessed(entity);

        result.put("croppedWidth", cropped.width());
        result.put("croppedHeight", cropped.height());
        result.put("message", "画像を取り込みました。（" + entity.getRequestNo() + " "
                + cropped.width() + "x" + cropped.height() + "）");
        log.info("geometry-ai preprocessed. requestId={} requestNo={} cropped={}x{}",
                entity.getRequestId(), entity.getRequestNo(), cropped.width(), cropped.height());
        return result;
    }

    /**
     * 前処理の対象か。
     *
     * <p>`QUEUED`（受付直後）・`PREPROCESSING`（働き手が確保した／落ちたまま残った）・
     * `FAILED(PREPROCESS)`（前処理で失敗した）が対象。既に画像を作ってある行に対しても
     * **同じパスへ作り直すだけ**なので冪等。</p>
     */
    private static boolean isPreprocessTarget(GeometryAiRequestEntity entity) {
        String status = entity.getStatusCode();
        if ("QUEUED".equals(status) || "PREPROCESSING".equals(status)) {
            return true;
        }
        return "FAILED".equals(status) && "PREPROCESS".equals(entity.getFailedStage());
    }

    /** 読み取り中のまま落ちた行（worker の再開対象）。 */
    public static boolean isInterrupted(GeometryAiRequestEntity entity) {
        return "PREPROCESSING".equals(entity.getStatusCode());
    }

    /** 失敗を記録して実行を失敗させる（要求は FAILED(PREPROCESS) になる）。 */
    private void fail(GeometryAiRequestEntity entity, String errorCode, String message) {
        entity.setFailedStage("PREPROCESS");
        entity.setErrorCode(errorCode);
        entity.setErrorMessage(message);
        recorder.updateFailed(entity);
        log.warn("geometry-ai preprocess failed. requestId={} code={} message={}",
                entity.getRequestId(), errorCode, message);
        throw new GeometryAiStepException(message);
    }

    private int intSetting(String key) {
        return Integer.parseInt(settingsService.requireGlobal("geometry-ai", "GEOMETRY_AI", key).trim());
    }

    /** 工程の失敗（`BatchServiceImpl` が FAILED として履歴に残す）。 */
    public static class GeometryAiStepException extends RuntimeException {
        public GeometryAiStepException(String message) {
            super(message);
        }
    }
}
