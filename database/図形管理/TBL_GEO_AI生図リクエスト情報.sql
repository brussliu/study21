-- ============================================================================
-- Study 2.1  AI 生図リクエスト DDL
-- テーブル: GEO_AI生図リクエスト情報
-- ----------------------------------------------------------------------------
-- 「画像を上げて AI に GeoGebra コマンドを作らせる」1 回 = 1 行。
-- 2.0 に AI 生図は無いので移行は無い（新規テーブル）。
-- 設計: tmp/geometry-ai-design.md §3.1
--
-- 状態機械（状態コード）:
--   QUEUED         受付済（順番待ち）             … user-api が作成
--   PREPROCESSING  読み取り中（働き手が確保）      … admin-api の働き手
--   PREPROCESSED   読み取り済（AI 生成待ち）       … admin-api の働き手
--   GENERATING     AI 生成中（呼び出しの前に確定） … batC51-A〜D
--   GENERATED      生成済（検証待ち）             … batC51-A〜D
--   VALIDATING     検証・保存中                   … admin-api の働き手
--   READY          完了（作図できます）           … admin-api の働き手
--   REGISTERED     完了（図形として保存済）        … user-api /confirm
--   NEEDS_INPUT    追加入力待ち（AI が質問）       … admin-api の働き手
--   FAILED         失敗（失敗工程コードで区別）    … 各工程
--   CANCELLED      利用者が取消                   … user-api /cancel
--
-- 実行は**バックエンドの働き手**（admin-api の GeometryAiWorker）が行う。
-- 画面は要求行を作るだけで、起動 API を呼ばなくてよい（画面を閉じても処理は続く）。
--
-- 画像は DB に持たずファイルで持つ（GEO_図形情報 のサムネイルとは方針が違う。
-- 理由: アップロード画像は数百 KB〜数 MB で、AI へ送る前に作り直す中間生成物であり、
-- 一覧に出すのは切り抜きプレビューだけなので、COM_臨時ファイル情報 と同じ
-- 「パス + ファイル名」方式にして API から配信する）。
-- 保存先は application.yml の study21.geometry-ai.storage-root。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: 何度流しても同じ（CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS）
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."GEO_AI生図リクエスト情報" (
    "生図リクエストID"   BIGSERIAL    NOT NULL,
    -- 利用者に見せる番号（'AIG' + yyyyMMddHHmmssSSS + 4桁。図形番号と同じ採番方式）
    "要求番号"           VARCHAR(30)  NOT NULL,
    -- QUEUED / PREPROCESSED / GENERATING / GENERATED / READY / REGISTERED / FAILED / CANCELLED
    "状態コード"         VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',

    -- ---- 画像（元画像と切り抜き画像・AI へ送る画像） ----
    "元画像パス"         VARCHAR(255) NULL,   -- 例 'geometry-ai/2/202609'
    "元画像名称"         VARCHAR(200) NULL,   -- 例 'a1b2c3d4.png'
    "元画像MIME"         VARCHAR(120) NULL,
    "元画像サイズ"       BIGINT       NULL,
    "元画像幅"           INTEGER      NULL,
    "元画像高さ"         INTEGER      NULL,
    "切抜画像パス"       VARCHAR(255) NULL,
    "切抜画像名称"       VARCHAR(200) NULL,
    "切抜画像サイズ"     BIGINT       NULL,
    "切抜画像幅"         INTEGER      NULL,
    "切抜画像高さ"       INTEGER      NULL,
    -- 切り抜き範囲（元画像に対する正規化座標 0..1。画面のドラッグ選択をそのまま保存）
    "切抜範囲X"          NUMERIC(6,5) NULL,
    "切抜範囲Y"          NUMERIC(6,5) NULL,
    "切抜範囲幅"         NUMERIC(6,5) NULL,
    "切抜範囲高さ"       NUMERIC(6,5) NULL,

    -- ---- 作図方法（A〜D）と作成する図の種類 ----
    -- 利用者が選んだ作図方法。NULL は歴史的な要求（モードが無い時代。A として扱う）
    "作図モード"         VARCHAR(10)  NULL,   -- A / B / C / D
    -- 利用者が指定した「作成する図の種類」（AUTO が既定）
    "結果種別要求"       VARCHAR(20)  NULL,   -- AUTO / GEOMETRY / GRAPH / MIXED
    -- 実際に作る種類（AI が決めた／検証で確定した値。決められないときは NULL）
    "結果種別確定"       VARCHAR(20)  NULL,   -- GEOMETRY / GRAPH / MIXED
    -- モードと種類に当てはまる補充パラメータ（日本語の項目名 → 値。当てはまらない項目は入らない）
    "補充パラメータ"     JSONB        NULL,
    -- 追跡用の設定スナップショット（テンプレートの版＝ハッシュなど。**API キーは入れない**）
    "設定スナップショット" JSONB      NULL,
    -- AI の判定（GENERATABLE / NEEDS_INPUT / UNSUPPORTED。**実行・保存の結果ではない**）
    "判定"               VARCHAR(20)  NULL,
    -- 追加入力待ちの質問（配列。利用者が答えて送り直す）
    "確認質問"           JSONB        NULL,

    -- ---- 分類（歴史的な列。画面はモードと結果種別を使う） ----
    -- 利用者が選んだ大分類（画面の kind）。NULL は「未指定」＝設定の既定を使う
    "利用者区分"         VARCHAR(20)  NULL,   -- FIGURE / FUNCTION / MIXED
    -- 利用者が選んだ図形の種類（画面の subKind。利用者区分が FIGURE のときだけ入る）
    "利用者図形種"       VARCHAR(20)  NULL,   -- TRIANGLE / CIRCLE / QUAD / OTHER
    -- AI が判定した大分類（画面の kind と同じ値域。利用者区分が NULL のときだけ作図種別に使う）
    "AI区分"             VARCHAR(20)  NULL,   -- FIGURE / FUNCTION / MIXED / UNKNOWN
    -- GeoGebra の appName に対応する最終種別（GEO_図形情報.図形種別 と同じ値域）
    "作図種別"           VARCHAR(20)  NOT NULL DEFAULT 'geometry',

    -- ---- AI の入出力 ----
    "利用者指示"         TEXT         NULL,   -- 画面で任意に入れる補足指示
    "送信プロンプト"     TEXT         NULL,   -- 実際に送った本文（画像は伏せた要約に置換）
    "生成コマンド"       TEXT         NULL,   -- AI が返した GeoGebra コマンド（1 行 1 コマンド）
    "生成コマンド数"     INTEGER      NULL,
    "提案JSON"           JSONB        NULL,   -- {図形名, タグ[], メモ, 数式, 認識テキスト}
    "検証エラー内容"     TEXT         NULL,   -- batC53 の検証で落ちた理由（行番号つき）
    "AI呼出履歴ID"       BIGINT       NULL,   -- BAT_AI呼出履歴情報.呼出履歴ID（FK は張らない）
    "再試行回数"         INTEGER      NOT NULL DEFAULT 0,

    -- ---- 失敗 ----
    -- PREPROCESS / GENERATE / VALIDATE
    "失敗工程コード"     VARCHAR(20)  NULL,
    "エラーコード"       VARCHAR(100) NULL,
    "エラーメッセージ"   TEXT         NULL,

    -- ---- 実行したバッチへの参照（AI 生成 = batC51-A〜D。前処理・検証はバッチではないので NULL） ----
    "前処理実行ID"       BIGINT       NULL,
    "AI実行ID"           BIGINT       NULL,
    "確定実行ID"         BIGINT       NULL,

    -- ---- 結果として作られた図形 ----
    "図形ID"             BIGINT       NULL,

    -- ---- 2.1 の共通規約 ----
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "GEO_AI生図リクエスト情報_pkey" PRIMARY KEY ("生図リクエストID"),
    CONSTRAINT "FK_GEO_AI生図_登録者" FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_GEO_AI生図_更新者" FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_GEO_AI生図_図形" FOREIGN KEY ("図形ID")
        REFERENCES public."GEO_図形情報" ("図形ID") ON DELETE RESTRICT,
    CONSTRAINT "CK_GEO_AI生図_状態" CHECK ("状態コード" IN
        ('QUEUED','PREPROCESSING','PREPROCESSED','GENERATING','GENERATED','VALIDATING',
         'READY','REGISTERED','NEEDS_INPUT','FAILED','CANCELLED')),
    -- 作図方法と結果種別（画面の語彙と同じ値）
    CONSTRAINT "CK_GEO_AI生図_作図モード" CHECK ("作図モード" IS NULL OR "作図モード" IN
        ('A','B','C','D')),
    CONSTRAINT "CK_GEO_AI生図_結果種別要求" CHECK ("結果種別要求" IS NULL OR "結果種別要求" IN
        ('AUTO','GEOMETRY','GRAPH','MIXED')),
    CONSTRAINT "CK_GEO_AI生図_結果種別確定" CHECK ("結果種別確定" IS NULL OR "結果種別確定" IN
        ('GEOMETRY','GRAPH','MIXED')),
    CONSTRAINT "CK_GEO_AI生図_判定" CHECK ("判定" IS NULL OR "判定" IN
        ('GENERATABLE','NEEDS_INPUT','UNSUPPORTED')),
    CONSTRAINT "CK_GEO_AI生図_作図種別" CHECK ("作図種別" IN ('geometry','function')),
    -- 分類コードは画面（GeometryAiView.vue）の kind / subKind と同じ値
    CONSTRAINT "CK_GEO_AI生図_利用者区分" CHECK ("利用者区分" IS NULL OR "利用者区分" IN
        ('FIGURE','FUNCTION','MIXED')),
    CONSTRAINT "CK_GEO_AI生図_利用者図形種" CHECK ("利用者図形種" IS NULL OR "利用者図形種" IN
        ('TRIANGLE','CIRCLE','QUAD','OTHER')),
    CONSTRAINT "CK_GEO_AI生図_AI区分" CHECK ("AI区分" IS NULL OR "AI区分" IN
        ('FIGURE','FUNCTION','MIXED','UNKNOWN')),
    -- 図形の種類は「利用者区分が FIGURE」のときだけ入る
    CONSTRAINT "CK_GEO_AI生図_図形種の整合" CHECK ("利用者図形種" IS NULL OR "利用者区分" = 'FIGURE'),
    CONSTRAINT "CK_GEO_AI生図_失敗工程" CHECK ("失敗工程コード" IS NULL OR "失敗工程コード" IN
        ('PREPROCESS','GENERATE','VALIDATE')),
    -- 切り抜き範囲は正規化座標（0..1）で、最小 5%（画面の CROP_MIN = 0.05 と同じ）
    CONSTRAINT "CK_GEO_AI生図_切抜範囲" CHECK (
        ("切抜範囲X"     IS NULL OR ("切抜範囲X"     >= 0 AND "切抜範囲X"     <= 1))
        AND ("切抜範囲Y"     IS NULL OR ("切抜範囲Y"     >= 0 AND "切抜範囲Y"     <= 1))
        AND ("切抜範囲幅"    IS NULL OR ("切抜範囲幅"    >= 0.05 AND "切抜範囲幅"    <= 1))
        AND ("切抜範囲高さ"  IS NULL OR ("切抜範囲高さ"  >= 0.05 AND "切抜範囲高さ"  <= 1))
        -- はみ出し防止（x + w <= 1）
        AND ("切抜範囲X" IS NULL OR "切抜範囲幅" IS NULL OR "切抜範囲X" + "切抜範囲幅" <= 1)
        AND ("切抜範囲Y" IS NULL OR "切抜範囲高さ" IS NULL OR "切抜範囲Y" + "切抜範囲高さ" <= 1)
    ),
    CONSTRAINT "CK_GEO_AI生図_サイズ" CHECK (
        ("元画像サイズ" IS NULL OR "元画像サイズ" >= 0)
        AND ("切抜画像サイズ" IS NULL OR "切抜画像サイズ" >= 0)
        AND ("元画像幅" IS NULL OR "元画像幅" > 0)
        AND ("元画像高さ" IS NULL OR "元画像高さ" > 0)
    ),
    CONSTRAINT "CK_GEO_AI生図_コマンド数" CHECK ("生成コマンド数" IS NULL OR "生成コマンド数" >= 0),
    CONSTRAINT "CK_GEO_AI生図_再試行" CHECK ("再試行回数" >= 0),
    CONSTRAINT "CK_GEO_AI生図_バージョン" CHECK ("バージョン" >= 1),
    -- FAILED のときは失敗工程が入っていること（状態と原因の食い違いを防ぐ）
    CONSTRAINT "CK_GEO_AI生図_失敗工程必須" CHECK ("状態コード" <> 'FAILED' OR "失敗工程コード" IS NOT NULL),
    -- REGISTERED のときは図形が入っていること
    CONSTRAINT "CK_GEO_AI生図_図形必須" CHECK ("状態コード" <> 'REGISTERED' OR "図形ID" IS NOT NULL)
);

