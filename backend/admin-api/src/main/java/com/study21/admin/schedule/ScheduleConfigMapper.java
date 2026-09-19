package com.study21.admin.schedule;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * スケジュール設定の DB 読み込み（**一括**。タスクごとに 1 回ずつ問い合わせない）。
 *
 * <p>MyBatis（{@code SqlLoggingInterceptor}）が SQL を記録するので、ここでもれなくログに残る。</p>
 */
@Mapper
public interface ScheduleConfigMapper {

    /**
     * 対象ページ区分・設定キーの GLOBAL 設定値。
     *
     * @return {@code 設定キー → 設定値}（行が無いキーは含まれない）
     */
    List<Map<String, Object>> findSettingValues(@Param("pageCodes") List<String> pageCodes,
                                                @Param("settingKeys") List<String> settingKeys);

    /**
     * 対象タスクの有効／無効（BAT_バッチコントロール情報 が唯一の正）。
     *
     * @return {@code バッチコード → 状態('1'/'0')}
     */
    List<Map<String, Object>> findControlStatuses(@Param("taskCodes") List<String> taskCodes);
}
