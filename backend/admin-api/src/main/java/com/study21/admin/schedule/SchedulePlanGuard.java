package com.study21.admin.schedule;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 「この計画実行点を、いま実行してよいか」の**唯一の判定**。
 *
 * <p>スケジューラ（30 秒ごとの検査）・再起動の復旧・実行直前の再検証が**同じ規則**を使う。
 * 判定を 3 か所に書くと、片方だけ直して食い違う（＝復旧で古い操作を実行する）ため。</p>
 *
 * <p>見るもの（利用者の指示）:</p>
 * <ol>
 *   <li><b>いまの時刻</b>（Asia/Tokyo）に対して、その点が「いま有効なはずの 1 点」か</li>
 *   <li><b>設定の版</b>（判定に使ったスナップショットの版。ログ・実行記録に残す）</li>
 *   <li><b>適用時刻</b>（設定の適用時刻より前の点は実行しない）</li>
 *   <li><b>有効／無効</b>（無効なら自動実行しない）</li>
 *   <li><b>タスクの実行状態</b>（もう一方のネット切替が実行中なら見送る）</li>
 * </ol>
 *
 * <p>種別ごとの規則:</p>
 * <ul>
 *   <li><b>R（batR03 / batR04）</b>: 2 つで**1 つのネット状態の切替**。いま以前で最後に到来した点
 *       （＝現在有効なはずの状態）だけを実行し、古い逆向きの操作は実行しない。
 *       復旧でも同じ（夜間の停止が翌朝まで残っていても、朝の開始だけを実行する）。</li>
 *   <li><b>L（batL02 / batL03）</b>: 取りこぼしは**最新の 1 点に合并**する。古い点は実行しない
 *       （履歴を 1 件ずつ再生しない）。実行直前の再検証だけは「1 周期より古い点を実行しない」
 *       という緩い規則にする（待ち行列で少し待っただけで実行できなくならないように）。</li>
 * </ul>
 *
 * <p>「手動操作を上書きしない」規則は業務側（{@code NetworkUsageService}）にある。ここでは
 * 判定せず、そのまま実行させる（端末の更新日時を見て業務が最終判断する）。</p>
 */
@Component
public class SchedulePlanGuard {

    /** 判定の場面（同じ規則でも、厳しさが少し違う）。 */
    public enum Stage {
        /** 30 秒ごとの検査（これから確保する点を選んだところ）。 */
        SCHEDULING,
        /** 再起動の復旧（確保済みの点をやり直してよいか）。**いちばん厳しい**。 */
        RECOVERY,
        /** 実行直前の再検証（待機中に設定が変わっていないか）。 */
        BEFORE_RUN
    }

    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ScheduleConfigService configService;
    private final ScheduleRuleCatalog catalog;
    private final ScheduledTriggerStore triggerStore;

    public SchedulePlanGuard(ScheduleConfigService configService,
                             ScheduleRuleCatalog catalog,
                             ScheduledTriggerStore triggerStore) {
        this.configService = configService;
        this.catalog = catalog;
        this.triggerStore = triggerStore;
    }