-- 要求番号は一意（利用者に見せる番号）
CREATE UNIQUE INDEX IF NOT EXISTS uq_geo_ai_request_no
    ON public."GEO_AI生図リクエスト情報" ("要求番号");

-- 一覧（新しい順。図形管理の AI 生図履歴）
CREATE INDEX IF NOT EXISTS idx_geo_ai_request_created
    ON public."GEO_AI生図リクエスト情報" ("登録日時" DESC, "生図リクエストID" DESC);

-- 未処理の探索（バッチが拾う）。部分索引で小さく保つ
-- 働き手（worker）が「まだ終わっていない行」を毎周期さがすので、処理中の状態を全部含める
CREATE INDEX IF NOT EXISTS idx_geo_ai_request_pending
    ON public."GEO_AI生図リクエスト情報" ("状態コード", "登録日時")
    WHERE "状態コード" IN ('QUEUED','PREPROCESSING','PREPROCESSED','GENERATING','GENERATED','VALIDATING');

-- 利用者ごとの履歴・日次上限の集計
CREATE INDEX IF NOT EXISTS idx_geo_ai_request_account_created
    ON public."GEO_AI生図リクエスト情報" ("登録者アカウントID", "登録日時" DESC);

-- AI 呼出履歴からの逆引き
CREATE INDEX IF NOT EXISTS idx_geo_ai_request_call
    ON public."GEO_AI生図リクエスト情報" ("AI呼出履歴ID");

