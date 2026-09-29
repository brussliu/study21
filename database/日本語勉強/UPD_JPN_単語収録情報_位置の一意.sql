-- ============================================================================
-- Study 2.1  日本語勉強 DDL 変更
-- 内容: JPN_単語収録情報 に「同じ教材・同じ分類・同じ SEQ は 1 つだけ」の
--       部分 UNIQUE 索引を足す
-- ----------------------------------------------------------------------------
-- なぜ:
--   収録の位置（書籍・分類・単語SEQ）は教材上の「何番目か」なので、
--   ACTIVE の行では重複してはいけない。いまは普通の索引しか無く、
--   画面が送った SEQ が既存と重なっても DB が止められなかった
--   （アプリ側で「埋まっていれば後ろに詰める」ようにしたが、DB でも守る）。
--
-- 部分索引にする理由:
--   ・INACTIVE（使わない）行は同じ位置を持ってよい（履歴として残すため）
--   ・書籍ID が NULL の行は対象外（教材マスタに紐づかない行。UNIQUE では
--     NULL 同士は重複扱いにならないので、意図を明示するため述語にも書く）
--
-- 前提: 対象行に重複が無いこと（下のチェックで 0 件を確認してから作る）
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

BEGIN;

-- 作成前チェック: 重複があれば 0 件にならない（そのときはこのスクリプトを止めて
-- どちらを残すか決めてから流す）
SELECT COUNT(*) AS duplicate_positions
FROM (
    SELECT "書籍ID", "分類", "単語SEQ"
      FROM public."JPN_単語収録情報"
     WHERE "状態コード" = 'ACTIVE' AND "書籍ID" IS NOT NULL
     GROUP BY "書籍ID", "分類", "単語SEQ"
    HAVING COUNT(*) > 1
) AS d;

CREATE UNIQUE INDEX IF NOT EXISTS uq_jpn_collect_position
    ON public."JPN_単語収録情報" ("書籍ID", "分類", "単語SEQ")
    WHERE "状態コード" = 'ACTIVE' AND "書籍ID" IS NOT NULL;

COMMENT ON INDEX public.uq_jpn_collect_position IS
    '同じ教材・同じ分類（Unit）の中で 単語SEQ を一意にする（ACTIVE のみ。教材の並び順の重複を防ぐ）';

-- 作成後確認: 索引ができているか
SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public' AND tablename = 'JPN_単語収録情報'
  AND indexname = 'uq_jpn_collect_position';

COMMIT;
