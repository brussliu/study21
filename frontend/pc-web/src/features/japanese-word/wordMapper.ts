/**
 * 本番 API（`/api/user/japanese`）の単語・詳細を、新画面（`views/japanese/demo/`）が使う形へ移し替える。
 *
 * 詳細は**版**（`JPN_単語詳細情報` の版ヘッダ ＋ 11 の子テーブル）で、サーバーが 1 つの JSON に
 * 組み立てて返す。**キーはサーバーの産出が唯一の規範**なので、ここではそのキーだけを読む
 * （2.0 の列名 `noteType` / `relationType` などはもう返らない）。
 * 項目名の対応をここ 1 か所にまとめる（画面側は表示だけを受け持つ）。
 *
 * API に無い項目は空のままにする。推測で埋めない。
 * ・一言の核心的な意味（`coreMeaning`）… 無ければ中国語の代表意味（`chineseMeaning`）を使う
 */

import type {
  JpnCaution,
  JpnCautionKind,
  JpnCollocation,
  JpnCollection,
  JpnConjugation,
  JpnDetailVersion,
  JpnDialog,
  JpnExample,
  JpnMemoryHint,
  JpnPattern,
  JpnPractice,
  JpnPracticeKind,
  JpnPronunciation,
  JpnRelatedWord,
  JpnSense,
  JpnSynonym,
  JpnTransitivityPair,
  JpnUsageNote,
  JpnWordDetail,
  JpnWordDetailBody
} from '@/api/japanese'
import type {
  DemoCautionKind,
  DemoCollection,
  DemoDetailCaution,
  DemoDetailCollocation,
  DemoDetailConjugation,
  DemoDetailContent,
  DemoDetailDialog,
  DemoDetailExample,
  DemoDetailMemoryHint,
  DemoDetailPattern,
  DemoDetailPronunciation,
  DemoDetailRelatedWord,
  DemoDetailSense,
  DemoDetailStatus,
  DemoDetailSynonym,
  DemoDetailTransitivityPair,
  DemoDetailUsageNote,
  DemoPartOfSpeech,
  DemoPracticeKind,
  DemoPracticeQuestion,
  DemoRelatedRelation,
  DemoWord
} from '@/features/japanese-demo/types'
import { INTERCHANGEABLE_LABELS, PRACTICE_KIND_LABELS } from '@/features/japanese-demo/logic'

/** 空の詳細（API に対応する項目が無いときの器）。 */
export function emptyDetail(): DemoDetailContent {
  return {
    coreMeaning: '',
    descriptionJa: '',
    descriptionZh: '',
    senses: [],
    examples: [],
    patterns: [],
    dialogs: [],
    synonyms: [],
    cautions: [],
    conjugations: [],
    transitivityPair: null,
    pronunciation: null,
    collocations: [],
    relatedWords: [],
    usageNotes: [],
    memoryHint: null,
    practices: []
  }
}

/** 画面で使う品詞（新画面の選択肢）に寄せる。当てはまらないものは「名詞」にする。 */
function toPartOfSpeech(value: string | null): DemoPartOfSpeech {
  const known: DemoPartOfSpeech[] = ['名詞', '動詞', 'い形容詞', 'な形容詞', '副詞', '名詞・動詞']
  const text = (value ?? '').trim()
  return known.find((option) => option === text) ?? '名詞'
}

/** 詳細の取得状態（一覧の「詳細情報」列と同じ考え方）。 */
export function detailStatusOf(detail: JpnWordDetail | null): DemoDetailStatus {
  if (detail === null) {
    return 'NOT_GENERATED'
  }
  return detail.detail.manuallyCorrected === true ? 'EDITED' : 'GENERATED'
}

