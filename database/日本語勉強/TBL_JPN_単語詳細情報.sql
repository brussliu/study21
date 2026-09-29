-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_単語詳細情報（語の詳細の「版」＝バージョン）
-- ----------------------------------------------------------------------------
-- 何: 1 行 = その語の詳細の 1 版。語レベルの内容（意味・説明・品詞・レベルなど）と
--     小さい構造（発音・活用形・自他対応）を持ち、行として増減する段落
--     （語義・例文・文型・会話・類義語・注意・コロケーション・関連語・使用場面・練習）は
--     子テーブル（`JPN_単語詳細_*`）が持つ。
--
-- 版の考え方（設計: 日本語勉強_再設計案_中文.md）:
--   ・**すべての取得・編集が 1 版を作る**。内容は「そのときの有効版を複製し、変わった部分だけ入れ替える」
--   ・新しい版は自動で ACTIVE になり、前の版は ARCHIVED になる
--     （部分 UNIQUE 索引で「1 語につき ACTIVE は 1 版」を DB が保証する）
--   ・有効版の指定＝ ACTIVE を移すだけ。未指定なら最後に作った版（＝最新）が有効
--   ・`元詳細ID` で版のつながり、`生成ID` でどの AI 生成から生まれた版かを辿れる
--   ・人の編集も 1 版作る（手修正フラグ = true）ので、誤編集は前に戻せる
--
-- 2.0 との違い:
--   ・2.0 は「本体の列 ＋ 6 子テーブル」で 1 語 1 版だった（履歴は残らない）
--   ・`旧詳細ID` は引き継がない（2.0 からの詳細データは削除済みで、形も根本的に変わるため）
--   ・`詳細JSON`（全部入りの JSONB）はやめ、段落ごとの表と列に分けた
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_単語詳細情報" (
    "詳細ID"           BIGSERIAL    NOT NULL,
    "単語ID"           BIGINT       NOT NULL,
    -- その語の中での版番号（1 起）。履歴の並びと表示に使う
    "内容版数"         INTEGER      NOT NULL DEFAULT 1,
    -- ACTIVE=有効版（1 語に 1 行） / ARCHIVED=履歴
    "状態コード"       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    -- この版が基にした版（最初の版は NULL）
    "元詳細ID"         BIGINT       NULL,
    -- この版を作った AI 生成（JPN_AI生成履歴情報.生成ID）。人の編集版は NULL
    "生成ID"           BIGINT       NULL,
    "AIプロバイダ"     VARCHAR(40)  NULL,
    "AIモデル"         VARCHAR(120) NULL,
    "取得日時"         TIMESTAMP    NULL,
    -- この版に人の編集が含まれるか
    "手修正フラグ"     BOOLEAN      NOT NULL DEFAULT false,
    "備考"             TEXT         NULL,

    -- 語レベルの内容（子テーブルにしないもの）
    "核心意味"         TEXT         NULL,
    "説明_日本語"      TEXT         NULL,
    "説明_中国語"      TEXT         NULL,
    "品詞"             VARCHAR(100) NULL,
    "JLPTレベル"       VARCHAR(10)  NULL,
    "活用型"           VARCHAR(100) NULL,
    -- TRANSITIVE / INTRANSITIVE / BOTH / NONE
    "自他"             VARCHAR(20)  NULL,
    "重要度"           SMALLINT     NULL,
    "記憶ヒント"       TEXT         NULL,
    "記憶ヒント根拠"   TEXT         NULL,
    -- 発音（単数。読み・アクセント型・アクセント表記・ヒント・音声サンプルの有無）
    "発音JSON"         JSONB        NOT NULL DEFAULT '{}',
    -- 活用形の表（配列）
    "活用形JSON"       JSONB        NOT NULL DEFAULT '[]',
    -- 自他動詞の対応（オブジェクト。無い語は NULL）
    "自他対応JSON"     JSONB        NULL,
    -- AI の生の応答（人の編集版は元の版の値を引き継ぐ）
    "元レスポンスJSON" JSONB        NOT NULL DEFAULT '{}',
    -- 楽観的ロック（画面の編集・有効版の切り替えが使う）
    "バージョン"       INTEGER      NOT NULL DEFAULT 1,

    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"     VARCHAR(20)  NOT NULL DEFAULT 'BATCH',
    "更新元コード"     VARCHAR(20)  NULL,
    "登録日時"         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_単語詳細情報_pkey" PRIMARY KEY ("詳細ID"),
    CONSTRAINT "FK_JPN_単語詳細_単語"
        FOREIGN KEY ("単語ID")
        REFERENCES public."JPN_単語情報" ("単語ID") ON DELETE CASCADE,
    CONSTRAINT "FK_JPN_単語詳細_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_単語詳細_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    -- 同じ語に同じ版番号は 2 つ無い
    CONSTRAINT "uq_jpn_detail_version" UNIQUE ("単語ID", "内容版数"),
    CONSTRAINT "CK_JPN_単語詳細_状態コード"
        CHECK ("状態コード" IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT "CK_JPN_単語詳細_内容版数" CHECK ("内容版数" >= 1),
    CONSTRAINT "CK_JPN_単語詳細_バージョン" CHECK ("バージョン" >= 1),
    CONSTRAINT "CK_JPN_単語詳細_重要度"
        CHECK ("重要度" IS NULL OR ("重要度" BETWEEN 1 AND 5)),
    CONSTRAINT "CK_JPN_単語詳細_JLPTレベル"
        CHECK ("JLPTレベル" IS NULL OR "JLPTレベル" IN ('N5', 'N4', 'N3', 'N2', 'N1')),
    CONSTRAINT "CK_JPN_単語詳細_自他"
        CHECK ("自他" IS NULL OR "自他" IN ('TRANSITIVE', 'INTRANSITIVE', 'BOTH', 'NONE')),
    CONSTRAINT "CK_JPN_単語詳細_発音JSON" CHECK (jsonb_typeof("発音JSON") = 'object'),
    CONSTRAINT "CK_JPN_単語詳細_活用形JSON" CHECK (jsonb_typeof("活用形JSON") = 'array'),
    CONSTRAINT "CK_JPN_単語詳細_自他対応JSON"
        CHECK ("自他対応JSON" IS NULL OR jsonb_typeof("自他対応JSON") = 'object'),
    CONSTRAINT "CK_JPN_単語詳細_元レスポンスJSON"
        CHECK (jsonb_typeof("元レスポンスJSON") = 'object')
);

