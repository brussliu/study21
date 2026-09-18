package com.study21.user.config;

import com.study21.common.security.config.SecurityConfigUtil;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * user-api 安全配置：health / system/info / actuator 允许匿名，其余默认拒绝。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/user/health", "/api/user/system/info", "/actuator/health").permitAll()
                        // 保護者・生徒の新規登録 / ログイン / パスワード再設定（認証前の公開エンドポイント）
                        .requestMatchers("/api/user/register", "/api/user/login", "/api/user/password/**").permitAll()
                        // ブラウザ拡張からの受信（セッションではなく接続コードで認証する）。
                        // 画面向けの /browser-extension/registration は従来どおりログイン必須なので、
                        // ここで開けるのはこの 3 つだけにする（順序が意味を持つので authenticated より前に置く）。
                        .requestMatchers("/api/user/browser-extension/register",
                                "/api/user/browser-extension/heartbeat",
                                "/api/user/browser-extension/events").permitAll()
                        .requestMatchers("/api/user/logout", "/api/user/documents/**", "/api/user/document-folders/**",
                                "/api/user/temp-files/**", "/api/user/test-infos/**", "/api/user/link-clips/**",
                                // 自分の情報の修正・パスワード変更（ログイン中のみ）
                                "/api/user/profile", "/api/user/profile/**",
                                // ブラウザ拡張の接続設定（接続コードと接続中の端末。本人のみ）
                                "/api/user/browser-extension", "/api/user/browser-extension/**",
                                // サイト管理・端末コントロール（当面ロールでは分けない。ログイン必須）
                                "/api/user/net-sites", "/api/user/net-sites/**",
                                "/api/user/net-terminals", "/api/user/net-terminals/**",
                                // サイトアクセス履歴・Web閲覧履歴（インターネット利用履歴）。参照のみ
                                "/api/user/net-access-logs", "/api/user/net-access-logs/**",
                                "/api/user/web-browsing-logs", "/api/user/web-browsing-logs/**",
                                // 図形管理（数学勉強＞図形管理）
                                "/api/user/geometry", "/api/user/geometry/**",
                                // AI 生図・AI 画図助手（図形管理の一部。ログイン必須。
                                // anyRequest().denyAll() なので、明示しないと 403 になる）
                                "/api/user/geometry/ai", "/api/user/geometry/ai/**",
                                // 日本語勉強（単語情報管理・単語テスト・単語勉強状況）
                                "/api/user/japanese", "/api/user/japanese/**",
                                // 読書管理（書籍管理・書籍閲覧）。家族で共有する本棚
                                "/api/user/reading", "/api/user/reading/**",
                                // 授業録音 / AI 授業記録（ログイン必須。
                                // anyRequest().denyAll() なので、明示しないと 403 になる）
                                "/api/user/classroom", "/api/user/classroom/**",
                                // 学習日報（閲覧のみ）
                                "/api/user/daily-reports", "/api/user/daily-reports/**",
                                "/api/user/study-monitor", "/api/user/study-monitor/**",
                                "/api/user/games", "/api/user/games/**",
                                // TODO
                                "/api/user/todos", "/api/user/todos/**")
                        .authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(SecurityConfigUtil.jsonAuthenticationEntryPoint())
                        .accessDeniedHandler(SecurityConfigUtil.jsonAccessDeniedHandler()));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${study21.cors.allowed-origins:http://localhost:5173,http://localhost:5174}") String allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        // ブラウザ拡張からの受信 API は、リクエストの Origin が chrome-extension://<拡張のID> になる。
        // 拡張の ID は「パッケージ化されていない拡張機能」として読み込むたびに変わり、事前に固定できない。
        // この 3 つのパスだけはパターンで許可する（画面向けの API は従来どおり明示リストのまま）。
        CorsConfiguration extension = new CorsConfiguration();
        extension.setAllowedOriginPatterns(List.of("chrome-extension://*"));
        extension.setAllowedMethods(List.of("POST", "OPTIONS"));
        extension.setAllowedHeaders(List.of("*"));
        extension.setMaxAge(3600L);

        // 登録順が意味を持つ: UrlBasedCorsConfigurationSource は先に登録した設定から順に照合し、
        // 最初に当たったものを返す（"一番詳しいパターン" ではない）。
        // そのため個別のパスを "/**" より先に登録する。逆にすると拡張の Origin が弾かれる。
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/user/browser-extension/register", extension);
        source.registerCorsConfiguration("/api/user/browser-extension/heartbeat", extension);
        source.registerCorsConfiguration("/api/user/browser-extension/events", extension);
        source.registerCorsConfiguration("/**", SecurityConfigUtil.corsConfiguration(origins));
        return source;
    }

    /** パスワードの BCrypt ハッシュ化（登録・ログイン共通）。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
