package com.study21.common.security;

/**
 * 权限检查抽象。角色与业务功能的权限映射尚未决定，本阶段仅定义接口。
 */
public interface PermissionChecker {

    boolean hasRole(Role role);

    boolean hasAnyRole(Role... roles);

    void requireRole(Role role);

    void requireAnyRole(Role... roles);
}
