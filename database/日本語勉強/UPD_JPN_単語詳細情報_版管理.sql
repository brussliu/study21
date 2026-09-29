-- ============================================================================
-- Study 2.1  日本語勉強 DDL 変更
-- 内容: JPN_単語詳細情報 を「1 語 1 行（詳細JSON の塊）」から「1 語 N 版（版管理）」へ作り直す
-- ----------------------------------------------------------------------------
-- なぜ（設計: 日本語勉強_再設計案_中文.md）:
--   ・詳細を段落ごとの子テーブルに分け、段落単位の AI 取り直しと人の行編集を可能にする
--   ・取得・編集のすべてを「版」として残し、画面から有効版を選べるようにする
--   ・未指定なら最新版が有効（部分 UNIQUE 索引で「1 語 1 有効版」を保証）
--
-- この表は現在 **0 行**（2.0 からの詳細データは削除済み）なので、
-- 移行はせずに作り直す。中身が入っていると気づかず消すことになるため、
-- 先に件数を数えて 1 行でもあれば例外で止める。
--
-- 実行順（このファイルは「古い形を消す」だけ。新しい形は TBL ファイルが作る）:
--   1. このファイル（0 行チェック → DROP）
--   2. TBL_JPN_単語詳細情報.sql                        （版の本体）
--   3. TBL_JPN_単語詳細_語義情報.sql 〜 _練習情報.sql   （11 の子テーブル。会話 → 会話行 の順）
--   4. UPD_JPN_テスト出題情報_出題選択肢.sql             （出題に提示した選択肢の列）
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

BEGIN;

-- 0 行でなければ止める（履歴を失う変更なので、勝手に消さない）
DO $$
DECLARE
    v_rows BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_rows FROM public."JPN_単語詳細情報";
    IF v_rows > 0 THEN
        RAISE EXCEPTION 'JPN_単語詳細情報 に % 行あります。版管理へ作り直す前に、必要な内容を退避してください。', v_rows;
    END IF;
END $$;

-- 子テーブル（存在すれば）を先に消す。会話行 → 会話 → その他
DROP TABLE IF EXISTS public."JPN_単語詳細_会話行情報";
DROP TABLE IF EXISTS public."JPN_単語詳細_会話情報";
DROP TABLE IF EXISTS public."JPN_単語詳細_練習情報";
DROP TABLE IF EXISTS public."JPN_単語詳細_使用場面情報";
DROP TABLE IF EXISTS public."JPN_単語詳細_関連語情報";
DROP TABLE IF EXISTS public."JPN_単語詳細_コロケーション情報";
DROP TABLE IF EXISTS public."JPN_単語詳細_注意情報";
DROP TABLE IF EXISTS public."JPN_単語詳細_類義語情報";
DROP TABLE IF EXISTS public."JPN_単語詳細_文型情報";
DROP TABLE IF EXISTS public."JPN_単語詳細_例文情報";
DROP TABLE IF EXISTS public."JPN_単語詳細_語義情報";

-- 本体（古い形: 詳細JSON の塊。旧詳細ID・GIN 索引もここで消える）
DROP TABLE IF EXISTS public."JPN_単語詳細情報";

COMMIT;

-- この後、TBL_JPN_単語詳細情報.sql と 11 の子テーブル、テスト出題情報の列追加を流す。
