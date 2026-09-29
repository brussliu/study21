package com.study21.admin.englishessay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.geometryai.GeometryAiConnectionResolver;
import com.study21.admin.setting.SettingsService;
import com.study21.common.core.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 英作文の画像を文字にする（同期の {@code POST /ocr} と {@code batC11} が<b>同じ道</b>を通る）。
 *
 * <p>接縫（{@code GeometryAiClient}）は <b>1 回 1 枚</b>しか渡せないので、ここで 1 枚ずつ呼び、
 * 区分ごとにまとめる:</p>
 * <ul>
 *   <li>設問（question）は<b>改行で連結</b>する（設問文は行の構造を保つ）</li>
 *   <li>答案（answer）は<b>空白で連結</b>する（複数枚の手書きを 1 本の作文にする）</li>
 *   <li>信頼度は AI の応答にあればそれを 0〜100 に直し、無ければ 90。区分ごとは平均</li>
 * </ul>
 *
 * <p>プロンプトは設定の {@code ENGLISH_ESSAY_OCR_PROMPT} / {@code _OCR_USER_PROMPT} をそのまま使い、
 * {@code {{level}}} / {@code {{image_count}}} / {@code {{image_categories}}} を埋める
 * （1 回 1 枚なので {@code image_count} は 1）。</p>
 *
 * <p>Temperature と最大出力Token数も設定（{@code _OCR_TEMPERATURE} /
 * {@code _OCR_MAX_COMPLETION_TOKENS}）から読む。題の生成も<b>同じ値</b>を使う（2 つの呼び出しで
 * 別々の設定を持たない）。設定が無い・不正なときは {@link EnglishEssayAiSettings} の既定に落ちる。</p>
 *
 * <p>文字が取れない画像は<b>黙って空を返さない</b>。{@code OCR_RETRY_LIMIT} まで再試行し、
 * それでも駄目なら日本語の理由で失敗する（HTTP 4xx は再試行しない）。</p>
 */
@Component
public class EnglishEssayOcrStep {

    private static final Logger log = LoggerFactory.getLogger(EnglishEssayOcrStep.class);

    /** {@code batC11}（この Step を使うバッチ）。 */
    public static final String BATCH_CODE = EnglishEssayAiSettings.OCR_BATCH_CODE;

    /** 再試行の待ち（2.0 と同じ 2s / 4s / 8s、上限 8 秒）。 */
    private static final long[] BACKOFF_MS = {2_000L, 4_000L, 8_000L};

    /** 答案をまとめるときに空白の連続を 1 つにする。 */
    private static final java.util.regex.Pattern WHITESPACE = java.util.regex.Pattern.compile("\\s+");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EnglishEssayAiClient aiClient;
    private final EnglishEssayAiMapper mapper;
    private final EnglishEssayImageStore imageStore;
    private final SettingsService settingsService;
    private final GeometryAiConnectionResolver connectionResolver;
    private final EnglishEssayImageResizer resizer;

    public EnglishEssayOcrStep(EnglishEssayAiClient aiClient,
                               EnglishEssayAiMapper mapper,
                               EnglishEssayImageStore imageStore,
                               SettingsService settingsService,
                               GeometryAiConnectionResolver connectionResolver,
                               EnglishEssayImageResizer resizer) {
        this.aiClient = aiClient;
        this.mapper = mapper;
        this.imageStore = imageStore;
        this.settingsService = settingsService;
        this.connectionResolver = connectionResolver;
        this.resizer = resizer;
    }

    /** AI に渡す 1 枚（画面から受けた生のバイト列。縮小はこの中で行う）。 */
    public record ImageInput(String category, String fileName, byte[] bytes, String mime) {
    }

    /** 1 枚ぶんの結果（画面の {@code pages[]}）。 */
    public record PageResult(String category, String text, int confidence) {
    }

    /** まとめた結果（同期エンドポイントの応答と同じ形）。 */
    public record OcrResult(String questionText, String essayText, List<PageResult> pages,
                            int questionConfidence, int essayConfidence) {
    }

    /** 生成した題。 */
    public record TitleResult(String titleJa, String titleZh) {
    }

    /**
     * 画面から受けた画像を文字にする（{@code POST /ocr}）。
     *
     * <p>設定の解決と上限（枚数・1 枚の大きさ）の検証もここで行う（入口を薄く保つ）。</p>
     */
    public OcrResult recognizeRequest(Long executionId, EnglishEssayLevel level, List<ImageInput> images) {
        EnglishEssayAiSettings.Ocr settings = EnglishEssayAiSettings.ocr(
                settingsService.requireSettings(BATCH_CODE, EnglishEssayAiSettings.requirements(BATCH_CODE)));
        validateRequest(images, settings);
        GeometryAiConnectionResolver.AiConnection connection =
                connectionResolver.resolve(BATCH_CODE, settings.provider());
        return recognize(executionId, "request", level, images, settings, connection);
    }

