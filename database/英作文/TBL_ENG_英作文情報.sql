-- ============================================================================
-- Study 2.1  英作文AI添削 / 英作文情報  DDL
-- テーブル: ENG_英作文情報 （★2.1 新設。2.0 の STY_英作文情報 に相当）
-- ----------------------------------------------------------------------------
-- 1 行 = 1 回の提出（設問と答案の画像を上げ、文字にして、AI に添削させる）。
-- **添削の結果はここに持たない**（何度も添削できるので ENG_AI添削履歴情報 に積む）。
-- 画像は ENG_英作文画像情報（設問画像／答案画像）。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順: TBL_ACC_アカウント.sql の後 → 本ファイル → TBL_ENG_英作文画像情報.sql
--         → TBL_ENG_AI添削履歴情報.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."ENG_英作文情報" (
    "英作文ID"           BIGSERIAL    NOT NULL,
    -- 提出した利用者（生徒。保護者が代理で作る場合も保護者のアカウント）
    "利用者アカウントID" BIGINT       NOT NULL,
    -- 英検級。添削の配点（GRADE1 = 各観点 8 で計 32／PRE1・GRADE2 = 各 4 で計 16）がこれで決まる
    "英検級"             VARCHAR(20)  NOT NULL,
    -- 題（日本語）。AI が付けた主題タイトルを利用者が直せる
    "題"                 VARCHAR(200) NOT NULL,
    -- 題（中国語）。レポートの「標題（中文）」に使う
    "題_中国語"          VARCHAR(200) NULL,
    -- 作文の設問（OCR の結果を利用者が直したもの）
    "設問文"             TEXT         NULL,
    -- 手書き作文（同上）
    "作文本文"           TEXT         NULL,
    -- 語数（作文本文から数えた値。一覧と検索のため持つ）
    "語数"               INTEGER      NOT NULL DEFAULT 0,
    -- A = 有効 / X = 削除（論理削除。2.0 と同じ。添削の履歴は残す）
    "状態コード"         VARCHAR(20)  NOT NULL DEFAULT 'A',

    -- ---- 2.1 の共通規約 ----
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ENG_英作文情報_pkey" PRIMARY KEY ("英作文ID"),
    CONSTRAINT "CK_ENG_英作文_級" CHECK ("英検級" IN ('GRADE1', 'PRE1', 'GRADE2')),
    CONSTRAINT "CK_ENG_英作文_状態" CHECK ("状態コード" IN ('A', 'X')),
    CONSTRAINT "CK_ENG_英作文_語数" CHECK ("語数" >= 0),
    CONSTRAINT "FK_ENG_英作文_利用者" FOREIGN KEY ("利用者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE CASCADE
);

-- 一覧（自分の作文を新しい順に引く）
CREATE INDEX IF NOT EXISTS idx_eng_essay_owner_created
    ON public."ENG_英作文情報" ("利用者アカウントID", "登録日時" DESC, "英作文ID" DESC)
    WHERE "状態コード" = 'A';

-- 絞り込み（英検級）と、提出済みの判定
CREATE INDEX IF NOT EXISTS idx_eng_essay_owner_level
    ON public."ENG_英作文情報" ("利用者アカウントID", "英検級")
    WHERE "状態コード" = 'A';

COMMENT ON TABLE public."ENG_英作文情報" IS
    '英作文の提出（1 行 = 1 回の提出）。添削の結果は ENG_AI添削履歴情報 に積む（何度も添削できる）';
COMMENT ON COLUMN public."ENG_英作文情報"."英検級" IS
    'GRADE1 / PRE1 / GRADE2。添削の観点の満点（8 か 4）はこの級で決まる';
COMMENT ON COLUMN public."ENG_英作文情報"."題" IS
    '題（日本語）。AI が付けた主題タイトルを利用者が直せる。レポートの「作文タイトル」に出す';
COMMENT ON COLUMN public."ENG_英作文情報"."設問文" IS
    '作文の設問。OCR の結果を利用者が直したもの（添削のたびに履歴へ写す）';
COMMENT ON COLUMN public."ENG_英作文情報"."作文本文" IS
    '手書き作文の文字起こし。OCR の結果を利用者が直したもの（添削のたびに履歴へ写す）';
COMMENT ON COLUMN public."ENG_英作文情報"."状態コード" IS
    'A = 有効 / X = 削除（論理削除）。削除しても添削の履歴は残す（履歴側で見られる）';
