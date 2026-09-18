package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.geometry.GeometryModels;
import com.study21.user.geometry.GeometryService;
import com.study21.user.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * 図形管理 API（user-api）。数学勉強＞図形管理の一覧画面と作図画面が使う。
 *
 * <ul>
 *   <li>`GET /figures` … 一覧（キーワード・種類・タグ・並び替え・ページング・サマリ）</li>
 *   <li>`GET /figures/{figureId}` … 1 件（GeoGebraXML とサムネイルつき。作図画面用）</li>
 *   <li>`GET /figures/{figureId}/thumbnail` … サムネイルの PNG（一覧の img src）</li>
 *   <li>`POST /figures` / `PUT /figures/{figureId}` … 保存（2.0 の `/api/geometry/save` 相当）</li>
 *   <li>`POST /figures/{figureId}/copy` … コピー</li>
 *   <li>`PATCH /figures/{figureId}/order` … 表示順</li>
 *   <li>`DELETE /figures/{figureId}` … 削除（論理削除）</li>
 *   <li>`GET /tags` … タグの候補</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/user/geometry")
public class GeometryController {

    private final GeometryService geometryService;

    public GeometryController(GeometryService geometryService) {
        this.geometryService = geometryService;
    }

    @GetMapping("/figures")
    public ApiResponse<GeometryModels.FigureListResult> figures(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "figureType", required = false) String figureType,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "24") int size) {
        return ApiResponse.ok(geometryService.search(keyword, figureType, tag, sort, includeDeleted, page, size));
    }

    @GetMapping("/figures/{figureId}")
    public ApiResponse<GeometryModels.FigureDetail> figure(@PathVariable long figureId) {
        return ApiResponse.ok(geometryService.detail(figureId));
    }

    /** 一覧のカードに出すサムネイル。画像なので ApiResponse ではなく PNG をそのまま返す。 */
    @GetMapping("/figures/{figureId}/thumbnail")
    public ResponseEntity<byte[]> thumbnail(@PathVariable long figureId) {
        byte[] png = geometryService.thumbnail(figureId);
        if (png == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(png);
    }

    @GetMapping("/tags")
    public ApiResponse<Map<String, Object>> tags(
            @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted) {
        return ApiResponse.ok(Map.of("items", geometryService.tags(includeDeleted)));
    }

    @PostMapping("/figures")
    public ApiResponse<GeometryModels.FigureMutationResult> create(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody GeometryModels.FigureSaveRequest request) {
        GeometryModels.FigureMutationResult result = geometryService.create(user, request);
        return ApiResponse.ok(result, result.message());
    }

    @PutMapping("/figures/{figureId}")
    public ApiResponse<GeometryModels.FigureMutationResult> update(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long figureId,
            @Valid @RequestBody GeometryModels.FigureSaveRequest request) {
        GeometryModels.FigureMutationResult result = geometryService.update(user, figureId, request);
        return ApiResponse.ok(result, result.message());
    }

    @PostMapping("/figures/{figureId}/copy")
    public ApiResponse<GeometryModels.FigureMutationResult> copy(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long figureId) {
        GeometryModels.FigureMutationResult result = geometryService.duplicate(user, figureId);
        return ApiResponse.ok(result, result.message());
    }

    @PatchMapping("/figures/{figureId}/order")
    public ApiResponse<GeometryModels.FigureMutationResult> order(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long figureId,
            @RequestBody GeometryModels.OrderRequest request) {
        GeometryModels.FigureMutationResult result = geometryService.updateOrder(user, figureId,
                request.displayOrder(), request.version());
        return ApiResponse.ok(result, result.message());
    }

    @DeleteMapping("/figures/{figureId}")
    public ApiResponse<GeometryModels.SimpleResult> delete(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long figureId) {
        GeometryModels.SimpleResult result = geometryService.delete(user, figureId);
        return ApiResponse.ok(result, result.message());
    }
}
