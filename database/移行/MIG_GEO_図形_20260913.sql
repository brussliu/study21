-- ============================================================================
-- Study 2.0 -> 2.1  図形管理（数学勉強 > 図形管理）移行
-- 移行元: study2.public."TRN_図形作成情報"   （図形 102 件）
-- 移行先: study21.public."GEO_図形情報"
-- ----------------------------------------------------------------------------
-- 事前条件:
--   1. database/図形管理/TBL_GEO_図形情報.sql を study21 に適用しておくこと
--      （適用済み。ACC_アカウント が先に存在している必要がある）。
--   2. 移行元・移行先 DB が同一 PostgreSQL インスタンスに存在すること
--      （dblink で接続）。
--
-- 実行例:
--   psql -h 192.168.0.100 -p 54320 -U postgres -d study21 -v ON_ERROR_STOP=1 \
--        -f database/移行/MIG_GEO_図形_20260913.sql
--
-- 値の対応:
--   図形番号        = 2.0 の 図形ID（'GEO202604250005247792940' / 'geometry-demo-1'）。
--                     2.1 の 図形ID は BIGSERIAL で採番し直す。
--   教科            = そのまま（空なら '数学'）
--   図形種別        = そのまま（NULL・空文字なら 'geometry'。2.0 は 011_… で後から
--                     追加した列なので、念のため両方を受ける）
--   登録区分        = そのまま（空なら 'saved'）
--   図形名          = そのまま
--   メモ            = 空文字 → NULL（2.1 は「未設定は NULL」）
--   タグ            = **2.0 のまま '|' 区切りで入れる**（カンマへ変換しない。
--                     空文字 → NULL）。2.0 は String.join("|", tags) で保存し、
--                     検索・タグ候補も '|' で分解していた。変換すると 2.0 の
--                     データと突き合わせられなくなるだけで得がないため。
--   GeoGebraXML     = NULL → 空文字（2.1 は NOT NULL DEFAULT ''）
--   サムネイル画像  = 空文字 → NULL（Base64 PNG をそのまま。2.0 と同じく DB に持つ）
--   表示順          = そのまま（NULL なら 0）
--   状態コード      = 削除FLG '1' → 'DELETED' / それ以外 → 'ACTIVE'
--                     （2.0 の 削除FLG は物理削除ではなく UPDATE による論理削除。
--                       2.1 も論理削除のまま 状態コード で持つ）
--   監査:
--     登録者/更新者アカウントID: 2.0 の 登録ID/更新ID を下の対応表で引く。
--       実データの 登録ID は 'geometry.jsp'（100 件）と 'system'（2 件）で、
--       どちらも人ではなく画面・機能の名前なので**引けず NULL になる**。
--       更新ID は 'geometry.jsp'（96 件）と 'ljz'（5 件）・'liu'（1 件）で、
--       この 6 件だけ 2 / 1 が入る。行は捨てない。
--       対応表の 'liu' → 1 / 'ljz' → 2 は TODO・日報・学習状況モニター・読書と
--       同じ判断（ACC_アカウント の 1 = bruss.ji.liu@gmail.com＝保護者 /
--       2 = ricky.jingze@gmail.com＝生徒）。
--     登録元コード='MIGRATION' / 更新元コード=NULL / バージョン=1。
--     登録日時/更新日時: 2.0 の値をそのまま使う（NULL なら CURRENT_TIMESTAMP。
--       実データは 102 件とも値があるためこのフォールバックは使われない）。
--
-- 冪等性:
--   図形番号 の UNIQUE に ON CONFLICT ("図形番号") DO NOTHING。
--   再実行しても件数は増えない（2 回実行して確認済み）。
--
-- 期待値について（2026-09-13 実測）:
--   当初の作業指示は「ACTIVE 100 / DELETED 2」だったが、study2 を実測すると
--   削除FLG='1' は **6 件**（demo 2 件 = geometry-demo-1 / geometry-demo-2、
--   saved 4 件 = 三角形・三角形2・三角形2・弓形）で、ACTIVE は 96 件だった。
--   サムネイル非空 100 件・GeoGebraXML 非空 100 件は指示どおり。
--   下の検証 DO ブロックは**実測値**で判定する（指示値を書くと移行が必ず失敗する）。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS dblink;

