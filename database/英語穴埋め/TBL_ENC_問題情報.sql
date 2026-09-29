-- ============================================================================
-- Study 2.1  英語穴埋め問題 / 問題情報  DDL
-- テーブル: ENC_問題情報 （★2.1 新設。2.0 の STY_英語穴埋め問題 に相当）
-- ----------------------------------------------------------------------------
-- 1 行 = 1 設問（空所 1 つ）。親は ENC_問題セット情報、選択肢は ENC_問題選択肢情報。
--
-- 2.0 からの主な変更:
--   1. 選択肢は JSON ではなく子表（ENC_問題選択肢情報）にする。
--      2.0 の `選択肢JSON` / `選択肢翻訳_日本語` / `選択肢翻訳_中国語`（JSONB の配列）は移行で分解する。
--      **2.0 の `正解 TEXT`（正解の文字列）は持たない。** 正解は「この `正解選択肢番号` ＋
--      選択肢子表の `選択肢本文`」で 1 か所だけ表す。
--   2. 2.0 の `AI解説` は 2.1 では持たない。出す解説は `AI解説_日本語` / `AI解説_中国語` の 2 本。
--   3. 設問は**論理削除**（状態コード = 'X'）。過去の挑戦履歴の参照を壊さないため、
--      消しても行は残す。
--   4. OCR の実行そのものは `OCR構造化JSON` に残し、AI 呼び出しの本文は
--      BAT_AI呼出履歴情報（プロンプト／レスポンス）が持つ。`AI呼出履歴ID` は解説（batC14）の生成元。
--   5. 共通列は 6 列（JPN_単語問題情報 と同じ）。`バージョン` は親（ENC_問題セット情報）だけが持つ。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順: TBL_ENC_問題画像情報.sql の後 → 本ファイル → TBL_ENC_問題選択肢情報.sql
--         → TBL_ENC_挑戦履歴情報.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."ENC_問題情報" (
    "問題ID"                BIGSERIAL    NOT NULL,
    "問題セットID"          BIGINT       NOT NULL,
    -- どの紙面から取った設問か（分からなければ NULL。紙面を消すと NULL になる）
    "問題画像ID"            BIGINT       NULL,
    -- 教材の中の設問番号（1 から。セットの中で一意）
    "問題番号"              INTEGER      NOT NULL,
    -- 紙面の中での並び（OCR が返した順。任意）
    "画像内順序"            INTEGER      NULL,
    -- 設問の本文（空所は (　　) など。OCR の結果を利用者が直したもの）
    "問題文"                TEXT         NOT NULL,
    -- 設問の和訳（日本語）
    "問題翻訳_日本語"       TEXT         NULL,
    -- 設問の和訳（中国語）
    "問題翻訳_中国語"       TEXT         NULL,
    -- 正解の選択肢を補った完成問題文
    "完成問題文"            TEXT         NULL,
    -- 紙面に書かれた学生の答案（選択肢の記号）
    "学生答案"              TEXT         NULL,
    -- 赤で直された答案（先生の赤字。正解の根拠になる）
    "赤字訂正答案"          TEXT         NULL,
    -- 正解の選択肢番号（ENC_問題選択肢情報.選択肢番号 と同じ値）
    "正解選択肢番号"        INTEGER      NULL,
    -- RED_CORRECTION = 赤字の訂正から / UNCORRECTED_STUDENT_ANSWER = 直されていない学生答案から /
    -- UNKNOWN = 分からない
    "正解決定根拠"          VARCHAR(40)  NULL,
    -- CORRECT / INCORRECT / UNANSWERED / UNKNOWN（2.0 と同じ語）
    "回答判定"              VARCHAR(16)  NULL,
    -- OCR の信頼度（0〜1。2.0 と同じ NUMERIC(6,5)）
    "OCR信頼度"             NUMERIC(6,5) NULL,
    -- この設問の知識ポイント（batC14 が作る）
    "知識ポイント"          TEXT         NULL,
    -- AI 解説（日本語／中国語）
    "AI解説_日本語"         TEXT         NULL,
    "AI解説_中国語"         TEXT         NULL,
    -- 誤答の分析（日本語／中国語）
    "学生誤答分析_日本語"   TEXT         NULL,
    "学生誤答分析_中国語"   TEXT         NULL,
    -- batC14 が返した解説の構造化応答をそのまま持つ
    "AI解説構造化JSON"      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    -- batC13 が返した設問の構造化応答をそのまま持つ（2.0 の 022 で追加）
    "OCR構造化JSON"         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    -- WAITING = 処理待ち / RUNNING = 処理中 / COMPLETED = 生成済 / ERROR = エラー
    "AI解説状態"            VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    -- 解説を作った AI（2.0 と同じ列名）
    "解説AIプロバイダ"      VARCHAR(40)  NULL,
    "解説AIモデル"          VARCHAR(120) NULL,
    "解説生成日時"          TIMESTAMP    NULL,
    -- 解説（batC14）の AI 呼出履歴。OCR（batC13）は画像 1 枚ごとに呼ぶので、こちらは
    -- BAT_AI呼出履歴情報 を バッチコード＋処理キー で引く（FK は張らない＝追記専用のログ）
    "AI呼出履歴ID"          BIGINT       NULL,
    -- A = 有効 / X = 削除（論理削除。挑戦履歴の参照を壊さない）
    "状態コード"            VARCHAR(20)  NOT NULL DEFAULT 'A',

    -- ---- 2.1 の共通規約（子は 6 列。バージョン は持たない） ----
    -- `バージョン` は楽観ロックの実装と対で意味を持つので、**親（問題セット）だけ**が持つ。
    -- 設問は子なので、2.1 の子の型（JPN_単語問題情報 と同じ 6 列）に合わせる。
    "登録者アカウントID"    BIGINT       NULL,
    "更新者アカウントID"    BIGINT       NULL,
    "登録元コード"          VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"          VARCHAR(20)  NULL,
    "登録日時"              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ENC_問題情報_pkey" PRIMARY KEY ("問題ID"),
    CONSTRAINT "UK_ENC_問題_番号" UNIQUE ("問題セットID", "問題番号"),
    CONSTRAINT "CK_ENC_問題_番号" CHECK ("問題番号" >= 1),
    CONSTRAINT "CK_ENC_問題_画像内順序" CHECK ("画像内順序" IS NULL OR "画像内順序" >= 1),
    CONSTRAINT "CK_ENC_問題_正解選択肢番号" CHECK ("正解選択肢番号" IS NULL OR "正解選択肢番号" >= 1),
    CONSTRAINT "CK_ENC_問題_OCR信頼度" CHECK ("OCR信頼度" IS NULL OR "OCR信頼度" BETWEEN 0 AND 1),
    CONSTRAINT "CK_ENC_問題_正解決定根拠" CHECK ("正解決定根拠" IS NULL OR "正解決定根拠" IN
        ('RED_CORRECTION', 'UNCORRECTED_STUDENT_ANSWER', 'UNKNOWN')),
    CONSTRAINT "CK_ENC_問題_回答判定" CHECK ("回答判定" IS NULL OR "回答判定" IN
        ('CORRECT', 'INCORRECT', 'UNANSWERED', 'UNKNOWN')),
    CONSTRAINT "CK_ENC_問題_AI解説状態" CHECK ("AI解説状態" IN
        ('WAITING', 'RUNNING', 'COMPLETED', 'ERROR')),
    CONSTRAINT "CK_ENC_問題_状態" CHECK ("状態コード" IN ('A', 'X')),
    CONSTRAINT "CK_ENC_問題_AI解説構造化JSON" CHECK (jsonb_typeof("AI解説構造化JSON") = 'object'),
    CONSTRAINT "CK_ENC_問題_OCR構造化JSON" CHECK (jsonb_typeof("OCR構造化JSON") = 'object'),
    CONSTRAINT "FK_ENC_問題_セット" FOREIGN KEY ("問題セットID")
        REFERENCES public."ENC_問題セット情報" ("問題セットID") ON DELETE CASCADE,
    -- 紙面を消しても設問は残す（どの紙面だったかが分からなくなるだけ）
    CONSTRAINT "FK_ENC_問題_画像" FOREIGN KEY ("問題画像ID")
        REFERENCES public."ENC_問題画像情報" ("問題画像ID") ON DELETE SET NULL
);

