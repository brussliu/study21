-- ============================================================================
-- Study 2.1  資料管理データ移行  2.0(study2) → 2.1(study21)
-- ----------------------------------------------------------------------------
-- 前提: あらかじめ以下を実行済みであること
--   1. TBL_DOC_フォルダ情報.sql
--   2. TBL_DOC_資料情報.sql
--   3. TBL_DOC_資料詳細情報.sql
--   4. 移行先家族の生徒アカウントが存在すること
--
-- 必須引数: -v family_student_id=<移行先の STUDENT アカウントID>
-- 2.0 の DOC テーブルには所有ユーザー列がないため、1回の実行で1家族へ割り当てる。
--
-- 実行方法（postgres コンテナ内。同一インスタンス内のため dblink は
-- ローカル接続＝trust 認証でパスワード不要）:
--   docker exec -i postgresql18 psql -U postgres -d study21 \
--     -v ON_ERROR_STOP=1 -v family_student_id=<STUDENTアカウントID> \
--     < MIG_DOC_資料管理_20260831.sql
--
-- 内容:
--   (1) study2 の DOC_資料情報 / DOC_資料詳細情報 を study21 へ取込
--       （old / bak テーブルは対象外）
--   (2) 既存の 大分類→中分類→小分類→細分類 の組合せから
--       DOC_フォルダ情報 の 4 階層ツリーを自動生成
--   (3) 各資料の フォルダID を最深一致フォルダに設定
--
-- 冪等性: 取込は ON CONFLICT DO NOTHING、フォルダ生成も一意索引で
--   重複回避、フォルダID 設定は NULL の行のみ更新（再実行可能）。
--   処理全体は BEGIN ... COMMIT で原子的に実行する。
-- ============================================================================

\if :{?family_student_id}
\else
\echo 'ERROR: -v family_student_id=<STUDENT account id> is required'
\quit
\endif

BEGIN;

CREATE TEMP TABLE migr_doc_target_family ON COMMIT DROP AS
SELECT "アカウントID" AS family_student_id
  FROM public."ACC_アカウント"
 WHERE "アカウントID" = :'family_student_id'::BIGINT
   AND "アカウント種別" = 'STUDENT';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM migr_doc_target_family) THEN
        RAISE EXCEPTION 'family_student_id must reference a STUDENT account';
    END IF;
END $$;

CREATE EXTENSION IF NOT EXISTS dblink;

-- 資料番号は 2.1 全体で一意。別家族の既存資料へ詳細行を誤結合しないよう、
-- 取込前に衝突を検出して処理全体を停止する。
CREATE TEMP TABLE migr_doc_legacy_numbers ON COMMIT DROP AS
SELECT t."資料番号"
FROM dblink('dbname=study2', 'SELECT "資料番号" FROM public."DOC_資料情報"')
     AS t("資料番号" VARCHAR(20));

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM public."DOC_資料情報" target
          JOIN migr_doc_legacy_numbers legacy USING ("資料番号")
         WHERE target."家族学生ID" <> (SELECT family_student_id FROM migr_doc_target_family)
    ) THEN
        RAISE EXCEPTION 'legacy document number conflicts with another family';
    END IF;
END $$;

-- ----------------------------------------------------------------------------
-- (1) データ取込: DOC_資料情報（件数は実行時点の2.0データに従う）
-- ----------------------------------------------------------------------------
INSERT INTO public."DOC_資料情報" (
    "資料番号", "家族学生ID", "ステータス", "有効期限",
    "大分類", "中分類", "小分類", "細分類", "コメント",
    "登録ID", "更新ID", "登録日時", "更新日時"
)
SELECT
    t."資料番号", :'family_student_id'::BIGINT, t."ステータス", t."有効期限",
    t."大分類", t."中分類", t."小分類", t."細分類", t."コメント",
    t."登録ID", t."更新ID", t."登録日時", t."更新日時"
FROM dblink(
    'dbname=study2',
    'SELECT "資料番号", "ステータス", "有効期限",
            "大分類", "中分類", "小分類", "細分類", "コメント",
            "登録ID", "更新ID", "登録日時", "更新日時"
     FROM public."DOC_資料情報" ORDER BY "資料番号"'
) AS t(
    "資料番号" VARCHAR(20), "ステータス" VARCHAR(20), "有効期限" VARCHAR(10),
    "大分類" VARCHAR(50), "中分類" VARCHAR(50), "小分類" VARCHAR(50),
    "細分類" VARCHAR(50), "コメント" VARCHAR(100),
    "登録ID" VARCHAR(20), "更新ID" VARCHAR(20),
    "登録日時" TIMESTAMP, "更新日時" TIMESTAMP
)
ON CONFLICT ("資料番号") DO NOTHING;

