package com.study21.admin.controller;

import com.study21.admin.geometryai.AiAssistPipelineService;
import com.study21.common.core.api.ApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 画図助手（batC52）の起動（admin-api・薄い入口）。
 *
 * <p>画面（user-api で依頼を作ったあと）が**このエンドポイントを 1 回だけ**呼ぶ。内部で batC52 を
 * **同期で最後まで実行**して結果（READY / FAILED）を返す（`?async=true` は付けない。待たせない）。
 * バッチコードは batC52、URL は {@code /api/admin/batch/geometry-assist/{assistId}/run}。</p>
 *
 * <p>**認証導入時の TODO**: 本スケルトンは認証未実装のため `/api/admin/batch/**` は許可されている。
 * このエンドポイントは「既存の依頼行しか処理しない（新しい依頼は作れない）」ので被害は限られるが、
 * 認証導入時は ADMIN ロールまたは当該依頼の所有者に制限する。</p>
 */
@RestController
@RequestMapping("/api/admin/batch/geometry-assist")
public class GeometryAiAssistBatchController {

    private final AiAssistPipelineService pipelineService;

    public GeometryAiAssistBatchController(AiAssistPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    /** 依頼を batC52 で同期実行する（即時・待たない）。 */
    @PostMapping("/{assistId}/run")
    public ApiResponse<Map<String, Object>> run(
            @PathVariable long assistId,
            @RequestBody(required = false) Map<String, Object> request) {
        String operator = request == null || request.get("operator") == null
                ? null : String.valueOf(request.get("operator"));
        Map<String, Object> result = pipelineService.run(assistId, operator);
        return ApiResponse.ok(result, "AI 画図助手の変更案を生成しました。");
    }
}
