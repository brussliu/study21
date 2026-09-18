-- ============================================================================
-- Study 2.0 -> 2.1  読書管理 移行（追補）: 分類と本文 PDF / 表紙
-- 移行元: study2.public."TRN_英語読書書籍情報"（書籍 6 冊。分類の手掛かりは タグ）
--         study2.public."COM_ファイル情報"（機能区分='ENGLISH_READING' の 12 件＝
--                                          PDF 6 件 + COVER 6 件）
-- 移行先: study21.public."RED_書籍分類情報" / "RED_書籍情報"."分類ID" /
--         "RED_書籍ファイル情報"
-- ----------------------------------------------------------------------------
-- 背景:
--   2.0 の書籍管理には分類が無く、`タグ`（'Novel' / '中国語'）と `難易度` でしか
--   区別できなかった。2.1 は本棚の区切りとして `RED_書籍分類情報` を持ち、
--   実データ（6 冊）が次の 3 分類に分かれるように初期値を入れる。
--
--     分類（表示順）        書籍                                         手掛かり
--     1 英語 小説           01./02./03. Harry Potter … （3 冊）           タグ 'Novel'
--     2 英語 リーディング   Lonely Planet China / Raya and the Last Dragon  タグ無しの英語
--     3 中国語               射鵰英雄伝                                    タグ '中国語'
--
--   分類は**マスタ**なので、あとから保護者が書籍管理画面で自由に改名・追加・
--   並べ替え・削除できる（この移行は初期値の投入にすぎない）。
--
-- 事前条件:
--   1. database/読書管理/TBL_RED_書籍分類情報.sql・TBL_RED_書籍ファイル情報.sql を
--      適用し、TBL_RED_書籍情報.sql の追補（分類ID 列）を当てておくこと。
--   2. MIG_RED_読書_20260913.sql 済み（書籍 6 冊が入っていること）。
--   3. 移行元・移行先 DB が同一 PostgreSQL インスタンスに存在すること（dblink）。
--
-- 実行例:
--   psql -h 192.168.0.100 -p 54320 -U postgres -d study21 -v ON_ERROR_STOP=1 \
--        -f database/移行/MIG_RED_読書_分類_20260913.sql
--
-- **実体ファイルについて（重要）**
--   2.0 の PDF・表紙の実体は 2.0 サーバの `webapps/file/ENGLISH_READING/<yyyyMM>/` に
--   あり、2.0 のソース一式には含まれていない（この環境からは取得できない）。
--   そこで 2.1 は
--     a) 書籍管理画面から PDF をアップロードし直す（`books/<書籍番号>/` に保存）
--     b) 2.0 の `file/ENGLISH_READING` ツリーを配備先にコピーし、
--        `study21.reading.legacy-storage-root` をそこへ向ける（読取専用で解決）
--   のどちらでも読めるようにしてある。この移行では**メタ情報だけ**を引き継ぎ、
--   実体が無い場合は `GET /books/{id}/pdf` が 404 になり、画面は「PDF 未登録」と
--   2.0 の元ファイル名を出す（どのファイルを持ってくればよいか分かるように）。
--
-- 冪等性:
--   * 分類: 同じ 分類名（全体の分類）が無いときだけ入れる（`WHERE NOT EXISTS`）。
--   * 分類ID の設定: 分類ID が NULL の書籍だけに入れる（画面で付け替えた値を壊さない）。
--   * ファイル: UNIQUE (書籍ID, ファイル区分) に ON CONFLICT DO NOTHING。
--   再実行しても件数は増えない。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS dblink;

-- ---------------------------------------------------------------------------
-- 1. 分類マスタの初期値（実データから導いた 3 分類）
-- ---------------------------------------------------------------------------
-- 2026-09-14 の追補（TBL_RED_書籍分類情報.sql）で一意制約が「分類名」から
-- 「(COALESCE(所有家族学生ID,0), 分類名)」に変わったため、`ON CONFLICT ("分類名")` は
-- 使えない（再実行すると「一致する一意制約が無い」でエラーになる）。
-- どの環境でも再実行できるように `WHERE NOT EXISTS` の形にする。
INSERT INTO public."RED_書籍分類情報" ("分類名", "表示順", "説明", "登録元コード")
SELECT v."分類名", v."表示順", v."説明", 'MIGRATION'
  FROM (VALUES ('英語 小説',        1, '英語の物語（2.0 のタグ Novel）'),
               ('英語 リーディング', 2, '英語の読み物・ガイド'),
               ('中国語',           3, '中国語の書籍')) AS v("分類名", "表示順", "説明")
 WHERE NOT EXISTS (
        SELECT 1
          FROM public."RED_書籍分類情報" c
         WHERE c."分類名" = v."分類名"
           AND c."所有家族学生ID" IS NULL
       );

-- ---------------------------------------------------------------------------
-- 2. 書籍に分類を付ける（分類ID が NULL の行だけ）
--    手掛かりは 2.0 の タグ。タグが無い英語の本は「英語 リーディング」に入れる。
-- ---------------------------------------------------------------------------
UPDATE public."RED_書籍情報" b
   SET "分類ID" = c."分類ID"
  FROM public."RED_書籍分類情報" c
 WHERE b."分類ID" IS NULL
   AND c."分類名" = CASE
         WHEN b."タグ" ILIKE '%中国語%' THEN '中国語'
         WHEN b."タグ" ILIKE '%Novel%' THEN '英語 小説'
         ELSE '英語 リーディング'
       END;

