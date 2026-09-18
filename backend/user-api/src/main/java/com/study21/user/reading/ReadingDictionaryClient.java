package com.study21.user.reading;

/**
 * 外部の辞書・翻訳 API への問い合わせ（HTTP）を差し替えられるようにする継ぎ目。
 *
 * <p>本番実装は {@link ReadingDictionaryHttpClient}（`java.net.http.HttpClient`）。
 * テストは応答を固定したスタブを差し込むので、**外部ネットワークに依存しない**。</p>
 *
 * <p>どれも「生の応答本文」を返す（解釈は {@link ReadingDictionaryParsers}）。
 * 取得できなかったとき（通信不能・タイムアウト・200 以外）は例外にせず null。</p>
 */
public interface ReadingDictionaryClient {

    /** ExcelAPI（enja）: 英単語の日本語訳。プレーンテキストで返る。 */
    String englishToJapanese(String word);

    /** 有道 suggest: 英単語の中国語の意味。JSON で返る。 */
    String englishToChinese(String word);

    /** 有道 jsonapi: 中国語の語の拼音と解説。JSON で返る。 */
    String chineseEntry(String word);
}
