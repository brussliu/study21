-- ============================================================================
-- Study 2.1  バッチの実行スケジュール設定（可変化）とスケジュール状態の導入
-- ----------------------------------------------------------------------------
-- 追加・変更するもの:
--   1. COM_設定項目 … 実行スケジュールの設定 6 件（カタログ）
--        * NET_CONTROL_START_TIME / NET_CONTROL_END_TIME（batR04 / batR03 の実行時刻）
--        * STUDY_MONITOR_L02_INTERVAL_MINUTES / _OFFSET_MINUTES（batL02 の実行設定）
--        * STUDY_MONITOR_L03_INTERVAL_MINUTES / _OFFSET_MINUTES（batL03 の実行設定）
--   2. COM_設定情報 … 1 の初期値（**未登録のときだけ**入れる。利用者が保存した値は上書きしない）
--   3. BAT_スケジュール状態情報 … 新規テーブル（DDL は
--      database/バッチ/TBL_BAT_スケジュール状態情報.sql。ここでも冪等に作る）
--   4. BAT_バッチ実行履歴情報 … 計画実行点で引くための索引（予定時刻つきの行だけ）
--
-- 背景（利用者の指示。2026-09-19）:
--   2.0 は batR03（23:30）・batR04（06:30）の実行時刻が**コードと通知文に固定**で、
--   batL02 / batL03 も「5 分ごと」の固定スケジューラ（BatL02FiveMinuteScheduler）だった。
--   2.1 は実行時刻・実行間隔・ずらしを COM_設定項目 / COM_設定情報 で設定できるようにし、
--   有効／無効は **BAT_バッチコントロール情報 を唯一の正**とする（2 つ目のスイッチを作らない）。
--
-- 用語（混ぜない）:
--   * 実行間隔 / ずらし  … バッチをいつ動かすか（この移行で追加）
--   * 動画処理時間帯      … どの撮影時刻の動画を取り込むか（STUDY_MONITOR_VIDEO_PROCESSING_*）
--   * スナップショット間隔 … 動画から何秒ごとに画像を切り出すか（STUDY_MONITOR_SNAPSHOT_INTERVAL_SECONDS）
--
-- 初期値（利用者の指定）:
--   * batR04 利用開始 06:30 / batR03 利用終了 23:30
--   * batL02 間隔 5 分・ずらし 1 分（毎時 01, 06, 11, …, 56 分）
--   * batL03 間隔 5 分・ずらし 0 分（毎時 00, 05, 10, …, 55 分）
--
-- 冪等: COM_設定項目 は ON CONFLICT DO NOTHING、COM_設定情報 は ON CONFLICT DO NOTHING
--       （既存値を**上書きしない**）、テーブル・索引は IF NOT EXISTS。
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: database/設定/TBL_COM_設定項目_init.sql・TBL_COM_設定情報_init.sql、
--           database/バッチ/TBL_BAT_バッチコントロール情報.sql、
--           database/バッチ/TBL_BAT_バッチ実行履歴情報.sql の後
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. 設定カタログ（COM_設定項目）
-- ----------------------------------------------------------------------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('NET_CONTROL','NET_CONTROL_START_TIME','TIME','1','HH:mm','ネットワーク制御：インターネット利用の開始時刻（batR04。端末を通常モードTへ戻す）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('NET_CONTROL','NET_CONTROL_END_TIME','TIME','1','HH:mm','ネットワーク制御：インターネット利用の終了時刻（batR03。端末を停止モードSへ切り替える）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_L02_INTERVAL_MINUTES','ENUM','1','1,5,10,15,30,60','学習モニター：batL02（動画取込・スナップショット切出）の実行間隔（分）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_L02_OFFSET_MINUTES','INTEGER','1','0..59','学習モニター：batL02 の実行タイミングのずらし（分。0〜実行間隔-1。毎時「ずらし + n×間隔」分に実行）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_L03_INTERVAL_MINUTES','ENUM','1','1,5,10,15,30,60','学習モニター：batL03（スナップショットAI分析）の実行間隔（分）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_L03_OFFSET_MINUTES','INTEGER','1','0..59','学習モニター：batL03 の実行タイミングのずらし（分。0〜実行間隔-1）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2. 初期値（COM_設定情報）。**既に行があるときは触らない**（利用者の保存値を守る）
-- ----------------------------------------------------------------------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値")
VALUES ('NET_CONTROL','NET_CONTROL_START_TIME','GLOBAL','06:30')
ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値")
VALUES ('NET_CONTROL','NET_CONTROL_END_TIME','GLOBAL','23:30')
ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_L02_INTERVAL_MINUTES','GLOBAL','5')
ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_L02_OFFSET_MINUTES','GLOBAL','1')
ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_L03_INTERVAL_MINUTES','GLOBAL','5')
ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_L03_OFFSET_MINUTES','GLOBAL','0')
ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3. スケジュール状態（新規テーブル。スイッチは持たない＝有効／無効は
--    BAT_バッチコントロール情報 が唯一の正）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public."BAT_スケジュール状態情報" (
    "バッチコード"       VARCHAR(20)  NOT NULL,
    "最終予定日時"       TIMESTAMP    NULL,
    "最終実行ID"         BIGINT       NULL,
    "最終確保日時"       TIMESTAMP    NULL,
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "PK_BAT_スケジュール状態" PRIMARY KEY ("バッチコード"),
    CONSTRAINT "CK_BAT_スケジュール_コード" CHECK (BTRIM("バッチコード") <> ''),
    CONSTRAINT "CK_BAT_スケジュール_バージョン" CHECK ("バージョン" > 0)
);

COMMENT ON TABLE public."BAT_スケジュール状態情報" IS
    'バッチごとのスケジュール確保状態（最後に確保した計画実行点）。30 秒スケジューラの二重実行防止';
COMMENT ON COLUMN public."BAT_スケジュール状態情報"."最終予定日時" IS
    '最後に確保した計画実行点（Asia/Tokyo のローカル時刻）。この値より新しい点だけを確保できる';

-- ----------------------------------------------------------------------------
-- 4. 実行履歴の索引（計画実行点で引く）
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_bat_execution_code_schedule
    ON public."BAT_バッチ実行履歴情報" ("バッチコード", "予定時刻" DESC)
    WHERE "予定時刻" IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_bat_schedule_code
    ON public."BAT_スケジュール状態情報" ("バッチコード");

COMMIT;

-- ----------------------------------------------------------------------------
-- 確認（実行後に目視する。値は 2026-09-19 時点の想定）
-- ----------------------------------------------------------------------------
\echo '--- 確認1: カタログ（6 件）---'
SELECT "ページ区分", "設定キー", "値タイプ", "有効値"
  FROM public."COM_設定項目"
 WHERE "設定キー" IN ('NET_CONTROL_START_TIME','NET_CONTROL_END_TIME',
                      'STUDY_MONITOR_L02_INTERVAL_MINUTES','STUDY_MONITOR_L02_OFFSET_MINUTES',
                      'STUDY_MONITOR_L03_INTERVAL_MINUTES','STUDY_MONITOR_L03_OFFSET_MINUTES')
 ORDER BY 1,2;

\echo '--- 確認2: 初期値（未登録のときだけ入る。既存値は変わらない）---'
SELECT "ページ区分", "設定キー", "設定値"
  FROM public."COM_設定情報"
 WHERE "設定キー" IN ('NET_CONTROL_START_TIME','NET_CONTROL_END_TIME',
                      'STUDY_MONITOR_L02_INTERVAL_MINUTES','STUDY_MONITOR_L02_OFFSET_MINUTES',
                      'STUDY_MONITOR_L03_INTERVAL_MINUTES','STUDY_MONITOR_L03_OFFSET_MINUTES')
 ORDER BY 1,2;

\echo '--- 確認3: スケジュール状態テーブル（0 行から始まる）---'
SELECT count(*) AS rows FROM public."BAT_スケジュール状態情報";

\echo '--- 確認4: 実行履歴の索引 ---'
SELECT indexname FROM pg_indexes
 WHERE tablename = 'BAT_バッチ実行履歴情報' AND indexname = 'idx_bat_execution_code_schedule';
