package com.study21.admin.englishessay;

import com.study21.admin.setting.SettingRequirement;
import com.study21.admin.setting.SettingsService;
import com.study21.admin.setting.SettingsValidationException;
import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 英作文 AI 添削の設定（{@code ENGLISH_ESSAY_*} 21 項）と、級の扱い。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>バッチごとに要求する設定キーが違う（batC11 = OCR・タイトル／batC12 = 添削）</li>
 *   <li><b>既定値を持たない</b>。欠落・範囲外は日本語の理由で例外（2.1 の規約）。
 *       ただし実行パラメータ（Temperature / 最大出力Token数）の 4 項だけは既定に落ちる</li>
 *   <li>機能有効化（{@code ENGLISH_ESSAY_ENABLED}）が false なら受付を拒否する</li>
 *   <li>級ごとの配点（GRADE1 = 各 8／PRE1・GRADE2 = 各 4）と語数の目安</li>
 *   <li>プロンプトの {@code {{...}}} 置換</li>
 * </ol>
 */
class EnglishEssayAiSettingsTest {

    private static final String PAGE = "ENGLISH_ESSAY";

    private static Map<String, String> ocrValues() {
        Map<String, String> values = new HashMap<>();
        values.put("ENGLISH_ESSAY_ENABLED", "true");
        values.put("ENGLISH_ESSAY_OCR_AI_PROVIDER", "chatgpt:1");
        values.put("ENGLISH_ESSAY_TITLE_AI_PROVIDER", "chatgpt:1");
        values.put("ENGLISH_ESSAY_MAX_IMAGES", "8");
        values.put("ENGLISH_ESSAY_MAX_IMAGE_MB", "10");
        values.put("ENGLISH_ESSAY_OCR_MAX_IMAGE_PIXELS", "2048");
        values.put("ENGLISH_ESSAY_OCR_REQUEST_TIMEOUT_SECONDS", "600");
        values.put("ENGLISH_ESSAY_OCR_RETRY_LIMIT", "1");
        values.put("ENGLISH_ESSAY_OCR_MAX_COMPLETION_TOKENS", "20480");
        values.put("ENGLISH_ESSAY_OCR_TEMPERATURE", "0.3");
        values.put("ENGLISH_ESSAY_OCR_PROMPT", "ocr system {{level}}");
        values.put("ENGLISH_ESSAY_OCR_USER_PROMPT", "ocr user {{level}} {{image_count}}");
        values.put("ENGLISH_ESSAY_TITLE_PROMPT", "title system");
        values.put("ENGLISH_ESSAY_TITLE_USER_PROMPT", "title user {{question_text}}");
        return values;
    }

    private static Map<String, String> gradingValues() {
        Map<String, String> values = new HashMap<>();
        values.put("ENGLISH_ESSAY_ENABLED", "true");
        values.put("ENGLISH_ESSAY_GRADING_AI_PROVIDER", "chatgpt:1");
        values.put("ENGLISH_ESSAY_GRADING_REQUEST_TIMEOUT_SECONDS", "600");
        values.put("ENGLISH_ESSAY_GRADING_RETRY_LIMIT", "1");
        values.put("ENGLISH_ESSAY_GRADING_MAX_COMPLETION_TOKENS", "16000");
        values.put("ENGLISH_ESSAY_GRADING_TEMPERATURE", "0.7");
        values.put("ENGLISH_ESSAY_GRADING_PROMPT", "grade system {{level}}");
        values.put("ENGLISH_ESSAY_GRADING_USER_PROMPT",
                "grade user {{level}} {{question_text}} {{essay_text}} {{word_count}}");
        return values;
    }

    @Test
    @DisplayName("batC11 は OCR・タイトルの設定を要求する（設定ページは ENGLISH_ESSAY）")
    void requiresOcrSettings() {
        List<SettingRequirement> requirements = EnglishEssayAiSettings.requirements("batC11");

        assertThat(requirements).extracting(SettingRequirement::settingKey)
                .contains("ENGLISH_ESSAY_ENABLED", "ENGLISH_ESSAY_OCR_AI_PROVIDER",
                        "ENGLISH_ESSAY_TITLE_AI_PROVIDER", "ENGLISH_ESSAY_MAX_IMAGES",
                        "ENGLISH_ESSAY_MAX_IMAGE_MB", "ENGLISH_ESSAY_OCR_MAX_IMAGE_PIXELS",
                        "ENGLISH_ESSAY_OCR_REQUEST_TIMEOUT_SECONDS", "ENGLISH_ESSAY_OCR_RETRY_LIMIT",
                        "ENGLISH_ESSAY_OCR_MAX_COMPLETION_TOKENS", "ENGLISH_ESSAY_OCR_TEMPERATURE",
                        "ENGLISH_ESSAY_OCR_PROMPT", "ENGLISH_ESSAY_OCR_USER_PROMPT",
                        "ENGLISH_ESSAY_TITLE_PROMPT", "ENGLISH_ESSAY_TITLE_USER_PROMPT")
                .doesNotContain("ENGLISH_ESSAY_GRADING_PROMPT");
        assertThat(requirements).allSatisfy(requirement ->
                assertThat(requirement.pageCode()).isEqualTo(PAGE));
    }

