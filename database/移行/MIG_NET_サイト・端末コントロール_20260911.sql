-- ============================================================================
-- Study 2.0 -> 2.1  サイト管理 / 端末コントロール 移行
-- 移行元: study2.public.NET_サイト情報 / NET_端末コントロール情報
-- 移行先: study21.public.NET_サイト情報 / NET_端末コントロール情報
-- ----------------------------------------------------------------------------
-- 事前条件:
--   1. database/サイト管理/TBL_NET_サイト情報.sql
--   2. database/端末コントロール/TBL_NET_端末コントロール情報.sql
--   3. 移行元・移行先DBが同一PostgreSQLインスタンスに存在すること（dblink で接続）
--
-- 実行例:
--   psql -U postgres -d study21 -v ON_ERROR_STOP=1 -f MIG_NET_サイト・端末コントロール_20260911.sql
--
-- 値の対応（設計書 database/サイト管理/NET_サイト管理・端末コントロール設計.md §3）:
--   区分       0.勉強/1.通常/2.休憩/3.ゲーム -> STUDY/NORMAL/BREAK/GAME
--   判定方法   先頭一致/末尾一致/含める/完全一致 -> PREFIX/SUFFIX/CONTAINS/EXACT
--   分類       学習/娯楽/ショッピング/SNS/その他 -> LEARNING/ENTERTAINMENT/SHOPPING/SNS/OTHER
--              上記以外（英会話・ニュース等） -> OTHER + 分類名称 に元の文字列
--   承認      承認済 -> APPROVED（承認日時は移行日時で補完）/ 未承認・NULL -> PENDING
--   ステータス 有効・NULL -> 状態 '1' / 無効 -> 状態 '0'
--   端末ステータス T/K/G/B/S/J -> 端末モード（同じ値）
--
-- 監査:
--   2.0 の 登録ID/更新ID は 'site.jsp' / 'batR03' / 'AGENT' のような画面名・バッチ名で
--   実行者を特定できないため、アカウントID は NULL とし 登録元/更新元コード に 'MIGRATION' を残す。
--
-- 冪等性:
--   旧 ID を維持する。同じ ID に異なる内容が存在する場合は全体をロールバックする。
-- ============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS dblink;

CREATE TEMP TABLE migr_net_site ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study2',
       $remote$
       SELECT "サイトID", COALESCE("サイト名称", ''), COALESCE("サイトURL", ''),
              COALESCE("区分", ''), COALESCE("判定方法", ''), COALESCE("分類", ''),
              COALESCE("承認ステータス", ''), COALESCE("ステータス", ''),
              COALESCE("備考", ''), "登録日時", "更新日時"
         FROM public."NET_サイト情報"
       $remote$
       ) AS remote (
           "サイトID" BIGINT,
           "サイト名称" TEXT,
           "サイトURL" TEXT,
           "区分" TEXT,
           "判定方法" TEXT,
           "分類" TEXT,
           "承認ステータス" TEXT,
           "ステータス" TEXT,
           "備考" TEXT,
           "登録日時" TIMESTAMP,
           "更新日時" TIMESTAMP
       );

CREATE TEMP TABLE migr_net_terminal ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study2',
       $remote$
       SELECT "端末ID", COALESCE("IPアドレス", ''), COALESCE("端末名称", ''),
              COALESCE("端末ステータス", 'T'), "登録日時", "更新日時"
         FROM public."NET_端末コントロール情報"
       $remote$
       ) AS remote (
           "端末ID" BIGINT,
           "IPアドレス" TEXT,
           "端末名称" TEXT,
           "端末ステータス" TEXT,
           "登録日時" TIMESTAMP,
           "更新日時" TIMESTAMP
       );

