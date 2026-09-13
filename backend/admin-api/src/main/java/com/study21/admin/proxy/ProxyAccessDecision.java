package com.study21.admin.proxy;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * プロキシのアクセス可否判定（純粋なロジック）。
 *
 * <p>2.0（com.study2.proxy.service.ProxyServerService#evaluateAccess）の判定を、Netty や
 * Spring、DB に依存しない形で独立させたもの。入力は「クライアント IP・接続先ホスト・
 * 端末コントロール情報から引いた端末モード・承認済み有効サイトの一覧」、出力は
 * 「許可／拒否・拒否理由・端末モード」。</p>
 *
 * <p>判定順は 2.0 と同じ。とくに <b>自由（J）・ゲーム（G）はホストを見る前に許可する</b>こと、
 * <b>停止（S）はホストを見る前に拒否する</b>ことを崩さないこと。</p>
 *
 * <ol>
 *   <li>クライアント IP が取れない → 拒否「IPアドレスが取得できないためアクセス不可」</li>
 *   <li>端末コントロール情報に無い → 拒否「端末情報未登録のためアクセス不可」</li>
 *   <li>停止モード（S） → 拒否「停止モードのためアクセス不可」</li>
 *   <li>自由（J）・ゲーム（G） → サイト判定せず許可</li>
 *   <li>接続先ホストが取れない → 拒否「接続先ホストを識別できないためアクセス不可」</li>
 *   <li>許可リストを使わないモード → 拒否「端末ステータスが不正のためアクセス不可」</li>
 *   <li>許可サイトに一致しない → 拒否「現在モードで許可されたサイトではないためアクセス不可」</li>
 * </ol>
 *
 * <p>拒否理由の文言は 2.0 と逐字一致させること（プロキシが端末へ返す HTML と
 * NET_プロキシ通信履歴情報.エラー内容 にそのまま入る）。</p>
 */
public final class ProxyAccessDecision {

    /** 拒否理由: クライアント IP が取得できない。 */
    public static final String REASON_IP_UNAVAILABLE = "IPアドレスが取得できないためアクセス不可";
    /** 拒否理由: 端末コントロール情報に IP が登録されていない。 */
    public static final String REASON_TERMINAL_UNREGISTERED = "端末情報未登録のためアクセス不可";
    /** 拒否理由: 停止モード（S）。 */
    public static final String REASON_STOPPED = "停止モードのためアクセス不可";
    /** 拒否理由: 接続先ホストが識別できない。 */
    public static final String REASON_HOST_UNKNOWN = "接続先ホストを識別できないためアクセス不可";
    /** 拒否理由: 端末モードが許可リスト方式でない（想定外の値）。 */
    public static final String REASON_INVALID_TERMINAL_STATUS = "端末ステータスが不正のためアクセス不可";
    /** 拒否理由: 現在のモードで許可されたサイトではない。 */
    public static final String REASON_SITE_NOT_ALLOWED = "現在モードで許可されたサイトではないためアクセス不可";
    /** 拒否理由: 端末コントロール情報・サイト情報の取得に失敗した。 */
    public static final String REASON_CONTROL_UNAVAILABLE = "アクセス制御情報の取得に失敗したためアクセス不可";

    /** 端末モード: 通常（許可リスト方式）。 */
    public static final String MODE_T = "T";
    /** 端末モード: 休憩（許可リスト方式）。 */
    public static final String MODE_K = "K";
    /** 端末モード: ゲーム（判定せず許可）。 */
    public static final String MODE_G = "G";
    /** 端末モード: 勉強（許可リスト方式）。 */
    public static final String MODE_B = "B";
    /** 端末モード: 停止（すべて拒否）。 */
    public static final String MODE_S = "S";
    /** 端末モード: 自由（判定せず許可）。 */
    public static final String MODE_J = "J";

    /** サイト区分コード: 勉強。 */
    public static final String KIND_STUDY = "STUDY";
    /** サイト区分コード: 通常。 */
    public static final String KIND_NORMAL = "NORMAL";
    /** サイト区分コード: 休憩。 */
    public static final String KIND_BREAK = "BREAK";
    /** サイト区分コード: ゲーム。 */
    public static final String KIND_GAME = "GAME";

