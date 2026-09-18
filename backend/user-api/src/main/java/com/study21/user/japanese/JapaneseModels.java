package com.study21.user.japanese;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 日本語勉強（単語情報管理・単語テスト・単語勉強状況）のモデル。
 *
 * <p>2.0 の日本語機能（`japanese_word.jsp` / `japanese_test.jsp` / `japanese_word_status.jsp`。
 * データは study3 DB）を 2.1 の 3 画面として作り直したもの。データは `JPN_*` テーブル
 * （2.0 から移行済み）。学習状況は**アカウントごと**に持つ。</p>
 */
public final class JapaneseModels {

    private JapaneseModels() {
    }

    /** テスト種別（2.0 の A〜E）。 */
    public static final List<String> TEST_TYPES = List.of("A", "B", "C", "D", "E");
    /** 学習状態。 */
    public static final List<String> LEARN_STATES = List.of("NOT_STARTED", "LEARNING", "REVIEW", "MASTERED");
    /** 出題方式。 */
    public static final List<String> TEST_MODES = List.of("ALL", "RANDOM");
    /** テストの状態。 */
    public static final List<String> TEST_STATES = List.of("CREATED", "RUNNING", "COMPLETED");

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;
    /** 1 回のテストで出題できる最大数。 */
    public static final int MAX_QUESTIONS = 50;

    // ---------------------------------------------------------------- 単語

    /** 単語情報管理の 1 行（収録と学習状況をまとめて返す）。 */
    public record WordRow(
            long wordId,
            String word,
            String reading,
            String jlptLevel,
            String partOfSpeech,
            String stateCode,
            String note,
            int version,
            /** 収録（教材のどこに載っているか） */
            String book,
            String category,
            String level,
            Integer wordSeq,
            long collectionCount,
            /** 学習状況 */
            String learnState,
            BigDecimal mastery,
            int answeredCount,
            int correctCount,
            boolean favorite,
            boolean learned,
            String lastStudiedAt,
            String nextReviewAt) {
    }

    /** 単語の収録（1 語が複数の教材に載ることがある）。 */
    public record CollectionRow(
            long collectionId,
            String level,
            String book,
            String category,
            int wordSeq,
            String listedWord,
            String listedReading,
            String listedPartOfSpeech,
            String chineseMeaning) {
    }

    /** 単語に紐づく問題（種類と数）。 */
    public record QuestionRow(
            long questionId,
            String questionType,
            int questionNo,
            String questionText,
            String correctValue,
            int choiceCount) {
    }

    /** 単語の詳細（2.0 の 6 つの子テーブルを 1 つの JSON にまとめたもの）。 */
    public record WordDetailView(
            long detailId,
            int contentVersion,
            String aiProvider,
            String aiModel,
            String fetchedAt,
            /** senses / examples / pronunciations / collocations / relatedWords / cautions */
            Map<String, Object> detail) {
    }

    public record WordListResult(
            List<WordRow> items,
            long totalElements,
            int page,
            int size,
            int totalPages,
            WordTotals totals) {
    }

    public record WordTotals(
            long wordCount,
            long learnedCount,
            long favoriteCount,
            BigDecimal averageMastery,
            long answeredCount) {
    }

    public record WordDetailResult(
            WordRow word,
            List<CollectionRow> collections,
            List<QuestionRow> questions,
            WordDetailView detail) {
    }

    // ------------------------------------------------------------ テスト

    /** 単語テストの 1 回分。 */
    public record TestRow(
            long testId,
            String testNo,
            String testType,
            String level,
            String book,
            String categoryFrom,
            String categoryTo,
            String difficulty,
            String mode,
            int questionCount,
            int doneCount,
            int correctCount,
            int wrongCount,
            String state,
            String startedAt,
            String finishedAt,
            String lastStudiedAt,
            long activeMs,
            /** 正答率（％） */
            int scorePercent,
            int version) {
    }

    /** テストの 1 問（出題＋問題＋選択肢）。 */
    public record TestQuestionRow(
            long entryId,
            int orderNo,
            String state,
            String judgment,
            int answerCount,
            int wrongCount,
            long activeMs,
            String answeredAt,
            Long questionId,
            long wordId,
            String word,
            String reading,
            String questionType,
            String questionText,
            String correctValue,
            String explanation,
            String book,
            String category) {
    }

    /** 選択肢。 */
    public record ChoiceRow(
            long choiceId,
            int orderNo,
            String value,
            String reading,
            boolean correct,
            String description) {
    }

