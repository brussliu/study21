-- ============================================================================
-- Study 2.1  英語穴埋め問題 / 問題選択肢情報  DDL
-- テーブル: ENC_問題選択肢情報 （★2.1 新設。**2.0 には表が無い**）
-- ----------------------------------------------------------------------------
-- 1 行 = 1 選択肢。親は ENC_問題情報。
-- 2.0 は `選択肢JSON` / `選択肢翻訳_日本語` / `選択肢翻訳_中国語`（JSONB の配列）に
-- まとめて持っていたが、2.1 では 1 選択肢 = 1 行にする
-- （日本語勉強の JPN_単語問題選択肢情報 と同じ形）。
--
--   選択肢JSON[i]            → 選択肢番号（1 から）＋ 選択肢本文
--   選択肢翻訳_日本語[i]     → 選択肢訳_日本語
--   選択肢翻訳_中国語[i]     → 選択肢訳_中国語
--
-- 2.0 の `正解 TEXT`（正解の文字列）は、この表には持たない。正解は
-- **ENC_問題情報.正解選択肢番号**（＋この表の `選択肢本文`）で 1 か所だけ表す
-- （同じ事実を 2 か所に置くと、片方だけ直して食い違う）。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順: TBL_ENC_問題情報.sql の後
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."ENC_問題選択肢情報" (
    "選択肢ID"           BIGSERIAL    NOT NULL,
    "問題ID"             BIGINT       NOT NULL,
    -- 1 から。2.0 の選択肢は 1 問 4 択だったが、上限は設けない（CHECK は 1〜6）
    "選択肢番号"         INTEGER      NOT NULL,
    -- 選択肢の本文（2.0 の 選択肢JSON の各要素）
    "選択肢本文"         TEXT         NOT NULL,
    -- 選択肢の和訳
    "選択肢訳_日本語"    TEXT         NULL,
    -- 選択肢の和訳（中国語）
    "選択肢訳_中国語"    TEXT         NULL,
    -- 補足（日本語／中国語）。2.0 には無い列
    "補足_日本語"        TEXT         NULL,
    "補足_中国語"        TEXT         NULL,

    -- ---- 2.1 の共通規約（子表は 登録者 と 登録日時 の 2 列だけ） ----
    "登録者アカウントID" BIGINT       NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ENC_問題選択肢情報_pkey" PRIMARY KEY ("選択肢ID"),
    CONSTRAINT "CK_ENC_選択肢_番号" CHECK ("選択肢番号" BETWEEN 1 AND 6),
    CONSTRAINT "CK_ENC_選択肢_本文" CHECK (NULLIF(BTRIM("選択肢本文"), '') IS NOT NULL),
    -- 同じ設問の中で選択肢番号は重ならない
    CONSTRAINT "UK_ENC_選択肢_番号" UNIQUE ("問題ID", "選択肢番号"),
    CONSTRAINT "FK_ENC_選択肢_問題" FOREIGN KEY ("問題ID")
        REFERENCES public."ENC_問題情報" ("問題ID") ON DELETE CASCADE
);

COMMENT ON TABLE public."ENC_問題選択肢情報" IS
    '設問の選択肢（1 行 = 1 選択肢）。2.0 の 選択肢JSON（JSONB の配列）を 1 行ずつに分解したもの';
COMMENT ON COLUMN public."ENC_問題選択肢情報"."選択肢番号" IS
    '1 から。ENC_問題情報.正解選択肢番号 と ENC_挑戦履歴情報.正解選択肢番号 はこの値を指す';
COMMENT ON COLUMN public."ENC_問題選択肢情報"."選択肢本文" IS
    '選択肢の本文。2.0 の 正解 TEXT はこの本文（または選択肢の記号）と突き合わせて 正解選択肢番号 を決める';
COMMENT ON COLUMN public."ENC_問題選択肢情報"."補足_日本語" IS
    '選択肢の補足（2.0 には無い列）。解説を出すときの言い換えなど';
