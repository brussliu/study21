-- ============================================================================
-- Study 2.1  AI 画図指示（AI 画図助手）DDL
-- テーブル: GEO_AI画図指示情報
-- ----------------------------------------------------------------------------
-- 作図画面の「AI 画図助手」（日本語の指示から作図を直す）1 回 = 1 行。
-- 設計: tmp/geometry-ai-design.md §3.2
--
-- 画図助手は利用者の指示に対して 1 回だけ AI を呼ぶ（対話的）。利用者の要望で
-- **バッチ batC54 として実行**する（他の AI 呼び出しと同じくバッチ管理・実行履歴で追える）。
-- 流れ: user-api が「生成状態=PENDING」の行を作る → admin-api の batC54 が拾って AI を呼ぶ
--       → 生成コマンド・説明を書いて「生成状態=READY」（失敗は FAILED + 理由）。
--       画面は GET /assist/{id} で状態を見ながら待つ。
--
-- 画面の会話ログ（指示履歴）は 図形ID + 登録者アカウントID で引く（端末をまたいで同じ履歴が
-- 見えるように）。指示前XML は 1 行が大きいので一覧では返さず、【戻す】のときに 1 件だけ取る。
--
-- 生成状態:
--   PENDING    依頼を作った（AI 未実行。batC54 のキュー）
--   GENERATING batC54 が実行中
--   READY      AI の応答を保存した
--   FAILED      AI 呼び出し・検証に失敗（理由は エラーコード / エラーメッセージ）
--
-- 適用区分:
--   SUGGESTED AI が変更案を返した（まだ作図へ反映していない）… 記録時の既定
--   APPLIED   作図に取り込んだ（画面は案が返ると**押させずに**反映する）
--   REJECTED  作図に反映しなかった（AI の応答は成功している）。理由（どの行で失敗したか）は
--             エラーメッセージに残す＝端末をまたいだ履歴でも同じ理由が見える
--   FAILED    AI の呼び出し・検証に失敗した（作図は変わっていない）
--
-- 反映区分（記録用の反映方法。**新しい依頼は APPEND だけ**＝画面に選択は置かない）:
--   APPEND  今の作図に追加する（いまはこれだけ。作り直したいときは利用者が【全消去】してから指示する）
--   REPLACE 作図全体を作り直す（過去の履歴の表示・互換のためだけに残る。値は変えない）
--
-- AI 呼出履歴（BAT_AI呼出履歴情報）には バッチコード='geometry-ai-assist' で 1 行残す。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."GEO_AI画図指示情報" (
    "画図指示ID"         BIGSERIAL    NOT NULL,
    "図形ID"             BIGINT       NULL,   -- 編集対象。新規作図中は NULL
    "指示文"             TEXT         NOT NULL,
    "指示前XML"          TEXT         NULL,   -- 適用前の作図データ（取消・検証用）
    "作図オブジェクト"   TEXT         NULL,   -- 指示時点のオブジェクト一覧（「名前 = 定義（型）」）。AI の {objects} に使う
    "失敗の内容"         TEXT         NULL,   -- 前の案が実行できなかった内容（行と理由）。AI の {failure} に使う
    "生成コマンド"       TEXT         NULL,
    "生成コマンド数"     INTEGER      NULL,
    "適用区分"           VARCHAR(20)  NOT NULL DEFAULT 'SUGGESTED', -- SUGGESTED / APPLIED / REJECTED / FAILED
    "反映区分"           VARCHAR(20)  NOT NULL DEFAULT 'APPEND',   -- APPEND / REPLACE
    "説明"               TEXT         NULL,   -- AI が返した日本語の説明（画面に出す）
    "AI呼出履歴ID"       BIGINT       NULL,
    -- PENDING=依頼済み / GENERATING=batC54 実行中 / READY=生成済み / FAILED=失敗
    "生成状態"           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    "再試行回数"         INTEGER      NOT NULL DEFAULT 0,
    "エラーコード"       VARCHAR(100) NULL,
    "エラーメッセージ"   TEXT         NULL,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "GEO_AI画図指示情報_pkey" PRIMARY KEY ("画図指示ID"),
    CONSTRAINT "FK_GEO_AI画図指示_図形" FOREIGN KEY ("図形ID")
        REFERENCES public."GEO_図形情報" ("図形ID") ON DELETE RESTRICT,
    CONSTRAINT "FK_GEO_AI画図指示_登録者" FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_GEO_AI画図指示_更新者" FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_GEO_AI画図指示_適用" CHECK ("適用区分" IN ('SUGGESTED','APPLIED','REJECTED','FAILED')),
    CONSTRAINT "CK_GEO_AI画図指示_反映" CHECK ("反映区分" IN ('APPEND','REPLACE')),
    CONSTRAINT "CK_GEO_AI画図指示_生成状態" CHECK ("生成状態" IN ('PENDING','GENERATING','READY','FAILED')),
    CONSTRAINT "CK_GEO_AI画図指示_再試行" CHECK ("再試行回数" >= 0),
    CONSTRAINT "CK_GEO_AI画図指示_コマンド数" CHECK ("生成コマンド数" IS NULL OR "生成コマンド数" >= 0)
);

