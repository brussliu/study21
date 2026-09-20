package com.study21.admin.internal;

import com.study21.admin.classroomai.ClassroomAiPipelineService;
import com.study21.admin.config.SecurityConfig;
import com.study21.admin.controller.ClassroomAiBatchController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * **授業まとめの内部入口が本当に守られているか**を、**実際の Spring Security のフィルタ経由**で
 * 確かめる。
 *
 * <p>この検証は**フィルタの順序と合言葉の判定**だけを見るので、DB も起動時のバッチも読み込まない
 * （`@WebMvcTest` の薄いスライス）。業務の service は替え玉にして、**拒否された要求が
 * service に届かないこと**を確かめる。</p>
 *
 * <p>見張るのは「広い `permitAll` に食われていないこと」。`/api/admin/batch/**` は既存のバッチ管理
 * UI の都合で許可されているので、**具体的な規則が先に効いていないと匿名で通る**。</p>
 */
@WebMvcTest(controllers = ClassroomAiBatchController.class, properties = {
    // 検証用の合言葉（本番の値ではない）
    "study21.internal.token=it-internal-token"
})
@Import({ SecurityConfig.class, InternalServiceAuthorizer.class,
        ClassroomAiBatchEndpointSecurityTest.PipelineStub.class })
class ClassroomAiBatchEndpointSecurityTest {

    /** 検証用の合言葉（本番の値ではない。コードにもリポジトリにも置かない）。 */
    private static final String TOKEN = "it-internal-token";

    /** 業務の替え玉（本物の AI もバッチも走らせない）。 */
    @TestConfiguration
    static class PipelineStub {
        @Bean
        ClassroomAiPipelineService classroomAiPipelineService() {
            return Mockito.mock(ClassroomAiPipelineService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    /** 業務の替え玉（**呼ばれたかどうか**を見る。本物の AI もバッチも走らせない）。 */
    @Autowired
    private ClassroomAiPipelineService stub;

    @BeforeEach
    void setUp() {
        Mockito.reset(stub);
    }

    private org.springframework.test.web.servlet.ResultActions call(String path, String token) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content("{}");
        if (token != null) {
            request = request.header("X-Internal-Token", token);
        }
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("① 匿名で単条回復・一括回復・起動を叩くと拒否（広い permitAll に食われていない）")
    void anonymousCannotReachClassroomSummaryEndpoints() throws Exception {
        for (String path : new String[] {
            "/api/admin/batch/classroom/notes/1/recover",
            "/api/admin/batch/classroom/notes/recover",
            "/api/admin/batch/classroom/notes/1/run"
        }) {
            int status = call(path, null).andReturn().getResponse().getStatus();
            assertThat(status)
                    .as("匿名で %s が通ってはいけない", path)
                    .isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
        }
        // **業務は呼ばれない**（拒否された要求が service に届いていない）
        verify(stub, never()).recoverOne(org.mockito.ArgumentMatchers.anyLong());
        verify(stub, never()).recoverLostGenerations(anyInt());
        verify(stub, never()).accept(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("② 合言葉が違えば拒否（正しい合言葉では届く＝判定が効いている）")
    void wrongTokenIsRejected() throws Exception {
        String path = "/api/admin/batch/classroom/notes/999999/recover";
        for (String token : new String[] { "wrong-token", "", "   " }) {
            int status = call(path, token).andReturn().getResponse().getStatus();
            assertThat(status)
                    .as("合言葉が違えば service に届かない（token=%s）", token)
                    .isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
        }
        verify(stub, never()).recoverOne(org.mockito.ArgumentMatchers.anyLong());

        // **正しい**合言葉なら届く（替え玉が応答する）
        Mockito.when(stub.recoverOne(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new ClassroomAiPipelineService.RecoveryView(
                        1L, "GENERATING", "RUNNING", false, false, "実行中です。"));
        var response = call(path, TOKEN).andReturn().getResponse();
        assertThat(response.getStatus())
                .as("合言葉が正しければ Controller まで届く")
                .isEqualTo(HttpStatus.OK.value());
        // 合言葉そのものは**応答に出さない**
        assertThat(response.getHeaderNames()).doesNotContain("X-Internal-Token");
        assertThat(response.getContentAsString()).doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("③ 一括回復も匿名では届かない（業務を呼ばない）")
    void bulkRecoveryCannotBeCalledAnonymously() throws Exception {
        int status = call("/api/admin/batch/classroom/notes/recover", null)
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
        verify(stub, never()).recoverLostGenerations(anyInt());
    }

    @Test
    @DisplayName("④ 他のバッチ入口（広い許可のまま）は影響を受けていない")
    void otherBatchPathsAreNotCoveredByTheRule() throws Exception {
        // 守っているのは `/api/admin/batch/classroom/**` だけ。ほかのパスはこの規則の対象外
        // （`anyRequest().denyAll()` なので 401/403 になるが、**まとめの規則で拒否されたのではない**）
        int status = call("/api/admin/batch/schedule/reload", null).andReturn().getResponse().getStatus();
        assertThat(status)
                .as("無関係なバッチ入口を『まとめの規則』で止めていない")
                .isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value(),
                        HttpStatus.NOT_FOUND.value(), HttpStatus.METHOD_NOT_ALLOWED.value());
    }
}
