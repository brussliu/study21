package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.japanese.JapaneseModels;
import com.study21.user.japanese.JapaneseService;
import com.study21.user.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日本語勉強 API（user-api）。親メニュー【日本語勉強】の 3 画面が使う。
 *
 * <ul>
 *   <li>`GET /words` / `GET /words/{wordId}` … 単語情報管理（一覧・詳細）</li>
 *   <li>`POST /words` / `PUT /words/{wordId}` / `DELETE /words/{wordId}` … 単語の登録・修正・削除</li>
 *   <li>`PUT /words/{wordId}/editor` … 基本情報と詳細の一括保存（詳細は**新しい版**になる）</li>
 *   <li>`GET /words/{wordId}/detail-versions` … 詳細の版の一覧（新しい順）</li>
 *   <li>`PUT /words/{wordId}/detail-versions/{detailId}/active` … 有効な版の切り替え</li>
 *   <li>`GET /words/{wordId}/question-versions` … 問題（C/D/E）の版の一覧（取得の履歴）</li>
 *   <li>`PUT /words/{wordId}/question-versions/{questionType}/active` … 使用する問題の版の切り替え</li>
 *   <li>`PATCH /words/{wordId}/favorite` / `/learned` … お気に入り・習得済</li>
 *   <li>`GET /tests` / `GET /tests/{testId}` … 単語テストの履歴・出題</li>
 *   <li>`POST /tests` … テストの作成（条件に合う問題を選ぶ）</li>
 *   <li>`POST /tests/{testId}/answers` … 回答（判定して学習状況を更新）</li>
 *   <li>`POST /tests/{testId}/complete` / `DELETE /tests/{testId}`</li>
 *   <li>`GET /status` / `GET /status/skills` … 単語勉強状況</li>
 * </ul>
 *
 * 2.0 の `japanese_word.jsp` / `japanese_test.jsp` / `japanese_word_status.jsp` の操作を
 * ひととおり揃えてある（データは study3 DB から移行済み）。
 */
@RestController
@RequestMapping("/api/user/japanese")
public class JapaneseController {

    private final JapaneseService japaneseService;

    public JapaneseController(JapaneseService japaneseService) {
        this.japaneseService = japaneseService;
    }

    /* ---------- 単語情報管理 ---------- */

