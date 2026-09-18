package com.study21.admin.classroomai;

import com.study21.admin.setting.SettingsService;
import com.study21.admin.setting.SettingsValidationException;
import com.study21.common.core.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 設定 `CLASSROOM_AI_NOTE_PROVIDER`（例 `deepseek:1`）から**実際の接続情報**を解決する。
 *
 * <p>モデル名・URL・API Key は `AI_MODEL` ページの**同じスロット**を共用する
 * （`AI_DEEPSEEK_MODEL` / `AI_DEEPSEEK_URL` / `AI_DEEPSEEK_API_KEY`）。{@code GeometryAiConnectionResolver}
 * と同じ作りで、未設定・不正は日本語の例外、スタブ（`study21.classroom-ai.stub=true`）のときは
 * 接続設定が無くても動かす。</p>
 */
@Component
public class ClassroomAiConnectionResolver {

    private static final Logger log = LoggerFactory.getLogger(ClassroomAiConnectionResolver.class);

    private final SettingsService settingsService;
    private final boolean stub;

    public ClassroomAiConnectionResolver(SettingsService settingsService,
                                         @Value("${study21.classroom-ai.stub:false}") boolean stub) {
        this.settingsService = settingsService;
        this.stub = stub;
    }

    /** 解決した接続情報。 */
    public record AiConnection(String provider, String model, String url, String apiKey) {
    }

    /** 設定のスロット（`deepseek:1`）を解決する。未設定・不正は日本語の例外。 */
    public AiConnection resolve(String taskCode, String slot) {
        if (slot == null || slot.isBlank()) {
            throw new ValidationException("AI のモデルが設定されていません（CLASSROOM_AI_NOTE_PROVIDER）。"
                    + "システム設定の「AI 授業記録（授業録音）」を確認してください。");
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
            log.warn("授業ノートはスタブ動作です（接続設定なし）。key={}", modelKey);
            return new AiConnection(provider, "stub-model", "stub://local", "stub-key");
        }
    }
}
