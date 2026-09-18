package com.study21.admin.geometryai;

import com.study21.admin.setting.SettingsService;
import com.study21.admin.setting.SettingsValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 生図の画像（元画像・切り抜き画像）のクリーンアップ。**既存の batR02 に相乗りする**
 * （新しいバッチを作らない。設計 §7）。
 *
 * <p>保持日数は設定 `GEOMETRY_AI_IMAGE_RETENTION_DAYS`（既定 30 日）。**行は残し、画像ファイルだけ
 * 消す**（要求の履歴は監査として残す）。消したことは `元画像パス` / `切抜画像パス` を NULL にして
 * 記録する（2 回目以降は対象にならない）。</p>
 *
 * <p>設定が未投入のときは**画像の削除だけを見送る**（他のクリーンアップを止めないため）。
 * 消しすぎを避けるため、1 回の実行で処理する件数に上限を置く。</p>
 */
@Component
public class GeometryAiImageCleanup {

    private static final Logger log = LoggerFactory.getLogger(GeometryAiImageCleanup.class);

    /** 設定が見つからないときのバッチコード（実行履歴のエラー詳細に入る）。 */
    private static final String TASK_CODE = "batR02";
    /** 1 回の実行で処理する最大件数（暴走を防ぐ）。 */
    private static final int BATCH_LIMIT = 200;
    /** 既定の保持日数（設定が未投入のときは削除しないので、これは使わない）。 */
    private static final int FALLBACK_RETENTION_DAYS = 30;

    private final GeometryAiRequestMapper requestMapper;
    private final GeometryAiImageStorage storage;
    private final SettingsService settingsService;

    public GeometryAiImageCleanup(GeometryAiRequestMapper requestMapper,
                                  GeometryAiImageStorage storage,
                                  SettingsService settingsService) {
        this.requestMapper = requestMapper;
        this.storage = storage;
        this.settingsService = settingsService;
    }

    /** 保持日数を過ぎた画像を消す（戻り値は履歴のメッセージに使う件数）。 */
    @Transactional
    public Map<String, Object> run() {
        int retentionDays = retentionDays();
        Map<String, Object> result = new LinkedHashMap<>();
        if (retentionDays <= 0) {
            result.put("skipped", true);
            result.put("message", "AI 生図の画像削除は行いませんでした（保持日数の設定が未投入です）。");
            return result;
        }

        List<GeometryAiRequestEntity> targets = requestMapper.findImageCleanupTargets(retentionDays, BATCH_LIMIT);
        int deletedFiles = 0;
        int clearedRows = 0;
        for (GeometryAiRequestEntity target : targets) {
            boolean deleted = storage.delete(target.getOriginalPath(), target.getOriginalName());
            deleted |= storage.delete(target.getCroppedPath(), target.getCroppedName());
            if (deleted) {
                deletedFiles += 1;
            }
            requestMapper.clearImageFiles(target.getRequestId());
            clearedRows += 1;
        }
        result.put("retentionDays", retentionDays);
        result.put("targets", targets.size());
        result.put("deletedFiles", deletedFiles);
        result.put("clearedRows", clearedRows);
        result.put("message", "AI 生図の画像を削除しました。（対象 " + targets.size() + " 件 / うちファイルあり "
                + deletedFiles + " 件・保持 " + retentionDays + " 日）");
        log.info("geometry ai image cleanup finished. retentionDays={} targets={} deleted={} rows={}",
                retentionDays, targets.size(), deletedFiles, clearedRows);
        return result;
    }

    /** 保持日数（未投入なら 0 = 削除しない）。 */
    private int retentionDays() {
        try {
            return Integer.parseInt(settingsService
                    .requireGlobal(TASK_CODE, "GEOMETRY_AI", "GEOMETRY_AI_IMAGE_RETENTION_DAYS").trim());
        } catch (SettingsValidationException | NumberFormatException cause) {
            log.warn("AI 生図の保持日数（GEOMETRY_AI_IMAGE_RETENTION_DAYS）を読めないため、画像の削除を見送ります。");
            return 0;
        }
    }

    /** 既定の保持日数（ドキュメント用。設定が無いときは削除しない）。 */
    public static int defaultRetentionDays() {
        return FALLBACK_RETENTION_DAYS;
    }
}
