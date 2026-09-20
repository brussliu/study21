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

    /**
     * ノート生成の**起動を受理する**（PHASE→batC61 / FINAL→batC62。AI の完了は待たない）。
     *
     * <p>受理だけして背景で走らせ、応答には**受理したか・いまの状態**を入れる。
     * 同じ要求を何度送っても、**2 つ目のタスクは作らない**（状態機械の条件つき更新で直列化する）。
     * 画面は結果を `GET /api/user/classroom/{id}` のポーリングで読む。</p>
     */
    @PostMapping("/notes/{noteId}/run")
    public ApiResponse<ClassroomAiPipelineService.Acceptance> run(
            @PathVariable long noteId,
            @RequestBody(required = false) Map<String, Object> request) {
        String operator = request == null || request.get("operator") == null
                ? null : String.valueOf(request.get("operator"));
        ClassroomAiPipelineService.Acceptance acceptance = pipelineService.accept(noteId, operator);
        return ApiResponse.ok(acceptance, acceptance.message());
    }
}
