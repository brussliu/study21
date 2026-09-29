-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_書籍情報（教材のマスタ）
-- ----------------------------------------------------------------------------
-- 2.0 は 書籍マスタを持たず、`STY_日本語単語収録情報.書籍` の**文字列だけ**で
-- 教材を表していた（実データは `01.N1~N5日本語単語` の 1 冊のみ）。
-- 2.1 の新規登録画面は「先に書籍を作り、その書籍に語を入れる」流れなので、
-- 書籍そのものに ID・表示名・並び・状態を持たせる。
--
-- 収録情報（JPN_単語収録情報）との関係:
--   ・1 冊の書籍に複数の収録（語 × 分類）がぶら下がる（1 対多）
--   ・同じ語が複数の書籍に載ることがあるので、**語と書籍は多対多**になる
--     （`JPN_単語情報` × `JPN_書籍情報` を `JPN_単語収録情報` がつなぐ）
--   ・`JPN_単語収録情報.書籍`（2.0 由来の表示名）は**残す**。
--     移行データと突き合わせるため、消さずに `書籍ID` を併せて持つ。
--
-- 実データからの起こし方（`UPD_JPN_書籍情報_20260922.sql`）:
--   `01.N1~N5日本語単語` → `書籍コード='01'` / `書籍名='N1~N5日本語単語'`
--   （先頭の `.` より前をコード、後ろを名前にする。`.` が無ければ全体を名前にする）
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_書籍情報" (
    "書籍ID"             BIGSERIAL    NOT NULL,
    -- 2.0 の STY_日本語単語収録情報.書籍 を分解したコード。移行の突き合わせに使う
    "旧書籍コード"       VARCHAR(100) NULL,
    -- 教材の識別コード（例: '01'）。名前に埋め込まず、別の列で持つ
    "書籍コード"         VARCHAR(100) NOT NULL,
    -- 画面に出す名前（例: 'N1~N5日本語単語'）
    "書籍名"             VARCHAR(300) NOT NULL,
    -- 同じシリーズの別巻をまとめるときに使う（今は未使用。増やすときのために用意する）
    "シリーズ名"         VARCHAR(300) NULL,
    -- この教材が対象にする JLPT の範囲（例: 'N1-N5'）。分からない本は NULL にする
    "レベル範囲"         VARCHAR(20)  NULL,
    -- 統計用（登録時に数え直す。画面の並べ替えと表示に使う）
    "分類数"             INTEGER      NOT NULL DEFAULT 0,
    "収録語数"           INTEGER      NOT NULL DEFAULT 0,
    "表示順"             INTEGER      NOT NULL DEFAULT 0,
    -- ACTIVE=使う / INACTIVE=使わない（消さずに一覧から外す）
    "状態コード"         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    "備考"               TEXT         NULL,
    -- 楽観的ロック
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    -- APP=画面からの登録 / MIGRATION=2.0 からの移行 / SEED=初期投入
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_書籍情報_pkey" PRIMARY KEY ("書籍ID"),
    CONSTRAINT "FK_JPN_書籍_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_書籍_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_書籍_状態コード"
        CHECK ("状態コード" IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT "CK_JPN_書籍_レベル範囲"
        CHECK (
            "レベル範囲" IS NULL
            OR "レベル範囲" ~ '^N[1-5](-N[1-5])?$'
        ),
    CONSTRAINT "CK_JPN_書籍_件数"
        CHECK ("分類数" >= 0 AND "収録語数" >= 0 AND "表示順" >= 0),
    CONSTRAINT "CK_JPN_書籍_バージョン"
        CHECK ("バージョン" >= 1),
    CONSTRAINT "CK_JPN_書籍_名前"
        CHECK (
            NULLIF(BTRIM("書籍コード"), '') IS NOT NULL
            AND NULLIF(BTRIM("書籍名"), '') IS NOT NULL
        )
);

-- 同じコードの書籍を二重に作らない（画面の「新しい書籍を追加」で守る）
CREATE UNIQUE INDEX IF NOT EXISTS uq_jpn_book_code
    ON public."JPN_書籍情報" ("書籍コード");

-- 移行の再実行を冪等にするための一意索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_jpn_book_old_code
    ON public."JPN_書籍情報" ("旧書籍コード");

-- 一覧の並べ替えと絞り込み（名前順・状態）
CREATE INDEX IF NOT EXISTS idx_jpn_book_order
    ON public."JPN_書籍情報" ("表示順", "書籍名");

CREATE INDEX IF NOT EXISTS idx_jpn_book_state
    ON public."JPN_書籍情報" ("状態コード");

COMMENT ON TABLE public."JPN_書籍情報" IS
    '日本語教材のマスタ。2.0 は収録情報の書籍名だけだったので 2.1 で新設した（語とは多対多）';
COMMENT ON COLUMN public."JPN_書籍情報"."旧書籍コード" IS
    '2.0 の STY_日本語単語収録情報.書籍（例: 01.N1~N5日本語単語）から起こした元の値。移行の冪等性に使う';
COMMENT ON COLUMN public."JPN_書籍情報"."書籍コード" IS
    '教材の識別コード（例: 01）。表示名とは別に持つ（並べ替えと突き合わせに使う）';
COMMENT ON COLUMN public."JPN_書籍情報"."書籍名" IS
    '画面に出す教材名（例: N1~N5日本語単語）。JPN_単語収録情報.書籍 と同じ値を持つ';
COMMENT ON COLUMN public."JPN_書籍情報"."シリーズ名" IS
    '同じシリーズの別巻をまとめるための名前。今は使わない（増やすときのために用意）';
COMMENT ON COLUMN public."JPN_書籍情報"."レベル範囲" IS
    'この教材が対象にする JLPT の範囲（例: N1-N5）。分からない本は NULL（推測で埋めない）';
COMMENT ON COLUMN public."JPN_書籍情報"."状態コード" IS
    'ACTIVE=使う / INACTIVE=使わない（行は消さずに一覧から外す）';
COMMENT ON COLUMN public."JPN_書籍情報"."登録元コード" IS
    'APP=画面からの登録 / MIGRATION=2.0 からの移行 / SEED=初期投入';
