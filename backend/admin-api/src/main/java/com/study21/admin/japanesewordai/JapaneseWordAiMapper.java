package com.study21.admin.japanesewordai;

import com.study21.common.core.japanese.JpnWordDetailChildren;
import com.study21.common.core.japanese.JpnWordDetailEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 日本語単語 AI 取得（batC41〜batC44）の読み書き。
 *
 * <p>SQL は {@code resources/mapper/JapaneseWordAiMapper.xml}。MyBatis が
 * {@code SqlLoggingInterceptor} で自動的に SQL ログへ記録するので、
 * ここでログを書かない（{@code docs/LOGGING.md}）。</p>
 */
@Mapper
public interface JapaneseWordAiMapper {

    /**
     * AI に渡す語を選ぶ。
     *
     * @param contentType 内容種別コード（{@code A_DETAIL} など）。既に成功している語は除く
     * @param wordIds     画面から選んだ語（空なら条件に合う語を古い順に選ぶ）
     * @param limit       一度の実行で受け付ける最大の語数
     */
    List<AiCallTarget> findTargets(@Param("contentType") String contentType,
                                   @Param("wordIds") List<Long> wordIds,
                                   @Param("limit") int limit);

    /** 1 語の収録（AI に渡す入力）。 */
    List<CollectionRow> listCollections(@Param("wordId") long wordId);

    /**
     * AI に渡す既存の詳細（<b>有効版</b>のヘッダ）。まだ詳細が無ければ null。
     *
     * <p>履歴（{@code 状態コード='ARCHIVED'}）は渡さない。AI の入力に使うのは今の内容だけ。</p>
     */
    JpnWordDetailEntity findActiveDetail(@Param("wordId") long wordId);

    /* ----- 有効版に属する 11 の子テーブル（AI に渡す詳細を組み立てる材料）----- */

    /** 語義（senses）。 */
    List<JpnWordDetailChildren.Sense> listDetailSenses(@Param("detailId") long detailId);

    /** 例文（examples）。 */
    List<JpnWordDetailChildren.Example> listDetailExamples(@Param("detailId") long detailId);

    /** 文型（patterns）。 */
    List<JpnWordDetailChildren.Pattern> listDetailPatterns(@Param("detailId") long detailId);

    /** 会話の枠（dialogs）。 */
    List<JpnWordDetailChildren.Dialog> listDetailDialogs(@Param("detailId") long detailId);

    /** 会話の発言（dialogs[].lines）。親は {@code 会話ID}。 */
    List<JpnWordDetailChildren.DialogLine> listDetailDialogLines(@Param("detailId") long detailId);

    /** 類義語（synonyms）。 */
    List<JpnWordDetailChildren.Synonym> listDetailSynonyms(@Param("detailId") long detailId);

    /** 間違えやすいポイント（cautions）。 */
    List<JpnWordDetailChildren.Caution> listDetailCautions(@Param("detailId") long detailId);

    /** コロケーション（collocations）。 */
    List<JpnWordDetailChildren.Collocation> listDetailCollocations(@Param("detailId") long detailId);

    /** 関連語（relatedWords）。 */
    List<JpnWordDetailChildren.RelatedWord> listDetailRelatedWords(@Param("detailId") long detailId);

    /** 使用場面（usageNotes）。 */
    List<JpnWordDetailChildren.UsageNote> listDetailUsageNotes(@Param("detailId") long detailId);

    /** ミニ練習（practices）。 */
    List<JpnWordDetailChildren.Practice> listDetailPractices(@Param("detailId") long detailId);

    /* ===================================================== 詳細の版（切片3） */

    /**
     * 今の有効版を {@code ARCHIVED} にする（新しい版を入れる前に呼ぶ）。
     *
     * <p>消さない。履歴として残し、あとから「どの版に戻すか」を選べるようにする。</p>
     */
    int archiveActiveDetail(@Param("wordId") long wordId);

    /**
     * 新しい版のヘッダを入れる（{@code 状態コード='ACTIVE'}）。
     *
     * <p>内容版数は INSERT の中で {@code MAX(内容版数) + 1} を数える（同じ語を並行に取っても
     * 番号がぶつからない）。{@code 元詳細ID} は「今の有効版」。採番された {@code 詳細ID} が
     * {@code version.header().getDetailId()} に入る。</p>
     */
    int insertDetailVersion(JpnWordDetailEntity header);

