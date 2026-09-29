package com.study21.admin.japanesewordai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.study21.common.core.japanese.JpnWordDetailChildren;
import com.study21.common.core.japanese.JpnWordDetailEntity;
import com.study21.common.core.japanese.JpnWordDetailJson;

import java.util.ArrayList;
import java.util.List;

/**
 * AI が返した詳細（{@code 詳細JSON} と同じ形）を、<b>新しい版の 1 組</b>
 * （版のヘッダ ＋ 11 の子テーブルの行）へ崩す純関数。AI も DB も触らない。
 *
 * <p>キー名の基準は {@link JapaneseWordAiDtoMapper#parseDetail(String)} が出す詳細。
 * あちらが「AI の DTO → 詳細の形」、こちらが「詳細の形 → 表の列」で、
 * {@code JapaneseWordAiDtoMapper} と対になる（書きと読みでキーを二重に決めない）。</p>
 *
 * <p><strong>人の行を守る並び</strong>: AI の行の {@code 表示順} は
 * {@code 複製した人の行の最大 + 1} から始める（引数 {@code maxKeptOrderNo}）。
 * 人が画面で入れた行（{@code 登録元コード='APP'} / {@code 手修正フラグ=true}）は
 * {@link JapaneseWordAiDetailWriter} が先に新しい版へ複製しているので、
 * <b>AI の行はそのうしろに付く</b>。
 * 規則（1 行で言うと）: <b>「人が入れた行が先、AI の新しい行が後ろ。表示順は 1 から連番」</b>。</p>
 */
