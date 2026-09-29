-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_単語詳細_練習情報（ミニ練習（practices））
-- ----------------------------------------------------------------------------
-- 何: 語の詳細の 1 行。詳細の「版」（JPN_単語詳細情報.詳細ID）に属する。
-- 版の考え方（設計: 日本語勉強_再設計案_中文.md）:
--   ・1 行 = 1 件。表示順で並び、人の編集で増減する
--   ・版が変わると行は新しい版へ複製される（古い版の行はそのまま残る）
--   ・AI の取り直しで、人の行（手修正フラグ = true）は消えない（新版へ複製される）
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_単語詳細_練習情報" (
    "練習ID"       BIGSERIAL   NOT NULL,
    "詳細ID"       BIGINT      NOT NULL,
    "表示順"        INTEGER     NOT NULL,
    "種別"         VARCHAR(20) NULL,
    "問題_日本語"     TEXT        NOT NULL,
    "問題_中国語"     TEXT        NULL,
    "選択肢JSON"    JSONB       NOT NULL DEFAULT '[]',
    "自由記述フラグ"    BOOLEAN     NOT NULL DEFAULT false,
    "正解"         TEXT        NULL,
    "解説"         TEXT        NULL,
    "手修正フラグ"     BOOLEAN     NOT NULL DEFAULT false,
    "登録者アカウントID" BIGINT      NULL,
    "更新者アカウントID" BIGINT      NULL,
    "登録元コード"     VARCHAR(20) NOT NULL DEFAULT 'BATCH',
    "更新元コード"     VARCHAR(20) NULL,
    "登録日時"       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_単語詳細_練習情報_pkey" PRIMARY KEY ("練習ID"),
    CONSTRAINT "FK_JPN_単語詳細_練習_詳細"
        FOREIGN KEY ("詳細ID")
        REFERENCES public."JPN_単語詳細情報" ("詳細ID") ON DELETE CASCADE,
    CONSTRAINT "FK_JPN_単語詳細_練習_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_単語詳細_練習_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_単語詳細_練習_表示順" CHECK ("表示順" >= 1),
    CONSTRAINT "CK_JPN_単語詳細_練習_種別" CHECK ("種別" IS NULL OR "種別" IN ('PARTICLE', 'SYNONYM', 'SCENE', 'WRITING')),
    CONSTRAINT "CK_JPN_単語詳細_練習_問題" CHECK (NULLIF(BTRIM("問題_日本語"), '') IS NOT NULL),
    CONSTRAINT "CK_JPN_単語詳細_練習_選択肢JSON" CHECK (jsonb_typeof("選択肢JSON") = 'array')
);

-- 版ごとにまとめて読む（画面は 表示順 で並べる）
CREATE INDEX IF NOT EXISTS idx_jpn_detail_practice_version
    ON public."JPN_単語詳細_練習情報" ("詳細ID", "表示順", "練習ID");

COMMENT ON TABLE public."JPN_単語詳細_練習情報" IS
    '学習画面のミニ練習。詳細の版に属する';
COMMENT ON COLUMN public."JPN_単語詳細_練習情報"."種別" IS
    'PARTICLE=助詞 / SYNONYM=類義語の使い分け / SCENE=場面に合う言い方 / WRITING=自由造句';
COMMENT ON COLUMN public."JPN_単語詳細_練習情報"."問題_日本語" IS
    '問題文（日本語）';
COMMENT ON COLUMN public."JPN_単語詳細_練習情報"."問題_中国語" IS
    '問題文（中国語）';
COMMENT ON COLUMN public."JPN_単語詳細_練習情報"."選択肢JSON" IS
    '選択式の選択肢（文字列の配列）。自由記述のときは空配列';
COMMENT ON COLUMN public."JPN_単語詳細_練習情報"."自由記述フラグ" IS
    'true=自由記述（WRITING）';
COMMENT ON COLUMN public."JPN_単語詳細_練習情報"."正解" IS
    '正解（選択式は選択肢の値、自由記述は模範例文）';
COMMENT ON COLUMN public."JPN_単語詳細_練習情報"."解説" IS
    '解説';
COMMENT ON COLUMN public."JPN_単語詳細_練習情報"."手修正フラグ" IS
    '人が画面で追加・修正した行（true）。AI の取り直しでも消さず、新しい版へ複製する';
COMMENT ON COLUMN public."JPN_単語詳細_練習情報"."登録元コード" IS
    'BATCH=AI が作った内容 / APP=人が作った内容（版へ複製した行は元の内容の出所を引き継ぐ）';
