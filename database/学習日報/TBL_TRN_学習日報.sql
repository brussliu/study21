-- ============================================================================
-- Study 2.1  学習日報 DDL（最終仕様）
--   テーブル: TRN_学習日報情報 / TRN_学習日報授業情報 / TRN_学習日報科目情報
-- ----------------------------------------------------------------------------
-- 2.0 の同名 3 テーブル（実データ: 日報 24 件・授業 133 件・科目 6 件）を
-- 2.1 の規約で再設計する。1 日 = 1 日報で、その日の授業（時限ごと）と
-- 全体の振り返り・4 観点の自己評価を残す。
--
-- 2.0 からの主な変更:
--   1. 持ち主を ユーザーID（'ljz' のような 2.0 独自の ID）から
--      アカウントID（ACC_アカウント への FK）へ移した。ただし 2.0 の ID は
--      2.1 のアカウントと 1 対 1 に対応しないため、旧ユーザーID を残す
--      （移行時に突き合わせが取れた行だけ アカウントID を入れる）。
--   2. 評価（学習集中度・理解度・学習量・学習態度・掌握度）は 1〜5 の CHECK を付けた
--      （2.0 は制約が無く、実データは 3〜4 に収まっていた）。
--   3. 時限は 1〜10 の CHECK、1 日報内で教科の重複を許さない一意制約を付けた。
--   4. 監査を 2.1 の規約（アカウント FK ＋ 登録元/更新元コード）に統一し、
--      楽観的ロック用の バージョン を追加した。
--   5. 索引を 2.1 の参照パターン（アカウント×対象日の降順、日報×時限）に合わせた。
--
-- 画面（frontend/pc-web/src/views/daily-report/DailyReportView.vue）は
-- 月カレンダー・週の時間割・1 日の詳細の 3 つの見方でこのデータを表示する。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."TRN_学習日報情報" (
    "日報ID"             BIGSERIAL    NOT NULL,
    -- 持ち主（生徒）。移行時に突き合わせが取れない場合は NULL
    "アカウントID"       BIGINT       NULL,
    -- 2.0 の ユーザーID（'ljz' など）。突き合わせの根拠として残す
    "旧ユーザーID"       VARCHAR(50)  NULL,
    "対象日"             DATE         NOT NULL,
    "全体の振り返り"     TEXT         NULL,
    -- 1〜5 の自己評価（2.0 は smallint のみで制約なし）
    "学習集中度"         SMALLINT     NULL,
    "理解度"             SMALLINT     NULL,
    "学習量"             SMALLINT     NULL,
    "学習態度"           SMALLINT     NULL,
    "今夜宿題内容"       TEXT         NULL,
    -- その日の区分。祝日・休日にすると授業の記録は消える（画面の日まとめで指定）
    "休日区分コード"     VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    "備考"               VARCHAR(200) NULL,
    -- DRAFT=記載あり（未提出・再提出待ち）/ SUBMITTED=提出済
    "提出状態コード"     VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    -- 最後に提出した日時。下書きに戻しても残す（「再提出待ち」の判定に使う）
    "提出日時"           TIMESTAMP    NULL,
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NULL,
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "TRN_学習日報情報_pkey" PRIMARY KEY ("日報ID"),
    CONSTRAINT "FK_学習日報_アカウント"
        FOREIGN KEY ("アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_学習日報_登録者"
        FOREIGN KEY ("登録者アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_学習日報_更新者"
        FOREIGN KEY ("更新者アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_学習日報_集中度" CHECK ("学習集中度" IS NULL OR "学習集中度" BETWEEN 1 AND 5),
    CONSTRAINT "CK_学習日報_理解度" CHECK ("理解度" IS NULL OR "理解度" BETWEEN 1 AND 5),
    CONSTRAINT "CK_学習日報_学習量" CHECK ("学習量" IS NULL OR "学習量" BETWEEN 1 AND 5),
    CONSTRAINT "CK_学習日報_学習態度" CHECK ("学習態度" IS NULL OR "学習態度" BETWEEN 1 AND 5),
    CONSTRAINT "CK_学習日報_提出状態" CHECK ("提出状態コード" IN ('DRAFT', 'SUBMITTED')),
    CONSTRAINT "CK_学習日報_休日区分" CHECK ("休日区分コード" IN ('NORMAL', 'HOLIDAY', 'REST')),
    CONSTRAINT "CK_学習日報_バージョン" CHECK ("バージョン" > 0),
    -- 同じ人の同じ日は 1 件だけ
    CONSTRAINT "UQ_学習日報_アカウント_対象日" UNIQUE ("アカウントID", "対象日")
);

-- ----------------------------------------------------------------------------
-- 提出（2026-09-12 追加）
--   2.0 の日報には「提出」の概念が無く「日報がある＝提出した」だった。
--   2.1 は 記載あり（下書き）と 提出済 を分ける:
--     * 日報と授業を保存した時点は DRAFT（記載あり）
--     * 画面の【提出】で SUBMITTED（提出済）になる
--     * 提出後に編集すると DRAFT に戻る（＝再提出待ち。提出日時は残す）
--   既存の行（2.0 から移行した 24 件・すでに作成済みの行）は提出済として扱う
--   （database/移行/MIG_TRN_学習日報_提出_20260912.sql）。
-- ----------------------------------------------------------------------------
ALTER TABLE public."TRN_学習日報情報"
    ADD COLUMN IF NOT EXISTS "提出状態コード" VARCHAR(20) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE public."TRN_学習日報情報"
    ADD COLUMN IF NOT EXISTS "提出日時" TIMESTAMP NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'CK_学習日報_提出状態'
    ) THEN
        ALTER TABLE public."TRN_学習日報情報"
            ADD CONSTRAINT "CK_学習日報_提出状態"
            CHECK ("提出状態コード" IN ('DRAFT', 'SUBMITTED'));
    END IF;
END $$;

-- ----------------------------------------------------------------------------
-- 授業ごとの自己評価・ノートの理由・休日区分（2026-09-12 追加）
--   ・4 観点のうち 学習集中度 / 学習量 / 学習態度 は「授業ごと」に移した
--     （理解度は使わない。列は移行データのために残す）
--   ・ノート記録はラジオ（記録した / 記録しない）にし、記録しないときは理由を持つ
--   ・祝日 / 休日 の日は授業の記録を消す（画面の日まとめで指定する）
-- ----------------------------------------------------------------------------
ALTER TABLE public."TRN_学習日報情報"
    ADD COLUMN IF NOT EXISTS "休日区分コード" VARCHAR(20) NOT NULL DEFAULT 'NORMAL';

ALTER TABLE public."TRN_学習日報授業情報"
    ADD COLUMN IF NOT EXISTS "ノート記載しない理由" VARCHAR(200) NULL;
ALTER TABLE public."TRN_学習日報授業情報"
    ADD COLUMN IF NOT EXISTS "学習集中度" SMALLINT NULL;
ALTER TABLE public."TRN_学習日報授業情報"
    ADD COLUMN IF NOT EXISTS "学習量" SMALLINT NULL;
ALTER TABLE public."TRN_学習日報授業情報"
    ADD COLUMN IF NOT EXISTS "学習態度" SMALLINT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'CK_学習日報_休日区分') THEN
        ALTER TABLE public."TRN_学習日報情報"
            ADD CONSTRAINT "CK_学習日報_休日区分"
            CHECK ("休日区分コード" IN ('NORMAL', 'HOLIDAY', 'REST'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'CK_学習日報授業_集中度') THEN
        ALTER TABLE public."TRN_学習日報授業情報"
            ADD CONSTRAINT "CK_学習日報授業_集中度"
            CHECK ("学習集中度" IS NULL OR "学習集中度" BETWEEN 1 AND 5);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'CK_学習日報授業_学習量') THEN
        ALTER TABLE public."TRN_学習日報授業情報"
            ADD CONSTRAINT "CK_学習日報授業_学習量"
            CHECK ("学習量" IS NULL OR "学習量" BETWEEN 1 AND 5);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'CK_学習日報授業_学習態度') THEN
        ALTER TABLE public."TRN_学習日報授業情報"
            ADD CONSTRAINT "CK_学習日報授業_学習態度"
            CHECK ("学習態度" IS NULL OR "学習態度" BETWEEN 1 AND 5);
    END IF;
