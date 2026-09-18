package com.study21.admin.classroomai;

import com.study21.admin.batch.AiCallLogEntity;
import com.study21.admin.batch.AiCallLogMapper;
import com.study21.common.core.exception.ConflictException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 授業ノートの**記録と状態遷移だけ**を別トランザクションで即 commit する（`REQUIRES_NEW`）。
 *
 * <p>理由は {@code GeometryAiRequestRecorder} と同じ: AI 呼び出し中に JVM が落ちると実行履歴の
 * 行ごと消えるため、お金を使った事実と「どこまで進んだか」を残す。別 Bean にしてあるのは
 * Spring の {@code @Transactional} が自己呼び出しでは効かないため。</p>
 */
@Component
public class ClassroomAiNoteRecorder {

    private final ClassroomNoteMapper noteMapper;
    private final ClassroomRecordMapper recordMapper;
    private final AiCallLogMapper aiCallLogMapper;

    public ClassroomAiNoteRecorder(ClassroomNoteMapper noteMapper,
                                   ClassroomRecordMapper recordMapper,
                                   AiCallLogMapper aiCallLogMapper) {
        this.noteMapper = noteMapper;
        this.recordMapper = recordMapper;
        this.aiCallLogMapper = aiCallLogMapper;
    }

    /** AI へ送る前に「生成中」を確定する。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markGenerating(ClassroomNoteEntity entity, Long executionId) {
        requireUpdated(noteMapper.markGenerating(entity.getNoteId(), executionId, versionOf(entity)), entity);
    }

    /** ノートを READY にする（FINAL はあわせて記録の最終まとめ・状態=COMPLETED）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateReady(ClassroomNoteEntity entity) {
        requireUpdated(noteMapper.updateReady(entity), entity);
        if ("FINAL".equals(entity.getKind())) {
            recordMapper.updateSummaryCompleted(entity.getRecordId(), entity.getNoteJson());
        }
    }

    /** 失敗を書く。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFailed(ClassroomNoteEntity entity) {
        requireUpdated(noteMapper.updateFailed(entity), entity);
    }

    /** AI 呼び出し履歴に 1 行足して、採番された 呼出履歴ID を返す。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long recordCall(AiCallLogEntity entity) {
        aiCallLogMapper.insert(entity);
        return entity.getCallId();
    }

    private static void requireUpdated(int updated, ClassroomNoteEntity entity) {
        if (updated == 0) {
            throw new ConflictException("この授業ノートは他の操作で更新されました（ノートID "
                    + entity.getNoteId() + "）。画面を再読み込みしてください。");
        }
        entity.setVersion(versionOf(entity) + 1);
    }

    private static int versionOf(ClassroomNoteEntity entity) {
        return entity.getVersion() == null ? 1 : entity.getVersion();
    }
}
