package com.study21.user.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CORS の設定（user-api）。
 *
 * ブラウザ拡張からの受信 API（/api/user/browser-extension/register|heartbeat|events）は、
 * Origin が `chrome-extension://<拡張のID>` になる。拡張の ID は読み込むたびに変わるため
 * パターンで許可している。
 *
 * ここが壊れると、サーバは 403「Invalid CORS request」を返し、拡張は履歴を送れなくなる
 * （画面から見ると「接続中の端末」が永久に空になる）。実際に起きたのは
 * 「UrlBasedCorsConfigurationSource は一番詳しいパターンではなく**先に登録した方**を返す」
 * という挙動で、個別パスの設定が "/**" に食われていた。
 */
class SecurityConfigCorsTest {

    private static final String PAGE_ORIGIN = "http://127.0.0.1:5199";
    private static final String EXTENSION_ORIGIN = "chrome-extension://abcdefghijklmnopabcdefghijklmnop";

    private final CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource(PAGE_ORIGIN);

    private CorsConfiguration configurationFor(String method, String path, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Origin", origin);
        return source.getCorsConfiguration(request);
    }

    @Test
    void allowsExtensionOriginForIngestPaths() {
        for (String path : new String[]{
                "/api/user/browser-extension/register",
                "/api/user/browser-extension/heartbeat",
                "/api/user/browser-extension/events"}) {
            CorsConfiguration configuration = configurationFor("POST", path, EXTENSION_ORIGIN);
            assertThat(configuration).as(path).isNotNull();
            assertThat(configuration.checkOrigin(EXTENSION_ORIGIN)).as(path).isEqualTo(EXTENSION_ORIGIN);
        }
    }

    /** 拡張の受信 API でも、無関係なサイトの Origin は許可しない。 */
    @Test
    void rejectsOtherOriginsForIngestPaths() {
        CorsConfiguration configuration = configurationFor("POST",
                "/api/user/browser-extension/events", "https://evil.example.com");

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("https://evil.example.com")).isNull();
    }

    /** 画面向けの API は従来どおり（明示リストのみ・Cookie あり）。 */
    @Test
    void keepsPageOriginsForScreenApis() {
        CorsConfiguration configuration = configurationFor("GET", "/api/user/web-browsing-logs", PAGE_ORIGIN);

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin(PAGE_ORIGIN)).isEqualTo(PAGE_ORIGIN);
        assertThat(configuration.checkOrigin("https://evil.example.com")).isNull();
        assertThat(configuration.getAllowCredentials()).isTrue();
        // 画面から拡張の受信 API を叩く必要はない（別の設定になっている）
        assertThat(configurationFor("GET", "/api/user/web-browsing-logs", EXTENSION_ORIGIN)
                .checkOrigin(EXTENSION_ORIGIN)).isNull();
    }
}
