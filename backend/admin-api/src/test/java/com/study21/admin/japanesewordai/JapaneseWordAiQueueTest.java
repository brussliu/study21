package com.study21.admin.japanesewordai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日本語単語の AI 取得の**待ち行列**（受付と取り出し）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li><b>受付は AI を呼ばない</b>。語 × 内容種別の {@code QUEUED} 行を作るだけ
 *       （画面は待たない＝非同期。2026-09-27 改修）</li>
 *   <li>実行中の行（{@code QUEUED} / {@code RUNNING}）がある語は**入れ直さない**
 *       （同じ語を二重に走らせない＝AI を二重に呼ばない）</li>
 *   <li><b>成功済みを除かない</b>。除くのは実行中の行だけ（「すべて再取得」を
 *       取り直しとして成立させる。受付の入口は SQL の対象選定を使わない）</li>
 *   <li>働き手の取り出しは**状態を条件にした更新**で確定する（他の働き手が先に取ったら取らない）</li>
 * </ol>
 */
class JapaneseWordAiQueueTest {

    private JapaneseWordAiMapper mapper;
    private JapaneseWordAiQueue queue;

    @BeforeEach
    void setUp() {
        mapper = mock(JapaneseWordAiMapper.class);
        queue = new JapaneseWordAiQueue(mapper);
    }

    private static JapaneseWordAiMapper.GenerationRow generation(long id, long wordId, String contentType,
                                                                 int version) {
        JapaneseWordAiMapper.GenerationRow row = new JapaneseWordAiMapper.GenerationRow();
        row.setGenerationId(id);
        row.setWordId(wordId);
        row.setContentType(contentType);
        row.setContentVersion(version);
        return row;
    }

    @Test
    @DisplayName("受付: 語 × 内容種別の QUEUED 行を作る（AI は呼ばない・対象選定もしない）")
    void acceptsIntoQueue() {
        when(mapper.findActiveGeneration(101L, "A_DETAIL")).thenReturn(null);
        when(mapper.nextGenerationVersion(101L, "A_DETAIL")).thenReturn(3);
        when(mapper.insertQueuedGeneration(any())).thenAnswer(invocation -> {
            JapaneseWordAiMapper.GenerationRow row = invocation.getArgument(0);
            row.setGenerationId(7L);
            return 1;
        });

        Map<String, Object> result = queue.accept("DETAIL", List.of(101L));

        assertThat(result).containsEntry("accepted", 1).containsEntry("reused", 0);
        // 画面はこの ID で進み具合を見る（一覧の「取得状態」は取り直しの判定に使えない）
        assertThat(result).containsEntry("generationIds", List.of(7L));

        ArgumentCaptor<JapaneseWordAiMapper.GenerationRow> row =
                ArgumentCaptor.forClass(JapaneseWordAiMapper.GenerationRow.class);
        verify(mapper).insertQueuedGeneration(row.capture());
        assertThat(row.getValue().getWordId()).isEqualTo(101L);
        assertThat(row.getValue().getContentType()).isEqualTo("A_DETAIL");
        // 版は受付時に決める（問題の行と同じ版を入れる規則のため）
        assertThat(row.getValue().getContentVersion()).isEqualTo(3);

        // AI を呼ばない入口なので、対象の選定（成功済みを除く SQL）は使わない
        verify(mapper, never()).findTargets(anyString(), any(), anyInt());
    }

    @Test
    @DisplayName("受付: 実行中の行は入れ直さないが、進み具合を見る ID には含める")
    void reusesActiveGeneration() {
        when(mapper.findActiveGeneration(101L, "A_DETAIL")).thenReturn(55L);

        Map<String, Object> result = queue.accept("DETAIL", List.of(101L));

        assertThat(result).containsEntry("accepted", 0).containsEntry("reused", 1);
        assertThat(result).containsEntry("generationIds", List.of(55L));
        verify(mapper, never()).insertQueuedGeneration(any());
        // 版を進めない（入れ直さないので採番も不要）
        verify(mapper, never()).nextGenerationVersion(anyLong(), anyString());
    }

    @Test
    @DisplayName("進み具合: 生の状態を数える（終わっていない件数・成功・失敗）")
    void reportsProgress() {
        when(mapper.countGenerationsByState(List.of(7L, 8L))).thenReturn(Map.of(
                "pending", 1L, "succeeded", 1L, "failed", 0L));

        Map<String, Object> progress = queue.progress(List.of(7L, 8L));

        assertThat(progress).containsEntry("pending", 1)
                .containsEntry("succeeded", 1).containsEntry("failed", 0);
    }

    @Test
    @DisplayName("進み具合: ID が無ければ数えない（DB を引かない）")
    void reportsNoProgressWithoutIds() {
        Map<String, Object> progress = queue.progress(List.of());

        assertThat(progress).containsEntry("pending", 0)
                .containsEntry("succeeded", 0).containsEntry("failed", 0);
        verify(mapper, never()).countGenerationsByState(any());
    }

