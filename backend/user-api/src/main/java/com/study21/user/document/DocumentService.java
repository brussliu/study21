package com.study21.user.document;

import com.study21.user.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentService {
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    /** 分類（大分類〜細分類）に対応するフォルダ階層の上限。 */
    private static final int MAX_FOLDER_DEPTH = 4;
    private static final String DEPTH_EXCEEDED = "フォルダは大分類〜細分類の4階層までです。";

    private final DocumentMapper mapper;
    private final DocumentFileStorage storage;

    public DocumentService(DocumentMapper mapper, DocumentFileStorage storage) {
        this.mapper = mapper;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public DocumentModels.Workspace workspace(UserPrincipal user, Long folderId, String keyword) {
        long familyId = familyStudentId(user);
        if (folderId != null) requireFolder(familyId, folderId);
        List<DocumentModels.Folder> folders = mapper.findFolders(familyId).stream().map(this::toFolder).toList();
        // フォルダビューでファイルごとのアイコンを出すため、ファイルは 1 回のクエリでまとめて取得する。
        Map<String, List<DocumentModels.FileInfo>> filesByDocument = new HashMap<>();
        for (DocumentFileEntity file : mapper.findAllFiles(familyId)) {
            filesByDocument.computeIfAbsent(file.getDocumentNo(), key -> new ArrayList<>()).add(toFileInfo(file));
        }
        List<DocumentModels.Summary> documents = mapper.findDocuments(familyId, folderId, trimToNull(keyword)).stream()
                .map(d -> toSummary(d, filesByDocument.getOrDefault(d.getDocumentNo(), List.of()))).toList();
        return new DocumentModels.Workspace(folders, documents);
    }

    @Transactional
    public DocumentModels.Summary create(UserPrincipal user, DocumentModels.SaveRequest request,
                                         List<MultipartFile> uploads) {
        long familyId = familyStudentId(user);
        validateRequest(familyId, request);
        String documentNo = generateDocumentNo();
        DocumentEntity entity = fromRequest(documentNo, familyId, request);
        mapper.insertDocument(entity);
        List<DocumentFileStorage.StoredFile> stored = new ArrayList<>();
        try {
            insertUploads(user, familyId, documentNo, uploads, stored);
        } catch (RuntimeException ex) {
            stored.forEach(file -> storage.deleteIfExists(file.physicalPath()));
            throw ex;
        }
        DocumentEntity created = mapper.findDocument(familyId, documentNo);
        return toSummary(created, fileInfos(familyId, documentNo));
    }

    @Transactional
    public DocumentModels.Summary update(UserPrincipal user, String documentNo, DocumentModels.SaveRequest request) {
        long familyId = familyStudentId(user);
        requireDocument(familyId, documentNo);
        validateRequest(familyId, request);
        mapper.updateDocument(familyId, fromRequest(documentNo, familyId, request), operator(user));
        return toSummary(mapper.findDocument(familyId, documentNo), fileInfos(familyId, documentNo));
    }

    @Transactional
    public List<DocumentModels.FileInfo> upload(UserPrincipal user, String documentNo, List<MultipartFile> uploads) {
        long familyId = familyStudentId(user);
        requireDocument(familyId, documentNo);
        mapper.lockDocument(familyId, documentNo);
        List<DocumentFileStorage.StoredFile> stored = new ArrayList<>();
        try {
            insertUploads(user, familyId, documentNo, uploads, stored);
        } catch (RuntimeException ex) {
            stored.forEach(file -> storage.deleteIfExists(file.physicalPath()));
            throw ex;
        }
        return files(user, documentNo);
    }

    @Transactional(readOnly = true)
    public List<DocumentModels.FileInfo> files(UserPrincipal user, String documentNo) {
        long familyId = familyStudentId(user);
        requireDocument(familyId, documentNo);
        return mapper.findFiles(familyId, documentNo).stream().map(this::toFileInfo).toList();
    }

    @Transactional(readOnly = true)
    public DocumentModels.Download download(UserPrincipal user, String documentNo, int branchNo) {
        long familyId = familyStudentId(user);
        DocumentFileEntity file = mapper.findFile(familyId, documentNo, branchNo);
        if (file == null) throw DocumentApiException.notFound();
        var path = storage.resolve(familyId, file.getPath(), file.getStoredFileName());
        if (!Files.isRegularFile(path)) throw DocumentApiException.notFound();
        return new DocumentModels.Download(path, storage.contentType(path, file.getExtension()),
                file.getOriginalFileName() == null ? file.getStoredFileName() : file.getOriginalFileName());
    }

    @Transactional
    public void deleteFile(UserPrincipal user, String documentNo, int branchNo) {
        long familyId = familyStudentId(user);
        DocumentFileEntity file = mapper.findFile(familyId, documentNo, branchNo);
        if (file == null) throw DocumentApiException.notFound();
        var path = storage.resolve(familyId, file.getPath(), file.getStoredFileName());
        mapper.deleteFile(familyId, documentNo, branchNo);
        storage.deleteIfExists(path);
    }

    @Transactional
    public void delete(UserPrincipal user, String documentNo) {
        long familyId = familyStudentId(user);
        requireDocument(familyId, documentNo);
        List<DocumentFileEntity> files = mapper.findFiles(familyId, documentNo);
        mapper.deleteFilesByDocument(familyId, documentNo);
        mapper.deleteDocument(familyId, documentNo);
        for (DocumentFileEntity file : files) {
            var path = storage.resolve(familyId, file.getPath(), file.getStoredFileName());
            storage.deleteIfExists(path);
        }
    }

    @Transactional
    public DocumentModels.Folder createFolder(UserPrincipal user, DocumentModels.FolderRequest request) {
        long familyId = familyStudentId(user);
        if (request.parentFolderId() != null) {
            requireFolder(familyId, request.parentFolderId());
            // 分類列は 4 列（大分類〜細分類）しか無いため、5 階層目は作成できない。
            if (mapper.findFolderDepth(familyId, request.parentFolderId()) >= MAX_FOLDER_DEPTH) {
                throw DocumentApiException.invalid(DEPTH_EXCEEDED);
            }
        }
        DocumentFolderEntity folder = new DocumentFolderEntity();
        folder.setFamilyStudentId(familyId);
        folder.setParentFolderId(request.parentFolderId());
        folder.setFolderName(request.folderName().trim());
        folder.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        folder.setNote(trimToNull(request.note()));
        mapper.insertFolder(folder);
        return toFolder(folder);
    }

    @Transactional
    public DocumentModels.Folder updateFolder(UserPrincipal user, long folderId, DocumentModels.FolderRequest request) {
        long familyId = familyStudentId(user);
        requireFolder(familyId, folderId);
        if (request.parentFolderId() != null && request.parentFolderId() == folderId) {
            throw DocumentApiException.invalid("フォルダ自身を親に指定できません。");
        }
        if (request.parentFolderId() != null) requireFolder(familyId, request.parentFolderId());
        if (request.parentFolderId() != null
                && mapper.countFolderInSubtree(familyId, folderId, request.parentFolderId()) > 0) {
            throw DocumentApiException.invalid("子フォルダを親に指定できません。");
        }
        // 親変更で配下ごと深くなるため、移動後も 4 階層に収まるか確認する。
        long parentDepth = request.parentFolderId() == null ? 0
                : mapper.findFolderDepth(familyId, request.parentFolderId());
        if (parentDepth + 1 + mapper.findFolderSubtreeHeight(familyId, folderId) > MAX_FOLDER_DEPTH) {
            throw DocumentApiException.invalid(DEPTH_EXCEEDED);
        }
        mapper.updateFolder(familyId, folderId, request.parentFolderId(), request.folderName().trim(),
                request.displayOrder() == null ? 0 : request.displayOrder(), trimToNull(request.note()), operator(user));
        // 一覧ビューの分類列（大分類〜細分類）はフォルダ階層の互換列のため、
        // 名称・階層の変更を配下資料へ波及させる（更新日時は変更しない）。
        mapper.syncDocumentCategories(familyId, folderId);
        return toFolder(mapper.findFolder(familyId, folderId));
    }

    @Transactional
    public void deleteFolder(UserPrincipal user, long folderId) {
        long familyId = familyStudentId(user);
        requireFolder(familyId, folderId);
        if (mapper.countFolderChildren(familyId, folderId) > 0 || mapper.countFolderDocuments(familyId, folderId) > 0) {
            throw DocumentApiException.conflict("子フォルダまたは資料があるフォルダは削除できません。");
        }
        mapper.deleteFolder(familyId, folderId);
    }

    private void insertUploads(UserPrincipal user, long familyId, String documentNo, List<MultipartFile> uploads,
                               List<DocumentFileStorage.StoredFile> stored) {
        if (uploads == null) return;
        int branchNo = mapper.findMaxBranchNo(documentNo) + 1;
        for (MultipartFile upload : uploads) {
            if (upload == null || upload.isEmpty()) continue;
            if (upload.getSize() > MAX_FILE_BYTES) throw DocumentApiException.invalid("1ファイルは20MB以下にしてください。");
            var saved = storage.store(familyId, documentNo, branchNo, upload);
            stored.add(saved);
            DocumentFileEntity file = new DocumentFileEntity();
            file.setDocumentNo(documentNo);
            file.setBranchNo(branchNo++);
            file.setExtension(saved.extension());
            file.setOriginalFileName(saved.originalName());
            file.setPath(saved.relativePath());
            file.setStoredFileName(saved.storedName());
            mapper.insertFile(file, operator(user));
        }
    }

    private long familyStudentId(UserPrincipal user) {
        List<Long> ids = mapper.findFamilyStudentIds(user.accountId(), user.accountType().code());
        if (ids.size() != 1) {
            throw DocumentApiException.invalid("学生と保護者の紐付けを一意に特定できません。");
        }
        return ids.getFirst();
    }

    private void validateRequest(long familyId, DocumentModels.SaveRequest request) {
        if (request == null) throw DocumentApiException.invalid("資料情報を入力してください。");
        if (request.folderId() != null) requireFolder(familyId, request.folderId());
        String status = request.status() == null ? "1" : request.status();
        if (!status.equals("0") && !status.equals("1")) throw DocumentApiException.invalid("ステータスが不正です。");
        if (request.expiryDate() != null && !request.expiryDate().isBlank()) {
            try { LocalDate.parse(request.expiryDate(), LEGACY_DATE); }
            catch (Exception ex) { throw DocumentApiException.invalid("有効期限はYYYY/MM/DD形式で入力してください。"); }
        }
    }

    private void requireFolder(long familyId, long folderId) {
        if (mapper.findFolder(familyId, folderId) == null) throw DocumentApiException.notFound();
    }

    private DocumentEntity requireDocument(long familyId, String documentNo) {
        DocumentEntity document = mapper.findDocument(familyId, documentNo);
        if (document == null) throw DocumentApiException.notFound();
        return document;
    }

    private String generateDocumentNo() {
        for (int i = 0; i < 5; i++) {
            String candidate = "D" + UUID.randomUUID().toString().replace("-", "").substring(0, 19).toUpperCase(Locale.ROOT);
            if (mapper.countDocumentNo(candidate) == 0) return candidate;
        }
        throw new IllegalStateException("資料番号を生成できませんでした。");
    }

    private DocumentEntity fromRequest(String documentNo, long familyId, DocumentModels.SaveRequest request) {
        DocumentEntity entity = new DocumentEntity();
        entity.setDocumentNo(documentNo);
        entity.setFamilyStudentId(familyId);
        entity.setFolderId(request.folderId());
        entity.setStatus(request.status() == null ? "1" : request.status());
        entity.setExpiryDate(trimToNull(request.expiryDate()));
        entity.setComment(trimToNull(request.comment()));
        applyFolderCategories(entity, familyId);
        return entity;
    }

    /**
     * 分類（大分類〜細分類）はフォルダ階層そのものなので、選択フォルダの祖先名称から導出する。
     * 未分類（フォルダ未選択）の場合は 4 列ともクリアする。
     */
    private void applyFolderCategories(DocumentEntity entity, long familyId) {
        entity.setLargeCategory(null);
        entity.setMediumCategory(null);
        entity.setSmallCategory(null);
        entity.setDetailCategory(null);
        if (entity.getFolderId() == null) return;
        List<String> path = mapper.findFolderPathNames(familyId, entity.getFolderId());
        if (!path.isEmpty()) entity.setLargeCategory(path.get(0));
        if (path.size() > 1) entity.setMediumCategory(path.get(1));
        if (path.size() > 2) entity.setSmallCategory(path.get(2));
        if (path.size() > 3) entity.setDetailCategory(path.get(3));
    }

    private DocumentModels.Folder toFolder(DocumentFolderEntity f) {
        return new DocumentModels.Folder(f.getFolderId(), f.getParentFolderId(), f.getFolderName(),
                f.getDisplayOrder() == null ? 0 : f.getDisplayOrder(), f.getNote());
    }

    private DocumentModels.Summary toSummary(DocumentEntity d, List<DocumentModels.FileInfo> files) {
        return new DocumentModels.Summary(d.getDocumentNo(), d.getFolderId(), d.getStatus(), d.getExpiryDate(),
                d.getLargeCategory(), d.getMediumCategory(), d.getSmallCategory(), d.getDetailCategory(),
                d.getComment(), files.size(), files, d.getCreatedAt(), d.getUpdatedAt());
    }

    private List<DocumentModels.FileInfo> fileInfos(long familyId, String documentNo) {
        return mapper.findFiles(familyId, documentNo).stream().map(this::toFileInfo).toList();
    }

    private DocumentModels.FileInfo toFileInfo(DocumentFileEntity f) {
        String ext = f.getExtension() == null ? "" : f.getExtension().toLowerCase(Locale.ROOT);
        boolean image = List.of("png", "jpg", "jpeg", "gif", "webp", "bmp").contains(ext);
        String url = "/api/user/documents/" + f.getDocumentNo() + "/files/" + f.getBranchNo() + "/content";
        return new DocumentModels.FileInfo(f.getBranchNo(), f.getOriginalFileName(), ext, f.getComment(), url, image);
    }

    private String operator(UserPrincipal user) { return String.valueOf(user.accountId()); }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
