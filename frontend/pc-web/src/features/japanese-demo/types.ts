/**
 * 日本語勉強【単語情報管理】デモ（画面確認用の試作）の型。
 *
 * この型は**デモ専用**で、本番の `@/api/japanese` の型とは別物。
 * 本番の API・DB・AI・TTS には一切つながない（`mock/` と `store/` だけで完結する）。
 * 本番の型をそのまま使わない理由は、正式開発の前に
 * 「画面で必要な項目」を自由に増減して確認するため。
 */

/** 詳細情報（AI 生成の参考情報）の状態。 */
export type DemoDetailStatus =
  /** まだ生成していない */
  | 'NOT_GENERATED'
  /** 生成中（デモの遅延つきモック） */
  | 'RUNNING'
  /** 生成済み */
  | 'GENERATED'
  /** 人が手で直した */
  | 'EDITED'
  /** 生成に失敗した（内容が無い状態とは区別する） */
  | 'FAILED'

/** 品詞の区分（デモで使う代表だけ）。 */
export type DemoPartOfSpeech = '名詞' | '動詞' | 'い形容詞' | 'な形容詞' | '副詞' | '名詞・動詞'

/** 語義（意味のまとまり）。 */
export interface DemoDetailSense {
  id: string
  /** 語義番号（1 から） */
  number: number
  /** 日本語の意味 */
  japanese: string
  /** 中国語の意味 */
  chinese: string
  /** 使う場面・文脈 */
  context: string
  /** 文体（普通／やや硬い／話し言葉 など） */
  style: string
}

/** 例文（語義に紐づけられる）。 */
export interface DemoDetailExample {
  id: string
  /** 日本語の例文 */
  japanese: string
  /** 読み（かな） */
  reading: string
  /** 中国語訳 */
  chinese: string
  /** どの語義の例文か（語義番号。null なら全体） */
  senseNumber: number | null
  /** 出典・場面メモ（例: 学校生活） */
  source: string
  /** やさしい例文か、応用例文か */
  level: 'BASIC' | 'APPLIED'
}

/** 文型・助詞の使い方。 */
export interface DemoDetailPattern {
  id: string
  /** 文型（助詞を ** で囲む。例: 人**に**相談する） */
  pattern: string
  /** 読み */
  reading: string
  /** 中国語での説明 */
  chinese: string
  /** 短い例文 */
  example: string
  /** 例文の中国語訳 */
  exampleChinese: string
}

/** 会話（2〜4 文の短いやりとり）。 */
export interface DemoDetailDialogLine {
  id: string
  /** 役（先生／学生／店員 など） */
  speaker: string
  /** せりふ */
  japanese: string
  /** 中国語訳 */
  chinese: string
}

/** 会話のかたまり（場面つき）。 */
export interface DemoDetailDialog {
  id: string
  /** 場面（例: 職員室で先生に相談する） */
  scene: string
  lines: DemoDetailDialogLine[]
}

/** 類義語・使い分け。 */
export interface DemoDetailSynonym {
  id: string
  /** 比べる言葉 */
  heading: string
  /** 読み */
  reading: string
  /** 中国語の意味 */
  chinese: string
  /** 共通する意味 */
  shared: string
  /** 違うところ（中国語で説明してよい） */
  difference: string
  /** 使う場面 */
  usage: string
  /** 入れ替えられるか（互換性の説明。絶対規則にしない） */
  interchangeable: 'YES' | 'SOMETIMES' | 'NO'
  /** 入れ替えの補足（どの場合に入れ替えられるか） */
  interchangeNote: string
  /** 比べる例文（日本語） */
  exampleJapanese: string
  /** 比べる例文の中国語訳 */
  exampleChinese: string
}

/** 間違えやすいポイントの種類。 */
export type DemoCautionKind =
  /** 文法として誤り */
  | 'GRAMMAR'
  /** この場面では不自然 */
  | 'UNNATURAL'
  /** 意味が違う（日中同形異義語など） */
  | 'MEANING'
  /** 助詞の選び方 */
  | 'PARTICLE'

