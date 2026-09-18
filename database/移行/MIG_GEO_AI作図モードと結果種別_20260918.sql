-- ============================================================================
-- Study 2.1  移行: AI 生図の再設計（作図モード A〜D・結果種別・判定）
-- ----------------------------------------------------------------------------
-- 追加・変更するもの:
--   1. GEO_AI生図リクエスト情報 … 「何を入力にしたか（作図モード）」と「何を作るか（結果種別）」、
--      そして AI の判定・確認質問・追跡用のスナップショットの列（7 列。すべて NULL 可）
--   2. GEO_AI生図リクエスト情報.状態コード … 新しい状態 PREPROCESSING / VALIDATING / NEEDS_INPUT を
--      許すように制約 CK_GEO_AI生図_状態 を**広げる**（古い値は全部そのまま。既存行は変わらない）
--   3. COM_設定項目 / COM_設定情報 … モード別の設定 7 項目 × 4 モード = 28 件（GEOMETRY_AI_<A〜D>_*）
--   4. BAT_バッチコントロール情報 … モード別バッチ batC51-A〜D の 4 行（有効＝'1'）
--
-- 背景（利用者の指示）:
--   1 本の batC51 が「画像をもとに再現」だけをしていたのを、**入力をどう読むか**で
--   4 つのモード（A 画像をもとに再現 / B 数式からグラフを作成 / C 文章の条件から作図 /
--   D 文章と図を合わせて作図）に分け、モードごとに 1 バッチ（batC51-A〜D）として登録する。
--   **何を作るか**（図形／グラフ／混在）はモードとは別の軸（結果種別）にする。
--   モードが無い時代の要求（作図モード が NULL）は **A として扱う**ので、既存行は触らない。
--
-- 設定の継承:
--   モード別の項目は**未設定・空のとき共通（GEOMETRY_AI_SYSTEM_PROMPT /
--   GEOMETRY_AI_INSTRUCTION_TEMPLATE / GEOMETRY_AI_PROVIDER / GEOMETRY_AI_TEMPERATURE /
--   GEOMETRY_AI_MAX_COMPLETION_TOKENS / GEOMETRY_AI_REQUEST_TIMEOUT_SECONDS /
--   GEOMETRY_AI_RETRY_LIMIT）をそのまま使う**。今回は**値を空文字で入れるだけ**にして、
--   利用者があとから書けるようにする（既存の共通の値は触らない）。
--
-- 冪等性（何度流しても同じ）:
--   ADD COLUMN IF NOT EXISTS / COMMENT ON COLUMN（上書き）/ 制約は名前と定義を確認してから付け直す /
--   INSERT は ON CONFLICT DO NOTHING。既存行の状態コードは**変更しない**（値域を広げるだけ）。
--   設定の init（TBL_COM_設定項目_init / TBL_COM_設定情報_init）を**先に流しても後に流しても**同じ結果になる
--   （この移行が入れるキーは init に無く、init が入れるキーはこの移行が触らない）。
--
-- バッチコードの桁:
--   BAT_バッチコントロール情報.バッチコード は VARCHAR(20)。**連字符つきの接尾辞は
--   batC15-1〜3 で既に使っている**（同じ流儀）ので、どの列も広げる必要は無い
--   （batC51-A は 8 文字。ファイル末尾の確認用 SELECT で実際の桁と文字数を出す）。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: database/図形管理/TBL_GEO_AI生図リクエスト情報.sql、
--           database/設定/TBL_COM_設定項目_init.sql、database/設定/TBL_COM_設定情報_init.sql、
--           database/バッチ/TBL_BAT_バッチコントロール情報.sql の後
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. 列の追加（すべて NULL 可。既存行は NULL のまま＝歴史的な要求として扱える）
-- ----------------------------------------------------------------------------
ALTER TABLE public."GEO_AI生図リクエスト情報"
    ADD COLUMN IF NOT EXISTS "作図モード"           VARCHAR(10) NULL,
    ADD COLUMN IF NOT EXISTS "結果種別要求"         VARCHAR(20) NULL,
    ADD COLUMN IF NOT EXISTS "結果種別確定"         VARCHAR(20) NULL,
    ADD COLUMN IF NOT EXISTS "補充パラメータ"       JSONB       NULL,
    ADD COLUMN IF NOT EXISTS "設定スナップショット" JSONB       NULL,
    ADD COLUMN IF NOT EXISTS "判定"                 VARCHAR(20) NULL,
    ADD COLUMN IF NOT EXISTS "確認質問"             JSONB       NULL;

COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."作図モード" IS
    'A=画像をもとに再現 / B=数式からグラフを作成 / C=文章の条件から作図 / D=文章と図を合わせて作図。NULL=歴史的な要求（モードが無い時代。**A として扱う**）';

COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."結果種別要求" IS
    '利用者が指定した「作成する図の種類」。AUTO=おまかせ（AI が決める）/ GEOMETRY=図形 / GRAPH=関数・方程式のグラフ / MIXED=両方';

COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."結果種別確定" IS
    '実際に作る種類。GEOMETRY=図形 / GRAPH=関数・方程式のグラフ / MIXED=両方。決められないとき（判定が NEEDS_INPUT / UNSUPPORTED）は NULL';

COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."補充パラメータ" IS
    'モードと結果種別に当てはまる補充パラメータ（日本語の項目名 → 値 の JSON）。当てはまらない項目は入れない（画面が復元して表示する）';

COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."設定スナップショット" IS
    '追跡用の設定スナップショット（テンプレートの版＝ハッシュ、モード別／共通のどちらを使ったか、使用モデル名、パラメータ）。**API キー・URL は入れない**';

COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."判定" IS
    'AI の判定。GENERATABLE=作図できる / NEEDS_INPUT=追加入力が必要 / UNSUPPORTED=この版では作れない。**実行・検証・保存の結果（状態コード）とは別**';

COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."確認質問" IS
    '追加入力待ちの確認質問（JSON 配列）。判定が NEEDS_INPUT のとき画面が復元して表示する';

-- ----------------------------------------------------------------------------
-- 2. 新しい列の CHECK 制約（名前で存在を確認してから付ける＝何度流しても同じ）
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF to_regclass('public."GEO_AI生図リクエスト情報"') IS NULL THEN
        RAISE NOTICE 'GEO_AI生図リクエスト情報 が無いので、列の制約は付けません（先に DDL を流してください）';
        RETURN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conname = 'CK_GEO_AI生図_作図モード'
                      AND conrelid = to_regclass('public."GEO_AI生図リクエスト情報"')) THEN
        ALTER TABLE public."GEO_AI生図リクエスト情報"
            ADD CONSTRAINT "CK_GEO_AI生図_作図モード"
            CHECK ("作図モード" IS NULL OR "作図モード" IN ('A','B','C','D'));
        RAISE NOTICE '制約 CK_GEO_AI生図_作図モード を付けました';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conname = 'CK_GEO_AI生図_結果種別要求'
                      AND conrelid = to_regclass('public."GEO_AI生図リクエスト情報"')) THEN
        ALTER TABLE public."GEO_AI生図リクエスト情報"
            ADD CONSTRAINT "CK_GEO_AI生図_結果種別要求"
            CHECK ("結果種別要求" IS NULL OR "結果種別要求" IN ('AUTO','GEOMETRY','GRAPH','MIXED'));
        RAISE NOTICE '制約 CK_GEO_AI生図_結果種別要求 を付けました';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conname = 'CK_GEO_AI生図_結果種別確定'
                      AND conrelid = to_regclass('public."GEO_AI生図リクエスト情報"')) THEN
        ALTER TABLE public."GEO_AI生図リクエスト情報"
            ADD CONSTRAINT "CK_GEO_AI生図_結果種別確定"
            CHECK ("結果種別確定" IS NULL OR "結果種別確定" IN ('GEOMETRY','GRAPH','MIXED'));
        RAISE NOTICE '制約 CK_GEO_AI生図_結果種別確定 を付けました';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conname = 'CK_GEO_AI生図_判定'
                      AND conrelid = to_regclass('public."GEO_AI生図リクエスト情報"')) THEN
        ALTER TABLE public."GEO_AI生図リクエスト情報"
            ADD CONSTRAINT "CK_GEO_AI生図_判定"
            CHECK ("判定" IS NULL OR "判定" IN ('GENERATABLE','NEEDS_INPUT','UNSUPPORTED'));
        RAISE NOTICE '制約 CK_GEO_AI生図_判定 を付けました';
    END IF;
