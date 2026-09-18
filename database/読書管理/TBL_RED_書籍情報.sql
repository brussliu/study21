-- ============================================================================
-- Study 2.1  読書管理 DDL（最終仕様）
-- テーブル: RED_書籍情報
-- ----------------------------------------------------------------------------
-- 2.0 の英語読書（`english_reading.jsp` / `english_reading_reader.jsp`）で扱っていた
-- 書籍を 2.1 の規約で再設計する。2.0 のテーブルは `TRN_英語読書書籍情報`（実データ 6 冊）。
--
-- 2.0 からの主な変更:
--   1. 主キーを業務キー（'ER-20260402-124745'）から 2.1 の規約どおり BIGSERIAL に変え、
--      2.0 の ID は 書籍番号 として残す（利用者に見せる番号。アプリが採番する）。
--      読書記録・標記は 書籍ID（内部ID）で親を参照する。
--   2. 本棚は**家族で共有**する（2.0 に持ち主の概念が無く、実際に 1 つの本棚を
--      家族で使っていたため）。誰が登録したかは 登録者/更新者アカウントID に残す。
--   3. 監査・楽観的ロック（バージョン）を 2.1 の規約に統一した。
--   4. PDF のファイル参照（2.0 の COM_ファイル情報 PDF / COVER）は持たない。
--      2.1 の閲覧画面は本文 PDF を使わず、ページごとの標記・メモ・進捗を扱う
--      （database/読書管理/読書管理設計.md を参照）。
--
-- 2.0 の実データ（移行後）:
--   * 教科 は '英語'（2.0 も英語固定。ただし '射鵰英雄伝'（中国語）が 1 冊あり、
--     タグに '中国語' が入っている。教科はそのまま '英語' で移行し、タグで区別する）
--   * タグ はカンマ区切りのテキスト（'Novel' / '中国語' など。未設定は NULL）
--   * 総ページ数 は PDF 原本のページ数（159〜1515）。現在ページ は最後に読んだページ
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."RED_書籍情報" (
    "書籍ID"             BIGSERIAL    NOT NULL,
    -- 2.0 の 書籍ID（'ER-20260402-124745'）。アプリが ER-yyyyMMdd-HHmmss で採番する
    "書籍番号"           VARCHAR(30)  NOT NULL,
    -- 現時点では英語固定（2.0 と同じ）
    "教科"               VARCHAR(20)  NOT NULL DEFAULT '英語',
    "書籍名"             VARCHAR(150) NOT NULL,
    "作者"               VARCHAR(120) NOT NULL,
    -- Starter / Elementary / Intermediate / Upper
    "難易度"             VARCHAR(20)  NOT NULL DEFAULT 'Elementary',
    -- 未着手 / 読書中 / 一時停止 / 読了
    "読書ステータス"     VARCHAR(20)  NOT NULL DEFAULT '未着手',
    "総ページ数"         INTEGER      NOT NULL,
    -- 最後に読んだページ（総ページ数を超えないようサービス側で丸める）
    "現在ページ"         INTEGER      NOT NULL DEFAULT 1,
    -- true の書籍を一覧の先頭に表示する
    "置頂フラグ"         BOOLEAN      NOT NULL DEFAULT FALSE,
    -- カンマ区切り（'Novel,Classic'）。未設定は NULL
    "タグ"               TEXT         NULL,
    "概要"               TEXT         NULL,
    -- 直近 1 回の読書時間（分）
    "最近の読書時間分"   INTEGER      NOT NULL DEFAULT 0,
    "累計読書時間分"     INTEGER      NOT NULL DEFAULT 0,
    "累計標記件数"       INTEGER      NOT NULL DEFAULT 0,
    "最終読書日時"       TIMESTAMP    NULL,
    "備考"               TEXT         NULL,
    -- 楽観的ロック
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    -- APP=画面からの登録 / MIGRATION=2.0 からの移行
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "RED_書籍情報_pkey" PRIMARY KEY ("書籍ID"),
    CONSTRAINT "FK_RED_書籍_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_RED_書籍_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_RED_書籍_難易度"
        CHECK ("難易度" IN ('Starter', 'Elementary', 'Intermediate', 'Upper')),
    CONSTRAINT "CK_RED_書籍_読書ステータス"
        CHECK ("読書ステータス" IN ('未着手', '読書中', '一時停止', '読了')),
    CONSTRAINT "CK_RED_書籍_総ページ数"
        CHECK ("総ページ数" >= 1),
    CONSTRAINT "CK_RED_書籍_現在ページ"
        CHECK ("現在ページ" >= 1),
    CONSTRAINT "CK_RED_書籍_最近の読書時間分"
        CHECK ("最近の読書時間分" >= 0),
    CONSTRAINT "CK_RED_書籍_累計読書時間分"
        CHECK ("累計読書時間分" >= 0),
    CONSTRAINT "CK_RED_書籍_累計標記件数"
        CHECK ("累計標記件数" >= 0),
    CONSTRAINT "CK_RED_書籍_バージョン"
        CHECK ("バージョン" >= 1)
);