    /**
     * 画像の枚数・1 枚の大きさを設定の上限と突き合わせる。
     *
     * @throws ValidationException 上限を超えているとき（日本語の理由）
     */
    static void validateRequest(List<ImageInput> images, EnglishEssayAiSettings.Ocr settings) {
        if (images == null || images.isEmpty()) {
            throw new ValidationException("画像を選んでください。");
        }
        if (images.size() > settings.maxImages()) {
            throw new ValidationException("画像は " + settings.maxImages() + " 枚までです（"
                    + images.size() + " 枚受け取りました）。設定ページの「最大画像枚数」を確認してください。");
        }
        long limitBytes = (long) settings.maxImageMb() * 1024 * 1024;
        for (ImageInput image : images) {
            if (image.bytes() == null || image.bytes().length == 0) {
                throw new ValidationException("画像「" + image.fileName() + "」を読み込めませんでした。");
            }
            if (image.bytes().length > limitBytes) {
                throw new ValidationException("画像「" + image.fileName() + "」のサイズが上限（"
                        + settings.maxImageMb() + "MB）を超えています。");
            }
        }
    }

    /**
     * 1 枚ずつ AI に渡して文字を取る（同期と batC11 が同じ道を通る）。
     *
     * @param targetKey 呼出履歴の処理キーに使う目印（作文ID か {@code request}）
     */
    public OcrResult recognize(Long executionId, String targetKey, EnglishEssayLevel level,
                               List<ImageInput> images, EnglishEssayAiSettings.Ocr settings,
                               GeometryAiConnectionResolver.AiConnection connection) {
        List<PageResult> pages = new ArrayList<>();
        for (int index = 0; index < images.size(); index += 1) {
            pages.add(recognizeOne(executionId, targetKey, level, images.get(index), index + 1,
                    settings, connection));
        }
        return aggregate(pages);
    }

    /** 題（日本語・中国語）を生成する。 */
    public TitleResult generateTitle(Long executionId, String targetKey, EnglishEssayLevel level,
                                     String questionText, EnglishEssayAiSettings.Ocr settings,
                                     GeometryAiConnectionResolver.AiConnection connection) {
        String systemPrompt = EnglishEssayAiPrompt.render(settings.titleSystemPrompt(),
                Map.of("level", level.name()));
        String userPrompt = EnglishEssayAiPrompt.render(settings.titleUserPrompt(),
                Map.of("level", level.name(), "question_text", nullToEmpty(questionText)));

        int attempts = settings.attempts();
        String lastReason = null;
        for (int attempt = 0; attempt < attempts; attempt += 1) {
            EnglishEssayAiClient.CallResult call = aiClient.call(new EnglishEssayAiClient.AiCallRequest(
                    BATCH_CODE, executionId,
                    "english-essay/" + targetKey + "/title", "ja-zh",
                    connection.provider(), connection.model(), connection.url(), connection.apiKey(),
                    systemPrompt, userPrompt, null, null,
                    settings.timeoutSeconds(), settings.temperature(), settings.maxCompletionTokens()));
            if (call.isSuccess()) {
                try {
                    JsonNode root = MAPPER.readTree(call.content());
                    String titleJa = text(root.path("titleJa"));
                    String titleZh = text(root.path("titleZh"));
                    if (!titleJa.isEmpty() && !titleZh.isEmpty()) {
                        return new TitleResult(titleJa, titleZh);
                    }
                    lastReason = "AI の応答にタイトル（titleJa / titleZh）がありません。";
                } catch (Exception cause) {
                    lastReason = "AI の応答が JSON ではありません。";
                }
            } else {
                lastReason = reasonOf(call);
                if (call.fatal()) {
                    break;
                }
            }
            if (attempt + 1 < attempts) {
                sleepBackoff(attempt);
            }
        }
        throw new EnglishEssayAiException("作文のタイトルを生成できませんでした: " + lastReason);
    }