    private final boolean allowed;
    private final String reason;
    private final String terminalMode;

    private ProxyAccessDecision(boolean allowed, String reason, String terminalMode) {
        this.allowed = allowed;
        this.reason = reason;
        this.terminalMode = terminalMode;
    }

    /** 許可する（理由は持たない）。 */
    public static ProxyAccessDecision allow(String terminalMode) {
        return new ProxyAccessDecision(true, null, terminalMode);
    }

    /** 拒否する（理由は必須）。 */
    public static ProxyAccessDecision deny(String reason, String terminalMode) {
        return new ProxyAccessDecision(false, reason, terminalMode);
    }

    public boolean isAllowed() {
        return allowed;
    }

    /** 拒否理由。許可したときは null。 */
    public String getReason() {
        return reason;
    }

    /** 端末モード（T/K/G/B/S/J）。端末が未登録・判定前に拒否したときは null。 */
    public String getTerminalMode() {
        return terminalMode;
    }

    /**
     * アクセス可否を判定する。
     *
     * @param clientIp       リクエストのクライアント IP（正規化前でよい）
     * @param host           接続先ホスト（正規化前でよい）
     * @param rawTerminalMode 端末コントロール情報から引いた端末モード（未登録なら null）
     * @param sites          承認済みかつ有効なサイト一覧（{@link ProxySiteMapper#findApprovedActiveSites()}）
     */
    public static ProxyAccessDecision decide(String clientIp,
                                             String host,
                                             String rawTerminalMode,
                                             List<ProxySiteMapper.ApprovedSite> sites) {
        return decide(clientIp, host, rawTerminalMode, () -> sites);
    }

    /**
     * アクセス可否を判定する（サイト一覧を遅延取得する版）。
     *
     * <p>2.0 は「許可リストを使うモード」のときだけサイト一覧を読んでいた。停止モードや
     * 自由・ゲーム、端末未登録のリクエストで余分な SELECT を走らせないよう、
     * プロキシ本体はこちらの版を使う。</p>
     *
     * @param siteSource サイト一覧を返す関数（必要なときに 1 回だけ呼ばれる）
     */
    public static ProxyAccessDecision decide(String clientIp,
                                             String host,
                                             String rawTerminalMode,
                                             Supplier<List<ProxySiteMapper.ApprovedSite>> siteSource) {
        String normalizedIp = ProxyHostMatcher.normalizeIp(clientIp);
        if (normalizedIp == null) {
            return deny(REASON_IP_UNAVAILABLE, null);
        }

        String terminalMode = normalizeTerminalMode(rawTerminalMode);
        if (terminalMode == null) {
            return deny(REASON_TERMINAL_UNREGISTERED, null);
        }

        if (MODE_S.equals(terminalMode)) {
            return deny(REASON_STOPPED, terminalMode);
        }
        if (MODE_J.equals(terminalMode) || MODE_G.equals(terminalMode)) {
            return allow(terminalMode);
        }

        String normalizedHost = ProxyHostMatcher.normalizeHost(host);
        if (normalizedHost == null) {
            return deny(REASON_HOST_UNKNOWN, terminalMode);
        }

        if (!isWhitelistMode(terminalMode)) {
            return deny(REASON_INVALID_TERMINAL_STATUS, terminalMode);
        }

        List<ProxySiteMapper.ApprovedSite> sites = siteSource == null ? List.of() : siteSource.get();
        if (!isHostAllowedForTerminalMode(normalizedHost, terminalMode, sites)) {
            return deny(REASON_SITE_NOT_ALLOWED, terminalMode);
        }
        return allow(terminalMode);
    }

    /** 許可リストで判定するモードか（通常・休憩・ゲーム・勉強）。 */
    public static boolean isWhitelistMode(String terminalMode) {
        return MODE_T.equals(terminalMode)
                || MODE_K.equals(terminalMode)
                || MODE_G.equals(terminalMode)
                || MODE_B.equals(terminalMode);
    }

