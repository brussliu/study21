-- ============================================================================
-- Study 2.1  移行: STT（音声認識）の接続情報を「AIモデル」ページへ移す
-- ----------------------------------------------------------------------------
-- 背景: STT は「AI モデルではない」として CLASSROOM_AI ページに専用の接続情報
--       （CLASSROOM_AI_STT_MODEL / _ENDPOINT / _API_KEY）を持っていた。利用者の指示で
--       **Google Speech-to-Text と Alibaba Paraformer-Realtime-V2 を AI モデルと同じ扱い**にし、
--       「AIモデル」ページの専用タブで設定する（provider=google / alibaba はこちらを読む）。
--
-- 追加するもの:
--   1. COM_設定項目 … AI_MODEL の 6 キー（Google / Alibaba の モデル・API Key・URL）
--   2. COM_設定情報 … モデルと URL の既定値（**API Key は seed しない**＝各自で入力）
--   3. 旧キー（CLASSROOM_AI_STT_MODEL / _ENDPOINT）の値を **Google の設定へ引き継ぐ**
--      （新しいキーが未設定のときだけ。設定済みの値を上書きしない）
--
-- 何度流しても同じ（ON CONFLICT DO NOTHING / NOT EXISTS）。旧キーは消さない
-- （provider=whisper / azure / other の互換のため。画面の選択肢からは外れている）。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: database/設定/TBL_COM_設定項目_init.sql の後（同じ内容が入っていれば何もしない）
-- ============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. カタログ（COM_設定項目）
-- ---------------------------------------------------------------------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_GOOGLE_STT_MODEL','TEXT','1',NULL,'AIモデル：Google Speech-to-Text モデル指定（既定 latest_long）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_GOOGLE_STT_API_KEY','TEXT','1',NULL,'AIモデル：Google Speech-to-Text apiKey（Google Cloud の API キー。seed しない）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_GOOGLE_STT_URL','TEXT','1',NULL,'AIモデル：Google Speech-to-Text URL（既定 https://speech.googleapis.com/v1/speech:recognize）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_ALIBABA_STT_MODEL','TEXT','1',NULL,'AIモデル：Alibaba Paraformer-Realtime-V2 モデル指定（既定 paraformer-realtime-v2）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_ALIBABA_STT_API_KEY','TEXT','1',NULL,'AIモデル：Alibaba Paraformer-Realtime-V2 apiKey（DashScope の API Key。千問と同じキーを使える。seed しない）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_ALIBABA_STT_URL','TEXT','1',NULL,'AIモデル：Alibaba Paraformer-Realtime-V2 URL（既定 wss://dashscope.aliyuncs.com/api-ws/v1/inference）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- STT プロバイダーの選択肢を 3 つ（＋ローカル検証用の stub）に整理する。
-- 旧値（whisper / azure / other）もそのまま保存できるように有効値へ残す
-- （残しておかないと、旧値が入っている環境で設定画面が保存できなくなる）。
UPDATE public."COM_設定項目"
   SET "有効値" = 'browser,google,alibaba,stub,whisper,azure,other',
       "説明" = 'STT プロバイダ（browser=ブラウザ音声認識（Chrome/Edge・キー不要・既定） / google=Google Speech-to-Text / alibaba=Alibaba Paraformer-Realtime-V2 / stub=ローカル検証用。whisper・azure・other は旧値の互換）'
 WHERE "ページ区分" = 'CLASSROOM_AI' AND "設定キー" = 'CLASSROOM_AI_STT_PROVIDER';

-- ---------------------------------------------------------------------------
-- 2. 旧キーの値を Google の設定へ引き継ぐ（新しいキーが未設定のときだけ）
--    ※ 既定値の INSERT より**先に**行う（既定値が入った後だと NOT EXISTS で弾かれるため）
-- ---------------------------------------------------------------------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値")
SELECT 'AI_MODEL', 'AI_GOOGLE_STT_MODEL', 'GLOBAL', old."設定値"
  FROM public."COM_設定情報" old
 WHERE old."ページ区分" = 'CLASSROOM_AI'
   AND old."設定キー" = 'CLASSROOM_AI_STT_MODEL'
   AND old."スコープ" = 'GLOBAL'
   AND BTRIM(COALESCE(old."設定値", '')) <> ''
   AND NOT EXISTS (
       SELECT 1 FROM public."COM_設定情報" current
        WHERE current."ページ区分" = 'AI_MODEL'
          AND current."設定キー" = 'AI_GOOGLE_STT_MODEL'
          AND current."スコープ" = 'GLOBAL')
ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値")
SELECT 'AI_MODEL', 'AI_GOOGLE_STT_URL', 'GLOBAL', old."設定値"
  FROM public."COM_設定情報" old
 WHERE old."ページ区分" = 'CLASSROOM_AI'
   AND old."設定キー" = 'CLASSROOM_AI_STT_ENDPOINT'
   AND old."スコープ" = 'GLOBAL'
   AND BTRIM(COALESCE(old."設定値", '')) <> ''
   AND NOT EXISTS (
       SELECT 1 FROM public."COM_設定情報" current
        WHERE current."ページ区分" = 'AI_MODEL'
          AND current."設定キー" = 'AI_GOOGLE_STT_URL'
          AND current."スコープ" = 'GLOBAL')
ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- Google の API Key も引き継ぐ（新キーが未設定のときだけ）
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値")
SELECT 'AI_MODEL', 'AI_GOOGLE_STT_API_KEY', 'GLOBAL', old."設定値"
  FROM public."COM_設定情報" old
 WHERE old."ページ区分" = 'CLASSROOM_AI'
   AND old."設定キー" = 'CLASSROOM_AI_STT_API_KEY'
   AND old."スコープ" = 'GLOBAL'
   AND BTRIM(COALESCE(old."設定値", '')) <> ''
   AND NOT EXISTS (
       SELECT 1 FROM public."COM_設定情報" current
        WHERE current."ページ区分" = 'AI_MODEL'
          AND current."設定キー" = 'AI_GOOGLE_STT_API_KEY'
          AND current."スコープ" = 'GLOBAL')
ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. 既定値（モデルと URL。API Key は seed しない＝各自で入力する）
-- ---------------------------------------------------------------------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_GOOGLE_STT_MODEL','GLOBAL','latest_long') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_GOOGLE_STT_URL','GLOBAL','https://speech.googleapis.com/v1/speech:recognize') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_ALIBABA_STT_MODEL','GLOBAL','paraformer-realtime-v2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_ALIBABA_STT_URL','GLOBAL','wss://dashscope.aliyuncs.com/api-ws/v1/inference') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

COMMIT;

-- ---------------------------------------------------------------------------
-- 確認用
-- ---------------------------------------------------------------------------
--   SELECT "設定キー", "設定値" FROM public."COM_設定情報"
--    WHERE "ページ区分" = 'AI_MODEL' AND "設定キー" LIKE 'AI_%STT_%' ORDER BY 1;
--   -- 期待値: AI_ALIBABA_STT_MODEL=paraformer-realtime-v2 / AI_ALIBABA_STT_URL=wss://dashscope...
--   --         AI_GOOGLE_STT_MODEL=latest_long（旧 CLASSROOM_AI_STT_MODEL があればその値）
--   --         AI_GOOGLE_STT_URL=https://speech.googleapis.com/v1/speech:recognize
--   --         AI_GOOGLE_STT_API_KEY / AI_ALIBABA_STT_API_KEY は設定したときだけ行がある
