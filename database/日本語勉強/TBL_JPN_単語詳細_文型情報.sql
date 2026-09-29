-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_単語詳細_文型情報（文型・パターン（patterns））
-- ----------------------------------------------------------------------------
-- 何: 語の詳細の 1 行。詳細の「版」（JPN_単語詳細情報.詳細ID）に属する。
-- 版の考え方（設計: 日本語勉強_再設計案_中文.md）:
--   ・1 行 = 1 件。表示順で並び、人の編集で増減する
--   ・版が変わると行は新しい版へ複製される（古い版の行はそのまま残る）
--   ・AI の取り直しで、人の行（手修正フラグ = true）は消えない（新版へ複製される）
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_単語詳細_文型情報" (
    "文型ID"       BIGSERIAL    NOT NULL,
    "詳細ID"       BIGINT       NOT NULL,
    "表示順"        INTEGER      NOT NULL,
    "文型"         VARCHAR(300) NOT NULL,
    "文型読み"       VARCHAR(300) NULL,
    "説明_中国語"     TEXT         NULL,
    "例文_日本語"     TEXT         NULL,
    "例文_中国語"     TEXT         NULL,
    "手修正フラグ"     BOOLEAN      NOT NULL DEFAULT false,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"     VARCHAR(20)  NOT NULL DEFAULT 'BATCH',
    "更新元コード"     VARCHAR(20)  NULL,
    "登録日時"       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_単語詳細_文型情報_pkey" PRIMARY KEY ("文型ID"),
    CONSTRAINT "FK_JPN_単語詳細_文型_詳細"
        FOREIGN KEY ("詳細ID")
        REFERENCES public."JPN_単語詳細情報" ("詳細ID") ON DELETE CASCADE,
    CONSTRAINT "FK_JPN_単語詳細_文型_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_単語詳細_文型_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_単語詳細_文型_表示順" CHECK ("表示順" >= 1),
    CONSTRAINT "CK_JPN_単語詳細_文型_文型" CHECK (NULLIF(BTRIM("文型"), '') IS NOT NULL)
);

-- 版ごとにまとめて読む（画面は 表示順 で並べる）
CREATE INDEX IF NOT EXISTS idx_jpn_detail_pattern_version
    ON public."JPN_単語詳細_文型情報" ("詳細ID", "表示順", "文型ID");

COMMENT ON TABLE public."JPN_単語詳細_文型情報" IS
    '語の使い方の型（助詞・活用の型）。詳細の版に属する';
COMMENT ON COLUMN public."JPN_単語詳細_文型情報"."文型" IS
    '型（例: 人に相談する）';
COMMENT ON COLUMN public."JPN_単語詳細_文型情報"."文型読み" IS
    '型の読み';
COMMENT ON COLUMN public."JPN_単語詳細_文型情報"."説明_中国語" IS
    '型の説明（中国語）';
COMMENT ON COLUMN public."JPN_単語詳細_文型情報"."例文_日本語" IS
    'この型を使った短い例';
COMMENT ON COLUMN public."JPN_単語詳細_文型情報"."例文_中国語" IS
    'その例の中国語訳';
COMMENT ON COLUMN public."JPN_単語詳細_文型情報"."手修正フラグ" IS
    '人が画面で追加・修正した行（true）。AI の取り直しでも消さず、新しい版へ複製する';
COMMENT ON COLUMN public."JPN_単語詳細_文型情報"."登録元コード" IS
    'BATCH=AI が作った内容 / APP=人が作った内容（版へ複製した行は元の内容の出所を引き継ぐ）';
