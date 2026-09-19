package com.study21.admin.network;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchTaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * batR03「インターネット利用終了」のハンドラ。
 *
 * <p>2.0 の {@code BatR03Task} と同じ意味: 学習タイマーを止め（2.1 はブラウザ内なので対象外。
 * {@link NetworkUsageService} の注記）、**有効な端末を停止モード（S）へ一括切替**し、
 * ネットワーク制御アプリ（プロキシ）の稼働を保証して、結果を履歴に残す。</p>
 *
 * <p>実行時刻は設定（{@code NET_CONTROL_END_TIME}）から決まる。**このクラスに時刻を書かない**
 * （2.0 は 23:30 が説明文と通知文に固定されていた）。</p>
 */
@Component
public class NetworkStopBatchHandler implements BatchTaskHandler {

    /** バッチコード。 */
    public static final String BATCH_CODE = "batR03";

    /** 追加の必須設定は無い（実行時刻は {@code NET_CONTROL_END_TIME} をスケジューラが読む）。 */
    public static final List<com.study21.admin.setting.SettingRequirement> REQUIRED_SETTINGS = List.of();

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Logger log = LoggerFactory.getLogger(NetworkStopBatchHandler.class);

    private final NetworkUsageService networkUsageService;

    public NetworkStopBatchHandler(NetworkUsageService networkUsageService) {
        this.networkUsageService = networkUsageService;
    }

    @Override
    public String taskCode() {
        return BATCH_CODE;
    }

    @Override
    public String execute(BatchExecutionEntity execution) {
        LocalDateTime plannedAt = ScheduleTimeParser.parse(execution.getScheduleTime());
        String plannedAtLabel = plannedAt == null ? "-" : plannedAt.format(TIME_FORMAT);

        // 2.0 の stopRunningTimersForInternetShutdown に当たる処理。2.1 のタイマーは
        // ブラウザ内（localStorage）なので、サーバー側で止める対象が無い
        log.info("学習タイマーの停止は 2.1 では対象外です（タイマーはブラウザ内。docs/HOME.md §2）。taskCode={}",
                BATCH_CODE);

        NetworkUsageService.NetworkSwitchResult result =
                networkUsageService.stop(BATCH_CODE, plannedAt);
        if (!result.applied()) {
            return result.skipReason();
        }
        return NetworkUsageService.summarize("インターネット利用を終了しました", result.mode(),
                result.updatedCount(), result.activeCount(), result.proxyMessage(), plannedAtLabel);
    }
}
