-- ============================================================================
-- Study 2.1  英作文AI添削の実行パラメータ（Temperature / 最大出力Token数）を設定化
-- ----------------------------------------------------------------------------
-- 追加・変更するもの:
--   1. COM_設定項目 … 実行パラメータ 4 件（カタログ。設定ページの区分は ENGLISH_ESSAY）
--        * ENGLISH_ESSAY_OCR_MAX_COMPLETION_TOKENS     （INTEGER 1024..65536）
--        * ENGLISH_ESSAY_OCR_TEMPERATURE               （ENUM 0.0〜2.0）
--        * ENGLISH_ESSAY_GRADING_MAX_COMPLETION_TOKENS （INTEGER 1024..65536）
--        * ENGLISH_ESSAY_GRADING_TEMPERATURE           （ENUM 0.0〜2.0）
--   2. COM_設定情報 … 1 の初期値（**未登録のときだけ**入れる。利用者が保存した値は上書きしない）
--
-- なぜ必要か（利用者の指示。2026-09-27）:
--   `EnglishEssayOcrStep`（TEMPERATURE 0.0 / MAX_COMPLETION_TOKENS 4096）と
--   `EnglishEssayGradingStep`（0.2 / 8192）は**実行パラメータがコードに埋まって**いた
--   （docs/HARDCODED_LIMITS.md の A-11）。モデルを変えたときに出力上限を調整できないので、
--   日本語単語 AI（BAT_C41_TEMPERATURE / BAT_C41_MAX_COMPLETION_TOKENS）と同じ形で設定にする。
--   初期値は**それまでコードに埋まっていた値**をそのまま使う（挙動を変えない）。
--
-- カタログ（COM_設定項目）も一緒に入れる理由:
--   `SettingsService#requireSettings` は設定値だけでなく**カタログ定義の存在**も確かめる
--   （無いと「カタログ定義が存在しません」で実行前に止まる）。既存DBでは
--   TBL_COM_設定項目_init.sql を流し直さないので、ここで両方入れる。
--
-- 冪等: どちらも ON CONFLICT DO NOTHING（既存値を**上書きしない**）。再実行しても安全。
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: database/設定/TBL_COM_設定項目.sql・TBL_COM_設定情報.sql の後
--
-- 実行例:
--   psql -h 192.168.0.100 -p 54320 -U postgres -d study21 -v ON_ERROR_STOP=1 \
--        -f database/移行/MIG_ENG_英作文_AI実行パラメータ設定_20260927.sql
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. 設定カタログ（COM_設定項目）
-- ----------------------------------------------------------------------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_OCR_MAX_COMPLETION_TOKENS','INTEGER','1','1024..65536','英作文：OCR：最大出力Token数（1024〜65536）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_OCR_TEMPERATURE','ENUM','1','0.0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0,1.1,1.2,1.3,1.4,1.5,1.6,1.7,1.8,1.9,2.0','英作文：OCR：Temperature（0.0〜2.0）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_GRADING_MAX_COMPLETION_TOKENS','INTEGER','1','1024..65536','英作文：添削：最大出力Token数（1024〜65536）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_GRADING_TEMPERATURE','ENUM','1','0.0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0,1.1,1.2,1.3,1.4,1.5,1.6,1.7,1.8,1.9,2.0','英作文：添削：Temperature（0.0〜2.0）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2. 初期値（COM_設定情報）。**既に行があるときは触らない**（利用者の保存値を守る）
--    値は 2026-09-27 までコードに埋まっていた値と同じ
-- ----------------------------------------------------------------------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_OCR_MAX_COMPLETION_TOKENS','GLOBAL','4096')
ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_OCR_TEMPERATURE','GLOBAL','0.0')
ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_GRADING_MAX_COMPLETION_TOKENS','GLOBAL','8192')
ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_GRADING_TEMPERATURE','GLOBAL','0.2')
ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

COMMIT;

-- ----------------------------------------------------------------------------
-- 確認（実行後に目視する）
-- ----------------------------------------------------------------------------
\echo '--- 確認1: カタログ（4 件そろっていれば OK）---'
SELECT "ページ区分", "設定キー", "値タイプ", "有効値"
  FROM public."COM_設定項目"
 WHERE "ページ区分" = 'ENGLISH_ESSAY'
   AND "設定キー" IN ('ENGLISH_ESSAY_OCR_MAX_COMPLETION_TOKENS','ENGLISH_ESSAY_OCR_TEMPERATURE',
                      'ENGLISH_ESSAY_GRADING_MAX_COMPLETION_TOKENS','ENGLISH_ESSAY_GRADING_TEMPERATURE')
 ORDER BY 2;

\echo '--- 確認2: 初期値（4 件。既存値は変わらない）---'
SELECT "ページ区分", "設定キー", "設定値"
  FROM public."COM_設定情報"
 WHERE "ページ区分" = 'ENGLISH_ESSAY'
   AND "設定キー" IN ('ENGLISH_ESSAY_OCR_MAX_COMPLETION_TOKENS','ENGLISH_ESSAY_OCR_TEMPERATURE',
                      'ENGLISH_ESSAY_GRADING_MAX_COMPLETION_TOKENS','ENGLISH_ESSAY_GRADING_TEMPERATURE')
 ORDER BY 2;

-- 4 件そろっていないときは NOTICE で知らせる（psql の画面に出る）
DO $$
DECLARE rows integer;
BEGIN
    SELECT count(*) INTO rows FROM public."COM_設定情報"
     WHERE "ページ区分" = 'ENGLISH_ESSAY'
       AND "設定キー" IN ('ENGLISH_ESSAY_OCR_MAX_COMPLETION_TOKENS','ENGLISH_ESSAY_OCR_TEMPERATURE',
                          'ENGLISH_ESSAY_GRADING_MAX_COMPLETION_TOKENS','ENGLISH_ESSAY_GRADING_TEMPERATURE');
    IF rows = 4 THEN
        RAISE NOTICE '英作文AIの実行パラメータ 4 件を登録しました（設定ページ「英作文AI添削」で変えられます）';
    ELSE
        RAISE NOTICE '英作文AIの実行パラメータは % 件だけです（4 件のはず。確認1・確認2 を見てください）', rows;
    END IF;
END $$;
