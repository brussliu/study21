package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.net.NetSiteModels;
import com.study21.user.net.NetSiteService;
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
 * サイト管理の API（2.0 の site.jsp / SiteController 相当）。
 *
 * プロキシの許可判定に使うデータを管理する。2.0 は操作後に batL01（プロキシ再起動）を
 * 起動していたが、2.1 の batL01 は未実装のため DB 更新だけを行う。
 */
@RestController
@RequestMapping("/api/user/net-sites")
public class NetSiteController {

    private final NetSiteService netSiteService;

    public NetSiteController(NetSiteService netSiteService) {
        this.netSiteService = netSiteService;
    }

    /** 一覧（検索条件・並び替え・ページング）。 */
    @GetMapping
    public ApiResponse<NetSiteModels.SiteSearchResult> search(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String judgeMethod,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(netSiteService.search(new NetSiteService.NetSiteSearchQuery(
                kind, judgeMethod, category, approvalStatus, status, keyword, sortBy, sortDir, page, size)));
    }

    /** 新規登録（未承認で登録される）。 */
    @PostMapping
    public ApiResponse<NetSiteModels.SiteMutationResult> create(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody NetSiteModels.SiteSaveRequest request) {
        NetSiteModels.SiteMutationResult result = netSiteService.create(user, request);
        return ApiResponse.ok(result, result.message());
    }

    /** 更新（未承認に戻る＝再承認が必要）。 */
    @PutMapping("/{siteId}")
    public ApiResponse<NetSiteModels.SiteMutationResult> update(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long siteId,
            @Valid @RequestBody NetSiteModels.SiteSaveRequest request) {
        NetSiteModels.SiteMutationResult result = netSiteService.update(user, siteId, request);
        return ApiResponse.ok(result, result.message());
    }

    @DeleteMapping("/{siteId}")
    public ApiResponse<NetSiteModels.SiteMutationResult> delete(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long siteId) {
        NetSiteModels.SiteMutationResult result = netSiteService.delete(user, siteId);
        return ApiResponse.ok(result, result.message());
    }

    /** 承認（1 件ずつ。承認者と承認日時を残す）。 */
    @PostMapping("/{siteId}/approval")
    public ApiResponse<NetSiteModels.SiteMutationResult> approve(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long siteId) {
        NetSiteModels.SiteMutationResult result = netSiteService.approve(user, siteId);
        return ApiResponse.ok(result, result.message());
    }

    /**
     * 却下（未承認・承認済みのどちらからでも「却下」にできる）。
     * 承認の取り消しとしても使う。
     */
    @PostMapping("/{siteId}/rejection")
    public ApiResponse<NetSiteModels.SiteMutationResult> reject(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long siteId) {
        NetSiteModels.SiteMutationResult result = netSiteService.reject(user, siteId);
        return ApiResponse.ok(result, result.message());
    }

}
