package com.study21.admin.englishessay;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 英作文 AI 添削の読み書き（{@code ENG_英作文情報} / {@code ENG_英作文画像情報} /
 * {@code ENG_AI添削履歴情報}）。
 *
 * <p>SQL は {@code resources/mapper/EnglishEssayAiMapper.xml}。MyBatis が
 * {@code SqlLoggingInterceptor} で自動的に SQL ログへ記録するので、ここでログを書かない
 * （{@code docs/LOGGING.md}。JDBC の直書きも禁止）。</p>
 *
 * <p><b>列名とプロパティ名</b>: DB の列名は日本語なので、XML で必ず
 * {@code AS <英語名>} を付ける（{@code map-underscore-to-camel-case} は使えない）。</p>
 */
@Mapper
public interface EnglishEssayAiMapper {

    /* ===================================================== 英作文（OCR / 受付） */

    /** 作文 1 件（添削の写しの元）。無ければ null。 */
    EssayRow findEssay(@Param("essayId") long essayId);

    /** 作文の画像（表示順＝設問 → 答案）。 */
    List<EssayImageRow> listEssayImages(@Param("essayId") long essayId);

    /** OCR の結果を画像の行へ書き戻す（その画像の文字と信頼度）。 */
    int updateImageRecognized(@Param("imageId") long imageId,
                              @Param("text") String text,
                              @Param("confidence") Integer confidence);

    /**
     * OCR の結果を作文の行へ書き戻す（設問文・作文本文・語数）。
     *
     * <p>語数は<b>数え直した値</b>を入れる（一覧・検索が使う。作文本文を人が直したときも画面が更新する）。</p>
     */
    int updateEssayRecognized(@Param("essayId") long essayId,
                              @Param("questionText") String questionText,
                              @Param("essayText") String essayText,
                              @Param("wordCount") int wordCount);

    /** 題（日本語・中国語）を書き戻す。 */
    int updateEssayTitle(@Param("essayId") long essayId,
                         @Param("titleJa") String titleJa,
                         @Param("titleZh") String titleZh);

    /* ===================================================== 添削の受付 */

    /** 実行中の添削（{@code QUEUED} / {@code RUNNING}）を探す。無ければ null。 */
    Long findActiveGrading(@Param("essayId") long essayId);

    /** 次に入れる回数（その作文の「今までの最大 + 1」）。 */
    int nextRound(@Param("essayId") long essayId);

    /**
     * 受付: 添削の履歴を {@code QUEUED} で 1 行作る（AI は呼ばない）。
     *
     * <p>級・題・設問・本文・語数は<b>そのときの写し</b>を入れる（あとで作文を直しても、
     * 出したレポートは変わらない）。採番された {@code 添削ID} が entity に入る。</p>
     */
    int insertGrading(GradingRow grading);

    /* ===================================================== 働き手（取り出しと結果） */

    /**
     * 実行する 1 件を確保する（無ければ null）。
     *
     * <p>{@code QUEUED}（待ち）と、落ちたままの {@code RUNNING}（{@code 開始日時} が
     * {@code staleMinutes} 分より古いもの。無ければ {@code 登録日時}）を古い順に 1 件。
     * {@code FOR UPDATE SKIP LOCKED} なので、複数の働き手が同時に取りに来ても同じ行を配らない。
     * {@code FAILED} は拾わない（利用者がやり直す。黙って課金し直さない）。</p>
     */
    Long findClaimableGradingId(@Param("staleMinutes") int staleMinutes);

    /** 確保したことを確定する（状態を条件にした更新。他の働き手が先に進めていたら 0 行）。 */
    int markGradingClaimed(@Param("gradingId") long gradingId,
                           @Param("fromStatuses") List<String> fromStatuses);

    /** 確保した 1 件の中身（働き手が実行するのに要る写し）。 */
    GradingRow findGrading(@Param("gradingId") long gradingId);

    /** 成功: 得点・満点・{@code 添削結果JSON}・{@code AI呼出履歴ID} を書く。 */
    int markGradingSucceeded(@Param("gradingId") long gradingId,
                             @Param("score") Integer score,
                             @Param("maxScore") Integer maxScore,
                             @Param("resultJson") String resultJson,
                             @Param("callLogId") Long callLogId);

    /** 失敗: 日本語の理由と終了日時を残す（次は拾わない。利用者がやり直す）。 */
    int markGradingFailed(@Param("gradingId") long gradingId,
                          @Param("reason") String reason);

    /** 画面のポーリング用（状態・回数・得点・失敗理由）。無ければ null。 */
    GradingStatusRow findGradingStatus(@Param("gradingId") long gradingId);

    /* ===================================================== 行の型 */

    /** 作文 1 行。 */
    class EssayRow {

