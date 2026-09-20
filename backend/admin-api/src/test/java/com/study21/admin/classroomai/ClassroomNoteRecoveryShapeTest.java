package com.study21.admin.classroomai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **回復の応答の形**を固定する（user-api と画面が読む欄の名前が変わらないように）。
 *
 * <p>user-api は admin-api のモジュールを参照できない（依存の向きが逆）ので、向こう側の契約テストは
 * 「admin-api が実際に直列化する形」をここで押さえる。**欄の名前（特に `reason`）を変えたら、
 * user-api の `toTaskResult` と画面も一緒に直す必要がある**。</p>
 */
class ClassroomNoteRecoveryShapeTest {

    private static final ObjectMapper MAPPER = Jackson2ObjectMapperBuilder.json().build();

    @Test
    @DisplayName("回復の応答は noteId / status / liveness / recoverable / recovered / reason で返る")
    void recoveryViewSerializesWithReason() throws Exception {
        ClassroomAiPipelineService.RecoveryView view = new ClassroomAiPipelineService.RecoveryView(
                7L, "FAILED", "LOST", true, true,
                "実行が失われていたため、やり直せる状態に戻しました。");

        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(view));

        assertThat(json.get("noteId").asLong()).isEqualTo(7L);
        assertThat(json.get("status").asText()).isEqualTo("FAILED");
        assertThat(json.get("liveness").asText()).isEqualTo("LOST");
        assertThat(json.get("recoverable").asBoolean()).isTrue();
        assertThat(json.get("recovered").asBoolean()).isTrue();
        // **理由は `reason`**（`message` ではない）。user-api がここを `message` へ写す
        assertThat(json.hasNonNull("reason")).isTrue();
        assertThat(json.get("reason").asText()).contains("やり直せる状態に戻しました");
        assertThat(json.has("message")).isFalse();
    }

    @Test
    @DisplayName("理由が無いときは reason が null で返る（欄そのものは消えない）")
    void blankReasonIsNull() throws Exception {
        ClassroomAiPipelineService.RecoveryView view = new ClassroomAiPipelineService.RecoveryView(
                7L, "GENERATING", "RUNNING", false, false, null);

        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(view));

        assertThat(json.has("reason")).as("欄は消えない（null で載る）").isTrue();
        assertThat(json.get("reason").isNull()).isTrue();
    }
}