END $$;

-- ----------------------------------------------------------------------------
-- 3. 状態コードの許容値を広げる（PREPROCESSING / VALIDATING / NEEDS_INPUT を追加）
--    古い値（QUEUED/PREPROCESSED/GENERATING/GENERATED/READY/REGISTERED/FAILED/CANCELLED）は
--    全部そのまま残すので、既存行はそのまま有効。既に広がっていれば何もしない。
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    current_definition TEXT;
BEGIN
    IF to_regclass('public."GEO_AI生図リクエスト情報"') IS NULL THEN
        RAISE NOTICE 'GEO_AI生図リクエスト情報 が無いので、状態コードの制約は触りません（先に DDL を流してください）';
        RETURN;
    END IF;

    SELECT pg_get_constraintdef(c.oid)
      INTO current_definition
      FROM pg_constraint c
     WHERE c.conname = 'CK_GEO_AI生図_状態'
       AND c.conrelid = to_regclass('public."GEO_AI生図リクエスト情報"');

    IF current_definition IS NOT NULL
       AND current_definition LIKE '%PREPROCESSING%'
       AND current_definition LIKE '%VALIDATING%'
       AND current_definition LIKE '%NEEDS_INPUT%' THEN
        RAISE NOTICE '状態コードの制約は既に新しい値域です（何もしません）';
        RETURN;
    END IF;

    ALTER TABLE public."GEO_AI生図リクエスト情報"
        DROP CONSTRAINT IF EXISTS "CK_GEO_AI生図_状態";
    ALTER TABLE public."GEO_AI生図リクエスト情報"
        ADD CONSTRAINT "CK_GEO_AI生図_状態" CHECK ("状態コード" IN
            ('QUEUED','PREPROCESSING','PREPROCESSED','GENERATING','GENERATED',
             'VALIDATING','NEEDS_INPUT','READY','REGISTERED','FAILED','CANCELLED'));
    RAISE NOTICE '状態コードの制約を新しい値域へ付け直しました（旧: %）',
        COALESCE(current_definition, '（制約なし）');
END $$;

COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."状態コード" IS
    'QUEUED=受付済（前処理待ち） / PREPROCESSING=前処理中 / PREPROCESSED=前処理済（AI 生成待ち） / GENERATING=AI生成中 / GENERATED=コマンド保存済（検証待ち） / VALIDATING=検証中 / NEEDS_INPUT=追加入力待ち（AI の判定が NEEDS_INPUT。確認質問を画面に出す） / READY=検証済（確認待ち） / REGISTERED=図形登録済 / FAILED=失敗 / CANCELLED=取消';

