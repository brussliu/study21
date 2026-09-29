package com.study21.common.core.japanese;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 詳細（旧 {@code 詳細JSON} と同じ形の JSON）を読む小さな道具。
 *
 * <p>詳細を作る側（admin-api の AI 取得）と、画面の編集を版にする側（common-core の
 * {@link JpnWordDetailEditorComposer}）が<b>同じ読み方</b>をするために置く。ここが 1 か所に
 * なっていないと「書き側が入れた値」と「読み側が突き合わせる値」が食い違う。</p>
 *
 * <p><strong>欠けたキーは null</strong>（{@code asText()} の既定文字列を入れない）。
 * AI の取り直しでは「今回書かなかった段落は元の版のまま残す」ので、書かなかったことと
 * 空文字を書いたことを区別する必要がある。</p>
 *
 * <p>Spring に依存しない（user-api と admin-api のどちらからでも同じ結果になる）。</p>
 */
public final class JpnWordDetailJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JpnWordDetailJson() {
    }

    /**
     * 詳細の本体。{@code detail} の入れ子（AI の生の応答）でも、詳細そのものでも読めるようにする。
     *
     * @return 詳細のオブジェクト。null・空・配列・壊れた JSON は null
     */
    public static JsonNode detail(String detailJson) {
        JsonNode root = readTree(detailJson);
        if (root == null || !root.isObject() || root.isEmpty()) {
            return null;
        }
        JsonNode nested = root.path("detail");
        return nested.isObject() ? nested : root;
    }

    /**
     * 詳細の Map（Jackson が作ったもの）を JSON のノードにする。
     *
     * <p>画面から来た詳細は Map のままなので、読み方を 1 つに揃えるためにここでノードにする
     * （{@code 詳細JSON} の文字列にする必要は無い。もう JSONB の列は無い）。</p>
     */
    public static JsonNode detail(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        JsonNode root = MAPPER.valueToTree(detail);
        return detail(root);
    }

    /** 詳細のノード。{@code detail} の入れ子は外す。 */
    public static JsonNode detail(JsonNode root) {
        if (root == null || !root.isObject() || root.isEmpty()) {
            return null;
        }
        JsonNode nested = root.path("detail");
        return nested.isObject() ? nested : root;
    }

    /**
     * 詳細の段落（配列）を取り出す。配列でなければ空。
     *
     * <p>{@code detail} が null のときは「段落を書かなかった」ではなく「詳細そのものが無い」なので、
     * 呼ぶ側は元の版の内容を使う（{@link #items} は空を返す）。</p>
     */
    public static List<JsonNode> items(JsonNode detail, String field) {
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

    /** その段落のキーがあるか（無い＝今回は触らない、と読む）。 */
    public static boolean has(JsonNode detail, String field) {
        return detail != null && detail.has(field);
    }

    /** オブジェクト（空のオブジェクトは null 扱い）。 */
    public static JsonNode object(JsonNode node) {
        return node != null && node.isObject() && !node.isEmpty() ? node : null;
    }

    /** 文字列の値を読む。数値・真偽値は文字列にする。無いキー・JSON の null は null。 */
    public static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    /** 整数の値を読む。数でなければ null。 */
    public static Integer integer(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    /** 真偽の値を読む（無ければ false）。 */
    public static boolean bool(JsonNode node, String field) {
        return node != null && node.path(field).asBoolean(false);
    }

    /** オブジェクト・配列を JSONB の列に入れる文字列にする（無ければ null）。 */
    public static String json(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.toString();
    }

    /** JSON の文字列になっている配列を文字列の配列にする（文字列でない要素は落とす）。 */
    public static List<String> stringArray(String json) {
        List<String> list = new ArrayList<>();
        for (JsonNode node : readArray(json)) {
            if (node.isTextual()) {
                list.add(node.asText());
            }
        }
        return list;
    }

    /** 文字列の配列（JSON のノード）を JSON の文字列にする（列は JSONB。無ければ空配列）。 */
    public static String stringArrayJson(JsonNode node) {
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

    /** JSON のオブジェクト・配列の配列（{@code 活用形JSON} など）を読む。 */
    public static List<JsonNode> readArray(String json) {
        JsonNode node = readTree(json);
        if (!(node instanceof ArrayNode array)) {
            return List.of();
        }
        List<JsonNode> list = new ArrayList<>(array.size());
        array.forEach(list::add);
        return list;
    }

    /** 列の CHECK に合う値だけを返す（前後の空白を落とし、合わなければ null）。 */
    public static String oneOf(String value, String... allowed) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (String candidate : allowed) {
            if (candidate.equals(trimmed)) {
                return trimmed;
            }
        }
        return null;
    }

    /** 例文のレベル（列の CHECK は BASIC / APPLIED だけ）。 */
    public static String level(String value) {
        if (value == null) {
            return null;
        }
        return oneOf(value.toUpperCase(Locale.ROOT), "BASIC", "APPLIED");
    }

    /** 注意の区分（列の CHECK は 4 通り）。 */
    public static String cautionKind(String value) {
        return oneOf(value, "GRAMMAR", "UNNATURAL", "MEANING", "PARTICLE");
    }

    /** 関連語の関係（列の CHECK は 4 通り）。 */
    public static String relation(String value) {
        return oneOf(value, "類義語", "対義語", "間違えやすい", "同じ読み");
    }

    /** 練習の種別（列の CHECK は 4 通り）。 */
    public static String practiceKind(String value) {
        return oneOf(value, "PARTICLE", "SYNONYM", "SCENE", "WRITING");
    }

    private static JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception cause) {
            return null;
        }
    }
}
