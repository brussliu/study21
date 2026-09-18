-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_テスト情報（日本語テストの 1 回分）
-- ----------------------------------------------------------------------------
-- 2.0 の `STY_日本語単語テスト情報`（実データ 15 件。study3 DB。移行日 2026-09-13 実測）を
-- 2.1 の規約で再設計する。出題（1 語 1 行）は `JPN_テスト出題情報`（1,105 件）。
--
-- **課題テーブルと回答履歴を 2.1 では持たない理由**
--   2.0 は「テスト → 出題（1 語）→ 課題（A_STUDY / B1_ORTHOGRAPHY / B2_READING /
--   C1_READING / C2_KANJI / D_CONTEXT_MEANING / E_KANJI_USAGE の段階別）→ 回答履歴」
--   の 4 階層だった（課題 1,326 件・回答履歴 1,113 件）。
--   2.1 はテストを「1 語 1 出題」の 2 階層にし、**結果は 出題情報 の 最終判定・
--   誤答回数・回答回数 で表す**。理由:
--     1. 課題テーブルは中断再開位置を正確に持つためのものだったが、
--        2.1 の画面は 1 語ぶんをまとめて出題する（B は 1 語に 2 課題を続けて出す）ので、
--        「出題単位でどこまで終わったか」だけで再開できる。
--     2. 回答履歴（1 試行ごとの選択内容）は 2.0 でも集計にしか使っておらず、
--        画面に出していたのは 回答回数・誤答回数・正誤 だけだった。
--        同じ情報は 出題情報 の 回答回数・誤答回数・最終判定 で足りる。
--     3. 回答履歴は 1 問 1 行（実データ 1,113 件）で、教材 9,847 語を全問解くと
--        数百万行になる。2.1 は SQL ログを含めて DB を軽く保つ方針なので持たない。
--   ⇒ そのため 2.0 の 課題数/完了課題数/正解課題数/不正解課題数 は
--     2.1 の 出題数/完了出題数/正解数/不正解数 に集約した。
--     **B は 1 語に 2 課題あるので 正解数 + 不正解数 が 出題数 を超える**
--     （実データ: 出題数 90 / 正解数 170 / 不正解数 10）。この CHECK は付けない。
--
-- 2.0 の実データで分かっていること（移行の前提）:
--   * テスト種別 A〜E が各 3 件（計 15 件）。状態は COMPLETED 10 / CREATED 4 / RUNNING 1。
--     2.0 には 'ABORTED' のコードもあるが実データには無い（2.1 の CHECK からは外した）。
--   * `ユーザーID` は **全件 'liu'**（保護者）。2.1 は学習をアカウント単位で持つので
--     `利用者アカウントID`（ACC_アカウント への FK）へ移行する（'liu' → 1）。
--   * `レベル` は **全件 空文字**。実際の対象は 書籍='01.N1~N5日本語単語' と
--     分類開始/終了（Unit011〜Unit012 など）で決まる。空文字は NULL に寄せる。
--   * `出題方式` は全件 'ALL'。`難易度` は全件 'NORMAL'。
--   * `テスト番号` は 'JPT20260831-213640911-e3b22b' 形式（最長 28 文字）。
--
-- 2.0 からの主な変更:
--   1. 持ち主を ユーザーID（VARCHAR）から 利用者アカウントID（FK）へ移した。
--   2. 課題の集計列（課題数/完了課題数/正解課題数/不正解課題数）を
--      正解数/不正解数 に集約した（上記）。
--   3. 状態 → 状態コード、監査列を 2.1 の規約へ統一、バージョンを追加。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_テスト情報" (
    "テストID"           BIGSERIAL    NOT NULL,
    -- 2.0 の テストID。冪等な移行（ON CONFLICT）と突き合わせの根拠として残す
    "旧テストID"         BIGINT       NULL,
    -- 利用者に見せる番号（2.0 の 'JPT20260831-213640911-e3b22b' をそのまま引き継ぐ）
    "テスト番号"         VARCHAR(50)  NOT NULL,
    -- 学習はアカウントごと（2.0 の ユーザーID から移行）
    "利用者アカウントID" BIGINT       NOT NULL,
    -- A.勉強 / B.意味→日本語 / C.漢字→読み / D.文脈詞義判断 / E.漢字選択
    "テスト種別"         CHAR(1)      NOT NULL,
    "レベル"             VARCHAR(20)  NULL,
    "書籍"               VARCHAR(100) NULL,
    "分類開始"           VARCHAR(30)  NULL,
    "分類終了"           VARCHAR(30)  NULL,
    -- EASY / NORMAL / HARD
    "難易度"             VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    -- ALL / RANDOM / REVIEW / WRONG_ONLY
    "出題方式"           VARCHAR(20)  NOT NULL DEFAULT 'ALL',
    -- 作成時の検索条件をそのまま保持する
    "検索条件JSON"       JSONB        NOT NULL DEFAULT '{}',
    -- 対象の単語数（2.0 の 出題数）
    "出題数"             INTEGER      NOT NULL DEFAULT 0,
    "完了出題数"         INTEGER      NOT NULL DEFAULT 0,
    -- 2.0 の 正解課題数 / 不正解課題数（課題を廃止したのでこの列に集約）
    "正解数"             INTEGER      NOT NULL DEFAULT 0,
    "不正解数"           INTEGER      NOT NULL DEFAULT 0,
    -- CREATED=未開始 / RUNNING=実施中 / COMPLETED=完了
    "状態コード"         VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    "開始日時"           TIMESTAMP    NULL,
    "終了日時"           TIMESTAMP    NULL,
    "最終学習日時"       TIMESTAMP    NULL,
    "有効学習時間ms"     BIGINT       NOT NULL DEFAULT 0,
    -- 楽観的ロック
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_テスト情報_pkey" PRIMARY KEY ("テストID"),
    CONSTRAINT "FK_JPN_テスト_利用者"
        FOREIGN KEY ("利用者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_テスト_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_テスト_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_テスト_テスト種別"
        CHECK ("テスト種別" IN ('A', 'B', 'C', 'D', 'E')),
    CONSTRAINT "CK_JPN_テスト_難易度"
        CHECK ("難易度" IN ('EASY', 'NORMAL', 'HARD')),
    CONSTRAINT "CK_JPN_テスト_出題方式"
        CHECK ("出題方式" IN ('ALL', 'RANDOM', 'REVIEW', 'WRONG_ONLY')),
    CONSTRAINT "CK_JPN_テスト_状態コード"
        CHECK ("状態コード" IN ('CREATED', 'RUNNING', 'COMPLETED')),
    CONSTRAINT "CK_JPN_テスト_件数"
        CHECK (
            "出題数" >= 0 AND "完了出題数" >= 0 AND "完了出題数" <= "出題数"
            AND "正解数" >= 0 AND "不正解数" >= 0
            AND "有効学習時間ms" >= 0
        ),
    CONSTRAINT "CK_JPN_テスト_バージョン"
        CHECK ("バージョン" >= 1),
    CONSTRAINT "CK_JPN_テスト_検索条件JSON"
        CHECK (jsonb_typeof("検索条件JSON") = 'object')
);

