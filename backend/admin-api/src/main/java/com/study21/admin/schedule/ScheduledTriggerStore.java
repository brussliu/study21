package com.study21.admin.schedule;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchExecutionMapper;
import com.study21.admin.batch.BatchExecutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 計画実行点の**確保（claim）と実行記録**を 1 つのトランザクションで行う。
 *
 * <p>「同じ計画実行点を 2 回実行しない」ための仕組みは 2 段:</p>
 * <ol>
 *   <li>メモリの早見（{@code lastClaimed}）。同じ点を何度も検査しても DB を叩かない
 *       （30 秒ごとの検査で毎回 UPDATE を出さないため）。</li>
 *   <li>DB の原子的な確保（{@link SchedulePlanMapper#claim}）。**こちらが正**で、
 *       再起動・多重起動・ポーリングのゆらぎでも 1 回だけになる。</li>
 * </ol>
 *
 * <p>確保と実行記録は**同じトランザクション**で行う。途中で落ちれば両方巻き戻り、
 * その計画実行点は次の検査で再び確保を試みる（実行記録だけ残って実行されない状態を作らない）。</p>
 */
@Component
public class ScheduledTriggerStore {

    /** スケジューラが実行記録に残す依頼元コード。 */
    public static final String SCHEDULER_CODE = "SCHEDULER";

    private static final DateTimeFormatter PAYLOAD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final Logger log = LoggerFactory.getLogger(ScheduledTriggerStore.class);

    private final SchedulePlanMapper planMapper;
    private final BatchExecutionMapper executionMapper;
    private final ScheduleRuleCatalog catalog;

    /** メモリの早見（DB の値が正。ここは「DB を叩く回数を減らす」ためだけに使う）。 */
    private final Map<String, LocalDateTime> lastClaimed = new ConcurrentHashMap<>();

    public ScheduledTriggerStore(SchedulePlanMapper planMapper,
                                 BatchExecutionMapper executionMapper,
                                 ScheduleRuleCatalog catalog) {
        this.planMapper = planMapper;
        this.executionMapper = executionMapper;
        this.catalog = catalog;
    }

    /**
     * 起動時に計画状態を 1 回だけ読む（4 タスクぶんを 1 クエリ）。
     * 読めなくても致命的ではない（確保は DB が正なので、そのまま動く）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadPlansOnStartup() {
        try {
            for (Map<String, Object> row : planMapper.findPlans(catalog.taskCodes())) {
                Object code = row.get("batchCode");
                Object planned = row.get("lastPlannedAt");
                if (code != null && planned instanceof java.sql.Timestamp timestamp) {
                    lastClaimed.put(String.valueOf(code), timestamp.toLocalDateTime());
                }
            }
            log.info("バッチの計画状態を読み込みました。{}", lastClaimed);
        } catch (RuntimeException cause) {
            log.warn("バッチの計画状態を読み込めませんでした（確保のたびに DB を見ます）。reason={}",
                    cause.getMessage());
        }
    }

    /**
     * 計画実行点を確保して実行記録を作る（**1 トランザクション**）。
     *
     * @return 確保できたときの実行ID。既に確保済み（他の実行・過去の実行）なら null
     */
    @Transactional
    public Long claimAndRecord(String taskCode, LocalDateTime plannedAt, String batchType) {
        String claimed = planMapper.claim(taskCode, plannedAt.format(PAYLOAD_FORMAT));
        if (claimed == null) {
            return null;
        }
        lastClaimed.put(taskCode, plannedAt);

        BatchExecutionEntity record = new BatchExecutionEntity();
        record.setBatchCode(taskCode);
        record.setBatchType(batchType);
        record.setTriggerType(batchType);
        record.setStatus(BatchExecutionStatus.QUEUED.name());
        record.setRequestedByCode(SCHEDULER_CODE);
        record.setScheduleTime(plannedAt.format(PAYLOAD_FORMAT));
        record.setMessage("スケジュール実行（予定 " + plannedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "）");
        executionMapper.insert(record);
        planMapper.attachExecution(taskCode, record.getExecutionId());
        log.info("バッチの計画実行点を確保しました。taskCode={} plannedAt={} executionId={}",
                taskCode, plannedAt, record.getExecutionId());
        return record.getExecutionId();
    }

    /**
     * 計画実行点を**実行せずに**進める（タスクが無効のとき）。
     *
     * <p>無効の間に計画を進めておかないと、有効に戻した直後に古い計画実行点を
     * 「取りこぼしの補償」として実行してしまう（利用者の指示: 設定変更で過去の補償を起こさない）。</p>
     *
     * @return 進めたら true（既に同じか新しい点を確保済みなら false）
     */
    @Transactional
    public boolean advanceWithoutRun(String taskCode, LocalDateTime plannedAt) {
        LocalDateTime known = lastClaimed.get(taskCode);
        if (known != null && !plannedAt.isAfter(known)) {
            return false;
        }
        String claimed = planMapper.claim(taskCode, plannedAt.format(PAYLOAD_FORMAT));
        if (claimed != null) {
            lastClaimed.put(taskCode, plannedAt);
            return true;
        }
        return false;
    }

    /** 同じ計画実行点を既に確保済みか（DB を叩かない早見）。 */
    public boolean alreadyClaimed(String taskCode, LocalDateTime plannedAt) {
        LocalDateTime known = lastClaimed.get(taskCode);
        return known != null && !plannedAt.isAfter(known);
    }

    /** 再起動で計画状態が変わった可能性があるとき（管理者の再読み込み）に読み直す。 */
    public void reloadPlans() {
        lastClaimed.clear();
        loadPlansOnStartup();
    }

    /** テスト用: メモリの早見を初期化する。 */
    void resetMemory() {
        lastClaimed.clear();
    }

    /** 対象タスクの最後に確保した計画実行点（監視・表示用）。 */
    public LocalDateTime lastClaimedAt(String taskCode) {
        return lastClaimed.get(taskCode);
    }

    /** 実行中（QUEUED/RUNNING）の他の実行があるか。 */
    public boolean isOtherExecutionRunning(String taskCode, long executionId) {
        return executionMapper.findRunningByBatchCodeExcept(taskCode, executionId) != null;
    }

    /** 実行記録（スキップ理由を書くために読む）。 */
    public List<BatchExecutionEntity> recent(String taskCode, int limit) {
        return executionMapper.findRecent(taskCode, limit);
    }
}
