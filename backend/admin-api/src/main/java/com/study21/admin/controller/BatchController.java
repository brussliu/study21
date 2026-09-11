package com.study21.admin.controller;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchService;
import com.study21.common.core.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
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

    @GetMapping("/tasks")
    public ApiResponse<Map<String, Object>> tasks() {
        return ApiResponse.ok(batchService.listTasks());
    }

    @PostMapping("/trigger")
    public ApiResponse<Map<String, Object>> trigger(@RequestBody(required = false) Map<String, Object> request) {
        String taskCode = stringOf(request == null ? null : request.get("taskCode"));
        String requestedBy = stringOf(request == null ? null : request.get("requestedBy"));
        return ApiResponse.ok(batchService.trigger(taskCode, requestedBy));
    }

    @GetMapping("/executions")
    public ApiResponse<List<BatchExecutionEntity>> executions(
            @RequestParam(value = "taskCode", required = false) String taskCode,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return ApiResponse.ok(batchService.recentExecutions(taskCode, limit));
    }

    private String stringOf(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