    /**
     * 人が入れた行の {@code 表示順} の<b>最大</b>を引く（{@code 登録元コード='APP'} または
     * {@code 手修正フラグ=true}）。AI の行はこの次から振る。
     *
     * <p>名前に「件数」と付けないのは、返すのが行数ではなく<b>表示順の最大</b>だから
     * （人が入れた行が無ければ 0）。</p>
     */
    int maxKeptDetailOrderNo(@Param("detailId") long detailId);

    /* 人が入れた行の複製（元の版 → 新しい版）。内容・出所・表示順 はそのまま */

    int copyDetailSenses(@Param("detailId") long detailId,
                         @Param("sourceDetailId") long sourceDetailId);

    int copyDetailExamples(@Param("detailId") long detailId,
                           @Param("sourceDetailId") long sourceDetailId);

    int copyDetailPatterns(@Param("detailId") long detailId,
                           @Param("sourceDetailId") long sourceDetailId);

    int copyDetailDialogs(@Param("detailId") long detailId,
                          @Param("sourceDetailId") long sourceDetailId);

    /** 会話行は新しい {@code 会話ID} に付け替える（{@link #copyDetailDialogs} の後に呼ぶ）。 */
    int copyDetailDialogLines(@Param("detailId") long detailId,
                              @Param("sourceDetailId") long sourceDetailId);

    int copyDetailSynonyms(@Param("detailId") long detailId,
                           @Param("sourceDetailId") long sourceDetailId);

    int copyDetailCautions(@Param("detailId") long detailId,
                           @Param("sourceDetailId") long sourceDetailId);

    int copyDetailCollocations(@Param("detailId") long detailId,
                               @Param("sourceDetailId") long sourceDetailId);

    int copyDetailRelatedWords(@Param("detailId") long detailId,
                               @Param("sourceDetailId") long sourceDetailId);

    int copyDetailUsageNotes(@Param("detailId") long detailId,
                             @Param("sourceDetailId") long sourceDetailId);

    int copyDetailPractices(@Param("detailId") long detailId,
                            @Param("sourceDetailId") long sourceDetailId);

    /* AI の新しい行（{@code 表示順} は「複製した行の最大 + 段落内の位置」） */

    int insertDetailSense(JpnWordDetailChildren.Sense row, @Param("detailId") Long detailId);

    int insertDetailExample(JpnWordDetailChildren.Example row, @Param("detailId") Long detailId);

    int insertDetailPattern(JpnWordDetailChildren.Pattern row, @Param("detailId") Long detailId);

    /** 会話の枠を入れる（採番された {@code 会話ID} が {@code row.getDialogId()} に入る）。 */
    int insertDetailDialog(JpnWordDetailChildren.Dialog row, @Param("detailId") Long detailId);

    /** 会話の発言を入れる（親の {@code 会話ID} は {@code row.getDialogId()}）。 */
    int insertDetailDialogLine(JpnWordDetailChildren.DialogLine row, @Param("detailId") Long detailId);

    int insertDetailSynonym(JpnWordDetailChildren.Synonym row, @Param("detailId") Long detailId);

    int insertDetailCaution(JpnWordDetailChildren.Caution row, @Param("detailId") Long detailId);

    int insertDetailCollocation(JpnWordDetailChildren.Collocation row, @Param("detailId") Long detailId);

    int insertDetailRelatedWord(JpnWordDetailChildren.RelatedWord row, @Param("detailId") Long detailId);

    int insertDetailUsageNote(JpnWordDetailChildren.UsageNote row, @Param("detailId") Long detailId);

    int insertDetailPractice(JpnWordDetailChildren.Practice row, @Param("detailId") Long detailId);

    /* ===================================================== 生成履歴 */

    /**
     * まだ読みが無い語に、AI が書いた読みを入れる。
     *
     * <p>画面からの登録では読みを取らない（設計 §5）ので、詳細の取得時にここで埋める。
     * <b>既に読みがある語は更新しない</b>（画面で入れた値を AI で上書きしない）。
     * 同じ見出し語・同じ読みの語が既にあるときも更新しない（部分 UNIQUE に当てない）。
     * 読みキーは NOT NULL なので、読みと同じ値を入れる。</p>
     *
     * @return 更新した行数（0 = 既に読みがある／同じ語が既にある／語が無い）
     */
    int updateReadingIfBlank(@Param("wordId") long wordId, @Param("reading") String reading);

