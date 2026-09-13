package com.study21.admin.proxy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * プロキシのアクセス可否判定（{@link ProxyAccessDecision}）の業務ルール。
 *
 * <p>2.0（com.study2.proxy.service.ProxyServerService#evaluateAccess）の判定順と拒否理由を
 * 固定する。Netty も Spring も DB も使わない純粋なテストなので、プロキシは起動しない。</p>
 *
 * <p>端末モードと許可区分の関係（設計書
 * database/サイト管理/NET_サイト管理・端末コントロール設計.md）:</p>
 * <ul>
 *   <li>T（通常）: STUDY・NORMAL</li>
 *   <li>K（休憩）: STUDY・NORMAL・BREAK</li>
 *   <li>B（勉強）: STUDY のみ</li>
 *   <li>S（停止）: すべて拒否</li>
 *   <li>J（自由）・G（ゲーム）: 判定せずすべて許可</li>
 * </ul>
 */
class ProxyAccessDecisionTest {

    private static final String CLIENT_IP = "192.168.0.92";
    private static final String HOST = "www.youtube.com";

    private static ProxySiteMapper.ApprovedSite site(String siteUrl, String hostName,
                                                    String judgeMethodCode, String kindCode) {
        return new ProxySiteMapper.ApprovedSite(siteUrl, hostName, judgeMethodCode, kindCode);
    }

    private static ProxyAccessDecision decide(String host, String terminalMode,
                                              ProxySiteMapper.ApprovedSite... sites) {
        return ProxyAccessDecision.decide(CLIENT_IP, host, terminalMode, List.of(sites));
    }

