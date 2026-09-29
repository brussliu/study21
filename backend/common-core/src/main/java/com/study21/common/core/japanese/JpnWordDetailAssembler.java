package com.study21.common.core.japanese;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 詳細の「版」（JPN_単語詳細情報 の 1 行）＋ 11 の子テーブルの行を、
 * <strong>旧 {@code 詳細JSON} とまったく同じ形の detail オブジェクト</strong>へ組み立てる純関数。
 *
 * <p>キー名の唯一の基準は admin-api の
 * {@code JapaneseWordAiDtoMapper.toDetailJson}（AI の DTO から {@code 詳細JSON} を作る関数）。
 * あちらが「書き」、こちらが「読み」で、<b>同じキー・同じ形</b>にする。学習画面は
 * この形だけを知っていればよい（キーを 2 か所で決めない）。</p>
 *
 * <p><strong>Spring に依存しない</strong>ので、user-api も admin-api も同じ組み立てを使う
 * （AI の入力に渡す既存詳細も、画面が読む詳細も、必ず同じ形になる）。</p>
 *
 * <p>守る規則:</p>
 * <ul>
 *   <li>語レベルの値は版の行から、段落は子テーブルの行から取る</li>
 *   <li>並びは SQL（{@code 表示順, <段落>ID}）が決める。ここでは並べ替えない</li>
 *   <li>{@code pronunciation}（単数）と {@code pronunciations}（0/1 件の配列）の両方を出す。
 *       {@code pronunciations[0].moraCount} は<b>読みの文字数</b>、音声の URL と提供元は
 *       <b>常に null</b>（別の仕組み＝音声キャッシュが持つ。無い値を捏造しない）</li>
 *   <li>{@code structured} は {@code 元レスポンスJSON}（AI の生の応答）</li>
 *   <li>{@code 手修正フラグ} は版の行と子の行の<b>どちらかが true なら true</b>
 *       （人が直した行がある版は「手を入れた版」）</li>
 * </ul>
 */