-- ----------------------------------------------------------------------------
-- 4. モード別の設定（カタログ COM_設定項目）
--    7 項目 × モード A〜D = 28 件。必須フラグは全部 '0'（未設定・空は共通を継承する）。
--    値タイプは COM_設定項目 の CHECK（STRING/INTEGER/DECIMAL/BOOLEAN/TEXT/TIME/ENUM）に合わせ、
--    有効値の範囲式は既存の書き方（min..max。例 '1024..65536'）に揃える。
-- ----------------------------------------------------------------------------
-- ---- モード A（画像をもとに再現）----
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_A_SYSTEM_PROMPT','TEXT','0',NULL,'AI生図（モード A 画像をもとに再現）：モード別の System Prompt。空なら共通（GEOMETRY_AI_SYSTEM_PROMPT）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_A_TASK_TEMPLATE','TEXT','0',NULL,'AI生図（モード A 画像をもとに再現）：モード別のタスクテンプレート（{mode} {resultType} {note} {supplements} {maxCommands} などを実行時に置換する）。空なら共通（GEOMETRY_AI_INSTRUCTION_TEMPLATE）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_A_PROVIDER','STRING','0',NULL,'AI生図（モード A 画像をもとに再現）：使用するモデルのスロット（例 qwen:4）。空なら共通（GEOMETRY_AI_PROVIDER）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_A_TEMPERATURE','DECIMAL','0','0..2','AI生図（モード A 画像をもとに再現）：Temperature（0〜2。低めにして出力を安定させる）。空なら共通（GEOMETRY_AI_TEMPERATURE）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_A_MAX_COMPLETION_TOKENS','INTEGER','0','1024..65536','AI生図（モード A 画像をもとに再現）：最大出力 Token 数（1024〜65536）。空なら共通（GEOMETRY_AI_MAX_COMPLETION_TOKENS）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_A_REQUEST_TIMEOUT_SECONDS','INTEGER','0','30..1800','AI生図（モード A 画像をもとに再現）：AI API への 1 回の通信を待つ最大秒数（30〜1800）。空なら共通（GEOMETRY_AI_REQUEST_TIMEOUT_SECONDS）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_A_RETRY_LIMIT','INTEGER','0','0..3','AI生図（モード A 画像をもとに再現）：タイムアウト・5xx・JSON 不正時の再実行回数（0〜3。回数分だけ課金される）。空なら共通（GEOMETRY_AI_RETRY_LIMIT）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ---- モード B（数式からグラフを作成）----
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_B_SYSTEM_PROMPT','TEXT','0',NULL,'AI生図（モード B 数式からグラフを作成）：モード別の System Prompt。空なら共通（GEOMETRY_AI_SYSTEM_PROMPT）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_B_TASK_TEMPLATE','TEXT','0',NULL,'AI生図（モード B 数式からグラフを作成）：モード別のタスクテンプレート（{mode} {resultType} {note} {supplements} {maxCommands} などを実行時に置換する）。空なら共通（GEOMETRY_AI_INSTRUCTION_TEMPLATE）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_B_PROVIDER','STRING','0',NULL,'AI生図（モード B 数式からグラフを作成）：使用するモデルのスロット（例 qwen:4）。空なら共通（GEOMETRY_AI_PROVIDER）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_B_TEMPERATURE','DECIMAL','0','0..2','AI生図（モード B 数式からグラフを作成）：Temperature（0〜2。低めにして出力を安定させる）。空なら共通（GEOMETRY_AI_TEMPERATURE）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_B_MAX_COMPLETION_TOKENS','INTEGER','0','1024..65536','AI生図（モード B 数式からグラフを作成）：最大出力 Token 数（1024〜65536）。空なら共通（GEOMETRY_AI_MAX_COMPLETION_TOKENS）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_B_REQUEST_TIMEOUT_SECONDS','INTEGER','0','30..1800','AI生図（モード B 数式からグラフを作成）：AI API への 1 回の通信を待つ最大秒数（30〜1800）。空なら共通（GEOMETRY_AI_REQUEST_TIMEOUT_SECONDS）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_B_RETRY_LIMIT','INTEGER','0','0..3','AI生図（モード B 数式からグラフを作成）：タイムアウト・5xx・JSON 不正時の再実行回数（0〜3。回数分だけ課金される）。空なら共通（GEOMETRY_AI_RETRY_LIMIT）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ---- モード C（文章の条件から作図）----
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_C_SYSTEM_PROMPT','TEXT','0',NULL,'AI生図（モード C 文章の条件から作図）：モード別の System Prompt。空なら共通（GEOMETRY_AI_SYSTEM_PROMPT）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_C_TASK_TEMPLATE','TEXT','0',NULL,'AI生図（モード C 文章の条件から作図）：モード別のタスクテンプレート（{mode} {resultType} {note} {supplements} {maxCommands} などを実行時に置換する）。空なら共通（GEOMETRY_AI_INSTRUCTION_TEMPLATE）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_C_PROVIDER','STRING','0',NULL,'AI生図（モード C 文章の条件から作図）：使用するモデルのスロット（例 qwen:4）。空なら共通（GEOMETRY_AI_PROVIDER）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_C_TEMPERATURE','DECIMAL','0','0..2','AI生図（モード C 文章の条件から作図）：Temperature（0〜2。低めにして出力を安定させる）。空なら共通（GEOMETRY_AI_TEMPERATURE）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_C_MAX_COMPLETION_TOKENS','INTEGER','0','1024..65536','AI生図（モード C 文章の条件から作図）：最大出力 Token 数（1024〜65536）。空なら共通（GEOMETRY_AI_MAX_COMPLETION_TOKENS）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_C_REQUEST_TIMEOUT_SECONDS','INTEGER','0','30..1800','AI生図（モード C 文章の条件から作図）：AI API への 1 回の通信を待つ最大秒数（30〜1800）。空なら共通（GEOMETRY_AI_REQUEST_TIMEOUT_SECONDS）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_C_RETRY_LIMIT','INTEGER','0','0..3','AI生図（モード C 文章の条件から作図）：タイムアウト・5xx・JSON 不正時の再実行回数（0〜3。回数分だけ課金される）。空なら共通（GEOMETRY_AI_RETRY_LIMIT）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ---- モード D（文章と図を合わせて作図）----
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_D_SYSTEM_PROMPT','TEXT','0',NULL,'AI生図（モード D 文章と図を合わせて作図）：モード別の System Prompt。空なら共通（GEOMETRY_AI_SYSTEM_PROMPT）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_D_TASK_TEMPLATE','TEXT','0',NULL,'AI生図（モード D 文章と図を合わせて作図）：モード別のタスクテンプレート（{mode} {resultType} {note} {supplements} {maxCommands} などを実行時に置換する）。空なら共通（GEOMETRY_AI_INSTRUCTION_TEMPLATE）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_D_PROVIDER','STRING','0',NULL,'AI生図（モード D 文章と図を合わせて作図）：使用するモデルのスロット（例 qwen:4）。空なら共通（GEOMETRY_AI_PROVIDER）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_D_TEMPERATURE','DECIMAL','0','0..2','AI生図（モード D 文章と図を合わせて作図）：Temperature（0〜2。低めにして出力を安定させる）。空なら共通（GEOMETRY_AI_TEMPERATURE）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_D_MAX_COMPLETION_TOKENS','INTEGER','0','1024..65536','AI生図（モード D 文章と図を合わせて作図）：最大出力 Token 数（1024〜65536）。空なら共通（GEOMETRY_AI_MAX_COMPLETION_TOKENS）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_D_REQUEST_TIMEOUT_SECONDS','INTEGER','0','30..1800','AI生図（モード D 文章と図を合わせて作図）：AI API への 1 回の通信を待つ最大秒数（30〜1800）。空なら共通（GEOMETRY_AI_REQUEST_TIMEOUT_SECONDS）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_D_RETRY_LIMIT','INTEGER','0','0..3','AI生図（モード D 文章と図を合わせて作図）：タイムアウト・5xx・JSON 不正時の再実行回数（0〜3。回数分だけ課金される）。空なら共通（GEOMETRY_AI_RETRY_LIMIT）を継承する')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ----------------------------------------------------------------------------
-- 5. モード別の設定の**値**（COM_設定情報。GLOBAL スコープ）
--    今回は**空文字**で入れる（利用者があとから書く。空・空白は共通を継承する。
--    既にあるキーの値は触らない＝ON CONFLICT DO NOTHING）。
-- ----------------------------------------------------------------------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_A_SYSTEM_PROMPT','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_A_TASK_TEMPLATE','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_A_PROVIDER','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_A_TEMPERATURE','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_A_MAX_COMPLETION_TOKENS','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_A_REQUEST_TIMEOUT_SECONDS','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_A_RETRY_LIMIT','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_B_SYSTEM_PROMPT','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_B_TASK_TEMPLATE','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_B_PROVIDER','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_B_TEMPERATURE','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_B_MAX_COMPLETION_TOKENS','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_B_REQUEST_TIMEOUT_SECONDS','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_B_RETRY_LIMIT','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_C_SYSTEM_PROMPT','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_C_TASK_TEMPLATE','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_C_PROVIDER','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_C_TEMPERATURE','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_C_MAX_COMPLETION_TOKENS','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_C_REQUEST_TIMEOUT_SECONDS','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_C_RETRY_LIMIT','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_D_SYSTEM_PROMPT','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_D_TASK_TEMPLATE','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_D_PROVIDER','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_D_TEMPERATURE','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_D_MAX_COMPLETION_TOKENS','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_D_REQUEST_TIMEOUT_SECONDS','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('GEOMETRY_AI','GEOMETRY_AI_D_RETRY_LIMIT','GLOBAL','') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- ----------------------------------------------------------------------------
-- 6. バッチコントロール（モード別バッチを有効にして登録する）
--    状態 '1' = 有効。既に同じコードの行があれば何もしない（利用者の設定を上書きしない）。
--    備考はモードの名前（バッチ管理画面にこのまま出る）。登録元／更新元は移行の印。
-- ----------------------------------------------------------------------------
INSERT INTO public."BAT_バッチコントロール情報"
    ("バッチコード", "状態", "備考", "登録元コード", "更新元コード")
