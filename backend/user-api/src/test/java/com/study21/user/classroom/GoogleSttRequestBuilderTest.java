package com.study21.user.classroom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GoogleSttRequestBuilder} のリクエスト組み立て（**ネットワーク不要**）。
 *
 * <p>URL への {@code key=} 付与、エンコード判定、JSON 本文の各フィールド（languageCode /
 * model / encoding / audio.content の base64 / alternativeLanguageCodes）を検証する。</p>
 */
class GoogleSttRequestBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ClassroomSttClient.SttRequest request(String languageCode, String alternativeLanguageCode,
                                                         String mime, String model) {
        return new ClassroomSttClient.SttRequest(
                "google", model, "https://speech.googleapis.com/v1/speech:recognize", "test-key",
                languageCode, alternativeLanguageCode, new byte[]{1, 2, 3}, mime, 60);
    }

    // ------------------------------------------------------------ 音声形式（画面が作る PCM）

    @Test
    void rawPcmIsSentAsLinear16WithSampleRate() throws Exception {
        // 画面は分塊ごとに「ヘッダ無しの 16bit PCM（audio/L16）」を作って送る。
        // ヘッダが無いので sampleRateHertz を付けないと Google は解釈できない
        assertThat(GoogleSttRequestBuilder.encodingOf("audio/L16")).isEqualTo("LINEAR16");
        assertThat(GoogleSttRequestBuilder.sampleRateOf("audio/L16")).isEqualTo(16000);

        JsonNode body = MAPPER.readTree(
                GoogleSttRequestBuilder.buildBody(request("ja-JP", "en-US", "audio/L16", "latest_long")));
        assertThat(body.path("config").path("encoding").asText()).isEqualTo("LINEAR16");
        assertThat(body.path("config").path("sampleRateHertz").asInt()).isEqualTo(16000);
    }

    @Test
    void containerFormatsDoNotCarrySampleRate() throws Exception {
        // webm / ogg / mp3 はコンテナ・bitstream から読めるので sampleRateHertz は付けない
        assertThat(GoogleSttRequestBuilder.sampleRateOf("audio/webm")).isNull();
        assertThat(GoogleSttRequestBuilder.sampleRateOf("audio/ogg")).isNull();
        JsonNode body = MAPPER.readTree(
                GoogleSttRequestBuilder.buildBody(request("ja-JP", null, "audio/webm", "latest_long")));
        assertThat(body.path("config").has("sampleRateHertz")).isFalse();
    }

    // ------------------------------------------------------------ URL

    @Test
    void appendsApiKeyAsQueryParam() {
        URI uri = GoogleSttRequestBuilder.buildUri(
                "https://speech.googleapis.com/v1/speech:recognize", "test-key");
        assertThat(uri.toString()).isEqualTo(
                "https://speech.googleapis.com/v1/speech:recognize?key=test-key");
    }

    @Test
    void appendsKeyWithAmpersandWhenQueryExists() {
        URI uri = GoogleSttRequestBuilder.buildUri(
                "https://speech.googleapis.com/v1/speech:recognize?x=1", "test-key");
        assertThat(uri.toString()).isEqualTo(
                "https://speech.googleapis.com/v1/speech:recognize?x=1&key=test-key");
    }

    @Test
    void doesNotDuplicateExistingKey() {
        URI uri = GoogleSttRequestBuilder.buildUri(
                "https://speech.googleapis.com/v1/speech:recognize?key=already", "test-key");
        assertThat(uri.toString()).isEqualTo(
                "https://speech.googleapis.com/v1/speech:recognize?key=already");
    }

    // ------------------------------------------------------------ エンコード

    @Test
    void encodingByMime() {
        assertThat(GoogleSttRequestBuilder.encodingOf("audio/webm")).isEqualTo("WEBM_OPUS");
        assertThat(GoogleSttRequestBuilder.encodingOf("video/webm")).isEqualTo("WEBM_OPUS");
        assertThat(GoogleSttRequestBuilder.encodingOf("audio/ogg")).isEqualTo("OGG_OPUS");
        assertThat(GoogleSttRequestBuilder.encodingOf("audio/mpeg")).isEqualTo("MP3");
        assertThat(GoogleSttRequestBuilder.encodingOf(null)).isEqualTo("WEBM_OPUS");
        // mp4/AAC は Google v1 が直接受け付けない
        assertThat(GoogleSttRequestBuilder.encodingOf("audio/mp4")).isNull();
        assertThat(GoogleSttRequestBuilder.encodingOf("video/mp4")).isNull();
    }

    // ------------------------------------------------------------ 本文

    @Test
    void buildBodyContainsExpectedFields() throws Exception {
        String body = GoogleSttRequestBuilder.buildBody(request("ja-JP", null, "audio/webm", null));
        JsonNode root = MAPPER.readTree(body);

        assertThat(root.path("config").path("encoding").asText()).isEqualTo("WEBM_OPUS");
        assertThat(root.path("config").path("languageCode").asText()).isEqualTo("ja-JP");
        assertThat(root.path("config").path("model").asText()).isEqualTo("latest_long");
        assertThat(root.path("config").path("enableAutomaticPunctuation").asBoolean()).isTrue();
        assertThat(root.path("config").has("alternativeLanguageCodes")).isFalse();
        // Opus はコンテナがサンプルレートを持つので送らない
        assertThat(root.path("config").has("sampleRateHertz")).isFalse();

        byte[] decoded = Base64.getDecoder().decode(root.path("audio").path("content").asText());
        assertThat(decoded).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }

    @Test
    void buildBodyUsesConfiguredModel() throws Exception {
        String body = GoogleSttRequestBuilder.buildBody(request("ja-JP", null, "audio/webm", "latest_short"));
        JsonNode root = MAPPER.readTree(body);
        assertThat(root.path("config").path("model").asText()).isEqualTo("latest_short");
    }

    @Test
    void buildBodyAddsAlternativeLanguageCode() throws Exception {
        String body = GoogleSttRequestBuilder.buildBody(request("ja-JP", "en-US", "audio/webm", null));
        JsonNode root = MAPPER.readTree(body);
        assertThat(root.path("config").path("languageCode").asText()).isEqualTo("ja-JP");
        assertThat(root.path("config").path("alternativeLanguageCodes").get(0).asText()).isEqualTo("en-US");
    }

    @Test
    void buildBodyDefaultsLanguageToJapaneseWhenBlank() throws Exception {
        String body = GoogleSttRequestBuilder.buildBody(request("", null, "audio/webm", null));
        JsonNode root = MAPPER.readTree(body);
        assertThat(root.path("config").path("languageCode").asText()).isEqualTo("ja-JP");
    }
}
