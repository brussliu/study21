package com.study21.admin.englishessay;

import com.study21.admin.geometryai.GeometryAiConnectionResolver;
import com.study21.admin.setting.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 英検基準の AI 添削（{@code batC12} と働き手が<b>同じ道</b>を通る）。
 *
 * <p>流れ:</p>
 * <ol>
 *   <li>設定の {@code ENGLISH_ESSAY_GRADING_*} を解決する（<b>プロンプトと接続先は既定値を持たない</b>。
 *       Temperature と最大出力Token数だけは、設定が無い・不正なときに
 *       {@link EnglishEssayAiSettings} の既定（0.2 / 8192）へ落ちる）</li>
 *   <li>設定のプロンプトで AI を呼ぶ（{@code {{level}}} / {@code {{question_text}}} /
 *       {@code {{essay_text}}} / {@code {{word_count}}} を置換）</li>
 *   <li>応答を検証して 2.0 と同一のレポート形にする（{@link EnglishEssayGradingReport}）</li>
 *   <li>成功なら {@code SUCCEEDED} + 得点 + 結果JSON + 呼出履歴ID、失敗なら {@code FAILED} + 日本語の理由</li>
 * </ol>
 *
 * <p><b>例外にしない</b>（働き手を止めない。理由は添削履歴の {@code FAILED} と実行ログに残る）。
 * 設定が読めないときも<b>実行中のまま残さない</b>（残すと働き手が拾い直して回り続ける）。</p>
 */
@Component
public class EnglishEssayGradingStep {

    private static final Logger log = LoggerFactory.getLogger(EnglishEssayGradingStep.class);

    /** {@code batC12}（この Step を使うバッチ）。 */
    public static final String BATCH_CODE = EnglishEssayAiSettings.GRADING_BATCH_CODE;

    /** {@code 失敗理由} の列（VARCHAR(500)）に収める。 */
    private static final int FAILURE_REASON_LIMIT = 500;

    private final EnglishEssayAiClient aiClient;
    private final EnglishEssayAiMapper mapper;
    private final SettingsService settingsService;
    private final GeometryAiConnectionResolver connectionResolver;

    public EnglishEssayGradingStep(EnglishEssayAiClient aiClient,
                                   EnglishEssayAiMapper mapper,
                                   SettingsService settingsService,
                                   GeometryAiConnectionResolver connectionResolver) {
        this.aiClient = aiClient;
        this.mapper = mapper;
        this.settingsService = settingsService;
        this.connectionResolver = connectionResolver;
    }

    /** 働き手の 1 件（受付が積んだ写し）。 */
    public boolean runItem(EnglishEssayAiMapper.GradingRow item) {
        return runItem(item, null);
    }

    /**
     * 1 件を添削する。
     *
     * @param executionId 実行履歴の ID（バッチから走るときだけ入る。画面の受付は null）
     * @return 成功したか（失敗の理由は添削履歴に入っている）
     */
    public boolean runItem(EnglishEssayAiMapper.GradingRow item, Long executionId) {
        EnglishEssayAiSettings.Grading settings;
        GeometryAiConnectionResolver.AiConnection connection;
        EnglishEssayLevel level;
        try {
            settings = EnglishEssayAiSettings.grading(settingsService.requireSettings(
                    BATCH_CODE, EnglishEssayAiSettings.requirements(BATCH_CODE)));
            connection = connectionResolver.resolve(BATCH_CODE, settings.provider());
            level = EnglishEssayLevel.parse(item.getLevel());
        } catch (RuntimeException cause) {
            fail(item.getGradingId(), "CONFIG_ERROR: " + messageOf(cause));
            return false;
        }

        String lastReason = null;
        int attempts = settings.attempts();
        for (int attempt = 0; attempt < attempts; attempt += 1) {
            EnglishEssayAiClient.CallResult call = aiClient.call(new EnglishEssayAiClient.AiCallRequest(
                    BATCH_CODE, executionId,
                    "english-essay/" + item.getEssayId() + "/grading/round-" + item.getRound(), "ja-zh",
                    connection.provider(), connection.model(), connection.url(), connection.apiKey(),
                    promptOf(settings.systemPrompt(), level, item, false),
                    promptOf(settings.userPrompt(), level, item, true),
                    null, null,
                    settings.timeoutSeconds(), settings.temperature(), settings.maxCompletionTokens()));
            if (!call.isSuccess()) {
                lastReason = reasonOf(call);
                if (call.fatal()) {
                    break;
                }
            } else {
                EnglishEssayGradingReport.Result report = EnglishEssayGradingReport.build(
                        call.content(), level, item.getWordCount());
                if (report.success()) {
                    mapper.markGradingSucceeded(item.getGradingId(), report.score(), report.maxScore(),
                            report.reportJson(), call.callLogId());
                    log.info("english essay graded. gradingId={} essayId={} round={} score={}/{}",
                            item.getGradingId(), item.getEssayId(), item.getRound(),
                            report.score(), report.maxScore());
                    return true;
                }
                lastReason = report.errorMessage();
            }
            if (attempt + 1 < attempts) {
                sleepBackoff(attempt);
            }
        }
        fail(item.getGradingId(), lastReason == null ? "AI の応答を検証できませんでした。" : lastReason);
        return false;
    }

    /** プロンプトを組み立てる（設定の本文をそのまま使う）。 */
    private static String promptOf(String template, EnglishEssayLevel level,
                                   EnglishEssayAiMapper.GradingRow item, boolean user) {
        if (!user) {
            return EnglishEssayAiPrompt.render(template, Map.of("level", level.name()));
        }
        return EnglishEssayAiPrompt.render(template, Map.of(
                "level", level.name(),
                "question_text", nullToEmpty(item.getQuestionText()),
                "essay_text", nullToEmpty(item.getEssayText()),
                "word_count", String.valueOf(item.getWordCount())));
    }

    /** 失敗を記録する（理由は列の長さに収める）。 */
    private void fail(Long gradingId, String reason) {
        String message = reason == null || reason.isBlank() ? "AI の応答を検証できませんでした。" : reason;
        mapper.markGradingFailed(gradingId, limit(message, FAILURE_REASON_LIMIT));
        log.warn("english essay grading failed. gradingId={} reason={}", gradingId, message);
    }

    private static String reasonOf(EnglishEssayAiClient.CallResult call) {
        String code = call.errorCode() == null ? "" : call.errorCode() + " ";
        return code + nullToEmpty(call.errorMessage());
    }

    private static String messageOf(RuntimeException cause) {
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** 再試行の待ち（2.0 と同じ 2s / 4s / 8s、上限 8 秒）。 */
    private static void sleepBackoff(int attempt) {
        long[] backoff = {2_000L, 4_000L, 8_000L};
        try {
            Thread.sleep(backoff[Math.min(attempt, backoff.length - 1)]);
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
        }
    }
}
