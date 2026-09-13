-- ============================================================================
-- Study 2.1  バッチコントロール DDL（最終仕様）
-- テーブル: BAT_バッチコントロール情報
-- ----------------------------------------------------------------------------
-- バッチ管理画面の「有効／無効」を保持する専用テーブル。
--
-- 2.0 は COM_設定情報 に BATCH_TASK_ENABLED_<バッチコード> というキーで
-- '1'/'0' を保存していた（BatchTaskActivationService）。設定テーブルは
-- 画面の入力値や AI プロンプトなどと同居しており、
--   * バッチの運転状態を一覧で取りにくい
--   * 実行結果（最終実行日時など）と同じ場所で管理できない
--   * 監査（誰がいつ切り替えたか）を残しにくい
-- といった問題があったため、2.1 では専用テーブルへ移した。
--
-- 既定値・備考は 2.0 の運用を引き継ぐ:
--   * 有効（状態='1'）= 定時・循環で実行する。無効（'0'）は定時実行しない（手動実行は可）
--   * 備考の文言は 2.0 と同じ「バッチ管理画面の有効設定（OFF時は定時実行しない）」
--   * 行が無いバッチは「定義側の既定値」で動作する（2.0 と同じ）
--
-- 切り替えられるのは 種別 L（循環）と R（定時）のバッチだけ
-- （2.0 のメッセージ「有効設定を変更できるのは batL / batR のみです。」を踏襲）。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."BAT_バッチコントロール情報" (
    -- バッチ定義（BatchTaskRegistry）のコード。定義側の変更に追従できるよう
    -- 主キーはコード（連番ではない）
    "バッチコード"       VARCHAR(20)  NOT NULL,
    -- '1'=有効（定時・循環で実行する） / '0'=無効（定時実行しない）
    "状態"               VARCHAR(1)   NOT NULL DEFAULT '1',
    -- 最後に実行が終わった日時（画面の一覧表示用。バッチ側が更新する）
    "最終実行日時"       TIMESTAMP    NULL,
    "備考"               VARCHAR(200) NULL,
    -- 複数管理者による同時更新を検出する楽観的ロック値
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    -- 人が切り替えたときの実行者。スケジューラ・移行では NULL
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    -- 人が操作していない登録／更新の識別子（例: SCHEDULER / MIGRATION）
    "登録元コード"       VARCHAR(20)  NULL,
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "BAT_バッチコントロール情報_pkey"
        PRIMARY KEY ("バッチコード"),
    CONSTRAINT "FK_BAT_コントロール_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_BAT_コントロール_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_BAT_コントロール_状態"
        CHECK ("状態" IN ('0', '1')),
    CONSTRAINT "CK_BAT_コントロール_コード"
        CHECK (BTRIM("バッチコード") <> ''),
    CONSTRAINT "CK_BAT_コントロール_バージョン"
        CHECK ("バージョン" > 0)
);

-- スケジューラが「有効なバッチ」だけを引くための索引（部分索引で小さく保つ）。
CREATE INDEX IF NOT EXISTS idx_bat_control_enabled
    ON public."BAT_バッチコントロール情報" ("バッチコード")
    WHERE "状態" = '1';

COMMENT ON TABLE public."BAT_バッチコントロール情報" IS
    'バッチの有効／無効（定時実行の可否）。2.0 の COM_設定情報 BATCH_TASK_ENABLED_* を置き換える';
COMMENT ON COLUMN public."BAT_バッチコントロール情報"."バッチコード" IS
    'バッチ定義のコード（batL01 / batR03 など）。定義はコード側（BatchTaskRegistry）が持つ';
COMMENT ON COLUMN public."BAT_バッチコントロール情報"."状態" IS
    '1=有効（定時・循環で実行する） / 0=無効（定時実行しない。手動実行は可）';
COMMENT ON COLUMN public."BAT_バッチコントロール情報"."最終実行日時" IS
    '最後に実行が終わった日時（バッチ実行の完了時に更新する）';
COMMENT ON COLUMN public."BAT_バッチコントロール情報"."バージョン" IS '楽観的ロック用。更新成功時に1加算';
