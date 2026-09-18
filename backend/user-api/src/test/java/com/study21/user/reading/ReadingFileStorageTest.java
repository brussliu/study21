package com.study21.user.reading;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 読書管理のファイル保存規則（`ReadingFileStorage`）の検証。
 *
 * 契約（tmp/reading-redesign/API.md §2）:
 * ・2.1 の保存先は `books/&lt;書籍番号&gt;/&lt;書籍番号&gt;-&lt;uuid8&gt;.pdf`（DB には相対パスだけ）
 * ・PDF は拡張子 .pdf と Content-Type application/pdf の両方が必要
 * ・表紙は画像（png / jpeg / webp）だけ・5MB まで
 * ・2.0 から引き継いだ `file/ENGLISH_READING` ツリーは**読取専用**
 */
class ReadingFileStorageTest {

    @TempDir
    Path root;

    private ReadingFileStorage storage;

    private static ReadingFileStorage newStorage(Path storageRoot, String legacyRoot) {
        return new ReadingFileStorage(storageRoot.toString(), legacyRoot);
    }

    @Test
    void storesPdfUnderBookNoAndResolvesIt() throws Exception {
        storage = newStorage(root.resolve("new"), "");

        var stored = storage.storePdf("ER-20260402-124745",
                new MockMultipartFile("file", "Harry Potter.pdf", "application/pdf", "%PDF-1.4".getBytes()));

        assertThat(stored.relativePath()).isEqualTo("books/ER-20260402-124745/");
        assertThat(stored.storedName()).startsWith("ER-20260402-124745-").endsWith(".pdf");
        assertThat(stored.originalName()).isEqualTo("Harry Potter.pdf");
        assertThat(stored.contentType()).isEqualTo("application/pdf");
        assertThat(Files.isRegularFile(stored.physicalPath())).isTrue();

        // DB に入れる相対パスからもう一度解決できる
        Path resolved = storage.resolve(stored.relativePath(), stored.storedName());
        assertThat(resolved).isEqualTo(stored.physicalPath());
        assertThat(storage.exists(stored.relativePath(), stored.storedName())).isTrue();
    }

