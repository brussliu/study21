/**
 * AI 生図の「作図方法（A〜D）」と「作成する図の種類」、モードごとの補充項目の**定義**。
 *
 * <p>画面（`GeometryAiView.vue`）はここに書いた定義をそのまま描く。**どの項目がどのモード・種類で
 * 使えるか**を 1 か所に集めてあるので、応用が効かない項目を AI へ送ってしまうことがない
 * （「隠れた・当てはまらないパラメータを送らない」）。サーバー（`GeometryAiSupplements.java`）も
 * 同じ規則で絞るので、二重の防御になる。</p>
 *
 * <ul>
 *   <li>作図方法（A〜D）は「入力をどう読むか」を決める。</li>
 *   <li>作成する図の種類（AUTO / GEOMETRY / GRAPH / MIXED）は「何を作るか」を決める。
 *       B（数式からグラフ）だけは GRAPH に固定で、画面に選択肢を出さない。</li>
 * </ul>
 */

/** 作図方法。 */
export type FigureMode = 'A' | 'B' | 'C' | 'D'

/** 作成する図の種類。 */
export type FigureOutputType = 'AUTO' | 'GEOMETRY' | 'GRAPH' | 'MIXED'

export interface ModeDefinition {
  mode: FigureMode
  /** 画面に出す名前（サーバーの `FigureMode.label` と同じ） */
  label: string
  /** 何をするモードかの 1 文 */
  description: string
  /** 読み取る範囲の指定で気をつけること（モードごとの案内） */
  cropHint: string
  /** 「作成する図の種類」を選ばせるか（B は選ばせない） */
  asksOutputType: boolean
}

/** 作図方法の定義（画面の並び順）。 */
export const MODE_DEFINITIONS: ModeDefinition[] = [
  {
    mode: 'A',
    label: '画像をもとに再現',
    description: '画像から読み取った図形・式・文章をもとに、同じ作図を作り直します。',
    cropHint: '図形の全体・座標軸・目盛・書き込まれた値を残してください。切れてしまうと再現できません。',
    asksOutputType: true
  },
  {
    mode: 'B',
    label: '数式からグラフを作成',
    description: '画像に書かれた式・方程式・パラメータ・定義域から、グラフを作ります。',
    cropHint: '式の全体（左辺と右辺）・パラメータ・定義域の条件が切れないようにしてください。',
    asksOutputType: false
  },
  {
    mode: 'C',
    label: '文章の条件から作図',
    description: '問題文などの文章にある条件だけから作図します。',
    cropHint: '問題文と作図の指示が全部入るようにしてください。途中で切れると条件が足りません。',
    asksOutputType: true
  },
  {
    mode: 'D',
    label: '文章と図を合わせて作図',
    description: '文章の条件と参考図の両方を使って作図します。',
    cropHint: '問題文・式と、参考図の両方が入るようにしてください。',
    asksOutputType: true
  }
]

/** 作成する図の種類の定義（AUTO が既定）。 */
export const OUTPUT_TYPE_OPTIONS: { value: FigureOutputType; label: string; description: string }[] = [
  {
    value: 'AUTO',
    label: '自動判定',
    description: '内容を見て AI が決めます（既定）。'
  },
  {
    value: 'GEOMETRY',
    label: '幾何図形',
    description: '点・線・三角形・円などの図形を作ります。'
  },
  {
    value: 'GRAPH',
    label: '関数・方程式のグラフ',
    description: '関数や方程式のグラフを作ります（座標軸・目盛・定義域も扱います）。'
  },
  {
    value: 'MIXED',
    label: '図形とグラフの組み合わせ',
    description: '図形とグラフの両方を作ります。'
  }
]

/** 種類の名前（表示用）。 */
export function outputTypeLabel(value: FigureOutputType | null | undefined): string {
  return OUTPUT_TYPE_OPTIONS.find((option) => option.value === value)?.label ?? '自動判定'
}

/** 種類の説明（表示用）。 */
export function outputTypeDescription(value: FigureOutputType | null | undefined): string {
  return OUTPUT_TYPE_OPTIONS.find((option) => option.value === value)?.description ?? ''
}

