package com.study21.admin.geometryai;

import com.study21.admin.geometryai.processor.FigureProcessorA;
import com.study21.admin.geometryai.processor.FigureProcessorB;
import com.study21.admin.geometryai.processor.FigureProcessorC;
import com.study21.admin.geometryai.processor.FigureProcessorD;
import com.study21.admin.geometryai.processor.FigureProcessorRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.admin.geometryai.dto.AiResponseSchemaService;
import com.study21.admin.setting.SettingsService;
import com.study21.admin.setting.SettingsValidationException;
import com.study21.common.core.geometryai.AiFigureConfig;
import com.study21.common.core.geometryai.AiFigureSettingKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
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
 * 検証工程（AI を呼ばない・決定的）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>`GENERATED` を検証して `READY` にする（**メッセージはコマンドの規則の話に留める**）</li>
 *   <li>**中断した `VALIDATING` を回復できる**（受け付けないと確保しただけで終わり、
 *       同じ行を拾い続けて後ろの要求が進まない）</li>
 *   <li>`FAILED(VALIDATE)` ももう一度検証できる</li>
 *   <li>確定済み（`READY` / `REGISTERED` / `NEEDS_INPUT`）は何もしない</li>
 *   <li>設定不足・想定外の失敗は**要求行へ FAILED と理由を書く**</li>
 *   <li>許可リストと上限は**要求行に固定した設定**を使う（待ち行列中の設定変更で変わらない）</li>
 * </ol>
 */
class AiFigureValidateStepTest {

    private static final long REQUEST_ID = 71L;

    /** 幾何図形として作図できる AI の応答（モード A の DTO）。 */
    private static final String PROPOSAL = """
            {"判定":"GENERATABLE","確定図種":"GEOMETRY","説明":"三角形を作ります。","作図オブジェクト":[
              {"名前":"A","種類":"POINT","定義":"A = (0, 0)","出典":"FROM_IMAGE","精度":"EXACT"},
              {"名前":"B","種類":"POINT","定義":"B = (5, 0)","出典":"FROM_IMAGE","精度":"EXACT"},
              {"名前":"C","種類":"POINT","定義":"C = (0, 3)","出典":"FROM_IMAGE","精度":"EXACT"},
              {"名前":"poly","種類":"POLYGON","定義":"Polygon(A, B, C)","出典":"DERIVED","精度":"EXACT"}],
             "コマンド":["A = (0, 0)","B = (5, 0)","C = (0, 3)","poly = Polygon(A, B, C)"]}
            """;

    private GeometryAiRequestMapper requestMapper;
    private GeometryAiRequestRecorder recorder;
    private SettingsService settingsService;
    private GeometryAiConnectionResolver connectionResolver;
    private AiFigureTaskConfigResolver taskConfigResolver;
    private AiFigureValidateStep step;

    @BeforeEach
    void setUp() {
        requestMapper = mock(GeometryAiRequestMapper.class);
        recorder = mock(GeometryAiRequestRecorder.class);
        settingsService = mock(SettingsService.class);
        connectionResolver = mock(GeometryAiConnectionResolver.class);
        FigureProcessorRegistry registry = new FigureProcessorRegistry(List.of(
                new FigureProcessorA(), new FigureProcessorB(), new FigureProcessorC(), new FigureProcessorD()));
        // 設定の入口は生成と**同じもの**を使う（別の入口を作らないことをテストでも固定する）
        taskConfigResolver = new AiFigureTaskConfigResolver(
                new FigureProcessorSettings(settingsService, new AiResponseSchemaService(new ObjectMapper())),
                connectionResolver);
        step = new AiFigureValidateStep(requestMapper, recorder,
                new FigureOutputValidator(new GeometryCommandValidator()), registry, settingsService,
                taskConfigResolver);

        // 固定した設定が無い（歴史的な）行は、いまの設定を読んで検証する。
        // 生成と同じ入口を通るので、共通の設定は必須の分がそろっている必要がある
        Map<String, String> values = new LinkedHashMap<>();
        values.put(AiFigureSettingKeys.ENABLED, "true");
        values.put(AiFigureSettingKeys.PROVIDER, "qwen:4");
        values.put(AiFigureSettingKeys.OUTPUT_FORMAT, "JSON");
        values.put(AiFigureSettingKeys.SYSTEM_PROMPT, "共通のルールです。");
        values.put(AiFigureSettingKeys.TEMPERATURE, "0.2");
        values.put(AiFigureSettingKeys.MAX_COMPLETION_TOKENS, "4096");
        values.put(AiFigureSettingKeys.REQUEST_TIMEOUT_SECONDS, "120");
        values.put(AiFigureSettingKeys.RETRY_LIMIT, "0");
        values.put(AiFigureSettingKeys.ALLOWED_COMMANDS, "Point,Segment,Polygon,Text,Function");
        values.put(AiFigureSettingKeys.MAX_COMMANDS, "80");
        when(settingsService.requireSettings(anyString(), any())).thenReturn(values);
        when(settingsService.findGlobal(eq(AiFigureSettingKeys.PAGE), anyString())).thenReturn(java.util.Optional.empty());
    }

