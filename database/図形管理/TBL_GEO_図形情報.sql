-- ============================================================================
-- Study 2.1  図形管理 DDL（最終仕様）
-- テーブル: GEO_図形情報
-- ----------------------------------------------------------------------------
-- 2.0 の図形管理（`geometry.jsp` / `geometry_draw.jsp`）で扱っていた
-- 「GeoGebra で作図した図形」を 2.1 の規約で再設計する。
-- 2.0 のテーブルは `TRN_図形作成情報`（実データ 102 件）。
--
-- 2.0 からの主な変更:
--   1. 主キーを業務キー（'GEO202604250005247792940'）から 2.1 の規約どおり
--      BIGSERIAL に変え、2.0 の 図形ID は 図形番号 として残す
--      （'GEO…' はアプリが採番する利用者に見せる番号）。
--   2. **持ち主は持たない（家族で共有する教材）。** 2.0 に持ち主の概念が無く、
--      実際に 1 つの図形一覧を家族で使っていたため、`アカウントID` のような
--      所有列は作らない。誰が作図・更新したかは 登録者/更新者アカウントID に残す。
--   3. **サムネイルは Base64 で DB に持つ（2.0 と同じ）。**
--      件数が 100 件程度（実測 サムネイルあり 100 件・最大 約 315KB）なので
--      ファイル分離も別テーブルも作らない。分離すると 2.0 からの移行と
--      一覧表示のたびに外部参照が増え、配備（コンテナ）も複雑になるため。
--      ※ `サムネイル画像` は TEXT なので 1GB 近くまで入るが、実際は数百KB まで。
--   4. **タグは `|` 区切り（2.0 互換）。** 2.0 は `String.join("|", tags)` で
--      保存し、検索・タグ候補も `|`（と一部の記号）で分解していた。実データも
--      `三角形|部品` の形（`|` を含む行 30 件）。カンマに変えると 2.0 のデータと
--      突き合わせられず、移行で変換が必要になるだけで得がないためそのまま。
--   5. **`削除FLG` は `状態コード` に置き換えた。論理削除は残す。**
--      2.0 は `削除FLG` '0'/'1' で、削除は `UPDATE … SET 削除FLG='1'`（論理削除）。
--      2.1 は値域を明示した `状態コード`（'ACTIVE' / 'DELETED'）にし、一覧の
--      絞り込みもこの列で行う。物理削除はしない（2.0 と同じ）。
--   6. 監査列・楽観的ロック（バージョン）を 2.1 の規約に統一した。
--
-- 2.0 の実データ（2026-09-13 実測。移行の前提）:
--   * 総数 102 件（登録区分 saved 100 / demo 2。図形種別 は全件 'geometry'）
--   * 状態: ACTIVE 96 / DELETED 6（削除FLG='1' は demo 2 件＋saved 4 件）
--   * サムネイルあり 100 件（Base64、9,734〜322,146 文字）。demo 2 件は空
--   * GeoGebraXML あり 100 件（6,331〜19,686 文字）。demo 2 件は空文字
--   * タグは NULL 36 件、`|` 区切りのみ（カンマ・読点は使われていない）。最長 16 文字
--   * メモ は NULL 83 件・空文字 0 件
--   * 教科 は全件 '数学'、図形名 は最長 12 文字、図形ID は最長 24 文字
--   * 登録ID は 'geometry.jsp'（100 件）と 'system'（2 件）＝人ではないので
--     登録者アカウントID は NULL になる。更新ID は 'geometry.jsp'（96 件）と
--     'ljz'（5 件）・'liu'（1 件）で、この 6 件だけ対応表で引ける
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."GEO_図形情報" (
    "図形ID"             BIGSERIAL    NOT NULL,
    -- 2.0 の 図形ID（'GEO202604250005247792940' / 'geometry-demo-1'）。
    -- 'GEO' + yyyyMMdd + HHmmss + ミリ秒 + 連番 をアプリが採番する
    "図形番号"           VARCHAR(30)  NOT NULL,
    -- 現時点では数学固定（2.0 と同じ。将来 理科 などへ広げる想定）
    "教科"               VARCHAR(20)  NOT NULL DEFAULT '数学',
    -- geometry = 幾何図形 / function = 関数グラフ
    "図形種別"           VARCHAR(20)  NOT NULL DEFAULT 'geometry',
    -- demo = 初期データ（サンプル）/ saved = 利用者が作図したもの
    "登録区分"           VARCHAR(10)  NOT NULL DEFAULT 'saved',
    "図形名"             VARCHAR(120) NOT NULL,
    "メモ"               TEXT         NULL,
    -- '|' 区切りのタグ（例 '三角形|部品'）。未設定は NULL。詳細はヘッダ 4. を参照
    "タグ"               TEXT         NULL,
    -- GeoGebra の作図データ（XML）。未作図は空文字（2.0 と同じ）
    "GeoGebraXML"        TEXT         NOT NULL DEFAULT '',
    -- 一覧用サムネイル（Base64 PNG）。2.0 と同じく DB に持つ。詳細はヘッダ 3. を参照
    "サムネイル画像"     TEXT         NULL,
    -- 一覧の表示順（2.0 は登録時に MAX+1 を採番。同値も許す）
    "表示順"             INTEGER      NOT NULL DEFAULT 0,
    -- ACTIVE = 有効 / DELETED = 削除済（2.0 の 削除FLG '0'/'1' を置き換える）
    "状態コード"         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    -- 楽観的ロック
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    -- APP=画面からの登録 / MIGRATION=2.0 からの移行
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "GEO_図形情報_pkey" PRIMARY KEY ("図形ID"),
    CONSTRAINT "FK_GEO_図形_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_GEO_図形_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_GEO_図形_図形種別"
        CHECK ("図形種別" IN ('geometry', 'function')),
    CONSTRAINT "CK_GEO_図形_登録区分"
        CHECK ("登録区分" IN ('demo', 'saved')),
    CONSTRAINT "CK_GEO_図形_状態コード"
        CHECK ("状態コード" IN ('ACTIVE', 'DELETED')),
    CONSTRAINT "CK_GEO_図形_表示順"
        CHECK ("表示順" >= 0),
    CONSTRAINT "CK_GEO_図形_バージョン"
        CHECK ("バージョン" >= 1)
);

