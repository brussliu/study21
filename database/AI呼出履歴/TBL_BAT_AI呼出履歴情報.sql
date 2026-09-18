-- ============================================================================
-- Study 2.1  AI呼出履歴 DDL（最終仕様）
-- テーブル: BAT_AI呼出履歴情報
-- ----------------------------------------------------------------------------
-- 2.0 の "BAT_AI呼出履歴情報"（66,800 件・約 394MB・稼働中）を 2.1 の規約で再設計する。
-- バッチが AI（LLM）を呼び出した 1 回 = 1 行。プロンプトとレスポンスの本文も持つため
-- 行が大きく、これが表のサイズの大半を占める（最大 209KB のレスポンスがある）。
-- 2.0 の履歴管理画面（history.jsp）の「AI呼出履歴」タブが参照していた。
-- 2.1 では バッチ管理 の子画面 **AI呼出履歴**（`/{area}/batch-ai-history`）が参照する。
--
-- 2.0 からの主な変更:
--   1. 結果区分を '成功'/'失敗' からコード（SUCCESS / FAILURE）へ。CHECK で固定した。
--   2. 追記専用のテーブルなので バージョン（楽観的ロック）は持たない。
--   3. 監査を 2.1 の規約に合わせた。2.0 の 登録ID / 更新ID は 'batch' などの固定値で
--      実行者を特定できないため、アカウントID は NULL とし 登録元コード='BATCH' で表す
--      （移行データは 'BATCH' ではなく 'MIGRATION'）。
--   4. 数値・時刻の範囲を CHECK で固定した（2.0 は制約が無く、異常値が入り得た）。
--   5. 索引を 2.1 の参照パターン（時系列、バッチ別、AI 別、結果別）に揃えた。
--
-- 2.0 の実データ（2026-02-28〜・稼働中）:
--   * バッチコードは 31 種類（batC04 / batC04-1 / batC04-2 / batC04-3 / batL03 など）。
--     枝番つきのコードがあるため BAT_バッチコントロール情報 への FK は張らない。
--   * 実行ID は 66,800 件中 48,336 件が NULL（バッチ実行に紐づかない呼び出しがある）。
--     2.1 の BAT_バッチ実行履歴情報 に存在しない 実行ID もあるため FK は張らない。
--   * 言語区分・AI区分・モデル名 は 2.0 の値（ja / ja-zh / en / 中国語 / 日本語 / ocr / image、
--     qwen / deepseek / bigmodel / chatgpt / doubao / gemini など）をそのまま保持する。
--     UI の選択肢は入力値ではなく COM_設定情報 側で管理する。
--
-- 将来: 件数とサイズが増え続けるため、月単位のパーティション化を検討する
--       （NET_プロキシ通信履歴情報 と同じ課題。docs/履歴管理.md）。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."BAT_AI呼出履歴情報" (
    "呼出履歴ID"         BIGSERIAL    NOT NULL,
    -- 2.0 の 実行ID（2.1 の BAT_バッチ実行履歴情報 に無い場合もあるため FK は張らない）
    "実行ID"             BIGINT       NULL,
    -- 2.0 の タスクコード（batC04-1 などの枝番も含む）
    "バッチコード"       VARCHAR(50)  NOT NULL,
    -- バッチが対象を特定するためのキー（単語 ID・資料 ID など）
    "処理キー"           VARCHAR(300) NULL,
    "言語区分"           VARCHAR(20)  NULL,
    -- qwen / deepseek / chatgpt / doubao / gemini / bigmodel など
    "AI区分"             VARCHAR(20)  NULL,
    "モデル名"           VARCHAR(100) NULL,
    "呼出URL"            TEXT         NULL,
    "HTTPステータス"     INTEGER      NULL,
    "開始日時"           TIMESTAMP    NOT NULL,
    "終了日時"           TIMESTAMP    NULL,
    "処理時間ms"         INTEGER      NULL,
    -- SUCCESS / FAILURE（2.0 の '成功' / '失敗'）
    "結果区分"           VARCHAR(20)  NOT NULL,
    "エラーコード"       VARCHAR(100) NULL,
    "エラーメッセージ"   TEXT         NULL,
    -- 送ったプロンプト（本文。大きい）
    "プロンプト"         TEXT         NULL,
    -- 受け取ったレスポンス（本文。最大 200KB 超）
    "レスポンス"         TEXT         NULL,
    "入力トークン数"     INTEGER      NULL,
    "出力トークン数"     INTEGER      NULL,
    "合計トークン数"     INTEGER      NULL,
    -- バッチは人が操作しないため通常 NULL（記録元は 登録元コード で表す）
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'BATCH',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "BAT_AI呼出履歴情報_pkey" PRIMARY KEY ("呼出履歴ID"),
    CONSTRAINT "FK_BAT_AI呼出履歴_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_BAT_AI呼出履歴_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_BAT_AI呼出履歴_バッチコード"
        CHECK (BTRIM("バッチコード") <> ''),
    CONSTRAINT "CK_BAT_AI呼出履歴_結果区分"
        CHECK ("結果区分" IN ('SUCCESS', 'FAILURE')),
    CONSTRAINT "CK_BAT_AI呼出履歴_HTTPステータス"
        CHECK ("HTTPステータス" IS NULL OR ("HTTPステータス" BETWEEN 100 AND 599)),
    CONSTRAINT "CK_BAT_AI呼出履歴_処理時間"
        CHECK ("処理時間ms" IS NULL OR "処理時間ms" >= 0),
    -- 終了が開始より前になることはない
    CONSTRAINT "CK_BAT_AI呼出履歴_終了日時"
        CHECK ("終了日時" IS NULL OR "終了日時" >= "開始日時"),
    CONSTRAINT "CK_BAT_AI呼出履歴_トークン数"
        CHECK (
            ("入力トークン数" IS NULL OR "入力トークン数" >= 0)
            AND ("出力トークン数" IS NULL OR "出力トークン数" >= 0)
            AND ("合計トークン数" IS NULL OR "合計トークン数" >= 0)
        )
);