DO $$
DECLARE
    site_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO site_count FROM migr_net_site;

    IF EXISTS (SELECT 1 FROM migr_net_site WHERE BTRIM("サイト名称") = '' OR BTRIM("サイトURL") = '') THEN
        RAISE EXCEPTION '2.0 site contains a blank name or URL';
    END IF;

    IF EXISTS (SELECT 1 FROM migr_net_site WHERE LENGTH("サイト名称") > 200 OR LENGTH("備考") > 200) THEN
        RAISE EXCEPTION '2.0 site value exceeds the 2.1 column length';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_net_site
         WHERE "区分" NOT IN ('0.勉強', '1.通常', '2.休憩', '3.ゲーム')
    ) THEN
        RAISE EXCEPTION '2.0 site contains an unsupported 区分';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_net_site
         WHERE "判定方法" NOT IN ('', '先頭一致', '末尾一致', '含める', '完全一致')
    ) THEN
        RAISE EXCEPTION '2.0 site contains an unsupported 判定方法';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_net_site
         WHERE "承認ステータス" NOT IN ('', '未承認', '承認済')
            OR "ステータス" NOT IN ('', '有効', '無効')
    ) THEN
        RAISE EXCEPTION '2.0 site contains an unsupported approval or status value';
    END IF;

    -- 正規化したホスト名が空になる URL は移行できない
    IF EXISTS (
        SELECT 1 FROM migr_net_site
         WHERE regexp_replace(regexp_replace(lower("サイトURL"), '^[a-z]+://', ''), '[/:?].*$', '') = ''
    ) THEN
        RAISE EXCEPTION '2.0 site URL cannot be normalized into a host name';
    END IF;

    IF EXISTS (SELECT 1 FROM migr_net_terminal WHERE BTRIM("IPアドレス") = '' OR BTRIM("端末名称") = '') THEN
        RAISE EXCEPTION '2.0 terminal contains a blank IP or name';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_net_terminal WHERE "端末ステータス" NOT IN ('T', 'K', 'G', 'B', 'S', 'J')
    ) THEN
        RAISE EXCEPTION '2.0 terminal contains an unsupported 端末ステータス';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_net_terminal GROUP BY "IPアドレス" HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION '2.0 terminal contains duplicate IP addresses';
    END IF;

    -- 既に同じ ID があって内容が違う場合は中断（冪等な再実行は許容する）
    IF EXISTS (
        SELECT 1 FROM migr_net_site source
          JOIN public."NET_サイト情報" target USING ("サイトID")
         WHERE target."サイト名称" <> source."サイト名称"
            OR target."サイトURL" <> source."サイトURL"
    ) THEN
        RAISE EXCEPTION 'A target site ID already exists with different data';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_net_terminal source
          JOIN public."NET_端末コントロール情報" target USING ("端末ID")
         WHERE target."IPアドレス" <> source."IPアドレス"
    ) THEN
        RAISE EXCEPTION 'A target terminal ID already exists with different data';
    END IF;

    RAISE NOTICE '2.0 のサイト % 件、端末 % 件を移行します', site_count,
                 (SELECT COUNT(*) FROM migr_net_terminal);
END $$;

INSERT INTO public."NET_サイト情報" (
    "サイトID", "サイト名称", "サイトURL", "ホスト名", "区分コード", "判定方法コード",
    "分類コード", "分類名称", "承認ステータス", "承認日時", "状態", "備考", "バージョン",
    "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時"
)
SELECT source."サイトID",
       source."サイト名称",
       source."サイトURL",
       -- ホスト名の正規化（小文字化・スキーム/ポート/パス/クエリ除去・先頭 www. 除去）
       regexp_replace(
           regexp_replace(
               regexp_replace(lower(source."サイトURL"), '^[a-z]+://', ''),
               '[/:?].*$', ''
           ),
           '^www\.', ''
       ),
       CASE source."区分"
           WHEN '0.勉強' THEN 'STUDY'
           WHEN '1.通常' THEN 'NORMAL'
           WHEN '2.休憩' THEN 'BREAK'
           WHEN '3.ゲーム' THEN 'GAME'
       END,
       CASE source."判定方法"
           WHEN '先頭一致' THEN 'PREFIX'
           WHEN '含める' THEN 'CONTAINS'
           WHEN '完全一致' THEN 'EXACT'
           ELSE 'SUFFIX'
       END,
       CASE source."分類"
           WHEN '学習' THEN 'LEARNING'
           WHEN '娯楽' THEN 'ENTERTAINMENT'
           WHEN 'ショッピング' THEN 'SHOPPING'
           WHEN 'SNS' THEN 'SNS'
           ELSE 'OTHER'
       END,
       CASE
           WHEN source."分類" IN ('', '学習', '娯楽', 'ショッピング', 'SNS', 'その他') THEN NULL
           ELSE source."分類"
       END,
       CASE source."承認ステータス" WHEN '承認済' THEN 'APPROVED' ELSE 'PENDING' END,
       CASE source."承認ステータス" WHEN '承認済' THEN CURRENT_TIMESTAMP ELSE NULL END,
       CASE source."ステータス" WHEN '無効' THEN '0' ELSE '1' END,
       NULLIF(BTRIM(source."備考"), ''),
       1,
       NULL, NULL, 'MIGRATION', 'MIGRATION',
       COALESCE(source."登録日時", CURRENT_TIMESTAMP),
       COALESCE(source."更新日時", CURRENT_TIMESTAMP)
  FROM migr_net_site source
 ON CONFLICT ("サイトID") DO NOTHING;

