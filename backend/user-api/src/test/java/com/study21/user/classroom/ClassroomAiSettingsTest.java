package com.study21.user.classroom;

import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ClassroomAiSettings} のアクセサ（設定値の解釈）のテスト。
 *
 * <p>DB に依存しないよう、{@code Snapshot} を直接組み立ててアクセサだけを検証する。</p>
 */
class ClassroomAiSettingsTest {

    private static ClassroomAiSettings.Snapshot snapshot(Map<String, String> values) {
        return new ClassroomAiSettings.Snapshot(new LinkedHashMap<>(values));
    }

    private static ClassroomAiSettings settings() {
        return new ClassroomAiSettings(null, false);
    }

    @Test
    void enabledDefaultsToTrue() {
        // 「有効／無効」の設定項目は画面から外したので、未設定は有効として扱う
        assertTrue(settings().enabled(snapshot(Map.of())));
    }

    @Test
    void enabledReadsTrue() {
        assertTrue(settings().enabled(snapshot(Map.of(ClassroomAiSettings.KEY_ENABLED, "true"))));
    }

    @Test
    void enabledReadsFalse() {
        assertFalse(settings().enabled(snapshot(Map.of(ClassroomAiSettings.KEY_ENABLED, "false"))));
    }

    @Test
    void chunkSecondsUsesSeedFallback() {
        assertEquals(20, settings().chunkSeconds(snapshot(Map.of())));
    }

    @Test
    void chunkSecondsReadsValue() {
        assertEquals(10, settings().chunkSeconds(snapshot(Map.of(ClassroomAiSettings.KEY_CHUNK_SECONDS, "10"))));
    }

    @Test
    void triggerKeywordsSplitsByCommaAndFullWidthComma() {
        assertEquals(List.of("宿題", "試験の重点"),
                settings().triggerKeywords(snapshot(Map.of(ClassroomAiSettings.KEY_TRIGGER_KEYWORDS, "宿題,試験の重点"))));
    }

    @Test
    void sttLanguageCodeIsFixedInCode() {
        // 言語コードはコード側で固定（画面では設定しない）
        ClassroomAiSettings.Snapshot empty = snapshot(Map.of());
        assertEquals("zh-CN", settings().sttLanguageCode(empty, "zh"));
        assertEquals("ja-JP", settings().sttLanguageCode(empty, "ja"));
        assertEquals("en-US", settings().sttLanguageCode(empty, "en"));
        assertEquals("zh-CN", settings().sttLanguageCode(empty, "zh-en"));
        assertEquals("ja-JP", settings().sttLanguageCode(empty, "ja-en"));
        // 「自動」は廃止。未指定・旧 auto は日本語として扱う
        assertEquals("ja-JP", settings().sttLanguageCode(empty, null));
        assertEquals("ja-JP", settings().sttLanguageCode(empty, "auto"));
    }

    @Test
    void sttAlternativeLanguageCodeIsOnlyForMixedModes() {
        assertEquals("en-US", settings().sttAlternativeLanguageCode("zh-en"));
        assertEquals("en-US", settings().sttAlternativeLanguageCode("ja-en"));
        assertNull(settings().sttAlternativeLanguageCode("ja"));
        assertNull(settings().sttAlternativeLanguageCode(null));
    }

    @Test
    void maxRecordingMinutesUsesSeedFallback() {
        assertEquals(120, settings().maxRecordingMinutes(snapshot(Map.of())));
    }

    @Test
    @DisplayName("STT プロバイダー = browser のときはブラウザ認識（サーバーは STT を呼ばない）")
    void browserSttIsSelectedByProvider() {
        ClassroomAiSettings.Snapshot browser = new ClassroomAiSettings.Snapshot(
                Map.of(ClassroomAiSettings.KEY_STT_PROVIDER, "browser"));

        assertThat(settings().browserStt(browser)).isTrue();
        // モデル・Endpoint・API Key が無くても接続情報の解決で落ちない（呼ばないので要らない）
        ClassroomAiSettings.SttConnection connection = settings().resolveStt(browser);
        assertThat(connection.provider()).isEqualTo("browser");
        assertThat(connection.apiKey()).isNull();

        ClassroomAiSettings.Snapshot google = new ClassroomAiSettings.Snapshot(
                Map.of(ClassroomAiSettings.KEY_STT_PROVIDER, "google"));
        assertThat(settings().browserStt(google)).isFalse();
    }

    @Test
    @DisplayName("STT = google は「AIモデル」ページの Google Speech-to-Text の設定を使う")
    void googleSttUsesAiModelPageConnection() {
        ClassroomAiSettings.SttConnection connection = settings().resolveStt(snapshot(Map.of(
                ClassroomAiSettings.KEY_STT_PROVIDER, "google",
                ClassroomAiSettings.KEY_GOOGLE_STT_MODEL, "latest_long",
                ClassroomAiSettings.KEY_GOOGLE_STT_URL, "https://speech.googleapis.com/v1/speech:recognize",
                ClassroomAiSettings.KEY_GOOGLE_STT_API_KEY, "google-key")));

        assertThat(connection.provider()).isEqualTo("google");
        assertThat(connection.model()).isEqualTo("latest_long");
        assertThat(connection.url()).isEqualTo("https://speech.googleapis.com/v1/speech:recognize");
        assertThat(connection.apiKey()).isEqualTo("google-key");
    }

