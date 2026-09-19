package com.study21.admin.studymonitor;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.geometryai.GeometryAiConnectionResolver;
import com.study21.admin.setting.SettingsService;
import com.study21.admin.setting.SettingsValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * batL03 の業務処理（2.0 の {@code BatL03Task} の移行）。
 *
 * <p>確かめる接縫（Mapper と AI はモック。実 DB は使わない）:</p>
 * <ol>
 *   <li>対象の選定は SQL に任せる（未分析だけ・古い順・一度の分析枚数）</li>
 *   <li>分析結果の書き戻し（一次＝最終・{@code FLASH}・二次判定要否='0'・二次分析状態コード='NOT_REQUIRED'）</li>
 *   <li>失敗は ERROR 行を残し、全件失敗なら例外が呼び出し側に伝わる</li>
 *   <li>画像が無い・保存ルートの外・画像が壊れている場合は **AI を呼ぶ前に**失敗にする</li>
 *   <li>並列数（{@code STUDY_MONITOR_AI_THREADS}）ぶん同時に呼び、部分失敗を集計する</li>
 * </ol>
 */
class StudyMonitorAnalyzeHandlerTest {

    private static final long SNAPSHOT_ID = 12L;
    private static final LocalDateTime CAPTURED_AT = LocalDateTime.of(2026, 7, 26, 15, 53, 46);

    @TempDir
    Path snapshotRoot;

    private StudyMonitorAnalysisMapper mapper;
    private StudyMonitorAiClient aiClient;
    private SettingsService settingsService;
    private GeometryAiConnectionResolver connectionResolver;
    private StudyMonitorAnalyzeHandler handler;

    @BeforeEach
    void setUp() {
        mapper = mock(StudyMonitorAnalysisMapper.class);
        aiClient = mock(StudyMonitorAiClient.class);
        settingsService = mock(SettingsService.class);
        connectionResolver = mock(GeometryAiConnectionResolver.class);
        handler = new StudyMonitorAnalyzeHandler(mapper, aiClient, new StudyMonitorImageResizer(),
                settingsService, connectionResolver, snapshotRoot.toString());
        when(connectionResolver.resolve(anyString(), anyString())).thenReturn(
                new GeometryAiConnectionResolver.AiConnection("qwen", "Qwen3-VL-Flash",
                        "https://example.com/compatible-mode/v1/chat/completions", "secret-key"));
        stubSettings(25, 2);
    }

