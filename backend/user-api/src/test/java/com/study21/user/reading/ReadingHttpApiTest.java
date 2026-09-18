package com.study21.user.reading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 読書管理 API を実際の HTTP（MockMvc）で往復させる検証。
 *
 * <p>本文 PDF のアップロードと配信（pdf.js は `Range` で分割取得するので
 * **実体のバイト列が返ること**まで見る）と、読書履歴の絞り込み
 * （`bookId` / `dateFrom` / `dateTo` / `pageNo`）。ストレージは一時ディレクトリ、
 * DB は実 DB（テストはロールバックする）を使う。</p>
 *
 * <p>実行には DB のパスワードが要る（無いときはスキップする）:
 * {@code STUDY21_DATASOURCE_PASSWORD=... mvn -pl user-api -am test}</p>
 */
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class ReadingHttpApiTest extends ReadingHttpTestSupport {

    @Autowired
    private ReadingService readingService;

    @Test
    void uploadsPdfThenStreamsItWithRangeSupport() throws Exception {
        Cookie cookie = login();
        long bookId = readingService.createBook(loggedIn(), new ReadingModels.BookSaveRequest(
                "HTTP 経由の PDF テスト", "Test Author", "Starter", "未着手", 50, null, false, List.of(),
                null, null, null, null, null)).book().bookId();

        byte[] pdf = pdfBytes();
        MockMultipartFile file = pdfPart("HTTP テスト.pdf", pdf);

        // アップロード（totalPages つき）
        mockMvc.perform(multipart("/api/user/reading/books/{bookId}/pdf", bookId)
                        .file(file).param("totalPages", "12").cookie(cookie))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        ReadingModels.BookRow book = readingService.detail(loggedIn(), bookId, null, null).book();
        assertThat(book.hasPdf()).isTrue();
        assertThat(book.pdfAvailable()).as("実体もストレージにある").isTrue();
        assertThat(book.pdfOriginalName()).isEqualTo("HTTP テスト.pdf");
        assertThat(book.totalPages()).as("画面が数えた総ページ数を反映する").isEqualTo(12);

        // 本棚のサマリ（書籍管理画面の「PDF 登録済み N 冊」）にも反映される
        assertThat(readingService.searchBooks(loggedIn(), null, null, null, null, null, null, null, null, null, 1, 100).totals().pdfCount())
                .as("実体がある 1 冊").isEqualTo(1);
        assertThat(readingService.searchBooks(loggedIn(), null, null, null, null, null, null, null, null, null, 1, 100).totals()
                .uncategorizedCount())
                .as("分類を付けずに登録したので 1 冊").isEqualTo(1);

        // 全体（pdf.js の初回ロード）。実体のバイト列がそのまま返る
        MvcResult full = mockMvc.perform(get("/api/user/reading/books/{bookId}/pdf", bookId).cookie(cookie))
                .andReturn();
        assertThat(full.getResponse().getStatus()).isEqualTo(200);
        assertThat(full.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(full.getResponse().getHeader(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(full.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline");
        assertThat(full.getResponse().getContentAsByteArray()).isEqualTo(pdf);

        // Range（206 + Content-Range + 部分バイト）
        MvcResult partial = mockMvc.perform(get("/api/user/reading/books/{bookId}/pdf", bookId)
                        .header(HttpHeaders.RANGE, "bytes=0-9").cookie(cookie))
                .andReturn();
        assertThat(partial.getResponse().getStatus()).isEqualTo(206);
        assertThat(partial.getResponse().getHeader(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes 0-9/" + pdf.length);
        assertThat(partial.getResponse().getContentAsByteArray())
                .isEqualTo(java.util.Arrays.copyOfRange(pdf, 0, 10));

        // suffix 形式（最後の 5 バイト）
        MvcResult suffix = mockMvc.perform(get("/api/user/reading/books/{bookId}/pdf", bookId)
                        .header(HttpHeaders.RANGE, "bytes=-5").cookie(cookie))
                .andReturn();
        assertThat(suffix.getResponse().getStatus()).isEqualTo(206);
        assertThat(suffix.getResponse().getHeader(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes " + (pdf.length - 5) + "-" + (pdf.length - 1) + "/" + pdf.length);

        // 範囲外は 416
        mockMvc.perform(get("/api/user/reading/books/{bookId}/pdf", bookId)
                        .header(HttpHeaders.RANGE, "bytes=" + (pdf.length + 100) + "-").cookie(cookie))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(416));

        // download=true は添付（日本語のファイル名が RFC 5987 で入る）。バイト列は同じ
        MvcResult downloaded = mockMvc.perform(get("/api/user/reading/books/{bookId}/pdf", bookId)
                        .param("download", "true").cookie(cookie))
                .andReturn();
        assertThat(downloaded.getResponse().getStatus()).isEqualTo(200);
        assertThat(downloaded.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment")
                .contains("filename*=UTF-8''")
                .contains("filename=");
        // 日本語ファイル名が percent-encode されて入っている（元ファイル名をそのまま使う）
        assertThat(downloaded.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains(urlEncode("HTTP テスト.pdf"));
        assertThat(downloaded.getResponse().getContentAsByteArray()).isEqualTo(pdf);
        // download=false を明示しても inline のまま
        MvcResult inlineAgain = mockMvc.perform(get("/api/user/reading/books/{bookId}/pdf", bookId)
                        .param("download", "false").cookie(cookie))
                .andReturn();
        assertThat(inlineAgain.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline");

        // 差し替えると新しい実体が返る（古い実体は残さない）
        byte[] replaced = "%PDF-1.7 replaced".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(multipart("/api/user/reading/books/{bookId}/pdf", bookId)
                        .file(pdfPart("new.pdf", replaced))
                        .cookie(cookie))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
        MvcResult afterReplace = mockMvc.perform(
                        get("/api/user/reading/books/{bookId}/pdf", bookId).cookie(cookie))
                .andReturn();
        assertThat(afterReplace.getResponse().getContentAsByteArray()).isEqualTo(replaced);
        assertThat(countFiles()).as("差し替えで古い実体を残さない").isEqualTo(1);

        // 削除すると 404（画面は「PDF 未登録」を出す）
        mockMvc.perform(delete("/api/user/reading/books/{bookId}/pdf", bookId).cookie(cookie))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
        mockMvc.perform(get("/api/user/reading/books/{bookId}/pdf", bookId).cookie(cookie))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));
        assertThat(countFiles()).as("実体も消えている").isZero();
    }

    /** 移行した 6 冊はメタ情報だけなので、実体が無く 404（画面は「PDF 未登録」）。 */
    @Test
    void migratedBookWithoutFileReturnsNotFound() throws Exception {
        Cookie cookie = login();
        ReadingModels.BookRow migrated = readingService.searchBooks(loggedIn(), "Harry Potter", null, null, null, null, null, null, null, null, 1, 5).items().stream().findFirst().orElseThrow();
        assertThat(migrated.hasPdf()).isTrue();
        assertThat(migrated.pdfAvailable()).isFalse();

        mockMvc.perform(get("/api/user/reading/books/{bookId}/pdf", migrated.bookId()).cookie(cookie))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));
        mockMvc.perform(get("/api/user/reading/books/{bookId}/cover", migrated.bookId()).cookie(cookie))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));
    }

    /**
     * 読書履歴の絞り込みが HTTP のクエリで効くこと。
     *
     * <p>書籍ID=3（移行データ 265 件・2026-04-08〜2026-08-05）を使う。日付は
     * `LocalDate` として受け、`dateTo` はその日いっぱいを含む。不正な値は 400。</p>
     */
    @Test
    void recordHistoryFiltersOverHttp() throws Exception {
        Cookie cookie = login();
        ObjectMapper json = new ObjectMapper();

        // 絞り込みなし（既存の呼び方）: 全 265 件
        JsonNode all = fetchRecords(cookie, json, null, null, null);
        assertThat(all.path("totalElements").asLong()).isEqualTo(265);

        // 最初の日（2026-04-08）だけ → その日の記録のみ・totalElements も一致
        JsonNode firstDay = fetchRecords(cookie, json, "2026-04-08", "2026-04-08", null);
        assertThat(firstDay.path("totalElements").asLong()).isPositive();
        assertThat(firstDay.path("totalElements").asLong()).isLessThan(265);
        for (JsonNode item : firstDay.path("items")) {
            assertThat(item.path("readAt").asText()).startsWith("2026-04-08");
        }

        // 範囲外は 0 件
        assertThat(fetchRecords(cookie, json, "2026-01-01", "2026-01-31", null).path("totalElements").asLong())
                .isZero();

        // ページ番号（移行データは 7 ページからなので 1 ページは 0 件）
        assertThat(fetchRecords(cookie, json, null, null, 1).path("totalElements").asLong()).isZero();

        // 形式が不正な日付・1 未満のページ番号は 400（JSON のエラー本文）
        mockMvc.perform(get("/api/user/reading/records").param("bookId", "3")
                        .param("dateFrom", "2026-13-40").cookie(cookie))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/user/reading/records").param("bookId", "3")
                        .param("dateTo", "abc").cookie(cookie))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/user/reading/records").param("bookId", "3")
                        .param("pageNo", "0").cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    /** 読書履歴を 1 ページだけ取って `data` を返す。 */
    private JsonNode fetchRecords(Cookie cookie, ObjectMapper json, String dateFrom, String dateTo, Integer pageNo)
            throws Exception {
        var request = get("/api/user/reading/records").param("bookId", "3")
                .param("page", "1").param("size", "100").cookie(cookie);
        if (dateFrom != null) {
            request = request.param("dateFrom", dateFrom);
        }
        if (dateTo != null) {
            request = request.param("dateTo", dateTo);
        }
        if (pageNo != null) {
            request = request.param("pageNo", String.valueOf(pageNo));
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    /** 未認証では 401（画面の PDF URL もログインが要る）。 */
    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/user/reading/books/{bookId}/pdf", 1L))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));
    }

    // ------------------------------------------- 公開範囲とロール（2026-09-14）

    /**
     * **管理者は user-api でもログインできる**（決定 Q8）。全体書籍だけが見えて、
     * 全体書籍を登録できる（画面 `/admin/reading-books` が動く前提）。
     */
    @Test
    void adminSessionSeesAndCreatesGlobalBooksOnly() throws Exception {
        Cookie cookie = loginAsAdmin();

        // 一覧は 200（管理者にも見える本がある）
        MvcResult list = mockMvc.perform(get("/api/user/reading/books").cookie(cookie))
                .andExpect(status().isOk()).andReturn();
        JsonNode page = objectMapper.readTree(list.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        // 管理者に見えるのは全体書籍だけ。冊数はデータ（全体書籍の登録数）で変わるので固定値では見ない
        assertThat(page.path("items").size()).isGreaterThan(0);
        page.path("items").forEach(item -> assertThat(item.path("scope").asText()).isEqualTo("GLOBAL"));

        // 登録は全体書籍になる
        MvcResult created = mockMvc.perform(post("/api/user/reading/books").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"HTTP 管理者の本","author":"Test Author","difficulty":"Starter",
                                 "status":"未着手","totalPages":10,"scope":"GLOBAL"}
                                """))
                .andExpect(status().isOk()).andReturn();
        JsonNode book = objectMapper.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("book");
        assertThat(book.path("scope").asText()).isEqualTo("GLOBAL");
        assertThat(book.path("ownerFamilyId").isNull()).isTrue();
        assertThat(book.path("inMyShelf").asBoolean()).isFalse();

        // 家庭の本は作れない（403）
        mockMvc.perform(post("/api/user/reading/books").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"家庭の本","author":"Test Author","difficulty":"Starter",
                                 "status":"未着手","totalPages":10,"scope":"FAMILY"}
                                """))
                .andExpect(status().isForbidden());
    }

    /** 生徒は登録できない（403）。読むことと【自分の本棚】だけ（決定 Q9）。 */
    @Test
    void studentCannotCreateBooks() throws Exception {
        Cookie cookie = loginAsStudent();

        mockMvc.perform(post("/api/user/reading/books").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"生徒の本","author":"Test Author","difficulty":"Starter",
                                 "status":"未着手","totalPages":10}
                                """))
                .andExpect(status().isForbidden());

        // 一覧は見られる（GLOBAL ＋ 自家庭）
        mockMvc.perform(get("/api/user/reading/books").cookie(cookie)).andExpect(status().isOk());
    }

    /**
     * 【本棚に入れる/外す】。**冪等**で、`shelf=MINE` にだけ出る。
     * （触るのは自分の行だけなので、呼び出し元のアカウントが変わるだけで壊れない）
     */
    @Test
    void shelfAddAndRemoveAreIdempotentOverHttp() throws Exception {
        Cookie cookie = loginAsStudent();
        long bookId = readingService.searchBooks(loggedIn(), null, null, null, null, null, null, null, null,
                null, 1, 1).items().get(0).bookId();

        // 最初は空（移行で初期投入しない。決定 D3）
        assertThat(mine(cookie).path("items").size()).isZero();

        for (int i = 0; i < 2; i++) {
            MvcResult added = mockMvc.perform(put("/api/user/reading/books/{bookId}/shelf", bookId).cookie(cookie))
                    .andExpect(status().isOk()).andReturn();
            JsonNode book = objectMapper.readTree(added.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .path("data").path("book");
            assertThat(book.path("inMyShelf").asBoolean()).as("何度押しても入っている").isTrue();
        }
        assertThat(mine(cookie).path("items").size()).isEqualTo(1);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete("/api/user/reading/books/{bookId}/shelf", bookId).cookie(cookie))
                    .andExpect(status().isOk());
        }
        assertThat(mine(cookie).path("items").size()).isZero();

        // 図書館（shelf 指定なし）には残る
        MvcResult library = mockMvc.perform(get("/api/user/reading/books").cookie(cookie))
                .andExpect(status().isOk()).andReturn();
        JsonNode items = objectMapper.readTree(library.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("items");
        assertThat(items.findValues("bookId")).anyMatch(node -> node.asLong() == bookId);
    }

    /** `?scope=FAMILY` は自分の家庭の本だけ（管理者には 0 件）。 */
    @Test
    void scopeFilterNarrowsToFamily() throws Exception {
        Cookie cookie = login();  // 保護者
        long bookId = readingService.createBook(loggedIn(), new ReadingModels.BookSaveRequest(
                "HTTP 家庭の本", "Test Author", "Starter", "未着手", 10, null, false, List.of(),
                null, null, null, null, null)).book().bookId();

        MvcResult family = mockMvc.perform(get("/api/user/reading/books").param("scope", "FAMILY").cookie(cookie))
                .andExpect(status().isOk()).andReturn();
        JsonNode page = objectMapper.readTree(family.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("items").get(0).path("bookId").asLong()).isEqualTo(bookId);

        // GLOBAL は移行した 6 冊（家庭の本は含まない）
        MvcResult global = mockMvc.perform(get("/api/user/reading/books").param("scope", "GLOBAL").cookie(cookie))
                .andExpect(status().isOk()).andReturn();
        JsonNode globalPage = objectMapper.readTree(global.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertThat(globalPage.path("items").findValues("bookId"))
                .noneMatch(node -> node.asLong() == bookId);
    }

    /** 見えない本（他家庭の本）は 404。PDF の配信でも漏らさない。 */
    @Test
    void invisibleBookIsNotFound() throws Exception {
        Cookie owner = login();
        long bookId = readingService.createBook(loggedIn(), new ReadingModels.BookSaveRequest(
                "HTTP 他家庭から見えない本", "Test Author", "Starter", "未着手", 10, null, false, List.of(),
                null, null, null, null, null)).book().bookId();

        Cookie other = loginAsStudent();   // 別の家庭の生徒
        mockMvc.perform(get("/api/user/reading/books/{bookId}", bookId).cookie(other))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/user/reading/books/{bookId}/pdf", bookId).cookie(other))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/user/reading/books/{bookId}/shelf", bookId).cookie(other))
                .andExpect(status().isNotFound());

        // 本人（登録した保護者）には見える
        mockMvc.perform(get("/api/user/reading/books/{bookId}", bookId).cookie(owner))
                .andExpect(status().isOk());
    }

    /** その人の【自分の本棚】の 1 ページ。 */
    private JsonNode mine(Cookie cookie) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/user/reading/books").param("shelf", "MINE")
                        .param("size", "100").cookie(cookie))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    // -------------------------------------------------------------------- 部品

}
