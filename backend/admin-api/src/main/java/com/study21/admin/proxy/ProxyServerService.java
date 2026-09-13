package com.study21.admin.proxy;

import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;

import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

/**
 * プロキシサービス（LittleProxy + Netty による HTTP プロキシ）。
 *
 * <p>2.0 の batL01 は「JVM 内でプロキシを起動する」ことだけを行っていた。その
 * {@code ProxyServerService#startIfNeeded()} を 2.1 の admin-api へ移植したもの。
 * 起動（{@link #startIfNeeded()}）は batS01 から呼ばれる想定で、ここではバッチに関する
 * 処理は一切持たない。</p>
 *
 * <p>1 リクエストごとに次の順で処理する（2.0 と同じ）。</p>
 * <ol>
 *   <li>クライアント IP・接続先ホスト・ヘッダを要求から取り出す</li>
 *   <li>{@link ProxyAccessDecision} で可否を判定する（判定順も 2.0 と同じ）</li>
 *   <li>拒否なら端末へ 403 の HTML を返し、許可ならそのまま転送する</li>
 *   <li>どちらの場合も NET_プロキシ通信履歴情報 に 1 行追記する</li>
 * </ol>
 *
 * <p>DB アクセスはすべて Mapper（MyBatis）経由。2.0 は素の JDBC だったが、2.1 は
 * SqlLoggingInterceptor で DB 操作を logs/backend/admin-api-sql.log に記録する決まりのため、
 * プロキシからも直接 JDBC を使わない。</p>
 */
@Service
public class ProxyServerService {

    private static final Logger log = LoggerFactory.getLogger(ProxyServerService.class);

    /** プロキシの待受ポートの既定値。study21.proxy.port で変更できる。 */
    private static final int FALLBACK_PROXY_PORT = 7777;

    /**
     * 2.0 から引き継いだ診断用の固定値。
     * この IP への拒否だけは、原因調査用に詳細ログ（許可サイトの照合結果）を出す。
     */
    private static final String DIAGNOSTIC_TARGET_HOST = "192.168.0.100";

    /**
     * 2.0 から引き継いだ診断用の固定値。
     * ログを見たときに「どのビルドのプロキシが動いているか」を判別するための版表示。
     */
    private static final String DIAGNOSTIC_VERSION = "2026-05-28-2335";

    private final ProxyTerminalMapper terminalMapper;
    private final ProxySiteMapper siteMapper;
    private final ProxyAccessLogMapper accessLogMapper;

    /** 待受ポート。application.yml の study21.proxy.port（既定 7777）。 */
    private final int port;

    private final AtomicBoolean starting = new AtomicBoolean(false);

    private volatile HttpProxyServer proxyServer;

    public ProxyServerService(ProxyTerminalMapper terminalMapper,
                              ProxySiteMapper siteMapper,
                              ProxyAccessLogMapper accessLogMapper,
                              @Value("${study21.proxy.port:7777}") int port) {
        this.terminalMapper = terminalMapper;
        this.siteMapper = siteMapper;
        this.accessLogMapper = accessLogMapper;
        if (port > 0 && port <= 65535) {
            this.port = port;
        } else {
            log.warn("Invalid proxy port configured. Falling back to default. configured={} fallback={}",
                    port, FALLBACK_PROXY_PORT);
            this.port = FALLBACK_PROXY_PORT;
        }
    }

    /**
     * プロキシが動いているか。
     *
     * <p>2.0 と同じく、参照が残っていてもポートが閉じていれば参照を捨てて false を返す
     * （別プロセスに置き換わった・停止した場合の保険）。</p>
     */
    public boolean isRunning() {
        synchronized (this) {
            if (proxyServer == null) {
                return false;
            }
            if (!isPortAlive(port)) {
                log.warn("Proxy server reference exists but port is not alive. Resetting proxyServer handle. port={}", port);
                proxyServer = null;
                return false;
            }
            return true;
        }
    }