    @Test
    @DisplayName("batC12 は添削の設定を要求する")
    void requiresGradingSettings() {
        List<SettingRequirement> requirements = EnglishEssayAiSettings.requirements("batC12");

        assertThat(requirements).extracting(SettingRequirement::settingKey)
                .contains("ENGLISH_ESSAY_ENABLED", "ENGLISH_ESSAY_GRADING_AI_PROVIDER",
                        "ENGLISH_ESSAY_GRADING_REQUEST_TIMEOUT_SECONDS", "ENGLISH_ESSAY_GRADING_RETRY_LIMIT",
                        "ENGLISH_ESSAY_GRADING_MAX_COMPLETION_TOKENS", "ENGLISH_ESSAY_GRADING_TEMPERATURE",
                        "ENGLISH_ESSAY_GRADING_PROMPT", "ENGLISH_ESSAY_GRADING_USER_PROMPT")
                .doesNotContain("ENGLISH_ESSAY_OCR_PROMPT");
        assertThat(requirements).allSatisfy(requirement ->
                assertThat(requirement.pageCode()).isEqualTo(PAGE));
    }

    @Test
    @DisplayName("OCR の設定を解決する（画面の値をそのまま使う）")
    void resolvesOcrSettings() {
        EnglishEssayAiSettings.Ocr ocr = EnglishEssayAiSettings.ocr(ocrValues());

        assertThat(ocr.provider()).isEqualTo("chatgpt:1");
        assertThat(ocr.titleProvider()).isEqualTo("chatgpt:1");
        assertThat(ocr.maxImages()).isEqualTo(8);
        assertThat(ocr.maxImageMb()).isEqualTo(10);
        assertThat(ocr.maxImagePixels()).isEqualTo(2048);
        assertThat(ocr.timeoutSeconds()).isEqualTo(600);
        assertThat(ocr.retryLimit()).isEqualTo(1);
        assertThat(ocr.attempts()).isEqualTo(2);
        // 実行パラメータは設定から読む（コードに埋めない）
        assertThat(ocr.temperature()).isEqualTo(0.3);
        assertThat(ocr.maxCompletionTokens()).isEqualTo(20480);
    }

    @Test
    @DisplayName("添削の設定を解決する（Temperature と最大出力Token数も設定から読む）")
    void resolvesGradingSettings() {
        EnglishEssayAiSettings.Grading grading = EnglishEssayAiSettings.grading(gradingValues());

        assertThat(grading.provider()).isEqualTo("chatgpt:1");
        assertThat(grading.timeoutSeconds()).isEqualTo(600);
        assertThat(grading.retryLimit()).isEqualTo(1);
        assertThat(grading.attempts()).isEqualTo(2);
        assertThat(grading.temperature()).isEqualTo(0.7);
        assertThat(grading.maxCompletionTokens()).isEqualTo(16000);
    }

    @Test
    @DisplayName("実行パラメータが未設定なら既定（OCR 0.0 / 4096・添削 0.2 / 8192）に落とす")
    void fallsBackToDefaultAiParametersWhenMissing() {
        Map<String, String> ocrValues = ocrValues();
        ocrValues.remove("ENGLISH_ESSAY_OCR_TEMPERATURE");
        ocrValues.remove("ENGLISH_ESSAY_OCR_MAX_COMPLETION_TOKENS");

        EnglishEssayAiSettings.Ocr ocr = EnglishEssayAiSettings.ocr(ocrValues);
        assertThat(ocr.temperature()).isEqualTo(0.0);
        assertThat(ocr.maxCompletionTokens()).isEqualTo(4096);

        Map<String, String> gradingValues = gradingValues();
        gradingValues.remove("ENGLISH_ESSAY_GRADING_TEMPERATURE");
        gradingValues.remove("ENGLISH_ESSAY_GRADING_MAX_COMPLETION_TOKENS");

        EnglishEssayAiSettings.Grading grading = EnglishEssayAiSettings.grading(gradingValues);
        assertThat(grading.temperature()).isEqualTo(0.2);
        assertThat(grading.maxCompletionTokens()).isEqualTo(8192);
    }

