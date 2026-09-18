package com.study21.user.japanese;

import com.study21.user.security.UserPrincipal;

/**
 * 日本語勉強（単語情報管理・単語テスト・単語勉強状況）の業務処理。
 *
 * <p>2.0 の日本語機能（study3 DB の `STY_日本語*` テーブル）を 2.1 に移したもの。
 * 学習状況（習得度・お気に入り・復習日）は**アカウントごと**に持つ。</p>
 */
public interface JapaneseService {

    /* ---------- 単語情報管理 ---------- */

    /** 単語の一覧（検索・ページング・サマリ）。 */
    JapaneseModels.WordListResult searchWords(long accountId, String keyword, String reading, String jlpt,
                                              String part, String state, String book, String category,
                                              String learnState, int page, int size);

    /** 単語 1 件（収録・問題・AI 詳細つき）。 */
    JapaneseModels.WordDetailResult wordDetail(long accountId, long wordId);

    /** 単語の登録。 */
    JapaneseModels.WordMutationResult createWord(UserPrincipal user, JapaneseModels.WordSaveRequest request);

    /** 単語の修正（楽観的ロック）。 */
    JapaneseModels.WordMutationResult updateWord(UserPrincipal user, long wordId,
                                                 JapaneseModels.WordSaveRequest request);

    /** 単語の削除（収録・問題・学習状況も一緒に消える）。 */
    JapaneseModels.SimpleResult deleteWord(UserPrincipal user, long wordId);

    /** お気に入りの切替。 */
    JapaneseModels.WordMutationResult setFavorite(UserPrincipal user, long wordId, boolean favorite);

    /** 習得済の切替。 */
    JapaneseModels.WordMutationResult setLearned(UserPrincipal user, long wordId, boolean learned);

    /* ---------- 単語テスト ---------- */

    /** テストの履歴（新しい順）。 */
    JapaneseModels.TestListResult searchTests(long accountId, String state, String testType, int page, int size);

    /** テスト 1 件（出題と選択肢つき。再開・結果表示に使う）。 */
    JapaneseModels.TestDetailResult testDetail(long accountId, long testId);

    /** テストの作成（条件に合う問題を選んで出題を作る）。 */
    JapaneseModels.TestDetailResult createTest(UserPrincipal user, JapaneseModels.TestCreateRequest request);

    /** 回答（正誤を判定し、学習状況・技能習得・日次を更新する）。 */
    JapaneseModels.AnswerResult answer(UserPrincipal user, long testId, JapaneseModels.AnswerRequest request);

    /** テストを完了にする。 */
    JapaneseModels.TestMutationResult completeTest(UserPrincipal user, long testId);

    /** テストの削除（出題も一緒に消える）。 */
    JapaneseModels.SimpleResult deleteTest(UserPrincipal user, long testId);

    /* ---------- 単語勉強状況 ---------- */

    /** 勉強状況（サマリ・日次・語別の学習状況）。 */
    JapaneseModels.StatusResult status(long accountId, String learnState, String jlpt, int page, int size);

    /** 技能別の習得。 */
    JapaneseModels.SkillListResult skills(long accountId, String testType, String skill, int page, int size);
}
