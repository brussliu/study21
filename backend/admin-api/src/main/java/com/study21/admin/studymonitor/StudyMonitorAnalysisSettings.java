package com.study21.admin.studymonitor;

import com.study21.common.core.exception.ValidationException;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * batL03（学習モニター スナップショット AI 分析）が必要とする設定を解決した結果。
 *
 * <p>2.0 の {@code BatL03Task#loadSettings} は設定が欠けていればプログラム側の既定値
 * （バッチ枚数 10・並列 3・120 秒・3840×2160・閾値 0.750 など）で埋めていた。
 * 2.1 の規約（{@code SettingsService}）は**既定値・フォールバックを持たない**ので、
 * 欠落・型不正・範囲外はここで日本語の理由とともに例外にする。</p>
 *
 * <p>二次判定（2.0 の {@code STUDY_MONITOR_SECOND_*_PROMPT} / {@code STUDY_MONITOR_SECOND_AI_PROVIDER} /
 * {@code STUDY_MONITOR_SECONDARY_THRESHOLD}）は 2.1 の設定にキーが無いので**扱わない**
 * （{@link StudyMonitorAnalyzeHandler} は一次判定の結果をそのまま最終結果にする）。</p>
 */
public record StudyMonitorAnalysisSettings(
        /** 一度の実行で AI に渡す最大枚数（{@code STUDY_MONITOR_AI_BATCH_LIMIT}）。 */
        int batchLimit,
        /** 同時に AI を呼ぶ本数（{@code STUDY_MONITOR_AI_THREADS}）。 */
        int threads,
        /** 1 枚あたりの AI 呼び出しのタイムアウト秒（{@code STUDY_MONITOR_AI_TIMEOUT_SECONDS}）。 */
        int timeoutSeconds,
        /** AI へ渡す前に縮小する解像度（{@code STUDY_MONITOR_AI_IMAGE_RESOLUTION}）。 */
        Resolution resolution,
        /** AI モデルのスロット（{@code STUDY_MONITOR_FIRST_AI_PROVIDER}。例 {@code qwen:3}）。 */
        String provider,
        /** 一次判定の System Prompt（{@code STUDY_MONITOR_FIRST_SYSTEM_PROMPT}）。 */
        String systemPrompt,
        /** 一次判定の User Prompt（{@code STUDY_MONITOR_FIRST_USER_PROMPT}）。 */
        String userPrompt) {

    /** AI へ渡す画像の大きさ（2.0 と同じ 4 種類を想定するが、値は設定から読む）。 */
    public record Resolution(int width, int height) {
    }

    /** {@code 1920×1080} / {@code 1920x1080} の形（全角の × と半角の x の両方を受ける）。 */
    private static final Pattern RESOLUTION_PATTERN = Pattern.compile("^(\\d{1,5})\\s*[x×]\\s*(\\d{1,5})$");

    /**
     * 設定値（{@code SettingsService#requireSettings} の戻り値）から解決する。
     *
     * @throws ValidationException 型不正・範囲外（キーの欠落は {@code requireSettings} が先に検出する）
     */
    public static StudyMonitorAnalysisSettings from(Map<String, String> values) {
        return new StudyMonitorAnalysisSettings(
                number(values, "STUDY_MONITOR_AI_BATCH_LIMIT", 1, 100),
                number(values, "STUDY_MONITOR_AI_THREADS", 1, 10),
                number(values, "STUDY_MONITOR_AI_TIMEOUT_SECONDS", 30, 600),
                resolution(values.get("STUDY_MONITOR_AI_IMAGE_RESOLUTION")),
                text(values, "STUDY_MONITOR_FIRST_AI_PROVIDER"),
                text(values, "STUDY_MONITOR_FIRST_SYSTEM_PROMPT"),
                text(values, "STUDY_MONITOR_FIRST_USER_PROMPT"));
    }

    private static int number(Map<String, String> values, String key, int min, int max) {
        String value = values == null ? null : values.get(key);
        int parsed;
        try {
            parsed = Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException cause) {
            throw new ValidationException(key + " は数値で設定してください。");
        }
        if (parsed < min || parsed > max) {
            throw new ValidationException(key + " は " + min + "〜" + max + " の範囲で設定してください。");
        }
        return parsed;
    }

    private static String text(Map<String, String> values, String key) {
        String value = values == null ? null : values.get(key);
        if (value == null || value.isBlank()) {
            throw new ValidationException(key + " を設定してください。");
        }
        return value.trim();
    }

    /** 解像度の設定を読む（{@code 1920×1080} の形。2.0 は不明な値を既定値で置き換えていた）。 */
    static Resolution resolution(String value) {
        Matcher matcher = RESOLUTION_PATTERN.matcher(value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw new ValidationException(
                    "STUDY_MONITOR_AI_IMAGE_RESOLUTION は 1920×1080 の形式で設定してください。");
        }
        int width = Integer.parseInt(matcher.group(1));
        int height = Integer.parseInt(matcher.group(2));
        if (width < 1 || height < 1 || width > 10000 || height > 10000) {
            throw new ValidationException(
                    "STUDY_MONITOR_AI_IMAGE_RESOLUTION の解像度が範囲外です（1〜10000）。");
        }
        return new Resolution(width, height);
    }
}
