-- ============================================================================
-- Study 2.0 -> 2.1  NET サイト情報 復元（6 件）
-- 移行元: study2.public.NET_サイト情報（サイトID 196〜201）
-- 移行先: study21.public.NET_サイト情報
-- ----------------------------------------------------------------------------
-- 背景:
--   database/移行/MIG_NET_サイト・端末コントロール_20260911.sql は 2.0 の 175 件を
--   移行していたが、その後の運用で次の 6 件が 2.1 側から消えていた
--   （2026-09-12 時点の study21 は 169 件）。ユーザーの判断で復元する。
--
--     196 global.talk-cloud.net
--     197 arms-retcode.aliyuncs.com
--     198 talk-cloud.net
--     199 aliyuncs.com
--     200 campustop.net
--     201 server-side-tagging-kh4kgqvzda-uc.a.run.app
--
-- 方針:
--   * 対象 ID を明示的に限定する（他の行・他のサイトには触れない）。
--   * 変換規則は MIG_NET_サイト・端末コントロール_20260911.sql と完全に同じ。
--   * 旧 ID を維持し、ON CONFLICT DO NOTHING で冪等（再実行しても増えない）。
--
-- 事前条件: database/サイト管理/TBL_NET_サイト情報.sql 適用済み・study2 へ dblink 接続可
-- 実行例:   psql -U postgres -d study21 -v ON_ERROR_STOP=1 -f MIG_NET_サイト_復元_20260912.sql
-- ============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS dblink;

CREATE TEMP TABLE migr_net_site_restore ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study2',
       $remote$
       SELECT "サイトID", COALESCE("サイト名称", ''), COALESCE("サイトURL", ''),
              COALESCE("区分", ''), COALESCE("判定方法", ''), COALESCE("分類", ''),
              COALESCE("承認ステータス", ''), COALESCE("ステータス", ''),
              COALESCE("備考", ''), "登録日時", "更新日時"
         FROM public."NET_サイト情報"
        WHERE "サイトID" BETWEEN 196 AND 201
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

DO $$
DECLARE
    source_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO source_count FROM migr_net_site_restore;
    IF source_count <> 6 THEN
        RAISE EXCEPTION 'Expected 6 sites (196-201) in 2.0, found %', source_count;
    END IF;

    IF EXISTS (SELECT 1 FROM migr_net_site_restore WHERE BTRIM("サイト名称") = '' OR BTRIM("サイトURL") = '') THEN
        RAISE EXCEPTION '2.0 site contains a blank name or URL';
    END IF;

    IF EXISTS (SELECT 1 FROM migr_net_site_restore WHERE LENGTH("サイト名称") > 200 OR LENGTH("備考") > 200) THEN
        RAISE EXCEPTION '2.0 site value exceeds the 2.1 column length';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_net_site_restore
         WHERE "区分" NOT IN ('0.勉強', '1.通常', '2.休憩', '3.ゲーム')
    ) THEN
        RAISE EXCEPTION '2.0 site contains an unsupported 区分';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_net_site_restore
         WHERE "判定方法" NOT IN ('', '先頭一致', '末尾一致', '含める', '完全一致')
    ) THEN
        RAISE EXCEPTION '2.0 site contains an unsupported 判定方法';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_net_site_restore
         WHERE "承認ステータス" NOT IN ('', '未承認', '承認済')
            OR "ステータス" NOT IN ('', '有効', '無効')
    ) THEN
        RAISE EXCEPTION '2.0 site contains an unsupported approval or status value';
    END IF;

    -- 既に同じ ID があって内容が違う場合は中断（冪等な再実行だけを許容する）
    IF EXISTS (
        SELECT 1 FROM migr_net_site_restore source
          JOIN public."NET_サイト情報" target USING ("サイトID")
         WHERE target."サイト名称" <> source."サイト名称"
            OR target."サイトURL" <> source."サイトURL"
    ) THEN
        RAISE EXCEPTION 'A target site ID already exists with different data';
    END IF;

    RAISE NOTICE '2.0 のサイト % 件（196〜201）を復元します', source_count;
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
  FROM migr_net_site_restore source
  ON CONFLICT ("サイトID") DO NOTHING;

DO $$
DECLARE
    source_count BIGINT;
    restored_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO source_count FROM migr_net_site_restore;
    SELECT COUNT(*) INTO restored_count
      FROM migr_net_site_restore source
      JOIN public."NET_サイト情報" target USING ("サイトID")
     WHERE target."サイト名称" = source."サイト名称"
       AND target."サイトURL" = source."サイトURL";

    IF source_count <> restored_count THEN
        RAISE EXCEPTION 'Site restore count mismatch: source %, restored %', source_count, restored_count;
    END IF;
END $$;

SELECT SETVAL(
    PG_GET_SERIAL_SEQUENCE('public."NET_サイト情報"', 'サイトID'),
    COALESCE((SELECT MAX("サイトID") FROM public."NET_サイト情報"), 1),
    EXISTS (SELECT 1 FROM public."NET_サイト情報")
);

COMMIT;

\echo '--- 復元した 6 件 ---'
SELECT "サイトID", "サイト名称", "ホスト名", "区分コード", "判定方法コード",
       "分類コード", "承認ステータス", "状態"
  FROM public."NET_サイト情報"
 WHERE "サイトID" BETWEEN 196 AND 201 ORDER BY "サイトID";

\echo '--- 合計（175 件になること）---'
SELECT COUNT(*) AS "サイト件数" FROM public."NET_サイト情報";