-- 書籍番号は一意（利用者に見せる番号）
CREATE UNIQUE INDEX IF NOT EXISTS uq_red_book_no
    ON public."RED_書籍情報" ("書籍番号");

-- 一覧（置頂を先頭に、次に最後に読んだ順）
CREATE INDEX IF NOT EXISTS idx_red_book_shelf
    ON public."RED_書籍情報" ("置頂フラグ" DESC, "最終読書日時" DESC NULLS LAST, "書籍ID" DESC);

-- 絞り込み（教科・難易度・読書ステータス）
CREATE INDEX IF NOT EXISTS idx_red_book_filter
    ON public."RED_書籍情報" ("教科", "難易度", "読書ステータス");

-- 書籍名での検索
CREATE INDEX IF NOT EXISTS idx_red_book_name
    ON public."RED_書籍情報" ("教科", "書籍名");

COMMENT ON TABLE public."RED_書籍情報" IS
    '読書管理で扱う書籍（家族で共有する本棚）。2.0 の TRN_英語読書書籍情報';
COMMENT ON COLUMN public."RED_書籍情報"."書籍番号" IS
    '利用者に見せる番号（2.0 の 書籍ID。ER-yyyyMMdd-HHmmss）';
COMMENT ON COLUMN public."RED_書籍情報"."教科" IS '現時点では英語固定';
COMMENT ON COLUMN public."RED_書籍情報"."難易度" IS 'Starter / Elementary / Intermediate / Upper';
COMMENT ON COLUMN public."RED_書籍情報"."読書ステータス" IS '未着手 / 読書中 / 一時停止 / 読了';
COMMENT ON COLUMN public."RED_書籍情報"."総ページ数" IS '本の総ページ数';
COMMENT ON COLUMN public."RED_書籍情報"."現在ページ" IS '最後に読んだページ';
COMMENT ON COLUMN public."RED_書籍情報"."置頂フラグ" IS 'true の書籍を一覧の先頭に表示する';
COMMENT ON COLUMN public."RED_書籍情報"."タグ" IS 'カンマ区切りのタグ（未設定は NULL）';
COMMENT ON COLUMN public."RED_書籍情報"."累計標記件数" IS '全ページの標記件数（2.0 の集計値を引き継ぐ）';
COMMENT ON COLUMN public."RED_書籍情報"."登録元コード" IS 'APP=画面からの登録 / MIGRATION=2.0 からの移行';

-- ============================================================================
-- 追補（2026-09-13）: 本棚の分類（保護者が本を分類して素早く探せるようにする）。
-- 分類マスタは `TBL_RED_書籍分類情報.sql`。分類を消したら本は「未分類」に戻す
-- （本を失わないため ON DELETE SET NULL）。
-- すでにテーブルを作ってある環境でも、このファイルをもう一度実行すれば当たる。
-- ============================================================================

ALTER TABLE public."RED_書籍情報"
    ADD COLUMN IF NOT EXISTS "分類ID" BIGINT NULL;

ALTER TABLE public."RED_書籍情報"
    DROP CONSTRAINT IF EXISTS "FK_RED_書籍_分類";
ALTER TABLE public."RED_書籍情報"
    ADD CONSTRAINT "FK_RED_書籍_分類"
        FOREIGN KEY ("分類ID")
        REFERENCES public."RED_書籍分類情報" ("分類ID") ON DELETE SET NULL;

-- 本棚は分類ごとにまとめて出す（表示順は分類側で決める）
CREATE INDEX IF NOT EXISTS idx_red_book_category
    ON public."RED_書籍情報" ("分類ID", "置頂フラグ" DESC, "書籍ID" DESC);

COMMENT ON COLUMN public."RED_書籍情報"."分類ID" IS
    '本棚の分類（RED_書籍分類情報）。NULL は未分類。分類を削除すると NULL に戻る';

-- ============================================================================
-- 追補（2026-09-13）: 言語（中国語 / 英語 / 日本語）。
-- 利用者の指示により、分類は「小説 / 雑誌」のような**ジャンル**だけを持ち、
-- 「英語」「中国語」は分類ではなく 言語 列で表す（教科 は 2.0 から引き継いだ
-- 名残で '英語' 固定のままだが、画面では使わない）。
-- すでにテーブルを作ってある環境でも、このファイルをもう一度実行すれば当たる。
-- ============================================================================

ALTER TABLE public."RED_書籍情報"
    ADD COLUMN IF NOT EXISTS "言語" VARCHAR(20) NOT NULL DEFAULT '英語';

ALTER TABLE public."RED_書籍情報"
    DROP CONSTRAINT IF EXISTS "CK_RED_書籍_言語";
