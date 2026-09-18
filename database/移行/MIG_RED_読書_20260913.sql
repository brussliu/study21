-- ============================================================================
-- Study 2.0 -> 2.1  読書管理 移行
-- 移行元: study2.public."TRN_英語読書書籍情報"   （書籍   6 冊）
--         study2.public."TRN_英語読書記録情報"   （読書記録 326 件）
--         study2.public."TRN_英語読書標記情報"   （標記   838 件）
-- 移行先: study21.public."RED_書籍情報" / "RED_読書記録情報" / "RED_標記情報"
-- ----------------------------------------------------------------------------
-- 事前条件:
--   1. database/読書管理/TBL_RED_書籍情報.sql・TBL_RED_読書記録情報.sql・
--      TBL_RED_標記情報.sql を study21 に適用しておくこと（適用済み）。
--   2. 移行元・移行先 DB が同一 PostgreSQL インスタンスに存在すること（dblink で接続）。
--
-- 実行例:
--   psql -h 192.168.0.100 -p 54320 -U postgres -d study21 -v ON_ERROR_STOP=1 \
--        -f database/移行/MIG_RED_読書_20260913.sql
--
-- 値の対応:
--   書籍:
--     書籍番号 = 2.0 の 書籍ID（'ER-20260402-124745'）。2.1 の 書籍ID は BIGSERIAL で採番し直す
--       （記録・標記の親は 書籍番号 経由で 2.1 の 書籍ID に読み替える）。
--     教科・書籍名・作者・難易度・読書ステータス・総ページ数・現在ページ・置頂フラグ・
--     概要・最近の読書時間分・累計読書時間分・累計標記件数・最終読書日時・備考 はそのまま。
--     タグ: 2.0 は「未設定」が NULL と 空文字 の両方ありうるので、空文字は NULL に寄せる
--       （2.1 は「未設定は NULL」。実データ 6 冊では NULL のみ）。
--     登録者/更新者アカウントID: 2.0 の 登録ID/更新ID を下の対応表で引く。
--       **2.0 の読書管理の実データは全件 'english_reading'**（人ではなく画面/機能の名前）で
--       対応表に無いため、実データでは全て NULL になる。行は捨てない。
--       対応表の 'liu' → 1 / 'ljz' → 2 は TODO・日報・学習状況モニターと同じ判断
--       （ACC_アカウント の 1 = bruss.ji.liu@gmail.com＝保護者 / 2 = ricky.jingze@gmail.com＝生徒）。
--     登録元コード='MIGRATION' / 更新元コード=NULL / バージョン=1。
--     登録日時/更新日時: 2.0 の値をそのまま使う。NULL なら 最終読書日時 → CURRENT_TIMESTAMP の
--       順で埋める（2.1 は NOT NULL）。実データは 6 冊とも 登録日時 があるため、
--       このフォールバックは使われない（最終読書日時 が NULL の 3 冊も 登録日時 を引き継ぐ）。
--   記録:
--     書籍ID は 書籍番号（= 2.0 の 書籍ID）経由で 2.1 の 書籍ID（BIGINT）へ読み替える。
--     2.1 に同じ 書籍番号 が無い行は**移行しない（SKIP）**。件数は真ん中の DO ブロックで
--     NOTICE に出し、最後の件数検証は「読み替えできた行」と比較する（黙って減らさない）。
--     読書日時・開始ページ・終了ページ・読書時間分・標記件数・メモ はそのまま。
--     登録者/更新者アカウントID は NULL（2.0 の 登録ID は 'english_reading' で実行者が特定できない）。
--     登録元コード='MIGRATION' / 更新元コード=NULL / 登録日時・更新日時=CURRENT_TIMESTAMP
--       （業務日時は 読書日時 が持っているため、登録日時は移行した時刻にする）。
--   標記:
--     同じく 書籍ID を読み替える（引けない行は SKIP。件数は NOTICE）。
--     ページ番号・標記種別・対象文字・標記内容・色・太さ・位置X・位置Y・幅・高さ・描画データ・
--     表示順 はそのまま。位置X/Y・幅・高さ・描画データ は **2.1 の表示では使わない**が、
--     2.0 のデータを捨てないために保持する（TBL_RED_標記情報.sql のコメント参照）。
--     実データは 太さ が全件 NULL（2.0 の既定は 1 だが値が入っていない）なので NULL のまま。
--     監査は 記録 と同じ（アカウントID は NULL・登録元コード='MIGRATION'・日時は CURRENT_TIMESTAMP）。
--
-- 冪等性:
--   * 書籍: 書籍番号 の UNIQUE に ON CONFLICT ("書籍番号") DO NOTHING。
--   * 記録: 2.0 に自然キーが無い（記録SEQ は 2.1 に持ち込まない）ため、WHERE NOT EXISTS で
--     「書籍ID・読書日時・開始ページ・終了ページ・読書時間分・メモ」が同じ行を飛ばす。
--   * 標記: 同じく WHERE NOT EXISTS で
--     「書籍ID・ページ番号・標記種別・対象文字・標記内容」が同じ行を飛ばす。
--   再実行しても件数は増えない（2 回実行して確認済み）。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS dblink;