    /**
     * まだ JLPT レベルが無い語に、AI が書いたレベル（N1〜N5）を入れる。
     *
     * <p>一覧の「JLPT レベルで絞り込む」が効くようにするため。**既にある語は更新しない**
     * （画面で入れた値を AI で上書きしない）。値は N1〜N5 だけを渡すこと（列の CHECK がある）。</p>
     *
     * @return 更新した行数（0 = 既にレベルがある、または語が無い）
     */
    int updateJlptIfBlank(@Param("wordId") long wordId, @Param("jlpt") String jlpt);

    /** 同じ語・同じ種別の ACTIVE な問題を ARCHIVED にする。 */
    int archiveQuestions(@Param("wordId") long wordId, @Param("questionType") String questionType);

    /**
     * 次に入れる問題の版（その語・その内容種別で「今までの最大 + 1」）。
     *
     * <p>問題（C/D/E）は版で切り替える（画面の「取得状態」から開く履歴で「この版を使う」）。
     * 生成履歴と同じ数え方にそろえるので、**同じ実行の生成履歴と問題の行が同じ版番号**になる。
     * 番号は {@code ARCHIVED} の行も数える（消さないので、作り直すたびに増える）。</p>
     */
    int nextGenerationVersion(@Param("wordId") long wordId, @Param("contentType") String contentType);

    /** 問題を 1 件入れる（採番した 問題ID が {@link QuestionRow#getQuestionId()} に入る）。 */
    int insertQuestion(QuestionRow question);

    /** 選択肢を 1 件入れる。 */
    int insertChoice(ChoiceRow choice);

    /** 実行中の生成履歴（{@code QUEUED} / {@code RUNNING}）を探す。無ければ null。 */
    Long findActiveGeneration(@Param("wordId") long wordId, @Param("contentType") String contentType);

    /**
     * 生成履歴の版（実行中の行を拾い直したときに使う。{@code 内容版数}）。
     *
     * <p>中断した取得を続けるときは、**その生成と同じ版**の問題を書く（新しい版を作らない）。</p>
     */
    int findGenerationVersion(@Param("generationId") long generationId);

    /** 生成履歴を 1 行作る（{@code RUNNING}。採番した 生成ID が entity に入る）。 */
    int insertGeneration(GenerationRow generation);

    /* ===================================================== 待ち行列（受付と取り出し） */

    /**
     * 受付: 生成履歴を {@code QUEUED} で 1 行作る（AI は呼ばない）。
     *
     * <p>{@link #insertGeneration(GenerationRow)} との違いは状態だけ。同期で走らせるときは
     * すぐ実行するので {@code RUNNING} から始めるが、**非同期（受付 → 働き手）**では
     * 実行待ちとして積む（{@code QUEUED}）。採番した 生成ID が entity に入る。</p>
     */
    int insertQueuedGeneration(GenerationRow generation);

    /**
     * 働き手: 実行する 1 件を確保する（無ければ null）。
     *
     * <p>{@code QUEUED}（待ち）と、落ちたままの {@code RUNNING}
     * （{@code 更新日時} が {@code staleMinutes} 分より古いもの）を古い順に 1 件。
     * {@code FOR UPDATE SKIP LOCKED} なので、複数の働き手が同時に取りに来ても**同じ行を配らない**。</p>
     *
     * <p>{@code FAILED} は拾わない（利用者がやり直す。黙って課金し直さない）。</p>
     */
    Long findClaimableGenerationId(@Param("staleMinutes") int staleMinutes);

    /**
     * 働き手: 確保したことを確定する（状態を条件にした更新）。
     *
     * <p>取ったあとに他の働き手が先に進めていたら 0 行になる（そのときは何もしない＝二重に走らせない）。</p>
     */
    int markGenerationClaimed(@Param("generationId") long generationId,
                              @Param("fromStatuses") java.util.List<String> fromStatuses);

    /** 働き手: 確保した 1 件の中身（語・内容種別・版）。 */
    GenerationRow findGeneration(@Param("generationId") long generationId);

    /** 働き手: AI に渡す 1 語（受付が積んだ行の 単語ID から引く）。見つからなければ null。 */
    AiCallTarget findWordTarget(@Param("wordId") long wordId);

    /** 働き手: 実行に入ったことを記録する（どの AI 区分・モデルで走ったかを残す）。 */
    int markGenerationStarted(@Param("generationId") long generationId,
                              @Param("aiType") String aiType,
                              @Param("modelName") String modelName);

