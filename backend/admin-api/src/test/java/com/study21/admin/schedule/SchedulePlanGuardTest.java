package com.study21.admin.schedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 「この計画実行点を、いま実行してよいか」の共通判定（{@link SchedulePlanGuard}）。
 *
 * <p>スケジューラ・再起動の復旧・実行直前の再検証が**同じ規則**を使うことを固定する。
 * 特に利用者が挙げた次の 4 つ:</p>
 * <ol>
 *   <li>夜間に停止していて翌朝に復帰 → 実行するのは**朝の開始だけ**（前夜の停止は実行しない）</li>
 *   <li>R03 / R04 は**1 つのネット状態**（逆向きの操作を後から実行しない）</li>
 *   <li>L02 / L03 は**最新の 1 点に合并**（古い点を 1 件ずつ再生しない）</li>
 *   <li>適用時刻・有効／無効・設定の有無も同じ判定で見る</li>
 * </ol>
 */
class SchedulePlanGuardTest {

    private static final ZoneId ZONE = ScheduleConfigService.ZONE;

    /** 2026-09-20 09:00 JST。 */
    private static final Instant MORNING = Instant.parse("2026-09-20T00:00:00Z");

    private ScheduledTriggerStore triggerStore;
    private ScheduleRuleCatalog catalog;
    private SchedulePlanGuard guard;
    /** 直近に作ったスナップショット（判定にはこれを渡す）。 */
    private ScheduleConfigSnapshot snapshot;

    @BeforeEach
    void setUp() {
        triggerStore = mock(ScheduledTriggerStore.class);
        catalog = new ScheduleRuleCatalog();
        guard = new SchedulePlanGuard(catalog, triggerStore);
    }

    private void config(TaskSchedule... schedules) {
        Map<String, TaskSchedule> tasks = new LinkedHashMap<>();
        Map<String, TaskConfigStatus> statuses = new LinkedHashMap<>();
        for (String code : catalog.taskCodes()) {
            statuses.put(code, TaskConfigStatus.MISSING);
        }
        for (TaskSchedule schedule : schedules) {
            tasks.put(schedule.taskCode(), schedule);
            statuses.put(schedule.taskCode(), TaskConfigStatus.LOADED);
        }
        snapshot = new ScheduleConfigSnapshot(9, MORNING, ZONE,
                tasks, statuses, Map.of("batR03", 3L, "batR04", 1L), null);
    }

    private SchedulePlanGuard.PlanDecision decide(String taskCode, LocalDateTime plannedAt,
                                                  SchedulePlanGuard.Stage stage) {
        return guard.decide(snapshot, taskCode, plannedAt, MORNING, stage);
    }

    /** 渡したスナップショットをそのまま使う（判定の途中で取り直さない）。 */
    private SchedulePlanGuard.PlanDecision decideWith(ScheduleConfigSnapshot given, String taskCode,
                                                      LocalDateTime plannedAt, SchedulePlanGuard.Stage stage) {
        return guard.decide(given, taskCode, plannedAt, MORNING, stage);
    }

    @Test
    @DisplayName("夜間に停止していて翌朝に復帰: 朝の開始だけを実行する（前夜の停止は実行しない）")
    void theMorningStartIsTheOnlyValidNetworkEvent() {
        config(TaskSchedule.daily("batR03", true, LocalTime.of(23, 30)),
                TaskSchedule.daily("batR04", true, LocalTime.of(6, 30)));

        // 前夜の停止（23:30）は「いま有効なはずの状態」ではない
        SchedulePlanGuard.PlanDecision stop =
                decide("batR03", LocalDateTime.of(2026, 9, 19, 23, 30), SchedulePlanGuard.Stage.RECOVERY);
        assertThat(stop.allowed()).isFalse();
        assertThat(stop.reason()).contains("より新しいネット状態の切替").contains("2026-09-20 06:30");

        // 朝の開始（06:30）は実行してよい
        SchedulePlanGuard.PlanDecision start =
                decide("batR04", LocalDateTime.of(2026, 9, 20, 6, 30), SchedulePlanGuard.Stage.RECOVERY);
        assertThat(start.allowed()).isTrue();
        assertThat(start.configVersion()).isEqualTo(9);
    }

