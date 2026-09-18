-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_単語詳細情報（AI が取得した語の詳細）
-- ----------------------------------------------------------------------------
-- 2.0 の `STY_日本語単語詳細情報`（実データ 383 件。移行元DB は study3。2026-09-13 実測）
-- と、その 6 つの子テーブルを 1 テーブルに集約する。
--
--   2.0 の子テーブル（実データ件数）       → 2.1 の 詳細JSON のキー
--   STY_日本語単語詳細_語義情報        653 → senses
--   STY_日本語単語詳細_例文情報      1,011 → examples
--   STY_日本語単語詳細_発音情報        384 → pronunciations
--   STY_日本語単語詳細_コロケーション情報 926 → collocations
--   STY_日本語単語詳細_関連語情報      676 → relatedWords
--   STY_日本語単語詳細_使用注意情報    485 → cautions
--
-- **詳細を JSONB に集約した理由**
--   2.0 は 6 つの子テーブル（語義・例文・発音・コロケーション・関連語・使用注意）を
--   正規化して持っていたが、2.1 ではこの詳細は **AI が生成した読み取り専用の参考情報**で、
--   検索・集計・絞り込みの対象ではない（画面は 1 語ぶんをまとめて表示するだけ）。
--   子テーブルに分けると 6 テーブルぶんの JOIN と並び替えが毎回必要になる一方、
--   正規化の利点（部分更新・部分検索）は使わない。よって JSONB 1 列に集約した。
--   1 語 1 詳細（uq_jpn_detail_word）なので、行が増えても 1 行 1 語のままである。
--   中身を検索したくなったときは idx_jpn_detail_json（GIN）でキー・値を引ける。
--
-- **詳細JSON の構造（本体の列 ＋ 6 子テーブルを 1 つの JSONB に集約）**
--   トップレベル（2.0 の 詳細情報 本体の列。構造化JSON のルート直下の項目）:
--     jlptLevel                  JLPTレベル
--     partOfSpeech               品詞
--     conjugation                活用種類
--     transitivity               自他区分
--     importance                 重要度
--     chineseMeaning             代表中国語意味
--     descriptionJa              日本語説明
--     descriptionZh              中国語説明
--     structuredSchemaVersion    構造化スキーマ版
--     manuallyCorrected          手動修正済フラグ
--     structured                 構造化JSON（AI の生の構造化結果をそのまま）
--   配列（2.0 の 6 子テーブル）:
--   senses         [{number, japanese, chinese, context, style, noteJapanese, noteChinese, displayOrder}]
--   examples       [{japanese, reading, chinese, contextJapanese, contextChinese, source, senseNumber, displayOrder}]
--   pronunciations [{reading, accentNotation, accentType, moraCount, audioUrl, audioProvider, displayOrder}]
--   collocations   [{expression, reading, chinese, exampleJapanese, exampleChinese, displayOrder}]
--   relatedWords   [{relatedWordId, relationType, heading, reading, chinese, differenceJapanese, differenceChinese, eCandidate, displayOrder}]
--   cautions       [{noteType, japanese, chinese, wrongExample, correctExample, displayOrder}]
--   各要素は 2.0 の列名を camelCase にしたもの。配列は 2.0 の 表示順 の昇順。
--   `relatedWordId` だけは 2.0 の 関連先日本語単語ID を 2.1 の 単語ID に読み替える
--   （2.0 の実データは全件 NULL。将来の詳細再取得で入る想定）。
--
-- 2.0 からの主な変更:
--   1. 主キー 日本語単語詳細ID → 詳細ID（BIGSERIAL）。2.0 の ID は 旧詳細ID に残す。
--   2. **詳細情報 本体の列 ＋ 6 子テーブル → 詳細JSON 1 列**（上記の構造）。
--   3. 2.0 の 状態（全件 'AI_GENERATED'）は列を持たない（常に AI 生成の参考情報のため）。
--      反映結果ID / 確認ユーザーID / 確認日時（全件 NULL。詳細の確認画面を持たない）も引き継がない。
--   4. 取得日時 は 2.0 の 更新日時（詳細の最終更新）を入れる。NULL なら 登録日時 → CURRENT_TIMESTAMP。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_単語詳細情報" (
    "詳細ID"             BIGSERIAL    NOT NULL,
    -- 2.0 の 日本語単語詳細ID。冪等な移行（ON CONFLICT）と突き合わせの根拠として残す
    "旧詳細ID"           BIGINT       NULL,
    "単語ID"             BIGINT       NOT NULL,
    -- 2.0 の 内容版数（実データは 1 が 370 件・2 が 13 件）
    "内容版数"           INTEGER      NOT NULL DEFAULT 1,
    -- 2.0 の 詳細情報 本体の列 ＋ 6 子テーブルを集約した読み取り専用の参考情報（上記の構造）
    "詳細JSON"           JSONB        NOT NULL DEFAULT '{}',
    "AIプロバイダ"       VARCHAR(40)  NULL,
    "AIモデル"           VARCHAR(120) NULL,
    -- この詳細を AI から取得した日時
    "取得日時"           TIMESTAMP    NULL,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_単語詳細情報_pkey" PRIMARY KEY ("詳細ID"),
    CONSTRAINT "FK_JPN_単語詳細_単語"
        FOREIGN KEY ("単語ID")
        REFERENCES public."JPN_単語情報" ("単語ID") ON DELETE CASCADE,
    CONSTRAINT "FK_JPN_単語詳細_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_単語詳細_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_単語詳細_内容版数"
        CHECK ("内容版数" >= 1),
    CONSTRAINT "CK_JPN_単語詳細_詳細JSON"
        CHECK (jsonb_typeof("詳細JSON") = 'object')
);

-- 1 語 1 詳細（AI 詳細は語ごとに 1 つ。再取得は上書き）
CREATE UNIQUE INDEX IF NOT EXISTS uq_jpn_detail_word
    ON public."JPN_単語詳細情報" ("単語ID");

-- 2.0 の 日本語単語詳細ID。移行の再実行を冪等にするための一意索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_jpn_detail_old_id
    ON public."JPN_単語詳細情報" ("旧詳細ID");

-- 詳細JSON の中をキー・値で引く（語義・例文などを横断で探すとき）
CREATE INDEX IF NOT EXISTS idx_jpn_detail_json
    ON public."JPN_単語詳細情報" USING gin ("詳細JSON");

COMMENT ON TABLE public."JPN_単語詳細情報" IS
    'AI が取得した日本語単語の詳細（読み取り専用の参考情報）。2.0 の STY_日本語単語詳細情報（383 件）＋ 6 子テーブル';
COMMENT ON COLUMN public."JPN_単語詳細情報"."旧詳細ID" IS
    '2.0 の 日本語単語詳細ID。移行の冪等性と突き合わせに使う';
COMMENT ON COLUMN public."JPN_単語詳細情報"."詳細JSON" IS
    '2.0 の 詳細情報 本体の列（jlptLevel / partOfSpeech / conjugation / transitivity / importance / chineseMeaning / descriptionJa / descriptionZh / structuredSchemaVersion / manuallyCorrected / structured）＋ 6 子テーブル（senses / examples / pronunciations / collocations / relatedWords / cautions）を JSONB に集約した読み取り専用の参考情報（検索対象ではない）';
COMMENT ON COLUMN public."JPN_単語詳細情報"."取得日時" IS
    '2.0 の 更新日時（AI 詳細の最終更新）を引き継ぐ';
COMMENT ON COLUMN public."JPN_単語詳細情報"."登録元コード" IS
    'APP=画面からの登録 / MIGRATION=2.0 からの移行';
