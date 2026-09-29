-- ============================================================================
-- Study 2.0 -> 2.1  英作文AI添削のデータ移行
-- ----------------------------------------------------------------------------
-- 移行元: study2.public.STY_英作文情報 / STY_英作文画像情報
-- 移行先: study21.public.ENG_英作文情報 / ENG_英作文画像情報 / ENG_AI添削履歴情報
--
--   同一 PostgreSQL インスタンス（192.168.0.100:54320）に study2 と study21 が同居しているので
--   dblink('dbname=study2', …) で読む（日本語勉強の移行と同じやり方）。
--
-- 実行例:
--   psql -h 192.168.0.100 -p 54320 -U postgres -d study21 -v ON_ERROR_STOP=1 \
--        -f database/移行/MIG_ENG_英作文_2.0から移行_20260927.sql
--
-- 事前条件:
--   1. database/英作文/TBL_ENG_*.sql を適用済み（テーブルが在ること）
--   2. study2 の STY_英作文情報 / STY_英作文画像情報 が在ること
--
-- 移行元の実測（2026-09-27）:
--   STY_英作文情報       35 件（liu 27 / ljz 8。状態 A 21 / X 14）… 全件 批改結果JSON あり
--   STY_英作文画像情報   83 件（question 39 / answer 37 / auto 5 / both 2）
--   うち 24 件は 批改結果JSON が**新しい形**（rubricValues / rubricMax / modelAnswer あり）、
--   11 件は**古い形**（それらが無い）。→ **JSON はそのまま移す**。画面側の
--   `normalizeGradingReport()` が両方の形を読む（足りない部分は出さない＝偽の値をでっち上げない）。
--
-- 対応付けの規則:
--   * ユーザーID 'liu'（保護者 bruss.ji.liu@gmail.com）→ アカウント 1
--     'ljz'（生徒 ricky.jingze@gmail.com）→ アカウント 2
--     （日本語勉強の移行（MIG_JPN_日本語勉強_20260913.sql）と同じ対応）
--   * 英作文ID / 英作文画像ID は**そのまま使う**（追跡できるように）。移行後にシーケンスを進める。
--   * 添削は「第 1 回」として 1 行積む（2.0 は 1 列を上書きしていたので、常に 1 回分しか無い）。
--   * 画像区分 'auto' / 'both' は 'question' にする（2.1 の画面は 2 択のため）。
--     'both'（設問と答案が同じ紙）は設問側に寄せる。元の値はここでは失われる（必要なら別途）。
--   * 画像の実体は**別途コピーが要る**（§末尾）。
--
-- 何度流しても同じ（NOT EXISTS / ON CONFLICT で二重に入れない）。
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS dblink;

-- ---- 移行元を 1 か所で読む（列の型を明示する） ----------------------------
-- （毎回 dblink を書くと長いので CTE にはせず、必要な列だけを都度引く）

-- ---- 1) 作文 ----------------------------------------------------------------
INSERT INTO public."ENG_英作文情報" (
    "英作文ID", "利用者アカウントID", "英検級", "題", "題_中国語", "設問文", "作文本文", "語数",
    "状態コード", "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時")
