package com.study21.admin.studymonitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.study21.admin.batch.AiCallLogEntity;
import com.study21.admin.batch.AiCallLogMapper;
import com.study21.admin.geometryai.GeometryAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI（vision）の呼び出しと応答の検証（2.0 の {@code BatL03Task#callVision} / {@code parseJson} /
 * {@code allowedStatus} / {@code confidence} の移行）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>OpenAI 互換のリクエスト（画像 1 枚 + JSON 出力 + temperature 0.1）を既存の接縫に渡す</li>
 *   <li>応答 JSON の検証: {@code status} は 6 状態だけ・{@code confidence} は 0〜1・JSON でない応答は失敗</li>
 *   <li>日本語の状態 → 2.1 のコード（6 種類）</li>
 *   <li>呼び出し 1 回 = {@code BAT_AI呼出履歴情報} 1 行（成功でも失敗でも）</li>
 * </ol>
 */
class StudyMonitorAiClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final byte[] IMAGE = {1, 2, 3};

    private GeometryAiClient geometryAiClient;
    private AiCallLogMapper aiCallLogMapper;
    private StudyMonitorAiClient client;

    @BeforeEach
    void setUp() {
        geometryAiClient = mock(GeometryAiClient.class);
        aiCallLogMapper = mock(AiCallLogMapper.class);
        client = new StudyMonitorAiClient(geometryAiClient, aiCallLogMapper);
    }

    private static StudyMonitorAiClient.AnalyzeRequest request() {
        return new StudyMonitorAiClient.AnalyzeRequest(77L, "study-monitor/snapshot/12/first",
                "qwen", "Qwen3-VL-Flash", "https://example.com/compatible-mode/v1", "secret-key",
                "役割：判定アシスタントです。", "次の画像を分析してください。",
                IMAGE, "image/jpeg", 120);
    }

    /** OpenAI 互換の応答の封筒（接縫が返す形）。 */
    private static String envelope(String content) {
        ObjectNode root = MAPPER.createObjectNode();
        root.putArray("choices").addObject().putObject("message").put("content", content);
        return root.toString();
    }

    private static String reply(String status, String confidence, String reason) {
        return "{\"status\":\"" + status + "\",\"confidence\":" + confidence + ",\"reason\":\"" + reason + "\"}";
    }

    @Test
    @DisplayName("正常な応答をコードへ変換する")
    void convertsValidReply() {
        String content = reply("学習中（PC使用）", "0.9", "PCの画面に学習ソフトが映っています。");
        when(geometryAiClient.call(any())).thenReturn(GeometryAiClient.AiResponse.success(200, envelope(content)));

        StudyMonitorAiClient.AnalyzeResult result = client.analyze(request());

        assertThat(result.stateCode()).isEqualTo("STUDY_PC");
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.reason()).isEqualTo("PCの画面に学習ソフトが映っています。");
        // 一次応答JSON に入れるのは AI の生応答（封筒の中身）そのもの
        assertThat(result.rawResponse()).isEqualTo(content);
    }

    @Test
    @DisplayName("日本語の 6 状態をコードへ変換する")
    void convertsEveryStateCode() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("学習中（PC不使用・読書または筆記）", "STUDY_NO_PC");
        expected.put("学習中（PC使用）", "STUDY_PC");
        expected.put("離席中", "AWAY");
        expected.put("PC使用中（非学習）", "PC_NON_STUDY");
        expected.put("その他", "OTHER");
        expected.put("判断不可", "UNKNOWN");

        for (Map.Entry<String, String> entry : expected.entrySet()) {
            when(geometryAiClient.call(any())).thenReturn(GeometryAiClient.AiResponse.success(200,
                    envelope(reply(entry.getKey(), "0.5", "理由"))));

            assertThat(client.analyze(request()).stateCode())
                    .as(entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    @DisplayName("状態値が 6 つ以外なら失敗にする（生応答も残す）")
    void rejectsUnknownStatus() {
        String content = reply("食事中", "0.9", "理由");
        when(geometryAiClient.call(any())).thenReturn(GeometryAiClient.AiResponse.success(200, envelope(content)));

        StudyMonitorAiClient.AnalyzeException error = catchThrowableOfType(
                () -> client.analyze(request()), StudyMonitorAiClient.AnalyzeException.class);

        assertThat(error).hasMessageContaining("状態値が不正").hasMessageContaining("食事中");
        assertThat(error.rawResponse()).isEqualTo(content);
    }

    @Test
    @DisplayName("confidence が範囲外なら失敗にする")
    void rejectsConfidenceOutOfRange() {
        for (String confidence : new String[]{"1.5", "-0.1"}) {
            when(geometryAiClient.call(any())).thenReturn(GeometryAiClient.AiResponse.success(200,
                    envelope(reply("離席中", confidence, "理由"))));

            assertThatThrownBy(() -> client.analyze(request()))
                    .as(confidence)
                    .isInstanceOf(StudyMonitorAiClient.AnalyzeException.class)
                    .hasMessageContaining("confidence");
        }
    }

    @Test
    @DisplayName("confidence が無い応答も失敗にする")
    void rejectsMissingConfidence() {
        when(geometryAiClient.call(any())).thenReturn(GeometryAiClient.AiResponse.success(200,
                envelope("{\"status\":\"離席中\",\"reason\":\"理由\"}")));

        assertThatThrownBy(() -> client.analyze(request()))
                .isInstanceOf(StudyMonitorAiClient.AnalyzeException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    @DisplayName("confidence が数値文字列でも受ける（2.0 と同じ）")
    void acceptsNumericStringConfidence() {
        when(geometryAiClient.call(any())).thenReturn(GeometryAiClient.AiResponse.success(200,
                envelope(reply("その他", "\"0.75\"", "理由"))));

        assertThat(client.analyze(request()).confidence()).isEqualTo(0.75);
    }

    @Test
    @DisplayName("JSON でない応答は失敗にする")
    void rejectsNonJsonReply() {
        when(geometryAiClient.call(any())).thenReturn(GeometryAiClient.AiResponse.success(200, "<html>error</html>"));

        assertThatThrownBy(() -> client.analyze(request()))
                .isInstanceOf(StudyMonitorAiClient.AnalyzeException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    @DisplayName("応答が空なら失敗にする")
    void rejectsEmptyReply() {
        when(geometryAiClient.call(any())).thenReturn(GeometryAiClient.AiResponse.success(200, envelope("")));

        assertThatThrownBy(() -> client.analyze(request()))
                .isInstanceOf(StudyMonitorAiClient.AnalyzeException.class)
                .hasMessageContaining("空");
    }

    @Test
    @DisplayName("Markdown のコードフェンス付きでも読む（2.0 と同じ）")
    void stripsCodeFence() {
        String content = "```json\n" + reply("離席中", "0.8", "理由") + "\n```";
        when(geometryAiClient.call(any())).thenReturn(GeometryAiClient.AiResponse.success(200, envelope(content)));

        assertThat(client.analyze(request()).stateCode()).isEqualTo("AWAY");
    }

    @Test
    @DisplayName("画像 1 枚を OpenAI 互換の形で既存の接縫へ渡す")
    void sendsOpenAiCompatibleRequest() {
        when(geometryAiClient.call(any())).thenReturn(GeometryAiClient.AiResponse.success(200,
                envelope(reply("離席中", "0.8", "理由"))));

        client.analyze(request());

        ArgumentCaptor<GeometryAiClient.AiRequest> captor =
                ArgumentCaptor.forClass(GeometryAiClient.AiRequest.class);
        verify(geometryAiClient).call(captor.capture());
        GeometryAiClient.AiRequest sent = captor.getValue();
        assertThat(sent.provider()).isEqualTo("qwen");
        assertThat(sent.model()).isEqualTo("Qwen3-VL-Flash");
        assertThat(sent.apiKey()).isEqualTo("secret-key");
        // 末尾が /v1 か /compatible-mode のときは /chat/completions を補う（2.0 と同じ）
        assertThat(sent.url()).isEqualTo("https://example.com/compatible-mode/v1/chat/completions");
        assertThat(sent.systemPrompt()).isEqualTo("役割：判定アシスタントです。");
        assertThat(sent.userPrompt()).isEqualTo("次の画像を分析してください。");
        assertThat(sent.image()).isEqualTo(IMAGE);
        assertThat(sent.imageMime()).isEqualTo("image/jpeg");
        assertThat(sent.timeoutSeconds()).isEqualTo(120);
        assertThat(sent.temperature()).isEqualTo(0.1);
        assertThat(sent.jsonResponse()).isTrue();
        assertThat(sent.maxCompletionTokens()).isPositive();
    }

    @Test
    @DisplayName("末尾が /chat/completions の URL はそのまま使う")
    void keepsExplicitEndpoint() {
        assertThat(StudyMonitorAiClient.endpointUrl("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"))
                .isEqualTo("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions");
        assertThat(StudyMonitorAiClient.endpointUrl("https://example.com/v1"))
                .isEqualTo("https://example.com/v1/chat/completions");
        assertThat(StudyMonitorAiClient.endpointUrl("https://example.com/compatible-mode"))
                .isEqualTo("https://example.com/compatible-mode/chat/completions");
    }

    @Test
    @DisplayName("呼び出し 1 回につき AI 呼出履歴に 1 行残す")
    void recordsAiCallHistory() {
        String content = reply("学習中（PC使用）", "0.9", "理由");
        when(geometryAiClient.call(any())).thenReturn(GeometryAiClient.AiResponse.success(200, envelope(content)));

        client.analyze(request());

        ArgumentCaptor<AiCallLogEntity> captor = ArgumentCaptor.forClass(AiCallLogEntity.class);
        verify(aiCallLogMapper).insert(captor.capture());
        AiCallLogEntity call = captor.getValue();
        assertThat(call.getBatchCode()).isEqualTo("batL03");
        assertThat(call.getProcessKey()).isEqualTo("study-monitor/snapshot/12/first");
        assertThat(call.getLanguage()).isEqualTo("ja");
        assertThat(call.getAiType()).isEqualTo("qwen");
        assertThat(call.getModelName()).isEqualTo("Qwen3-VL-Flash");
        assertThat(call.getCallUrl()).isEqualTo("https://example.com/compatible-mode/v1/chat/completions");
        assertThat(call.getHttpStatus()).isEqualTo(200);
        assertThat(call.getStartTime()).isNotNull();
        assertThat(call.getEndTime()).isNotNull();
        assertThat(call.getDurationMs()).isNotNegative();
        assertThat(call.getResult()).isEqualTo("SUCCESS");
        assertThat(call.getErrorCode()).isNull();
        assertThat(call.getErrorMessage()).isNull();
        assertThat(call.getPrompt()).contains("[system]").contains("役割：判定アシスタントです。")
                .contains("[user]").contains("次の画像を分析してください。");
        assertThat(call.getResponse()).contains("学習中（PC使用）");
        assertThat(call.getExecutionId()).isEqualTo(77L);
        assertThat(call.getCreatedBy()).isNull();
        assertThat(call.getSourceCode()).isEqualTo("BATCH");
    }

    @Test
    @DisplayName("AI の呼び出しが失敗したら履歴に FAILURE を残して例外にする")
    void recordsFailureAndThrows() {
        when(geometryAiClient.call(any())).thenReturn(GeometryAiClient.AiResponse.failure(500, "HTTP_5XX",
                "AI の呼び出しに失敗しました（HTTP 500）。"));

        assertThatThrownBy(() -> client.analyze(request()))
                .isInstanceOf(StudyMonitorAiClient.AnalyzeException.class)
                .hasMessageContaining("HTTP 500");

        ArgumentCaptor<AiCallLogEntity> captor = ArgumentCaptor.forClass(AiCallLogEntity.class);
        verify(aiCallLogMapper).insert(captor.capture());
        AiCallLogEntity call = captor.getValue();
        assertThat(call.getResult()).isEqualTo("FAILURE");
        assertThat(call.getErrorCode()).isEqualTo("HTTP_5XX");
        assertThat(call.getErrorMessage()).contains("HTTP 500");
        assertThat(call.getHttpStatus()).isEqualTo(500);
        assertThat(call.getResponse()).isNull();
    }
}