/** 作図方法の名前（表示用。B は種類が固定なので補足を付ける）。 */
export function modeLabel(mode: FigureMode | null | undefined): string {
  return MODE_DEFINITIONS.find((definition) => definition.mode === mode)?.label ?? '（旧）画像から作図'
}

/** 作図方法の定義。 */
export function modeDefinition(mode: FigureMode): ModeDefinition {
  const found = MODE_DEFINITIONS.find((definition) => definition.mode === mode)
  return found ?? MODE_DEFINITIONS[0]
}

/* ------------------------------------------------------------ 補充項目 */

/** 補充項目の入力値（画面が持つ値。すべて文字列か真偽値）。 */
export interface SupplementValues {
  reproduceFocus: string
  whenInsufficient: string
  knownValues: string
  coordinateRange: string
  formulaCorrection: string
  parameters: string
  domain: string
  viewRange: string
  showAuxiliary: string
  goal: string
  problemCorrection: string
  keepObjects: string
  changeObjects: string
  textCorrection: string
  keepLabels: boolean
}

/** 補充項目の種類。 */
export type SupplementFieldType = 'radio' | 'text' | 'textarea'

/** 選択肢（`stored` はサーバーが保存する日本語。画面の文言と違うときだけ書く）。 */
export interface SupplementOption {
  value: string
  /** 画面に出す文言 */
  label: string
  /** サーバーが保存する文言（省略時は `label` と同じ） */
  stored?: string
}

/** 補充項目 1 つ分の定義。 */
export interface SupplementField {
  /** 画面とサーバーで共通のキー */
  key: keyof Omit<SupplementValues, 'keepLabels'>
  label: string
  type: SupplementFieldType
  help?: string
  options?: SupplementOption[]
  /** 上級の項目（既定は畳んで出す） */
  advanced?: boolean
}

const FOCUS_FIELD: SupplementField = {
  key: 'reproduceFocus',
  label: '再現の重点',
  type: 'radio',
  help: '数値や関係が読み取れるときは「数学的な関係を優先」がおすすめです。',
  options: [
    { value: 'MATH_FIRST', label: '数学的な関係を優先' },
    { value: 'APPEARANCE_FIRST', label: '見た目・配置を優先' }
  ]
}

const INSUFFICIENT_FIELD: SupplementField = {
  key: 'whenInsufficient',
  label: '情報が足りないとき',
  type: 'radio',
  help: '近似を選んでも、はっきり書かれている数値や数学的な条件を勝手に変えることはありません。',
  options: [
    { value: 'ASK_FIRST', label: '確認してから進める（既定）', stored: '確認してから進める' },
    { value: 'ALLOW_APPROXIMATE', label: '近似（明記する）で進めてよい' }
  ]
}

const COORDINATE_RANGE_FIELD: SupplementField = {
  key: 'coordinateRange',
  label: '座標の範囲・目盛',
  type: 'textarea',
  advanced: true,
  help: '例: x は -6〜6、y は -2〜8、目盛は 1 きざみ'
}

const PARAMETERS_FIELD: SupplementField = {
  key: 'parameters',
  label: 'パラメータの値',
  type: 'textarea',
  advanced: true,
  help: '例: a = 2、k = -1（書かなければ記号のまま扱います）'
}

const DOMAIN_FIELD: SupplementField = {
  key: 'domain',
  label: '定義域',
  type: 'textarea',
  advanced: true,
  help: '例: -2 ≦ x ≦ 3、x ≠ 0（表示範囲とは別です）'
}

const VIEW_RANGE_FIELD: SupplementField = {
  key: 'viewRange',
  label: '表示範囲',
  type: 'text',
  help: '空欄なら自動（キーになる対象が全部見えるように決めます）'
}

const SHOW_AUXILIARY_FIELD: SupplementField = {
  key: 'showAuxiliary',
  label: '補助的な対象',
  type: 'radio',
  help: '自動では答えの性質を持つ対象（交点・接線など）を足しません。',
  options: [
    { value: 'false', label: '自動では足さない（既定）', stored: '追加しない（答えの性質を持つものは足さない）' },
    { value: 'true', label: '必要なら足す', stored: '追加する' }
  ]
}

