package com.study21.user.english;

import com.study21.common.core.exception.GlobalExceptionHandler;
import com.study21.common.core.exception.NotFoundException;
import com.study21.user.account.AccountType;
import com.study21.user.controller.EnglishEssayController;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 英作文の**入口の形を固定する**テスト（画面を作る別の担当者がこの契約に合わせる）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>`POST /api/user/english-essays/{essayId}/gradings` … 本文 `{"round"?:2}`、応答
 *       `data = {gradingId, round, message}`（`round` を省いたときはサービスへ null を渡す）</li>
 *   <li>`GET /api/user/english-essays/limits` … `data = {maxImages, maxImageMb}`</li>
 *   <li>`PUT /api/user/english-essays/{essayId}` の `images[]` に `recognizedText` / `confidence`
 *       （画像ごとの OCR の生の結果）が載る</li>
 *   <li>他人・存在しない・削除済みは 404（応答の形も日本語の理由）</li>
 * </ol>
 */
class EnglishEssayEndpointContractTest {

    private static final long ESSAY_ID = 900001L;

    /** ログイン中の生徒（`@AuthenticationPrincipal` で Controller が受け取る人）。 */
    private static final UserPrincipal STUDENT =
            new UserPrincipal(2L, "s-e2e@example.com", "検証 生徒", AccountType.STUDENT);

    private EnglishEssayService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(EnglishEssayService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EnglishEssayController(service))
                // ログイン中の人を `@AuthenticationPrincipal` で受け取れるようにする
                // （standalone では自動登録されない。これが無いと引数が 400 になる）
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(STUDENT, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /{essayId}/gradings: round を指定して受け付け、受付けた回と案内を返す")
    void acceptsGrading() throws Exception {
        when(service.acceptGrading(any(), eq(ESSAY_ID), eq(2))).thenReturn(
                new EnglishEssayModels.GradingAccepted(7001L, 2,
                        "英検基準AI添削を受付けました（第 2 回）。バックグラウンドで処理します。"));

        mockMvc.perform(post("/api/user/english-essays/" + ESSAY_ID + "/gradings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"round\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.gradingId").value(7001))
                .andExpect(jsonPath("$.data.round").value(2))
                .andExpect(jsonPath("$.data.message").value(
                        "英検基準AI添削を受付けました（第 2 回）。バックグラウンドで処理します。"))
                .andExpect(jsonPath("$.message").value(
                        "英検基準AI添削を受付けました（第 2 回）。バックグラウンドで処理します。"));

        verify(service).acceptGrading(STUDENT, ESSAY_ID, 2);
    }

    @Test
    @DisplayName("POST /{essayId}/gradings: round を省いたら null で受付ける（次の回は admin-api が決める）")
    void acceptsGradingWithoutRound() throws Exception {
        when(service.acceptGrading(any(), eq(ESSAY_ID), any())).thenReturn(
                new EnglishEssayModels.GradingAccepted(7002L, 3, "英検基準AI添削を受付けました（第 3 回）。"));

        // 本文が `{}`（round を書かない）でも受付ける＝次の回は admin-api が決める
        mockMvc.perform(post("/api/user/english-essays/" + ESSAY_ID + "/gradings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.round").value(3));

        ArgumentCaptor<Integer> round = ArgumentCaptor.forClass(Integer.class);
        verify(service, org.mockito.Mockito.times(1)).acceptGrading(eq(STUDENT), eq(ESSAY_ID), round.capture());
        assertThat(round.getValue()).isNull();
    }

    @Test
    @DisplayName("POST /{essayId}/gradings: 自分の作文でなければ 404（日本語の理由）")
    void hidesOtherAccountsEssay() throws Exception {
        when(service.acceptGrading(any(), anyLong(), any()))
                .thenThrow(new NotFoundException("英作文が見つかりません。"));

        mockMvc.perform(post("/api/user/english-essays/" + ESSAY_ID + "/gradings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("英作文が見つかりません。"));
    }

    @Test
    @DisplayName("GET /limits: 画像の上限（maxImages / maxImageMb）を返す")
    void returnsLimits() throws Exception {
        when(service.limits()).thenReturn(new EnglishEssayModels.ImageLimits(8, 10));

        mockMvc.perform(get("/api/user/english-essays/limits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxImages").value(8))
                .andExpect(jsonPath("$.data.maxImageMb").value(10));
    }

    @Test
    @DisplayName("PUT /{essayId}: images[] の recognizedText / confidence をそのまま受け取る")
    void acceptsImageRecognitionOnUpdate() throws Exception {
        mockMvc.perform(put("/api/user/english-essays/" + ESSAY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"level":"GRADE1","title":"題","titleZh":null,"questionText":"設問",
                                 "essayText":"one two","images":[
                                   {"imageId":9001,"category":"question","order":1,
                                    "recognizedText":"Do you agree?","confidence":93},
                                   {"imageId":9002,"category":"answer","order":2}]}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<EnglishEssayModels.UpdateRequest> captor =
                ArgumentCaptor.forClass(EnglishEssayModels.UpdateRequest.class);
        verify(service).update(any(), eq(ESSAY_ID), captor.capture());
        var images = captor.getValue().images();
        assertThat(images).hasSize(2);
        assertThat(images.get(0).imageId()).isEqualTo(9001L);
        assertThat(images.get(0).recognizedText()).isEqualTo("Do you agree?");
        assertThat(images.get(0).confidence()).isEqualTo(93);
        // 送らなかった画像は「指定なし」（既存値を消さない）
        assertThat(images.get(1).recognizedText()).isNull();
        assertThat(images.get(1).confidence()).isNull();
    }
}
