-- ============================================================================
-- Study 2.0 -> 2.1  Web閲覧履歴 移行
-- 移行元: study2.public.TRN_ブラウザ閲覧履歴情報
-- 移行先: study21.public.NET_Web閲覧履歴情報
-- ----------------------------------------------------------------------------
-- 事前条件:
--   1. database/Web閲覧履歴/TBL_NET_Web閲覧履歴情報.sql
--   2. 移行元・移行先DBが同一PostgreSQLインスタンスに存在すること（dblink で接続）
--
-- 実行例:
--   psql -U postgres -d study21 -v ON_ERROR_STOP=1 -f MIG_NET_Web閲覧履歴_20260912.sql
--
-- 持ち主の対応（日報・TODO と同じ判断）:
--   'ljz'（劉競澤） -> アカウント 2 / 'liu'（劉季＝保護者） -> アカウント 1
--   対応表に無い ユーザーID（'student01' など）は 利用者アカウントID = NULL とし、
--   旧ユーザーID だけ残す（行は捨てない）。
--
-- 値の対応:
--   アクティブフラグ / 履歴同期元フラグ … 2.0 の CHAR(1) をそのまま（NULL は '0'）
--   端末ID     -> 端末識別子（ブラウザ拡張の識別子。NET_端末コントロール情報 とは別物）
--   登録ID/更新ID は実行者を特定できないため、アカウントID は NULL で 登録元コード='MIGRATION'
--
-- 冪等性:
--   旧 履歴番号 を維持し ON CONFLICT DO NOTHING（再実行しても増えない）。
--   移行元は稼働中で件数が増えるため、件数検証は「この実行で読んだ件数」と比較する。
-- ============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS dblink;

CREATE TEMP TABLE migr_web_owner ("旧ユーザーID" TEXT PRIMARY KEY, "アカウントID" BIGINT) ON COMMIT DROP;
INSERT INTO migr_web_owner VALUES ('ljz', 2), ('liu', 1);

CREATE TEMP TABLE migr_web_history ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study2',
       $remote$
       SELECT "履歴番号", COALESCE("ユーザーID", ''), COALESCE("端末ID", ''),
              COALESCE("端末名", ''), COALESCE("ブラウザ種別", ''),
              COALESCE("ブラウザプロファイルID", ''), "タブID", "ウィンドウID",
              COALESCE("セッションID", ''), COALESCE("イベント種別", ''),
              "URL", "ドメイン", "ページタイトル", "リファラーURL", "ファビコンURL",
              COALESCE("ページ遷移種別", ''), COALESCE("アクティブフラグ", '0'),
              COALESCE("履歴同期元フラグ", '0'), "アクセス日時", "滞在秒数", "閲覧回数",
              "JSON詳細", "登録日時", "更新日時"
         FROM public."TRN_ブラウザ閲覧履歴情報"
       $remote$
       ) AS remote (
           "履歴番号" BIGINT,
           "ユーザーID" TEXT,
           "端末ID" TEXT,
           "端末名" TEXT,
           "ブラウザ種別" TEXT,
           "ブラウザプロファイルID" TEXT,
           "タブID" BIGINT,
           "ウィンドウID" BIGINT,
           "セッションID" TEXT,
           "イベント種別" TEXT,
           "URL" TEXT,
           "ドメイン" TEXT,
           "ページタイトル" TEXT,
           "リファラーURL" TEXT,
           "ファビコンURL" TEXT,
           "ページ遷移種別" TEXT,
           "アクティブフラグ" TEXT,
           "履歴同期元フラグ" TEXT,
           "アクセス日時" TIMESTAMP,
           "滞在秒数" INTEGER,
           "閲覧回数" INTEGER,
           "JSON詳細" TEXT,
           "登録日時" TIMESTAMP,
           "更新日時" TIMESTAMP
       );

DO $$
DECLARE
    source_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO source_count FROM migr_web_history;
    RAISE NOTICE '2.0 の Web閲覧履歴 % 件を移行します', source_count;

    IF EXISTS (SELECT 1 FROM migr_web_history WHERE "アクセス日時" IS NULL) THEN
        RAISE EXCEPTION '2.0 browser history contains a row without アクセス日時';
    END IF;

    IF EXISTS (SELECT 1 FROM migr_web_history WHERE BTRIM("イベント種別") = '') THEN
        RAISE EXCEPTION '2.0 browser history contains a blank イベント種別';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_web_history
         WHERE "アクティブフラグ" NOT IN ('0', '1') OR "履歴同期元フラグ" NOT IN ('0', '1')
    ) THEN
        RAISE EXCEPTION '2.0 browser history contains an unsupported flag value';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_web_history WHERE LENGTH("端末ID") > 200 OR LENGTH("端末名") > 200
            OR LENGTH("ブラウザプロファイルID") > 200 OR LENGTH("セッションID") > 200
            OR LENGTH("ドメイン") > 500 OR LENGTH("ユーザーID") > 100
            OR LENGTH("イベント種別") > 50 OR LENGTH("ブラウザ種別") > 50
            OR LENGTH("ページ遷移種別") > 100
    ) THEN
        RAISE EXCEPTION '2.0 browser history value exceeds the 2.1 column length';
    END IF;

    -- 既に同じ ID があって内容が違う場合は中断（冪等な再実行だけを許容する）
    IF EXISTS (
        SELECT 1 FROM migr_web_history source
          JOIN public."NET_Web閲覧履歴情報" target ON target."閲覧履歴ID" = source."履歴番号"
         WHERE target."アクセス日時" <> source."アクセス日時"
    ) THEN
        RAISE EXCEPTION 'A target browser history ID already exists with different data';
    END IF;