-- 紙面から設問を引く
CREATE INDEX IF NOT EXISTS idx_enc_question_image
    ON public."ENC_問題情報" ("問題画像ID")
    WHERE "問題画像ID" IS NOT NULL;

-- 一覧の絞り込み（判定別）
CREATE INDEX IF NOT EXISTS idx_enc_question_answer_status
    ON public."ENC_問題情報" ("回答判定");

-- 一覧の絞り込み（正解をどう決めたか別）
CREATE INDEX IF NOT EXISTS idx_enc_question_answer_source
    ON public."ENC_問題情報" ("正解決定根拠");

-- 教材の有効な設問を番号順に引く
CREATE INDEX IF NOT EXISTS idx_enc_question_set_no
    ON public."ENC_問題情報" ("問題セットID", "問題番号")
    WHERE "状態コード" = 'A';

COMMENT ON TABLE public."ENC_問題情報" IS
    '英語穴埋めの設問（1 行 = 1 設問）。選択肢は ENC_問題選択肢情報、挑戦は ENC_挑戦履歴情報';
COMMENT ON COLUMN public."ENC_問題情報"."問題画像ID" IS
    'この設問を取った紙面。紙面を消すと NULL になる（設問は残す）';
COMMENT ON COLUMN public."ENC_問題情報"."正解選択肢番号" IS
    '正解の選択肢番号（ENC_問題選択肢情報.選択肢番号）。**2.0 の 正解 TEXT を置き換えたもの**。正解の本文は選択肢子表に 1 か所だけ置く';
