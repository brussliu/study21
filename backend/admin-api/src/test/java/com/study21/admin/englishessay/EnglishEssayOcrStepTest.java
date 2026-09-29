package com.study21.admin.englishessay;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.geometryai.GeometryAiConnectionResolver;
import com.study21.admin.setting.SettingsService;
import com.study21.common.core.exception.ApiException;
import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OCR（batC11 と同期の {@code POST /ocr} で同じ Step を使う）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li><b>1 回 1 枚</b>: 画像の枚数だけ AI を呼ぶ（接縫は 1 枚しか渡せない）</li>
 *   <li>プロンプトは設定の {@code _OCR_PROMPT} / {@code _OCR_USER_PROMPT}（{@code {{level}}} を置換）</li>
 *   <li>区分ごとに本文をまとめる（question は改行で連結／answer は空白で連結）</li>
 *   <li>信頼度は応答にあればそれを 0〜100 に正規化、無ければ 90。区分ごとは平均</li>
 *   <li>文字が取れない画像は黙って空を返さず、日本語の理由で失敗する</li>
 *   <li>再試行は {@code RETRY_LIMIT + 1} 回。HTTP 4xx は再試行しない</li>
 * </ol>
 */
class EnglishEssayOcrStepTest {

    private static final String TARGET = "900001";

    private EnglishEssayAiClient aiClient;
    private EnglishEssayAiMapper mapper;
    private EnglishEssayImageStore imageStore;
    private SettingsService settingsService;
    private GeometryAiConnectionResolver connectionResolver;
    private EnglishEssayImageResizer resizer;
    private EnglishEssayOcrStep step;

    @BeforeEach
    void setUp() {
        aiClient = mock(EnglishEssayAiClient.class);
        mapper = mock(EnglishEssayAiMapper.class);
        imageStore = mock(EnglishEssayImageStore.class);
        settingsService = mock(SettingsService.class);
        connectionResolver = mock(GeometryAiConnectionResolver.class);
        resizer = mock(EnglishEssayImageResizer.class);
        step = new EnglishEssayOcrStep(aiClient, mapper, imageStore, settingsService, connectionResolver, resizer);
        when(resizer.resize(any(), anyInt())).thenAnswer(invocation ->
                new EnglishEssayImageResizer.ResizedImage(invocation.getArgument(0), "image/png"));
    }

    private static EnglishEssayAiSettings.Ocr settings(int retryLimit) {
        return new EnglishEssayAiSettings.Ocr("chatgpt:1", "chatgpt:1", 8, 10, 2048, 600, retryLimit,
                0.3, 20480,
                "OCR システム {{level}}", "OCR 利用者 {{level}} 枚数 {{image_count}}",
                "タイトル システム", "タイトル 利用者 {{level}} {{question_text}}");
    }

    private static GeometryAiConnectionResolver.AiConnection connection() {
        return new GeometryAiConnectionResolver.AiConnection(
                "chatgpt", "gpt-5", "https://example.com/v1", "secret");
    }

    private static EnglishEssayOcrStep.ImageInput image(String category, String fileName, String text) {
        return new EnglishEssayOcrStep.ImageInput(category, fileName, text.getBytes(), "image/png");
    }

    /** AI 呼び出しの成功（内容は封筒を剥がした後の JSON。実際の Client がそう返す）。 */
    private static EnglishEssayAiClient.CallResult ok(String content) {
        return new EnglishEssayAiClient.CallResult(content, null, null, false, 500L);
    }

    /** AI 呼び出しの失敗。 */
    private static EnglishEssayAiClient.CallResult failure(String code, String message, boolean fatal) {
        return new EnglishEssayAiClient.CallResult(null, code, message, fatal, 501L);
    }

