package com.study21.admin.batch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * バッチ実行履歴の `要求内容`（JSONB）から「何を処理したか」を取り出す。
 *
 * <p>batC52 の番号を再利用したため、実行履歴の行が「旧・AI 生図 AI 生成」と「新・AI 画図助手」に
 * 混ざる。`要求内容` のキーで判別できるので、実行履歴 API が
 * {@code targetKind} / {@code targetId} / {@code targetKey} を返す（画面は「AI生図 #164」「画図助手 #12」と出せる）。</p>
 */
public final class BatchExecutionTarget {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BatchExecutionTarget() {
    }

    /** 取り出した対象。`targetKind` が null なら「対象なし（その他）」。 */
    public record Target(String targetKind, Long targetId, String targetKey) {
    }

    private static final Target NONE = new Target(null, null, null);

    /** 要求内容の JSON 文字列から対象を読む。 */
    public static Target parse(String requestPayloadJson) {
        if (requestPayloadJson == null || requestPayloadJson.isBlank()) {
            return NONE;
        }
        try {
            JsonNode root = MAPPER.readTree(requestPayloadJson);
            for (String key : new String[]{"aiRequestId", "assistId", "noteId"}) {
                JsonNode node = root.path(key);
                if (node.isNumber() || node.isTextual()) {
                    long id;
                    try {
                        id = node.asLong();
                    } catch (NumberFormatException cause) {
                        return NONE;
                    }
                    return new Target(kindOf(key), id, key);
                }
            }
            return NONE;
        } catch (IOException cause) {
            return NONE;
        }
    }

    private static String kindOf(String key) {
        return switch (key) {
            case "aiRequestId" -> "AI_FIGURE";
            case "assistId" -> "AI_ASSIST";
            case "noteId" -> "CLASSROOM_NOTE";
            default -> null;
        };
    }
}