-- ---------------------------------------------------------------------------
-- 0. 2.0 の実行者（登録ID/更新ID）→ 2.1 のアカウント 対応表
--    他の移行（TODO・日報・学習状況モニター・読書）と同じ判断。
--    引けない ID（'geometry.jsp' / 'system' など）は NULL にする。
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE migr_geo_owner ("旧ユーザーID" TEXT PRIMARY KEY, "アカウントID" BIGINT) ON COMMIT DROP;
INSERT INTO migr_geo_owner VALUES ('ljz', 2), ('liu', 1);

-- ---------------------------------------------------------------------------
-- 1. 2.0 の図形を読む
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE migr_geo_figure ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study2',
       $remote$
       SELECT "図形ID", "教科", "図形種別", "登録区分", "図形名", "メモ", "タグ",
              "GeoGebraXML", "サムネイル画像", "表示順", "削除FLG",
              COALESCE("登録ID", ''), COALESCE("更新ID", ''), "登録日時", "更新日時"
         FROM public."TRN_図形作成情報"
       $remote$
       ) AS remote (
           "図形ID" VARCHAR(30),
           "教科" VARCHAR(20),
           "図形種別" VARCHAR(20),
           "登録区分" VARCHAR(10),
           "図形名" VARCHAR(120),
           "メモ" TEXT,
           "タグ" TEXT,
           "GeoGebraXML" TEXT,
           "サムネイル画像" TEXT,
           "表示順" INTEGER,
           "削除FLG" VARCHAR(1),
           "登録ID" VARCHAR(20),
           "更新ID" VARCHAR(20),
           "登録日時" TIMESTAMP,
           "更新日時" TIMESTAMP
       );

-- ---------------------------------------------------------------------------
-- 2. 移行前の検査（壊れた値はここで止める）
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    figure_rows BIGINT;
    active_rows BIGINT;
    deleted_rows BIGINT;
BEGIN
    SELECT COUNT(*) INTO figure_rows FROM migr_geo_figure;
    SELECT COUNT(*) INTO active_rows  FROM migr_geo_figure WHERE COALESCE("削除FLG", '0') <> '1';
    SELECT COUNT(*) INTO deleted_rows FROM migr_geo_figure WHERE "削除FLG" = '1';
    RAISE NOTICE '2.0 の図形管理: 図形 % 件（ACTIVE % / DELETED %）を移行します',
        figure_rows, active_rows, deleted_rows;

    IF figure_rows = 0 THEN
        RAISE EXCEPTION '2.0 の TRN_図形作成情報 が 0 件です（dblink の接続先を確認してください）';
    END IF;

    -- 自然キー（冪等の判定に使う）が重複していると、再実行の判定が曖昧になる
    IF figure_rows <> (SELECT COUNT(DISTINCT "図形ID") FROM migr_geo_figure) THEN
        RAISE EXCEPTION '2.0 の図形に 図形ID が重複する行があります（冪等の判定ができません）';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_geo_figure WHERE LENGTH("図形ID") > 30) THEN
        RAISE EXCEPTION '2.0 の 図形ID が 図形番号 VARCHAR(30) に入りません';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_geo_figure WHERE BTRIM(COALESCE("図形名", '')) = '') THEN
        RAISE EXCEPTION '2.0 の図形に 図形名 が空の行があります';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_geo_figure
                WHERE COALESCE(NULLIF(BTRIM(COALESCE("図形種別", '')), ''), 'geometry')
                      NOT IN ('geometry', 'function')) THEN
        RAISE EXCEPTION '2.0 の図形に 2.1 が知らない 図形種別 があります';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_geo_figure
                WHERE COALESCE(NULLIF(BTRIM(COALESCE("登録区分", '')), ''), 'saved')
                      NOT IN ('demo', 'saved')) THEN
        RAISE EXCEPTION '2.0 の図形に 2.1 が知らない 登録区分 があります';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_geo_figure WHERE "削除FLG" NOT IN ('0', '1')) THEN
        RAISE EXCEPTION '2.0 の図形に 2.1 が知らない 削除FLG があります';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_geo_figure WHERE COALESCE("表示順", 0) < 0) THEN
        RAISE EXCEPTION '2.0 の図形に負の 表示順 があります';
    END IF;

    -- 2.1 の CHECK に入らない値が無いことの確認（図形名の長さ）
    IF EXISTS (SELECT 1 FROM migr_geo_figure WHERE LENGTH("図形名") > 120) THEN
        RAISE EXCEPTION '2.0 の 図形名 が 図形名 VARCHAR(120) に入りません';
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 3. 図形（図形番号 の UNIQUE で冪等）
-- ---------------------------------------------------------------------------
INSERT INTO public."GEO_図形情報" (
    "図形番号", "教科", "図形種別", "登録区分", "図形名", "メモ", "タグ",
    "GeoGebraXML", "サムネイル画像", "表示順", "状態コード",
    "バージョン", "登録者アカウントID", "更新者アカウントID",
    "登録元コード", "更新元コード", "登録日時", "更新日時"
)
SELECT source."図形ID",                                                          -- 図形番号（2.0 の 図形ID）
       COALESCE(NULLIF(BTRIM(COALESCE(source."教科", '')), ''), '数学'),
       COALESCE(NULLIF(BTRIM(COALESCE(source."図形種別", '')), ''), 'geometry'),
       COALESCE(NULLIF(BTRIM(COALESCE(source."登録区分", '')), ''), 'saved'),
       source."図形名",
       NULLIF(source."メモ", ''),                                                -- 空文字は NULL（未設定）
       NULLIF(source."タグ", ''),                                                -- '|' 区切りのまま。空文字は NULL
       COALESCE(source."GeoGebraXML", ''),                                       -- NULL は空文字（2.1 は NOT NULL）
       NULLIF(source."サムネイル画像", ''),                                      -- 空文字は NULL
       COALESCE(source."表示順", 0),
       CASE WHEN source."削除FLG" = '1' THEN 'DELETED' ELSE 'ACTIVE' END,        -- 削除FLG '0'/'1' → 状態コード
       1,
       reg."アカウントID",
       upd."アカウントID",
       'MIGRATION', NULL,
       COALESCE(source."登録日時", CURRENT_TIMESTAMP),
       COALESCE(source."更新日時", CURRENT_TIMESTAMP)
  FROM migr_geo_figure source
  LEFT JOIN migr_geo_owner reg ON reg."旧ユーザーID" = source."登録ID"
  LEFT JOIN migr_geo_owner upd ON upd."旧ユーザーID" = source."更新ID"
 ON CONFLICT ("図形番号") DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. 件数検証（実測値と突き合わせる。違えば ROLLBACK させる）