    private GeometryAiRequestEntity request(String status, String failedStage) {
        GeometryAiRequestEntity entity = new GeometryAiRequestEntity();
        entity.setRequestId(REQUEST_ID);
        entity.setRequestNo("AIG202609191200001234");
        entity.setStatusCode(status);
        entity.setFailedStage(failedStage);
        entity.setMode("A");
        entity.setRequestedOutputType("AUTO");
        entity.setFigureType("geometry");
        entity.setProposalJson(PROPOSAL);
        entity.setVersion(3);
        return entity;
    }

    /** 受付時に固定した設定（許可リストは Polygon を含まない＝固定版を使えば検証に落ちる）。 */
    private static String pinnedSnapshot(String allowedCommands) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(AiFigureSettingKeys.SYSTEM_PROMPT, "共通のルール。");
        values.put(AiFigureSettingKeys.PROVIDER, "qwen:4");
        values.put(AiFigureSettingKeys.ALLOWED_COMMANDS, allowedCommands);
        values.put(AiFigureSettingKeys.MAX_COMMANDS, "80");
        return AiFigureConfig.resolve("A", "batC51-A", values, "2026-09-19T00:00:00").toSnapshotJson(null);
    }

    @Test
    @DisplayName("GENERATED を検証して READY にし、コマンドの規則だけを確かめたと言う")
    void validatesGeneratedRequest() {
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("GENERATED", null));

        Map<String, Object> result = step.run(REQUEST_ID);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("commandCount")).isEqualTo(4);
        assertThat(String.valueOf(result.get("message")))
                .contains("コマンドの規則チェック")
                // 数学的な条件まで確かめたとは言わない（実際に実行するのは作図画面のブラウザ）
                .contains("数学的な条件の検証は行っていません");
        verify(recorder).markValidating(any());
        verify(recorder).updateReady(any());
        verify(recorder, never()).updateFailed(any());
    }

    @Test
    @DisplayName("中断した VALIDATING を回復できる（受け付けないと確保しただけで終わる）")
    void resumesInterruptedValidating() {
        // 働き手が確保した直後に落ちた行（状態は VALIDATING のまま）
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("VALIDATING", null));

        Map<String, Object> result = step.run(REQUEST_ID);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("commandCount")).isEqualTo(4);
        verify(recorder).updateReady(any());
    }

    @Test
    @DisplayName("FAILED(VALIDATE) はもう一度検証できる")
    void revalidatesFailedValidate() {
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("FAILED", "VALIDATE"));

        Map<String, Object> result = step.run(REQUEST_ID);

        assertThat(result.get("success")).isEqualTo(true);
        verify(recorder).updateReady(any());
    }

    @Test
    @DisplayName("待ち行列が確保に使う状態（VALIDATING）を、検証の入口も受け付ける（契約が一致している）")
    void acceptsTheStatusTheQueueClaimsWith() {
        // 待ち行列（GeometryAiTaskQueue）は確保した行を VALIDATING にする。
        // ここで受け付けないと「確保しただけで何もせず終わる」→ 同じ行を拾い続けて後ろが進まない
        for (String status : List.of("GENERATED", "VALIDATING")) {
            assertThat(AiFigureValidateStep.isValidateTarget(request(status, null))).as(status).isTrue();
        }
        assertThat(AiFigureValidateStep.isValidateTarget(request("FAILED", "VALIDATE"))).isTrue();
        // 生成待ち・処理中のものは検証の対象ではない（AI を呼ぶ工程の担当）
        for (String status : List.of("QUEUED", "PREPROCESSING", "PREPROCESSED", "GENERATING", "CANCELLED")) {
            assertThat(AiFigureValidateStep.isValidateTarget(request(status, null))).as(status).isFalse();
        }
    }

    @Test
    @DisplayName("確定済み（READY / REGISTERED / NEEDS_INPUT）は何もしない")
    void skipsAlreadySettled() {
        for (String status : List.of("READY", "REGISTERED", "NEEDS_INPUT")) {
            org.mockito.Mockito.reset(recorder);
            when(requestMapper.findById(REQUEST_ID)).thenReturn(request(status, null));

            Map<String, Object> result = step.run(REQUEST_ID);

            assertThat(result.get("skipped")).as(status).isEqualTo(true);
            verify(recorder, never()).markValidating(any());
        }
    }

    @Test
    @DisplayName("設定が不足していたら FAILED(VALIDATE) へ理由を書く（処理中のまま残さない）")
    void writesFailedWhenSettingsAreMissing() {
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("GENERATED", null));
        when(settingsService.requireSettings(anyString(), any())).thenThrow(
                new SettingsValidationException(List.of("setting_key=GEOMETRY_AI_ALLOWED_COMMANDS, 原因=設定値が未設定または空です")));

        assertThatThrownBy(() -> step.run(REQUEST_ID))
                .isInstanceOf(AiFigurePreprocessStep.GeometryAiStepException.class)
                .hasMessageContaining("AI の設定が不足しています");

        ArgumentCaptor<GeometryAiRequestEntity> captor = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(recorder).updateFailed(captor.capture());
        GeometryAiRequestEntity saved = captor.getValue();
        assertThat(saved.getFailedStage()).isEqualTo("VALIDATE");
        assertThat(saved.getErrorCode()).isEqualTo("CONFIG");
        assertThat(saved.getErrorMessage()).contains("AI の設定が不足しています").contains("ALLOWED_COMMANDS");
    }

    @Test
    @DisplayName("想定外の失敗も FAILED(VALIDATE) へ理由を書く")
    void writesFailedOnUnexpectedError() {
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("GENERATED", null));
        org.mockito.Mockito.doThrow(new IllegalStateException("記録に失敗しました"))
                .when(recorder).markValidating(any());

        assertThatThrownBy(() -> step.run(REQUEST_ID))
                .isInstanceOf(AiFigurePreprocessStep.GeometryAiStepException.class)
                .hasMessageContaining("想定外");

        ArgumentCaptor<GeometryAiRequestEntity> captor = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(recorder).updateFailed(captor.capture());
        assertThat(captor.getValue().getFailedStage()).isEqualTo("VALIDATE");
        assertThat(captor.getValue().getErrorCode()).isEqualTo("UNEXPECTED");
    }

    @Test
    @DisplayName("提案 JSON が読めないときは生成のやり直しを促す（FAILED(GENERATE) / NO_PROPOSAL）")
    void asksForRegenerationWhenProposalIsBroken() {
        GeometryAiRequestEntity entity = request("GENERATED", null);
        entity.setProposalJson("{ 壊れた");
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);

        assertThatThrownBy(() -> step.run(REQUEST_ID))
                .isInstanceOf(AiFigurePreprocessStep.GeometryAiStepException.class)
                .hasMessageContaining("もう一度生成");

        ArgumentCaptor<GeometryAiRequestEntity> captor = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(recorder).updateFailed(captor.capture());
        assertThat(captor.getValue().getFailedStage()).isEqualTo("GENERATE");
        assertThat(captor.getValue().getErrorCode()).isEqualTo("NO_PROPOSAL");
    }

    @Test
    @DisplayName("許可リストは要求行に固定した設定を使う（設定を変えても検証条件が変わらない）")
    void usesPinnedAllowedCommands() {
        GeometryAiRequestEntity entity = request("GENERATED", null);
        // 受付時は Polygon を許可していなかった（あとから設定に足しても、この要求は落ちる）
        entity.setSettingsSnapshotJson(pinnedSnapshot("Point,Segment,Text"));
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);

        assertThatThrownBy(() -> step.run(REQUEST_ID))
                .isInstanceOf(AiFigurePreprocessStep.GeometryAiStepException.class)
                .hasMessageContaining("Polygon");

        // 固定版があるので、いまの設定は読まない
        verify(settingsService, never()).requireSettings(anyString(), any());
    }

    @Test
    @DisplayName("固定した設定が壊れていたら FAILED(VALIDATE, CONFIG_SNAPSHOT) で止める（黙って切り替えない）")
    void failsOnBrokenSnapshot() {
        GeometryAiRequestEntity entity = request("GENERATED", null);
        entity.setSettingsSnapshotJson("{\"version\":1,\"config\":{\"version\":1,\"mode\":\"A\"}}");
        when(requestMapper.findById(REQUEST_ID)).thenReturn(entity);

        assertThatThrownBy(() -> step.run(REQUEST_ID))
                .isInstanceOf(AiFigurePreprocessStep.GeometryAiStepException.class)
                .hasMessageContaining("固定した設定を使えません");

        ArgumentCaptor<GeometryAiRequestEntity> captor = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(recorder).updateFailed(captor.capture());
        assertThat(captor.getValue().getFailedStage()).isEqualTo("VALIDATE");
        assertThat(captor.getValue().getErrorCode()).isEqualTo("CONFIG_SNAPSHOT");
        // いまの設定（有効な許可リスト）では検証しない
        verify(settingsService, never()).requireSettings(anyString(), any());
    }

    @Test
    @DisplayName("固定した設定が無い（歴史的な）行だけ、いまの設定を読む")
    void readsLiveSettingsWhenNothingIsPinned() {
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("GENERATED", null));

        Map<String, Object> result = step.run(REQUEST_ID);

        assertThat(result.get("success")).isEqualTo(true);
        verify(settingsService).requireSettings(eq("batC51-A"), any());
    }
}
