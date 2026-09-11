# 安全与角色

## 1. 角色

本阶段仅定义角色枚举（`com.study21.common.security.Role`）：

- `ADMIN`
- `STUDENT`
- `GUARDIAN`

角色与业务功能的权限映射**尚未决定**，不在此阶段擅自确定。

## 2. 页面权限

前端路由使用 Route Meta（`requiresAuth`、`title`、`layout`）与 Menu Registry 预留结构，
但**不写死**任何「角色 → 页面」的映射。当前所有页面默认均可访问（`requiresAuth: false`），
业务页面权限留待后续决定。

## 3. 操作权限

后端预留 `PermissionChecker` 接口（`hasRole` / `hasAnyRole` / `requireRole` / `requireAnyRole`），
本阶段不实现具体检查。

## 4. 数据权限

数据权限（例如家长只能看到自己孩子数据）尚未设计，本阶段不实现。

## 5. 本阶段不实现真实认证

- 无用户/角色/权限数据库查询。
- 无真实登录、无 JWT 签发、无 Session 持久化。
- PC 前端提供 UI 确认专用的假认证，状态只保存在浏览器 `sessionStorage`。
- 假认证不校验账号、不保存或发送密码，也不代表后端认证已经完成。

## 6. 安全默认策略

- `health` 与 `system/info` 允许匿名访问。
- 其余未定义路径默认拒绝（denyAll）。
- admin-api 与 user-api 使用不同安全配置。
- CORS 允许来源从环境变量读取，禁止 `*`。
- 日志不输出敏感信息。
