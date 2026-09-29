package com.study21.user.english;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.GlobalExceptionHandler;
import com.study21.common.core.exception.ValidationException;
import com.study21.user.controller.EnglishEssayOcrController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 同期 OCR の**公開入口**（`POST /api/user/english-essays/ocr`）の形を固定するテスト。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>multipart（`level` / `categories` / `files`。**admin-api と同じパラメータ名**）を受け、
 *       そのまま転送層へ渡す（ファイルの実体も渡る）</li>
 *   <li>応答は `data = {questionText, essayText, pages[], questionConfidence, essayConfidence}` を
 *       **そのまま**返す（画面が期待する形を勝手に変えない）</li>
 *   <li>失敗は日本語の理由つき（入力の誤りは 400、admin-api が断ったら 409）。
 *       黙って空の成功を返さない</li>
 * </ol>
 */
class EnglishEssayOcrControllerTest {

    private EnglishEssayOcrProxyService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(EnglishEssayOcrProxyService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EnglishEssayOcrController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("files", name, "image/png", content.getBytes());
    }

    private static com.fasterxml.jackson.databind.JsonNode ocrData() {
        try {
            return new ObjectMapper().readTree("{\"questionText\":\"Do you agree?\","
                    + "\"essayText\":\"I think so.\","
                    + "\"pages\":[{\"category\":\"question\",\"text\":\"Do you agree?\",\"confidence\":93},"
                    + "{\"category\":\"answer\",\"text\":\"I think so.\",\"confidence\":88}],"
                    + "\"questionConfidence\":93,\"essayConfidence\":88}");
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    @Test
    @DisplayName("POST /ocr: multipart をそのまま渡し、応答の data をそのまま返す")
    void forwardsMultipartAndReturnsData() throws Exception {
        when(service.recognize(any(), any(), any())).thenReturn(new EnglishEssayOcrProxyService.OcrResult(
                ocrData(), "英作文の画像を文字にしました（2 枚）。内容を確認してから添削を受付けてください。"));

        mockMvc.perform(multipart("/api/user/english-essays/ocr")
                        .file(file("q.png", "q"))
                        .file(file("a.png", "a"))
                        .param("level", "PRE1")
                        .param("categories", "question,answer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionText").value("Do you agree?"))
                .andExpect(jsonPath("$.data.essayText").value("I think so."))
                .andExpect(jsonPath("$.data.pages[0].category").value("question"))
                .andExpect(jsonPath("$.data.pages[0].text").value("Do you agree?"))
                .andExpect(jsonPath("$.data.pages[0].confidence").value(93))
                .andExpect(jsonPath("$.data.pages[1].category").value("answer"))
                .andExpect(jsonPath("$.data.questionConfidence").value(93))
                .andExpect(jsonPath("$.data.essayConfidence").value(88))
                .andExpect(jsonPath("$.message").value(
                        "英作文の画像を文字にしました（2 枚）。内容を確認してから添削を受付けてください。"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MultipartFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(service).recognize(eq("PRE1"), eq("question,answer"), captor.capture());
        assertThat(captor.getValue()).extracting(MultipartFile::getOriginalFilename)
                .containsExactly("q.png", "a.png");
        assertThat(captor.getValue().get(0).getBytes()).containsExactly("q".getBytes());
    }

    @Test
    @DisplayName("POST /ocr: 画像が無ければ 400（日本語の理由。転送しない）")
    void requiresImages() throws Exception {
        when(service.recognize(any(), any(), any()))
                .thenThrow(new ValidationException("画像を選んでください（設問画像と答案画像）。"));

        mockMvc.perform(multipart("/api/user/english-essays/ocr").param("level", "PRE1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("画像を選んでください（設問画像と答案画像）。"));
    }

    @Test
    @DisplayName("POST /ocr: admin-api が断ったら、その日本語の理由のまま返す（500 にしない）")
    void keepsUpstreamReason() throws Exception {
        when(service.recognize(any(), any(), any()))
                .thenThrow(new ConflictException("画像の区分は question / answer のいずれかです: memo"));

        mockMvc.perform(multipart("/api/user/english-essays/ocr")
                        .file(file("q.png", "q"))
                        .param("level", "PRE1")
                        .param("categories", "memo"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("画像の区分は question / answer のいずれかです: memo"));
    }
}
