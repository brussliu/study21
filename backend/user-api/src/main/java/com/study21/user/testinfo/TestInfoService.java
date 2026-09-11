package com.study21.user.testinfo;

import com.study21.user.security.UserPrincipal;
import com.study21.user.tempfile.TempFileEntity;
import com.study21.user.tempfile.TempFileMapper;
import com.study21.user.tempfile.TempFileStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TestInfoService {
    /** 2.0 と同じ上限（1テスト 10 件）。 */
    private static final int MAX_FILES_PER_TEST = 10;
    /** アプリ全体の multipart 上限（application.yml）に合わせる。 */
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final DateTimeFormatter TEST_NO_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final TestInfoMapper mapper;
    private final TestFileStorage storage;
    private final TempFileMapper tempFileMapper;
    private final TempFileStorage tempFileStorage;

    public TestInfoService(TestInfoMapper mapper, TestFileStorage storage,
                           TempFileMapper tempFileMapper, TempFileStorage tempFileStorage) {
        this.mapper = mapper;
        this.storage = storage;
        this.tempFileMapper = tempFileMapper;
        this.tempFileStorage = tempFileStorage;
    }

    // ---------- 一覧・検索 ----------

    @Transactional(readOnly = true)
    public TestInfoModels.Workspace workspace(UserPrincipal user, String subject, String kind, String keyword) {
        long familyId = familyStudentId(user);
        List<TestInfoEntity> tests = mapper.searchTestInfos(familyId, trimToNull(subject), trimToNull(kind),
                trimToNull(keyword));
        Map<Long, List<TestFileEntity>> filesByTest = new HashMap<>();
        if (!tests.isEmpty()) {
            List<Long> ids = tests.stream().map(TestInfoEntity::getTestId).toList();
            for (TestFileEntity file : mapper.findTestFilesByTestIds(familyId, ids)) {
                filesByTest.computeIfAbsent(file.getTestId(), key -> new ArrayList<>()).add(file);
            }
        }
        List<TestInfoModels.Row> rows = tests.stream()
                .map(test -> toRow(test, filesByTest.getOrDefault(test.getTestId(), List.of())))
                .toList();
        return new TestInfoModels.Workspace(rows);
    }

    // ---------- 登録・更新・削除 ----------

    @Transactional
    public TestInfoModels.Saved create(UserPrincipal user, TestInfoModels.SaveRequest request,
                                       List<MultipartFile> uploads) {
        long familyId = familyStudentId(user);
        Score score = normalizeScore(request.score(), request.fullScore());
        TestInfoEntity entity = new TestInfoEntity();
        entity.setFamilyStudentId(familyId);
        entity.setTestNo(generateTestNo(familyId));
        entity.setTestName(request.testName().trim());
        entity.setSubject(defaultIfBlank(request.subject(), "英語"));
        entity.setKind(defaultIfBlank(request.kind(), "通常"));
        entity.setExamDate(request.examDate());
        entity.setScore(score.score());
        entity.setFullScore(score.fullScore());
        entity.setMemo(trimToNull(request.memo()));
        mapper.insertTestInfo(entity);

        List<TestFileStorage.StoredFile> stored = new ArrayList<>();
        try {
            insertUploads(user, familyId, entity, uploads, stored,
                    mapper.findMaxDisplayOrder(familyId, entity.getTestNo()) + 1);
        } catch (RuntimeException ex) {
            stored.forEach(file -> storage.deleteIfExists(file.physicalPath()));
            throw ex;
        }
        return new TestInfoModels.Saved(toRow(requireTest(familyId, entity.getTestNo()), null));
    }

    @Transactional
    public TestInfoModels.Saved update(UserPrincipal user, String testNo, TestInfoModels.UpdateRequest request) {
        long familyId = familyStudentId(user);
        TestInfoEntity existing = requireTest(familyId, testNo);
        if (request.version() == null) throw TestInfoApiException.invalid("バージョンが指定されていません。");
        Score score = normalizeScore(request.score(), request.fullScore());
        int updated = mapper.updateTestInfo(familyId, testNo, request.version(), request.testName().trim(),
                defaultIfBlank(request.subject(), existing.getSubject()), defaultIfBlank(request.kind(), existing.getKind()),
                request.examDate(), score.score(), score.fullScore(), trimToNull(request.memo()), operator(user));
        if (updated == 0) {
            throw TestInfoApiException.conflict("他の端末で更新されています。再読込してください。");
        }
        if (request.files() != null) {
            for (TestInfoModels.FileOrderRequest order : request.files()) {
                if (order == null) continue;
                TestFileEntity file = mapper.findTestFile(familyId, testNo, order.fileId());
                if (file == null) continue;
                mapper.updateTestFileMeta(familyId, testNo, order.fileId(),
                        order.displayOrder() == null ? file.getDisplayOrder() : order.displayOrder(),
                        trimToNull(order.comment()), operator(user));
            }
        }
        return new TestInfoModels.Saved(toRow(requireTest(familyId, testNo), null));
    }

    @Transactional
    public TestInfoModels.Deleted delete(UserPrincipal user, String testNo) {
        long familyId = familyStudentId(user);
        TestInfoEntity test = requireTest(familyId, testNo);
        List<TestFileEntity> files = mapper.findTestFiles(familyId, testNo);
        mapper.deleteTestFilesByTestId(test.getTestId());
        mapper.deleteTestInfo(familyId, testNo);
        for (TestFileEntity file : files) {
            storage.deleteIfExists(storage.resolve(familyId, file.getPath(), file.getStoredFileName()));
        }
        return new TestInfoModels.Deleted(true);
    }

    // ---------- ファイル操作 ----------

    @Transactional
    public TestInfoModels.Saved addFiles(UserPrincipal user, String testNo, List<MultipartFile> uploads) {
        long familyId = familyStudentId(user);
        TestInfoEntity test = requireTest(familyId, testNo);
        // 件数制限の検査中に同時追加されないよう、テスト行をロックする。
        mapper.lockTestInfo(familyId, testNo);
        List<MultipartFile> valid = uploads == null ? List.of()
                : uploads.stream().filter(file -> file != null && !file.isEmpty()).toList();
        requireCapacity(familyId, testNo, valid.size());
        List<TestFileStorage.StoredFile> stored = new ArrayList<>();
        try {
            insertUploads(user, familyId, test, valid, stored, mapper.findMaxDisplayOrder(familyId, testNo) + 1);
        } catch (RuntimeException ex) {
            stored.forEach(file -> storage.deleteIfExists(file.physicalPath()));
            throw ex;
        }
        return new TestInfoModels.Saved(toRow(requireTest(familyId, testNo), null));
    }

    /** 「臨時ファイルから選択」: 実体をコピーして試験用紙ファイルとして取り込む。 */
    @Transactional
    public TestInfoModels.Saved addFromTempFile(UserPrincipal user, String testNo, long tempFileId) {
        long familyId = familyStudentId(user);
        TestInfoEntity test = requireTest(familyId, testNo);
        TempFileEntity source = tempFileMapper.findById(familyId, tempFileId);
        if (source == null) throw TestInfoApiException.notFound();
        byte[] bytes = readFile(tempFileStorage.resolve(familyId, source.getPath(), source.getStoredFileName()));
        return appendBytes(user, familyId, test, source.getOriginalFileName(), source.getMimeType(), bytes);
    }

    /** 「テスト情報から選択」: 他のテストの試験用紙ファイルを複製して取り込む。 */
    @Transactional
    public TestInfoModels.Saved addFromTestFile(UserPrincipal user, String testNo, String sourceTestNo, long sourceFileId) {
        long familyId = familyStudentId(user);
        TestInfoEntity test = requireTest(familyId, testNo);
        TestFileEntity source = mapper.findTestFile(familyId, sourceTestNo, sourceFileId);
        if (source == null) throw TestInfoApiException.notFound();
        byte[] bytes = readFile(storage.resolve(familyId, source.getPath(), source.getStoredFileName()));
        return appendBytes(user, familyId, test, source.getOriginalFileName(), source.getMimeType(), bytes);
    }

    /** 画像の回転・手書き注釈の保存: 実体を差し替え、縮小画像とハッシュを再生成する。 */
    @Transactional
    public TestInfoModels.Saved replaceFileImage(UserPrincipal user, String testNo, long fileId,
                                                 MultipartFile image, Integer rotateDegrees) {
        long familyId = familyStudentId(user);
        TestInfoEntity test = requireTest(familyId, testNo);
        TestFileEntity existing = mapper.findTestFile(familyId, testNo, fileId);
        if (existing == null) throw TestInfoApiException.notFound();
        requireImage(image);
        String originalName = existing.getOriginalFileName();
        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (Exception ex) {
            throw TestInfoApiException.invalid("画像を読み込めませんでした。");
        }
        TestFileStorage.RotatedImage rotated = storage.rotate(bytes, originalName,
                rotateDegrees == null ? 0 : rotateDegrees);
        TestFileStorage.StoredFile saved = storage.storeBytes(familyId, testNo, rotated.originalName(),
                existing.getMimeType(), rotated.bytes());
        try {
            mapper.updateTestFileImage(familyId, testNo, fileId, saved.storedName(), saved.extension(),
                    saved.mimeType(), saved.fileSize(), saved.sha256(), saved.relativePath(),
                    saved.thumbnail500(), saved.thumbnail200(), saved.thumbnail50(), operator(user));
        } catch (RuntimeException ex) {
            storage.deleteIfExists(saved.physicalPath());
            throw ex;
        }
        storage.deleteIfExists(storage.resolve(familyId, existing.getPath(), existing.getStoredFileName()));
        return new TestInfoModels.Saved(toRow(requireTest(familyId, testNo), null));
    }

    @Transactional
    public TestInfoModels.Saved deleteFile(UserPrincipal user, String testNo, long fileId) {
        long familyId = familyStudentId(user);
        requireTest(familyId, testNo);
        TestFileEntity file = mapper.findTestFile(familyId, testNo, fileId);
        if (file == null) throw TestInfoApiException.notFound();
        mapper.deleteTestFile(familyId, testNo, fileId);
        storage.deleteIfExists(storage.resolve(familyId, file.getPath(), file.getStoredFileName()));
        return new TestInfoModels.Saved(toRow(requireTest(familyId, testNo), null));
    }

    @Transactional(readOnly = true)
    public TestInfoModels.Download download(UserPrincipal user, String testNo, long fileId) {
        long familyId = familyStudentId(user);
        requireTest(familyId, testNo);
        TestFileEntity file = mapper.findTestFile(familyId, testNo, fileId);
        if (file == null) throw TestInfoApiException.notFound();
        Path path = storage.resolve(familyId, file.getPath(), file.getStoredFileName());
        if (!Files.isRegularFile(path)) throw TestInfoApiException.notFound();
        String name = file.getOriginalFileName() == null ? file.getStoredFileName() : file.getOriginalFileName();
        return new TestInfoModels.Download(path, storage.contentType(path, file.getExtension()), name);
    }

    // ---------- 内部処理 ----------

    private TestInfoModels.Saved appendBytes(UserPrincipal user, long familyId, TestInfoEntity test,
                                             String originalName, String mimeType, byte[] bytes) {
        mapper.lockTestInfo(familyId, test.getTestNo());
        requireCapacity(familyId, test.getTestNo(), 1);
        int displayOrder = mapper.findMaxDisplayOrder(familyId, test.getTestNo()) + 1;
        TestFileStorage.StoredFile saved = storage.storeBytes(familyId, test.getTestNo(), originalName, mimeType, bytes);
        try {
            TestFileEntity entity = fromStored(test.getTestId(), saved, displayOrder, null);
            mapper.insertTestFile(entity, operator(user));
        } catch (RuntimeException ex) {
            storage.deleteIfExists(saved.physicalPath());
            throw ex;
        }
        return new TestInfoModels.Saved(toRow(requireTest(familyId, test.getTestNo()), null));
    }

    private void insertUploads(UserPrincipal user, long familyId, TestInfoEntity test, List<MultipartFile> uploads,
                               List<TestFileStorage.StoredFile> stored, int startDisplayOrder) {
        if (uploads == null || uploads.isEmpty()) return;
        int displayOrder = startDisplayOrder;
        for (MultipartFile upload : uploads) {
            if (upload == null || upload.isEmpty()) continue;
            if (upload.getSize() > MAX_FILE_BYTES) {
                throw TestInfoApiException.invalid("1ファイルは20MB以下にしてください。");
            }
            TestFileStorage.StoredFile saved = storage.store(familyId, test.getTestNo(), upload);
            stored.add(saved);
            mapper.insertTestFile(fromStored(test.getTestId(), saved, displayOrder++, null), operator(user));
        }
    }

    private void requireCapacity(long familyId, String testNo, int adding) {
        if (adding <= 0) return;
        int current = mapper.countTestFiles(familyId, testNo);
        if (current + adding > MAX_FILES_PER_TEST) {
            throw TestInfoApiException.invalid("試験用紙ファイルは" + MAX_FILES_PER_TEST + "件までです。");
        }
    }

    private TestFileEntity fromStored(long testId, TestFileStorage.StoredFile saved, int displayOrder, String comment) {
        TestFileEntity entity = new TestFileEntity();
        entity.setTestId(testId);
        entity.setDisplayOrder(displayOrder);
        entity.setOriginalFileName(saved.originalName());
        entity.setStoredFileName(saved.storedName());
        entity.setExtension(saved.extension());
        entity.setMimeType(saved.mimeType());
        entity.setFileSize(saved.fileSize());
        entity.setSha256(saved.sha256());
        entity.setPath(saved.relativePath());
        entity.setThumbnail500(saved.thumbnail500());
        entity.setThumbnail200(saved.thumbnail200());
        entity.setThumbnail50(saved.thumbnail50());
        entity.setComment(comment);
        return entity;
    }

    private TestInfoModels.Row toRow(TestInfoEntity test, List<TestFileEntity> files) {
        List<TestFileEntity> rows = files == null
                ? mapper.findTestFiles(test.getFamilyStudentId(), test.getTestNo()) : files;
        List<TestInfoModels.FileInfo> infos = rows.stream().map(file -> toFileInfo(test.getTestNo(), file)).toList();
        return new TestInfoModels.Row(test.getTestId(), test.getTestNo(), test.getTestName(), test.getSubject(),
                test.getKind(), test.getExamDate(), test.getScore(), test.getFullScore(),
                accuracyText(test.getScore(), test.getFullScore()), test.getMemo(),
                test.getVersion() == null ? 1 : test.getVersion(), infos.size(), infos,
                test.getCreatedAt(), test.getUpdatedAt());
    }

    private TestInfoModels.FileInfo toFileInfo(String testNo, TestFileEntity file) {
        boolean image = storage.isImage(file.getExtension());
        String contentUrl = "/api/user/test-infos/" + testNo + "/files/" + file.getFileId() + "/content";
        String previewUrl = image ? contentUrl : null;
        return new TestInfoModels.FileInfo(file.getFileId(),
                file.getDisplayOrder() == null ? 0 : file.getDisplayOrder(), file.getOriginalFileName(),
                file.getExtension(), file.getMimeType(), file.getFileSize(), file.getComment(), image,
                contentUrl, previewUrl);
    }

    /** 2.0 と同じ表示規則: 満点が無ければ "--"、それ以外は 0〜100% に丸めた小数1桁。 */
    private String accuracyText(Integer score, Integer fullScore) {
        if (score == null || fullScore == null || fullScore <= 0) return "--";
        double rate = Math.min(100.0, Math.max(0.0, score * 100.0 / fullScore));
        return String.format(Locale.ROOT, "%.1f%%", rate);
    }

    /**
     * 得点・満点は「両方あり」か「両方なし」のみ許可する（DDL の CHECK と同じ規則）。
     */
    private Score normalizeScore(Integer score, Integer fullScore) {
        if (score == null && fullScore == null) return new Score(null, null);
        if (score == null || fullScore == null || fullScore <= 0) {
            throw TestInfoApiException.invalid("得点数と満点数は両方入力してください。");
        }
        if (score < 0) throw TestInfoApiException.invalid("得点数は0以上で入力してください。");
        if (score > fullScore) throw TestInfoApiException.invalid("得点数は満点数以下で入力してください。");
        return new Score(score, fullScore);
    }

    /** 2.0 と同じ採番: TST-yyyyMMdd-HHmmss（家族内で一意）。衝突時は -01〜-99 を付与する。 */
    private String generateTestNo(long familyId) {
        String base = "TST-" + LocalDateTime.now().format(TEST_NO_STAMP);
        if (mapper.countTestNo(familyId, base) == 0) return base;
        for (int i = 1; i <= 99; i++) {
            String candidate = base + "-" + String.format(Locale.ROOT, "%02d", i);
            if (mapper.countTestNo(familyId, candidate) == 0) return candidate;
        }
        return base + "-" + (System.currentTimeMillis() % 1000);
    }

    private TestInfoEntity requireTest(long familyId, String testNo) {
        TestInfoEntity test = mapper.findTestInfo(familyId, testNo);
        if (test == null) throw TestInfoApiException.notFound();
        return test;
    }

    private void requireImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw TestInfoApiException.invalid("画像ファイルを指定してください。");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".png") && !name.endsWith(".jpg") && !name.endsWith(".jpeg")
                && !name.endsWith(".gif") && !name.endsWith(".bmp") && !name.endsWith(".webp")) {
            throw TestInfoApiException.invalid("画像ファイルを指定してください。");
        }
        if (file.getSize() > MAX_FILE_BYTES) throw TestInfoApiException.invalid("1ファイルは20MB以下にしてください。");
    }

    private long familyStudentId(UserPrincipal user) {
        List<Long> ids = mapper.findFamilyStudentIds(user.accountId(), user.accountType().code());
        if (ids.size() != 1) {
            throw TestInfoApiException.invalid("学生と保護者の紐付けを一意に特定できません。");
        }
        return ids.getFirst();
    }

    /** 他機能のファイル実体（臨時ファイル・他テスト）を読み込む。 */
    private byte[] readFile(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw TestInfoApiException.notFound();
        }
    }

    private String operator(UserPrincipal user) { return user == null ? "system" : String.valueOf(user.accountId()); }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private record Score(Integer score, Integer fullScore) {}
}
