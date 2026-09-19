package com.study21.admin.studymonitor;

import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * batL03 の設定の解決。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>7 つのキーをそのまま解決する（2.1 は既定値を持たない）</li>
 *   <li>範囲外・数値でない値は**黙って既定値に置き換えず**例外（2.0 は clamp していた）</li>
 *   <li>{@code STUDY_MONITOR_AI_IMAGE_RESOLUTION} は {@code 1920×1080}（全角）と {@code 1920x1080} を受ける</li>
 * </ol>
 */
class StudyMonitorAnalysisSettingsTest {

    private static Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("STUDY_MONITOR_AI_BATCH_LIMIT", "25");
        values.put("STUDY_MONITOR_AI_THREADS", "4");
        values.put("STUDY_MONITOR_AI_TIMEOUT_SECONDS", "180");
        values.put("STUDY_MONITOR_AI_IMAGE_RESOLUTION", "1920×1080");
        values.put("STUDY_MONITOR_FIRST_AI_PROVIDER", "qwen:3");
        values.put("STUDY_MONITOR_FIRST_SYSTEM_PROMPT", "役割：判定アシスタントです。");
        values.put("STUDY_MONITOR_FIRST_USER_PROMPT", "次の画像を分析してください。");
        return values;
    }

    @Test
    @DisplayName("設定をそのまま解決する")
    void resolvesValues() {
        StudyMonitorAnalysisSettings settings = StudyMonitorAnalysisSettings.from(values());

        assertThat(settings.batchLimit()).isEqualTo(25);
        assertThat(settings.threads()).isEqualTo(4);
        assertThat(settings.timeoutSeconds()).isEqualTo(180);
        assertThat(settings.resolution()).isEqualTo(new StudyMonitorAnalysisSettings.Resolution(1920, 1080));
        assertThat(settings.provider()).isEqualTo("qwen:3");
        assertThat(settings.systemPrompt()).isEqualTo("役割：判定アシスタントです。");
        assertThat(settings.userPrompt()).isEqualTo("次の画像を分析してください。");
    }

    @Test
    @DisplayName("範囲外の値は既定値で埋めずに例外にする")
    void rejectsOutOfRange() {
        Map<String, String> values = values();
        values.put("STUDY_MONITOR_AI_BATCH_LIMIT", "101");

        assertThatThrownBy(() -> StudyMonitorAnalysisSettings.from(values))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("STUDY_MONITOR_AI_BATCH_LIMIT")
                .hasMessageContaining("1〜100");
    }

    @Test
    @DisplayName("並列数・タイムアウトも範囲を守る")
    void rejectsOutOfRangeThreadsAndTimeout() {
        Map<String, String> threads = values();
        threads.put("STUDY_MONITOR_AI_THREADS", "0");
        assertThatThrownBy(() -> StudyMonitorAnalysisSettings.from(threads))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("1〜10");

        Map<String, String> timeout = values();
        timeout.put("STUDY_MONITOR_AI_TIMEOUT_SECONDS", "29");
        assertThatThrownBy(() -> StudyMonitorAnalysisSettings.from(timeout))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("30〜600");
    }

    @Test
    @DisplayName("数値でない値は例外にする")
    void rejectsNonNumeric() {
        Map<String, String> values = values();
        values.put("STUDY_MONITOR_AI_BATCH_LIMIT", "十枚");

        assertThatThrownBy(() -> StudyMonitorAnalysisSettings.from(values))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("数値");
    }

    @Test
    @DisplayName("解像度は全角の × と半角の x の両方を受ける")
    void acceptsBothResolutionSeparators() {
        assertThat(StudyMonitorAnalysisSettings.resolution("1920×1080"))
                .isEqualTo(new StudyMonitorAnalysisSettings.Resolution(1920, 1080));
        assertThat(StudyMonitorAnalysisSettings.resolution("1280x720"))
                .isEqualTo(new StudyMonitorAnalysisSettings.Resolution(1280, 720));
        assertThat(StudyMonitorAnalysisSettings.resolution(" 3840×2160 "))
                .isEqualTo(new StudyMonitorAnalysisSettings.Resolution(3840, 2160));
    }

    @Test
    @DisplayName("解像度の形式が違えば例外にする（2.0 は既定 3840×2160 に置き換えていた）")
    void rejectsBadResolution() {
        assertThatThrownBy(() -> StudyMonitorAnalysisSettings.resolution("フルHD"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("1920×1080");
        assertThatThrownBy(() -> StudyMonitorAnalysisSettings.resolution(null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("プロンプトが空なら例外にする")
    void rejectsBlankPrompt() {
        Map<String, String> values = new HashMap<>(values());
        values.put("STUDY_MONITOR_FIRST_SYSTEM_PROMPT", "  ");

        assertThatThrownBy(() -> StudyMonitorAnalysisSettings.from(values))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("STUDY_MONITOR_FIRST_SYSTEM_PROMPT");
    }
}
