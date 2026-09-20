-- ============================================================================
-- Study 2.1  授業録音 / AI 授業記録 DDL
-- テーブル: CR_授業記録情報
-- ----------------------------------------------------------------------------
-- 1 授業（1 回の録音セッション）= 1 行のマスタ。生徒がブラウザで録音し、
-- user-api が分塊（チャンク）を受け取って STT し、転写を CR_授業転写セグメント情報 に追記、
-- フェーズ単位の AI 授業ノートと終了時の最終まとめは CR_授業ノート情報 に持つ。
-- 2.0 にこの機能は無いので移行は無い（新規テーブル）。
-- 設計: tmp/classroom-ai-design.md §3.1
--
-- 状態機械（状態コード）:
--   RECORDING     録音中                         … user-api が作成
--   STOPPED       停止（最終まとめ待ち）          … user-api /end
--   TRANSCRIBING  転写中（再解析など。予約）       … （MVP では主に使わない）
--   ANALYZING     AI 分析中（ノート生成待ち）     … batC61/batC62 が拾う
--   COMPLETED     完了（最終まとめ生成済み）      … batC62
--   FAILED        失敗                           … バッチ / 手動
--   CANCELLED     取消                           … user-api /delete 等
--
-- 音声は DB に持たずファイルで持つ（study21.classroom.storage-root の
-- `classroom/<accountId>/<yyyyMM>/<uuid>.webm`）。DB には相対パス + ファイル名だけを入れる。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: 何度流しても同じ（CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS）
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."CR_授業記録情報" (
    "授業記録ID"         BIGSERIAL    NOT NULL,
    -- 利用者に見せる番号（'CR' + yyyyMMddHHmmssSSS + 4桁。AI 生図の要求番号と同じ採番方式）
    "授業記録番号"       VARCHAR(30)  NOT NULL,
    -- 所有者（録音した生徒）。visibility の根拠（学生=自分 / 保護者=家族 / 管理者=全体）
    "登録者アカウントID" BIGINT       NOT NULL,
    -- 家族内共有の判定用（TempFileStorage.familyStudentId と同じ思想）。
    -- 学生ID = 録音した生徒、家族ID = その保護者。denormalize して一覧を JOIN なしで引けるようにする
    "学生ID"             BIGINT       NULL,
    "家族ID"             BIGINT       NULL,
    "授業名"             VARCHAR(200) NULL,   -- 任意タイトル（科目名など）
    "科目"               VARCHAR(40)  NULL,   -- 科目（数学・英語など）。授業名とは別に持つ
    -- zh / ja / en / zh-en / ja-en / auto（frontend/features/classroom/classroom.ts の LanguageMode）
    "言語モード"         VARCHAR(20)  NOT NULL DEFAULT 'auto',
    -- FK → CR_前置詞プリセット情報（選択時にスナップショットを 前置詞テキスト にも残す）
    "前置詞ID"           BIGINT       NULL,
    "前置詞テキスト"     TEXT         NULL,
    -- RECORDING / STOPPED / TRANSCRIBING / ANALYZING / COMPLETED / FAILED / CANCELLED
    "状態"               VARCHAR(20)  NOT NULL DEFAULT 'RECORDING',
    "開始時刻"           TIMESTAMP    NULL,
    "終了時刻"           TIMESTAMP    NULL,
    -- ---- 録音（音声は DB 外。相対パス + ファイル名 + 実体判定） ----
    "録音ファイル保存先" VARCHAR(255) NULL,   -- 例 'classroom/2/202609'
    "保存ファイル名"     VARCHAR(200) NULL,   -- 例 'a1b2c3d4.webm'
    "録音MIME"           VARCHAR(120) NULL,
    "録音ファイルサイズ" BIGINT       NULL,
    "録音時間秒"         INTEGER      NULL,
    -- トリガー判定用の累積カウンタ（denormalized）。追記セグメントの文字数分だけ加算する
    "転写済み文字数"     INTEGER      NOT NULL DEFAULT 0,
    -- 終了時の最終まとめ（テーマ／学習内容／先生の重点／宿題…）。JSONB
    "最終まとめJSON"     JSONB        NULL,
    -- 保存期間から算出（batR02 の掃除対象。設定 CLASSROOM_AI_RETENTION_DAYS）
    "保持期限"           DATE         NULL,

    -- ---- 書き起こし（認識）の収尾の結果（2026-09-19 改修 第 4 段。MIG_CR_授業録音_収尾確定と結合状態 と同じ） ----
    -- COMPLETE / INCOMPLETE / RUNNING / NO_AUDIO / UNKNOWN
    "認識収尾状態"       VARCHAR(20)  NULL,
    -- 認識が**完全にそろった**か（false = やり直しても直らない不完整な終わりがある）
    "認識完備"           BOOLEAN      NULL,
    -- 音源ごとの結果（JSON の配列）
    "認識音源状態"       TEXT         NULL,
    "認識収尾理由"       VARCHAR(500) NULL,
    "認識収尾更新日時"   TIMESTAMP    NULL,

    -- ---- 再生用の 1 本（分塊の結合）の状態（同 改修） ----
    -- NOT_STARTED / QUEUED / PROCESSING / READY / FAILED / INCOMPLETE
    "結合状態"           VARCHAR(20)  NULL,
    -- 結合のもとにした分塊の内容の要約（SHA-256）
    "結合元ダイジェスト" VARCHAR(64)  NULL,
    "結合長秒"           NUMERIC(10,3) NULL,
    "結合理由"           VARCHAR(500) NULL,
    "結合開始日時"       TIMESTAMP    NULL,
    "結合終了日時"       TIMESTAMP    NULL,

    -- ---- 最終まとめの生成（起動）の記録（同 改修 第 5 段） ----
    -- だれがいつ「生成を始めた」と言ったか（プロセスが落ちたままの GENERATING を見分ける）
    "生成開始日時"       TIMESTAMP    NULL,

    -- ---- 2.1 の共通規約（GEO_AI生図リクエスト情報 と同じ） ----
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "CR_授業記録情報_pkey" PRIMARY KEY ("授業記録ID"),
    CONSTRAINT "FK_CR_授業記録_登録者" FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_CR_授業記録_学生" FOREIGN KEY ("学生ID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_CR_授業記録_家族" FOREIGN KEY ("家族ID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_CR_授業記録_更新者" FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_CR_授業記録_前置詞" FOREIGN KEY ("前置詞ID")
        REFERENCES public."CR_前置詞プリセット情報" ("前置詞ID") ON DELETE SET NULL,
    CONSTRAINT "CK_CR_授業記録_状態" CHECK ("状態" IN
        ('RECORDING','STOPPED','TRANSCRIBING','ANALYZING','COMPLETED','FAILED','CANCELLED')),
    CONSTRAINT "CK_CR_授業記録_言語モード" CHECK ("言語モード" IN
        ('zh','ja','en','zh-en','ja-en','auto')),
    CONSTRAINT "CK_CR_授業記録_番号" CHECK (BTRIM("授業記録番号") <> ''),
    CONSTRAINT "CK_CR_授業記録_転写済み文字数" CHECK ("転写済み文字数" >= 0),
    CONSTRAINT "CK_CR_授業記録_録音時間" CHECK ("録音時間秒" IS NULL OR "録音時間秒" >= 0),
    CONSTRAINT "CK_CR_授業記録_サイズ" CHECK ("録音ファイルサイズ" IS NULL OR "録音ファイルサイズ" >= 0),
    CONSTRAINT "CK_CR_授業記録_終了時刻" CHECK ("終了時刻" IS NULL OR "開始時刻" IS NULL OR "終了時刻" >= "開始時刻"),
    CONSTRAINT "CK_CR_授業記録_バージョン" CHECK ("バージョン" >= 1),
    CONSTRAINT "CK_CR_授業記録_結合状態" CHECK ("結合状態" IS NULL OR "結合状態" IN
        ('NOT_STARTED','QUEUED','PROCESSING','READY','FAILED','INCOMPLETE')),
    CONSTRAINT "CK_CR_授業記録_認識収尾状態" CHECK ("認識収尾状態" IS NULL OR "認識収尾状態" IN
        ('COMPLETE','INCOMPLETE','RUNNING','NO_AUDIO','UNKNOWN'))
);

