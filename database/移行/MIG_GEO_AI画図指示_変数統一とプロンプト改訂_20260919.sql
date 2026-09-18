-- ============================================================================
-- Study 2.1  移行: AI 生図のプロンプト変数の統一と、旧版 System Prompt の改訂
-- ----------------------------------------------------------------------------
-- 追加・変更するもの:
--   1. COM_設定情報 … 旧版の共通 System Prompt（GEOMETRY_AI_SYSTEM_PROMPT）を、
--      **JSON 出力・利用者指定の座標範囲**に合う文面へ差し替える
--      （「コマンド列だけを出力」「1 行に 1 コマンド」「座標は -10..10」を削除する）
--   2. COM_設定情報 … モード A〜D のタスクテンプレート（User Prompt）の**変数名を統一**する
--      （コードが登録している名前だけを使う。{output_schema} は {outputSchema} にする）
--   3. COM_設定項目 … 上のキーの説明文と必須フラグを実際の運用に合わせる
--      （共通の User Prompt は**空でよい**＝必須にしない）
--
-- 背景（利用者の指示）:
--   画面の設定には A〜D の**日本語のプロンプトが既に入っている**（利用者が書いた業務要件）。
--   ところがそのテンプレートは {requested_output_type} / {user_note} / {allowed_commands} のような
--   **コードが知らない変数**を使っており、実行時に「知らない変数があります」で失敗していた
--   （さらに {output_schema} はサーバーが自動で足す Schema と二重になっていた）。
--   ここでは**業務要件の文面はそのまま残し、変数名だけをコードの名前に合わせる**。
--
-- 変数の対応（旧 → 新）:
--   {requested_output_type}            → {resultType}（名前は {resultTypeLabel}）
--   {user_note}                        → {note}
--   {allowed_commands}                 → {allowedCommands}
--   {output_schema}                    → {outputSchema}（DTO から自動生成した Schema が入る）
--   {reproduction_priority}            → {reproduceFocus}
--   {preserve_labels}                  → {keepLabels}
--   {insufficient_information_policy}  → {whenInsufficient}
--   {known_information}                → {knownValues}
--   {view_range}                       → {viewRange}（A は {coordinateRange}）
--   {formula_override}                 → {formulaCorrection}
--   {parameter_values}                 → {parameters}
--   {auxiliary_objects}                → {auxiliaryObjects}
--   {construction_goal}                → {goal}
--   {text_correction}                  → {problemCorrection}（D は {textCorrection}）
--   {preserve_content}                 → {keepObjects}
--   {requested_changes}                → {changeObjects}
--   {corrections}                      → {textCorrection}
--   {focus}                            → 削除（当てはまる補充項目が無い。{note} に統合する）
--
-- 冪等性（何度流しても同じ）:
--   差し替えは「**まだ旧い目印が残っている行だけ**」を対象にする。
--   一度流したあとは目印が消えるので何も起きないし、利用者があとから文面を書き換えても
--   この移行がそれを上書きすることはない（＝実行済みの環境で再実行しても安全）。
--
-- 対象DB: study21 (PostgreSQL)
-- 実行順序: database/設定/TBL_COM_設定項目_init.sql、
--           database/設定/TBL_COM_設定情報_init.sql、
--           database/移行/MIG_GEO_AI作図モードと結果種別_20260918.sql の後
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. 共通の System Prompt（旧版の削除）
--    「コマンド列だけを出力」「1 行に 1 コマンド」「座標は -10..10」は、
--    いまの JSON 出力と「利用者が座標の範囲を指定できる」という約束と衝突する。
--    禁止事項・許可リスト・ASCII の名前・推測しない、という業務要件はそのまま残す。
-- ----------------------------------------------------------------------------
UPDATE public."COM_設定情報"
   SET "設定値" = $prompt$あなたは日本の学習塾の数学教材を作る作図アシスタントです。
与えられた画像から読み取れる図形・関数・数式・文字を使って、GeoGebra のコマンドを組み立てます。