SELECT s."英作文ID",
       CASE s."ユーザーID" WHEN 'liu' THEN 1 WHEN 'ljz' THEN 2 END,
       CASE upper(coalesce(s."英検級", ''))
           WHEN 'GRADE1' THEN 'GRADE1' WHEN 'PRE1' THEN 'PRE1' WHEN 'GRADE2' THEN 'GRADE2'
           ELSE 'GRADE2' END,
       coalesce(nullif(btrim(s."タイトル_日本語"), ''), nullif(btrim(s."タイトル"), ''), '英作文'),
       nullif(btrim(s."タイトル_中国語"), ''),
       nullif(btrim(s."設問文"), ''),
       nullif(btrim(s."作文本文"), ''),
       coalesce(s."語数", 0),
       CASE WHEN upper(coalesce(s."状態", 'A')) = 'X' THEN 'X' ELSE 'A' END,
       NULL, NULL, 'MIGRATION', NULL,
       coalesce(s."作成日時", CURRENT_TIMESTAMP),
       coalesce(s."更新日時", s."作成日時", CURRENT_TIMESTAMP)
  FROM dblink('dbname=study2',
           'SELECT "英作文ID", "ユーザーID", "英検級", "タイトル", "タイトル_日本語", "タイトル_中国語",
                   "設問文", "作文本文", "語数", "状態", "作成日時", "更新日時"
              FROM public."STY_英作文情報"')
       AS s("英作文ID" bigint, "ユーザーID" text, "英検級" text, "タイトル" text,
            "タイトル_日本語" text, "タイトル_中国語" text, "設問文" text, "作文本文" text,
            "語数" integer, "状態" text, "作成日時" timestamp, "更新日時" timestamp)
 WHERE s."ユーザーID" IN ('liu', 'ljz')
   AND NOT EXISTS (SELECT 1 FROM public."ENG_英作文情報" e WHERE e."英作文ID" = s."英作文ID");

-- 2.0 に居るが対応の無い利用者は知らせる（黙って落とさない）
DO $$
DECLARE skipped integer;
BEGIN
    SELECT count(*) INTO skipped
      FROM dblink('dbname=study2', 'SELECT DISTINCT "ユーザーID" FROM public."STY_英作文情報"')
           AS s("ユーザーID" text)
     WHERE s."ユーザーID" NOT IN ('liu', 'ljz');
    IF skipped > 0 THEN
        RAISE NOTICE '対応の無い ユーザーID が % 種類あります（その作文は移行していません）', skipped;
    END IF;
END $$;

-- ---- 2) 画像（実体は別途コピー。§末尾） ------------------------------------
INSERT INTO public."ENG_英作文画像情報" (
    "英作文画像ID", "英作文ID", "表示順", "画像区分", "原本ファイル名", "保存ファイル名",
    "相対パス", "MIMEタイプ", "ファイルサイズ", "登録者アカウントID", "登録日時")
SELECT i."英作文画像ID",
       i."英作文ID",
       coalesce(i."表示順", 1),
       CASE lower(coalesce(i."画像区分", '')) WHEN 'answer' THEN 'answer' ELSE 'question' END,
       i."原ファイル名",
       i."保存ファイル名",
       -- 2.0 は <root>/english-essay/<相対パス>/<保存ファイル名> だった。
       -- 2.1 は新しい規約（english-essay/{アカウントID}/{yyyyMM}/…）なので、旧データは legacy の下に置く。
       'english-essay/legacy/'
           || coalesce(nullif(btrim(i."相対パス"), ''), 'unknown'),
       coalesce(nullif(btrim(i."MIMEタイプ"), ''), 'image/jpeg'),
       coalesce(i."ファイルサイズ", 0),
       e."利用者アカウントID",
       coalesce(i."作成日時", CURRENT_TIMESTAMP)
  FROM dblink('dbname=study2',
           'SELECT "英作文画像ID", "英作文ID", "表示順", "画像区分", "原ファイル名", "保存ファイル名",
                   "相対パス", "MIMEタイプ", "ファイルサイズ", "作成日時"
              FROM public."STY_英作文画像情報"')
       AS i("英作文画像ID" bigint, "英作文ID" bigint, "表示順" integer, "画像区分" text,
            "原ファイル名" text, "保存ファイル名" text, "相対パス" text, "MIMEタイプ" text,
            "ファイルサイズ" bigint, "作成日時" timestamp)
  JOIN public."ENG_英作文情報" e ON e."英作文ID" = i."英作文ID"
 WHERE NOT EXISTS (SELECT 1 FROM public."ENG_英作文画像情報" x WHERE x."英作文画像ID" = i."英作文画像ID");

-- ---- 3) 添削（2.0 の 1 列 → 第 1 回として積む） -----------------------------
INSERT INTO public."ENG_AI添削履歴情報" (
    "添削ID", "英作文ID", "回数", "状態コード", "英検級", "題_日本語", "題_中国語",
    "設問文", "作文本文", "語数", "総合得点", "満点", "添削結果JSON", "AI呼出履歴ID", "失敗理由",
    "開始日時", "終了日時", "登録者アカウントID", "登録日時")
