package com.study21.admin.englishessay;

import com.study21.admin.batch.AiCallLogEntity;
import com.study21.admin.batch.AiCallLogMapper;
import com.study21.admin.geometryai.GeometryAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 英作文の AI 呼び出し（OCR と添削で共通）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li><b>画像（vision）を接縫へそのまま渡す</b>（1 回 1 枚。{@code image} / {@code imageMime}）</li>
 *   <li>OpenAI 互換の封筒から中身を取り出し、コードフェンスを剥がす。打ち切り（{@code length}）は失敗</li>
 *   <li><b>1 回の呼び出し = {@code BAT_AI呼出履歴情報} 1 行</b>（成功でも失敗でも）。採番 ID を返す</li>
 *   <li>HTTP 4xx は再試行しない（{@code fatal}）</li>
 * </ol>
 */
class EnglishEssayAiClientTest {

    private GeometryAiClient geometryAiClient;
    private AiCallLogMapper aiCallLogMapper;
    private EnglishEssayAiClient client;

    @BeforeEach
    void setUp() {
        geometryAiClient = mock(GeometryAiClient.class);
        aiCallLogMapper = mock(AiCallLogMapper.class);
        client = new EnglishEssayAiClient(geometryAiClient, aiCallLogMapper);
        when(aiCallLogMapper.insert(any())).thenAnswer(invocation -> {
            AiCallLogEntity log = invocation.getArgument(0);
            log.setCallId(7701L);
            return 1;
        });
    }

    private EnglishEssayAiClient.AiCallRequest request() {
        return new EnglishEssayAiClient.AiCallRequest(
                "batC11", 12L, "english-essay/900001/ocr/question-1", "en",
                "chatgpt", "gpt-5", "https://example.com/v1", "secret",
                "system prompt", "user prompt",
                new byte[] {1, 2, 3}, "image/png",
                600, 0.0, 4096);
    }

    private static String envelope(String content) {
        try {
            return "{\"choices\":[{\"message\":{\"content\":"
                    + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(content)
                    + "},\"finish_reason\":\"stop\"}]}";
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    @Test
    @DisplayName("画像を接縫へ渡し、封筒から中身を取り出す（フェンスも剥がす）")
    void passesImageAndExtractsContent() {
        String json = "{\"pages\":[]}";
        when(geometryAiClient.call(any())).thenReturn(
                GeometryAiClient.AiResponse.success(200, envelope("```json\n" + json + "\n```")));

        EnglishEssayAiClient.CallResult result = client.call(request());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.content()).isEqualTo(json);
        assertThat(result.fatal()).isFalse();
        assertThat(result.callLogId()).isEqualTo(7701L);

        ArgumentCaptor<GeometryAiClient.AiRequest> ai = ArgumentCaptor.forClass(GeometryAiClient.AiRequest.class);
        verify(geometryAiClient).call(ai.capture());
        assertThat(ai.getValue().image()).containsExactly(1, 2, 3);
        assertThat(ai.getValue().imageMime()).isEqualTo("image/png");
        assertThat(ai.getValue().systemPrompt()).isEqualTo("system prompt");
        assertThat(ai.getValue().userPrompt()).isEqualTo("user prompt");
        assertThat(ai.getValue().jsonResponse()).isTrue();
        // URL の補完（末尾 /v1 → /chat/completions）
        assertThat(ai.getValue().url()).isEqualTo("https://example.com/v1/chat/completions");

        ArgumentCaptor<AiCallLogEntity> captor = ArgumentCaptor.forClass(AiCallLogEntity.class);
        verify(aiCallLogMapper).insert(captor.capture());
        AiCallLogEntity log = captor.getValue();
        assertThat(log.getBatchCode()).isEqualTo("batC11");
        assertThat(log.getProcessKey()).isEqualTo("english-essay/900001/ocr/question-1");
        assertThat(log.getLanguage()).isEqualTo("en");
        assertThat(log.getResult()).isEqualTo("SUCCESS");
        assertThat(log.getSourceCode()).isEqualTo("BATCH");
        assertThat(log.getExecutionId()).isEqualTo(12L);
        assertThat(log.getHttpStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("失敗でも呼出履歴に 1 行残し、呼出履歴ID を返す")
    void recordsFailure() {
        when(geometryAiClient.call(any())).thenReturn(
                GeometryAiClient.AiResponse.failure(429, "HTTP_429", "レート制限"));

        EnglishEssayAiClient.CallResult result = client.call(request());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).isEqualTo("HTTP_429");
        assertThat(result.callLogId()).isEqualTo(7701L);
        assertThat(result.fatal()).isFalse();

        ArgumentCaptor<AiCallLogEntity> captor = ArgumentCaptor.forClass(AiCallLogEntity.class);
        verify(aiCallLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo("FAILURE");
    }

    @Test
    @DisplayName("HTTP 4xx は再試行しない（fatal）")
    void marks4xxFatal() {
        when(geometryAiClient.call(any())).thenReturn(
                GeometryAiClient.AiResponse.failure(401, "HTTP_4XX", "API Key が不正です"));

        EnglishEssayAiClient.CallResult result = client.call(request());

        assertThat(result.fatal()).isTrue();
    }

    @Test
    @DisplayName("出力が上限で切れたら失敗（切れた JSON を保存しない）")
    void rejectsTruncated() {
        when(geometryAiClient.call(any())).thenReturn(GeometryAiClient.AiResponse.success(200,
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"pages\\\":\"},\"finish_reason\":\"length\"}]}"));

        EnglishEssayAiClient.CallResult result = client.call(request());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).isEqualTo("MAX_TOKENS");
        verify(aiCallLogMapper, times(1)).insert(any());
    }

    @Test
    @DisplayName("中身が空の応答は EMPTY_RESPONSE")
    void rejectsEmpty() {
        when(geometryAiClient.call(any())).thenReturn(
                GeometryAiClient.AiResponse.success(200, envelope("   ")));

        EnglishEssayAiClient.CallResult result = client.call(request());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).isEqualTo("EMPTY_RESPONSE");
    }

    @Test
    @DisplayName("封筒でない本文は、そのまま中身として扱う（スタブ）")
    void acceptsRawBody() {
        when(geometryAiClient.call(any())).thenReturn(
                GeometryAiClient.AiResponse.success(200, "{\"titleJa\":\"題\"}"));

        EnglishEssayAiClient.CallResult result = client.call(request());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.content()).isEqualTo("{\"titleJa\":\"題\"}");
    }
}
