-- ============================================================================
-- Study 2.1  授業録音 / AI 授業記録 DDL
-- テーブル: CR_前置詞プリセット情報
-- ----------------------------------------------------------------------------
-- 授業の「前置詞」（シナリオプリセット）。授業の目的に合わせて AI ノートの整理方針を変える。
-- キー・バリュー設定（COM_設定情報）には載せない（複数件・ユーザー管理のため専用テーブル）。
-- GLOBAL 既定は init SQL で seed（汎用 3 件）。ユーザーが家庭/学生スコープで追加編集できる。
-- 設計: tmp/classroom-ai-design.md §3.4
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: 何度流しても同じ（CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS）
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."CR_前置詞プリセット情報" (
    "前置詞ID"          BIGSERIAL    NOT NULL,
    -- GLOBAL=全ユーザー共通 / FAMILY=家族 / STUDENT=学生個人
    "スコープ"          VARCHAR(20)  NOT NULL DEFAULT 'GLOBAL',
    -- NULL = GLOBAL（全員）。FAMILY/STUDENT は登録者アカウントID を入れる
    "登録者アカウントID" BIGINT       NULL,
    "プリセット名"      VARCHAR(200) NOT NULL,
    "前置詞テキスト"    TEXT         NULL,
    "表示順"            INTEGER      NOT NULL DEFAULT 0,
    "登録ID"            VARCHAR(64)  NULL,
    "更新ID"            VARCHAR(64)  NULL,
    "登録日時"          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "CR_前置詞プリセット情報_pkey" PRIMARY KEY ("前置詞ID"),
    CONSTRAINT "FK_CR_前置詞_登録者" FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE CASCADE,
    CONSTRAINT "CK_CR_前置詞_スコープ" CHECK ("スコープ" IN ('GLOBAL','FAMILY','STUDENT')),
    CONSTRAINT "CK_CR_前置詞_名前" CHECK (BTRIM("プリセット名") <> ''),
    CONSTRAINT "CK_CR_前置詞_表示順" CHECK ("表示順" >= 0),
    CONSTRAINT "CK_CR_前置詞_所有者" CHECK (
        ("スコープ" = 'GLOBAL' AND "登録者アカウントID" IS NULL)
        OR ("スコープ" <> 'GLOBAL' AND "登録者アカウントID" IS NOT NULL)
    )
);

-- 一覧は「GLOBAL → 自分のスコープ」を表示順で出す
CREATE INDEX IF NOT EXISTS idx_cr_preset_scope_order
    ON public."CR_前置詞プリセット情報" ("スコープ", "表示順", "前置詞ID");

-- ユーザー（家族/学生）ごとのプリセット
CREATE INDEX IF NOT EXISTS idx_cr_preset_owner
    ON public."CR_前置詞プリセット情報" ("登録者アカウントID");

COMMENT ON TABLE public."CR_前置詞プリセット情報" IS
    '授業の「前置詞」（シナリオプリセット）。GLOBAL は init SQL で seed（汎用 3 件）。';
COMMENT ON COLUMN public."CR_前置詞プリセット情報"."スコープ" IS
    'GLOBAL=全ユーザー共通 / FAMILY=家族（登録者=保護者） / STUDENT=学生個人';
