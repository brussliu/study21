-- ============================================================================
-- Study 2.1  Web閲覧履歴 DDL（最終仕様）
-- テーブル: NET_ブラウザ接続情報
-- ----------------------------------------------------------------------------
-- ブラウザ拡張（`extension/`）と 2.1 をつなぐ「接続コード」を持つ。1 アカウント = 1 行。
--
-- 2.0 には認証が無く、拡張が送ってくる JSON の userId（'ljz' など）をそのまま信じて
-- 記録していた。2.1 では本人しか知らない接続コードを拡張に設定させ、その接続コードで
-- アカウントを特定する。接続コードは「インターネット利用履歴 → Web閲覧履歴」画面で
-- 発行・再発行できる（再発行すると古いコードは即時に無効になる）。
--
--   接続コード → NET_ブラウザ接続情報 → 利用者アカウントID
--                 NET_ブラウザ端末情報（どの端末から送られたか）
--                 NET_Web閲覧履歴情報（実際の閲覧イベント）
--
-- 接続コードは推測できない値（SecureRandom 32 バイト = 64 文字の 16 進）で、
-- 画面では 4 文字ずつ区切って表示する。DB にはそのまま保存する（照合に使うため）。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."NET_ブラウザ接続情報" (
    "接続ID"             BIGSERIAL    NOT NULL,
    "利用者アカウントID" BIGINT       NOT NULL,
    -- 拡張の設定画面に入力してもらう接続コード（64 文字の 16 進）
    "接続トークン"       VARCHAR(64)  NOT NULL,
    -- ACTIVE=有効 / REVOKED=無効（再発行の実装で使う想定。当面は ACTIVE のみ）
    "状態コード"         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    -- 拡張から最後に受け付けた日時（register / heartbeat / events のいずれか）
    "最終使用日時"       TIMESTAMP    NULL,
    -- 楽観的ロック（再発行の競合検出）
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    -- APP=画面からの発行 / MIGRATION=2.0 からの移行（現状は APP のみ）
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "NET_ブラウザ接続情報_pkey" PRIMARY KEY ("接続ID"),
    CONSTRAINT "FK_NET_ブラウザ接続_利用者"
        FOREIGN KEY ("利用者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_NET_ブラウザ接続_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_NET_ブラウザ接続_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_NET_ブラウザ接続_状態"
        CHECK ("状態コード" IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT "CK_NET_ブラウザ接続_トークン"
        CHECK (BTRIM("接続トークン") <> ''),
    CONSTRAINT "CK_NET_ブラウザ接続_バージョン"
        CHECK ("バージョン" >= 1)
);

-- 1 アカウント 1 本。
CREATE UNIQUE INDEX IF NOT EXISTS uq_net_browser_conn_account
    ON public."NET_ブラウザ接続情報" ("利用者アカウントID");

-- 接続コードからの照合（拡張のすべてのリクエストがこれを使う）。
CREATE UNIQUE INDEX IF NOT EXISTS uq_net_browser_conn_token
    ON public."NET_ブラウザ接続情報" ("接続トークン");

COMMENT ON TABLE public."NET_ブラウザ接続情報" IS
    'ブラウザ拡張と 2.1 をつなぐ接続コード（1 アカウント = 1 行）。2.0 の認証なし方式を置き換える';
COMMENT ON COLUMN public."NET_ブラウザ接続情報"."接続トークン" IS
    '拡張の設定画面に入力する接続コード。SecureRandom 32 バイトの 16 進 64 文字';
COMMENT ON COLUMN public."NET_ブラウザ接続情報"."状態コード" IS 'ACTIVE=有効 / REVOKED=無効';
COMMENT ON COLUMN public."NET_ブラウザ接続情報"."最終使用日時" IS
    '拡張から最後に受け付けた日時（register / heartbeat / events のいずれか）';
COMMENT ON COLUMN public."NET_ブラウザ接続情報"."登録元コード" IS 'APP=画面からの発行 / MIGRATION=2.0 からの移行';
