package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.reading.ReadingDictionaryService;
import com.study21.user.reading.ReadingModels;
import com.study21.user.reading.ReadingRange;
import com.study21.user.reading.ReadingService;
import com.study21.user.security.UserPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 読書管理 API（user-api）。親メニュー【読書管理】の 2 画面（書籍管理・書籍閲覧）が使う。
 *
 * <ul>
 *   <li>`GET /books` … 本の一覧（絞り込み・ページング・サマリ。`categoryId=0` は未分類のみ）</li>
 *   <li>`GET /books?scope=` … 公開範囲の絞り込み（`ALL`＝全体書籍＋自家庭（既定）/ `GLOBAL` / `FAMILY`）</li>
 *   <li>`GET /books?shelf=MINE` … 【自分の本棚】だけ（アカウントごとの本棚）</li>
 *   <li>`PUT` / `DELETE /books/{bookId}/shelf` … 【本棚に入れる】/【本棚から外す】（冪等・自分の行だけ）</li>
 *   <li>`GET /books?language=` … 言語（中国語 / 英語 / 日本語）での絞り込み</li>
 *   <li>`GET /books/{bookId}` … 1 冊（閲覧画面: 記録・標記・標記のあるページ）</li>
 *   <li>`POST /books` / `PUT /books/{bookId}` … 登録・修正（楽観的ロック・分類）</li>
 *   <li>`PATCH /books/{bookId}/pin` / `/status` … 置頂・読書ステータス</li>
 *   <li>`DELETE /books/{bookId}` … 削除（記録・標記・実体ファイルも一緒に消える）</li>
 *   <li>`GET /books/{bookId}/pdf` / `/cover` … 本文 PDF・表紙の配信（inline・Range 対応）</li>
 *   <li>`POST` / `DELETE /books/{bookId}/pdf` / `/cover` … アップロード・削除</li>
 *   <li>`/categories` … 本棚の分類マスタ（一覧・登録・修正・削除）</li>
 *   <li>`POST /lookup` … 選んだ語の意味・読みの引き当て（語彙・読み方）</li>
 *   <li>`GET /records` / `POST /books/{bookId}/records` / `DELETE /records/{recordId}` … 読書履歴
 *       （`bookId` / `dateFrom` / `dateTo` / `pageNo` で絞り込み）</li>
 *   <li>`GET /books/{bookId}/marks` / `POST .../marks` / `DELETE /marks/{markId}` … 標記</li>
 *   <li>`DELETE /books/{bookId}/marks` … 標記・読書記録の全削除＋読書の進捗のリセット（画面の【標記クリア】）</li>
 * </ul>
 *
 * 2.0（english_reading.jsp / english_reading_reader.jsp）の操作をひととおり揃えてある。
 */
@RestController
@RequestMapping("/api/user/reading")
public class ReadingController {

    private final ReadingService readingService;
    private final ReadingDictionaryService dictionaryService;

    public ReadingController(ReadingService readingService, ReadingDictionaryService dictionaryService) {
        this.readingService = readingService;
        this.dictionaryService = dictionaryService;
    }

    /* ---------- 本棚 ---------- */

