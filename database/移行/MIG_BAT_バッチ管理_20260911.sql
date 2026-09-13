-- ============================================================================
-- Study 2.1  バッチ管理の表再編
--   BAT_タスク実行情報      -> BAT_バッチ実行履歴情報（改名・列名統一・状態コード化）
--   新設                    -> BAT_バッチコントロール情報（有効／無効を COM_設定情報 から移す）
-- ----------------------------------------------------------------------------
-- 事前条件:
--   1. database/バッチ/TBL_BAT_バッチ実行履歴情報.sql
--   2. database/バッチ/TBL_BAT_バッチコントロール情報.sql
--   3. 移行元: 旧 BAT_タスク実行情報（既に無い場合は何もしない）
--   4. 有効／無効の移行元: COM_設定情報 の BATCH_TASK_ENABLED_<バッチコード>
--
-- 実行例:
--   psql -U postgres -d study21 -v ON_ERROR_STOP=1 -f MIG_BAT_バッチ管理_20260911.sql
--
-- 冪等性:
--   既に改名済み・移行済みの場合は何度実行しても結果は変わらない。
--   COM_設定情報 の BATCH_TASK_ENABLED_* は移行後に削除する（二重管理を避ける）。
--
-- 状態の対応（2.0 は日本語、2.1 はコード）:
--   正常終了 -> SUCCESS / 異常終了 -> FAILED / スキップ -> SKIPPED
--   待機中   -> QUEUED  / 実行中   -> RUNNING
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. テーブル名と列名を変更する（旧テーブルがあるときだけ）
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF to_regclass('public."BAT_タスク実行情報"') IS NOT NULL
       AND to_regclass('public."BAT_バッチ実行履歴情報"') IS NULL THEN
        ALTER TABLE public."BAT_タスク実行情報" RENAME TO "BAT_バッチ実行履歴情報";
        RAISE NOTICE 'BAT_タスク実行情報 を BAT_バッチ実行履歴情報 へ改名しました';
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public."BAT_バッチ実行履歴情報"') IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'BAT_バッチ実行履歴情報' AND column_name = 'タスクコード') THEN
            ALTER TABLE public."BAT_バッチ実行履歴情報" RENAME COLUMN "タスクコード" TO "バッチコード";
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'BAT_バッチ実行履歴情報' AND column_name = 'タスク種別') THEN
            ALTER TABLE public."BAT_バッチ実行履歴情報" RENAME COLUMN "タスク種別" TO "バッチ種別";
        END IF;

        -- 依頼者（自由文字列）を 依頼者アカウントID ＋ 依頼元コード に置き換える
        ALTER TABLE public."BAT_バッチ実行履歴情報"
            ADD COLUMN IF NOT EXISTS "依頼者アカウントID" BIGINT NULL,
            ADD COLUMN IF NOT EXISTS "依頼元コード" VARCHAR(20) NULL;

        IF EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'BAT_バッチ実行履歴情報' AND column_name = '依頼者') THEN
            UPDATE public."BAT_バッチ実行履歴情報"
               SET "依頼元コード" = COALESCE("依頼元コード", LEFT(BTRIM("依頼者"), 20))
             WHERE "依頼者" IS NOT NULL AND BTRIM("依頼者") <> '';
            ALTER TABLE public."BAT_バッチ実行履歴情報" DROP COLUMN "依頼者";
            RAISE NOTICE '依頼者 を 依頼元コード へ移しました（人が特定できる場合は 依頼者アカウントID を使ってください）';
        END IF;

        -- 状態をコードへ変換する（未対応の値があれば中断）
        IF EXISTS (SELECT 1 FROM public."BAT_バッチ実行履歴情報"
                    WHERE "状態" NOT IN ('QUEUED','RUNNING','SUCCESS','FAILED','SKIPPED',
                                         '待機中','実行中','正常終了','異常終了','スキップ')) THEN
            RAISE EXCEPTION '2.1 で扱えない状態の値があります（% など）',
                (SELECT "状態" FROM public."BAT_バッチ実行履歴情報"
                  WHERE "状態" NOT IN ('QUEUED','RUNNING','SUCCESS','FAILED','SKIPPED',
                                       '待機中','実行中','正常終了','異常終了','スキップ') LIMIT 1);
        END IF;

        UPDATE public."BAT_バッチ実行履歴情報"
           SET "状態" = CASE "状態"
                            WHEN '正常終了' THEN 'SUCCESS'
                            WHEN '異常終了' THEN 'FAILED'
                            WHEN 'スキップ' THEN 'SKIPPED'
                            WHEN '待機中' THEN 'QUEUED'
                            WHEN '実行中' THEN 'RUNNING'
                            ELSE "状態"
                        END
         WHERE "状態" IN ('正常終了','異常終了','スキップ','待機中','実行中');

        -- 状態コード化に伴い、実行中は開始時刻が入っていることを保証する
        UPDATE public."BAT_バッチ実行履歴情報"
           SET "開始時刻" = COALESCE("開始時刻", "登録日時")
         WHERE "状態" IN ('RUNNING','SUCCESS','FAILED') AND "開始時刻" IS NULL;
    END IF;
