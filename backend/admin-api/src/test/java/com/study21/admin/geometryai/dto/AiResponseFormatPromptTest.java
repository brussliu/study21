package com.study21.admin.geometryai.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * システムプロンプトへ「出力形式（DTO から生成した JSON Schema）」を足す。
 * プロンプト側に JSON を手書きしないための仕組みなので、足した文が DTO 由来であることを確かめる。
 */
class AiResponseFormatPromptTest {

    private final AiResponseFormatPrompt prompt =
            new AiResponseFormatPrompt(new AiResponseSchemaService(new ObjectMapper()));

    @Test
    void batC51の出力形式を足す() {
        String result = prompt.appendTo("あなたは作図アシスタントです。", "batC51");

        assertThat(result).startsWith("あなたは作図アシスタントです。");
        assertThat(result).contains("## 出力形式（JSON Schema）");
        assertThat(result).contains("\"コマンド\"");   // DTO から生成したスキーマ本文
        assertThat(result).contains("JSON オブジェクト");
    }

    @Test
    void batC52の出力形式を足す() {
        String result = prompt.appendTo("あなたは作図を直すアシスタントです。", "batC52");

        assertThat(result).contains("## 出力形式（JSON Schema）");
        assertThat(result).contains("\"説明\"");
    }

    @Test
    void 未登録のバッチコードでは何も足さない() {
        assertThat(prompt.appendTo("そのまま", "batC99")).isEqualTo("そのまま");
    }

    @Test
    void システムプロンプトが空でも出力形式だけは渡す() {
        String result = prompt.appendTo(null, "batC51");

        assertThat(result).startsWith("## 出力形式（JSON Schema）");
        assertThat(result).contains("\"コマンド\"");
    }
}