    @Test
    void rejectsWhenClientIpIsUnavailable() {
        ProxyAccessDecision decision = ProxyAccessDecision.decide(null, HOST, "T", List.of());

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReason()).isEqualTo(ProxyAccessDecision.REASON_IP_UNAVAILABLE);
        assertThat(decision.getTerminalMode()).isNull();
    }

    @Test
    void rejectsWhenTerminalIsNotRegistered() {
        // 端末コントロール情報に IP が無い（Mapper は null を返す）
        ProxyAccessDecision decision = decide(HOST, null);

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReason()).isEqualTo(ProxyAccessDecision.REASON_TERMINAL_UNREGISTERED);
        assertThat(decision.getTerminalMode()).isNull();
    }

    @Test
    void stoppedModeRejectsEveryRequest() {
        // 許可サイトが一致していても、ホストが取れなくても停止モードは拒否
        ProxyAccessDecision withSite = decide(HOST, "S", site("youtube.com", "youtube.com", "SUFFIX", "NORMAL"));
        ProxyAccessDecision withoutHost = decide(null, "S");

        assertThat(withSite.isAllowed()).isFalse();
        assertThat(withSite.getReason()).isEqualTo(ProxyAccessDecision.REASON_STOPPED);
        assertThat(withSite.getTerminalMode()).isEqualTo("S");
        assertThat(withoutHost.getReason()).isEqualTo(ProxyAccessDecision.REASON_STOPPED);
    }

    @Test
    void freeModeAllowsEveryRequestWithoutSiteCheck() {
        // J（自由）は ホストを見る前に許可する（2.0 と同じ順序）
        ProxyAccessDecision decision = decide(null, "J");

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getReason()).isNull();
        assertThat(decision.getTerminalMode()).isEqualTo("J");
    }

    @Test
    void gameModeAllowsEveryRequestWithoutSiteCheck() {
        ProxyAccessDecision decision = decide("unknown.example.com", "G");

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getTerminalMode()).isEqualTo("G");
    }

    @Test
    void normalModeAllowsStudyAndNormalKindOnly() {
        assertThat(decide(HOST, "T", site("youtube.com", "youtube.com", "SUFFIX", "STUDY")).isAllowed()).isTrue();
        assertThat(decide(HOST, "T", site("youtube.com", "youtube.com", "SUFFIX", "NORMAL")).isAllowed()).isTrue();

        ProxyAccessDecision breakKind = decide(HOST, "T", site("youtube.com", "youtube.com", "SUFFIX", "BREAK"));
        ProxyAccessDecision gameKind = decide(HOST, "T", site("youtube.com", "youtube.com", "SUFFIX", "GAME"));

        assertThat(breakKind.isAllowed()).isFalse();
        assertThat(breakKind.getReason()).isEqualTo(ProxyAccessDecision.REASON_SITE_NOT_ALLOWED);
        assertThat(breakKind.getTerminalMode()).isEqualTo("T");
        assertThat(gameKind.isAllowed()).isFalse();
        assertThat(gameKind.getReason()).isEqualTo(ProxyAccessDecision.REASON_SITE_NOT_ALLOWED);
    }

    @Test
    void breakModeAllowsStudyNormalAndBreakKindOnly() {
        assertThat(decide(HOST, "K", site("youtube.com", "youtube.com", "SUFFIX", "STUDY")).isAllowed()).isTrue();
        assertThat(decide(HOST, "K", site("youtube.com", "youtube.com", "SUFFIX", "NORMAL")).isAllowed()).isTrue();
        assertThat(decide(HOST, "K", site("youtube.com", "youtube.com", "SUFFIX", "BREAK")).isAllowed()).isTrue();

        ProxyAccessDecision gameKind = decide(HOST, "K", site("youtube.com", "youtube.com", "SUFFIX", "GAME"));
        assertThat(gameKind.isAllowed()).isFalse();
        assertThat(gameKind.getReason()).isEqualTo(ProxyAccessDecision.REASON_SITE_NOT_ALLOWED);
        assertThat(gameKind.getTerminalMode()).isEqualTo("K");
    }

    @Test
    void studyModeAllowsStudyKindOnly() {
        assertThat(decide(HOST, "B", site("youtube.com", "youtube.com", "SUFFIX", "STUDY")).isAllowed()).isTrue();

        assertThat(decide(HOST, "B", site("youtube.com", "youtube.com", "SUFFIX", "NORMAL")).isAllowed()).isFalse();
        assertThat(decide(HOST, "B", site("youtube.com", "youtube.com", "SUFFIX", "BREAK")).isAllowed()).isFalse();
        assertThat(decide(HOST, "B", site("youtube.com", "youtube.com", "SUFFIX", "GAME")).isAllowed()).isFalse();
    }

    @Test
    void rejectsWhenRequestHostIsUnknown() {
        ProxyAccessDecision decision = decide(null, "T", site("youtube.com", "youtube.com", "SUFFIX", "STUDY"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReason()).isEqualTo(ProxyAccessDecision.REASON_HOST_UNKNOWN);
        assertThat(decision.getTerminalMode()).isEqualTo("T");
    }

    @Test
    void rejectsWhenNoApprovedSiteMatches() {
        ProxyAccessDecision decision = decide("unknown.example.com", "T",
                site("youtube.com", "youtube.com", "SUFFIX", "STUDY"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReason()).isEqualTo(ProxyAccessDecision.REASON_SITE_NOT_ALLOWED);

        ProxyAccessDecision noSites = decide(HOST, "T");
        assertThat(noSites.isAllowed()).isFalse();
        assertThat(noSites.getReason()).isEqualTo(ProxyAccessDecision.REASON_SITE_NOT_ALLOWED);
    }

    @Test
    void matchesBothSiteUrlAndHostNameColumns() {
        // 2.1 の判定は サイトURL と ホスト名 の両方を候補にする（サイト名称は使わない）
        ProxySiteMapper.ApprovedSite site = site("https://www.youtube.com/watch", "youtube.com", "SUFFIX", "STUDY");

        assertThat(decide("www.youtube.com", "T", site).isAllowed()).isTrue();  // サイトURL で一致
        assertThat(decide("youtube.com", "T", site).isAllowed()).isTrue();      // ホスト名 で一致
        assertThat(decide("m.youtube.com", "T", site).isAllowed()).isTrue();    // 末尾一致
    }

    @Test
    void appliesJudgeMethodCodeForEachKind() {
        assertThat(decide("dmm-eikaiwa.example.com", "T",
                site("dmm-eikaiwa", "dmm-eikaiwa", "PREFIX", "STUDY")).isAllowed()).isTrue();
        assertThat(decide("dmm-eikaiwa.example.com", "T",
                site("dmm-eikaiwa", "dmm-eikaiwa", "EXACT", "STUDY")).isAllowed()).isFalse();

        // 判定方法コードが未設定のときは 2.0 と同じく末尾一致として扱う
        assertThat(decide("www.youtube.com", "T",
                site("youtube.com", "youtube.com", null, "NORMAL")).isAllowed()).isTrue();
    }

    @Test
    void normalizeTerminalModeAcceptsCodesAndLegacyJapaneseLabels() {
        assertThat(ProxyAccessDecision.normalizeTerminalMode("T")).isEqualTo("T");
        assertThat(ProxyAccessDecision.normalizeTerminalMode(" k ")).isEqualTo("K");
        assertThat(ProxyAccessDecision.normalizeTerminalMode("通常")).isEqualTo("T");
        assertThat(ProxyAccessDecision.normalizeTerminalMode("休憩")).isEqualTo("K");
        assertThat(ProxyAccessDecision.normalizeTerminalMode("ゲーム")).isEqualTo("G");
        assertThat(ProxyAccessDecision.normalizeTerminalMode("勉強")).isEqualTo("B");
        assertThat(ProxyAccessDecision.normalizeTerminalMode("停止")).isEqualTo("S");
        assertThat(ProxyAccessDecision.normalizeTerminalMode("自由")).isEqualTo("J");
        // 未知の値は「端末情報未登録」と同じ扱い（2.0 と同じ）
        assertThat(ProxyAccessDecision.normalizeTerminalMode("X")).isNull();
        assertThat(ProxyAccessDecision.normalizeTerminalMode("")).isNull();
        assertThat(ProxyAccessDecision.normalizeTerminalMode(null)).isNull();
    }

    @Test
    void normalizeSiteKindAcceptsCodesAndLegacyValues() {
        assertThat(ProxyAccessDecision.normalizeSiteKind("STUDY")).isEqualTo("STUDY");
        assertThat(ProxyAccessDecision.normalizeSiteKind("normal")).isEqualTo("NORMAL");
        assertThat(ProxyAccessDecision.normalizeSiteKind("BREAK")).isEqualTo("BREAK");
        assertThat(ProxyAccessDecision.normalizeSiteKind("GAME")).isEqualTo("GAME");
        // 2.0 の表記（区分は 0.勉強 / 1.通常 / 2.休憩 / 3.ゲーム）
        assertThat(ProxyAccessDecision.normalizeSiteKind("0.勉強")).isEqualTo("STUDY");
        assertThat(ProxyAccessDecision.normalizeSiteKind("1.通常")).isEqualTo("NORMAL");
        assertThat(ProxyAccessDecision.normalizeSiteKind("2.休憩")).isEqualTo("BREAK");
        assertThat(ProxyAccessDecision.normalizeSiteKind("3.ゲーム")).isEqualTo("GAME");
        assertThat(ProxyAccessDecision.normalizeSiteKind("unknown")).isNull();
        assertThat(ProxyAccessDecision.normalizeSiteKind(null)).isNull();
    }

    @Test
    void readsApprovedSitesOnlyForModesThatNeedThem() {
        AtomicInteger loads = new AtomicInteger();

        // S（停止）・J（自由）・G（ゲーム）・端末未登録では許可サイトを読まない（2.0 と同じ DB アクセス）
        ProxyAccessDecision.decide(CLIENT_IP, HOST, "S", () -> {
            loads.incrementAndGet();
            return List.of();
        });
        ProxyAccessDecision.decide(CLIENT_IP, HOST, "J", () -> {
            loads.incrementAndGet();
            return List.of();
        });
        ProxyAccessDecision.decide(CLIENT_IP, HOST, "G", () -> {
            loads.incrementAndGet();
            return List.of();
        });
        ProxyAccessDecision.decide(CLIENT_IP, HOST, null, () -> {
            loads.incrementAndGet();
            return List.of();
        });
        assertThat(loads.get()).isZero();

        // 許可リストを使うモードでは 1 回だけ読む
        ProxyAccessDecision.decide(CLIENT_IP, HOST, "T", () -> {
            loads.incrementAndGet();
            return List.of(site("youtube.com", "youtube.com", "SUFFIX", "STUDY"));
        });
        assertThat(loads.get()).isEqualTo(1);
    }

    /**
     * 拒否理由は 2.0 と逐字一致していること。
     *
     * <p>理由は端末へ返す HTML と NET_プロキシ通信履歴情報.エラー内容 にそのまま入るため、
     * 文言を変えると 2.0 の履歴と突き合わせられなくなる。とくに
     * 「端末ステータスが不正のためアクセス不可」は 2.0 でも到達しない分岐（未知の端末モードは
     * 「端末情報未登録」として扱われる）だが、文言そのものは同じにしておく。</p>
     */
    @Test
    void rejectionMessagesMatchThe2_0Wording() {
        assertThat(ProxyAccessDecision.REASON_IP_UNAVAILABLE)
                .isEqualTo("IPアドレスが取得できないためアクセス不可");
        assertThat(ProxyAccessDecision.REASON_TERMINAL_UNREGISTERED)
                .isEqualTo("端末情報未登録のためアクセス不可");
        assertThat(ProxyAccessDecision.REASON_STOPPED)
                .isEqualTo("停止モードのためアクセス不可");
        assertThat(ProxyAccessDecision.REASON_HOST_UNKNOWN)
                .isEqualTo("接続先ホストを識別できないためアクセス不可");
        assertThat(ProxyAccessDecision.REASON_INVALID_TERMINAL_STATUS)
                .isEqualTo("端末ステータスが不正のためアクセス不可");
        assertThat(ProxyAccessDecision.REASON_SITE_NOT_ALLOWED)
                .isEqualTo("現在モードで許可されたサイトではないためアクセス不可");
        assertThat(ProxyAccessDecision.REASON_CONTROL_UNAVAILABLE)
                .isEqualTo("アクセス制御情報の取得に失敗したためアクセス不可");
    }
}
