package com.study21.user.japanese;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.user.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 日本語勉強の実装（2.0 の日本語機能を 2.1 の 3 画面向けに作り直したもの）。
 *
 * <p>2.0 から引き継いだ規則:</p>
 * <ul>
 *   <li>テスト種別は A〜E。C は読み・漢字、D は文脈の意味、E は漢字の使い方。
 *       A・B は 2.0 に問題テーブルが無く、単語（見出し語・読み）から出題する。</li>
 *   <li>単語の習得度は**技能（テスト種別 × 技能区分）ごと**に持ち、
 *       学習状況の 総合習得度 はその平均。平均 80 以上で MASTERED、
 *       直前が誤答なら REVIEW、それ以外は LEARNING（2.0 と同じ）。</li>
 *   <li>テストの進捗（完了数・正解数・不正解数・学習時間）はテストと出題の両方に記録する。</li>
 * </ul>
 *
 * <p>復習間隔は 2.1 の規則（正解なら間隔を 2 倍に延ばし、誤答なら 1 日に戻す）で、
 * 習得度は 1 問 ±20 の増減。2.0 の詳細な間隔表は移行していない（設計書に記載）。</p>
 */
@Service
public class JapaneseServiceImpl implements JapaneseService {

    private static final Logger log = LoggerFactory.getLogger(JapaneseServiceImpl.class);

    private static final DateTimeFormatter TEST_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int MAX_REVIEW_INTERVAL_DAYS = 30;
    private static final BigDecimal MASTERY_STEP = BigDecimal.valueOf(20);
    private static final BigDecimal MASTERED_THRESHOLD = BigDecimal.valueOf(80);

    /** テスト種別 → 出題する問題種別（2.0 の問題テーブルに合わせる）。 */
    private static final Map<String, List<String>> QUESTION_TYPES = Map.of(
            "C", List.of("C1_READING", "C2_KANJI"),
            "D", List.of("D_CONTEXT_MEANING"),
            "E", List.of("E_KANJI_USAGE"));

    /** 問題種別 → 技能区分（2.0 の技能習得情報に合わせる）。 */
    private static final Map<String, String> SKILL_CODES = Map.of(
            "C1_READING", "C_READING_RECOGNITION",
            "C2_KANJI", "C_KANJI_RECOGNITION",
            "D_CONTEXT_MEANING", "D_CONTEXT_MEANING",
            "E_KANJI_USAGE", "E_KANJI_USAGE",
            "A_READING", "A_READING_RECALL",
            "B_ORTHOGRAPHY", "B_ORTHOGRAPHY",
            "B_READING_RECALL", "B_READING_RECALL");

    private final JpnWordMapper wordMapper;
    private final JpnTestMapper testMapper;
    private final JpnStatusMapper statusMapper;
    private final ObjectMapper objectMapper;

    public JapaneseServiceImpl(JpnWordMapper wordMapper, JpnTestMapper testMapper,
                               JpnStatusMapper statusMapper, ObjectMapper objectMapper) {
        this.wordMapper = wordMapper;
        this.testMapper = testMapper;
        this.statusMapper = statusMapper;
        this.objectMapper = objectMapper;
    }

    // ================================================================ 単語

    @Override
    @Transactional(readOnly = true)
    public JapaneseModels.WordListResult searchWords(long accountId, String keyword, String reading, String jlpt,
                                                     String part, String state, String book, String category,
                                                     String learnState, int page, int size) {
        int safeSize = size <= 0 ? JapaneseModels.DEFAULT_SIZE : Math.min(size, JapaneseModels.MAX_SIZE);
        int safePage = Math.max(1, page);
        String learnStateFilter = normalizeChoice(learnState, JapaneseModels.LEARN_STATES, "学習状態");
        String stateFilter = normalizeChoice(state, List.of("ACTIVE", "INACTIVE"), "状態");

        long total = wordMapper.count(blankToNull(keyword), blankToNull(reading), blankToNull(jlpt),
                blankToNull(part), stateFilter, blankToNull(book), blankToNull(category), learnStateFilter, accountId);
        List<JapaneseModels.WordRow> items = wordMapper.search(blankToNull(keyword), blankToNull(reading),
                        blankToNull(jlpt), blankToNull(part), stateFilter, blankToNull(book), blankToNull(category),
                        learnStateFilter, accountId, safeSize, (safePage - 1) * safeSize)
                .stream()
                .map(JapaneseServiceImpl::toWordRow)
                .toList();
        JpnTotalsEntity totals = wordMapper.totals(accountId);
        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new JapaneseModels.WordListResult(items, total, safePage, safeSize, totalPages,
                new JapaneseModels.WordTotals(totals.words(), totals.learned(), totals.favorites(),
                        totals.mastery(), totals.answered()));
    }

