-- ============================================================================
-- Study 2.1  テスト情報  DDL
-- テーブル: TST_テスト情報
-- ----------------------------------------------------------------------------
-- 2.0 の TRN_テスト情報を、2.1 の家族所有権モデルに合わせて再設計する。
-- 1行は「1人の生徒の1科目のテスト」を表す。
--
-- 2.0 からの主な変更点:
--   ★追加  家族学生ID: 生徒本人とその保護者が共有する所有キー
--   ★追加  テストID: 内部参照用の不変キー。表示用のテスト番号と分離
--   ★変更  テスト番号は家族内一意のビジネスキー
--   ★追加  得点・満点の整合性 CHECK（0/0 を「未入力」に使わず NULL/NULL で表現）
--   ★追加  バージョン: 生徒と保護者の同時編集による上書きを防止
--   ★廃止  内容SEQ: 2.0 実データ97件で全件 NULL、機能上も未使用
--
-- アクセス制御:
--   - STUDENT は自分のアカウントIDを家族学生IDとして使用する。
--   - GUARDIAN は ACC_アカウント.保護者ID の逆引きで対応生徒IDへ解決する。
--   - すべての SELECT/UPDATE/DELETE で家族学生IDを条件に含める。
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."TST_テスト情報" (
    "テストID"     BIGSERIAL    NOT NULL,
    "家族学生ID"   BIGINT       NOT NULL,
    "テスト番号"   VARCHAR(30)  NOT NULL,
    "テスト名称"   VARCHAR(120) NOT NULL,
    "教科"           VARCHAR(20)  NOT NULL,
    "区分"           VARCHAR(20)  NOT NULL,
    "試験日"       DATE         NULL,
    -- 未入力は得点数・満点数の両方を NULL とする。
    "得点数"       INTEGER      NULL,
    "満点数"       INTEGER      NULL,
    "詳細メモ"     TEXT         NULL,
    -- 更新時は WHERE バージョン = :beforeVersion で照合し、1加算する。
    "バージョン"     INTEGER      NOT NULL DEFAULT 1,
    "登録ID"         VARCHAR(20)  NULL,
    "更新ID"         VARCHAR(20)  NULL,
    "登録日時"       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "TST_テスト情報_pkey" PRIMARY KEY ("テストID"),
    CONSTRAINT "FK_TST_テスト_家族学生" FOREIGN KEY ("家族学生ID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "UK_TST_テスト_家族番号" UNIQUE ("家族学生ID", "テスト番号"),
    CONSTRAINT "CK_TST_テスト_番号" CHECK (BTRIM("テスト番号") <> ''),
    CONSTRAINT "CK_TST_テスト_名称" CHECK (BTRIM("テスト名称") <> ''),
    CONSTRAINT "CK_TST_テスト_教科" CHECK (BTRIM("教科") <> ''),
    CONSTRAINT "CK_TST_テスト_区分" CHECK (BTRIM("区分") <> ''),
    CONSTRAINT "CK_TST_テスト_得点" CHECK (
        ("得点数" IS NULL AND "満点数" IS NULL)
        OR
        ("得点数" IS NOT NULL AND "満点数" IS NOT NULL
         AND "得点数" >= 0 AND "満点数" > 0 AND "得点数" <= "満点数")
    ),
    CONSTRAINT "CK_TST_テスト_バージョン" CHECK ("バージョン" > 0)
);

-- 家族別の一覧、教科・区分検索、試験日降順をまとめてカバーする。
CREATE INDEX IF NOT EXISTS idx_tst_test_family_search
    ON public."TST_テスト情報" ("家族学生ID", "教科", "区分", "試験日" DESC, "テストID" DESC);

-- 検索条件に教科・区分を指定しない「最新テスト」取得用。
CREATE INDEX IF NOT EXISTS idx_tst_test_family_exam_date
    ON public."TST_テスト情報" ("家族学生ID", "試験日" DESC, "テストID" DESC);

COMMENT ON TABLE public."TST_テスト情報" IS '生徒単位で保護者と共有するテスト基本情報';
COMMENT ON COLUMN public."TST_テスト情報"."家族学生ID" IS '所有者である生徒アカウントID。対応保護者と共有';
COMMENT ON COLUMN public."TST_テスト情報"."テスト番号" IS '画面表示・2.0移行用の家族内一意番号';
COMMENT ON COLUMN public."TST_テスト情報"."バージョン" IS '楽観的ロック用。更新成功時に1加算';