-- 既に作ってある環境でも列・状態の値域が揃うように貼り直す（何度流しても同じ）
ALTER TABLE public."GEO_AI画図指示情報"
    ADD COLUMN IF NOT EXISTS "生成状態" VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE public."GEO_AI画図指示情報"
    ADD COLUMN IF NOT EXISTS "再試行回数" INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public."GEO_AI画図指示情報" DROP CONSTRAINT IF EXISTS "CK_GEO_AI画図指示_生成状態";
ALTER TABLE public."GEO_AI画図指示情報" ADD CONSTRAINT "CK_GEO_AI画図指示_生成状態"
    CHECK ("生成状態" IN ('PENDING','GENERATING','READY','FAILED'));
ALTER TABLE public."GEO_AI画図指示情報" DROP CONSTRAINT IF EXISTS "CK_GEO_AI画図指示_再試行";
ALTER TABLE public."GEO_AI画図指示情報" ADD CONSTRAINT "CK_GEO_AI画図指示_再試行"
    CHECK ("再試行回数" >= 0);

-- 既に作ってある環境でも状態の値域が揃うように、CHECK と既定値は貼り直す（何度流しても同じ）
ALTER TABLE public."GEO_AI画図指示情報" DROP CONSTRAINT IF EXISTS "CK_GEO_AI画図指示_適用";
ALTER TABLE public."GEO_AI画図指示情報" ALTER COLUMN "適用区分" SET DEFAULT 'SUGGESTED';
ALTER TABLE public."GEO_AI画図指示情報" ADD CONSTRAINT "CK_GEO_AI画図指示_適用"
    CHECK ("適用区分" IN ('SUGGESTED','APPLIED','REJECTED','FAILED'));

CREATE INDEX IF NOT EXISTS idx_geo_ai_assist_figure_created
    ON public."GEO_AI画図指示情報" ("図形ID", "登録日時" DESC);
-- 画面が図形ごとの履歴（自分が作った行）を新しい順に引くための索引
CREATE INDEX IF NOT EXISTS idx_geo_ai_assist_figure_owner
    ON public."GEO_AI画図指示情報" ("図形ID", "登録者アカウントID", "画図指示ID" DESC);
CREATE INDEX IF NOT EXISTS idx_geo_ai_assist_account_created
    ON public."GEO_AI画図指示情報" ("登録者アカウントID", "登録日時" DESC);
-- batC54 が PENDING / GENERATING の行を拾うための索引
CREATE INDEX IF NOT EXISTS idx_geo_ai_assist_status_created
    ON public."GEO_AI画図指示情報" ("生成状態", "登録日時");

COMMENT ON TABLE public."GEO_AI画図指示情報" IS
    E'AI 画図助手（作図画面の日本語指示 → GeoGebra コマンド）の 1 指示 = 1 行。\n2.0 に無い新機能なので移行は無い。\n指示前XML は「反映前に何だったか」を残すため（利用者の取消・問い合わせ対応）。';
COMMENT ON COLUMN public."GEO_AI画図指示情報"."図形ID" IS
    '編集している GEO_図形情報.図形ID。新規作図中（未保存）は NULL';
COMMENT ON COLUMN public."GEO_AI画図指示情報"."適用区分" IS
    'SUGGESTED=変更案を返した（未反映） / APPLIED=作図に反映した（画面は自動で反映する） / REJECTED=反映しなかった（反映できなかった理由は エラーメッセージ） / FAILED=AI の呼び出し・検証に失敗した';
COMMENT ON COLUMN public."GEO_AI画図指示情報"."反映区分" IS
    '記録用の反映方法。APPEND=今の作図に追加（**いまは新しい依頼はこれだけ**）/ REPLACE=作図全体を作り直す（過去の履歴の表示・互換のためだけに残る）';
COMMENT ON COLUMN public."GEO_AI画図指示情報"."AI呼出履歴ID" IS
    'BAT_AI呼出履歴情報.呼出履歴ID（バッチコード=''geometry-ai-assist''）。FK は張らない';
COMMENT ON COLUMN public."GEO_AI画図指示情報"."生成状態" IS
    'PENDING=依頼済み（batC54 のキュー）/ GENERATING=batC54 実行中 / READY=生成済み / FAILED=失敗（理由は エラーコード・エラーメッセージ）';
COMMENT ON COLUMN public."GEO_AI画図指示情報"."再試行回数" IS
    'batC54 が再実行した回数（AI 呼び出しの失敗・タイムアウトからの復帰用）';
