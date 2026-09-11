-- ============================================================================
-- Study 2.0 -> 2.1  リンククリップ移行
-- 移行元: study2.public.COM_リンククリップ情報 / COM_リンククリップタグ情報
-- 移行先: study21.public.LNK_リンククリップ情報 / LNK_リンククリップタグ情報
-- ----------------------------------------------------------------------------
-- 事前条件:
--   1. TBL_LNK_リンククリップ情報.sql
--   2. TBL_LNK_リンククリップタグ情報.sql
--   3. 移行元・移行先DBが同一PostgreSQLインスタンスに存在すること
--
-- 旧ユーザー対応:
--   liu -> bruss.ji.liu@gmail.com  (GUARDIAN)
--   ljz -> ricky.jingze@gmail.com  (STUDENT)
--
-- 実行例:
--   psql -U postgres -d study21 -v ON_ERROR_STOP=1 -f MIG_LNK_リンククリップ_20260911.sql
--
-- 冪等性:
--   旧リンククリップIDを維持し、既存ID/タグは変更しない。
--   同じIDに異なる所有者・URL・タイトルが存在する場合は全体をロールバックする。
-- ============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS dblink;

CREATE TEMP TABLE migr_lnk_user_map (
    "旧ユーザーID" VARCHAR(64) PRIMARY KEY,
    "所有者アカウントID" BIGINT NOT NULL UNIQUE
) ON COMMIT DROP;

INSERT INTO migr_lnk_user_map ("旧ユーザーID", "所有者アカウントID")
SELECT mapping.old_user_id, account."アカウントID"
  FROM (VALUES
        ('liu'::VARCHAR, 'bruss.ji.liu@gmail.com'::VARCHAR, 'GUARDIAN'::VARCHAR),
        ('ljz'::VARCHAR, 'ricky.jingze@gmail.com'::VARCHAR, 'STUDENT'::VARCHAR)
       ) AS mapping(old_user_id, login_id, account_type)
  JOIN public."ACC_アカウント" account
    ON LOWER(account."ログインID") = LOWER(mapping.login_id)
   AND account."アカウント種別" = mapping.account_type;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM migr_lnk_user_map) <> 2 THEN
        RAISE EXCEPTION 'Required 2.1 link-clip owner accounts were not found or account types do not match';
    END IF;
END $$;

CREATE TEMP TABLE migr_lnk_old_clip ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study2',
       $remote$
       SELECT "リンククリップID", "ユーザーID", "保存先区分", "ソース区分", "クリップ種別",
              "サイト名称", "ページタイトル", "URL", "URL正規化", "概要", "AI概要", "メモ",
              "公開者名称", "公開日時", "動画時間秒", "縮略画像URL",
              "お気に入りフラグ", "既読フラグ", "アーカイブフラグ", "閲覧回数",
              "最終閲覧日時", "メタ情報取得日時", "AI要約生成日時", "登録日時", "更新日時"
         FROM public."COM_リンククリップ情報"
       $remote$
       ) AS remote (
           "リンククリップID" BIGINT,
           "ユーザーID" VARCHAR(64),
           "保存先区分" VARCHAR(30),
           "ソース区分" VARCHAR(30),
           "クリップ種別" VARCHAR(30),
           "サイト名称" VARCHAR(200),
           "ページタイトル" VARCHAR(300),
           "URL" TEXT,
           "URL正規化" TEXT,
           "概要" TEXT,
           "AI概要" TEXT,
           "メモ" TEXT,
           "公開者名称" VARCHAR(200),
           "公開日時" TIMESTAMP,
           "動画時間秒" INTEGER,
           "縮略画像URL" TEXT,
           "お気に入りフラグ" CHAR(1),
           "既読フラグ" CHAR(1),
           "アーカイブフラグ" CHAR(1),
           "閲覧回数" INTEGER,
           "最終閲覧日時" TIMESTAMP,
           "メタ情報取得日時" TIMESTAMP,
           "AI要約生成日時" TIMESTAMP,
           "登録日時" TIMESTAMP,
           "更新日時" TIMESTAMP
       );

