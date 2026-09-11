package com.study21.user.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 資料バイナリの保存規則とパストラバーサル防止を隠蔽する深いモジュール。
 * 新規ファイルは公開静的ディレクトリに置かず、必ず認証済みダウンロード経由で返す。
 */
@Component
public class DocumentFileStorage {
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final Path storageRoot;
    private final Path legacyStorageRoot;

    public DocumentFileStorage(
            @Value("${study21.documents.storage-root:${user.dir}/data/documents}") String storageRoot,
            @Value("${study21.documents.legacy-storage-root:}") String legacyStorageRoot) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.legacyStorageRoot = legacyStorageRoot == null || legacyStorageRoot.isBlank()
                ? null : Path.of(legacyStorageRoot).toAbsolutePath().normalize();
    }

    public StoredFile store(long familyStudentId, String documentNo, int branchNo, MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw DocumentApiException.invalid("空のファイルは保存できません。");
        }
        String originalName = safeOriginalName(upload.getOriginalFilename());
        String extension = extensionOf(originalName);
        String suffix = extension.isEmpty() ? "" : "." + extension;
        String storedName = branchNo + "-" + UUID.randomUUID().toString().replace("-", "") + suffix;
        String relativeDirectory = "families/" + familyStudentId + "/" + LocalDate.now().format(YEAR_MONTH)
                + "/" + documentNo;
        Path directory = storageRoot.resolve(relativeDirectory).normalize();
        Path target = directory.resolve(storedName).normalize();
        requireUnder(directory, storageRoot);
        requireUnder(target, directory);
        try {
            Files.createDirectories(directory);
            try (var input = upload.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("資料ファイルの保存に失敗しました。", ex);
        }
        return new StoredFile(relativeDirectory.replace('\\', '/'), storedName, originalName, extension, target,
                upload.getContentType());
    }

    public Path resolve(long familyStudentId, String databasePath, String storedFileName) {
        String safeName = safeStoredName(storedFileName);
        String normalized = databasePath == null ? "" : databasePath.replace('\\', '/').trim();
        if (normalized.startsWith("families/")) {
            String expectedPrefix = "families/" + familyStudentId + "/";
            if (!normalized.startsWith(expectedPrefix)) {
                throw DocumentApiException.notFound();
            }
            Path directory = storageRoot.resolve(normalized).normalize();
            requireUnder(directory, storageRoot);
            Path file = directory.resolve(safeName).normalize();
            requireUnder(file, directory);
            return file;
        }
        // 2.0 の /doc/... は設定された旧ルートから読取専用で解決し、段階移行を可能にする。
        if (legacyStorageRoot != null) {
            String legacyRelative = normalized.replaceFirst("^/?(?:file/)?doc/?", "");
            Path directory = legacyStorageRoot.resolve(legacyRelative).normalize();
            requireUnder(directory, legacyStorageRoot);
            Path file = directory.resolve(safeName).normalize();
            requireUnder(file, directory);
            return file;
        }
        throw DocumentApiException.notFound();
    }

    public void deleteIfExists(Path path) {
        if (path == null) return;
        Path normalized = path.toAbsolutePath().normalize();
        // 旧 2.0 領域は移行期間中も読取専用。2.1 から削除しない。
        if (!normalized.startsWith(storageRoot)) return;
        try {
            Files.deleteIfExists(normalized);
        } catch (IOException ex) {
            throw new IllegalStateException("資料ファイルの削除に失敗しました。", ex);
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

    private String safeOriginalName(String input) {
        String normalized = input == null ? "file" : input.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) name = "file";
        return name.length() <= 200 ? name : name.substring(name.length() - 200);
    }

    private String safeStoredName(String input) {
        if (input == null || input.isBlank() || input.contains("/") || input.contains("\\")
                || input.equals(".") || input.equals("..")) {
            throw DocumentApiException.notFound();
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
            throw DocumentApiException.invalid("保存先パスが不正です。");
        }
    }

    public record StoredFile(String relativePath, String storedName, String originalName, String extension,
                             Path physicalPath, String contentType) {}
}
