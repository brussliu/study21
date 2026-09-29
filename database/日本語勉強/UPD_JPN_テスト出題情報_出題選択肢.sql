-- ============================================================================
-- Study 2.1  日本語勉強 DDL 変更
-- 内容: JPN_テスト出題情報 に「そのテストで実際に見せた選択肢」を持たせる
-- ----------------------------------------------------------------------------
-- なぜ（設計: 日本語勉強_再設計案_中文.md）:
--   選択肢は「4 択の答え」ではなく**プール**（正解 1 ＋ 誤答 4〜6）になる。
--   テストを作るときに「正解 1 ＋ 誤答からランダム 3」を選んで並び順も混ぜるので、
--   **その出題で何を見せたか**を出題行に固定して残す必要がある。
--   理由:
--     ・同じテストを開き直しても同じ選択肢（毎回変わらない）
--     ・プールを AI で取り直しても、受験済みの記録が壊れない
--     ・表示と判定が必ず一致する
--
--   形: [{"choiceId": 123, "value": "あい", "reading": "あい", "correct": true}, ...]
--   A・B（プールを持たない種別）も同じ形で書く（判定の経路を 1 本にするため）。
--
-- 冪等: 列は IF NOT EXISTS、制約は DROP IF EXISTS → ADD なので何度流してもよい
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

BEGIN;

ALTER TABLE public."JPN_テスト出題情報"
    ADD COLUMN IF NOT EXISTS "出題選択肢JSON" JSONB NOT NULL DEFAULT '[]';

ALTER TABLE public."JPN_テスト出題情報"
    DROP CONSTRAINT IF EXISTS "CK_JPN_テスト出題_出題選択肢JSON";

ALTER TABLE public."JPN_テスト出題情報"
    ADD CONSTRAINT "CK_JPN_テスト出題_出題選択肢JSON"
        CHECK (jsonb_typeof("出題選択肢JSON") = 'array');

COMMENT ON COLUMN public."JPN_テスト出題情報"."出題選択肢JSON" IS
    'この出題で実際に見せた選択肢（提示順）。[{"choiceId","value","reading","correct"}]。'
    'プール（JPN_単語問題選択肢情報）からテスト作成時に選んだ 4 件を固定して残す';

-- 確認
SELECT column_name, data_type, is_nullable, column_default
  FROM information_schema.columns
 WHERE table_schema = 'public'
   AND table_name = 'JPN_テスト出題情報'
   AND column_name = '出題選択肢JSON';

COMMIT;