    /**
     * {@code batC11}: DB の画像を読んで OCR し、設問文・作文本文・語数と題を書き戻す。
     *
     * @return 実行履歴の メッセージ に残す要約
     */
    public Map<String, Object> run(BatchExecutionEntity execution, long essayId) {
        EnglishEssayAiMapper.EssayRow essay = mapper.findEssay(essayId);
        if (essay == null) {
            throw new ValidationException("英作文が見つかりません（削除された可能性があります）: " + essayId);
        }
        List<EnglishEssayAiMapper.EssayImageRow> rows = mapper.listEssayImages(essayId);
        if (rows.isEmpty()) {
            throw new ValidationException("英作文の画像がありません（先に画像を登録してください）: " + essayId);
        }
        EnglishEssayAiSettings.Ocr settings = EnglishEssayAiSettings.ocr(
                settingsService.requireSettings(BATCH_CODE, EnglishEssayAiSettings.requirements(BATCH_CODE)));

        List<ImageInput> images = new ArrayList<>();
        for (EnglishEssayAiMapper.EssayImageRow row : rows) {
            byte[] bytes = imageStore.read(row.getRelativePath(), row.getSavedFileName());
            if (bytes == null || bytes.length == 0) {
                throw new ValidationException("画像ファイルが見つかりません: "
                        + row.getRelativePath() + "/" + row.getSavedFileName());
            }
            images.add(new ImageInput(row.getCategory(), row.getSavedFileName(), bytes, row.getMimeType()));
        }
        validateRequest(images, settings);

        EnglishEssayLevel level = EnglishEssayLevel.parse(essay.getLevel());
        Long executionId = execution == null ? null : execution.getExecutionId();
        String targetKey = String.valueOf(essayId);
        GeometryAiConnectionResolver.AiConnection connection =
                connectionResolver.resolve(BATCH_CODE, settings.provider());

        OcrResult result = recognize(executionId, targetKey, level, images, settings, connection);
        for (int index = 0; index < rows.size(); index += 1) {
            PageResult page = result.pages().get(index);
            mapper.updateImageRecognized(rows.get(index).getImageId(), page.text(), page.confidence());
        }
        int wordCount = EnglishEssayGradingReport.wordCountOf(result.essayText());
        mapper.updateEssayRecognized(essayId, result.questionText(), result.essayText(), wordCount);

        String titleNote;
        try {
            GeometryAiConnectionResolver.AiConnection titleConnection =
                    connectionResolver.resolve(BATCH_CODE, settings.titleProvider());
            TitleResult title = generateTitle(executionId, targetKey, level, result.questionText(),
                    settings, titleConnection);
            mapper.updateEssayTitle(essayId, title.titleJa(), title.titleZh());
            titleNote = " 題: " + title.titleJa();
        } catch (RuntimeException cause) {
            // 題が作れなくても OCR の結果は残す（画面で利用者が直せる）。理由は実行ログに残す
            log.warn("英作文の題を生成できませんでした。essayId={} reason={}", essayId, cause.getMessage());
            titleNote = " 題の生成に失敗しました（理由は実行ログ）。";
        }

        long questionPages = result.pages().stream().filter(page -> "question".equals(page.category())).count();
        long answerPages = result.pages().size() - questionPages;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("message", "英作文 OCR: 設問 " + questionPages + " 枚 / 答案 " + answerPages
                + " 枚を読み取りました（語数 " + wordCount + "）。" + titleNote);
        summary.put("essayId", essayId);
        summary.put("questionPages", questionPages);
        summary.put("answerPages", answerPages);
        summary.put("wordCount", wordCount);
        return summary;
    }

    /* ===================================================== 内部 */

    /** 1 枚ぶん（再試行つき）。 */
    private PageResult recognizeOne(Long executionId, String targetKey, EnglishEssayLevel level,
                                    ImageInput image, int order, EnglishEssayAiSettings.Ocr settings,
                                    GeometryAiConnectionResolver.AiConnection connection) {
        String systemPrompt = EnglishEssayAiPrompt.render(settings.systemPrompt(),
                Map.of("level", level.name()));
        String userPrompt = EnglishEssayAiPrompt.render(settings.userPrompt(), Map.of(
                "level", level.name(),
                "image_count", "1",
                "image_categories", nullToEmpty(image.category())));

        EnglishEssayImageResizer.ResizedImage prepared;
        try {
            prepared = resizer.resize(image.bytes(), settings.maxImagePixels());
        } catch (IllegalArgumentException cause) {
            throw new ValidationException("画像「" + image.fileName() + "」を読み込めませんでした。"
                    + "（JPEG / PNG などの画像を選んでください）");
        }

        int attempts = settings.attempts();
        String lastReason = null;
        for (int attempt = 0; attempt < attempts; attempt += 1) {
            EnglishEssayAiClient.CallResult call = aiClient.call(new EnglishEssayAiClient.AiCallRequest(
                    BATCH_CODE, executionId,
                    "english-essay/" + targetKey + "/ocr/" + nullToEmpty(image.category()) + "-" + order, "en",
                    connection.provider(), connection.model(), connection.url(), connection.apiKey(),
                    systemPrompt, userPrompt, prepared.bytes(), prepared.mime(),
                    settings.timeoutSeconds(), settings.temperature(), settings.maxCompletionTokens()));
            if (call.isSuccess()) {
                try {
                    return pageOf(call.content(), image.category());
                } catch (InvalidOcrResponseException cause) {
                    lastReason = cause.getMessage();
                }
            } else {
                lastReason = reasonOf(call);
                if (call.fatal()) {
                    break;
                }
            }
            if (attempt + 1 < attempts) {
                sleepBackoff(attempt);
            }
        }
        throw new EnglishEssayAiException("画像「" + image.fileName()
                + "」から文字を読み取れませんでした: " + lastReason);
    }

