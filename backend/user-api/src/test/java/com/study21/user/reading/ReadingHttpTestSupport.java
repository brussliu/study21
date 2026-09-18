package com.study21.user.reading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 読書管理 API を実際の HTTP（MockMvc）で検証するテストの共通土台。
 *
 * <p>実 DB（テストはロールバック）＋実セッション（検証用の埋め込み Redis）＋一時ディレクトリの
 * ストレージを使う。サブクラスはこのクラスを継承して `@Test` を足す。</p>
 *
 * <p>実行には DB のパスワードが要る（無いときはスキップする）:
 * {@code STUDY21_DATASOURCE_PASSWORD=... mvn -pl user-api -am test}</p>
 *
 * <p><b>注意:</b> JUnit の `@EnabledIfEnvironmentVariable` はサブクラスへ継承されないので、
 * 具象テストクラス側にも付ける（付け忘れると DB が無い環境でエラーになる）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class ReadingHttpTestSupport {

    /** 本文 PDF・表紙の保存先（テストの間だけ使う一時ディレクトリ） */
    protected static final Path STORAGE_ROOT = createTempDirectory();

    protected static final String COOKIE_NAME = "STUDY21_USER_SESSION";
    private static final String PASSWORD = "Parent1234";

    /**
     * 移行（`MIG_ACC_管理者_アカウント_20260914.sql`）で作る管理者アカウント。
     * アカウントID は BIGSERIAL の採番なので、固定せずログイン応答から受け取る。
     */
    protected static final String ADMIN_LOGIN_ID = "admin@study21.local";
    private static final String ADMIN_PASSWORD = "Admin1234!";
    /** {@link #loginAsAdmin()} が入れる管理者のアカウントID */
    private static Long adminAccountId;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** {@link #login()} が入れる、ログイン中の利用者 */
    private static UserPrincipal loggedIn;

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("study21-reading-test");
        } catch (IOException cause) {
            throw new IllegalStateException("一時ディレクトリを作成できませんでした。", cause);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", EmbeddedTestRedis::host);
        registry.add("spring.data.redis.port", EmbeddedTestRedis::port);
        registry.add("spring.data.redis.password", () -> "");
        // 実体ファイルは一時ディレクトリへ（リポジトリを汚さない）
        registry.add("study21.reading.storage-root", STORAGE_ROOT::toString);
        registry.add("study21.reading.legacy-storage-root", () -> "");
    }

    /**
     * 読むだけの人（生徒）。家庭は自分のアカウント（アカウントID 2）。
     * 2026-09-14 の決定で、生徒は本の登録・修正・削除ができない（403）。
     */
    protected static UserPrincipal student() {
        return new UserPrincipal(2L, "ricky.jingze@gmail.com", "試験 生徒", AccountType.STUDENT);
    }

    /**
     * 本を登録・修正する人（保護者）。家庭は「保護者—生徒の紐付け」から解決される
     * （アカウントID 1 の保護者 → アカウントID 2 の生徒）。
     */
    protected static UserPrincipal guardian() {
        return new UserPrincipal(1L, "bruss.ji.liu@gmail.com", "試験 保護者", AccountType.GUARDIAN);
    }

    /**
     * 管理者（全体書籍だけを扱う。決定 Q8 で user-api でも認証できる）。
     * **先に {@link #loginAsAdmin()} を呼ぶこと**（アカウントID はログイン応答から受け取る）。
     */
    protected static UserPrincipal admin() {
        if (adminAccountId == null) {
            throw new IllegalStateException("loginAsAdmin() を先に呼んでください。");
        }
        return new UserPrincipal(adminAccountId, ADMIN_LOGIN_ID, "システム 管理者", AccountType.ADMIN);
    }

    /** PDF のバイト列（%PDF- で始まる 200 バイトのダミー）。 */
    protected static byte[] pdfBytes() {
        StringBuilder builder = new StringBuilder("%PDF-1.4\n");
        while (builder.length() < 200) {
            builder.append("0123456789");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 保存している実体ファイルの数。 */
    protected static long countFiles() throws IOException {
        if (!Files.isDirectory(STORAGE_ROOT)) {
            return 0;
        }
        try (var walk = Files.walk(STORAGE_ROOT)) {
            return walk.filter(Files::isRegularFile).count();
        }
    }

    /**
     * 検証用のアカウント（保護者＋生徒）を作ってログインし、セッションのクッキーを返す。
     * ログインした保護者の principal は {@link #loggedIn()} で取れる
     * （サービス層を直接呼ぶときはこちらを使う。家庭＝登録した生徒）。
     */
    protected Cookie login() throws Exception {
        String email = "e2e-reading-" + System.nanoTime() + "@example.com";
        String body = """
                {"parentEmail":"%s","parentPassword":"%s",
                 "sei":"検証","mei":"保護者","seiKana":"けんしょう","meiKana":"ほごしゃ","grade":"中学1年生",
                 "studentEmail":"s-%s","studentPassword":"Student1234","agreed":true}
                """.formatted(email, PASSWORD, email);
        MvcResult registered = mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        assertThat(registered.getResponse().getStatus()).isEqualTo(200);
        JsonNode account = objectMapper.readTree(registered.getResponse().getContentAsString()).path("data");
        loggedIn = new UserPrincipal(account.path("guardianAccountId").asLong(), email, "検証 保護者",
                AccountType.GUARDIAN);
        loggedInFamilyStudentId = account.path("studentAccountId").asLong();

        return session(email, PASSWORD);
    }

    /**
     * 検証用のアカウント（保護者＋生徒）を作って、**生徒**でログインする。
     * 生徒の権限（読む＋自分の本棚だけ。決定 Q9）を HTTP で確かめるときに使う。
     */
    protected Cookie loginAsStudent() throws Exception {
        String email = "e2e-reading-" + System.nanoTime() + "@example.com";
        String body = """
                {"parentEmail":"%s","parentPassword":"%s",
                 "sei":"検証","mei":"保護者","seiKana":"けんしょう","meiKana":"ほごしゃ","grade":"中学1年生",
                 "studentEmail":"s-%s","studentPassword":"Student1234","agreed":true}
                """.formatted(email, PASSWORD, email);
        MvcResult registered = mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        assertThat(registered.getResponse().getStatus()).isEqualTo(200);
        JsonNode account = objectMapper.readTree(registered.getResponse().getContentAsString()).path("data");
        long studentId = account.path("studentAccountId").asLong();
        loggedIn = new UserPrincipal(studentId, "s-" + email, "検証 生徒", AccountType.STUDENT);
        loggedInFamilyStudentId = studentId;
        return session("s-" + email, "Student1234");
    }

    /** ログインしてセッションのクッキーを返す（Spring Session はクッキーを Base64 で入れる）。 */
    private Cookie session(String loginId, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(loginId, password)))
                .andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String setCookie = login.getResponse().getHeader("Set-Cookie");
        String value = setCookie.split(";", 2)[0].substring(COOKIE_NAME.length() + 1);
        assertThat(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)).matches("[0-9a-f-]{36}");
        return new Cookie(COOKIE_NAME, value);
    }

    /** 管理者（移行で作った `admin@study21.local`）でログインしてクッキーを返す。 */
    protected Cookie loginAsAdmin() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"%s\",\"password\":\"%s\"}"
                                .formatted(ADMIN_LOGIN_ID, ADMIN_PASSWORD)))
                .andReturn();
        assertThat(login.getResponse().getStatus())
                .as("管理者は user-api でもログインできる（決定 Q8）").isEqualTo(200);
        JsonNode account = objectMapper.readTree(login.getResponse().getContentAsString()).path("data");
        assertThat(account.path("accountType").asText()).isEqualTo("ADMIN");
        adminAccountId = account.path("accountId").asLong();
        loggedIn = admin();
        loggedInFamilyStudentId = null;
        String setCookie = login.getResponse().getHeader("Set-Cookie");
        String value = setCookie.split(";", 2)[0].substring(COOKIE_NAME.length() + 1);
        return new Cookie(COOKIE_NAME, value);
    }

    /** {@link #login()} でログインした保護者（サービス層を直接呼ぶときに使う）。 */
    protected UserPrincipal loggedIn() {
        return loggedIn;
    }

    /** ログインした保護者の家庭（生徒のアカウントID）。 */
    protected static Long loggedInFamilyStudentId;

    /** マルチパートの PDF（`file` パート）。 */
    protected static MockMultipartFile pdfPart(String fileName, byte[] body) {
        return new MockMultipartFile("file", fileName, "application/pdf", body);
    }

    /** 認証に使う URL エンコード（テスト内の日本語の照合用）。 */
    protected static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
