-- ============================================================================
-- Study 2.1  学習状況モニターのデータ移行（2.0 → 2.1）
--   移行元: study2 の STY_学習モニターカメラ情報 / STY_学習モニター動画情報 /
--           STY_学習モニタースナップショット情報 / STY_学習モニター画像分析情報
--   移行先: MON_学習モニターカメラ情報 / MON_学習モニター動画情報 /
--           MON_学習モニタースナップショット情報 / MON_学習モニター画像分析情報
--
-- 前提: 移行元・移行先 DB が同一 PostgreSQL インスタンスにあること（dblink で接続）。
--       DDL は先に実行しておくこと（database/学習状況モニター/TBL_MON_学習状況モニター.sql）。
--
-- 主な変換（詳細は database/学習状況モニター/学習状況モニター設計.md）:
--   1. 旧 ID をそのまま使う（動画・スナップショット・分析は 2.0 の ID を維持）。
--   2. 状態 'A'/'X' → '1'/'0'。
--   3. 判定結果の日本語 → コード（STUDY_NO_PC / STUDY_PC / AWAY / PC_NON_STUDY / OTHER / UNKNOWN）。
--   4. 保存パスは絶対パス → **保存ルートからの相対パス**
--      （スナップショット: 20260726/snapshot_000045.jpg ／ 動画: checked/xxx.mp4）。
--   5. 監査は 登録元/更新元コード = 'MIGRATION'、アカウントID は NULL（取り込みはバッチのため）。
--      手動修正だけは 2.0 の 'liu' / 'ljz' を 2.1 のアカウント（1 / 2）に対応付ける。
--
-- 冪等: 既に入っている行は ON CONFLICT DO NOTHING で飛ばす（再実行できる）。
-- 画像・動画の実ファイルは別途コピーする（この SQL の末尾のコメントを参照）。
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS dblink;

-- ---------------------------------------------------------------------------
-- 1. カメラ
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE migr_mon_camera ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study2',
       $remote$
       SELECT "学習モニターカメラID", "ユーザーID", "カメラコード", "カメラ名称", "設置場所",
              "タイムゾーン", "スナップショット間隔秒", "有効フラグ", "登録日時", "更新日時"
         FROM public."STY_学習モニターカメラ情報"
       $remote$
       ) AS remote (
           "学習モニターカメラID" BIGINT,
           "ユーザーID" VARCHAR(64),
           "カメラコード" VARCHAR(30),
           "カメラ名称" VARCHAR(100),
           "設置場所" VARCHAR(200),
           "タイムゾーン" VARCHAR(50),
           "スナップショット間隔秒" INTEGER,
           "有効フラグ" VARCHAR(1),
           "登録日時" TIMESTAMP,
           "更新日時" TIMESTAMP
       );

INSERT INTO public."MON_学習モニターカメラ情報" (
    "カメラID", "アカウントID", "旧ユーザーID", "カメラコード", "カメラ名称", "設置場所",
    "タイムゾーン", "スナップショット間隔秒", "状態", "バージョン",
    "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時"
)
SELECT source."学習モニターカメラID",
       -- 2.0 の ユーザーID '1000' は 2.0 独自の番号で 2.1 のアカウントと対応しない。
       -- 録画しているのは 劉競澤（'ljz' → アカウント 2）なのでそこへ寄せる（要確認）。
       CASE WHEN source."ユーザーID" = '1000' THEN 2 ELSE NULL END,
       source."ユーザーID",
       source."カメラコード", source."カメラ名称", source."設置場所",
       COALESCE(source."タイムゾーン", 'Asia/Tokyo'),
       COALESCE(source."スナップショット間隔秒", 60),
       CASE WHEN source."有効フラグ" = '1' THEN '1' ELSE '0' END,
       1,
       NULL, NULL, 'MIGRATION', 'MIGRATION',
       COALESCE(source."登録日時", CURRENT_TIMESTAMP),
       COALESCE(source."更新日時", CURRENT_TIMESTAMP)
  FROM migr_mon_camera source
 ON CONFLICT ("カメラID") DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. 動画
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE migr_mon_video ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study2',
       $remote$
       SELECT "学習モニター動画ID", "学習モニターカメラID", "撮影開始日時", "撮影終了日時", "撮影日",
              "動画ファイル名", "保存パス", "動画時間秒", "ファイルサイズ", "取込状態", "取込日時",
              "状態", "登録日時", "更新日時"
         FROM public."STY_学習モニター動画情報"
       $remote$
       ) AS remote (
           "学習モニター動画ID" BIGINT,
           "学習モニターカメラID" BIGINT,
           "撮影開始日時" TIMESTAMP,
           "撮影終了日時" TIMESTAMP,
           "撮影日" DATE,
           "動画ファイル名" VARCHAR(255),
           "保存パス" VARCHAR(500),
           "動画時間秒" INTEGER,
           "ファイルサイズ" BIGINT,
           "取込状態" VARCHAR(20),
           "取込日時" TIMESTAMP,
           "状態" VARCHAR(1),
           "登録日時" TIMESTAMP,
           "更新日時" TIMESTAMP
       );