    /** プロキシの待受ポート。 */
    public int getPort() {
        return port;
    }

    /**
     * まだ動いていなければプロキシを起動する（2.0 の batL01 と同じ役割）。
     *
     * @return 起動結果の説明（起動済み・起動した・ポート使用中）
     */
    public String startIfNeeded() {
        if (isRunning()) {
            return "Proxy already running on port " + getPort();
        }
        if (!starting.compareAndSet(false, true)) {
            return "Proxy startup is already in progress";
        }

        try {
            HttpProxyServer started = DefaultHttpProxyServer.bootstrap()
                    .withPort(port)
                    .withAllowLocalOnly(false)
                    .withFiltersSource(new HttpFiltersSourceAdapter() {
                        @Override
                        public HttpFilters filterRequest(HttpRequest originalRequest, ChannelHandlerContext ctx) {
                            return new HttpFiltersAdapter(originalRequest, ctx) {
                                @Override
                                public io.netty.handler.codec.http.HttpResponse clientToProxyRequest(HttpObject httpObject) {
                                    if (httpObject instanceof HttpRequest request) {
                                        ChannelHandlerContext actualCtx = this.ctx != null ? this.ctx : ctx;
                                        RequestInfo info = toRequestInfo(actualCtx, request);
                                        ProxyAccessDecision decision;
                                        try {
                                            decision = evaluateAccess(info);
                                        } catch (Exception ex) {
                                            log.error("Proxy access control evaluation failed. clientIp={} host={} method={}",
                                                    info.clientIp, info.host, info.method, ex);
                                            decision = ProxyAccessDecision.deny(
                                                    ProxyAccessDecision.REASON_CONTROL_UNAVAILABLE, null);
                                        }
                                        if (!decision.isAllowed()) {
                                            emitBlockedTargetDiagnostics(info, decision);
                                            logAccess(info, HttpResponseStatus.FORBIDDEN.code(), decision.getReason());
                                            log.info("Proxy request denied. version={} clientIp={} status={} host={} method={} reason={}",
                                                    DIAGNOSTIC_VERSION,
                                                    info.clientIp, decision.getTerminalMode(), info.host, info.method, decision.getReason());
                                            return buildDeniedResponse(request, decision.getReason());
                                        }
                                        logAccess(info, null, null);
                                    }
                                    return null;
                                }
                            };
                        }
                    })
                    .start();

            synchronized (this) {
                proxyServer = started;
            }
            log.info("Proxy server started. port={}", port);
            emitRuntimeDiagnostics();
            log.info("Proxy diagnostic marker enabled. targetHost={} version={}",
                    DIAGNOSTIC_TARGET_HOST, DIAGNOSTIC_VERSION);
            return "Proxy started on port " + port;
        } catch (Exception e) {
            if (hasCause(e, BindException.class) && isPortAlive(port)) {
                log.warn("Proxy port is already in use. Treating proxy as already running. port={}", port, e);
                return "Proxy port " + port + " is already in use";
            }
            log.error("Failed to start proxy server.", e);
            throw new IllegalStateException("プロキシサーバーの起動に失敗しました。", e);
        } finally {
            starting.set(false);
        }
    }

    /** アプリケーション終了時にプロキシを止める。 */
    @PreDestroy
    public void shutdown() {
        shutdownInternal("preDestroy");
    }

    /** コンテキスト終了時にもプロキシを止める（2.0 と同じ二重の保険）。 */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed(ContextClosedEvent event) {
        shutdownInternal("contextClosed");
    }

