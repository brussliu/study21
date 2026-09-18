-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_単語問題選択肢情報（問題の正解・誤答の選択肢）
-- ----------------------------------------------------------------------------
-- 2.0 の `STY_日本語単語問題選択肢情報`（実データ 6,800 件。study3 DB。移行日 2026-09-13 実測）を
-- 2.1 の規約で再設計する。親は `JPN_単語問題情報`（1,700 件）。
--
-- 2.0 の実データで分かっていること（移行の前提）:
--   * 1 問題あたり 4 択（6,800 = 1,700 × 4）。`正解フラグ` が true の行は
--     ちょうど 1,700 件（= 1 問 1 正解）。
--   * `誤答区分` は正解の行では空文字（''）で、誤答の行だけ
--     KANJI_SIMILAR 1,592 / READING_SIMILAR 1,143 / OTHER 1,031 / HOMOPHONE 476 /
--     CONTEXT_MISMATCH 463 / MEANING_SIMILAR 395 が入っている。
--     **空文字は NULL に寄せる**（2.1 の「未設定は NULL」）。
--   * `選択肢値` の最大長は 31 文字。`選択肢読み` は 5,268 件、
--     `説明_中国語` は 3,742 件に入っている。`説明_日本語` は **全件 NULL**。
--   * `品質状態` は **全件 'APPROVED'**（＝採用済みの問題の選択肢しか残っていない）。
--     2.1 は問題側の 状態コード で採用・却下を表すので、選択肢には状態を持たない。
--   * 選択肢は問題の一部なので、親を消したら一緒に消える（ON DELETE CASCADE）。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_単語問題選択肢情報" (
    "選択肢ID"           BIGSERIAL    NOT NULL,
    -- 2.0 の 選択肢ID。冪等な移行（ON CONFLICT）と突き合わせの根拠として残す
    "旧選択肢ID"         BIGINT       NULL,
    "問題ID"             BIGINT       NOT NULL,
    -- 1 から。問題の中で一意（uq_jpn_choice_order）
    "表示順"             INTEGER      NOT NULL,
    "選択肢値"           VARCHAR(300) NOT NULL,
    "選択肢読み"         VARCHAR(300) NULL,
    "正解フラグ"         BOOLEAN      NOT NULL DEFAULT FALSE,
    -- KANJI_SIMILAR / READING_SIMILAR / HOMOPHONE / MEANING_SIMILAR /
    -- CONTEXT_MISMATCH / OTHER。正解の行は NULL
    "誤答区分"           VARCHAR(30)  NULL,
    "説明_日本語"        TEXT         NULL,
    "説明_中国語"        TEXT         NULL,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_単語問題選択肢情報_pkey" PRIMARY KEY ("選択肢ID"),
    CONSTRAINT "FK_JPN_単語選択肢_問題"
        FOREIGN KEY ("問題ID")
        REFERENCES public."JPN_単語問題情報" ("問題ID") ON DELETE CASCADE,
    CONSTRAINT "FK_JPN_単語選択肢_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_単語選択肢_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_単語選択肢_表示順"
        CHECK ("表示順" >= 1),
    CONSTRAINT "CK_JPN_単語選択肢_選択肢値"
        CHECK (NULLIF(BTRIM("選択肢値"), '') IS NOT NULL),
    CONSTRAINT "CK_JPN_単語選択肢_誤答区分"
        CHECK (
            "誤答区分" IS NULL
            OR "誤答区分" IN (
                'READING_SIMILAR', 'SOUND_SIMILAR', 'KANJI_SIMILAR',
                'HOMOPHONE', 'MEANING_SIMILAR', 'CONTEXT_MISMATCH', 'OTHER'
            )
        )
);

-- 問題の中での表示順は一意
CREATE UNIQUE INDEX IF NOT EXISTS uq_jpn_choice_order
    ON public."JPN_単語問題選択肢情報" ("問題ID", "表示順");

-- 2.0 の 選択肢ID。移行の再実行を冪等にするための一意索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_jpn_choice_old_id
    ON public."JPN_単語問題選択肢情報" ("旧選択肢ID");

COMMENT ON TABLE public."JPN_単語問題選択肢情報" IS
    '問題の正解・誤答の選択肢と誤答分類。2.0 の STY_日本語単語問題選択肢情報（6,800 件）';
COMMENT ON COLUMN public."JPN_単語問題選択肢情報"."旧選択肢ID" IS
    '2.0 の 選択肢ID。移行の冪等性と突き合わせに使う';
COMMENT ON COLUMN public."JPN_単語問題選択肢情報"."誤答区分" IS
    '誤答の理由区分。正解の行は NULL（2.0 の空文字は移行時に NULL へ）';
COMMENT ON COLUMN public."JPN_単語問題選択肢情報"."正解フラグ" IS
    '1 問題につき true は 1 行（2.0 の実データで確認済み）';
COMMENT ON COLUMN public."JPN_単語問題選択肢情報"."登録元コード" IS
    'APP=画面からの登録 / MIGRATION=2.0 からの移行';
