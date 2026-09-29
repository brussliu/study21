package com.study21.common.core.japanese;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 画面の編集（{@code PUT /words/{wordId}/editor} が受ける詳細）を、
 * <b>新しい版の 1 組</b>（版のヘッダ ＋ 11 の子テーブルの行）へ崩す純関数。DB も Spring も触らない。
 *
 * <p>規則（設計: {@code 日本語勉強_再設計案_中文.md} §1.4 の「画面編集保存」）:</p>
 * <ul>
 *   <li>版は<b>積み上げる</b>。新しい版が {@code ACTIVE}、今の有効版は {@code ARCHIVED}
 *       （ARCHIVED にするのは呼ぶ側＝DB を触る側。部分 UNIQUE 索引の順番の話）</li>
 *   <li>{@code 元詳細ID}＝今の有効版、{@code 生成ID}＝NULL（AI ではなく人が作った版）、
 *       {@code 手修正フラグ}=true、{@code 登録元コード}='APP'</li>
 *   <li>{@code AIプロバイダ}／{@code AIモデル}／{@code 取得日時}／{@code 元レスポンスJSON} は
 *       元の版から引き継ぐ（どの応答から生まれた版かは編集しても変わらない）</li>
 *   <li>語レベルの値（核心意味・説明・発音・活用形・自他対応…）は<b>要求の値</b>。
 *       要求に無いキーは元の版の値（画面が一部だけ送っても消えない）</li>
 * </ul>
 *
 * <p><strong>行の「出所」の判定</strong>（このスライスの要）:</p>
 * <ol>
 *   <li>要求の段落の行を、元の版の行と<b>内容で突き合わせる</b>（表示順ではなく内容。表示順は
 *       画面が並べ替えるので、同じ内容の行が別の位置に来ることがある）</li>
 *   <li><b>まったく同じ行</b> → 元の行の出所をそのまま引き継ぐ。AI の行（{@code BATCH}）は
 *       {@code BATCH} のまま、人の行（{@code APP}）は {@code APP} のまま
 *       → 触っていない AI の行は、次の AI 取得で普段どおり置き換わる</li>
 *   <li><b>変わった行・新しく足した行</b> → {@code APP}・{@code 手修正フラグ}=true
 *       → 人が触った行は以後 AI に上書きされない</li>
 *   <li><b>元の版にあって要求に無い行</b> → 消す（複製しない。画面で削除した行は消えたまま）</li>
 * </ol>
 *
 * <p>「同じ行」の判定は段落ごとの内容の組で行う（例文なら 例文_日本語・例文読み・例文_中国語・
 * 語義番号・出典・レベル）。会話は 2 層なので、まず会話（場面＋発言の並び）で突き合わせ、
 * 同じ会話の中で発言を突き合わせる。</p>
 *
 * <p>値の読み方は {@link JpnWordDetailJson}（AI 取得の書き側と同じ）。キー名の基準は
 * {@link JpnWordDetailAssembler} が出す詳細（画面が読む形）。</p>
 */
public final class JpnWordDetailEditorComposer {

    /** AI が作った内容の出所（人が作った行は 'APP'）。 */
    private static final String SOURCE_BATCH = "BATCH";
    /** 人が画面で作った内容の出所。 */
    private static final String SOURCE_APP = "APP";

    private JpnWordDetailEditorComposer() {
    }

    /**
     * 新しい版の 1 組。
     *
     * @param header   版のヘッダ（{@code 詳細ID} は INSERT の採番で入る）
     * @param children 11 の子テーブルの行（{@code 表示順} は 1 から振り直したもの）
     * @param manual   この版に人の行があるか（版のヘッダの {@code 手修正フラグ} に入れる）
     */
    public record NewVersion(JpnWordDetailEntity header, JpnWordDetailChildren.Rows children, boolean manual) {
    }

