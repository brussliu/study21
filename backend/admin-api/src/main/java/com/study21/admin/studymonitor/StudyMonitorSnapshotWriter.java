package com.study21.admin.studymonitor;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * スナップショットのファイルを所定の場所へ移し、DB に登録する内容（相対パス・撮影日時・
 * 動画内オフセット）を組み立てる（batL02）。
 *
 * <p>2.0 の {@code BatL02Task#moveSnapshotsToDateFolders} と同じ規則:
 * <b>ffmpeg の出力（staging）→ 保存ルート/&lt;yyyyMMdd&gt;/snapshot_%06d.jpg</b>。
 * 連番は 2.0 と同じく「その日の 0 時からの秒 ÷ 切出間隔 + 1」で決める
 * （時刻ごとに 1 枚なので、撮影日時から一意に決まる）。</p>
 *
 * <p>2.1 の違いは {@code 保存パス} が**保存ルートからの相対パス**であること
 * （2.0 は絶対パス。移行済みデータの形 {@code 20260726/snapshot_000045.jpg} に合わせる）。</p>
 */
@Component
public class StudyMonitorSnapshotWriter {

    /** 2.0 と同じファイル名（連番）。 */
    private static final String SNAPSHOT_FILE_NAME_FORMAT = "snapshot_%06d.jpg";

    private final VideoFileTools videoFileTools;

    public StudyMonitorSnapshotWriter(VideoFileTools videoFileTools) {
        this.videoFileTools = videoFileTools;
    }

    /**
     * staging に切り出した画像を、撮影日時の日付フォルダーへ移す。
     *
     * <p>移動先は撮影日時（＝撮影開始日時 + index × 切出間隔）で決まるので、日をまたぐ動画では
     * 2 つの日付フォルダーに分かれる（2.0 と同じ）。</p>
     *
     * @param snapshotRoot 保存ルート（この下からの相対パスを DB に持つ）
     * @param period       動画の撮影時間帯（ファイル名から）
     * @param staged       staging の画像（ファイル名の昇順。index が動画内の位置）
     * @return 移動後の 1 枚ごとの情報（撮影日時・オフセット・相対パス）
     */
    public List<StagedSnapshot> moveToDateFolders(Path snapshotRoot, VideoFileTools.VideoPeriod period,
                                                  List<Path> staged, int snapshotIntervalSeconds)
            throws IOException {
        List<StagedSnapshot> result = new ArrayList<>();
        for (int index = 0; index < staged.size(); index++) {
            long offsetMillis = index * snapshotIntervalSeconds * 1_000L;
            LocalDateTime capturedAt = period.startedAt().plusNanos(offsetMillis * 1_000_000L);
            Path dateDirectory = snapshotRoot.resolve(
                    capturedAt.toLocalDate().format(VideoFileTools.SNAPSHOT_DATE_FORMAT)).normalize();
            if (!dateDirectory.startsWith(snapshotRoot)) {
                throw new IOException("スナップショットの日付フォルダーが不正です: " + dateDirectory);
            }
            Files.createDirectories(dateDirectory);

            long sequence = capturedAt.toLocalTime().toSecondOfDay() / snapshotIntervalSeconds + 1L;
            String fileName = SNAPSHOT_FILE_NAME_FORMAT.formatted(sequence);
            Path target = dateDirectory.resolve(fileName).normalize();
            if (!target.startsWith(dateDirectory)) {
                throw new IOException("スナップショットの保存先が不正です: " + target);
            }
            Path moved = Files.move(staged.get(index), target, StandardCopyOption.REPLACE_EXISTING);
            result.add(new StagedSnapshot(offsetMillis, capturedAt, fileName,
                    toRelativePath(snapshotRoot, moved)));
        }
        return result;
    }

    /**
     * 切り出した画像の大きさを読む。1 本の動画のスナップショットは同じ大きさなので、
     * 2.0 と同じく 1 枚目から読んで全枚に使う。
     */
    public VideoFileTools.ImageSize readImageSize(Path snapshot) throws IOException {
        return videoFileTools.readImageSize(snapshot);
    }

    /**
     * 保存ルートからの相対パス（DB の 保存パス に入れる値）。
     * 保存ルートの外にあるときは、そのまま絶対パスを返す。
     */
    public static String toRelativePath(Path root, Path file) {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        Path absoluteFile = file.toAbsolutePath().normalize();
        if (!absoluteFile.startsWith(absoluteRoot)) {
            return absoluteFile.toString();
        }
        return absoluteRoot.relativize(absoluteFile).toString().replace('\\', '/');
    }

    /**
     * 切り出した 1 枚分（DB に登録する内容）。
     *
     * @param offsetMillis 動画の先頭からの位置（ミリ秒）
     * @param capturedAt   撮影日時（撮影開始日時 + オフセット）
     * @param fileName     画像ファイル名（snapshot_%06d.jpg）
     * @param relativePath 保存ルートからの相対パス
     */
    public record StagedSnapshot(long offsetMillis, LocalDateTime capturedAt,
                                 String fileName, String relativePath) {
    }
}
