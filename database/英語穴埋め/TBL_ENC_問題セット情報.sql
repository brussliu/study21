-- ============================================================================
-- Study 2.1  英語穴埋め問題 / 問題セット情報  DDL
-- テーブル: ENC_問題セット情報 （★2.1 新設。2.0 の STY_英語穴埋め問題セット に相当）
-- ----------------------------------------------------------------------------
-- 1 行 = 1 セット = 1 教材（問題用紙 1 部）。この下に設問（ENC_問題情報）が並ぶ。
-- 画像は ENC_問題画像情報（実体は DB の外。§設計書）。
--
-- 2.0 の `ユーザーID VARCHAR(64)`（'liu' / 'ljz' のような文字列）は、2.1 では
-- 利用者アカウントID（ACC_アカウント への FK）に置き換える。
-- 2.0 の `状態 CHAR(1)` は 2.1 の語に合わせて 状態コード VARCHAR(20) にする（'A' / 'X' は同じ）。
-- 2.0 の `登録ID` / `更新ID`（人が読む ID 文字列）は 2.1 の共通規約
-- （登録者・更新者アカウントID ＋ 登録元・更新元コード）に置き換える。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順: TBL_ACC_アカウント.sql の後 → 本ファイル → TBL_ENC_問題画像情報.sql
--         → TBL_ENC_問題情報.sql → TBL_ENC_問題選択肢情報.sql → TBL_ENC_挑戦履歴情報.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."ENC_問題セット情報" (
    "問題セットID"       BIGSERIAL    NOT NULL,
    -- 教材を持っている利用者（生徒。保護者が代理で作る場合も保護者のアカウント）
    "利用者アカウントID" BIGINT       NOT NULL,
    -- EXAM = 試験過去問 / PRACTICE = 練習問題 / OTHER = その他
    "出典区分"           VARCHAR(20)  NOT NULL,
    -- 出典の年度（2000〜2100）。分からなければ NULL
    "年度"               INTEGER      NULL,
    -- 試験名・練習帳の名前（一覧と検索に出す）
    "試験練習名"         VARCHAR(200) NOT NULL,
    -- 章・節（任意）
    "章節"               VARCHAR(200) NULL,
    -- 人が書く覚え書き
    "備考"               TEXT         NULL,
    -- 教材全体の概要（OCR の結果を利用者が直したもの）
    "問題概要"           TEXT         NULL,
    -- 教材全体の知識ポイント（同上）
    "知識ポイント"       TEXT         NULL,
    -- WAITING = 処理待ち / RUNNING = 処理中 / COMPLETED = 生成済 / ERROR = エラー
    -- （2.0 の OCR状態 と同じ語）
    "OCR状態"            VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    -- 同上（問題別 AI 解説の生成状態。batC14）
    "AI解説状態"         VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    -- A = 有効 / X = 削除（論理削除。設問と挑戦の履歴は残す）
    "状態コード"         VARCHAR(20)  NOT NULL DEFAULT 'A',

    -- ---- 2.1 の共通規約 ----
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ENC_問題セット情報_pkey" PRIMARY KEY ("問題セットID"),
    CONSTRAINT "CK_ENC_問題セット_出典" CHECK ("出典区分" IN ('EXAM', 'PRACTICE', 'OTHER')),
    CONSTRAINT "CK_ENC_問題セット_年度" CHECK ("年度" IS NULL OR "年度" BETWEEN 2000 AND 2100),
    CONSTRAINT "CK_ENC_問題セット_OCR状態" CHECK ("OCR状態" IN ('WAITING', 'RUNNING', 'COMPLETED', 'ERROR')),
    CONSTRAINT "CK_ENC_問題セット_AI解説状態" CHECK ("AI解説状態" IN ('WAITING', 'RUNNING', 'COMPLETED', 'ERROR')),
    CONSTRAINT "CK_ENC_問題セット_状態" CHECK ("状態コード" IN ('A', 'X')),
    CONSTRAINT "FK_ENC_問題セット_利用者" FOREIGN KEY ("利用者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE CASCADE
);

-- 一覧（自分の教材を新しい順に引く）
CREATE INDEX IF NOT EXISTS idx_enc_set_owner_created
    ON public."ENC_問題セット情報" ("利用者アカウントID", "登録日時" DESC, "問題セットID" DESC)
    WHERE "状態コード" = 'A';

-- 絞り込み（出典区分・年度）
CREATE INDEX IF NOT EXISTS idx_enc_set_owner_source
    ON public."ENC_問題セット情報" ("利用者アカウントID", "出典区分", "年度")
    WHERE "状態コード" = 'A';

COMMENT ON TABLE public."ENC_問題セット情報" IS
    '英語穴埋め問題のセット＝教材（1 行 = 1 セット）。設問は ENC_問題情報、画像は ENC_問題画像情報';
COMMENT ON COLUMN public."ENC_問題セット情報"."利用者アカウントID" IS
    '教材を持っている利用者。2.0 の ユーザーID（''liu'' などの文字列）を ACC_アカウント への FK に置き換えたもの';
COMMENT ON COLUMN public."ENC_問題セット情報"."出典区分" IS
    'EXAM = 試験過去問 / PRACTICE = 練習問題 / OTHER = その他（2.0 の chk_sty_cloze_source と同じ値）';
COMMENT ON COLUMN public."ENC_問題セット情報"."OCR状態" IS
    'WAITING = 処理待ち / RUNNING = 処理中 / COMPLETED = 生成済 / ERROR = エラー。batC13（画像 OCR・問題構造化）の進み具合';
COMMENT ON COLUMN public."ENC_問題セット情報"."AI解説状態" IS
    'WAITING / RUNNING / COMPLETED / ERROR。batC14（問題別 AI 解説）の進み具合';
COMMENT ON COLUMN public."ENC_問題セット情報"."状態コード" IS
    'A = 有効 / X = 削除（論理削除。2.0 の 状態 CHAR(1) と同じ値）。削除しても設問と挑戦の履歴は残す';
COMMENT ON COLUMN public."ENC_問題セット情報"."登録元コード" IS
    'APP=画面からの登録 / MIGRATION=2.0 からの移行';
