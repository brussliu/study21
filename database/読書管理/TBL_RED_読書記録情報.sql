-- ============================================================================
-- Study 2.1  読書管理 DDL（最終仕様）
-- テーブル: RED_読書記録情報
-- ----------------------------------------------------------------------------
-- 「今回どこからどこまで読んで、何分読んで、標記を何件付けたか」の履歴。
-- 2.0 の `TRN_英語読書記録情報`（実データ 326 件）に当たる。
--
-- 2.0 からの主な変更:
--   1. 主キーを 記録SEQ（bigserial）から 2.1 の規約どおり 記録ID に改名。
--   2. 親（書籍）を 書籍番号（VARCHAR）から 書籍ID（BIGINT・FK）へ。
--      書籍を消したら記録も消える（ON DELETE CASCADE）。
--   3. 監査を 2.1 の規約に統一（登録者/更新者アカウントID・登録元コード）。
--
-- 記録を付けると RED_書籍情報 側も一緒に更新する（サービス層で 1 トランザクション）:
--   現在ページ / 最近の読書時間分 / 累計読書時間分 / 累計標記件数 / 最終読書日時 /
--   読書ステータス（未着手なら 読書中へ）
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."RED_読書記録情報" (
    "記録ID"             BIGSERIAL    NOT NULL,
    "書籍ID"             BIGINT       NOT NULL,
    -- 今回の読書を記録した日時
    "読書日時"           TIMESTAMP    NOT NULL,
    "開始ページ"         INTEGER      NULL,
    "終了ページ"         INTEGER      NULL,
    -- 今回の読書時間（分）
    "読書時間分"         INTEGER      NOT NULL DEFAULT 0,
    -- 今回追加・更新した標記の件数
    "標記件数"           INTEGER      NOT NULL DEFAULT 0,
    "メモ"               TEXT         NULL,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "RED_読書記録情報_pkey" PRIMARY KEY ("記録ID"),
    CONSTRAINT "FK_RED_読書記録_書籍"
        FOREIGN KEY ("書籍ID")
        REFERENCES public."RED_書籍情報" ("書籍ID") ON DELETE CASCADE,
    CONSTRAINT "FK_RED_読書記録_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_RED_読書記録_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_RED_読書記録_開始ページ"
        CHECK ("開始ページ" IS NULL OR "開始ページ" >= 1),
    CONSTRAINT "CK_RED_読書記録_終了ページ"
        CHECK ("終了ページ" IS NULL OR "終了ページ" >= 1),
    CONSTRAINT "CK_RED_読書記録_ページ範囲"
        CHECK ("開始ページ" IS NULL OR "終了ページ" IS NULL OR "終了ページ" >= "開始ページ"),
    CONSTRAINT "CK_RED_読書記録_読書時間分"
        CHECK ("読書時間分" >= 0),
    CONSTRAINT "CK_RED_読書記録_標記件数"
        CHECK ("標記件数" >= 0)
);

-- 書籍ごとの履歴（新しい順）
CREATE INDEX IF NOT EXISTS idx_red_record_book
    ON public."RED_読書記録情報" ("書籍ID", "読書日時" DESC, "記録ID" DESC);

-- 全書籍の履歴（書籍管理画面の「読書履歴」）
CREATE INDEX IF NOT EXISTS idx_red_record_read_at
    ON public."RED_読書記録情報" ("読書日時" DESC, "記録ID" DESC);

COMMENT ON TABLE public."RED_読書記録情報" IS
    '読書の記録（今回読んだページ・時間・標記件数・メモ）。2.0 の TRN_英語読書記録情報';
COMMENT ON COLUMN public."RED_読書記録情報"."読書日時" IS '今回の読書を記録した日時';
COMMENT ON COLUMN public."RED_読書記録情報"."開始ページ" IS '今回読んだ開始ページ';
COMMENT ON COLUMN public."RED_読書記録情報"."終了ページ" IS '今回読んだ終了ページ';
COMMENT ON COLUMN public."RED_読書記録情報"."読書時間分" IS '今回の読書時間（分）';
COMMENT ON COLUMN public."RED_読書記録情報"."標記件数" IS '今回追加・更新した標記の件数';
COMMENT ON COLUMN public."RED_読書記録情報"."登録元コード" IS 'APP=画面からの登録 / MIGRATION=2.0 からの移行';