VALUES
    ('batC51-A', '1', 'AI生図 画像をもとに再現',       'MIG', 'MIG'),
    ('batC51-B', '1', 'AI生図 数式からグラフを作成',   'MIG', 'MIG'),
    ('batC51-C', '1', 'AI生図 文章の条件から作図',     'MIG', 'MIG'),
    ('batC51-D', '1', 'AI生図 文章と図を合わせて作図', 'MIG', 'MIG')
ON CONFLICT ("バッチコード") DO NOTHING;

COMMIT;

-- ============================================================================
-- 確認用（そのまま psql で流すと結果が出る）
-- ============================================================================

\echo '--- 確認1: GEO_AI生図リクエスト情報 の新しい列（7 列。すべて NULL 可）---'
SELECT ordinal_position AS "順", column_name AS "列", data_type AS "型",
       character_maximum_length AS "長さ", is_nullable AS "NULL可"
  FROM information_schema.columns
 WHERE table_schema = 'public'
   AND table_name = 'GEO_AI生図リクエスト情報'
   AND column_name IN ('作図モード','結果種別要求','結果種別確定','補充パラメータ',
                       '設定スナップショット','判定','確認質問')
 ORDER BY ordinal_position;

\echo '--- 確認2: 新しい列の CHECK 制約（4 つ）---'
SELECT c.conname AS "制約", pg_get_constraintdef(c.oid) AS "定義"
  FROM pg_constraint c
 WHERE c.conrelid = to_regclass('public."GEO_AI生図リクエスト情報"')
   AND c.conname IN ('CK_GEO_AI生図_作図モード','CK_GEO_AI生図_結果種別要求',
                     'CK_GEO_AI生図_結果種別確定','CK_GEO_AI生図_判定')
 ORDER BY c.conname;

