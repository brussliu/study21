package com.study21.admin.englishessay;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 添削の待ち行列（受付と取り出し）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>受付は <b>そのときの写し</b>（級・題・設問・本文・語数）を {@code QUEUED} で積むだけ（AI は呼ばない）</li>
 *   <li>回数は「既存の最大 + 1」。指定された回数が既にあれば 409（{@code UNIQUE(英作文ID, 回数)}）</li>
 *   <li>実行中の添削があれば受け付けない（「前の添削が終わっていません」）</li>
 *   <li>働き手の取り出しは状態を条件にした更新で確定する（二重に走らせない）</li>
 * </ol>
 */
class EnglishEssayAiQueueTest {

    private EnglishEssayAiMapper mapper;
    private EnglishEssayAiQueue queue;

    @BeforeEach
    void setUp() {
        mapper = mock(EnglishEssayAiMapper.class);
        queue = new EnglishEssayAiQueue(mapper);
    }

    private static EnglishEssayAiMapper.EssayRow essay() {
        EnglishEssayAiMapper.EssayRow row = new EnglishEssayAiMapper.EssayRow();
        row.setEssayId(900001L);
        row.setUserAccountId(2L);
        row.setLevel("PRE1");
        row.setTitleJa("読書と動画");
        row.setTitleZh("读书与视频");
        row.setQuestionText("Do you agree?");
        row.setEssayText("I think reading is better.");
        row.setWordCount(5);
        row.setStateCode("A");
        return row;
    }

    private void stubInsert(long gradingId) {
        when(mapper.insertGrading(any())).thenAnswer(invocation -> {
            EnglishEssayAiMapper.GradingRow row = invocation.getArgument(0);
            row.setGradingId(gradingId);
            return 1;
        });
    }

    @Test
    @DisplayName("受付: そのときの写しを QUEUED で積み、回数は「既存の最大 + 1」")
    void acceptsWithSnapshot() {
        when(mapper.findEssay(900001L)).thenReturn(essay());
        when(mapper.findActiveGrading(900001L)).thenReturn(null);
        when(mapper.nextRound(900001L)).thenReturn(2);
        stubInsert(501L);

        EnglishEssayAiQueue.Accepted accepted = queue.accept(900001L, null);

        assertThat(accepted.gradingId()).isEqualTo(501L);
        assertThat(accepted.round()).isEqualTo(2);

        ArgumentCaptor<EnglishEssayAiMapper.GradingRow> row =
                ArgumentCaptor.forClass(EnglishEssayAiMapper.GradingRow.class);
        verify(mapper).insertGrading(row.capture());
        assertThat(row.getValue().getEssayId()).isEqualTo(900001L);
        assertThat(row.getValue().getRound()).isEqualTo(2);
        assertThat(row.getValue().getLevel()).isEqualTo("PRE1");
        assertThat(row.getValue().getTitleJa()).isEqualTo("読書と動画");
        assertThat(row.getValue().getTitleZh()).isEqualTo("读书与视频");
        assertThat(row.getValue().getQuestionText()).isEqualTo("Do you agree?");
        assertThat(row.getValue().getEssayText()).isEqualTo("I think reading is better.");
        assertThat(row.getValue().getWordCount()).isEqualTo(5);
        assertThat(row.getValue().getUserAccountId()).isEqualTo(2L);
        // AI は呼ばない（受付だけ）
        assertThat(row.getValue().getStatusCode()).isEqualTo("QUEUED");
    }

    @Test
    @DisplayName("受付: 指定された回数を使う（省略時だけ次の回）")
    void acceptsExplicitRound() {
        when(mapper.findEssay(900001L)).thenReturn(essay());
        when(mapper.findActiveGrading(900001L)).thenReturn(null);
        stubInsert(501L);

        EnglishEssayAiQueue.Accepted accepted = queue.accept(900001L, 3);

        assertThat(accepted.round()).isEqualTo(3);
        verify(mapper, never()).nextRound(anyLong());
    }

