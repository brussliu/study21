package com.study21.admin.englishessay;

import com.study21.admin.geometryai.GeometryAiConnectionResolver;
import com.study21.admin.setting.SettingsService;
import com.study21.admin.setting.SettingsValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 添削の実行（働き手と batC12 が同じ道を通る）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>設定の {@code _GRADING_PROMPT} / {@code _GRADING_USER_PROMPT} を使い、
 *       {@code {{level}}} / {@code {{question_text}}} / {@code {{essay_text}}} / {@code {{word_count}}} を置換する</li>
 *   <li>応答を<b>検証してから</b>保存する（不正なら {@code RETRY_LIMIT} 回まで再試行）</li>
 *   <li>成功: {@code SUCCEEDED} + 得点 + 添削結果JSON + 呼出履歴ID</li>
 *   <li>失敗: {@code FAILED} + 日本語の理由（例外にしない＝働き手を止めない）</li>
 *   <li>設定が読めないときは実行中のまま残さず {@code FAILED} にする</li>
 * </ol>
 */
class EnglishEssayGradingStepTest {

    private EnglishEssayAiClient aiClient;
    private EnglishEssayAiMapper mapper;
    private SettingsService settingsService;
    private GeometryAiConnectionResolver connectionResolver;
    private EnglishEssayGradingStep step;

    @BeforeEach
    void setUp() {
        aiClient = mock(EnglishEssayAiClient.class);
        mapper = mock(EnglishEssayAiMapper.class);
        settingsService = mock(SettingsService.class);
        connectionResolver = mock(GeometryAiConnectionResolver.class);
        step = new EnglishEssayGradingStep(aiClient, mapper, settingsService, connectionResolver);
    }

    private static Map<String, String> gradingValues(int retryLimit) {
        Map<String, String> values = new HashMap<>();
        values.put("ENGLISH_ESSAY_ENABLED", "true");
        values.put("ENGLISH_ESSAY_GRADING_AI_PROVIDER", "chatgpt:1");
        values.put("ENGLISH_ESSAY_GRADING_REQUEST_TIMEOUT_SECONDS", "600");
        values.put("ENGLISH_ESSAY_GRADING_RETRY_LIMIT", String.valueOf(retryLimit));
        values.put("ENGLISH_ESSAY_GRADING_MAX_COMPLETION_TOKENS", "16000");
        values.put("ENGLISH_ESSAY_GRADING_TEMPERATURE", "0.9");
        values.put("ENGLISH_ESSAY_GRADING_PROMPT", "添削 システム {{level}}");
        values.put("ENGLISH_ESSAY_GRADING_USER_PROMPT",
                "級 {{level}} 問題 {{question_text}} 本文 {{essay_text}} 語数 {{word_count}}");
        return values;
    }

    private void stubSettings(int retryLimit) {
        when(settingsService.requireSettings("batC12", EnglishEssayAiSettings.requirements("batC12")))
                .thenReturn(gradingValues(retryLimit));
        when(connectionResolver.resolve("batC12", "chatgpt:1")).thenReturn(
                new GeometryAiConnectionResolver.AiConnection(
                        "chatgpt", "gpt-5", "https://example.com/v1", "secret"));
    }

    private static EnglishEssayAiMapper.GradingRow item() {
        EnglishEssayAiMapper.GradingRow row = new EnglishEssayAiMapper.GradingRow();
        row.setGradingId(501L);
        row.setEssayId(900001L);
        row.setRound(2);
        row.setStatusCode("RUNNING");
        row.setLevel("PRE1");
        row.setTitleJa("読書と動画");
        row.setTitleZh("读书与视频");
        row.setQuestionText("Do you agree?");
        row.setEssayText("I think reading is better than videos.");
        row.setWordCount(8);
        return row;
    }

