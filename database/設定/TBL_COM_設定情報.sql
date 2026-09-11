-- ============================================================================
-- Study 2.1  設定値  DDL
-- テーブル: COM_設定情報
-- ----------------------------------------------------------------------------
-- 設定値を保持する。scope（スコープ）+ 学生ID / 保護者ID で所属を明示する。
--
-- スコープ:
--   GLOBAL                管理者が保守するグローバル設定
--   STUDENT               特定学生のみ（保護者へ自動公開しない）
--   PARENT                特定保護者のみ（学生へ自動公開しない）
--   STUDENT_PARENT_SHARED 特定学生×特定保護者の組み合わせのみ共有
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."COM_設定情報" (
    "設定ID"       BIGSERIAL    NOT NULL,
    "ページ区分"    VARCHAR(64)  NOT NULL,
    "設定キー"      VARCHAR(128) NOT NULL,
    "スコープ"      VARCHAR(24)  NOT NULL,
    "学生ID"        VARCHAR(64)  NULL,
    "保護者ID"      VARCHAR(64)  NULL,
    "設定値"        TEXT         NULL,
    "備考"          VARCHAR(500) NULL,
    "登録ID"        VARCHAR(64)  NULL,
    "更新ID"        VARCHAR(64)  NULL,
    "登録日時"      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "PK_COM_設定情報" PRIMARY KEY ("設定ID"),
    CONSTRAINT "FK_COM_設定情報_項目" FOREIGN KEY ("ページ区分", "設定キー")
        REFERENCES public."COM_設定項目" ("ページ区分", "設定キー"),
    CONSTRAINT "CK_COM_設定情報_スコープ" CHECK (
        "スコープ" IN ('GLOBAL','STUDENT','PARENT','STUDENT_PARENT_SHARED')
    ),
    CONSTRAINT "CK_COM_設定情報_スコープ_所有者" CHECK (
        ("スコープ" = 'GLOBAL'
             AND "学生ID" IS NULL AND "保護者ID" IS NULL)
        OR
        ("スコープ" = 'STUDENT'
             AND "学生ID" IS NOT NULL AND "保護者ID" IS NULL)
        OR
        ("スコープ" = 'PARENT'
             AND "学生ID" IS NULL AND "保護者ID" IS NOT NULL)
        OR
        ("スコープ" = 'STUDENT_PARENT_SHARED'
             AND "学生ID" IS NOT NULL AND "保護者ID" IS NOT NULL)
    )
);

-- 一意制約（スコープごとの重複防止）
CREATE UNIQUE INDEX IF NOT EXISTS "UK_COM_設定情報_global"
    ON public."COM_設定情報" ("ページ区分", "設定キー")
    WHERE "スコープ" = 'GLOBAL';

CREATE UNIQUE INDEX IF NOT EXISTS "UK_COM_設定情報_student"
    ON public."COM_設定情報" ("ページ区分", "設定キー", "学生ID")
    WHERE "スコープ" = 'STUDENT';

CREATE UNIQUE INDEX IF NOT EXISTS "UK_COM_設定情報_parent"
    ON public."COM_設定情報" ("ページ区分", "設定キー", "保護者ID")
    WHERE "スコープ" = 'PARENT';

CREATE UNIQUE INDEX IF NOT EXISTS "UK_COM_設定情報_shared"
    ON public."COM_設定情報" ("ページ区分", "設定キー", "学生ID", "保護者ID")
    WHERE "スコープ" = 'STUDENT_PARENT_SHARED';

-- 検索用インデックス
CREATE INDEX IF NOT EXISTS "IDX_COM_設定情報_ページキー"
    ON public."COM_設定情報" ("ページ区分", "設定キー");
CREATE INDEX IF NOT EXISTS "IDX_COM_設定情報_学生"
    ON public."COM_設定情報" ("学生ID")
    WHERE "学生ID" IS NOT NULL;
CREATE INDEX IF NOT EXISTS "IDX_COM_設定情報_保護者"
    ON public."COM_設定情報" ("保護者ID")
    WHERE "保護者ID" IS NOT NULL;
