-- ============================================================================
-- Study 2.1  バッチ実行履歴・バッチコントロールのデータ移行
--   2.0 study2.BAT_タスク実行情報      -> study21.BAT_バッチ実行履歴情報（4,139 件）
--   2.0 の有効／無効設定                -> study21.BAT_バッチコントロール情報
-- ----------------------------------------------------------------------------
-- 事前条件:
--   1. database/バッチ/TBL_BAT_バッチ実行履歴情報.sql
--   2. database/バッチ/TBL_BAT_バッチコントロール情報.sql
--   3. 移行元: study2 の BAT_タスク実行情報（別 DB。dblink で読む）
--
-- 実行例:
--   psql -U postgres -d study21 -v ON_ERROR_STOP=1 -f MIG_BAT_実行履歴_20260912.sql
--
-- 冪等性:
--   実行ID を維持して ON CONFLICT DO NOTHING で投入するため、何度実行しても
--   結果は変わらない（既存行は書き換えない）。
--
-- 値の対応:
--   タスクコード : 'batL01' は 2.1 で 'batS01' に改名したため読み替える
--   状態         : 正常終了->SUCCESS / 異常終了->FAILED / スキップ->SKIPPED
--                  待機中->QUEUED / 実行中->FAILED（下記 3. 参照）
--   依頼者       : ACC_アカウント のログインID と一致すれば 依頼者アカウントID、
--                  一致しなければ 依頼元コード に入れる（人が特定できない値のため）
--
-- 移行時に補正する点（2.1 の CHECK 制約に合わせる）:
--   1. 2.0 に「開始時刻が NULL なのに 正常終了/異常終了」の行が 3 件ある
--      （batC16/batC17/batC18・2026-08-04）。開始時刻を
--      終了時刻 -> 予定時刻 -> 登録日時 の順で補完する。
--   2. 2.0 で「実行中」のまま残っている行（移行時点で 2 件。2.0 が同時刻に
--      起動した batL03/batR01）は、2.1 で「実行中」のまま残すと
--      二重起動の判定を塞ぐため FAILED として記録し、理由を エラー詳細 に残す。
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. 2.0 の履歴を一時表へ取り込む（この時点の内容をスナップショットとする）
-- ----------------------------------------------------------------------------
CREATE TEMP TABLE migr_bat_history ON COMMIT DROP AS
SELECT *
  FROM dblink('dbname=study2',
              $q$
              SELECT "実行ID", "タスクコード", "タスク種別", "起動種別", "状態", "要求内容",
                     "依頼者", "予定時刻", "開始時刻", "終了時刻", "処理時間ms",
                     "メッセージ", "エラー詳細", "登録日時", "更新日時"
                FROM public."BAT_タスク実行情報"
              $q$)
       AS t("実行ID" BIGINT, "タスクコード" VARCHAR(20), "タスク種別" CHAR(1), "起動種別" CHAR(1),
            "状態" VARCHAR(20), "要求内容" JSONB, "依頼者" VARCHAR(100),
            "予定時刻" TIMESTAMP, "開始時刻" TIMESTAMP, "終了時刻" TIMESTAMP, "処理時間ms" BIGINT,
            "メッセージ" TEXT, "エラー詳細" TEXT, "登録日時" TIMESTAMP, "更新日時" TIMESTAMP);

DO $$
DECLARE
    history_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO history_count FROM migr_bat_history;
    RAISE NOTICE '2.0 のバッチ実行履歴 % 件を移行します', history_count;

    -- 想定外の値が混ざっていたら止める（2.0 側の仕様変更に気づけるように）
    IF EXISTS (SELECT 1 FROM migr_bat_history
                WHERE "状態" NOT IN ('正常終了', '異常終了', 'スキップ', '待機中', '実行中')) THEN
        RAISE EXCEPTION '2.0 batch history contains an unsupported status';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_bat_history WHERE "タスク種別" NOT IN ('C', 'L', 'R')) THEN
        RAISE EXCEPTION '2.0 batch history contains an unsupported task type';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_bat_history WHERE "起動種別" NOT IN ('C', 'L', 'R')) THEN
        RAISE EXCEPTION '2.0 batch history contains an unsupported trigger type';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_bat_history WHERE LENGTH("依頼者") > 20) THEN
        RAISE EXCEPTION '2.0 batch history contains a requester name longer than 20 characters';
    END IF;

    -- 既に移行済みの ID が別内容で入っていたら止める（上書き事故を防ぐ）
    IF EXISTS (
        SELECT 1
          FROM migr_bat_history s
          JOIN public."BAT_バッチ実行履歴情報" t ON t."実行ID" = s."実行ID"
         WHERE t."登録日時" <> s."登録日時"
            OR t."状態" <> CASE s."状態"
                               WHEN '正常終了' THEN 'SUCCESS'
                               WHEN '異常終了' THEN 'FAILED'
                               WHEN 'スキップ' THEN 'SKIPPED'
                               WHEN '待機中' THEN 'QUEUED'
                               ELSE 'FAILED'
                           END
    ) THEN
        RAISE EXCEPTION 'A target batch history row already exists with different data';
    END IF;