CREATE TEMP TABLE migr_lnk_old_tag ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study2',
       $remote$
       SELECT "リンククリップID", "タグ名称", "表示順", "登録日時", "更新日時"
         FROM public."COM_リンククリップタグ情報"
       $remote$
       ) AS remote (
           "リンククリップID" BIGINT,
           "タグ名称" VARCHAR(100),
           "表示順" INTEGER,
           "登録日時" TIMESTAMP,
           "更新日時" TIMESTAMP
       );

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM migr_lnk_old_clip source
         WHERE NOT EXISTS (
             SELECT 1 FROM migr_lnk_user_map mapping
              WHERE mapping."旧ユーザーID" = source."ユーザーID"
         )
    ) THEN
        RAISE EXCEPTION 'Unmapped 2.0 link-clip user exists';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_lnk_old_clip
         WHERE NULLIF(BTRIM("ページタイトル"), '') IS NULL
            OR NULLIF(BTRIM("URL"), '') IS NULL
            OR NULLIF(BTRIM("URL正規化"), '') IS NULL
            OR LENGTH("URL正規化") > 2048
    ) THEN
        RAISE EXCEPTION '2.0 link-clip contains an invalid required field';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_lnk_old_clip
         WHERE "保存先区分" NOT IN ('未整理', 'あとで見る', '学習素材', '参考資料', '完了')
            OR "ソース区分" NOT IN ('WEB', 'YOUTUBE', 'GITHUB', 'WIKIPEDIA', 'NEWS', 'OTHER')
            OR "クリップ種別" NOT IN ('LINK', 'VIDEO', 'ARTICLE', 'REPOSITORY', 'NEWS')
            OR "お気に入りフラグ" NOT IN ('0', '1')
            OR "既読フラグ" NOT IN ('0', '1')
            OR "アーカイブフラグ" NOT IN ('0', '1')
            OR COALESCE("閲覧回数", -1) < 0
            OR ("動画時間秒" IS NOT NULL AND "動画時間秒" < 0)
    ) THEN
        RAISE EXCEPTION '2.0 link-clip contains an unsupported code or numeric value';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_lnk_old_tag tag
          LEFT JOIN migr_lnk_old_clip clip USING ("リンククリップID")
         WHERE clip."リンククリップID" IS NULL
            OR NULLIF(BTRIM(tag."タグ名称"), '') IS NULL
            OR tag."表示順" < 0
    ) THEN
        RAISE EXCEPTION '2.0 link-clip tag contains an orphan or invalid value';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM migr_lnk_old_tag
         GROUP BY "リンククリップID", LOWER("タグ名称")
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION '2.0 link-clip contains case-insensitive duplicate tags';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM migr_lnk_old_clip source
          JOIN migr_lnk_user_map mapping ON mapping."旧ユーザーID" = source."ユーザーID"
          JOIN public."LNK_リンククリップ情報" target USING ("リンククリップID")
         WHERE target."所有者アカウントID" <> mapping."所有者アカウントID"
            OR target."URL" <> source."URL"
            OR target."ページタイトル" <> source."ページタイトル"
    ) THEN
        RAISE EXCEPTION 'A target link-clip ID already exists with different data';
    END IF;
END $$;

