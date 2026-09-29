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
    public static final int MAX_QUESTIONS = 100;

    // ---------------------------------------------------------------- 単語

    /**
     * 一覧の「取得状態」列に出す AI 取得の状態（`JPN_AI生成履歴情報` の最新行）。
     *
     * <p>一覧の列は 4 つの区画（A・B＝詳細／C＝読み問題／D＝文脈問題／E＝漢字問題）なので、
     * 内容種別コード 5 つを 4 つにまとめて返す。まだ実行していない区画は null（＝未取得）。
     * 値は {@code QUEUED} / {@code RUNNING} / {@code SUCCEEDED} / {@code FAILED} / {@code CANCELED}。</p>
     */
    public record WordAiState(
            /** A・B（{@code A_DETAIL}） */
            String detail,
            /** C（{@code C1_READING} / {@code C2_KANJI}。どちらか成功していれば成功） */
            String reading,
            /** D（{@code D_CONTEXT_MEANING}） */
            String context,
            /** E（{@code E_KANJI_USAGE}） */
            String kanji) {
    }

    /**
     * 有効版の詳細にある段落の件数（一覧の「詳細情報件数」列）。
     *
     * <p>2.0 の英語学習（`word.jsp`）は「語義 2・例文 3…」のように段落ごとの件数を出していた。
     * 2.0 は詳細を 1 つの JSON に持っていたので配列の長さを数えていたが、2.1 は段落を子テーブルに
     * 分けて持つので**行数**を数える（同じ意味）。有効版が無い語は {@code null}（画面は「—」）。</p>
     */
    public record WordDetailCounts(
            /** 語義 */
            int senses,
            /** 例文 */
            int examples,
            /** 文型 */
            int patterns,
            /** 会話（発言は数えない） */
            int dialogs,
            /** 類義語 */
            int synonyms,
            /** 注意（間違えやすいポイント） */
            int cautions,
            /** コロケーション */
            int collocations,
            /** 関連語 */
            int relatedWords,
            /** 使用場面 */
            int usageNotes,
            /** ミニ練習 */
            int practices) {
    }

    /**
     * 問題（C/D/E）の 1 版（一覧の「取得状態」から開く履歴の 1 行）。
     *
     * <p>2.0 の英語学習（`word.jsp`）の「詳細情報取得履歴」と同じで、**AI の取得 1 回 = 1 行**。
     * {@code questionCount} が 0 の版（失敗した取得）は切り替えられない。</p>
     */
    public record WordQuestionVersion(
            /** C1_READING / C2_KANJI / D_CONTEXT_MEANING / E_KANJI_USAGE */
            String questionType,
            /** この取得の版（1 から）。 */
            int contentVersion,
            /** その版の問題数（0 = 失敗した取得）。 */
            int questionCount,
            /** 今その版を使っているか。 */
            boolean active,
            /** 生成の状態（RUNNING / SUCCEEDED / FAILED）。 */
            String generationState,
            String aiProvider,
            String aiModel,
            int generatedCount,
            int failedCount,
            String errorMessage,
            String startedAt,
            String finishedAt) {
    }

    /** 問題の版の一覧（内容種別ごとに新しい順）。 */
    public record WordQuestionVersionList(List<WordQuestionVersion> items, int totalCount) {
    }

    /** 単語情報管理の 1 行（収録と学習状況をまとめて返す）。 */
    public record WordRow(
            long wordId,
            String word,
            String reading,
            String jlptLevel,
            String partOfSpeech,
            /**
             * 中国語訳（一覧の列）。単語情報には列が無いので**有効版の詳細の最初の語義の中国語**を
             * 検索のときに引く（詳細がまだ無ければ null＝画面は「—」）。
             */
            String chineseMeaning,
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
            String nextReviewAt,
            /** AI 取得の状態（一覧の「取得状態」列） */
            WordAiState aiState,
            /**
             * 有効版の詳細の段落の件数（一覧の「詳細情報件数」列）。
             * 詳細がまだ無い語は {@code null}（画面は「—」）。
             */
            WordDetailCounts detailCounts) {
    }

    /**
     * AI 取得の対象（**検索条件に一致する語**のうち、今回受付ける分）。
     *
     * <p>画面の窓（AI 取得）が出す数字と、受理 API へ送る語の一覧。一覧と同じ絞り込み・
     * 同じ並びなので、「表示順の先頭から N 語」になる。</p>
     *
     * @param total      検索条件に一致する語の数
     * @param acquired   そのうち取得済み（SUCCEEDED）の数
     * @param candidates この選択（スキップ／すべて再取得）で対象になりうる語の数
     * @param wordIds    今回受付ける語（{@code limit} 件まで）
     * @param overLimit  対象のうち今回に収まらない数（次回に回す）
     * @param limit      この回の上限（設定ページの「1 回の最大単語数」）
     */
    public record AiTargets(long total, long acquired, long candidates, java.util.List<Long> wordIds,
                            long overLimit, int limit) {
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
            /**
             * この版の {@code バージョン}（楽観ロック）。
             *
             * <p>「この版を使う」ときに画面がそのまま送り返す値。版ごとに違うので、
             * 版の一覧（{@link DetailVersionRow#version()}）と合わせて使う。</p>
             */
            int version,
            String aiProvider,
            String aiModel,
            String fetchedAt,
            /** senses / examples / pronunciations / collocations / relatedWords / cautions */
            Map<String, Object> detail) {
    }

    /**
     * 詳細の版 1 つの要約（画面の「版の履歴」1 行）。
     *
     * <p>{@code counts} は各段落の行数（どの版が内容が多いかを画面で見せる）。数えるのは
     * 段落の親テーブル 10 個で、会話の発言（{@code dialogLines}）は会話に含まれるので別に数えない。</p>
     */
    public record DetailVersionRow(
            long detailId,
            int contentVersion,
            /**
             * この版の {@code バージョン}（楽観ロック）。
             *
             * <p>【この版を使う】は<b>その版の</b>この値を送る。表示中の詳細の
             * {@code contentVersion} ではない（版ごとに別の値なので、取り違えると必ず 409）。</p>
             */
            int version,
            String stateCode,
            /** 今 有効な版か（{@code 状態コード='ACTIVE'}）。 */
            boolean active,
            /** 人が手を入れた版か（版のヘッダか段落の 手修正フラグ）。 */
            boolean manual,
            String aiProvider,
            String aiModel,
            Long generationId,
            String fetchedAt,
            String note,
            String createdAt,
            String updatedAt,
            DetailVersionCounts counts) {
    }

    /** 版に属する段落の行数（11 のうち、会話の発言を除く 10）。 */
    public record DetailVersionCounts(
            int senses,
            int examples,
            int patterns,
            int dialogs,
            int synonyms,
            int cautions,
            int collocations,
            int relatedWords,
            int usageNotes,
            int practices) {
    }

    /** 版の一覧（新しい順）。 */
    public record WordDetailVersions(List<DetailVersionRow> items) {
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

    /**
     * 選択肢 1 件（**この出題で実際に見せたもの**）。
     *
     * <p>切片6 で「テスト作成時にプールから選んで固定した 4 択」になった。画面と回答が使うのは
     * この行で、{@code プール}（{@code JPN_単語問題選択肢情報}）は読まない。</p>
     *
     * <ul>
     *   <li>{@code choiceId} … プールの選択肢ID（C/D/E）。**A・B はプールを持たないので null**</li>
     *   <li>{@code choiceKey} … 画面が回答に使う値（＝{@code choiceId}）。null のときは
     *       表示順（{@code orderNo}）で答える（A・B と、出題選択肢JSON を持たない古い出題）</li>
     * </ul>
     */
    public record ChoiceRow(
            Long choiceId,
            int orderNo,
            String value,
            String reading,
            boolean correct,
            String description,
            /** 画面が回答に送る値（choiceId、無ければ表示順）。 */
            Long choiceKey) {
    }

    /** テスト 1 問＋選択肢。 */
    public record TestQuestionView(TestQuestionRow question, List<ChoiceRow> choices, Map<String, Object> snapshot, List<Map<String, Object>> history) {
        public TestQuestionView(TestQuestionRow question, List<ChoiceRow> choices) { this(question, choices, Map.of(), List.of()); }
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

    /** 基本情報と詳細を一括保存する。未取得の詳細の版数は 0。 */
    public record WordEditorRequest(
            @jakarta.validation.Valid @jakarta.validation.constraints.NotNull WordSaveRequest word,
            @jakarta.validation.constraints.NotNull @Min(0) Integer contentVersion,
            @jakarta.validation.constraints.NotNull Map<String, Object> detail) {
    }

    public record LearnedRequest(boolean learned) {
    }

    /**
     * 有効にする版の指定（楽観的ロック）。
     *
     * <p>{@code version} はその版の {@code バージョン}（画面が読んだときの値）。ほかの操作が
     * 先に更新していれば一致せず、409 になる。</p>
     */
    public record ActivateVersionRequest(
            @jakarta.validation.constraints.NotNull(message = "版番号を指定してください。")
            @Min(value = 1, message = "版番号の指定が正しくありません。") Integer version) {
    }

    /**
     * 使用する問題の版の指定（問題は版ごとに楽観的ロックを持たないので、版番号だけ）。
     *
     * <p>その版に問題が無ければ（失敗した取得の版）400 になる。</p>
     */
    public record ActivateQuestionVersionRequest(
            @jakarta.validation.constraints.NotNull(message = "版番号を指定してください。")
            @Min(value = 1, message = "版番号の指定が正しくありません。") Integer contentVersion) {
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
            @Min(value = 0, message = "数量は0（全部）以上で指定してください。") @jakarta.validation.constraints.Max(100) Integer questionCount) {
    }

    /** 回答（出題順で指定する）。 */
    public record AnswerRequest(
            @Min(value = 1, message = "出題順を指定してください。") int orderNo,
            /**
             * 選んだ選択肢。値の意味は出題の作り方で変わる:
             * C/D/E はプールの {@code 選択肢ID}、A・B は 1 からの表示順
             * （画面はどちらも {@code ChoiceRow.choiceKey} をそのまま送る）。
             */
            Long choiceId,
            String answerText,
            Long elapsedMs, String readingText) {
        public AnswerRequest(int orderNo, Long choiceId, String answerText, Long elapsedMs) {
            this(orderNo, choiceId, answerText, elapsedMs, null);
        }
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
            String message, boolean answered) {
        public AnswerResult(boolean correct, String judgment, String correctValue, String explanation, TestRow test, String message) {
            this(correct, judgment, correctValue, explanation, test, message, true);
        }
    }

    public record WordMutationResult(WordRow word, String message) {
    }

    public record TestMutationResult(TestRow test, String message) {
    }

    public record SimpleResult(int count, String message) {
    }

    /**
     * 新規登録画面の 1 語（**見出し語と、教材のどこに載っているか**）。
     *
     * <p>語だけでは一覧の書籍・分類が空になるので、収録（書籍・分類・SEQ）を必ず一緒に渡す。
     * 既に母表にある語は {@code wordId} を入れて再利用する（同じ語を二重に作らない）。</p>
     */
    public record RegisterWord(
            /** 既存の語を使うときの ID（新規なら null）。 */
            Long wordId,
            @NotBlank(message = "見出し語を入力してください。")
            @Size(max = 300, message = "見出し語は300文字以内で入力してください。") String word,
            @Size(max = 300, message = "読みは300文字以内で入力してください。") String reading,
            /** 教材のレベル（2.0 の 収録.レベル。全件 'N1-N5'）。 */
            @Size(max = 20, message = "レベルの指定が正しくありません。") String level,
            @Size(max = 100, message = "書籍名は100文字以内で入力してください。") String book,
            @Size(max = 30, message = "分類は30文字以内で入力してください。") String category,
            /**
             * 収録の位置（1 以上）。省略したとき、または既にその位置が埋まっているときは
             * サーバー側が「その Unit の次の番号」を入れる（画面の割り当ては表示中のページから
             * 推すので、重なることがあるため）。
             */
            @Min(value = 1, message = "単語SEQは1以上で入力してください。") Integer wordSeq,
            @Size(max = 300, message = "掲載見出し語は300文字以内で入力してください。") String listedWord,
            @Size(max = 100, message = "掲載品詞は100文字以内で入力してください。") String listedPartOfSpeech,
            String listedChineseMeaning) {
    }

    /** 新規登録画面の保存（語と収録をまとめて）。 */
    public record RegisterRequest(
            List<RegisterWord> words) {
    }

    /** 登録の結果（作った語・収録の件数と、飛ばした語）。 */
    public record RegisterResult(int wordCount, int collectionCount, int skippedCount,
                                 List<String> skipped, String message) {
    }
}
