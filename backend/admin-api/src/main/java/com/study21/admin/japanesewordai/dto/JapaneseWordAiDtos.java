package com.study21.admin.japanesewordai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 日本語単語 AI（batC41〜batC44）の出力 DTO の唯一の定義。
 *
 * <p>このクラスの入れ子の型が「AI 出力の構造」の唯一の定義（Single Source of Truth）。
 * プロンプトへ渡す「出力形式」はこの型から JSON Schema を自動生成して注入し、設定ページの
 * Data TAB も同じ生成結果を表示する（{@code AiResponseSchemaService} と同じ仕組み）。</p>
 *
 * <p>型とバッチ・画面の対応:</p>
 * <ul>
 *   <li>{@link Detail} … batC41（単語詳細。A 勉強・B 一覧のどちらでも使う）。**学習画面
 *       （A. 勉強）が要求する構造**をすべて持つ。値は AI が入力の語（見出し語・読み・
 *       教材に載っている意味）から作り、{@code JapaneseDetailResponseParser} が
 *       {@code JPN_単語詳細情報.詳細JSON} へ正規化して保存する。</li>
 *   <li>{@link Problem} / {@link ProblemChoice} … batC42（C1 読み・C2 漢字）、
 *       batC43（D 文脈意味）、batC44（E 漢字用法）。値は AI が入力の語から作り、
 *       {@code JapaneseProblemResponseParser} が {@code JPN_単語問題情報} と
 *       {@code JPN_単語問題選択肢情報} へ正規化して保存する。</li>
 * </ul>
 *
 * <p>キーは英語の camelCase（学習画面の型と揃える）。2.0 のプロンプトが使っていたキーは
 * {@link JsonAlias} で受ける。配列は「無い」と「空」を区別せず、無いときは空配列を返す。
 * 1 つしか無いもの（{@link Detail#transitivityPair} / {@link Detail#pronunciation} /
 * {@link Detail#memoryHint}）は、その語に当てはまらないとき null にする。</p>
 *
 * <p>なお列挙の値（{@code transitivity} など）は、既存の解析器が文字列として読むため
 * **String** で持つ（{@code @Schema} の説明に取り得る値を書く）。</p>
 */
public final class JapaneseWordAiDtos {

    private JapaneseWordAiDtos() {
    }

    /* ==================================================== batC41: 単語詳細（A・B 共通） */

    /**
     * 単語詳細（batC41 の出力）。学習画面（A. 勉強）が要求する構造をすべて持つ。
     *
     * <p>値は AI が入力の語（見出し語・読み・教材の意味）から作り、
     * {@code JapaneseDetailResponseParser} が {@code JPN_単語詳細情報.詳細JSON} へ
     * 正規化する。語義 1 件以上・例文 2 件以上・コロケーション 2 件以上が下限。</p>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Detail {

        @Schema(description = "一言の核心的な意味（見出しのすぐ下に出す中国語 1 文）。"
                + "語義の一覧（senses）とは別の項目で、こちらは 1 つだけ",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("coreMeaning")
        private String coreMeaning;

        @Schema(description = "日本語の説明（この語を日本語で説明する 1〜3 文）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("descriptionJa")
        @JsonAlias({"japaneseExplanation"})
        private String descriptionJa;

        @Schema(description = "中国語の説明（この語を中国語で説明する 1〜3 文）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("descriptionZh")
        @JsonAlias({"chineseExplanation"})
        private String descriptionZh;

        @Schema(description = "品詞（例: 名詞／動詞／い形容詞／な形容詞／副詞）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("partOfSpeech")
        private String partOfSpeech;

        @Schema(description = "JLPT のレベル。**N1〜N5 の 5 通りだけ**（例: N3）。"
                + "これは単語のレベルとして単語情報へ書き戻されるので、必ず 1 つ選ぶこと",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("jlpt")
        @JsonAlias({"jlptLevel"})
        private String jlpt;

        @Schema(description = "活用の型（例: 五段／一段／サ変／形容詞）。活用しない語は「なし」",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("conjugation")
        @JsonAlias({"conjugationType"})
        private String conjugation;

        @Schema(description = "自他の別。TRANSITIVE=他動詞 / INTRANSITIVE=自動詞 / "
                + "BOTH=両方 / NONE=どちらでもない（名詞など）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("transitivity")
        private String transitivity;

        @Schema(description = "重要度（1〜5。5 が最も重要）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("importance")
        private Integer importance;

        @Schema(description = "語義（意味のまとまり。1 件以上）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("senses")
        private List<Sense> senses;

        @Schema(description = "例文（2 件以上。語義番号で語義に結び付ける）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("examples")
        private List<Example> examples;

        @Schema(description = "文型（助詞の使い方。助詞を ** で囲む。例: 人**に**相談する）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("patterns")
        private List<Pattern> patterns;

        @Schema(description = "会話（2〜4 文の短いやりとり。場面つき）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("dialogs")
        private List<Dialog> dialogs;

        @Schema(description = "類義語と使い分け",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("synonyms")
        private List<Synonym> synonyms;

        @Schema(description = "間違えやすいポイント（中国語母語の学習者向け）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("cautions")
        @JsonAlias({"usageNotes"})
        private List<Caution> cautions;

        @Schema(description = "活用形の表（動詞・形容詞のとき。活用しない語は空）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("conjugations")
        private List<Conjugation> conjugations;

        @Schema(description = "自他動詞の対応（自他のどちらも無い語は null）")
        @JsonProperty("transitivityPair")
        private TransitivityPair transitivityPair;

        @Schema(description = "発音・アクセント。**読みは必ず書く**"
                + "（画面から読みを持たずに登録された語は、この読みを単語情報へ書き戻す）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("pronunciation")
        private Pronunciation pronunciation;

        @Schema(description = "コロケーション（よく使う言い回し。2 件以上）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("collocations")
        private List<Collocation> collocations;

        @Schema(description = "関連語（類義語・対義語・間違えやすい語・同じ読みの語）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("relatedWords")
        private List<RelatedWord> relatedWords;

        @Schema(description = "使用場面・語感のメモ（**cautions とは別物**。"
                + "間違いの指摘ではなく、場面・丁寧さの説明）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("usageNotes")
        private List<UsageNote> usageNotes;

        @Schema(description = "記憶のヒント（語源ではない。覚え方の助けとして示す）")
        @JsonProperty("memoryHint")
        private MemoryHint memoryHint;

        @Schema(description = "ミニ練習（学習画面の「練習」の節）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("practices")
        private List<Practice> practices;

        public String getCoreMeaning() {
            return coreMeaning;
        }

        public void setCoreMeaning(String coreMeaning) {
            this.coreMeaning = coreMeaning;
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

        public String getPartOfSpeech() {
            return partOfSpeech;
        }

        public void setPartOfSpeech(String partOfSpeech) {
            this.partOfSpeech = partOfSpeech;
        }

        public String getJlpt() {
            return jlpt;
        }

        public void setJlpt(String jlpt) {
            this.jlpt = jlpt;
        }

        public String getConjugation() {
            return conjugation;
        }

        public void setConjugation(String conjugation) {
            this.conjugation = conjugation;
        }

        public String getTransitivity() {
            return transitivity;
        }

        public void setTransitivity(String transitivity) {
            this.transitivity = transitivity;
        }

        public Integer getImportance() {
            return importance;
        }

        public void setImportance(Integer importance) {
            this.importance = importance;
        }

        public List<Sense> getSenses() {
            return senses;
        }

        public void setSenses(List<Sense> senses) {
            this.senses = senses;
        }

        public List<Example> getExamples() {
            return examples;
        }

        public void setExamples(List<Example> examples) {
            this.examples = examples;
        }

        public List<Pattern> getPatterns() {
            return patterns;
        }

        public void setPatterns(List<Pattern> patterns) {
            this.patterns = patterns;
        }

        public List<Dialog> getDialogs() {
            return dialogs;
        }

        public void setDialogs(List<Dialog> dialogs) {
            this.dialogs = dialogs;
        }

        public List<Synonym> getSynonyms() {
            return synonyms;
        }

        public void setSynonyms(List<Synonym> synonyms) {
            this.synonyms = synonyms;
        }

        public List<Caution> getCautions() {
            return cautions;
        }

        public void setCautions(List<Caution> cautions) {
            this.cautions = cautions;
        }

        public List<Conjugation> getConjugations() {
            return conjugations;
        }

        public void setConjugations(List<Conjugation> conjugations) {
            this.conjugations = conjugations;
        }

        public TransitivityPair getTransitivityPair() {
            return transitivityPair;
        }

        public void setTransitivityPair(TransitivityPair transitivityPair) {
            this.transitivityPair = transitivityPair;
        }

        public Pronunciation getPronunciation() {
            return pronunciation;
        }

        public void setPronunciation(Pronunciation pronunciation) {
            this.pronunciation = pronunciation;
        }

        public List<Collocation> getCollocations() {
            return collocations;
        }

        public void setCollocations(List<Collocation> collocations) {
            this.collocations = collocations;
        }

        public List<RelatedWord> getRelatedWords() {
            return relatedWords;
        }

        public void setRelatedWords(List<RelatedWord> relatedWords) {
            this.relatedWords = relatedWords;
        }

        public List<UsageNote> getUsageNotes() {
            return usageNotes;
        }

        public void setUsageNotes(List<UsageNote> usageNotes) {
            this.usageNotes = usageNotes;
        }

        public MemoryHint getMemoryHint() {
            return memoryHint;
        }

        public void setMemoryHint(MemoryHint memoryHint) {
            this.memoryHint = memoryHint;
        }

        public List<Practice> getPractices() {
            return practices;
        }

        public void setPractices(List<Practice> practices) {
            this.practices = practices;
        }
    }

    /* ------------------------------------------------------------------ 詳細の部品 */

    /**
     * 語義（意味のまとまり）。学習画面の「意味」の節が 1 件ずつ並べる。
     *
     * <p>語義番号は 1 から。{@link Example#senseNumber} と
     * {@link UsageNote#senseNumber} がこの番号を指す。</p>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sense {

        @Schema(description = "語義番号（1 から。例文と使用場面メモがこの番号を指す）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("number")
        private int number;

        @Schema(description = "日本語の意味", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("japanese")
        private String japanese;

        @Schema(description = "中国語の意味", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("chinese")
        private String chinese;

        @Schema(description = "使う場面・文脈（例: 職場で目上の人に）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("context")
        private String context;

        @Schema(description = "文体（例: 普通／やや硬い／話し言葉）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("style")
        private String style;

        @Schema(description = "補足（日本語。ニュアンス・使い分け）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("noteJapanese")
        private String noteJapanese;

        @Schema(description = "補足（中国語。中国語母語の学習者に伝わる説明）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("noteChinese")
        private String noteChinese;

        public int getNumber() {
            return number;
        }

        public void setNumber(int number) {
            this.number = number;
        }

        public String getJapanese() {
            return japanese;
        }

        public void setJapanese(String japanese) {
            this.japanese = japanese;
        }

        public String getChinese() {
            return chinese;
        }

        public void setChinese(String chinese) {
            this.chinese = chinese;
        }

        public String getContext() {
            return context;
        }

        public void setContext(String context) {
            this.context = context;
        }

        public String getStyle() {
            return style;
        }

        public void setStyle(String style) {
            this.style = style;
        }

        public String getNoteJapanese() {
            return noteJapanese;
        }

        public void setNoteJapanese(String noteJapanese) {
            this.noteJapanese = noteJapanese;
        }

        public String getNoteChinese() {
            return noteChinese;
        }

        public void setNoteChinese(String noteChinese) {
            this.noteChinese = noteChinese;
        }
    }

    /** 例文。どの語義の例文かを {@link #senseNumber} で示す。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Example {

        @Schema(description = "例文（日本語）", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("japanese")
        private String japanese;

        @Schema(description = "例文の読み（かな）", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("reading")
        private String reading;

        @Schema(description = "例文の中国語訳", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("chinese")
        private String chinese;

        @Schema(description = "どの語義の例文か（語義番号。全体に当てはまるなら null）")
        @JsonProperty("senseNumber")
        private Integer senseNumber;

        @Schema(description = "出典・場面のメモ（例: 学校生活）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("source")
        private String source;

        @Schema(description = "やさしい例文か応用例文か。BASIC=基本 / APPLIED=応用",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("level")
        private String level;

        public String getJapanese() {
            return japanese;
        }

        public void setJapanese(String japanese) {
            this.japanese = japanese;
        }

        public String getReading() {
            return reading;
        }

        public void setReading(String reading) {
            this.reading = reading;
        }

        public String getChinese() {
            return chinese;
        }

        public void setChinese(String chinese) {
            this.chinese = chinese;
        }

        public Integer getSenseNumber() {
            return senseNumber;
        }

        public void setSenseNumber(Integer senseNumber) {
            this.senseNumber = senseNumber;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }
    }

    /** 文型（助詞の使い方）。助詞は ** で囲んで示す。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pattern {

        @Schema(description = "文型（助詞を ** で囲む。例: 人**に**相談する）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("pattern")
        private String pattern;

        @Schema(description = "文型の読み", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("reading")
        private String reading;

        @Schema(description = "文型の説明（中国語）", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("chinese")
        private String chinese;

        @Schema(description = "短い例文（日本語）", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("example")
        private String example;

        @Schema(description = "例文の中国語訳", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("exampleChinese")
        private String exampleChinese;

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public String getReading() {
            return reading;
        }

        public void setReading(String reading) {
            this.reading = reading;
        }

        public String getChinese() {
            return chinese;
        }

        public void setChinese(String chinese) {
            this.chinese = chinese;
        }

        public String getExample() {
            return example;
        }

        public void setExample(String example) {
            this.example = example;
        }

        public String getExampleChinese() {
            return exampleChinese;
        }

        public void setExampleChinese(String exampleChinese) {
            this.exampleChinese = exampleChinese;
        }
    }

    /** 会話（2〜4 文の短いやりとり）。場面つき。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Dialog {

        @Schema(description = "場面（例: 職員室で先生に相談する）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("scene")
        private String scene;

        @Schema(description = "会話のせりふ（2〜4 文）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("lines")
        private List<DialogLine> lines;

        public String getScene() {
            return scene;
        }

        public void setScene(String scene) {
            this.scene = scene;
        }

        public List<DialogLine> getLines() {
            return lines;
        }

        public void setLines(List<DialogLine> lines) {
            this.lines = lines;
        }
    }

    /** 会話の 1 文（役とせりふ）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DialogLine {

        @Schema(description = "役（例: 先生／学生／店員）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("speaker")
        private String speaker;

        @Schema(description = "せりふ（日本語）", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("japanese")
        private String japanese;

        @Schema(description = "せりふの中国語訳", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("chinese")
        private String chinese;

        public String getSpeaker() {
            return speaker;
        }

        public void setSpeaker(String speaker) {
            this.speaker = speaker;
        }

        public String getJapanese() {
            return japanese;
        }

        public void setJapanese(String japanese) {
            this.japanese = japanese;
        }

        public String getChinese() {
            return chinese;
        }

        public void setChinese(String chinese) {
            this.chinese = chinese;
        }
    }

    /** 類義語と使い分け。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Synonym {

        @Schema(description = "比べる語（見出し語）", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("heading")
        private String heading;

        @Schema(description = "比べる語の読み", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("reading")
        private String reading;

        @Schema(description = "比べる語の中国語の意味",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("chinese")
        private String chinese;

        @Schema(description = "共通する意味", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("shared")
        private String shared;

        @Schema(description = "違うところ（中国語で説明してよい）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("difference")
        private String difference;

        @Schema(description = "使い分ける場面（どちらをどの場面で使うか）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("scene")
        private String scene;

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

        public String getChinese() {
            return chinese;
        }

        public void setChinese(String chinese) {
            this.chinese = chinese;
        }

        public String getShared() {
            return shared;
        }

        public void setShared(String shared) {
            this.shared = shared;
        }

        public String getDifference() {
            return difference;
        }

        public void setDifference(String difference) {
            this.difference = difference;
        }

        public String getScene() {
            return scene;
        }

        public void setScene(String scene) {
            this.scene = scene;
        }
    }

    /** 間違えやすいポイント（誤った言い方と自然な言い方を対で示す）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Caution {

        @Schema(description = "種類。GRAMMAR=文法として誤り / UNNATURAL=この場面では不自然 / "
                + "MEANING=意味の取り違え（日中同形異義語など）/ PARTICLE=助詞の選び方",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("kind")
        private String kind;

        @Schema(description = "何が問題か（見出し）", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("title")
        private String title;

        @Schema(description = "誤った言い方", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("wrong")
        private String wrong;

        @Schema(description = "自然な言い方", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("correct")
        private String correct;

        @Schema(description = "なぜ違うか（中国語で説明してよい）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("reason")
        private String reason;

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getWrong() {
            return wrong;
        }

        public void setWrong(String wrong) {
            this.wrong = wrong;
        }

        public String getCorrect() {
            return correct;
        }

        public void setCorrect(String correct) {
            this.correct = correct;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    /** 活用形（動詞・形容詞のとき）。表の 1 行を表す。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Conjugation {

        @Schema(description = "形の名前（例: て形／た形／ない形／可能形）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("form")
        private String form;

        @Schema(description = "活用した形", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("value")
        private String value;

        @Schema(description = "その形を使った短い例文",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("example")
        private String example;

        public String getForm() {
            return form;
        }

        public void setForm(String form) {
            this.form = form;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getExample() {
            return example;
        }

        public void setExample(String example) {
            this.example = example;
        }
    }

    /** 自他動詞の対応（自動詞と他動詞の対と、助詞の違い）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransitivityPair {

        @Schema(description = "自動詞", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("intransitive")
        private String intransitive;

        @Schema(description = "他動詞", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("transitive")
        private String transitive;

        @Schema(description = "助詞の違いの説明（「～が／～を」など）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("particleNote")
        private String particleNote;

        @Schema(description = "自動詞を使った例文", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("intransitiveExample")
        private String intransitiveExample;

        @Schema(description = "他動詞を使った例文", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("transitiveExample")
        private String transitiveExample;

        public String getIntransitive() {
            return intransitive;
        }

        public void setIntransitive(String intransitive) {
            this.intransitive = intransitive;
        }

        public String getTransitive() {
            return transitive;
        }

        public void setTransitive(String transitive) {
            this.transitive = transitive;
        }

        public String getParticleNote() {
            return particleNote;
        }

        public void setParticleNote(String particleNote) {
            this.particleNote = particleNote;
        }

        public String getIntransitiveExample() {
            return intransitiveExample;
        }

        public void setIntransitiveExample(String intransitiveExample) {
            this.intransitiveExample = intransitiveExample;
        }

        public String getTransitiveExample() {
            return transitiveExample;
        }

        public void setTransitiveExample(String transitiveExample) {
            this.transitiveExample = transitiveExample;
        }
    }

    /** 発音・アクセント。**確認できない値は作らず null にする。** */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pronunciation {

        @Schema(description = "読み（かな）", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("reading")
        private String reading;

        @Schema(description = "アクセントの型（1 から）。未確認なら null")
        @JsonProperty("accentType")
        private Integer accentType;

        @Schema(description = "アクセント表記（例: あꜜい）。未確認なら null")
        @JsonProperty("accentNotation")
        private String accentNotation;

        @Schema(description = "発音のヒント（中国語母語の学習者向けの補足）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("hint")
        private String hint;

        @Schema(description = "音声サンプルがあるか（無いときは画面が「音声なし」と明示する）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("hasAudioSample")
        private boolean hasAudioSample;

        public String getReading() {
            return reading;
        }

        public void setReading(String reading) {
            this.reading = reading;
        }

        public Integer getAccentType() {
            return accentType;
        }

        public void setAccentType(Integer accentType) {
            this.accentType = accentType;
        }

        public String getAccentNotation() {
            return accentNotation;
        }

        public void setAccentNotation(String accentNotation) {
            this.accentNotation = accentNotation;
        }

        public String getHint() {
            return hint;
        }

        public void setHint(String hint) {
            this.hint = hint;
        }

        public boolean isHasAudioSample() {
            return hasAudioSample;
        }

        public void setHasAudioSample(boolean hasAudioSample) {
            this.hasAudioSample = hasAudioSample;
        }
    }

    /** コロケーション（よく使う言い回し）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Collocation {

        @Schema(description = "言い回し（例: 相談に乗る）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("expression")
        private String expression;

        @Schema(description = "言い回しの読み", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("reading")
        private String reading;

        @Schema(description = "言い回しの中国語の意味",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("chinese")
        private String chinese;

        @Schema(description = "使う場面", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("usage")
        private String usage;

        @Schema(description = "言い回しを使った例文（日本語）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("exampleJapanese")
        private String exampleJapanese;

        @Schema(description = "例文の中国語訳", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("exampleChinese")
        private String exampleChinese;

        public String getExpression() {
            return expression;
        }

        public void setExpression(String expression) {
            this.expression = expression;
        }

        public String getReading() {
            return reading;
        }

        public void setReading(String reading) {
            this.reading = reading;
        }

        public String getChinese() {
            return chinese;
        }

        public void setChinese(String chinese) {
            this.chinese = chinese;
        }

        public String getUsage() {
            return usage;
        }

        public void setUsage(String usage) {
            this.usage = usage;
        }

        public String getExampleJapanese() {
            return exampleJapanese;
        }

        public void setExampleJapanese(String exampleJapanese) {
            this.exampleJapanese = exampleJapanese;
        }

        public String getExampleChinese() {
            return exampleChinese;
        }

        public void setExampleChinese(String exampleChinese) {
            this.exampleChinese = exampleChinese;
        }
    }

    /** 関連語（類義語・対義語・間違えやすい語・同じ読みの語）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RelatedWord {

        @Schema(description = "関係。類義語 / 対義語 / 間違えやすい / 同じ読み",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("relation")
        private String relation;

        @Schema(description = "関連語の見出し語", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("heading")
        private String heading;

        @Schema(description = "関連語の読み", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("reading")
        private String reading;

        @Schema(description = "関連語の中国語の意味",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("chinese")
        private String chinese;

        public String getRelation() {
            return relation;
        }

        public void setRelation(String relation) {
            this.relation = relation;
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

        public String getChinese() {
            return chinese;
        }

        public void setChinese(String chinese) {
            this.chinese = chinese;
        }
    }

    /**
     * 使用場面・語感のメモ。
     *
     * <p><strong>{@link Caution}（間違えやすいポイント）とは別物。</strong>
     * ここには「話し言葉か書き言葉か」「誰に対して使うか」のような、誤りの指摘ではない説明を書く。</p>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UsageNote {

        @Schema(description = "話し言葉か書き言葉か。話し言葉 / 書き言葉 / どちらも",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("register")
        private String register;

        @Schema(description = "丁寧さ。カジュアル / 普通 / 丁寧",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("politeness")
        private String politeness;

        @Schema(description = "使う相手・場面（例: 目上の人に）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("audience")
        private String audience;

        @Schema(description = "補足（中国語でよい）", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("note")
        private String note;

        @Schema(description = "どの語義についてか（語義番号。全体に当てはまるなら null）")
        @JsonProperty("senseNumber")
        private Integer senseNumber;

        public String getRegister() {
            return register;
        }

        public void setRegister(String register) {
            this.register = register;
        }

        public String getPoliteness() {
            return politeness;
        }

        public void setPoliteness(String politeness) {
            this.politeness = politeness;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public Integer getSenseNumber() {
            return senseNumber;
        }

        public void setSenseNumber(Integer senseNumber) {
            this.senseNumber = senseNumber;
        }
    }

    /** 記憶のヒント（**語源ではない**。覚え方の助けとして示す）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MemoryHint {

        @Schema(description = "一言の覚え方", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("hint")
        private String hint;

        @Schema(description = "その覚え方の根拠（漢字の形・場面など。中国語でよい）。"
                + "語源として断定しない",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("basis")
        private String basis;

        public String getHint() {
            return hint;
        }

        public void setHint(String hint) {
            this.hint = hint;
        }

        public String getBasis() {
            return basis;
        }

        public void setBasis(String basis) {
            this.basis = basis;
        }
    }

    /** ミニ練習（選択式・記述式の両方）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Practice {

        @Schema(description = "種類。PARTICLE=助詞 / SYNONYM=類義語の使い分け / "
                + "SCENE=場面に合う言い方 / WRITING=自由造句",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("kind")
        private String kind;

        @Schema(description = "問題文（日本語）", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("question")
        private String question;

        @Schema(description = "問題の補足（中国語でよい）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("questionChinese")
        private String questionChinese;

        @Schema(description = "選択式の選択肢（自由造句のときは空）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("choices")
        private List<String> choices;

        @Schema(description = "正解（選択式は選択肢の値。自由造句は模範例文）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("answer")
        private String answer;

        @Schema(description = "解説（中国語でよい）", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("explanation")
        private String explanation;

        @Schema(description = "記述式（自由造句）かどうか",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("freeWriting")
        private boolean freeWriting;

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public String getQuestionChinese() {
            return questionChinese;
        }

        public void setQuestionChinese(String questionChinese) {
            this.questionChinese = questionChinese;
        }

        public List<String> getChoices() {
            return choices;
        }

        public void setChoices(List<String> choices) {
            this.choices = choices;
        }

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }

        public String getExplanation() {
            return explanation;
        }

        public void setExplanation(String explanation) {
            this.explanation = explanation;
        }

        public boolean isFreeWriting() {
            return freeWriting;
        }

        public void setFreeWriting(boolean freeWriting) {
            this.freeWriting = freeWriting;
        }
    }

    /* ==================================================== batC42〜batC44: 単語問題 */

    /**
     * 単語問題 1 件（batC42 の C1・C2、batC43 の D、batC44 の E が共通で使う）。
     *
     * <p>値は AI が入力の語から作り、{@code JapaneseProblemResponseParser} が
     * {@code JPN_単語問題情報} と選択肢へ正規化する。表記と読みは入力の語で上書きするので、
     * AI が語を書き換えても母表と食い違う問題は保存されない。</p>
     *
     * <p>選択肢は<b>プール</b>（設計「2. 选项池」）: <b>正解 1 件 ＋ 誤答 4〜6 件（合計 5〜7 件）・
     * 値は一意・正解はちょうど 1 件</b>。正解は {@link #correctValue} と値が一致する行で、
     * その行の {@code wrongType} は null にする。実際に見せる 4 択は、このプールから
     * テスト作成時に「正解 1 ＋ 誤答 3」を選んで並べ替えて作る。</p>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Problem {

        @Schema(description = "問題の種別。C1_READING=表記から読みを問う / C2_KANJI=読みから表記を問う / "
                + "D_CONTEXT_MEANING=文脈に合う意味を問う / E_KANJI_USAGE=漢字の用法を問う",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("problemType")
        private String problemType;

        @Schema(description = "問題文（日本語。学習者に出す指示文）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("questionJapanese")
        private String questionJapanese;

        @Schema(description = "問題文の中国語（意味が伝わるように）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("questionChinese")
        private String questionChinese;

        @Schema(description = "問う対象の見出し語（表記）")
        @JsonProperty("targetHeading")
        private String targetHeading;

        @Schema(description = "問う対象の読み")
        @JsonProperty("targetReading")
        private String targetReading;

        @Schema(description = "音声で読み上げる文（D は文脈の文。無いときは null）")
        @JsonProperty("audioText")
        private String audioText;

        @Schema(description = "例文（日本語。空欄や下線を含めてよい）")
        @JsonProperty("sentenceJapanese")
        private String sentenceJapanese;

        @Schema(description = "例文の読み（かな）")
        @JsonProperty("sentenceReading")
        private String sentenceReading;

        @Schema(description = "正解の値（選択肢の value と必ず一致させる）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("correctValue")
        private String correctValue;

        @Schema(description = "正解の補足（なぜ正解か）")
        @JsonProperty("correctNote")
        private String correctNote;

        @Schema(description = "解説（日本語）")
        @JsonProperty("explanationJapanese")
        private String explanationJapanese;

        @Schema(description = "解説（中国語）")
        @JsonProperty("explanationChinese")
        private String explanationChinese;

        @Schema(description = "難易度。EASY / NORMAL / HARD",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("difficulty")
        private String difficulty;

        @Schema(description = "選択肢のプール（**正解 1 件 ＋ 誤答 4〜6 件 ＝ 合計 5〜7 件**。"
                + "値は一意で、正解はこのうち 1 件だけ）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("options")
        private List<ProblemChoice> options;

        public String getProblemType() {
            return problemType;
        }

        public void setProblemType(String problemType) {
            this.problemType = problemType;
        }

        public String getQuestionJapanese() {
            return questionJapanese;
        }

        public void setQuestionJapanese(String questionJapanese) {
            this.questionJapanese = questionJapanese;
        }

        public String getQuestionChinese() {
            return questionChinese;
        }

        public void setQuestionChinese(String questionChinese) {
            this.questionChinese = questionChinese;
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

        public String getAudioText() {
            return audioText;
        }

        public void setAudioText(String audioText) {
            this.audioText = audioText;
        }

        public String getSentenceJapanese() {
            return sentenceJapanese;
        }

        public void setSentenceJapanese(String sentenceJapanese) {
            this.sentenceJapanese = sentenceJapanese;
        }

        public String getSentenceReading() {
            return sentenceReading;
        }

        public void setSentenceReading(String sentenceReading) {
            this.sentenceReading = sentenceReading;
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

        public String getExplanationJapanese() {
            return explanationJapanese;
        }

        public void setExplanationJapanese(String explanationJapanese) {
            this.explanationJapanese = explanationJapanese;
        }

        public String getExplanationChinese() {
            return explanationChinese;
        }

        public void setExplanationChinese(String explanationChinese) {
            this.explanationChinese = explanationChinese;
        }

        public String getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(String difficulty) {
            this.difficulty = difficulty;
        }

        public List<ProblemChoice> getOptions() {
            return options;
        }

        public void setOptions(List<ProblemChoice> options) {
            this.options = options;
        }
    }

    /** 単語問題の選択肢 1 件（{@code JPN_単語問題選択肢情報} の 1 行）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProblemChoice {

        @Schema(description = "選択肢の値（プールの中で重複させない）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("value")
        private String value;

        @Schema(description = "選択肢の読み", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("reading")
        private String reading;

        @Schema(description = "誤答の区分（正解の行は null。例: 読み間違い／意味の取り違え）")
        @JsonProperty("wrongType")
        private String wrongType;

        @Schema(description = "この選択肢がなぜ誤りかの説明（中国語）")
        @JsonProperty("explanationChinese")
        private String explanationChinese;

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

        public String getWrongType() {
            return wrongType;
        }

        public void setWrongType(String wrongType) {
            this.wrongType = wrongType;
        }

        public String getExplanationChinese() {
            return explanationChinese;
        }

        public void setExplanationChinese(String explanationChinese) {
            this.explanationChinese = explanationChinese;
        }
    }
}
