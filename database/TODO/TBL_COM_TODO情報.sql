-- ============================================================================
-- Study 2.1  TODO DDL（最終仕様）
-- テーブル: COM_TODO情報
-- ----------------------------------------------------------------------------
-- 2.0 の COM_TODO管理情報（169 件。うち子タスク 33 件）を 2.1 の規約で再設計する。
-- 親子は 親TODOID の自己参照で表す（2.0 と同じ。子タスク専用の表は作らない）。
--
-- 2.0 からの主な変更:
--   1. 持ち主を ユーザーID（'ljz' など）から アカウントID（ACC_アカウント への FK）へ。
--      2.0 の ID も 旧ユーザーID として残す（日報と同じ扱い）。
--   2. 状態は 'O'/'C'/'X'/'D' の 1 文字だったので、コード（TODO / DOING / DONE）へ。
--      優先度も 'H'/'M'/'L' → コード（HIGH / NORMAL / LOW）へ。
--   3. 誰が作ったかを 作成者アカウントID で持つ（保護者が子どもの TODO を作れるため）。
--      2.0 の 作成者 は表示名だけだったので 旧作成者名 に残す。
--   4. 監査・楽観的ロック（バージョン）を 2.1 の規約に統一。
--   5. 索引を 2.1 の参照パターン（持ち主×期限、親子、状態）に合わせた。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."COM_TODO情報" (
    "TODOID"             BIGSERIAL    NOT NULL,
    -- 持ち主（生徒）
    "アカウントID"       BIGINT       NOT NULL,
    -- 作った人（本人・保護者・管理者）。移行データは NULL のこともある
    "作成者アカウントID" BIGINT       NULL,
    -- 親タスク（NULL = 親タスク自身）
    "親TODOID"           BIGINT       NULL,
    -- 同じ親の中での並び順
    "表示順"             INTEGER      NOT NULL DEFAULT 0,
    "タイトル"           VARCHAR(200) NOT NULL,
    "メモ"               TEXT         NULL,
    -- TODO=未着手 / DOING=進行中 / DONE=完了
    "状態コード"         VARCHAR(10)  NOT NULL DEFAULT 'TODO',
    -- HIGH / NORMAL / LOW
    "優先度コード"       VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    "期限日"             DATE         NULL,
    "実施開始日時"       TIMESTAMP    NULL,
    "実施完了日時"       TIMESTAMP    NULL,
    -- 2.0 の クライアントTODOID（端末側で採番した ID。同期用に残す）
    "クライアントTODOID" VARCHAR(64)  NULL,
    "旧ユーザーID"       VARCHAR(50)  NULL,
    "旧作成者名"         VARCHAR(50)  NULL,
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NULL,
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "COM_TODO情報_pkey" PRIMARY KEY ("TODOID"),
    CONSTRAINT "FK_TODO_アカウント"
        FOREIGN KEY ("アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_TODO_作成者"
        FOREIGN KEY ("作成者アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_TODO_親" FOREIGN KEY ("親TODOID") REFERENCES public."COM_TODO情報" ("TODOID") ON DELETE CASCADE,
    CONSTRAINT "FK_TODO_登録者"
        FOREIGN KEY ("登録者アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_TODO_更新者"
        FOREIGN KEY ("更新者アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_TODO_状態" CHECK ("状態コード" IN ('TODO', 'DOING', 'DONE')),
    CONSTRAINT "CK_TODO_優先度" CHECK ("優先度コード" IN ('HIGH', 'NORMAL', 'LOW')),
    CONSTRAINT "CK_TODO_タイトル" CHECK (BTRIM("タイトル") <> ''),
    CONSTRAINT "CK_TODO_親" CHECK ("親TODOID" IS NULL OR "親TODOID" <> "TODOID"),
    CONSTRAINT "CK_TODO_バージョン" CHECK ("バージョン" > 0),
    -- 完了は実施完了日時が入っていること（状態と時刻の食い違いを防ぐ）
    CONSTRAINT "CK_TODO_完了日時" CHECK ("状態コード" <> 'DONE' OR "実施完了日時" IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_todo_account_due
    ON public."COM_TODO情報" ("アカウントID", "期限日");
CREATE INDEX IF NOT EXISTS idx_todo_parent
    ON public."COM_TODO情報" ("親TODOID", "表示順");
CREATE INDEX IF NOT EXISTS idx_todo_open
    ON public."COM_TODO情報" ("アカウントID", "状態コード")
    WHERE "状態コード" <> 'DONE';

COMMENT ON TABLE public."COM_TODO情報" IS
    'TODO（親子は 親TODOID の自己参照。子タスクも同じ表に入る）';
COMMENT ON COLUMN public."COM_TODO情報"."アカウントID" IS '持ち主（生徒）。保護者が作った場合も持ち主は子ども';
COMMENT ON COLUMN public."COM_TODO情報"."作成者アカウントID" IS '作った人。保護者が子どもの TODO を作った場合は保護者のアカウント';
COMMENT ON COLUMN public."COM_TODO情報"."状態コード" IS 'TODO=未着手 / DOING=進行中 / DONE=完了';
COMMENT ON COLUMN public."COM_TODO情報"."優先度コード" IS 'HIGH / NORMAL / LOW';
