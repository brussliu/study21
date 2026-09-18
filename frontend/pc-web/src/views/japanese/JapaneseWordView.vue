<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiError, formatIsoDateTime, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import { paginationItems } from '@/features/pagination/pagination'
import {
  QUESTION_TYPE_LABELS,
  RELATED_TYPE_LABELS,
  LEARN_STATE_BADGES,
  LEARN_STATE_LABELS,
  createJpnWord,
  deleteJpnWord,
  fetchJpnWord,
  searchJpnWords,
  updateJpnWord,
  type JpnCollection,
  type JpnWord,
  type JpnWordDetail,
  type JpnWordDetailResult,
  type JpnWordQuestion,
  type JpnWordSave,
  type LearnState
} from '@/api/japanese'
import '@/features/japanese/japanese.css'

/**
 * 日本語勉強【単語情報管理】。
 *
 * 2.0 の `japanese_word.jsp`（＋ `js/japanese_word.js`）を 2.1 のデザインで作り直した画面。
 * データは移行済みの `JPN_*` テーブル（約 9,847 語）を API で読み書きする。
 *
 * ・検索条件（キーワード・JLPT レベル・品詞・書籍・分類（From ～ To）を 1 行にまとめる）
 * ・単語一覧（操作 / JLPTレベル / 単語ID / 書籍 / 分類 / 単語 / 読み方 / 品詞 / 中国語訳 /
 *   取得状態（A・B，C，D，E）。操作はアイコンボタンだけを左端に置く。1 ページ 20/50/100 件）
 *   JLPTレベル・単語ID・書籍・取得状態は幅を詰め、空いた幅を中国語訳に回す
 * ・単語の登録・修正・削除（お気に入り・習得済の切り替えは一覧のアイコンを外したので、
 *   いまは画面から操作できない。API と単語のデータには残っている）
 * ・単語の詳細（収録・基本情報・語義・例文・発音・コロケーション・関連語・使用注意・問題を
 *   タブで切り替える。2.0 の詳細の項目をすべて出す）
 *
 * 右上の A〜E 取得（詳細情報・読み問題・文脈問題・漢字問題）は、**取得用の API がまだ無い**
 * ため無効のボタンとして置く（押せない理由は title に出す）。
 *
 * 一覧 API が持っていない情報は、画面側で次のように補っている。
 * ・書籍・分類の選択肢 … 選択肢一覧を返す API が無いので、読み込んだ単語の値から作る
 * ・品詞の選択肢 … `品詞` は `[名・他サ]` のような組み合わせのラベルで 65 種類あるため、
 *   代表の区分だけを並べる（絞り込みは部分一致なので「名」「サ」で複合ラベルにも当たる）
 * ・分類（To）… API は `category` を 1 つしか受け取らないため、送るのは From だけ
 * ・中国語訳・取得状態（A・B／C／D／E）… 一覧 API に項目が無いので「—」「未取得」を出す
 */

const toast = useToast()

/** A〜E の取得ボタン（取得用の API がまだ無いため、押せないボタンとして置く）。 */
const ACQUIRE_ACTIONS: { key: string; short: string; label: string; icon: string }[] = [
  { key: 'AB', short: 'A・B', label: '詳細情報取得（A・B）', icon: 'info' },
  { key: 'C', short: 'C', label: '読み問題取得（C）', icon: 'book-open' },
  { key: 'D', short: 'D', label: '文脈問題取得（D）', icon: 'note' },
  { key: 'E', short: 'E', label: '漢字問題取得（E）', icon: 'pen' }
]
/** 取得ボタンが押せない理由（取得用の API が無い）。 */
const ACQUIRE_UNAVAILABLE_TITLE = '取得用の API はまだありません。'
/** 取得状態の列が「未取得」なのは、一覧 API が取得状態を返さないため。 */
const ACQUIRE_COLUMN_TITLE = '取得状態は一覧 API が返さないため「未取得」を表示しています。'
/** 分類（To）が絞り込みに使われない理由。 */
const CATEGORY_TO_TITLE = '分類（To）は API が未対応のため、絞り込みには使われません（送信するのは From だけです）。'

/* ---------- 検索条件 ---------- */

/** JLPT レベルの選択肢（2.0 の日本語単語のレベル区分）。 */
const JLPT_OPTIONS = ['N1', 'N2', 'N3', 'N4', 'N5']

/**
 * 品詞の選択肢（2.0 の教材が使う日本語の文法区分）。
 *
 * `JPN_単語情報.品詞` の実データは `[名]` / `[名・他サ]` のような**組み合わせ**のラベルで
 * 65 種類あるため、全部を並べずに**代表の区分**だけを選べるようにする。
 * 絞り込みは API の部分一致（`ILIKE '%…%'`）なので「名」「サ」で複合ラベルにも当たる。
 */
const PART_OPTIONS = [
  '名', '代', '副', '形', '形動', '連体', '接', '感', '助', '接頭', '接尾',
  '五段', '下一段', '上一段', 'サ変'
]

/** 単語の状態（`stateCode`）。 */
type WordState = 'ACTIVE' | 'INACTIVE'
const STATE_OPTIONS: WordState[] = ['ACTIVE', 'INACTIVE']
const STATE_LABELS: Record<WordState, string> = { ACTIVE: '有効', INACTIVE: '無効' }

/** 1 ページの件数。 */
const PAGE_SIZES = [20, 50, 100]

const filters = reactive({
  keyword: '',
  jlpt: '',
  part: '',
  book: '',
  /** 分類の範囲（From／To）。API に渡すのは From だけ。 */
  categoryFrom: '',
  categoryTo: ''
})

const isFiltered = computed(() =>
  filters.keyword.trim() !== '' || filters.jlpt !== '' || filters.part.trim() !== ''
  || filters.book !== '' || filters.categoryFrom !== '' || filters.categoryTo !== '')

/**
 * 書籍・分類の選択肢（「（すべて）」は含めない）。
 *
 * 選択肢一覧を返す API が無いので、**読み込んだ単語に出てきた値**を集めて作る
 * （ページをめくったり検索したりするほど増える）。API に一覧の口ができたら差し替える。
 */
const bookOptions = ref<string[]>([])
const categoryOptions = ref<string[]>([])

/** 読み込んだ一覧から、書籍・分類の選択肢を足す（並びは名前順）。 */
function collectFilterOptions(rows: JpnWord[]): void {
  const books = new Set(bookOptions.value)
  const categories = new Set(categoryOptions.value)
  for (const row of rows) {
    if (row.book !== null && row.book !== '') {
      books.add(row.book)
    }
    if (row.category !== null && row.category !== '') {
      categories.add(row.category)
    }
  }
  bookOptions.value = [...books].sort((left, right) => left.localeCompare(right, 'ja'))
  categoryOptions.value = [...categories].sort((left, right) => left.localeCompare(right, 'ja'))
}

/* ---------- 一覧 ---------- */

