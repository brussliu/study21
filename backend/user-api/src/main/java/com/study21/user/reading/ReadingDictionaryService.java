package com.study21.user.reading;

/**
 * 語彙・読みの引き当て（閲覧画面の「語彙」「読み方」）。
 *
 * <p>選んだ語を、本の言語に応じて外部の辞書・翻訳 API で引く。結果は
 * `RED_語彙辞書情報` に貯めて次回から外部へ行かない。</p>
 */
public interface ReadingDictionaryService {

    /**
     * 語の意味・読みを引く。
     *
     * <ul>
     *   <li>英語の本 … 日本語訳（ExcelAPI）と中国語訳（有道）</li>
     *   <li>中国語の本 … 拼音と解説（有道）</li>
     *   <li>日本語の本 … 引かない（全部 null）</li>
     * </ul>
     *
     * @throws com.study21.common.core.exception.ValidationException 言語が 3 種類以外・語が空白のとき（400）
     */
    ReadingModels.LookupResult lookup(ReadingModels.LookupRequest request);
}
