package com.study21.user.japanese;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * JPN_単語情報（母表）と、その収録・問題・詳細の Mapper。
 *
 * <p>学習状況（習得度・お気に入りなど）は {@link JpnStatusMapper} が扱う。
 * ここでは単語そのものと、教材のどこに載っているか・どんな問題があるかを読む。</p>
 */
@Mapper
public interface JpnWordMapper {

    long count(@Param("keyword") String keyword,
               @Param("reading") String reading,
               @Param("jlpt") String jlpt,
               @Param("part") String part,
               @Param("state") String state,
               @Param("book") String book,
               @Param("category") String category,
               @Param("learnState") String learnState,
               @Param("accountId") long accountId);

    List<JpnWordEntity> search(@Param("keyword") String keyword,
                               @Param("reading") String reading,
                               @Param("jlpt") String jlpt,
                               @Param("part") String part,
                               @Param("state") String state,
                               @Param("book") String book,
                               @Param("category") String category,
                               @Param("learnState") String learnState,
                               @Param("accountId") long accountId,
                               @Param("limit") int limit,
                               @Param("offset") int offset);

    /** 一覧のサマリ（絞り込みは掛けず、本棚全体）。 */
    JpnTotalsEntity totals(@Param("accountId") long accountId);

    JpnWordEntity findById(@Param("wordId") long wordId, @Param("accountId") long accountId);

    /** 登録・更新の重複チェック（同じ 見出し語 + 読み が既にあるか）。 */
    JpnWordEntity findByWordAndReading(@Param("word") String word, @Param("reading") String reading);

    int insert(JpnWordEntity entity);

    int update(JpnWordEntity entity);

    int delete(@Param("wordId") long wordId);

    /** 収録（教材のどこに載っているか）。 */
    List<JpnCollectionEntity> listCollections(@Param("wordId") long wordId);

    /** その単語の問題（種類と数）。 */
    List<JpnQuestionEntity> listQuestions(@Param("wordId") long wordId);

    JpnWordDetailEntity findDetail(@Param("wordId") long wordId);
}
