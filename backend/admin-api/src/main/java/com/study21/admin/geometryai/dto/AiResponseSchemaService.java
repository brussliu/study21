package com.study21.admin.geometryai.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import org.springframework.stereotype.Component;

/**
 * AI の出力 DTO から **JSON Schema を自動生成**する（DTO が唯一の定義）。
 *
 * <p>生成した 1 つの結果を次の 2 か所で使う。だからプロンプトや DB に JSON Schema を
 * 二重管理しなくてよい:</p>
 * <ol>
 *   <li>AI へ渡すプロンプトの「出力形式」({@link #promptSectionOf(Class)})</li>
 *   <li>設定ページの Data TAB（{@link #schemaJsonOf(Class)}。DTO の構造の可視化）</li>
 * </ol>
 *
 * <p>生成規則:</p>
 * <ul>
 *   <li>{@code @Schema(description=...)} → {@code description}（全フィールド必須の運用）</li>
 *   <li>{@code @Schema(requiredMode = REQUIRED)} → {@code required}</li>
 *   <li>Enum → {@code enum}（値の列挙）。クラス／定数の説明も反映する</li>
 *   <li>入れ子 DTO と {@code List<…>} → 入れ子の {@code object} / {@code array} へ展開
 *       （{@link OptionPreset#PLAIN_JSON} なので {@code $ref} を使わず、その場で展開される）</li>
 *   <li>Jackson の {@code @JsonProperty} / {@code @JsonAlias} などの扱いは JacksonModule に任せる</li>
 * </ul>
 */
@Component
public class AiResponseSchemaService {

    /** Data TAB の見出しと、プロンプトへ注入する節の見出しに使う。 */
    private static final String PROMPT_HEADING = "## 出力形式（JSON Schema）";

    private final ObjectMapper objectMapper;
    private final SchemaGenerator generator;

    public AiResponseSchemaService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SchemaGeneratorConfig config = new SchemaGeneratorConfigBuilder(
                objectMapper, SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .with(new JacksonModule())
                .with(new Swagger2Module())
                // 画面とプロンプトに出す素の形にする（$schema・$id は付けない）
                .without(Option.SCHEMA_VERSION_INDICATOR)
                .build();
        this.generator = new SchemaGenerator(config);
    }

    /** DTO から JSON Schema を生成する（{@code $schema} などは付けない素の形）。 */
    public ObjectNode schemaOf(Class<?> dtoClass) {
        return generator.generateSchema(dtoClass);
    }

    /** DTO から生成した JSON Schema（整形済みテキスト。Data TAB と画面の表示に使う）。 */
    public String schemaJsonOf(Class<?> dtoClass) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schemaOf(dtoClass));
        } catch (com.fasterxml.jackson.core.JsonProcessingException cause) {
            // DTO から生成した ObjectNode なので通常起こらない
            throw new IllegalStateException("AI 出力スキーマを文字列にできませんでした: " + dtoClass.getName(), cause);
        }
    }

    /**
     * プロンプトへ注入する「出力形式」の節を作る。
     *
     * <p>本文は DTO から生成した JSON Schema そのもの。手書きの JSON 例は持たない。
     * ここに書く文は DTO に依存しない共通の言い回しにしてある（どのバッチでも同じ節を使う）。</p>
     */
    public String promptSectionOf(Class<?> dtoClass) {
        return PROMPT_HEADING + "\n"
                + "返答は次の JSON Schema に従う **JSON オブジェクト 1 個だけ**にしてください。\n"
                + "- 前後に説明文・Markdown・コードフェンス（```）を付けない。\n"
                + "- これより上に「コマンド列だけを出力する」等の形式の指定があっても、この Schema を優先する。\n"
                + "- Schema の description が、それぞれの項目に何を書くかの説明です。\n"
                + schemaJsonOf(dtoClass) + "\n";
    }
}
