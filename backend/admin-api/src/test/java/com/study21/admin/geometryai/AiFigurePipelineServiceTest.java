package com.study21.admin.geometryai;

import com.study21.admin.batch.BatchService;
import com.study21.common.core.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流水線（前処理 → AI 生成 → 検証）の入口。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>3 工程を順に進め、止まった工程を返す</li>
 *   <li>**止まったら必ず要求行へ FAILED と理由を書く**（処理中のまま残して拾い直され続けない）</li>
 *   <li>生成が「成功しなかった」と言ったら FAILED(GENERATE) を書く（設定・プロンプトの失敗で
 *       `PREPROCESSED` のまま残ると、働き手が**同じ行を永遠に拾って後ろが進まない**）</li>
 *   <li>「既に実行中」（ConflictException）は失敗ではない（走っている実行の結果を壊さない）</li>
 *   <li>既に理由の書いてある FAILED は上書きしない</li>
 *   <li>検証だけの経路も同じように守る（AI は呼び直さない）</li>
 * </ol>
 */
class AiFigurePipelineServiceTest {

    private static final long REQUEST_ID = 88L;

    private BatchService batchService;
    private GeometryAiRequestMapper requestMapper;
    private GeometryAiRequestRecorder recorder;
    private AiFigurePreprocessStep preprocessStep;
    private AiFigureValidateStep validateStep;
    private AiFigurePipelineService pipeline;

    @BeforeEach
    void setUp() {
        batchService = mock(BatchService.class);
        requestMapper = mock(GeometryAiRequestMapper.class);
        recorder = mock(GeometryAiRequestRecorder.class);
        preprocessStep = mock(AiFigurePreprocessStep.class);
        validateStep = mock(AiFigureValidateStep.class);
        pipeline = new AiFigurePipelineService(batchService, requestMapper, recorder, preprocessStep, validateStep);
    }

    private GeometryAiRequestEntity request(String status) {
        GeometryAiRequestEntity entity = new GeometryAiRequestEntity();
        entity.setRequestId(REQUEST_ID);
        entity.setRequestNo("AIG202609191200001234");
        entity.setStatusCode(status);
        entity.setMode("A");
        entity.setVersion(5);
        return entity;
    }

    private static Map<String, Object> stepResult(boolean success, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("message", message);
        return result;
    }

    // ------------------------------------------------------------------ 正常

    @Test
    @DisplayName("前処理 → 生成 → 検証の順に進み、最後まで行けば stoppedAt は null")
    void runsAllThreeStages() {
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("QUEUED"));
        when(batchService.rerunStep(eq("batC51-A"), anyString(), any()))
                .thenReturn(stepResult(true, "生成しました。"));

        Map<String, Object> result = pipeline.run(REQUEST_ID, "worker");

