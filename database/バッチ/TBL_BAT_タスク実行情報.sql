-- ============================================================================
-- Study 2.1  バッチ実行記録  DDL
-- テーブル: BAT_タスク実行情報
-- ----------------------------------------------------------------------------
-- 旧 000_BAT_タスク実行情報.sql と同等の構造を study21 向けに作成。
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."BAT_タスク実行情報" (
    "実行ID"     BIGSERIAL PRIMARY KEY,
    "タスクコード" VARCHAR(20)  NOT NULL,
    "タスク種別"   CHAR(1)      NOT NULL,
    "起動種別"     CHAR(1)      NOT NULL,
    "状態"         VARCHAR(20)  NOT NULL,
    "要求内容"     JSONB        NULL,
    "依頼者"       VARCHAR(100) NULL,
    "予定時刻"     TIMESTAMP    NULL,
    "開始時刻"     TIMESTAMP    NULL,
    "終了時刻"     TIMESTAMP    NULL,
    "処理時間ms"   BIGINT       NULL,
    "メッセージ"   TEXT         NULL,
    "エラー詳細"   TEXT         NULL,
    "登録日時"     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bat_task_exec_01
    ON public."BAT_タスク実行情報" ("タスクコード", "登録日時" DESC);
CREATE INDEX IF NOT EXISTS idx_bat_task_exec_02
    ON public."BAT_タスク実行情報" ("状態");
CREATE INDEX IF NOT EXISTS idx_bat_task_exec_03
    ON public."BAT_タスク実行情報" ("タスク種別", "予定時刻" DESC);
