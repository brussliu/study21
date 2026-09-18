-- ============================================================================
-- Study 2.1  読書管理 DDL
-- テーブル: RED_語彙辞書情報（語彙・読み方の引き当て結果のキャッシュ）
-- ----------------------------------------------------------------------------
-- 閲覧画面の「語彙」（英語の本）と「読み方」（中国語の本）で、本文から選んだ語の
-- 意味・読みを**自動で引く**ための結果を貯める表。
--
-- 設計のポイント:
--   1. **外部の辞書・翻訳サービスに問い合わせた結果を貯めるだけ**の表。
--      2.0 の英語読書は `api/word/translateByAi`（実体は ExcelAPI / 有道 / Google 翻訳）で
--      都度オンライン翻訳していた。2.1 は同じ発想で引くが、**同じ語を何度も引かない**
--      ようにここへ貯める（オフラインでも一度引いた語は出せる）。
--   2. 見出し語は**正規化して**入れる（前後の空白除去・英字は小文字化・全角空白の除去）。
--      `UNIQUE (言語, 見出し語)` で 1 語 1 行。
--   3. 利用者のデータではないので監査列（登録者アカウントID 等）は持たない。
--      「いつ・どこから引いたか」だけを `取得元コード` / `取得日時` に残す。
--   4. 取得できなかった項目は NULL。行があっても全部 NULL なら「引けたが情報なし」を
--      表す（画面はその旨を出す）。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."RED_語彙辞書情報" (
    "辞書ID"         BIGSERIAL    NOT NULL,
    -- 本の言語（引き当ての向きを決める）: 英語→日英中 / 中国語→拼音と解説
    "言語"           VARCHAR(20)  NOT NULL,
    -- 正規化した見出し語（例 'hello' / '绝对'）
    "見出し語"       VARCHAR(100) NOT NULL,
    -- 日本語での意味（英語の本の「語彙」で使う。ExcelAPI enja）
    "日本語訳"       TEXT         NULL,
    -- 中国語での意味（英語の本の「語彙」で使う。有道）
    "中国語訳"       TEXT         NULL,
    -- ピンイン（中国語の本の「読み方」で使う。有道 phone）
    "ピンイン"       VARCHAR(200) NULL,
    -- 解説（中国語の本の「読み方」で使う。有道の中国語解説・英語対訳など）
    "解説"           TEXT         NULL,
    -- EXCELAPI / YOUDAO / GOOGLE / AI / MANUAL
    "取得元コード"   VARCHAR(50)  NOT NULL DEFAULT 'YOUDAO',
    "取得日時"       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "RED_語彙辞書情報_pkey" PRIMARY KEY ("辞書ID"),
    CONSTRAINT "UK_RED_語彙辞書_言語見出し" UNIQUE ("言語", "見出し語"),
    CONSTRAINT "CK_RED_語彙辞書_言語"
        CHECK ("言語" IN ('中国語', '英語', '日本語')),
    CONSTRAINT "CK_RED_語彙辞書_見出し語"
        CHECK (NULLIF(BTRIM("見出し語"), '') IS NOT NULL)
);

-- 「この語は引いてあるか」を引く（UNIQUE 索引が効くので追加索引は不要）

COMMENT ON TABLE public."RED_語彙辞書情報" IS
    '語彙・読み方の引き当て結果のキャッシュ（閲覧画面の「語彙」「読み方」）。2.0 の api/word/translateByAi 相当';
COMMENT ON COLUMN public."RED_語彙辞書情報"."言語" IS
    '本の言語。英語＝日本語訳と中国語訳を引く / 中国語＝ピンインと解説を引く';
COMMENT ON COLUMN public."RED_語彙辞書情報"."見出し語" IS
    '正規化した語（前後空白除去・英字は小文字）。UNIQUE (言語, 見出し語)';
COMMENT ON COLUMN public."RED_語彙辞書情報"."取得元コード" IS
    'EXCELAPI / YOUDAO / GOOGLE / AI / MANUAL';
