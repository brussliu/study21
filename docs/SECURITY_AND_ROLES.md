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

**例外 2（2026-09-14・読書管理）**: 読書管理だけ、この節より先にロールで機能を分けた
（利用者の決定 Q1〜Q9）。理由は「全体書籍（管理者が登録した本）と家庭の本（保護者が登録した本）を
分け、【自分の本棚】をアカウントごとに持つ」ためで、本の可視範囲と操作可否がロールと所有で決まる。

- 管理者（`ADMIN`）… 全体書籍（`公開範囲コード='GLOBAL'`）だけが見え、登録・修正・削除できる
- 保護者（`GUARDIAN`）… 全体書籍 ＋ 自分の家庭の本（`FAMILY`）。直せるのは**自分の家庭の本だけ**
- 生徒（`STUDENT`）… 見える本を読む・標記・記録。【自分の本棚】の出し入れ。登録・修正・削除は 403
- 見えない本は **404**（存在を漏らさない）、見えるが操作できないときは **403**
- メニューの【書籍管理】は**生徒に出さない**（ルートは残るが API が 403。決定 Q9）
- そのため user-api は**管理者（`ADMIN`）も認証する**（`AccountType.ADMIN` を追加。決定 Q8）。
  管理者の初期アカウントは移行 `database/移行/MIG_ACC_管理者_アカウント_20260914.sql` で 1 行だけ作り、
  **初回ログイン後にパスワードを変更する**（§2.1 の「当面ロールで分けない」の対象外）
- 実装は `ReadingScope`（可視範囲）と `ReadingServiceImpl`（`requireBook` / `requireManage`）。
  一覧・件数・サマリ・分類の冊数・PDF 配信に同じ可視条件を当てる

役割ごとの制限を入れるときは、この節を書き換えて `docs/ROLE_FUNCTION_MATRIX.md` を更新する。

## 3. 操作权限

后端预留 `PermissionChecker` 接口（`hasRole` / `hasAnyRole` / `requireRole` / `requireAnyRole`），
本阶段不实现具体检查。

## 4. 数据权限

数据权限（例如家长只能看到自己孩子数据）尚未设计，本阶段不实现。

## 5. 认证与登录状态（现状）

**user-api（保護者・生徒）… 実装済み**

- アカウントは DB（`ACC_アカウント`）にあり、パスワードは BCrypt で照合する。
- ログインすると `HttpSession` に `UserPrincipal`（アカウントID・ログインID・表示名・種別）を入れる。
- **セッションは Redis に保存する**（Spring Session。`spring.session.store-type=redis`、
  名前空間 `study21:session:user`）。
  - 有効期限は **60 分**（最後のアクセスから。`spring.session.timeout`）
  - クッキー名は `STUDY21_USER_SESSION`（HttpOnly / SameSite=Lax）
  - user-api を再起動・作り直してもログインしたままになる
- `docs/DEPLOYMENT.md` §2.5・`docs/DOCKER_DEPLOYMENT.md` に運用（Redis コンテナ）を書いてある。

**admin-api（管理者）… まだ骨組み**

- `/api/admin/login` は DB の管理者アカウントと BCrypt で照合するだけで、
  サーバー側にセッションを作らない（`SessionCreationPolicy.STATELESS`）。
  そのため Redis にも繋いでいない。
- 管理機能（バッチ・システム設定）は当面 `permitAll`（認証を本格化するときに
  `ADMIN` ロール必須へ変える。そのときは user-api と同じ Redis 設定を足し、
  クッキー名と名前空間は分ける）。

**PC 前端**

- ログイン結果（表示名・ロール）は `sessionStorage`（`study21.auth.v2`）にも持つ。
  これは画面の出し分け用で、**認証そのものはサーバーのセッション**が根拠。
- したがってロールを書き換えても、サーバー側の権限判定（403 等）は変わらない。

## 6. 安全默认策略

- `health` 与 `system/info` 允许匿名访问。
- 其余未定义路径默认拒绝（denyAll）。
- admin-api 与 user-api 使用不同安全配置。
- CORS 允许来源从环境变量读取，禁止 `*`。
- 日志不输出敏感信息。