public final class JpnWordDetailAssembler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JpnWordDetailAssembler() {
    }

    /**
     * 版の行と子の行を detail オブジェクトへ組み立てる。
     *
     * @param header   有効版の行（{@code 状態コード='ACTIVE'}）。null なら null を返す
     *                  （詳細がまだ無い語。画面は詳細の節を出さない）
     * @param children 11 の子テーブルの行（{@link JpnWordDetailChildren.Rows}）。null は空扱い
     * @return 旧 {@code 詳細JSON} と同じキーを持つオブジェクト（値に null を入れられるので
     *         {@code Map.of} ではなく {@code LinkedHashMap}）
     */
    public static Map<String, Object> assemble(JpnWordDetailEntity header,
                                               JpnWordDetailChildren.Rows children) {
        if (header == null) {
            return null;
        }
        JpnWordDetailChildren.Rows rows = children == null ? JpnWordDetailChildren.Rows.empty() : children;

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("coreMeaning", header.getCoreMeaning());
        // 既存画面（wordMapper.ts）は核心の意味を chineseMeaning で読む。値は 1 つなので
        // 両方の名前で同じ値を入れる（書き側の toDetailJson と同じ規則）
        json.put("chineseMeaning", header.getCoreMeaning());
        json.put("descriptionJa", header.getDescriptionJa());
        json.put("descriptionZh", header.getDescriptionZh());
        json.put("partOfSpeech", header.getPartOfSpeech());
        json.put("jlptLevel", header.getJlptLevel());
        json.put("conjugation", header.getConjugation());
        json.put("transitivity", header.getTransitivity());
        json.put("importance", header.getImportance());
        json.put("manuallyCorrected", isManuallyCorrected(header, rows));

        json.put("senses", senses(rows.senses()));
        json.put("examples", examples(rows.examples()));
        json.put("patterns", patterns(rows.patterns()));
        json.put("dialogs", dialogs(rows.dialogs()));
        json.put("synonyms", synonyms(rows.synonyms()));
        json.put("cautions", cautions(rows.cautions()));
        json.put("conjugations", conjugations(header.getConjugationsJson()));
        json.put("transitivityPair", transitivityPair(header.getTransitivityPairJson()));
        json.put("pronunciation", pronunciation(header.getPronunciationJson()));
        json.put("pronunciations", pronunciations(header.getPronunciationJson()));
        json.put("collocations", collocations(rows.collocations()));
        json.put("relatedWords", relatedWords(rows.relatedWords()));
        json.put("usageNotes", usageNotes(rows.usageNotes()));
        json.put("memoryHint", memoryHint(header));
        json.put("practices", practices(rows.practices()));

        // AI の生の応答（ルートの JSON）。あとから組み立て結果と突き合わせるために残す
        json.put("structured", structured(header.getStructuredJson()));
        return json;
    }

    /** 詳細の版番号（楽観的ロックと「取得状態」に使う）。詳細が無ければ 0。 */
    public static int contentVersionOf(JpnWordDetailEntity header) {
        return header == null || header.getContentVersion() == null ? 0 : header.getContentVersion();
    }

    /**
     * 人が手を入れた版か。
     *
     * <p>版の行の {@code 手修正フラグ} だけでなく、子の行の {@code 手修正フラグ} も見る
     * （段落だけを人が直した版は、版の行のフラグが立っていないことがある）。</p>
     */
    private static boolean isManuallyCorrected(JpnWordDetailEntity header, JpnWordDetailChildren.Rows rows) {
        if (Boolean.TRUE.equals(header.getManualCorrected())) {
            return true;
        }
        for (JpnWordDetailChildren.Sense row : nullToEmpty(rows.senses())) {
            if (Boolean.TRUE.equals(row.getManualCorrected())) {
                return true;
            }
        }
        for (JpnWordDetailChildren.Example row : nullToEmpty(rows.examples())) {
            if (Boolean.TRUE.equals(row.getManualCorrected())) {
                return true;
            }
        }
        for (JpnWordDetailChildren.Pattern row : nullToEmpty(rows.patterns())) {
            if (Boolean.TRUE.equals(row.getManualCorrected())) {
                return true;
            }
        }
        for (JpnWordDetailChildren.Dialog row : nullToEmpty(rows.dialogs())) {
            if (Boolean.TRUE.equals(row.getManualCorrected())) {
                return true;
            }
            for (JpnWordDetailChildren.DialogLine line : nullToEmpty(row.getLines())) {
                if (Boolean.TRUE.equals(line.getManualCorrected())) {
                    return true;
                }
            }
        }
        for (JpnWordDetailChildren.Synonym row : nullToEmpty(rows.synonyms())) {
            if (Boolean.TRUE.equals(row.getManualCorrected())) {
                return true;
            }
        }
        for (JpnWordDetailChildren.Caution row : nullToEmpty(rows.cautions())) {
            if (Boolean.TRUE.equals(row.getManualCorrected())) {
                return true;
            }
        }
        for (JpnWordDetailChildren.Collocation row : nullToEmpty(rows.collocations())) {
            if (Boolean.TRUE.equals(row.getManualCorrected())) {
                return true;
            }
        }
        for (JpnWordDetailChildren.RelatedWord row : nullToEmpty(rows.relatedWords())) {
            if (Boolean.TRUE.equals(row.getManualCorrected())) {
                return true;
            }
        }
        for (JpnWordDetailChildren.UsageNote row : nullToEmpty(rows.usageNotes())) {
            if (Boolean.TRUE.equals(row.getManualCorrected())) {
                return true;
            }
        }
        for (JpnWordDetailChildren.Practice row : nullToEmpty(rows.practices())) {
            if (Boolean.TRUE.equals(row.getManualCorrected())) {
                return true;
            }
        }
        return false;
    }

    /* ------------------------------------------------------------ 段落（子テーブル） */

    private static List<Map<String, Object>> senses(List<JpnWordDetailChildren.Sense> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        int index = 0;
        for (JpnWordDetailChildren.Sense row : nullToEmpty(source)) {
            index += 1;
            Map<String, Object> item = new LinkedHashMap<>();
            // 例文と使用場面が語義番号で結び付くので、無ければ 1 から振る（書き側と同じ）
            item.put("number", row.getSenseNumber() != null && row.getSenseNumber() > 0
                    ? row.getSenseNumber() : index);
            item.put("japanese", row.getJapanese());
            item.put("chinese", row.getChinese());
            item.put("context", row.getContext());
            item.put("style", row.getStyle());
            item.put("noteJapanese", row.getNoteJapanese());
            item.put("noteChinese", row.getNoteChinese());
            list.add(item);
        }
        return list;
    }

    private static List<Map<String, Object>> examples(List<JpnWordDetailChildren.Example> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JpnWordDetailChildren.Example row : nullToEmpty(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("japanese", row.getJapanese());
            item.put("reading", row.getReading());
            item.put("chinese", row.getChinese());
            item.put("senseNumber", row.getSenseNumber());
            item.put("source", row.getSource());
            item.put("level", row.getLevel());
            list.add(item);
        }
        return list;
    }

    private static List<Map<String, Object>> patterns(List<JpnWordDetailChildren.Pattern> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JpnWordDetailChildren.Pattern row : nullToEmpty(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("pattern", row.getPattern());
            item.put("reading", row.getReading());
            item.put("chinese", row.getChinese());
            item.put("example", row.getExample());
            item.put("exampleChinese", row.getExampleChinese());
            list.add(item);
        }
        return list;
    }

    /** 会話は 2 層（{@code [{scene, lines:[{speaker, japanese, chinese}]}]}）。 */
    private static List<Map<String, Object>> dialogs(List<JpnWordDetailChildren.Dialog> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JpnWordDetailChildren.Dialog row : nullToEmpty(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("scene", row.getScene());
            List<Map<String, Object>> lines = new ArrayList<>();
            for (JpnWordDetailChildren.DialogLine line : nullToEmpty(row.getLines())) {
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

    private static List<Map<String, Object>> synonyms(List<JpnWordDetailChildren.Synonym> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JpnWordDetailChildren.Synonym row : nullToEmpty(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("heading", row.getHeading());
            item.put("reading", row.getReading());
            item.put("chinese", row.getChinese());
            item.put("shared", row.getShared());
            item.put("difference", row.getDifference());
            item.put("scene", row.getScene());
            list.add(item);
        }
        return list;
    }

    private static List<Map<String, Object>> cautions(List<JpnWordDetailChildren.Caution> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JpnWordDetailChildren.Caution row : nullToEmpty(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kind", row.getKind());
            item.put("title", row.getTitle());
            item.put("wrong", row.getWrong());
            item.put("correct", row.getCorrect());
            item.put("reason", row.getReason());
            list.add(item);
        }
        return list;
    }

    private static List<Map<String, Object>> collocations(List<JpnWordDetailChildren.Collocation> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JpnWordDetailChildren.Collocation row : nullToEmpty(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("expression", row.getExpression());
            item.put("reading", row.getReading());
            item.put("chinese", row.getChinese());
            item.put("usage", row.getUsage());
            item.put("exampleJapanese", row.getExampleJapanese());
            item.put("exampleChinese", row.getExampleChinese());
            list.add(item);
        }
        return list;
    }

    private static List<Map<String, Object>> relatedWords(List<JpnWordDetailChildren.RelatedWord> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JpnWordDetailChildren.RelatedWord row : nullToEmpty(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("relation", row.getRelation());
            item.put("heading", row.getHeading());
            item.put("reading", row.getReading());
            item.put("chinese", row.getChinese());
            list.add(item);
        }
        return list;
    }

    private static List<Map<String, Object>> usageNotes(List<JpnWordDetailChildren.UsageNote> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JpnWordDetailChildren.UsageNote row : nullToEmpty(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("register", row.getRegister());
            item.put("politeness", row.getPoliteness());
            item.put("audience", row.getAudience());
            item.put("note", row.getNote());
            item.put("senseNumber", row.getSenseNumber());
            list.add(item);
        }
        return list;
    }

    private static List<Map<String, Object>> practices(List<JpnWordDetailChildren.Practice> source) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JpnWordDetailChildren.Practice row : nullToEmpty(source)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kind", row.getKind());
            item.put("question", row.getQuestion());
            item.put("questionChinese", row.getQuestionChinese());
            // 自由造句では選択肢が空（画面は空配列で耐える）
            item.put("choices", readStringArray(row.getChoicesJson()));
            item.put("answer", row.getAnswer());
            item.put("explanation", row.getExplanation());
            item.put("freeWriting", Boolean.TRUE.equals(row.getFreeWriting()));
            list.add(item);
        }
        return list;
    }

    /* ------------------------------------------------- 語レベルの小構造（JSONB の列） */

    /** 活用形（配列）。{@code 活用形JSON} をそのまま使う（無ければ空配列）。 */
    private static List<Map<String, Object>> conjugations(String json) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode node : readArray(json)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("form", text(node, "form"));
            item.put("value", text(node, "value"));
            item.put("example", text(node, "example"));
            list.add(item);
        }
        return list;
    }

    /** 自他動詞の対応。当てはまらない語では null（画面は節ごと出さない）。 */
    private static Map<String, Object> transitivityPair(String json) {
        JsonNode node = readObject(json);
        if (node == null) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("intransitive", text(node, "intransitive"));
        item.put("transitive", text(node, "transitive"));
        item.put("particleNote", text(node, "particleNote"));
        item.put("intransitiveExample", text(node, "intransitiveExample"));
        item.put("transitiveExample", text(node, "transitiveExample"));
        return item;
    }

    /**
     * 発音（1 件）。読みが無い語では null。
     *
     * <p>{@code 発音JSON} は {@code NOT NULL DEFAULT '{}'} なので、中身が無いときは
     * <b>空のオブジェクトではなく null</b> を返す（書き側の DTO が発音を持たないときに
     * null を入れていたのと同じ形にする）。</p>
     */
    private static Map<String, Object> pronunciation(String json) {
        JsonNode node = readObject(json);
        if (node == null || node.isEmpty()) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("reading", text(node, "reading"));
        // アクセントは未確認なら null（推測で埋めない）
        item.put("accentType", node.hasNonNull("accentType") ? node.path("accentType").asInt() : null);
        item.put("accentNotation", text(node, "accentNotation"));
        item.put("hint", text(node, "hint"));
        item.put("hasAudioSample", node.path("hasAudioSample").asBoolean(false));
        return item;
    }

    /**
     * 発音の配列（既存画面 {@code wordMapper.ts} が読む形）。0 件か 1 件。
     *
     * <p>{@code moraCount} は読みの文字数（分からなければ 0）。音声の URL と提供元は別の
     * 仕組みが持つので常に null。</p>
     */
    private static List<Map<String, Object>> pronunciations(String json) {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> single = pronunciation(json);
        if (single == null) {
            return list;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("reading", single.get("reading"));
        item.put("accentNotation", single.get("accentNotation"));
        item.put("accentType", single.get("accentType"));
        Object reading = single.get("reading");
        String value = reading == null ? null : String.valueOf(reading);
        item.put("moraCount", value == null || value.isBlank() ? 0 : value.length());
        item.put("audioUrl", null);
        item.put("audioProvider", null);
        list.add(item);
        return list;
    }

    /** 記憶のヒント。{@code 記憶ヒント} と {@code 記憶ヒント根拠} の両方が空なら null。 */
    private static Map<String, Object> memoryHint(JpnWordDetailEntity header) {
        String hint = header.getMemoryHint();
        String basis = header.getMemoryHintBasis();
        boolean blankHint = hint == null || hint.isBlank();
        boolean blankBasis = basis == null || basis.isBlank();
        if (blankHint && blankBasis) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("hint", hint);
        item.put("basis", basis);
        return item;
    }

    /**
     * AI の生の応答（{@code 元レスポンスJSON}）。人が編集した版は元の版の値を引き継ぐので、
     * どの版でも「どの応答から生まれたか」が分かる。読めなければ空のオブジェクト。
     */
    private static Object structured(String json) {
        if (json == null || json.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception cause) {
            return MAPPER.createObjectNode();
        }
    }

    /* ------------------------------------------------------------------ JSON の読み */

    /** オブジェクトの JSON を読む。null・空・配列・壊れた JSON は null（空オブジェクトも null）。 */
    private static JsonNode readObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            return node != null && node.isObject() && !node.isEmpty() ? node : null;
        } catch (Exception cause) {
            return null;
        }
    }

    private static List<JsonNode> readArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (!(node instanceof ArrayNode array)) {
                return List.of();
            }
            List<JsonNode> list = new ArrayList<>(array.size());
            array.forEach(list::add);
            return list;
        } catch (Exception cause) {
            return List.of();
        }
    }

    /** 文字列の配列（練習の選択肢）。文字列でない要素は落とす。 */
    private static List<String> readStringArray(String json) {
        List<String> list = new ArrayList<>();
        for (JsonNode node : readArray(json)) {
            if (node.isTextual()) {
                list.add(node.asText());
            }
        }
        return list;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asText();
    }

    private static <T> List<T> nullToEmpty(List<T> source) {
        return source == null ? List.of() : source;
    }
}
