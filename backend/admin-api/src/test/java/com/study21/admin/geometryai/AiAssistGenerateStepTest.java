package com.study21.admin.geometryai;

import com.study21.admin.batch.AiCallLogEntity;
import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.geometryai.dto.AiResponseFormatPrompt;
import com.study21.admin.geometryai.dto.AiResponseSchemaService;
import com.study21.admin.setting.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * batC52（AI 画図助手 生成）の工程。
 *
 * ・PENDING を拾って READY にする（呼出履歴に batC52 が 1 行残る）
 * ・`assistId` 指定で特定の行を処理する
 * ・AI 失敗で FAILED ＋ 再試行回数 +1
 * ・対象なしで SKIPPED 相当、既に READY なら AI を呼ばない
 */
class AiAssistGenerateStepTest {

    private static final long ASSIST_ID = 12L;

    private GeometryAiAssistMapper assistMapper;
    private GeometryAiAssistRecorder recorder;
    private GeometryAiConnectionResolver connectionResolver;
    private GeometryAiClient aiClient;
    private SettingsService settingsService;
    private AiAssistGenerateStep step;

    @BeforeEach
    void setUp() {
        assistMapper = mock(GeometryAiAssistMapper.class);
        recorder = mock(GeometryAiAssistRecorder.class);
        connectionResolver = mock(GeometryAiConnectionResolver.class);
        aiClient = mock(GeometryAiClient.class);
        settingsService = mock(SettingsService.class);
        // 出力形式の注入は本物を使う（DTO から生成した JSON Schema がプロンプトへ入ることを確かめる）
        step = new AiAssistGenerateStep(assistMapper, recorder, connectionResolver, aiClient, settingsService,
                new AiResponseFormatPrompt(new AiResponseSchemaService(new ObjectMapper())));

        when(connectionResolver.resolve(eq("batC52"), anyString())).thenReturn(
                new GeometryAiConnectionResolver.AiConnection("qwen", "qwen-vl-max",
                        "https://example.com/v1/chat/completions", "secret"));
        when(recorder.recordCall(any())).thenReturn(999L);
    }

