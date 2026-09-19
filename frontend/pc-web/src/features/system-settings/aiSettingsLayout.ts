/**
 * AI 呼び出しを含む設定ブロックの**共通レイアウト**（唯一の定義）。
 *
 * 利用者の指示（2026-09-16）で、設定ページの AI 設定はどのバッチでも同じ形に揃える:
 *
 * - **基本設定**: AI 呼び出しに関する設定（使用モデル / リクエストタイムアウト /
 *   Temperature / 最大出力Token数 / 出力形式 など）
 * - **System Prompt**: システムプロンプト
 * - **User Prompt**: ユーザープロンプト（実在するブロックだけ）
 * - **その他**: AI とは関係しない設定（最大再実行回数 / スレッド数 / 画像条件 /
 *   1回の最大処理数 / コマンド数の上限 / 日次の上限 / 承認フロー など）
 *
 * 名称と並びも固定し、**数字項目はすべてスライダー**にする（step と単位の suffix も
 * ここで決める）。この規則を使うのは次の 3 か所:
 *
 * 1. `study2SettingRuntime.ts`（runtime が描く AI バッチのブロック）
 * 2. `GeometryAiSettingsSection.vue`（batC51 / batC52）
 * 3. `ClassroomAiSettingsSection.vue`（STT / batC61 / batC62）
 *
 * AI 呼び出しを含まないブロック（画像の前処理・コマンド検証・録音の上限・トリガー・
 * プリセット管理など）は対象外（今のまま）。
 */

/** AI 設定ブロックの TAB（この順で表示する）。 */
export const AI_TABS = ['基本設定', 'System Prompt', 'User Prompt', 'その他'] as const

export type AiTab = (typeof AI_TABS)[number]

export const AI_TAB_BASIC: AiTab = '基本設定'
export const AI_TAB_SYSTEM: AiTab = 'System Prompt'
export const AI_TAB_USER: AiTab = 'User Prompt'
export const AI_TAB_OTHER: AiTab = 'その他'

/** 設定項目（runtime の field 定義と、コンポーネントの手書き項目の共通の形）。 */
export interface AiFieldLike {
  key: string
  label?: string
  type?: string
  min?: number
  max?: number
  step?: number
  suffix?: string
  tab?: string
  full?: boolean
  disabled?: boolean
  help?: string
  [key: string]: unknown
}

/** スライダーの刻みと単位（名称には単位を入れず、右側の値表示で示す）。 */
export interface AiSliderSpec {
  step: number
  suffix: string
}

/** 名前は UserPrompt を含まないが、実体は User Prompt（AI への指示テンプレート）。 */
const USER_PROMPT_KEYS = new Set([
  'geometryAiInstructionTemplate',
  // AI 生図のモード別タスクテンプレート（A〜D。空は共通を継承する）。
  // ここに登録しないと「その他」TAB に入ってしまう（利用者テンプレートは User Prompt に出す）。
  'geometryAiATaskTemplate',
  'geometryAiBTaskTemplate',
  'geometryAiCTaskTemplate',
  'geometryAiDTaskTemplate'
])

/**
 * バッチの**実行設定**（バッチをいつ動かすか）のキー。
 *
 * `monitorL02IntervalMinutes` は `/Timeout$/` に、`monitorL02OffsetMinutes` は `/Provider$/` に
 * たまたま一致してしまうため、ここで先に除く（そのままだと「その他」TAB へ入り、
 * 実行間隔が AI の通信条件として並んでしまう）。実行設定は AI の設定ではない。
 */
const SCHEDULE_SETTING_KEYS = new Set([
  'monitorL02IntervalMinutes',
  'monitorL02OffsetMinutes',
  'monitorL03IntervalMinutes',
  'monitorL03OffsetMinutes'
])

/** AI 呼び出しに関する設定（＝基本設定へ入るキー）。 */
function isBasicAiKey(key: string): boolean {
  // 実行設定（間隔・ずらし）は AI の設定ではないので、名前の一致より先に外す
  if (SCHEDULE_SETTING_KEYS.has(key)) return false
  if (USER_PROMPT_KEYS.has(key)) return false
  return (
    // 使用モデル（スロット選択 / プロバイダー選択）
    /(AiModel|AiProvider|Provider)$/.test(key) ||
    // 通信条件
    /TimeoutSeconds$|Timeout$/.test(key) ||
    /Temperature$/.test(key) ||
    /MaxCompletionTokens$/.test(key) ||
    // 出力の形
    /OutputFormat$/.test(key) ||
    // OCR の処理方式（どちらの AI 経路で処理するか）
    /OcrTextMethod$/.test(key) ||
    // STT（AI 音声認識）の接続と AI オプション
    /^classroomAiStt/.test(key)
  )
}