    /**
     * 画面から来た詳細を新しい版へ崩す。
     *
     * @param requestDetail 画面が保存した詳細（旧 {@code 詳細JSON} と同じ形。null は空扱い）
     * @param originVersion 基にする版＝今の有効版。null なら最初の版（子テーブルは空）
     * @param originRows    今の有効版の 11 の子テーブルの行。null は空扱い
     * @param wordId        語の ID
     * @param accountId     保存した人（{@code 登録者アカウントID}）
     */
    public static NewVersion compose(Map<String, Object> requestDetail,
                                     JpnWordDetailEntity originVersion,
                                     JpnWordDetailChildren.Rows originRows,
                                     long wordId, long accountId) {
        JsonNode detail = JpnWordDetailJson.detail(requestDetail);
        JpnWordDetailChildren.Rows origin = originRows == null
                ? JpnWordDetailChildren.Rows.empty() : originRows;

        JpnWordDetailChildren.Rows children = new JpnWordDetailChildren.Rows(
                senses(detail, origin.senses()),
                examples(detail, origin.examples()),
                patterns(detail, origin.patterns()),
                dialogs(detail, origin.dialogs()),
                synonyms(detail, origin.synonyms()),
                cautions(detail, origin.cautions()),
                collocations(detail, origin.collocations()),
                relatedWords(detail, origin.relatedWords()),
                usageNotes(detail, origin.usageNotes()),
                practices(detail, origin.practices()));

        boolean manual = JpnWordDetailChildren.hasManualRows(children);
        return new NewVersion(header(detail, originVersion, wordId, accountId, manual), children, manual);
    }

    /* ------------------------------------------------------------ 版のヘッダ */

    private static JpnWordDetailEntity header(JsonNode detail, JpnWordDetailEntity origin,
                                              long wordId, long accountId, boolean manual) {
        JpnWordDetailEntity header = new JpnWordDetailEntity();
        header.setWordId(wordId);
        header.setStateCode("ACTIVE");
        header.setOriginDetailId(origin == null ? null : origin.getDetailId());
        // 人が作った版（AI の生成ではない）
        header.setGenerationId(null);
        header.setFetchedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        header.setManualCorrected(manual);
        header.setCreatedBy(accountId);
        if (origin != null) {
            // どの AI の応答から生まれた版かは、人が直しても変わらない
            header.setAiProvider(origin.getAiProvider());
            header.setAiModel(origin.getAiModel());
            header.setStructuredJson(origin.getStructuredJson());
        }

        header.setCoreMeaning(value(detail, origin == null ? null : origin.getCoreMeaning(), "coreMeaning"));
        header.setDescriptionJa(value(detail, origin == null ? null : origin.getDescriptionJa(), "descriptionJa"));
        header.setDescriptionZh(value(detail, origin == null ? null : origin.getDescriptionZh(), "descriptionZh"));
        header.setPartOfSpeech(value(detail, origin == null ? null : origin.getPartOfSpeech(), "partOfSpeech"));
        header.setJlptLevel(value(detail, origin == null ? null : origin.getJlptLevel(), "jlptLevel"));
        header.setConjugation(value(detail, origin == null ? null : origin.getConjugation(), "conjugation"));
        header.setTransitivity(value(detail, origin == null ? null : origin.getTransitivity(), "transitivity"));

        Integer importance = JpnWordDetailJson.integer(detail, "importance");
        header.setImportance(importance == null && origin != null ? origin.getImportance() : importance);

        // 記憶のヒントは 1 つのオブジェクト（hint と basis）にまとまっている
        JsonNode memoryHint = JpnWordDetailJson.object(detail == null ? null : detail.path("memoryHint"));
        header.setMemoryHint(memoryHint == null
                ? (origin == null ? null : origin.getMemoryHint())
                : JpnWordDetailJson.text(memoryHint, "hint"));
        header.setMemoryHintBasis(memoryHint == null
                ? (origin == null ? null : origin.getMemoryHintBasis())
                : JpnWordDetailJson.text(memoryHint, "basis"));

        // 小構造（JSONB の列）。書かなければ元の版の値をそのまま持ち越す
        header.setPronunciationJson(jsonOf(detail, "pronunciation",
                origin == null ? null : origin.getPronunciationJson()));
        header.setConjugationsJson(jsonOf(detail, "conjugations",
                origin == null ? null : origin.getConjugationsJson()));
        header.setTransitivityPairJson(jsonOf(detail, "transitivityPair",
                origin == null ? null : origin.getTransitivityPairJson()));
        return header;
    }

    /** 語レベルの文字列。要求にキーが無ければ元の版の値（null を書いても元の値で埋める）。 */
    private static String value(JsonNode detail, String origin, String field) {
        String requested = JpnWordDetailJson.text(detail, field);
        return requested == null || requested.isBlank() ? origin : requested;
    }

