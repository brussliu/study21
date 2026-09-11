-- ============================================================================
-- Study 2.1  資料情報  DDL
-- テーブル: DOC_資料情報
-- ----------------------------------------------------------------------------
-- 2.0 (study2) の DOC_資料情報 を最小変更で 2.1 化したもの。
-- 変更点（2.0 との差分）:
--   ★追加  "フォルダID"        BIGINT NULL, FK → DOC_フォルダ情報 (RESTRICT)
--                              （フォルダ階層への所属。NULL = 未分類）
--   ★追加  CK: ステータス IN ('0','1')     （2.0 は無制約、実データは '1'/'0' のみ）
--   ★追加  登録日時/更新日時 の DEFAULT CURRENT_TIMESTAMP
--                              （新規登録時の省略を許可。既存値への影響なし）
--   ★追加  idx_doc_info_folder 索引        （フォルダ内資料一覧取得用）
--   ※維持  大分類/中分類/小分類/細分類はそのまま残す
--          （一覧ビューの検索条件・旧互換として使用。分類はフォルダ階層そのもので、
--            移行後は フォルダID が正となる）
--          ★分類列はアプリ側でフォルダ階層から導出する
--            （深さ 1=大分類 / 2=中分類 / 3=小分類 / 4=細分類。深さ 5 以上は対応列が無く NULL）
--            ・資料の登録/更新時: 選択フォルダの祖先名称から設定（リクエストでは受け付けない）
--            ・フォルダの名称/階層変更時: 配下資料へ反映（更新日時は変更しない）
--   ※維持  資料番号 (PK) / ステータス('1'有効,'0'無効) / 有効期限('YYYY/MM/DD' 主体、
--          '2027' 等の緩い値も既存データに存在するため VARCHAR(10) のまま) / コメント
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."DOC_資料情報" (
    "資料番号"   VARCHAR(20)  NOT NULL,
    -- 家族共有の所有キー。生徒本人と、その生徒に紐づく保護者だけが同じ値を使用する。
    "家族学生ID" BIGINT       NOT NULL,
    -- ★追加: 所属フォルダ（NULL = 未分類）。フォルダ削除は RESTRICT で保護。
    "フォルダID" BIGINT       NULL,
    -- '1' = 有効 / '0' = 無効（2.0 のコード値を踏襲。UI は API 層で変換）
    "ステータス" VARCHAR(20)  NULL,
    "有効期限"   VARCHAR(10)  NULL,
    "大分類"     VARCHAR(50)  NULL,
    "中分類"     VARCHAR(50)  NULL,
    "小分類"     VARCHAR(50)  NULL,
    "細分類"     VARCHAR(50)  NULL,
    "コメント"   VARCHAR(100) NULL,
    "登録ID"     VARCHAR(20)  NULL,
    "更新ID"     VARCHAR(20)  NULL,
    "登録日時"   TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"   TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "DOC_資料情報_pkey" PRIMARY KEY ("資料番号"),
    CONSTRAINT "FK_DOC_資料家族学生" FOREIGN KEY ("家族学生ID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_DOC_資料_フォルダ" FOREIGN KEY ("フォルダID", "家族学生ID")
        REFERENCES public."DOC_フォルダ情報" ("フォルダID", "家族学生ID") ON DELETE RESTRICT,
    -- ★追加: 2.0 の実データは '1'/'0' のみであることを検証済み
    CONSTRAINT "CK_DOC_資料_ステータス" CHECK ("ステータス" IN ('0', '1'))
);

-- ★追加: フォルダ内の資料一覧取得用索引
CREATE INDEX IF NOT EXISTS idx_doc_info_folder
    ON public."DOC_資料情報" ("家族学生ID", "フォルダID");

CREATE INDEX IF NOT EXISTS idx_doc_info_family_updated
    ON public."DOC_資料情報" ("家族学生ID", "更新日時" DESC);