-- 1 語につき有効版は 1 つだけ（版の切り替えはこの索引が守る）
CREATE UNIQUE INDEX IF NOT EXISTS uq_jpn_detail_active
    ON public."JPN_単語詳細情報" ("単語ID")
    WHERE "状態コード" = 'ACTIVE';

-- 有効版を引く（一覧・詳細・AI の入力）
CREATE INDEX IF NOT EXISTS idx_jpn_detail_word_state
    ON public."JPN_単語詳細情報" ("単語ID", "状態コード");

COMMENT ON TABLE public."JPN_単語詳細情報" IS
    '語の詳細の 1 版。有効版（状態コード=ACTIVE）が画面と AI に使われる。すべての取得・編集が新しい版を作る';
COMMENT ON COLUMN public."JPN_単語詳細情報"."内容版数" IS
    'その語の中での版番号（1 起）。履歴の並びと表示に使う';
COMMENT ON COLUMN public."JPN_単語詳細情報"."状態コード" IS
    'ACTIVE=有効版（1 語に 1 行。部分 UNIQUE 索引で保証） / ARCHIVED=履歴';
COMMENT ON COLUMN public."JPN_単語詳細情報"."元詳細ID" IS
    'この版が基にした版。最初の版は NULL。FK は付けない（自表参照。履歴を消しても壊さない）';
COMMENT ON COLUMN public."JPN_単語詳細情報"."生成ID" IS
    'この版を作った JPN_AI生成履歴情報.生成ID。人の編集版は NULL。FK は付けない（生成履歴は保持期間で消えうる）';
COMMENT ON COLUMN public."JPN_単語詳細情報"."手修正フラグ" IS
    'この版に人の編集が含まれる（true）。AI の取り直しでは人の行を複製して残す';
COMMENT ON COLUMN public."JPN_単語詳細情報"."発音JSON" IS
    '発音（単数）: reading / accentType / accentNotation / hint / hasAudioSample';
COMMENT ON COLUMN public."JPN_単語詳細情報"."活用形JSON" IS
    '活用形の配列: [{form, value, example}]';
COMMENT ON COLUMN public."JPN_単語詳細情報"."自他対応JSON" IS
    '自他動詞の対応: {intransitive, transitive, particleNote, intransitiveExample, transitiveExample}。無い語は NULL';
COMMENT ON COLUMN public."JPN_単語詳細情報"."元レスポンスJSON" IS
    'AI の生の応答（人が編集した版は元の版の値を引き継ぐ）。組み立て後の内容と突き合わせるために残す';
COMMENT ON COLUMN public."JPN_単語詳細情報"."登録元コード" IS
    'BATCH=AI が作った版 / APP=人の編集で作った版';
