package com.study21.user.studymonitor;

import com.study21.user.security.UserPrincipal;

import java.io.InputStream;

/**
 * 学習状況モニターの照会と手動修正。
 *
 * <p>取り込み（batL02）と AI 分析（batL03）は 2.1 では未実装のため、
 * ここは画面が必要とする読み取りと、判定結果の手動修正だけを提供する。</p>
 */
public interface StudyMonitorService {

    /**
     * 対象日の動画とスナップショットを返す。
     *
     * @param date          対象日（必須。画面の既定は今日）
     * @param timeFrom      時間帯の開始（HH:mm。null なら 00:00）
     * @param timeTo        時間帯の終了（HH:mm。null なら 23:59）
     * @param videoId       動画を指定する場合（動画から確認モード）
     * @param analysisState all / done / waiting / error
     * @param resultCode    分析結果のコード（null ならすべて）
     */
    StudyMonitorModels.SnapshotSearchResult search(String date, String timeFrom, String timeTo,
                                                   Long videoId, String analysisState, String resultCode);

    /** 判定結果を一括で修正する（修正理由は必須。版が合わない行があれば 409）。 */
    StudyMonitorModels.ManualCorrectionResult correctManually(UserPrincipal user,
                                                             StudyMonitorModels.ManualCorrectionRequest request);

    /** スナップショットの画像を開く（保存ルートからの相対パスを解決する）。 */
    InputStream openImage(long snapshotId);

    /** 画像のファイル名（Content-Type の判定に使う）。 */
    String imageFileName(long snapshotId);
}