/** どの TAB に入れるか。 */
export function aiTabOf(key: string): AiTab {
  // 実行設定（実行間隔・ずらし）は「AI の通信条件」ではなく実行条件なので、基本設定に置く
  // （AI を使わないブロックなので厳密には共通レイアウトの対象外だが、置き場所は 1 か所に決める）
  if (SCHEDULE_SETTING_KEYS.has(key)) return AI_TAB_BASIC
  if (/UserPrompt/.test(key) || USER_PROMPT_KEYS.has(key)) return AI_TAB_USER
  if (/Prompt/.test(key)) return AI_TAB_SYSTEM
  if (isBasicAiKey(key)) return AI_TAB_BASIC
  return AI_TAB_OTHER
}

/** 統一後の名称（該当が無ければ今のラベルを残す＝undefined を返す）。 */
export function aiLabelOf(key: string): string | undefined {
  if (/(AiModel|AiProvider|Provider)$/.test(key)) return '使用モデル'
  if (/TimeoutSeconds$|Timeout$/.test(key)) return 'リクエストタイムアウト'
  if (/Temperature$/.test(key)) return 'Temperature'
  if (/MaxCompletionTokens$/.test(key)) return '最大出力Token数'
  if (/RetryLimit$|MaxRetries$/.test(key)) return '最大再実行回数'
  if (/Threads$|MaxConcurrency$/.test(key)) return 'スレッド数'
  if (/MaxImages$/.test(key)) return '最大画像枚数'
  if (/MaxImageMb$/.test(key)) return '画像1枚の最大サイズ'
  if (/MaxImagePixels$/.test(key)) return 'AI送信画像の最大辺'
  if (/BatchSize$|BatchMax$|BatchLimit$/.test(key)) return '1回の最大処理数'
  if (/DailyLimitPerAccount$/.test(key)) return '日次の上限'
  if (/ChunkSeconds$/.test(key)) return '分塊の長さ'
  return undefined
}

/**
 * 1 行の幅で出す項目か（AI のモデル／プロバイダーの選択）。
 *
 * <p>モデル名は長い（例: `千問 / qwen3.7-flash`）ので、半行だと見切れる。
 * どの画面（runtime / 図形管理 / 授業録音）でも同じ見た目にするため、規則はここ 1 か所に置く。</p>
 */
export function aiIsFullWidth(key: string): boolean {
  return /(AiModel|AiProvider|Provider)$/.test(key)
}

/** 1 回の最大処理数の単位（利用者の指定で**語**に統一。数え方の違いは説明文に書く）。 */
function batchUnitOf(key: string): string {
  if (/^intensiveExplanation/.test(key)) return '文'
  if (/^clozeExplanation/.test(key)) return '問'
  if (/BatchLimit$/.test(key)) return '枚'
  return '語'
}

/** 数字項目のスライダー仕様（step と単位）。数字でない項目は undefined。 */
export function aiSliderOf(key: string): AiSliderSpec | undefined {
  // 刻みと単位は利用者の指定（2026-09-18 の表）: 秒は 30 秒刻み、画像の辺は 512px 刻み、
  // 1 回の最大処理数は 10 件刻み。単位は「語／熟語」を**語**に統一した。
  if (/TimeoutSeconds$|Timeout$/.test(key)) return { step: 30, suffix: 's' }
  if (/Temperature$/.test(key)) return { step: 0.1, suffix: '' }
  if (/MaxCompletionTokens$/.test(key)) return { step: 512, suffix: '' }
  if (/RetryLimit$|MaxRetries$/.test(key)) return { step: 1, suffix: '回' }
  if (/Threads$|MaxConcurrency$/.test(key)) return { step: 1, suffix: '' }
  if (/MaxImages$/.test(key)) return { step: 1, suffix: '枚' }
  if (/MaxImageMb$/.test(key)) return { step: 1, suffix: 'MB' }
  if (/MaxImagePixels$/.test(key)) return { step: 512, suffix: 'px' }
  if (/BatchSize$|BatchMax$|BatchLimit$/.test(key)) return { step: 10, suffix: batchUnitOf(key) }
  if (/MaxCommands$/.test(key)) return { step: 1, suffix: '個' }
  if (/DailyLimitPerAccount$/.test(key)) return { step: 1, suffix: '回' }
  if (/ChunkSeconds$/.test(key)) return { step: 5, suffix: 's' }
  return undefined
}

