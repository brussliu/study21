package com.study21.common.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.common.core.api.ApiResponse;
import com.study21.common.core.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Security 基础配置工具。提供统一 JSON 的 401/403 处理器与 CORS 配置源。
 */
public final class SecurityConfigUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SecurityConfigUtil() {
    }

    public static AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) ->
                writeError(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED);
    }

    public static AccessDeniedHandler jsonAccessDeniedHandler() {
        return (HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) ->
                writeError(response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
    }

    public static CorsConfigurationSource corsConfigurationSource(List<String> allowedOrigins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration(allowedOrigins));
        return source;
    }

    /**
     * 画面（ブラウザ）からのリクエスト用の CORS 設定。
     *
     * <p>パスごとに設定を変えたいとき（例: ブラウザ拡張からの受信 API だけ別扱いにする）は
     * これを使って {@link UrlBasedCorsConfigurationSource} へ自分で登録する。
     * 注意: 複数の設定が同じパスに当たる場合、Spring は「一番詳しいパターン」ではなく
     * <b>先に登録した方</b>を返す（{@code UrlBasedCorsConfigurationSource} は登録順に照合する）。
     * 個別のパスは {@code "/**"} より先に登録すること。</p>
     */
    public static CorsConfiguration corsConfiguration(List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // セッションCookieを利用するため。allowedOrigins は明示リストであり "*" は許可しない。
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        return configuration;
    }

    private static void writeError(HttpServletResponse response, HttpStatus status, ErrorCode code) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.error(code.code(), code.defaultMessage());
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
    }
}