        private long essayId;
        private Long userAccountId;
        private String level;
        private String titleJa;
        private String titleZh;
        private String questionText;
        private String essayText;
        private int wordCount;
        private String stateCode;

        public long getEssayId() {
            return essayId;
        }

        public void setEssayId(long essayId) {
            this.essayId = essayId;
        }

        public Long getUserAccountId() {
            return userAccountId;
        }

        public void setUserAccountId(Long userAccountId) {
            this.userAccountId = userAccountId;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getTitleJa() {
            return titleJa;
        }

        public void setTitleJa(String titleJa) {
            this.titleJa = titleJa;
        }

        public String getTitleZh() {
            return titleZh;
        }

        public void setTitleZh(String titleZh) {
            this.titleZh = titleZh;
        }

        public String getQuestionText() {
            return questionText;
        }

        public void setQuestionText(String questionText) {
            this.questionText = questionText;
        }

        public String getEssayText() {
            return essayText;
        }

        public void setEssayText(String essayText) {
            this.essayText = essayText;
        }

        public int getWordCount() {
            return wordCount;
        }

        public void setWordCount(int wordCount) {
            this.wordCount = wordCount;
        }

        public String getStateCode() {
            return stateCode;
        }

        public void setStateCode(String stateCode) {
            this.stateCode = stateCode;
        }
    }

    /** 作文の画像 1 行（実体は DB の外。相対パス + 保存ファイル名で読む）。 */
    class EssayImageRow {

        private long imageId;
        private String category;
        private int orderNo;
        private String savedFileName;
        private String relativePath;
        private String mimeType;
        private long fileSize;

        public long getImageId() {
            return imageId;
        }

        public void setImageId(long imageId) {
            this.imageId = imageId;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public int getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(int orderNo) {
            this.orderNo = orderNo;
        }

        public String getSavedFileName() {
            return savedFileName;
        }

        public void setSavedFileName(String savedFileName) {
            this.savedFileName = savedFileName;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public void setRelativePath(String relativePath) {
            this.relativePath = relativePath;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public long getFileSize() {
            return fileSize;
        }

        public void setFileSize(long fileSize) {
            this.fileSize = fileSize;
        }
    }

    /** 添削の履歴 1 行（受付の入力にも、働き手の入力にも使う）。 */
    class GradingRow {

        private Long gradingId;
        private long essayId;
        private int round;
        private String statusCode;
        private String level;
        private String titleJa;
        private String titleZh;
        private String questionText;
        private String essayText;
        private int wordCount;
        private Long userAccountId;

        public Long getGradingId() {
            return gradingId;
        }

        public void setGradingId(Long gradingId) {
            this.gradingId = gradingId;
        }

        public long getEssayId() {
            return essayId;
        }

        public void setEssayId(long essayId) {
            this.essayId = essayId;
        }

        public int getRound() {
            return round;
        }

        public void setRound(int round) {
            this.round = round;
        }

        public String getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(String statusCode) {
            this.statusCode = statusCode;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getTitleJa() {
            return titleJa;
        }

        public void setTitleJa(String titleJa) {
            this.titleJa = titleJa;
        }

        public String getTitleZh() {
            return titleZh;
        }

        public void setTitleZh(String titleZh) {
            this.titleZh = titleZh;
        }

        public String getQuestionText() {
            return questionText;
        }

        public void setQuestionText(String questionText) {
            this.questionText = questionText;
        }

        public String getEssayText() {
            return essayText;
        }

        public void setEssayText(String essayText) {
            this.essayText = essayText;
        }

        public int getWordCount() {
            return wordCount;
        }

        public void setWordCount(int wordCount) {
            this.wordCount = wordCount;
        }

        public Long getUserAccountId() {
            return userAccountId;
        }

        public void setUserAccountId(Long userAccountId) {
            this.userAccountId = userAccountId;
        }
    }

    /** 画面のポーリングが読む 1 行。 */
    class GradingStatusRow {

        private long gradingId;
        private int round;
        private String statusCode;
        private Integer score;
        private Integer maxScore;
        private String failureReason;

        public long getGradingId() {
            return gradingId;
        }

        public void setGradingId(long gradingId) {
            this.gradingId = gradingId;
        }

        public int getRound() {
            return round;
        }

        public void setRound(int round) {
            this.round = round;
        }

        public String getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(String statusCode) {
            this.statusCode = statusCode;
        }

        public Integer getScore() {
            return score;
        }

        public void setScore(Integer score) {
            this.score = score;
        }

        public Integer getMaxScore() {
            return maxScore;
        }

        public void setMaxScore(Integer maxScore) {
            this.maxScore = maxScore;
        }

        public String getFailureReason() {
            return failureReason;
        }

        public void setFailureReason(String failureReason) {
            this.failureReason = failureReason;
        }
    }
}