    /**
     * 端末モードを正規化する。
     *
     * <p>2.1 の NET_端末コントロール情報.端末モード は T/K/G/B/S/J のコード（CHECK 制約あり）。
     * 移行データや手入力に備えて、2.0 が受け付けていた日本語表記（通常・休憩・ゲーム・勉強・
     * 停止・自由）も同じ値に寄せる。未知の値は null（＝端末情報未登録と同じ扱い。2.0 と同じ）。</p>
     */
    public static String normalizeTerminalMode(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        if (MODE_T.equals(upper) || upper.contains("通常")) {
            return MODE_T;
        }
        if (MODE_K.equals(upper) || upper.contains("休憩")) {
            return MODE_K;
        }
        if (MODE_G.equals(upper) || upper.contains("ゲーム")) {
            return MODE_G;
        }
        if (MODE_B.equals(upper) || upper.contains("勉強")) {
            return MODE_B;
        }
        if (MODE_S.equals(upper) || upper.contains("停止")) {
            return MODE_S;
        }
        if (MODE_J.equals(upper) || upper.contains("自由")) {
            return MODE_J;
        }
        return null;
    }

    /**
     * サイト区分コードを正規化する。
     *
     * <p>2.1 は STUDY / NORMAL / BREAK / GAME のコード（CHECK 制約あり）。移行データに備えて
     * 2.0 の表記（0.勉強 / 1.通常 / 2.休憩 / 3.ゲーム）も受け付ける。未知の値は null（＝不使用）。</p>
     */
    public static String normalizeSiteKind(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        if (KIND_STUDY.equals(upper)) {
            return KIND_STUDY;
        }
        if (KIND_NORMAL.equals(upper)) {
            return KIND_NORMAL;
        }
        if (KIND_BREAK.equals(upper)) {
            return KIND_BREAK;
        }
        if (KIND_GAME.equals(upper)) {
            return KIND_GAME;
        }
        if (upper.startsWith("0") || upper.contains("勉強")) {
            return KIND_STUDY;
        }
        if (upper.startsWith("1") || upper.contains("通常")) {
            return KIND_NORMAL;
        }
        if (upper.startsWith("2") || upper.contains("休憩")) {
            return KIND_BREAK;
        }
        if (upper.startsWith("3") || upper.contains("ゲーム")) {
            return KIND_GAME;
        }
        return null;
    }

    /**
     * 端末モードごとに許可するサイト区分（2.0 と同じ）。
     *
     * <ul>
     *   <li>T（通常）: STUDY・NORMAL</li>
     *   <li>K（休憩）: STUDY・NORMAL・BREAK</li>
     *   <li>B（勉強）: STUDY のみ</li>
     *   <li>G（ゲーム）: すべて許可（この判定に来る前に許可される）</li>
     *   <li>S（停止）・J（自由）: 許可リストを使わない</li>
     * </ul>
     */
    public static boolean isSiteKindAllowedForTerminalMode(String siteKindCode, String terminalMode) {
        String siteKind = normalizeSiteKind(siteKindCode);
        if (siteKind == null) {
            return false;
        }
        return switch (terminalMode == null ? "" : terminalMode) {
            case MODE_T -> KIND_STUDY.equals(siteKind) || KIND_NORMAL.equals(siteKind);
            case MODE_K -> KIND_STUDY.equals(siteKind) || KIND_NORMAL.equals(siteKind) || KIND_BREAK.equals(siteKind);
            case MODE_G -> true;
            case MODE_B -> KIND_STUDY.equals(siteKind);
            default -> false;
        };
    }

    /**
     * 承認済みサイトの中に、この端末モードで許可される接続先があるか。
     * サイト 1 行につき「サイトURL」と「ホスト名」の両方を候補にし、どれか一致すれば許可（2.0 と同じ）。
     */
    public static boolean isHostAllowedForTerminalMode(String requestHost,
                                                       String terminalMode,
                                                       List<ProxySiteMapper.ApprovedSite> sites) {
        if (sites == null) {
            return false;
        }
        for (ProxySiteMapper.ApprovedSite site : sites) {
            if (site == null || !isSiteKindAllowedForTerminalMode(site.getKindCode(), terminalMode)) {
                continue;
            }
            for (String candidate : ProxyHostMatcher.extractCandidateHosts(site.getSiteUrl(), site.getHostName())) {
                if (ProxyHostMatcher.matches(requestHost, candidate, site.getJudgeMethodCode())) {
                    return true;
                }
            }
        }
        return false;
    }
}
