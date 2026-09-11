-- ============================================================================
-- Study 2.1 資料物理ファイル切替
--
-- 前提:
--   1. MIG_DOC_資料管理_20260831.sql を実行済み
--   2. 2.0 の document root 配下を、2.1 の
--      families/{family_student_id}/ 配下へ相対構造を保ってコピー・照合済み
--
-- 必須引数: -v family_student_id=<STUDENTアカウントID>
-- 旧2.0は DB のファイル名称が「資料番号.ext」の場合、実ファイル名を
-- 「資料番号_枝番号.ext」に補正していたため、その規則もここで確定させる。
-- ============================================================================

\if :{?family_student_id}
\else
\echo 'ERROR: -v family_student_id=<STUDENT account id> is required'
\quit
\endif

BEGIN;

CREATE TEMP TABLE migr_doc_switch_family ON COMMIT DROP AS
SELECT "アカウントID" AS family_student_id
  FROM public."ACC_アカウント"
 WHERE "アカウントID" = :'family_student_id'::BIGINT
   AND "アカウント種別" = 'STUDENT';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM migr_doc_switch_family) THEN
        RAISE EXCEPTION 'family_student_id must reference a STUDENT account';
    END IF;
END $$;

UPDATE public."DOC_資料詳細情報" detail
   SET "ファイル名称" = CASE
           WHEN lower(detail."ファイル名称") = lower(detail."資料番号") || '.' || lower(detail."拡張子")
             THEN detail."資料番号" || '_' || detail."枝番号" || '.' || lower(detail."拡張子")
           ELSE detail."ファイル名称"
       END,
       "パス" = 'families/' || :'family_student_id' || '/' ||
           rtrim(
               regexp_replace(translate(detail."パス", chr(92), '/'), '^/?(file/)?doc/?', ''),
               '/'
           ),
       "更新ID" = 'migr_doc_file',
       "更新日時" = CURRENT_TIMESTAMP
  FROM public."DOC_資料情報" document
 WHERE document."資料番号" = detail."資料番号"
   AND document."家族学生ID" = :'family_student_id'::BIGINT
   AND translate(detail."パス", chr(92), '/') NOT LIKE 'families/%';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM public."DOC_資料詳細情報" detail
          JOIN public."DOC_資料情報" document USING ("資料番号")
         WHERE document."家族学生ID" = (SELECT family_student_id FROM migr_doc_switch_family)
           AND detail."パス" NOT LIKE 'families/' ||
               (SELECT family_student_id FROM migr_doc_switch_family) || '/%'
    ) THEN
        RAISE EXCEPTION 'some document paths were not switched';
    END IF;
END $$;

COMMIT;

SELECT count(*) AS switched_file_count
  FROM public."DOC_資料詳細情報" detail
  JOIN public."DOC_資料情報" document USING ("資料番号")
 WHERE document."家族学生ID" = :'family_student_id'::BIGINT
   AND detail."パス" LIKE 'families/' || :'family_student_id' || '/%';