    private void shutdownInternal(String reason) {
        HttpProxyServer server;
        synchronized (this) {
            if (proxyServer == null) {
                return;
            }
            server = proxyServer;
            proxyServer = null;
        }
        try {
            try {
                server.stop();
            } catch (Throwable stopEx) {
                log.warn("Proxy server graceful stop failed. reason={}", reason, stopEx);
                server.abort();
            }
            log.info("Proxy server stopped. reason={}", reason);
        } catch (Exception e) {
            log.warn("Failed to stop proxy server cleanly.", e);
        }
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 通信履歴を 1 行追記する。許可した要求は 応答状態コード・エラー内容 を null にする。
     * 記録の失敗でプロキシ本体を止めないよう、例外は警告ログに留める（2.0 と同じ）。
     */
    private void logAccess(RequestInfo info, Integer responseStatusCode, String errorContent) {
        try {
            accessLogMapper.insertRequestLog(
                    LocalDateTime.now(),
                    info.clientIp,
                    info.clientPort,
                    info.method,
                    info.host,
                    info.hostPort,
                    info.requestUrl,
                    info.requestPath,
                    info.queryString,
                    info.protocolType,
                    info.userAgent,
                    info.referer,
                    responseStatusCode,
                    errorContent);
        } catch (Exception e) {
            log.warn("Proxy access log write failed. method={} uri={}", info.method, info.requestUrl, e);
        }
    }

    /**
     * 端末 IP・接続先ホスト・端末モード・許可サイトから可否を判定する。
     * 判定そのものは {@link ProxyAccessDecision}（純粋なロジック）に置き、ここは DB の読み出しだけを行う。
     */
    private ProxyAccessDecision evaluateAccess(RequestInfo info) {
        String clientIp = ProxyHostMatcher.normalizeIp(info.clientIp);
        String rawTerminalMode = clientIp == null ? null : terminalMapper.findTerminalModeByIp(clientIp);
        return ProxyAccessDecision.decide(clientIp, info.host, rawTerminalMode, siteMapper::findApprovedActiveSites);
    }

    /**
     * 診断用: 特定のホスト（{@link #DIAGNOSTIC_TARGET_HOST}）への拒否だけ、許可サイトの照合結果を残す。
     * 2.0 で「なぜ拒否されたか分からない」ときの調査用に入れた処理をそのまま引き継いでいる。
     */
    private void emitBlockedTargetDiagnostics(RequestInfo info, ProxyAccessDecision decision) {
        String normalizedHost = ProxyHostMatcher.normalizeHost(info == null ? null : info.host);
        if (!DIAGNOSTIC_TARGET_HOST.equals(normalizedHost)) {
            return;
        }

        String clientIp = ProxyHostMatcher.normalizeIp(info == null ? null : info.clientIp);
        String rawTerminalMode = clientIp == null ? null : terminalMapper.findTerminalModeByIp(clientIp);
        List<String> siteDiagnostics = collectSiteDiagnostics(normalizedHost);
        log.error(
                "Blocked diagnostic for target host. version={} targetHost={} clientIp={} terminalMode={} rawTerminalMode={} method={} requestUrl={} requestPath={} queryString={} reason={} approvedSiteDiagnostics={}",
                DIAGNOSTIC_VERSION,
                normalizedHost,
                clientIp,
                decision == null ? null : decision.getTerminalMode(),
                rawTerminalMode,
                info == null ? null : info.method,
                info == null ? null : info.requestUrl,
                info == null ? null : info.requestPath,
                info == null ? null : info.queryString,
                decision == null ? null : decision.getReason(),
                siteDiagnostics);
        log.info("Proxy diagnostic marker emitted. version={} targetHost={} clientIp={}",
                DIAGNOSTIC_VERSION, normalizedHost, clientIp);
    }

    /**
     * 診断用: 起動時に実行環境（PID・作業ディレクトリ・クラス位置）を残す。
     * 2.0 で「どのプロセスがポートを掴んでいるか」を調べるために入れた処理をそのまま引き継いでいる。
     */
    private void emitRuntimeDiagnostics() {
        log.info(
                "Proxy runtime diagnostics. version={} pid={} userDir={} catalinaBase={} catalinaHome={} classLocation={}",
                DIAGNOSTIC_VERSION,
                ProcessHandle.current().pid(),
                System.getProperty("user.dir"),
                System.getProperty("catalina.base"),
                System.getProperty("catalina.home"),
                resolveClassLocation());
    }

    private String resolveClassLocation() {
        try {
            ProtectionDomain protectionDomain = ProxyServerService.class.getProtectionDomain();
            if (protectionDomain == null) {
                return null;
            }
            CodeSource codeSource = protectionDomain.getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return null;
            }
            return codeSource.getLocation().toString();
        } catch (Exception e) {
            return "unresolved:" + e.getClass().getSimpleName();
        }
    }

