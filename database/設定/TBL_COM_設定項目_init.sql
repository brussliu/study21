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

-- ---------------- GEOMETRY_AI (AI生図・AI画図助手／図形管理) ----------------
-- 設計: tmp/geometry-ai-design.md §5。画面は「AI 生図（図形管理）」セクション
-- （frontend/pc-web/src/views/admin/system-settings/GeometryAiSettingsSection.vue）。
-- 画面に出すのは 7 キー（*_ENABLED / *_PROVIDER / *_MAX_IMAGE_MB / *_DEFAULT_CROP /
-- *_DEFAULT_KIND / *_APPROVAL / *_INSTRUCTION_TEMPLATE）で、残りはバッチ専用。
-- API Key は AI_MODEL ページ（AI_QWEN_API_KEY 等）を共用するのでここには作らない。
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_ENABLED','ENUM','1','true,false','AI生図とAI画図助手：有効／無効（無効なら【新規】に AI の導線を出さず、API は 409）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','AI生図：使用する視覚モデル（URL・API Key は AIモデルページの同じスロットを共用）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_MAX_IMAGE_MB','INTEGER','1','1..50','AI生図：画像 1 枚の最大サイズ（MB。画面のスライダーと同じ 1〜50）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_DEFAULT_CROP','ENUM','1','manual,center,all','AI生図：画面を開いたときの切り抜きの初期状態（manual=毎回指定 / center=中央 70% / all=全体）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_DEFAULT_KIND','ENUM','1','figure,function,mixed','AI生図：分類の初期値（figure=図形 / function=関数グラフ / mixed=判別が難しい複合図形）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_APPROVAL','ENUM','1','manual,auto','AI生図：処理結果の承認フロー（manual=作図画面で確認してから保存 / auto=そのまま保存。サーバーに GeoGebra が無いため当面 manual のみ）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_INSTRUCTION_TEMPLATE','TEXT','0',NULL,'AI生図：共通の User Prompt（タスクテンプレート）。空でよい（モード別（GEOMETRY_AI_<A〜D>_TASK_TEMPLATE）に書けばそれを使い、どちらも無ければテンプレート無しで実行する）。変数: {mode} {modeLabel} {resultType} {resultTypeLabel} {note} {supplements} {keepLabels} {maxCommands} {allowedCommands} {outputFormat} {outputSchema}')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_SYSTEM_PROMPT','TEXT','1',NULL,'AI生図：共通の System Prompt（必須）。モード別の System Prompt があれば、その前に連結して使う（モード側で上書きしない）。共通＋モード別＋DTO から自動生成した出力 Schema の順に 1 つのプロンプトになる。変数: {allowedCommands} {maxCommands} {outputSchema} など')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_OUTPUT_FORMAT','ENUM','1','JSON,COMMAND','AI生図：AI の出力契約（JSON=1 個の JSON / COMMAND=コマンド行のみ）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_REQUEST_TIMEOUT_SECONDS','INTEGER','1','30..1800','AI生図：AI API への 1 回の通信を待つ最大秒数（30〜1800）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_RETRY_LIMIT','INTEGER','1','0..3','AI生図：タイムアウト・5xx・JSON 不正時の再実行回数（0〜3。回数分だけ課金される）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_TEMPERATURE','ENUM','1','0.0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0','AI生図：Temperature（低めにして出力を安定させる）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_MAX_COMPLETION_TOKENS','INTEGER','1','1024..16384','AI生図：最大出力 Token 数（1024〜16384）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_MAX_COMMANDS','INTEGER','1','1..200','AI生図：生成コマンドの上限（検証 batC53 で使用。1〜200）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_ALLOWED_COMMANDS','TEXT','1',NULL,'AI生図：許可コマンドのホワイトリスト（カンマ区切り。先頭トークンで判定する）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_MAX_IMAGE_PIXELS','INTEGER','1','512..8192','AI生図：AI へ送る画像の最大辺ピクセル（超える場合は縮小。512〜8192）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_IMAGE_RETENTION_DAYS','INTEGER','1','1..365','AI生図：元画像・切り抜き画像の保持日数（batR02 のクリーンアップで削除。1〜365）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_MAX_CONCURRENCY','INTEGER','0','1..4','AI生図：同時に AI を呼ぶ数（非同期ワーカー導入時に使用。既定 1）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_DAILY_LIMIT_PER_ACCOUNT','INTEGER','1','0..100','AI生図：1 アカウント 1 日の生図回数（0=無制限。0〜100）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_ASSIST_ENABLED','ENUM','0','true,false','AI画図助手：有効／無効（無効なら作図画面の助手パネルを出さない）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_ASSIST_PROVIDER','ENUM','0','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','AI画図助手：使用するモデル')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_ASSIST_SYSTEM_PROMPT','TEXT','0',NULL,'AI画図助手：System Prompt（現在の作図を踏まえてコマンドの差分だけを返させる）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_ASSIST_USER_PROMPT','TEXT','0',NULL,'AI画図助手：User Prompt（{objects} {instruction} {maxCommands} を置換する）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_ASSIST_TIMEOUT_SECONDS','INTEGER','0','10..300','AI画図助手：1 リクエストのタイムアウト秒（10〜300。画面は同期で待つ）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_ASSIST_MAX_COMMANDS','INTEGER','0','1..50','AI画図助手：返せるコマンド数の上限（1〜50）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('GEOMETRY_AI','GEOMETRY_AI_ASSIST_DAILY_LIMIT_PER_ACCOUNT','INTEGER','0','0..200','AI画図助手：1 アカウント 1 日の指示回数（0=無制限。0〜200）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ---------------- CLASSROOM_AI (授業録音 / AI 授業記録) ----------------
-- 設計: tmp/classroom-ai-design.md §2.4。
-- 画面（システム設定「AI 授業記録（授業録音）」）に出して保存するのは 7 キー
-- （*_ENABLED / *_STT_PROVIDER / *_CHUNK_SECONDS / *_TRIGGER_INTERVAL_MINUTES /
--  *_TRIGGER_MIN_CHARS / *_TRIGGER_KEYWORDS / *_MAX_RECORDING_MINUTES）で、
-- 残り（STT 接続・言語マトリクス・クールダウン・保存期間・日次上限・話者分離・
-- ノート LLM の接続とプロンプトなど）はバッチ/ランタイム専用で画面には出さない
-- （GeometryAiSettingsSection と同じ方針。SettingPageFields には 7 キーだけ定義する）。
-- ノート LLM の URL・API Key は AI_MODEL ページのスロットを共用する（API Key はここでは seed しない）。
-- STT の API Key は seed しない（未設定なら日本語の理由でエラーにする）。
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_ENABLED','ENUM','1','true,false','授業録音と AI 授業ノート：有効／無効（無効なら録音 API は 409）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_STT_PROVIDER','ENUM','1','browser,stub,whisper,azure,google,other','STT プロバイダ（browser=ブラウザ音声認識（Chrome/Edge・キー不要・既定） / stub=ローカル開発用スタブ / whisper=OpenAI Whisper / azure / google / other）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_NOTE_ENABLED','BOOLEAN','0','true,false','授業の AI 解析（フェーズノート batC61 / 最終まとめ batC62）を使うか（既定 true。false なら書き起こしだけ）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_STT_ENDPOINT','TEXT','0',NULL,'STT エンドポイント URL（OpenAI 互換 /audio/transcriptions 想定）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_STT_API_KEY','TEXT','0',NULL,'STT API Key（seed しない。未設定なら日本語の理由でエラー）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_STT_MODEL','TEXT','0',NULL,'STT モデル名（例 whisper-1）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_STT_TIMEOUT_SECONDS','INTEGER','0','5..300','STT API への 1 回の通信を待つ最大秒数（5〜300）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_CHUNK_SECONDS','INTEGER','1','5..120','アップロード分塊の長さ（秒。既定 20）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_LANG_ZH','TEXT','0',NULL,'言語モード 中国語 → STT 言語コード（既定 zh）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_LANG_JA','TEXT','0',NULL,'言語モード 日本語 → STT 言語コード（既定 ja）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_LANG_EN','TEXT','0',NULL,'言語モード 英語 → STT 言語コード（既定 en）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_LANG_ZH_EN','TEXT','0',NULL,'言語モード 中国語＋英語 → STT 言語コード（既定 zh）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_LANG_JA_EN','TEXT','0',NULL,'言語モード 日本語＋英語 → STT 言語コード（既定 ja）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_LANG_AUTO','TEXT','0',NULL,'言語モード 自動判別 → STT 言語コード（既定は空=自動）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_TRIGGER_INTERVAL_MINUTES','INTEGER','1','3..20','間隔トリガー：直前フェーズからこの分経過したらノート更新（既定 5）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_TRIGGER_MIN_CHARS','INTEGER','1','100..10000','文字量トリガー：累積転写文字数がこの文字数を超えたら更新（既定 200）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_TRIGGER_KEYWORDS','TEXT','1',NULL,'キーワードトリガー（カンマ区切り。例 宿題,試験の重点）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_TRIGGER_COOLDOWN_MINUTES','INTEGER','0','1..30','連発防止クールダウン（前回フェーズからこの分未満なら抑制。既定 3）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_MAX_RECORDING_MINUTES','INTEGER','1','1..240','録音最大時間（分。既定 120。超えた分はアップロードを拒否）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_RETENTION_DAYS','INTEGER','1','1..365','保存期間（日。既定 30。batR02 のクリーンアップで掃除）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_DAILY_LIMIT_PER_ACCOUNT','INTEGER','0','0..100','1 アカウント 1 日の録音回数（0=無制限。0〜100）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_NOTE_PROVIDER','ENUM','1','qwen:1,qwen:2,qwen:3,qwen:4,qwen:5,doubao:1,doubao:2,deepseek:1,deepseek:2,chatgpt:1,chatgpt:2','ノート/要約の LLM スロット（URL・API Key は AIモデルページの同じスロットを共用）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_NOTE_SYSTEM_PROMPT','TEXT','0',NULL,'フェーズノートの System Prompt（4 つのキーを持つ JSON を返させる）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_NOTE_USER_PROMPT','TEXT','0',NULL,'フェーズノートの User Prompt（{transcript} を置換する）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_SUMMARY_SYSTEM_PROMPT','TEXT','0',NULL,'最終まとめの System Prompt')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_SUMMARY_USER_PROMPT','TEXT','0',NULL,'最終まとめの User Prompt（{transcript} を置換する）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_NOTE_TIMEOUT_SECONDS','INTEGER','0','30..600','ノート生成 API への 1 回の通信を待つ最大秒数（30〜600）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_NOTE_MAX_COMPLETION_TOKENS','INTEGER','0','512..16384','ノート生成の最大出力 Token 数（512〜16384）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('CLASSROOM_AI','CLASSROOM_AI_VIEW_SCOPE','ENUM','0','self,family','閲覧範囲（self=自分のみ / family=自分の家族。既定 family）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