-- テスト番号は一意（利用者に見せる番号）
CREATE UNIQUE INDEX IF NOT EXISTS uq_jpn_test_number
    ON public."JPN_テスト情報" ("テスト番号");

-- 2.0 の テストID。移行の再実行を冪等にするための一意索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_jpn_test_old_id
    ON public."JPN_テスト情報" ("旧テストID");

-- テスト一覧（利用者ごと・新しい順）
CREATE INDEX IF NOT EXISTS idx_jpn_test_user
    ON public."JPN_テスト情報" ("利用者アカウントID", "登録日時" DESC);

-- 状態での絞り込み（未開始・実施中＝再開候補）
CREATE INDEX IF NOT EXISTS idx_jpn_test_state
    ON public."JPN_テスト情報" ("利用者アカウントID", "状態コード");

COMMENT ON TABLE public."JPN_テスト情報" IS
    '日本語テストの 1 回分（利用者ごと）。2.0 の STY_日本語単語テスト情報（15 件）';
COMMENT ON COLUMN public."JPN_テスト情報"."旧テストID" IS
    '2.0 の テストID。移行の冪等性と突き合わせに使う';
COMMENT ON COLUMN public."JPN_テスト情報"."テスト番号" IS
    '利用者に見せる番号（2.0 の JPTyyyyMMdd-HHmmssSSS-xxxxxx をそのまま引き継ぐ）';
COMMENT ON COLUMN public."JPN_テスト情報"."正解数" IS
    '2.0 の 正解課題数。B は 1 語に 2 課題あるため 出題数 を超えることがある';
COMMENT ON COLUMN public."JPN_テスト情報"."不正解数" IS
    '2.0 の 不正解課題数。2.0 の 課題数/完了課題数 は 2.1 では持たない（出題数/完了出題数 に集約）';
COMMENT ON COLUMN public."JPN_テスト情報"."状態コード" IS
    'CREATED=未開始 / RUNNING=実施中 / COMPLETED=完了（2.0 の ABORTED は実データに無いので持たない）';
COMMENT ON COLUMN public."JPN_テスト情報"."レベル" IS
    '2.0 の実データは全件 空文字（移行時に NULL）。対象範囲は 書籍 と 分類開始/終了 で決まる';
COMMENT ON COLUMN public."JPN_テスト情報"."登録元コード" IS
    'APP=画面からの登録 / MIGRATION=2.0 からの移行';
