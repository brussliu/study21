package com.study21.admin.geometryai;

import com.study21.admin.geometryai.dto.BatC51ResultDto;
import com.study21.admin.geometryai.dto.FigureKind;
import com.study21.admin.geometryai.dto.FigureSubKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI の応答からコマンドと提案を取り出す（設計 §6.3）。
 *
 * ・コードフェンス付きの JSON
 * ・壊れた JSON（→ コマンド行として拾い直す。それも無ければ INVALID_JSON）
 * ・`COMMAND` 形式（コマンド行のみ）
 * ・空応答
 */
class GeometryAiResponseParserTest {

    private static String openAiBody(String content) {
        return "{\"choices\":[{\"message\":{\"content\":" + json(content) + "}}]}";
    }

    private static String json(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                default -> builder.append(c);
            }
        }
        return builder.append('"').toString();
    }

    @Test
    void readsJsonInsideCodeFences() {
        String content = """
                ```json
                {"分類":"FIGURE","図形種":"TRIANGLE","図形名":"三角形ABC","タグ":["三角形","作図"],
                 "メモ":"画像から作成","認識":"AB=5","コマンド":["A = (0, 0)","Polygon(A, B, C)"]}
                ```""";

        GeometryAiResponseParser.ParseResult result =
                GeometryAiResponseParser.parse(openAiBody(content), "JSON");

        assertThat(result.isSuccess()).isTrue();
        // 取り出した結果は DTO（batC51 の出力データ構造の唯一の定義）
        BatC51ResultDto output = result.output();
        assertThat(output.getKind()).isEqualTo(FigureKind.FIGURE);
        assertThat(output.getSubKind()).isEqualTo(FigureSubKind.TRIANGLE);
        assertThat(output.getTitle()).isEqualTo("三角形ABC");
        assertThat(output.getTags()).containsExactly("三角形", "作図");
        assertThat(output.getMemo()).isEqualTo("画像から作成");
        assertThat(output.getRecognized()).isEqualTo("AB=5");
        assertThat(GeometryAiResponseParser.commandsOf(output)).containsExactly("A = (0, 0)", "Polygon(A, B, C)");
    }

    @Test
    void fallsBackToCommandLinesWhenJsonIsBroken() {
        String content = """
                {"分類":"FIGURE","コマンド":["A = (0, 0)",,
                A = (0, 0)
                B = (5, 0)
                Segment(A, B)""";

        GeometryAiResponseParser.ParseResult result =
                GeometryAiResponseParser.parse(openAiBody(content), "JSON");

        assertThat(result.isSuccess()).isTrue();
        assertThat(GeometryAiResponseParser.commandsOf(result.output()))
                .containsExactly("A = (0, 0)", "B = (5, 0)", "Segment(A, B)");
    }

    @Test
    void reportsInvalidJsonWhenNothingCanBeUsed() {
        GeometryAiResponseParser.ParseResult result =
                GeometryAiResponseParser.parse(openAiBody("{ \"分類\": "), "JSON");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_JSON");
    }

    @Test
    void readsCommandFormatAndKeepsTheJapaneseDescription() {
        String content = """
                説明: 三角形 ABC に垂線を引きます。
                A = (0, 0)
                B = (5, 0)
                C = (0, 4)
                # 補助線
                Polygon(A, B, C)""";

        GeometryAiResponseParser.ParseResult result =
                GeometryAiResponseParser.parse(openAiBody(content), "COMMAND");

        assertThat(result.isSuccess()).isTrue();
        assertThat(GeometryAiResponseParser.commandsOf(result.output())).containsExactly(
                "A = (0, 0)", "B = (5, 0)", "C = (0, 4)", "Polygon(A, B, C)");
    }

    @Test
    void reportsEmptyResponse() {
        GeometryAiResponseParser.ParseResult result =
                GeometryAiResponseParser.parse(openAiBody("   "), "JSON");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).isEqualTo("EMPTY_RESPONSE");
    }

    @Test
    void 英語キーのJSONもDTOで読める() {
        GeometryAiResponseParser.ParseResult result = GeometryAiResponseParser.parse(
                "{\"kind\":\"FIGURE\",\"subKind\":\"CIRCLE\",\"commands\":[\"Circle((0, 0), 3)\"],"
                        + "\"description\":\"円をかきます\"}", "JSON");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output().getKind()).isEqualTo(FigureKind.FIGURE);
        assertThat(result.output().getSubKind()).isEqualTo(FigureSubKind.CIRCLE);
        assertThat(result.output().getDescription()).isEqualTo("円をかきます");
    }

    @Test
    void 知らない列挙値でもコマンドは捨てない() {
        // 「分類」が想定外でも、コマンドが読めていれば生成は続ける（DTO は寛容に読む）
        GeometryAiResponseParser.ParseResult result = GeometryAiResponseParser.parse(
                openAiBody("{\"分類\":\"SOMETHING\",\"コマンド\":[\"A = (0, 0)\"]}"), "JSON");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output().getKind()).isNull();
        assertThat(GeometryAiResponseParser.commandsOf(result.output())).containsExactly("A = (0, 0)");
    }

    @Test
    void DTOに無いキーは無視する() {
        GeometryAiResponseParser.ParseResult result = GeometryAiResponseParser.parse(
                openAiBody("{\"コマンド\":[\"A = (0, 0)\"],\"余計\":{\"x\":1}}"), "JSON");

        assertThat(result.isSuccess()).isTrue();
        assertThat(GeometryAiResponseParser.commandsOf(result.output())).containsExactly("A = (0, 0)");
    }

    @Test
    void readsJsonReturnedDirectlyWithoutTheOpenAiWrapper() {
        GeometryAiResponseParser.ParseResult result = GeometryAiResponseParser.parse(
                "{\"コマンド\":[\"Circle((0, 0), 3)\"],\"分類\":\"FUNCTION\"}", "JSON");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output().getKind()).isEqualTo(FigureKind.FUNCTION);
        assertThat(GeometryAiResponseParser.commandsOf(result.output())).containsExactly("Circle((0, 0), 3)");
    }
}