END $$;

COMMENT ON COLUMN public."TRN_学習日報情報"."休日区分コード" IS
    'NORMAL=通常 / HOLIDAY=祝日 / REST=休日。祝日・休日にすると授業の記録は消え、未提出にも数えない';
COMMENT ON COLUMN public."TRN_学習日報授業情報"."ノート記載しない理由" IS
    'ノートを記録しなかった理由（記録したときは NULL）';
COMMENT ON COLUMN public."TRN_学習日報授業情報"."学習集中度" IS 'この授業の自己評価（1〜5）';
COMMENT ON COLUMN public."TRN_学習日報授業情報"."学習量" IS 'この授業の自己評価（1〜5）';
COMMENT ON COLUMN public."TRN_学習日報授業情報"."学習態度" IS 'この授業の自己評価（1〜5）';

COMMENT ON COLUMN public."TRN_学習日報情報"."提出状態コード" IS
    'DRAFT=記載あり（未提出・再提出待ち）/ SUBMITTED=提出済';
COMMENT ON COLUMN public."TRN_学習日報情報"."提出日時" IS
    '最後に提出した日時。提出後に編集して下書きに戻しても残す（再提出待ちの判定に使う）';

-- 提出済の日を数える（月の提出率・カレンダーの色分け）。
CREATE INDEX IF NOT EXISTS idx_learning_report_submitted
    ON public."TRN_学習日報情報" ("アカウントID", "提出状態コード", "対象日" DESC);

