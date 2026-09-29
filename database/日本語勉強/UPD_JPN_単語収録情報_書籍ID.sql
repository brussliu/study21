-- ============================================================================
-- Study 2.1  日本語勉強 DDL 追加（最終仕様）
-- 変更: JPN_単語収録情報 に「書籍ID」を足す
-- ----------------------------------------------------------------------------
-- 2.0 は 収録の `書籍` が**文字列**だったので、書籍そのものに ID・並び・状態を持てなかった。
-- `TBL_JPN_書籍情報.sql` で書籍マスタを新設し、収録から参照する。
--
-- 方針:
--   ・`書籍`（2.0 由来の表示名）は**消さない**。既存の画面と移行データの突き合わせに使う。
--     新しい画面は `書籍ID` を使い、`書籍` は同じ値を写しで持つ。
--   ・既存 9,886 行は `UPD_JPN_書籍情報_20260922.sql` で埋める
--     （書籍マスタを起こしてから、名前で引き当てる）。
--   ・移行スクリプト（MIG_JPN_*）は先に走っているので、ここでは触らない。
--
-- 冪等: `ADD COLUMN IF NOT EXISTS` / `DROP CONSTRAINT IF EXISTS` を使う。
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

ALTER TABLE public."JPN_単語収録情報"
    ADD COLUMN IF NOT EXISTS "書籍ID" BIGINT NULL;

ALTER TABLE public."JPN_単語収録情報"
    DROP CONSTRAINT IF EXISTS "FK_JPN_収録_書籍";
ALTER TABLE public."JPN_単語収録情報"
    ADD CONSTRAINT "FK_JPN_収録_書籍"
        FOREIGN KEY ("書籍ID")
        REFERENCES public."JPN_書籍情報" ("書籍ID") ON DELETE RESTRICT;

-- 書籍の中での分類の並びを引く（未設定の行は対象外なので部分索引にする）
CREATE INDEX IF NOT EXISTS idx_jpn_collection_book
    ON public."JPN_単語収録情報" ("書籍ID", "分類")
    WHERE "書籍ID" IS NOT NULL;

COMMENT ON COLUMN public."JPN_単語収録情報"."書籍ID" IS
    'JPN_書籍情報 への参照。書籍 列（2.0 由来の表示名）は突き合わせのため残している';