-- ----------------------------------------------------------------------------
-- (1) データ取込: DOC_資料詳細情報（縮略イメージは TEXT のまま）
-- ----------------------------------------------------------------------------
INSERT INTO public."DOC_資料詳細情報" (
    "資料番号", "枝番号", "拡張子", "元ファイル名称",
    "縮略ファイル500", "縮略ファイル200", "縮略ファイル50", "コメント",
    "パス", "ファイル名称", "登録ID", "更新ID", "登録日時", "更新日時"
)
SELECT
    t."資料番号", t."枝番号", t."拡張子", t."元ファイル名称",
    t."縮略ファイル500", t."縮略ファイル200", t."縮略ファイル50", t."コメント",
    t."パス", t."ファイル名称", t."登録ID", t."更新ID", t."登録日時", t."更新日時"
FROM dblink(
    'dbname=study2',
    'SELECT "資料番号", "枝番号", "拡張子", "元ファイル名称",
            "縮略ファイル500", "縮略ファイル200", "縮略ファイル50", "コメント",
            "パス", "ファイル名称", "登録ID", "更新ID", "登録日時", "更新日時"
     FROM public."DOC_資料詳細情報" ORDER BY "資料番号", "枝番号"'
) AS t(
    "資料番号" VARCHAR(20), "枝番号" INTEGER, "拡張子" VARCHAR(10),
    "元ファイル名称" VARCHAR(200),
    "縮略ファイル500" TEXT, "縮略ファイル200" TEXT, "縮略ファイル50" TEXT,
    "コメント" TEXT, "パス" VARCHAR(200), "ファイル名称" VARCHAR(200),
    "登録ID" VARCHAR(20), "更新ID" VARCHAR(20),
    "登録日時" TIMESTAMP, "更新日時" TIMESTAMP
)
ON CONFLICT ("資料番号", "枝番号") DO NOTHING;

-- ----------------------------------------------------------------------------
-- (2) フォルダツリー生成: 第1階層（大分類）
--     表示順は名称先頭の "01." 等の数値から採番（無ければ 0）
-- ----------------------------------------------------------------------------
INSERT INTO public."DOC_フォルダ情報" ("家族学生ID", "親フォルダID", "フォルダ名称", "表示順", "登録ID")
SELECT
    :'family_student_id'::BIGINT,
    NULL,
    m."大分類",
    COALESCE((regexp_match(m."大分類", '^\s*(\d+)'))[1]::INTEGER, 0),
    'migr_doc'
FROM (
    SELECT DISTINCT NULLIF("大分類", '') AS "大分類"
    FROM public."DOC_資料情報"
    WHERE "家族学生ID" = :'family_student_id'::BIGINT
) m
WHERE m."大分類" IS NOT NULL
ON CONFLICT ("家族学生ID", COALESCE("親フォルダID", 0), "フォルダ名称") DO NOTHING;

-- ----------------------------------------------------------------------------
-- (2) フォルダツリー生成: 第2階層（中分類）
-- ----------------------------------------------------------------------------
INSERT INTO public."DOC_フォルダ情報" ("家族学生ID", "親フォルダID", "フォルダ名称", "表示順", "登録ID")
SELECT
    :'family_student_id'::BIGINT,
    p1."フォルダID",
    m."中分類",
    COALESCE((regexp_match(m."中分類", '^\s*(\d+)'))[1]::INTEGER, 0),
    'migr_doc'
FROM (
    SELECT DISTINCT NULLIF("大分類", '') AS "大分類", NULLIF("中分類", '') AS "中分類"
    FROM public."DOC_資料情報"
    WHERE "家族学生ID" = :'family_student_id'::BIGINT
) m
JOIN public."DOC_フォルダ情報" p1
  ON p1."家族学生ID" = :'family_student_id'::BIGINT
 AND p1."親フォルダID" IS NULL AND p1."フォルダ名称" = m."大分類"
WHERE m."中分類" IS NOT NULL
ON CONFLICT ("家族学生ID", COALESCE("親フォルダID", 0), "フォルダ名称") DO NOTHING;

-- ----------------------------------------------------------------------------
-- (2) フォルダツリー生成: 第3階層（小分類）
-- ----------------------------------------------------------------------------
INSERT INTO public."DOC_フォルダ情報" ("家族学生ID", "親フォルダID", "フォルダ名称", "表示順", "登録ID")
SELECT
    :'family_student_id'::BIGINT,
    p2."フォルダID",
    m."小分類",
    COALESCE((regexp_match(m."小分類", '^\s*(\d+)'))[1]::INTEGER, 0),
    'migr_doc'
FROM (
    SELECT DISTINCT NULLIF("大分類", '') AS "大分類", NULLIF("中分類", '') AS "中分類",
           NULLIF("小分類", '') AS "小分類"
    FROM public."DOC_資料情報"
    WHERE "家族学生ID" = :'family_student_id'::BIGINT
) m
JOIN public."DOC_フォルダ情報" p1
  ON p1."家族学生ID" = :'family_student_id'::BIGINT
 AND p1."親フォルダID" IS NULL AND p1."フォルダ名称" = m."大分類"
JOIN public."DOC_フォルダ情報" p2
  ON p2."家族学生ID" = :'family_student_id'::BIGINT
 AND p2."親フォルダID" = p1."フォルダID" AND p2."フォルダ名称" = m."中分類"
