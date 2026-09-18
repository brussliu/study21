-- ============================================================================
-- Study 2.1  読書管理 DDL（最終仕様）
-- テーブル: RED_自分の本棚情報（アカウントごとの本棚）
-- ----------------------------------------------------------------------------
-- 画面の【自分の本棚】が読む「その人が入れた本」だけを持つテーブル。
-- 本そのもの（書名・作者・進捗・PDF）は `RED_書籍情報` にあり、ここは
-- 「誰がどの本を持っているか」だけを表す（2.0 に無かった概念）。
--
-- 2026-09-14 の決定:
--   1. **本棚の単位はアカウント（個人）**。【自分の本棚】という文言どおり。
--      家庭で共有したくなったら `アカウントID` を `家族学生ID` に読み替えるだけで、
--      設計（家族の解決 SQL）はそのまま使える。
--   2. **移行直後の本棚は空**。既存アカウントへ一括投入はしない。利用者が
--      【図書館】の【本棚に入れる】で 1 冊ずつ入れる（画面は空の案内を出す）。
--   3. 保護者が登録した本は、**登録した本人の本棚にだけ**自動で入る
--      （家庭の他の人は【図書館】から入れる）。管理者が登録した全体書籍は
--      誰の本棚にも入らない。
--
-- 設計のポイント:
--   * `UNIQUE (アカウントID, 書籍ID)` … 【本棚に入れる】は冪等になる
--     （`INSERT … ON CONFLICT DO NOTHING` で何度押しても 1 行）。
--   * アカウントは `ON DELETE RESTRICT`（監査列と同じ作法。物理削除は
--     「本棚 → その家庭の書籍 → アカウント」の順に行う）。
--   * 書籍は `ON DELETE CASCADE`（`RED_読書記録情報` / `RED_標記情報` と同じ。
--     本を消したらみんなの本棚からも消える）。
--   * 第 1 版は `表示順` を使わない（0 固定。並びは書籍側の 置頂 → 最終読書日時）。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."RED_自分の本棚情報" (
    "本棚ID"             BIGSERIAL    NOT NULL,
    -- この本棚の持ち主（生徒・保護者・管理者のいずれか 1 アカウント）
    "アカウントID"       BIGINT       NOT NULL,
    "書籍ID"             BIGINT       NOT NULL,
    -- 自分で並べ替えるとき用（第 1 版は 0 固定）
    "表示順"             INTEGER      NOT NULL DEFAULT 0,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    -- APP=画面から入れた / MIGRATION=移行時の初期投入（決定 2 により今回は使わない）
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "RED_自分の本棚情報_pkey" PRIMARY KEY ("本棚ID"),
    -- 同じ本を二重に入れない（【本棚に入れる】が冪等になる）
    CONSTRAINT "UK_RED_自分の本棚_アカウント書籍" UNIQUE ("アカウントID", "書籍ID"),
    CONSTRAINT "FK_RED_自分の本棚_アカウント"
        FOREIGN KEY ("アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    -- 本を消したら、みんなの本棚からも消える（標記・記録と同じ扱い）
    CONSTRAINT "FK_RED_自分の本棚_書籍"
        FOREIGN KEY ("書籍ID")
        REFERENCES public."RED_書籍情報" ("書籍ID") ON DELETE CASCADE,
    CONSTRAINT "FK_RED_自分の本棚_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_RED_自分の本棚_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_RED_自分の本棚_表示順" CHECK ("表示順" >= 0)
);

-- 本棚の一覧（持ち主 → 表示順 → 新しい順）
CREATE INDEX IF NOT EXISTS idx_red_my_shelf_account
    ON public."RED_自分の本棚情報" ("アカウントID", "表示順", "本棚ID" DESC);

-- 「この本を誰が持っているか」（書籍削除・可視判定の逆引き）
CREATE INDEX IF NOT EXISTS idx_red_my_shelf_book
    ON public."RED_自分の本棚情報" ("書籍ID");

COMMENT ON TABLE public."RED_自分の本棚情報" IS
    'アカウントごとの本棚。【図書館】から入れた本だけが並ぶ（本そのものは RED_書籍情報）';
COMMENT ON COLUMN public."RED_自分の本棚情報"."アカウントID" IS
    '本棚の持ち主。同じ本を家族の別の人が入れた場合は別の行になる';
COMMENT ON COLUMN public."RED_自分の本棚情報"."書籍ID" IS
    '入れた本（RED_書籍情報）。本を削除すると CASCADE でこの行も消える';
COMMENT ON COLUMN public."RED_自分の本棚情報"."表示順" IS
    '自分で並べ替えるとき用（第 1 版は 0 固定。並びは 置頂 → 最終読書日時）';
COMMENT ON COLUMN public."RED_自分の本棚情報"."登録元コード" IS
    'APP=画面から入れた / MIGRATION=移行時の初期投入（決定により今回は使わない）';