必ず守ること:
1. 返答の形式は、このプロンプトの末尾に自動で付く「出力形式（JSON Schema）」に従う（手書きの JSON 例は持たない）。
2. JSON の「コマンド」は 1 要素 1 コマンドにする。行頭が # のコメント行は入れない。
3. 使えるコマンドは許可リストにあるものだけにする。
4. 禁止: Delete / File / Import / Export / Execute / SetValue / Button / Checkbox / InputBox / SetActiveView / RunClickScript、スクリプト（GGBScript / JavaScript）、外部 URL の読み込み。
5. オブジェクト名は ASCII（A〜Z、a〜z、c1、f など）にする。日本語のラベルは Text で作る。
6. 座標の範囲は、利用者が指定した「座標の範囲・目盛」「表示範囲」に従う。指定が無いときは、図の主要な部分が見える範囲を自分で選び、選んだ範囲を「表示範囲」の項目に書く。
7. 画像から読み取った数式・文字（例: 三角形ABC、AB=5）は Text として図に置く。
8. 推測で値を埋めない。読めない値は出力しない（作図に必要な情報が足りないときは、判定を NEEDS_INPUT にして確認事項を返す）。
9. 指示された条件（直角・平行・等長・接点など）は、目分量の座標ではなくコマンドで本当に成り立たせる。$prompt$,
       "備考" = 'AI生図：共通の System Prompt（共通＋モード別を連結して使う。座標の範囲は利用者の指定に従う）',
       "更新日時" = CURRENT_TIMESTAMP
 WHERE "ページ区分" = 'GEOMETRY_AI'
   AND "設定キー" = 'GEOMETRY_AI_SYSTEM_PROMPT'
   AND "スコープ" = 'GLOBAL'
   AND ("設定値" LIKE '%コマンドだけを出力%' OR "設定値" LIKE '%座標は -10..10%');

-- ----------------------------------------------------------------------------
-- 2. モード A〜D のタスクテンプレート（変数名の統一。文面は業務要件をそのまま残す）
-- ----------------------------------------------------------------------------
-- ---- モード A（画像をもとに再現）----
UPDATE public."COM_設定情報"
   SET "設定値" = $t$添付画像の数学的な図を再現してください。

作成する図の種類：{resultType}（{resultTypeLabel}）
再現で重視する点：{reproduceFocus}
名前・ラベルの保持：{keepLabels}
情報が不足した場合の扱い：{whenInsufficient}
利用者が補足した公式・座標・寸法・関係：{knownValues}
座標の表示範囲：{coordinateRange}
補足指示：{note}

関数グラフが含まれる場合、公式を確定できるかを判断してください。
近似の場合は、その範囲と根拠を明示してください。

許可コマンド・数式表現：
{allowedCommands}

出力 Schema：
{outputSchema}$t$,
       "更新日時" = CURRENT_TIMESTAMP
 WHERE "ページ区分" = 'GEOMETRY_AI'
   AND "設定キー" = 'GEOMETRY_AI_A_TASK_TEMPLATE'
   AND "スコープ" = 'GLOBAL'
   AND ("設定値" LIKE '%{requested_output_type}%' OR "設定値" LIKE '%{output_schema}%'
        OR "設定値" LIKE '%{user_note}%' OR "設定値" LIKE '%{allowed_commands}%');

-- ---- モード B（数式からグラフを作成）----
UPDATE public."COM_設定情報"
   SET "設定値" = $t$添付画像の数式を読み取り、対応するグラフを作成してください。

結果の種類：GRAPH
利用者による数式の訂正：{formulaCorrection}
パラメータの値：{parameters}
定義域：{domain}
座標の表示範囲：{viewRange}
追加する補助対象：{auxiliaryObjects}
名前・ラベルの保持：{keepLabels}
補足指示：{note}

重要な記号が曖昧な場合、推測で式を決めず、確認事項を返してください。

許可コマンド・数式表現：
{allowedCommands}