COMMENT ON COLUMN public."ENC_問題情報"."正解決定根拠" IS
    'RED_CORRECTION = 赤字の訂正から / UNCORRECTED_STUDENT_ANSWER = 直されていない学生答案から / UNKNOWN = 分からない';
COMMENT ON COLUMN public."ENC_問題情報"."回答判定" IS
    'CORRECT / INCORRECT / UNANSWERED / UNKNOWN（2.0 と同じ語）';
COMMENT ON COLUMN public."ENC_問題情報"."完成問題文" IS
    '正解選択肢を補った完成問題文（2.0 の 023 で追加した列と同じ意味）';
COMMENT ON COLUMN public."ENC_問題情報"."学生誤答分析_日本語" IS
    '学生答案が不正解の場合の日本語分析（2.0 の 023 で追加した列と同じ意味）';
COMMENT ON COLUMN public."ENC_問題情報"."AI解説構造化JSON" IS
    '問題別 AI 解説の完全な構造化応答（2.0 の 023 と同じ）';
COMMENT ON COLUMN public."ENC_問題情報"."OCR構造化JSON" IS
    'OCR（batC13）が返した設問の構造化応答（2.0 の 022 と同じ）';
COMMENT ON COLUMN public."ENC_問題情報"."AI解説状態" IS
    'WAITING / RUNNING / COMPLETED / ERROR。batC14（問題別 AI 解説）の進み具合';
COMMENT ON COLUMN public."ENC_問題情報"."AI呼出履歴ID" IS
    '解説（batC14）の AI 呼出履歴。OCR（batC13）は画像 1 枚ごとに呼ぶので、こちらは BAT_AI呼出履歴情報 を バッチコード＋処理キー で引く（FK は張らない＝追記専用のログへの参照）';
COMMENT ON COLUMN public."ENC_問題情報"."状態コード" IS
    'A = 有効 / X = 削除（論理削除）。設問を消しても挑戦履歴の参照は壊さない';
COMMENT ON COLUMN public."ENC_問題情報"."登録元コード" IS
    'APP=画面からの登録 / MIGRATION=2.0 からの移行';
