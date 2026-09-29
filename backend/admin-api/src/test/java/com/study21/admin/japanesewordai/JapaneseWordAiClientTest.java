package com.study21.admin.japanesewordai;

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
 * 日本語単語の AI 呼び出し（batC41〜batC44 で共通）。
 *
 * <p>確かめる接縫は 2 点:</p>
 * <ol>
 *   <li>OpenAI 互換の応答から<b>中身の JSON を取り出す</b>（封筒・コードフェンス・打ち切りをどう扱うか）</li>
 *   <li><b>1 回の呼び出し = {@code BAT_AI呼出履歴情報} 1 行</b>（成功でも失敗でも残す）</li>
 * </ol>
 *
 * <p>HTTP は自分で書かない。既存の {@link GeometryAiClient}（画像なし・JSON 出力）をそのまま使う。</p>
 */
class JapaneseWordAiClientTest {

    private static final String BATCH_CODE = "batC41";
    private static final String PROCESS_KEY = "japanese-word/101/DETAIL/attempt-1";

    private GeometryAiClient geometryAiClient;
    private AiCallLogMapper aiCallLogMapper;
    private JapaneseWordAiClient client;

    @BeforeEach
    void setUp() {
        geometryAiClient = mock(GeometryAiClient.class);
        aiCallLogMapper = mock(AiCallLogMapper.class);
        client = new JapaneseWordAiClient(geometryAiClient, aiCallLogMapper);
        // MyBatis は採番した 呼出履歴ID を書き戻す（useGeneratedKeys）。
        // Step はこの ID を JPN_AI生成履歴情報.呼出履歴ID へ渡す
        when(aiCallLogMapper.insert(any())).thenAnswer(invocation -> {
            AiCallLogEntity log = invocation.getArgument(0);
            log.setCallId(8801L);
            return 1;
        });
    }

    private JapaneseWordAiClient.AiCallRequest request() {
        return new JapaneseWordAiClient.AiCallRequest(
                BATCH_CODE, 12L, PROCESS_KEY, "word-101",
                "qwen", "qwen3.7-plus", "https://example.com/v1/chat/completions", "secret",
                "system prompt", "user prompt", 300, 0.2, 11776);
    }

    /** OpenAI 互換の封筒。 */
    private static String envelope(String content) {
        return "{\"choices\":[{\"message\":{\"content\":" + jsonString(content) + "},\"finish_reason\":\"stop\"}]}";
    }

    private static String jsonString(String value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    @Test
    @DisplayName("封筒から中身を取り出し、Markdown のコードフェンスを剥がす")
    void extractsContent() {
        String json = "{\"jlpt\":\"N3\"}";
        when(geometryAiClient.call(any())).thenReturn(
                GeometryAiClient.AiResponse.success(200, envelope("```json\n" + json + "\n```")));

        JapaneseWordAiClient.CallResult result = client.call(request());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.content()).isEqualTo(json);

        ArgumentCaptor<AiCallLogEntity> captor = ArgumentCaptor.forClass(AiCallLogEntity.class);
        verify(aiCallLogMapper).insert(captor.capture());
        AiCallLogEntity log = captor.getValue();
        assertThat(log.getBatchCode()).isEqualTo(BATCH_CODE);
        assertThat(log.getProcessKey()).isEqualTo(PROCESS_KEY);
        assertThat(log.getResult()).isEqualTo("SUCCESS");
        assertThat(log.getSourceCode()).isEqualTo("BATCH");
        assertThat(log.getAiType()).isEqualTo("qwen");
        assertThat(log.getModelName()).isEqualTo("qwen3.7-plus");
        assertThat(log.getHttpStatus()).isEqualTo(200);
        assertThat(log.getLanguage()).isEqualTo("ja-zh");
        assertThat(log.getPrompt()).contains("[system]").contains("system prompt").contains("user prompt");
        assertThat(log.getExecutionId()).isEqualTo(12L);
        // 呼出履歴の ID を返す（Step が JPN_AI生成履歴情報.呼出履歴ID へ渡す）
        assertThat(result.callLogId()).isEqualTo(8801L);
    }

    @Test
    @DisplayName("失敗でも呼出履歴の ID を返す（どの呼び出しで失敗したか辿れる）")
    void returnsCallLogIdOnFailure() {
        when(geometryAiClient.call(any())).thenReturn(
                GeometryAiClient.AiResponse.failure(429, "HTTP_429", "レート制限"));

        JapaneseWordAiClient.CallResult result = client.call(request());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.callLogId()).isEqualTo(8801L);
    }

    @Test
    @DisplayName("出力が上限で打ち切られたら失敗にする（切れた JSON を DB に入れない）")
    void rejectsTruncated() {
        when(geometryAiClient.call(any())).thenReturn(GeometryAiClient.AiResponse.success(200,
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"jlpt\\\":\"},\"finish_reason\":\"length\"}]}"));

        JapaneseWordAiClient.CallResult result = client.call(request());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).isEqualTo("MAX_TOKENS");
        // 失敗でも呼出履歴には 1 行残す（お金を使った事実を消さない）
        ArgumentCaptor<AiCallLogEntity> captor = ArgumentCaptor.forClass(AiCallLogEntity.class);
        verify(aiCallLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo("FAILURE");
        assertThat(captor.getValue().getErrorCode()).isEqualTo("MAX_TOKENS");
    }

    @Test
    @DisplayName("HTTP エラーは接縫のエラーコードをそのまま返し、呼出履歴に残す")
    void recordsHttpError() {
        when(geometryAiClient.call(any())).thenReturn(
                GeometryAiClient.AiResponse.failure(429, "HTTP_429", "レート制限"));

        JapaneseWordAiClient.CallResult result = client.call(request());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).isEqualTo("HTTP_429");
        assertThat(result.errorMessage()).isEqualTo("レート制限");
        ArgumentCaptor<AiCallLogEntity> captor = ArgumentCaptor.forClass(AiCallLogEntity.class);
        verify(aiCallLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getHttpStatus()).isEqualTo(429);
        assertThat(captor.getValue().getResult()).isEqualTo("FAILURE");
    }

    @Test
    @DisplayName("中身が空の応答は EMPTY_RESPONSE として失敗にする")
    void rejectsEmptyContent() {
        when(geometryAiClient.call(any())).thenReturn(
                GeometryAiClient.AiResponse.success(200, envelope("   ")));

        JapaneseWordAiClient.CallResult result = client.call(request());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).isEqualTo("EMPTY_RESPONSE");
        verify(aiCallLogMapper, times(1)).insert(any());
    }

    @Test
    @DisplayName("封筒でない本文（スタブなど）は、そのまま中身として扱う")
    void acceptsRawBody() {
        when(geometryAiClient.call(any())).thenReturn(
                GeometryAiClient.AiResponse.success(200, "{\"jlpt\":\"N2\"}"));

        JapaneseWordAiClient.CallResult result = client.call(request());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.content()).isEqualTo("{\"jlpt\":\"N2\"}");
    }
}
