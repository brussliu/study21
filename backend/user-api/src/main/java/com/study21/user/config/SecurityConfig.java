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
import org.springframework.web.cors.CorsConfigurationSource;

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
                        .requestMatchers("/api/user/logout", "/api/user/documents/**", "/api/user/document-folders/**",
                                "/api/user/temp-files/**", "/api/user/test-infos/**", "/api/user/link-clips/**",
                                // 自分の情報の修正・パスワード変更（ログイン中のみ）
                                "/api/user/profile", "/api/user/profile/**",
                                // サイト管理・端末コントロール（当面ロールでは分けない。ログイン必須）
                                "/api/user/net-sites", "/api/user/net-sites/**",
                                "/api/user/net-terminals", "/api/user/net-terminals/**",
                                // サイトアクセス履歴・Web閲覧履歴（インターネット利用履歴）。参照のみ
                                "/api/user/net-access-logs", "/api/user/net-access-logs/**",
                                "/api/user/web-browsing-logs", "/api/user/web-browsing-logs/**",
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
        return SecurityConfigUtil.corsConfigurationSource(origins);
    }

    /** パスワードの BCrypt ハッシュ化（登録・ログイン共通）。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
