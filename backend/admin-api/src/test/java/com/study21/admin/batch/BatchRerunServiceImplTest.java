package com.study21.admin.batch;

import com.study21.admin.setting.SettingsService;
import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * バッチの【再実行】と起動時実行（種別 S）の業務ルール。
 *
 * 2.0 の batL01（プロキシサービス）は 6 時間ごとの定時実行だったが、2.1 では
 * 「admin-api の起動時に 1 回」＋「画面の再実行」に限る。その実行の入口と、
 * 履歴（BAT_バッチ実行履歴情報）への記録内容を固定する。
 */
class BatchRerunServiceImplTest {

    private BatchTaskRegistry registry;
    private BatchExecutionMapper executionMapper;
    private BatchControlMapper controlMapper;
    private BatchServiceImpl service;
    private RecordingHandler handler;

    /** batS01 = プロキシサービスの代役。呼ばれた回数と戻り値／例外を制御する。 */
    private static class RecordingHandler implements BatchTaskHandler {
        private final String code;
        int calls;
        String summary = "Proxy started on port 7777";
        RuntimeException failure;

        RecordingHandler() {
            this("batS01");
        }

        RecordingHandler(String code) {
            this.code = code;
        }

        @Override
        public String taskCode() {
            return code;
        }

        @Override
        public String execute(BatchExecutionEntity execution) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return summary;
        }
    }

    @BeforeEach
    void setUp() {
        registry = mock(BatchTaskRegistry.class);
        executionMapper = mock(BatchExecutionMapper.class);
        controlMapper = mock(BatchControlMapper.class);
        handler = new RecordingHandler();
        service = new BatchServiceImpl(registry, mock(SettingsService.class),
                executionMapper, controlMapper, mock(AiCallLogMapper.class), List.of(handler));

        // insert で実行ID が採番される（MyBatis の useGeneratedKeys 相当）
        doAnswer(invocation -> {
            BatchExecutionEntity entity = invocation.getArgument(0);
            entity.setExecutionId(777L);
            return 1;
        }).when(executionMapper).insert(any(BatchExecutionEntity.class));
        when(controlMapper.touchLastRunAt(anyString())).thenReturn(1);
    }

    private BatchTaskDefinition definition(String code, BatchTaskType type, boolean active) {
        return new BatchTaskDefinition(code, type, code + " の説明", active, null, null, "SYSTEM", List.of());
    }

    /** listTasks() が返す行のうち、指定したバッチコードの 1 行。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> rowOf(BatchServiceImpl target, String batchCode) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) target.listTasks().get("rows");
        return rows.stream()
                .filter(row -> batchCode.equals(row.get("taskCode")))
                .findFirst()
                .orElseThrow();
    }

    private BatchControlEntity control(String code, String status) {
        BatchControlEntity entity = new BatchControlEntity();
        entity.setBatchCode(code);
        entity.setStatus(status);
        entity.setVersion(1);
        return entity;
    }

    @Test
    void rerunExecutesTheHandlerAndRecordsSuccess() {
        when(registry.findByCode("batS01")).thenReturn(definition("batS01", BatchTaskType.S, true));
        when(executionMapper.findRunningByBatchCode("batS01")).thenReturn(null);

        Map<String, Object> result = service.rerun("batS01", "admin");

        // ハンドラが実行され、戻り値が履歴のメッセージになる
        assertThat(handler.calls).isEqualTo(1);
        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("status", "SUCCESS");
        assertThat(result).containsEntry("message", "Proxy started on port 7777");

        // 実行中として記録してから正常終了に更新する
        ArgumentCaptor<BatchExecutionEntity> inserted = ArgumentCaptor.forClass(BatchExecutionEntity.class);
        verify(executionMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getBatchCode()).isEqualTo("batS01");
        assertThat(inserted.getValue().getBatchType()).isEqualTo("S");
        assertThat(inserted.getValue().getTriggerType()).isEqualTo("C");
        assertThat(inserted.getValue().getStatus()).isEqualTo("RUNNING");
        assertThat(inserted.getValue().getStartTime()).isNotNull();
        assertThat(inserted.getValue().getRequestedByCode()).isEqualTo("admin");

        verify(executionMapper).markFinished(eq(777L), eq("SUCCESS"), eq("Proxy started on port 7777"),
                eq(null), anyLong());
        // 最終実行日時が更新される
        verify(controlMapper).touchLastRunAt("batS01");
    }

    @Test
    void rerunFallsBackToThePageCodeWhenOperatorIsMissing() {
        when(registry.findByCode("batS01")).thenReturn(definition("batS01", BatchTaskType.S, true));
        when(executionMapper.findRunningByBatchCode("batS01")).thenReturn(null);

        service.rerun("batS01", "   ");

        ArgumentCaptor<BatchExecutionEntity> inserted = ArgumentCaptor.forClass(BatchExecutionEntity.class);
        verify(executionMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getRequestedByCode()).isEqualTo("batch-page");
    }

    @Test
    void rerunIsRejectedWhenTheBatchHasNoHandlerYet() {
        // 2.1 では batS01 以外の業務処理は未移植（ハンドラが無い）
        when(registry.findByCode("batR02")).thenReturn(definition("batR02", BatchTaskType.R, false));

        assertThatThrownBy(() -> service.rerun("batR02", "admin"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("2.1 では未実装です");

        // 実行記録も残さない
        verify(executionMapper, never()).insert(any(BatchExecutionEntity.class));
    }

    @Test
    void rerunIsRejectedForCallBatchesEvenWhenTheHandlerExists() {
        // 種別 C（呼出）は他の処理から呼ばれるバッチなので、画面の【再実行】では起動しない
        // （業務処理＝ハンドラがあっても同じ。利用者の指示）
        RecordingHandler callHandler = new RecordingHandler("batC52");
        BatchServiceImpl callService = new BatchServiceImpl(registry, mock(SettingsService.class),
                executionMapper, controlMapper, mock(AiCallLogMapper.class), List.of(callHandler));
        when(registry.findByCode("batC52")).thenReturn(definition("batC52", BatchTaskType.C, false));

        assertThatThrownBy(() -> callService.rerun("batC52", "admin"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("画面から実行できません");

        assertThat(callHandler.calls).isZero();
        verify(executionMapper, never()).insert(any(BatchExecutionEntity.class));
    }

    @Test
    void callBatchesCanStillBeStartedByOtherProcessing() {
        // 止めるのは「画面からの入口」だけ。他の処理（AI 生図の流水線・授業ノートなど）からの
        // 呼出（rerunStep）は今までどおり動く
        RecordingHandler callHandler = new RecordingHandler("batC52");
        BatchServiceImpl callService = new BatchServiceImpl(registry, mock(SettingsService.class),
                executionMapper, controlMapper, mock(AiCallLogMapper.class), List.of(callHandler));
        when(registry.findByCode("batC52")).thenReturn(definition("batC52", BatchTaskType.C, false));
        when(executionMapper.findRunningByBatchCode("batC52")).thenReturn(null);

        Map<String, Object> result = callService.rerunStep("batC52", "APP", "{\"assistId\":12}");

        assertThat(callHandler.calls).isEqualTo(1);
        assertThat(result).containsEntry("success", true);
    }

    @Test
    void listTasksDoesNotOfferRerunForCallBatches() {
        // 一覧の行が返す 2 つのフラグの意味:
        //   canManualRerun = 画面の【再実行】ボタンを出すか（種別 C は出さない）
        //   canRerun       = そのボタンを押せるか（ハンドラ未実装は押せない）
        RecordingHandler callHandler = new RecordingHandler("batC52");
        BatchServiceImpl listService = new BatchServiceImpl(registry, mock(SettingsService.class),
                executionMapper, controlMapper, mock(AiCallLogMapper.class), List.of(handler, callHandler));
        when(registry.findAll()).thenReturn(List.of(
                definition("batS01", BatchTaskType.S, true),
                definition("batC52", BatchTaskType.C, false),
                definition("batR02", BatchTaskType.R, false)));
        when(controlMapper.findAll()).thenReturn(List.of());
        when(executionMapper.findLatestPerBatch()).thenReturn(List.of());

        assertThat(rowOf(listService, "batS01"))
                .containsEntry("canManualRerun", true)
                .containsEntry("canRerun", true);
        // 種別 C はハンドラがあってもボタンを出さない（押せる扱いにもしない）
        assertThat(rowOf(listService, "batC52"))
                .containsEntry("canManualRerun", false)
                .containsEntry("canRerun", false);
        // 種別 R はハンドラが無いので、ボタンは出すが押せない（2.1 では未実装）
        assertThat(rowOf(listService, "batR02"))
                .containsEntry("canManualRerun", true)
                .containsEntry("canRerun", false);
    }

    @Test
    void rerunIsRejectedWhileTheSameBatchIsRunning() {
        when(registry.findByCode("batS01")).thenReturn(definition("batS01", BatchTaskType.S, true));
        BatchExecutionEntity running = new BatchExecutionEntity();
        running.setExecutionId(500L);
        when(executionMapper.findRunningByBatchCode("batS01")).thenReturn(running);

        assertThatThrownBy(() -> service.rerun("batS01", "admin"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("既に実行中です")
                .hasMessageContaining("500");

        assertThat(handler.calls).isZero();
        verify(executionMapper, never()).insert(any(BatchExecutionEntity.class));
    }

    @Test
    void rerunWorksEvenWhenTheBatchIsDisabled() {
        // 無効 = 定時実行しない、の意味。手動の再実行は許可する（2.0 と同じ運用）
        when(registry.findByCode("batS01")).thenReturn(definition("batS01", BatchTaskType.S, false));
        when(controlMapper.findByBatchCode("batS01")).thenReturn(control("batS01", "0"));
        when(executionMapper.findRunningByBatchCode("batS01")).thenReturn(null);

        Map<String, Object> result = service.rerun("batS01", "admin");

        assertThat(handler.calls).isEqualTo(1);
        assertThat(result).containsEntry("success", true);
    }

    @Test
    void rerunRecordsFailureWithoutThrowing() {
        when(registry.findByCode("batS01")).thenReturn(definition("batS01", BatchTaskType.S, true));
        when(executionMapper.findRunningByBatchCode("batS01")).thenReturn(null);
        handler.failure = new IllegalStateException("プロキシサーバーの起動に失敗しました。");

        Map<String, Object> result = service.rerun("batS01", "admin");

        assertThat(result).containsEntry("success", false);
        assertThat(result).containsEntry("status", "FAILED");
        assertThat(result).containsEntry("message", "プロキシサーバーの起動に失敗しました。");
        assertThat((String) result.get("errorDetail")).contains("IllegalStateException");
        verify(executionMapper).markFinished(eq(777L), eq("FAILED"), anyString(), anyString(), anyLong());
        // 失敗しても最終実行日時は更新する（最後に動いた時刻として）
        verify(controlMapper).touchLastRunAt("batS01");
    }

    @Test
    void startupTargetsAreOnlyEnabledSystemBatches() {
        when(registry.findAll()).thenReturn(List.of(
                definition("batS01", BatchTaskType.S, true),
                definition("batR05", BatchTaskType.R, true),
                definition("batC04", BatchTaskType.C, true)));

        when(controlMapper.findByBatchCode("batS01")).thenReturn(control("batS01", "0"));
        assertThat(service.startupTargets()).isEmpty();

        when(controlMapper.findByBatchCode("batS01")).thenReturn(control("batS01", "1"));
        assertThat(service.startupTargets()).containsExactly("batS01");

        // コントロール表に行が無ければ定義の既定値（有効）を使う
        when(controlMapper.findByBatchCode("batS01")).thenReturn(null);
        assertThat(service.startupTargets()).containsExactly("batS01");
    }

    @Test
    void startupRunIsRecordedAsSystemTriggered() {
        when(registry.findByCode("batS01")).thenReturn(definition("batS01", BatchTaskType.S, true));
        when(executionMapper.findRunningByBatchCode("batS01")).thenReturn(null);

        service.runOnStartup("batS01");

        ArgumentCaptor<BatchExecutionEntity> inserted = ArgumentCaptor.forClass(BatchExecutionEntity.class);
        verify(executionMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getTriggerType()).isEqualTo("S");
        assertThat(inserted.getValue().getRequestedByCode()).isEqualTo("STARTUP");
    }

    @Test
    void historyIsPagedAndCounted() {
        when(executionMapper.countHistory(null, "FAILED", "proxy")).thenReturn(45L);
        when(executionMapper.searchHistory(eq(null), eq("FAILED"), eq("proxy"), eq(20), eq(20)))
                .thenReturn(List.of(new BatchExecutionEntity()));

        Map<String, Object> result = service.history(null, "FAILED", "proxy", 2, 20);

        assertThat(result).containsEntry("totalElements", 45L);
        assertThat(result).containsEntry("page", 2);
        assertThat(result).containsEntry("size", 20);
        assertThat(result).containsEntry("totalPages", 3);
    }

    @Test
    void historyClampsPageAndSizeAndBlankFilters() {
        when(executionMapper.countHistory(null, null, null)).thenReturn(0L);
        when(executionMapper.searchHistory(null, null, null, 100, 0)).thenReturn(List.of());

        Map<String, Object> result = service.history("  ", "", null, -5, 500);

        assertThat(result).containsEntry("page", 1);
        assertThat(result).containsEntry("size", 100);
        verify(executionMapper, times(1)).searchHistory(null, null, null, 100, 0);
    }
}
