package com.study21.admin.japanesewordai;

import com.study21.admin.setting.SettingRequirement;
import com.study21.common.core.exception.ValidationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * batC41〜batC44 が必要とする設定を解決した結果。
 *
 * <p>設定ページは 4 バッチとも {@code JAPANESE_WORD_AI} で、キーだけが
 * {@code BAT_C41_*} 〜 {@code BAT_C44_*} と違う。9 つのキーは同じ意味なので、
 * 1 つの record と {@link #requirements(String)} でまかなう。</p>
 *
 * <p><strong>既定値・フォールバックを持たない。</strong>2.0 は「threads の既定 3」
 * 「timeout は DETAIL 300 / 他 120」などをコードに持っていたが、2.1 の規約
 * （{@code SettingsService}）は欠落を実行前に例外にする。値は
 * {@code COM_設定情報} を単一の正とする。</p>
 *
 * @param provider            使用モデルのスロット（{@code qwen:1} など）
 * @param batchMax            一度の実行で受け付ける最大の語数
 * @param threads             同時に AI を呼ぶ本数
 * @param timeoutSeconds      1 語あたりの AI 呼び出しのタイムアウト秒
 * @param maxCompletionTokens 出力の最大トークン数
 * @param temperature         温度（0.0〜2.0）
 * @param systemPrompt        System Prompt（{@code {{kind}}} を置換する）
 * @param userPrompt          User Prompt（{@code {{kind}}} / {@code {{word_json}}} を置換する）
 * @param retryLimit          1 語あたりの最大再実行回数
 */
record JapaneseWordAiSettings(
        String provider,
        int batchMax,
        int threads,
        int timeoutSeconds,
        int maxCompletionTokens,
        double temperature,
        String systemPrompt,
        String userPrompt,
        int retryLimit) {

    /** 設定ページ（4 バッチで共通）。 */
    static final String PAGE_CODE = "JAPANESE_WORD_AI";

    /** {@code THREADS} の下限（{@link #of} の検証と、働き手の縮退で同じ値を使う）。 */
    private static final int THREADS_MIN = 1;
    /** {@code THREADS} の上限。 */
    private static final int THREADS_MAX = 10;

    /** そのバッチが要求する設定（キーの並びは 2.0 の設定画面と同じ）。 */
    static List<SettingRequirement> requirements(String batchCode) {
        String prefix = prefixOf(batchCode);
        return List.of(
                new SettingRequirement(PAGE_CODE, prefix + "_AI_PROVIDER"),
                new SettingRequirement(PAGE_CODE, prefix + "_BATCH_MAX"),
                new SettingRequirement(PAGE_CODE, prefix + "_THREADS"),
                new SettingRequirement(PAGE_CODE, prefix + "_REQUEST_TIMEOUT_SECONDS"),
                new SettingRequirement(PAGE_CODE, prefix + "_MAX_COMPLETION_TOKENS"),
                new SettingRequirement(PAGE_CODE, prefix + "_TEMPERATURE"),
                new SettingRequirement(PAGE_CODE, prefix + "_SYSTEM_PROMPT"),
                new SettingRequirement(PAGE_CODE, prefix + "_USER_PROMPT"),
                new SettingRequirement(PAGE_CODE, prefix + "_RETRY_LIMIT"));
    }

    /** 取得区分（{@code DETAIL} / {@code C} / {@code D} / {@code E}）が書く内容種別コード。 */
    static List<String> contentTypesOf(String kind) {
        try {
            return JapaneseWordAiPrompt.contentTypesOf(kind);
        } catch (IllegalArgumentException cause) {
            throw new ValidationException("取得区分が不正です: " + kind);
        }
    }

    /**
     * 内容種別コード（{@code A_DETAIL}）→ バッチコード（{@code batC41}）。
     *
     * <p>非同期の働き手は「1 語 × 1 内容種別」の行から実行するので、その行だけでは
     * <b>どのバッチの設定を使うか</b>が分からない。ここで戻す（{@link #contentTypesOf(String)} の逆）。</p>
     */
    static String batchCodeOf(String contentType) {
        return switch (contentType == null ? "" : contentType) {
            case "A_DETAIL" -> "batC41";
            case "C1_READING", "C2_KANJI" -> "batC42";
            case "D_CONTEXT_MEANING" -> "batC43";
            case "E_KANJI_USAGE" -> "batC44";
            default -> throw new ValidationException("内容種別が不正です: " + contentType);
        };
    }

    /**
     * 並列数（設定が読めない・範囲外のときは 1）。
     *
     * <p>{@link #of(Map, String)} は設定が欠けていれば<b>例外</b>にする（実行そのものを始めない）。
     * 働き手は「設定が壊れていても止まらない」必要があるので、ここでは安全側の 1 に落とす
     * （1 件ずつでも進む。止まるより遅いほうがよい）。</p>
     *
     * <p>設定は**設定キー**（{@code BAT_C41_THREADS}）で読む。画面のフィールドキー
     * （{@code c25Threads}）を返す {@code loadGlobalSettingFields()} を使うと**いつも読めない**。</p>
     */
    static int threadsOrOne(com.study21.admin.setting.SettingsService settingsService, String batchCode) {
        String value;
        try {
            // 任意項目として読む（未設定なら空）。働き手は設定が壊れていても止まらない
            value = settingsService
                    .findGlobal(PAGE_CODE, prefixOf(batchCode) + "_THREADS")
                    .orElse(null);
        } catch (RuntimeException cause) {
            return 1;
        }
        if (value == null || value.isBlank()) {
            return 1;
        }
        try {
            return clampThreads(Integer.parseInt(value.trim()));
        } catch (NumberFormatException cause) {
            return 1;
        }
    }

    /**
     * 働き手が 1 周期に確保する件数（4 バッチの {@code THREADS} の**最小値**）。
     *
     * <p>どのバッチの行を拾うかは確保するまで分からないので、**どのバッチの並列数の上限も
     * 超えない**安全側の値を使う。設定が読めないバッチがあれば 1 として数える。</p>
     */
    static int threadsLimitOf(com.study21.admin.setting.SettingsService settingsService) {
        int limit = 0;
        for (String batchCode : JapaneseWordAiLimits.BATCH_OF_KIND.values()) {
            int threads = threadsOrOne(settingsService, batchCode);
            limit = limit == 0 ? threads : Math.min(limit, threads);
        }
        return Math.max(1, limit);
    }

    /** {@code THREADS} の上下限（{@link #of} の検証と同じ範囲）。 */
    private static int clampThreads(int threads) {
        return threads < THREADS_MIN || threads > THREADS_MAX ? 1 : threads;
    }

    /** バッチコード（{@code batC41}）→ 設定キーの接頭辞（{@code BAT_C41}）。 */
    static String prefixOf(String batchCode) {
        String code = batchCode == null ? "" : batchCode.trim();
        if (!code.matches("batC4[1-4]")) {
            throw new ValidationException("日本語単語の AI 取得のバッチコードではありません: " + batchCode);
        }
        // batC41 → C41 → BAT_C41
        return "BAT_C" + code.substring(4);
    }

    /** 設定値のマップから解決する。 */
    static JapaneseWordAiSettings of(Map<String, String> source, String batchCode) {
        String prefix = prefixOf(batchCode);
        Map<String, String> values = source == null ? Map.of() : source;
        return new JapaneseWordAiSettings(
                text(values, prefix + "_AI_PROVIDER"),
                number(values, prefix + "_BATCH_MAX", 1, 200),
                number(values, prefix + "_THREADS", THREADS_MIN, THREADS_MAX),
                number(values, prefix + "_REQUEST_TIMEOUT_SECONDS", 30, 1800),
                number(values, prefix + "_MAX_COMPLETION_TOKENS", 1024, 65536),
                decimal(values, prefix + "_TEMPERATURE", 0.0, 2.0),
                text(values, prefix + "_SYSTEM_PROMPT"),
                text(values, prefix + "_USER_PROMPT"),
                number(values, prefix + "_RETRY_LIMIT", 0, 5));
    }

    /** batC41 の設定として解決する（テストと、DETAIL しか使わない経路の便宜）。 */
    static JapaneseWordAiSettings of(Map<String, String> source) {
        return of(source, "batC41");
    }

    /** 実際に使う並列数（対象の語数より多くしない）。 */
    int threadsFor(int wordCount) {
        return Math.max(1, Math.min(threads, Math.max(1, wordCount)));
    }

    private static int number(Map<String, String> values, String key, int min, int max) {
        long parsed = longOf(values, key);
        if (parsed < min || parsed > max) {
            throw new ValidationException(key + " は " + min + "〜" + max + " の範囲で設定してください。");
        }
        return (int) parsed;
    }

    private static double decimal(Map<String, String> values, String key, double min, double max) {
        String value = values.get(key);
        double parsed;
        try {
            parsed = Double.parseDouble(value == null ? "" : value.trim());
        } catch (NumberFormatException cause) {
            throw new ValidationException(key + " は数値で設定してください。");
        }
        if (parsed < min || parsed > max) {
            throw new ValidationException(key + " は " + min + "〜" + max + " の範囲で設定してください。");
        }
        return parsed;
    }

    private static long longOf(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new ValidationException(key + " を設定してください。");
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException cause) {
            throw new ValidationException(key + " は数値で設定してください。");
        }
    }

    private static String text(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new ValidationException(key + " を設定してください。");
        }
        return value;
    }

    /** 設定キーの一覧（呼出履歴の 処理キー などに使う）。 */
    static Map<String, String> describe(String batchCode) {
        Map<String, String> described = new LinkedHashMap<>();
        for (SettingRequirement requirement : requirements(batchCode)) {
            described.put(requirement.settingKey(), requirement.pageCode());
        }
        return described;
    }
}
