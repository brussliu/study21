package com.study21.admin.englishessay;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchTaskHandler;
import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 英作文のバッチハンドラ（{@code batC11} = OCR・{@code batC12} = 添削）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>バッチコードと、呼ぶ Step の対応</li>
 *   <li>要求内容（{@code {"essayId":N,"round":M,"gradingId":K}}）の読み取り</li>
 *   <li>{@code batC11} は同期エンドポイントと同じ Step を通る（{@code step.run}）</li>
 *   <li>{@code batC12} は働き手と同じ Step（{@code step.runItem}）を通る。
 *       受付が要る（{@code essayId} 指定）ときは待ち行列に積んでから実行する</li>
 * </ol>
 */
class EnglishEssayAiBatchHandlersTest {

    private static BatchExecutionEntity execution(String payload) {
        BatchExecutionEntity entity = new BatchExecutionEntity();
        entity.setExecutionId(12L);
        entity.setRequestPayload(payload);
        return entity;
    }

    private static EnglishEssayAiMapper.GradingRow grading(long gradingId, int round) {
        EnglishEssayAiMapper.GradingRow row = new EnglishEssayAiMapper.GradingRow();
        row.setGradingId(gradingId);
        row.setEssayId(900001L);
        row.setRound(round);
        return row;
    }

    @Test
    @DisplayName("batC11 は要求内容の essayId で OCR を実行する（同期エンドポイントと同じ Step）")
    void runsOcr() throws Exception {
        EnglishEssayOcrStep step = mock(EnglishEssayOcrStep.class);
        when(step.run(any(), anyLong())).thenReturn(Map.of("message", "英作文 OCR: 成功"));

        BatchTaskHandler handler = new EnglishEssayAiBatchHandlers.Ocr(step);

        assertThat(handler.taskCode()).isEqualTo("batC11");
        assertThat(handler.execute(execution("{\"essayId\":900001}"))).isEqualTo("英作文 OCR: 成功");
        verify(step).run(any(), eq(900001L));
    }

