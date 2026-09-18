-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_学習日次情報（利用者ごと・学習日ごとの集計）
-- ----------------------------------------------------------------------------
-- 2.0 の `STY_日本語学習日次情報`（実データ 13 件。study3 DB。移行日 2026-09-13 実測）を
-- 2.1 の規約で再設計する。1 利用者 × 1 学習日 = 1 行（主キー）。
--
-- この表は日本語勉強ホーム（2.0 `japanese.jsp`）の
-- 「今日の学習時間」「累計学習時間」「連続学習日数」「30日間の学習推移」に使う集計表。
--
-- 2.0 の実データで分かっていること（移行の前提）:
--   * `ユーザーID` は **全件 'liu'**（'liu' → アカウント 1）。
--   * 学習日は 2026-08-27 〜 2026-09-13 の 13 日分。
--   * テスト種別ごとの学習時間（A〜E）の合計は `有効学習時間ms` と一致している
--     （実データ 13 件で確認済み）。
--   * `完了テスト数` は 2.0 の実データでは **全件 0**（1 回のテストを複数日にまたぐと
--     完了日の日次に数えられていないため）。2.1 ではテスト完了時にその日の行へ加算する。
--   * 2.0 の `完了課題数` / `正解課題数` / `不正解課題数` は **課題単位**の数
--     （B は 1 語 2 課題）。2.1 は課題テーブルを持たないが、日次の粒度を変えると
--     2.0 と比較できなくなるので **列名も意味もそのまま引き継ぐ**
--     （＝ 出題 1 語ぶんの正誤を数えた値として扱う。設計文書に明記）。
--
-- 2.0 からの主な変更:
--   1. 持ち主を ユーザーID（VARCHAR）から 利用者アカウントID（FK）へ移した。
--   2. 監査列を 2.1 の規約へ統一した。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_学習日次情報" (
    "利用者アカウントID" BIGINT      NOT NULL,
    "学習日"             DATE        NOT NULL,
    -- その日の有効学習時間の合計
    "有効学習時間ms"     BIGINT      NOT NULL DEFAULT 0,
    -- テスト種別ごとの内訳（合計は 有効学習時間ms を超えない）
    "A学習時間ms"        BIGINT      NOT NULL DEFAULT 0,
    "B学習時間ms"        BIGINT      NOT NULL DEFAULT 0,
    "C学習時間ms"        BIGINT      NOT NULL DEFAULT 0,
    "D学習時間ms"        BIGINT      NOT NULL DEFAULT 0,
    "E学習時間ms"        BIGINT      NOT NULL DEFAULT 0,
    "学習単語数"         INTEGER     NOT NULL DEFAULT 0,
    "完了テスト数"       INTEGER     NOT NULL DEFAULT 0,
    -- 正誤の数（2.0 の「課題」＝出題 1 語ぶんの正誤）
    "完了課題数"         INTEGER     NOT NULL DEFAULT 0,
    "正解課題数"         INTEGER     NOT NULL DEFAULT 0,
    "不正解課題数"       INTEGER     NOT NULL DEFAULT 0,
    "登録者アカウントID" BIGINT      NULL,
    "更新者アカウントID" BIGINT      NULL,
    "登録元コード"       VARCHAR(20) NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20) NULL,
    "登録日時"           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_学習日次情報_pkey" PRIMARY KEY ("利用者アカウントID", "学習日"),
    CONSTRAINT "FK_JPN_学習日次_利用者"
        FOREIGN KEY ("利用者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_学習日次_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_学習日次_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_学習日次_学習時間"
        CHECK (
            "有効学習時間ms" >= 0
            AND "A学習時間ms" >= 0 AND "B学習時間ms" >= 0 AND "C学習時間ms" >= 0
            AND "D学習時間ms" >= 0 AND "E学習時間ms" >= 0
            AND "A学習時間ms" + "B学習時間ms" + "C学習時間ms"
                + "D学習時間ms" + "E学習時間ms" <= "有効学習時間ms"
        ),
    CONSTRAINT "CK_JPN_学習日次_件数"
        CHECK (
            "学習単語数" >= 0 AND "完了テスト数" >= 0 AND "完了課題数" >= 0
            AND "正解課題数" >= 0 AND "不正解課題数" >= 0
            AND "正解課題数" + "不正解課題数" <= "完了課題数"
        )
);

-- 日別の一覧（新しい順。ホームの推移グラフと学習状況の集計）
CREATE INDEX IF NOT EXISTS idx_jpn_daily_user
    ON public."JPN_学習日次情報" ("利用者アカウントID", "学習日" DESC);

COMMENT ON TABLE public."JPN_学習日次情報" IS
    '日本語勉強ホームの今日・累計時間、連続学習、日別推移に使う日次集計。2.0 の STY_日本語学習日次情報（13 件）';
COMMENT ON COLUMN public."JPN_学習日次情報"."完了課題数" IS
    '2.0 の「課題」＝出題 1 語ぶんの完了数。2.1 は課題テーブルを持たないが、2.0 と比較できるように列名を引き継ぐ';
COMMENT ON COLUMN public."JPN_学習日次情報"."完了テスト数" IS
    'その日に完了したテスト数。2.0 の実データは全件 0（複数日にまたぐテストが数えられていなかった）';
COMMENT ON COLUMN public."JPN_学習日次情報"."登録元コード" IS
    'APP=画面からの登録 / MIGRATION=2.0 からの移行';
