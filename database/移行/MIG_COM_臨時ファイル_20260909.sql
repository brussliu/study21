-- ============================================================================
-- Study 2.1  臨時ファイルデータ移行  2.0(study2) → 2.1(study21)
-- ----------------------------------------------------------------------------
-- 前提: あらかじめ以下を実行済みであること
--   1. TBL_COM_臨時ファイル情報.sql（2.1 の臨時ファイルテーブル）
--   2. 移行先家族の STUDENT アカウントが存在すること
--
-- 必須引数: -v family_student_id=<移行先の STUDENT アカウントID>
-- 2.0 の COM_臨時ファイル情報 には所有ユーザー列がないため、
-- 1回の実行で1家族へ割り当てる。
--
-- 実行方法（postgres コンテナ内。同一インスタンス内のため dblink は
-- ローカル接続＝trust 認証でパスワード不要）:
--   docker exec -i postgresql18 psql -U postgres -d study21 \
--     -v ON_ERROR_STOP=1 -v family_student_id=<STUDENTアカウントID> \
--     < MIG_COM_臨時ファイル_20260909.sql
--
-- 本スクリプトが行うこと（DB 側のみ。物理ファイルは別途コピー）:
--   (1) study2 の COM_臨時ファイル情報 を study21 へ取込
--       - "家族学生ID" に引数の STUDENT アカウントID を設定
--       - "パス" を 2.0 の file/TEMPFILE/{yyyyMM}/{yyyyMMdd}/ から
--         2.1 の families/{家族学生ID}/{yyyyMM}/{yyyyMMdd}/ へ書き換え
--       - "ファイル名称"（temp_{SEQ}.{拡張子}）は維持（UUID 化しない）
--       - "登録日時"/"更新日時" は元の値を維持（期間検索のため）
--       - "登録ID"/"更新ID" は VARCHAR(20) へ LEFT 20 で切り詰め
--
-- 冪等性: "ファイル名称"（2.0 では temp_{SEQ}.{ext} で全体一意）を
--   キーに既存行をスキップする（再実行可能）。
-- ============================================================================

\if :{?family_student_id}
\else
\echo 'ERROR: -v family_student_id=<STUDENT account id> is required'
\quit
\endif

BEGIN;

-- 移行先が STUDENT アカウントであることを検証
CREATE TEMP TABLE migr_tf_target_family ON COMMIT DROP AS
SELECT "アカウントID" AS family_student_id
  FROM public."ACC_アカウント"
 WHERE "アカウントID" = :'family_student_id'::BIGINT
   AND "アカウント種別" = 'STUDENT';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM migr_tf_target_family) THEN
        RAISE EXCEPTION 'family_student_id must reference a STUDENT account';
    END IF;
END $$;

CREATE EXTENSION IF NOT EXISTS dblink;

-- ----------------------------------------------------------------------------
-- (1) データ取込: COM_臨時ファイル情報
--     パス書換: file/TEMPFILE/{yyyyMM}/{yyyyMMdd}/ → families/{家族学生ID}/{yyyyMM}/{yyyyMMdd}/
-- ----------------------------------------------------------------------------
INSERT INTO public."COM_臨時ファイル情報" (
    "家族学生ID", "元ファイル名称", "ファイル名称", "拡張子", "MIME_TYPE",
    "ファイルサイズ", "パス", "縮略ファイル500", "縮略ファイル200", "縮略ファイル50",
    "コメント", "登録ID", "更新ID", "登録日時", "更新日時"
)
SELECT
    :'family_student_id'::BIGINT,
    t."元ファイル名称",
    COALESCE(t."ファイル名称",
             'temp_' || t."一時ファイルSEQ" || COALESCE('.' || NULLIF(t."拡張子", ''), '')),
    t."拡張子", t."MIME_TYPE", t."ファイルサイズ",
    'families/' || :'family_student_id'::BIGINT || '/'
        || regexp_replace(COALESCE(t."パス", 'file/TEMPFILE/'), '^/?file/TEMPFILE/', '', 'i'),
    t."縮略ファイル500", t."縮略ファイル200", t."縮略ファイル50", t."コメント",
    LEFT(t."登録ID", 20), LEFT(t."更新ID", 20),
    t."登録日時", t."更新日時"
FROM dblink(
    'dbname=study2',
    'SELECT "一時ファイルSEQ", "元ファイル名称", "ファイル名称", "拡張子", "MIME_TYPE",
            "ファイルサイズ", "縮略ファイル500", "縮略ファイル200", "縮略ファイル50",
            "コメント", "パス", "登録ID", "更新ID", "登録日時", "更新日時"
     FROM public."COM_臨時ファイル情報" ORDER BY "一時ファイルSEQ"'
) AS t(
    "一時ファイルSEQ" BIGINT, "元ファイル名称" VARCHAR(255), "ファイル名称" VARCHAR(200),
    "拡張子" VARCHAR(20), "MIME_TYPE" VARCHAR(120), "ファイルサイズ" BIGINT,
    "縮略ファイル500" TEXT, "縮略ファイル200" TEXT, "縮略ファイル50" TEXT,
    "コメント" TEXT, "パス" VARCHAR(255), "登録ID" VARCHAR(50), "更新ID" VARCHAR(50),
    "登録日時" TIMESTAMP, "更新日時" TIMESTAMP
)
WHERE NOT EXISTS (
    SELECT 1
      FROM public."COM_臨時ファイル情報" e
     WHERE e."家族学生ID" = :'family_student_id'::BIGINT
       AND e."ファイル名称" = t."ファイル名称"
);

-- ----------------------------------------------------------------------------
-- (2) 結果確認
-- ----------------------------------------------------------------------------
SELECT 'study21.COM_臨時ファイル情報(対象家族)' AS 対象,
       count(*) AS 件数
  FROM public."COM_臨時ファイル情報"
 WHERE "家族学生ID" = :'family_student_id'::BIGINT;

SELECT 'パス書換サンプル' AS 対象, "一時ファイルID", "元ファイル名称", "パス", "ファイル名称"
  FROM public."COM_臨時ファイル情報"
 WHERE "家族学生ID" = :'family_student_id'::BIGINT
 ORDER BY "一時ファイルID"
 LIMIT 5;

COMMIT;