    @Test
    @DisplayName("実行パラメータが不正（数値でない・範囲外）でも既定に落とす（勝手に失敗させない）")
    void fallsBackToDefaultAiParametersWhenInvalid() {
        Map<String, String> ocrValues = ocrValues();
        ocrValues.put("ENGLISH_ESSAY_OCR_TEMPERATURE", "あつい");
        ocrValues.put("ENGLISH_ESSAY_OCR_MAX_COMPLETION_TOKENS", "1");

        EnglishEssayAiSettings.Ocr ocr = EnglishEssayAiSettings.ocr(ocrValues);
        assertThat(ocr.temperature()).isEqualTo(0.0);
        assertThat(ocr.maxCompletionTokens()).isEqualTo(4096);

        Map<String, String> gradingValues = gradingValues();
        gradingValues.put("ENGLISH_ESSAY_GRADING_TEMPERATURE", "3.5");
        gradingValues.put("ENGLISH_ESSAY_GRADING_MAX_COMPLETION_TOKENS", "999999");

        EnglishEssayAiSettings.Grading grading = EnglishEssayAiSettings.grading(gradingValues);
        assertThat(grading.temperature()).isEqualTo(0.2);
        assertThat(grading.maxCompletionTokens()).isEqualTo(8192);
    }

    @Test
    @DisplayName("設定が欠けていたら日本語の理由で例外（既定値を持たない）")
    void rejectsMissingSettings() {
        Map<String, String> values = ocrValues();
        values.remove("ENGLISH_ESSAY_OCR_PROMPT");

        assertThatThrownBy(() -> EnglishEssayAiSettings.ocr(values))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ENGLISH_ESSAY_OCR_PROMPT");
    }

    @Test
    @DisplayName("範囲外の値は例外（MAX_IMAGES は 1〜20）")
    void rejectsOutOfRange() {
        Map<String, String> values = ocrValues();
        values.put("ENGLISH_ESSAY_MAX_IMAGES", "0");

        assertThatThrownBy(() -> EnglishEssayAiSettings.ocr(values))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ENGLISH_ESSAY_MAX_IMAGES");
    }

    @Test
    @DisplayName("機能有効化が false なら受付を拒否する（日本語の理由）")
    void rejectsWhenDisabled() {
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.findGlobal(PAGE, "ENGLISH_ESSAY_ENABLED")).thenReturn(Optional.of("false"));

        assertThatThrownBy(() -> EnglishEssayAiSettings.requireEnabled(settingsService))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("無効");
    }

    @Test
    @DisplayName("設定が読めないときも受付を拒否する（黙って動かさない）")
    void rejectsWhenEnabledUnreadable() {
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.findGlobal(PAGE, "ENGLISH_ESSAY_ENABLED"))
                .thenThrow(new SettingsValidationException(List.of("ENGLISH_ESSAY_ENABLED が未設定です。")));

        assertThatThrownBy(() -> EnglishEssayAiSettings.requireEnabled(settingsService))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ENGLISH_ESSAY_ENABLED");
    }

    @Test
    @DisplayName("機能有効化が true なら通す")
    void allowsWhenEnabled() {
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.findGlobal(PAGE, "ENGLISH_ESSAY_ENABLED")).thenReturn(Optional.of("true"));

        EnglishEssayAiSettings.requireEnabled(settingsService);
    }

    @Test
    @DisplayName("級の配点と語数の目安（GRADE1 = 8／PRE1・GRADE2 = 4）")
    void levelRules() {
        assertThat(EnglishEssayLevel.GRADE1.rubricMax()).isEqualTo(8);
        assertThat(EnglishEssayLevel.GRADE1.maxScore()).isEqualTo(32);
        assertThat(EnglishEssayLevel.PRE1.rubricMax()).isEqualTo(4);
        assertThat(EnglishEssayLevel.PRE1.maxScore()).isEqualTo(16);
        assertThat(EnglishEssayLevel.GRADE2.rubricMax()).isEqualTo(4);
        assertThat(EnglishEssayLevel.GRADE2.maxScore()).isEqualTo(16);

        assertThat(EnglishEssayLevel.GRADE1.wordRequirement()).isEqualTo("200〜240語");
        assertThat(EnglishEssayLevel.PRE1.wordRequirement()).isEqualTo("120〜150語");
        assertThat(EnglishEssayLevel.GRADE2.wordRequirement()).isEqualTo("80〜100語");

        assertThat(EnglishEssayLevel.parse("grade1")).isEqualTo(EnglishEssayLevel.GRADE1);
        assertThat(EnglishEssayLevel.parse(" PRE1 ")).isEqualTo(EnglishEssayLevel.PRE1);
        assertThatThrownBy(() -> EnglishEssayLevel.parse("EIKEN1"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("英検級");
    }

    @Test
    @DisplayName("プロンプトの {{...}} を置換する（知らない印はそのまま残す）")
    void rendersPrompt() {
        String rendered = EnglishEssayAiPrompt.render(
                "級={{level}} 問題={{question_text}} 未知={{unknown}}",
                Map.of("level", "PRE1", "question_text", "Do you agree?"));

        assertThat(rendered).isEqualTo("級=PRE1 問題=Do you agree? 未知={{unknown}}");
        assertThat(EnglishEssayAiPrompt.render(null, Map.of())).isEmpty();
        assertThat(EnglishEssayAiPrompt.render("そのまま", null)).isEqualTo("そのまま");
    }
}
