-- ============================================================================
-- Study 2.1  設定値 更新
-- 内容: 日本語単語 AI（batC42 / batC43 / batC44）の System Prompt を
--       「4 択」から「選択肢のプール（正解 1 ＋ 誤答 4〜6 ＝ 合計 5〜7）」に変える
-- ----------------------------------------------------------------------------
-- 背景（設計: database/日本語勉強/日本語勉強_再設計案_中文.md 第 2 章「选项池」）:
--   選択肢は「4 択の答え」ではなく**プール**にする。AI には 1 問につき
--     ・正解 1 件
--     ・誤答 4〜6 件（合計 5〜7 件）
--     ・値はすべて異なる（同じ value で reading だけ違うものも重複とみなす）
--   を出させ、**テストを作るとき**に「正解 1 ＋ 誤答から 3」を選んで並べ替えて
--   4 択にする（JPN_テスト出題情報.出題選択肢JSON に固定する）。
--   プールが足りない応答（4 件・8 件・誤答 3 件・正解が 1 件でない）は整題不合格にし、
--   1 行も書かない（JapaneseWordAiDtoMapper が判定する）。
--
-- 出典: database/設定/TBL_COM_設定情報_init.sql の同じ 3 行（C42 / C43 / C44）
-- 冪等: 設定キーで 1 行を置き換えるだけ（再実行しても同じ結果）
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

BEGIN;

-- batC42（C1 読み・C2 漢字）
UPDATE public."COM_設定情報"
SET "設定値" = '日本語の読み・漢字認識問題を作成してください。出力する JSON の形は、このあとに付く「出力形式（JSON Schema）」が唯一の定義です（スキーマに無いキーを足さない・Markdown 禁止）。
C1（C1_READING）: 漢字を見せて、**仮名**から読みを選ばせる。questionJapanese は「漢字を見て正しい読みを選んでください」。correctValue は入力の reading をそのまま使う。**入力の reading が空のときは、targetReading に正しい読みを書き、選択肢にもその読みを入れる**（読みを持たずに登録された語は、その読みが正解になる）。音声は不要（audioText は空）。
C2（C2_KANJI）: 読みの音声を聞かせて、**漢字表記**から選ばせる。questionJapanese は「音声を聞いて正しい漢字表記を選んでください」。correctValue は入力の heading をそのまま使う。audioText に読みを入れる（音声合成に使う）。
どちらも守ること:
・options は**正解 1 件 ＋ 誤答 4〜6 件（合計 5〜7 件）**（選択肢のプール。学習画面にはこのうち 4 件だけを出す）。value はすべて異なる（同じ value で reading だけ違うものも重複とみなす）。
・correctValue と一致する value は必ず 1 件だけ。
・誤答は「読み間違い・似た漢字」など、学習者が実際に迷うものにする（でたらめな語は作らない）。
・wrongType は誤答の理由（READING_SIMILAR / KANJI_SIMILAR など）。
・出力前に各自の問題の value を照合し、重複があれば直してから出力する。'
WHERE "ページ区分" = 'JAPANESE_WORD_AI'
  AND "設定キー" = 'BAT_C42_SYSTEM_PROMPT'
  AND "スコープ" = 'GLOBAL';

-- batC43（D 文脈の意味）
UPDATE public."COM_設定情報"
SET "設定値" = '文脈から日本語単語の意味を判断する四択問題を作成してください。出力する JSON の形は、このあとに付く「出力形式（JSON Schema）」が唯一の定義です（スキーマに無いキーを足さない・Markdown 禁止）。選択肢と解説は簡体中文にしてください。
・sentenceJapanese は、その語が**その意味で**使われている自然な例文（学習画面の例文と同じ調子）。sentenceReading に読み、audioText に例文そのもの（音声合成に使う）。
・correctValue は「その文脈での正しい中国語の意味」。options の value も中国語。
・options は**正解 1 件 ＋ 誤答 4〜6 件（合計 5〜7 件）**（選択肢のプール。学習画面にはこのうち 4 件だけを出す）。value はすべて異なり、correctValue と一致する value は 1 件だけ。
・誤答は「別の意味（MEANING_SIMILAR）」「文脈に合わない（CONTEXT_MISMATCH）」など、学習者が実際に迷うものにする。
・explanationChinese は、なぜ誤答が違うのかまで学習者が理解できる簡体中文で書く（正解の理由だけで終わらせない）。
・difficulty は EASY / NORMAL / HARD。'
WHERE "ページ区分" = 'JAPANESE_WORD_AI'
  AND "設定キー" = 'BAT_C43_SYSTEM_PROMPT'
  AND "スコープ" = 'GLOBAL';

-- batC44（E 漢字の使い分け）
UPDATE public."COM_設定情報"
SET "設定値" = '同じ読みを持つ漢字表記の使い分けを問う四択問題を作成してください。出力する JSON の形は、このあとに付く「出力形式（JSON Schema）」が唯一の定義です（スキーマに無いキーを足さない・Markdown 禁止）。解説は簡体中文です。
・sentenceJapanese は、正解の部分を（　）にした自然な例文（全角の空欄）。sentenceReading に読み、audioText に読みを入れる。
・correctValue は（　）に入る正しい漢字表記。targetHeading も同じ表記にする。
・options は**正解 1 件 ＋ 誤答 4〜6 件（合計 5〜7 件）**（選択肢のプール。学習画面にはこのうち 4 件だけを出す）。value は**同じ読みを持つ実在の漢字表記**にし、correctValue と一致する value は 1 件だけ。
・実在しない不自然な漢字表記は作らない（学習者が誤って覚えるため）。
・explanationChinese は、それぞれの漢字が**どう使い分けられるか**を簡体中文で説明する（正解の意味だけで終わらせない）。
・difficulty は EASY / NORMAL / HARD。'
WHERE "ページ区分" = 'JAPANESE_WORD_AI'
  AND "設定キー" = 'BAT_C44_SYSTEM_PROMPT'
  AND "スコープ" = 'GLOBAL';

-- 反映確認（プールの記述が入り、4 択の断定が消えたか）
SELECT "設定キー",
       position('誤答 4〜6' IN "設定値") > 0          AS has_pool_rule,
       position('options は必ず 4 件' IN "設定値") > 0 AS has_old_four_rule,
       length("設定値")                                AS chars
  FROM public."COM_設定情報"
 WHERE "ページ区分" = 'JAPANESE_WORD_AI'
   AND "設定キー" IN ('BAT_C42_SYSTEM_PROMPT', 'BAT_C43_SYSTEM_PROMPT', 'BAT_C44_SYSTEM_PROMPT')
 ORDER BY "設定キー";

COMMIT;
