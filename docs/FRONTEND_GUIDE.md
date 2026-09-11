# 前端指南

## 1. PC 前端结构（`frontend/pc-web`）

```
src/
├── main.ts                 # 入口：Pinia + Router + 共享 token
├── App.vue                 # RouterView + ToastHost
├── assets/main.css         # 基础样式（引用共享 token）
├── router/index.ts         # 路由 + Route Meta
├── config/menuRegistry.ts  # UI 移行期业务菜单
├── stores/                 # Pinia（theme / UI确认用假认证 / system）
├── layouts/                # AdminLayout / UserLayout
├── components/
│   ├── layout/             # AppSidebar / AppTopbar / AppBreadcrumb / PageTitle
│   └── SystemStatus.vue    # 系统状态（调用 health）
└── views/                  # login / prototype(Vue移行画面) / error
```

## 2. Mobile 前端结构（`frontend/mobile-web`）

Mobile First。包含顶部栏、底部导航、抽屉菜单、页面内容容器、主题切换、Loading/Empty/Error/Toast/Dialog、403/404/500。

PWA 结构：`public/manifest.webmanifest`、`public/icons/`（占位图标 + 说明）。
本阶段**不创建 Service Worker**（不缓存 API 响应、登录状态、敏感数据、用户信息）。

## 3. 路由

### PC

| 路由 | 布局 | 说明 |
|---|---|---|
| `/login` | blank | 普通用户登录 |
| `/admin/login` | blank | 管理员登录 |
| `/admin/home` `/admin/:screen` | AdminLayout | 管理员 UI 页面 |
| `/student/home` `/student/:screen` | UserLayout | 学生 UI 页面 |
| `/parent/home` `/parent/:screen` | UserLayout | 家长 UI 页面 |
| `/403` `/404` `/500` | blank | 错误页 |
| 其他 | - | 重定向 `/404` |

### Mobile

与 PC 同构（`/login`、`/admin/login`、`/admin/home`、`/student/home`、`/parent/home`、403/404/500），
全部使用 `MobileLayout`（除登录与错误页）。

## 4. Layout

- `AdminLayout`：侧栏 + 顶栏 + 面包屑 + 内容区 + 页面标题区 + 响应式侧栏。
- `UserLayout`（PC）：侧栏 + 顶栏 + 内容区。
- `MobileLayout`：顶栏 + 内容区 + 底部导航 + 抽屉菜单。

## 5. Menu Registry

业务菜单权限仍未决定。`src/config/menuRegistry.ts` 暂时向三个区域提供相同的迁移页面菜单：

- `frameworkMenu(area)`：系统首页。
- `prototypeMenu(area)`：原 UI 左侧菜单对应的 Vue 页面。
- `resolveMenu(area, role?)`：菜单统一入口。

不把角色可访问的业务功能写死。

## 6. Pinia

- `useThemeStore`：亮/暗主题切换（仅持久化主题，不持久化登录状态）。
- `useAuthStore`：UI 确认专用假认证；仅保存用户名和角色到 `sessionStorage`，不保存密码。
- `useSystemStore`：调用两个后端 health，维护 `adminHealth` / `userHealth` 状态。

## 7. Design Token

共享于 `web-shared/src/styles/design-tokens.css`（亮色 + `html[data-theme="dark"]` 暗色）。
组件只引用语义化别名（`--color-*`、`--sp-*`、`--fs-*` 等）。JS 侧使用 `web-shared/src/tokens/tokens.ts`。

## 8. AdminApiClient / UserApiClient

位于 `web-shared/src/api/`：

- `AdminApiClient`：baseUrl 默认 `/api/admin`，可被 `VITE_ADMIN_API_BASE_URL` 覆盖。
- `UserApiClient`：baseUrl 默认 `/api/user`，可被 `VITE_USER_API_BASE_URL` 覆盖。

统一处理：超时、网络错误、非 2xx、401/403/404/500、Trace ID、取消请求、JSON 响应、统一错误信息。
页面组件**不直接散落 fetch**，一律通过客户端调用。

## 9. 角色权限

角色权限暂未决定。前端仅定义 `Role` 类型（`'ADMIN' | 'STUDENT' | 'GUARDIAN'`），
不建立任何「角色 → 业务功能」的映射。
## 10. UI 页面迁移

原 UI 页面主体已转换为 PC 前端中的 Vue SFC，通过系统路由和 Layout 显示。
不再提供 `/demo` 页面目录，也不发布 `/ui-demo/*.html`。

- 必须先通过登录页的假认证进入系统。
- 当前页面数据和按钮操作仍为 UI Mock，不连接正式 API 或数据库。
- 后续按功能逐项整理组件状态、校验、API 和角色权限。
