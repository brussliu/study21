package com.study21.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.user.classroom.ClassroomNoteAdminClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * **まとめの起動・回復の権限**を、**実際の Spring Security のフィルタ経由**で確かめる。
 *
 * <p>見張るのは「他人の授業・他人のノートを操作できないこと」。画面は admin-api の内部入口を
 * 直接叩かず、この user-api の入口を通る。ここで**ログイン・授業の所有権・noteId の帰属**を
 * 確かめてから、はじめて下流（admin-api）を呼ぶ。</p>
 *
 * <ul>
 *   <li>未ログイン … 401（**下流を呼ばない**）</li>
 *   <li>他人の授業 … 404（存在を漏らさない）</li>
 *   <li>自分の授業だが**別の記録のノート** … 404（**下流を呼ばない**）</li>
 *   <li>ノートが無い・他人のノート … 404（**下流を呼ばない**）</li>
 *   <li>回復の入口に途中のノート（PHASE）… 400（この入口は最終まとめ専用）</li>
 *   <li>正しい組み合わせ … 200 で下流を 1 回だけ呼ぶ</li>
 * </ul>
 *
 * <p>下流は**替え玉**（{@link ClassroomNoteAdminClient} の spy）にして、実際の AI も
 * バッチも走らせない。DB はテスト用の独立した PostgreSQL（パスワードが無ければスキップ）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class ClassroomNotePermissionIT {

    private static final String PASSWORD = "Parent1234";
    private static final String COOKIE_NAME = "STUDY21_USER_SESSION";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;

    /** 下流（admin-api）の替え玉。**呼ばれたかどうか**をここで見る。 */
    @MockitoSpyBean
    private ClassroomNoteAdminClient noteAdminClient;

    /** 検証用の Redis（セッションの置き場。この検証の中だけで生きる）。 */
    private static final int REDIS_PORT = findFreePort();
    private static final java.util.Optional<redis.embedded.RedisServer> REDIS = startRedis();

    private static java.util.Optional<redis.embedded.RedisServer> startRedis() {
        try {
            redis.embedded.RedisServer server = redis.embedded.RedisServer.newRedisServer()
                    .port(REDIS_PORT).build();
            server.start();
            return java.util.Optional.of(server);
        } catch (Exception cause) {
            throw new IllegalStateException("検証用の Redis を起動できませんでした。", cause);
        }
    }

    @org.junit.jupiter.api.AfterAll
    static void stopRedis() {
        REDIS.ifPresent(server -> {
            try {
                server.stop();
            } catch (java.io.IOException ignored) {
                // 後片付けなので、止められなくても結果は変えない
            }
        });
    }

    private static int findFreePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (java.io.IOException cause) {
            throw new IllegalStateException("空きポートを取れませんでした", cause);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("study21.classroom.storage-root", () -> System.getProperty("java.io.tmpdir")
                + "/study21-it-note-permission");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> REDIS_PORT);
    }

    /** 検証用のアカウント（所有者・別の利用者）。 */
    private long ownerAccountId;
    private long otherAccountId;
    /** 所有者の記録と、その記録の最終まとめのノート。 */
    private long ownerRecordId;
    private long ownerFinalNoteId;
    /** 所有者の**別の**記録（noteId の取り違えを作る）。 */
    private long ownerOtherRecordId;
    /** 別の利用者の記録とノート。 */
    private long otherRecordId;
    private long otherFinalNoteId;
    /** 所有者の記録にある**途中の**ノート（PHASE）。 */
    private long ownerPhaseNoteId;

    @BeforeEach
    void setUp() {
        long stamp = System.nanoTime();
        long[] students = studentAccounts();
        ownerAccountId = students[0];
        otherAccountId = students[1];
        ownerRecordId = createRecord(ownerAccountId, "IT-OWNER-" + stamp);
        ownerFinalNoteId = createNote(ownerRecordId, "FINAL", "PENDING", ownerAccountId);
        ownerPhaseNoteId = createNote(ownerRecordId, "PHASE", "PENDING", ownerAccountId);
        ownerOtherRecordId = createRecord(ownerAccountId, "IT-OWNER-OTHER-" + stamp);
        otherRecordId = createRecord(otherAccountId, "IT-OTHER-" + stamp);
        otherFinalNoteId = createNote(otherRecordId, "FINAL", "PENDING", otherAccountId);

        // 下流の替え玉: **受理した**という応答を返す（実際の AI は走らせない）
        doReturn(objectMapper.createObjectNode()
                        .put("noteId", ownerFinalNoteId)
                        .put("accepted", true)
                        .put("status", "GENERATING")
                        .put("message", "最終まとめの作成を始めました。"))
                .when(noteAdminClient).accept(anyLong(), anyString());
        doReturn(objectMapper.createObjectNode()
                        .put("noteId", ownerFinalNoteId)
                        .put("status", "FAILED")
                        .put("liveness", "LOST")
                        .put("recoverable", true)
                        .put("recovered", true)
                        .put("message", "実行が失われていたため、やり直せる状態に戻しました。"))
                .when(noteAdminClient).recover(anyLong());
    }

    /* ---------------- 未ログイン ---------------- */

    @Test
    @DisplayName("① 未ログインの起動・回復は 401（**下流を呼ばない**）")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(post("/api/user/classroom/{id}/notes/run", ownerRecordId)
                        .param("noteId", String.valueOf(ownerFinalNoteId)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));
        mockMvc.perform(post("/api/user/classroom/{id}/notes/{noteId}/recover",
                        ownerRecordId, ownerFinalNoteId))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));

        verify(noteAdminClient, never()).accept(anyLong(), anyString());
        verify(noteAdminClient, never()).recover(anyLong());
    }

    /* ---------------- 他人の授業 ---------------- */

    @Test
    @DisplayName("② 他人の授業の起動・回復は 404（存在を漏らさない・**下流を呼ばない**）")
    void anotherUsersRecordIsRejected() throws Exception {
        String cookie = login(otherAccountId);

        mockMvc.perform(post("/api/user/classroom/{id}/notes/run", ownerRecordId)
                        .param("noteId", String.valueOf(ownerFinalNoteId)).cookie(cookieOf(cookie)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));
        mockMvc.perform(post("/api/user/classroom/{id}/notes/{noteId}/recover",
                        ownerRecordId, ownerFinalNoteId).cookie(cookieOf(cookie)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));

        verify(noteAdminClient, never()).accept(anyLong(), anyString());
        verify(noteAdminClient, never()).recover(anyLong());
    }

    /* ---------------- noteId の帰属 ---------------- */

    @Test
    @DisplayName("③ 自分の授業でも、**他の利用者のノート**を指定したら 404（下流を呼ばない）")
    void noteOfAnotherUserIsRejected() throws Exception {
        String cookie = login(ownerAccountId);

        mockMvc.perform(post("/api/user/classroom/{id}/notes/run", ownerRecordId)
                        .param("noteId", String.valueOf(otherFinalNoteId)).cookie(cookieOf(cookie)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));
        mockMvc.perform(post("/api/user/classroom/{id}/notes/{noteId}/recover",
                        ownerRecordId, otherFinalNoteId).cookie(cookieOf(cookie)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));

        verify(noteAdminClient, never()).accept(anyLong(), anyString());
        verify(noteAdminClient, never()).recover(anyLong());
    }

    @Test
    @DisplayName("④ 自分の授業でも、**自分の別の授業のノート**なら 404（下流を呼ばない）")
    void noteOfOwnAnotherRecordIsRejected() throws Exception {
        long otherNoteOfOwner = createNote(ownerOtherRecordId, "FINAL", "PENDING", ownerAccountId);
        String cookie = login(ownerAccountId);

        mockMvc.perform(post("/api/user/classroom/{id}/notes/run", ownerRecordId)
                        .param("noteId", String.valueOf(otherNoteOfOwner)).cookie(cookieOf(cookie)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));
        mockMvc.perform(post("/api/user/classroom/{id}/notes/{noteId}/recover",
                        ownerRecordId, otherNoteOfOwner).cookie(cookieOf(cookie)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));

        verify(noteAdminClient, never()).accept(anyLong(), anyString());
        verify(noteAdminClient, never()).recover(anyLong());
    }

    /* ---------------- 種別 ---------------- */

    @Test
    @DisplayName("⑤ 回復の入口に途中のノート（PHASE）を渡したら 400（最終まとめ専用・下流を呼ばない）")
    void phaseNoteCannotBeRecovered() throws Exception {
        String cookie = login(ownerAccountId);

        mockMvc.perform(post("/api/user/classroom/{id}/notes/{noteId}/recover",
                        ownerRecordId, ownerPhaseNoteId).cookie(cookieOf(cookie)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(400));
        verify(noteAdminClient, never()).recover(anyLong());
    }

    /* ---------------- 正しい組み合わせ ---------------- */

    @Test
    @DisplayName("⑥ 所有者が自分の最終まとめを起動・回復できる（下流を 1 回だけ呼ぶ）")
    void ownerCanRunAndRecover() throws Exception {
        String cookie = login(ownerAccountId);

        MvcResult run = mockMvc.perform(post("/api/user/classroom/{id}/notes/run", ownerRecordId)
                        .param("noteId", String.valueOf(ownerFinalNoteId)).cookie(cookieOf(cookie)))
                .andReturn();
        assertThat(run.getResponse().getStatus()).isEqualTo(200);
        JsonNode runData = objectMapper.readTree(run.getResponse().getContentAsString()).path("data");
        assertThat(runData.path("accepted").asBoolean()).isTrue();
        assertThat(runData.path("status").asText()).isEqualTo("GENERATING");

        MvcResult recover = mockMvc.perform(post("/api/user/classroom/{id}/notes/{noteId}/recover",
                        ownerRecordId, ownerFinalNoteId).cookie(cookieOf(cookie)))
                .andReturn();
        assertThat(recover.getResponse().getStatus()).isEqualTo(200);
        JsonNode recoverData = objectMapper.readTree(recover.getResponse().getContentAsString()).path("data");
        assertThat(recoverData.path("recovered").asBoolean()).isTrue();
        assertThat(recoverData.path("liveness").asText()).isEqualTo("LOST");

        // 下流は**それぞれ 1 回ずつ**（所有者のノートIDで）
        // **所有者のノートIDで**呼ばれた（引数は matcher で揃える）
        org.mockito.ArgumentCaptor<Long> accepted = org.mockito.ArgumentCaptor.forClass(Long.class);
        org.mockito.ArgumentCaptor<Long> recovered = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(noteAdminClient, org.mockito.Mockito.times(1)).accept(accepted.capture(), anyString());
        verify(noteAdminClient, org.mockito.Mockito.times(1)).recover(recovered.capture());
        assertThat(accepted.getValue()).isEqualTo(ownerFinalNoteId);
        assertThat(recovered.getValue()).isEqualTo(ownerFinalNoteId);
    }

    @Test
    @DisplayName("⑦ 下流が拒否・不調なら、成功として返さない")
    void downstreamFailureIsNotReportedAsSuccess() throws Exception {
        String cookie = login(ownerAccountId);
        // 下流が認証で拒否された（合言葉の不一致・設定漏れなど）
        org.mockito.Mockito.doThrow(
                        new ClassroomNoteAdminClient.ClassroomNoteCallException(
                                "まとめの操作が許可されていません（権限）。", false))
                .when(noteAdminClient).accept(anyLong(), anyString());

        MvcResult result = mockMvc.perform(post("/api/user/classroom/{id}/notes/run", ownerRecordId)
                        .param("noteId", String.valueOf(ownerFinalNoteId)).cookie(cookieOf(cookie)))
                .andReturn();

        // **成功（200 + accepted）にしない**。呼び出せなかったことを伝える
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        boolean success = body.path("success").asBoolean(false);
        assertThat(success).as("下流が失敗したのに成功を返してはいけない").isFalse();
        assertThat(body.path("data").path("accepted").asBoolean(false)).isFalse();
    }

    /* ---------------- 資材 ---------------- */

    /**
     * 検証用の生徒を作る（**採番はこちらで決める**）。
     *
     * <p>DDL の制約（`保護者 1 人につき生徒 1 人`・自己参照 FK）を満たすため、専用の保護者を
     * 1 人作ってから生徒を結び付ける。既存の 1・2 番のアカウントとは衝突しないよう、
     * 使われていない ID を採番してから入れる（採番済みの行を作らない）。</p>
     */
    /**
     * 検証用の生徒を 2 人返す（seed の生徒）。
     *
     * <p>アカウントの DDL は「生徒は保護者必須」「保護者 1 人につき生徒 1 人」なので、テストで
     * 作ると壊れやすい。下見用の DB（`tmp/tools/study21-testdb.sh`）が入れる生徒を使い、
     * パスワードだけ検証用に上書きする（**本番の DB には触らない**）。</p>
     */
    private long[] studentAccounts() {
        Long first = jdbc.queryForObject("""
                SELECT "アカウントID" FROM public."ACC_アカウント"
                 WHERE "アカウント種別" = 'STUDENT' ORDER BY "アカウントID" LIMIT 1
                """, Long.class);
        Long second = jdbc.queryForObject("""
                SELECT "アカウントID" FROM public."ACC_アカウント"
                 WHERE "アカウント種別" = 'STUDENT' AND "アカウントID" <> ?
                 ORDER BY "アカウントID" LIMIT 1
                """, Long.class, first);
        assertThat(first).as("検証用の生徒が要ります（tmp/tools/study21-testdb.sh で用意）").isNotNull();
        assertThat(second).as("検証用の生徒が 2 人要ります（tmp/tools/study21-testdb.sh で用意）").isNotNull();
        // ログイン用のパスワードを検証用に揃える（**一時的な下見用 DB の上でのみ**）
        for (Long id : new Long[] { first, second }) {
            jdbc.update("""
                    UPDATE public."ACC_アカウント" SET "パスワードハッシュ" = ?
                     WHERE "アカウントID" = ?
                    """, passwordEncoder.encode(PASSWORD), id);
        }
        return new long[] { first, second };
    }

    private long createRecord(long accountId, String recordNo) {
        Long id = jdbc.queryForObject("""
                INSERT INTO public."CR_授業記録情報"
                    ("授業記録番号", "登録者アカウントID", "学生ID", "状態", "バージョン", "登録元コード")
                VALUES (?, ?, ?, 'STOPPED', 1, 'APP')
                RETURNING "授業記録ID"
                """, Long.class, recordNo, accountId, accountId);
        return id == null ? 0L : id;
    }

    private long createNote(long recordId, String kind, String status, long accountId) {
        Long id = jdbc.queryForObject("""
                INSERT INTO public."CR_授業ノート情報"
                    ("授業記録ID", "種別", "フェーズ番号", "対象開始連番", "対象終了連番", "生成状態",
                     "再試行回数", "バージョン", "登録者アカウントID", "登録元コード")
                VALUES (?, ?, CASE WHEN ? = 'PHASE' THEN 1 ELSE NULL END, 1, 3, ?, 0, 1, ?, 'APP')
                RETURNING "授業ノートID"
                """, Long.class, recordId, kind, kind, status, accountId);
        return id == null ? 0L : id;
    }

    /** ログインしてセッションのクッキーを返す（**実際のログイン経路**を使う）。 */
    private String login(long accountId) throws Exception {
        String loginId = jdbc.queryForObject(
                "SELECT \"ログインID\" FROM public.\"ACC_アカウント\" WHERE \"アカウントID\" = ?",
                String.class, accountId);
        MvcResult result = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(loginId, PASSWORD)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).as("ログインできること").isEqualTo(200);
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).as("セッションのクッキーが返ること").isNotNull();
        String value = setCookie.split(";", 2)[0].substring(COOKIE_NAME.length() + 1);
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static jakarta.servlet.http.Cookie cookieOf(String sessionId) {
        return new jakarta.servlet.http.Cookie(COOKIE_NAME,
                Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8)));
    }
}
