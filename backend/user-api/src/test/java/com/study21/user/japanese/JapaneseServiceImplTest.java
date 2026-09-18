package com.study21.user.japanese;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日本語勉強の業務ルール（単語の登録・修正、テストの作成と回答、勉強状況の集計）。
 *
 * 2.0 の規則を引き継いでいる:
 * ・テスト種別は A〜E（C は読み・漢字、D は文脈の意味、E は漢字の使い方）
 * ・A・B は問題テーブルが無いので単語（見出し語・読み）から出題する
 * ・総合習得度 = 技能の平均習得度。平均 80 以上で MASTERED、誤答なら REVIEW
 */
class JapaneseServiceImplTest {

    private static final long ACCOUNT_ID = 2L;
    private static final long WORD_ID = 4913L;

    private JpnWordMapper wordMapper;
    private JpnTestMapper testMapper;
    private JpnStatusMapper statusMapper;
    private JapaneseServiceImpl service;

    @BeforeEach
    void setUp() {
        wordMapper = mock(JpnWordMapper.class);
        testMapper = mock(JpnTestMapper.class);
        statusMapper = mock(JpnStatusMapper.class);
        service = new JapaneseServiceImpl(wordMapper, testMapper, statusMapper, new ObjectMapper());

        // MyBatis は採番した主キーを書き戻す（useGeneratedKeys）
        doAnswer(invocation -> {
            JpnWordEntity entity = invocation.getArgument(0);
            entity.setWordId(WORD_ID);
            return 1;
        }).when(wordMapper).insert(any());
        doAnswer(invocation -> {
            JpnTestEntity entity = invocation.getArgument(0);
            entity.setTestId(77L);
            return 1;
        }).when(testMapper).insert(any());
        doAnswer(invocation -> {
            JpnTestQuestionEntity entity = invocation.getArgument(0);
            entity.setEntryId(500L + entity.getOrderNo());
            return 1;
        }).when(testMapper).insertEntry(any());
    }

    private UserPrincipal student() {
        return new UserPrincipal(ACCOUNT_ID, "ricky.jingze@gmail.com", "試験 生徒", AccountType.STUDENT);
    }

    private JpnWordEntity word(long wordId, String word, String reading) {
        JpnWordEntity entity = new JpnWordEntity();
        entity.setWordId(wordId);
        entity.setWord(word);
        entity.setReading(reading);
        entity.setWordKey(word);
        entity.setReadingKey(reading);
        entity.setStateCode("ACTIVE");
        entity.setVersion(1);
        entity.setLearnState("LEARNING");
        entity.setMastery(BigDecimal.valueOf(40));
        entity.setAnsweredCount(2);
        entity.setCorrectCount(1);
        entity.setCollectionCount(1L);
        entity.setBook("01.N1~N5日本語単語");
        entity.setCategory("Unit001");
        return entity;
    }

    private JpnTestEntity test(long testId, String testType, int questionCount, int done, int correct, int wrong) {
        JpnTestEntity entity = new JpnTestEntity();
        entity.setTestId(testId);
        entity.setTestNo("JT-20260913-210000");
        entity.setAccountId(ACCOUNT_ID);
        entity.setTestType(testType);
        entity.setDifficulty("NORMAL");
        entity.setMode("ALL");
        entity.setQuestionCount(questionCount);
        entity.setDoneCount(done);
        entity.setCorrectCount(correct);
        entity.setWrongCount(wrong);
        entity.setStateCode(done >= questionCount ? "COMPLETED" : "RUNNING");
        entity.setActiveMs(1000L);
        entity.setVersion(1);
        return entity;
    }

    private JpnTestQuestionEntity entry(long entryId, int orderNo, Long questionId, String state, String judgment) {
        JpnTestQuestionEntity entity = new JpnTestQuestionEntity();
        entity.setEntryId(entryId);
        entity.setTestId(77L);
        entity.setWordId(WORD_ID);
        entity.setQuestionId(questionId);
        entity.setOrderNo(orderNo);
        entity.setEntryState(state);
        entity.setJudgment(judgment);
        entity.setAnswerCount("ANSWERED".equals(state) ? 1 : 0);
        entity.setWrongCount("INCORRECT".equals(judgment) ? 1 : 0);
        entity.setWord("愛");
        entity.setReading("あい");
        entity.setSnapshotJson("{}");
        // findEntry は問題を結合して返すので、正解・解説・種別も入っている（本番と同じ形にする）
        if (questionId != null) {
            entity.setCorrectValue("あい");
            entity.setExplanationJa("「愛」は「あい」と読みます。");
            entity.setQuestionType("C1_READING");
        }
        return entity;
    }

