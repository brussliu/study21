package com.study21.user.testinfo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * テスト情報の試験用紙ファイルの保存規則を隠蔽する深いモジュール。
 * 2.0 の `file/TESTINFO/{yyyyMM}/{テスト番号}/` を踏襲しつつ、
 * 2.1 の家族所有権（families/{家族学生ID}/…）配下に置く。
 * 縮小画像（500/200/50px）は PNG base64 の文字列として DB に保存し、
 * SHA-256 とファイルサイズは移行・整合性検証用に記録する。
 */
@Component
public class TestFileStorage {
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final List<String> IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "gif", "bmp", "webp");
    private static final String CONTENT_DIR = "families";

    private final Path storageRoot;

    public TestFileStorage(@Value("${study21.test-files.storage-root:${user.dir}/data/test-files}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public StoredFile store(long familyStudentId, String testNo, MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw TestInfoApiException.invalid("空のファイルは保存できません。");
        }
        try (InputStream input = upload.getInputStream()) {
            return storeBytes(familyStudentId, testNo, safeOriginalName(upload.getOriginalFilename()),
                    upload.getContentType(), input.readAllBytes());
        } catch (IOException ex) {
            throw new IllegalStateException("試験用紙ファイルの保存に失敗しました。", ex);
        }
    }

    /** 既存バイト列（臨時ファイル・他テストのファイル・回転済み画像）を取り込む。 */
    public StoredFile storeBytes(long familyStudentId, String testNo, String originalName,
                                 String mimeType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw TestInfoApiException.invalid("空のファイルは保存できません。");
        }
        String name = safeOriginalName(originalName);
        String extension = extensionOf(name);
        String suffix = extension.isEmpty() ? "" : "." + extension;
        String storedName = sanitize(testNo) + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + suffix;
        String relativeDirectory = CONTENT_DIR + "/" + familyStudentId + "/" + LocalDate.now().format(YEAR_MONTH)
                + "/" + sanitize(testNo);
        Path directory = storageRoot.resolve(relativeDirectory).normalize();
        Path target = directory.resolve(storedName).normalize();
        requireUnder(directory, storageRoot);
        requireUnder(target, directory);
        try {
            Files.createDirectories(directory);
            Files.write(target, bytes);
        } catch (IOException ex) {
            throw new IllegalStateException("試験用紙ファイルの保存に失敗しました。", ex);
        }
        String[] thumbnails = generateThumbnails(target, extension);
        return new StoredFile(relativeDirectory.replace('\\', '/'), storedName, name, extension,
                mimeType == null || mimeType.isBlank() ? contentType(target, extension) : mimeType,
                (long) bytes.length, sha256(bytes), target,
                thumbnails[0], thumbnails[1], thumbnails[2]);
    }

    public Path resolve(long familyStudentId, String databasePath, String storedFileName) {
        String safeName = safeStoredName(storedFileName);
        String normalized = databasePath == null ? "" : databasePath.replace('\\', '/').trim();
        if (!normalized.startsWith(CONTENT_DIR + "/")) throw TestInfoApiException.notFound();
        if (!normalized.startsWith(CONTENT_DIR + "/" + familyStudentId + "/")) throw TestInfoApiException.notFound();
        Path directory = storageRoot.resolve(normalized).normalize();
        requireUnder(directory, storageRoot);
        Path file = directory.resolve(safeName).normalize();
        requireUnder(file, directory);
        return file;
    }

    public void deleteIfExists(Path path) {
        if (path == null) return;
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(storageRoot)) return;
        try {
            Files.deleteIfExists(normalized);
        } catch (IOException ex) {
            throw new IllegalStateException("試験用紙ファイルの削除に失敗しました。", ex);
        }
    }

    public String contentType(Path path, String extension) {
        try {
            String detected = Files.probeContentType(path);
            if (detected != null) return detected;
        } catch (IOException ignored) {
            // 拡張子による安全なフォールバックを使用する。
        }
        return switch (extension == null ? "" : extension.toLowerCase(Locale.ROOT)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    public boolean isImage(String extension) {
        return extension != null && IMAGE_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }

    /**
     * 画像を 90 度単位で回転し PNG バイト列で返す（2.0 の回転保存と同じ挙動）。
     * 画像以外・読み込み不能な場合は元のバイト列をそのまま返す。
     */
    public RotatedImage rotate(byte[] bytes, String originalName, int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        if (normalized == 0) return new RotatedImage(originalName, bytes);
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null) return new RotatedImage(originalName, bytes);
            boolean quarter = normalized == 90 || normalized == 270;
            int width = quarter ? source.getHeight() : source.getWidth();
            int height = quarter ? source.getWidth() : source.getHeight();
            BufferedImage rotated = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rotated.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.rotate(Math.toRadians(normalized), width / 2.0, height / 2.0);
                g.drawImage(source, (width - source.getWidth()) / 2, (height - source.getHeight()) / 2, null);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(rotated, "png", out);
            String stem = stripExtension(originalName);
            return new RotatedImage(stem + ".png", out.toByteArray());
        } catch (IOException | RuntimeException ex) {
            return new RotatedImage(originalName, bytes);
        }
    }

    private String[] generateThumbnails(Path saved, String extension) {
        if (!isImage(extension)) return new String[] { null, null, null };
        try {
            BufferedImage image = ImageIO.read(saved.toFile());
            if (image == null) return new String[] { null, null, null };
            return new String[] { encodeThumb(image, 500), encodeThumb(image, 200), encodeThumb(image, 50) };
        } catch (IOException ex) {
            return new String[] { null, null, null };
        }
    }

    private String encodeThumb(BufferedImage source, int maxEdge) {
        int width = source.getWidth();
        int height = source.getHeight();
        int targetWidth = width;
        int targetHeight = height;
        if (width > maxEdge || height > maxEdge) {
            double scale = Math.min((double) maxEdge / width, (double) maxEdge / height);
            targetWidth = Math.max(1, (int) Math.round(width * scale));
            targetHeight = Math.max(1, (int) Math.round(height * scale));
        }
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(scaled, "png", out);
        } catch (IOException ex) {
            return null;
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }
    }

    private String safeOriginalName(String input) {
        String normalized = input == null ? "file" : input.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) name = "file";
        return name.length() <= 200 ? name : name.substring(name.length() - 200);
    }

    private String safeStoredName(String input) {
        if (input == null || input.isBlank() || input.contains("/") || input.contains("\\")
                || input.equals(".") || input.equals("..")) {
            throw TestInfoApiException.notFound();
        }
        return input;
    }

    private String stripExtension(String name) {
        String safe = safeOriginalName(name);
        int dot = safe.lastIndexOf('.');
        return dot > 0 ? safe.substring(0, dot) : safe;
    }

    private String sanitize(String value) {
        String safe = value == null ? "" : value.replaceAll("[^A-Za-z0-9_-]", "_");
        if (safe.isBlank()) safe = "test";
        return safe.length() <= 60 ? safe : safe.substring(0, 60);
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return "";
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return extension.length() <= 10 ? extension : extension.substring(0, 10);
    }

    private void requireUnder(Path target, Path parent) {
        if (!target.toAbsolutePath().normalize().startsWith(parent.toAbsolutePath().normalize())) {
            throw TestInfoApiException.invalid("保存先パスが不正です。");
        }
    }

    public record StoredFile(String relativePath, String storedName, String originalName, String extension,
                             String mimeType, long fileSize, String sha256, Path physicalPath,
                             String thumbnail500, String thumbnail200, String thumbnail50) {}

    public record RotatedImage(String originalName, byte[] bytes) {}
}
