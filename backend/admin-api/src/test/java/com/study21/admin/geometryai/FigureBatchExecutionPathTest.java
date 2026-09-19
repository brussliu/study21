package com.study21.admin.geometryai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.admin.batch.AiCallLogMapper;
import com.study21.admin.batch.BatchControlMapper;
import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchExecutionMapper;
import com.study21.admin.batch.BatchTaskDefinition;
import com.study21.admin.batch.BatchTaskHandler;
import com.study21.admin.batch.BatchTaskRegistry;
import com.study21.admin.batch.BatchTaskType;
import com.study21.admin.batch.BatchServiceImpl;
import com.study21.admin.geometryai.dto.AiResponseFormatPrompt;
import com.study21.admin.geometryai.dto.AiResponseSchemaService;
import com.study21.admin.geometryai.dto.FigureMode;
import com.study21.admin.geometryai.dto.FigureOutputType;
import com.study21.admin.geometryai.processor.FigureGenerateBatchHandlers;
import com.study21.admin.geometryai.processor.FigureProcessorA;
import com.study21.admin.geometryai.processor.FigureProcessorB;
import com.study21.admin.geometryai.processor.FigureProcessorC;
import com.study21.admin.geometryai.processor.FigureProcessorD;
import com.study21.admin.geometryai.processor.FigureProcessorRegistry;
import com.study21.admin.setting.SettingRequirement;
import com.study21.admin.setting.SettingsService;
import com.study21.common.core.geometryai.AiFigureConfig;
import com.study21.common.core.geometryai.AiFigureSettingKeys;
import com.study21.common.core.geometryai.AiModelSlot;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * **実行の入口（{@code BatchServiceImpl.rerunStep}）から設定の解決までの通し**を確かめる。
 *
 * <p>スナップショットのクラスだけを単体で試すのではなく、実際にバッチを起動して
 * 「どの設定で AI を呼んだか」まで見る（{@code BatchService} はモックしない）。
 * 使うのは本物の {@link BatchServiceImpl}・{@link FigureGenerateBatchHandlers}・
 * {@link AiFigureGenerateStep}・{@link AiFigureTaskConfigResolver}・{@link FigureProcessorSettings}・
 * {@link GeometryAiConnectionResolver} で、DB と AI の HTTP だけをモックする。</p>
 *
 * <p>確かめること（利用者の要求）:</p>
 * <ol>
 *   <li>提出後に**共用プロンプトを消しても**、固定した設定があるタスクは続けて実行できる</li>
 *   <li>提出後に**プロンプト・Temperature・最大 Token を変えても**、固定した値で呼ぶ</li>
 *   <li>同じスロットの**モデル名を変えても**、固定したモデルで呼ぶ（黙って切り替えない）</li>
 *   <li>**壊れたスナップショット**は失敗し、いまの設定へは切り替えない</li>
 *   <li>**スナップショットが無い**歴史的なタスクは、いまの設定を固定してから実行し、
 *       次の再試行はその固定を使う（技術的な再試行で条件が変わらない）</li>
 *   <li>設定が壊れたタスクは**次のタスクを止めない**</li>
 *   <li>AI 生図以外のバッチは**今までどおり「いまの設定」を検証する**</li>
 * </ol>
 */
class FigureBatchExecutionPathTest {

    private static final long REQUEST_ID = 4242L;
    private static final String REQUEST_NO = "AIG202609191200001234";

    private SettingsService settingsService;
    private GeometryAiRequestMapper requestMapper;
    private GeometryAiRequestRecorder recorder;
    private GeometryAiImageStorage storage;
    private GeometryAiClient aiClient;
    private BatchExecutionMapper executionMapper;
    private BatchControlMapper controlMapper;
    private BatchTaskRegistry taskRegistry;
    private BatchServiceImpl batchService;
    /** AI 生図以外のバッチの代役（呼ばれたかを見る）。 */
    private final RecordingHandler recordingHandler = new RecordingHandler("batC61");

    /** いまの設定（テストごとに書き換える）。 */
    private final Map<String, String> current = new LinkedHashMap<>();
    /** AI_MODEL の設定（スロットごとのモデル名・URL・API Key）。 */
    private final Map<String, String> model = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        settingsService = mock(SettingsService.class);
        requestMapper = mock(GeometryAiRequestMapper.class);
        recorder = mock(GeometryAiRequestRecorder.class);
        storage = mock(GeometryAiImageStorage.class);
        aiClient = mock(GeometryAiClient.class);
        executionMapper = mock(BatchExecutionMapper.class);
        controlMapper = mock(BatchControlMapper.class);
        taskRegistry = mock(BatchTaskRegistry.class);

