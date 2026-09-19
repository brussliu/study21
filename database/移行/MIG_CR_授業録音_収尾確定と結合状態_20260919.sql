-- ============================================================================
-- 授業録音: 収尾で確定した「録れた分塊の範囲」と、再生用 1 本の生成状態を持たせる
-- ----------------------------------------------------------------------------
-- 背景（2026-09-19 改修 第 4 段）:
--   1. 終了（収尾）のときに確定した**実際に録れた分塊の最後の連番**を残していなかった。
--      そのため、終了のあとに遅れて届いた分塊が「宣言していない新しい連番」でも受け入れられ、
--      詳細画面が「全部そろっています」と言ったまま実体が増え得た。確定した範囲を DB に
--      残し、その外の連番は断る。
--   2. 再生用の 1 本（分塊の結合）の状態を**メモリの Map だけ**で持っていた。user-api を
--      再起動すると READY が消えて NONE に戻り、画面は「作成中」を出し続ける（永久に待つ）。
--      状態・理由・欠けた連番と、**どの分塊から作ったか**（内容の要約 = ダイジェスト）を
--      DB に残し、再起動後も「できている／やり直せる」を判断できるようにする。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: 何度流しても同じ（ADD COLUMN IF NOT EXISTS / COMMENT は同じ値で上書き）
-- ============================================================================

ALTER TABLE public."CR_授業記録情報"
    -- 収尾で確定した「実際に録れた」最後の分塊の連番（1 から。0/NULL = 確定していない）
    ADD COLUMN IF NOT EXISTS "録音分塊最終連番" INTEGER NULL,
    -- 収尾で確定したときの、宣言された**録れた分塊の数**（画面の一覧の件数）
    ADD COLUMN IF NOT EXISTS "録音分塊宣言数" INTEGER NULL,
    -- 収尾で確定した「録音の終わりの位置」（統一時間軸。16kHz のサンプル数）
    ADD COLUMN IF NOT EXISTS "録音終了サンプル" BIGINT NULL,
    -- 音声が全部そろっていると確認できたか（false = 欠けたまま終えた＝失った音がある）
    ADD COLUMN IF NOT EXISTS "録音完備" BOOLEAN NULL,
    -- 明示の「不完全なまま終了」で失った連番（カンマ区切り。詳細画面に出し続ける）
    ADD COLUMN IF NOT EXISTS "録音欠落連番" VARCHAR(1000) NULL,
    -- ---- 再生用 1 本の生成状態（NOT_STARTED / QUEUED / PROCESSING / READY / FAILED / INCOMPLETE）----
    ADD COLUMN IF NOT EXISTS "結合状態" VARCHAR(20) NULL,
    -- 結合のもとになった分塊の**内容の要約**（連番・バイト数・チェックサム）。出力が現在の分塊に対応するかの判断
    ADD COLUMN IF NOT EXISTS "結合元ダイジェスト" VARCHAR(64) NULL,
    -- できた 1 本の長さ（秒）と、人が読む理由（日本語）
    ADD COLUMN IF NOT EXISTS "結合長秒" NUMERIC(10,3) NULL,
    ADD COLUMN IF NOT EXISTS "結合理由" VARCHAR(500) NULL,
    -- 結合を始めた／終えた時刻（PROCESSING のまま残った回を再起動後に見分ける）
    ADD COLUMN IF NOT EXISTS "結合開始日時" TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS "結合終了日時" TIMESTAMP NULL;

-- 状態の値は決まった 6 つだけ（綴り間違いを DB で止める）
ALTER TABLE public."CR_授業記録情報"
    DROP CONSTRAINT IF EXISTS "CK_CR_授業記録_結合状態";
ALTER TABLE public."CR_授業記録情報"
    ADD CONSTRAINT "CK_CR_授業記録_結合状態" CHECK (
        "結合状態" IS NULL OR "結合状態" IN
            ('NOT_STARTED', 'QUEUED', 'PROCESSING', 'READY', 'FAILED', 'INCOMPLETE')
    );

COMMENT ON COLUMN public."CR_授業記録情報"."録音分塊最終連番" IS
    '収尾で確定した「実際に録れた」最後の分塊の連番。終了後に届いた分塊がこの外なら断る（確定した一覧を動かさない）';
COMMENT ON COLUMN public."CR_授業記録情報"."録音完備" IS
    '収尾のときに音声（分塊）が全部そろっていると確認できたか。false は欠けたまま終えた＝その区間の音は残っていない';
COMMENT ON COLUMN public."CR_授業記録情報"."録音欠落連番" IS
    '不完全なまま終えた回に失った連番（カンマ区切り）。詳細画面が「どこが失われたか」を出し続けるために残す';
COMMENT ON COLUMN public."CR_授業記録情報"."結合状態" IS
    'NOT_STARTED=まだ / QUEUED=受け付けた / PROCESSING=作成中 / READY=できた / FAILED=失敗（やり直せる）/ INCOMPLETE=欠落があり作らない';
COMMENT ON COLUMN public."CR_授業記録情報"."結合元ダイジェスト" IS
    '結合のもとにした分塊の内容の要約（SHA-256）。いまの分塊と一致すれば、その 1 本は最新（再起動後も判断できる）';