-- ---------------------------------------------------------------------------
-- 0. 2.0 の実行者（登録ID/更新ID）→ 2.1 のアカウント 対応表
--    他の移行（TODO・日報・学習状況モニター）と同じ判断。引けない ID は NULL にする。
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE migr_red_owner ("旧ユーザーID" TEXT PRIMARY KEY, "アカウントID" BIGINT) ON COMMIT DROP;
INSERT INTO migr_red_owner VALUES ('ljz', 2), ('liu', 1);

-- ---------------------------------------------------------------------------
-- 1. 2.0 の書籍を読む
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE migr_red_book ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study2',
       $remote$
       SELECT "書籍ID", "教科", "書籍名", "作者", "難易度", "読書ステータス",
              "総ページ数", "現在ページ", "置頂フラグ", "タグ", "概要",
              "最近の読書時間分", "累計読書時間分", "累計標記件数", "最終読書日時", "備考",
              COALESCE("登録ID", ''), COALESCE("更新ID", ''), "登録日時", "更新日時"
         FROM public."TRN_英語読書書籍情報"
       $remote$
       ) AS remote (
           "書籍ID" VARCHAR(30),
           "教科" VARCHAR(20),
           "書籍名" VARCHAR(150),
           "作者" VARCHAR(120),
           "難易度" VARCHAR(20),
           "読書ステータス" VARCHAR(20),
           "総ページ数" INTEGER,
           "現在ページ" INTEGER,
           "置頂フラグ" BOOLEAN,
           "タグ" TEXT,
           "概要" TEXT,
           "最近の読書時間分" INTEGER,
           "累計読書時間分" INTEGER,
           "累計標記件数" INTEGER,
           "最終読書日時" TIMESTAMP,
           "備考" TEXT,
           "登録ID" VARCHAR(20),
           "更新ID" VARCHAR(20),
           "登録日時" TIMESTAMP,
           "更新日時" TIMESTAMP
       );

-- ---------------------------------------------------------------------------
-- 2. 2.0 の読書記録を読む
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE migr_red_record ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study2',
       $remote$
       SELECT "記録SEQ", "書籍ID", "読書日時", "開始ページ", "終了ページ",
              "読書時間分", "標記件数", "メモ"
         FROM public."TRN_英語読書記録情報"
       $remote$
       ) AS remote (
           "記録SEQ" BIGINT,
           "書籍ID" VARCHAR(30),
           "読書日時" TIMESTAMP,
           "開始ページ" INTEGER,
           "終了ページ" INTEGER,
           "読書時間分" INTEGER,
           "標記件数" INTEGER,
           "メモ" TEXT
       );

-- ---------------------------------------------------------------------------
-- 3. 2.0 の標記を読む
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE migr_red_mark ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study2',
       $remote$
       SELECT "標記SEQ", "書籍ID", "ページ番号", "標記種別", "対象文字", "標記内容",
              "色", "太さ", "位置X", "位置Y", "幅", "高さ", "描画データ", "表示順"
         FROM public."TRN_英語読書標記情報"
       $remote$
       ) AS remote (
           "標記SEQ" BIGINT,
           "書籍ID" VARCHAR(30),
           "ページ番号" INTEGER,
           "標記種別" VARCHAR(20),
           "対象文字" TEXT,
           "標記内容" TEXT,
           "色" VARCHAR(20),
           "太さ" INTEGER,
           "位置X" NUMERIC(10, 6),
           "位置Y" NUMERIC(10, 6),
           "幅" NUMERIC(10, 6),
           "高さ" NUMERIC(10, 6),
           "描画データ" TEXT,
           "表示順" INTEGER
       );

