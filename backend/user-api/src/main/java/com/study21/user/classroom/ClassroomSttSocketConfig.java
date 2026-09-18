package com.study21.user.classroom;

import com.study21.user.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import jakarta.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 授業録音の**書き起こし音声の常時接続**（ブラウザ→後端の WebSocket）。
 *
 * <p><b>なぜ常時接続か</b>: HTTP の 1 往復ごとの送信は、往復が遅いと音が貯まって
 * 書き起こしが遅れる。音源ごとに WebSocket を 1 本つなぎっぱなしにして、
 * **100ms ずつ**（16kHz モノラル 16bit ＝ 3200 バイト）送る。</p>
 *
 * <p><b>認証</b>: ハンドシェイクはブラウザが Cookie（セッション）を付けて行うので、
 * `HttpSession` に入っている `SPRING_SECURITY_CONTEXT`（`AccountController` がログイン時に
 * 入れている）から利用者を取り出す。**未ログインは接続させない**。API Key は画面へ出さない
 * （認識はサーバー側のセッションが持つ）。</p>
 *
 * <p><b>接続先</b>: `/api/user/classroom/{recordId}/stt/socket?source=mic|shared`。</p>
 */
@Configuration
@EnableWebSocket
public class ClassroomSttSocketConfig implements WebSocketConfigurer {

    /** ハンドシェイクで取り出した利用者を入れる属性の名前。 */
    static final String PRINCIPAL_ATTR = "classroomPrincipal";

    private final ClassroomSttSocketHandler handler;

    @Value("${study21.cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
    private String allowedOrigins;

    public ClassroomSttSocketConfig(ClassroomSttSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // HTTP の CORS と同じ許可元だけを受け付ける（設定の 1 か所で決める）
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(origin -> !origin.isEmpty()).toList();
        registry.addHandler(handler, "/api/user/classroom/*/stt/socket")
                .addInterceptors(new PrincipalHandshakeInterceptor())
                .setAllowedOrigins(origins.toArray(new String[0]));
    }

    /** ログイン済みの利用者だけ通す（`HttpSession` の SecurityContext から取り出す）。 */
    static class PrincipalHandshakeInterceptor extends HttpSessionHandshakeInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
            if (!super.beforeHandshake(request, response, wsHandler, attributes)) {
                return false;
            }
            if (!(request instanceof ServletServerHttpRequest servletRequest)) {
                return false;
            }
            HttpSession session = servletRequest.getServletRequest().getSession(false);
            Object context = session == null
                    ? null : session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (context instanceof SecurityContext securityContext
                    && securityContext.getAuthentication() != null
                    && securityContext.getAuthentication().getPrincipal() instanceof UserPrincipal principal) {
                attributes.put(PRINCIPAL_ATTR, principal);
                return true;
            }
            // 未ログイン（セッションが無い・利用者が入っていない）は接続させない
            return false;
        }
    }
}
