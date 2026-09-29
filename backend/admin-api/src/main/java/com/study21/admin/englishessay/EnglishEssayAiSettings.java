package com.study21.admin.englishessay;

import com.study21.admin.setting.SettingRequirement;
import com.study21.admin.setting.SettingsService;
import com.study21.common.core.exception.ValidationException;

import java.util.List;
import java.util.Map;

/**
 * 英作文の AI（{@code batC11} の OCR・タイトル／{@code batC12} の添削）が必要とする設定を解決した結果。
 *
 * <p>プロンプト・接続先・上限（枚数など）は<b>既定値を持たない</b>（2.1 の規約。
 * {@code SettingsService} が欠落を実行前に例外にする）。例外は<b>実行パラメータの 2 つ</b>で、
 * Temperature と最大出力Token数は設定が無い・不正なとき {@link #OCR_TEMPERATURE_DEFAULT} などへ
 * 落とす（2026-09-27 の利用者の指示。キーが無い環境でも AI を止めないため。設定が無いときの
 * 値は、それまでコードに埋まっていた値と同じ）。
 * 設定ページは {@code ENGLISH_ESSAY} で、キーの並びは {@code COM_設定項目} のカタログと同じ。</p>
 *
 * <ul>
 *   <li>OCR: {@code ENGLISH_ESSAY_OCR_AI_PROVIDER} / {@code _OCR_PROMPT} / {@code _OCR_USER_PROMPT} /
 *       {@code _OCR_REQUEST_TIMEOUT_SECONDS} / {@code _OCR_RETRY_LIMIT} / {@code _OCR_MAX_IMAGE_PIXELS} /
 *       {@code _OCR_MAX_COMPLETION_TOKENS} / {@code _OCR_TEMPERATURE}</li>
 *   <li>取り込みの上限: {@code _MAX_IMAGES} / {@code _MAX_IMAGE_MB}</li>
 *   <li>題: {@code _TITLE_AI_PROVIDER} / {@code _TITLE_PROMPT} / {@code _TITLE_USER_PROMPT}</li>
 *   <li>添削: {@code _GRADING_AI_PROVIDER} / {@code _GRADING_PROMPT} / {@code _GRADING_USER_PROMPT} /
 *       {@code _GRADING_REQUEST_TIMEOUT_SECONDS} / {@code _GRADING_RETRY_LIMIT} /
 *       {@code _GRADING_MAX_COMPLETION_TOKENS} / {@code _GRADING_TEMPERATURE}</li>
 * </ul>
 */
public final class EnglishEssayAiSettings {

    /** 設定ページ区分。 */
    public static final String PAGE_CODE = "ENGLISH_ESSAY";

    /** 画像分類・OCR・主題タイトル生成のバッチ。 */
    public static final String OCR_BATCH_CODE = "batC11";

    /** 英検基準 AI 添削のバッチ。 */
    public static final String GRADING_BATCH_CODE = "batC12";

    /** 機能有効化の設定キー。 */
    public static final String ENABLED_KEY = "ENGLISH_ESSAY_ENABLED";

    /**
     * 実行パラメータの既定値（設定が無い・不正なときに使う）。
     *
     * <p>2026-09-27 までは {@code EnglishEssayOcrStep} / {@code EnglishEssayGradingStep} の
     * 定数だった値を、ここ 1 か所に集めた（設定ページの「OCR：Temperature」などを見て変えられる）。
     * 日本語単語 AI の {@code BAT_C41_*} と同じく、値そのものは {@code COM_設定情報} が正。</p>
     */
    static final double OCR_TEMPERATURE_DEFAULT = 0.0;

    /** {@code ENGLISH_ESSAY_OCR_MAX_COMPLETION_TOKENS} の既定値（1 枚ぶんのページ JSON に十分な量）。 */
    static final int OCR_MAX_COMPLETION_TOKENS_DEFAULT = 4096;

    /** {@code ENGLISH_ESSAY_GRADING_TEMPERATURE} の既定値（判定の揺れを抑える）。 */
    static final double GRADING_TEMPERATURE_DEFAULT = 0.2;

    /** {@code ENGLISH_ESSAY_GRADING_MAX_COMPLETION_TOKENS} の既定値（2 言語ぶんの長い添削に足りる量）。 */
    static final int GRADING_MAX_COMPLETION_TOKENS_DEFAULT = 8192;

    /** Temperature の下限（画面の ENUM は 0.0〜2.0）。 */
    private static final double TEMPERATURE_MIN = 0.0;

    /** Temperature の上限。 */
    private static final double TEMPERATURE_MAX = 2.0;

    /** 最大出力Token数の下限（日本語単語 AI と同じ範囲）。 */
    private static final int MAX_COMPLETION_TOKENS_MIN = 1024;