    /**
     * 受付けた取得の**進み具合**（その生成 ID 群の状態の内訳）。
     *
     * <p>一覧の「取得状態」は「一度でも成功したか」を優先するビューなので、取り直しの進み具合には
     * 使えない（成功済みの語は最初から「取得済」に見える）。受付が返した生成 ID を**生の表**で
     * 数えるための入口。</p>
     *
     * @return {@code {"pending":N,"succeeded":N,"failed":N}}（件数は数値）
     */
    java.util.Map<String, Object> countGenerationsByState(
            @Param("generationIds") java.util.List<Long> generationIds);

    /** 生成履歴を成功にする。 */
    int markGenerationSucceeded(@Param("generationId") long generationId,
                               @Param("callLogId") Long callLogId,
                               @Param("generatedCount") int generatedCount,
                               @Param("durationMs") int durationMs);

    /** 生成履歴を失敗にする。 */
    int markGenerationFailed(@Param("generationId") long generationId,
                             @Param("errorCode") String errorCode,
                             @Param("errorMessage") String errorMessage,
                             @Param("durationMs") int durationMs);

    /** AI に渡す 1 語。 */
    class AiCallTarget {

        private long wordId;
        private String heading;
        private String reading;
        private String partOfSpeech;
        private String jlptLevel;
        private String stateCode;

        public long getWordId() {
            return wordId;
        }

        public void setWordId(long wordId) {
            this.wordId = wordId;
        }

        public String getHeading() {
            return heading;
        }

        public void setHeading(String heading) {
            this.heading = heading;
        }

        public String getReading() {
            return reading;
        }

        public void setReading(String reading) {
            this.reading = reading;
        }

        public String getPartOfSpeech() {
            return partOfSpeech;
        }

        public void setPartOfSpeech(String partOfSpeech) {
            this.partOfSpeech = partOfSpeech;
        }

        public String getJlptLevel() {
            return jlptLevel;
        }

        public void setJlptLevel(String jlptLevel) {
            this.jlptLevel = jlptLevel;
        }

        public String getStateCode() {
            return stateCode;
        }

        public void setStateCode(String stateCode) {
            this.stateCode = stateCode;
        }
    }

    /** 収録 1 件（AI に渡す入力）。 */
    class CollectionRow {

        private String book;
        private String category;
        private Integer wordSeq;
        private String partOfSpeech;
        private String chineseMeaning;

        public String getBook() {
            return book;
        }

        public void setBook(String book) {
            this.book = book;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public Integer getWordSeq() {
            return wordSeq;
        }

        public void setWordSeq(Integer wordSeq) {
            this.wordSeq = wordSeq;
        }

        public String getPartOfSpeech() {
            return partOfSpeech;
        }

        public void setPartOfSpeech(String partOfSpeech) {
            this.partOfSpeech = partOfSpeech;
        }

        public String getChineseMeaning() {
            return chineseMeaning;
        }

        public void setChineseMeaning(String chineseMeaning) {
            this.chineseMeaning = chineseMeaning;
        }
    }

    /** 問題 1 行（INSERT の入力。採番された 問題ID が戻る）。 */
    class QuestionRow {

        private Long questionId;
        private long wordId;
        private String questionType;
        private int questionNo;
        private String questionJa;
        private String questionZh;
        private String targetHeading;
        private String targetReading;
        private String exampleJa;
        private String exampleReading;
        private String audioText;
        private String correctValue;
        private String correctNote;
        private String explanationJa;
        private String explanationZh;
        private String difficulty;
        /** AI の生の応答（そのまま 構造化JSON に入れる）。 */
        private String structuredJson;
        /**
         * この問題の版（{@link #nextGenerationVersion(long, String)} の値＝生成履歴と同じ番号）。
         * 画面の履歴で「この版を使う」と切り替えられる。
         */
        private int contentVersion;

        public Long getQuestionId() {
            return questionId;
        }

        public void setQuestionId(Long questionId) {
            this.questionId = questionId;
        }

        public long getWordId() {
            return wordId;
        }

        public void setWordId(long wordId) {
            this.wordId = wordId;
        }

        public String getQuestionType() {
            return questionType;
        }

        public void setQuestionType(String questionType) {
            this.questionType = questionType;
        }

        public int getQuestionNo() {
            return questionNo;
        }

        public void setQuestionNo(int questionNo) {
            this.questionNo = questionNo;
        }

        public String getQuestionJa() {
            return questionJa;
        }