INSERT INTO public."MON_学習モニター動画情報" (
    "動画ID", "カメラID", "撮影日", "撮影開始日時", "撮影終了日時", "動画ファイル名",
    "保存パス", "動画時間秒", "ファイルサイズ", "取込状態コード", "取込日時",
    "状態", "バージョン", "登録者アカウントID", "更新者アカウントID",
    "登録元コード", "更新元コード", "登録日時", "更新日時"
)
SELECT source."学習モニター動画ID", source."学習モニターカメラID", source."撮影日",
       source."撮影開始日時", source."撮影終了日時", source."動画ファイル名",
       -- /usr/local/cemera/<カメラ>/checked/xxx.mp4 → checked/xxx.mp4（動画ルートからの相対）
       NULLIF(regexp_replace(COALESCE(source."保存パス", ''), '^.*/cemera/([^/]+/)?', ''), ''),
       GREATEST(COALESCE(source."動画時間秒", 0), 0),
       source."ファイルサイズ",
       CASE source."取込状態" WHEN 'IMPORTED' THEN 'IMPORTED' WHEN 'ERROR' THEN 'ERROR' ELSE 'WAITING' END,
       COALESCE(source."取込日時", source."登録日時", CURRENT_TIMESTAMP),
       CASE WHEN source."状態" = 'A' THEN '1' ELSE '0' END,
       1, NULL, NULL, 'MIGRATION', 'MIGRATION',
       COALESCE(source."登録日時", CURRENT_TIMESTAMP),
       COALESCE(source."更新日時", CURRENT_TIMESTAMP)
  FROM migr_mon_video source
 WHERE source."撮影終了日時" > source."撮影開始日時"      -- DDL の CHECK に合わない行は入れない
 ON CONFLICT ("動画ID") DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. スナップショット
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE migr_mon_snapshot ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study2',
       $remote$
       SELECT "学習モニタースナップショットID", "学習モニター動画ID", "撮影日時",
              "動画内オフセットミリ秒", "画像ファイル名", "保存パス", "幅", "高さ",
              "切出状態", "状態", "登録日時", "更新日時"
         FROM public."STY_学習モニタースナップショット情報"
       $remote$
       ) AS remote (
           "学習モニタースナップショットID" BIGINT,
           "学習モニター動画ID" BIGINT,
           "撮影日時" TIMESTAMP,
           "動画内オフセットミリ秒" BIGINT,
           "画像ファイル名" VARCHAR(255),
           "保存パス" VARCHAR(500),
           "幅" INTEGER,
           "高さ" INTEGER,
           "切出状態" VARCHAR(20),
           "状態" VARCHAR(1),
           "登録日時" TIMESTAMP,
           "更新日時" TIMESTAMP
       );