/** 単語（母表）＋収録＋詳細を、新画面の `DemoWord` に移し替える。 */
export function toStudyWord(input: {
  wordId: number
  word: string
  reading: string
  jlptLevel: string | null
  partOfSpeech: string | null
  collections: JpnCollection[]
  detail: JpnWordDetail | null
  updatedAt?: string
}): DemoWord {
  return {
    id: String(input.wordId),
    heading: input.word,
    reading: input.reading ?? '',
    alternateReading: input.detail?.detail.alternateReading ?? null,
    partOfSpeech: toPartOfSpeech(input.partOfSpeech),
    jlpt: input.jlptLevel,
    chineseMeaning: input.detail?.detail.chineseMeaning ?? input.detail?.detail.coreMeaning ?? '',
    detailStatus: detailStatusOf(input.detail),
    failureReason: null,
    manuallyEdited: input.detail?.detail.manuallyCorrected === true,
    collections: input.collections.map(toCollection),
    detail: toDetail(input.detail),
    updatedAt: input.updatedAt ?? '',
    demoNote: null
  }
}

/** 収録（教材のどこに載っているか）。 */
function toCollection(collection: JpnCollection): DemoCollection {
  return {
    id: String(collection.collectionId),
    book: collection.book,
    unit: collection.category,
    seq: collection.wordSeq,
    listedWord: collection.listedWord ?? '',
    listedReading: collection.listedReading ?? '',
    listedChinese: collection.chineseMeaning ?? ''
  }
}

/** 詳細（版ヘッダ ＋ 11 段落）を、新画面の詳細の形へ。 */
export function toDetail(detail: JpnWordDetail | null): DemoDetailContent | null {
  if (detail === null) {
    return null
  }
  const body = detail.detail
  const content = emptyDetail()
  content.coreMeaning = body.coreMeaning ?? body.chineseMeaning ?? ''
  content.descriptionJa = body.descriptionJa ?? ''
  content.descriptionZh = body.descriptionZh ?? ''
  content.senses = toSenses(body.senses ?? [])
  content.examples = (body.examples ?? []).map(toExample)
  content.patterns = (body.patterns ?? []).map(toPattern)
  content.dialogs = (body.dialogs ?? []).map(toDialog)
  content.synonyms = (body.synonyms ?? []).map(toSynonym)
  content.cautions = (body.cautions ?? []).map(toCaution)
  content.conjugations = (body.conjugations ?? []).map(toConjugation)
  content.transitivityPair = toTransitivityPair(body.transitivityPair)
  content.pronunciation = toPronunciation(body)
  content.collocations = (body.collocations ?? []).map(toCollocation)
  content.relatedWords = (body.relatedWords ?? []).map(toRelatedWord)
  content.usageNotes = (body.usageNotes ?? []).map(toUsageNote)
  content.memoryHint = toMemoryHint(body.memoryHint)
  content.practices = (body.practices ?? []).map(toPractice)
  return content
}

/**
 * 語義の番号を補う。
 *
 * サーバーは語義番号が無ければ 1 から振るが、古い版は null のことがある。
 * 例文・使用場面は語義番号で結び付くので、ここでも同じ規則で振る。
 */
function toSenses(senses: JpnSense[]): DemoDetailSense[] {
  return senses.map((sense, index) => {
    const number = sense.number === null || sense.number === undefined || sense.number < 1
      ? index + 1
      : sense.number
    return {
      id: `sense-${number}`,
      number,
      japanese: sense.japanese ?? '',
      chinese: sense.chinese ?? '',
      context: sense.context ?? '',
      style: sense.style ?? ''
    }
  })
}

function toExample(example: JpnExample, index: number): DemoDetailExample {
  return {
    id: `example-${index}`,
    japanese: example.japanese ?? '',
    reading: example.reading ?? '',
    chinese: example.chinese ?? '',
    senseNumber: example.senseNumber ?? null,
    source: example.source ?? '',
    // 規範のキー（BASIC／APPLIED）。未設定のときだけ「やさしい例文」に寄せる
    level: example.level === 'APPLIED' ? 'APPLIED' : 'BASIC'
  }
}

function toPattern(pattern: JpnPattern, index: number): DemoDetailPattern {
  return {
    id: `pattern-${index}`,
    pattern: pattern.pattern ?? '',
    reading: pattern.reading ?? '',
    chinese: pattern.chinese ?? '',
    example: pattern.example ?? '',
    exampleChinese: pattern.exampleChinese ?? ''
  }
}

