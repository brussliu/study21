-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_単語詳細_注意情報（間違えやすいポイント（cautions））
-- ----------------------------------------------------------------------------
-- 何: 語の詳細の 1 行。詳細の「版」（JPN_単語詳細情報.詳細ID）に属する。
-- 版の考え方（設計: 日本語勉強_再設計案_中文.md）:
--   ・1 行 = 1 件。表示順で並び、人の編集で増減する
--   ・版が変わると行は新しい版へ複製される（古い版の行はそのまま残る）
--   ・AI の取り直しで、人の行（手修正フラグ = true）は消えない（新版へ複製される）
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_単語詳細_注意情報" (
    "注意ID"       BIGSERIAL    NOT NULL,
    "詳細ID"       BIGINT       NOT NULL,
    "表示順"        INTEGER      NOT NULL,
    "区分"         VARCHAR(20)  NULL,
    "見出し"        VARCHAR(300) NULL,
    "誤り"         TEXT         NULL,
    "正しい"        TEXT         NULL,
    "理由"         TEXT         NULL,
    "手修正フラグ"     BOOLEAN      NOT NULL DEFAULT false,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"     VARCHAR(20)  NOT NULL DEFAULT 'BATCH',
    "更新元コード"     VARCHAR(20)  NULL,
    "登録日時"       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_単語詳細_注意情報_pkey" PRIMARY KEY ("注意ID"),
    CONSTRAINT "FK_JPN_単語詳細_注意_詳細"
        FOREIGN KEY ("詳細ID")
        REFERENCES public."JPN_単語詳細情報" ("詳細ID") ON DELETE CASCADE,
    CONSTRAINT "FK_JPN_単語詳細_注意_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_単語詳細_注意_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_単語詳細_注意_表示順" CHECK ("表示順" >= 1),
    CONSTRAINT "CK_JPN_単語詳細_注意_区分" CHECK ("区分" IS NULL OR "区分" IN ('GRAMMAR', 'UNNATURAL', 'MEANING', 'PARTICLE'))
);

-- 版ごとにまとめて読む（画面は 表示順 で並べる）
CREATE INDEX IF NOT EXISTS idx_jpn_detail_caution_version
    ON public."JPN_単語詳細_注意情報" ("詳細ID", "表示順", "注意ID");

COMMENT ON TABLE public."JPN_単語詳細_注意情報" IS
    '中国語母語の学習者が間違えるポイント（誤りと正しい形の対比）。詳細の版に属する';
COMMENT ON COLUMN public."JPN_単語詳細_注意情報"."区分" IS
    'GRAMMAR=文法 / UNNATURAL=不自然 / MEANING=意味の取り違え / PARTICLE=助詞';
COMMENT ON COLUMN public."JPN_単語詳細_注意情報"."見出し" IS
    '一言の見出し';
COMMENT ON COLUMN public."JPN_単語詳細_注意情報"."誤り" IS
    '間違いの例';
COMMENT ON COLUMN public."JPN_単語詳細_注意情報"."正しい" IS
    '正しい言い方';
COMMENT ON COLUMN public."JPN_単語詳細_注意情報"."理由" IS
    'なぜ間違いか';
COMMENT ON COLUMN public."JPN_単語詳細_注意情報"."手修正フラグ" IS
    '人が画面で追加・修正した行（true）。AI の取り直しでも消さず、新しい版へ複製する';
COMMENT ON COLUMN public."JPN_単語詳細_注意情報"."登録元コード" IS
    'BATCH=AI が作った内容 / APP=人が作った内容（版へ複製した行は元の内容の出所を引き継ぐ）';
