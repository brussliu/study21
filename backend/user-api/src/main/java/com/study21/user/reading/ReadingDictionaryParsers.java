package com.study21.user.reading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 外部の辞書・翻訳 API の応答を解釈する純粋な関数群（HTTP も DB もしない）。
 *
 * <p>2.0 の `WordServiceImpl`（`translateByExcelApi` / `translateByYoudao`）と同じ形の
 * 応答を扱う。想定外の本文・壊れた JSON は**例外にせず null**（warn ログだけ出す）。</p>
 *
 * <ul>
 *   <li>ExcelAPI enja … プレーンテキスト（JSON ではない）</li>
 *   <li>有道 suggest … `data.entries[0].explain`</li>
 *   <li>有道 jsonapi … `ce.word[0].phone`（拼音）/ `trs[*].tr[*].l."#tran"`（解説）</li>
 * </ul>
 */
public final class ReadingDictionaryParsers {

    private static final Logger log = LoggerFactory.getLogger(ReadingDictionaryParsers.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 応答が壊れていたときに備えて、解釈する文字数を制限する */
    private static final int MAX_BODY_CHARS = 200_000;

    private ReadingDictionaryParsers() {
    }

    /** 中国語の引き当て結果（拼音と解説）。 */
    public record ChineseEntry(String pinyin, String explanation) {
    }

    /**
     * ExcelAPI enja の応答。プレーンテキストの日本語訳をそのまま返す
     * （例 `(電話の応答で)『もしもし』;…`）。空・エラー本文は null。
     */
    public static String excelApiJapanese(String body) {
        String text = limit(body);
        if (text == null) {
            return null;
        }
        String value = text.trim();
        // API が「見つかりません」等を短い英語で返すことがあるので素通しにしない
        if (value.isEmpty() || value.equalsIgnoreCase("null") || value.startsWith("{\"error")) {
            return null;
        }
        return value;
    }

    /** 有道 suggest の応答から中国語の意味（`data.entries[0].explain`）を取り出す。 */
    public static String youdaoSuggestChinese(String body) {
        JsonNode root = read(body);
        if (root == null) {
            return null;
        }
        JsonNode result = root.path("result");
        if (!"success".equals(result.path("msg").asText()) || result.path("code").asInt() != 200) {
            return null;
        }
        JsonNode entries = root.path("data").path("entries");
        if (!entries.isArray() || entries.isEmpty()) {
            return null;
        }
        return text(entries.path(0).path("explain"));
    }

    /**
     * 有道 jsonapi の応答から拼音と解説を取り出す。
     *
     * <p>拼音は `ce.word[0].phone` → `simple.word[0].phone` → `newhh.dataList[0].pinyin` の順。
     * 解説は `ce`（中国語の説明）→ `simple` → `ec`（英語対訳）の順で、`#tran` を集める。</p>
     *
     * @return どちらも取れなければ null
     */
    public static ChineseEntry youdaoJsonApi(String body) {
        JsonNode root = read(body);
        if (root == null) {
            return null;
        }
        String pinyin = firstText(
                text(root.path("ce").path("word").path(0).path("phone")),
                text(root.path("simple").path("word").path(0).path("phone")),
                text(root.path("newhh").path("dataList").path(0).path("pinyin")));
        String explanation = firstText(
                trsText(root.path("ce").path("word").path(0)),
                trsText(root.path("simple").path("word").path(0)),
                trsText(root.path("ec").path("word").path(0)));
        if (pinyin == null && explanation == null) {
            return null;
        }
        return new ChineseEntry(pinyin, explanation);
    }

    // -------------------------------------------------------------------- 内部

    /** `trs[*].tr[*].l."#tran"` をつないだ中国語の説明（重複は落とす）。 */
    private static String trsText(JsonNode word) {
        List<String> parts = new ArrayList<>();
        for (JsonNode trs : word.path("trs")) {
            for (JsonNode tr : trs.path("tr")) {
                String tran = text(tr.path("l").path("#tran"));
                if (tran != null && !parts.contains(tran)) {
                    parts.add(tran);
                }
            }
        }
        return parts.isEmpty() ? null : String.join("\n", parts);
    }

    private static JsonNode read(String body) {
        String text = limit(body);
        if (text == null) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(text);
            return root == null || root.isNull() ? null : root;
        } catch (Exception ex) {
            // 想定外の応答は握りつぶす（画面は「取得できませんでした」を出す）
            log.warn("reading dictionary response is not readable json. length={}", text.length());
            return null;
        }
    }

    private static String limit(String body) {
        if (body == null) {
            return null;
        }
        String text = body.length() > MAX_BODY_CHARS ? body.substring(0, MAX_BODY_CHARS) : body;
        return text.isBlank() ? null : text;
    }

    private static String text(JsonNode node) {
        if (node == null || !node.isValueNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
