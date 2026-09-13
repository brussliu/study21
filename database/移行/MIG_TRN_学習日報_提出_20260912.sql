-- ============================================================================
-- Study 2.0 -> 2.1  学習日報の「提出」状態の補正
-- 対象: study21.public.TRN_学習日報情報
-- ----------------------------------------------------------------------------
-- 背景:
--   2.0 の日報には「提出」の概念が無く、「日報の行がある＝提出した」という扱いだった。
--   2.1 で 記載あり（DRAFT）と 提出済（SUBMITTED）を分けたため、既存の行を
--   提出済に寄せる。
--
-- 方針:
--   * 対象は「提出日時が NULL の行」＝ 追加前からある行（2.0 からの移行分と、
--     2.1 の画面で作った分）。提出日時は 更新日時 → 登録日時 の順で補完する。
--   * 冪等: 2 回実行しても結果は変わらない（1 回目で 提出日時 が入るため対象から外れる）。
--   * 日報が無い日（未提出）は行が無いので何もしない。
--
-- 事前条件: database/学習日報/TBL_TRN_学習日報.sql（提出状態コード・提出日時）適用済み
-- 実行例:   psql -U postgres -d study21 -v ON_ERROR_STOP=1 -f MIG_TRN_学習日報_提出_20260912.sql
-- ============================================================================

BEGIN;

DO $$
DECLARE
    target_rows BIGINT;
BEGIN
    SELECT COUNT(*) INTO target_rows
      FROM public."TRN_学習日報情報"
     WHERE "提出日時" IS NULL;

    RAISE NOTICE '提出済に寄せる日報: % 件', target_rows;
END $$;

UPDATE public."TRN_学習日報情報"
   SET "提出状態コード" = 'SUBMITTED',
       "提出日時" = COALESCE("更新日時", "登録日時", CURRENT_TIMESTAMP),
       "更新元コード" = COALESCE("更新元コード", 'MIGRATION')
 WHERE "提出日時" IS NULL;

DO $$
DECLARE
    remaining BIGINT;
BEGIN
    SELECT COUNT(*) INTO remaining
      FROM public."TRN_学習日報情報"
     WHERE "提出日時" IS NULL;

    IF remaining <> 0 THEN
        RAISE EXCEPTION '提出日時が NULL の日報が % 件残っています', remaining;
    END IF;
END $$;

COMMIT;

\echo '--- 提出状態の内訳 ---'
SELECT "提出状態コード", COUNT(*) AS "件数", MIN("対象日") AS "最古", MAX("対象日") AS "最新"
  FROM public."TRN_学習日報情報" GROUP BY 1 ORDER BY 2 DESC;