-- 図形からの逆引き（どの AI 生図で作った図形か）
CREATE INDEX IF NOT EXISTS idx_geo_ai_request_figure
    ON public."GEO_AI生図リクエスト情報" ("図形ID");

COMMENT ON TABLE public."GEO_AI生図リクエスト情報" IS
    E'AI 生図（画像 → 分類 → AI → GeoGebra コマンド）の 1 リクエスト = 1 行。\n2.0 に無い新機能なので移行は無い。\n画像はファイルで持ち（パス + ファイル名）、DB には持たない。\nGeoGebraXML とサムネイルはここには持たない（作図画面の applet が作る）。';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."状態コード" IS
    'QUEUED=待機中 / PREPROCESSING=読み取り中 / PREPROCESSED=読み取り済 / GENERATING=生成中 / GENERATED=生成済（検証待ち） / VALIDATING=検証・保存中 / READY=完了（作図できます） / REGISTERED=完了（図形として保存済） / NEEDS_INPUT=追加入力待ち / FAILED=失敗 / CANCELLED=取消';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."作図モード" IS
    '利用者が選んだ作図方法（画面 GeometryAiView.vue）。A=画像をもとに再現 / B=数式からグラフを作成 / C=文章の条件から作図 / D=文章と図を合わせて作図。NULL=モードが無い時代の要求（A として扱う）';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."結果種別要求" IS
    '利用者が指定した「作成する図の種類」。AUTO=自動判定（既定）/ GEOMETRY / GRAPH / MIXED。B（数式からグラフ）はサーバーで GRAPH に固定する';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."結果種別確定" IS
    '実際に作る種類（GEOMETRY / GRAPH / MIXED）。AUTO のときだけ AI が決める。決められないときは NULL';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."補充パラメータ" IS
    'モードと結果種別に当てはまる補充パラメータ（日本語の項目名 → 値）。当てはまらない項目は保存しない（AI へ渡さない）';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."設定スナップショット" IS
    '追跡用の設定スナップショット（テンプレートの版＝ハッシュ・使った側など）。**API キーは入れない**';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."判定" IS
    'AI の判定（GENERATABLE / NEEDS_INPUT / UNSUPPORTED）。**実行・検証・保存が成功したかは別**（状態コードが表す）';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."確認質問" IS
    '追加入力待ち（NEEDS_INPUT）のときの質問（配列）。利用者が答えて同じ行を送り直す（/resubmit）';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."利用者区分" IS
    '利用者が選んだ大分類（画面 GeometryAiView.vue の kind）。FIGURE=図形 / FUNCTION=関数グラフ / MIXED=判別が難しい複合図形。NULL=未指定（設定の既定を使う）';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."利用者図形種" IS
    '利用者が選んだ図形の種類（画面の subKind。利用者区分が FIGURE のときだけ入る）。TRIANGLE=三角形 / CIRCLE=円 / QUAD=四角形 / OTHER=その他・複合';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."AI区分" IS
    'AI が判定した大分類（利用者区分と同じ値域 + UNKNOWN）。利用者区分が NULL のときだけ 作図種別 の決定に使う';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."作図種別" IS
    'geometry: 幾何図形 / function: 関数グラフ（GEO_図形情報.図形種別 と同じ値域。GeoGebra の appName を決める）';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."切抜範囲X" IS
    '切り抜き範囲（元画像に対する正規化座標 0..1）。画面のドラッグ選択（CropRect {x,y,w,h}）をそのまま保存する。最小 5%（CROP_MIN=0.05）';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."生成コマンド" IS
    'AI が返した GeoGebra コマンド（1 行 1 コマンド）。XML ではない（XML は作図画面の applet が作る）';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."送信プロンプト" IS
    'AI へ送った本文。画像は base64 を含めず <image:...> の要約に置換して保存する（呼出履歴を太らせないため）';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."AI呼出履歴ID" IS
    'BAT_AI呼出履歴情報.呼出履歴ID。履歴表は将来パーティション化の予定があるため FK は張らない';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."失敗工程コード" IS
    'PREPROCESS=前処理 / GENERATE=AI生成 / VALIDATE=検証';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."前処理実行ID" IS
    'BAT_バッチ実行履歴情報.実行ID（batC51）。FK は張らず索引も作らない（参照は要求行から実行行への一方向）';
COMMENT ON COLUMN public."GEO_AI生図リクエスト情報"."図形ID" IS
    'REGISTERED のとき、作図画面で保存された GEO_図形情報.図形ID';
