-- ============================================================================
-- Study 2.1  Web閲覧履歴 DDL（最終仕様）
-- テーブル: NET_ブラウザ端末情報
-- ----------------------------------------------------------------------------
-- ブラウザ拡張が register してきた端末（ブラウザ 1 つ = 1 行）。1 アカウントが複数端末を
-- 持てる（学校の PC と家の PC など）。心拍（heartbeat）で「いま繋がっているか」が分かる。
--
-- 2.0 の MST_ブラウザ監視端末情報 に当たる。2.0 からの変更点:
--   1. 持ち主を ユーザーID から 利用者アカウントID（ACC_アカウント への FK）へ。
--   2. 端末は 接続コード（NET_ブラウザ接続情報）経由で登録されるため、
--      「どのアカウントの端末か」は接続コードから決まる（リクエストの userId は信じない）。
--   3. 最終心拍日時を持ち、画面は「最終心拍から 10 分以内 = 接続中」と判定する
--      （拡張は 5 分ごとに心拍を送る）。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."NET_ブラウザ端末情報" (
    "端末登録ID"         BIGSERIAL    NOT NULL,
    "利用者アカウントID" BIGINT       NOT NULL,
    -- ブラウザ拡張が採番した端末の識別子（'chrome-1750000000000-ab12cd34' など）
    "端末識別子"         VARCHAR(200) NOT NULL,
    "端末名称"           VARCHAR(200) NULL,
    "OS種別"             VARCHAR(50)  NULL,
    "ブラウザ種別"       VARCHAR(50)  NULL,
    "ブラウザバージョン" VARCHAR(50)  NULL,
    "拡張機能バージョン" VARCHAR(50)  NULL,
    "ブラウザプロファイルID" VARCHAR(200) NULL,
    -- ACTIVE=有効 / REVOKED=無効（管理者が止める想定。オフライン判定は最終心拍日時で行う）
    "状態コード"         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    -- ブラウザの起動時刻（拡張は起動時に register を送る）
    "最終起動日時"       TIMESTAMP    NULL,
    -- 最後に閲覧イベントを受け取った日時
    "最終送信日時"       TIMESTAMP    NULL,
    -- 最後に心拍を受け取った日時（画面の「接続中」判定に使う）
    "最終心拍日時"       TIMESTAMP    NULL,
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    -- BROWSER=拡張からの登録 / MIGRATION=2.0 からの移行（現状は BROWSER のみ）
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'BROWSER',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "NET_ブラウザ端末情報_pkey" PRIMARY KEY ("端末登録ID"),
    CONSTRAINT "FK_NET_ブラウザ端末_利用者"
        FOREIGN KEY ("利用者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_NET_ブラウザ端末_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_NET_ブラウザ端末_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_NET_ブラウザ端末_状態"
        CHECK ("状態コード" IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT "CK_NET_ブラウザ端末_識別子"
        CHECK (BTRIM("端末識別子") <> ''),
    CONSTRAINT "CK_NET_ブラウザ端末_バージョン"
        CHECK ("バージョン" >= 1)
);

-- 同じアカウントの同じ端末は 1 行（register は upsert する）。
CREATE UNIQUE INDEX IF NOT EXISTS uq_net_browser_device_account
    ON public."NET_ブラウザ端末情報" ("利用者アカウントID", "端末識別子");

-- 画面の端末一覧（新しい順）。
CREATE INDEX IF NOT EXISTS idx_net_browser_device_account
    ON public."NET_ブラウザ端末情報" ("利用者アカウントID", "最終心拍日時" DESC);

COMMENT ON TABLE public."NET_ブラウザ端末情報" IS
    'ブラウザ拡張が登録した端末（1 ブラウザ = 1 行）。2.0 の MST_ブラウザ監視端末情報';
COMMENT ON COLUMN public."NET_ブラウザ端末情報"."端末識別子" IS
    'ブラウザ拡張が採番した識別子。NET_Web閲覧履歴情報 の 端末識別子 と同じ値';
COMMENT ON COLUMN public."NET_ブラウザ端末情報"."状態コード" IS 'ACTIVE=有効 / REVOKED=無効';
COMMENT ON COLUMN public."NET_ブラウザ端末情報"."最終心拍日時" IS
    '最後に心拍を受け取った日時。画面は 10 分以内なら「接続中」と表示する';
COMMENT ON COLUMN public."NET_ブラウザ端末情報"."登録元コード" IS 'BROWSER=拡張からの登録 / MIGRATION=2.0 からの移行';
