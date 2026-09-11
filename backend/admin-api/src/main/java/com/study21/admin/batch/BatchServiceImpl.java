package com.study21.admin.batch;

import com.study21.admin.setting.SettingRequirement;
import com.study21.admin.setting.SettingsService;
import com.study21.admin.setting.SettingsValidationException;
import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * バッチ管理サービス実装。
 *
 * <p>設定検証は {@link SettingsService#requireSettings} に集約し、既定値・フォールバックを持たない。
 * 手動実行は設定不足を検出した時点で拒否する（フロント側の無効化だけに依存しない）。</p>
 *
 * <p>注意: 各タスクの業務処理（ハンドラ）は本セッションでは未移植。フレームワーク（設定検証・
 * 並列実行ガード・実行記録）のみを提供する。業務ハンドラ実装は {@link BatchTaskHandler} を参照。</p>
 */
@Service
public class BatchServiceImpl implements BatchService {

    private static final Logger log = LoggerFactory.getLogger(BatchServiceImpl.class);

    private final BatchTaskRegistry registry;
    private final SettingsService settingsService;
    private final BatchExecutionMapper executionMapper;
    private final ConcurrentHashMap<String, Boolean> runningGuard = new ConcurrentHashMap<>();

    public BatchServiceImpl(BatchTaskRegistry registry,
                            SettingsService settingsService,
                            BatchExecutionMapper executionMapper) {
        this.registry = registry;
        this.settingsService = settingsService;
        this.executionMapper = executionMapper;
    }

    @Override
    public Map<String, Object> listTasks() {
        List<BatchTaskDefinition> tasks = registry.findAll();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BatchTaskDefinition task : tasks) {
            rows.add(toRow(task));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("totalCount", rows.size());
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> trigger(String taskCode, String requestedBy) {
        BatchTaskDefinition task = registry.findByCode(taskCode);
        if (task == null) {
            throw new NotFoundException("バッチタスクが見つかりません: " + taskCode);
        }
        if (!task.canManualReRun()) {
            throw new ConflictException("このタスクは手動実行できません: " + taskCode);
        }

        // 1) 設定検証（不足・不正があればここで拒否）
        Map<String, String> settings = settingsService.requireSettings(taskCode, task.requiredSettings());

        // 2) 並列実行ガード
        if (runningGuard.putIfAbsent(taskCode, Boolean.TRUE) != null) {
            throw new ConflictException("タスクは既に実行中です: " + taskCode);
        }
        BatchExecutionEntity running = executionMapper.findRunningByTaskCode(taskCode);
        if (running != null) {
            runningGuard.remove(taskCode);
            throw new ConflictException("タスクは既に実行中です: " + taskCode + "（実行ID=" + running.getExecutionId() + "）");
        }

        try {
            BatchExecutionEntity record = new BatchExecutionEntity();
            record.setTaskCode(taskCode);
            record.setStatus(BatchExecutionStatus.QUEUED.name());
            record.setRequestedBy(normalize(requestedBy));
            record.setMessage("手動実行を受け付けました（設定検証済み）");
            executionMapper.insert(record);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("taskCode", taskCode);
            result.put("executionId", record.getExecutionId());
            result.put("status", BatchExecutionStatus.QUEUED.name());
            result.put("statusLabel", BatchExecutionStatus.QUEUED.label());
            result.put("queued", true);
            result.put("settingsResolved", settings.size());
            return result;
        } finally {
            runningGuard.remove(taskCode);
        }
    }

    @Override
    public List<BatchExecutionEntity> recentExecutions(String taskCode, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return executionMapper.findRecent(taskCode, safeLimit);
    }

    private Map<String, Object> toRow(BatchTaskDefinition task) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskCode", task.taskCode());
        row.put("taskType", task.taskType().name());
        row.put("description", task.description());
        row.put("active", task.active());
        row.put("loopEveryMinutes", task.loopEveryMinutes());
        row.put("minuteOfHour", task.minuteOfHour());
        row.put("allowConcurrent", task.allowConcurrent());
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

        // 最新実行状態
        BatchExecutionEntity latest = executionMapper.findRecent(task.taskCode(), 1)
                .stream().findFirst().orElse(null);
        row.put("latestStatus", latest == null ? null : latest.getStatus());
        row.put("latestStartTime", latest == null ? null : latest.getStartTime());
        row.put("latestEndTime", latest == null ? null : latest.getEndTime());
        row.put("latestMessage", latest == null ? null : latest.getMessage());
        row.put("running", latest != null
                && (BatchExecutionStatus.QUEUED.name().equals(latest.getStatus())
                    || BatchExecutionStatus.RUNNING.name().equals(latest.getStatus())));
        return row;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "batch-page" : value.trim();
    }
}
