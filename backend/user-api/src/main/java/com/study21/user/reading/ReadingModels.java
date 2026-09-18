package com.study21.user.reading;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

/**
 * 読書管理（書籍管理・書籍閲覧）のモデル。
 *
 * <p>2.0 の英語読書（`english_reading.jsp` / `english_reading_reader.jsp`）を
 * 2.1 の画面（親メニュー【読書管理】）として作り直したもの。データは
 * `RED_書籍情報` / `RED_読書記録情報` / `RED_標記情報`（2.0 から移行済み）。</p>
 */
public final class ReadingModels {

    private ReadingModels() {
    }

    /** 難易度（2.0 と同じ 4 段階）。 */
    public static final List<String> DIFFICULTIES = List.of("Starter", "Elementary", "Intermediate", "Upper");
    /** 読書ステータス（2.0 と同じ 4 種類）。 */
    public static final List<String> STATUSES = List.of("未着手", "読書中", "一時停止", "読了");
    /**
     * 本の言語。分類（棚）はジャンルだけを持ち、言語はこの値で表す
     * （DDL の CHECK と同じ並び・同じ 3 種類）。
     */
    public static final List<String> LANGUAGES = List.of("中国語", "英語", "日本語");
    /** 言語を指定しなかったときの既定（2.0 からの移行データと同じ）。 */
    public static final String DEFAULT_LANGUAGE = "英語";
    /**
     * 画面から付けられる標記の種別。
     * 2.1 の閲覧画面は本文 PDF を pdf.js で表示するので、2.0 と同じ 5 種類を扱う
     * （pen は {@code drawingData} に点列 JSON を入れる）。
     */
    public static final List<String> MARK_TYPES = List.of("highlight", "underline", "memo", "vocabulary", "pen");

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /** 分類名の最大長（DDL の VARCHAR(50) と同じ）。 */
    public static final int CATEGORY_NAME_MAX = 50;

    /** 未分類だけを引くための絞り込み値（`GET /books?categoryId=0`）。 */
    public static final long CATEGORY_NONE = 0L;

    public static final String FILE_KIND_PDF = "PDF";
    public static final String FILE_KIND_COVER = "COVER";

    // ------------------------------------------------------------------ 一覧

    /** 本棚の 1 冊。 */
    public record BookRow(
            long bookId,
            /** 利用者に見せる番号（2.0 の 書籍ID。ER-yyyyMMdd-HHmmss） */
            String bookNo,
            String subject,
            String title,
            String author,
            String difficulty,
            String status,
            int totalPages,
            int currentPage,
            boolean pinned,
            List<String> tags,
            String summary,
            String note,
            /** 直近 1 回の読書時間（分） */
            int recentMinutes,
            int totalMinutes,
            int markCount,
            String lastReadAt,
            /** 進捗（％） */
            int readPercent,
            int version,
            String createdAt,
            String updatedAt,
            /* --- ここから 2.1 の作り直しで追加（書籍管理の分類・PDF の状態。既存項目は維持） --- */
            /** 分類ID（未分類は null） */
            Long categoryId,
            /** 分類名（未分類は null） */
            String categoryName,
            /** RED_書籍ファイル情報 に PDF 行があるか */
            boolean hasPdf,
            /** アップロード（2.0 からの移行を含む）時の元ファイル名 */
            String pdfOriginalName,
            /** PDF の実体がストレージにあるか（無ければ画面は「PDF 未登録」を出す） */
            boolean pdfAvailable,
            /** 表紙行があるか */
            boolean hasCover,
            /** 表紙の実体がストレージにあるか */
            boolean coverAvailable,
            /** 本の言語（中国語 / 英語 / 日本語。教科 は 2.0 の名残で画面では使わない） */
            String language,
            /* --- ここから 2026-09-14 の追加（公開範囲と自分の本棚） --- */
            /** 公開範囲（`GLOBAL`=管理者の全体書籍 / `FAMILY`=家庭の書籍） */
            String scope,
            /** FAMILY のときの持ち主の家庭（生徒のアカウントID）。GLOBAL は null */
            Long ownerFamilyId,
            /** その家庭の生徒の表示名（画面の「家庭の本」バッジに出す）。GLOBAL は null */
            String ownerFamilyLabel,
            /** 見ている本人の【自分の本棚】に入っているか（【本棚に入れる/外す】の出し分け） */
            boolean inMyShelf) {
    }

