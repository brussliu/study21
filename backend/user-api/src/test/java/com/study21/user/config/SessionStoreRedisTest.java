package com.study21.user.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * セッションが Redis に保存されることの検証（実際の Redis を一時的に起動して確かめる）。
 *
 * <p>2.1 のセッションは Spring Session で Redis に置いている（`spring.session.store-type=redis`）。
 * アプリ側のコード（ログインで `session.setAttribute`）は変えていないため、
 * 「本当に Redis に入っているか」「有効期限が 60 分か」「ログアウトで消えるか」を
 * ここで押さえる。メモリのセッションに戻ってしまう変更（依存や設定の削除）があれば赤くなる。</p>
 *
 * <p>実行には DB のパスワードが要る（無いときはスキップする）:
 * {@code STUDY21_DATASOURCE_PASSWORD=... mvn -pl user-api test}</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class SessionStoreRedisTest {

    /** 検証用の Redis（このテストの中だけで生きる）。 */
    private static final int REDIS_PORT = findFreePort();
    private static final Optional<RedisServer> REDIS = startRedisIfEnabled();

    /** セッションのクッキー名（`server.servlet.session.cookie.name`）。 */
    private static final String COOKIE_NAME = "STUDY21_USER_SESSION";
    /** Redis の鍵の前置き（`spring.session.redis.namespace`）。 */
    private static final String NAMESPACE = "study21:session:user";
    /** 有効期限（60 分）。 */
    private static final long TIMEOUT_SECONDS = 60 * 60;

    private static final String PASSWORD = "Parent1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionRepository<? extends Session> sessionRepository;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    /** 空いているポートを 1 つ借りる（すぐ閉じるので、起動までの競合は許容する）。 */
    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException cause) {
            throw new IllegalStateException("空きポートを取得できませんでした。", cause);
        }
    }

    /** テスト用の Redis を起動する（DB のパスワードが無くて無効化されるときは起動しない）。 */
    private static Optional<RedisServer> startRedisIfEnabled() {
        String password = System.getenv("STUDY21_DATASOURCE_PASSWORD");
        if (password == null || password.isBlank()) {
            return Optional.empty();
        }
        try {
            RedisServer server = RedisServer.newRedisServer().port(REDIS_PORT).build();
            server.start();
            return Optional.of(server);
        } catch (IOException cause) {
            throw new IllegalStateException("検証用の Redis を起動できませんでした。", cause);
        }
    }

    @AfterAll
    static void stopRedis() throws IOException {
        if (REDIS.isPresent()) {
            REDIS.get().stop();
        }
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> REDIS_PORT);
        registry.add("spring.data.redis.password", () -> "");
    }

    @Test
    void sessionRepositoryIsRedisBacked() {
        // メモリ（Tomcat / MapSessionRepository）に戻っていたらここで気づく
        assertThat(sessionRepository.getClass().getName())
                .startsWith("org.springframework.session.data.redis");
    }

    @Test
    void loginStoresSessionInRedisWithSixtyMinuteTimeout() throws Exception {
        String email = registerAccount();

        MvcResult login = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email)))
                .andReturn();

        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String setCookie = login.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).as("クッキー名はサービスごとに分けている（admin-api とぶつけない）")
                .startsWith(COOKIE_NAME + "=")
                .doesNotContain("JSESSIONID");

        String cookieValue = setCookie.split(";", 2)[0].substring(COOKIE_NAME.length() + 1);
        // Spring Session はクッキーに Base64 で入れる（値そのものはセッションID）
        String sessionId = new String(Base64.getDecoder().decode(cookieValue), StandardCharsets.UTF_8);
        assertThat(sessionId).matches("[0-9a-f-]{36}");

        // Redis に「名前空間つきの鍵」で入っている
        String key = NAMESPACE + ":sessions:" + sessionId;
        Session stored = sessionRepository.findById(sessionId);
        assertThat(stored).as("セッションが Redis から読める").isNotNull();
        Object securityContext = stored.getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(securityContext).as("ログイン情報がセッションに入っている").isNotNull();

        // 有効期限は 60 分（数秒のずれは許す）
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            Long ttl = connection.keyCommands().ttl(key.getBytes(StandardCharsets.UTF_8));
            assertThat(ttl).as("セッションの有効期限は 60 分").isNotNull()
                    .isBetween(TIMEOUT_SECONDS - 60, TIMEOUT_SECONDS);
        }

        // クッキーを付ければ認証が通る（＝ Redis のセッションが効いている）
        mockMvc.perform(get("/api/user/profile").cookie(new jakarta.servlet.http.Cookie(COOKIE_NAME, cookieValue)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        // クッキーが無ければ 401
        mockMvc.perform(get("/api/user/profile"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));

        // ログアウトするとセッションが消え、同じクッキーでは通らない
        mockMvc.perform(post("/api/user/logout").cookie(new jakarta.servlet.http.Cookie(COOKIE_NAME, cookieValue)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
        Session afterLogout = sessionRepository.findById(sessionId);
        assertThat(afterLogout).as("ログアウトでセッションが消える").isNull();

        mockMvc.perform(get("/api/user/profile").cookie(new jakarta.servlet.http.Cookie(COOKIE_NAME, cookieValue)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));
    }

    // -------------------------------------------------------------------- 部品

    /** 検証用のアカウントを作る（テストのトランザクションでロールバックする）。 */
    private String registerAccount() throws Exception {
        String email = "e2e-session-" + System.nanoTime() + "@example.com";
        String body = """
                {"parentEmail":"%s","parentPassword":"%s",
                 "sei":"検証","mei":"保護者","seiKana":"けんしょう","meiKana":"ほごしゃ","grade":"中学1年生",
                 "studentEmail":"s-%s","studentPassword":"Student1234","agreed":true}
                """.formatted(email, PASSWORD, email);
        MvcResult registered = mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        assertThat(registered.getResponse().getStatus()).isEqualTo(200);
        return email;
    }

    private String loginBody(String email) {
        return "{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD);
    }
}
