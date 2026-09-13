-- ============================================================================
-- Study 2.0 -> 2.1  AI呼出履歴 移行（全件）
-- 移行元: study2.public.BAT_AI呼出履歴情報（66,800 件・約 394MB）
-- 移行先: study21.public.BAT_AI呼出履歴情報
-- ----------------------------------------------------------------------------
-- 事前条件:
--   1. database/AI呼出履歴/TBL_BAT_AI呼出履歴情報.sql
--   2. 移行元・移行先DBが同一PostgreSQLインスタンスに存在すること（dblink で接続）
--
-- 実行例:
--   psql -U postgres -d study21 -v ON_ERROR_STOP=1 -f MIG_BAT_AI呼出履歴_20260912.sql
--
-- 方針:
--   * **プロンプト・レスポンスの本文も含めて全件移行する**（ユーザーの指定）。
--     1 行が大きい（レスポンスは最大 200KB 超）ので、時間と WAL を多く使う。
--   * 値の対応: 結果区分 '成功' -> 'SUCCESS' / '失敗' -> 'FAILURE'。
--     言語区分・AI区分・モデル名 は 2.0 の値をそのまま保持する。
--   * 実行ID は 2.0 の値をそのまま残す（2.1 の BAT_バッチ実行履歴情報 に無い ID もある。
--     66,800 件中 48,336 件は 2.0 でも NULL）。
--   * 監査: 2.0 の 登録ID / 更新ID は実行者を特定できないため、アカウントID は NULL で
--     登録元コード = 'MIGRATION' とする。
--
-- 冪等性:
--   旧 履歴ID を維持し ON CONFLICT DO NOTHING（再実行しても増えない）。
--   移行元は稼働中で件数が増えるため、件数検証は「この実行で読んだ件数」と比較する。
-- ============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS dblink;

CREATE TEMP TABLE migr_ai_call ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study2',
       $remote$
       SELECT "履歴ID", "実行ID", COALESCE("タスクコード", ''),
              COALESCE("処理キー", ''), COALESCE("言語区分", ''), COALESCE("AI区分", ''),
              COALESCE("モデル名", ''), "呼出URL", "HTTPステータス",
              "開始日時", "終了日時", "処理時間ms", COALESCE("結果区分", ''),
              COALESCE("エラーコード", ''), "エラーメッセージ", "プロンプト", "レスポンス",
              "入力トークン数", "出力トークン数", "合計トークン数",
              "登録日時", "更新日時"
         FROM public."BAT_AI呼出履歴情報"
       $remote$
       ) AS remote (
           "履歴ID" BIGINT,
           "実行ID" BIGINT,
           "タスクコード" TEXT,
           "処理キー" TEXT,
           "言語区分" TEXT,
           "AI区分" TEXT,
           "モデル名" TEXT,
           "呼出URL" TEXT,
           "HTTPステータス" INTEGER,
           "開始日時" TIMESTAMP,
           "終了日時" TIMESTAMP,
           "処理時間ms" INTEGER,
           "結果区分" TEXT,
           "エラーコード" TEXT,
           "エラーメッセージ" TEXT,
           "プロンプト" TEXT,
           "レスポンス" TEXT,
           "入力トークン数" INTEGER,
           "出力トークン数" INTEGER,
           "合計トークン数" INTEGER,
           "登録日時" TIMESTAMP,
           "更新日時" TIMESTAMP
       );

DO $$
DECLARE
    source_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO source_count FROM migr_ai_call;
    RAISE NOTICE '2.0 の AI呼出履歴 % 件を移行します', source_count;

    IF EXISTS (SELECT 1 FROM migr_ai_call WHERE "開始日時" IS NULL) THEN
        RAISE EXCEPTION '2.0 AI call history contains a row without 開始日時';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_ai_call WHERE "結果区分" NOT IN ('成功', '失敗')
    ) THEN
        RAISE EXCEPTION '2.0 AI call history contains an unsupported 結果区分';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_ai_call
         WHERE "終了日時" IS NOT NULL AND "終了日時" < "開始日時"
    ) THEN
        RAISE EXCEPTION '2.0 AI call history contains 終了日時 before 開始日時';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_ai_call
         WHERE "HTTPステータス" IS NOT NULL AND ("HTTPステータス" < 100 OR "HTTPステータス" > 599)
    ) THEN
        RAISE EXCEPTION '2.0 AI call history contains an out-of-range HTTP status';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_ai_call
         WHERE COALESCE("処理時間ms", 0) < 0
            OR COALESCE("入力トークン数", 0) < 0
            OR COALESCE("出力トークン数", 0) < 0
            OR COALESCE("合計トークン数", 0) < 0
    ) THEN
        RAISE EXCEPTION '2.0 AI call history contains a negative numeric value';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_ai_call
         WHERE LENGTH("タスクコード") > 50 OR LENGTH("処理キー") > 300
            OR LENGTH("言語区分") > 20 OR LENGTH("AI区分") > 20
            OR LENGTH("モデル名") > 100 OR LENGTH("エラーコード") > 100
    ) THEN
        RAISE EXCEPTION '2.0 AI call history value exceeds the 2.1 column length';
    END IF;

    IF EXISTS (SELECT 1 FROM migr_ai_call WHERE BTRIM("タスクコード") = '') THEN
        RAISE EXCEPTION '2.0 AI call history contains a blank タスクコード';
    END IF;

    -- 既に同じ ID があって内容が違う場合は中断（冪等な再実行だけを許容する）
    IF EXISTS (
        SELECT 1 FROM migr_ai_call source
          JOIN public."BAT_AI呼出履歴情報" target ON target."呼出履歴ID" = source."履歴ID"
         WHERE target."開始日時" <> source."開始日時"
            OR target."バッチコード" <> source."タスクコード"
    ) THEN
        RAISE EXCEPTION 'A target AI call history ID already exists with different data';
    END IF;