    /** 最大出力Token数の上限。 */
    private static final int MAX_COMPLETION_TOKENS_MAX = 65536;

    private EnglishEssayAiSettings() {
    }

    /**
     * OCR（batC11 と同期の {@code POST /ocr}）の設定。
     *
     * @param maxImagePixels      AI へ送る画像の<b>長辺</b>の上限（超える画像は縮小する）
     * @param retryLimit          画像 1 枚あたりの最大再実行回数
     * @param temperature         AI 呼び出しの温度（0.0〜2.0。題の生成にも同じ値を使う）
     * @param maxCompletionTokens AI 呼び出しの最大出力Token数（1024〜65536）
     */
    record Ocr(
            String provider,
            String titleProvider,
            int maxImages,
            int maxImageMb,
            int maxImagePixels,
            int timeoutSeconds,
            int retryLimit,
            double temperature,
            int maxCompletionTokens,
            String systemPrompt,
            String userPrompt,
            String titleSystemPrompt,
            String titleUserPrompt) {

        /** 1 枚あたりの試行回数（初回 + 再実行）。 */
        int attempts() {
            return Math.max(0, retryLimit) + 1;
        }
    }

    /**
     * 添削（batC12 と働き手）の設定。
     *
     * @param retryLimit          1 回の添削あたりの最大再実行回数（AI エラー・JSON 検証エラー）
     * @param temperature         AI 呼び出しの温度（0.0〜2.0）
     * @param maxCompletionTokens AI 呼び出しの最大出力Token数（1024〜65536）
     */
    record Grading(
            String provider,
            int timeoutSeconds,
            int retryLimit,
            double temperature,
            int maxCompletionTokens,
            String systemPrompt,
            String userPrompt) {

        /** 1 件あたりの試行回数（初回 + 再実行）。 */
        int attempts() {
            return Math.max(0, retryLimit) + 1;
        }
    }

    /** そのバッチが要求する設定（{@code BatchTaskRegistry} の宣言と同じ並び）。 */
    public static List<SettingRequirement> requirements(String batchCode) {
        String code = batchCode == null ? "" : batchCode.trim();
        List<String> keys = switch (code) {
            case OCR_BATCH_CODE -> List.of(
                    "ENGLISH_ESSAY_ENABLED",
                    "ENGLISH_ESSAY_OCR_AI_PROVIDER",
                    "ENGLISH_ESSAY_TITLE_AI_PROVIDER",
                    "ENGLISH_ESSAY_MAX_IMAGES",
                    "ENGLISH_ESSAY_MAX_IMAGE_MB",
                    "ENGLISH_ESSAY_OCR_MAX_IMAGE_PIXELS",
                    "ENGLISH_ESSAY_OCR_REQUEST_TIMEOUT_SECONDS",
                    "ENGLISH_ESSAY_OCR_RETRY_LIMIT",
                    "ENGLISH_ESSAY_OCR_MAX_COMPLETION_TOKENS",
                    "ENGLISH_ESSAY_OCR_TEMPERATURE",
                    "ENGLISH_ESSAY_OCR_PROMPT",
                    "ENGLISH_ESSAY_OCR_USER_PROMPT",
                    "ENGLISH_ESSAY_TITLE_PROMPT",
                    "ENGLISH_ESSAY_TITLE_USER_PROMPT");
            case GRADING_BATCH_CODE -> List.of(
                    "ENGLISH_ESSAY_ENABLED",
                    "ENGLISH_ESSAY_GRADING_AI_PROVIDER",
                    "ENGLISH_ESSAY_GRADING_REQUEST_TIMEOUT_SECONDS",
                    "ENGLISH_ESSAY_GRADING_MAX_COMPLETION_TOKENS",
                    "ENGLISH_ESSAY_GRADING_TEMPERATURE",
                    "ENGLISH_ESSAY_GRADING_RETRY_LIMIT",
                    "ENGLISH_ESSAY_GRADING_PROMPT",
                    "ENGLISH_ESSAY_GRADING_USER_PROMPT");
            default -> throw new ValidationException("英作文 AI のバッチコードではありません: " + batchCode);
        };
        return keys.stream().map(key -> new SettingRequirement(PAGE_CODE, key)).toList();
    }

