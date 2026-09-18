package com.study21.admin.batch;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BatchExecutionTarget} の要求内容（JSONB）の解釈。
 */
class BatchExecutionTargetTest {

    @Test
    void parsesAiRequestIdAsAiFigure() {
        BatchExecutionTarget.Target target = BatchExecutionTarget.parse("{\"aiRequestId\":164}");
        assertThat(target.targetKind()).isEqualTo("AI_FIGURE");
        assertThat(target.targetId()).isEqualTo(164L);
        assertThat(target.targetKey()).isEqualTo("aiRequestId");
    }

    @Test
    void parsesAssistIdAsAiAssist() {
        BatchExecutionTarget.Target target = BatchExecutionTarget.parse("{\"assistId\":12}");
        assertThat(target.targetKind()).isEqualTo("AI_ASSIST");
        assertThat(target.targetId()).isEqualTo(12L);
        assertThat(target.targetKey()).isEqualTo("assistId");
    }

    @Test
    void parsesNoteIdAsClassroomNote() {
        BatchExecutionTarget.Target target = BatchExecutionTarget.parse("{\"noteId\":5}");
        assertThat(target.targetKind()).isEqualTo("CLASSROOM_NOTE");
        assertThat(target.targetId()).isEqualTo(5L);
        assertThat(target.targetKey()).isEqualTo("noteId");
    }

    @Test
    void returnsNoneForUnknownKeyOrBlank() {
        assertThat(BatchExecutionTarget.parse("{\"other\":1}").targetKind()).isNull();
        assertThat(BatchExecutionTarget.parse("").targetKind()).isNull();
        assertThat(BatchExecutionTarget.parse(null).targetKind()).isNull();
        assertThat(BatchExecutionTarget.parse("not json").targetKind()).isNull();
    }

    @Test
    void ignoresExtraFields() {
        // AI 生図の実ペイロードは requestNo / stage も持つ
        BatchExecutionTarget.Target target =
                BatchExecutionTarget.parse("{\"aiRequestId\":164,\"requestNo\":\"AIG...\",\"stage\":\"GENERATE\"}");
        assertThat(target.targetKind()).isEqualTo("AI_FIGURE");
        assertThat(target.targetId()).isEqualTo(164L);
    }
}
