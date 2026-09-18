<script setup lang="ts">
import { computed, onBeforeUnmount, nextTick, onMounted, ref } from 'vue'
import { ApiError, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import AiDataSchemaPanel from '@/features/system-settings/AiDataSchemaPanel.vue'
import { loadSettingFields, saveSettingFields } from '@/api/system-settings'
import {
  AI_TAB_USER,
  type AiFieldLike,
  aiTabsOf,
  normalizeAiFields
} from '@/features/system-settings/aiSettingsLayout'
import { modelSlotOptions, withCurrentModelSlot } from '@/features/system-settings/modelSlots'
import '@/features/geometry/geometry-ai.css'

/**
 * システム設定【図形管理】分頁 — AI 生図と AI 画図助手の設定。
 *
 * 画面はこのコンポーネントが描き（設定ランタイムの同カテゴリ `geometry_ai` は external）、
 * 値の読み書きは `/api/admin/setting/initSettings` / `saveSettings` を通す。送るフィールドキーは
 * `SettingPageFields`（サーバー側の対応表）に定義済みの **GEOMETRY_AI の 54 キー**
 * （共通の 26 キー ＋ 作図モード別の 28 キー）。未定義キーを送ると 400 になり、画面の他の設定も
 * まとめて保存できなくなる。
 *
 * 機能は 2 つあり、**通り道が違う**（この分頁のカードはそれを表す）:
 *   1. AI 生図（写真 → 作図）… 画像の取込・前処理は**バックエンドの通常コード**（バッチではない）、
 *      **AI を呼ぶ batC51 だけがバッチ**（作図モードごとに 1 バッチ = batC51-A〜D）、
 *      コマンドの検証・確定も**バックエンドの通常コード**。
 *      （2026-09 の構成変更。以前は batC51/52/53 の 3 バッチだった）
 *   2. AI 画図助手（作図画面の【AI 助手】）… **batC52**（旧 batC52＝AI 生成 の番号を再利用）。
 *      設定は `GEOMETRY_AI_ASSIST_*` の独立したキー。
 *
 * AI 生図の AI 生成は**作図モード 4 つ**（A: 画像をもとに再現 / B: 数式からグラフを作成 /
 * C: 文章の条件から作図 / D: 文章と図を合わせて作図）に分かれていて、モードごとに独立した
 * バッチ・プロンプト・出力 DTO を持つ（サーバーの `FigureMode` と同じ 4 つ）。AI 生成のブロックは
 * **共通規則（4 モード共通）のカード ＋ モードのカード 4 枚**で、モード別の設定キーは
 * `GEOMETRY_AI_<モード>_<項目>`（例: `GEOMETRY_AI_A_TASK_TEMPLATE`）に対応する camelCase。
 * **モード別の値が空のときは共通の設定をそのまま使う**（サーバーの `FigureProcessorSettings` が
 * 継承する）ので、モードごとのプロンプトは空のままでよい。
 *
 * レイアウトは設定ページの他機能（日本語単語AI）に合わせる:
 *   セクションカード（見出し＋説明）＋ 入力→出力バッジ ＋ タブ（基本設定 / System Prompt /
 *   User Prompt / その他）。タブの切替は**設定ランタイムの委譲ハンドラ**をそのまま使う
 *   （`data-setting-subsection` と `[data-method-tab]` / `[data-method-panel]`）。
 *   「使用モデル」は「AIモデル」ページのスロットを選ぶドロップダウンにする。
 *
 *   **AI を呼ぶ 2 つのカード（batC51 / batC52）は共通レイアウト**
 *   （`src/features/system-settings/aiSettingsLayout.ts`）に揃える。TAB・名称・並び・
 *   スライダーの刻みと単位はその定義が決めるので、**コンポーネント側に書き写さないこと**
 *   （画面の項目はキーと範囲・説明だけを持ち、`normalizeAiFields` を通して描く）。
 *   数字の項目はすべてスライダーで、右側の値表示（値＋単位）はこのコンポーネントが作る
 *   （設定ページに載るとランタイムの委譲ハンドラが同じ値を更新する）。
 *   AI を呼ばないカード（画像の取込・前処理 / コマンド検証・確定）は今までどおり数値入力。
 *
 * **有効／無効（`geometryAiEnabled` / `geometryAiAssistEnabled`）は画面に出さない**
 * （機能は常に有効。サーバーの既定も true）。値は読み込んだものをそのまま送り返し、
 * 読めなかったときは**そのキーを送らない**（空文字で上書きしない・失わせない）。
 */
const toast = useToast()

/**
 * このセクションは設定ページの分類【図形管理】のパネルの中に出す。
 * パネルとマウント点（`[data-external-slot]`）は設定ランタイムが作るので、
 * 現れたら（作り直されたら）そこへ **Teleport** する。
 * 見つからないとき（このコンポーネントだけを載せたテストなど）は、その場に描く。
 */
const SLOT_SELECTOR = '[data-external-slot="geometry_ai"]'
const slot = ref<HTMLElement | null>(null)
/** 設定ページの中に居るか（パネルの置き場があるか）。 */
const inSettingsPage = ref(false)
/** Teleport できているか（できているときはパネルが同じ見出しを出すので、カードの見出しは出さない）。 */
const teleported = computed(() => slot.value !== null)
/** マウント点がまだ無い（読み込み中）ときは、下にぶら下がって見えないように隠す。 */
const waitingForSlot = computed(() => slot.value === null && inSettingsPage.value)

let slotObserver: MutationObserver | null = null

/** マウント点を探し直す（ランタイムがパネルを作り直したら新しい要素に入り直す）。 */
function resolveSlot(): void {
  inSettingsPage.value = document.getElementById('settingPanels') !== null
  const found = document.querySelector(SLOT_SELECTOR)
  const next = found instanceof HTMLElement ? found : null
  if (next !== slot.value) slot.value = next
}

onBeforeUnmount(() => {
  slotObserver?.disconnect()
  slotObserver = null
})

const loading = ref(true)
const saving = ref(false)
/** 読み込み・保存の結果を出す案内（保存できないときは理由を日本語で出す）。 */
const status = ref('')
/**
 * サーバーが返した値（そのまま持ち回るキーと、「使用モデル」のスロット名に使う）。
 * `collect()` はここに入っている値だけを「保存しても失わせない」対象にする。
 */
const loadedSettings = ref<Record<string, string>>({})

/**
 * 画面に編集欄が無いキー（読み込んだ値をそのまま送り返す）。
 * どちらも有効／無効のスイッチだが、**機能は常に有効**なので画面には出さない。
 */
const PRESERVED_KEYS = ['geometryAiEnabled', 'geometryAiAssistEnabled']

/* ---------- 作図モード（batC51-A〜D。モードごとに独立したバッチ・プロンプト・出力 DTO） ---------- */

/**
 * AI 生図の**作図モード**（サーバーの `FigureMode` と同じ 4 つ）。
 *
 * モードごとに 1 バッチ（`batC51-A`〜`batC51-D`）・1 つの出力 DTO を持ち、設定キーは
 * `GEOMETRY_AI_<モード>_<項目>`（例: `GEOMETRY_AI_A_TASK_TEMPLATE`）に対応する camelCase になる。
 * 画面のカード・フック・Data TAB のバッチコードは、すべてこの 1 つの定義から作る
 * （4 枚ぶん書き写さない）。
 */
interface GmMode {
  /** 画面の値（A〜D）。設定キーの接頭辞になる。 */
  mode: string
  /** 画面の区切り（`data-setting-subsection`）。 */
  id: string
  /** フック・コントロール id の接尾辞（a〜d）。 */
  slug: string
  /** このモードのバッチコード（batC51-A）。Data TAB が取る出力 DTO もこのコードで決まる。 */
  taskCode: string
  /** カードの見出し。 */
  title: string
  /** このモードが何をするかの 1 行。 */
  description: string
  /** 入力→出力バッジ（`icon` は入力側のアイコン）。 */
  io: { icon: string; input: string; output: string }
}

const MODES: GmMode[] = [
  {
    mode: 'A', id: 'geometry-ai-mode-a', slug: 'a', taskCode: 'batC51-A',
    title: 'batC51-A（画像をもとに再現）',
    description: '画像から読み取った図形・式・文章をもとに、同じ作図を作り直します。',
    io: { icon: 'image', input: 'AI に送る画像', output: 'GeoGebra コマンド' }
  },
  {
    mode: 'B', id: 'geometry-ai-mode-b', slug: 'b', taskCode: 'batC51-B',
    title: 'batC51-B（数式からグラフを作成）',
    description: '入力した式・方程式・定義域から、関数や方程式のグラフを作ります。'
      + '作成する図の種類は GRAPH に固定です（画像は使いません）。',
    io: { icon: 'sigma', input: '数式・方程式・定義域', output: '関数・方程式のグラフ' }
  },
  {
    mode: 'C', id: 'geometry-ai-mode-c', slug: 'c', taskCode: 'batC51-C',
    title: 'batC51-C（文章の条件から作図）',
    description: '問題文などの文章にある条件だけから作図します（参考図は使いません）。',
    io: { icon: 'note', input: '問題文などの文章', output: 'GeoGebra コマンド' }
  },
  {
    mode: 'D', id: 'geometry-ai-mode-d', slug: 'd', taskCode: 'batC51-D',
    title: 'batC51-D（文章と図を合わせて作図）',
    description: '文章の条件と参考図の両方を使って作図します。',
    io: { icon: 'grid', input: '文章＋参考図', output: 'GeoGebra コマンド' }
  }
]

/** モード別の設定項目の接尾辞（`GEOMETRY_AI_<モード>_<接尾辞>` に対応する camelCase）。 */
const MODE_SUFFIXES = [
  'Provider', 'Temperature', 'MaxCompletionTokens', 'RequestTimeoutSeconds', 'RetryLimit',
  'SystemPrompt', 'TaskTemplate'
] as const

/** モード別の設定キー（camelCase）。`SettingPageFields` のフィールドキーと同じ規則で作る。 */
function modeKey(mode: GmMode, suffix: (typeof MODE_SUFFIXES)[number]): string {
  return `geometryAi${mode.mode}${suffix}`
}

/**
 * そのモードの User Prompt で使える変数（補充項目の分）。
 *
 * <p>名前の定義はサーバー側（`FigurePromptTemplate` / `AiFigureSupplements`）が唯一で、
 * **保存時も同じ一覧で検証する**。ここは画面の案内文（知らない変数を書くと保存で止まる）。</p>
 */
const MODE_VARIABLES: Record<string, string[]> = {
  A: ['reproduceFocus', 'whenInsufficient', 'knownValues', 'coordinateRange'],
  B: ['formulaCorrection', 'parameters', 'domain', 'viewRange', 'auxiliaryObjects'],
  C: ['goal', 'problemCorrection', 'whenInsufficient', 'parameters', 'domain', 'viewRange'],
  D: ['goal', 'keepObjects', 'changeObjects', 'textCorrection', 'parameters', 'domain', 'viewRange']
}

/**
 * ヘルプ文に出す変数（**1 行に収まる数だけ**）。
 *
 * 使える変数の全体はサーバーが保存時に検証して返す（知らない変数を書くと、そこへ一覧が出る）。
 * ここは「どんな変数が使えるか」の当たりを付けるための短い案内。
 */
function modeVariablesHelp(mode: GmMode): string {
  const names = ['resultType', ...(MODE_VARIABLES[mode.mode] ?? []).slice(0, 1)]
  return names.map((name) => `{${name}}`).join('・') + ' など。'
}

/**
 * モード別の既定値は**すべて空**。空は「共通の設定を継承する」を意味するので、既定値を入れては
 * いけない（入れると共通の設定を変えてもモードが古い値で上書きしてしまう）。
 */
const MODE_DEFAULTS: Record<string, string> = Object.fromEntries(
  MODES.flatMap((mode) => MODE_SUFFIXES.map((suffix) => [modeKey(mode, suffix), '']))
)

/**
 * **空欄が「共通の設定を継承する」を意味するキー**（4 つのモードの 28 キー）。
 * 数字の項目でも、空欄は既定値で埋めずに空のまま送る（サーバーは空を「共通を使う」と読む）。
 */
const MODE_INHERIT_KEYS = new Set(Object.keys(MODE_DEFAULTS))

/* ---------- 値（サーバーと読み書きする GEOMETRY_AI の 54 キー） ---------- */

/**
 * 画面の値（画面に出さないキーも含む）。キーは `SettingPageFields` のフィールドキーそのもの。
 * 既定値は `COM_設定情報` の seed と同じ（サーバーが値を返さないときだけ使う）。
 * モード別の 28 キー（`geometryAiA*`〜`geometryAiD*`）は `MODE_DEFAULTS`（すべて空）。
 */
const DEFAULT_VALUES: Record<string, string> = {
  // 画面に出さない（値を保持するだけ）
  geometryAiEnabled: 'true',
  geometryAiAssistEnabled: 'true',
  // batC51（画像取込・前処理）
  geometryAiMaxImageMb: '10',
  geometryAiMaxImagePixels: '1536',
  geometryAiDefaultCrop: 'manual',
  geometryAiDefaultKind: 'figure',
  geometryAiImageRetentionDays: '30',
  // batC51（AI 生成）共通規則。4 つのモードの欄が空のときに使われる
  geometryAiProvider: 'qwen:4',
  geometryAiOutputFormat: 'JSON',
  geometryAiTemperature: '0.2',
  geometryAiMaxCompletionTokens: '4096',
  geometryAiRequestTimeoutSeconds: '120',
  geometryAiSystemPrompt: '',
  // 共用の User Prompt は**空でよい**（4 つのモードの欄に書けばそれを使う。両方空ならテンプレート無し）
  geometryAiInstructionTemplate: '',
  geometryAiRetryLimit: '1',
  geometryAiDailyLimitPerAccount: '20',
  geometryAiMaxConcurrency: '1',
  geometryAiApproval: 'manual',
  // batC51-A〜D（作図モード別。空＝共通の設定を継承する）
  ...MODE_DEFAULTS,
  // コマンド検証・確定（バックエンドで実行。バッチではない）
  geometryAiAllowedCommands: '',
  geometryAiMaxCommands: '80',
  // AI 画図助手（バッチを通らない。user-api が同期で呼ぶ）
  geometryAiAssistProvider: 'qwen:4',
  geometryAiAssistTimeoutSeconds: '60',
  geometryAiAssistMaxCommands: '20',
  geometryAiAssistDailyLimitPerAccount: '50',
  geometryAiAssistSystemPrompt: '',
  geometryAiAssistUserPrompt: 'いまの作図のオブジェクト: {objects}\n指示: {instruction}\n'
    + 'コマンドは最大 {maxCommands} 個。'
}

/**
 * 数値のキー（空欄のまま保存されたときに直前に読み込んだ値／seed の既定値で送る）。
 *
 * モード別の数字も入れておくが、**空欄は「共通の設定を継承する」なので `numberText` は空のまま
 * 送る**（`MODE_INHERIT_KEYS`）。Temperature は小数を丸めないため、共通・モード別ともここには
 * 入れない（`apply` がそのままの値を保持する）。
 */
const NUMBER_SEEDS: Record<string, number> = {
  geometryAiMaxImageMb: 10,
  geometryAiMaxImagePixels: 1536,
  geometryAiImageRetentionDays: 30,
  geometryAiMaxCompletionTokens: 4096,
  geometryAiRequestTimeoutSeconds: 120,
  geometryAiRetryLimit: 1,
  geometryAiDailyLimitPerAccount: 20,
  geometryAiMaxConcurrency: 1,
  geometryAiMaxCommands: 80,
  geometryAiAssistTimeoutSeconds: 60,
  geometryAiAssistMaxCommands: 20,
  geometryAiAssistDailyLimitPerAccount: 50,
  // モード別（未設定なら共通を使うので、値が入っているときだけ数値として扱う）
  ...Object.fromEntries(MODES.flatMap((mode) => [
    [modeKey(mode, 'RequestTimeoutSeconds'), 120],
    [modeKey(mode, 'MaxCompletionTokens'), 4096],
    [modeKey(mode, 'RetryLimit'), 0]
  ]))
}

const values = ref<Record<string, string>>({ ...DEFAULT_VALUES })
/** 直前に読み込んだ数値（空欄で保存されたときに使う）。 */
const numericLoaded: Record<string, string> = {}

/* ---------- 選択肢 ---------- */

const CROP_OPTIONS = [
  { value: 'manual', label: '毎回指定する（アップロード後に切り抜く）' },
  { value: 'center', label: '中央を優先する' },
  { value: 'all', label: '全体を使う' }
]

const KIND_OPTIONS = [
  { value: 'figure', label: '図形' },
  { value: 'function', label: '関数グラフ' },
  { value: 'mixed', label: '判別が難しい複合図形' }
]

const OUTPUT_FORMAT_OPTIONS = [
  { value: 'JSON', label: 'JSON（コマンドと補足をまとめて返す）' },
  { value: 'COMMAND', label: 'コマンド列（1 行 1 コマンド）' }
]

const APPROVAL_OPTIONS = [
  { value: 'manual', label: '手動で確認（作図画面で確認してから保存する）' },
  { value: 'auto', label: '自動（生成した結果をそのまま保存する）' }
]

/** 「使用モデル」の選択肢（「AIモデル」ページのスロット。日本語単語AI と同じ並び・同じラベル）。 */
const modelOptions = computed(() => withCurrentModelSlot(
  modelSlotOptions(loadedSettings.value, true), values.value.geometryAiProvider ?? ''))
const assistModelOptions = computed(() => withCurrentModelSlot(
  modelSlotOptions(loadedSettings.value), values.value.geometryAiAssistProvider ?? ''))

/**
 * モード別カードの「使用モデル」の選択肢。
 *
 * モード別の値が**空のときは共通の設定を使う**ので、先頭に**空の選択肢**を置く
 * （利用者が「継承」へ戻せるようにする）。スロットの並び・ラベルは共通カードと同じ
 * （`modelSlots.ts` が唯一の定義）。
 */
function modeModelOptions(key: string): { value: string; label: string }[] {
  return [
    { value: '', label: '共通の設定を使う（未選択）' },
    ...withCurrentModelSlot(modelSlotOptions(loadedSettings.value, true), values.value[key] ?? '')
  ]
}

/* ---------- AI を呼ぶ 2 つのカード（batC51 / batC52。共通レイアウト） ---------- */

/*
 * タブ（基本設定 / System Prompt / User Prompt / その他）は `aiSettingsLayout.ts` の `aiTabsOf` が決める。
 * 並びや「項目の無い TAB は出さない」規則をここに書き写さないこと（唯一の定義は共通レイアウト側）。
 * TAB の切替は設定ランタイムの委譲ハンドラが行う（`data-method-tab` / `data-method-panel`）。
 */

/**
 * カードの 1 項目。
 *
 * **TAB・名称・並び・スライダーの刻みと単位は `aiSettingsLayout.ts`（唯一の定義）が決める**ので、
 * ここには「キー・コントロールの種類・値の範囲・説明」だけを書く。数字の項目は `type: 'number'`
 * と書き、`normalizeAiFields` が `range`（スライダー）＋ step ＋ suffix へ揃える。
 * `label` は、共通レイアウトが統一後の名称を持っているキー（使用モデル / リクエストタイムアウト /
 * スレッド数 …）では上書きされる。ここには揃えたあとの表示名を書く。
 */
interface GmField extends AiFieldLike {
  key: string
  label: string
  /**
   * コントロールの種類。数字の項目は `'number'` と書き、`normalizeAiFields` が `'range'`
   * （スライダー）へ揃える（画面に描くときは `'range'` になっている）。
   */
  type: 'select' | 'textarea' | 'radio' | 'number' | 'range'
  /** テスト用フック（今の `data-gm-set-*` をそのまま残す） */
  hook: string
  /** スライダー以外のコントロールの id（スライダーはランタイムと同じ `setting_<key>` を使う） */
  id?: string
  /** 項目の説明（1 行。事実に沿って書く） */
  help: string
  min?: number
  max?: number
  /** textarea の行数 */
  rows?: number
  /** 選択肢（select / radio） */
  options?: () => { value: string; label: string }[]
}

/** カードの見出し・説明の一部（`strong: true` の部分だけ強調して出す）。 */
interface GmText {
  text: string
  strong?: boolean
}

/** AI を呼ぶカード（見出し・入出力バッジ・項目）。 */
interface GmCard {
  /** 画面の区切り（`data-setting-subsection`） */
  id: string
  icon: string
  title: string
  description: GmText[]
  io: { icon: string; input: string; output: string }
  fields: GmField[]
  /** 表示する TAB（項目のある TAB ＋ Data。並びは AI_TABS の順） */
  tabs: string[]
  /** Data TAB に出す AI 出力 DTO のバッチコード（AI を呼ぶカードだけ）。 */
  dataTask?: string
  /** カードの下に出す補足（共通規則の「空は共通を使う」の案内など）。 */
  note?: string
}

/**
 * 共通規則（4 モード共通）の項目。
 *
 * ここは**モード別の欄が空のときに各モードが使う値**（サーバーの `FigureProcessorSettings` が
 * 継承する）と、モードごとには持たない実行条件（スレッド数・日次の上限・承認フロー）。
 *
 * 基本設定の並びは 使用モデル → リクエストタイムアウト → Temperature → 最大出力Token数 →
 * 出力形式（`aiRankOf` が決める）。その他は 最大再実行回数 → スレッド数（旧「同時処理数」）→
 * 日次の上限（旧「日次の上限（回）」）→ 処理結果の承認フロー。
 */
const COMMON_FIELDS: GmField[] = [
  {
    key: 'geometryAiProvider', label: '使用モデル', type: 'select', id: 'gm-set-model',
    hook: 'data-gm-set-model', options: () => modelOptions.value,
    help: '画像を読む視覚モデルを「AIモデル」ページのスロットから選びます。モード別の欄が空のときはここを使います。'
  },
  {
    key: 'geometryAiRequestTimeoutSeconds', label: 'リクエストタイムアウト', type: 'number',
    min: 30, max: 600, hook: 'data-gm-set-request-timeout',
    help: 'AI 呼び出し 1 回を待つ最大秒数です（30〜1800 秒）。'
  },
  {
    key: 'geometryAiTemperature', label: 'Temperature', type: 'number', min: 0, max: 2,
    hook: 'data-gm-set-temperature',
    help: '低い値ほど出力が安定します（0.0〜1.0 の 0.1 刻み。既定 0.2）。'
  },
  {
    key: 'geometryAiMaxCompletionTokens', label: '最大出力Token数', type: 'number',
    min: 1024, max: 65536, hook: 'data-gm-set-max-tokens',
    help: '1 回の応答で受け取る最大 Token 数です（1024〜16384）。'
  },
  {
    key: 'geometryAiOutputFormat', label: '出力形式', type: 'select', id: 'gm-set-output-format',
    hook: 'data-gm-set-output-format', options: () => OUTPUT_FORMAT_OPTIONS,
    help: 'AI に求める出力の形です（JSON かコマンド列）。'
  },
  {
    key: 'geometryAiSystemPrompt', label: 'System Prompt', type: 'textarea', rows: 8, full: true,
    id: 'gm-set-system-prompt', hook: 'data-gm-set-system-prompt',
    help: '必須です。モード別より先に置き、最後に出力 Schema が付きます。'
  },
  {
    key: 'geometryAiInstructionTemplate',
    label: 'User Prompt（共通の指示テンプレート・任意）',
    type: 'textarea', rows: 6, full: true, id: 'gm-set-template', hook: 'data-gm-set-template',
    help: '空でかまいません。{note}・{resultType} などを置換します。'
  },
  {
    key: 'geometryAiRetryLimit', label: '最大再実行回数', type: 'number', min: 0, max: 3,
    hook: 'data-gm-set-retry',
    help: 'JSON の検証に失敗したときの再実行回数です（0〜3 回）。'
  },
  {
    key: 'geometryAiMaxConcurrency', label: 'スレッド数', type: 'number', min: 1, max: 10,
    hook: 'data-gm-set-max-concurrency',
    help: '同時に処理する AI 生図の件数です（1〜4 件）。'
  },
  {
    key: 'geometryAiDailyLimitPerAccount', label: '日次の上限', type: 'number', min: 0, max: 100,
    hook: 'data-gm-set-daily-limit',
    help: '1 アカウント 1 日に作れる AI 生図の数です（0 は無制限）。'
  },
  {
    key: 'geometryAiApproval', label: '処理結果の承認フロー', type: 'radio', id: 'gm-set-approval',
    hook: 'data-gm-set-approval', full: true, options: () => APPROVAL_OPTIONS,
    help: '「手動で確認」なら作図画面で確認・調整してから保存します。'
  }
]

/**
 * batC52（AI 画図助手）の項目。
 *
 * 基本設定は 使用モデル → リクエストタイムアウト（旧「タイムアウト（秒）」）→ コマンド数の上限。
 * 日次の上限（旧「日次の上限（回）」）は AI の設定ではないので「その他」に入る
 * （そのため、このカードの「その他」は空ではない）。
 */
const ASSIST_FIELDS: GmField[] = [
  {
    key: 'geometryAiAssistProvider', label: '使用モデル', type: 'select', id: 'gm-set-assist-provider',
    hook: 'data-gm-set-assist-provider', options: () => assistModelOptions.value,
    help: '指示からコマンドを作るモデルを「AIモデル」ページのスロットから選びます。'
  },
  {
    key: 'geometryAiAssistTimeoutSeconds', label: 'リクエストタイムアウト', type: 'number',
    min: 30, max: 600, hook: 'data-gm-set-assist-timeout',
    help: '指示 1 回の AI 呼び出しを待つ最大秒数です（10〜300 秒）。'
  },
  {
    key: 'geometryAiAssistMaxCommands', label: 'コマンド数の上限', type: 'number', min: 1, max: 100,
    hook: 'data-gm-set-assist-max-commands',
    help: '1 回の指示で受け付けるコマンド数の上限です（1〜50）。'
  },
  {
    key: 'geometryAiAssistSystemPrompt', label: 'System Prompt', type: 'textarea', rows: 8,
    full: true, id: 'gm-set-assist-system-prompt', hook: 'data-gm-set-assist-system-prompt',
    help: '指示をコマンドに直すときの役割・禁止事項の指示です。'
  },
  {
    key: 'geometryAiAssistUserPrompt', label: 'User Prompt', type: 'textarea', rows: 4, full: true,
    id: 'gm-set-assist-user-prompt', hook: 'data-gm-set-assist-user-prompt',
    help: '{objects}・{instruction}・{maxCommands} を実行時に置き換えます。'
  },
  {
    key: 'geometryAiAssistDailyLimitPerAccount', label: '日次の上限', type: 'number', min: 0, max: 100,
    hook: 'data-gm-set-assist-daily-limit',
    help: '1 アカウント 1 日の AI 画図助手の回数です（0 は無制限）。'
  }
]

/** Data TAB（AI 出力 DTO の構造を見るタブ。項目を持たないので専用に足す）。 */
const DATA_TAB = 'Data'

/**
 * モード別カードの項目（7 項目 × 4 モード。キーは `geometryAi<モード><項目>`）。
 *
 * ここには「キー・コントロールの種類・値の範囲・説明」だけを書く（TAB・名称・並び・スライダーの
 * 刻みと単位は共通レイアウト `aiSettingsLayout.ts` が決める。数字は `type: 'number'` と書けば
 * `normalizeAiFields` がスライダーへ揃える）。**どの項目も空のときは共通の設定を使う**ので、
 * 説明にそう書く（利用者が空のままにしてよいと分かるように）。
 */
function modeFieldsOf(mode: GmMode): GmField[] {
  return [
    {
      key: modeKey(mode, 'Provider'), label: '使用モデル', type: 'select',
      id: `gm-set-${mode.slug}-provider`, hook: `data-gm-set-${mode.slug}-provider`,
      options: () => modeModelOptions(modeKey(mode, 'Provider')),
      help: '空のときは共通の設定を使います。「AIモデル」ページのスロットから選びます。'
    },
    {
      key: modeKey(mode, 'RequestTimeoutSeconds'), label: 'リクエストタイムアウト', type: 'number',
      min: 30, max: 600, hook: `data-gm-set-${mode.slug}-request-timeout`,
      help: 'AI 呼び出し 1 回を待つ最大秒数です。空のときは共通の設定を使います。'
    },
    {
      key: modeKey(mode, 'Temperature'), label: 'Temperature', type: 'number', min: 0, max: 2,
      hook: `data-gm-set-${mode.slug}-temperature`,
      help: '低い値ほど出力が安定します。空のときは共通の設定を使います。'
    },
    {
      key: modeKey(mode, 'MaxCompletionTokens'), label: '最大出力Token数', type: 'number',
      min: 1024, max: 65536, hook: `data-gm-set-${mode.slug}-max-tokens`,
      help: '1 回の応答で受け取る最大 Token 数です。空のときは共通の設定を使います。'
    },
    {
      key: modeKey(mode, 'RetryLimit'), label: '最大再実行回数', type: 'number', min: 0, max: 3,
      hook: `data-gm-set-${mode.slug}-retry`,
      help: '出力の検証に失敗したときの再実行回数です。空のときは共通の設定を使います。'
    },
    {
      key: modeKey(mode, 'SystemPrompt'), label: 'System Prompt', type: 'textarea', rows: 8,
      full: true, id: `gm-set-${mode.slug}-system-prompt`,
      hook: `data-gm-set-${mode.slug}-system-prompt`,
      help: 'このモードの指示です。共通に追加されます。空のときは共通の設定を使います。'
    },
    {
      key: modeKey(mode, 'TaskTemplate'), label: 'User Prompt（このモードの指示テンプレート）',
      type: 'textarea', rows: 6, full: true, id: `gm-set-${mode.slug}-task-template`,
      hook: `data-gm-set-${mode.slug}-task-template`,
      help: `空のときは共通の設定を使います。${modeVariablesHelp(mode)}`
    }
  ]
}

/**
 * AI を呼ばないカード（画像の取込・前処理／コマンド検証・確定）の**数字項目もスライダー**にする
 * （利用者の指示 2026-09-18）。TAB・名称・スライダーの刻みと単位は AI のブロックと同じ規則に揃える。
 * ここに置くのは数字の項目だけで、選択（切り抜き・分類）とテキストはカードの直書きのまま。
 */
const PREPROCESS_RANGE_FIELDS: GmField[] = [
  {
    key: 'geometryAiMaxImageMb', label: '画像 1 枚の最大サイズ', type: 'range', min: 1, max: 50,
    step: 1, suffix: 'MB', hook: 'data-gm-set-max-size',
    help: 'アップロードを受け付ける画像 1 枚の最大サイズです（1〜50 MB）。'
  },
  {
    key: 'geometryAiMaxImagePixels', label: 'AI へ送る画像の最大辺', type: 'range', min: 1024, max: 8192,
    step: 512, suffix: 'px', hook: 'data-gm-set-max-pixels',
    help: 'AI へ送る前に縮小する画像の最大辺です（512〜8192 px）。'
  }
]

/** 既定の切り抜き・分類の**あとに出す**数字項目（カードの並びは今までと同じ）。 */
const PREPROCESS_RETENTION_FIELDS: GmField[] = [
  {
    key: 'geometryAiImageRetentionDays', label: '画像の保持日数', type: 'range', min: 1, max: 365,
    step: 1, suffix: '日', hook: 'data-gm-set-retention',
    help: '取り込んだ画像を保持する日数です（1〜365 日）。'
  }
]

/** コマンド検証・確定の数字項目（同じくスライダー）。 */
const VERIFY_RANGE_FIELDS: GmField[] = [
  {
    key: 'geometryAiMaxCommands', label: 'コマンド数の上限', type: 'range', min: 1, max: 100,
    step: 1, suffix: '個', hook: 'data-gm-set-max-commands',
    help: '1 つの作図で使えるコマンド数の上限です（1〜200）。'
  }
]

/**
 * TAB の並びを作る。Data はプロンプトの後ろ・その他の前に置く
 * （基本設定 | System Prompt | User Prompt | Data | その他）。
 */
function tabsOfCard(tabs: readonly string[], dataTask?: string): string[] {
  if (!dataTask) {
    return [...tabs]
  }
  const withoutOther = tabs.filter((tab) => tab !== 'その他')
  return [...withoutOther, DATA_TAB, ...(tabs.includes('その他') ? ['その他'] : [])]
}

/**
 * モード別カードの User Prompt（`geometryAiATaskTemplate` など）の TAB を **User Prompt** に寄せる。
 *
 * 共通レイアウトはキー名だけで TAB を決める（`aiSettingsLayout.ts` の `USER_PROMPT_KEYS` は共通の
 * `geometryAiInstructionTemplate` を登録している）。モード別のテンプレートは `TaskTemplate` という
 * 名前なので、そのままでは「その他」に入ってしまう。モード別カードも共通カードと同じ並び
 * （基本設定 / System Prompt / User Prompt / Data / その他）にするため、ここで TAB だけを
 * 共通レイアウトの語彙（`AI_TAB_USER`）へ寄せる（名称・並び・刻みと単位は共通レイアウトのまま）。
 */
function userPromptTabOf(field: GmField): GmField {
  return field.key.endsWith('TaskTemplate') ? { ...field, tab: AI_TAB_USER } : field
}

/** カードを共通レイアウト（TAB・名称・並び・スライダー）に揃える。 */
function toCard(card: Omit<GmCard, 'tabs'>): GmCard {
  const fields = normalizeAiFields(card.fields).map(userPromptTabOf)
  return { ...card, fields, tabs: tabsOfCard(aiTabsOf(fields), card.dataTask) }
}

/**
 * 共通規則（4 モード共通）。AI 生成のブロックの**先頭**に出す。
 *
 * モード別カードの欄が空のときに各モードが使う値と、AI 生図の実行条件（スレッド数・日次の上限・
 * 承認フロー）をここに置く。**日次の上限などの実行条件はモードごとには持たない**（ここだけ）。
 */
const COMMON_CARD = toCard({
  id: 'geometry-ai-common', icon: 'sliders', title: '共通規則（4 モード共通）',
  description: [
    { text: 'batC51（AI 生成）の 4 つのモードで共通に使う設定です。各モードの欄が空のときはここを使います。'
      + 'AI 生図で' },
    { text: 'バッチを通るのはこのブロックだけ', strong: true },
    { text: 'です。' }
  ],
  note: 'モード別の欄（プロンプト・使用モデル・数字）は空のままで構いません。'
    + '空のときはこの共通の設定をそのまま使います。モードごとに変えたい項目だけ入力してください。',
  io: { icon: 'sliders', input: '4 モードの既定', output: '空の欄が継承' },
  fields: COMMON_FIELDS
})

/**
 * モード別カード（batC51-A〜D）。共通規則の**あと**に 4 枚並べる。
 * 各モードは 1 バッチで、Data TAB にはこのモードの出力 DTO を出す（`dataTask` ＝ バッチコード）。
 */
const MODE_CARDS: GmCard[] = MODES.map((mode) => toCard({
  id: mode.id, icon: mode.io.icon, title: mode.title, dataTask: mode.taskCode,
  description: [
    { text: mode.description },
    { text: ` ${mode.taskCode} のバッチとして実行されます。` },
    { text: '欄が空のときは共通の設定を使います。', strong: true }
  ],
  io: mode.io,
  fields: modeFieldsOf(mode)
}))

/** AI 生成のブロックに出すカード（先頭が 共通規則、続いて 4 つのモード）。 */
const AI_GENERATE_CARDS: GmCard[] = [COMMON_CARD, ...MODE_CARDS]

/** batC52（AI 画図助手）。設定ページの最後に出す。 */
const ASSIST_CARD = toCard({
  id: 'geometry-assist', icon: 'sliders', title: 'batC52（AI 画図助手・作図画面の【AI 助手】）', dataTask: 'batC52',
  description: [{ text: '作図画面で日本語の指示を出し、いまの作図を直します。' }],
  io: { icon: 'sliders', input: 'いまの作図＋日本語の指示', output: '変更後のコマンド' },
  fields: ASSIST_FIELDS
})

/** TAB の中の項目（並びは共通レイアウトが決めた順のまま）。 */
function fieldsOf(card: GmCard, tab: string): GmField[] {
  return card.fields.filter((field) => field.tab === tab)
}

/** 選択肢（select / radio）。 */
function optionsOf(field: GmField): { value: string; label: string }[] {
  return field.options ? field.options() : []
}

/** コントロールの id。スライダーはランタイムが `setting_<key>` と `setting_<key>_value` を前提にする。 */
function controlIdOf(field: GmField): string {
  return field.type === 'range' ? `setting_${field.key}` : String(field.id ?? '')
}

/**
 * 見出しに付ける属性。
 *
 * ・select / textarea / range … `<label for="<コントロールの id>">` で結び付ける
 * ・radio … ラジオは **かたまり** で 1 項目なので、かたまりを包む div を `for` で指すと
 *   Chrome の Issues「Incorrect use of <label for=FORM_ELEMENT>」になる。
 *   見出しは `<span class="setting-label" id="<id>_label">` にして、
 *   かたまり側を `role="radiogroup"` ＋ `aria-labelledby` で結び付ける。
 */
function labelBindingOf(field: GmField): Record<string, string> {
  return field.type === 'radio' ? { id: `${controlIdOf(field)}_label` } : { for: controlIdOf(field) }
}

/** 見出しの要素（radio のかたまりだけ label ではなく span）。 */
function labelTagOf(field: GmField): string {
  return field.type === 'radio' ? 'span' : 'label'
}

/**
 * スライダーのつまみの位置（いまの値。範囲外・未設定のときはランタイムと同じく min に寄せる）。
 * 値そのものは今までどおり `values` が持ち、保存も `collect()` が `values` から読む。
 */
function rangeValue(field: GmField): string {
  const parsed = Number.parseFloat(String(values.value[field.key] ?? ''))
  const min = Number(field.min ?? 0)
  const max = Number(field.max ?? min)
  if (!Number.isFinite(parsed)) return String(min)
  return String(Math.min(max, Math.max(min, parsed)))
}

/** 右側の値表示（値＋単位）。ランタイムが載っていない単体テストでも正しく見えるようにここで作る。 */
function rangeText(field: GmField): string {
  return rangeValue(field) + String(field.suffix ?? '')
}

/**
 * スライダーの塗り具合（CSS 変数 `--setting-range-progress`）。
 *
 * ランタイムが描く項目は `applyValues` が値を入れて `updateRangeControl` が塗りを更新するが、
 * この画面の項目は**ランタイムの項目ではない**（`geometry_ai` は external）ので、
 * 初回表示の塗りはこのコンポーネントが入れる（ドラッグ中はランタイムが同じ値を書く）。
 */
function rangeProgress(field: GmField): string {
  const min = Number(field.min ?? 0)
  const max = Number(field.max ?? min)
  const value = Number(rangeValue(field))
  return `${max > min ? ((value - min) / (max - min)) * 100 : 0}%`
}

/** テスト用フック（今の `data-gm-set-*`）。 */
function hookOf(field: GmField): Record<string, string> {
  return { [field.hook]: '' }
}

/** 選択肢ごとのテスト用フック（`data-gm-set-approval="manual"` など）。 */
function optionHookOf(field: GmField, value: string): Record<string, string> {
  return { [field.hook]: value }
}

/* ---------- 保存（サーバーと読み書きする GEOMETRY_AI の 54 キー） ---------- */

/**
 * 数値欄の値を送る形にする（空なら直前に読み込んだ値、無ければ seed の既定値）。
 *
 * ただし**モード別の項目は「空＝共通の設定を継承」**なので、空欄を既定値で埋めずに空のまま送る
 * （埋めると、共通の設定を変えてもモードが古い既定値で上書きしてしまう）。
 */
function numberText(key: string): string {
  const raw = String(values.value[key] ?? '').trim()
  const parsed = Number(raw)
  if (raw !== '' && Number.isFinite(parsed)) return String(parsed)
  if (MODE_INHERIT_KEYS.has(key)) return ''
  return numericLoaded[key] ?? String(NUMBER_SEEDS[key] ?? 0)
}

/**
 * 【既定に戻す】（利用者の指示: 設定ページに「既定値に戻す」を置く）。
 *
 * <p>共通規則は seed の既定値（`DEFAULT_VALUES`）へ、**モード別は「空＝共通を継承」**なので
 * 空欄へ戻す。戻しただけでは保存されない（保存は今までどおり【この設定を保存】）。</p>
 */
function resetToDefaults(): void {
  const next = { ...values.value }
  for (const key of Object.keys(DEFAULT_VALUES)) {
    if (PRESERVED_KEYS.includes(key)) continue
    next[key] = MODE_INHERIT_KEYS.has(key) ? '' : DEFAULT_VALUES[key]
  }
  values.value = next
  status.value = '既定の値に戻しました。【この設定を保存】を押すと保存されます。'
}

/** いまの画面の値をサーバーへ送る形にする（文字列だけ。型・範囲はサーバーが再検証する）。 */
function collect(): Record<string, string> {
  const result: Record<string, string> = {}
  for (const key of Object.keys(DEFAULT_VALUES)) {
    if (PRESERVED_KEYS.includes(key)) continue
    result[key] = key in NUMBER_SEEDS
      ? numberText(key)
      : String(values.value[key] ?? '').trim()
  }
  // 画面に出さないキーは、読み込んだ値をそのまま送り返す（読めなかったときは送らない）
  for (const key of PRESERVED_KEYS) {
    const loaded = loadedSettings.value[key]
    if (loaded !== undefined && loaded !== '') result[key] = loaded
  }
  return result
}

/** サーバーから来た値を画面へ反映する（知らないキーは無視する）。 */
function apply(next: Record<string, string> | null | undefined): void {
  if (next === null || next === undefined) return
  loadedSettings.value = { ...loadedSettings.value, ...next }
  const merged = { ...values.value }
  for (const key of Object.keys(DEFAULT_VALUES)) {
    const value = next[key]
    if (value === undefined || value === null || value === '') continue
    if (key in NUMBER_SEEDS) {
      const parsed = Number(value)
      if (!Number.isFinite(parsed) || parsed < 0) continue
      const rounded = String(Math.round(parsed))
      numericLoaded[key] = rounded
      merged[key] = rounded
    } else {
      merged[key] = String(value)
    }
  }
  values.value = merged
}

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

/** このセクションのキーを保存する。 */
async function save(): Promise<void> {
  if (saving.value) return
  saving.value = true
  status.value = ''
  try {
    const response = await saveSettingFields(collect())
    apply(response.data.settings)
    status.value = '設定を保存しました。'
    toast.success('図形管理の AI の設定を保存しました。')
  } catch (caught) {
    status.value = messageOf(caught, '設定を保存できませんでした。')
    toast.danger(status.value)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await nextTick()
  resolveSlot()
  if (typeof MutationObserver !== 'undefined') {
    // 設定ランタイムは読み込み後と【再読込】のたびにパネルを作り直すので、その都度入り直す
    slotObserver = new MutationObserver(() => { resolveSlot() })
    slotObserver.observe(document.body, { childList: true, subtree: true })
  }
  // 画面全体の【設定を保存】にこのセクションの値を載せる（値はこの画面が持っているため）。
  // フィールドキーはサーバーの SettingPageFields（SettingPageFields.java）と一致させる。
  // 未定義のキーを送ると 400 になり、画面の他の設定もまとめて保存できなくなる。
  window.__study21SystemSettings?.registerSection?.({ getValues: collect, applyValues: apply })
  try {
    const response = await loadSettingFields()
    apply(response.data.settings)
    status.value = ''
  } catch (caught) {
    // 設定 API に届かないときは初期値のまま使えるようにする（保存時に理由が出る）
    status.value = messageOf(caught, '設定を読み込めませんでした。')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <Teleport :to="slot ?? 'body'" :disabled="teleported === false">
    <section
      :class="teleported ? 'gm-ai-panel' : 'card gm-ai-set'" :hidden="waitingForSlot"
      data-gm-ai-settings
    >
      <div v-if="!teleported" class="card__header gm-list-head">
        <h2 class="card__title"><AppIcon name="wand" size="sm" /> AI 生図（図形管理）</h2>
        <span class="badge badge--neutral" data-gm-set-badge>図形管理</span>
      </div>

      <!-- ===================== batC51（画像取込・前処理） ===================== -->
      <section class="setting-batch-section" data-setting-subsection="geometry-preprocess">
        <header class="setting-batch-section-head">
          <div class="setting-batch-section-icon"><AppIcon name="image" /></div>
          <div>
            <h4>画像の取込・前処理（バックエンドで実行）</h4>
            <p>
              アップロードされた写真を受け取り、AI へ送る前に大きさを整えます。
              <strong>バッチではありません</strong>（AI を呼ばないため、バッチ実行履歴には残りません）。
            </p>
          </div>
        </header>

        <div class="setting-method-tabs setting-method-tabs-wtb">
          <div class="setting-tabs-buttons">
            <button type="button" class="active" data-method-tab="基本設定">基本設定</button>
          </div>
          <span class="setting-io-badge">
            <AppIcon name="image" size="sm" /> 図形の写真
            <AppIcon name="chevron-right" size="sm" /> AI に送る画像
          </span>
        </div>

        <section class="setting-method-tab-panel" data-method-panel="基本設定">
          <div class="setting-field-grid">
            <!-- 数字はスライダー（AI のブロックと同じ見た目・同じ刻み） -->
            <div
              v-for="field in PREPROCESS_RANGE_FIELDS" :key="field.key"
              class="setting-field setting-range-field"
            >
              <label class="setting-label" :for="controlIdOf(field)">
                <span>{{ field.label }}</span><small class="setting-field-key">{{ field.key }}</small>
              </label>
              <div class="setting-range-row">
                <input
                  :id="controlIdOf(field)" v-model="values[field.key]" class="setting-range" type="range"
                  :min="field.min" :max="field.max" :step="field.step" :value="rangeValue(field)"
                  :data-suffix="String(field.suffix ?? '')" v-bind="hookOf(field)"
                  :style="{ '--setting-range-progress': rangeProgress(field) }"
                >
                <output
                  :id="`${controlIdOf(field)}_value`" class="setting-range-value" :for="controlIdOf(field)"
                  :data-suffix="String(field.suffix ?? '')"
                >{{ rangeText(field) }}</output>
              </div>
              <p class="setting-help">{{ field.help }}</p>
            </div>

            <div class="setting-field">
              <label class="setting-label" for="gm-set-crop">
                <span>既定の切り抜き</span><small class="setting-field-key">geometryAiDefaultCrop</small>
              </label>
              <select
                id="gm-set-crop" v-model="values.geometryAiDefaultCrop"
                class="setting-control setting-dropdown" data-gm-set-crop
              >
                <option v-for="option in CROP_OPTIONS" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
              <p class="setting-help">AI 生図の画面を開いたときの切り抜きの初期状態です。</p>
            </div>

            <div class="setting-field">
              <label class="setting-label" for="gm-set-kind">
                <span>既定の分類</span><small class="setting-field-key">geometryAiDefaultKind</small>
              </label>
              <select
                id="gm-set-kind" v-model="values.geometryAiDefaultKind"
                class="setting-control setting-dropdown" data-gm-set-kind
              >
                <option v-for="option in KIND_OPTIONS" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
              <p class="setting-help">分類は AI に渡す条件です（図形／関数グラフ／複合図形）。</p>
            </div>

            <!-- 画像の保持日数もスライダー（カードの並びは今までどおり分類のあと） -->
            <div
              v-for="field in PREPROCESS_RETENTION_FIELDS" :key="field.key"
              class="setting-field setting-range-field"
            >
              <label class="setting-label" :for="controlIdOf(field)">
                <span>{{ field.label }}</span><small class="setting-field-key">{{ field.key }}</small>
              </label>
              <div class="setting-range-row">
                <input
                  :id="controlIdOf(field)" v-model="values[field.key]" class="setting-range" type="range"
                  :min="field.min" :max="field.max" :step="field.step" :value="rangeValue(field)"
                  :data-suffix="String(field.suffix ?? '')" v-bind="hookOf(field)"
                  :style="{ '--setting-range-progress': rangeProgress(field) }"
                >
                <output
                  :id="`${controlIdOf(field)}_value`" class="setting-range-value" :for="controlIdOf(field)"
                  :data-suffix="String(field.suffix ?? '')"
                >{{ rangeText(field) }}</output>
              </div>
              <p class="setting-help">{{ field.help }}</p>
            </div>
          </div>
        </section>
      </section>

      <!--
        ===================== batC51（AI 生成）: 共通規則 ＋ 4 つの作図モード =====================
        モードは A〜D の 4 つで、それぞれが 1 バッチ（batC51-A〜D）・1 プロンプト・1 出力 DTO を持つ。
        モード別の欄が空のときは、先頭の共通規則のカードの設定をそのまま使う。
      -->
      <section
        v-for="card in AI_GENERATE_CARDS" :key="card.id" class="setting-batch-section"
        :data-setting-subsection="card.id"
      >
        <header class="setting-batch-section-head">
          <div class="setting-batch-section-icon"><AppIcon :name="card.icon" /></div>
          <div>
            <h4>{{ card.title }}</h4>
            <p>
              <template v-for="(part, index) in card.description" :key="index">
                <strong v-if="part.strong">{{ part.text }}</strong>
                <template v-else>{{ part.text }}</template>
              </template>
            </p>
            <!-- 空＝共通の設定を継承する案内（共通規則のカードにだけ出す） -->
            <p v-if="card.note" class="gm-hint" data-gm-set-inherit-note>{{ card.note }}</p>
          </div>
        </header>

        <div class="setting-method-tabs setting-method-tabs-wtb">
          <div class="setting-tabs-buttons">
            <button
              v-for="(tab, index) in card.tabs" :key="tab" type="button"
              :class="{ active: index === 0 }" :data-method-tab="tab"
            >
              {{ tab }}
            </button>
          </div>
          <span class="setting-io-badge">
            <AppIcon :name="card.io.icon" size="sm" /> {{ card.io.input }}
            <AppIcon name="chevron-right" size="sm" /> {{ card.io.output }}
          </span>
        </div>

        <!--
          TAB も項目も共通レイアウト（aiSettingsLayout.ts）が決めた種類・順にそのまま出す。
          コントロールは項目の種類ごとに描く:
            ・select   … 使用モデル（「AIモデル」ページのスロット。空は共通の設定＝継承）／出力形式
            ・textarea … System Prompt / User Prompt
            ・radio    … 処理結果の承認フロー
            ・それ以外 … 数字の項目＝スライダー。`setting-range-row` と `setting_<key>`、
                          `<output id="setting_<key>_value">` を使い、設定ページに載ったときに
                          設定ランタイムの委譲ハンドラが右側の値表示を更新できる形にする。
        -->
        <section
          v-for="(tab, index) in card.tabs" :key="tab" class="setting-method-tab-panel"
          :data-method-panel="tab" :hidden="index !== 0"
        >
          <!-- Data TAB は DTO から生成した JSON Schema を見るだけ（項目は持たない） -->
          <AiDataSchemaPanel v-if="tab === DATA_TAB" :task="card.dataTask!" />

          <div v-else class="setting-field-grid">
            <div
              v-for="field in fieldsOf(card, tab)" :key="field.key" class="setting-field"
              :class="{ full: field.full, 'setting-range-field': field.type === 'range' }"
            >
              <component :is="labelTagOf(field)" class="setting-label" v-bind="labelBindingOf(field)">
                <span>{{ field.label }}</span><small class="setting-field-key">{{ field.key }}</small>
              </component>

              <select
                v-if="field.type === 'select'" :id="controlIdOf(field)" v-model="values[field.key]"
                class="setting-control setting-dropdown" v-bind="hookOf(field)"
              >
                <option v-for="option in optionsOf(field)" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>

              <textarea
                v-else-if="field.type === 'textarea'" :id="controlIdOf(field)" v-model="values[field.key]"
                class="setting-control" :rows="field.rows ?? 8" spellcheck="false" v-bind="hookOf(field)"
              ></textarea>

              <div
                v-else-if="field.type === 'radio'" :id="controlIdOf(field)" class="setting-radio-group"
                role="radiogroup" :aria-labelledby="labelBindingOf(field).id"
              >
                <label v-for="option in optionsOf(field)" :key="option.value" class="setting-radio-option">
                  <input
                    v-model="values[field.key]" type="radio" :name="controlIdOf(field)"
                    :value="option.value" v-bind="optionHookOf(field, option.value)"
                  >
                  <span>{{ option.label }}</span>
                </label>
              </div>

              <div v-else class="setting-range-row">
                <input
                  :id="controlIdOf(field)" v-model="values[field.key]" class="setting-range" type="range"
                  :min="field.min" :max="field.max" :step="field.step" :value="rangeValue(field)"
                  :data-suffix="String(field.suffix ?? '')" v-bind="hookOf(field)"
                  :style="{ '--setting-range-progress': rangeProgress(field) }"
                >
                <output
                  :id="`${controlIdOf(field)}_value`" class="setting-range-value" :for="controlIdOf(field)"
                  :data-suffix="String(field.suffix ?? '')"
                >{{ rangeText(field) }}</output>
              </div>

              <p class="setting-help">{{ field.help }}</p>
            </div>
          </div>
        </section>
      </section>

      <!-- ===================== コマンド検証・確定（バックエンドで実行） ===================== -->
      <section class="setting-batch-section" data-setting-subsection="geometry-verify">
        <header class="setting-batch-section-head">
          <div class="setting-batch-section-icon"><AppIcon name="check-circle" /></div>
          <div>
            <h4>コマンド検証・確定（バックエンドで実行）</h4>
            <p>
              AI が返したコマンドを許可リストと突き合わせ、通ったものだけを作図として確定します。
              <strong>バッチではありません</strong>（AI を呼ばないため、バッチ実行履歴には残りません）。
            </p>
          </div>
        </header>

        <div class="setting-method-tabs setting-method-tabs-wtb">
          <div class="setting-tabs-buttons">
            <button type="button" class="active" data-method-tab="基本設定">基本設定</button>
          </div>
          <span class="setting-io-badge">
            <AppIcon name="list" size="sm" /> GeoGebra コマンド
            <AppIcon name="chevron-right" size="sm" /> 検証済みの作図
          </span>
        </div>

        <section class="setting-method-tab-panel" data-method-panel="基本設定">
          <div class="setting-field-grid">
            <div class="setting-field full">
              <label class="setting-label" for="gm-set-allowed-commands">
                <span>許可するコマンド</span>
                <small class="setting-field-key">geometryAiAllowedCommands</small>
              </label>
              <textarea
                id="gm-set-allowed-commands" v-model="values.geometryAiAllowedCommands"
                class="setting-control" rows="4" spellcheck="false" data-gm-set-allowed-commands
              ></textarea>
              <p class="setting-help">
                AI が生成してよいコマンドの種類（カンマ区切り）です。ここに無いコマンドは検証の工程で弾かれます。
              </p>
            </div>

            <div
              v-for="field in VERIFY_RANGE_FIELDS" :key="field.key"
              class="setting-field setting-range-field"
            >
              <label class="setting-label" :for="controlIdOf(field)">
                <span>{{ field.label }}</span><small class="setting-field-key">{{ field.key }}</small>
              </label>
              <div class="setting-range-row">
                <input
                  :id="controlIdOf(field)" v-model="values[field.key]" class="setting-range" type="range"
                  :min="field.min" :max="field.max" :step="field.step" :value="rangeValue(field)"
                  :data-suffix="String(field.suffix ?? '')" v-bind="hookOf(field)"
                  :style="{ '--setting-range-progress': rangeProgress(field) }"
                >
                <output
                  :id="`${controlIdOf(field)}_value`" class="setting-range-value" :for="controlIdOf(field)"
                  :data-suffix="String(field.suffix ?? '')"
                >{{ rangeText(field) }}</output>
              </div>
              <p class="setting-help">{{ field.help }}</p>
            </div>
          </div>
        </section>
      </section>

      <!-- ===================== AI 画図助手（バッチを通らない） ===================== -->
      <section class="setting-batch-section" data-setting-subsection="geometry-assist">
        <header class="setting-batch-section-head">
          <div class="setting-batch-section-icon"><AppIcon :name="ASSIST_CARD.icon" /></div>
          <div>
            <h4>{{ ASSIST_CARD.title }}</h4>
            <p>
              <template v-for="(part, index) in ASSIST_CARD.description" :key="index">
                <strong v-if="part.strong">{{ part.text }}</strong>
                <template v-else>{{ part.text }}</template>
              </template>
            </p>
          </div>
        </header>

        <div class="setting-method-tabs setting-method-tabs-wtb">
          <div class="setting-tabs-buttons">
            <button
              v-for="(tab, index) in ASSIST_CARD.tabs" :key="tab" type="button"
              :class="{ active: index === 0 }" :data-method-tab="tab"
            >
              {{ tab }}
            </button>
          </div>
          <span class="setting-io-badge">
            <AppIcon :name="ASSIST_CARD.io.icon" size="sm" /> {{ ASSIST_CARD.io.input }}
            <AppIcon name="chevron-right" size="sm" /> {{ ASSIST_CARD.io.output }}
          </span>
        </div>

        <!--
          TAB も項目も共通レイアウト（aiSettingsLayout.ts）が決めた種類・順にそのまま出す。
          コントロールは項目の種類ごとに描く:
            ・select   … 使用モデル（「AIモデル」ページのスロット）
            ・textarea … System Prompt / User Prompt
            ・それ以外 … 数字の項目＝スライダー。`setting-range-row` と `setting_<key>`、
                          `<output id="setting_<key>_value">` を使い、設定ページに載ったときに
                          設定ランタイムの委譲ハンドラが右側の値表示を更新できる形にする。
        -->
        <section
          v-for="(tab, index) in ASSIST_CARD.tabs" :key="tab" class="setting-method-tab-panel"
          :data-method-panel="tab" :hidden="index !== 0"
        >
          <!-- Data TAB は DTO から生成した JSON Schema を見るだけ（項目は持たない） -->
          <AiDataSchemaPanel v-if="tab === DATA_TAB" :task="ASSIST_CARD.dataTask!" />

          <div v-else class="setting-field-grid">
            <div
              v-for="field in fieldsOf(ASSIST_CARD, tab)" :key="field.key" class="setting-field"
              :class="{ full: field.full, 'setting-range-field': field.type === 'range' }"
            >
              <component :is="labelTagOf(field)" class="setting-label" v-bind="labelBindingOf(field)">
                <span>{{ field.label }}</span><small class="setting-field-key">{{ field.key }}</small>
              </component>

              <select
                v-if="field.type === 'select'" :id="controlIdOf(field)" v-model="values[field.key]"
                class="setting-control setting-dropdown" v-bind="hookOf(field)"
              >
                <option v-for="option in optionsOf(field)" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>

              <textarea
                v-else-if="field.type === 'textarea'" :id="controlIdOf(field)" v-model="values[field.key]"
                class="setting-control" :rows="field.rows ?? 8" spellcheck="false" v-bind="hookOf(field)"
              ></textarea>

              <div v-else class="setting-range-row">
                <input
                  :id="controlIdOf(field)" v-model="values[field.key]" class="setting-range" type="range"
                  :min="field.min" :max="field.max" :step="field.step" :value="rangeValue(field)"
                  :data-suffix="String(field.suffix ?? '')" v-bind="hookOf(field)"
                  :style="{ '--setting-range-progress': rangeProgress(field) }"
                >
                <output
                  :id="`${controlIdOf(field)}_value`" class="setting-range-value" :for="controlIdOf(field)"
                  :data-suffix="String(field.suffix ?? '')"
                >{{ rangeText(field) }}</output>
              </div>

              <p class="setting-help">{{ field.help }}</p>
            </div>
          </div>
        </section>
      </section>

      <div class="gm-ai-set__foot">
        <button
          type="button" class="btn btn--primary" :disabled="saving || loading" data-gm-set-save @click="save"
        >
          <AppIcon name="check" size="sm" />
          {{ saving ? '保存中...' : 'この設定を保存' }}
        </button>
        <button
          type="button" class="btn btn--secondary" :disabled="saving || loading" data-gm-set-reset
          @click="resetToDefaults"
        >
          <AppIcon name="rotate" size="sm" /> 既定に戻す
        </button>
        <span class="gm-hint" data-gm-set-save-note>
          <template v-if="status !== ''">{{ status }}</template>
          <template v-else>この分頁の設定を保存します（上部の【設定を保存】でも保存されます）。</template>
        </span>
      </div>
    </section>
  </Teleport>
</template>