/** 会話は `dialogs[].lines[]` の 2 層のまま移す。 */
function toDialog(dialog: JpnDialog, index: number): DemoDetailDialog {
  return {
    id: `dialog-${index}`,
    scene: dialog.scene ?? '',
    lines: (dialog.lines ?? []).map((line, lineIndex) => ({
      id: `dialog-${index}-line-${lineIndex}`,
      speaker: line.speaker ?? '',
      japanese: line.japanese ?? '',
      chinese: line.chinese ?? ''
    }))
  }
}

/**
 * 類義語。画面には「入れ替えられるか」の欄があるが、規範のキーには無いので
 * **決めつけない**（`SOMETIMES` にして、根拠が無いことを注記で示す）。
 */
function toSynonym(synonym: JpnSynonym, index: number): DemoDetailSynonym {
  return {
    id: `synonym-${index}`,
    heading: synonym.heading ?? '',
    reading: synonym.reading ?? '',
    chinese: synonym.chinese ?? '',
    shared: synonym.shared ?? '',
    difference: synonym.difference ?? '',
    usage: synonym.scene ?? '',
    interchangeable: 'SOMETIMES',
    interchangeNote: INTERCHANGEABLE_LABELS.SOMETIMES,
    exampleJapanese: '',
    exampleChinese: ''
  }
}

/** 注意の区分。規範の値（4 つ）だけをそのまま使う。 */
function toCautionKind(kind: JpnCautionKind | null): DemoCautionKind {
  switch (kind) {
    case 'GRAMMAR':
      return 'GRAMMAR'
    case 'MEANING':
      return 'MEANING'
    case 'PARTICLE':
      return 'PARTICLE'
    default:
      return 'UNNATURAL'
  }
}

function toCaution(caution: JpnCaution, index: number): DemoDetailCaution {
  return {
    id: `caution-${index}`,
    kind: toCautionKind(caution.kind),
    title: caution.title ?? '',
    wrong: caution.wrong ?? '',
    correct: caution.correct ?? '',
    reason: caution.reason ?? ''
  }
}

function toConjugation(conjugation: JpnConjugation, index: number): DemoDetailConjugation {
  return {
    id: `conjugation-${index}`,
    form: conjugation.form ?? '',
    value: conjugation.value ?? '',
    example: conjugation.example ?? ''
  }
}

function toTransitivityPair(pair: JpnTransitivityPair | null | undefined): DemoDetailTransitivityPair | null {
  if (pair === null || pair === undefined) {
    return null
  }
  const hasContent = [pair.intransitive, pair.transitive, pair.particleNote,
    pair.intransitiveExample, pair.transitiveExample].some((value) => (value ?? '') !== '')
  if (!hasContent) {
    return null
  }
  return {
    intransitive: pair.intransitive ?? '',
    transitive: pair.transitive ?? '',
    particleNote: pair.particleNote ?? '',
    intransitiveExample: pair.intransitiveExample ?? '',
    transitiveExample: pair.transitiveExample ?? ''
  }
}

/**
 * 発音。規範の `pronunciation` を使い、無ければ旧 API 互換の `pronunciations[0]` から作る。
 * 旧配列には `hint` が無いので空にする（推測で埋めない）。
 */
function toPronunciation(body: JpnWordDetailBody): DemoDetailPronunciation | null {
  const pronunciation: JpnPronunciation | null | undefined = body.pronunciation
  if (pronunciation !== null && pronunciation !== undefined && (pronunciation.reading ?? '') !== '') {
    return {
      reading: pronunciation.reading ?? '',
      accentType: pronunciation.accentType ?? null,
      accentNotation: pronunciation.accentNotation ?? null,
      hint: pronunciation.hint ?? '',
      hasAudioSample: pronunciation.hasAudioSample === true
    }
  }
  const row = (body.pronunciations ?? [])[0]
  if (row === undefined || (row.reading ?? '') === '') {
    return null
  }
  return {
    reading: row.reading ?? '',
    accentType: row.accentType ?? null,
    accentNotation: row.accentNotation ?? null,
    hint: '',
    hasAudioSample: (row.audioUrl ?? '') !== ''
  }
}

