package com.study21.user.reading;

import com.study21.user.security.UserPrincipal;
import org.springframework.web.multipart.MultipartFile;

/**
 * 読書管理（書籍管理・書籍閲覧）の業務処理。
 *
 * <p>2026-09-14 の決定で、本は**公開範囲**（`GLOBAL`=管理者が登録した全体書籍 /
 * `FAMILY`=家庭の中だけの書籍）を持ち、【自分の本棚】は**アカウントごと**になった。
 * 見える範囲と操作できる範囲は</p>
 *
 * <ul>
 *   <li>管理者 … 全体書籍だけが見える。全体書籍だけを登録・修正・削除できる</li>
 *   <li>保護者 … 全体書籍 ＋ 自分の家庭の書籍が見える。自分の家庭の書籍だけを登録・修正・削除できる</li>
 *   <li>生徒   … 見える本を読む・標記・記録。【自分の本棚】の出し入れ。登録・修正・削除はできない</li>
 * </ul>
 *
 * <p>そのため一覧・1 冊・標記・記録・配信はすべて {@link UserPrincipal} を受け取り、
 * 見えない本は 404（存在を漏らさない）、見えるが操作できないときは 403 にする。</p>
 */
public interface ReadingService {

    /**
     * 本の一覧（絞り込み・ページング・サマリ）。
     *
     * @param scope `ALL`（全体書籍＋自分の家庭。既定）/ `GLOBAL`（全体書籍だけ）/
     *              `FAMILY`（自分の家庭だけ）。null・空は `ALL`
     * @param shelf `MINE` のとき自分の【自分の本棚】だけ。null・空はすべて
     */
    ReadingModels.BookListResult searchBooks(UserPrincipal user, String keyword, String difficulty, String status,
                                             String tag, Boolean pinned, Long categoryId, String language,
                                             String scope, String shelf, int page, int size);

    /** 閲覧画面が 1 回で読むもの（書籍＋記録＋標記＋標記のあるページ）。見えない本は 404。 */
    ReadingModels.BookDetail detail(UserPrincipal user, long bookId, Integer markPage, String markType);

    /** 書籍を登録する（書籍番号は ER-yyyyMMdd-HHmmss で採番）。管理者＝全体書籍／保護者＝家庭の書籍。 */
    ReadingModels.BookMutationResult createBook(UserPrincipal user, ReadingModels.BookSaveRequest request);

    /** 書籍を修正する（楽観的ロック）。自分の家庭の書籍（管理者は全体書籍）だけ。 */
    ReadingModels.BookMutationResult updateBook(UserPrincipal user, long bookId, ReadingModels.BookSaveRequest request);

    /** 置頂（ピン留め）を切り替える。置頂は本の属性なので、直せる人だけが変えられる。 */
    ReadingModels.BookMutationResult setPinned(UserPrincipal user, long bookId, boolean pinned, Integer version);

    /** 読書ステータスを変える（読む人は誰でも変えられる。見えない本は 404）。 */
    ReadingModels.BookMutationResult setStatus(UserPrincipal user, long bookId, String status, Integer version);

    /** 書籍を削除する（読書記録・標記・みんなの本棚の行も一緒に消える）。 */
    ReadingModels.SimpleResult deleteBook(UserPrincipal user, long bookId);

    /**
     * 読書履歴（書籍を指定しなければ**見える本**の履歴すべて）。
     *
     * <p>絞り込み（すべて任意）: `dateFrom` / `dateTo` は `yyyy-MM-dd`（`dateTo` はその日
     * いっぱいを含む）、`pageNo` は「そのページを読んだ記録」。</p>
     */
    ReadingModels.RecordListResult listRecords(UserPrincipal user, Long bookId, String dateFrom, String dateTo,
                                               Integer pageNo, int page, int size);

    /** 読書記録を付ける（書籍の進捗・累計も一緒に更新する）。 */
    ReadingModels.RecordMutationResult createRecord(UserPrincipal user, long bookId,
                                                    ReadingModels.RecordSaveRequest request);