-- ---------------------------------------------------------------------------
-- 4. 移行前の検査（壊れた値はここで止める）
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    book_rows BIGINT;
    record_rows BIGINT;
    mark_rows BIGINT;
BEGIN
    SELECT COUNT(*) INTO book_rows FROM migr_red_book;
    SELECT COUNT(*) INTO record_rows FROM migr_red_record;
    SELECT COUNT(*) INTO mark_rows FROM migr_red_mark;
    RAISE NOTICE '2.0 の読書管理: 書籍 % 冊 / 読書記録 % 件 / 標記 % 件を移行します',
        book_rows, record_rows, mark_rows;

    -- 書籍
    IF EXISTS (SELECT 1 FROM migr_red_book WHERE BTRIM(COALESCE("書籍名", '')) = '') THEN
        RAISE EXCEPTION '2.0 の書籍に 書籍名 が空の行があります';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_red_book WHERE BTRIM(COALESCE("作者", '')) = '') THEN
        RAISE EXCEPTION '2.0 の書籍に 作者 が空の行があります';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_red_book WHERE "難易度" NOT IN ('Starter', 'Elementary', 'Intermediate', 'Upper')) THEN
        RAISE EXCEPTION '2.0 の書籍に 2.1 が知らない 難易度 があります';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_red_book
                WHERE "読書ステータス" NOT IN ('未着手', '読書中', '一時停止', '読了')) THEN
        RAISE EXCEPTION '2.0 の書籍に 2.1 が知らない 読書ステータス があります';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_red_book WHERE "総ページ数" IS NULL OR "総ページ数" < 1) THEN
        RAISE EXCEPTION '2.0 の書籍に 総ページ数 が不正な行があります';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_red_book WHERE COALESCE("現在ページ", 1) < 1) THEN
        RAISE EXCEPTION '2.0 の書籍に 現在ページ が不正な行があります';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_red_book
                WHERE COALESCE("最近の読書時間分", 0) < 0
                   OR COALESCE("累計読書時間分", 0) < 0
                   OR COALESCE("累計標記件数", 0) < 0) THEN
        RAISE EXCEPTION '2.0 の書籍に負の時間・件数があります';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_red_book WHERE LENGTH("書籍ID") > 30) THEN
        RAISE EXCEPTION '2.0 の 書籍ID が 書籍番号 VARCHAR(30) に入りません';
    END IF;

    -- 読書記録
    IF EXISTS (SELECT 1 FROM migr_red_record WHERE "読書日時" IS NULL) THEN
        RAISE EXCEPTION '2.0 の読書記録に 読書日時 が NULL の行があります';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_red_record WHERE COALESCE("読書時間分", 0) < 0) THEN
        RAISE EXCEPTION '2.0 の読書記録に負の 読書時間分 があります';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_red_record WHERE "開始ページ" < 1 OR "終了ページ" < 1) THEN
        RAISE EXCEPTION '2.0 の読書記録に 0 以下のページがあります';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_red_record WHERE "終了ページ" < "開始ページ") THEN
        RAISE EXCEPTION '2.0 の読書記録に ページ範囲が逆の行があります';
    END IF;
    -- 自然キー（冪等の判定に使う）が重複していると、再実行の判定が曖昧になる
    IF (SELECT COUNT(*) FROM migr_red_record) <>
       (SELECT COUNT(*) FROM (SELECT DISTINCT "書籍ID", "読書日時",
                                     COALESCE("開始ページ", -1), COALESCE("終了ページ", -1),
                                     "読書時間分", COALESCE("メモ", '')
                                FROM migr_red_record) d) THEN
        RAISE EXCEPTION '2.0 の読書記録に自然キーが重複する行があります（冪等の判定ができません）';
    END IF;

    -- 標記
    IF EXISTS (SELECT 1 FROM migr_red_mark
                WHERE "標記種別" NOT IN ('highlight', 'underline', 'memo', 'vocabulary', 'pen')) THEN
        RAISE EXCEPTION '2.0 の標記に 2.1 が知らない 標記種別 があります';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_red_mark WHERE "ページ番号" IS NULL OR "ページ番号" < 1) THEN
        RAISE EXCEPTION '2.0 の標記に ページ番号 が不正な行があります';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_red_mark
                WHERE "位置X" NOT BETWEEN 0 AND 1 OR "位置Y" NOT BETWEEN 0 AND 1
                   OR "幅" NOT BETWEEN 0 AND 1 OR "高さ" NOT BETWEEN 0 AND 1) THEN
        RAISE EXCEPTION '2.0 の標記に 0-1 の範囲外の座標があります';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_red_mark WHERE "太さ" IS NOT NULL AND "太さ" < 1) THEN
        RAISE EXCEPTION '2.0 の標記に 太さ が不正な行があります';
    END IF;
    IF (SELECT COUNT(*) FROM migr_red_mark) <>
       (SELECT COUNT(*) FROM (SELECT DISTINCT "書籍ID", "ページ番号", "標記種別",
                                     COALESCE("対象文字", ''), COALESCE("標記内容", '')
                                FROM migr_red_mark) d) THEN
        RAISE EXCEPTION '2.0 の標記に自然キーが重複する行があります（冪等の判定ができません）';
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 5. 書籍
-- ---------------------------------------------------------------------------
INSERT INTO public."RED_書籍情報" (
    "書籍番号", "教科", "書籍名", "作者", "難易度", "読書ステータス",
    "総ページ数", "現在ページ", "置頂フラグ", "タグ", "概要",
    "最近の読書時間分", "累計読書時間分", "累計標記件数", "最終読書日時", "備考",
    "バージョン", "登録者アカウントID", "更新者アカウントID",
    "登録元コード", "更新元コード", "登録日時", "更新日時"
)
SELECT source."書籍ID",                                   -- 書籍番号（2.0 の 書籍ID）
       COALESCE(NULLIF(BTRIM(source."教科"), ''), '英語'),
       source."書籍名",
       source."作者",
       source."難易度",
       source."読書ステータス",
       source."総ページ数",
       source."現在ページ",
       COALESCE(source."置頂フラグ", FALSE),
       NULLIF(BTRIM(COALESCE(source."タグ", '')), ''),    -- 空文字は NULL（未設定）
       source."概要",
       COALESCE(source."最近の読書時間分", 0),
       COALESCE(source."累計読書時間分", 0),
       COALESCE(source."累計標記件数", 0),
       source."最終読書日時",
       source."備考",
       1,
       reg."アカウントID",
       upd."アカウントID",
       'MIGRATION', NULL,
       COALESCE(source."登録日時", source."最終読書日時", CURRENT_TIMESTAMP),
       COALESCE(source."更新日時", source."最終読書日時", CURRENT_TIMESTAMP)
  FROM migr_red_book source
  LEFT JOIN migr_red_owner reg ON reg."旧ユーザーID" = source."登録ID"
  LEFT JOIN migr_red_owner upd ON upd."旧ユーザーID" = source."更新ID"
 ON CONFLICT ("書籍番号") DO NOTHING;