-- 時系列の一覧（AI呼出履歴画面は新しい順に出す）。
CREATE INDEX IF NOT EXISTS idx_bat_ai_call_started
    ON public."BAT_AI呼出履歴情報" ("開始日時" DESC, "呼出履歴ID" DESC);

-- バッチ別の一覧。
CREATE INDEX IF NOT EXISTS idx_bat_ai_call_batch
    ON public."BAT_AI呼出履歴情報" ("バッチコード", "開始日時" DESC);

-- AI 区分・モデル別の集計（トークン数・コストの分析に使う）。
CREATE INDEX IF NOT EXISTS idx_bat_ai_call_ai_model
    ON public."BAT_AI呼出履歴情報" ("AI区分", "モデル名", "開始日時" DESC);

-- 失敗だけを見る（結果別の一覧）。
CREATE INDEX IF NOT EXISTS idx_bat_ai_call_result
    ON public."BAT_AI呼出履歴情報" ("結果区分", "開始日時" DESC);

-- バッチ実行からの逆引き。
CREATE INDEX IF NOT EXISTS idx_bat_ai_call_execution
    ON public."BAT_AI呼出履歴情報" ("実行ID");

COMMENT ON TABLE public."BAT_AI呼出履歴情報" IS
    'バッチの AI（LLM）呼び出し履歴（1 行 = 1 呼び出し）。プロンプトとレスポンスの本文を含む';
COMMENT ON COLUMN public."BAT_AI呼出履歴情報"."実行ID" IS
    '2.0 の 実行ID。2.1 のバッチから記録するときは BAT_バッチ実行履歴情報.実行ID を入れる（画面からの同期呼び出し＝AI 画図助手は NULL）';
COMMENT ON COLUMN public."BAT_AI呼出履歴情報"."バッチコード" IS
    '2.0 の タスクコード（batC04-1 などの枝番つきも含むため BAT_バッチコントロール情報 への FK は張らない）。バッチ以外の呼び出しは機能コードを入れる（例: ''geometry-ai-assist''）';
COMMENT ON COLUMN public."BAT_AI呼出履歴情報"."結果区分" IS 'SUCCESS=成功 / FAILURE=失敗（2.0 の 成功 / 失敗）';
COMMENT ON COLUMN public."BAT_AI呼出履歴情報"."プロンプト" IS 'AI に送ったプロンプト本文';
COMMENT ON COLUMN public."BAT_AI呼出履歴情報"."レスポンス" IS 'AI から受け取ったレスポンス本文（最大 200KB 超になる）';
COMMENT ON COLUMN public."BAT_AI呼出履歴情報"."登録元コード" IS
    'BATCH=バッチからの記録 / MIGRATION=2.0 からの移行 / APP=アプリからの登録';
