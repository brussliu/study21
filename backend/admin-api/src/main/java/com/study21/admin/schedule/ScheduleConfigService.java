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
     * このタスクの設定を**使える状態にしてから**返す（メモリ優先。無いときだけ DB 托底）。
     *
     * <p>托底は「まとめて 1 回」だけ（複数スレッドが同時に不足しても 1 回。ロック内で再確認）。
     * 退避中は DB を引かない。</p>
     */
    public ScheduleConfigSnapshot ensureTaskConfig(String taskCode) {
        ScheduleConfigSnapshot current = snapshot.get();
        if (current.statusOf(taskCode).usable()) {
            return current;
        }
        if (isBackoffActive() || isFallbackBackoffActive(taskCode)) {
            return current;
        }
        synchronized (refreshLock) {
            ScheduleConfigSnapshot again = snapshot.get();
            if (again.statusOf(taskCode).usable() || isBackoffActive() || isFallbackBackoffActive(taskCode)) {
                return again;   // 他のスレッドが先に読んだ／退避中
            }
            refreshLocked("托底: " + taskCode);
            ScheduleConfigSnapshot loaded = snapshot.get();
            if (!loaded.statusOf(taskCode).usable()) {
                // 読めたが、そのタスクの設定が無い・不正だった。
                // 毎回 DB を引きに行かないよう、タスクごとに退避する
                // （設定を保存すれば refresh が走って退避は消える）
                noteFallbackMiss(taskCode);
            }
            return loaded;
        }
    }

    /** そのタスクの托底が「設定なし・不正」で終わったときの、次の確認までの待ち。 */
    private void noteFallbackMiss(String taskCode) {
        int failures = fallbackMisses.merge(taskCode, 1, Integer::sum);
        fallbackNextCheckAt.put(taskCode, clock.instant().plus(backoff(failures)));
    }

    private boolean isFallbackBackoffActive(String taskCode) {
        Instant next = fallbackNextCheckAt.get(taskCode);
        return next != null && clock.instant().isBefore(next);
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
                // 自分より後に要求された更新が先に発行済み（古い結果で上書きしない）
                log.debug("古いスケジュール設定の更新要求を破棄しました。reason={} sequence={} published={}",
                        reason, sequence, publishedSequence);
                return RefreshResult.discarded(snapshot.get().version());
            }
            return refreshLocked(reason);
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
        return new ScheduleConfigReport(ZONE.getId(), current.version(), current.loadedAt(),
                pendingRefresh, lastRefreshAt, lastRefreshError, nextRetryAt,
                suppressedFailures.get(), tasks);
    }

    // ------------------------------------------------------------------ 内部

    private RefreshResult refreshLocked(String reason) {
        Instant now = clock.instant();
        lastRefreshAt = now;
        try {
            ScheduleConfigLoader.ScheduleSourceData data =
                    loader.load(catalog.requiredSettingKeys(), catalog.taskCodes());
            ScheduleConfigSnapshot built = build(data, now);
            long sequence = requestSequence.get();
            ScheduleConfigSnapshot published = built.next(now,
                    built.tasks(), built.statuses(), null);
            snapshot.set(published);
            publishedSequence = sequence;
            consecutiveFailures = 0;
            nextRetryAt = null;
            pendingRefresh = false;
            lastRefreshError = null;
            suppressedFailures.set(0);
            // 反映できたので、タスクごとの托底の退避も消す
            fallbackMisses.clear();
            fallbackNextCheckAt.clear();
            log.info("バッチのスケジュール設定を反映しました。reason={} version={} {}", reason,
                    published.version(), report().summarize());
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

    private ScheduleConfigSnapshot build(ScheduleConfigLoader.ScheduleSourceData data, Instant loadedAt) {
        Map<String, TaskSchedule> tasks = new LinkedHashMap<>();
        Map<String, TaskConfigStatus> statuses = new LinkedHashMap<>();
        for (ScheduleTaskRule rule : catalog.rules()) {
            Resolved resolved = resolve(rule, data);
            statuses.put(rule.taskCode(), resolved.status());
            resolved.schedule().ifPresent(schedule -> tasks.put(rule.taskCode(), schedule));
        }
        // loadedAt は refresh 側で入れるのでここでは現在の版をそのまま使う
        return new ScheduleConfigSnapshot(snapshot.get().version(), loadedAt, ZONE, tasks, statuses, null);
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
        if (schedule == null) {
            return new ScheduleConfigReport.TaskStatus(taskCode, status.name(), status.label(), null,
                    status.label(), null, null, null, List.of(), null, status.label());
        }
        LocalDateTime next = schedule.nextPointAfter(now);
        return new ScheduleConfigReport.TaskStatus(taskCode, status.name(), status.label(), schedule.enabled(),
                schedule.describe(), schedule.intervalMinutes(), schedule.offsetMinutes(),
                schedule.kind() == ScheduleKind.DAILY ? schedule.dailyTime().toString() : null,
                schedule.pointsOfDay(now.toLocalDate()).stream().map(LocalTime::toString).toList(),
                next, next.format(DATE_TIME_LABEL));
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
