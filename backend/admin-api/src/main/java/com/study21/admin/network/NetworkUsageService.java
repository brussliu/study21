package com.study21.admin.network;

import com.study21.admin.proxy.ProxyServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * インターネット利用の**開始／終了**の業務（batR03 / batR04 の共通部分）。
 *
 * <p>2.0 の batR03 / batR04 と同じ意味にする:</p>
 * <ol>
 *   <li>有効な端末（{@code 状態='1'}）の**端末モード**を一括で切り替える
 *       （終了 → {@code S} 停止 / 開始 → {@code T} 通常）</li>
 *   <li>**ネットワーク制御アプリ**（プロキシ）の稼働を保証する
 *       （2.0 の「batL01 を起動」＝ 2.1 の batS01 / {@link ProxyServerService}。
 *        モードはプロキシが毎リクエスト DB から読むので、書き換えれば次のリクエストから効く）</li>
 *   <li>結果を要約して履歴に残す（通知文は 2.1 では送信できないためログに残す。下の注記）</li>
 * </ol>
 *
 * <p><strong>上書きしない規則</strong>: 計画実行点より**後に端末が更新されている**ときは、
 * 利用者の手動操作（端末コントロールの【一括適用】など）を上書きしないよう切り替えを見送る。
 * サービス停止から復帰したときに、古い計画実行点で手動の判断を壊さないための規則。</p>
 *
 * <p><strong>注記（2.1 で未実装のもの）</strong>:</p>
 * <ul>
 *   <li>2.0 の batR03 は「学習タイマーの停止（{@code stopRunningTimersForInternetShutdown}）」も
 *       行っていた。2.1 のタイマーは**ブラウザ内（localStorage）**でサーバーに状態が無いため
 *       （docs/HOME.md §2・§6）、サーバー側で止める対象が無い。呼び出し側でその旨をログに残す。</li>
 *   <li>2.0 の batR03 は LINE へ通知していたが、2.1 は**LINE 送信そのものが未実装**
 *       （設定キー {@code LINE_MESSAGING_*} だけがある）。ここでは文面を作ってログに残し、
 *       実行履歴のメッセージには要約だけを入れる。送信を実装するときはこの文面をそのまま使える。</li>
 * </ul>
 */
@Service
public class NetworkUsageService {

    /** 端末モード: 停止。 */
    public static final String MODE_STOP = "S";

    /** 端末モード: 通常。 */
    public static final String MODE_NORMAL = "T";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Logger log = LoggerFactory.getLogger(NetworkUsageService.class);

    private final NetTerminalModeMapper terminalMapper;
    private final ProxyServerService proxyServerService;

    public NetworkUsageService(NetTerminalModeMapper terminalMapper, ProxyServerService proxyServerService) {
        this.terminalMapper = terminalMapper;
        this.proxyServerService = proxyServerService;
    }

    /** インターネット利用の**終了**（端末を停止モード S へ）。 */
    public NetworkSwitchResult stop(String taskCode, LocalDateTime plannedAt) {
        return switchMode(taskCode, plannedAt, MODE_STOP, "インターネット利用を終了しました");
    }

    /** インターネット利用の**開始**（端末を通常モード T へ）。 */
    public NetworkSwitchResult start(String taskCode, LocalDateTime plannedAt) {
        return switchMode(taskCode, plannedAt, MODE_NORMAL, "インターネット利用を開始しました");
    }

    private NetworkSwitchResult switchMode(String taskCode, LocalDateTime plannedAt, String mode, String reason) {
        int activeTerminals = terminalMapper.countActiveTerminals();
        Optional<String> skip = manualChangeReason(plannedAt);
        if (skip.isPresent()) {
            log.warn("端末モードの一括切替を見送りました。taskCode={} mode={} reason={}", taskCode, mode, skip.get());
            return new NetworkSwitchResult(false, mode, 0, activeTerminals, null, skip.get(), null);
        }

        int updated = terminalMapper.updateAllTerminalModes(mode, taskCode);
        String proxyMessage = proxyServerService.startIfNeeded();
        String notification = buildNotificationText(reason, plannedAt, mode, updated, proxyMessage);
        log.info("インターネット利用の切替を適用しました。taskCode={} mode={} updated={} proxy={}",
                taskCode, mode, updated, proxyMessage);
        // 2.1 は LINE 送信が未実装のため、送るはずの文面をここに残す（実装したらこの文面を使う）
        log.info("（通知文面・LINE 送信は 2.1 未実装）\n{}", notification);
        return new NetworkSwitchResult(true, mode, updated, activeTerminals, proxyMessage, null, notification);
    }

    /**
     * 手動操作を上書きしないための判定。
     *
     * <p>計画実行点より後に端末が更新されていれば、その更新は利用者の操作なのでここでは切り替えない。
     * **このバッチ自身の更新（{@code batR03} / {@code batR04}）は数えない**
     * （数えると、前回の実行で動いた更新日時を手動操作と誤認して切替を見送ってしまう）。</p>
     *
     * <p>計画実行点が分からないときは判定しない（従来どおり切り替える）。</p>
     */
    private Optional<String> manualChangeReason(LocalDateTime plannedAt) {
        if (plannedAt == null) {
            return Optional.empty();
        }
        LocalDateTime latest = terminalMapper.findLatestManualUpdatedAt();
        if (latest != null && latest.isAfter(plannedAt)) {
            return Optional.of("端末が計画時刻（" + plannedAt.format(TIME_FORMAT) + "）より後に更新されているため、"
                    + "手動操作を上書きしないよう切り替えませんでした（最終更新 "
                    + latest.format(TIME_FORMAT) + "）。");
        }
        return Optional.empty();
    }

    /** 実行履歴に残す 1 行の要約。 */
    public static String summarize(String reason, String mode, int updated, int activeTerminals,
                                   String proxyMessage, String plannedAtLabel) {
        String modeLabel = MODE_STOP.equals(mode) ? "停止モード（S）" : "通常モード（T）";
        return reason + "（実行時刻 " + plannedAtLabel + "、端末制御: " + modeLabel
                + "、更新 " + updated + "/" + activeTerminals + " 台、ネットワーク制御: " + proxyMessage + "）";
    }

    /**
     * 通知の文面（2.0 の batR03 の LINE 文面から、**実行時刻を設定値にする**ように変えたもの）。
     */
    public static String buildNotificationText(String reason, LocalDateTime plannedAt, String mode,
                                               int updated, String proxyMessage) {
        String modeLabel = MODE_STOP.equals(mode) ? "停止モード（S）へ切替" : "通常モード（T）へ切替";
        String time = plannedAt == null ? "-" : plannedAt.format(TIME_FORMAT);
        return """
                【STUDY 2.1 お知らせ】
                %s。

                ・実行時刻：%s
                ・端末制御：%s
                ・更新端末数：%d 台
                ・ネットワーク制御：%s
                """.formatted(reason, time, modeLabel, updated, proxyMessage);
    }

    /**
     * 1 回の切り替えの結果。
     *
     * @param applied        実際に切り替えたか（false のときは {@code skipReason} がある）
     * @param mode           切り替えたモード（'S' / 'T'）
     * @param updatedCount   更新した端末の数
     * @param activeCount    有効な端末の数
     * @param proxyMessage   ネットワーク制御アプリ（プロキシ）の状態メッセージ
     * @param skipReason     切り替えを見送った理由
     * @param notification   通知の文面（2.1 は送信せずログに残す）
     */
    public record NetworkSwitchResult(boolean applied, String mode, int updatedCount, int activeCount,
                                      String proxyMessage, String skipReason, String notification) {
    }
}
