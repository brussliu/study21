package com.study21.user.japanese;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;

/** JPN_テスト情報 と JPN_テスト出題情報 の Mapper。 */
@Mapper
public interface JpnTestMapper {

    long count(@Param("accountId") long accountId,
               @Param("state") String state,
               @Param("testType") String testType);

    List<JpnTestEntity> search(@Param("accountId") long accountId,
                               @Param("state") String state,
                               @Param("testType") String testType,
                               @Param("limit") int limit,
                               @Param("offset") int offset);

    JpnTotalsEntity totals(@Param("accountId") long accountId);

    JpnTestEntity findById(@Param("testId") long testId);

    /** テスト番号の重複チェック（JT-yyyyMMdd-HHmmss で採番する）。 */
    JpnTestEntity findByNo(@Param("testNo") String testNo);

    int insert(JpnTestEntity entity);

    /** 回答・完了による進捗の更新（楽観的ロックは掛けない。学習の記録なので競合しにくい）。 */
    int updateProgress(@Param("testId") long testId,
                       @Param("doneCount") int doneCount,
                       @Param("correctCount") int correctCount,
                       @Param("wrongCount") int wrongCount,
                       @Param("state") String state,
                       @Param("lastStudiedAt") Timestamp lastStudiedAt,
                       @Param("activeMs") long activeMs);

    /** 実際に出題できた数で上書きする（条件に合う問題が少ないとき）。 */
    int updateQuestionCount(@Param("testId") long testId, @Param("questionCount") int questionCount);

    int updateState(@Param("testId") long testId,
                    @Param("state") String state,
                    @Param("startedAt") Timestamp startedAt,
                    @Param("finishedAt") Timestamp finishedAt);

    int delete(@Param("testId") long testId);

    /* ---------- 出題 ---------- */

    List<JpnTestQuestionEntity> listEntries(@Param("testId") long testId);

    JpnTestQuestionEntity findEntry(@Param("testId") long testId, @Param("orderNo") int orderNo);

    int insertEntry(JpnTestQuestionEntity entity);

    /** 回答の反映（判定・回答回数・誤答回数・学習時間）。問題が未確定なら一緒に確定する。 */
    int updateEntryAnswer(@Param("entryId") long entryId,
                          @Param("questionId") Long questionId,
                          @Param("state") String state,
                          @Param("judgment") String judgment,
                          @Param("answerCount") int answerCount,
                          @Param("wrongCount") int wrongCount,
                          @Param("activeMs") long activeMs,
                          @Param("answeredAt") Timestamp answeredAt);

    /** テスト作成時に条件に合う問題を選ぶ（出題方式 RANDOM ならランダム、ALL なら単語順）。 */
    List<JpnQuestionEntity> pickQuestions(@Param("testType") String testType,
                                          @Param("level") String level,
                                          @Param("book") String book,
                                          @Param("categoryFrom") String categoryFrom,
                                          @Param("categoryTo") String categoryTo,
                                          @Param("difficulty") String difficulty,
                                          @Param("random") boolean random,
                                          @Param("accountId") long accountId,
                                          @Param("limit") int limit);

    List<JpnChoiceEntity> listChoices(@Param("questionId") long questionId);

    /** 出題に紐づく問題の選択肢（まとめて引く）。 */
    List<JpnChoiceEntity> listChoicesByQuestionIds(@Param("questionIds") List<Long> questionIds);

    /** その単語の、指定した問題種別のいずれかに当てはまる問題（2.0 の移行データで問題が未確定のとき）。 */
    JpnQuestionEntity findQuestionByWordAndTypes(@Param("wordId") long wordId,
                                                @Param("types") List<String> types);
}