    /** 分類マスタの 1 件（本棚の棚）。 */
    public record CategoryRow(
            long categoryId,
            String name,
            int displayOrder,
            String description,
            /** その分類に入っている冊数（削除時に「未分類になります」と案内する） */
            long bookCount,
            /**
             * 分類の持ち主の家庭（生徒のアカウントID）。
             * null＝全体の分類（管理者が作り、全家庭に見える）。画面の「全体」バッジに使う。
             */
            Long ownerFamilyId) {
    }

    public record CategoryListResult(List<CategoryRow> items) {
    }

    /** 書籍の PDF・表紙を配信するための情報（実体のパスは API に露出させない）。 */
    public record FileDownload(Path path, String contentType, String fileName, long size) {
    }

    /** 本棚のサマリ（見出しに出す件数・時間）。 */
    public record ShelfTotals(
            long bookCount,
            long readingCount,
            long finishedCount,
            long totalMinutes,
            long markCount,
            /** 本文 PDF の行があり、実体もストレージにある冊数（書籍管理のサマリ） */
            long pdfCount,
            /** 未分類（分類ID が NULL）の冊数（分類タブのバッジ） */
            long uncategorizedCount,
            /**
             * 見ている本人の【自分の本棚】の冊数。
             * 図書館タブで「自分の本棚 N 冊」を出すために使う（決定: 本棚はアカウント単位）。
             */
            long myShelfCount) {
    }

    public record BookListResult(
            List<BookRow> items,
            long totalElements,
            int page,
            int size,
            int totalPages,
            ShelfTotals totals) {
    }

    /** 書籍管理画面の下部に出す「読書履歴」の 1 行。 */
    public record RecordRow(
            long recordId,
            long bookId,
            String bookTitle,
            String readAt,
            Integer pageStart,
            Integer pageEnd,
            int minutes,
            int markCount,
            String memo) {
    }

    public record RecordListResult(List<RecordRow> items, long totalElements, int page, int size, int totalPages) {
    }

    /** 標記の 1 件。 */
    public record MarkRow(
            long markId,
            long bookId,
            int pageNo,
            String markType,
            String targetText,
            String content,
            String color,
            String createdAt,
            /* --- ここから PDF に重ねるための座標（ページ内の正規化値 0〜1。未設定は null） --- */
            BigDecimal positionX,
            BigDecimal positionY,
            BigDecimal width,
            BigDecimal height,
            /** 手書き（pen）の点列 JSON。文字列のまま返す（中身は加工しない） */
            String drawingData) {
    }

    public record MarkListResult(
            List<MarkRow> items,
            long totalElements,
            int page,
            int size,
            int totalPages,
            /** 種別ごとの件数（語彙・ハイライト…） */
            long vocabularyCount,
            long highlightCount,
            long memoCount,
            long underlineCount) {
    }

    /** 閲覧画面が1回で読むもの（書籍＋その本の記録・標記）。 */
    public record BookDetail(
            BookRow book,
            List<RecordRow> records,
            List<MarkRow> marks,
            /** 標記が付いているページ番号（ページレールの印） */
            List<Integer> markedPages) {
    }

    // ------------------------------------------------------------ 登録・更新

    /** 書籍の登録・修正。 */
    public record BookSaveRequest(
            @NotBlank(message = "書籍名を入力してください。")
            @Size(max = 150, message = "書籍名は150文字以内で入力してください。") String title,
            @NotBlank(message = "作者を入力してください。")
            @Size(max = 120, message = "作者は120文字以内で入力してください。") String author,
            @Size(max = 20, message = "難易度の指定が正しくありません。") String difficulty,
            @Size(max = 20, message = "読書ステータスの指定が正しくありません。") String status,
            @Min(value = 1, message = "総ページ数は1以上で入力してください。") int totalPages,
            Integer currentPage,
            Boolean pinned,
            List<String> tags,
            String summary,
            String note,
            /** 楽観的ロック（修正のとき必須） */
            Integer version,
            /** 分類ID（null＝未分類。0 や存在しない ID は受け付けない） */
            Long categoryId,
            /**
             * 本の言語（中国語 / 英語 / 日本語）。null か空なら既存値を残し、
             * 新規登録のときは '英語' にする。
             */
            @Size(max = 20, message = "言語の指定が正しくありません。") String language,
            /**
             * 公開範囲（`GLOBAL` / `FAMILY`）。**管理者の登録・修正のときだけ有効**。
             * 保護者はサーバが `FAMILY`（自家庭）に固定するので、送っても無視される
             * （決定: 作成時のスコープはクライアントに決めさせない）。
             */
            @Size(max = 20, message = "公開範囲の指定が正しくありません。") String scope) {

        /**
         * 公開範囲を省略する登録・修正（画面は公開範囲を送らない。
         * サーバがロールで決める: 管理者＝GLOBAL / 保護者＝FAMILY＋自家庭）。
         */
        public BookSaveRequest(String title, String author, String difficulty, String status, int totalPages,
                               Integer currentPage, Boolean pinned, List<String> tags, String summary,
                               String note, Integer version, Long categoryId, String language) {
            this(title, author, difficulty, status, totalPages, currentPage, pinned, tags, summary,
                    note, version, categoryId, language, null);
        }
    }

