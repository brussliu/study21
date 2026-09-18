package com.study21.admin.classroomai;

import java.util.List;

/**
 * 前置詞プリセット（GLOBAL スコープ）の管理（設定画面のサブパネル）。
 */
public interface ClassroomPresetService {

    List<ClassroomPresetModels.PresetView> list();

    ClassroomPresetModels.PresetView create(ClassroomPresetModels.SaveRequest request);

    ClassroomPresetModels.PresetView update(long presetId, ClassroomPresetModels.SaveRequest request);

    ClassroomPresetModels.DeleteResult delete(long presetId);
}
