-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_単語収録情報（その単語が教材のどこに載っているか）
-- ----------------------------------------------------------------------------
-- 2.0 の `STY_日本語単語収録情報`（実データ 9,886 件。study3 DB。移行日 2026-09-13 実測）を
-- 2.1 の規約で再設計する。1 単語は複数の書籍・Unit に収録されうるので 1 対多。
--
-- 2.0 からの主な変更:
--   1. 主キー 収録ID は 2.1 でも BIGSERIAL。2.0 の ID は 旧収録ID に残す。
--   2. 親（単語）への参照を 単語ID（BIGINT FK）に変え、ON DELETE CASCADE にした
--      （単語を消したら収録情報も消える）。
--   3. 監査列を 2.1 の規約に統一した（2.0 の 登録ID/更新ID は全件 'MIGRATION_STUDY3'）。
--   4. 状態 → 状態コード。
--
-- 2.0 の実データで分かっていること（移行の前提）:
--   * `レベル` は **全件 'N1-N5'**、`書籍` は **全件 '01.N1~N5日本語単語'** の 1 冊のみ。
--     母表側の `代表JLPTレベル` が全件 NULL なので、**レベルの実体はこの列**にある。
--   * `分類` は 'Unit001'〜'Unit590' の 588 種類（教材の課次）。`単語SEQ` は 1〜9,886。
--   * `(書籍, 分類, 単語SEQ)` の 2.0 の UNIQUE 制約は成立している（重複 0 件）。
--   * `掲載品詞` / `掲載中国語意味` は教材の紙面どおりの表記で、母表の正規化値とは別物。
--     単語情報管理画面と単語テストで「教材のどこに載っているか」を出すために持つ。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_単語収録情報" (
    "収録ID"             BIGSERIAL    NOT NULL,
    -- 2.0 の 収録ID。冪等な移行（ON CONFLICT）と突き合わせの根拠として残す
    "旧収録ID"           BIGINT       NULL,
    "単語ID"             BIGINT       NOT NULL,
    -- 2.0 は 'N1-N5' 固定。レベル帯を表す
    "レベル"             VARCHAR(20)  NOT NULL,
    -- 2.0 は '01.N1~N5日本語単語' の 1 冊
    "書籍"               VARCHAR(100) NOT NULL,
    -- 'Unit001' 形式の課次
    "分類"               VARCHAR(30)  NOT NULL,
    "単語SEQ"            INTEGER      NOT NULL,
    -- 教材の紙面どおりの表記（母表の正規化値とは別）
    "掲載見出し語"       VARCHAR(300) NULL,
    "掲載読み"           VARCHAR(300) NULL,
    "掲載品詞"           VARCHAR(100) NULL,
    "掲載中国語意味"     TEXT         NULL,
    -- 教材の出典情報（ページ番号など）。2.0 から引き継ぐ
    "出典JSON"           JSONB        NOT NULL DEFAULT '{}',
    -- ACTIVE / INACTIVE
    "状態コード"         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_単語収録情報_pkey" PRIMARY KEY ("収録ID"),
    CONSTRAINT "FK_JPN_単語収録_単語"
        FOREIGN KEY ("単語ID")
        REFERENCES public."JPN_単語情報" ("単語ID") ON DELETE CASCADE,
    CONSTRAINT "FK_JPN_単語収録_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_単語収録_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_単語収録_単語SEQ"
        CHECK ("単語SEQ" >= 1),
    CONSTRAINT "CK_JPN_単語収録_状態コード"
        CHECK ("状態コード" IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT "CK_JPN_単語収録_出典JSON"
        CHECK (jsonb_typeof("出典JSON") = 'object'),
    CONSTRAINT "CK_JPN_単語収録_必須文字列"
        CHECK (
            NULLIF(BTRIM("レベル"), '') IS NOT NULL
            AND NULLIF(BTRIM("書籍"), '') IS NOT NULL
            AND NULLIF(BTRIM("分類"), '') IS NOT NULL
        )
);

-- 2.0 の 収録ID。移行の再実行を冪等にするための一意索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_jpn_collect_old_id
    ON public."JPN_単語収録情報" ("旧収録ID");

-- 単語から収録位置を引く（単語情報管理画面・単語テストの出題対象選択）
CREATE INDEX IF NOT EXISTS idx_jpn_collect_word
    ON public."JPN_単語収録情報" ("単語ID");

-- 教材の書籍・課次から単語を並べる（単語テストの分類範囲指定）
CREATE INDEX IF NOT EXISTS idx_jpn_collect_book
    ON public."JPN_単語収録情報" ("書籍", "分類", "単語SEQ");

COMMENT ON TABLE public."JPN_単語収録情報" IS
    '日本語単語が教材のどの書籍・分類（Unit）・SEQ に収録されているか。2.0 の STY_日本語単語収録情報（9,886 件）';
COMMENT ON COLUMN public."JPN_単語収録情報"."旧収録ID" IS
    '2.0 の 収録ID。移行の冪等性と突き合わせに使う';
COMMENT ON COLUMN public."JPN_単語収録情報"."レベル" IS
    'レベル帯。2.0 の実データは全件 ''N1-N5''。母表の JLPTレベル が全件 NULL のため、実質的なレベルはこの列';
COMMENT ON COLUMN public."JPN_単語収録情報"."分類" IS
    '''Unit001'' 形式の課次。2.0 の実データは Unit001〜Unit590';
COMMENT ON COLUMN public."JPN_単語収録情報"."掲載品詞" IS
    '教材（2.0 の STY_単語情報）に記載された品詞';
COMMENT ON COLUMN public."JPN_単語収録情報"."登録元コード" IS
    'APP=画面からの登録 / MIGRATION=2.0 からの移行';
