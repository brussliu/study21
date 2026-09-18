package com.study21.user.controller;

import com.study21.user.account.AccountType;
import com.study21.user.classroom.ClassroomService;
import com.study21.user.classroom.ClassroomSttStreamService;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * `POST /api/user/classroom/{id}/stt/stream` の**欄の名前と渡し方**の検証。
 *
 * <p>画面（常時接続と同じ識別）は `?source=mic&amp;frameNo=101&amp;startSample=160000` を送る。
 * 名前が食い違うと、番号も位置も届かないまま「到着順に採番し、時間軸が 0 から始まる」経路へ
 * 黙って落ちる（張り直した録音の時刻が 0 に戻る）。ここでは HTTP の欄として実際に束ねて、
 * サービスの引数へそのまま渡ることを見る。</p>
 *
 * <p>渡さない（欄が無い）ときは今までどおり: 番号は到着順・位置は不明として渡す。</p>
 */
class ClassroomControllerSttStreamTest {

    private static final long RECORD_ID = 12L;
    private static final long ACCOUNT_ID = 2L;

    private ClassroomService classroomService;
    private ClassroomSttStreamService streamService;
    private MockMvc mockMvc;

    /** `@AuthenticationPrincipal` を解決する（Spring Security のフィルタを持ち込まない）。 */
    private static final UserPrincipal PRINCIPAL =
            new UserPrincipal(ACCOUNT_ID, "student@example.com", "生徒", AccountType.STUDENT);

    @BeforeEach
    void setUp() {
        classroomService = mock(ClassroomService.class);
        streamService = mock(ClassroomSttStreamService.class);
        when(classroomService.requireSttAudioAccountId(any(UserPrincipal.class), anyLong()))
                .thenReturn(ACCOUNT_ID);
        when(streamService.push(anyLong(), anyLong(), any(), any(), anyInt(), anyLong()))
                .thenReturn(new ClassroomSttStreamService.StreamPush("", List.of(), null, 0));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ClassroomController(classroomService, streamService))
                .setCustomArgumentResolvers(new PrincipalResolver())
                .build();
    }

    @Test
    @DisplayName("frameNo と startSample をそのまま受け取ってサービスへ渡す")
    void passesFrameIdentityToService() throws Exception {
        byte[] pcm = new byte[3_200];

        mockMvc.perform(post("/api/user/classroom/12/stt/stream")
                        .queryParam("source", "mic")
                        .queryParam("frameNo", "101")
                        .queryParam("startSample", "160000")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(pcm))
                .andExpect(status().isOk());

        verify(streamService).push(eq(RECORD_ID), eq(ACCOUNT_ID), eq("mic"), eq(pcm), eq(101), eq(160_000L));
    }

    @Test
    @DisplayName("欄が無いときは到着順・位置なしとして渡す（今までどおりの互換）")
    void fallsBackWhenIdentityIsMissing() throws Exception {
        byte[] pcm = new byte[3_200];

        mockMvc.perform(post("/api/user/classroom/12/stt/stream")
                        .queryParam("source", "shared")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(pcm))
                .andExpect(status().isOk());

        verify(streamService).push(eq(RECORD_ID), eq(ACCOUNT_ID), eq("shared"), eq(pcm), eq(0),
                eq(ClassroomSttStreamService.UNKNOWN_SAMPLE));
    }

    /** `@AuthenticationPrincipal UserPrincipal` に固定の利用者を返すだけの解決器。 */
    private static final class PrincipalResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return UserPrincipal.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                NativeWebRequest request, WebDataBinderFactory binderFactory) {
            return PRINCIPAL;
        }
    }
}
