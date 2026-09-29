-- ============================================================================
-- Study 2.1  日本語勉強 DDL 追加（最終仕様）
-- 変更: JPN_テスト出題情報 に「回答履歴JSON」を足す
-- ----------------------------------------------------------------------------
-- 2.0 は テスト → 出題（1 語）→ 課題（段階）→ 回答履歴（1 試行 1 行）の 4 階層だった。
-- 2.1 は課題テーブルを持たないので、**1 問ごとの対錯は出題の行の中に配列で残す**。
--
-- この列でできること:
--   ・何問目を、いつ、何回答えて、正解だったか（試行ごとの履歴）
--   ・テストを中断して開き直したときに「どこまで進んだか」
--     → `出題状態='PENDING'` のうち **`出題順` がいちばん小さい行**が次の 1 問
--       （この列は「済んだぶんの記録」で、進行位置は `出題状態` が持つ）
--
-- なぜ子テーブルを作らないか:
--   ・2.0 の回答履歴（1 試行 1 行）は集計にしか使っておらず、画面にも出していなかった。
--     配列なら 1 問 1 行のままで、教材 9,847 語を全問解いても行数が増えない。
--   ・「1 語のテストの様子」を読むときは出題 1 行で完結する（JOIN が要らない）。
--   → 設計文書 §2「回答履歴を子テーブルにしない理由」を参照。
--
-- 中身（要素のキー。アプリ側で作る）:
--   { attempt: 1, answer: "べんきょう", correct: true, elapsedMs: 4200,
--     audioPlays: 2, at: "2026-09-22T10:00:00" }
--   ・`answer` … 選んだ選択肢の値、または入力した文字列
--   ・`correct` … その試行の判定（最終判定は行の `最終判定` に持つ）
--   ・`at` … 回答した時刻。`attempt` は 1 から
--
-- 冪等: `ADD COLUMN IF NOT EXISTS` / `DROP CONSTRAINT IF EXISTS` を使う。
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

ALTER TABLE public."JPN_テスト出題情報"
    ADD COLUMN IF NOT EXISTS "回答履歴JSON" JSONB NOT NULL DEFAULT '[]';

-- 中身は必ず配列（オブジェクトや数値を入れさせない）
ALTER TABLE public."JPN_テスト出題情報"
    DROP CONSTRAINT IF EXISTS "CK_JPN_テスト出題_回答履歴JSON";
ALTER TABLE public."JPN_テスト出題情報"
    ADD CONSTRAINT "CK_JPN_テスト出題_回答履歴JSON"
        CHECK (jsonb_typeof("回答履歴JSON") = 'array');

-- 回答履歴の中を引く（「この語を間違えたテスト」を探す等）
CREATE INDEX IF NOT EXISTS idx_jpn_test_question_history
    ON public."JPN_テスト出題情報"
    USING GIN ("回答履歴JSON");

COMMENT ON COLUMN public."JPN_テスト出題情報"."回答履歴JSON" IS
    '1 試行ごとの記録の配列（attempt/answer/correct/elapsedMs/audioPlays/at）。進行位置は 出題状態=''PENDING'' の先頭で見る';
