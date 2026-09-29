-- ============================================================================
-- Study 2.1  英作文AI添削（ENG_*） 既存 DB への適用
-- ----------------------------------------------------------------------------
-- 新規テーブル 3 つを作る（英作文 / 画像 / AI添削履歴）。
-- **何度流しても同じ**（CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS /
-- COMMENT は同じ値で上書き）。
--
-- 実行例:
--   psql -h 192.168.0.100 -p 54320 -U postgres -d study21 -v ON_ERROR_STOP=1 \
--        -f database/移行/MIG_ENG_英作文_20260927.sql
--
-- 中身は database/英作文/TBL_ENG_*.sql と同じ（**変えるときは両方そろえる**）。
-- 実行順: 英作文情報 → 画像情報 → AI添削履歴情報（FK の向き）。
-- ============================================================================
-- ---- database/英作文/TBL_ENG_英作文情報.sql と同じ ----
-- ============================================================================
-- Study 2.1  英作文AI添削 / 英作文情報  DDL
-- テーブル: ENG_英作文情報 （★2.1 新設。2.0 の STY_英作文情報 に相当）
-- ----------------------------------------------------------------------------
-- 1 行 = 1 回の提出（設問と答案の画像を上げ、文字にして、AI に添削させる）。
-- **添削の結果はここに持たない**（何度も添削できるので ENG_AI添削履歴情報 に積む）。
-- 画像は ENG_英作文画像情報（設問画像／答案画像）。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順: TBL_ACC_アカウント.sql の後 → 本ファイル → TBL_ENG_英作文画像情報.sql
--         → TBL_ENG_AI添削履歴情報.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."ENG_英作文情報" (
    "英作文ID"           BIGSERIAL    NOT NULL,
    -- 提出した利用者（生徒。保護者が代理で作る場合も保護者のアカウント）
    "利用者アカウントID" BIGINT       NOT NULL,
    -- 英検級。添削の配点（GRADE1 = 各観点 8 で計 32／PRE1・GRADE2 = 各 4 で計 16）がこれで決まる
    "英検級"             VARCHAR(20)  NOT NULL,
    -- 題（日本語）。AI が付けた主題タイトルを利用者が直せる
    "題"                 VARCHAR(200) NOT NULL,
    -- 題（中国語）。レポートの「標題（中文）」に使う
    "題_中国語"          VARCHAR(200) NULL,
    -- 作文の設問（OCR の結果を利用者が直したもの）
    "設問文"             TEXT         NULL,
    -- 手書き作文（同上）
    "作文本文"           TEXT         NULL,
    -- 語数（作文本文から数えた値。一覧と検索のため持つ）
    "語数"               INTEGER      NOT NULL DEFAULT 0,
    -- A = 有効 / X = 削除（論理削除。2.0 と同じ。添削の履歴は残す）
    "状態コード"         VARCHAR(20)  NOT NULL DEFAULT 'A',

    -- ---- 2.1 の共通規約 ----
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ENG_英作文情報_pkey" PRIMARY KEY ("英作文ID"),
    CONSTRAINT "CK_ENG_英作文_級" CHECK ("英検級" IN ('GRADE1', 'PRE1', 'GRADE2')),
    CONSTRAINT "CK_ENG_英作文_状態" CHECK ("状態コード" IN ('A', 'X')),
    CONSTRAINT "CK_ENG_英作文_語数" CHECK ("語数" >= 0),
    CONSTRAINT "FK_ENG_英作文_利用者" FOREIGN KEY ("利用者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE CASCADE
);

-- 一覧（自分の作文を新しい順に引く）
CREATE INDEX IF NOT EXISTS idx_eng_essay_owner_created
    ON public."ENG_英作文情報" ("利用者アカウントID", "登録日時" DESC, "英作文ID" DESC)
    WHERE "状態コード" = 'A';

-- 絞り込み（英検級）と、提出済みの判定
CREATE INDEX IF NOT EXISTS idx_eng_essay_owner_level
    ON public."ENG_英作文情報" ("利用者アカウントID", "英検級")
    WHERE "状態コード" = 'A';

COMMENT ON TABLE public."ENG_英作文情報" IS
    '英作文の提出（1 行 = 1 回の提出）。添削の結果は ENG_AI添削履歴情報 に積む（何度も添削できる）';
COMMENT ON COLUMN public."ENG_英作文情報"."英検級" IS
    'GRADE1 / PRE1 / GRADE2。添削の観点の満点（8 か 4）はこの級で決まる';
COMMENT ON COLUMN public."ENG_英作文情報"."題" IS
    '題（日本語）。AI が付けた主題タイトルを利用者が直せる。レポートの「作文タイトル」に出す';
COMMENT ON COLUMN public."ENG_英作文情報"."設問文" IS
    '作文の設問。OCR の結果を利用者が直したもの（添削のたびに履歴へ写す）';
COMMENT ON COLUMN public."ENG_英作文情報"."作文本文" IS
    '手書き作文の文字起こし。OCR の結果を利用者が直したもの（添削のたびに履歴へ写す）';
COMMENT ON COLUMN public."ENG_英作文情報"."状態コード" IS
    'A = 有効 / X = 削除（論理削除）。削除しても添削の履歴は残す（履歴側で見られる）';

-- ---- database/英作文/TBL_ENG_英作文画像情報.sql と同じ ----
-- ============================================================================
-- Study 2.1  英作文AI添削 / 英作文画像情報  DDL
-- テーブル: ENG_英作文画像情報 （★2.1 新設。2.0 の STY_英作文画像情報 に相当）
-- ----------------------------------------------------------------------------
-- 1 行 = 1 枚の画像（設問画像／答案画像）。実体は DB の外（相対パス + ファイル名）。
-- OCR の結果（その画像の文字と信頼度）も**この行に持つ**（利用者が直した本文は
-- ENG_英作文情報 側にあり、こちらは認識した生の結果）。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順: TBL_ENG_英作文情報.sql の後
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."ENG_英作文画像情報" (
    "英作文画像ID"        BIGSERIAL    NOT NULL,
    "英作文ID"            BIGINT       NOT NULL,
    -- 表示順（1 から。設問画像 → 答案画像の順に見せる）
    "表示順"              INTEGER      NOT NULL DEFAULT 1,
    -- question = 設問画像 / answer = 答案画像（利用者が 1 枚ずつ指定する）
    "画像区分"            VARCHAR(20)  NOT NULL DEFAULT 'question',
    -- 上げられた元のファイル名（画面に出す）
    "原本ファイル名"      VARCHAR(300) NULL,
    -- 保存したファイル名（UUID + 拡張子。元の名前は使わない）
    "保存ファイル名"      VARCHAR(200) NOT NULL,
    -- 保存先の相対パス（例 'english-essay/2/202609'。ストレージ根は設定で決まる）
    "相対パス"            VARCHAR(500) NOT NULL,
    "MIMEタイプ"          VARCHAR(120) NOT NULL,
    "ファイルサイズ"      BIGINT       NOT NULL DEFAULT 0,
    -- OCR（画像 → 文字）の結果。まだ読んでいなければ NULL
    "認識テキスト"        TEXT         NULL,
    -- 認識の信頼度（0〜100）。画面に「信頼度 N%」と出す
    "認識信頼度"          INTEGER      NULL,

    "登録者アカウントID"  BIGINT       NULL,
    "登録日時"            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ENG_英作文画像情報_pkey" PRIMARY KEY ("英作文画像ID"),
    CONSTRAINT "CK_ENG_英作文画像_区分" CHECK ("画像区分" IN ('question', 'answer')),
    CONSTRAINT "CK_ENG_英作文画像_順" CHECK ("表示順" >= 1),
    CONSTRAINT "CK_ENG_英作文画像_信頼度" CHECK ("認識信頼度" IS NULL OR ("認識信頼度" >= 0 AND "認識信頼度" <= 100)),
    -- 同じ作文の中で表示順は重ならない
    CONSTRAINT "UK_ENG_英作文画像_順" UNIQUE ("英作文ID", "表示順"),
    CONSTRAINT "FK_ENG_英作文画像_作文" FOREIGN KEY ("英作文ID")
        REFERENCES public."ENG_英作文情報" ("英作文ID") ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_eng_essay_image_essay
    ON public."ENG_英作文画像情報" ("英作文ID", "表示順");

COMMENT ON TABLE public."ENG_英作文画像情報" IS
    '英作文の設問・答案の画像（1 行 = 1 枚）。実体は DB の外に置き、ここには場所と OCR の結果を持つ';
COMMENT ON COLUMN public."ENG_英作文画像情報"."画像区分" IS
    'question = 設問画像 / answer = 答案画像。利用者が 1 枚ずつ指定する（2.0 と同じ）';
COMMENT ON COLUMN public."ENG_英作文画像情報"."認識テキスト" IS
    'この画像から読み取った文字。設問か答案かで、作文情報の 設問文／作文本文 にまとめる';
COMMENT ON COLUMN public."ENG_英作文画像情報"."認識信頼度" IS
    'OCR の信頼度（0〜100）。画面に「信頼度 N%」と出す（区分ごとの平均も出す）';

-- ---- database/英作文/TBL_ENG_AI添削履歴情報.sql と同じ ----
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

