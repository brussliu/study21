package com.study21.admin.classroomai;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * CR_前置詞プリセット情報（GLOBAL スコープ）の Mapper（admin-api 側・設定画面）。
 *
 * <p>設定画面の専用サブパネルは **GLOBAL スコープだけ**を管理する。更新・削除は
 * {@code スコープ='GLOBAL'} を条件に含める（他スコープを書き換えない）。</p>
 */
@Mapper
public interface ClassroomPresetMapper {

    /** GLOBAL スコープのプリセット（表示順 → 前置詞ID の順）。 */
    List<ClassroomPresetEntity> findGlobal();

    ClassroomPresetEntity findById(@Param("presetId") long presetId);

    int insert(ClassroomPresetEntity entity);

    /** GLOBAL だけ更新する（0 行なら他スコープか削除済み）。 */
    int updateGlobal(ClassroomPresetEntity entity);

    /** GLOBAL だけ削除する（0 行なら他スコープか削除済み）。 */
    int deleteGlobal(@Param("presetId") long presetId);
}