INSERT INTO public."NET_端末コントロール情報" (
    "端末ID", "IPアドレス", "端末名称", "端末モード", "状態", "バージョン",
    "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時"
)
SELECT source."端末ID", source."IPアドレス", source."端末名称", source."端末ステータス", '1', 1,
       NULL, NULL, 'MIGRATION', 'MIGRATION',
       COALESCE(source."登録日時", CURRENT_TIMESTAMP),
       COALESCE(source."更新日時", CURRENT_TIMESTAMP)
  FROM migr_net_terminal source
 ON CONFLICT ("端末ID") DO NOTHING;

DO $$
DECLARE
    source_sites BIGINT;
    migrated_sites BIGINT;
    source_terminals BIGINT;
    migrated_terminals BIGINT;
BEGIN
    SELECT COUNT(*) INTO source_sites FROM migr_net_site;
    SELECT COUNT(*) INTO migrated_sites
      FROM migr_net_site source
      JOIN public."NET_サイト情報" target USING ("サイトID")
     WHERE target."サイト名称" = source."サイト名称"
       AND target."サイトURL" = source."サイトURL"
       AND target."登録元コード" = 'MIGRATION';

    SELECT COUNT(*) INTO source_terminals FROM migr_net_terminal;
    SELECT COUNT(*) INTO migrated_terminals
      FROM migr_net_terminal source
      JOIN public."NET_端末コントロール情報" target USING ("端末ID")
     WHERE target."IPアドレス" = source."IPアドレス"
       AND target."端末名称" = source."端末名称";

    IF source_sites <> migrated_sites THEN
        RAISE EXCEPTION 'Site count mismatch: source %, migrated %', source_sites, migrated_sites;
    END IF;
    IF source_terminals <> migrated_terminals THEN
        RAISE EXCEPTION 'Terminal count mismatch: source %, migrated %', source_terminals, migrated_terminals;
    END IF;
END $$;

SELECT SETVAL(
    PG_GET_SERIAL_SEQUENCE('public."NET_サイト情報"', 'サイトID'),
    COALESCE((SELECT MAX("サイトID") FROM public."NET_サイト情報"), 1),
    EXISTS (SELECT 1 FROM public."NET_サイト情報")
);

SELECT SETVAL(
    PG_GET_SERIAL_SEQUENCE('public."NET_端末コントロール情報"', '端末ID'),
    COALESCE((SELECT MAX("端末ID") FROM public."NET_端末コントロール情報"), 1),
    EXISTS (SELECT 1 FROM public."NET_端末コントロール情報")
);

COMMIT;

\echo '--- 移行結果: サイト（区分別）---'
SELECT "区分コード", "承認ステータス", "状態", COUNT(*) AS "件数"
  FROM public."NET_サイト情報"
 GROUP BY 1, 2, 3 ORDER BY 1, 2, 3;

\echo '--- 移行結果: 判定方法・分類 ---'
SELECT "判定方法コード", COUNT(*) AS "件数" FROM public."NET_サイト情報" GROUP BY 1 ORDER BY 1;
SELECT "分類コード", "分類名称", COUNT(*) AS "件数" FROM public."NET_サイト情報" GROUP BY 1, 2 ORDER BY 1, 2;

\echo '--- 移行結果: 端末 ---'
SELECT "端末ID", "IPアドレス", "端末名称", "端末モード", "状態"
  FROM public."NET_端末コントロール情報" ORDER BY "端末ID";

\echo '--- 移行結果: 合計 ---'
SELECT (SELECT COUNT(*) FROM public."NET_サイト情報") AS "サイト",
       (SELECT COUNT(*) FROM public."NET_端末コントロール情報") AS "端末";
