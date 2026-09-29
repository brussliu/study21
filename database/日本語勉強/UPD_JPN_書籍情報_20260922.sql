-- ============================================================================
-- Study 2.1  日本語勉強 データ更新（最終仕様）
-- 内容: 既存の収録から 書籍マスタ（JPN_書籍情報）を起こし、
--       JPN_単語収録情報.書籍ID を埋める
-- ----------------------------------------------------------------------------
-- 2.0 の実データは `STY_日本語単語収録情報.書籍 = '01.N1~N5日本語単語'` の 1 冊だけ
-- （2026-09-22 実測: 収録 9,886 行 / 分類 588）。
-- ここではその文字列を **先頭の `.` で分けて** コードと名前にする。
--   '01.N1~N5日本語単語' → 書籍コード '01' / 書籍名 'N1~N5日本語単語'
--   `.` が無い値          → 書籍コード 'LEGACY' / 書籍名 そのまま（推測で分けない）
--
-- 冪等:
--   ・書籍は `旧書籍コード` がまだ無いものだけ入れる（`WHERE NOT EXISTS`）。
--     `ON CONFLICT` でも増えないが、INSERT を試すたびに BIGSERIAL の採番が進むので、
--     再実行で番号が飛ばないよう**そもそも INSERT しない**形にしている。
--   ・収録は `書籍ID IS NULL` の行だけを埋める（2 回目は 0 行）
--   ・`書籍` の値と `書籍名` を突き合わせて引く（名前に改行・空白の揺れがあっても拾う）
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

BEGIN;

-- 1. 収録に出てくる書籍を、コードと名前に分けて起こす
INSERT INTO public."JPN_書籍情報" (
    "旧書籍コード", "書籍コード", "書籍名", "レベル範囲", "状態コード", "登録元コード"
)
SELECT
    src."書籍",
    -- 先頭の '.' より前をコードにする。無いときは LEGACY
    CASE
        WHEN POSITION('.' IN src."書籍") > 0
            THEN LEFT(src."書籍", POSITION('.' IN src."書籍") - 1)
        ELSE 'LEGACY'
    END,
    CASE
        WHEN POSITION('.' IN src."書籍") > 0
            THEN SUBSTRING(src."書籍" FROM POSITION('.' IN src."書籍") + 1)
        ELSE src."書籍"
    END,
    -- レベル範囲は名前から分かるときだけ入れる（例: N1~N5 → N1-N5）。分からなければ NULL
    CASE
        WHEN src."書籍" ~ 'N1[~-]N5' THEN 'N1-N5'
        WHEN src."書籍" ~ 'N5[~-]N1' THEN 'N1-N5'
        ELSE NULL
    END,
    'ACTIVE',
    'MIGRATION'
FROM (
    SELECT DISTINCT "書籍"
    FROM public."JPN_単語収録情報"
    WHERE NULLIF(BTRIM("書籍"), '') IS NOT NULL
) AS src
WHERE NOT EXISTS (
    SELECT 1 FROM public."JPN_書籍情報" AS b WHERE b."旧書籍コード" = src."書籍"
);

-- 2. 収録に書籍ID を入れる（まだ入っていない行だけ）
UPDATE public."JPN_単語収録情報" AS c
SET "書籍ID" = b."書籍ID",
    "更新日時" = CURRENT_TIMESTAMP
FROM public."JPN_書籍情報" AS b
WHERE c."書籍ID" IS NULL
  AND (b."旧書籍コード" = c."書籍" OR b."書籍名" = c."書籍");

-- 3. 統計（分類数・収録語数）を数え直す
UPDATE public."JPN_書籍情報" AS b
SET "分類数" = COALESCE(agg."分類数", 0),
    "収録語数" = COALESCE(agg."収録語数", 0),
    "更新日時" = CURRENT_TIMESTAMP
FROM (
    SELECT
        "書籍ID",
        COUNT(DISTINCT "分類") AS "分類数",
        COUNT(*) AS "収録語数"
    FROM public."JPN_単語収録情報"
    WHERE "書籍ID" IS NOT NULL
    GROUP BY "書籍ID"
) AS agg
WHERE b."書籍ID" = agg."書籍ID";

-- 収録が 1 件も無い書籍（これから作る本）は 0 のまま
UPDATE public."JPN_書籍情報"
SET "分類数" = 0, "収録語数" = 0
WHERE "書籍ID" NOT IN (
    SELECT DISTINCT "書籍ID" FROM public."JPN_単語収録情報" WHERE "書籍ID" IS NOT NULL
);

-- 4. 確認（書籍ID が埋まらなかった収録が残っていないか）
DO $$
DECLARE
    missing INTEGER;
BEGIN
    SELECT COUNT(*) INTO missing
    FROM public."JPN_単語収録情報"
    WHERE "書籍ID" IS NULL;

    IF missing > 0 THEN
        RAISE EXCEPTION '書籍ID が未設定の収録が % 行あります（書籍名の対応を確認してください）', missing;
    END IF;
END $$;

COMMIT;

-- 結果の確認用
SELECT "書籍ID", "書籍コード", "書籍名", "レベル範囲", "分類数", "収録語数", "登録元コード"
FROM public."JPN_書籍情報"
ORDER BY "書籍ID";
