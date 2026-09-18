package com.study21.admin.geometryai;

import com.study21.admin.batch.AiCallLogEntity;
import com.study21.admin.batch.AiCallLogMapper;
import com.study21.common.core.exception.ConflictException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 画図助手（batC52）の**記録と状態遷移だけ**を別トランザクションで即 commit する（`REQUIRES_NEW`）。
 *
 * <p>理由は {@code GeometryAiRequestRecorder} と同じ: AI 呼び出し中に JVM が落ちると実行履歴の
 * 行ごと消えるため、お金を使った事実と「どこまで進んだか」を残す。別 Bean にしてあるのは
 * Spring の {@code @Transactional} が自己呼び出しでは効かないため。</p>
 */
@Component
public class GeometryAiAssistRecorder {

    private final GeometryAiAssistMapper assistMapper;
    private final AiCallLogMapper aiCallLogMapper;

    public GeometryAiAssistRecorder(GeometryAiAssistMapper assistMapper, AiCallLogMapper aiCallLogMapper) {
        this.assistMapper = assistMapper;
        this.aiCallLogMapper = aiCallLogMapper;
    }

    /** AI へ送る前に「生成中」を確定する。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markGenerating(GeometryAiAssistEntity entity, Long executionId) {
        if (assistMapper.markGenerating(entity.getAssistId(), executionId) == 0) {
            throw new ConflictException("この AI 画図助手の依頼は他の操作で更新されました（依頼ID "
                    + entity.getAssistId() + "）。");
        }
    }

    /** AI の結果を READY にする。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateReady(GeometryAiAssistEntity entity) {
        if (assistMapper.updateReady(entity) == 0) {
            throw new ConflictException("この AI 画図助手の依頼は他の操作で更新されました（依頼ID "
                    + entity.getAssistId() + "）。");
        }
    }

    /** 失敗を書く（再試行回数 +1）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFailed(GeometryAiAssistEntity entity) {
        if (assistMapper.updateFailed(entity) == 0) {
            throw new ConflictException("この AI 画図助手の依頼は他の操作で更新されました（依頼ID "
                    + entity.getAssistId() + "）。");
        }
    }

    /** AI 呼び出し履歴に 1 行足して、採番された 呼出履歴ID を返す。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long recordCall(AiCallLogEntity entity) {
        aiCallLogMapper.insert(entity);
        return entity.getCallId();
    }
}