END $$;

INSERT INTO public."BAT_AI呼出履歴情報" (
    "呼出履歴ID", "実行ID", "バッチコード", "処理キー", "言語区分", "AI区分", "モデル名",
    "呼出URL", "HTTPステータス", "開始日時", "終了日時", "処理時間ms", "結果区分",
    "エラーコード", "エラーメッセージ", "プロンプト", "レスポンス",
    "入力トークン数", "出力トークン数", "合計トークン数",
    "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時"
)
SELECT source."履歴ID",
       source."実行ID",
       source."タスクコード",
       NULLIF(BTRIM(source."処理キー"), ''),
       NULLIF(BTRIM(source."言語区分"), ''),
       NULLIF(BTRIM(source."AI区分"), ''),
       NULLIF(BTRIM(source."モデル名"), ''),
       source."呼出URL",
       source."HTTPステータス",
       source."開始日時",
       source."終了日時",
       source."処理時間ms",
       CASE source."結果区分" WHEN '成功' THEN 'SUCCESS' ELSE 'FAILURE' END,
       NULLIF(BTRIM(source."エラーコード"), ''),
       source."エラーメッセージ",
       source."プロンプト",
       source."レスポンス",
       source."入力トークン数",
       source."出力トークン数",
       source."合計トークン数",
       NULL, NULL, 'MIGRATION', 'MIGRATION',
       COALESCE(source."登録日時", CURRENT_TIMESTAMP),
       COALESCE(source."更新日時", CURRENT_TIMESTAMP)
  FROM migr_ai_call source
  ON CONFLICT ("呼出履歴ID") DO NOTHING;

DO $$
DECLARE
    source_rows BIGINT;
    migrated_rows BIGINT;
BEGIN
    SELECT COUNT(*) INTO source_rows FROM migr_ai_call;
    SELECT COUNT(*) INTO migrated_rows
      FROM migr_ai_call source
      JOIN public."BAT_AI呼出履歴情報" target ON target."呼出履歴ID" = source."履歴ID";

    IF source_rows <> migrated_rows THEN
        RAISE EXCEPTION 'AI call history count mismatch: source %, migrated %',
            source_rows, migrated_rows;
    END IF;
END $$;

SELECT SETVAL(
    PG_GET_SERIAL_SEQUENCE('public."BAT_AI呼出履歴情報"', '呼出履歴ID'),
    COALESCE((SELECT MAX("呼出履歴ID") FROM public."BAT_AI呼出履歴情報"), 1),
    EXISTS (SELECT 1 FROM public."BAT_AI呼出履歴情報")
);

COMMIT;

\echo '--- 移行結果: 件数・期間・サイズ ---'
SELECT COUNT(*) AS "件数", MIN("開始日時") AS "最古", MAX("開始日時") AS "最新",
       pg_size_pretty(pg_total_relation_size('public."BAT_AI呼出履歴情報"')) AS "サイズ"
  FROM public."BAT_AI呼出履歴情報";

\echo '--- 移行結果: 結果区分 ---'
SELECT "結果区分", COUNT(*) AS "件数" FROM public."BAT_AI呼出履歴情報" GROUP BY 1 ORDER BY 2 DESC;

\echo '--- 移行結果: バッチコード（上位 15）---'
SELECT "バッチコード", COUNT(*) AS "件数" FROM public."BAT_AI呼出履歴情報"
 GROUP BY 1 ORDER BY 2 DESC LIMIT 15;

\echo '--- 移行結果: AI区分 ---'
SELECT "AI区分", COUNT(*) AS "件数" FROM public."BAT_AI呼出履歴情報" GROUP BY 1 ORDER BY 2 DESC;