/** 間違えやすいポイント（中国語母語の学習者向け）。 */
export interface DemoDetailCaution {
  id: string
  kind: DemoCautionKind
  /** 何が問題か（見出し） */
  title: string
  /** 誤った言い方 */
  wrong: string
  /** 自然な言い方 */
  correct: string
  /** なぜ違うか（中国語で説明してよい） */
  reason: string
}

/** 活用形（動詞・形容詞のみ）。 */
export interface DemoDetailConjugation {
  id: string
  /** 形の名前（例: て形／た形／ない形） */
  form: string
  /** 活用した形 */
  value: string
  /** 短い例文 */
  example: string
}

/** 自他動詞の対応。 */
export interface DemoDetailTransitivityPair {
  /** 自動詞 */
  intransitive: string
  /** 他動詞 */
  transitive: string
  /** 助詞の違いの説明（「～が／～を」など） */
  particleNote: string
  /** 例文（自動詞） */
  intransitiveExample: string
  /** 例文（他動詞） */
  transitiveExample: string
}

/** 発音・アクセント。 */
export interface DemoDetailPronunciation {
  /** 読み */
  reading: string
  /** アクセントの型。未確認なら null（勝手に作らない） */
  accentType: number | null
  /** アクセント表記（例: あꜜい）。未確認なら null */
  accentNotation: string | null
  /** 発音のヒント（中国語母語話者向けの補足） */
  hint: string
  /** 音声サンプルがあるか（デモでは常に false。無いときは「デモ音声」と明示する） */
  hasAudioSample: boolean
}

/** コロケーション（よく使う言い回し）。 */
export interface DemoDetailCollocation {
  id: string
  /** 言い回し */
  expression: string
  /** 読み */
  reading: string
  /** 中国語の意味 */
  chinese: string
  /** 使う場面 */
  usage: string
}

/** 関連語の関係。 */
export type DemoRelatedRelation = '類義語' | '対義語' | '間違えやすい' | '同じ読み'

/** 関連語。 */
export interface DemoDetailRelatedWord {
  id: string
  relation: DemoRelatedRelation
  heading: string
  reading: string
  chinese: string
}

/** 使用場面・語感のメモ。 */
export interface DemoDetailUsageNote {
  id: string
  /** 話し言葉／書き言葉 など */
  register: '話し言葉' | '書き言葉' | 'どちらも'
  /** 丁寧さ（カジュアル／普通／丁寧） */
  politeness: 'カジュアル' | '普通' | '丁寧'
  /** 使う相手・場面 */
  audience: string
  /** 補足（中国語でよい）。どの語義についてか分かるときは語義番号を添える */
  note: string
  /** どの語義についてか（語義番号。null なら全体） */
  senseNumber: number | null
}

/** 記憶のヒント（語源ではない。記憶の助けとして明示する）。 */
export interface DemoDetailMemoryHint {
  /** 一言の覚え方 */
  hint: string
  /** 何に結びつけるか（漢字の形・場面など） */
  basis: string
}

/** ミニ練習の種類。 */
export type DemoPracticeKind = 'PARTICLE' | 'SYNONYM' | 'SCENE' | 'WRITING'

/** ミニ練習の問題。 */
export interface DemoPracticeQuestion {
  id: string
  kind: DemoPracticeKind
  /** 問題文（日本語） */
  question: string
  /** 問題の補足（中国語でよい） */
  questionChinese: string
  /** 選択式の選択肢（WRITING のときは空） */
  choices: string[]
  /** 正解（選択式なら選択肢の値。WRITING なら模範例文） */
  answer: string
  /** 解説（中国語でよい） */
  explanation: string
  /** 記述式（自由造句）かどうか */
  freeWriting: boolean
  /** 記述式のときに出す、模擬 AI フィードバックの見本 */
  demoFeedback: DemoWritingFeedback | null
}

/** 自由造句のフィードバック（**模擬**。本物の添削ではない）。 */
export interface DemoWritingFeedback {
  /** 文法の観点 */
  grammar: string
  /** 自然さの観点 */
  naturalness: string
  /** 参考表現 */
  reference: string
}

