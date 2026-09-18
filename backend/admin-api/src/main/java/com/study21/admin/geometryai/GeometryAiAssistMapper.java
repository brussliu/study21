package com.study21.admin.geometryai;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * GEO_AI画図指示情報（AI 画図助手）の Mapper（admin-api 側）。
 *
 * <p>batC52 が**生成状態=PENDING の行をキューとして**拾い、生成状態を遷移させる。
 * 更新は生成状態でガードする（バージョン列が無いため）。</p>
 */
@Mapper
public interface GeometryAiAssistMapper {

    GeometryAiAssistEntity findById(@Param("assistId") long assistId);

    /** batC52 が拾う 1 件（PENDING / GENERATING の最古）。 */
    GeometryAiAssistEntity findPendingTarget();

    /** AI へ送る前に「生成中」を確定する（再実行時に「前回は呼び出し中に落ちた」と分かる）。 */
    int markGenerating(@Param("assistId") long assistId, @Param("executionId") Long executionId);

    /** AI の結果を書く（生成状態=READY、生成コマンド、説明、呼出履歴ID）。 */
    int updateReady(GeometryAiAssistEntity entity);

    /** 失敗を書く（生成状態=FAILED、エラー、再試行回数 +1）。 */
    int updateFailed(GeometryAiAssistEntity entity);
}
