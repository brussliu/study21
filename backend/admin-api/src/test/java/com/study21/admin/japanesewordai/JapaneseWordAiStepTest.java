package com.study21.admin.japanesewordai;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.geometryai.GeometryAiClient;
import com.study21.admin.geometryai.GeometryAiConnectionResolver;
import com.study21.admin.geometryai.dto.AiResponseFormatPrompt;
import com.study21.admin.setting.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * batC41〜batC44 の業務処理（1 語ずつ AI に渡して書き戻す）。
 *
 * <p>確かめる接縫（Mapper と AI はモック。実 DB は使わない）:</p>
 * <ol>
 *   <li>対象は SQL に任せる（まだ成功していない語だけ。{@code BATCH_MAX} 件まで）</li>
 *   <li>1 語 = 1 回の生成履歴。成功で {@code SUCCEEDED} ＋ 生成件数、失敗で {@code FAILED} ＋ 理由</li>
 *   <li>詳細は「新しい版を作る」、問題は「その回の内容種別の題だけ入れる」＋ 選択肢はプール（正解 1 ＋ 誤答 4〜6）</li>
 *   <li>失敗した語は<b>内容を書かない</b>（中途半端な詳細・問題を残さない）</li>
 *   <li>{@code RETRY_LIMIT} の回数だけ再試行し、4xx は再試行しない</li>
 *   <li>一部が失敗しても、成功した語は残して集計を返す（0 件成功なら例外）</li>
 * </ol>
 */
class JapaneseWordAiStepTest {

    private static final long WORD_ID = 101L;

    private JapaneseWordAiMapper mapper;
    private JapaneseWordAiClient aiClient;
    private JapaneseWordAiDetailReader detailReader;
    private JapaneseWordAiDetailWriter detailWriter;
    private SettingsService settingsService;
    private GeometryAiConnectionResolver connectionResolver;
    private AiResponseFormatPrompt responseFormatPrompt;
    private JapaneseWordAiStep step;

