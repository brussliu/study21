-- ============================================================================
-- Study 2.1  端末コントロール情報 DDL（最終仕様）
-- テーブル: NET_端末コントロール情報
-- ----------------------------------------------------------------------------
-- 2.0 の "NET_端末コントロール情報"（5 件）を 2.1 の規約で再設計する。
-- プロキシはクライアント IP でこの表を引き、端末モードに応じて
-- サイトの許可判定（NET_サイト情報）を切り替える。
--
-- 2.0 からの主な変更:
--   1. 「端末ステータス」を 端末モード に改称した。実体は運用モード
--      （通常/休憩/ゲーム/勉強/停止/自由）であり、停止・自由はサイト判定を
--      通さない。画面の表示名は「端末ステータス」のままでよい。
--   2. 状態（'1'=有効 / '0'=無効）を追加した。2.0 は行を消す以外に端末を
--      退役させられず、同じ IP を再利用できなかった。2.1 は 状態='0' にすれば
--      同じ IP を別端末として登録できる（有効な行の間だけで IP 一意）。
--   3. 監査を強化した。2.0 の 登録ID / 更新ID は 'AGENT' / 'batR04' のような
--      実行主体の名前で、アカウントとの紐付けが無かった。2.1 は
--      登録者/更新者アカウントID（ACC_アカウント への FK）を持ち、
--      エージェントやバッチが更新した場合は 登録元コード / 更新元コード に残す。
--      （2.0 の実データにはエージェントが自動登録した端末があるため、
--        アカウントID は NULL 可としている）
--   4. 備考 / 最終接続日時 / バージョン（楽観的ロック）を追加した。
--
-- モードと許可サイト区分の関係は
-- database/サイト管理/NET_サイト管理・端末コントロール設計.md を参照。
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."NET_端末コントロール情報" (
    "端末ID"             BIGSERIAL    NOT NULL,
    -- クライアント IP（IPv4 / IPv6 のどちらも可。プロキシはこの値で端末を特定する）
    "IPアドレス"         VARCHAR(45)  NOT NULL,
    "端末名称"           VARCHAR(100) NOT NULL,
    -- 運用モード: T=通常 / K=休憩 / G=ゲーム / B=勉強 / S=停止 / J=自由
    "端末モード"         VARCHAR(1)   NOT NULL DEFAULT 'T',
    -- '1'=有効 / '0'=無効（無効な端末は許可判定に使わない）
    "状態"               VARCHAR(1)   NOT NULL DEFAULT '1',
    "備考"               VARCHAR(200) NULL,
    -- プロキシが最後にこの端末からリクエストを受けた日時（プロキシ実装時に更新。未実装の間は NULL）
    "最終接続日時"       TIMESTAMP    NULL,
    -- 複数管理者による同時更新を検出する楽観的ロック値
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    -- 人が操作したときの実行者。エージェント/バッチによる登録・更新では NULL
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    -- 人が操作していない登録/更新の識別子（例: AGENT / BAT_R04）
    "登録元コード"       VARCHAR(20)  NULL,
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "NET_端末コントロール情報_pkey"
        PRIMARY KEY ("端末ID"),
    CONSTRAINT "FK_NET_端末_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_NET_端末_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_NET_端末_モード"
        CHECK ("端末モード" IN ('T', 'K', 'G', 'B', 'S', 'J')),
    CONSTRAINT "CK_NET_端末_状態"
        CHECK ("状態" IN ('0', '1')),
    CONSTRAINT "CK_NET_端末_名称"
        CHECK (BTRIM("端末名称") <> ''),
    -- IP の書式はアプリ側で検証する。ここでは明らかな誤り（空白・全角・不要な記号）だけを弾く。
    CONSTRAINT "CK_NET_端末_IP"
        CHECK ("IPアドレス" ~ '^[0-9A-Fa-f:.]{1,45}$'),
    CONSTRAINT "CK_NET_端末_バージョン"
        CHECK ("バージョン" > 0)
);

-- 有効な端末だけ IP 一意（退役させた端末と同じ IP を再利用できる）。
CREATE UNIQUE INDEX IF NOT EXISTS uq_net_terminal_ip_active
    ON public."NET_端末コントロール情報" ("IPアドレス")
    WHERE "状態" = '1';

-- プロキシの端末特定（IP → モード）。
CREATE INDEX IF NOT EXISTS idx_net_terminal_ip
    ON public."NET_端末コントロール情報" ("IPアドレス");

-- 一覧画面: 状態・モードで絞り、更新日時の降順。
CREATE INDEX IF NOT EXISTS idx_net_terminal_list
    ON public."NET_端末コントロール情報" ("状態", "端末モード", "更新日時" DESC, "端末ID" DESC);

COMMENT ON TABLE public."NET_端末コントロール情報" IS
    '監視対象の端末（クライアント IP 単位）。端末モードにより許可されるサイト区分が変わる';
COMMENT ON COLUMN public."NET_端末コントロール情報"."IPアドレス" IS
    'クライアント IP。有効（状態=1）な行の間でのみ一意';
COMMENT ON COLUMN public."NET_端末コントロール情報"."端末モード" IS
    'T=通常 / K=休憩 / G=ゲーム / B=勉強 / S=停止 / J=自由（2.0 の「端末ステータス」）';
COMMENT ON COLUMN public."NET_端末コントロール情報"."状態" IS '1=有効 / 0=無効（無効な端末は許可判定の対象外）';
COMMENT ON COLUMN public."NET_端末コントロール情報"."最終接続日時" IS
    'プロキシが最後に受けたリクエストの日時（プロキシ側の実装時に更新する）';
COMMENT ON COLUMN public."NET_端末コントロール情報"."登録元コード" IS '人が操作していない登録の識別子（AGENT / MIGRATION など）';
COMMENT ON COLUMN public."NET_端末コントロール情報"."更新元コード" IS '人が操作していない更新の識別子（AGENT / BAT_R04 など）';
COMMENT ON COLUMN public."NET_端末コントロール情報"."バージョン" IS '楽観的ロック用。更新成功時に1加算';