        public void setQuestionJa(String questionJa) {
            this.questionJa = questionJa;
        }

        public String getQuestionZh() {
            return questionZh;
        }

        public void setQuestionZh(String questionZh) {
            this.questionZh = questionZh;
        }

        public String getTargetHeading() {
            return targetHeading;
        }

        public void setTargetHeading(String targetHeading) {
            this.targetHeading = targetHeading;
        }

        public String getTargetReading() {
            return targetReading;
        }

        public void setTargetReading(String targetReading) {
            this.targetReading = targetReading;
        }

        public String getExampleJa() {
            return exampleJa;
        }

        public void setExampleJa(String exampleJa) {
            this.exampleJa = exampleJa;
        }

        public String getExampleReading() {
            return exampleReading;
        }

        public void setExampleReading(String exampleReading) {
            this.exampleReading = exampleReading;
        }

        public String getAudioText() {
            return audioText;
        }

        public void setAudioText(String audioText) {
            this.audioText = audioText;
        }

        public String getCorrectValue() {
            return correctValue;
        }

        public void setCorrectValue(String correctValue) {
            this.correctValue = correctValue;
        }

        public String getCorrectNote() {
            return correctNote;
        }

        public void setCorrectNote(String correctNote) {
            this.correctNote = correctNote;
        }

        public String getExplanationJa() {
            return explanationJa;
        }

        public void setExplanationJa(String explanationJa) {
            this.explanationJa = explanationJa;
        }

        public String getExplanationZh() {
            return explanationZh;
        }

        public void setExplanationZh(String explanationZh) {
            this.explanationZh = explanationZh;
        }

        public String getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(String difficulty) {
            this.difficulty = difficulty;
        }

        public String getStructuredJson() {
            return structuredJson;
        }

        public void setStructuredJson(String structuredJson) {
            this.structuredJson = structuredJson;
        }

        public int getContentVersion() {
            return contentVersion;
        }

        public void setContentVersion(int contentVersion) {
            this.contentVersion = contentVersion;
        }
    }

    /** 選択肢 1 行（INSERT の入力）。 */
    class ChoiceRow {

        private long questionId;
        private int orderNo;
        private String value;
        private String reading;
        private boolean correct;
        private String wrongType;
        private String descriptionJa;
        private String descriptionZh;

        public long getQuestionId() {
            return questionId;
        }

        public void setQuestionId(long questionId) {
            this.questionId = questionId;
        }

        public int getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(int orderNo) {
            this.orderNo = orderNo;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getReading() {
            return reading;
        }

        public void setReading(String reading) {
            this.reading = reading;
        }

        public boolean isCorrect() {
            return correct;
        }

        public void setCorrect(boolean correct) {
            this.correct = correct;
        }

        public String getWrongType() {
            return wrongType;
        }

        public void setWrongType(String wrongType) {
            this.wrongType = wrongType;
        }

        public String getDescriptionJa() {
            return descriptionJa;
        }

        public void setDescriptionJa(String descriptionJa) {
            this.descriptionJa = descriptionJa;
        }

        public String getDescriptionZh() {
            return descriptionZh;
        }

        public void setDescriptionZh(String descriptionZh) {
            this.descriptionZh = descriptionZh;
        }
    }

    /** 生成履歴 1 行（INSERT の入力。採番された 生成ID が戻る）。 */
    class GenerationRow {

        private Long generationId;
        private long wordId;
        private String contentType;
        private String aiType;
        private String modelName;
        /**
         * この取得の版（その語・その内容種別で何回目の取得か）。
         *
         * <p>問題（C/D/E）は版で切り替えるので、**生成履歴と同じ番号**を問題の行にも入れる
         * （{@link #nextGenerationVersion(long, String)} が「今までの最大 + 1」を返す）。
         * 画面の「取得状態」から開く履歴は、この番号で「どの版を使うか」を選ぶ。</p>
         */
        private int contentVersion;

        public Long getGenerationId() {
            return generationId;
        }

        public void setGenerationId(Long generationId) {
            this.generationId = generationId;
        }

        public long getWordId() {
            return wordId;
        }

        public void setWordId(long wordId) {
            this.wordId = wordId;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getAiType() {
            return aiType;
        }

        public void setAiType(String aiType) {
            this.aiType = aiType;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public int getContentVersion() {
            return contentVersion;
        }

        public void setContentVersion(int contentVersion) {
            this.contentVersion = contentVersion;
        }
    }
}
