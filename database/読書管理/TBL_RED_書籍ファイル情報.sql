-- ============================================================================
-- Study 2.1  読書管理 DDL
-- テーブル: RED_書籍ファイル情報（書籍の本文 PDF と表紙画像）
-- ----------------------------------------------------------------------------
-- 2.0 は本文 PDF と表紙画像を `COM_ファイル情報`（機能区分='ENGLISH_READING'、
-- ファイル区分='PDF' / 'COVER'）に入れ、実体を `webapps/file/ENGLISH_READING/…` に
-- 置いていた。2.1 では**モジュールごとに自分のファイル表を持つ**方針（資料管理の
-- `DOC_資料詳細情報` と同じ）で、書籍 1 冊 = PDF 1 件 + 表紙 1 件に限定した表を作る。
--
-- 設計のポイント:
--   1. `UNIQUE (書籍ID, ファイル区分)` で 1 冊 1 PDF・1 表紙。差し替えは同じ行の
--      更新（登録元コード・登録日時は残し、更新元/更新日時を進める）。
--   2. `保存パス` は**相対パス**。2.1 が保存したものは `books/<書籍番号>/`、
--      2.0 から引き継いだメタ情報は `file/ENGLISH_READING/<yyyyMM>/`（読取専用）。
--      実体の場所はストレージの設定（`study21.reading.storage-root` /
--      `study21.reading.legacy-storage-root`）が決める。パスを DB に絶対パスで
--      持つと、配備先（コンテナ）で動かなくなるため。
--   3. **実体ファイルは無くても行を作る**（2.0 のメタ情報を捨てないため）。
--      表示するときはストレージに実体があるかを確認し、無ければ画面に
--      「PDF 未登録」を出す（`GET /books/{id}/pdf` は 404）。
--   4. ファイルサイズ・ページ数は分かるときだけ入れる（2.0 の COM_ファイル情報 には
--      サイズ列が無いので、移行した行は NULL）。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."RED_書籍ファイル情報" (
    "ファイルID"         BIGSERIAL    NOT NULL,
    "書籍ID"             BIGINT       NOT NULL,
    -- PDF=本文（閲覧画面で pdf.js が表示する）/ COVER=本棚に出す表紙画像
    "ファイル区分"       VARCHAR(20)  NOT NULL,
    -- 利用者が選んだ元のファイル名（例 '01.Harry Potter and the Sorcerers Stone.pdf'）
    "元ファイル名称"     VARCHAR(255) NULL,
    -- ストレージ上のファイル名（2.1: '<書籍番号>-<uuid>.pdf' / 2.0: '20260402-124745-…pdf'）
    "保存ファイル名"     VARCHAR(255) NOT NULL,
    -- ストレージのルートからの相対ディレクトリ（末尾は '/'。例 'books/ER-20260402-124745/'）
    "保存パス"           VARCHAR(255) NOT NULL,
    "MIME_TYPE"          VARCHAR(100) NULL,
    "ファイルサイズ"     BIGINT       NULL,
    -- PDF の総ページ数（アップロード時に画面が pdf.js で数えて送る。不明は NULL）
    "ページ数"           INTEGER      NULL,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    -- APP=画面からの登録 / MIGRATION=2.0 からの移行
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "RED_書籍ファイル情報_pkey" PRIMARY KEY ("ファイルID"),
    -- 1 冊に PDF 1 件・表紙 1 件
    CONSTRAINT "UK_RED_書籍ファイル_区分" UNIQUE ("書籍ID", "ファイル区分"),
    -- 書籍を消したらファイルの管理情報も消える（実体はサービスが先に消す）
    CONSTRAINT "FK_RED_書籍ファイル_書籍"
        FOREIGN KEY ("書籍ID")
        REFERENCES public."RED_書籍情報" ("書籍ID") ON DELETE CASCADE,
    CONSTRAINT "FK_RED_書籍ファイル_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_RED_書籍ファイル_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_RED_書籍ファイル_区分"
        CHECK ("ファイル区分" IN ('PDF', 'COVER')),
    CONSTRAINT "CK_RED_書籍ファイル_サイズ"
        CHECK ("ファイルサイズ" IS NULL OR "ファイルサイズ" >= 0),
    CONSTRAINT "CK_RED_書籍ファイル_ページ数"
        CHECK ("ページ数" IS NULL OR "ページ数" >= 1),
    CONSTRAINT "CK_RED_書籍ファイル_保存ファイル名"
        CHECK (NULLIF(BTRIM("保存ファイル名"), '') IS NOT NULL)
);

-- 本棚・閲覧画面は「この本の PDF / 表紙」を引く（UNIQUE 索引が先頭に効くので追加索引は不要）
CREATE INDEX IF NOT EXISTS idx_red_book_file_kind
    ON public."RED_書籍ファイル情報" ("ファイル区分", "書籍ID");

COMMENT ON TABLE public."RED_書籍ファイル情報" IS
    '書籍の本文 PDF と表紙画像の管理情報。2.0 の COM_ファイル情報（機能区分 ENGLISH_READING）';
COMMENT ON COLUMN public."RED_書籍ファイル情報"."ファイル区分" IS
    'PDF=本文（閲覧画面で表示）/ COVER=表紙画像。1 冊 1 件ずつ';
COMMENT ON COLUMN public."RED_書籍ファイル情報"."保存ファイル名" IS
    'ストレージ上のファイル名。パストラバーサル防止のため英数字と . - _ のみ';
COMMENT ON COLUMN public."RED_書籍ファイル情報"."保存パス" IS
    'ストレージルートからの相対ディレクトリ（末尾 /）。2.1 は books/<書籍番号>/、2.0 は file/ENGLISH_READING/<yyyyMM>/（読取専用）';
COMMENT ON COLUMN public."RED_書籍ファイル情報"."ページ数" IS
    'PDF の総ページ数。アップロード時に画面（pdf.js）が数えて送る。不明は NULL';
COMMENT ON COLUMN public."RED_書籍ファイル情報"."登録元コード" IS
    'APP=画面からの登録 / MIGRATION=2.0 からの移行';
