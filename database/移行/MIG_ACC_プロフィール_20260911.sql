-- ============================================================================
-- Study 2.1  アカウント  「ユーザー情報の修正」用の列を追加
--
-- 対象: public."ACC_アカウント"
-- 追加する列:
--   "電話番号"         VARCHAR(20) NULL                 … 任意入力
--   "メール通知"       VARCHAR(1)  NOT NULL DEFAULT '1' … '1'=受け取る / '0'=受け取らない
--   "リマインダー通知" VARCHAR(1)  NOT NULL DEFAULT '0'
--
-- 背景: 2.1 では「ユーザー情報の修正」画面を実装するにあたり、2.0 のデモ画面に
--       あった電話番号・通知設定を保存できるようにする（メールアドレスは
--       ログインID を兼ねるため変更不可）。
--
-- 冪等: 既に列・制約がある場合は何もしない（再実行可）。
-- 実行: psql -h <host> -p <port> -U <user> -d study21 -f MIG_ACC_プロフィール_20260911.sql
-- ============================================================================

BEGIN;

ALTER TABLE public."ACC_アカウント"
    ADD COLUMN IF NOT EXISTS "電話番号"         VARCHAR(20) NULL,
    ADD COLUMN IF NOT EXISTS "メール通知"       VARCHAR(1)  NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS "リマインダー通知" VARCHAR(1)  NOT NULL DEFAULT '0';

COMMENT ON COLUMN public."ACC_アカウント"."電話番号" IS '電話番号（任意。ハイフン有無は問わない）';
COMMENT ON COLUMN public."ACC_アカウント"."メール通知" IS '通知設定: ''1''=更新のお知らせをメールで受け取る / ''0''=受け取らない';
COMMENT ON COLUMN public."ACC_アカウント"."リマインダー通知" IS '通知設定: ''1''=学習リマインダーを受け取る / ''0''=受け取らない';

-- 通知設定は '0' / '1' のみ（「状態」と同じ規約）。ADD CONSTRAINT は IF NOT EXISTS を
-- 持たないため、存在チェックしてから付ける。
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'CK_ACC_メール通知'
           AND conrelid = 'public."ACC_アカウント"'::regclass
    ) THEN
        ALTER TABLE public."ACC_アカウント"
            ADD CONSTRAINT "CK_ACC_メール通知" CHECK ("メール通知" IN ('0', '1'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'CK_ACC_リマインダー通知'
           AND conrelid = 'public."ACC_アカウント"'::regclass
    ) THEN
        ALTER TABLE public."ACC_アカウント"
            ADD CONSTRAINT "CK_ACC_リマインダー通知" CHECK ("リマインダー通知" IN ('0', '1'));
    END IF;
END
$$;

COMMIT;

\echo 'ACC_アカウント: 電話番号 / メール通知 / リマインダー通知 を追加しました。'
