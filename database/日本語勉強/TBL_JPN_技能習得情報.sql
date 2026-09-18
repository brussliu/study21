-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_技能習得情報（利用者ごと・単語ごと・技能区分ごとの習得度）
-- ----------------------------------------------------------------------------
-- 2.0 の `STY_日本語単語技能習得情報`（実データ 906 件。study3 DB。移行日 2026-09-13 実測）を
-- 2.1 の規約で再設計する。1 利用者 × 1 単語 × 1 テスト種別 × 1 技能区分 = 1 行（主キー）。
--
-- 2.0 の実データで分かっていること（移行の前提）:
--   * `ユーザーID` は **全件 'liu'**（'liu' → アカウント 1）。
--   * `技能区分` の内訳（6 種類）:
--       B_ORTHOGRAPHY 180 / B_READING_RECALL 180 / C_READING_RECOGNITION 180 /
--       C_KANJI_RECOGNITION 6 / D_CONTEXT_MEANING 180 / E_KANJI_USAGE 180
--     （C_KANJI_RECOGNITION は 6 件だけ＝C2 の問題が用意できていない語があるため）
--   * `学習状態` は LEARNING 848 / REVIEW 58。`最終判定` は CORRECT 848 / INCORRECT 58。
--   * `次回復習日時` は **全件 NULL**。
--   * 2.0 の主キーは（ユーザーID, 日本語単語ID, 技能区分）で テスト種別 を含まないが、
--     技能区分はテスト種別を一意に決める（B_* → B など）ので、
--     2.1 で テスト種別 を主キーに足しても一意性は変わらない（実データで重複 0 件を確認）。
--
-- 2.0 からの主な変更:
--   1. 持ち主を ユーザーID（VARCHAR）から 利用者アカウントID（FK）へ移した。
--   2. 主キーに テスト種別 を明示的に足して 4 列にした（技能区分 から決まる値を冗長に持つ。
--      「B の正解率」「C の正解率」を種別で引くため）。
--   3. `技能区分` の CHECK は 2.0 と同じ組み合わせを保つ（種別と技能の不整合を DB で防ぐ）。
--   4. 監査列を 2.1 の規約へ統一した。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_技能習得情報" (
    "利用者アカウントID" BIGINT       NOT NULL,
    "単語ID"             BIGINT       NOT NULL,
    -- B / C / D / E（A.勉強は技能別の習得度を持たない）
    "テスト種別"         CHAR(1)      NOT NULL,
    -- B_ORTHOGRAPHY / B_READING_RECALL / C_READING_RECOGNITION /
    -- C_KANJI_RECOGNITION / D_CONTEXT_MEANING / E_KANJI_USAGE
    "技能区分"           VARCHAR(50)  NOT NULL,
    -- NOT_STARTED / LEARNING / REVIEW / MASTERED
    "学習状態"           VARCHAR(20)  NOT NULL DEFAULT 'NOT_STARTED',
    -- 0〜100。この技能だけの習得度
    "習得度"             NUMERIC(5,2) NOT NULL DEFAULT 0,
    "回答回数"           INTEGER      NOT NULL DEFAULT 0,
    "正解回数"           INTEGER      NOT NULL DEFAULT 0,
    "不正解回数"         INTEGER      NOT NULL DEFAULT 0,
    "連続正解回数"       INTEGER      NOT NULL DEFAULT 0,
    "最大連続正解回数"   INTEGER      NOT NULL DEFAULT 0,
    -- CORRECT / INCORRECT / CONFIRMED / MIXED / SKIPPED
    "最終判定"           VARCHAR(20)  NULL,
    "最終学習日時"       TIMESTAMP    NULL,
    "次回復習日時"       TIMESTAMP    NULL,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_技能習得情報_pkey"
        PRIMARY KEY ("利用者アカウントID", "単語ID", "テスト種別", "技能区分"),
    CONSTRAINT "FK_JPN_技能習得_利用者"
        FOREIGN KEY ("利用者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_技能習得_単語"
        FOREIGN KEY ("単語ID")
        REFERENCES public."JPN_単語情報" ("単語ID") ON DELETE CASCADE,
    CONSTRAINT "FK_JPN_技能習得_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_技能習得_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_技能習得_種別と技能"
        CHECK (
            ("テスト種別" = 'B' AND "技能区分" IN ('B_ORTHOGRAPHY', 'B_READING_RECALL'))
            OR ("テスト種別" = 'C' AND "技能区分" IN ('C_READING_RECOGNITION', 'C_KANJI_RECOGNITION'))
            OR ("テスト種別" = 'D' AND "技能区分" = 'D_CONTEXT_MEANING')
            OR ("テスト種別" = 'E' AND "技能区分" = 'E_KANJI_USAGE')
        ),
    CONSTRAINT "CK_JPN_技能習得_学習状態"
        CHECK ("学習状態" IN ('NOT_STARTED', 'LEARNING', 'REVIEW', 'MASTERED')),
    CONSTRAINT "CK_JPN_技能習得_習得度"
        CHECK ("習得度" BETWEEN 0 AND 100),
    CONSTRAINT "CK_JPN_技能習得_件数"
        CHECK (
            "回答回数" >= 0 AND "正解回数" >= 0 AND "不正解回数" >= 0
            AND "正解回数" + "不正解回数" <= "回答回数"
            AND "連続正解回数" >= 0 AND "最大連続正解回数" >= 0
            AND "連続正解回数" <= "最大連続正解回数"
        )
);

-- 技能区分ごとの習得状況（単語勉強状況画面の B/C/D/E 別の集計）
CREATE INDEX IF NOT EXISTS idx_jpn_skill_user
    ON public."JPN_技能習得情報" ("利用者アカウントID", "技能区分");

-- 復習予定の一覧（技能ごとの次回復習）
CREATE INDEX IF NOT EXISTS idx_jpn_skill_review
    ON public."JPN_技能習得情報" ("利用者アカウントID", "次回復習日時");

COMMENT ON TABLE public."JPN_技能習得情報" IS
    'B1/B2・C1/C2・D・E の技能別正解率と習得度。2.0 の STY_日本語単語技能習得情報（906 件）';
COMMENT ON COLUMN public."JPN_技能習得情報"."技能区分" IS
    'B_ORTHOGRAPHY / B_READING_RECALL / C_READING_RECOGNITION / C_KANJI_RECOGNITION / D_CONTEXT_MEANING / E_KANJI_USAGE';
COMMENT ON COLUMN public."JPN_技能習得情報"."テスト種別" IS
    '技能区分から一意に決まるが、種別ごとの集計のために主キーに含めて冗長に持つ';
COMMENT ON COLUMN public."JPN_技能習得情報"."登録元コード" IS
    'APP=画面からの登録 / MIGRATION=2.0 からの移行';