    @BeforeEach
    void setUp() {
        mapper = mock(JapaneseWordAiMapper.class);
        aiClient = mock(JapaneseWordAiClient.class);
        detailReader = mock(JapaneseWordAiDetailReader.class);
        detailWriter = mock(JapaneseWordAiDetailWriter.class);
        settingsService = mock(SettingsService.class);
        connectionResolver = mock(GeometryAiConnectionResolver.class);
        responseFormatPrompt = mock(AiResponseFormatPrompt.class);
        // 出力形式（DTO から生成した JSON Schema）は別の部品が足す。ここでは素通しにする
        when(responseFormatPrompt.appendTo(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        step = new JapaneseWordAiStep(mapper, aiClient, detailReader, detailWriter, settingsService,
                connectionResolver, responseFormatPrompt);
        // AI に渡す既存の詳細（有効版を組み立てたもの）。まだ無ければ null
        when(detailReader.readAssembledDetail(anyLong())).thenReturn(null);
        // 建新版は「作った版の情報」を返す（版のヘッダに生成ID が入る）
        when(detailWriter.createVersion(anyLong(), anyString(), any(), anyString(), anyString()))
                .thenReturn(new JapaneseWordAiDetailWriter.CreatedVersion(901L, 900L, 7L, 0));
        when(connectionResolver.resolve(anyString(), anyString())).thenReturn(
                new GeometryAiConnectionResolver.AiConnection("qwen", "qwen3.7-plus",
                        "https://example.com/v1/chat/completions", "secret"));
        when(settingsService.requireSettings(anyString(), any())).thenReturn(settings("batC41"));
        when(mapper.findTargets(anyString(), any(), anyInt())).thenReturn(List.of(target()));
        when(mapper.listCollections(anyLong())).thenReturn(List.of(collection()));
        when(mapper.findActiveGeneration(anyLong(), anyString())).thenReturn(null);
        // この取得の版（その語・その内容種別で「今までの最大 + 1」）。問題の行にも同じ番号が入る
        when(mapper.nextGenerationVersion(anyLong(), anyString())).thenReturn(3);
        when(mapper.insertGeneration(any())).thenAnswer(invocation -> {
            JapaneseWordAiMapper.GenerationRow row = invocation.getArgument(0);
            row.setGenerationId(7L);
            return 1;
        });
        when(mapper.insertQuestion(any())).thenAnswer(invocation -> {
            JapaneseWordAiMapper.QuestionRow row = invocation.getArgument(0);
            row.setQuestionId(55L);
            return 1;
        });
    }

    private static Map<String, String> settings(String batchCode) {
        String prefix = JapaneseWordAiSettings.prefixOf(batchCode);
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "_AI_PROVIDER", "qwen:1");
        values.put(prefix + "_BATCH_MAX", "20");
        values.put(prefix + "_THREADS", "2");
        values.put(prefix + "_REQUEST_TIMEOUT_SECONDS", "300");
        values.put(prefix + "_MAX_COMPLETION_TOKENS", "11776");
        values.put(prefix + "_TEMPERATURE", "0.2");
        values.put(prefix + "_SYSTEM_PROMPT", "system");
        values.put(prefix + "_USER_PROMPT", "取得区分: {{kind}}\n{{word_json}}");
        values.put(prefix + "_RETRY_LIMIT", "1");
        return values;
    }

    private static JapaneseWordAiMapper.AiCallTarget target() {
        return targetOf("あい", "N3");
    }

    /** 読みだけ指定（レベルは入っている語）。{@code reading} が null なら「まだ読みが無い」語。 */
    private static JapaneseWordAiMapper.AiCallTarget targetOf(String reading) {
        return targetOf(reading, "N3");
    }

    /**
     * 対象語。
     *
     * @param reading 読み。null なら「まだ読みが無い」語（画面からの登録直後）
     * @param jlpt    レベル。null なら「まだレベルが無い」語
     */
    private static JapaneseWordAiMapper.AiCallTarget targetOf(String reading, String jlpt) {
        JapaneseWordAiMapper.AiCallTarget target = new JapaneseWordAiMapper.AiCallTarget();
        target.setWordId(WORD_ID);
        target.setHeading("愛");
        target.setReading(reading);
        target.setPartOfSpeech("名詞");
        target.setJlptLevel(jlpt);
        target.setStateCode("ACTIVE");
        return target;
    }

    private static JapaneseWordAiMapper.CollectionRow collection() {
        JapaneseWordAiMapper.CollectionRow row = new JapaneseWordAiMapper.CollectionRow();
        row.setBook("日本語単語帳①");
        row.setCategory("Unit001");
        row.setWordSeq(12);
        row.setPartOfSpeech("名詞");
        row.setChineseMeaning("爱");
        return row;
    }

    /** 詳細の正常な応答（語義1・例文2・コロケーション2）。DTO（batC41）のキー名で返る。 */
    private static String detailJson() {
        return """
                {"detail":{
                 "coreMeaning":"爱","descriptionJa":"大切に思う気持ち。","descriptionZh":"珍视的感情。",
                 "partOfSpeech":"名詞","jlpt":"N3","conjugation":"なし","transitivity":"NONE","importance":4,
                 "senses":[{"number":1,"japanese":"大切に思う気持ち。","chinese":"爱"}],
                 "pronunciation":{"reading":"あい","accentType":1,"accentNotation":"あ1",
                                  "hint":"低く始める。","hasAudioSample":false},
                 "examples":[{"japanese":"親の愛は無条件だ。","reading":"おやのあいはむじょうけんだ。","chinese":"父母的爱是无条件的。"},
                             {"japanese":"彼は植物を愛している。","reading":"かれはしょくぶつをあいしている。","chinese":"他热爱植物。"}],
                 "collocations":[{"expression":"愛を込める","chinese":"倾注爱意"},{"expression":"愛が深い","chinese":"爱意深厚"}],
                 "relatedWords":[],"usageNotes":[],"practices":[]}}
                """;
    }

    private static BatchExecutionEntity execution() {
        BatchExecutionEntity entity = new BatchExecutionEntity();
        entity.setExecutionId(12L);
        return entity;
    }

    /**
     * 呼出履歴に記録された ID を持つ結果にする（{@code JapaneseWordAiClient} は
     * 採番した {@code 呼出履歴ID} を返すので、Step はそれを生成履歴へ渡す）。
     */
    private static JapaneseWordAiClient.CallResult logged(JapaneseWordAiClient.CallResult result) {
        return result.withCallLogId(CALL_LOG_ID);
    }

    /** 呼出履歴の ID（採番された値）。 */
    private static final long CALL_LOG_ID = 8801L;

    @Test
    @DisplayName("DETAIL の成功で、生成履歴・詳細・呼出履歴がそろう")
    void writesDetail() {
        when(aiClient.call(any())).thenReturn(logged(JapaneseWordAiClient.CallResult.success(detailJson())));

        Map<String, Object> result = step.run(execution(), "batC41", "DETAIL", List.of());

        assertThat(result.get("message").toString()).contains("成功 1");
        // 生成履歴は RUNNING → SUCCEEDED（生成件数は 1 行）
        ArgumentCaptor<JapaneseWordAiMapper.GenerationRow> created =
                ArgumentCaptor.forClass(JapaneseWordAiMapper.GenerationRow.class);
        verify(mapper).insertGeneration(created.capture());
        assertThat(created.getValue().getContentType()).isEqualTo("A_DETAIL");
        assertThat(created.getValue().getWordId()).isEqualTo(WORD_ID);
        verify(mapper).markGenerationSucceeded(eq(7L), eq(8801L), eq(1), anyInt());
        verify(mapper, never()).markGenerationFailed(anyLong(), anyString(), anyString(), anyInt());
        // 詳細は「新しい版を作る」（内容版数は SQL 側で上げる）
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(detailWriter).createVersion(eq(WORD_ID), json.capture(), eq(7L), eq("qwen"), eq("qwen3.7-plus"));
        assertThat(json.getValue()).contains("\"jlptLevel\":\"N3\"");
        assertThat(json.getValue()).contains("\"structured\"");
        // 問題は触らない
        verify(mapper, never()).archiveQuestions(anyLong(), anyString());
        // AI 呼び出しは 1 回だけ（再試行しない）
        verify(aiClient, times(1)).call(any());
    }

    @Test
    @DisplayName("C の成功で、その回の内容種別の題だけが入る（C1 と C2 を二重に書かない）")
    void writesProblems() {
        when(settingsService.requireSettings(anyString(), any())).thenReturn(settings("batC42"));
        when(aiClient.call(any())).thenReturn(logged(JapaneseWordAiClient.CallResult.success("""
                {"problems":[
                  {"problemType":"C1_READING","questionJapanese":"読みを選んでください",
                   "targetHeading":"愛","targetReading":"あい","correctValue":"あい",
                   "options":[{"value":"えい","reading":"えい","wrongType":"READING_SIMILAR"},
                              {"value":"あう","reading":"あう","wrongType":"READING_SIMILAR"},
                              {"value":"い","reading":"い","wrongType":"READING_SIMILAR"},
                              {"value":"あい","reading":"あい","wrongType":"READING_CORRECT"},
                              {"value":"あえ","reading":"あえ","wrongType":"READING_SIMILAR"},
                              {"value":"あお","reading":"あお","wrongType":"READING_SIMILAR"}]},
                  {"problemType":"C2_KANJI","questionJapanese":"漢字を選んでください",
                   "targetHeading":"愛","targetReading":"あい","audioText":"あい","correctValue":"愛",
                   "options":[{"value":"哀","reading":"あい","wrongType":"KANJI_SIMILAR"},
                              {"value":"相","reading":"あい","wrongType":"KANJI_SIMILAR"},
                              {"value":"藍","reading":"あい","wrongType":"KANJI_SIMILAR"},
                              {"value":"愛","reading":"あい","wrongType":"KANJI_CORRECT"},
                              {"value":"挨","reading":"あい","wrongType":"KANJI_SIMILAR"},
                              {"value":"曖","reading":"あい","wrongType":"KANJI_SIMILAR"}]}
                ]}
                """)));

        Map<String, Object> result = step.run(execution(), "batC42", "C", List.of());

        assertThat(result.get("message").toString()).contains("成功 1");
        // 種別ごとに古い ACTIVE を ARCHIVED にしてから入れる（内容種別ごとに 1 回 ＝ 2 回）。
        // C の応答には C1 と C2 が両方入っているが、**その回の内容種別の題だけ**を書く
        verify(mapper, times(1)).archiveQuestions(WORD_ID, "C1_READING");
        verify(mapper, times(1)).archiveQuestions(WORD_ID, "C2_KANJI");
        ArgumentCaptor<JapaneseWordAiMapper.QuestionRow> questions =
                ArgumentCaptor.forClass(JapaneseWordAiMapper.QuestionRow.class);
        verify(mapper, times(2)).insertQuestion(questions.capture());
        assertThat(questions.getAllValues().stream().map(JapaneseWordAiMapper.QuestionRow::getQuestionType))
                .containsExactly("C1_READING", "C2_KANJI");
        // この取得の版（生成履歴と同じ番号）を問題の行にも入れる。
        // 画面の「取得状態」から開く履歴で「この版を使う」と切り替える目印
        assertThat(questions.getAllValues()).allSatisfy(question ->
                assertThat(question.getContentVersion()).isEqualTo(3));
        // 選択肢はプール（正解 1 ＋ 誤答 5 ＝ 6 件）× 2 題（1 内容種別につき 1 題）
        verify(mapper, times(12)).insertChoice(any());
        // 生成履歴は C1/C2 で 2 行（内容種別ごと）。生成件数も 1 種別 1 題
        verify(mapper, times(2)).insertGeneration(any());
        verify(mapper, times(2)).markGenerationSucceeded(anyLong(), eq(8801L), eq(1), anyInt());
        // 詳細は触らない
        verify(detailWriter, never()).createVersion(anyLong(), anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("失敗した語は内容を書かず、理由を残す（中途半端な詳細を残さない）")
    void doesNotWriteOnFailure() {
        when(aiClient.call(any())).thenReturn(
                logged(JapaneseWordAiClient.CallResult.failure("HTTP_500", "サーバーエラー")));

        // 1 語も成功しなければ例外（実行履歴を FAILED にする）
        assertThatThrownBy(() -> step.run(execution(), "batC41", "DETAIL", List.of()))
                .isInstanceOf(JapaneseWordAiStep.StepException.class)
                .hasMessageContaining("すべて失敗")
                .hasMessageContaining("HTTP_500");

        // RETRY_LIMIT=1 なので記録も 2 回
        verify(mapper, times(2)).markGenerationFailed(eq(7L), eq("HTTP_500"), anyString(), anyInt());
        verify(detailWriter, never()).createVersion(anyLong(), anyString(), any(), anyString(), anyString());
        // RETRY_LIMIT=1 なので 2 回呼ぶ
        verify(aiClient, times(2)).call(any());
    }

    @Test
    @DisplayName("4xx は再試行しない（待っても直らない）")
    void doesNotRetryClientError() {
        when(aiClient.call(any())).thenReturn(
                logged(JapaneseWordAiClient.CallResult.failure("HTTP_4XX", "リクエストが不正です")));

        assertThatThrownBy(() -> step.run(execution(), "batC41", "DETAIL", List.of()))
                .isInstanceOf(JapaneseWordAiStep.StepException.class);

        verify(aiClient, times(1)).call(any());
        verify(mapper).markGenerationFailed(eq(7L), eq("HTTP_4XX"), anyString(), anyInt());
    }

    @Test
    @DisplayName("内容が薄い応答は失敗にし、内容を書かない")
    void rejectsThinContent() {
        when(aiClient.call(any())).thenReturn(logged(JapaneseWordAiClient.CallResult.success(
                "{\"detail\":{\"senses\":[],\"examples\":[],\"collocations\":[]}}")));

        assertThatThrownBy(() -> step.run(execution(), "batC41", "DETAIL", List.of()))
                .isInstanceOf(JapaneseWordAiStep.StepException.class)
                .hasMessageContaining("すべて失敗");

        // 薄い内容も再試行する（RETRY_LIMIT=1 なので 2 回）
        verify(mapper, times(2)).markGenerationFailed(eq(7L), eq("THIN_CONTENT"), anyString(), anyInt());
        verify(detailWriter, never()).createVersion(anyLong(), anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("中断した取得を続けるときは、その生成と同じ版で問題を書く（版を増やさない）")
    void reusesTheGenerationVersionWhenResuming() {
        // 実行中の生成履歴がある（前回が途中で止まった）
        when(mapper.findActiveGeneration(anyLong(), anyString())).thenReturn(7L);
        when(mapper.findGenerationVersion(7L)).thenReturn(5);
        when(settingsService.requireSettings(anyString(), any())).thenReturn(settings("batC43"));
        when(aiClient.call(any())).thenReturn(logged(JapaneseWordAiClient.CallResult.success("""
                {"problem":{
                 "problemType":"D_CONTEXT_MEANING",
                 "questionJapanese":"意味を選んでください","targetHeading":"愛","targetReading":"あい",
                 "correctValue":"爱情",
                 "options":[{"value":"あ","reading":"あ","wrongType":"MEANING_SIMILAR"},
                            {"value":"い","reading":"い","wrongType":"MEANING_SIMILAR"},
                            {"value":"う","reading":"う","wrongType":"MEANING_SIMILAR"},
                            {"value":"爱情","reading":"あいじょう","wrongType":"MEANING_CORRECT"},
                            {"value":"え","reading":"え","wrongType":"MEANING_SIMILAR"},
                            {"value":"お","reading":"お","wrongType":"MEANING_SIMILAR"}]}
                }
                """)));

        step.run(execution(), "batC43", "D", List.of());

        ArgumentCaptor<JapaneseWordAiMapper.QuestionRow> questions =
                ArgumentCaptor.forClass(JapaneseWordAiMapper.QuestionRow.class);
        verify(mapper).insertQuestion(questions.capture());
        // 新しい版（nextGenerationVersion）は使わない
        verify(mapper, never()).nextGenerationVersion(anyLong(), anyString());
        assertThat(questions.getValue().getContentVersion()).isEqualTo(5);
    }

    @Test
    @DisplayName("対象が 0 件なら AI を呼ばない")
    void doesNothingWithoutTargets() {
        when(mapper.findTargets(anyString(), any(), anyInt())).thenReturn(List.of());

        Map<String, Object> result = step.run(execution(), "batC41", "DETAIL", List.of());

        assertThat(result.get("message").toString()).contains("対象=0件");
        verify(aiClient, never()).call(any());
        verify(mapper, never()).insertGeneration(any());
    }

    @Test
    @DisplayName("全件失敗なら例外にして、実行履歴を FAILED にする（黙って終わらせない）")
    void failsWhenEverythingFails() {
        when(aiClient.call(any())).thenReturn(
                logged(JapaneseWordAiClient.CallResult.failure("HTTP_5XX", "サーバーエラー")));

        assertThatThrownBy(() -> step.run(execution(), "batC41", "DETAIL", List.of()))
                .isInstanceOf(JapaneseWordAiStep.StepException.class)
                .hasMessageContaining("1 語すべて失敗");
    }

    @Test
    @DisplayName("取得区分ごとに、AI へ渡す内容種別と問題種別が変わる")
    void usesKindSpecificTypes() {
        when(settingsService.requireSettings(anyString(), any())).thenReturn(settings("batC43"));
        when(aiClient.call(any())).thenReturn(logged(JapaneseWordAiClient.CallResult.success("""
                {"problem":{
                 "problemType":"D_CONTEXT_MEANING",
                 "questionJapanese":"意味を選んでください","targetHeading":"愛","targetReading":"あい",
                 "sentenceJapanese":"植物に対する愛を語った。","correctValue":"喜爱",
                 "options":[{"value":"爱情","wrongType":"MEANING_SIMILAR"},
                            {"value":"恋爱","wrongType":"MEANING_SIMILAR"},
                            {"value":"喜爱","wrongType":"MEANING_CORRECT"},
                            {"value":"爱好","wrongType":"CONTEXT_MISMATCH"},
                            {"value":"爱慕","wrongType":"MEANING_SIMILAR"},
                            {"value":"关爱","wrongType":"CONTEXT_MISMATCH"}]}}
                """)));

        step.run(execution(), "batC43", "D", List.of());

        ArgumentCaptor<JapaneseWordAiMapper.GenerationRow> created =
                ArgumentCaptor.forClass(JapaneseWordAiMapper.GenerationRow.class);
        verify(mapper).insertGeneration(created.capture());
        assertThat(created.getValue().getContentType()).isEqualTo("D_CONTEXT_MEANING");
        verify(mapper).archiveQuestions(WORD_ID, "D_CONTEXT_MEANING");
        verify(mapper).insertQuestion(any());
    }

    @Test
    @DisplayName("読みが空の語は、DETAIL の成功で AI の読みを書き戻す（登録時に読みを取らないため）")
    void fillsBlankReadingFromDetail() {
        when(mapper.findTargets(anyString(), any(), anyInt())).thenReturn(List.of(targetOf(null)));
        when(aiClient.call(any())).thenReturn(logged(JapaneseWordAiClient.CallResult.success(detailJson())));

        step.run(execution(), "batC41", "DETAIL", List.of());

        verify(mapper).updateReadingIfBlank(WORD_ID, "あい");
    }

    @Test
    @DisplayName("読みが既にある語は書き戻さない（画面で入れた読みを上書きしない）")
    void keepsReadingWhenAlreadyFilled() {
        when(aiClient.call(any())).thenReturn(logged(JapaneseWordAiClient.CallResult.success(detailJson())));

        step.run(execution(), "batC41", "DETAIL", List.of());

        verify(mapper, never()).updateReadingIfBlank(anyLong(), anyString());
    }

    @Test
    @DisplayName("AI が読みを書かなければ書き戻さない（空で上書きしない）")
    void doesNotFillWithBlankReading() {
        when(mapper.findTargets(anyString(), any(), anyInt())).thenReturn(List.of(targetOf(null)));
        when(aiClient.call(any())).thenReturn(logged(JapaneseWordAiClient.CallResult.success("""
                {"detail":{
                 "coreMeaning":"爱","descriptionJa":"大切に思う気持ち。","descriptionZh":"珍视的感情。",
                 "partOfSpeech":"名詞","jlpt":"N3","conjugation":"なし","transitivity":"NONE","importance":4,
                 "senses":[{"number":1,"japanese":"大切に思う気持ち。","chinese":"爱"}],
                 "pronunciation":{"reading":"  ","hint":"低く始める。","hasAudioSample":false},
                 "examples":[{"japanese":"親の愛は無条件だ。","reading":"おやのあいはむじょうけんだ。","chinese":"父母的爱是无条件的。"},
                             {"japanese":"彼は植物を愛している。","reading":"かれはしょくぶつをあいしている。","chinese":"他热爱植物。"}],
                 "collocations":[{"expression":"愛を込める","chinese":"倾注爱意"},{"expression":"愛が深い","chinese":"爱意深厚"}],
                 "relatedWords":[],"usageNotes":[],"practices":[]}}
                """)));

        step.run(execution(), "batC41", "DETAIL", List.of());

        verify(mapper, never()).updateReadingIfBlank(anyLong(), anyString());
    }

    @Test
    @DisplayName("DETAIL が失敗したら読みを書き戻さない（中途半端に埋めない）")
    void doesNotFillReadingOnFailure() {
        when(mapper.findTargets(anyString(), any(), anyInt())).thenReturn(List.of(targetOf(null)));
        when(aiClient.call(any())).thenReturn(
                logged(JapaneseWordAiClient.CallResult.failure("HTTP_500", "サーバーエラー")));

        assertThatThrownBy(() -> step.run(execution(), "batC41", "DETAIL", List.of()))
                .isInstanceOf(JapaneseWordAiStep.StepException.class);

        verify(mapper, never()).updateReadingIfBlank(anyLong(), anyString());
    }

    @Test
    @DisplayName("同じ見出し語・同じ読みの語が既にあって書き戻せなくても、詳細の取得は成功のままにする")
    void keepsDetailWhenReadingIsAlreadyTaken() {
        when(mapper.findTargets(anyString(), any(), anyInt())).thenReturn(List.of(targetOf(null)));
        when(mapper.updateReadingIfBlank(anyLong(), anyString())).thenReturn(0);
        when(aiClient.call(any())).thenReturn(logged(JapaneseWordAiClient.CallResult.success(detailJson())));

        Map<String, Object> result = step.run(execution(), "batC41", "DETAIL", List.of());

        // 書き戻せなくても詳細は入っているので、失敗にしない（理由はログに残す）
        assertThat(result.get("message").toString()).contains("成功 1");
        verify(detailWriter).createVersion(eq(WORD_ID), anyString(), eq(7L), eq("qwen"), eq("qwen3.7-plus"));
        verify(mapper).markGenerationSucceeded(eq(7L), eq(8801L), eq(1), anyInt());
    }

    @Test
    @DisplayName("JLPT レベルが空の語は、DETAIL の成功で AI のレベルを書き戻す（一覧の絞り込みが効くように）")
    void fillsBlankJlptFromDetail() {
        when(mapper.findTargets(anyString(), any(), anyInt())).thenReturn(List.of(targetOf(null, null)));
        when(aiClient.call(any())).thenReturn(logged(JapaneseWordAiClient.CallResult.success(detailJson())));

        step.run(execution(), "batC41", "DETAIL", List.of());

        verify(mapper).updateJlptIfBlank(WORD_ID, "N3");
    }

    @Test
    @DisplayName("JLPT レベルが既にある語は書き戻さない（画面で入れた値を上書きしない）")
    void keepsExistingJlpt() {
        when(aiClient.call(any())).thenReturn(logged(JapaneseWordAiClient.CallResult.success(detailJson())));

        step.run(execution(), "batC41", "DETAIL", List.of());

        verify(mapper, never()).updateJlptIfBlank(anyLong(), anyString());
    }

    @Test
    @DisplayName("AI がレベルを書かなければ書き戻さない（N1〜N5 以外で CHECK に当てない）")
    void doesNotFillInvalidJlpt() {
        when(mapper.findTargets(anyString(), any(), anyInt())).thenReturn(List.of(targetOf(null, null)));
        when(aiClient.call(any())).thenReturn(logged(JapaneseWordAiClient.CallResult.success("""
                {"detail":{
                 "coreMeaning":"爱","descriptionJa":"大切に思う気持ち。","descriptionZh":"珍视的感情。",
                 "partOfSpeech":"名詞","jlpt":"3 級","conjugation":"なし","transitivity":"NONE","importance":4,
                 "senses":[{"number":1,"japanese":"大切に思う気持ち。","chinese":"爱"}],
                 "pronunciation":{"reading":"あい","hint":"低く始める。","hasAudioSample":false},
                 "examples":[{"japanese":"親の愛は無条件だ。","reading":"おやのあいはむじょうけんだ。","chinese":"父母的爱是无条件的。"},
                             {"japanese":"彼は植物を愛している。","reading":"かれはしょくぶつをあいしている。","chinese":"他热爱植物。"}],
                 "collocations":[{"expression":"愛を込める","chinese":"倾注爱意"},{"expression":"愛が深い","chinese":"爱意深厚"}],
                 "relatedWords":[],"usageNotes":[],"practices":[]}}
                """)));

        step.run(execution(), "batC41", "DETAIL", List.of());

        verify(mapper, never()).updateJlptIfBlank(anyLong(), anyString());
    }

    @Test
    @DisplayName("既に同じ語・同じ種別が実行中なら作らない（二重起動を防ぐ）")
    void skipsActiveGeneration() {
        when(mapper.findActiveGeneration(anyLong(), anyString())).thenReturn(9L);
        when(aiClient.call(any())).thenReturn(logged(JapaneseWordAiClient.CallResult.success(detailJson())));

        step.run(execution(), "batC41", "DETAIL", List.of());

        // 実行中の行があるので、新しい行は作らずその行を更新する
        verify(mapper, never()).insertGeneration(any());
        verify(mapper).markGenerationSucceeded(eq(9L), eq(8801L), eq(1), anyInt());
    }

    /* ---------- 働き手（非同期）が実行する 1 件 ---------- */

    /** 確保済みの 1 件（受付が積んだ行を働き手が取った形）。 */
    private static JapaneseWordAiMapper.GenerationRow claimed(long generationId, long wordId,
                                                              String contentType, int contentVersion) {
        JapaneseWordAiMapper.GenerationRow row = new JapaneseWordAiMapper.GenerationRow();
        row.setGenerationId(generationId);
        row.setWordId(wordId);
        row.setContentType(contentType);
        row.setContentVersion(contentVersion);
        return row;
    }

    @Test
    @DisplayName("働き手: 確保された 1 件を実行して成功にする（成功済みでも取り直す＝対象選定をしない）")
    void runsClaimedGeneration() {
        when(mapper.findWordTarget(WORD_ID)).thenReturn(target());
        when(aiClient.call(any())).thenReturn(logged(JapaneseWordAiClient.CallResult.success(detailJson())));

        boolean succeeded = step.runItem(claimed(77L, WORD_ID, "A_DETAIL", 3));

        assertThat(succeeded).isTrue();
        // 受付が積んだ行をそのまま使う。対象の選定（成功済みを除く SQL）は通さない
        verify(mapper, never()).findTargets(anyString(), any(), anyInt());
        verify(mapper, never()).insertGeneration(any());
        verify(mapper, never()).insertQueuedGeneration(any());
        // 実行に入ったこと（AI区分・モデル名）を記録する
        verify(mapper).markGenerationStarted(77L, "qwen", "qwen3.7-plus");
        verify(mapper).markGenerationSucceeded(eq(77L), eq(8801L), eq(1), anyInt());
        // 内容は受付が決めた版で書く（問題の行と同じ版にする規則）
        verify(detailWriter).createVersion(eq(WORD_ID), anyString(), eq(77L), eq("qwen"), eq("qwen3.7-plus"));
        verify(mapper, never()).markGenerationFailed(anyLong(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("働き手: 語が見つからなければ、AI を呼ばずに失敗として記録する")
    void failsClaimedGenerationWhenWordMissing() {
        when(mapper.findWordTarget(999L)).thenReturn(null);

        boolean succeeded = step.runItem(claimed(77L, 999L, "A_DETAIL", 1));

        assertThat(succeeded).isFalse();
        verify(aiClient, never()).call(any());
        verify(mapper).markGenerationFailed(eq(77L), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("働き手: 失敗したら FAILED を記録する（再試行は RETRY_LIMIT まで）")
    void failsClaimedGeneration() {
        when(mapper.findWordTarget(WORD_ID)).thenReturn(target());
        when(aiClient.call(any())).thenReturn(
                logged(JapaneseWordAiClient.CallResult.failure("HTTP_500", "サーバーエラー")));

        boolean succeeded = step.runItem(claimed(77L, WORD_ID, "A_DETAIL", 1));

        assertThat(succeeded).isFalse();
        // RETRY_LIMIT=1 なので 2 回呼び、最後は FAILED（例外にしない＝働き手を止めない）
        verify(aiClient, times(2)).call(any());
        verify(mapper, times(2)).markGenerationFailed(eq(77L), eq("HTTP_500"), anyString(), anyInt());
        verify(mapper, never()).markGenerationSucceeded(anyLong(), any(), anyInt(), anyInt());
        verify(detailWriter, never()).createVersion(anyLong(), anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("働き手: 設定が読めないときは、実行中のまま残さず失敗として閉じる（拾い直し続けない）")
    void failsClaimedGenerationWhenSettingsBroken() {
        // 設定が欠けていると requireSettings は例外にする（実行そのものを始めない）
        when(settingsService.requireSettings(anyString(), any()))
                .thenThrow(new com.study21.common.core.exception.ValidationException(
                        "BAT_C41_AI_PROVIDER を設定してください。"));

        boolean succeeded = step.runItem(claimed(77L, WORD_ID, "A_DETAIL", 1));

        assertThat(succeeded).isFalse();
        // AI は呼ばない。理由を残して FAILED にする（RUNNING のまま残すと
        // 働き手が 5 分ごとに拾い直して、いつまでも直らないまま回り続ける）
        verify(aiClient, never()).call(any());
        verify(mapper).markGenerationFailed(eq(77L), eq("CONFIG_ERROR"), anyString(), eq(0));
    }
}
