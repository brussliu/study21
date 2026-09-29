-- ============================================================================
-- Study 2.1  日本語勉強 データ補完
-- 内容: 収録情報が無い単語を、既存の書籍「N1~N5日本語単語」へ収録し直す
-- ----------------------------------------------------------------------------
-- 背景:
--   単語情報管理画面の旧い登録処理は `JPN_単語情報` だけを書き、
--   `JPN_単語収録情報` を書いていなかった。そのため 345 語が
--   「書籍・分類が空」のまま一覧に出ていた（アプリ側は修正済み）。
--   ここでは既に登録済みの語へ収録情報を補う。
--
-- 割り当て（ユーザー指示）:
--   書籍 = 'N1~N5日本語単語'（`JPN_書籍情報` の ACTIVE な 1 冊）
--   分類 = 単語ID の昇順に 100 語ずつ Unit001, Unit002, ...
--   SEQ  = 各 Unit の中の連番（1〜100）
--
-- 冪等: 収録が 1 件も無い語だけを対象にする（再実行しても二重にならない）
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

BEGIN;

-- 補完の対象（この時点の件数を確認する）
SELECT COUNT(*) AS target_words
FROM public."JPN_単語情報" AS w
WHERE NOT EXISTS (
    SELECT 1 FROM public."JPN_単語収録情報" AS c WHERE c."単語ID" = w."単語ID"
);

WITH target AS (
    SELECT
        w."単語ID",
        w."見出し語",
        w."読み",
        w."品詞",
        ROW_NUMBER() OVER (ORDER BY w."単語ID") AS rn
    FROM public."JPN_単語情報" AS w
    WHERE NOT EXISTS (
        SELECT 1 FROM public."JPN_単語収録情報" AS c WHERE c."単語ID" = w."単語ID"
    )
)
INSERT INTO public."JPN_単語収録情報" (
    "単語ID", "書籍ID", "レベル", "書籍", "分類", "単語SEQ",
    "掲載見出し語", "掲載読み", "掲載品詞",
    "出典JSON", "状態コード", "登録元コード"
)
SELECT
    t."単語ID",
    b."書籍ID",
    'N1-N5',
    b."書籍名",
    'Unit' || LPAD((((t.rn - 1) / 100) + 1)::text, 3, '0'),
    (((t.rn - 1) % 100) + 1)::int,
    t."見出し語",
    NULLIF(BTRIM(COALESCE(t."読み", '')), ''),
    NULLIF(BTRIM(COALESCE(t."品詞", '')), ''),
    '{}'::jsonb,
    'ACTIVE',
    'APP'
FROM target AS t
CROSS JOIN public."JPN_書籍情報" AS b
WHERE b."状態コード" = 'ACTIVE'
  AND b."書籍名" = 'N1~N5日本語単語';

-- 書籍マスタの統計を数え直す（分類数・収録語数）
UPDATE public."JPN_書籍情報" AS b
SET "分類数" = COALESCE(s.unit_count, 0),
    "収録語数" = COALESCE(s.word_count, 0),
    "更新日時" = CURRENT_TIMESTAMP
FROM (
    SELECT
        c."書籍ID",
        COUNT(DISTINCT c."分類") AS unit_count,
        COUNT(*) AS word_count
    FROM public."JPN_単語収録情報" AS c
    WHERE c."状態コード" = 'ACTIVE'
    GROUP BY c."書籍ID"
) AS s
WHERE s."書籍ID" = b."書籍ID";

-- 結果確認
SELECT b."書籍名", b."分類数", b."収録語数"
FROM public."JPN_書籍情報" AS b ORDER BY b."書籍ID";

SELECT c."書籍", c."分類", COUNT(*) AS words, MIN(c."単語SEQ") AS min_seq, MAX(c."単語SEQ") AS max_seq
FROM public."JPN_単語収録情報" AS c
GROUP BY c."書籍", c."分類" ORDER BY c."分類";

SELECT COUNT(*) AS still_empty
FROM public."JPN_単語情報" AS w
WHERE NOT EXISTS (
    SELECT 1 FROM public."JPN_単語収録情報" AS c WHERE c."単語ID" = w."単語ID"
);

COMMIT;
