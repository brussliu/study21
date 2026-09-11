-- ============================================================================
-- Study 2.1  資料詳細情報  DDL
-- テーブル: DOC_資料詳細情報
-- ----------------------------------------------------------------------------
-- 2.0 (study2) の DOC_資料詳細情報 と同一構造（変更なし）。
--   ※PK (資料番号, 枝番号) の先頭列が 資料番号 のため、資料番号での
--     検索は PK 索引でカバーされ、追加索引は不要。
--   ※DOC_資料情報 への DB レベル FK は 2.0 でも存在しなかったため
--     作らない（アプリ層で保証。実データに孤児行なしを検証済み）。
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."DOC_資料詳細情報" (
    "資料番号"        VARCHAR(20)  NOT NULL,
    "枝番号"          INTEGER      NOT NULL,
    "拡張子"          VARCHAR(10)  NULL,
    "元ファイル名称"  VARCHAR(200) NULL,
    "縮略ファイル500" TEXT         NULL,
    "縮略ファイル200" TEXT         NULL,
    "縮略ファイル50"  TEXT         NULL,
    "コメント"        TEXT         NULL,
    "パス"            VARCHAR(200) NULL,
    "ファイル名称"    VARCHAR(200) NULL,
    "登録ID"          VARCHAR(20)  NULL,
    "更新ID"          VARCHAR(20)  NULL,
    "登録日時"        TIMESTAMP    NULL,
    "更新日時"        TIMESTAMP    NULL,
    CONSTRAINT "DOC_資料詳細情報_pkey" PRIMARY KEY ("資料番号", "枝番号")
);
