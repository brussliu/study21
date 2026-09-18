-- ============================================================================
-- Study 2.1  移行: AI 画図助手をバッチ（batC54）で実行するための列追加
-- テーブル: GEO_AI画図指示情報
-- ----------------------------------------------------------------------------
-- 背景: 画図助手は当初 user-api が**同期で** AI を呼んでいた（バッチを通らない）。
--       利用者の要望で **batC54** として実行し、他の AI 呼び出しと同じく
--       バッチ管理・実行履歴で追えるようにする。
--
-- 追加する列:
--   生成状態   … PENDING（依頼済み＝batC54 のキュー）/ GENERATING / READY / FAILED
--   再試行回数 … batC54 が再実行した回数
--
-- 何度流しても同じ（ADD COLUMN IF NOT EXISTS / DROP CONSTRAINT IF EXISTS）。
-- 既存行は「すでに同期実行で生成済み」なので READY にする（失敗していた行は FAILED）。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: database/図形管理/TBL_GEO_AI画図指示情報.sql の後
-- ============================================================================

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

CREATE INDEX IF NOT EXISTS idx_geo_ai_assist_status_created
    ON public."GEO_AI画図指示情報" ("生成状態", "登録日時");

-- 既存行の補正（列を足した直後は既定値 PENDING なので、履歴の実態に合わせる）
UPDATE public."GEO_AI画図指示情報"
   SET "生成状態" = CASE WHEN "適用区分" = 'FAILED' THEN 'FAILED' ELSE 'READY' END
 WHERE "生成状態" = 'PENDING'
   AND ("生成コマンド" IS NOT NULL OR "適用区分" = 'FAILED');

COMMENT ON COLUMN public."GEO_AI画図指示情報"."生成状態" IS
    'PENDING=依頼済み（batC54 のキュー）/ GENERATING=batC54 実行中 / READY=生成済み / FAILED=失敗（理由は エラーコード・エラーメッセージ）';
COMMENT ON COLUMN public."GEO_AI画図指示情報"."再試行回数" IS
    'batC54 が再実行した回数（AI 呼び出しの失敗・タイムアウトからの復帰用）';