    private static String validResponse() {
        return "{\"level\":\"PRE1\",\"titleJa\":\"読書と動画\",\"titleZh\":\"读书与视频\","
                + "\"rubric\":{"
                + "\"content\":{\"score\":3,\"maxScore\":4,\"reasonJa\":\"良い\",\"reasonZh\":\"好\"},"
                + "\"organization\":{\"score\":3,\"maxScore\":4,\"reasonJa\":\"良い\",\"reasonZh\":\"好\"},"
                + "\"vocabulary\":{\"score\":2,\"maxScore\":4,\"reasonJa\":\"平易\",\"reasonZh\":\"简单\"},"
                + "\"grammar\":{\"score\":3,\"maxScore\":4,\"reasonJa\":\"正確\",\"reasonZh\":\"准确\"}},"
                + "\"corrections\":[{\"original\":\"I think\",\"corrected\":\"I believe\","
                + "\"categoryJa\":\"表現\",\"categoryZh\":\"表达\",\"reasonJa\":\"繰り返し\",\"reasonZh\":\"重复\"}],"
                + "\"feedbackJa\":{\"summary\":\"主張は伝わります。理由を増やしましょう。\","
                + "\"strengths\":[\"設問適合\",\"理由\",\"つながり\"],\"improvements\":[],\"nextAdvice\":\"理由を 2 つ。\"},"
                + "\"feedbackZh\":{\"summary\":\"主张明确。再补理由。\","
                + "\"strengths\":[\"切题\",\"理由\",\"衔接\"],\"improvements\":[],\"nextAdvice\":\"写两个理由。\"},"
                + "\"modelAnswer\":\"I believe reading books is better than watching videos because "
                + "we can concentrate deeply and think about the content for a long time. "
                + "In conclusion, reading is useful for our study.\","
                + "\"warnings\":[],\"disclaimerJa\":\"公式採点ではありません。\",\"disclaimerZh\":\"非官方评分。\"}";
    }

    private static EnglishEssayAiClient.CallResult success(String content, long callLogId) {
        return new EnglishEssayAiClient.CallResult(content, null, null, false, callLogId);
    }

    private static EnglishEssayAiClient.CallResult failure(String code, String message, boolean fatal,
                                                           long callLogId) {
        return new EnglishEssayAiClient.CallResult(null, code, message, fatal, callLogId);
    }

    @Test
    @DisplayName("成功: 得点・満点・添削結果JSON・呼出履歴ID を保存する")
    void writesSuccess() {
        stubSettings(0);
        when(aiClient.call(any())).thenReturn(success(validResponse(), 4501L));

        assertThat(step.runItem(item())).isTrue();

        ArgumentCaptor<String> report = ArgumentCaptor.forClass(String.class);
        verify(mapper).markGradingSucceeded(eq(501L), eq(11), eq(16), report.capture(), eq(4501L));
        assertThat(report.getValue()).contains("\"AI_GRADED\"").contains("\"eiken-ai-v1\"");
        verify(mapper, never()).markGradingFailed(anyLong(), anyString());

        ArgumentCaptor<EnglishEssayAiClient.AiCallRequest> call =
                ArgumentCaptor.forClass(EnglishEssayAiClient.AiCallRequest.class);
        verify(aiClient).call(call.capture());
        assertThat(call.getValue().batchCode()).isEqualTo("batC12");
        assertThat(call.getValue().systemPrompt()).isEqualTo("添削 システム PRE1");
        assertThat(call.getValue().userPrompt())
                .isEqualTo("級 PRE1 問題 Do you agree? 本文 I think reading is better than videos. 語数 8");
        assertThat(call.getValue().processKey()).isEqualTo("english-essay/900001/grading/round-2");
        assertThat(call.getValue().language()).isEqualTo("ja-zh");
        assertThat(call.getValue().timeoutSeconds()).isEqualTo(600);
        // 実行パラメータは設定の値（Temperature / 最大出力Token数）を渡す
        assertThat(call.getValue().temperature()).isEqualTo(0.9);
        assertThat(call.getValue().maxCompletionTokens()).isEqualTo(16000);
        // 添削は画像を渡さない（テキストだけ）
        assertThat(call.getValue().image()).isNull();
    }

    @Test
    @DisplayName("Temperature / 最大出力Token数が未設定・不正なら既定（0.2 / 8192）で呼ぶ")
    void fallsBackToDefaultAiParameters() {
        Map<String, String> values = gradingValues(0);
        values.remove("ENGLISH_ESSAY_GRADING_TEMPERATURE");
        values.put("ENGLISH_ESSAY_GRADING_MAX_COMPLETION_TOKENS", "12");   // 範囲外（1024 未満）
        when(settingsService.requireSettings("batC12", EnglishEssayAiSettings.requirements("batC12")))
                .thenReturn(values);
        when(connectionResolver.resolve("batC12", "chatgpt:1")).thenReturn(
                new GeometryAiConnectionResolver.AiConnection(
                        "chatgpt", "gpt-5", "https://example.com/v1", "secret"));
        when(aiClient.call(any())).thenReturn(success(validResponse(), 1L));

        assertThat(step.runItem(item())).isTrue();

        ArgumentCaptor<EnglishEssayAiClient.AiCallRequest> call =
                ArgumentCaptor.forClass(EnglishEssayAiClient.AiCallRequest.class);
        verify(aiClient).call(call.capture());
        assertThat(call.getValue().temperature()).isEqualTo(0.2);
        assertThat(call.getValue().maxCompletionTokens()).isEqualTo(8192);
    }