const loading = ref(false)
const error = ref('')
const words = ref<JpnWord[]>([])
const totalElements = ref(0)
const totalPages = ref(1)
const page = ref(1)
const size = ref(PAGE_SIZES[0])
/** 更新系の処理中（二重送信を防ぐ）。 */
const busy = ref(false)

/** ページ番号は共通ロジック（現在ページの前後＋先頭・末尾、離れた部分は省略記号）。 */
const pageItems = computed(() => paginationItems(page.value, Math.max(1, totalPages.value)))

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

async function loadWords(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const response = await searchJpnWords({
      keyword: filters.keyword.trim() === '' ? undefined : filters.keyword.trim(),
      jlpt: filters.jlpt === '' ? undefined : filters.jlpt,
      part: filters.part.trim() === '' ? undefined : filters.part.trim(),
      book: filters.book === '' ? undefined : filters.book,
      // API が受け取る分類は 1 つだけなので、送るのは From（To は画面の入力だけ）
      category: filters.categoryFrom === '' ? undefined : filters.categoryFrom,
      page: page.value,
      size: size.value
    })
    words.value = response.data.items
    totalElements.value = response.data.totalElements
    totalPages.value = Math.max(1, response.data.totalPages)
    collectFilterOptions(response.data.items)
  } catch (caught) {
    error.value = messageOf(caught, '単語の一覧を取得できませんでした。')
    words.value = []
    totalElements.value = 0
    totalPages.value = 1
  } finally {
    loading.value = false
  }
}

/** 【検索】条件を適用して 1 ページ目から読み直す。 */
function search(): void {
  page.value = 1
  void loadWords()
}

/** 【リセット】条件を空にして 1 ページ目から読み直す。 */
function reset(): void {
  filters.keyword = ''
  filters.jlpt = ''
  filters.part = ''
  filters.book = ''
  filters.categoryFrom = ''
  filters.categoryTo = ''
  page.value = 1
  void loadWords()
}

function goto(target: number): void {
  const next = Math.min(Math.max(1, target), Math.max(1, totalPages.value))
  if (next === page.value) return
  page.value = next
  void loadWords()
}

/** 件数を変えたら 1 ページ目に戻す。 */
function changeSize(): void {
  page.value = 1
  void loadWords()
}



