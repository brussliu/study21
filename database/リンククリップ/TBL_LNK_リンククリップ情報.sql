-- ============================================================================
-- Study 2.1  リンククリップ情報 DDL
-- テーブル: LNK_リンククリップ情報
-- ----------------------------------------------------------------------------
-- 2.0 の COM_リンククリップ情報を、2.1 のアカウントモデルに合わせて再設計する。
-- リンククリップは家族共有ではなくアカウント個人所有とする。
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."LNK_リンククリップ情報" (
    "リンククリップID"       BIGSERIAL    NOT NULL,
    "所有者アカウントID"     BIGINT       NOT NULL,
    -- 固定の画面分類。表示文言はフロント側でローカライズする。
    "保存先コード"           VARCHAR(20)  NOT NULL,
    "ソースコード"           VARCHAR(20)  NOT NULL,
    "クリップ種別"           VARCHAR(20)  NOT NULL,
    "サイト名称"             VARCHAR(200) NULL,
    "ページタイトル"         VARCHAR(300) NOT NULL,
    "URL"                    TEXT         NOT NULL,
    -- トラッキングパラメータ等を除いた重複検索用URL。アプリ側で生成する。
    "URL正規化"              VARCHAR(2048) NOT NULL,
    "概要"                   TEXT         NULL,
    "AI概要"                 TEXT         NULL,
    "メモ"                   TEXT         NULL,
    "公開者名称"             VARCHAR(200) NULL,
    "公開日時"               TIMESTAMP    NULL,
    "動画時間秒"             INTEGER      NULL,
    "縮略画像URL"            TEXT         NULL,
    "お気に入り"             BOOLEAN      NOT NULL DEFAULT FALSE,
    "既読"                   BOOLEAN      NOT NULL DEFAULT FALSE,
    "アーカイブ"             BOOLEAN      NOT NULL DEFAULT FALSE,
    "閲覧回数"               INTEGER      NOT NULL DEFAULT 0,
    "最終閲覧日時"           TIMESTAMP    NULL,
    "メタ情報取得日時"       TIMESTAMP    NULL,
    "AI要約生成日時"         TIMESTAMP    NULL,
    -- 複数端末からの同時更新を検出する楽観的ロック値。
    "バージョン"             INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID"     BIGINT       NOT NULL,
    "更新者アカウントID"     BIGINT       NOT NULL,
    "登録日時"               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "LNK_リンククリップ情報_pkey"
        PRIMARY KEY ("リンククリップID"),
    CONSTRAINT "FK_LNK_リンククリップ_所有者"
        FOREIGN KEY ("所有者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_LNK_リンククリップ_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_LNK_リンククリップ_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_LNK_リンククリップ_保存先"
        CHECK ("保存先コード" IN ('INBOX', 'READ_LATER', 'LEARNING', 'REFERENCE', 'DONE')),
    CONSTRAINT "CK_LNK_リンククリップ_ソース"
        CHECK ("ソースコード" IN ('WEB', 'YOUTUBE', 'GITHUB', 'WIKIPEDIA', 'NEWS', 'LOCAL_FILE', 'OTHER')),
    CONSTRAINT "CK_LNK_リンククリップ_種別"
        CHECK ("クリップ種別" IN ('LINK', 'VIDEO', 'ARTICLE', 'REPOSITORY', 'NEWS', 'FILE')),
    CONSTRAINT "CK_LNK_リンククリップ_タイトル"
        CHECK (BTRIM("ページタイトル") <> ''),
    CONSTRAINT "CK_LNK_リンククリップ_URL"
        CHECK (BTRIM("URL") <> ''),
    CONSTRAINT "CK_LNK_リンククリップ_URL正規化"
        CHECK (BTRIM("URL正規化") <> ''),
    CONSTRAINT "CK_LNK_リンククリップ_動画時間"
        CHECK ("動画時間秒" IS NULL OR "動画時間秒" >= 0),
    CONSTRAINT "CK_LNK_リンククリップ_閲覧回数"
        CHECK ("閲覧回数" >= 0),
    CONSTRAINT "CK_LNK_リンククリップ_バージョン"
        CHECK ("バージョン" > 0)
);

-- 通常一覧: 個人 + 保存先 + アーカイブ状態 + 更新日時降順。
CREATE INDEX IF NOT EXISTS idx_lnk_clip_owner_folder_updated
    ON public."LNK_リンククリップ情報"
       ("所有者アカウントID", "保存先コード", "アーカイブ", "更新日時" DESC, "リンククリップID" DESC);

-- ソース絞り込み一覧。
CREATE INDEX IF NOT EXISTS idx_lnk_clip_owner_source_updated
    ON public."LNK_リンククリップ情報"
       ("所有者アカウントID", "ソースコード", "アーカイブ", "更新日時" DESC, "リンククリップID" DESC);

-- URL重複確認。2.0の挙動を維持し、DBの一意制約にはしない。
CREATE INDEX IF NOT EXISTS idx_lnk_clip_owner_normalized_url
    ON public."LNK_リンククリップ情報" ("所有者アカウントID", "URL正規化");

-- お気に入り一覧だけを小さい部分索引で処理する。
CREATE INDEX IF NOT EXISTS idx_lnk_clip_owner_favorite
    ON public."LNK_リンククリップ情報" ("所有者アカウントID", "更新日時" DESC)
    WHERE "お気に入り" = TRUE AND "アーカイブ" = FALSE;

COMMENT ON TABLE public."LNK_リンククリップ情報" IS 'アカウント個人所有のURL・ローカルファイルリンク保存情報';
COMMENT ON COLUMN public."LNK_リンククリップ情報"."所有者アカウントID" IS '閲覧・更新可能な単一アカウント。親子間では共有しない';
COMMENT ON COLUMN public."LNK_リンククリップ情報"."保存先コード" IS 'INBOX / READ_LATER / LEARNING / REFERENCE / DONE';
COMMENT ON COLUMN public."LNK_リンククリップ情報"."ソースコード" IS 'WEB / YOUTUBE / GITHUB / WIKIPEDIA / NEWS / LOCAL_FILE / OTHER';
COMMENT ON COLUMN public."LNK_リンククリップ情報"."URL正規化" IS '重複候補検索用にアプリ側で正規化したURL';
COMMENT ON COLUMN public."LNK_リンククリップ情報"."バージョン" IS '楽観的ロック用。更新成功時に1加算';

