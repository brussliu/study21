package com.study21.admin.englishessay;

import com.study21.admin.batch.BatchService;
import com.study21.admin.controller.EnglishEssayAiBatchController;
import com.study21.admin.setting.SettingsService;
import com.study21.common.core.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 英作文の入口（{@code /api/admin/batch/english-essay}）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>{@code POST /ocr}: multipart（{@code level} / {@code categories} / {@code files}）を受け、
 *       決められた形（{@code questionText} / {@code essayText} / {@code pages} / 信頼度）を返す</li>
 *   <li>区分の既定は「先頭が question・残り answer」。数の食い違いは 400</li>
 *   <li>{@code POST /gradings}: <b>受付だけ</b>して {@code gradingId} と日本語の案内を返す
 *       （機能が無効・batC12 が無効なら拒否）</li>
 *   <li>{@code GET /gradings/{id}}: 画面のポーリング用の状態</li>
 * </ol>
 */
class EnglishEssayAiBatchControllerTest {

    private static final String BASE = "/api/admin/batch/english-essay";

    private EnglishEssayOcrStep ocrStep;
    private EnglishEssayAiQueue queue;
    private SettingsService settingsService;
    private BatchService batchService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ocrStep = mock(EnglishEssayOcrStep.class);
        queue = mock(EnglishEssayAiQueue.class);
        settingsService = mock(SettingsService.class);
        batchService = mock(BatchService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EnglishEssayAiBatchController(ocrStep, queue, settingsService, batchService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(settingsService.findGlobal("ENGLISH_ESSAY", "ENGLISH_ESSAY_ENABLED"))
                .thenReturn(Optional.of("true"));
    }

    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("files", name, "image/png", content.getBytes());
    }

