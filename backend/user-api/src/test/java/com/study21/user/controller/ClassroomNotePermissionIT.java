package com.study21.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.user.classroom.ClassroomNoteAdminClient;
import com.study21.user.testing.ClassroomTestData;
import com.study21.user.testing.TestDatabase;
import com.study21.user.testing.TestSqlMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * **まとめの起動・回復の権限**を、**実際の Spring Security のフィルタ経由**で確かめる。
 *
 * <p>見張るのは「他人の授業・他人のノートを操作できないこと」。画面は admin-api の内部入口を
 * 直接叩かず、この user-api の入口を通る。ここで**ログイン・授業の所有権・noteId の帰属**を
 * 確かめてから、はじめて下流（admin-api）を呼ぶ。</p>
 *
 * <p><b>データの隔離（2026-09-19 改修 第 9 段）</b>: 既存のアカウントを**一切使わない**。
 * 検証用の保護者→生徒・授業・まとめを毎回作り、控えた ID だけを後片付けする。日常使っている
 * アカウントのパスワードを書き換えることはしない。**専用のテスト DB**（
 * `STUDY21_TEST_DATASOURCE_URL`）が設定されていなければ、この検証は動かさない</p>
 *
 * <p>下流は**替え玉**（{@link ClassroomNoteAdminClient} の spy）。実際の AI もバッチも走らせない。
 * 起動時の自動バッチも止める。</p>
 */
@SpringBootTest(properties = {
    // 起動時に本当の業務（AI・バッチ）を走らせない
    "study21.batch.auto-run.startup-enabled=false",
    "study21.batch.auto-run.schedule-enabled=false",
    "study21.batch.auto-run.recovery-enabled=false"
})
@AutoConfigureMockMvc
/*
 * **トランザクションの中で組み立てる**（アカウントの「保護者には生徒が 1 人以上」は
 * **遅延トリガ**なので、保護者と生徒を同じトランザクションで入れる必要がある）。検証は同じ
 * スレッド（MockMvc）で完結し、下流は替え玉なので他スレッドの書き込みは無い。終わったら
 * **ロールバック**され、後片付けの取りこぼしも残らない。
 */
@org.springframework.transaction.annotation.Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_TEST_DATASOURCE_URL", matches = ".+",
        disabledReason = "専用のテスト DB（STUDY21_TEST_DATASOURCE_URL）が未設定のためスキップ")
class ClassroomNotePermissionIT {

    private static final String COOKIE_NAME = "STUDY21_USER_SESSION";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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

    /** **テスト専用の MyBatis Mapper**（検証データの作成・後片付け。SQL ログにも残る）。 */
    @Autowired
    private TestSqlMapper sql;
    @Autowired
    private PasswordEncoder passwordEncoder;