        assertThat(result.get("completed")).isEqualTo(true);
        assertThat(result.get("stoppedAt")).isNull();
        assertThat(result.get("steps")).asList().hasSize(1);
        verify(preprocessStep).run(REQUEST_ID);
        verify(validateStep).run(REQUEST_ID);
        verify(recorder, never()).updateFailed(any());
    }

    @Test
    @DisplayName("前処理で止まったら、その工程を返して FAILED を書く")
    void stopsAtPreprocess() {
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("QUEUED"));
        doThrow(new AiFigurePreprocessStep.GeometryAiStepException("画像を取り込めませんでした。"))
                .when(preprocessStep).run(REQUEST_ID);

        Map<String, Object> result = pipeline.run(REQUEST_ID, "worker");

        assertThat(result.get("stoppedAt")).isEqualTo("PREPROCESS");
        verify(recorder).updateFailed(any());
        verify(batchService, never()).rerunStep(anyString(), anyString(), any());
    }

    // ------------------------------------------------------------------ 止まった行を残さない

    @Test
    @DisplayName("生成が失敗したら FAILED(GENERATE) を理由つきで書く（PREPROCESSED のまま残さない）")
    void writesFailedWhenGenerateFails() {
        when(requestMapper.findById(REQUEST_ID))
                .thenReturn(request("QUEUED"))
                // 2 回目（失敗の書き込み）は前処理で進んだ状態を返す
                .thenReturn(request("PREPROCESSED"));
        when(batchService.rerunStep(eq("batC51-A"), anyString(), any()))
                .thenReturn(stepResult(false, "AI の設定が不足しています。"));

        Map<String, Object> result = pipeline.run(REQUEST_ID, "worker");

        assertThat(result.get("stoppedAt")).isEqualTo("GENERATE");
        ArgumentCaptor<GeometryAiRequestEntity> captor = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(recorder).updateFailed(captor.capture());
        assertThat(captor.getValue().getFailedStage()).isEqualTo("GENERATE");
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("AI の設定が不足しています。");
        verify(validateStep, never()).run(anyLong());
    }

    @Test
    @DisplayName("生成で想定外の例外が出ても FAILED(GENERATE) にする")
    void writesFailedOnUnexpectedGenerateError() {
        when(requestMapper.findById(REQUEST_ID))
                .thenReturn(request("QUEUED"))
                .thenReturn(request("PREPROCESSED"));
        when(batchService.rerunStep(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("プロンプトの変数を展開できませんでした"));

        Map<String, Object> result = pipeline.run(REQUEST_ID, "worker");

        assertThat(result.get("stoppedAt")).isEqualTo("GENERATE");
        ArgumentCaptor<GeometryAiRequestEntity> captor = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(recorder).updateFailed(captor.capture());
        assertThat(captor.getValue().getErrorCode()).isEqualTo("STAGE_FAILED");
        assertThat(captor.getValue().getErrorMessage()).contains("変数を展開できませんでした");
    }

    @Test
    @DisplayName("固定した設定が使えない（壊れたスナップショット）ときは CONFIG_SNAPSHOT で書き戻す")
    void writesConfigSnapshotFailure() {
        when(requestMapper.findById(REQUEST_ID))
                .thenReturn(request("QUEUED"))
                .thenReturn(request("PREPROCESSED"));
        // 実行前の設定検証（要求に固定した設定）で拒否されたときの例外
        when(batchService.rerunStep(anyString(), anyString(), any()))
                .thenThrow(new AiFigureConfigException("この要求に固定した設定を使えません: 足りない項目があります。"));

        Map<String, Object> result = pipeline.run(REQUEST_ID, "worker");

        assertThat(result.get("stoppedAt")).isEqualTo("GENERATE");
        ArgumentCaptor<GeometryAiRequestEntity> captor = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(recorder).updateFailed(captor.capture());
        assertThat(captor.getValue().getFailedStage()).isEqualTo("GENERATE");
        assertThat(captor.getValue().getErrorCode()).isEqualTo("CONFIG_SNAPSHOT");
        assertThat(captor.getValue().getErrorMessage()).contains("固定した設定を使えません");
    }

    @Test
    @DisplayName("既に理由の書いてある FAILED は上書きしない")
    void doesNotOverwriteExistingFailure() {
        when(requestMapper.findById(REQUEST_ID))
                .thenReturn(request("QUEUED"))
                .thenReturn(request("FAILED"));
        when(batchService.rerunStep(anyString(), anyString(), any()))
                .thenReturn(stepResult(false, "だめでした"));

        pipeline.run(REQUEST_ID, "worker");

        verify(recorder, never()).updateFailed(any());
    }

    @Test
    @DisplayName("「既に実行中」は失敗ではない（走っている実行の結果を壊さない）")
    void doesNotFailWhenAnotherRunIsInProgress() {
        when(requestMapper.findById(REQUEST_ID)).thenReturn(request("QUEUED"));
        when(batchService.rerunStep(anyString(), anyString(), any()))
                .thenThrow(new ConflictException("バッチは既に実行中です: batC51-A"));

        Map<String, Object> result = pipeline.run(REQUEST_ID, "worker");

        assertThat(result.get("stoppedAt")).isEqualTo("GENERATE");
        verify(recorder, never()).updateFailed(any());
    }

    @Test
    @DisplayName("検証で想定外の例外が出ても FAILED(VALIDATE) にする")
    void writesFailedOnUnexpectedValidateError() {
        when(requestMapper.findById(REQUEST_ID))
                .thenReturn(request("QUEUED"))
                .thenReturn(request("GENERATED"));
        when(batchService.rerunStep(anyString(), anyString(), any()))
                .thenReturn(stepResult(true, "生成しました。"));
        doThrow(new IllegalStateException("設定が読めませんでした")).when(validateStep).run(REQUEST_ID);

        Map<String, Object> result = pipeline.run(REQUEST_ID, "worker");

        assertThat(result.get("stoppedAt")).isEqualTo("VALIDATE");
        ArgumentCaptor<GeometryAiRequestEntity> captor = ArgumentCaptor.forClass(GeometryAiRequestEntity.class);
        verify(recorder).updateFailed(captor.capture());
        assertThat(captor.getValue().getFailedStage()).isEqualTo("VALIDATE");
    }

    // ------------------------------------------------------------------ 検証だけの経路

    @Test
    @DisplayName("検証だけの経路は AI を呼ばず、止まったら同じように FAILED を書く")
    void resumeValidationWritesFailure() {
        when(requestMapper.findById(REQUEST_ID))
                .thenReturn(request("VALIDATING"))
                .thenReturn(request("VALIDATING"));
        doThrow(new IllegalStateException("検証できません")).when(validateStep).run(REQUEST_ID);

        Map<String, Object> result = pipeline.resumeValidation(REQUEST_ID, "worker");

        assertThat(result.get("stoppedAt")).isEqualTo("VALIDATE");
        assertThat(result.get("skippedPreprocessAndGenerate")).isEqualTo(true);
        verify(batchService, never()).rerunStep(anyString(), anyString(), any());
        verify(preprocessStep, never()).run(anyLong());
        verify(recorder).updateFailed(any());
    }

    @Test
    @DisplayName("検証だけの経路が成功したら FAILED は書かない")
    void resumeValidationSucceeds() {
        when(requestMapper.findById(REQUEST_ID))
                .thenReturn(request("VALIDATING"))
                .thenReturn(request("READY"));

        Map<String, Object> result = pipeline.resumeValidation(REQUEST_ID, "worker");

        assertThat(result.get("completed")).isEqualTo(true);
        assertThat(result.get("status")).isEqualTo("READY");
        verify(recorder, never()).updateFailed(any());
    }
}
