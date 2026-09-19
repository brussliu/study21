-- ============================================================================
-- Study 2.1  授業録音 / AI 授業記録 DDL
-- テーブル: CR_授業録音分塊情報
-- ----------------------------------------------------------------------------
-- 分塊（`MediaRecorder` の `timeslice` で切れた 1 塊）を**1 行 1 ファイル**で持つ。
-- 理由（2026-09-19 改修）:
--   それまでは「録音 1 本のファイルへ追記」だけをしており、分塊の重複判定に
--   **転写セグメントの最大連番**（CR_授業転写セグメント情報.連番）を流用していた。
--   連番の意味が違うため、
--     * 転写が分塊より多く出た回（例: 転写 8 文・分塊 3 個）… 3 個目の分塊が
--       「重複」と誤判定されて**音声が捨てられる**
--     * 転写が 1 件も出なかった回（無音の授業など）… 最大連番が 0 のままで、
--       画面を開き直して同じ分塊を送ると**二重に追記される**
--     * 画面は「次に送る分塊の連番」を転写の連番から作れない
--   という 3 つの不具合が出ていた。
--   分塊を自前の (授業記録ID, 分塊連番) で持てば、到着順・再送・再起動に依らず
--   同じ分塊は 1 行 1 ファイルに落ち着く（＝再生用の 1 本は後から組立て直せる）。
-- 設計: docs/DECISIONS.md「なぜ録音の分塊を独立した表（CR_授業録音分塊情報）にするか」
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: 何度流しても同じ（CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS）
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."CR_授業録音分塊情報" (
    "録音分塊ID"        BIGSERIAL      NOT NULL,
    "授業記録ID"        BIGINT         NOT NULL,
    -- 分塊の連番（1 から。画面が送る seq と同じ）。同じ連番＝同じ分塊
    "分塊連番"          INTEGER        NOT NULL,
    -- 録音開始からのオフセット（秒。画面が測った実際の経過秒。旧クライアントは NULL）
    "開始オフセット秒"  NUMERIC(10,3)  NULL,
    "終了オフセット秒"  NUMERIC(10,3)  NULL,
    -- 保存したバイト数（冪等判定と、再生用の 1 本の出来上がりサイズの見積りに使う）
    "バイト数"          BIGINT         NOT NULL,
    -- 中身の照合（SHA-256 の 16 進 64 文字）。同じ連番で**違う中身**が来たことを見分ける
    "チェックサム"      VARCHAR(64)    NOT NULL,
    -- 実体の置き場（ストレージルートからの相対ディレクトリ）とファイル名
    "保存先"            VARCHAR(255)   NOT NULL,
    "保存ファイル名"    VARCHAR(255)   NOT NULL,
    "MIME"              VARCHAR(100)   NOT NULL DEFAULT 'audio/webm',
    -- この分塊が**新しいコンテナの先頭**か（EBML/OGG/MP4 のヘッダで始まるか）。
    -- 一時停止・画面の開き直しで `MediaRecorder` を作り直すと 2 本目のコンテナが始まる。
    -- 再生用の 1 本を組立てるときは、2 本目以降のヘッダを落として中身（Cluster）だけを繋ぐ
    "コンテナ先頭"      BOOLEAN        NOT NULL DEFAULT FALSE,
    -- 処理状態: STORED=音声だけ保存 / TRANSCRIBED=書き起こし済み / SKIPPED=書き起こしを省略
    -- （ブラウザ認識・ストリーミング書き起こし・終了後の遅延分塊）
    "処理状態"          VARCHAR(20)    NOT NULL DEFAULT 'STORED',
    -- この分塊から作った転写セグメントの数（0 = 書き起こし無し）
    "転写セグメント数"  INTEGER        NOT NULL DEFAULT 0,
    "登録日時"          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "CR_授業録音分塊情報_pkey" PRIMARY KEY ("録音分塊ID"),
    CONSTRAINT "FK_CR_授業録音分塊_授業記録" FOREIGN KEY ("授業記録ID")
        REFERENCES public."CR_授業記録情報" ("授業記録ID") ON DELETE CASCADE,
    -- 同じ連番は 1 行だけ（再送・同時再送の重複はここで弾く。中身の照合はアプリ側）
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

-- 連番順の取得（再生用の組立て・画面の続きの連番・録音時間の判定）
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
