-- ============================================================================
-- Study 2.1  英語穴埋め問題 / 挑戦履歴情報  DDL
-- テーブル: ENC_挑戦履歴情報 （★2.1 新設。2.0 の STY_英語穴埋め挑戦履歴 に相当）
-- ----------------------------------------------------------------------------
-- 1 行 = 1 回の解答（再挑戦も 1 行ずつ積む）。親は ENC_問題情報。
--
-- 設問の本文・選択肢・正解・解説は**そのときの写し**を持つ。あとから設問や選択肢を直しても、
-- 過去の履歴の意味（何を聞かれて何を選んで、なぜ間違えたか）は変わらない。
--
-- 2.0 からの主な変更:
--   1. `ユーザーID VARCHAR(64)` は 利用者アカウントID（ACC_アカウント への FK）にする。
--      教材は利用者ごとなので親から辿れるが、2.0 と同じく列として持つ（一覧と検索に使う）。
--   2. 回数を**明示の列**にする（2.0 は COUNT で何回目かを数えていた）。
--      `UNIQUE (問題ID, 回数)` で同じ回を二重に積めない。
--   3. スナップショットの列（問題文・選択肢JSON・正解選択肢番号・解説）は 2.1 で足した。
--   4. 共通列は 登録者アカウントID ＋ 登録日時 の 2 列（ENG_AI添削履歴情報 と同じ）。
--      履歴は**追記のみ**なので、版管理（バージョン）と更新の監査は持たない。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順: TBL_ACC_アカウント.sql → TBL_ENC_問題セット情報.sql → TBL_ENC_問題画像情報.sql
--         → TBL_ENC_問題情報.sql → 本ファイル（利用者アカウントID も ACC を参照する）
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."ENC_挑戦履歴情報" (
    "挑戦履歴ID"         BIGSERIAL    NOT NULL,
    "問題ID"             BIGINT       NOT NULL,
    -- 挑戦した利用者（生徒。保護者が代理で解く場合も保護者のアカウント）
    "利用者アカウントID" BIGINT       NOT NULL,
    -- 何回目の解答か（1 から。設問ごとに 1, 2, 3…）
    "回数"               INTEGER      NOT NULL,
    -- 選んだ答案（選択肢の記号、または選択肢の本文）
    "選択答案"           TEXT         NOT NULL,
    -- CORRECT / INCORRECT（2.0 と同じ語）
    "判定"               VARCHAR(16)  NOT NULL,

    -- ---- そのときの写し（履歴の再現に要る） ----
    "問題文"             TEXT         NOT NULL,
    -- そのときの選択肢（2.0 の 選択肢JSON と同じ形の配列）
    "選択肢JSON"         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    -- そのときの正解の選択肢番号（分からなければ NULL）
    "正解選択肢番号"     INTEGER      NULL,
    -- そのときの解説（ENC_問題情報.AI解説_日本語 / AI解説_中国語 の写し）
    "解説_日本語"        TEXT         NULL,
    "解説_中国語"        TEXT         NULL,

    "回答日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- ---- 2.1 の共通規約（履歴は 登録者 と 登録日時 の 2 列だけ） ----
    -- 履歴は**追記のみ**（書き換えない）ので、版管理と更新の監査は持たない。
    -- 2.0 からの移行分は 挑戦履歴ID の範囲と 回答日時 で見分ける。
    "登録者アカウントID" BIGINT       NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ENC_挑戦履歴情報_pkey" PRIMARY KEY ("挑戦履歴ID"),
    -- 同じ設問の同じ回は 1 行だけ
    CONSTRAINT "UK_ENC_挑戦履歴_回" UNIQUE ("問題ID", "回数"),
    CONSTRAINT "CK_ENC_挑戦履歴_回" CHECK ("回数" >= 1),
    CONSTRAINT "CK_ENC_挑戦履歴_判定" CHECK ("判定" IN ('CORRECT', 'INCORRECT')),
    CONSTRAINT "CK_ENC_挑戦履歴_正解選択肢番号" CHECK ("正解選択肢番号" IS NULL OR "正解選択肢番号" >= 1),
    CONSTRAINT "CK_ENC_挑戦履歴_選択肢JSON" CHECK (jsonb_typeof("選択肢JSON") = 'array'),
    CONSTRAINT "FK_ENC_挑戦履歴_問題" FOREIGN KEY ("問題ID")
        REFERENCES public."ENC_問題情報" ("問題ID") ON DELETE CASCADE,
    CONSTRAINT "FK_ENC_挑戦履歴_利用者" FOREIGN KEY ("利用者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE CASCADE
);

-- 利用者の履歴を新しい順に引く（正解・不正解の回数を出す）
CREATE INDEX IF NOT EXISTS idx_enc_challenge_owner_date
    ON public."ENC_挑戦履歴情報" ("利用者アカウントID", "回答日時" DESC);

-- 設問ごとの履歴は UK_ENC_挑戦履歴_回（問題ID, 回数）で引けるので、別の索引は張らない。

COMMENT ON TABLE public."ENC_挑戦履歴情報" IS
    '解答の履歴（1 行 = 1 回の解答。再挑戦も積む）。設問の写しを持つので、あとで設問を直しても履歴の意味は変わらない';
COMMENT ON COLUMN public."ENC_挑戦履歴情報"."回数" IS
    '1 から。設問ごとの 何回目か（2.0 は COUNT で数えていたのを明示の列にした）。UK_ENC_挑戦履歴_回 で一意';
COMMENT ON COLUMN public."ENC_挑戦履歴情報"."利用者アカウントID" IS
    '挑戦した利用者。2.0 の ユーザーID（''liu'' などの文字列）を ACC_アカウント への FK に置き換えたもの';
COMMENT ON COLUMN public."ENC_挑戦履歴情報"."選択答案" IS
    '選んだ答案（選択肢の記号、または本文）。判定はこれと 正解選択肢番号 を突き合わせて決める';
COMMENT ON COLUMN public."ENC_挑戦履歴情報"."判定" IS
    'CORRECT = 正解 / INCORRECT = 不正解（2.0 と同じ語）';
COMMENT ON COLUMN public."ENC_挑戦履歴情報"."問題文" IS
    '解いたときの問題文（写し）。あとで設問を直しても、出した履歴の意味は変わらない';
COMMENT ON COLUMN public."ENC_挑戦履歴情報"."選択肢JSON" IS
    '解いたときの選択肢（写し。2.0 の 選択肢JSON と同じ形の配列）';
COMMENT ON COLUMN public."ENC_挑戦履歴情報"."解説_日本語" IS
    '解いたときの AI 解説（ENC_問題情報.AI解説_日本語 の写し）';
