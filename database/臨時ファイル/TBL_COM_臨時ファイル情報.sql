-- ============================================================================
-- Study 2.1  臨時ファイル情報  DDL
-- テーブル: COM_臨時ファイル情報
-- ----------------------------------------------------------------------------
-- 2.0 (study2) の COM_臨時ファイル情報 を 2.1 の所有権モデル（家族学生ID）へ
-- 再設計したもの。臨時ファイル管理（アップロード・一覧検索・画像編集・DL）の
-- 中核テーブル。
-- 変更点（2.0 との差分）:
--   ★追加  "家族学生ID"        BIGINT NOT NULL, FK → ACC_アカウント (RESTRICT)
--                              （生徒本人と保護者が共有する所有キー。2.0 は所有者なし）
--   ★変更  一時ファイルSEQ → 一時ファイルID（BIGSERIAL サロゲートPK）
--   ★変更  登録ID/更新ID を VARCHAR(20) に統一（2.0 は VARCHAR(50)）
--   ★変更  登録日時/更新日時 を NOT NULL + DEFAULT CURRENT_TIMESTAMP に統一
--   ★追加  CK_COM_臨時_サイズ: ファイルサイズ >= 0（負値の混入防止）
--   ★追加  idx_com_tempfile_family_created 索引（家族別・登録日時降順）
--   ★追加  idx_com_tempfile_family_mime 索引（家族別・タイプ絞り込み用）
--   ※維持  元ファイル名称 / ファイル名称 / 拡張子 / MIME_TYPE / ファイルサイズ /
--          縮略ファイル500/200/50 / コメント / パス
--          （縮略ファイルは 2.0 の base64 データ保存を踏襲）
-- 運用メモ:
--   - タイプ（画像 / その他）は MIME_TYPE の接頭辞 'image/' で判定（列は追加しない）
--   - 期間検索（直近◯日 / 3ヵ月以前）は 登録日時 を基準に判定
--   - 画像編集の「保存」は当該行の UPDATE、「別名保存」は新規行の INSERT で実現
--   - 物理ファイルの保存先は パス + ファイル名称、縮小画像は 縮略ファイル* 列に保持
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."COM_臨時ファイル情報" (
    "一時ファイルID"   BIGSERIAL    NOT NULL,
    -- 家族共有の所有キー。生徒本人と、その生徒に紐づく保護者だけが同じ値を使用する。
    "家族学生ID"       BIGINT       NOT NULL,
    -- アップロード時の元ファイル名（例: IMG_0001.jpg）。画像編集の別名保存にも使用。
    "元ファイル名称"   VARCHAR(255) NULL,
    -- ストレージ上の保存ファイル名（重複回避のため元名から採番し直したもの）
    "ファイル名称"     VARCHAR(200) NULL,
    "拡張子"           VARCHAR(20)  NULL,
    "MIME_TYPE"        VARCHAR(120) NULL,
    "ファイルサイズ"   BIGINT       NULL,
    -- 物理ファイルの保存先ディレクトリ（サーバー上の相対/絶対パス）
    "パス"             VARCHAR(255) NULL,
    -- 一覧・詳細用の縮小画像（base64 データ）。画像以外は NULL。2.0 の列名を踏襲。
    "縮略ファイル500"  TEXT         NULL,
    "縮略ファイル200"  TEXT         NULL,
    "縮略ファイル50"   TEXT         NULL,
    "コメント"         TEXT         NULL,
    "登録ID"           VARCHAR(20)  NULL,
    "更新ID"           VARCHAR(20)  NULL,
    "登録日時"         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "COM_臨時ファイル情報_pkey" PRIMARY KEY ("一時ファイルID"),
    -- 家族（生徒本人・保護者）に紐づく。アカウント削除は RESTRICT で保護。
    CONSTRAINT "FK_COM_臨時_家族学生" FOREIGN KEY ("家族学生ID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    -- ファイルサイズは負値を許容しない
    CONSTRAINT "CK_COM_臨時_サイズ" CHECK ("ファイルサイズ" IS NULL OR "ファイルサイズ" >= 0)
);

-- 一覧取得・期間（直近◯日 / 3ヵ月以前）絞り込み用
CREATE INDEX IF NOT EXISTS idx_com_tempfile_family_created
    ON public."COM_臨時ファイル情報" ("家族学生ID", "登録日時" DESC);

-- タイプ（画像 / その他）絞り込み用（MIME_TYPE の接頭辞判定）
CREATE INDEX IF NOT EXISTS idx_com_tempfile_family_mime
    ON public."COM_臨時ファイル情報" ("家族学生ID", "MIME_TYPE");