    /**
     * 診断用: 拒否されたホストについて、承認済みサイトのどれが一致したかを文字列化する。
     * 2.1 は サイト名称 を判定に使わないため、候補は サイトURL と ホスト名 のみ。
     */
    private List<String> collectSiteDiagnostics(String requestHost) {
        List<String> diagnostics = new ArrayList<>();
        String normalizedRequestHost = ProxyHostMatcher.normalizeHost(requestHost);
        for (ProxySiteMapper.ApprovedSite site : siteMapper.findApprovedActiveSites()) {
            if (site == null) {
                continue;
            }
            String siteUrl = site.getSiteUrl();
            String hostName = site.getHostName();
            String siteKind = site.getKindCode();
            String judgeMethod = site.getJudgeMethodCode();
            String normalizedKind = ProxyAccessDecision.normalizeSiteKind(siteKind);
            boolean kindAllowedForT = ProxyAccessDecision.isSiteKindAllowedForTerminalMode(
                    siteKind, ProxyAccessDecision.MODE_T);
            for (String candidate : ProxyHostMatcher.extractCandidateHosts(siteUrl, hostName)) {
                boolean hostMatched = ProxyHostMatcher.matches(normalizedRequestHost, candidate, judgeMethod);
                if (hostMatched || DIAGNOSTIC_TARGET_HOST.equals(ProxyHostMatcher.normalizeHost(candidate))) {
                    diagnostics.add(
                            "siteUrl=" + String.valueOf(siteUrl)
                                    + ", hostName=" + String.valueOf(hostName)
                                    + ", siteKind=" + String.valueOf(siteKind)
                                    + ", normalizedKind=" + String.valueOf(normalizedKind)
                                    + ", judgeMethod=" + String.valueOf(judgeMethod)
                                    + ", candidateHost=" + String.valueOf(candidate)
                                    + ", hostMatched=" + hostMatched
                                    + ", kindAllowedForT=" + kindAllowedForT);
                }
            }
        }
        return diagnostics;
    }

    /**
     * 拒否したときに端末へ返す HTML（403）。文言・見た目は 2.0 と同じ。
     */
    private FullHttpResponse buildDeniedResponse(HttpRequest request, String reason) {
        String displayReason = (reason == null || reason.isBlank())
                ? "この端末は現在の利用ルールによりアクセスが制限されています。"
                : reason;
        String html = """
                <!doctype html>
                <html lang="ja">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>アクセス制限</title>
                  <style>
                    body {
                      margin: 0;
                      font-family: "Segoe UI", "Yu Gothic UI", "Meiryo", sans-serif;
                      background: linear-gradient(135deg, #eef7f6, #f8fbfb);
                      color: #153a45;
                    }
                    .page {
                      min-height: 100vh;
                      display: flex;
                      align-items: center;
                      justify-content: center;
                      padding: 24px;
                      box-sizing: border-box;
                    }
                    .card {
                      width: min(760px, 100%);
                      background: #ffffff;
                      border: 1px solid #cfe2e6;
                      border-radius: 16px;
                      box-shadow: 0 14px 40px rgba(21, 58, 69, 0.12);
                      overflow: hidden;
                    }
                    .head {
                      background: #1f6f78;
                      color: #ffffff;
                      padding: 18px 24px;
                      font-size: 22px;
                      font-weight: 700;
                      letter-spacing: 0.02em;
                    }
                    .body {
                      padding: 24px;
                    }
                    .lead {
                      margin: 0 0 12px;
                      font-size: 16px;
                      line-height: 1.7;
                    }
                    .reason {
                      margin: 0;
                      padding: 14px 16px;
                      border-radius: 10px;
                      border: 1px solid #f0c3c3;
                      background: #fff4f4;
                      color: #8b1f1f;
                      font-weight: 600;
                      word-break: break-word;
                    }
                    .note {
                      margin: 14px 0 0;
                      font-size: 13px;
                      color: #5f7880;
                    }
                  </style>
                </head>
                <body>
                  <div class="page">
                    <section class="card" role="alert" aria-live="assertive">
                      <header class="head">アクセスが制限されました</header>
                      <div class="body">
                        <p class="lead">このリクエストは端末コントロール設定によりブロックされました。</p>
                        <p class="reason">${reason}</p>
                        <p class="note">管理者に連絡する場合は、アクセス時刻と端末IPをお伝えください。</p>
                      </div>
                    </section>
                  </div>
                </body>
                </html>
                """.replace("${reason}", escapeHtml(displayReason));
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        HttpVersion version = request != null && request.protocolVersion() != null
                ? request.protocolVersion()
                : HttpVersion.HTTP_1_1;
        FullHttpResponse response = new DefaultFullHttpResponse(
                version,
                HttpResponseStatus.FORBIDDEN,
                Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        response.headers().set(HttpHeaderNames.PRAGMA, "no-cache");
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        return response;
    }

    private String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }

