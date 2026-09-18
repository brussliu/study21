package com.study21.admin.geometryai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * バックエンドの働き手（画面からの起動に依存しない）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>待ち行列から 1 件取って流水線を回す（画面は何も呼ばなくてよい）</li>
 *   <li>検証待ち（GENERATED）は**検証だけ**を回す（AI を呼び直さない）</li>
 *   <li>1 件の失敗で働き手は止まらない（例外を飲み込んで次の周期へ）</li>
 *   <li>1 回の周期で処理するのは 1 件だけ（同時に走らせない）</li>
 * </ol>
 */
class GeometryAiWorkerTest {

    private GeometryAiTaskQueue queue;
    private AiFigurePipelineService pipelineService;
    private GeometryAiWorker worker;

    @BeforeEach
    void setUp() {
        queue = mock(GeometryAiTaskQueue.class);
        pipelineService = mock(AiFigurePipelineService.class);
        worker = new GeometryAiWorker(queue, pipelineService);
    }

    @Test
    @DisplayName("待ち行列から 1 件取って流水線を回す")
    void runsPipelineForClaimedRequest() {
        when(queue.claimForPipeline(anyInt())).thenReturn(12L);

        assertThat(worker.tick()).isEqualTo(12L);

        verify(pipelineService).run(eq(12L), anyString());
        // 検証だけの経路は使わない
        verify(pipelineService, never()).resumeValidation(anyLong(), anyString());
    }

    @Test
    @DisplayName("生成済み（検証待ち）は検証だけを回す（AI を呼び直さない）")
    void resumesValidationOnly() {
        when(queue.claimForPipeline(anyInt())).thenReturn(null);
        when(queue.claimForValidation(anyInt())).thenReturn(20L);

        assertThat(worker.tick()).isEqualTo(20L);

        verify(pipelineService).resumeValidation(eq(20L), anyString());
        verify(pipelineService, never()).run(anyLong(), anyString());
    }

    @Test
    @DisplayName("拾うものが無ければ何もしない")
    void doesNothingWhenQueueIsEmpty() {
        assertThat(worker.tick()).isNull();
        verify(pipelineService, never()).run(anyLong(), anyString());
        verify(pipelineService, never()).resumeValidation(anyLong(), anyString());
    }

    @Test
    @DisplayName("1 件の失敗で働き手は止まらない（理由はログに残す）")
    void keepsRunningAfterFailure() {
        when(queue.claimForPipeline(anyInt())).thenReturn(30L);
        doThrow(new AiFigurePreprocessStep.GeometryAiStepException("画像を取り込めませんでした。"))
                .when(pipelineService).run(eq(30L), anyString());

        // 例外を投げずに次の周期へ進める
        assertThat(worker.tick()).isEqualTo(30L);
    }

    @Test
    @DisplayName("1 回の周期で処理するのは 1 件だけ")
    void handlesOneRequestPerTick() {
        when(queue.claimForPipeline(anyInt())).thenReturn(40L).thenReturn(41L);

        assertThat(worker.tick()).isEqualTo(40L);
        assertThat(worker.tick()).isEqualTo(41L);

        verify(pipelineService, org.mockito.Mockito.times(2)).run(anyLong(), anyString());
    }
}