    @GetMapping("/words")
    public ApiResponse<JapaneseModels.WordListResult> words(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "reading", required = false) String reading,
            @RequestParam(value = "jlpt", required = false) String jlpt,
            @RequestParam(value = "part", required = false) String part,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "book", required = false) String book,
            /** 分類の範囲（From ～ To。片方だけでもよい） */
            @RequestParam(value = "categoryFrom", required = false) String categoryFrom,
            @RequestParam(value = "categoryTo", required = false) String categoryTo,
            @RequestParam(value = "learnState", required = false) String learnState,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(japaneseService.searchWords(user.accountId(), keyword, reading, jlpt, part, state,
                book, categoryFrom, categoryTo, learnState, page, size));
    }

    /**
     * AI 取得の対象（**検索条件に一致する語**のうち、今回受付ける分）。
     *
     * <p>画面の窓（AI 取得）が、対象の件数と「今回受付ける語」を出すために呼ぶ。絞り込みは
     * 一覧（{@code GET /words}）と**同じ**で、**ページは関係しない**（何ページ目でも、
     * 条件に一致する語全体から表示順に選ぶ）。</p>
     *
     * @param kind         取得区分（{@code DETAIL} / {@code C} / {@code D} / {@code E}）
     * @param skipAcquired true＝取得済み・取得中を除く／false＝一致する語をそのまま（すべて再取得）
     * @param limit        この回に受付ける最大語数（画面が設定値から渡す。1〜200）
     */
    @GetMapping("/words/ai-targets")
    public ApiResponse<JapaneseModels.AiTargets> aiTargets(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(value = "kind") String kind,
            @RequestParam(value = "skipAcquired", defaultValue = "true") boolean skipAcquired,
            @RequestParam(value = "limit", defaultValue = "200") int limit,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "reading", required = false) String reading,
            @RequestParam(value = "jlpt", required = false) String jlpt,
            @RequestParam(value = "part", required = false) String part,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "book", required = false) String book,
            @RequestParam(value = "categoryFrom", required = false) String categoryFrom,
            @RequestParam(value = "categoryTo", required = false) String categoryTo,
            @RequestParam(value = "learnState", required = false) String learnState) {
        return ApiResponse.ok(japaneseService.aiTargets(user.accountId(), keyword, reading, jlpt, part, state,
                book, categoryFrom, categoryTo, learnState, kind, skipAcquired, limit));
    }

    @GetMapping("/words/{wordId}")
    public ApiResponse<JapaneseModels.WordDetailResult> word(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long wordId) {
        return ApiResponse.ok(japaneseService.wordDetail(user.accountId(), wordId));
    }

    @PostMapping("/words")
    public ApiResponse<JapaneseModels.WordMutationResult> createWord(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody JapaneseModels.WordSaveRequest request) {
        JapaneseModels.WordMutationResult result = japaneseService.createWord(user, request);
        return ApiResponse.ok(result, result.message());
    }

    /**
     * 新規登録画面の保存（**語と収録をまとめて**入れる）。
     *
     * <p>語だけを作ると一覧の書籍・分類が空になるので、画面はこちらを使う
     * （1 語ずつ {@code POST /words} を呼ぶ必要はない）。</p>
     */
    @PostMapping("/words/register")
    public ApiResponse<JapaneseModels.RegisterResult> registerWords(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody JapaneseModels.RegisterRequest request) {
        JapaneseModels.RegisterResult result = japaneseService.registerWords(user, request);
        return ApiResponse.ok(result, result.message());
    }

    @PutMapping("/words/{wordId}")
    public ApiResponse<JapaneseModels.WordMutationResult> updateWord(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long wordId,
            @Valid @RequestBody JapaneseModels.WordSaveRequest request) {
        JapaneseModels.WordMutationResult result = japaneseService.updateWord(user, wordId, request);
        return ApiResponse.ok(result, result.message());
    }

    @PutMapping("/words/{wordId}/editor")
    public ApiResponse<JapaneseModels.WordDetailResult> saveWordEditor(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long wordId,
            @Valid @RequestBody JapaneseModels.WordEditorRequest request) {
        return ApiResponse.ok(japaneseService.saveWordEditor(user, wordId, request), "保存しました。");
    }

    /**
     * 詳細の版の履歴（**新しい順**）。段落の行数も返すので、画面は「どの版が内容が多いか」を出せる。
     */
    @GetMapping("/words/{wordId}/detail-versions")
    public ApiResponse<JapaneseModels.WordDetailVersions> detailVersions(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long wordId) {
        return ApiResponse.ok(japaneseService.detailVersions(user.accountId(), wordId));
    }

    /**
     * 指定した版を有効にする（楽観的ロックは {@code version}）。
     *
     * <p>更新後の詳細を返すので、画面は取得し直さずに表示を差し替えられる。
     * 版を指定しないときは何もしなくてよい（新しい版は必ず ACTIVE で生まれるので、
     * 未指定＝最後に作った版が有効）。</p>
     */
    @PutMapping("/words/{wordId}/detail-versions/{detailId}/active")
    public ApiResponse<JapaneseModels.WordDetailResult> activateDetailVersion(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long wordId,
            @PathVariable long detailId,
            @Valid @RequestBody JapaneseModels.ActivateVersionRequest request) {
        return ApiResponse.ok(
                japaneseService.activateDetailVersion(user, wordId, detailId, request),
                "有効な版を切り替えました。");
    }

    /**
     * 問題（C/D/E）の版の履歴（一覧の「取得状態」のタグから開く）。
     *
     * <p>1 行 = AI の取得 1 回（{@code JPN_AI生成履歴情報}）。2.0 の「詳細情報取得履歴」と同じ形で、
     * プロバイダ・モデル・状態・取得日時・生成件数を返す。</p>
     */
    @GetMapping("/words/{wordId}/question-versions")
    public ApiResponse<JapaneseModels.WordQuestionVersionList> questionVersions(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long wordId) {
        return ApiResponse.ok(japaneseService.questionVersions(user.accountId(), wordId));
    }

    /**
     * 問題の版を切り替える（その種別の中で、指定した版だけを有効にする）。
     *
     * <p>問題を消さないので、切り替えてもテストの出題（過去の参照）は壊れない。</p>
     */
    @PutMapping("/words/{wordId}/question-versions/{questionType}/active")
    public ApiResponse<JapaneseModels.WordQuestionVersionList> activateQuestionVersion(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long wordId,
            @PathVariable String questionType,
            @Valid @RequestBody JapaneseModels.ActivateQuestionVersionRequest request) {
        return ApiResponse.ok(
                japaneseService.activateQuestionVersion(user, wordId, questionType, request.contentVersion()),
                "使用する版を切り替えました。");
    }

    @DeleteMapping("/words/{wordId}")
    public ApiResponse<JapaneseModels.SimpleResult> deleteWord(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long wordId) {
        JapaneseModels.SimpleResult result = japaneseService.deleteWord(user, wordId);
        return ApiResponse.ok(result, result.message());
    }

    @PatchMapping("/words/{wordId}/favorite")
    public ApiResponse<JapaneseModels.WordMutationResult> favorite(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long wordId,
            @RequestBody JapaneseModels.FavoriteRequest request) {
        JapaneseModels.WordMutationResult result = japaneseService.setFavorite(user, wordId, request.favorite());
        return ApiResponse.ok(result, result.message());
    }

    @PatchMapping("/words/{wordId}/learned")
    public ApiResponse<JapaneseModels.WordMutationResult> learned(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long wordId,
            @RequestBody JapaneseModels.LearnedRequest request) {
        JapaneseModels.WordMutationResult result = japaneseService.setLearned(user, wordId, request.learned());
        return ApiResponse.ok(result, result.message());
    }

    /* ---------- 単語テスト ---------- */

    @GetMapping("/tests")
    public ApiResponse<JapaneseModels.TestListResult> tests(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "testType", required = false) String testType,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(japaneseService.searchTests(user.accountId(), state, testType, page, size));
    }

    @GetMapping("/tests/{testId}")
    public ApiResponse<JapaneseModels.TestDetailResult> test(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long testId) {
        return ApiResponse.ok(japaneseService.testDetail(user.accountId(), testId));
    }

    @PostMapping("/tests/{testId}/start")
    public ApiResponse<JapaneseModels.TestDetailResult> startTest(@AuthenticationPrincipal UserPrincipal user, @PathVariable long testId) {
        return ApiResponse.ok(japaneseService.startTest(user.accountId(), testId));
    }

    @PostMapping("/tests")
    public ApiResponse<JapaneseModels.TestDetailResult> createTest(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody JapaneseModels.TestCreateRequest request) {
        return ApiResponse.ok(japaneseService.createTest(user, request), "テストを作成しました。");
    }

    @PostMapping("/tests/{testId}/answers")
    public ApiResponse<JapaneseModels.AnswerResult> answer(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long testId,
            @Valid @RequestBody JapaneseModels.AnswerRequest request) {
        JapaneseModels.AnswerResult result = japaneseService.answer(user, testId, request);
        return ApiResponse.ok(result, result.message());
    }

    @PostMapping("/tests/{testId}/complete")
    public ApiResponse<JapaneseModels.TestMutationResult> complete(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long testId) {
        JapaneseModels.TestMutationResult result = japaneseService.completeTest(user, testId);
        return ApiResponse.ok(result, result.message());
    }

    @DeleteMapping("/tests/{testId}")
    public ApiResponse<JapaneseModels.SimpleResult> deleteTest(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long testId) {
        JapaneseModels.SimpleResult result = japaneseService.deleteTest(user, testId);
        return ApiResponse.ok(result, result.message());
    }

    /* ---------- 単語勉強状況 ---------- */

    @GetMapping("/status")
    public ApiResponse<JapaneseModels.StatusResult> status(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(value = "learnState", required = false) String learnState,
            @RequestParam(value = "jlpt", required = false) String jlpt,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(japaneseService.status(user.accountId(), learnState, jlpt, page, size));
    }

    @GetMapping("/status/skills")
    public ApiResponse<JapaneseModels.SkillListResult> skills(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(value = "testType", required = false) String testType,
            @RequestParam(value = "skill", required = false) String skill,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(japaneseService.skills(user.accountId(), testType, skill, page, size));
    }
}
