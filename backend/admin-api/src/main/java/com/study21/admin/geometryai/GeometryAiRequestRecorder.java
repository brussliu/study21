package com.study21.admin.geometryai;

import com.study21.admin.batch.AiCallLogEntity;
import com.study21.admin.batch.AiCallLogMapper;
import com.study21.common.core.exception.ConflictException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 生図の**記録と状態遷移だけ**を別トランザクションで即 commit する（`REQUIRES_NEW`）。
 *
 * <p>理由（設計 §6.4 の明示的な例外）: `BatchServiceImpl.execute()` は `@Transactional` で
 * RUNNING の行を insert してから最後に commit するため、**AI 呼び出し中に JVM が落ちると
 * 実行履歴の行ごと消える**。お金を使った事実と「どこまで進んだか」は残さなければならない。</p>
 *
 * <p>別 Bean にしてあるのは、Spring の `@Transactional` が**自己呼び出しでは効かない**ため
 * （工程クラスの中から呼ぶと新しいトランザクションにならない）。</p>
 */
@Component
public class GeometryAiRequestRecorder {

    private final GeometryAiRequestMapper requestMapper;
    private final AiCallLogMapper aiCallLogMapper;

    public GeometryAiRequestRecorder(GeometryAiRequestMapper requestMapper, AiCallLogMapper aiCallLogMapper) {
        this.requestMapper = requestMapper;
        this.aiCallLogMapper = aiCallLogMapper;
    }

    /** AI へ送る前に「生成中」を確定する（再実行時に「前回は呼び出し中に落ちた」と分かる）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markGenerating(GeometryAiRequestEntity entity) {
        requireUpdated(requestMapper.markGenerating(entity.getRequestId(), entity.getAiExecutionId(),
                versionOf(entity)), entity);
    }

    /** 読み取り中にする（前処理に入ったことを残す）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPreprocessing(GeometryAiRequestEntity entity) {
        requireUpdated(requestMapper.markPreprocessing(entity.getRequestId(), entity.getPreprocessExecutionId(),
                versionOf(entity)), entity);
    }

    /** 検証中にする（**まだ保存はしない**。コマンドの規則を確かめている段階）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markValidating(GeometryAiRequestEntity entity) {
        requireUpdated(requestMapper.markValidating(entity.getRequestId(), entity.getValidateExecutionId(),
                versionOf(entity)), entity);
    }

    /** 追加入力待ち（AI が質問を返した。利用者が答えて送り直す）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateNeedsInput(GeometryAiRequestEntity entity) {
        requireUpdated(requestMapper.updateNeedsInput(entity), entity);
    }

    /** 前処理の結果（状態 = PREPROCESSED）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updatePreprocessed(GeometryAiRequestEntity entity) {
        requireUpdated(requestMapper.updatePreprocessed(entity), entity);
    }

    /** AI の結果（状態 = GENERATED）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateGenerated(GeometryAiRequestEntity entity) {
        requireUpdated(requestMapper.updateGenerated(entity), entity);
    }

    /** 検証に通った（状態 = READY）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateReady(GeometryAiRequestEntity entity) {
        requireUpdated(requestMapper.updateReady(entity), entity);
    }

    /** 失敗（状態 = FAILED）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long updateFailed(GeometryAiRequestEntity entity) {
        requireUpdated(requestMapper.updateFailed(entity), entity);
        return entity.getRequestId();
    }

    /** AI 呼び出し履歴に 1 行足して、採番された 呼出履歴ID を返す。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long recordCall(AiCallLogEntity entity) {
        aiCallLogMapper.insert(entity);
        return entity.getCallId();
    }

    /**
     * 更新できたことを確かめ、**手元の版数も +1 する**。
     *
     * <p>同じ工程の中で続けて更新するとき（生成中 → 生成済 など）、手元の版数が古いままだと
     * 2 回目が 0 行更新になってしまうため。</p>
     */
    private static void requireUpdated(int updated, GeometryAiRequestEntity entity) {
        if (updated == 0) {
            throw new ConflictException("この AI 生図は他の操作で更新されました（要求番号 "
                    + entity.getRequestNo() + "）。画面を再読み込みしてください。");
        }
        entity.setVersion(versionOf(entity) + 1);
    }

    private static int versionOf(GeometryAiRequestEntity entity) {
        return entity.getVersion() == null ? 1 : entity.getVersion();
    }
}
