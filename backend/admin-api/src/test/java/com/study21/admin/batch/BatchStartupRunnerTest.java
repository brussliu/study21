package com.study21.admin.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 起動時バッチの**自動運転のスイッチ**（{@link BatchStartupRunner}）。
 *
 * <p>無効にすると {@code ApplicationReadyEvent} では何もしない（テストや手動運用のため）。
 * 業務の入口（{@code BatchService#runOnStartup}）はそのまま呼べるので、テストは
 * 「入口を差し替える」のではなく**スイッチの効き方**をここで固定する。</p>
 */
class BatchStartupRunnerTest {

    @Test
    @DisplayName("スイッチが無効なら、起動イベントでも起動時バッチを実行しない")
    void disabledSwitchRunsNothing() {
        BatchService batchService = mock(BatchService.class);
        BatchStartupRunner runner = new BatchStartupRunner(batchService, false);

        runner.onApplicationReady();

        verifyNoInteractions(batchService);
    }

    @Test
    @DisplayName("スイッチが有効なら（既定）、起動対象を順に実行する")
    void enabledSwitchRunsTheStartupTargets() {
        BatchService batchService = mock(BatchService.class);
        when(batchService.startupTargets()).thenReturn(List.of("batS01"));
        when(batchService.runOnStartup("batS01")).thenReturn(Map.of("executionId", 1L, "status", "SUCCESS"));
        BatchStartupRunner runner = new BatchStartupRunner(batchService, true);

        runner.onApplicationReady();

        verify(batchService).startupTargets();
        verify(batchService).runOnStartup("batS01");
    }

    @Test
    @DisplayName("既定の入口（引数 1 つ）は有効（正常環境の挙動を変えない）")
    void defaultEntryIsEnabled() {
        BatchService batchService = mock(BatchService.class);
        when(batchService.startupTargets()).thenReturn(List.of());
        BatchStartupRunner runner = new BatchStartupRunner(batchService);

        runner.onApplicationReady();

        verify(batchService).startupTargets();
        verify(batchService, never()).runOnStartup(org.mockito.ArgumentMatchers.anyString());
    }
}
