-- ============================================================================
-- Study 2.1  パスワードリセット  DDL
-- テーブル: ACC_パスワードリセット   （★2.1 新設テーブル）
-- ----------------------------------------------------------------------------
-- パスワード再設定（パスワードをお忘れの方）用の一時トークン管理。
-- トークンは平文を保存せず SHA-256 ハッシュのみ保持する。
-- 有効期限超過・使用済みのトークンは再設定に使えない。
-- 対象DB: study21 (PostgreSQL)
-- 実行順: TBL_ACC_アカウント.sql の後。
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."ACC_パスワードリセット" (
    "リセットID"     BIGSERIAL   PRIMARY KEY,
    "アカウントID"   BIGINT      NOT NULL,
    -- SHA-256（16進 64 文字）。生トークンは保存しない。
    "トークンハッシュ" VARCHAR(64) NOT NULL,
    -- トークンの有効期限（発行 + 30 分）
    "有効期限"       TIMESTAMP   NOT NULL,
    -- '0' = 未使用 / '1' = 使用済み
    "使用済みフラグ" VARCHAR(1)  NOT NULL DEFAULT '0',
    "登録ID"         VARCHAR(20) NULL,
    "更新ID"         VARCHAR(20) NULL,
    "登録日時"       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "FK_ACC_リセット_アカウント" FOREIGN KEY ("アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_ACC_リセット_使用済み" CHECK ("使用済みフラグ" IN ('0', '1'))
);

-- トークン照合用（一意）
CREATE UNIQUE INDEX IF NOT EXISTS uq_acc_password_reset_token
    ON public."ACC_パスワードリセット" ("トークンハッシュ");

-- アカウント別の未使用トークン探索用
CREATE INDEX IF NOT EXISTS idx_acc_password_reset_account
    ON public."ACC_パスワードリセット" ("アカウントID", "使用済みフラグ");