    private JpnChoiceEntity choice(long choiceId, String value, boolean correct) {
        JpnChoiceEntity entity = new JpnChoiceEntity();
        entity.setChoiceId(choiceId);
        entity.setValue(value);
        entity.setCorrect(correct);
        entity.setOrderNo((int) choiceId);
        return entity;
    }

    // ---------------------------------------------------------- 単語

    @Test
    void createsWordWithNormalizedKeys() {
        when(wordMapper.findByWordAndReading("愛", "あい")).thenReturn(null);
        when(wordMapper.findById(WORD_ID, ACCOUNT_ID)).thenReturn(word(WORD_ID, "愛", "あい"));

        JapaneseModels.WordMutationResult result = service.createWord(student(),
                new JapaneseModels.WordSaveRequest("  愛 ", " あい ", "N3", "[名]", null, "メモ", null));

        ArgumentCaptor<JpnWordEntity> captor = ArgumentCaptor.forClass(JpnWordEntity.class);
        verify(wordMapper).insert(captor.capture());
        assertThat(captor.getValue().getWord()).isEqualTo("愛");
        assertThat(captor.getValue().getWordKey()).isEqualTo("愛");
        assertThat(captor.getValue().getReadingKey()).isEqualTo("あい");
        assertThat(captor.getValue().getStateCode()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(ACCOUNT_ID);
        assertThat(result.message()).contains("登録しました");
    }

    @Test
    void rejectsDuplicateWord() {
        when(wordMapper.findByWordAndReading("愛", "あい")).thenReturn(word(1L, "愛", "あい"));

        assertThatThrownBy(() -> service.createWord(student(),
                new JapaneseModels.WordSaveRequest("愛", "あい", null, null, null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateUsesOptimisticLock() {
        when(wordMapper.findById(WORD_ID, ACCOUNT_ID)).thenReturn(word(WORD_ID, "愛", "あい"));
        when(wordMapper.findByWordAndReading("愛", "あい")).thenReturn(word(WORD_ID, "愛", "あい"));
        when(wordMapper.update(any())).thenReturn(0);

        assertThatThrownBy(() -> service.updateWord(student(), WORD_ID,
                new JapaneseModels.WordSaveRequest("愛", "あい", null, null, null, null, 3)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("先に更新されました");
    }

    @Test
    void rejectsUnknownWord() {
        when(wordMapper.findById(999L, ACCOUNT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.wordDetail(ACCOUNT_ID, 999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void wordDetailReturnsCollectionsQuestionsAndDetail() {
        when(wordMapper.findById(WORD_ID, ACCOUNT_ID)).thenReturn(word(WORD_ID, "愛", "あい"));
        JpnCollectionEntity collection = new JpnCollectionEntity();
        collection.setCollectionId(1L);
        collection.setBook("01.N1~N5日本語単語");
        collection.setCategory("Unit001");
        collection.setLevel("N1-N5");
        collection.setWordSeq(13);
        when(wordMapper.listCollections(WORD_ID)).thenReturn(List.of(collection));
        JpnQuestionEntity question = new JpnQuestionEntity();
        question.setQuestionId(5L);
        question.setQuestionType("C1_READING");
        question.setQuestionTextJa("漢字を見て正しい読みを選んでください");
        question.setCorrectValue("あい");
        question.setChoiceCount(4);
        when(wordMapper.listQuestions(WORD_ID)).thenReturn(List.of(question));
        JpnWordDetailEntity detail = new JpnWordDetailEntity();
        detail.setDetailId(9L);
        detail.setContentVersion(1);
        detail.setDetailJson("{\"jlptLevel\":\"N3\",\"senses\":[{\"number\":1,\"japanese\":\"いつくしむ\"}]}");
        when(wordMapper.findDetail(WORD_ID)).thenReturn(detail);

        JapaneseModels.WordDetailResult result = service.wordDetail(ACCOUNT_ID, WORD_ID);

        assertThat(result.collections()).hasSize(1);
        assertThat(result.collections().get(0).chineseMeaning()).isNull();
        assertThat(result.questions()).hasSize(1);
        assertThat(result.questions().get(0).choiceCount()).isEqualTo(4);
        assertThat(result.detail().detail()).containsEntry("jlptLevel", "N3");
        assertThat(result.detail().detail()).containsKey("senses");
    }

    @Test
    void favoriteAndLearnedCreateStatusRowFirst() {
        when(wordMapper.findById(WORD_ID, ACCOUNT_ID)).thenReturn(word(WORD_ID, "愛", "あい"));

        service.setFavorite(student(), WORD_ID, true);
        service.setLearned(student(), WORD_ID, true);

        // お気に入りと習得済はどちらも先に学習状況の行を用意する
        verify(statusMapper, org.mockito.Mockito.times(2)).insertStatusIfAbsent(ACCOUNT_ID, WORD_ID);
        verify(statusMapper).updateFavorite(eq(ACCOUNT_ID), eq(WORD_ID), eq(true), any());
        verify(statusMapper).updateLearned(eq(ACCOUNT_ID), eq(WORD_ID), eq(true), any());
    }

    // -------------------------------------------------------- テスト

    @Test
    void searchTestsValidatesFiltersAndClampsPaging() {
        when(testMapper.count(ACCOUNT_ID, "COMPLETED", null)).thenReturn(2L);
        when(testMapper.search(eq(ACCOUNT_ID), eq("COMPLETED"), eq(null), anyInt(), anyInt()))
                .thenReturn(List.of(test(1L, "A", 10, 10, 8, 2)));
        when(testMapper.totals(ACCOUNT_ID)).thenReturn(new JpnTotalsEntity());

        JapaneseModels.TestListResult result = service.searchTests(ACCOUNT_ID, "COMPLETED", null, 0, 9999);

        assertThat(result.size()).isEqualTo(JapaneseModels.MAX_SIZE);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).scorePercent()).isEqualTo(80);

        assertThatThrownBy(() -> service.searchTests(ACCOUNT_ID, "DONE", null, 1, 20))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void createTestPicksQuestionsForTypeC() {
        when(testMapper.findByNo(anyString())).thenReturn(null);
        when(testMapper.findById(77L)).thenReturn(test(77L, "C", 2, 0, 0, 0));
        when(testMapper.pickQuestions(eq("C"), any(), any(), any(), any(), eq("NORMAL"), eq(false), eq(ACCOUNT_ID), eq(2)))
                .thenReturn(List.of(question(5L), question(6L)));
        when(testMapper.listEntries(77L)).thenReturn(List.of(entry(1L, 1, 5L, "PENDING", null),
                entry(2L, 2, 6L, "PENDING", null)));

        service.createTest(student(), new JapaneseModels.TestCreateRequest("C", null, null, null, null,
                "NORMAL", "ALL", 2));

        ArgumentCaptor<JpnTestQuestionEntity> captor = ArgumentCaptor.forClass(JpnTestQuestionEntity.class);
        verify(testMapper, org.mockito.Mockito.times(2)).insertEntry(captor.capture());
        assertThat(captor.getAllValues()).extracting(JpnTestQuestionEntity::getQuestionId)
                .containsExactly(5L, 6L);
        verify(testMapper).updateQuestionCount(77L, 2);
    }

    @Test
    void createTestForTypeABuildsQuestionsFromWords() {
        when(testMapper.findByNo(anyString())).thenReturn(null);
        when(testMapper.findById(77L)).thenReturn(test(77L, "A", 1, 0, 0, 0));
        when(wordMapper.search(any(), any(), any(), any(), any(), any(), any(), any(), eq(ACCOUNT_ID), anyInt(), anyInt()))
                .thenReturn(List.of(word(1L, "愛", "あい"), word(2L, "朝", "あさ"),
                        word(3L, "雨", "あめ"), word(4L, "犬", "いぬ")));
        when(testMapper.listEntries(77L)).thenReturn(List.of(entry(500L, 1, null, "PENDING", null)));

        service.createTest(student(), new JapaneseModels.TestCreateRequest("A", null, null, null, null,
                null, "ALL", 1));

        ArgumentCaptor<JpnTestQuestionEntity> captor = ArgumentCaptor.forClass(JpnTestQuestionEntity.class);
        verify(testMapper).insertEntry(captor.capture());
        String snapshot = captor.getValue().getSnapshotJson();
        assertThat(snapshot).contains("\"questionType\":\"A_READING\"");
        assertThat(snapshot).contains("\"choices\"");
        assertThat(captor.getValue().getQuestionId()).isNull();
    }

    @Test
    void answersChoiceQuestionAndUpdatesLearning() {
        when(testMapper.findById(77L)).thenReturn(test(77L, "C", 2, 0, 0, 0));
        when(testMapper.findEntry(77L, 1)).thenReturn(entry(501L, 1, 5L, "PENDING", null));
        when(testMapper.listChoices(5L)).thenReturn(List.of(choice(1L, "あい", true), choice(2L, "あお", false)));
        when(testMapper.listEntries(77L)).thenReturn(List.of(
                entry(501L, 1, 5L, "ANSWERED", "CORRECT"), entry(502L, 2, 6L, "PENDING", null)));
        when(statusMapper.findSkill(anyLong(), anyLong(), anyString(), anyString())).thenReturn(null);
        when(statusMapper.averageSkillMastery(ACCOUNT_ID, WORD_ID)).thenReturn(BigDecimal.valueOf(60));

        JapaneseModels.AnswerResult result = service.answer(student(), 77L,
                new JapaneseModels.AnswerRequest(1, 1L, null, 500L));

        assertThat(result.correct()).isTrue();
        assertThat(result.judgment()).isEqualTo("CORRECT");
        assertThat(result.correctValue()).isEqualTo("あい");
        // 出題の記録（判定・問題の確定・学習時間）
        verify(testMapper).updateEntryAnswer(eq(501L), eq(5L), eq("ANSWERED"), eq("CORRECT"),
                eq(1), eq(0), eq(500L), any());
        // テストの進捗（1 / 2 完了、正解 1）
        verify(testMapper).updateProgress(eq(77L), eq(1), eq(1), eq(0), eq("RUNNING"), any(), anyLong());
        // 技能と学習状況、日次
        verify(statusMapper).insertSkillIfAbsent(ACCOUNT_ID, WORD_ID, "C", "C_READING_RECOGNITION");
        verify(statusMapper).updateSkillAfterAnswer(eq(ACCOUNT_ID), eq(WORD_ID), eq("C"),
                eq("C_READING_RECOGNITION"), eq("LEARNING"), any(), eq(true), eq("CORRECT"), any(), eq(500L), any());
        verify(statusMapper).updateStatusAfterAnswer(eq(ACCOUNT_ID), eq(WORD_ID), eq("LEARNING"),
                any(), eq(true), eq(false), any(), eq(3), eq("C"), eq("CORRECT"), eq(500L), any());
        verify(statusMapper).refreshStatusFromSkills(eq(ACCOUNT_ID), eq(WORD_ID), eq("CORRECT"), eq(0));
        verify(statusMapper).upsertDaily(eq(ACCOUNT_ID), any(), eq("C"), eq(500L), eq(true));
    }

    @Test
    void answersQuestionWithoutChoiceTableUsingSnapshot() {
        // A・B の出題（問題テーブルを使わない）は 出題のスナップショットで判定する
        JpnTestEntity running = test(77L, "A", 1, 0, 0, 0);
        when(testMapper.findById(77L)).thenReturn(running);
        JpnTestQuestionEntity pending = entry(501L, 1, null, "PENDING", null);
        pending.setSnapshotJson("{\"questionType\":\"A_READING\",\"questionText\":\"「愛」の読みを選んでください。\","
                + "\"correctValue\":\"あい\",\"choices\":[\"あお\",\"あい\",\"あめ\",\"いぬ\"]}");
        when(testMapper.findEntry(77L, 1)).thenReturn(pending);
        when(testMapper.listEntries(77L)).thenReturn(List.of(entry(501L, 1, null, "ANSWERED", "CORRECT")));
        when(statusMapper.averageSkillMastery(ACCOUNT_ID, WORD_ID)).thenReturn(BigDecimal.valueOf(20));

        // 選択肢は ID ではなく並び順（2 番目 = あい）で答える
        JapaneseModels.AnswerResult result = service.answer(student(), 77L,
                new JapaneseModels.AnswerRequest(1, 2L, null, 100L));

        assertThat(result.correct()).isTrue();
        assertThat(result.correctValue()).isEqualTo("あい");
        verify(testMapper).updateProgress(eq(77L), eq(1), eq(1), eq(0), eq("COMPLETED"), any(), anyLong());
        verify(testMapper).updateState(eq(77L), eq("COMPLETED"), any(), any());
    }

    @Test
    void rejectsAnswerWithoutChoice() {
        when(testMapper.findById(77L)).thenReturn(test(77L, "C", 1, 0, 0, 0));
        when(testMapper.findEntry(77L, 1)).thenReturn(entry(501L, 1, 5L, "PENDING", null));
        when(testMapper.listChoices(5L)).thenReturn(List.of(choice(1L, "あい", true)));

        assertThatThrownBy(() -> service.answer(student(), 77L,
                new JapaneseModels.AnswerRequest(1, 999L, null, 100L)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("選択肢");
    }

    @Test
    void rejectsAnsweringTwice() {
        when(testMapper.findById(77L)).thenReturn(test(77L, "C", 2, 1, 1, 0));
        when(testMapper.findEntry(77L, 1)).thenReturn(entry(501L, 1, 5L, "ANSWERED", "CORRECT"));

        assertThatThrownBy(() -> service.answer(student(), 77L,
                new JapaneseModels.AnswerRequest(1, 1L, null, 100L)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void completesTest() {
        when(testMapper.findById(77L)).thenReturn(test(77L, "C", 2, 2, 2, 0));

        JapaneseModels.TestMutationResult result = service.completeTest(student(), 77L);

        verify(testMapper).updateState(eq(77L), eq("COMPLETED"), any(), any());
        assertThat(result.message()).contains("完了");
    }

    @Test
    void rejectsAnswerForAnotherAccount() {
        JpnTestEntity other = test(77L, "C", 2, 0, 0, 0);
        other.setAccountId(99L);
        when(testMapper.findById(77L)).thenReturn(other);

        assertThatThrownBy(() -> service.testDetail(ACCOUNT_ID, 77L))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------------------------------------------------- 勉強状況

    @Test
    void statusBuildsSummaryAndDaily() {
        JpnTotalsEntity totals = new JpnTotalsEntity();
        totals.setWordCount(213L);
        totals.setLearnedCount(0L);
        totals.setFavoriteCount(12L);
        totals.setAverageMastery(BigDecimal.valueOf(59.8));
        totals.setAnsweredCount(500L);
        totals.setCorrectCount(400L);
        totals.setActiveMs(3_600_000L);
        totals.setTodayActiveMs(60_000L);
        when(statusMapper.summary(eq(ACCOUNT_ID), any())).thenReturn(totals);
        JpnDailyEntity daily = new JpnDailyEntity();
        daily.setStudyDate(java.time.LocalDate.of(2026, 9, 12));
        daily.setActiveMs(120_000L);
        daily.setTypeAMs(30_000L);
        when(statusMapper.listDaily(ACCOUNT_ID, 31)).thenReturn(List.of(daily));
        when(statusMapper.countStatuses(ACCOUNT_ID, null, null)).thenReturn(1L);
        JpnStatusEntity status = new JpnStatusEntity();
        status.setWordId(WORD_ID);
        status.setWord("愛");
        status.setLearnState("LEARNING");
        status.setMastery(BigDecimal.valueOf(59.8));
        when(statusMapper.listStatuses(eq(ACCOUNT_ID), any(), any(), anyInt(), anyInt())).thenReturn(List.of(status));

        JapaneseModels.StatusResult result = service.status(ACCOUNT_ID, null, null, 1, 20);

        assertThat(result.summary().studiedWordCount()).isEqualTo(213);
        assertThat(result.summary().accuracyPercent()).isEqualTo(80);
        assertThat(result.summary().todayActiveMs()).isEqualTo(60_000L);
        assertThat(result.daily()).hasSize(1);
        assertThat(result.daily().get(0).typeAMs()).isEqualTo(30_000L);
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void skillsValidateTestType() {
        assertThatThrownBy(() -> service.skills(ACCOUNT_ID, "Z", null, 1, 20))
                .isInstanceOf(ValidationException.class);
        verify(statusMapper, never()).listSkills(anyLong(), any(), any(), anyInt(), anyInt());
    }

    private JpnQuestionEntity question(long questionId) {
        JpnQuestionEntity entity = new JpnQuestionEntity();
        entity.setQuestionId(questionId);
        entity.setWordId(WORD_ID);
        entity.setQuestionType("C1_READING");
        entity.setCorrectValue("あい");
        return entity;
    }
}
