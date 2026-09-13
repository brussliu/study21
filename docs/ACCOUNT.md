# アカウント（ユーザー情報の修正 / パスワードの変更）

保護者・生徒が自分の情報を修正し、パスワードを変更する機能。
**2 つの機能は別ページ**で提供する（旧 2.0 のデモダイアログを置き換えたもの）。

- 対象ロール: `GUARDIAN` / `STUDENT`（`user-api`）
- 管理者（`ADMIN`）は対象外。右上メニューにも項目を出さない
  （admin-api は無状態・認証未実装で、管理者の「自分」を識別できないため）
- mobile-web は現在**骨組みのみ**。この機能は PC（`pc-web`）だけに実装している

## 1. 画面（ダイアログ）

**ページ遷移はしない**。右上のユーザー名メニュー（`AppTopbar.vue`）から
2 つの独立したダイアログを開く。

| 機能 | 開き方 | コンポーネント |
|---|---|---|
| ユーザー情報の修正 | メニュー「ユーザー情報の修正」 | `frontend/pc-web/src/components/account/UserProfileDialog.vue` |
| パスワードの変更 | メニュー「パスワードの変更」 | `frontend/pc-web/src/components/account/PasswordChangeDialog.vue` |

- 保存すると **親ページ（開いていた画面）を再読み込み**して変更を反映する
  （`features/user-profile/reloadPage.ts` の `reloadParentPage()`。表示名も再読み込み後に反映される）。
- 旧 2.0 デモの `components/layout/UserProfileDialog.vue` は削除済み。
  ページ版（`views/account/*` と `/student/profile` などのルート）も廃止した。

### 編集できる項目（ユーザー情報の修正）

| 項目 | 必須 | 備考 |
|---|---|---|
| 姓 / 名 | 必須 | 100 文字以内 |
| ふりがな（せい / めい） | 生徒は必須 | 保護者は任意（空なら NULL で保存） |
| 学年 | 生徒は必須 | 保護者には項目自体を出さない（DB の `CK_ACC_学年種別` を守る） |
| 電話番号 | 任意 | 数字・ハイフン・括弧・空白・`+`、20 文字以内 |
| 更新のお知らせをメールで受け取る | - | `メール通知`（既定 '1'） |
| 学習リマインダーを受け取る | - | `リマインダー通知`（既定 '0'） |
| メールアドレス | **変更不可** | ログインID を兼ねるため参照のみ（リクエストにも項目が無い） |
| 権限 | **変更不可** | 参照のみ（管理者のみ変更できる） |

### パスワードの変更

- 現在のパスワードで本人確認する（違えば 400 `現在のパスワードが正しくありません。`）。
- 新しいパスワードは **8 文字以上・英字と数字をそれぞれ 1 文字以上**（登録画面と同じ規則）。
- 現在と同じパスワードは拒否する。
- 確認用入力は画面側だけで照合し、API へは送らない。
- 変更後もログインは継続する（再ログインは求めない）。

## 2. API（user-api）

| メソッド | パス | 説明 |
|---|---|---|
| `GET` | `/api/user/profile` | 自分の情報を取得 |
| `PUT` | `/api/user/profile` | 編集できる項目を更新（メールアドレス・権限は対象外） |
| `POST` | `/api/user/profile/password` | パスワードを変更（現在のパスワードで本人確認） |

- 実装: `backend/user-api/src/main/java/com/study21/user/`
  - `controller/AccountProfileController.java`
  - `account/AccountProfileService(.java|Impl.java)` / `ProfileUpdateRequest` / `PasswordChangeRequest` / `UserProfileResponse`
  - `account/AccountMapper(.java|.xml)` … `findById` / `updateProfile` / `updatePasswordHashBySelf`
- 認証: セッションの `UserPrincipal` から自分のアカウントを特定する（他人の情報は扱わない）。
  `SecurityConfig` で `/api/user/profile/**` は**ログイン必須**（`/api/user/password/**` の
  再設定用公開エンドポイントとは別のパスにしている）。
- パスワードは BCrypt で再ハッシュ化して保存する（平文は保持しない）。
- DB 操作は `SqlLoggingInterceptor` が `logs/backend/user-api-sql.log` へ記録する
  （`updateProfile` / `updatePasswordHashBySelf` も 1 操作 = 1 行で出る）。

## 3. データベース

`ACC_アカウント` に次の列を使う（`database/アカウント/TBL_ACC_アカウント.sql` が最終仕様）。

| 列 | 型 | 説明 |
|---|---|---|
| `電話番号` | VARCHAR(20) NULL | 任意 |
| `メール通知` | VARCHAR(1) NOT NULL DEFAULT '1' | `'1'`=受け取る / `'0'`=受け取らない |
| `リマインダー通知` | VARCHAR(1) NOT NULL DEFAULT '0' | 同上（`CK_ACC_メール通知` / `CK_ACC_リマインダー通知` で '0'/'1' のみ許可） |

既存 DB には移行スクリプトを適用する（冪等・再実行可）:

```bash
psql -h <host> -p <port> -U <user> -d study21 -f database/移行/MIG_ACC_プロフィール_20260911.sql
```

## 4. テスト

| テスト | 場所 | 見るもの |
|---|---|---|
| 業務ルール（必須項目・種別ごとの扱い・本人確認・保存値の変換） | `backend/user-api/src/test/java/com/study21/user/account/AccountProfileServiceImplTest.java` | 10 tests |
| 実 DB の SQL（新列・CHECK・メール不変・ハッシュ更新。ロールバックする） | `.../account/AccountProfileRepositoryTest.java` | 3 tests（`STUDY21_DATASOURCE_PASSWORD` 未設定時はスキップ） |
| 画面（別ページであること・バリデーション・API 呼び出し・表示名更新） | `frontend/pc-web/tests/user-profile.spec.ts` | 16 tests |
| 入力規則の共有 | `frontend/packages/web-shared/src/rules/accountRules.ts` | パスワード・電話・学年の選択肢 |

実 DB テストの実行:

```bash
cd backend && STUDY21_DATASOURCE_PASSWORD=<パスワード> mvn -pl user-api -am test
```

ブラウザでの実機確認は `tmp/e2e/e2e-profile.mjs`（検証用の一時ファイル）。
ローカルの user-api（8083）と実 DB を使い、画面から保存・パスワード変更まで通す。
