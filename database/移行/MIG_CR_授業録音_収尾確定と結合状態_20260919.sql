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
--   3. **書き起こし（認識）の収尾の結果**を残していなかった。収尾は「成功」と「失敗」の 2 つでは
--      なく、**やり直しても直らない不完整な終わり**（`error` は null・`finalizeCompleted=false`）
--      があるため、その場の応答だけでは「識別が完全だったか」を後から確かめられない
--      （画面を開き直すと分からなくなる）。記録に**識別の完備**と**音源ごとの結果**を残す。
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

ALTER TABLE public."CR_授業記録情報"
    -- 書き起こし（認識）の収尾の結果（COMPLETE / INCOMPLETE / RUNNING / NO_AUDIO / UNKNOWN）
    ADD COLUMN IF NOT EXISTS "認識収尾状態" VARCHAR(20) NULL,
    -- 認識が**完全にそろった**か（false = やり直しても直らない不完整な終わりがある）
    ADD COLUMN IF NOT EXISTS "認識完備" BOOLEAN NULL,
    -- 音源ごとの収尾の結果（JSON の配列。音源・段階・やり直せるか・済んだか・件数・理由）
    ADD COLUMN IF NOT EXISTS "認識音源状態" TEXT NULL,
    -- 人が読む理由（日本語。失敗ではない知らせもここに入る）
    ADD COLUMN IF NOT EXISTS "認識収尾理由" VARCHAR(500) NULL,
    ADD COLUMN IF NOT EXISTS "認識収尾更新日時" TIMESTAMP NULL;

-- ---- 最終まとめ（ノート）の生成開始の記録 ----
-- だれがいつ「生成を始めた」と言ったか（起動の受理）。プロセスが落ちて GENERATING のまま
-- 残った行を、再起動後に**やり直せる**と判断するのに使う（永久に「作成中」で止めない）。
ALTER TABLE public."CR_授業ノート情報"
    ADD COLUMN IF NOT EXISTS "生成開始日時" TIMESTAMP NULL,
    -- **この試行の識別子**（起動を受理するたびに新しくなる）。
    -- 完了・失敗の更新は「いまのトークンと一致するとき」だけ通す＝**遅れて返ってきた古い試行**が
    -- 新しい試行や既にできた結果を上書きしない。
    ADD COLUMN IF NOT EXISTS "生成トークン" VARCHAR(64) NULL;

COMMENT ON COLUMN public."CR_授業ノート情報"."生成トークン" IS
    '生成を受理した試行の識別子。条件つき更新の照合に使い、古い試行の遅い書き込みを捨てる';

-- **この試行を実行しているバッチ実行記録**（`BAT_バッチ実行履歴情報`.`実行ID`）。
-- 「そのまとめの実行が生きているか」を**別の授業の実行と混同せずに**判断するために持つ
-- （`AI呼出履歴ID` は AI 呼び出しログの ID なので**混用しない**）。
ALTER TABLE public."CR_授業ノート情報"
    ADD COLUMN IF NOT EXISTS "生成実行ID" BIGINT NULL;

COMMENT ON COLUMN public."CR_授業ノート情報"."生成実行ID" IS
    'この生成を実行しているバッチ実行記録の ID（実行ID）。失联判定を「その実行」で行うために使う';

COMMENT ON COLUMN public."CR_授業ノート情報"."生成開始日時" IS
    '生成の起動を受理した時刻。一定時間より古い GENERATING は「落ちた」とみなしてやり直す';

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
COMMENT ON COLUMN public."CR_授業記録情報"."認識完備" IS
    '書き起こし（認識）の収尾が完全に済んだか。false は「やり直しても直らない不完整な終わり」がある（音声の欠落とは別）';
COMMENT ON COLUMN public."CR_授業記録情報"."認識音源状態" IS
    '音源（mic / shared）ごとの収尾の結果を JSON の配列で持つ。音源ごとに「済んだか・やり直せるか」が違うため';
COMMENT ON COLUMN public."CR_授業記録情報"."結合状態" IS
    'NOT_STARTED=まだ / QUEUED=受け付けた / PROCESSING=作成中 / READY=できた / FAILED=失敗（やり直せる）/ INCOMPLETE=欠落があり作らない';
COMMENT ON COLUMN public."CR_授業記録情報"."結合元ダイジェスト" IS
    '結合のもとにした分塊の内容の要約（SHA-256）。いまの分塊と一致すれば、その 1 本は最新（再起動後も判断できる）';