SELECT nextval(pg_get_serial_sequence('public."ENG_AI添削履歴情報"', '添削ID')),
       s."英作文ID", 1, 'SUCCEEDED',
       e."英検級", e."題", e."題_中国語", e."設問文", e."作文本文", e."語数",
       -- 得点は列（2.0 の総合得点）を優先し、無ければ JSON から
       coalesce(s."総合得点", nullif(s."批改結果JSON" ->> 'score', '')::integer),
       coalesce(s."満点", nullif(s."批改結果JSON" ->> 'maxScore', '')::integer),
       -- **JSON はそのまま**（古い形と新しい形がある。画面側で正規化する）
       s."批改結果JSON",
       NULL, NULL, NULL,
       coalesce(s."更新日時", s."作成日時"),
       e."利用者アカウントID",
       coalesce(s."更新日時", s."作成日時", CURRENT_TIMESTAMP)
  FROM dblink('dbname=study2',
           'SELECT "英作文ID", "総合得点", "満点", "批改結果JSON", "作成日時", "更新日時"
              FROM public."STY_英作文情報"')
       AS s("英作文ID" bigint, "総合得点" integer, "満点" integer, "批改結果JSON" jsonb,
            "作成日時" timestamp, "更新日時" timestamp)
  JOIN public."ENG_英作文情報" e ON e."英作文ID" = s."英作文ID"
 WHERE s."批改結果JSON" IS NOT NULL
   -- OCR までで AI 添削をしていない行は入れない（status が OCR_* で得点も無い。2026-09-27 追加）
   AND (s."批改結果JSON" ->> 'status') IS DISTINCT FROM 'OCR_ONLY'
   AND (s."批改結果JSON" ->> 'status') IS DISTINCT FROM 'OCR_COMPLETED'
   AND (s."批改結果JSON" ->> 'status') IS DISTINCT FROM 'OCR_PENDING'
   AND NOT EXISTS (SELECT 1 FROM public."ENG_AI添削履歴情報" g
                    WHERE g."英作文ID" = s."英作文ID" AND g."回数" = 1);

-- ---- 4) シーケンスを進める（採番が 2.0 の ID と衝突しないように） ----------
SELECT setval(pg_get_serial_sequence('public."ENG_英作文情報"', '英作文ID'),
              (SELECT coalesce(max("英作文ID"), 1) FROM public."ENG_英作文情報"), true);
SELECT setval(pg_get_serial_sequence('public."ENG_英作文画像情報"', '英作文画像ID'),
              (SELECT coalesce(max("英作文画像ID"), 1) FROM public."ENG_英作文画像情報"), true);

-- ---- 5) 結果の確認 ---------------------------------------------------------
DO $$
DECLARE essays integer; images integer; gradings integer;
BEGIN
    SELECT count(*) INTO essays FROM public."ENG_英作文情報" WHERE "登録元コード" = 'MIGRATION';
    SELECT count(*) INTO images FROM public."ENG_英作文画像情報" i
      JOIN public."ENG_英作文情報" e ON e."英作文ID" = i."英作文ID"
     WHERE e."登録元コード" = 'MIGRATION';
    SELECT count(*) INTO gradings FROM public."ENG_AI添削履歴情報" g
      JOIN public."ENG_英作文情報" e ON e."英作文ID" = g."英作文ID"
     WHERE e."登録元コード" = 'MIGRATION';
    RAISE NOTICE '移行済み: 作文 % 件 / 画像 % 件 / 添削 % 件', essays, images, gradings;
END $$;

-- ============================================================================
-- 画像の実体（**別途コピーが要る**）
-- ----------------------------------------------------------------------------
-- 2.0: <catalina.base>/webapps/file/english-essay/<相対パス>/<保存ファイル名>
--      （例 .../file/english-essay/liu/6c8e720e-…/b3b6bf8c-….jpg）
-- 2.1: <study21.english-essay.storage-root>/english-essay/legacy/<相対パス>/<保存ファイル名>
--      （この移行で 相対パス に 'english-essay/legacy/' を前置している）
--
-- つまり 2.0 の `file/english-essay/` の中身を、2.1 の
-- `<storage-root>/english-essay/legacy/` へ**そのままの階層でコピー**すればよい。
-- コピー用のスクリプト: deploy/copy-essay-images.sh（旧サーバで実行する）
-- ============================================================================
