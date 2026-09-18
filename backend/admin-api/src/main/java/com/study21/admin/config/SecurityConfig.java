package com.study21.admin.config;

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
 * admin-api 安全配置：health / system/info / actuator 允许匿名，其余默认拒绝。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/admin/health", "/api/admin/system/info", "/actuator/health").permitAll()
                        // 管理者ログイン（認証前の公開エンドポイント）
                        .requestMatchers("/api/admin/login").permitAll()
                        // バッチ管理・システム設定（管理者機能）。
                        // 注: 本スケルトンは認証未実装のため当面許可。認証導入時は ADMIN ロール必須とする。
                        .requestMatchers("/api/admin/batch/**", "/api/admin/settings/**", "/api/admin/setting/**").permitAll()
                        // 授業録音の前置詞プリセット管理（設定画面のサブパネル。GLOBAL スコープのみ）
                        .requestMatchers("/api/admin/classroom-presets", "/api/admin/classroom-presets/**").permitAll()
                        // AI 生図の起動（画面から 1 回だけ呼ぶ）。/api/admin/batch/** に含まれるが、
                        // 「既存の要求行しか処理しない」ことを明示するために別に書いておく。
                        // TODO 認証導入時は ADMIN ロールまたは当該要求の所有者に制限する（設計 §4.4）
                        .requestMatchers("/api/admin/batch/geometry-ai", "/api/admin/batch/geometry-ai/**").permitAll()
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

    /** パスワードの BCrypt ハッシュ化（管理端ログイン照合用）。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