/** 単語を削除する（語義・例文・問題などは DB の CASCADE で一緒に消える）。 */
async function removeWord(row: JpnWord): Promise<void> {
  if (busy.value) return
  if (!window.confirm(`「${row.word}」を削除します。語義・例文・問題も一緒に削除されます。よろしいですか？`)) {
    return
  }
  busy.value = true
  try {
    const response = await deleteJpnWord(row.wordId)
    toast.success(response.data.message)
    // 最後の 1 件を消したときは前のページへ戻る
    if (words.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await loadWords()
  } catch (caught) {
    toast.danger(messageOf(caught, '単語を削除できませんでした。'))
  } finally {
    busy.value = false
  }
}

/* ---------- 単語の登録・修正ダイアログ ---------- */

const wordDialogOpen = ref(false)
const editingId = ref<number | null>(null)
/** 修正時に「表示していた version」（楽観ロック用にそのまま送る）。 */
const editingVersion = ref<number | null>(null)

const wordForm = reactive({
  word: '',
  reading: '',
  jlptLevel: '',
  partOfSpeech: '',
  stateCode: 'ACTIVE' as WordState,
  note: ''
})

const fieldErrors = reactive<Record<string, string>>({})

function clearErrors(errors: Record<string, string>): void {
  for (const key of Object.keys(errors)) {
    delete errors[key]
  }
}

/** フォームに初期値を入れる（row が null なら新規）。 */
function fillForm(row: JpnWord | null): void {
  wordForm.word = row?.word ?? ''
  wordForm.reading = row?.reading ?? ''
  wordForm.jlptLevel = row?.jlptLevel ?? ''
  wordForm.partOfSpeech = row?.partOfSpeech ?? ''
  wordForm.stateCode = row?.stateCode === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE'
  wordForm.note = row?.note ?? ''
}

function openCreate(): void {
  editingId.value = null
  editingVersion.value = null
  fillForm(null)
  clearErrors(fieldErrors)
  wordDialogOpen.value = true
}

function openEdit(row: JpnWord): void {
  editingId.value = row.wordId
  editingVersion.value = row.version
  fillForm(row)
  clearErrors(fieldErrors)
  wordDialogOpen.value = true
}

function closeWordDialog(): void {
  wordDialogOpen.value = false
  clearErrors(fieldErrors)
}

async function saveWord(): Promise<void> {
  if (busy.value) return
  clearErrors(fieldErrors)

  const word = wordForm.word.trim()
  if (word === '') {
    fieldErrors.word = '見出し語を入力してください。'
  }
  if (Object.keys(fieldErrors).length > 0) {
    toast.warning('入力内容を確認してください。')
    return
  }

  busy.value = true
  try {
    const body: JpnWordSave = {
      word,
      reading: wordForm.reading.trim() === '' ? undefined : wordForm.reading.trim(),
      jlptLevel: wordForm.jlptLevel === '' ? undefined : wordForm.jlptLevel,
      partOfSpeech: wordForm.partOfSpeech.trim() === '' ? undefined : wordForm.partOfSpeech.trim(),
      stateCode: wordForm.stateCode,
      note: wordForm.note.trim() === '' ? undefined : wordForm.note.trim(),
      version: editingVersion.value ?? undefined
    }
    const response = editingId.value === null
      ? await createJpnWord(body)
      : await updateJpnWord(editingId.value, body)
    toast.success(response.data.message)
    wordDialogOpen.value = false
    await loadWords()
  } catch (caught) {
    toast.danger(messageOf(caught, '単語を保存できませんでした。'))
  } finally {
    busy.value = false
  }
}

/* ---------- 単語の詳細ダイアログ ---------- */

type SenseItem = NonNullable<JpnWordDetail['detail']['senses']>[number]
type ExampleItem = NonNullable<JpnWordDetail['detail']['examples']>[number]
type PronunciationItem = NonNullable<JpnWordDetail['detail']['pronunciations']>[number]
type CollocationItem = NonNullable<JpnWordDetail['detail']['collocations']>[number]
type RelatedWordItem = NonNullable<JpnWordDetail['detail']['relatedWords']>[number]
type CautionItem = NonNullable<JpnWordDetail['detail']['cautions']>[number]

/** 詳細ダイアログのタブ。2.0 の詳細（本体 ＋ 6 子テーブル）を 1 つずつ見せる。 */
interface DetailTab {
  /** 中身の識別子（`data-jp-detail-tab` / `data-jp-detail-panel` に使う）。 */
  key: string
  label: string
  /** タブの id（中身の `aria-labelledby` から指す）。 */
  tabId: string
  /** 中身の id（タブの `aria-controls` から指す）。 */
  panelId: string
}

const DETAIL_TABS: DetailTab[] = [
  { key: 'collections', label: '収録', tabId: 'jpDetailTab-collections', panelId: 'jpDetailPanel-collections' },
  { key: 'basic', label: '基本情報', tabId: 'jpDetailTab-basic', panelId: 'jpDetailPanel-basic' },
  { key: 'senses', label: '語義', tabId: 'jpDetailTab-senses', panelId: 'jpDetailPanel-senses' },
  { key: 'examples', label: '例文', tabId: 'jpDetailTab-examples', panelId: 'jpDetailPanel-examples' },
  { key: 'pronunciations', label: '発音', tabId: 'jpDetailTab-pronunciations', panelId: 'jpDetailPanel-pronunciations' },
  { key: 'collocations', label: 'コロケーション', tabId: 'jpDetailTab-collocations', panelId: 'jpDetailPanel-collocations' },
  { key: 'relatedWords', label: '関連語', tabId: 'jpDetailTab-relatedWords', panelId: 'jpDetailPanel-relatedWords' },
  { key: 'cautions', label: '使用注意', tabId: 'jpDetailTab-cautions', panelId: 'jpDetailPanel-cautions' },
  { key: 'questions', label: '問題', tabId: 'jpDetailTab-questions', panelId: 'jpDetailPanel-questions' },
]

/**
 * 「項目（ラベルと値）」の一覧に出せる形。
 * 画面はこの形に落としてから描くので、表示の抜け（＝項目の書き忘れ）が起きにくい。
 */
type DetailFieldValue = string | number | boolean | null | undefined

/**
 * 値を表示用の文字列にする関数（真偽値・区分コード・日時の日本語表記）。
 * 呼ぶ側（`fieldValue`）が「値が入っている」ことを確かめてから渡すので、引数は string。
 */
type FieldFormatter = (value: string) => string

interface DetailField {
  label: string
  value: DetailFieldValue
  /** 真偽値や区分コードの日本語表記（未指定ならそのまま文字にする）。 */
  format?: FieldFormatter
}

/** 1 つの項目を作る。 */
function detailField(label: string, value: DetailFieldValue, format?: FieldFormatter): DetailField {
  return { label, value, format }
}

/** 未入力の項目の表示（2.0 の画面と同じ「—」）。 */
const EMPTY_VALUE = '—'

const detailOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<JpnWordDetailResult | null>(null)

/** 選択中のタブ（key）。開くたびに先頭（収録）へ戻す。 */
const activeDetailTab = ref<string>(DETAIL_TABS[0]!.key)

const detailWord = computed<JpnWord | null>(() => detail.value?.word ?? null)
const detailCollections = computed<JpnCollection[]>(() => detail.value?.collections ?? [])
const detailQuestions = computed<JpnWordQuestion[]>(() => detail.value?.questions ?? [])
/** 詳細そのもの（詳細ID・内容版数・AI プロバイダなどを持つ行）。未取得なら null。 */
const detailView = computed<JpnWordDetail | null>(() => detail.value?.detail ?? null)
/** 詳細（語義・例文・発音・コロケーション・関連語・使用注意）本体。未取得なら null。 */
const detailBody = computed<JpnWordDetail['detail'] | null>(() => detailView.value?.detail ?? null)

const senses = computed<SenseItem[]>(() => detailBody.value?.senses ?? [])
const examples = computed<ExampleItem[]>(() => detailBody.value?.examples ?? [])
const pronunciations = computed<PronunciationItem[]>(() => detailBody.value?.pronunciations ?? [])
const collocations = computed<CollocationItem[]>(() => detailBody.value?.collocations ?? [])
const relatedWords = computed<RelatedWordItem[]>(() => detailBody.value?.relatedWords ?? [])
const cautions = computed<CautionItem[]>(() => detailBody.value?.cautions ?? [])

/* --- 2.0 の列名をそのままラベルにした項目（詳細ダイアログに全部出す） --- */

/** 単語そのものの項目（母表）。詳細がまだ無くても出す。 */
function basicWordFields(row: JpnWord): DetailField[] {
  return [
    detailField('見出し語', row.word),
    detailField('読み', row.reading),
    detailField('JLPTレベル', row.jlptLevel),
    detailField('品詞', row.partOfSpeech),
    detailField('状態', row.stateCode, (value) => STATE_LABELS[value as WordState] ?? String(value)),
    detailField('備考', row.note),
    detailField('バージョン', row.version)
  ]
}

/** 学習状況（アカウントごと）。2.0 の 単語情報管理でも語ごとに見ていた。 */
function basicStudyFields(row: JpnWord): DetailField[] {
  return [
    detailField('学習状態', row.learnState, (value) => learnStateLabel(value as LearnState)),
    detailField('習得度', `${row.mastery}%`),
    detailField('回答数', row.answeredCount),
    detailField('正解数', row.correctCount),
    detailField('お気に入り', row.favorite, yesNo),
    detailField('習得済', row.learned, yesNo),
    detailField('最終学習', row.lastStudiedAt, dateTimeLabel),
    detailField('次回復習', row.nextReviewAt, dateTimeLabel)
  ]
}

/** 詳細の取得情報（2.0 の STY_日本語単語詳細情報 本体の列のうち、メタ情報）。 */
function basicMetaFields(view: JpnWordDetail): DetailField[] {
  return [
    detailField('詳細ID', view.detailId),
    detailField('内容版数', view.contentVersion),
    detailField('AIプロバイダ', view.aiProvider),
    detailField('AIモデル', view.aiModel),
    detailField('取得日時', view.fetchedAt, dateTimeLabel),
    detailField('構造化スキーマ版', view.detail.structuredSchemaVersion),
    detailField('手動修正済', view.detail.manuallyCorrected, yesNo)
  ]
}

/** AI が書いた語の説明（2.0 の詳細情報 本体の列）。 */
function basicDescriptionFields(body: JpnWordDetail['detail']): DetailField[] {
  return [
    detailField('詳細のJLPTレベル', body.jlptLevel),
    detailField('詳細の品詞', body.partOfSpeech),
    detailField('活用種類', body.conjugation),
    detailField('自他区分', body.transitivity),
    detailField('重要度', body.importance),
    detailField('代表中国語意味', body.chineseMeaning),
    detailField('日本語説明', body.descriptionJa),
    detailField('中国語説明', body.descriptionZh)
  ]
}

/** 収録（教材のどこに載っているか）。 */
function collectionFields(collection: JpnCollection): DetailField[] {
  return [
    detailField('書籍', collection.book),
    detailField('分類', collection.category),
    detailField('レベル', collection.level),
    detailField('単語SEQ', collection.wordSeq),
    detailField('掲載見出し語', collection.listedWord),
    detailField('掲載読み', collection.listedReading),
    detailField('掲載品詞', collection.listedPartOfSpeech),
    detailField('掲載中国語意味', collection.chineseMeaning)
  ]
}

/** 語義（2.0 の STY_日本語単語詳細_語義情報）。 */
function senseFields(sense: SenseItem): DetailField[] {
  return [
    detailField('意味（日本語）', sense.japanese),
    detailField('意味（中国語）', sense.chinese),
    detailField('使用場面', sense.context),
    detailField('文体', sense.style),
    detailField('補足説明（日本語）', sense.noteJapanese),
    detailField('補足説明（中国語）', sense.noteChinese)
  ]
}

/** 例文。 */
function exampleFields(example: ExampleItem): DetailField[] {
  return [
    detailField('例文（日本語）', example.japanese),
    detailField('例文読み', example.reading),
    detailField('例文（中国語）', example.chinese),
    detailField('文脈意味（日本語）', example.contextJapanese),
    detailField('文脈意味（中国語）', example.contextChinese),
    detailField('出典', example.source),
    detailField('語義番号', example.senseNumber)
  ]
}

/** 発音。 */
function pronunciationFields(pronunciation: PronunciationItem): DetailField[] {
  return [
    detailField('読み', pronunciation.reading),
    detailField('アクセント表記', pronunciation.accentNotation),
    detailField('アクセント型', pronunciation.accentType),
    detailField('モーラ数', pronunciation.moraCount),
    detailField('音声URL', pronunciation.audioUrl),
    detailField('音声プロバイダ', pronunciation.audioProvider)
  ]
}

/** コロケーション（よく使う言い回し）。 */
function collocationFields(collocation: CollocationItem): DetailField[] {
  return [
    detailField('表現', collocation.expression),
    detailField('読み', collocation.reading),
    detailField('中国語', collocation.chinese),
    detailField('例文（日本語）', collocation.exampleJapanese),
    detailField('例文（中国語）', collocation.exampleChinese)
  ]
}

/** 関連語（類義語・対義語・間違えやすい語）。 */
function relatedWordFields(related: RelatedWordItem): DetailField[] {
  return [
    detailField('見出し', related.heading),
    detailField('読み', related.reading),
    detailField('中国語', related.chinese),
    detailField('違い（日本語）', related.differenceJapanese),
    detailField('違い（中国語）', related.differenceChinese),
    detailField('E問題の候補', related.eCandidate, yesNo)
  ]
}

/** 使用注意。 */
function cautionFields(caution: CautionItem): DetailField[] {
  return [
    detailField('注意の種類', caution.noteType),
    detailField('注意（日本語）', caution.japanese),
    detailField('注意（中国語）', caution.chinese),
    detailField('誤用例', caution.wrongExample),
    detailField('正用例', caution.correctExample)
  ]
}

/**
 * 問題（C〜E）。
 * 2.0 の 単語情報管理 は問題の有無だけだったが、2.1 の詳細 API は語に紐づく問題を返すので、
 * 選択肢の数まで見せる（問題の文面・正解は 2.0 のテスト実施画面と同じ項目）。
 */
function questionFields(question: JpnWordQuestion): DetailField[] {
  return [
    detailField('問題番号', question.questionNo),
    detailField('問題種別', question.questionType, questionTypeLabel),
    detailField('問題文（日本語）', question.questionText),
    detailField('正解値', question.correctValue),
    detailField('選択肢', `${question.choiceCount} 件`)
  ]
}

/** タブの件数（数えられる中身を持つタブだけ。基本情報は該当なし＝バッジを付けない）。 */
function detailTabCount(key: string): number | null {
  switch (key) {
    case 'collections':
      return detailCollections.value.length
    case 'senses':
      return senses.value.length
    case 'examples':
      return examples.value.length
    case 'pronunciations':
      return pronunciations.value.length
    case 'collocations':
      return collocations.value.length
    case 'relatedWords':
      return relatedWords.value.length
    case 'cautions':
      return cautions.value.length
    case 'questions':
      return detailQuestions.value.length
    default:
      return null
  }
}

/** 項目の値を表示用の文字列にする（null・空文字は「—」、真偽値や区分は日本語）。 */
function fieldValue(field: DetailField): string {
  const value = field.value
  if (value === null || value === undefined || value === '') {
    return EMPTY_VALUE
  }
  const text = field.format ? field.format(String(value)) : String(value)
  return text === '' ? EMPTY_VALUE : text
}

/** はい／いいえ（2.0 の真偽値の列は「はい」「いいえ」で見せていた）。 */
function yesNo(value: DetailFieldValue): string {
  return value === true || value === 'true' ? 'はい' : 'いいえ'
}

async function openDetail(row: JpnWord): Promise<void> {
  detailOpen.value = true
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  // 前の単語で見ていたタブを引き継がない（毎回「収録」から見せる）
  activeDetailTab.value = DETAIL_TABS[0]!.key
  try {
    const response = await fetchJpnWord(row.wordId)
    detail.value = response.data
  } catch (caught) {
    detailError.value = messageOf(caught, '単語の詳細を取得できませんでした。')
  } finally {
    detailLoading.value = false
  }
}

function closeDetail(): void {
  detailOpen.value = false
  detail.value = null
  detailError.value = ''
}

function selectDetailTab(key: string): void {
  activeDetailTab.value = key
}


/* ---------- 表示の小物 ---------- */

/** 学習状態のバッジ（API の定義をそのまま使う）。 */
function learnStateBadge(state: LearnState): string {
  return LEARN_STATE_BADGES[state] ?? 'badge--neutral'
}

function learnStateLabel(state: LearnState): string {
  return LEARN_STATE_LABELS[state] ?? state
}

/** 問題種別の説明（A〜E）。未知の種別はコードのまま出す。 */
function questionTypeLabel(type: string): string {
  return QUESTION_TYPE_LABELS[type] ?? type
}

function dateTimeLabel(iso: string | null): string {
  return iso === null ? '—' : formatIsoDateTime(iso)
}

onMounted(() => {
  void loadWords()
})
</script>

<template>
  <div class="jp-page">
    <!-- 検索条件（キーワード・JLPT レベル・品詞・書籍・分類（From／To）） -->
    <div class="search-panel">
      <div class="search-panel__head">
        <h3 class="search-panel__title"><AppIcon name="search" size="sm" /> 検索条件</h3>
        <div class="jp-head-actions">
          <div class="search-panel__actions">
            <button type="button" class="btn btn--primary" data-jp-search :disabled="loading" @click="search">
              <AppIcon name="search" size="sm" /> 検索
            </button>
            <button type="button" class="btn btn--primary" data-jp-add @click="openCreate">
              <AppIcon name="plus" size="sm" /> 新規
            </button>
            <button type="button" class="btn btn--secondary" data-jp-reset :disabled="loading" @click="reset">
              <AppIcon name="rotate" size="sm" /> リセット
            </button>
          </div>
          <!-- 右上の A〜E 取得。取得用の API がまだ無いので押せないボタンとして置く -->
          <div class="search-panel__actions jp-head-actions__acquire" data-jp-acquire-actions>
            <button
              v-for="action in ACQUIRE_ACTIONS" :key="action.key"
              type="button" class="btn btn--secondary" :data-jp-acquire-action="action.key"
              disabled :title="ACQUIRE_UNAVAILABLE_TITLE"
            >
              <AppIcon :name="action.icon" size="sm" /> {{ action.label }}
            </button>
          </div>
        </div>
      </div>
      <div class="filters">
        <!-- 条件は 1 行にまとめる（狭い画面では折り返して縦に積む）。
             伸びるのはキーワードだけにして、ほかの条件は内容ぶんの幅にする -->
        <div class="filters__row jp-filters__row">
          <span class="filter-item filter-item--grow jp-filters__keyword">
            <span class="filter-item__label">キーワード：</span>
            <input
              v-model="filters.keyword" class="input" type="search" data-jp-filter="keyword"
              placeholder="見出し語の一部" @keyup.enter="search"
            >
          </span>
          <span class="filter-item jp-filters__jlpt">
            <span class="filter-item__label">JLPT：</span>
            <select v-model="filters.jlpt" class="select" data-jp-filter="jlpt" aria-label="JLPTレベル">
              <option value="">すべて</option>
              <option v-for="option in JLPT_OPTIONS" :key="option" :value="option">{{ option }}</option>
            </select>
          </span>
          <span class="filter-item jp-filters__part">
            <span class="filter-item__label">品詞：</span>
            <select v-model="filters.part" class="select" data-jp-filter="part" aria-label="品詞">
              <option value="">（すべて）</option>
              <option v-for="option in PART_OPTIONS" :key="option" :value="option">{{ option }}</option>
            </select>
          </span>
          <span class="filter-item jp-filters__book">
            <span class="filter-item__label">書籍：</span>
            <select v-model="filters.book" class="select" data-jp-filter="book" aria-label="書籍">
              <option value="">（すべて）</option>
              <option v-for="option in bookOptions" :key="option" :value="option">{{ option }}</option>
            </select>
          </span>
          <!-- 分類の範囲は 1 つのまとまりにして、間を「～」で示す -->
          <span class="filter-item" data-jp-filter-range>
            <span class="filter-item__label">分類：</span>
            <span class="range-input">
              <select
                v-model="filters.categoryFrom" class="select" data-jp-filter="categoryFrom"
                aria-label="分類（From）"
              >
                <option value="">（すべて）</option>
                <option v-for="option in categoryOptions" :key="option" :value="option">{{ option }}</option>
              </select>
              <span class="range-input__sep" aria-hidden="true">～</span>
              <select
                v-model="filters.categoryTo" class="select" data-jp-filter="categoryTo"
                aria-label="分類（To）" :title="CATEGORY_TO_TITLE"
              >
                <option value="">（すべて）</option>
                <option v-for="option in categoryOptions" :key="option" :value="option">{{ option }}</option>
              </select>
            </span>
          </span>
        </div>
      </div>
      <!-- 選べるのに効かない条件は、その理由を画面にも書いておく -->
      <p class="jp-hint" data-jp-filter-note>
        分類（To）は API が未対応のため、絞り込みに使われるのは 分類（From）だけです。
      </p>
    </div>

    <!-- 単語一覧 -->
    <section class="card">
      <div class="card__header">
        <h2 class="card__title"><AppIcon name="list" size="sm" /> 単語一覧</h2>
        <span class="cell-muted" data-jp-count>全 {{ totalElements }} 件</span>
        <!-- 検索条件が効いているときだけ、その一覧が絞り込み結果だと分かるようにする -->
        <span v-if="isFiltered" class="jp-hint" data-jp-filtered-count>絞り込み中</span>
        <div class="search-panel__actions">
          <button type="button" class="btn btn--secondary btn--sm" data-jp-refresh :disabled="loading" @click="loadWords">
            <AppIcon name="rotate" size="sm" /> 再読み込み
          </button>
        </div>
      </div>

      <p v-if="error" class="alert alert--danger">{{ error }}</p>
      <p v-else-if="loading" class="jp-page__loading">読み込んでいます...</p>
      <p v-else-if="words.length === 0" class="jp-page__empty" data-jp-empty>該当する単語がありません。</p>

      <div v-else class="table-wrap">
        <table class="data-table jp-words-table" data-jp-words>
          <thead>
            <tr>
              <th class="col-actions">操作</th>
              <th class="col-jp-jlpt">JLPTレベル</th>
              <th class="col-jp-word-id">単語ID</th>
              <th class="col-jp-book">書籍</th>
              <th class="col-jp-category">分類</th>
              <th class="col-jp-word">単語</th>
              <th class="col-jp-reading">読み方</th>
              <th class="col-jp-part">品詞</th>
              <!-- 中国語訳は幅を取って読みやすくする（ほかの列を詰めて空きを作る） -->
              <th class="col-jp-chinese">中国語訳</th>
              <th class="col-jp-acquire">取得状態（A・B，C，D，E）</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in words" :key="row.wordId" :data-jp-word-row="row.wordId">
              <!-- 操作はアイコンだけ（意味は title と aria-label で伝える） -->
              <td class="row-actions jp-row-actions">
                <button
                  type="button" class="btn btn--icon btn--sm" title="詳細" aria-label="詳細"
                  data-jp-detail @click="openDetail(row)"
                >
                  <AppIcon name="eye" size="sm" class="icon--view" />
                </button>
                <button
                  type="button" class="btn btn--icon btn--sm" title="修正" aria-label="修正"
                  data-jp-edit @click="openEdit(row)"
                >
                  <AppIcon name="edit" size="sm" class="icon--edit" />
                </button>
                <button
                  type="button" class="btn btn--icon btn--sm is-danger" :disabled="busy"
                  title="削除" aria-label="削除"
                  data-jp-delete @click="removeWord(row)"
                >
                  <AppIcon name="trash" size="sm" />
                </button>
              </td>
              <td>{{ row.jlptLevel ?? '—' }}</td>
              <td class="cell-muted">{{ row.wordId }}</td>
              <td>{{ row.book ?? '—' }}</td>
              <td>{{ row.category ?? '—' }}</td>
              <td><span class="jp-word">{{ row.word }}</span></td>
              <td>{{ row.reading === '' ? '—' : row.reading }}</td>
              <td>{{ row.partOfSpeech ?? '—' }}</td>
              <!-- 中国語訳は一覧 API が返さないため、今は「—」だけを出す -->
              <td class="cell-muted" data-jp-chinese>—</td>
              <!-- 取得状態（A・B／C／D／E）も一覧 API が返さないため「未取得」を出す -->
              <td data-jp-acquire :title="ACQUIRE_COLUMN_TITLE">
                <span class="jp-acquire">
                  <span v-for="action in ACQUIRE_ACTIONS" :key="action.key" class="jp-acquire__item">
                    <span class="jp-acquire__label">{{ action.short }}</span>未取得
                  </span>
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="error === '' && !loading && totalElements > 0" class="pagination">
        <span class="pagination__info">全 {{ totalElements }} 件（{{ page }} / {{ totalPages }} ページ）</span>
        <!-- 件数はページングの左隣に置く（画面共通のルール） -->
        <label class="pagination__size">
          <span>件数</span>
          <select
            v-model.number="size" class="select" aria-label="1ページの件数"
            data-jp-page-size @change="changeSize"
          >
            <option v-for="option in PAGE_SIZES" :key="option" :value="option">{{ option }} 件</option>
          </select>
        </label>
        <div class="pagination__pages">
          <button type="button" class="page-btn" :disabled="page <= 1" @click="goto(page - 1)">‹</button>
          <template v-for="(item, key) in pageItems" :key="key">
            <span v-if="item === 'gap'" class="page-gap">…</span>
            <button
              v-else type="button" class="page-btn" :class="{ 'is-active': item === page }"
              @click="goto(item)"
            >
              {{ item }}
            </button>
          </template>
          <button type="button" class="page-btn" :disabled="page >= totalPages" @click="goto(page + 1)">›</button>
        </div>
      </div>
    </section>

    <!-- 単語の登録・修正 -->
    <div v-if="wordDialogOpen" class="overlay">
      <section
        class="dialog dialog--md" role="dialog" aria-modal="true"
        aria-labelledby="jpWordDialogTitle" data-jp-word-dialog
      >
        <div class="dialog__head">
          <h2 id="jpWordDialogTitle" class="dialog__title">
            <AppIcon name="edit" size="sm" /> {{ editingId === null ? '単語の登録' : '単語の修正' }}
          </h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="closeWordDialog">
            <AppIcon name="x" size="sm" />
          </button>
        </div>

        <div class="dialog__body">
          <div class="jp-dialog-grid">
            <div class="field">
              <label class="field__label" for="jpWord">見出し語<span class="jp-required">必須</span></label>
              <input
                id="jpWord" v-model="wordForm.word" class="input" type="text" maxlength="100"
                placeholder="例: 勉強" :class="{ 'is-invalid': fieldErrors.word !== undefined }"
              >
              <p v-if="fieldErrors.word" class="field__error">{{ fieldErrors.word }}</p>
            </div>
            <div class="field">
              <label class="field__label" for="jpReading">読み</label>
              <input
                id="jpReading" v-model="wordForm.reading" class="input" type="text" maxlength="100"
                placeholder="例: べんきょう"
              >
            </div>
            <div class="field">
              <label class="field__label" for="jpJlpt">JLPTレベル</label>
              <select id="jpJlpt" v-model="wordForm.jlptLevel" class="select">
                <option value="">未設定</option>
                <option v-for="option in JLPT_OPTIONS" :key="option" :value="option">{{ option }}</option>
              </select>
            </div>
            <div class="field">
              <label class="field__label" for="jpPart">品詞</label>
              <input
                id="jpPart" v-model="wordForm.partOfSpeech" class="input" type="text" maxlength="50"
                placeholder="例: 名詞"
              >
            </div>
            <div class="field">
              <label class="field__label" for="jpState">状態</label>
              <select id="jpState" v-model="wordForm.stateCode" class="select">
                <option v-for="option in STATE_OPTIONS" :key="option" :value="option">
                  {{ STATE_LABELS[option] }}
                </option>
              </select>
            </div>
            <div class="field field--wide">
              <label class="field__label" for="jpNote">備考</label>
              <textarea id="jpNote" v-model="wordForm.note" class="textarea" rows="3" maxlength="2000"></textarea>
            </div>
          </div>
          <p class="jp-hint">
            見出し語は必須です。読み・JLPTレベル・品詞は一覧の絞り込みと詳細表示に使います。
            状態を「無効」にすると、単語テストの出題対象から外れます。
          </p>
        </div>

        <div class="dialog__foot">
          <button type="button" class="btn btn--secondary" data-jp-word-cancel @click="closeWordDialog">
            キャンセル
          </button>
          <button type="button" class="btn btn--primary" data-jp-word-save :disabled="busy" @click="saveWord">
            <AppIcon name="check" size="sm" /> {{ busy ? '保存中...' : '保存' }}
          </button>
        </div>
      </section>
    </div>

    <!-- 単語の詳細 -->
    <div v-if="detailOpen" class="overlay">
      <section
        class="dialog dialog--lg" role="dialog" aria-modal="true"
        aria-labelledby="jpDetailDialogTitle" data-jp-detail-dialog
      >
        <div class="dialog__head">
          <h2 id="jpDetailDialogTitle" class="dialog__title">
            <AppIcon name="eye" size="sm" /> 単語の詳細
          </h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="closeDetail">
            <AppIcon name="x" size="sm" />
          </button>
        </div>

        <div class="dialog__body jp-detail__body">
          <p v-if="detailError" class="alert alert--danger">{{ detailError }}</p>
          <p v-else-if="detailLoading" class="jp-page__loading">読み込んでいます...</p>

          <div v-else-if="detailWord" class="jp-detail">
            <div class="jp-detail__head">
              <span class="jp-detail__word">{{ detailWord.word }}</span>
              <span class="jp-detail__reading">{{ detailWord.reading === '' ? '—' : detailWord.reading }}</span>
              <span v-if="detailWord.jlptLevel" class="badge badge--neutral">{{ detailWord.jlptLevel }}</span>
              <span v-if="detailWord.partOfSpeech" class="badge badge--outline">{{ detailWord.partOfSpeech }}</span>
              <span class="badge" :class="learnStateBadge(detailWord.learnState)">
                {{ learnStateLabel(detailWord.learnState) }}
              </span>
            </div>

            <!-- 2.0 の詳細（本体 ＋ 6 子テーブル）と収録・問題をタブで分けて見せる。
                 タブは 2.0 の子テーブルと同じ並び（収録 → 基本情報 → 語義 → … → 問題） -->
            <div class="jp-detail__layout">
              <div class="tabs jp-detail__tabs" role="tablist" aria-orientation="vertical" data-jp-detail-tabs>
                <button
                  v-for="tab in DETAIL_TABS" :key="tab.key" v-bind="{ id: tab.tabId }"
                  type="button" class="tabs__tab jp-detail__tab"
                  :class="{ 'is-active': activeDetailTab === tab.key }"
                  :data-jp-detail-tab="tab.key" role="tab"
                  :aria-selected="activeDetailTab === tab.key" :aria-controls="tab.panelId"
                  @click="selectDetailTab(tab.key)"
                >
                  {{ tab.label }}
                  <span
                    v-if="detailTabCount(tab.key) !== null" class="jp-section__count"
                    data-jp-detail-count
                  >
                    {{ detailTabCount(tab.key) }}
                  </span>
                </button>
              </div>

              <div class="jp-detail__panels">
                <!-- 収録（教材のどこに載っているか） -->
                <section
                  v-show="activeDetailTab === 'collections'" id="jpDetailPanel-collections" class="jp-section"
                  data-jp-detail-panel="collections" data-jp-detail-section="collections"
                  role="tabpanel" aria-labelledby="jpDetailTab-collections"
                >
                  <h3 class="jp-section__title">
                    収録 <span class="jp-section__count">{{ detailCollections.length }} 件</span>
                  </h3>
                  <template v-if="detailCollections.length > 0">
                    <article
                      v-for="collection in detailCollections" :key="collection.collectionId"
                      class="jp-sense" :data-jp-collection="collection.collectionId"
                    >
                      <div class="jp-sense__head">
                        <span class="jp-sense__number">{{ collection.collectionId }}</span>
                        <span class="jp-sense__text">{{ collection.book }}</span>
                        <span class="badge badge--outline">{{ collection.category }}</span>
                      </div>
                      <dl class="jp-detail-fields">
                        <div
                          v-for="field in collectionFields(collection)" :key="field.label"
                          class="jp-detail-field" :data-jp-detail-field="field.label"
                        >
                          <dt class="jp-detail-field__label">{{ field.label }}</dt>
                          <dd class="jp-detail-field__value">{{ fieldValue(field) }}</dd>
                        </div>
                      </dl>
                    </article>
                  </template>
                  <p v-else class="jp-hint">まだ登録されていません。</p>
                </section>

                <!-- 基本情報（母表 ＋ 学習状況 ＋ 詳細の取得情報 ＋ AI の説明） -->
                <section
                  v-show="activeDetailTab === 'basic'" id="jpDetailPanel-basic" class="jp-section"
                  data-jp-detail-panel="basic"
                  role="tabpanel" aria-labelledby="jpDetailTab-basic"
                >
                  <h3 class="jp-section__title">単語</h3>
                  <dl class="jp-detail-fields">
                    <div
                      v-for="field in basicWordFields(detailWord)" :key="field.label"
                      class="jp-detail-field" :data-jp-detail-field="field.label"
                    >
                      <dt class="jp-detail-field__label">{{ field.label }}</dt>
                      <dd class="jp-detail-field__value">{{ fieldValue(field) }}</dd>
                    </div>
                  </dl>

                  <h3 class="jp-section__title">学習状況</h3>
                  <dl class="jp-detail-fields">
                    <div
                      v-for="field in basicStudyFields(detailWord)" :key="field.label"
                      class="jp-detail-field" :data-jp-detail-field="field.label"
                    >
                      <dt class="jp-detail-field__label">{{ field.label }}</dt>
                      <dd class="jp-detail-field__value">{{ fieldValue(field) }}</dd>
                    </div>
                  </dl>

                  <template v-if="detailView">
                    <h3 class="jp-section__title">詳細の取得情報</h3>
                    <dl class="jp-detail-fields">
                      <div
                        v-for="field in basicMetaFields(detailView)" :key="field.label"
                        class="jp-detail-field" :data-jp-detail-field="field.label"
                      >
                        <dt class="jp-detail-field__label">{{ field.label }}</dt>
                        <dd class="jp-detail-field__value">{{ fieldValue(field) }}</dd>
                      </div>
                    </dl>

                    <!-- AI が書いた語の説明（2.0 の 詳細情報 本体の列） -->
                    <h3 class="jp-section__title">AI 詳細</h3>
                    <dl class="jp-detail-fields">
                      <div
                        v-for="field in basicDescriptionFields(detailBody ?? {})" :key="field.label"
                        class="jp-detail-field" :data-jp-detail-field="field.label"
                      >
                        <dt class="jp-detail-field__label">{{ field.label }}</dt>
                        <dd class="jp-detail-field__value">{{ fieldValue(field) }}</dd>
                      </div>
                    </dl>
                  </template>
                  <p v-else class="jp-hint">この単語の詳細はまだ取得されていません。</p>
                </section>

                <!-- 語義 -->
                <section
                  v-show="activeDetailTab === 'senses'" id="jpDetailPanel-senses" class="jp-section"
                  data-jp-detail-panel="senses" data-jp-detail-section="senses"
                  role="tabpanel" aria-labelledby="jpDetailTab-senses"
                >
                  <h3 class="jp-section__title">
                    語義 <span class="jp-section__count">{{ senses.length }} 件</span>
                  </h3>
                  <template v-if="senses.length > 0">
                    <article v-for="sense in senses" :key="sense.number" class="jp-sense">
                      <div class="jp-sense__head">
                        <span class="jp-sense__number">{{ sense.number }}</span>
                        <span class="jp-sense__text">{{ sense.japanese ?? '—' }}</span>
                      </div>
                      <dl class="jp-detail-fields">
                        <div
                          v-for="field in senseFields(sense)" :key="field.label"
                          class="jp-detail-field" :data-jp-detail-field="field.label"
                        >
                          <dt class="jp-detail-field__label">{{ field.label }}</dt>
                          <dd class="jp-detail-field__value">{{ fieldValue(field) }}</dd>
                        </div>
                      </dl>
                    </article>
                  </template>
                  <p v-else class="jp-hint">まだ登録されていません。</p>
                </section>

                <!-- 例文 -->
                <section
                  v-show="activeDetailTab === 'examples'" id="jpDetailPanel-examples" class="jp-section"
                  data-jp-detail-panel="examples" data-jp-detail-section="examples"
                  role="tabpanel" aria-labelledby="jpDetailTab-examples"
                >
                  <h3 class="jp-section__title">
                    例文 <span class="jp-section__count">{{ examples.length }} 件</span>
                  </h3>
                  <template v-if="examples.length > 0">
                    <article v-for="(example, index) in examples" :key="index" class="jp-sense">
                      <div class="jp-sense__head">
                        <span class="jp-sense__number">{{ index + 1 }}</span>
                        <span class="jp-sense__text">{{ example.japanese ?? '—' }}</span>
                      </div>
                      <dl class="jp-detail-fields">
                        <div
                          v-for="field in exampleFields(example)" :key="field.label"
                          class="jp-detail-field" :data-jp-detail-field="field.label"
                        >
                          <dt class="jp-detail-field__label">{{ field.label }}</dt>
                          <dd class="jp-detail-field__value">{{ fieldValue(field) }}</dd>
                        </div>
                      </dl>
                    </article>
                  </template>
                  <p v-else class="jp-hint">まだ登録されていません。</p>
                </section>

                <!-- 発音 -->
                <section
                  v-show="activeDetailTab === 'pronunciations'" id="jpDetailPanel-pronunciations" class="jp-section"
                  data-jp-detail-panel="pronunciations" data-jp-detail-section="pronunciations"
                  role="tabpanel" aria-labelledby="jpDetailTab-pronunciations"
                >
                  <h3 class="jp-section__title">
                    発音 <span class="jp-section__count">{{ pronunciations.length }} 件</span>
                  </h3>
                  <template v-if="pronunciations.length > 0">
                    <article v-for="(pronunciation, index) in pronunciations" :key="index" class="jp-sense">
                      <div class="jp-sense__head">
                        <span class="jp-sense__number">{{ index + 1 }}</span>
                        <span class="jp-sense__text">{{ pronunciation.reading ?? '—' }}</span>
                        <span v-if="pronunciation.accentNotation" class="badge badge--outline">
                          アクセント {{ pronunciation.accentNotation }}
                        </span>
                      </div>
                      <dl class="jp-detail-fields">
                        <div
                          v-for="field in pronunciationFields(pronunciation)" :key="field.label"
                          class="jp-detail-field" :data-jp-detail-field="field.label"
                        >
                          <dt class="jp-detail-field__label">{{ field.label }}</dt>
                          <dd class="jp-detail-field__value">{{ fieldValue(field) }}</dd>
                        </div>
                      </dl>
                    </article>
                  </template>
                  <p v-else class="jp-hint">まだ登録されていません。</p>
                </section>

                <!-- コロケーション -->
                <section
                  v-show="activeDetailTab === 'collocations'" id="jpDetailPanel-collocations" class="jp-section"
                  data-jp-detail-panel="collocations" data-jp-detail-section="collocations"
                  role="tabpanel" aria-labelledby="jpDetailTab-collocations"
                >
                  <h3 class="jp-section__title">
                    コロケーション <span class="jp-section__count">{{ collocations.length }} 件</span>
                  </h3>
                  <template v-if="collocations.length > 0">
                    <article v-for="(collocation, index) in collocations" :key="index" class="jp-sense">
                      <div class="jp-sense__head">
                        <span class="jp-sense__number">{{ index + 1 }}</span>
                        <span class="jp-sense__text">{{ collocation.expression ?? '—' }}</span>
                      </div>
                      <dl class="jp-detail-fields">
                        <div
                          v-for="field in collocationFields(collocation)" :key="field.label"
                          class="jp-detail-field" :data-jp-detail-field="field.label"
                        >
                          <dt class="jp-detail-field__label">{{ field.label }}</dt>
                          <dd class="jp-detail-field__value">{{ fieldValue(field) }}</dd>
                        </div>
                      </dl>
                    </article>
                  </template>
                  <p v-else class="jp-hint">まだ登録されていません。</p>
                </section>

                <!-- 関連語 -->
                <section
                  v-show="activeDetailTab === 'relatedWords'" id="jpDetailPanel-relatedWords" class="jp-section"
                  data-jp-detail-panel="relatedWords" data-jp-detail-section="relatedWords"
                  role="tabpanel" aria-labelledby="jpDetailTab-relatedWords"
                >
                  <h3 class="jp-section__title">
                    関連語 <span class="jp-section__count">{{ relatedWords.length }} 件</span>
                  </h3>
                  <template v-if="relatedWords.length > 0">
                    <article v-for="(related, index) in relatedWords" :key="index" class="jp-sense">
                      <div class="jp-sense__head">
                        <span class="jp-sense__number">{{ index + 1 }}</span>
                        <span class="jp-sense__text">{{ related.heading ?? '—' }}</span>
                        <span v-if="related.relationType" class="badge badge--outline">
                          {{ RELATED_TYPE_LABELS[related.relationType] ?? related.relationType }}
                        </span>
                      </div>
                      <dl class="jp-detail-fields">
                        <div
                          v-for="field in relatedWordFields(related)" :key="field.label"
                          class="jp-detail-field" :data-jp-detail-field="field.label"
                        >
                          <dt class="jp-detail-field__label">{{ field.label }}</dt>
                          <dd class="jp-detail-field__value">{{ fieldValue(field) }}</dd>
                        </div>
                      </dl>
                    </article>
                  </template>
                  <p v-else class="jp-hint">まだ登録されていません。</p>
                </section>

                <!-- 使用注意 -->
                <section
                  v-show="activeDetailTab === 'cautions'" id="jpDetailPanel-cautions" class="jp-section"
                  data-jp-detail-panel="cautions" data-jp-detail-section="cautions"
                  role="tabpanel" aria-labelledby="jpDetailTab-cautions"
                >
                  <h3 class="jp-section__title">
                    使用注意 <span class="jp-section__count">{{ cautions.length }} 件</span>
                  </h3>
                  <template v-if="cautions.length > 0">
                    <article v-for="(caution, index) in cautions" :key="index" class="jp-sense">
                      <div class="jp-sense__head">
                        <span class="jp-sense__number">{{ index + 1 }}</span>
                        <span v-if="caution.noteType" class="badge badge--warning">{{ caution.noteType }}</span>
                        <span class="jp-sense__text">{{ caution.japanese ?? '—' }}</span>
                      </div>
                      <dl class="jp-detail-fields">
                        <div
                          v-for="field in cautionFields(caution)" :key="field.label"
                          class="jp-detail-field" :data-jp-detail-field="field.label"
                        >
                          <dt class="jp-detail-field__label">{{ field.label }}</dt>
                          <dd class="jp-detail-field__value">{{ fieldValue(field) }}</dd>
                        </div>
                      </dl>
                    </article>
                  </template>
                  <p v-else class="jp-hint">まだ登録されていません。</p>
                </section>

                <!-- 問題（C〜E。2.0 は有無だけだったが、2.1 は 1 問ずつ見せる） -->
                <section
                  v-show="activeDetailTab === 'questions'" id="jpDetailPanel-questions" class="jp-section"
                  data-jp-detail-panel="questions" data-jp-detail-section="questions"
                  role="tabpanel" aria-labelledby="jpDetailTab-questions"
                >
                  <h3 class="jp-section__title">
                    問題 <span class="jp-section__count">{{ detailQuestions.length }} 件</span>
                  </h3>
                  <template v-if="detailQuestions.length > 0">
                    <article
                      v-for="question in detailQuestions" :key="question.questionId"
                      class="jp-sense" :data-jp-question="question.questionId"
                    >
                      <div class="jp-sense__head">
                        <span class="jp-sense__number">{{ question.questionNo }}</span>
                        <span class="badge badge--neutral">{{ questionTypeLabel(question.questionType) }}</span>
                        <span class="jp-sense__text">{{ question.questionText ?? '—' }}</span>
                      </div>
                      <!-- 見出しに出した問題番号・種別・問題文は、項目一覧では繰り返さない -->
                      <dl class="jp-detail-fields">
                        <div
                          v-for="field in questionFields(question)" :key="field.label"
                          class="jp-detail-field" :data-jp-detail-field="field.label"
                        >
                          <dt class="jp-detail-field__label">{{ field.label }}</dt>
                          <dd class="jp-detail-field__value">{{ fieldValue(field) }}</dd>
                        </div>
                      </dl>
                    </article>
                  </template>
                  <p v-else class="jp-hint">まだ登録されていません。</p>
                </section>
              </div>
            </div>
          </div>
        </div>

        <div class="dialog__foot">
          <button type="button" class="btn btn--secondary" data-jp-detail-close @click="closeDetail">閉じる</button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* 検索条件の見出し行は、絞り込みの操作（検索・新規・リセット）と
   A〜E の取得ボタンを 1 行にまとめる（間は縦罫で区切る） */
.jp-head-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-3);
}

.jp-head-actions__acquire {
  padding-left: var(--sp-3);
  border-left: 1px solid var(--color-border);
}

/* 操作列のアイコンボタンは折り返さない（5 つ並ぶ） */
.jp-row-actions {
  white-space: nowrap;
}
</style>
