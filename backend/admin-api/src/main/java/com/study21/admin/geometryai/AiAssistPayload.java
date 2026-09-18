package com.study21.admin.geometryai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.admin.batch.BatchExecutionEntity;

import java.io.IOException;

/**
 * バッチ実行履歴の `要求内容`（列 `要求内容` JSONB）の読み書き（AI 画図助手・batC52）。
 *
 * <p>形は {@code {"assistId": 123}}。旧 AI 生図の実行（batC51）は {@code {"aiRequestId": N}} を
 * 入れるので、**この違いで過去の batC52（旧 AI 生図 AI 生成）と新しい batC52（助手）の履歴を
 * 見分けられる**（番号を再利用したため）。</p>
 */
public final class AiAssistPayload {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiAssistPayload() {
    }

    /** 実行の要求内容に埋め込む JSON を作る。 */
    public static String of(long assistId) {
        return "{\"assistId\":" + assistId + "}";
    }

    /** 実行の要求内容から `assistId` を読む。無い（バッチ管理画面からの単体再実行）ときは null。 */
    public static Long assistId(BatchExecutionEntity execution) {
        if (execution == null || execution.getRequestPayload() == null
                || execution.getRequestPayload().isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(execution.getRequestPayload()).path("assistId");
            return node.isNumber() || node.isTextual() ? node.asLong() : null;
        } catch (IOException cause) {
            return null;
        }
    }
}
