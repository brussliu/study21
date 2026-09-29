-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_単語詳細_関連語情報（関連語（relatedWords））
-- ----------------------------------------------------------------------------
-- 何: 語の詳細の 1 行。詳細の「版」（JPN_単語詳細情報.詳細ID）に属する。
-- 版の考え方（設計: 日本語勉強_再設計案_中文.md）:
--   ・1 行 = 1 件。表示順で並び、人の編集で増減する
--   ・版が変わると行は新しい版へ複製される（古い版の行はそのまま残る）
--   ・AI の取り直しで、人の行（手修正フラグ = true）は消えない（新版へ複製される）
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_単語詳細_関連語情報" (
    "関連語ID"      BIGSERIAL    NOT NULL,
    "詳細ID"       BIGINT       NOT NULL,
    "表示順"        INTEGER      NOT NULL,
    "関係"         VARCHAR(20)  NULL,
    "見出し語"       VARCHAR(300) NOT NULL,
    "読み"         VARCHAR(300) NULL,
    "中国語"        TEXT         NULL,
    "手修正フラグ"     BOOLEAN      NOT NULL DEFAULT false,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"     VARCHAR(20)  NOT NULL DEFAULT 'BATCH',
    "更新元コード"     VARCHAR(20)  NULL,
    "登録日時"       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_単語詳細_関連語情報_pkey" PRIMARY KEY ("関連語ID"),
    CONSTRAINT "FK_JPN_単語詳細_関連語_詳細"
        FOREIGN KEY ("詳細ID")
        REFERENCES public."JPN_単語詳細情報" ("詳細ID") ON DELETE CASCADE,
    CONSTRAINT "FK_JPN_単語詳細_関連語_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_単語詳細_関連語_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_単語詳細_関連語_表示順" CHECK ("表示順" >= 1),
    CONSTRAINT "CK_JPN_単語詳細_関連語_関係" CHECK ("関係" IS NULL OR "関係" IN ('類義語', '対義語', '間違えやすい', '同じ読み')),
    CONSTRAINT "CK_JPN_単語詳細_関連語_見出し語" CHECK (NULLIF(BTRIM("見出し語"), '') IS NOT NULL)
);

-- 版ごとにまとめて読む（画面は 表示順 で並べる）
CREATE INDEX IF NOT EXISTS idx_jpn_detail_related_version
    ON public."JPN_単語詳細_関連語情報" ("詳細ID", "表示順", "関連語ID");

COMMENT ON TABLE public."JPN_単語詳細_関連語情報" IS
    '類義語・対義語・間違えやすい語・同じ読みの語。詳細の版に属する';
COMMENT ON COLUMN public."JPN_単語詳細_関連語情報"."関係" IS
    '類義語 / 対義語 / 間違えやすい / 同じ読み';
COMMENT ON COLUMN public."JPN_単語詳細_関連語情報"."見出し語" IS
    '関連する語';
COMMENT ON COLUMN public."JPN_単語詳細_関連語情報"."読み" IS
    '関連する語の読み';
COMMENT ON COLUMN public."JPN_単語詳細_関連語情報"."中国語" IS
    '関連する語の中国語';
COMMENT ON COLUMN public."JPN_単語詳細_関連語情報"."手修正フラグ" IS
    '人が画面で追加・修正した行（true）。AI の取り直しでも消さず、新しい版へ複製する';
COMMENT ON COLUMN public."JPN_単語詳細_関連語情報"."登録元コード" IS
    'BATCH=AI が作った内容 / APP=人が作った内容（版へ複製した行は元の内容の出所を引き継ぐ）';