    @Test
    @DisplayName("R03 / R04 のどちらでも、いま以前で最後の点だけが実行できる")
    void onlyTheLatestNetworkPointIsValid() {
        config(TaskSchedule.daily("batR03", true, LocalTime.of(23, 30)),
                TaskSchedule.daily("batR04", true, LocalTime.of(6, 30)));

        // 23:50（停止直後）は停止が有効
        assertThat(guard.decide(snapshot, "batR03", LocalDateTime.of(2026, 9, 19, 23, 30), Instant.parse("2026-09-19T14:50:00Z"),
                SchedulePlanGuard.Stage.BEFORE_RUN).allowed()).isTrue();
        // 07:00（開始後）は開始だけが有効
        assertThat(guard.decide(snapshot, "batR04", LocalDateTime.of(2026, 9, 20, 6, 30), MORNING,
                SchedulePlanGuard.Stage.BEFORE_RUN).allowed()).isTrue();
        assertThat(guard.decide(snapshot, "batR03", LocalDateTime.of(2026, 9, 19, 23, 30), MORNING,
                SchedulePlanGuard.Stage.BEFORE_RUN).allowed()).isFalse();
    }

    @Test
    @DisplayName("L02 は最新の 1 点だけを実行する（古い点は新しい点に追い越されている）")
    void onlyTheLatestIntervalPointIsValid() {
        config(TaskSchedule.interval("batL02", true, 5, 1));

        // 09:00 の時点の最新の点は 08:56
        assertThat(decide("batL02", LocalDateTime.of(2026, 9, 20, 8, 56),
                SchedulePlanGuard.Stage.RECOVERY).allowed()).isTrue();
        SchedulePlanGuard.PlanDecision old =
                decide("batL02", LocalDateTime.of(2026, 9, 20, 8, 36), SchedulePlanGuard.Stage.RECOVERY);
        assertThat(old.allowed()).isFalse();
        assertThat(old.reason()).contains("より新しい計画実行点（2026-09-20 08:56）");
    }

    @Test
    @DisplayName("実行直前の再検証は 1 周期ぶん許す（待ち行列で少し待っただけで実行できなくならない）")
    void beforeRunAllowsOneIntervalOfDelay() {
        config(TaskSchedule.interval("batL02", true, 5, 1));

        // 08:56 の点を 09:00 に実行する（4 分待ち）→ 実行してよい
        assertThat(decide("batL02", LocalDateTime.of(2026, 9, 20, 8, 56),
                SchedulePlanGuard.Stage.BEFORE_RUN).allowed()).isTrue();
        // 08:51 の点を 09:00 に実行する（9 分待ち＝1 周期超）→ 実行しない
        SchedulePlanGuard.PlanDecision stale =
                decide("batL02", LocalDateTime.of(2026, 9, 20, 8, 51), SchedulePlanGuard.Stage.BEFORE_RUN);
        assertThat(stale.allowed()).isFalse();
        assertThat(stale.reason()).contains("古くなっている");
    }

    @Test
    @DisplayName("無効・設定なし・適用時刻より前は、どの場面でも実行しない")
    void disabledMissingAndBeforeEffectiveFromAreNeverRun() {
        config(TaskSchedule.daily("batR04", false, LocalTime.of(6, 30)),
                TaskSchedule.interval("batL02", true, 5, 1)
                        .withEffectiveFrom(LocalDateTime.of(2026, 9, 20, 8, 58)));

        SchedulePlanGuard.PlanDecision disabled =
                decide("batR04", LocalDateTime.of(2026, 9, 20, 6, 30), SchedulePlanGuard.Stage.BEFORE_RUN);
        assertThat(disabled.allowed()).isFalse();
        assertThat(disabled.reason()).contains("無効");

        // batR03 はこのスナップショットで「設定なし」
        SchedulePlanGuard.PlanDecision missing =
                decide("batR03", LocalDateTime.of(2026, 9, 19, 23, 30), SchedulePlanGuard.Stage.BEFORE_RUN);
        assertThat(missing.allowed()).isFalse();
        assertThat(missing.reason()).contains("実行設定が無い");

        // 復旧では「設定が無い」を**閉じない**（保留）。設定を直せば実行できるため
        SchedulePlanGuard.PlanDecision missingInRecovery =
                decide("batR03", LocalDateTime.of(2026, 9, 19, 23, 30), SchedulePlanGuard.Stage.RECOVERY);
        assertThat(missingInRecovery.allowed()).isFalse();
        assertThat(missingInRecovery.deferred()).isTrue();

        // 適用時刻（08:58）より前の点は実行しない
        SchedulePlanGuard.PlanDecision beforeEffective =
                decide("batL02", LocalDateTime.of(2026, 9, 20, 8, 56), SchedulePlanGuard.Stage.BEFORE_RUN);
        assertThat(beforeEffective.allowed()).isFalse();
        assertThat(beforeEffective.reason()).contains("設定の適用時刻");
    }

