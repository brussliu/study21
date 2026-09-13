package com.study21.admin.batch;

import com.study21.admin.setting.SettingRequirement;
import com.study21.admin.setting.SettingsService;
import com.study21.admin.setting.SettingsValidationException;
import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * バッチ管理サービス実装。
 *
 * <p>設定検証は {@link SettingsService#requireSettings} に集約し、既定値・フォールバックを持たない。
 * 再実行は設定不足を検出した時点で拒否する（フロント側の無効化だけに依存しない）。</p>
 *
 * <p>実行の入口は 2 つだけ:
 * <ol>
 *   <li>admin-api の起動時（種別 S かつ有効なバッチ。{@link BatchStartupRunner} から呼ぶ）</li>
 *   <li>バッチ管理画面の【再実行】（{@link #rerun}）</li>
 * </ol>
 * 定時・循環のスケジューラは 2.1 では未実装（2.0 の batL01 が持っていた 6 時間ごとの実行は
 * 廃止し、起動時と再実行に限った）。</p>
 */
@Service
public class BatchServiceImpl implements BatchService {

    private static final Logger log = LoggerFactory.getLogger(BatchServiceImpl.class);

    /** 2.0 と同じ有効設定の備考文言。 */
    private static final String ACTIVE_NOTE = "バッチ管理画面の有効設定（OFF時は定時実行しない）";

    /** 画面から起動したときの 依頼元コード（ログイン中の管理者が特定できない場合の識別子）。 */
    private static final String OPERATOR_FALLBACK = "batch-page";

    /** admin-api の起動時に実行したことを示す 依頼元コード。 */
    private static final String STARTUP_CODE = "STARTUP";

    /** エラー詳細に残す最大文字数（スタックトレースが長くなりすぎないように）。 */
    private static final int ERROR_DETAIL_LIMIT = 4000;

    /** AI 呼び出し履歴の詳細で返すプロンプト・レスポンスの上限文字数（1 行が 200KB 超になる）。 */
    private static final int AI_BODY_LIMIT = 200_000;

    private final BatchTaskRegistry registry;
    private final SettingsService settingsService;
    private final BatchExecutionMapper executionMapper;
    private final BatchControlMapper controlMapper;
    /** AI 呼び出し履歴（2.0 から移行した BAT_AI呼出履歴情報）。参照のみ。 */
    private final AiCallLogMapper aiCallLogMapper;
    private final Map<String, BatchTaskHandler> handlers;
    private final ConcurrentHashMap<String, Boolean> runningGuard = new ConcurrentHashMap<>();

    public BatchServiceImpl(BatchTaskRegistry registry,
                            SettingsService settingsService,
                            BatchExecutionMapper executionMapper,
                            BatchControlMapper controlMapper,
                            AiCallLogMapper aiCallLogMapper,
                            List<BatchTaskHandler> taskHandlers) {
        this.registry = registry;
        this.settingsService = settingsService;
        this.executionMapper = executionMapper;
        this.controlMapper = controlMapper;
        this.aiCallLogMapper = aiCallLogMapper;
        this.handlers = taskHandlers == null
                ? Map.of()
                : taskHandlers.stream().collect(Collectors.toMap(BatchTaskHandler::taskCode, Function.identity(),
                        (a, b) -> a));
    }

    @Override
    public Map<String, Object> listTasks() {
        ensureControls();
        Map<String, BatchControlEntity> controls = new LinkedHashMap<>();
        for (BatchControlEntity control : controlMapper.findAll()) {
            controls.put(control.getBatchCode(), control);
        }
        List<BatchExecutionEntity> latestByBatch = executionMapper.findLatestPerBatch();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (BatchTaskDefinition task : registry.findAll()) {
            rows.add(toRow(task, controls.get(task.taskCode()), latestByBatch));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("totalCount", rows.size());
        // 起動時に実行されるバッチ（画面に「起動時に実行」と出すため）
        result.put("startupTargets", startupTargets());
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> rerun(String batchCode, String operator) {
        return execute(batchCode, "C", normalize(operator), "画面から再実行しました");
    }

    @Override
    public List<String> startupTargets() {
        ensureControls();
        List<String> codes = new ArrayList<>();
        for (BatchTaskDefinition task : registry.findAll()) {
            if (task.taskType() != BatchTaskType.S) {
                continue;
            }
            BatchControlEntity control = controlMapper.findByBatchCode(task.taskCode());
            boolean active = control == null ? task.active() : control.isActive();
            if (active) {
                codes.add(task.taskCode());
            }
        }
        return codes;
    }

    @Override
    @Transactional
    public Map<String, Object> runOnStartup(String batchCode) {
        return execute(batchCode, "S", STARTUP_CODE, "admin-api の起動時に実行しました");
    }

    /**
     * 実行の共通処理: 定義確認 → 設定検証 → 二重起動チェック → 実行 → 履歴の更新。
     */
    private Map<String, Object> execute(String batchCode, String triggerType, String requestedByCode, String reason) {
        BatchTaskDefinition task = registry.findByCode(batchCode);
        if (task == null) {
            throw new NotFoundException("バッチタスクが見つかりません: " + batchCode);
        }
        BatchTaskHandler handler = handlers.get(batchCode);
        if (handler == null) {
            throw new ValidationException("このバッチは 2.1 では未実装です（業務処理のハンドラがありません）: " + batchCode);
        }

        // 1) 設定検証（不足・不正があればここで拒否。実行記録も残さない）
        settingsService.requireSettings(batchCode, task.requiredSettings());

        // 2) 二重起動ガード（有効／無効は実行の可否に影響しない）
        if (runningGuard.putIfAbsent(batchCode, Boolean.TRUE) != null) {
            throw new ConflictException("バッチは既に実行中です: " + batchCode);
        }
        BatchExecutionEntity running = executionMapper.findRunningByBatchCode(batchCode);
        if (running != null) {
            runningGuard.remove(batchCode);
            throw new ConflictException("バッチは既に実行中です: " + batchCode + "（実行ID=" + running.getExecutionId() + "）");
        }

        try {
            BatchExecutionEntity record = new BatchExecutionEntity();
            record.setBatchCode(batchCode);
            record.setBatchType(task.taskType().name());
            record.setTriggerType(triggerType);
            record.setStatus(BatchExecutionStatus.RUNNING.name());
            record.setRequestedByCode(requestedByCode);
            record.setStartTime(LocalDateTime.now().toString());
            record.setMessage(reason);
            executionMapper.insert(record);

            long startedAt = System.currentTimeMillis();
            try {
                String summary = handler.execute(record);
                long durationMs = System.currentTimeMillis() - startedAt;
                String message = summary == null || summary.isBlank() ? "正常に終了しました。" : summary;
                executionMapper.markFinished(record.getExecutionId(), BatchExecutionStatus.SUCCESS.name(),
                        message, null, durationMs);
                controlMapper.touchLastRunAt(batchCode);
                log.info("Batch finished. batchCode={} executionId={} trigger={} durationMs={} message={}",
                        batchCode, record.getExecutionId(), triggerType, durationMs, message);
                return result(true, record.getExecutionId(), BatchExecutionStatus.SUCCESS, message, null);
            } catch (Exception e) {
                long durationMs = System.currentTimeMillis() - startedAt;
                String detail = stackTraceOf(e);
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                executionMapper.markFinished(record.getExecutionId(), BatchExecutionStatus.FAILED.name(),
                        "異常終了しました。", detail, durationMs);
                controlMapper.touchLastRunAt(batchCode);
                log.error("Batch failed. batchCode={} executionId={} trigger={} durationMs={}",
                        batchCode, record.getExecutionId(), triggerType, durationMs, e);
                return result(false, record.getExecutionId(), BatchExecutionStatus.FAILED, message, detail);
            }
        } finally {
            runningGuard.remove(batchCode);
        }
    }

    @Override
    public Map<String, Object> history(String batchCode, String status, String keyword, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(1, page);
        String code = blankToNull(batchCode);
        String state = blankToNull(status);
        String search = blankToNull(keyword);

        long total = executionMapper.countHistory(code, state, search);
        List<BatchExecutionEntity> items = executionMapper.searchHistory(code, state, search,
                safeSize, (safePage - 1) * safeSize);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("totalElements", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("totalPages", safeSize == 0 ? 0 : (int) Math.ceil((double) total / safeSize));
        return result;
    }

    @Override
    public Map<String, Object> aiCalls(String batchCode, String aiType, String result, String keyword,
                                       String startFrom, String startTo, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(1, page);
        String code = blankToNull(batchCode);
        String type = blankToNull(aiType);
        String state = normalizeResult(result);
        String search = blankToNull(keyword);
        String from = blankToNull(startFrom);
        String to = blankToNull(startTo);

        long total = aiCallLogMapper.countCalls(code, type, state, search, from, to);
        List<Map<String, Object>> items = aiCallLogMapper.searchCalls(code, type, state, search, from, to,
                        safeSize, (safePage - 1) * safeSize)
                .stream()
                .map(BatchServiceImpl::toAiCallRow)
                .toList();

        Map<String, Object> result2 = new LinkedHashMap<>();
        result2.put("items", items);
        result2.put("totalElements", total);
        result2.put("page", safePage);
        result2.put("size", safeSize);
        result2.put("totalPages", safeSize == 0 ? 0 : (int) Math.ceil((double) total / safeSize));
        return result2;
    }

    @Override
    public Map<String, Object> aiCallDetail(long callId) {
        AiCallLogEntity entity = aiCallLogMapper.findById(callId);
        if (entity == null) {
            throw new NotFoundException("AI呼出履歴が見つかりません。");
        }
        Map<String, Object> row = toAiCallRow(entity);
        row.put("prompt", truncate(entity.getPrompt()));
        row.put("response", truncate(entity.getResponse()));
        row.put("bodyTruncated", isTruncated(entity.getPrompt()) || isTruncated(entity.getResponse()));
        row.put("bodyLimit", AI_BODY_LIMIT);
        return row;
    }

    @Override
    public Map<String, Object> aiCallFilters() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aiTypes", aiCallLogMapper.findDistinctAiTypes());
        result.put("batchCodes", aiCallLogMapper.findDistinctBatchCodes());
        result.put("models", aiCallLogMapper.findDistinctModels());
        result.put("results", List.of("SUCCESS", "FAILURE"));
        return result;
    }

    /** 一覧・詳細で共通の 1 行（本文は呼び出し側で足す）。 */
    private static Map<String, Object> toAiCallRow(AiCallLogEntity entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("callId", entity.getCallId());
        row.put("executionId", entity.getExecutionId());
        row.put("batchCode", entity.getBatchCode());
        row.put("processKey", entity.getProcessKey());
        row.put("language", entity.getLanguage());
        row.put("aiType", entity.getAiType());
        row.put("modelName", entity.getModelName());
        row.put("callUrl", entity.getCallUrl());
        row.put("httpStatus", entity.getHttpStatus());
        row.put("startTime", entity.getStartTime());
        row.put("endTime", entity.getEndTime());
        row.put("durationMs", entity.getDurationMs());
        row.put("result", entity.getResult());
        row.put("resultLabel", "SUCCESS".equals(entity.getResult()) ? "成功" : "失敗");
        row.put("errorCode", entity.getErrorCode());
        row.put("errorMessage", entity.getErrorMessage());
        row.put("inputTokens", entity.getInputTokens());
        row.put("outputTokens", entity.getOutputTokens());
        row.put("totalTokens", entity.getTotalTokens());
        return row;
    }

    /** 結果の絞り込み値（SUCCESS / FAILURE）。それ以外は 400。 */
    private String normalizeResult(String result) {
        String value = blankToNull(result);
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase();
        if (!"SUCCESS".equals(upper) && !"FAILURE".equals(upper)) {
            throw new ValidationException("結果は SUCCESS / FAILURE のいずれかを指定してください。");
        }
        return upper;
    }

    private String truncate(String body) {
        if (body == null) {
            return null;
        }
        return body.length() <= AI_BODY_LIMIT ? body : body.substring(0, AI_BODY_LIMIT);
    }

    private boolean isTruncated(String body) {
        return body != null && body.length() > AI_BODY_LIMIT;
    }

    private Map<String, Object> result(boolean success, Long executionId, BatchExecutionStatus status,
                                       String message, String errorDetail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("executionId", executionId);
        result.put("status", status.name());
        result.put("statusLabel", status.label());
        result.put("message", message);
        result.put("errorDetail", errorDetail);
        return result;
    }

    private Map<String, Object> toRow(BatchTaskDefinition task,
                                      BatchControlEntity control,
                                      List<BatchExecutionEntity> latestByBatch) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskCode", task.taskCode());
        row.put("taskType", task.taskType().name());
        row.put("description", task.description());
        // 有効／無効は BAT_バッチコントロール情報 を正とする（行が無ければ定義の既定値）
        row.put("active", control == null ? task.active() : control.isActive());
        row.put("activeVersion", control == null ? null : control.getVersion());
        row.put("lastRunAt", control == null ? null : control.getLastRunAt());
        row.put("canToggleActive", task.canToggleActive());
        row.put("canRerun", handlers.containsKey(task.taskCode()));
        row.put("runsOnStartup", task.taskType() == BatchTaskType.S);
        row.put("loopEveryMinutes", task.loopEveryMinutes());
        row.put("minuteOfHour", task.minuteOfHour());
        row.put("pageCode", task.pageCode());

        // 設定充足状態（不足なら missingSettings に一覧）
        List<SettingRequirement> requirements = task.requiredSettings();
        row.put("requiredSettings", requirements.stream().map(SettingRequirement::settingKey).toList());
        boolean complete = true;
        List<String> missing = new ArrayList<>();
        if (!requirements.isEmpty()) {
            try {
                settingsService.requireSettings(task.taskCode(), requirements);
            } catch (SettingsValidationException e) {
                complete = false;
                missing.addAll(e.getDetails());
            }
        }
        row.put("settingsComplete", complete);
        row.put("missingSettings", missing);

        // 最新実行状態（バッチごとの最新 1 件をまとめて引く）
        BatchExecutionEntity latest = latestByBatch.stream()
                .filter(execution -> task.taskCode().equals(execution.getBatchCode()))
                .findFirst()
                .orElse(null);
        row.put("latestStatus", latest == null ? null : latest.getStatus());
        row.put("latestStartTime", latest == null ? null : latest.getStartTime());
        row.put("latestEndTime", latest == null ? null : latest.getEndTime());
        row.put("latestMessage", latest == null ? null : latest.getMessage());
        row.put("running", latest != null
                && (BatchExecutionStatus.QUEUED.name().equals(latest.getStatus())
                    || BatchExecutionStatus.RUNNING.name().equals(latest.getStatus())));
        return row;
    }

    @Override
    @Transactional
    public Map<String, Object> updateActive(String batchCode, boolean active, String operator) {
        BatchTaskDefinition task = registry.findByCode(batchCode);
        if (task == null) {
            throw new NotFoundException("バッチタスクが見つかりません: " + batchCode);
        }
        if (!task.canToggleActive()) {
            // 2.0 のメッセージを引き継ぎつつ、2.1 で追加した batS を足す
            throw new ValidationException("有効設定を変更できるのは batS / batL / batR のみです。");
        }
        ensureControls();
        BatchControlEntity control = controlMapper.findByBatchCode(batchCode);
        String operatorCode = normalize(operator);
        int updated = controlMapper.updateStatus(batchCode, active ? "1" : "0",
                control == null ? null : control.getVersion(), null, operatorCode);
        if (updated == 0) {
            throw new ConflictException("他の管理者が先に更新しました。再読み込みしてください。");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("batchCode", batchCode);
        result.put("active", active);
        result.put("message", "バッチの有効設定を更新しました。");
        return result;
    }

    /** 定義にある（切り替え可能な）バッチの行を BAT_バッチコントロール情報 に用意する。 */
    private void ensureControls() {
        for (BatchTaskDefinition task : registry.findAll()) {
            if (!task.canToggleActive()) {
                continue;
            }
            BatchControlEntity entity = new BatchControlEntity();
            entity.setBatchCode(task.taskCode());
            entity.setStatus(task.active() ? "1" : "0");
            entity.setNote(ACTIVE_NOTE);
            entity.setCreatedByCode("SYSTEM");
            entity.setUpdatedByCode("SYSTEM");
            controlMapper.insertIfAbsent(entity);
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? OPERATOR_FALLBACK : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String stackTraceOf(Exception e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        String text = writer.toString();
        return text.length() <= ERROR_DETAIL_LIMIT ? text : text.substring(0, ERROR_DETAIL_LIMIT);
    }
}
