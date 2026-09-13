package com.study21.user.studymonitor;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.user.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学習状況モニターの実装。
 *
 * <p>対象日（YYYY-MM-DD）と時間帯（HH:mm）で絞り、動画セグメントとスナップショットを返す。
 * 画像は保存ルート（`study21.study-monitor.snapshot-root`）からの相対パスで解決する
 * （移行したデータの 保存パス は `20260726/snapshot_000045.jpg` の形）。</p>
 */
@Service
public class StudyMonitorServiceImpl implements StudyMonitorService {

    private final StudyMonitorMapper mapper;
    private final Path snapshotRoot;

    public StudyMonitorServiceImpl(StudyMonitorMapper mapper,
                                   @Value("${study21.study-monitor.snapshot-root:${user.dir}/data/snapshots}")
                                   String snapshotRoot) {
        this.mapper = mapper;
        this.snapshotRoot = Path.of(snapshotRoot).toAbsolutePath().normalize();
    }

    @Override
    public StudyMonitorModels.SnapshotSearchResult search(String date, String timeFrom, String timeTo,
                                                          Long videoId, String analysisState, String resultCode) {
        LocalDate target = parseDate(date);
        LocalTime from = parseTime(timeFrom, LocalTime.MIN);
        LocalTime to = parseTime(timeTo, LocalTime.of(23, 59));
        if (to.isBefore(from)) {
            throw new ValidationException("終了時刻は開始時刻より後に設定してください。");
        }
        String state = normalizeAnalysisState(analysisState);
        String result = normalizeResult(resultCode);

        LocalDateTime fromAt = LocalDateTime.of(target, from);
        LocalDateTime toAt = LocalDateTime.of(target, to);
        Long video = videoId == null || videoId <= 0 ? null : videoId;

        List<StudyMonitorModels.VideoRow> videos = mapper.searchVideos(fromAt, toAt, video);
        List<StudyMonitorModels.SnapshotRow> snapshots =
                mapper.searchSnapshots(fromAt, toAt, video, state, result);

        return new StudyMonitorModels.SnapshotSearchResult(
                target.toString(), from.toString(), to.toString(), videos, snapshots, summarize(videos, snapshots));
    }

    /** 集計（2.0 の「検索結果の分析集計」と同じ 4 つ ＋ 結果ごとの枚数）。 */
    private StudyMonitorModels.Summary summarize(List<StudyMonitorModels.VideoRow> videos,
                                                 List<StudyMonitorModels.SnapshotRow> snapshots) {
        int completed = 0;
        int waiting = 0;
        int errors = 0;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String code : StudyMonitorModels.RESULT_CODES) {
            counts.put(code, 0);
        }
        for (StudyMonitorModels.SnapshotRow snapshot : snapshots) {
            switch (snapshot.analysisState() == null ? "WAITING" : snapshot.analysisState()) {
                case "COMPLETED" -> completed += 1;
                case "ERROR" -> errors += 1;
                default -> waiting += 1;
            }
            if (snapshot.resultCode() != null && counts.containsKey(snapshot.resultCode())) {
                counts.merge(snapshot.resultCode(), 1, Integer::sum);
            }
        }
        return new StudyMonitorModels.Summary(
                videos.size(), snapshots.size(), completed, waiting, errors, counts);
    }

    @Override
    @Transactional
    public StudyMonitorModels.ManualCorrectionResult correctManually(
            UserPrincipal user, StudyMonitorModels.ManualCorrectionRequest request) {
        if (user == null) {
            throw new ValidationException("ログインが必要です。");
        }
        String result = normalizeResult(request == null ? null : request.result());
        if (result == null) {
            throw new ValidationException("分析結果を選択してください。");
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty()) {
            throw new ValidationException("修正理由を入力してください。");   // 2.0 と同じく理由は必須
        }
        if (reason.length() > 2000) {
            throw new ValidationException("修正理由は2000文字以内で入力してください。");
        }

        List<StudyMonitorModels.ManualUpdate> updates = new ArrayList<>();
        for (StudyMonitorModels.ManualUpdate update : request.updates()) {
            if (update == null || update.snapshotId() <= 0) {
                continue;
            }
            if (updates.stream().noneMatch(existing -> existing.snapshotId() == update.snapshotId())) {
                updates.add(update);
            }
        }
        if (updates.isEmpty()) {
            throw new ValidationException("分析結果を変更する画像を選択してください。");
        }

        int updated = 0;
        for (StudyMonitorModels.ManualUpdate update : updates) {
            updated += mapper.updateManualAnalysis(update.snapshotId(), update.version(), result, reason,
                    user.accountId());
        }
        if (updated != updates.size()) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        return new StudyMonitorModels.ManualCorrectionResult(
                "選択した画像の分析結果を更新しました。", updated);
    }

    @Override
    public InputStream openImage(long snapshotId) {
        Path file = resolveImage(snapshotId);
        try {
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new NotFoundException("画像を読み込めませんでした。");
        }
    }

    @Override
    public String imageFileName(long snapshotId) {
        return resolveImage(snapshotId).getFileName().toString();
    }

    /** 保存パスを保存ルートからの相対として解決する（ルートの外は開かない）。 */
    private Path resolveImage(long snapshotId) {
        StudyMonitorModels.SnapshotRow row = findSnapshot(snapshotId);
        Path file = snapshotRoot.resolve(row.imagePath()).normalize();
        if (!file.startsWith(snapshotRoot)) {
            throw new NotFoundException("画像が見つかりません。");
        }
        if (!Files.isReadable(file)) {
            throw new NotFoundException("画像が見つかりません。");
        }
        return file;
    }

    private StudyMonitorModels.SnapshotRow findSnapshot(long snapshotId) {
        if (snapshotId <= 0) {
            throw new NotFoundException("画像が見つかりません。");
        }
        // 対象日が分からないので、撮影日時をキーに 1 日ぶんだけ引く
        StudyMonitorModels.SnapshotRow found = mapper.findSnapshot(snapshotId);
        if (found == null || found.imagePath() == null || found.imagePath().isBlank()) {
            throw new NotFoundException("画像が見つかりません。");
        }
        return found;
    }

    private static LocalDate parseDate(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new ValidationException("対象日は YYYY-MM-DD で指定してください。");
        }
    }

    private static LocalTime parseTime(String value, LocalTime fallback) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return fallback;
        }
        try {
            return LocalTime.parse(text.length() == 5 ? text : text.substring(0, 5));
        } catch (RuntimeException e) {
            throw new ValidationException("時刻は HH:mm で指定してください。");
        }
    }

    private static String normalizeAnalysisState(String value) {
        String text = value == null ? "" : value.trim().toLowerCase();
        if (text.isEmpty()) {
            return "all";
        }
        if (!StudyMonitorModels.ANALYSIS_STATES.contains(text)) {
            throw new ValidationException("AI分析は all / done / waiting / error のいずれかを指定してください。");
        }
        return text;
    }

    private static String normalizeResult(String value) {
        String text = value == null ? "" : value.trim().toUpperCase();
        if (text.isEmpty() || "ALL".equals(text)) {
            return null;
        }
        if (!StudyMonitorModels.RESULT_CODES.contains(text)) {
            throw new ValidationException("分析結果の指定が不正です。");
        }
        return text;
    }
}
