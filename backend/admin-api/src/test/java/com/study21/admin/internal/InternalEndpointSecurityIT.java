package com.study21.admin.internal;

import com.study21.common.security.internal.InternalTokenFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **admin-api の内部入口が実際に守られているか**を、**実際の Spring Security のフィルタ経由**で
 * 確かめる（`TestRestTemplate` で本当に HTTP を投げる。Controller を直接呼ばない）。
 *
 * <p>守るべき入口:</p>
 * <ul>
 *   <li>`/api/admin/batch/classroom/**` … user-api からのみ（**合言葉** `X-Internal-Token`）</li>
 * </ul>
 *
 * <p>見張るのは「広い `permitAll` に食われていないこと」。`/api/admin/batch/**` は既存の
 * バッチ管理 UI の都合で許可されているので、**具体的な規則が先に効いていないと匿名で通る**。</p>
 *
 * <p>合言葉は検証用の値を {@code study21.internal.token} に渡す（**リポジトリには置かない**）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    // 起動時のバッチ復旧・スケジューラは止める（この検証には要らない）
    "study21.batch.auto-run.recovery-enabled=false",
    "study21.batch.auto-run.schedule-enabled=false",
    // 検証用の合言葉（本番の値ではない。コードにもリポジトリにも置かない）
    "study21.internal.token=it-internal-token"
})
class InternalEndpointSecurityIT {

    /** 検証用の合言葉（上の properties と同じ値）。 */
    private static final String TOKEN = "it-internal-token";

    @Autowired
    private TestRestTemplate rest;

    private ResponseEntity<String> post(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set(InternalTokenFilter.HEADER_NAME, token);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);
    }

    @Test
    @DisplayName("① 匿名で単条回復・一括回復・起動を叩くと 403（広い permitAll に食われていない）")
    void anonymousCannotReachClassroomSummaryEndpoints() {
        for (String path : new String[] {
            "/api/admin/batch/classroom/notes/1/recover",
            "/api/admin/batch/classroom/notes/recover",
            "/api/admin/batch/classroom/notes/1/run"
        }) {
            ResponseEntity<String> response = post(path, null);
            /*
             * 匿名は**入り口で断られる**（session が無いので 401、合言葉の判定に届けば 403）。
             * どちらでも「通っていない」ことが大事（200 や 404 で Controller に届いてはいけない）。
             */
            assertThat(response.getStatusCode())
                    .as("匿名で %s が Controller に届いてはいけない", path)
                    .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        }
    }

    @Test
    @DisplayName("② 合言葉が違えば拒否（正しい合言葉では届く＝判定が効いている）")
    void wrongTokenIsRejected() {
        String path = "/api/admin/batch/classroom/notes/999999/recover";
        for (String token : new String[] { "wrong-token", "", "   " }) {
            ResponseEntity<String> response = post(path, token);
            assertThat(response.getStatusCode())
                    .as("合言葉が違えば Controller に届かない（token=%s）", token)
                    .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        }
        // 同じパスへ**正しい**合言葉なら届く（404 = Controller が「ノートが無い」と答えた）
        assertThat(post(path, TOKEN).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("③ 正しい合言葉なら、内部入口に**到達する**（結果はノートの有無で決まる）")
    void correctTokenReachesTheEndpoint() {
        ResponseEntity<String> response = post("/api/admin/batch/classroom/notes/999999/recover", TOKEN);

        // 入口は通っている（ノートが無いので 404。ここが 403 のままなら保護で止まっている）
        assertThat(response.getStatusCode())
                .as("合言葉が正しければ Controller まで届く")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("④ 他のバッチ入口（広い許可のまま）は影響を受けていない")
    void otherBatchEndpointsKeepWorking() {
        // 仕様上 permitAll のままの入口（既存のバッチ管理 UI）。ここを閉めると無関係な機能が止まる
        ResponseEntity<String> response = post("/api/admin/batch/schedule/reload", null);
        assertThat(response.getStatusCode())
                .as("無関係なバッチ入口を巻き込んで止めていない（403 以外で応答する）")
                .isNotEqualTo(HttpStatus.FORBIDDEN);
    }
}
