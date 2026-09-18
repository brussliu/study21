package com.study21.user.japanese;

import com.study21.common.core.exception.NotFoundException;
import com.study21.user.account.AccountService;
import com.study21.user.account.AccountType;
import com.study21.user.account.RegisterRequest;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 実 DB（PostgreSQL）に対する日本語勉強の検証。
 *
 * ・2.0（study3 DB）から移行した単語 9,847 語・テスト 15 回・学習状況 213 語が読めること
 * ・単語テストを 作成 → 回答 → 完了 → 削除 まで通ること
 * ・回答で学習状況・技能習得・日次が更新されること
 * テストはロールバックするので DB は汚れない。
 *
 * 実行には DB のパスワードが要る（無いときはスキップする）:
 *   STUDY21_DATASOURCE_PASSWORD=... mvn -pl user-api -am test
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class JapaneseRepositoryTest {

    @Autowired
    private JapaneseService japaneseService;

    @Autowired
    private AccountService accountService;

    /** 検証用のアカウントをこのテストの中で作る（実在のアカウントの学習状況を汚さない）。 */
    private long createStudentAccountId() {
        String email = "e2e-jp-test-" + System.nanoTime() + "@example.com";
        RegisterRequest request = new RegisterRequest();
        request.setParentEmail(email);
        request.setParentPassword("Parent1234");
        request.setSei("検証");
        request.setMei("保護者");
        request.setSeiKana("けんしょう");
        request.setMeiKana("ほごしゃ");
        request.setGrade("中学1年生");
        request.setStudentEmail("s-" + email);
        request.setStudentPassword("Student1234");
        request.setAgreed(true);
        return accountService.register(request).getStudentAccountId();
    }

    /** 単語の一覧・詳細・勉強状況は アカウントID だけあればよい。 */
    private long createStudentAccountIdForRead() {
        return createStudentAccountId();
    }

    @Test
    void migratedWordsAreReadable() {
        long accountId = createStudentAccountId();

        JapaneseModels.WordListResult words = japaneseService.searchWords(accountId, null, null, null, null,
                null, null, null, null, 1, 20);

        // 2.0 から移行した 9,847 語
        assertThat(words.totals().wordCount()).isGreaterThanOrEqualTo(9_800);
        assertThat(words.items()).hasSize(20);
        assertThat(words.items()).allSatisfy(item -> {
            assertThat(item.word()).isNotBlank();
            assertThat(item.reading()).isNotBlank();
            assertThat(item.learnState()).isEqualTo("NOT_STARTED");
            assertThat(item.collectionCount()).isGreaterThanOrEqualTo(1);
            assertThat(item.book()).isEqualTo("01.N1~N5日本語単語");
        });

        // 見出し語で絞り込める（「愛」の並びには「恋愛」なども含まれる）
        JapaneseModels.WordListResult searched = japaneseService.searchWords(accountId, "愛", "あい", null, null,
                null, null, null, null, 1, 20);
        assertThat(searched.items()).isNotEmpty();
        assertThat(searched.items()).extracting(JapaneseModels.WordRow::word).contains("愛");
    }

    @Test
    void wordDetailReturnsMigratedDetailQuestionsAndCollections() {
        long accountId = createStudentAccountId();
        long wordId = japaneseService.searchWords(accountId, "愛", "あい", null, null, null, null, null, null, 1, 20)
                .items().stream()
                .filter(item -> "愛".equals(item.word()))
                .findFirst()
                .orElseThrow()
                .wordId();

        JapaneseModels.WordDetailResult detail = japaneseService.wordDetail(accountId, wordId);

        assertThat(detail.word().word()).isEqualTo("愛");
        assertThat(detail.collections()).isNotEmpty();
        assertThat(detail.collections().get(0).book()).isEqualTo("01.N1~N5日本語単語");
        assertThat(detail.questions()).isNotEmpty();
        assertThat(detail.questions()).allSatisfy(question -> {
            assertThat(question.questionType()).isNotBlank();
            assertThat(question.choiceCount()).isEqualTo(4);
        });
        // 2.0 の AI 詳細（語義・例文などを 1 つの JSON にまとめたもの）
        assertThat(detail.detail()).isNotNull();
        assertThat(detail.detail().detail()).isNotEmpty();
        assertThat(detail.detail().detail().keySet())
                .containsAnyOf("senses", "examples", "jlptLevel", "chineseMeaning");
    }

    @Test
    void migratedTestsAreReadable() {
        UserPrincipal owner = new UserPrincipal(1L, "bruss.ji.liu@gmail.com", "試驗 保護者", AccountType.GUARDIAN);

        JapaneseModels.TestListResult tests = japaneseService.searchTests(owner.accountId(), null, null, 1, 50);

        // 2.0 から移行した 15 回（A〜E が各 3 回）
        assertThat(tests.items()).hasSize(15);
        assertThat(tests.totals().testCount()).isEqualTo(15);
        assertThat(tests.items()).extracting(JapaneseModels.TestRow::testType)
                .contains("A", "B", "C", "D", "E");

        // 完了したテストの出題（判定つき）が読める
        JapaneseModels.TestRow completed = tests.items().stream()
                .filter(test -> "COMPLETED".equals(test.state()) && test.doneCount() > 0)
                .findFirst()
                .orElseThrow();
        JapaneseModels.TestDetailResult detail = japaneseService.testDetail(owner.accountId(), completed.testId());

        assertThat(detail.questions()).hasSize(completed.questionCount());
        assertThat(detail.questions()).allSatisfy(view -> {
            assertThat(view.question().word()).isNotBlank();
            assertThat(view.question().orderNo()).isGreaterThanOrEqualTo(1);
        });
        assertThat(detail.questions().stream().filter(view -> view.question().judgment() != null).count())
                .isGreaterThan(0);
    }

    @Test
    void learningStatusIsReadable() {
        JapaneseModels.StatusResult status = japaneseService.status(1L, null, null, 1, 20);

        assertThat(status.summary().studiedWordCount()).isEqualTo(213);
        assertThat(status.summary().answeredCount()).isGreaterThan(0);
        assertThat(status.summary().accuracyPercent()).isBetween(0, 100);
        assertThat(status.daily()).isNotEmpty();
        assertThat(status.items()).isNotEmpty();
        assertThat(status.items()).allSatisfy(row -> {
            assertThat(row.word()).isNotBlank();
            assertThat(row.mastery()).isNotNull();
            assertThat(row.learnState()).isIn(JapaneseModels.LEARN_STATES);
        });

        // 技能別の習得（906 件。テスト種別と技能区分で絞れる）
        JapaneseModels.SkillListResult skills = japaneseService.skills(1L, "B", null, 1, 20);
        assertThat(skills.items()).isNotEmpty();
        assertThat(skills.items()).allSatisfy(skill -> {
            assertThat(skill.testType()).isEqualTo("B");
            assertThat(skill.skillCode()).startsWith("B_");
        });
    }

    @Test
    void takesTestAndUpdatesLearning() {
        long accountId = createStudentAccountId();
        UserPrincipal user = new UserPrincipal(accountId, "e2e@example.com", "試験 生徒", AccountType.STUDENT);
        long wordId = japaneseService.searchWords(user.accountId(), "愛", "あい", null, null, null, null, null,
                null, 1, 20).items().stream()
                .filter(item -> "愛".equals(item.word()))
                .findFirst()
                .orElseThrow()
                .wordId();

        // 作成（種別 D は文脈の意味。ランダムに 3 問）
        JapaneseModels.TestDetailResult created = japaneseService.createTest(user,
                new JapaneseModels.TestCreateRequest("D", null, null, null, null, "NORMAL", "RANDOM", 3));

        assertThat(created.test().testNo()).startsWith("JT-");
        assertThat(created.test().state()).isEqualTo("RUNNING");
        assertThat(created.test().questionCount()).isEqualTo(3);
        assertThat(created.questions()).hasSize(3);
        assertThat(created.questions()).allSatisfy(view -> {
            assertThat(view.question().questionText()).isNotBlank();
            assertThat(view.question().correctValue()).isNotBlank();
            assertThat(view.choices()).hasSize(4);
            assertThat(view.choices()).anySatisfy(choice -> assertThat(choice.correct()).isTrue());
        });

        // 1 問ずつ正解して回答する
        for (JapaneseModels.TestQuestionView view : created.questions()) {
            JapaneseModels.ChoiceRow correct = view.choices().stream()
                    .filter(JapaneseModels.ChoiceRow::correct).findFirst().orElseThrow();
            JapaneseModels.AnswerResult result = japaneseService.answer(user, created.test().testId(),
                    new JapaneseModels.AnswerRequest(view.question().orderNo(), correct.choiceId(), null, 5_000L));
            assertThat(result.correct()).isTrue();
            assertThat(result.judgment()).isEqualTo("CORRECT");
        }

        JapaneseModels.TestRow finished = japaneseService.testDetail(user.accountId(), created.test().testId()).test();
        assertThat(finished.doneCount()).isEqualTo(3);
        assertThat(finished.correctCount()).isEqualTo(3);
        assertThat(finished.scorePercent()).isEqualTo(100);
        assertThat(finished.state()).isEqualTo("COMPLETED");

        // 学習状況・技能習得・日次が更新されている
        JapaneseModels.StatusResult status = japaneseService.status(user.accountId(), null, null, 1, 20);
        assertThat(status.summary().studiedWordCount()).isEqualTo(3);
        assertThat(status.summary().answeredCount()).isEqualTo(3);
        assertThat(status.summary().correctCount()).isEqualTo(3);
        assertThat(status.summary().todayActiveMs()).isEqualTo(15_000L);
        assertThat(status.items()).allSatisfy(row -> {
            assertThat(row.mastery()).isGreaterThan(java.math.BigDecimal.ZERO);
            assertThat(row.nextReviewAt()).isNotNull();
        });
        JapaneseModels.SkillListResult skills = japaneseService.skills(user.accountId(), "D", null, 1, 20);
        assertThat(skills.items()).hasSize(3);
        assertThat(skills.items()).allSatisfy(skill ->
                assertThat(skill.skillCode()).isEqualTo("D_CONTEXT_MEANING"));

        // 語別の学習状況は wordId を指定して読む（お気に入り・習得済の切替も同じ経路）
        assertThat(japaneseService.wordDetail(user.accountId(), wordId).word().word()).isNotBlank();

        // 完了 → 削除
        assertThat(japaneseService.completeTest(user, created.test().testId()).message()).contains("完了");
        japaneseService.deleteTest(user, created.test().testId());
        assertThatThrownBy(() -> japaneseService.testDetail(user.accountId(), created.test().testId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void favoriteAndLearnedAreStoredPerAccount() {
        long accountId = createStudentAccountId();
        UserPrincipal user = new UserPrincipal(accountId, "e2e@example.com", "試験 生徒", AccountType.STUDENT);
        long wordId = japaneseService.searchWords(user.accountId(), "朝", "あさ", null, null, null, null, null,
                null, 1, 20).items().stream()
                .filter(item -> "朝".equals(item.word()))
                .findFirst()
                .orElseThrow()
                .wordId();

        JapaneseModels.WordMutationResult favorite = japaneseService.setFavorite(user, wordId, true);
        assertThat(favorite.word().favorite()).isTrue();

        JapaneseModels.WordMutationResult learned = japaneseService.setLearned(user, wordId, true);
        assertThat(learned.word().learned()).isTrue();
        assertThat(learned.word().learnState()).isEqualTo("MASTERED");

        JapaneseModels.StatusResult status = japaneseService.status(user.accountId(), null, null, 1, 20);
        assertThat(status.summary().favoriteCount()).isEqualTo(1);
        assertThat(status.summary().learnedCount()).isEqualTo(1);

        // 解除もできる
        assertThat(japaneseService.setLearned(user, wordId, false).word().learned()).isFalse();
        assertThat(japaneseService.setFavorite(user, wordId, false).word().favorite()).isFalse();
    }

    @Test
    void createsUpdatesAndDeletesWord() {
        long accountId = createStudentAccountId();
        UserPrincipal user = new UserPrincipal(accountId, "e2e@example.com", "試験 生徒", AccountType.STUDENT);

        JapaneseModels.WordMutationResult created = japaneseService.createWord(user,
                new JapaneseModels.WordSaveRequest("検証用単語", "けんしょうようたんご", "N1", "[名]", "ACTIVE",
                        "テストで作成", null));
        long wordId = created.word().wordId();
        assertThat(created.word().word()).isEqualTo("検証用単語");
        assertThat(created.word().learnState()).isEqualTo("NOT_STARTED");

        JapaneseModels.WordMutationResult updated = japaneseService.updateWord(user, wordId,
                new JapaneseModels.WordSaveRequest("検証用単語2", "けんしょうようたんご2", "N2", "[名]", "ACTIVE",
                        null, created.word().version()));
        assertThat(updated.word().word()).isEqualTo("検証用単語2");
        assertThat(updated.word().jlptLevel()).isEqualTo("N2");

        // 検索で見つかる（読みの部分一致）
        JapaneseModels.WordListResult found = japaneseService.searchWords(user.accountId(), null,
                "けんしょうよう", null, null, null, null, null, null, 1, 20);
        assertThat(found.items()).extracting(JapaneseModels.WordRow::wordId).contains(wordId);

        japaneseService.deleteWord(user, wordId);
        assertThatThrownBy(() -> japaneseService.wordDetail(user.accountId(), wordId))
                .isInstanceOf(NotFoundException.class);
    }
}