    @Test
    @DisplayName("受付: 内容種別ごとに行を作る（C は C1 と C2 の 2 行）")
    void acceptsEachContentType() {
        when(mapper.findActiveGeneration(anyLong(), anyString())).thenReturn(null);
        when(mapper.nextGenerationVersion(anyLong(), anyString())).thenReturn(1);
        // 実際は入れた行に採番される（0 のままだと「同時受付で負けた」扱いになる）
        when(mapper.insertQueuedGeneration(any())).thenAnswer(invocation -> {
            JapaneseWordAiMapper.GenerationRow row = invocation.getArgument(0);
            row.setGenerationId(row.getWordId() + ("C1_READING".equals(row.getContentType()) ? 1 : 2));
            return 1;
        });

        Map<String, Object> result = queue.accept("C", List.of(101L));

        assertThat(result).containsEntry("accepted", 2).containsEntry("reused", 0);
        verify(mapper).insertQueuedGeneration(org.mockito.ArgumentMatchers.argThat(
                r -> "C1_READING".equals(r.getContentType())));
        verify(mapper).insertQueuedGeneration(org.mockito.ArgumentMatchers.argThat(
                r -> "C2_KANJI".equals(r.getContentType())));
    }

    @Test
    @DisplayName("受付: 同時に受付けて入らなかったら、相手の行を自分の結果に入れて reused に数える")
    void countsLostRaceAsReused() {
        when(mapper.findActiveGeneration(101L, "A_DETAIL")).thenReturn(null, 55L);
        when(mapper.nextGenerationVersion(101L, "A_DETAIL")).thenReturn(1);
        // 入れる SQL は「実行中の行が無いときだけ」なので、負けた側は 0 行（採番されない）
        when(mapper.insertQueuedGeneration(any())).thenReturn(0);

        Map<String, Object> result = queue.accept("DETAIL", List.of(101L));

        assertThat(result).containsEntry("accepted", 0).containsEntry("reused", 1);
        assertThat(result).containsEntry("generationIds", List.of(55L));
    }

    @Test
    @DisplayName("受付: 成功済みでも入れる（除くのは実行中の行だけ＝「すべて再取得」を取り直しにする）")
    void acceptsEvenWhenSucceeded() {
        // 成功済みの語は findActiveGeneration が null を返す（QUEUED / RUNNING だけを見る SQL）
        when(mapper.findActiveGeneration(101L, "A_DETAIL")).thenReturn(null);
        when(mapper.nextGenerationVersion(101L, "A_DETAIL")).thenReturn(2);
        when(mapper.insertQueuedGeneration(any())).thenAnswer(invocation -> {
            JapaneseWordAiMapper.GenerationRow row = invocation.getArgument(0);
            row.setGenerationId(7L);
            return 1;
        });

        Map<String, Object> result = queue.accept("DETAIL", List.of(101L));

        assertThat(result).containsEntry("accepted", 1);
        verify(mapper).insertQueuedGeneration(any());
    }

    @Test
    @DisplayName("受付: 語が無ければ何も入れない")
    void acceptsNothingWithoutWords() {
        Map<String, Object> result = queue.accept("DETAIL", List.of());

        assertThat(result).containsEntry("accepted", 0).containsEntry("reused", 0);
        verify(mapper, never()).insertQueuedGeneration(any());
    }

    @Test
    @DisplayName("取り出し: 1 件を確保して RUNNING にする（待機中と、落ちたままの実行中）")
    void claimsOneItem() {
        when(mapper.findClaimableGenerationId(5)).thenReturn(77L);
        when(mapper.markGenerationClaimed(eq(77L), any())).thenReturn(1);
        when(mapper.findGeneration(77L)).thenReturn(generation(77L, 101L, "A_DETAIL", 1));

        List<JapaneseWordAiMapper.GenerationRow> claimed = queue.claim(1, 5);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).getGenerationId()).isEqualTo(77L);
        assertThat(claimed.get(0).getWordId()).isEqualTo(101L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> from = ArgumentCaptor.forClass(List.class);
        verify(mapper).markGenerationClaimed(eq(77L), from.capture());
        assertThat(from.getValue()).containsExactlyInAnyOrder("QUEUED", "RUNNING");
    }

    @Test
    @DisplayName("取り出し: 最大 limit 件まで（1 件ずつ確保する）")
    void claimsUpToLimit() {
        when(mapper.findClaimableGenerationId(5)).thenReturn(11L, 12L, null);
        when(mapper.markGenerationClaimed(anyLong(), any())).thenReturn(1);
        when(mapper.findGeneration(11L)).thenReturn(generation(11L, 101L, "A_DETAIL", 1));
        when(mapper.findGeneration(12L)).thenReturn(generation(12L, 102L, "A_DETAIL", 1));

        List<JapaneseWordAiMapper.GenerationRow> claimed = queue.claim(3, 5);

        assertThat(claimed).extracting(JapaneseWordAiMapper.GenerationRow::getGenerationId)
                .containsExactly(11L, 12L);
    }

    @Test
    @DisplayName("取り出し: 他の働き手が先に取っていたら含めない（二重に走らせない）")
    void skipsWhenAlreadyClaimed() {
        when(mapper.findClaimableGenerationId(5)).thenReturn(77L, null);
        when(mapper.markGenerationClaimed(eq(77L), any())).thenReturn(0);

        assertThat(queue.claim(1, 5)).isEmpty();
        verify(mapper, never()).findGeneration(anyLong());
    }

    @Test
    @DisplayName("取り出し: 拾うものが無ければ空（状態は触らない）")
    void claimsNothingWhenEmpty() {
        when(mapper.findClaimableGenerationId(5)).thenReturn(null);

        assertThat(queue.claim(3, 5)).isEmpty();
        verify(mapper, never()).markGenerationClaimed(anyLong(), any());
    }
}
