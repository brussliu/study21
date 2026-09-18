package com.study21.admin.ai;

import com.study21.admin.geometryai.GeometryAiClient;
import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AIモデルページの【接続テスト】。
 *
 * <p>「AIモデル」ページの 4 つのチャット系プロバイダー（千問 / 豆包 / DeepSeek / OpenAI）は
 * すべて OpenAI 互換の {@code chat/completions} なので、**同じ 1 回の小さなチャット呼び出し**で
 * 接続（URL・API Key・モデル名）を確かめられる。BigModel / 智譜 OCR は画像を送る別形式なので対象外。</p>
 *
 * <p>画面（設定ランタイム）は 200 の本文にある {@code ok} を見て成功／失敗の表示を変える。
 * 設定の不備（未対応プロバイダー・キーや URL の欠落）は 400 にして、理由をそのまま画面へ出す。</p>
 */
class AiConnectionTesterTest {

    private GeometryAiClient aiClient;
    private AiConnectionTester tester;

    @BeforeEach
    void setUp() {
        aiClient = mock(GeometryAiClient.class);
        tester = new AiConnectionTester(aiClient);
    }

    private static GeometryAiClient.AiResponse ok() {
        return GeometryAiClient.AiResponse.success(200,
                "{\"choices\":[{\"message\":{\"content\":\"pong\"}}]}");
    }

    @Test
    void 接続できたら成功と分かる情報を返す() {
        when(aiClient.call(any())).thenReturn(ok());

        Map<String, Object> result = tester.test("qwen", "qwen3.7-flash",
                "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "secret-key");

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(result.get("provider")).isEqualTo("qwen");
        assertThat(result.get("model")).isEqualTo("qwen3.7-flash");
        assertThat(String.valueOf(result.get("message"))).contains("接続できました");
        assertThat(result.get("latencyMs")).isInstanceOf(Integer.class);

        // 送る中身は「小さな 1 回のチャット」だけ（画像なし・出力は短く）
        ArgumentCaptor<GeometryAiClient.AiRequest> captor =
                ArgumentCaptor.forClass(GeometryAiClient.AiRequest.class);
        verify(aiClient).call(captor.capture());
        GeometryAiClient.AiRequest request = captor.getValue();
        assertThat(request.provider()).isEqualTo("qwen");
        assertThat(request.model()).isEqualTo("qwen3.7-flash");
        assertThat(request.apiKey()).isEqualTo("secret-key");
        assertThat(request.image()).isNull();
        assertThat(request.jsonResponse()).isFalse();
        assertThat(request.maxCompletionTokens()).isLessThanOrEqualTo(32);
    }

    @Test
    void OpenAIはchatgptの名前でも受け付ける() {
        when(aiClient.call(any())).thenReturn(ok());

        for (String provider : new String[]{"openai", "chatgpt"}) {
            Map<String, Object> result = tester.test(provider, "gpt-5.4-mini",
                    "https://api.openai.com/v1/chat/completions", "sk-test");
            assertThat(result.get("ok")).as(provider).isEqualTo(true);
        }
    }

    @Test
    void 接続できなかったら理由を返す_例外にはしない() {
        when(aiClient.call(any())).thenReturn(GeometryAiClient.AiResponse.failure(401, "HTTP_4XX",
                "AI がリクエストを受け付けませんでした（HTTP 401）。API Key とモデル名を確認してください。"));

        Map<String, Object> result = tester.test("deepseek", "deepseek-v4-flash",
                "https://api.deepseek.com/v1/chat/completions", "bad-key");

        assertThat(result.get("ok")).isEqualTo(false);
        assertThat(String.valueOf(result.get("message"))).contains("HTTP 401");
    }

    @Test
    void 未対応のプロバイダーは400にする() {
        assertThatThrownBy(() -> tester.test("bigmodel", "glm-ocr", "https://example.com", "key"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("接続テストに対応していません");
        verify(aiClient, never()).call(any());
    }

    @Test
    void モデル名やURLやAPIキーが空なら400にする() {
        assertThatThrownBy(() -> tester.test("qwen", "  ", "https://example.com", "key"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("モデル名");
        assertThatThrownBy(() -> tester.test("qwen", "qwen3.7-flash", "", "key"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("URL");
        assertThatThrownBy(() -> tester.test("qwen", "qwen3.7-flash", "https://example.com", null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("API Key");
        verify(aiClient, never()).call(any());
    }
}
