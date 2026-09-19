package com.study21.admin.studymonitor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学習状況モニターの AI 分析（batL03）が使う Mapper。
 *
 * <p>触るのは 2 テーブルだけ:</p>
 * <ul>
 *   <li>{@code MON_学習モニタースナップショット情報} … 分析する画像を選ぶ（読むだけ。batL02 が作る）</li>
 *   <li>{@code MON_学習モニター画像分析情報} … 判定結果（COMPLETED）と失敗（ERROR）を書く</li>
 * </ul>
 *
 * <p><strong>2.0 との違い</strong>: 2.0 は実行のたびに「{@code 分析状態='ERROR'} の全行」を
 * {@code DELETE} してから対象を選んでいた。そのため壊れたスナップショットが永久に再試行され、
 * 最古の 1 枚が毎回枠（一度の分析枚数）を埋めて後続が餓死していた。2.1 は
 * <b>ERROR 行を消さない</b>（{@code 最新版フラグ='1'} の行が残るので対象から外れる）し、
 * 自動リトライもしない（人は画面で ERROR を見て直す）。したがってこの Mapper に
 * DELETE は 1 つも無い。</p>
 */
@Mapper
public interface StudyMonitorAnalysisMapper {

    /**
     * 分析するスナップショット（未分析のものだけを古い順に）。
     *
     * <p>対象は**スナップショットの完成状態**（{@code 状態='1'} かつ {@code 切出状態コード='CREATED'}）で決め、
     * 分析の最新版（{@code 最新版フラグ='1'}）が 1 行でもあれば対象から外す。
     * ERROR 行にも最新版フラグが立つので、**壊れた 1 枚が枠を占め続けることも、永久に再試行されることもない**。</p>
     *
     * <p>2.0 は「L02（切出）が済んだかどうか」を時間のずらしで決め打ちしていたが、2.1 は状態を見る。</p>
     *
     * @param limit 一度の分析枚数（{@code STUDY_MONITOR_AI_BATCH_LIMIT}）
     */
    List<Target> findAnalysisTargets(@Param("limit") int limit);

    /**
     * 一次判定の結果を書き戻す（{@code 分析状態コード='COMPLETED'}・{@code 最新版フラグ='1'}）。
     *
     * <p>2.1 は二次判定をしないので、最終列は一次判定と同じ値・{@code 最終採用段階コード='FLASH'} になる。</p>
     */
    int insertCompleted(@Param("row") CompletedRow row);

    /**
     * 失敗した 1 枚を {@code ERROR} として残す（既に最新版があれば更新する）。
     *
     * <p>この行にも {@code 最新版フラグ='1'} が立つので、同じスナップショットは次回以降の対象にならない
     * （＝自動リトライしない）。同じスナップショットの最新版は 1 行だけ（部分 UNIQUE
     * {@code uq_mon_analysis_latest}）なので、行があれば {@code ON CONFLICT ... DO UPDATE} で
     * エラー内容を上書きする。</p>
     */
    int upsertError(@Param("row") ErrorRow row);

    /** 分析対象のスナップショット 1 枚（{@code 保存パス} は保存ルートからの相対パス）。 */
    record Target(long snapshotId, String savePath, /** MON_学習モニタースナップショット情報.撮影日時 */
                  LocalDateTime capturedAt) {
    }

    /**
     * 分析結果（COMPLETED）の 1 行。
     *
     * <p>二次判定の列（{@code 二次判定要否} / {@code 二次分析状態コード} / {@code 二次判定閾値}）は
     * 2.1 の設定にキーが無いので、DB の既定と同じ値を明示して入れる
     * （{@link StudyMonitorAnalyzeHandler} の定数を参照）。</p>
     */
    record CompletedRow(
            long snapshotId,
            Timestamp capturedAt,
            String firstResultCode,
            BigDecimal firstConfidence,
            String firstReason,
            String firstAiProvider,
            String firstAiModel,
            Timestamp firstStartedAt,
            Timestamp firstFinishedAt,
            /** AI の生応答（JSON 本文）。そのまま {@code 一次応答JSON}（jsonb）に入れる。 */
            String firstResponseJson,
            String secondRequired,
            String secondStateCode,
            BigDecimal threshold,
            String finalStageCode,
            String finalResultCode,
            BigDecimal finalConfidence,
            String finalReason,
            Timestamp analysisFinishedAt) {
    }

    /** 失敗（ERROR）の 1 行。 */
    record ErrorRow(
            long snapshotId,
            Timestamp capturedAt,
            String firstAiProvider,
            String firstAiModel,
            String firstErrorMessage,
            /** AI の生応答が JSON として読めるときだけ入れる（読めなければ null）。 */
            String firstResponseJson) {
    }
}
