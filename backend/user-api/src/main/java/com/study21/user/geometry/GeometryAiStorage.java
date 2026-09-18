package com.study21.user.geometry;

import com.study21.common.core.exception.ValidationException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * AI 生図の画像（元画像）をファイルで保存・配信する。
 *
 * <p>画像を DB に持たないのは `GEO_図形情報` のサムネイルとは方針が違うため:
 * アップロード画像は数百 KB〜数 MB で、AI へ送る前に作り直す中間生成物であり、
 * 一覧に出すのは切り抜きプレビューだけ（設計 §3.1）。</p>
 *
 * <p>置き場は `application.yml` の `study21.geometry-ai.storage-root`（既存の
 * `*FileStorage` と同じく設定表ではなくプロパティで持つ）。規約:
 * `geometry-ai/{登録者アカウントID}/{yyyyMM}/{uuid}.{ext}`。この規約を
 * **所有者チェックの根拠**にもする（他人のディレクトリは触れない）。
 * 切り抜き画像は `batC51`（admin-api）が同じ規約で作る。</p>
 *
 * <p>パストラバーサル対策は {@code TempFileStorage} の作法をそのまま使う
 * （{@link #requireUnder} / {@link #safeStoredName} / {@link #extensionOf}）。</p>
 */
@Component
public class GeometryAiStorage {

    private static final Logger log = LoggerFactory.getLogger(GeometryAiStorage.class);

    /**
     * サーバーでデコードできる拡張子。
     * **WebP は入っていない**（JDK の ImageIO に WebP のリーダーが無い）。画面は WebP も
     * 受け付けるが、切り抜きと縮小はサーバーで行うため、アップロード時に日本語で断る。
     */
    private static final List<String> DECODABLE_EXTENSIONS = List.of("png", "jpg", "jpeg");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final Path root;

    public GeometryAiStorage(@Value("${study21.geometry-ai.storage-root:${user.dir}/data/geometry-ai}") String storageRoot) {
        this.root = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    /**
     * 起動時に**自分の置き場**を記録する。
     *
     * <p>元画像はここ（user-api）が書き、切り抜きは admin-api の batC51 が読むので、
     * **両サービスが同じ置き場を指している必要がある**（`STUDY21_GEOMETRY_AI_STORAGE_ROOT`）。
     * ずれていると batC51 が「元画像が見つかりません（NO_IMAGE）」になるので、
     * 起動ログに絶対パスを出して突き合わせられるようにする。</p>
     */
    @PostConstruct
    void logStorageRoot() {
        log.info("AI 生図の画像置き場（user-api）: {} （既存: {}）", root, Files.isDirectory(root));
    }

    /** 保存した画像 1 件（パスはストレージルートからの相対パス）。 */
    public record StoredImage(String relativePath, String fileName, String mime, long size,
                              int width, int height, /** `/requests` に渡す一時トークン */
                              String token) {
    }

    public Path getRoot() {
        return root;
    }

    /**
     * 元画像を保存する（AI へはまだ送らない）。
     *
     * @param maxMb   設定 `GEOMETRY_AI_MAX_IMAGE_MB`
     * @param maxEdge 設定 `GEOMETRY_AI_MAX_IMAGE_PIXELS`（この 4 倍を超える画像は先に拒否する）
     */
    public StoredImage storeOriginal(long accountId, MultipartFile file, int maxMb, int maxEdge) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("画像が選ばれていません。");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extensionOf(original);
        if (!DECODABLE_EXTENSIONS.contains(extension)) {
            // 画面は PNG / JPEG / WebP を受け付けるが、**サーバーは WebP をデコードできない**
            // （JDK の ImageIO に WebP は無い）。切り抜きと縮小はサーバーで行うので、
            // 先に理由を出して選び直してもらう（後で失敗させるより分かりやすい）。
            throw new ValidationException("PNG か JPEG の画像を選んでください（WebP は AI 生図で扱えません）。");
        }
        String mime = file.getContentType() == null ? "" : file.getContentType().trim();
        if (!GeometryAiModels.IMAGE_MIME_TYPES.contains(mime)) {
            throw new ValidationException("画像の形式を確認できませんでした。PNG か JPEG を選んでください。");
        }
        long maxBytes = (long) maxMb * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new ValidationException("画像は " + maxMb + " MB までです。");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException cause) {
            throw new ValidationException("画像を読み込めませんでした。もう一度選んでください。");
        }

        // 画素数の上限は**デコードの前に**判定する（巨大画像で OOM になり得るため）
        int[] size = readDimension(bytes);
        long limitEdge = (long) maxEdge * 4L;
        if (Math.max(size[0], size[1]) > limitEdge) {
            throw new ValidationException("画像が大きすぎます（長辺 " + limitEdge + " px まで。"
                    + "AI へ送る画像は最大辺 " + maxEdge + " px に縮小します）。");
        }

        String relativeDir = "geometry-ai/" + accountId + "/" + LocalDate.now().format(MONTH);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = requireUnder(relativeDir, storedName);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException cause) {
            log.error("AI 生図の画像を保存できませんでした。path={}", target, cause);
            throw new ValidationException("画像を保存できませんでした。時間をおいてもう一度お試しください。");
        }
        return new StoredImage(relativeDir, storedName, mime, bytes.length, size[0], size[1],
                tokenOf(accountId, relativeDir, storedName));
    }

    /** `imageToken` から保存済みの相対パスを復元する（自分のディレクトリの画像だけ）。 */
    public String resolveToken(long accountId, String token) {
        if (token == null || token.isBlank()) {
            throw new ValidationException("画像が選ばれていません。");
        }
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(token.trim()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException cause) {
            throw new ValidationException("画像の指定が正しくありません。画像を選び直してください。");
        }
        String[] parts = decoded.split("\n");
        if (parts.length != 3) {
            throw new ValidationException("画像の指定が正しくありません。画像を選び直してください。");
        }
        long owner = parseLongOrZero(parts[0]);
        String relativeDir = parts[1];
        String fileName = parts[2];
        if (owner != accountId) {
            // 他人が作った画像は使えない（署名ではなく所有者で守る）
            throw new ValidationException("画像の指定が正しくありません。画像を選び直してください。");
        }
        if (!relativeDir.startsWith("geometry-ai/" + accountId + "/")) {
            throw new ValidationException("画像の指定が正しくありません。画像を選び直してください。");
        }
        Path path = requireUnder(relativeDir, fileName);
        if (!Files.isReadable(path)) {
            throw new ValidationException("画像が見つかりません。もう一度アップロードしてください。");
        }
        return relativeDir + "/" + fileName;
    }

    /**
     * 保存済みファイルの情報を読み直す（`/requests` が受け取った `imageToken` から使う）。
     *
     * <p>寸法・大きさは保存時に分かっているが、トークンには入れない（DB の列に入れる値なので
     * 改ざんできない経路で取り直す）。</p>
     */
    public StoredImage load(String relativeFilePath) {
        int index = relativeFilePath == null ? -1 : relativeFilePath.lastIndexOf('/');
        if (index <= 0 || index == relativeFilePath.length() - 1) {
            throw new ValidationException("画像の指定が正しくありません。画像を選び直してください。");
        }
        String relativeDir = relativeFilePath.substring(0, index);
        String fileName = relativeFilePath.substring(index + 1);
        Path path = requireUnder(relativeDir, fileName);
        if (!Files.isReadable(path)) {
            throw new ValidationException("画像が見つかりません。もう一度アップロードしてください。");
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            int[] size = readDimension(bytes);
            return new StoredImage(relativeDir, fileName, mimeOf(fileName), bytes.length, size[0], size[1], null);
        } catch (IOException cause) {
            throw new ValidationException("画像を読み込めませんでした。もう一度アップロードしてください。");
        }
    }

    /** 保存済みの画像を読む（相対パスは DB に入っている値。トラバーサルは弾く）。 */
    public byte[] read(String relativeDir, String fileName) {
        if (relativeDir == null || relativeDir.isBlank() || fileName == null || fileName.isBlank()) {
            return null;
        }
        Path path = requireUnder(relativeDir, fileName);
        if (!Files.isReadable(path)) {
            return null;
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException cause) {
            log.warn("AI 生図の画像を読めませんでした。path={}", path, cause);
            return null;
        }
    }

    /** 画像の MIME（拡張子から決める。配信の Content-Type に使う）。 */
    public String mimeOf(String fileName) {
        String extension = extensionOf(fileName);
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            default -> "image/png";
        };
    }

    // -------------------------------------------------------------------- 内部

    /** 相対パスをルート配下に解決する（`../` などは拒否）。 */
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

    private static String tokenOf(long accountId, String relativeDir, String fileName) {
        String raw = accountId + "\n" + relativeDir + "\n" + fileName;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static long parseLongOrZero(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException cause) {
            return -1L;
        }
    }

    private static String extensionOf(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 画像の寸法だけを読む（デコードしない）。
     *
     * <p>`ImageIO.read` は巨大画像で OOM になり得るため、先に `ImageReader` で寸法を取る
     * （設計 §7 の画素数チェック）。</p>
     */
    private static int[] readDimension(byte[] bytes) {
        try (var stream = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            if (stream == null) {
                throw new ValidationException("画像を読み込めませんでした。PNG か JPEG を選んでください。");
            }
            var readers = javax.imageio.ImageIO.getImageReaders(stream);
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