    /** 要求から、判定と履歴に必要な情報を取り出す（2.0 と同じ取り方）。 */
    private RequestInfo toRequestInfo(ChannelHandlerContext ctx, HttpRequest request) {
        RequestInfo info = new RequestInfo();
        info.method = request.method() == null ? null : request.method().name();
        info.requestUrl = request.uri();
        info.userAgent = headerValue(request, "User-Agent");
        info.referer = headerValue(request, "Referer");

        SocketAddress remote = ctx == null || ctx.channel() == null ? null : ctx.channel().remoteAddress();
        if (remote instanceof InetSocketAddress inet) {
            if (inet.getAddress() != null) {
                info.clientIp = inet.getAddress().getHostAddress();
            } else if (inet.getHostString() != null) {
                info.clientIp = inet.getHostString();
            }
            info.clientPort = inet.getPort();
        } else if (remote != null) {
            info.clientIp = remote.toString();
        }
        if (info.clientIp == null || info.clientIp.isBlank()) {
            info.clientIp = firstClientIpFromHeaders(request);
        }
        if (info.clientPort == null) {
            info.clientPort = clientPortFromHeaders(request);
        }

        fillTargetInfo(info, request);
        return info;
    }

    /** CONNECT（HTTPS）と通常の HTTP で、接続先ホスト・ポート・パス・クエリの取り方が変わる（2.0 と同じ）。 */
    private void fillTargetInfo(RequestInfo info, HttpRequest request) {
        String uriText = request.uri() == null ? "" : request.uri().trim();
        String hostHeader = headerValue(request, "Host");

        if ("CONNECT".equalsIgnoreCase(info.method)) {
            info.protocolType = "HTTPS";
            ProxyHostMatcher.HostPort hp = ProxyHostMatcher.parseHostPort(uriText);
            if (hp.host() == null || hp.host().isBlank()) {
                hp = ProxyHostMatcher.parseHostPort(hostHeader);
            }
            info.host = hp.host();
            info.hostPort = hp.port() != null ? hp.port() : 443;
            info.requestPath = null;
            info.queryString = null;
            return;
        }

        try {
            URI uri = URI.create(uriText);
            if (uri.getScheme() != null) {
                info.protocolType = uri.getScheme().toUpperCase(Locale.ROOT);
                info.host = uri.getHost();
                int uriPort = uri.getPort();
                if (uriPort > 0) {
                    info.hostPort = uriPort;
                } else if ("HTTPS".equals(info.protocolType)) {
                    info.hostPort = 443;
                } else if ("HTTP".equals(info.protocolType)) {
                    info.hostPort = 80;
                }
                info.requestPath = uri.getPath();
                info.queryString = uri.getQuery();
                if (info.host != null) {
                    return;
                }
            }
        } catch (Exception ignored) {
            // 絶対 URL でない場合は Host ヘッダから組み立てる（2.0 と同じ）
        }

        info.protocolType = "HTTP";
        ProxyHostMatcher.HostPort hp = ProxyHostMatcher.parseHostPort(hostHeader);
        info.host = hp.host();
        info.hostPort = hp.port() != null ? hp.port() : 80;
        int q = uriText.indexOf('?');
        if (q >= 0) {
            info.requestPath = uriText.substring(0, q);
            info.queryString = q + 1 < uriText.length() ? uriText.substring(q + 1) : null;
        } else {
            info.requestPath = uriText;
            info.queryString = null;
        }
    }