    @GetMapping("/books")
    public ApiResponse<ReadingModels.BookListResult> books(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "pinned", required = false) Boolean pinned,
            /** 分類の棚。0＝未分類のみ、省略＝すべて */
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            /** 本の言語（中国語 / 英語 / 日本語）。省略＝すべて */
            @RequestParam(value = "language", required = false) String language,
            /** 公開範囲。ALL＝全体書籍＋自分の家庭（省略時）/ GLOBAL＝全体書籍のみ / FAMILY＝自分の家庭のみ */
            @RequestParam(value = "scope", required = false) String scope,
            /** MINE＝自分の【自分の本棚】だけ（省略＝すべて） */
            @RequestParam(value = "shelf", required = false) String shelf,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(readingService.searchBooks(user, keyword, difficulty, status, tag, pinned, categoryId,
                language, scope, shelf, page, size));
    }

    @GetMapping("/books/{bookId}")
    public ApiResponse<ReadingModels.BookDetail> book(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookId,
            @RequestParam(value = "markPage", required = false) Integer markPage,
            @RequestParam(value = "markType", required = false) String markType) {
        return ApiResponse.ok(readingService.detail(user, bookId, markPage, markType));
    }

    @PostMapping("/books")
    public ApiResponse<ReadingModels.BookMutationResult> create(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody ReadingModels.BookSaveRequest request) {
        ReadingModels.BookMutationResult result = readingService.createBook(user, request);
        return ApiResponse.ok(result, result.message());
    }

    @PutMapping("/books/{bookId}")
    public ApiResponse<ReadingModels.BookMutationResult> update(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookId,
            @Valid @RequestBody ReadingModels.BookSaveRequest request) {
        ReadingModels.BookMutationResult result = readingService.updateBook(user, bookId, request);
        return ApiResponse.ok(result, result.message());
    }

    @PatchMapping("/books/{bookId}/pin")
    public ApiResponse<ReadingModels.BookMutationResult> pin(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookId,
            @RequestBody ReadingModels.PinRequest request) {
        ReadingModels.BookMutationResult result = readingService.setPinned(user, bookId, request.pinned(),
                request.version());
        return ApiResponse.ok(result, result.message());
    }

    @PatchMapping("/books/{bookId}/status")
    public ApiResponse<ReadingModels.BookMutationResult> status(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookId,
            @Valid @RequestBody ReadingModels.StatusRequest request) {
        ReadingModels.BookMutationResult result = readingService.setStatus(user, bookId, request.status(),
                request.version());
        return ApiResponse.ok(result, result.message());
    }

    @DeleteMapping("/books/{bookId}")
    public ApiResponse<ReadingModels.SimpleResult> delete(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookId) {
        ReadingModels.SimpleResult result = readingService.deleteBook(user, bookId);
        return ApiResponse.ok(result, result.message());
    }

    /* ---------- 自分の本棚（アカウントごと） ---------- */

    /**
     * 【本棚に入れる】。**冪等**（すでに入っていれば 200 のまま件数は増えない）。
     * 触るのは呼び出した本人の行だけ（他人のアカウントID は受け取らない）。
     */
    @PutMapping("/books/{bookId}/shelf")
    public ApiResponse<ReadingModels.BookMutationResult> addToShelf(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookId) {
        ReadingModels.BookMutationResult result = readingService.addToShelf(user, bookId);
        return ApiResponse.ok(result, result.message());
    }

    /** 【本棚から外す】。**冪等**（入っていなければ何もしない）。 */
    @DeleteMapping("/books/{bookId}/shelf")
    public ApiResponse<ReadingModels.BookMutationResult> removeFromShelf(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookId) {
        ReadingModels.BookMutationResult result = readingService.removeFromShelf(user, bookId);
        return ApiResponse.ok(result, result.message());
    }

    /* ---------- 読書履歴 ---------- */

    /**
     * 読書履歴（書籍管理画面・閲覧画面の「読書履歴」）。
     *
     * @param dateFrom yyyy-MM-dd（その日以降）
     * @param dateTo   yyyy-MM-dd（その日いっぱいを含む）
     * @param pageNo   そのページを読んだ記録だけ（1 以上）
     */
    @GetMapping("/records")
    public ApiResponse<ReadingModels.RecordListResult> records(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(value = "bookId", required = false) Long bookId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(readingService.listRecords(user, bookId, dateFrom, dateTo, pageNo, page, size));
    }

    @PostMapping("/books/{bookId}/records")
    public ApiResponse<ReadingModels.RecordMutationResult> saveRecord(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookId,
            @Valid @RequestBody ReadingModels.RecordSaveRequest request) {
        ReadingModels.RecordMutationResult result = readingService.createRecord(user, bookId, request);
        return ApiResponse.ok(result, result.message());
    }

    @DeleteMapping("/records/{recordId}")
    public ApiResponse<ReadingModels.SimpleResult> deleteRecord(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long recordId) {
        ReadingModels.SimpleResult result = readingService.deleteRecord(user, recordId);
        return ApiResponse.ok(result, result.message());
    }

    /* ---------- 標記 ---------- */

    @GetMapping("/books/{bookId}/marks")
    public ApiResponse<ReadingModels.MarkListResult> marks(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookId,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "markType", required = false) String markType,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        return ApiResponse.ok(readingService.listMarks(user, bookId, pageNo, markType, page, size));
    }

    @PostMapping("/books/{bookId}/marks")
    public ApiResponse<ReadingModels.MarkMutationResult> addMark(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookId,
            @Valid @RequestBody ReadingModels.MarkSaveRequest request) {
        ReadingModels.MarkMutationResult result = readingService.createMark(user, bookId, request);
        return ApiResponse.ok(result, result.message());
    }

    @DeleteMapping("/marks/{markId}")
    public ApiResponse<ReadingModels.BookMutationResult> deleteMark(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long markId) {
        ReadingModels.BookMutationResult result = readingService.deleteMark(user, markId);
        return ApiResponse.ok(result, result.message());
    }

    /**
     * その書籍の標記と読書記録をすべて削除し、あわせて読書の進捗もリセットする
     * （画面の【標記クリア】。パスと返す形は 2.0 の「標記削除」から変えない）。
     */
    @DeleteMapping("/books/{bookId}/marks")
    public ApiResponse<ReadingModels.BookMutationResult> resetMarks(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookId) {
        ReadingModels.BookMutationResult result = readingService.resetMarksAndProgress(user, bookId);
        return ApiResponse.ok(result, result.message());
    }

    /* ---------- 分類（本棚の棚） ---------- */

    @GetMapping("/categories")
    public ApiResponse<ReadingModels.CategoryListResult> categories(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(readingService.listCategories(user));
    }

    @PostMapping("/categories")
    public ApiResponse<ReadingModels.CategoryMutationResult> createCategory(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody ReadingModels.CategorySaveRequest request) {
        ReadingModels.CategoryMutationResult result = readingService.createCategory(user, request);
        return ApiResponse.ok(result, result.message());
    }

    @PutMapping("/categories/{categoryId}")
    public ApiResponse<ReadingModels.CategoryMutationResult> updateCategory(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long categoryId,
            @Valid @RequestBody ReadingModels.CategorySaveRequest request) {
        ReadingModels.CategoryMutationResult result = readingService.updateCategory(user, categoryId, request);
        return ApiResponse.ok(result, result.message());
    }

    /** 分類を削除する。**本は消さない**（未分類に戻る）。 */
    @DeleteMapping("/categories/{categoryId}")
    public ApiResponse<ReadingModels.SimpleResult> deleteCategory(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long categoryId) {
        ReadingModels.SimpleResult result = readingService.deleteCategory(user, categoryId);
        return ApiResponse.ok(result, result.message());
    }

    /* ---------- 本文 PDF・表紙画像 ---------- */

    @PostMapping(value = "/books/{bookId}/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ReadingModels.BookMutationResult> uploadPdf(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookId,
            @RequestPart("file") MultipartFile file,
            /** 画面（pdf.js）が数えた総ページ数。あれば総ページ数を更新する */
            @RequestParam(value = "totalPages", required = false) Integer totalPages) {
        ReadingModels.BookMutationResult result = readingService.uploadPdf(user, bookId, file, totalPages);
        return ApiResponse.ok(result, result.message());
    }

    @DeleteMapping("/books/{bookId}/pdf")
    public ApiResponse<ReadingModels.SimpleResult> deletePdf(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookId) {
        ReadingModels.SimpleResult result = readingService.deletePdf(user, bookId);
        return ApiResponse.ok(result, result.message());
    }

    /**
     * 本文 PDF を配信する（pdf.js が読む）。
     *
     * <p>`Content-Disposition` は既定で `inline`（画面で表示）。`download=true` のときは
     * `attachment`（ブラウザが保存する）にし、元ファイル名を RFC 5987 の
     * `filename*=UTF-8''…` ＋ ASCII フォールバックで返す（日本語のファイル名のため）。</p>
     *
     * <p>`Range` があれば 206（`Content-Range` つき）、不正・範囲外は 416。
     * 行が無い・実体が無い・2.0 のツリー未設定は 404（画面は「PDF 未登録」を出す）。</p>
     */
    @GetMapping("/books/{bookId}/pdf")
    public void pdf(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            /** true のときだけ添付（ダウンロード）として返す */
            @RequestParam(value = "download", defaultValue = "false") boolean download,
            HttpServletResponse response) {
        writeFile(readingService.downloadPdf(user, bookId), range, download, response);
    }

    @PostMapping(value = "/books/{bookId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ReadingModels.BookMutationResult> uploadCover(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookId,
            @RequestPart("file") MultipartFile file) {
        ReadingModels.BookMutationResult result = readingService.uploadCover(user, bookId, file);
        return ApiResponse.ok(result, result.message());
    }

    @DeleteMapping("/books/{bookId}/cover")
    public ApiResponse<ReadingModels.SimpleResult> deleteCover(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookId) {
        ReadingModels.SimpleResult result = readingService.deleteCover(user, bookId);
        return ApiResponse.ok(result, result.message());
    }

    /** 表紙画像を配信する（無ければ 404。画面は表紙なしの表示に切り替える）。 */
    @GetMapping("/books/{bookId}/cover")
    public void cover(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            HttpServletResponse response) {
        writeFile(readingService.downloadCover(user, bookId), range, false, response);
    }

    /* ---------- 語彙・読みの引き当て ---------- */

    /**
     * 選んだ語の意味・読みを引く（英語の本＝語彙、中国語の本＝読み方）。
     *
     * <p>外部の辞書・翻訳 API を使うが、結果は `RED_語彙辞書情報` に貯めて次回から
     * 外部へ行かない。取れなくても 200 で null を返す（画面は「取得できませんでした」を出す）。</p>
     */
    @PostMapping("/lookup")
    public ApiResponse<ReadingModels.LookupResult> lookup(
            @Valid @RequestBody ReadingModels.LookupRequest request) {
        ReadingModels.LookupResult result = dictionaryService.lookup(request);
        return ApiResponse.ok(result, result.message());
    }

    /**
     * ダウンロード（配信）の共通処理。
     *
     * <p>Range が無ければ 200 で全体、あれば 206 でその範囲だけを書き出す。実体は
     * `ResourceRegion` に頼らずストリームで直接書く（Content-Type を `application/pdf` や
     * `image/png` にしたまま部分返却すると、Spring のメッセージコンバータが対応しないため）。</p>
     *
     * <p>416 は**何も書く前に**投げる（GlobalExceptionHandler が JSON のエラーを返せるように）。</p>
     *
     * @param attachment true なら `Content-Disposition: attachment`（ブラウザが保存する）。
     *                   日本語のファイル名のために Spring が `filename*=UTF-8''…` と
     *                   ASCII フォールバック（`filename="=?UTF-8?Q?…?="`）の両方を書く。
     */
    private void writeFile(ReadingModels.FileDownload file, String rangeHeader, boolean attachment,
                           HttpServletResponse response) {
        Path path = file.path();
        long length;
        try {
            // Range の判定は DB の値ではなく実体の長さで行う（DB が古くても 416 にしない）
            length = Files.size(path);
        } catch (IOException ex) {
            throw new IllegalStateException("書籍ファイルの読み込みに失敗しました。", ex);
        }
        ReadingRange.Resolved resolved = ReadingRange.resolve(rangeHeader, length);

        ContentDisposition disposition = (attachment ? ContentDisposition.attachment()
                : ContentDisposition.inline())
                .filename(file.fileName(), StandardCharsets.UTF_8).build();
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setContentType(file.contentType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
        long count = resolved == null ? length : resolved.count();
        if (resolved != null) {
            response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
            response.setHeader(HttpHeaders.CONTENT_RANGE, resolved.contentRange());
        }
        response.setContentLengthLong(count);

        try (InputStream in = Files.newInputStream(path);
             OutputStream out = response.getOutputStream()) {
            if (resolved == null) {
                in.transferTo(out);
            } else {
                StreamUtils.copyRange(in, out, resolved.start(), resolved.end());
            }
            out.flush();
        } catch (IOException ex) {
            throw new IllegalStateException("書籍ファイルの配信に失敗しました。", ex);
        }
    }
}
