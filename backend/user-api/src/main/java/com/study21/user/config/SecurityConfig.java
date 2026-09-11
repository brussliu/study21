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
                                "/api/user/temp-files/**", "/api/user/test-infos/**", "/api/user/link-clips/**")
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