/** モードと種類に当てはまる補充項目（単純な項目が先・上級は `advanced`）。 */
export function supplementFieldsFor(mode: FigureMode, outputType: FigureOutputType): SupplementField[] {
  const graphLike = outputType === 'GRAPH' || outputType === 'MIXED'
  switch (mode) {
    case 'A':
      return [
        FOCUS_FIELD,
        INSUFFICIENT_FIELD,
        ...(graphLike ? [COORDINATE_RANGE_FIELD] : []),
        {
          key: 'knownValues',
          label: '既知の値（式・点・寸法・角）',
          type: 'textarea',
          advanced: true,
          help: '画像に書かれていて読み取りにくい値を、ここに書いて補えます。'
        }
      ]
    case 'B':
      return [
        VIEW_RANGE_FIELD,
        SHOW_AUXILIARY_FIELD,
        {
          key: 'formulaCorrection',
          label: '式の訂正',
          type: 'textarea',
          advanced: true,
          help: 'OCR の読み違いを直せます（例: 「x2」は x²）'
        },
        PARAMETERS_FIELD,
        DOMAIN_FIELD
      ]
    case 'C':
      return [
        {
          key: 'goal',
          label: '作図の目標',
          type: 'radio',
          options: [
            { value: 'GIVEN_ONLY', label: '与えられた条件だけを図にする' },
            { value: 'COMPLETE_CONSTRUCTION', label: '問題が求める作図を完成させる' }
          ]
        },
        INSUFFICIENT_FIELD,
        ...(graphLike ? [PARAMETERS_FIELD, DOMAIN_FIELD, VIEW_RANGE_FIELD] : []),
        {
          key: 'problemCorrection',
          label: '問題文の訂正',
          type: 'textarea',
          advanced: true,
          help: '読み取れなかった語句・記号をここで直せます。'
        }
      ]
    case 'D':
      return [
        {
          key: 'goal',
          label: '作図の目標',
          type: 'radio',
          options: [
            { value: 'REPRODUCE', label: '元の図をそのまま再現する（既定）', stored: '元の図をそのまま再現する' },
            { value: 'TRANSFORM', label: '文字の条件に合わせて補完・変換する' }
          ]
        },
        {
          key: 'keepObjects',
          label: '残す対象',
          type: 'textarea',
          help: '例: 円 c と点 A はそのまま残す'
        },
        {
          key: 'changeObjects',
          label: '追加・変更する対象',
          type: 'textarea',
          help: '例: 頂点 C から辺 AB に垂線を追加する'
        },
        ...(graphLike ? [PARAMETERS_FIELD, DOMAIN_FIELD, VIEW_RANGE_FIELD] : []),
        {
          key: 'textCorrection',
          label: '文字・式・ラベルの訂正',
          type: 'textarea',
          advanced: true,
          help: '読み取れなかった文字やラベルをここで直せます。'
        }
      ]
    default:
      return []
  }
}

/** 補充項目の既定値（モードと種類で変わる）。 */
export function defaultSupplementValues(mode: FigureMode, _outputType: FigureOutputType): Partial<SupplementValues> {
  const values: Partial<SupplementValues> = {}
  if (mode === 'A') {
    values.reproduceFocus = 'MATH_FIRST'
    values.whenInsufficient = 'ASK_FIRST'
  }
  if (mode === 'C') {
    values.goal = 'GIVEN_ONLY'
    values.whenInsufficient = 'ASK_FIRST'
  }
  if (mode === 'D') {
    values.goal = 'REPRODUCE'
  }
  if (mode === 'B') {
    values.showAuxiliary = 'false'
  }
  return values
}

/** 送信する補充パラメータ（**当てはまる項目だけ**。当てはまらない項目は `null` にする）。 */
export interface SupplementPayload {
  reproduceFocus?: string | null
  whenInsufficient?: string | null
  knownValues?: string | null
  coordinateRange?: string | null
  formulaCorrection?: string | null
  parameters?: string | null
  domain?: string | null
  viewRange?: string | null
  showAuxiliary?: boolean | null
  goal?: string | null
  problemCorrection?: string | null
  keepObjects?: string | null
  changeObjects?: string | null
  textCorrection?: string | null
  keepLabels?: boolean | null
}

