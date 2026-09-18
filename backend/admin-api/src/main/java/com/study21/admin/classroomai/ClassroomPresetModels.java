package com.study21.admin.classroomai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 前置詞プリセット管理（設定画面のサブパネル・admin-api）のモデル。
 */
public final class ClassroomPresetModels {

    private ClassroomPresetModels() {
    }

    /** 追加・更新の入力。スコープは常に GLOBAL（登録者アカウントID は NULL）。 */
    public record SaveRequest(
            @NotBlank(message = "プリセット名を入力してください。")
            @Size(max = 200, message = "プリセット名は200文字以内で入力してください。")
            String name,
            /** 任意。AI が重点的に整理する内容など */
            String text,
            @Min(value = 0, message = "表示順は 0 以上で指定してください。")
            @Max(value = 10000, message = "表示順が大きすぎます。")
            Integer displayOrder) {
    }

    /** 一覧・追加・更新の応答。 */
    public record PresetView(
            long presetId,
            String scope,
            String name,
            String text,
            int displayOrder,
            String createdAt,
            String updatedAt) {
    }

    public record PresetListResult(List<PresetView> items) {
    }

    public record DeleteResult(long presetId, String message) {
    }
}
