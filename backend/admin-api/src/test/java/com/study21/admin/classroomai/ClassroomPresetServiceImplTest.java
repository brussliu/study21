package com.study21.admin.classroomai;

import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 前置詞プリセット管理（GLOBAL スコープ）の業務ルール。
 *
 * ・追加は常に GLOBAL（登録者アカウントID=NULL）
 * ・名前は必須（空は 400）
 * ・GLOBAL 以外の ID は 404（更新・削除）
 */
class ClassroomPresetServiceImplTest {

    private ClassroomPresetMapper presetMapper;
    private ClassroomPresetServiceImpl service;

    @BeforeEach
    void setUp() {
        presetMapper = mock(ClassroomPresetMapper.class);
        service = new ClassroomPresetServiceImpl(presetMapper);
    }

    private ClassroomPresetEntity global(long id, String name) {
        ClassroomPresetEntity entity = new ClassroomPresetEntity();
        entity.setPresetId(id);
        entity.setScope("GLOBAL");
        entity.setName(name);
        entity.setDisplayOrder(0);
        entity.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        entity.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return entity;
    }

    private static ClassroomPresetModels.SaveRequest request(String name, String text, Integer order) {
        return new ClassroomPresetModels.SaveRequest(name, text, order);
    }

    @Test
    void createInsertsGlobalWithNullOwner() {
        when(presetMapper.insert(any())).thenAnswer(invocation -> {
            ClassroomPresetEntity entity = invocation.getArgument(0);
            entity.setPresetId(99L);
            return 1;
        });
        when(presetMapper.findById(99L)).thenReturn(global(99L, "数学の授業"));

        ClassroomPresetModels.PresetView view = service.create(request("  数学の授業  ", "公式を整理", 3));

        ArgumentCaptor<ClassroomPresetEntity> captor = ArgumentCaptor.forClass(ClassroomPresetEntity.class);
        verify(presetMapper).insert(captor.capture());
        assertThat(captor.getValue().getScope()).isEqualTo("GLOBAL");
        assertThat(captor.getValue().getCreatedBy()).isNull();
        assertThat(captor.getValue().getName()).isEqualTo("数学の授業");
        assertThat(captor.getValue().getDisplayOrder()).isEqualTo(3);
        assertThat(view.presetId()).isEqualTo(99L);
    }

    @Test
    void createRejectsBlankName() {
        assertThatThrownBy(() -> service.create(request("   ", null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("プリセット名を入力してください");
        verify(presetMapper, never()).insert(any());
    }

    @Test
    void createDefaultsDisplayOrderToZero() {
        when(presetMapper.insert(any())).thenAnswer(invocation -> {
            ClassroomPresetEntity entity = invocation.getArgument(0);
            entity.setPresetId(100L);
            return 1;
        });
        when(presetMapper.findById(100L)).thenReturn(global(100L, "通常の授業"));

        service.create(request("通常の授業", null, null));

        ArgumentCaptor<ClassroomPresetEntity> captor = ArgumentCaptor.forClass(ClassroomPresetEntity.class);
        verify(presetMapper).insert(captor.capture());
        assertThat(captor.getValue().getDisplayOrder()).isEqualTo(0);
    }

    @Test
    void updateNonGlobalThrows404() {
        ClassroomPresetEntity family = global(7L, "家庭の授業");
        family.setScope("FAMILY");
        family.setCreatedBy(1L);
        when(presetMapper.findById(7L)).thenReturn(family);

        assertThatThrownBy(() -> service.update(7L, request("書き換え", null, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("見つかりません");
        verify(presetMapper, never()).updateGlobal(any());
    }

    @Test
    void deleteNonGlobalThrows404() {
        ClassroomPresetEntity student = global(8L, "個人の授業");
        student.setScope("STUDENT");
        student.setCreatedBy(2L);
        when(presetMapper.findById(8L)).thenReturn(student);

        assertThatThrownBy(() -> service.delete(8L))
                .isInstanceOf(NotFoundException.class);
        verify(presetMapper, never()).deleteGlobal(anyLong());
    }

    @Test
    void listReturnsGlobalOnly() {
        when(presetMapper.findGlobal()).thenReturn(List.of(global(1L, "通常の授業")));
        assertThat(service.list()).hasSize(1);
        assertThat(service.list().get(0).name()).isEqualTo("通常の授業");
    }
}