    /**
     * 1 つの計画実行点の判定。
     *
     * @param taskCode  バッチコード
     * @param plannedAt 計画実行点（実行記録の「予定時刻」）
     * @param now       いまの時刻
     * @param stage     判定の場面
     */
    public PlanDecision decide(String taskCode, LocalDateTime plannedAt, Instant now, Stage stage) {
        ScheduleConfigSnapshot snapshot = configService.snapshot();
        long version = snapshot.version();
        if (plannedAt == null) {
            return PlanDecision.skip("計画実行点（予定時刻）が分かりません。", version);
        }
        ScheduleTaskRule rule = catalog.find(taskCode).orElse(null);
        if (rule == null) {
            return PlanDecision.skip("スケジュールの対象外のバッチです。", version);
        }
        TaskConfigStatus status = snapshot.statusOf(taskCode);
        TaskSchedule schedule = snapshot.tasks().get(taskCode);
        if (!status.usable() || schedule == null) {
            if (status == TaskConfigStatus.NOT_LOADED) {
                // 設定を**まだ読めていない**（起動直後・DB を読めなかった）。無効と決めつけず、判定を保留する
                // （保留した実行は復旧で閉じない。次の起動や托底のあとにもう一度判定する）
                return PlanDecision.wait("実行設定をまだ読み込めていないため、判定を保留します（" + status.label() + "）。", version);
            }
            return PlanDecision.skip(statusReason(status) + "ため実行しません。", version);
        }
        if (!schedule.enabled()) {
            return PlanDecision.skip("無効に設定されているため実行しません。", version);
        }
        if (!schedule.canRunAt(plannedAt)) {
            return PlanDecision.skip("設定の適用時刻（" + label(schedule.effectiveFrom())
                    + "）より前の計画実行点です。", version);
        }
        LocalDateTime nowLocal = LocalDateTime.ofInstant(now, ScheduleConfigService.ZONE);
        if (plannedAt.isAfter(nowLocal)) {
            return PlanDecision.skip("まだ到来していない計画実行点です（" + label(plannedAt) + "）。", version);
        }
        if (rule.kind() == ScheduleKind.DAILY) {
            return decideNetworkPoint(rule, schedule, plannedAt, nowLocal, stage, version);
        }
        return decideIntervalPoint(schedule, plannedAt, nowLocal, stage, version);
    }

    /**
     * 種別 R（ネット利用の開始／終了）の判定。
     *
     * <p>2 つのタスクで 1 つのネット状態なので、**いま以前で最後に到来した点**だけを実行する。
     * それより古い点（＝逆向きの操作）は実行しない。</p>
     */
    private PlanDecision decideNetworkPoint(ScheduleTaskRule rule, TaskSchedule schedule,
                                            LocalDateTime plannedAt, LocalDateTime nowLocal,
                                            Stage stage, long version) {
        LocalDateTime newest = newestNetworkPointAtOrBefore(nowLocal);
        if (newest == null) {
            return PlanDecision.skip("いま適用すべきネット状態の切替が見つかりません。", version);
        }
        if (newest.isAfter(plannedAt)) {
            return PlanDecision.skip("より新しいネット状態の切替（" + label(newest)
                    + "）があるため、この古い切替は実行しません。", version);
        }
        if (newest.isBefore(plannedAt)) {
            return PlanDecision.skip("いま適用すべきネット状態の切替は " + label(newest)
                    + " のため、この点は実行しません。", version);
        }
        // 検査（これから確保する場面）だけ、もう一方の切替が実行中なら見送る。
        // 復旧では未完了の行を先に閉じているので、ここでは判定しない。
        // 実行直前では「いま有効なはずの状態」を適用する（古い逆向きの操作の結果を残さない）
        if (stage == Stage.SCHEDULING) {
            String sibling = siblingTaskCode(rule.taskCode());
            if (sibling != null && triggerStore.isTaskRunning(sibling)) {
                return PlanDecision.wait("もう一方のネット切替（" + sibling
                        + "）が実行中のため、今回は見送ります（次の検査で再挑戦）。", version);
            }
        }
        return PlanDecision.run(schedule, version);
    }

    /** 種別 L（学習モニターの取込・分析）の判定。取りこぼしは最新の 1 点に合并する。 */
    private PlanDecision decideIntervalPoint(TaskSchedule schedule, LocalDateTime plannedAt,
                                             LocalDateTime nowLocal, Stage stage, long version) {
        LocalDateTime latest = schedule.previousRunnablePointAtOrBefore(nowLocal).orElse(null);
        if (stage == Stage.BEFORE_RUN) {
            // 待ち行列で少し待っただけで実行できなくならないよう、1 周期ぶんは許す
            Integer interval = schedule.intervalMinutes();
            LocalDateTime oldestAllowed = interval == null ? plannedAt : nowLocal.minusMinutes(interval);
            if (plannedAt.isBefore(oldestAllowed)) {
                return PlanDecision.skip("計画実行点（" + label(plannedAt)
                        + "）が " + interval + " 分より古くなっているため実行しません（次は最新の 1 点だけを実行します）。",
                        version);
            }
            return PlanDecision.run(schedule, version);
        }
        if (latest == null || !latest.equals(plannedAt)) {
            String detail = latest == null
                    ? "適用時刻より後にまだ計画実行点が来ていません"
                    : "より新しい計画実行点（" + label(latest) + "）";
            return PlanDecision.skip(detail
                    + "があるため、この点は実行しません（次回は最新の 1 点だけを実行します）。", version);
        }
        return PlanDecision.run(schedule, version);
    }

