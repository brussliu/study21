-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_単語問題情報（C/D/E で使う AI 生成問題）
-- ----------------------------------------------------------------------------
-- 2.0 の `STY_日本語単語問題情報`（実データ 1,700 件。study3 DB。移行日 2026-09-13 実測）を
-- 2.1 の規約で再設計する。選択肢は子テーブル `JPN_単語問題選択肢情報`（6,800 件）。
--
-- 2.0 の実データで分かっていること（移行の前提）:
--   * `問題種別` の内訳: C1_READING 381 / C2_KANJI 381 / D_CONTEXT_MEANING 383 /
--     E_KANJI_USAGE 555。問題種別ごとに 1 語 1 問（問題番号は全件 1）。
--   * `状態` は ACTIVE 1,525 / ARCHIVED 175（ARCHIVED は E_KANJI_USAGE のみ＝作り直された旧問）。
--     よって 状態コード の CHECK には 2.0 のコードをそのまま用意する。
--   * `難易度` は NORMAL 1,462 / EASY 238。
--   * `正解値` / `問題文_日本語` / `対象表記` / `対象読み` / `構造化JSON` は全件入っている。
--     `解説_中国語` は D・E だけ、`音声テキスト` は 1,319 件に入っている。
--   * `日本語単語詳細ID` / `詳細内容版数` / `反映結果ID` / `AIプロバイダ` / `AIモデル` /
--     `プロンプト版` は 2.1 では持たない（AI 取得の実行管理はバッチ側の責務で、
--     問題そのものの表示・採点には使わない）。`構造化JSON` はそのまま残す。
--
-- 2.0 からの主な変更:
--   1. 主キー 問題ID は BIGSERIAL のまま。2.0 の ID は 旧問題ID に残す。
--   2. 親（単語）への参照を 単語ID（BIGINT FK）にした。
--   3. 状態 → 状態コード、監査列を 2.1 の規約へ統一。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_単語問題情報" (
    "問題ID"             BIGSERIAL    NOT NULL,
    -- 2.0 の 問題ID。冪等な移行（ON CONFLICT）と突き合わせの根拠として残す
    "旧問題ID"           BIGINT       NULL,
    "単語ID"             BIGINT       NOT NULL,
    -- C1_READING / C2_KANJI / D_CONTEXT_MEANING / E_KANJI_USAGE
    -- （CHECK は付けない。問題種別は今後増えうるし、追加のたびに ALTER が要るため）
    "問題種別"           VARCHAR(30)  NOT NULL,
    -- 同じ種別で複数問を作る場合の番号。2.0 の実データは全件 1
    "問題番号"           SMALLINT     NOT NULL DEFAULT 1,
    "問題文_日本語"      TEXT         NULL,
    "問題文_中国語"      TEXT         NULL,
    "対象表記"           VARCHAR(300) NULL,
    "対象読み"           VARCHAR(300) NULL,
    "例文_日本語"        TEXT         NULL,
    "例文読み"           TEXT         NULL,
    -- 読み上げ用のテキスト（音声再生は 2.1 では未実装）
    "音声テキスト"       TEXT         NULL,
    "正解値"             TEXT         NOT NULL,
    "正解補足"           TEXT         NULL,
    "解説_日本語"        TEXT         NULL,
    "解説_中国語"        TEXT         NULL,
    -- EASY / NORMAL / HARD
    "難易度"             VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    -- GENERATED / ACTIVE / REJECTED / ARCHIVED（2.0 の 状態 と同じコード）
    "状態コード"         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    "内容版数"           INTEGER      NOT NULL DEFAULT 1,
    -- AI が返した問題の構造化データをそのまま保持する
    "構造化JSON"         JSONB        NOT NULL DEFAULT '{}',
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_単語問題情報_pkey" PRIMARY KEY ("問題ID"),
    CONSTRAINT "FK_JPN_単語問題_単語"
        FOREIGN KEY ("単語ID")
        REFERENCES public."JPN_単語情報" ("単語ID") ON DELETE CASCADE,
    CONSTRAINT "FK_JPN_単語問題_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_単語問題_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_単語問題_問題番号"
        CHECK ("問題番号" >= 1 AND "内容版数" >= 1),
    CONSTRAINT "CK_JPN_単語問題_難易度"
        CHECK ("難易度" IN ('EASY', 'NORMAL', 'HARD')),
    CONSTRAINT "CK_JPN_単語問題_状態コード"
        CHECK ("状態コード" IN ('GENERATED', 'ACTIVE', 'REJECTED', 'ARCHIVED')),
    CONSTRAINT "CK_JPN_単語問題_正解値"
        CHECK (NULLIF(BTRIM("正解値"), '') IS NOT NULL),
    CONSTRAINT "CK_JPN_単語問題_構造化JSON"
        CHECK (jsonb_typeof("構造化JSON") = 'object')
);

-- 2.0 の 問題ID。移行の再実行を冪等にするための一意索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_jpn_question_old_id
    ON public."JPN_単語問題情報" ("旧問題ID");

-- 語から問題を引く（出題時に 種別で 1 問選ぶ）
CREATE INDEX IF NOT EXISTS idx_jpn_question_word
    ON public."JPN_単語問題情報" ("単語ID", "問題種別");

-- 種別ごとの有効問題数（単語情報管理画面の取得状況・テスト作成の対象件数）
CREATE INDEX IF NOT EXISTS idx_jpn_question_type
    ON public."JPN_単語問題情報" ("問題種別", "状態コード");

COMMENT ON TABLE public."JPN_単語問題情報" IS
    'C1/C2/D/E で使う AI 生成問題。2.0 の STY_日本語単語問題情報（1,700 件）';
COMMENT ON COLUMN public."JPN_単語問題情報"."旧問題ID" IS
    '2.0 の 問題ID。移行の冪等性と突き合わせに使う';
COMMENT ON COLUMN public."JPN_単語問題情報"."問題種別" IS
    'C1_READING / C2_KANJI / D_CONTEXT_MEANING / E_KANJI_USAGE。CHECK は付けず、値を増やすときはこのコメントと設計文書を更新する';
COMMENT ON COLUMN public."JPN_単語問題情報"."状態コード" IS
    'GENERATED=生成直後 / ACTIVE=採用 / REJECTED=却下 / ARCHIVED=旧版（2.0 の 状態 と同じコード）';
COMMENT ON COLUMN public."JPN_単語問題情報"."登録元コード" IS
    'APP=画面からの登録 / MIGRATION=2.0 からの移行';

-- ============================================================================
-- 追補（2026-09-13）: 単語を削除したら、その語の問題も一緒に消えるようにした
-- （収録・詳細・学習状況・技能習得は最初から ON DELETE CASCADE）。
-- すでにテーブルを作ってある環境でも、このファイルをもう一度実行すれば当たる。
-- ============================================================================

ALTER TABLE public."JPN_単語問題情報"
    DROP CONSTRAINT IF EXISTS "FK_JPN_単語問題_単語";
ALTER TABLE public."JPN_単語問題情報"
    ADD CONSTRAINT "FK_JPN_単語問題_単語"
        FOREIGN KEY ("単語ID")
        REFERENCES public."JPN_単語情報" ("単語ID") ON DELETE CASCADE;
