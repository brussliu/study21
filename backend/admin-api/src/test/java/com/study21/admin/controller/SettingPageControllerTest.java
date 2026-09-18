package com.study21.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.admin.ai.AiConnectionTester;
import com.study21.admin.ai.SttConnectionTester;
import com.study21.admin.geometryai.dto.AiResponseSchemaService;
import com.study21.admin.setting.SettingsService;
import com.study21.common.core.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.study21.common.core.exception.ValidationException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 設定ページ専用 API（{@code /api/admin/setting}）。
 *
 * <p>Data TAB 用のスキーマ（{@code GET /ai-response-schema}）と、
 * AIモデルページの【接続テスト】（{@code POST /testAi} / 音声認識は {@code POST /testStt}）の
 * 入り口を確かめる。</p>
 */
class SettingPageControllerTest {

    private MockMvc mockMvc;
    private AiConnectionTester connectionTester;
    private SttConnectionTester sttConnectionTester;

    @BeforeEach
    void setUp() {
        connectionTester = mock(AiConnectionTester.class);
        sttConnectionTester = mock(SttConnectionTester.class);
        SettingPageController controller = new SettingPageController(
                mock(SettingsService.class), new AiResponseSchemaService(new ObjectMapper()), connectionTester,
                sttConnectionTester);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void batC51Aのスキーマを返す() throws Exception {
        // 画面（システム設定 > AI 設定）はモード別のバッチコード（batC51-A〜D）で Data TAB を出す
        mockMvc.perform(get("/api/admin/setting/ai-response-schema").param("task", "batC51-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskCode").value("batC51-A"))
                .andExpect(jsonPath("$.data.dto").value("BatC51AResultDto"))
                .andExpect(jsonPath("$.data.schema.type").value("object"))
                .andExpect(jsonPath("$.data.schema.properties.コマンド.type").value("array"))
                .andExpect(jsonPath("$.data.schema.properties.作図オブジェクト.type").value("array"))
                .andExpect(jsonPath("$.data.schema.properties.画像オブジェクト.type").value("array"));
    }

    @Test
    void 裸のbatC51はスキーマを返さない() throws Exception {
        // モードが無い時代の batC51 はバッチごと削除した。DTO の表にも無いので拒否される
        mockMvc.perform(get("/api/admin/setting/ai-response-schema").param("task", "batC51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batC52のスキーマも同じ仕組みで返す() throws Exception {
        mockMvc.perform(get("/api/admin/setting/ai-response-schema").param("task", "batC52"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dto").value("BatC52ResultDto"))
                .andExpect(jsonPath("$.data.schema.properties.説明.type").value("string"));
    }

    @Test
    void 未登録のタスクは400() throws Exception {
        mockMvc.perform(get("/api/admin/setting/ai-response-schema").param("task", "batC99"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 接続テストは画面が送った値をそのまま渡す() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("provider", "qwen");
        result.put("model", "qwen3.7-flash");
        result.put("latencyMs", 120);
        result.put("message", "接続できました（120ms）");
        when(connectionTester.test(any(), any(), any(), any())).thenReturn(result);

        mockMvc.perform(post("/api/admin/setting/testAi")
                        .contentType("application/json")
                        .content("{\"provider\":\"qwen\",\"model\":\"qwen3.7-flash\","
                                + "\"apiKey\":\"secret\",\"url\":\"https://example.com/v1/chat/completions\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.model").value("qwen3.7-flash"))
                .andExpect(jsonPath("$.data.message").value("接続できました（120ms）"));

        verify(connectionTester).test("qwen", "qwen3.7-flash",
                "https://example.com/v1/chat/completions", "secret");
    }

    @Test
    void 音声認識の接続テストも画面が送った値をそのまま渡す() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("provider", "alibaba");
        result.put("model", "paraformer-realtime-v2");
        result.put("latencyMs", 340);
        result.put("message", "接続できました（無音を送って確認。340ms）");
        when(sttConnectionTester.test(any(), any(), any(), any())).thenReturn(result);

        mockMvc.perform(post("/api/admin/setting/testStt")
                        .contentType("application/json")
                        .content("{\"provider\":\"alibaba\",\"model\":\"paraformer-realtime-v2\","
                                + "\"apiKey\":\"dashscope-key\","
                                + "\"url\":\"wss://dashscope.aliyuncs.com/api-ws/v1/inference\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.model").value("paraformer-realtime-v2"));

        verify(sttConnectionTester).test("alibaba", "paraformer-realtime-v2",
                "wss://dashscope.aliyuncs.com/api-ws/v1/inference", "dashscope-key");
    }

    @Test
    void 音声認識の接続テストでAPIキーが無ければ400を返す() throws Exception {
        when(sttConnectionTester.test(any(), any(), any(), any()))
                .thenThrow(new ValidationException("接続テストには API Key が必要です。"));

        mockMvc.perform(post("/api/admin/setting/testStt")
                        .contentType("application/json")
                        .content("{\"provider\":\"google\",\"model\":\"latest_long\","
                                + "\"apiKey\":\"\",\"url\":\"https://example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("接続テストには API Key が必要です。"));
    }

    @Test
    void 未対応のプロバイダーは400を返す() throws Exception {
        when(connectionTester.test(any(), any(), any(), any()))
                .thenThrow(new ValidationException("この AI は接続テストに対応していません（bigmodel）。"));

        mockMvc.perform(post("/api/admin/setting/testAi")
                        .contentType("application/json")
                        .content("{\"provider\":\"bigmodel\",\"model\":\"glm-ocr\","
                                + "\"apiKey\":\"k\",\"url\":\"https://example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("この AI は接続テストに対応していません（bigmodel）。"));
    }
}