    /** OCR の設定を解決する。 */
    static Ocr ocr(Map<String, String> source) {
        Map<String, String> values = source == null ? Map.of() : source;
        return new Ocr(
                text(values, "ENGLISH_ESSAY_OCR_AI_PROVIDER"),
                text(values, "ENGLISH_ESSAY_TITLE_AI_PROVIDER"),
                number(values, "ENGLISH_ESSAY_MAX_IMAGES", 1, 20),
                number(values, "ENGLISH_ESSAY_MAX_IMAGE_MB", 1, 100),
                number(values, "ENGLISH_ESSAY_OCR_MAX_IMAGE_PIXELS", 512, 8192),
                number(values, "ENGLISH_ESSAY_OCR_REQUEST_TIMEOUT_SECONDS", 30, 1800),
                number(values, "ENGLISH_ESSAY_OCR_RETRY_LIMIT", 0, 5),
                decimalOr(values, "ENGLISH_ESSAY_OCR_TEMPERATURE",
                        TEMPERATURE_MIN, TEMPERATURE_MAX, OCR_TEMPERATURE_DEFAULT),
                numberOr(values, "ENGLISH_ESSAY_OCR_MAX_COMPLETION_TOKENS",
                        MAX_COMPLETION_TOKENS_MIN, MAX_COMPLETION_TOKENS_MAX,
                        OCR_MAX_COMPLETION_TOKENS_DEFAULT),
                text(values, "ENGLISH_ESSAY_OCR_PROMPT"),
                text(values, "ENGLISH_ESSAY_OCR_USER_PROMPT"),
                text(values, "ENGLISH_ESSAY_TITLE_PROMPT"),
                text(values, "ENGLISH_ESSAY_TITLE_USER_PROMPT"));
    }

    /** 添削の設定を解決する。 */
    static Grading grading(Map<String, String> source) {
        Map<String, String> values = source == null ? Map.of() : source;
        return new Grading(
                text(values, "ENGLISH_ESSAY_GRADING_AI_PROVIDER"),
                number(values, "ENGLISH_ESSAY_GRADING_REQUEST_TIMEOUT_SECONDS", 30, 1800),
                number(values, "ENGLISH_ESSAY_GRADING_RETRY_LIMIT", 0, 5),
                decimalOr(values, "ENGLISH_ESSAY_GRADING_TEMPERATURE",
                        TEMPERATURE_MIN, TEMPERATURE_MAX, GRADING_TEMPERATURE_DEFAULT),
                numberOr(values, "ENGLISH_ESSAY_GRADING_MAX_COMPLETION_TOKENS",
                        MAX_COMPLETION_TOKENS_MIN, MAX_COMPLETION_TOKENS_MAX,
                        GRADING_MAX_COMPLETION_TOKENS_DEFAULT),
                text(values, "ENGLISH_ESSAY_GRADING_PROMPT"),
                text(values, "ENGLISH_ESSAY_GRADING_USER_PROMPT"));
    }

    /**
     * 機能有効化（{@code ENGLISH_ESSAY_ENABLED}）を確かめる。
     *
     * <p>受付の入口（同期 OCR・添削の受付）が<b>これを呼んでから</b>仕事を始める。設定が読めない
     * ときも「動かさない」（黙って動かすと、止めたいときに止まらない）。</p>
     *
     * @throws ValidationException 無効・未設定・読めないとき（日本語の理由）
     */
    public static void requireEnabled(SettingsService settingsService) {        String value;
        try {
            value = settingsService.findGlobal(PAGE_CODE, ENABLED_KEY).orElse("");
        } catch (RuntimeException cause) {
            throw new ValidationException("英作文AI添削の設定（" + ENABLED_KEY
                    + "）を読めません。設定ページの「機能有効化」を確認してください。");
        }
        if (!isTrue(value)) {
            throw new ValidationException("英作文AI添削は現在無効です（設定ページの「機能有効化」を ON にしてください）。");
        }
    }

    /** {@code true} / {@code 1} だけを有効とみなす（画面の ENUM は true/false）。 */
    private static boolean isTrue(String value) {
        String text = value == null ? "" : value.trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    private static int number(Map<String, String> values, String key, int min, int max) {
        long parsed = longOf(values, key);
        if (parsed < min || parsed > max) {
            throw new ValidationException(key + " は " + min + "〜" + max + " の範囲で設定してください。");
        }
        return (int) parsed;
    }

    /**
     * 整数を読み、無い・数値でない・範囲外なら既定に落とす（実行パラメータ専用）。
     *
     * <p>設定ページにキーが無い環境（移行前の DB）でも AI を止めないため。落ちたことは
     * 呼び出し側で分からないので、既定値は「それまでコードに埋まっていた値」と同じにする。</p>
     */
    private static int numberOr(Map<String, String> values, String key, int min, int max, int fallback) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed < min || parsed > max ? fallback : parsed;
        } catch (NumberFormatException cause) {
            return fallback;
        }
    }

    /** 小数を読み、無い・数値でない・範囲外なら既定に落とす（実行パラメータ専用）。 */
    private static double decimalOr(Map<String, String> values, String key, double min, double max,
                                    double fallback) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return parsed < min || parsed > max ? fallback : parsed;
        } catch (NumberFormatException cause) {
            return fallback;
        }
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
}
