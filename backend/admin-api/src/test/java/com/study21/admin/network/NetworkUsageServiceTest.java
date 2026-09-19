package com.study21.admin.network;

import com.study21.admin.proxy.ProxyServerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * インターネット利用の開始／終了（batR03 / batR04 の共通部分）。
 *
 * <p>2.0 の業務意味を保つこと（端末モードを一括で切り替え、**ネットワーク制御アプリを起動保証**する。
 * DB だけ更新して終わりにしない）と、復帰時に**利用者の手動操作を上書きしない**ことを固定する。</p>
 */
class NetworkUsageServiceTest {

    private NetTerminalModeMapper terminalMapper;
    private ProxyServerService proxyServerService;
    private NetworkUsageService service;

    private static final LocalDateTime PLANNED = LocalDateTime.of(2026, 9, 19, 23, 30);

    @BeforeEach
    void setUp() {
        terminalMapper = mock(NetTerminalModeMapper.class);
        proxyServerService = mock(ProxyServerService.class);
        service = new NetworkUsageService(terminalMapper, proxyServerService);
        when(terminalMapper.countActiveTerminals()).thenReturn(5);
        when(terminalMapper.updateAllTerminalModes(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(5);
        when(proxyServerService.startIfNeeded()).thenReturn("Proxy already running on port 7777");
    }

    @Test
    @DisplayName("終了（batR03）: 端末を停止モード S にして、ネットワーク制御アプリの稼働を保証する")
    void stopSwitchesTerminalsToStopModeAndStartsProxy() {
        NetworkUsageService.NetworkSwitchResult result = service.stop("batR03", PLANNED);

        assertThat(result.applied()).isTrue();
        assertThat(result.mode()).isEqualTo("S");
        assertThat(result.updatedCount()).isEqualTo(5);
        verify(terminalMapper).updateAllTerminalModes("S", "batR03");
        // DB を書き換えるだけで終わらせない（プロキシが動いていないと制御が効かない）
        verify(proxyServerService).startIfNeeded();
        assertThat(result.proxyMessage()).contains("Proxy");
        assertThat(result.notification()).contains("停止モード（S）").contains("2026-09-19 23:30");
    }

    @Test
    @DisplayName("開始（batR04）: 端末を通常モード T に戻す（許可サイトの判定はそのまま効く）")
    void startSwitchesTerminalsToNormalMode() {
        NetworkUsageService.NetworkSwitchResult result = service.start("batR04", PLANNED);

        assertThat(result.applied()).isTrue();
        assertThat(result.mode()).isEqualTo("T");
        verify(terminalMapper).updateAllTerminalModes("T", "batR04");
        verify(proxyServerService).startIfNeeded();
        assertThat(result.notification()).contains("通常モード（T）");
    }

    @Test
    @DisplayName("計画時刻より後に端末が更新されていたら切り替えない（手動操作を上書きしない）")
    void doesNotOverwriteNewerManualChange() {
        when(terminalMapper.findLatestUpdatedAt()).thenReturn(PLANNED.plusMinutes(15));

        NetworkUsageService.NetworkSwitchResult result = service.stop("batR03", PLANNED);

        assertThat(result.applied()).isFalse();
        assertThat(result.updatedCount()).isZero();
        assertThat(result.skipReason())
                .contains("計画時刻（2026-09-19 23:30）より後に更新")
                .contains("手動操作を上書きしない")
                .contains("最終更新 2026-09-19 23:45");
        verify(terminalMapper, never()).updateAllTerminalModes(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(proxyServerService, never()).startIfNeeded();
    }

    @Test
    @DisplayName("計画時刻より前の更新なら切り替える（前回の実行で更新されただけのとき）")
    void appliesWhenTheLastChangeIsOlderThanThePlan() {
        when(terminalMapper.findLatestUpdatedAt()).thenReturn(PLANNED.minusMinutes(10));

        assertThat(service.start("batR04", PLANNED).applied()).isTrue();
        verify(terminalMapper).updateAllTerminalModes("T", "batR04");
    }

    @Test
    @DisplayName("計画時刻が分からないときは判定しない（従来どおり切り替える）")
    void appliesWhenPlannedAtIsUnknown() {
        assertThat(service.stop("batR03", null).applied()).isTrue();
        verify(terminalMapper).updateAllTerminalModes("S", "batR03");
    }

    @Test
    @DisplayName("要約は実行時刻・端末の数・ネットワーク制御の状態を含む（実行時刻をコードに書かない）")
    void summarizeIncludesPlannedTime() {
        String message = NetworkUsageService.summarize("インターネット利用を終了しました", "S", 3, 5,
                "Proxy started on port 7777", "2026-09-19 23:30");
        assertThat(message).contains("2026-09-19 23:30").contains("更新 3/5 台").contains("Proxy started");
    }
}
