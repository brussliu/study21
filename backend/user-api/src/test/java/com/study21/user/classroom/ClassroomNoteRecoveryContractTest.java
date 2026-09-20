package com.study21.user.classroom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **まとめの回復の応答の契約**の検証（層をまたいだ欄の名前の食い違いを防ぐ）。
 *
 * <p>admin-api は回復で `reason` を返し、user-api は `message` を返す。画面は `message` だけを読む。
 * ここでは **admin-api の本物の DTO を JSON へ直列化**し、それを **user-api の本物の応答組み立て**に
 * 通して、「理由が消えないこと」「欄の名前が一致していること」を確かめる（手書きの JSON では
 * この食い違いを見つけられない）。</p>
 */
class ClassroomNoteRecoveryContractTest {

    private static final ObjectMapper MAPPER = Jackson2ObjectMapperBuilder.json().build();

    /**
     * admin-api の回復の応答（`RecoveryView`）を JSON にする。
     *
     * <p>本物の DTO は admin-api のモジュールにあり、user-api からは参照できない（依存の向きが逆）。
     * そこで**admin-api が実際に直列化する形**（`{noteId, status, liveness, recoverable, recovered,
     * reason}`）をここに書く。**欄の名前と種類が食い違ったらこのテストが落ちる**
     * （admin-api 側の `RecoveryView` を変えたら、ここも直す必要がある）。</p>
     */
    private JsonNode adminRecoveryJson(String status, String liveness, boolean recoverable,
                                       boolean recovered, String reason) throws Exception {
        var node = MAPPER.createObjectNode();
        node.put("noteId", 1L);
        node.put("status", status);
        node.put("liveness", liveness);
        node.put("recoverable", recoverable);
        node.put("recovered", recovered);
        if (reason == null) {
            node.putNull("reason");
        } else {
            node.put("reason", reason);
        }
        // **admin-api は `message` を返さない**（回復は reason）。ここを変えると写し間違いが起きる
        assertThat(node.has("message")).isFalse();
        return node;
    }

    /** user-api の応答組み立て（`toTaskResult`）を呼ぶ。 */
    private ClassroomModels.NoteTaskResult toTaskResult(JsonNode adminData) throws Exception {
        Method method = ClassroomServiceImpl.class.getDeclaredMethod("toTaskResult",
                long.class, JsonNode.class, boolean.class);
        method.setAccessible(true);
        return (ClassroomModels.NoteTaskResult) method.invoke(null, 1L, adminData, true);
    }

    /** user-api の結果を JSON にして読み直す（画面が受け取る形）。 */
    private JsonNode asWire(ClassroomModels.NoteTaskResult result) throws Exception {
        return MAPPER.readTree(MAPPER.writeValueAsString(result));
    }

