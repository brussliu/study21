package com.study21.admin.setting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * システム設定ページ（study2SettingRuntime.ts）のフィールドキー（camelCase）と
 * DB カタログ（COM_設定項目）の (ページ区分, 設定キー) の対応表。
 *
 * <p>画面側は旧 Study2 設定画面から移行した camelCase キーで値を送受信するため、
 * admin-api がこの対応表で DB キーへ変換する。UI 側のキーと DB 側のキーは
 * 機械的な命名規則では対応しないため、明示的な全件定義とする。</p>
 *
 * <p>注: DB カタログには画面に存在しない行（AI_GEMINI_*、ENGLISH_ESSAY_TITLE_*、
 * ENGLISH_READING_INTENSIVE_OCR_ARTICLE_*、ENGLISH_WORD_TEXTBOOK_AI_*）も登録されているが、
 * これらは本対応表の対象外（バッチ用・将来用）。</p>
 */
public final class SettingPageFields {

    /** フィールドキー → (ページ区分, 設定キー)。 */
    public record FieldRef(String pageCode, String settingKey) {
    }

    private static final Map<String, FieldRef> BY_FIELD_KEY;
    private static final Map<String, String> FIELD_KEY_BY_DB_KEY;

    static {
        Map<String, FieldRef> m = new LinkedHashMap<>();

        // --- AIモデル (AI_MODEL) ---
        put(m, "qwenModel",           "AI_MODEL", "AI_QWEN_MODEL");
        put(m, "qwenModel2",          "AI_MODEL", "AI_QWEN_MODEL_2");
        put(m, "qwenModel3",          "AI_MODEL", "AI_QWEN_MODEL_3");
        put(m, "qwenModel4",          "AI_MODEL", "AI_QWEN_MODEL_4");
        put(m, "qwenModel5",          "AI_MODEL", "AI_QWEN_MODEL_5");
        put(m, "qwenApiKey",          "AI_MODEL", "AI_QWEN_API_KEY");
        put(m, "qwenUrl",             "AI_MODEL", "AI_QWEN_URL");
        put(m, "doubaoModel",         "AI_MODEL", "AI_DOUBAO_MODEL");
        put(m, "doubaoModel2",        "AI_MODEL", "AI_DOUBAO_MODEL_2");
        put(m, "doubaoApiKey",        "AI_MODEL", "AI_DOUBAO_API_KEY");
        put(m, "doubaoUrl",           "AI_MODEL", "AI_DOUBAO_URL");
        put(m, "deepseekModel",       "AI_MODEL", "AI_DEEPSEEK_MODEL");
        put(m, "deepseekModel2",      "AI_MODEL", "AI_DEEPSEEK_MODEL_2");
        put(m, "deepseekApiKey",      "AI_MODEL", "AI_DEEPSEEK_API_KEY");
        put(m, "deepseekUrl",         "AI_MODEL", "AI_DEEPSEEK_URL");
        put(m, "chatgptModel",        "AI_MODEL", "AI_CHATGPT_MODEL");
        put(m, "chatgptModel2",       "AI_MODEL", "AI_CHATGPT_MODEL_2");
        put(m, "chatgptApiKey",       "AI_MODEL", "AI_CHATGPT_API_KEY");
        put(m, "chatgptUrl",          "AI_MODEL", "AI_CHATGPT_URL");
        put(m, "bigmodelOcrModel",    "AI_MODEL", "AI_BIGMODEL_OCR_MODEL");
        put(m, "bigmodelOcrApiKey",   "AI_MODEL", "AI_BIGMODEL_OCR_API_KEY");
        put(m, "bigmodelOcrUrl",      "AI_MODEL", "AI_BIGMODEL_OCR_URL");

        // --- 単語翻訳発音 (TRANSLATION / VOICE) ---
        put(m, "zhWordApi",           "TRANSLATION", "ZH_WORD_TRANSLATE_API");
        put(m, "zhSentenceApi",       "TRANSLATION", "ZH_SENTENCE_TRANSLATE_API");
        put(m, "zhThreads",           "TRANSLATION", "ZH_TRANSLATE_THREADS");
        put(m, "jaWordApi",           "TRANSLATION", "JA_WORD_TRANSLATE_API");
        put(m, "jaSentenceApi",       "TRANSLATION", "JA_SENTENCE_TRANSLATE_API");
        put(m, "jaThreads",           "TRANSLATION", "JA_TRANSLATE_THREADS");
        put(m, "enVoiceWordApi",      "VOICE", "EN_WORD_VOICE_API");
        put(m, "enVoiceSentenceApi",  "VOICE", "EN_SENTENCE_VOICE_API");
        put(m, "enVoiceThreads",      "VOICE", "EN_VOICE_THREADS");

        // --- 単語情報生成 (WORD_QUESTION / WORD_EXPLANATION) ---
        put(m, "c04AiModel",          "WORD_QUESTION", "BAT_C04_AI_MODEL");
        put(m, "c04Threads",          "WORD_QUESTION", "BAT_C04_THREADS");
        put(m, "c04SystemPromptZh",   "WORD_QUESTION", "BAT_C04_SYSTEM_PROMPT_ZH");
        put(m, "c04SystemPromptJa",   "WORD_QUESTION", "BAT_C04_SYSTEM_PROMPT_JA");
        put(m, "c04UserPromptZh",     "WORD_QUESTION", "BAT_C04_USER_PROMPT_ZH");
        put(m, "c04UserPromptJa",     "WORD_QUESTION", "BAT_C04_USER_PROMPT_JA");
        put(m, "c05AiModel",          "WORD_EXPLANATION", "BAT_C05_AI_MODEL");
        put(m, "c05Threads",          "WORD_EXPLANATION", "BAT_C05_THREADS");
        put(m, "c05PromptZh",         "WORD_EXPLANATION", "BAT_C05_PROMPT_ZH");
        put(m, "c06AiModel",          "WORD_EXPLANATION", "BAT_C06_AI_MODEL");
        put(m, "c06Threads",          "WORD_EXPLANATION", "BAT_C06_THREADS");
        put(m, "c06PromptJa",         "WORD_EXPLANATION", "BAT_C06_PROMPT_JA");

        // --- 画像共通処理 batC91 / 英語読解・精読 (ENGLISH_READING_INTENSIVE) ---
        put(m, "intensiveMaxImages",      "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_MAX_IMAGES");
        put(m, "intensiveMaxImageMb",     "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_MAX_IMAGE_MB");
        put(m, "intensiveOcrAiProvider",  "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_OCR_AI_PROVIDER");
        put(m, "intensiveOcrTimeoutSeconds",   "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_OCR_TIMEOUT_SECONDS");
        put(m, "intensiveOcrMaxImagePixels",   "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_OCR_MAX_IMAGE_PIXELS");
        put(m, "intensiveOcrSystemPrompt",     "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_OCR_SYSTEM_PROMPT");
        put(m, "intensiveOcrUserPrompt",       "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_OCR_USER_PROMPT");
        put(m, "intensiveOcrMaxRetries",       "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_OCR_MAX_RETRIES");
        put(m, "intensiveBatC15AiProvider",    "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_BAT_C15_AI_PROVIDER");
        put(m, "intensiveOcrTextMethod",       "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_OCR_TEXT_MODE_METHOD");
        put(m, "intensiveMethodAArticleOcrProvider", "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_METHOD_A_ARTICLE_OCR_PROVIDER");
        put(m, "intensiveOcrQuestionSystemPrompt",   "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_OCR_QUESTION_SYSTEM_PROMPT");
        put(m, "intensiveOcrQuestionUserPrompt",     "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_OCR_QUESTION_USER_PROMPT");
        put(m, "intensiveOcrMethodAStructureSystemPrompt", "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_OCR_METHOD_A_STRUCTURE_SYSTEM_PROMPT");
        put(m, "intensiveOcrMethodAStructureUserPrompt",   "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_OCR_METHOD_A_STRUCTURE_USER_PROMPT");
        put(m, "intensiveMethodBOcrProvider",   "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_METHOD_B_OCR_PROVIDER");
        put(m, "intensiveOcrStructureSystemPrompt", "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_OCR_STRUCTURE_SYSTEM_PROMPT");
        put(m, "intensiveOcrStructureUserPrompt",   "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_OCR_STRUCTURE_USER_PROMPT");
        put(m, "intensiveGuideAiProvider",      "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_GUIDE_AI_PROVIDER");
        put(m, "intensiveGuideTimeoutSeconds",  "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_GUIDE_TIMEOUT_SECONDS");
        put(m, "intensiveGuideSystemPrompt",    "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_GUIDE_SYSTEM_PROMPT");
        put(m, "intensiveGuideUserPrompt",      "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_GUIDE_USER_PROMPT");
        put(m, "intensiveExplanationAiProvider",      "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_EXPLANATION_AI_PROVIDER");
        put(m, "intensiveExplanationTimeoutSeconds",  "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_EXPLANATION_TIMEOUT_SECONDS");
        put(m, "intensiveExplanationBatchSize",       "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_EXPLANATION_BATCH_SIZE");
        put(m, "intensiveExplanationThreads",         "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_EXPLANATION_THREADS");
        put(m, "intensiveExplanationSystemPrompt",    "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_EXPLANATION_SYSTEM_PROMPT");
        put(m, "intensiveExplanationUserPrompt",      "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_EXPLANATION_USER_PROMPT");
        put(m, "intensiveQuestionAiProvider",     "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_QUESTION_AI_PROVIDER");
        put(m, "intensiveQuestionTimeoutSeconds", "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_QUESTION_TIMEOUT_SECONDS");
        put(m, "intensiveQuestionSystemPrompt",   "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_QUESTION_SYSTEM_PROMPT");
        put(m, "intensiveQuestionUserPrompt",     "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_QUESTION_USER_PROMPT");

        // --- 英作文AI添削 (ENGLISH_ESSAY) ---
        put(m, "essayMaxImages",       "ENGLISH_ESSAY", "ENGLISH_ESSAY_MAX_IMAGES");
        put(m, "essayMaxImageMb",      "ENGLISH_ESSAY", "ENGLISH_ESSAY_MAX_IMAGE_MB");
        put(m, "essayOcrAiProvider",   "ENGLISH_ESSAY", "ENGLISH_ESSAY_OCR_AI_PROVIDER");
        put(m, "essayOcrMaxImagePixels", "ENGLISH_ESSAY", "ENGLISH_ESSAY_OCR_MAX_IMAGE_PIXELS");
        put(m, "essayOcrRequestTimeoutSeconds", "ENGLISH_ESSAY", "ENGLISH_ESSAY_OCR_REQUEST_TIMEOUT_SECONDS");
        put(m, "essayOcrPrompt",       "ENGLISH_ESSAY", "ENGLISH_ESSAY_OCR_PROMPT");
        put(m, "essayOcrUserPrompt",   "ENGLISH_ESSAY", "ENGLISH_ESSAY_OCR_USER_PROMPT");
        put(m, "essayOcrRetryLimit",   "ENGLISH_ESSAY", "ENGLISH_ESSAY_OCR_RETRY_LIMIT");
        put(m, "essayGradingAiProvider", "ENGLISH_ESSAY", "ENGLISH_ESSAY_GRADING_AI_PROVIDER");
        put(m, "essayGradingRequestTimeoutSeconds", "ENGLISH_ESSAY", "ENGLISH_ESSAY_GRADING_REQUEST_TIMEOUT_SECONDS");
        put(m, "essayGradingPrompt",   "ENGLISH_ESSAY", "ENGLISH_ESSAY_GRADING_PROMPT");
        put(m, "essayGradingUserPrompt", "ENGLISH_ESSAY", "ENGLISH_ESSAY_GRADING_USER_PROMPT");
        put(m, "essayGradingRetryLimit", "ENGLISH_ESSAY", "ENGLISH_ESSAY_GRADING_RETRY_LIMIT");

        // --- 英語穴埋め問題 (ENGLISH_CLOZE) ---
        put(m, "clozeMaxImages",       "ENGLISH_CLOZE", "ENGLISH_CLOZE_MAX_IMAGES");
        put(m, "clozeMaxImageMb",      "ENGLISH_CLOZE", "ENGLISH_CLOZE_MAX_IMAGE_MB");
        put(m, "clozeOcrAiProvider",   "ENGLISH_CLOZE", "ENGLISH_CLOZE_OCR_AI_PROVIDER");
        put(m, "clozeOcrMaxImagePixels", "ENGLISH_CLOZE", "ENGLISH_CLOZE_OCR_MAX_IMAGE_PIXELS");
        put(m, "clozeOcrTimeoutSeconds", "ENGLISH_CLOZE", "ENGLISH_CLOZE_OCR_TIMEOUT_SECONDS");
        put(m, "clozeOcrSystemPrompt", "ENGLISH_CLOZE", "ENGLISH_CLOZE_OCR_SYSTEM_PROMPT");
        put(m, "clozeOcrUserPrompt",   "ENGLISH_CLOZE", "ENGLISH_CLOZE_OCR_USER_PROMPT");
        put(m, "clozeOcrRetryLimit",   "ENGLISH_CLOZE", "ENGLISH_CLOZE_OCR_RETRY_LIMIT");
        put(m, "clozeExplanationAiProvider", "ENGLISH_CLOZE", "ENGLISH_CLOZE_EXPLANATION_AI_PROVIDER");
        put(m, "clozeExplanationBatchMax",   "ENGLISH_CLOZE", "ENGLISH_CLOZE_EXPLANATION_BATCH_MAX");
        put(m, "clozeExplanationThreads",    "ENGLISH_CLOZE", "ENGLISH_CLOZE_EXPLANATION_THREADS");
        put(m, "clozeExplanationRequestTimeoutSeconds", "ENGLISH_CLOZE", "ENGLISH_CLOZE_EXPLANATION_REQUEST_TIMEOUT_SECONDS");
        put(m, "clozeExplanationSystemPrompt", "ENGLISH_CLOZE", "ENGLISH_CLOZE_EXPLANATION_SYSTEM_PROMPT");
        put(m, "clozeExplanationUserPrompt",   "ENGLISH_CLOZE", "ENGLISH_CLOZE_EXPLANATION_USER_PROMPT");
        put(m, "clozeExplanationRetryLimit",   "ENGLISH_CLOZE", "ENGLISH_CLOZE_EXPLANATION_RETRY_LIMIT");

        // --- 学習状況モニター (STUDY_MONITOR) ---
        put(m, "monitorVideoSourceDirectory",      "STUDY_MONITOR", "STUDY_MONITOR_VIDEO_SOURCE_DIRECTORY");
        put(m, "monitorSnapshotOutputDirectory",   "STUDY_MONITOR", "STUDY_MONITOR_SNAPSHOT_OUTPUT_DIRECTORY");
        put(m, "monitorVideoProcessingStartTime",  "STUDY_MONITOR", "STUDY_MONITOR_VIDEO_PROCESSING_START_TIME");
        put(m, "monitorVideoProcessingEndTime",    "STUDY_MONITOR", "STUDY_MONITOR_VIDEO_PROCESSING_END_TIME");
        put(m, "monitorSnapshotIntervalSeconds",   "STUDY_MONITOR", "STUDY_MONITOR_SNAPSHOT_INTERVAL_SECONDS");
        put(m, "monitorCameraLocation",            "STUDY_MONITOR", "STUDY_MONITOR_CAMERA_LOCATION");
        put(m, "monitorCameraContext",             "STUDY_MONITOR", "STUDY_MONITOR_CAMERA_CONTEXT");
        put(m, "monitorFirstAiProvider",           "STUDY_MONITOR", "STUDY_MONITOR_FIRST_AI_PROVIDER");
        put(m, "monitorAiBatchLimit",              "STUDY_MONITOR", "STUDY_MONITOR_AI_BATCH_LIMIT");
        put(m, "monitorAiThreads",                 "STUDY_MONITOR", "STUDY_MONITOR_AI_THREADS");
        put(m, "monitorAiTimeoutSeconds",          "STUDY_MONITOR", "STUDY_MONITOR_AI_TIMEOUT_SECONDS");
        put(m, "monitorAiImageResolution",         "STUDY_MONITOR", "STUDY_MONITOR_AI_IMAGE_RESOLUTION");
        put(m, "monitorFirstSystemPrompt",         "STUDY_MONITOR", "STUDY_MONITOR_FIRST_SYSTEM_PROMPT");
        put(m, "monitorFirstUserPrompt",           "STUDY_MONITOR", "STUDY_MONITOR_FIRST_USER_PROMPT");

        // --- 学習日報 (DAILY_REPORT) ---
        put(m, "dailyReportReminderEnabled", "DAILY_REPORT", "DAILY_REPORT_REMINDER_ENABLED");
        // 提出時の LINE 通知（設定画面では「学習日報」に置く。送信そのものは未実装）
        put(m, "lineDailyReportEnabled",          "DAILY_REPORT", "LINE_DAILY_REPORT_ENABLED");
        put(m, "lineDailyReportTo",               "DAILY_REPORT", "LINE_DAILY_REPORT_TO");
        put(m, "lineDailyReportSendOnResubmit",   "DAILY_REPORT", "LINE_DAILY_REPORT_SEND_ON_RESUBMIT");
        put(m, "lineDailyReportTemplate",         "DAILY_REPORT", "LINE_DAILY_REPORT_TEMPLATE");
        put(m, "lineDailyReportLessonTemplate",   "DAILY_REPORT", "LINE_DAILY_REPORT_LESSON_TEMPLATE");

        // --- 英単語詳細AI取得 (ENGLISH_WORD_DETAIL_AI / WORD_QUESTION) ---
        put(m, "wordDetailAiProvider",            "ENGLISH_WORD_DETAIL_AI", "ENGLISH_WORD_DETAIL_AI_PROVIDER");
        put(m, "wordDetailAiBatchSize",           "ENGLISH_WORD_DETAIL_AI", "ENGLISH_WORD_DETAIL_AI_BATCH_SIZE");
        put(m, "wordDetailAiThreads",             "ENGLISH_WORD_DETAIL_AI", "ENGLISH_WORD_DETAIL_AI_THREADS");
        put(m, "wordDetailAiRequestTimeoutSeconds", "ENGLISH_WORD_DETAIL_AI", "ENGLISH_WORD_DETAIL_AI_REQUEST_TIMEOUT_SECONDS");
        put(m, "wordDetailAiMaxCompletionTokens", "ENGLISH_WORD_DETAIL_AI", "ENGLISH_WORD_DETAIL_AI_MAX_COMPLETION_TOKENS");
        put(m, "wordDetailAiTemperature",         "ENGLISH_WORD_DETAIL_AI", "ENGLISH_WORD_DETAIL_AI_TEMPERATURE");
        put(m, "wordDetailAiSystemPrompt",        "ENGLISH_WORD_DETAIL_AI", "ENGLISH_WORD_DETAIL_AI_SYSTEM_PROMPT");
        put(m, "wordDetailAiUserPrompt",          "ENGLISH_WORD_DETAIL_AI", "ENGLISH_WORD_DETAIL_AI_USER_PROMPT");
        put(m, "wordDetailAiRetryLimit",          "ENGLISH_WORD_DETAIL_AI", "ENGLISH_WORD_DETAIL_AI_RETRY_LIMIT");
        put(m, "c042AiProvider",              "WORD_QUESTION", "BAT_C04_2_AI_PROVIDER");
        put(m, "c042BatchSize",               "WORD_QUESTION", "BAT_C04_2_BATCH_SIZE");
        put(m, "c042Threads",                 "WORD_QUESTION", "BAT_C04_2_THREADS");
        put(m, "c042RequestTimeoutSeconds",   "WORD_QUESTION", "BAT_C04_2_REQUEST_TIMEOUT_SECONDS");
        put(m, "c042MaxCompletionTokens",     "WORD_QUESTION", "BAT_C04_2_MAX_COMPLETION_TOKENS");
        put(m, "c042Temperature",             "WORD_QUESTION", "BAT_C04_2_TEMPERATURE");
        put(m, "c042SystemPrompt",            "WORD_QUESTION", "BAT_C04_2_SYSTEM_PROMPT");
        put(m, "c042UserPrompt",              "WORD_QUESTION", "BAT_C04_2_USER_PROMPT");
        put(m, "c042RetryLimit",              "WORD_QUESTION", "BAT_C04_2_RETRY_LIMIT");
        put(m, "c043AiProvider",              "WORD_QUESTION", "BAT_C04_3_AI_PROVIDER");
        put(m, "c043BatchSize",               "WORD_QUESTION", "BAT_C04_3_BATCH_SIZE");
        put(m, "c043Threads",                 "WORD_QUESTION", "BAT_C04_3_THREADS");
        put(m, "c043RequestTimeoutSeconds",   "WORD_QUESTION", "BAT_C04_3_REQUEST_TIMEOUT_SECONDS");
        put(m, "c043MaxCompletionTokens",     "WORD_QUESTION", "BAT_C04_3_MAX_COMPLETION_TOKENS");
        put(m, "c043Temperature",             "WORD_QUESTION", "BAT_C04_3_TEMPERATURE");
        put(m, "c043SystemPrompt",            "WORD_QUESTION", "BAT_C04_3_SYSTEM_PROMPT");
        put(m, "c043UserPrompt",              "WORD_QUESTION", "BAT_C04_3_USER_PROMPT");
        put(m, "c043RetryLimit",              "WORD_QUESTION", "BAT_C04_3_RETRY_LIMIT");

        // --- 英熟語詳細AI取得 (ENGLISH_PHRASE_DETAIL_AI / WORD_QUESTION) ---
        put(m, "phraseStandardizationAiProvider",          "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_STANDARDIZATION_AI_PROVIDER");
        put(m, "phraseStandardizationAiBatchSize",         "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_STANDARDIZATION_AI_BATCH_SIZE");
        put(m, "phraseStandardizationAiThreads",           "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_STANDARDIZATION_AI_THREADS");
        put(m, "phraseStandardizationAiRequestTimeoutSeconds", "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_STANDARDIZATION_AI_REQUEST_TIMEOUT_SECONDS");
        put(m, "phraseStandardizationAiMaxCompletionTokens", "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_STANDARDIZATION_AI_MAX_COMPLETION_TOKENS");
        put(m, "phraseStandardizationAiTemperature",       "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_STANDARDIZATION_AI_TEMPERATURE");
        put(m, "phraseStandardizationAiAutoApproveThreshold", "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_STANDARDIZATION_AI_AUTO_APPROVE_THRESHOLD");
        put(m, "phraseStandardizationAiSystemPrompt",      "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_STANDARDIZATION_AI_SYSTEM_PROMPT");
        put(m, "phraseStandardizationAiUserPrompt",        "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_STANDARDIZATION_AI_USER_PROMPT");
        put(m, "phraseStandardizationAiRetryLimit",        "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_STANDARDIZATION_AI_RETRY_LIMIT");
        put(m, "phraseDetailAiProvider",            "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_DETAIL_AI_PROVIDER");
        put(m, "phraseDetailAiBatchSize",           "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_DETAIL_AI_BATCH_SIZE");
        put(m, "phraseDetailAiThreads",             "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_DETAIL_AI_THREADS");
        put(m, "phraseDetailAiRequestTimeoutSeconds", "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_DETAIL_AI_REQUEST_TIMEOUT_SECONDS");
        put(m, "phraseDetailAiMaxCompletionTokens", "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_DETAIL_AI_MAX_COMPLETION_TOKENS");
        put(m, "phraseDetailAiTemperature",         "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_DETAIL_AI_TEMPERATURE");
        put(m, "phraseDetailAiSystemPrompt",        "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_DETAIL_AI_SYSTEM_PROMPT");
        put(m, "phraseDetailAiUserPrompt",          "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_DETAIL_AI_USER_PROMPT");
        put(m, "phraseDetailAiRetryLimit",          "ENGLISH_PHRASE_DETAIL_AI", "ENGLISH_PHRASE_DETAIL_AI_RETRY_LIMIT");
        put(m, "c23AiProvider",              "WORD_QUESTION", "BAT_C33_AI_PROVIDER");
        put(m, "c23BatchSize",               "WORD_QUESTION", "BAT_C33_BATCH_SIZE");
        put(m, "c23Threads",                 "WORD_QUESTION", "BAT_C33_THREADS");
        put(m, "c23RequestTimeoutSeconds",   "WORD_QUESTION", "BAT_C33_REQUEST_TIMEOUT_SECONDS");
        put(m, "c23MaxCompletionTokens",     "WORD_QUESTION", "BAT_C33_MAX_COMPLETION_TOKENS");
        put(m, "c23Temperature",             "WORD_QUESTION", "BAT_C33_TEMPERATURE");
        put(m, "c23SystemPrompt",            "WORD_QUESTION", "BAT_C33_SYSTEM_PROMPT");
        put(m, "c23UserPrompt",              "WORD_QUESTION", "BAT_C33_USER_PROMPT");
        put(m, "c23RetryLimit",              "WORD_QUESTION", "BAT_C33_RETRY_LIMIT");
        put(m, "c24AiProvider",              "WORD_QUESTION", "BAT_C34_AI_PROVIDER");
        put(m, "c24BatchSize",               "WORD_QUESTION", "BAT_C34_BATCH_SIZE");
        put(m, "c24Threads",                 "WORD_QUESTION", "BAT_C34_THREADS");
        put(m, "c24RequestTimeoutSeconds",   "WORD_QUESTION", "BAT_C34_REQUEST_TIMEOUT_SECONDS");
        put(m, "c24MaxCompletionTokens",     "WORD_QUESTION", "BAT_C34_MAX_COMPLETION_TOKENS");
        put(m, "c24Temperature",             "WORD_QUESTION", "BAT_C34_TEMPERATURE");
        put(m, "c24SystemPrompt",            "WORD_QUESTION", "BAT_C34_SYSTEM_PROMPT");
        put(m, "c24UserPrompt",              "WORD_QUESTION", "BAT_C34_USER_PROMPT");
        put(m, "c24RetryLimit",              "WORD_QUESTION", "BAT_C34_RETRY_LIMIT");

        // --- 日本語単語AI (JAPANESE_WORD_AI) ---
        put(m, "c25AiProvider",            "JAPANESE_WORD_AI", "BAT_C41_AI_PROVIDER");
        put(m, "c25BatchMax",              "JAPANESE_WORD_AI", "BAT_C41_BATCH_MAX");
        put(m, "c25Threads",               "JAPANESE_WORD_AI", "BAT_C41_THREADS");
        put(m, "c25RequestTimeoutSeconds", "JAPANESE_WORD_AI", "BAT_C41_REQUEST_TIMEOUT_SECONDS");
        put(m, "c25MaxCompletionTokens",   "JAPANESE_WORD_AI", "BAT_C41_MAX_COMPLETION_TOKENS");
        put(m, "c25Temperature",           "JAPANESE_WORD_AI", "BAT_C41_TEMPERATURE");
        put(m, "c25SystemPrompt",          "JAPANESE_WORD_AI", "BAT_C41_SYSTEM_PROMPT");
        put(m, "c25UserPrompt",            "JAPANESE_WORD_AI", "BAT_C41_USER_PROMPT");
        put(m, "c25RetryLimit",            "JAPANESE_WORD_AI", "BAT_C41_RETRY_LIMIT");
        put(m, "c26AiProvider",            "JAPANESE_WORD_AI", "BAT_C42_AI_PROVIDER");
        put(m, "c26BatchMax",              "JAPANESE_WORD_AI", "BAT_C42_BATCH_MAX");
        put(m, "c26Threads",               "JAPANESE_WORD_AI", "BAT_C42_THREADS");
        put(m, "c26RequestTimeoutSeconds", "JAPANESE_WORD_AI", "BAT_C42_REQUEST_TIMEOUT_SECONDS");
        put(m, "c26MaxCompletionTokens",   "JAPANESE_WORD_AI", "BAT_C42_MAX_COMPLETION_TOKENS");
        put(m, "c26Temperature",           "JAPANESE_WORD_AI", "BAT_C42_TEMPERATURE");
        put(m, "c26SystemPrompt",          "JAPANESE_WORD_AI", "BAT_C42_SYSTEM_PROMPT");
        put(m, "c26UserPrompt",            "JAPANESE_WORD_AI", "BAT_C42_USER_PROMPT");
        put(m, "c26RetryLimit",            "JAPANESE_WORD_AI", "BAT_C42_RETRY_LIMIT");
        put(m, "c27AiProvider",            "JAPANESE_WORD_AI", "BAT_C43_AI_PROVIDER");
        put(m, "c27BatchMax",              "JAPANESE_WORD_AI", "BAT_C43_BATCH_MAX");
        put(m, "c27Threads",               "JAPANESE_WORD_AI", "BAT_C43_THREADS");
        put(m, "c27RequestTimeoutSeconds", "JAPANESE_WORD_AI", "BAT_C43_REQUEST_TIMEOUT_SECONDS");
        put(m, "c27MaxCompletionTokens",   "JAPANESE_WORD_AI", "BAT_C43_MAX_COMPLETION_TOKENS");
        put(m, "c27Temperature",           "JAPANESE_WORD_AI", "BAT_C43_TEMPERATURE");
        put(m, "c27SystemPrompt",          "JAPANESE_WORD_AI", "BAT_C43_SYSTEM_PROMPT");
        put(m, "c27UserPrompt",            "JAPANESE_WORD_AI", "BAT_C43_USER_PROMPT");
        put(m, "c27RetryLimit",            "JAPANESE_WORD_AI", "BAT_C43_RETRY_LIMIT");
        put(m, "c28AiProvider",            "JAPANESE_WORD_AI", "BAT_C44_AI_PROVIDER");
        put(m, "c28BatchMax",              "JAPANESE_WORD_AI", "BAT_C44_BATCH_MAX");
        put(m, "c28Threads",               "JAPANESE_WORD_AI", "BAT_C44_THREADS");
        put(m, "c28RequestTimeoutSeconds", "JAPANESE_WORD_AI", "BAT_C44_REQUEST_TIMEOUT_SECONDS");
        put(m, "c28MaxCompletionTokens",   "JAPANESE_WORD_AI", "BAT_C44_MAX_COMPLETION_TOKENS");
        put(m, "c28Temperature",           "JAPANESE_WORD_AI", "BAT_C44_TEMPERATURE");
        put(m, "c28SystemPrompt",          "JAPANESE_WORD_AI", "BAT_C44_SYSTEM_PROMPT");
        put(m, "c28UserPrompt",            "JAPANESE_WORD_AI", "BAT_C44_USER_PROMPT");
        put(m, "c28RetryLimit",            "JAPANESE_WORD_AI", "BAT_C44_RETRY_LIMIT");

        // --- LINE連携 (LINE) ---
        put(m, "lineMessagingChannelAccessToken",     "LINE", "LINE_MESSAGING_CHANNEL_ACCESS_TOKEN");
        put(m, "lineMessagingPushUrl",                "LINE", "LINE_MESSAGING_PUSH_URL");
        put(m, "lineMessagingDefaultTo",              "LINE", "LINE_MESSAGING_DEFAULT_TO");
        put(m, "lineMessagingChannelSecret",          "LINE", "LINE_MESSAGING_CHANNEL_SECRET");
        put(m, "lineMessagingWebhookValidateSignature", "LINE", "LINE_MESSAGING_WEBHOOK_VALIDATE_SIGNATURE");


        BY_FIELD_KEY = Collections.unmodifiableMap(m);

        Map<String, String> reverse = new LinkedHashMap<>();
        m.forEach((fieldKey, ref) -> reverse.put(ref.pageCode() + "\n" + ref.settingKey(), fieldKey));
        FIELD_KEY_BY_DB_KEY = Collections.unmodifiableMap(reverse);
    }

    private static void put(Map<String, FieldRef> m, String fieldKey, String pageCode, String settingKey) {
        FieldRef previous = m.put(fieldKey, new FieldRef(pageCode, settingKey));
        if (previous != null) {
            throw new IllegalStateException("設定ページフィールドキーが重複しています: " + fieldKey);
        }
    }

    private SettingPageFields() {
    }

    /** フィールドキーから (ページ区分, 設定キー) を解決する。未定義は null。 */
    public static FieldRef of(String fieldKey) {
        return fieldKey == null ? null : BY_FIELD_KEY.get(fieldKey);
    }

    /** (ページ区分, 設定キー) からフィールドキーを逆引きする。未定義は null。 */
    public static String fieldKeyOf(String pageCode, String settingKey) {
        if (pageCode == null || settingKey == null) {
            return null;
        }
        return FIELD_KEY_BY_DB_KEY.get(pageCode + "\n" + settingKey);
    }

    /** 全フィールドキー → (ページ区分, 設定キー) の対応（読み取り専用）。 */
    public static Map<String, FieldRef> all() {
        return BY_FIELD_KEY;
    }
}
