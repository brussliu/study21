package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.net.NetTerminalModels;
import com.study21.user.net.NetTerminalService;
import com.study21.user.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 端末コントロールの API（2.0 の terminal_control.jsp / TerminalControlController 相当）。
 *
 * ログイン必須。**当面ロールでは分けない**（生徒・保護者とも利用できる。2.0 は保護者限定だった）。
 * 一覧の参照・端末の新規登録・編集・モード変更（選択した複数台の一括）を提供する。
 * 2.0 はモード変更後に batL01（プロキシ再起動）を起動していたが、2.1 の batL01 は
 * 未実装のため DB 更新だけを行う。
 */
@RestController
@RequestMapping("/api/user/net-terminals")
public class NetTerminalController {

    private final NetTerminalService netTerminalService;

    public NetTerminalController(NetTerminalService netTerminalService) {
        this.netTerminalService = netTerminalService;
    }

    /** 一覧（端末モード・状態・キーワードで絞り込み）。 */
    @GetMapping
    public ApiResponse<NetTerminalModels.TerminalSearchResult> search(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(netTerminalService.search(user,
                new NetTerminalService.NetTerminalSearchQuery(mode, status, keyword, page, size)));
    }

    /** 端末を新規登録する。 */
    @PostMapping
    public ApiResponse<NetTerminalModels.TerminalMutationResult> create(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody NetTerminalModels.TerminalSaveRequest request) {
        NetTerminalModels.TerminalMutationResult result = netTerminalService.create(user, request);
        return ApiResponse.ok(result, result.message());
    }

    /** 端末の内容を編集する。 */
    @PutMapping("/{terminalId}")
    public ApiResponse<NetTerminalModels.TerminalMutationResult> update(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long terminalId,
            @Valid @RequestBody NetTerminalModels.TerminalSaveRequest request) {
        NetTerminalModels.TerminalMutationResult result = netTerminalService.update(user, terminalId, request);
        return ApiResponse.ok(result, result.message());
    }

    /** 1 台のモードを変更する。 */
    @PostMapping("/{terminalId}/mode")
    public ApiResponse<NetTerminalModels.TerminalMutationResult> updateMode(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable long terminalId,
            @Valid @RequestBody NetTerminalModels.ModeChangeRequest request) {
        NetTerminalModels.TerminalMutationResult result = netTerminalService.updateMode(user, terminalId, request);
        return ApiResponse.ok(result, result.message());
    }

    /** 選択した端末のモードを一括で変更する。 */
    @PostMapping("/mode")
    public ApiResponse<NetTerminalModels.TerminalMutationResult> updateModes(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody NetTerminalModels.BulkModeChangeRequest request) {
        NetTerminalModels.TerminalMutationResult result = netTerminalService.updateModes(user, request);
        return ApiResponse.ok(result, result.message());
    }
}
