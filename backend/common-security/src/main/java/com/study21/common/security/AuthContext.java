package com.study21.common.security;

import java.util.Optional;

/**
 * 认证上下文抽象。本阶段不实现真实登录，因此不提供具体实现。
 */
public interface AuthContext {

    Optional<CurrentUser> currentUser();
}
