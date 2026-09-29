-- ============================================================================
-- Study 2.1  英作文AI添削 / 英作文画像情報  DDL
-- テーブル: ENG_英作文画像情報 （★2.1 新設。2.0 の STY_英作文画像情報 に相当）
-- ----------------------------------------------------------------------------
-- 1 行 = 1 枚の画像（設問画像／答案画像）。実体は DB の外（相対パス + ファイル名）。
-- OCR の結果（その画像の文字と信頼度）も**この行に持つ**（利用者が直した本文は
-- ENG_英作文情報 側にあり、こちらは認識した生の結果）。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順: TBL_ENG_英作文情報.sql の後
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."ENG_英作文画像情報" (
    "英作文画像ID"        BIGSERIAL    NOT NULL,
    "英作文ID"            BIGINT       NOT NULL,
    -- 表示順（1 から。設問画像 → 答案画像の順に見せる）
    "表示順"              INTEGER      NOT NULL DEFAULT 1,
    -- question = 設問画像 / answer = 答案画像（利用者が 1 枚ずつ指定する）
    "画像区分"            VARCHAR(20)  NOT NULL DEFAULT 'question',
    -- 上げられた元のファイル名（画面に出す）
    "原本ファイル名"      VARCHAR(300) NULL,
    -- 保存したファイル名（UUID + 拡張子。元の名前は使わない）
    "保存ファイル名"      VARCHAR(200) NOT NULL,
    -- 保存先の相対パス（例 'english-essay/2/202609'。ストレージ根は設定で決まる）
    "相対パス"            VARCHAR(500) NOT NULL,
    "MIMEタイプ"          VARCHAR(120) NOT NULL,
    "ファイルサイズ"      BIGINT       NOT NULL DEFAULT 0,
    -- OCR（画像 → 文字）の結果。まだ読んでいなければ NULL
    "認識テキスト"        TEXT         NULL,
    -- 認識の信頼度（0〜100）。画面に「信頼度 N%」と出す
    "認識信頼度"          INTEGER      NULL,

    "登録者アカウントID"  BIGINT       NULL,
    "登録日時"            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ENG_英作文画像情報_pkey" PRIMARY KEY ("英作文画像ID"),
    CONSTRAINT "CK_ENG_英作文画像_区分" CHECK ("画像区分" IN ('question', 'answer')),
    CONSTRAINT "CK_ENG_英作文画像_順" CHECK ("表示順" >= 1),
    CONSTRAINT "CK_ENG_英作文画像_信頼度" CHECK ("認識信頼度" IS NULL OR ("認識信頼度" >= 0 AND "認識信頼度" <= 100)),
    -- 同じ作文の中で表示順は重ならない
    CONSTRAINT "UK_ENG_英作文画像_順" UNIQUE ("英作文ID", "表示順"),
    CONSTRAINT "FK_ENG_英作文画像_作文" FOREIGN KEY ("英作文ID")
        REFERENCES public."ENG_英作文情報" ("英作文ID") ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_eng_essay_image_essay
    ON public."ENG_英作文画像情報" ("英作文ID", "表示順");

COMMENT ON TABLE public."ENG_英作文画像情報" IS
    '英作文の設問・答案の画像（1 行 = 1 枚）。実体は DB の外に置き、ここには場所と OCR の結果を持つ';
COMMENT ON COLUMN public."ENG_英作文画像情報"."画像区分" IS
    'question = 設問画像 / answer = 答案画像。利用者が 1 枚ずつ指定する（2.0 と同じ）';
COMMENT ON COLUMN public."ENG_英作文画像情報"."認識テキスト" IS
    'この画像から読み取った文字。設問か答案かで、作文情報の 設問文／作文本文 にまとめる';
COMMENT ON COLUMN public."ENG_英作文画像情報"."認識信頼度" IS
    'OCR の信頼度（0〜100）。画面に「信頼度 N%」と出す（区分ごとの平均も出す）';
