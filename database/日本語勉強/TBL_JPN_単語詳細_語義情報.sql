-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_単語詳細_語義情報（語義（senses））
-- ----------------------------------------------------------------------------
-- 何: 語の詳細の 1 行。詳細の「版」（JPN_単語詳細情報.詳細ID）に属する。
-- 版の考え方（設計: 日本語勉強_再設計案_中文.md）:
--   ・1 行 = 1 件。表示順で並び、人の編集で増減する
--   ・版が変わると行は新しい版へ複製される（古い版の行はそのまま残る）
--   ・AI の取り直しで、人の行（手修正フラグ = true）は消えない（新版へ複製される）
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_単語詳細_語義情報" (
    "語義ID"       BIGSERIAL   NOT NULL,
    "詳細ID"       BIGINT      NOT NULL,
    "表示順"        INTEGER     NOT NULL,
    "語義番号"       SMALLINT    NOT NULL,
    "日本語"        TEXT        NOT NULL,
    "中国語"        TEXT        NULL,
    "場面"         TEXT        NULL,
    "文体"         TEXT        NULL,
    "補足_日本語"     TEXT        NULL,
    "補足_中国語"     TEXT        NULL,
    "手修正フラグ"     BOOLEAN     NOT NULL DEFAULT false,
    "登録者アカウントID" BIGINT      NULL,
    "更新者アカウントID" BIGINT      NULL,
    "登録元コード"     VARCHAR(20) NOT NULL DEFAULT 'BATCH',
    "更新元コード"     VARCHAR(20) NULL,
    "登録日時"       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_単語詳細_語義情報_pkey" PRIMARY KEY ("語義ID"),
    CONSTRAINT "FK_JPN_単語詳細_語義_詳細"
        FOREIGN KEY ("詳細ID")
        REFERENCES public."JPN_単語詳細情報" ("詳細ID") ON DELETE CASCADE,
    CONSTRAINT "FK_JPN_単語詳細_語義_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_単語詳細_語義_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_単語詳細_語義_表示順" CHECK ("表示順" >= 1),
    CONSTRAINT "CK_JPN_単語詳細_語義_語義番号" CHECK ("語義番号" >= 1),
    CONSTRAINT "CK_JPN_単語詳細_語義_日本語" CHECK (NULLIF(BTRIM("日本語"), '') IS NOT NULL)
);

-- 版ごとにまとめて読む（画面は 表示順 で並べる）
CREATE INDEX IF NOT EXISTS idx_jpn_detail_sense_version
    ON public."JPN_単語詳細_語義情報" ("詳細ID", "表示順", "語義ID");

COMMENT ON TABLE public."JPN_単語詳細_語義情報" IS
    '語の語義（意味のまとまり）。詳細の版に属する（2.0 の STY_日本語単語詳細_語義情報）';
COMMENT ON COLUMN public."JPN_単語詳細_語義情報"."語義番号" IS
    '語義の番号（1 起）。例文・使用場面が 語義番号 で参照する';
COMMENT ON COLUMN public."JPN_単語詳細_語義情報"."日本語" IS
    '語義の説明（日本語）';
COMMENT ON COLUMN public."JPN_単語詳細_語義情報"."中国語" IS
    '語義の中国語';
COMMENT ON COLUMN public."JPN_単語詳細_語義情報"."場面" IS
    'どんな場面で使うか';
COMMENT ON COLUMN public."JPN_単語詳細_語義情報"."文体" IS
    '普通／やや硬い／話し言葉 など';
COMMENT ON COLUMN public."JPN_単語詳細_語義情報"."補足_日本語" IS
    '補足（日本語）';
COMMENT ON COLUMN public."JPN_単語詳細_語義情報"."補足_中国語" IS
    '補足（中国語）';
COMMENT ON COLUMN public."JPN_単語詳細_語義情報"."手修正フラグ" IS
    '人が画面で追加・修正した行（true）。AI の取り直しでも消さず、新しい版へ複製する';
COMMENT ON COLUMN public."JPN_単語詳細_語義情報"."登録元コード" IS
    'BATCH=AI が作った内容 / APP=人が作った内容（版へ複製した行は元の内容の出所を引き継ぐ）';
