package com.study21.user.reading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 外部の無料辞書・翻訳 API を `java.net.http.HttpClient` で引く実装
 * （2.0 の `WordServiceImpl` の `translateByExcelApi` / `translateByYoudao` と同じ URL）。
 *
 * <ul>
 *   <li>ExcelAPI enja … `https://api.excelapi.org/dictionary/enja?word=<urlencoded>`</li>
 *   <li>有道 suggest … `http://dict.youdao.com/suggest?num=1&doctype=json&q=<urlencoded>`</li>
 *   <li>有道 jsonapi … `http://dict.youdao.com/jsonapi?q=<urlencoded>`</li>
 * </ul>
 *
 * <p>タイムアウトは 5 秒（`study21.reading.dictionary.timeout`）。例外・タイムアウト・
 * 200 以外は**握りつぶして null**（warn ログだけ）。ネットワークが無い環境でも
 * API は 200 を返し、画面は「取得できませんでした」を出す。</p>
 *
 * <p>`enabled=false` にすると一切通信しない（完全オフラインの配備・テスト用）。</p>
 */
@Component
public class ReadingDictionaryHttpClient implements ReadingDictionaryClient {

    private static final Logger log = LoggerFactory.getLogger(ReadingDictionaryHttpClient.class);
    private static final String EXCEL_API_ENJA = "https://api.excelapi.org/dictionary/enja?word=";
    private static final String YOUDAO_SUGGEST = "http://dict.youdao.com/suggest?num=1&doctype=json&q=";
    private static final String YOUDAO_JSONAPI = "http://dict.youdao.com/jsonapi?q=";

    private final HttpClient httpClient;
    private final boolean enabled;
    private final Duration timeout;
    private final String userAgent;

    public ReadingDictionaryHttpClient(
            @Value("${study21.reading.dictionary.enabled:true}") boolean enabled,
            @Value("${study21.reading.dictionary.timeout:5s}") Duration timeout,
            @Value("${study21.reading.dictionary.user-agent:Study21-Reading/1.0}") String userAgent) {
        this.enabled = enabled;
        this.timeout = timeout;
        this.userAgent = userAgent;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String englishToJapanese(String word) {
        return get(EXCEL_API_ENJA + encode(word), "text/plain, */*", "excelapi-enja");
    }

    @Override
    public String englishToChinese(String word) {
        return get(YOUDAO_SUGGEST + encode(word), "application/json", "youdao-suggest");
    }

    @Override
    public String chineseEntry(String word) {
        return get(YOUDAO_JSONAPI + encode(word), "application/json", "youdao-jsonapi");
    }

    private String get(String url, String accept, String provider) {
        if (!enabled) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("Accept", accept)
                    .header("User-Agent", userAgent)
                    .GET()
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("reading dictionary request failed. provider={} status={}", provider,
                        response.statusCode());
                return null;
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("reading dictionary request interrupted. provider={}", provider);
            return null;
        } catch (Exception ex) {
            // 名前解決できない・タイムアウト・不正な URL など。画面は「取得できませんでした」を出す
            log.warn("reading dictionary request error. provider={} message={}", provider, ex.toString());
            return null;
        }
    }

    private static String encode(String word) {
        return URLEncoder.encode(word == null ? "" : word, StandardCharsets.UTF_8);
    }
}
