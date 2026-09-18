-- ============================================================================
-- Study 2.1  アカウント 移行: 初期管理者アカウント（seed）
-- ----------------------------------------------------------------------------
-- 目的（2026-09-14 の決定 Q8）:
--   読書管理の【全体書籍】は**管理者が管理する**ことにした。そのためには管理者が
--   user-api のセッションを持てる必要がある（pc-web の管理者画面から読書 API を
--   呼ぶ）。これまで user-api は管理者を認証せず（`AccountType` は保護者・生徒のみ）、
--   しかも `ACC_アカウント` に ADMIN の行が 1 件も無かったので、**初期管理者を 1 行だけ
--   入れる**。あわせて user-api 側に ADMIN ログインを足してある（別作業）。
--
-- ★ 初期ログイン情報（このファイルで作るアカウント）★
--     ログインID : admin@study21.local
--     パスワード : Admin1234!
--   パスワードは BCrypt ハッシュ（コスト 10）で保存する。平文は保存されない。
--
-- ★★ 初回ログイン後に必ずパスワードを変更すること ★★
--   このファイルのパスワードは**リポジトリに書いてある初期値**なので、
--   運用に入る前に必ず変更する（変更後はこのファイルの初期値は使えなくなる）。
--   ・一般ユーザーの画面（/api/user/password/**）は保護者・生徒専用のため、
--     管理者のパスワード変更は管理側の手順（管理者アカウントの更新）で行う。
--
-- 対象:
--   追加する行は **ADMIN 1 行だけ**。既存の 14 行（保護者・生徒・検証用アカウント）は
--   一切変更しない。管理者は DDL（TBL_ACC_アカウント.sql）の制約により
--   有効期限・学年・保護者ID を持たない（姓名は必須）。
--
-- 事前条件:
--   1. database/アカウント/TBL_ACC_アカウント.sql 適用済み（`アカウント種別='ADMIN'` を許す）。
--   2. user-api に ADMIN ログインが入っていること（この移行だけでは画面は動かない）。
--
-- 実行例:
--   psql -h 192.168.0.100 -p 54320 -U postgres -d study21 -v ON_ERROR_STOP=1 \
--        -f database/移行/MIG_ACC_管理者_アカウント_20260914.sql
--
-- 冪等性:
--   ログインID（大文字小文字を無視）で `WHERE NOT EXISTS` してから入れるので、
--   何度実行しても増えない（`uq_acc_login_id` は LOWER("ログインID") の一意索引）。
--
-- ロールバック手順:
--   DELETE FROM public."ACC_アカウント"
--    WHERE LOWER("ログインID") = 'admin@study21.local' AND "アカウント種別" = 'ADMIN';
--   ※ 読書の本をこの管理者が登録していた場合、監査列の FK（ON DELETE RESTRICT）で
--     消せない。先に本を削除するか、状態 '0' で停用する（通常は停用で運用）。
--
-- 配備先でも同じように実行すること（DDL と移行は利用者が手で流す運用）。
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

BEGIN;

INSERT INTO public."ACC_アカウント"
    ("ログインID", "パスワードハッシュ", "アカウント種別", "状態",
     "姓", "名", "学年", "保護者ID", "有効期限", "利用規約同意日時",
     "登録ID", "更新ID", "登録日時", "更新日時")
SELECT 'admin@study21.local',
       '$2a$10$KVbLt1UCoXlzTIPwpTBrs.pSQawI9YIWRAPQuddmXiziGE5hoUbxK',  -- Admin1234!（BCrypt・初回変更必須）
       'ADMIN', '1',
       'システム', '管理者', NULL, NULL, NULL, NULL,
       'MIGRATION', 'MIGRATION', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
 WHERE NOT EXISTS (
        SELECT 1 FROM public."ACC_アカウント"
         WHERE LOWER("ログインID") = 'admin@study21.local'
       );

-- 結果確認
\echo '--- 管理者アカウント（1 行・有効期限なしが期待値）'
SELECT "アカウントID", "ログインID", "アカウント種別", "状態", "有効期限"
  FROM public."ACC_アカウント"
 WHERE "アカウント種別" = 'ADMIN'
 ORDER BY "アカウントID";
\echo '--- 種別ごとの件数（GUARDIAN/STUDENT が増えていないこと）'
SELECT "アカウント種別", count(*) AS 件数
  FROM public."ACC_アカウント"
 GROUP BY "アカウント種別"
 ORDER BY "アカウント種別";
\echo '--- アカウント総数（この移行で増えるのは ADMIN の 1 行だけ）'
SELECT count(*) AS アカウント総数 FROM public."ACC_アカウント";

COMMIT;
