package com.study21.admin.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * バッチのスケジュール設定の**メモリ上の唯一の置き場**（Spring のシングルトン）。
 *
 * <p>狙い（利用者の指示）: 30 秒ごとのスケジュール検査で**設定テーブルを引きに行かない**。
 * DB は「起動時」「保存のコミット後」「メモリに無いときの托底」「管理者の再読み込み」でだけ読む。</p>
 *
 * <ul>
 *   <li>スナップショットは不変（{@link ScheduleConfigSnapshot}）で、
 *       {@link AtomicReference} により**丸ごと**差し替える（新しい間隔と古い時刻が混ざらない）。</li>
 *   <li>有効／無効は BAT_バッチコントロール情報 が唯一の正（このクラスはスイッチを持たない）。</li>
 *   <li>版（version）は発行の通し番号。**古い要求の結果が新しい設定を上書きしない**よう、
 *       発行は直列化（{@code synchronized}）したうえで要求の通し番号で守る。</li>
 *   <li>DB を読めないときは**前の有効なスナップショットを残す**（消さない）。托底の失敗は
 *       30 秒 → 60 秒 → 120 秒 → 240 秒 → 300 秒（上限）で再試行し、その間は DB を引かない。
 *       ログも失敗が続く間は 1 回目だけ出す（限頻）。</li>
 *   <li>設定が無い・不正なタスクは {@link TaskConfigStatus#MISSING} / {@link TaskConfigStatus#INVALID}
 *       として**自動実行しない**（コードに隠れた既定値でネット制御を動かさない）。</li>
 * </ul>
 *
 * <p>時計は常に {@link #ZONE}（Asia/Tokyo）。サーバーの OS タイムゾーンには依存しない。</p>
 */
@Service
public class ScheduleConfigService {

    /** スケジュールの時計（固定。OS のタイムゾーンを使わない）。 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    private static final Duration[] BACKOFF = {
            Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(120),
            Duration.ofSeconds(240), Duration.ofSeconds(300)};

    private static final DateTimeFormatter DATE_TIME_LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 画面・ログに出す「次に再確認する時刻」（秒まで出す。30 秒周期なので分だけでは足りない）。 */
    private static final DateTimeFormatter RETRY_LABEL = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Logger log = LoggerFactory.getLogger(ScheduleConfigService.class);

    private final ScheduleRuleCatalog catalog;
    private final ScheduleConfigLoader loader;
    private final Clock clock;

    private final AtomicReference<ScheduleConfigSnapshot> snapshot;
    private final Object refreshLock = new Object();
    /** 更新要求の通し番号（要求した順。発行済みの番号より古い結果は捨てる）。 */
    private final AtomicLong requestSequence = new AtomicLong();
    private volatile long publishedSequence;
    private volatile Instant lastRefreshAt;
    private volatile String lastRefreshError;
    private volatile boolean pendingRefresh;
    private volatile int consecutiveFailures;
    private volatile Instant nextRetryAt;
    private final AtomicLong suppressedFailures = new AtomicLong();
    /** タスクごとの托底の失敗（設定が無い・不正だった）回数と、次の確認時刻。 */
    private final Map<String, Integer> fallbackMisses = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Instant> fallbackNextCheckAt = new java.util.concurrent.ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public ScheduleConfigService(ScheduleRuleCatalog catalog, ScheduleConfigLoader loader) {
        this(catalog, loader, Clock.system(ScheduleConfigService.ZONE));
    }

    /** テスト用（時計を差し替える）。 */
    public ScheduleConfigService(ScheduleRuleCatalog catalog, ScheduleConfigLoader loader, Clock clock) {
        this.catalog = catalog;
        this.loader = loader;
        this.clock = clock;
        this.snapshot = new AtomicReference<>(ScheduleConfigSnapshot.empty(ZONE, catalog.taskCodes()));
    }

    /**
     * 起動時に 1 回読む（アプリが受け付け可能になってから）。
     *
     * <p>失敗しても起動は止めない（前のスナップショット＝この場合は空のまま）。
     * 30 秒スケジューラが托底で再試行する。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        RefreshResult result = refresh("起動時");
        if (result.published()) {
            log.info("バッチのスケジュール設定を読み込みました。version={} {}", result.version(), report().summarize());
        } else {
            log.warn("起動時のスケジュール設定の読み込みに失敗しました（30 秒ごとの托底で再試行します）。reason={}",
                    result.error());
        }
    }

    /**
     * いまメモリで効いているスナップショット（**DB を引かない**）。
     *
     * <p>スケジュール検査は必ずこれを 1 回だけ呼び、その 1 枚だけで判断する。</p>
     */
    public ScheduleConfigSnapshot snapshot() {
        return snapshot.get();
    }

    /**
     * メモリに無い・不正なタスクがあれば、托底を**1 回だけ**試して最新のスナップショットを返す。
     *
     * <p>托底は「足りないタスクぶんをまとめて 1 回」だけ（1 回の読み込みで**全タスクの状態**が
     * 更新される）。同じ回・同時に何本要求されても読み込みは 1 回（ロック内で再確認）。
     * タスクごとの退避中は DB を引かない。</p>
     *
     * <p>スケジューラは 1 回の検査でこれを 1 回だけ呼ぶ（タスクごとに呼ばない）。</p>
     */
    public ScheduleConfigSnapshot ensureUsableConfig() {
        ScheduleConfigSnapshot current = snapshot.get();
        if (allUsable(current)) {
            return current;   // 設定がそろっている → メモリだけ（DB を引かない）
        }
        if (isBackoffActive()) {
            return current;   // DB を読めなかったときの退避中
        }
        if (!anyFallbackDue(current)) {
            return current;   // 足りないタスクはすべて「設定なし・不正」の退避中
        }
        long sequence = requestSequence.incrementAndGet();
        synchronized (refreshLock) {
            ScheduleConfigSnapshot again = snapshot.get();
            if (allUsable(again) || isBackoffActive() || !anyFallbackDue(again)) {
                return again;   // 他のスレッドが先に読んだ／退避中
            }
            refreshLocked("托底: 設定が足りません", sequence);
            return snapshot.get();
        }
    }

    /**
     * そのタスクの設定を使える状態にしてから返す（{@link #ensureUsableConfig()} と同じ規則）。
     *
     * <p>托底は 1 回の読み込みで全タスクを見るため、結果は他のタスクにも同時に反映される。</p>
     */
    public ScheduleConfigSnapshot ensureTaskConfig(String taskCode) {
        return ensureUsableConfig();
    }

    /** 4 タスクすべての設定が使えるか。 */
    private boolean allUsable(ScheduleConfigSnapshot current) {
        for (String taskCode : catalog.taskCodes()) {
            if (!current.statusOf(taskCode).usable()) {
                return false;
            }
        }
        return true;
    }

    /** 托底を試す時期が来ている「使えないタスク」があるか（タスクごとの退避を見る）。 */
    private boolean anyFallbackDue(ScheduleConfigSnapshot current) {
        Instant now = clock.instant();
        for (String taskCode : catalog.taskCodes()) {
            if (current.statusOf(taskCode).usable()) {
                continue;
            }
            Instant next = fallbackNextCheckAt.get(taskCode);
            if (next == null || !now.isBefore(next)) {
                return true;
            }
        }
        return false;
    }

    /** そのタスクの托底が「設定なし・不正」で終わったときの、次の確認までの待ち。 */
    private void noteFallbackMiss(String taskCode) {
        int failures = fallbackMisses.merge(taskCode, 1, Integer::sum);
        fallbackNextCheckAt.put(taskCode, clock.instant().plus(backoff(failures)));
    }

    /**
     * 読み込みが成功したあとの、タスクごとの退避の更新。
     *
     * <p><b>使えるようになったタスクだけ**退避を消す</b>。</b>まだ設定が無い・不正なタスクの回数は
     * **残して進める**（消してしまうと退避が 30 秒に戻り、30 秒ごとに DB を引き続ける）。
     * これが「DB を読めなかった」ときの退避（{@link #isBackoffActive()}）とは別物である点に注意。</p>
     */
    private void updateFallbackStateAfterLoad(ScheduleConfigSnapshot published) {
        for (String taskCode : catalog.taskCodes()) {
            if (published.statusOf(taskCode).usable()) {
                fallbackMisses.remove(taskCode);
                fallbackNextCheckAt.remove(taskCode);
            } else {
                noteFallbackMiss(taskCode);
            }
        }
    }

    /**
     * 設定を読み直してメモリに反映する（保存後・有効／無効の切替後・管理者の再読み込み・自動再試行）。
     *
     * <p>失敗しても**前の有効なスナップショットを残す**（DB は既にコミット済みなので巻き戻さない。
     * 画面には「保存済み・実行設定への反映待ち」として見せる）。</p>
     */
    public RefreshResult refresh(String reason) {
        long sequence = requestSequence.incrementAndGet();
        synchronized (refreshLock) {
            if (sequence <= publishedSequence) {
                // 自分より後に要求された更新が先に発行済み（古い結果で上書きしない）。
                // ここで捨てた要求は**読み込みもしない**ので「発行済み」にはしない
                log.debug("古いスケジュール設定の更新要求を破棄しました。reason={} sequence={} published={}",
                        reason, sequence, publishedSequence);
                return RefreshResult.discarded(snapshot.get().version());
            }
            return refreshLocked(reason, sequence);
        }
    }

    /** 退避の期限が来ていて、まだメモリに反映できていない更新があるときだけ読み直す。 */
    public boolean retryPendingIfDue() {
        if (!pendingRefresh || isBackoffActive()) {
            return false;
        }
        RefreshResult result = refresh("自動再試行");
        return result.published();
    }

    /** 次の再試行までの残り（デバッグ・画面用）。退避中でなければ空。 */
    public Optional<Duration> remainingBackoff() {
        Instant retryAt = nextRetryAt;
        if (retryAt == null) {
            return Optional.empty();
        }
        Duration remaining = Duration.between(clock.instant(), retryAt);
        return remaining.isNegative() ? Optional.empty() : Optional.of(remaining);
    }

    /** そのタスクの托底を次に試すまでの残り（設定が無い・不正のときの退避）。 */
    public Optional<Duration> remainingFallbackBackoff(String taskCode) {
        Instant next = fallbackNextCheckAt.get(taskCode);
        if (next == null) {
            return Optional.empty();
        }
        Duration remaining = Duration.between(clock.instant(), next);
        return remaining.isNegative() ? Optional.empty() : Optional.of(remaining);
    }

    /** 画面・API に見せる状態（秘密は含まない）。 */
    public ScheduleConfigReport report() {
        ScheduleConfigSnapshot current = snapshot.get();
        LocalDateTime now = LocalDateTime.now(clock);
        List<ScheduleConfigReport.TaskStatus> tasks = catalog.taskCodes().stream()
                .map(taskCode -> toTaskStatus(current, taskCode, now))
                .toList();
        List<String> missing = tasks.stream()
                .filter(task -> !TaskConfigStatus.valueOf(task.status()).usable())
                .map(ScheduleConfigReport.TaskStatus::taskCode)
                .toList();
        return new ScheduleConfigReport(ZONE.getId(), current.version(), current.loadedAt(),
                pendingRefresh, lastRefreshAt, lastRefreshError, nextRetryAt,
                suppressedFailures.get(), configMissingMessage(missing), tasks);
    }

    /**
     * 「設定が無い・不正で自動実行できないタスク」の案内（無ければ null）。
     *
     * <p>次にいつ確認するかも出す（退避で待っている間に「なぜ動かないのか」が分かるように）。</p>
     */
    private String configMissingMessage(List<String> missing) {
        if (missing.isEmpty()) {
            return null;
        }
        Optional<Instant> next = missing.stream()
                .map(fallbackNextCheckAt::get)
                .filter(java.util.Objects::nonNull)
                .min(Instant::compareTo);
        String when = next.map(instant -> "次に " + LocalDateTime.ofInstant(instant, ZONE).format(RETRY_LABEL)
                + " に再確認します。").orElse("次の検査で再確認します。");
        return "実行設定が無い・不正なため自動実行しないバッチがあります（" + String.join("、", missing) + "）。" + when;
    }

    /** 読み込み結果のログ（**限頻**。設定が無いタスクは退避の段階ごとに 1 行だけ出す）。 */
    private void logRefreshResult(String reason, ScheduleConfigSnapshot published) {
        List<String> unusable = new ArrayList<>();
        int maxFailures = 0;
        for (String taskCode : catalog.taskCodes()) {
            TaskConfigStatus status = published.statusOf(taskCode);
            if (status.usable()) {
                continue;
            }
            unusable.add(taskCode + "（" + status.label() + "）");
            maxFailures = Math.max(maxFailures, fallbackMisses.getOrDefault(taskCode, 0));
        }
        if (unusable.isEmpty()) {
            log.info("バッチのスケジュール設定を反映しました。reason={} version={} {}", reason,
                    published.version(), report().summarize());
            return;
        }
        String message = "バッチのスケジュール設定を反映しましたが、自動実行できないタスクがあります。"
                + "reason={} version={} tasks={} 設定を確認してください（退避で自動的に再確認します）。";
        if (maxFailures <= BACKOFF.length) {
            // 退避の段階が変わるたびに 1 行（毎回の検査で同じことを出さない）
            log.warn(message, reason, published.version(), unusable);
        } else {
            log.debug(message + " failures={}", reason, published.version(), unusable, maxFailures);
        }
    }

    // ------------------------------------------------------------------ 内部

    private RefreshResult refreshLocked(String reason, long sequence) {
        Instant now = clock.instant();
        lastRefreshAt = now;
        try {
            ScheduleConfigLoader.ScheduleSourceData data =
                    loader.load(catalog.requiredSettingKeys(), catalog.taskCodes());
            ScheduleConfigSnapshot built = build(data, now);
            ScheduleConfigSnapshot published = built.next(now,
                    built.tasks(), built.statuses(), built.planVersions(), null);
            snapshot.set(published);
            // **この要求自身の番号**を発行済みにする。requestSequence.get() を使うと、
            // まだ読み込んでいない後続の要求まで「発行済み」になり、
            // その要求が破棄されて**最新の設定が反映されない**（取りこぼし）
            publishedSequence = sequence;
            consecutiveFailures = 0;
            nextRetryAt = null;
            pendingRefresh = false;
            lastRefreshError = null;
            suppressedFailures.set(0);
            // タスクごとの退避は「使えるようになったタスクだけ」消す（設定が無いタスクの回数は残す）
            updateFallbackStateAfterLoad(published);
            logRefreshResult(reason, published);
            return RefreshResult.published(published.version());
        } catch (ScheduleConfigLoader.ScheduleConfigLoadException cause) {
            return onFailure(reason, cause);
        } catch (RuntimeException cause) {
            return onFailure(reason, cause);
        }
    }

    private RefreshResult onFailure(String reason, RuntimeException cause) {
        consecutiveFailures++;
        nextRetryAt = clock.instant().plus(backoff(consecutiveFailures));
        pendingRefresh = true;
        lastRefreshError = messageOf(cause);
        // 前の有効なスナップショットは残す（タスクはその設定のまま動き続ける）
        if (consecutiveFailures == 1) {
            log.warn("バッチのスケジュール設定を反映できませんでした（前の設定を保持して再試行します）。reason={} [{}]",
                    reason, lastRefreshError, cause);
        } else {
            long suppressed = suppressedFailures.incrementAndGet();
            log.debug("バッチのスケジュール設定の反映に続けて失敗しています。reason={} failures={} suppressed={} [{}]",
                    reason, consecutiveFailures, suppressed, lastRefreshError);
        }
        return RefreshResult.failed(snapshot.get().version(), lastRefreshError, nextRetryAt);
    }

    private boolean isBackoffActive() {
        Instant retryAt = nextRetryAt;
        return retryAt != null && clock.instant().isBefore(retryAt);
    }

    private static Duration backoff(int failures) {
        int index = Math.min(Math.max(1, failures), BACKOFF.length) - 1;
        return BACKOFF[index];
    }

    /**
     * 読んだ値からスナップショットを作る（**何も書かない**）。
     *
     * <p><b>設定の適用時刻（effectiveFrom）</b>は DB が正（{@code BAT_スケジュール状態情報}）。
     * 利用者が実行時刻・間隔・ずらし・有効／無効を変えたとき、**設定値の保存と同じトランザクション**で
     * 適用時刻と計画バージョンが書かれている（{@code ScheduleTimingRecorder}）。
     * ここ（キャッシュの読み込み）で補って書くと、書けたかどうか分からないまま
     * 「新しい設定＋古い適用時刻」が残り、過去の計画実行点を実行しかねない。</p>
     *
     * <p>サービス再起動のときは DB の適用時刻をそのまま使う（＝「利用者の設定変更」と
     * 「再起動の補執行」を区別する。再起動では適用時刻を進めない）。</p>
     */
    private ScheduleConfigSnapshot build(ScheduleConfigLoader.ScheduleSourceData data, Instant loadedAt) {
        Map<String, TaskSchedule> tasks = new LinkedHashMap<>();
        Map<String, TaskConfigStatus> statuses = new LinkedHashMap<>();
        Map<String, Long> planVersions = new LinkedHashMap<>();
        ScheduleConfigSnapshot previous = snapshot.get();
        boolean firstLoad = !previous.loadedOnce();
        LocalDateTime nowLocal = LocalDateTime.ofInstant(loadedAt, ZONE);

        for (ScheduleTaskRule rule : catalog.rules()) {
            String taskCode = rule.taskCode();
            Resolved resolved = resolve(rule, data);
            statuses.put(taskCode, resolved.status());
            planVersions.put(taskCode, data.planVersions().getOrDefault(taskCode, 0L));
            if (resolved.schedule().isEmpty()) {
                continue;
            }
            TaskSchedule schedule = resolved.schedule().orElseThrow();
            TaskSchedule before = previous.taskOf(taskCode).orElse(null);
            LocalDateTime dbEffective = data.configEffectiveFrom().get(taskCode);
            LocalDateTime effectiveFrom;
            if (firstLoad) {
                // 起動時: DB に保存されている適用時刻をそのまま使う（再起動で過去の点を実行しない）
                effectiveFrom = dbEffective;
            } else if (before != null && before.sameTiming(schedule)) {
                // 実行設定は変わっていない → DB とメモリの**遅い方**（安全側。過去の点を実行しない）
                effectiveFrom = laterOf(dbEffective, before.effectiveFrom());
            } else if (data.planVersions().getOrDefault(taskCode, 0L) > previous.planVersionOf(taskCode)
                    && dbEffective != null) {
                // **保存トランザクションが一緒に書いた適用時刻**（設定値と同じ版）を使う
                effectiveFrom = dbEffective;
            } else {
                // 設定は変わったのに適用時刻が記録されていない＝アプリの保存経路以外で変わった
                // （DB を直接書き換えた等）。記録はせず、**メモリだけで**「いまから」にする
                effectiveFrom = nowLocal;
                log.warn("実行設定がアプリの保存経路以外で変更されました。適用時刻を記録できないため、"
                        + "いまから新しい設定として扱います（次の保存で記録されます）。taskCode={}", taskCode);
            }
            tasks.put(taskCode, schedule.withEffectiveFrom(effectiveFrom));
        }
        // loadedAt は refresh 側で入れるのでここでは現在の版をそのまま使う
        return new ScheduleConfigSnapshot(previous.version(), loadedAt, ZONE, tasks, statuses, planVersions, null);
    }

    /** 2 つのうち遅い方（null は「制限なし」なので、値がある方を採る）。 */
    private static LocalDateTime laterOf(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }

    private Resolved resolve(ScheduleTaskRule rule, ScheduleConfigLoader.ScheduleSourceData data) {
        boolean enabled = Boolean.TRUE.equals(data.enabledByTask().get(rule.taskCode()));
        try {
            if (rule.kind() == ScheduleKind.DAILY) {
                String raw = data.settings().get(rule.timeSettingKey());
                if (raw == null || raw.isBlank()) {
                    return Resolved.missing();
                }
                LocalTime time = LocalTime.parse(raw.trim());
                return Resolved.loaded(TaskSchedule.daily(rule.taskCode(), enabled, time));
            }
            String rawInterval = data.settings().get(rule.intervalSettingKey());
            String rawOffset = data.settings().get(rule.offsetSettingKey());
            if (rawInterval == null || rawInterval.isBlank() || rawOffset == null || rawOffset.isBlank()) {
                return Resolved.missing();
            }
            int interval = Integer.parseInt(rawInterval.trim());
            int offset = Integer.parseInt(rawOffset.trim());
            if (!ScheduleRuleCatalog.INTERVAL_CHOICES.contains(interval) || offset < 0 || offset >= interval) {
                return Resolved.invalid("実行間隔=" + interval + " 分 / ずらし=" + offset + " 分");
            }
            return Resolved.loaded(TaskSchedule.interval(rule.taskCode(), enabled, interval, offset));
        } catch (RuntimeException cause) {
            return Resolved.invalid(messageOf(cause));
        }
    }

    private ScheduleConfigReport.TaskStatus toTaskStatus(ScheduleConfigSnapshot current, String taskCode,
                                                         LocalDateTime now) {
        TaskConfigStatus status = current.statusOf(taskCode);
        TaskSchedule schedule = current.tasks().get(taskCode);
        int failures = fallbackMisses.getOrDefault(taskCode, 0);
        Instant nextCheck = fallbackNextCheckAt.get(taskCode);
        String fallback = status.usable() ? null : fallbackMessage(status, failures, nextCheck);
        long planVersion = current.planVersionOf(taskCode);
        if (schedule == null) {
            return new ScheduleConfigReport.TaskStatus(taskCode, status.name(), status.label(), null,
                    status.label(), null, null, null, List.of(), null, status.label(), null,
                    failures, nextCheck, fallback, planVersion);
        }
        // 画面の「次回実行時刻」は**スケジューラが実際に実行する点**と同じ規則で出す
        // （設定の適用時刻より前の点は実行しないため、そこは飛ばす）
        LocalDateTime next = schedule.nextRunnablePointAfter(now);
        return new ScheduleConfigReport.TaskStatus(taskCode, status.name(), status.label(), schedule.enabled(),
                schedule.describe(), schedule.intervalMinutes(), schedule.offsetMinutes(),
                schedule.kind() == ScheduleKind.DAILY ? schedule.dailyTime().toString() : null,
                schedule.pointsOfDay(now.toLocalDate()).stream().map(LocalTime::toString).toList(),
                next, next.format(DATE_TIME_LABEL),
                schedule.effectiveFrom() == null ? null : schedule.effectiveFrom().format(DATE_TIME_LABEL),
                failures, nextCheck, fallback, planVersion);
    }

    /** タスク 1 件の「動かない理由と次にいつ確認するか」。 */
    private String fallbackMessage(TaskConfigStatus status, int failures, Instant nextCheck) {
        if (nextCheck == null) {
            return "設定が" + (status == TaskConfigStatus.MISSING ? "無い" : "不正") + "ため自動実行しません。";
        }
        return "設定が" + (status == TaskConfigStatus.MISSING ? "無い" : "不正")
                + "ため自動実行しません（" + failures + " 回連続。次に "
                + LocalDateTime.ofInstant(nextCheck, ZONE).format(RETRY_LABEL) + " に再確認します）。";
    }

    private static String messageOf(Throwable cause) {
        if (cause == null) {
            return "原因不明";
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message.strip();
    }

    /** 解決の結果（スケジュール or 状態）。 */
    private record Resolved(Optional<TaskSchedule> schedule, TaskConfigStatus status) {
        static Resolved loaded(TaskSchedule schedule) {
            return new Resolved(Optional.of(schedule), TaskConfigStatus.LOADED);
        }

        static Resolved missing() {
            return new Resolved(Optional.empty(), TaskConfigStatus.MISSING);
        }

        static Resolved invalid(String reason) {
            log.warn("バッチのスケジュール設定が不正です: {}", reason);
            return new Resolved(Optional.empty(), TaskConfigStatus.INVALID);
        }
    }

    /**
     * 設定の反映結果。
     *
     * @param published 反映できたか
     * @param version   反映できたときの版（できなければ今の版）
     * @param error     失敗の理由（成功なら null）
     * @param nextRetryAt 失敗したときの次の再試行予定
     */
    public record RefreshResult(boolean published, long version, String error, Instant nextRetryAt) {
        static RefreshResult published(long version) {
            return new RefreshResult(true, version, null, null);
        }

        static RefreshResult failed(long version, String error, Instant nextRetryAt) {
            return new RefreshResult(false, version, error, nextRetryAt);
        }

        static RefreshResult discarded(long version) {
            return new RefreshResult(false, version, null, null);
        }
    }
}