INSERT INTO public."MON_学習モニタースナップショット情報" (
    "スナップショットID", "動画ID", "撮影日時", "動画内オフセットミリ秒", "画像ファイル名",
    "保存パス", "幅", "高さ", "切出状態コード", "状態", "バージョン",
    "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時"
)
SELECT source."学習モニタースナップショットID", source."学習モニター動画ID", source."撮影日時",
       GREATEST(COALESCE(source."動画内オフセットミリ秒", 0), 0),
       source."画像ファイル名",
       -- /usr/local/tomcat/webapps/file/snapshots/20260726/snapshot_000045.jpg
       --   → 20260726/snapshot_000045.jpg（スナップショットルートからの相対）
       regexp_replace(COALESCE(source."保存パス", source."画像ファイル名"), '^.*/snapshots/', ''),
       source."幅", source."高さ",
       CASE source."切出状態" WHEN 'WAITING' THEN 'WAITING' WHEN 'ERROR' THEN 'ERROR' ELSE 'CREATED' END,
       CASE WHEN source."状態" = 'A' THEN '1' ELSE '0' END,
       1, NULL, NULL, 'MIGRATION', 'MIGRATION',
       COALESCE(source."登録日時", CURRENT_TIMESTAMP),
       COALESCE(source."更新日時", CURRENT_TIMESTAMP)
  FROM migr_mon_snapshot source
  JOIN public."MON_学習モニター動画情報" video ON video."動画ID" = source."学習モニター動画ID"
 WHERE BTRIM(COALESCE(source."保存パス", source."画像ファイル名", '')) <> ''
   AND NOT EXISTS (
       SELECT 1 FROM public."MON_学習モニタースナップショット情報" existing
        WHERE existing."動画ID" = source."学習モニター動画ID"
          AND existing."動画内オフセットミリ秒" = GREATEST(COALESCE(source."動画内オフセットミリ秒", 0), 0)
   )
 ON CONFLICT ("スナップショットID") DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. 画像分析
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE migr_mon_analysis ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study2',
       $remote$
       SELECT "学習モニター画像分析ID", "学習モニタースナップショットID", "最新版フラグ", "撮影日時",
              "分析状態", "二次判定閾値",
              "一次分析状態", "一次判定結果", "一次信頼度", "一次判定理由",
              "一次AIプロバイダ", "一次AIモデル", "一次プロンプト版",
              "一次分析開始日時", "一次分析完了日時", "一次応答JSON", "一次エラー内容",
              "二次判定要否", "二次分析状態", "二次判定結果", "二次信頼度", "二次詳細推論",
              "二次AIプロバイダ", "二次AIモデル", "二次プロンプト版",
              "二次分析開始日時", "二次分析完了日時", "二次応答JSON", "二次エラー内容",
              "最終採用段階", "最終分析結果", "最終信頼度", "最終判定理由",
              "検出タグJSON", "AIコメント", "分析完了日時",
              "手動修正フラグ", "手動修正結果", "手動修正理由", "手動修正日時", "手動修正ID",
              "登録日時", "更新日時"
         FROM public."STY_学習モニター画像分析情報"
       $remote$
       ) AS remote (
           "学習モニター画像分析ID" BIGINT,
           "学習モニタースナップショットID" BIGINT,
           "最新版フラグ" VARCHAR(1),
           "撮影日時" TIMESTAMP,
           "分析状態" VARCHAR(20),
           "二次判定閾値" NUMERIC(4,3),
           "一次分析状態" VARCHAR(20),
           "一次判定結果" VARCHAR(40),
           "一次信頼度" NUMERIC(4,3),
           "一次判定理由" TEXT,
           "一次AIプロバイダ" VARCHAR(30),
           "一次AIモデル" VARCHAR(100),
           "一次プロンプト版" VARCHAR(50),
           "一次分析開始日時" TIMESTAMP,
           "一次分析完了日時" TIMESTAMP,
           "一次応答JSON" JSONB,
           "一次エラー内容" TEXT,
           "二次判定要否" VARCHAR(1),
           "二次分析状態" VARCHAR(20),
           "二次判定結果" VARCHAR(40),
           "二次信頼度" NUMERIC(4,3),
           "二次詳細推論" TEXT,
           "二次AIプロバイダ" VARCHAR(30),
           "二次AIモデル" VARCHAR(100),
           "二次プロンプト版" VARCHAR(50),
           "二次分析開始日時" TIMESTAMP,
           "二次分析完了日時" TIMESTAMP,
           "二次応答JSON" JSONB,
           "二次エラー内容" TEXT,
           "最終採用段階" VARCHAR(10),
           "最終分析結果" VARCHAR(40),
           "最終信頼度" NUMERIC(4,3),
           "最終判定理由" TEXT,
           "検出タグJSON" JSONB,
           "AIコメント" TEXT,
           "分析完了日時" TIMESTAMP,
           "手動修正フラグ" VARCHAR(1),
           "手動修正結果" VARCHAR(40),
           "手動修正理由" TEXT,
           "手動修正日時" TIMESTAMP,
           "手動修正ID" VARCHAR(64),
           "登録日時" TIMESTAMP,
           "更新日時" TIMESTAMP
       );