INSERT INTO public."LNK_リンククリップ情報" (
    "リンククリップID", "所有者アカウントID", "保存先コード", "ソースコード", "クリップ種別",
    "サイト名称", "ページタイトル", "URL", "URL正規化", "概要", "AI概要", "メモ",
    "公開者名称", "公開日時", "動画時間秒", "縮略画像URL",
    "お気に入り", "既読", "アーカイブ", "閲覧回数", "最終閲覧日時",
    "メタ情報取得日時", "AI要約生成日時", "バージョン",
    "登録者アカウントID", "更新者アカウントID", "登録日時", "更新日時"
)
SELECT source."リンククリップID",
       mapping."所有者アカウントID",
       CASE source."保存先区分"
           WHEN '未整理' THEN 'INBOX'
           WHEN 'あとで見る' THEN 'READ_LATER'
           WHEN '学習素材' THEN 'LEARNING'
           WHEN '参考資料' THEN 'REFERENCE'
           WHEN '完了' THEN 'DONE'
       END,
       CASE
           WHEN LOWER(source."URL") LIKE 'file:/%' OR source."URL" ~ '^[A-Za-z]:' THEN 'LOCAL_FILE'
           ELSE source."ソース区分"
       END,
       CASE
           WHEN LOWER(source."URL") LIKE 'file:/%' OR source."URL" ~ '^[A-Za-z]:' THEN 'FILE'
           ELSE source."クリップ種別"
       END,
       source."サイト名称", source."ページタイトル", source."URL", source."URL正規化",
       source."概要", source."AI概要", source."メモ", source."公開者名称", source."公開日時",
       source."動画時間秒", source."縮略画像URL",
       source."お気に入りフラグ" = '1', source."既読フラグ" = '1', source."アーカイブフラグ" = '1',
       source."閲覧回数", source."最終閲覧日時", source."メタ情報取得日時", source."AI要約生成日時",
       1, mapping."所有者アカウントID", mapping."所有者アカウントID",
       COALESCE(source."登録日時", CURRENT_TIMESTAMP), COALESCE(source."更新日時", CURRENT_TIMESTAMP)
  FROM migr_lnk_old_clip source
  JOIN migr_lnk_user_map mapping ON mapping."旧ユーザーID" = source."ユーザーID"
 ON CONFLICT ("リンククリップID") DO NOTHING;

INSERT INTO public."LNK_リンククリップタグ情報" (
    "リンククリップID", "タグ名称", "表示順", "登録者アカウントID", "更新者アカウントID",
    "登録日時", "更新日時"
)
SELECT tag."リンククリップID", tag."タグ名称", tag."表示順",
       clip."所有者アカウントID", clip."所有者アカウントID",
       COALESCE(tag."登録日時", CURRENT_TIMESTAMP), COALESCE(tag."更新日時", CURRENT_TIMESTAMP)
  FROM migr_lnk_old_tag tag
  JOIN public."LNK_リンククリップ情報" clip USING ("リンククリップID")
 ON CONFLICT ("リンククリップID", "タグ名称") DO NOTHING;

DO $$
DECLARE
    source_clip_count BIGINT;
    migrated_clip_count BIGINT;
    source_tag_count BIGINT;
    migrated_tag_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO source_clip_count FROM migr_lnk_old_clip;
    SELECT COUNT(*) INTO migrated_clip_count
      FROM migr_lnk_old_clip source
      JOIN migr_lnk_user_map mapping ON mapping."旧ユーザーID" = source."ユーザーID"
      JOIN public."LNK_リンククリップ情報" target USING ("リンククリップID")
     WHERE target."所有者アカウントID" = mapping."所有者アカウントID"
       AND target."URL" = source."URL"
       AND target."ページタイトル" = source."ページタイトル";

    SELECT COUNT(*) INTO source_tag_count FROM migr_lnk_old_tag;
    SELECT COUNT(*) INTO migrated_tag_count
      FROM migr_lnk_old_tag source
      JOIN public."LNK_リンククリップタグ情報" target
        ON target."リンククリップID" = source."リンククリップID"
       AND target."タグ名称" = source."タグ名称";

    IF source_clip_count <> migrated_clip_count THEN
        RAISE EXCEPTION 'Link-clip count mismatch: source %, migrated %', source_clip_count, migrated_clip_count;
    END IF;
    IF source_tag_count <> migrated_tag_count THEN
        RAISE EXCEPTION 'Link-clip tag count mismatch: source %, migrated %', source_tag_count, migrated_tag_count;
    END IF;
END $$;

SELECT SETVAL(
    PG_GET_SERIAL_SEQUENCE('public."LNK_リンククリップ情報"', 'リンククリップID'),
    COALESCE((SELECT MAX("リンククリップID") FROM public."LNK_リンククリップ情報"), 1),
    EXISTS (SELECT 1 FROM public."LNK_リンククリップ情報")
);

SELECT account."ログインID" AS "所有者", COUNT(*) AS "クリップ件数"
  FROM public."LNK_リンククリップ情報" clip
  JOIN public."ACC_アカウント" account ON account."アカウントID" = clip."所有者アカウントID"
 GROUP BY account."ログインID"
 ORDER BY account."ログインID";

SELECT COUNT(*) AS "タグ件数" FROM public."LNK_リンククリップタグ情報";

COMMIT;
