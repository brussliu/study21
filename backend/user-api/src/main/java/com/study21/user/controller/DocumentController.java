package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.document.DocumentModels;
import com.study21.user.document.DocumentService;
import com.study21.user.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/user")
public class DocumentController {
    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping("/documents")
    public ApiResponse<DocumentModels.Workspace> workspace(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.workspace(user, folderId, keyword));
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentModels.Summary> create(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestPart("metadata") DocumentModels.SaveRequest metadata,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return ApiResponse.ok(service.create(user, metadata, files));
    }

    @PutMapping(value = "/documents/{documentNo}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<DocumentModels.Summary> update(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String documentNo,
            @Valid @org.springframework.web.bind.annotation.RequestBody DocumentModels.SaveRequest request) {
        return ApiResponse.ok(service.update(user, documentNo, request));
    }

    @DeleteMapping("/documents/{documentNo}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal user, @PathVariable String documentNo) {
        service.delete(user, documentNo);
        return ApiResponse.ok(null);
    }

    @GetMapping("/documents/{documentNo}/files")
    public ApiResponse<List<DocumentModels.FileInfo>> files(
            @AuthenticationPrincipal UserPrincipal user, @PathVariable String documentNo) {
        return ApiResponse.ok(service.files(user, documentNo));
    }

    @PostMapping(value = "/documents/{documentNo}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<DocumentModels.FileInfo>> upload(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String documentNo,
            @RequestPart("files") List<MultipartFile> files) {
        return ApiResponse.ok(service.upload(user, documentNo, files));
    }

    @GetMapping("/documents/{documentNo}/files/{branchNo}/content")
    public ResponseEntity<Resource> content(@AuthenticationPrincipal UserPrincipal user,
                                            @PathVariable String documentNo, @PathVariable int branchNo,
                                            @RequestParam(defaultValue = "false") boolean download) {
        DocumentModels.Download file = service.download(user, documentNo, branchNo);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(file.downloadName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new FileSystemResource(file.path()));
    }

    @DeleteMapping("/documents/{documentNo}/files/{branchNo}")
    public ApiResponse<Void> deleteFile(@AuthenticationPrincipal UserPrincipal user,
                                        @PathVariable String documentNo, @PathVariable int branchNo) {
        service.deleteFile(user, documentNo, branchNo);
        return ApiResponse.ok(null);
    }

    @PostMapping(value = "/document-folders", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<DocumentModels.Folder> createFolder(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @org.springframework.web.bind.annotation.RequestBody DocumentModels.FolderRequest request) {
        return ApiResponse.ok(service.createFolder(user, request));
    }

    @PutMapping(value = "/document-folders/{folderId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<DocumentModels.Folder> updateFolder(
            @AuthenticationPrincipal UserPrincipal user, @PathVariable long folderId,
            @Valid @org.springframework.web.bind.annotation.RequestBody DocumentModels.FolderRequest request) {
        return ApiResponse.ok(service.updateFolder(user, folderId, request));
    }

    @DeleteMapping("/document-folders/{folderId}")
    public ApiResponse<Void> deleteFolder(@AuthenticationPrincipal UserPrincipal user, @PathVariable long folderId) {
        service.deleteFolder(user, folderId);
        return ApiResponse.ok(null);
    }
}
