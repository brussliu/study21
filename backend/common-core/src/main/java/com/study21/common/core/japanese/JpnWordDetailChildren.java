package com.study21.common.core.japanese;

import java.util.List;

/**
 * 詳細の版に属する 11 の子テーブルの行（組み立ての入力）。
 *
 * <p>user-api と admin-api の両方が読むため common-core に置く。列名は各テーブルの
 * 日本語の列名をそのまま写した（どんな列を読むかが SQL と見比べやすい）。
 * <strong>日本語のままでは Java の識別子として使えないので、列名と同じ意味の英語名にする</strong>。</p>
 *
 * <p><strong>並び</strong>: SQL が {@code 表示順, <段落>ID} で並べて返す前提。
 * ここでは並べ替えない（並びは 1 か所＝SQL が決める）。</p>
 *
 * <p><strong>共通の列</strong>: どの行も {@code orderNo}（表示順）・{@code manualCorrected}
 * （手修正フラグ）・{@code sourceCode}（登録元コード。BATCH=AI / APP=人）を持つ。
 * 会話行だけは {@code 詳細ID} ではなく {@code 会話ID} で親にぶら下がる。</p>
 */
public final class JpnWordDetailChildren {

    private JpnWordDetailChildren() {
    }

    /**
     * 11 の段落のどれかに<b>人の行</b>があるか
     * （{@code 登録元コード='APP'} または {@code 手修正フラグ=true}）。
     *
     * <p>版のヘッダの {@code 手修正フラグ} はこの結果を写したもの。段落だけを直した版でも
     * 「人の版」と分かるように、行から数え直せるようにしておく。</p>
     */
    public static boolean hasManualRows(Rows rows) {
        return Rows.hasManualRows(rows);
    }

    /** 11 の子テーブルの行をまとめて渡す（{@link JpnWordDetailAssembler} の入力）。 */
    public record Rows(List<Sense> senses,
                       List<Example> examples,
                       List<Pattern> patterns,
                       List<Dialog> dialogs,
                       List<Synonym> synonyms,
                       List<Caution> cautions,
                       List<Collocation> collocations,
                       List<RelatedWord> relatedWords,
                       List<UsageNote> usageNotes,
                       List<Practice> practices) {

        /** 何も無い版（子テーブルがすべて空）。 */
        public static Rows empty() {
            return new Rows(List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
        }

        /**
         * 11 の段落のどれかに<b>人の行</b>があるか
         * （{@code 登録元コード='APP'} または {@code 手修正フラグ=true}）。
         *
         * <p>版のヘッダの {@code 手修正フラグ} はこの結果を写したもの。段落だけを直した版でも
         * 「人の版」と分かるように、行から数え直せるようにしておく。</p>
         */
        public static boolean hasManualRows(Rows rows) {
            if (rows == null) {
                return false;
            }
            for (Sense row : nullToEmpty(rows.senses())) {
                if (manual(row.getSourceCode(), row.getManualCorrected())) {
                    return true;
                }
            }
            for (Example row : nullToEmpty(rows.examples())) {
                if (manual(row.getSourceCode(), row.getManualCorrected())) {
                    return true;
                }
            }
            for (Pattern row : nullToEmpty(rows.patterns())) {
                if (manual(row.getSourceCode(), row.getManualCorrected())) {
                    return true;
                }
            }
            for (Dialog dialog : nullToEmpty(rows.dialogs())) {
                if (manual(dialog.getSourceCode(), dialog.getManualCorrected())) {
                    return true;
                }
                for (DialogLine line : nullToEmpty(dialog.getLines())) {
                    if (manual(line.getSourceCode(), line.getManualCorrected())) {
                        return true;
                    }
                }
            }
            for (Synonym row : nullToEmpty(rows.synonyms())) {
                if (manual(row.getSourceCode(), row.getManualCorrected())) {
                    return true;
                }
            }
            for (Caution row : nullToEmpty(rows.cautions())) {
                if (manual(row.getSourceCode(), row.getManualCorrected())) {
                    return true;
                }
            }
            for (Collocation row : nullToEmpty(rows.collocations())) {
                if (manual(row.getSourceCode(), row.getManualCorrected())) {
                    return true;
                }
            }
            for (RelatedWord row : nullToEmpty(rows.relatedWords())) {
                if (manual(row.getSourceCode(), row.getManualCorrected())) {
                    return true;
                }
            }
            for (UsageNote row : nullToEmpty(rows.usageNotes())) {
                if (manual(row.getSourceCode(), row.getManualCorrected())) {
                    return true;
                }
            }
            for (Practice row : nullToEmpty(rows.practices())) {
                if (manual(row.getSourceCode(), row.getManualCorrected())) {
                    return true;
                }
            }
            return false;
        }

        private static boolean manual(String sourceCode, Boolean manualCorrected) {
            return Boolean.TRUE.equals(manualCorrected) || "APP".equals(sourceCode);
        }

        private static <T> List<T> nullToEmpty(List<T> source) {
            return source == null ? List.of() : source;
        }
    }

    /** JPN_単語詳細_語義情報（senses の 1 件）。 */
    public static class Sense {

        private Integer orderNo;
        private Integer senseNumber;
        private String japanese;
        private String chinese;
        private String context;
        private String style;
        private String noteJapanese;
        private String noteChinese;
        private Boolean manualCorrected;
        private String sourceCode;

        public Integer getOrderNo() { return orderNo; }
        public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }
        public Integer getSenseNumber() { return senseNumber; }
        public void setSenseNumber(Integer senseNumber) { this.senseNumber = senseNumber; }
        public String getJapanese() { return japanese; }
        public void setJapanese(String japanese) { this.japanese = japanese; }
        public String getChinese() { return chinese; }
        public void setChinese(String chinese) { this.chinese = chinese; }
        public String getContext() { return context; }
        public void setContext(String context) { this.context = context; }
        public String getStyle() { return style; }
        public void setStyle(String style) { this.style = style; }
        public String getNoteJapanese() { return noteJapanese; }
        public void setNoteJapanese(String noteJapanese) { this.noteJapanese = noteJapanese; }
        public String getNoteChinese() { return noteChinese; }
        public void setNoteChinese(String noteChinese) { this.noteChinese = noteChinese; }
        public Boolean getManualCorrected() { return manualCorrected; }
        public void setManualCorrected(Boolean manualCorrected) { this.manualCorrected = manualCorrected; }
        public String getSourceCode() { return sourceCode; }
        public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    }