/** TAB 内の並び順（小さいほど先頭）。 */
export function aiRankOf(key: string): number {
  const tab = aiTabOf(key)
  if (tab === AI_TAB_BASIC) {
    if (/(AiModel|AiProvider|Provider)$/.test(key)) return 10
    // STT は「接続の設定（プロバイダー・モデル・Endpoint・API Key）」を先頭にまとめる
    if (/^classroomAiStt(Provider|Model|Endpoint|ApiKey)$/.test(key)) return 10
    if (/TimeoutSeconds$|Timeout$/.test(key)) return 20
    if (/Temperature$/.test(key)) return 30
    if (/MaxCompletionTokens$/.test(key)) return 40
    if (/OutputFormat$/.test(key)) return 50
    return 90
  }
  if (tab === AI_TAB_OTHER) {
    if (/RetryLimit$|MaxRetries$/.test(key)) return 10
    if (/Threads$|MaxConcurrency$/.test(key)) return 20
    if (/MaxImages$/.test(key)) return 30
    if (/MaxImageMb$/.test(key)) return 40
    if (/MaxImagePixels$/.test(key)) return 50
    if (/BatchSize$|BatchMax$|BatchLimit$/.test(key)) return 60
    // コマンド数の上限は「1 回の実行で受け付ける数」＝ 実行条件（AI の設定ではない）
    if (/MaxCommands$/.test(key)) return 70
    return 90
  }
  return 10
}

/** TAB の表示順（AI_TABS の並び）。 */
export function aiTabIndex(tab: string): number {
  const index = AI_TABS.indexOf(tab as AiTab)
  return index < 0 ? AI_TABS.length : index
}

/**
 * 1 ブロック分の項目を共通レイアウトへ揃える（TAB・名称・スライダー・並び）。
 *
 * 元の配列は変更せず、揃えた結果を新しい配列で返す（並びは TAB → 項目の順）。
 */
export function normalizeAiFields<F extends AiFieldLike>(fields: readonly F[]): F[] {
  const normalized = fields.map((field) => {
    const next: AiFieldLike = { ...field, tab: aiTabOf(field.key) }
    const label = aiLabelOf(field.key)
    if (label) next.label = label
    if (aiIsFullWidth(field.key)) {
      // モデル／プロバイダーの選択は 1 行で出す（3 つの画面で同じ見た目にする）
      next.full = true
    }
    if (field.type === 'range' || field.type === 'number') {
      // 数字はすべてスライダー（刻みと単位はキーごとの決め打ち）
      const slider = aiSliderOf(field.key)
      next.type = 'range'
      if (slider) {
        next.step = slider.step
        next.suffix = slider.suffix
      }
    }
    return next as F
  })
  return sortAiFields(normalized)
}

/**
 * TAB → 項目の順に並べ替える（同じ順位は定義順のまま＝安定）。
 *
 * 画面は `category.fields` の順に項目を描くので、ブロック内の並びを揃えるには
 * 定義そのものをこの順にしておく必要がある。
 */
export function sortAiFields<F extends AiFieldLike>(fields: readonly F[]): F[] {
  return fields
    .map((field, index) => ({ field, index }))
    .sort((left, right) => {
      const byTab = aiTabIndex(String(left.field.tab)) - aiTabIndex(String(right.field.tab))
      if (byTab !== 0) return byTab
      const byRank = aiRankOf(left.field.key) - aiRankOf(right.field.key)
      if (byRank !== 0) return byRank
      return left.index - right.index // 同じ順位は定義順のまま（安定）
    })
    .map((entry) => entry.field)
}

/** 統一後の TAB 一覧（項目の無い TAB は出さない。並びは AI_TABS の順）。 */
export function aiTabsOf(fields: readonly AiFieldLike[]): AiTab[] {
  return AI_TABS.filter((tab) => fields.some((field) => (field.tab ?? aiTabOf(field.key)) === tab))
}
