-- ============================================================================
-- Study 2.1  設定カタログ初期データ（自動生成）
-- テーブル: COM_設定項目
-- 生成元: study2/study2 SettingServiceImpl.java の DEFINITIONS（246 項目）
-- 注意: 値(defaultValue)はプログラム既定値のため本シードでは登録しない。
-- 実行順序: TBL_COM_設定項目.sql の後
-- ============================================================================

-- ---------------- TRANSLATION (単語翻訳設定) ----------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('TRANSLATION','ZH_WORD_TRANSLATE_API','ENUM','1','YouDao,Google','中文翻訳：単語翻訳API（YouDao/Google）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('TRANSLATION','ZH_SENTENCE_TRANSLATE_API','ENUM','1','YouDao,Google','中文翻訳：例句翻訳API（YouDao/Google）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('TRANSLATION','ZH_TRANSLATE_THREADS','INTEGER','1','1..20','中文翻訳：処理スレッド数（1〜20）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('TRANSLATION','JA_WORD_TRANSLATE_API','ENUM','1','ExcelAPI,Google,YouDao','日文翻訳：単語翻訳API（ExcelAPI/Google/YouDao）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('TRANSLATION','JA_SENTENCE_TRANSLATE_API','ENUM','1','ExcelAPI,Google,YouDao','日文翻訳：例句翻訳API（ExcelAPI/Google/YouDao）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('TRANSLATION','JA_TRANSLATE_THREADS','INTEGER','1','1..20','日文翻訳：処理スレッド数（1〜20）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ---------------- VOICE (英語発音) ----------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('VOICE','EN_WORD_VOICE_API','ENUM','1','YouDao,Google','英語発音：単語発音取得API（YouDao/Google）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('VOICE','EN_SENTENCE_VOICE_API','ENUM','1','YouDao,Google','英語発音：例句発音取得API（YouDao/Google）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('VOICE','EN_VOICE_THREADS','INTEGER','1','1..20','英語発音：処理スレッド数（1〜20）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ---------------- WORD_QUESTION (単語情報生成: batC04 初級編英訳中日問題) ----------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_AI_MODEL','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','batC04 初級編英訳中日問題：AIモデルページで設定した使用モデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_THREADS','INTEGER','1','1..20','batC04 初級編英訳中日問題：処理スレッド数（1〜20）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_SYSTEM_PROMPT_ZH','TEXT','1',NULL,'batC04 初級編英訳中日問題：中国語 System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_USER_PROMPT_ZH','TEXT','1',NULL,'batC04 初級編英訳中日問題：中国語 User Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_SYSTEM_PROMPT_JA','TEXT','1',NULL,'batC04 初級編英訳中日問題：日本語 System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_USER_PROMPT_JA','TEXT','1',NULL,'batC04 初級編英訳中日問題：日本語 User Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_2_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','batC22 中級編D：AIモデルページで設定した使用モデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_2_BATCH_SIZE','INTEGER','1','10..200','batC22 中級編D：1回の最大単語数（10語単位、10〜200）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_2_THREADS','INTEGER','1','1..20','batC22 中級編D：処理スレッド数（1〜20）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_2_REQUEST_TIMEOUT_SECONDS','INTEGER','1','30..1800','batC22 中級編D：単次リクエストタイムアウト秒数（30〜1800）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_2_MAX_COMPLETION_TOKENS','INTEGER','1','1024..65536','batC22 中級編D：最大出力Token数（1024〜65536）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_2_TEMPERATURE','ENUM','1','0.0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0,1.1,1.2,1.3,1.4,1.5,1.6,1.7,1.8,1.9,2.0','batC22 中級編D：Temperature（0.0〜2.0）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_2_SYSTEM_PROMPT','TEXT','1',NULL,'batC22 中級編D：System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_2_USER_PROMPT','TEXT','1',NULL,'batC22 中級編D：User Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_2_RETRY_LIMIT','INTEGER','1','0..5','batC22 中級編D：単語単位の最大再実行回数（0〜5）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_3_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','batC23 中級編E：AIモデルページで設定した使用モデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_3_BATCH_SIZE','INTEGER','1','10..200','batC23 中級編E：1回の最大単語数（10語単位、10〜200）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_3_THREADS','INTEGER','1','1..20','batC23 中級編E：処理スレッド数（1〜20）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_3_REQUEST_TIMEOUT_SECONDS','INTEGER','1','30..1800','batC23 中級編E：単次リクエストタイムアウト秒数（30〜1800）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_3_MAX_COMPLETION_TOKENS','INTEGER','1','1024..65536','batC23 中級編E：最大出力Token数（1024〜65536）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_3_TEMPERATURE','ENUM','1','0.0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0,1.1,1.2,1.3,1.4,1.5,1.6,1.7,1.8,1.9,2.0','batC23 中級編E：Temperature（0.0〜2.0）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_3_SYSTEM_PROMPT','TEXT','1',NULL,'batC23 中級編E：System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_3_USER_PROMPT','TEXT','1',NULL,'batC23 中級編E：User Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C04_3_RETRY_LIMIT','INTEGER','1','0..5','batC23 中級編E：単語単位の最大再実行回数（0〜5）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C33_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','batC33 熟語D：AIモデルページで設定した使用モデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C33_BATCH_SIZE','INTEGER','1','10..200','batC33 熟語D：1回の最大熟語数（10語単位、10〜200）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C33_THREADS','INTEGER','1','1..20','batC33 熟語D：処理スレッド数（1〜20）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C33_REQUEST_TIMEOUT_SECONDS','INTEGER','1','30..1800','batC33 熟語D：単次リクエストタイムアウト秒数（30〜1800）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C33_MAX_COMPLETION_TOKENS','INTEGER','1','1024..65536','batC33 熟語D：最大出力Token数（1024〜65536）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C33_TEMPERATURE','ENUM','1','0.0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0,1.1,1.2,1.3,1.4,1.5,1.6,1.7,1.8,1.9,2.0','batC33 熟語D：Temperature（0.0〜2.0）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C33_SYSTEM_PROMPT','TEXT','1',NULL,'batC33 熟語D：System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C33_USER_PROMPT','TEXT','1',NULL,'batC33 熟語D：User Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C33_RETRY_LIMIT','INTEGER','1','0..5','batC33 熟語D：熟語単位の最大再実行回数（0〜5）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C34_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','batC34 熟語E：AIモデルページで設定した使用モデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C34_BATCH_SIZE','INTEGER','1','10..200','batC34 熟語E：1回の最大熟語数（10語単位、10〜200）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C34_THREADS','INTEGER','1','1..20','batC34 熟語E：処理スレッド数（1〜20）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C34_REQUEST_TIMEOUT_SECONDS','INTEGER','1','30..1800','batC34 熟語E：単次リクエストタイムアウト秒数（30〜1800）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C34_MAX_COMPLETION_TOKENS','INTEGER','1','1024..65536','batC34 熟語E：最大出力Token数（1024〜65536）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C34_TEMPERATURE','ENUM','1','0.0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0,1.1,1.2,1.3,1.4,1.5,1.6,1.7,1.8,1.9,2.0','batC34 熟語E：Temperature（0.0〜2.0）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C34_SYSTEM_PROMPT','TEXT','1',NULL,'batC34 熟語E：System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C34_USER_PROMPT','TEXT','1',NULL,'batC34 熟語E：User Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_QUESTION','BAT_C34_RETRY_LIMIT','INTEGER','1','0..5','batC34 熟語E：熟語単位の最大再実行回数（0〜5）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ---------------- WORD_EXPLANATION (単語情報生成: batC05/batC06 単語説明生成) ----------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_EXPLANATION','BAT_C05_AI_MODEL','TEXT','1',NULL,'中国語説明取得：使用モデル（複数可。カンマ区切り）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_EXPLANATION','BAT_C05_THREADS','INTEGER','1','1..20','中国語説明取得：処理スレッド数（1〜20）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_EXPLANATION','BAT_C05_PROMPT_ZH','TEXT','1',NULL,'中国語説明取得：中国語プロンプト')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_EXPLANATION','BAT_C06_AI_MODEL','TEXT','1',NULL,'日本語説明取得：使用モデル（複数可。カンマ区切り）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_EXPLANATION','BAT_C06_THREADS','INTEGER','1','1..20','日本語説明取得：処理スレッド数（1〜20）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('WORD_EXPLANATION','BAT_C06_PROMPT_JA','TEXT','1',NULL,'日本語説明取得：日本語プロンプト')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ---------------- AI_MODEL (AIモデル) ----------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_QWEN_MODEL','TEXT','1',NULL,'AIモデル：千问 モデル1指定')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_QWEN_MODEL_2','TEXT','1',NULL,'AIモデル：千问 モデル2指定')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_QWEN_MODEL_3','TEXT','1',NULL,'AIモデル：千问 モデル3指定（学習モニター一次判定用）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_QWEN_MODEL_4','TEXT','1',NULL,'AIモデル：千问 モデル4指定（学習モニター二次判定用）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_QWEN_MODEL_5','TEXT','1',NULL,'AIモデル：千问 モデル5指定（英単語教材AI認識のQwen-VL-OCR Batch用）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_QWEN_API_KEY','TEXT','1',NULL,'AIモデル：千问 apiKey')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_QWEN_URL','TEXT','1',NULL,'AIモデル：千问 URL')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_DOUBAO_MODEL','TEXT','1',NULL,'AIモデル：豆包 モデル1指定')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_DOUBAO_MODEL_2','TEXT','1',NULL,'AIモデル：豆包 モデル2指定')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_DOUBAO_API_KEY','TEXT','1',NULL,'AIモデル：豆包 apiKey')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_DOUBAO_URL','TEXT','1',NULL,'AIモデル：豆包 URL')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_DEEPSEEK_MODEL','TEXT','1',NULL,'AIモデル：DeepSeek モデル1指定')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_DEEPSEEK_MODEL_2','TEXT','1',NULL,'AIモデル：DeepSeek モデル2指定')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_DEEPSEEK_API_KEY','TEXT','1',NULL,'AIモデル：DeepSeek apiKey')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_DEEPSEEK_URL','TEXT','1',NULL,'AIモデル：DeepSeek URL')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_CHATGPT_MODEL','TEXT','1',NULL,'AIモデル：OpenAI モデル1指定')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_CHATGPT_MODEL_2','TEXT','1',NULL,'AIモデル：OpenAI モデル2指定')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_CHATGPT_API_KEY','TEXT','1',NULL,'AIモデル：OpenAI apiKey')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_CHATGPT_URL','TEXT','1',NULL,'AIモデル：OpenAI URL')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_GEMINI_MODEL','TEXT','1',NULL,'AIモデル：gemini モデル指定（既存機能互換）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_GEMINI_API_KEY','TEXT','1',NULL,'AIモデル：gemini apiKey（既存機能互換）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_GEMINI_URL','TEXT','1',NULL,'AIモデル：gemini URL（既存機能互換）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_BIGMODEL_OCR_API_KEY','TEXT','1',NULL,'AIモデル：BigModel / 智谱 OCR apiKey')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_BIGMODEL_OCR_URL','TEXT','1',NULL,'AIモデル：BigModel / 智谱 OCR URL')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('AI_MODEL','AI_BIGMODEL_OCR_MODEL','TEXT','1',NULL,'AIモデル：BigModel / 智谱 OCR モデル指定')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ---------------- ENGLISH_ESSAY (英作文AI添削) ----------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_ENABLED','ENUM','1','true,false','英作文：機能有効化（true/false）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_OCR_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2,bigmodel:1','英作文：画像分類・OCRで使用するAIモデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_TITLE_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','英作文：タイトル生成で使用するAIモデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_GRADING_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','英作文：作文添削で使用するAI模型')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_GRADING_REQUEST_TIMEOUT_SECONDS','INTEGER','1','30..1800','英作文：batC12 AI添削の1回のリクエストタイムアウト秒数（30～1800）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_GRADING_RETRY_LIMIT','INTEGER','1','0..5','英作文：batC12 AIエラーまたはJSON構造検証エラー時の作文単位の最大再実行回数（0～5）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_MAX_IMAGES','INTEGER','1','1..20','英作文：1回にアップロードできる画像枚数')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_MAX_IMAGE_MB','INTEGER','1','1..100','英作文：画像1枚あたりの最大サイズ（MB）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_OCR_MAX_IMAGE_PIXELS','INTEGER','1','512..8192','英作文：OCRでAIへ送信する画像の最大辺ピクセル（超える場合は縮小）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_OCR_REQUEST_TIMEOUT_SECONDS','INTEGER','1','30..1800','英作文：batC11 画像分類・OCRの1回のリクエストタイムアウト秒数（30～1800）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_OCR_RETRY_LIMIT','INTEGER','1','0..5','英作文：batC11 通信またはJSON構造検証エラー時の画像単位の最大再実行回数（0～5）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_OCR_PROMPT','TEXT','1',NULL,'英作文：画像分類・OCR System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_OCR_USER_PROMPT','TEXT','1',NULL,'英作文：画像分類・OCR User Prompt（入力変数およびJSON形式）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_TITLE_PROMPT','TEXT','1',NULL,'英作文：タイトル生成 System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_TITLE_USER_PROMPT','TEXT','1',NULL,'英作文：タイトル生成 User Prompt（{{level}}、{{question_text}}を実行時に置換）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_GRADING_PROMPT','TEXT','1',NULL,'英作文：英検基準添削 System Prompt（字数は問題文から判定）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_GRADING_USER_PROMPT','TEXT','1',NULL,'英作文：英検基準添削 User Prompt（入力変数およびJSON形式）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ---------------- ENGLISH_CLOZE (英語穴埋め問題) ----------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_MAX_IMAGES','INTEGER','1','1..20','英語穴埋め問題：1回にアップロードできる画像枚数')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_MAX_IMAGE_MB','INTEGER','1','1..100','英語穴埋め問題：画像1枚あたりの最大サイズ（MB）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_OCR_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','英語穴埋め問題：batC13 OCRで使用するAIモデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_OCR_MAX_IMAGE_PIXELS','INTEGER','1','512..8192','英語穴埋め問題：batC13 OCRでAIへ送信する画像の最大辺（px）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_OCR_TIMEOUT_SECONDS','INTEGER','1','30..1800','英語穴埋め問題：batC13 OCRタイムアウト秒数（30～1800）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_OCR_RETRY_LIMIT','INTEGER','1','0..5','英語穴埋め問題：batC13 通信またはJSON構造検証エラー時の画像単位の最大再実行回数（0～5）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_OCR_SYSTEM_PROMPT','TEXT','1',NULL,'英語穴埋め問題：batC13 OCR System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_OCR_USER_PROMPT','TEXT','1',NULL,'英語穴埋め問題：batC13 OCR User Prompt（入力変数およびJSON形式）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_EXPLANATION_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','英語穴埋め問題：問題別AI解説で使用するAIモデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_EXPLANATION_BATCH_MAX','INTEGER','1','1..100','英語穴埋め問題：一度にAI解説を生成する最大問題数')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_EXPLANATION_THREADS','INTEGER','1','1..10','英語穴埋め問題：batC14 同時処理スレッド数（1～10）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_EXPLANATION_REQUEST_TIMEOUT_SECONDS','INTEGER','1','30..1800','英語穴埋め問題：batC14 問題別AI解説の1回のリクエストタイムアウト秒数（30～1800）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_EXPLANATION_RETRY_LIMIT','INTEGER','1','0..5','英語穴埋め問題：batC14 AIエラーまたはJSON構造検証エラー時の問題単位の最大再実行回数（0～5）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_EXPLANATION_SYSTEM_PROMPT','TEXT','1',NULL,'answerStatus": "CORRECT')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_EXPLANATION_USER_PROMPT','TEXT','1',NULL,'英語穴埋め問題：問題別AI解説 User Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ---------------- ENGLISH_READING_INTENSIVE (英語読解・精読) ----------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_MAX_IMAGES','INTEGER','1','1..20','英語読解・精読：1回にアップロードできる画像枚数')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_MAX_IMAGE_MB','INTEGER','1','1..100','英語読解・精読：画像1枚あたりの最大サイズ（MB）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','英語読解・精読：第1段階OCRで使用するAIモデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_BAT_C15_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','英語読解・精読：batC15 集計・整形で使用するAIモデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_TIMEOUT_SECONDS','INTEGER','1','30..1800','英語読解・精読：第1段階OCR AIのリクエストタイムアウト（秒）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_MAX_RETRIES','INTEGER','1','0..5','英語読解・精読：第1段階OCR AI呼出の最大再実行回数')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_MAX_IMAGE_PIXELS','INTEGER','1','512..8192','英語読解・精読：OCRでAIへ送信する画像の最大辺ピクセル（超える場合は縮小）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_TEXT_MODE_METHOD','ENUM','1','A,B','英語読解・精読：文字モードのOCR処理方式（A=画像別並列、B=智谱OCR+AI構造化）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_METHOD_B_OCR_PROVIDER','ENUM','1','bigmodel:1','英語読解・精読：方式Bで画像を文字へ変換するOCR専用モデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_METHOD_A_ARTICLE_OCR_PROVIDER','ENUM','1','bigmodel:1','英語読解・精読：方式Aで文章画像を文字へ変換するOCR専用モデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_STRUCTURE_SYSTEM_PROMPT','TEXT','1',NULL,'英語読解・精読：文字モード方式B 構造化 System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_STRUCTURE_USER_PROMPT','TEXT','1',NULL,'英語読解・精読：文字モード方式B 構造化 User Prompt（{{image_count}}、{{image_categories_json}}、{{ocr_texts}}を置換）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_METHOD_A_STRUCTURE_SYSTEM_PROMPT','TEXT','1',NULL,'英語読解・精読：方式A 最終整形 System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_METHOD_A_STRUCTURE_USER_PROMPT','TEXT','1',NULL,'英語読解・精読：方式A 最終整形 User Prompt（{{image_count}}、{{image_categories_json}}、{{ocr_texts}}、{{question_structure_json}}を置換）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_SYSTEM_PROMPT','TEXT','1',NULL,'英語読解・精読：第1段階 OCR System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_USER_PROMPT','TEXT','1',NULL,'英語読解・精読：第1段階 OCR User Prompt（入力変数およびJSON形式）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_ARTICLE_SYSTEM_PROMPT','TEXT','1',NULL,'英語読解・精読：方式A 文章画像スレッド System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_ARTICLE_USER_PROMPT','TEXT','1',NULL,'英語読解・精読：方式A 文章画像スレッド User Prompt（{{image_count}}、{{image_categories_json}}を置換）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_QUESTION_SYSTEM_PROMPT','TEXT','1',NULL,'英語読解・精読：方式A 設問画像スレッド System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_QUESTION_USER_PROMPT','TEXT','1',NULL,'英語読解・精読：方式A 設問画像スレッド User Prompt（{{image_count}}、{{image_categories_json}}を置換）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_GUIDE_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','英語読解・精読：第2段階の基本情報・導読抽出で使用するAIモデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_GUIDE_TIMEOUT_SECONDS','INTEGER','1','30..1800','英語読解・精読：第2段階 基本情報・導読AIのタイムアウト（秒）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_GUIDE_SYSTEM_PROMPT','TEXT','1',NULL,'英語読解・精読：第2段階 基本情報・導読 System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_GUIDE_USER_PROMPT','TEXT','1',NULL,'英語読解・精読：第2段階 基本情報・導読 User Prompt（入力変数およびJSON形式）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_EXPLANATION_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','英語読解・精読：第3段階の逐文解説・重点語彙生成で使用するAIモデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_EXPLANATION_TIMEOUT_SECONDS','INTEGER','1','30..1800','英語読解・精読：第3段階 解説・語彙AIのタイムアウト（秒）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_EXPLANATION_BATCH_SIZE','INTEGER','1','1..50','英語読解・精読：第3段階で1回のAI呼び出しに含める文数')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_EXPLANATION_THREADS','INTEGER','1','1..5','英語読解・精読：第3段階の並列処理スレッド数（最大5）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_EXPLANATION_SYSTEM_PROMPT','TEXT','1',NULL,'英語読解・精読：第3段階 解説・語彙 System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_EXPLANATION_USER_PROMPT','TEXT','1',NULL,'英語読解・精読：第3段階 解説・語彙 User Prompt（入力変数およびJSON形式）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_QUESTION_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','英語読解・精読：第4段階の設問解析で使用するAIモデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_QUESTION_TIMEOUT_SECONDS','INTEGER','1','30..1800','英語読解・精読：第4段階 設問解析AIのタイムアウト（秒）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_QUESTION_SYSTEM_PROMPT','TEXT','1',NULL,'英語読解・精読：第4段階 設問解析 System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_QUESTION_USER_PROMPT','TEXT','1',NULL,'英語読解・精読：第4段階 設問解析 User Prompt（入力変数およびJSON形式）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ---------------- STUDY_MONITOR (学習状況モニター) ----------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_VIDEO_SOURCE_DIRECTORY','TEXT','1',NULL,'学習モニター：batL02が動画を取り込むソースフォルダ（サーバー絶対パス）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_SNAPSHOT_OUTPUT_DIRECTORY','TEXT','1',NULL,'学習モニター：batL02が切り出したスナップショットを保存するフォルダ（サーバー絶対パス）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_VIDEO_PROCESSING_START_TIME','TIME','1','HH:mm','学習モニター：batL02が処理する動画の撮影開始時刻（範囲開始・含む）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_VIDEO_PROCESSING_END_TIME','TIME','1','HH:mm','学習モニター：batL02が処理する動画の撮影開始時刻（範囲終了・含む）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_SNAPSHOT_INTERVAL_SECONDS','INTEGER','1','1..3600','学習モニター：batL02が動画からスナップショットを切り出す間隔（秒）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_AI_BATCH_LIMIT','INTEGER','1','1..100','学習モニター：batL03が手動実行1回あたりにAI分析する最大スナップショット数')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_AI_THREADS','INTEGER','1','1..10','学習モニター：batL03が同時にAI分析するスナップショット数')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_AI_TIMEOUT_SECONDS','INTEGER','1','30..600','学習モニター：batL03の画像1回あたりのAI応答待機時間（秒）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_AI_IMAGE_RESOLUTION','ENUM','1','3840×2160,2560×1440,1920×1080,1280×720','学習モニター：batL03がAIへ送信する画像解像度（元のスナップショットは変更しない）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_CAMERA_LOCATION','TEXT','1',NULL,'学習モニター：唯一のカメラの設置場所（AI入力には使用しない管理情報）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_CAMERA_CONTEXT','TEXT','1',NULL,'学習モニター：カメラの撮影範囲・補足（AI入力には使用しない管理情報）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_FIRST_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','学習モニター：AI分析（batL03）で使用するAIモデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_FIRST_SYSTEM_PROMPT','TEXT','1',NULL,'学習モニター：AI分析（batL03） System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('STUDY_MONITOR','STUDY_MONITOR_FIRST_USER_PROMPT','TEXT','1',NULL,'学習モニター：AI分析（batL03） User Prompt（画像左上の日時を確認し、画像だけを判定する）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ---------------- DAILY_REPORT (学習日報) ----------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('DAILY_REPORT','DAILY_REPORT_REMINDER_ENABLED','ENUM','1','true,false','学習日報：当日未記入時のホーム画面リマインダー（true/false）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- 学習日報の通知（提出時に日報の内容を LINE へ送る。送信の実装はこれから）
-- 学習日報の通知（提出時に日報の内容を LINE へ送る。送信の実装はこれから）
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('DAILY_REPORT','LINE_DAILY_REPORT_ENABLED','ENUM','0','true,false','学習日報：提出時に LINE へ送るか（true/false）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('DAILY_REPORT','LINE_DAILY_REPORT_TO','TEXT','0',NULL,'学習日報：送信先ID（userId / groupId / roomId。複数はカンマ区切り。空なら「デフォルト送信先ID」へ送る）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('DAILY_REPORT','LINE_DAILY_REPORT_SEND_ON_RESUBMIT','ENUM','0','true,false','学習日報：再提出（提出後に編集して再度提出）でも送るか（true/false）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('DAILY_REPORT','LINE_DAILY_REPORT_TEMPLATE','TEXT','0',NULL,'学習日報：メッセージ本文のテンプレート（{{日付}}・{{記入者}}・{{授業一覧}}・{{振り返り}}・{{今夜の勉強}}・{{提出日時}}・{{時限数}}・{{曜日}} を置換）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('DAILY_REPORT','LINE_DAILY_REPORT_LESSON_TEMPLATE','TEXT','0',NULL,'学習日報：授業1件のテンプレート（{{時限}}・{{教科}}・{{授業内容}}・{{掌握度}}・{{学習集中度}}・{{学習量}}・{{学習態度}}・{{ノート}} を置換。{{授業一覧}} の中で1件ずつ使う）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;


-- ---------------- ENGLISH_WORD_TEXTBOOK_AI (単語教材取込AI) ----------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_WORD_TEXTBOOK_AI','ENGLISH_WORD_TEXTBOOK_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','英単語教材取込：AIモデルページで設定した使用モデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_WORD_TEXTBOOK_AI','ENGLISH_WORD_TEXTBOOK_AI_COMPLETION_WINDOW','ENUM','1','24h,48h','英単語教材取込：Batch完了待ち時間')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_WORD_TEXTBOOK_AI','ENGLISH_WORD_TEXTBOOK_AI_SYSTEM_PROMPT','TEXT','1',NULL,'英単語教材取込：AI System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_WORD_TEXTBOOK_AI','ENGLISH_WORD_TEXTBOOK_AI_USER_PROMPT','TEXT','1',NULL,'英単語教材取込：AI User Prompt（{{left_page}}/{{right_page}}を置換）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_WORD_TEXTBOOK_AI','ENGLISH_WORD_TEXTBOOK_AI_MAX_IMAGE_PIXELS','INTEGER','1','512..8192','英単語教材取込：AI送信画像の最大辺（px）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_WORD_TEXTBOOK_AI','ENGLISH_WORD_TEXTBOOK_AI_TIMEOUT_SECONDS','INTEGER','1','30..3600','英単語教材取込：AI認識の処理タイムアウト（秒）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_WORD_TEXTBOOK_AI','ENGLISH_WORD_TEXTBOOK_AI_MAX_CONCURRENCY','INTEGER','1','1..20','英単語教材取込：AI認識の最大同時処理数（1〜20）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ---------------- ENGLISH_PHRASE_DETAIL_AI (英熟語詳細AI) ----------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','英熟語標準化及び分類：AIモデルページで設定した使用モデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_BATCH_SIZE','INTEGER','1','10..200','英熟語標準化及び分類：1回の最大熟語数（10語単位、10〜200）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_THREADS','INTEGER','1','1..20','英熟語標準化及び分類：処理スレッド数（1〜20）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_REQUEST_TIMEOUT_SECONDS','INTEGER','1','30..1800','英熟語標準化及び分類：単次リクエストタイムアウト秒数（30〜1800）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_MAX_COMPLETION_TOKENS','INTEGER','1','1024..65536','英熟語標準化及び分類：最大出力Token数（1024〜65536）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_TEMPERATURE','ENUM','1','0.0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0','英熟語標準化及び分類：Temperature（0.0〜2.0）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_AUTO_APPROVE_THRESHOLD','ENUM','1','0.70,0.75,0.80,0.85,0.90,0.95,1.00','英熟語標準化及び分類：自動完了とする信頼度しきい値')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_SYSTEM_PROMPT','TEXT','1',NULL,'英熟語標準化及び分類：System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_USER_PROMPT','TEXT','1',NULL,'英熟語標準化及び分類：User Prompt（{{phrase_id}}、{{original}}、{{book}}、{{classification}}を置換）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_RETRY_LIMIT','INTEGER','1','0..5','英熟語標準化及び分類：熟語単位の最大再実行回数（0〜5）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_DETAIL_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','英熟語詳細AI取得：AIモデルページで設定した使用モデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_DETAIL_AI_BATCH_SIZE','INTEGER','1','10..200','英熟語詳細AI取得：1回の最大熟語数（10語単位、10〜200）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_DETAIL_AI_THREADS','INTEGER','1','1..20','英熟語詳細AI取得：処理スレッド数（1〜20）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_DETAIL_AI_REQUEST_TIMEOUT_SECONDS','INTEGER','1','30..1800','英熟語詳細AI取得：単次リクエストタイムアウト秒数（30〜1800）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_DETAIL_AI_MAX_COMPLETION_TOKENS','INTEGER','1','1024..65536','英熟語詳細AI取得：最大出力Token数（1024〜65536）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_DETAIL_AI_TEMPERATURE','ENUM','1','0.0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0','英熟語詳細AI取得：Temperature（0.0〜2.0）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_DETAIL_AI_SYSTEM_PROMPT','TEXT','1',NULL,'英熟語詳細AI取得：構造化詳細知識 System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_DETAIL_AI_USER_PROMPT','TEXT','1',NULL,'英熟語詳細AI取得：User Prompt（熟語・標準構文・分類等を置換）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_DETAIL_AI_RETRY_LIMIT','INTEGER','1','0..5','英熟語詳細AI取得：熟語単位の最大再実行回数（0〜5）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ---------------- ENGLISH_WORD_DETAIL_AI (英単語詳細AI) ----------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_WORD_DETAIL_AI','ENGLISH_WORD_DETAIL_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','英単語詳細AI取得：AIモデルページで設定した使用モデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_WORD_DETAIL_AI','ENGLISH_WORD_DETAIL_AI_BATCH_SIZE','INTEGER','1','10..200','英単語詳細AI取得：1回の最大単語数（10語単位、10〜200）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_WORD_DETAIL_AI','ENGLISH_WORD_DETAIL_AI_THREADS','INTEGER','1','1..20','英単語詳細AI取得：処理スレッド数（1〜20）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_WORD_DETAIL_AI','ENGLISH_WORD_DETAIL_AI_REQUEST_TIMEOUT_SECONDS','INTEGER','1','30..1800','英単語詳細AI取得：単次リクエストタイムアウト秒数（30〜1800）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_WORD_DETAIL_AI','ENGLISH_WORD_DETAIL_AI_MAX_COMPLETION_TOKENS','INTEGER','1','1024..65536','英単語詳細AI取得：最大出力Token数（1024〜65536）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_WORD_DETAIL_AI','ENGLISH_WORD_DETAIL_AI_TEMPERATURE','ENUM','1','0.0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0,1.1,1.2,1.3,1.4,1.5,1.6,1.7,1.8,1.9,2.0','英単語詳細AI取得：Temperature（0.0〜2.0）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_WORD_DETAIL_AI','ENGLISH_WORD_DETAIL_AI_SYSTEM_PROMPT','TEXT','1',NULL,'英単語詳細AI取得：構造化詳細知識 System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_WORD_DETAIL_AI','ENGLISH_WORD_DETAIL_AI_USER_PROMPT','TEXT','1',NULL,'英単語詳細AI取得：User Prompt（{{word_id}}、{{word}}、{{book}}、{{classification}}を置換）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('ENGLISH_WORD_DETAIL_AI','ENGLISH_WORD_DETAIL_AI_RETRY_LIMIT','INTEGER','1','0..5','英単語詳細AI取得：単語単位の最大再実行回数（0〜5）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ---------------- JAPANESE_WORD_AI (日本語単語AI) ----------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C41_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','batC41 日本語単語詳細情報（A・B共通）：使用モデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C41_BATCH_MAX','INTEGER','1','1..100','batC41 日本語単語詳細情報：1回の最大単語数（1〜100）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C41_THREADS','INTEGER','1','1..10','batC41 日本語単語詳細情報：処理スレッド数（1〜10）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C41_REQUEST_TIMEOUT_SECONDS','INTEGER','1','30..1800','batC41 日本語単語詳細情報：単次リクエストタイムアウト秒数（30〜1800）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C41_MAX_COMPLETION_TOKENS','INTEGER','1','1024..65536','batC41 日本語単語詳細情報：最大出力Token数（1024〜65536）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C41_TEMPERATURE','ENUM','1','0.0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0,1.1,1.2,1.3,1.4,1.5,1.6,1.7,1.8,1.9,2.0','batC41 日本語単語詳細情報：Temperature（0.0〜2.0）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C41_SYSTEM_PROMPT','TEXT','1',NULL,'batC41 日本語単語詳細情報：System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C41_USER_PROMPT','TEXT','1',NULL,'batC41 日本語単語詳細情報：User Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C41_RETRY_LIMIT','INTEGER','1','0..5','batC41 日本語単語詳細情報：単語単位の最大再実行回数（0〜5）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C42_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','batC42 日本語C読み・漢字問題：使用モデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C42_BATCH_MAX','INTEGER','1','1..100','batC42 日本語C問題：1回の最大単語数（1〜100）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C42_THREADS','INTEGER','1','1..10','batC42 日本語C問題：処理スレッド数（1〜10）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C42_REQUEST_TIMEOUT_SECONDS','INTEGER','1','30..1800','batC42 日本語C問題：単次リクエストタイムアウト秒数（30〜1800）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C42_MAX_COMPLETION_TOKENS','INTEGER','1','1024..65536','batC42 日本語C問題：最大出力Token数（1024〜65536）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C42_TEMPERATURE','ENUM','1','0.0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0,1.1,1.2,1.3,1.4,1.5,1.6,1.7,1.8,1.9,2.0','batC42 日本語C問題：Temperature（0.0〜2.0）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C42_SYSTEM_PROMPT','TEXT','1',NULL,'batC42 日本語C読み・漢字問題：System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C42_USER_PROMPT','TEXT','1',NULL,'batC42 日本語C読み・漢字問題：User Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C42_RETRY_LIMIT','INTEGER','1','0..5','batC42 日本語C問題：単語単位の最大再実行回数（0〜5）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C43_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','batC43 日本語D文脈意味問題：使用モデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C43_BATCH_MAX','INTEGER','1','1..100','batC43 日本語D問題：1回の最大単語数（1〜100）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C43_THREADS','INTEGER','1','1..10','batC43 日本語D問題：処理スレッド数（1〜10）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C43_REQUEST_TIMEOUT_SECONDS','INTEGER','1','30..1800','batC43 日本語D問題：単次リクエストタイムアウト秒数（30〜1800）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C43_MAX_COMPLETION_TOKENS','INTEGER','1','1024..65536','batC43 日本語D問題：最大出力Token数（1024〜65536）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C43_TEMPERATURE','ENUM','1','0.0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0,1.1,1.2,1.3,1.4,1.5,1.6,1.7,1.8,1.9,2.0','batC43 日本語D問題：Temperature（0.0〜2.0）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C43_SYSTEM_PROMPT','TEXT','1',NULL,'batC43 日本語D文脈意味問題：System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C43_USER_PROMPT','TEXT','1',NULL,'batC43 日本語D文脈意味問題：User Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C43_RETRY_LIMIT','INTEGER','1','0..5','batC43 日本語D問題：単語単位の最大再実行回数（0〜5）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C44_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','batC44 日本語E漢字使分け問題：使用モデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C44_BATCH_MAX','INTEGER','1','1..100','batC44 日本語E問題：1回の最大単語数（1〜100）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C44_THREADS','INTEGER','1','1..10','batC44 日本語E問題：処理スレッド数（1〜10）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C44_REQUEST_TIMEOUT_SECONDS','INTEGER','1','30..1800','batC44 日本語E問題：単次リクエストタイムアウト秒数（30〜1800）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C44_MAX_COMPLETION_TOKENS','INTEGER','1','1024..65536','batC44 日本語E問題：最大出力Token数（1024〜65536）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C44_TEMPERATURE','ENUM','1','0.0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0,1.1,1.2,1.3,1.4,1.5,1.6,1.7,1.8,1.9,2.0','batC44 日本語E問題：Temperature（0.0〜2.0）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C44_SYSTEM_PROMPT','TEXT','1',NULL,'batC44 日本語E漢字使分け問題：System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C44_USER_PROMPT','TEXT','1',NULL,'batC44 日本語E漢字使分け問題：User Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('JAPANESE_WORD_AI','BAT_C44_RETRY_LIMIT','INTEGER','1','0..5','batC44 日本語E問題：単語単位の最大再実行回数（0〜5）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ---------------- LINE (LINE連携) ----------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('LINE','LINE_MESSAGING_CHANNEL_ACCESS_TOKEN','TEXT','1',NULL,'LINE送信：Messaging API チャネルアクセストークン')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('LINE','LINE_MESSAGING_PUSH_URL','TEXT','1',NULL,'LINE送信：Push API URL')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('LINE','LINE_MESSAGING_DEFAULT_TO','TEXT','1',NULL,'LINE送信：デフォルト送信先ID（userId / groupId / roomId。複数はカンマ区切り可）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('LINE','LINE_MESSAGING_CHANNEL_SECRET','TEXT','1',NULL,'LINE Webhook：Channel secret')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('LINE','LINE_MESSAGING_WEBHOOK_VALIDATE_SIGNATURE','ENUM','1','true,false','LINE Webhook：署名検証（true/false）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