END $$;

INSERT INTO public."NET_Web閲覧履歴情報" (
    "閲覧履歴ID", "利用者アカウントID", "旧ユーザーID", "端末識別子", "端末名称",
    "ブラウザ種別", "ブラウザプロファイルID", "タブID", "ウィンドウID", "セッションID",
    "イベント種別", "URL", "ドメイン", "ページタイトル", "リファラーURL", "ファビコンURL",
    "ページ遷移種別", "アクティブフラグ", "履歴同期元フラグ", "アクセス日時",
    "滞在秒数", "閲覧回数", "JSON詳細",
    "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時"
)
SELECT source."履歴番号",
       owner."アカウントID",
       NULLIF(BTRIM(source."ユーザーID"), ''),
       NULLIF(BTRIM(source."端末ID"), ''),
       NULLIF(BTRIM(source."端末名"), ''),
       NULLIF(BTRIM(source."ブラウザ種別"), ''),
       NULLIF(BTRIM(source."ブラウザプロファイルID"), ''),
       source."タブID",
       source."ウィンドウID",
       NULLIF(BTRIM(source."セッションID"), ''),
       source."イベント種別",
       source."URL",
       NULLIF(BTRIM(source."ドメイン"), ''),
       source."ページタイトル",
       source."リファラーURL",
       source."ファビコンURL",
       NULLIF(BTRIM(source."ページ遷移種別"), ''),
       source."アクティブフラグ",
       source."履歴同期元フラグ",
       source."アクセス日時",
       source."滞在秒数",
       source."閲覧回数",
       source."JSON詳細",
       NULL, NULL, 'MIGRATION', 'MIGRATION',
       COALESCE(source."登録日時", CURRENT_TIMESTAMP),
       COALESCE(source."更新日時", CURRENT_TIMESTAMP)
  FROM migr_web_history source
  LEFT JOIN migr_web_owner owner ON owner."旧ユーザーID" = source."ユーザーID"
  ON CONFLICT ("閲覧履歴ID") DO NOTHING;

DO $$
DECLARE
    source_rows BIGINT;
    migrated_rows BIGINT;
BEGIN
    SELECT COUNT(*) INTO source_rows FROM migr_web_history;
    SELECT COUNT(*) INTO migrated_rows
      FROM migr_web_history source
      JOIN public."NET_Web閲覧履歴情報" target ON target."閲覧履歴ID" = source."履歴番号";

    IF source_rows <> migrated_rows THEN
        RAISE EXCEPTION 'Web browsing history count mismatch: source %, migrated %',
            source_rows, migrated_rows;
    END IF;
END $$;

SELECT SETVAL(
    PG_GET_SERIAL_SEQUENCE('public."NET_Web閲覧履歴情報"', '閲覧履歴ID'),
    COALESCE((SELECT MAX("閲覧履歴ID") FROM public."NET_Web閲覧履歴情報"), 1),
    EXISTS (SELECT 1 FROM public."NET_Web閲覧履歴情報")
);

COMMIT;

\echo '--- 移行結果: 件数と期間 ---'
SELECT COUNT(*) AS "件数", MIN("アクセス日時") AS "最古", MAX("アクセス日時") AS "最新"
  FROM public."NET_Web閲覧履歴情報";

\echo '--- 移行結果: 持ち主 ---'
SELECT COALESCE("旧ユーザーID", '(なし)') AS "旧ユーザーID",
       COALESCE("利用者アカウントID"::TEXT, 'NULL') AS "アカウントID", COUNT(*) AS "件数"
  FROM public."NET_Web閲覧履歴情報" GROUP BY 1, 2 ORDER BY 3 DESC;

\echo '--- 移行結果: イベント種別 ---'
SELECT "イベント種別", COUNT(*) AS "件数" FROM public."NET_Web閲覧履歴情報" GROUP BY 1 ORDER BY 2 DESC;
