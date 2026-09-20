-- ============================================================================
-- Study 2.1  授業録音 / AI 授業記録 DDL
-- テーブル: CR_授業ノート情報
-- ----------------------------------------------------------------------------
-- フェーズノート（授業中の AI 分析結果）と最終まとめ（終了時）を「ノート行」で持つ。
-- 生成状態=PENDING の行を「admin-api バッチ（batC61 / batC62）が拾うキュー」にする
-- （GEO_AI生図リクエスト情報 の橋渡しを別テーブル化せず、ノート行自体を要求行として使う）。
-- 設計: tmp/classroom-ai-design.md §3.3
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: 何度流しても同じ（CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS）
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."CR_授業ノート情報" (
    "授業ノートID"      BIGSERIAL    NOT NULL,
    "授業記録ID"        BIGINT       NOT NULL,
    -- PHASE=フェーズ分析 / FINAL=最終まとめ
    "種別"              VARCHAR(10)  NOT NULL,
    -- PHASE のとき、何回目の分析か（1 始まり）
    "フェーズ番号"      INTEGER      NULL,
    -- 対象とする転写セグメントの範囲（開始連番・終了連番）。FINAL は全範囲（終了連番=最後）
    "対象開始連番"      INTEGER      NULL,
    "対象終了連番"      INTEGER      NULL,
    -- AI が返したノート本文（JSON）。画面はこれを描く
    "ノートJSON"        JSONB        NULL,
    -- PENDING=待ち / GENERATING=生成中 / READY=完了 / FAILED=失敗
    "生成状態"          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- BAT_AI呼出履歴情報.呼出履歴ID（FK は張らない）
    "AI呼出履歴ID"      BIGINT       NULL,
    "エラーコード"      VARCHAR(100) NULL,
    "エラーメッセージ"  TEXT         NULL,
    "再試行回数"        INTEGER      NOT NULL DEFAULT 0,
    -- 生成の**起動を受理した**時刻（前回の開始から一定時間たった GENERATING は「落ちた」とみなす）
    "生成開始日時"      TIMESTAMP    NULL,
    -- 受理した**試行の識別子**（遅れて返った古い試行の書き込みを捨てる照合に使う）
    "生成トークン"      VARCHAR(64)  NULL,
    -- **この試行を実行しているバッチ実行記録**の ID（別の授業の実行と混同しないための帰属）
    "生成実行ID"        BIGINT       NULL,

    -- ---- 2.1 の共通規約 ----
    "バージョン"         INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'APP',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "CR_授業ノート情報_pkey" PRIMARY KEY ("授業ノートID"),
    CONSTRAINT "FK_CR_授業ノート_授業記録" FOREIGN KEY ("授業記録ID")
        REFERENCES public."CR_授業記録情報" ("授業記録ID") ON DELETE CASCADE,
    CONSTRAINT "FK_CR_授業ノート_登録者" FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_CR_授業ノート_更新者" FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_CR_授業ノート_種別" CHECK ("種別" IN ('PHASE','FINAL')),
    CONSTRAINT "CK_CR_授業ノート_生成状態" CHECK ("生成状態" IN
        ('PENDING','GENERATING','READY','FAILED')),
    CONSTRAINT "CK_CR_授業ノート_フェーズ番号" CHECK ("フェーズ番号" IS NULL OR "フェーズ番号" >= 1),
    CONSTRAINT "CK_CR_授業ノート_範囲" CHECK (
        "対象開始連番" IS NULL OR "対象終了連番" IS NULL OR "対象終了連番" >= "対象開始連番"
    ),
    CONSTRAINT "CK_CR_授業ノート_再試行" CHECK ("再試行回数" >= 0),
    CONSTRAINT "CK_CR_授業ノート_バージョン" CHECK ("バージョン" >= 1),
    -- FAILED のときはエラーが入っていること
    CONSTRAINT "CK_CR_授業ノート_失敗理由" CHECK ("生成状態" <> 'FAILED' OR "エラーコード" IS NOT NULL)
);

-- バッチが拾う PENDING キュー（部分索引で小さく保つ）
CREATE INDEX IF NOT EXISTS idx_cr_note_pending
    ON public."CR_授業ノート情報" ("生成状態", "登録日時")
    WHERE "生成状態" IN ('PENDING','GENERATING');

-- 1 授業記録のノート一覧（詳細画面）
CREATE INDEX IF NOT EXISTS idx_cr_note_record
    ON public."CR_授業ノート情報" ("授業記録ID", "種別", "フェーズ番号");

COMMENT ON TABLE public."CR_授業ノート情報" IS
    'フェーズ分析（PHASE）と最終まとめ（FINAL）の AI ノート。生成状態=PENDING が admin-api バッチのキュー。';
COMMENT ON COLUMN public."CR_授業ノート情報"."種別" IS
    'PHASE=フェーズ分析 / FINAL=最終まとめ';
COMMENT ON COLUMN public."CR_授業ノート情報"."生成状態" IS
    'PENDING=待ち（バッチが拾う） / GENERATING=生成中 / READY=完了 / FAILED=失敗';
