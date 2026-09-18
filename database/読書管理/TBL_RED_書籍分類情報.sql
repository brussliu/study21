-- ============================================================================
-- Study 2.1  読書管理 DDL
-- テーブル: RED_書籍分類情報（本棚の分類＝マスタ）
-- ----------------------------------------------------------------------------
-- 書籍管理画面（保護者が使う）で本を分類し、本棚から素早く探せるようにするための
-- マスタ。2.0 には分類が無く、タグ（'Novel' / '中国語'）と難易度でしか区別できな
-- かったため、2.1 で明示的な分類を持たせる。
--
-- 設計のポイント:
--   1. 分類は**マスタ＋表示順**。本に付ける値は 1 つだけ（`RED_書籍情報.分類ID`）。
--      タグ（複数・自由入力）は検索の補助として残し、分類は「棚の区切り」に使う。
--   2. 分類を消しても本は消さない（`RED_書籍情報.分類ID` は ON DELETE SET NULL →
--      本は「未分類」に戻る）。書棚の整理中に本を失わないため。
--   3. 件数が多くないので階層は持たない（親分類ID は作らない）。順番は 表示順 で
--      並べ替え、同じ値は 分類ID で安定させる。
--   4. 論理削除は持たない。使わなくなった分類は削除（本は未分類へ）か改名でよい。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."RED_書籍分類情報" (
    "分類ID"             BIGSERIAL    NOT NULL,
    -- 本棚に出す名前（例 '英語 小説' / '中国語'）。前後の空白はアプリで落とす
    "分類名"             VARCHAR(50)  NOT NULL,
    -- 本棚での並び順（小さいほど先頭）。0 も許す
    "表示順"             INTEGER      NOT NULL DEFAULT 0,
    -- 補足（本棚の見出しに出す説明。未設定は NULL）
    "説明"               TEXT         NULL,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    -- APP=画面からの登録 / MIGRATION=2.0 からの移行
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "RED_書籍分類情報_pkey" PRIMARY KEY ("分類ID"),
    -- 同じ名前の分類を 2 つ作らない（本棚の見出しが重複すると探しにくい）
    CONSTRAINT "UK_RED_書籍分類_分類名" UNIQUE ("分類名"),
    CONSTRAINT "FK_RED_書籍分類_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_RED_書籍分類_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_RED_書籍分類_分類名"
        CHECK (NULLIF(BTRIM("分類名"), '') IS NOT NULL),
    CONSTRAINT "CK_RED_書籍分類_表示順"
        CHECK ("表示順" >= 0)
);

-- 本棚の並び（表示順 → 分類ID）
CREATE INDEX IF NOT EXISTS idx_red_category_order
    ON public."RED_書籍分類情報" ("表示順", "分類ID");

COMMENT ON TABLE public."RED_書籍分類情報" IS
    '本棚の分類マスタ。書籍管理画面で作成・改名・並べ替え・削除する（本は未分類に戻る）';
COMMENT ON COLUMN public."RED_書籍分類情報"."分類名" IS
    '本棚に出す名前（例 英語 小説 / 英語 リーディング / 中国語）。一意';
COMMENT ON COLUMN public."RED_書籍分類情報"."表示順" IS
    '本棚での並び順（小さいほど先頭）。同じ値は 分類ID 順';
COMMENT ON COLUMN public."RED_書籍分類情報"."登録元コード" IS
    'APP=画面からの登録 / MIGRATION=2.0 からの移行';

-- ============================================================================
-- 追補（2026-09-14）: 分類（棚）を家庭ごとに分ける。
-- ----------------------------------------------------------------------------
-- 2026-09-14 の決定:
--   * 第 1 版は **分類は家庭ごと**。`所有家族学生ID` が NULL の分類は
--     「全体の分類」で、管理者が作り全家庭に見える（既存 2 件はこちら）。
--     保護者が作った分類はその家庭だけに見える。
--   * `分類ID`（どの分類に入っているか）は第 1 版では **書籍側のまま**。
--     全体書籍は管理者が、家庭の書籍は保護者が分類を付ける。
--     「全体書籍を自分の棚に好きな名前で置きたい」要求が出たら、第 2 版で
--     `分類ID` を `RED_自分の本棚情報` 側へ移す。
--
-- 一意制約の付け替え（重要）:
--   いまは `UK_RED_書籍分類_分類名`（分類名 だけで一意）なので、家庭ごとに
--   同じ名前の分類を作れない。`COALESCE("所有家族学生ID", 0)` を付けた
--   一意索引に付け替える（全体の分類は 0 に寄せる）。
--   この付け替えで `ON CONFLICT ("分類名")` を使う移行 SQL は使えなくなるため、
--   `database/移行/MIG_RED_読書_分類_20260913.sql` は `WHERE NOT EXISTS` の形へ直した。
--   すでにテーブルを作ってある環境でも、このファイルをもう一度実行すれば当たる。
-- ============================================================================

ALTER TABLE public."RED_書籍分類情報"
    ADD COLUMN IF NOT EXISTS "所有家族学生ID" BIGINT NULL;

ALTER TABLE public."RED_書籍分類情報" DROP CONSTRAINT IF EXISTS "FK_RED_書籍分類_所有家族";
ALTER TABLE public."RED_書籍分類情報"
    ADD CONSTRAINT "FK_RED_書籍分類_所有家族"
        FOREIGN KEY ("所有家族学生ID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT;

-- 分類名は「家庭の中で一意」（全体の分類は 0 に寄せて 1 つのグループにする）
ALTER TABLE public."RED_書籍分類情報" DROP CONSTRAINT IF EXISTS "UK_RED_書籍分類_分類名";
CREATE UNIQUE INDEX IF NOT EXISTS uq_red_category_name_scope
    ON public."RED_書籍分類情報" (COALESCE("所有家族学生ID", 0), "分類名");

COMMENT ON COLUMN public."RED_書籍分類情報"."所有家族学生ID" IS
    '分類の持ち主の家庭（生徒のアカウントID）。NULL は全体の分類（管理者が作り全家庭に見える）';
COMMENT ON COLUMN public."RED_書籍分類情報"."分類名" IS
    '本棚に出す名前（例 英語 小説 / 英語 リーディング / 中国語）。家庭ごとに一意';
