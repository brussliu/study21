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
               /** 分類の範囲（From ～ To）。片方だけでもよい */
               @Param("categoryFrom") String categoryFrom,
               @Param("categoryTo") String categoryTo,
               @Param("learnState") String learnState,
               @Param("accountId") long accountId);

    List<JpnWordEntity> search(@Param("keyword") String keyword,
                               @Param("reading") String reading,
                               @Param("jlpt") String jlpt,
                               @Param("part") String part,
                               @Param("state") String state,
                               @Param("book") String book,
                               @Param("categoryFrom") String categoryFrom,
                               @Param("categoryTo") String categoryTo,
                               @Param("learnState") String learnState,
                               @Param("accountId") long accountId,
                               @Param("limit") int limit,
                               @Param("offset") int offset);

    /** 一覧のサマリ（絞り込みは掛けず、本棚全体）。 */
    JpnTotalsEntity totals(@Param("accountId") long accountId);

    /**
     * AI 取得の対象（**検索条件に一致する語**のうち、今回受付ける分）の ID を選ぶ。
     *
     * <p>絞り込みは一覧と**同じ**（{@link #search} と同じ WHERE を共有する）。並びも同じ
     * （書籍 → 分類 → 単語SEQ → 単語ID）なので、「表示順の先頭から N 語」になる。</p>
     *
     * @param kind         取得区分（{@code DETAIL} / {@code C} / {@code D} / {@code E}）
     * @param skipAcquired true＝取得済み・取得中を除く／false＝一致する語をそのまま（すべて再取得）
     * @param limit        この回に受付ける最大語数
     */
    List<Long> findAiTargets(@Param("keyword") String keyword,
                             @Param("reading") String reading,
                             @Param("jlpt") String jlpt,
                             @Param("part") String part,
                             @Param("state") String state,
                             @Param("book") String book,
                             @Param("categoryFrom") String categoryFrom,
                             @Param("categoryTo") String categoryTo,
                             @Param("learnState") String learnState,
                             @Param("accountId") long accountId,
                             @Param("kind") String kind,
                             @Param("skipAcquired") boolean skipAcquired,
                             @Param("limit") int limit);

    /**
     * AI 取得の対象の件数（一致総数・取得済み数・今回取得できる数）。
     *
     * <p>窓の「N 語が対象です（うち取得済み M 語）」に使う。一覧と同じビュー
     * （{@code v_jpn_word_ai_state}）を見るので、画面の「取得状態」と食い違わない。</p>
     */
    JpnAiTargetCountsEntity countAiTargets(@Param("keyword") String keyword,
                                           @Param("reading") String reading,
                                           @Param("jlpt") String jlpt,
                                           @Param("part") String part,
                                           @Param("state") String state,
                                           @Param("book") String book,
                                           @Param("categoryFrom") String categoryFrom,
                                           @Param("categoryTo") String categoryTo,
                                           @Param("learnState") String learnState,
                                           @Param("accountId") long accountId,
                                           @Param("kind") String kind);

    JpnWordEntity findById(@Param("wordId") long wordId, @Param("accountId") long accountId);

    /** 登録・更新の重複チェック（同じ 見出し語 + 読み が既にあるか）。 */
    JpnWordEntity findByWordAndReading(@Param("word") String word, @Param("reading") String reading);

    int insert(JpnWordEntity entity);

    int update(JpnWordEntity entity);

    int delete(@Param("wordId") long wordId);

    /** 収録（教材のどこに載っているか）。 */
    List<JpnCollectionEntity> listCollections(@Param("wordId") long wordId);

    /**
     * 収録を 1 件つくる（新規登録画面の保存）。
     *
     * <p>書籍は**名前**で入れる（{@code JPN_書籍情報} のマスタは名前で引き当てる）。</p>
     */
    int insertCollection(@Param("wordId") long wordId,
                         @Param("level") String level,
                         @Param("book") String book,
                         @Param("category") String category,
                         @Param("wordSeq") Integer wordSeq,
                         @Param("listedWord") String listedWord,
                         @Param("listedPartOfSpeech") String listedPartOfSpeech,
                         @Param("listedChineseMeaning") String listedChineseMeaning,
                         @Param("accountId") long accountId);

    /** その語が既にその書籍・分類に載っているか（二重に収録を作らない）。 */
    long countCollection(@Param("wordId") long wordId,
                         @Param("book") String book,
                         @Param("category") String category);

    /** その書籍・分類で、いま使われている単語SEQ の最大値（次の SEQ を決める）。 */
    Integer maxCollectionSeq(@Param("book") String book, @Param("category") String category);

    /** その書籍・分類・SEQ が既に使われているか（位置が重ならないようにする）。 */
    long countCollectionSeq(@Param("book") String book,
                            @Param("category") String category,
                            @Param("wordSeq") Integer wordSeq);

    /** 書籍マスタから名前で ID を引く（無ければ null）。 */
    Long findBookIdByName(@Param("book") String book);

    /** 数字だけの書籍コードの最大値（新しい書籍のコードを決める）。無ければ null。 */
    Integer maxBookCode();

    /** 書籍マスタを 1 行作る（分類数・収録語数は 0 で入れ、あとで数え直す）。 */
    int insertBook(@Param("bookCode") String bookCode,
                   @Param("bookName") String bookName,
                   @Param("accountId") long accountId);

    /** その書籍の 分類数・収録語数 を収録から数え直す。 */
    int refreshBookCounts(@Param("book") String book);

    /** その単語の問題（種類と数）。 */
    List<JpnQuestionEntity> listQuestions(@Param("wordId") long wordId);

    /**
     * 問題（C/D/E）の版の一覧（一覧の「取得状態」から開く履歴。新しい版が先）。
     *
     * <p>1 行 = {@code JPN_AI生成履歴情報} の 1 回（＝版）。その版で書かれた問題の数と、
     * 今その版が使われているか（{@code 状態コード='ACTIVE'}）も一緒に返す。</p>
     */
    List<JpnQuestionVersionEntity> listQuestionVersions(@Param("wordId") long wordId);

    /**
     * 問題の版を切り替える（その種別の中で、指定した版だけを {@code ACTIVE}、ほかを {@code ARCHIVED}）。
     *
     * <p>指定した版に問題が無ければ 0 行（呼ぶ側が 400 にする）。問題は消さない（テストの出題が
     * 参照しているため）。</p>
     *
     * @return 更新した行数
     */
    int activateQuestionVersion(@Param("wordId") long wordId,
                                @Param("questionType") String questionType,
                                @Param("contentVersion") int contentVersion,
                                @Param("accountId") long accountId);

    /**
     * 同じ見出し語で別の読みを持つ語があれば、その読み（無ければ null）。
     *
     * <p>画面が「同じ表記に「〜」の読みもあります（別の単語として登録）」と出すために使う。
     * 2.0 は 詳細JSON の {@code alternateReading} に持っていたが、2.1 は語の表記から引く。</p>
     */
    String findAlternateReading(@Param("wordId") long wordId);
}
