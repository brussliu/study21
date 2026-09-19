-- MIGRATION-ID: bat-execution-source-execution-id
-- ============================================================================
-- 移行: BAT_バッチ実行履歴情報 に「元実行ID」を追加する（再起動の復旧のやり直し関係）
-- 対象DB: study21 (PostgreSQL)
-- 冪等: ADD COLUMN IF NOT EXISTS / CREATE UNIQUE INDEX IF NOT EXISTS
-- ----------------------------------------------------------------------------
-- なぜ必要か:
--   再起動の復旧で「実行中の実行」をやり直すとき、
--   ・旧実行を閉じる（FAILED）
--   ・やり直しの実行（QUEUED）を作る
--   を**同じトランザクション**で行う。そのとき、どの実行のやり直しかを DB に残し、
--   **元実行ID に部分一意索引**を張ることで、
--   ・2 つの復旧が同時に走ってもやり直しは 1 つだけ（メモリのロックに依存しない）
--   ・やり直しを作った直後に落ちても、次の起動が「やり直し済み」と分かる
--   ことを保証する。
-- 既存行は NULL（通常の実行）。部分索引なので NULL は対象外（通常の実行に影響しない）。
-- 実行順序: database/バッチ/TBL_BAT_バッチ実行履歴情報.sql の後
-- ============================================================================

ALTER TABLE public."BAT_バッチ実行履歴情報"
    ADD COLUMN IF NOT EXISTS "元実行ID" BIGINT NULL;

COMMENT ON COLUMN public."BAT_バッチ実行履歴情報"."元実行ID" IS
    '再起動の復旧でやり直しを作ったときの元の実行ID（同じ元実行から 1 つだけ）';

-- ----------------------------------------------------------------------------
-- 一意索引を作る前に、既存データの重複を明示的に確認する。
-- 重複があった場合、履歴は**削除も変更もしない**（黙って直さない）。
-- 移行を中止して件数を報告するので、利用者が内容を確認してから再実行する。
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    duplicates BIGINT;
BEGIN
    SELECT COUNT(*) INTO duplicates
      FROM (SELECT "元実行ID"
              FROM public."BAT_バッチ実行履歴情報"
             WHERE "元実行ID" IS NOT NULL
             GROUP BY "元実行ID"
            HAVING COUNT(*) > 1) d;
    IF duplicates > 0 THEN
        RAISE EXCEPTION '移行を中止しました: BAT_バッチ実行履歴情報 の 元実行ID に重複が % 件あります。'
                        '実行履歴は削除も変更もしていません。重複を確認してから再実行してください。',
                        duplicates;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS "UQ_BAT_実行履歴_元実行ID"
    ON public."BAT_バッチ実行履歴情報" ("元実行ID")
    WHERE "元実行ID" IS NOT NULL;
