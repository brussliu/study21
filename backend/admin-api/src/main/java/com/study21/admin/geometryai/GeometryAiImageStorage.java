package com.study21.admin.geometryai;

import com.study21.common.core.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Locale;

/**
 * AI 生図の画像を読む・切り抜く・縮小する（batC51 の前処理）。
 *
 * <p>元画像は user-api が保存したものを**同じ置き場**から読む（`study21.geometry-ai.storage-root`。
 * 配備では両サービスに同じボリュームを割り当てる）。書き出すのは**切り抜き済み・縮小済みの
 * 「AI へ送る画像」**で、DB の `切抜画像パス` / `切抜画像名称` に記録する。</p>
 *
 * <p>パストラバーサル対策は user-api の `GeometryAiStorage` と同じ（`requireUnder` / 相対パス検証）。
 * 画像処理は **JDK の `ImageIO` + `BufferedImage` + `Graphics2D` だけ**を使う
 * （このリポジトリに画像処理ライブラリの依存は無い）。</p>
 *
 * <p>**再エンコードするので EXIF（位置情報・撮影機種）は落ちる**（設計 §7）。
 * 同じ要求を 2 回処理しても出力ファイルは同じパスに上書きされ、結果は同じ（冪等）。</p>
 */
@Component
public class GeometryAiImageStorage {

    private static final Logger log = LoggerFactory.getLogger(GeometryAiImageStorage.class);
    /** JPEG の品質（図形の線は十分読める。写真に効く。設計 §6.5）。 */
    private static final float JPEG_QUALITY = 0.85f;

    private final Path root;

    public GeometryAiImageStorage(
            @Value("${study21.geometry-ai.storage-root:${user.dir}/data/geometry-ai}") String storageRoot) {
        this.root = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    /**
     * 起動時に**自分の置き場**を記録する。
     *
     * <p>AI 生図の元画像は user-api が書き、切り抜きはこのサービス（batC51）が読むので、
     * **両サービスが同じ置き場を指している必要がある**（`STUDY21_GEOMETRY_AI_STORAGE_ROOT`）。
     * ずれていると「元画像が見つかりません（NO_IMAGE）」になるだけでは原因が分からないので、
     * 起動ログとエラーメッセージに絶対パスを出す。</p>
     */
    @PostConstruct
    void logStorageRoot() {
        log.info("AI 生図の画像置き場（admin-api / batC51）: {} （既存: {}）", root, Files.isDirectory(root));
    }

    /** 設定された置き場（診断用。起動ログとエラー メッセージに出る）。 */
    public Path getRoot() {
        return root;
    }

    /**
     * 相対パスから**期待する絶対パス**を組み立てる（診断用。ファイルの有無は見ない）。
     * パスが不正なときはその旨を返す（例外は投げない）。
     */
    public String absolutePathOf(String relativeDir, String fileName) {
        try {
            return requireUnder(relativeDir, fileName).toString();
        } catch (RuntimeException cause) {
            return "(指定が不正: " + relativeDir + "/" + fileName + ")";
        }
    }

    /** 保存した画像 1 件（パスはストレージルートからの相対パス）。 */
    public record StoredImage(String relativePath, String fileName, String mime, long size,
                              int width, int height) {
    }

    /**
     * 「見つからない」ことを、**期待した絶対パスつき**で説明する（設計 §6.4 の診断）。
     * user-api と admin-api の置き場がずれているときに一目で分かるようにする。
     */
    public String missingImageMessage(String relativeDir, String fileName) {
        return "元画像が見つかりません（期待したパス: " + absolutePathOf(relativeDir, fileName) + "）。"
                + "AI 生図の画像は user-api と admin-api が同じ置き場を共有する必要があります"
                + "（study21.geometry-ai.storage-root / STUDY21_GEOMETRY_AI_STORAGE_ROOT。"
                + "このサービスの置き場: " + root + "）。";
    }

    /** 画像を読む（無ければ null）。 */
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

    /**
     * 切り抜いて縮小した画像を作る（**同じ要求なら同じパスに上書き**）。
     *
     * @param cropX   切り抜き範囲（元画像に対する正規化 0..1）
     * @param maxEdge 設定 `GEOMETRY_AI_MAX_IMAGE_PIXELS`（AI へ送る画像の最大辺）
     * @return 書き出した画像（デコードできない画像は {@link DecodeException}）
     */
    public StoredImage cropAndResize(String relativeDir, String fileName, String storedName,
                                     BigDecimal cropX, BigDecimal cropY, BigDecimal cropW, BigDecimal cropH,
                                     int maxEdge) throws DecodeException {
        byte[] bytes = read(relativeDir, fileName);
        if (bytes == null) {
            throw new DecodeException("元画像が見つかりません。");
        }
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException cause) {
            throw new DecodeException("画像を読み込めませんでした。");
        }
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            // デコード失敗はリトライしても無駄（設計 §6.4）
            throw new DecodeException("画像の形式を判別できませんでした（PNG / JPEG をお使いください）。");
        }

