package com.study21.admin.batch;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * BAT_バッチ実行履歴情報（バッチの実行履歴）の Mapper。
 * 有効／無効は BAT_バッチコントロール情報（{@link BatchControlMapper}）が持つ。
 */
@Mapper
public interface BatchExecutionMapper {

    int insert(BatchExecutionEntity entity);

    int markRunning(@Param("executionId") long executionId);

    /**
     * 実行の終了を記録する（状態・終了時刻・処理時間・メッセージ・エラー詳細）。
     * 処理時間は呼び出し側が測った値を入れる（ハンドラの実行時間をそのまま残す）。
     */
    int markFinished(@Param("executionId") long executionId,
                     @Param("status") String status,
                     @Param("message") String message,
                     @Param("errorDetail") String errorDetail,
                     @Param("durationMs") long durationMs);

    BatchExecutionEntity findById(@Param("executionId") long executionId);

    BatchExecutionEntity findRunningByBatchCode(@Param("batchCode") String batchCode);

    /**
     * 未完了の実行を探す（**自分自身は除く**）。
     *
     * <p>スケジューラは「実行記録を作ってから実行する」ので、自分の行（待機中）を
     * 「前回の実行」と取り違えないようにするために使う。</p>
     */
    BatchExecutionEntity findRunningByBatchCodeExcept(@Param("batchCode") String batchCode,
                                                      @Param("executionId") long executionId);

    /**
     * 未完了（待機中・実行中）の実行をまとめて失敗として閉じる（サービス再起動時の復旧）。
     *
     * <p>残したままだと、そのタスクの次の実行が「前回が実行中」と見なされて走らない。</p>
     *
     * @return 閉じた件数
     */
    int markUnfinishedAsFailed(@Param("message") String message);

    /**
     * 未完了（待機中・実行中）の実行を**古い順**に返す（再起動の復旧で 1 件ずつ見る）。
     *
     * <p>待機中（まだ始まっていない）と実行中（結果が分からない）で復旧の仕方を変えるため、
     * まとめて失敗にするのではなく 1 件ずつ扱えるようにしてある。</p>
     */
    List<BatchExecutionEntity> findUnfinished();

    /**
     * **起動の境界まで**の未完了（待機中・実行中）の実行を古い順に返す。
     *
     * <p>再起動の復旧が対象にするのは「このプロセスが始まる前に作られた実行」だけ。
     * 境界（{@link #findMaxExecutionId()} を起動時に読んだ値）より新しい実行は
     * **このプロセス自身が作ったもの**なので、復旧の対象にしてはいけない
     * （実行中の自分の実行を「落ちた実行」と誤解して、二重に走らせてしまう）。</p>
     *
     * @param boundaryExecutionId 起動時に読んだ「そのときの最大の実行ID」
     */
    List<BatchExecutionEntity> findUnfinishedBefore(@Param("boundaryExecutionId") long boundaryExecutionId);

    /**
     * いまの最大の実行ID（起動の境界を決めるために起動時に 1 回だけ読む）。
     *
     * <p>実行が 1 件も無ければ 0。</p>
     */
    long findMaxExecutionId();

    /**
     * 未完了（待機中・実行中）のときだけ閉じる（復旧の**条件つきの確保**）。
     *
     * <p>同じ実行を 2 つの復旧が同時に扱っても、閉じられるのは 1 つだけ
     * （PostgreSQL の行ロックで直列化され、後から来た方は 0 件になる）。
     * メモリのロックに依存しない。</p>
     *
     * @return 閉じた件数（0 = 既に他の復旧が処理した）
     */
    int closeIfUnfinished(@Param("executionId") long executionId,
                          @Param("status") String status,
                          @Param("message") String message);

    /** ある実行の**やり直し**（元実行ID が一致する実行）を返す（無ければ null）。 */
    BatchExecutionEntity findBySourceExecutionId(@Param("sourceExecutionId") long sourceExecutionId);

    /**
     * 復旧のやり直しの実行記録を作る（**元実行ID の一意性で守る**）。
     *
     * <p>既に同じ元実行のやり直しがあれば何も挿入しない（0 件）。呼び出し側は
     * {@link #findBySourceExecutionId(long)} で既存の 1 件を読んで使う。</p>
     *
     * @return 挿入した件数（0 = 既にやり直しがある）
     */
    int insertRetryIfAbsent(BatchExecutionEntity entity);

    /**
     * 要求内容（JSONB）の aiRequestId で実行履歴を引く（AI 生図の工程ごとの状況）。
     * 古い順（batC51 → 52 → 53 の順）に返す。
     */
    List<BatchExecutionEntity> findByRequestAiRequestId(@Param("aiRequestId") long aiRequestId);

    List<BatchExecutionEntity> findRecent(@Param("batchCode") String batchCode, @Param("limit") int limit);

    /**
     * バッチごとの最新 1 件（一覧画面の「最新実行」を 1 クエリで引く）。
     */
    List<BatchExecutionEntity> findLatestPerBatch();

    /** 実行履歴の件数（batchCode / status / keyword で絞り込み）。 */
    long countHistory(@Param("batchCode") String batchCode,
                      @Param("status") String status,
                      @Param("keyword") String keyword);

    /** 実行履歴の 1 ページ（新しい順）。keyword はバッチコード・メッセージ・エラー詳細を対象にする。 */
    List<BatchExecutionEntity> searchHistory(@Param("batchCode") String batchCode,
                                             @Param("status") String status,
                                             @Param("keyword") String keyword,
                                             @Param("limit") int limit,
                                             @Param("offset") int offset);

    int countByBatchCodeAndStatus(@Param("batchCode") String batchCode, @Param("status") String status);
}