    /** 読書履歴を 1 件削除する（残った記録から累計を計算し直す）。 */
    ReadingModels.SimpleResult deleteRecord(UserPrincipal user, long recordId);

    /** 標記の一覧（ページ・種別で絞れる）。見えない本は 404。 */
    ReadingModels.MarkListResult listMarks(UserPrincipal user, long bookId, Integer pageNo, String markType,
                                           int page, int size);

    /** 標記を追加する。 */
    ReadingModels.MarkMutationResult createMark(UserPrincipal user, long bookId,
                                                ReadingModels.MarkSaveRequest request);

    /** 標記を 1 件削除する。 */
    ReadingModels.BookMutationResult deleteMark(UserPrincipal user, long markId);

    /**
     * その書籍の標記と読書記録をすべて削除し、あわせて読書の進捗もリセットする
     * （2.0 の「標記削除」を 2026-09-14 の指示で拡張。画面の【標記クリア】）。
     * 標記・読書記録の削除と進捗のリセットは 1 つのトランザクションで行う。
     */
    ReadingModels.BookMutationResult resetMarksAndProgress(UserPrincipal user, long bookId);

    // ------------------------------------------------------------ 自分の本棚

    /**
     * 【本棚に入れる】。**自分の行だけ**を作る（他人のアカウントID は受け取らない）。
     * すでに入っていれば何もしない（冪等）。入れたあとの 1 冊を返す。
     */
    ReadingModels.BookMutationResult addToShelf(UserPrincipal user, long bookId);

    /** 【本棚から外す】。入っていなければ何もしない（冪等）。 */
    ReadingModels.BookMutationResult removeFromShelf(UserPrincipal user, long bookId);

    // ------------------------------------------------------------ 分類（マスタ）

    /** 見える分類の一覧（全体の分類 ＋ 自分の家庭の分類。表示順 → 分類ID 順。冊数つき）。 */
    ReadingModels.CategoryListResult listCategories(UserPrincipal user);

    /** 分類を登録する（管理者＝全体の分類／保護者＝自分の家庭の分類）。名前は同じ持ち主の中で重複不可。 */
    ReadingModels.CategoryMutationResult createCategory(UserPrincipal user,
                                                        ReadingModels.CategorySaveRequest request);

    /** 分類を修正する（改名・並べ替え・説明）。自分の持ち物の分類だけ。 */
    ReadingModels.CategoryMutationResult updateCategory(UserPrincipal user, long categoryId,
                                                        ReadingModels.CategorySaveRequest request);

    /** 分類を削除する。**本は消さない**（分類ID が NULL に戻り未分類になる）。 */
    ReadingModels.SimpleResult deleteCategory(UserPrincipal user, long categoryId);

    // ------------------------------------------------------ 本文 PDF・表紙画像

    /** 本文 PDF を登録・差し替える（PDF のみ。totalPages があれば総ページ数を更新する）。 */
    ReadingModels.BookMutationResult uploadPdf(UserPrincipal user, long bookId, MultipartFile file,
                                               Integer totalPages);

    /** 本文 PDF の行と実体を削除する。 */
    ReadingModels.SimpleResult deletePdf(UserPrincipal user, long bookId);

    /** 本文 PDF を配信するための情報（見えない本・行が無い・実体が無い・legacy 未設定は 404）。 */
    ReadingModels.FileDownload downloadPdf(UserPrincipal user, long bookId);

    /** 表紙画像を登録・差し替える（png / jpeg / webp・5MB まで）。 */
    ReadingModels.BookMutationResult uploadCover(UserPrincipal user, long bookId, MultipartFile file);

    /** 表紙画像の行と実体を削除する。 */
    ReadingModels.SimpleResult deleteCover(UserPrincipal user, long bookId);

    /** 表紙画像を配信するための情報（見えない本・行が無い・実体が無い・legacy 未設定は 404）。 */
    ReadingModels.FileDownload downloadCover(UserPrincipal user, long bookId);
}
