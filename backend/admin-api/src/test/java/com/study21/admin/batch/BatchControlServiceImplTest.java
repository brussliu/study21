package com.study21.admin.batch;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
 * 切り替えられるのは batL / batR だけだった。2.1 では起動時実行の batS と、
 * <b>業務処理が実装済みの呼出（種別 C）</b>を加え、有効／無効の保存先を
 * BAT_バッチコントロール情報 に移した（その挙動を固定する）。
 *
 * <p>種別 C の「有効」は<b>いま使っているかの目印</b>であると同時に、
 * <b>無効にすると他の処理から呼び出せない</b>という意味を持つ
 * （呼出の拒否は {@link BatchRerunServiceImplTest} が固定する）。</p>
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
                mock(AiCallLogMapper.class), List.of(), List.of());
    }

    private BatchTaskDefinition definition(String code, BatchTaskType type, boolean active) {
        return new BatchTaskDefinition(code, type, code + " の説明", active, 5, null, "PAGE", List.of());
    }

    /**
     * ハンドラ（＝業務処理が実装済み）を持つサービス。
     * 種別 C を切り替えられるかは「実装済みか」で決まるので、その有無を差し替える。
     */
    private BatchServiceImpl serviceWithHandlers(String... taskCodes) {
        List<BatchTaskHandler> handlers = Arrays.stream(taskCodes)
                .map(code -> (BatchTaskHandler) new StubHandler(code))
                .toList();
        return new BatchServiceImpl(registry, mock(com.study21.admin.setting.SettingsService.class),
                executionMapper, controlMapper, mock(AiCallLogMapper.class), handlers, List.of());
    }

    /** 実行しないハンドラ（実装済みであることだけを示す）。 */
    private static class StubHandler implements BatchTaskHandler {
        private final String taskCode;

        StubHandler(String taskCode) {
            this.taskCode = taskCode;
        }

        @Override
        public String taskCode() {
            return taskCode;
        }

        @Override
        public String execute(BatchExecutionEntity execution) {
            throw new UnsupportedOperationException("このテストでは実行しない");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rowsOf(BatchServiceImpl target) {
        return (List<Map<String, Object>>) target.listTasks().get("rows");
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
    @DisplayName("切り替えられるバッチ（S / L / R と実装済みの種別 C）だけ行を用意する")
    void toggleableBatchesGetControlRowsOnList() {
        when(registry.findAll()).thenReturn(List.of(
                definition("batS01", BatchTaskType.S, true),
                definition("batR05", BatchTaskType.R, true),
                definition("batC41", BatchTaskType.C, true),
                definition("batC01", BatchTaskType.C, false)));
        when(controlMapper.findAll()).thenReturn(List.of());
        when(executionMapper.findLatestPerBatch()).thenReturn(List.of());

        serviceWithHandlers("batC41").listTasks();

        var captor = org.mockito.ArgumentCaptor.forClass(BatchControlEntity.class);
        verify(controlMapper, atLeast(1)).insertIfAbsent(captor.capture());
        Map<String, BatchControlEntity> inserted = captor.getAllValues().stream()
                .collect(Collectors.toMap(BatchControlEntity::getBatchCode, Function.identity(),
                        (first, second) -> first));
        // S / L / R は定義の既定値で行を作る（2.0 と同じ備考＝OFF にすると定時実行しない）
        assertThat(inserted).containsKeys("batS01", "batR05");
        assertThat(inserted.get("batS01").getNote())
                .isEqualTo("バッチ管理画面の有効設定（OFF時は定時実行しない）");
        assertThat(inserted.get("batS01").getStatus()).isEqualTo("1");
        // 実装済みの種別 C も切り替えられるので行を作る（OFF の意味が違うので備考も変える）
        assertThat(inserted.get("batC41").getNote())
                .isEqualTo("バッチ管理画面の有効設定（OFF時は他の処理から呼び出さない）");
        assertThat(inserted.get("batC41").getStatus()).isEqualTo("1");
        // 未実装の C は切り替えられないので行を作らない（定義の既定値のまま）
        assertThat(inserted).doesNotContainKey("batC01");
    }

    @Test
    @DisplayName("コントロール表に行が無い種別 C は、定義の既定値がそのまま一覧の有効になる")
    void callBatchesFallBackToTheirDefaultWithoutControlRow() {
        when(registry.findAll()).thenReturn(List.of(
                definition("batC41", BatchTaskType.C, true),
                definition("batC01", BatchTaskType.C, false)));
        when(controlMapper.findAll()).thenReturn(List.of());
        when(executionMapper.findLatestPerBatch()).thenReturn(List.of());

        List<Map<String, Object>> rows = rowsOf(serviceWithHandlers("batC41"));

        // 使っている C（日本語単語の AI 取得）は定義が有効なので、一覧でも有効に見える。
        // 実装済みなので画面から切り替えられる（2026-09-22 の利用者指示）
        assertThat(rows).filteredOn(row -> "batC41".equals(row.get("taskCode")))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row).containsEntry("active", true);
                    assertThat(row).containsEntry("canToggleActive", true);
                    assertThat(row).containsEntry("canManualRerun", false);
                });
        // 未実装の C は無効のままで、切り替えもできない
        assertThat(rows).filteredOn(row -> "batC01".equals(row.get("taskCode")))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row).containsEntry("active", false);
                    assertThat(row).containsEntry("canToggleActive", false);
                });
    }

    @Test
    @DisplayName("種別 C の有効はコントロール表が正（画面で無効にしたら一覧も無効）")
    void controlRowOverridesTheDefinitionForCallBatches() {
        when(registry.findAll()).thenReturn(List.of(definition("batC41", BatchTaskType.C, true)));
        when(controlMapper.findAll()).thenReturn(List.of(control("batC41", "0", 2)));
        when(executionMapper.findLatestPerBatch()).thenReturn(List.of());

        assertThat(rowsOf(serviceWithHandlers("batC41"))).singleElement()
                .satisfies(row -> {
                    assertThat(row).containsEntry("active", false);
                    assertThat(row).containsEntry("activeVersion", 2);
                    // 無効にしても切り替えられる（戻せる）
                    assertThat(row).containsEntry("canToggleActive", true);
                });
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
    @DisplayName("未実装の種別 C は切り替えられない（画面のスイッチは置灰のまま）")
    void unimplementedCallBatchesCannotBeToggled() {
        when(registry.findByCode("batC04")).thenReturn(definition("batC04", BatchTaskType.C, true));

        assertThatThrownBy(() -> service.updateActive("batC04", false, "admin"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("実装済みの呼出（種別 C）");
        verify(controlMapper, never()).updateStatus(anyString(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("実装済みの種別 C は画面から有効／無効を切り替えられる")
    void implementedCallBatchCanBeToggled() {
        when(registry.findAll()).thenReturn(List.of(definition("batC41", BatchTaskType.C, true)));
        when(registry.findByCode("batC41")).thenReturn(definition("batC41", BatchTaskType.C, true));
        when(controlMapper.findByBatchCode("batC41")).thenReturn(control("batC41", "1", 1));
        when(controlMapper.updateStatus("batC41", "0", 1, null, "admin")).thenReturn(1);

        Map<String, Object> result = serviceWithHandlers("batC41").updateActive("batC41", false, "admin");

        assertThat(result).containsEntry("success", true).containsEntry("active", false);
        assertThat(result).containsEntry("batchCode", "batC41");
        verify(controlMapper).updateStatus("batC41", "0", 1, null, "admin");
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