        BufferedImage cropped = crop(source, cropX, cropY, cropW, cropH);
        BufferedImage resized = resize(cropped, maxEdge);
        // 出力形式は保存名の拡張子で決める（DB に記録した名前と中身を食い違わせない）
        boolean png = "png".equalsIgnoreCase(extensionOf(storedName));
        byte[] encoded = png ? encodePng(resized) : encodeJpeg(resized);

        Path target = requireUnder(relativeDir, storedName);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, encoded);
        } catch (IOException cause) {
            log.error("AI 生図の切り抜き画像を保存できませんでした。path={}", target, cause);
            throw new DecodeException("切り抜いた画像を保存できませんでした。");
        }
        return new StoredImage(relativeDir, storedName, png ? "image/png" : "image/jpeg",
                encoded.length, resized.getWidth(), resized.getHeight());
    }

    /** ファイルを消す（保持日数を過ぎた画像のクリーンアップ）。無ければ false。 */
    public boolean delete(String relativeDir, String fileName) {
        if (relativeDir == null || relativeDir.isBlank() || fileName == null || fileName.isBlank()) {
            return false;
        }
        Path path;
        try {
            path = requireUnder(relativeDir, fileName);
        } catch (ValidationException cause) {
            log.warn("AI 生図の画像パスが不正です。dir={} name={}", relativeDir, fileName);
            return false;
        }
        try {
            return Files.deleteIfExists(path);
        } catch (IOException cause) {
            log.warn("AI 生図の画像を消せませんでした。path={}", path, cause);
            return false;
        }
    }

    // -------------------------------------------------------------------- 内部

    /** 切り抜き画像のファイル名（要求番号 + `-crop`。再実行で同じパスに上書きする）。 */
    public static String croppedName(String requestNo, String originalName) {
        String extension = "png".equalsIgnoreCase(extensionOf(originalName)) ? "png" : "jpg";
        return requestNo + "-crop." + extension;
    }

    /** 正規化座標で切り抜く。 */
    private static BufferedImage crop(BufferedImage source, BigDecimal cropX, BigDecimal cropY,
                                     BigDecimal cropW, BigDecimal cropH) {
        int width = source.getWidth();
        int height = source.getHeight();
        int x = (int) Math.floor(scale(cropX, width));
        int y = (int) Math.floor(scale(cropY, height));
        int w = (int) Math.ceil(scale(cropW, width));
        int h = (int) Math.ceil(scale(cropH, height));
        x = Math.max(0, Math.min(x, width - 1));
        y = Math.max(0, Math.min(y, height - 1));
        w = Math.max(1, Math.min(w, width - x));
        h = Math.max(1, Math.min(h, height - y));
        if (x == 0 && y == 0 && w == width && h == height) {
            return source;
        }
        // getSubimage は元画像を参照し続けるので、コピーしてから返す
        BufferedImage sub = source.getSubimage(x, y, w, h);
        BufferedImage copy = new BufferedImage(sub.getWidth(), sub.getHeight(),
                sub.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(sub, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    /** 最大辺を maxEdge 以下に縮小する（既に小さければそのまま）。 */
    private static BufferedImage resize(BufferedImage source, int maxEdge) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);
        if (maxEdge <= 0 || longest <= maxEdge) {
            return source;
        }
        double ratio = (double) maxEdge / longest;
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));
        BufferedImage target = new BufferedImage(targetWidth, targetHeight,
                source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static byte[] encodePng(BufferedImage image) throws DecodeException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException cause) {
            throw new DecodeException("画像を作り直せませんでした（PNG）。");
        }
    }

    private static byte[] encodeJpeg(BufferedImage image) throws DecodeException {
        BufferedImage rgb = image;
        if (image.getColorModel().hasAlpha()) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgb.createGraphics();
            try {
                graphics.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
            } finally {
                graphics.dispose();
            }
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            return encodePng(rgb);
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(rgb, null, null), param);
            return out.toByteArray();
        } catch (IOException cause) {
            throw new DecodeException("画像を作り直せませんでした（JPEG）。");
        } finally {
            writer.dispose();
        }
    }

    private static boolean hasAlpha(BufferedImage image) {
        return image.getColorModel().hasAlpha();
    }

    private static double scale(BigDecimal value, int size) {
        if (value == null) {
            return size;
        }
        return value.doubleValue() * size;
    }

    private static String extensionOf(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    /** 相対パスをルート配下に解決する（`../` などは拒否。user-api と同じ作法）。 */
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

    /** 画像をデコードできなかった（リトライしても無駄な失敗）。 */
    public static class DecodeException extends Exception {
        public DecodeException(String message) {
            super(message);
        }
    }
}
