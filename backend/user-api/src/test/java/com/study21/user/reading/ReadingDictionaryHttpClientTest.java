package com.study21.user.reading;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本番の辞書クライアント（{@link ReadingDictionaryHttpClient}）の検証。
 *
 * <p>**外部ネットワークには出ない**。`enabled=false` のときは一切通信せず null を返すこと
 * （＝完全オフラインの配備でも API は 200 で「取得できませんでした」を返せること）を固定する。
 * 実際の応答の解釈は {@link ReadingDictionaryParsersTest}（実測した応答）が受け持つ。</p>
 */
class ReadingDictionaryHttpClientTest {

    @Test
    void returnsNothingWithoutTouchingTheNetworkWhenDisabled() {
        ReadingDictionaryHttpClient client =
                new ReadingDictionaryHttpClient(false, Duration.ofSeconds(1), "Study21-Test/1.0");

        assertThat(client.englishToJapanese("hello")).isNull();
        assertThat(client.englishToChinese("hello")).isNull();
        assertThat(client.chineseEntry("汉字")).isNull();
    }

    /** 空の語でも例外にしない（URL は組める）。 */
    @Test
    void doesNotThrowForEmptyWord() {
        ReadingDictionaryHttpClient client =
                new ReadingDictionaryHttpClient(false, Duration.ofSeconds(1), "Study21-Test/1.0");

        assertThat(client.englishToJapanese("")).isNull();
        assertThat(client.chineseEntry(null)).isNull();
    }
}
