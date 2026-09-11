-- ============================================================================
-- Study 2.1  アカウント  DDL（最終仕様）
-- テーブル: ACC_アカウント   （★2.1 新設テーブル）
-- ----------------------------------------------------------------------------
-- 保護者・生徒・管理者のログインアカウントを一元管理するテーブル。
-- 「アカウント種別」で GUARDIAN（保護者）/ STUDENT（生徒）/ ADMIN（管理者）を区別する。
--
-- このファイルは最終仕様を単体で完結させる（アップグレード用スクリプト不要）。
-- 旧 UPD_ACC_アカウント_ADMIN対応.sql / UPD_ACC_人員権限_20260908.sql の内容は
-- すべて取り込み済み。新規構築はこの DDL だけで最終状態になる。
--
-- 設計ポイント:
--   1. ログインID はメールアドレス（小文字正規化して保存）。
--      大文字小文字を区別せず一意（uq_acc_login_id: LOWER("ログインID")）。
--      全ロール（保護者・生徒・管理者）横断で重複を1本の一意索引で防止する。
--   2. 親子は厳密な1対1（DB で保証）:
--      - CHECK      : 生徒は保護者ID 必須・自己参照禁止。保護者/管理者は NULL。
--      - 生成列     : 保護者種別 = 生徒なら 'GUARDIAN'、それ以外は NULL。
--      - 複合FK     : (保護者ID, 保護者種別) → (アカウントID, アカウント種別)。
--                     生成列により参照先は必ず「保護者」行に限定される
--                     （管理者・生徒を親に指定できない）。
--      - UNIQUE     : uq_acc_guardian_one_student（保護者ID、遅延可能）。
--                     同一保護者が2人目の生徒を持つことを防止。
--      - 遅延トリガ : コミット時に「保護者には生徒が1人以上」を検証。
--                     同一トランザクション内で 保護者→生徒 の順に登録できる。
--   3. 管理者(ADMIN) は 有効期限 / 利用規約同意日時 / ふりがな を NULL にできる
--      （無期限運用）。姓名は必須、学年と保護者ID は NULL。
--   4. 「状態」は既存テーブルのステータス規約に合わせ '1'=有効 / '0'=無効。
--   5. パスワードは平文を保存せず BCrypt ハッシュ（アプリ側で生成）のみ保持。
--   6. 削除は ON DELETE RESTRICT。物理削除は「生徒 → 保護者」の順に同一トランザクションで。
--      通常は状態 '0' による停用を使う。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."ACC_アカウント" (
    "アカウントID"       BIGSERIAL    NOT NULL,
    -- ログインID（メールアドレス。小文字正規化して登録・照合する）
    "ログインID"         VARCHAR(255) NOT NULL,
    -- BCrypt ハッシュ。平文パスワードは保存しない
    "パスワードハッシュ" VARCHAR(255) NOT NULL,
    -- 'GUARDIAN'（保護者）/ 'STUDENT'（生徒）/ 'ADMIN'（管理者）
    "アカウント種別"     VARCHAR(20)  NOT NULL,
    -- 状態: '1'=有効 / '0'=無効（既存ステータス規約に合わせる）
    "状態"               VARCHAR(1)   NOT NULL DEFAULT '1',
    -- 姓名: 生徒・管理者は必須。保護者は未収集のため NULL 可（CK_ACC_姓名必須）
    "姓"                 VARCHAR(100) NULL,
    "名"                 VARCHAR(100) NULL,
    -- ふりがな: 生徒は必須。保護者・管理者は NULL 可（CK_ACC_かな必須）
    "姓かな"             VARCHAR(100) NULL,
    "名かな"             VARCHAR(100) NULL,
    -- 学年: 生徒のみ必須（例: 小学3年生 / 中学1年生 / 高校2年生）。保護者・管理者は NULL
    "学年"               VARCHAR(50)  NULL,
    -- ★保護者ID（生徒→保護者の自己参照。保護者・管理者は NULL）
    "保護者ID"           BIGINT       NULL,
    -- ★生成列: 複合FKで「参照先が保護者であること」を保証する補助キー
    "保護者種別"         VARCHAR(20)  GENERATED ALWAYS AS (
                             CASE WHEN "アカウント種別" = 'STUDENT' THEN 'GUARDIAN' ELSE NULL END
                         ) STORED,
    -- ★有効期限（登録日 + 1ヶ月。期限超過はログイン不可）。管理者は NULL=無期限
    "有効期限"           DATE         NULL,
    -- 利用規約への同意日時（生徒は保護者による代諾時刻を保持）。管理者は NULL 可
    "利用規約同意日時"   TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP,
    "登録ID"             VARCHAR(20)  NULL,
    "更新ID"             VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "ACC_アカウント_pkey" PRIMARY KEY ("アカウントID"),
    -- 複合FKの参照先として (アカウントID, アカウント種別) を一意化
    CONSTRAINT "uq_acc_id_type" UNIQUE ("アカウントID", "アカウント種別"),
    -- 1保護者につき生徒は1人。一括の紐付け替えは SET CONSTRAINTS ... DEFERRED を使う
    CONSTRAINT "uq_acc_guardian_one_student" UNIQUE ("保護者ID")
        DEFERRABLE INITIALLY IMMEDIATE,
    -- 参照先は「保護者」行のみ。ON UPDATE は既定(NO ACTION)、ON DELETE は RESTRICT
    CONSTRAINT "FK_ACC_生徒保護者" FOREIGN KEY ("保護者ID", "保護者種別")
        REFERENCES public."ACC_アカウント" ("アカウントID", "アカウント種別") ON DELETE RESTRICT,
    CONSTRAINT "CK_ACC_種別" CHECK ("アカウント種別" IN ('GUARDIAN', 'STUDENT', 'ADMIN')),
    CONSTRAINT "CK_ACC_状態" CHECK ("状態" IN ('0', '1')),
    -- 保護者ID: 生徒は必須かつ自己参照禁止。保護者・管理者は必ず NULL
    CONSTRAINT "CK_ACC_保護者ID種別" CHECK (
        ("アカウント種別" = 'STUDENT' AND "保護者ID" IS NOT NULL AND "保護者ID" <> "アカウントID")
        OR ("アカウント種別" IN ('GUARDIAN', 'ADMIN') AND "保護者ID" IS NULL)
    ),
    -- 学年: 生徒は必須、保護者・管理者は NULL
    CONSTRAINT "CK_ACC_学年種別" CHECK (
        ("アカウント種別" = 'STUDENT' AND "学年" IS NOT NULL)
        OR ("アカウント種別" IN ('GUARDIAN', 'ADMIN') AND "学年" IS NULL)
    ),
    -- 姓名: 保護者のみ NULL 可（未収集）。生徒・管理者は必須
    CONSTRAINT "CK_ACC_姓名必須" CHECK (
        "アカウント種別" = 'GUARDIAN' OR ("姓" IS NOT NULL AND "名" IS NOT NULL)
    ),
    -- ふりがな: 生徒は必須。保護者・管理者は NULL 可
    CONSTRAINT "CK_ACC_かな必須" CHECK (
        "アカウント種別" <> 'STUDENT' OR ("姓かな" IS NOT NULL AND "名かな" IS NOT NULL)
    ),
    -- 有効期限: 管理者以外は必須
    CONSTRAINT "CK_ACC_有効期限" CHECK (
        "アカウント種別" = 'ADMIN' OR "有効期限" IS NOT NULL
    ),
    -- 利用規約同意日時: 管理者以外は必須
    CONSTRAINT "CK_ACC_規約同意" CHECK (
        "アカウント種別" = 'ADMIN' OR "利用規約同意日時" IS NOT NULL
    )
);