-- ---------------------------------------------------------------------------
-- 6. 読書記録（書籍番号 → 2.1 の 書籍ID に読み替え）
--    引けない行（2.1 に同じ 書籍番号 が無い）は入れない。件数は下の DO で NOTICE に出す。
-- ---------------------------------------------------------------------------
INSERT INTO public."RED_読書記録情報" (
    "書籍ID", "読書日時", "開始ページ", "終了ページ", "読書時間分", "標記件数", "メモ",
    "登録者アカウントID", "更新者アカウントID",
    "登録元コード", "更新元コード", "登録日時", "更新日時"
)
SELECT book."書籍ID",
       source."読書日時",
       source."開始ページ",
       source."終了ページ",
       COALESCE(source."読書時間分", 0),
       COALESCE(source."標記件数", 0),
       source."メモ",
       NULL, NULL,
       'MIGRATION', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  FROM migr_red_record source
  JOIN public."RED_書籍情報" book ON book."書籍番号" = source."書籍ID"
 WHERE NOT EXISTS (
       SELECT 1
         FROM public."RED_読書記録情報" target
        WHERE target."書籍ID" = book."書籍ID"
          AND target."読書日時" = source."読書日時"
          AND COALESCE(target."開始ページ", -1) = COALESCE(source."開始ページ", -1)
          AND COALESCE(target."終了ページ", -1) = COALESCE(source."終了ページ", -1)
          AND target."読書時間分" = COALESCE(source."読書時間分", 0)
          AND COALESCE(target."メモ", '') = COALESCE(source."メモ", '')
       );

