-- ============================================================================
-- Study 2.1  授業録音 / AI 授業記録 DDL
-- テーブル: CR_授業転写セグメント情報
-- ----------------------------------------------------------------------------
-- 転写は 1 本の大テキストではなく「セグメント行」で持つ。
-- 理由: タイムスタンプ・話者ラベル・キーワードトリガーの判定対象（追記セグメント）・
-- 画面の逐次表示が自然。全文が必要な再解析（最終まとめ）は連番順に連結して組み立てる。
-- 設計: tmp/classroom-ai-design.md §3.2
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: 何度流しても同じ（CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS）
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."CR_授業転写セグメント情報" (
    "転写セグメントID"  BIGSERIAL    NOT NULL,
    "授業記録ID"        BIGINT       NOT NULL,
    -- 追記の順序（チャンクの連番と同じ。冪等性の判定にも使う）
    "連番"              INTEGER      NOT NULL,
    -- 録音開始からのオフセット（秒。小数点以下 3 桁まで）
    "開始オフセット秒"  NUMERIC(10,3) NULL,
    "終了オフセット秒"  NUMERIC(10,3) NULL,
    -- 話者は**音源**で決める（マイク＝学生／共有の音＝先生／マイクのみ＝講義）
    "話者ラベル"        VARCHAR(20)  NULL,
    -- 発話の安定した識別子（音源#開始ミリ秒＝音声の位置）。同じ発話の再保存は UPDATE にする
    "発話キー"          VARCHAR(160) NULL,
    -- この文を出した音源（mic=マイク / shared=共有した音）。旧データは NULL（＝講義）
    "音源"              VARCHAR(20)  NULL,
    "原文テキスト"      TEXT         NOT NULL,
    -- このセグメントの言語（言語モードから決めた STT 言語コード。記録用）
    "言語"              VARCHAR(20)  NULL,
    "登録日時"          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "CR_授業転写セグメント情報_pkey" PRIMARY KEY ("転写セグメントID"),
    CONSTRAINT "FK_CR_転写セグメント_授業記録" FOREIGN KEY ("授業記録ID")
        REFERENCES public."CR_授業記録情報" ("授業記録ID") ON DELETE CASCADE,
    -- 連番の重複を防ぐ（同じチャンクの再送は同じ連番なので冪等に弾ける）
    CONSTRAINT "UK_CR_転写セグメント_連番" UNIQUE ("授業記録ID", "連番"),
    CONSTRAINT "CK_CR_転写セグメント_連番" CHECK ("連番" >= 1),
    CONSTRAINT "CK_CR_転写セグメント_オフセット" CHECK (
        "開始オフセット秒" IS NULL OR "開始オフセット秒" >= 0
    ),
    CONSTRAINT "CK_CR_転写セグメント_終了オフセット" CHECK (
        "終了オフセット秒" IS NULL OR "開始オフセット秒" IS NULL OR "終了オフセット秒" >= "開始オフセット秒"
    ),
    CONSTRAINT "CK_CR_転写セグメント_テキスト" CHECK (BTRIM("原文テキスト") <> '')
);

-- 追記順の取得（ポーリング・全文連結）
CREATE INDEX IF NOT EXISTS idx_cr_segment_record_seq
    ON public."CR_授業転写セグメント情報" ("授業記録ID", "連番");

-- 同じ発話の再送・再保存を 1 行に寄せる（NULL＝旧データは対象外）
CREATE UNIQUE INDEX IF NOT EXISTS "UK_CR_転写セグメント_発話キー"
    ON public."CR_授業転写セグメント情報" ("授業記録ID", "発話キー")
    WHERE "発話キー" IS NOT NULL;

COMMENT ON TABLE public."CR_授業転写セグメント情報" IS
    '授業の転写（文字起こし）をセグメント行で持つ。1 チャンク = 1 連番（複数セグメントにも対応）。';
COMMENT ON COLUMN public."CR_授業転写セグメント情報"."連番" IS
    '追記の順序（チャンクの連番と同じ）。(授業記録ID, 連番) で一意なので、同じチャンクの再送を冪等に弾ける';
COMMENT ON COLUMN public."CR_授業転写セグメント情報"."話者ラベル" IS
    '話者は音源で決める（マイク＝学生／共有の音＝先生／マイクのみ＝講義）';
COMMENT ON COLUMN public."CR_授業転写セグメント情報"."発話キー" IS
    '発話の安定した識別子（音源#開始ミリ秒＝音声の位置）。同じ発話の再保存は UPDATE にする'
    '（認識セッション番号は使わない＝張り直し・再起動でキーが変わらない）';
