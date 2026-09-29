-- ============================================================================
-- Study 2.1  日本語勉強 DDL（最終仕様）
-- テーブル: JPN_音声キャッシュ情報（学習画面の読み上げ音声のキャッシュ）
-- ----------------------------------------------------------------------------
-- 学習画面（A 勉強）は「単語・読み・例文」を音声で再生する。音声は外部の TTS
-- （2.0 は Google の音声合成）で作るため、**同じ文を二度作るとそのぶん課金（額）を食う**。
--
-- そこで **最初の 1 回だけ外部を呼び、できた音声をサーバに置いて、次からはそれを返す**。
-- この表はその「どの文の音声が、どこにあるか」を持つ台帳。
--
-- 流れ:
--   ① 画面が `GET /speech?text=...&speaker=...&speed=...` を呼ぶ
--   ② `音声キー`（テキスト＋話者＋速度の SHA-256）でこの表を引く
--        ・`状態コード='READY'` の行があり、ファイルの実体もある
--          → **そのファイルを返すだけ**（外部を呼ばない。`再生回数` を +1）
--        ・`FAILED` の行 → 一定時間は再試行しない（毎回呼んで無駄に消費しない）
--        ・行が無い／実体が消えている → 外部 TTS を 1 回だけ呼ぶ
--   ③ 取れた音声を `音声保存先パス` に置き、この表に 1 行（`READY`）を入れる
--      （取れなければ `FAILED` と `エラーコード` を残し、次回の判断に使う）
--
-- 置き場（既存のファイル保存と同じ約束）:
--   ・`study21.speech.storage-root`（既定 `${user.dir}/data/speech`）の下に置く
--   ・DB には **その根からの相対パス**だけを入れる（絶対パスは環境で変わるため）
--   ・ファイル名は UUID（元の文は `テキスト` に残す）
--   ・Web サーバーの静的ディレクトリには置かない。配信は API 経由（セッション確認後）
--
-- 同じ文を別の語が使うこともある（例文の使い回し）ので、**語ではなく文でキャッシュ**する。
-- `対象種別コード`・`対象ID` は「どの画面から最初に作られたか」の記録で、キャッシュの鍵ではない。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."JPN_音声キャッシュ情報" (
    "音声ID"             BIGSERIAL    NOT NULL,
    -- キャッシュの鍵。テキスト＋話者＋速度の SHA-256（64 桁）。同じ文・同じ話者・同じ速さは 1 行
    "音声キー"           CHAR(64)     NOT NULL,
    -- 生成に使った文（そのまま。長い例文があるので TEXT）
    "テキスト"           TEXT         NOT NULL,
    -- 文の種類（TEXT / WORD / READING / EXAMPLE / DIALOG。増えても CHECK は付けない）
    "テキスト種別コード" VARCHAR(20)  NULL,
    -- 話者（声）と速さ。同じ文でも声や速さが違えば別の音声
    "話者"               VARCHAR(100) NULL,
    "速度"               NUMERIC(3,2) NULL,
    -- tts=外部で作った / upload=人や取り込みで置いた（外部を呼ばない）
    "音声ソースコード"   VARCHAR(20)  NOT NULL DEFAULT 'TTS',
    -- TTS の提供元・声の名前・言語（例: google / ja-JP-Neural2-B / ja-JP）
    "TTS提供元"          VARCHAR(50)  NULL,
    "声の名前"           VARCHAR(100) NULL,
    "言語コード"         VARCHAR(20)  NULL,
    -- READY=使える / FAILED=作れなかった / EXPIRED=実体を消した（行は残す）
    "状態コード"         VARCHAR(20)  NOT NULL DEFAULT 'READY',
    -- storage-root からの相対パス。**絶対パスは入れない**（環境で変わる）
    "音声保存先パス"     VARCHAR(500) NULL,
    -- 実体の情報（配信ヘッダと、消えたことの検知に使う）
    "内容ハッシュ"       CHAR(64)     NULL,
    "ファイルサイズ"     BIGINT       NULL,
    "MIMEタイプ"         VARCHAR(50)  NOT NULL DEFAULT 'audio/mpeg',
    "長さms"             INTEGER      NULL,
    -- 課金の目安: 外部を呼んだのは 1 回だけ。`再生回数` は外部を呼ばずに返した回数も数える
    "外部呼出回数"       INTEGER      NOT NULL DEFAULT 0,
    "再生回数"           INTEGER      NOT NULL DEFAULT 0,
    -- 失敗の記録（一定時間は再試行しないための材料）
    "エラーコード"       VARCHAR(100) NULL,
    "エラーメッセージ"   TEXT         NULL,
    "失敗回数"           INTEGER      NOT NULL DEFAULT 0,
    -- どの画面から最初に作られたか（語／問題／例文など）。**キャッシュの鍵ではない**
    "対象種別コード"     VARCHAR(20)  NULL,
    "対象ID"             BIGINT       NULL,
    -- 初回に作った人（課金はアカウントに紐づくため）と、最後に使った人
    "生成アカウントID"   BIGINT       NULL,
    "最終利用アカウントID" BIGINT     NULL,
    "生成日時"           TIMESTAMP    NULL,
    "最終利用日時"       TIMESTAMP    NULL,
    -- 実体を消した日時（掃除の記録）
    "削除日時"           TIMESTAMP    NULL,
    "登録者アカウントID" BIGINT       NULL,
    "更新者アカウントID" BIGINT       NULL,
    -- TTS=外部で生成 / APP=画面 / BATCH=バッチ / MIGRATION=移行
    "登録元コード"       VARCHAR(20)  NOT NULL DEFAULT 'TTS',
    "更新元コード"       VARCHAR(20)  NULL,
    "登録日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "JPN_音声キャッシュ情報_pkey" PRIMARY KEY ("音声ID"),
    CONSTRAINT "FK_JPN_音声_生成アカウント"
        FOREIGN KEY ("生成アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_音声_最終利用アカウント"
        FOREIGN KEY ("最終利用アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_音声_登録者"
        FOREIGN KEY ("登録者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_JPN_音声_更新者"
        FOREIGN KEY ("更新者アカウントID")
        REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_JPN_音声_状態コード"
        CHECK ("状態コード" IN ('READY', 'FAILED', 'EXPIRED')),
    CONSTRAINT "CK_JPN_音声_ソース"
        CHECK ("音声ソースコード" IN ('TTS', 'UPLOAD')),
    CONSTRAINT "CK_JPN_音声_鍵"
        CHECK (NULLIF(BTRIM("テキスト"), '') IS NOT NULL AND "音声キー" ~ '^[0-9a-f]{64}$'),
    CONSTRAINT "CK_JPN_音声_速度"
        CHECK ("速度" IS NULL OR ("速度" >= 0.5 AND "速度" <= 2.0)),
    CONSTRAINT "CK_JPN_音声_件数"
        CHECK (
            "外部呼出回数" >= 0 AND "再生回数" >= 0 AND "失敗回数" >= 0
            AND ("ファイルサイズ" IS NULL OR "ファイルサイズ" >= 0)
            AND ("長さms" IS NULL OR "長さms" >= 0)
        ),
    -- 使えると言いながら置き場が無い／作れなかったのに理由が無い、を作らない
    CONSTRAINT "CK_JPN_音声_実体"
        CHECK ("状態コード" <> 'READY' OR "音声保存先パス" IS NOT NULL),
    CONSTRAINT "CK_JPN_音声_失敗"
        CHECK ("状態コード" <> 'FAILED' OR "エラーコード" IS NOT NULL)
);

-- キャッシュの引き当て（毎回の再生で引くので、これが一番効く索引）
CREATE UNIQUE INDEX IF NOT EXISTS uq_jpn_audio_key
    ON public."JPN_音声キャッシュ情報" ("音声キー");

-- 失敗したものだけを探す（再試行の判断用）
CREATE INDEX IF NOT EXISTS idx_jpn_audio_failed
    ON public."JPN_音声キャッシュ情報" ("更新日時")
    WHERE "状態コード" = 'FAILED';

-- 実体の掃除（久しく使っていない READY を探す）
CREATE INDEX IF NOT EXISTS idx_jpn_audio_unused
    ON public."JPN_音声キャッシュ情報" ("最終利用日時")
    WHERE "状態コード" = 'READY';

-- 「この語の音声は作ったか」を引く（学習画面の表示用。鍵ではない）
CREATE INDEX IF NOT EXISTS idx_jpn_audio_target
    ON public."JPN_音声キャッシュ情報" ("対象種別コード", "対象ID");

COMMENT ON TABLE public."JPN_音声キャッシュ情報" IS
    '学習画面の読み上げ音声のキャッシュ。最初の 1 回だけ外部 TTS を呼び、以後はここから返して課金を増やさない';
COMMENT ON COLUMN public."JPN_音声キャッシュ情報"."音声キー" IS
    'テキスト＋話者＋速度の SHA-256（小文字 64 桁）。同じ文・同じ声・同じ速さは 1 行にまとめる';
COMMENT ON COLUMN public."JPN_音声キャッシュ情報"."音声保存先パス" IS
    'study21.speech.storage-root からの相対パス。絶対パスは環境で変わるので入れない';
COMMENT ON COLUMN public."JPN_音声キャッシュ情報"."外部呼出回数" IS
    '外部 TTS を呼んだ回数（課金の目安）。READY になった行は 1 のまま増えない';
COMMENT ON COLUMN public."JPN_音声キャッシュ情報"."対象種別コード" IS
    'どの語・どの問題から最初に作られたかの記録。キャッシュの鍵ではなく、画面の表示に使う';
COMMENT ON COLUMN public."JPN_音声キャッシュ情報"."対象ID" IS
    '対象種別コードと組で使う ID（語 ID など）。キャッシュの鍵ではない';
COMMENT ON COLUMN public."JPN_音声キャッシュ情報"."状態コード" IS
    'READY=使える / FAILED=作れなかった（エラーコード必須）/ EXPIRED=実体を消した（行は残す）';