-- ---------------------------------------------------------------------------
-- 7. 標記（書籍番号 → 2.1 の 書籍ID に読み替え）
-- ---------------------------------------------------------------------------
INSERT INTO public."RED_標記情報" (
    "書籍ID", "ページ番号", "標記種別", "対象文字", "標記内容",
    "色", "太さ", "位置X", "位置Y", "幅", "高さ", "描画データ", "表示順",
    "登録者アカウントID", "更新者アカウントID",
    "登録元コード", "更新元コード", "登録日時", "更新日時"
)
SELECT book."書籍ID",
       source."ページ番号",
       source."標記種別",
       source."対象文字",
       source."標記内容",
       source."色",
       source."太さ",
       source."位置X",
       source."位置Y",
       source."幅",
       source."高さ",
       source."描画データ",
       COALESCE(source."表示順", 0),
       NULL, NULL,
       'MIGRATION', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  FROM migr_red_mark source
  JOIN public."RED_書籍情報" book ON book."書籍番号" = source."書籍ID"
 WHERE NOT EXISTS (
       SELECT 1
         FROM public."RED_標記情報" target
        WHERE target."書籍ID" = book."書籍ID"
          AND target."ページ番号" = source."ページ番号"
          AND target."標記種別" = source."標記種別"
          AND COALESCE(target."対象文字", '') = COALESCE(source."対象文字", '')
          AND COALESCE(target."標記内容", '') = COALESCE(source."標記内容", '')
       );

-- ---------------------------------------------------------------------------
-- 8. 件数検証（読み替えできた行と移行先を突き合わせる）
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    src_books BIGINT;
    dst_books BIGINT;
    src_records BIGINT;
    dst_records BIGINT;
    skip_records BIGINT;
    src_marks BIGINT;
    dst_marks BIGINT;
    skip_marks BIGINT;
