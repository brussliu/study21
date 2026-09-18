package com.study21.user.geometry;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * GEO_AI画図指示情報（AI 画図助手）の Mapper（user-api 側）。
 *
 * <p>助手は画面からの同期呼び出しなので、記録するのも user-api のこの Mapper。</p>
 */
@Mapper
public interface GeometryAiAssistMapper {

    int insert(GeometryAiAssistEntity entity);

    GeometryAiAssistEntity findById(@Param("assistId") long assistId);

    /**
     * 図形ごとの指示履歴（**新しい順**。自分が作った行だけ）。
     *
     * <p>画面の会話ログはこれを読む（端末をまたいでも同じ履歴が見えるようにするため）。
     * 「指示前XML」は 1 行が大きくなり得るので一覧では返さず、`findById` で 1 件ずつ取る。</p>
     */
    List<GeometryAiAssistEntity> findByFigure(@Param("figureId") long figureId,
                                              @Param("accountId") long accountId,
                                              @Param("limit") int limit);

    /** 1 アカウントの今日の指示回数（日次上限の判定）。 */
    long countTodayByAccount(@Param("accountId") long accountId);

    /** 利用者が【反映】を押した（適用区分 = APPLIED。反映方法も残す＝いまは APPEND だけ）。自分の行だけ。 */
    int updateApplied(@Param("assistId") long assistId,
                      @Param("mode") String mode,
                      @Param("operator") Long operator);

    /**
     * 変更案を**作図に反映しなかった**（適用区分 = REJECTED）。自分の行だけ。
     *
     * <p>利用者の【破棄】だけでなく、案は返ったが**作図に反映できなかった**ときにも使う。
     * そのときは理由（どの行で失敗したか）を `エラーメッセージ` に残す（端末をまたいで
     * 同じ理由が見えるように）。</p>
     */
    int updateRejected(@Param("assistId") long assistId,
                       @Param("operator") Long operator,
                       @Param("reason") String reason);
}