    /** 接続先を**専用のテスト DB に固定**する（`STUDY21_DATASOURCE_*` へは落とさない）。 */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.override(registry);
        // セッションの置き場も検証用（**共有の Redis を汚さない**）
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> REDIS_PORT);
    }

    /** 下流（admin-api）の替え玉。**呼ばれたかどうか**をここで見る。 */
    @MockitoSpyBean
    private ClassroomNoteAdminClient noteAdminClient;

    /** **専用のテスト DB 以外では動かさない**（設定漏れのまま配備先の DB へ書かない）。 */
    @BeforeAll
    static void requireDedicatedTestDatabase(@Value("${spring.datasource.url:}") String datasourceUrl) {
        // 環境変数が無ければクラスごとスキップ（`@EnabledIfEnvironmentVariable` と同じ判断）
        TestDatabase.skipUnlessConfigured();
        assertThat(datasourceUrl)
                .as("検証は専用のテスト DB でのみ動かします（STUDY21_TEST_DATASOURCE_URL）")
                .isNotBlank()
                .doesNotContain("192.168.0.100");
    }

    /* ---------------- 検証データ（毎回作って毎回消す） ---------------- */

    private ClassroomTestData data;
    private ClassroomTestData.Student owner;
    private ClassroomTestData.Student other;
    private ClassroomTestData.Lesson ownerLesson;
    private ClassroomTestData.Lesson ownerOtherLesson;
    private ClassroomTestData.Note ownerFinalNote;
    private ClassroomTestData.Note ownerPhaseNote;
    private ClassroomTestData.Note ownerOtherLessonNote;
    private ClassroomTestData.Lesson otherLesson;
    private ClassroomTestData.Note otherFinalNote;

    @BeforeEach
    void setUp() {
        data = new ClassroomTestData(sql, passwordEncoder);
        owner = data.createStudent("owner");
        other = data.createStudent("other");
        ownerLesson = data.createLesson(owner.accountId());
        ownerFinalNote = data.createNote(ownerLesson.recordId(), "FINAL", "PENDING");
        ownerPhaseNote = data.createNote(ownerLesson.recordId(), "PHASE", "PENDING");
        ownerOtherLesson = data.createLesson(owner.accountId());
        ownerOtherLessonNote = data.createNote(ownerOtherLesson.recordId(), "FINAL", "PENDING");
        otherLesson = data.createLesson(other.accountId());
        otherFinalNote = data.createNote(otherLesson.recordId(), "FINAL", "PENDING");

        // 下流の替え玉: **受理した**という応答を返す（実際の AI は走らせない）
        doReturn(objectMapper.createObjectNode()
                        .put("noteId", ownerFinalNote.noteId())
                        .put("accepted", true)
                        .put("status", "GENERATING")
                        .put("message", "最終まとめの作成を始めました。"))
                .when(noteAdminClient).accept(anyLong(), anyString());
        // 回復は admin-api の**本物の欄**（`reason`）で返す（user-api が message へ写す）
        doReturn(objectMapper.createObjectNode()
                        .put("noteId", ownerFinalNote.noteId())
                        .put("status", "FAILED")
                        .put("liveness", "LOST")
                        .put("recoverable", true)
                        .put("recovered", true)
                        .put("reason", "実行が失われていたため、やり直せる状態に戻しました。"))
                .when(noteAdminClient).recover(anyLong());
    }

    @AfterEach
    void cleanUp() {
        /*
         * 後片付けは**明示の DELETE ではなくロールバック**に任せる（この検証は 1 つの
         * トランザクションの中で組み立てているため）。控えた ID は失敗時の手掛かりとして残す。
         */
        assertThat(data.createdIds()).isNotEmpty();
    }

    /* ---------------- 未ログイン ---------------- */

    @Test
    @DisplayName("① 未ログインの起動・回復は 401（**下流を呼ばない**）")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(post("/api/user/classroom/{id}/notes/run", ownerLesson.recordId())
                        .param("noteId", String.valueOf(ownerFinalNote.noteId())))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));
        mockMvc.perform(post("/api/user/classroom/{id}/notes/{noteId}/recover",
                        ownerLesson.recordId(), ownerFinalNote.noteId()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));

        verify(noteAdminClient, never()).accept(anyLong(), anyString());
        verify(noteAdminClient, never()).recover(anyLong());
    }

    /* ---------------- 他人の授業 ---------------- */

    @Test
    @DisplayName("② 他人の授業の起動・回復は 404（存在を漏らさない・**下流を呼ばない**）")
    void anotherUsersRecordIsRejected() throws Exception {
        String session = login(other);

        mockMvc.perform(post("/api/user/classroom/{id}/notes/run", ownerLesson.recordId())
                        .param("noteId", String.valueOf(ownerFinalNote.noteId()))
                        .cookie(cookieOf(session)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));
        mockMvc.perform(post("/api/user/classroom/{id}/notes/{noteId}/recover",
                        ownerLesson.recordId(), ownerFinalNote.noteId()).cookie(cookieOf(session)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));

        verify(noteAdminClient, never()).accept(anyLong(), anyString());
        verify(noteAdminClient, never()).recover(anyLong());
    }

    /* ---------------- noteId の帰属 ---------------- */

    @Test
    @DisplayName("③ 自分の授業でも、**他の利用者のノート**を指定したら 404（下流を呼ばない）")
    void noteOfAnotherUserIsRejected() throws Exception {
        String session = login(owner);

        mockMvc.perform(post("/api/user/classroom/{id}/notes/run", ownerLesson.recordId())
                        .param("noteId", String.valueOf(otherFinalNote.noteId()))
                        .cookie(cookieOf(session)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));
        mockMvc.perform(post("/api/user/classroom/{id}/notes/{noteId}/recover",
                        ownerLesson.recordId(), otherFinalNote.noteId()).cookie(cookieOf(session)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));

        verify(noteAdminClient, never()).accept(anyLong(), anyString());
        verify(noteAdminClient, never()).recover(anyLong());
    }

    @Test
    @DisplayName("④ 自分の授業でも、**自分の別の授業のノート**なら 404（下流を呼ばない）")
    void noteOfOwnAnotherRecordIsRejected() throws Exception {
        String session = login(owner);

        mockMvc.perform(post("/api/user/classroom/{id}/notes/run", ownerLesson.recordId())
                        .param("noteId", String.valueOf(ownerOtherLessonNote.noteId()))
                        .cookie(cookieOf(session)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));
        mockMvc.perform(post("/api/user/classroom/{id}/notes/{noteId}/recover",
                        ownerLesson.recordId(), ownerOtherLessonNote.noteId())
                        .cookie(cookieOf(session)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));

        verify(noteAdminClient, never()).accept(anyLong(), anyString());
        verify(noteAdminClient, never()).recover(anyLong());
    }

    /* ---------------- 種別 ---------------- */

    @Test
    @DisplayName("⑤ 回復の入口に途中のノート（PHASE）を渡したら 400（最終まとめ専用・下流を呼ばない）")
    void phaseNoteCannotBeRecovered() throws Exception {
        String session = login(owner);

        mockMvc.perform(post("/api/user/classroom/{id}/notes/{noteId}/recover",
                        ownerLesson.recordId(), ownerPhaseNote.noteId()).cookie(cookieOf(session)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(400));
        verify(noteAdminClient, never()).recover(anyLong());
    }

    /* ---------------- 正しい組み合わせ ---------------- */

    @Test
    @DisplayName("⑥ 所有者が自分の最終まとめを起動・回復できる（下流を 1 回だけ呼ぶ）")
    void ownerCanRunAndRecover() throws Exception {
        String session = login(owner);

        MvcResult run = mockMvc.perform(post("/api/user/classroom/{id}/notes/run", ownerLesson.recordId())
                        .param("noteId", String.valueOf(ownerFinalNote.noteId()))
                        .cookie(cookieOf(session)))
                .andReturn();
        assertThat(run.getResponse().getStatus()).isEqualTo(200);
        JsonNode runData = objectMapper.readTree(run.getResponse().getContentAsString()).path("data");
        assertThat(runData.path("accepted").asBoolean()).isTrue();
        assertThat(runData.path("status").asText()).isEqualTo("GENERATING");

        MvcResult recover = mockMvc.perform(post("/api/user/classroom/{id}/notes/{noteId}/recover",
                        ownerLesson.recordId(), ownerFinalNote.noteId()).cookie(cookieOf(session)))
                .andReturn();
        assertThat(recover.getResponse().getStatus()).isEqualTo(200);
        JsonNode recoverData = objectMapper.readTree(recover.getResponse().getContentAsString()).path("data");
        assertThat(recoverData.path("recovered").asBoolean()).isTrue();
        assertThat(recoverData.path("liveness").asText()).isEqualTo("LOST");

        org.mockito.ArgumentCaptor<Long> accepted = org.mockito.ArgumentCaptor.forClass(Long.class);
        org.mockito.ArgumentCaptor<Long> recovered = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(noteAdminClient, times(1)).accept(accepted.capture(), anyString());
        verify(noteAdminClient, times(1)).recover(recovered.capture());
        assertThat(accepted.getValue()).isEqualTo(ownerFinalNote.noteId());
        assertThat(recovered.getValue()).isEqualTo(ownerFinalNote.noteId());
    }

    @Test
    @DisplayName("⑥ admin-api の reason が、user-api の message として画面へ届く（欄の写し間違いを防ぐ）")
    void recoveryReasonIsMappedToMessage() throws Exception {
        String session = login(owner);

        MvcResult result = mockMvc.perform(post("/api/user/classroom/{id}/notes/{noteId}/recover",
                        ownerLesson.recordId(), ownerFinalNote.noteId()).cookie(cookieOf(session)))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        // 画面が読む欄は **message**（admin-api の reason が写っている）
        assertThat(data.hasNonNull("message")).isTrue();
        assertThat(data.get("message").asText()).contains("やり直せる状態に戻しました");
        assertThat(data.has("reason")).as("user-api は reason を返さない（欄を統一）").isFalse();
    }

    @Test
    @DisplayName("⑦ 下流が拒否・不調なら、成功として返さない（記録も動かさない）")
    void downstreamFailureIsNotReportedAsSuccess() throws Exception {
        String session = login(owner);
        org.mockito.Mockito.doThrow(
                        new ClassroomNoteAdminClient.ClassroomNoteCallException(
                                "まとめの操作が許可されていません（権限）。", false))
                .when(noteAdminClient).accept(anyLong(), anyString());

        MvcResult result = mockMvc.perform(post("/api/user/classroom/{id}/notes/run", ownerLesson.recordId())
                        .param("noteId", String.valueOf(ownerFinalNote.noteId()))
                        .cookie(cookieOf(session)))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("success").asBoolean(false))
                .as("下流が失敗したのに成功を返してはいけない").isFalse();
        assertThat(body.path("data").path("accepted").asBoolean(false)).isFalse();
        // **記録も動かさない**（まとめは作ったままの PENDING）
        assertThat(statusOf(ownerFinalNote.noteId())).isEqualTo("PENDING");
    }

    /* ---------------- データの隔離（対照レコード） ---------------- */

    @Test
    @DisplayName("⑧ 検証の準備・後片付けで、**関係ないアカウントを触らない**（対照レコード）")
    void unrelatedAccountIsUntouched() throws Exception {
        // この検証が管理しない「対照」のアカウント（このテストだけが消す）
        ClassroomTestData control = new ClassroomTestData(sql, passwordEncoder);
        ClassroomTestData.Student witness = control.createStudent("control");
        String hashBefore = data.passwordHashOf(witness.accountId());
        try {
            String session = login(owner);
            mockMvc.perform(post("/api/user/classroom/{id}/notes/run", ownerLesson.recordId())
                            .param("noteId", String.valueOf(ownerFinalNote.noteId()))
                            .cookie(cookieOf(session)))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

            // 対照のアカウントは**残っていて、パスワードも変わっていない**
            assertThat(data.accountExists(witness.accountId())).isTrue();
            assertThat(data.passwordHashOf(witness.accountId())).isEqualTo(hashBefore);
        } finally {
            // 対照レコードも**ロールバック**で消える（明示の DELETE はしない）
            assertThat(control.createdIds()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("⑨ 2 回続けて回しても、一意キーが衝突しない（毎回新しいデータを作る）")
    void repeatedRunsDoNotConflict() throws Exception {
        for (int round = 0; round < 2; round += 1) {
            ClassroomTestData roundData = new ClassroomTestData(sql, passwordEncoder);
            try {
                ClassroomTestData.Student student = roundData.createStudent("repeat");
                ClassroomTestData.Lesson lesson = roundData.createLesson(student.accountId());
                ClassroomTestData.Note note = roundData.createNote(lesson.recordId(), "FINAL", "PENDING");
                String session = login(student);
                mockMvc.perform(post("/api/user/classroom/{id}/notes/run", lesson.recordId())
                                .param("noteId", String.valueOf(note.noteId()))
                                .cookie(cookieOf(session)))
                        .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
            } finally {
                assertThat(roundData.createdIds()).isNotEmpty();
            }
        }
    }

    /* ---------------- 資材 ---------------- */

    /** そのまとめのいまの生成状態（検証データのヘルパー経由＝MyBatis の SQL ログに残る）。 */
    private String statusOf(long noteId) {
        return data.noteStatus(noteId);
    }

    /** ログインしてセッションの ID を返す（**実際のログイン経路**を使う）。 */
    private String login(ClassroomTestData.Student student) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"%s\",\"password\":\"%s\"}"
                                .formatted(student.loginId(), student.password())))
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