        // ---- いまの設定（提出後に書き換えられる側） ----
        current.put(AiFigureSettingKeys.ENABLED, "true");
        current.put(AiFigureSettingKeys.PROVIDER, "qwen:4");
        current.put(AiFigureSettingKeys.OUTPUT_FORMAT, "JSON");
        current.put(AiFigureSettingKeys.SYSTEM_PROMPT, "いまの共通ルール。");
        current.put(AiFigureSettingKeys.TEMPERATURE, "0.2");
        current.put(AiFigureSettingKeys.MAX_COMPLETION_TOKENS, "4096");
        current.put(AiFigureSettingKeys.REQUEST_TIMEOUT_SECONDS, "120");
        current.put(AiFigureSettingKeys.RETRY_LIMIT, "0");
        current.put(AiFigureSettingKeys.MAX_COMMANDS, "80");
        current.put(AiFigureSettingKeys.ALLOWED_COMMANDS, "Point,Segment,Polygon,Text");

        // ---- AI_MODEL（接続。秘密はここから読む） ----
        model.put("AI_QWEN_MODEL_4", "qwen-vl-max");
        model.put("AI_QWEN_URL", "https://example.com/v1/chat/completions");
        model.put("AI_QWEN_API_KEY", "secret-key");

        when(settingsService.requireSettings(anyString(), any())).thenAnswer(invocation -> {
            List<SettingRequirement> requirements = invocation.getArgument(1);
            Map<String, String> values = new LinkedHashMap<>();
            for (SettingRequirement requirement : requirements) {
                String value = current.get(requirement.settingKey());
                if (value == null || value.isBlank()) {
                    throw new com.study21.admin.setting.SettingsValidationException(List.of(
                            "setting_key=" + requirement.settingKey() + ", 原因=設定値が未設定または空です"));
                }
                values.put(requirement.settingKey(), value);
            }
            return values;
        });
        when(settingsService.findGlobal(eq(AiFigureSettingKeys.PAGE), anyString())).thenReturn(Optional.empty());
        when(settingsService.requireGlobal(anyString(), eq(AiModelSlot.PAGE), anyString()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(2);
                    String value = model.get(key);
                    if (value == null || value.isBlank()) {
                        throw new com.study21.admin.setting.SettingsValidationException(List.of(
                                "setting_key=" + key + ", 原因=設定値が未設定または空です"));
                    }
                    return value;
                });

