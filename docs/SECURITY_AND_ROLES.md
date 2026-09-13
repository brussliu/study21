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

## 2.1 当面の運用（2026-09-12・ユーザーの指定）

**当面はロールで機能を分けない。** すべての機能を学生にも開放し、検証も学生だけで行う
（保護者・管理者の権限設計は後でまとめて決める）。そのため:

- 左メニューは 3 エリア（`/admin` `/student` `/parent`）で同じものを出す（`resolveMenu` は
  `role` を受け取るが使っていない）。
- user-api は「ログイン必須」だけを見る。2.0 が保護者限定だった端末コントロールも、
  ログインしていれば学生が使える（`NetTerminalServiceImpl#requireLogin`）。
- admin-api（`/api/admin/**`）はスケルトンのため認証を付けていない（`BatchController` の
  コメント参照）。学生の画面（`/{area}/batch` など）からも同じ API を呼ぶ。
- 学生で全画面が開けることは `tmp/e2e/e2e-student-screens.mjs`（98 項目）で確認する。
  実装済みの 15 画面を順に開き、権限エラー・API エラー・コンソールエラーが出ないことを見る。

例外として「保護者が子どもの分も作る」操作（TODO・リンククリップの
**お子さまにも登録する**）は、ロールではなく**親子の紐付けがあるかどうか**で決まる。
自分のアカウントに子どもが紐づいていない場合は 400 を返し、学生の画面にはチェックボックスを出さない
（`alsoForStudent`。データの関係であって権限ではない。`docs/TODO/TODO設計.md` §4）。

役割ごとの制限を入れるときは、この節を書き換えて `docs/ROLE_FUNCTION_MATRIX.md` を更新する。

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