    /** JPN_単語詳細_例文情報（examples の 1 件）。 */
    public static class Example {

        private Integer orderNo;
        private String japanese;
        private String reading;
        private String chinese;
        private Integer senseNumber;
        private String source;
        private String level;
        private Boolean manualCorrected;
        private String sourceCode;

        public Integer getOrderNo() { return orderNo; }
        public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }
        public String getJapanese() { return japanese; }
        public void setJapanese(String japanese) { this.japanese = japanese; }
        public String getReading() { return reading; }
        public void setReading(String reading) { this.reading = reading; }
        public String getChinese() { return chinese; }
        public void setChinese(String chinese) { this.chinese = chinese; }
        public Integer getSenseNumber() { return senseNumber; }
        public void setSenseNumber(Integer senseNumber) { this.senseNumber = senseNumber; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public Boolean getManualCorrected() { return manualCorrected; }
        public void setManualCorrected(Boolean manualCorrected) { this.manualCorrected = manualCorrected; }
        public String getSourceCode() { return sourceCode; }
        public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    }

    /** JPN_単語詳細_文型情報（patterns の 1 件）。 */
    public static class Pattern {

        private Integer orderNo;
        private String pattern;
        private String reading;
        private String chinese;
        private String example;
        private String exampleChinese;
        private Boolean manualCorrected;
        private String sourceCode;

        public Integer getOrderNo() { return orderNo; }
        public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        public String getReading() { return reading; }
        public void setReading(String reading) { this.reading = reading; }
        public String getChinese() { return chinese; }
        public void setChinese(String chinese) { this.chinese = chinese; }
        public String getExample() { return example; }
        public void setExample(String example) { this.example = example; }
        public String getExampleChinese() { return exampleChinese; }
        public void setExampleChinese(String exampleChinese) { this.exampleChinese = exampleChinese; }
        public Boolean getManualCorrected() { return manualCorrected; }
        public void setManualCorrected(Boolean manualCorrected) { this.manualCorrected = manualCorrected; }
        public String getSourceCode() { return sourceCode; }
        public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    }