    private void stubSettings(int batchLimit, int threads) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("STUDY_MONITOR_AI_BATCH_LIMIT", String.valueOf(batchLimit));
        values.put("STUDY_MONITOR_AI_THREADS", String.valueOf(threads));
        values.put("STUDY_MONITOR_AI_TIMEOUT_SECONDS", "120");
        values.put("STUDY_MONITOR_AI_IMAGE_RESOLUTION", "1920×1080");
        values.put("STUDY_MONITOR_FIRST_AI_PROVIDER", "qwen:3");
        values.put("STUDY_MONITOR_FIRST_SYSTEM_PROMPT", "システムプロンプト");
        values.put("STUDY_MONITOR_FIRST_USER_PROMPT", "次の画像を分析してください。");
        when(settingsService.requireSettings(anyString(), any())).thenReturn(values);
    }

    private static BatchExecutionEntity execution() {
        BatchExecutionEntity execution = new BatchExecutionEntity();
        execution.setExecutionId(77L);
        return execution;
    }

    private static StudyMonitorAnalysisMapper.Target target(long snapshotId, String savePath) {
        return new StudyMonitorAnalysisMapper.Target(snapshotId, savePath, CAPTURED_AT);
    }

    /** スナップショット画像を保存ルートの下に作る（見つかったときの正常系用）。 */
    private String writeImage(String relative, int width, int height) throws Exception {
        Path file = snapshotRoot.resolve(relative);
        Files.createDirectories(file.getParent());
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", file.toFile());
        return relative;
    }

    private void stubAnalyze(String stateCode, double confidence, String reason) {
        when(aiClient.analyze(any())).thenReturn(new StudyMonitorAiClient.AnalyzeResult(
                stateCode, confidence, reason, "{\"status\":\"学習中（PC使用）\",\"confidence\":0.9}"));
    }

    @Test
    @DisplayName("対象が無ければ AI を呼ばずに対象 0 件を返す")
    void doesNothingWhenNoTarget() throws Exception {
        when(mapper.findAnalysisTargets(anyInt())).thenReturn(List.of());

        String message = handler.execute(execution());

        assertThat(message).isEqualTo("AI分析対象=0件（未分析のスナップショットはありません）, 処理時間=0.0秒");
        verify(mapper).findAnalysisTargets(25);
        verifyNoInteractions(aiClient);
        verify(mapper, never()).insertCompleted(any());
        verify(mapper, never()).upsertError(any());
        // 何もしない実行を AI 接続の設定で落とさない
        verify(connectionResolver, never()).resolve(anyString(), anyString());
    }

    @Test
    @DisplayName("未分析のスナップショットだけを一度の分析枚数ぶん処理する")
    void analyzesSelectedTargetsOnly() throws Exception {
        when(mapper.findAnalysisTargets(anyInt())).thenReturn(List.of(
                target(SNAPSHOT_ID, writeImage("20260726/a.jpg", 100, 50)),
                target(13L, writeImage("20260726/b.jpg", 100, 50))));
        stubAnalyze("STUDY_PC", 0.9, "PC を使っています。");

        String message = handler.execute(execution());

        assertThat(message).contains("AI分析対象=2件").contains("成功=2件").contains("失敗=0件").contains("並列数=2");
        // 選定は SQL の条件（未分析だけ）に任せ、上位で絞り直さない
        verify(mapper).findAnalysisTargets(25);
        verify(aiClient, times(2)).analyze(any());
        verify(mapper, times(2)).insertCompleted(any());
        verify(mapper, never()).upsertError(any());
    }

    @Test
    @DisplayName("分析結果を書き戻す（一次＝最終・FLASH・二次判定なし・監査は BAT_L03）")
    void writesCompletedRow() throws Exception {
        String path = writeImage("20260726/snapshot_000045.jpg", 1920, 1080);
        when(mapper.findAnalysisTargets(anyInt())).thenReturn(List.of(target(SNAPSHOT_ID, path)));
        stubAnalyze("STUDY_NO_PC", 0.876, "机の上でノートに筆記しています。");

        handler.execute(execution());

        ArgumentCaptor<StudyMonitorAiClient.AnalyzeRequest> requestCaptor =
                ArgumentCaptor.forClass(StudyMonitorAiClient.AnalyzeRequest.class);
        verify(aiClient).analyze(requestCaptor.capture());
        StudyMonitorAiClient.AnalyzeRequest request = requestCaptor.getValue();
        assertThat(request.processingKey()).isEqualTo("study-monitor/snapshot/12/first");
        assertThat(request.executionId()).isEqualTo(77L);
        assertThat(request.provider()).isEqualTo("qwen");
        assertThat(request.model()).isEqualTo("Qwen3-VL-Flash");
        assertThat(request.systemPrompt()).isEqualTo("システムプロンプト");
        assertThat(request.userPrompt()).isEqualTo("次の画像を分析してください。");
        assertThat(request.timeoutSeconds()).isEqualTo(120);
        assertThat(request.imageMime()).isEqualTo("image/jpeg");
        assertThat(request.image()).isNotEmpty();

        ArgumentCaptor<StudyMonitorAnalysisMapper.CompletedRow> rowCaptor =
                ArgumentCaptor.forClass(StudyMonitorAnalysisMapper.CompletedRow.class);
        verify(mapper).insertCompleted(rowCaptor.capture());
        StudyMonitorAnalysisMapper.CompletedRow row = rowCaptor.getValue();
        assertThat(row.snapshotId()).isEqualTo(SNAPSHOT_ID);
        // 撮影日時は AI が読んだ値ではなくスナップショットの 撮影日時（2.1 の DDL どおり）
        assertThat(row.capturedAt()).isEqualTo(Timestamp.valueOf(CAPTURED_AT));
        assertThat(row.firstResultCode()).isEqualTo("STUDY_NO_PC");
        assertThat(row.firstConfidence()).isEqualByComparingTo(new BigDecimal("0.876"));
        assertThat(row.firstConfidence().scale()).isEqualTo(3);
        assertThat(row.firstReason()).isEqualTo("机の上でノートに筆記しています。");
        assertThat(row.firstAiProvider()).isEqualTo("qwen");
        assertThat(row.firstAiModel()).isEqualTo("Qwen3-VL-Flash");
        assertThat(row.firstStartedAt()).isNotNull();
        assertThat(row.firstFinishedAt()).isNotNull();
        assertThat(row.firstResponseJson()).isEqualTo("{\"status\":\"学習中（PC使用）\",\"confidence\":0.9}");
        // 二次判定はしない（2.1 の設定にキーが無い）。DB の既定と同じ値のまま
        assertThat(row.secondRequired()).isEqualTo("0");
        assertThat(row.secondStateCode()).isEqualTo("NOT_REQUIRED");
        assertThat(row.threshold()).isEqualByComparingTo(new BigDecimal("0.750"));
        // 最終列は一次判定と同じ（採用段階は FLASH）
        assertThat(row.finalStageCode()).isEqualTo("FLASH");
        assertThat(row.finalResultCode()).isEqualTo(row.firstResultCode());
        assertThat(row.finalConfidence()).isEqualByComparingTo(row.firstConfidence());
        assertThat(row.finalReason()).isEqualTo(row.firstReason());
        assertThat(row.analysisFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("画像ファイルが無ければ AI を呼ばずに ERROR 行を残す")
    void failsWithoutCallingAiWhenImageMissing() throws Exception {
        when(mapper.findAnalysisTargets(anyInt())).thenReturn(List.of(
                target(SNAPSHOT_ID, "20260726/missing.jpg")));

        assertThatThrownBy(() -> handler.execute(execution()))
                .isInstanceOf(StudyMonitorAnalyzeHandler.StudyMonitorAnalysisFailedException.class)
                .hasMessageContaining("失敗=1件")
                .hasMessageContaining("画像ファイルがありません");

        verify(aiClient, never()).analyze(any());
        verify(mapper, never()).insertCompleted(any());
        StudyMonitorAnalysisMapper.ErrorRow row = captureErrorRow();
        assertThat(row.snapshotId()).isEqualTo(SNAPSHOT_ID);
        assertThat(row.capturedAt()).isEqualTo(Timestamp.valueOf(CAPTURED_AT));
        assertThat(row.firstAiProvider()).isEqualTo("qwen");
        assertThat(row.firstAiModel()).isEqualTo("Qwen3-VL-Flash");
        assertThat(row.firstErrorMessage()).contains("画像ファイルがありません").contains("missing.jpg");
        assertThat(row.firstResponseJson()).isNull();
    }

    @Test
    @DisplayName("画像が壊れていても AI を呼ばずに ERROR 行を残す")
    void failsWithoutCallingAiWhenImageBroken() throws Exception {
        Path file = snapshotRoot.resolve("20260726/broken.jpg");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "これは画像ではありません", StandardCharsets.UTF_8);
        when(mapper.findAnalysisTargets(anyInt())).thenReturn(List.of(
                target(SNAPSHOT_ID, "20260726/broken.jpg")));

        assertThatThrownBy(() -> handler.execute(execution()))
                .isInstanceOf(StudyMonitorAnalyzeHandler.StudyMonitorAnalysisFailedException.class);

        verify(aiClient, never()).analyze(any());
        assertThat(captureErrorRow().firstErrorMessage()).contains("読み込めません");
    }

    @Test
    @DisplayName("保存ルートの外を指す保存パスは拒否する")
    void rejectsPathOutsideRoot() throws Exception {
        for (String savePath : List.of("../outside.jpg", "/etc/passwd", "20260726/../../secret.jpg")) {
            // 1 つの実行で 1 回だけ ERROR 行を検証したいので、反復ごとにモックを作り直す
            StudyMonitorAnalysisMapper loopMapper = mock(StudyMonitorAnalysisMapper.class);
            StudyMonitorAiClient loopAiClient = mock(StudyMonitorAiClient.class);
            StudyMonitorAnalyzeHandler loopHandler = new StudyMonitorAnalyzeHandler(loopMapper, loopAiClient,
                    new StudyMonitorImageResizer(), settingsService, connectionResolver, snapshotRoot.toString());
            when(loopMapper.findAnalysisTargets(anyInt())).thenReturn(List.of(target(SNAPSHOT_ID, savePath)));

            assertThatThrownBy(() -> loopHandler.execute(execution()))
                    .as(savePath)
                    .isInstanceOf(StudyMonitorAnalyzeHandler.StudyMonitorAnalysisFailedException.class)
                    .hasMessageContaining("保存ルート");

            verify(loopAiClient, never()).analyze(any());
            ArgumentCaptor<StudyMonitorAnalysisMapper.ErrorRow> captor =
                    ArgumentCaptor.forClass(StudyMonitorAnalysisMapper.ErrorRow.class);
            verify(loopMapper).upsertError(captor.capture());
            assertThat(captor.getValue().firstErrorMessage())
                    .as(savePath)
                    .contains("保存ルートの外");
        }
    }

    @Test
    @DisplayName("保存パスの解決（ルートの中は通り、外は拒否する）")
    void resolvesSavePath() {
        assertThat(handler.resolveImagePath("20260726/snapshot_000045.jpg"))
                .isEqualTo(snapshotRoot.resolve("20260726/snapshot_000045.jpg"));
        // 途中に .. があってもルートの中に収まるなら通す
        assertThat(handler.resolveImagePath("20260726/../20260726/a.jpg"))
                .isEqualTo(snapshotRoot.resolve("20260726/a.jpg"));
        assertThatThrownBy(() -> handler.resolveImagePath("../a.jpg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("保存ルートの外");
        assertThatThrownBy(() -> handler.resolveImagePath("/etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.resolveImagePath("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("保存パス");
    }

    @Test
    @DisplayName("AI の応答が不正なら ERROR 行を残し、例外が呼び出し側に伝わる")
    void marksErrorWhenAiReplyIsInvalid() throws Exception {
        when(mapper.findAnalysisTargets(anyInt())).thenReturn(List.of(
                target(SNAPSHOT_ID, writeImage("20260726/a.jpg", 100, 50))));
        when(aiClient.analyze(any())).thenThrow(new StudyMonitorAiClient.AnalyzeException(
                "AI の状態値が不正です: 食事中", "{\"status\":\"食事中\"}"));

        assertThatThrownBy(() -> handler.execute(execution()))
                .isInstanceOf(StudyMonitorAnalyzeHandler.StudyMonitorAnalysisFailedException.class)
                .hasMessageContaining("失敗=1件")
                .hasMessageContaining("状態値が不正");

        verify(mapper, never()).insertCompleted(any());
        StudyMonitorAnalysisMapper.ErrorRow row = captureErrorRow();
        assertThat(row.firstErrorMessage()).contains("状態値が不正");
        // 読めた生応答は ERROR 行にも残す（人が画面で見て直せるように）
        assertThat(row.firstResponseJson()).isEqualTo("{\"status\":\"食事中\"}");
    }

    @Test
    @DisplayName("AI の接続に失敗しても ERROR 行を残す")
    void marksErrorWhenAiCallFails() throws Exception {
        when(mapper.findAnalysisTargets(anyInt())).thenReturn(List.of(
                target(SNAPSHOT_ID, writeImage("20260726/a.jpg", 100, 50))));
        when(aiClient.analyze(any())).thenThrow(new StudyMonitorAiClient.AnalyzeException(
                "AI の呼び出しに失敗しました（HTTP 500）。"));

        assertThatThrownBy(() -> handler.execute(execution()))
                .isInstanceOf(StudyMonitorAnalyzeHandler.StudyMonitorAnalysisFailedException.class);

        StudyMonitorAnalysisMapper.ErrorRow row = captureErrorRow();
        assertThat(row.firstErrorMessage()).contains("HTTP 500");
        assertThat(row.firstResponseJson()).isNull();
    }

    @Test
    @DisplayName("部分失敗は成功ぶんを書き戻し、要約に失敗件数を残して正常終了する")
    void aggregatesPartialFailure() throws Exception {
        long failingId = 13L;
        when(mapper.findAnalysisTargets(anyInt())).thenReturn(List.of(
                target(SNAPSHOT_ID, writeImage("20260726/a.jpg", 100, 50)),
                target(failingId, writeImage("20260726/b.jpg", 100, 50)),
                target(14L, writeImage("20260726/c.jpg", 100, 50))));
        when(aiClient.analyze(any())).thenAnswer(invocation -> {
            StudyMonitorAiClient.AnalyzeRequest request = invocation.getArgument(0);
            if (request.processingKey().contains("/" + failingId + "/")) {
                throw new StudyMonitorAiClient.AnalyzeException("AI の状態値が不正です: 不明");
            }
            return new StudyMonitorAiClient.AnalyzeResult("AWAY", 0.4, "離席しています。", "{\"status\":\"離席中\"}");
        });

        String message = handler.execute(execution());

        assertThat(message).contains("AI分析対象=3件").contains("成功=2件").contains("失敗=1件")
                .contains("状態値が不正");
        verify(mapper, times(2)).insertCompleted(any());
        verify(mapper).upsertError(any());
    }

    @Test
    @DisplayName("並列数の設定ぶん同時に AI を呼ぶ")
    void runsWithConfiguredThreads() throws Exception {
        stubSettings(25, 2);
        List<StudyMonitorAnalysisMapper.Target> targets = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            targets.add(target(100L + index, writeImage("20260726/s" + index + ".jpg", 100, 50)));
        }
        when(mapper.findAnalysisTargets(anyInt())).thenReturn(targets);

        // 1 本目の呼び出しが返る前に 2 本目が入ってくるか（＝並列数の設定ぶん同時に動くか）を
        // 時間の当て推量ではなく待ち合わせで確かめる（スリープだと環境で揺れる）
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch released = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean firstCallOverlapped = new AtomicBoolean();
        Set<String> workers = ConcurrentHashMap.newKeySet();
        when(aiClient.analyze(any())).thenAnswer(invocation -> {
            workers.add(Thread.currentThread().getName());
            int order = calls.incrementAndGet();
            bothEntered.countDown();
            if (bothEntered.getCount() == 0) {
                released.countDown();
            }
            boolean overlapped = released.await(10, TimeUnit.SECONDS);
            if (order == 1) {
                firstCallOverlapped.set(overlapped);
            }
            return new StudyMonitorAiClient.AnalyzeResult("OTHER", 0.2, "その他", "{\"status\":\"その他\"}");
        });

        String message = handler.execute(execution());

        assertThat(message).contains("AI分析対象=4件").contains("成功=4件").contains("並列数=2");
        assertThat(firstCallOverlapped).as("workers=%s", workers).isTrue();
        assertThat(workers).hasSize(2);
        // 同じスナップショットを 2 回処理しない（処理キーは 1 枚 1 つ）
        assertThat(capturedProcessingKeys()).hasSize(4).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("設定が欠けていれば既定値で動かさずに例外にする")
    void failsWhenSettingsMissing() {
        when(settingsService.requireSettings(anyString(), any())).thenThrow(
                new SettingsValidationException(List.of("STUDY_MONITOR_AI_BATCH_LIMIT が未設定です。")));

        assertThatThrownBy(() -> handler.execute(execution()))
                .isInstanceOf(SettingsValidationException.class);

        verifyNoInteractions(mapper);
        verifyNoInteractions(aiClient);
    }

    @Test
    @DisplayName("要求する設定は batL03 の 7 キー（既定値を持たない）")
    void declaresRequiredSettings() {
        assertThat(handler.taskCode()).isEqualTo("batL03");
        assertThat(StudyMonitorAnalyzeHandler.REQUIRED_SETTINGS)
                .extracting(requirement -> requirement.pageCode() + "." + requirement.settingKey())
                .containsExactly(
                        "STUDY_MONITOR.STUDY_MONITOR_AI_BATCH_LIMIT",
                        "STUDY_MONITOR.STUDY_MONITOR_AI_THREADS",
                        "STUDY_MONITOR.STUDY_MONITOR_AI_TIMEOUT_SECONDS",
                        "STUDY_MONITOR.STUDY_MONITOR_AI_IMAGE_RESOLUTION",
                        "STUDY_MONITOR.STUDY_MONITOR_FIRST_AI_PROVIDER",
                        "STUDY_MONITOR.STUDY_MONITOR_FIRST_SYSTEM_PROMPT",
                        "STUDY_MONITOR.STUDY_MONITOR_FIRST_USER_PROMPT");
        // 二次判定のキーは 2.1 に無い（SECONDARY_THRESHOLD / SECOND_AI_PROVIDER / SECOND_*_PROMPT）
        assertThat(StudyMonitorAnalyzeHandler.REQUIRED_SETTINGS)
                .extracting(requirement -> requirement.settingKey())
                .noneMatch(key -> key.contains("SECONDARY_THRESHOLD")
                        || key.contains("SECOND_AI_PROVIDER")
                        || key.contains("SECOND_SYSTEM_PROMPT")
                        || key.contains("SECOND_USER_PROMPT"));
    }

    private StudyMonitorAnalysisMapper.ErrorRow captureErrorRow() {
        ArgumentCaptor<StudyMonitorAnalysisMapper.ErrorRow> captor =
                ArgumentCaptor.forClass(StudyMonitorAnalysisMapper.ErrorRow.class);
        verify(mapper).upsertError(captor.capture());
        return captor.getValue();
    }

    private List<String> capturedProcessingKeys() {
        ArgumentCaptor<StudyMonitorAiClient.AnalyzeRequest> captor =
                ArgumentCaptor.forClass(StudyMonitorAiClient.AnalyzeRequest.class);
        verify(aiClient, org.mockito.Mockito.atLeastOnce()).analyze(captor.capture());
        return captor.getAllValues().stream()
                .map(StudyMonitorAiClient.AnalyzeRequest::processingKey)
                .collect(Collectors.toList());
    }
}
