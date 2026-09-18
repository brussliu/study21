package com.study21.admin.geometryai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * AI の応答本文を **DTO（{@code @Schema} 付きのクラス）へ読み込む**共通の入り口。
 *
 * <p>DTO が AI 出力データ構造の唯一の定義なので、読み込みもここ 1 か所に集める
 * （batC51 / batC52 で同じ実装を使う）。あわせて、これまで各パーサに重複していた
 * 「OpenAI 互換の本文から content を取り出す」「コードフェンスを剥がす」もここへ集約した。</p>
 *
 * <p>読み込みは寛容にする（モデルの出力は毎回同じ形とは限らないため）:</p>
 * <ul>
 *   <li>キー名 … DTO の {@code @JsonProperty}（日本語）と {@code @JsonAlias}（英語）の両方</li>
 *   <li>Enum … 大文字小文字を無視し、知らない値は {@code null} にする（未知の値を理由に
 *       コマンドまで捨てない）</li>
 *   <li>知らないキーは無視する（DTO に無い項目が混ざっても壊れない）</li>
 * </ul>
 */
public final class AiResponseDtoParser {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);

    private AiResponseDtoParser() {
    }

    /** AI の応答本文（OpenAI 互換でも素の本文でもよい）から DTO を取り出す。読めなければ空。 */
    public static <T> Optional<T> parse(String body, Class<T> dtoClass) {
        return parseContent(assistantContent(body), dtoClass);
    }

    /**
     * すでに取り出した**本文（assistant の content）**から DTO を取り出す。読めなければ空。
     *
     * <p>本文は JSON そのものとは限らない（コードフェンス付き・前後に文がある）ので、
     * フェンスを剥がして最初の {@code {} から最後の {@code }} までを読む。</p>
     */
    public static <T> Optional<T> parseContent(String content, Class<T> dtoClass) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        String text = stripFence(content).trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(MAPPER.readValue(text.substring(start, end + 1), dtoClass));
        } catch (JsonProcessingException cause) {
            return Optional.empty();
        }
    }

    /** OpenAI 互換の {@code choices[0].message.content}（無ければ本文そのもの）。 */
    public static String assistantContent(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode node = root.path("choices").path(0).path("message").path("content");
            if (!node.isMissingNode() && !node.isNull() && node.isTextual()) {
                return node.asText();
            }
            if (root.path("コマンド").isArray() || root.path("commands").isArray()) {
                return body;
            }
            return null;
        } catch (JsonProcessingException cause) {
            return null;
        }
    }

    /** コードフェンス（```json … ```）を剥がす。 */
    static String stripFence(String text) {
        String value = text.trim();
        if (!value.startsWith("```")) {
            return value;
        }
        int firstLineEnd = value.indexOf('\n');
        if (firstLineEnd < 0) {
            return value;
        }
        String rest = value.substring(firstLineEnd + 1);
        int closing = rest.lastIndexOf("```");
        return (closing < 0 ? rest : rest.substring(0, closing)).trim();
    }

    /** DTO を JSON 文字列にする（DB の {@code 提案JSON} などへ入れる用。null の項目は出さない）。 */
    public static String toJson(Object dto) {
        try {
            return MAPPER.writeValueAsString(dto);
        } catch (JsonProcessingException cause) {
            throw new IllegalStateException("AI 出力 DTO を JSON にできませんでした", cause);
        }
    }
}
