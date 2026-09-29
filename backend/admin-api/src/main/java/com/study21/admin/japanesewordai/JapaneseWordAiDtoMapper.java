package com.study21.admin.japanesewordai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.admin.japanesewordai.dto.BatC41ResultDto;
import com.study21.admin.japanesewordai.dto.BatC42ResultDto;
import com.study21.admin.japanesewordai.dto.BatC43ResultDto;
import com.study21.admin.japanesewordai.dto.BatC44ResultDto;
import com.study21.admin.japanesewordai.dto.JapaneseWordAiDtos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI の応答 → DTO → DB の形へ移す（純関数。AI も DB も触らない）。
 *
 * <p><strong>なぜ DTO から移すのか。</strong> プロンプト（{@code BatC41ResultDto} などの
 * JSON Schema）は {@code JapaneseWordAiDtos} から自動生成して渡すので、AI は
 * <b>DTO のキー名（英語 camelCase）</b> で返す。したがって応答は Jackson でそのまま DTO へ
 * デシリアライズでき、キー名を手で書き換える必要がない
 * （{@code JapaneseWordAiDtos} に {@code @JsonIgnoreProperties(ignoreUnknown = true)} が
 * 付いているので、AI が余計なキーを足しても無視される）。</p>
 *
 * <p>移す先は 2 つ:</p>
 * <ul>
 *   <li>{@link #parseDetail(String)} … {@code JPN_単語詳細情報.詳細JSON}（学習画面が読む形。
 *       {@code docs} ではなく {@code DemoWordStudyView.vue} / {@code wordMapper.ts} が読む
 *       キー名に合わせる）</li>
 *   <li>{@link #parseProblems(String, String, String, String)} … {@code JPN_単語問題情報} と
 *       {@code JPN_単語問題選択肢情報} の 1 行ずつ</li>
 * </ul>
 *
 * <p><strong>詳細の下限</strong>: プロンプトが「語義 1 件以上・例文 2 件以上・コロケーション
 * 2 件以上」を求めているので、満たさない応答は受け取らない（{@code THIN_CONTENT}）。
 * 学習画面が空になるのを DB に入れる前に止める。</p>
 *
 * <p><strong>問題の正解は入力の語が正</strong>（AI の値を信用しない）: C1 は入力の読み、
 * C2 は入力の表記。D / E は AI の {@code correctValue} を使う。選択肢は
 * <b>正解 1 件 ＋ 誤答 4〜6 件（合計 5〜7 件）・値は一意・正解はちょうど 1 件</b>で、
 * <b>正解の行の {@code wrongType} は null</b> にする（2.0 の実データ 6,800 行と同じ。誤答だけ区分を残す）。</p>
 *
 * <p><strong>選択肢は 4 択ではなくプール</strong>（切片6・設計「2. 选项池」）: ここで作るのは
 * 「その語の問題が持つ選択肢の全部」で、4 択に絞るのはテスト作成時（user-api の
 * {@code JapaneseServiceImpl.createTest} が正解 1 ＋ 誤答 3 を選んで並べ替える）。
 * 規則を満たさない応答は<b>整題不合格</b>にして 1 行も書かない（呼び側が失敗として記録する）。</p>
 *
 * <p>DTO に無い項目は null / 空配列で埋める（画面は {@code v-if} で耐える）。
 * <b>存在しない値は作らない</b>（音声の URL は null、アクセント型は無ければ null）。</p>
 */
public final class JapaneseWordAiDtoMapper {

    private JapaneseWordAiDtoMapper() {
    }

    /** プロンプトが求める下限（語義・例文・コロケーション）。 */
    private static final int MIN_SENSES = 1;
    private static final int MIN_EXAMPLES = 2;
    private static final int MIN_COLLOCATIONS = 2;

    /** 選択肢プールの件数（正解 1 ＋ 誤答 4〜6 ＝ 合計 5〜7）。 */
    private static final int MIN_CHOICES = 5;
    private static final int MAX_CHOICES = 7;
    /**
     * 正解はちょうどこの件数（プールでも正解は 1 件）。
     *
     * <p>{@link #MIN_CHOICES} ＝ 正解 1 ＋ 誤答 4 なので、<b>件数を通れば誤答は必ず 4 件以上</b>
     * （テスト作成時に選ぶ誤答 3 件は必ず確保できる）。</p>
     */
    private static final int REQUIRED_CORRECT_CHOICES = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 正規化の結果。失敗のときは {@code detailJson} が null、成功のときは {@code errorCode} が null。
     *
     * @param reading AI が書いた語の読み（{@code pronunciation.reading}）。空なら null。
     *                読みを持たずに登録された語へ書き戻すために使う（設計 §5）
     * @param jlpt    AI が書いた JLPT レベル（{@code jlpt}）。**N1〜N5 以外は null**。
     *                レベルを持たない語へ書き戻すために使う
     */
    public record DetailResult(String detailJson, String reading, String jlpt,
                               String errorCode, String errorMessage) {

        static DetailResult success(String detailJson, String reading, String jlpt) {
            return new DetailResult(detailJson, reading, jlpt, null, null);
        }

        static DetailResult failure(String errorCode, String errorMessage) {
            return new DetailResult(null, null, null, errorCode, errorMessage);
        }

        public boolean isSuccess() {
            return errorCode == null;
        }
    }

    /** 選択肢 1 件（{@code JPN_単語問題選択肢情報} の 1 行）。 */
    public record ParsedChoice(int orderNo, String value, String reading, boolean correct,
                               String wrongType, String explanationJa, String explanationZh) {
    }

    /** 問題 1 件（{@code JPN_単語問題情報} の 1 行 ＋ 選択肢）。 */
    public record ParsedProblem(String questionType, int questionNo, String questionJa, String questionZh,
                                String targetHeading, String targetReading, String exampleJa,
                                String exampleReading, String audioText, String correctValue,
                                String correctNote, String explanationJa, String explanationZh,
                                String difficulty,
                                /** その問題ぶんの DTO を JSON にしたもの（{@code 構造化JSON} に入れる）。 */
                                String structuredJson,
                                List<ParsedChoice> choices) {
    }

    /** 正規化の結果。 */
    public record ProblemResult(List<ParsedProblem> problems, String errorCode, String errorMessage) {

        static ProblemResult success(List<ParsedProblem> problems) {
            return new ProblemResult(problems, null, null);
        }

        static ProblemResult failure(String errorCode, String errorMessage) {
            return new ProblemResult(null, errorCode, errorMessage);
        }

        public boolean isSuccess() {
            return errorCode == null;
        }
    }

    /* ==================================================== batC41: 単語詳細 */

    /**
     * AI の応答を {@code 詳細JSON}（学習画面が読む形）へ移す。
     *
     * @param body AI の応答（{@code {"detail": {...}}}。Markdown のフェンスは剥がしてから渡す）
     */
    public static DetailResult parseDetail(String body) {
        if (body == null || body.isBlank()) {
            return DetailResult.failure("EMPTY_RESPONSE", "AI の応答が空です。");
        }
        JsonNode raw;
        BatC41ResultDto dto;
        try {
            raw = MAPPER.readTree(body);
            dto = MAPPER.readValue(body, BatC41ResultDto.class);
        } catch (Exception cause) {
            return DetailResult.failure("INVALID_JSON", "AI の応答が JSON ではありません。");
        }
        JapaneseWordAiDtos.Detail detail = dto == null ? null : dto.getDetail();
        if (detail == null) {
            return DetailResult.failure("EMPTY_RESPONSE", "AI の応答に単語詳細（detail）が入っていません。");
        }
        int senses = items(detail.getSenses()).size();
        if (senses < MIN_SENSES) {
            return DetailResult.failure("THIN_CONTENT",
                    "語義が " + MIN_SENSES + " 件以上ありません（" + senses + " 件）。");
        }
        int examples = items(detail.getExamples()).size();
        if (examples < MIN_EXAMPLES) {
            return DetailResult.failure("THIN_CONTENT",
                    "例文が " + MIN_EXAMPLES + " 件以上ありません（" + examples + " 件）。");
        }
        int collocations = items(detail.getCollocations()).size();
        if (collocations < MIN_COLLOCATIONS) {
            return DetailResult.failure("THIN_CONTENT",
                    "コロケーションが " + MIN_COLLOCATIONS + " 件以上ありません（" + collocations + " 件）。");
        }
        try {
            return DetailResult.success(MAPPER.writeValueAsString(toDetailJson(detail, raw)),
                    pronunciationReading(detail.getPronunciation()), jlptLevel(detail.getJlpt()));
        } catch (Exception cause) {
            return DetailResult.failure("INVALID_JSON", "詳細JSON を組み立てられませんでした。");
        }
    }

    /** AI が書いた語の読み。書いていなければ null（空文字は読まない）。 */
    private static String pronunciationReading(JapaneseWordAiDtos.Pronunciation pronunciation) {
        if (pronunciation == null) {
            return null;
        }
        String reading = pronunciation.getReading();
        return reading == null || reading.isBlank() ? null : reading.trim();
    }

    /**
     * AI が書いた JLPT レベル。**N1〜N5 だけ**を受け取る（それ以外は null）。
     *
     * <p>この値はそのまま {@code JPN_単語情報.JLPTレベル} に入る。列の CHECK は
     * {@code N1〜N5} だけを許すので、ここで落としてから書き戻す。</p>
     */
    private static String jlptLevel(String jlpt) {
        if (jlpt == null) {
            return null;
        }
        String normalized = jlpt.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.matches("N[1-5]") ? normalized : null;
    }

    /**
     * DTO を {@code 詳細JSON} のキーへ写す。
     *
     * <p>キー名は学習画面（{@code DemoWordStudyView.vue}）と 2.1 の既存画面
     * （{@code wordMapper.ts}）が読む名前。{@code jlpt} ではなく {@code jlptLevel}、
     * 発音は 1 件の {@code pronunciation} と配列の {@code pronunciations} の両方を入れる。</p>
     */
    private static Map<String, Object> toDetailJson(JapaneseWordAiDtos.Detail detail, JsonNode raw) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("coreMeaning", detail.getCoreMeaning());
        // 2.1 の既存画面（wordMapper.ts）は核心の意味を chineseMeaning で読む。
        // 値は 1 つしか無いので、両方の名前で同じ値を入れる（取り違えを画面側で起こさない）
        json.put("chineseMeaning", detail.getCoreMeaning());
        json.put("descriptionJa", detail.getDescriptionJa());
        json.put("descriptionZh", detail.getDescriptionZh());
        json.put("partOfSpeech", detail.getPartOfSpeech());
        // 学習画面の名前は jlptLevel（AI の DTO は jlpt）
        json.put("jlptLevel", detail.getJlpt());
        json.put("conjugation", detail.getConjugation());
        json.put("transitivity", detail.getTransitivity());
        json.put("importance", detail.getImportance());
        // AI が作った直後は「手で直していない」（手直しは別の画面が true にする）
        json.put("manuallyCorrected", false);

        json.put("senses", senses(detail.getSenses()));
        json.put("examples", examples(detail.getExamples()));
        json.put("patterns", patterns(detail.getPatterns()));
        json.put("dialogs", dialogs(detail.getDialogs()));
        json.put("synonyms", synonyms(detail.getSynonyms()));
        json.put("cautions", cautions(detail.getCautions()));
        json.put("conjugations", conjugations(detail.getConjugations()));
        json.put("transitivityPair", transitivityPair(detail.getTransitivityPair()));
        json.put("pronunciation", pronunciation(detail.getPronunciation()));
        json.put("pronunciations", pronunciations(detail.getPronunciation()));
        json.put("collocations", collocations(detail.getCollocations()));
        json.put("relatedWords", relatedWords(detail.getRelatedWords()));
        json.put("usageNotes", usageNotes(detail.getUsageNotes()));
        json.put("memoryHint", memoryHint(detail.getMemoryHint()));
        json.put("practices", practices(detail.getPractices()));

        // AI の生の応答（ルートの JSON）をそのまま残す（あとから突き合わせられるように）
        json.put("structured", raw);
        return json;
    }

    private static List<Map<String, Object>> senses(List<JapaneseWordAiDtos.Sense> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        int index = 0;
        for (JapaneseWordAiDtos.Sense sense : items(source)) {
            index += 1;
            Map<String, Object> item = new LinkedHashMap<>();
            // 例文と使用場面メモが語義番号で結び付くので、無ければ 1 から振る
            item.put("number", sense.getNumber() > 0 ? sense.getNumber() : index);
            item.put("japanese", sense.getJapanese());
            item.put("chinese", sense.getChinese());
            item.put("context", sense.getContext());
            item.put("style", sense.getStyle());
            item.put("noteJapanese", sense.getNoteJapanese());
            item.put("noteChinese", sense.getNoteChinese());
            list.add(item);
        }
        return list;
    }

    private static List<Map<String, Object>> examples(List<JapaneseWordAiDtos.Example> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JapaneseWordAiDtos.Example example : items(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("japanese", example.getJapanese());
            item.put("reading", example.getReading());
            item.put("chinese", example.getChinese());
            item.put("senseNumber", example.getSenseNumber());
            item.put("source", example.getSource());
            item.put("level", example.getLevel());
            list.add(item);
        }
        return list;
    }

    private static List<Map<String, Object>> patterns(List<JapaneseWordAiDtos.Pattern> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JapaneseWordAiDtos.Pattern pattern : items(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("pattern", pattern.getPattern());
            item.put("reading", pattern.getReading());
            item.put("chinese", pattern.getChinese());
            item.put("example", pattern.getExample());
            item.put("exampleChinese", pattern.getExampleChinese());
            list.add(item);
        }
        return list;
    }

    private static List<Map<String, Object>> dialogs(List<JapaneseWordAiDtos.Dialog> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JapaneseWordAiDtos.Dialog dialog : items(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("scene", dialog.getScene());
            List<Map<String, Object>> lines = new ArrayList<>();
            for (JapaneseWordAiDtos.DialogLine line : items(dialog.getLines())) {
                Map<String, Object> lineJson = new LinkedHashMap<>();
                lineJson.put("speaker", line.getSpeaker());
                lineJson.put("japanese", line.getJapanese());
                lineJson.put("chinese", line.getChinese());
                lines.add(lineJson);
            }
            item.put("lines", lines);
            list.add(item);
        }
        return list;
    }

    private static List<Map<String, Object>> synonyms(List<JapaneseWordAiDtos.Synonym> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JapaneseWordAiDtos.Synonym synonym : items(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("heading", synonym.getHeading());
            item.put("reading", synonym.getReading());
            item.put("chinese", synonym.getChinese());
            item.put("shared", synonym.getShared());
            item.put("difference", synonym.getDifference());
            item.put("scene", synonym.getScene());
            list.add(item);
        }
        return list;
    }

    private static List<Map<String, Object>> cautions(List<JapaneseWordAiDtos.Caution> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JapaneseWordAiDtos.Caution caution : items(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kind", caution.getKind());
            item.put("title", caution.getTitle());
            item.put("wrong", caution.getWrong());
            item.put("correct", caution.getCorrect());
            item.put("reason", caution.getReason());
            list.add(item);
        }
        return list;
    }

    private static List<Map<String, Object>> conjugations(List<JapaneseWordAiDtos.Conjugation> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JapaneseWordAiDtos.Conjugation conjugation : items(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("form", conjugation.getForm());
            item.put("value", conjugation.getValue());
            item.put("example", conjugation.getExample());
            list.add(item);
        }
        return list;
    }

    /** 自他動詞の対応。当てはまらない語では null（画面は節ごと出さない）。 */
    private static Map<String, Object> transitivityPair(JapaneseWordAiDtos.TransitivityPair pair) {
        if (pair == null) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("intransitive", pair.getIntransitive());
        item.put("transitive", pair.getTransitive());
        item.put("particleNote", pair.getParticleNote());
        item.put("intransitiveExample", pair.getIntransitiveExample());
        item.put("transitiveExample", pair.getTransitiveExample());
        return item;
    }

    /** 発音（1 件）。読みが無い語では null。 */
    private static Map<String, Object> pronunciation(JapaneseWordAiDtos.Pronunciation source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("reading", source.getReading());
        // アクセントは未確認なら null（推測で埋めない）
        item.put("accentType", source.getAccentType());
        item.put("accentNotation", source.getAccentNotation());
        item.put("hint", source.getHint());
        item.put("hasAudioSample", source.isHasAudioSample());
        return item;
    }

    /**
     * 発音の配列（2.1 の既存画面 {@code wordMapper.ts} が読む形）。
     *
     * <p>DTO は発音を 1 件しか持たないので、配列は 0 件か 1 件。
     * {@code moraCount} は DTO に無いので<b>読みの文字数</b>で埋める（分からなければ 0）。
     * 音声の URL と提供元は別の仕組み（音声キャッシュ）が持つので常に null。</p>
     */
    private static List<Map<String, Object>> pronunciations(JapaneseWordAiDtos.Pronunciation source) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (source == null) {
            return list;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("reading", source.getReading());
        item.put("accentNotation", source.getAccentNotation());
        item.put("accentType", source.getAccentType());
        String reading = source.getReading();
        item.put("moraCount", reading == null || reading.isBlank() ? 0 : reading.length());
        item.put("audioUrl", null);
        item.put("audioProvider", null);
        list.add(item);
        return list;
    }

    private static List<Map<String, Object>> collocations(List<JapaneseWordAiDtos.Collocation> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JapaneseWordAiDtos.Collocation collocation : items(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("expression", collocation.getExpression());
            item.put("reading", collocation.getReading());
            item.put("chinese", collocation.getChinese());
            item.put("usage", collocation.getUsage());
            item.put("exampleJapanese", collocation.getExampleJapanese());
            item.put("exampleChinese", collocation.getExampleChinese());
            list.add(item);
        }
        return list;
    }

    private static List<Map<String, Object>> relatedWords(List<JapaneseWordAiDtos.RelatedWord> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JapaneseWordAiDtos.RelatedWord related : items(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("relation", related.getRelation());
            item.put("heading", related.getHeading());
            item.put("reading", related.getReading());
            item.put("chinese", related.getChinese());
            list.add(item);
        }
        return list;
    }

    private static List<Map<String, Object>> usageNotes(List<JapaneseWordAiDtos.UsageNote> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JapaneseWordAiDtos.UsageNote note : items(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("register", note.getRegister());
            item.put("politeness", note.getPoliteness());
            item.put("audience", note.getAudience());
            item.put("note", note.getNote());
            item.put("senseNumber", note.getSenseNumber());
            list.add(item);
        }
        return list;
    }

    /** 記憶のヒント。無ければ null。 */
    private static Map<String, Object> memoryHint(JapaneseWordAiDtos.MemoryHint source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("hint", source.getHint());
        item.put("basis", source.getBasis());
        return item;
    }

    private static List<Map<String, Object>> practices(List<JapaneseWordAiDtos.Practice> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JapaneseWordAiDtos.Practice practice : items(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kind", practice.getKind());
            item.put("question", practice.getQuestion());
            item.put("questionChinese", practice.getQuestionChinese());
            // 自由造句では選択肢が空（画面は空配列で耐える）
            item.put("choices", new ArrayList<>(items(practice.getChoices())));
            item.put("answer", practice.getAnswer());
            item.put("explanation", practice.getExplanation());
            item.put("freeWriting", practice.isFreeWriting());
            list.add(item);
        }
        return list;
    }

    /* ==================================================== batC42〜batC44: 単語問題 */

    /**
     * AI の応答を問題の行へ移す。
     *
     * @param body    AI の応答（C は {@code {"problems": [...]}}、D / E は {@code {"problem": {...}}}）
     * @param kind    取得区分（{@code C} / {@code D} / {@code E}）
     * @param heading 入力した語の見出し語（表記の正）
     * @param reading 入力した語の読み（読みの正）
     */
    public static ProblemResult parseProblems(String body, String kind, String heading, String reading) {
        // 取得区分 → そのバッチが作る種別（順番も固定する）
        List<String> expectedTypes = switch (kind == null ? "" : kind) {
            case "C" -> List.of("C1_READING", "C2_KANJI");
            case "D" -> List.of("D_CONTEXT_MEANING");
            case "E" -> List.of("E_KANJI_USAGE");
            default -> null;
        };
        if (expectedTypes == null) {
            return ProblemResult.failure("UNKNOWN_KIND", "取得区分が不正です: " + kind);
        }
        if (body == null || body.isBlank()) {
            return ProblemResult.failure("EMPTY_RESPONSE", "AI の応答が空です。");
        }
        List<JapaneseWordAiDtos.Problem> sources;
        try {
            sources = sourcesOf(body, kind);
        } catch (Exception cause) {
            return ProblemResult.failure("INVALID_JSON", "AI の応答が JSON ではありません。");
        }
        if (sources.isEmpty()) {
            return ProblemResult.failure("MISSING_PROBLEMS", "AI の応答に問題が入っていません。");
        }

        // 種別ごとに 1 件ずつ（C は C1 と C2 の 2 件）。重複や別の種別は受け取らない
        Map<String, JapaneseWordAiDtos.Problem> byType = new LinkedHashMap<>();
        for (JapaneseWordAiDtos.Problem source : sources) {
            if (source == null) {
                // AI が空の要素を混ぜても落とさない（揃っていない扱いにする）
                continue;
            }
            String declared = source.getProblemType() == null ? "" : source.getProblemType().trim();
            // D / E は problem が 1 件しか無いので、種別が空ならその区分の種別とみなす
            String type = declared.isEmpty() && expectedTypes.size() == 1 ? expectedTypes.get(0) : declared;
            if (!expectedTypes.contains(type) || byType.put(type, source) != null) {
                return ProblemResult.failure("MISSING_PROBLEMS",
                        "この取得区分（" + kind + "）で作る問題種別が揃っていません: " + declared);
            }
        }
        if (byType.size() != expectedTypes.size()) {
            return ProblemResult.failure("MISSING_PROBLEMS",
                    "作るはずの問題が揃っていません（" + byType.size() + " / " + expectedTypes.size() + "）。");
        }

        List<ParsedProblem> problems = new ArrayList<>();
        for (String type : expectedTypes) {
            JapaneseWordAiDtos.Problem source = byType.get(type);
            ParsedProblem parsed = problemOf(source, type, heading, reading);
            if (parsed == null) {
                return ProblemResult.failure("INVALID_CHOICES",
                        invalidReason(source, correctValueOf(type, source, heading, reading)));
            }
            // 問題番号は種別ごとに 1 から（1 種別につき 1 件なので常に 1）
            problems.add(parsed);
        }
        return ProblemResult.success(problems);
    }

    /** 応答を DTO へ読み、問題の一覧にする（C は配列、D / E は 1 件）。 */
    private static List<JapaneseWordAiDtos.Problem> sourcesOf(String body, String kind) throws Exception {
        if ("C".equals(kind)) {
            BatC42ResultDto dto = MAPPER.readValue(body, BatC42ResultDto.class);
            return dto == null ? List.of() : items(dto.getProblems());
        }
        JapaneseWordAiDtos.Problem problem;
        if ("D".equals(kind)) {
            BatC43ResultDto dto = MAPPER.readValue(body, BatC43ResultDto.class);
            problem = dto == null ? null : dto.getProblem();
        } else {
            BatC44ResultDto dto = MAPPER.readValue(body, BatC44ResultDto.class);
            problem = dto == null ? null : dto.getProblem();
        }
        return problem == null ? List.of() : List.of(problem);
    }

    /** 1 件を DB の行へ移す。選択肢の規則を満たさなければ null。 */
    private static ParsedProblem problemOf(JapaneseWordAiDtos.Problem problem, String type,
                                           String heading, String reading) {
        String correctValue = correctValueOf(type, problem, heading, reading);
        List<ParsedChoice> choices = markCorrect(problem.getOptions(), correctValue);
        if (choices == null) {
            return null;
        }
        String exampleJa = problem.getSentenceJapanese();
        String audioText = problem.getAudioText();
        if ("D_CONTEXT_MEANING".equals(type) && audioText == null) {
            // D は文脈の文を読み上げる（AI が音声テキストを書かなければ例文を使う）
            audioText = exampleJa;
        }
        String difficulty = problem.getDifficulty();
        if (difficulty == null) {
            difficulty = "NORMAL";
        }
        // C は入力の語についての問題なので、表記と読みも入力の語を正とする
        boolean aboutInputWord = "C1_READING".equals(type) || "C2_KANJI".equals(type);
        return new ParsedProblem(
                type,
                1,
                problem.getQuestionJapanese(),
                problem.getQuestionChinese(),
                aboutInputWord ? heading : problem.getTargetHeading(),
                aboutInputWord ? orElse(reading, problem.getTargetReading()) : problem.getTargetReading(),
                exampleJa,
                problem.getSentenceReading(),
                audioText,
                correctValue,
                problem.getCorrectNote(),
                problem.getExplanationJapanese(),
                problem.getExplanationChinese(),
                difficulty,
                // 人が後から追えるように、その問題ぶんの DTO を残す
                json(problem),
                choices);
    }

    /**
     * その問題の正解の値。
     *
     * <p><b>入力の語が正</b>: C1 は入力の読み（表記 → 読みを問う）、C2 は入力の表記
     * （読み → 表記を問う）。AI が書いた {@code correctValue} は使わない。D / E は AI の値。</p>
     *
     * <p>ただし <b>入力の読みがまだ空の語</b>（画面から読みを持たずに登録した直後）だけは、
     * AI が書いた読みを正解に使う。詳細の取得（batC41）で読みは書き戻されるが、
     * それより先に C を走らせても「正解が無い」で全件失敗させないため。</p>
     */
    private static String correctValueOf(String type, JapaneseWordAiDtos.Problem problem,
                                         String heading, String reading) {
        return switch (type) {
            case "C1_READING" -> orElse(reading, problem.getTargetReading());
            case "C2_KANJI" -> heading;
            default -> problem.getCorrectValue();
        };
    }

    /** 値があればその値、空なら代わり（まだ読みが無い語の受け渡しに使う）。 */
    private static String orElse(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback == null || fallback.isBlank() ? value : fallback;
    }

    /**
     * 正解の行に印を付け、誤答だけに区分を残す。
     *
     * <p><b>プールの規則</b>（切片6）: 正解 1 件 ＋ 誤答 4〜6 件（合計 5〜7 件）・値は一意・
     * 正解はちょうど 1 件。満たさなければ null（整題不合格＝1 行も書かない）。</p>
     */
    private static List<ParsedChoice> markCorrect(List<JapaneseWordAiDtos.ProblemChoice> options,
                                                  String correctValue) {
        if (!isPoolSize(options)) {
            return null;
        }
        List<ParsedChoice> choices = new ArrayList<>();
        Set<String> values = new LinkedHashSet<>();
        boolean duplicated = false;
        int correctCount = 0;
        int order = 0;
        for (JapaneseWordAiDtos.ProblemChoice option : options) {
            order += 1;
            String value = option.getValue();
            if (!values.add(value)) {
                duplicated = true;
            }
            boolean isCorrect = value != null && value.equals(correctValue);
            if (isCorrect) {
                correctCount += 1;
            }
            choices.add(new ParsedChoice(
                    order,
                    value,
                    option.getReading(),
                    isCorrect,
                    // 正解の行は 誤答区分 を持たない（2.0 の実データと同じ）
                    isCorrect ? null : option.getWrongType(),
                    // DTO の選択肢は日本語の説明を持たない（画面は中国語の説明を出す）
                    null,
                    option.getExplanationChinese()));
        }
        if (duplicated || correctCount != REQUIRED_CORRECT_CHOICES) {
            return null;
        }
        return choices;
    }

    /** 選択肢がプールの件数（合計 5〜7 件 ＝ 正解 1 ＋ 誤答 4〜6）か。 */
    private static boolean isPoolSize(List<JapaneseWordAiDtos.ProblemChoice> options) {
        int size = items(options).size();
        return size >= MIN_CHOICES && size <= MAX_CHOICES;
    }

    /** 失敗の理由（人が直せるように、どの規則で落ちたかを書く）。 */
    private static String invalidReason(JapaneseWordAiDtos.Problem problem, String correctValue) {
        List<JapaneseWordAiDtos.ProblemChoice> options = problem.getOptions();
        if (!isPoolSize(options)) {
            return "選択肢が " + MIN_CHOICES + "〜" + MAX_CHOICES + " 件ではありません（"
                    + items(options).size() + " 件）。";
        }
        Set<String> values = new LinkedHashSet<>();
        boolean duplicated = false;
        int correct = 0;
        for (JapaneseWordAiDtos.ProblemChoice option : options) {
            String value = option.getValue();
            if (!values.add(value)) {
                duplicated = true;
            }
            if (value != null && value.equals(correctValue)) {
                correct += 1;
            }
        }
        // 件数（5〜7）はここまでで満たしている。誤答は必ず 4 件以上ある（MIN_CHOICES の説明を見よ）
        if (duplicated) {
            return "選択肢の値が重複しています。";
        }
        // 「正解の数」は重複より後に見る（同じ値が 2 行あっても、まず「重複」と伝えたほうが直しやすい）
        return "正解がちょうど " + REQUIRED_CORRECT_CHOICES + " 件ではありません（" + correct + " 件）。";
    }

    /** 値を JSON にする（DTO は単純な入れ子なので実際には失敗しない）。 */
    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception cause) {
            return null;
        }
    }

    /** null の配列は空として扱う（AI が配列を返さなくても落とさない）。 */
    private static <T> List<T> items(List<T> source) {
        return source == null ? List.of() : source;
    }
}
