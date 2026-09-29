-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_単語詳細_使用場面情報（使用場面・語感（usageNotes））
-- ----------------------------------------------------------------------------
-- 何: 語の詳細の 1 行。詳細の「版」（JPN_単語詳細情報.詳細ID）に属する。
-- 版の考え方（設計: 日本語勉強_再設計案_中文.md）:
--   ・1 行 = 1 件。表示順で並び、人の編集で増減する
--   ・版が変わると行は新しい版へ複製される（古い版の行はそのまま残る）
--   ・AI の取り直しで、人の行（手修正フラグ = true）は消えない（新版へ複製される）
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_単語詳細_使用場面情報" (
    "使用場面ID"     BIGSERIAL    NOT NULL,
    "詳細ID"       BIGINT       NOT NULL,
    "表示順"        INTEGER      NOT NULL,
    "語義番号"       SMALLINT     NULL,
    "文体区分"       VARCHAR(20)  NULL,
    "丁寧さ"        VARCHAR(20)  NULL,
    "対象"         VARCHAR(100) NULL,
    "備考"         TEXT         NULL,
    "手修正フラグ"     BOOLEAN      NOT NULL DEFAULT false,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"     VARCHAR(20)  NOT NULL DEFAULT 'BATCH',
    "更新元コード"     VARCHAR(20)  NULL,
    "登録日時"       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_単語詳細_使用場面情報_pkey" PRIMARY KEY ("使用場面ID"),
    CONSTRAINT "FK_JPN_単語詳細_使用場面_詳細"
        FOREIGN KEY ("詳細ID")
        REFERENCES public."JPN_単語詳細情報" ("詳細ID") ON DELETE CASCADE,
    CONSTRAINT "FK_JPN_単語詳細_使用場面_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_単語詳細_使用場面_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_単語詳細_使用場面_表示順" CHECK ("表示順" >= 1),
    CONSTRAINT "CK_JPN_単語詳細_使用場面_語義番号" CHECK ("語義番号" IS NULL OR "語義番号" >= 1)
);

-- 版ごとにまとめて読む（画面は 表示順 で並べる）
CREATE INDEX IF NOT EXISTS idx_jpn_detail_usage_note_version
    ON public."JPN_単語詳細_使用場面情報" ("詳細ID", "表示順", "使用場面ID");

COMMENT ON TABLE public."JPN_単語詳細_使用場面情報" IS
    '語の使用場面・語感（cautions とは別物。間違いの指摘ではない）。詳細の版に属する';
COMMENT ON COLUMN public."JPN_単語詳細_使用場面情報"."語義番号" IS
    '対応する語義番号。NULL は語全体';
COMMENT ON COLUMN public."JPN_単語詳細_使用場面情報"."文体区分" IS
    '話し言葉／書き言葉／どちらも';
COMMENT ON COLUMN public."JPN_単語詳細_使用場面情報"."丁寧さ" IS
    'カジュアル／普通／丁寧';
COMMENT ON COLUMN public."JPN_単語詳細_使用場面情報"."対象" IS
    '誰に使うか';
COMMENT ON COLUMN public."JPN_単語詳細_使用場面情報"."備考" IS
    '補足';
COMMENT ON COLUMN public."JPN_単語詳細_使用場面情報"."手修正フラグ" IS
    '人が画面で追加・修正した行（true）。AI の取り直しでも消さず、新しい版へ複製する';
COMMENT ON COLUMN public."JPN_単語詳細_使用場面情報"."登録元コード" IS
    'BATCH=AI が作った内容 / APP=人が作った内容（版へ複製した行は元の内容の出所を引き継ぐ）';
