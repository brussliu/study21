package com.study21.user.english;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.List;

/**
 * 英作文AI添削の「作文データの層」のモデル（画面の語彙をそのまま使う）。
 *
 * <p>画面（`frontend/pc-web/src/features/english-essay/*`）が前提にしている形に固定してある。
 * 名前を変えない（変換表を作らせない）。</p>
 *
 * <p>画像の実体は DB に持たない（`ENG_英作文画像情報` は場所と MIME だけ）。</p>
 */
public final class EnglishEssayModels {

    private EnglishEssayModels() {
    }

    /** 英検級（`ENG_英作文情報.英検級` の CHECK と同じ）。 */
    public static final List<String> LEVELS = List.of("GRADE1", "PRE1", "GRADE2");
    /** 画像区分（設問画像 / 答案画像）。 */
    public static final List<String> IMAGE_CATEGORIES = List.of("question", "answer");

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    /** 表示順の上限（同じ作文の中で 1 から）。設定の枚数上限（1〜20）より十分大きい。 */
    public static final int MAX_IMAGE_ORDER = 1000;
    /** 題・題（中国語）の長さ（`ENG_英作文情報` の VARCHAR(200) と同じ）。 */
    public static final int TITLE_MAX = 200;

    // ---------------------------------------------------------------- 一覧

    /** 一覧の「最新の添削」（まだ無ければ null）。 */
    public record LatestGrading(
            long gradingId,
            int round,
            String statusCode,
            Integer score,
            Integer maxScore,
            String createdAt) {
    }

    /**
     * 一覧の 1 行。画像枚数は LATERAL の count で数えた値。
     *
     * <p>{@code ownerAccountId} / {@code ownerName} は**その作文の持ち主**。保護者は自分の子どもの
     * 作文も見えるので、画面が「誰の作文か」を出せるようにする（自分＝ログイン中のアカウントなら
     * 本人の作文。既存のフィールドの意味は変えない）。</p>
     */
    public record EssayRow(
            long essayId,
            long ownerAccountId,
            String ownerName,
            String level,
            String title,
            String titleZh,
            String questionText,
            String essayText,
            int wordCount,
            int imageCount,
            int questionImageCount,
            int answerImageCount,
            String createdAt,
            String updatedAt,
            LatestGrading latestGrading) {
    }

    public record EssayListResult(
            List<EssayRow> items,
            long total,
            int page,
            int size,
            int totalPages) {
    }

    // ---------------------------------------------------------------- 詳細

    /** 画像 1 枚（OCR の生の結果も返す。利用者が直した本文は作文側）。 */
    public record ImageRow(
            long imageId,
            int order,
            String category,
            String originalFileName,
            String mimeType,
            long fileSize,
            String recognizedText,
            Integer confidence) {
    }

    /**
     * 添削 1 回（`ENG_AI添削履歴情報`。**user-api は参照するだけ**）。
     *
     * <p>`report` は `添削結果JSON` をそのまま展開したもの（Jackson が JSON にする）。
     * 形は 2.0 と同じレポート（4 観点・修正ポイント・改善後の作文例）。</p>
     */
    public record GradingRow(
            long gradingId,
            int round,
            String statusCode,
            String level,
            String titleJa,
            String titleZh,
            String questionText,
            String essayText,
            int wordCount,
            Integer score,
            Integer maxScore,
            JsonNode report,
            String failureReason,
            String startedAt,
            String finishedAt,
            String createdAt) {
    }

    /**
     * 作文 1 件。{@code ownerAccountId} / {@code ownerName} はその作文の持ち主
     * （保護者が子どもの作文を見たときに「誰の作文か」を出す。自分なら本人）。
     */
    public record EssayDetail(
            long essayId,
            long ownerAccountId,
            String ownerName,
            String level,
            String title,
            String titleZh,
            String questionText,
            String essayText,
            int wordCount,
            String stateCode,
            String createdAt,
            String updatedAt,
            List<ImageRow> images,
            List<GradingRow> gradings) {
    }

    // ---------------------------------------------------------------- 要求

    /** 新規作成（級・題 日/中・設問・本文）。 */
    public record CreateRequest(
            String level,
            String title,
            String titleZh,
            String questionText,
            String essayText) {
    }

    /**
     * 画像 1 枚の置き方（更新の要求に含める）。
     *
     * <p>`recognizedText` / `confidence` はその画像の **OCR の生の結果**（`認識テキスト` /
     * `認識信頼度`）。画面は OCR の結果を持っているので、保存のときに一緒に送る
     * （同期 OCR の経路ではこれまで書かれず、`ENG_英作文画像情報` の 2 列が NULL のままだった）。
     * **省略（null）のときは既存値を消さない**（`confidence` は 0〜100。外れていれば 400）。</p>
     */
    public record ImageRef(
            Long imageId,
            String category,
            Integer order,
            String recognizedText,
            Integer confidence) {

        /** 区分と表示順だけの指定（OCR の結果を送らない回）。 */
        public ImageRef(Long imageId, String category, Integer order) {
            this(imageId, category, order, null, null);
        }
    }

    /**
     * 更新（本文は語数を数え直す）。
     *
     * <p>`images` は**この作文の画像の一覧そのもの**。含まれない画像の行は削除し、含まれる行は
     * 区分と表示順を更新する。`null`（送らない）のときは画像を触らない（`[]` は全部消す）。</p>
     */
    public record UpdateRequest(
            String level,
            String title,
            String titleZh,
            String questionText,
            String essayText,
            List<ImageRef> images) {
    }

    /** 画像 1 枚を上げた結果。 */
    public record ImageUploadResult(
            long imageId,
            int order,
            String category,
            String originalFileName,
            String mimeType,
            long fileSize) {
    }

    /** 配信する画像の実体（Controller が `ResponseEntity<Resource>` にする）。 */
    public record ImageFile(
            Path path,
            String contentType,
            String fileName) {
    }

    // ---------------------------------------------------------------- 添削の受付

    /**
     * 添削の受付の結果（`POST /api/user/english-essays/{essayId}/gradings` の応答）。
     *
     * <p>`round` は**受付けた回**。要求で省略したときは admin-api が決めた「次の回」が入る
     * （採番の規則は admin-api に 1 つだけ置く）。</p>
     */
    public record GradingAccepted(
            long gradingId,
            int round,
            String message) {
    }

    // ---------------------------------------------------------------- 上限（画面へ配る）

    /**
     * 画像の上限（`GET /api/user/english-essays/limits` の応答）。
     *
     * <p>値は**設定が唯一の出所**（`ENGLISH_ESSAY_MAX_IMAGES` / `ENGLISH_ESSAY_MAX_IMAGE_MB`）。
     * 画面が同じ値を二重に持つと、設定を変えたときに事前チェックだけ古いままになる
     * （`docs/HARDCODED_LIMITS.md` A-12）。読めないときだけ既定（8 枚 / 10MB）へ落とす。</p>
     */
    public record ImageLimits(
            int maxImages,
            int maxImageMb) {
    }
}
