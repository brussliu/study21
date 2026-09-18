-- ============================================================================
-- Study 2.1  移行: AI 画図助手の履歴を端末をまたいで見るための索引
-- テーブル: GEO_AI画図指示情報
-- ----------------------------------------------------------------------------
-- 背景: 画面の会話ログは localStorage（端末ごと）に持っていたため、別の端末・別の
--       ブラウザで開くと履歴が見えなかった。DB の GEO_AI画図指示情報 を
--       「図形 + 自分」で引いて履歴にする（`GET /api/user/geometry/ai/assist?figureId=`）。
--
-- 追加するもの:
--   idx_geo_ai_assist_figure_owner … 図形 × 登録者 で新しい順に引くための索引
--   適用区分 のコメント更新        … 画面が自動で反映するようになったので表現を直す
--
-- 何度流しても同じ（CREATE INDEX IF NOT EXISTS / COMMENT ON は上書き）。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: database/図形管理/TBL_GEO_AI画図指示情報.sql の後
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_geo_ai_assist_figure_owner
    ON public."GEO_AI画図指示情報" ("図形ID", "登録者アカウントID", "画図指示ID" DESC);

COMMENT ON COLUMN public."GEO_AI画図指示情報"."適用区分" IS
    'SUGGESTED=変更案を返した（未反映） / APPLIED=作図に反映した（画面は自動で反映する） / REJECTED=反映しなかった（反映できなかった理由は エラーメッセージ） / FAILED=AI の呼び出し・検証に失敗した';
