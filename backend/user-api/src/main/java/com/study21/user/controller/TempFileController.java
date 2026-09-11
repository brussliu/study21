package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.security.UserPrincipal;
import com.study21.user.tempfile.TempFileModels;
import com.study21.user.tempfile.TempFileService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/user/temp-files")
public class TempFileController {
    private final TempFileService service;

    public TempFileController(TempFileService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<TempFileModels.Summary>> search(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "300") int limit) {
        return ApiResponse.ok(service.search(user, keyword, type, period, limit));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<TempFileModels.Summary>> upload(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(required = false) String comment) {
        return ApiResponse.ok(service.upload(user, files, comment));
    }

    @PutMapping(value = "/{tempFileId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<TempFileModels.Summary> updateMeta(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long tempFileId,
            @Valid @RequestBody TempFileModels.UpdateRequest request) {
        return ApiResponse.ok(service.updateMeta(user, tempFileId, request));
    }

    @DeleteMapping("/{tempFileId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal user, @PathVariable long tempFileId) {
        service.delete(user, tempFileId);
        return ApiResponse.ok(null);
    }

    @PostMapping(value = "/batch-delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> deleteBatch(@AuthenticationPrincipal UserPrincipal user,
                                         @RequestBody TempFileModels.DeleteRequest request) {
        service.deleteBatch(user, request == null ? null : request.ids());
        return ApiResponse.ok(null);
    }

    @GetMapping("/{tempFileId}/content")
    public ResponseEntity<Resource> content(@AuthenticationPrincipal UserPrincipal user,
                                            @PathVariable long tempFileId,
                                            @RequestParam(defaultValue = "false") boolean download) {
        TempFileModels.Download file = service.content(user, tempFileId);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(file.downloadName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new FileSystemResource(file.path()));
    }

    @PostMapping(value = "/{tempFileId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TempFileModels.Summary> replaceImage(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long tempFileId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String originalFileName,
            @RequestParam(required = false) String comment) {
        return ApiResponse.ok(service.replaceImage(user, tempFileId, file, originalFileName, comment));
    }

    @PostMapping(value = "/{tempFileId}/save-as", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TempFileModels.Summary> saveAsImage(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long tempFileId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String originalFileName,
            @RequestParam(required = false) String comment) {
        return ApiResponse.ok(service.saveAsImage(user, tempFileId, file, originalFileName, comment));
    }
}