/** AI 詳細の中身（詳細編集で編集する本体）。 */
export interface DemoDetailContent {
  /** 一言の核心的な意味（見出しの下に出す） */
  coreMeaning: string
  /** 日本語の説明 */
  descriptionJa: string
  /** 中国語の説明 */
  descriptionZh: string
  senses: DemoDetailSense[]
  examples: DemoDetailExample[]
  patterns: DemoDetailPattern[]
  dialogs: DemoDetailDialog[]
  synonyms: DemoDetailSynonym[]
  cautions: DemoDetailCaution[]
  conjugations: DemoDetailConjugation[]
  transitivityPair: DemoDetailTransitivityPair | null
  pronunciation: DemoDetailPronunciation | null
  collocations: DemoDetailCollocation[]
  relatedWords: DemoDetailRelatedWord[]
  usageNotes: DemoDetailUsageNote[]
  memoryHint: DemoDetailMemoryHint | null
  practices: DemoPracticeQuestion[]
}

/** 単語が載っている教材（1 語が複数の本に載ることがある）。 */
export interface DemoCollection {
  id: string
  /** 書籍名 */
  book: string
  /** Unit（例: Unit003） */
  unit: string
  /** Unit の中での順番（1 から） */
  seq: number
  /** 教材の紙面どおりの表記（母表の正規化値と違うことがある） */
  listedWord: string
  listedReading: string
  /** 教材の中国語意味 */
  listedChinese: string
}

/** 単語 1 語（デモ）。 */
export interface DemoWord {
  id: string
  /** 見出し語 */
  heading: string
  /** 読み（かな） */
  reading: string
  /** 同じ表記で読みが違うときの別読み（例: 開く の ひらく） */
  alternateReading: string | null
  /** 品詞 */
  partOfSpeech: DemoPartOfSpeech
  /** JLPT レベルの**例示値**（権威ある判定ではない。画面では「例」と明示する） */
  jlpt: string | null
  /** 中国語の核心的な意味 */
  chineseMeaning: string
  /** 詳細情報の状態 */
  detailStatus: DemoDetailStatus
  /** 生成が失敗したときの理由（一覧に出す） */
  failureReason: string | null
  /** 手で直したか（`detailStatus === 'EDITED'` と対応） */
  manuallyEdited: boolean
  /** 教材のどこに載っているか（1 件以上） */
  collections: DemoCollection[]
  /** AI 詳細の中身（未生成・失敗のときは null） */
  detail: DemoDetailContent | null
  /** 最終更新（表示用の相対時刻の元。内部 ID や技術情報は出さない） */
  updatedAt: string
  /** 生成中に進めるためのモック用の目印（デモの演出にだけ使う） */
  demoNote: string | null
}

/** 書籍（デモ）。 */
export interface DemoBook {
  id: string
  name: string
  /** 1 Unit あたりの標準語数 */
  unitSize: number
  /** いまの Unit 一覧（順番どおり） */
  units: DemoBookUnit[]
  note: string
}

/** 書籍の Unit。 */
export interface DemoBookUnit {
  name: string
  /** 入っている語数（デモでは入力から積み上げる） */
  count: number
  /** 語数の上限が決まっているか（未満の Unit を作るため） */
  capacity: number
}

/** デモ表示設定の項目（§8）。 */
export interface DemoDisplayState {
  /** 一覧の見え方 */
  listMode: DemoListMode
  /** 詳細の見え方 */
  detailMode: DemoDetailMode
  /** 保存の見え方 */
  saveMode: DemoSaveMode
}

export type DemoListMode = 'NORMAL' | 'NO_RESULT' | 'NOT_GENERATED' | 'GENERATING' | 'FAILED'
export type DemoDetailMode = 'FULL' | 'PARTIAL'
export type DemoSaveMode = 'NORMAL' | 'SAVE_FAILED' | 'CONFLICT'

/** デモの非同期処理（AI 生成の模擬など）の進行状態。 */
export interface DemoJob {
  wordId: string
  kind: 'GENERATE' | 'SUPPLEMENT'
  /** 開始時刻（ms）。古い結果で新しい内容を上書きしないための世代管理にも使う */
  startedAt: number
  /** この世代の通し番号（画面を離れても世代で判定する） */
  generation: number
}
