package com.study21.admin.geometryai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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
 * 仕事の取り出し（要求行を待ち行列として使う）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>生成までの工程と「検証だけ」を分けて取り出す（生成済みを生成からやり直すと**二重課金**）</li>
 *   <li>取った行だけを進める（他の働き手が先に取っていたら何もしない＝二重処理しない）</li>
 *   <li>拾うものが無ければ何もしない</li>
 * </ol>
 */
class GeometryAiTaskQueueTest {

    private GeometryAiRequestMapper requestMapper;
    private GeometryAiTaskQueue queue;

    @BeforeEach
    void setUp() {
        requestMapper = mock(GeometryAiRequestMapper.class);
        queue = new GeometryAiTaskQueue(requestMapper);
    }

    @Test
    @DisplayName("生成までの工程の 1 件を確保して読み取り中にする")
    void claimsForPipeline() {
        when(requestMapper.findClaimablePipelineId(anyInt())).thenReturn(77L);
        when(requestMapper.markClaimed(anyLong(), anyString(), any())).thenReturn(1);

        assertThat(queue.claimForPipeline(5)).isEqualTo(77L);

        verify(requestMapper).findClaimablePipelineId(5);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<String>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(requestMapper).markClaimed(eq(77L), eq("PREPROCESSING"), captor.capture());
        // 対象は「待機中」「読み取り済み（生成待ち）」と、落ちたままの「読み取り中」「生成中」
        assertThat(captor.getValue()).containsExactlyInAnyOrder("QUEUED", "PREPROCESSED", "PREPROCESSING", "GENERATING");
        // **FAILED は拾わない**（利用者の【もう一度生成】だけが再開する＝黙って課金しない）
        assertThat(captor.getValue()).doesNotContain("FAILED");
    }

    @Test
    @DisplayName("検証だけの 1 件を確保する（生成済み・落ちた検証中）")
    void claimsForValidation() {
        when(requestMapper.findClaimableValidationId(anyInt())).thenReturn(88L);
        when(requestMapper.markClaimed(anyLong(), anyString(), any())).thenReturn(1);

        assertThat(queue.claimForValidation(5)).isEqualTo(88L);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<String>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(requestMapper).markClaimed(eq(88L), eq("VALIDATING"), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder("GENERATED", "VALIDATING");
    }

    @Test
    @DisplayName("他の働き手が先に取っていたら何も返さない（二重処理しない）")
    void doesNotReturnWhenAlreadyClaimed() {
        when(requestMapper.findClaimablePipelineId(anyInt())).thenReturn(77L);
        when(requestMapper.markClaimed(anyLong(), anyString(), any())).thenReturn(0);

        assertThat(queue.claimForPipeline(5)).isNull();
    }

    @Test
    @DisplayName("拾うものが無ければ更新もしない")
    void doesNothingWhenEmpty() {
        when(requestMapper.findClaimablePipelineId(anyInt())).thenReturn(null);
        when(requestMapper.findClaimableValidationId(anyInt())).thenReturn(null);

        assertThat(queue.claimForPipeline(5)).isNull();
        assertThat(queue.claimForValidation(5)).isNull();
        verify(requestMapper, never()).markClaimed(anyLong(), anyString(), any());
    }
}