public final class JapaneseWordAiDetailComposer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** AI が作った内容の出所（人が作った行は 'APP'）。 */
    private static final String SOURCE_BATCH = "BATCH";

    private JapaneseWordAiDetailComposer() {
    }

    /**
     * 新しい版の 1 組。
     *
     * @param header   版のヘッダ（{@code 詳細ID} は INSERT の採番で入る）
     * @param children 11 の子テーブルの行（AI の新しい内容。表示順は人が入れた行の後ろ）
     */
    public record NewVersion(JpnWordDetailEntity header, JpnWordDetailChildren.Rows children) {
    }

    /**
     * 詳細の JSON を新しい版へ崩す。
     *
     * @param detailJson      AI が返した詳細（{@code 詳細JSON} と同じ形の JSON 文字列）
     * @param originVersion   基にする版（今の有効版。無ければ null）。{@code 元詳細ID} に使う
     * @param generationId    今回の AI 生成（{@code JPN_AI生成履歴情報.生成ID}）
     * @param aiProvider      この版を作った AI
     * @param aiModel         この版を作ったモデル
     * @param maxKeptOrderNo  複製した人の行の {@code 表示順} の最大（AI の行はこの次から）
     */
    public static NewVersion compose(String detailJson, JpnWordDetailEntity originVersion,
                                     Long generationId, String aiProvider, String aiModel,
                                     int maxKeptOrderNo) {
        JsonNode detail = detailNode(detailJson);
        JpnWordDetailEntity header = header(originVersion, generationId, aiProvider, aiModel, detail, maxKeptOrderNo);
        int start = Math.max(0, maxKeptOrderNo) + 1;

        return new NewVersion(header, new JpnWordDetailChildren.Rows(
                senses(detail, start),
                examples(detail, start),
                patterns(detail, start),
                dialogs(detail, start),
                synonyms(detail, start),
                cautions(detail, start),
                collocations(detail, start),
                relatedWords(detail, start),
                usageNotes(detail, start),
                practices(detail, start)));
    }

    /** 発音の読み（AI が書いた値）。読みを持たずに登録された語へ書き戻すときに使う。 */
    public static String pronunciationReading(String pronunciationJson) {
        JsonNode pronunciation = object(pronunciationJson);
        String reading = pronunciation == null ? null : text(pronunciation, "reading");
        return reading == null || reading.isBlank() ? null : reading.trim();
    }

    /* ------------------------------------------------------------ 版のヘッダ */

    private static JpnWordDetailEntity header(JpnWordDetailEntity originVersion, Long generationId,
                                              String aiProvider, String aiModel, JsonNode detail,
                                              int maxKeptOrderNo) {
        JpnWordDetailEntity header = new JpnWordDetailEntity();
        // 語は基にした版から引き継ぐ（最初の版は基にする版が無いので呼び出し側が入れる）
        header.setWordId(originVersion == null ? null : originVersion.getWordId());
        header.setStateCode("ACTIVE");
        header.setOriginDetailId(originVersion == null ? null : originVersion.getDetailId());
        header.setGenerationId(generationId);
        header.setAiProvider(aiProvider);
        header.setAiModel(aiModel);
        header.setFetchedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        // この版に人の編集が含まれるか。AI の行は BATCH だが、**人が入れた行を複製した**なら
        // この版にも人の内容が入っているので true にする（表示順は 1 起番なので、複製した行が
        // あれば最大は 1 以上）
        header.setManualCorrected(maxKeptOrderNo > 0);

        if (detail == null) {
            return header;
        }
        header.setCoreMeaning(text(detail, "coreMeaning"));
        header.setDescriptionJa(text(detail, "descriptionJa"));
        header.setDescriptionZh(text(detail, "descriptionZh"));
        header.setPartOfSpeech(text(detail, "partOfSpeech"));
        // 詳細のキーは jlptLevel（AI の DTO は jlpt。parseDetail が写し替えている）
        header.setJlptLevel(text(detail, "jlptLevel"));
        header.setConjugation(text(detail, "conjugation"));
        header.setTransitivity(text(detail, "transitivity"));
        header.setImportance(integer(detail, "importance"));

        JsonNode memoryHint = object(detail.path("memoryHint"));
        header.setMemoryHint(memoryHint == null ? null : text(memoryHint, "hint"));
        header.setMemoryHintBasis(memoryHint == null ? null : text(memoryHint, "basis"));

        // 小構造は列が分かれているので JSONB の文字列として持つ
        header.setPronunciationJson(json(detail.path("pronunciation")));
        header.setConjugationsJson(json(detail.path("conjugations")));
        header.setTransitivityPairJson(json(detail.path("transitivityPair")));
        header.setStructuredJson(json(detail.path("structured")));
        return header;
    }

    /* ------------------------------------------------------- 段落（子テーブル） */

    private static List<JpnWordDetailChildren.Sense> senses(JsonNode detail, int start) {
        List<JpnWordDetailChildren.Sense> rows = new ArrayList<>();
        for (JsonNode node : items(detail, "senses")) {
            JpnWordDetailChildren.Sense row = new JpnWordDetailChildren.Sense();
            row.setOrderNo(next(rows.size(), start));
            // 例文と使用場面が語義番号で結び付くので、無ければ 1 から振る（組み立てと同じ規則）
            int number = integer(node, "number") == null ? rows.size() + 1 : integer(node, "number");
            row.setSenseNumber(number > 0 ? number : rows.size() + 1);
            row.setJapanese(text(node, "japanese"));
            row.setChinese(text(node, "chinese"));
            row.setContext(text(node, "context"));
            row.setStyle(text(node, "style"));
            row.setNoteJapanese(text(node, "noteJapanese"));
            row.setNoteChinese(text(node, "noteChinese"));
            row.setManualCorrected(false);
            row.setSourceCode(SOURCE_BATCH);
            rows.add(row);
        }
        return rows;
    }

    private static List<JpnWordDetailChildren.Example> examples(JsonNode detail, int start) {
        List<JpnWordDetailChildren.Example> rows = new ArrayList<>();
        for (JsonNode node : items(detail, "examples")) {
            JpnWordDetailChildren.Example row = new JpnWordDetailChildren.Example();
            row.setOrderNo(next(rows.size(), start));
            row.setJapanese(text(node, "japanese"));
            row.setReading(text(node, "reading"));
            row.setChinese(text(node, "chinese"));
            row.setSenseNumber(integer(node, "senseNumber"));
            row.setSource(text(node, "source"));
            // 列の CHECK は BASIC / APPLIED だけ。それ以外（AI の書き間違い）は入れない
            row.setLevel(level(text(node, "level")));
            row.setManualCorrected(false);
            row.setSourceCode(SOURCE_BATCH);
            rows.add(row);
        }
        return rows;
    }

    private static List<JpnWordDetailChildren.Pattern> patterns(JsonNode detail, int start) {
        List<JpnWordDetailChildren.Pattern> rows = new ArrayList<>();
        for (JsonNode node : items(detail, "patterns")) {
            JpnWordDetailChildren.Pattern row = new JpnWordDetailChildren.Pattern();
            row.setOrderNo(next(rows.size(), start));
            row.setPattern(text(node, "pattern"));
            row.setReading(text(node, "reading"));
            row.setChinese(text(node, "chinese"));
            row.setExample(text(node, "example"));
            row.setExampleChinese(text(node, "exampleChinese"));
            row.setManualCorrected(false);
            row.setSourceCode(SOURCE_BATCH);
            rows.add(row);
        }
        return rows;
    }

    private static List<JpnWordDetailChildren.Dialog> dialogs(JsonNode detail, int start) {
        List<JpnWordDetailChildren.Dialog> rows = new ArrayList<>();
        for (JsonNode node : items(detail, "dialogs")) {
            JpnWordDetailChildren.Dialog row = new JpnWordDetailChildren.Dialog();
            row.setOrderNo(next(rows.size(), start));
            row.setScene(text(node, "scene"));
            row.setManualCorrected(false);
            row.setSourceCode(SOURCE_BATCH);
            // 発言も同じ規則（表示順は人が入れた行の後ろから）
            List<JpnWordDetailChildren.DialogLine> lines = new ArrayList<>();
            for (JsonNode lineNode : items(node, "lines")) {
                JpnWordDetailChildren.DialogLine line = new JpnWordDetailChildren.DialogLine();
                line.setOrderNo(next(lines.size(), start));
                line.setSpeaker(text(lineNode, "speaker"));
                line.setJapanese(text(lineNode, "japanese"));
                line.setChinese(text(lineNode, "chinese"));
                line.setManualCorrected(false);
                line.setSourceCode(SOURCE_BATCH);
                lines.add(line);
            }
            row.setLines(lines);
            rows.add(row);
        }
        return rows;
    }

    private static List<JpnWordDetailChildren.Synonym> synonyms(JsonNode detail, int start) {
        List<JpnWordDetailChildren.Synonym> rows = new ArrayList<>();
        for (JsonNode node : items(detail, "synonyms")) {
            JpnWordDetailChildren.Synonym row = new JpnWordDetailChildren.Synonym();
            row.setOrderNo(next(rows.size(), start));
            row.setHeading(text(node, "heading"));
            row.setReading(text(node, "reading"));
            row.setChinese(text(node, "chinese"));
            row.setShared(text(node, "shared"));
            row.setDifference(text(node, "difference"));
            row.setScene(text(node, "scene"));
            row.setManualCorrected(false);
            row.setSourceCode(SOURCE_BATCH);
            rows.add(row);
        }
        return rows;
    }

    private static List<JpnWordDetailChildren.Caution> cautions(JsonNode detail, int start) {
        List<JpnWordDetailChildren.Caution> rows = new ArrayList<>();
        for (JsonNode node : items(detail, "cautions")) {
            JpnWordDetailChildren.Caution row = new JpnWordDetailChildren.Caution();
            row.setOrderNo(next(rows.size(), start));
            // 列の CHECK は 4 通りだけ。それ以外は入れない
            row.setKind(cautionKind(text(node, "kind")));
            row.setTitle(text(node, "title"));
            row.setWrong(text(node, "wrong"));
            row.setCorrect(text(node, "correct"));
            row.setReason(text(node, "reason"));
            row.setManualCorrected(false);
            row.setSourceCode(SOURCE_BATCH);
            rows.add(row);
        }
        return rows;
    }

    private static List<JpnWordDetailChildren.Collocation> collocations(JsonNode detail, int start) {
        List<JpnWordDetailChildren.Collocation> rows = new ArrayList<>();
        for (JsonNode node : items(detail, "collocations")) {
            JpnWordDetailChildren.Collocation row = new JpnWordDetailChildren.Collocation();
            row.setOrderNo(next(rows.size(), start));
            row.setExpression(text(node, "expression"));
            row.setReading(text(node, "reading"));
            row.setChinese(text(node, "chinese"));
            row.setUsage(text(node, "usage"));
            row.setExampleJapanese(text(node, "exampleJapanese"));
            row.setExampleChinese(text(node, "exampleChinese"));
            row.setManualCorrected(false);
            row.setSourceCode(SOURCE_BATCH);
            rows.add(row);
        }
        return rows;
    }

    private static List<JpnWordDetailChildren.RelatedWord> relatedWords(JsonNode detail, int start) {
        List<JpnWordDetailChildren.RelatedWord> rows = new ArrayList<>();
        for (JsonNode node : items(detail, "relatedWords")) {
            JpnWordDetailChildren.RelatedWord row = new JpnWordDetailChildren.RelatedWord();
            row.setOrderNo(next(rows.size(), start));
            // 列の CHECK は 4 通りだけ。それ以外は入れない
            row.setRelation(relation(text(node, "relation")));
            row.setHeading(text(node, "heading"));
            row.setReading(text(node, "reading"));
            row.setChinese(text(node, "chinese"));
            row.setManualCorrected(false);
            row.setSourceCode(SOURCE_BATCH);
            rows.add(row);
        }
        return rows;
    }

    private static List<JpnWordDetailChildren.UsageNote> usageNotes(JsonNode detail, int start) {
        List<JpnWordDetailChildren.UsageNote> rows = new ArrayList<>();
        for (JsonNode node : items(detail, "usageNotes")) {
            JpnWordDetailChildren.UsageNote row = new JpnWordDetailChildren.UsageNote();
            row.setOrderNo(next(rows.size(), start));
            row.setRegister(text(node, "register"));
            row.setPoliteness(text(node, "politeness"));
            row.setAudience(text(node, "audience"));
            row.setNote(text(node, "note"));
            row.setSenseNumber(integer(node, "senseNumber"));
            row.setManualCorrected(false);
            row.setSourceCode(SOURCE_BATCH);
            rows.add(row);
        }
        return rows;
    }

    private static List<JpnWordDetailChildren.Practice> practices(JsonNode detail, int start) {
        List<JpnWordDetailChildren.Practice> rows = new ArrayList<>();
        for (JsonNode node : items(detail, "practices")) {
            JpnWordDetailChildren.Practice row = new JpnWordDetailChildren.Practice();
            row.setOrderNo(next(rows.size(), start));
            row.setKind(practiceKind(text(node, "kind")));
            row.setQuestion(text(node, "question"));
            row.setQuestionChinese(text(node, "questionChinese"));
            // 選択肢は文字列の配列（列は JSONB）
            row.setChoicesJson(stringArrayJson(node.path("choices")));
            row.setFreeWriting(node.path("freeWriting").asBoolean(false));
            row.setAnswer(text(node, "answer"));
            row.setExplanation(text(node, "explanation"));
            row.setManualCorrected(false);
            row.setSourceCode(SOURCE_BATCH);
            rows.add(row);
        }
        return rows;
    }

    /* ------------------------------------------------------------------ 小物 */

    private static int next(int index, int start) {
        return start + index;
    }

    /*
      列の CHECK に合う値だけを残す 4 つの正規化は common-core の JpnWordDetailJson と共通
      （画面の編集を版にする側＝JpnWordDetailEditorComposer と同じ規則にする）。
      値が無い・合わないものは null（列に入れない）。
    */

    /** レベルは BASIC / APPLIED だけ（列の CHECK）。 */
    private static String level(String value) {
        return JpnWordDetailJson.level(value);
    }

    private static String cautionKind(String value) {
        return JpnWordDetailJson.cautionKind(value);
    }

    private static String relation(String value) {
        return JpnWordDetailJson.relation(value);
    }

    private static String practiceKind(String value) {
        return JpnWordDetailJson.practiceKind(value);
    }

    /**
     * 詳細の本体。{@code JapaneseWordAiDtoMapper.parseDetail} の成果は
     * <b>詳細そのもの</b>（{@code detail} の入れ子は無い）だが、AI の生の応答
     * （{@code {"detail": {...}}}）をそのまま渡されても読めるようにする。
     */
    private static JsonNode detailNode(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(detailJson);
            if (root == null || !root.isObject() || root.isEmpty()) {
                return null;
            }
            JsonNode nested = root.path("detail");
            return nested.isObject() ? nested : root;
        } catch (Exception cause) {
            return null;
        }
    }

    private static List<JsonNode> items(JsonNode detail, String field) {
        if (detail == null) {
            return List.of();
        }
        JsonNode node = detail.path(field);
        if (!(node instanceof ArrayNode array)) {
            return List.of();
        }
        List<JsonNode> list = new ArrayList<>(array.size());
        array.forEach(list::add);
        return list;
    }

    private static JsonNode object(JsonNode node) {
        return node != null && node.isObject() && !node.isEmpty() ? node : null;
    }

    private static JsonNode object(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return object(MAPPER.readTree(json));
        } catch (Exception cause) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asText();
    }

    private static Integer integer(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    /** 小構造（オブジェクト・配列）を JSONB の列に入れる文字列にする。 */
    private static String json(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.toString();
    }

    /** 文字列の配列を JSON の文字列にする（列は JSONB）。 */
    private static String stringArrayJson(JsonNode node) {
        ArrayNode array = MAPPER.createArrayNode();
        if (node instanceof ArrayNode source) {
            for (JsonNode item : source) {
                if (item.isTextual()) {
                    array.add(item.asText());
                }
            }
        }
        return array.toString();
    }
}