\echo '--- 確認3: 状態コードの許容値（PREPROCESSING / VALIDATING / NEEDS_INPUT が入っていること）---'
SELECT pg_get_constraintdef(c.oid) AS "CK_GEO_AI生図_状態"
  FROM pg_constraint c
 WHERE c.conrelid = to_regclass('public."GEO_AI生図リクエスト情報"')
   AND c.conname = 'CK_GEO_AI生図_状態';

\echo '--- 確認4: いま入っている状態コード（既存行はそのまま有効）---'
SELECT "状態コード" AS "状態", count(*) AS "件数"
  FROM public."GEO_AI生図リクエスト情報"
 GROUP BY 1 ORDER BY 1;

\echo '--- 確認5: モード別の設定（カタログと値。28 件ずつ・値は空文字）---'
SELECT i."設定キー" AS "設定キー", i."値タイプ" AS "型", i."必須フラグ" AS "必須", i."有効値" AS "有効値",
       COALESCE('[' || v."設定値" || ']', '（値の行なし）') AS "設定値"
  FROM public."COM_設定項目" i
  LEFT JOIN public."COM_設定情報" v
         ON v."ページ区分" = i."ページ区分"
        AND v."設定キー"   = i."設定キー"
        AND v."スコープ"   = 'GLOBAL'
 WHERE i."ページ区分" = 'GEOMETRY_AI'
   AND i."設定キー" ~ '^GEOMETRY_AI_[ABCD]_(SYSTEM_PROMPT|TASK_TEMPLATE|PROVIDER|TEMPERATURE|MAX_COMPLETION_TOKENS|REQUEST_TIMEOUT_SECONDS|RETRY_LIMIT)$'
 ORDER BY i."設定キー";

