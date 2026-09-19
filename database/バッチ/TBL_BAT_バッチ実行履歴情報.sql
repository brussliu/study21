-- ============================================================================
-- Study 2.1  バッチ実行履歴 DDL（最終仕様）
-- テーブル: BAT_バッチ実行履歴情報
-- ----------------------------------------------------------------------------
-- 旧 "BAT_タスク実行情報" を改名・再設計したもの（2.0 の同名テーブルに対応）。
-- バッチの実行そのものを 1 行 = 1 実行で記録する。有効／無効（定時実行の可否）は
-- BAT_バッチコントロール情報 が持つ。
--
-- 2.0 / 旧 2.1 からの主な変更:
--   1. テーブル名を BAT_タスク実行情報 → BAT_バッチ実行履歴情報 に変更した
--      （2.1 の用語を「バッチ」に統一。列名も タスクコード → バッチコード など）。
--   2. 状態を日本語（正常終了 / 異常終了 / スキップ）からコードへ変更した
--      （QUEUED / RUNNING / SUCCESS / FAILED / SKIPPED、CHECK 制約付き）。
--      表示文言はフロント側でローカライズする。
--   3. 依頼者（VARCHAR(100) の自由文字列）を 依頼者アカウントID（ACC_アカウント への FK）
--      ＋ 依頼元コード に分けた。2.0 の実データは 'scheduler' 'batL02-scheduler'
--      'word.jsp' 'liu' のように人が特定できない値が大半だったため、両方を残す。
--   4. 実行時間（処理時間ms）とエラー詳細を実際に記録する（列は 2.0 からあるが
--      2.1 の実装では未使用だった）。
--   5. 索引を新しい参照パターン（バッチ別の履歴、未完了の探索、種別ごとの予定）に合わせた。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."BAT_バッチ実行履歴情報" (
    "実行ID"             BIGSERIAL    NOT NULL,
    -- どのバッチか（例: batL01 / batC04 / batR03）
    "バッチコード"       VARCHAR(20)  NOT NULL,
    -- 'C'=呼出（画面などから随時） / 'L'=循環（一定間隔） / 'R'=定時
    "バッチ種別"         CHAR(1)      NOT NULL,
    -- 起動のされ方（'C' / 'L' / 'R'）。種別と揃うのが基本だが、手動起動の記録にも使う
    "起動種別"           CHAR(1)      NOT NULL,
    -- QUEUED=待機中 / RUNNING=実行中 / SUCCESS=正常終了 / FAILED=異常終了 / SKIPPED=スキップ
    "状態"               VARCHAR(20)  NOT NULL,
    -- 実行時の要求内容（対象ID・パラメータなど）。任意
    "要求内容"           JSONB        NULL,
    -- 人が起動した場合の実行者。定時・循環・システム起動では NULL
    "依頼者アカウントID" BIGINT       NULL,
    -- 人が起動していない場合の識別子（例: scheduler / batL02-scheduler / word.jsp）
    "依頼元コード"       VARCHAR(20)  NULL,
    "予定時刻"           TIMESTAMP    NULL,
    "開始時刻"           TIMESTAMP    NULL,
    "終了時刻"           TIMESTAMP    NULL,
    -- 開始から終了までの処理時間（ミリ秒）
    "処理時間ms"         BIGINT       NULL,
    -- 正常終了・スキップの要約メッセージ
    "メッセージ"         TEXT         NULL,
    -- 異常終了時のスタックトレースなど
    "エラー詳細"         TEXT         NULL,
    -- 再起動の復旧で「この実行をやり直すために作った実行」の元の実行ID。
    -- 同じ元実行から 2 つやり直しを作らないよう、**部分一意索引**（下）で守る
    -- （復旧は「旧実行を閉じる＋やり直しを作る」を 1 トランザクションで行う）
    "元実行ID"           BIGINT       NULL,
    -- **どのプロセス（admin-api の起動）が作った実行か**を示す起動識別子。
    -- 再起動の復旧は「前のプロセスが残した実行」だけを扱う（この値が現在のプロセスと違う行）。
    -- NULL は「この列が無かった頃の行」＝ 旧構造の遺留として扱う（復旧の対象にする）
    "起動識別子"         VARCHAR(40)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "BAT_バッチ実行履歴情報_pkey"
        PRIMARY KEY ("実行ID"),
    CONSTRAINT "FK_BAT_実行履歴_依頼者"
        FOREIGN KEY ("依頼者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_BAT_実行履歴_バッチ種別"
        CHECK ("バッチ種別" IN ('C', 'L', 'R', 'S')),
    CONSTRAINT "CK_BAT_実行履歴_起動種別"
        CHECK ("起動種別" IN ('C', 'L', 'R', 'S')),
    CONSTRAINT "CK_BAT_実行履歴_状態"
        CHECK ("状態" IN ('QUEUED', 'RUNNING', 'SUCCESS', 'FAILED', 'SKIPPED')),
    CONSTRAINT "CK_BAT_実行履歴_コード"
        CHECK (BTRIM("バッチコード") <> ''),
    CONSTRAINT "CK_BAT_実行履歴_処理時間"
        CHECK ("処理時間ms" IS NULL OR "処理時間ms" >= 0),
    -- 実行中は開始時刻が入っていること（状態と時刻の食い違いを防ぐ）
    CONSTRAINT "CK_BAT_実行履歴_開始時刻"
        CHECK ("状態" IN ('QUEUED', 'SKIPPED') OR "開始時刻" IS NOT NULL)
);

-- バッチ別の履歴一覧（画面は「直近の実行」を新しい順に出す）。
CREATE INDEX IF NOT EXISTS idx_bat_history_code_registered
    ON public."BAT_バッチ実行履歴情報" ("バッチコード", "登録日時" DESC, "実行ID" DESC);

-- 未完了（待機中・実行中）の探索。二重起動の防止とスケジューラの復帰に使う。
CREATE INDEX IF NOT EXISTS idx_bat_history_unfinished
    ON public."BAT_バッチ実行履歴情報" ("バッチコード", "状態")
    WHERE "状態" IN ('QUEUED', 'RUNNING');

-- 復旧のやり直し関係（元実行ID）の一意性。**同じ元実行からやり直しは 1 つだけ**。
-- NULL（通常の実行）は部分索引の対象外なので、通常の実行には影響しない。
CREATE UNIQUE INDEX IF NOT EXISTS "UQ_BAT_実行履歴_元実行ID"
    ON public."BAT_バッチ実行履歴情報" ("元実行ID")
    WHERE "元実行ID" IS NOT NULL;

-- 種別ごとの予定（定時バッチの一覧）。
CREATE INDEX IF NOT EXISTS idx_bat_history_type_schedule
    ON public."BAT_バッチ実行履歴情報" ("バッチ種別", "予定時刻" DESC);

COMMENT ON TABLE public."BAT_バッチ実行履歴情報" IS
    'バッチの実行履歴（1 行 = 1 実行）。有効／無効は BAT_バッチコントロール情報 が持つ';
COMMENT ON COLUMN public."BAT_バッチ実行履歴情報"."バッチコード" IS 'バッチ定義（BatchTaskRegistry）のコード。例: batS01 / batC04 / batR03';
COMMENT ON COLUMN public."BAT_バッチ実行履歴情報"."元実行ID" IS '再起動の復旧でやり直しを作ったときの元の実行ID（同じ元実行から 1 つだけ）';
COMMENT ON COLUMN public."BAT_バッチ実行履歴情報"."起動識別子" IS '実行記録を作ったプロセスの起動識別子。再起動の復旧は「現在と違う値（NULL を含む）」だけを遺留として扱う';

-- 再起動の復旧が「前のプロセスの遺留」を引くための索引（未完了だけ）。
CREATE INDEX IF NOT EXISTS idx_bat_history_leftover
    ON public."BAT_バッチ実行履歴情報" ("状態", "起動識別子")
    WHERE "状態" IN ('QUEUED', 'RUNNING');
COMMENT ON COLUMN public."BAT_バッチ実行履歴情報"."バッチ種別" IS 'C=呼出（随時） / L=循環（一定間隔） / R=定時 / S=システム起動時';
COMMENT ON COLUMN public."BAT_バッチ実行履歴情報"."起動種別" IS '起動のされ方（C/L/R/S）';
COMMENT ON COLUMN public."BAT_バッチ実行履歴情報"."状態" IS
    'QUEUED=待機中 / RUNNING=実行中 / SUCCESS=正常終了 / FAILED=異常終了 / SKIPPED=スキップ';
COMMENT ON COLUMN public."BAT_バッチ実行履歴情報"."依頼者アカウントID" IS
    '画面から起動したときの実行者。定時・循環・システム起動では NULL';
COMMENT ON COLUMN public."BAT_バッチ実行履歴情報"."依頼元コード" IS
    '人が起動していない場合の識別子（STARTUP / scheduler / word.jsp など）';
COMMENT ON COLUMN public."BAT_バッチ実行履歴情報"."処理時間ms" IS '開始から終了までのミリ秒。未完了の間は NULL';

-- ----------------------------------------------------------------------------
-- 既存テーブルへの追補（種別 S = システム起動時のバッチを追加したときの差分）
-- CREATE TABLE IF NOT EXISTS は既存テーブルには効かないため、制約を貼り直す。
-- 何度実行しても結果は変わらない。
-- ----------------------------------------------------------------------------
ALTER TABLE public."BAT_バッチ実行履歴情報"
    DROP CONSTRAINT IF EXISTS "CK_BAT_実行履歴_バッチ種別";
ALTER TABLE public."BAT_バッチ実行履歴情報"
    ADD CONSTRAINT "CK_BAT_実行履歴_バッチ種別"
        CHECK ("バッチ種別" IN ('C', 'L', 'R', 'S'));

ALTER TABLE public."BAT_バッチ実行履歴情報"
    DROP CONSTRAINT IF EXISTS "CK_BAT_実行履歴_起動種別";
ALTER TABLE public."BAT_バッチ実行履歴情報"
    ADD CONSTRAINT "CK_BAT_実行履歴_起動種別"
        CHECK ("起動種別" IN ('C', 'L', 'R', 'S'));