出力 Schema：
{outputSchema}$t$,
       "更新日時" = CURRENT_TIMESTAMP
 WHERE "ページ区分" = 'GEOMETRY_AI'
   AND "設定キー" = 'GEOMETRY_AI_B_TASK_TEMPLATE'
   AND "スコープ" = 'GLOBAL'
   AND ("設定値" LIKE '%{formula_override}%' OR "設定値" LIKE '%{parameter_values}%'
        OR "設定値" LIKE '%{view_range}%' OR "設定値" LIKE '%{output_schema}%'
        OR "設定値" LIKE '%{user_note}%' OR "設定値" LIKE '%{allowed_commands}%');

-- ---- モード C（文章の条件から作図）----
UPDATE public."COM_設定情報"
   SET "設定値" = $t$添付画像の文章条件を読み取り、作図してください。

作成する図の種類：{resultType}（{resultTypeLabel}）
作図の目的：{goal}
文章の訂正・補足：{problemCorrection}
条件が不足した場合の扱い：{whenInsufficient}
パラメータの値：{parameters}
定義域：{domain}
座標の表示範囲：{viewRange}
名前・ラベルの保持：{keepLabels}
補足指示：{note}

図形にもグラフにもなり得るため、指定された結果の種類と条件の両方を確認してください。
必要な計算と、追加の解答・証明を区別してください。

許可コマンド・数式表現：
{allowedCommands}

出力 Schema：
{outputSchema}$t$,
       "更新日時" = CURRENT_TIMESTAMP
 WHERE "ページ区分" = 'GEOMETRY_AI'
   AND "設定キー" = 'GEOMETRY_AI_C_TASK_TEMPLATE'
   AND "スコープ" = 'GLOBAL'
   AND ("設定値" LIKE '%{requested_output_type}%' OR "設定値" LIKE '%{construction_goal}%'
        OR "設定値" LIKE '%{text_correction}%' OR "設定値" LIKE '%{output_schema}%'
        OR "設定値" LIKE '%{user_note}%' OR "設定値" LIKE '%{allowed_commands}%');

-- ---- モード D（文章と図を合わせて作図）----
UPDATE public."COM_設定情報"
   SET "設定値" = $t$添付画像の文章・数式・参考図を組み合わせて作図してください。

作成する図の種類：{resultType}（{resultTypeLabel}）
今回の目的：{goal}
原図から保持する内容：{keepObjects}
追加・変更する内容：{changeObjects}
利用者による訂正：{textCorrection}
パラメータの値：{parameters}
定義域：{domain}
座標の表示範囲：{viewRange}
名前・ラベルの保持：{keepLabels}
補足指示：{note}

文章と図中の対象を対応付けてください。
明示条件の矛盾や、作図を左右する情報不足がある場合は、具体的な確認事項を返してください。

許可コマンド・数式表現：
{allowedCommands}

出力 Schema：
{outputSchema}$t$,
       "更新日時" = CURRENT_TIMESTAMP
 WHERE "ページ区分" = 'GEOMETRY_AI'
   AND "設定キー" = 'GEOMETRY_AI_D_TASK_TEMPLATE'
   AND "スコープ" = 'GLOBAL'
   AND ("設定値" LIKE '%{requested_output_type}%' OR "設定値" LIKE '%{preserve_content}%'
        OR "設定値" LIKE '%{requested_changes}%' OR "設定値" LIKE '%{output_schema}%'
        OR "設定値" LIKE '%{user_note}%' OR "設定値" LIKE '%{allowed_commands}%');