    @Test
    @DisplayName("POST /ocr: 区分ごとの本文・pages・信頼度を返す")
    void recognizesImages() throws Exception {
        when(ocrStep.recognizeRequest(any(), any(), any())).thenReturn(new EnglishEssayOcrStep.OcrResult(
                "Do you agree?", "I think so.",
                List.of(new EnglishEssayOcrStep.PageResult("question", "Do you agree?", 93),
                        new EnglishEssayOcrStep.PageResult("answer", "I think so.", 88)),
                93, 88));

        mockMvc.perform(multipart(BASE + "/ocr")
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
                .andExpect(jsonPath("$.data.essayConfidence").value(88));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EnglishEssayOcrStep.ImageInput>> captor = ArgumentCaptor.forClass(List.class);
        verify(ocrStep).recognizeRequest(eq(null), eq(EnglishEssayLevel.PRE1), captor.capture());
        assertThat(captor.getValue()).extracting(EnglishEssayOcrStep.ImageInput::category)
                .containsExactly("question", "answer");
        assertThat(captor.getValue().get(0).fileName()).isEqualTo("q.png");
        assertThat(captor.getValue().get(0).bytes()).containsExactly("q".getBytes());
        assertThat(captor.getValue().get(0).mime()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("POST /ocr: 区分を省略したら「先頭が question・残り answer」")
    void defaultsCategories() throws Exception {
        when(ocrStep.recognizeRequest(any(), any(), any())).thenReturn(new EnglishEssayOcrStep.OcrResult(
                "q", "a", List.of(), 90, 90));

        mockMvc.perform(multipart(BASE + "/ocr")
                        .file(file("q.png", "q"))
                        .file(file("a1.png", "a1"))
                        .file(file("a2.png", "a2"))
                        .param("level", "grade2"))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EnglishEssayOcrStep.ImageInput>> captor = ArgumentCaptor.forClass(List.class);
        verify(ocrStep).recognizeRequest(eq(null), eq(EnglishEssayLevel.GRADE2), captor.capture());
        assertThat(captor.getValue()).extracting(EnglishEssayOcrStep.ImageInput::category)
                .containsExactly("question", "answer", "answer");
    }

    @Test
    @DisplayName("POST /ocr: 級が不正なら 400（日本語の理由）")
    void rejectsInvalidLevel() throws Exception {
        mockMvc.perform(multipart(BASE + "/ocr")
                        .file(file("q.png", "q"))
                        .param("level", "EIKEN1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("英検級")));

        verify(ocrStep, never()).recognizeRequest(any(), any(), any());
    }

    @Test
    @DisplayName("POST /ocr: 区分の数が画像より多い・区分が不正なら 400")
    void rejectsInvalidCategories() throws Exception {
        mockMvc.perform(multipart(BASE + "/ocr")
                        .file(file("q.png", "q"))
                        .param("level", "PRE1")
                        .param("categories", "question,answer"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart(BASE + "/ocr")
                        .file(file("q.png", "q"))
                        .param("level", "PRE1")
                        .param("categories", "question,unknown"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /ocr: 画像が無ければ 400")
    void rejectsNoFiles() throws Exception {
        mockMvc.perform(multipart(BASE + "/ocr").param("level", "PRE1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /ocr: 機能が無効なら 400（黙って何もしない）")
    void rejectsWhenDisabled() throws Exception {
        when(settingsService.findGlobal("ENGLISH_ESSAY", "ENGLISH_ESSAY_ENABLED"))
                .thenReturn(Optional.of("false"));

        mockMvc.perform(multipart(BASE + "/ocr")
                        .file(file("q.png", "q"))
                        .param("level", "PRE1"))
                .andExpect(status().isBadRequest());

        verify(ocrStep, never()).recognizeRequest(any(), any(), any());
    }

    @Test
    @DisplayName("POST /ocr: batC11 が無効なら 400（同期でもバッチ一覧の有効／無効が効く）")
    void rejectsWhenOcrBatchIsDisabled() throws Exception {
        org.mockito.Mockito.doThrow(new com.study21.common.core.exception.ValidationException(
                        "バッチが無効に設定されています: batC11（バッチ一覧で有効にしてください）"))
                .when(batchService).requireCallable("batC11");

        mockMvc.perform(multipart(BASE + "/ocr")
                        .file(file("q.png", "q"))
                        .param("level", "PRE1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("batC11")));

        verify(ocrStep, never()).recognizeRequest(any(), any(), any());
    }

    @Test
    @DisplayName("POST /gradings: 受付だけして gradingId と案内を返す（batC12 の有効を確認）")
    void acceptsGrading() throws Exception {
        when(queue.accept(900001L, 2)).thenReturn(new EnglishEssayAiQueue.Accepted(501L, 2));

        mockMvc.perform(post(BASE + "/gradings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"essayId\":900001,\"round\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gradingId").value(501))
                .andExpect(jsonPath("$.data.round").value(2))
                .andExpect(jsonPath("$.data.message").value(
                        "英検基準AI添削を受付けました（第 2 回）。バックグラウンドで処理します。"));

        verify(batchService).requireCallable("batC12");
        verify(queue).accept(900001L, 2);
    }

    @Test
    @DisplayName("POST /gradings: essayId が無ければ 400")
    void rejectsGradingWithoutEssayId() throws Exception {
        mockMvc.perform(post(BASE + "/gradings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(queue, never()).accept(any(), any());
    }

    @Test
    @DisplayName("POST /gradings: 機能が無効なら 400")
    void rejectsGradingWhenDisabled() throws Exception {
        when(settingsService.findGlobal("ENGLISH_ESSAY", "ENGLISH_ESSAY_ENABLED"))
                .thenReturn(Optional.of("false"));

        mockMvc.perform(post(BASE + "/gradings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"essayId\":900001}"))
                .andExpect(status().isBadRequest());

        verify(queue, never()).accept(any(), any());
    }

    @Test
    @DisplayName("POST /gradings: batC12 が無効なら日本語の理由で拒否する")
    void rejectsGradingWhenBatchDisabled() throws Exception {
        org.mockito.Mockito.doThrow(new com.study21.common.core.exception.ValidationException(
                        "バッチ batC12 は無効です。バッチ一覧で有効にしてください。"))
                .when(batchService).requireCallable("batC12");

        mockMvc.perform(post(BASE + "/gradings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"essayId\":900001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("batC12")));

        verify(queue, never()).accept(any(), any());
    }

    @Test
    @DisplayName("GET /gradings/{id}: 画面のポーリング用の状態を返す")
    void returnsGradingStatus() throws Exception {
        when(queue.status(501L)).thenReturn(new java.util.LinkedHashMap<>(Map.of(
                "gradingId", 501L, "round", 2, "statusCode", "SUCCEEDED",
                "score", 11, "maxScore", 16)));

        mockMvc.perform(get(BASE + "/gradings/501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gradingId").value(501))
                .andExpect(jsonPath("$.data.round").value(2))
                .andExpect(jsonPath("$.data.statusCode").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.score").value(11))
                .andExpect(jsonPath("$.data.maxScore").value(16));

        verify(queue).status(501L);
        verify(queue, never()).accept(any(), any());
    }
}
