package com.study21.common.core.geometryai;

import com.study21.common.core.exception.ValidationException;

import java.util.Locale;
import java.util.Optional;

/**
 * AI モデルの**スロット**（設定値 {@code qwen:4} のような指定）と、その設定キーの対応。
 *
 * <p>モデル名・接続先 URL・API Key は `AI_MODEL` ページの**スロットごとの設定**を共用する
 * （例: {@code AI_QWEN_MODEL_4} / {@code AI_QWEN_URL} / {@code AI_QWEN_API_KEY}）。
 * キーの組み立て方をここに 1 回だけ書く。user-api（要求の受付でモデル名を固定する）と
 * admin-api（実行時に接続を解決する）の**両方から使う**ので、片方だけ直すと
 * 「固定したモデル」と「実際に呼ぶモデル」が食い違う。</p>
 *
 * <p><strong>API Key と URL はスロットではなく提供元（provider）ごと</strong>なので、
 * スロットの中身（モデル名）を変えても接続先は同じものを使う。
 * 秘密は要求行のスナップショットへ入れず、実行時にここから安全に読む。</p>
 */
public record AiModelSlot(String slot, String provider, String prefix, String modelKey) {

    /** 設定ページ（COM_設定項目 のページ区分）。 */
    public static final String PAGE = "AI_MODEL";

    /** 接続先 URL の設定キー（提供元ごと）。 */
    public String urlKey() {
        return prefix + "_URL";
    }

    /** API Key の設定キー（提供元ごと。**スナップショットへは入れない**）。 */
    public String apiKeyKey() {
        return prefix + "_API_KEY";
    }

    /** スロットの指定を解決する（`qwen:4` のような形。不正なら空）。 */
    public static Optional<AiModelSlot> of(String slot) {
        if (slot == null) {
            return Optional.empty();
        }
        String value = slot.trim();
        int index = value.indexOf(':');
        if (index <= 0 || index == value.length() - 1) {
            return Optional.empty();
        }
        String provider = value.substring(0, index).toLowerCase(Locale.ROOT);
        String number = value.substring(index + 1).trim();
        String prefix = switch (provider) {
            case "qwen" -> "AI_QWEN";
            case "doubao" -> "AI_DOUBAO";
            case "deepseek" -> "AI_DEEPSEEK";
            case "chatgpt", "openai" -> "AI_CHATGPT";
            default -> null;
        };
        if (prefix == null || number.isEmpty()) {
            return Optional.empty();
        }
        // 1 番だけは接尾辞なしのキーを使う（既存の AI_MODEL ページの作り）
        String modelKey = "1".equals(number) ? prefix + "_MODEL" : prefix + "_MODEL_" + number;
        // 提供元の表記をそろえる（openai は chatgpt と同じ設定を指す）
        String normalized = "openai".equals(provider) ? "chatgpt" : provider;
        return Optional.of(new AiModelSlot(value, normalized, prefix, modelKey));
    }

    /** スロットの指定を解決する（不正なら日本語の理由で例外）。 */
    public static AiModelSlot require(String slot) {
        return of(slot).orElseThrow(() -> new ValidationException(
                slot == null || slot.isBlank()
                        ? "AI のモデルが設定されていません（GEOMETRY_AI_PROVIDER）。"
                          + "システム設定の「図形管理」を確認してください。"
                        : "AI のモデルの指定が正しくありません（設定値: " + slot + "）。"));
    }
}
