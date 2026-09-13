-- ============================================================================
-- Study 2.1  学習日報のデータ移行
--   2.0 study2.TRN_学習日報情報      -> study21.TRN_学習日報情報      (24 件)
--   2.0 study2.TRN_学習日報授業情報   -> study21.TRN_学習日報授業情報   (133 件)
--   2.0 study2.TRN_学習日報科目情報   -> study21.TRN_学習日報科目情報   (6 件)
-- ----------------------------------------------------------------------------
-- 事前条件:
--   1. database/学習日報/TBL_TRN_学習日報.sql
--   2. 移行元: study2 の同名 3 テーブル（dblink で読む）
--
-- 実行例:
--   psql -U postgres -d study21 -v ON_ERROR_STOP=1 -f MIG_TRN_学習日報_20260912.sql
--
-- 冪等性: 旧 ID（日報番号・日報授業番号・日報科目番号）を維持して
--   ON CONFLICT DO NOTHING で投入するため、何度実行しても結果は変わらない。
--
-- 持ち主の対応（2.0 の ユーザーID -> 2.1 の アカウントID）:
--   2.0 の日報は 'ljz' の 1 人分だけ。'ljz' は「劉競澤 = Liu Jingze」の頭文字で、
--   2.1 のアカウント 2（ricky.jingze@gmail.com / STUDENT）に当たる
--   （2.0 の MST_ユーザー情報 には表示名が日本語で「柳澤直輝」と入っていたが、
--     ユーザーID とメールから同一人物と判断した。相違があれば下の対応表を直す）。
--   対応が取れない ID は アカウントID を NULL にし、旧ユーザーID だけ残す。
--
-- 値の補正（2.1 の CHECK に合わせる）:
--   * 掌握度・4 観点の評価は 1〜5。範囲外があればエラーで止める（実データは 3〜4）。
--   * 時限は 1〜10（実データは 1〜7）。教科名が空の行はエラーで止める。
--   * 同じ人の同じ日が重複していたらエラーで止める（一意制約違反を事前に検出）。
-- ============================================================================

BEGIN;

-- 2.0 の ユーザーID -> 2.1 の アカウントID
CREATE TEMP TABLE migr_report_owner ("旧ユーザーID" TEXT PRIMARY KEY, "アカウントID" BIGINT) ON COMMIT DROP;
INSERT INTO migr_report_owner VALUES ('ljz', 2);

CREATE TEMP TABLE migr_report ON COMMIT DROP AS
SELECT * FROM dblink('dbname=study2',
    $q$ SELECT "日報番号","ユーザーID","対象日","全体の振り返り","学習集中度","理解度","学習量",
                "学習態度","今夜宿題内容","備考","登録日時","更新日時"
          FROM public."TRN_学習日報情報" $q$)
    AS t("日報番号" BIGINT, "ユーザーID" VARCHAR(50), "対象日" DATE, "全体の振り返り" TEXT,
         "学習集中度" SMALLINT, "理解度" SMALLINT, "学習量" SMALLINT, "学習態度" SMALLINT,
         "今夜宿題内容" TEXT, "備考" TEXT, "登録日時" TIMESTAMP, "更新日時" TIMESTAMP);

CREATE TEMP TABLE migr_lesson ON COMMIT DROP AS
SELECT * FROM dblink('dbname=study2',
    $q$ SELECT "日報授業番号","日報番号","時限","教科名","授業内容","掌握度","ノート記録フラグ",
                "登録日時","更新日時"
          FROM public."TRN_学習日報授業情報" $q$)
    AS t("日報授業番号" BIGINT, "日報番号" BIGINT, "時限" INTEGER, "教科名" VARCHAR(50),
         "授業内容" TEXT, "掌握度" SMALLINT, "ノート記録フラグ" BOOLEAN,
         "登録日時" TIMESTAMP, "更新日時" TIMESTAMP);

CREATE TEMP TABLE migr_subject ON COMMIT DROP AS
SELECT * FROM dblink('dbname=study2',
    $q$ SELECT "日報科目番号","日報番号","表示順","教科名","学習内容","登録日時","更新日時"
          FROM public."TRN_学習日報科目情報" $q$)
    AS t("日報科目番号" BIGINT, "日報番号" BIGINT, "表示順" INTEGER, "教科名" VARCHAR(50),
         "学習内容" TEXT, "登録日時" TIMESTAMP, "更新日時" TIMESTAMP);

