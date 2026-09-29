package com.study21.user.english;

import com.study21.common.core.exception.ValidationException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 英作文の画像（設問画像・答案画像）をファイルで保存・配信する。
 *
 * <p>実体を DB に持たないのは `ENG_英作文画像情報` の方針（列は場所と MIME だけ）。
 * 数百 KB〜数 MB の写真を DB へ入れると、一覧もバックアップも重くなる。</p>
 *
 * <p>置き場は `application.yml` の `study21.english-essay.storage-root`（設定表ではなくプロパティ。
 * 既存の `*FileStorage` と同じ）。規約:
 * {@code english-essay/{アカウントID}/{yyyyMM}/{uuid}.{ext}}。この規約を
 * **所有者チェックの根拠**にもする（他人のディレクトリは触れない）。
 * <strong>根は `english-essay/` の親</strong>（相対パス側が `english-essay/…` で始まるため。
 * 根を `english-essay` 自身にすると 1 段深く探して「実体がありません」になる。2026-09-28 修正）。</p>
 *
 * <p>パストラバーサル対策は {@code GeometryAiStorage} / {@code TempFileStorage} の作法をそのまま使う
 * （{@link #requireUnder} / {@link #safeStoredName}）。</p>
 *
 * <p><strong>WebP は JDK の ImageIO でデコードできない</strong>ので、寸法（読めるかどうか）の確認は
 * PNG / JPEG だけで行い、WebP は大きさだけで通す（`GeometryAiStorage.java` の判断と同じ）。
 * 英作文の画像は**縮小も切り抜きもしない**（OCR は admin-api が解像度を決めて送る）ので、
 * WebP でも困らない。</p>
 */
@Component
public class EnglishEssayStorage {

    private static final Logger log = LoggerFactory.getLogger(EnglishEssayStorage.class);

    /** 受け付ける拡張子（2.0 と同じ 3 形式）。 */
    private static final List<String> ACCEPTED_EXTENSIONS = List.of("png", "jpg", "jpeg", "webp");
    /** `/api/user/english-essays/{id}/images` が受け付ける MIME。 */
    public static final List<String> IMAGE_MIME_TYPES = List.of("image/png", "image/jpeg", "image/webp");
    /** サーバーで寸法を読める拡張子（**WebP は入っていない**）。 */
    private static final List<String> DECODABLE_EXTENSIONS = List.of("png", "jpg", "jpeg");

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    /** 原本ファイル名の上限（`ENG_英作文画像情報.原本ファイル名` は VARCHAR(300)）。 */
    private static final int ORIGINAL_NAME_MAX = 300;

    private final Path root;

    public EnglishEssayStorage(
            @Value("${study21.english-essay.storage-root:${user.dir}/data}") String storageRoot) {
        this.root = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    /** 起動時に**自分の置き場**を記録する（配備でボリュームの割り当てを間違えないため）。 */
    @PostConstruct
    void logStorageRoot() {
        log.info("英作文の画像置き場（user-api）: {} （既存: {}）", root, Files.isDirectory(root));
    }

    /** 保存した画像 1 件（`相対パス` はストレージルートからの相対パス）。 */
    public record StoredImage(String relativePath, String storedFileName, String originalFileName,
                              String mimeType, long size) {
    }

    public Path getRoot() {
        return root;
    }

    /**
     * 画像 1 枚を保存する。
     *
     * @param maxMb 設定 `ENGLISH_ESSAY_MAX_IMAGE_MB`（既定 10）
     * @throws ValidationException 空・形式違い・大きすぎ・読めない画像のとき（**日本語の理由**）
     */
    public StoredImage store(long accountId, MultipartFile file, int maxMb) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("画像が選ばれていません。");
        }
        String original = safeOriginalName(file.getOriginalFilename());
        String extension = extensionOf(original);
        if (!ACCEPTED_EXTENSIONS.contains(extension)) {
            throw new ValidationException("PNG・JPEG・WebP の画像を選んでください。");
        }
        String mime = file.getContentType() == null ? "" : file.getContentType().trim().toLowerCase(Locale.ROOT);
        if (!IMAGE_MIME_TYPES.contains(mime)) {
            throw new ValidationException("画像の形式を確認できませんでした。PNG・JPEG・WebP を選んでください。");
        }
        long maxBytes = (long) Math.max(1, maxMb) * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new ValidationException("画像は " + Math.max(1, maxMb) + " MB までです。");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException cause) {
            throw new ValidationException("画像を読み込めませんでした。もう一度選んでください。");
        }
        if (DECODABLE_EXTENSIONS.contains(extension)) {
            // 壊れた画像・画像でないファイルを先に断る（保存してから気づくより分かりやすい）
            readDimension(bytes);
        }
        // WebP は JDK がデコードできないので、ここでは何もしない（大きさだけで通す）

        String relativeDir = "english-essay/" + accountId + "/" + LocalDate.now().format(MONTH);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = requireUnder(relativeDir, storedName);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException cause) {
            log.error("英作文の画像を保存できませんでした。path={}", target, cause);
            throw new ValidationException("画像を保存できませんでした。時間をおいてもう一度お試しください。");
        }
        return new StoredImage(relativeDir, storedName, original, mime, bytes.length);
    }

    /** 保存済みの画像の場所を解決する（`相対パス` と `保存ファイル名` は DB に入っている値）。 */
    public Path resolve(String relativePath, String storedFileName) {
        if (relativePath == null || relativePath.isBlank() || storedFileName == null || storedFileName.isBlank()) {
            throw new ValidationException("画像の指定が正しくありません。");
        }
        return requireUnder(relativePath, storedFileName);
    }

    // -------------------------------------------------------------------- 内部

    /** 相対パスをルート配下に解決する（`..` や絶対パスは拒否）。 */
    public Path requireUnder(String relativeDir, String fileName) {
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

    /** 相対ディレクトリ（`/` 区切り。`..` や絶対パスは拒否）。 */
    private static String safeRelativeDir(String relativeDir) {
        String value = relativeDir == null ? "" : relativeDir.trim().replace('\\', '/');
        if (value.startsWith("/") || value.contains("..") || value.contains("\0")) {
            throw new ValidationException("保存先の指定が正しくありません。");
        }
        return value;
    }

    /** 保存名（UUID + 拡張子だけを許す）。 */
    private static String safeStoredName(String fileName) {
        String value = fileName == null ? "" : fileName.trim();
        if (value.isEmpty() || value.contains("/") || value.contains("\\") || value.contains("..")
                || value.contains("\0") || value.startsWith(".")) {
            throw new ValidationException("ファイル名の指定が正しくありません。");
        }
        return value;
    }

    /** 原本ファイル名（画面に出すだけ。パスは落とし、長さは列に合わせる）。 */
    private static String safeOriginalName(String input) {
        String normalized = input == null ? "" : input.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) {
            name = "image";
        }
        return name.length() <= ORIGINAL_NAME_MAX ? name : name.substring(name.length() - ORIGINAL_NAME_MAX);
    }

    private static String extensionOf(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 画像の寸法だけを読む（デコードはしない）。
     *
     * <p>`ImageIO.read` は巨大画像で OOM になり得るので、`ImageReader` で寸法だけ取る
     * （`GeometryAiStorage` と同じ）。読めなければ「画像ではない」＝日本語で断る。</p>
     */
    private static int[] readDimension(byte[] bytes) {
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (stream == null) {
                throw new ValidationException("画像を読み込めませんでした。PNG か JPEG を選んでください。");
            }
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new ValidationException("画像を読み込めませんでした。PNG か JPEG を選んでください。");
            }
            var reader = readers.next();
            try {
                reader.setInput(stream);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new ValidationException("画像の大きさを読み取れませんでした。");
                }
                return new int[]{width, height};
            } finally {
                reader.dispose();
            }
        } catch (IOException cause) {
            throw new ValidationException("画像を読み込めませんでした。PNG か JPEG を選んでください。");
        }
    }
}
