-- ============================================================================
-- Study 2.1  プロキシ通信履歴 DDL（最終仕様）
-- テーブル: NET_プロキシ通信履歴情報
-- ----------------------------------------------------------------------------
-- 2.0 の "NET_プロキシ通信履歴情報"（164,394 件・稼働中）を 2.1 の規約で再設計する。
-- プロキシサービス（batS01 が起動する。admin-api 内で動く LittleProxy）が
-- 1 リクエスト = 1 行で記録する。許可した要求と拒否した要求の両方を残す
-- （拒否は 応答状態コード 403 と理由が入る）。
--
-- 2.0 からの主な変更:
--   1. 監査を 2.1 の規約に合わせた。2.0 は 登録ID / 更新ID に固定値 'proxy' を
--      入れていた（誰が操作したかではなく「プロキシが記録した」以上の意味が無い）。
--      2.1 は 登録者/更新者アカウントID（ACC_アカウント への FK。プロキシは人が
--      操作しないため常に NULL）＋ 登録元コード='PROXY' で表す。
--   2. 追記専用のテーブルなので バージョン（楽観的ロック）は持たない。
--   3. 値の範囲を CHECK で固定した（応答状態コードは 100〜599、ポートは 0〜65535、
--      クライアントIP は空文字不可）。2.0 は制約が無く、異常値が入り得た。
--   4. 索引を 2.1 の参照パターン（時系列の一覧、ホスト別、端末別）に合わせた。
--      2.0 の idx_net_proxy_log_01〜03 と同じ考え方だが命名を 2.1 の規約に揃えた。
--
-- 注意（2.0 の実データ）:
--   * 2.0 の 通信履歴ID は 620 万番台まで進んでいる（削除やシーケンスの進み方の
--     影響）。移行では旧 ID を維持し、移行後にシーケンスを最大値へ合わせる。
--   * 1 行あたり数 KB（ユーザーエージェント・参照元URL）になることがあるため、
--     件数が増え続ける。将来 月単位のパーティション化を検討する（docs/NET_CONTROL.md）。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."NET_プロキシ通信履歴情報" (
    "通信履歴ID"         BIGSERIAL    NOT NULL,
    -- プロキシが要求を受け付けた時刻（アプリ側の時計）
    "受付日時"           TIMESTAMP    NOT NULL,
    -- 要求側が記録した時刻（プロキシヘッダ・リクエストヘッダから取れる場合のみ）
    "要求日時"           TIMESTAMP    NULL,
    -- 端末の IP アドレス（端末コントロール情報と突き合わせる値）
    "クライアントIP"     VARCHAR(64)  NOT NULL,
    "クライアントポート" INTEGER      NULL,
    "HTTPメソッド"       VARCHAR(16)  NULL,
    -- 接続先（許可判定に使ったホスト名。小文字化・www 除去はしていない生の値）
    "接続先ホスト"       VARCHAR(255) NULL,
    "接続先ポート"       INTEGER      NULL,
    "要求URL"            TEXT         NULL,
    "要求パス"           TEXT         NULL,
    "クエリ文字列"       TEXT         NULL,
    -- HTTP / HTTPS（CONNECT）など
    "プロトコル種別"     VARCHAR(20)  NULL,
    "ユーザーエージェント" TEXT       NULL,
    "参照元URL"          TEXT         NULL,
    -- NULL=許可した要求 / 403=拒否した要求（2.0 と同じ使い方）
    "応答状態コード"     INTEGER      NULL,
    -- 拒否した理由（例: 停止モードのためアクセス不可）
    "エラー内容"         TEXT         NULL,
    -- プロキシは人が操作しないため常に NULL（将来、代理で許可した管理者を入れる余地）
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    -- 記録元の識別子（PROXY / MIGRATION など）
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'PROXY',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "NET_プロキシ通信履歴情報_pkey"
        PRIMARY KEY ("通信履歴ID"),
    CONSTRAINT "FK_NET_プロキシ履歴_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_NET_プロキシ履歴_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_NET_プロキシ履歴_クライアントIP"
        CHECK (BTRIM("クライアントIP") <> ''),
    CONSTRAINT "CK_NET_プロキシ履歴_クライアントポート"
        CHECK ("クライアントポート" IS NULL OR ("クライアントポート" BETWEEN 0 AND 65535)),
    CONSTRAINT "CK_NET_プロキシ履歴_接続先ポート"
        CHECK ("接続先ポート" IS NULL OR ("接続先ポート" BETWEEN 0 AND 65535)),
    CONSTRAINT "CK_NET_プロキシ履歴_応答状態コード"
        CHECK ("応答状態コード" IS NULL OR ("応答状態コード" BETWEEN 100 AND 599)),
    -- 拒否（403）のときは理由が入っていること
    CONSTRAINT "CK_NET_プロキシ履歴_拒否理由"
        CHECK ("応答状態コード" IS DISTINCT FROM 403 OR "エラー内容" IS NOT NULL)
);

-- 時系列の一覧（履歴画面は新しい順に出す）。
CREATE INDEX IF NOT EXISTS idx_net_proxy_history_received
    ON public."NET_プロキシ通信履歴情報" ("受付日時" DESC, "通信履歴ID" DESC);

-- 接続先ホスト別の集計・調査。
CREATE INDEX IF NOT EXISTS idx_net_proxy_history_host
    ON public."NET_プロキシ通信履歴情報" ("接続先ホスト", "受付日時" DESC);

-- 端末（クライアントIP）別の一覧。
CREATE INDEX IF NOT EXISTS idx_net_proxy_history_client
    ON public."NET_プロキシ通信履歴情報" ("クライアントIP", "受付日時" DESC);

COMMENT ON TABLE public."NET_プロキシ通信履歴情報" IS
    'プロキシサービスの通信履歴（1 行 = 1 リクエスト）。許可・拒否の両方を記録する';
COMMENT ON COLUMN public."NET_プロキシ通信履歴情報"."受付日時" IS 'プロキシが要求を受け付けた時刻';
COMMENT ON COLUMN public."NET_プロキシ通信履歴情報"."要求日時" IS '要求側が記録した時刻（取得できた場合のみ）';
COMMENT ON COLUMN public."NET_プロキシ通信履歴情報"."クライアントIP" IS '端末の IP アドレス（NET_端末コントロール情報 と突き合わせる）';
COMMENT ON COLUMN public."NET_プロキシ通信履歴情報"."接続先ホスト" IS '許可判定に使った接続先ホスト名';
COMMENT ON COLUMN public."NET_プロキシ通信履歴情報"."応答状態コード" IS
    'NULL=許可した要求 / 403=拒否した要求（理由は エラー内容）';
COMMENT ON COLUMN public."NET_プロキシ通信履歴情報"."エラー内容" IS '拒否した理由（例: 停止モードのためアクセス不可）';
COMMENT ON COLUMN public."NET_プロキシ通信履歴情報"."登録者アカウントID" IS
    'プロキシは人が操作しないため常に NULL。記録元は 登録元コード（PROXY / MIGRATION）で表す';
