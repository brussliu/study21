package com.study21.admin.geometryai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.admin.batch.AiCallLogEntity;
import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.geometryai.dto.AiResponseFormatPrompt;
import com.study21.admin.geometryai.dto.AiResponseSchemaService;
import com.study21.admin.geometryai.dto.FigureMode;
import com.study21.admin.geometryai.dto.FigureOutcome;
import com.study21.admin.geometryai.dto.FigureOutputType;
import com.study21.admin.geometryai.processor.FigureProcessorA;
import com.study21.admin.geometryai.processor.FigureProcessorB;
import com.study21.admin.geometryai.processor.FigureProcessorC;
import com.study21.admin.geometryai.processor.FigureProcessorD;
import com.study21.admin.geometryai.processor.FigureProcessorRegistry;
import com.study21.admin.setting.SettingsService;
import com.study21.common.core.geometryai.AiFigureConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * AI 生成工程（モード A〜D 共通の実装）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>成功で `GENERATED` ＋ 呼出履歴に 1 行（バッチコードは**モード別**）</li>
 *   <li>タイムアウトは再試行し、全滅で `FAILED(GENERATE)`。4xx は再試行しない</li>
 *   <li>既に `GENERATED` なら AI を呼ばない（無駄な課金をしない）</li>
 *   <li>モード別バッチは**自分のモードの要求だけ**を処理する（取り違えたらスキップ）</li>
 *   <li>NEEDS_INPUT / UNSUPPORTED は「コマンドが空で正常」なので再質問しない（1 回で確定）</li>
 *   <li>履歴的な batC51（モードが無い要求）は A として処理し、結果種別は当時の分類から読み替える</li>
 *   <li>追跡用の設定スナップショットを残す（鍵は入らない）</li>
 * </ol>
 */
class AiFigureGenerateStepTest {

    private static final long REQUEST_ID = 44L;

    private GeometryAiRequestMapper requestMapper;
    private GeometryAiRequestRecorder recorder;
    private GeometryAiImageStorage storage;
    private GeometryAiConnectionResolver connectionResolver;
    private GeometryAiClient aiClient;
    private SettingsService settingsService;
    private AiFigureGenerateStep step;

    @BeforeEach
    void setUp() {
        requestMapper = mock(GeometryAiRequestMapper.class);
        recorder = mock(GeometryAiRequestRecorder.class);
        storage = mock(GeometryAiImageStorage.class);
        connectionResolver = mock(GeometryAiConnectionResolver.class);
        aiClient = mock(GeometryAiClient.class);
        settingsService = mock(SettingsService.class);

        FigureProcessorRegistry registry = new FigureProcessorRegistry(List.of(
                new FigureProcessorA(), new FigureProcessorB(), new FigureProcessorC(), new FigureProcessorD()));
        FigureProcessorSettings processorSettings = new FigureProcessorSettings(settingsService,
                new AiResponseSchemaService(new ObjectMapper()));
        // 出力形式の注入は本物を使う（DTO から生成した JSON Schema がプロンプトへ入ることを確かめる）
        step = new AiFigureGenerateStep(requestMapper, recorder, storage, connectionResolver,
                registry, processorSettings, new FigurePromptBuilder(), aiClient,
                new AiResponseFormatPrompt(new AiResponseSchemaService(new ObjectMapper())));

        when(connectionResolver.resolve(anyString(), anyString())).thenReturn(
                new GeometryAiConnectionResolver.AiConnection("qwen", "qwen-vl-max",
                        "https://example.com/v1/chat/completions", "secret"));
        when(storage.read(anyString(), anyString())).thenReturn(new byte[]{1, 2, 3});
        when(recorder.recordCall(any())).thenReturn(999L);
        when(settingsService.findGlobal(anyString(), anyString())).thenReturn(Optional.empty());
    }

