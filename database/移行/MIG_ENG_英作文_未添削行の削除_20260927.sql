-- ============================================================================
-- Study 2.1  英作文 2.0 移行の**未添削の行**を削除する
-- ----------------------------------------------------------------------------
-- 2.0 の `批改結果JSON` には「OCR までで AI 添削はしていない」行が混ざっている
-- （status = OCR_ONLY / OCR_COMPLETED / OCR_PENDING、得点 NULL）。移行の初版はそれも
-- `状態コード='SUCCEEDED'` の添削履歴として入れてしまったため、ここで取り除く
-- （未添削として扱うのが事実に合う）。
--
-- 残すもの: 同じ移行でも status='AI_GRADED'（21 件）と、status が無くても**得点がある**行（3 件）。
-- 実行例:
--   psql -h 192.168.0.100 -p 54320 -U postgres -d study21 -v ON_ERROR_STOP=1 \
--        -f database/移行/MIG_ENG_英作文_未添削行の削除_20260927.sql
-- 何度流しても同じ（2 回目以降は 0 件）。
-- ============================================================================

DELETE FROM public."ENG_AI添削履歴情報" g
 USING public."ENG_英作文情報" e
 WHERE e."英作文ID" = g."英作文ID"
   AND e."登録元コード" = 'MIGRATION'
   AND g."総合得点" IS NULL
   AND coalesce(g."添削結果JSON" ->> 'status', '') IN ('OCR_ONLY', 'OCR_COMPLETED', 'OCR_PENDING');

DO $$
DECLARE rest integer; removed integer;
BEGIN
    SELECT count(*) INTO rest FROM public."ENG_AI添削履歴情報" g
      JOIN public."ENG_英作文情報" e ON e."英作文ID" = g."英作文ID"
     WHERE e."登録元コード" = 'MIGRATION';
    RAISE NOTICE '2.0 移行の添削行は % 件になりました（未添削の行を削除）', rest;
END $$;