    @Test
    @DisplayName("STT = google は未設定のモデル・URLに既定値を使い、API Key が無ければ理由を出す")
    void googleSttFallsBackToSeedValues() {
        // モデルと URL は seed と同じ既定値を使う（設定を空にしても動く）
        ClassroomAiSettings.Snapshot withKey = snapshot(new LinkedHashMap<>(Map.of(
                ClassroomAiSettings.KEY_STT_PROVIDER, "google",
                ClassroomAiSettings.KEY_GOOGLE_STT_API_KEY, "google-key")));
        ClassroomAiSettings.SttConnection connection = settings().resolveStt(withKey);
        assertThat(connection.model()).isEqualTo("latest_long");
        assertThat(connection.url()).isEqualTo("https://speech.googleapis.com/v1/speech:recognize");

        // API Key は seed しないので、未設定なら「どこで設定するか」を日本語で案内する
        ClassroomAiSettings.Snapshot withoutKey = snapshot(Map.of(ClassroomAiSettings.KEY_STT_PROVIDER, "google"));
        assertThatThrownBy(() -> settings().resolveStt(withoutKey))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("AI_GOOGLE_STT_API_KEY")
                .hasMessageContaining("AIモデル");
    }

    @Test
    @DisplayName("STT = alibaba は「AIモデル」ページの Alibaba Paraformer-Realtime-V2 の設定を使う")
    void alibabaSttUsesAiModelPageConnection() {
        ClassroomAiSettings.Snapshot withKey = snapshot(new LinkedHashMap<>(Map.of(
                ClassroomAiSettings.KEY_STT_PROVIDER, "alibaba",
                ClassroomAiSettings.KEY_ALIBABA_STT_API_KEY, "dashscope-key")));
        ClassroomAiSettings.SttConnection connection = settings().resolveStt(withKey);
        assertThat(connection.provider()).isEqualTo("alibaba");
        // モデルと URL も seed と同じ既定値（Realtime の WebSocket）
        assertThat(connection.model()).isEqualTo("paraformer-realtime-v2");
        assertThat(connection.url()).isEqualTo("wss://dashscope.aliyuncs.com/api-ws/v1/inference");
        assertThat(connection.apiKey()).isEqualTo("dashscope-key");

        // 設定した値が優先される
        ClassroomAiSettings.SttConnection custom = settings().resolveStt(snapshot(new LinkedHashMap<>(Map.of(
                ClassroomAiSettings.KEY_STT_PROVIDER, "alibaba",
                ClassroomAiSettings.KEY_ALIBABA_STT_MODEL, "paraformer-realtime-v1",
                ClassroomAiSettings.KEY_ALIBABA_STT_URL, "wss://example.test/asr",
                ClassroomAiSettings.KEY_ALIBABA_STT_API_KEY, "dashscope-key"))));
        assertThat(custom.model()).isEqualTo("paraformer-realtime-v1");
        assertThat(custom.url()).isEqualTo("wss://example.test/asr");
    }

    @Test
    @DisplayName("STT = alibaba で API Key が無ければ、どこで設定するかを案内する")
    void alibabaSttWithoutApiKeyFails() {
        ClassroomAiSettings.Snapshot withoutKey = snapshot(Map.of(ClassroomAiSettings.KEY_STT_PROVIDER, "alibaba"));
        assertThatThrownBy(() -> settings().resolveStt(withoutKey))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("AI_ALIBABA_STT_API_KEY")
                .hasMessageContaining("AIモデル");
    }

    @Test
    @DisplayName("旧プロバイダー（whisper など）は今までどおり CLASSROOM_AI の接続情報を使う（互換）")
    void legacyProviderStillUsesClassroomKeys() {
        ClassroomAiSettings.SttConnection connection = settings().resolveStt(snapshot(new LinkedHashMap<>(Map.of(
                ClassroomAiSettings.KEY_STT_PROVIDER, "whisper",
                ClassroomAiSettings.KEY_STT_MODEL, "whisper-1",
                ClassroomAiSettings.KEY_STT_ENDPOINT, "https://api.openai.com/v1/audio/transcriptions",
                ClassroomAiSettings.KEY_STT_API_KEY, "sk-1"))));

        assertThat(connection.provider()).isEqualTo("whisper");
        assertThat(connection.model()).isEqualTo("whisper-1");
        assertThat(connection.url()).isEqualTo("https://api.openai.com/v1/audio/transcriptions");
    }
}
