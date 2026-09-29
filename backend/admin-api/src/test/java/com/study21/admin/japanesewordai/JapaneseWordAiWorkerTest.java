package com.study21.admin.japanesewordai;

import com.study21.admin.setting.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日本語単語の AI 取得の**働き手**（受付が積んだ {@code QUEUED} を実行する）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>この周期で確保した件だけを実行する（1 件ずつ確保するのは待ち行列の役目）</li>
 *   <li>並列数は<b>設定の {@code THREADS}</b>（4 バッチの最小値。どのバッチの上限も超えない）</li>
 *   <li>設定が読めないときは 1 件ずつ（安全側。設定が壊れていても進める）</li>
 *   <li><b>1 件の失敗で働き手は止まらない</b>（残りを続ける）</li>
 * </ol>
 */
class JapaneseWordAiWorkerTest {

    private JapaneseWordAiQueue queue;
    private JapaneseWordAiStep step;
    private SettingsService settingsService;
    private JapaneseWordAiWorker worker;

    @BeforeEach
    void setUp() {
        queue = mock(JapaneseWordAiQueue.class);
        step = mock(JapaneseWordAiStep.class);
        settingsService = mock(SettingsService.class);
        worker = new JapaneseWordAiWorker(queue, step, settingsService);
    }

    /**
     * 4 バッチの並列数（{@code BAT_C41_THREADS} など）。
     *
     * <p>設定は**設定キー**で読む（`findGlobal`）。画面のフィールドキー（`c25Threads`）を返す
     * {@code loadGlobalSettingFields} を使うと、キーが違うので**いつも読めない**（実際に起きた。2026-09-27）。</p>
     */
    private void stubThreads(int c41, int c42, int c43, int c44) {
        when(settingsService.findGlobal("JAPANESE_WORD_AI", "BAT_C41_THREADS"))
                .thenReturn(java.util.Optional.of(String.valueOf(c41)));
        when(settingsService.findGlobal("JAPANESE_WORD_AI", "BAT_C42_THREADS"))
                .thenReturn(java.util.Optional.of(String.valueOf(c42)));
        when(settingsService.findGlobal("JAPANESE_WORD_AI", "BAT_C43_THREADS"))
                .thenReturn(java.util.Optional.of(String.valueOf(c43)));
        when(settingsService.findGlobal("JAPANESE_WORD_AI", "BAT_C44_THREADS"))
                .thenReturn(java.util.Optional.of(String.valueOf(c44)));
    }

    private static JapaneseWordAiMapper.GenerationRow claimed(long generationId, long wordId) {
        JapaneseWordAiMapper.GenerationRow row = new JapaneseWordAiMapper.GenerationRow();
        row.setGenerationId(generationId);
        row.setWordId(wordId);
        row.setContentType("A_DETAIL");
        row.setContentVersion(1);
        return row;
    }

    @Test
    @DisplayName("確保した件だけを実行して、実行した件数を返す")
    void runsClaimedItems() {
        when(queue.claim(anyInt(), anyInt())).thenReturn(List.of(claimed(11L, 101L), claimed(12L, 102L)));
        when(step.runItem(any())).thenReturn(true);

        assertThat(worker.tick()).isEqualTo(2);

        verify(step).runItem(org.mockito.ArgumentMatchers.argThat(item -> item.getGenerationId() == 11L));
        verify(step).runItem(org.mockito.ArgumentMatchers.argThat(item -> item.getGenerationId() == 12L));
    }

    @Test
    @DisplayName("並列数は設定の THREADS（4 バッチの最小値。どのバッチの上限も超えない）")
    void usesConfiguredThreads() {
        stubThreads(5, 3, 3, 3);
        when(queue.claim(anyInt(), anyInt())).thenReturn(List.of());

        worker.tick();

        // 5 / 3 / 3 / 3 の最小＝3
        verify(queue).claim(3, JapaneseWordAiWorker.STALE_MINUTES);
    }

    @Test
    @DisplayName("設定が読めないときは 1 件ずつ（設定が壊れていても止まらない）")
    void fallsBackToOneWhenSettingsUnreadable() {
        when(settingsService.findGlobal(anyString(), anyString()))
                .thenThrow(new IllegalStateException("設定が読めません"));
        when(queue.claim(anyInt(), anyInt())).thenReturn(List.of());

        worker.tick();

        verify(queue).claim(1, JapaneseWordAiWorker.STALE_MINUTES);
    }

    @Test
    @DisplayName("拾うものが無ければ何も実行しない")
    void doesNothingWhenEmpty() {
        when(queue.claim(anyInt(), anyInt())).thenReturn(List.of());

        assertThat(worker.tick()).isZero();

        verify(step, never()).runItem(any());
    }

    @Test
    @DisplayName("1 件が例外でも、残りの件は実行する（働き手は止まらない）")
    void keepsGoingWhenOneItemFails() {
        JapaneseWordAiMapper.GenerationRow first = claimed(11L, 101L);
        JapaneseWordAiMapper.GenerationRow second = claimed(12L, 102L);
        when(queue.claim(anyInt(), anyInt())).thenReturn(List.of(first, second));
        when(step.runItem(first)).thenThrow(new IllegalStateException("想定外"));
        when(step.runItem(second)).thenReturn(true);

        assertThat(worker.tick()).isEqualTo(2);

        verify(step, times(2)).runItem(any());
    }

    @Test
    @DisplayName("失敗（false）も「実行した」として数える（同じ周期で拾い直さない）")
    void countsFailedItems() {
        when(queue.claim(anyInt(), anyInt())).thenReturn(List.of(claimed(11L, 101L)));
        when(step.runItem(any())).thenReturn(false);

        assertThat(worker.tick()).isEqualTo(1);

        verify(step, times(1)).runItem(any());
    }
}
