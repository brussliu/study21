package com.study21.admin.geometryai;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * GEO_AI生図リクエスト情報（AI 生図の要求）の Mapper（admin-api 側）。
 *
 * <p>AI 生図の 3 工程（前処理・AI 生成（モード別バッチ batC51-A〜D）・検証）が使う。
 * 画面向けの入口は user-api にあり、両サービスは互いを呼べないので**この表の状態列だけ**で橋渡しする。</p>
 *
 * <p>更新は必ず**楽観的ロック（バージョン）**を伴う。画面（取消・確定）とバッチが同じ行を
 * 触るため、取った行の版数が変わっていたら何も書かない（0 行更新）。</p>
 */
@Mapper
public interface GeometryAiRequestMapper {

    GeometryAiRequestEntity findById(@Param("requestId") long requestId);

    /**
     * 前処理（通常コード。バッチではない）が拾う 1 件。
     * `QUEUED`（受付済）と `FAILED(PREPROCESS)`（前処理で失敗した）の最古のもの。
     */
    GeometryAiRequestEntity findPreprocessTarget();

    /**
     * AI 生成（モード別バッチ batC51-A〜D）が拾う 1 件。
     * `PREPROCESSED`（前処理済）・`GENERATING`（前回の呼び出し中に落ちた残骸）・
     * `FAILED(GENERATE)`（生成で失敗した）の最古のもの。
     *
     * <p>モードは**必ず指定する**。{@code A} のときだけ `作図モード` が NULL の行も対象にする
     * （モード欄が無い時代の要求を A として扱うため）。</p>
     */
    GeometryAiRequestEntity findGenerateTarget(@Param("mode") String mode);

    /**
     * 検証が拾う 1 件。
     * `GENERATED`（コマンド保存済・検証待ち）・`VALIDATING`（確保した直後に落ちた残骸）・
     * `FAILED(VALIDATE)`（検証で失敗した）の最古のもの。
     *
     * <p>作業の取り出しは `findClaimableValidationId`（働き手が使う）で、**同じ状態を対象にする**。
     * ここだけ `VALIDATING` を外すと、確保しただけで何もせず同じ行を拾い続けてしまう。</p>
     */
    GeometryAiRequestEntity findValidateTarget();

    /**
     * 働き手（worker）が**これから処理する 1 件**を選ぶ（生成までの工程）。
     *
     * <p>`FOR UPDATE SKIP LOCKED` で他の働き手と衝突しないようにし、同じ要求を二重に処理しない
     * （＝ AI を二重に呼ばない）。対象は「待機中」「読み取り済み（生成待ち）」と、落ちたままの
     * 「読み取り中」「生成中」（一定時間より古いもの）。**FAILED は自動で拾わない**（利用者の
     * 【もう一度生成】だけが再開する。黙って課金しない）。</p>
     */
    Long findClaimablePipelineId(@Param("staleMinutes") int staleMinutes);

    /**
     * 働き手が**検証だけ**を行う 1 件を選ぶ（AI を呼び直さない）。
     *
     * <p>対象は「生成済み（検証待ち）」と、落ちたままの「検証中」。生成までやり直すと
     * **AI をもう一度呼んでしまう**ので、この 2 つは必ずこちらで拾う。</p>
     */
    Long findClaimableValidationId(@Param("staleMinutes") int staleMinutes);

    /** 働き手が処理に入ることを確定する（状態と版数を 1 回で進める）。 */
    int markClaimed(@Param("requestId") long requestId,
                    @Param("status") String status,
                    @Param("fromStatuses") java.util.List<String> fromStatuses);

    /** AI 生成中にする（**呼び出しの前に確定**。再実行時に「前回は呼び出し中に落ちた」と分かる）。 */
    int markGenerating(@Param("requestId") long requestId,
                       @Param("executionId") Long executionId,
                       @Param("version") int version);

    /** 読み取り中にする（前処理に入ったことを残す）。 */
    int markPreprocessing(@Param("requestId") long requestId,
                          @Param("executionId") Long executionId,
                          @Param("version") int version);

    /** 検証中にする（AI の出力を確かめている段階。**まだ保存はしない**）。 */
    int markValidating(@Param("requestId") long requestId,
                       @Param("executionId") Long executionId,
                       @Param("version") int version);

    /** 追加入力待ちにする（AI が質問を返した）。 */
    int updateNeedsInput(GeometryAiRequestEntity entity);

    /** 前処理の結果を書く（状態 = PREPROCESSED、切り抜き画像、実行ID）。 */
    int updatePreprocessed(GeometryAiRequestEntity entity);

    /** AI の結果を書く（状態 = GENERATED、生成コマンド、提案、呼出履歴ID、実行ID）。 */
    int updateGenerated(GeometryAiRequestEntity entity);

    /** 検証に通った（状態 = READY、作図種別の確定、実行ID）。 */
    int updateReady(GeometryAiRequestEntity entity);

    /** 失敗を書く（状態 = FAILED、失敗工程、エラーコード、検証エラー内容）。 */
    int updateFailed(GeometryAiRequestEntity entity);

    /**
     * 保持日数を過ぎた画像を持つ行（batR02 のクリーンアップ）。
     * **行は残し、画像ファイルだけ消す**（要求の履歴は監査として残す）。
     */
    List<GeometryAiRequestEntity> findImageCleanupTargets(@Param("retentionDays") int retentionDays,
                                                          @Param("limit") int limit);

    /** 画像の参照を消す（ファイルは呼び出し側が消す）。 */
    int clearImageFiles(@Param("requestId") long requestId);
}
