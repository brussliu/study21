-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_単語情報（日本語単語の母表）
-- ----------------------------------------------------------------------------
-- 2.0 の `STY_日本語単語母表`（実データ 9,847 件。study3 DB。移行日 2026-09-13 実測）を
-- 2.1 の規約で再設計する。見出し語と読みの組合せで 1 語を識別する。
--
-- 2.0 からの主な変更:
--   1. 主キー 日本語単語ID → 単語ID（BIGSERIAL）。2.0 の ID は 旧単語ID に残す
--      （冪等な移行と、2.0 との突き合わせのために必須）。
--   2. 監査列を 2.1 の規約（登録者/更新者アカウントID ＋ 登録元/更新元コード）に統一した。
--      2.0 の 登録ID/更新ID は全件 'MIGRATION_STUDY3'（人ではない）ため、
--      移行データの 登録者/更新者アカウントID は NULL、登録元コード='MIGRATION' にする。
--   3. 楽観的ロック用の バージョン を追加した（単語情報管理画面の修正で使う）。
--   4. 状態 → 状態コード。2.0 は 'ACTIVE' のみ（9,847 件）。
--
-- 2.0 の実データで分かっていること（移行の前提）:
--   * `代表JLPTレベル` は **全件 NULL**。2.0 は AI 詳細取得時に
--     `STY_日本語単語詳細情報.JLPTレベル` へ N1〜N5 を入れたが、母表の代表値は
--     一度も埋められていない。実際のレベルは 収録情報.レベル（='N1-N5'）側にある。
--   * `代表品詞` は `[名]` / `[名・他サ]` / `[他五]` のような **日本語の記号つき文字列**
--     （2.0 の教材由来。英語の品詞コードではない）。2.1 でもそのまま持つ。
--   * `見出し語キー` は 9,714 種類、`(見出し語キー, 読みキー)` は 9,847 種類で
--     **重複しない**（2.0 の UNIQUE 制約がそのまま成立している）。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_単語情報" (
    "単語ID"             BIGSERIAL    NOT NULL,
    -- 2.0 の 日本語単語ID。冪等な移行（ON CONFLICT）と突き合わせの根拠として残す
    "旧単語ID"           BIGINT       NULL,
    "見出し語"           VARCHAR(300) NOT NULL,
    "読み"               VARCHAR(300) NOT NULL,
    -- アプリ側で NFKC・空白統一を行った検索キー
    "見出し語キー"       VARCHAR(300) NOT NULL,
    -- アプリ側で NFKC・ひらがな統一を行った読み検索キー
    "読みキー"           VARCHAR(300) NOT NULL,
    -- 実データは全件 NULL。レベルの実体は JPN_単語収録情報.レベル にある
    "JLPTレベル"         VARCHAR(10)  NULL,
    "品詞"               VARCHAR(300) NULL,
    -- ACTIVE / INACTIVE
    "状態コード"         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    "備考"               TEXT         NULL,
    -- 楽観的ロック
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    -- APP=画面からの登録 / MIGRATION=2.0 からの移行
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_単語情報_pkey" PRIMARY KEY ("単語ID"),
    CONSTRAINT "FK_JPN_単語_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_単語_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_単語_JLPTレベル"
        CHECK ("JLPTレベル" IS NULL OR "JLPTレベル" IN ('N5', 'N4', 'N3', 'N2', 'N1')),
    CONSTRAINT "CK_JPN_単語_状態コード"
        CHECK ("状態コード" IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT "CK_JPN_単語_バージョン"
        CHECK ("バージョン" >= 1),
    CONSTRAINT "CK_JPN_単語_見出し語"
        CHECK (
            NULLIF(BTRIM("見出し語"), '') IS NOT NULL
            AND NULLIF(BTRIM("読み"), '') IS NOT NULL
            AND NULLIF(BTRIM("見出し語キー"), '') IS NOT NULL
            AND NULLIF(BTRIM("読みキー"), '') IS NOT NULL
        )
);

-- 2.0 の 日本語単語ID。移行の再実行を冪等にするための一意索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_jpn_word_old_id
    ON public."JPN_単語情報" ("旧単語ID");

-- 同じ見出し語でも読みが違えば別の語。2.0 の UNIQUE がそのまま成立する
-- （実データ 9,847 件で重複 0 件を確認済み）
CREATE UNIQUE INDEX IF NOT EXISTS uq_jpn_word_key
    ON public."JPN_単語情報" ("見出し語キー", "読みキー");

-- 読みからの検索（単語テストの読み問題 C1 など）
CREATE INDEX IF NOT EXISTS idx_jpn_word_reading
    ON public."JPN_単語情報" ("読みキー");

-- 単語情報管理画面の絞り込み（レベル・品詞・状態）
CREATE INDEX IF NOT EXISTS idx_jpn_word_filter
    ON public."JPN_単語情報" ("JLPTレベル", "品詞", "状態コード");

COMMENT ON TABLE public."JPN_単語情報" IS
    '日本語学習用の単語母表。2.0 の STY_日本語単語母表（9,847 件）';
COMMENT ON COLUMN public."JPN_単語情報"."旧単語ID" IS
    '2.0 の 日本語単語ID。移行の冪等性と突き合わせに使う';
COMMENT ON COLUMN public."JPN_単語情報"."見出し語キー" IS
    'アプリ側で NFKC・空白統一を行った検索キー';
COMMENT ON COLUMN public."JPN_単語情報"."読みキー" IS
    'アプリ側で NFKC・ひらがな統一を行った読み検索キー';
COMMENT ON COLUMN public."JPN_単語情報"."JLPTレベル" IS
    '代表 JLPT レベル。2.0 の実データは全件 NULL で、実際のレベルは 収録情報.レベル（=N1-N5）側にある';
COMMENT ON COLUMN public."JPN_単語情報"."品詞" IS
    '2.0 の 代表品詞 をそのまま引き継ぐ（[名] / [名・他サ] / [他五] のような日本語表記）';
COMMENT ON COLUMN public."JPN_単語情報"."登録元コード" IS
    'APP=画面からの登録 / MIGRATION=2.0 からの移行';
