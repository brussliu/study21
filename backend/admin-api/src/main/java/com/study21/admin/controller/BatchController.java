package com.study21.admin.controller;

import com.study21.admin.batch.BatchService;
import com.study21.common.core.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * バッチ管理 API（管理者向け）。
 *
 * <p>注: 本スケルトンは認証未実装のため、本エンドポイントは SecurityConfig で許可されている。
 * 認証導入時は ADMIN ロール必須とする（フロントの仮認証は本APIには適用されない）。</p>
 */
@RestController
@RequestMapping("/api/admin/batch")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    /** バッチ一覧（有効／無効・最新実行・設定充足状態）。 */
    @GetMapping("/tasks")
    public ApiResponse<Map<String, Object>> tasks() {
        return ApiResponse.ok(batchService.listTasks());
    }

    /**
     * バッチの【再実行】。実行履歴に 1 行追加し、業務処理をその場で実行する。
     * 有効／無効に関係なく実行できる（無効は「定時実行しない」の意味）。
     */
    @PostMapping("/tasks/{batchCode}/rerun")
    public ApiResponse<Map<String, Object>> rerun(
            @PathVariable String batchCode,
            @RequestBody(required = false) Map<String, Object> request) {
        String operator = stringOf(request == null ? null : request.get("operator"));
        return ApiResponse.ok(batchService.rerun(batchCode, operator));
    }

    /**
     * バッチの有効／無効を切り替える（batS / batL / batR のみ）。
     * 2.0 は COM_設定情報 に保存していたが、2.1 は BAT_バッチコントロール情報 に保存する。
     */
    @PostMapping("/tasks/{batchCode}/active")
    public ApiResponse<Map<String, Object>> updateActive(
            @PathVariable String batchCode,
            @RequestBody(required = false) Map<String, Object> request) {
        boolean active = Boolean.TRUE.equals(request == null ? null : request.get("active"));
        String operator = stringOf(request == null ? null : request.get("operator"));
        return ApiResponse.ok(batchService.updateActive(batchCode, active, operator));
    }

    /** 実行履歴（新しい順・ページング。バッチコード／状態／キーワードで絞り込み）。 */
    @GetMapping("/executions")
    public ApiResponse<Map<String, Object>> executions(
            @RequestParam(value = "batchCode", required = false) String batchCode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(batchService.history(batchCode, status, keyword, page, size));
    }

    /**
     * AI 呼び出し履歴（新しい順・ページング）。
     * バッチコード／AI 区分／結果／キーワード／開始日時の範囲で絞り込める。
     * 一覧では本文（プロンプト・レスポンス）を返さない。
     */
    @GetMapping("/ai-calls")
    public ApiResponse<Map<String, Object>> aiCalls(
            @RequestParam(value = "batchCode", required = false) String batchCode,
            @RequestParam(value = "aiType", required = false) String aiType,
            @RequestParam(value = "result", required = false) String result,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "startFrom", required = false) String startFrom,
            @RequestParam(value = "startTo", required = false) String startTo,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(batchService.aiCalls(batchCode, aiType, result, keyword,
                startFrom, startTo, page, size));
    }

    /** AI呼出履歴画面の絞り込みに出す値（AI 区分・バッチコード・モデル名の実データ一覧）。 */
    @GetMapping("/ai-calls/filters")
    public ApiResponse<Map<String, Object>> aiCallFilters() {
        return ApiResponse.ok(batchService.aiCallFilters());
    }

    /** AI 呼び出し履歴の 1 件（プロンプト・レスポンスの本文を含む）。 */
    @GetMapping("/ai-calls/{callId}")
    public ApiResponse<Map<String, Object>> aiCallDetail(@PathVariable long callId) {
        return ApiResponse.ok(batchService.aiCallDetail(callId));
    }

    private String stringOf(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