    @Override
    @Transactional(readOnly = true)
    public JapaneseModels.WordDetailResult wordDetail(long accountId, long wordId) {
        JpnWordEntity word = requireWord(accountId, wordId);
        List<JapaneseModels.CollectionRow> collections = wordMapper.listCollections(wordId).stream()
                .map(entity -> new JapaneseModels.CollectionRow(entity.getCollectionId(), entity.getLevel(),
                        entity.getBook(), entity.getCategory(), entity.getWordSeq() == null ? 0 : entity.getWordSeq(),
                        entity.getListedWord(), entity.getListedReading(), entity.getListedPartOfSpeech(),
                        entity.getChineseMeaning()))
                .toList();
        List<JapaneseModels.QuestionRow> questions = wordMapper.listQuestions(wordId).stream()
                .map(entity -> new JapaneseModels.QuestionRow(entity.getQuestionId(), entity.getQuestionType(),
                        entity.getQuestionNo() == null ? 1 : entity.getQuestionNo(), entity.getQuestionTextJa(),
                        entity.getCorrectValue(), entity.getChoiceCount() == null ? 0 : entity.getChoiceCount()))
                .toList();
        JpnWordDetailEntity detail = wordMapper.findDetail(wordId);
        return new JapaneseModels.WordDetailResult(toWordRow(word), collections, questions, toDetailView(detail));
    }

    @Override
    @Transactional
    public JapaneseModels.WordMutationResult createWord(UserPrincipal user,
                                                        JapaneseModels.WordSaveRequest request) {
        String word = request.word().trim();
        String reading = trimToEmpty(request.reading());
        JpnWordEntity existing = wordMapper.findByWordAndReading(word, reading);
        if (existing != null) {
            throw new ConflictException("同じ見出し語と読みの単語がすでに登録されています。");
        }
        JpnWordEntity entity = new JpnWordEntity();
        entity.setWord(word);
        entity.setReading(reading);
        entity.setWordKey(word);
        entity.setReadingKey(reading);
        entity.setJlptLevel(blankToNull(request.jlptLevel()));
        entity.setPartOfSpeech(blankToNull(request.partOfSpeech()));
        entity.setStateCode(choiceOrDefault(request.stateCode(), List.of("ACTIVE", "INACTIVE"), "状態", "ACTIVE"));
        entity.setNote(blankToNull(request.note()));
        entity.setCreatedBy(user.accountId());
        wordMapper.insert(entity);
        return new JapaneseModels.WordMutationResult(
                toWordRow(requireWord(user.accountId(), entity.getWordId())), "単語を登録しました。");
    }

