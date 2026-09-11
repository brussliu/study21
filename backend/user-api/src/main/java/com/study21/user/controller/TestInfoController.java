package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.security.UserPrincipal;
import com.study21.user.testinfo.TestInfoModels;
import com.study21.user.testinfo.TestInfoService;
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

/** テスト情報管理（2.0 の testinfo.jsp 相当）。家族所有権の範囲内でのみ操作できる。 */
@RestController
@RequestMapping("/api/user")
public class TestInfoController {
    private final TestInfoService service;

    public TestInfoController(TestInfoService service) {
        this.service = service;
    }

    @GetMapping("/test-infos")
    public ApiResponse<TestInfoModels.Workspace> workspace(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.workspace(user, subject, kind, keyword));
    }

    @PostMapping(value = "/test-infos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TestInfoModels.Saved> create(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestPart("metadata") TestInfoModels.SaveRequest metadata,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return ApiResponse.ok(service.create(user, metadata, files));
    }

    @PutMapping(value = "/test-infos/{testNo}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<TestInfoModels.Saved> update(
            @AuthenticationPrincipal UserPrincipal user, @PathVariable String testNo,
            @Valid @RequestBody TestInfoModels.UpdateRequest request) {
        return ApiResponse.ok(service.update(user, testNo, request));
    }

    @DeleteMapping("/test-infos/{testNo}")
    public ApiResponse<TestInfoModels.Deleted> delete(@AuthenticationPrincipal UserPrincipal user,
                                                      @PathVariable String testNo) {
        return ApiResponse.ok(service.delete(user, testNo));
    }

    @PostMapping(value = "/test-infos/{testNo}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TestInfoModels.Saved> upload(
            @AuthenticationPrincipal UserPrincipal user, @PathVariable String testNo,
            @RequestPart("files") List<MultipartFile> files) {
        return ApiResponse.ok(service.addFiles(user, testNo, files));
    }

    @PostMapping(value = "/test-infos/{testNo}/files/from-temp-file", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<TestInfoModels.Saved> fromTempFile(
            @AuthenticationPrincipal UserPrincipal user, @PathVariable String testNo,
            @Valid @RequestBody TestInfoModels.FromTempFileRequest request) {
        return ApiResponse.ok(service.addFromTempFile(user, testNo, request.tempFileId()));
    }

    @PostMapping(value = "/test-infos/{testNo}/files/from-test-file", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<TestInfoModels.Saved> fromTestFile(
            @AuthenticationPrincipal UserPrincipal user, @PathVariable String testNo,
            @Valid @RequestBody TestInfoModels.FromTestFileRequest request) {
        return ApiResponse.ok(service.addFromTestFile(user, testNo, request.sourceTestNo(), request.sourceFileId()));
    }

    /** 画像の回転・手書き注釈の保存（2.0 の showpic 保存相当）。 */
    @PostMapping(value = "/test-infos/{testNo}/files/{fileId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TestInfoModels.Saved> replaceImage(
            @AuthenticationPrincipal UserPrincipal user, @PathVariable String testNo, @PathVariable long fileId,
            @RequestPart("image") MultipartFile image,
            @RequestParam(required = false) Integer rotateDegrees) {
        return ApiResponse.ok(service.replaceFileImage(user, testNo, fileId, image, rotateDegrees));
    }

    @DeleteMapping("/test-infos/{testNo}/files/{fileId}")
    public ApiResponse<TestInfoModels.Saved> deleteFile(@AuthenticationPrincipal UserPrincipal user,
                                                        @PathVariable String testNo, @PathVariable long fileId) {
        return ApiResponse.ok(service.deleteFile(user, testNo, fileId));
    }

    @GetMapping("/test-infos/{testNo}/files/{fileId}/content")
    public ResponseEntity<Resource> content(@AuthenticationPrincipal UserPrincipal user,
                                            @PathVariable String testNo, @PathVariable long fileId,
                                            @RequestParam(defaultValue = "false") boolean download) {
        TestInfoModels.Download file = service.download(user, testNo, fileId);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(file.downloadName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new FileSystemResource(file.path()));
    }
}