-- ---------------------------------------------------------------------------
-- 3. 2.0 の PDF / 表紙のメタ情報を読む（実体は 2.0 側にある）
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE migr_red_file ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study2',
       $remote$
       SELECT "機能番号"      AS book_no,
              "ファイル区分"  AS file_kind,
              "元ファイル名称" AS original_name,
              "ファイル名称"  AS stored_name,
              "パス"          AS stored_path,
              "MIME_TYPE"     AS mime_type,
              "登録日時"      AS created_at
         FROM "COM_ファイル情報"
        WHERE "機能区分" = 'ENGLISH_READING'
          AND "ファイル区分" IN ('PDF', 'COVER')
          AND COALESCE("ファイル名称", '') <> ''
       $remote$
       ) AS remote(book_no TEXT, file_kind TEXT, original_name TEXT, stored_name TEXT,
                   stored_path TEXT, mime_type TEXT, created_at TIMESTAMP);

-- パスは末尾に '/' を付けて正規化する（2.1 はディレクトリとして扱う）
UPDATE migr_red_file
   SET stored_path = COALESCE(NULLIF(BTRIM(COALESCE(stored_path, '')), ''), 'file/ENGLISH_READING/');
UPDATE migr_red_file
   SET stored_path = stored_path || '/'
 WHERE RIGHT(stored_path, 1) <> '/';

-- ---------------------------------------------------------------------------
-- 4. 書籍に紐づけて取り込む（書籍番号 経由。引けない行は SKIP して NOTICE）
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    v_total   INTEGER;
    v_matched INTEGER;
    v_skipped INTEGER;
BEGIN
    SELECT count(*) INTO v_total FROM migr_red_file;
    SELECT count(*) INTO v_matched
      FROM migr_red_file f
      JOIN public."RED_書籍情報" b ON b."書籍番号" = f.book_no;
    v_skipped := v_total - v_matched;
    IF v_skipped > 0 THEN
        RAISE NOTICE '書籍番号 が 2.1 に無いファイル情報 % 件は移行しません（全体 % 件）', v_skipped, v_total;
    END IF;
END $$;

INSERT INTO public."RED_書籍ファイル情報"
    ("書籍ID", "ファイル区分", "元ファイル名称", "保存ファイル名", "保存パス", "MIME_TYPE",
     "ファイルサイズ", "ページ数", "登録元コード", "登録日時", "更新日時")
SELECT b."書籍ID",
       CASE f.file_kind WHEN 'PDF' THEN 'PDF' ELSE 'COVER' END,
       NULLIF(BTRIM(COALESCE(f.original_name, '')), ''),
       f.stored_name,
       f.stored_path,
       NULLIF(BTRIM(COALESCE(f.mime_type, '')), ''),
       NULL,   -- 2.0 の COM_ファイル情報 にサイズ列が無い
       NULL,   -- ページ数は 2.0 に無い（書籍の 総ページ数 を使う）
       'MIGRATION',
       COALESCE(f.created_at, CURRENT_TIMESTAMP),
       COALESCE(f.created_at, CURRENT_TIMESTAMP)
  FROM migr_red_file f
  JOIN public."RED_書籍情報" b ON b."書籍番号" = f.book_no
ON CONFLICT ("書籍ID", "ファイル区分") DO NOTHING;

-- ---------------------------------------------------------------------------
-- 5. 結果確認
-- ---------------------------------------------------------------------------
\echo '--- 分類ごとの冊数'
SELECT c."表示順", c."分類名", count(b."書籍ID") AS 冊数
  FROM public."RED_書籍分類情報" c
  LEFT JOIN public."RED_書籍情報" b ON b."分類ID" = c."分類ID"
 GROUP BY c."表示順", c."分類名"
 ORDER BY c."表示順";
\echo '--- 未分類の冊数（0 が期待値）'
SELECT count(*) AS 未分類 FROM public."RED_書籍情報" WHERE "分類ID" IS NULL;
\echo '--- ファイル情報（区分ごと）'
SELECT "ファイル区分", count(*) AS 件数, count("ファイルサイズ") AS サイズ有, count("ページ数") AS ページ数有
  FROM public."RED_書籍ファイル情報" GROUP BY "ファイル区分" ORDER BY "ファイル区分";
\echo '--- 書籍ごとの登録状況（2.0 の元ファイル名つき）'
SELECT b."書籍番号", b."書籍名", c."分類名",
       COALESCE(p."元ファイル名称", '(PDF なし)') AS "PDF",
       COALESCE(v."元ファイル名称", '(表紙なし)') AS "表紙"
  FROM public."RED_書籍情報" b
  LEFT JOIN public."RED_書籍分類情報" c ON c."分類ID" = b."分類ID"
  LEFT JOIN public."RED_書籍ファイル情報" p ON p."書籍ID" = b."書籍ID" AND p."ファイル区分" = 'PDF'
  LEFT JOIN public."RED_書籍ファイル情報" v ON v."書籍ID" = b."書籍ID" AND v."ファイル区分" = 'COVER'
 ORDER BY c."表示順", b."書籍ID";

COMMIT;