-- ----------------------------------------------------------------------------
-- 遅延制約トリガ: コミット時に「保護者には生徒が1人以上いる」ことを検証する。
-- 遅延させることで、同一トランザクション内で 保護者 → 生徒 の順に登録できる。
-- 違反時は CHECK 違反(23514)として通知する。
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.fn_acc_guardian_requires_student() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    -- (1) 保護者行が（新規に/引き続き）存在するなら、その保護者には生徒が必要
    IF TG_OP <> 'DELETE' AND NEW."アカウント種別" = 'GUARDIAN' THEN
        IF NOT EXISTS (
            SELECT 1 FROM public."ACC_アカウント" WHERE "保護者ID" = NEW."アカウントID"
        ) THEN
            RAISE EXCEPTION '保護者 % に生徒が紐づいていません。', NEW."アカウントID"
                USING ERRCODE = '23514';
        END IF;
    END IF;

    -- (2) 生徒の紐付けが外れた/行が消えた場合、元の保護者が残っていれば生徒が必要
    IF TG_OP <> 'INSERT' AND OLD."アカウント種別" = 'STUDENT' AND OLD."保護者ID" IS NOT NULL THEN
        IF EXISTS (
                SELECT 1 FROM public."ACC_アカウント"
                 WHERE "アカウントID" = OLD."保護者ID" AND "アカウント種別" = 'GUARDIAN')
           AND NOT EXISTS (
                SELECT 1 FROM public."ACC_アカウント" WHERE "保護者ID" = OLD."保護者ID") THEN
            RAISE EXCEPTION '保護者 % に生徒が紐づいていません。', OLD."保護者ID"
                USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NULL;
END $$;

DROP TRIGGER IF EXISTS trg_acc_guardian_requires_student ON public."ACC_アカウント";
CREATE CONSTRAINT TRIGGER trg_acc_guardian_requires_student
    AFTER INSERT OR UPDATE OR DELETE ON public."ACC_アカウント"
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION public.fn_acc_guardian_requires_student();

-- ログインID の全ロール横断一意性（大文字小文字を区別しない）
CREATE UNIQUE INDEX IF NOT EXISTS uq_acc_login_id
    ON public."ACC_アカウント" (LOWER("ログインID"));

-- 生徒→保護者の一覧・逆引き用
CREATE INDEX IF NOT EXISTS idx_acc_guardian
    ON public."ACC_アカウント" ("保護者ID");

-- ログイン照合用（メール検索 + 有効/無効フィルタ）
CREATE INDEX IF NOT EXISTS idx_acc_login_lookup
    ON public."ACC_アカウント" (LOWER("ログインID"), "状態");
