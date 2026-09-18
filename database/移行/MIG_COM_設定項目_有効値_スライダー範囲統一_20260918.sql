-- ============================================================================
-- Study 2.1  移行: 設定項目カタログの許容範囲（有効値）を、新しいスライダー範囲に合わせる
-- 対象: COM_設定項目（値タイプ INTEGER の「有効値」= min..max）
-- ----------------------------------------------------------------------------
-- 背景: 設定ページの数字項目をスライダーにし、範囲を利用者の表（tmp/e2e/slider-spec.json）へ揃えた。
--       サーバー（SettingsServiceImpl）は保存時に COM_設定項目.有効値 の範囲で検証し、外れると 400 を返す。
--       カタログが古いままだと、画面で選べるのに保存できない値ができる（表の値 80 も 1..50 の外だった）。
--       ここでは**新しい範囲へ広げる**（狭める行は無い）。
--
-- 何度流しても同じ。
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

BEGIN;

UPDATE public."COM_設定項目" SET "有効値" = '10..200' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C41_BATCH_MAX';  -- c25BatchMax: 1..100 → 10..200
UPDATE public."COM_設定項目" SET "有効値" = '10..200' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C42_BATCH_MAX';  -- c26BatchMax: 1..100 → 10..200
UPDATE public."COM_設定項目" SET "有効値" = '10..200' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C43_BATCH_MAX';  -- c27BatchMax: 1..100 → 10..200
UPDATE public."COM_設定項目" SET "有効値" = '10..200' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C44_BATCH_MAX';  -- c28BatchMax: 1..100 → 10..200
UPDATE public."COM_設定項目" SET "有効値" = '1024..65536' WHERE "ページ区分" = 'CLASSROOM_AI' AND "設定キー" = 'CLASSROOM_AI_NOTE_MAX_COMPLETION_TOKENS';  -- classroomAiNoteMaxCompletionTokens: 512..16384 → 1024..65536
UPDATE public."COM_設定項目" SET "有効値" = '30..600' WHERE "ページ区分" = 'CLASSROOM_AI' AND "設定キー" = 'CLASSROOM_AI_STT_TIMEOUT_SECONDS';  -- classroomAiSttTimeoutSeconds: 5..300 → 30..600
UPDATE public."COM_設定項目" SET "有効値" = '1..100' WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_ASSIST_MAX_COMMANDS';  -- geometryAiAssistMaxCommands: 1..50 → 1..100
UPDATE public."COM_設定項目" SET "有効値" = '30..600' WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_ASSIST_TIMEOUT_SECONDS';  -- geometryAiAssistTimeoutSeconds: 10..300 → 30..600
UPDATE public."COM_設定項目" SET "有効値" = '1024..65536' WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_MAX_COMPLETION_TOKENS';  -- geometryAiMaxCompletionTokens: 1024..16384 → 1024..65536
UPDATE public."COM_設定項目" SET "有効値" = '1..10' WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_MAX_CONCURRENCY';  -- geometryAiMaxConcurrency: 1..4 → 1..10
UPDATE public."COM_設定項目" SET "有効値" = '10..100' WHERE "ページ区分" = 'ENGLISH_READING_INTENSIVE' AND "設定キー" = 'ENGLISH_READING_INTENSIVE_EXPLANATION_BATCH_SIZE';  -- intensiveExplanationBatchSize: 1..50 → 10..100
UPDATE public."COM_設定項目" SET "有効値" = '1..10' WHERE "ページ区分" = 'ENGLISH_READING_INTENSIVE' AND "設定キー" = 'ENGLISH_READING_INTENSIVE_EXPLANATION_THREADS';  -- intensiveExplanationThreads: 1..5 → 1..10

COMMIT;