        // ---- 要求行・記録・画像・AI ----
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("PREPROCESSED"));
        when(storage.read(anyString(), anyString())).thenReturn(new byte[]{1, 2, 3});
        when(recorder.recordCall(any())).thenReturn(999L);
        when(aiClient.call(any())).thenReturn(ok());
        doAnswer(invocation -> {
            BatchExecutionEntity entity = invocation.getArgument(0);
            entity.setExecutionId(777L);
            return 1;
        }).when(executionMapper).insert(any(BatchExecutionEntity.class));
        when(controlMapper.touchLastRunAt(anyString())).thenReturn(1);

        // ---- 実行の入口（本物を組み立てる） ----
        FigureProcessorRegistry processors = new FigureProcessorRegistry(List.of(
                new FigureProcessorA(), new FigureProcessorB(), new FigureProcessorC(), new FigureProcessorD()));
        AiResponseSchemaService schemaService = new AiResponseSchemaService(new ObjectMapper());
        FigureProcessorSettings processorSettings = new FigureProcessorSettings(settingsService, schemaService);
        GeometryAiConnectionResolver connectionResolver =
                new GeometryAiConnectionResolver(settingsService, false);
        AiFigureTaskConfigResolver taskConfigResolver =
                new AiFigureTaskConfigResolver(processorSettings, connectionResolver);
        AiFigureGenerateStep generateStep = new AiFigureGenerateStep(requestMapper, recorder, storage,
                taskConfigResolver, processors, processorSettings, new FigurePromptBuilder(), aiClient,
                new AiResponseFormatPrompt(schemaService));
        List<BatchTaskHandler> handlers = List.of(
                new FigureGenerateBatchHandlers.A(generateStep),
                new FigureGenerateBatchHandlers.B(generateStep),
                new FigureGenerateBatchHandlers.C(generateStep),
                new FigureGenerateBatchHandlers.D(generateStep),
                recordingHandler);
        FigureTaskConfigPreflight preflight =
                new FigureTaskConfigPreflight(taskRegistry, processors, requestMapper, taskConfigResolver);

        for (FigureMode mode : FigureMode.values()) {
            when(taskRegistry.findByCode(mode.taskCode())).thenReturn(new BatchTaskDefinition(mode.taskCode(),
                    BatchTaskType.C, "AI生図 " + mode.label(), false, null, null, "GEOMETRY_AI",
                    FigureProcessorSettings.requiredSettings()));
        }
        when(executionMapper.findRunningByBatchCode(anyString())).thenReturn(null);

        batchService = new BatchServiceImpl(taskRegistry, settingsService, executionMapper, controlMapper,
                mock(AiCallLogMapper.class), handlers, List.of(preflight));
    }

    // ------------------------------------------------------------------ 道具

    /** 呼ばれたかどうかだけを見るハンドラ（AI 生図以外のバッチの代役）。 */
    private static class RecordingHandler implements BatchTaskHandler {
        private final String code;
        int calls;

        RecordingHandler(String code) {
            this.code = code;
        }

        @Override
        public String taskCode() {
            return code;
        }

        @Override
        public String execute(BatchExecutionEntity execution) {
            calls++;
            return "実行しました";
        }
    }

    private GeometryAiRequestEntity request(String status) {
        GeometryAiRequestEntity entity = new GeometryAiRequestEntity();
        entity.setRequestId(REQUEST_ID);
        entity.setRequestNo(REQUEST_NO);
        entity.setStatusCode(status);
        entity.setMode("A");
        entity.setRequestedOutputType("AUTO");
        entity.setFigureType("geometry");
        entity.setNote("垂線も入れて");
        entity.setVersion(2);
        entity.setCroppedPath("geometry-ai/7/202609");
        entity.setCroppedName(REQUEST_NO + "-crop.png");
        entity.setCropX(new BigDecimal("0.10000"));
        entity.setCropW(new BigDecimal("0.70000"));
        return entity;
    }

    /** 提出時のスナップショット（受付時に固定したもの）。 */
    private String submittedSnapshot(Map<String, String> submitted, String pinnedModel, int revision) {
        return AiFigureConfig.resolve("A", "batC51-A", submitted, "2026-09-19T00:00:00", pinnedModel, revision)
                .toSnapshotJson(null);
    }

    /** 提出時の設定（いまの設定とは別に作る）。 */
    private static Map<String, String> submitted() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(AiFigureSettingKeys.ENABLED, "true");
        values.put(AiFigureSettingKeys.PROVIDER, "qwen:4");
        values.put(AiFigureSettingKeys.OUTPUT_FORMAT, "JSON");
        values.put(AiFigureSettingKeys.SYSTEM_PROMPT, "提出時の共通ルール。");
        values.put(AiFigureSettingKeys.TEMPERATURE, "0.7");
        values.put(AiFigureSettingKeys.MAX_COMPLETION_TOKENS, "2048");
        values.put(AiFigureSettingKeys.REQUEST_TIMEOUT_SECONDS, "120");
        values.put(AiFigureSettingKeys.RETRY_LIMIT, "0");
        values.put(AiFigureSettingKeys.MAX_COMMANDS, "80");
        values.put(AiFigureSettingKeys.ALLOWED_COMMANDS, "Point,Segment,Polygon,Text");
        return values;
    }

    private static GeometryAiClient.AiResponse ok() {
        String content = "{\"判定\":\"GENERATABLE\",\"確定図種\":\"GEOMETRY\",\"コマンド\":[\"A = (0, 0)\"]}";
        return GeometryAiClient.AiResponse.success(200,
                "{\"choices\":[{\"message\":{\"content\":" + jsonString(content) + "}}]}");
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

    /**
     * バッチを 1 回実行する（AI 生図の工程として呼ぶ形）。
     *
     * <p>実行前の設定検証で拒否したときは、**実行記録を作らずに例外**を投げる（ふつうのバッチの
     * 設定検証と同じ振る舞い）。流水線（{@code AiFigurePipelineService}）はそれを受けて
     * 要求行へ `FAILED` と理由を書くので、ここでは流水線が見る形（success=false ＋ 理由）にそろえる。</p>
     */
    private Map<String, Object> runStep() {
        try {
            return batchService.rerunStep("batC51-A", "geometry-ai-worker",
                    AiFigurePayload.of(REQUEST_ID, REQUEST_NO, "GENERATE"));
        } catch (RuntimeException cause) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("success", false);
            failure.put("message", cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
            return failure;
        }
    }

    private GeometryAiClient.AiRequest sentRequest() {
        ArgumentCaptor<GeometryAiClient.AiRequest> captor =
                ArgumentCaptor.forClass(GeometryAiClient.AiRequest.class);
        verify(aiClient).call(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------ 1

    @Test
    @DisplayName("提出後に共用プロンプトを消しても、固定した設定があるタスクは実行できる")
    void keepsRunningAfterTheCommonPromptIsCleared() {
        GeometryAiRequestEntity entity = request("PREPROCESSED");
        entity.setSettingsSnapshotJson(submittedSnapshot(submitted(), "qwen-vl-max", 1));
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        // 提出後に共用プロンプトを空にする（＝いまの設定では検証に通らない）
        current.put(AiFigureSettingKeys.SYSTEM_PROMPT, "");

        Map<String, Object> result = runStep();

        assertThat(result).containsEntry("success", true);
        assertThat(sentRequest().systemPrompt()).contains("提出時の共通ルール。");
    }

    // ------------------------------------------------------------------ 2

    @Test
    @DisplayName("提出後にプロンプト・Temperature・最大 Token を変えても、固定した値で呼ぶ")
    void usesTheSubmittedPromptAndParameters() {
        GeometryAiRequestEntity entity = request("PREPROCESSED");
        entity.setSettingsSnapshotJson(submittedSnapshot(submitted(), "qwen-vl-max", 1));
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        // 提出後に全部書き換える
        current.put(AiFigureSettingKeys.SYSTEM_PROMPT, "あとから書き換えたルール。");
        current.put(AiFigureSettingKeys.TEMPERATURE, "0.1");
        current.put(AiFigureSettingKeys.MAX_COMPLETION_TOKENS, "8192");

        runStep();

        GeometryAiClient.AiRequest sent = sentRequest();
        assertThat(sent.systemPrompt()).contains("提出時の共通ルール。").doesNotContain("あとから書き換えた");
        assertThat(sent.temperature()).isEqualTo(0.7);
        assertThat(sent.maxCompletionTokens()).isEqualTo(2048);
    }

    // ------------------------------------------------------------------ 3

    @Test
    @DisplayName("同じスロットのモデル名を変えても、固定したモデルで呼ぶ（黙って切り替えない）")
    void doesNotSilentlySwitchTheModel() {
        GeometryAiRequestEntity entity = request("PREPROCESSED");
        entity.setSettingsSnapshotJson(submittedSnapshot(submitted(), "qwen-vl-max", 1));
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        // 同じスロット（qwen:4）のモデル名だけを変える
        model.put("AI_QWEN_MODEL_4", "qwen3-vl-plus");

        runStep();

        assertThat(sentRequest().model()).isEqualTo("qwen-vl-max");
        // 実行の記録に「固定したモデルを使った」ことを残す（あとから気づける）
        ArgumentCaptor<GeometryAiRequestEntity> saved = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(recorder).updateGenerated(saved.capture());
        assertThat(saved.getValue().getSettingsSnapshotJson()).contains("\"modelFromPinned\":true");
    }

    @Test
    @DisplayName("スロットそのものが消えていたら、別のモデルへ切り替えず失敗する")
    void failsWhenTheModelSlotIsGone() {
        GeometryAiRequestEntity entity = request("PREPROCESSED");
        entity.setSettingsSnapshotJson(submittedSnapshot(submitted(), "qwen-vl-max", 1));
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        model.clear();

        Map<String, Object> result = runStep();

        assertThat(result).containsEntry("success", false);
        verify(aiClient, never()).call(any());
        ArgumentCaptor<GeometryAiRequestEntity> saved = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(recorder).updateFailed(saved.capture());
        assertThat(saved.getValue().getFailedStage()).isEqualTo("GENERATE");
        assertThat(saved.getValue().getErrorMessage()).contains("AI のモデル設定を読めません");
    }

    // ------------------------------------------------------------------ 4

    @Test
    @DisplayName("壊れたスナップショットは明確に失敗し、いまの設定へは切り替えない")
    void failsOnBrokenSnapshotWithoutFallingBackToLiveSettings() {
        GeometryAiRequestEntity entity = request("PREPROCESSED");
        entity.setSettingsSnapshotJson("{\"version\":1,\"config\":{\"version\":1,\"mode\":\"A\"}}");
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);

        Map<String, Object> result = runStep();

        assertThat(result).containsEntry("success", false);
        // いまの設定（有効な共用プロンプト）で黙って走らせない
        verify(aiClient, never()).call(any());
        assertThat(String.valueOf(result.get("message"))).contains("固定した設定を使えません");
    }

    @Test
    @DisplayName("固定したモードが要求と違えば失敗する（取り違えたまま走らせない）")
    void failsOnModeMismatch() {
        GeometryAiRequestEntity entity = request("PREPROCESSED");
        entity.setSettingsSnapshotJson(AiFigureConfig.resolve("C", "batC51-C", submitted(),
                "2026-09-19T00:00:00", "qwen-vl-max", 1).toSnapshotJson(null));
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);

        Map<String, Object> result = runStep();

        assertThat(result).containsEntry("success", false);
        verify(aiClient, never()).call(any());
        assertThat(String.valueOf(result.get("message"))).contains("作図モード");
    }

    // ------------------------------------------------------------------ 5

    @Test
    @DisplayName("スナップショットが無い歴史的なタスクは、いまの設定を固定してから実行する")
    void pinsLiveSettingsForLegacyTasks() {
        GeometryAiRequestEntity entity = request("PREPROCESSED");
        entity.setSettingsSnapshotJson(null);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);

        Map<String, Object> result = runStep();

        assertThat(result).containsEntry("success", true);
        // **AI を呼ぶ前に**固定している（途中で落ちても同じ条件で再開できる）
        ArgumentCaptor<String> pinned = ArgumentCaptor.forClass(String.class);
        verify(recorder).pinConfig(any(), pinned.capture());
        AiFigureConfig restored = AiFigureConfig.fromSnapshotJson(pinned.getValue()).orElseThrow();
        assertThat(restored.systemPromptCommon()).isEqualTo("いまの共通ルール。");
        assertThat(restored.model()).isEqualTo("qwen-vl-max");
        assertThat(restored.revision()).isEqualTo(1);
        assertThat(sentRequest().model()).isEqualTo("qwen-vl-max");
    }

    @Test
    @DisplayName("固定したあとの再試行（技術的な拾い直し）は同じ固定を使う（設定を変えても条件が変わらない）")
    void technicalRetryReusesThePinnedConfig() {
        GeometryAiRequestEntity entity = request("PREPROCESSED");
        String pinned = submittedSnapshot(submitted(), "qwen-vl-max", 1);
        entity.setSettingsSnapshotJson(pinned);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        // 2 回目の実行の前に、いまの設定を全部書き換えてしまう
        current.put(AiFigureSettingKeys.SYSTEM_PROMPT, "再試行の前に書き換えたルール。");
        current.put(AiFigureSettingKeys.TEMPERATURE, "0.9");
        model.put("AI_QWEN_MODEL_4", "qwen3-vl-plus");

        runStep();

        GeometryAiClient.AiRequest sent = sentRequest();
        assertThat(sent.systemPrompt()).contains("提出時の共通ルール。");
        assertThat(sent.temperature()).isEqualTo(0.7);
        assertThat(sent.model()).isEqualTo("qwen-vl-max");
        // 固定は書き換えない（技術的な再試行で版を進めない）
        verify(recorder, never()).pinConfig(any(), anyString());
    }

    // ------------------------------------------------------------------ 6

    @Test
    @DisplayName("設定が壊れたタスクは次のタスクを止めない（実行の入口が固まらない）")
    void oneBrokenTaskDoesNotBlockTheNext() {
        GeometryAiRequestEntity broken = request("PREPROCESSED");
        broken.setSettingsSnapshotJson("{\"version\":1,\"config\":{\"version\":1,\"mode\":\"A\"}}");
        GeometryAiRequestEntity healthy = request("PREPROCESSED");
        healthy.setSettingsSnapshotJson(submittedSnapshot(submitted(), "qwen-vl-max", 1));
        when(requestMapper.findById(REQUEST_ID)).thenReturn(broken, healthy, healthy, healthy, healthy);

        Map<String, Object> first = runStep();
        Map<String, Object> second = runStep();

        assertThat(first).containsEntry("success", false);
        assertThat(second).containsEntry("success", true);
    }

    // ------------------------------------------------------------------ 7

    @Test
    @DisplayName("AI 生図以外のバッチは、今までどおり「いまの設定」を検証する")
    void otherBatchesKeepTheCurrentSettingsCheck() {
        // AI 生図ではないバッチ（授業ノート等）は、いまの設定が欠けていれば実行しない
        when(taskRegistry.findByCode("batC61")).thenReturn(new BatchTaskDefinition("batC61",
                BatchTaskType.C, "授業ノート", false, null, null, "CLASSROOM_AI",
                List.of(new SettingRequirement("CLASSROOM_AI", "CLASSROOM_AI_NOTE_SYSTEM_PROMPT"))));
        current.put("CLASSROOM_AI_NOTE_SYSTEM_PROMPT", "");

        String message;
        boolean success;
        try {
            Map<String, Object> result = batchService.rerunStep("batC61", "worker", null);
            success = Boolean.TRUE.equals(result.get("success"));
            message = String.valueOf(result.get("message"));
        } catch (RuntimeException cause) {
            success = false;
            message = cause.getMessage();
        }

        assertThat(success).isFalse();
        assertThat(message).contains("CLASSROOM_AI_NOTE_SYSTEM_PROMPT");
        verify(aiClient, never()).call(any());
        // 設定が欠けているので、業務処理（ハンドラ）は呼ばれない
        assertThat(handlersOf("batC61").calls).isZero();
    }

    /** テストで組み立てたハンドラ（呼ばれたかを見る）。 */
    private RecordingHandler handlersOf(String code) {
        return recordingHandler;
    }

    @Test
    @DisplayName("AI 生図のバッチでも、固定が無く今の設定も壊れていれば実行前検証で止まる")
    void legacyTaskWithoutSnapshotIsCheckedAgainstLiveSettings() {
        GeometryAiRequestEntity entity = request("PREPROCESSED");
        entity.setSettingsSnapshotJson(null);
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        current.put(AiFigureSettingKeys.SYSTEM_PROMPT, "");

        Map<String, Object> result = runStep();

        assertThat(result).containsEntry("success", false);
        verify(aiClient, never()).call(any());
        // 実行記録は作らない（設定検証で拒否したため）
        verify(executionMapper, never()).insert(any(BatchExecutionEntity.class));
    }

    @Test
    @DisplayName("実行前の設定検証は要求に固定した設定を見る（いまの設定は読まない）")
    void preflightResolvesTheTaskSnapshot() {
        GeometryAiRequestEntity entity = request("PREPROCESSED");
        entity.setSettingsSnapshotJson(submittedSnapshot(submitted(), "qwen-vl-max", 1));
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);
        current.put(AiFigureSettingKeys.SYSTEM_PROMPT, "");
        current.remove(AiFigureSettingKeys.ALLOWED_COMMANDS);

        Map<String, Object> result = runStep();

        assertThat(result).containsEntry("success", true);
        // いまの設定の検証（requireSettings）は呼ばれない
        verify(settingsService, never()).requireSettings(anyString(), any());
        verify(executionMapper).insert(any(BatchExecutionEntity.class));
    }

    @Test
    @DisplayName("固定した設定のモデル名が空なら、実行の直前に読めたモデルで固定する")
    void pinsTheModelWhenItWasNotPinnedAtSubmit() {
        GeometryAiRequestEntity entity = request("PREPROCESSED");
        // 提出時に AI_MODEL が未設定だった（モデル名が空のまま固定された）
        entity.setSettingsSnapshotJson(submittedSnapshot(submitted(), null, 2));
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);

        Map<String, Object> result = runStep();

        assertThat(result).containsEntry("success", true);
        ArgumentCaptor<String> pinned = ArgumentCaptor.forClass(String.class);
        verify(recorder).pinConfig(any(), pinned.capture());
        AiFigureConfig restored = AiFigureConfig.fromSnapshotJson(pinned.getValue()).orElseThrow();
        assertThat(restored.model()).isEqualTo("qwen-vl-max");
        // 実行版は提出時のものを保つ（技術的な固定で版を進めない）
        assertThat(restored.revision()).isEqualTo(2);
        assertThat(restored.temperature()).isEqualTo(0.7);
        assertThat(restored.outputFormat()).isEqualTo("JSON");
    }
}