-- ---- 事前検証 -----------------------------------------------------------------
DO $$
DECLARE
    report_count BIGINT; lesson_count BIGINT; subject_count BIGINT; unmapped BIGINT;
BEGIN
    SELECT COUNT(*) INTO report_count FROM migr_report;
    SELECT COUNT(*) INTO lesson_count FROM migr_lesson;
    SELECT COUNT(*) INTO subject_count FROM migr_subject;
    RAISE NOTICE '2.0 の学習日報: 日報 % 件 / 授業 % 件 / 科目 % 件 を移行します',
        report_count, lesson_count, subject_count;

    IF EXISTS (SELECT 1 FROM migr_report WHERE "対象日" IS NULL) THEN
        RAISE EXCEPTION '2.0 daily report contains a row without 対象日';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_report
                WHERE GREATEST(COALESCE("学習集中度",0), COALESCE("理解度",0),
                               COALESCE("学習量",0), COALESCE("学習態度",0)) > 5
                   OR LEAST(COALESCE("学習集中度",1), COALESCE("理解度",1),
                            COALESCE("学習量",1), COALESCE("学習態度",1)) < 1) THEN
        RAISE EXCEPTION '2.0 daily report contains a rating outside 1..5';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_lesson
                WHERE "時限" IS NULL OR "時限" < 1 OR "時限" > 10
                   OR BTRIM(COALESCE("教科名",'')) = '') THEN
        RAISE EXCEPTION '2.0 lesson contains an invalid 時限 or a blank 教科名';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_lesson WHERE "掌握度" IS NOT NULL AND ("掌握度" < 1 OR "掌握度" > 5)) THEN
        RAISE EXCEPTION '2.0 lesson contains 掌握度 outside 1..5';
    END IF;
    -- 2 件目以降の日報に紐づく授業が無い（孤立）行は無いか
    IF EXISTS (SELECT 1 FROM migr_lesson l
                WHERE NOT EXISTS (SELECT 1 FROM migr_report r WHERE r."日報番号" = l."日報番号")) THEN
        RAISE EXCEPTION '2.0 lesson references a missing daily report';
    END IF;
    -- 同じ人の同じ日の重複
    IF EXISTS (SELECT 1 FROM migr_report r JOIN migr_report_owner o ON o."旧ユーザーID" = r."ユーザーID"
                GROUP BY o."アカウントID", r."対象日" HAVING COUNT(*) > 1) THEN
        RAISE EXCEPTION '2.0 daily report contains duplicate (account, date)';
    END IF;
    -- 対応表に無いユーザーID（NULL のまま移行されるので知らせる）
    SELECT COUNT(DISTINCT r."ユーザーID") INTO unmapped
      FROM migr_report r LEFT JOIN migr_report_owner o ON o."旧ユーザーID" = r."ユーザーID"
     WHERE o."旧ユーザーID" IS NULL;
    IF unmapped > 0 THEN
        RAISE NOTICE '対応表に無いユーザーID が % 件あります（アカウントID は NULL で移行）', unmapped;
    END IF;
END $$;

-- ---- 日報 ---------------------------------------------------------------------
INSERT INTO public."TRN_学習日報情報" (
    "日報ID", "アカウントID", "旧ユーザーID", "対象日", "全体の振り返り",
    "学習集中度", "理解度", "学習量", "学習態度", "今夜宿題内容", "備考",
    "登録元コード", "登録日時", "更新元コード", "更新日時")
SELECT r."日報番号", o."アカウントID", r."ユーザーID", r."対象日", r."全体の振り返り",
       r."学習集中度", r."理解度", r."学習量", r."学習態度", r."今夜宿題内容", LEFT(r."備考", 200),
       'MIGRATION', r."登録日時", 'MIGRATION', r."更新日時"
  FROM migr_report r
  LEFT JOIN migr_report_owner o ON o."旧ユーザーID" = r."ユーザーID"
 ON CONFLICT ("日報ID") DO NOTHING;

