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
 * batR04「インターネット利用開始」のハンドラ。
 *
 * <p>2.0 の {@code BatR04Task} と同じ意味: **有効な端末を通常モード（T）へ一括切替**し、
 * ネットワーク制御アプリ（プロキシ）の稼働を保証する。2.0 と同じく通知は送らない。</p>
 *
 * <p>通常モード（T）は**許可サイトの一覧で判定されるモード**なので、開始しても他のネット制限
 * （サイトの承認・区分）は迂回されない（{@code ProxyAccessDecision#isSiteKindAllowedForTerminalMode}）。
 * 停止モード（S）・自由（J）・ゲーム（G）のような「判定を飛ばす」モードにはしない。</p>
 */
@Component
public class NetworkStartBatchHandler implements BatchTaskHandler {

    /** バッチコード。 */
    public static final String BATCH_CODE = "batR04";

    /** 追加の必須設定は無い（実行時刻は {@code NET_CONTROL_START_TIME} をスケジューラが読む）。 */
    public static final List<com.study21.admin.setting.SettingRequirement> REQUIRED_SETTINGS = List.of();

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Logger log = LoggerFactory.getLogger(NetworkStartBatchHandler.class);

    private final NetworkUsageService networkUsageService;

    public NetworkStartBatchHandler(NetworkUsageService networkUsageService) {
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
        log.info("インターネット利用開始を実行します。executionId={} plannedAt={}",
                execution.getExecutionId(), plannedAtLabel);

        NetworkUsageService.NetworkSwitchResult result =
                networkUsageService.start(BATCH_CODE, plannedAt);
        if (!result.applied()) {
            return result.skipReason();
        }
        return NetworkUsageService.summarize("インターネット利用を開始しました", result.mode(),
                result.updatedCount(), result.activeCount(), result.proxyMessage(), plannedAtLabel);
    }
}
