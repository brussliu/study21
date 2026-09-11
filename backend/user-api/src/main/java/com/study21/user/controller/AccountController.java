package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.account.AccountService;
import com.study21.user.account.LoginRequest;
import com.study21.user.account.LoginResponse;
import com.study21.user.account.RegisterRequest;
import com.study21.user.account.RegisterResponse;
import com.study21.user.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 保護者・生徒アカウントの公開 API（認証前の公開エンドポイント）。
 */
@RestController
@RequestMapping("/api/user")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** 保護者 + 初期生徒の一括登録。 */
    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(accountService.register(request));
    }

    /** メールアドレス + パスワードでログイン認証。 */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest servletRequest) {
        LoginResponse response = accountService.login(request);
        UserPrincipal principal = new UserPrincipal(response.getAccountId(), response.getLoginId(),
                response.getDisplayName(), response.getAccountType());
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_" + response.getAccountType().code())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = servletRequest.getSession(true);
        servletRequest.changeSessionId();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return ApiResponse.ok(response);
    }

    /** 一般ユーザーのサーバーセッションを破棄する。 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ApiResponse.ok(null);
    }
}
