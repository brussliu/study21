-- ============================================================================
-- Study 2.1  移行: 設定ページのスライダー項目の値を、利用者が指定した値に揃える
-- 対象: COM_設定情報（GLOBAL スコープ）／AI を呼ぶブロックと、その周辺の数字項目
-- ----------------------------------------------------------------------------
-- 背景: 設定ページの数字項目をすべてスライダーにし（2026-09-18）、最小・最大・刻み・単位を
--       利用者の表（tmp/e2e/slider-spec.json）へ揃えた。あわせて、その表の「DB 保存值」列の値を
--       実際の設定値として書き込む（表の値はすべて新しい範囲内・刻み上にあることを確認済み）。
--
-- 何度流しても同じ（同じ値を入れるだけ。値の履歴は残さない）。
-- キーは画面の camelCase ではなく DB の設定キー（SettingPageFields.java の対応表で変換）。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: database/設定/TBL_COM_設定情報_init.sql の後
-- ============================================================================

BEGIN;

-- ---------------- CLASSROOM_AI ----------------
UPDATE public."COM_設定情報" SET "設定値" = '5' WHERE "ページ区分" = 'CLASSROOM_AI' AND "設定キー" = 'CLASSROOM_AI_CHUNK_SECONDS' AND "スコープ" = 'GLOBAL';  -- classroomAiChunkSeconds（5〜120 / 刻み 5s）
UPDATE public."COM_設定情報" SET "設定値" = '100' WHERE "ページ区分" = 'CLASSROOM_AI' AND "設定キー" = 'CLASSROOM_AI_DAILY_LIMIT_PER_ACCOUNT' AND "スコープ" = 'GLOBAL';  -- classroomAiDailyLimitPerAccount（0〜100 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '120' WHERE "ページ区分" = 'CLASSROOM_AI' AND "設定キー" = 'CLASSROOM_AI_MAX_RECORDING_MINUTES' AND "スコープ" = 'GLOBAL';  -- classroomAiMaxRecordingMinutes（1〜240 / 刻み 1分）
UPDATE public."COM_設定情報" SET "設定値" = '8192' WHERE "ページ区分" = 'CLASSROOM_AI' AND "設定キー" = 'CLASSROOM_AI_NOTE_MAX_COMPLETION_TOKENS' AND "スコープ" = 'GLOBAL';  -- classroomAiNoteMaxCompletionTokens（1024〜65536 / 刻み 512）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'CLASSROOM_AI' AND "設定キー" = 'CLASSROOM_AI_NOTE_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- classroomAiNoteTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '365' WHERE "ページ区分" = 'CLASSROOM_AI' AND "設定キー" = 'CLASSROOM_AI_RETENTION_DAYS' AND "スコープ" = 'GLOBAL';  -- classroomAiRetentionDays（1〜365 / 刻み 1日）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'CLASSROOM_AI' AND "設定キー" = 'CLASSROOM_AI_STT_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- classroomAiSttTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '3' WHERE "ページ区分" = 'CLASSROOM_AI' AND "設定キー" = 'CLASSROOM_AI_TRIGGER_COOLDOWN_MINUTES' AND "スコープ" = 'GLOBAL';  -- classroomAiTriggerCooldownMinutes（1〜30 / 刻み 1分）
UPDATE public."COM_設定情報" SET "設定値" = '5' WHERE "ページ区分" = 'CLASSROOM_AI' AND "設定キー" = 'CLASSROOM_AI_TRIGGER_INTERVAL_MINUTES' AND "スコープ" = 'GLOBAL';  -- classroomAiTriggerIntervalMinutes（3〜20 / 刻み 1分）
UPDATE public."COM_設定情報" SET "設定値" = '200' WHERE "ページ区分" = 'CLASSROOM_AI' AND "設定キー" = 'CLASSROOM_AI_TRIGGER_MIN_CHARS' AND "スコープ" = 'GLOBAL';  -- classroomAiTriggerMinChars（100〜10000 / 刻み 100文字）
-- ---------------- ENGLISH_CLOZE ----------------
UPDATE public."COM_設定情報" SET "設定値" = '10' WHERE "ページ区分" = 'ENGLISH_CLOZE' AND "設定キー" = 'ENGLISH_CLOZE_EXPLANATION_BATCH_MAX' AND "スコープ" = 'GLOBAL';  -- clozeExplanationBatchMax（10〜100 / 刻み 10問）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'ENGLISH_CLOZE' AND "設定キー" = 'ENGLISH_CLOZE_EXPLANATION_REQUEST_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- clozeExplanationRequestTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '0' WHERE "ページ区分" = 'ENGLISH_CLOZE' AND "設定キー" = 'ENGLISH_CLOZE_EXPLANATION_RETRY_LIMIT' AND "スコープ" = 'GLOBAL';  -- clozeExplanationRetryLimit（0〜5 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'ENGLISH_CLOZE' AND "設定キー" = 'ENGLISH_CLOZE_EXPLANATION_THREADS' AND "スコープ" = 'GLOBAL';  -- clozeExplanationThreads（1〜10 / 刻み 1）
UPDATE public."COM_設定情報" SET "設定値" = '10' WHERE "ページ区分" = 'ENGLISH_CLOZE' AND "設定キー" = 'ENGLISH_CLOZE_MAX_IMAGES' AND "スコープ" = 'GLOBAL';  -- clozeMaxImages（1〜20 / 刻み 1枚）
UPDATE public."COM_設定情報" SET "設定値" = '10' WHERE "ページ区分" = 'ENGLISH_CLOZE' AND "設定キー" = 'ENGLISH_CLOZE_MAX_IMAGE_MB' AND "スコープ" = 'GLOBAL';  -- clozeMaxImageMb（1〜50 / 刻み 1MB）
UPDATE public."COM_設定情報" SET "設定値" = '2048' WHERE "ページ区分" = 'ENGLISH_CLOZE' AND "設定キー" = 'ENGLISH_CLOZE_OCR_MAX_IMAGE_PIXELS' AND "スコープ" = 'GLOBAL';  -- clozeOcrMaxImagePixels（1024〜8192 / 刻み 512px）
UPDATE public."COM_設定情報" SET "設定値" = '0' WHERE "ページ区分" = 'ENGLISH_CLOZE' AND "設定キー" = 'ENGLISH_CLOZE_OCR_RETRY_LIMIT' AND "スコープ" = 'GLOBAL';  -- clozeOcrRetryLimit（0〜5 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'ENGLISH_CLOZE' AND "設定キー" = 'ENGLISH_CLOZE_OCR_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- clozeOcrTimeoutSeconds（30〜600 / 刻み 30s）
-- ---------------- ENGLISH_ESSAY ----------------
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'ENGLISH_ESSAY' AND "設定キー" = 'ENGLISH_ESSAY_GRADING_REQUEST_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- essayGradingRequestTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '0' WHERE "ページ区分" = 'ENGLISH_ESSAY' AND "設定キー" = 'ENGLISH_ESSAY_GRADING_RETRY_LIMIT' AND "スコープ" = 'GLOBAL';  -- essayGradingRetryLimit（0〜5 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '10' WHERE "ページ区分" = 'ENGLISH_ESSAY' AND "設定キー" = 'ENGLISH_ESSAY_MAX_IMAGES' AND "スコープ" = 'GLOBAL';  -- essayMaxImages（1〜20 / 刻み 1枚）
UPDATE public."COM_設定情報" SET "設定値" = '10' WHERE "ページ区分" = 'ENGLISH_ESSAY' AND "設定キー" = 'ENGLISH_ESSAY_MAX_IMAGE_MB' AND "スコープ" = 'GLOBAL';  -- essayMaxImageMb（1〜50 / 刻み 1MB）
UPDATE public."COM_設定情報" SET "設定値" = '2048' WHERE "ページ区分" = 'ENGLISH_ESSAY' AND "設定キー" = 'ENGLISH_ESSAY_OCR_MAX_IMAGE_PIXELS' AND "スコープ" = 'GLOBAL';  -- essayOcrMaxImagePixels（1024〜8192 / 刻み 512px）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'ENGLISH_ESSAY' AND "設定キー" = 'ENGLISH_ESSAY_OCR_REQUEST_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- essayOcrRequestTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '0' WHERE "ページ区分" = 'ENGLISH_ESSAY' AND "設定キー" = 'ENGLISH_ESSAY_OCR_RETRY_LIMIT' AND "スコープ" = 'GLOBAL';  -- essayOcrRetryLimit（0〜5 / 刻み 1回）
-- ---------------- ENGLISH_PHRASE_DETAIL_AI ----------------
UPDATE public."COM_設定情報" SET "設定値" = '100' WHERE "ページ区分" = 'ENGLISH_PHRASE_DETAIL_AI' AND "設定キー" = 'ENGLISH_PHRASE_DETAIL_AI_BATCH_SIZE' AND "スコープ" = 'GLOBAL';  -- phraseDetailAiBatchSize（10〜200 / 刻み 10語）
UPDATE public."COM_設定情報" SET "設定値" = '20480' WHERE "ページ区分" = 'ENGLISH_PHRASE_DETAIL_AI' AND "設定キー" = 'ENGLISH_PHRASE_DETAIL_AI_MAX_COMPLETION_TOKENS' AND "スコープ" = 'GLOBAL';  -- phraseDetailAiMaxCompletionTokens（1024〜65536 / 刻み 512）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'ENGLISH_PHRASE_DETAIL_AI' AND "設定キー" = 'ENGLISH_PHRASE_DETAIL_AI_REQUEST_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- phraseDetailAiRequestTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '0' WHERE "ページ区分" = 'ENGLISH_PHRASE_DETAIL_AI' AND "設定キー" = 'ENGLISH_PHRASE_DETAIL_AI_RETRY_LIMIT' AND "スコープ" = 'GLOBAL';  -- phraseDetailAiRetryLimit（0〜5 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '0.2' WHERE "ページ区分" = 'ENGLISH_PHRASE_DETAIL_AI' AND "設定キー" = 'ENGLISH_PHRASE_DETAIL_AI_TEMPERATURE' AND "スコープ" = 'GLOBAL';  -- phraseDetailAiTemperature（0〜2 / 刻み 0.1）
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'ENGLISH_PHRASE_DETAIL_AI' AND "設定キー" = 'ENGLISH_PHRASE_DETAIL_AI_THREADS' AND "スコープ" = 'GLOBAL';  -- phraseDetailAiThreads（1〜10 / 刻み 1）
UPDATE public."COM_設定情報" SET "設定値" = '100' WHERE "ページ区分" = 'ENGLISH_PHRASE_DETAIL_AI' AND "設定キー" = 'ENGLISH_PHRASE_STANDARDIZATION_AI_BATCH_SIZE' AND "スコープ" = 'GLOBAL';  -- phraseStandardizationAiBatchSize（10〜200 / 刻み 10語）
UPDATE public."COM_設定情報" SET "設定値" = '8192' WHERE "ページ区分" = 'ENGLISH_PHRASE_DETAIL_AI' AND "設定キー" = 'ENGLISH_PHRASE_STANDARDIZATION_AI_MAX_COMPLETION_TOKENS' AND "スコープ" = 'GLOBAL';  -- phraseStandardizationAiMaxCompletionTokens（1024〜65536 / 刻み 512）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'ENGLISH_PHRASE_DETAIL_AI' AND "設定キー" = 'ENGLISH_PHRASE_STANDARDIZATION_AI_REQUEST_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- phraseStandardizationAiRequestTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '0' WHERE "ページ区分" = 'ENGLISH_PHRASE_DETAIL_AI' AND "設定キー" = 'ENGLISH_PHRASE_STANDARDIZATION_AI_RETRY_LIMIT' AND "スコープ" = 'GLOBAL';  -- phraseStandardizationAiRetryLimit（0〜5 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '0.2' WHERE "ページ区分" = 'ENGLISH_PHRASE_DETAIL_AI' AND "設定キー" = 'ENGLISH_PHRASE_STANDARDIZATION_AI_TEMPERATURE' AND "スコープ" = 'GLOBAL';  -- phraseStandardizationAiTemperature（0〜2 / 刻み 0.1）
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'ENGLISH_PHRASE_DETAIL_AI' AND "設定キー" = 'ENGLISH_PHRASE_STANDARDIZATION_AI_THREADS' AND "スコープ" = 'GLOBAL';  -- phraseStandardizationAiThreads（1〜10 / 刻み 1）
-- ---------------- ENGLISH_READING_INTENSIVE ----------------
UPDATE public."COM_設定情報" SET "設定値" = '10' WHERE "ページ区分" = 'ENGLISH_READING_INTENSIVE' AND "設定キー" = 'ENGLISH_READING_INTENSIVE_EXPLANATION_BATCH_SIZE' AND "スコープ" = 'GLOBAL';  -- intensiveExplanationBatchSize（10〜100 / 刻み 10文）
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'ENGLISH_READING_INTENSIVE' AND "設定キー" = 'ENGLISH_READING_INTENSIVE_EXPLANATION_THREADS' AND "スコープ" = 'GLOBAL';  -- intensiveExplanationThreads（1〜10 / 刻み 1）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'ENGLISH_READING_INTENSIVE' AND "設定キー" = 'ENGLISH_READING_INTENSIVE_EXPLANATION_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- intensiveExplanationTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'ENGLISH_READING_INTENSIVE' AND "設定キー" = 'ENGLISH_READING_INTENSIVE_GUIDE_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- intensiveGuideTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '10' WHERE "ページ区分" = 'ENGLISH_READING_INTENSIVE' AND "設定キー" = 'ENGLISH_READING_INTENSIVE_MAX_IMAGES' AND "スコープ" = 'GLOBAL';  -- intensiveMaxImages（1〜20 / 刻み 1枚）
UPDATE public."COM_設定情報" SET "設定値" = '10' WHERE "ページ区分" = 'ENGLISH_READING_INTENSIVE' AND "設定キー" = 'ENGLISH_READING_INTENSIVE_MAX_IMAGE_MB' AND "スコープ" = 'GLOBAL';  -- intensiveMaxImageMb（1〜50 / 刻み 1MB）
UPDATE public."COM_設定情報" SET "設定値" = '2048' WHERE "ページ区分" = 'ENGLISH_READING_INTENSIVE' AND "設定キー" = 'ENGLISH_READING_INTENSIVE_OCR_MAX_IMAGE_PIXELS' AND "スコープ" = 'GLOBAL';  -- intensiveOcrMaxImagePixels（1024〜8192 / 刻み 512px）
UPDATE public."COM_設定情報" SET "設定値" = '0' WHERE "ページ区分" = 'ENGLISH_READING_INTENSIVE' AND "設定キー" = 'ENGLISH_READING_INTENSIVE_OCR_MAX_RETRIES' AND "スコープ" = 'GLOBAL';  -- intensiveOcrMaxRetries（0〜5 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'ENGLISH_READING_INTENSIVE' AND "設定キー" = 'ENGLISH_READING_INTENSIVE_OCR_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- intensiveOcrTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'ENGLISH_READING_INTENSIVE' AND "設定キー" = 'ENGLISH_READING_INTENSIVE_QUESTION_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- intensiveQuestionTimeoutSeconds（30〜600 / 刻み 30s）
-- ---------------- ENGLISH_WORD_DETAIL_AI ----------------
UPDATE public."COM_設定情報" SET "設定値" = '100' WHERE "ページ区分" = 'ENGLISH_WORD_DETAIL_AI' AND "設定キー" = 'ENGLISH_WORD_DETAIL_AI_BATCH_SIZE' AND "スコープ" = 'GLOBAL';  -- wordDetailAiBatchSize（10〜200 / 刻み 10語）
UPDATE public."COM_設定情報" SET "設定値" = '20480' WHERE "ページ区分" = 'ENGLISH_WORD_DETAIL_AI' AND "設定キー" = 'ENGLISH_WORD_DETAIL_AI_MAX_COMPLETION_TOKENS' AND "スコープ" = 'GLOBAL';  -- wordDetailAiMaxCompletionTokens（1024〜65536 / 刻み 512）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'ENGLISH_WORD_DETAIL_AI' AND "設定キー" = 'ENGLISH_WORD_DETAIL_AI_REQUEST_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- wordDetailAiRequestTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '0' WHERE "ページ区分" = 'ENGLISH_WORD_DETAIL_AI' AND "設定キー" = 'ENGLISH_WORD_DETAIL_AI_RETRY_LIMIT' AND "スコープ" = 'GLOBAL';  -- wordDetailAiRetryLimit（0〜5 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '0.2' WHERE "ページ区分" = 'ENGLISH_WORD_DETAIL_AI' AND "設定キー" = 'ENGLISH_WORD_DETAIL_AI_TEMPERATURE' AND "スコープ" = 'GLOBAL';  -- wordDetailAiTemperature（0〜2 / 刻み 0.1）
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'ENGLISH_WORD_DETAIL_AI' AND "設定キー" = 'ENGLISH_WORD_DETAIL_AI_THREADS' AND "スコープ" = 'GLOBAL';  -- wordDetailAiThreads（1〜10 / 刻み 1）
-- ---------------- GEOMETRY_AI ----------------
UPDATE public."COM_設定情報" SET "設定値" = '100' WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_ASSIST_DAILY_LIMIT_PER_ACCOUNT' AND "スコープ" = 'GLOBAL';  -- geometryAiAssistDailyLimitPerAccount（0〜100 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '80' WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_ASSIST_MAX_COMMANDS' AND "スコープ" = 'GLOBAL';  -- geometryAiAssistMaxCommands（1〜100 / 刻み 1個）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_ASSIST_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- geometryAiAssistTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '100' WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_DAILY_LIMIT_PER_ACCOUNT' AND "スコープ" = 'GLOBAL';  -- geometryAiDailyLimitPerAccount（0〜100 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '365' WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_IMAGE_RETENTION_DAYS' AND "スコープ" = 'GLOBAL';  -- geometryAiImageRetentionDays（1〜365 / 刻み 1日）
UPDATE public."COM_設定情報" SET "設定値" = '80' WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_MAX_COMMANDS' AND "スコープ" = 'GLOBAL';  -- geometryAiMaxCommands（1〜100 / 刻み 1個）
UPDATE public."COM_設定情報" SET "設定値" = '8192' WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_MAX_COMPLETION_TOKENS' AND "スコープ" = 'GLOBAL';  -- geometryAiMaxCompletionTokens（1024〜65536 / 刻み 512）
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_MAX_CONCURRENCY' AND "スコープ" = 'GLOBAL';  -- geometryAiMaxConcurrency（1〜10 / 刻み 1）
UPDATE public."COM_設定情報" SET "設定値" = '10' WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_MAX_IMAGE_MB' AND "スコープ" = 'GLOBAL';  -- geometryAiMaxImageMb（1〜50 / 刻み 1MB）
UPDATE public."COM_設定情報" SET "設定値" = '2048' WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_MAX_IMAGE_PIXELS' AND "スコープ" = 'GLOBAL';  -- geometryAiMaxImagePixels（1024〜8192 / 刻み 512px）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_REQUEST_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- geometryAiRequestTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '0' WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_RETRY_LIMIT' AND "スコープ" = 'GLOBAL';  -- geometryAiRetryLimit（0〜3 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '0.2' WHERE "ページ区分" = 'GEOMETRY_AI' AND "設定キー" = 'GEOMETRY_AI_TEMPERATURE' AND "スコープ" = 'GLOBAL';  -- geometryAiTemperature（0〜2 / 刻み 0.1）
-- ---------------- JAPANESE_WORD_AI ----------------
UPDATE public."COM_設定情報" SET "設定値" = '100' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C41_BATCH_MAX' AND "スコープ" = 'GLOBAL';  -- c25BatchMax（10〜200 / 刻み 10語）
UPDATE public."COM_設定情報" SET "設定値" = '20480' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C41_MAX_COMPLETION_TOKENS' AND "スコープ" = 'GLOBAL';  -- c25MaxCompletionTokens（1024〜65536 / 刻み 512）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C41_REQUEST_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- c25RequestTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '0' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C41_RETRY_LIMIT' AND "スコープ" = 'GLOBAL';  -- c25RetryLimit（0〜5 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '0.2' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C41_TEMPERATURE' AND "スコープ" = 'GLOBAL';  -- c25Temperature（0〜2 / 刻み 0.1）
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C41_THREADS' AND "スコープ" = 'GLOBAL';  -- c25Threads（1〜10 / 刻み 1）
UPDATE public."COM_設定情報" SET "設定値" = '100' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C42_BATCH_MAX' AND "スコープ" = 'GLOBAL';  -- c26BatchMax（10〜200 / 刻み 10語）
UPDATE public."COM_設定情報" SET "設定値" = '8192' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C42_MAX_COMPLETION_TOKENS' AND "スコープ" = 'GLOBAL';  -- c26MaxCompletionTokens（1024〜65536 / 刻み 512）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C42_REQUEST_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- c26RequestTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '0' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C42_RETRY_LIMIT' AND "スコープ" = 'GLOBAL';  -- c26RetryLimit（0〜5 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '0.2' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C42_TEMPERATURE' AND "スコープ" = 'GLOBAL';  -- c26Temperature（0〜2 / 刻み 0.1）
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C42_THREADS' AND "スコープ" = 'GLOBAL';  -- c26Threads（1〜10 / 刻み 1）
UPDATE public."COM_設定情報" SET "設定値" = '100' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C43_BATCH_MAX' AND "スコープ" = 'GLOBAL';  -- c27BatchMax（10〜200 / 刻み 10語）
UPDATE public."COM_設定情報" SET "設定値" = '8192' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C43_MAX_COMPLETION_TOKENS' AND "スコープ" = 'GLOBAL';  -- c27MaxCompletionTokens（1024〜65536 / 刻み 512）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C43_REQUEST_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- c27RequestTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '0' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C43_RETRY_LIMIT' AND "スコープ" = 'GLOBAL';  -- c27RetryLimit（0〜5 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '0.2' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C43_TEMPERATURE' AND "スコープ" = 'GLOBAL';  -- c27Temperature（0〜2 / 刻み 0.1）
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C43_THREADS' AND "スコープ" = 'GLOBAL';  -- c27Threads（1〜10 / 刻み 1）
UPDATE public."COM_設定情報" SET "設定値" = '100' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C44_BATCH_MAX' AND "スコープ" = 'GLOBAL';  -- c28BatchMax（10〜200 / 刻み 10語）
UPDATE public."COM_設定情報" SET "設定値" = '8192' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C44_MAX_COMPLETION_TOKENS' AND "スコープ" = 'GLOBAL';  -- c28MaxCompletionTokens（1024〜65536 / 刻み 512）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C44_REQUEST_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- c28RequestTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '0' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C44_RETRY_LIMIT' AND "スコープ" = 'GLOBAL';  -- c28RetryLimit（0〜5 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '0.2' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C44_TEMPERATURE' AND "スコープ" = 'GLOBAL';  -- c28Temperature（0〜2 / 刻み 0.1）
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'JAPANESE_WORD_AI' AND "設定キー" = 'BAT_C44_THREADS' AND "スコープ" = 'GLOBAL';  -- c28Threads（1〜10 / 刻み 1）
-- ---------------- STUDY_MONITOR ----------------
UPDATE public."COM_設定情報" SET "設定値" = '10' WHERE "ページ区分" = 'STUDY_MONITOR' AND "設定キー" = 'STUDY_MONITOR_AI_BATCH_LIMIT' AND "スコープ" = 'GLOBAL';  -- monitorAiBatchLimit（10〜100 / 刻み 10枚）
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'STUDY_MONITOR' AND "設定キー" = 'STUDY_MONITOR_AI_THREADS' AND "スコープ" = 'GLOBAL';  -- monitorAiThreads（1〜10 / 刻み 1）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'STUDY_MONITOR' AND "設定キー" = 'STUDY_MONITOR_AI_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- monitorAiTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '60' WHERE "ページ区分" = 'STUDY_MONITOR' AND "設定キー" = 'STUDY_MONITOR_SNAPSHOT_INTERVAL_SECONDS' AND "スコープ" = 'GLOBAL';  -- monitorSnapshotIntervalSeconds（10〜600 / 刻み 10秒）
-- ---------------- TRANSLATION ----------------
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'TRANSLATION' AND "設定キー" = 'JA_TRANSLATE_THREADS' AND "スコープ" = 'GLOBAL';  -- jaThreads（1〜10 / 刻み 1）
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'TRANSLATION' AND "設定キー" = 'ZH_TRANSLATE_THREADS' AND "スコープ" = 'GLOBAL';  -- zhThreads（1〜10 / 刻み 1）
-- ---------------- VOICE ----------------
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'VOICE' AND "設定キー" = 'EN_VOICE_THREADS' AND "スコープ" = 'GLOBAL';  -- enVoiceThreads（1〜10 / 刻み 1）
-- ---------------- WORD_EXPLANATION ----------------
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'WORD_EXPLANATION' AND "設定キー" = 'BAT_C05_THREADS' AND "スコープ" = 'GLOBAL';  -- c05Threads（1〜10 / 刻み 1）
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'WORD_EXPLANATION' AND "設定キー" = 'BAT_C06_THREADS' AND "スコープ" = 'GLOBAL';  -- c06Threads（1〜10 / 刻み 1）
-- ---------------- WORD_QUESTION ----------------
UPDATE public."COM_設定情報" SET "設定値" = '100' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C04_2_BATCH_SIZE' AND "スコープ" = 'GLOBAL';  -- c042BatchSize（10〜200 / 刻み 10語）
UPDATE public."COM_設定情報" SET "設定値" = '8192' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C04_2_MAX_COMPLETION_TOKENS' AND "スコープ" = 'GLOBAL';  -- c042MaxCompletionTokens（1024〜65536 / 刻み 512）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C04_2_REQUEST_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- c042RequestTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '0' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C04_2_RETRY_LIMIT' AND "スコープ" = 'GLOBAL';  -- c042RetryLimit（0〜5 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '0.2' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C04_2_TEMPERATURE' AND "スコープ" = 'GLOBAL';  -- c042Temperature（0〜2 / 刻み 0.1）
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C04_2_THREADS' AND "スコープ" = 'GLOBAL';  -- c042Threads（1〜10 / 刻み 1）
UPDATE public."COM_設定情報" SET "設定値" = '100' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C04_3_BATCH_SIZE' AND "スコープ" = 'GLOBAL';  -- c043BatchSize（10〜200 / 刻み 10語）
UPDATE public."COM_設定情報" SET "設定値" = '8192' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C04_3_MAX_COMPLETION_TOKENS' AND "スコープ" = 'GLOBAL';  -- c043MaxCompletionTokens（1024〜65536 / 刻み 512）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C04_3_REQUEST_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- c043RequestTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '0' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C04_3_RETRY_LIMIT' AND "スコープ" = 'GLOBAL';  -- c043RetryLimit（0〜5 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '0.2' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C04_3_TEMPERATURE' AND "スコープ" = 'GLOBAL';  -- c043Temperature（0〜2 / 刻み 0.1）
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C04_3_THREADS' AND "スコープ" = 'GLOBAL';  -- c043Threads（1〜10 / 刻み 1）
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C04_THREADS' AND "スコープ" = 'GLOBAL';  -- c04Threads（1〜10 / 刻み 1）
UPDATE public."COM_設定情報" SET "設定値" = '100' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C33_BATCH_SIZE' AND "スコープ" = 'GLOBAL';  -- c23BatchSize（10〜200 / 刻み 10語）
UPDATE public."COM_設定情報" SET "設定値" = '8192' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C33_MAX_COMPLETION_TOKENS' AND "スコープ" = 'GLOBAL';  -- c23MaxCompletionTokens（1024〜65536 / 刻み 512）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C33_REQUEST_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- c23RequestTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '0' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C33_RETRY_LIMIT' AND "スコープ" = 'GLOBAL';  -- c23RetryLimit（0〜5 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '0.2' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C33_TEMPERATURE' AND "スコープ" = 'GLOBAL';  -- c23Temperature（0〜2 / 刻み 0.1）
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C33_THREADS' AND "スコープ" = 'GLOBAL';  -- c23Threads（1〜10 / 刻み 1）
UPDATE public."COM_設定情報" SET "設定値" = '100' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C34_BATCH_SIZE' AND "スコープ" = 'GLOBAL';  -- c24BatchSize（10〜200 / 刻み 10語）
UPDATE public."COM_設定情報" SET "設定値" = '8192' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C34_MAX_COMPLETION_TOKENS' AND "スコープ" = 'GLOBAL';  -- c24MaxCompletionTokens（1024〜65536 / 刻み 512）
UPDATE public."COM_設定情報" SET "設定値" = '300' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C34_REQUEST_TIMEOUT_SECONDS' AND "スコープ" = 'GLOBAL';  -- c24RequestTimeoutSeconds（30〜600 / 刻み 30s）
UPDATE public."COM_設定情報" SET "設定値" = '0' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C34_RETRY_LIMIT' AND "スコープ" = 'GLOBAL';  -- c24RetryLimit（0〜5 / 刻み 1回）
UPDATE public."COM_設定情報" SET "設定値" = '0.2' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C34_TEMPERATURE' AND "スコープ" = 'GLOBAL';  -- c24Temperature（0〜2 / 刻み 0.1）
UPDATE public."COM_設定情報" SET "設定値" = '2' WHERE "ページ区分" = 'WORD_QUESTION' AND "設定キー" = 'BAT_C34_THREADS' AND "スコープ" = 'GLOBAL';  -- c24Threads（1〜10 / 刻み 1）

COMMIT;

-- 確認用:
--   SELECT "ページ区分", "設定キー", "設定値" FROM public."COM_設定情報"
--    WHERE "スコープ" = 'GLOBAL' ORDER BY 1, 2;
