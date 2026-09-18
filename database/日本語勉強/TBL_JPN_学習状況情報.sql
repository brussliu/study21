-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_学習状況情報（利用者ごと・単語ごとの学習状況）
-- ----------------------------------------------------------------------------
-- 2.0 の `STY_日本語単語学習状況情報`（実データ 213 件。study3 DB。移行日 2026-09-13 実測）を
-- 2.1 の規約で再設計する。1 利用者 × 1 単語 = 1 行（主キー）。
--
-- 2.0 の実データで分かっていること（移行の前提）:
--   * `ユーザーID` は **全件 'liu'**（保護者）。2.1 の 利用者アカウントID は
--     既存の移行と同じ対応（'liu' → 1 / 'ljz' → 2）で読み替える。
--   * `学習状態` は LEARNING 204 / REVIEW 9（'NOT_STARTED' と 'MASTERED' は行が無いだけ）。
--   * `総合習得度` は 10.00〜77.50。`習得済フラグ` と `お気に入りフラグ` は **全件 false**。
--   * `習得日時` / `お気に入り日時` / `次回復習日時` は **全件 NULL**、`復習間隔日数` は全件 0。
--     つまり 2.0 は復習の予定（SRS）までは動いていなかった。列だけ用意しておく。
--   * `最終テスト種別` は A / D / E、`最終課題コード` は A_STUDY / D_CONTEXT_MEANING /
--     E_KANJI_USAGE が入っている（B は語ごとの技能習得側に残っている）。
--   * `初回学習日時` / `最終学習日時` は全件入っている。
--
-- 2.0 からの主な変更:
--   1. 持ち主を ユーザーID（VARCHAR）から 利用者アカウントID（ACC_アカウント への FK）へ移した。
--   2. 監査列を 2.1 の規約へ統一した。
--   3. 技能別（B1/B2・C1/C2・D・E）の習得度は `JPN_技能習得情報` に分けたままにする
--      （2.0 も別テーブルだった。画面は B/C/D/E 別の正解率を出す）。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_学習状況情報" (
    "利用者アカウントID" BIGINT       NOT NULL,
    "単語ID"             BIGINT       NOT NULL,
    -- NOT_STARTED / LEARNING / REVIEW / MASTERED
    "学習状態"           VARCHAR(20)  NOT NULL DEFAULT 'NOT_STARTED',
    -- 0〜100。語全体の総合習得度
    "総合習得度"         NUMERIC(5,2) NOT NULL DEFAULT 0,
    "習得済フラグ"       BOOLEAN      NOT NULL DEFAULT FALSE,
    "習得日時"           TIMESTAMP    NULL,
    "お気に入りフラグ"   BOOLEAN      NOT NULL DEFAULT FALSE,
    "お気に入り日時"     TIMESTAMP    NULL,
    -- A.勉強で詳細を確認した回数
    "A確認回数"          INTEGER      NOT NULL DEFAULT 0,
    "回答回数"           INTEGER      NOT NULL DEFAULT 0,
    "正解回数"           INTEGER      NOT NULL DEFAULT 0,
    "不正解回数"         INTEGER      NOT NULL DEFAULT 0,
    "連続正解回数"       INTEGER      NOT NULL DEFAULT 0,
    "最大連続正解回数"   INTEGER      NOT NULL DEFAULT 0,
    "有効学習時間ms"     BIGINT       NOT NULL DEFAULT 0,
    -- 最後に解いたテスト種別（A〜E）
    "最終テスト種別"     CHAR(1)      NULL,
    -- 最後に解いた課題コード（A_STUDY / B1_ORTHOGRAPHY / … / E_KANJI_USAGE）
    "最終課題コード"     VARCHAR(30)  NULL,
    -- CORRECT / INCORRECT / CONFIRMED / MIXED / SKIPPED
    "最終判定"           VARCHAR(20)  NULL,
    "初回学習日時"       TIMESTAMP    NULL,
    "最終学習日時"       TIMESTAMP    NULL,
    -- 次に復習すべき日時（2.0 の実データは全件 NULL。SRS を入れるときに使う）
    "次回復習日時"       TIMESTAMP    NULL,
    "復習間隔日数"       INTEGER      NOT NULL DEFAULT 0,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_学習状況情報_pkey" PRIMARY KEY ("利用者アカウントID", "単語ID"),
    CONSTRAINT "FK_JPN_学習状況_利用者"
        FOREIGN KEY ("利用者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_学習状況_単語"
        FOREIGN KEY ("単語ID")
        REFERENCES public."JPN_単語情報" ("単語ID") ON DELETE CASCADE,
    CONSTRAINT "FK_JPN_学習状況_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_学習状況_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_学習状況_学習状態"
        CHECK ("学習状態" IN ('NOT_STARTED', 'LEARNING', 'REVIEW', 'MASTERED')),
    CONSTRAINT "CK_JPN_学習状況_総合習得度"
        CHECK ("総合習得度" BETWEEN 0 AND 100),
    CONSTRAINT "CK_JPN_学習状況_件数"
        CHECK (
            "A確認回数" >= 0 AND "回答回数" >= 0 AND "正解回数" >= 0 AND "不正解回数" >= 0
            AND "正解回数" + "不正解回数" <= "回答回数"
            AND "連続正解回数" >= 0 AND "最大連続正解回数" >= 0
            AND "連続正解回数" <= "最大連続正解回数"
            AND "有効学習時間ms" >= 0 AND "復習間隔日数" >= 0
        ),
    CONSTRAINT "CK_JPN_学習状況_最終テスト種別"
        CHECK ("最終テスト種別" IS NULL OR "最終テスト種別" IN ('A', 'B', 'C', 'D', 'E'))
);

-- 学習状態での絞り込み（学習中・要復習の一覧）
CREATE INDEX IF NOT EXISTS idx_jpn_status_state
    ON public."JPN_学習状況情報" ("利用者アカウントID", "学習状態");

-- 復習予定の一覧（次回復習日時の早い順に出す）
CREATE INDEX IF NOT EXISTS idx_jpn_status_review
    ON public."JPN_学習状況情報" ("利用者アカウントID", "次回復習日時");

COMMENT ON TABLE public."JPN_学習状況情報" IS
    '利用者・単語単位の総合習得度、お気に入り、復習予定、学習時間。2.0 の STY_日本語単語学習状況情報（213 件）';
COMMENT ON COLUMN public."JPN_学習状況情報"."総合習得度" IS
    '語全体の習得度（0〜100）。技能別は JPN_技能習得情報.習得度';
COMMENT ON COLUMN public."JPN_学習状況情報"."次回復習日時" IS
    '次に復習すべき日時。2.0 の実データは全件 NULL（復習予定は動いていなかった）';
COMMENT ON COLUMN public."JPN_学習状況情報"."最終課題コード" IS
    'A_STUDY / B1_ORTHOGRAPHY / B2_READING / C1_READING / C2_KANJI / D_CONTEXT_MEANING / E_KANJI_USAGE';
COMMENT ON COLUMN public."JPN_学習状況情報"."登録元コード" IS
    'APP=画面からの登録 / MIGRATION=2.0 からの移行';