--    期待値は 2026-09-13 の study2 実測（下の「期待値について」参照）。
--    指示書の「ACTIVE 100 / DELETED 2」は実データと違うので使わない。
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    c_total       BIGINT;
    c_active      BIGINT;
    c_deleted     BIGINT;
    c_thumb       BIGINT;
    c_xml         BIGINT;
    src_total     BIGINT;
    src_active    BIGINT;
    src_deleted   BIGINT;
    src_thumb     BIGINT;
    src_xml       BIGINT;
    missing       BIGINT;
BEGIN
    SELECT COUNT(*),
           COUNT(*) FILTER (WHERE "状態コード" = 'ACTIVE'),
           COUNT(*) FILTER (WHERE "状態コード" = 'DELETED'),
           COUNT(*) FILTER (WHERE COALESCE("サムネイル画像", '') <> ''),
           COUNT(*) FILTER (WHERE COALESCE("GeoGebraXML", '') <> '')
      INTO c_total, c_active, c_deleted, c_thumb, c_xml
      FROM public."GEO_図形情報";

    SELECT COUNT(*),
           COUNT(*) FILTER (WHERE COALESCE("削除FLG", '0') <> '1'),
           COUNT(*) FILTER (WHERE "削除FLG" = '1'),
           COUNT(*) FILTER (WHERE COALESCE("サムネイル画像", '') <> ''),
           COUNT(*) FILTER (WHERE COALESCE("GeoGebraXML", '') <> '')
      INTO src_total, src_active, src_deleted, src_thumb, src_xml
      FROM migr_geo_figure;

    IF c_total <> 102 OR src_total <> 102 THEN
        RAISE EXCEPTION '総数が 102 ではありません: 移行元 % / 移行先 %', src_total, c_total;
    END IF;
    IF c_active <> 96 THEN
        RAISE EXCEPTION '状態コード = ACTIVE が 96 ではありません: %', c_active;
    END IF;
    IF c_deleted <> 6 THEN
        RAISE EXCEPTION '状態コード = DELETED が 6 ではありません: %', c_deleted;
    END IF;
    IF c_thumb <> 100 THEN
        RAISE EXCEPTION 'サムネイルを持つ行が 100 ではありません: %', c_thumb;
    END IF;
    IF c_xml <> 100 THEN
        RAISE EXCEPTION 'GeoGebraXML が空でない行が 100 ではありません: %', c_xml;
    END IF;

    -- 2.0 側の集計とも一致すること（削除FLG → 状態コード の写像が正しいか）
    IF (src_active, src_deleted, src_thumb, src_xml) <> (c_active, c_deleted, c_thumb, c_xml) THEN
        RAISE EXCEPTION '2.0 の集計と一致しません: 2.0(ACTIVE % / DELETED % / サムネ % / XML %) '
                        '2.1(ACTIVE % / DELETED % / サムネ % / XML %)',
            src_active, src_deleted, src_thumb, src_xml, c_active, c_deleted, c_thumb, c_xml;
    END IF;

    -- 図形番号 の読み替え漏れが無いこと
    SELECT COUNT(*) INTO missing
      FROM migr_geo_figure source
     WHERE NOT EXISTS (SELECT 1 FROM public."GEO_図形情報" t WHERE t."図形番号" = source."図形ID");
    IF missing > 0 THEN
        RAISE EXCEPTION '移行できていない図形が % 件あります', missing;
    END IF;

    RAISE NOTICE '図形管理の移行完了: 図形 % 件（ACTIVE % / DELETED % / サムネイル % / GeoGebraXML %）',
        c_total, c_active, c_deleted, c_thumb, c_xml;
