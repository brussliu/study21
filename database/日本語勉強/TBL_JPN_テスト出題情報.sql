-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_テスト出題情報（テストに含まれる 1 問＝1 語）
-- ----------------------------------------------------------------------------
-- 2.0 の `STY_日本語単語テスト出題情報`（実データ 1,105 件。study3 DB。移行日 2026-09-13 実測）を
-- 2.1 の規約で再設計する。親は `JPN_テスト情報`（15 件）。
--
-- **2.0 の課題テーブル（1,326 件）と回答履歴（1,113 件）は持たない。**
--   テストの結果はこの行の 出題状態・最終判定・回答回数・誤答回数 で表す
--   （判断の理由は `TBL_JPN_テスト情報.sql` のヘッダーと
--   `database/日本語勉強/日本語勉強設計.md` §2 を参照）。
--
-- 2.0 の実データで分かっていること（移行の前提）:
--   * `出題状態` は ANSWERED 933 / WAITING 172。2.0 には 'ANSWERING' もあるが実データには無い。
--     2.1 のコードは PENDING（=2.0 の WAITING）/ ANSWERED / SKIPPED。
--   * `最終判定` は ANSWERED の行だけ入る: CORRECT 669 / CONFIRMED 213 /
--     INCORRECT 44 / MIXED 7。2.0 の 'SKIPPED' は実データに無い。
--   * `収録ID` は全 1,105 件に入っている（教材のどの位置から出題したか）。
--   * `単語スナップショットJSON`（最大 7,428 文字）は出題時点の単語・詳細の写し。
--     教材や詳細が後から変わっても、解いたときの問題内容を再現できるようにそのまま持つ。
--   * `必要課題数` / `完了課題数` は持たない（課題を廃止したため）。
--   * `問題ID` は 2.0 の移行データでは全件 NULL（課題テーブル側が問題を参照していた）。
--     2.1 で採番したテストはここに `JPN_単語問題情報` の問題IDを入れる。
--
-- 2.0 からの主な変更:
--   1. 主キー 出題ID は BIGSERIAL のまま。2.0 の ID は 旧出題ID に残す。
--   2. 親（テスト・単語）への参照を BIGINT の FK に変えた。
--      `収録ID` は 2.0 では FK だったが、2.1 では教材の改訂で消えても
--      出題の記録を残せるようにするため FK を付けない（表示用の参照だけ）。
--      同じ理由で `問題ID` にも FK を付けない（問題を再生成しても記録は残す）。
--   3. 出題状態 WAITING → PENDING、監査列を 2.1 の規約へ統一。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_テスト出題情報" (
    "出題ID"                 BIGSERIAL    NOT NULL,
    -- 2.0 の 出題ID。冪等な移行（ON CONFLICT）と突き合わせの根拠として残す
    "旧出題ID"               BIGINT       NULL,
    "テストID"               BIGINT       NOT NULL,
    "単語ID"                 BIGINT       NOT NULL,
    -- 出題元の収録位置（2.0 の 収録ID）。2.1 の 単語収録情報 の ID
    "収録ID"                 BIGINT       NULL,
    -- テストの中での出題順（1 から）
    "出題順"                 INTEGER      NOT NULL,
    -- 採番して作ったテストで使う問題。2.0 から移行した出題は NULL
    "問題ID"                 BIGINT       NULL,
    -- PENDING=未回答（2.0 の WAITING）/ ANSWERED=回答済み / SKIPPED=とばした
    "出題状態"               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- CORRECT / INCORRECT / CONFIRMED / MIXED / SKIPPED（2.0 のコードをそのまま）
    "最終判定"               VARCHAR(20)  NULL,
    "回答回数"               INTEGER      NOT NULL DEFAULT 0,
    "誤答回数"               INTEGER      NOT NULL DEFAULT 0,
    "有効学習時間ms"         BIGINT       NOT NULL DEFAULT 0,
    "回答完了日時"           TIMESTAMP    NULL,
    -- 出題時点の単語・詳細の写し（あとで教材が変わっても問題内容を再現できるように残す）
    "単語スナップショットJSON" JSONB      NOT NULL DEFAULT '{}',
    "登録者アカウントID"     BIGINT       NULL,
    "更新者アカウントID"     BIGINT       NULL,
    "登録元コード"           VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"           VARCHAR(20)  NULL,
    "登録日時"               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_テスト出題情報_pkey" PRIMARY KEY ("出題ID"),
    CONSTRAINT "FK_JPN_テスト出題_テスト"
        FOREIGN KEY ("テストID")
        REFERENCES public."JPN_テスト情報" ("テストID") ON DELETE CASCADE,
    CONSTRAINT "FK_JPN_テスト出題_単語"
        FOREIGN KEY ("単語ID")
        REFERENCES public."JPN_単語情報" ("単語ID") ON DELETE CASCADE,
    CONSTRAINT "FK_JPN_テスト出題_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_テスト出題_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_テスト出題_出題順"
        CHECK ("出題順" >= 1),
    CONSTRAINT "CK_JPN_テスト出題_出題状態"
        CHECK ("出題状態" IN ('PENDING', 'ANSWERED', 'SKIPPED')),
    CONSTRAINT "CK_JPN_テスト出題_件数"
        CHECK (
            "回答回数" >= 0 AND "誤答回数" >= 0 AND "誤答回数" <= "回答回数"
            AND "有効学習時間ms" >= 0
        ),
    CONSTRAINT "CK_JPN_テスト出題_スナップショットJSON"
        CHECK (jsonb_typeof("単語スナップショットJSON") = 'object')
);

