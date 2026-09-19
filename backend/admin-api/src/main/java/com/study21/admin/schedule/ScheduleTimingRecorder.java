package com.study21.admin.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * **実行設定の変更**（適用時刻＋計画バージョン）を記録する。
 *
 * <p>利用者が実行時刻・間隔・ずらし・有効／無効を変えたとき、
 * {@code BAT_スケジュール状態情報.設定適用日時} と {@code 設定版} を進める。
 * この時刻より前の計画実行点は実行しない（設定を変えた直後に過去の点を今さら実行しない）。</p>
 *
 * <p><b>必ず「設定値を保存するトランザクションの中」で呼ぶ。</b>そうしないと、
 * ・設定値だけコミットされて適用時刻が書かれていない
 * ・適用時刻だけ書かれて設定値がロールバックした
 * という食い違いが残り、再起動後に**新しい設定＋古い適用時刻**で過去の計画実行点を
 * 実行してしまう（＝漏れではなく誤実行）。同じトランザクションなら、どちらか一方だけが
 * 残ることはない。</p>
 *
 * <p>呼び出し元は 3 つ（入口はすべてここを通る）:</p>
 * <ol>
 *   <li>設定ページの保存（{@code SettingsServiceImpl} → {@link ScheduleSettingSaveHook}）</li>
 *   <li>バッチの有効／無効の切替（{@code BatchServiceImpl#updateActive}）</li>
 *   <li>（将来）実行設定を変える入口を足すとき</li>
 * </ol>
 */
@Component
public class ScheduleTimingRecorder {

    private static final Logger log = LoggerFactory.getLogger(ScheduleTimingRecorder.class);

    private final SchedulePlanMapper planMapper;
    private final ScheduleRuleCatalog catalog;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ScheduleTimingRecorder(SchedulePlanMapper planMapper, ScheduleRuleCatalog catalog) {
        this(planMapper, catalog, Clock.system(ScheduleConfigService.ZONE));
    }

    /** テスト用（時計を差し替える）。 */
    public ScheduleTimingRecorder(SchedulePlanMapper planMapper, ScheduleRuleCatalog catalog, Clock clock) {
        this.planMapper = planMapper;
        this.catalog = catalog;
        this.clock = clock;
    }

    /**
     * 変更された設定（ページ区分 → 設定キー → 新しい値）から、影響するタスクの計画バージョンを進める。
     *
     * <p>設定値の保存と**同じトランザクション**で呼ぶこと。</p>
     *
     * @param changedByPage 保存で**値が変わった**設定（変わっていない項目は含めない）
     * @return 計画バージョンを進めたタスクコード
     */
    public Set<String> recordTimingChange(Map<String, Map<String, String>> changedByPage) {
        Set<String> taskCodes = tasksAffectedBy(changedByPage);
        if (taskCodes.isEmpty()) {
            return Set.of();
        }
        return recordTimingChange(taskCodes);
    }

    /**
     * 指定タスクの計画バージョンを進める（有効／無効の切替のように、設定キーを伴わない入口用）。
     *
     * @param taskCodes 実行設定が変わったタスク
     * @return 計画バージョンを進めたタスクコード
     */
    public Set<String> recordTimingChange(Collection<String> taskCodes) {
        if (taskCodes == null || taskCodes.isEmpty()) {
            return Set.of();
        }
        LocalDateTime effectiveFrom = LocalDateTime.now(clock);
        String iso = effectiveFrom.toString();
        Set<String> recorded = new LinkedHashSet<>();
        for (String taskCode : taskCodes) {
            if (taskCode == null || taskCode.isBlank() || catalog.find(taskCode).isEmpty()) {
                continue; // スケジュール対象外（起動時バッチなど）は計画バージョンを持たない
            }
            planMapper.markConfigEffectiveFrom(taskCode, iso);
            recorded.add(taskCode);
        }
        if (!recorded.isEmpty()) {
            log.info("実行設定の変更を記録しました（設定値と同じトランザクション）。"
                    + "tasks={} effectiveFrom={}", recorded, effectiveFrom);
        }
        return recorded;
    }

    /**
     * 変更された設定キーから、影響するタスクコードを求める（カタログの設定キーと突き合わせる）。
     *
     * <p>時刻・間隔・ずらしの**どれを変えても**同じ規則で扱う（入口ごとに規則を分けない）。</p>
     */
    public Set<String> tasksAffectedBy(Map<String, Map<String, String>> changedByPage) {
        Set<String> tasks = new LinkedHashSet<>();
        if (changedByPage == null || changedByPage.isEmpty()) {
            return tasks;
        }
        for (Map.Entry<String, Map<String, String>> page : changedByPage.entrySet()) {
            Map<String, String> keys = page.getValue();
            if (keys == null || keys.isEmpty()) {
                continue;
            }
            for (ScheduleTaskRule rule : catalog.rules()) {
                if (!rule.pageCode().equals(page.getKey())) {
                    continue;
                }
                for (String key : rule.settingKeys()) {
                    if (keys.containsKey(key)) {
                        tasks.add(rule.taskCode());
                    }
                }
            }
        }
        return tasks;
    }
}
