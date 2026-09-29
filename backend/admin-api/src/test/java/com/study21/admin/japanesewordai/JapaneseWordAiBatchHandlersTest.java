package com.study21.admin.japanesewordai;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchTaskHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * batC41〜batC44 のバッチハンドラ（タスクコードと取得区分の対応）。
 *
 * <p>確かめる接縫はこの 1 点: <b>4 つのバッチコードが、それぞれ正しい取得区分で業務処理を呼ぶ</b>。
 * 業務処理の中身は {@link JapaneseWordAiStepTest} が見る。</p>
 */
class JapaneseWordAiBatchHandlersTest {

    private static BatchExecutionEntity execution(String payload) {
        BatchExecutionEntity entity = new BatchExecutionEntity();
        entity.setExecutionId(12L);
        entity.setRequestPayload(payload);
        return entity;
    }

    @Test
    @DisplayName("4 つのハンドラが、それぞれのバッチコードと取得区分で業務処理を呼ぶ")
    void mapsBatchCodeToKind() throws Exception {
        JapaneseWordAiStep step = mock(JapaneseWordAiStep.class);
        when(step.run(any(), anyString(), anyString(), any())).thenReturn(java.util.Map.of("message", "ok"));

        assertThat(new JapaneseWordAiBatchHandlers.Detail(step).taskCode()).isEqualTo("batC41");
        assertThat(new JapaneseWordAiBatchHandlers.Reading(step).taskCode()).isEqualTo("batC42");
        assertThat(new JapaneseWordAiBatchHandlers.Context(step).taskCode()).isEqualTo("batC43");
        assertThat(new JapaneseWordAiBatchHandlers.KanjiUsage(step).taskCode()).isEqualTo("batC44");

        BatchTaskHandler[] handlers = {
                new JapaneseWordAiBatchHandlers.Detail(step),
                new JapaneseWordAiBatchHandlers.Reading(step),
                new JapaneseWordAiBatchHandlers.Context(step),
                new JapaneseWordAiBatchHandlers.KanjiUsage(step)
        };
        String[] kinds = {"DETAIL", "C", "D", "E"};
        for (int index = 0; index < handlers.length; index += 1) {
            assertThat(handlers[index].execute(execution(null))).isEqualTo("ok");
            verify(step).run(any(), eq(handlers[index].taskCode()), eq(kinds[index]), any());
        }
    }

    @Test
    @DisplayName("要求内容の wordIds を対象に渡す（画面から選んだ語だけを取得する）")
    void readsWordIdsFromPayload() throws Exception {
        JapaneseWordAiStep step = mock(JapaneseWordAiStep.class);
        when(step.run(any(), anyString(), anyString(), any())).thenReturn(java.util.Map.of("message", "ok"));

        new JapaneseWordAiBatchHandlers.Detail(step).execute(execution("{\"wordIds\":[101,102]}"));

        verify(step).run(any(), eq("batC41"), eq("DETAIL"), eq(List.of(101L, 102L)));
    }

    @Test
    @DisplayName("要求内容が無い・壊れているときは、対象を指定しない（SQL が選ぶ）")
    void toleratesBrokenPayload() {
        assertThat(JapaneseWordAiBatchHandlers.wordIdsOf(execution(null))).isEmpty();
        assertThat(JapaneseWordAiBatchHandlers.wordIdsOf(execution("{"))).isEmpty();
        assertThat(JapaneseWordAiBatchHandlers.wordIdsOf(execution("{}"))).isEmpty();
        assertThat(JapaneseWordAiBatchHandlers.wordIdsOf(execution("{\"wordIds\":\"101\"}"))).isEmpty();
    }

    @Test
    @DisplayName("要求する設定は、そのバッチのキー（BAT_C4x_*）を指す")
    void requiresOwnSettings() {
        assertThat(JapaneseWordAiBatchHandlers.requirements("batC42"))
                .extracting(requirement -> requirement.settingKey())
                .contains("BAT_C42_AI_PROVIDER", "BAT_C42_SYSTEM_PROMPT", "BAT_C42_RETRY_LIMIT")
                .allSatisfy(key -> assertThat(key).startsWith("BAT_C42_"));
        assertThat(JapaneseWordAiBatchHandlers.requirements("batC44"))
                .extracting(requirement -> requirement.settingKey())
                .allSatisfy(key -> assertThat(key).startsWith("BAT_C44_"));
        // 設定ページは 4 バッチとも同じ
        assertThat(JapaneseWordAiBatchHandlers.requirements("batC41"))
                .allSatisfy(requirement -> assertThat(requirement.pageCode())
                        .isEqualTo(JapaneseWordAiSettings.PAGE_CODE));
    }
}