    @Test
    @DisplayName("1 枚ずつ AI に渡す（プロンプトは設定のものを {{level}} 置換して使う）")
    void callsOncePerImage() {
        when(aiClient.call(any())).thenReturn(
                ok("{\"pages\":[{\"questionText\":\"Do you agree?\",\"essayText\":\"\",\"confidence\":93}]}"),
                ok("{\"pages\":[{\"questionText\":\"\",\"essayText\":\"I think so.\",\"confidence\":88}]}"));

        EnglishEssayOcrStep.OcrResult result = step.recognize(12L, TARGET, EnglishEssayLevel.PRE1,
                List.of(image("question", "q.png", "q"), image("answer", "a.png", "a")),
                settings(1), connection());

        ArgumentCaptor<EnglishEssayAiClient.AiCallRequest> captor =
                ArgumentCaptor.forClass(EnglishEssayAiClient.AiCallRequest.class);
        verify(aiClient, times(2)).call(captor.capture());
        List<EnglishEssayAiClient.AiCallRequest> calls = captor.getAllValues();

        assertThat(calls.get(0).systemPrompt()).isEqualTo("OCR システム PRE1");
        assertThat(calls.get(0).userPrompt()).isEqualTo("OCR 利用者 PRE1 枚数 1");
        assertThat(calls.get(0).batchCode()).isEqualTo("batC11");
        assertThat(calls.get(0).executionId()).isEqualTo(12L);
        assertThat(calls.get(0).processKey()).isEqualTo("english-essay/900001/ocr/question-1");
        assertThat(calls.get(0).image()).containsExactly("q".getBytes());
        assertThat(calls.get(0).imageMime()).isEqualTo("image/png");
        assertThat(calls.get(0).language()).isEqualTo("en");
        assertThat(calls.get(0).timeoutSeconds()).isEqualTo(600);
        // 実行パラメータは設定の値（Temperature / 最大出力Token数）を渡す
        assertThat(calls.get(0).temperature()).isEqualTo(0.3);
        assertThat(calls.get(0).maxCompletionTokens()).isEqualTo(20480);
        assertThat(calls.get(1).processKey()).isEqualTo("english-essay/900001/ocr/answer-2");

        assertThat(result.questionText()).isEqualTo("Do you agree?");
        assertThat(result.essayText()).isEqualTo("I think so.");
        assertThat(result.questionConfidence()).isEqualTo(93);
        assertThat(result.essayConfidence()).isEqualTo(88);
        assertThat(result.pages()).extracting(EnglishEssayOcrStep.PageResult::category)
                .containsExactly("question", "answer");
    }

    @Test
    @DisplayName("区分ごとにまとめる（question は改行・answer は空白で連結し、信頼度は区分ごとの平均）")
    void aggregatesByCategory() {
        when(aiClient.call(any())).thenReturn(
                ok("{\"pages\":[{\"questionText\":\"Line1\",\"confidence\":0.9}]}"),
                ok("{\"pages\":[{\"questionText\":\"Line2\",\"confidence\":0.8}]}"),
                ok("{\"pages\":[{\"essayText\":\"First page\"}]}"),
                ok("{\"pages\":[{\"essayText\":\"Second   page\"}]}"));

        EnglishEssayOcrStep.OcrResult result = step.recognize(null, TARGET, EnglishEssayLevel.GRADE2,
                List.of(image("question", "q1.png", "1"), image("question", "q2.png", "2"),
                        image("answer", "a1.png", "3"), image("answer", "a2.png", "4")),
                settings(0), connection());

        assertThat(result.questionText()).isEqualTo("Line1\nLine2");
        // 空白の連続は 1 つに畳む（手書きの改行を 1 本の作文にする）
        assertThat(result.essayText()).isEqualTo("First page Second page");
        assertThat(result.pages()).hasSize(4);
        assertThat(result.questionConfidence()).isEqualTo(85);
        // 応答に信頼度が無ければ 90（画像ごとの決め打ちにしない）
        assertThat(result.essayConfidence()).isEqualTo(90);
        assertThat(result.pages().get(2).confidence()).isEqualTo(90);
    }