-- 図形番号は一意（利用者に見せる番号。2.0 の 図形ID をそのまま引き継ぐ）
CREATE UNIQUE INDEX IF NOT EXISTS uq_geo_figure_no
    ON public."GEO_図形情報" ("図形番号");

-- 一覧の絞り込み（2.0 の IDX_TRN_図形作成情報_検索 と同じ並び。削除FLG → 状態コード）
CREATE INDEX IF NOT EXISTS idx_geo_figure_search
    ON public."GEO_図形情報" ("教科", "状態コード", "登録区分", "更新日時" DESC);

-- 図形名での検索（2.0 の IDX_TRN_図形作成情報_名称）
CREATE INDEX IF NOT EXISTS idx_geo_figure_name
    ON public."GEO_図形情報" ("教科", "図形名");

-- 表示順の並び（2.0 の IDX_TRN_図形作成情報_表示順）
CREATE INDEX IF NOT EXISTS idx_geo_figure_order
    ON public."GEO_図形情報" ("教科", "表示順", "図形ID");

-- タグの検索（2.0 の IDX_TRN_図形作成情報_タグ。'|' 区切りの部分一致で使う）
CREATE INDEX IF NOT EXISTS idx_geo_figure_tag
    ON public."GEO_図形情報" ("タグ");

-- COMMENT は式を書けない（文字列リテラル 1 つだけ）ので E'' で改行を入れる
COMMENT ON TABLE public."GEO_図形情報" IS
    E'図形管理（数学勉強）で扱う GeoGebra の作図。2.0 の TRN_図形作成情報 を再設計。\n持ち主は持たず家族で共有する教材（2.0 に持ち主の概念が無かったため）。\nサムネイルは Base64 で DB に持つ（2.0 と同じ。件数が 100 件程度なので分離しない）。\nタグは ''|'' 区切り（2.0 互換）。\n削除FLG は 状態コード（ACTIVE / DELETED）に置き換えた（論理削除は残す）。';
COMMENT ON COLUMN public."GEO_図形情報"."図形ID" IS '2.1 の内部 ID（BIGSERIAL）';
COMMENT ON COLUMN public."GEO_図形情報"."図形番号" IS
    '利用者に見せる番号（2.0 の 図形ID。GEO-yyyyMMdd… / geometry-demo-N）';
COMMENT ON COLUMN public."GEO_図形情報"."教科" IS '現時点では数学固定（2.0 と同じ）';
COMMENT ON COLUMN public."GEO_図形情報"."図形種別" IS 'geometry: 幾何図形 / function: 関数グラフ';
COMMENT ON COLUMN public."GEO_図形情報"."登録区分" IS 'demo: 初期データ（サンプル）/ saved: 利用者が作図したもの';
COMMENT ON COLUMN public."GEO_図形情報"."図形名" IS '一覧や作図画面で表示する図形名称';
COMMENT ON COLUMN public."GEO_図形情報"."メモ" IS '図形に対する補足メモ（未設定は NULL）';
COMMENT ON COLUMN public."GEO_図形情報"."タグ" IS
    '| 区切りのタグ（例 ''三角形|部品''。2.0 互換なのでカンマではない）。未設定は NULL';
COMMENT ON COLUMN public."GEO_図形情報"."GeoGebraXML" IS 'GeoGebra の作図データ（XML）。未作図は空文字';
COMMENT ON COLUMN public."GEO_図形情報"."サムネイル画像" IS
    '一覧表示用サムネイル（Base64 PNG。2.0 と同じく DB に持つ）。無い行は NULL';
COMMENT ON COLUMN public."GEO_図形情報"."表示順" IS '一覧の表示順（2.0 は登録時に MAX+1 を採番。同値を許す）';
COMMENT ON COLUMN public."GEO_図形情報"."状態コード" IS
    'ACTIVE: 有効 / DELETED: 削除済（2.0 の 削除FLG ''0''/''1'' を置き換え。論理削除）';
COMMENT ON COLUMN public."GEO_図形情報"."バージョン" IS '楽観的ロック（作図画面からの更新で使う）';
COMMENT ON COLUMN public."GEO_図形情報"."登録元コード" IS
    'APP=画面からの登録 / AI=AI生図（GEO_AI生図リクエスト情報）からの登録 / MIGRATION=2.0 からの移行';
