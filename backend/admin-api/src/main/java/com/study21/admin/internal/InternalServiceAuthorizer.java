package com.study21.admin.internal;

import com.study21.common.security.internal.InternalTokenFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * **サービス間（user-api）の内部入口**の判定。
 *
 * <p>授業まとめの起動・回復は、画面から直接叩かれる入口ではない（画面 → user-api が所有権を
 * 確かめる → user-api がここへ来る）。したがって守りは**利用者の session ではなく、サービス間の
 * 合言葉**（{@code X-Internal-Token}）で行う。</p>
 *
 * <p><b>合言葉が未設定なら全て拒否**（匿名に落とさない）。設定は環境変数
 * {@code STUDY21_INTERNAL_TOKEN} から読み、**リポジトリにも前端にも置かない**。ログにも出さない。</p>
 *
 * <p>`permitAll()` で守る形（フィルタを登録するだけ・内網だから安全、など）にしない:
 * Security の規則として書くことで、**広い `/api/admin/batch/**` の許可より前**に評価される。</p>
 */
@Component
public class InternalServiceAuthorizer {

    private final InternalTokenFilter tokenFilter;

    public InternalServiceAuthorizer(@Value("${study21.internal.token:}") String internalToken) {
        this.tokenFilter = new InternalTokenFilter(internalToken);
    }

    /** 合言葉が設定されているか（未設定なら内部入口は全て拒否される）。 */
    public boolean configured() {
        return tokenFilter.configured();
    }

    /**
     * **合言葉が一致するときだけ**通す判定（session では通さない）。
     *
     * @return Security の認可判定に渡す管理オブジェクト
     */
    public AuthorizationManager<RequestAuthorizationContext> authorizeFromHeaderOnlyAdmin() {
        return (authentication, context) -> {
            HttpServletRequest request = context.getRequest();
            String presented = request.getHeader(InternalTokenFilter.HEADER_NAME);
            return new AuthorizationDecision(tokenFilter.matchesToken(presented));
        };
    }
}