INSERT INTO public."MON_学習モニター画像分析情報" (
    "画像分析ID", "スナップショットID", "最新版フラグ", "撮影日時", "分析状態コード", "二次判定閾値",
    "一次分析状態コード", "一次判定結果コード", "一次信頼度", "一次判定理由",
    "一次AIプロバイダ", "一次AIモデル", "一次プロンプト版",
    "一次分析開始日時", "一次分析完了日時", "一次応答JSON", "一次エラー内容",
    "二次判定要否", "二次分析状態コード", "二次判定結果コード", "二次信頼度", "二次詳細推論",
    "二次AIプロバイダ", "二次AIモデル", "二次プロンプト版",
    "二次分析開始日時", "二次分析完了日時", "二次応答JSON", "二次エラー内容",
    "最終採用段階コード", "最終分析結果コード", "最終信頼度", "最終判定理由",
    "検出タグJSON", "AIコメント", "分析完了日時",
    "手動修正フラグ", "手動修正結果コード", "手動修正理由", "手動修正日時", "手動修正者アカウントID",
    "バージョン", "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時"
)
SELECT source."学習モニター画像分析ID", source."学習モニタースナップショットID",
       CASE WHEN source."最新版フラグ" = '1' THEN '1' ELSE '0' END,
       source."撮影日時",
       CASE source."分析状態"
            WHEN 'COMPLETED' THEN 'COMPLETED' WHEN 'RUNNING' THEN 'RUNNING' WHEN 'ERROR' THEN 'ERROR'
            ELSE 'WAITING' END,
       LEAST(GREATEST(COALESCE(source."二次判定閾値", 0.750), 0), 1),
       CASE source."一次分析状態"
            WHEN 'COMPLETED' THEN 'COMPLETED' WHEN 'RUNNING' THEN 'RUNNING' WHEN 'ERROR' THEN 'ERROR'
            ELSE 'WAITING' END,
       CASE source."一次判定結果"
            WHEN '学習中（PC不使用・読書または筆記）' THEN 'STUDY_NO_PC'
            WHEN '学習中（PC使用）' THEN 'STUDY_PC'
            WHEN '離席中' THEN 'AWAY'
            WHEN 'PC使用中（非学習）' THEN 'PC_NON_STUDY'
            WHEN 'その他' THEN 'OTHER'
            WHEN '判断不可' THEN 'UNKNOWN'
            ELSE NULL END,
       source."一次信頼度", source."一次判定理由",
       COALESCE(source."一次AIプロバイダ", 'qwen'), COALESCE(source."一次AIモデル", 'Qwen3-VL-Flash'),
       source."一次プロンプト版", source."一次分析開始日時", source."一次分析完了日時",
       source."一次応答JSON", source."一次エラー内容",
       CASE WHEN source."二次判定要否" = '1' THEN '1' ELSE '0' END,
       CASE source."二次分析状態"
            WHEN 'COMPLETED' THEN 'COMPLETED' WHEN 'RUNNING' THEN 'RUNNING' WHEN 'ERROR' THEN 'ERROR'
            WHEN 'WAITING' THEN 'WAITING' ELSE 'NOT_REQUIRED' END,
       CASE source."二次判定結果"
            WHEN '学習中（PC不使用・読書または筆記）' THEN 'STUDY_NO_PC'
            WHEN '学習中（PC使用）' THEN 'STUDY_PC'
            WHEN '離席中' THEN 'AWAY'
            WHEN 'PC使用中（非学習）' THEN 'PC_NON_STUDY'
            WHEN 'その他' THEN 'OTHER'
            WHEN '判断不可' THEN 'UNKNOWN'
            ELSE NULL END,
       source."二次信頼度", source."二次詳細推論",
       source."二次AIプロバイダ", COALESCE(source."二次AIモデル", 'Qwen3-VL-Plus'),
       source."二次プロンプト版", source."二次分析開始日時", source."二次分析完了日時",
       source."二次応答JSON", source."二次エラー内容",
       CASE source."最終採用段階" WHEN 'FLASH' THEN 'FLASH' WHEN 'PLUS' THEN 'PLUS' WHEN 'MANUAL' THEN 'MANUAL'
            ELSE NULL END,
       CASE source."最終分析結果"
            WHEN '学習中（PC不使用・読書または筆記）' THEN 'STUDY_NO_PC'
            WHEN '学習中（PC使用）' THEN 'STUDY_PC'
            WHEN '離席中' THEN 'AWAY'
            WHEN 'PC使用中（非学習）' THEN 'PC_NON_STUDY'
            WHEN 'その他' THEN 'OTHER'
            WHEN '判断不可' THEN 'UNKNOWN'
            ELSE NULL END,
       source."最終信頼度", source."最終判定理由",
       COALESCE(source."検出タグJSON", '[]'::jsonb), source."AIコメント", source."分析完了日時",
       CASE WHEN source."手動修正フラグ" = '1' THEN '1' ELSE '0' END,
       CASE source."手動修正結果"
            WHEN '学習中（PC不使用・読書または筆記）' THEN 'STUDY_NO_PC'
            WHEN '学習中（PC使用）' THEN 'STUDY_PC'
            WHEN '離席中' THEN 'AWAY'
            WHEN 'PC使用中（非学習）' THEN 'PC_NON_STUDY'
            WHEN 'その他' THEN 'OTHER'
            WHEN '判断不可' THEN 'UNKNOWN'
            ELSE NULL END,
       source."手動修正理由", source."手動修正日時",
       -- 2.0 の 手動修正ID（'liu' / 'ljz'）を 2.1 のアカウントへ
       CASE source."手動修正ID" WHEN 'liu' THEN 1 WHEN 'ljz' THEN 2 ELSE NULL END,
       1, NULL, NULL, 'MIGRATION', 'MIGRATION',
       COALESCE(source."登録日時", CURRENT_TIMESTAMP),
       COALESCE(source."更新日時", CURRENT_TIMESTAMP)
  FROM migr_mon_analysis source
  JOIN public."MON_学習モニタースナップショット情報" snapshot
    ON snapshot."スナップショットID" = source."学習モニタースナップショットID"
 ON CONFLICT ("画像分析ID") DO NOTHING;

