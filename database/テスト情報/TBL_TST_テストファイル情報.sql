-- ============================================================================
-- Study 2.1  テストファイル情報  DDL
-- テーブル: TST_テストファイル情報
-- ----------------------------------------------------------------------------
-- 2.0 で COM_ファイル情報 (機能区分='TESTINFO') に保存していた
-- 試験用紙ファイルを、テスト集約専用の子テーブルとして分離する。
-- 汎用の機能区分/機能番号ではなく、テストIDへの実FKを持つ。
--
-- 設計ポイント:
--   - 親テストを削除するとファイルメタデータも CASCADE 削除。
--     物理ファイルはサービス層がトランザクション成功後に削除する。
--   - 縮略ファイル列は2.0の表示品質とデータをそのまま移行できるよう維持。
--   - ファイルサイズ/SHA-256は新規アップロードと移行検証に使用。
--   - 1テスト最大10件の制限は、複数ファイル同時登録を行うサービス層で
--     テスト行を FOR UPDATE ロックして検査する。
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."TST_テストファイル情報" (
    "テストファイルID" BIGSERIAL    NOT NULL,
    "テストID"         BIGINT       NOT NULL,
    "表示順"             INTEGER      NOT NULL DEFAULT 0,
    "元ファイル名称"     VARCHAR(255) NOT NULL,
    "ファイル名称"       VARCHAR(255) NOT NULL,
    "拡張子"             VARCHAR(20)  NULL,
    "MIME_TYPE"          VARCHAR(120) NULL,
    "ファイルサイズ"     BIGINT       NULL,
    "SHA256"             CHAR(64)     NULL,
    -- ストレージルートからの相対ディレクトリを保存する。
    "パス"               VARCHAR(500) NOT NULL,
    "縮略ファイル500"    TEXT         NULL,
    "縮略ファイル200"    TEXT         NULL,
    "縮略ファイル50"     TEXT         NULL,
    "コメント"           TEXT         NULL,
    "登録ID"             VARCHAR(20)  NULL,
    "更新ID"             VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "TST_テストファイル情報_pkey" PRIMARY KEY ("テストファイルID"),
    CONSTRAINT "FK_TST_テストファイル_テスト" FOREIGN KEY ("テストID")
        REFERENCES public."TST_テスト情報" ("テストID") ON DELETE CASCADE,
    CONSTRAINT "CK_TST_テストファイル_表示順" CHECK ("表示順" >= 0),
    CONSTRAINT "CK_TST_テストファイル_元名称" CHECK (BTRIM("元ファイル名称") <> ''),
    CONSTRAINT "CK_TST_テストファイル_保存名称" CHECK (BTRIM("ファイル名称") <> ''),
    CONSTRAINT "CK_TST_テストファイル_パス" CHECK (BTRIM("パス") <> ''),
    CONSTRAINT "CK_TST_テストファイル_サイズ" CHECK ("ファイルサイズ" IS NULL OR "ファイルサイズ" >= 0),
    CONSTRAINT "CK_TST_テストファイル_SHA256" CHECK (
        "SHA256" IS NULL OR "SHA256" ~ '^[0-9a-f]{64}$'
    )
);

-- 一覧の表示順は同値を許容し、IDで安定ソートする。
-- これにより並び替え時に一時的な重複値を使える。
CREATE INDEX IF NOT EXISTS idx_tst_test_file_order
    ON public."TST_テストファイル情報" ("テストID", "表示順", "テストファイルID");

COMMENT ON TABLE public."TST_テストファイル情報" IS 'テスト情報に添付する試験用紙ファイル';
COMMENT ON COLUMN public."TST_テストファイル情報"."パス" IS 'ストレージルートからの相対ディレクトリ';
COMMENT ON COLUMN public."TST_テストファイル情報"."SHA256" IS '物理ファイルの移行・整合性検証用SHA-256（小文字hex）';
