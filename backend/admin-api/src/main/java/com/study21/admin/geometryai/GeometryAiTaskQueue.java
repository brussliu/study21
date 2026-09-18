package com.study21.admin.geometryai;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 生図の**仕事の取り出し**（要求行そのものを待ち行列として使う）。
 *
 * <p>画面は要求行を作るだけ（`QUEUED`）で、実行は**バックエンドの働き手**が行う。取り出しは
 * `FOR UPDATE SKIP LOCKED` ＋ 状態の更新を**1 つのトランザクション**で行うので、複数の働き手や
 * 再起動が重なっても**同じ要求を二重に処理しない**（＝ AI を二重に呼ばない）。</p>
 *
 * <p>「生成までの工程」と「検証だけ」を分けているのは、`GENERATED`（生成済み・検証待ち）を
 * 生成からやり直すと **AI をもう一度呼んでしまう**から（お金が二重にかかる）。</p>
 */
@Component
public class GeometryAiTaskQueue {

    /** 生成までの工程で拾う状態。 */
    private static final List<String> PIPELINE_FROM = List.of("QUEUED", "PREPROCESSED", "PREPROCESSING", "GENERATING");
    /** 検証だけを拾う状態。 */
    private static final List<String> VALIDATION_FROM = List.of("GENERATED", "VALIDATING");

    private final GeometryAiRequestMapper requestMapper;

    public GeometryAiTaskQueue(GeometryAiRequestMapper requestMapper) {
        this.requestMapper = requestMapper;
    }

    /**
     * 生成までの工程を担当する 1 件を確保する（無ければ null）。
     *
     * <p>確保した行は `PREPROCESSING`（読み取り中）にする。**FAILED は拾わない**
     * （利用者の【もう一度生成】だけが `PREPROCESSED` に戻して再開する）。</p>
     */
    @Transactional
    public Long claimForPipeline(int staleMinutes) {
        Long requestId = requestMapper.findClaimablePipelineId(staleMinutes);
        if (requestId == null) {
            return null;
        }
        if (requestMapper.markClaimed(requestId, "PREPROCESSING", PIPELINE_FROM) == 0) {
            // 他の働き手が先に取った（SKIP LOCKED をすり抜けた場合の保険）。次の周期で取り直す
            return null;
        }
        return requestId;
    }

    /** 検証だけを担当する 1 件を確保する（無ければ null）。AI は呼ばない。 */
    @Transactional
    public Long claimForValidation(int staleMinutes) {
        Long requestId = requestMapper.findClaimableValidationId(staleMinutes);
        if (requestId == null) {
            return null;
        }
        if (requestMapper.markClaimed(requestId, "VALIDATING", VALIDATION_FROM) == 0) {
            return null;
        }
        return requestId;
    }
}
