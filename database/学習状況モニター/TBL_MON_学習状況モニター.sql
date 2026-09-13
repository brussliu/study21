-- ============================================================================
-- Study 2.1  学習状況モニター DDL（最終仕様）
--   テーブル: MON_学習モニターカメラ情報 / MON_学習モニター動画情報 /
--             MON_学習モニタースナップショット情報 / MON_学習モニター画像分析情報
-- ----------------------------------------------------------------------------
-- 2.0 の同名 4 テーブル（実データ: カメラ 1 件・動画 2,777 件・
-- スナップショット 43,326 件・分析 43,326 件）を 2.1 の規約で再設計する。
--
-- 撮影の流れ（2.0 の batL02 / batL03 と同じ）:
--   1. batL02 がカメラのフォルダーから動画を取り込み（動画情報）、
--      1 分間隔で画像を切り出す（スナップショット情報）
--   2. batL03 が画像を AI で判定する（画像分析情報。一次判定 → 必要なら二次判定）
--   3. 画面（学習状況モニター）で結果を見て、必要なら手動で修正する
--
-- 2.0 からの主な変更:
--   1. 持ち主を ユーザーID（'1000' のような 2.0 独自の ID）から
--      アカウントID（ACC_アカウント への FK）へ移した。突き合わせが取れない場合は
--      旧ユーザーID を残す（学習日報と同じ扱い）。
--   2. 判定結果・状態を**日本語の自由文字列からコード＋CHECK** へ変えた
--      （2.0 は CHECK に日本語を並べていた）。表示名はアプリ側（netLabels と同じ考え方）。
--         分析結果: STUDY_NO_PC / STUDY_PC / AWAY / PC_NON_STUDY / OTHER / UNKNOWN
--         分析状態: WAITING / RUNNING / COMPLETED / ERROR
--         採用段階: FLASH（一次）/ PLUS（二次）/ MANUAL（手動修正）
--   3. 状態は '1'=有効 / '0'=無効（2.0 は 'A'/'X'）。切出・取込の状態もコード化した。
--   4. 監査を 2.1 の規約（アカウント FK ＋ 登録元/更新元コード）に統一した。
--      取り込み・分析はバッチが行うため、登録者アカウントID は NULL 可で
--      登録元コードに 'BAT_L02' / 'BAT_L03' を入れる（端末コントロールと同じ扱い）。
--   5. 楽観的ロック用の バージョン を追加した（画面からの手動修正で使う）。
--   6. 保存パスは**保存ルートからの相対パス**にした（2.0 は絶対パス。
--      詳細は database/学習状況モニター/学習状況モニター設計.md）。
--   7. 索引を 2.1 の参照パターン（撮影日時の降順、分析状態×結果、日付ごとの一覧）に合わせた。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. カメラ（2.0 の STY_学習モニターカメラ情報）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public."MON_学習モニターカメラ情報" (
    "カメラID"             BIGSERIAL    NOT NULL,
    -- 持ち主（生徒）。移行時に突き合わせが取れない場合は NULL
    "アカウントID"         BIGINT       NULL,
    -- 2.0 の ユーザーID（'1000' など）。突き合わせの根拠として残す
    "旧ユーザーID"         VARCHAR(64)  NULL,
    "カメラコード"         VARCHAR(30)  NOT NULL,
    "カメラ名称"           VARCHAR(100) NOT NULL,
    "設置場所"             VARCHAR(200) NULL,
    "タイムゾーン"         VARCHAR(50)  NOT NULL DEFAULT 'Asia/Tokyo',
    -- 何秒ごとに画像を切り出すか（batL02）
    "スナップショット間隔秒" INTEGER    NOT NULL DEFAULT 60,
    -- '1'=有効 / '0'=無効（無効なカメラは取り込まない）
    "状態"                 VARCHAR(1)   NOT NULL DEFAULT '1',
    "バージョン"           INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID"   BIGINT       NULL,
    "更新者アカウントID"   BIGINT       NULL,
    "登録元コード"         VARCHAR(20)  NULL,
    "更新元コード"         VARCHAR(20)  NULL,
    "登録日時"             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "MON_学習モニターカメラ情報_pkey" PRIMARY KEY ("カメラID"),
    CONSTRAINT "FK_学習モニターカメラ_アカウント"
        FOREIGN KEY ("アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_学習モニターカメラ_登録者"
        FOREIGN KEY ("登録者アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_学習モニターカメラ_更新者"
        FOREIGN KEY ("更新者アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_学習モニターカメラ_間隔"
        CHECK ("スナップショット間隔秒" BETWEEN 1 AND 3600),
    CONSTRAINT "CK_学習モニターカメラ_状態" CHECK ("状態" IN ('0', '1')),
    CONSTRAINT "CK_学習モニターカメラ_名称" CHECK (BTRIM("カメラ名称") <> ''),
    CONSTRAINT "CK_学習モニターカメラ_バージョン" CHECK ("バージョン" > 0)
);

-- カメラは 1 台だけ（2.0 も 1 件）。コードで一意にする
CREATE UNIQUE INDEX IF NOT EXISTS uq_mon_camera_code
    ON public."MON_学習モニターカメラ情報" ("カメラコード");

COMMENT ON TABLE public."MON_学習モニターカメラ情報" IS
    '学習状況モニターのカメラ。画像の切り出し間隔を持つ（2.0 の STY_学習モニターカメラ情報）';
COMMENT ON COLUMN public."MON_学習モニターカメラ情報"."状態" IS '1=有効 / 0=無効（無効なカメラは取り込まない）';

-- ---------------------------------------------------------------------------
-- 2. 動画（2.0 の STY_学習モニター動画情報）… batL02 が取り込む
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public."MON_学習モニター動画情報" (
    "動画ID"               BIGSERIAL    NOT NULL,
    "カメラID"             BIGINT       NOT NULL,
    "撮影日"               DATE         NOT NULL,
    "撮影開始日時"         TIMESTAMP    NOT NULL,
    "撮影終了日時"         TIMESTAMP    NOT NULL,
    "動画ファイル名"       VARCHAR(255) NOT NULL,
    -- 保存ルートからの相対パス（例: 20260726/00_20260726155346_20260726160140.mp4）
    "保存パス"             VARCHAR(500) NULL,
    "動画時間秒"           INTEGER      NOT NULL,
    "ファイルサイズ"       BIGINT       NULL,
    -- WAITING=取込待ち / IMPORTED=取込済 / ERROR=取込失敗
    "取込状態コード"       VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    "取込日時"             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "状態"                 VARCHAR(1)   NOT NULL DEFAULT '1',
    "バージョン"           INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID"   BIGINT       NULL,
    "更新者アカウントID"   BIGINT       NULL,
    "登録元コード"         VARCHAR(20)  NULL,
    "更新元コード"         VARCHAR(20)  NULL,
    "登録日時"             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "MON_学習モニター動画情報_pkey" PRIMARY KEY ("動画ID"),
    CONSTRAINT "FK_学習モニター動画_カメラ"
        FOREIGN KEY ("カメラID") REFERENCES public."MON_学習モニターカメラ情報" ("カメラID") ON DELETE RESTRICT,
    CONSTRAINT "FK_学習モニター動画_登録者"
        FOREIGN KEY ("登録者アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_学習モニター動画_更新者"
        FOREIGN KEY ("更新者アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_学習モニター動画_期間" CHECK ("撮影終了日時" > "撮影開始日時"),
    CONSTRAINT "CK_学習モニター動画_時間" CHECK ("動画時間秒" >= 0),
    CONSTRAINT "CK_学習モニター動画_取込状態"
        CHECK ("取込状態コード" IN ('WAITING', 'IMPORTED', 'ERROR')),
    CONSTRAINT "CK_学習モニター動画_状態" CHECK ("状態" IN ('0', '1')),
    CONSTRAINT "CK_学習モニター動画_ファイル名" CHECK (BTRIM("動画ファイル名") <> ''),
    CONSTRAINT "CK_学習モニター動画_バージョン" CHECK ("バージョン" > 0)
);

-- 同じカメラで同じファイル・同じ時間帯は 1 件だけ（2.0 と同じ）
CREATE UNIQUE INDEX IF NOT EXISTS uq_mon_video_camera_file
    ON public."MON_学習モニター動画情報" ("カメラID", "動画ファイル名");
CREATE UNIQUE INDEX IF NOT EXISTS uq_mon_video_camera_period
    ON public."MON_学習モニター動画情報" ("カメラID", "撮影開始日時", "撮影終了日時");

-- 画面（対象日ごとの動画一覧）とホームの時間軸
CREATE INDEX IF NOT EXISTS idx_mon_video_search
    ON public."MON_学習モニター動画情報" ("カメラID", "撮影日", "撮影開始日時" DESC)
    WHERE "状態" = '1';

COMMENT ON TABLE public."MON_学習モニター動画情報" IS
    '取り込んだ録画ファイル（2.0 の STY_学習モニター動画情報）。batL02 が作る';
COMMENT ON COLUMN public."MON_学習モニター動画情報"."保存パス" IS
    '保存ルートからの相対パス（例: 20260726/xxx.mp4）。2.0 は絶対パスだった';

-- ---------------------------------------------------------------------------
-- 3. スナップショット（2.0 の STY_学習モニタースナップショット情報）… batL02 が切り出す
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public."MON_学習モニタースナップショット情報" (
    "スナップショットID"     BIGSERIAL    NOT NULL,
    "動画ID"                 BIGINT       NOT NULL,
    "撮影日時"               TIMESTAMP    NOT NULL,
    -- 動画の先頭からの位置（ミリ秒）
    "動画内オフセットミリ秒" BIGINT       NOT NULL,
    "画像ファイル名"         VARCHAR(255) NOT NULL,
    -- 保存ルートからの相対パス（例: 20260726/snapshot_000045.jpg）
    "保存パス"               VARCHAR(500) NOT NULL,
    "幅"                     INTEGER      NULL,
    "高さ"                   INTEGER      NULL,
    -- WAITING=切出待ち / CREATED=切出済 / ERROR=切出失敗
    "切出状態コード"         VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    "状態"                   VARCHAR(1)   NOT NULL DEFAULT '1',
    "バージョン"             INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID"     BIGINT       NULL,
    "更新者アカウントID"     BIGINT       NULL,
    "登録元コード"           VARCHAR(20)  NULL,
    "更新元コード"           VARCHAR(20)  NULL,
    "登録日時"               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "MON_学習モニタースナップショット情報_pkey" PRIMARY KEY ("スナップショットID"),
    CONSTRAINT "FK_学習モニタースナップショット_動画"
        FOREIGN KEY ("動画ID") REFERENCES public."MON_学習モニター動画情報" ("動画ID") ON DELETE CASCADE,
    CONSTRAINT "FK_学習モニタースナップショット_登録者"
        FOREIGN KEY ("登録者アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_学習モニタースナップショット_更新者"
        FOREIGN KEY ("更新者アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_学習モニタースナップショット_オフセット" CHECK ("動画内オフセットミリ秒" >= 0),
    CONSTRAINT "CK_学習モニタースナップショット_切出状態"
        CHECK ("切出状態コード" IN ('WAITING', 'CREATED', 'ERROR')),
    CONSTRAINT "CK_学習モニタースナップショット_状態" CHECK ("状態" IN ('0', '1')),
    CONSTRAINT "CK_学習モニタースナップショット_保存パス" CHECK (BTRIM("保存パス") <> ''),
    CONSTRAINT "CK_学習モニタースナップショット_サイズ"
        CHECK (("幅" IS NULL OR "幅" > 0) AND ("高さ" IS NULL OR "高さ" > 0)),
    CONSTRAINT "CK_学習モニタースナップショット_バージョン" CHECK ("バージョン" > 0)
);

-- 同じ動画の同じ位置は 1 枚だけ（2.0 と同じ）
CREATE UNIQUE INDEX IF NOT EXISTS uq_mon_snapshot_video_offset
    ON public."MON_学習モニタースナップショット情報" ("動画ID", "動画内オフセットミリ秒");

-- 画面（撮影日時の範囲）とホームの時間軸
CREATE INDEX IF NOT EXISTS idx_mon_snapshot_captured
    ON public."MON_学習モニタースナップショット情報" ("撮影日時" DESC, "動画ID")
    WHERE "状態" = '1';

COMMENT ON TABLE public."MON_学習モニタースナップショット情報" IS
    '動画から 1 分間隔で切り出した画像（2.0 の STY_学習モニタースナップショット情報）。batL02 が作る';

-- ---------------------------------------------------------------------------
-- 4. 画像分析（2.0 の STY_学習モニター画像分析情報）… batL03 が書き、画面で手動修正する
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public."MON_学習モニター画像分析情報" (
    "画像分析ID"             BIGSERIAL    NOT NULL,
    "スナップショットID"     BIGINT       NOT NULL,
    -- '1'=最新版（画面が見るのはこれだけ）/ '0'=過去版
    "最新版フラグ"           VARCHAR(1)   NOT NULL DEFAULT '1',
    -- 検索を速くするための控え（スナップショットの 撮影日時 と同じ値）
    "撮影日時"               TIMESTAMP    NULL,
    -- WAITING / RUNNING / COMPLETED / ERROR
    "分析状態コード"         VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    -- 一次判定の信頼度がこれを下回ると二次判定する（2.0 と同じ 0.750）
    "二次判定閾値"           NUMERIC(4,3) NOT NULL DEFAULT 0.750,

    -- 一次判定（batL03）
    "一次分析状態コード"     VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    "一次判定結果コード"     VARCHAR(20)  NULL,
    "一次信頼度"             NUMERIC(4,3) NULL,
    "一次判定理由"           TEXT         NULL,
    "一次AIプロバイダ"       VARCHAR(30)  NOT NULL DEFAULT 'qwen',
    "一次AIモデル"           VARCHAR(100) NOT NULL DEFAULT 'Qwen3-VL-Flash',
    "一次プロンプト版"       VARCHAR(50)  NULL,
    "一次分析開始日時"       TIMESTAMP    NULL,
    "一次分析完了日時"       TIMESTAMP    NULL,
    "一次応答JSON"           JSONB        NULL,
    "一次エラー内容"         TEXT         NULL,

    -- 二次判定（必要なときだけ）
    "二次判定要否"           VARCHAR(1)   NOT NULL DEFAULT '0',
    "二次分析状態コード"     VARCHAR(20)  NOT NULL DEFAULT 'NOT_REQUIRED',
    "二次判定結果コード"     VARCHAR(20)  NULL,
    "二次信頼度"             NUMERIC(4,3) NULL,
    "二次詳細推論"           TEXT         NULL,
    "二次AIプロバイダ"       VARCHAR(30)  NULL DEFAULT 'qwen',
    "二次AIモデル"           VARCHAR(100) NULL DEFAULT 'Qwen3-VL-Plus',
    "二次プロンプト版"       VARCHAR(50)  NULL,
    "二次分析開始日時"       TIMESTAMP    NULL,
    "二次分析完了日時"       TIMESTAMP    NULL,
    "二次応答JSON"           JSONB        NULL,
    "二次エラー内容"         TEXT         NULL,

    -- 最終的な判定（画面に出す値）
    "最終採用段階コード"     VARCHAR(20)  NULL,
    "最終分析結果コード"     VARCHAR(20)  NULL,
    "最終信頼度"             NUMERIC(4,3) NULL,
    "最終判定理由"           TEXT         NULL,
    "検出タグJSON"           JSONB        NOT NULL DEFAULT '[]',
    "AIコメント"             TEXT         NULL,
    "分析完了日時"           TIMESTAMP    NULL,

    -- 画面からの手動修正（2.0 の 手動修正* と同じ。修正理由は必須）
    "手動修正フラグ"         VARCHAR(1)   NOT NULL DEFAULT '0',
    "手動修正結果コード"     VARCHAR(20)  NULL,
    "手動修正理由"           TEXT         NULL,
    "手動修正日時"           TIMESTAMP    NULL,
    "手動修正者アカウントID" BIGINT       NULL,

    "バージョン"             INTEGER      NOT NULL DEFAULT 1,
    "登録者アカウントID"     BIGINT       NULL,
    "更新者アカウントID"     BIGINT       NULL,
    "登録元コード"           VARCHAR(20)  NULL,
    "更新元コード"           VARCHAR(20)  NULL,
    "登録日時"               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "MON_学習モニター画像分析情報_pkey" PRIMARY KEY ("画像分析ID"),
    CONSTRAINT "FK_学習モニター分析_スナップショット"
        FOREIGN KEY ("スナップショットID")
        REFERENCES public."MON_学習モニタースナップショット情報" ("スナップショットID") ON DELETE CASCADE,
    CONSTRAINT "FK_学習モニター分析_手動修正者"
        FOREIGN KEY ("手動修正者アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_学習モニター分析_登録者"
        FOREIGN KEY ("登録者アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "FK_学習モニター分析_更新者"
        FOREIGN KEY ("更新者アカウントID") REFERENCES public."ACC_アカウント" ("アカウントID") ON DELETE RESTRICT,
    CONSTRAINT "CK_学習モニター分析_最新版" CHECK ("最新版フラグ" IN ('0', '1')),
    CONSTRAINT "CK_学習モニター分析_状態"
        CHECK ("分析状態コード" IN ('WAITING', 'RUNNING', 'COMPLETED', 'ERROR')),
    CONSTRAINT "CK_学習モニター分析_一次状態"
        CHECK ("一次分析状態コード" IN ('WAITING', 'RUNNING', 'COMPLETED', 'ERROR')),
    CONSTRAINT "CK_学習モニター分析_二次状態"
        CHECK ("二次分析状態コード" IN ('WAITING', 'RUNNING', 'COMPLETED', 'ERROR', 'NOT_REQUIRED')),
    CONSTRAINT "CK_学習モニター分析_二次要否" CHECK ("二次判定要否" IN ('0', '1')),
    CONSTRAINT "CK_学習モニター分析_採用段階"
        CHECK ("最終採用段階コード" IS NULL OR "最終採用段階コード" IN ('FLASH', 'PLUS', 'MANUAL')),
    -- 判定結果はコードで持つ（表示名はアプリ側）
    CONSTRAINT "CK_学習モニター分析_一次結果"
        CHECK ("一次判定結果コード" IS NULL OR "一次判定結果コード" IN
               ('STUDY_NO_PC', 'STUDY_PC', 'AWAY', 'PC_NON_STUDY', 'OTHER', 'UNKNOWN')),
    CONSTRAINT "CK_学習モニター分析_二次結果"
        CHECK ("二次判定結果コード" IS NULL OR "二次判定結果コード" IN
               ('STUDY_NO_PC', 'STUDY_PC', 'AWAY', 'PC_NON_STUDY', 'OTHER', 'UNKNOWN')),
    CONSTRAINT "CK_学習モニター分析_最終結果"
        CHECK ("最終分析結果コード" IS NULL OR "最終分析結果コード" IN
               ('STUDY_NO_PC', 'STUDY_PC', 'AWAY', 'PC_NON_STUDY', 'OTHER', 'UNKNOWN')),
    CONSTRAINT "CK_学習モニター分析_手動フラグ" CHECK ("手動修正フラグ" IN ('0', '1')),
    CONSTRAINT "CK_学習モニター分析_手動結果"
        CHECK ("手動修正結果コード" IS NULL OR "手動修正結果コード" IN
               ('STUDY_NO_PC', 'STUDY_PC', 'AWAY', 'PC_NON_STUDY', 'OTHER', 'UNKNOWN')),
    -- 手動修正するときは 結果・日時がそろっていること
    -- （理由は画面（API）で必須にする。2.0 の実データには理由が無い行が 883 件あるため、
    --   DB では要求しない）
    CONSTRAINT "CK_学習モニター分析_手動修正"
        CHECK ("手動修正フラグ" = '0'
               OR ("手動修正結果コード" IS NOT NULL AND "手動修正日時" IS NOT NULL)),
    -- 信頼度・閾値は 0〜1
    CONSTRAINT "CK_学習モニター分析_信頼度"
        CHECK (("一次信頼度" IS NULL OR "一次信頼度" BETWEEN 0 AND 1)
               AND ("二次信頼度" IS NULL OR "二次信頼度" BETWEEN 0 AND 1)
               AND ("最終信頼度" IS NULL OR "最終信頼度" BETWEEN 0 AND 1)
               AND "二次判定閾値" BETWEEN 0 AND 1),
    CONSTRAINT "CK_学習モニター分析_バージョン" CHECK ("バージョン" > 0)
);

-- 1 枚の画像につき最新版は 1 件（2.0 と同じ）
CREATE UNIQUE INDEX IF NOT EXISTS uq_mon_analysis_latest
    ON public."MON_学習モニター画像分析情報" ("スナップショットID")
    WHERE "最新版フラグ" = '1';

-- ホームの時間軸（撮影日時の降順）と、画面の絞り込み（状態×結果）
CREATE INDEX IF NOT EXISTS idx_mon_analysis_captured
    ON public."MON_学習モニター画像分析情報" ("撮影日時" DESC)
    WHERE "最新版フラグ" = '1';
CREATE INDEX IF NOT EXISTS idx_mon_analysis_search
    ON public."MON_学習モニター画像分析情報" ("分析状態コード", "最終分析結果コード", "分析完了日時" DESC)
    WHERE "最新版フラグ" = '1';

COMMENT ON TABLE public."MON_学習モニター画像分析情報" IS
    'スナップショットの AI 判定結果（2.0 の STY_学習モニター画像分析情報）。batL03 が書き、画面で手動修正する';
COMMENT ON COLUMN public."MON_学習モニター画像分析情報"."最終分析結果コード" IS
    'STUDY_NO_PC=学習中（PC不使用・読書または筆記）/ STUDY_PC=学習中（PC使用）/ AWAY=離席中 / PC_NON_STUDY=PC使用中（非学習）/ OTHER=その他 / UNKNOWN=判断不可';
COMMENT ON COLUMN public."MON_学習モニター画像分析情報"."最終採用段階コード" IS
    'FLASH=一次判定を採用 / PLUS=二次判定を採用 / MANUAL=手動修正を採用';
COMMENT ON COLUMN public."MON_学習モニター画像分析情報"."最新版フラグ" IS
    '1=最新版（画面はこれだけを見る）/ 0=過去版';