    /**
     * いま以前で**最後に到来したネット状態の切替**（batR03 / batR04 のうち新しい方）。
     *
     * <p>無効・適用時刻より前の点も「最後に到来した点」として数える（それが最新なら、
     * それより古い逆向きの操作は実行しない。スケジューラと同じ規則）。</p>
     */
    private LocalDateTime newestNetworkPointAtOrBefore(LocalDateTime nowLocal) {
        ScheduleConfigSnapshot snapshot = configService.snapshot();
        LocalDateTime newest = null;
        for (ScheduleTaskRule rule : catalog.rules()) {
            if (rule.kind() != ScheduleKind.DAILY) {
                continue;
            }
            TaskSchedule schedule = snapshot.tasks().get(rule.taskCode());
            if (schedule == null) {
                continue;
            }
            LocalDateTime point = schedule.previousPointAtOrBefore(nowLocal);
            if (point != null && (newest == null || point.isAfter(newest))) {
                newest = point;
            }
        }
        return newest;
    }

    /** ネット利用のもう一方のタスク（batR03 ⇄ batR04）。 */
    public static String siblingTaskCode(String taskCode) {
        if ("batR03".equals(taskCode)) {
            return "batR04";
        }
        if ("batR04".equals(taskCode)) {
            return "batR03";
        }
        return null;
    }

    /** 設定が使えない理由（日本語。そのまま「〜ため実行しません」につなげる）。 */
    static String statusReason(TaskConfigStatus status) {
        return switch (status) {
            case NOT_LOADED -> "実行設定をまだ読み込めていない";
            case MISSING -> "実行設定が無い";
            case INVALID -> "実行設定が不正な";
            case LOADED -> "（実行設定は有効）";
        };
    }

    /** ログ・実行記録に残す日本語（分からないときは「不明」）。 */
    static String label(LocalDateTime value) {
        return value == null ? "不明" : value.format(LABEL);
    }

    /**
     * 判定の結果。
     *
     * @param kind          判定（{@link Kind#RUN} 実行してよい / {@link Kind#WAIT} 今は実行しないが
     *                      無効と決めつけない（見送り・保留）/ {@link Kind#SKIP} 実行しない）
     * @param reason        KIND が RUN 以外のときの理由（日本語）
     * @param configVersion 判定に使った設定の版（ログ・実行記録用）
     * @param effectiveFrom 判定に使った設定の適用時刻（ログ用。無ければ null）
     */
    public record PlanDecision(Kind kind, String reason, long configVersion, LocalDateTime effectiveFrom) {

        /** 判定の種類。 */
        public enum Kind {
            /** 実行してよい。 */
            RUN,
            /** 今は実行しない（もう一方の切替が実行中・設定をまだ読めていない）。無効と決めつけない。 */
            WAIT,
            /** 実行しない（無効・適用時刻より前・新しい点に追い越された・設定が無い／不正）。 */
            SKIP
        }

        static PlanDecision run(TaskSchedule schedule, long version) {
            return new PlanDecision(Kind.RUN, null, version, schedule.effectiveFrom());
        }

        static PlanDecision wait(String reason, long version) {
            return new PlanDecision(Kind.WAIT, reason, version, null);
        }

        static PlanDecision skip(String reason, long version) {
            return new PlanDecision(Kind.SKIP, reason, version, null);
        }

        /** 実行してよいか。 */
        public boolean allowed() {
            return kind == Kind.RUN;
        }

        /** 今は実行しないが、無効と決めつけてはいけないか（見送り・保留）。 */
        public boolean deferred() {
            return kind == Kind.WAIT;
        }
    }
}