-- 授業記録番号は一意（利用者に見せる番号）
CREATE UNIQUE INDEX IF NOT EXISTS uq_cr_record_no
    ON public."CR_授業記録情報" ("授業記録番号");

-- 一覧（新しい順。履歴画面）
CREATE INDEX IF NOT EXISTS idx_cr_record_created
    ON public."CR_授業記録情報" ("登録日時" DESC, "授業記録ID" DESC);

-- 所有者（学生）ごとの履歴
CREATE INDEX IF NOT EXISTS idx_cr_record_owner_created
    ON public."CR_授業記録情報" ("登録者アカウントID", "登録日時" DESC);

-- 保護者（家族）ごとの履歴
CREATE INDEX IF NOT EXISTS idx_cr_record_family_created
    ON public."CR_授業記録情報" ("家族ID", "登録日時" DESC);

-- batR02 の保持期限切れの掃除対象（保持期限を過ぎた行）
CREATE INDEX IF NOT EXISTS idx_cr_record_retention
    ON public."CR_授業記録情報" ("保持期限")
    WHERE "保持期限" IS NOT NULL;

COMMENT ON TABLE public."CR_授業記録情報" IS
    E'授業録音 / AI 授業記録の 1 セッション = 1 行。\n2.0 に無い新機能なので移行は無い。\n音声はファイルで持ち（パス + ファイル名）、DB には持たない。';
COMMENT ON COLUMN public."CR_授業記録情報"."状態" IS
    'RECORDING=録音中 / STOPPED=停止（最終まとめ待ち） / TRANSCRIBING=転写中（予約） / ANALYZING=AI分析中 / COMPLETED=完了 / FAILED=失敗 / CANCELLED=取消';
COMMENT ON COLUMN public."CR_授業記録情報"."言語モード" IS
    '授業の言語モード（frontend/features/classroom/classroom.ts の LanguageMode）。zh / ja / en / zh-en / ja-en / auto';
COMMENT ON COLUMN public."CR_授業記録情報"."学生ID" IS
    '録音した生徒のアカウントID（登録者アカウントID と同値）。家族内共有の判定用に denormalize';
COMMENT ON COLUMN public."CR_授業記録情報"."家族ID" IS
    '録音した生徒の保護者のアカウントID（ACC_アカウント.保護者ID）。保護者の「子どもの記録」閲覧の判定用';
COMMENT ON COLUMN public."CR_授業記録情報"."保持期限" IS
    '設定 CLASSROOM_AI_RETENTION_DAYS から算出した削除期限。batR02 がこの日付を過ぎた音声・転写を掃除する';