-- ----------------------------------------------------------------------------
-- 3. カタログ（COM_設定項目）の説明と必須フラグを実際の運用に合わせる
--    ・共通の User Prompt は**空でよい**（必須フラグ '0'）
--    ・モード別の System Prompt は「共通を継承」ではなく「**共通に追加**」
--    ・テンプレートの説明には、実際に使える変数だけを書く
-- ----------------------------------------------------------------------------
UPDATE public."COM_設定項目"
   SET "必須フラグ" = '0',
       "説明" = 'AI生図：共通の User Prompt（タスクテンプレート）。空でよい（モード別（GEOMETRY_AI_<A〜D>_TASK_TEMPLATE）に書けばそれを使い、どちらも無ければテンプレート無しで実行する）。変数: {mode} {modeLabel} {resultType} {resultTypeLabel} {note} {supplements} {keepLabels} {maxCommands} {allowedCommands} {outputFormat} {outputSchema}'
 WHERE "ページ区分" = 'GEOMETRY_AI'
   AND "設定キー" = 'GEOMETRY_AI_INSTRUCTION_TEMPLATE';

UPDATE public."COM_設定項目"
   SET "説明" = 'AI生図：共通の System Prompt（必須）。モード別の System Prompt があれば、その**前に連結**して使う（モード側で上書きしない）。共通＋モード別＋DTO から自動生成した出力 Schema の順に 1 つのプロンプトになる。変数: {allowedCommands} {maxCommands} {outputSchema} など'
 WHERE "ページ区分" = 'GEOMETRY_AI'
   AND "設定キー" = 'GEOMETRY_AI_SYSTEM_PROMPT';

UPDATE public."COM_設定項目"
   SET "説明" = 'AI生図（モード A 画像をもとに再現）：モード別の System Prompt。共通（GEOMETRY_AI_SYSTEM_PROMPT）に**追加**される（上書きしない）。空なら共通だけを使う'
 WHERE "ページ区分" = 'GEOMETRY_AI'
   AND "設定キー" = 'GEOMETRY_AI_A_SYSTEM_PROMPT';
UPDATE public."COM_設定項目"
   SET "説明" = 'AI生図（モード B 数式からグラフを作成）：モード別の System Prompt。共通（GEOMETRY_AI_SYSTEM_PROMPT）に**追加**される（上書きしない）。空なら共通だけを使う'
 WHERE "ページ区分" = 'GEOMETRY_AI'
   AND "設定キー" = 'GEOMETRY_AI_B_SYSTEM_PROMPT';
UPDATE public."COM_設定項目"
   SET "説明" = 'AI生図（モード C 文章の条件から作図）：モード別の System Prompt。共通（GEOMETRY_AI_SYSTEM_PROMPT）に**追加**される（上書きしない）。空なら共通だけを使う'
 WHERE "ページ区分" = 'GEOMETRY_AI'
   AND "設定キー" = 'GEOMETRY_AI_C_SYSTEM_PROMPT';
UPDATE public."COM_設定項目"
   SET "説明" = 'AI生図（モード D 文章と図を合わせて作図）：モード別の System Prompt。共通（GEOMETRY_AI_SYSTEM_PROMPT）に**追加**される（上書きしない）。空なら共通だけを使う'
 WHERE "ページ区分" = 'GEOMETRY_AI'
   AND "設定キー" = 'GEOMETRY_AI_D_SYSTEM_PROMPT';

UPDATE public."COM_設定項目"
   SET "説明" = 'AI生図（モード A 画像をもとに再現）：モード別のタスクテンプレート（User Prompt）。空なら共通（GEOMETRY_AI_INSTRUCTION_TEMPLATE）を使い、それも空ならテンプレート無しで実行する。変数: {resultType} {resultTypeLabel} {reproduceFocus} {whenInsufficient} {knownValues} {coordinateRange} {keepLabels} {note} {allowedCommands} {outputSchema}'
 WHERE "ページ区分" = 'GEOMETRY_AI'
   AND "設定キー" = 'GEOMETRY_AI_A_TASK_TEMPLATE';
