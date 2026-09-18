package com.study21.user.classroom;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * CR_前置詞プリセット情報（授業の「前置詞」シナリオプリセット）の Mapper（user-api 側）。
 *
 * <p>画面（録音開始時の選択肢）が読むのは **GLOBAL + 自分のスコープ** だけ。プリセットの
 * 追加・削除・並べ替えは admin-api の設定画面（専用サブパネル）が行う。</p>
 */
@Mapper
public interface ClassroomPresetMapper {

    /** GLOBAL と、そのアカウントが登録したプリセット（表示順）。 */
    List<ClassroomPresetEntity> findVisible(@Param("accountId") Long accountId);

    ClassroomPresetEntity findById(@Param("presetId") long presetId);
}
