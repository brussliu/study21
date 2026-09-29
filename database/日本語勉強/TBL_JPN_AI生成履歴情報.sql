-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_AI生成履歴情報（A〜E の内容を AI から取った記録）
-- ----------------------------------------------------------------------------
-- 2.0 は AI の取得を**バッチ側のテーブル**（`BAT_AI*`）で管理していたが、
-- 「どの語の何を、いつ、どのモデルで取ったか」は日本語勉強の機能そのものなので、
-- この機能のテーブルとして持つ。
--
-- 役割の分け方:
--   ・この表（JPN_AI生成履歴情報）… **何を生成したか**の記録。
--     1 行 = 1 語 × 1 内容種別（詳細／読み問題／漢字問題／文脈問題／漢字用法）
--   ・`BAT_AI呼出履歴情報`（既存）… **AI をどう呼んだか**の記録
--     （プロンプト・応答・トークン数・HTTP ステータス・所要時間）。
--     `呼出履歴ID` でこの表から参照する。**FK は付けない**
--     （呼出履歴は保持期間で消えることがあり、生成の記録は残したいため）。
--
-- 内容種別（`内容種別コード`）と 2.0 の画面の対応:
--   A_DETAIL             … 語の詳細（語義・例文・発音・コロケーション・関連語・使用注意）
--                          ＋ B（意味→日本語）で使う情報。`JPN_単語詳細情報` に入る
--   C1_READING           … C の読み問題。`JPN_単語問題情報.問題種別='C1_READING'`
--   C2_KANJI             … C の漢字問題。同 `'C2_KANJI'`
--   D_CONTEXT_MEANING    … D の文脈問題。同 `'D_CONTEXT_MEANING'`
--   E_KANJI_USAGE        … E の漢字用法問題。同 `'E_KANJI_USAGE'`
--   ・A と B は同じ 1 回の取得（詳細JSON）から作れるので、**同じ行**で数える。
--   ・テスト種別 A〜E との対応は 1. の画面設計を参照。
--
-- 2.0 の実データは既に `JPN_単語詳細情報` / `JPN_単語問題情報` へ移行済みなので、
-- この表には**移行しない**（これから実行する取得から記録する）。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_AI生成履歴情報" (
    "生成ID"             BIGSERIAL    NOT NULL,
    -- 対象の語
    "単語ID"             BIGINT       NOT NULL,
    -- 何を生成したか（上の内容種別）
    "内容種別コード"     VARCHAR(30)  NOT NULL,
    -- QUEUED=待ち / RUNNING=実行中 / SUCCEEDED=成功 / FAILED=失敗 / CANCELED=中止
    "状態コード"         VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
    -- 使った AI（BAT_AI呼出履歴情報 と同じ語彙）
    "AI区分"             VARCHAR(20)  NULL,
    "モデル名"           VARCHAR(100) NULL,
    -- 生成の版（同じ語・同じ種別を取り直したら増える）
    "内容版数"           INTEGER      NOT NULL DEFAULT 1,
    -- 呼出の記録（BAT_AI呼出履歴情報.呼出履歴ID）。FK は付けない
    "呼出履歴ID"         BIGINT       NULL,
    -- 生成できた件数と失敗した件数（詳細は語義・例文…の内訳、問題は選択肢を含む問題数）
    "生成件数"           INTEGER      NOT NULL DEFAULT 0,
    "失敗件数"           INTEGER      NOT NULL DEFAULT 0,
    -- 失敗したときの理由（画面にそのまま出す）
    "エラーコード"       VARCHAR(100) NULL,
    "エラーメッセージ"   TEXT         NULL,
    -- 実行時間とトークン（呼出履歴が消えてもコストを見られるように写しを持つ）
    "処理時間ms"         INTEGER      NULL,
    "入力トークン数"     INTEGER      NULL,
    "出力トークン数"     INTEGER      NULL,
    "開始日時"           TIMESTAMP    NULL,
    "終了日時"           TIMESTAMP    NULL,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    -- APP=画面からの実行 / BATCH=定時・バッチからの実行
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_AI生成履歴情報_pkey" PRIMARY KEY ("生成ID"),
    CONSTRAINT "FK_JPN_AI生成_単語"
        FOREIGN KEY ("単語ID")
        REFERENCES public."JPN_単語情報" ("単語ID") ON DELETE CASCADE,
    CONSTRAINT "FK_JPN_AI生成_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_AI生成_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_AI生成_内容種別"
        CHECK ("内容種別コード" IN (
            'A_DETAIL', 'C1_READING', 'C2_KANJI', 'D_CONTEXT_MEANING', 'E_KANJI_USAGE'
        )),
    CONSTRAINT "CK_JPN_AI生成_状態コード"
        CHECK ("状態コード" IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELED')),
    CONSTRAINT "CK_JPN_AI生成_件数"
        CHECK (
            "内容版数" >= 1 AND "生成件数" >= 0 AND "失敗件数" >= 0
            AND ("処理時間ms" IS NULL OR "処理時間ms" >= 0)
            AND ("入力トークン数" IS NULL OR "入力トークン数" >= 0)
            AND ("出力トークン数" IS NULL OR "出力トークン数" >= 0)
        ),
    -- 成功したのに 0 件、という状態を作らない（気づけるようにする）
    CONSTRAINT "CK_JPN_AI生成_成功時の件数"
        CHECK ("状態コード" <> 'SUCCEEDED' OR "生成件数" >= 1)
);

-- 語ごとの生成履歴（詳細画面・単語情報管理の一覧で見る）
CREATE INDEX IF NOT EXISTS idx_jpn_ai_gen_word
    ON public."JPN_AI生成履歴情報" ("単語ID", "内容種別コード", "登録日時" DESC);

-- 実行中のものを見つける（二重起動の防止。未完了だけの部分索引）
CREATE INDEX IF NOT EXISTS idx_jpn_ai_gen_active
    ON public."JPN_AI生成履歴情報" ("単語ID", "内容種別コード")
    WHERE "状態コード" IN ('QUEUED', 'RUNNING');

-- 失敗の一覧（再実行の入口）
CREATE INDEX IF NOT EXISTS idx_jpn_ai_gen_failed
    ON public."JPN_AI生成履歴情報" ("登録日時" DESC)
    WHERE "状態コード" = 'FAILED';

-- 呼出履歴との突き合わせ
CREATE INDEX IF NOT EXISTS idx_jpn_ai_gen_call
    ON public."JPN_AI生成履歴情報" ("呼出履歴ID");

COMMENT ON TABLE public."JPN_AI生成履歴情報" IS
    'A〜E（語の詳細・読み問題・漢字問題・文脈問題・漢字用法）を AI から取った記録。AI の生の呼出は BAT_AI呼出履歴情報 側';
COMMENT ON COLUMN public."JPN_AI生成履歴情報"."内容種別コード" IS
    'A_DETAIL=語の詳細（A と B で使う）/ C1_READING / C2_KANJI / D_CONTEXT_MEANING / E_KANJI_USAGE';
COMMENT ON COLUMN public."JPN_AI生成履歴情報"."内容版数" IS
    '同じ語・同じ種別を取り直したときの版。JPN_単語詳細情報.内容版数 / JPN_単語問題情報.内容版数 と対応する';
COMMENT ON COLUMN public."JPN_AI生成履歴情報"."呼出履歴ID" IS
    'BAT_AI呼出履歴情報.呼出履歴ID。FK は付けない（呼出履歴は保持期間で消えることがあるため）';
COMMENT ON COLUMN public."JPN_AI生成履歴情報"."登録元コード" IS
    'APP=画面からの実行 / BATCH=定時・バッチからの実行';
