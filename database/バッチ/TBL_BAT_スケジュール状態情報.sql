-- ============================================================================
-- Study 2.1  バッチのスケジュール状態 DDL
-- テーブル: BAT_スケジュール状態情報
-- ----------------------------------------------------------------------------
-- 「どの計画実行点まで確保（claim）したか」を**バッチごとに 1 行**で持つ。
-- 2.1 の統一スケジューラ（30 秒ごと）は、起動中のメモリではなく**この行**で
-- 実行済み／未実行を判定する（再起動・多重起動・ポーリングのゆらぎで
-- 同じ計画実行点を二重に走らせないため）。
--
-- 計画実行点（予定日時）の決め方:
--   * 種別 R（定時・batR03 / batR04）… COM_設定情報 の時刻（例 23:30）のその日の 1 点
--   * 種別 L（循環・batL02 / batL03）… 「毎時 ずらし分 + n × 実行間隔」の各点
--     （例: 間隔 5 分・ずらし 1 分 → 01, 06, 11, …, 56 分）
--   いずれも **Asia/Tokyo のローカル時刻**（サーバーの OS タイムゾーンに依存しない）。
--
-- 確保（claim）は次の 1 文で行う（同時に走っても 1 つだけが勝つ）:
--   INSERT ... ON CONFLICT ("バッチコード") DO UPDATE
--      SET "最終予定日時" = EXCLUDED."最終予定日時" ...
--    WHERE "BAT_スケジュール状態情報"."最終予定日時" IS NULL
--       OR "BAT_スケジュール状態情報"."最終予定日時" < EXCLUDED."最終予定日時"
--   更新行数 1 = 確保できた / 0 = 他の実行（または過去の実行）が既に確保済み。
--
-- 注: 有効／無効（自動実行するか）は **BAT_バッチコントロール情報 が唯一の正**。
--     この表はスイッチを持たない（2 つ目のスイッチを作らない）。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: database/バッチ/TBL_BAT_バッチコントロール情報.sql の後（依存は無いが揃える）
-- 冪等: CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."BAT_スケジュール状態情報" (
    -- バッチコード（BAT_バッチコントロール情報・BAT_バッチ実行履歴情報 と同じ値）
    "バッチコード"       VARCHAR(20)  NOT NULL,
    -- 最後に確保した計画実行点（Asia/Tokyo のローカル時刻。未確保なら NULL）
    "最終予定日時"       TIMESTAMP    NULL,
    -- いまの実行設定が**効き始めた時刻**（利用者が最後に時刻・間隔・ずらしを変えた時刻）。
    -- この時刻以前の計画実行点は実行しない（設定を変えた直後に過去の点を今さら実行しないため）。
    -- サービス再起動でもこの値を引き継ぎ、「設定変更」と「再起動の補執行」を区別する
    "設定適用日時"       TIMESTAMP    NULL,
    -- その計画実行点で登録した実行ID（BAT_バッチ実行履歴情報。監査用。FK は張らない）
    "最終実行ID"         BIGINT       NULL,
    -- 確保した時刻（デバッグ・監視用）
    "最終確保日時"       TIMESTAMP    NULL,
    -- 確保の通し番号（楽観的ロック・監査用）
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "PK_BAT_スケジュール状態" PRIMARY KEY ("バッチコード"),
    CONSTRAINT "CK_BAT_スケジュール_コード" CHECK (BTRIM("バッチコード") <> ''),
    CONSTRAINT "CK_BAT_スケジュール_バージョン" CHECK ("バージョン" > 0)
);

COMMENT ON TABLE public."BAT_スケジュール状態情報" IS
    'バッチごとのスケジュール確保状態（最後に確保した計画実行点）。30 秒スケジューラの二重実行防止';
COMMENT ON COLUMN public."BAT_スケジュール状態情報"."バッチコード" IS
    'バッチコード（batR03 / batR04 / batL02 / batL03 など）';
COMMENT ON COLUMN public."BAT_スケジュール状態情報"."最終予定日時" IS
    '最後に確保した計画実行点（Asia/Tokyo のローカル時刻）。この値より新しい点だけを確保できる';
COMMENT ON COLUMN public."BAT_スケジュール状態情報"."設定適用日時" IS
    'いまの実行設定が効き始めた時刻（Asia/Tokyo）。この時刻以前の計画実行点は実行しない';
COMMENT ON COLUMN public."BAT_スケジュール状態情報"."最終実行ID" IS
    'その計画実行点で登録した BAT_バッチ実行履歴情報.実行ID（監査用。FK は張らない）';
COMMENT ON COLUMN public."BAT_スケジュール状態情報"."最終確保日時" IS
    '最後に確保した実時刻（監視・デバッグ用）';

-- スケジューラはバッチコードで 1 行ずつ引く（主キーで足りる）。全件を読む用途（画面の
-- 「次回実行時刻」一覧）もあるため、主キーの昇順で安定させる。
CREATE INDEX IF NOT EXISTS idx_bat_schedule_code
    ON public."BAT_スケジュール状態情報" ("バッチコード");

-- 実行履歴から「そのバッチの最後の計画実行点」を引くための索引（予定時刻があるものだけ）。
CREATE INDEX IF NOT EXISTS idx_bat_execution_code_schedule
    ON public."BAT_バッチ実行履歴情報" ("バッチコード", "予定時刻" DESC)
    WHERE "予定時刻" IS NOT NULL;
