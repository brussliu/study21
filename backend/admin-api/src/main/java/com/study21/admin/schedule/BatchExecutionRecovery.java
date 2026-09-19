package com.study21.admin.schedule;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchExecutionMapper;
import com.study21.admin.batch.BatchExecutionStatus;
import com.study21.admin.batch.ProcessRunId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * サービス再起動で**未完了のまま残った実行**を復旧する。
 *
 * <p>落ちた実行（待機中・実行中）を残したままだと、同じタスクの次の実行が
 * 「前回がまだ実行中」と見なされて永久に走らない。起動時に 1 回、次のように扱う:</p>
 *
 * <ul>
 *   <li><b>まず「いまの計画」で有効かを判定する</b>（{@link SchedulePlanGuard}）。スケジューラと
 *       **同じ規則**を使う（現在時刻・設定の版・適用時刻・有効／無効・実行状態）。</li>
 *   <li><b>待機中（まだ始まっていない）</b>: そのまま実行し直す（同じ実行IDを使う。
 *       業務は何度実行しても同じ結果になるように作ってある）。</li>
 *   <li><b>実行中（結果が分からない）</b>: 閉じてやり直しを作るまでを**1 トランザクション**で行い
 *       （{@link ScheduledTriggerStore#recoverRunningExecution}）、コミット後に実行器へ渡す。
 *       新しい計画実行点に追い越されていればやり直さない。</li>
 *   <li>スケジューラが管理しないタスク（起動時バッチなど）は今までどおり失敗として閉じるだけ。</li>
 * </ul>
 *
 * <p><b>種別 R（batR03 / batR04）は 1 つのネット状態</b>なので、未完了が両方にあるときは
 * **新しい点から順に見る**。古い方（＝逆向きの操作）は判定で無効になり、スキップとして閉じる。
 * <b>種別 L（batL02 / batL03）は取りこぼしを最新の 1 点に合并する</b>（古い点は実行しない）。</p>
 *
 * <h2>1 パスは「整理」と「投入」の 2 段階（古い記録を先に閉じる）</h2>
 *
 * <p>判定しながら実行器へ渡すと、**同じタスクの古い記録がまだ未完了のうちに新しい記録が走り出し**、
 * 業務側の「前回が終わっていないのでスキップ」に当たって**本来実行すべき記録が失われる**
 * （例: batL02 の古い A と最新の B があり、B が A のためにスキップされ、そのあと A が
 * 「計画が古い」で閉じられる）。そこで 1 パスを 2 段階に分ける:</p>
 *
 * <ol>
 *   <li><b>第 1 段階（判定と整理）</b>: そのパスの**1 枚のスナップショット**で全遺留を判定し、
 *       実行しない記録は理由つきで閉じ、有効な記録は**投入待ちとして集めるだけ**にする
 *       （実行器へはまだ渡さない）。実行中（RUNNING）は従来どおり
 *       {@link ScheduledTriggerStore#recoverRunningExecution} で
 *       「閉じる＋やり直しを作る」を**1 トランザクション**で行い、**コミット後に**やり直しを
 *       投入待ちへ入れる。</li>
 *   <li><b>第 2 段階（投入）</b>: 投入待ちを実行器へ渡す。同じタスクに未決の遺留
 *       （保留・閉じられなかった・復旧トランザクションが失敗した）が残っているタスクは
 *       **今回は投入せず持ち越す**（次で再判定。未決が片付けば投入する）。他のタスクは進める。</li>
 * </ol>
 *
 * <p>投入待ちは**復旧の追跡に残す**（渡すまで、または明確に終わったと分かるまで外さない）。
 * 実行中から作ったやり直しは**このプロセスの記録**なので「前のプロセスの遺留」の検索では
 * 二度と出てこない。だから**この追跡が唯一の持ち主**であり、次パス・次回起動まで責任を持つ。</p>
 *
 * <h2>復旧は「1 回やって終わり」ではない（自動で続ける）</h2>
 *
 * <p>起動時に設定が読めないことがある（DB がまだ上がっていない等）。そのとき実行を
 * 「無効」と決めつけて閉じてはいけないし、**閉じずに放置すると次の実行が永久に止まる**。
 * そこで段階を持つ:</p>
 *
 * <ol>
 *   <li>{@link Phase#PENDING_DISCOVERY} … 前のプロセスが残した実行（遺留）をまだ読めていない。
 *       30 秒の検査のたびに（退避しながら）読み直す。</li>
 *   <li>{@link Phase#PENDING_REVIEW} … 遺留リストは読めた（**この時点で固定**）。設定が読めない等で
 *       まだ判定できない行が残っている。30 秒の検査のたびに（退避しながら）同じリストを判定し直す。</li>
 *   <li>{@link Phase#COMPLETED} … すべて処理した。**以後は DB を引かない**（無駄な問い合わせをしない）。</li>
 * </ol>
 *
 * <p><b>復旧の境界は「実行記録の帰属」で決める</b>: 対象は「**前のプロセスが残した実行**」だけ。
 * 実行記録には作ったプロセスの起動識別子（{@link com.study21.admin.batch.ProcessRunId}）が
 * 挿入時に自動で刻まれていて（{@code BatchExecutionRunIdInterceptor}）、復旧は
 * {@link BatchExecutionMapper#findLeftoversExceptRunId(String)} で
 * **現在の識別子と違う行だけ**を遺留として引く。</p>
 *
 * <p>「起動時に読んだ最大の実行ID より古い行」で選ばない理由: 最初の読み込みが失敗して
 * 再試行する間に現在のプロセスが実行を作ると、その実行が境界の内側に入ってしまい、
 * **実行中の自分の実行を遺留と誤認して閉じる／二重に走らせる**。帰属で選べば、
 * 読み直しを何度しても自分の実行は入らない（境界を「固定」する必要も無い）。
 * 履歴の 起動識別子 が NULL の行（この列が無かった頃の行）は互換のため遺留として扱う。</p>
 *
 * <p>同じ遺留の行を 2 回処理しないよう、処理は 1 本に直列化し（{@code synchronized}）、
 * 行は**処理が確定した時点でリストから外す**。加えて DB 側でも
 * 「未完了のときだけ閉じる（条件つき UPDATE）」と「やり直しは元実行ID で 1 つだけ（部分一意索引）」
 * で守るので、**メモリのロックだけに依存しない**。</p>
 *
 * <p>順序は {@link Ordered#HIGHEST_PRECEDENCE}。起動時バッチ（{@code BatchStartupRunner}）より
 * **先**に走らせて、起動時バッチの新しい実行を誤って失敗にしないようにする。</p>
 *
 * <p><b>手動操作の保護は業務側で続く</b>（{@code NetworkUsageService} が端末の更新日時を見て、
 * 計画時刻より後に利用者が触っていれば切り替えない）。ここでは実行してよいかを決めるだけ。</p>
 *
 * <p>前提: 第一版は admin-api が 1 インスタンスで動く（多重起動する構成にしたときは、
 * 他インスタンスが実行中の行を閉じてしまわないよう、この復旧の条件を見直すこと）。
 * ただし「やり直しは 1 つだけ」「閉じるのは 1 つだけ」は DB 側で守ってある。</p>
 */
@Component
public class BatchExecutionRecovery {

    /** 復旧の段階。 */
    public enum Phase {
        /** 前のプロセスが残した実行（遺留）をまだ読めていない。 */
        PENDING_DISCOVERY("遺留の確認待ち"),
        /** 遺留リストは読めたが、設定が読めない等でまだ判定できない行が残っている。 */
        PENDING_REVIEW("計画の判定待ち"),
        /** すべて処理した（以後は問い合わせしない）。 */
        COMPLETED("完了");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        /** 画面に出す日本語。 */
        public String label() {
            return label;
        }
    }

    /** 再起動で閉じた実行に残すメッセージ（結果が分からない実行）。 */
    static final String RECOVERY_MESSAGE = "サービス再起動のため中断しました（結果は不明です）。";

    /** 実行しないと判定して閉じた実行に残すメッセージの前置き（理由を続ける）。 */
    static final String SKIPPED_PREFIX = "サービス再起動時に計画が有効でなかったため実行しませんでした。";

    /** やり直しの実行記録に残す依頼元コード。 */
    static final String RETRY_CODE = "RECOVERY";

    /** 続けられないとき／判定を保留したときの再試行の間隔（上限 300 秒）。 */
    private static final Duration[] BACKOFF = {
            Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(120),
            Duration.ofSeconds(240), Duration.ofSeconds(300)};

    private static final DateTimeFormatter PAYLOAD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final Logger log = LoggerFactory.getLogger(BatchExecutionRecovery.class);

    private final BatchExecutionMapper executionMapper;
    private final ScheduledTriggerStore triggerStore;
    private final BatchScheduleExecutor executor;
    private final ScheduleRuleCatalog catalog;
    private final SchedulePlanGuard planGuard;
    private final ScheduleConfigService configService;
    private final ProcessRunId processRunId;
    /**
     * **自動運転のスイッチ**（起動時の自動復旧と、30 秒検査からの再試行）。既定は有効。
     *
     * <p>無効にしても復旧の機能そのものは使える（{@link #recoverInterruptedExecutions()} や
     * {@link #retryPendingRecoveryNow(String)} を**明示的に**呼べば動く）。テストや、
     * 手動でだけ動かす環境のために、自動の入口だけを止める。</p>
     */
    private final boolean autoRunEnabled;
    private final Clock clock;

    /** 処理を 1 本に直列化する（起動イベント・周期検査・手動の再読み込みが同時に来ても 1 件ずつ）。 */
    private final Object lock = new Object();
    /** まだ**決着していない**遺留（保留・閉じる途中・復旧トランザクション待ち）。 */
    private final Set<Long> leftoverIds = new LinkedHashSet<>();
    /** まだ**実行器へ渡していない**実行（遺留の再投入と、実行中から作ったやり直し）。 */
    private final Map<Long, PendingDispatch> pendingDispatches = new LinkedHashMap<>();
    private Phase phase = Phase.PENDING_DISCOVERY;
    private Instant nextAttemptAt = Instant.EPOCH;
    private int attempts;
    private int processed;
    private int skipped;
    private int retried;

    @org.springframework.beans.factory.annotation.Autowired
    public BatchExecutionRecovery(BatchExecutionMapper executionMapper,
                                  ScheduledTriggerStore triggerStore,
                                  BatchScheduleExecutor executor,
                                  ScheduleRuleCatalog catalog,
                                  SchedulePlanGuard planGuard,
                                  ScheduleConfigService configService,
                                  ProcessRunId processRunId,
                                  @org.springframework.beans.factory.annotation.Value(
                                          "${study21.batch.auto-run.recovery-enabled:true}") boolean autoRunEnabled) {
        this(executionMapper, triggerStore, executor, catalog, planGuard, configService, processRunId,
                autoRunEnabled, Clock.system(ScheduleConfigService.ZONE));
    }

    /** テスト用（時計と自動運転のスイッチを差し替える）。 */
    public BatchExecutionRecovery(BatchExecutionMapper executionMapper,
                                  ScheduledTriggerStore triggerStore,
                                  BatchScheduleExecutor executor,
                                  ScheduleRuleCatalog catalog,
                                  SchedulePlanGuard planGuard,
                                  ScheduleConfigService configService,
                                  ProcessRunId processRunId,
                                  boolean autoRunEnabled,
                                  Clock clock) {
        this.executionMapper = executionMapper;
        this.triggerStore = triggerStore;
        this.executor = executor;
        this.catalog = catalog;
        this.planGuard = planGuard;
        this.configService = configService;
        this.processRunId = processRunId;
        this.autoRunEnabled = autoRunEnabled;
        this.clock = clock;
    }

    /**
     * 起動時に 1 回（**設定の読み込みのあと・起動時バッチより先**）。
     *
     * <p>設定の読み込み（{@code ScheduleConfigService#loadOnStartup}）と同じイベントなので、
     * 順序を明示する: 設定 → この復旧 → 起動時バッチ（{@code BatchStartupRunner}）。
     * 復旧が先だと「設定が読めていない」で判定を保留してしまい、読み込みも 2 回になる。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public void recoverInterruptedExecutions() {
        // 自動の入口（薄い適配層）。スイッチが無効なら何もしない。
        // 復旧そのものは recoverInterruptedExecutions() を明示的に呼べば動く
        if (!autoRunEnabled) {
            log.info("起動時の自動復旧は無効です（study21.batch.auto-run.recovery-enabled=false）。"
                    + "明示的に呼ぶと復旧します。");
            return;
        }
        advance("起動時");
    }

    /**
     * 30 秒の検査から呼ぶ（退避の間隔を守る）。
     *
     * <p>復旧が完了していれば**何もしない**（DB を引かない）。設定が読めなかった等で
     * 保留している行があれば、退避の間隔で判定し直す（＝**再起動しなくても自動で続く**）。</p>
     */
    public void retryPendingRecoveryIfDue() {
        if (!autoRunEnabled) {
            return;   // 自動の入口は無効（30 秒検査からも進めない）
        }
        advance("自動再試行", false);
    }

    /**
     * 設定が読めるようになった直後（設定の保存・有効／無効の切替・管理者の再読み込み）に呼ぶ。
     *
     * <p>このときは退避を待たずに**すぐ**判定し直す。「設定を直したのに最大 5 分待たされる」を
     * 避けるため（設定の保存が即座に反映されるのと同じ考え方）。</p>
     */
    public void retryPendingRecoveryNow(String reason) {
        advance(reason, true);
    }

    /** いまの復旧の状態（画面・ログ用）。 */
    public RecoveryStatus status() {
        synchronized (lock) {
            Set<Long> pending = new LinkedHashSet<>(leftoverIds);
            pending.addAll(pendingDispatches.keySet());
            return new RecoveryStatus(phase, phase.label(), processRunId.value(),
                    pending.size(), processed, skipped, retried);
        }
    }

    // ------------------------------------------------------------------ 内部

    /** 1 回ぶんの前進（起動イベント・周期検査・設定の回復から呼ばれる）。 */
    void advance(String reason) {
        advance(reason, false);
    }

    /**
     * 1 回ぶんの前進。
     *
     * @param force 退避を待たずに判定し直すか（設定が読めるようになった直後）
     */
    private void advance(String reason, boolean force) {
        synchronized (lock) {
            if (phase == Phase.COMPLETED) {
                return;   // 終わったら問い合わせない
            }
            Instant now = Instant.now(clock);
            if (!force && now.isBefore(nextAttemptAt)) {
                return;   // 退避中（毎回 DB を引かない）
            }
            if (force) {
                // 設定が読めるようになった等、状況が変わった → 退避を数え直す
                attempts = 0;
                nextAttemptAt = Instant.EPOCH;
            }
            try {
                if (phase == Phase.PENDING_DISCOVERY) {
                    discover(reason);
                }
                if (phase == Phase.PENDING_REVIEW) {
                    review(reason, now);
                }
                attempts = 0;
            } catch (RuntimeException cause) {
                attempts++;
                Duration wait = backoff(attempts);
                nextAttemptAt = now.plus(wait);
                log.warn("再起動の復旧を続けられませんでした（{} 秒後に再試行します）。"
                        + "phase={} reason={} cause={}", wait.toSeconds(), phase, reason, messageOf(cause));
            }
        }
    }

    /**
     * 前のプロセスが残した実行（遺留）を確定する（失敗したら次の周期でやり直す）。
     *
     * <p><b>境界は「実行ID の大小」ではなく「実行記録の帰属」で決める</b>
     * （{@code 起動識別子} が現在のプロセスと違う行だけを遺留とする）。ID の大小で選ぶと、
     * 最初の読み込みが失敗して再試行する間に現在のプロセスが作った実行を遺留と誤認し、
     * 実行中の自分の実行を閉じたり二重に走らせたりする。帰属で選べば、読み直しを何度しても
     * **自分の実行は絶対に入らない**（境界の固定という概念が要らない）。</p>
     *
     * <p>設定も一緒に読んでおく（読めなければ判定は保留になる）。</p>
     */
    private void discover(String reason) {
        // 設定は先に読んでおく（托底。読めなくても遺留リストは読む）
        configService.ensureUsableConfig();

        String runId = processRunId.value();
        List<BatchExecutionEntity> leftovers = executionMapper.findLeftoversExceptRunId(runId);
        leftoverIds.clear();
        for (BatchExecutionEntity row : leftovers) {
            leftoverIds.add(row.getExecutionId());
        }
        if (leftoverIds.isEmpty()) {
            phase = Phase.COMPLETED;
            log.info("再起動で復旧すべき実行はありません。runId={} reason={}", runId, reason);
            return;
        }
        phase = Phase.PENDING_REVIEW;
        log.warn("再起動で中断した実行が見つかりました（1 件ずつ判定します）。"
                + "runId={} count={} ids={} reason={}", runId, leftoverIds.size(), leftoverIds, reason);
    }

    /**
     * 1 パスぶんの復旧（**判定と整理 → 投入**の 2 段階）。
     *
     * <p>第 1 段階は 1 枚のスナップショットだけを使い、**実行器へは渡さない**。
     * 実行しない記録を先に閉じてから、第 2 段階で有効な記録を投入する
     * （古い記録が未完了のまま新しい記録が走り出して「前回が終わっていない」で
     * 失われるのを防ぐ）。</p>
     */
    private void review(String reason, Instant now) {
        ScheduleConfigSnapshot snapshot = configService.snapshot();
        Map<Long, BatchExecutionEntity> rows = new LinkedHashMap<>();
        for (Long executionId : new ArrayList<>(leftoverIds)) {
            BatchExecutionEntity row = executionMapper.findById(executionId);
            if (row == null) {
                leftoverIds.remove(executionId);   // 行が無い（消された）→ もう扱わない
                continue;
            }
            rows.put(executionId, row);
        }

        // ---------------------------------------------------------------- 第 1 段階: 判定と整理
        // 判定した「投入待ち」は**メンバーの追跡（pendingDispatches）へ直接入れる**。
        // 局所変数だけに持たせると、途中で照会が失敗して抜けたときに新しく作ったやり直しが
        // 追跡から消える（このプロセスの記録なので遺留検索では二度と出ない＝漏執行になる）。
        Set<String> unresolvedTasks = new LinkedHashSet<>();
        List<BatchExecutionEntity> ordered = new ArrayList<>(rows.values());
        ordered.sort(Comparator
                .comparing((BatchExecutionEntity row) -> plannedAtOf(row),
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(BatchExecutionEntity::getExecutionId));

        for (BatchExecutionEntity row : ordered) {
            long executionId = row.getExecutionId();
            String taskCode = row.getBatchCode();
            if (processRunId.value().equals(row.getRunId())) {
                // **このプロセスが作った実行**（実行中・待機中）。復旧の対象にしない
                // （読み直しの間に増えても、帰属で必ず外れる。二重実行・実行中の行を閉じる事故を防ぐ）
                leftoverIds.remove(executionId);
                pendingDispatches.remove(executionId);
                log.info("このプロセスが作った実行は復旧しません。executionId={} taskCode={} 状態={}",
                        executionId, taskCode, row.getStatus());
                continue;
            }
            String status = row.getStatus();
            if (!BatchExecutionStatus.QUEUED.name().equals(status)
                    && !BatchExecutionStatus.RUNNING.name().equals(status)) {
                leftoverIds.remove(executionId);   // 既に終わっている（他の復旧が閉じた等）
                pendingDispatches.remove(executionId);
                continue;
            }
            if (!catalog.taskCodes().contains(taskCode)) {
                // スケジューラが管理しないタスクは今までどおり閉じるだけ
                if (closeSafely(executionId, BatchExecutionStatus.FAILED, RECOVERY_MESSAGE, taskCode)) {
                    leftoverIds.remove(executionId);
                    processed++;
                } else {
                    unresolvedTasks.add(taskCode);
                }
                continue;
            }

            LocalDateTime plannedAt = plannedAtOf(row);
            SchedulePlanGuard.PlanDecision decision = planGuard.decide(snapshot, taskCode,
                    plannedAt, now, SchedulePlanGuard.Stage.RECOVERY);
            if (decision.deferred()) {
                // 判定できない（設定が読めない等）→ 保留。**同じタスクの投入は次に回す**
                unresolvedTasks.add(taskCode);
                continue;
            }
            if (!decision.allowed()) {
                if (closeNotRunnable(row, plannedAt, decision)) {
                    leftoverIds.remove(executionId);
                    processed++;
                    skipped++;
                } else {
                    unresolvedTasks.add(taskCode);   // 閉じられなかった → 次で再試行（未決として扱う）
                }
                continue;
            }
            if (BatchExecutionStatus.QUEUED.name().equals(status)) {
                // まだ始まっていない → 同じ実行IDで実行し直す。**投入は第 2 段階**
                log.warn("再起動前に確保していた計画実行点を実行し直します（投入は整理のあと）。"
                        + "taskCode={} executionId={} 予定={} configVersion={}",
                        taskCode, executionId, plannedAt, decision.configVersion());
                // 投入待ちは**メンバーの追跡**に入れる（局所変数だけに持たせない）。
                // 遺留リストからはまだ外さない（投入が済むまで「決着していない」）
                pendingDispatches.put(executionId, new PendingDispatch(taskCode, executionId, plannedAt));
                continue;
            }
            // 実行中（結果が分からない）→ 閉じる＋やり直しを 1 トランザクションで作る
            ScheduledTriggerStore.RecoveryResult result;
            try {
                result = triggerStore.recoverRunningExecution(executionId, taskCode,
                        row.getTriggerType() == null ? "C" : row.getTriggerType(),
                        plannedAt, RECOVERY_MESSAGE, RETRY_CODE);
            } catch (RuntimeException cause) {
                // 閉じるのもやり直しの作成も**巻き戻っている**（どちらか片方だけ残らない）。
                // 次のパスでやり直す（未決として扱うので、同じタスクの投入も待つ）
                log.warn("中断した実行の復旧に失敗しました（次のパスでやり直します）。taskCode={} executionId={} "
                        + "reason={}", taskCode, executionId, messageOf(cause));
                unresolvedTasks.add(taskCode);
                continue;
            }
            if (!result.handled()) {
                unresolvedTasks.add(taskCode);   // 他の復旧が処理中 → 次のパスで確認する
                continue;
            }
            processed++;
            if (result.retryExecutionId() != null) {
                // **コミット後**にメンバーの追跡へ入れる（トランザクションの中で投入しない）。
                // このやり直しは**このプロセスの記録**なので「前のプロセスの遺留」検索では
                // 二度と出てこない → ここで入れた追跡が投入までの唯一の持ち主になる。
                // 先に登録してから元の ID を外す（登録前に例外が出ても取りこぼさない順序）
                pendingDispatches.put(result.retryExecutionId(),
                        new PendingDispatch(taskCode, result.retryExecutionId(), plannedAt));
            }
            leftoverIds.remove(executionId);
        }

        // ---------------------------------------------------------------- 第 2 段階: 投入
        // **追跡の複製**を回す（回しながら集合を触らない）。判断できないものは残す。
        int submitted = 0;
        int held = 0;
        for (PendingDispatch pending : new ArrayList<>(pendingDispatches.values())) {
            if (!pendingDispatches.containsKey(pending.executionId())) {
                continue;   // 同じパスで既に外れている（他の行の処理で決着した）
            }
            if (unresolvedTasks.contains(pending.taskCode())) {
                // 同じタスクに未決の遺留が残っている。ここで投入すると業務側の
                // 「前回が終わっていない」でスキップされ、**本来実行すべき記録が失われる**。
                // 追跡に残したまま次に回す（未決が片付けば投入する）
                held++;
                continue;
            }
            DispatchState state = stateOf(pending.executionId());
            if (state == DispatchState.TERMINAL) {
                // 既に実行済み・閉じられている（別の経路が処理した）→ 追跡から外す（二重実行しない）
                pendingDispatches.remove(pending.executionId());
                leftoverIds.remove(pending.executionId());
                continue;
            }
            if (state == DispatchState.UNKNOWN) {
                // **照会できなかった**。「無い」「終わった」と解釈してはいけない →
                // 追跡に残し、既存の退避の仕組みで次のパスでやり直す
                held++;
                continue;
            }
            if (dispatchToExecutor(pending)) {
                // 実行器が引き受けた → ここで追跡から外す（以後の面倒は実行器が見る）
                pendingDispatches.remove(pending.executionId());
                leftoverIds.remove(pending.executionId());
                retried++;
                submitted++;
            } else {
                // 投入できなかった（想定外の例外）→ 追跡に残して次に回す（記録は待機中のまま残る）
                held++;
            }
        }

        // ---------------------------------------------------------------- 決着の判定
        Set<Long> pending = new LinkedHashSet<>(leftoverIds);
        pending.addAll(pendingDispatches.keySet());
        if (pending.isEmpty()) {
            phase = Phase.COMPLETED;
            log.warn("サービス再起動で中断した実行の復旧が完了しました。processed={} retried={} skipped={} reason={}",
                    processed, retried, skipped, reason);
            return;
        }
        attempts++;
        Duration wait = backoff(attempts);
        nextAttemptAt = now.plus(wait);
        log.warn("再起動の復旧に未決の実行が残っています（{} 秒後に判定し直します）。"
                + "未決の遺留={} 投入待ち={} 保留のタスク={} 今回は投入しなかった件数={} 投入した件数={}",
                wait.toSeconds(), leftoverIds.size(), pendingDispatches.size(),
                unresolvedTasks.isEmpty() ? "-" : unresolvedTasks, held, submitted);
    }

    /** 実行器へ渡す（1 回だけ）。想定外の例外は握って false（追跡に残して次のパスで再挑戦）。 */
    private boolean dispatchToExecutor(PendingDispatch pending) {
        try {
            executor.submit(pending.taskCode(), pending.executionId(), pending.plannedAt());
            return true;
        } catch (RuntimeException cause) {
            log.warn("復旧した実行を実行器へ渡せませんでした（次のパスで再挑戦します）。taskCode={} executionId={} "
                    + "reason={}", pending.taskCode(), pending.executionId(), messageOf(cause));
            return false;
        }
    }

    /**
     * その実行記録のいまの状態（投入してよいかの判断に使う）。
     *
     * <p><b>照会の失敗（{@link DispatchState#UNKNOWN}）を「無い」「終わった」と解釈しない</b>。
     * 失敗したら待機中のまま追跡に残し、次のパスでやり直す（取りこぼしを作らない）。</p>
     */
    private DispatchState stateOf(long executionId) {
        BatchExecutionEntity row;
        try {
            row = executionMapper.findById(executionId);
        } catch (RuntimeException cause) {
            log.warn("実行記録の状態を確かめられませんでした（次のパスでやり直します）。executionId={} reason={}",
                    executionId, messageOf(cause));
            return DispatchState.UNKNOWN;
        }
        if (row == null) {
            return DispatchState.TERMINAL;   // 行が無い＝投入しても何も起きない
        }
        return BatchExecutionStatus.QUEUED.name().equals(row.getStatus())
                ? DispatchState.QUEUED : DispatchState.TERMINAL;
    }

    /** 投入してよいかの判断（照会結果）。 */
    private enum DispatchState {
        /** まだ待機中（投入してよい）。 */
        QUEUED,
        /** 実行済み・閉じられている（投入しない。追跡から外す）。 */
        TERMINAL,
        /** 照会できなかった（判断しない。追跡に残す）。 */
        UNKNOWN
    }

    /** 例外を握って閉じる（false = 閉じられなかった＝次のパスで再試行）。 */
    private boolean closeSafely(long executionId, BatchExecutionStatus status, String message, String taskCode) {
        try {
            triggerStore.closeLeftover(executionId, status, message);
            return true;
        } catch (RuntimeException cause) {
            log.warn("中断した実行を閉じられませんでした（次のパスで再試行します）。taskCode={} executionId={} "
                    + "reason={}", taskCode, executionId, messageOf(cause));
            return false;
        }
    }

    /**
     * 実行しないと判定した行を閉じる（待機中は SKIPPED、実行中は結果不明として FAILED）。
     *
     * @return 閉じた（または既に閉じられていた）ら true。閉じるのに失敗したら false（次で再試行）
     */
    private boolean closeNotRunnable(BatchExecutionEntity row, LocalDateTime plannedAt,
                                     SchedulePlanGuard.PlanDecision decision) {
        boolean queued = BatchExecutionStatus.QUEUED.name().equals(row.getStatus());
        String message = queued
                ? SKIPPED_PREFIX + decision.reason()
                : RECOVERY_MESSAGE + "（いまの計画では有効でないため、やり直しません。" + decision.reason() + "）";
        BatchExecutionStatus status = queued ? BatchExecutionStatus.SKIPPED : BatchExecutionStatus.FAILED;
        if (!closeSafely(row.getExecutionId(), status, message, row.getBatchCode())) {
            return false;
        }
        log.warn("中断した実行は実行しません（いまの計画では有効でない）。taskCode={} executionId={} 予定={} "
                + "status={} reason={} configVersion={}", row.getBatchCode(), row.getExecutionId(), plannedAt,
                row.getStatus(), decision.reason(), decision.configVersion());
        return true;
    }

    /** まだ実行器へ渡していない実行（遺留の再投入と、実行中から作ったやり直し）。 */
    private record PendingDispatch(String taskCode, long executionId, LocalDateTime plannedAt) {
    }

    private static Duration backoff(int failures) {
        int index = Math.min(Math.max(1, failures), BACKOFF.length) - 1;
        return BACKOFF[index];
    }

    /** 実行記録の 予定時刻（ISO 形式。空白区切りで入っている行も読めるようにする）。 */
    private static LocalDateTime plannedAtOf(BatchExecutionEntity row) {
        return parse(row.getScheduleTime());
    }

    private static LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), PAYLOAD_FORMAT);
        } catch (RuntimeException cause) {
            try {
                return LocalDateTime.parse(value.trim().replace(' ', 'T'));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    private static String messageOf(Throwable cause) {
        if (cause == null) {
            return "原因不明";
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message.strip();
    }

    /**
     * 復旧の状態（画面・ログ用）。
     *
     * @param phase              いまの段階
     * @param phaseLabel         画面に出す日本語
     * @param runId このプロセスの起動識別子（この値と違う実行だけを遺留として扱う）
     * @param pendingCount       まだ処理が確定していない遺留の数
     * @param processed          処理が確定した数
     * @param skipped            実行せずに閉じた数
     * @param retried            実行し直した数
     */
    public record RecoveryStatus(Phase phase, String phaseLabel, String runId,
                                 int pendingCount, int processed, int skipped, int retried) {
    }
}