    private String headerValue(HttpRequest request, String name) {
        if (request == null || request.headers() == null) {
            return null;
        }
        return request.headers().get(name);
    }

    /** リモートアドレスが取れないときの保険（X-Forwarded-For / X-Real-IP / Forwarded）。 */
    private String firstClientIpFromHeaders(HttpRequest request) {
        String xForwardedFor = headerValue(request, "X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String first = xForwardedFor.split(",")[0].trim();
            if (!first.isBlank()) {
                return first;
            }
        }
        String xRealIp = headerValue(request, "X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        String forwarded = headerValue(request, "Forwarded");
        if (forwarded != null && !forwarded.isBlank()) {
            String lower = forwarded.toLowerCase(Locale.ROOT);
            int idx = lower.indexOf("for=");
            if (idx >= 0) {
                String value = forwarded.substring(idx + 4).trim();
                int sep = value.indexOf(';');
                if (sep >= 0) {
                    value = value.substring(0, sep).trim();
                }
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                if (value.startsWith("[")) {
                    int end = value.indexOf(']');
                    if (end > 0) {
                        return value.substring(1, end);
                    }
                }
                int colon = value.lastIndexOf(':');
                if (colon > 0 && value.indexOf(':') == colon) {
                    return value.substring(0, colon);
                }
                return value;
            }
        }
        return null;
    }

    /** クライアントポートが取れないときの保険（X-Forwarded-Port / Forwarded）。 */
    private Integer clientPortFromHeaders(HttpRequest request) {
        String xForwardedPort = headerValue(request, "X-Forwarded-Port");
        Integer fromXForwardedPort = parseIntOrNull(xForwardedPort);
        if (fromXForwardedPort != null) {
            return fromXForwardedPort;
        }

        String forwarded = headerValue(request, "Forwarded");
        if (forwarded == null || forwarded.isBlank()) {
            return null;
        }
        String lower = forwarded.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("for=");
        if (idx < 0) {
            return null;
        }
        String value = forwarded.substring(idx + 4).trim();
        int sep = value.indexOf(';');
        if (sep >= 0) {
            value = value.substring(0, sep).trim();
        }
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end > 0 && end + 1 < value.length() && value.charAt(end + 1) == ':') {
                return parseIntOrNull(value.substring(end + 2));
            }
            return null;
        }
        int colon = value.lastIndexOf(':');
        if (colon > 0 && value.indexOf(':') == colon) {
            return parseIntOrNull(value.substring(colon + 1));
        }
        return null;
    }

    private Integer parseIntOrNull(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 待受ポートが実際に開いているか（参照だけ残った状態の検出に使う）。 */
    private boolean isPortAlive(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 1 リクエスト分の情報（2.0 の内部クラス RequestInfo と同じ）。 */
    private static class RequestInfo {
        String clientIp;
        Integer clientPort;
        String method;
        String host;
        Integer hostPort;
        String requestUrl;
        String requestPath;
        String queryString;
        String protocolType;
        String userAgent;
        String referer;
    }
}
