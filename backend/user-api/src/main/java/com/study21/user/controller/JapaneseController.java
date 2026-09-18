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
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "learnState", required = false) String learnState,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(japaneseService.searchWords(user.accountId(), keyword, reading, jlpt, part, state,
                book, category, learnState, page, size));
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

    @PutMapping("/words/{wordId}")
    public ApiResponse<JapaneseModels.WordMutationResult> updateWord(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long wordId,
            @Valid @RequestBody JapaneseModels.WordSaveRequest request) {
        JapaneseModels.WordMutationResult result = japaneseService.updateWord(user, wordId, request);
        return ApiResponse.ok(result, result.message());
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
