-- ============================================================================
-- Study 2.1  読書管理 DDL（最終仕様）
-- テーブル: RED_標記情報
-- ----------------------------------------------------------------------------
-- 読書中に付けた「標記」＝ハイライト / 下線 / メモ / 語彙。2.0 の
-- `TRN_英語読書標記情報`（実データ 838 件。うち語彙 818 件）に当たる。
--
-- 2.0 からの主な変更:
--   1. 主キーを 標記SEQ（bigserial）から 2.1 の規約どおり 標記ID に改名。
--   2. 親（書籍）を 書籍番号（VARCHAR）から 書籍ID（BIGINT・FK）へ。
--      書籍を消したら標記も消える（ON DELETE CASCADE）。
--   3. 監査を 2.1 の規約に統一。
--
-- 位置X/Y・幅・高さ・描画データについて:
--   2.0 は PDF のページ画像の上に重ねて表示するための座標と描画データを持っていた
--   （実データも全件保持している＝838 件すべてに値がある）。
--   2.1 の閲覧画面は本文 PDF を扱わない（ページごとの学習内容を並べる方式）ため
--   これらの列は**表示には使わない**が、2.0 のデータを捨てないために残してある
--   （将来 PDF 表示を作るときに使える）。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."RED_標記情報" (
    "標記ID"             BIGSERIAL    NOT NULL,
    "書籍ID"             BIGINT       NOT NULL,
    "ページ番号"         INTEGER      NOT NULL,
    -- highlight / underline / memo / vocabulary / pen
    "標記種別"           VARCHAR(20)  NOT NULL,
    -- ハイライト・下線の対象文字列、語彙の見出し語
    "対象文字"           TEXT         NULL,
    -- メモ本文・語彙の意味
    "標記内容"           TEXT         NULL,
    -- 表示色（#rrggbb）
    "色"                 VARCHAR(20)  NULL,
    "太さ"               INTEGER      NULL,
    -- 2.0 の PDF 重ね表示用（2.1 の表示では未使用。移行データを保持するため残す）
    "位置X"              NUMERIC(10, 6) NULL,
    "位置Y"              NUMERIC(10, 6) NULL,
    "幅"                 NUMERIC(10, 6) NULL,
    "高さ"               NUMERIC(10, 6) NULL,
    "描画データ"         TEXT         NULL,
    -- 同じページの中での並び順（1 から）
    "表示順"             INTEGER      NOT NULL DEFAULT 0,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "RED_標記情報_pkey" PRIMARY KEY ("標記ID"),
    CONSTRAINT "FK_RED_標記_書籍"
        FOREIGN KEY ("書籍ID")
        REFERENCES public."RED_書籍情報" ("書籍ID") ON DELETE CASCADE,
    CONSTRAINT "FK_RED_標記_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_RED_標記_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_RED_標記_ページ番号"
        CHECK ("ページ番号" >= 1),
    CONSTRAINT "CK_RED_標記_標記種別"
        CHECK ("標記種別" IN ('highlight', 'underline', 'memo', 'vocabulary', 'pen')),
    CONSTRAINT "CK_RED_標記_太さ"
        CHECK ("太さ" IS NULL OR "太さ" >= 1),
    CONSTRAINT "CK_RED_標記_位置X"
        CHECK ("位置X" IS NULL OR ("位置X" >= 0 AND "位置X" <= 1)),
    CONSTRAINT "CK_RED_標記_位置Y"
        CHECK ("位置Y" IS NULL OR ("位置Y" >= 0 AND "位置Y" <= 1)),
    CONSTRAINT "CK_RED_標記_幅"
        CHECK ("幅" IS NULL OR ("幅" >= 0 AND "幅" <= 1)),
    CONSTRAINT "CK_RED_標記_高さ"
        CHECK ("高さ" IS NULL OR ("高さ" >= 0 AND "高さ" <= 1))
);

-- 書籍 × ページの標記（閲覧画面はここを引く）
CREATE INDEX IF NOT EXISTS idx_red_mark_book_page
    ON public."RED_標記情報" ("書籍ID", "ページ番号", "表示順", "標記ID");

-- 種別での絞り込み（語彙ピックアップなど）
CREATE INDEX IF NOT EXISTS idx_red_mark_type
    ON public."RED_標記情報" ("書籍ID", "標記種別", "ページ番号");

COMMENT ON TABLE public."RED_標記情報" IS
    '読書中に付けた標記（ハイライト/下線/メモ/語彙）。2.0 の TRN_英語読書標記情報';
COMMENT ON COLUMN public."RED_標記情報"."標記種別" IS 'highlight / underline / memo / vocabulary / pen';
COMMENT ON COLUMN public."RED_標記情報"."対象文字" IS 'ハイライト・下線の対象文字列、語彙の見出し語';
COMMENT ON COLUMN public."RED_標記情報"."標記内容" IS 'メモ本文・語彙の意味';
COMMENT ON COLUMN public."RED_標記情報"."位置X" IS '2.0 の PDF 重ね表示用（2.1 の表示では未使用）';
COMMENT ON COLUMN public."RED_標記情報"."描画データ" IS '2.0 の手書きデータ（2.1 の表示では未使用）';
COMMENT ON COLUMN public."RED_標記情報"."登録元コード" IS 'APP=画面からの登録 / MIGRATION=2.0 からの移行';
