package com.study21.admin.classroomai;

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

/**
 * 授業録音の音声ファイルを読む・消す（admin-api 側・batR02 の掃除）。
 *
 * <p>音声は user-api が保存したものを**同じ置き場**から読む（`study21.classroom.storage-root`。
 * 配備では両サービスに同じボリュームを割り当てる）。ここは保持期限切れの削除だけに使う。</p>
 */
@Component
public class ClassroomRecordingStorage {

    private static final Logger log = LoggerFactory.getLogger(ClassroomRecordingStorage.class);

    private final Path root;

    public ClassroomRecordingStorage(
            @Value("${study21.classroom.storage-root:${user.dir}/data/classroom}") String storageRoot) {
        this.root = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    @PostConstruct
    void logStorageRoot() {
        log.info("授業録音の置き場（admin-api / batR02）: {} （既存: {}）", root, Files.isDirectory(root));
    }

    public Path getRoot() {
        return root;
    }

    /** ファイルを消す（無ければ false）。パスが不正なら例外にせず false。 */
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

    /**
     * その録音の**分塊**（`chunk-{記録ID}-*.webm`）を消す（保持期限切れの掃除）。
     *
     * <p>ファイル名に記録 ID が入っているので、置き場を他の記録と共有していても
     * （改修前から続いている録音の月ごとのディレクトリ）巻き込まない。</p>
     */
    public int deleteChunks(String relativeDir, long recordId) {
        if (relativeDir == null || relativeDir.isBlank()) {
            return 0;
        }
        Path base;
        try {
            base = requireUnder(relativeDir, "work").getParent();
        } catch (ValidationException cause) {
            log.warn("授業録音のパスが不正です。dir={}", relativeDir);
            return 0;
        }
        if (base == null || !Files.isDirectory(base)) {
            return 0;
        }
        String prefix = "chunk-" + recordId + "-";
        int deleted = 0;
        try (java.util.stream.Stream<Path> entries = Files.list(base)) {
            for (Path entry : entries.toList()) {
                if (Files.isRegularFile(entry) && entry.getFileName().toString().startsWith(prefix)
                        && Files.deleteIfExists(entry)) {
                    deleted += 1;
                }
            }
            // 空になった置き場だけ消す（中身が残っていれば他の記録のもの）
            try (java.util.stream.Stream<Path> left = Files.list(base)) {
                if (left.findAny().isEmpty()) {
                    Files.deleteIfExists(base);
                }
            }
        } catch (IOException cause) {
            log.warn("授業録音の分塊を消せませんでした。dir={}", relativeDir, cause);
        }
        return deleted;
    }

    private Path requireUnder(String relativeDir, String fileName) {
        String dir = relativeDir == null ? "" : relativeDir.trim().replace('\\', '/');
        if (dir.startsWith("/") || dir.contains("..") || dir.contains("\0")) {
            throw new ValidationException("保存先の指定が正しくありません。");
        }
        Path base = root.resolve(dir).normalize();
        if (!base.startsWith(root)) {
            throw new ValidationException("保存先の指定が正しくありません。");
        }
        String name = fileName == null ? "" : fileName.trim();
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..")
                || name.contains("\0") || name.startsWith(".")) {
            throw new ValidationException("ファイル名の指定が正しくありません。");
        }
        Path target = base.resolve(name).normalize();
        if (!target.startsWith(base)) {
            throw new ValidationException("ファイル名の指定が正しくありません。");
        }
        return target;
    }
}
