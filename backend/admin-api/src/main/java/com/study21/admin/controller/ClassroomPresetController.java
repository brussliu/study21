package com.study21.admin.controller;

import com.study21.admin.classroomai.ClassroomPresetModels;
import com.study21.admin.classroomai.ClassroomPresetService;
import com.study21.common.core.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前置詞プリセット管理（設定画面のサブパネル・admin-api）。
 *
 * <p>管理するのは **GLOBAL スコープのみ**（user-api の {@code GET /api/user/classroom/presets} が
 * 「GLOBAL + 自分のスコープ」を返す）。GLOBAL 以外の ID は 404。</p>
 */
@RestController
@RequestMapping("/api/admin/classroom-presets")
public class ClassroomPresetController {

    private final ClassroomPresetService presetService;

    public ClassroomPresetController(ClassroomPresetService presetService) {
        this.presetService = presetService;
    }

    @GetMapping
    public ApiResponse<ClassroomPresetModels.PresetListResult> list() {
        return ApiResponse.ok(new ClassroomPresetModels.PresetListResult(presetService.list()));
    }

    @PostMapping
    public ApiResponse<ClassroomPresetModels.PresetView> create(
            @Valid @RequestBody ClassroomPresetModels.SaveRequest request) {
        return ApiResponse.ok(presetService.create(request), "前置詞プリセットを追加しました。");
    }

    @PutMapping("/{presetId}")
    public ApiResponse<ClassroomPresetModels.PresetView> update(
            @PathVariable long presetId,
            @Valid @RequestBody ClassroomPresetModels.SaveRequest request) {
        return ApiResponse.ok(presetService.update(presetId, request), "前置詞プリセットを更新しました。");
    }

    @DeleteMapping("/{presetId}")
    public ApiResponse<ClassroomPresetModels.DeleteResult> delete(@PathVariable long presetId) {
        ClassroomPresetModels.DeleteResult result = presetService.delete(presetId);
        return ApiResponse.ok(result, result.message());
    }
}
