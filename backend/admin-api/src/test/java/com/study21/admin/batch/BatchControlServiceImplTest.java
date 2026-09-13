package com.study21.admin.batch;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * バッチの有効／無効（BAT_バッチコントロール情報）の業務ルール。
 *
 * 2.0 は COM_設定情報 の BATCH_TASK_ENABLED_&lt;コード&gt; に保存し、
 * 切り替えられるのは batL / batR だけだった。2.1 では起動時実行の batS を加え、
 * 有効／無効の保存先を BAT_バッチコントロール情報 に移した（その挙動を固定する）。
 */
class BatchControlServiceImplTest {

    private BatchTaskRegistry registry;
    private BatchExecutionMapper executionMapper;
    private BatchControlMapper controlMapper;
    private BatchServiceImpl service;

    @BeforeEach
    void setUp() {
        registry = mock(BatchTaskRegistry.class);
        executionMapper = mock(BatchExecutionMapper.class);
        controlMapper = mock(BatchControlMapper.class);
        var settingsService = mock(com.study21.admin.setting.SettingsService.class);
        service = new BatchServiceImpl(registry, settingsService, executionMapper, controlMapper,
                mock(AiCallLogMapper.class), List.of());
    }

    private BatchTaskDefinition definition(String code, BatchTaskType type, boolean active) {
        return new BatchTaskDefinition(code, type, code + " の説明", active, 5, null, "PAGE", List.of());
    }

    private BatchControlEntity control(String code, String status, int version) {
        BatchControlEntity entity = new BatchControlEntity();
        entity.setBatchCode(code);
        entity.setStatus(status);
        entity.setVersion(version);
        entity.setLastRunAt(new Timestamp(System.currentTimeMillis()));
        return entity;
    }

    @Test
    void scheduledAndLoopBatchesGetControlRowsOnList() {
        when(registry.findAll()).thenReturn(List.of(
                definition("batS01", BatchTaskType.S, true),
                definition("batR05", BatchTaskType.R, true),
                definition("batC04", BatchTaskType.C, true)));
        when(controlMapper.findAll()).thenReturn(List.of());
        when(executionMapper.findLatestPerBatch()).thenReturn(List.of());

        service.listTasks();

        // L / R だけ行を用意する（C は切り替え対象外）
        var captor = org.mockito.ArgumentCaptor.forClass(BatchControlEntity.class);
        verify(controlMapper, atLeast(1)).insertIfAbsent(captor.capture());
        assertThat(captor.getAllValues()).extracting(BatchControlEntity::getBatchCode)
                .contains("batS01", "batR05")
                .doesNotContain("batC04");
        assertThat(captor.getAllValues().get(0).getNote())
                .isEqualTo("バッチ管理画面の有効設定（OFF時は定時実行しない）");
        assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo("1");
    }

    @Test
    void listTasksTakesActiveStateFromTheControlTable() {
        when(registry.findAll()).thenReturn(List.of(definition("batS01", BatchTaskType.S, true)));
        when(controlMapper.findAll()).thenReturn(List.of(control("batS01", "0", 3)));
        when(executionMapper.findLatestPerBatch()).thenReturn(List.of());

        Map<String, Object> result = service.listTasks();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        // 定義では有効(true)でも、コントロール表で無効なら無効として返す
        assertThat(rows.get(0)).containsEntry("active", false);
        assertThat(rows.get(0)).containsEntry("activeVersion", 3);
        assertThat(rows.get(0).get("lastRunAt")).isNotNull();
    }

    @Test
    void onlyLoopAndScheduledBatchesCanBeToggled() {
        when(registry.findByCode("batC04")).thenReturn(definition("batC04", BatchTaskType.C, true));

        assertThatThrownBy(() -> service.updateActive("batC04", false, "admin"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("有効設定を変更できるのは batS / batL / batR のみです。");
        verify(controlMapper, never()).updateStatus(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void unknownBatchIsNotFound() {
        when(registry.findByCode("batX99")).thenReturn(null);

        assertThatThrownBy(() -> service.updateActive("batX99", true, "admin"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("バッチタスクが見つかりません");
    }

    @Test
    void toggleStoresStatusWithCurrentVersionAndOperator() {
        when(registry.findAll()).thenReturn(List.of(definition("batS01", BatchTaskType.S, true)));
        when(registry.findByCode("batS01")).thenReturn(definition("batS01", BatchTaskType.S, true));
        when(controlMapper.findByBatchCode("batS01")).thenReturn(control("batS01", "1", 4));
        when(controlMapper.updateStatus("batS01", "0", 4, null, "admin")).thenReturn(1);

        Map<String, Object> result = service.updateActive("batS01", false, " admin ");

        assertThat(result).containsEntry("active", false);
        assertThat(result).containsEntry("message", "バッチの有効設定を更新しました。");
        verify(controlMapper).updateStatus("batS01", "0", 4, null, "admin");
    }

    @Test
    void toggleDetectsConcurrentUpdate() {
        when(registry.findAll()).thenReturn(List.of(definition("batR05", BatchTaskType.R, true)));
        when(registry.findByCode("batR05")).thenReturn(definition("batR05", BatchTaskType.R, true));
        when(controlMapper.findByBatchCode("batR05")).thenReturn(control("batR05", "1", 1));
        when(controlMapper.updateStatus(eq("batR05"), eq("0"), eq(1), isNull(), anyString())).thenReturn(0);

        assertThatThrownBy(() -> service.updateActive("batR05", false, "admin"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("他の管理者が先に更新しました");
    }
}