END $$;

-- ---------------------------------------------------------------------------
-- 5. 採番（BIGSERIAL）を移行した最大値に合わせる
-- ---------------------------------------------------------------------------
SELECT SETVAL(
    PG_GET_SERIAL_SEQUENCE('public."GEO_図形情報"', '図形ID'),
    COALESCE((SELECT MAX("図形ID") FROM public."GEO_図形情報"), 1),
    EXISTS (SELECT 1 FROM public."GEO_図形情報")
);

COMMIT;

-- ============================================================================
-- 移行結果の確認（ここから下は読み取りのみ。実行しなくてもよい）
-- ============================================================================

\echo '--- 移行結果: 件数と内訳 ---'
SELECT COUNT(*)                                        AS "総数",
       COUNT(*) FILTER (WHERE "状態コード" = 'ACTIVE')  AS "ACTIVE",
       COUNT(*) FILTER (WHERE "状態コード" = 'DELETED') AS "DELETED",
       COUNT(*) FILTER (WHERE "登録区分" = 'demo')      AS "demo",
       COUNT(*) FILTER (WHERE "登録区分" = 'saved')     AS "saved",
       COUNT(*) FILTER (WHERE COALESCE("サムネイル画像", '') <> '') AS "サムネイルあり",
       COUNT(*) FILTER (WHERE COALESCE("GeoGebraXML", '') <> '')    AS "GeoGebraXMLあり"
  FROM public."GEO_図形情報";

\echo '--- 移行結果: 監査 ---'
SELECT "登録元コード", "更新元コード", "バージョン",
       COUNT(*)                                          AS "件数",
       COUNT(*) FILTER (WHERE "登録者アカウントID" IS NULL) AS "登録者NULL",
       COUNT(*) FILTER (WHERE "更新者アカウントID" IS NULL) AS "更新者NULL"
  FROM public."GEO_図形情報"
 GROUP BY 1, 2, 3;

\echo '--- 移行結果: タグの一覧（| 区切りを分解。件数の多い順） ---'
SELECT tag AS "タグ", COUNT(*) AS "件数"
  FROM public."GEO_図形情報",
       LATERAL regexp_split_to_table(COALESCE("タグ", ''), '\|') AS tag
 WHERE BTRIM(tag) <> ''
 GROUP BY tag
 ORDER BY 2 DESC, 1;

\echo '--- 移行結果: GeoGebraXML / サムネイルの長さ ---'
SELECT MIN(LENGTH("GeoGebraXML"))                                     AS "XML最小",
       MAX(LENGTH("GeoGebraXML"))                                     AS "XML最大",
       MIN(LENGTH("サムネイル画像")) FILTER (WHERE "サムネイル画像" IS NOT NULL) AS "サムネ最小",
       MAX(LENGTH("サムネイル画像"))                                     AS "サムネ最大"
  FROM public."GEO_図形情報";

\echo '--- 移行結果: 図形一覧（表示順 → 図形番号） ---'
SELECT "図形番号", "図形名", "図形種別", "登録区分", "表示順", "状態コード",
       COALESCE("タグ", '')                       AS "タグ",
       LENGTH("GeoGebraXML")                      AS "XML長",
       COALESCE(LENGTH("サムネイル画像"), 0)      AS "サムネ長",
       "更新日時"
  FROM public."GEO_図形情報"
 ORDER BY "表示順", "図形番号"
 LIMIT 20;