    private void stubSettings() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("GEOMETRY_AI_ENABLED", "true");
        values.put("GEOMETRY_AI_ASSIST_PROVIDER", "qwen:4");
        values.put("GEOMETRY_AI_ASSIST_SYSTEM_PROMPT", "作図アシスタントです。");
        values.put("GEOMETRY_AI_ASSIST_USER_PROMPT", "いまの作図のオブジェクト: {objects}\n指示: {instruction}");
        values.put("GEOMETRY_AI_ASSIST_TIMEOUT_SECONDS", "60");
        values.put("GEOMETRY_AI_ASSIST_MAX_COMMANDS", "20");
        when(settingsService.requireSettings(eq("batC52"), any())).thenReturn(values);
    }

    @Test
    @DisplayName("プロンプトに「コマンドの書き方」と、画面が送ったオブジェクト一覧・前の失敗が入る")
    void promptCarriesSignatureCardObjectsAndFailure() {
        stubSettings();
        GeometryAiAssistEntity entity = assist("PENDING");
        // 画面が作った一覧（円の定義が分かる）と、前の案の失敗
        entity.setObjectsSummary("c = Circle((0, 0), 3)（円）");
        entity.setFailureDetail("実行できなかったコマンド:\n  Polygon(A, B, C, 3)");
        when(assistMapper.findById(ASSIST_ID)).thenReturn(entity);
        when(aiClient.call(any())).thenReturn(ok("{\"コマンド\":[\"Polygon(A, B, C)\"],\"説明\":\"直しました\"}"));

        step.run(execution(ASSIST_ID));

        ArgumentCaptor<GeometryAiClient.AiRequest> captor =
                ArgumentCaptor.forClass(GeometryAiClient.AiRequest.class);
        verify(aiClient).call(captor.capture());
        String systemPrompt = captor.getValue().systemPrompt();
        String userPrompt = captor.getValue().userPrompt();
        // 引数の数の早見表（実測した失敗の再発防止）
        assertThat(systemPrompt).contains("GeoGebra コマンドの書き方");
        assertThat(systemPrompt).contains("Polygon(A, B, C)");
        assertThat(systemPrompt).contains("作図アシスタントです。");
        // 画面が送った一覧と、前の失敗
        assertThat(userPrompt).contains("c = Circle((0, 0), 3)（円）");
        assertThat(userPrompt).contains("前の案は実行できませんでした");
        assertThat(userPrompt).contains("Polygon(A, B, C, 3)");
    }

    private GeometryAiAssistEntity assist(String status) {
        GeometryAiAssistEntity entity = new GeometryAiAssistEntity();
        entity.setAssistId(ASSIST_ID);
        entity.setFigureId(1L);
        entity.setInstruction("垂線を引いて");
        entity.setBeforeXml("<construction><element label=\"A\"/></construction>");
        entity.setStatus(status);
        entity.setRetryCount(0);
        entity.setCreatedBy(77L);
        return entity;
    }

    private BatchExecutionEntity execution(Long assistId) {
        BatchExecutionEntity execution = new BatchExecutionEntity();
        execution.setExecutionId(5002L);
        execution.setBatchCode("batC52");
        execution.setRequestPayload(assistId == null ? null : AiAssistPayload.of(assistId));
        return execution;
    }

    private static GeometryAiClient.AiResponse ok(String content) {
        String body = "{\"choices\":[{\"message\":{\"content\":" + jsonString(content) + "}}]}";
        return GeometryAiClient.AiResponse.success(200, body);
    }

    private static String jsonString(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                default -> builder.append(c);
            }
        }
        return builder.append('"').toString();
    }

    @Test
    void generatesCommandsAndRecordsTheCall() {
        stubSettings();
        GeometryAiAssistEntity entity = assist("PENDING");
        when(assistMapper.findById(ASSIST_ID)).thenReturn(entity);
        when(aiClient.call(any())).thenReturn(ok("説明: 垂線を引きます。\nD = (0, 0)\nSegment(C, D)"));

        Map<String, Object> result = step.run(execution(ASSIST_ID));

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("commandCount")).isEqualTo(2);
        verify(recorder).markGenerating(entity, 5002L);
        ArgumentCaptor<GeometryAiAssistEntity> captor = ArgumentCaptor.forClass(GeometryAiAssistEntity.class);
        verify(recorder).updateReady(captor.capture());
        assertThat(captor.getValue().getCommands()).isEqualTo("D = (0, 0)\nSegment(C, D)");
        assertThat(captor.getValue().getDescription()).isEqualTo("垂線を引きます。");
        assertThat(captor.getValue().getAiCallId()).isEqualTo(999L);

        // 出力形式（JSON Schema）は DTO から生成してシステムプロンプトへ足している
        ArgumentCaptor<GeometryAiClient.AiRequest> requestCaptor =
                ArgumentCaptor.forClass(GeometryAiClient.AiRequest.class);
        verify(aiClient).call(requestCaptor.capture());
        assertThat(requestCaptor.getValue().systemPrompt()).contains("## 出力形式（JSON Schema）");
        assertThat(requestCaptor.getValue().systemPrompt()).contains("\"コマンド\"");

        ArgumentCaptor<AiCallLogEntity> callCaptor = ArgumentCaptor.forClass(AiCallLogEntity.class);
        verify(recorder).recordCall(callCaptor.capture());
        assertThat(callCaptor.getValue().getBatchCode()).isEqualTo("batC52");
        assertThat(callCaptor.getValue().getSourceCode()).isEqualTo("BATCH");
        assertThat(callCaptor.getValue().getResult()).isEqualTo("SUCCESS");
    }

    @Test
    void picksTheOldestPendingWhenNoIdIsGiven() {
        stubSettings();
        GeometryAiAssistEntity entity = assist("PENDING");
        when(assistMapper.findPendingTarget()).thenReturn(entity);
        when(aiClient.call(any())).thenReturn(ok("{\"コマンド\":[\"A = (0, 0)\"]}"));

        Map<String, Object> result = step.run(execution(null));

        assertThat(result.get("assistId")).isEqualTo(ASSIST_ID);
        verify(recorder).markGenerating(entity, 5002L);
    }

    @Test
    void failsAndIncrementsRetryCount() {
        stubSettings();
        GeometryAiAssistEntity entity = assist("PENDING");
        when(assistMapper.findById(ASSIST_ID)).thenReturn(entity);
        when(aiClient.call(any())).thenReturn(GeometryAiClient.AiResponse.failure(0, "TIMEOUT",
                "AI の応答が時間内に返りませんでした（60 秒）。"));

        assertThatThrownBy(() -> step.run(execution(ASSIST_ID)))
                .isInstanceOf(AiAssistGenerateStep.AiAssistStepException.class)
                .hasMessageContaining("時間内");

        ArgumentCaptor<GeometryAiAssistEntity> captor = ArgumentCaptor.forClass(GeometryAiAssistEntity.class);
        verify(recorder).updateFailed(captor.capture());
        assertThat(captor.getValue().getErrorCode()).isEqualTo("TIMEOUT");
        // 失敗も呼出履歴に残す
        verify(recorder).recordCall(any());
    }

    @Test
    void skipsWhenAlreadyReady() {
        stubSettings();
        when(assistMapper.findById(ASSIST_ID)).thenReturn(assist("READY"));

        Map<String, Object> result = step.run(execution(ASSIST_ID));

        assertThat(result.get("skipped")).isEqualTo(true);
        assertThat(String.valueOf(result.get("message"))).contains("既に生成済み");
        verify(aiClient, never()).call(any());
    }

    @Test
    void skipsWhenNoTarget() {
        stubSettings();
        when(assistMapper.findPendingTarget()).thenReturn(null);

        Map<String, Object> result = step.run(execution(null));

        assertThat(result.get("skipped")).isEqualTo(true);
        verify(aiClient, never()).call(any());
    }
}
