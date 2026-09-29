-- ============================================================================
-- Study 2.1  英語穴埋め問題 / 問題画像情報  DDL
-- テーブル: ENC_問題画像情報 （★2.1 新設。2.0 の STY_英語穴埋め問題画像 に相当）
-- ----------------------------------------------------------------------------
-- 1 行 = 1 枚の画像（教材の紙面）。**実体は DB の外**（相対パス + ファイル名）。
-- 2.0 は `画像データ BYTEA` で DB に直接持っていたが、2.1 は英作文
-- （ENG_英作文画像情報）と同じくファイルに置き、ここには場所だけを持つ。
--
-- 2.0 の列名との対応: 原ファイル名 → 原本ファイル名。
-- 保存ファイル名・相対パス・ファイルサイズ は 2.1 で足した列（2.0 には無い）。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順: TBL_ENC_問題セット情報.sql の後 → 本ファイル → TBL_ENC_問題情報.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."ENC_問題画像情報" (
    "問題画像ID"         BIGSERIAL    NOT NULL,
    "問題セットID"       BIGINT       NOT NULL,
    -- 表示順（1 から。セットの中で一意）
    "表示順"             INTEGER      NOT NULL DEFAULT 1,
    -- 上げられた元のファイル名（画面に出す）
    "原本ファイル名"     VARCHAR(300) NULL,
    -- 保存したファイル名（UUID + 拡張子。元の名前は使わない）
    "保存ファイル名"     VARCHAR(200) NOT NULL,
    -- 保存先の相対パス（例 'english-cloze/2/202609'。ストレージ根は設定で決まる）
    "相対パス"           VARCHAR(500) NOT NULL,
    "MIMEタイプ"         VARCHAR(120) NOT NULL,
    "ファイルサイズ"     BIGINT       NOT NULL DEFAULT 0,

    -- ---- 2.1 の共通規約（子表は 登録者 と 登録日時 の 2 列だけ） ----
    "登録者アカウントID" BIGINT       NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ENC_問題画像情報_pkey" PRIMARY KEY ("問題画像ID"),
    CONSTRAINT "CK_ENC_問題画像_順" CHECK ("表示順" >= 1),
    CONSTRAINT "CK_ENC_問題画像_サイズ" CHECK ("ファイルサイズ" >= 0),
    -- 同じセットの中で表示順は重ならない
    CONSTRAINT "UK_ENC_問題画像_順" UNIQUE ("問題セットID", "表示順"),
    CONSTRAINT "FK_ENC_問題画像_セット" FOREIGN KEY ("問題セットID")
        REFERENCES public."ENC_問題セット情報" ("問題セットID") ON DELETE CASCADE
);

-- セットの紙面を表示順に引く
CREATE INDEX IF NOT EXISTS idx_enc_image_set
    ON public."ENC_問題画像情報" ("問題セットID", "表示順");

COMMENT ON TABLE public."ENC_問題画像情報" IS
    '教材の紙面の画像（1 行 = 1 枚）。実体は DB の外に置き、ここには場所だけを持つ（2.0 は BYTEA 直持ち）';
COMMENT ON COLUMN public."ENC_問題画像情報"."表示順" IS
    '1 から。画面はこの順に紙面を並べる。セットの中で一意（UK_ENC_問題画像_順）';
COMMENT ON COLUMN public."ENC_問題画像情報"."原本ファイル名" IS
    '上げられた元のファイル名（2.0 の 原ファイル名）。表示用で、保存には使わない';
COMMENT ON COLUMN public."ENC_問題画像情報"."相対パス" IS
    '保存先の相対パス（例 ''english-cloze/2/202609''）。根は study21.english-cloze.storage-root。パスの遍歴（..）は弾く';
COMMENT ON COLUMN public."ENC_問題画像情報"."ファイルサイズ" IS
    '実体のバイト数。2.0 は DB に入れていたので列が無かった';