-- ---- 授業 ---------------------------------------------------------------------
INSERT INTO public."TRN_学習日報授業情報" (
    "日報授業ID", "日報ID", "時限", "教科名", "授業内容", "掌握度", "ノート記録フラグ",
    "登録元コード", "登録日時", "更新元コード", "更新日時")
SELECT l."日報授業番号", l."日報番号", l."時限", l."教科名", l."授業内容", l."掌握度",
       COALESCE(l."ノート記録フラグ", FALSE),
       'MIGRATION', l."登録日時", 'MIGRATION', l."更新日時"
  FROM migr_lesson l
  JOIN public."TRN_学習日報情報" r ON r."日報ID" = l."日報番号"
 ON CONFLICT ("日報授業ID") DO NOTHING;

-- ---- 科目 ---------------------------------------------------------------------
INSERT INTO public."TRN_学習日報科目情報" (
    "日報科目ID", "日報ID", "表示順", "教科名", "学習内容",
    "登録元コード", "登録日時", "更新元コード", "更新日時")
SELECT s."日報科目番号", s."日報番号", s."表示順", s."教科名", s."学習内容",
       'MIGRATION', s."登録日時", 'MIGRATION', s."更新日時"
  FROM migr_subject s
  JOIN public."TRN_学習日報情報" r ON r."日報ID" = s."日報番号"
 ON CONFLICT ("日報科目ID") DO NOTHING;

-- ---- シーケンスを最大値へ -------------------------------------------------------
SELECT setval(pg_get_serial_sequence('public."TRN_学習日報情報"', '日報ID'),
              GREATEST((SELECT COALESCE(MAX("日報ID"), 0) FROM public."TRN_学習日報情報"), 1), true);
SELECT setval(pg_get_serial_sequence('public."TRN_学習日報授業情報"', '日報授業ID'),
              GREATEST((SELECT COALESCE(MAX("日報授業ID"), 0) FROM public."TRN_学習日報授業情報"), 1), true);
SELECT setval(pg_get_serial_sequence('public."TRN_学習日報科目情報"', '日報科目ID'),
              GREATEST((SELECT COALESCE(MAX("日報科目ID"), 0) FROM public."TRN_学習日報科目情報"), 1), true);

-- ---- 件数の検証 ---------------------------------------------------------------
DO $$
DECLARE
    src BIGINT; dst BIGINT;
BEGIN
    SELECT COUNT(*) INTO src FROM migr_report;
    SELECT COUNT(*) INTO dst FROM public."TRN_学習日報情報" t JOIN migr_report r ON r."日報番号" = t."日報ID";
    IF src <> dst THEN RAISE EXCEPTION 'Daily report count mismatch: source %, migrated %', src, dst; END IF;

    SELECT COUNT(*) INTO src FROM migr_lesson;
    SELECT COUNT(*) INTO dst FROM public."TRN_学習日報授業情報" t JOIN migr_lesson l ON l."日報授業番号" = t."日報授業ID";
    IF src <> dst THEN RAISE EXCEPTION 'Lesson count mismatch: source %, migrated %', src, dst; END IF;

    SELECT COUNT(*) INTO src FROM migr_subject;
    SELECT COUNT(*) INTO dst FROM public."TRN_学習日報科目情報" t JOIN migr_subject s ON s."日報科目番号" = t."日報科目ID";
    IF src <> dst THEN RAISE EXCEPTION 'Subject count mismatch: source %, migrated %', src, dst; END IF;

    RAISE NOTICE '学習日報の移行完了: 日報 % / 授業 % / 科目 %', 
        (SELECT COUNT(*) FROM public."TRN_学習日報情報"),
        (SELECT COUNT(*) FROM public."TRN_学習日報授業情報"),
        (SELECT COUNT(*) FROM public."TRN_学習日報科目情報");
END $$;

SELECT "旧ユーザーID", "アカウントID", COUNT(*) AS "日報数", MIN("対象日") AS "最古", MAX("対象日") AS "最新"
  FROM public."TRN_学習日報情報" GROUP BY 1, 2;

COMMIT;
