-- ============================================================================
-- Study 2.1  サイト情報 DDL（最終仕様）
-- テーブル: NET_サイト情報
-- ----------------------------------------------------------------------------
-- 2.0 の "NET_サイト情報"（175 件）を 2.1 の規約で再設計する。
-- 端末コントロール（NET_端末コントロール情報）と対で使い、
-- 「端末モードごとにどのサイトを許可するか」を定義する。
--
-- 2.0 からの主な変更:
--   1. 区分・判定方法・分類・承認ステータスを日本語の自由文字列から
--      コード（CHECK 制約付き）へ変更した。2.0 の「0.勉強」「末尾一致」「承認済」
--      などは画面の表示文言であり、2.1 ではフロント側でローカライズする。
--   2. 分類は 2.0 では 6 種類の値が使われていた（学習/娯楽/SNS/その他/英会話/ニュース）。
--      UI の選択肢に無い値もあったため、分類コード='OTHER' + 分類名称（自由入力）で
--      表現できるようにした（移行時に情報を落とさない）。
--   3. 監査を強化した。2.0 の 登録ID / 更新ID は 'site.jsp' / 'history.jsp' /
--      'batR03' のような画面名・バッチ名で、誰が操作したか分からなかった。
--      2.1 は 登録者/更新者アカウントID（ACC_アカウント への FK）を持ち、
--      人が操作していない更新は 登録元コード / 更新元コード に残す。
--      （移行・バッチ・エージェントによる更新があるためアカウントID は NULL 可）
--   4. 有効／無効を 2.1 共通の 状態（'1'=有効 / '0'=無効）に統一した。
--   5. 承認を「誰がいつ」まで保持する（承認者アカウントID / 承認日時）。
--   6. 楽観的ロック用の バージョン を追加した。
--   7. 判定用に正規化した ホスト名 を保持する（重複確認・索引用）。
--      2.0 はサイト名称もホスト判定の候補にしていたが、2.1 では
--      サイトURL と ホスト名 だけを判定に使う（表示名で許可される事故を防ぐ）。
--
-- 判定の仕様（モードと区分の関係、URL の一致方法）は
-- database/サイト管理/NET_サイト管理・端末コントロール設計.md を参照。
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."NET_サイト情報" (
    "サイトID"           BIGSERIAL    NOT NULL,
    -- 画面に出す名称（判定には使わない）
    "サイト名称"         VARCHAR(200) NOT NULL,
    -- 許可判定に使う URL（ホスト名だけでも可。パス付きも可）
    "サイトURL"          TEXT         NOT NULL,
    -- 判定・重複確認用にアプリ側で正規化した値
    -- （小文字化、スキーム/ポート/パス/クエリ除去、先頭の www. 除去）
    "ホスト名"           VARCHAR(255) NOT NULL,
    -- 端末モード別の許可区分（STUDY / NORMAL / BREAK / GAME）
    "区分コード"         VARCHAR(20)  NOT NULL,
    -- URL の一致方法（PREFIX / SUFFIX / CONTAINS / EXACT。2.0 の既定は末尾一致）
    "判定方法コード"     VARCHAR(20)  NOT NULL DEFAULT 'SUFFIX',
    -- 分類（LEARNING / ENTERTAINMENT / SHOPPING / SNS / OTHER）
    "分類コード"         VARCHAR(20)  NOT NULL,
    -- 分類コード = 'OTHER' のときに使う自由入力の分類名（例: 英会話 / ニュース）
    "分類名称"           VARCHAR(50)  NULL,
    -- 'PENDING'=未承認（新規・編集後） / 'APPROVED'=承認済 / 'REJECTED'=却下
    -- プロキシが許可判定に使うのは APPROVED かつ 状態='1' の行だけ
    "承認ステータス"     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    "承認者アカウントID" BIGINT       NULL,
    "承認日時"           TIMESTAMP    NULL,
    -- '1'=有効 / '0'=無効（2.1 共通のステータス規約）
    "状態"               VARCHAR(1)   NOT NULL DEFAULT '1',
    "備考"               VARCHAR(200) NULL,
    -- 複数管理者による同時更新を検出する楽観的ロック値
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    -- 人が操作したときの実行者。移行・バッチ・エージェントによる登録/更新では NULL
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    -- 人が操作していない登録/更新の識別子（例: MIGRATION / BAT_R03 / AGENT）
    "登録元コード"       VARCHAR(20)  NULL,
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "NET_サイト情報_pkey"
        PRIMARY KEY ("サイトID"),
    CONSTRAINT "FK_NET_サイト_承認者"
        FOREIGN KEY ("承認者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_NET_サイト_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_NET_サイト_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_NET_サイト_区分"
        CHECK ("区分コード" IN ('STUDY', 'NORMAL', 'BREAK', 'GAME')),
    CONSTRAINT "CK_NET_サイト_判定方法"
        CHECK ("判定方法コード" IN ('PREFIX', 'SUFFIX', 'CONTAINS', 'EXACT')),
    CONSTRAINT "CK_NET_サイト_分類"
        CHECK ("分類コード" IN ('LEARNING', 'ENTERTAINMENT', 'SHOPPING', 'SNS', 'OTHER')),
    CONSTRAINT "CK_NET_サイト_承認ステータス"
        CHECK ("承認ステータス" IN ('PENDING', 'APPROVED', 'REJECTED')),
    -- 承認済みにするときは承認日時を必ず残す（承認者が特定できない移行データも許容する）
    CONSTRAINT "CK_NET_サイト_承認整合"
        CHECK ("承認ステータス" <> 'APPROVED' OR "承認日時" IS NOT NULL),
    CONSTRAINT "CK_NET_サイト_状態"
        CHECK ("状態" IN ('0', '1')),
    CONSTRAINT "CK_NET_サイト_名称"
        CHECK (BTRIM("サイト名称") <> ''),
    CONSTRAINT "CK_NET_サイト_URL"
        CHECK (BTRIM("サイトURL") <> ''),
    CONSTRAINT "CK_NET_サイト_ホスト名"
        CHECK (BTRIM("ホスト名") <> ''),
    CONSTRAINT "CK_NET_サイト_分類名称"
        CHECK ("分類名称" IS NULL OR BTRIM("分類名称") <> ''),
    CONSTRAINT "CK_NET_サイト_バージョン"
        CHECK ("バージョン" > 0)
);

-- プロキシの許可判定: 「承認済み かつ 有効」だけを読む（部分索引で小さく保つ）。
CREATE INDEX IF NOT EXISTS idx_net_site_approved_active
    ON public."NET_サイト情報" ("区分コード", "ホスト名")
    WHERE "承認ステータス" = 'APPROVED' AND "状態" = '1';

-- 同一ホストの重複確認・ホスト単位の絞り込み。
-- 2.0 の実データは 175 行中 149 ホスト（25 ホストが重複）で、重複登録は運用上あり得る。
-- 許可判定は「一致した行のどれかが区分に合えば許可」なので一意制約にはしない。
CREATE INDEX IF NOT EXISTS idx_net_site_host
    ON public."NET_サイト情報" ("ホスト名", "サイトID" DESC);

-- 一覧画面: 状態・承認ステータスで絞り、更新日時の降順。
CREATE INDEX IF NOT EXISTS idx_net_site_list
    ON public."NET_サイト情報" ("状態", "承認ステータス", "更新日時" DESC, "サイトID" DESC);

-- 区分・分類での絞り込み（画面のフィルタ）。
CREATE INDEX IF NOT EXISTS idx_net_site_kind
    ON public."NET_サイト情報" ("区分コード", "更新日時" DESC, "サイトID" DESC);
CREATE INDEX IF NOT EXISTS idx_net_site_category
    ON public."NET_サイト情報" ("分類コード", "更新日時" DESC, "サイトID" DESC);

COMMENT ON TABLE public."NET_サイト情報" IS
    '端末モード別に許可するサイト（URL の一致方法つき）。承認済みかつ有効な行だけが許可判定に使われる';
COMMENT ON COLUMN public."NET_サイト情報"."サイト名称" IS '画面表示用の名称。判定には使わない';
COMMENT ON COLUMN public."NET_サイト情報"."サイトURL" IS '許可判定に使う URL（2.0 の実データはホスト名のみ。パス付きも可）';
COMMENT ON COLUMN public."NET_サイト情報"."ホスト名" IS 'アプリ側で正規化したホスト名（小文字・www.除去・ポート/パス除去）';
COMMENT ON COLUMN public."NET_サイト情報"."区分コード" IS
    'STUDY=勉強 / NORMAL=通常 / BREAK=休憩 / GAME=ゲーム。端末モードごとに許可される区分が決まる';
COMMENT ON COLUMN public."NET_サイト情報"."判定方法コード" IS
    'PREFIX=先頭一致 / SUFFIX=末尾一致 / CONTAINS=含める / EXACT=完全一致（2.0 の既定は SUFFIX）';
COMMENT ON COLUMN public."NET_サイト情報"."分類コード" IS
    'LEARNING=学習 / ENTERTAINMENT=娯楽 / SHOPPING=ショッピング / SNS / OTHER=その他';
COMMENT ON COLUMN public."NET_サイト情報"."分類名称" IS
    '分類コード=OTHER のときの自由入力名（2.0 の「英会話」「ニュース」などを保持する）';
COMMENT ON COLUMN public."NET_サイト情報"."承認ステータス" IS
    'PENDING=未承認 / APPROVED=承認済 / REJECTED=却下。編集すると PENDING に戻す運用（2.0 と同じ）';
COMMENT ON COLUMN public."NET_サイト情報"."承認者アカウントID" IS '承認した管理者。移行データなど特定できない場合は NULL';
COMMENT ON COLUMN public."NET_サイト情報"."状態" IS '1=有効 / 0=無効';
COMMENT ON COLUMN public."NET_サイト情報"."登録元コード" IS '人が操作していない登録の識別子（MIGRATION など）';
COMMENT ON COLUMN public."NET_サイト情報"."更新元コード" IS '人が操作していない更新の識別子（BAT_R03 / AGENT など）';
COMMENT ON COLUMN public."NET_サイト情報"."バージョン" IS '楽観的ロック用。更新成功時に1加算';