function toCollocation(collocation: JpnCollocation, index: number): DemoDetailCollocation {
  return {
    id: `collocation-${index}`,
    expression: collocation.expression ?? '',
    reading: collocation.reading ?? '',
    chinese: collocation.chinese ?? '',
    usage: collocation.usage ?? collocation.exampleJapanese ?? ''
  }
}

/** 関連語の関係。規範の値（4 つ）だけをそのまま使う。 */
function toRelatedRelation(relation: string | null): DemoRelatedRelation {
  if (relation === '類義語' || relation === '対義語' || relation === '間違えやすい' || relation === '同じ読み') {
    return relation
  }
  // 画面に「関連語」だけの区分が無いので、いちばん近い「類義語」に寄せる
  return '類義語'
}

function toRelatedWord(related: JpnRelatedWord, index: number): DemoDetailRelatedWord {
  return {
    id: `related-${index}`,
    relation: toRelatedRelation(related.relation),
    heading: related.heading ?? '',
    reading: related.reading ?? '',
    chinese: related.chinese ?? ''
  }
}

/** 使用場面。語義からの合成はやめ、規範の `usageNotes` をそのまま読む。 */
function toUsageNote(note: JpnUsageNote, index: number): DemoDetailUsageNote {
  return {
    id: `usage-${index}`,
    register: toRegister(note.register),
    politeness: toPoliteness(note.politeness),
    audience: note.audience ?? '',
    note: note.note ?? '',
    senseNumber: note.senseNumber ?? null
  }
}

function toRegister(value: string | null): DemoDetailUsageNote['register'] {
  return value === '話し言葉' || value === '書き言葉' ? value : 'どちらも'
}

function toPoliteness(value: string | null): DemoDetailUsageNote['politeness'] {
  return value === 'カジュアル' || value === '丁寧' ? value : '普通'
}

function toMemoryHint(hint: JpnMemoryHint | null | undefined): DemoDetailMemoryHint | null {
  if (hint === null || hint === undefined) {
    return null
  }
  if ((hint.hint ?? '') === '' && (hint.basis ?? '') === '') {
    return null
  }
  return { hint: hint.hint ?? '', basis: hint.basis ?? '' }
}

/** 練習の種別。規範の値（4 つ）だけをそのまま使う。 */
function toPracticeKind(kind: JpnPracticeKind | null): DemoPracticeKind {
  if (kind !== null && kind in PRACTICE_KIND_LABELS) {
    return kind as DemoPracticeKind
  }
  return 'SCENE'
}

function toPractice(practice: JpnPractice, index: number): DemoPracticeQuestion {
  return {
    id: `practice-${index}`,
    kind: toPracticeKind(practice.kind),
    question: practice.question ?? '',
    questionChinese: practice.questionChinese ?? '',
    choices: (practice.choices ?? []).map((choice) => String(choice)),
    answer: practice.answer ?? '',
    explanation: practice.explanation ?? '',
    freeWriting: practice.freeWriting === true,
    // 本物の添削は無い（デモの見本も出さない）
    demoFeedback: null
  }
}

/** 版の一覧の 1 行に出す短い説明（画面の「版の履歴」）。 */
export function detailVersionOrigin(version: JpnDetailVersion): string {
  if (version.manual) {
    return '人が作成'
  }
  const model = [version.aiProvider, version.aiModel].filter((value) => (value ?? '') !== '').join(' / ')
  return model === '' ? 'AI が作成' : `AI が作成（${model}）`
}

/** 版の段落の件数（画面に出す「例文 5 件」の一覧）。 */
export function detailVersionCountLabels(version: JpnDetailVersion): { label: string; count: number }[] {
  const counts = version.counts
  return [
    { label: '語義', count: counts.senses },
    { label: '例文', count: counts.examples },
    { label: '文型', count: counts.patterns },
    { label: '会話', count: counts.dialogs },
    { label: '類義語', count: counts.synonyms },
    { label: '注意', count: counts.cautions },
    { label: 'コロケーション', count: counts.collocations },
    { label: '関連語', count: counts.relatedWords },
    { label: '使用場面', count: counts.usageNotes },
    { label: '練習', count: counts.practices }
  ]
}
