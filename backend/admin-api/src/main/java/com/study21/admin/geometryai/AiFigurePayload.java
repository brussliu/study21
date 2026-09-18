package com.study21.admin.geometryai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.admin.batch.BatchExecutionEntity;

import java.io.IOException;

/**
 * バッチ実行履歴の `要求内容`（列 `要求内容` JSONB。既存の列で、2.1 では今まで未使用だった）の読み書き。
 *
 * <p>AI 生図は「1 操作 = 3 工程（batC51 → 52 → 53）」なので、**どのバッチ実行がどの要求を
 * 処理したか**をこの列に残す（設計 §2.2）。形は次のとおり:</p>
 *
 * <pre>{"aiRequestId": 123, "requestNo": "AIG202609141200001234", "stage": "PREPROCESS"}</pre>
 */
public final class AiFigurePayload {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiFigurePayload() {
    }

    /** 実行の要求内容に埋め込む JSON を作る。 */
    public static String of(long requestId, String requestNo, String stage) {
        StringBuilder builder = new StringBuilder("{\"aiRequestId\":").append(requestId);
        if (requestNo != null && !requestNo.isBlank()) {
            builder.append(",\"requestNo\":\"").append(requestNo.replace("\"", "")).append('"');
        }
        if (stage != null && !stage.isBlank()) {
            builder.append(",\"stage\":\"").append(stage).append('"');
        }
        return builder.append('}').toString();
    }

    /**
     * 実行の要求内容から `aiRequestId` を読む。
     * 無い（画面の【再実行】から呼ばれた）ときは null を返す＝「未処理の最古の 1 件」を拾う。
     */
    public static Long requestId(BatchExecutionEntity execution) {
        return execution == null ? null : requestId(execution.getRequestPayload());
    }

    /** JSON 文字列から `aiRequestId` を読む。 */
    public static Long requestId(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(payloadJson);
            JsonNode node = root.path("aiRequestId");
            return node.isNumber() || node.isTextual() ? node.asLong() : null;
        } catch (IOException cause) {
            return null;
        }
    }
}