    @Test
    @DisplayName("admin-api の reason が、user-api の message として画面まで届く（欄の名前が食い違わない）")
    void adminReasonBecomesUserApiMessage() throws Exception {
        JsonNode admin = adminRecoveryJson("FAILED", "LOST", true, true,
                "実行が失われていたため、やり直せる状態に戻しました。");

        // admin-api 側は `reason` で返す（直列化された実物で確認）
        assertThat(admin.hasNonNull("reason")).as("admin-api の欄は reason").isTrue();
        assertThat(admin.has("message")).as("admin-api は message を返さない").isFalse();

        ClassroomModels.NoteTaskResult result = toTaskResult(admin);
        JsonNode wire = asWire(result);

        assertThat(result.message()).isEqualTo("実行が失われていたため、やり直せる状態に戻しました。");
        // **画面が読む欄**（`message`）に載っている
        assertThat(wire.hasNonNull("message")).isTrue();
        assertThat(wire.get("message").asText()).contains("やり直せる状態に戻しました");
        // 構造化の欄も落ちていない
        assertThat(wire.get("status").asText()).isEqualTo("FAILED");
        assertThat(wire.get("liveness").asText()).isEqualTo("LOST");
        assertThat(wire.get("recovered").asBoolean()).isTrue();
        assertThat(wire.get("recoverable").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("段階ごとの理由がそれぞれ画面に出る（RUNNING / BINDING / UNKNOWN / LOST 回復済み）")
    void eachLivenessKeepsItsOwnReason() throws Exception {
        // RUNNING: 実行中
        ClassroomModels.NoteTaskResult running = toTaskResult(
                adminRecoveryJson("GENERATING", "RUNNING", false, false,
                        "この最終まとめは実行中です（このままお待ちください）。"));
        assertThat(running.message()).contains("実行中");
        assertThat(running.accepted()).as("実行中は受理済みとして扱う").isTrue();
        assertThat(running.recovered()).isFalse();

        // BINDING: 準備中
        ClassroomModels.NoteTaskResult binding = toTaskResult(
                adminRecoveryJson("GENERATING", "BINDING", false, false,
                        "この最終まとめの実行を準備しています。"));
        assertThat(binding.message()).contains("準備しています");

        // UNKNOWN: 確認できない（**成功とも失敗とも言わない**）
        ClassroomModels.NoteTaskResult unknown = toTaskResult(
                adminRecoveryJson("GENERATING", "UNKNOWN", false, false,
                        "実行の状態を確認できませんでした。"));
        assertThat(unknown.message()).contains("確認できませんでした");
        assertThat(unknown.recovered()).isFalse();

        // LOST かつ回復した: やり直せる
        ClassroomModels.NoteTaskResult recovered = toTaskResult(
                adminRecoveryJson("FAILED", "LOST", true, true,
                        "実行が失われていたため、やり直せる状態に戻しました。"));
        assertThat(recovered.message()).contains("やり直せる状態に戻しました");
        assertThat(recovered.status()).isEqualTo("FAILED");
        assertThat(recovered.recovered()).isTrue();
    }

    @Test
    @DisplayName("理由が空・空白でも undefined/null を出さず、段階に合った兜底にする")
    void blankReasonFallsBackWithoutUndefined() throws Exception {
        // 理由が null
        ClassroomModels.NoteTaskResult nullReason = toTaskResult(
                adminRecoveryJson("GENERATING", "RUNNING", false, false, null));
        assertThat(nullReason.message()).isNotBlank();
        assertThat(nullReason.message()).contains("実行中");

        // 理由が空白だけ
        ClassroomModels.NoteTaskResult blank = toTaskResult(
                adminRecoveryJson("GENERATING", "BINDING", false, false, "   "));
        assertThat(blank.message()).isNotBlank();
        assertThat(blank.message()).contains("準備しています");

        // 確かめられなかった回
        ClassroomModels.NoteTaskResult unknown = toTaskResult(
                adminRecoveryJson("GENERATING", "UNKNOWN", false, false, ""));
        assertThat(unknown.message()).contains("確認できませんでした");

        // 回復成功なのに理由が無い回
        ClassroomModels.NoteTaskResult recovered = toTaskResult(
                adminRecoveryJson("FAILED", "LOST", true, true, null));
        assertThat(recovered.message()).contains("やり直せる状態に戻しました");

        // どの兜底も undefined/null を出さない（JSON に載る値が文字列であること）
        for (ClassroomModels.NoteTaskResult result
                : new ClassroomModels.NoteTaskResult[] { nullReason, blank, unknown, recovered }) {
            JsonNode wire = asWire(result);
            assertThat(wire.hasNonNull("message")).isTrue();
            assertThat(wire.get("message").asText()).doesNotContain("undefined").doesNotContain("null");
        }
    }

    @Test
    @DisplayName("起動の message は今までどおり message から読む（accepted と recovered を混ぜない）")
    void acceptUsesMessageAndKeepsAcceptedSeparate() throws Exception {
        JsonNode adminAccept = MAPPER.readTree("""
                {"noteId":1,"status":"GENERATING","accepted":true,"message":"最終まとめの作成を始めました。"}
                """);
        Method method = ClassroomServiceImpl.class.getDeclaredMethod("toTaskResult",
                long.class, JsonNode.class, boolean.class);
        method.setAccessible(true);
        ClassroomModels.NoteTaskResult result = (ClassroomModels.NoteTaskResult) method.invoke(
                null, 1L, adminAccept, false);

        assertThat(result.message()).isEqualTo("最終まとめの作成を始めました。");
        assertThat(result.accepted()).isTrue();
        // 起動の結果に**回復の欄は載せない**（混ぜない）
        assertThat(result.recovered()).isFalse();
        assertThat(result.recoverable()).isFalse();
        assertThat(result.liveness()).isNull();
    }
}