    @Test
    @DisplayName("受付: 実行中の添削があれば拒否（前の添削が終わっていない）")
    void rejectsWhenAlreadyRunning() {
        when(mapper.findEssay(900001L)).thenReturn(essay());
        when(mapper.findActiveGrading(900001L)).thenReturn(77L);

        assertThatThrownBy(() -> queue.accept(900001L, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("前の添削が終わっていません");
        verify(mapper, never()).insertGrading(any());
    }

    @Test
    @DisplayName("受付: 同じ回数が既にあれば 409（UNIQUE に当たったら衝突として返す）")
    void reportsConflictOnDuplicateRound() {
        when(mapper.findEssay(900001L)).thenReturn(essay());
        when(mapper.findActiveGrading(900001L)).thenReturn(null);
        when(mapper.insertGrading(any())).thenThrow(new DuplicateKeyException(
                "duplicate key value violates unique constraint \"UK_ENG_AI添削履歴_回\""));

        assertThatThrownBy(() -> queue.accept(900001L, 2))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("第 2 回");
    }

    @Test
    @DisplayName("受付: 作文が無い・削除済み・本文が未確定なら日本語の理由で拒否")
    void rejectsInvalidEssay() {
        when(mapper.findEssay(900001L)).thenReturn(null);
        assertThatThrownBy(() -> queue.accept(900001L, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("見つかりません");

        EnglishEssayAiMapper.EssayRow deleted = essay();
        deleted.setStateCode("X");
        when(mapper.findEssay(900001L)).thenReturn(deleted);
        assertThatThrownBy(() -> queue.accept(900001L, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("削除");

        EnglishEssayAiMapper.EssayRow blank = essay();
        blank.setEssayText("  ");
        when(mapper.findEssay(900001L)).thenReturn(blank);
        assertThatThrownBy(() -> queue.accept(900001L, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("作文本文");

        assertThatThrownBy(() -> queue.accept(null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("英作文");
    }

    @Test
    @DisplayName("状態: 画面のポーリング用の形を返す")
    void returnsStatus() {
        EnglishEssayAiMapper.GradingStatusRow row = new EnglishEssayAiMapper.GradingStatusRow();
        row.setGradingId(501L);
        row.setRound(2);
        row.setStatusCode("SUCCEEDED");
        row.setScore(11);
        row.setMaxScore(16);
        when(mapper.findGradingStatus(501L)).thenReturn(row);

        Map<String, Object> status = queue.status(501L);

        assertThat(status).containsEntry("gradingId", 501L)
                .containsEntry("round", 2)
                .containsEntry("statusCode", "SUCCEEDED")
                .containsEntry("score", 11)
                .containsEntry("maxScore", 16)
                .containsEntry("failureReason", null);
    }

    @Test
    @DisplayName("状態: 無い添削は 404")
    void reportsMissingStatus() {
        when(mapper.findGradingStatus(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> queue.status(501L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("添削");
    }

    @Test
    @DisplayName("取り出し: 1 件を確保して RUNNING にする（待機中と、落ちたままの実行中）")
    void claimsOneItem() {
        EnglishEssayAiMapper.GradingRow row = new EnglishEssayAiMapper.GradingRow();
        row.setGradingId(77L);
        row.setEssayId(900001L);
        row.setRound(1);
        when(mapper.findClaimableGradingId(5)).thenReturn(77L);
        when(mapper.markGradingClaimed(org.mockito.ArgumentMatchers.eq(77L), any())).thenReturn(1);
        when(mapper.findGrading(77L)).thenReturn(row);

        List<EnglishEssayAiMapper.GradingRow> claimed = queue.claim(1, 5);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).getGradingId()).isEqualTo(77L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> from = ArgumentCaptor.forClass(List.class);
        verify(mapper).markGradingClaimed(org.mockito.ArgumentMatchers.eq(77L), from.capture());
        assertThat(from.getValue()).containsExactlyInAnyOrder("QUEUED", "RUNNING");
    }

    @Test
    @DisplayName("取り出し: 他の働き手が先に取っていたら含めない（二重に走らせない）")
    void skipsAlreadyClaimed() {
        when(mapper.findClaimableGradingId(5)).thenReturn(77L, null);
        when(mapper.markGradingClaimed(org.mockito.ArgumentMatchers.eq(77L), any())).thenReturn(0);

        assertThat(queue.claim(1, 5)).isEmpty();
        verify(mapper, never()).findGrading(anyLong());
    }

    @Test
    @DisplayName("取り出し: 拾うものが無ければ空")
    void claimsNothingWhenEmpty() {
        when(mapper.findClaimableGradingId(5)).thenReturn(null);

        assertThat(queue.claim(3, 5)).isEmpty();
        verify(mapper, never()).markGradingClaimed(anyLong(), any());
    }
}
