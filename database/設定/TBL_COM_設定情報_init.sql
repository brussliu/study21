-- ============================================================================
-- Study 2.1  設定値 初期データ（自動生成）
-- テーブル: COM_設定情報
-- 生成元: study2/study2 SettingServiceImpl.java の DEFINITIONS（既定値をそのまま初期値化）
-- スコープ: すべて GLOBAL。学生ID/保護者ID は NULL。
-- 空文字の既定値（APIキー等）は登録しない（管理者が後で設定）。
-- 実行順序: TBL_COM_設定項目.sql → TBL_COM_設定項目_init.sql → TBL_COM_設定情報.sql の後
-- ============================================================================

-- ---------------- TRANSLATION ----------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('TRANSLATION','ZH_WORD_TRANSLATE_API','GLOBAL','YouDao') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('TRANSLATION','ZH_SENTENCE_TRANSLATE_API','GLOBAL','YouDao') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('TRANSLATION','ZH_TRANSLATE_THREADS','GLOBAL','5') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('TRANSLATION','JA_WORD_TRANSLATE_API','GLOBAL','ExcelAPI') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('TRANSLATION','JA_SENTENCE_TRANSLATE_API','GLOBAL','ExcelAPI') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('TRANSLATION','JA_TRANSLATE_THREADS','GLOBAL','5') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- ---------------- VOICE ----------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('VOICE','EN_WORD_VOICE_API','GLOBAL','YouDao') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('VOICE','EN_SENTENCE_VOICE_API','GLOBAL','YouDao') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('VOICE','EN_VOICE_THREADS','GLOBAL','5') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- ---------------- WORD_QUESTION ----------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_AI_MODEL','GLOBAL','chatgpt:1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_THREADS','GLOBAL','5') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_SYSTEM_PROMPT_ZH','GLOBAL','你是一名为中文学习者编写英语词汇语境选择题的英语教师。
                    必须把目标内容作为一个完整的单词、短语、固定搭配或短语动词处理，不能拆开解释。
                    输出要求：
                    1. question：自然、简洁、地道的英文句子，并使用目标英文表达或符合语境的自然语法变化形式。
                    2. correct：目标英文表达在该语境下最合适的简体中文意思。
                    3. wrong1 到 wrong5：5个简体中文错误选项，并满足：
                       - 不能与 correct 重复
                       - 彼此不能重复
                       - 不能只是 correct 的近义改写
                       - 要有一定迷惑性，但在该句语境下不正确
                       - 表达粒度尽量与 correct 接近
                    4. explain：用简体中文说明目标英文表达的实际含义、用法以及正确答案的理由。
                    5. 不要输出拼音、音标、序号、Markdown、代码块或任何额外说明。
                    6. 必须返回一个可被JSON解析器直接解析的合法JSON对象。所有字段名和值必须使用英文双引号，字符串内禁止出现未转义的换行符，逗号、冒号、括号必须完整。JSON之外不得有任何内容。
                    只返回 1 个 JSON 对象，且必须严格包含以下字段：
                    question, correct, wrong1, wrong2, wrong3, wrong4, wrong5, explain') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_USER_PROMPT_ZH','GLOBAL','目标英文表达："{word}"
                    题目语言类型：{language}

                    请围绕这个英文表达生成1道语境选择题。question必须使用目标表达或符合语境的自然语法变化形式。
                    请严格按照System Prompt指定的JSON格式返回。输出必须是可以直接解析的合法JSON，字符串内禁止出现未转义的换行符，逗号和括号必须完整。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_SYSTEM_PROMPT_JA','GLOBAL','あなたは日本語学習者向けの英語語彙・語句の文脈選択問題を作成する英語教師です。
                    対象は単語、フレーズ、熟語、句動詞のいずれでも、必ず表現全体を一つの意味単位として扱ってください。
                    出力要件：
                    1. question：自然で簡潔な英語の文を1つ作り、対象表現または文脈に適した自然で正しい活用形を使用すること。
                    2. correct：その文脈における対象表現の最も適切な日本語の意味を書くこと。
                    3. wrong1 から wrong5：日本語の誤答を 5 つ作り、以下を満たすこと。
                       - correct と重複しない
                       - 相互に重複しない
                       - correct の単なる言い換えにしない
                       - ある程度もっともらしいが、その文脈では不適切
                       - 表現の粒度は correct に近づける
                    4. explain：日本語で文脈における意味・用法と、correct が適切な理由を簡潔に説明すること。
                    5. 箇条書き、Markdown、コードブロック、余計な説明は禁止。
                    6. JSONパーサーで直接解析できる合法なJSONを返してください。フィールド名と値は英語のダブルクォートで囲み、文字列内にエスケープされていない改行を含めないでください。カンマ、コロン、括弧は欠落や重複がない状態にしてください。
                    JSON のみを返してください。
                    必須フィールド：
                    question, correct, wrong1, wrong2, wrong3, wrong4, wrong5, explain') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_USER_PROMPT_JA','GLOBAL','対象の英語表現："{word}"
                    問題の言語種別：{language}

                    この英語表現を使った文脈選択問題を1問作成してください。questionには対象表現または文脈に適した自然で正しい活用形を使用してください。
                    System Promptで指定されたJSON形式だけを返してください。JSONパーサーで直接解析できる合法JSONである必要があります。文字列内の改行は必ずエスケープし、カンマや括弧を欠落させないでください。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_2_AI_PROVIDER','GLOBAL','chatgpt:1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_2_BATCH_SIZE','GLOBAL','50') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_2_THREADS','GLOBAL','5') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_2_REQUEST_TIMEOUT_SECONDS','GLOBAL','300') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_2_MAX_COMPLETION_TOKENS','GLOBAL','4000') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_2_TEMPERATURE','GLOBAL','0.2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_2_SYSTEM_PROMPT','GLOBAL','あなたは日本語・中国語話者向け英単語テストの編集者です。
                    入力された英単語の正解語義と例文は変更せず、D.英訳中日四択問題用の誤答を作成してください。

                    【最重要】distractorsは「誤答だけ」の配列です。全選択肢の配列ではありません。
                    正解はシステムが別途追加するため、正解、日本語・中国語の正解翻訳、正解の言い換え、正解の類義語を
                    distractorsへ絶対に入れないでください。正解を先頭要素として追加することも禁止します。

                    必ず次のJSONだけを返してください。
                    {
                      "word":"入力された見出し語",
                      "distractors":[
                        {"japanese":"日本語の誤答","chinese":"対応する中国語の誤答"},
                        {"japanese":"日本語の誤答","chinese":"対応する中国語の誤答"},
                        {"japanese":"日本語の誤答","chinese":"対応する中国語の誤答"},
                        {"japanese":"日本語の誤答","chinese":"対応する中国語の誤答"},
                        {"japanese":"日本語の誤答","chinese":"対応する中国語の誤答"}
                      ],
                      "exampleEnglish":"対象語義の英語例文",
                      "exampleJapanese":"例文の日本語訳",
                      "exampleChinese":"例文の中国語訳",
                      "explanationJapanese":"正解語義と例文を説明する日本語解説",
                      "explanationChinese":"正解語義と例文を説明する中国語解説"
                    }

                    ルール：
                    1. distractorsは3〜5件にし、可能な限り5件作成してください。
                    2. distractorsの各要素には、空でない文字列のjapaneseとchineseを必ず両方出力してください。片方の省略、null、空文字は禁止します。
                    3. 各誤答の日本語と中国語は同じ意味にしてください。
                    4. 正解と同じ内容、正解の一部、単なる表記違い、言い換え、類義語を誤答にしないでください。日本語か中国語のどちらか一方でも正解と同じなら禁止です。
                    5. 誤答は相互に重複させず、品詞と意味の粒度を正解に合わせてください。
                    6. もっともらしいが、入力された対象語義としては明確に不正解な内容にしてください。他の語義が入力JSONにあっても、対象語義の正解候補として混ぜないでください。
                    7. 代表例文が入力されている場合は変更せず出力し、空の場合は対象語義に合う自然な例文と日中訳を作成してください。
                    8. 個別入力項目と元の単語詳細JSONが競合する場合は、正解_日本語、正解_中国語、代表例文の個別入力項目を優先してください。
                    9. 出力直前に、(a)正解がdistractorsにない、(b)全要素にjapanese/chineseがある、(c)誤答同士が重複しない、の3点を内部確認し、違反があれば修正してから出力してください。
                    10. Markdown、コードブロック、追加説明は禁止します。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_2_USER_PROMPT','GLOBAL','次の確定済み単語詳細を使ってD.英訳中日問題データを生成してください。
                    見出し語: {{word}}
                    単語ID: {{word_id}}
                    書籍: {{book}}
                    分類: {{classification}}
                    語義番号: {{sense_number}}
                    品詞: {{part_of_speech}}
                    正解_日本語: {{correct_japanese}}
                    正解_中国語: {{correct_chinese}}
                    代表例文_英語: {{example_english}}
                    代表例文_日本語: {{example_japanese}}
                    代表例文_中国語: {{example_chinese}}

                    元の単語詳細JSON:
                    {{source_json}}

                    最優先指示：
                    - distractorsには誤答だけを出力し、正解_日本語・正解_中国語と同じ内容を絶対に含めないでください。
                    - distractorsの全要素に、空でないjapaneseとchineseを必ず両方出力してください。
                    - 正解はシステムが追加するため、あなたが選択肢へ追加する必要はありません。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_2_RETRY_LIMIT','GLOBAL','2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_3_AI_PROVIDER','GLOBAL','chatgpt:1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_3_BATCH_SIZE','GLOBAL','50') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_3_THREADS','GLOBAL','5') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_3_REQUEST_TIMEOUT_SECONDS','GLOBAL','300') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_3_MAX_COMPLETION_TOKENS','GLOBAL','4000') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_3_TEMPERATURE','GLOBAL','0.2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_3_SYSTEM_PROMPT','GLOBAL','あなたは日本語・中国語話者向け英単語テストの編集者です。
                    入力された英単語の基本語義と例文を変更せず、E.文脈英訳四択問題用のデータを作成してください。

                    【最重要】distractorsは「誤答だけ」の配列です。全選択肢の配列ではありません。
                    正解はシステムが別途追加するため、correctJapanese、correctChinese、それらの言い換え・類義語を
                    distractorsへ絶対に入れないでください。正解を先頭要素として追加することも禁止します。

                    必ず次のJSONだけを返してください。
                    {
                      "word":"入力された見出し語",
                      "sentenceEnglish":"見出し語を含む英語例文",
                      "sentenceJapanese":"例文の日本語訳",
                      "sentenceChinese":"例文の中国語訳",
                      "correctJapanese":"この文脈における正解の日本語意味",
                      "correctChinese":"この文脈における正解の中国語意味",
                      "distractors":[
                        {"japanese":"日本語の誤答","chinese":"対応する中国語の誤答"},
                        {"japanese":"日本語の誤答","chinese":"対応する中国語の誤答"},
                        {"japanese":"日本語の誤答","chinese":"対応する中国語の誤答"},
                        {"japanese":"日本語の誤答","chinese":"対応する中国語の誤答"},
                        {"japanese":"日本語の誤答","chinese":"対応する中国語の誤答"}
                      ],
                      "explanationJapanese":"正解語義と例文を説明する日本語解説",
                      "explanationChinese":"正解語義と例文を説明する中国語解説"
                    }

                    ルール：
                    1. distractorsは3〜5件にし、可能な限り5件作成してください。
                    2. distractorsの各要素には、空でない文字列のjapaneseとchineseを必ず両方出力してください。片方の省略、null、空文字は禁止します。
                    3. 各誤答の日本語と中国語は同じ意味にしてください。
                    4. 正解と同じ内容、正解の一部、単なる表記違い、言い換え、類義語を誤答にしないでください。日本語か中国語のどちらか一方でも正解と同じなら禁止です。
                    5. 誤答は相互に重複させず、品詞と意味の粒度を正解に合わせてください。
                    6. もっともらしいが、この例文の文脈では不適切な意味にしてください。他の語義が入力JSONにあっても、対象語義の正解候補として混ぜないでください。
                    7. 代表例文が入力されている場合は変更せず出力し、空の場合は対象語義に合う自然な例文と日中訳を作成してください。
                    8. sentenceEnglishには見出し語またはその自然な活用形を含めてください。
                    9. correctJapaneseとcorrectChineseは、入力された正解_日本語・正解_中国語を一文字も変更せず、そのまま複写してください。不自然な表記や誤字に見えても訂正、翻訳、言い換え、句読点変更をしないでください。
                    10. 個別入力項目と元の単語詳細JSONが競合する場合は、正解_日本語、正解_中国語、代表例文の個別入力項目を優先してください。
                    11. 出力直前に、(a)正解項目が入力と完全一致する、(b)正解がdistractorsにない、(c)全要素にjapanese/chineseがある、(d)誤答同士が重複しない、の4点を内部確認し、違反があれば修正してから出力してください。
                    12. Markdown、コードブロック、追加説明は禁止します。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_3_USER_PROMPT','GLOBAL','次の確定済み単語詳細を使ってE.文脈英訳問題データを生成してください。
                    見出し語: {{word}}
                    単語ID: {{word_id}}
                    書籍: {{book}}
                    分類: {{classification}}
                    語義番号: {{sense_number}}
                    品詞: {{part_of_speech}}
                    正解_日本語: {{correct_japanese}}
                    正解_中国語: {{correct_chinese}}
                    代表例文_英語: {{example_english}}
                    代表例文_日本語: {{example_japanese}}
                    代表例文_中国語: {{example_chinese}}

                    元の単語詳細JSON:
                    {{source_json}}

                    最優先指示：
                    - correctJapaneseには「{{correct_japanese}}」、correctChineseには「{{correct_chinese}}」を一文字も変更せず複写してください。
                    - distractorsには誤答だけを出力し、上記正解と同じ内容を絶対に含めないでください。
                    - distractorsの全要素に、空でないjapaneseとchineseを必ず両方出力してください。
                    - 正解はシステムが追加するため、あなたが選択肢へ追加する必要はありません。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C04_3_RETRY_LIMIT','GLOBAL','2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C33_AI_PROVIDER','GLOBAL','chatgpt:1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C33_BATCH_SIZE','GLOBAL','50') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C33_THREADS','GLOBAL','5') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C33_REQUEST_TIMEOUT_SECONDS','GLOBAL','300') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C33_MAX_COMPLETION_TOKENS','GLOBAL','4000') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C33_TEMPERATURE','GLOBAL','0.2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C33_SYSTEM_PROMPT','GLOBAL','あなたは日本語・中国語話者向け英語熟語テストの編集者です。
                    入力された英語熟語・表現の正解意味と例文は変更せず、D.表現意味選択四択問題用の誤答を作成してください。

                    【最重要】distractorsは「誤答だけ」の配列です。全選択肢の配列ではありません。
                    正解はシステムが別途追加するため、正解、日本語・中国語の正解翻訳、正解の言い換え、正解の類義語を
                    distractorsへ絶対に入れないでください。正解を先頭要素として追加することも禁止します。

                    必ず次のJSONだけを返してください。
                    {
                      "phrase":"入力された表示表現",
                      "distractors":[{"japanese":"日本語の誤答","chinese":"対応する中国語の誤答"}],
                      "exampleEnglish":"対象表現の英語例文",
                      "exampleJapanese":"例文の日本語訳",
                      "exampleChinese":"例文の中国語訳",
                      "explanationJapanese":"正解意味と例文を説明する日本語解説",
                      "explanationChinese":"正解意味と例文を説明する中国語解説"
                    }

                    ルール：
                    1. distractorsは3〜5件にし、可能な限り5件作成してください。
                    2. distractorsの各要素には、空でない文字列のjapaneseとchineseを必ず両方出力してください。
                    3. 各誤答の日本語と中国語は同じ意味にしてください。
                    4. 正解と同じ内容、正解の一部、言い換え、類義語を誤答にしないでください。
                    5. 誤答は相互に重複させず、意味の粒度を正解に合わせてください。
                    6. もっともらしいが、対象表現の意味としては明確に不正解な内容にしてください。
                    7. 代表例文が入力されている場合は変更せず出力し、空の場合は自然な例文と日中訳を作成してください。
                    8. 出力直前に、(a)正解がdistractorsにない、(b)全要素にjapanese/chineseがある、(c)誤答同士が重複しない、を内部確認してください。
                    9. Markdown、コードブロック、追加説明は禁止します。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C33_USER_PROMPT','GLOBAL','次の確定済み熟語詳細を使ってD.表現意味選択問題データを生成してください。
                    表示表現: {{phrase}}
                    熟語ID: {{phrase_id}}
                    書籍: {{book}}
                    分類: {{classification}}
                    標準構文: {{standard_pattern}}
                    正解_日本語: {{correct_japanese}}
                    正解_中国語: {{correct_chinese}}
                    代表例文_英語: {{example_english}}
                    代表例文_日本語: {{example_japanese}}
                    代表例文_中国語: {{example_chinese}}

                    元の熟語詳細JSON:
                    {{source_json}}

                    最優先指示：
                    - distractorsには誤答だけを出力し、正解_日本語・正解_中国語と同じ内容を絶対に含めないでください。
                    - distractorsの全要素に、空でないjapaneseとchineseを必ず両方出力してください。
                    - 正解はシステムが追加するため、あなたが選択肢へ追加する必要はありません。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C33_RETRY_LIMIT','GLOBAL','2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C34_AI_PROVIDER','GLOBAL','chatgpt:1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C34_BATCH_SIZE','GLOBAL','50') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C34_THREADS','GLOBAL','5') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C34_REQUEST_TIMEOUT_SECONDS','GLOBAL','300') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C34_MAX_COMPLETION_TOKENS','GLOBAL','4000') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C34_TEMPERATURE','GLOBAL','0.2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C34_SYSTEM_PROMPT','GLOBAL','あなたは日本語・中国語話者向け英語熟語テストの編集者です。
                    入力された英語熟語・表現の正解意味と例文を変更せず、E.文脈意味選択四択問題用のデータを作成してください。

                    【最重要】distractorsは「誤答だけ」の配列です。全選択肢の配列ではありません。
                    正解はシステムが別途追加するため、correctJapanese、correctChinese、それらの言い換え・類義語を
                    distractorsへ絶対に入れないでください。正解を先頭要素として追加することも禁止します。

                    必ず次のJSONだけを返してください。
                    {
                      "phrase":"入力された表示表現",
                      "sentenceEnglish":"表示表現を含む英語例文",
                      "sentenceJapanese":"例文の日本語訳",
                      "sentenceChinese":"例文の中国語訳",
                      "correctJapanese":"この文脈における正解の日本語意味",
                      "correctChinese":"この文脈における正解の中国語意味",
                      "distractors":[{"japanese":"日本語の誤答","chinese":"対応する中国語の誤答"}],
                      "explanationJapanese":"正解意味と例文を説明する日本語解説",
                      "explanationChinese":"正解意味と例文を説明する中国語解説"
                    }

                    ルール：
                    1. distractorsは3〜5件にし、可能な限り5件作成してください。
                    2. distractorsの各要素には、空でない文字列のjapaneseとchineseを必ず両方出力してください。
                    3. 各誤答の日本語と中国語は同じ意味にしてください。
                    4. 正解と同じ内容、正解の一部、言い換え、類義語を誤答にしないでください。
                    5. 誤答は相互に重複させず、意味の粒度を正解に合わせてください。
                    6. もっともらしいが、この例文の文脈では不適切な意味にしてください。
                    7. 代表例文が入力されている場合は変更せず出力し、空の場合は自然な例文と日中訳を作成してください。
                    8. sentenceEnglishには表示表現またはその自然な活用形を含めてください。
                    9. correctJapaneseとcorrectChineseは、入力された正解_日本語・正解_中国語を一文字も変更せず複写してください。
                    10. 出力直前に、(a)正解項目が入力と完全一致する、(b)正解がdistractorsにない、(c)全要素にjapanese/chineseがある、(d)誤答同士が重複しない、を内部確認してください。
                    11. Markdown、コードブロック、追加説明は禁止します。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C34_USER_PROMPT','GLOBAL','次の確定済み熟語詳細を使ってE.文脈意味選択問題データを生成してください。
                    表示表現: {{phrase}}
                    熟語ID: {{phrase_id}}
                    書籍: {{book}}
                    分類: {{classification}}
                    標準構文: {{standard_pattern}}
                    正解_日本語: {{correct_japanese}}
                    正解_中国語: {{correct_chinese}}
                    例文_英語: {{sentence_english}}
                    例文_日本語: {{sentence_japanese}}
                    例文_中国語: {{sentence_chinese}}
                    対象表現: {{target_expression}}

                    元の熟語詳細JSON:
                    {{source_json}}

                    最優先指示：
                    - correctJapaneseには「{{correct_japanese}}」、correctChineseには「{{correct_chinese}}」を一文字も変更せず複写してください。
                    - distractorsには誤答だけを出力し、上記正解と同じ内容を絶対に含めないでください。
                    - distractorsの全要素に、空でないjapaneseとchineseを必ず両方出力してください。
                    - 正解はシステムが追加するため、あなたが選択肢へ追加する必要はありません。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_QUESTION','BAT_C34_RETRY_LIMIT','GLOBAL','2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- ---------------- WORD_EXPLANATION ----------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_EXPLANATION','BAT_C05_AI_MODEL','GLOBAL','chat-gpt') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_EXPLANATION','BAT_C05_THREADS','GLOBAL','5') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_EXPLANATION','BAT_C05_PROMPT_ZH','GLOBAL','你是英语老师，请用中文讲解一下“{word}”这个英文“{flg}”，要求:
                    1，词性，发音和含义
                    2，常见搭配和用法
                    3，词形变化（没有的话可以不写）
                    4，同义词，近义词，反义词等
                    5，两个例句
                    6，两道相关考题') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_EXPLANATION','BAT_C06_AI_MODEL','GLOBAL','chat-gpt') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_EXPLANATION','BAT_C06_THREADS','GLOBAL','5') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('WORD_EXPLANATION','BAT_C06_PROMPT_JA','GLOBAL','英単語「{word}」について、日本語で意味・使い方・注意点を簡潔に説明してください。JSONは不要で、説明本文のみ返してください。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- ---------------- AI_MODEL ----------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_QWEN_MODEL','GLOBAL','qwen3.7-flash') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_QWEN_MODEL_2','GLOBAL','qwen3.7-plus') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_QWEN_MODEL_3','GLOBAL','Qwen3-VL-Flash') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_QWEN_MODEL_4','GLOBAL','Qwen3-VL-Plus') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_QWEN_MODEL_5','GLOBAL','qwen-vl-ocr-latest') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_QWEN_URL','GLOBAL','https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_DOUBAO_MODEL','GLOBAL','doubao-seed-2-1-turbo') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_DOUBAO_MODEL_2','GLOBAL','doubao-seed-2-1-pro') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_DOUBAO_URL','GLOBAL','https://ark.cn-beijing.volces.com/api/v3/chat/completions') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_DEEPSEEK_MODEL','GLOBAL','deepseek-v4-flash') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_DEEPSEEK_MODEL_2','GLOBAL','deepseek-v4-pro') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_DEEPSEEK_URL','GLOBAL','https://api.deepseek.com/v1/chat/completions') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_CHATGPT_MODEL','GLOBAL','gpt-5.4-mini') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_CHATGPT_MODEL_2','GLOBAL','gpt-5.6-terra') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_CHATGPT_URL','GLOBAL','https://api.openai.com/v1/chat/completions') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_GEMINI_MODEL','GLOBAL','gemini-1.5-flash') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_GEMINI_URL','GLOBAL','https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_BIGMODEL_OCR_URL','GLOBAL','https://open.bigmodel.cn/api/paas/v4/layout_parsing') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('AI_MODEL','AI_BIGMODEL_OCR_MODEL','GLOBAL','glm-ocr') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- ---------------- ENGLISH_ESSAY ----------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_ENABLED','GLOBAL','true') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_OCR_AI_PROVIDER','GLOBAL','chatgpt:1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_TITLE_AI_PROVIDER','GLOBAL','chatgpt:1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_GRADING_AI_PROVIDER','GLOBAL','chatgpt:1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_MAX_IMAGES','GLOBAL','8') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_MAX_IMAGE_MB','GLOBAL','10') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_OCR_MAX_IMAGE_PIXELS','GLOBAL','2048') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_OCR_REQUEST_TIMEOUT_SECONDS','GLOBAL','600') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_OCR_RETRY_LIMIT','GLOBAL','1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_GRADING_REQUEST_TIMEOUT_SECONDS','GLOBAL','600') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_GRADING_RETRY_LIMIT','GLOBAL','1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_OCR_PROMPT','GLOBAL','あなたは英語試験文書のOCRおよびレイアウト分析アシスタントです。
                    複数画像を question、answer、both、unknown に分類し、作文問題と学生の手書き作文を別々に抽出してください。
                    複数ページは画像順に結合してください。学生原文の綴り、文法、大文字小文字、句読点の誤りは修正せず、そのまま保持してください。
                    判読できない箇所は [unclear] とし、推測しないでください。JSONのみを返してください。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_OCR_USER_PROMPT','GLOBAL','対象の英検級：
                    {{level}}

                    画像枚数：
                    {{image_count}}

                    ユーザーが指定した各画像の種別：
                    {{image_categories}}

                    添付された画像を順番に解析し、作文問題と学生の手書き作文を抽出してください。
                    ユーザー指定が auto の画像は、内容から question、answer、both、unknown のいずれかを判定してください。
                    ユーザー指定がある場合も画像内容と矛盾していないか確認し、矛盾があれば warnings に記録してください。

                    次のJSON構造のみを返してください。
                    {
                      "pages": [
                        {
                          "pageNumber": 1,
                          "imageType": "question|answer|both|unknown",
                          "questionText": "",
                          "essayText": "",
                          "confidence": 0,
                          "warnings": []
                        }
                      ],
                      "questionText": "結合後の完全な作文問題",
                      "essayText": "結合後の学生作文原文",
                      "questionConfidence": 0,
                      "essayConfidence": 0,
                      "detectedRequirements": {
                        "wordRequirementSource": "",
                        "requiredReasonCount": null,
                        "requiredPointCount": null,
                        "points": [],
                        "otherRequirements": []
                      },
                      "warnings": []
                    }') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_TITLE_PROMPT','GLOBAL','あなたは英検英作文の主題タイトル生成アシスタントです。
                    OCRで抽出された questionText の中心テーマ・論点だけを要約してください。
                    titleJa は自然な日本語の短い名詞句、titleZh は自然な簡体中国語の短い名詞句にしてください。
                    設問文の全文翻訳、汎用語、英作文問題、作文の設問、英语作文题目は禁止です。
                    titleJa は8～25文字、titleZh は6～20文字を目安にし、英単語・英文・ローマ字を含めないでください。
                    JSONのみを返してください。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_TITLE_USER_PROMPT','GLOBAL','対象の英検級：
                    {{level}}

                    OCRで抽出した設問：
                    {{question_text}}

                    次のJSON構造のみを返してください。
                    {
                      "titleJa": "日本語の具体的な主題タイトル",
                      "titleZh": "简体中文的具体主题标题"
                    }') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_GRADING_PROMPT','GLOBAL','あなたは日本実用英語技能検定（英検）のライティング指導に詳しい英語教師です。
                    作文問題に実際に記載された指示と対象級に基づき、内容、構成、語彙、文法の4観点で学生作文を添削してください。

                    1級は各観点0～8点、合計32点、準1級と2級は各観点0～4点、合計16点として評価してください。

                    字数条件は必ず作文問題本文から抽出してください。級別の一般的な字数を固定条件として使用してはいけません。
                    問題に字数条件がない場合は type を not_specified、最小・最大字数を null とし、字数を理由に減点しないでください。
                    条件が不明確な場合は type を unclear とし、警告を出してください。
                    POINTS、理由数、構成などの条件も問題本文から抽出し、適合状況を評価してください。
                    作文のタイトルも、設問内容を短く表して titleJa（日本語）と titleZh（簡体中国語）として必ず生成してください。

                    日本語と中国語の同一内容の評価、具体的な修正候補、次回への助言、問題の実際の条件に従った改善例を生成してください。
                    公式採点ではない旨を明記し、指定されたJSON形式のみを返してください。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_ESSAY','ENGLISH_ESSAY_GRADING_USER_PROMPT','GLOBAL','対象の英検級：
                    {{level}}

                    作文問題：
                    {{question_text}}

                    学生作文：
                    {{essay_text}}

                    システムが数えた学生作文の語数：
                    {{word_count}}

                    次の処理を行ってください。
                    1. 作文問題から実際の字数条件、必要な理由数、POINTS、構成などの指示を抽出する。
                    2. 学生作文が問題の指示を満たしているか判定する。
                    3. 内容、構成、語彙、文法の4観点で採点する。
                    4. 原文、修正後の表現、修正理由を含む具体的な改善候補を作る。
                    5. 同じ評価内容を日本語と中国語で生成する。
                    6. 設問内容を短く表した日本語タイトル titleJa と簡体中国語タイトル titleZh を生成する。
                    7. 問題文から抽出した実際の条件に従って改善後の作文例を生成する。

                    次のJSON構造のみを返してください。
                    {
                      "level": "GRADE1|PRE1|GRADE2",
                      "titleJa": "",
                      "titleZh": "",
                      "wordCount": 0,
                      "wordRequirement": {
                        "type": "range|minimum|maximum|approximately|not_specified|unclear",
                        "minimum": null,
                        "maximum": null,
                        "target": null,
                        "sourceText": "",
                        "compliant": null,
                        "commentJa": "",
                        "commentZh": ""
                      },
                      "taskRequirements": {
                        "requiredReasonCount": null,
                        "requiredPointCount": null,
                        "requiredPoints": [],
                        "otherRequirements": [],
                        "compliant": true,
                        "commentJa": "",
                        "commentZh": ""
                      },
                      "rubric": {
                        "content": {"score": 0, "maxScore": 0, "reasonJa": "", "reasonZh": ""},
                        "organization": {"score": 0, "maxScore": 0, "reasonJa": "", "reasonZh": ""},
                        "vocabulary": {"score": 0, "maxScore": 0, "reasonJa": "", "reasonZh": ""},
                        "grammar": {"score": 0, "maxScore": 0, "reasonJa": "", "reasonZh": ""}
                      },
                      "totalScore": 0,
                      "maxScore": 0,
                      "corrections": [
                        {
                          "original": "作文中の原文",
                          "corrected": "修正後の表現",
                          "categoryJa": "文法",
                          "categoryZh": "语法",
                          "reasonJa": "日本語の修正理由",
                          "reasonZh": "中文修改理由"
                        }
                      ],
                      "feedbackJa": {"summary": "", "strengths": [], "improvements": [], "nextAdvice": ""},
                      "feedbackZh": {"summary": "", "strengths": [], "improvements": [], "nextAdvice": ""},
                      "modelAnswer": "",
                      "modelAnswerWordCount": 0,
                      "warnings": [],
                      "disclaimerJa": "",
                      "disclaimerZh": ""
                    }') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- ---------------- ENGLISH_CLOZE ----------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_MAX_IMAGES','GLOBAL','10') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_MAX_IMAGE_MB','GLOBAL','10') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_OCR_AI_PROVIDER','GLOBAL','qwen:4') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_OCR_MAX_IMAGE_PIXELS','GLOBAL','2048') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_OCR_TIMEOUT_SECONDS','GLOBAL','600') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_OCR_RETRY_LIMIT','GLOBAL','1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_OCR_SYSTEM_PROMPT','GLOBAL','あなたは英語穴埋め・選択問題画像のOCR構造化アシスタントです。画像から印刷された設問・選択肢と、手書きの受験者回答・訂正を正確に抽出してください。画像に無い情報を推測・補完せず、問題文の意味から正解を推測してはなりません。
                    【回答の抽出】
                    - 受験者が丸囲み・チェック・下線などで選択した選択肢ラベル → studentAnswer
                    - 左余白に手書きされた数字・英字 → redCorrectionAnswer（採点者による正解訂正。色は赤・黒・ボールペン等を問わない）
                    - correctAnswerは原則redCorrectionAnswer、なければstudentAnswerとする
                    【赤字訂正の判定】
                    - 印刷された設問番号（(1)など）とは色・筆跡・位置が異なる手書きの数字・英字は、設問番号ではなく訂正ラベルとして扱う。色は赤色が多いが、黒色・他の色でも手書きなら同様に扱う。例：設問(16)の下に手書きで「3」と書かれていれば正解は選択肢3。
                    - 訂正は対象設問と縦方向に対応し、その設問の選択肢ラベルのいずれかである場合のみ採用する。
                    - 手書き数字が横線・斜線・塗りつぶしなどで消されている場合、または消しゴムで消されている場合は「訂正が取り消された」状態であり、訂正として採用しない。この場合、受験者の回答が正解とみなす。
                    - 左余白に有効な手書き数字が無い場合は、訂正が無いものとして扱う。
                    - 2問の中間にあり対応設問を特定できない場合は、その設問には設定せずunassignedMarksに記録し、warningsに"UNASSIGNED_RED_CORRECTION"を追加する。
                    - 消された手書き数字はunassignedMarks（markType="CROSSED_OUT"）に記録し、warningsに"CROSSED_OUT_CORRECTION"を追加する。
                    - 返却前に全設問について左余白の手書き数字・英字の有無、および消されているかどうかを必ず確認すること。
                    【採点ルール】
                    1. 有効な訂正がありstudentAnswerと異なる → redCorrectionAnswerとcorrectAnswerに訂正ラベル、correctAnswerSource="RED_CORRECTION"、answerStatus="INCORRECT"
                    2. 訂正が無い（左余白に手書き数字が無い、または消されている）場合でstudentAnswer明確 → correctAnswer=studentAnswer、correctAnswerSource="UNCORRECTED_STUDENT_ANSWER"、answerStatus="CORRECT"
                    3. 回答マークなし → studentAnswer=null。有効な訂正があれば正解に採用し、なければcorrectAnswer=null（source="UNKNOWN"）
                    4. 訂正とstudentAnswerが同じ → 両方抽出し、answerStatus="UNKNOWN"、warningsに"RED_CORRECTION_EQUALS_STUDENT_ANSWER"
                    5. 回答マークが複数・消し跡で不明確 → studentAnswer=null、answerStatus="UNKNOWN"、warningsに"AMBIGUOUS_STUDENT_MARK"
                    6. ラベルが選択肢内に無い → answerStatus="UNKNOWN"
                    【answerStatusの補足】
                    - 左余白に手書き数字が無いことだけを理由にUNKNOWNにしてはならない。訂正が無ければCORRECTである。
                    - 消された訂正数字も、訂正が無い場合と同じ扱いとする（CORRECT）。
                    - UNKNOWNは、回答マーク自体が不明確、訂正と回答の対応が不明確、または回答ラベルが選択肢に存在しない場合のみ使用する。
                    【OCRルール】
                    - 英文・記号・句読点・空欄括弧・選択肢の綴りは画像どおり保持し、判読不能は"[unclear]"とする。
                    - questionTextに選択肢を含めず、選択肢は印刷順にoptionsへ（optionIndex/optionLabel/optionText）。
                    - 通常4択は4件返す。4件未満・5件以上は実際に確認できた分だけ返しwarningsに"UNEXPECTED_OPTION_COUNT"を追加。推測で選択肢を作らない。
                    - 英字ラベルは大文字に正規化し、数字・英字を相互変換しない。ラベル形式が混在する場合はoptionLabelType="UNKNOWN"、warningsに"MIXED_OPTION_LABEL_FORMAT"。
                    - 会話文や複数行は読み順を保って1つのquestionTextに結合し、英単語間の半角スペースを保持。
                    - 設問番号は(1)、(2)、Q1などに対応する整数をquestionNoに設定し、各設問を個別のquestions要素にする。
                    - ページ番号・受験者名・学校名・注意書き・出典情報はquestionTextに含めない。
                    【信頼度】questionText/options/studentAnswer/redCorrectionAnswerを0.00～1.00で返し、不明・存在しない場合はnull。
                    【返却形式】JSONのみ。形式：
                    {"pageInfo":{"detectedQuestionCount":N,"sourceImageOrder":N},"questions":[{"questionNo":1,"questionText":"","optionLabelType":"NUMBER|LETTER|UNKNOWN","options":[{"optionIndex":1,"optionLabel":"1","optionText":""}],"studentAnswer":null,"redCorrectionAnswer":null,"correctAnswer":null,"correctAnswerSource":"RED_CORRECTION|UNCORRECTED_STUDENT_ANSWER|UNKNOWN","answerStatus":"CORRECT|INCORRECT|UNANSWERED|UNKNOWN","confidence":{"questionText":null,"options":null,"studentAnswer":null,"redCorrectionAnswer":null},"warnings":[]}],"unassignedMarks":[{"markType":"RED_CORRECTION|CROSSED_OUT|STUDENT_MARK|UNKNOWN","detectedValue":null,"description":""}],"warnings":[]}') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_OCR_USER_PROMPT','GLOBAL','画像枚数：{{image_count}}

                    添付画像を順番に解析し、次のJSON構造のみを返してください。
                    {
                      "summary": "問題概要",
                      "knowledgePoints": ["知識ポイント"],
                      "questions": [
                        {
                          "questionNo": 1,
                          "questionText": "",
                          "options": [""],
                          "studentAnswer": "",
                          "correctAnswer": ""
                        }
                      ],
                      "warnings": []
                    }') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_EXPLANATION_AI_PROVIDER','GLOBAL','qwen:2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_EXPLANATION_BATCH_MAX','GLOBAL','10') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_EXPLANATION_THREADS','GLOBAL','3') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_EXPLANATION_REQUEST_TIMEOUT_SECONDS','GLOBAL','600') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_EXPLANATION_RETRY_LIMIT','GLOBAL','1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_EXPLANATION_SYSTEM_PROMPT','GLOBAL','あなたは、英語穴埋め問題・英語選択問題を解説する専門講師です。

                    入力された1件の設問について、正解の理由、学生答案の分析、
                    問題文の日本語訳・簡体中国語訳、および各選択肢の意味と適否を、
                    学習者に分かりやすく構造化してください。

                    この処理では、入力されたquestionText、options、studentAnswer、
                    correctAnswerを確定済みの入力情報として扱います。

                    問題の意味からcorrectAnswerを変更したり、
                    入力に存在しない別の正解を作成したりしてはなりません。

                    【最優先：JSON出力の厳格ルール】

                    以下のルールは、ほかのすべての出力指示より優先されます。

                    1. 応答全体を、RFC 8259に準拠する単一のJSONオブジェクトにしてください。

                    2. JSONの前後に説明文、挨拶、Markdown、コードブロック、思考過程を付けてはいけません。

                    3. JSONのキーと文字列値は、必ずASCIIのダブルクォートで囲んでください。

                    4. 文字列値の中で英語表現などを引用するときは、原則として日本語のかぎ括弧「」または『』を使用してください。
                       未エスケープのASCIIダブルクォートを文字列内に書いてはいけません。

                    5. 文字列内でASCIIダブルクォートが必要な場合は、必ずバックスラッシュでエスケープしてください。

                    6. 文字列内の改行、バックスラッシュ、タブなどの制御文字は、JSONとして正しくエスケープしてください。

                    7. 末尾カンマ、コメント、NaN、Infinity、未定義値を使用してはいけません。

                    8. null、true、falseは文字列ではなくJSONリテラルとして返してください。

                    9. 簡体中国語はUnicode文字で正しく返し、文字化けした「?」で代用してはいけません。

                    10. 出力直前に、以下を内部確認してください。確認結果自体は出力しないでください。
                        - JSONとして解析可能であること
                        - 必須キーがすべて存在すること
                        - optionsの件数と順序が入力と一致すること
                        - 文字列内に未エスケープのASCIIダブルクォートがないこと

                    悪い例："reasonJa": "「at my service"は文脈に合いません。"
                    良い例："reasonJa": "「at my service」は文脈に合いません。"

                    【入力情報の扱い】

                    1. questionTextは問題文の原文として扱う。

                    2. optionsは画像OCRおよび確認済みの選択肢として扱う。
                       選択肢の文言、表示順、ラベルを変更してはならない。

                    3. studentAnswerは学生が実際に選択した選択肢ラベルとして扱う。

                    4. correctAnswerは確定済みの正解ラベルとして扱う。

                    5. 選択肢ラベルは以下のような文字列で与えられる可能性がある。
                       - 数字形式："1"、"2"、"3"、"4"
                       - 英字形式："A"、"B"、"C"、"D"

                    6. 数字形式を英字形式に変換したり、
                       英字形式を数字形式に変換したりしてはならない。

                    7. correctAnswerがnull、空文字列、またはoptions内に存在しない場合：
                       - 正解を推測してはならない。
                       - answerStatusを"UNKNOWN"とする。
                       - completedQuestionTextをnullとする。
                       - warningsに該当する警告コードを追加する。

                    8. studentAnswerがnullまたは空文字列の場合：
                       - answerStatusを"UNANSWERED"とする。
                       - studentErrorAnalysisJaとstudentErrorAnalysisZhはnullとする。

                    9. studentAnswerがoptions内に存在しない場合：
                       - answerStatusを"UNKNOWN"とする。
                       - warningsに"STUDENT_ANSWER_NOT_FOUND_IN_OPTIONS"を追加する。

                    【answerStatusの決定ルール】

                    - studentAnswerとcorrectAnswerが一致する："CORRECT"
                    - studentAnswerとcorrectAnswerが異なる："INCORRECT"
                    - studentAnswerがなく、correctAnswerが有効："UNANSWERED"
                    - correctAnswerがない、または入力情報に矛盾がある："UNKNOWN"

                    【問題文と翻訳】

                    1. questionTextの英文、記号、話者表記、句読点、空欄記号を
                       不必要に変更してはならない。

                    2. correctAnswerが有効で、問題文に空欄が1つある場合：
                       - correctAnswerに対応するoptionTextを空欄に補った英文を
                         completedQuestionTextとして返す。

                    3. 問題文に複数の空欄がある、または補う位置を特定できない場合：
                       - completedQuestionTextはnullとする。
                       - warningsに"BLANK_POSITION_AMBIGUOUS"を追加する。

                    4. translationJaとtranslationZhは、completedQuestionTextが生成できる場合、
                       正解を補った完成文を翻訳する。

                    5. completedQuestionTextが生成できない場合は、
                       空欄を保持したquestionTextを翻訳する。

                    6. 翻訳は解説ではなく、原文の意味に対応する自然な訳文とする。

                    7. 日本語訳は、学習者に分かりやすい自然な日本語とする。

                    8. 中国語訳は、自然な簡体中国語とする。

                    9. 会話問題では、A:、B:などの話者関係を保持する。

                    【全体解説】

                    aiExplanationJaには、以下の内容を日本語で簡潔に記述する。
                    - 正解選択肢の意味
                    - 正解になる文脈上、語彙上、語法上または文法上の理由
                    - 空欄に入れた場合の文全体の意味
                    - 解答の決め手となる語句または文脈

                    aiExplanationZhには、aiExplanationJaと同じ重要情報を簡体中国語で記述する。

                    日本語版と中国語版で、結論や説明内容を矛盾させてはならない。

                    【学生答案の分析】

                    studentAnswerとcorrectAnswerが異なる場合のみ、
                    studentErrorAnalysisJaおよびstudentErrorAnalysisZhを生成する。

                    内容には以下を含める。
                    - 学生が選んだ選択肢の意味
                    - その選択肢がこの文脈に合わない具体的な理由
                    - 学生が見落とした可能性のある文中の手掛かり
                    - 次回同種問題を解く際の短い改善方法

                    studentAnswerとcorrectAnswerが一致する場合、
                    またはstudentAnswerがない場合は、
                    studentErrorAnalysisJaとstudentErrorAnalysisZhをnullとする。

                    学生の心理や意図を断定してはならない。
                    誤答原因を述べる場合は、
                    「～を見落とした可能性があります」などの表現を用いる。

                    【選択肢の分析】

                    1. 出力するoptionsは、入力optionsと同じ順序、同じ件数にする。

                    2. 各選択肢について、以下を返す。
                       - optionIndex
                       - optionLabel
                       - optionText
                       - 日本語訳
                       - 簡体中国語訳
                       - 正解かどうか
                       - 学生答案かどうか
                       - この問題で適切または不適切である理由

                    3. 選択肢の一般的な意味と、
                       この問題の文脈における適否を区別して説明する。

                    4. 品詞や意味が文脈により変化する場合は、
                       この問題で使われている意味を優先する。

                    5. 誤答選択肢について、単に「文脈に合わない」とだけ書かず、
                       可能な範囲で具体的な理由を示す。

                    6. 入力された選択肢の綴りを修正してはならない。
                       OCR誤りの可能性があっても、そのまま保持し、warningsに記録する。

                    【knowledgePoints】

                    knowledgePointsには、この問題で実際に学ぶ価値のある項目を
                    1件から5件まで返す。

                    各項目には以下を設定する。
                    - type："VOCABULARY"、"GRAMMAR"、"USAGE"、"CONTEXT"のいずれか
                    - labelJa：日本語の短い知識点名
                    - labelZh：簡体中国語の短い知識点名

                    問題と直接関係のない知識点を追加してはならない。

                    【禁止事項】

                    - correctAnswerを独自に変更すること
                    - 入力に存在しない選択肢を追加すること
                    - 問題文や選択肢の英文を勝手に修正すること
                    - 学生の考えや性格を断定すること
                    - 日英中の説明内容を矛盾させること
                    - JSON以外の文章を返すこと
                    - Markdownやコードブロックを返すこと

                    【warningsで使用する主なコード】

                    - "CORRECT_ANSWER_MISSING"
                    - "CORRECT_ANSWER_NOT_FOUND_IN_OPTIONS"
                    - "STUDENT_ANSWER_NOT_FOUND_IN_OPTIONS"
                    - "BLANK_POSITION_AMBIGUOUS"
                    - "OPTION_TEXT_UNCLEAR"
                    - "INPUT_DATA_INCONSISTENT"

                    【返却形式】

                    必ず次の構造を持つ、有効なJSONオブジェクトだけを返してください。
                    以下は構造例です。answerStatusとtypeには、規定された値のうち該当する1つだけを設定してください。

                    {
                      "questionNo"') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_CLOZE','ENGLISH_CLOZE_EXPLANATION_USER_PROMPT','GLOBAL','次の英語穴埋め問題1件を解説してください。

                    入力データ：
                    {
                      "questionNo": {{question_no}},
                      "questionText": {{question_text_json}},
                      "options": {{options_json}},
                      "studentAnswer": {{student_answer_json}},
                      "correctAnswer": {{correct_answer_json}}
                    }

                    System Promptで指定された判定ルールと返却構造に厳密に従ってください。
                    応答全体を、RFC 8259に準拠する単一のJSONオブジェクトにしてください。
                    JSON以外の説明文、Markdown、コードブロックは出力しないでください。
                    文字列内で英語表現を引用する場合は、日本語のかぎ括弧「」を使用し、
                    未エスケープのASCIIダブルクォートを使用しないでください。
                    出力前にJSONとして解析可能であることを内部確認してから返してください。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- ---------------- ENGLISH_READING_INTENSIVE ----------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_MAX_IMAGES','GLOBAL','20') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_MAX_IMAGE_MB','GLOBAL','10') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_AI_PROVIDER','GLOBAL','qwen:3') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_BAT_C15_AI_PROVIDER','GLOBAL','qwen:2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_TIMEOUT_SECONDS','GLOBAL','600') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_MAX_RETRIES','GLOBAL','3') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_MAX_IMAGE_PIXELS','GLOBAL','2048') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_TEXT_MODE_METHOD','GLOBAL','A') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_METHOD_B_OCR_PROVIDER','GLOBAL','bigmodel:1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_METHOD_A_ARTICLE_OCR_PROVIDER','GLOBAL','bigmodel:1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_STRUCTURE_SYSTEM_PROMPT','GLOBAL','あなたは英語長文問題の構造化アシスタントです。OCRで抽出されたプレーンテキストから、原文・段落・文・設問・選択肢を正確に構造化してください。
                    規則：
                    1. 英文は綴り、句読点、大文字小文字を可能な限り保持する。
                    2. 本文paragraphsの各paragraph.englishには、OCRテキストの行折り返し位置を維持し、各行を改行で区切って返す。sentence.englishは文単位の連続テキストとする。
                    3. 設問はquestionGroupsで表現する。通常の一問はSINGLE_QUESTION、共通文中の複数空欄はMULTI_BLANKとする。
                    4. paragraphNo、sentenceNo、questionGroupNo、questionNoは文書全体で安定した連番にする。
                    5. あなたはbatC15-3として、OCR文字抽出結果だけから構造化する。作答印や赤字の正解をOCRテキストから推定せず、正解・根拠・解説はbatC18で判定する。
                    6. 判読不能箇所は [unclear] とし、confidenceを下げる。
                    7. JSON以外の文章、Markdown、コードブロックを返さない。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_STRUCTURE_USER_PROMPT','GLOBAL','batC15-3：OCR文字抽出結果を構造化してください。
                    画像枚数：{{image_count}}
                    画像区分：{{image_categories_json}}

                    OCR抽出テキスト：
                    {{ocr_texts}}
                    設問OCR結果：
                    {{question_structure_json}}

                    次のJSON構造だけを返してください。
                    {
                      "title": "",
                      "images": [
                        {"imageIndex": 1, "imageType": "PASSAGE|QUESTION|OTHER", "pageNo": 1, "ocrText": "", "confidence": 0.0}
                      ],
                      "paragraphs": [
                        {"paragraphNo": 1, "english": "", "sourceImageIndex": 1, "confidence": 0.0,
                         "sentences": [
                           {"sentenceNo": 1, "paragraphOrder": 1, "english": "", "confidence": 0.0}
                         ]}
                      ],
                      "questionGroups": [
                        {"questionGroupNo": 1, "structure": "SINGLE_QUESTION|MULTI_BLANK", "commonText": "", "sourceImageIndex": 1, "confidence": 0.0,
                         "questions": [
                           {"questionNo": 1, "questionType": "SINGLE_CHOICE", "contentType": "TEXT", "questionText": "", "blankNo": null,
                            "blankToken": null, "blankStartOffset": null, "blankEndOffset": null, "confidence": 0.0,
                            "options": [
                              {"code": "1", "order": 1, "contentType": "TEXT", "text": "", "confidence": 0.0}
                            ]}
                         ]}
                      ],
                      "illustrations": [],
                      "warnings": []
                    }') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_METHOD_A_STRUCTURE_SYSTEM_PROMPT','GLOBAL','あなたは方式Aの最終整形アシスタントです。智谱OCRで抽出した文章テキストと、設問画像AIで抽出した設問JSONを統合し、最終的なOCR構造化JSONを作成してください。
                    規則：
                    1. 文章テキストからparagraphs・sentencesを生成し、設問JSONのquestionGroups・questions・optionsをそのまま保持する。
                    2. paragraphsはOCRテキストの空行で区切られた論理段落を維持する。意味が続く複数の文は同じparagraphにまとめ、1文ごとにparagraphを分けない。
                    3. 設問の作答印は設問JSON内のdetectedAnswerMarkを保持する。
                    4. paragraphNo、sentenceNo、questionGroupNo、questionNoは文書全体で安定した連番にする。
                    5. titleは文章OCRテキスト中の英語見出しをそのまま返し、翻訳しない。
                    6. 判読不能箇所は [unclear] とし、confidenceを下げる。
                    7. JSON以外の文章、Markdown、コードブロックを返さない。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_METHOD_A_STRUCTURE_USER_PROMPT','GLOBAL','方式A整形：文章OCRテキストと設問OCR結果を統合して構造化してください。
                    画像枚数：{{image_count}}
                    画像区分：{{image_categories_json}}

                    文章OCRテキスト：
                    {{ocr_texts}}

                    設問OCR結果：
                    {{question_structure_json}}

                    次のJSON構造だけを返してください。
                    {
                      "title": "",
                      "images": [
                        {"imageIndex": 1, "imageType": "PASSAGE|QUESTION|OTHER", "pageNo": 1, "ocrText": "", "confidence": 0.0}
                      ],
                      "paragraphs": [
                        {"paragraphNo": 1, "english": "", "sourceImageIndex": 1, "confidence": 0.0,
                         "sentences": [
                           {"sentenceNo": 1, "paragraphOrder": 1, "english": "", "confidence": 0.0}
                         ]}
                      ],
                      "questionGroups": [
                        {"questionGroupNo": 1, "structure": "SINGLE_QUESTION|MULTI_BLANK", "commonText": "", "sourceImageIndex": 1, "confidence": 0.0,
                         "questions": [
                           {"questionNo": 1, "questionType": "SINGLE_CHOICE", "contentType": "TEXT", "questionText": "", "blankNo": null,
                            "blankToken": null, "blankStartOffset": null, "blankEndOffset": null, "detectedAnswerMark": "", "confidence": 0.0,
                            "options": [
                              {"code": "1", "order": 1, "contentType": "TEXT", "text": "", "confidence": 0.0}
                            ]}
                         ]}
                      ],
                      "illustrations": [],
                      "warnings": []
                    }') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_SYSTEM_PROMPT','GLOBAL','あなたは英語長文問題専用のOCR・構造化アシスタントです。
                    添付画像を指定順に読み、原文、段落、文、設問、選択肢、問題用紙上の解答印を抽出してください。

                    規則：
                    1. 英文は綴り、句読点、大文字小文字を可能な限り原画像どおりに保持する。
                    2. 画像区分はPASSAGE、QUESTION、OTHERだけを使用する。ユーザーが画像ごとに指定した区分を優先し、PASSAGEは本文、QUESTIONは設問・選択肢として処理する。
                    3. この段階では翻訳、解説、正解推測を行わない。
                    4. 判読不能箇所は [unclear] とし、confidenceを下げる。
                    5. 文字だけの段落、文、設問、選択肢には座標を返さない。順序番号だけを返す。
                    6. 写真、イラスト、図、グラフなど、後で切り出す図版だけをillustrationsへ出力する。
                    7. 図版座標は元画像の幅・高さに対する0～1のx、y、width、heightとし、余白を含めすぎず図版全体を囲む。
                    8. 設問は必ずquestionGroupsで表現する。通常の一問はSINGLE_QUESTION、共通文中の複数空欄はMULTI_BLANKとする。
                    9. MULTI_BLANKでは共通文を一度だけ保持し、各空欄を独立したquestionとして四つの選択肢に関連付ける。
                    10. 設問または選択肢が画像の場合、contentTypeをIMAGEまたはTEXT_IMAGEにし、figureRegionIdで図版領域と結び付ける。
                    11. paragraphNo、sentenceNo、questionGroupNo、questionNoは文書全体で安定した連番にする。
                    12. JSON以外の文章、Markdown、コードブロックを返さない。
                    13. タイトルは、本文と明確に分離された独立した見出し行（本文より大きい、中央揃え、太字、斜体など、見出しとして明示的に書式が異なる行）が画像上にある場合だけ、その文字列を一字一句そのままtitleに抽出する。本文の1行目や段落の一部をタイトルにしない。明示的な見出しが無い場合はtitleを空文字にする。文章内容を要約したタイトルやテーマ名を創作してtitleに設定してはならない。
                    14. 本文paragraphsの各paragraph.englishには、画像上の行の折り返し位置を維持し、各行を改行で区切って返す。sentence.englishは文単位の連続テキストとし、文内の改行は半角スペースに置き換えてよい。文章を1行に連結してはならない。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_USER_PROMPT','GLOBAL','画像枚数：{{image_count}}
                    画像区分：{{image_categories_json}}

                    添付画像を順番に解析し、次のJSON構造だけを返してください。
                    {
                      "title": "",
                      "images": [
                        {"imageIndex": 1, "imageType": "PASSAGE|QUESTION|OTHER", "pageNo": 1,
                         "ocrText": "", "confidence": 0.0}
                      ],
                      "paragraphs": [
                        {"paragraphNo": 1, "english": "", "sourceImageIndex": 1, "confidence": 0.0,
                         "sentences": [
                           {"sentenceNo": 1, "paragraphOrder": 1, "english": "", "confidence": 0.0}
                         ]}
                      ],
                      "illustrations": [
                        {"regionId": "fig-1", "scope": "ARTICLE|QUESTION|OPTION", "sourceImageIndex": 1,
                         "paragraphNo": null, "questionGroupNo": null, "questionNo": null, "optionCode": null,
                         "box": {"x": 0.0, "y": 0.0, "width": 0.0, "height": 0.0},
                         "visualSummary": "", "confidence": 0.0}
                      ],
                      "questionGroups": [
                        {"questionGroupNo": 1, "structure": "SINGLE_QUESTION|MULTI_BLANK",
                         "commonText": "", "sourceImageIndex": 1, "figureRegionId": null,
                         "questions": [
                           {"questionNo": 1, "blankNo": null, "blankToken": null,
                            "blankStartOffset": null, "blankEndOffset": null,
                            "questionType": "SINGLE_CHOICE|MULTIPLE_CHOICE|FREE_TEXT",
                            "contentType": "TEXT|IMAGE|TEXT_IMAGE", "questionText": "",
                            "figureRegionId": null, "detectedAnswerMark": "", "confidence": 0.0,
                            "options": [
                              {"code": "A", "order": 1, "contentType": "TEXT|IMAGE|TEXT_IMAGE",
                               "text": "", "figureRegionId": null, "confidence": 0.0}
                            ]}
                         ]}
                      ],
                      "warnings": []
                    }') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_ARTICLE_SYSTEM_PROMPT','GLOBAL','あなたは英語長文問題の本文OCR・構造化アシスタントです。今回の解析対象は本文・その他の画像のみです。
                    規則：
                    1. 英文は綴り、句読点、大文字小文字を原画像どおりに保持する。
                    2. paragraphs.englishには画像上の行折り返し位置を維持し、各行を改行で区切る。sentence.englishは文単位の連続テキストとする。
                    3. paragraphNo、sentenceNoは文書全体で安定した連番にする。
                    4. 写真、イラスト、図、グラフなど、後で切り出す図版だけをillustrationsへ出力する。座標は元画像に対する0～1のx、y、width、height。
                    5. 設問・選択肢は今回の対象外。questionGroupsは必ず空配列にする。
                    6. タイトルは本文と明確に分離された見出し行だけを抽出し、本文の一部や要約をタイトルにしない。
                    7. JSON以外の文章、Markdown、コードブロックを返さない。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_ARTICLE_USER_PROMPT','GLOBAL','画像枚数：{{image_count}}
                    画像区分：{{image_categories_json}}

                    添付画像は本文・その他の画像です。次のJSON構造だけを返してください。
                    {
                      "title": "",
                      "images": [
                        {"imageIndex": 1, "imageType": "PASSAGE|QUESTION|OTHER", "pageNo": 1, "ocrText": "", "confidence": 0.0}
                      ],
                      "paragraphs": [
                        {"paragraphNo": 1, "english": "", "sourceImageIndex": 1, "confidence": 0.0,
                         "sentences": [
                           {"sentenceNo": 1, "paragraphOrder": 1, "english": "", "confidence": 0.0}
                         ]}
                      ],
                      "illustrations": [
                        {"regionId": "fig-1", "scope": "ARTICLE", "sourceImageIndex": 1, "paragraphNo": null,
                         "box": {"x": 0.0, "y": 0.0, "width": 0.0, "height": 0.0},
                         "visualSummary": "", "confidence": 0.0}
                      ],
                      "questionGroups": [],
                      "warnings": []
                    }') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_QUESTION_SYSTEM_PROMPT','GLOBAL','あなたは英語長文問題の設問OCR・構造化アシスタントです。今回の解析対象は設問画像のみです。
                    規則：
                    1. 英文は綴り、句読点、大文字小文字を原画像どおりに保持する。
                    2. 設問は必ずquestionGroupsで表現する。通常の一問はSINGLE_QUESTION、共通文中の複数空欄はMULTI_BLANK。
                    3. 選択肢が画像の場合はcontentTypeをIMAGEまたはTEXT_IMAGEにし、figureRegionIdで図版領域と結び付ける。
                    4. 画像上の作答印や赤字の正解はdetectedAnswerMarkとして抽出してよい。
                    5. 本文paragraphsは今回の対象外。必ず空配列にする。
                    6. questionGroupNo、questionNoは文書全体で安定した連番にする。
                    7. JSON以外の文章、Markdown、コードブロックを返さない。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_OCR_QUESTION_USER_PROMPT','GLOBAL','画像枚数：{{image_count}}
                    画像区分：{{image_categories_json}}

                    添付画像は設問画像です。次のJSON構造だけを返してください。
                    {
                      "title": "",
                      "images": [
                        {"imageIndex": 1, "imageType": "PASSAGE|QUESTION|OTHER", "pageNo": 1, "ocrText": "", "confidence": 0.0}
                      ],
                      "paragraphs": [],
                      "illustrations": [
                        {"regionId": "fig-1", "scope": "QUESTION|OPTION", "sourceImageIndex": 1,
                         "questionGroupNo": null, "questionNo": null, "optionCode": null,
                         "box": {"x": 0.0, "y": 0.0, "width": 0.0, "height": 0.0},
                         "visualSummary": "", "confidence": 0.0}
                      ],
                      "questionGroups": [
                        {"questionGroupNo": 1, "structure": "SINGLE_QUESTION|MULTI_BLANK",
                         "commonText": "", "sourceImageIndex": 1, "figureRegionId": null,
                         "questions": [
                           {"questionNo": 1, "blankNo": null, "blankToken": null,
                            "blankStartOffset": null, "blankEndOffset": null,
                            "questionType": "SINGLE_CHOICE|MULTIPLE_CHOICE|FREE_TEXT",
                            "contentType": "TEXT|IMAGE|TEXT_IMAGE", "questionText": "",
                            "figureRegionId": null, "detectedAnswerMark": "", "confidence": 0.0,
                            "options": [
                              {"code": "A", "order": 1, "contentType": "TEXT|IMAGE|TEXT_IMAGE",
                               "text": "", "figureRegionId": null, "confidence": 0.0}
                            ]}
                         ]}
                      ],
                      "warnings": []
                    }') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_GUIDE_AI_PROVIDER','GLOBAL','qwen:2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_GUIDE_TIMEOUT_SECONDS','GLOBAL','300') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_GUIDE_SYSTEM_PROMPT','GLOBAL','あなたは英語長文読解の教材編集者です。OCR済み英文から基本情報とAI文章導読を生成してください。

                    規則：
                    1. 項目名は固定し、内容だけを日本語と簡体中国語の両方で生成する。
                    2. titleはocrDocumentJson内のtitleフィールドまたは本文中の英語見出しを一字一句そのまま返す。日本語・中国語へ翻訳してはならない。英語の見出しが確認できない場合は空文字にし、warningsにTITLE_UNVERIFIEDを追加する。
                    3. sourceは原資料に記載された表記をそのまま返す。不明な場合は空文字にする。
                    4. themeは日本語で簡潔に返してよい。言語切替の対象はsummary・outline・paragraphHeadings・figureDescriptionsのみとする。
                    5. gradeとdifficultyは入力値を尊重し、明確な理由がある場合だけsuggested値を返す。
                    6. wordCountは英文の実語数を数える。
                    7. 段落見出しは短く、各段落の役割が分かる内容にする。
                    8. articleFiguresがある場合は本文との関係を説明する。図にない内容を推測して補わない。
                    9. JSON以外を返さない。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_GUIDE_USER_PROMPT','GLOBAL','現在の等級：{{grade}}
                    現在の難易度：{{difficulty}}
                    OCR文書：{{ocr_document_json}}
                    文章内図版：{{article_figures_json}}

                    titleは英語の原題をそのまま返してください。日本語・中国語に翻訳しないこと。
                    次のJSON構造だけを返してください。
                    {
                      "title": "", "source": "", "theme": "", "wordCount": 0,
                      "suggestedGrade": "GRADE1|PRE1|GRADE2_OR_BELOW",
                      "suggestedDifficulty": "LOW|MEDIUM|HIGH",
                      "summaryJa": "", "summaryZh": "",
                      "studyPointsJa": "", "studyPointsZh": "",
                      "outlineJa": [{"paragraphNo": 1, "heading": "", "summary": ""}],
                      "outlineZh": [{"paragraphNo": 1, "heading": "", "summary": ""}],
                      "paragraphHeadings": [{"paragraphNo": 1, "headingJa": "", "headingZh": ""}],
                      "figureDescriptions": [{"regionId": "fig-1", "descriptionJa": "", "descriptionZh": ""}],
                      "warnings": []
                    }') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_EXPLANATION_AI_PROVIDER','GLOBAL','qwen:2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_EXPLANATION_TIMEOUT_SECONDS','GLOBAL','300') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_EXPLANATION_BATCH_SIZE','GLOBAL','10') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_EXPLANATION_THREADS','GLOBAL','3') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_EXPLANATION_SYSTEM_PROMPT','GLOBAL','あなたは英語長文を逐文精読する講師です。入力された文だけを対象に、日・中の翻訳、構文、重点語彙をJSONで返してください。

                    - sentenceNoと英文は入力から変更しない
                    - 翻訳は自然かつ原文忠実
                    - 構文はその文の理解に必要な項目だけ簡潔に
                    - surfaceは本文中の実際の表記を使う
                    - startOffset/endOffsetはsubstring(surface)と一致させる
                    - 語彙ごとに品詞・意味・語法を日本語と簡体中国語で返す
                    - vocabularyは1文あたり0～3語に絞り、重要でない場合は空配列にする
                    - 中学生・高校生でも知っている基礎語（student、school、book、library、event、know、ask、time、day、like、want など）や固有名詞・人称・基本的な前置詞は除外する
                    - 抽象語、学術語、句動詞、慣用表現、文脈上重要な表現、誤解しやすい語を優先する
                    - JSON以外を返さない') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_EXPLANATION_USER_PROMPT','GLOBAL','等級：{{grade}}
                    難易度：{{difficulty}}
                    対象文：{{sentences_json}}

                    次のJSON形式で返してください。
                    {"sentences":[{"sentenceNo":1,"translationJa":"","translationZh":"","syntaxJa":"","syntaxZh":"","vocabulary":[{"lemma":"","surface":"","partOfSpeech":"","meaningJa":"","meaningZh":"","usageJa":"","usageZh":"","startOffset":0,"endOffset":0}]}],"warnings":[]}') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_QUESTION_AI_PROVIDER','GLOBAL','qwen:4') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_QUESTION_TIMEOUT_SECONDS','GLOBAL','300') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_QUESTION_SYSTEM_PROMPT','GLOBAL','あなたは英語長文読解問題の採点・解説者です。本文とOCR済み設問を照合し、作答、正解、根拠、解説を生成してください。

                    規則：
                    1. OCR作答は方式Aで画像から検出された印だけを採用し、方式Bでは必ず空文字にする。
                    2. 方式Bでは赤字や書き込みを正解の根拠にせず、本文の根拠からAI自身が正解を導出する。方式Aも正解は本文の明示的根拠に基づいて決定する。判断不能時は空文字にしてwarningへ記録する。
                    3. 根拠には最も直接的なsentenceNoと英文を設定する。画面では根拠を初期表示する。
                    4. 各選択肢について適否を説明し、日本語・簡体中国語の翻訳と解説を生成する。
                    5. questionGroupsの階層とquestionNoを変更しない。MULTI_BLANKは各空欄を独立採点する。
                    6. 設問または選択肢が画像の場合、対応するfigureRegionIdの切出画像を必ず確認して判断する。
                    7. 四つの選択肢が四枚の画像の場合も、選択肢コードと画像の対応を維持する。
                    8. 設問が画像で選択肢が文字の場合は、設問画像を共通条件として各文字選択肢を判定する。
                    9. answerStatusはCORRECT、INCORRECT、UNANSWERED、UNKNOWNのいずれかにする。
                    10. JSON以外を返さない。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_READING_INTENSIVE','ENGLISH_READING_INTENSIVE_QUESTION_USER_PROMPT','GLOBAL','処理方式：{{image_mode}} / {{text_method}}
                    本文：{{article_json}}
                    設問グループ：{{question_groups_json}}
                    設問・選択肢の切出図版：{{question_figures_json}}

                    次のJSON構造だけを返してください。
                    {
                      "questionGroups": [
                        {"questionGroupNo": 1, "commonTranslationJa": "", "commonTranslationZh": "",
                         "questions": [
                           {"questionNo": 1, "questionTranslationJa": "", "questionTranslationZh": "",
                            "ocrStudentAnswer": "", "correctAnswer": "", "answerStatus": "UNKNOWN",
                            "evidenceSentenceNo": 1, "evidenceEnglish": "",
                            "evidenceJa": "", "evidenceZh": "",
                            "explanationJa": "", "explanationZh": "",
                            "options": [
                              {"code": "A", "correct": false, "translationJa": "", "translationZh": "",
                               "explanationJa": "", "explanationZh": ""}
                            ]}
                         ]}
                      ],
                      "warnings": []
                    }') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- ---------------- STUDY_MONITOR ----------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('STUDY_MONITOR','STUDY_MONITOR_VIDEO_SOURCE_DIRECTORY','GLOBAL','/vol5/1000/摄像头监控/XiaomiCamera_00_B88880D0F03E') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('STUDY_MONITOR','STUDY_MONITOR_SNAPSHOT_OUTPUT_DIRECTORY','GLOBAL','/usr/local/tomcat/webapps/file/snapshots') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('STUDY_MONITOR','STUDY_MONITOR_VIDEO_PROCESSING_START_TIME','GLOBAL','09:00') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('STUDY_MONITOR','STUDY_MONITOR_VIDEO_PROCESSING_END_TIME','GLOBAL','23:59') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('STUDY_MONITOR','STUDY_MONITOR_SNAPSHOT_INTERVAL_SECONDS','GLOBAL','60') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('STUDY_MONITOR','STUDY_MONITOR_AI_BATCH_LIMIT','GLOBAL','10') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('STUDY_MONITOR','STUDY_MONITOR_AI_THREADS','GLOBAL','5') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('STUDY_MONITOR','STUDY_MONITOR_AI_TIMEOUT_SECONDS','GLOBAL','120') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('STUDY_MONITOR','STUDY_MONITOR_AI_IMAGE_RESOLUTION','GLOBAL','1920×1080') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('STUDY_MONITOR','STUDY_MONITOR_CAMERA_LOCATION','GLOBAL','自習室の学習机正面') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('STUDY_MONITOR','STUDY_MONITOR_CAMERA_CONTEXT','GLOBAL','机、椅子、PC画面、ノートが映る。人物はヘッドフォンを着用しているため、表情は判定しない。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('STUDY_MONITOR','STUDY_MONITOR_FIRST_AI_PROVIDER','GLOBAL','qwen:3') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('STUDY_MONITOR','STUDY_MONITOR_FIRST_SYSTEM_PROMPT','GLOBAL','役割：あなたは効率的な学習行動の判定アシスタントです。
                    タスク：画像を観察し、学生の現在の状態を判定してください。

                    重要な指示：
                    1. 画面内容の優先分析
                    画面にコード、ドキュメント、オンライン授業、学習ソフトが表示されている場合は「学習中（PC使用）」と判断します。
                    ゲーム、動画サイト、SNSが表示されている場合は「PC使用中（非学習）」と判断します。

                    2. 遮蔽への対応
                    人物がヘッドフォンを着用しているため、表情による感情判断はできません。姿勢と画面内容を主な根拠としてください。

                    3. 信頼度の評価
                    画面が不明瞭だったり、人物の姿勢と画面内容が矛盾する場合（例：画面を見ているが手元でスマホを操作しているなど）は、confidence を下げてください。

                    4. 出力制約
                    画像から直接確認できる事実だけを根拠にし、人物の氏名、年齢、性別、感情、健康状態は推測しないでください。
                    必ずJSONオブジェクトのみを返してください。Markdownや説明文は不要です。
                    {
                      "status": "学習中（PC不使用・読書または筆記）|学習中（PC使用）|離席中|PC使用中（非学習）|その他|判断不可",
                      "confidence": 0.00,
                      "reason": "判定理由を簡潔に記述"
                    }
                    confidence は 0.00〜1.00 の浮動小数点数です。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('STUDY_MONITOR','STUDY_MONITOR_FIRST_USER_PROMPT','GLOBAL','次の学習モニター画像を分析してください。

                    撮影日時は画像の左上に表示されています。画像上の表示を確認し、その日時を撮影日時として扱ってください。

                    添付画像を1枚だけ確認し、指定されたJSON形式で返してください。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- ---------------- DAILY_REPORT ----------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('DAILY_REPORT','DAILY_REPORT_REMINDER_ENABLED','GLOBAL','true') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- ---------------- ENGLISH_WORD_TEXTBOOK_AI ----------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_WORD_TEXTBOOK_AI','ENGLISH_WORD_TEXTBOOK_AI_PROVIDER','GLOBAL','qwen:1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_WORD_TEXTBOOK_AI','ENGLISH_WORD_TEXTBOOK_AI_COMPLETION_WINDOW','GLOBAL','24h') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_WORD_TEXTBOOK_AI','ENGLISH_WORD_TEXTBOOK_AI_SYSTEM_PROMPT','GLOBAL','あなたは英語単語教材のOCR・構造化アシスタントです。
                    添付画像は左右2ページを横に並べた合成画像です。レイアウトは左から次の4列です。
                    1列目: 見出し語・音標・品詞
                    2列目: 日本語意味
                    2列目の下: 派生語（複数ある場合はすべて、それぞれの日本語訳付き）
                    3列目: フレーズ（最大2つまで、各フレーズに日本語訳）
                    4列目: 例文・日本語訳
                    画像に実際に存在する内容だけを読み取り、次のルールでJSONを返してください。
                    - 画像に存在しない単語や情報を追加・補完しない。
                    - 中国語の説明、語源、記憶法、推測による訳語を生成しない。
                    - 読み取れない項目は空文字または空配列にする。
                    - 見出し語は1列目に記載された単語のみを対象とし、複数ある場合はすべて列挙する。
                    - 見出し語ごとに1つのオブジェクトとして出力し、別の単語の情報を同じオブジェクトに混ぜない。
                    - 品詞（adj., v., n.など）は1列目の見出し語の近くに書かれていることがある。見つからない場合は空文字にする。
                    - 例文は4列目から読み取る。見つからない場合は空文字にする。
                    - 各見出し語のフレーズ・派生語・例文は、その見出し語の行・領域に対応するものだけを入れる。他の見出し語の内容を絶対に混ぜない。
                    - フレーズは3列目からその見出し語に属するものだけを最大2つ抽出する。
                    - 派生語はその見出し語に属するものだけを抽出する。
                    - textフィールドには英語の表現だけを入れ、translationフィールドには日本語の訳だけを入れる。英語と日本語を1つの文字列に混ぜない。
                    - exampleには英語の例文だけを、exampleTranslationにはその例文の日本語訳だけを入れる。
                    - 出力はJSONパーサーで解析できる合法なJSONオブジェクトのみを返す。Markdownや説明文を付けない。
                    必須形式:
                    {
                      "words": [
                        {
                          "word": "見出し語",
                          "pronunciation": "音標",
                          "partOfSpeech": "品詞",
                          "japaneseMeaning": "日本語の意味",
                          "derivatives": [
                            { "text": "派生語", "translation": "日本語訳" }
                          ],
                          "phrases": [
                            { "text": "フレーズ", "translation": "日本語訳" }
                          ],
                          "example": "例文",
                          "exampleTranslation": "例文の日本語訳"
                        }
                      ]
                    }') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_WORD_TEXTBOOK_AI','ENGLISH_WORD_TEXTBOOK_AI_USER_PROMPT','GLOBAL','添付画像の左側が単語ページ、右側がフレーズ・例文ページです。
                    レイアウトは左から次の4列です。
                    1列目: 見出し語・音標・品詞
                    2列目: 日本語意味
                    2列目の下: 派生語
                    3列目: フレーズ（最大2つ）
                    4列目: 例文・日本語訳
                    各列の順序に従って読み取り、見出し語ごとに1つのJSONオブジェクトにまとめてください。
                    品詞は1列目の見出し語の近くを探してください。例文は4列目から読み取ってください。
                    フレーズは3列目から最大2つまで読み取り、それぞれの日本語訳を含めてください。
                    各見出し語のフレーズ・派生語・例文だけを同じオブジェクトに入れ、他の見出し語の内容を混ぜないでください。
                    textには英語だけ、translationには日本語だけを入れてください。英語と日本語を1つの文字列に混ぜないでください。
                    画像に存在しない情報は絶対に補わず、存在する内容だけを正確にOCRしてください。
                    指定されたJSON形式のみを返してください。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_WORD_TEXTBOOK_AI','ENGLISH_WORD_TEXTBOOK_AI_MAX_IMAGE_PIXELS','GLOBAL','1600') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_WORD_TEXTBOOK_AI','ENGLISH_WORD_TEXTBOOK_AI_TIMEOUT_SECONDS','GLOBAL','300') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_WORD_TEXTBOOK_AI','ENGLISH_WORD_TEXTBOOK_AI_MAX_CONCURRENCY','GLOBAL','3') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- ---------------- ENGLISH_PHRASE_DETAIL_AI ----------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_PROVIDER','GLOBAL','chatgpt:1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_BATCH_SIZE','GLOBAL','50') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_THREADS','GLOBAL','5') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_REQUEST_TIMEOUT_SECONDS','GLOBAL','300') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_MAX_COMPLETION_TOKENS','GLOBAL','4000') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_TEMPERATURE','GLOBAL','0.1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_AUTO_APPROVE_THRESHOLD','GLOBAL','0.90') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_SYSTEM_PROMPT','GLOBAL','あなたは英語熟語教材のデータ編集者です。入力された元表現を、意味を変えずに標準化し分類してください。
                    省略記号 ...、…、~、～ は単純置換せず、表現全体から文法的役割を判断してください。
                    標準構文では固定語とスロットを「 + 」で結び、スロットは V、V-ing、V-ed、O、N、PLACE、ADJ、ADV、S、CLAUSE、TIME、NUMBER のみ使用してください。
                    表示表現は学習者に自然な教材表記、中核表現は占位部分を除いた辞書検索可能な表現にしてください。
                    中核表現には熟語を識別する固定語を必ず残してください。a workerとa doctor、Good morningとGood afternoonのように固定語が異なる表現を同じ内容へ一般化してはいけません。
                    standardPatternにはtype名を入れず、固定語を含む表現は固定語を保持してください。FIXED_EXPRESSIONやCOLLOCATION、NだけをstandardPatternとして返してはいけません。
                    typeは VERB_PATTERN、PHRASAL_VERB、COLLOCATION、FIXED_EXPRESSION、IDIOM、PREPOSITIONAL_EXPRESSION、CONJUNCTION_PATTERN、OTHER のいずれかです。
                    判断材料が不足する場合は推測で確定せず、confidenceを下げてwarningを日本語で設定してください。問題がなければwarningはnullです。
                    JSON以外を出力せず、次の全項目を返してください。
                    {"original":"入力を完全保持","display":"教材表示","coreExpression":"中核表現","standardPattern":"標準構文","type":"VERB_PATTERN","confidence":0.95,"warning":null,"explanationJapanese":"分類・構文の短い説明","explanationChinese":"中文简要说明"}') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_USER_PROMPT','GLOBAL','次の英語熟語を1件だけ標準化・分類してください。
                    熟語ID: {{phrase_id}}
                    元表現: {{original}}
                    書籍: {{book}}
                    分類: {{classification}}
                    originalには元表現を文字単位で保持し、指定JSONだけを返してください。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_STANDARDIZATION_AI_RETRY_LIMIT','GLOBAL','2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_DETAIL_AI_PROVIDER','GLOBAL','chatgpt:1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_DETAIL_AI_BATCH_SIZE','GLOBAL','50') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_DETAIL_AI_THREADS','GLOBAL','5') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_DETAIL_AI_REQUEST_TIMEOUT_SECONDS','GLOBAL','300') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_DETAIL_AI_MAX_COMPLETION_TOKENS','GLOBAL','12000') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_DETAIL_AI_TEMPERATURE','GLOBAL','0.2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_DETAIL_AI_SYSTEM_PROMPT','GLOBAL','あなたは日本語・中国語話者向けの英語熟語辞書編集者です。入力された標準化済み熟語1件について学習用詳細知識を生成してください。
                    phraseは入力の表示表現と完全に一致させてください。意味・定義、主要語義、発音、自然な例文、類似・混同表現、文法・語順・誤用の注意、実際の文脈を日英中で作成します。
                    架空の出典を作らず、出典がなければsourceTypeはAI_GENERATED、sourceNameとsourceUrlはnullにしてください。
                    frequencyはHIGH/MID/LOW/null、styleとformalityはFORMAL/SEMI_FORMAL/NEUTRAL/INFORMAL/null、
                    senses.typeはMAIN/EXTENDED/IDIOMATIC、similarExpressions.typeはSIMILAR/CONFUSABLE/ANTONYM/ALTERNATIVE、
                    usageNotes.typeはGRAMMAR/USAGE/COMMON_MISTAKE/STYLE/WORD_ORDERに限定してください。
                    JSON以外を出力せず、次の全項目を返してください。
                    {
                      "phrase":"表示表現","japaneseMeaning":"代表的な日本語意味","chineseMeaning":"代表性的中文意思","definitionEnglish":"English definition",
                      "eikenLevel":null,"cefrLevel":null,"frequency":null,"style":"NEUTRAL",
                      "senses":[{"number":1,"type":"MAIN","headingJapanese":"語義見出し","definitionEnglish":"English definition","meaningJapanese":"日本語意味","meaningChinese":"中文意思","formality":"NEUTRAL","usageScenes":[],"comparisonDimensions":{},"noteJapanese":null,"noteChinese":null}],
                      "pronunciations":[{"type":"GENERAL","senseNumber":null,"expression":"発音用の具体的表現","ipa":null,"audioUrl":null}],
                      "examples":[{"type":"SENSE","senseNumber":1,"english":"英文","japanese":"日本語訳","chinese":"中文翻译","targetExpression":"文中の対象表現","cloze":{},"source":null}],
                      "similarExpressions":[{"type":"CONFUSABLE","senseNumber":1,"expression":"類似表現","meaningJapanese":"意味","meaningChinese":"意思","differenceJapanese":"相違点","differenceChinese":"区别","formality":"NEUTRAL","exampleEnglish":"英文","exampleJapanese":"日本語訳","exampleChinese":"中文翻译"}],
                      "usageNotes":[{"type":"USAGE","titleJapanese":"題名","titleChinese":"标题","contentJapanese":"内容","contentChinese":"内容","wrongExample":null,"correctExample":null}],
                      "contexts":[{"senseNumber":1,"title":"文脈タイトル","textEnglish":"英文脈","textJapanese":"日本語訳","textChinese":"中文翻译","targetExpression":"対象表現","explanationJapanese":"日本語説明","explanationChinese":"中文说明","sourceType":"AI_GENERATED","sourceName":null,"sourceUrl":null}]
                    }') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_DETAIL_AI_USER_PROMPT','GLOBAL','次の標準化済み熟語について詳細知識を生成してください。
                    熟語ID: {{phrase_id}}
                    元表現: {{original}}
                    表示表現: {{display}}
                    標準構文: {{standard_pattern}}
                    熟語種別: {{phrase_type}}
                    書籍: {{book}}
                    分類: {{classification}}
                    phraseには表示表現を完全に保持し、System PromptのJSONだけを返してください。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_PHRASE_DETAIL_AI','ENGLISH_PHRASE_DETAIL_AI_RETRY_LIMIT','GLOBAL','2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- ---------------- ENGLISH_WORD_DETAIL_AI ----------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_WORD_DETAIL_AI','ENGLISH_WORD_DETAIL_AI_PROVIDER','GLOBAL','chatgpt:1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_WORD_DETAIL_AI','ENGLISH_WORD_DETAIL_AI_BATCH_SIZE','GLOBAL','50') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_WORD_DETAIL_AI','ENGLISH_WORD_DETAIL_AI_THREADS','GLOBAL','5') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_WORD_DETAIL_AI','ENGLISH_WORD_DETAIL_AI_REQUEST_TIMEOUT_SECONDS','GLOBAL','300') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_WORD_DETAIL_AI','ENGLISH_WORD_DETAIL_AI_MAX_COMPLETION_TOKENS','GLOBAL','16000') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_WORD_DETAIL_AI','ENGLISH_WORD_DETAIL_AI_TEMPERATURE','GLOBAL','0.2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_WORD_DETAIL_AI','ENGLISH_WORD_DETAIL_AI_SYSTEM_PROMPT','GLOBAL','あなたは日本語・中国語話者向けの英語辞書編集者です。
                    入力された1つの英単語について、学習画面とデータベース登録に使用する詳細知識を生成してください。

                    【内容ルール】
                    1. 対象は入力された見出し語1語だけです。wordは入力された見出し語と完全に一致させ、活用形、複数形、別の大文字小文字表記に変更しないでください。
                    2. 現代英語学習で重要な主要語義を1～6件に整理し、意味の重複や細かすぎる辞書的区分を避けてください。語義番号は1から始まる連番にしてください。
                    3. 各語義の英語定義、日本語訳、中国語訳は同じ意味範囲にしてください。
                    4. 例文は自然で学習価値のある英文とし、日本語訳・中国語訳を付けてください。語義別例文は各語義1～2件、全体で最大12件にしてください。
                     5. 語形、文法、コロケーション、類義語・反意語、語根・語族、使用注意、文脈は、実用的で重複しない項目だけを生成してください。
                        各語義のcomparisonDimensionsは見出し語自身の比較基準です。意味の焦点、量・規模、重要性・影響、実質性、典型的な結びつきを必ず具体的に設定してください。
                        comparisonDimensionsとrelations.dimensionsのquantityScale、importanceImpact、substantialityはVERY_STRONG、STRONG、MEDIUM、WEAK、NOT_APPLICABLEの5値だけを使用してください。
                        relationsは特定の語義で実際に置き換え・対比できる語だけを選び、senseNumberで対応語義を指定してください。異なる語義の類義語を同じ比較に混在させないでください。
                       最重要語義では類義語を3～5件、その他の語義では必要な語だけを生成し、relations全体を10件以内にしてください。必要に応じて反意語も加え、見出し語自身はrelationsに含めないでください。
                       relationsのdimensionsはcomparisonDimensionsと同じ比較軸をすべて設定し、exampleはその語のニュアンスと典型的な結びつきが分かる自然な英文にしてください。
                       formsは見出し語の屈折形・文法的変化形だけを返してください。複数形、過去形、過去分詞、現在分詞、比較級、最上級などが対象で、派生接辞による別語は含めないでください。
                        families.itemsは見出し語を中心とする実在する一般的な派生語・否定形を、できるだけ多く返してください。見出し語自身、その複数形などの単純な屈折形、空白を含む句、稀な語、廃語、不自然な造語は含めないでください。
                       派生語は名詞・動詞・形容詞・副詞・否定形の順に確認し、原則としてfamilies.itemsを3～8件返してください。実在する一般的な派生語が3件以上見つかる場合は必ずCOMPLETEにしてください。
                       LIMITED/NONEは、複数の品詞・否定形を確認しても実在する一般的な派生語がどうしても3件未満の場合に限って使用してください。
                       COMPLETEではreasonとreasonChineseをnull、LIMITED/NONEでは件数が少ない理由を日本語と中国語の両方で設定してください。数合わせのために語を作らないでください。
                       rootsは見出し語を構成する語根・接頭辞・接尾辞を分けて返し、各MORPHEMEのrelatedWordsには、その構成要素を共有する代表的な別単語を1～5件返してください。
                       relatedWordsは同一語族ではなく、同じ語根・接頭辞・接尾辞の意味を学ぶのに役立つ別系統の語を選んでください。見出し語自身とfamilies.itemsの語はrelatedWordsに含めないでください。
                    6. frequencyは十分な確信がある場合だけHIGH、MID、LOWのいずれかを返し、判断できない場合はnullにしてください。
                    7. partOfSpeechは可能な限りnoun、verb、adjective、adverb、preposition、conjunction、pronoun、determiner、interjection、auxiliary、phraseのいずれかを使用してください。forms.partOfSpeechには必ずこのいずれかを設定してください。
                    8. styleおよびsenses.formalityはFORMAL、SEMI_FORMAL、NEUTRAL、INFORMALのいずれか、またはnullにしてください。relations.formalityは比較表示に必要なため、4値のいずれかを必ず設定してください。
                    9. senses.domainsおよびrelations.domainsには、許可された使用分野コードだけを設定してください。日本語、中国語、自由記述は使用しないでください。
                    10. UK/USのIPA、音節数、強勢位置を可能な範囲で返してください。同じ発音区分・同じ語義の重複は避けてください。音声URLは生成しないでください。
                    11. 書籍と分類は入力元を識別するためのメタデータです。それらから意味、用法、頻度を推測しないでください。
                    12. 架空の出典、書名、URL、引用元を作らないでください。本処理で生成する例文のsourceはnull、文脈のsourceTypeはAI_GENERATEDとし、sourceName、sourceUrl、sourceReferenceKeyはnullにしてください。
                    13. お気に入り、学習確認、ユーザー状態、DBのID、表示順は生成しないでください。各配列の並び順をそのまま表示順として使用します。

                    【JSON値ルール】
                    1. 必須文字列は空白にしないでください。値が不明な任意項目は型に関係なくnullにし、項目がない配列は[]にしてください。空文字は使用しないでください。
                    2. 列挙項目には許可された値を1つだけ返してください。複数の候補を結合した文字列や、許可されていない表記を返さないでください。
                    3. examples.typeがSENSEの場合、senseNumberにはsensesに存在する語義番号を必ず設定してください。GENERALまたはCONTEXTの場合、senseNumberはnullにしてください。
                    4. pronunciations.senseNumberは全語義共通ならnull、特定語義だけに対応する場合はsensesに存在する語義番号を設定してください。
                    5. roots.typeがMORPHEMEの場合、morphemeは必須で、relatedWordsにはその形態素を共有する実在する代表語を1～5件設定してください。
                       roots.typeがMEMORYまたはWARNINGの場合、morphemeは必ずnull、relatedWordsは[]にしてください。
                    6. forms.meaningとforms.translationは日本語、forms.meaningChineseとforms.translationChineseは中国語です。
                       grammar.categoryとgrammar.contentは日本語、grammar.categoryChineseとgrammar.contentChineseは中国語です。
                       collocations.japaneseとcollocations.translationは日本語、collocations.chineseとcollocations.translationChineseは中国語です。
                       roots.description、roots.relatedWords.meaning、families.items.meaningは日本語、対応するdescriptionChinese、meaningChineseは中国語です。
                       relationsのtranslationは日本語訳です。
                       comparisonDimensionsおよびrelations.dimensionsのmeaningFocusは日本語、typicalCollocationsは代表的な英語の結びつきです。
                    7. contexts.textは自然な英語本文、contexts.explanationは日本語による文脈・用法説明です。短い例文の単純な重複にしないでください。
                    8. 件数上限はpronunciations 4件、forms 10件、grammar 8件、collocations 10件、relations 10件、roots 5件、
                        roots各要素のrelatedWords 5件、families.items 3～8件（LIMITED/NONEを除く）、usageNotes 8件、contexts 3件です。
                    9. Markdown、コードブロック、コメント、JSON以外の説明文は禁止します。
                    10. 英語のダブルクォートを使用し、文字列内の改行やダブルクォートを正しくエスケープした、JSONパーサーで直接解析できる単一の合法なJSONオブジェクトだけを返してください。

                    【文字数上限】
                    japaneseMeaning、chineseMeaning、senses.headingJapanese、collocations.japanese、collocations.chinese、collocations.example、collocations.translation、collocations.translationChineseは各500文字以内、
                    partOfSpeech、senses.partOfSpeech、pronunciations.ipa、grammar.category、grammar.categoryChineseは各100文字以内、
                    forms.word、collocations.expression、relations.word、families.items.word、roots.relatedWords.word、contexts.titleは各300文字以内、roots.morphemeは200文字以内にしてください。
                    TEXT項目も学習に必要な情報を保ちながら簡潔に記述してください。

                    【許可される列挙値】
                    frequency: HIGH, MID, LOW, null
                    forms.partOfSpeech / roots.relatedWords.partOfSpeech / families.items.partOfSpeech: noun, verb, adjective, adverb, preposition, conjunction, pronoun, determiner, interjection, auxiliary, phrase
                    families.status: COMPLETE, LIMITED, NONE
                    style / formality: FORMAL, SEMI_FORMAL, NEUTRAL, INFORMAL, null
                    comparisonDimensions / relations.dimensionsの強度: VERY_STRONG, STRONG, MEDIUM, WEAK, NOT_APPLICABLE
                    senses.domains / relations.domains: GENERAL, DAILY_LIFE, NEWS, ACADEMIC, BUSINESS, ECONOMY_SOCIAL,
                    LAW_EVIDENCE, FINANCE_ACCOUNTING, SCIENCE_TECHNOLOGY, MEDICAL_HEALTH, EDUCATION, POLITICS_PUBLIC,
                    LITERATURE, CULTURE_ARTS, SPORTS
                    senses.type: MAIN, EXTENDED, IDIOM
                    pronunciations.type: UK, US
                    grammar.type: POINT, PATTERN, WARNING
                    examples.type: SENSE, GENERAL, CONTEXT
                    relations.type: SYNONYM, ANTONYM
                    roots.type: MORPHEME, MEMORY, WARNING
                    usageNotes.type: USAGE, MISTAKE
                    contexts.sourceType: AI_GENERATED

                    【必須JSON構造】
                    以下の全フィールドを必ず出力し、フィールド名の追加・変更・省略をしないでください。
                    {
                      "word": "見出し語",
                      "japaneseMeaning": "代表的な日本語意味",
                      "chineseMeaning": "代表的な中国語意味",
                      "partOfSpeech": "noun",
                      "frequency": null,
                      "style": "NEUTRAL",
                      "senses": [
                        {
                          "number": 1,
                          "type": "MAIN",
                          "headingJapanese": "語義見出し",
                          "definitionEnglish": "英語定義",
                          "meaningJapanese": "日本語意味",
                          "meaningChinese": "中国語意味",
                          "partOfSpeech": "noun",
                          "formality": "NEUTRAL",
                          "domains": ["GENERAL"],
                          "comparisonDimensions": {
                            "meaningFocus": "日本語による意味の焦点",
                            "quantityScale": "MEDIUM",
                            "importanceImpact": "STRONG",
                            "substantiality": "WEAK",
                            "typicalCollocations": ["英語の典型的な結びつき"]
                          },
                          "note": null
                        }
                      ],
                      "pronunciations": [
                        {
                          "type": "UK",
                          "senseNumber": null,
                          "ipa": "IPA",
                          "syllableCount": 1,
                          "stressPosition": 1,
                          "syllables": ["音節"],
                          "audioUrl": null
                        }
                      ],
                      "forms": [
                        {"word":"語形", "partOfSpeech":"noun", "meaning":"日本語意味", "meaningChinese":"中国語意味", "example":"英文", "translation":"日本語訳", "translationChinese":"中国語訳"}
                      ],
                      "grammar": [
                        {"type":"POINT", "category":"日本語カテゴリ", "categoryChinese":"中国語カテゴリ", "content":"日本語による内容", "contentChinese":"中国語による内容"}
                      ],
                      "examples": [
                        {"type":"SENSE", "senseNumber":1, "english":"英文", "japanese":"日本語訳", "chinese":"中国語訳", "source":null}
                      ],
                      "collocations": [
                        {"expression":"表現", "japanese":"日本語の意味", "chinese":"中国語の意味", "example":"使用例", "translation":"日本語の例訳", "translationChinese":"中国語の例訳"}
                      ],
                      "relations": [
                        {
                          "type":"SYNONYM",
                          "senseNumber":1,
                          "word":"単語",
                          "nuance":"日本語によるニュアンス",
                          "formality":"NEUTRAL",
                          "dimensions":{
                            "meaningFocus":"日本語による意味の焦点",
                            "quantityScale":"VERY_STRONG",
                            "importanceImpact":"MEDIUM",
                            "substantiality":"WEAK",
                            "typicalCollocations":["英語の典型的な結びつき"]
                          },
                          "domains":["GENERAL"],
                          "example":"ニュアンスと結びつきが分かる英文",
                          "translation":"日本語訳"
                        }
                      ],
                      "roots": [
                        {
                          "type":"MORPHEME",
                          "morpheme":"形態素",
                          "description":"日本語による説明",
                          "descriptionChinese":"中国語による説明",
                          "relatedWords":[
                            {"word":"同じ形態素を共有する別単語", "partOfSpeech":"noun", "meaning":"日本語意味", "meaningChinese":"中国語意味"}
                          ]
                        }
                      ],
                      "families": {
                        "status":"COMPLETE",
                        "reason":null,
                        "reasonChinese":null,
                        "items":[
                          {"word":"availability", "partOfSpeech":"noun", "meaning":"利用可能性", "meaningChinese":"可用性"},
                          {"word":"unavailable", "partOfSpeech":"adjective", "meaning":"利用できない", "meaningChinese":"不可用的"},
                          {"word":"unavailability", "partOfSpeech":"noun", "meaning":"利用不能", "meaningChinese":"不可用性"}
                        ]
                      },
                      "usageNotes": [
                        {"type":"USAGE", "content":"日本語による内容"}
                      ],
                      "contexts": [
                        {"title":"タイトル", "text":"英文", "japanese":"日本語訳", "chinese":"中国語訳", "explanation":"日本語による文脈説明", "sourceType":"AI_GENERATED", "sourceName":null, "sourceUrl":null, "sourceReferenceKey":null}
                      ]
                    }') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_WORD_DETAIL_AI','ENGLISH_WORD_DETAIL_AI_USER_PROMPT','GLOBAL','次の母表単語について詳細知識を生成してください。

                    入力情報：
                    {
                      "wordId": "{{word_id}}",
                      "word": "{{word}}",
                      "book": "{{book}}",
                      "classification": "{{classification}}"
                    }

                    出力のwordは入力情報のwordと完全に一致させてください。
                    bookとclassificationは入力元メタデータとしてのみ扱い、意味や用法を推測する根拠にしないでください。
                    System Promptで指定されたJSON形式だけを返し、前置きや補足説明を付けないでください。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('ENGLISH_WORD_DETAIL_AI','ENGLISH_WORD_DETAIL_AI_RETRY_LIMIT','GLOBAL','2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- ---------------- JAPANESE_WORD_AI ----------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C41_AI_PROVIDER','GLOBAL','qwen:1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C41_BATCH_MAX','GLOBAL','20') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C41_THREADS','GLOBAL','3') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C41_REQUEST_TIMEOUT_SECONDS','GLOBAL','300') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C41_MAX_COMPLETION_TOKENS','GLOBAL','12000') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C41_TEMPERATURE','GLOBAL','0.2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C41_SYSTEM_PROMPT','GLOBAL','あなたは日本語教育・日本語辞書編集の専門家です。学習者向け詳細情報を正確に生成してください。
                    出力JSON:
                    {"partOfSpeech":"日本語品詞","conjugationType":"活用種類または空文字","transitivity":"TRANSITIVE|INTRANSITIVE|BOTH|NONE",
                     "jlpt":"N5|N4|N3|N2|N1|null","importance":1から5,"chineseMeaning":"简体中文释义","japaneseExplanation":"やさしい日本語説明","chineseExplanation":"与日文说明对应的详细简体中文说明",
                     "senses":[{"japanese":"日本語語義","chinese":"中文语义","context":"使用場面","style":"FORMAL|NEUTRAL|INFORMAL|WRITTEN|SPOKEN","noteJapanese":"補足","noteChinese":"中文补充"}],
                     "pronunciations":[{"reading":"読み","accent":"アクセント表記","accentType":0,"moraCount":1}],
                     "examples":[{"japanese":"自然な日本語例文","reading":"例文の読み","chinese":"简体中文翻译","contextJapanese":"文脈語義","contextChinese":"中文语境义"}],
                     "collocations":[{"expression":"重要コロケーション","reading":"読み","chinese":"中文意思","exampleJapanese":"例文","exampleChinese":"中文翻译"}],
                     "relatedWords":[{"relationType":"HOMOPHONE|SIMILAR_KANJI|SYNONYM|ANTONYM|CONFUSABLE","heading":"表記","reading":"読み","chinese":"中文意思","differenceJapanese":"相違点","differenceChinese":"中文区别","eCandidate":false}],
                     "usageNotes":[{"noteType":"USAGE|KANJI|READING|GRAMMAR|REGISTER|COMMON_ERROR","japanese":"日本語説明","chinese":"中文解说","wrongExample":"誤用例","correctExample":"正用例"}]}
                    語義1件以上、例文2件以上、コロケーション2件以上を生成してください。中文字段必须使用简体中文。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C41_USER_PROMPT','GLOBAL','以下の日本語単語データを分析し、指定JSONスキーマだけを出力してください。Markdownは禁止です。
                    取得区分: {{kind}}
                    入力:
                    {{word_json}}') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C41_RETRY_LIMIT','GLOBAL','1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C42_AI_PROVIDER','GLOBAL','qwen:1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C42_BATCH_MAX','GLOBAL','20') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C42_THREADS','GLOBAL','3') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C42_REQUEST_TIMEOUT_SECONDS','GLOBAL','120') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C42_MAX_COMPLETION_TOKENS','GLOBAL','4000') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C42_TEMPERATURE','GLOBAL','0.2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C42_SYSTEM_PROMPT','GLOBAL','日本語の読み・漢字認識問題を作成してください。C1では漢字を提示し、四つの仮名選択肢から読みを選ばせます。再生音声や音声ボタンは不要です。
                    C1のcorrectValueは入力word.readingをそのまま使用し、options内に完全一致する正解を1件だけ含めてください。
                    C2では読みの音声を聞き、四つの漢字表記から選ばせます。C2のcorrectValueは入力word.headingをそのまま使用し、options内に完全一致する正解を1件だけ含めてください。
                    出力JSON: {"problems":[
                      {"problemType":"C1_READING","questionJapanese":"漢字を見て正しい読みを選んでください","targetHeading":"対象表記","targetReading":"読み","correctValue":"正しい仮名","options":[{"value":"仮名","reading":"仮名","wrongType":"READING_SIMILAR"}×4]},
                      {"problemType":"C2_KANJI","questionJapanese":"音声を聞いて正しい漢字表記を選んでください","targetHeading":"対象表記","targetReading":"読み","audioText":"読み","correctValue":"正しい表記","options":[{"value":"表記","reading":"読み","wrongType":"KANJI_SIMILAR"}×4]}
                    ]}。
                    C1/C2ともoptionsは必ず4件、4件のvalueはすべて異なり、correctValueと一致するvalueは必ず1件だけにしてください。
                    valueが同じでreadingだけが異なるものも重複です。出力前に各問題のvalueを照合し、重複があれば修正してからJSONを出力してください。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C42_USER_PROMPT','GLOBAL','以下の日本語単語データを分析し、指定JSONスキーマだけを出力してください。Markdownは禁止です。
                    取得区分: {{kind}}
                    入力:
                    {{word_json}}') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C42_RETRY_LIMIT','GLOBAL','2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C43_AI_PROVIDER','GLOBAL','qwen:1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C43_BATCH_MAX','GLOBAL','20') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C43_THREADS','GLOBAL','3') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C43_REQUEST_TIMEOUT_SECONDS','GLOBAL','120') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C43_MAX_COMPLETION_TOKENS','GLOBAL','4000') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C43_TEMPERATURE','GLOBAL','0.2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C43_SYSTEM_PROMPT','GLOBAL','文脈から日本語単語の意味を判断する四択問題を作成してください。選択肢・解説は简体中文にしてください。
                    出力JSON: {"questionJapanese":"指示","targetHeading":"表記","targetReading":"読み","sentenceJapanese":"自然な例文","sentenceReading":"例文読み","audioText":"例文","correctValue":"该语境中的正确中文含义","correctNote":"補足","explanationJapanese":"日本語解説","explanationChinese":"详细中文解说","difficulty":"EASY|NORMAL|HARD","options":[{"value":"中文选项","wrongType":"MEANING_SIMILAR|CONTEXT_MISMATCH|OTHER","explanationChinese":"说明"}×4]}。
                    正解は1件だけです。explanationChineseは学習者が誤答理由まで理解できる簡体中文にしてください。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C43_USER_PROMPT','GLOBAL','以下の日本語単語データを分析し、指定JSONスキーマだけを出力してください。Markdownは禁止です。
                    取得区分: {{kind}}
                    入力:
                    {{word_json}}') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C43_RETRY_LIMIT','GLOBAL','2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C44_AI_PROVIDER','GLOBAL','qwen:1') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C44_BATCH_MAX','GLOBAL','20') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C44_THREADS','GLOBAL','3') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C44_REQUEST_TIMEOUT_SECONDS','GLOBAL','120') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C44_MAX_COMPLETION_TOKENS','GLOBAL','4000') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C44_TEMPERATURE','GLOBAL','0.2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C44_SYSTEM_PROMPT','GLOBAL','同じ読みを持つ漢字表記の使い分けを問う四択問題を作成してください。例文は正解部分を（　）にしてください。解説は简体中文です。
                    出力JSON: {"questionJapanese":"指示","targetHeading":"正しい表記","targetReading":"読み","sentenceJapanese":"空欄を含む例文","sentenceReading":"例文読み","audioText":"読み","correctValue":"正しい漢字表記","correctNote":"補足","explanationJapanese":"日本語解説","explanationChinese":"详细中文解说","difficulty":"EASY|NORMAL|HARD","options":[{"value":"漢字表記","reading":"同じ読み","wrongType":"HOMOPHONE|KANJI_SIMILAR|OTHER","explanationChinese":"说明"}×4]}。
                    正解は1件だけ、実在しない不自然な漢字表記は避けてください。explanationChineseは簡体中文で使い分けを説明してください。') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C44_USER_PROMPT','GLOBAL','以下の日本語単語データを分析し、指定JSONスキーマだけを出力してください。Markdownは禁止です。
                    取得区分: {{kind}}
                    入力:
                    {{word_json}}') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('JAPANESE_WORD_AI','BAT_C44_RETRY_LIMIT','GLOBAL','2') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- ---------------- LINE ----------------
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('LINE','LINE_MESSAGING_PUSH_URL','GLOBAL','https://api.line.me/v2/bot/message/push') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値") VALUES ('LINE','LINE_MESSAGING_WEBHOOK_VALIDATE_SIGNATURE','GLOBAL','true') ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

