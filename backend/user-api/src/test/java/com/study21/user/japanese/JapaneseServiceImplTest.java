package com.study21.user.japanese;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.common.core.japanese.JpnWordDetailChildren;
import com.study21.common.core.japanese.JpnWordDetailEntity;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InOrder;

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
    private JpnWordDetailMapper detailMapper;
    private JpnTestMapper testMapper;
    private JpnStatusMapper statusMapper;
    private JapaneseServiceImpl service;

    @BeforeEach
    void setUp() {
        wordMapper = mock(JpnWordMapper.class);
        detailMapper = mock(JpnWordDetailMapper.class);
        testMapper = mock(JpnTestMapper.class);
        statusMapper = mock(JpnStatusMapper.class);
        service = new JapaneseServiceImpl(wordMapper, detailMapper, testMapper, statusMapper, new ObjectMapper(),
                new java.util.Random(20260922L));

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
        // 詳細の版の INSERT は採番した 詳細ID を書き戻す（useGeneratedKeys）
        doAnswer(invocation -> {
            JpnWordDetailEntity header = invocation.getArgument(0);
            header.setDetailId(900L);
            return 1;
        }).when(detailMapper).insertDetailVersion(any());
    }

    @Test
    void studyConfirmationDoesNotRequireChoices() {
        when(testMapper.findById(77L)).thenReturn(test(77L, "A", 1, 0, 0, 0));
        JpnTestQuestionEntity pending = entry(501L, 1, null, "PENDING", null);
        pending.setSnapshotJson("{\"questionType\":\"A_STUDY\",\"word\":\"愛\",\"reading\":\"あい\"}");
        when(testMapper.findEntry(77L, 1)).thenReturn(pending);
        when(testMapper.listEntries(77L)).thenReturn(List.of(entry(501L, 1, null, "ANSWERED", "CONFIRMED")));
        var result = service.answer(student(), 77L, new JapaneseModels.AnswerRequest(1, null, "学習完了", 100L));
        assertThat(result.judgment()).isEqualTo("CONFIRMED");
        verify(testMapper).updateProgress(eq(77L), eq(1), eq(1), eq(0), eq("COMPLETED"), any(), anyLong());
    }

    @Test
    void inputTestKeepsFirstTwoWrongAttemptsPending() {
        when(testMapper.findById(77L)).thenReturn(test(77L, "B", 1, 0, 0, 0));
        JpnTestQuestionEntity pending = entry(501L, 1, null, "PENDING", null);
        pending.setSnapshotJson("{\"questionType\":\"B_INPUT\",\"word\":\"愛\",\"reading\":\"あい\"}");
        when(testMapper.findEntry(77L, 1)).thenReturn(pending);
        when(testMapper.listEntries(77L)).thenReturn(List.of(pending));
        var result = service.answer(student(), 77L, new JapaneseModels.AnswerRequest(1, null, "愛", 300L, "あお"));
        assertThat(result.judgment()).isEqualTo("MIXED");
        verify(testMapper).updateEntryAnswer(eq(501L), eq(null), eq("PENDING"), eq(null), eq(1), eq(1), eq(300L), eq(null));
        verify(testMapper).appendHistory(eq(501L), org.mockito.ArgumentMatchers.contains("あお"));
        verify(statusMapper, never()).insertStatusIfAbsent(anyLong(), anyLong());
    }

    @Test
    void inputTestAcceptsNormalizedKanaAndRecordsBothSkills() {
        when(testMapper.findById(77L)).thenReturn(test(77L, "B", 1, 0, 0, 0));
        JpnTestQuestionEntity pending = entry(501L, 1, null, "PENDING", null);
        pending.setSnapshotJson("{\"questionType\":\"B_INPUT\",\"word\":\"愛\",\"reading\":\"あい\"}");
        when(testMapper.findEntry(77L, 1)).thenReturn(pending);
        when(testMapper.listEntries(77L)).thenReturn(List.of(entry(501L, 1, null, "ANSWERED", "CORRECT")));
        var result = service.answer(student(), 77L, new JapaneseModels.AnswerRequest(1, null, "愛", 300L, "ｱｲ"));
        assertThat(result.correct()).isTrue();
        verify(statusMapper).insertSkillIfAbsent(ACCOUNT_ID, WORD_ID, "B", "B_READING_RECALL");
        verify(statusMapper).insertSkillIfAbsent(ACCOUNT_ID, WORD_ID, "B", "B_ORTHOGRAPHY");
    }

    @Test
    void snapshotRemainsStableAfterWordAndProblemChange() {
        when(testMapper.findById(77L)).thenReturn(test(77L, "C", 1, 0, 0, 0));
        JpnTestQuestionEntity pending = entry(501L, 1, 5L, "PENDING", null);
        pending.setSnapshotJson("{\"word\":\"経験\",\"reading\":\"けいけん\",\"questionText\":\"保存された問題\",\"correctValue\":\"けいけん\"}");
        pending.setChoicesJson(promptedChoicesJson());
        when(testMapper.listEntries(77L)).thenReturn(List.of(pending));
        var q = service.testDetail(ACCOUNT_ID, 77L).questions().getFirst().question();
        assertThat(q.word()).isEqualTo("経験");
        assertThat(q.correctValue()).isEqualTo("けいけん");
        assertThat(q.questionText()).isEqualTo("保存された問題");
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

    /**
     * 選択肢プール（正解 1 ＋ 誤答 5 ＝ 6 件。設計「2. 选项池」）。
     *
     * <p>正解は入力の読み「あい」＝ choiceId 4。誤答は 5 件あるので、テスト作成時に
     * 「正解 1 ＋ 誤答から 3」を選べる。</p>
     */
    private List<JpnChoiceEntity> pool() {
        return List.of(
                choice(1L, "えい", false),
                choice(2L, "あう", false),
                choice(3L, "い", false),
                choice(4L, "あい", true),
                choice(5L, "あえ", false),
                choice(6L, "あお", false));
    }

    /**
     * 出題行に固定した「今回提示した 4 択」（{@code 出題選択肢JSON} の値）。
     *
     * <p>正解（choiceId 4 の「あい」）を<b>3 番目</b>に置いた並び。プールの表示順とは違うので、
     * 「テスト作成時に選んで並べ替えた」ことが分かる。</p>
     */
    private static String promptedChoicesJson() {
        return "[{\"choiceId\":1,\"value\":\"えい\",\"reading\":\"えい\",\"correct\":false},"
                + "{\"choiceId\":6,\"value\":\"あお\",\"reading\":\"あお\",\"correct\":false},"
                + "{\"choiceId\":4,\"value\":\"あい\",\"reading\":\"あい\",\"correct\":true},"
                + "{\"choiceId\":3,\"value\":\"い\",\"reading\":\"い\",\"correct\":false}]";
    }

    /** 単語スナップショット（A・B の出題内容）から正解の値を読む。 */
    private static String correctValueOf(JpnTestQuestionEntity entity) {
        try {
            return new ObjectMapper().readTree(entity.getSnapshotJson()).path("correctValue").asText();
        } catch (Exception cause) {
            throw new IllegalStateException("単語スナップショットJSON を読めませんでした: "
                    + entity.getSnapshotJson(), cause);
        }
    }

    /** 出題行に入った 4 択を読む（insert された 出題選択肢JSON を Jackson で読み直す）。 */
    private static List<Map<String, Object>> promptedChoicesOf(JpnTestQuestionEntity entity) {
        try {
            return new ObjectMapper().readValue(entity.getChoicesJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                    });
        } catch (Exception cause) {
            throw new IllegalStateException("出題選択肢JSON を読めませんでした: " + entity.getChoicesJson(), cause);
        }
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

    // ---------------------------------------- 編集の保存（詳細の新しい版を作る）

    /** 語レベルの値をすべて埋めた有効版のヘッダ（段落は子テーブルから別に引く）。 */
    private static JpnWordDetailEntity editorHeader(int contentVersion) {
        JpnWordDetailEntity detail = detailHeader(contentVersion);
        detail.setCoreMeaning("大切に思う気持ち。");
        detail.setDescriptionJa("古い説明");
        detail.setDescriptionZh("旧的说明");
        detail.setPartOfSpeech("名詞");
        detail.setConjugation("なし");
        detail.setTransitivity("NONE");
        detail.setImportance(3);
        detail.setMemoryHint("ヒント");
        detail.setMemoryHintBasis("根拠");
        detail.setAiProvider("qwen");
        detail.setAiModel("qwen3.7-plus");
        detail.setStructuredJson("{\"raw\":true}");
        return detail;
    }

    /** AI が作った例文（出所 BATCH）。 */
    private static JpnWordDetailChildren.Example example(String japanese, String chinese) {
        JpnWordDetailChildren.Example row = new JpnWordDetailChildren.Example();
        row.setOrderNo(1);
        row.setJapanese(japanese);
        row.setChinese(chinese);
        row.setManualCorrected(false);
        row.setSourceCode("BATCH");
        return row;
    }

    /** 編集保存の共通の下ごしらえ（語・有効版・母表の更新）。 */
    private void givenEditorSave(int contentVersion) {
        when(wordMapper.findById(WORD_ID, ACCOUNT_ID)).thenReturn(word(WORD_ID, "愛", "あい"));
        when(detailMapper.findActiveDetail(WORD_ID)).thenReturn(editorHeader(contentVersion));
        when(wordMapper.update(any())).thenReturn(1);
        when(detailMapper.listSenses(9L)).thenReturn(List.of(sense()));
        when(detailMapper.listExamples(9L)).thenReturn(List.of(example("AI の例文1。", "AI 例句1。"),
                example("AI の例文2。", "AI 例句2。")));
    }

    private static JapaneseModels.WordEditorRequest editorRequest(Map<String, Object> detail) {
        return new JapaneseModels.WordEditorRequest(
                new JapaneseModels.WordSaveRequest("愛", "あい", "N3", "名詞", "ACTIVE", "備考", 1),
                4, detail);
    }

    @Test
    @DisplayName("編集の保存: 段落の行は「変わっていない AI の行＝BATCH・変えた行＝APP・消した行は入れない」")
    void editorMarksRowSourcesByContent() {
        givenEditorSave(4);
        // 画面は 例文1 をそのまま、例文2 を直して、語義も直して送ってきた（例文2 の位置は 1 つ目）
        Map<String, Object> editor = new java.util.LinkedHashMap<>();
        editor.put("descriptionJa", "新しい説明");
        editor.put("senses", List.of(Map.of("number", 1, "japanese", "直した語義。")));
        editor.put("examples", List.of(
                Map.of("japanese", "AI の例文1。", "chinese", "AI 例句1。"),
                Map.of("japanese", "人が直した例文。", "chinese", "人修改的例句。")));

        service.saveWordEditor(student(), WORD_ID, editorRequest(editor));

        // 1) 古い版を ARCHIVED にしてから 2) 新しい版を入れる（順番が逆だと部分 UNIQUE に当たる）
        InOrder order = inOrder(detailMapper);
        order.verify(detailMapper).archiveActiveDetail(WORD_ID, ACCOUNT_ID);
        order.verify(detailMapper).insertDetailVersion(any());

        ArgumentCaptor<JpnWordDetailEntity> header = ArgumentCaptor.forClass(JpnWordDetailEntity.class);
        verify(detailMapper).insertDetailVersion(header.capture());
        JpnWordDetailEntity created = header.getValue();
        assertThat(created.getWordId()).isEqualTo(WORD_ID);
        assertThat(created.getStateCode()).isEqualTo("ACTIVE");
        // 元詳細ID は基にした版（今の有効版）。生成ID は NULL＝AI ではなく人が作った版
        assertThat(created.getOriginDetailId()).isEqualTo(9L);
        assertThat(created.getGenerationId()).isNull();
        assertThat(created.getManualCorrected()).isTrue();
        assertThat(created.getCreatedBy()).isEqualTo(ACCOUNT_ID);
        // AI の版から引き継ぐ（どの応答から生まれたか）と、要求の値で置き換わる語レベルの値
        assertThat(created.getAiProvider()).isEqualTo("qwen");
        assertThat(created.getStructuredJson()).isEqualTo("{\"raw\":true}");
        assertThat(created.getDescriptionJa()).isEqualTo("新しい説明");
        // 要求に無いキーは元の版の値（画面が一部だけ送っても消えない）
        assertThat(created.getDescriptionZh()).isEqualTo("旧的说明");
        assertThat(created.getJlptLevel()).isEqualTo("N3");

        // 語義: 本文が変わったので人が作った行（APP・手修正フラグ true）
        ArgumentCaptor<List<JpnWordDetailChildren.Sense>> senses = ArgumentCaptor.forClass(List.class);
        verify(detailMapper).insertSenses(eq(900L), senses.capture());
        assertThat(senses.getValue()).hasSize(1);
        assertThat(senses.getValue().get(0).getSourceCode()).isEqualTo("APP");
        assertThat(senses.getValue().get(0).getManualCorrected()).isTrue();
        assertThat(senses.getValue().get(0).getOrderNo()).isEqualTo(1);

        // 例文: まったく同じ行は AI の出所を引き継ぎ、直した行だけ APP。表示順は 1 から振り直す
        ArgumentCaptor<List<JpnWordDetailChildren.Example>> examples = ArgumentCaptor.forClass(List.class);
        verify(detailMapper).insertExamples(eq(900L), examples.capture());
        assertThat(examples.getValue()).hasSize(2);
        assertThat(examples.getValue().get(0).getSourceCode()).isEqualTo("BATCH");
        assertThat(examples.getValue().get(0).getManualCorrected()).isFalse();
        assertThat(examples.getValue().get(1).getSourceCode()).isEqualTo("APP");
        assertThat(examples.getValue().get(1).getManualCorrected()).isTrue();
        assertThat(examples.getValue().get(1).getJapanese()).isEqualTo("人が直した例文。");
        assertThat(examples.getValue()).extracting(JpnWordDetailChildren.Example::getOrderNo)
                .containsExactly(1, 2);
        // 空の段落は入れない
        verify(detailMapper, never()).insertPatterns(anyLong(), any());
    }

    @Test
    @DisplayName("編集の保存: 会話は枠を入れてから、採番された 会話ID で発言を入れる（表示順は元のまま）")
    void editorInsertsDialogLinesWithGeneratedDialogId() {
        givenEditorSave(4);
        JpnWordDetailChildren.Dialog saved = new JpnWordDetailChildren.Dialog();
        saved.setDialogId(7001L);
        saved.setOrderNo(1);
        saved.setScene("店");
        when(detailMapper.listDialogs(900L)).thenReturn(List.of(saved));

        Map<String, Object> dialog = new java.util.LinkedHashMap<>();
        dialog.put("scene", "店");
        dialog.put("lines", List.of(
                Map.of("speaker", "A", "japanese", "いらっしゃい。", "chinese", "欢迎。"),
                Map.of("speaker", "B", "japanese", "これください。", "chinese", "请给我这个。")));
        // 本文が変わるので会話も人が作った行（APP）
        Map<String, Object> editor = new java.util.LinkedHashMap<>();
        editor.put("descriptionJa", "新しい説明");
        editor.put("senses", List.of());
        editor.put("examples", List.of());
        editor.put("dialogs", List.of(dialog));

        service.saveWordEditor(student(), WORD_ID, editorRequest(editor));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JpnWordDetailChildren.Dialog>> dialogs = ArgumentCaptor.forClass(List.class);
        verify(detailMapper).insertDialogs(eq(900L), dialogs.capture());
        assertThat(dialogs.getValue().get(0).getSourceCode()).isEqualTo("APP");
        assertThat(dialogs.getValue().get(0).getOrderNo()).isEqualTo(1);

        // 発言は採番された 会話ID で入り、表示順は画面が送った並びのまま
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JpnWordDetailChildren.DialogLine>> lines = ArgumentCaptor.forClass(List.class);
        verify(detailMapper).insertDialogLines(eq(900L), lines.capture());
        assertThat(lines.getValue()).hasSize(2);
        assertThat(lines.getValue().get(0).getDialogId()).isEqualTo(7001L);
        assertThat(lines.getValue().get(0).getSpeaker()).isEqualTo("A");
        assertThat(lines.getValue().get(1).getSpeaker()).isEqualTo("B");
        assertThat(lines.getValue()).extracting(JpnWordDetailChildren.DialogLine::getOrderNo)
                .containsExactly(1, 2);
    }

    @Test
    @DisplayName("編集の保存: 要求に無い段落の行は消す（複製しない）")
    void editorDropsRowsMissingFromRequest() {
        givenEditorSave(4);
        // 語義と例文を送らない＝画面で全部消した（空配列は「空にする」の意味）
        Map<String, Object> editor = Map.of("descriptionJa", "新しい説明",
                "senses", List.of(), "examples", List.of());

        service.saveWordEditor(student(), WORD_ID, editorRequest(editor));

        verify(detailMapper, never()).insertSenses(anyLong(), any());
        verify(detailMapper, never()).insertExamples(anyLong(), any());
    }

    @Test
    @DisplayName("編集の保存: 詳細がまだ無い語は最初の版を作る（元詳細ID は NULL）")
    void editorCreatesFirstVersion() {
        when(wordMapper.findById(WORD_ID, ACCOUNT_ID)).thenReturn(word(WORD_ID, "愛", "あい"));
        when(detailMapper.findActiveDetail(WORD_ID)).thenReturn(null);
        when(wordMapper.update(any())).thenReturn(1);

        service.saveWordEditor(student(), WORD_ID, new JapaneseModels.WordEditorRequest(
                new JapaneseModels.WordSaveRequest("愛", "あい", "N3", "名詞", "ACTIVE", "備考", 1), 0,
                Map.of("descriptionJa", "新しい説明")));

        // 古い版が無いので ARCHIVED にする相手も無い（部分 UNIQUE 索引にも当たらない）
        verify(detailMapper, never()).archiveActiveDetail(anyLong(), anyLong());
        ArgumentCaptor<JpnWordDetailEntity> header = ArgumentCaptor.forClass(JpnWordDetailEntity.class);
        verify(detailMapper).insertDetailVersion(header.capture());
        assertThat(header.getValue().getOriginDetailId()).isNull();
    }

    @Test
    @DisplayName("編集の保存: 版数が合わなければ 409（母表も詳細も書かない）")
    void editorRejectsStaleDetailBeforeWritingWord() {
        when(wordMapper.findById(WORD_ID, ACCOUNT_ID)).thenReturn(word(WORD_ID, "愛", "あい"));
        when(detailMapper.findActiveDetail(WORD_ID)).thenReturn(detailHeader(5));
        assertThatThrownBy(() -> service.saveWordEditor(student(), WORD_ID, editorRequest(Map.of())))
                .isInstanceOf(ConflictException.class);
        verify(wordMapper, never()).update(any());
        verify(detailMapper, never()).insertDetailVersion(any());
        verify(detailMapper, never()).archiveActiveDetail(anyLong(), anyLong());
    }

    @Test
    @DisplayName("編集の保存: 母表の楽観的ロックに負けたら詳細も書かない")
    void editorDoesNotWriteDetailWhenWordVersionConflicts() {
        when(wordMapper.findById(WORD_ID, ACCOUNT_ID)).thenReturn(word(WORD_ID, "愛", "あい"));
        when(detailMapper.findActiveDetail(WORD_ID)).thenReturn(editorHeader(4));
        when(wordMapper.update(any())).thenReturn(0);

        assertThatThrownBy(() -> service.saveWordEditor(student(), WORD_ID,
                editorRequest(Map.of("descriptionJa", "新しい説明"))))
                .isInstanceOf(ConflictException.class);
        // 詳細の版は作ったが、母表の更新で失敗するのでトランザクションごとロールバックする
        verify(wordMapper).update(any());
    }

    @Test
    @DisplayName("編集の保存: 同時に保存が走って一意索引に当たったら 409（500 にしない）")
    void editorReportsConflictWhenUniqueIndexRejectsTheNewVersion() {
        givenEditorSave(4);
        // 2 つの保存が同時に走ると、後から来た方は「ARCHIVED にする相手がもう居ない」。
        // そのまま INSERT すると部分 UNIQUE 索引（uq_jpn_detail_active）に当たる
        doThrow(new DataIntegrityViolationException("uq_jpn_detail_active"))
                .when(detailMapper).insertDetailVersion(any());

        assertThatThrownBy(() -> service.saveWordEditor(student(), WORD_ID,
                editorRequest(Map.of("descriptionJa", "新しい説明"))))
                .isInstanceOf(ConflictException.class);
    }

    // -------------------------------------------------------- 版の履歴と切り替え

    private static JpnWordDetailVersionEntity version(long detailId, int contentVersion, String stateCode,
                                                      boolean manual) {
        JpnWordDetailVersionEntity entity = new JpnWordDetailVersionEntity();
        entity.setDetailId(detailId);
        entity.setContentVersion(contentVersion);
        entity.setStateCode(stateCode);
        entity.setManualCorrected(manual);
        // 楽観的ロック（画面が読んだときの値）
        entity.setVersion(3);
        entity.setSenseCount(2);
        entity.setExampleCount(3);
        return entity;
    }

    @Test
    @DisplayName("版の履歴: 新しい順に、段落の行数つきで返す")
    void listsDetailVersions() {
        when(wordMapper.findById(WORD_ID, ACCOUNT_ID)).thenReturn(word(WORD_ID, "愛", "あい"));
        when(detailMapper.listDetailVersions(WORD_ID)).thenReturn(List.of(
                version(900L, 2, "ACTIVE", true), version(800L, 1, "ARCHIVED", false)));

        JapaneseModels.WordDetailVersions result = service.detailVersions(ACCOUNT_ID, WORD_ID);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).contentVersion()).isEqualTo(2);
        // 切り替えの楽観ロックは版ごとの バージョン。一覧が返さないと画面が送れない
        assertThat(result.items().get(0).version()).isEqualTo(3);
        assertThat(result.items().get(0).active()).isTrue();
        assertThat(result.items().get(0).manual()).isTrue();
        assertThat(result.items().get(0).counts().senses()).isEqualTo(2);
        assertThat(result.items().get(0).counts().examples()).isEqualTo(3);
        // 0 件の段落は 0（null を返さない）
        assertThat(result.items().get(0).counts().patterns()).isZero();
        assertThat(result.items().get(1).active()).isFalse();
    }

    @Test
    @DisplayName("版の履歴: 段落だけ人が直した版も「人の版」に見せる")
    void marksVersionManualWhenOnlyRowsWereEdited() {
        when(wordMapper.findById(WORD_ID, ACCOUNT_ID)).thenReturn(word(WORD_ID, "愛", "あい"));
        JpnWordDetailVersionEntity onlyRows = version(900L, 2, "ACTIVE", false);
        onlyRows.setManualRows(true);
        when(detailMapper.listDetailVersions(WORD_ID)).thenReturn(List.of(onlyRows));

        JapaneseModels.WordDetailVersions result = service.detailVersions(ACCOUNT_ID, WORD_ID);

        assertThat(result.items().get(0).manual()).isTrue();
    }

    @Test
    @DisplayName("版の切り替え: 先に元の有効版を ARCHIVED にしてから指定の版を ACTIVE にする")
    void activatesDetailVersion() {
        when(wordMapper.findById(WORD_ID, ACCOUNT_ID)).thenReturn(word(WORD_ID, "愛", "あい"));
        when(detailMapper.findDetailVersion(WORD_ID, 800L)).thenReturn(version(800L, 1, "ARCHIVED", false));
        when(detailMapper.activateDetailVersion(WORD_ID, 800L, 3, ACCOUNT_ID)).thenReturn(1);
        when(detailMapper.findActiveDetail(WORD_ID)).thenReturn(editorHeader(1));
        when(detailMapper.listSenses(9L)).thenReturn(List.of(sense()));

        JapaneseModels.WordDetailResult result = service.activateDetailVersion(student(), WORD_ID, 800L,
                new JapaneseModels.ActivateVersionRequest(3));

        InOrder order = inOrder(detailMapper);
        order.verify(detailMapper).archiveActiveDetail(WORD_ID, ACCOUNT_ID);
        order.verify(detailMapper).activateDetailVersion(WORD_ID, 800L, 3, ACCOUNT_ID);
        // 画面がすぐ差し替えられるように、更新後の詳細を返す
        assertThat(result.detail().detailId()).isEqualTo(9L);
        // 表示中の詳細にも バージョン が入る（切り替えの楽観ロックはこの値を使う）
        assertThat(result.detail().version()).isEqualTo(7);
        assertThat(result.detail().detail()).containsKey("senses");
    }

    @Test
    @DisplayName("版の切り替え: バージョンが古ければ 409（他の操作が先に更新した）")
    void rejectsStaleVersionOnActivate() {
        when(wordMapper.findById(WORD_ID, ACCOUNT_ID)).thenReturn(word(WORD_ID, "愛", "あい"));
        when(detailMapper.findDetailVersion(WORD_ID, 800L)).thenReturn(version(800L, 1, "ARCHIVED", false));

        assertThatThrownBy(() -> service.activateDetailVersion(student(), WORD_ID, 800L,
                new JapaneseModels.ActivateVersionRequest(2)))
                .isInstanceOf(ConflictException.class);
        // 楽観的ロックは先に見る（元の有効版を ARCHIVED にしてから気づくと、履歴になってしまう）
        verify(detailMapper, never()).archiveActiveDetail(anyLong(), anyLong());
        verify(detailMapper, never()).activateDetailVersion(anyLong(), anyLong(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("版の切り替え: 同時に切り替えて一意索引に当たったら 409（500 にしない）")
    void reportsConflictWhenActivateHitsUniqueIndex() {
        when(wordMapper.findById(WORD_ID, ACCOUNT_ID)).thenReturn(word(WORD_ID, "愛", "あい"));
        when(detailMapper.findDetailVersion(WORD_ID, 800L)).thenReturn(version(800L, 1, "ARCHIVED", false));
        when(detailMapper.activateDetailVersion(WORD_ID, 800L, 3, ACCOUNT_ID))
                .thenThrow(new DataIntegrityViolationException("uq_jpn_detail_active"));
        assertThatThrownBy(() -> service.activateDetailVersion(student(), WORD_ID, 800L,
                new JapaneseModels.ActivateVersionRequest(3)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("版の切り替え: バージョンが 127 を超えても一致すれば切り替えられる（Integer を == で比べない）")
    void activatesDetailVersionWithLargeVersionNumber() {
        when(wordMapper.findById(WORD_ID, ACCOUNT_ID)).thenReturn(word(WORD_ID, "愛", "あい"));
        JpnWordDetailVersionEntity target = version(800L, 1, "ARCHIVED", false);
        // Integer のキャッシュ（-128〜127）の外。参照が違うので == だと必ず食い違う
        target.setVersion(300);
        when(detailMapper.findDetailVersion(WORD_ID, 800L)).thenReturn(target);
        when(detailMapper.activateDetailVersion(WORD_ID, 800L, 300, ACCOUNT_ID)).thenReturn(1);
        when(detailMapper.findActiveDetail(WORD_ID)).thenReturn(editorHeader(1));
        when(detailMapper.listSenses(9L)).thenReturn(List.of(sense()));

        service.activateDetailVersion(student(), WORD_ID, 800L, new JapaneseModels.ActivateVersionRequest(300));

        verify(detailMapper).activateDetailVersion(WORD_ID, 800L, 300, ACCOUNT_ID);
    }

    @Test
    @DisplayName("版の切り替え: その語の版でなければ 404")
    void rejectsVersionOfAnotherWord() {
        when(wordMapper.findById(WORD_ID, ACCOUNT_ID)).thenReturn(word(WORD_ID, "愛", "あい"));
        when(detailMapper.findDetailVersion(WORD_ID, 777L)).thenReturn(null);

        assertThatThrownBy(() -> service.activateDetailVersion(student(), WORD_ID, 777L,
                new JapaneseModels.ActivateVersionRequest(1)))
                .isInstanceOf(NotFoundException.class);
        verify(detailMapper, never()).activateDetailVersion(anyLong(), anyLong(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("版の切り替え: 既に有効な版を指定したら何もしない")
    void keepsAlreadyActiveVersion() {
        when(wordMapper.findById(WORD_ID, ACCOUNT_ID)).thenReturn(word(WORD_ID, "愛", "あい"));
        when(detailMapper.findDetailVersion(WORD_ID, 900L)).thenReturn(version(900L, 2, "ACTIVE", true));
        when(detailMapper.findActiveDetail(WORD_ID)).thenReturn(editorHeader(2));
        when(detailMapper.listSenses(9L)).thenReturn(List.of(sense()));

        service.activateDetailVersion(student(), WORD_ID, 900L, new JapaneseModels.ActivateVersionRequest(3));

        verify(detailMapper, never()).archiveActiveDetail(anyLong(), anyLong());
        verify(detailMapper, never()).activateDetailVersion(anyLong(), anyLong(), anyInt(), anyLong());
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
        when(detailMapper.findActiveDetail(WORD_ID)).thenReturn(detailHeader(1));
        when(detailMapper.listSenses(9L)).thenReturn(List.of(sense()));

        JapaneseModels.WordDetailResult result = service.wordDetail(ACCOUNT_ID, WORD_ID);

        assertThat(result.collections()).hasSize(1);
        assertThat(result.collections().get(0).chineseMeaning()).isNull();
        assertThat(result.questions()).hasSize(1);
        assertThat(result.questions().get(0).choiceCount()).isEqualTo(4);
        // 画面が読む形は、版のヘッダ ＋ 子テーブルから組み立てたもの
        assertThat(result.detail().detailId()).isEqualTo(9L);
        assertThat(result.detail().contentVersion()).isEqualTo(1);
        assertThat(result.detail().detail()).containsEntry("jlptLevel", "N3");
        assertThat(result.detail().detail()).containsKey("senses");
        assertThat((List<?>) result.detail().detail().get("senses")).hasSize(1);
    }

    /** 有効版のヘッダ（段落は子テーブルから別に引く）。 */
    private static JpnWordDetailEntity detailHeader(int contentVersion) {
        JpnWordDetailEntity detail = new JpnWordDetailEntity();
        detail.setDetailId(9L);
        detail.setWordId(WORD_ID);
        detail.setContentVersion(contentVersion);
        // 楽観ロック（切り替えのときに画面が渡す値）
        detail.setVersion(7);
        detail.setStateCode("ACTIVE");
        detail.setJlptLevel("N3");
        detail.setManualCorrected(false);
        detail.setPronunciationJson("{\"reading\":\"あい\"}");
        return detail;
    }

    /** 語義 1 件（組み立てで senses になる）。 */
    private static JpnWordDetailChildren.Sense sense() {
        JpnWordDetailChildren.Sense row = new JpnWordDetailChildren.Sense();
        row.setOrderNo(1);
        row.setSenseNumber(1);
        row.setJapanese("大切に思う気持ち。");
        row.setManualCorrected(false);
        row.setSourceCode("BATCH");
        return row;
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

    @Test
    @DisplayName("一覧: AI 取得の状態を 4 区画（A・B／C／D／E）にまとめて返す")
    void mapsAiStateForList() {
        JpnWordEntity entity = word(1L, "愛", "あい");
        entity.setDetailAiState("SUCCEEDED");
        entity.setReadingProblemAiState(null);
        entity.setKanjiProblemReadingState("SUCCEEDED"); // C2 だけ成功
        entity.setContextProblemAiState("FAILED");
        entity.setKanjiProblemAiState(null); // E はまだ実行していない
        when(wordMapper.count(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong())).thenReturn(1L);
        when(wordMapper.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), anyInt(), anyInt()))
                .thenReturn(List.of(entity));
        when(wordMapper.totals(ACCOUNT_ID)).thenReturn(new JpnTotalsEntity());

        JapaneseModels.WordListResult result =
                service.searchWords(ACCOUNT_ID, null, null, null, null, null, null, null, null, null, 1, 20);

        JapaneseModels.WordAiState state = result.items().get(0).aiState();
        assertThat(state.detail()).isEqualTo("SUCCEEDED");
        // C は C1 か C2 のどちらかが成功していれば「取得済」に見せる
        assertThat(state.reading()).isEqualTo("SUCCEEDED");
        assertThat(state.context()).isEqualTo("FAILED");
        assertThat(state.kanji()).isNull();
    }

    @Test
    @DisplayName("AI 取得の対象: 一致総数・取得済み・今回受付ける語を、一覧と同じ絞り込みで返す")
    void selectsAiTargetsForTheWholeFilteredSet() {
        JpnAiTargetCountsEntity counts = new JpnAiTargetCountsEntity();
        counts.setTotal(120L);
        counts.setAcquired(100L);
        counts.setCandidates(20L);
        when(wordMapper.countAiTargets(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), eq("DETAIL")))
                .thenReturn(counts);
        when(wordMapper.findAiTargets(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(),
                eq("DETAIL"), eq(true), eq(200)))
                .thenReturn(List.of(1L, 2L, 3L));

        JapaneseModels.AiTargets targets = service.aiTargets(ACCOUNT_ID, "Unit", null, null, null, null, null,
                null, null, null, "DETAIL", true, 200);

        // 窓が出す数字: 一致 120 語・うち取得済み 100 語・今回対象 20 語（うち受付けるのは 3 語）
        assertThat(targets.total()).isEqualTo(120);
        assertThat(targets.acquired()).isEqualTo(100);
        assertThat(targets.candidates()).isEqualTo(20);
        assertThat(targets.wordIds()).containsExactly(1L, 2L, 3L);
        assertThat(targets.overLimit()).isEqualTo(17);
        assertThat(targets.limit()).isEqualTo(200);
    }

    @Test
    @DisplayName("AI 取得の対象: 「すべて再取得」は取得済みも対象にする（候補＝一致総数）")
    void selectsAllWordsForReacquire() {
        JpnAiTargetCountsEntity counts = new JpnAiTargetCountsEntity();
        counts.setTotal(120L);
        counts.setAcquired(100L);
        counts.setCandidates(20L);
        when(wordMapper.countAiTargets(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(counts);
        when(wordMapper.findAiTargets(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(),
                eq("C"), eq(false), anyInt()))
                .thenReturn(List.of(9L));

        JapaneseModels.AiTargets targets = service.aiTargets(ACCOUNT_ID, null, null, null, null, null, null,
                null, null, null, "C", false, 20);

        assertThat(targets.candidates()).isEqualTo(120);
        assertThat(targets.overLimit()).isEqualTo(119);
    }

    @Test
    @DisplayName("AI 取得の対象: 取得区分が無い・知らない区分は拒否する（SQL を引く前に止める）")
    void rejectsUnknownKindForAiTargets() {
        assertThatThrownBy(() -> service.aiTargets(ACCOUNT_ID, null, null, null, null, null, null,
                null, null, null, null, true, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("取得区分");
        assertThatThrownBy(() -> service.aiTargets(ACCOUNT_ID, null, null, null, null, null, null,
                null, null, null, "X", true, 20))
                .isInstanceOf(ValidationException.class);
        verify(wordMapper, never()).countAiTargets(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("AI 取得の対象: 上限は 1〜200 に丸める（画面が大きな値を渡しても受け付けない）")
    void clampsAiTargetLimit() {
        when(wordMapper.countAiTargets(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(new JpnAiTargetCountsEntity());
        when(wordMapper.findAiTargets(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(),
                anyString(), anyBoolean(), anyInt()))
                .thenReturn(List.of());

        assertThat(service.aiTargets(ACCOUNT_ID, null, null, null, null, null, null, null, null, null,
                "E", true, 9999).limit()).isEqualTo(200);
        assertThat(service.aiTargets(ACCOUNT_ID, null, null, null, null, null, null, null, null, null,
                "E", true, 0).limit()).isEqualTo(200);
    }

    // ---------------------------------------- 単語の一括登録（新規登録画面の保存）

    /** 新規登録画面が送る 1 語（書籍・分類・SEQ 付き）。 */
    private static JapaneseModels.RegisterWord registerWord(String word, Integer seq) {
        return new JapaneseModels.RegisterWord(null, word, null, null,
                "N1~N5日本語単語", "Unit001", seq, null, null, null);
    }

    @Test
    @DisplayName("一括登録: 語と収録を一緒に入れる（一覧の書籍・分類が空にならない）")
    void registersWordWithCollection() {
        when(wordMapper.findByWordAndReading("愛", null)).thenReturn(null);
        when(wordMapper.countCollection(WORD_ID, "N1~N5日本語単語", "Unit001")).thenReturn(0L);

        JapaneseModels.RegisterResult result = service.registerWords(student(),
                new JapaneseModels.RegisterRequest(List.of(registerWord("愛", 7))));

        // 語は読みなし（読みキーは空文字）で作る
        ArgumentCaptor<JpnWordEntity> createdWord = ArgumentCaptor.forClass(JpnWordEntity.class);
        verify(wordMapper).insert(createdWord.capture());
        assertThat(createdWord.getValue().getReading()).isNull();
        assertThat(createdWord.getValue().getReadingKey()).isEmpty();
        // 収録は書籍・分類・SEQ 付きで入る（レベルは画面から来ないので null のまま渡す）
        ArgumentCaptor<String> level = ArgumentCaptor.forClass(String.class);
        verify(wordMapper).insertCollection(eq(WORD_ID), level.capture(),
                eq("N1~N5日本語単語"), eq("Unit001"), eq(7), eq("愛"), any(), any(), eq(ACCOUNT_ID));
        assertThat(level.getValue()).isNull();
        assertThat(result.wordCount()).isEqualTo(1);
        assertThat(result.collectionCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
    }

    @Test
    @DisplayName("一括登録: 既に母表にある語は作り直さず、その ID を使う")
    void reusesExistingWord() {
        when(wordMapper.findByWordAndReading("愛", null)).thenReturn(word(999L, "愛", null));
        when(wordMapper.countCollection(999L, "N1~N5日本語単語", "Unit001")).thenReturn(0L);

        JapaneseModels.RegisterResult result = service.registerWords(student(),
                new JapaneseModels.RegisterRequest(List.of(registerWord("愛", 1))));

        verify(wordMapper, never()).insert(any());
        verify(wordMapper).insertCollection(eq(999L), any(), anyString(), anyString(), anyInt(),
                anyString(), any(), any(), anyLong());
        assertThat(result.wordCount()).isZero();
        assertThat(result.collectionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("一括登録: 同じ語が同じ Unit に載っていれば飛ばす")
    void skipsWordAlreadyInUnit() {
        when(wordMapper.findByWordAndReading("愛", null)).thenReturn(null);
        when(wordMapper.countCollection(WORD_ID, "N1~N5日本語単語", "Unit001")).thenReturn(1L);

        JapaneseModels.RegisterResult result = service.registerWords(student(),
                new JapaneseModels.RegisterRequest(List.of(registerWord("愛", 1))));

        verify(wordMapper, never()).insertCollection(anyLong(), any(), anyString(), anyString(),
                anyInt(), anyString(), any(), any(), anyLong());
        assertThat(result.collectionCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.skipped()).containsExactly("愛");
        assertThat(result.message()).contains("飛ばしました");
    }

    @Test
    @DisplayName("一括登録: 書籍か分類が無ければ 1 件も入れない（黙って収録なしにしない）")
    void rejectsWordWithoutBookOrCategory() {
        JapaneseModels.RegisterWord broken = new JapaneseModels.RegisterWord(null, "愛", null, null,
                "N1~N5日本語単語", "  ", 1, null, null, null);

        assertThatThrownBy(() -> service.registerWords(student(),
                new JapaneseModels.RegisterRequest(List.of(broken))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("愛");

        verify(wordMapper, never()).insert(any());
        verify(wordMapper, never()).insertCollection(anyLong(), any(), anyString(), anyString(),
                anyInt(), anyString(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("一括登録: SEQ の指定が無ければ、その Unit の次の番号を使う")
    void assignsNextSeqWhenMissing() {
        when(wordMapper.findByWordAndReading("愛", null)).thenReturn(null);
        when(wordMapper.countCollection(WORD_ID, "N1~N5日本語単語", "Unit001")).thenReturn(0L);
        when(wordMapper.maxCollectionSeq("N1~N5日本語単語", "Unit001")).thenReturn(100);

        service.registerWords(student(),
                new JapaneseModels.RegisterRequest(List.of(registerWord("愛", null))));

        verify(wordMapper).insertCollection(eq(WORD_ID), any(), anyString(), anyString(), eq(101),
                anyString(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("一括登録: 指定された SEQ が既に使われていれば、後ろに詰める（位置が重ならない）")
    void avoidsTakenSeq() {
        when(wordMapper.findByWordAndReading("愛", null)).thenReturn(null);
        when(wordMapper.countCollection(WORD_ID, "N1~N5日本語単語", "Unit001")).thenReturn(0L);
        when(wordMapper.countCollectionSeq("N1~N5日本語単語", "Unit001", 7)).thenReturn(1L);
        when(wordMapper.maxCollectionSeq("N1~N5日本語単語", "Unit001")).thenReturn(100);

        service.registerWords(student(),
                new JapaneseModels.RegisterRequest(List.of(registerWord("愛", 7))));

        verify(wordMapper).insertCollection(eq(WORD_ID), any(), anyString(), anyString(), eq(101),
                anyString(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("一括登録: 指定された SEQ が空いていれば、その番号をそのまま使う")
    void keepsFreeSeq() {
        when(wordMapper.findByWordAndReading("愛", null)).thenReturn(null);
        when(wordMapper.countCollection(WORD_ID, "N1~N5日本語単語", "Unit001")).thenReturn(0L);
        when(wordMapper.countCollectionSeq("N1~N5日本語単語", "Unit001", 7)).thenReturn(0L);

        service.registerWords(student(),
                new JapaneseModels.RegisterRequest(List.of(registerWord("愛", 7))));

        verify(wordMapper).insertCollection(eq(WORD_ID), any(), anyString(), anyString(), eq(7),
                anyString(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("一括登録: 新しい書籍名なら、先に書籍マスタを作る（収録の書籍ID を空にしない）")
    void createsBookMasterForNewBook() {
        when(wordMapper.findBookIdByName("N1~N5日本語単語")).thenReturn(null);
        when(wordMapper.maxBookCode()).thenReturn(1);
        when(wordMapper.findByWordAndReading("愛", null)).thenReturn(null);
        when(wordMapper.countCollection(WORD_ID, "N1~N5日本語単語", "Unit001")).thenReturn(0L);

        service.registerWords(student(),
                new JapaneseModels.RegisterRequest(List.of(registerWord("愛", 1))));

        // 既存の '01' の次は '02'
        verify(wordMapper).insertBook("02", "N1~N5日本語単語", ACCOUNT_ID);
        verify(wordMapper).insertCollection(eq(WORD_ID), any(), eq("N1~N5日本語単語"), anyString(),
                anyInt(), anyString(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("一括登録: 既にある書籍名なら、マスタを作り直さない")
    void keepsExistingBookMaster() {
        when(wordMapper.findBookIdByName("N1~N5日本語単語")).thenReturn(2L);
        when(wordMapper.findByWordAndReading("愛", null)).thenReturn(null);
        when(wordMapper.countCollection(WORD_ID, "N1~N5日本語単語", "Unit001")).thenReturn(0L);

        service.registerWords(student(),
                new JapaneseModels.RegisterRequest(List.of(registerWord("愛", 1))));

        verify(wordMapper, never()).insertBook(anyString(), anyString(), anyLong());
        // マスタを引くのは書籍ごとに 1 回だけ（語の数だけ引かない）
        verify(wordMapper).findBookIdByName("N1~N5日本語単語");
    }

    @Test
    @DisplayName("一括登録: 収録を入れた後、書籍の 分類数・収録語数 を数え直す")
    void refreshesBookCounts() {
        when(wordMapper.findBookIdByName("N1~N5日本語単語")).thenReturn(2L);
        when(wordMapper.findByWordAndReading("愛", null)).thenReturn(null);
        when(wordMapper.countCollection(WORD_ID, "N1~N5日本語単語", "Unit001")).thenReturn(0L);

        service.registerWords(student(),
                new JapaneseModels.RegisterRequest(List.of(registerWord("愛", 1))));

        verify(wordMapper).refreshBookCounts("N1~N5日本語単語");
    }

    @Test
    @DisplayName("一括登録: 位置が競合したら、次の番号で 1 回だけやり直す（一意索引に当てない）")
    void retriesWhenPositionIsTaken() {
        when(wordMapper.findBookIdByName("N1~N5日本語単語")).thenReturn(2L);
        when(wordMapper.findByWordAndReading("愛", null)).thenReturn(null);
        when(wordMapper.countCollection(WORD_ID, "N1~N5日本語単語", "Unit001")).thenReturn(0L);
        when(wordMapper.countCollectionSeq("N1~N5日本語単語", "Unit001", 7)).thenReturn(0L);
        when(wordMapper.maxCollectionSeq("N1~N5日本語単語", "Unit001")).thenReturn(100);
        // 1 回目は同時に走った別の登録が同じ位置を取った（uq_jpn_collect_position）
        when(wordMapper.insertCollection(anyLong(), any(), anyString(), anyString(), anyInt(),
                anyString(), any(), any(), anyLong()))
                .thenThrow(new DuplicateKeyException("uq_jpn_collect_position"))
                .thenReturn(1);

        service.registerWords(student(),
                new JapaneseModels.RegisterRequest(List.of(registerWord("愛", 7))));

        ArgumentCaptor<Integer> seqs = ArgumentCaptor.forClass(Integer.class);
        verify(wordMapper, times(2)).insertCollection(eq(WORD_ID), any(), eq("N1~N5日本語単語"),
                eq("Unit001"), seqs.capture(), anyString(), any(), any(), anyLong());
        // 指定の 7 で失敗 → その Unit の次の番号 101 でやり直す
        assertThat(seqs.getAllValues()).containsExactly(7, 101);
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
        when(testMapper.listChoices(5L)).thenReturn(pool());
        when(testMapper.listChoices(6L)).thenReturn(pool());
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

    /* ============================================== 選択肢プール（切片6・設計「2. 选项池」） */

    /** プール（6 件）から出題を作る下ごしらえ。出題は 1 件だけ選ばれる。 */
    private void givenPoolForCreate() {
        when(testMapper.findByNo(anyString())).thenReturn(null);
        when(testMapper.findById(77L)).thenReturn(test(77L, "C", 1, 0, 0, 0));
        when(testMapper.pickQuestions(eq("C"), any(), any(), any(), any(), eq("NORMAL"), eq(false),
                eq(ACCOUNT_ID), eq(1))).thenReturn(List.of(question(5L)));
        when(testMapper.listChoices(5L)).thenReturn(pool());
    }

    @Test
    @DisplayName("テスト作成: プールから正解1＋誤答3を選んで並べ替え、今回提示した4択を出題行に固定する")
    void createTestFreezesFourChoicesFromPool() {
        givenPoolForCreate();

        service.createTest(student(), new JapaneseModels.TestCreateRequest("C", null, null, null, null,
                "NORMAL", "ALL", 1));

        ArgumentCaptor<JpnTestQuestionEntity> captor = ArgumentCaptor.forClass(JpnTestQuestionEntity.class);
        verify(testMapper).insertEntry(captor.capture());
        List<Map<String, Object>> choices = promptedChoicesOf(captor.getValue());

        // 4 択（正解 1 ＋ 誤答 3）。並びはプールの順のままではない（テスト作成時に選んで混ぜる）
        assertThat(choices).hasSize(4);
        assertThat(choices).filteredOn(item -> Boolean.TRUE.equals(item.get("correct"))).hasSize(1);
        // 正解はプールの正解（choiceId 4 の「あい」）から来ている
        Map<String, Object> correct = choices.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("correct"))).findFirst().orElseThrow();
        assertThat(correct.get("choiceId")).isEqualTo(4);
        assertThat(correct.get("value")).isEqualTo("あい");
        // 誤答はプールの誤答から選ばれている（プールに無い値は作らない）
        assertThat(choices).filteredOn(item -> !Boolean.TRUE.equals(item.get("correct")))
                .allSatisfy(item -> assertThat(List.of(1, 2, 3, 5, 6))
                        .contains(((Number) item.get("choiceId")).intValue()));
        // 値は 4 件とも違う
        assertThat(choices).extracting(item -> String.valueOf(item.get("value"))).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("テスト作成: 同じテストを開き直しても、提示した4択は同じ（出題選択肢JSON から返す）")
    void testDetailReturnsFrozenChoices() {
        when(testMapper.findById(77L)).thenReturn(test(77L, "C", 1, 0, 0, 0));
        JpnTestQuestionEntity frozen = entry(501L, 1, 5L, "PENDING", null);
        frozen.setChoicesJson(promptedChoicesJson());
        when(testMapper.listEntries(77L)).thenReturn(List.of(frozen));

        // 2 回開いても同じ（プールを読み直さない。listChoices は呼ばれない）
        for (int opened = 0; opened < 2; opened += 1) {
            JapaneseModels.TestDetailResult detail = service.testDetail(ACCOUNT_ID, 77L);
            List<JapaneseModels.ChoiceRow> choices = detail.questions().get(0).choices();
            assertThat(choices).hasSize(4);
            assertThat(choices).extracting(JapaneseModels.ChoiceRow::orderNo).containsExactly(1, 2, 3, 4);
            assertThat(choices).extracting(JapaneseModels.ChoiceRow::value)
                    .containsExactly("えい", "あお", "あい", "い");
            assertThat(choices).filteredOn(JapaneseModels.ChoiceRow::correct).hasSize(1);
            // 判定に使う選択肢ID（プールの ID）と、画面が送る値（＝ID）が分かる
            assertThat(choices).extracting(JapaneseModels.ChoiceRow::choiceId)
                    .containsExactly(1L, 6L, 4L, 3L);
        }
        verify(testMapper, never()).listChoices(anyLong());
    }

    @Test
    @DisplayName("テスト作成(A・B): 単語から作った4択も同じ形で 出題選択肢JSON に書く（判定の経路を1本にする）")
    void createTestForTypeAFreezesStudyContent() {
        when(wordMapper.findById(anyLong(), eq(ACCOUNT_ID))).thenAnswer(i -> word(i.getArgument(0), "愛", "あい"));
        when(testMapper.findByNo(anyString())).thenReturn(null);
        when(testMapper.findById(77L)).thenReturn(test(77L, "A", 1, 0, 0, 0));
        when(testMapper.pickWords(any(), any(), any(), any(), any(), anyBoolean(), anyInt()))
                .thenReturn(List.of(word(1L, "愛", "あい"), word(2L, "朝", "あさ"),
                        word(3L, "雨", "あめ"), word(4L, "犬", "いぬ")));
        when(testMapper.listEntries(77L)).thenReturn(List.of(entry(500L, 1, null, "PENDING", null)));

        service.createTest(student(), new JapaneseModels.TestCreateRequest("A", null, null, null, null,
                null, "ALL", 1));

        ArgumentCaptor<JpnTestQuestionEntity> captor = ArgumentCaptor.forClass(JpnTestQuestionEntity.class);
        verify(testMapper).insertEntry(captor.capture());
        JpnTestQuestionEntity inserted = captor.getValue();

        assertThat(inserted.getChoicesJson()).isEqualTo("[]");
        assertThat(inserted.getSnapshotJson()).contains("A_STUDY", "wordDetail");

    }

    @Test
    @DisplayName("回答: 今回提示した4択で判定する（プールを読み直さない）")
    void answersUsingPromptedChoices() {
        when(testMapper.findById(77L)).thenReturn(test(77L, "C", 1, 0, 0, 0));
        JpnTestQuestionEntity pending = entry(501L, 1, 5L, "PENDING", null);
        pending.setChoicesJson(promptedChoicesJson());
        when(testMapper.findEntry(77L, 1)).thenReturn(pending);
        when(testMapper.listEntries(77L)).thenReturn(List.of(entry(501L, 1, 5L, "ANSWERED", "CORRECT")));
        when(statusMapper.averageSkillMastery(ACCOUNT_ID, WORD_ID)).thenReturn(BigDecimal.valueOf(20));

        // 3 番目に提示した choiceId 4（正解）
        JapaneseModels.AnswerResult result = service.answer(student(), 77L,
                new JapaneseModels.AnswerRequest(1, 4L, null, 100L));

        assertThat(result.correct()).isTrue();
        assertThat(result.correctValue()).isEqualTo("あい");
        // プール（6 件）を読み直さない。提示した 4 択だけで判定する
        verify(testMapper, never()).listChoices(anyLong());
    }

    @Test
    @DisplayName("回答: 今回提示していない選択肢IDなら 400（表示と判定を食い違わせない）")
    void rejectsChoiceNotPrompted() {
        when(testMapper.findById(77L)).thenReturn(test(77L, "C", 1, 0, 0, 0));
        JpnTestQuestionEntity pending = entry(501L, 1, 5L, "PENDING", null);
        pending.setChoicesJson(promptedChoicesJson());
        when(testMapper.findEntry(77L, 1)).thenReturn(pending);

        // choiceId 2（「あう」）はプールにはあるが、今回提示した 4 択には入っていない
        assertThatThrownBy(() -> service.answer(student(), 77L,
                new JapaneseModels.AnswerRequest(1, 2L, null, 100L)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("選択肢");
    }

    @Test
    @DisplayName("回答: 出題選択肢JSON が無い古い出題は、プール（問題ID）で判定する（移行データの防御）")
    void answersLegacyEntryUsingPool() {
        when(testMapper.findById(77L)).thenReturn(test(77L, "C", 1, 0, 0, 0));
        when(testMapper.findEntry(77L, 1)).thenReturn(entry(501L, 1, 5L, "PENDING", null));
        when(testMapper.listChoices(5L)).thenReturn(pool());
        when(testMapper.listEntries(77L)).thenReturn(List.of(entry(501L, 1, 5L, "ANSWERED", "CORRECT")));
        when(statusMapper.averageSkillMastery(ACCOUNT_ID, WORD_ID)).thenReturn(BigDecimal.valueOf(20));

        JapaneseModels.AnswerResult result = service.answer(student(), 77L,
                new JapaneseModels.AnswerRequest(1, 4L, null, 100L));

        assertThat(result.correct()).isTrue();
        verify(testMapper).listChoices(5L);
    }

    @Test
    @DisplayName("回答(A・B): 出題選択肢JSON の choiceId は 1 からの並び順としても受け取る（今の画面を壊さない）")
    void answersTypeAUsingPromptedChoices() {
        JpnTestEntity running = test(77L, "A", 1, 0, 0, 0);
        when(testMapper.findById(77L)).thenReturn(running);
        JpnTestQuestionEntity pending = entry(501L, 1, null, "PENDING", null);
        pending.setSnapshotJson("{\"questionType\":\"A_READING\",\"questionText\":\"「愛」の読みを選んでください。\","
                + "\"correctValue\":\"あい\",\"choices\":[\"あお\",\"あい\",\"あめ\",\"いぬ\"]}");
        pending.setChoicesJson("[{\"choiceId\":null,\"value\":\"あお\",\"reading\":null,\"correct\":false},"
                + "{\"choiceId\":null,\"value\":\"あい\",\"reading\":null,\"correct\":true},"
                + "{\"choiceId\":null,\"value\":\"あめ\",\"reading\":null,\"correct\":false},"
                + "{\"choiceId\":null,\"value\":\"いぬ\",\"reading\":null,\"correct\":false}]");
        when(testMapper.findEntry(77L, 1)).thenReturn(pending);
        when(testMapper.listEntries(77L)).thenReturn(List.of(entry(501L, 1, null, "ANSWERED", "CORRECT")));
        when(statusMapper.averageSkillMastery(ACCOUNT_ID, WORD_ID)).thenReturn(BigDecimal.valueOf(20));

        // 2 番目に提示した「あい」を並び順で答える（今の画面と同じ送り方）
        JapaneseModels.AnswerResult result = service.answer(student(), 77L,
                new JapaneseModels.AnswerRequest(1, 2L, null, 100L));

        assertThat(result.correct()).isTrue();
        assertThat(result.correctValue()).isEqualTo("あい");
        verify(testMapper, never()).listChoices(anyLong());
    }

    @Test
    @DisplayName("回答: 提示した選択肢に choiceId が無くても、値（answerText）で判定できる")
    void answersPromptedChoiceByValue() {
        when(testMapper.findById(77L)).thenReturn(test(77L, "C", 1, 0, 0, 0));
        JpnTestQuestionEntity pending = entry(501L, 1, 5L, "PENDING", null);
        pending.setChoicesJson(promptedChoicesJson());
        when(testMapper.findEntry(77L, 1)).thenReturn(pending);
        when(testMapper.listEntries(77L)).thenReturn(List.of(entry(501L, 1, 5L, "ANSWERED", "CORRECT")));
        when(statusMapper.averageSkillMastery(ACCOUNT_ID, WORD_ID)).thenReturn(BigDecimal.valueOf(20));

        // choiceId は送らず、提示した値（3 番目の「あい」）だけで答える
        JapaneseModels.AnswerResult result = service.answer(student(), 77L,
                new JapaneseModels.AnswerRequest(1, null, "あい", 100L));

        assertThat(result.correct()).isTrue();
        assertThat(result.correctValue()).isEqualTo("あい");
    }

    @Test
    @DisplayName("回答: 提示に無い値（answerText）なら 400（表示していない値で正解にしない）")
    void rejectsValueNotPrompted() {
        when(testMapper.findById(77L)).thenReturn(test(77L, "C", 1, 0, 0, 0));
        JpnTestQuestionEntity pending = entry(501L, 1, 5L, "PENDING", null);
        pending.setChoicesJson(promptedChoicesJson());
        when(testMapper.findEntry(77L, 1)).thenReturn(pending);

        // 「あう」はプールにはあるが、今回提示した 4 択には入っていない
        assertThatThrownBy(() -> service.answer(student(), 77L,
                new JapaneseModels.AnswerRequest(1, null, "あう", 100L)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("選択肢");
    }

    /**
     * 受入基準 #8（設計 §4）: <b>プールを取り直しても、受験済みの出題は変わらない</b>。
     *
     * <p>テスト作成時に固定した 4 択は出題行の {@code 出題選択肢JSON} にしか無いので、
     * 元の問題と選択肢が差し替わっても（AI の再取得で旧問題が ARCHIVED になっても）
     * 画面に出す 4 択も判定も変わらない。</p>
     */
    @Test
    @DisplayName("プールの再取得: 元の問題が差し替わっても、作成済みテストの4択は変わらない")
    void keepsFrozenChoicesAfterPoolRefetched() {
        givenPoolForCreate();
        service.createTest(student(), new JapaneseModels.TestCreateRequest("C", null, null, null, null,
                "NORMAL", "ALL", 1));

        ArgumentCaptor<JpnTestQuestionEntity> captor = ArgumentCaptor.forClass(JpnTestQuestionEntity.class);
        verify(testMapper).insertEntry(captor.capture());
        String frozen = captor.getValue().getChoicesJson();
        List<String> frozenValues = promptedChoicesOf(captor.getValue()).stream()
                .map(item -> String.valueOf(item.get("value"))).toList();

        // AI の再取得で問題が作り直された（旧問題は ARCHIVED になり、プールは別のものに変わった）
        reset(testMapper);
        JpnTestQuestionEntity saved = entry(501L, 1, 5L, "PENDING", null);
        saved.setChoicesJson(frozen);
        when(testMapper.findById(77L)).thenReturn(test(77L, "C", 1, 0, 0, 0));
        when(testMapper.listEntries(77L)).thenReturn(List.of(saved));
        // もしプールを読み直す実装なら、ここで別の選択肢が返ってしまう
        when(testMapper.listChoices(5L)).thenReturn(List.of(
                choice(91L, "新しい誤答1", false), choice(92L, "新しい誤答2", false),
                choice(93L, "新しい正解", true), choice(94L, "新しい誤答3", false)));

        JapaneseModels.TestDetailResult detail = service.testDetail(ACCOUNT_ID, 77L);

        assertThat(detail.questions().get(0).choices()).extracting(JapaneseModels.ChoiceRow::value)
                .containsExactlyElementsOf(frozenValues);
        // 判定も固定した 4 択のまま（再取得後のプールは見ない）
        verify(testMapper, never()).listChoices(anyLong());
        // 出題選択肢JSON は書き換えられていない（読み取りだけ）
        verify(testMapper, never()).insertEntry(any());
    }

    @Test
    @DisplayName("回答(A・B): choiceId が無い古い出題はスナップショットの choices で判定する（互換）")
    void answersTypeAUsingSnapshotChoices() {
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
    }

    @Test
    void createTestForTypeABuildsQuestionsFromWords() {
        when(wordMapper.findById(anyLong(), eq(ACCOUNT_ID))).thenAnswer(i -> word(i.getArgument(0), "愛", "あい"));
        when(testMapper.findByNo(anyString())).thenReturn(null);
        when(testMapper.findById(77L)).thenReturn(test(77L, "A", 1, 0, 0, 0));
        when(testMapper.pickWords(any(), any(), any(), any(), any(), anyBoolean(), anyInt()))
                .thenReturn(List.of(word(1L, "愛", "あい"), word(2L, "朝", "あさ"),
                        word(3L, "雨", "あめ"), word(4L, "犬", "いぬ")));
        when(testMapper.listEntries(77L)).thenReturn(List.of(entry(500L, 1, null, "PENDING", null)));

        service.createTest(student(), new JapaneseModels.TestCreateRequest("A", null, null, null, null,
                null, "ALL", 1));

        ArgumentCaptor<JpnTestQuestionEntity> captor = ArgumentCaptor.forClass(JpnTestQuestionEntity.class);
        verify(testMapper).insertEntry(captor.capture());
        String snapshot = captor.getValue().getSnapshotJson();
        assertThat(snapshot).contains("\"questionType\":\"A_STUDY\"");
        assertThat(snapshot).contains("\"wordDetail\"");
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
    @DisplayName("回答: A（勉強）は技能習得の行を書かない（A は A確認回数 で数える）")
    void answeringATestDoesNotTrackSkills() {
        // JPN_技能習得情報 の CHECK は B〜E の技能区分だけを許す。A で技能の行を書くと
        // 実 DB では CHECK 違反で回答そのものが失敗する（実測）。
        when(testMapper.findById(77L)).thenReturn(test(77L, "A", 1, 0, 0, 0));
        JpnTestQuestionEntity pending = entry(501L, 1, null, "PENDING", null);
        pending.setSnapshotJson("{\"questionType\":\"A_READING\",\"questionText\":\"「愛」の読みを選んでください。\","
                + "\"correctValue\":\"あい\",\"choices\":[\"あお\",\"あい\",\"あめ\",\"いぬ\"]}");
        when(testMapper.findEntry(77L, 1)).thenReturn(pending);
        when(testMapper.listEntries(77L)).thenReturn(List.of(entry(501L, 1, null, "ANSWERED", "CORRECT")));

        JapaneseModels.AnswerResult result = service.answer(student(), 77L,
                new JapaneseModels.AnswerRequest(1, 2L, null, 100L));

        assertThat(result.correct()).isTrue();
        // 技能の行は作らない・更新しない
        verify(statusMapper, never()).insertSkillIfAbsent(anyLong(), anyLong(), anyString(), anyString());
        verify(statusMapper, never()).updateSkillAfterAnswer(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), any(), anyBoolean(), anyString(), any(), anyLong(), any());
        // 学習状況と日次は A でも更新する（A確認回数 が +1 される）
        verify(statusMapper).insertStatusIfAbsent(ACCOUNT_ID, WORD_ID);
        verify(statusMapper).updateStatusAfterAnswer(eq(ACCOUNT_ID), eq(WORD_ID), eq("LEARNING"),
                any(), eq(true), eq(false), any(), eq(3), eq("A"), eq("CORRECT"), eq(100L), any());
        verify(statusMapper).refreshStatusFromSkills(eq(ACCOUNT_ID), eq(WORD_ID), eq("CORRECT"), eq(1));
        verify(statusMapper).upsertDaily(eq(ACCOUNT_ID), any(), eq("A"), eq(100L), eq(true));
    }

    @Test
    @DisplayName("詳細: 同じ見出し語で別の読みを持つ語があれば alternateReading に入れる")
    void wordDetailIncludesAlternateReading() {
        when(wordMapper.findById(WORD_ID, ACCOUNT_ID)).thenReturn(word(WORD_ID, "開く", "ひらく"));
        when(wordMapper.listCollections(WORD_ID)).thenReturn(List.of());
        when(wordMapper.listQuestions(WORD_ID)).thenReturn(List.of());
        when(wordMapper.findAlternateReading(WORD_ID)).thenReturn("あく");
        // 詳細（版）は 11 の子テーブルが空でも組み立てられる
        JpnWordDetailEntity version = new JpnWordDetailEntity();
        version.setDetailId(9L);
        version.setWordId(WORD_ID);
        version.setContentVersion(1);
        when(detailMapper.findActiveDetail(WORD_ID)).thenReturn(version);

        JapaneseModels.WordDetailResult result = service.wordDetail(ACCOUNT_ID, WORD_ID);

        assertThat(result.detail().detail()).containsEntry("alternateReading", "あく");
    }

    @Test
    @DisplayName("詳細: 別の読みを持つ語が無ければ alternateReading は入れない")
    void wordDetailOmitsAlternateReadingWhenNone() {
        when(wordMapper.findById(WORD_ID, ACCOUNT_ID)).thenReturn(word(WORD_ID, "愛", "あい"));
        when(wordMapper.listCollections(WORD_ID)).thenReturn(List.of());
        when(wordMapper.listQuestions(WORD_ID)).thenReturn(List.of());
        when(wordMapper.findAlternateReading(WORD_ID)).thenReturn(null);
        JpnWordDetailEntity version = new JpnWordDetailEntity();
        version.setDetailId(9L);
        version.setWordId(WORD_ID);
        version.setContentVersion(1);
        when(detailMapper.findActiveDetail(WORD_ID)).thenReturn(version);

        JapaneseModels.WordDetailResult result = service.wordDetail(ACCOUNT_ID, WORD_ID);

        assertThat(result.detail().detail()).doesNotContainKey("alternateReading");
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
