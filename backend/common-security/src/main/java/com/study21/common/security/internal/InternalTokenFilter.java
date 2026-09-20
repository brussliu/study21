package com.study21.common.security.internal;

import com.study21.common.core.api.ErrorCode;
import com.study21.common.core.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * **サービス間（user-api → admin-api）の入口**を守るフィルタ。
 *
 * <p>画面から直接叩かれるべきでない内部入口（授業まとめの起動・回復など）は、session 認証では
 * なく**共有の合言葉**（`X-Internal-Token`）で守る。理由:</p>
 * <ul>
 *   <li>利用者（生徒・保護者）の session は admin-api には無い（別サービス・別クッキー）。
 *       かといって無認証にすると、**誰でも他人のまとめを起動・回復できてしまう**。</li>
 *   <li>「内網だから」「CORS があるから」「画面にボタンを出さないから」は**守りにならない**。</li>
 * </ul>
 *
 * <p><b>合言葉が未設定なら、このフィルタは全て拒否する</b>（匿名に落とさない）。設定は
 * 環境変数から読み（{@code STUDY21_INTERNAL_TOKEN}）、リポジトリにも前端にも置かない。
 * 比較は定数時間（{@link MessageDigest#isEqual}）で行う。ログには**合言葉を出さない**。</p>
 *
 * <p>このフィルタは**守りたいパスにだけ**登録する（{@code addUrlPatterns}）。広く掛けると
 * 無関係な入口を止めてしまう。</p>
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    /** 合言葉を載せるヘッダ（**URL には載せない**。ログにも残さない）。 */
    public static final String HEADER_NAME = "X-Internal-Token";

    private static final Logger log = LoggerFactory.getLogger(InternalTokenFilter.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String expectedToken;

    public InternalTokenFilter(String expectedToken) {
        this.expectedToken = expectedToken == null || expectedToken.isBlank() ? null : expectedToken.trim();
    }

    /** 合言葉が設定されているか（未設定なら内部入口は**すべて拒否**される）。 */
    public boolean configured() {
        return expectedToken != null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER_NAME);
        if (matchesToken(presented)) {
            chain.doFilter(request, response);
            return;
        }
        /*
         * **拒否**（401 ではなく 403）。利用者の session では通せない入口なので「入れない」が正しい。
         * 理由（未設定・不一致）は**ログにだけ**残し、応答には出さない（総当たりの手掛かりを与えない）。
         */
        log.warn("internal endpoint refused. path={} method={} configured={} presented={}",
                request.getRequestURI(), request.getMethod(), configured(), presented != null);
        writeError(response);
    }

    /** 提示された合言葉が正しいか（**定数時間**で比べる。Security の判定からも使う）。 */
    public boolean matchesToken(String presented) {
        if (expectedToken == null || presented == null || presented.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8),
                presented.trim().getBytes(StandardCharsets.UTF_8));
    }

    private static void writeError(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.error(ErrorCode.FORBIDDEN);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
    }
}
