# PC 前端结构与公共框架说明

> 本文说明 Study 2.1 PC 前端（`frontend/pc-web`）的当前结构、公共 Layout、
> 菜单配置、假认证机制与 `prototype/generated` 临时页面规则。
> 文档按实际文件路径描述，不含空泛说明。

## 1. 技术栈与启动/构建

- Vue 3 + TypeScript(strict) + Vite + Vue Router + Pinia + Vitest
- 依赖定义：`frontend/pc-web/package.json`
- Vite 配置：`frontend/pc-web/vite.config.ts`（端口 5173，代理 `/api/admin`、`/api/user`）
- 入口：`frontend/pc-web/src/main.ts`
- 启动：`npm run dev -w pc-web`；构建：`npm run build -w pc-web`；测试：`npm run test -w pc-web`

## 2. 目录结构

```text
frontend/pc-web/
├── src/
│   ├── main.ts                      # 入口（加载公共 CSS）
│   ├── App.vue                      # RouterView + ToastHost
│   ├── assets/prototype/*.css       # ui-demo 忠实副本（唯一公共样式来源）
│   ├── composables/useSidebar.ts    # 侧栏折叠/抽屉状态
│   ├── router/index.ts              # 路由 + 登录守卫
│   ├── stores/                      # auth(假认证) / theme
│   ├── config/
│   │   ├── menuRegistry.ts          # 集中式菜单（三角色共用）
│   │   └── prototypePages.generated.ts  # 94 页面注册表（脚本生成）
│   ├── layouts/                     # AdminLayout / UserLayout
│   ├── components/layout/           # AppSidebar / AppTopbar / AppBreadcrumb / PageTitle
│   ├── components/account/          # UserProfileDialog / PasswordChangeDialog（右上メニューから開く）
│   ├── api/                         # account / documents / linkclip / net / register / tempfiles / testinfo
│   ├── views/
│   │   ├── net/                     # SiteManagementView（サイト管理）/ TerminalControlView（端末コントロール）
│   │   ├── login/                   # UserLoginView / AdminLoginView
│   │   ├── prototype/               # PrototypePageView + generated/（94 页）
│   │   └── error/                   # 403 / 404 / 500
│   └── views/{admin,student,parent}/# 旧占位首页（已不被路由引用，保留）
└── tests/                           # Vitest 单元测试
```

## 3. 公共 Layout 组件

Layout 使用原 ui-demo 的 `.app` / `.sidebar` / `.app-main` / `.topbar` / `.page`
DOM 结构与类名，样式全部来自 `src/assets/prototype/layout.css`（与 ui-demo 逐字节一致）。

| 组件 | 路径 | 对应 ui-demo 结构 |
|---|---|---|
| `AdminLayout` | `src/layouts/AdminLayout.vue` | `.app` → `.sidebar` + `.sidebar-backdrop` + `.app-main` → `.topbar` + `main.page` |
| `UserLayout` | `src/layouts/UserLayout.vue` | 同上；`area` 由路由判定（`/parent`→parent，否则 student） |
| `AppSidebar` | `src/components/layout/AppSidebar.vue` | `.sidebar`（brand / nav / footer、折叠 `.is-collapsed`、抽屉 `.is-open`、分组 `.nav-group.is-open`、高亮 `.is-active`） |
| `AppTopbar` | `src/components/layout/AppTopbar.vue` | `.topbar`（左侧菜单按钮 + 日期时钟 + 励志语；右侧角色/用户 + 主题切换） |
| `AppBreadcrumb` | `src/components/layout/AppBreadcrumb.vue` | `.breadcrumb`（ホーム / 当前页标题，标题取自 `prototypePages.generated.ts`） |
| `PageTitle` | `src/components/layout/PageTitle.vue` | `.page-head`（title/sub/actions） |

侧栏折叠状态与抽屉状态：`src/composables/useSidebar.ts`（折叠持久化到 `localStorage`，
键 `study21.sidebar`，行为对齐 ui-demo 的 `shell.js`）。

## 4. 菜单配置位置

- 唯一来源：`src/config/menuRegistry.ts`。
- 三角色（admin/student/parent）**共用同一份菜单数据**，不复制三套。
- `frameworkMenu(area)`：框架级「ホーム」；`prototypeMenu(area)`：业务菜单（含子菜单与图标）。
- 图标与 ui-demo 侧栏一致，引用 `/prototype-assets/icons/icons.svg#i-*`。
- `resolveMenu(area, role?)`：未来按角色过滤菜单的扩展点（当前 role 未生效，权限未定）。

## 5. 假认证机制

- 位置：`src/stores/auth.ts`。
- 会话保存于 `sessionStorage`（键 `study21.mock-auth`），刷新后仍有效。
- `fakeLogin(role, username?)` 写入角色与用户名；默认显示名：
  ADMIN→`テスト管理者`、STUDENT→`山田 太郎`、GUARDIAN→`山田 花子`。
- `logout()` 清除会话；退出后由 `AppSidebar` 跳转回登录页。
- 路由守卫：`src/router/index.ts` 的 `beforeEach`（未登录访问受保护路由 → 登录页；
  已登录访问登录页 → 按角色回 `/admin/home`、`/parent/home`、`/student/home`）。
- 登录页：`src/views/login/UserLoginView.vue`（学生/家长）与 `AdminLoginView.vue`（管理员）。
  登录调用真实 API（`/api/user/login`・`/api/admin/login`），只把用户名与角色放进
  `sessionStorage`（**不保存密码**）。
- 个人信息的修正与密码变更分别是**对话框**（`src/components/account/`，从右上菜单打开），
  保存后重新加载父页面。详见 `docs/ACCOUNT.md`。

## 6. prototype/generated 规则（临时页面区域）

- 临时区域：`src/views/prototype/generated/`，共 94 个 `.vue` 页面。
- 注册表：`src/config/prototypePages.generated.ts`（由脚本生成，勿手改）。
- 动态加载：`src/views/prototype/PrototypePageView.vue` 用 `import.meta.glob` 按 slug 懒加载组件。
- 生成脚本：以前は `scripts/`（`generate-ui-pages.mjs` / `render-ui-pages-from-browser.mjs`）に置いていたが、そのディレクトリは削除済み。再生成が必要なときは git 履歴から復元するか、`tmp/` 配下に置いて使う。
- 路由：`/{area}/home` 与 `/{area}/:screen` 均落到 `PrototypePageView`。
- 本区域是**临时迁移页面**，不是已完成业务功能；页面头部显示「UI移行版」徽章标识。

### 文件名含 `demo` 的页面（暂保留，后续正式迁移时再重命名）

| slug | 源文件 | 组件 |
|---|---|---|
| `link-clip-demo` | `link_clip_demo.html` | `LinkClipDemoPage.vue` |
| `japanese-test-demo` | `japanese_test_demo.html` | `JapaneseTestDemoPage.vue` |

## 7. 公共样式唯一来源

- 唯一来源：`src/assets/prototype/*.css`（与 ui-demo `assets/css/*.css` 逐字节一致，共 9 个文件）。
- `src/main.ts` 只加载这 9 个文件；不再加载 `@study21/web-shared/styles` 与旧 `src/assets/main.css`（已删除）。
- 页面专用样式（`generated/*.vue` 的 `<style scoped>`）不得污染公共 Layout。
