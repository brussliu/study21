-- ============================================================================
-- Study 2.1  Web閲覧履歴 DDL（最終仕様）
-- テーブル: NET_Web閲覧履歴情報
-- ----------------------------------------------------------------------------
-- 2.0 の "TRN_ブラウザ閲覧履歴情報"（30,948 件・稼働中）を 2.1 の規約で再設計する。
-- ブラウザ拡張が端末から送ってくる閲覧イベントを 1 イベント = 1 行で記録したもの。
-- 2.0 の履歴管理画面（history.jsp）の「ブラウザ閲覧履歴」タブが参照していた。
-- 2.1 では インターネット利用履歴（`/{area}/internet-usage`）の **Web閲覧履歴** タブが参照する。
--
-- 2.0 からの主な変更:
--   1. 名前を 2.1 の画面名（Web閲覧履歴）に合わせた。NET_ はネットワーク制御の配下を表す
--      （同じ画面の「サイトアクセス履歴」= NET_プロキシ通信履歴情報 と対になる）。
--   2. 持ち主を ユーザーID（'ljz' など）から 利用者アカウントID（ACC_アカウント への FK）へ。
--      2.0 の ID も 旧ユーザーID として残す（日報・TODO・プロキシ履歴と同じ扱い）。
--   3. 監査を 2.1 の規約に合わせた。2.0 の 登録ID / 更新ID は 'browser' などの固定値で
--      実行者を特定できないため、アカウントID は NULL とし 登録元/更新元コード に残す。
--   4. 追記専用のテーブルなので バージョン（楽観的ロック）は持たない。
--   5. フラグ・数値の範囲を CHECK で固定した（2.0 は制約が無く、異常値が入り得た）。
--   6. 索引を 2.1 の参照パターン（時系列の一覧、端末別、ドメイン別、利用者別）に揃えた。
--
-- 2.0 の実データ（2026-06-27〜・稼働中）:
--   * 端末ID はブラウザ拡張が採番した識別子（'chrome-xxxxx' など）で、
--     NET_端末コントロール情報 の IP アドレスとは別物。端末名称は行内に持っている。
--   * イベント種別は HISTORY_VISITED / NAV_HISTORY_UPDATED / TAB_UPDATED /
--     TAB_ACTIVATED / NAV_COMMITTED の 5 種類。
--   * 1 行の TEXT 列（URL・ページタイトル・JSON詳細）は大きくなり得る（最大 3KB 程度）。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."NET_Web閲覧履歴情報" (
    "閲覧履歴ID"         BIGSERIAL    NOT NULL,
    -- 持ち主（2.0 の ユーザーID から引く。引けない場合は NULL）
    "利用者アカウントID" BIGINT       NULL,
    -- 2.0 の ユーザーID（'ljz' など）
    "旧ユーザーID"       VARCHAR(100) NULL,
    -- ブラウザ拡張が採番した端末の識別子（IP アドレスではない）
    "端末識別子"         VARCHAR(200) NULL,
    "端末名称"           VARCHAR(200) NULL,
    "ブラウザ種別"       VARCHAR(50)  NULL,
    "ブラウザプロファイルID" VARCHAR(200) NULL,
    "タブID"             BIGINT       NULL,
    "ウィンドウID"       BIGINT       NULL,
    "セッションID"       VARCHAR(200) NULL,
    -- HISTORY_VISITED / NAV_HISTORY_UPDATED / TAB_UPDATED / TAB_ACTIVATED / NAV_COMMITTED
    "イベント種別"       VARCHAR(50)  NOT NULL,
    "URL"                TEXT         NULL,
    "ドメイン"           VARCHAR(500) NULL,
    "ページタイトル"     TEXT         NULL,
    "リファラーURL"      TEXT         NULL,
    "ファビコンURL"      TEXT         NULL,
    -- link / reload / typed / auto_bookmark など（ブラウザが付ける遷移理由）
    "ページ遷移種別"     VARCHAR(100) NULL,
    -- '1'=そのタブがアクティブだった / '0'=非アクティブ
    "アクティブフラグ"   CHAR(1)      NOT NULL DEFAULT '0',
    -- '1'=ブラウザ履歴の同期で入った行 / '0'=リアルタイムのイベント
    "履歴同期元フラグ"   CHAR(1)      NOT NULL DEFAULT '0',
    "アクセス日時"       TIMESTAMP    NOT NULL,
    "滞在秒数"           INTEGER      NULL,
    "閲覧回数"           INTEGER      NULL,
    -- 拡張がイベントごとに付ける一意な ID（UUID）。送信の再試行で二重登録しないための鍵。
    -- 2.0 からの移行データは NULL（重複しないので UNIQUE 索引と共存できる）
    "イベント識別子"     VARCHAR(100) NULL,
    -- 拡張が送ってきた元の JSON（調査用。通常の一覧では使わない）
    "JSON詳細"           TEXT         NULL,
    -- ブラウザ拡張は人が操作しないため通常 NULL（記録元は 登録元コード で表す）
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'BROWSER',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "NET_Web閲覧履歴情報_pkey" PRIMARY KEY ("閲覧履歴ID"),
    CONSTRAINT "FK_NET_Web閲覧履歴_利用者"
        FOREIGN KEY ("利用者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_NET_Web閲覧履歴_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_NET_Web閲覧履歴_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_NET_Web閲覧履歴_イベント種別"
        CHECK (BTRIM("イベント種別") <> ''),
    CONSTRAINT "CK_NET_Web閲覧履歴_アクティブフラグ"
        CHECK ("アクティブフラグ" IN ('0', '1')),
    CONSTRAINT "CK_NET_Web閲覧履歴_履歴同期元フラグ"
        CHECK ("履歴同期元フラグ" IN ('0', '1')),
    CONSTRAINT "CK_NET_Web閲覧履歴_滞在秒数"
        CHECK ("滞在秒数" IS NULL OR "滞在秒数" >= 0),
    CONSTRAINT "CK_NET_Web閲覧履歴_閲覧回数"
        CHECK ("閲覧回数" IS NULL OR "閲覧回数" >= 0)
);

-- 追補（2026-09-13）: 拡張からの受信 API を作るにあたって イベント識別子 を追加した。
-- すでにテーブルを作ってある環境でも、このファイルをもう一度実行すれば当たる
-- （CREATE TABLE IF NOT EXISTS は素通りするので、下の ALTER が実体）。
ALTER TABLE public."NET_Web閲覧履歴情報"
    ADD COLUMN IF NOT EXISTS "イベント識別子" VARCHAR(100) NULL;

-- 時系列の一覧（履歴画面は新しい順に出す）。
CREATE INDEX IF NOT EXISTS idx_net_web_history_accessed
    ON public."NET_Web閲覧履歴情報" ("アクセス日時" DESC, "閲覧履歴ID" DESC);

-- 端末別の一覧（2.0 の「端末ID / 端末名称」の絞り込み）。
CREATE INDEX IF NOT EXISTS idx_net_web_history_terminal
    ON public."NET_Web閲覧履歴情報" ("端末識別子", "アクセス日時" DESC);

-- ドメイン別の集計・調査。
CREATE INDEX IF NOT EXISTS idx_net_web_history_domain
    ON public."NET_Web閲覧履歴情報" ("ドメイン", "アクセス日時" DESC);

-- 利用者（アカウント）別の一覧。
CREATE INDEX IF NOT EXISTS idx_net_web_history_user
    ON public."NET_Web閲覧履歴情報" ("利用者アカウントID", "アクセス日時" DESC);

-- イベント種別の絞り込み。
CREATE INDEX IF NOT EXISTS idx_net_web_history_event
    ON public."NET_Web閲覧履歴情報" ("イベント種別");

-- 拡張の再送による二重登録を防ぐ（端末 + イベント識別子）。
-- イベント識別子 が NULL の行（2.0 からの移行データ）は UNIQUE 違反にならない。
CREATE UNIQUE INDEX IF NOT EXISTS uq_net_web_history_event_id
    ON public."NET_Web閲覧履歴情報" ("端末識別子", "イベント識別子");

COMMENT ON TABLE public."NET_Web閲覧履歴情報" IS
    'ブラウザ拡張が送ってくる Web 閲覧イベントの履歴（1 行 = 1 イベント）。2.0 の TRN_ブラウザ閲覧履歴情報';
COMMENT ON COLUMN public."NET_Web閲覧履歴情報"."利用者アカウントID" IS
    '持ち主。2.0 の ユーザーID から引く（引けない行は NULL で 旧ユーザーID だけ残る）';
COMMENT ON COLUMN public."NET_Web閲覧履歴情報"."旧ユーザーID" IS '2.0 の ユーザーID（移行データのみ）';
COMMENT ON COLUMN public."NET_Web閲覧履歴情報"."端末識別子" IS
    'ブラウザ拡張が採番した端末の識別子（NET_端末コントロール情報 の IP アドレスとは別物）';
COMMENT ON COLUMN public."NET_Web閲覧履歴情報"."イベント種別" IS
    'HISTORY_VISITED / NAV_HISTORY_UPDATED / TAB_UPDATED / TAB_ACTIVATED / NAV_COMMITTED';
COMMENT ON COLUMN public."NET_Web閲覧履歴情報"."アクティブフラグ" IS '1=アクティブなタブだった / 0=非アクティブ';
COMMENT ON COLUMN public."NET_Web閲覧履歴情報"."JSON詳細" IS '拡張が送ってきた元の JSON（調査用）';
COMMENT ON COLUMN public."NET_Web閲覧履歴情報"."登録元コード" IS
    'BROWSER=拡張からの記録 / MIGRATION=2.0 からの移行 / APP=アプリからの登録';
COMMENT ON COLUMN public."NET_Web閲覧履歴情報"."イベント識別子" IS
    '拡張がイベントごとに付ける UUID。送信の再試行で二重登録しないための鍵（移行データは NULL）';

-- ============================================================================
-- 追補（2026-09-13）: 拡張からの受信 API を作るにあたって イベント識別子 を追加した。
-- 追加の ALTER は上の CREATE TABLE の直後に置いてある（既存環境でもこのファイルを
-- もう一度実行すれば当たる）。ここは経緯の記録。
-- ============================================================================

