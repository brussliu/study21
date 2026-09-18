package com.study21.admin.batch;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * BAT_AI呼出履歴情報（バッチの AI 呼び出し履歴）の Mapper。
 *
 * <p>参照のみ（記録は各バッチが行う）。プロンプト / レスポンス の本文は
 * 1 行が大きい（最大 200KB 超）ため、一覧では読まず {@link #findById(long)} でだけ読む。</p>
 */
@Mapper
public interface AiCallLogMapper {

    /** 件数（一覧と同じ絞り込み条件）。 */
    long countCalls(@Param("batchCode") String batchCode,
                    @Param("aiType") String aiType,
                    @Param("result") String result,
                    @Param("keyword") String keyword,
                    @Param("startFrom") String startFrom,
                    @Param("startTo") String startTo);

    /** 新しい順の 1 ページ（本文は含まない）。 */
    List<AiCallLogEntity> searchCalls(@Param("batchCode") String batchCode,
                                      @Param("aiType") String aiType,
                                      @Param("result") String result,
                                      @Param("keyword") String keyword,
                                      @Param("startFrom") String startFrom,
                                      @Param("startTo") String startTo,
                                      @Param("limit") int limit,
                                      @Param("offset") int offset);

    /** 1 件（プロンプト / レスポンス を含む）。 */
    AiCallLogEntity findById(@Param("callId") long callId);

    /** 絞り込みに出す AI 区分の一覧（実データにある値）。 */
    List<String> findDistinctAiTypes();

    /** 絞り込みに出すバッチコードの一覧（実データにある値）。 */
    List<String> findDistinctBatchCodes();

    /** 絞り込みに出すモデル名の一覧（実データにある値）。 */
    List<String> findDistinctModels();

    /**
     * 1 行記録する（2.1 で新設。2.0 からの移行データしか無かった記録側）。
     *
     * <p>NOT NULL は バッチコード / 開始日時 / 結果区分（+ DB 既定 登録元コード='BATCH'）。
     * 採番された 呼出履歴ID は keyProperty で entity に入る。</p>
     */
    int insert(AiCallLogEntity entity);
}
