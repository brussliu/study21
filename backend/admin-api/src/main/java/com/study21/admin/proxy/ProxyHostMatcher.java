package com.study21.admin.proxy;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * プロキシのホスト判定ユーティリティ。
 *
 * <p>2.0（com.study2.proxy.service.ProxyServerService）の私有メソッドを、Netty や Spring に
 * 依存しない形でそのまま移設したもの。判定順・正規化の仕方は 2.0 と同じで、次の順に処理する。</p>
 * <ol>
 *   <li>小文字化と前後の空白除去</li>
 *   <li>IPv6 の [] 除去、パス以降の除去、ポートの除去、末尾ドットの除去</li>
 *   <li>www. を除いた形との 2 通り（{@link #hostVariants(String)}）で照合</li>
 * </ol>
 *
 * <p>判定方法コードは 2.1 の NET_サイト情報.判定方法コード（PREFIX / SUFFIX / CONTAINS / EXACT）。
 * 2.0 の日本語表記（先頭一致 / 末尾一致 / 含める / 完全一致）も受け付ける（移行データ対策）。</p>
 */
public final class ProxyHostMatcher {

    /** 判定方法コード: 先頭一致。 */
    public static final String JUDGE_PREFIX = "PREFIX";
    /** 判定方法コード: 末尾一致。 */
    public static final String JUDGE_SUFFIX = "SUFFIX";
    /** 判定方法コード: 含める。 */
    public static final String JUDGE_CONTAINS = "CONTAINS";
    /** 判定方法コード: 完全一致。 */
    public static final String JUDGE_EXACT = "EXACT";

    /** 判定方法コードが未設定・不正なときの既定（2.0 の「末尾一致」と同じ）。 */
    public static final String DEFAULT_JUDGE_METHOD = JUDGE_SUFFIX;

    private ProxyHostMatcher() {
    }

    /**
     * 判定方法コードを正規化する。空・未設定・未知の値は 2.0 と同じく「末尾一致」にする。
     */
    public static String normalizeJudgeMethod(String judgeMethodCode) {
        if (judgeMethodCode == null) {
            return DEFAULT_JUDGE_METHOD;
        }
        String value = judgeMethodCode.trim();
        if (value.isEmpty()) {
            return DEFAULT_JUDGE_METHOD;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case JUDGE_PREFIX, "先頭一致" -> JUDGE_PREFIX;
            case JUDGE_SUFFIX, "末尾一致" -> JUDGE_SUFFIX;
            case JUDGE_CONTAINS, "含める" -> JUDGE_CONTAINS;
            case JUDGE_EXACT, "完全一致" -> JUDGE_EXACT;
            default -> DEFAULT_JUDGE_METHOD;
        };
    }

    /**
     * ホスト名を正規化する（小文字化・ポート除去・パス除去・末尾ドット除去・IPv6 の [] 除去）。
     *
     * @return 正規化できないとき（null・空白のみ）は null
     */
    public static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        String value = host.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end > 0) {
                value = value.substring(1, end);
            }
        }
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        int colon = value.lastIndexOf(':');
        if (colon > 0 && value.indexOf(':') == colon) {
            value = value.substring(0, colon);
        }
        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isEmpty() ? null : value;
    }

    /**
     * 端末 IP を正規化する（前後の空白除去と、InetSocketAddress 由来の先頭スラッシュ除去）。
     *
     * @return 正規化できないときは null
     */
    public static String normalizeIp(String ip) {
        if (ip == null) {
            return null;
        }
        String value = ip.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    /** 先頭の www. をすべて取り除く（2.0 と同じく繰り返し除去する）。 */
    public static String stripLeadingWww(String host) {
        String value = host;
        while (value != null && value.startsWith("www.")) {
            value = value.substring(4);
        }
        return value;
    }

    /** 照合に使うホストの表記ゆれ（そのまま／www. を除いた形）。 */
    public static List<String> hostVariants(String host) {
        List<String> variants = new ArrayList<>();
        if (host == null || host.isBlank()) {
            return variants;
        }
        variants.add(host);
        String stripped = stripLeadingWww(host);
        if (!stripped.equals(host)) {
            variants.add(stripped);
        }
        return variants;
    }

    /**
     * 許可サイト 1 行分の候補ホストを取り出す。
     *
     * <p>2.1 は サイトURL と ホスト名 だけを候補にする。2.0 は サイト名称 も候補にしていたが、
     * 表示名を書き換えると許可範囲が変わってしまうため 2.1 では使わない（設計書
     * database/サイト管理/NET_サイト管理・端末コントロール設計.md）。</p>
     */
    public static List<String> extractCandidateHosts(String siteUrl, String hostName) {
        List<String> hosts = new ArrayList<>();
        String fromUrl = extractHostCandidate(siteUrl);
        String fromHostName = extractHostCandidate(hostName);
        if (fromUrl != null) {
            hosts.add(fromUrl);
        }
        if (fromHostName != null && !hosts.contains(fromHostName)) {
            hosts.add(fromHostName);
        }
        return hosts;
    }

    /**
     * URL あるいはホスト名の文字列からホスト名を取り出す（2.0 の extractHostCandidate と同じ）。
     * スキーム無しの文字列にも対応する。
     *
     * @return 取り出せないときは null
     */
    public static String extractHostCandidate(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return null;
        }

        String parsedHost = parseHostFromUri(text);
        if (parsedHost != null) {
            return parsedHost;
        }
        parsedHost = parseHostFromUri("http://" + text);
        if (parsedHost != null) {
            return parsedHost;
        }
        int schemeIdx = text.indexOf("://");
        if (schemeIdx >= 0 && schemeIdx + 3 < text.length()) {
            text = text.substring(schemeIdx + 3);
        }

        int slash = text.indexOf('/');
        if (slash >= 0) {
            text = text.substring(0, slash);
        }
        if (text.startsWith("*.")) {
            text = text.substring(2);
        }
        int colon = text.lastIndexOf(':');
        if (colon > 0 && text.indexOf(':') == colon) {
            text = text.substring(0, colon);
        }
        text = normalizeHost(text);
        return text;
    }

    /**
     * リクエストのホストが候補ホストに一致するかを判定方法コードで判定する。
     * 両側を正規化し、www. の有無の組み合わせのどれかが一致すれば許可する（2.0 と同じ）。
     */
    public static boolean matches(String requestHost, String candidateHost, String judgeMethodCode) {
        String request = normalizeHost(requestHost);
        String candidate = normalizeHost(candidateHost);
        if (request == null || candidate == null) {
            return false;
        }

        String method = normalizeJudgeMethod(judgeMethodCode);
        for (String requestValue : hostVariants(request)) {
            for (String candidateValue : hostVariants(candidate)) {
                if (matchesByJudgeMethod(requestValue, candidateValue, method)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesByJudgeMethod(String request, String candidate, String judgeMethodCode) {
        return switch (judgeMethodCode) {
            case JUDGE_PREFIX -> request.startsWith(candidate);
            case JUDGE_CONTAINS -> request.contains(candidate);
            case JUDGE_EXACT -> request.equals(candidate);
            case JUDGE_SUFFIX -> request.endsWith(candidate);
            default -> request.endsWith(candidate);
        };
    }

    private static String parseHostFromUri(String uriText) {
        try {
            URI uri = URI.create(uriText);
            String host = uri.getHost();
            return normalizeHost(host);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * "ホスト:ポート" 形式（Host ヘッダ・CONNECT の要求行）を分解する。
     * IPv6 の [::1]:443 形式にも対応する（2.0 の parseHostPort と同じ）。
     */
    public static HostPort parseHostPort(String raw) {
        if (raw == null || raw.isBlank()) {
            return new HostPort(null, null);
        }
        String text = raw.trim();
        if (text.startsWith("[")) {
            int end = text.indexOf(']');
            if (end > 0) {
                String host = text.substring(1, end);
                Integer port = (end + 1 < text.length() && text.charAt(end + 1) == ':')
                        ? parsePort(text.substring(end + 2))
                        : null;
                return new HostPort(host, port);
            }
        }
        int colon = text.lastIndexOf(':');
        if (colon > 0 && colon == text.indexOf(':')) {
            return new HostPort(text.substring(0, colon), parsePort(text.substring(colon + 1)));
        }
        return new HostPort(text, null);
    }

    /** ポート文字列を数値にする。範囲外（1〜65535 以外）や数値でないときは null。 */
    public static Integer parsePort(String value) {
        try {
            int port = Integer.parseInt(value.trim());
            return (port > 0 && port <= 65535) ? port : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** ホスト名とポートの組（2.0 の内部クラス HostPort と同じ用途）。 */
    public record HostPort(String host, Integer port) {
    }
}
