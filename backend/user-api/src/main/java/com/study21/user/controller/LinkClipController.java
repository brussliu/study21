package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.linkclip.LinkClipModels;
import com.study21.user.linkclip.LinkClipService;
import com.study21.user.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * リンククリップ（2.0 の link_clip_demo 相当）。アカウント個人所有のため、
 * 所有者はログイン中のアカウントのみ。
 */
@RestController
@RequestMapping("/api/user")
public class LinkClipController {
    private final LinkClipService service;

    public LinkClipController(LinkClipService service) {
        this.service = service;
    }

    @GetMapping("/link-clips")
    public ApiResponse<LinkClipModels.Workspace> workspace(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(required = false) String folder,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String clipType,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean favorite,
            @RequestParam(defaultValue = "active") String archive) {
        return ApiResponse.ok(service.workspace(user, folder, source, clipType, tag, keyword, favorite, archive));
    }

    /** 保存前のプレビュー（Open Graph 等を取得。DB へは書かない）。 */
    @PostMapping(value = "/link-clips/preview")
    public ApiResponse<LinkClipModels.Preview> preview(@AuthenticationPrincipal UserPrincipal user,
                                                       @RequestBody LinkClipModels.PreviewRequest request) {
        return ApiResponse.ok(service.preview(user, request == null ? null : request.url()));
    }

    /** 同じ URL の既存クリップ（重複候補。登録は禁止しない）。 */
    @GetMapping("/link-clips/duplicates")
    public ApiResponse<LinkClipModels.DuplicateCheck> duplicates(@AuthenticationPrincipal UserPrincipal user,
                                                                @RequestParam String url) {
        return ApiResponse.ok(service.duplicates(user, url));
    }

    @PostMapping(value = "/link-clips", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<LinkClipModels.Saved> create(@AuthenticationPrincipal UserPrincipal user,
                                                    @Valid @RequestBody LinkClipModels.SaveRequest request) {
        return ApiResponse.ok(service.create(user, request));
    }

    @PutMapping(value = "/link-clips/{linkClipId}", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<LinkClipModels.Saved> update(@AuthenticationPrincipal UserPrincipal user,
                                                    @PathVariable long linkClipId,
                                                    @Valid @RequestBody LinkClipModels.UpdateRequest request) {
        return ApiResponse.ok(service.update(user, linkClipId, request));
    }

    @DeleteMapping("/link-clips/{linkClipId}")
    public ApiResponse<LinkClipModels.Deleted> delete(@AuthenticationPrincipal UserPrincipal user,
                                                      @PathVariable long linkClipId) {
        return ApiResponse.ok(service.delete(user, linkClipId));
    }

    /** お気に入り / 既読 / アーカイブの切り替え。 */
    @PostMapping(value = "/link-clips/{linkClipId}/flags", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<LinkClipModels.Saved> flags(@AuthenticationPrincipal UserPrincipal user,
                                                   @PathVariable long linkClipId,
                                                   @RequestBody LinkClipModels.FlagsRequest request) {
        return ApiResponse.ok(service.updateFlags(user, linkClipId, request));
    }

    /** リンクを開いた記録（閲覧回数 +1・最終閲覧日時・既読）。 */
    @PostMapping("/link-clips/{linkClipId}/view")
    public ApiResponse<LinkClipModels.Saved> recordView(@AuthenticationPrincipal UserPrincipal user,
                                                        @PathVariable long linkClipId) {
        return ApiResponse.ok(service.recordView(user, linkClipId));
    }
}
