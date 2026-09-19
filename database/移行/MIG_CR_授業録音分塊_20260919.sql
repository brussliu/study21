-- ============================================================================
-- 授業録音: 分塊を独立した表（CR_授業録音分塊情報）で持つ
-- ----------------------------------------------------------------------------
-- 背景（2026-09-19 改修）:
--   分塊の重複判定に**転写セグメントの最大連番**（CR_授業転写セグメント情報.連番）を
--   流用していた。連番の意味が違うため、実際に次の 3 つが起きていた:
--     1. 転写が分塊より多く出た回（例: 転写 8 文・分塊 3 個）… 3 個目の分塊が「重複」と
--        誤判定され、**音声が捨てられる**（後から作り直せない）。
--     2. 転写が 1 件も出なかった回（無音の授業など）… 最大連番が 0 のままで、画面を
--        開き直して同じ分塊を送ると**二重に追記される**（同じ音が 2 回鳴る）。
--     3. 画面が「次に送る分塊の連番」を転写の連番から作れない（続きから録れない）。
--   さらに、再生用の 1 本へ**到着順に追記**していたため、「ファイル追記は成功したが
--   DB のトランザクションが失敗した」再送で同じ音が二重に入り得た。
--
-- この移行でやること:
--   * CR_授業録音分塊情報 を作る（1 分塊 = 1 行 1 ファイル。DDL は
--     database/授業録音/TBL_CR_授業録音分塊情報.sql と同じもの）。
--   * これ以降の録音は、分塊を `chunk-{連番}.webm` として保存し、再生用の 1 本は
--     再生のときに**連番順に組立てる**（2 本目以降のコンテナヘッダは落とす）。
--
-- 既存データについて（**移行しない**）:
--   すでに録音済みの記録は 1 本のファイルへ追記済みで、分塊ごとのファイルを持たない。
--   分塊の行を作らないので、再生は今までどおり `CR_授業記録情報.録音ファイル保存先 /
--   保存ファイル名` の 1 本をそのまま配信する（`ClassroomRecordingStorage` 側も、
--   分塊のファイルが 1 つも無ければ組立てをしない）。**消さない・作り直さない**
--   （音声は後から作り直せないため）。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: 何度流しても同じ（IF NOT EXISTS / COMMENT は同じ値で上書き）
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."CR_授業録音分塊情報" (
    "録音分塊ID"        BIGSERIAL      NOT NULL,
    "授業記録ID"        BIGINT         NOT NULL,
    "分塊連番"          INTEGER        NOT NULL,
    "開始オフセット秒"  NUMERIC(10,3)  NULL,
    "終了オフセット秒"  NUMERIC(10,3)  NULL,
    "バイト数"          BIGINT         NOT NULL,
    "チェックサム"      VARCHAR(64)    NOT NULL,
    "保存先"            VARCHAR(255)   NOT NULL,
    "保存ファイル名"    VARCHAR(255)   NOT NULL,
    "MIME"              VARCHAR(100)   NOT NULL DEFAULT 'audio/webm',
    "コンテナ先頭"      BOOLEAN        NOT NULL DEFAULT FALSE,
    "処理状態"          VARCHAR(20)    NOT NULL DEFAULT 'STORED',
    "転写セグメント数"  INTEGER        NOT NULL DEFAULT 0,
    "登録日時"          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "CR_授業録音分塊情報_pkey" PRIMARY KEY ("録音分塊ID"),
    CONSTRAINT "FK_CR_授業録音分塊_授業記録" FOREIGN KEY ("授業記録ID")
        REFERENCES public."CR_授業記録情報" ("授業記録ID") ON DELETE CASCADE,
    CONSTRAINT "UK_CR_授業録音分塊_連番" UNIQUE ("授業記録ID", "分塊連番"),
    CONSTRAINT "CK_CR_授業録音分塊_連番" CHECK ("分塊連番" >= 1),
    CONSTRAINT "CK_CR_授業録音分塊_バイト数" CHECK ("バイト数" > 0),
    CONSTRAINT "CK_CR_授業録音分塊_開始オフセット" CHECK (
        "開始オフセット秒" IS NULL OR "開始オフセット秒" >= 0
    ),
    CONSTRAINT "CK_CR_授業録音分塊_終了オフセット" CHECK (
        "終了オフセット秒" IS NULL OR "開始オフセット秒" IS NULL
            OR "終了オフセット秒" >= "開始オフセット秒"
    ),
    CONSTRAINT "CK_CR_授業録音分塊_処理状態" CHECK (
        "処理状態" IN ('STORED', 'TRANSCRIBED', 'SKIPPED')
    )
);

CREATE INDEX IF NOT EXISTS idx_cr_recording_chunk_record_seq
    ON public."CR_授業録音分塊情報" ("授業記録ID", "分塊連番");

COMMENT ON TABLE public."CR_授業録音分塊情報" IS
    '授業録音の分塊（MediaRecorder の 1 塊 = 1 行 1 ファイル）。(授業記録ID, 分塊連番) で一意';
COMMENT ON COLUMN public."CR_授業録音分塊情報"."分塊連番" IS
    '画面が送る seq。同じ連番の再送は中身（バイト数・チェックサム）が同じなら保存済みの結果を返す（冪等）';
COMMENT ON COLUMN public."CR_授業録音分塊情報"."コンテナ先頭" IS
    '新しいコンテナ（EBML/OGG/MP4）のヘッダで始まる分塊か。一時停止・開き直しで MediaRecorder を作り直した回の先頭が true';
COMMENT ON COLUMN public."CR_授業録音分塊情報"."終了オフセット秒" IS
    '画面が測った実際の経過秒。録音の最大時間の判定と、開き直したあとの続きの位置に使う（転写の連番では測らない）';