    @Test
    @DisplayName("文字が取れない画像は日本語の理由で失敗する（黙って空を返さない）")
    void rejectsBlankText() {
        when(aiClient.call(any())).thenReturn(ok("{\"pages\":[{\"questionText\":\"\",\"confidence\":90}]}"));

        assertThatThrownBy(() -> step.recognize(null, TARGET, EnglishEssayLevel.PRE1,
                List.of(image("question", "q.png", "q")), settings(0), connection()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("読み取れません");
    }

    @Test
    @DisplayName("再試行は RETRY_LIMIT + 1 回（サーバーエラーは待ってやり直す）")
    void retriesOnServerError() {
        when(aiClient.call(any())).thenReturn(
                failure("HTTP_500", "サーバーエラー", false),
                ok("{\"pages\":[{\"questionText\":\"Recovered\",\"confidence\":90}]}"));

        EnglishEssayOcrStep.OcrResult result = step.recognize(null, TARGET, EnglishEssayLevel.PRE1,
                List.of(image("question", "q.png", "q")), settings(1), connection());

        assertThat(result.questionText()).isEqualTo("Recovered");
        verify(aiClient, times(2)).call(any());
    }

    @Test
    @DisplayName("HTTP 4xx は再試行しない（待っても直らない）")
    void doesNotRetryOn4xx() {
        when(aiClient.call(any())).thenReturn(
                failure("HTTP_4XX", "API Key が不正です", true));

        assertThatThrownBy(() -> step.recognize(null, TARGET, EnglishEssayLevel.PRE1,
                List.of(image("question", "q.png", "q")), settings(3), connection()))
                .isInstanceOf(ApiException.class);

        verify(aiClient, times(1)).call(any());
    }

    @Test
    @DisplayName("題（日本語・中国語）を生成する（設問文を {{question_text}} へ入れる）")
    void generatesTitle() {
        when(aiClient.call(any())).thenReturn(ok("{\"titleJa\":\"部活動の時間\",\"titleZh\":\"社团活动时间\"}"));

        EnglishEssayOcrStep.TitleResult title = step.generateTitle(12L, TARGET, EnglishEssayLevel.GRADE1,
                "Do you agree?", settings(0), connection());

        assertThat(title.titleJa()).isEqualTo("部活動の時間");
        assertThat(title.titleZh()).isEqualTo("社团活动时间");

        ArgumentCaptor<EnglishEssayAiClient.AiCallRequest> captor =
                ArgumentCaptor.forClass(EnglishEssayAiClient.AiCallRequest.class);
        verify(aiClient).call(captor.capture());
        assertThat(captor.getValue().systemPrompt()).isEqualTo("タイトル システム");
        assertThat(captor.getValue().userPrompt()).isEqualTo("タイトル 利用者 GRADE1 Do you agree?");
        assertThat(captor.getValue().processKey()).isEqualTo("english-essay/900001/title");
    }

    @Test
    @DisplayName("題が空なら失敗（画面に見出しが無い状態を作らない）")
    void rejectsBlankTitle() {
        when(aiClient.call(any())).thenReturn(ok("{\"titleJa\":\"\",\"titleZh\":\"\"}"));

        assertThatThrownBy(() -> step.generateTitle(null, TARGET, EnglishEssayLevel.GRADE1,
                "Do you agree?", settings(0), connection()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("タイトル");
    }

    @Test
    @DisplayName("画像が多すぎる・大きすぎるときは受付を拒否する（設定の上限）")
    void rejectsTooManyOrTooLargeImages() {
        // 枚数 1 枚・1 枚 1MB の設定
        EnglishEssayAiSettings.Ocr one = new EnglishEssayAiSettings.Ocr("chatgpt:1", "chatgpt:1", 1, 1,
                2048, 600, 0, 0.0, 4096, "s", "u", "ts", "tu");

        assertThatThrownBy(() -> EnglishEssayOcrStep.validateRequest(
                List.of(image("question", "q1.png", "1"), image("question", "q2.png", "2")), one))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("1 枚")
                .hasMessageContaining("2 枚");

        EnglishEssayOcrStep.ImageInput big = new EnglishEssayOcrStep.ImageInput(
                "answer", "big.png", new byte[2 * 1024 * 1024], "image/png");
        assertThatThrownBy(() -> EnglishEssayOcrStep.validateRequest(List.of(big), one))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("big.png")
                .hasMessageContaining("MB");

        assertThatThrownBy(() -> EnglishEssayOcrStep.validateRequest(List.of(), one))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("画像");
    }

    @Test
    @DisplayName("batC11: DB の画像を読んで OCR し、区分ごとの本文・語数・題を書き戻す")
    void runWritesRecognizedTextAndTitle() {
        when(settingsService.requireSettings("batC11", EnglishEssayAiSettings.requirements("batC11")))
                .thenReturn(Map.of());
        when(settingsService.findGlobal("ENGLISH_ESSAY", "ENGLISH_ESSAY_ENABLED"))
                .thenReturn(java.util.Optional.of("true"));
        when(connectionResolver.resolve("batC11", "chatgpt:1")).thenReturn(connection());
        when(aiClient.call(any())).thenReturn(
                ok("{\"pages\":[{\"questionText\":\"Do you agree?\",\"confidence\":95}]}"),
                ok("{\"pages\":[{\"essayText\":\"I think so because it is good.\"}]}"),
                ok("{\"titleJa\":\"学校生活\",\"titleZh\":\"学校生活\"}"));

        when(mapper.findEssay(900001L)).thenReturn(essay());
        when(mapper.listEssayImages(900001L)).thenReturn(List.of(
                imageRow(11L, "question", 1, "q.png"), imageRow(12L, "answer", 2, "a.png")));
        when(imageStore.read("english-essay/2/202609", "q.png")).thenReturn("q".getBytes());
        when(imageStore.read("english-essay/2/202609", "a.png")).thenReturn("a".getBytes());

        // OCR の設定は settingsService の戻り値からは作れないので、Step 自身に解決させる
        // （requireSettings の戻り値を OCR の値にする）
        when(settingsService.requireSettings("batC11", EnglishEssayAiSettings.requirements("batC11")))
                .thenReturn(ocrValues());

        Map<String, Object> result = step.run(execution(), 900001L);

        verify(mapper).updateImageRecognized(11L, "Do you agree?", 95);
        verify(mapper).updateImageRecognized(12L, "I think so because it is good.", 90);
        // 語数は数え直して保存する（一覧・検索が使う）
        verify(mapper).updateEssayRecognized(900001L, "Do you agree?",
                "I think so because it is good.", 7);
        verify(mapper).updateEssayTitle(900001L, "学校生活", "学校生活");
        assertThat(String.valueOf(result.get("message"))).contains("OCR");

        // 設定の Temperature / 最大出力Token数が AI 呼び出し（OCR と題）に渡る
        ArgumentCaptor<EnglishEssayAiClient.AiCallRequest> call =
                ArgumentCaptor.forClass(EnglishEssayAiClient.AiCallRequest.class);
        verify(aiClient, times(3)).call(call.capture());
        assertThat(call.getAllValues()).allSatisfy(request -> {
            assertThat(request.temperature()).isEqualTo(0.4);
            assertThat(request.maxCompletionTokens()).isEqualTo(24576);
        });
    }

    @Test
    @DisplayName("batC11: Temperature / 最大出力Token数が未設定・不正なら既定（0.0 / 4096）で呼ぶ")
    void fallsBackToDefaultAiParameters() {
        Map<String, String> values = ocrValues();
        values.put("ENGLISH_ESSAY_OCR_TEMPERATURE", "3.5");            // 範囲外（0.0〜2.0）
        values.put("ENGLISH_ESSAY_OCR_MAX_COMPLETION_TOKENS", "x");    // 数値でない
        when(settingsService.requireSettings("batC11", EnglishEssayAiSettings.requirements("batC11")))
                .thenReturn(values);
        when(connectionResolver.resolve("batC11", "chatgpt:1")).thenReturn(connection());
        when(aiClient.call(any())).thenReturn(
                ok("{\"pages\":[{\"questionText\":\"Do you agree?\",\"confidence\":95}]}"),
                ok("{\"pages\":[{\"essayText\":\"I think so.\"}]}"),
                ok("{\"titleJa\":\"学校生活\",\"titleZh\":\"学校生活\"}"));

        when(mapper.findEssay(900001L)).thenReturn(essay());
        when(mapper.listEssayImages(900001L)).thenReturn(List.of(
                imageRow(11L, "question", 1, "q.png"), imageRow(12L, "answer", 2, "a.png")));
        when(imageStore.read("english-essay/2/202609", "q.png")).thenReturn("q".getBytes());
        when(imageStore.read("english-essay/2/202609", "a.png")).thenReturn("a".getBytes());

        step.run(execution(), 900001L);

        ArgumentCaptor<EnglishEssayAiClient.AiCallRequest> call =
                ArgumentCaptor.forClass(EnglishEssayAiClient.AiCallRequest.class);
        verify(aiClient, times(3)).call(call.capture());
        assertThat(call.getAllValues()).allSatisfy(request -> {
            assertThat(request.temperature()).isEqualTo(0.0);
            assertThat(request.maxCompletionTokens()).isEqualTo(4096);
        });
    }

    @Test
    @DisplayName("batC11: 画像が無い作文は失敗にする")
    void runRejectsEssayWithoutImages() {
        when(mapper.findEssay(900001L)).thenReturn(essay());
        when(mapper.listEssayImages(900001L)).thenReturn(List.of());

        assertThatThrownBy(() -> step.run(execution(), 900001L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("画像");
        verify(aiClient, never()).call(any());
    }

    @Test
    @DisplayName("batC11: 作文が見つからないときは失敗にする")
    void runRejectsUnknownEssay() {
        when(mapper.findEssay(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> step.run(execution(), 900001L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("見つかりません");
    }

    private static Map<String, String> ocrValues() {
        Map<String, String> values = new java.util.HashMap<>();
        values.put("ENGLISH_ESSAY_ENABLED", "true");
        values.put("ENGLISH_ESSAY_OCR_AI_PROVIDER", "chatgpt:1");
        values.put("ENGLISH_ESSAY_TITLE_AI_PROVIDER", "chatgpt:1");
        values.put("ENGLISH_ESSAY_MAX_IMAGES", "8");
        values.put("ENGLISH_ESSAY_MAX_IMAGE_MB", "10");
        values.put("ENGLISH_ESSAY_OCR_MAX_IMAGE_PIXELS", "2048");
        values.put("ENGLISH_ESSAY_OCR_REQUEST_TIMEOUT_SECONDS", "600");
        values.put("ENGLISH_ESSAY_OCR_RETRY_LIMIT", "0");
        values.put("ENGLISH_ESSAY_OCR_MAX_COMPLETION_TOKENS", "24576");
        values.put("ENGLISH_ESSAY_OCR_TEMPERATURE", "0.4");
        values.put("ENGLISH_ESSAY_OCR_PROMPT", "OCR システム {{level}}");
        values.put("ENGLISH_ESSAY_OCR_USER_PROMPT", "OCR 利用者 {{level}} 枚数 {{image_count}}");
        values.put("ENGLISH_ESSAY_TITLE_PROMPT", "タイトル システム");
        values.put("ENGLISH_ESSAY_TITLE_USER_PROMPT", "タイトル 利用者 {{level}} {{question_text}}");
        return values;
    }

    private static EnglishEssayAiMapper.EssayRow essay() {
        EnglishEssayAiMapper.EssayRow essay = new EnglishEssayAiMapper.EssayRow();
        essay.setEssayId(900001L);
        essay.setUserAccountId(2L);
        essay.setLevel("PRE1");
        essay.setTitleJa("古い題");
        essay.setTitleZh("旧标题");
        essay.setWordCount(0);
        essay.setStateCode("A");
        return essay;
    }

    private static EnglishEssayAiMapper.EssayImageRow imageRow(long imageId, String category, int orderNo,
                                                               String fileName) {
        EnglishEssayAiMapper.EssayImageRow row = new EnglishEssayAiMapper.EssayImageRow();
        row.setImageId(imageId);
        row.setCategory(category);
        row.setOrderNo(orderNo);
        row.setSavedFileName(fileName);
        row.setRelativePath("english-essay/2/202609");
        row.setMimeType("image/png");
        return row;
    }

    private static BatchExecutionEntity execution() {
        BatchExecutionEntity entity = new BatchExecutionEntity();
        entity.setExecutionId(12L);
        return entity;
    }
}
