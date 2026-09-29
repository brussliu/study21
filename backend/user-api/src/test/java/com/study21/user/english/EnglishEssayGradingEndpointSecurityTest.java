package com.study21.user.english;

import com.study21.user.config.SecurityConfig;
import com.study21.user.controller.EnglishEssayController;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 英作文の入口が**ログイン必須**であることを、**実際の Spring Security のフィルタ経由**で確かめる。
 *
 * <p>添削の受付（`POST /api/user/english-essays/{essayId}/gradings`）は、**画面が叩く唯一の入口**。
 * ここが匿名で通ると、所有者の確認（サービス層）まで辿り着けてしまう。</p>
 *
 * <p>確かめるのは**匿名が拒否されること**まで（ログイン済みが通ることは `SecurityConfig` の
 * 明示リスト `/api/user/english-essays/**` が担保する。`anyRequest().denyAll()` なので、
 * その 1 行が消えると**ログインしていても 403** になる。実セッションでの確認は
 * `EnglishEssayRepositoryTest`（サービス層）と実機の画面で行う）。</p>
 */
@WebMvcTest(controllers = EnglishEssayController.class)
@Import({ SecurityConfig.class, EnglishEssayGradingEndpointSecurityTest.StubBeans.class })
class EnglishEssayGradingEndpointSecurityTest {

    @TestConfiguration
    static class StubBeans {

        @Bean
        EnglishEssayService englishEssayService() {
            return Mockito.mock(EnglishEssayService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EnglishEssayService service;

    @Test
    @DisplayName("① 匿名で添削の受付を叩くと拒否（業務まで届かない）")
    void anonymousCannotAcceptGrading() throws Exception {
        int status = mockMvc.perform(post("/api/user/english-essays/900001/gradings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"round\":2}"))
                .andReturn().getResponse().getStatus();

        assertThat(status)
                .as("匿名で受付が通ってはいけない")
                .isEqualTo(HttpStatus.UNAUTHORIZED.value());
        verify(service, never()).acceptGrading(any(), anyLong(), any());
    }

    @Test
    @DisplayName("② 匿名で上限・一覧・詳細も読めない")
    void anonymousCannotReadEssayApis() throws Exception {
        for (String path : new String[] {
            "/api/user/english-essays/limits",
            "/api/user/english-essays",
            "/api/user/english-essays/900001"
        }) {
            int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertThat(status).as("匿名で %s が通ってはいけない", path)
                    .isEqualTo(HttpStatus.UNAUTHORIZED.value());
        }
        verify(service, never()).limits();
    }
}