WHERE m."小分類" IS NOT NULL
ON CONFLICT ("家族学生ID", COALESCE("親フォルダID", 0), "フォルダ名称") DO NOTHING;

-- ----------------------------------------------------------------------------
-- (2) フォルダツリー生成: 第4階層（細分類）
-- ----------------------------------------------------------------------------
INSERT INTO public."DOC_フォルダ情報" ("家族学生ID", "親フォルダID", "フォルダ名称", "表示順", "登録ID")
SELECT
    :'family_student_id'::BIGINT,
    p3."フォルダID",
    m."細分類",
    COALESCE((regexp_match(m."細分類", '^\s*(\d+)'))[1]::INTEGER, 0),
    'migr_doc'
FROM (
    SELECT DISTINCT NULLIF("大分類", '') AS "大分類", NULLIF("中分類", '') AS "中分類",
           NULLIF("小分類", '') AS "小分類", NULLIF("細分類", '') AS "細分類"
    FROM public."DOC_資料情報"
    WHERE "家族学生ID" = :'family_student_id'::BIGINT
) m
JOIN public."DOC_フォルダ情報" p1
  ON p1."家族学生ID" = :'family_student_id'::BIGINT
 AND p1."親フォルダID" IS NULL AND p1."フォルダ名称" = m."大分類"
JOIN public."DOC_フォルダ情報" p2
  ON p2."家族学生ID" = :'family_student_id'::BIGINT
 AND p2."親フォルダID" = p1."フォルダID" AND p2."フォルダ名称" = m."中分類"
JOIN public."DOC_フォルダ情報" p3
  ON p3."家族学生ID" = :'family_student_id'::BIGINT
 AND p3."親フォルダID" = p2."フォルダID" AND p3."フォルダ名称" = m."小分類"
WHERE m."細分類" IS NOT NULL
ON CONFLICT ("家族学生ID", COALESCE("親フォルダID", 0), "フォルダ名称") DO NOTHING;

-- ----------------------------------------------------------------------------
-- (3) 資料への フォルダID 設定（分類チェーンと最深一致するフォルダ。
--     途中の階層が欠けている資料は存在する階層までで一致させる）
-- ----------------------------------------------------------------------------
WITH doc AS (
    SELECT "資料番号",
           NULLIF("大分類", '') AS l1, NULLIF("中分類", '') AS l2,
           NULLIF("小分類", '') AS l3, NULLIF("細分類", '') AS l4
    FROM public."DOC_資料情報"
    WHERE "家族学生ID" = :'family_student_id'::BIGINT
),
pick AS (
    SELECT
        d."資料番号",
        COALESCE(f4."フォルダID", f3."フォルダID", f2."フォルダID", f1."フォルダID") AS "フォルダID"
    FROM doc d
JOIN public."DOC_フォルダ情報" f1
      ON f1."家族学生ID" = :'family_student_id'::BIGINT
     AND f1."親フォルダID" IS NULL AND f1."フォルダ名称" = d.l1
    LEFT JOIN public."DOC_フォルダ情報" f2
      ON f2."家族学生ID" = :'family_student_id'::BIGINT
     AND f2."親フォルダID" = f1."フォルダID" AND f2."フォルダ名称" = d.l2
    LEFT JOIN public."DOC_フォルダ情報" f3
      ON f3."家族学生ID" = :'family_student_id'::BIGINT
     AND f3."親フォルダID" = f2."フォルダID" AND f3."フォルダ名称" = d.l3
    LEFT JOIN public."DOC_フォルダ情報" f4
      ON f4."家族学生ID" = :'family_student_id'::BIGINT
     AND f4."親フォルダID" = f3."フォルダID" AND f4."フォルダ名称" = d.l4
)
UPDATE public."DOC_資料情報" m
SET "フォルダID" = p."フォルダID",
    "更新ID" = 'migr_doc',
    "更新日時" = CURRENT_TIMESTAMP
FROM pick p
WHERE m."資料番号" = p."資料番号"
  AND m."家族学生ID" = :'family_student_id'::BIGINT
  AND m."フォルダID" IS NULL;

-- ----------------------------------------------------------------------------
-- (4) 結果確認
-- ----------------------------------------------------------------------------
SELECT 'DOC_資料情報' AS 対象, count(*) AS 件数 FROM public."DOC_資料情報" WHERE "家族学生ID"=:'family_student_id'::BIGINT
UNION ALL
SELECT 'DOC_資料詳細情報', count(*) FROM public."DOC_資料詳細情報" d JOIN public."DOC_資料情報" m USING ("資料番号") WHERE m."家族学生ID"=:'family_student_id'::BIGINT
UNION ALL
SELECT 'DOC_フォルダ情報', count(*) FROM public."DOC_フォルダ情報" WHERE "家族学生ID"=:'family_student_id'::BIGINT
UNION ALL
SELECT '未分類(フォルダID NULL)', count(*) FROM public."DOC_資料情報" WHERE "家族学生ID"=:'family_student_id'::BIGINT AND "フォルダID" IS NULL;

COMMIT;