    /** 1 枚ぶんの応答を、その区分の本文と信頼度にする。 */
    private static PageResult pageOf(String content, String category) {
        JsonNode root;
        try {
            root = MAPPER.readTree(content);
        } catch (Exception cause) {
            throw new InvalidOcrResponseException("AI の応答が JSON ではありません。");
        }
        // 1 枚ずつ渡しているので、pages[0] があればそれを使う（無ければ応答そのもの）
        JsonNode pages = root.path("pages");
        JsonNode page = pages.isArray() && !pages.isEmpty() ? pages.path(0) : root;

        boolean question = "question".equals(category);
        String text = text(page.path(question ? "questionText" : "essayText"));
        if (text.isEmpty()) {
            text = text(root.path(question ? "questionText" : "essayText"));
        }
        if (text.isEmpty()) {
            throw new InvalidOcrResponseException("画像から文字を読み取れませんでした。");
        }
        int confidence = confidenceOf(page.path("confidence"), root, category);
        return new PageResult(category, text, confidence);
    }

    /**
     * 信頼度（0〜100）。
     *
     * <p>応答の値は 0〜1 のことも 0〜100 のこともある（プロンプトの例は 0 始まり）。
     * 1 以下なら割合として 100 倍する。値が無い・0 以下なら 90（画像ごとの決め打ちはしない）。</p>
     */
    private static int confidenceOf(JsonNode pageConfidence, JsonNode root, String category) {
        Double value = null;
        if (pageConfidence.isNumber()) {
            value = pageConfidence.asDouble();
        } else {
            JsonNode top = root.path("question".equals(category) ? "questionConfidence" : "essayConfidence");
            if (top.isNumber()) {
                value = top.asDouble();
            }
        }
        if (value == null || value <= 0) {
            return 90;
        }
        double percent = value <= 1.0 ? value * 100 : value;
        return (int) Math.max(0, Math.min(100, Math.round(percent)));
    }

    /** 区分ごとにまとめる（question は改行・answer は空白）。 */
    private static OcrResult aggregate(List<PageResult> pages) {
        List<String> questions = new ArrayList<>();
        List<String> answers = new ArrayList<>();
        for (PageResult page : pages) {
            if ("question".equals(page.category())) {
                questions.add(page.text());
            } else {
                answers.add(WHITESPACE.matcher(page.text()).replaceAll(" ").trim());
            }
        }
        return new OcrResult(
                String.join("\n", questions),
                String.join(" ", answers),
                List.copyOf(pages),
                averageConfidence(pages, "question"),
                averageConfidence(pages, "answer"));
    }

    /** 区分ごとの平均の信頼度（その区分の画像が無ければ 90）。 */
    private static int averageConfidence(List<PageResult> pages, String category) {
        long sum = 0;
        int count = 0;
        for (PageResult page : pages) {
            if (category.equals(page.category())) {
                sum += page.confidence();
                count += 1;
            }
        }
        if (count == 0) {
            return 90;
        }
        return (int) Math.round((double) sum / count);
    }

    private static String reasonOf(EnglishEssayAiClient.CallResult call) {
        String code = call.errorCode() == null ? "" : call.errorCode() + " ";
        return code + nullToEmpty(call.errorMessage());
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.asText("").trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void sleepBackoff(int attempt) {
        long wait = BACKOFF_MS[Math.min(attempt, BACKOFF_MS.length - 1)];
        try {
            Thread.sleep(wait);
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
        }
    }

    /** 応答の中身が読めなかった（再試行する価値がある）。 */
    private static class InvalidOcrResponseException extends RuntimeException {

        InvalidOcrResponseException(String message) {
            super(message);
        }
    }
}
