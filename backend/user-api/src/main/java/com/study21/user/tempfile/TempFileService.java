package com.study21.user.tempfile;

import com.study21.user.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class TempFileService {
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final int DEFAULT_LIMIT = 300;

    private final TempFileMapper mapper;
    private final TempFileStorage storage;

    public TempFileService(TempFileMapper mapper, TempFileStorage storage) {
        this.mapper = mapper;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<TempFileModels.Summary> search(UserPrincipal user, String keyword, String type, String period, int limit) {
        long familyId = familyStudentId(user);
        Boolean imageOnly = switch (type == null ? "" : type.trim()) {
            case "画像", "image" -> Boolean.TRUE;
            case "その他", "other" -> Boolean.FALSE;
            default -> null;
        };
        Timestamp[] window = periodWindow(period);
        int safeLimit = limit <= 0 || limit > 500 ? DEFAULT_LIMIT : limit;
        return mapper.search(familyId, trimToNull(keyword), imageOnly, window[0], window[1], safeLimit)
                .stream().map(this::toSummary).toList();
    }

    @Transactional
    public List<TempFileModels.Summary> upload(UserPrincipal user, List<MultipartFile> files, String comment) {
        long familyId = familyStudentId(user);
        List<TempFileModels.Summary> created = new ArrayList<>();
        List<TempFileStorage.StoredFile> stored = new ArrayList<>();
        try {
            if (files != null) {
                for (MultipartFile upload : files) {
                    if (upload == null || upload.isEmpty()) continue;
                    if (upload.getSize() > MAX_FILE_BYTES) {
                        throw TempFileApiException.invalid("1ファイルは20MB以下にしてください。");
                    }
                    TempFileStorage.StoredFile saved = storage.store(familyId, upload);
                    stored.add(saved);
                    TempFileEntity entity = fromStored(familyId, saved, trimToNull(comment));
                    mapper.insert(entity);
                    created.add(toSummary(entity));
                }
            }
        } catch (RuntimeException ex) {
            stored.forEach(file -> storage.deleteIfExists(file.physicalPath()));
            throw ex;
        }
        return created;
    }

    @Transactional
    public TempFileModels.Summary updateMeta(UserPrincipal user, long tempFileId, TempFileModels.UpdateRequest request) {
        long familyId = familyStudentId(user);
        requireFile(familyId, tempFileId);
        String name = request == null ? null : trimToNull(request.originalFileName());
        String comment = request == null ? null : trimToNull(request.comment());
        if (name == null) throw TempFileApiException.invalid("ファイル名を入力してください。");
        mapper.updateMeta(familyId, tempFileId, name, comment, operator(user));
        return toSummary(mapper.findById(familyId, tempFileId));
    }

    @Transactional
    public void delete(UserPrincipal user, long tempFileId) {
        long familyId = familyStudentId(user);
        TempFileEntity file = requireFile(familyId, tempFileId);
        mapper.deleteById(familyId, tempFileId);
        storage.deleteIfExists(storage.resolve(familyId, file.getPath(), file.getStoredFileName()));
    }

    @Transactional
    public void deleteBatch(UserPrincipal user, List<Long> ids) {
        long familyId = familyStudentId(user);
        if (ids == null || ids.isEmpty()) return;
        List<Long> unique = new ArrayList<>(new LinkedHashSet<>(ids.stream().filter(Objects::nonNull).toList()));
        List<TempFileEntity> rows = unique.stream()
                .map(id -> mapper.findById(familyId, id)).filter(Objects::nonNull).toList();
        mapper.deleteByIds(familyId, unique);
        for (TempFileEntity row : rows) {
            storage.deleteIfExists(storage.resolve(familyId, row.getPath(), row.getStoredFileName()));
        }
    }

    @Transactional(readOnly = true)
    public TempFileModels.Download content(UserPrincipal user, long tempFileId) {
        long familyId = familyStudentId(user);
        TempFileEntity file = requireFile(familyId, tempFileId);
        var path = storage.resolve(familyId, file.getPath(), file.getStoredFileName());
        if (!Files.isRegularFile(path)) throw TempFileApiException.notFound();
        String name = file.getOriginalFileName() == null ? file.getStoredFileName() : file.getOriginalFileName();
        return new TempFileModels.Download(path, storage.contentType(path, file.getExtension()), name);
    }

    /** 画像編集「保存」: 加工済み画像で既存行を置き換える（ファイル差し替え + 縮小画像再生成）。 */
    @Transactional
    public TempFileModels.Summary replaceImage(UserPrincipal user, long tempFileId, MultipartFile file,
                                               String originalFileName, String comment) {
        long familyId = familyStudentId(user);
        TempFileEntity existing = requireFile(familyId, tempFileId);
        requireImage(file);
        TempFileStorage.StoredFile saved = storage.store(familyId, file);
        try {
            String name = originalFileName == null || originalFileName.isBlank()
                    ? existing.getOriginalFileName() : originalFileName.trim();
            mapper.updateImage(familyId, tempFileId, name, saved.storedName(), saved.extension(),
                    saved.mimeType(), saved.fileSize(), saved.relativePath(),
                    saved.thumbnail500(), saved.thumbnail200(), saved.thumbnail50(),
                    trimToNull(comment) == null ? existing.getComment() : trimToNull(comment), operator(user));
        } catch (RuntimeException ex) {
            storage.deleteIfExists(saved.physicalPath());
            throw ex;
        }
        storage.deleteIfExists(storage.resolve(familyId, existing.getPath(), existing.getStoredFileName()));
        return toSummary(mapper.findById(familyId, tempFileId));
    }

    /** 画像編集「別名保存」: 加工済み画像を新しい臨時ファイルとして追加する。 */
    @Transactional
    public TempFileModels.Summary saveAsImage(UserPrincipal user, long tempFileId, MultipartFile file,
                                              String originalFileName, String comment) {
        long familyId = familyStudentId(user);
        TempFileEntity existing = requireFile(familyId, tempFileId);
        requireImage(file);
        TempFileStorage.StoredFile saved = storage.store(familyId, file);
        try {
            String name = originalFileName == null || originalFileName.isBlank()
                    ? uniqueCopyName(existing.getOriginalFileName()) : originalFileName.trim();
            TempFileEntity entity = fromStored(familyId, saved, trimToNull(comment) == null
                    ? existing.getComment() : trimToNull(comment));
            entity.setOriginalFileName(name);
            mapper.insert(entity);
            return toSummary(entity);
        } catch (RuntimeException ex) {
            storage.deleteIfExists(saved.physicalPath());
            throw ex;
        }
    }

    private TempFileEntity fromStored(long familyId, TempFileStorage.StoredFile saved, String comment) {
        TempFileEntity entity = new TempFileEntity();
        entity.setFamilyStudentId(familyId);
        entity.setOriginalFileName(saved.originalName());
        entity.setStoredFileName(saved.storedName());
        entity.setExtension(saved.extension());
        entity.setMimeType(saved.mimeType());
        entity.setFileSize(saved.fileSize());
        entity.setPath(saved.relativePath());
        entity.setThumbnail500(saved.thumbnail500());
        entity.setThumbnail200(saved.thumbnail200());
        entity.setThumbnail50(saved.thumbnail50());
        entity.setComment(comment);
        return entity;
    }

    private TempFileModels.Summary toSummary(TempFileEntity e) {
        boolean image = storage.isImage(e.getExtension());
        String thumbnail = e.getThumbnail200() != null ? e.getThumbnail200() : e.getThumbnail50();
        String contentUrl = "/api/user/temp-files/" + e.getTempFileId() + "/content";
        return new TempFileModels.Summary(e.getTempFileId(), e.getOriginalFileName(), e.getExtension(),
                e.getMimeType(), e.getFileSize() == null ? 0L : e.getFileSize(), e.getComment(),
                thumbnail, image, contentUrl, e.getCreatedAt(), e.getUpdatedAt());
    }

    private TempFileEntity requireFile(long familyId, long tempFileId) {
        TempFileEntity file = mapper.findById(familyId, tempFileId);
        if (file == null) throw TempFileApiException.notFound();
        return file;
    }

    private void requireImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw TempFileApiException.invalid("画像ファイルを指定してください。");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".png") && !name.endsWith(".jpg") && !name.endsWith(".jpeg")
                && !name.endsWith(".gif") && !name.endsWith(".bmp") && !name.endsWith(".webp")) {
            throw TempFileApiException.invalid("画像ファイルを指定してください。");
        }
        if (file.getSize() > MAX_FILE_BYTES) throw TempFileApiException.invalid("1ファイルは20MB以下にしてください。");
    }

    private String uniqueCopyName(String original) {
        String base = original == null || original.isBlank() ? "image" : original.trim();
        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        String ext = dot > 0 ? base.substring(dot) : "";
        return stem + "_copy" + ext;
    }

    private long familyStudentId(UserPrincipal user) {
        List<Long> ids = mapper.findFamilyStudentIds(user.accountId(), user.accountType().code());
        if (ids.size() != 1) {
            throw TempFileApiException.invalid("学生と保護者の紐付けを一意に特定できません。");
        }
        return ids.getFirst();
    }

    private Timestamp[] periodWindow(String period) {
        if (period == null) return new Timestamp[] { null, null };
        long now = System.currentTimeMillis();
        return switch (period.trim()) {
            case "直近1日間" -> new Timestamp[] { new Timestamp(now - DAY_MS), null };
            case "直近1週間" -> new Timestamp[] { new Timestamp(now - 7 * DAY_MS), null };
            case "直近1ヵ月" -> new Timestamp[] { new Timestamp(now - 30 * DAY_MS), null };
            case "直近3ヵ月" -> new Timestamp[] { new Timestamp(now - 90 * DAY_MS), null };
            case "3ヵ月以前" -> new Timestamp[] { null, new Timestamp(now - 90 * DAY_MS) };
            default -> new Timestamp[] { null, null };
        };
    }

    private String operator(UserPrincipal user) { return String.valueOf(user.accountId()); }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