BEGIN
    SELECT COUNT(*) INTO src_books FROM migr_red_book;
    SELECT COUNT(*) INTO dst_books
      FROM public."RED_書籍情報" target
      JOIN migr_red_book source ON source."書籍ID" = target."書籍番号";
    IF src_books <> dst_books THEN
        RAISE EXCEPTION '書籍の件数が一致しません: 移行元 % / 移行先 %', src_books, dst_books;
    END IF;

    -- 書籍番号が引けない記録（SKIP した行）
    SELECT COUNT(*) INTO skip_records
      FROM migr_red_record source
     WHERE NOT EXISTS (SELECT 1 FROM public."RED_書籍情報" b WHERE b."書籍番号" = source."書籍ID");
    IF skip_records > 0 THEN
        RAISE NOTICE '書籍番号が引けない読書記録 % 件は移行しません（SKIP）', skip_records;
    END IF;

    SELECT COUNT(*) INTO src_records
      FROM migr_red_record source
      JOIN public."RED_書籍情報" b ON b."書籍番号" = source."書籍ID";
    SELECT COUNT(*) INTO dst_records
      FROM public."RED_読書記録情報" target
      JOIN public."RED_書籍情報" book ON book."書籍ID" = target."書籍ID"
      JOIN migr_red_record source
        ON source."書籍ID" = book."書籍番号"
       AND source."読書日時" = target."読書日時"
       AND COALESCE(source."開始ページ", -1) = COALESCE(target."開始ページ", -1)
       AND COALESCE(source."終了ページ", -1) = COALESCE(target."終了ページ", -1)
       AND COALESCE(source."読書時間分", 0) = target."読書時間分"
       AND COALESCE(source."メモ", '') = COALESCE(target."メモ", '');
    IF src_records <> dst_records THEN
        RAISE EXCEPTION '読書記録の件数が一致しません: 移行元 % / 移行先 %', src_records, dst_records;
    END IF;

    SELECT COUNT(*) INTO skip_marks
      FROM migr_red_mark source
     WHERE NOT EXISTS (SELECT 1 FROM public."RED_書籍情報" b WHERE b."書籍番号" = source."書籍ID");
    IF skip_marks > 0 THEN
        RAISE NOTICE '書籍番号が引けない標記 % 件は移行しません（SKIP）', skip_marks;
    END IF;

    SELECT COUNT(*) INTO src_marks
      FROM migr_red_mark source
      JOIN public."RED_書籍情報" b ON b."書籍番号" = source."書籍ID";
    SELECT COUNT(*) INTO dst_marks
      FROM public."RED_標記情報" target
      JOIN public."RED_書籍情報" book ON book."書籍ID" = target."書籍ID"
      JOIN migr_red_mark source
        ON source."書籍ID" = book."書籍番号"
       AND source."ページ番号" = target."ページ番号"
       AND source."標記種別" = target."標記種別"
       AND COALESCE(source."対象文字", '') = COALESCE(target."対象文字", '')
       AND COALESCE(source."標記内容", '') = COALESCE(target."標記内容", '');
    IF src_marks <> dst_marks THEN
        RAISE EXCEPTION '標記の件数が一致しません: 移行元 % / 移行先 %', src_marks, dst_marks;
    END IF;

    RAISE NOTICE '読書管理の移行完了: 書籍 % 冊 / 読書記録 % 件（SKIP %）/ 標記 % 件（SKIP %）',
        dst_books, dst_records, skip_records, dst_marks, skip_marks;
END $$;

-- ---------------------------------------------------------------------------
-- 9. 採番（BIGSERIAL）を移行した最大値に合わせる
-- ---------------------------------------------------------------------------
SELECT SETVAL(
    PG_GET_SERIAL_SEQUENCE('public."RED_書籍情報"', '書籍ID'),
    COALESCE((SELECT MAX("書籍ID") FROM public."RED_書籍情報"), 1),
    EXISTS (SELECT 1 FROM public."RED_書籍情報")
);

SELECT SETVAL(
    PG_GET_SERIAL_SEQUENCE('public."RED_読書記録情報"', '記録ID'),
    COALESCE((SELECT MAX("記録ID") FROM public."RED_読書記録情報"), 1),
    EXISTS (SELECT 1 FROM public."RED_読書記録情報")
);

SELECT SETVAL(
    PG_GET_SERIAL_SEQUENCE('public."RED_標記情報"', '標記ID'),
    COALESCE((SELECT MAX("標記ID") FROM public."RED_標記情報"), 1),
    EXISTS (SELECT 1 FROM public."RED_標記情報")
);

COMMIT;

-- ============================================================================
-- 移行結果の確認（ここから下は読み取りのみ。実行しなくてもよい）
-- ============================================================================

\echo '--- 移行結果: 件数 ---'
SELECT (SELECT COUNT(*) FROM public."RED_書籍情報")     AS "書籍",
       (SELECT COUNT(*) FROM public."RED_読書記録情報") AS "読書記録",
       (SELECT COUNT(*) FROM public."RED_標記情報")     AS "標記";

\echo '--- 移行結果: 書籍ごとの記録・標記の内訳 ---'
SELECT b."書籍番号", b."書籍名",
       (SELECT COUNT(*) FROM public."RED_読書記録情報" r WHERE r."書籍ID" = b."書籍ID") AS records,
       (SELECT COUNT(*) FROM public."RED_標記情報" m WHERE m."書籍ID" = b."書籍ID")     AS marks,
       b."累計標記件数"
  FROM public."RED_書籍情報" b
 ORDER BY 1;

\echo '--- 移行結果: 標記種別 ---'
SELECT "標記種別", COUNT(*) AS "件数" FROM public."RED_標記情報" GROUP BY 1 ORDER BY 2 DESC;
