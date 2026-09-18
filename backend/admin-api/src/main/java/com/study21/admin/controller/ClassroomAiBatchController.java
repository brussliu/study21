package com.study21.admin.controller;

import com.study21.admin.classroomai.ClassroomAiPipelineService;
import com.study21.common.core.api.ApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 授業ノート生成の起動（admin-api・薄い入口）。
 *
 * <p>画面（user-api で分塊を送ったあと、トリガー成立や終了時に返る noteId を元に）が
 * **このエンドポイントを 1 回だけ**呼ぶ。内部で PHASE→batC61 / FINAL→batC62 を実行する
 * （`GeometryAiBatchController` 相当。設計 §9）。</p>
 *
 * <p>**認証導入時の TODO**: 本スケルトンは認証未実装のため `/api/admin/batch/**` は許可されている。
 * このエンドポイントは「既存のノート行しか処理しない（新しいノートは作れない）」ので被害は限られるが、
 * 認証導入時は ADMIN ロールまたは当該ノートの所有者に制限する。</p>
 */
@RestController
@RequestMapping("/api/admin/batch/classroom")
public class ClassroomAiBatchController {

    private final ClassroomAiPipelineService pipelineService;

    public ClassroomAiBatchController(ClassroomAiPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    /** ノート生成（PHASE→batC61 / FINAL→batC62）。**同期**で最後まで進める。 */
    @PostMapping("/notes/{noteId}/run")
    public ApiResponse<Map<String, Object>> run(
            @PathVariable long noteId,
            @RequestBody(required = false) Map<String, Object> request) {
        String operator = request == null || request.get("operator") == null
                ? null : String.valueOf(request.get("operator"));
        Map<String, Object> result = pipelineService.run(noteId, operator);
        return ApiResponse.ok(result, "授業ノートの生成を実行しました。");
    }
}
