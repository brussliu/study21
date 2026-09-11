-- ============================================================================
-- Study 2.1  資料フォルダ  DDL
-- テーブル: DOC_フォルダ情報   （★2.1 新設テーブル）
-- ----------------------------------------------------------------------------
-- 資料管理フォルダビュー用のフォルダ階層（自己参照ツリー構造）。
-- 親フォルダID = NULL はルート直下のフォルダを表す。
-- ★階層は 4 まで（ルート直下=1 が 大分類、2=中分類、3=小分類、4=細分類）。
--   分類列（DOC_資料情報）が 4 列しか無いため、5 階層目は作成できない。
--   深すぎるフォルダは fn_doc_folder_check_depth() トリガで拒否する。
-- 2.0 には存在しないテーブル。既存 DOC_資料情報 の 大分類〜細分類 から
-- 移行スクリプト（MIG_DOC_資料管理_20260831.sql）でツリーを生成する。
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."DOC_フォルダ情報" (
    "フォルダID"    BIGSERIAL    PRIMARY KEY,
    -- 家族共有の所有キー。生徒本人IDを家族IDとして使用し、保護者は紐づく生徒IDへ解決する。
    "家族学生ID"    BIGINT       NOT NULL,
    -- ★親フォルダID（NULL = ルート直下）。親子階層を表す中核カラム。
    "親フォルダID"  BIGINT       NULL,
    "フォルダ名称"  VARCHAR(100) NOT NULL,
    -- ★同一親フォルダ内の並び順（先頭の "01." 等の番号から移行時に採番）
    "表示順"        INTEGER      NOT NULL DEFAULT 0,
    "備考"          VARCHAR(200) NULL,
    "登録ID"        VARCHAR(20)  NULL,
    "更新ID"        VARCHAR(20)  NULL,
    "登録日時"      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 子フォルダを持つフォルダは削除不可（削除前に内容物の移動が必要）
    CONSTRAINT "FK_DOC_フォルダ家族学生" FOREIGN KEY ("家族学生ID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "UK_DOC_フォルダID家族" UNIQUE ("フォルダID", "家族学生ID"),
    -- 親子フォルダが別家族を跨がないよう複合FKで保証する。
    CONSTRAINT "FK_DOC_フォルダ親" FOREIGN KEY ("親フォルダID", "家族学生ID")
        REFERENCES public."DOC_フォルダ情報" ("フォルダID", "家族学生ID") ON DELETE RESTRICT,
    -- 自分自身を親にすることはできない
    CONSTRAINT "CK_DOC_フォルダ自己参照禁止"
        CHECK ("親フォルダID" IS NULL OR "親フォルダID" <> "フォルダID")
);

-- 同一親フォルダ内での名称重複を防止（親NULL=ルート含む）
CREATE UNIQUE INDEX IF NOT EXISTS uq_doc_folder_name_in_parent
    ON public."DOC_フォルダ情報" ("家族学生ID", COALESCE("親フォルダID", 0), "フォルダ名称");

-- 子フォルダ列挙用索引
CREATE INDEX IF NOT EXISTS idx_doc_folder_parent
    ON public."DOC_フォルダ情報" ("家族学生ID", "親フォルダID");

-- ============================================================================
-- ★追加: 4階層制限（ルート直下=1 … 4=細分類）を DB 側でも保証する。
--   挿入時は親の深さを、親変更時は「新しい親の深さ + 自身の配下の高さ」を検査する。
--   アプリ側（DocumentService）でも同じ制限を検証しているが、
--   直接 SQL で更新しても階層が壊れないようにトリガで担保する。
-- ============================================================================
CREATE OR REPLACE FUNCTION public.fn_doc_folder_check_depth() RETURNS trigger AS $$
DECLARE
    parent_id      BIGINT;
    parent_depth   INT := 0;
    subtree_height INT := 0;
BEGIN
    -- 新しい親の深さ（ルート直下 = 1）
    parent_id := NEW."親フォルダID";
    WHILE parent_id IS NOT NULL LOOP
        parent_depth := parent_depth + 1;
        IF parent_depth >= 4 THEN
            RAISE EXCEPTION 'フォルダは大分類〜細分類の4階層までです。' USING ERRCODE = '23514';
        END IF;
        SELECT f."親フォルダID" INTO parent_id
          FROM public."DOC_フォルダ情報" f
         WHERE f."フォルダID" = parent_id;
    END LOOP;

    -- 自身を 0 とした配下の高さ（親変更で配下ごと深くなるため）
    WITH RECURSIVE subtree AS (
        SELECT NEW."フォルダID" AS folder_id, 0 AS height
        UNION ALL
        SELECT child."フォルダID", parent.height + 1
          FROM public."DOC_フォルダ情報" child
          JOIN subtree parent ON child."親フォルダID" = parent.folder_id
    )
    SELECT COALESCE(MAX(height), 0) INTO subtree_height FROM subtree;

    IF parent_depth + 1 + subtree_height > 4 THEN
        RAISE EXCEPTION 'フォルダは大分類〜細分類の4階層までです。' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_doc_folder_check_depth ON public."DOC_フォルダ情報";
CREATE TRIGGER trg_doc_folder_check_depth
    BEFORE INSERT OR UPDATE OF "親フォルダID" ON public."DOC_フォルダ情報"
    FOR EACH ROW EXECUTE FUNCTION public.fn_doc_folder_check_depth();