    @Test
    @DisplayName("応答が不正なら RETRY_LIMIT 回まで再試行し、それでも駄目なら FAILED")
    void retriesInvalidResponseThenFails() {
        stubSettings(1);
        when(aiClient.call(any())).thenReturn(
                success("{\"titleJa\":\"題\"}", 1L), success("{\"titleJa\":\"題\"}", 2L));

        assertThat(step.runItem(item())).isFalse();

        verify(aiClient, times(2)).call(any());
        verify(mapper, never()).markGradingSucceeded(anyLong(), any(), any(), anyString(), any());
        verify(mapper).markGradingFailed(eq(501L), anyString());
    }

    @Test
    @DisplayName("不正のあとに正しい応答が来たら成功（再試行が効く）")
    void recoversOnRetry() {
        stubSettings(1);
        when(aiClient.call(any())).thenReturn(
                success("{\"titleJa\":\"題\"}", 1L), success(validResponse(), 2L));

        assertThat(step.runItem(item())).isTrue();

        verify(aiClient, times(2)).call(any());
        verify(mapper).markGradingSucceeded(eq(501L), eq(11), eq(16), anyString(), eq(2L));
    }

    @Test
    @DisplayName("AI の呼び出しが致命的（4xx）なら再試行せず FAILED（理由は日本語）")
    void doesNotRetryOnFatal() {
        stubSettings(3);
        when(aiClient.call(any())).thenReturn(
                failure("HTTP_4XX", "API Key が不正です。", true, 9L));

        assertThat(step.runItem(item())).isFalse();

        verify(aiClient, times(1)).call(any());
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(mapper).markGradingFailed(eq(501L), reason.capture());
        assertThat(reason.getValue()).contains("API Key が不正です。");
    }

    @Test
    @DisplayName("設定が読めないときは実行中のまま残さず FAILED にする")
    void failsOnConfigError() {
        when(settingsService.requireSettings(anyString(), any()))
                .thenThrow(new SettingsValidationException(List.of("ENGLISH_ESSAY_GRADING_PROMPT が未設定です。")));

        assertThat(step.runItem(item())).isFalse();

        verify(aiClient, never()).call(any());
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(mapper).markGradingFailed(eq(501L), reason.capture());
        assertThat(reason.getValue()).contains("ENGLISH_ESSAY_GRADING_PROMPT が未設定です。");
    }

    @Test
    @DisplayName("失敗理由は 500 文字に収める（列の上限）")
    void truncatesFailureReason() {
        stubSettings(0);
        when(aiClient.call(any())).thenReturn(failure("HTTP_500", "あ".repeat(900), false, 1L));

        step.runItem(item());

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(mapper).markGradingFailed(eq(501L), reason.capture());
        assertThat(reason.getValue()).hasSize(500);
    }

    @Test
    @DisplayName("接続が解決できないときは FAILED（別のモデルへ黙って切り替えない）")
    void failsWhenConnectionUnresolved() {
        when(settingsService.requireSettings("batC12", EnglishEssayAiSettings.requirements("batC12")))
                .thenReturn(gradingValues(0));
        when(connectionResolver.resolve("batC12", "chatgpt:1"))
                .thenThrow(new com.study21.common.core.exception.ValidationException("AI のモデル設定を読めません。"));

        assertThat(step.runItem(item())).isFalse();

        verify(aiClient, never()).call(any());
        verify(mapper).markGradingFailed(eq(501L), anyString());
    }

    @Test
    @DisplayName("呼び出しの言語は ja-zh で記録する（日本語と中国語の 2 言語を作る）")
    void recordsLanguage() {
        stubSettings(0);
        when(aiClient.call(any())).thenReturn(success(validResponse(), 1L));

        step.runItem(item());

        ArgumentCaptor<EnglishEssayAiClient.AiCallRequest> call =
                ArgumentCaptor.forClass(EnglishEssayAiClient.AiCallRequest.class);
        verify(aiClient).call(call.capture());
        assertThat(call.getValue().processKey()).contains("english-essay/900001/grading/round-2");
    }
}
