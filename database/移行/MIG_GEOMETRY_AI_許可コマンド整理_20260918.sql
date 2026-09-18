-- ============================================================================
-- Study 2.1  移行: AI 生図／画図助手の「許可コマンド一覧」から**この版の GeoGebra に無いもの**を外す
-- 設定: GEOMETRY_AI / GEOMETRY_AI_ALLOWED_COMMANDS
-- ----------------------------------------------------------------------------
-- 実測（GeoGebra 5.4.920.0・実機の applet で 1 つずつ実行）で**存在しない**と分かったコマンドを外す。
-- 残しておくと AI がそれを選び、実行時に必ず失敗する（実測: AI が Triangle を返して失敗した）。
--
--   外したもの: Triangle / Square / RegularPolygon / ParallelLine / Zeroes /
--               CircleThrough / CircleWithCenterThroughPoint / CircleWithCenterRadius /
--               SetColor / SetLineThickness / ShowLabel / SetLabelMode / SetVisibleInView
--   代わりに使うもの（同じことができる・実機で確認済み）:
--     Triangle → Polygon(A, B, C)          Square → Polygon(A, B, 4)
--     RegularPolygon → Polygon(A, B, n)    ParallelLine → Line(P, f)（点 P を通り f に平行）
--     Zeroes → Roots / Root                 円の長い名前 → Circle(...) の 3 つの形
--     表示（色・太さ・ラベル）→ この版ではコマンドから変えられない（指定しない）
--   なお Dilate は存在するが引数の順が「倍率 → 中心」（Dilate(A, 2, (0, 0))）。
--
-- 何度流しても同じ（同じ値を入れるだけ）。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: database/設定/TBL_COM_設定情報_init.sql の後
-- ============================================================================

BEGIN;

UPDATE public."COM_設定情報"
   SET "設定値" = 'Point,Segment,Line,Ray,Vector,Polygon,Rectangle,Circle,Semicircle,Arc,Angle,AngleBisector,PerpendicularLine,PerpendicularBisector,Midpoint,Intersect,Polyline,Distance,Length,Area,Slope,Tangent,Text,Function,Curve,Derivative,Integral,Root,Extremum,Reflect,Rotate,Translate,Dilate,Sequence'
 WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_ALLOWED_COMMANDS' AND "スコープ" = 'GLOBAL';

UPDATE public."COM_設定項目"
   SET "説明" = 'AI 生図・画図助手で使ってよいコマンド。**この版の GeoGebra に無いものは入れない**（実機で実行して確認する）'
 WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_ALLOWED_COMMANDS';

COMMIT;

-- 確認用:
--   SELECT "設定値" FROM public."COM_設定情報"
--    WHERE "ページ区分"='GEOMETRY_AI' AND "設定キー"='GEOMETRY_AI_ALLOWED_COMMANDS';