CREATE INDEX IF NOT EXISTS idx_learning_report_account_date
    ON public."TRN_学習日報情報" ("アカウントID", "対象日" DESC);
CREATE INDEX IF NOT EXISTS idx_learning_report_date
    ON public."TRN_学習日報情報" ("対象日" DESC);

CREATE TABLE IF NOT EXISTS public."TRN_学習日報授業情報" (
    "日報授業ID"         BIGSERIAL    NOT NULL,
    "日報ID"             BIGINT       NOT NULL,
    -- 時限（1〜10。2.0 の実データは 1〜7）
    "時限"               INTEGER      NOT NULL,
    "教科名"             VARCHAR(50)  NOT NULL,
    "授業内容"           TEXT         NULL,
    -- 授業の掌握度（1〜5）
    "掌握度"             SMALLINT     NULL,
    -- ノートを記録したか（2.0 の ノート記録フラグ。画面はラジオで選ぶ）
    "ノート記録フラグ"   BOOLEAN      NOT NULL DEFAULT FALSE,
    -- ノートを記録しなかった理由（記録したときは NULL）
    "ノート記載しない理由" VARCHAR(200) NULL,
    -- 授業ごとの自己評価（1〜5。2026-09-12 に日報から授業へ移した）
    "学習集中度"         SMALLINT     NULL,
    "学習量"             SMALLINT     NULL,
    "学習態度"           SMALLINT     NULL,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NULL,
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "TRN_学習日報授業情報_pkey" PRIMARY KEY ("日報授業ID"),
    CONSTRAINT "FK_学習日報授業_日報"
        FOREIGN KEY ("日報ID") REFERENCES public."TRN_学習日報情報" ("日報ID") ON DELETE CASCADE,
    CONSTRAINT "CK_学習日報授業_時限" CHECK ("時限" BETWEEN 1 AND 10),
    CONSTRAINT "CK_学習日報授業_掌握度" CHECK ("掌握度" IS NULL OR "掌握度" BETWEEN 1 AND 5),
    CONSTRAINT "CK_学習日報授業_集中度" CHECK ("学習集中度" IS NULL OR "学習集中度" BETWEEN 1 AND 5),
    CONSTRAINT "CK_学習日報授業_学習量" CHECK ("学習量" IS NULL OR "学習量" BETWEEN 1 AND 5),
    CONSTRAINT "CK_学習日報授業_学習態度" CHECK ("学習態度" IS NULL OR "学習態度" BETWEEN 1 AND 5),
    CONSTRAINT "CK_学習日報授業_教科名" CHECK (BTRIM("教科名") <> ''),
    CONSTRAINT "UQ_学習日報授業_日報_時限" UNIQUE ("日報ID", "時限")
);

CREATE TABLE IF NOT EXISTS public."TRN_学習日報科目情報" (
    "日報科目ID"         BIGSERIAL    NOT NULL,
    "日報ID"             BIGINT       NOT NULL,
    "表示順"             INTEGER      NOT NULL,
    "教科名"             VARCHAR(50)  NOT NULL,
    "学習内容"           TEXT         NULL,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NULL,
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "TRN_学習日報科目情報_pkey" PRIMARY KEY ("日報科目ID"),
    CONSTRAINT "FK_学習日報科目_日報"
        FOREIGN KEY ("日報ID") REFERENCES public."TRN_学習日報情報" ("日報ID") ON DELETE CASCADE,
    CONSTRAINT "CK_学習日報科目_教科名" CHECK (BTRIM("教科名") <> ''),
    CONSTRAINT "UQ_学習日報科目_日報_表示順" UNIQUE ("日報ID", "表示順")
);

COMMENT ON TABLE public."TRN_学習日報情報" IS
    '学習日報（1 行 = 1 日）。授業ごとの記録は TRN_学習日報授業情報、科目ごとの内容は TRN_学習日報科目情報';
COMMENT ON COLUMN public."TRN_学習日報情報"."アカウントID" IS '持ち主（生徒）のアカウント。2.0 から移行した分は突き合わせが取れた行だけ入る';
COMMENT ON COLUMN public."TRN_学習日報情報"."旧ユーザーID" IS '2.0 の ユーザーID（例: ljz）。移行の根拠として保持する';
COMMENT ON COLUMN public."TRN_学習日報情報"."学習集中度" IS '1〜5 の自己評価（画面は 5 段階で表示）';
COMMENT ON TABLE public."TRN_学習日報授業情報" IS '学習日報の授業（時限ごと）。掌握度とノート記録の有無を持つ';
COMMENT ON COLUMN public."TRN_学習日報授業情報"."ノート記録フラグ" IS 'この授業のノートを記録したか（2.0 の ノート記録フラグ）';
COMMENT ON TABLE public."TRN_学習日報科目情報" IS '学習日報の科目ごとの学習内容（2.0 の 科目情報。表示順で並べる）';
