package com.study21.admin.geometryai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 補充パラメータ（要求行の JSON）をプロンプトへ渡す行に直す。
 *
 * <p>キーは**日本語の項目名**（画面の見出しと同じ）。値が空の項目は出さない。
 * どの項目を保存してよいかは user-api（受理する側）がモードと結果種別で決めるので、
 * ここは「入っているものを読める形にする」だけ（**適用作外の項目を勝手に足さない**）。</p>
 */
public final class FigureSupplements {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FigureSupplements() {
    }

    /** JSON（null / 空 / 壊れている も許す）を「項目名: 値」の行にする。 */
    public static List<String> rows(String supplementsJson) {
        List<String> rows = new ArrayList<>();
        if (supplementsJson == null || supplementsJson.isBlank()) {
            return rows;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(supplementsJson);
        } catch (Exception cause) {
            return rows;
        }
        if (root == null || !root.isObject()) {
            return rows;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String value = text(field.getValue());
            if (value != null && !value.isBlank()) {
                rows.add(field.getKey() + ": " + value);
            }
        }
        return rows;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText().trim();
        }
        if (node.isBoolean()) {
            return node.asBoolean() ? "はい" : "いいえ";
        }
        if (node.isNumber()) {
            return node.asText();
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode element : node) {
                String text = text(element);
                if (text != null && !text.isBlank()) {
                    values.add(text);
                }
            }
            return String.join("、", values);
        }
        return node.toString();
    }
}
