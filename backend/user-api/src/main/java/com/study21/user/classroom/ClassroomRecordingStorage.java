package com.study21.user.classroom;

import com.study21.common.core.exception.ValidationException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 授業録音の音声（分塊を追記した 1 本のファイル）を保存・配信する。
 *
 * <p>音声を DB に持たないのは、長時間の録音（最大 120 分）が数十 MB になるため
 * （AI 生図の画像と同じ「パス + ファイル名」方式）。置き場は {@code study21.classroom.storage-root}。
 * 規約: {@code classroom/{登録者アカウントID}/{yyyyMM}/{uuid}.{ext}}。この規約を
 * **所有者チェックの根拠**にもする（他人のディレクトリは触れない）。</p>
 *
 * <p>ブラウザの {@code MediaRecorder}（webm/opus）の分塊は同じ EBML ストリームのバイト列なので、
 * **到着順に追記すれば再生可能な 1 本の webm になる**（分塊は 1 リクエスト数 MB なので、
 * `multipart.max-file-size` は余裕）。パストラバーサル対策は {@code GeometryAiStorage} と同じ
 * {@link #requireUnder} / {@link #safeStoredName} を使う。</p>
 */
@Component
public class ClassroomRecordingStorage {

    private static final Logger log = LoggerFactory.getLogger(ClassroomRecordingStorage.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final Path root;

    public ClassroomRecordingStorage(
            @Value("${study21.classroom.storage-root:${user.dir}/data/classroom}") String storageRoot) {
        this.root = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    @PostConstruct
    void logStorageRoot() {
        log.info("授業録音の置き場（user-api）: {} （既存: {}）", root, Files.isDirectory(root));
    }

    public Path getRoot() {
        return root;
    }

    /** 保存するファイル 1 本（パスはストレージルートからの相対パス）。 */
    public record StoredRecording(String relativeDir, String fileName, String mime) {
    }

    /** 録音を保存する相対パス・ファイル名・MIME を決める（実体は最初の分塊で作る）。 */
    public StoredRecording newRecording(long accountId, String mime) {
        String normalizedMime = normalizeMime(mime);
        String extension = extensionOf(normalizedMime);
        String relativeDir = "classroom/" + accountId + "/" + LocalDate.now().format(MONTH);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        return new StoredRecording(relativeDir, fileName, normalizedMime);
    }

    /** 分塊のバイト列を追記する（無ければ新規作成）。 */
    public void append(String relativeDir, String fileName, byte[] bytes) {
        Path target = requireUnder(relativeDir, fileName);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes == null ? new byte[0] : bytes,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException cause) {
            log.error("授業録音を保存できませんでした。path={}", target, cause);
            throw new ValidationException("録音を保存できませんでした。時間をおいてもう一度お試しください。");
        }
    }

    /** 実体のパスを解決する（相対パスは DB に入っている値。トラバーサルは弾く）。 */
    public Path resolve(String relativeDir, String fileName) {
        if (relativeDir == null || relativeDir.isBlank() || fileName == null || fileName.isBlank()) {
            return null;
        }
        return requireUnder(relativeDir, fileName);
    }

    /** ファイルを消す（録音の削除・保持期限切れの掃除）。無ければ false。 */
    public boolean delete(String relativeDir, String fileName) {
        if (relativeDir == null || relativeDir.isBlank() || fileName == null || fileName.isBlank()) {
            return false;
        }
        Path path;
        try {
            path = requireUnder(relativeDir, fileName);
        } catch (ValidationException cause) {
            log.warn("授業録音のパスが不正です。dir={} name={}", relativeDir, fileName);
            return false;
        }
        try {
            return Files.deleteIfExists(path);
        } catch (IOException cause) {
            log.warn("授業録音を消せませんでした。path={}", path, cause);
            return false;
        }
    }

    /** 配信の Content-Type（拡張子から決める）。 */
    public String mimeOf(String fileName) {
        return switch (extensionOf(fileName)) {
            case "ogg" -> "audio/ogg";
            case "mp4" -> "audio/mp4";
            case "wav" -> "audio/wav";
            default -> "audio/webm";
        };
    }

    // -------------------------------------------------------------------- 内部

    private Path requireUnder(String relativeDir, String fileName) {
        Path base = root.resolve(safeRelativeDir(relativeDir)).normalize();
        if (!base.startsWith(root)) {
            throw new ValidationException("保存先の指定が正しくありません。");
        }
        Path target = base.resolve(safeStoredName(fileName)).normalize();
        if (!target.startsWith(base)) {
            throw new ValidationException("ファイル名の指定が正しくありません。");
        }
        return target;
    }

    private static String safeRelativeDir(String relativeDir) {
        String value = relativeDir == null ? "" : relativeDir.trim().replace('\\', '/');
        if (value.startsWith("/") || value.contains("..") || value.contains("\0")) {
            throw new ValidationException("保存先の指定が正しくありません。");
        }
        return value;
    }

    private static String safeStoredName(String fileName) {
        String value = fileName == null ? "" : fileName.trim();
        if (value.isEmpty() || value.contains("/") || value.contains("\\") || value.contains("..")
                || value.contains("\0") || value.startsWith(".")) {
            throw new ValidationException("ファイル名の指定が正しくありません。");
        }
        return value;
    }

    private static String normalizeMime(String mime) {
        if (mime == null || mime.isBlank()) {
            return "audio/webm";
        }
        int semicolon = mime.indexOf(';');
        String value = (semicolon < 0 ? mime : mime.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? "audio/webm" : value;
    }

    private static String extensionOf(String mimeOrName) {
        String value = mimeOrName == null ? "" : mimeOrName.toLowerCase(Locale.ROOT);
        if (value.startsWith("audio/") || value.startsWith("video/")) {
            return switch (value) {
                case "audio/ogg", "application/ogg" -> "ogg";
                case "audio/mp4", "video/mp4", "audio/mp3", "audio/mpeg" -> "mp4";
                case "audio/wav", "audio/x-wav" -> "wav";
                default -> "webm";
            };
        }
        int dot = value.lastIndexOf('.');
        if (dot < 0 || dot == value.length() - 1) {
            return "webm";
        }
        return value.substring(dot + 1);
    }
}