    /** テスト 1 問＋選択肢。 */
    public record TestQuestionView(TestQuestionRow question, List<ChoiceRow> choices) {
    }

    public record TestListResult(
            List<TestRow> items,
            long totalElements,
            int page,
            int size,
            int totalPages,
            TestTotals totals) {
    }

    public record TestTotals(
            long testCount,
            long completedCount,
            long runningCount,
            /** 完了したテストの平均正答率（％） */
            int averageScore,
            long totalActiveMs) {
    }

    public record TestDetailResult(TestRow test, List<TestQuestionView> questions) {
    }

    // ------------------------------------------------------ 勉強状況

    /** 語ごとの学習状況。 */
    public record StatusRow(
            long wordId,
            String word,
            String reading,
            String jlptLevel,
            String partOfSpeech,
            String book,
            String category,
            String learnState,
            BigDecimal mastery,
            boolean learned,
            boolean favorite,
            int answeredCount,
            int correctCount,
            int wrongCount,
            int streak,
            int bestStreak,
            long activeMs,
            String lastTestType,
            String lastJudgment,
            String firstStudiedAt,
            String lastStudiedAt,
            String nextReviewAt,
            int reviewIntervalDays) {
    }

    /** 日次の学習量。 */
    public record DailyRow(
            String studyDate,
            long activeMs,
            long typeAMs,
            long typeBMs,
            long typeCMs,
            long typeDMs,
            long typeEMs,
            int wordCount,
            int testCount,
            int doneCount,
            int correctCount,
            int wrongCount) {
    }

    /** 技能（テスト種別 × 技能区分）ごとの習得。 */
    public record SkillRow(
            long wordId,
            String word,
            String reading,
            String testType,
            String skillCode,
            String learnState,
            BigDecimal mastery,
            int answeredCount,
            int correctCount,
            int wrongCount,
            int streak,
            int bestStreak,
            String lastJudgment,
            String lastStudiedAt,
            String nextReviewAt) {
    }

    /** 勉強状況のサマリ。 */
    public record StatusSummary(
            long studiedWordCount,
            long learnedCount,
            long favoriteCount,
            BigDecimal averageMastery,
            long answeredCount,
            long correctCount,
            /** 正答率（％） */
            int accuracyPercent,
            long activeMs,
            long todayActiveMs,
            String lastStudiedAt) {
    }

    public record StatusResult(
            StatusSummary summary,
            List<DailyRow> daily,
            List<StatusRow> items,
            long totalElements,
            int page,
            int size,
            int totalPages) {
    }

    public record SkillListResult(List<SkillRow> items, long totalElements, int page, int size, int totalPages) {
    }

    // -------------------------------------------------------- リクエスト

    /** 単語の登録・修正。 */
    public record WordSaveRequest(
            @NotBlank(message = "見出し語を入力してください。")
            @Size(max = 300, message = "見出し語は300文字以内で入力してください。") String word,
            @Size(max = 300, message = "読みは300文字以内で入力してください。") String reading,
            @Size(max = 10, message = "JLPTレベルの指定が正しくありません。") String jlptLevel,
            @Size(max = 300, message = "品詞は300文字以内で入力してください。") String partOfSpeech,
            @Size(max = 20, message = "状態の指定が正しくありません。") String stateCode,
            String note,
            Integer version) {
    }

    public record FavoriteRequest(boolean favorite) {
    }

    public record LearnedRequest(boolean learned) {
    }

    /** テストの作成。 */
    public record TestCreateRequest(
            @NotBlank(message = "テスト種別を指定してください。") String testType,
            String level,
            String book,
            String categoryFrom,
            String categoryTo,
            String difficulty,
            String mode,
            @Min(value = 1, message = "出題数は1以上で指定してください。") Integer questionCount) {
    }

    /** 回答（出題順で指定する）。 */
    public record AnswerRequest(
            @Min(value = 1, message = "出題順を指定してください。") int orderNo,
            Long choiceId,
            String answerText,
            Long elapsedMs) {
    }

    // ------------------------------------------------------------ 返信

    /** 回答の判定結果。 */
    public record AnswerResult(
            boolean correct,
            String judgment,
            String correctValue,
            String explanation,
            /** 更新後のテスト（進捗） */
            TestRow test,
            String message) {
    }

    public record WordMutationResult(WordRow word, String message) {
    }

    public record TestMutationResult(TestRow test, String message) {
    }

    public record SimpleResult(int count, String message) {
    }
}
