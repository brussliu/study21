package com.study21.user.tempfile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 臨時ファイルのバイナリ保存規則・パストラバーサル防止・縮小画像生成を隠蔽する深いモジュール。
 * 画像は縮小画像（500/200/50 px）を PNG base64 で生成し、一覧表示に使う。
 */
@Component
public class TempFileStorage {
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final List<String> IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "gif", "bmp", "webp");

    private final Path storageRoot;

    public TempFileStorage(@Value("${study21.temp-files.storage-root:${user.dir}/data/temp-files}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public StoredFile store(long familyStudentId, MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw TempFileApiException.invalid("空のファイルは保存できません。");
        }
        String originalName = safeOriginalName(upload.getOriginalFilename());
        String extension = extensionOf(originalName);
        String suffix = extension.isEmpty() ? "" : "." + extension;
        String storedName = UUID.randomUUID().toString().replace("-", "") + suffix;
        String relativeDirectory = "families/" + familyStudentId + "/" + LocalDate.now().format(YEAR_MONTH);
        Path directory = storageRoot.resolve(relativeDirectory).normalize();
        Path target = directory.resolve(storedName).normalize();
        requireUnder(directory, storageRoot);
        requireUnder(target, directory);
        try {
            Files.createDirectories(directory);
            try (InputStream input = upload.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("臨時ファイルの保存に失敗しました。", ex);
        }
        String[] thumbnails = generateThumbnails(target, extension);
        return new StoredFile(relativeDirectory.replace('\\', '/'), storedName, originalName, extension,
                upload.getContentType(), upload.getSize(), target,
                thumbnails[0], thumbnails[1], thumbnails[2]);
    }

    public Path resolve(long familyStudentId, String databasePath, String storedFileName) {
        String safeName = safeStoredName(storedFileName);
        String normalized = databasePath == null ? "" : databasePath.replace('\\', '/').trim();
        if (!normalized.startsWith("families/")) throw TempFileApiException.notFound();
        String expectedPrefix = "families/" + familyStudentId + "/";
        if (!normalized.startsWith(expectedPrefix)) throw TempFileApiException.notFound();
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
            throw new IllegalStateException("臨時ファイルの削除に失敗しました。", ex);
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

    private String safeOriginalName(String input) {
        String normalized = input == null ? "file" : input.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) name = "file";
        return name.length() <= 200 ? name : name.substring(name.length() - 200);
    }

    private String safeStoredName(String input) {
        if (input == null || input.isBlank() || input.contains("/") || input.contains("\\")
                || input.equals(".") || input.equals("..")) {
            throw TempFileApiException.notFound();
        }
        return input;
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return "";
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return extension.length() <= 10 ? extension : extension.substring(0, 10);
    }

    private void requireUnder(Path target, Path parent) {
        if (!target.toAbsolutePath().normalize().startsWith(parent.toAbsolutePath().normalize())) {
            throw TempFileApiException.invalid("保存先パスが不正です。");
        }
    }

    public record StoredFile(String relativePath, String storedName, String originalName, String extension,
                             String mimeType, long fileSize, Path physicalPath,
                             String thumbnail500, String thumbnail200, String thumbnail50) {}
}
