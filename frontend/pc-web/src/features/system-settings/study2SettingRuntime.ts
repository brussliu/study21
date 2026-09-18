// This compatibility runtime is generated from the Study 2.0 JavaScript implementation.
// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-nocheck
// Study2 の最新 setting.js（2026-08-26）を移行した暫定ランタイム。
// 画面構造は Vue が管理し、設定項目の定義・描画・操作はこのランタイムが担当する。
import { AI_TABS, aiTabOf, aiTabsOf, normalizeAiFields, sortAiFields } from './aiSettingsLayout';
(function() {
  'use strict';

  const PROVIDERS = ['chat-gpt', 'deepseek', 'doubao', 'gemini'];
  const ESSAY_MODEL_PROVIDERS = [
    { key: 'qwen', label: '千問', prefix: 'qwen' },
    { key: 'doubao', label: '豆包', prefix: 'doubao' },
    { key: 'deepseek', label: 'DeepSeek', prefix: 'deepseek' },
    { key: 'chatgpt', label: 'OpenAI', prefix: 'chatgpt' }
  ];
  const CATEGORIES = [
    { id: 'models', label: 'AIモデル', icon: 'fa-brain', description: '各AIサービスおよびOCR AIのモデル、APIキー、URLを設定します。音声認識（STT）の接続情報もここで設定します。', fields: [
      ...modelFields('qwen','千問'), ...modelFields('doubao','豆包'), ...modelFields('deepseek','DeepSeek'), ...modelFields('chatgpt','OpenAI'),
      f('bigmodelOcrModel','モデル指定','BigModel / 智譜 OCR','text'), f('bigmodelOcrApiKey','API Key','BigModel / 智譜 OCR','password'), f('bigmodelOcrUrl','URL','BigModel / 智譜 OCR','text'),
      // 音声認識（STT）。授業録音の STT プロバイダーで google / alibaba を選んだときにここを使う
      f('googleSttModel','モデル指定','Google Speech-to-Text','text',null,'書き起こしに使うモデルです。長い音声は latest_long、短い音声は latest_short を指定します。'),
      f('googleSttApiKey','API Key','Google Speech-to-Text','password',null,'Google Cloud の API キーです（Cloud Speech-to-Text API を有効にしたプロジェクトのキー）。'),
      sttUrlField(f('googleSttUrl','URL','Google Speech-to-Text','text',null,'Cloud Speech-to-Text v1 の speech:recognize の URL です。API Key は URL の key に付けて送ります。'), 'google-stt'),
      f('alibabaSttModel','モデル指定','Alibaba Paraformer-Realtime-V2','text',null,'リアルタイム音声認識のモデルです。既定は paraformer-realtime-v2 です。'),
      f('alibabaSttApiKey','API Key','Alibaba Paraformer-Realtime-V2','password',null,'DashScope（阿里云百炼）の API Key です。千問（Qwen）と同じキーを使えます。'),
      sttUrlField(f('alibabaSttUrl','URL','Alibaba Paraformer-Realtime-V2','text',null,'DashScope の WebSocket 接続先です（既定 wss://dashscope.aliyuncs.com/api-ws/v1/inference）。'), 'alibaba-stt')
    ]},
    { id: 'translation', label: '単語翻訳発音', icon: 'fa-language', description: '中国語・日本語の単語・例文翻訳APIと、英語の単語・例文発音取得API、同時処理数を設定します。', fields: [
      // 数字の項目は AI を呼ぶブロックと同じく**スライダー**にする（利用者の指示 2026-09-18）
      f('zhWordApi','中国語・単語翻訳API','中国語翻訳','select',['YouDao','Google']), f('zhSentenceApi','中国語・例文翻訳API','中国語翻訳','select',['YouDao','Google']), rangeField(f('zhThreads','中国語・処理スレッド数','中国語翻訳','range',null,'1～10',1,10),1,'',''),
      f('jaWordApi','日本語・単語翻訳API','日本語翻訳','select',['ExcelAPI','Google','YouDao']), f('jaSentenceApi','日本語・例文翻訳API','日本語翻訳','select',['ExcelAPI','Google','YouDao']), rangeField(f('jaThreads','日本語・処理スレッド数','日本語翻訳','range',null,'1～10',1,10),1,'',''),
      f('enVoiceWordApi','単語発音取得API','英語発音','select',['YouDao','Google']), f('enVoiceSentenceApi','例文発音取得API','英語発音','select',['YouDao','Google']), rangeField(f('enVoiceThreads','処理スレッド数','英語発音','range',null,'1～10',1,10),1,'','')
    ]},
    { id: 'batchai', label: '単語情報生成', icon: 'fa-robot', description: '初級編英訳中日問題（batC04）と中国語・日本語の単語説明生成（batC05/batC06）を3つのバッチで設定します。API KeyとURLは「AIモデル」の接続設定を共通利用します。', sections: [
      { id:'bat-c04', title:'batC04（初級編 英訳中日問題生成）', description:'初級編の英訳中日問題を生成するAIモデル、スレッド数、中国語・日本語プロンプトです。', icon:'fa-book', tabs:['基本設定','System Prompt','User Prompt'], ioNotice:{icon:'fa-language',input:'初級編単語',output:'初級編英訳中日問題'}, fieldKeys:['c04AiModel','c04Threads','c04SystemPromptZh','c04SystemPromptJa','c04UserPromptZh','c04UserPromptJa'] },
      { id:'bat-c05', title:'batC05（AI中国語説明取得）', description:'英単語の中国語説明をAIで生成します。', icon:'fa-language', tabs:['基本設定','System Prompt'], ioNotice:{icon:'fa-language',input:'英単語',output:'中国語説明'}, fieldKeys:['c05AiModel','c05Threads','c05PromptZh'] },
      { id:'bat-c06', title:'batC06（AI日本語説明取得）', description:'英単語の日本語説明をAIで生成します。', icon:'fa-language', tabs:['基本設定','System Prompt'], ioNotice:{icon:'fa-language',input:'英単語',output:'日本語説明'}, fieldKeys:['c06AiModel','c06Threads','c06PromptJa'] }
    ], fields: [
      fullField(tabField(f('c04AiModel','使用モデル','batC04 初級編','ai-model-dropdown',null,'初級編の既存問題を生成するモデルです。'),'基本設定')),
      rangeField(tabField(f('c04Threads','スレッド数','batC04 初級編','range',null,'初級編問題を同時生成するスレッド数です。', 1, 10),'基本設定'),1,'',''),
      tabField(f('c04SystemPromptZh','中国語 System Prompt','batC04 初級編','textarea',null,'中国語問題の役割、出力形式、選択肢と解説のルールです。'),'System Prompt'),
      tabField(f('c04SystemPromptJa','日本語 System Prompt','batC04 初級編','textarea',null,'日本語問題の役割、出力形式、選択肢と解説のルールです。'),'System Prompt'),
      tabField(f('c04UserPromptZh','中国語 User Prompt','batC04 初級編','textarea',null,'{word}、{language}、{kind1}を実行時に置換します。'),'User Prompt'),
      tabField(f('c04UserPromptJa','日本語 User Prompt','batC04 初級編','textarea',null,'{word}、{language}、{kind1}を実行時に置換します。'),'User Prompt'),
      fullField(tabField(f('c05AiModel','使用AIモデル（複数可）','batC05','multi',PROVIDERS,'中国語説明の生成に使用するモデルです。複数選択した場合は設定値をカンマ区切りで保存します。'),'基本設定')),
      rangeField(tabField(f('c05Threads','スレッド数','batC05','range',null,'中国語説明を同時生成するスレッド数です。', 1, 10),'基本設定'),1,'',''),
      tabField(f('c05PromptZh','System Prompt','batC05','textarea',null,'中国語説明生成のプロンプトです。'),'System Prompt'),
      fullField(tabField(f('c06AiModel','使用AIモデル（複数可）','batC06','multi',PROVIDERS,'日本語説明の生成に使用するモデルです。複数選択した場合は設定値をカンマ区切りで保存します。'),'基本設定')),
      rangeField(tabField(f('c06Threads','スレッド数','batC06','range',null,'日本語説明を同時生成するスレッド数です。', 1, 10),'基本設定'),1,'',''),
      tabField(f('c06PromptJa','System Prompt','batC06','textarea',null,'日本語説明生成のプロンプトです。'),'System Prompt')
    ]},
    { id: 'image', label: '画像共通処理', icon: 'fa-image', description: '共通AI OCR（batC91）の実行条件とプロンプトを設定します。API KeyとURLは「AIモデル」の接続設定を共通利用します。', sections: [
      { id:'bat-c91', title:'batC91（共通AI OCR）', description:'長文・設問画像の共通OCR条件とプロンプトを設定します。', icon:'fa-file-image', tabs:['基本設定','System Prompt','User Prompt','その他'], ioNotice:{icon:'fa-file-image',input:'長文・設問画像',output:'OCRテキスト（JSON）'}, fieldKeys:['intensiveOcrAiProvider','intensiveMaxImages','intensiveMaxImageMb','intensiveOcrTimeoutSeconds','intensiveOcrMaxImagePixels','intensiveOcrSystemPrompt','intensiveOcrUserPrompt','intensiveOcrMaxRetries'] }
    ], fields: [
      fullField(tabField(aiModelDropdownField('intensiveOcrAiProvider','batC91','共通OCRの実行に使用するモデルです。',true),'基本設定')),
      rangeField(tabField(f('intensiveMaxImages','最大画像枚数','batC91','range',null,'原文、設問、解答用紙を合わせて1回あたり1～20枚です。',1,20),'基本設定'),1,'枚',''),
      rangeField(tabField(f('intensiveMaxImageMb','画像1枚の最大サイズ','batC91','range',null,'画像1枚あたりの最大サイズ（MB）です。', 1, 50),'基本設定'),1,'MB',''),
      rangeField(tabField(f('intensiveOcrTimeoutSeconds','リクエストタイムアウト','batC91','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      rangeField(tabField(f('intensiveOcrMaxImagePixels','AI送信画像の最大辺','batC91','range',null,'OCRでAIへ送信する画像の最大辺ピクセルです。超える場合は縮小します。', 1024, 8192),'基本設定'),64,'px',''),
      disabledField(tabField(f('intensiveOcrSystemPrompt','System Prompt','batC91','textarea',null,'共通OCR System Promptです。現在は方式A個別プロンプトを使用するため編集できません。'),'System Prompt')),
      disabledField(tabField(f('intensiveOcrUserPrompt','User Prompt','batC91','textarea',null,'共通OCR User Promptです。現在は方式A個別プロンプトを使用するため編集できません。'),'User Prompt')),
      rangeField(tabField(f('intensiveOcrMaxRetries','最大再実行回数','batC91','range',null,'通信またはJSON構造検証エラー時の画像単位の再実行回数です。',0,5),'その他'),1,'回','')
    ]},
    { id: 'essay', label: '英作文AI添削', icon: 'fa-file-signature', description: '英作文の画像分類・OCRと英検基準AI添削を2つのバッチで設定します。API KeyとURLは「AIモデル」の接続設定を共通利用します。', sections: [
      { id:'bat-c11', title:'batC11（英作文 画像分類・OCR）', description:'画像を分類・OCRし、英作文の主題とタイトルを生成します。', icon:'fa-file-image', tabs:['基本設定','System Prompt','User Prompt','その他'], ioNotice:{icon:'fa-file-image',input:'英作文画像',output:'主題・タイトル・OCRテキスト（JSON）'}, fieldKeys:['essayMaxImages','essayMaxImageMb','essayOcrAiProvider','essayOcrMaxImagePixels','essayOcrRequestTimeoutSeconds','essayOcrPrompt','essayOcrUserPrompt','essayOcrRetryLimit'] },
      { id:'bat-c12', title:'batC12（英作文 英検基準AI添削）', description:'OCR済み英作文を英検基準で採点・添削します。', icon:'fa-check-double', tabs:['基本設定','System Prompt','User Prompt','その他'], ioNotice:{icon:'fa-check-double',input:'OCR済み英作文',output:'採点・添削結果（JSON）'}, fieldKeys:['essayGradingAiProvider','essayGradingRequestTimeoutSeconds','essayGradingPrompt','essayGradingUserPrompt','essayGradingRetryLimit'] }
    ], fields: [
      rangeField(tabField(f('essayMaxImages','最大画像枚数','batC11','range',null,'1回のアップロードで受け付ける画像枚数です。',1,20),'基本設定'),1,'枚',''),
      rangeField(tabField(f('essayMaxImageMb','画像1枚の最大サイズ','batC11','range',null,'画像1枚あたりの最大サイズ（MB）です。', 1, 50),'基本設定'),1,'MB',''),
      fullField(tabField(aiModelDropdownField('essayOcrAiProvider','batC11','画像の分類・OCRで使用するモデルです。',true),'基本設定')),
      rangeField(tabField(f('essayOcrMaxImagePixels','AI送信画像の最大辺','batC11','range',null,'OCRでAIへ送信する画像の最大辺ピクセルです。超える場合は縮小します。', 1024, 8192),'基本設定'),64,'px',''),
      rangeField(tabField(f('essayOcrRequestTimeoutSeconds','リクエストタイムアウト','batC11','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      tabField(f('essayOcrPrompt','System Prompt','batC11','textarea',null,'設問と手書き作文の分離、原文保持、判読不能文字の扱いを定義します。'),'System Prompt'),
      tabField(f('essayOcrUserPrompt','User Prompt','batC11','textarea',null,'{{level}}、{{image_count}}、{{image_categories}}を置換し、画像を添付します。'),'User Prompt'),
      rangeField(tabField(f('essayOcrRetryLimit','最大再実行回数','batC11','range',null,'通信またはJSON構造検証エラー時の画像単位の再実行回数です。',0,5),'その他'),1,'回',''),
      fullField(tabField(aiModelDropdownField('essayGradingAiProvider','batC12','英検基準の採点・添削に使用するモデルです。',false),'基本設定')),
      rangeField(tabField(f('essayGradingRequestTimeoutSeconds','リクエストタイムアウト','batC12','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      tabField(f('essayGradingPrompt','System Prompt','batC12','textarea',null,'採点基準、文字数判定原則、出力制約、タイトル生成を設定します。'),'System Prompt'),
      tabField(f('essayGradingUserPrompt','User Prompt','batC12','textarea',null,'{{level}}、{{question_text}}、{{essay_text}}、{{word_count}}を置換します。'),'User Prompt'),
      rangeField(tabField(f('essayGradingRetryLimit','最大再実行回数','batC12','range',null,'AIエラーまたはJSON構造検証エラー時の作文単位の再実行回数です。',0,5),'その他'),1,'回','')
    ]},
    { id: 'english_cloze', label: '英語穴埋め問題', icon: 'fa-puzzle-piece', description: '穴埋め問題画像のOCR・構造化と問題ごとのAI解説を2つのバッチで設定します。API KeyとURLは「AIモデル」の接続設定を共通利用します。', sections: [
      { id:'bat-c13', title:'batC13（英語穴埋め 画像OCR・問題構造化）', description:'問題画像から問題文、選択肢、答案と正解を抽出します。', icon:'fa-file-image', tabs:['基本設定','System Prompt','User Prompt','その他'], ioNotice:{icon:'fa-file-image',input:'穴埋め問題画像',output:'問題構造（JSON）'}, fieldKeys:['clozeMaxImages','clozeMaxImageMb','clozeOcrAiProvider','clozeOcrMaxImagePixels','clozeOcrTimeoutSeconds','clozeOcrSystemPrompt','clozeOcrUserPrompt','clozeOcrRetryLimit'] },
      { id:'bat-c14', title:'batC14（英語穴埋め 問題別AI解説）', description:'登録済み問題ごとに知識ポイントと解説を生成します。', icon:'fa-robot', tabs:['基本設定','System Prompt','User Prompt','その他'], ioNotice:{icon:'fa-robot',input:'登録済み問題',output:'知識ポイント・解説（JSON）'}, fieldKeys:['clozeExplanationAiProvider','clozeExplanationBatchMax','clozeExplanationThreads','clozeExplanationRequestTimeoutSeconds','clozeExplanationSystemPrompt','clozeExplanationUserPrompt','clozeExplanationRetryLimit'] }
    ], fields: [
      rangeField(tabField(f('clozeMaxImages','最大画像枚数','batC13','range',null,'1回のアップロードで受け付ける画像枚数です。',1,20),'基本設定'),1,'枚',''),
      rangeField(tabField(f('clozeMaxImageMb','画像1枚の最大サイズ','batC13','range',null,'画像1枚あたりの最大サイズ（MB）です。', 1, 50),'基本設定'),1,'MB',''),
      fullField(tabField(aiModelDropdownField('clozeOcrAiProvider','batC13','画像OCR・問題構造化で使用するモデルです。',true),'基本設定')),
      rangeField(tabField(f('clozeOcrMaxImagePixels','AI送信画像の最大辺','batC13','range',null,'OCRでAIへ送信する画像の最大辺ピクセルです。超える場合は縮小します。', 1024, 8192),'基本設定'),64,'px',''),
      rangeField(tabField(f('clozeOcrTimeoutSeconds','リクエストタイムアウト','batC13','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      tabField(f('clozeOcrSystemPrompt','System Prompt','batC13','textarea',null,'問題文、選択肢、作答時の答案、正解を画像から分離してJSON化します。'),'System Prompt'),
      tabField(f('clozeOcrUserPrompt','User Prompt','batC13','textarea',null,'{{source_type}}、{{year}}、{{source_name}}、{{chapter}}、{{note}}、{{image_count}}を置換します。'),'User Prompt'),
      rangeField(tabField(f('clozeOcrRetryLimit','最大再実行回数','batC13','range',null,'通信またはJSON構造検証エラー時の画像単位の再実行回数です。',0,5),'その他'),1,'回',''),
      fullField(tabField(aiModelDropdownField('clozeExplanationAiProvider','batC14','問題別AI解説の生成に使用するモデルです。',false),'基本設定')),
      rangeField(tabField(f('clozeExplanationBatchMax','1回の最大生成問題数','batC14','range',null,'一覧の「AI解説を生成」で1回に受け付ける問題数です。', 10, 100),'基本設定'),1,'問',''),
      rangeField(tabField(f('clozeExplanationThreads','スレッド数','batC14','range',null,'batC14が問題を並列処理するスレッド数です。APIの同時実行制限に合わせて設定してください。',1,10),'基本設定'),1,'',''),
      rangeField(tabField(f('clozeExplanationRequestTimeoutSeconds','リクエストタイムアウト','batC14','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      tabField(f('clozeExplanationSystemPrompt','System Prompt','batC14','textarea',null,'1回につき1問だけを分析し、知識ポイントと日本語解説をJSONで生成します。'),'System Prompt'),
      tabField(f('clozeExplanationUserPrompt','User Prompt','batC14','textarea',null,'{{question_no}}、{{question_text_json}}、{{options_json}}、{{student_answer_json}}、{{correct_answer_json}}をJSON値として置換します。'),'User Prompt'),
      rangeField(tabField(f('clozeExplanationRetryLimit','最大再実行回数','batC14','range',null,'AIエラーまたはJSON構造検証エラー時の問題単位の再実行回数です。',0,5),'その他'),1,'回','')
    ]},
    { id: 'english_reading_intensive', label: '英語読解・精読', icon: 'fa-book-open', description: '長文画像をOCRし、導読・逐文精読・設問解析を生成する4段階処理を設定します。API KeyとURLは「AIモデル」の接続設定を共通利用します。', sections: [
      { id:'bat-c15', title:'batC15（OCR実行・結果集計）', description:'OCR処理方式を選択し、各方式のOCR結果を集計して後続処理で使用する構造へ整形します。', icon:'fa-file-image', tabs:['基本設定'], ioNotice:{icon:'fa-file-image',input:'方式A・方式BのOCR結果',output:'長文・設問の最終構造（JSON）'}, fieldKeys:['intensiveBatC15AiProvider','intensiveOcrTextMethod'] },
      { id:'bat-c15-1', title:'batC15-1（方式A 設問画像OCR・最終整形）', description:'方式Aで設問画像をAI認識し、智譜OCRの文章文字と統合して最終JSONへ整形します。', icon:'fa-file-image', tabs:['基本設定','System Prompt','User Prompt'], ioNotice:{icon:'fa-file-image',input:'設問画像・智譜OCR文字',output:'最終整形JSON'}, fieldKeys:['intensiveMethodAArticleOcrProvider','intensiveOcrQuestionSystemPrompt','intensiveOcrQuestionUserPrompt','intensiveOcrMethodAStructureSystemPrompt','intensiveOcrMethodAStructureUserPrompt'] },
      { id:'bat-c15-3', title:'batC15-3（方式B OCR・AI構造化）', description:'方式Bで全画像を智譜OCRで文字化し、AIで問題構造へ変換します。', icon:'fa-file-image', tabs:['基本設定','System Prompt','User Prompt'], ioNotice:{icon:'fa-file-image',input:'全画像のOCR文字',output:'問題構造（JSON）'}, fieldKeys:['intensiveMethodBOcrProvider','intensiveOcrStructureSystemPrompt','intensiveOcrStructureUserPrompt'] },
      { id:'bat-c16', title:'batC16（基本情報・導読抽出）', description:'長文の基本情報と読解ガイドを生成します。', icon:'fa-compass', tabs:['基本設定','System Prompt','User Prompt'], ioNotice:{icon:'fa-compass',input:'OCR済み長文',output:'基本情報・導読（JSON）'}, fieldKeys:['intensiveGuideAiProvider','intensiveGuideTimeoutSeconds','intensiveGuideSystemPrompt','intensiveGuideUserPrompt'] },
      { id:'bat-c17', title:'batC17（解説・重点語彙生成）', description:'逐文解説、文法と重点語彙を生成します。', icon:'fa-book-reader', tabs:['基本設定','System Prompt','User Prompt'], ioNotice:{icon:'fa-book-reader',input:'分割済み英文',output:'逐文解説・語彙（JSON）'}, fieldKeys:['intensiveExplanationAiProvider','intensiveExplanationTimeoutSeconds','intensiveExplanationBatchSize','intensiveExplanationThreads','intensiveExplanationSystemPrompt','intensiveExplanationUserPrompt'] },
      { id:'bat-c18', title:'batC18（設問解析）', description:'設問、選択肢、正解根拠と解説を生成します。', icon:'fa-question-circle', tabs:['基本設定','System Prompt','User Prompt'], ioNotice:{icon:'fa-question-circle',input:'長文・設問構造',output:'解析結果（JSON）'}, fieldKeys:['intensiveQuestionAiProvider','intensiveQuestionTimeoutSeconds','intensiveQuestionSystemPrompt','intensiveQuestionUserPrompt'] }
    ], fields: [
      fullField(tabField(aiModelDropdownField('intensiveBatC15AiProvider','batC15','OCR結果の集計・最終整形で使用するモデルです。',false),'基本設定')),
      tabField(f('intensiveOcrTextMethod','文字モードの処理方式','batC15','select',[{value:'A',label:'方式A（画像別並列OCR）'},{value:'B',label:'方式B（智譜OCR＋AI構造化）'}],'【文字】を選択したときに使用します。【文字+図】は方式Aで処理します。'),'基本設定'),
      tabField(f('intensiveMethodAArticleOcrProvider','文章OCRモデル','batC15-1 方式A','select',[{value:'bigmodel:1',label:'BigModel / 智譜 OCR（glm-ocr）'}],'方式Aでは文章画像を智譜OCRで文字化します。'),'基本設定'),
      tabField(f('intensiveOcrQuestionSystemPrompt','設問画像 System Prompt','batC15-1 方式A','textarea',null,'設問画像スレッド専用のプロンプトです。未設定時は共通OCR System Promptを使用します。'),'System Prompt'),
      tabField(f('intensiveOcrQuestionUserPrompt','設問画像 User Prompt','batC15-1 方式A','textarea',null,'{{image_count}}、{{image_categories_json}}を置換します。設問画像スレッド専用に設定できます。'),'User Prompt'),
      tabField(f('intensiveOcrMethodAStructureSystemPrompt','最終整形 System Prompt','batC15-1 方式A','textarea',null,'智譜OCR文字と設問OCR結果を統合して最終JSONを整形します。'),'System Prompt'),
      tabField(f('intensiveOcrMethodAStructureUserPrompt','最終整形 User Prompt','batC15-1 方式A','textarea',null,'{{image_count}}、{{image_categories_json}}、{{ocr_texts}}、{{question_structure_json}}を置換します。'),'User Prompt'),
      tabField(f('intensiveMethodBOcrProvider','OCR用モデル','batC15-3 方式B','select',[{value:'bigmodel:1',label:'BigModel / 智譜 OCR（glm-ocr）'}],'画像を文字へ変換するOCR専用モデルです。方式B選択時に使用します。'),'基本設定'),
      tabField(f('intensiveOcrStructureSystemPrompt','System Prompt','batC15-3 方式B','textarea',null,'智譜OCRで抽出した文字をAIが構造化するルールです。作答印・赤字正解は認識しません。'),'System Prompt'),
      tabField(f('intensiveOcrStructureUserPrompt','User Prompt','batC15-3 方式B','textarea',null,'{{image_count}}、{{image_categories_json}}、{{ocr_texts}}、{{question_structure_json}}を置換します。'),'User Prompt'),
      fullField(tabField(aiModelDropdownField('intensiveGuideAiProvider','batC16','基本情報・導読の生成に使用するモデルです。',false),'基本設定')),
      rangeField(tabField(f('intensiveGuideTimeoutSeconds','リクエストタイムアウト','batC16','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      tabField(f('intensiveGuideSystemPrompt','System Prompt','batC16','textarea',null,'タイトル、出典、テーマ、語数、段落見出し、日中両言語の導読を生成します。'),'System Prompt'),
      tabField(f('intensiveGuideUserPrompt','User Prompt','batC16','textarea',null,'{{grade}}、{{difficulty}}、{{ocr_document_json}}、{{article_figures_json}}を置換します。'),'User Prompt'),
      fullField(tabField(aiModelDropdownField('intensiveExplanationAiProvider','batC17','逐文解説・重点語彙の生成に使用するモデルです。',false),'基本設定')),
      rangeField(tabField(f('intensiveExplanationTimeoutSeconds','リクエストタイムアウト','batC17','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      rangeField(tabField(f('intensiveExplanationBatchSize','1回の最大文数','batC17','range',null,'長文を分割してAIへ送る際の1リクエストあたり文数です。超える場合は自動分割します。', 10, 100),'基本設定'),1,'文',''),
      rangeField(tabField(f('intensiveExplanationThreads','スレッド数','batC17','range',null,'複数バッチを同時に処理するスレッド数です。API制限に応じて調整できます。', 1, 10),'基本設定'),1,'',''),
      tabField(f('intensiveExplanationSystemPrompt','System Prompt','batC17','textarea',null,'各文の日中翻訳、構文・表現、重点語彙、語法・注意を生成します。重点語彙は1文0～3語に絞ります。'),'System Prompt'),
      tabField(f('intensiveExplanationUserPrompt','User Prompt','batC17','textarea',null,'{{grade}}、{{difficulty}}、{{sentences_json}}を置換します。'),'User Prompt'),
      fullField(tabField(aiModelDropdownField('intensiveQuestionAiProvider','batC18','設問解析の生成に使用するモデルです。',false),'基本設定')),
      rangeField(tabField(f('intensiveQuestionTimeoutSeconds','リクエストタイムアウト','batC18','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      tabField(f('intensiveQuestionSystemPrompt','System Prompt','batC18','textarea',null,'単一設問・複数空欄・画像設問・画像選択肢を解析し、作答、正答案、根拠と解説を生成します。'),'System Prompt'),
      tabField(f('intensiveQuestionUserPrompt','User Prompt','batC18','textarea',null,'{{article_json}}、{{question_groups_json}}、{{question_figures_json}}を置換します。'),'User Prompt')
    ]},
    { id: 'study_monitor', label: '学習状況モニター', icon: 'fa-video', description: '動画の取込時間帯、スナップショット、およびAI分析条件を設定します。', sections: [
      { id:'bat-l02', title:'batL02（動画取込・スナップショット）', description:'監視カメラの動画を取り込み、指定した間隔でスナップショットを切り出す条件を設定します。', icon:'fa-video', tabs:['基本設定','カメラ基本情報'], ioNotice:{icon:'fa-video',input:'監視カメラ動画',output:'スナップショット画像'}, fieldKeys:['monitorVideoSourceDirectory','monitorSnapshotOutputDirectory','monitorVideoProcessingStartTime','monitorVideoProcessingEndTime','monitorSnapshotIntervalSeconds','monitorCameraLocation','monitorCameraContext'] },
      { id:'bat-l03', title:'batL03（学習状況AI分析）', description:'スナップショットをAIで分析し、学習状態を判定します。', icon:'fa-brain', tabs:['基本設定','System Prompt','User Prompt'], ioNotice:{icon:'fa-brain',input:'スナップショット画像',output:'学習状態（JSON）'}, fieldKeys:['monitorFirstAiProvider','monitorAiBatchLimit','monitorAiThreads','monitorAiTimeoutSeconds','monitorAiImageResolution','monitorFirstSystemPrompt','monitorFirstUserPrompt'] }
    ], fields: [
      tabField(f('monitorVideoSourceDirectory','動画ソースフォルダ','基本設定','text',null,'batL02 が再帰的に動画ファイルを検索するフォルダです。サーバー上の絶対パスを指定してください。'),'基本設定'),
      tabField(f('monitorSnapshotOutputDirectory','スナップショット保存フォルダ','基本設定','text',null,'batL02 が切り出した画像を保存するサーバー上の絶対パスです。動画ソースと同じマウント配下を推奨します。'),'基本設定'),
      tabField(f('monitorVideoProcessingStartTime','動画処理開始時刻','基本設定','time',null,'この時刻以降に撮影開始した動画だけを処理します。'),'基本設定'),
      tabField(f('monitorVideoProcessingEndTime','動画処理終了時刻','基本設定','time',null,'この時刻までに撮影開始した動画だけを処理します。'),'基本設定'),
      rangeField(tabField(f('monitorSnapshotIntervalSeconds','スナップショット間隔（秒）','基本設定','range',null,'batL02 が動画から画像を切り出す間隔です（10〜600 秒、10 秒刻み）。', 10, 600),'基本設定'),10,'秒',''),
      tabField(f('monitorCameraLocation','設置場所','カメラ基本情報','text',null,'例：自習室の学習机正面。唯一のカメラの設置場所を管理します（AIには渡しません）。'),'カメラ基本情報'),
      tabField(f('monitorCameraContext','撮影範囲・補足','カメラ基本情報','textarea',null,'例：机、椅子、PC画面、ノートが映る。カメラの管理情報として保存します（AIには渡しません）。'),'カメラ基本情報'),
      fullField(tabField(f('monitorFirstAiProvider','使用モデル','基本設定','ai-model-dropdown',null,'学習状態の判定に使用するモデルです。通常は Qwen3-VL-Flash を選択します。'),'基本設定')),
      rangeField(tabField(f('monitorAiBatchLimit','1回のAI分析枚数','基本設定','range',null,'batL03 を手動実行したときに分析する最大画像数です。', 10, 100),'基本設定'),1,'枚',''),
      rangeField(tabField(f('monitorAiThreads','スレッド数','基本設定','range',null,'batL03 が同時にAI分析する画像数です。APIの同時実行制限に合わせて設定してください。',1,10),'基本設定'),1,'',''),
      rangeField(tabField(f('monitorAiTimeoutSeconds','リクエストタイムアウト','基本設定','range',null,'AI APIへの1回の通信を待つ最大秒数です。',30,600),'基本設定'),10,'s',''),
      tabField(f('monitorAiImageResolution','AI送信画像解像度','基本設定','select',['3840×2160','2560×1440','1920×1080','1280×720'],'元のスナップショットは変更せず、AIへ送信する画像のみ縮小します。'),'基本設定'),
      tabField(f('monitorFirstSystemPrompt','System Prompt','System Prompt','textarea',null,'画面内容を優先して、6種類の状態と confidence / reason をJSONで返します。'),'System Prompt'),
      tabField(f('monitorFirstUserPrompt','User Prompt','User Prompt','textarea',null,'画像左上の撮影日時を確認するよう指示し、画像だけを渡します。'),'User Prompt')
    ]},
    { id: 'daily_report', label: '学習日報', icon: 'fa-clipboard-list', description: '学習日報の通知（ホーム画面のリマインダーと、提出時に LINE へ送る内容）を設定します。', fields: [
      f('dailyReportReminderEnabled','未記入リマインダー','ホーム画面通知','select',[{value:'true',label:'有効'},{value:'false',label:'無効'}],'有効の場合、当日の学習日報がまだ保存されていないとホーム画面に案内を表示します。'),
      // 提出時の LINE 通知（2.0 は日報の保存後に LINE へ送っていた。2.1 は【提出】時に送る予定）
      f('lineDailyReportEnabled','日報を LINE へ送る','LINE通知','select',['true','false'],'学習日報を提出したときに、日報の内容を LINE へ送ります。送信先は「LINE連携」の設定を使います。'),
      f('lineDailyReportTo','日報の送信先ID','LINE通知','text',null,'userId / groupId / roomId。複数はカンマ区切り。空のときは「LINE連携」のデフォルト送信先IDへ送ります。'),
      f('lineDailyReportSendOnResubmit','再提出でも送る','LINE通知','select',['true','false'],'提出後に編集して再度提出したときも送るかどうかです。'),
      fullField(f('lineDailyReportTemplate','メッセージ本文のテンプレート','LINE通知','textarea',null,'{{日付}}・{{曜日}}・{{記入者}}・{{時限数}}・{{授業一覧}}・{{振り返り}}・{{今夜の勉強}}・{{提出日時}} を実行時に置換します。')),
      fullField(f('lineDailyReportLessonTemplate','授業1件のテンプレート','LINE通知','textarea',null,'{{時限}}・{{教科}}・{{授業内容}}・{{掌握度}}・{{学習集中度}}・{{学習量}}・{{学習態度}}・{{ノート}} を置換します。{{授業一覧}} の中で1件ずつ使います。'))
    ]},
    { id: 'english_word_detail_ai', label: '英単語詳細AI取得', icon: 'fa-robot', description: '単語母表から詳細知識をAI Batchで生成し、中級編D・Eの問題データを生成する設定です。', sections: [
      { id:'detail', title:'batC21（英単語詳細AI取得）', description:'単語母表から詳細知識をAI Batchで生成し、検証・レビューして正式版へ反映する設定です。', icon:'fa-robot', tabs:['基本設定','System Prompt','User Prompt','その他'], ioNotice:{icon:'fa-font',input:'母表単語',output:'詳細知識（JSON）'}, fieldKeys:['wordDetailAiProvider','wordDetailAiBatchSize','wordDetailAiThreads','wordDetailAiRequestTimeoutSeconds','wordDetailAiMaxCompletionTokens','wordDetailAiTemperature','wordDetailAiSystemPrompt','wordDetailAiUserPrompt','wordDetailAiRetryLimit'] },
      { id:'intermediate_d', title:'batC22（中級編D）', description:'確定済み単語詳細からD誤答データを生成するAIモデル、実行条件、プロンプトです。', icon:'fa-layer-group', tabs:['基本設定','System Prompt','User Prompt','その他'], ioNotice:{icon:'fa-language',input:'中級編単語詳細',output:'D誤答データ'}, fieldKeys:['c042AiProvider','c042BatchSize','c042Threads','c042RequestTimeoutSeconds','c042MaxCompletionTokens','c042Temperature','c042SystemPrompt','c042UserPrompt','c042RetryLimit'] },
      { id:'intermediate_e', title:'batC23（中級編E）', description:'確定済み単語詳細の基本語義からE.文脈英訳問題を生成するAIモデル、実行条件、プロンプトです。', icon:'fa-layer-group', tabs:['基本設定','System Prompt','User Prompt','その他'], ioNotice:{icon:'fa-language',input:'中級編基本語義・例文',output:'E文脈選択データ'}, fieldKeys:['c043AiProvider','c043BatchSize','c043Threads','c043RequestTimeoutSeconds','c043MaxCompletionTokens','c043Temperature','c043SystemPrompt','c043UserPrompt','c043RetryLimit'] }
    ], fields: [
      fullField(tabField(f('wordDetailAiProvider','使用モデル','batC21','ai-model-dropdown',null,'AIモデルページで設定したモデルを選択します。'),'基本設定')),
      rangeField(tabField(f('wordDetailAiBatchSize','1回の最大単語数','batC21','range',null,'10語単位で設定します（10～200語）。',10,200),'基本設定'),10,'語',''),
      rangeField(tabField(f('wordDetailAiThreads','スレッド数','batC21','range',null,'同時に処理する単語詳細取得スレッド数です。', 1, 10),'基本設定'),1,'',''),
      rangeField(tabField(f('wordDetailAiRequestTimeoutSeconds','リクエストタイムアウト','batC21','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      rangeField(tabField(f('wordDetailAiMaxCompletionTokens','最大出力Token数','batC21','range',null,'推論過程と最終回答を合わせた、AI APIの1回あたりの最大出力Token数です。実際の上限は選択したモデルに依存します。',1024,65536),'基本設定'),1,' tokens',''),
      rangeField(tabField(f('wordDetailAiTemperature','Temperature','batC21','range',null,'値を低くすると構造化された単語知識を安定して生成できます。',0,2),'基本設定'),0.1,'',''),
      tabField(f('wordDetailAiSystemPrompt','System Prompt','batC21','textarea',null,'語義、発音、語形、例文、文法、コロケーション、類義語、語根、使用注意、文脈を構造化JSONで生成する共通指示です。'),'System Prompt'),
      tabField(f('wordDetailAiUserPrompt','User Prompt','batC21','textarea',null,'{{word_id}}、{{word}}、{{book}}、{{classification}}を実行時に置換します。'),'User Prompt'),
      rangeField(tabField(f('wordDetailAiRetryLimit','最大再実行回数','batC21','range',null,'構造検証エラーやAIエラー時に単語単位で再実行する上限です。',0,5),'その他'),1,'回',''),
      fullField(tabField(f('c042AiProvider','使用モデル','batC22 中級編','ai-model-dropdown',null,'確定済みの単語詳細から中級編データを生成するモデルです。'),'基本設定')),
      rangeField(tabField(f('c042BatchSize','1回の最大単語数','batC22 中級編','range',null,'1回の実行対象件数です（10語単位、最大200語）。',10,200),'基本設定'),10,'語',''),
      rangeField(tabField(f('c042Threads','スレッド数','batC22 中級編','range',null,'中級編データを同時生成するスレッド数です。', 1, 10),'基本設定'),1,'',''),
      rangeField(tabField(f('c042RequestTimeoutSeconds','リクエストタイムアウト','batC22 中級編','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      rangeField(tabField(f('c042MaxCompletionTokens','最大出力Token数','batC22 中級編','range',null,'AI APIの1回あたりの最大出力Token数です。',1024,65536),'基本設定'),1,' tokens',''),
      rangeField(tabField(f('c042Temperature','Temperature','batC22 中級編','range',null,'低い値ほど誤答JSONを安定して生成できます。',0,2),'基本設定'),0.1,'',''),
      tabField(f('c042SystemPrompt','System Prompt','batC22 中級編','textarea',null,'確定済み語義を変更せず、日中対応の誤答3件と双語解説を生成するルールです。'),'System Prompt'),
      tabField(f('c042UserPrompt','User Prompt','batC22 中級編','textarea',null,'{{word}}、{{correct_japanese}}、{{correct_chinese}}、{{source_json}}などを実行時に置換します。'),'User Prompt'),
      rangeField(tabField(f('c042RetryLimit','最大再実行回数','batC22 中級編','range',null,'AIエラーまたは構造検証エラー時の単語単位の再実行回数です。',0,5),'その他'),1,'回',''),
      fullField(tabField(f('c043AiProvider','使用モデル','batC23 中級編','ai-model-dropdown',null,'基本語義と例文からE.文脈英訳データを生成するモデルです。'),'基本設定')),
      rangeField(tabField(f('c043BatchSize','1回の最大単語数','batC23 中級編','range',null,'1回の実行対象件数です（10語単位、最大200語）。',10,200),'基本設定'),10,'語',''),
      rangeField(tabField(f('c043Threads','スレッド数','batC23 中級編','range',null,'E問題を同時生成するスレッド数です。', 1, 10),'基本設定'),1,'',''),
      rangeField(tabField(f('c043RequestTimeoutSeconds','リクエストタイムアウト','batC23 中級編','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      rangeField(tabField(f('c043MaxCompletionTokens','最大出力Token数','batC23 中級編','range',null,'AI APIの1回あたりの最大出力Token数です。',1024,65536),'基本設定'),1,' tokens',''),
      rangeField(tabField(f('c043Temperature','Temperature','batC23 中級編','range',null,'低い値ほど誤答JSONを安定して生成できます。',0,2),'基本設定'),0.1,'',''),
      tabField(f('c043SystemPrompt','System Prompt','batC23 中級編','textarea',null,'文脈に合う正解と、文脈では不適切な誤答を生成するルールです。'),'System Prompt'),
      tabField(f('c043UserPrompt','User Prompt','batC23 中級編','textarea',null,'{{word}}、{{correct_japanese}}、{{correct_chinese}}、{{source_json}}などを実行時に置換します。'),'User Prompt'),
      rangeField(tabField(f('c043RetryLimit','最大再実行回数','batC23 中級編','range',null,'AIエラーまたは構造検証エラー時の単語単位の再実行回数です。',0,5),'その他'),1,'回','')
    ]},
    { id: 'english_phrase_detail_ai', label: '英熟語詳細AI取得', icon: 'fa-link', description: '熟語の標準化・分類、詳細情報取得、およびD・E問題データ生成のAI設定です。', sections: [
      { id:'standardization', title:'標準化及び分類', description:'熟語母表の元表現を標準化し、表示表現・標準構文・熟語種別を取得する設定です。', icon:'fa-project-diagram', tabs:['基本設定','System Prompt','User Prompt','その他'], ioNotice:{icon:'fa-link',input:'熟語母表・元表現',output:'標準化・分類（JSON）'}, fieldKeys:['phraseStandardizationAiProvider','phraseStandardizationAiBatchSize','phraseStandardizationAiThreads','phraseStandardizationAiRequestTimeoutSeconds','phraseStandardizationAiMaxCompletionTokens','phraseStandardizationAiTemperature','phraseStandardizationAiAutoApproveThreshold','phraseStandardizationAiSystemPrompt','phraseStandardizationAiUserPrompt','phraseStandardizationAiRetryLimit'] },
      { id:'detail', title:'詳細情報取得', description:'標準化及び分類が完了した熟語から、詳細な学習情報を取得する設定です。', icon:'fa-robot', tabs:['基本設定','System Prompt','User Prompt','その他'], ioNotice:{icon:'fa-link',input:'標準化済み熟語',output:'詳細知識（JSON）'}, fieldKeys:['phraseDetailAiProvider','phraseDetailAiBatchSize','phraseDetailAiThreads','phraseDetailAiRequestTimeoutSeconds','phraseDetailAiMaxCompletionTokens','phraseDetailAiTemperature','phraseDetailAiSystemPrompt','phraseDetailAiUserPrompt','phraseDetailAiRetryLimit'] },
      { id:'phrase_d', title:'batC33（熟語D）', description:'確定済み熟語詳細からD.表現意味選択の誤答データを生成するAIモデル、実行条件、プロンプトです。', icon:'fa-link', tabs:['基本設定','System Prompt','User Prompt','その他'], ioNotice:{icon:'fa-language',input:'熟語詳細',output:'D誤答データ'}, fieldKeys:['c23AiProvider','c23BatchSize','c23Threads','c23RequestTimeoutSeconds','c23MaxCompletionTokens','c23Temperature','c23SystemPrompt','c23UserPrompt','c23RetryLimit'] },
      { id:'phrase_e', title:'batC34（熟語E）', description:'確定済み熟語詳細の例文・文脈からE.文脈意味選択データを生成するAIモデル、実行条件、プロンプトです。', icon:'fa-link', tabs:['基本設定','System Prompt','User Prompt','その他'], ioNotice:{icon:'fa-language',input:'熟語詳細・例文',output:'E文脈選択データ'}, fieldKeys:['c24AiProvider','c24BatchSize','c24Threads','c24RequestTimeoutSeconds','c24MaxCompletionTokens','c24Temperature','c24SystemPrompt','c24UserPrompt','c24RetryLimit'] }
    ], fields: [
      fullField(tabField(f('phraseStandardizationAiProvider','使用モデル','基本設定','ai-model-dropdown',null,'word.jspの「標準化及び分類」で使用するモデルです。'),'基本設定')),
      rangeField(tabField(f('phraseStandardizationAiBatchSize','1回の最大熟語数','基本設定','range',null,'10語単位で設定します（10～200語）。',10,200),'基本設定'),10,'語',''),
      rangeField(tabField(f('phraseStandardizationAiThreads','スレッド数','基本設定','range',null,'標準化と分類を同時に処理する熟語数です。', 1, 10),'基本設定'),1,'',''),
      rangeField(tabField(f('phraseStandardizationAiRequestTimeoutSeconds','リクエストタイムアウト','基本設定','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      rangeField(tabField(f('phraseStandardizationAiMaxCompletionTokens','最大出力Token数','基本設定','range',null,'標準化JSONの最大出力Token数です。',1024,65536),'基本設定'),1,' tokens',''),
      rangeField(tabField(f('phraseStandardizationAiTemperature','Temperature','基本設定','range',null,'低い値ほど表記と分類が安定します。', 0, 2),'基本設定'),0.1,'',''),
      tabField(f('phraseStandardizationAiAutoApproveThreshold','自動完了の信頼度','基本設定','select',['0.70','0.75','0.80','0.85','0.90','0.95','1.00'],'しきい値未満または警告ありの結果は「要確認」になります。'),'基本設定'),
      tabField(f('phraseStandardizationAiSystemPrompt','System Prompt','System Prompt','textarea',null,'省略記号の役割判定、標準構文、熟語種別、信頼度の出力ルールです。'),'System Prompt'),
      tabField(f('phraseStandardizationAiUserPrompt','User Prompt','User Prompt','textarea',null,'{{phrase_id}}、{{original}}、{{book}}、{{classification}}を置換します。'),'User Prompt'),
      rangeField(tabField(f('phraseStandardizationAiRetryLimit','最大再実行回数','その他','range',null,'JSON検証またはAIエラー時の熟語単位の再実行回数です。',0,5),'その他'),1,'回',''),
      fullField(tabField(f('phraseDetailAiProvider','使用モデル','基本設定','ai-model-dropdown',null,'word.jspの「詳細情報取得」で使用するモデルです。'),'基本設定')),
      rangeField(tabField(f('phraseDetailAiBatchSize','1回の最大熟語数','基本設定','range',null,'10語単位で設定します（10～200語）。',10,200),'基本設定'),10,'語',''),
      rangeField(tabField(f('phraseDetailAiThreads','スレッド数','基本設定','range',null,'熟語詳細を同時に処理するスレッド数です。', 1, 10),'基本設定'),1,'',''),
      rangeField(tabField(f('phraseDetailAiRequestTimeoutSeconds','リクエストタイムアウト','基本設定','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      rangeField(tabField(f('phraseDetailAiMaxCompletionTokens','最大出力Token数','基本設定','range',null,'詳細知識JSONの最大出力Token数です。',1024,65536),'基本設定'),1,' tokens',''),
      rangeField(tabField(f('phraseDetailAiTemperature','Temperature','基本設定','range',null,'低い値ほど構造化された詳細知識を安定して生成できます。', 0, 2),'基本設定'),0.1,'',''),
      tabField(f('phraseDetailAiSystemPrompt','System Prompt','System Prompt','textarea',null,'語義、発音、例文、類似表現、使用注意、文脈を日英中のJSONで生成するルールです。'),'System Prompt'),
      tabField(f('phraseDetailAiUserPrompt','User Prompt','User Prompt','textarea',null,'標準化済みの表示表現、標準構文、熟語種別等を置換します。'),'User Prompt'),
      rangeField(tabField(f('phraseDetailAiRetryLimit','最大再実行回数','その他','range',null,'JSON検証またはAIエラー時の熟語単位の再実行回数です。',0,5),'その他'),1,'回',''),
      fullField(tabField(f('c23AiProvider','使用モデル','batC33 熟語D','ai-model-dropdown',null,'確定済みの熟語詳細から熟語D誤答データを生成するモデルです。'),'基本設定')),
      rangeField(tabField(f('c23BatchSize','1回の最大熟語数','batC33 熟語D','range',null,'1回の実行対象件数です（10語単位、最大200語）。',10,200),'基本設定'),10,'語',''),
      rangeField(tabField(f('c23Threads','スレッド数','batC33 熟語D','range',null,'熟語Dデータを同時生成するスレッド数です。', 1, 10),'基本設定'),1,'',''),
      rangeField(tabField(f('c23RequestTimeoutSeconds','リクエストタイムアウト','batC33 熟語D','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      rangeField(tabField(f('c23MaxCompletionTokens','最大出力Token数','batC33 熟語D','range',null,'AI APIの1回あたりの最大出力Token数です。',1024,65536),'基本設定'),1,' tokens',''),
      rangeField(tabField(f('c23Temperature','Temperature','batC33 熟語D','range',null,'低い値ほど誤答JSONを安定して生成できます。',0,2),'基本設定'),0.1,'',''),
      tabField(f('c23SystemPrompt','System Prompt','batC33 熟語D','textarea',null,'確定済み熟語意味を変更せず、日中対応の誤答と双語解説を生成するルールです。'),'System Prompt'),
      tabField(f('c23UserPrompt','User Prompt','batC33 熟語D','textarea',null,'{{phrase}}、{{correct_japanese}}、{{correct_chinese}}、{{source_json}}などを実行時に置換します。'),'User Prompt'),
      rangeField(tabField(f('c23RetryLimit','最大再実行回数','batC33 熟語D','range',null,'AIエラーまたは構造検証エラー時の熟語単位の再実行回数です。',0,5),'その他'),1,'回',''),
      fullField(tabField(f('c24AiProvider','使用モデル','batC34 熟語E','ai-model-dropdown',null,'熟語詳細の例文・文脈から熟語Eデータを生成するモデルです。'),'基本設定')),
      rangeField(tabField(f('c24BatchSize','1回の最大熟語数','batC34 熟語E','range',null,'1回の実行対象件数です（10語単位、最大200語）。',10,200),'基本設定'),10,'語',''),
      rangeField(tabField(f('c24Threads','スレッド数','batC34 熟語E','range',null,'熟語E問題を同時生成するスレッド数です。', 1, 10),'基本設定'),1,'',''),
      rangeField(tabField(f('c24RequestTimeoutSeconds','リクエストタイムアウト','batC34 熟語E','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      rangeField(tabField(f('c24MaxCompletionTokens','最大出力Token数','batC34 熟語E','range',null,'AI APIの1回あたりの最大出力Token数です。',1024,65536),'基本設定'),1,' tokens',''),
      rangeField(tabField(f('c24Temperature','Temperature','batC34 熟語E','range',null,'低い値ほど誤答JSONを安定して生成できます。',0,2),'基本設定'),0.1,'',''),
      tabField(f('c24SystemPrompt','System Prompt','batC34 熟語E','textarea',null,'文脈に合う正解と、文脈では不適切な誤答を生成するルールです。'),'System Prompt'),
      tabField(f('c24UserPrompt','User Prompt','batC34 熟語E','textarea',null,'{{phrase}}、{{correct_japanese}}、{{correct_chinese}}、{{source_json}}などを実行時に置換します。'),'User Prompt'),
      rangeField(tabField(f('c24RetryLimit','最大再実行回数','batC34 熟語E','range',null,'AIエラーまたは構造検証エラー時の熟語単位の再実行回数です。',0,5),'その他'),1,'回','')
    ]},
    { id: 'japanese_word_ai', label: '日本語単語AI', icon: 'fa-spell-check', description: '日本語単語の詳細情報とC・D・E問題を生成する4つのバッチ設定です。API KeyとURLは「AIモデル」の接続設定を共通利用します。', sections: [
      { id:'detail', title:'batC41（詳細情報 A・B）', description:'A・Bで共通利用する語義、発音、例文、コロケーション等の詳細情報を生成します。', icon:'fa-robot', tabs:['基本設定','System Prompt','User Prompt','その他'], ioNotice:{icon:'fa-info-circle',input:'単語母表・掲載情報',output:'A・B詳細情報（JSON）'}, fieldKeys:['c25AiProvider','c25BatchMax','c25Threads','c25RequestTimeoutSeconds','c25MaxCompletionTokens','c25Temperature','c25SystemPrompt','c25UserPrompt','c25RetryLimit'] },
      { id:'problem_c', title:'batC42（C 読み・漢字）', description:'C1の仮名選択問題とC2の音声から漢字を選ぶ問題を同時に生成します。', icon:'fa-font', tabs:['基本設定','System Prompt','User Prompt','その他'], ioNotice:{icon:'fa-font',input:'単語表記・読み',output:'C1・C2問題（JSON）'}, fieldKeys:['c26AiProvider','c26BatchMax','c26Threads','c26RequestTimeoutSeconds','c26MaxCompletionTokens','c26Temperature','c26SystemPrompt','c26UserPrompt','c26RetryLimit'] },
      { id:'problem_d', title:'batC43（D 文脈意味）', description:'日本語例文の文脈に合う中国語意味を選択する問題を生成します。', icon:'fa-align-left', tabs:['基本設定','System Prompt','User Prompt','その他'], ioNotice:{icon:'fa-language',input:'単語詳細・例文',output:'D問題・中文解説（JSON）'}, fieldKeys:['c27AiProvider','c27BatchMax','c27Threads','c27RequestTimeoutSeconds','c27MaxCompletionTokens','c27Temperature','c27SystemPrompt','c27UserPrompt','c27RetryLimit'] },
      { id:'problem_e', title:'batC44（E 漢字使分け）', description:'同じ読みを持つ漢字表記の使い分け問題と中国語解説を生成します。', icon:'fa-exchange-alt', tabs:['基本設定','System Prompt','User Prompt','その他'], ioNotice:{icon:'fa-language',input:'表記・同音語情報',output:'E問題・中文解説（JSON）'}, fieldKeys:['c28AiProvider','c28BatchMax','c28Threads','c28RequestTimeoutSeconds','c28MaxCompletionTokens','c28Temperature','c28SystemPrompt','c28UserPrompt','c28RetryLimit'] }
    ], fields: [
      fullField(tabField(f('c25AiProvider','使用モデル','batC41','ai-model-dropdown',null,'詳細情報（A・B共通）の生成に使用するモデルです。'),'基本設定')),
      rangeField(tabField(f('c25BatchMax','1回の最大単語数','batC41','range',null,'詳細情報取得で一度に受け付ける単語数です。', 10, 200),'基本設定'),1,'語',''),
      rangeField(tabField(f('c25Threads','スレッド数','batC41','range',null,'詳細情報を同時処理する単語数です。',1,10),'基本設定'),1,'',''),
      rangeField(tabField(f('c25RequestTimeoutSeconds','リクエストタイムアウト','batC41','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      rangeField(tabField(f('c25MaxCompletionTokens','最大出力Token数','batC41','range',null,'詳細情報JSONの最大出力Token数です。',1024,65536),'基本設定'),1,' tokens',''),
      rangeField(tabField(f('c25Temperature','Temperature','batC41','range',null,'低い値ほど詳細情報JSONを安定して生成できます。',0,2),'基本設定'),0.1,'',''),
      tabField(f('c25SystemPrompt','System Prompt','batC41','textarea',null,'詳細情報JSONの項目、件数、日中言語ルールを指定します。'),'System Prompt'),
      tabField(f('c25UserPrompt','User Prompt','batC41','textarea',null,'{{kind}}と{{word_json}}を実行時に置換します。'),'User Prompt'),
      rangeField(tabField(f('c25RetryLimit','最大再実行回数','batC41','range',null,'通信またはJSON構造検証エラー時の単語単位の再実行回数です。',0,5),'その他'),1,'回',''),
      fullField(tabField(f('c26AiProvider','使用モデル','batC42','ai-model-dropdown',null,'C1・C2問題の生成に使用するモデルです。'),'基本設定')),
      rangeField(tabField(f('c26BatchMax','1回の最大単語数','batC42','range',null,'C問題取得で一度に受け付ける単語数です。', 10, 200),'基本設定'),1,'語',''),
      rangeField(tabField(f('c26Threads','スレッド数','batC42','range',null,'C問題を同時処理する単語数です。',1,10),'基本設定'),1,'',''),
      rangeField(tabField(f('c26RequestTimeoutSeconds','リクエストタイムアウト','batC42','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      rangeField(tabField(f('c26MaxCompletionTokens','最大出力Token数','batC42','range',null,'C1・C2問題JSONの最大出力Token数です。',1024,65536),'基本設定'),1,' tokens',''),
      rangeField(tabField(f('c26Temperature','Temperature','batC42','range',null,'低い値ほど問題JSONを安定して生成できます。',0,2),'基本設定'),0.1,'',''),
      tabField(f('c26SystemPrompt','System Prompt','batC42','textarea',null,'C1は音声なしの仮名四択、C2は音声から漢字を選ぶ規則を指定します。'),'System Prompt'),
      tabField(f('c26UserPrompt','User Prompt','batC42','textarea',null,'{{kind}}と{{word_json}}を実行時に置換します。'),'User Prompt'),
      rangeField(tabField(f('c26RetryLimit','最大再実行回数','batC42','range',null,'通信またはJSON構造検証エラー時の単語単位の再実行回数です。',0,5),'その他'),1,'回',''),
      fullField(tabField(f('c27AiProvider','使用モデル','batC43','ai-model-dropdown',null,'D問題の生成に使用するモデルです。'),'基本設定')),
      rangeField(tabField(f('c27BatchMax','1回の最大単語数','batC43','range',null,'D問題取得で一度に受け付ける単語数です。', 10, 200),'基本設定'),1,'語',''),
      rangeField(tabField(f('c27Threads','スレッド数','batC43','range',null,'D問題を同時処理する単語数です。',1,10),'基本設定'),1,'',''),
      rangeField(tabField(f('c27RequestTimeoutSeconds','リクエストタイムアウト','batC43','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      rangeField(tabField(f('c27MaxCompletionTokens','最大出力Token数','batC43','range',null,'D問題JSONの最大出力Token数です。',1024,65536),'基本設定'),1,' tokens',''),
      rangeField(tabField(f('c27Temperature','Temperature','batC43','range',null,'低い値ほど問題JSONを安定して生成できます。',0,2),'基本設定'),0.1,'',''),
      tabField(f('c27SystemPrompt','System Prompt','batC43','textarea',null,'文脈意味四択と簡体中文解説の生成規則を指定します。'),'System Prompt'),
      tabField(f('c27UserPrompt','User Prompt','batC43','textarea',null,'{{kind}}と{{word_json}}を実行時に置換します。'),'User Prompt'),
      rangeField(tabField(f('c27RetryLimit','最大再実行回数','batC43','range',null,'通信またはJSON構造検証エラー時の単語単位の再実行回数です。',0,5),'その他'),1,'回',''),
      fullField(tabField(f('c28AiProvider','使用モデル','batC44','ai-model-dropdown',null,'E問題の生成に使用するモデルです。'),'基本設定')),
      rangeField(tabField(f('c28BatchMax','1回の最大単語数','batC44','range',null,'E問題取得で一度に受け付ける単語数です。', 10, 200),'基本設定'),1,'語',''),
      rangeField(tabField(f('c28Threads','スレッド数','batC44','range',null,'E問題を同時処理する単語数です。',1,10),'基本設定'),1,'',''),
      rangeField(tabField(f('c28RequestTimeoutSeconds','リクエストタイムアウト','batC44','range',null,'AI APIへの1回の通信を待つ最大秒数です。', 30, 600),'基本設定'),10,'s',''),
      rangeField(tabField(f('c28MaxCompletionTokens','最大出力Token数','batC44','range',null,'E問題JSONの最大出力Token数です。',1024,65536),'基本設定'),1,' tokens',''),
      rangeField(tabField(f('c28Temperature','Temperature','batC44','range',null,'低い値ほど問題JSONを安定して生成できます。',0,2),'基本設定'),0.1,'',''),
      tabField(f('c28SystemPrompt','System Prompt','batC44','textarea',null,'同音漢字使い分け四択と簡体中文解説の生成規則を指定します。'),'System Prompt'),
      tabField(f('c28UserPrompt','User Prompt','batC44','textarea',null,'{{kind}}と{{word_json}}を実行時に置換します。'),'User Prompt'),
      rangeField(tabField(f('c28RetryLimit','最大再実行回数','batC44','range',null,'通信またはJSON構造検証エラー時の単語単位の再実行回数です。',0,5),'その他'),1,'回','')
    ]},
    { id: 'line', label: 'LINE連携', icon: 'fa-comment-dots', description: 'LINE Messaging APIとWebhook署名検証を設定します。', fields: [
      f('lineMessagingChannelAccessToken','チャネルアクセストークン','Messaging API','password'), f('lineMessagingPushUrl','Push API URL','Messaging API','text'), f('lineMessagingDefaultTo','デフォルト送信先ID','Messaging API','text',null,'複数の場合はカンマ区切り'),
      f('lineMessagingChannelSecret','Channel secret','Webhook','password'), f('lineMessagingWebhookValidateSignature','署名検証','Webhook','select',['true','false'])
    ]},
    /*
     * AI生図（図形管理）。**描画は別コンポーネントが行う**（views/admin/system-settings/
     * GeometryAiSettingsSection.vue。項目名・並び・説明・保存はそちらが持つ）。
     * `external: true` … 分類ナビには出すが、**パネルの項目はランタイムが描かない**
     * （空のマウント点 `[data-external-slot="geometry_ai"]` だけを出し、その中へコンポーネントが
     * Teleport する）。カタログのキーにはバッチ専用・保存不可のものも含まれるため、
     * ここから描くと保存できない項目を出してしまう。
     * ここに fields を定義するのは「画面全体の【設定を保存】と再読込に、この 7 キーを参加させる」ためで、
     * 値の取得・反映は registerSection で登録された画面側の関数を通す（collectValues / applyValues）。
     * 残りの GEOMETRY_AI キー（System Prompt・許可コマンド・日次上限など）はバッチ専用で
     * 画面に出さないため、ここには定義しない（SettingPageFields にも入れない）。
     * なお実際に送るキーの一覧は `SettingPageFields.java` と
     * `GeometryAiSettingsSection.vue` が持つ（ここは画面全体の保存に参加させるための最小限）。
     */
    { id: 'geometry_ai', label: '図形管理', icon: 'fa-image', external: true,
      description: 'AI 生図と AI 画図助手の設定です。', fields: [
      f('geometryAiEnabled','AI 生図を使う（有効／無効）','AI生図','select',['true','false']),
      f('geometryAiProvider','使用する AI モデル','AI生図','select',['qwen:4','doubao:1','deepseek:1','chatgpt:1']),
      rangeField(f('geometryAiMaxImageMb','画像 1 枚の最大サイズ','AI生図','range',null,'1〜50 MB（画面のスライダーと同じ）',1,50),1,'MB',''),
      f('geometryAiDefaultCrop','既定の切り抜き','AI生図','select',['manual','center','all']),
      f('geometryAiDefaultKind','既定の分類','AI生図','select',['figure','function','mixed']),
      f('geometryAiApproval','処理結果の承認フロー','AI生図','select',['manual','auto']),
      f('geometryAiInstructionTemplate','AI への指示テンプレート','AI生図','textarea',null,'{kind}・{figureType}・{note} を実行時に置換します。')
    ]},
    /*
     * 授業録音 / AI 授業記録。**描画は別コンポーネントが行う**（views/admin/system-settings/
     * ClassroomAiSettingsSection.vue。項目名・並び・説明・保存はそちらが持つ）。
     * `external: true` の扱いは GEOMETRY_AI と同じ（ナビには出す／項目はランタイムが描かない）。
     * 実際に送るキーは **CLASSROOM_AI のカタログ全 30 キーのうち画面が持つ 21 キー**で、`SettingPageFields.java` と
     * `ClassroomAiSettingsSection.vue` が持つ（ここは画面全体の保存に参加させるための最小限）。
     */
    { id: 'classroom_ai', label: '授業録音', icon: 'fa-video', external: true,
      description: '授業の録音・書き起こし（STT）・AI 授業ノートの設定です。', fields: [
      f('classroomAiEnabled','授業録音と AI 授業ノートを使う（有効／無効）','授業録音','select',['true','false']),
      f('classroomAiSttProvider','STT プロバイダー','授業録音','select',['browser','google','alibaba','stub'],'browser=ブラウザ音声認識（キー不要）／google=Google Speech-to-Text／alibaba=Alibaba Paraformer-Realtime-V2（接続情報は AIモデルページ）'),
      rangeField(f('classroomAiChunkSeconds','アップロード分塊の長さ','授業録音','range',null,'5〜120 秒（既定 20）',5,120),5,'s',''),
      rangeField(f('classroomAiTriggerIntervalMinutes','間隔トリガー（分）','授業録音','range',null,'3〜20 分（既定 5）',3,20),1,'分',''),
      rangeField(f('classroomAiTriggerMinChars','文字量トリガー','授業録音','range',null,'100〜10000 文字（既定 200）',100,10000),100,'文字',''),
      f('classroomAiTriggerKeywords','トリガーキーワード','授業録音','text',null,'カンマ区切り（例 宿題,試験の重点）'),
      rangeField(f('classroomAiMaxRecordingMinutes','録音最大時間（分）','授業録音','range',null,'1〜240 分（既定 120）',1,240),1,'分','')
    ]}
  ];

  /*
   * AI設定は「日本語単語AI」と同じ構造へ統一する。
   * - カテゴリ内を実際のバッチ単位で分割
   * - 各バッチ内をタブで整理（TAB 名・並び・項目名・スライダーは aiSettingsLayout.ts が唯一の定義）
   * - 廃止済み batC19（英単語教材AI認識）はカテゴリ定義自体を削除済み
   */
  function configureAiCategory(categoryId, sectionDefinitions) {
    const category = CATEGORIES.find(function(item) { return item.id === categoryId; });
    if (!category) return;

    category.sections = sectionDefinitions.map(function(definition) {
      const fields = category.fields.filter(function(field) { return definition.matches(field); });
      fields.forEach(function(field) {
        field.tab = definition.resolveTab ? definition.resolveTab(field) : aiTabOf(field.key);
      });
      const preferredTabs = definition.tabs || AI_TABS;
      const tabs = preferredTabs.filter(function(tab) {
        return fields.some(function(field) { return field.tab === tab; });
      });
      return {
        id: definition.id,
        title: definition.title,
        description: definition.description,
        icon: definition.icon || 'fa-robot',
        ioNotice: definition.ioNotice,
        tabs: tabs,
        fieldKeys: fields.map(function(field) { return field.key; })
      };
    }).filter(function(section) { return section.fieldKeys.length > 0; });
    category.tabs = [];
  }

  configureAiCategory('models', [
    {
      id: 'shared-models',
      title: '共通AI接続設定（各AIバッチ共通）',
      description: '各バッチから参照するモデル、API Key、URLをAIサービス別に管理します。',
      icon: 'fa-brain',
      tabs: ['千問', '豆包', 'DeepSeek', 'OpenAI', 'BigModel / 智譜 OCR', 'Google Speech-to-Text', 'Alibaba Paraformer-Realtime-V2'],
      matches: function() { return true; },
      resolveTab: function(field) { return field.group; }
    }
  ]);

  // batchai（batC04/batC05/batC06）はカテゴリ定義内の sections で直接定義済み。

  // essay（batC11/batC12）、english_cloze（batC13/batC14）、
  // english_reading_intensive（batC15/batC15-1/batC15-3/batC16/batC17/batC18）は
  // カテゴリ定義内の sections で直接「日本語単語AI」と同じ形式を定義済み。

  configureAiCategory('english_word_detail_ai', [
    {
      id: 'bat-c21',
      title: 'batC21（英単語詳細AI取得）',
      description: '単語母表から詳細知識を生成し、検証対象として登録します。',
      icon: 'fa-robot',
      matches: function(field) { return /^wordDetail/.test(field.key); }
    },
    {
      id: 'bat-c22',
      title: 'batC22（中級編D 英訳中日問題）',
      description: '確定済み単語詳細からD.英訳中日の誤答データを生成します。',
      icon: 'fa-layer-group',
      ioNotice: { icon: 'fa-language', input: '中級編単語詳細', output: 'D誤答データ' },
      matches: function(field) { return /^c042/.test(field.key); }
    },
    {
      id: 'bat-c23',
      title: 'batC23（中級編E 文脈英訳問題）',
      description: '確定済み単語詳細の基本語義からE.文脈英訳問題を生成します。',
      icon: 'fa-layer-group',
      ioNotice: { icon: 'fa-language', input: '中級編基本語義・例文', output: 'E文脈選択データ' },
      matches: function(field) { return /^c043/.test(field.key); }
    }
  ]);

  configureAiCategory('english_phrase_detail_ai', [
    {
      id: 'bat-c31',
      title: 'batC31（英熟語標準化及び分類）',
      description: '熟語母表の元表現を標準化し、表示表現と熟語種別を取得します。',
      icon: 'fa-project-diagram',
      matches: function(field) { return /^phraseStandardization/.test(field.key); }
    },
    {
      id: 'bat-c32',
      title: 'batC32（英熟語詳細情報AI取得）',
      description: '標準化済み熟語から詳細な学習情報を生成します。',
      icon: 'fa-robot',
      matches: function(field) { return /^phraseDetail/.test(field.key); }
    },
    {
      id: 'bat-c33',
      title: 'batC33（熟語D 表現意味選択問題）',
      description: '確定済み熟語詳細からD.表現意味選択の誤答データを生成します。',
      icon: 'fa-link',
      ioNotice: { icon: 'fa-language', input: '熟語詳細', output: 'D誤答データ' },
      matches: function(field) { return /^c23/.test(field.key); }
    },
    {
      id: 'bat-c34',
      title: 'batC34（熟語E 文脈意味選択問題）',
      description: '確定済み熟語詳細の例文・文脈からE.文脈意味選択データを生成します。',
      icon: 'fa-link',
      ioNotice: { icon: 'fa-language', input: '熟語詳細・例文', output: 'E文脈選択データ' },
      matches: function(field) { return /^c24/.test(field.key); }
    }
  ]);

  /*
   * すべての AI バッチの設定を共通レイアウトへ揃える（利用者の指示 2026-09-16）。
   * TAB・名称・並び・スライダーの規則は aiSettingsLayout.ts が唯一の定義。
   * AI を使わないブロック（batL02 のカメラ基本情報など）は対象外＝今のまま。
   */
  const AI_CATEGORY_IDS = [
    'batchai', 'image', 'essay', 'english_cloze', 'english_reading_intensive',
    'study_monitor', 'english_word_detail_ai', 'english_phrase_detail_ai', 'japanese_word_ai'
  ];
  const AI_EXCLUDED_SECTIONS = { study_monitor: ['bat-l02'] };

  function normalizeAiSections() {
    AI_CATEGORY_IDS.forEach(function(categoryId) {
      const category = CATEGORIES.find(function(item) { return item.id === categoryId; });
      if (!category || !category.sections) return;
      const excluded = AI_EXCLUDED_SECTIONS[categoryId] || [];
      category.sections.forEach(function(section) {
        if (excluded.indexOf(section.id) >= 0) return;
        const fields = category.fields.filter(function(field) { return section.fieldKeys.includes(field.key); });
        normalizeAiFields(fields).forEach(function(next) {
          const field = fields.find(function(item) { return item.key === next.key; });
          if (field) Object.assign(field, next);
        });
        section.tabs = aiTabsOf(fields);
      });
      // 画面は category.fields の順に描くので、定義そのものを TAB → 項目の順にしておく
      category.fields = sortAiFields(category.fields);
    });
  }
  normalizeAiSections();

  let currentSettings = {};
  let toastTimer = 0;
  /**
   * 別コンポーネントが描く設定項目（AI 生図の設定セクションなど）の値の受け渡し。
   * 画面全体の【設定を保存】は collectValues() の値を送るため、登録が無いと
   * そのコンポーネントの値が保存から漏れる（＝保存ボタンが効かないように見える）。
   */
  const fieldSections = [];
  function registerSection(section) {
    if (!section || typeof section.getValues !== 'function') return;
    fieldSections.push(section);
    if (typeof section.applyValues === 'function') section.applyValues(currentSettings);
  }

  function f(key, label, group, type, options, help, min, max) { return { key, label, group, type, options: options || [], help: help || '', min, max }; }
  function disabledField(field) { field.disabled = true; return field; }
  function tabField(field, tab) { field.tab = tab; return field; }
  function fullField(field) { field.full = true; return field; }
  function rangeField(field, step, suffix, storageSuffix) { field.step = step || 1; field.suffix = suffix || ''; field.storageSuffix = storageSuffix || ''; return field; }
  function _toggleField(field) { field.trueValue = 'true'; field.falseValue = 'false'; return field; }
  function aiModelDropdownField(key, group, help, includeBigModel) {
    const field = f(key,'使用モデル',group,'ai-model-dropdown',null,help);
    field.includeBigModel = Boolean(includeBigModel);
    return field;
  }
  function aiModelOptions(includeBigModel) {
    const options = ESSAY_MODEL_PROVIDERS.reduce(function(result, provider) {
      const slots = provider.key === 'qwen' ? [1, 2, 3, 4, 5] : [1, 2];
      slots.forEach(function(slot) {
        const suffix = slot === 1 ? '' : String(slot);
        const model = currentSettings[provider.prefix + 'Model' + suffix] || ('モデル' + slot + '未設定');
        result.push({ value: provider.key + ':' + slot, label: provider.label + ' / ' + model });
      });
      return result;
    }, []);
    if (includeBigModel) options.push({ value: 'bigmodel:1', label: 'BigModel / 智譜 OCR / ' + (currentSettings.bigmodelOcrModel || 'glm-ocr') });
    return options;
  }
  /**
   * STT（音声認識）の URL 項目へ【接続テスト】を付ける。
   *
   * 接続テストは音声（無音のごく短い WAV）を 1 回送って、API Key とモデル・URL が正しいかを確かめる。
   * プロバイダー名は `google-stt` / `alibaba-stt`（Chat の 4 プロバイダーとは別の入口
   * ＝ `POST /api/admin/setting/testStt` へ送る）。
   */
  function sttUrlField(field, testProvider) {
    field.testProvider = testProvider;
    return field;
  }

  function modelFields(prefix, label) {
    const modelHelp = prefix === 'doubao' ? '現在のAPI Keyに権限があるモデルID、またはArk推論接続点IDを指定してください。' : '';
    const fields = [f(prefix+'Model','モデル1',label,'text',null,modelHelp), f(prefix+'Model2','モデル2',label,'text')];
    if (prefix === 'qwen') {
      fields.push(f('qwenModel3','モデル3（画像一次判定用）',label,'text',null,'Qwen3-VL-Flash など、一次判定に使用する視覚モデルを指定します。'));
      fields.push(f('qwenModel4','モデル4（画像二次判定用）',label,'text',null,'Qwen3-VL-Plus など、二次判定に使用する視覚モデルを指定します。'));
      fields.push(f('qwenModel5','モデル5（OCR用）',label,'text',null,'qwen3.5-ocr など、画像OCRに使用するモデルを指定します。'));
    }
    fields.push(f(prefix+'ApiKey','API Key',label,'password'), f(prefix+'Url','URL',label,'text'));
    const urlField = fields[fields.length - 1];
    if (prefix === 'qwen') urlField.help = 'ベースURL（/compatible-mode/v1）と完全な /chat/completions URL のどちらでも使用できます。';
    urlField.testProvider = prefix === 'chatgpt' ? 'openai' : prefix;
    return fields;
  }
  function byId(id) { return document.getElementById(id); }
  function escapeHtml(value) { return String(value == null ? '' : value).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#039;'); }
  /**
   * ボタンの中身を入れ替える。アイコンは必ず SVG スプライトへ変換してから入れる
   * （画面には FontAwesome を読み込んでいないので、`<i class="fas …">` をそのまま入れると
   * アイコンが消えて行の高さも変わる。描画時と押した後で見た目を揃えるための共通処理）。
   */
  function setButtonContent(button, html) {
    if (!button) return;
    button.innerHTML = normalizeIcons(html);
  }

  function normalizeIcons(html) {
    const iconMap = {
      'fa-home':'home', 'fa-search':'search', 'fa-plus':'plus', 'fa-times':'x', 'fa-check':'check',
      'fa-sync-alt':'rotate', 'fa-save':'check', 'fa-brain':'sliders', 'fa-language':'globe',
      'fa-volume-up':'play', 'fa-tasks':'check-square', 'fa-book':'book', 'fa-layer-group':'grid',
      'fa-link':'bookmark', 'fa-sliders-h':'sliders', 'fa-robot':'sliders', 'fa-image':'image',
      'fa-file-signature':'edit', 'fa-file-image':'image', 'fa-check-double':'check-circle',
      'fa-puzzle-piece':'grid', 'fa-book-open':'book-open', 'fa-compass':'globe',
      'fa-book-reader':'book-open', 'fa-question-circle':'info', 'fa-video':'video',
      'fa-clipboard-list':'clipboard', 'fa-font':'type', 'fa-info-circle':'info',
      'fa-arrow-right':'chevron-right', 'fa-exchange-alt':'rotate', 'fa-archive':'folder',
      'fa-project-diagram':'grid', 'fa-align-left':'menu', 'fa-comment-dots':'edit',
      'fa-plug':'sliders', 'fa-circle-notch':'rotate', 'fa-exclamation-triangle':'alert'
    };
    return html.replace(/<i class="fas ([^"]+)"><\/i>/g, function(_, classes) {
      const faClass = String(classes).split(/\s+/).find(function(name) { return name.indexOf('fa-') === 0 && name !== 'fa-spin'; });
      const icon = iconMap[faClass] || 'circle';
      return '<svg class="icon" aria-hidden="true"><use href="#i-' + icon + '"></use></svg>';
    });
  }

  function postJson(url, payload) {
    return fetch(url, { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(payload || {}) })
      .then(function(response) {
        if (response.ok) return response.json();
        return response.json().catch(function() { return null; }).then(function(errorResult) {
          throw new Error(errorResult && errorResult.message ? errorResult.message : 'HTTP ' + response.status);
        });
      })
      .then(function(result) { if (!result || result.success !== true) throw new Error(result && result.message ? result.message : '処理に失敗しました。'); return result.data || {}; });
  }

  /** ナビとパネルに出すカテゴリ（hidden は出さない。external はナビに出して中身は別コンポーネント）。 */
  function visibleCategories() {
    return CATEGORIES.filter(function(category) { return !category.hidden; });
  }

  function renderPage() {
    const categories = visibleCategories();
    byId('settingCategoryNav').innerHTML = normalizeIcons(categories.map(function(category, index) {
      // external の分類（AI生図 / AI授業記録）は項目をこのランタイムが持たないので件数は出さない
      const count = category.external ? '' : '<small>' + category.fields.length + '</small>';
      return '<button type="button" class="setting-category-btn' + (index === 0 ? ' active' : '') + '" data-category="' + category.id + '"><i class="fas ' + category.icon + '"></i><span>' + escapeHtml(category.label) + '</span>' + count + '</button>';
    }).join(''));
    byId('settingPanels').innerHTML = normalizeIcons(categories.map(function(category, index) {
      return '<article class="setting-panel" data-panel="' + category.id + '"' + (index ? ' hidden' : '') + '><header class="setting-panel-head"><h3>' + escapeHtml(category.label) + '</h3><p>' + escapeHtml(category.description) + '</p></header>' + renderCategoryBody(category) + '</article>';
    }).join(''));
    applyValues(currentSettings);
  }

  function renderCategoryBody(category) {
    /*
     * external: true のカテゴリ（AI生図 / AI授業記録）は**中身をここで描かない**。
     * 空のマウント点だけを出し、項目は対応する Vue コンポーネント
     * （views/admin/system-settings/*SettingsSection.vue）が Teleport で入れる。
     * カタログにはバッチ専用・保存できないキーも含まれるので、ここから描くと誤解を招く
     * （保存すると 400 になる項目が出てしまう）。
     */
    if (category.external) {
      return '<div class="setting-panel__external" data-external-slot="' + escapeHtml(category.id) + '"></div>';
    }
    if (Array.isArray(category.sections) && category.sections.length) {
      return renderSectionedCategory(category);
    }
    const groups = [];
    category.fields.forEach(function(field) { if (!groups.includes(field.group)) groups.push(field.group); });
    return groups.map(function(group) {
      return renderGroups(category.fields.filter(function(field) { return field.group === group; }));
    }).join('');
  }

  function renderTabbedCategoryBody(category) {
    let html = '';
    if (category.tabNotice) {
      html += '<div class="setting-tab-notice"><i class="fas fa-info-circle"></i> ' + escapeHtml(category.tabNotice) + '</div>';
    }
    html += '<div class="setting-method-tabs setting-method-tabs-wtb">' +
      '<div class="setting-tabs-buttons">' + category.tabs.map(function(tab, tabIndex) {
      return '<button type="button" class="' + (tabIndex === 0 ? 'active' : '') + '" data-method-tab="' + escapeHtml(tab) + '">' + escapeHtml(tab) + '</button>';
      }).join('') + '</div>' +
      (category.ioNotice
        ? '<span class="setting-io-badge"><i class="fas ' + escapeHtml(category.ioNotice.icon || 'fa-image') + '"></i> ' + escapeHtml(category.ioNotice.input)
            + ' <i class="fas fa-arrow-right"></i> ' + escapeHtml(category.ioNotice.output) + '</span>'
        : '') +
      '</div>';
    html += category.tabs.map(function(tab, tabIndex) {
      const fields = category.fields.filter(function(field) { return (field.tab || '基本設定') === tab; });
      const panelHtml = '<div class="setting-field-grid">' + fields.map(renderField).join('') + '</div>';
      return '<section class="setting-method-tab-panel" data-method-panel="' + escapeHtml(tab) + '"' + (tabIndex === 0 ? '' : ' hidden') + '>' +
        panelHtml + '</section>';
    }).join('');
    return html;
  }

  function renderSectionedCategory(category) {
    return (category.sections || []).map(function(section) {
      const sectionFields = category.fields.filter(function(field) { return section.fieldKeys.includes(field.key); });
      const sectionCategory = { tabs: section.tabs, ioNotice: section.ioNotice, fields: sectionFields };
      return '<section class="setting-batch-section" data-setting-subsection="' + escapeHtml(section.id) + '">' +
        '<header class="setting-batch-section-head"><div class="setting-batch-section-icon"><i class="fas ' + escapeHtml(section.icon) + '"></i></div>' +
        '<div><h4>' + escapeHtml(section.title) + '</h4><p>' + escapeHtml(section.description) + '</p></div></header>' +
        renderTabbedCategoryBody(sectionCategory) + '</section>';
    }).join('');
  }

  function renderGroups(fields) {
    const groups = [];
    fields.forEach(function(field) { if (!groups.includes(field.group)) groups.push(field.group); });
    return groups.map(function(group) {
      const groupFields = fields.filter(function(field) { return field.group === group; });
      return '<section class="setting-group"><h4 class="setting-group-title"><i class="fas fa-sliders-h"></i>' + escapeHtml(group) + '</h4><div class="setting-field-grid">' + groupFields.map(renderField).join('') + '</div></section>';
    }).join('');
  }

  function rangeNumericValue(field, value) {
    const parsed = Number.parseFloat(String(value == null ? '' : value));
    const fallback = Number.isFinite(Number(field.min)) ? Number(field.min) : 0;
    const number = Number.isFinite(parsed) ? parsed : fallback;
    return Math.min(Number(field.max), Math.max(Number(field.min), number));
  }

  function rangeDisplayValue(field, value) {
    return String(value) + (field.suffix || '');
  }

  function updateRangeControl(input) {
    if (!input || input.type !== 'range') return;
    const min = Number(input.min || 0);
    const max = Number(input.max || 100);
    const value = Number(input.value || min);
    const progress = max > min ? ((value - min) / (max - min)) * 100 : 0;
    input.style.setProperty('--setting-range-progress', progress + '%');
    const output = byId(input.id + '_value');
    if (output) output.textContent = String(input.value) + (input.dataset.suffix || '');
  }

  /**
   * ラジオ／チェックボックスは「かたまり」で 1 項目なので、見出しの結び付け方がふつうの入力と違う。
   *
   * かたまりを包む div を `<label for>` で指すと Chrome の Issues
   * 「Incorrect use of <label for=FORM_ELEMENT>（label の for が入力要素を指していない）」になる。
   * 見出しは `<span class="setting-label" id="setting_<key>_label">` にして、
   * かたまり側を `role="radiogroup" / "group"` ＋ `aria-labelledby` で見出しと結び付ける。
   */
  function groupCaptionId(field) {
    return 'setting_' + field.key + '_label';
  }

  /** かたまり（ラジオ／チェックボックス）の項目か。 */
  function isGroupField(field) {
    return field.type === 'select' || field.type === 'ai-model-select' || field.type === 'multi';
  }

  function renderField(field) {
    const value = currentSettings[field.key] == null ? '' : currentSettings[field.key];
    let control;
    if (field.type === 'select' || field.type === 'ai-model-select') {
      const options = field.type === 'ai-model-select' ? aiModelOptions(field.includeBigModel) : field.options.map(function(option) { return typeof option === 'object' ? option : { value: option, label: option }; });
      control = '<div class="setting-radio-group' + (field.disabled ? ' is-disabled' : '') + '" id="setting_' + field.key + '" role="radiogroup" aria-labelledby="' + groupCaptionId(field) + '">' + options.map(function(option) {
        return '<label class="setting-radio-option"><input type="radio" name="setting_' + field.key + '" value="' + escapeHtml(option.value) + '"' + (field.disabled ? ' disabled' : '') + '> <span>' + escapeHtml(option.label) + '</span></label>';
      }).join('') + '</div>';
    } else if (field.type === 'dropdown' || field.type === 'ai-model-dropdown') {
      const options = field.type === 'ai-model-dropdown' ? aiModelOptions(field.includeBigModel) : field.options.map(function(option) { return typeof option === 'object' ? option : { value: option, label: option }; });
      control = '<select class="setting-control setting-dropdown" id="setting_' + field.key + '"' + (field.disabled ? ' disabled' : '') + '>' + options.map(function(option) {
        return '<option value="' + escapeHtml(option.value) + '">' + escapeHtml(option.label) + '</option>';
      }).join('') + '</select>';
    } else if (field.type === 'range') {
      const rangeValue = rangeNumericValue(field, value);
      control = '<div class="setting-range-row"><input class="setting-range" id="setting_' + field.key + '" type="range" min="' + field.min + '" max="' + field.max + '" step="' + (field.step || 1) + '" value="' + rangeValue + '" data-suffix="' + escapeHtml(field.suffix || '') + '" data-storage-suffix="' + escapeHtml(field.storageSuffix || '') + '"' + (field.disabled ? ' disabled' : '') + '><output class="setting-range-value" id="setting_' + field.key + '_value" for="setting_' + field.key + '">' + escapeHtml(rangeDisplayValue(field, rangeValue)) + '</output></div>';
    } else if (field.type === 'toggle') {
      const checked = String(value) === String(field.trueValue || 'true');
      return '<div class="setting-field setting-toggle-field' + (field.full ? ' full' : '') + (field.disabled ? ' is-disabled' : '') + '"><div class="setting-toggle-copy"><strong>' + escapeHtml(field.label) + '</strong>' + (field.help ? '<p>' + escapeHtml(field.help) + '</p>' : '') + '</div><label class="setting-switch"><input id="setting_' + field.key + '" type="checkbox"' + (checked ? ' checked' : '') + (field.disabled ? ' disabled' : '') + ' aria-label="' + escapeHtml(field.label) + '"><span aria-hidden="true"></span></label></div>';
    } else if (field.type === 'textarea') {
      control = '<textarea class="setting-control" id="setting_' + field.key + '" rows="8"' + (field.disabled ? ' disabled' : '') + '></textarea>';
    } else if (field.type === 'multi') {
      const selected = String(value).split(',').map(function(item) { return item.trim(); });
      control = '<div class="setting-check-group" id="setting_' + field.key + '" role="group" aria-labelledby="' + groupCaptionId(field) + '">' + field.options.map(function(option) { return '<label class="setting-check-option"><input type="checkbox" name="setting_' + field.key + '" value="' + escapeHtml(option) + '"' + (selected.includes(option) ? ' checked' : '') + '> ' + escapeHtml(option) + '</label>'; }).join('') + '</div>';
    } else {
      const attrs = field.type === 'number' ? ' type="number" min="' + field.min + '" max="' + field.max + '"' : ' type="' + (field.type === 'password' ? 'password' : field.type === 'time' ? 'time' : 'text') + '" autocomplete="off"';
      control = '<input class="setting-control" id="setting_' + field.key + '"' + attrs + (field.disabled ? ' disabled' : '') + '>';
    }
    const testControl = field.testProvider ? '<div class="setting-api-test-row"><button type="button" class="setting-api-test" data-provider="' + escapeHtml(field.testProvider) + '"><i class="fas fa-plug"></i> 接続テスト</button><span class="setting-api-test-status" id="setting_test_status_' + escapeHtml(field.testProvider) + '" aria-live="polite"></span></div>' : '';
    const caption = '<span>' + escapeHtml(field.label) + '</span><small class="setting-field-key">' + escapeHtml(field.key) + '</small>';
    // かたまり（ラジオ／チェックボックス）は for を持てない（持つと DevTools の違反になる）ので span にする
    const captionHtml = isGroupField(field)
      ? '<span class="setting-label" id="' + groupCaptionId(field) + '">' + caption + '</span>'
      : '<label class="setting-label" for="setting_' + field.key + '">' + caption + '</label>';
    return '<div class="setting-field' + (field.type === 'textarea' || field.full ? ' full' : '') + (field.type === 'range' ? ' setting-range-field' : '') + (field.disabled ? ' is-disabled' : '') + '">' + captionHtml + control + testControl + (field.help ? '<p class="setting-help">' + escapeHtml(field.help) + '</p>' : '') + '</div>';
  }

  function applyValues(settings) {
    CATEGORIES.forEach(function(category) { category.fields.forEach(function(field) {
      const element = byId('setting_' + field.key);
      if (!element) return;
      const value = settings[field.key] == null ? '' : String(settings[field.key]);
      if (field.type === 'multi') {
        const selected = value.split(',').map(function(item) { return item.trim(); });
        element.querySelectorAll('input').forEach(function(input) { input.checked = selected.includes(input.value); });
      } else if (field.type === 'select' || field.type === 'ai-model-select') {
        element.querySelectorAll('input[type="radio"]').forEach(function(input) { input.checked = input.value === value; });
      } else if (field.type === 'dropdown' || field.type === 'ai-model-dropdown') {
        element.value = value;
      } else if (field.type === 'range') {
        element.value = String(rangeNumericValue(field, value));
        updateRangeControl(element);
      } else if (field.type === 'toggle') {
        element.checked = value === String(field.trueValue || 'true');
      } else element.value = value;
    }); });
    updateOcrPromptStates();
    fieldSections.forEach(function(section) {
      if (typeof section.applyValues === 'function') section.applyValues(settings);
    });
  }

  function updateOcrPromptStates() {
    [
      { provider:'essayOcrAiProvider', prompts:['essayOcrPrompt','essayOcrUserPrompt'] },
      { provider:'intensiveOcrAiProvider', prompts:['intensiveOcrSystemPrompt','intensiveOcrUserPrompt'] },
      { provider:'clozeOcrAiProvider', prompts:['clozeOcrSystemPrompt','clozeOcrUserPrompt'] }
    ].forEach(function(config) {
      const providerGroup = byId('setting_' + config.provider);
      if (!providerGroup) return;
      const selected = providerGroup.querySelector('input[type="radio"]:checked');
      const isBigModel = selected && selected.value.indexOf('bigmodel:') === 0;
      config.prompts.forEach(function(key) {
        const control = byId('setting_' + key);
        if (!control) return;
        control.disabled = Boolean(isBigModel);
        control.setAttribute('aria-disabled', String(Boolean(isBigModel)));
        const field = control.closest('.setting-field');
        if (field) field.classList.toggle('is-disabled', Boolean(isBigModel));
      });
    });
    CATEGORIES.forEach(function(category) { category.fields.forEach(function(field) {
      if (!field.disabled) return;
      const control = byId('setting_' + field.key);
      if (!control) return;
      control.disabled = true;
      control.setAttribute('aria-disabled', 'true');
      const wrapper = control.closest('.setting-field');
      if (wrapper) wrapper.classList.add('is-disabled');
    }); });
  }

  function collectValues() {
    const result = {};
    CATEGORIES.forEach(function(category) { category.fields.forEach(function(field) {
      const element = byId('setting_' + field.key);
      // hidden なカテゴリの項目は別コンポーネントが持つので、ここでは触らない（下の fieldSections）
      if (!element) return;
      if (field.type === 'multi') result[field.key] = Array.from(element.querySelectorAll('input:checked')).map(function(input) { return input.value; }).join(',');
      else if (field.type === 'select' || field.type === 'ai-model-select') { const checked = element.querySelector('input[type="radio"]:checked'); result[field.key] = checked ? checked.value : ''; }
      else if (field.type === 'toggle') result[field.key] = element.checked ? String(field.trueValue || 'true') : String(field.falseValue || 'false');
      else if (field.type === 'range') result[field.key] = element.value + (field.storageSuffix || '');
      else result[field.key] = element.value;
    }); });
    fieldSections.forEach(function(section) {
      const values = section.getValues() || {};
      Object.keys(values).forEach(function(key) { result[key] = values[key] == null ? '' : String(values[key]); });
    });
    return result;
  }

  function activateCategory(categoryId) {
    document.querySelectorAll('.setting-category-btn').forEach(function(button) { button.classList.toggle('active', button.dataset.category === categoryId); });
    document.querySelectorAll('.setting-panel').forEach(function(panel) { panel.hidden = panel.dataset.panel !== categoryId; });
    window.location.hash = categoryId;
  }

  function loadSettings() {
    byId('settingLoading').hidden = false;
    byId('settingPanels').hidden = true;
    postJson('/api/admin/setting/initSettings', { userId:'setting.jsp' }).then(function(data) {
      currentSettings = data.settings || {};
      renderPage();
      byId('settingLoading').hidden = true;
      byId('settingPanels').hidden = false;
      const requested = window.location.hash.replace('#','');
      if (visibleCategories().some(function(item) { return item.id === requested; })) activateCategory(requested);
    }).catch(function(_error) { currentSettings = {}; renderPage(); byId('settingLoading').hidden = true; byId('settingPanels').hidden = false; showToast('設定APIに接続できないため、項目定義を表示しています。'); });
  }

  function saveSettings() {
    const button = byId('settingSaveBtn');
    button.disabled = true;
    setButtonContent(button, '<i class="fas fa-circle-notch fa-spin"></i> 保存中');
    postJson('/api/admin/setting/saveSettings', { userId:'setting.jsp', settings:collectValues() }).then(function(data) {
      currentSettings = data.settings || collectValues();
      applyValues(currentSettings);
      showToast(data.message || '設定を保存しました。');
    }).catch(function(error) { showToast(error.message || '設定の保存に失敗しました。'); }).finally(function() { button.disabled=false; setButtonContent(button, '<i class="fas fa-save"></i> 設定を保存'); });
  }

  /**
   * 【接続テスト】サーバー（POST /api/admin/setting/testAi）へ保存前の入力値を送る。
   * 応答の `ok=false` は「接続はしたが AI に断られた」なので、その理由を赤で出す。
   * 対象は OpenAI 互換のチャット API を使う 4 プロバイダー（BigModel / 智譜 OCR はボタンを出さない）。
   */
  function testAiConnection(button) {
    const provider = button.dataset.provider;
    // STT（音声認識）は音声を 1 回送る別のテスト（POST /api/admin/setting/testStt）へ送る。
    // キーの接頭辞も Chat の 4 プロバイダーとは違う（googleStt* / alibabaStt*）
    const sttProvider = provider === 'google-stt' ? 'google' : (provider === 'alibaba-stt' ? 'alibaba' : null);
    const prefix = provider === 'openai' ? 'chatgpt' : (sttProvider ? sttProvider + 'Stt' : provider);
    const values = collectValues();
    const status = byId('setting_test_status_' + provider);
    button.disabled = true;
    setButtonContent(button, '<i class="fas fa-circle-notch fa-spin"></i> テスト中');
    if (status) { status.textContent = ''; status.className = 'setting-api-test-status'; }
    postJson(sttProvider ? '/api/admin/setting/testStt' : '/api/admin/setting/testAi',
      { provider: sttProvider || provider, model: values[prefix + 'Model'], apiKey: values[prefix + 'ApiKey'], url: values[prefix + 'Url'] })
      .then(function(data) {
        const message = data.message || (data.ok === false ? '接続できませんでした。' : '接続成功');
        if (data.ok === false) {
          // 接続はしたが AI に断られた（API Key・モデル名・URL の指定違い）
          if (status) { status.textContent = message; status.classList.add('is-error'); }
          showToast(message);
          return;
        }
        if (status) { status.textContent = message; status.classList.add('is-success'); }
        showToast(message + '：' + (data.model || values[prefix + 'Model']));
      })
      .catch(function(error) {
        if (status) { status.textContent = error.message || '接続失敗'; status.classList.add('is-error'); }
        showToast(error.message || 'APIテストに失敗しました。');
      })
      .finally(function() { button.disabled = false; setButtonContent(button, '<i class="fas fa-plug"></i> 接続テスト'); });
  }

  function showToast(message) { const toast=byId('settingToast'); window.clearTimeout(toastTimer); toast.textContent=message; toast.classList.add('show'); toastTimer=window.setTimeout(function(){toast.classList.remove('show');},2800); }
  function bindEvents() {
    byId('settingCategoryNav').addEventListener('click', function(event) { const button=event.target.closest('[data-category]'); if (button) activateCategory(button.dataset.category); });
    byId('settingPanels').addEventListener('click', function(event) {
      const button = event.target.closest('[data-method-tab]');
      if (!button) return;
      const tabName = button.dataset.methodTab;
      const scope = button.closest('[data-setting-subsection]') || button.closest('.setting-panel');
      if (!scope) return;
      scope.querySelectorAll('[data-method-tab]').forEach(function(item) { item.classList.toggle('active', item === button); });
      scope.querySelectorAll('[data-method-panel]').forEach(function(item) { item.hidden = item.dataset.methodPanel !== tabName; });
    });
    byId('settingPanels').addEventListener('change', function(event) {
      if (event.target && ['setting_essayOcrAiProvider','setting_intensiveOcrAiProvider'].includes(event.target.name)) updateOcrPromptStates();
    });
    byId('settingPanels').addEventListener('input', function(event) {
      if (event.target && event.target.type === 'range') updateRangeControl(event.target);
    });
    byId('settingPanels').addEventListener('click', function(event) {
      const button = event.target.closest('.setting-api-test');
      if (button) testAiConnection(button);
    });
    byId('settingReloadBtn').addEventListener('click', loadSettings);
    byId('settingSaveBtn').addEventListener('click', saveSettings);
  }
  function bootstrap() { bindEvents(); loadSettings(); }
  window.__study21SystemSettings = { mount: bootstrap, registerSection: registerSection, collectValues: collectValues };
})();

export {};
