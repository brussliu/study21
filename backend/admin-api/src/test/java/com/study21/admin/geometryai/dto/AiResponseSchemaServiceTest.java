package com.study21.admin.geometryai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DTO → JSON Schema の生成（{@link AiResponseSchemaService}）。
 *
 * <p>DTO が AI 出力データ構造の唯一の定義なので、ここで確かめるのは:
 * フィールド名・型・{@code @Schema(description)} の説明・required・Enum の値・
 * List / Array・入れ子 DTO が、そのまま JSON Schema に出ること。</p>
 */
class AiResponseSchemaServiceTest {

    private final AiResponseSchemaService service = new AiResponseSchemaService(new ObjectMapper());

    @Test
    void batC51のスキーマに説明と必須と列挙が出る() throws Exception {
        String schema = service.schemaJsonOf(BatC51ResultDto.class);
        JsonNode root = new ObjectMapper().readTree(schema);

        assertThat(root.path("type").asText()).isEqualTo("object");

        // フィールド名と型
        JsonNode properties = root.path("properties");
        assertThat(properties.has("コマンド")).isTrue();
        assertThat(properties.path("コマンド").path("type").asText()).isEqualTo("array");
        assertThat(properties.path("コマンド").path("items").path("type").asText()).isEqualTo("string");
        assertThat(properties.path("タグ").path("type").asText()).isEqualTo("array");
        assertThat(properties.path("図形名").path("type").asText()).isEqualTo("string");

        // @Schema(description) の説明がそのまま出る
        assertThat(properties.path("コマンド").path("description").asText())
                .contains("GeoGebra のコマンド");
        assertThat(properties.path("認識").path("description").asText())
                .contains("画像から読み取った");

        // Enum は値が限定される
        assertThat(properties.path("分類").path("enum")).hasSize(4);
        assertThat(properties.path("分類").path("enum").toString())
                .contains("FIGURE").contains("FUNCTION").contains("MIXED").contains("UNKNOWN");
        assertThat(properties.path("図形種").path("enum").toString())
                .contains("TRIANGLE").contains("CIRCLE").contains("QUAD").contains("OTHER");

        // required は必須の 2 つだけ（title などは必須ではない）
        assertThat(root.path("required").toString()).contains("分類").contains("コマンド");
        assertThat(root.path("required").toString()).doesNotContain("図形名").doesNotContain("メモ");
    }

    @Test
    void batC52のスキーマも同じ仕組みで作れる() throws Exception {
        JsonNode root = new ObjectMapper().readTree(service.schemaJsonOf(BatC52ResultDto.class));

        assertThat(root.path("properties").path("コマンド").path("type").asText()).isEqualTo("array");
        assertThat(root.path("properties").path("説明").path("description").asText())
                .contains("説明");
        assertThat(root.path("required").toString()).contains("コマンド");
    }

    @Test
    void 入れ子のDTOはその場に展開される() throws Exception {
        // 1 回だけ使う型は $ref を使わず、その場に展開される（プロンプトに埋めても読みやすい）
        JsonNode root = new ObjectMapper().readTree(service.schemaJsonOf(Single.class));

        JsonNode inner = root.path("properties").path("inner");
        assertThat(inner.path("type").asText()).isEqualTo("object");
        assertThat(inner.path("description").asText()).isEqualTo("入れ子のオブジェクト");
        assertThat(inner.path("properties").path("name").path("description").asText()).isEqualTo("名前");
        assertThat(inner.path("required").toString()).contains("name");
    }

    @Test
    void 同じ型を2回使うときは定義と参照に分かれる() throws Exception {
        JsonNode root = new ObjectMapper().readTree(service.schemaJsonOf(Outer.class));

        // 繰り返し使う型は $defs にまとまり、$ref から参照される
        assertThat(root.path("$defs").path("Inner").path("properties").path("name").path("description").asText())
                .isEqualTo("名前");
        assertThat(root.path("properties").path("inner").path("$ref").asText())
                .isEqualTo("#/$defs/Inner");
        assertThat(root.path("properties").path("items").path("type").asText()).isEqualTo("array");
        assertThat(root.path("properties").path("items").path("items").path("$ref").asText())
                .isEqualTo("#/$defs/Inner");
        // Enum の値は $defs の中でも限定される
        assertThat(root.path("$defs").path("Inner").path("properties").path("kind").path("enum").toString())
                .contains("SMALL").contains("LARGE");
    }

    @Test
    void プロンプトへ注入する出力形式はDTOから作ったスキーマ本文を含む() {
        String section = service.promptSectionOf(BatC51ResultDto.class);

        assertThat(section).contains("出力形式");
        assertThat(section).contains("\"コマンド\"");        // JSON Schema 本文（DTO 由来）
        assertThat(section).contains("\"分類\"");
        assertThat(section).contains("JSON オブジェクト");
        // 手書きの JSON 例を二重管理しない（スキーマは DTO から生成したものだけ）
        assertThat(section).doesNotContain("\"分類\":\"FIGURE\"");
    }

    @Test
    void バッチコードからDTOを引ける() {
        assertThat(AiResponseDtos.dtoOf("batC51-A")).contains(BatC51AResultDto.class);
        assertThat(AiResponseDtos.dtoOf("batC52")).contains(BatC52ResultDto.class);
        // モードが無い時代の裸の batC51 はバッチごと削除した（DTO の表にも無い）
        assertThat(AiResponseDtos.dtoOf("batC51")).isEmpty();
        assertThat(AiResponseDtos.dtoOf("batC99")).isEmpty();
    }

    /** 入れ子の確認用（本番の DTO は geometry の 2 つだけだが、仕組みは入れ子にも対応する）。 */
    static class Outer {
        @Schema(description = "入れ子のオブジェクト", requiredMode = Schema.RequiredMode.REQUIRED)
        public Inner inner;

        @Schema(description = "入れ子の一覧")
        public List<Inner> items;
    }

    static class Single {
        @Schema(description = "入れ子のオブジェクト", requiredMode = Schema.RequiredMode.REQUIRED)
        public Inner inner;
    }

    static class Inner {
        @Schema(description = "名前", requiredMode = Schema.RequiredMode.REQUIRED)
        public String name;

        @Schema(description = "大きさ")
        public Size kind;
    }

    enum Size {
        /** 小。 */
        SMALL,
        /** 大。 */
        LARGE
    }
}
