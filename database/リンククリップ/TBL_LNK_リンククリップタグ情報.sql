-- ============================================================================
-- Study 2.1  リンククリップタグ情報 DDL
-- テーブル: LNK_リンククリップタグ情報
-- ----------------------------------------------------------------------------
-- タグはリンククリップ集約内の子情報。親削除時に自動削除する。
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."LNK_リンククリップタグ情報" (
    "リンククリップID"       BIGINT       NOT NULL,
    "タグ名称"               VARCHAR(100) NOT NULL,
    "表示順"                 INTEGER      NOT NULL DEFAULT 0,
    "登録者アカウントID"     BIGINT       NOT NULL,
    "更新者アカウントID"     BIGINT       NOT NULL,
    "登録日時"               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "LNK_リンククリップタグ情報_pkey"
        PRIMARY KEY ("リンククリップID", "タグ名称"),
    CONSTRAINT "FK_LNK_リンククリップタグ_クリップ"
        FOREIGN KEY ("リンククリップID")
        REFERENCES public."LNK_リンククリップ情報" ("リンククリップID") ON DELETE CASCADE,
    CONSTRAINT "FK_LNK_リンククリップタグ_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_LNK_リンククリップタグ_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_LNK_リンククリップタグ_名称"
        CHECK (BTRIM("タグ名称") <> ''),
    CONSTRAINT "CK_LNK_リンククリップタグ_表示順"
        CHECK ("表示順" >= 0)
);

-- 大文字小文字だけが異なる同一タグを1クリップへ重複登録させない。
CREATE UNIQUE INDEX IF NOT EXISTS uq_lnk_clip_tag_case_insensitive
    ON public."LNK_リンククリップタグ情報" ("リンククリップID", LOWER("タグ名称"));

CREATE INDEX IF NOT EXISTS idx_lnk_clip_tag_name
    ON public."LNK_リンククリップタグ情報" (LOWER("タグ名称"), "リンククリップID");

CREATE INDEX IF NOT EXISTS idx_lnk_clip_tag_order
    ON public."LNK_リンククリップタグ情報" ("リンククリップID", "表示順", "タグ名称");

COMMENT ON TABLE public."LNK_リンククリップタグ情報" IS 'リンククリップに付与する検索タグ';
COMMENT ON COLUMN public."LNK_リンククリップタグ情報"."タグ名称" IS '1クリップ内では大文字小文字を区別せず一意';
