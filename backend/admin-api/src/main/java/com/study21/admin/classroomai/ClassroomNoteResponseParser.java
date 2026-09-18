package com.study21.admin.classroomai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * AI の応答から授業ノートの JSON（テーマ／学習内容／先生の重点／宿題）を取り出す。
 *
 * <p>OpenAI 互換の {@code choices[0].message.content} を読み、コードフェンスを剥がして
 * 最初の {@code {…}} を JSON オブジェクトとして検証する。壊れていれば {@code INVALID_JSON}。</p>
 */
public final class ClassroomNoteResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ClassroomNoteResponseParser() {
    }

    /** 解析結果。`errorCode` が null なら成功（noteJson は検証済みの JSON 文字列）。 */
    public record ParseResult(String noteJson, String errorCode, String errorMessage) {

        public static ParseResult ok(String noteJson) {
            return new ParseResult(noteJson, null, null);
        }

        public static ParseResult failure(String code, String message) {
            return new ParseResult(null, code, message);
        }

        public boolean isSuccess() {
            return errorCode == null;
        }
    }

    /** HTTP の本文を解析する。 */
    public static ParseResult parse(String body) {
        String content = assistantContent(body);
        if (content == null || content.isBlank()) {
            return ParseResult.failure("EMPTY_RESPONSE", "AI から内容が返りませんでした。");
        }
        String text = stripFence(content).trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return ParseResult.failure("INVALID_JSON", "AI の応答を JSON として読めませんでした。");
        }
        String json = text.substring(start, end + 1);
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isObject()) {
                return ParseResult.failure("INVALID_JSON", "AI の応答が JSON オブジェクトではありません。");
            }
            return ParseResult.ok(json);
        } catch (IOException cause) {
            return ParseResult.failure("INVALID_JSON", "AI の応答を JSON として読めませんでした。");
        }
    }

    /** OpenAI 互換の `choices[0].message.content`（無ければ本文そのもの）。 */
    public static String assistantContent(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode node = root.path("choices").path(0).path("message").path("content");
            if (!node.isMissingNode() && !node.isNull() && node.isTextual()) {
                return node.asText();
            }
            return null;
        } catch (IOException cause) {
            return null;
        }
    }

    /** コードフェンス（```json … ```）を剥がす。 */
    static String stripFence(String text) {
        String value = text.trim();
        if (!value.startsWith("```")) {
            return value;
        }
        int firstLineEnd = value.indexOf('\n');
        if (firstLineEnd < 0) {
            return value;
        }
        String rest = value.substring(firstLineEnd + 1);
        int closing = rest.lastIndexOf("```");
        return (closing < 0 ? rest : rest.substring(0, closing)).trim();
    }
}
