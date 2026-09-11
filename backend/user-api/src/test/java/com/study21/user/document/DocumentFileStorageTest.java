package com.study21.user.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DocumentFileStorageTest {
    @TempDir Path root;

    @Test
    void storesUnderFamilyAndResolvesOnlyThatFamily() throws Exception {
        DocumentFileStorage storage = new DocumentFileStorage(root.toString(), "");
        var upload = new MockMultipartFile("files", "lesson.pdf", "application/pdf", "content".getBytes());

        var stored = storage.store(42L, "D0001", 1, upload);

        assertTrue(stored.relativePath().startsWith("families/42/"));
        assertTrue(Files.isRegularFile(storage.resolve(42L, stored.relativePath(), stored.storedName())));
        assertThrows(DocumentApiException.class,
                () -> storage.resolve(99L, stored.relativePath(), stored.storedName()));
    }

    @Test
    void legacyFileIsReadableButNeverDeleted() throws Exception {
        Path legacy = root.resolve("legacy");
        Path file = legacy.resolve("202401/D1/D1_1.pdf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "legacy");
        DocumentFileStorage storage = new DocumentFileStorage(root.resolve("new").toString(), legacy.toString());

        Path resolved = storage.resolve(42L, "/doc/202401/D1", "D1_1.pdf");
        storage.deleteIfExists(resolved);

        assertTrue(Files.exists(file));
    }
}
