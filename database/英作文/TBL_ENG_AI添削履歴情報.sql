-- ============================================================================
-- Study 2.1  英作文AI添削 / AI添削履歴情報  DDL
-- テーブル: ENG_AI添削履歴情報 （★2.1 新設。**2.0 には無い**）
-- ----------------------------------------------------------------------------
-- **何度も添削できる**ようにするため、添削の結果を 1 行ずつ積む（2.0 は 1 列を上書きしていた）。
-- 画面は「回数」で切り替えて、その回のレポートを丸ごと見せる。
--
-- 級・題・設問・本文は**そのときの写し**を持つ（あとで作文や級を直しても、出したレポートは動かない）。
-- 実行は非同期（2.0 と同じ）: 受付で QUEUED を積み、働き手が RUNNING → SUCCEEDED / FAILED へ進める。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順: TBL_ENG_英作文情報.sql の後
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."ENG_AI添削履歴情報" (
    "添削ID"              BIGSERIAL    NOT NULL,
    "英作文ID"            BIGINT       NOT NULL,
    -- 何回目の添削か（1 から。作文ごとに 1, 2, 3…）
    "回数"                INTEGER      NOT NULL,
    -- QUEUED = 受付済 / RUNNING = 実行中 / SUCCEEDED = 成功 / FAILED = 失敗 / CANCELED = 中止
    "状態コード"          VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',

    -- ---- そのときの写し（レポートの再現に要る） ----
    "英検級"              VARCHAR(20)  NOT NULL,
    "題_日本語"           VARCHAR(200) NULL,
    "題_中国語"           VARCHAR(200) NULL,
    "設問文"              TEXT         NULL,
    "作文本文"            TEXT         NULL,
    "語数"                INTEGER      NOT NULL DEFAULT 0,

    -- ---- 結果 ----
    "総合得点"            INTEGER      NULL,
    "満点"                INTEGER      NULL,
    -- 2.0 と同形のレポート（level/titleJa/titleZh/score/maxScore/rubric[4]/
    -- modelAnswer/taskRequirements/warnings/japanese{...}/chinese{...}）
    "添削結果JSON"        JSONB        NULL,
    -- どの AI 呼び出しだったか（BAT_AI呼出履歴情報.呼出履歴ID。FK は張らない＝追記専用）
    "AI呼出履歴ID"        BIGINT       NULL,
    -- 失敗したときの理由（人が読む日本語。成功なら NULL）
    "失敗理由"            VARCHAR(500) NULL,
    "開始日時"            TIMESTAMP    NULL,
    "終了日時"            TIMESTAMP    NULL,
    "登録者アカウントID"  BIGINT       NULL,
    "登録日時"            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ENG_AI添削履歴情報_pkey" PRIMARY KEY ("添削ID"),
    CONSTRAINT "UK_ENG_AI添削履歴_回" UNIQUE ("英作文ID", "回数"),
    CONSTRAINT "CK_ENG_AI添削履歴_状態" CHECK ("状態コード" IN
        ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELED')),
    CONSTRAINT "CK_ENG_AI添削履歴_回" CHECK ("回数" >= 1),
    CONSTRAINT "CK_ENG_AI添削履歴_得点" CHECK (
        ("総合得点" IS NULL OR "総合得点" >= 0)
        AND ("満点" IS NULL OR "満点" > 0)
    ),
    CONSTRAINT "FK_ENG_AI添削履歴_作文" FOREIGN KEY ("英作文ID")
        REFERENCES public."ENG_英作文情報" ("英作文ID") ON DELETE CASCADE
);

-- 画面（回数の切り替え・一覧の得点）
CREATE INDEX IF NOT EXISTS idx_eng_essay_grading_essay
    ON public."ENG_AI添削履歴情報" ("英作文ID", "回数" DESC);

-- 働き手の取件（実行待ち・実行中のものだけ。部分索引なので小さい）
CREATE INDEX IF NOT EXISTS idx_eng_essay_grading_active
    ON public."ENG_AI添削履歴情報" ("状態コード", "登録日時")
    WHERE "状態コード" IN ('QUEUED', 'RUNNING');

COMMENT ON TABLE public."ENG_AI添削履歴情報" IS
    'AI 添削の履歴（1 行 = 1 回の添削）。画面は 回数 で切り替えて見る（2.0 は上書きだった）';
COMMENT ON COLUMN public."ENG_AI添削履歴情報"."状態コード" IS
    'QUEUED = 受付済 / RUNNING = 実行中 / SUCCEEDED = 成功 / FAILED = 失敗 / CANCELED = 中止。FAILED は働き手が拾わない（利用者がやり直す）';
COMMENT ON COLUMN public."ENG_AI添削履歴情報"."題_日本語" IS
    'そのときの題（レポートの「作文タイトル」）。あとで作文の題を直しても、出したレポートは変わらない';
COMMENT ON COLUMN public."ENG_AI添削履歴情報"."添削結果JSON" IS
    '2.0 と同形のレポート。日/中の 2 言語、4 観点（内容・構成・語彙・文法）、修正ポイント、改善後の作文例';
COMMENT ON COLUMN public."ENG_AI添削履歴情報"."AI呼出履歴ID" IS
    'BAT_AI呼出履歴情報 の呼出履歴ID。FK は張らない（追記専用のログへの参照）';
