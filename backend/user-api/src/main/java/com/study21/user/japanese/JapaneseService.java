package com.study21.user.japanese;

import com.study21.user.security.UserPrincipal;

/**
 * 日本語勉強（単語情報管理・単語テスト・単語勉強状況）の業務処理。
 *
 * <p>2.0 の日本語機能（study3 DB の `STY_日本語*` テーブル）を 2.1 に移したもの。
 * 学習状況（習得度・お気に入り・復習日）は**アカウントごと**に持つ。</p>
 */
public interface JapaneseService {

    /**
     * 単語の編集を保存する（<b>詳細の新しい版</b>を作る）。
     *
     * <p>{@code request.contentVersion()} は画面が読んだときの {@code 内容版数}。今の有効版と
     * 違えば 409（ほかの操作が先に版を作った）。段落の行は要求の内容で作り直し、
     * <b>元の版と内容が同じ行は出所を引き継ぎ</b>（AI の行は BATCH のまま）、変わった行・
     * 足した行は {@code 登録元コード='APP'} にする。要求に無い行は消える。</p>
     */
    JapaneseModels.WordDetailResult saveWordEditor(UserPrincipal user, long wordId,
                                                   JapaneseModels.WordEditorRequest request);

    /**
     * その語の詳細の版の一覧（<b>新しい順</b>）。段落の行数も返す。
     */
    JapaneseModels.WordDetailVersions detailVersions(long accountId, long wordId);

    /**
     * 指定した版を有効にする（{@code 状態コード} を {@code ACTIVE} に移す）。
     *
     * <p>楽観的ロックは {@code バージョン}。一致しなければ 409。その語の版でなければ 404。
     * 同じトランザクションで元の有効版を {@code ARCHIVED} にする（部分 UNIQUE 索引
     * {@code uq_jpn_detail_active} があるので、先に ARCHIVED にしてから ACTIVE にする）。</p>
     *
     * <p><strong>「未指定＝最新版が有効」</strong>: 版を指定しない場合は何もしなくてよい。
     * 新しい版は必ず {@code ACTIVE} で生まれる（{@code saveWordEditor} と AI 取得のどちらも）ので、
     * 何も指定しなければ最後に作った版がそのまま有効になっている。</p>
     *
     * @return 更新後の詳細（{@code GET /words/{wordId}} の detail と同じ形）
     */
    JapaneseModels.WordDetailResult activateDetailVersion(UserPrincipal user, long wordId, long detailId,
                                                          JapaneseModels.ActivateVersionRequest request);

    /**
     * 問題（C/D/E）の版の一覧（一覧の「取得状態」のタグから開く履歴。新しい版が先）。
     *
     * <p>1 行 = AI の取得 1 回（{@code JPN_AI生成履歴情報} の {@code 内容版数}）。
     * その版で書かれた問題の数と、今その版を使っているかも返す（2.0 の「詳細情報取得履歴」と同じ）。</p>
     */
    JapaneseModels.WordQuestionVersionList questionVersions(long accountId, long wordId);

    /**
     * 問題の版を切り替える（その種別の中で、指定した版だけを有効にする）。
     *
     * <p>問題は消さない（テストの出題が参照している）。指定した版に問題が無ければ 400。</p>
     */
    JapaneseModels.WordQuestionVersionList activateQuestionVersion(UserPrincipal user, long wordId,
                                                                   String questionType, int contentVersion);

    /* ---------- 単語情報管理 ---------- */

    /**
     * 単語の一覧（検索・ページング・サマリ）。
     *
     * <p>分類は**範囲**（`categoryFrom` ～ `categoryTo`。どちらか片方だけでもよい）。
     * 教材の課次（`Unit001` など）を「この Unit からこの Unit まで」で絞るために使う（2.0 と同じ）。</p>
     */
    JapaneseModels.WordListResult searchWords(long accountId, String keyword, String reading, String jlpt,
                                              String part, String state, String book,
                                              String categoryFrom, String categoryTo,
                                              String learnState, int page, int size);

    /** 単語 1 件（収録・問題・AI 詳細つき）。 */
    JapaneseModels.WordDetailResult wordDetail(long accountId, long wordId);

    /**
     * AI 取得の対象（**検索条件に一致する語**のうち、今回受付ける分）を選ぶ。
     *
     * <p>絞り込みは {@link #searchWords} と**同じ**（一覧に出ている語の集まりがそのまま対象）。
     * ページは関係しない（何ページ目でも、条件に一致する語全体から表示順に選ぶ）。</p>
     *
     * @param kind         取得区分（{@code DETAIL} / {@code C} / {@code D} / {@code E}）
     * @param skipAcquired true＝取得済み・取得中を除く／false＝一致する語をそのまま（すべて再取得）
     * @param limit        この回に受付ける最大語数（画面が設定値から渡す。1〜200 に丸める）
     */
    JapaneseModels.AiTargets aiTargets(long accountId, String keyword, String reading, String jlpt,
                                       String part, String state, String book,
                                       String categoryFrom, String categoryTo, String learnState,
                                       String kind, boolean skipAcquired, int limit);

    /** 単語の登録。 */
    JapaneseModels.WordMutationResult createWord(UserPrincipal user, JapaneseModels.WordSaveRequest request);

    /**
     * 新規登録画面の保存（**語と収録をいっしょに**入れる）。
     *
     * <p>語だけを作ると「どの書籍のどの Unit に載っているか」が残らず、一覧の書籍・分類が空になる。
     * 1 回の呼び出しで語（無ければ作る）と収録を作る。</p>
     */
    /**
     * 語と収録（書籍・分類・SEQ）をまとめて登録する（新規登録画面の保存）。
     *
     * <p>語だけを入れると一覧の書籍・分類が空になるので、画面はこちらを使う。
     * 同じ語・同じ Unit に既にある行は飛ばす（{@code skipped} に残す）。</p>
     */
    JapaneseModels.RegisterResult registerWords(UserPrincipal user, JapaneseModels.RegisterRequest request);

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
    JapaneseModels.TestDetailResult startTest(long accountId, long testId);

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
