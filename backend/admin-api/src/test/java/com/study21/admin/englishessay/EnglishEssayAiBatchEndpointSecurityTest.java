package com.study21.admin.englishessay;

import com.study21.admin.batch.BatchService;
import com.study21.admin.config.SecurityConfig;
import com.study21.admin.controller.EnglishEssayAiBatchController;
import com.study21.admin.internal.InternalServiceAuthorizer;
import com.study21.admin.setting.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * **英作文の AI の入口（admin-api の内部入口）が本当に守られているか**を、**実際の Spring Security の
 * フィルタ経由**で確かめる。
 *
 * <p>この入口は**画面から直接叩かない**。利用者の権限（ログイン・作文の所有権）は user-api の
 * {@code POST /api/user/english-essays/{essayId}/gradings} と
 * {@code POST /api/user/english-essays/ocr} が確かめ、**そこからサービス間の合言葉**
 * （{@code X-Internal-Token}）付きで呼ばれる。したがってここは
 * {@link InternalServiceAuthorizer} で守られており、**匿名では通らない**（合言葉が未設定なら全て拒否）。</p>
 *
 * <p>見張るのは「広い {@code /api/admin/batch/**} の {@code permitAll} に食われていないこと」。
 * 以前はこの入口が {@code permitAll} で、{@code essayId} を差し替えるだけで**他人の作文**に
 * 添削を積めた（AI 費用を使わせられる）。**同期 OCR も同じ穴だった**（URL を知っていれば
 * 誰でも AI を呼べた）。**規則の順序を入れ替えるとこのテストが落ちる**。</p>
 */
@WebMvcTest(controllers = EnglishEssayAiBatchController.class, properties = {
    // 検証用の合言葉（本番の値ではない）
    "study21.internal.token=it-internal-token"
})
@Import({ SecurityConfig.class, InternalServiceAuthorizer.class,
        EnglishEssayAiBatchEndpointSecurityTest.StubBeans.class })
class EnglishEssayAiBatchEndpointSecurityTest {

    /** 検証用の合言葉（本番の値ではない。コードにもリポジトリにも置かない）。 */
    private static final String TOKEN = "it-internal-token";

    private static final String GRADINGS = "/api/admin/batch/english-essay/gradings";

    private static final String OCR = "/api/admin/batch/english-essay/ocr";

    /** 業務の替え玉（本物の AI もバッチも走らせない）。 */
    @TestConfiguration
    static class StubBeans {

        @Bean
        EnglishEssayOcrStep englishEssayOcrStep() {
            return Mockito.mock(EnglishEssayOcrStep.class);
        }

        @Bean
        EnglishEssayAiQueue englishEssayAiQueue() {
            return Mockito.mock(EnglishEssayAiQueue.class);
        }

        @Bean
        SettingsService settingsService() {
            return Mockito.mock(SettingsService.class);
        }

        @Bean
        BatchService batchService() {
            return Mockito.mock(BatchService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    /** 業務の替え玉（**呼ばれたかどうか**を見る。本物の AI は走らせない）。 */
    @Autowired
    private EnglishEssayAiQueue queue;

    /** OCR の替え玉（**AI を呼ばずに**、入口の守りだけを見る）。 */
    @Autowired
    private EnglishEssayOcrStep ocrStep;

    @Autowired
    private SettingsService settingsService;

    @BeforeEach
    void setUp() {
        Mockito.reset(queue, settingsService, ocrStep);
        // 機能有効（受付の手前で弾かれないようにする）
        when(settingsService.findGlobal("ENGLISH_ESSAY", "ENGLISH_ESSAY_ENABLED"))
                .thenReturn(Optional.of("true"));
        when(queue.accept(anyLong(), any())).thenReturn(new EnglishEssayAiQueue.Accepted(501L, 2));
        when(ocrStep.recognizeRequest(any(), any(), any())).thenReturn(new EnglishEssayOcrStep.OcrResult(
                "Do you agree?", "I think so.",
                List.of(new EnglishEssayOcrStep.PageResult("question", "Do you agree?", 93),
                        new EnglishEssayOcrStep.PageResult("answer", "I think so.", 88)),
                93, 88));
    }

    private org.springframework.test.web.servlet.ResultActions acceptGrading(String token) throws Exception {
        MockHttpServletRequestBuilder request = post(GRADINGS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"essayId\":900001,\"round\":2}");
        if (token != null) {
            request = request.header("X-Internal-Token", token);
        }
        return mockMvc.perform(request);
    }

    /** 同期 OCR（multipart）を 1 回叩く（合言葉は指定されたときだけ付ける）。 */
    private org.springframework.test.web.servlet.ResultActions recognize(String token) throws Exception {
        MockHttpServletRequestBuilder request = multipart(OCR)
                .file(new MockMultipartFile("files", "q.png", "image/png", "q".getBytes()))
                .param("level", "PRE1")
                .param("categories", "question");
        if (token != null) {
            request = request.header("X-Internal-Token", token);
        }
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("① 匿名で添削の受付を叩くと拒否（広い permitAll に食われていない）")
    void anonymousCannotAcceptGrading() throws Exception {
        int status = acceptGrading(null).andReturn().getResponse().getStatus();

        assertThat(status)
                .as("匿名で %s が通ってはいけない", GRADINGS)
                .isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
        // **業務は呼ばれない**（他人の作文に添削を積めない）
        verify(queue, never()).accept(anyLong(), any());
    }

    @Test
    @DisplayName("② 合言葉が違えば拒否（正しい合言葉では届く＝判定が効いている）")
    void wrongTokenIsRejected() throws Exception {
        for (String token : new String[] { "wrong-token", "it-internal-toke", "", "   " }) {
            int status = acceptGrading(token).andReturn().getResponse().getStatus();
            assertThat(status)
                    .as("合言葉が違えば業務に届かない（token=%s）", token)
                    .isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
        }
        verify(queue, never()).accept(anyLong(), any());

        // **正しい**合言葉なら届く（替え玉が応答する）
        var response = acceptGrading(TOKEN).andReturn().getResponse();
        assertThat(response.getStatus())
                .as("合言葉が正しければ Controller まで届く")
                .isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentAsString()).contains("\"gradingId\":501");
        // 合言葉そのものは**応答に出さない**
        assertThat(response.getHeaderNames()).doesNotContain("X-Internal-Token");
        assertThat(response.getContentAsString()).doesNotContain(TOKEN);
        verify(queue).accept(900001L, 2);
    }

    @Test
    @DisplayName("③ 状態の問い合わせ（GET /gradings/{id}）も匿名では届かない")
    void anonymousCannotReadGradingStatus() throws Exception {
        int status = mockMvc.perform(get(GRADINGS + "/501")).andReturn().getResponse().getStatus();

        assertThat(status).isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
        verify(queue, never()).status(anyLong());

        // 正しい合言葉なら届く
        when(queue.status(501L)).thenReturn(new java.util.LinkedHashMap<>());
        int accepted = mockMvc.perform(get(GRADINGS + "/501").header("X-Internal-Token", TOKEN))
                .andReturn().getResponse().getStatus();
        assertThat(accepted).isEqualTo(HttpStatus.OK.value());
        verify(queue).status(501L);
    }

    @Test
    @DisplayName("④ 匿名で同期 OCR を叩くと拒否（URL を知っていても AI を使えない）")
    void anonymousCannotRecognizeImages() throws Exception {
        int status = recognize(null).andReturn().getResponse().getStatus();

        assertThat(status)
                .as("匿名で %s が通ってはいけない（AI の費用を使わせない）", OCR)
                .isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
        // **OCR は走らない**（AI を呼ばない）
        verify(ocrStep, never()).recognizeRequest(any(), any(), any());
    }

    @Test
    @DisplayName("⑤ 同期 OCR も合言葉が違えば拒否（正しい合言葉では届く＝判定が効いている）")
    void wrongTokenCannotRecognizeImages() throws Exception {
        for (String token : new String[] { "wrong-token", "it-internal-toke", "", "   " }) {
            int status = recognize(token).andReturn().getResponse().getStatus();
            assertThat(status)
                    .as("合言葉が違えば OCR まで届かない（token=%s）", token)
                    .isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
        }
        verify(ocrStep, never()).recognizeRequest(any(), any(), any());

        // **正しい**合言葉なら届く（替え玉が応答する）
        var response = recognize(TOKEN).andReturn().getResponse();
        assertThat(response.getStatus())
                .as("合言葉が正しければ Controller まで届く")
                .isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentAsString()).contains("Do you agree?");
        // 合言葉そのものは**応答に出さない**
        assertThat(response.getHeaderNames()).doesNotContain("X-Internal-Token");
        assertThat(response.getContentAsString()).doesNotContain(TOKEN);
        verify(ocrStep).recognizeRequest(any(), any(), any());
    }
}