/**
 * 画面の値から送信する形を作る。
 *
 * <p>**当てはまらない項目は入れない**（モードを切り替えたときに残っている入力も送らない）。
 * これでサーバー側の絞り込みと一致する。</p>
 */
export function toSupplementPayload(
  mode: FigureMode,
  outputType: FigureOutputType,
  values: Partial<SupplementValues>
): SupplementPayload {
  const payload: SupplementPayload = {}
  const fields = supplementFieldsFor(mode, outputType)
  for (const field of fields) {
    const raw = values[field.key]
    if (field.key === 'showAuxiliary') {
      // ラジオは文字列で持つ（'true' / 'false'）
      if (raw === undefined || raw === null || raw === '') continue
      payload.showAuxiliary = raw === 'true'
      continue
    }
    const text = typeof raw === 'string' ? raw.trim() : ''
    if (text === '') continue
    // 当てはまる項目だけを入れる（キーは動的なので、記録として緩い型で書く）
    ;(payload as Record<string, string | boolean | null>)[field.key] = text
  }
  // 元の名前とラベルは共通（既定は「残す」＝ true。いいえのときだけ意味がある）
  payload.keepLabels = values.keepLabels !== false
  return payload
}

/**
 * 保存済みの補充（日本語の項目名 → 値）から画面の値へ戻す（追加入力待ちの再開で使う）。
 *
 * <p>サーバーは日本語の項目名で保存するので、ここで逆引きする。**知らない項目は無視する**
 * （古い要求・新しい項目でも壊れない）。</p>
 */
export function fromSupplementRows(rows: Record<string, string>): Partial<SupplementValues> {
  const values: Partial<SupplementValues> = {}
  for (const field of ALL_SUPPLEMENT_FIELDS) {
    const stored = rows[field.label]
    if (stored === undefined) continue
    if (field.type === 'radio' && field.options !== undefined) {
      // 画面の文言とサーバーの文言が違うことがあるので、両方で照合する
      const option = field.options.find((candidate) =>
        candidate.label === stored || (candidate.stored ?? candidate.label) === stored)
      if (option !== undefined) {
        values[field.key] = option.value
      }
      continue
    }
    values[field.key] = stored
  }
  const keepLabels = rows['元の名前とラベル']
  if (keepLabels !== undefined) {
    values.keepLabels = !keepLabels.startsWith('いいえ')
  }
  return values
}

/** すべての補充項目（逆引き用の一覧）。 */
const ALL_SUPPLEMENT_FIELDS: SupplementField[] = [
  FOCUS_FIELD,
  INSUFFICIENT_FIELD,
  COORDINATE_RANGE_FIELD,
  PARAMETERS_FIELD,
  DOMAIN_FIELD,
  VIEW_RANGE_FIELD,
  SHOW_AUXILIARY_FIELD,
  {
    key: 'knownValues',
    label: '既知の値（式・点・寸法・角）',
    type: 'textarea'
  },
  {
    key: 'formulaCorrection',
    label: '式の訂正',
    type: 'textarea'
  },
  {
    key: 'goal',
    label: '作図の目標',
    type: 'radio',
    options: [
      { value: 'GIVEN_ONLY', label: '与えられた条件だけを図にする' },
      { value: 'COMPLETE_CONSTRUCTION', label: '問題が求める作図を完成させる' },
      { value: 'REPRODUCE', label: '元の図をそのまま再現する' },
      { value: 'TRANSFORM', label: '文字の条件に合わせて補完・変換する' }
    ]
  },
  {
    key: 'problemCorrection',
    label: '問題文の訂正',
    type: 'textarea'
  },
  {
    key: 'keepObjects',
    label: '残す対象',
    type: 'textarea'
  },
  {
    key: 'changeObjects',
    label: '追加・変更する対象',
    type: 'textarea'
  },
  {
    key: 'textCorrection',
    label: '文字・式・ラベルの訂正',
    type: 'textarea'
  }
]
