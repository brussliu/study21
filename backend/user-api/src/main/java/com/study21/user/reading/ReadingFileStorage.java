package com.study21.user.reading;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 読書管理のバイナリ（本文 PDF・表紙画像）の保存規則・パストラバーサル防止・
 * 実体の有無の判定を隠蔽する深いモジュール（資料管理の `DocumentFileStorage` と同じ作り）。
 *
 * <p>保存先は `books/&lt;書籍番号&gt;/&lt;書籍番号&gt;-&lt;uuid8&gt;.pdf`（DB には相対パスだけを入れる。
 * 絶対パスを入れると配備先で動かなくなるため）。</p>
 *
 * <p>2.0 から引き継いだ行（`保存パス='file/ENGLISH_READING/202604/'`）は
 * {@code study21.reading.legacy-storage-root} から**読取専用**で解決する。実体が無ければ
 * {@link #exists} が false になり、画面は「PDF 未登録」を出せる。legacy ルートは
 * 2.1 からは絶対に消さない（{@link #deleteIfExists} が素通りする）。</p>
 */
@Component
public class ReadingFileStorage {

    /** PDF の上限（契約: 200MB）。 */
    public static final long MAX_PDF_BYTES = 200L * 1024 * 1024;
    /** 表紙の上限（契約: 5MB）。 */
    public static final long MAX_COVER_BYTES = 5L * 1024 * 1024;

    private static final String PDF_MIME = "application/pdf";
    private static final List<String> COVER_EXTENSIONS = List.of("png", "jpg", "jpeg", "webp");
    private static final List<String> COVER_MIME_TYPES =
            List.of("image/png", "image/jpeg", "image/jpg", "image/webp");
    /** 2.0 のツリー（`file/ENGLISH_READING/<yyyyMM>/`）を読取専用で解決するときの前置き。 */
    private static final String LEGACY_REGEX = "^/?(?:file/)?ENGLISH_READING/?";
    /** 2.1 が自分で置いたファイルの前置き。 */
    private static final String OWNED_PREFIX = "books/";

    private final Path storageRoot;
    private final Path legacyStorageRoot;

    public ReadingFileStorage(
            @Value("${study21.reading.storage-root:${user.dir}/data/reading}") String storageRoot,
            @Value("${study21.reading.legacy-storage-root:}") String legacyStorageRoot) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.legacyStorageRoot = legacyStorageRoot == null || legacyStorageRoot.isBlank()
                ? null : Path.of(legacyStorageRoot).toAbsolutePath().normalize();
    }

    /** 本文 PDF を保存する。拡張子 `.pdf` と Content-Type `application/pdf` の両方が必要。 */
    public StoredFile storePdf(String bookNo, MultipartFile upload) {
        requirePresent(upload);
        String originalName = safeOriginalName(upload.getOriginalFilename());
        requireExtension(originalName, List.of("pdf"), "PDF");
        if (!PDF_MIME.equals(normalizedContentType(upload.getContentType()))) {
            throw ReadingApiException.invalid("PDF（application/pdf）のファイルを選んでください。");
        }
        requireSize(upload.getSize(), MAX_PDF_BYTES, "PDF");
        return store(bookNo, originalName, "pdf", PDF_MIME, upload);
    }

    /** 表紙画像を保存する。png / jpeg / webp のみ、5MB まで。 */
    public StoredFile storeCover(String bookNo, MultipartFile upload) {
        requirePresent(upload);
        String originalName = safeOriginalName(upload.getOriginalFilename());
        requireExtension(originalName, COVER_EXTENSIONS, "表紙");
        String contentType = normalizedContentType(upload.getContentType());
        if (!COVER_MIME_TYPES.contains(contentType)) {
            throw ReadingApiException.invalid("表紙は画像（png / jpeg / webp）を選んでください。");
        }
        requireSize(upload.getSize(), MAX_COVER_BYTES, "表紙");
        return store(bookNo, originalName, extensionOf(originalName), contentType, upload);
    }

    /**
     * DB の `保存パス` + `保存ファイル名` から実体の場所を求める。
     *
     * @throws ReadingApiException 解決できないとき（legacy ルート未設定・パスが不正）は 404
     */
    public Path resolve(String databasePath, String storedFileName) {
        String safeName = safeStoredName(storedFileName);
        String normalized = normalizeDirectory(databasePath);
        if (normalized.startsWith(OWNED_PREFIX)) {
            return underRoot(storageRoot, normalized, safeName);
        }
        if (legacyStorageRoot == null) {
            // 2.0 のツリーが配備されていない（実体は無い）
            throw ReadingApiException.notFound("PDF が見つかりません。");
        }
        String relative = normalized.replaceFirst(LEGACY_REGEX, "");
        return underRoot(legacyStorageRoot, relative, safeName);
    }

    /** 解決できない行（legacy ルート未設定・パス不正）は例外にせず null を返す。 */
    public Path resolveOrNull(String databasePath, String storedFileName) {
        try {
            return resolve(databasePath, storedFileName);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** 実体がストレージにあるか（画面の「PDF 未登録」の判定に使う）。 */
    public boolean exists(String databasePath, String storedFileName) {
        Path path = resolveOrNull(databasePath, storedFileName);
        return path != null && Files.isRegularFile(path);
    }

    /**
     * DB の行が指す実体を消す。legacy（2.0 のツリー）は読取専用なので消さない
     * （ストレージルートの外なので {@link #deleteIfExists} が素通りする）。
     */
    public void deleteStored(String databasePath, String storedFileName) {
        deleteIfExists(resolveOrNull(databasePath, storedFileName));
    }

    public void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        Path normalized = path.toAbsolutePath().normalize();
        // 2.0 から引き継いだ実体は読取専用。2.1 からは消さない。
        if (!normalized.startsWith(storageRoot)) {
            return;
        }
        try {
            Files.deleteIfExists(normalized);
        } catch (IOException ex) {
            throw new IllegalStateException("書籍ファイルの削除に失敗しました。", ex);
        }
    }

    /** 配信するときの Content-Type。DB の MIME_TYPE を優先し、無ければ拡張子から決める。 */
    public String contentType(String databaseMimeType, String storedFileName) {
        String mime = normalizedContentType(databaseMimeType);
        if (!mime.isEmpty() && !"application/octet-stream".equals(mime)) {
            return mime;
        }
        return switch (extensionOf(storedFileName)) {
            case "pdf" -> PDF_MIME;
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    // -------------------------------------------------------------------- 内部

    private StoredFile store(String bookNo, String originalName, String extension, String contentType,
                             MultipartFile upload) {
        String safeBookNo = safeBookNo(bookNo);
        String storedName = safeBookNo + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                + "." + extension;
        String relativeDirectory = OWNED_PREFIX + safeBookNo + "/";
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
            throw new IllegalStateException("書籍ファイルの保存に失敗しました。", ex);
        }
        return new StoredFile(relativeDirectory, storedName, originalName, extension, target, contentType,
                upload.getSize());
    }

    private Path underRoot(Path root, String relativeDirectory, String safeName) {
        String relative = relativeDirectory == null ? "" : relativeDirectory;
        Path directory = root.resolve(relative).normalize();
        requireUnder(directory, root);
        Path file = directory.resolve(safeName).normalize();
        requireUnder(file, directory);
        return file;
    }

    private static void requirePresent(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw ReadingApiException.invalid("空のファイルは保存できません。");
        }
    }

    private static void requireSize(long size, long max, String label) {
        if (size > max) {
            throw ReadingApiException.invalid(label + "の上限は " + (max / 1024 / 1024) + "MB です。");
        }
    }

    private static void requireExtension(String name, List<String> allowed, String label) {
        String extension = extensionOf(name);
        if (!allowed.contains(extension)) {
            throw ReadingApiException.invalid(label + "は " + String.join(" / ", allowed)
                    + " のいずれかのファイルを選んでください。");
        }
    }

    /** `application/pdf; charset=...` のようなパラメータを落として小文字にする。 */
    private static String normalizedContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        String value = semicolon < 0 ? contentType : contentType.substring(0, semicolon);
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /** 書籍番号はそのままディレクトリ名に使わず、安全な文字だけに落とす。 */
    private static String safeBookNo(String bookNo) {
        String value = bookNo == null ? "" : bookNo.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (value.isEmpty() || value.equals(".") || value.equals("..")) {
            throw ReadingApiException.invalid("書籍番号が不正です。");
        }
        return value.length() <= 60 ? value : value.substring(0, 60);
    }

    private static String normalizeDirectory(String databasePath) {
        return databasePath == null ? "" : databasePath.replace('\\', '/').trim();
    }

    private static String safeOriginalName(String input) {
        String normalized = input == null ? "file" : input.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) {
            name = "file";
        }
        return name.length() <= 200 ? name : name.substring(name.length() - 200);
    }

    private static String safeStoredName(String input) {
        if (input == null || input.isBlank() || input.contains("/") || input.contains("\\")
                || input.equals(".") || input.equals("..")) {
            throw ReadingApiException.notFound("PDF が見つかりません。");
        }
        return input;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return extension.length() <= 10 ? extension : extension.substring(0, 10);
    }

    private static void requireUnder(Path target, Path parent) {
        if (!target.toAbsolutePath().normalize().startsWith(parent.toAbsolutePath().normalize())) {
            throw ReadingApiException.invalid("保存先パスが不正です。");
        }
    }

    public record StoredFile(String relativePath, String storedName, String originalName, String extension,
                             Path physicalPath, String contentType, long fileSize) {
    }
}
