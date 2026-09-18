package com.study21.user.geometry;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * GEO_AI生図リクエスト情報（AI 生図の要求）の Mapper（user-api 側）。
 *
 * <p>画面向けの入口が作る／読む／確定する行だけを扱う。AI を呼ぶ 3 工程
 * （batC51/52/53）の更新は admin-api の Mapper が行う（サービス間で API を呼ばず、
 * DB の状態列だけで橋渡しする。docs/ARCHITECTURE.md §3）。</p>
 *
 * <p>**画像の中身は返さない**（ファイルで持ち、配信は `GeometryAiStorage` が行う）。</p>
 */
@Mapper
public interface GeometryAiRequestMapper {

    int insert(GeometryAiRequestEntity entity);

    GeometryAiRequestEntity findById(@Param("requestId") long requestId);

    GeometryAiRequestEntity findByNo(@Param("requestNo") String requestNo);

    /** 履歴の件数（作成者ごと。管理者の全件表示はしない＝他人の要求は見えない）。 */
    long count(@Param("createdBy") Long createdBy, @Param("status") String status);

    /** 履歴の 1 ページ（新しい順）。 */
    List<GeometryAiRequestEntity> search(@Param("createdBy") Long createdBy,
                                         @Param("status") String status,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);

    /**
     * 図形管理の一覧に出す**処理中のタスクと最近のタスク**（新しい順）。
     *
     * <p>**図形として保存済（REGISTERED）は返さない**。保存できたものは図形一覧のカードとして出るので、
     * 同じものが 2 つ並ばないようにする（利用者の指示）。</p>
     */
    List<GeometryAiRequestEntity> findTasks(@Param("createdBy") Long createdBy, @Param("limit") int limit);

    /** 1 アカウントの今日の作成件数（日次上限の判定。索引 idx_geo_ai_request_account_created）。 */
    long countTodayByAccount(@Param("accountId") long accountId);

    /** 図形として登録済みにする（楽観的ロック）。 */
    int updateRegistered(@Param("requestId") long requestId,
                         @Param("figureId") long figureId,
                         @Param("operator") Long operator,
                         @Param("sourceCode") String sourceCode,
                         @Param("version") int version);

    /** 取り消し（QUEUED / PREPROCESSED のみ。楽観的ロック）。 */
    int updateCancelled(@Param("requestId") long requestId,
                        @Param("operator") Long operator,
                        @Param("sourceCode") String sourceCode,
                        @Param("version") int version);

    /**
     * 条件を直して送り直す（**同じ要求行を使い回す**）。
     *
     * <p>読み取る範囲・作図方法・結果種別・補充・補足要求を更新し、AI の成果物（コマンド・提案・
     * 判定・質問・失敗の記録）を消して `QUEUED` に戻す。再試行回数を +1 する。</p>
     */
    int updateResubmitted(GeometryAiRequestEntity entity);

    /**
     * もう一度生成する（AI の成果物を消して前処理済みへ戻す。再試行回数を +1）。
     * 失敗の記録も消す（同じ行を使い回すので、前回の失敗を残さない）。
     *
     * <p>設定も**そのときの有効な設定で固定し直す**（利用者が明示的に頼んだ再生成なので、
     * 直した設定を拾わせる）。</p>
     */
    int updateRetried(GeometryAiRequestEntity entity);
}