    /** 分類の登録・修正。 */
    public record CategorySaveRequest(
            @NotBlank(message = "分類名を入力してください。")
            @Size(max = CATEGORY_NAME_MAX, message = "分類名は50文字以内で入力してください。") String name,
            /** 本棚での並び順（省略時は末尾）。0 以上 */
            @Min(value = 0, message = "表示順は0以上で入力してください。") Integer displayOrder,
            String description) {
    }

    /** 置頂（ピン留め）の切り替え。 */
    public record PinRequest(boolean pinned, Integer version) {
    }

    /** 読書ステータスの変更。 */
    public record StatusRequest(@NotBlank(message = "読書ステータスを指定してください。") String status, Integer version) {
    }

    /** 読書記録の登録。 */
    public record RecordSaveRequest(
            /** 読書日時（省略時は受信時刻） */
            String readAt,
            Integer pageStart,
            Integer pageEnd,
            @Min(value = 0, message = "読書時間は0以上で入力してください。") Integer minutes,
            Integer markCount,
            String memo) {
    }

    /** 標記の登録。 */
    public record MarkSaveRequest(
            @Min(value = 1, message = "ページ番号は1以上で指定してください。") int pageNo,
            @NotBlank(message = "標記の種別を指定してください。") String markType,
            String targetText,
            String content,
            String color,
            /* --- PDF に重ねるための座標（ページ内の正規化値 0〜1。任意） --- */
            BigDecimal positionX,
            BigDecimal positionY,
            BigDecimal width,
            BigDecimal height,
            /** 手書き（pen）の点列 JSON。文字列のまま保存する */
            String drawingData) {
    }

    // ------------------------------------------------------- 語彙・読みの引き当て

    /** 選んだ語の意味・読みを引く要求（英語の本は語彙、中国語の本は読み方）。 */
    public record LookupRequest(
            @NotBlank(message = "語を入力してください。")
            @Size(max = 100, message = "語は100文字以内で入力してください。") String text,
            @NotBlank(message = "言語を指定してください。") String language) {
    }

    /**
     * 引き当ての結果。**取れなかった項目は null**（例外にはしない）。
     *
     * @param source 使った提供元（`EXCELAPI` / `YOUDAO` / `GOOGLE` / `AI` / `MANUAL`。複数は '+' 連結）
     * @param cached `RED_語彙辞書情報` から返したなら true（外部へ行っていない）
     */
    public record LookupResult(
            String text,
            String language,
            String japanese,
            String chinese,
            String pinyin,
            String explanation,
            String source,
            boolean cached,
            String message) {
    }

    // ------------------------------------------------------------------ 返信

    /** 書籍の更新結果（更新後の 1 冊を返すので画面がそのまま差し替えられる）。 */
    public record BookMutationResult(BookRow book, String message) {
    }

    /** 分類の更新結果。 */
    public record CategoryMutationResult(CategoryRow category, String message) {
    }

    /** 読書記録の登録結果（書籍の進捗も一緒に返す）。 */
    public record RecordMutationResult(RecordRow record, BookRow book, String message) {
    }

    /** 標記の登録結果。 */
    public record MarkMutationResult(MarkRow mark, BookRow book, String message) {
    }

    /** 削除などの結果。 */
    public record SimpleResult(int count, String message) {
    }
}
