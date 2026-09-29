package com.study21.admin.japanesewordai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.admin.geometryai.dto.AiResponseDtos;
import com.study21.admin.geometryai.dto.AiResponseFormatPrompt;
import com.study21.admin.geometryai.dto.AiResponseSchemaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 日本語単語 AI の**出力データ構造は DTO が唯一の定義**であることの確認。
 *
 * <p>DTO から JSON Schema を作り、それを</p>
 * <ol>
 *   <li>AI へ渡すシステムプロンプトへ足す（{@link AiResponseFormatPrompt}）</li>
 *   <li>設定ページの Data TAB に出す（{@code aiDataSchemaPanel.ts} →
 *       {@code data-ai-data-schema-slot} の枠）</li>
 * </ol>
 * <p>の 2 か所で使う。だからプロンプトにも DB にも JSON Schema を二重管理しない。</p>
 *
 * <p>確かめる接縫: <b>4 つのバッチコードに DTO が登録されていて、学習画面（A. 勉強）が要求する
 * 項目がスキーマに出る</b>。DTO から項目を消すとこのテストが落ちるので、画面が空になる前に気づける。</p>
 */
class JapaneseWordAiSchemaTest {

    private static final List<String> BATCH_CODES = List.of("batC41", "batC42", "batC43", "batC44");

    private static final AiResponseSchemaService SCHEMA = new AiResponseSchemaService(new ObjectMapper());

    @Test
    @DisplayName("batC41〜batC44 に出力 DTO が登録されている")
    void registersDtos() {
        for (String batchCode : BATCH_CODES) {
            assertThat(AiResponseDtos.dtoOf(batchCode))
                    .as("%s の出力 DTO", batchCode)
                    .isPresent();
        }
        // 登録した DTO は日本語単語のもの（幾何の DTO と取り違えていない）
        assertThat(AiResponseDtos.dtoOf("batC41").orElseThrow().getName())
                .isEqualTo("com.study21.admin.japanesewordai.dto.BatC41ResultDto");
    }

    @Test
    @DisplayName("batC41 のスキーマに、学習画面（A. 勉強）が使う項目がすべて出る")
    void detailSchemaCoversStudyScreen() {
        Class<?> dto = AiResponseDtos.dtoOf("batC41").orElseThrow();
        String json = SCHEMA.schemaJsonOf(dto);

        assertThat(SCHEMA.schemaOf(dto)).isNotNull();
        // 学習画面が読むキー（DemoDetailContent と同じ名前）
        for (String key : List.of(
                "coreMeaning", "descriptionJa", "descriptionZh", "partOfSpeech", "jlpt", "conjugation",
                "transitivity", "importance",
                "senses", "examples", "patterns", "dialogs", "synonyms", "cautions", "conjugations",
                "transitivityPair", "pronunciation", "collocations", "relatedWords", "usageNotes",
                "memoryHint", "practices")) {
            assertThat(json).as("スキーマのキー %s", key).contains("\"" + key + "\"");
        }
        // 入れ子（配列の要素）も展開されている
        assertThat(json).contains("\"japanese\"").contains("\"chinese\"");
        // `cautions` と `usageNotes` は別物として出る（同じキーに潰れていない）
        assertThat(json).contains("\"cautions\"");
    }

    @Test
    @DisplayName("batC41 では読みを必ず書かせる（読みを持たずに登録された語へ書き戻すため）")
    void requiresReading() {
        JsonNode detail = SCHEMA.schemaOf(AiResponseDtos.dtoOf("batC41").orElseThrow())
                .path("properties").path("detail");

        assertThat(requiredOf(detail))
                .as("detail の必須項目")
                .contains("pronunciation");
        assertThat(requiredOf(detail.path("properties").path("pronunciation")))
                .as("pronunciation の必須項目")
                .contains("reading");
    }

    /** JSON Schema の {@code required} を文字列の一覧にする（無ければ空）。 */
    private static List<String> requiredOf(JsonNode node) {
        List<String> names = new ArrayList<>();
        for (JsonNode name : node.path("required")) {
            names.add(name.asText());
        }
        return names;
    }

    @Test
    @DisplayName("問題（C1/C2・D・E）のスキーマに、4 択の項目と正解がすべて出る")
    void problemSchemaCoversChoices() {
        for (String batchCode : List.of("batC42", "batC43", "batC44")) {
            String json = SCHEMA.schemaJsonOf(AiResponseDtos.dtoOf(batchCode).orElseThrow());
            for (String key : List.of(
                    "questionJapanese", "targetHeading", "targetReading", "audioText",
                    "sentenceJapanese", "sentenceReading", "correctValue", "correctNote",
                    "explanationJapanese", "explanationChinese", "difficulty", "options")) {
                assertThat(json).as("%s のキー %s", batchCode, key).contains("\"" + key + "\"");
            }
        }
        // C は複数（C1 と C2）、D / E は 1 件
        assertThat(SCHEMA.schemaJsonOf(AiResponseDtos.dtoOf("batC42").orElseThrow())).contains("\"problems\"");
        assertThat(SCHEMA.schemaJsonOf(AiResponseDtos.dtoOf("batC43").orElseThrow())).contains("\"problem\"");
    }

    @Test
    @DisplayName("システムプロンプトの後ろに「出力形式（JSON Schema）」を足す")
    void appendsSchemaToSystemPrompt() {
        AiResponseFormatPrompt format = new AiResponseFormatPrompt(SCHEMA);

        String appended = format.appendTo("あなたは専門家です。", "batC41");

        assertThat(appended).startsWith("あなたは専門家です。");
        assertThat(appended).contains("出力形式（JSON Schema）");
        assertThat(appended).contains("coreMeaning");
        // DTO が未登録のバッチには何も足さない（今までどおり動かす）
        assertThat(format.appendTo("そのまま", "batZ99")).isEqualTo("そのまま");
    }
}
