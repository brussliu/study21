package com.study21.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserApiApplicationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
        // Spring Context 启动验证
    }

    @Test
    void healthReturnsUnifiedResponse() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/user/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("code", "OK");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).containsEntry("status", "UP");
        assertThat(data).containsEntry("service", "user-api");
    }

    @Test
    void systemInfoReturnsNonSensitiveInfo() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/user/system/info", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).containsEntry("systemName", "Study 2.1");
        assertThat(data).containsEntry("serviceName", "user-api");
        assertThat(data).containsKey("environment");
        assertThat(data).containsKey("timestamp");
    }

    @Test
    void traceIdIsPresentInHeaderAndBody() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/user/health", Map.class);

        String headerTraceId = response.getHeaders().getFirst("X-Trace-Id");
        assertThat(headerTraceId).isNotBlank();
        assertThat(response.getBody().get("traceId")).isEqualTo(headerTraceId);
    }

    @Test
    void undefinedPathIsDenied() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/user/unknown", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("code", "UNAUTHENTICATED");
    }
}
