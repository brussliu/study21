package com.study21.user.english;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EnglishEssayOcrProxyService} の**転送の契約**の検証（画面の multipart を内部入口の形に写す）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>multipart の `MultipartFile` を**ファイルの実体**（名前・種類・中身）として渡す</li>
 *   <li>画像が無いときは**admin-api を呼ばずに** 400 の理由を返す（AI を無駄に呼ばない）</li>
 *   <li>admin-api が断ったら、その**日本語の理由のまま** 409 で返す（500 にしない）</li>
 *   <li>応答の `pages` の枚数を案内文に写す（画面に出す言葉を 2 か所で作らない）</li>
 * </ol>
 */
class EnglishEssayOcrProxyServiceTest {

    private EnglishEssayAiAdminClient adminClient;
    private EnglishEssayOcrProxyService service;

    @BeforeEach
    void setUp() {
        adminClient = mock(EnglishEssayAiAdminClient.class);
        service = new EnglishEssayOcrProxyService(adminClient);
    }

    private static MultipartFile file(String name, String mime, String content) {
        return new MockMultipartFile("files", name, mime, content.getBytes());
    }

    private static com.fasterxml.jackson.databind.JsonNode data(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    @Test
    @DisplayName("画像の実体（名前・種類・中身）と区分を admin-api へ渡し、pages の枚数を案内文にする")
    void mapsMultipartToInternalRequest() {
        when(adminClient.recognize(eq("PRE1"), eq("question,answer"), any())).thenReturn(data(
                "{\"questionText\":\"Do you agree?\",\"essayText\":\"I think so.\","
                        + "\"pages\":[{\"category\":\"question\",\"text\":\"Do you agree?\",\"confidence\":93}],"
                        + "\"questionConfidence\":93,\"essayConfidence\":88}"));

        EnglishEssayOcrProxyService.OcrResult result = service.recognize("PRE1", "question,answer",
                List.of(file("q.png", "image/png", "question-bytes"),
                        file("a.jpg", "image/jpeg", "answer-bytes")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EnglishEssayAiAdminClient.OcrImage>> captor = ArgumentCaptor.forClass(List.class);
        verify(adminClient).recognize(eq("PRE1"), eq("question,answer"), captor.capture());
        assertThat(captor.getValue()).extracting(EnglishEssayAiAdminClient.OcrImage::fileName)
                .containsExactly("q.png", "a.jpg");
        assertThat(captor.getValue()).extracting(EnglishEssayAiAdminClient.OcrImage::mime)
                .containsExactly("image/png", "image/jpeg");
        assertThat(new String(captor.getValue().get(0).bytes())).isEqualTo("question-bytes");

        // 応答の data はそのまま、案内文だけこちらで作る
        assertThat(result.data().get("questionText").asText()).isEqualTo("Do you agree?");
        assertThat(result.message()).isEqualTo("英作文の画像を文字にしました（1 枚）。"
                + "内容を確認してから添削を受付けてください。");
    }

    @Test
    @DisplayName("画像が無ければ admin-api を呼ばずに 400 の理由を返す")
    void rejectsEmptyImages() {
        assertThatThrownBy(() -> service.recognize("PRE1", null, List.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("画像を選んでください");
        assertThatThrownBy(() -> service.recognize("PRE1", null, null))
                .isInstanceOf(ValidationException.class);

        verify(adminClient, never()).recognize(any(), any(), any());
    }

    @Test
    @DisplayName("admin-api が断ったら、その日本語の理由のまま 409 で返す")
    void keepsUpstreamReason() {
        when(adminClient.recognize(any(), any(), any())).thenThrow(
                new EnglishEssayAiAdminClient.EnglishEssayCallException(
                        "英作文AI添削が無効になっています。", false));

        assertThatThrownBy(() -> service.recognize("PRE1", null, List.of(file("q.png", "image/png", "q"))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("英作文AI添削が無効になっています。");
    }
}