    /** 小構造（JSONB）。要求にそのキーがあれば要求の値、無ければ元の版の値。 */
    private static String jsonOf(JsonNode detail, String field, String origin) {
        if (!JpnWordDetailJson.has(detail, field)) {
            return origin;
        }
        return JpnWordDetailJson.json(detail.path(field));
    }

    /* ------------------------------------------------------------ 語義 */

    private static List<JpnWordDetailChildren.Sense> senses(JsonNode detail,
                                                            List<JpnWordDetailChildren.Sense> origin) {
        List<JpnWordDetailChildren.Sense> rows = new ArrayList<>();
        for (JsonNode node : JpnWordDetailJson.items(detail, "senses")) {
            JpnWordDetailChildren.Sense row = new JpnWordDetailChildren.Sense();
            row.setSenseNumber(number(node, rows.size() + 1));
            row.setJapanese(JpnWordDetailJson.text(node, "japanese"));
            row.setChinese(JpnWordDetailJson.text(node, "chinese"));
            row.setContext(JpnWordDetailJson.text(node, "context"));
            row.setStyle(JpnWordDetailJson.text(node, "style"));
            row.setNoteJapanese(JpnWordDetailJson.text(node, "noteJapanese"));
            row.setNoteChinese(JpnWordDetailJson.text(node, "noteChinese"));
            applyOrigin(row, find(origin, row, JpnWordDetailEditorComposer::matchSense), rows.size() + 1);
            rows.add(row);
        }
        return rows;
    }

    private static boolean matchSense(JpnWordDetailChildren.Sense left, JpnWordDetailChildren.Sense right) {
        return same(left.getSenseNumber(), right.getSenseNumber())
                && same(left.getJapanese(), right.getJapanese())
                && same(left.getChinese(), right.getChinese())
                && same(left.getContext(), right.getContext())
                && same(left.getStyle(), right.getStyle())
                && same(left.getNoteJapanese(), right.getNoteJapanese())
                && same(left.getNoteChinese(), right.getNoteChinese());
    }

    /* ------------------------------------------------------------ 例文 */

    private static List<JpnWordDetailChildren.Example> examples(JsonNode detail,
                                                                List<JpnWordDetailChildren.Example> origin) {
        List<JpnWordDetailChildren.Example> rows = new ArrayList<>();
        for (JsonNode node : JpnWordDetailJson.items(detail, "examples")) {
            JpnWordDetailChildren.Example row = new JpnWordDetailChildren.Example();
            row.setJapanese(JpnWordDetailJson.text(node, "japanese"));
            row.setReading(JpnWordDetailJson.text(node, "reading"));
            row.setChinese(JpnWordDetailJson.text(node, "chinese"));
            row.setSenseNumber(JpnWordDetailJson.integer(node, "senseNumber"));
            row.setSource(JpnWordDetailJson.text(node, "source"));
            // 列の CHECK は BASIC / APPLIED だけ
            row.setLevel(JpnWordDetailJson.level(JpnWordDetailJson.text(node, "level")));
            applyOrigin(row, find(origin, row, JpnWordDetailEditorComposer::matchExample), rows.size() + 1);
            rows.add(row);
        }
        return rows;
    }

    private static boolean matchExample(JpnWordDetailChildren.Example left, JpnWordDetailChildren.Example right) {
        return same(left.getJapanese(), right.getJapanese())
                && same(left.getReading(), right.getReading())
                && same(left.getChinese(), right.getChinese())
                && same(left.getSenseNumber(), right.getSenseNumber())
                && same(left.getSource(), right.getSource())
                && same(left.getLevel(), right.getLevel());
    }

    /* ------------------------------------------------------------ 文型 */

    private static List<JpnWordDetailChildren.Pattern> patterns(JsonNode detail,
                                                                List<JpnWordDetailChildren.Pattern> origin) {
        List<JpnWordDetailChildren.Pattern> rows = new ArrayList<>();
        for (JsonNode node : JpnWordDetailJson.items(detail, "patterns")) {
            JpnWordDetailChildren.Pattern row = new JpnWordDetailChildren.Pattern();
            row.setPattern(JpnWordDetailJson.text(node, "pattern"));
            row.setReading(JpnWordDetailJson.text(node, "reading"));
            row.setChinese(JpnWordDetailJson.text(node, "chinese"));
            row.setExample(JpnWordDetailJson.text(node, "example"));
            row.setExampleChinese(JpnWordDetailJson.text(node, "exampleChinese"));
            applyOrigin(row, find(origin, row, JpnWordDetailEditorComposer::matchPattern), rows.size() + 1);
            rows.add(row);
        }
        return rows;
    }