\echo '--- 確認6: モード別の設定の件数（カタログ 28 / 値 28・すべて空文字）---'
SELECT (SELECT count(*) FROM public."COM_設定項目"
         WHERE "ページ区分" = 'GEOMETRY_AI'
           AND "設定キー" ~ '^GEOMETRY_AI_[ABCD]_(SYSTEM_PROMPT|TASK_TEMPLATE|PROVIDER|TEMPERATURE|MAX_COMPLETION_TOKENS|REQUEST_TIMEOUT_SECONDS|RETRY_LIMIT)$') AS "カタログ件数",
       (SELECT count(*) FROM public."COM_設定情報"
         WHERE "ページ区分" = 'GEOMETRY_AI'
           AND "設定キー" ~ '^GEOMETRY_AI_[ABCD]_(SYSTEM_PROMPT|TASK_TEMPLATE|PROVIDER|TEMPERATURE|MAX_COMPLETION_TOKENS|REQUEST_TIMEOUT_SECONDS|RETRY_LIMIT)$'
           AND "スコープ" = 'GLOBAL') AS "値の件数",
       (SELECT count(*) FROM public."COM_設定情報"
         WHERE "ページ区分" = 'GEOMETRY_AI'
           AND "設定キー" ~ '^GEOMETRY_AI_[ABCD]_(SYSTEM_PROMPT|TASK_TEMPLATE|PROVIDER|TEMPERATURE|MAX_COMPLETION_TOKENS|REQUEST_TIMEOUT_SECONDS|RETRY_LIMIT)$'
           AND "スコープ" = 'GLOBAL' AND BTRIM(COALESCE("設定値", '')) = '') AS "空文字の件数";

-- ----------------------------------------------------------------------------
-- 7. 未処理の探索に使う部分索引の述語を新しい状態まで広げる
-- ----------------------------------------------------------------------------
-- 働き手（worker）は「待機中（QUEUED）」「読み取り中（PREPROCESSING）」などの
-- 「まだ終わっていない」行を毎周期さがす。部分索引の述語が古い 4 状態のままだと
-- 新しい状態（PREPROCESSING / VALIDATING）が索引に載らず、探索が全件走査になる。
-- 述語を張り直す（索引名は同じ。IF NOT EXISTS では述語を変えられないので DROP → CREATE）。
DROP INDEX IF EXISTS public.idx_geo_ai_request_pending;
CREATE INDEX IF NOT EXISTS idx_geo_ai_request_pending
    ON public."GEO_AI生図リクエスト情報" ("状態コード", "登録日時")
    WHERE "状態コード" IN ('QUEUED','PREPROCESSING','PREPROCESSED','GENERATING','GENERATED','VALIDATING');

-- 検証の対象（生成待ち・検証待ち）も同じ索引で拾える（追加入力待ち・失敗は人の操作で戻る）

\echo '--- 確認7: バッチコントロール（batC51-A〜D の 4 行。batC51 はそのまま）---'
SELECT "バッチコード" AS "バッチコード", "状態" AS "有効", "備考" AS "備考", "登録元コード" AS "登録元"
  FROM public."BAT_バッチコントロール情報"
 WHERE "バッチコード" LIKE 'batC51%'
 ORDER BY "バッチコード";

\echo '--- 確認8: バッチコード列の桁（連字符つき接尾辞が収まること。VARCHAR(20)）---'
SELECT table_name AS "表", column_name AS "列", data_type AS "型", character_maximum_length AS "桁"
  FROM information_schema.columns
 WHERE table_schema = 'public'
   AND column_name = 'バッチコード'
   AND table_name IN ('BAT_バッチコントロール情報', 'BAT_バッチ実行履歴情報')
 ORDER BY table_name;

\echo '--- 確認10: 未処理探索の部分索引（新しい状態まで含んでいること）---'
SELECT indexname AS "索引", indexdef AS "定義"
  FROM pg_indexes
 WHERE schemaname = 'public' AND indexname = 'idx_geo_ai_request_pending';

\echo '--- 確認9: 新しいバッチコードの文字数（8 文字。列は 20 桁なので広げる必要は無い）---'
SELECT "バッチコード" AS "バッチコード", length("バッチコード") AS "文字数"
  FROM public."BAT_バッチコントロール情報"
 WHERE "バッチコード" LIKE 'batC51%'
 ORDER BY "バッチコード";
