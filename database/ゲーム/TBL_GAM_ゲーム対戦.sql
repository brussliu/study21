-- ============================================================================
-- Study 2.1  ゲーム対戦 DDL（最終仕様）
--   テーブル: GAM_対戦情報 / GAM_対戦手情報
-- ----------------------------------------------------------------------------
-- 五子棋（GOMOKU）と 黑白棋（REVERSI）で、生徒と保護者が**同時に対戦**するための表。
-- 2.0 のゲームは 1 人用だけで対戦のデータが無いため、これは 2.1 の新規設計
-- （移行するデータは無い）。
--
-- 設計の考え方:
--   1. **盤面は 対戦手情報（1 手 = 1 行）が正**で、対戦情報の 盤面 は
--      その結果として持つ（表示を速くするため）。理由:
--        ・反則（埋まっている所に置く・他人の手を指す・黒白棋の不正な返し）を
--          サーバー側で検証できる
--        ・履歴・再生・将来の「待った」に使える
--        ・クライアントから送られた盤面を信用しなくてよい（改ざん防止）
--   2. 手番・勝敗・パス（黑白棋）・引き分け・投了は**サーバーが判定**して
--      対戦情報に書き戻す。
--   3. 同時更新は バージョン（楽観的ロック）で検出する。古い画面から指すと 409。
--   4. リアルタイムの通知は SSE（`/api/user/games/stream`）で行うが、
--      **DB が唯一の正**。切断・再読み込み後は必ずこの表から読み直す。
--   5. 招待は別表を作らず「状態コード = WAITING の対戦行」で表す（ユーザーの指定 ①）。
--   6. 持ち時間の制限は無し（ユーザーの指定）。放置された対戦は CANCELLED にできる。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 対戦（1 局 = 1 行）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public."GAM_対戦情報" (
    "対戦ID"               BIGSERIAL    NOT NULL,
    -- GOMOKU=五子棋 / REVERSI=黑白棋
    "ゲーム種別コード"     VARCHAR(20)  NOT NULL,
    "挑戦者アカウントID"   BIGINT       NOT NULL,
    "相手アカウントID"     BIGINT       NOT NULL,
    -- 次に指す人（サーバーが更新する。開始前は黒を持つ人）。
    -- 終了後も NULL にしない（CK_ゲーム対戦_手番）。勝者（引き分けは最後に指した人）を残す
    "手番アカウントID"     BIGINT       NULL,
    -- WAITING=招待中 / PLAYING=対戦中 / FINISHED=終了 / DECLINED=断り / CANCELLED=取消
    "状態コード"           VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    -- 挑戦者が持つ石（GOMOKU: BLACK/WHITE / REVERSI: BLACK/WHITE）。
    -- 申し込むときに「先手（BLACK）／後手（WHITE）」を選ぶ（承諾後の最初の手番は黒を持つ人）
    "挑戦者石コード"       VARCHAR(10)  NOT NULL DEFAULT 'BLACK',
    -- 盤面（2 次元配列。空きは null。行数・列数はゲームごとに固定）
    "盤面"                 JSONB        NOT NULL DEFAULT '[]',
    -- 手数（対戦手情報の件数と一致させる）
    "手数"                 INTEGER      NOT NULL DEFAULT 0,
    -- FINISHED のときの勝者。引き分けは NULL
    "勝者アカウントID"     BIGINT       NULL,
    -- WIN=勝敗がついた / DRAW=引き分け / RESIGN=投了 / TIMEOUT=放置（将来用）
    "結果コード"           VARCHAR(20)  NULL,
    "開始日時"             TIMESTAMP    NULL,
    "終了日時"             TIMESTAMP    NULL,
    "バージョン"           INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID"   BIGINT       NULL,
    "更新者アカウントID"   BIGINT       NULL,
    "登録元コード"         VARCHAR(20)  NULL,
    "更新元コード"         VARCHAR(20)  NULL,
    "登録日時"             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "GAM_対戦情報_pkey" PRIMARY KEY ("対戦ID"),
    CONSTRAINT "FK_ゲーム対戦_挑戦者"
        FOREIGN KEY ("挑戦者アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_ゲーム対戦_相手"
        FOREIGN KEY ("相手アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_ゲーム対戦_手番"
        FOREIGN KEY ("手番アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_ゲーム対戦_勝者"
        FOREIGN KEY ("勝者アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_ゲーム対戦_登録者"
        FOREIGN KEY ("登録者アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_ゲーム対戦_更新者"
        FOREIGN KEY ("更新者アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_ゲーム対戦_種別" CHECK ("ゲーム種別コード" IN ('GOMOKU', 'REVERSI')),
    CONSTRAINT "CK_ゲーム対戦_状態"
        CHECK ("状態コード" IN ('WAITING', 'PLAYING', 'FINISHED', 'DECLINED', 'CANCELLED')),
    CONSTRAINT "CK_ゲーム対戦_石" CHECK ("挑戦者石コード" IN ('BLACK', 'WHITE')),
    CONSTRAINT "CK_ゲーム対戦_結果"
        CHECK ("結果コード" IS NULL OR "結果コード" IN ('WIN', 'DRAW', 'RESIGN', 'TIMEOUT')),
    -- 自分自身とは対戦できない
    CONSTRAINT "CK_ゲーム対戦_別人" CHECK ("挑戦者アカウントID" <> "相手アカウントID"),
    -- 終了した対戦には結果と終了日時が入る
    CONSTRAINT "CK_ゲーム対戦_終了" CHECK ("状態コード" <> 'FINISHED'
        OR ("結果コード" IS NOT NULL AND "終了日時" IS NOT NULL)),
    -- 対戦中・終了は手番の人がいる
    CONSTRAINT "CK_ゲーム対戦_手番" CHECK ("状態コード" NOT IN ('PLAYING', 'FINISHED')
        OR "手番アカウントID" IS NOT NULL),
    CONSTRAINT "CK_ゲーム対戦_手数" CHECK ("手数" >= 0),
    CONSTRAINT "CK_ゲーム対戦_盤面" CHECK (jsonb_typeof("盤面") = 'array'),
    CONSTRAINT "CK_ゲーム対戦_バージョン" CHECK ("バージョン" > 0)
);

-- 対局一覧（自分が関わっている対戦を状態ごとに、新しい順）
CREATE INDEX IF NOT EXISTS idx_gam_match_player
    ON public."GAM_対戦情報" ("状態コード", "更新日時" DESC);
CREATE INDEX IF NOT EXISTS idx_gam_match_challenger
    ON public."GAM_対戦情報" ("挑戦者アカウントID", "更新日時" DESC);
CREATE INDEX IF NOT EXISTS idx_gam_match_opponent
    ON public."GAM_対戦情報" ("相手アカウントID", "更新日時" DESC);

COMMENT ON TABLE public."GAM_対戦情報" IS
    'ゲームの対戦 1 局（五子棋・黑白棋）。盤面の正は GAM_対戦手情報 で、ここは表示用の写しと状態を持つ';
COMMENT ON COLUMN public."GAM_対戦情報"."状態コード" IS
    'WAITING=招待中 / PLAYING=対戦中 / FINISHED=終了 / DECLINED=断り / CANCELLED=取消';
COMMENT ON COLUMN public."GAM_対戦情報"."結果コード" IS 'WIN=勝敗がついた / DRAW=引き分け / RESIGN=投了 / TIMEOUT=放置';
COMMENT ON COLUMN public."GAM_対戦情報"."バージョン" IS '楽観的ロック用。指し手・状態変更のたびに 1 加算';

-- ---------------------------------------------------------------------------
-- 2. 指し手（1 手 = 1 行。盤面の正）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public."GAM_対戦手情報" (
    "対戦手ID"             BIGSERIAL    NOT NULL,
    "対戦ID"               BIGINT       NOT NULL,
    -- 1 から始まる手数（対戦情報の 手数 と一致）
    "手数"                 INTEGER      NOT NULL,
    "アカウントID"         BIGINT       NOT NULL,
    -- 置いた石（GOMOKU: BLACK/WHITE / REVERSI: BLACK/WHITE）
    "石コード"             VARCHAR(10)  NOT NULL,
    -- 着手（0 始まりの添字）。PASS のときは NULL
    "行"                   INTEGER      NULL,
    "列"                   INTEGER      NULL,
    -- 手番を飛ばした（黑白棋で置ける所が無いとき）
    "パスフラグ"           VARCHAR(1)   NOT NULL DEFAULT '0',
    -- この手で返した石（黑白棋。JSONB の配列 [[行,列], ...]）
    "返した石"             JSONB        NOT NULL DEFAULT '[]',
    -- この手で揃った並び（五子棋の勝ち判定に使った座標）
    "成立ライン"           JSONB        NOT NULL DEFAULT '[]',
    "登録日時"             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "GAM_対戦手情報_pkey" PRIMARY KEY ("対戦手ID"),
    CONSTRAINT "FK_ゲーム対戦手_対戦"
        FOREIGN KEY ("対戦ID") REFERENCES public."GAM_対戦情報" ("対戦ID") ON DELETE CASCADE,
    CONSTRAINT "FK_ゲーム対戦手_アカウント"
        FOREIGN KEY ("アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_ゲーム対戦手_石" CHECK ("石コード" IN ('BLACK', 'WHITE')),
    CONSTRAINT "CK_ゲーム対戦手_パス" CHECK ("パスフラグ" IN ('0', '1')),
    -- パスのときは座標が無く、それ以外は座標がある
    CONSTRAINT "CK_ゲーム対戦手_座標" CHECK (
        ("パスフラグ" = '1' AND "行" IS NULL AND "列" IS NULL)
        OR ("パスフラグ" = '0' AND "行" IS NOT NULL AND "列" IS NOT NULL AND "行" >= 0 AND "列" >= 0)),
    CONSTRAINT "CK_ゲーム対戦手_手数" CHECK ("手数" >= 1),
    CONSTRAINT "CK_ゲーム対戦手_返し" CHECK (jsonb_typeof("返した石") = 'array'),
    CONSTRAINT "CK_ゲーム対戦手_ライン" CHECK (jsonb_typeof("成立ライン") = 'array')
);

-- 同じ対戦の同じ手数は 1 行だけ（二重に指せない）
CREATE UNIQUE INDEX IF NOT EXISTS uq_gam_move_number
    ON public."GAM_対戦手情報" ("対戦ID", "手数");
-- 同じ対戦の同じマスは 1 回だけ（反則の二重チェック。パスは対象外）
CREATE UNIQUE INDEX IF NOT EXISTS uq_gam_move_cell
    ON public."GAM_対戦手情報" ("対戦ID", "行", "列")
    WHERE "パスフラグ" = '0';

COMMENT ON TABLE public."GAM_対戦手情報" IS
    'ゲームの指し手（1 手 = 1 行）。盤面はこの表から再現できる（サーバー側の検証の正）';
COMMENT ON COLUMN public."GAM_対戦手情報"."返した石" IS '黑白棋でこの手によって返った石の座標（[[行,列], ...]）';
COMMENT ON COLUMN public."GAM_対戦手情報"."成立ライン" IS '五子棋で勝ちが成立した 5 連の座標（[[行,列], ...]）。勝ちでなければ空';