UPDATE public."COM_設定項目"
   SET "説明" = 'AI生図（モード B 数式からグラフを作成）：モード別のタスクテンプレート（User Prompt）。空なら共通（GEOMETRY_AI_INSTRUCTION_TEMPLATE）を使い、それも空ならテンプレート無しで実行する。変数: {resultType} {resultTypeLabel} {formulaCorrection} {parameters} {domain} {viewRange} {auxiliaryObjects} {keepLabels} {note} {allowedCommands} {outputSchema}'
 WHERE "ページ区分" = 'GEOMETRY_AI'
   AND "設定キー" = 'GEOMETRY_AI_B_TASK_TEMPLATE';
UPDATE public."COM_設定項目"
   SET "説明" = 'AI生図（モード C 文章の条件から作図）：モード別のタスクテンプレート（User Prompt）。空なら共通（GEOMETRY_AI_INSTRUCTION_TEMPLATE）を使い、それも空ならテンプレート無しで実行する。変数: {resultType} {resultTypeLabel} {goal} {problemCorrection} {whenInsufficient} {parameters} {domain} {viewRange} {keepLabels} {note} {allowedCommands} {outputSchema}'
 WHERE "ページ区分" = 'GEOMETRY_AI'
   AND "設定キー" = 'GEOMETRY_AI_C_TASK_TEMPLATE';
UPDATE public."COM_設定項目"
   SET "説明" = 'AI生図（モード D 文章と図を合わせて作図）：モード別のタスクテンプレート（User Prompt）。空なら共通（GEOMETRY_AI_INSTRUCTION_TEMPLATE）を使い、それも空ならテンプレート無しで実行する。変数: {resultType} {resultTypeLabel} {goal} {keepObjects} {changeObjects} {textCorrection} {parameters} {domain} {viewRange} {keepLabels} {note} {allowedCommands} {outputSchema}'
 WHERE "ページ区分" = 'GEOMETRY_AI'
   AND "設定キー" = 'GEOMETRY_AI_D_TASK_TEMPLATE';

COMMIT;

-- ----------------------------------------------------------------------------
-- 確認用（実行結果のレポート。移行の一部ではない）
-- ----------------------------------------------------------------------------
SELECT "設定キー",
       CASE WHEN "設定値" IS NULL OR "設定値" = '' THEN '（空）' ELSE length("設定値")::text || ' 文字' END AS "長さ",
       "設定値" LIKE '%{requested_output_type}%' AS "旧変数が残っている",
       "設定値" LIKE '%{output_schema}%'         AS "旧Schema変数が残っている",
       "設定値" LIKE '%{resultType}%'            AS "新しい変数を使っている"
  FROM public."COM_設定情報"
 WHERE "ページ区分" = 'GEOMETRY_AI'
   AND "設定キー" IN ('GEOMETRY_AI_SYSTEM_PROMPT', 'GEOMETRY_AI_INSTRUCTION_TEMPLATE',
                      'GEOMETRY_AI_A_SYSTEM_PROMPT', 'GEOMETRY_AI_A_TASK_TEMPLATE',
                      'GEOMETRY_AI_B_SYSTEM_PROMPT', 'GEOMETRY_AI_B_TASK_TEMPLATE',
                      'GEOMETRY_AI_C_SYSTEM_PROMPT', 'GEOMETRY_AI_C_TASK_TEMPLATE',
                      'GEOMETRY_AI_D_SYSTEM_PROMPT', 'GEOMETRY_AI_D_TASK_TEMPLATE')
 ORDER BY "設定キー";

-- 旧変数（コードが知らない名前）が 1 つも残っていないことを確かめる。
-- 1 行でも返ったら、そのテンプレートを設定画面で直すこと（保存時に検証で止まる）。
SELECT "設定キー", "設定値"
  FROM public."COM_設定情報"
 WHERE "ページ区分" = 'GEOMETRY_AI'
   AND "スコープ" = 'GLOBAL'
   AND "設定値" ~ '\{(requested_output_type|user_note|allowed_commands|output_schema|reproduction_priority|preserve_labels|insufficient_information_policy|known_information|view_range|formula_override|parameter_values|auxiliary_objects|construction_goal|text_correction|preserve_content|requested_changes|corrections|focus)\}';
