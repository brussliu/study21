package com.study21.admin.schedule;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * スケジュール状態（{@code BAT_スケジュール状態情報}）の Mapper。
 *
 * <p>「同じ計画実行点を 2 回実行しない」ための**永続的な確保（claim）**を担う。
 * JVM のメモリロックには依存しない（再起動・多重起動でも 1 回だけになる）。</p>
 */
@Mapper
public interface SchedulePlanMapper {

    /**
     * 計画実行点を確保する（**1 文で原子的**）。
     *
     * <p>行が無ければ作る。あるときは「いまの 最終予定日時 より新しい」ときだけ更新する。
     * 更新できた（挿入できた）ときだけ {@code 最終予定日時} を返す。0 件なら null
     * （＝他の実行が既に確保済み、または古い点なので何もしない）。</p>
     */
    String claim(@Param("batchCode") String batchCode, @Param("plannedAt") String plannedAt);

    /** 確保した実行IDを書き戻す（監査用）。 */
    int attachExecution(@Param("batchCode") String batchCode, @Param("executionId") long executionId);

    /** 対象タスクの計画状態（起動時に 1 回だけまとめて読む）。 */
    List<Map<String, Object>> findPlans(@Param("taskCodes") List<String> taskCodes);

    /** 1 件の計画状態（0 件の確保に負けたときに読み直す）。 */
    Map<String, Object> findPlan(@Param("batchCode") String batchCode);
}