    /**
     * 設定（必要最小限）をスタブする。
     *
     * <p><strong>共用の User Prompt（{@code GEOMETRY_AI_INSTRUCTION_TEMPLATE}）は入れない</strong>。
     * モード別のタスクテンプレートだけで組み立てられることを、この設定で確かめる。</p>
     */
    private void stubSettings(int retryLimit) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("GEOMETRY_AI_ENABLED", "true");
        values.put("GEOMETRY_AI_PROVIDER", "qwen:4");
        values.put("GEOMETRY_AI_OUTPUT_FORMAT", "JSON");
        values.put("GEOMETRY_AI_SYSTEM_PROMPT", "共通のルールです。");
        values.put("GEOMETRY_AI_TEMPERATURE", "0.2");
        values.put("GEOMETRY_AI_MAX_COMPLETION_TOKENS", "4096");
        values.put("GEOMETRY_AI_REQUEST_TIMEOUT_SECONDS", "120");
        values.put("GEOMETRY_AI_RETRY_LIMIT", String.valueOf(retryLimit));
        values.put("GEOMETRY_AI_MAX_COMMANDS", "80");
        values.put("GEOMETRY_AI_ALLOWED_COMMANDS", "Point,Segment,Polygon,Text,Function");
        when(settingsService.requireSettings(anyString(), any())).thenReturn(values);
        // モード別は**タスクテンプレートだけ**書いてある（System Prompt・パラメータは共通を継承）
        when(settingsService.findGlobal(eq("GEOMETRY_AI"), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(1);
            return key.endsWith("_TASK_TEMPLATE")
                    ? Optional.of("{modeLabel} として作図してください。最大 {maxCommands} 個。")
                    : Optional.empty();
        });
    }

    private GeometryAiRequestEntity request(String status, String failedStage) {
        GeometryAiRequestEntity entity = new GeometryAiRequestEntity();
        entity.setRequestId(REQUEST_ID);
        entity.setRequestNo("AIG202609141200001234");
        entity.setStatusCode(status);
        entity.setFailedStage(failedStage);
        entity.setMode("A");
        entity.setRequestedOutputType("AUTO");
        entity.setFigureType("geometry");
        entity.setNote("垂線も入れて");
        entity.setVersion(2);
        entity.setCreatedBy(77L);
        entity.setOriginalMime("image/png");
        entity.setCroppedPath("geometry-ai/7/202609");
        entity.setCroppedName("AIG202609141200001234-crop.png");
        entity.setCroppedWidth(1536);
        entity.setCroppedHeight(1024);
        entity.setCropX(new BigDecimal("0.10000"));
        entity.setCropW(new BigDecimal("0.70000"));
        return entity;
    }

    private BatchExecutionEntity execution(Long aiRequestId) {
        return execution(aiRequestId, "batC51-A");
    }

    private BatchExecutionEntity execution(Long aiRequestId, String batchCode) {
        BatchExecutionEntity execution = new BatchExecutionEntity();
        execution.setExecutionId(5001L);
        execution.setBatchCode(batchCode);
        execution.setRequestPayload(aiRequestId == null ? null
                : AiFigurePayload.of(aiRequestId, "AIG202609141200001234", batchCode));
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

    /** モード A の応答（作図オブジェクトつき）。 */
    private static final String A_JSON = """
            {"判定":"GENERATABLE","要求図種":"AUTO","確定図種":"GEOMETRY","タイトル":"三角形ABC",
             "説明":"三角形を作ります。","作図オブジェクト":[
               {"名前":"A","種類":"POINT","定義":"A = (0, 0)","出典":"FROM_IMAGE","精度":"EXACT"},
               {"名前":"B","種類":"POINT","定義":"B = (5, 0)","出典":"FROM_IMAGE","精度":"EXACT"},
               {"名前":"poly","種類":"POLYGON","定義":"Polygon(A, B, C)","出典":"DERIVED","精度":"EXACT"}],
             "コマンド":["A = (0, 0)","B = (5, 0)","C = (0, 3)","poly = Polygon(A, B, C)"]}
            """;

    // ------------------------------------------------------------------ 基本

    @Test
    @DisplayName("成功で GENERATED になり、呼出履歴にモード別のバッチコードで 1 行残る")
    void generatesCommandsAndRecordsTheCall() {
        stubSettings(0);
        GeometryAiRequestEntity entity = request("PREPROCESSED", null);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        when(aiClient.call(any())).thenReturn(ok(A_JSON));

        Map<String, Object> result = step.run(execution(REQUEST_ID), FigureMode.A);

        assertThat(result.get("commandCount")).isEqualTo(4);
        assertThat(result.get("aiKind")).isEqualTo("FIGURE");
        assertThat(result.get("outcome")).isEqualTo("GENERATABLE");
        assertThat(result.get("resolvedOutputType")).isEqualTo("GEOMETRY");

        // 呼び出しの前に「生成中」を確定してから AI を呼ぶ
        verify(recorder).markGenerating(entity);
        ArgumentCaptor<GeometryAiRequestEntity> captor = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(recorder).updateGenerated(captor.capture());
        GeometryAiRequestEntity saved = captor.getValue();
        assertThat(saved.getCommands()).contains("poly = Polygon(A, B, C)");
        assertThat(saved.getCommandCount()).isEqualTo(4);
        assertThat(saved.getAiCallId()).isEqualTo(999L);
        assertThat(saved.getAiExecutionId()).isEqualTo(5001L);
        assertThat(saved.getMode()).isEqualTo("A");
        assertThat(saved.getResolvedOutputType()).isEqualTo("GEOMETRY");
        assertThat(saved.getProposalJson()).contains("\"タイトル\":\"三角形ABC\"");
        // 設定スナップショット（テンプレートの版）を残し、鍵は入れない
        assertThat(saved.getSettingsSnapshotJson())
                .contains("\"taskCode\":\"batC51-A\"")
                .contains("\"systemPromptFrom\":\"COMMON\"")
                .doesNotContain("secret");
        // 画像は要約に置き換えて保存する（base64 を残さない）
        assertThat(saved.getPrompt()).contains("<image:AIG202609141200001234-crop.png 1536x1024>");
        assertThat(saved.getPrompt()).doesNotContain("base64");

        ArgumentCaptor<AiCallLogEntity> callCaptor = ArgumentCaptor.forClass(AiCallLogEntity.class);
        verify(recorder).recordCall(callCaptor.capture());
        AiCallLogEntity call = callCaptor.getValue();
        assertThat(call.getBatchCode()).isEqualTo("batC51-A");
        assertThat(call.getProcessKey()).isEqualTo("AIG202609141200001234");
        assertThat(call.getResult()).isEqualTo("SUCCESS");
        assertThat(call.getCreatedBy()).isEqualTo(77L);
        assertThat(call.getPrompt()).doesNotContain("base64");
    }

    @Test
    @DisplayName("モード別のプロンプトと出力形式（そのモードの DTO）が AI へ渡る")
    void usesModeSpecificPromptAndSchema() {
        stubSettings(0);
        GeometryAiRequestEntity entity = request("PREPROCESSED", null);
        entity.setMode("B");
        entity.setRequestedOutputType("AUTO");
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        when(aiClient.call(any())).thenReturn(ok("{\"判定\":\"GENERATABLE\",\"確定図種\":\"GRAPH\","
                + "\"式\":[{\"元の式\":\"y = x^2\",\"正規化\":\"f(x) = x^2\",\"種類\":\"FUNCTION\",\"変数\":[\"x\"]}],"
                + "\"コマンド\":[\"f(x) = x^2\"]}"));

        Map<String, Object> result = step.run(execution(REQUEST_ID, "batC51-B"), FigureMode.B);

        ArgumentCaptor<GeometryAiClient.AiRequest> captor = ArgumentCaptor.forClass(GeometryAiClient.AiRequest.class);
        verify(aiClient).call(captor.capture());
        GeometryAiClient.AiRequest sent = captor.getValue();
        assertThat(sent.systemPrompt()).contains("B 数式からグラフを作成");
        assertThat(sent.systemPrompt()).contains("GRAPH");
        // 出力形式はモード B の DTO から生成する（定義域・式の項目が出る）
        assertThat(sent.systemPrompt()).contains("定義域").contains("式の曖昧さ");
        assertThat(sent.userPrompt()).contains("数式からグラフを作成");
        assertThat(result.get("resolvedOutputType")).isEqualTo("GRAPH");
    }

    @Test
    @DisplayName("モード別バッチは別モードの要求を処理しない（要求行が正）")
    void skipsOtherModeRequests() {
        stubSettings(0);
        GeometryAiRequestEntity entity = request("PREPROCESSED", null);
        entity.setMode("B");
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);

        Map<String, Object> result = step.run(execution(REQUEST_ID, "batC51-A"), FigureMode.A);

        assertThat(result.get("skipped")).isEqualTo(true);
        assertThat(String.valueOf(result.get("message"))).contains("作図モードは B");
        verify(aiClient, never()).call(any());
        verify(recorder, never()).markGenerating(any());
    }

    @Test
    @DisplayName("NEEDS_INPUT はコマンドが空でも再質問しない（1 回の呼び出しで確定する）")
    void doesNotRepairNeedsInput() {
        stubSettings(2);
        GeometryAiRequestEntity entity = request("PREPROCESSED", null);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        when(aiClient.call(any())).thenReturn(ok("""
                {"判定":"NEEDS_INPUT","説明":"辺 AB の長さが読めません。",
                 "質問":[{"ID":"q1","質問":"辺 AB の長さは？","選択肢":[],"回答の形":"NUMBER"}]}
                """));

        Map<String, Object> result = step.run(execution(REQUEST_ID), FigureMode.A);

        assertThat(result.get("outcome")).isEqualTo("NEEDS_INPUT");
        assertThat(result.get("commandCount")).isEqualTo(0);
        // 形式の直し（再質問）をしない＝課金は 1 回だけ
        verify(aiClient, org.mockito.Mockito.times(1)).call(any());
        ArgumentCaptor<GeometryAiRequestEntity> captor = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(recorder).updateGenerated(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo("NEEDS_INPUT");
        assertThat(captor.getValue().getQuestionsJson()).contains("辺 AB の長さは？");
    }

    @Test
    @DisplayName("UNSUPPORTED も 1 回で確定する（理由は説明に残る）")
    void acceptsUnsupportedWithoutRepair() {
        stubSettings(2);
        GeometryAiRequestEntity entity = request("PREPROCESSED", null);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        when(aiClient.call(any())).thenReturn(ok("""
                {"判定":"UNSUPPORTED","説明":"3D の作図には対応していません。","コマンド":[]}
                """));

        Map<String, Object> result = step.run(execution(REQUEST_ID), FigureMode.A);

        assertThat(result.get("outcome")).isEqualTo("UNSUPPORTED");
        verify(aiClient, org.mockito.Mockito.times(1)).call(any());
    }

    // ------------------------------------------------------------------ 歴史的な要求

    @Test
    @DisplayName("歴史的な batC51（モードが無い要求）は A として処理し、結果種別を分類から読み替える")
    void treatsLegacyRequestAsModeA() {
        stubSettings(0);
        GeometryAiRequestEntity entity = request("PREPROCESSED", null);
        entity.setMode(null);
        entity.setRequestedOutputType(null);
        entity.setUserKind("FUNCTION");
        entity.setUserSubKind(null);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        when(aiClient.call(any())).thenReturn(ok("{\"判定\":\"GENERATABLE\",\"コマンド\":[\"f(x) = x^2\"]}"));

        Map<String, Object> result = step.run(execution(REQUEST_ID, "batC51"), null);

        assertThat(result.get("mode")).isEqualTo("A");
        assertThat(result.get("requestedOutputType")).isEqualTo("GRAPH");
        ArgumentCaptor<GeometryAiClient.AiRequest> captor = ArgumentCaptor.forClass(GeometryAiClient.AiRequest.class);
        verify(aiClient).call(captor.capture());
        assertThat(captor.getValue().systemPrompt()).contains("A 画像をもとに再現");
        assertThat(captor.getValue().systemPrompt()).contains("GRAPH");
    }

    @Test
    @DisplayName("要求 ID が無いときはモード A の生成待ちを拾う")
    void picksTheOldestPendingRequestOfTheMode() {
        stubSettings(0);
        GeometryAiRequestEntity entity = request("GENERATING", null);
        when(requestMapper.findGenerateTarget("A")).thenReturn(entity);
        when(aiClient.call(any())).thenReturn(ok(A_JSON));

        Map<String, Object> result = step.run(execution(null, "batC51-A"), FigureMode.A);

        assertThat(result.get("aiRequestId")).isEqualTo(REQUEST_ID);
        verify(requestMapper).findGenerateTarget("A");
        // 呼び出し中に落ちた残骸（GENERATING）も再実行できる
        verify(recorder).markGenerating(entity);
    }

    @Test
    @DisplayName("既に生成済みなら AI を呼ばずにスキップする")
    void doesNothingWhenTheRequestIsAlreadyGenerated() {
        stubSettings(0);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("GENERATED", null));

        Map<String, Object> result = step.run(execution(REQUEST_ID), FigureMode.A);

        assertThat(result.get("skipped")).isEqualTo(true);
        assertThat(String.valueOf(result.get("message"))).contains("既に生成済み");
        verify(aiClient, never()).call(any());
        verify(recorder, never()).markGenerating(any());
    }

    // ------------------------------------------------------------------ 失敗と再試行

    @Test
    @DisplayName("タイムアウトは再試行の上限まで試してから FAILED(GENERATE)")
    void reportsATimeoutAfterTheRetryLimit() {
        stubSettings(0);
        GeometryAiRequestEntity entity = request("PREPROCESSED", null);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        when(aiClient.call(any())).thenReturn(GeometryAiClient.AiResponse.failure(0, "TIMEOUT",
                "AI の応答が時間内に返りませんでした（120 秒）。"));

        assertThatThrownBy(() -> step.run(execution(REQUEST_ID), FigureMode.A))
                .isInstanceOf(AiFigurePreprocessStep.GeometryAiStepException.class)
                .hasMessageContaining("時間内");

        ArgumentCaptor<GeometryAiRequestEntity> captor = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(recorder).updateFailed(captor.capture());
        assertThat(captor.getValue().getFailedStage()).isEqualTo("GENERATE");
        assertThat(captor.getValue().getErrorCode()).isEqualTo("TIMEOUT");
        // 失敗も呼出履歴に残す（お金を使った事実を消さない）
        verify(recorder).recordCall(any());
    }

    @Test
    @DisplayName("一時的な失敗（429）は再試行して成功できる")
    void retriesTransientFailuresUpToTheLimit() {
        stubSettings(1);
        GeometryAiRequestEntity entity = request("PREPROCESSED", null);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        when(aiClient.call(any()))
                .thenReturn(GeometryAiClient.AiResponse.failure(429, "HTTP_429", "混み合っています。"))
                .thenReturn(ok(A_JSON));

        Map<String, Object> result = step.run(execution(REQUEST_ID), FigureMode.A);

        assertThat(result.get("commandCount")).isEqualTo(4);
        verify(recorder, org.mockito.Mockito.times(2)).recordCall(any());
        verify(recorder).updateGenerated(any());
    }

    @Test
    @DisplayName("4xx（キー・モデル不正）は再試行しない")
    void doesNotRetryFatalClientErrors() {
        stubSettings(2);
        GeometryAiRequestEntity entity = request("PREPROCESSED", null);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        when(aiClient.call(any())).thenReturn(GeometryAiClient.AiResponse.failure(401, "HTTP_4XX",
                "AI がリクエストを受け付けませんでした（HTTP 401）。"));

        assertThatThrownBy(() -> step.run(execution(REQUEST_ID), FigureMode.A))
                .isInstanceOf(AiFigurePreprocessStep.GeometryAiStepException.class);

        verify(aiClient, org.mockito.Mockito.times(1)).call(any());
    }

    @Test
    @DisplayName("壊れた JSON は 1 回だけ再質問する（前置きつき）")
    void repairsBrokenJsonOnce() {
        stubSettings(1);
        GeometryAiRequestEntity entity = request("PREPROCESSED", null);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        when(aiClient.call(any()))
                .thenReturn(ok("{ \"判定\": "))
                .thenReturn(ok("{\"判定\":\"GENERATABLE\",\"コマンド\":[\"f(x) = x^2\"]}"));

        Map<String, Object> result = step.run(execution(REQUEST_ID), FigureMode.A);

        assertThat(result.get("commandCount")).isEqualTo(1);
        ArgumentCaptor<GeometryAiClient.AiRequest> captor = ArgumentCaptor.forClass(GeometryAiClient.AiRequest.class);
        verify(aiClient, org.mockito.Mockito.times(2)).call(captor.capture());
        assertThat(captor.getValue().userPrompt()).contains("前回の出力は検証に失敗しました");
        assertThat(captor.getValue().systemPrompt()).contains("## 出力形式（JSON Schema）");
        assertThat(captor.getValue().systemPrompt()).contains("\"コマンド\"");
    }

    @Test
    @DisplayName("補充パラメータはプロンプトへ行として入り、空の項目は出さない")
    void passesSupplementsToPrompt() {
        stubSettings(0);
        GeometryAiRequestEntity entity = request("PREPROCESSED", null);
        entity.setSupplementsJson("{\"再現の重点\":\"数学的な関係を優先\",\"情報が足りないとき\":\"確認してから進める\","
                + "\"既知の値\":\"\",\"座標の範囲\":null}");
        entity.setRequestedOutputType("GRAPH");
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        when(aiClient.call(any())).thenReturn(ok(A_JSON));

        step.run(execution(REQUEST_ID), FigureMode.A);

        ArgumentCaptor<GeometryAiClient.AiRequest> captor = ArgumentCaptor.forClass(GeometryAiClient.AiRequest.class);
        verify(aiClient).call(captor.capture());
        String prompt = captor.getValue().userPrompt();
        assertThat(prompt).contains("再現の重点: 数学的な関係を優先");
        assertThat(prompt).contains("情報が足りないとき: 確認してから進める");
        assertThat(prompt).doesNotContain("既知の値");
        assertThat(prompt).doesNotContain("座標の範囲");
        assertThat(prompt).doesNotContain("{");
    }

    @Test
    @DisplayName("結果種別は要求行の指定を優先する（B は GRAPH に固定）")
    void requestTypeComesFromRequestRow() {
        stubSettings(0);
        GeometryAiRequestEntity entity = request("PREPROCESSED", null);
        entity.setRequestedOutputType("MIXED");
        assertThat(AiFigureGenerateStep.requestedOutputTypeOf(entity, new FigureProcessorA()))
                .isEqualTo(FigureOutputType.MIXED);
        // B は画面で選ばせないので、要求行に何があっても GRAPH
        assertThat(AiFigureGenerateStep.requestedOutputTypeOf(entity, new FigureProcessorB()))
                .isEqualTo(FigureOutputType.GRAPH);
        // 要求行に無ければ AUTO（歴史的な要求は分類から読み替え）
        entity.setRequestedOutputType(null);
        entity.setUserKind("FUNCTION");
        assertThat(AiFigureGenerateStep.requestedOutputTypeOf(entity, new FigureProcessorA()))
                .isEqualTo(FigureOutputType.GRAPH);
        assertThat(FigureOutcome.GENERATABLE.needsCommands()).isTrue();
    }

    // ------------------------------------------------------------------ 共用の User Prompt が空

    @Test
    @DisplayName("共用の User Prompt が空でも、モード別のタスクテンプレートで組み立てられる")
    void buildsPromptWithoutCommonUserPrompt() {
        stubSettings(0);
        GeometryAiRequestEntity entity = request("PREPROCESSED", null);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        when(aiClient.call(any())).thenReturn(ok(A_JSON));

        step.run(execution(REQUEST_ID), FigureMode.A);

        ArgumentCaptor<GeometryAiClient.AiRequest> captor = ArgumentCaptor.forClass(GeometryAiClient.AiRequest.class);
        verify(aiClient).call(captor.capture());
        GeometryAiClient.AiRequest sent = captor.getValue();
        // モード別のタスクテンプレートが使われ、変数はすべて展開されている
        assertThat(sent.userPrompt()).contains("画像をもとに再現 として作図してください。最大 80 個。");
        assertThat(sent.userPrompt()).doesNotContain("{");
        // 共通の System Prompt は残る（モード別が無くても消えない）
        assertThat(sent.systemPrompt()).contains("共通のルールです。");
        assertThat(sent.systemPrompt()).contains("A 画像をもとに再現");
    }

    // ------------------------------------------------------------------ 止まったら理由を書く

    @Test
    @DisplayName("設定が不足していたら FAILED(GENERATE) に理由を書く（処理中のまま残さない）")
    void writesFailedWhenSettingsAreMissing() {
        stubSettings(0);
        GeometryAiRequestEntity entity = request("PREPROCESSED", null);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        when(settingsService.requireSettings(anyString(), any())).thenThrow(
                new com.study21.admin.setting.SettingsValidationException(
                        List.of("setting_key=GEOMETRY_AI_SYSTEM_PROMPT, 原因=設定値が未設定または空です")));

        assertThatThrownBy(() -> step.run(execution(REQUEST_ID), FigureMode.A))
                .isInstanceOf(AiFigurePreprocessStep.GeometryAiStepException.class)
                .hasMessageContaining("AI の設定が不足しています");

        ArgumentCaptor<GeometryAiRequestEntity> captor = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(recorder).updateFailed(captor.capture());
        assertThat(captor.getValue().getFailedStage()).isEqualTo("GENERATE");
        assertThat(captor.getValue().getErrorCode()).isEqualTo("CONFIG");
        assertThat(captor.getValue().getErrorMessage()).contains("GEOMETRY_AI_SYSTEM_PROMPT");
        verify(aiClient, never()).call(any());
    }

    @Test
    @DisplayName("テンプレートに知らない変数があれば FAILED(GENERATE) に理由を書く")
    void writesFailedWhenTemplateHasUnknownVariable() {
        stubSettings(0);
        when(settingsService.findGlobal(eq("GEOMETRY_AI"), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(1);
            return key.endsWith("_TASK_TEMPLATE") ? Optional.of("作図: {unknownVar}") : Optional.empty();
        });
        GeometryAiRequestEntity entity = request("PREPROCESSED", null);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);

        assertThatThrownBy(() -> step.run(execution(REQUEST_ID), FigureMode.A))
                .isInstanceOf(AiFigurePreprocessStep.GeometryAiStepException.class)
                .hasMessageContaining("{unknownVar}");

        ArgumentCaptor<GeometryAiRequestEntity> captor = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(recorder).updateFailed(captor.capture());
        assertThat(captor.getValue().getFailedStage()).isEqualTo("GENERATE");
        assertThat(captor.getValue().getErrorCode()).isEqualTo("PROMPT");
        verify(aiClient, never()).call(any());
    }

    @Test
    @DisplayName("AI の接続を解決できなければ FAILED(GENERATE) に理由を書く")
    void writesFailedWhenConnectionCannotBeResolved() {
        stubSettings(0);
        GeometryAiRequestEntity entity = request("PREPROCESSED", null);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        when(connectionResolver.resolve(anyString(), anyString()))
                .thenThrow(new com.study21.common.core.exception.ValidationException(
                        "AI モデルの接続設定がありません（qwen:4）。"));

        assertThatThrownBy(() -> step.run(execution(REQUEST_ID), FigureMode.A))
                .isInstanceOf(AiFigurePreprocessStep.GeometryAiStepException.class)
                .hasMessageContaining("AI モデルの接続設定がありません");

        ArgumentCaptor<GeometryAiRequestEntity> captor = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(recorder).updateFailed(captor.capture());
        assertThat(captor.getValue().getFailedStage()).isEqualTo("GENERATE");
        assertThat(captor.getValue().getErrorCode()).isEqualTo("PROMPT");
        verify(aiClient, never()).call(any());
    }

    // ------------------------------------------------------------------ 固定した設定を使う

    @Test
    @DisplayName("要求行に固定した設定があれば、いまの設定を変えてもその版で生成する")
    void usesPinnedConfigWhenPresent() {
        stubSettings(0);
        // 受付時の設定（あとから書き換えられる前のもの）
        Map<String, String> pinnedValues = new LinkedHashMap<>();
        pinnedValues.put("GEOMETRY_AI_SYSTEM_PROMPT", "受付時の共通ルール。");
        pinnedValues.put("GEOMETRY_AI_PROVIDER", "qwen:4");
        pinnedValues.put("GEOMETRY_AI_ALLOWED_COMMANDS", "Point,Segment,Polygon,Text,Function");
        pinnedValues.put("GEOMETRY_AI_MAX_COMMANDS", "40");
        GeometryAiRequestEntity entity = request("PREPROCESSED", null);
        entity.setSettingsSnapshotJson(
                AiFigureConfig.resolve("A", "batC51-A", pinnedValues, "2026-09-19T00:00:00").toSnapshotJson(null));
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        when(aiClient.call(any())).thenReturn(ok(A_JSON));

        step.run(execution(REQUEST_ID), FigureMode.A);

        ArgumentCaptor<GeometryAiClient.AiRequest> captor = ArgumentCaptor.forClass(GeometryAiClient.AiRequest.class);
        verify(aiClient).call(captor.capture());
        assertThat(captor.getValue().systemPrompt())
                .contains("受付時の共通ルール。")
                .doesNotContain("共通のルールです。");
        // 上限も受付時の値（いまの設定は 80）
        ArgumentCaptor<GeometryAiRequestEntity> saved = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(recorder).updateGenerated(saved.capture());
        assertThat(saved.getValue().getSettingsSnapshotJson())
                .contains("\"systemPromptCommon\":\"受付時の共通ルール。\"")
                .contains("\"maxCommands\":40")
                .contains("\"model\":\"qwen-vl-max\"");
    }

    @Test
    @DisplayName("固定した設定が無ければ今の設定から解決し、その本文を要求行へ書く")
    void pinsLiveConfigWhenNothingWasPinned() {
        stubSettings(0);
        GeometryAiRequestEntity entity = request("PREPROCESSED", null);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        when(aiClient.call(any())).thenReturn(ok(A_JSON));

        step.run(execution(REQUEST_ID), FigureMode.A);

        ArgumentCaptor<GeometryAiRequestEntity> saved = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(recorder).updateGenerated(saved.capture());
        // ハッシュだけでなく本文が残る（あとから「どの文面で作ったか」を読み返せる）
        assertThat(saved.getValue().getSettingsSnapshotJson())
                .contains("\"systemPromptCommon\":\"共通のルールです。\"")
                .contains("\"taskTemplate\"")
                .doesNotContain("secret");
    }
}