END $$;

-- ----------------------------------------------------------------------------
-- 2. 移行する（実行ID は 2.0 の値を維持する）
-- ----------------------------------------------------------------------------
INSERT INTO public."BAT_バッチ実行履歴情報" (
    "実行ID", "バッチコード", "バッチ種別", "起動種別", "状態", "要求内容",
    "依頼者アカウントID", "依頼元コード", "予定時刻", "開始時刻", "終了時刻",
    "処理時間ms", "メッセージ", "エラー詳細", "登録日時", "更新日時"
)
SELECT
    s."実行ID",
    -- 2.0 の batL01 は 2.1 で batS01（プロキシサービス）
    CASE WHEN s."タスクコード" = 'batL01' THEN 'batS01' ELSE s."タスクコード" END,
    s."タスク種別",
    s."起動種別",
    CASE s."状態"
        WHEN '正常終了' THEN 'SUCCESS'
        WHEN '異常終了' THEN 'FAILED'
        WHEN 'スキップ' THEN 'SKIPPED'
        WHEN '待機中'   THEN 'QUEUED'
        ELSE 'FAILED'          -- '実行中'（移行時に中断していたもの）
    END,
    s."要求内容",
    a."アカウントID",
    CASE WHEN a."アカウントID" IS NULL THEN s."依頼者" ELSE NULL END,
    s."予定時刻",
    -- 開始時刻が NULL のまま 正常終了/異常終了 になっている行を補完する
    CASE
        WHEN s."開始時刻" IS NOT NULL THEN s."開始時刻"
        WHEN s."状態" IN ('正常終了', '異常終了') THEN COALESCE(s."終了時刻", s."予定時刻", s."登録日時")
        ELSE NULL
    END,
    s."終了時刻",
    s."処理時間ms",
    s."メッセージ",
    CASE
        WHEN s."状態" = '実行中' THEN
            COALESCE(NULLIF(s."エラー詳細", '') || E'\n', '')
            || '2.0 からの移行時に実行中のまま残っていたため、異常終了として記録しました。'
        ELSE s."エラー詳細"
    END,
    s."登録日時",
    s."更新日時"
  FROM migr_bat_history s
  LEFT JOIN public."ACC_アカウント" a ON a."ログインID" = s."依頼者"
 ON CONFLICT ("実行ID") DO NOTHING;

-- 実行ID は BIGSERIAL。移行した最大値の次から採番させる
SELECT setval(
    pg_get_serial_sequence('public."BAT_バッチ実行履歴情報"', '実行ID'),
    GREATEST((SELECT COALESCE(MAX("実行ID"), 0) FROM public."BAT_バッチ実行履歴情報"), 1),
    true
);

-- 件数の検証（移行漏れがあれば止める）
DO $$
DECLARE
    source_count BIGINT;
    migrated_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO source_count FROM migr_bat_history;
    SELECT COUNT(*) INTO migrated_count
      FROM public."BAT_バッチ実行履歴情報" t
      JOIN migr_bat_history s ON s."実行ID" = t."実行ID";

    IF source_count <> migrated_count THEN
        RAISE EXCEPTION 'Batch history count mismatch: source %, migrated %', source_count, migrated_count;
    END IF;
    RAISE NOTICE 'バッチ実行履歴 % 件を移行しました', migrated_count;
END $$;

-- ----------------------------------------------------------------------------
-- 3. バッチコントロール（有効／無効）
--    2.0 の COM_設定情報 BATCH_TASK_ENABLED_* は引き継がない。
--    2.1 では batS01（プロキシサービス）だけを有効にし、他は無効から始める
--    （手動実行は無効でも可能。有効は「起動時に実行する」の意味）。
-- ----------------------------------------------------------------------------
INSERT INTO public."BAT_バッチコントロール情報" (
    "バッチコード", "状態", "備考", "登録元コード"
)
SELECT code,
       CASE WHEN code = 'batS01' THEN '1' ELSE '0' END,
       'バッチ管理画面の有効設定（OFF時は定時実行しない）',
       'MIGRATION'
  FROM (VALUES ('batS01'), ('batL02'), ('batL03'),
               ('batR01'), ('batR02'), ('batR03'), ('batR04'), ('batR05')) AS v(code)
 ON CONFLICT ("バッチコード") DO NOTHING;

-- ----------------------------------------------------------------------------
-- 4. 結果の確認
-- ----------------------------------------------------------------------------
SELECT "バッチコード", COUNT(*) AS "件数", MIN("登録日時") AS "最古", MAX("登録日時") AS "最新"
  FROM public."BAT_バッチ実行履歴情報"
 GROUP BY 1
 ORDER BY 2 DESC;

SELECT "バッチコード", "状態", "備考"
  FROM public."BAT_バッチコントロール情報"
 ORDER BY "状態" DESC, 1;

COMMIT;