-- テストの中での出題順は一意
CREATE UNIQUE INDEX IF NOT EXISTS uq_jpn_test_question_order
    ON public."JPN_テスト出題情報" ("テストID", "出題順");

-- 2.0 の 出題ID。移行の再実行を冪等にするための一意索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_jpn_test_question_old_id
    ON public."JPN_テスト出題情報" ("旧出題ID");

-- 語ごとの出題履歴（単語勉強状況・復習の対象を引く）
CREATE INDEX IF NOT EXISTS idx_jpn_test_question_word
    ON public."JPN_テスト出題情報" ("単語ID");

COMMENT ON TABLE public."JPN_テスト出題情報" IS
    'テスト内の 1 語ぶんの出題と結果。2.0 の STY_日本語単語テスト出題情報（1,105 件）';
COMMENT ON COLUMN public."JPN_テスト出題情報"."旧出題ID" IS
    '2.0 の 出題ID。移行の冪等性と突き合わせに使う';
COMMENT ON COLUMN public."JPN_テスト出題情報"."出題状態" IS
    'PENDING=未回答（2.0 の WAITING）/ ANSWERED=回答済み / SKIPPED=とばした';
COMMENT ON COLUMN public."JPN_テスト出題情報"."問題ID" IS
    '採番して作ったテストで使う問題。2.0 から移行した出題は NULL（2.0 は課題側が問題を参照していた）';
COMMENT ON COLUMN public."JPN_テスト出題情報"."単語スナップショットJSON" IS
    '出題時点の単語・詳細の写し。教材や詳細が変わっても解いた内容を再現するために残す';
COMMENT ON COLUMN public."JPN_テスト出題情報"."収録ID" IS
    '出題元の教材の収録位置。FK は付けない（教材の改訂で消えても出題記録を残すため）';
COMMENT ON COLUMN public."JPN_テスト出題情報"."登録元コード" IS
    'APP=画面からの登録 / MIGRATION=2.0 からの移行';

-- ============================================================================
-- 追補（2026-09-13）: 単語を削除したら、その語の出題も一緒に消えるようにした
-- （収録・詳細・問題・学習状況・技能習得と同じ扱い）。
-- すでにテーブルを作ってある環境でも、このファイルをもう一度実行すれば当たる。
-- ============================================================================

ALTER TABLE public."JPN_テスト出題情報"
    DROP CONSTRAINT IF EXISTS "FK_JPN_テスト出題_単語";
ALTER TABLE public."JPN_テスト出題情報"
    ADD CONSTRAINT "FK_JPN_テスト出題_単語"
        FOREIGN KEY ("単語ID")
        REFERENCES public."JPN_単語情報" ("単語ID") ON DELETE CASCADE;
