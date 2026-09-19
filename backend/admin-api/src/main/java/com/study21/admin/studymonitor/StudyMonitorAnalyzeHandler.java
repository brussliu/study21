package com.study21.admin.studymonitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchTaskHandler;
import com.study21.admin.geometryai.GeometryAiConnectionResolver;
import com.study21.admin.setting.SettingRequirement;
import com.study21.admin.setting.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * batL03「学習モニター スナップショット AI 分析」の業務処理（2.0 の {@code BatL03Task} の移行）。
 *
 * <p>流れ（2.0 と同じ）:</p>
 * <ol>
 *   <li>設定を解決する（{@link #REQUIRED_SETTINGS}。**2.1 は既定値を持たない**）</li>
 *   <li>未分析のスナップショットを**古い順に**一度の分析枚数ぶん選ぶ（選定は SQL 側。
 *       {@link StudyMonitorAnalysisMapper#findAnalysisTargets}）</li>
 *   <li>画像を設定の解像度へ縮小し、1 枚ずつ AI に渡す（並列数は設定）</li>
 *   <li>結果を {@code MON_学習モニター画像分析情報} に書き戻す（失敗は ERROR 行）</li>
 * </ol>
 *
 * <p><strong>2.0 から意図的に変えた点</strong></p>
 * <ul>
 *   <li>2.0 は実行のたびに {@code 分析状態='ERROR'} の全行を DELETE してから対象を選んでいた。
 *       そのため壊れたスナップショットが永久に再試行され、最古の 1 枚が毎回枠を埋めて
 *       後続が餓死した。2.1 は <b>ERROR 行を消さない</b>（{@code 最新版フラグ='1'} が立つので
 *       対象から外れる）。<b>自動リトライはしない</b>（人は画面で ERROR を見て直す）。</li>
 *   <li>対象は「スナップショットの完成状態」だけで決める。2.0 の batL02 は時間をずらして
 *       「切出が済んだ頃だろう」と決め打ちしていたが、2.1 はその判断をしない。</li>
 *   <li>画像ファイルが無い・保存ルートの外を指す場合は、<b>AI を呼ぶ前に</b>失敗にする
 *       （無駄な課金をしない）。</li>
 *   <li>{@code 撮影日時} は AI が画像から読んだ値ではなく<b>スナップショットの 撮影日時</b>を使う
 *       （2.1 の DDL が「スナップショットの 撮影日時 と同じ値の控え」と定めている。
 *       2.0 は AI の {@code captured_at} を必須にしていたため、日時が読めない画像は
 *       それだけで ERROR になっていた）。</li>
 *   <li>二次判定はしない（2.1 の設定に二次判定のキーが無い）。最終列は一次判定と同じ値で
 *       {@code 最終採用段階コード='FLASH'}。</li>
 * </ul>
 */
@Component
public class StudyMonitorAnalyzeHandler implements BatchTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(StudyMonitorAnalyzeHandler.class);

    /** 2.0 から引き継いだバッチコード。 */
    public static final String TASK_CODE = "batL03";

    /** 監査の 登録元コード / 更新元コード（バッチが書くのでアカウントID は NULL）。 */
    public static final String AUDIT_SOURCE_CODE = "BAT_L03";

    /** 呼出履歴の 処理キーの前置き（{@code study-monitor/snapshot/<ID>/first}）。 */
    public static final String PROCESS_KEY_PREFIX = "study-monitor/snapshot/";

    /** 2.1 は二次判定をしないので、DB の既定と同じ値のまま入れる。 */
    public static final String SECOND_REQUIRED_NO = "0";
    /** 同上（二次判定を行わない）。 */
    public static final String SECOND_STATE_NOT_REQUIRED = "NOT_REQUIRED";
    /** 一次判定を採用したことを示す採用段階（2.0 も二次判定をしなかったときは FLASH）。 */
    public static final String FINAL_STAGE_FLASH = "FLASH";

    /**
     * 二次判定の閾値。
     *
     * <p>2.1 は二次判定をしないので**この値は使わない**が、列が NOT NULL（既定 0.750）なので
     * DB の既定と同じ値を明示して入れる（列の意味は 2.0 のまま残す）。</p>
     */
    public static final BigDecimal SECOND_THRESHOLD = new BigDecimal("0.750");

    /** batL03 が要求する設定（2.1 は既定値を持たない。欠落は実行前に例外になる）。 */
    public static final List<SettingRequirement> REQUIRED_SETTINGS = List.of(
            new SettingRequirement("STUDY_MONITOR", "STUDY_MONITOR_AI_BATCH_LIMIT"),
            new SettingRequirement("STUDY_MONITOR", "STUDY_MONITOR_AI_THREADS"),
            new SettingRequirement("STUDY_MONITOR", "STUDY_MONITOR_AI_TIMEOUT_SECONDS"),
            new SettingRequirement("STUDY_MONITOR", "STUDY_MONITOR_AI_IMAGE_RESOLUTION"),
            new SettingRequirement("STUDY_MONITOR", "STUDY_MONITOR_FIRST_AI_PROVIDER"),
            new SettingRequirement("STUDY_MONITOR", "STUDY_MONITOR_FIRST_SYSTEM_PROMPT"),
            new SettingRequirement("STUDY_MONITOR", "STUDY_MONITOR_FIRST_USER_PROMPT"));

    /** 要約に載せるエラーの件数（2.0 と同じ 3 件まで）。 */
    private static final int ERROR_SAMPLE_LIMIT = 3;
    /** 要約・ERROR 行に載せる 1 件のエラー文の長さ（2.0 と同じ 140 / 2000 文字）。 */
    private static final int SUMMARY_ERROR_LIMIT = 140;
    private static final int ERROR_MESSAGE_LIMIT = 2000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StudyMonitorAnalysisMapper mapper;
    private final StudyMonitorAiClient aiClient;
    private final StudyMonitorImageResizer imageResizer;
    private final SettingsService settingsService;
    private final GeometryAiConnectionResolver connectionResolver;
    /** スナップショット画像の保存ルート（DB の 保存パス はここからの相対パス）。 */
    private final Path snapshotRoot;

    public StudyMonitorAnalyzeHandler(StudyMonitorAnalysisMapper mapper,
                                      StudyMonitorAiClient aiClient,
                                      StudyMonitorImageResizer imageResizer,
                                      SettingsService settingsService,
                                      GeometryAiConnectionResolver connectionResolver,
                                      @Value("${study21.study-monitor.snapshot-root:${user.dir}/data/snapshots}")
                                      String snapshotRoot) {
        this.mapper = mapper;
        this.aiClient = aiClient;
        this.imageResizer = imageResizer;
        this.settingsService = settingsService;
        this.connectionResolver = connectionResolver;
        this.snapshotRoot = Path.of(snapshotRoot).toAbsolutePath().normalize();
    }

    @Override
    public String taskCode() {
        return TASK_CODE;
    }

    @Override
    public String execute(BatchExecutionEntity execution) throws Exception {
        StudyMonitorAnalysisSettings settings = StudyMonitorAnalysisSettings.from(
                settingsService.requireSettings(TASK_CODE, REQUIRED_SETTINGS));

        // 対象の選定は SQL（スナップショットの完成状態 + 最新版フラグが無いこと）。
        // 2.0 のように ERROR 行を消してから選び直さない
        List<StudyMonitorAnalysisMapper.Target> targets = mapper.findAnalysisTargets(settings.batchLimit());
        if (targets.isEmpty()) {
            return "AI分析対象=0件（未分析のスナップショットはありません）, 処理時間=0.0秒";
        }

        // 接続情報は設定（STUDY_MONITOR_FIRST_AI_PROVIDER = qwen:3 → AI_QWEN_MODEL_3 / URL / API Key）から
        // 既存の解決部品で読む。対象が 0 件のときは読まない（何もしない実行を設定で落とさない）
        GeometryAiConnectionResolver.AiConnection connection =
                connectionResolver.resolve(TASK_CODE, settings.provider());
        Long executionId = execution == null ? null : execution.getExecutionId();

        long startedNanos = System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(settings.threads());
        List<Future<ItemResult>> futures = new ArrayList<>();
        for (StudyMonitorAnalysisMapper.Target target : targets) {
            futures.add(executor.submit(() -> analyzeOne(target, settings, connection, executionId)));
        }
        int succeeded = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        try {
            for (Future<ItemResult> future : futures) {
                ItemResult result;
                try {
                    result = future.get();
                } catch (ExecutionException cause) {
                    // analyzeOne は自分で ERROR 行を書く。ここへ来るのは想定外の失敗だけ
                    result = ItemResult.failure(cause.getCause() == null ? cause : cause.getCause());
                } catch (InterruptedException cause) {
                    Thread.currentThread().interrupt();
                    throw cause;
                }
                if (result.success()) {
                    succeeded++;
                } else {
                    failed++;
                    if (errors.size() < ERROR_SAMPLE_LIMIT) {
                        errors.add(shorten(result.errorMessage(), SUMMARY_ERROR_LIMIT));
                    }
                }
            }
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }

        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        String message = "AI分析対象=" + targets.size() + "件, 成功=" + succeeded + "件, 失敗=" + failed
                + "件, 並列数=" + settings.threads() + ", 処理時間=" + formatDuration(elapsedMillis)
                + (errors.isEmpty() ? "" : ", エラー=" + errors);
        log.info("{} finished. targets={} succeeded={} failed={} threads={} durationMs={}",
                TASK_CODE, targets.size(), succeeded, failed, settings.threads(), elapsedMillis);
        if (failed > 0 && succeeded == 0) {
            // 全件失敗はバッチとして異常終了にする（部分失敗は要約に残して正常終了。2.0 と同じ）
            throw new StudyMonitorAnalysisFailedException(message);
        }
        return message;
    }

    /** 1 枚を判定して書き戻す。失敗は ERROR 行を残して呼び出し側（要約）へ返す。 */
    private ItemResult analyzeOne(StudyMonitorAnalysisMapper.Target target,
                                  StudyMonitorAnalysisSettings settings,
                                  GeometryAiConnectionResolver.AiConnection connection,
                                  Long executionId) {
        try {
            // 画像は AI を呼ぶ前に確かめる（無駄な課金をしない）
            byte[] original = readImage(target);
            StudyMonitorImageResizer.ResizedImage image = imageResizer.resize(original, settings.resolution());

            Timestamp startedAt = Timestamp.valueOf(LocalDateTime.now());
            StudyMonitorAiClient.AnalyzeResult result = aiClient.analyze(new StudyMonitorAiClient.AnalyzeRequest(
                    executionId,
                    PROCESS_KEY_PREFIX + target.snapshotId() + "/first",
                    connection.provider(), connection.model(), connection.url(), connection.apiKey(),
                    settings.systemPrompt(), settings.userPrompt(),
                    image.bytes(), image.mime(), settings.timeoutSeconds()));
            Timestamp finishedAt = Timestamp.valueOf(LocalDateTime.now());

            // 2.1 は二次判定をしないので、最終列は一次判定と同じ値（採用段階は FLASH）
            BigDecimal confidence = scale(result.confidence());
            mapper.insertCompleted(new StudyMonitorAnalysisMapper.CompletedRow(
                    target.snapshotId(),
                    Timestamp.valueOf(target.capturedAt()),
                    result.stateCode(), confidence, result.reason(),
                    connection.provider(), connection.model(), startedAt, finishedAt, result.rawResponse(),
                    SECOND_REQUIRED_NO, SECOND_STATE_NOT_REQUIRED, SECOND_THRESHOLD,
                    FINAL_STAGE_FLASH, result.stateCode(), confidence, result.reason(),
                    Timestamp.valueOf(LocalDateTime.now())));
            return ItemResult.completed();
        } catch (Exception cause) {
            markError(target, connection, cause);
            log.error("Study monitor AI analysis failed. snapshotId={}", target.snapshotId(), cause);
            return ItemResult.failure(cause);
        }
    }

    /** 画像を読む（保存ルートの外は読まない）。 */
    private byte[] readImage(StudyMonitorAnalysisMapper.Target target) throws IOException {
        Path file = resolveImagePath(target.savePath());
        if (!Files.isReadable(file)) {
            throw new IllegalArgumentException("画像ファイルがありません: " + target.savePath());
        }
        return Files.readAllBytes(file);
    }

    /**
     * {@code 保存パス}（保存ルートからの相対パス）を実際のファイルの場所にする。
     *
     * <p>正規化したあとに保存ルートの下にあることを確かめる（{@code ../} や絶対パスで
     * ルートの外へ出る指定は拒否する）。</p>
     */
    Path resolveImagePath(String savePath) {
        if (savePath == null || savePath.isBlank()) {
            throw new IllegalArgumentException("保存パスが設定されていません。");
        }
        Path file = snapshotRoot.resolve(savePath).normalize();
        if (!file.startsWith(snapshotRoot)) {
            throw new IllegalArgumentException("保存パスが保存ルートの外を指しています: " + savePath);
        }
        return file;
    }

    /** 失敗した 1 枚を ERROR として残す（消さない・自動リトライしない）。 */
    private void markError(StudyMonitorAnalysisMapper.Target target,
                           GeometryAiConnectionResolver.AiConnection connection,
                           Exception cause) {
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        String rawResponse = cause instanceof StudyMonitorAiClient.AnalyzeException analyzeFailure
                ? analyzeFailure.rawResponse() : null;
        try {
            mapper.upsertError(new StudyMonitorAnalysisMapper.ErrorRow(
                    target.snapshotId(),
                    Timestamp.valueOf(target.capturedAt()),
                    connection == null ? null : connection.provider(),
                    connection == null ? null : connection.model(),
                    shorten(message, ERROR_MESSAGE_LIMIT),
                    // 応答が JSON として読めたときだけ残す（列は jsonb）
                    jsonOrNull(rawResponse)));
        } catch (Exception cause2) {
            // ERROR 行の記録に失敗しても、元の失敗（要約と実行履歴）は伝える
            log.error("Failed to save monitor analysis error. snapshotId={}", target.snapshotId(), cause2);
        }
    }

    private static String jsonOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            MAPPER.readTree(value);
            return value;
        } catch (Exception cause) {
            return null;
        }
    }

    /** 信頼度は NUMERIC(4,3) に合わせる（0〜1 なので 0.900 の形）。 */
    private static BigDecimal scale(double confidence) {
        return BigDecimal.valueOf(confidence).setScale(3, RoundingMode.HALF_UP);
    }

    private static String formatDuration(long millis) {
        return String.format(Locale.ROOT, "%.1f秒", millis / 1000.0);
    }

    private static String shorten(String value, int max) {
        if (value == null) {
            return "unknown";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    /** 1 枚の結果（要約の集計用）。 */
    private record ItemResult(boolean success, String errorMessage) {
        static ItemResult completed() {
            return new ItemResult(true, null);
        }

        static ItemResult failure(Throwable cause) {
            return new ItemResult(false, cause == null ? "unknown" : cause.getMessage());
        }
    }

    /** 対象の全件が失敗したとき（バッチとしては異常終了。実行履歴のエラー詳細に残す）。 */
    public static class StudyMonitorAnalysisFailedException extends RuntimeException {
        public StudyMonitorAnalysisFailedException(String message) {
            super(message);
        }
    }
}