    @Override
    @Transactional
    public JapaneseModels.WordMutationResult updateWord(UserPrincipal user, long wordId,
                                                        JapaneseModels.WordSaveRequest request) {
        JpnWordEntity current = requireWord(user.accountId(), wordId);
        String word = request.word().trim();
        String reading = trimToEmpty(request.reading());
        JpnWordEntity duplicate = wordMapper.findByWordAndReading(word, reading);
        if (duplicate != null && !Objects.equals(duplicate.getWordId(), wordId)) {
            throw new ConflictException("同じ見出し語と読みの単語がすでに登録されています。");
        }
        JpnWordEntity entity = new JpnWordEntity();
        entity.setWordId(wordId);
        entity.setWord(word);
        entity.setReading(reading);
        entity.setWordKey(word);
        entity.setReadingKey(reading);
        entity.setJlptLevel(blankToNull(request.jlptLevel()));
        entity.setPartOfSpeech(blankToNull(request.partOfSpeech()));
        entity.setStateCode(choiceOrDefault(request.stateCode(), List.of("ACTIVE", "INACTIVE"), "状態",
                current.getStateCode()));
        entity.setNote(blankToNull(request.note()));
        entity.setUpdatedBy(user.accountId());
        entity.setVersion(request.version() == null ? current.getVersion() : request.version());
        if (wordMapper.update(entity) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        return new JapaneseModels.WordMutationResult(
                toWordRow(requireWord(user.accountId(), wordId)), "単語を更新しました。");
    }

    @Override
    @Transactional
    public JapaneseModels.SimpleResult deleteWord(UserPrincipal user, long wordId) {
        requireWord(user.accountId(), wordId);
        wordMapper.delete(wordId);
        return new JapaneseModels.SimpleResult(1, "単語を削除しました。");
    }

    @Override
    @Transactional
    public JapaneseModels.WordMutationResult setFavorite(UserPrincipal user, long wordId, boolean favorite) {
        requireWord(user.accountId(), wordId);
        statusMapper.insertStatusIfAbsent(user.accountId(), wordId);
        statusMapper.updateFavorite(user.accountId(), wordId, favorite, Timestamp.valueOf(LocalDateTime.now()));
        return new JapaneseModels.WordMutationResult(toWordRow(requireWord(user.accountId(), wordId)),
                favorite ? "お気に入りに追加しました。" : "お気に入りから外しました。");
    }

    @Override
    @Transactional
    public JapaneseModels.WordMutationResult setLearned(UserPrincipal user, long wordId, boolean learned) {
        requireWord(user.accountId(), wordId);
        statusMapper.insertStatusIfAbsent(user.accountId(), wordId);
        statusMapper.updateLearned(user.accountId(), wordId, learned, Timestamp.valueOf(LocalDateTime.now()));
        return new JapaneseModels.WordMutationResult(toWordRow(requireWord(user.accountId(), wordId)),
                learned ? "習得済にしました。" : "習得済を解除しました。");
    }

    // ============================================================== テスト

    @Override
    @Transactional(readOnly = true)
    public JapaneseModels.TestListResult searchTests(long accountId, String state, String testType,
                                                     int page, int size) {
        int safeSize = size <= 0 ? JapaneseModels.DEFAULT_SIZE : Math.min(size, JapaneseModels.MAX_SIZE);
        int safePage = Math.max(1, page);
        String stateFilter = normalizeChoice(state, JapaneseModels.TEST_STATES, "テストの状態");
        String typeFilter = normalizeChoice(testType, JapaneseModels.TEST_TYPES, "テスト種別");

        long total = testMapper.count(accountId, stateFilter, typeFilter);
        List<JapaneseModels.TestRow> items = testMapper.search(accountId, stateFilter, typeFilter,
                        safeSize, (safePage - 1) * safeSize)
                .stream()
                .map(JapaneseServiceImpl::toTestRow)
                .toList();
        JpnTotalsEntity totals = testMapper.totals(accountId);
        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new JapaneseModels.TestListResult(items, total, safePage, safeSize, totalPages,
                new JapaneseModels.TestTotals(totals.tests(), totals.completed(), totals.running(),
                        totals.averageScoreValue(), totals.totalActive()));
    }

    @Override
    @Transactional(readOnly = true)
    public JapaneseModels.TestDetailResult testDetail(long accountId, long testId) {
        JpnTestEntity test = requireTest(accountId, testId);
        return new JapaneseModels.TestDetailResult(toTestRow(test), buildQuestionViews(testId));
    }

    @Override
    @Transactional
    public JapaneseModels.TestDetailResult createTest(UserPrincipal user, JapaneseModels.TestCreateRequest request) {
        String testType = normalizeChoice(request.testType(), JapaneseModels.TEST_TYPES, "テスト種別");
        if (testType == null) {
            throw new ValidationException("テスト種別を指定してください。");
        }
        int count = Math.max(1, Math.min(request.questionCount() == null ? 10 : request.questionCount(),
                JapaneseModels.MAX_QUESTIONS));
        String mode = choiceOrDefault(request.mode(), JapaneseModels.TEST_MODES, "出題方式", "ALL");
        String difficulty = choiceOrDefault(request.difficulty(),
                List.of("EASY", "NORMAL", "HARD"), "難易度", "NORMAL");
        boolean random = "RANDOM".equals(mode);

        JpnTestEntity test = new JpnTestEntity();
        test.setTestNo(nextTestNo());
        test.setAccountId(user.accountId());
        test.setTestType(testType);
        test.setLevel(blankToNull(request.level()));
        test.setBook(blankToNull(request.book()));
        test.setCategoryFrom(blankToNull(request.categoryFrom()));
        test.setCategoryTo(blankToNull(request.categoryTo()));
        test.setDifficulty(difficulty);
        test.setMode(mode);
        test.setQuestionCount(count);
        test.setStartedAt(Timestamp.valueOf(LocalDateTime.now()));
        testMapper.insert(test);

        List<JpnTestQuestionEntity> entries = "A".equals(testType) || "B".equals(testType)
                ? buildWordEntries(test, testType)
                : buildQuestionEntries(test, testType, difficulty);
        if (entries.isEmpty()) {
            throw new ValidationException("条件に合う問題がありません。レベルや分類の指定を変えてください。");
        }
        for (JpnTestQuestionEntity entry : entries) {
            testMapper.insertEntry(entry);
        }
        // 実際に出題できた数で上書きする（条件に合う問題が少ないとき）
        testMapper.updateQuestionCount(test.getTestId(), entries.size());
        return new JapaneseModels.TestDetailResult(toTestRow(testMapper.findById(test.getTestId())),
                buildQuestionViews(test.getTestId()));
    }

    /** C・D・E: 問題テーブルから条件に合う問題を選ぶ。 */
    private List<JpnTestQuestionEntity> buildQuestionEntries(JpnTestEntity test, String testType, String difficulty) {
        List<JpnQuestionEntity> questions = testMapper.pickQuestions(testType, test.getLevel(), test.getBook(),
                test.getCategoryFrom(), test.getCategoryTo(), difficulty, "RANDOM".equals(test.getMode()),
                test.getAccountId(), test.getQuestionCount());
        List<JpnTestQuestionEntity> entries = new ArrayList<>();
        int order = 1;
        for (JpnQuestionEntity question : questions) {
            JpnTestQuestionEntity entry = new JpnTestQuestionEntity();
            entry.setTestId(test.getTestId());
            entry.setWordId(question.getWordId());
            entry.setQuestionId(question.getQuestionId());
            entry.setOrderNo(order);
            entry.setAccountId(test.getAccountId());
            entries.add(entry);
            order += 1;
        }
        return entries;
    }

    /**
     * A・B: 2.0 に問題テーブルが無いので、単語（見出し語・読み）から出題を作る。
     * A は「見出し語 → 読み」、B は「読み → 見出し語」。誤答の選択肢は同じ条件の別の単語から取る。
     */
    private List<JpnTestQuestionEntity> buildWordEntries(JpnTestEntity test, String testType) {
        // 出題数 + 誤答用の予備をまとめて引き、先頭から出題・残りを選択肢に使う
        int needed = Math.min(test.getQuestionCount() * 4, JapaneseModels.MAX_SIZE);
        List<JpnWordEntity> words = wordMapper.search(null, null, null, null, "ACTIVE", test.getBook(),
                null, null, test.getAccountId(), needed, 0);
        List<JpnWordEntity> pool = new ArrayList<>(words.stream()
                .filter(word -> word.getReading() != null && !word.getReading().isBlank())
                .toList());
        if (pool.size() < 4) {
            throw new ValidationException("選択肢を作るための単語が足りません（同じ条件の単語が4語以上必要です）。");
        }
        Collections.shuffle(pool);
        List<JpnTestQuestionEntity> entries = new ArrayList<>();
        int order = 1;
        for (JpnWordEntity word : pool) {
            if (order > test.getQuestionCount()) {
                break;
            }
            boolean askReading = "A".equals(testType);
            String questionType = askReading ? "A_READING" : "B_ORTHOGRAPHY";
            String correctValue = askReading ? word.getReading() : word.getWord();
            List<String> distractors = pool.stream()
                    .filter(other -> !Objects.equals(other.getWordId(), word.getWordId()))
                    .map(other -> askReading ? other.getReading() : other.getWord())
                    .filter(value -> value != null && !value.equals(correctValue))
                    .distinct()
                    .limit(3)
                    .toList();
            if (distractors.size() < 3) {
                continue;
            }
            List<String> choices = new ArrayList<>(distractors);
            choices.add(correctValue);
            Collections.shuffle(choices);

            JpnTestQuestionEntity entry = new JpnTestQuestionEntity();
            entry.setTestId(test.getTestId());
            entry.setWordId(word.getWordId());
            entry.setOrderNo(order);
            entry.setAccountId(test.getAccountId());
            entry.setSnapshotJson(wordSnapshot(questionType, word, correctValue, choices));
            entries.add(entry);
            order += 1;
        }
        return entries;
    }

    /** A・B の出題内容（問題文・正解・選択肢）を 1 つの JSON にして出題行に持たせる。 */
    private String wordSnapshot(String questionType, JpnWordEntity word, String correctValue, List<String> choices) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("questionType", questionType);
        snapshot.put("questionText", "A_READING".equals(questionType)
                ? "「" + word.getWord() + "」の読みを選んでください。"
                : "「" + word.getReading() + "」の漢字表記を選んでください。");
        snapshot.put("correctValue", correctValue);
        snapshot.put("word", word.getWord());
        snapshot.put("reading", word.getReading());
        snapshot.put("choices", choices);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception cause) {
            throw new IllegalStateException("出題内容を作れませんでした。", cause);
        }
    }

    @Override
    @Transactional
    public JapaneseModels.AnswerResult answer(UserPrincipal user, long testId,
                                              JapaneseModels.AnswerRequest request) {
        JpnTestEntity test = requireTest(user.accountId(), testId);
        JpnTestQuestionEntity entry = testMapper.findEntry(testId, request.orderNo());
        if (entry == null) {
            throw new NotFoundException("出題が見つかりません。");
        }
        if ("ANSWERED".equals(entry.getEntryState())) {
            throw new ConflictException("この問題はすでに回答済みです。");
        }
        if ("COMPLETED".equals(test.getStateCode())) {
            throw new ConflictException("このテストは完了しています。");
        }

        // 問題が未確定（2.0 から移行した出題、または A・B の出題）はここで内容を決める
        Map<String, Object> snapshot = parseSnapshot(entry.getSnapshotJson());
        JpnQuestionEntity question = entry.getQuestionId() == null && snapshot.isEmpty()
                ? resolveQuestion(test, entry)
                : null;
        Long questionId = entry.getQuestionId() != null
                ? entry.getQuestionId()
                : (question == null ? null : question.getQuestionId());

        AnswerJudgement judgement = judge(test, entry, question, snapshot, request);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        long elapsed = request.elapsedMs() == null ? 0L : Math.max(0L, request.elapsedMs());

        int answerCount = (entry.getAnswerCount() == null ? 0 : entry.getAnswerCount()) + 1;
        int wrongCount = (entry.getWrongCount() == null ? 0 : entry.getWrongCount()) + (judgement.correct() ? 0 : 1);
        testMapper.updateEntryAnswer(entry.getEntryId(), questionId, "ANSWERED", judgement.judgment(),
                answerCount, wrongCount, elapsed, now);

        // テストの進捗
        List<JpnTestQuestionEntity> entries = testMapper.listEntries(testId);
        int done = (int) entries.stream().filter(item -> "ANSWERED".equals(item.getEntryState())).count();
        int correct = (int) entries.stream()
                .filter(item -> "CORRECT".equals(item.getJudgment())).count();
        int wrong = done - correct;
        String state = done >= entries.size() ? "COMPLETED" : "RUNNING";
        long activeMs = (test.getActiveMs() == null ? 0L : test.getActiveMs()) + elapsed;
        testMapper.updateProgress(testId, done, correct, wrong, state, now, activeMs);
        if ("COMPLETED".equals(state)) {
            testMapper.updateState(testId, "COMPLETED", test.getStartedAt(), now);
        }

        // 学習状況・技能習得・日次（技能区分はテスト種別 × 問題種別から決める）
        String skillCode = SKILL_CODES.getOrDefault(judgement.questionType(), test.getTestType() + "_SKILL");
        updateLearning(user.accountId(), entry.getWordId(), test.getTestType(), skillCode,
                judgement.judgment(), judgement.correct(), elapsed, now);

        JpnTestEntity updated = testMapper.findById(testId);
        return new JapaneseModels.AnswerResult(judgement.correct(), judgement.judgment(),
                judgement.correctValue(), judgement.explanation(), toTestRow(updated),
                judgement.correct() ? "正解です。" : "不正解です。");
    }

    /**
     * 回答の判定。選択肢のある出題（C/D/E）は選択肢で、A・B と 2.0 から移行した出題は
     * 正解値との一致で判定する。正解値と解説は出題行（問題を結合済み）から取る。
     */
    private AnswerJudgement judge(JpnTestEntity test, JpnTestQuestionEntity entry, JpnQuestionEntity resolved,
                                  Map<String, Object> snapshot, JapaneseModels.AnswerRequest request) {
        String correctValue = entry.getCorrectValue() != null
                ? entry.getCorrectValue()
                : (resolved != null ? resolved.getCorrectValue()
                        : (snapshot.get("correctValue") == null ? null : String.valueOf(snapshot.get("correctValue"))));
        String explanation = entry.getExplanationJa() != null
                ? entry.getExplanationJa()
                : (resolved == null ? null : resolved.getExplanationJa());
        String questionType = entry.getQuestionType() != null
                ? entry.getQuestionType()
                : (resolved != null ? resolved.getQuestionType()
                        : String.valueOf(snapshot.getOrDefault("questionType", test.getTestType())));

        boolean correct;
        if (entry.getQuestionId() != null) {
            List<JpnChoiceEntity> choices = testMapper.listChoices(entry.getQuestionId());
            JpnChoiceEntity selected = choices.stream()
                    .filter(choice -> Objects.equals(choice.getChoiceId(), request.choiceId()))
                    .findFirst()
                    .orElse(null);
            if (selected == null && request.answerText() != null && !request.answerText().isBlank()) {
                selected = choices.stream()
                        .filter(choice -> choice.getValue() != null
                                && choice.getValue().equals(request.answerText().trim()))
                        .findFirst()
                        .orElse(null);
            }
            if (selected == null) {
                throw new ValidationException("選択肢を指定してください。");
            }
            correct = Boolean.TRUE.equals(selected.getCorrect())
                    || Objects.equals(selected.getValue(), correctValue);
        } else {
            if (correctValue == null) {
                throw new ValidationException("この出題には正解が登録されていません。");
            }
            String answer = request.answerText() == null ? null : request.answerText().trim();
            if ((answer == null || answer.isEmpty()) && request.choiceId() != null) {
                answer = choiceValueFromSnapshot(snapshot, request.choiceId());
            }
            if (answer == null || answer.isEmpty()) {
                throw new ValidationException("回答を指定してください。");
            }
            correct = answer.equals(correctValue);
        }
        return new AnswerJudgement(correct, correct ? "CORRECT" : "INCORRECT", correctValue, explanation,
                questionType);
    }

    /** A・B の出題は選択肢 ID を持たないので、選択肢の並び順（1 から）で受け取る。 */
    private String choiceValueFromSnapshot(Map<String, Object> snapshot, Long choiceId) {
        Object choices = snapshot.get("choices");
        if (!(choices instanceof List<?> list)) {
            return null;
        }
        int index = choiceId.intValue() - 1;
        if (index < 0 || index >= list.size()) {
            return null;
        }
        Object value = list.get(index);
        return value == null ? null : String.valueOf(value);
    }

    /** 2.0 から移行した出題で問題が未確定のとき、その語のテスト種別に合う問題を 1 つ選ぶ。 */
    private JpnQuestionEntity resolveQuestion(JpnTestEntity test, JpnTestQuestionEntity entry) {
        List<String> types = QUESTION_TYPES.get(test.getTestType());
        if (types == null) {
            return null;
        }
        return testMapper.findQuestionByWordAndTypes(entry.getWordId(), types);
    }

    /** 回答 1 件ぶんの学習状況・技能習得・日次の更新。 */
    private void updateLearning(long accountId, long wordId, String testType, String skillCode,
                                String judgment, boolean correct, long elapsedMs, Timestamp now) {
        int reviewInterval = nextReviewInterval(accountId, wordId, testType, skillCode, correct);
        Timestamp nextReviewAt = Timestamp.valueOf(now.toLocalDateTime().plusDays(reviewInterval));

        statusMapper.insertSkillIfAbsent(accountId, wordId, testType, skillCode);
        BigDecimal skillMastery = nextMastery(statusMapper.averageSkillMastery(accountId, wordId), correct);
        statusMapper.updateSkillAfterAnswer(accountId, wordId, testType, skillCode,
                correct ? "LEARNING" : "REVIEW", skillMastery, correct, judgment, nextReviewAt, elapsedMs, now);

        statusMapper.insertStatusIfAbsent(accountId, wordId);
        statusMapper.updateStatusAfterAnswer(accountId, wordId, correct ? "LEARNING" : "REVIEW",
                skillMastery, correct, false, nextReviewAt, reviewInterval, testType, judgment, elapsedMs, now);
        // 総合習得度と学習状態は技能の平均から作り直す（2.0 と同じ規則）
        statusMapper.refreshStatusFromSkills(accountId, wordId, judgment, "A".equals(testType) ? 1 : 0);
        statusMapper.upsertDaily(accountId, LocalDate.now(), testType, elapsedMs, correct);
    }

    /** 習得度の増減（正解 +20 / 誤答 -20。0〜100 に収める）。 */
    private static BigDecimal nextMastery(BigDecimal current, boolean correct) {
        BigDecimal base = current == null ? BigDecimal.ZERO : current;
        BigDecimal next = correct ? base.add(MASTERY_STEP) : base.subtract(MASTERY_STEP);
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return next.compareTo(BigDecimal.valueOf(100)) > 0 ? BigDecimal.valueOf(100) : next;
    }

    /** 復習間隔（正解なら倍に延ばす。誤答なら 1 日に戻す）。 */
    private int nextReviewInterval(long accountId, long wordId, String testType, String skillCode, boolean correct) {
        if (!correct) {
            return 1;
        }
        JpnSkillEntity current = statusMapper.findSkill(accountId, wordId, testType, skillCode);
        if (current == null || current.getNextReviewAt() == null) {
            return 3;
        }
        // 前回の復習予定が先なら間隔を延ばす（最大 30 日）
        long days = java.time.Duration.between(
                current.getNextReviewAt().toLocalDateTime(), LocalDateTime.now()).toDays();
        return (int) Math.max(1, Math.min(MAX_REVIEW_INTERVAL_DAYS, Math.max(3, days + 3)));
    }

    @Override
    @Transactional
    public JapaneseModels.TestMutationResult completeTest(UserPrincipal user, long testId) {
        JpnTestEntity test = requireTest(user.accountId(), testId);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        testMapper.updateState(testId, "COMPLETED", test.getStartedAt(), now);
        return new JapaneseModels.TestMutationResult(toTestRow(testMapper.findById(testId)), "テストを完了にしました。");
    }

    @Override
    @Transactional
    public JapaneseModels.SimpleResult deleteTest(UserPrincipal user, long testId) {
        requireTest(user.accountId(), testId);
        testMapper.delete(testId);
        return new JapaneseModels.SimpleResult(1, "テストを削除しました。");
    }

    // ========================================================== 勉強状況

    @Override
    @Transactional(readOnly = true)
    public JapaneseModels.StatusResult status(long accountId, String learnState, String jlpt, int page, int size) {
        int safeSize = size <= 0 ? JapaneseModels.DEFAULT_SIZE : Math.min(size, JapaneseModels.MAX_SIZE);
        int safePage = Math.max(1, page);
        String learnStateFilter = normalizeChoice(learnState, JapaneseModels.LEARN_STATES, "学習状態");

        JpnTotalsEntity totals = statusMapper.summary(accountId, LocalDate.now());
        List<JapaneseModels.DailyRow> daily = statusMapper.listDaily(accountId, 31).stream()
                .map(entity -> new JapaneseModels.DailyRow(
                        entity.getStudyDate() == null ? null : entity.getStudyDate().toString(),
                        value(entity.getActiveMs()), value(entity.getTypeAMs()), value(entity.getTypeBMs()),
                        value(entity.getTypeCMs()), value(entity.getTypeDMs()), value(entity.getTypeEMs()),
                        intValue(entity.getWordCount()), intValue(entity.getTestCount()),
                        intValue(entity.getDoneCount()), intValue(entity.getCorrectCount()),
                        intValue(entity.getWrongCount())))
                .toList();
        long total = statusMapper.countStatuses(accountId, learnStateFilter, blankToNull(jlpt));
        List<JapaneseModels.StatusRow> items = statusMapper.listStatuses(accountId, learnStateFilter,
                        blankToNull(jlpt), safeSize, (safePage - 1) * safeSize)
                .stream()
                .map(JapaneseServiceImpl::toStatusRow)
                .toList();
        int totalPages = (int) Math.ceil((double) total / safeSize);
        long answered = totals.answered();
        int accuracy = answered == 0 ? 0 : (int) Math.round(totals.correct() * 100.0 / answered);
        JapaneseModels.StatusSummary summary = new JapaneseModels.StatusSummary(totals.words(), totals.learned(),
                totals.favorites(), totals.mastery(), answered, totals.correct(), accuracy, totals.active(),
                totals.todayActive(),
                totals.getLastStudiedAt() == null ? null : totals.getLastStudiedAt().toLocalDateTime().toString());
        return new JapaneseModels.StatusResult(summary, daily, items, total, safePage, safeSize, totalPages);
    }

    @Override
    @Transactional(readOnly = true)
    public JapaneseModels.SkillListResult skills(long accountId, String testType, String skill, int page, int size) {
        int safeSize = size <= 0 ? JapaneseModels.DEFAULT_SIZE : Math.min(size, JapaneseModels.MAX_SIZE);
        int safePage = Math.max(1, page);
        String typeFilter = normalizeChoice(testType, JapaneseModels.TEST_TYPES, "テスト種別");
        long total = statusMapper.countSkills(accountId, typeFilter, blankToNull(skill));
        List<JapaneseModels.SkillRow> items = statusMapper.listSkills(accountId, typeFilter, blankToNull(skill),
                        safeSize, (safePage - 1) * safeSize)
                .stream()
                .map(JapaneseServiceImpl::toSkillRow)
                .toList();
        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new JapaneseModels.SkillListResult(items, total, safePage, safeSize, totalPages);
    }

    // ------------------------------------------------------------ 出題の組み立て

    /** 出題一覧（問題と選択肢つき）。2.0 の移行データは出題のスナップショットから作る。 */
    private List<JapaneseModels.TestQuestionView> buildQuestionViews(long testId) {
        List<JpnTestQuestionEntity> entries = testMapper.listEntries(testId);
        List<Long> questionIds = entries.stream()
                .map(JpnTestQuestionEntity::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, List<JpnChoiceEntity>> choicesByQuestion = questionIds.isEmpty()
                ? Map.of()
                : testMapper.listChoicesByQuestionIds(questionIds).stream()
                        .collect(Collectors.groupingBy(JpnChoiceEntity::getQuestionId,
                                LinkedHashMap::new, Collectors.toList()));

        List<JapaneseModels.TestQuestionView> views = new ArrayList<>();
        for (JpnTestQuestionEntity entry : entries) {
            Map<String, Object> snapshot = parseSnapshot(entry.getSnapshotJson());
            List<JapaneseModels.ChoiceRow> choices = new ArrayList<>();
            if (entry.getQuestionId() != null) {
                List<JpnChoiceEntity> rows = choicesByQuestion.getOrDefault(entry.getQuestionId(), List.of());
                for (JpnChoiceEntity choice : rows) {
                    choices.add(new JapaneseModels.ChoiceRow(choice.getChoiceId(),
                            choice.getOrderNo() == null ? 0 : choice.getOrderNo(), choice.getValue(),
                            choice.getReading(), Boolean.TRUE.equals(choice.getCorrect()),
                            choice.getDescriptionJa()));
                }
            } else if (snapshot.get("choices") instanceof List<?> values) {
                int order = 1;
                for (Object value : values) {
                    String text = value == null ? "" : String.valueOf(value);
                    boolean correct = text.equals(String.valueOf(snapshot.get("correctValue")));
                    choices.add(new JapaneseModels.ChoiceRow(order, order, text, null, correct, null));
                    order += 1;
                }
            }
            JapaneseModels.TestQuestionRow question = new JapaneseModels.TestQuestionRow(
                    entry.getEntryId(), entry.getOrderNo() == null ? 0 : entry.getOrderNo(),
                    entry.getEntryState(), entry.getJudgment(), intValue(entry.getAnswerCount()),
                    intValue(entry.getWrongCount()), value(entry.getActiveMs()),
                    iso(entry.getAnsweredAt()), entry.getQuestionId(), entry.getWordId(), entry.getWord(),
                    entry.getReading(),
                    entry.getQuestionType() == null
                            ? (snapshot.get("questionType") == null ? null : String.valueOf(snapshot.get("questionType")))
                            : entry.getQuestionType(),
                    entry.getQuestionTextJa() == null
                            ? (snapshot.get("questionText") == null ? null : String.valueOf(snapshot.get("questionText")))
                            : entry.getQuestionTextJa(),
                    entry.getCorrectValue() == null
                            ? (snapshot.get("correctValue") == null ? null : String.valueOf(snapshot.get("correctValue")))
                            : entry.getCorrectValue(),
                    entry.getExplanationJa(), entry.getBook(), entry.getCategory());
            views.add(new JapaneseModels.TestQuestionView(question, choices));
        }
        return views;
    }

    private Map<String, Object> parseSnapshot(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception cause) {
            log.warn("出題のスナップショットを読めませんでした（先頭 60 文字）: {}",
                    json.substring(0, Math.min(60, json.length())));
            return Map.of();
        }
    }

    // -------------------------------------------------------------------- 内部

    private JpnWordEntity requireWord(long accountId, long wordId) {
        JpnWordEntity word = wordMapper.findById(wordId, accountId);
        if (word == null) {
            throw new NotFoundException("単語が見つかりません。");
        }
        return word;
    }

    private JpnTestEntity requireTest(long accountId, long testId) {
        JpnTestEntity test = testMapper.findById(testId);
        if (test == null || test.getAccountId() == null || test.getAccountId() != accountId) {
            throw new NotFoundException("テストが見つかりません。");
        }
        return test;
    }

    private String nextTestNo() {
        String base = "JT-" + LocalDateTime.now().format(TEST_NO_FORMAT);
        String candidate = base;
        int suffix = 1;
        while (testMapper.findByNo(candidate) != null) {
            suffix += 1;
            candidate = base + "-" + suffix;
        }
        return candidate;
    }

    private static String normalizeChoice(String value, List<String> allowed, String label) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        if (!allowed.contains(text)) {
            throw new ValidationException(label + "は " + String.join(" / ", allowed) + " のいずれかを指定してください。");
        }
        return text;
    }

    private static String choiceOrDefault(String value, List<String> allowed, String label, String fallback) {
        String normalized = normalizeChoice(value, allowed, label);
        return normalized == null ? fallback : normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static long value(Long number) {
        return number == null ? 0L : number;
    }

    private static int intValue(Integer number) {
        return number == null ? 0 : number;
    }

    private static String iso(Timestamp value) {
        return value == null ? null : value.toLocalDateTime().toString();
    }

    private static String iso(LocalDate value) {
        return value == null ? null : value.toString();
    }

    /** 出題のスナップショット（JSON 文字列）を画面用のオブジェクトに変換する。 */
    private JapaneseModels.WordDetailView toDetailView(JpnWordDetailEntity entity) {
        if (entity == null) {
            return null;
        }
        Map<String, Object> detail = Map.of();
        if (entity.getDetailJson() != null && !entity.getDetailJson().isBlank()) {
            try {
                detail = objectMapper.readValue(entity.getDetailJson(), new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception cause) {
                log.warn("単語の詳細 JSON を読めませんでした。wordId={}", entity.getWordId());
            }
        }
        return new JapaneseModels.WordDetailView(entity.getDetailId() == null ? 0L : entity.getDetailId(),
                entity.getContentVersion() == null ? 1 : entity.getContentVersion(), entity.getAiProvider(),
                entity.getAiModel(), iso(entity.getFetchedAt()), detail);
    }

    private static JapaneseModels.WordRow toWordRow(JpnWordEntity entity) {
        return new JapaneseModels.WordRow(
                entity.getWordId() == null ? 0L : entity.getWordId(),
                entity.getWord(), entity.getReading(), entity.getJlptLevel(), entity.getPartOfSpeech(),
                entity.getStateCode(), entity.getNote(), entity.getVersion() == null ? 1 : entity.getVersion(),
                entity.getBook(), entity.getCategory(), entity.getLevel(), entity.getWordSeq(),
                entity.getCollectionCount() == null ? 0L : entity.getCollectionCount(),
                entity.getLearnState() == null ? "NOT_STARTED" : entity.getLearnState(),
                entity.getMastery() == null ? BigDecimal.ZERO : entity.getMastery(),
                intValue(entity.getAnsweredCount()), intValue(entity.getCorrectCount()),
                Boolean.TRUE.equals(entity.getFavorite()), Boolean.TRUE.equals(entity.getLearned()),
                iso(entity.getLastStudiedAt()), iso(entity.getNextReviewAt()));
    }

    private static JapaneseModels.TestRow toTestRow(JpnTestEntity entity) {
        int done = intValue(entity.getDoneCount());
        int correct = intValue(entity.getCorrectCount());
        int score = done == 0 ? 0 : (int) Math.round(correct * 100.0 / done);
        return new JapaneseModels.TestRow(
                entity.getTestId() == null ? 0L : entity.getTestId(), entity.getTestNo(), entity.getTestType(),
                entity.getLevel(), entity.getBook(), entity.getCategoryFrom(), entity.getCategoryTo(),
                entity.getDifficulty(), entity.getMode(), intValue(entity.getQuestionCount()), done, correct,
                intValue(entity.getWrongCount()), entity.getStateCode(), iso(entity.getStartedAt()),
                iso(entity.getFinishedAt()), iso(entity.getLastStudiedAt()), value(entity.getActiveMs()),
                score, entity.getVersion() == null ? 1 : entity.getVersion());
    }

    private static JapaneseModels.StatusRow toStatusRow(JpnStatusEntity entity) {
        return new JapaneseModels.StatusRow(
                entity.getWordId() == null ? 0L : entity.getWordId(), entity.getWord(), entity.getReading(),
                entity.getJlptLevel(), entity.getPartOfSpeech(), entity.getBook(), entity.getCategory(),
                entity.getLearnState() == null ? "NOT_STARTED" : entity.getLearnState(),
                entity.getMastery() == null ? BigDecimal.ZERO : entity.getMastery(),
                Boolean.TRUE.equals(entity.getLearned()), Boolean.TRUE.equals(entity.getFavorite()),
                intValue(entity.getAnsweredCount()), intValue(entity.getCorrectCount()),
                intValue(entity.getWrongCount()), intValue(entity.getStreak()), intValue(entity.getBestStreak()),
                value(entity.getActiveMs()), entity.getLastTestType(), entity.getLastJudgment(),
                iso(entity.getFirstStudiedAt()), iso(entity.getLastStudiedAt()), iso(entity.getNextReviewAt()),
                intValue(entity.getReviewIntervalDays()));
    }

    private static JapaneseModels.SkillRow toSkillRow(JpnSkillEntity entity) {
        return new JapaneseModels.SkillRow(
                entity.getWordId() == null ? 0L : entity.getWordId(), entity.getWord(), entity.getReading(),
                entity.getTestType(), entity.getSkillCode(),
                entity.getLearnState() == null ? "NOT_STARTED" : entity.getLearnState(),
                entity.getMastery() == null ? BigDecimal.ZERO : entity.getMastery(),
                intValue(entity.getAnsweredCount()), intValue(entity.getCorrectCount()),
                intValue(entity.getWrongCount()), intValue(entity.getStreak()), intValue(entity.getBestStreak()),
                entity.getLastJudgment(), iso(entity.getLastStudiedAt()), iso(entity.getNextReviewAt()));
    }

    /** 出題の判定（内部用）。 */
    private record AnswerJudgement(boolean correct, String judgment, String correctValue, String explanation,
                                   String questionType) {
    }
}