ALTER TABLE public."RED_書籍情報"
    ADD CONSTRAINT "CK_RED_書籍_言語"
        CHECK ("言語" IN ('中国語', '英語', '日本語'));

CREATE INDEX IF NOT EXISTS idx_red_book_language
    ON public."RED_書籍情報" ("言語", "書籍ID");

COMMENT ON COLUMN public."RED_書籍情報"."言語" IS
    '本の言語（中国語 / 英語 / 日本語）。分類はジャンルだけを持ち、言語はこの列で表す';
COMMENT ON COLUMN public."RED_書籍情報"."教科" IS
    '2.0 から引き継いだ列（実データは全件 英語）。画面では使わず、言語 を使う';

-- ============================================================================
-- 追補（2026-09-14）: 公開範囲（全体書籍 / 家庭の書籍）と、所有する家庭。
-- ----------------------------------------------------------------------------
-- 2026-09-14 の決定:
--   * 「家庭」＝既存の「保護者—生徒」の紐付け。新しい家庭ID は作らず、
--     生徒の `ACC_アカウント.アカウントID` を `家族学生ID` として使う
--     （`DOC_資料情報.家族学生ID` と同じ考え方）。
--   * いままでの「家族で共有する 1 つの本棚」は **全体書籍（GLOBAL）**として
--     そのまま引き継ぐ（利用者の見た目は変わらない）。全体書籍は管理者が管理し、
--     全家庭の【図書館】に出る。
--   * 保護者が登録した本は **家庭の書籍（FAMILY）**。その家庭の保護者・生徒の
--     【図書館】だけに出る。
--
-- 既定値は FAMILY。GLOBAL は付け忘れると `CK_RED_書籍_公開範囲と所有` で落ちるので、
-- 事故で全体公開になることはない（全体書籍は管理者の登録経路が明示的に指定する）。
-- すでにテーブルを作ってある環境でも、このファイルをもう一度実行すれば当たる。
-- ============================================================================

ALTER TABLE public."RED_書籍情報"
    ADD COLUMN IF NOT EXISTS "公開範囲コード" VARCHAR(20) NULL;

ALTER TABLE public."RED_書籍情報"
    ADD COLUMN IF NOT EXISTS "所有家族学生ID" BIGINT NULL;

-- 既存行（2.0/2.1 の共有本棚）は「全体書籍」として引き継ぐ。再実行時は 0 件。
UPDATE public."RED_書籍情報"
   SET "公開範囲コード" = 'GLOBAL'
 WHERE "公開範囲コード" IS NULL;

ALTER TABLE public."RED_書籍情報" ALTER COLUMN "公開範囲コード" SET DEFAULT 'FAMILY';
ALTER TABLE public."RED_書籍情報" ALTER COLUMN "公開範囲コード" SET NOT NULL;

ALTER TABLE public."RED_書籍情報" DROP CONSTRAINT IF EXISTS "CK_RED_書籍_公開範囲";
ALTER TABLE public."RED_書籍情報"
    ADD CONSTRAINT "CK_RED_書籍_公開範囲"
        CHECK ("公開範囲コード" IN ('GLOBAL', 'FAMILY'));

ALTER TABLE public."RED_書籍情報" DROP CONSTRAINT IF EXISTS "FK_RED_書籍_所有家族";
ALTER TABLE public."RED_書籍情報"
    ADD CONSTRAINT "FK_RED_書籍_所有家族"
        FOREIGN KEY ("所有家族学生ID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT;

-- GLOBAL は所有元を持たない / FAMILY は必ず持つ（付け忘れを DB で防ぐ）
ALTER TABLE public."RED_書籍情報" DROP CONSTRAINT IF EXISTS "CK_RED_書籍_公開範囲と所有";
ALTER TABLE public."RED_書籍情報"
    ADD CONSTRAINT "CK_RED_書籍_公開範囲と所有"
        CHECK (("公開範囲コード" = 'GLOBAL'  AND "所有家族学生ID" IS NULL)
            OR ("公開範囲コード" = 'FAMILY'  AND "所有家族学生ID" IS NOT NULL));

-- 可視スコープの絞り込み（GLOBAL ∪ 自分の家庭）
CREATE INDEX IF NOT EXISTS idx_red_book_scope
    ON public."RED_書籍情報" ("公開範囲コード", "所有家族学生ID", "書籍ID" DESC);

COMMENT ON COLUMN public."RED_書籍情報"."公開範囲コード" IS
    'GLOBAL=管理者が登録した全体書籍 / FAMILY=家庭の中だけで見える書籍';
COMMENT ON COLUMN public."RED_書籍情報"."所有家族学生ID" IS
    'FAMILY のときの持ち主の家庭（生徒のアカウントID）。保護者は自分の生徒のIDに解決する。GLOBAL は NULL';
COMMENT ON COLUMN public."RED_書籍情報"."教科" IS
    '2.0 から引き継いだ列（実データは全件 英語）。画面では使わず、言語 を使う';