    @Test
    @DisplayName("もう一方のネット切替が実行中なら、検査では見送る（点を確保しない）")
    void defersWhenTheSiblingIsRunning() {
        config(TaskSchedule.daily("batR04", true, LocalTime.of(6, 30)));
        when(triggerStore.isTaskRunning("batR03")).thenReturn(true);

        SchedulePlanGuard.PlanDecision decision =
                decide("batR04", LocalDateTime.of(2026, 9, 20, 6, 30), SchedulePlanGuard.Stage.SCHEDULING);
        assertThat(decision.deferred()).isTrue();
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("もう一方のネット切替");

        // 実行直前では「いま有効なはずの状態」を適用する（見送らない）
        assertThat(decide("batR04", LocalDateTime.of(2026, 9, 20, 6, 30),
                SchedulePlanGuard.Stage.BEFORE_RUN).allowed()).isTrue();
    }

    @Test
    @DisplayName("判定の途中でメモリのスナップショットが入れ替わっても、渡した 1 枚だけで判定する")
    void usesOnlyTheGivenSnapshot() {
        config(TaskSchedule.daily("batR04", true, LocalTime.of(6, 30)));
        ScheduleConfigSnapshot older = snapshot;

        // メモリのスナップショットが「無効」に入れ替わった（別のスレッドの保存）
        Map<String, TaskSchedule> changed = new LinkedHashMap<>();
        changed.put("batR04", TaskSchedule.daily("batR04", false, LocalTime.of(6, 30)));
        Map<String, TaskConfigStatus> statuses = new LinkedHashMap<>();
        for (String code : catalog.taskCodes()) {
            statuses.put(code, TaskConfigStatus.MISSING);
        }
        statuses.put("batR04", TaskConfigStatus.LOADED);
        ScheduleConfigSnapshot newer = new ScheduleConfigSnapshot(10, MORNING, ZONE, changed, statuses, Map.of(), null);

        // 古い方（渡した方）で判定 → 有効として実行してよい
        SchedulePlanGuard.PlanDecision decision = decideWith(older, "batR04",
                LocalDateTime.of(2026, 9, 20, 6, 30), SchedulePlanGuard.Stage.BEFORE_RUN);
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.configVersion()).isEqualTo(older.version());   // 版は渡したスナップショットのもの

        // 新しい方で判定すれば無効（次の判定は新しいスナップショットを使う）
        assertThat(decideWith(newer, "batR04", LocalDateTime.of(2026, 9, 20, 6, 30),
                SchedulePlanGuard.Stage.BEFORE_RUN).allowed()).isFalse();
        assertThat(decideWith(newer, "batR04", LocalDateTime.of(2026, 9, 20, 6, 30),
                SchedulePlanGuard.Stage.BEFORE_RUN).configVersion()).isEqualTo(newer.version());
    }

    @Test
    @DisplayName("R の判定も L の判定も、渡した 1 枚だけで計算する")
    void networkAndIntervalDecisionsUseTheGivenSnapshot() {
        // R: 渡したスナップショットに batR03 / batR04 が入っている
        config(TaskSchedule.daily("batR03", true, LocalTime.of(23, 30)),
                TaskSchedule.daily("batR04", true, LocalTime.of(6, 30)));
        ScheduleConfigSnapshot network = snapshot;
        assertThat(decideWith(network, "batR04", LocalDateTime.of(2026, 9, 20, 6, 30),
                SchedulePlanGuard.Stage.SCHEDULING).allowed()).isTrue();

        // L: 渡したスナップショットに batL02 だけが入っている（他のタスクは未設定のまま）
        config(TaskSchedule.interval("batL02", true, 5, 1));
        ScheduleConfigSnapshot interval = snapshot;
        assertThat(decideWith(interval, "batL02", LocalDateTime.of(2026, 9, 20, 8, 56),
                SchedulePlanGuard.Stage.SCHEDULING).allowed()).isTrue();
        // 渡した方に batR04 は入っていない → 「設定なし」として扱う（メモリの別スナップショットを見ない）
        assertThat(decideWith(interval, "batR04", LocalDateTime.of(2026, 9, 20, 6, 30),
                SchedulePlanGuard.Stage.BEFORE_RUN).reason()).contains("実行設定が無い");
    }

    @Test
    @DisplayName("まだ到来していない点・対象外のバッチは実行しない")
    void futurePointsAndUnknownTasksAreRejected() {
        config(TaskSchedule.daily("batR04", true, LocalTime.of(6, 30)));

        SchedulePlanGuard.PlanDecision future =
                decide("batR04", LocalDateTime.of(2026, 9, 21, 6, 30), SchedulePlanGuard.Stage.BEFORE_RUN);
        assertThat(future.allowed()).isFalse();
        assertThat(future.reason()).contains("まだ到来していない");

        SchedulePlanGuard.PlanDecision unknown =
                decide("batS01", LocalDateTime.of(2026, 9, 20, 6, 30), SchedulePlanGuard.Stage.BEFORE_RUN);
        assertThat(unknown.allowed()).isFalse();
        assertThat(unknown.reason()).contains("対象外");
    }
}