    /** JPN_単語詳細_会話情報（dialogs の 1 件。発言は {@link DialogLine}）。 */
    public static class Dialog {

        private Long dialogId;
        private Integer orderNo;
        private String scene;
        private Boolean manualCorrected;
        private String sourceCode;
        /** この会話の発言（{@code 表示順} で並ぶ）。 */
        private List<DialogLine> lines;

        public Long getDialogId() { return dialogId; }
        public void setDialogId(Long dialogId) { this.dialogId = dialogId; }
        public Integer getOrderNo() { return orderNo; }
        public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }
        public String getScene() { return scene; }
        public void setScene(String scene) { this.scene = scene; }
        public Boolean getManualCorrected() { return manualCorrected; }
        public void setManualCorrected(Boolean manualCorrected) { this.manualCorrected = manualCorrected; }
        public String getSourceCode() { return sourceCode; }
        public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
        public List<DialogLine> getLines() { return lines; }
        public void setLines(List<DialogLine> lines) { this.lines = lines; }
    }

    /** JPN_単語詳細_会話行情報（1 発言）。親は {@code 会話ID}。 */
    public static class DialogLine {

        private Long dialogId;
        private Integer orderNo;
        private String speaker;
        private String japanese;
        private String chinese;
        private Boolean manualCorrected;
        private String sourceCode;

        public Long getDialogId() { return dialogId; }
        public void setDialogId(Long dialogId) { this.dialogId = dialogId; }
        public Integer getOrderNo() { return orderNo; }
        public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }
        public String getSpeaker() { return speaker; }
        public void setSpeaker(String speaker) { this.speaker = speaker; }
        public String getJapanese() { return japanese; }
        public void setJapanese(String japanese) { this.japanese = japanese; }
        public String getChinese() { return chinese; }
        public void setChinese(String chinese) { this.chinese = chinese; }
        public Boolean getManualCorrected() { return manualCorrected; }
        public void setManualCorrected(Boolean manualCorrected) { this.manualCorrected = manualCorrected; }
        public String getSourceCode() { return sourceCode; }
        public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    }

    /** JPN_単語詳細_類義語情報（synonyms の 1 件）。 */
    public static class Synonym {

        private Integer orderNo;
        private String heading;
        private String reading;
        private String chinese;
        private String shared;
        private String difference;
        private String scene;
        private Boolean manualCorrected;
        private String sourceCode;

        public Integer getOrderNo() { return orderNo; }
        public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }
        public String getHeading() { return heading; }
        public void setHeading(String heading) { this.heading = heading; }
        public String getReading() { return reading; }
        public void setReading(String reading) { this.reading = reading; }
        public String getChinese() { return chinese; }
        public void setChinese(String chinese) { this.chinese = chinese; }
        public String getShared() { return shared; }
        public void setShared(String shared) { this.shared = shared; }
        public String getDifference() { return difference; }
        public void setDifference(String difference) { this.difference = difference; }
        public String getScene() { return scene; }
        public void setScene(String scene) { this.scene = scene; }
        public Boolean getManualCorrected() { return manualCorrected; }
        public void setManualCorrected(Boolean manualCorrected) { this.manualCorrected = manualCorrected; }
        public String getSourceCode() { return sourceCode; }
        public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    }

    /** JPN_単語詳細_注意情報（cautions の 1 件）。 */
    public static class Caution {

        private Integer orderNo;
        private String kind;
        private String title;
        private String wrong;
        private String correct;
        private String reason;
        private Boolean manualCorrected;
        private String sourceCode;

        public Integer getOrderNo() { return orderNo; }
        public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getWrong() { return wrong; }
        public void setWrong(String wrong) { this.wrong = wrong; }
        public String getCorrect() { return correct; }
        public void setCorrect(String correct) { this.correct = correct; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public Boolean getManualCorrected() { return manualCorrected; }
        public void setManualCorrected(Boolean manualCorrected) { this.manualCorrected = manualCorrected; }
        public String getSourceCode() { return sourceCode; }
        public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    }

    /** JPN_単語詳細_コロケーション情報（collocations の 1 件）。 */
    public static class Collocation {

        private Integer orderNo;
        private String expression;
        private String reading;
        private String chinese;
        private String usage;
        private String exampleJapanese;
        private String exampleChinese;
        private Boolean manualCorrected;
        private String sourceCode;

        public Integer getOrderNo() { return orderNo; }
        public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }
        public String getExpression() { return expression; }
        public void setExpression(String expression) { this.expression = expression; }
        public String getReading() { return reading; }
        public void setReading(String reading) { this.reading = reading; }
        public String getChinese() { return chinese; }
        public void setChinese(String chinese) { this.chinese = chinese; }
        public String getUsage() { return usage; }
        public void setUsage(String usage) { this.usage = usage; }
        public String getExampleJapanese() { return exampleJapanese; }
        public void setExampleJapanese(String exampleJapanese) { this.exampleJapanese = exampleJapanese; }
        public String getExampleChinese() { return exampleChinese; }
        public void setExampleChinese(String exampleChinese) { this.exampleChinese = exampleChinese; }
        public Boolean getManualCorrected() { return manualCorrected; }
        public void setManualCorrected(Boolean manualCorrected) { this.manualCorrected = manualCorrected; }
        public String getSourceCode() { return sourceCode; }
        public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    }

    /** JPN_単語詳細_関連語情報（relatedWords の 1 件）。 */
    public static class RelatedWord {

        private Integer orderNo;
        private String relation;
        private String heading;
        private String reading;
        private String chinese;
        private Boolean manualCorrected;
        private String sourceCode;

        public Integer getOrderNo() { return orderNo; }
        public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }
        public String getRelation() { return relation; }
        public void setRelation(String relation) { this.relation = relation; }
        public String getHeading() { return heading; }
        public void setHeading(String heading) { this.heading = heading; }
        public String getReading() { return reading; }
        public void setReading(String reading) { this.reading = reading; }
        public String getChinese() { return chinese; }
        public void setChinese(String chinese) { this.chinese = chinese; }
        public Boolean getManualCorrected() { return manualCorrected; }
        public void setManualCorrected(Boolean manualCorrected) { this.manualCorrected = manualCorrected; }
        public String getSourceCode() { return sourceCode; }
        public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    }

    /** JPN_単語詳細_使用場面情報（usageNotes の 1 件）。 */
    public static class UsageNote {

        private Integer orderNo;
        private Integer senseNumber;
        private String register;
        private String politeness;
        private String audience;
        private String note;
        private Boolean manualCorrected;
        private String sourceCode;

        public Integer getOrderNo() { return orderNo; }
        public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }
        public Integer getSenseNumber() { return senseNumber; }
        public void setSenseNumber(Integer senseNumber) { this.senseNumber = senseNumber; }
        public String getRegister() { return register; }
        public void setRegister(String register) { this.register = register; }
        public String getPoliteness() { return politeness; }
        public void setPoliteness(String politeness) { this.politeness = politeness; }
        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public Boolean getManualCorrected() { return manualCorrected; }
        public void setManualCorrected(Boolean manualCorrected) { this.manualCorrected = manualCorrected; }
        public String getSourceCode() { return sourceCode; }
        public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    }

    /** JPN_単語詳細_練習情報（practices の 1 件）。 */
    public static class Practice {

        private Integer orderNo;
        private String kind;
        private String question;
        private String questionChinese;
        /** 選択肢JSON（文字列の配列）。 */
        private String choicesJson;
        private Boolean freeWriting;
        private String answer;
        private String explanation;
        private Boolean manualCorrected;
        private String sourceCode;

        public Integer getOrderNo() { return orderNo; }
        public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }
        public String getQuestionChinese() { return questionChinese; }
        public void setQuestionChinese(String questionChinese) { this.questionChinese = questionChinese; }
        public String getChoicesJson() { return choicesJson; }
        public void setChoicesJson(String choicesJson) { this.choicesJson = choicesJson; }
        public Boolean getFreeWriting() { return freeWriting; }
        public void setFreeWriting(Boolean freeWriting) { this.freeWriting = freeWriting; }
        public String getAnswer() { return answer; }
        public void setAnswer(String answer) { this.answer = answer; }
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
        public Boolean getManualCorrected() { return manualCorrected; }
        public void setManualCorrected(Boolean manualCorrected) { this.manualCorrected = manualCorrected; }
        public String getSourceCode() { return sourceCode; }
        public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    }
}
