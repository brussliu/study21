package com.study21.common.security;

import java.util.Set;

/**
 * 当前用户抽象。真实登录尚未实现，本类型仅为未来认证结果预留。
 */
public record CurrentUser(String id, String displayName, Set<Role> roles) {
}
