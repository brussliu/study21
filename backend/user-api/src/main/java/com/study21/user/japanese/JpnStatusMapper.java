package com.study21.user.japanese;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

/**
 * JPN_学習状況情報 / JPN_技能習得情報 / JPN_学習日次情報 の Mapper。
 *
 * <p>学習状況は**アカウントごと**（2.0 の ユーザーID から移行）。</p>
 */
@Mapper
public interface JpnStatusMapper {

    /** 勉強状況のサマリ。 */
    JpnTotalsEntity summary(@Param("accountId") long accountId, @Param("today") LocalDate today);

    /** 日次の学習量（新しい順）。 */
    List<JpnDailyEntity> listDaily(@Param("accountId") long accountId, @Param("limit") int limit);

    /* ---------- 語別の学習状況 ---------- */

    long countStatuses(@Param("accountId") long accountId,
                       @Param("learnState") String learnState,
                       @Param("jlpt") String jlpt);

    List<JpnStatusEntity> listStatuses(@Param("accountId") long accountId,
                                       @Param("learnState") String learnState,
                                       @Param("jlpt") String jlpt,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    /* ---------- 技能別の習得 ---------- */

    long countSkills(@Param("accountId") long accountId,
                     @Param("testType") String testType,
                     @Param("skill") String skill);

    List<JpnSkillEntity> listSkills(@Param("accountId") long accountId,
                                    @Param("testType") String testType,
                                    @Param("skill") String skill,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);

    /* ---------- 更新（回答したとき） ---------- */

    /** 学習状況の行を用意する（無ければ作る）。 */
    int insertStatusIfAbsent(@Param("accountId") long accountId, @Param("wordId") long wordId);

    int updateStatusAfterAnswer(@Param("accountId") long accountId,
                                @Param("wordId") long wordId,
                                @Param("state") String state,
                                @Param("mastery") BigDecimal mastery,
                                @Param("correct") boolean correct,
                                @Param("learned") boolean learned,
                                @Param("nextReviewAt") Timestamp nextReviewAt,
                                @Param("reviewIntervalDays") int reviewIntervalDays,
                                @Param("testType") String testType,
                                @Param("judgment") String judgment,
                                @Param("elapsedMs") long elapsedMs,
                                @Param("now") Timestamp now);

    /** 技能習得の行を用意する（無ければ作る）。 */
    int insertSkillIfAbsent(@Param("accountId") long accountId,
                            @Param("wordId") long wordId,
                            @Param("testType") String testType,
                            @Param("skillCode") String skillCode);

    /** 技能 1 件（復習間隔の計算に使う）。 */
    JpnSkillEntity findSkill(@Param("accountId") long accountId,
                             @Param("wordId") long wordId,
                             @Param("testType") String testType,
                             @Param("skillCode") String skillCode);

    int updateSkillAfterAnswer(@Param("accountId") long accountId,
                               @Param("wordId") long wordId,
                               @Param("testType") String testType,
                               @Param("skillCode") String skillCode,
                               @Param("state") String state,
                               @Param("mastery") BigDecimal mastery,
                               @Param("correct") boolean correct,
                               @Param("judgment") String judgment,
                               @Param("nextReviewAt") Timestamp nextReviewAt,
                               @Param("elapsedMs") long elapsedMs,
                               @Param("now") Timestamp now);

    /** 技能の平均習得度（学習状況の 総合習得度 を 2.0 と同じ規則で決めるのに使う）。 */
    BigDecimal averageSkillMastery(@Param("accountId") long accountId, @Param("wordId") long wordId);

    /** 学習状況を「技能の平均」から作り直す（2.0 の規則: 80 以上で MASTERED、誤答なら REVIEW）。 */
    int refreshStatusFromSkills(@Param("accountId") long accountId,
                                @Param("wordId") long wordId,
                                @Param("judgment") String judgment,
                                @Param("aCheckCount") int aCheckCount);

    int updateFavorite(@Param("accountId") long accountId,
                       @Param("wordId") long wordId,
                       @Param("favorite") boolean favorite,
                       @Param("now") Timestamp now);

    int updateLearned(@Param("accountId") long accountId,
                      @Param("wordId") long wordId,
                      @Param("learned") boolean learned,
                      @Param("now") Timestamp now);

    /** 日次の学習量を加算する（無ければ作る）。 */
    int upsertDaily(@Param("accountId") long accountId,
                    @Param("studyDate") LocalDate studyDate,
                    @Param("testType") String testType,
                    @Param("elapsedMs") long elapsedMs,
                    @Param("correct") boolean correct);
}