END $$;

-- ----------------------------------------------------------------------------
-- 2. 制約を付け直す（旧 2.1 テーブルには制約が無かった）
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF to_regclass('public."BAT_バッチ実行履歴情報"') IS NULL THEN
        RETURN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'CK_BAT_実行履歴_バッチ種別'
                     AND conrelid = 'public."BAT_バッチ実行履歴情報"'::regclass) THEN
        ALTER TABLE public."BAT_バッチ実行履歴情報"
            ADD CONSTRAINT "CK_BAT_実行履歴_バッチ種別" CHECK ("バッチ種別" IN ('C','L','R'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'CK_BAT_実行履歴_起動種別'
                     AND conrelid = 'public."BAT_バッチ実行履歴情報"'::regclass) THEN
        ALTER TABLE public."BAT_バッチ実行履歴情報"
            ADD CONSTRAINT "CK_BAT_実行履歴_起動種別" CHECK ("起動種別" IN ('C','L','R'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'CK_BAT_実行履歴_状態'
                     AND conrelid = 'public."BAT_バッチ実行履歴情報"'::regclass) THEN
        ALTER TABLE public."BAT_バッチ実行履歴情報"
            ADD CONSTRAINT "CK_BAT_実行履歴_状態"
            CHECK ("状態" IN ('QUEUED','RUNNING','SUCCESS','FAILED','SKIPPED'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'CK_BAT_実行履歴_コード'
                     AND conrelid = 'public."BAT_バッチ実行履歴情報"'::regclass) THEN
        ALTER TABLE public."BAT_バッチ実行履歴情報"
            ADD CONSTRAINT "CK_BAT_実行履歴_コード" CHECK (BTRIM("バッチコード") <> '');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'CK_BAT_実行履歴_処理時間'
                     AND conrelid = 'public."BAT_バッチ実行履歴情報"'::regclass) THEN
        ALTER TABLE public."BAT_バッチ実行履歴情報"
            ADD CONSTRAINT "CK_BAT_実行履歴_処理時間"
            CHECK ("処理時間ms" IS NULL OR "処理時間ms" >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'CK_BAT_実行履歴_開始時刻'
                     AND conrelid = 'public."BAT_バッチ実行履歴情報"'::regclass) THEN
        ALTER TABLE public."BAT_バッチ実行履歴情報"
            ADD CONSTRAINT "CK_BAT_実行履歴_開始時刻"
            CHECK ("状態" IN ('QUEUED','SKIPPED') OR "開始時刻" IS NOT NULL);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'FK_BAT_実行履歴_依頼者'
                     AND conrelid = 'public."BAT_バッチ実行履歴情報"'::regclass) THEN
        ALTER TABLE public."BAT_バッチ実行履歴情報"
            ADD CONSTRAINT "FK_BAT_実行履歴_依頼者"
            FOREIGN KEY ("依頼者アカウントID")
            REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT;
    END IF;
END $$;

-- 主キー・NOT NULL の制約名はテーブル改名に追従しないため、新しい名前に揃える
DO $$
DECLARE
    target RECORD;
BEGIN
    IF to_regclass('public."BAT_バッチ実行履歴情報"') IS NULL THEN
        RETURN;
    END IF;
    FOR target IN
        SELECT conname FROM pg_constraint
         WHERE conrelid = 'public."BAT_バッチ実行履歴情報"'::regclass
           AND (conname LIKE 'BAT_タスク実行情報%' OR conname LIKE '%_タスクコード_not_null' OR conname LIKE '%_タスク種別_not_null')
    LOOP
        EXECUTE format('ALTER TABLE public."BAT_バッチ実行履歴情報" RENAME CONSTRAINT %I TO %I',
                       target.conname,
                       REPLACE(REPLACE(REPLACE(target.conname,
                                               'BAT_タスク実行情報', 'BAT_バッチ実行履歴情報'),
                                       'タスクコード', 'バッチコード'),
                               'タスク種別', 'バッチ種別'));
    END LOOP;
END $$;

-- 旧索引は名前が古いので落として、新しい索引（DDL 側）を作り直す
DROP INDEX IF EXISTS public.idx_bat_task_exec_01;
DROP INDEX IF EXISTS public.idx_bat_task_exec_02;
DROP INDEX IF EXISTS public.idx_bat_task_exec_03;

-- 索引・コメント・制約の最終形は DDL をそのまま適用する（既存テーブルには何もしない）
\ir ../バッチ/TBL_BAT_バッチ実行履歴情報.sql
\ir ../バッチ/TBL_BAT_バッチコントロール情報.sql

-- ----------------------------------------------------------------------------
-- 3. 有効／無効を COM_設定情報 から BAT_バッチコントロール情報 へ移す
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    moved INTEGER := 0;
BEGIN
    INSERT INTO public."BAT_バッチコントロール情報"
        ("バッチコード", "状態", "備考", "登録元コード", "更新元コード")
    SELECT REPLACE(s."設定キー", 'BATCH_TASK_ENABLED_', ''),
           CASE WHEN BTRIM(s."設定値") IN ('1', 'true', 'TRUE', 'on', 'ON') THEN '1' ELSE '0' END,
           'バッチ管理画面の有効設定（OFF時は定時実行しない）',
           'MIGRATION',
           'MIGRATION'
      FROM public."COM_設定情報" s
     WHERE s."設定キー" LIKE 'BATCH_TASK_ENABLED_%'
    ON CONFLICT ("バッチコード") DO NOTHING;

    GET DIAGNOSTICS moved = ROW_COUNT;
    IF moved > 0 THEN
        RAISE NOTICE 'COM_設定情報 から % 件の有効設定を移しました', moved;
    END IF;
END $$;

-- 移行済みのキーは消す（有効／無効の二重管理を避ける）
DELETE FROM public."COM_設定情報" WHERE "設定キー" LIKE 'BATCH_TASK_ENABLED_%';

COMMIT;

\echo '--- 結果: 実行履歴（バッチ別の件数）---'
SELECT "バッチ種別", count(*) AS "件数"
  FROM public."BAT_バッチ実行履歴情報"
 GROUP BY 1 ORDER BY 1;

\echo '--- 結果: 実行履歴（状態）---'
SELECT "状態", count(*) AS "件数" FROM public."BAT_バッチ実行履歴情報" GROUP BY 1 ORDER BY 1;

\echo '--- 結果: バッチコントロール ---'
SELECT "バッチコード", "状態", "最終実行日時", "登録元コード"
  FROM public."BAT_バッチコントロール情報" ORDER BY "バッチコード";

\echo '--- 確認: 旧テーブル／旧キーが残っていないこと ---'
SELECT to_regclass('public."BAT_タスク実行情報"') AS "旧テーブル",
       (SELECT count(*) FROM public."COM_設定情報" WHERE "設定キー" LIKE 'BATCH_TASK_ENABLED_%') AS "旧キー件数";
