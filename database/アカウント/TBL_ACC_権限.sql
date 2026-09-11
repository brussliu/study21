-- Study 2.1 / PostgreSQL 18
-- 人員・権限: ロール、権限、ロール権限。アカウントは既存 ACC_アカウントを拡張して使用。
-- 実行順序・設計意図: ACC_人员权限设计.md
BEGIN;

CREATE TABLE IF NOT EXISTS public."ACC_ロール" (
    "ロールコード" VARCHAR(20) PRIMARY KEY,
    "ロール名" VARCHAR(100) NOT NULL,
    "説明" VARCHAR(500),
    "状態" VARCHAR(1) NOT NULL DEFAULT '1',
    "登録ID" VARCHAR(20),
    "更新ID" VARCHAR(20),
    "登録日時" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_acc_role_code CHECK ("ロールコード" IN ('STUDENT', 'GUARDIAN', 'ADMIN')),
    CONSTRAINT ck_acc_role_name CHECK (length(btrim("ロール名")) > 0),
    CONSTRAINT ck_acc_role_status CHECK ("状態" IN ('0', '1'))
);
COMMENT ON TABLE public."ACC_ロール" IS '学生・保護者・管理者のロール。1アカウントは1ロールのみ。';
COMMENT ON COLUMN public."ACC_ロール"."状態" IS '1=有効、0=無効。無効ロールへの認可はアプリで拒否。';

CREATE TABLE IF NOT EXISTS public."ACC_権限" (
    "権限コード" VARCHAR(100) PRIMARY KEY,
    "権限名" VARCHAR(100) NOT NULL,
    "説明" VARCHAR(500),
    "状態" VARCHAR(1) NOT NULL DEFAULT '1',
    "登録ID" VARCHAR(20),
    "更新ID" VARCHAR(20),
    "登録日時" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_acc_permission_code CHECK ("権限コード" ~ '^[a-z][a-z0-9_.]*$'),
    CONSTRAINT ck_acc_permission_name CHECK (length(btrim("権限名")) > 0),
    CONSTRAINT ck_acc_permission_status CHECK ("状態" IN ('0', '1'))
);
COMMENT ON TABLE public."ACC_権限" IS '業務操作単位の権限カタログ。画面表示のみでなくAPI側で検証する。';

CREATE TABLE IF NOT EXISTS public."ACC_ロール権限" (
    "ロールコード" VARCHAR(20) NOT NULL,
    "権限コード" VARCHAR(100) NOT NULL,
    "データ範囲" VARCHAR(24) NOT NULL,
    "登録ID" VARCHAR(20),
    "更新ID" VARCHAR(20),
    "登録日時" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY ("ロールコード", "権限コード", "データ範囲"),
    CONSTRAINT fk_acc_grant_role FOREIGN KEY ("ロールコード")
        REFERENCES public."ACC_ロール" ("ロールコード") ON DELETE RESTRICT,
    CONSTRAINT fk_acc_grant_permission FOREIGN KEY ("権限コード")
        REFERENCES public."ACC_権限" ("権限コード") ON DELETE RESTRICT,
    CONSTRAINT ck_acc_grant_scope CHECK (
        ("ロールコード" = 'STUDENT' AND "データ範囲" = 'SELF')
        OR ("ロールコード" = 'GUARDIAN' AND "データ範囲" IN ('SELF', 'LINKED_STUDENT'))
        OR ("ロールコード" = 'ADMIN' AND "データ範囲" IN ('SELF', 'ALL'))
    ),
    -- 管理権限は学生・保護者へ付与できない。新規権限の割当可否は管理APIでも検証する。
    CONSTRAINT ck_acc_management_grant CHECK (
        "権限コード" NOT IN ('account.create', 'account.status.update', 'account.role.update',
                              'role.read', 'role.permission.update')
        OR ("ロールコード" = 'ADMIN' AND "データ範囲" = 'ALL')
    )
);
CREATE INDEX IF NOT EXISTS idx_acc_grant_permission ON public."ACC_ロール権限" ("権限コード");
COMMENT ON TABLE public."ACC_ロール権限" IS 'ロール別の許可リスト。行が存在しない操作は拒否。個人への直接付与は行わない。';
COMMENT ON COLUMN public."ACC_ロール権限"."データ範囲" IS
    'SELF=本人、LINKED_STUDENT=ログイン保護者に紐づく学生1名、ALL=全体。対象行の絞込みはAPIで実装する。';

COMMIT;