    @Test
    @DisplayName("batC11 は essayId が無ければ日本語の理由で失敗する")
    void rejectsOcrWithoutEssayId() {
        EnglishEssayOcrStep step = mock(EnglishEssayOcrStep.class);
        BatchTaskHandler handler = new EnglishEssayAiBatchHandlers.Ocr(step);

        assertThatThrownBy(() -> handler.execute(execution("{}")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("essayId");
        assertThatThrownBy(() -> handler.execute(execution("{")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("essayId");
    }

    @Test
    @DisplayName("batC12 は gradingId 指定で、その 1 件を実行する")
    void runsGradingByGradingId() throws Exception {
        EnglishEssayGradingStep step = mock(EnglishEssayGradingStep.class);
        EnglishEssayAiQueue queue = mock(EnglishEssayAiQueue.class);
        EnglishEssayAiMapper mapper = mock(EnglishEssayAiMapper.class);
        when(mapper.findGrading(501L)).thenReturn(grading(501L, 2));
        when(step.runItem(any(), any())).thenReturn(true);

        BatchTaskHandler handler = new EnglishEssayAiBatchHandlers.Grading(step, queue, mapper);

        assertThat(handler.taskCode()).isEqualTo("batC12");
        String message = handler.execute(execution("{\"gradingId\":501}"));
        assertThat(message).contains("第 2 回");
        assertThat(message).contains("完了");
        verify(step).runItem(any(), any());
    }

    @Test
    @DisplayName("batC12 は essayId 指定なら受付けてから、その 1 件を実行する（働き手と同じ道）")
    void acceptsThenRuns() throws Exception {
        EnglishEssayGradingStep step = mock(EnglishEssayGradingStep.class);
        EnglishEssayAiQueue queue = mock(EnglishEssayAiQueue.class);
        EnglishEssayAiMapper mapper = mock(EnglishEssayAiMapper.class);
        when(queue.accept(900001L, 3)).thenReturn(new EnglishEssayAiQueue.Accepted(501L, 3));
        when(mapper.findGrading(501L)).thenReturn(grading(501L, 3));
        when(step.runItem(any(), any())).thenReturn(true);

        BatchTaskHandler handler = new EnglishEssayAiBatchHandlers.Grading(step, queue, mapper);

        assertThat(handler.execute(execution("{\"essayId\":900001,\"round\":3}"))).contains("第 3 回");
        verify(queue).accept(900001L, 3);
        verify(step).runItem(any(), any());
    }

    @Test
    @DisplayName("batC12 は失敗しても例外にせず、理由を実行履歴の文言に残す")
    void reportsGradingFailure() throws Exception {
        EnglishEssayGradingStep step = mock(EnglishEssayGradingStep.class);
        EnglishEssayAiQueue queue = mock(EnglishEssayAiQueue.class);
        EnglishEssayAiMapper mapper = mock(EnglishEssayAiMapper.class);
        when(mapper.findGrading(501L)).thenReturn(grading(501L, 1));
        when(step.runItem(any(), any())).thenReturn(false);

        String message = new EnglishEssayAiBatchHandlers.Grading(step, queue, mapper)
                .execute(execution("{\"gradingId\":501}"));

        assertThat(message).contains("第 1 回").contains("失敗");
    }

    @Test
    @DisplayName("batC12 は gradingId も essayId も無ければ日本語の理由で失敗する")
    void rejectsGradingWithoutTarget() {
        EnglishEssayGradingStep step = mock(EnglishEssayGradingStep.class);
        EnglishEssayAiQueue queue = mock(EnglishEssayAiQueue.class);
        EnglishEssayAiMapper mapper = mock(EnglishEssayAiMapper.class);

        assertThatThrownBy(() -> new EnglishEssayAiBatchHandlers.Grading(step, queue, mapper)
                .execute(execution("{}")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("essayId");
    }

    @Test
    @DisplayName("batC12 は見つからない添削IDなら日本語の理由で失敗する")
    void rejectsUnknownGrading() {
        EnglishEssayGradingStep step = mock(EnglishEssayGradingStep.class);
        EnglishEssayAiQueue queue = mock(EnglishEssayAiQueue.class);
        EnglishEssayAiMapper mapper = mock(EnglishEssayAiMapper.class);
        when(mapper.findGrading(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> new EnglishEssayAiBatchHandlers.Grading(step, queue, mapper)
                .execute(execution("{\"gradingId\":999}")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("添削");
    }

    @Test
    @DisplayName("要求する設定は、そのバッチのキーを指す（ページは ENGLISH_ESSAY）")
    void requiresOwnSettings() {
        assertThat(EnglishEssayAiBatchHandlers.requirements("batC11"))
                .extracting(requirement -> requirement.settingKey())
                .contains("ENGLISH_ESSAY_OCR_PROMPT", "ENGLISH_ESSAY_TITLE_AI_PROVIDER")
                .allSatisfy(key -> assertThat(key).startsWith("ENGLISH_ESSAY_"));
        assertThat(EnglishEssayAiBatchHandlers.requirements("batC12"))
                .extracting(requirement -> requirement.settingKey())
                .contains("ENGLISH_ESSAY_GRADING_PROMPT", "ENGLISH_ESSAY_GRADING_RETRY_LIMIT")
                .allSatisfy(key -> assertThat(key).startsWith("ENGLISH_ESSAY_"));
        assertThat(EnglishEssayAiBatchHandlers.requirements("batC11"))
                .allSatisfy(requirement -> assertThat(requirement.pageCode()).isEqualTo("ENGLISH_ESSAY"));
    }

    @Test
    @DisplayName("要求内容の読み取りは壊れた JSON を許容する（null を返す）")
    void toleratesBrokenPayload() {
        assertThat(EnglishEssayAiBatchHandlers.essayIdOf(execution(null))).isNull();
        assertThat(EnglishEssayAiBatchHandlers.essayIdOf(execution("{"))).isNull();
        assertThat(EnglishEssayAiBatchHandlers.essayIdOf(execution("{\"essayId\":\"900001\"}"))).isNull();
        assertThat(EnglishEssayAiBatchHandlers.essayIdOf(execution("{\"essayId\":900001}"))).isEqualTo(900001L);
        assertThat(EnglishEssayAiBatchHandlers.gradingIdOf(execution("{\"gradingId\":501}"))).isEqualTo(501L);
        assertThat(EnglishEssayAiBatchHandlers.gradingIdOf(execution("{}"))).isNull();
        assertThat(EnglishEssayAiBatchHandlers.roundOf(execution("{\"round\":2}"))).isEqualTo(2);
        assertThat(EnglishEssayAiBatchHandlers.roundOf(execution("{}"))).isNull();
    }
}
