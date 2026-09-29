package com.study21.admin.englishessay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 添削の働き手（受付が積んだ {@code QUEUED} を実行する）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>この周期で確保した件だけを実行する（1 件ずつ確保するのは待ち行列の役目）</li>
 *   <li>1 周期の件数は 1 件（{@code ENGLISH_ESSAY_*} に並列数の設定が無いため）</li>
 *   <li>1 件の失敗で止まらない。失敗も「実行した」として数える</li>
 *   <li>実行中のまま残った行は 5 分より古ければ拾い直す</li>
 * </ol>
 */
class EnglishEssayAiWorkerTest {

    private EnglishEssayAiQueue queue;
    private EnglishEssayGradingStep step;
    private EnglishEssayAiWorker worker;

    @BeforeEach
    void setUp() {
        queue = mock(EnglishEssayAiQueue.class);
        step = mock(EnglishEssayGradingStep.class);
        worker = new EnglishEssayAiWorker(queue, step);
    }

    private static EnglishEssayAiMapper.GradingRow row(long gradingId) {
        EnglishEssayAiMapper.GradingRow item = new EnglishEssayAiMapper.GradingRow();
        item.setGradingId(gradingId);
        item.setEssayId(900001L);
        item.setRound(1);
        return item;
    }

    @Test
    @DisplayName("確保した件だけを実行して、実行した件数を返す")
    void runsClaimedItems() {
        when(queue.claim(anyInt(), anyInt())).thenReturn(List.of(row(11L), row(12L)));
        when(step.runItem(any())).thenReturn(true);

        assertThat(worker.tick()).isEqualTo(2);

        verify(step).runItem(org.mockito.ArgumentMatchers.argThat(item -> item.getGradingId() == 11L));
        verify(step).runItem(org.mockito.ArgumentMatchers.argThat(item -> item.getGradingId() == 12L));
    }

    @Test
    @DisplayName("1 周期の件数は 1 件。実行中が残っていれば 5 分より古いものを拾い直す")
    void usesOneItemPerTick() {
        when(queue.claim(anyInt(), anyInt())).thenReturn(List.of());

        worker.tick();

        verify(queue).claim(1, EnglishEssayAiWorker.STALE_MINUTES);
    }

    @Test
    @DisplayName("拾うものが無ければ何も実行しない")
    void doesNothingWhenEmpty() {
        when(queue.claim(anyInt(), anyInt())).thenReturn(List.of());

        assertThat(worker.tick()).isZero();

        verify(step, never()).runItem(any());
    }

    @Test
    @DisplayName("失敗（false）も「実行した」として数える（同じ周期で拾い直さない）")
    void countsFailedItems() {
        when(queue.claim(anyInt(), anyInt())).thenReturn(List.of(row(11L)));
        when(step.runItem(any())).thenReturn(false);

        assertThat(worker.tick()).isEqualTo(1);
    }

    @Test
    @DisplayName("1 件が例外でも、残りの件は実行する（働き手は止まらない）")
    void keepsGoingWhenOneItemFails() {
        EnglishEssayAiMapper.GradingRow first = row(11L);
        EnglishEssayAiMapper.GradingRow second = row(12L);
        when(queue.claim(anyInt(), anyInt())).thenReturn(List.of(first, second));
        when(step.runItem(first)).thenThrow(new IllegalStateException("想定外"));
        when(step.runItem(second)).thenReturn(true);

        assertThat(worker.tick()).isEqualTo(2);

        verify(step, times(2)).runItem(any());
    }
}
