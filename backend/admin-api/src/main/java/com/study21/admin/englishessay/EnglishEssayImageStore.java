package com.study21.admin.englishessay;

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
 * 英作文の画像を読む（admin-api 側・batC11 の OCR）。
 *
 * <p>画像は user-api が保存したものを<b>同じ置き場</b>から読む
 * （{@code study21.english-essay.storage-root}。配備では両サービスに同じボリュームを割り当てる。
 * AI 生図・授業録音と同じ考え方）。DB には 相対パス と 保存ファイル名 だけを持つ。
 * <strong>根は `english-essay/` の親</strong>（相対パスが `english-essay/…` で始まるため。
 * 2026-09-28 修正）。</p>
 */
@Component
public class EnglishEssayImageStore {

    private static final Logger log = LoggerFactory.getLogger(EnglishEssayImageStore.class);

    private final Path root;

    public EnglishEssayImageStore(
            @Value("${study21.english-essay.storage-root:${user.dir}/data}") String storageRoot) {
        this.root = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    @PostConstruct
    void logStorageRoot() {
        log.info("英作文の画像の置き場（admin-api / batC11）: {} （既存: {}）", root, Files.isDirectory(root));
    }

    public Path getRoot() {
        return root;
    }

    /**
     * 1 枚読む。
     *
     * @return 画像のバイト列。無い・読めないときは null（理由は呼び出し側が日本語で組み立てる）
     */
    public byte[] read(String relativeDir, String fileName) {
        if (relativeDir == null || relativeDir.isBlank() || fileName == null || fileName.isBlank()) {
            return null;
        }
        Path path;
        try {
            path = requireUnder(relativeDir, fileName);
        } catch (ValidationException cause) {
            log.warn("英作文の画像のパスが不正です。dir={} name={}", relativeDir, fileName);
            return null;
        }
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException cause) {
            log.warn("英作文の画像を読めませんでした。path={}", path, cause);
            return null;
        }
    }

    /** 置き場の外へ出ないことを確かめる（{@code 相対パス} と ファイル名 は画面由来なので）。 */
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
