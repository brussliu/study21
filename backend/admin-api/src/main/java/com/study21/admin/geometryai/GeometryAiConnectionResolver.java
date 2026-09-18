package com.study21.admin.geometryai;

import com.study21.admin.setting.SettingsService;
import com.study21.admin.setting.SettingsValidationException;
import com.study21.common.core.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 設定 `GEOMETRY_AI_PROVIDER`（例 `qwen:4`）から**実際の接続情報**を解決する。
 *
 * <p>モデル名・URL・API Key は `AI_MODEL` ページの**同じスロット**を共用する
 * （`AI_QWEN_MODEL_4` / `AI_QWEN_URL` / `AI_QWEN_API_KEY`）。スロットごとに動的なので
 * `BatchTaskDefinition.requiredSettings` には書けず、実行時に `SettingsService.requireGlobal` で
 * 解決する（設計 §2.3）。**API Key は seed しない**ので、未設定なら日本語の理由で失敗する。</p>
 */
@Component
public class GeometryAiConnectionResolver {

    private static final Logger log = LoggerFactory.getLogger(GeometryAiConnectionResolver.class);

    private final SettingsService settingsService;
    /** 検証用のスタブ（`study21.geometry-ai.stub=true`）。有効なら AI を実際に呼ばない。 */
    private final boolean stub;

    public GeometryAiConnectionResolver(SettingsService settingsService,
                                        @Value("${study21.geometry-ai.stub:false}") boolean stub) {
        this.settingsService = settingsService;
        this.stub = stub;
    }

    /** 解決した接続情報。 */
    public record AiConnection(String provider, String model, String url, String apiKey) {
    }

    /** 設定のスロット（`qwen:4`）を解決する。未設定・不正は日本語の例外。 */
    public AiConnection resolve(String taskCode, String slot) {
        if (slot == null || slot.isBlank()) {
            throw new ValidationException("AI のモデルが設定されていません（GEOMETRY_AI_PROVIDER）。"
                    + "システム設定の「AI 生図（図形管理）」を確認してください。");
        }
        int index = slot.indexOf(':');
        if (index <= 0 || index == slot.length() - 1) {
            throw new ValidationException("AI のモデルの指定が正しくありません（設定値: " + slot + "）。");
        }
        String provider = slot.substring(0, index).toLowerCase(Locale.ROOT);
        String number = slot.substring(index + 1);
        String prefix = switch (provider) {
            case "qwen" -> "AI_QWEN";
            case "doubao" -> "AI_DOUBAO";
            case "deepseek" -> "AI_DEEPSEEK";
            case "chatgpt", "openai" -> "AI_CHATGPT";
            default -> null;
        };
        if (prefix == null) {
            throw new ValidationException("AI のモデルの指定が正しくありません（設定値: " + slot + "）。");
        }
        String modelKey = "1".equals(number) ? prefix + "_MODEL" : prefix + "_MODEL_" + number;
        try {
            String model = settingsService.requireGlobal(taskCode, "AI_MODEL", modelKey);
            String url = settingsService.requireGlobal(taskCode, "AI_MODEL", prefix + "_URL");
            String apiKey = settingsService.requireGlobal(taskCode, "AI_MODEL", prefix + "_API_KEY");
            return new AiConnection(provider, model, url, apiKey);
        } catch (SettingsValidationException cause) {
            if (!stub) {
                throw cause;
            }
            // 検証用スタブ（外部へは出ない）では接続設定が無くても動かす
            log.warn("AI 生図はスタブ動作です（接続設定なし）。key={}", modelKey);
            return new AiConnection(provider, "stub-model", "stub://local", "stub-key");
        }
    }
}