    /** 拡張子が .pdf でも Content-Type が違うものは弾く（逆も同じ）。 */
    @Test
    void rejectsFilesThatAreNotPdf() {
        storage = newStorage(root.resolve("new"), "");

        assertThatThrownBy(() -> storage.storePdf("ER-1",
                new MockMultipartFile("file", "scan.png", "image/png", "x".getBytes())))
                .isInstanceOf(ReadingApiException.class)
                .hasMessageContaining("PDF");
        assertThatThrownBy(() -> storage.storePdf("ER-1",
                new MockMultipartFile("file", "scan.pdf", "image/png", "x".getBytes())))
                .isInstanceOf(ReadingApiException.class);
        assertThatThrownBy(() -> storage.storePdf("ER-1",
                new MockMultipartFile("file", "scan.pdf", "application/octet-stream", "x".getBytes())))
                .isInstanceOf(ReadingApiException.class);
        assertThatThrownBy(() -> storage.storePdf("ER-1",
                new MockMultipartFile("file", "scan", "application/pdf", "x".getBytes())))
                .isInstanceOf(ReadingApiException.class);
        // 空のファイルも保存しない
        assertThatThrownBy(() -> storage.storePdf("ER-1",
                new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(ReadingApiException.class);
    }

    /** `application/pdf; charset=UTF-8` のようなパラメータつきでも通す。 */
    @Test
    void acceptsPdfWithContentTypeParameters() throws Exception {
        storage = newStorage(root.resolve("new"), "");

        var stored = storage.storePdf("ER-1",
                new MockMultipartFile("file", "book.PDF", "application/pdf; charset=UTF-8", "%PDF".getBytes()));

        // 拡張子は小文字に落として保存する
        assertThat(stored.extension()).isEqualTo("pdf");
        assertThat(stored.storedName()).endsWith(".pdf");
    }

    @Test
    void rejectsPdfOverTwoHundredMegabytes() {
        storage = newStorage(root.resolve("new"), "");

        // 200MB の配列は作らず、サイズだけ大きいことにする（上限の判定を見る）
        MockMultipartFile tooLarge = new MockMultipartFile("file", "big.pdf", "application/pdf",
                "%PDF".getBytes()) {
            @Override
            public long getSize() {
                return ReadingFileStorage.MAX_PDF_BYTES + 1;
            }
        };

        assertThatThrownBy(() -> storage.storePdf("ER-1", tooLarge))
                .isInstanceOf(ReadingApiException.class)
                .hasMessageContaining("200MB");
    }

    /** 表紙は画像のみ（png / jpeg / webp）。PDF や 5MB 超は弾く。 */
    @Test
    void storesOnlyImagesAsCover() throws Exception {
        storage = newStorage(root.resolve("new"), "");

        var cover = storage.storeCover("ER-1",
                new MockMultipartFile("file", "cover.webp", "image/webp", "webp".getBytes()));
        assertThat(cover.storedName()).endsWith(".webp");
        assertThat(cover.contentType()).isEqualTo("image/webp");

        // 拡張子が画像でない
        assertThatThrownBy(() -> storage.storeCover("ER-1",
                new MockMultipartFile("file", "cover.pdf", "application/pdf", "pdf".getBytes())))
                .isInstanceOf(ReadingApiException.class)
                .hasMessageContaining("png");
        // 拡張子は画像だが Content-Type が画像でない
        assertThatThrownBy(() -> storage.storeCover("ER-1",
                new MockMultipartFile("file", "cover.png", "application/pdf", "pdf".getBytes())))
                .isInstanceOf(ReadingApiException.class)
                .hasMessageContaining("画像");
        MockMultipartFile tooLargeCover = new MockMultipartFile("file", "cover.png", "image/png",
                "png".getBytes()) {
            @Override
            public long getSize() {
                return ReadingFileStorage.MAX_COVER_BYTES + 1;
            }
        };
        assertThatThrownBy(() -> storage.storeCover("ER-1", tooLargeCover))
                .isInstanceOf(ReadingApiException.class)
                .hasMessageContaining("5MB");
    }

    /** パスがディレクトリの外へ出る指定（`../`）は解決しない。 */
    @Test
    void refusesPathTraversal() {
        storage = newStorage(root.resolve("new"), "");

        assertThatThrownBy(() -> storage.resolve("books/../../etc/", "passwd"))
                .isInstanceOf(ReadingApiException.class);
        assertThatThrownBy(() -> storage.resolve("books/ER-1/", "../secret.pdf"))
                .isInstanceOf(ReadingApiException.class);
    }

    /** legacy ルート未設定（2.0 のツリーが無い配備）では解決できず、実体なしとして扱う。 */
    @Test
    void legacyRowIsUnavailableWhenLegacyRootIsNotConfigured() {
        storage = newStorage(root.resolve("new"), "");

        assertThat(storage.exists("file/ENGLISH_READING/202604/", "20260402-124745-x.pdf")).isFalse();
        assertThat(storage.resolveOrNull("file/ENGLISH_READING/202604/", "x.pdf")).isNull();
        assertThatThrownBy(() -> storage.resolve("file/ENGLISH_READING/202604/", "x.pdf"))
                .isInstanceOf(ReadingApiException.class)
                .satisfies(ex -> assertThat(((ReadingApiException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    /** 2.0 のツリーは読取専用: 解決はできるが、2.1 からは消さない。 */
    @Test
    void legacyFileIsReadableButNeverDeleted() throws Exception {
        Path legacy = root.resolve("legacy");
        Path file = legacy.resolve("202604/20260402-124745-x.pdf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "legacy pdf");
        storage = newStorage(root.resolve("new"), legacy.toString());

        Path resolved = storage.resolve("file/ENGLISH_READING/202604/", "20260402-124745-x.pdf");
        assertThat(resolved).isEqualTo(file);
        assertThat(storage.exists("file/ENGLISH_READING/202604/", "20260402-124745-x.pdf")).isTrue();

        storage.deleteStored("file/ENGLISH_READING/202604/", "20260402-124745-x.pdf");

        assertThat(Files.exists(file)).as("2.0 の実体は消さない").isTrue();
    }

    @Test
    void deletesOnlyFilesUnderTheStorageRoot() throws Exception {
        storage = newStorage(root.resolve("new"), "");
        var stored = storage.storePdf("ER-1",
                new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF".getBytes()));

        storage.deleteStored(stored.relativePath(), stored.storedName());

        assertThat(Files.exists(stored.physicalPath())).isFalse();
        // 実体が無くなれば exists も false（画面は「PDF 未登録」に切り替わる）
        assertThat(storage.exists(stored.relativePath(), stored.storedName())).isFalse();
    }

    /** 保存ファイル名は DB の値をそのまま使わず、ディレクトリ区切りを含むものは拒否する。 */
    @Test
    void refusesStoredNameWithDirectorySeparator() {
        storage = newStorage(root.resolve("new"), "");

        assertThatThrownBy(() -> storage.resolve("books/ER-1/", "../x.pdf"))
                .isInstanceOf(ReadingApiException.class);
        assertThatThrownBy(() -> storage.resolve("books/ER-1/", ".."))
                .isInstanceOf(ReadingApiException.class);
    }

    /** Content-Type は DB の MIME_TYPE を優先し、無ければ拡張子から決める。 */
    @Test
    void resolvesContentTypeFromDatabaseOrExtension() {
        storage = newStorage(root.resolve("new"), "");

        assertThat(storage.contentType("application/pdf", "x.pdf")).isEqualTo("application/pdf");
        assertThat(storage.contentType(null, "x.pdf")).isEqualTo("application/pdf");
        assertThat(storage.contentType("", "cover.PNG")).isEqualTo("image/png");
        assertThat(storage.contentType("application/octet-stream", "cover.jpg")).isEqualTo("image/jpeg");
        assertThat(storage.contentType(null, "cover.webp")).isEqualTo("image/webp");
        assertThat(storage.contentType(null, "unknown")).isEqualTo("application/octet-stream");
    }
}
