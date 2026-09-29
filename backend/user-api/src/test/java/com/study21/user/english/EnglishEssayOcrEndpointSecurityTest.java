package com.study21.user.english;

import com.study21.user.account.AccountType;
import com.study21.user.config.SecurityConfig;
import com.study21.user.controller.EnglishEssayOcrController;
import com.study21.user.security.UserPrincipal;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * 同期 OCR の公開入口（`POST /api/user/english-essays/ocr`）が**ログイン必須**であることを、
 * **実際の Spring Security のフィルタ経由**で確かめる。
 *
 * <p>ここが匿名で通ると、**URL を知っているだけで誰でも AI を呼べる**（費用を使わせられる）。
 * 確かめるのは ①匿名は 401 で業務まで届かない ②ログイン済みは届く（`anyRequest().denyAll()` なので、
 * `SecurityConfig` の `/api/user/english-essays/**` の 1 行が消えると**ログインしていても 403**）。</p>
 */
@WebMvcTest(controllers = EnglishEssayOcrController.class)
@Import({ SecurityConfig.class, EnglishEssayOcrEndpointSecurityTest.StubBeans.class })
class EnglishEssayOcrEndpointSecurityTest {

    private static final String OCR = "/api/user/english-essays/ocr";

    /** ログイン中の生徒（ログインの作法は `AccountController#login` と同じ: session に SecurityContext）。 */
    private static final UserPrincipal STUDENT =
            new UserPrincipal(2L, "s-e2e@example.com", "検証 生徒", AccountType.STUDENT);

    @TestConfiguration
    static class StubBeans {

        @Bean
        EnglishEssayOcrProxyService englishEssayOcrProxyService() {
            return Mockito.mock(EnglishEssayOcrProxyService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EnglishEssayOcrProxyService service;

    /**
     * 替え玉の**呼び出し履歴を毎回消す**。
     *
     * <p>`@WebMvcTest` は同じ Bean（同じ Mockito の替え玉）をクラス内で使い回すので、消さないと
     * 「匿名では届かない」の検証が、**別のテストの呼び出し**を拾って落ちる（守りは効いている）。</p>
     */
    @BeforeEach
    void resetStub() {
        Mockito.reset(service);
    }

    /** その場で「ログイン済み」にする（`spring-security-test` は入れていないので自前で session に入れる）。 */
    private static RequestPostProcessor loggedIn() {
        return request -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(STUDENT, null, List.of()));
            SecurityContextHolder.setContext(context);
            request.getSession(true).setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            return request;
        };
    }

    private static MockMultipartFile image() {
        return new MockMultipartFile("files", "q.png", "image/png", "q".getBytes());
    }

    @Test
    @DisplayName("① 匿名で OCR を叩くと 401（業務まで届かない＝AI を呼べない）")
    void anonymousCannotRecognize() throws Exception {
        int status = mockMvc.perform(multipart(OCR).file(image()).param("level", "PRE1"))
                .andReturn().getResponse().getStatus();

        assertThat(status)
                .as("匿名で %s が通ってはいけない（AI の費用を使わせない）", OCR)
                .isEqualTo(HttpStatus.UNAUTHORIZED.value());
        verify(service, never()).recognize(any(), any(), any());
    }

    @Test
    @DisplayName("② ログイン済みは届く（`/api/user/english-essays/**` の規則が効いている）")
    void loggedInReachesController() throws Exception {
        when(service.recognize(any(), any(), any())).thenReturn(
                new EnglishEssayOcrProxyService.OcrResult(
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                                "{\"questionText\":\"q\",\"essayText\":\"a\",\"pages\":[],"
                                        + "\"questionConfidence\":0,\"essayConfidence\":0}"),
                        "英作文の画像を文字にしました。内容を確認してから添削を受付けてください。"));

        var response = mockMvc.perform(multipart(OCR).file(image()).param("level", "PRE1").with(loggedIn()))
                .andReturn().getResponse();

        assertThat(response.getStatus())
                .as("ログイン済みなら Controller まで届く（anyRequest().denyAll() に食われていない）")
                .isEqualTo(HttpStatus.OK.value());
        verify(service).recognize(any(), any(), any());
    }
}