COMMIT;

-- ---------------------------------------------------------------------------
-- 確認用
-- ---------------------------------------------------------------------------
--   SELECT (SELECT count(*) FROM public."MON_学習モニターカメラ情報") AS カメラ,
--          (SELECT count(*) FROM public."MON_学習モニター動画情報") AS 動画,
--          (SELECT count(*) FROM public."MON_学習モニタースナップショット情報") AS スナップショット,
--          (SELECT count(*) FROM public."MON_学習モニター画像分析情報") AS 分析;
--   -- 期待値: 1 / 2777 / 43326 / 43326
--
--   SELECT "最終分析結果コード", count(*) FROM public."MON_学習モニター画像分析情報"
--    WHERE "最新版フラグ" = '1' GROUP BY 1 ORDER BY 2 DESC;
--
-- ---------------------------------------------------------------------------
-- 実ファイル（画像）のコピー ※この SQL では行わない
-- ---------------------------------------------------------------------------
-- 2.0 のスナップショットは 2.0 サーバーの
--   <catalina.base>/webapps/file/snapshots/<YYYYMMDD>/snapshot_XXXXXX.jpg
-- にある（実データの保存パスは /usr/local/tomcat/webapps/file/snapshots/...）。
-- 2.1 では**保存ルートからの相対パス**（20260726/snapshot_000045.jpg）で持つので、
-- 2.0 サーバーで次のようにコピーし、2.1 の保存ルートへ置く。
--
--   # 2.0 サーバー側（コピー元）
--   tar czf /tmp/snapshots.tgz -C /usr/local/tomcat/webapps/file snapshots
--   # 2.1 側（保存ルートの下に snapshots を置く）
--   tar xzf /tmp/snapshots.tgz -C "$STUDY21_STUDY_MONITOR_SNAPSHOT_ROOT"
--
-- 動画（録画ファイル）は容量が大きいのでコピーしない。2.1 は動画ルート
-- （既定: /vol5/1000/摄像头监控/XiaomiCamera_00_B88880D0F03E）から読む。