    private static boolean matchPattern(JpnWordDetailChildren.Pattern left, JpnWordDetailChildren.Pattern right) {
        return same(left.getPattern(), right.getPattern())
                && same(left.getReading(), right.getReading())
                && same(left.getChinese(), right.getChinese())
                && same(left.getExample(), right.getExample())
                && same(left.getExampleChinese(), right.getExampleChinese());
    }

    /* ------------------------------------------------------------ 会話（2 層） */

    private static List<JpnWordDetailChildren.Dialog> dialogs(JsonNode detail,
                                                              List<JpnWordDetailChildren.Dialog> origin) {
        List<JpnWordDetailChildren.Dialog> rows = new ArrayList<>();
        for (JsonNode node : JpnWordDetailJson.items(detail, "dialogs")) {
            JpnWordDetailChildren.Dialog row = new JpnWordDetailChildren.Dialog();
            row.setScene(JpnWordDetailJson.text(node, "scene"));

            List<JpnWordDetailChildren.DialogLine> lines = new ArrayList<>();
            for (JsonNode lineNode : JpnWordDetailJson.items(node, "lines")) {
                JpnWordDetailChildren.DialogLine line = new JpnWordDetailChildren.DialogLine();
                line.setSpeaker(JpnWordDetailJson.text(lineNode, "speaker"));
                line.setJapanese(JpnWordDetailJson.text(lineNode, "japanese"));
                line.setChinese(JpnWordDetailJson.text(lineNode, "chinese"));
                lines.add(line);
            }
            row.setLines(lines);

            JpnWordDetailChildren.Dialog kept = find(origin, row, JpnWordDetailEditorComposer::matchDialog);
            applyOrigin(row, kept, rows.size() + 1);
            // 会話の中の発言も 1 件ずつ突き合わせる（人が直した発言だけ APP にする）
            List<JpnWordDetailChildren.DialogLine> keptLines = kept == null || kept.getLines() == null
                    ? List.of() : kept.getLines();
            int lineNo = 1;
            for (JpnWordDetailChildren.DialogLine line : lines) {
                applyOrigin(line, find(keptLines, line, JpnWordDetailEditorComposer::matchDialogLine), lineNo);
                lineNo += 1;
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 会話の突き合わせ。場面と発言の並び（話者・日本語・中国語）が同じなら「同じ会話」。
     *
     * <p>発言の 1 つでも変われば「別の会話」＝新しい会話として {@code APP} にする
     * （人が直した会話は AI に上書きされない）。</p>
     */
    private static boolean matchDialog(JpnWordDetailChildren.Dialog left, JpnWordDetailChildren.Dialog right) {
        if (!same(left.getScene(), right.getScene())) {
            return false;
        }
        List<JpnWordDetailChildren.DialogLine> leftLines = left.getLines() == null ? List.of() : left.getLines();
        List<JpnWordDetailChildren.DialogLine> rightLines = right.getLines() == null ? List.of() : right.getLines();
        if (leftLines.size() != rightLines.size()) {
            return false;
        }
        for (int index = 0; index < leftLines.size(); index += 1) {
            if (!matchDialogLine(leftLines.get(index), rightLines.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchDialogLine(JpnWordDetailChildren.DialogLine left,
                                           JpnWordDetailChildren.DialogLine right) {
        return same(left.getSpeaker(), right.getSpeaker())
                && same(left.getJapanese(), right.getJapanese())
                && same(left.getChinese(), right.getChinese());
    }

    /* ------------------------------------------------------------ 類義語 */

    private static List<JpnWordDetailChildren.Synonym> synonyms(JsonNode detail,
                                                                List<JpnWordDetailChildren.Synonym> origin) {
        List<JpnWordDetailChildren.Synonym> rows = new ArrayList<>();
        for (JsonNode node : JpnWordDetailJson.items(detail, "synonyms")) {
            JpnWordDetailChildren.Synonym row = new JpnWordDetailChildren.Synonym();
            row.setHeading(JpnWordDetailJson.text(node, "heading"));
            row.setReading(JpnWordDetailJson.text(node, "reading"));
            row.setChinese(JpnWordDetailJson.text(node, "chinese"));
            row.setShared(JpnWordDetailJson.text(node, "shared"));
            row.setDifference(JpnWordDetailJson.text(node, "difference"));
            row.setScene(JpnWordDetailJson.text(node, "scene"));
            applyOrigin(row, find(origin, row, JpnWordDetailEditorComposer::matchSynonym), rows.size() + 1);
            rows.add(row);
        }
        return rows;
    }

    private static boolean matchSynonym(JpnWordDetailChildren.Synonym left, JpnWordDetailChildren.Synonym right) {
        return same(left.getHeading(), right.getHeading())
                && same(left.getReading(), right.getReading())
                && same(left.getChinese(), right.getChinese())
                && same(left.getShared(), right.getShared())
                && same(left.getDifference(), right.getDifference())
                && same(left.getScene(), right.getScene());
    }

    /* ------------------------------------------------------------ 注意 */

    private static List<JpnWordDetailChildren.Caution> cautions(JsonNode detail,
                                                                List<JpnWordDetailChildren.Caution> origin) {
        List<JpnWordDetailChildren.Caution> rows = new ArrayList<>();
        for (JsonNode node : JpnWordDetailJson.items(detail, "cautions")) {
            JpnWordDetailChildren.Caution row = new JpnWordDetailChildren.Caution();
            // 列の CHECK は 4 通りだけ
            row.setKind(JpnWordDetailJson.cautionKind(JpnWordDetailJson.text(node, "kind")));
            row.setTitle(JpnWordDetailJson.text(node, "title"));
            row.setWrong(JpnWordDetailJson.text(node, "wrong"));
            row.setCorrect(JpnWordDetailJson.text(node, "correct"));
            row.setReason(JpnWordDetailJson.text(node, "reason"));
            applyOrigin(row, find(origin, row, JpnWordDetailEditorComposer::matchCaution), rows.size() + 1);
            rows.add(row);
        }
        return rows;
    }

    private static boolean matchCaution(JpnWordDetailChildren.Caution left, JpnWordDetailChildren.Caution right) {
        return same(left.getKind(), right.getKind())
                && same(left.getTitle(), right.getTitle())
                && same(left.getWrong(), right.getWrong())
                && same(left.getCorrect(), right.getCorrect())
                && same(left.getReason(), right.getReason());
    }

    /* ---------------------------------------------------- コロケーション */

    private static List<JpnWordDetailChildren.Collocation> collocations(
            JsonNode detail, List<JpnWordDetailChildren.Collocation> origin) {
        List<JpnWordDetailChildren.Collocation> rows = new ArrayList<>();
        for (JsonNode node : JpnWordDetailJson.items(detail, "collocations")) {
            JpnWordDetailChildren.Collocation row = new JpnWordDetailChildren.Collocation();
            row.setExpression(JpnWordDetailJson.text(node, "expression"));
            row.setReading(JpnWordDetailJson.text(node, "reading"));
            row.setChinese(JpnWordDetailJson.text(node, "chinese"));
            row.setUsage(JpnWordDetailJson.text(node, "usage"));
            row.setExampleJapanese(JpnWordDetailJson.text(node, "exampleJapanese"));
            row.setExampleChinese(JpnWordDetailJson.text(node, "exampleChinese"));
            applyOrigin(row, find(origin, row, JpnWordDetailEditorComposer::matchCollocation), rows.size() + 1);
            rows.add(row);
        }
        return rows;
    }

    private static boolean matchCollocation(JpnWordDetailChildren.Collocation left,
                                            JpnWordDetailChildren.Collocation right) {
        return same(left.getExpression(), right.getExpression())
                && same(left.getReading(), right.getReading())
                && same(left.getChinese(), right.getChinese())
                && same(left.getUsage(), right.getUsage())
                && same(left.getExampleJapanese(), right.getExampleJapanese())
                && same(left.getExampleChinese(), right.getExampleChinese());
    }

    /* ------------------------------------------------------------ 関連語 */

    private static List<JpnWordDetailChildren.RelatedWord> relatedWords(
            JsonNode detail, List<JpnWordDetailChildren.RelatedWord> origin) {
        List<JpnWordDetailChildren.RelatedWord> rows = new ArrayList<>();
        for (JsonNode node : JpnWordDetailJson.items(detail, "relatedWords")) {
            JpnWordDetailChildren.RelatedWord row = new JpnWordDetailChildren.RelatedWord();
            // 列の CHECK は 4 通りだけ
            row.setRelation(JpnWordDetailJson.relation(JpnWordDetailJson.text(node, "relation")));
            row.setHeading(JpnWordDetailJson.text(node, "heading"));
            row.setReading(JpnWordDetailJson.text(node, "reading"));
            row.setChinese(JpnWordDetailJson.text(node, "chinese"));
            applyOrigin(row, find(origin, row, JpnWordDetailEditorComposer::matchRelatedWord), rows.size() + 1);
            rows.add(row);
        }
        return rows;
    }

    private static boolean matchRelatedWord(JpnWordDetailChildren.RelatedWord left,
                                            JpnWordDetailChildren.RelatedWord right) {
        return same(left.getRelation(), right.getRelation())
                && same(left.getHeading(), right.getHeading())
                && same(left.getReading(), right.getReading())
                && same(left.getChinese(), right.getChinese());
    }

    /* ---------------------------------------------------------- 使用場面 */

    private static List<JpnWordDetailChildren.UsageNote> usageNotes(
            JsonNode detail, List<JpnWordDetailChildren.UsageNote> origin) {
        List<JpnWordDetailChildren.UsageNote> rows = new ArrayList<>();
        for (JsonNode node : JpnWordDetailJson.items(detail, "usageNotes")) {
            JpnWordDetailChildren.UsageNote row = new JpnWordDetailChildren.UsageNote();
            row.setRegister(JpnWordDetailJson.text(node, "register"));
            row.setPoliteness(JpnWordDetailJson.text(node, "politeness"));
            row.setAudience(JpnWordDetailJson.text(node, "audience"));
            row.setNote(JpnWordDetailJson.text(node, "note"));
            row.setSenseNumber(JpnWordDetailJson.integer(node, "senseNumber"));
            applyOrigin(row, find(origin, row, JpnWordDetailEditorComposer::matchUsageNote), rows.size() + 1);
            rows.add(row);
        }
        return rows;
    }

    private static boolean matchUsageNote(JpnWordDetailChildren.UsageNote left,
                                          JpnWordDetailChildren.UsageNote right) {
        return same(left.getRegister(), right.getRegister())
                && same(left.getPoliteness(), right.getPoliteness())
                && same(left.getAudience(), right.getAudience())
                && same(left.getNote(), right.getNote())
                && same(left.getSenseNumber(), right.getSenseNumber());
    }

    /* ------------------------------------------------------------ 練習 */

    private static List<JpnWordDetailChildren.Practice> practices(
            JsonNode detail, List<JpnWordDetailChildren.Practice> origin) {
        List<JpnWordDetailChildren.Practice> rows = new ArrayList<>();
        for (JsonNode node : JpnWordDetailJson.items(detail, "practices")) {
            JpnWordDetailChildren.Practice row = new JpnWordDetailChildren.Practice();
            // 列の CHECK は 4 通りだけ
            row.setKind(JpnWordDetailJson.practiceKind(JpnWordDetailJson.text(node, "kind")));
            row.setQuestion(JpnWordDetailJson.text(node, "question"));
            row.setQuestionChinese(JpnWordDetailJson.text(node, "questionChinese"));
            // 選択肢は文字列の配列（列は JSONB）
            row.setChoicesJson(JpnWordDetailJson.stringArrayJson(node.path("choices")));
            row.setFreeWriting(JpnWordDetailJson.bool(node, "freeWriting"));
            row.setAnswer(JpnWordDetailJson.text(node, "answer"));
            row.setExplanation(JpnWordDetailJson.text(node, "explanation"));
            applyOrigin(row, find(origin, row, JpnWordDetailEditorComposer::matchPractice), rows.size() + 1);
            rows.add(row);
        }
        return rows;
    }

    private static boolean matchPractice(JpnWordDetailChildren.Practice left,
                                         JpnWordDetailChildren.Practice right) {
        return same(left.getKind(), right.getKind())
                && same(left.getQuestion(), right.getQuestion())
                && same(left.getQuestionChinese(), right.getQuestionChinese())
                && same(left.getAnswer(), right.getAnswer())
                && same(left.getExplanation(), right.getExplanation())
                && same(left.getFreeWriting(), right.getFreeWriting())
                // 選択肢は JSONB の文字列。空白の入り方は DB が変えるので、値で比べる
                && same(JpnWordDetailJson.stringArray(left.getChoicesJson()),
                        JpnWordDetailJson.stringArray(right.getChoicesJson()));
    }

    /* ------------------------------------------------------------ 小物 */

    /** 語義番号（1 以上のときだけ。無ければ段落内の位置）。 */
    private static Integer number(JsonNode node, int fallback) {
        Integer value = JpnWordDetailJson.integer(node, "number");
        return value == null || value < 1 ? fallback : value;
    }

    /**
     * 元の行が見つかればその出所を引き継ぎ、見つからなければ「人が作った行」にする。
     *
     * @param orderNo 新しい版での {@code 表示順}（1 から。画面の並びをそのまま保存する）
     */
    private static <T> void applyOrigin(T row, T origin, int orderNo) {
        if (origin == null) {
            setOrigin(row, SOURCE_APP, true);
        } else {
            setOrigin(row, sourceOf(origin), manualOf(origin));
        }
        setOrderNo(row, orderNo);
    }

    /**
     * 出所を読む。列の既定は 'BATCH' なので、値が無い行は AI の行とみなす
     * （人かどうかの判断は {@link #manualOf} も見る）。
     */
    private static String sourceOf(Object row) {
        String source = sourceCodeOf(row);
        return source == null || source.isBlank() ? (manualOf(row) ? SOURCE_APP : SOURCE_BATCH) : source;
    }

    private static boolean manualOf(Object row) {
        Boolean manual = manualCorrectedOf(row);
        return Boolean.TRUE.equals(manual);
    }

    /**
     * 元の行の内容が同じ行を探す（見つからなければ null＝新しい行）。
     *
     * <p>突き合わせは内容で行う（表示順では行わない）。画面は行を並べ替えられるので、
     * 同じ内容の行が別の位置に来ることがある。</p>
     */
    private static <T> T find(List<T> rows, T target, java.util.function.BiPredicate<T, T> match) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        for (T row : rows) {
            if (match.test(target, row)) {
                return row;
            }
        }
        return null;
    }

    /** 同じ値か（両方 null または空白だけなら同じ）。 */
    private static boolean same(Object left, Object right) {
        return normalize(left).equals(normalize(right));
    }

    private static String normalize(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private static boolean same(List<String> left, List<String> right) {
        List<String> leftValues = left == null ? List.of() : left;
        List<String> rightValues = right == null ? List.of() : right;
        if (leftValues.size() != rightValues.size()) {
            return false;
        }
        for (int index = 0; index < leftValues.size(); index += 1) {
            if (!normalize(leftValues.get(index)).equals(normalize(rightValues.get(index)))) {
                return false;
            }
        }
        return true;
    }

    /* 段落の行の共通の列（表示順・手修正フラグ・登録元コード）への橋渡し。
       11 の行のクラスは共通の親を持たないので、ここで型ごとに振り分ける。 */

    private static void setOrderNo(Object row, int orderNo) {
        if (row instanceof JpnWordDetailChildren.Sense value) {
            value.setOrderNo(orderNo);
        } else if (row instanceof JpnWordDetailChildren.Example value) {
            value.setOrderNo(orderNo);
        } else if (row instanceof JpnWordDetailChildren.Pattern value) {
            value.setOrderNo(orderNo);
        } else if (row instanceof JpnWordDetailChildren.Dialog value) {
            value.setOrderNo(orderNo);
        } else if (row instanceof JpnWordDetailChildren.DialogLine value) {
            value.setOrderNo(orderNo);
        } else if (row instanceof JpnWordDetailChildren.Synonym value) {
            value.setOrderNo(orderNo);
        } else if (row instanceof JpnWordDetailChildren.Caution value) {
            value.setOrderNo(orderNo);
        } else if (row instanceof JpnWordDetailChildren.Collocation value) {
            value.setOrderNo(orderNo);
        } else if (row instanceof JpnWordDetailChildren.RelatedWord value) {
            value.setOrderNo(orderNo);
        } else if (row instanceof JpnWordDetailChildren.UsageNote value) {
            value.setOrderNo(orderNo);
        } else if (row instanceof JpnWordDetailChildren.Practice value) {
            value.setOrderNo(orderNo);
        }
    }

    private static void setOrigin(Object row, String sourceCode, boolean manual) {
        if (row instanceof JpnWordDetailChildren.Sense value) {
            value.setSourceCode(sourceCode);
            value.setManualCorrected(manual);
        } else if (row instanceof JpnWordDetailChildren.Example value) {
            value.setSourceCode(sourceCode);
            value.setManualCorrected(manual);
        } else if (row instanceof JpnWordDetailChildren.Pattern value) {
            value.setSourceCode(sourceCode);
            value.setManualCorrected(manual);
        } else if (row instanceof JpnWordDetailChildren.Dialog value) {
            value.setSourceCode(sourceCode);
            value.setManualCorrected(manual);
        } else if (row instanceof JpnWordDetailChildren.DialogLine value) {
            value.setSourceCode(sourceCode);
            value.setManualCorrected(manual);
        } else if (row instanceof JpnWordDetailChildren.Synonym value) {
            value.setSourceCode(sourceCode);
            value.setManualCorrected(manual);
        } else if (row instanceof JpnWordDetailChildren.Caution value) {
            value.setSourceCode(sourceCode);
            value.setManualCorrected(manual);
        } else if (row instanceof JpnWordDetailChildren.Collocation value) {
            value.setSourceCode(sourceCode);
            value.setManualCorrected(manual);
        } else if (row instanceof JpnWordDetailChildren.RelatedWord value) {
            value.setSourceCode(sourceCode);
            value.setManualCorrected(manual);
        } else if (row instanceof JpnWordDetailChildren.UsageNote value) {
            value.setSourceCode(sourceCode);
            value.setManualCorrected(manual);
        } else if (row instanceof JpnWordDetailChildren.Practice value) {
            value.setSourceCode(sourceCode);
            value.setManualCorrected(manual);
        }
    }

    private static String sourceCodeOf(Object row) {
        if (row instanceof JpnWordDetailChildren.Sense value) {
            return value.getSourceCode();
        } else if (row instanceof JpnWordDetailChildren.Example value) {
            return value.getSourceCode();
        } else if (row instanceof JpnWordDetailChildren.Pattern value) {
            return value.getSourceCode();
        } else if (row instanceof JpnWordDetailChildren.Dialog value) {
            return value.getSourceCode();
        } else if (row instanceof JpnWordDetailChildren.DialogLine value) {
            return value.getSourceCode();
        } else if (row instanceof JpnWordDetailChildren.Synonym value) {
            return value.getSourceCode();
        } else if (row instanceof JpnWordDetailChildren.Caution value) {
            return value.getSourceCode();
        } else if (row instanceof JpnWordDetailChildren.Collocation value) {
            return value.getSourceCode();
        } else if (row instanceof JpnWordDetailChildren.RelatedWord value) {
            return value.getSourceCode();
        } else if (row instanceof JpnWordDetailChildren.UsageNote value) {
            return value.getSourceCode();
        } else if (row instanceof JpnWordDetailChildren.Practice value) {
            return value.getSourceCode();
        }
        return null;
    }

    private static Boolean manualCorrectedOf(Object row) {
        if (row instanceof JpnWordDetailChildren.Sense value) {
            return value.getManualCorrected();
        } else if (row instanceof JpnWordDetailChildren.Example value) {
            return value.getManualCorrected();
        } else if (row instanceof JpnWordDetailChildren.Pattern value) {
            return value.getManualCorrected();
        } else if (row instanceof JpnWordDetailChildren.Dialog value) {
            return value.getManualCorrected();
        } else if (row instanceof JpnWordDetailChildren.DialogLine value) {
            return value.getManualCorrected();
        } else if (row instanceof JpnWordDetailChildren.Synonym value) {
            return value.getManualCorrected();
        } else if (row instanceof JpnWordDetailChildren.Caution value) {
            return value.getManualCorrected();
        } else if (row instanceof JpnWordDetailChildren.Collocation value) {
            return value.getManualCorrected();
        } else if (row instanceof JpnWordDetailChildren.RelatedWord value) {
            return value.getManualCorrected();
        } else if (row instanceof JpnWordDetailChildren.UsageNote value) {
            return value.getManualCorrected();
        } else if (row instanceof JpnWordDetailChildren.Practice value) {
            return value.getManualCorrected();
        }
        return null;
    }
}
