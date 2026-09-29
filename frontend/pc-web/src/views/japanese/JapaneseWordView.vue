<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ApiError, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import { paginationItems } from '@/features/pagination/pagination'
import JapaneseWordEditDialog from './JapaneseWordEditDialog.vue'
import DemoWordNewView from './demo/DemoWordNewView.vue'
import { useJpnRegisterStore } from '@/features/japanese-word/registerStore'
import { openJpnWordStudyPopup } from '@/features/japanese-word/studyPopup'
import { detailCountChips } from '@/features/japanese-word/detailCounts'
import {
  aiStateBadgeClass,
  aiStateLabelOf,
  aiStateOf,
  isAiStateDone,
  type JpnAiStateKey
} from '@/features/japanese-word/aiStateLabel'
import JpnAiHistoryDialog from '@/features/japanese-word/JpnAiHistoryDialog.vue'
import JpnAiFetchDialog from '@/features/japanese-word/JpnAiFetchDialog.vue'
import { JPN_ACQUIRE_ACTIONS, acquireActionOf } from '@/features/japanese-word/acquireActions'
import { youdaoWordUrl } from '@/features/japanese-word/youdao'
import { acquirePlan, type JpnAcquirePlan } from '@/features/japanese-word/acquirePlan'
import type { DemoBook, DemoBookUnit } from '@/features/japanese-demo/types'
import {
  deleteJpnWord,
  fetchJpnAiLimits,
  fetchJpnAiProgress,
  fetchJpnAiTargets,
  runJpnWordAi,
  searchJpnWords,
  type JpnAiKind,
  type JpnAiRunLimits,
  type JpnAiTargets,
  type JpnWord,
  type JpnWordFilters
} from '@/api/japanese'
import { fetchBatchTasks, type BatchTaskRow } from '@/api/batch'
import { jpnAiBatchBadgeOf } from '@/features/japanese-word/aiBatchState'
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
 * 右上の A〜E 取得（詳細情報・読み問題・文脈問題・漢字問題）は、押すと**取得方法の窓**を出す
 * （取得済みをスキップ／すべて再取得。利用者の指示 2026-09-26。参照は 2.0 の英語学習の
 * 単語情報管理 `word.jsp`）。対象は**検索条件に一致する語**（ページは問わない。サーバーが選ぶ）。上限は設定ページの「1 回の最大単語数」。
 * **受付だけして実行はバックエンドの働き手に任せる**（2026-09-27 に非同期へ変更。
 * 画面は「取得中」が消えるまで一覧をときどき見に行き、落ち着いたら結果を知らせる）。
 *
 * 一覧 API が持っていない情報は、画面側で次のように補っている。
 * ・書籍・分類の選択肢 … 選択肢一覧を返す API が無いので、読み込んだ単語の値から作る
 * ・品詞の選択肢 … `品詞` は `[名・他サ]` のような組み合わせのラベルで 65 種類あるため、
 *   代表の区分だけを並べる（絞り込みは部分一致なので「名」「サ」で複合ラベルにも当たる）
 * ・分類（From ～ To）… 一覧 API が範囲（`categoryFrom` / `categoryTo`）で受ける
 */

const toast = useToast()

/** AI 取得の種類（詳細情報（A・B）／読み問題（C）／文脈問題（D）／漢字問題（E））。
    ボタンは 1 つで、この並びを窓の中で選ばせる（定義は features/japanese-word/acquireActions.ts） */
const ACQUIRE_ACTIONS = JPN_ACQUIRE_ACTIONS
/** 取得中の区分（受付が終わるまで二重に押せないようにする）。 */
const acquireRunning = ref<JpnAiKind | null>(null)
/** 対象（検索条件に一致する語）をサーバーから取っている最中か（二重に押せないようにする）。 */
const plansLoading = ref(false)
/** 取得状態の列の説明（状態は一覧 API が 4 区画にまとめて返す）。 */
const ACQUIRE_COLUMN_TITLE =
  '取得状態（A・B，C，D，E）。一度でも成功した内容は「取得済」、失敗は「失敗」、まだ実行していない内容は「未取得」です。'

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

/** 1 ページの件数。 */
const PAGE_SIZES = [20, 50, 100]

const filters = reactive({
  keyword: '',
  jlpt: '',
  part: '',
  book: '',
  /** 分類の範囲（From／To）。両方 API へ送る（片方だけでもよい） */
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

/**
 * いまの絞り込み条件（一覧と AI 取得の対象で**同じもの**を渡す）。
 *
 * <p>対象の選定はサーバーが行うので、条件の組み立てを 2 か所に書くと
 * 「一覧に出ている語」と「取得の対象」が食い違う。ここ 1 か所から渡す。</p>
 */
function filterParams(): JpnWordFilters {
  return {
    keyword: filters.keyword.trim() === '' ? undefined : filters.keyword.trim(),
    jlpt: filters.jlpt === '' ? undefined : filters.jlpt,
    part: filters.part.trim() === '' ? undefined : filters.part.trim(),
    book: filters.book === '' ? undefined : filters.book,
    // 分類は範囲（From ～ To）。片方だけでも送る（空は「制限なし」）
    categoryFrom: filters.categoryFrom === '' ? undefined : filters.categoryFrom,
    categoryTo: filters.categoryTo === '' ? undefined : filters.categoryTo
  }
}

/**
 * 一覧を読む。
 *
 * <p><b>{@code quiet} は「画面を待たせない読み直し」</b>（AI 取得の様子見が 3 秒ごとに使う）。
 * 静かな読み直しでは、</p>
 * <ul>
 *   <li>{@code loading} を立てない（立てると表が「読み込んでいます…」に差し替わり、
 *       3 秒ごとに画面がちらつく。実際に起きた。2026-09-27）</li>
 *   <li>行の**同一性を保って**取得状態だけを入れ替える（表を組み直さない）</li>
 *   <li>失敗しても黙って見送る（次の周期でまた試す。利用者の操作ではないので邪魔しない）</li>
 * </ul>
 */
async function loadWords(options: { quiet?: boolean } = {}): Promise<void> {
  const quiet = options.quiet === true
  if (!quiet) {
    loading.value = true
    error.value = ''
  }
  try {
    const response = await searchJpnWords({
      ...filterParams(),
      page: page.value,
      size: size.value
    })
    words.value = quiet ? mergeWordStates(words.value, response.data.items) : response.data.items
    totalElements.value = response.data.totalElements
    totalPages.value = Math.max(1, response.data.totalPages)
    if (!quiet) {
      collectFilterOptions(response.data.items)
    }
  } catch (caught) {
    if (!quiet) {
      error.value = messageOf(caught, '単語の一覧を取得できませんでした。')
      words.value = []
      totalElements.value = 0
      totalPages.value = 1
    }
  } finally {
    if (!quiet) {
      loading.value = false
    }
  }
}

/**
 * 静かな読み直し用に、**行の同一性を保ったまま**取得状態（と詳細の件数）だけを差し替える。
 *
 * <p>行のオブジェクトを作り直すと、たとえ中身が同じでも表全体が描き直されてちらつく。
 * 同じ語 ID の行は使い回し、変わった項目だけを写す。</p>
 */
function mergeWordStates(current: JpnWord[], fetched: JpnWord[]): JpnWord[] {
  const byId = new Map(current.map((row) => [row.wordId, row]))
  return fetched.map((row) => {
    const existing = byId.get(row.wordId)
    if (!existing) {
      return row
    }
    existing.aiState = row.aiState
    existing.detailCounts = row.detailCounts
    return existing
  })
}

/** 【検索】条件を適用して 1 ページ目から読み直す。 */
function search(): void {
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


const wordDialogOpen = ref(false)
const createOpen = ref(false)
const editingId = ref<number | null>(null)
const registerStore = useJpnRegisterStore()

function knownBooks(rows: JpnWord[]): DemoBook[] {
  const byBook = new Map<string, Map<string, number>>()
  for (const row of rows) {
    if (row.book === null || row.category === null) continue
    const units = byBook.get(row.book) ?? new Map<string, number>()
    units.set(row.category, (units.get(row.category) ?? 0) + 1)
    byBook.set(row.book, units)
  }
  return [...byBook.entries()].map(([name, units]) => {
    const list: DemoBookUnit[] = [...units.entries()]
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([unit, count]) => ({ name: unit, count, capacity: Math.max(count, 20) }))
    return { id: 'book-' + name, name, unitSize: Math.max(1, ...list.map(unit => unit.count), 20), units: list, note: '' }
  })
}
function openCreate(): void {
  editingId.value = null
  registerStore.prepare({ books: knownBooks(words.value), knownHeadings: words.value.map(row => row.word) })
  createOpen.value = true
}
function closeCreate(): void { createOpen.value = false }
async function afterCreate(): Promise<void> {
  createOpen.value = false
  await loadWords()
  toast.success('単語を登録しました。')
}
function openEdit(row: JpnWord): void {
  editingId.value = row.wordId
  wordDialogOpen.value = true
}
/**
 * 一覧の【詳細】（目のアイコン）は**別ウィンドウ**で学習画面を開く（自動最大化）。
 *
 * 2.0 の `js/japanese_word.js` が `japanese_test_a.jsp?wordId=…&view=detail` を
 * `window.open` で開いていたのと同じ形（利用者の指示。2026-09-25）。
 * データは開いた窓が取り直すので、一覧はここで API を呼ばない。
 */
function openDetail(row: JpnWord): void {
  openJpnWordStudyPopup(row.wordId)
}

/* ---------- AI 取得の履歴（取得状態のタグから開く） ---------- */

/** 履歴ダイアログを出しているか。 */
const historyOpen = ref(false)
/** 履歴の対象（語と区画）。 */
const historyTarget = ref<{ wordId: number; section: JpnAiStateKey } | null>(null)

/**
 * 「取得済」のタグから、その区画の履歴を開く（使う版を選べる）。
 *
 * 2.0 の英語学習（`word.jsp`）の「詳細情報取得履歴」と同じ入口（あちらは行の履歴ボタン、
 * こちらは取得済のタグ）。
 */
function openHistory(row: JpnWord, section: JpnAiStateKey): void {
  if (!isAiStateDone(aiStateOf(row, section))) return
  historyTarget.value = { wordId: row.wordId, section }
  historyOpen.value = true
}

function closeHistory(): void {
  historyOpen.value = false
  historyTarget.value = null
}

/** 版を切り替えたら一覧を読み直す（取得状態と詳細情報件数が変わる）。 */
async function afterHistoryChange(): Promise<void> {
  await loadWords()
}

onMounted(() => {
  void loadWords()
  void loadAiLimits()
  void loadBatchStates()
})

// 受付けた取得の様子見は、画面を離れたら止める（裏で回し続けない）
onUnmounted(() => {
  stopAcquireWatch()
})

/**
 * 取得の上限（設定ページの「1 回の最大単語数」）を読む。
 *
 * <p>読めなければ {@code aiLimitsLoaded} は false のまま＝AI 取得のボタンは無効。
 * **画面側の既定値へは落とさない**（設定漏れのまま課金しない）。</p>
 */
async function loadAiLimits(): Promise<void> {
  try {
    const response = await fetchJpnAiLimits()
    const data = response.data
    if (response.success && data && typeof data === 'object') {
      aiLimits.value = data
      aiLimitsLoaded.value = aiLimitsReady.value
    }
  } catch {
    // 設定が読めない（未設定・設定ページの値が不正など）。実行させない
    aiLimits.value = {}
    aiLimitsLoaded.value = false
  }
}

/* ---------- AI 取得（batC41〜batC44） ---------- */

/**
 * AI 取得の窓に出す内容（開いているときだけ入る）。
 *
 * <p>押した瞬間には走らせず、まず窓を出す。窓では**どの情報を取るか**（A・B／C／D／E）と、
 * **取得済みをどうするか**（スキップ／すべて再取得）を決める（利用者の指示 2026-09-26。
 * 参照は 2.0 の英語学習の単語情報管理 `word.jsp` の窓）。どちらを選ぶと何語が対象になるかは、
 * 窓を出す前に**区画ごとに**数えて渡す（AI を呼ぶ前に見せる）。</p>
 */
const fetchPlans = ref<Record<JpnAiStateKey, { skip: JpnAcquirePlan; all: JpnAcquirePlan }> | null>(null)

/** 窓に出す「何が対象か」（一覧全体か、行の 1 語か）。 */
const fetchTargetLabel = ref('')

/**
 * 取得区分ごとの 1 回の上限（**設定ページの「1 回の最大単語数」だけ**が上限。API から取る）。
 *
 * <p>画面に既定値を持たない（利用者の指示 2026-09-27「把这个限制去掉，只使用设定页面的
 * 「1回の最大単語数」来进行限制」）。読めないときは**実行させない**（ボタンを無効にし、
 * 理由を知らせる）。コード側の既定値へ落とすと、設定漏れに気づけないまま課金されうる。</p>
 */
const aiLimits = ref<JpnAiRunLimits>({})
const aiLimitsLoaded = ref(false)

/** その区画の 1 回の上限（設定値）。読めていなければ null。 */
function limitOf(key: JpnAiStateKey): number | null {
  const limit = aiLimits.value[acquireActionOf(key).kind]?.limit
  return typeof limit === 'number' && limit > 0 ? limit : null
}

/**
 * AI 取得の 4 バッチ（batC41〜batC44）の状態（検索条件の右上に出す）。
 *
 * <p>状態は**批次一覧と同じ入口**（`GET /api/admin/batch/tasks`）から取る。別の入口を作ると
 * バッチ一覧の表示と食い違うため。**読めないときは何も出さない**（一覧も AI 取得も使える）。</p>
 */
const batchTasks = ref<BatchTaskRow[]>([])

const batchBadges = computed(() => {
  if (batchTasks.value.length === 0) {
    return []
  }
  return JPN_ACQUIRE_ACTIONS.map((action) => jpnAiBatchBadgeOf(
    action,
    batchTasks.value.find((row) => row.taskCode === action.batchCode),
    limitOf(action.key)
  ))
})

/** バッチの状態を読む（失敗しても画面は壊さない＝バッジを出さないだけ）。 */
async function loadBatchStates(): Promise<void> {
  try {
    const response = await fetchBatchTasks()
    batchTasks.value = response.data?.rows ?? []
  } catch {
    batchTasks.value = []
  }
}

/** 4 区画すべての上限が読めているか（読めていなければ AI 取得を実行させない）。 */
const aiLimitsReady = computed(() => ACQUIRE_ACTIONS.every((action) => limitOf(action.key) !== null))

/** 上限が読めないときの知らせ（ボタンの title と、押されたときのトーストで同じ文面を使う）。 */
const AI_LIMIT_MISSING_MESSAGE =
  '設定ページの「1 回の最大単語数」が読めないため、AI 取得を実行できません。設定を確認してください。'

/**
 * 対象（1 語）について 4 区画ぶんの計画を作って窓を出す（**行の【AI 取得】**）。
 *
 * <p>1 語なら問い合わせる必要が無いので、画面側で判定する（{@link acquirePlan}）。
 * 右上の【AI 取得】は対象が検索条件に一致する語全体なので、サーバーが選ぶ（{@link openAcquire}）。</p>
 */
function openAcquireFor(rows: readonly JpnWord[], label: string): void {
  const limits = ACQUIRE_ACTIONS.map((action) => limitOf(action.key))
  if (limits.some((limit) => limit === null)) {
    // 設定が読めないまま実行すると、上限が分からない（＝何語受付けるか決められない）
    toast.danger(AI_LIMIT_MISSING_MESSAGE)
    return
  }
  fetchTargetLabel.value = label
  fetchPlans.value = Object.fromEntries(ACQUIRE_ACTIONS.map((action, index) => [
    action.key,
    {
      skip: acquirePlan(rows, action.key, true, limits[index]!),
      all: acquirePlan(rows, action.key, false, limits[index]!)
    }
  ])) as Record<JpnAiStateKey, { skip: JpnAcquirePlan; all: JpnAcquirePlan }>
}

/**
 * 右上の【AI 取得】: **検索条件に一致する語**（ページは問わない）をまとめて取る。
 *
 * <p>対象の選定はサーバー（`/words/ai-targets`）が行う。一覧と同じ絞り込み・同じ並びなので、
 * 「いま絞り込んで見えている語の集まり」から表示順に選ばれる（2026-09-27。以前は当ページだけ
 * だった）。4 つの取得区分 × スキップ／すべて再取得の 8 通りをまとめて取る。
 * **上限は設定値だけ**（読めなければ実行しない）。</p>
 */
async function openAcquire(): Promise<void> {
  const limits = ACQUIRE_ACTIONS.map((action) => limitOf(action.key))
  if (limits.some((limit) => limit === null)) {
    toast.danger(AI_LIMIT_MISSING_MESSAGE)
    return
  }
  plansLoading.value = true
  try {
    const targets = await Promise.all(ACQUIRE_ACTIONS.flatMap((action, index) => [
      fetchJpnAiTargets({ ...filterParams(), kind: action.kind, skipAcquired: true, limit: limits[index]! }),
      fetchJpnAiTargets({ ...filterParams(), kind: action.kind, skipAcquired: false, limit: limits[index]! })
    ]))
    const plans = {} as Record<JpnAiStateKey, { skip: JpnAcquirePlan; all: JpnAcquirePlan }>
    ACQUIRE_ACTIONS.forEach((action, index) => {
      const skip = targets[index * 2]!.data
      const all = targets[index * 2 + 1]!.data
      plans[action.key] = {
        skip: planOf(skip, skip.candidates),
        all: planOf(all, all.total)
      }
    })
    fetchTargetLabel.value = `検索条件に一致する ${plans.AB.all.total} 語`
    fetchPlans.value = plans
  } catch (caught) {
    toast.danger(messageOf(caught, 'AI 取得の対象を取得できませんでした。'))
  } finally {
    plansLoading.value = false
  }
}

/** サーバーが選んだ対象を、窓が使う計画の形にする。 */
function planOf(targets: JpnAiTargets, candidates: number): JpnAcquirePlan {
  return {
    wordIds: targets.wordIds,
    total: targets.total,
    acquired: targets.acquired,
    targets: targets.wordIds.length,
    skipped: Math.max(0, targets.total - candidates),
    overLimit: targets.overLimit,
    limit: targets.limit
  }
}

/** 行の【AI 取得】: その 1 語だけを取る（ほかの語は対象にしない）。 */
function openRowAcquire(row: JpnWord): void {
  openAcquireFor([row], `この単語（${row.word}）`)
}

/** 窓を閉じる（何も実行しない）。 */
function closeAcquire(): void {
  fetchPlans.value = null
}

/**
 * 窓で選んだ内容で実行する。
 *
 * @param section      どの情報を取るか（A・B／C／D／E）
 * @param skipAcquired true なら取得済み・取得中を外した語だけを渡す
 */
async function confirmAcquire(section: JpnAiStateKey, skipAcquired: boolean): Promise<void> {
  const plans = fetchPlans.value
  if (plans === null) return
  const plan = skipAcquired ? plans[section].skip : plans[section].all
  const action = acquireActionOf(section)
  fetchPlans.value = null
  await acquire(action.kind, action.label, plan.wordIds, skipAcquired)
}

/**
 * 選んだ語を AI に取らせる（**受付だけ**。実行はバックエンドの働き手）。
 *
 * <p>2026-09-27 に非同期へ変更: 受付の API は {@code QUEUED} を積んで**すぐ返る**ので、画面は
 * 結果を待たない。「受付しました」を知らせたあと、{@link ACQUIRE_POLL_INTERVAL_MS} ごとに
 * 一覧を読み直し、受付けた語の「取得中（{@code QUEUED} / {@code RUNNING}）」が消えたら
 * 結果（成功・失敗の件数）を知らせる（{@link watchAcquired}）。</p>
 *
 * <p>**取得済みをスキップ**して 1 語も残らなかったときは、受付もしないでそう知らせる
 * （積んでも新しい内容は作れないし、課金だけ増えるため）。</p>
 */
async function acquire(kind: JpnAiKind, label: string, wordIds: number[], skipAcquired: boolean): Promise<void> {
  if (wordIds.length === 0) {
    toast.info(skipAcquired
      ? `【${label}】取得済みを除くと対象がありません（すべて取得済みです）。`
      : `【${label}】表示中の単語がありません。先に検索してください。`)
    return
  }
  acquireRunning.value = kind
  try {
    const response = await runJpnWordAi(kind, wordIds)
    // 受付だけ（実行はバックエンド）。何件受付けたか・何件が実行中かを知らせる。
    // 文面は画面の言い方（「詳細情報（A・B）」など）に合わせる（API は区分コードしか返さない）
    const accepted = response.data?.accepted ?? wordIds.length
    const reused = response.data?.reused ?? 0
    toast.success(`【${label}】受付けました（受付 ${accepted} 件 / 実行中 ${reused} 件）。`
      + 'バックグラウンドで取得します。')
    // 受付で状態が変わる（待ち・取得中になる）ので、すぐ 1 回読み直す。
    // **静かに**読み直す（受付の直後に表を「読み込んでいます…」へ差し替えてちらつかせない）
    await loadWords({ quiet: true })
    // 進み具合は受付が返した生成 ID で見る（一覧の「取得状態」は取り直しの判定に使えない）
    watchAcquired(kind, label, response.data?.generationIds ?? [])
  } catch (caught) {
    toast.danger(messageOf(caught, `【${label}】の受付に失敗しました。`))
  } finally {
    acquireRunning.value = null
  }
}

/* ---------- 受付けた取得の様子見（非同期なので結果は後から来る） ---------- */

/** 一覧を読み直す間隔（ミリ秒）。 */
const ACQUIRE_POLL_INTERVAL_MS = 3_000

/** 様子見をやめるまでの回数（3 秒 × 600 ＝ 30 分。これ以上は実行中でも知らせない）。 */
const ACQUIRE_POLL_MAX_TICKS = 600

/** いま様子を見ている取得（受付けた行の生成 ID と、結果を知らせるための情報）。 */
const acquireWatching = ref<{ kind: JpnAiKind; label: string; generationIds: number[] } | null>(null)

/** 様子見のタイマー（1 つだけ回す）。 */
let acquirePollTimer: number | null = null

/** 様子見の回数（上限で打ち切るため）。 */
let acquirePollTicks = 0

/** 様子見を止める。 */
function stopAcquireWatch(): void {
  if (acquirePollTimer !== null) {
    window.clearInterval(acquirePollTimer)
    acquirePollTimer = null
  }
  acquireWatching.value = null
  acquirePollTicks = 0
}

/**
 * 受付けた取得の状態が落ち着くまで、進み具合を見に行く。
 *
 * <p>止める条件は 3 つ: 未完了（{@code pending}）が 0 になった／受付けた ID が分からない
 * （古いサーバーなど）／回数の上限に達した。**一覧の「取得状態」では判定しない**:
 * あの列は「一度でも成功したか」を優先するので、取り直しでは最初から「取得済」に見えてしまう
 * （2026-09-27 のレビューで判明）。</p>
 */
function watchAcquired(kind: JpnAiKind, label: string, generationIds: number[]): void {
  stopAcquireWatch()
  acquireWatching.value = { kind, label, generationIds }
  acquirePollTimer = window.setInterval(() => { void pollAcquired() }, ACQUIRE_POLL_INTERVAL_MS)
}

/** 1 回ぶんの様子見（進み具合を見て、終わっていれば知らせる）。 */
async function pollAcquired(): Promise<void> {
  const watching = acquireWatching.value
  if (watching === null) {
    stopAcquireWatch()
    return
  }
  acquirePollTicks += 1
  try {
    // 進み具合（生の状態）と、画面の「取得中」表示のための一覧を取り直す。
    // 一覧は**静かに**読み直す（loading を立てない＝表を組み直さない＝ちらつかない）
    const [progress] = await Promise.all([
      fetchJpnAiProgress(watching.generationIds),
      loadWords({ quiet: true })
    ])
    const { pending, succeeded, failed } = progress.data
    if (pending > 0) {
      if (acquirePollTicks < ACQUIRE_POLL_MAX_TICKS) {
        return
      }
      // 実行中のまま上限に達した（長い・滞留している）。ここで打ち切って、あとは利用者に任せる
      toast.warning(`【${watching.label}】まだ取得中です。【再読み込み】で状態を確かめてください。`)
      stopAcquireWatch()
      return
    }
    // 落ち着いた。件数を知らせる
    const message = `【${watching.label}】取得が終わりました（成功 ${succeeded} / 失敗 ${failed}）。`
    if (failed > 0) {
      toast.warning(message)
    } else {
      toast.success(message)
    }
    stopAcquireWatch()
  } catch (caught) {
    // 進み具合が読めない（通信断・古いサーバー）。様子見は止める（黙って回り続けない）
    toast.danger(messageOf(caught, `【${watching.label}】の進み具合を確認できませんでした。`))
    stopAcquireWatch()
  }
}
</script>

<template>
  <div class="jp-page">
    <!-- 検索条件（キーワード・JLPT レベル・品詞・書籍・分類（From／To）） -->
    <div class="search-panel">
      <div class="search-panel__head">
        <h3 class="search-panel__title"><AppIcon name="search" size="sm" /> 検索条件</h3>
        <!-- AI 取得の 4 バッチ（batC41〜batC44）の状態。批次一覧と同じ入口から取るので、
             どのバッチが動いているか・最後は成功したかがここで分かる（利用者の指示 2026-09-27）。
             状態が読めないときは出さない（画面は使える） -->
        <div v-if="batchBadges.length > 0" class="jp-batch-states" data-jp-batch-states>
          <span
            v-for="badge in batchBadges"
            :key="badge.batchCode"
            class="badge jp-batch-states__item"
            :class="`badge--${badge.tone}`"
            :data-jp-batch-state="badge.batchCode"
            :title="badge.title"
          >{{ badge.short }}: {{ badge.label }}</span>
        </div>
        <div class="jp-head-actions">
          <div class="search-panel__actions">
            <button type="button" class="btn btn--primary" data-jp-search :disabled="loading" @click="search">
              <AppIcon name="search" size="sm" /> 検索
            </button>
            <button type="button" class="btn btn--primary" data-jp-add @click="openCreate">
              <AppIcon name="plus" size="sm" /> 新規
            </button>
            <!-- 条件をまとめて消す【リセット】は利用者の指示で外した（各項目を直接消す） -->
            <!-- 今の条件のまま取り直す（一覧の見出しではなく、検索の操作と同じ行に置く） -->
            <button type="button" class="btn btn--secondary" data-jp-refresh :disabled="loading" @click="loadWords()">
              <AppIcon name="rotate" size="sm" /> 再読み込み
            </button>
          </div>
          <!-- 右上の AI 取得（**検索条件に一致する語**をまとめて受付ける。ページは問わない）。
               4 つ並べると場所を取るので**ボタンは 1 つ**にし、どの情報を取るかは窓の中で選ぶ。
               左の縦罫は置かない（区切りは余白だけ＝利用者の指示） -->
          <div class="search-panel__actions" data-jp-acquire-actions>
            <button
              type="button" class="btn btn--secondary" data-jp-acquire
              :disabled="loading || plansLoading || acquireRunning !== null || !aiLimitsReady"
              :title="aiLimitsReady
                ? 'AI 取得：検索条件に一致する単語の取得を受付けます（実行はバックグラウンド）。何を取るかと上限は窓で確認できます。'
                : AI_LIMIT_MISSING_MESSAGE"
              @click="openAcquire"
            >
              <AppIcon name="robot" size="sm" /> AI 取得
            </button>
          </div>
        </div>
      </div>
      <div class="filters">
        <!-- 条件は 1 行にまとめる（狭い画面では折り返して縦に積む）。
             キーワードもほかの条件と同じく左から順に並べ、部分一致なので幅は広げない -->
        <div class="filters__row jp-filters__row">
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
                aria-label="分類（To）"
              >
                <option value="">（すべて）</option>
                <option v-for="option in categoryOptions" :key="option" :value="option">{{ option }}</option>
              </select>
            </span>
          </span>
          <!-- キーワードはほかの条件と同じ並び（左から順）。見出し語の部分一致なので、
               入力欄は広げない（伸ばすとほかの条件が押し出される） -->
          <span class="filter-item jp-filters__keyword">
            <span class="filter-item__label">キーワード：</span>
            <input
              v-model="filters.keyword" class="input" type="search" data-jp-filter="keyword"
              placeholder="見出し語の一部" @keyup.enter="search"
            >
          </span>
        </div>
      </div>
    </div>

    <!-- 単語一覧 -->
    <section class="card">
      <div class="card__header">
        <h2 class="card__title"><AppIcon name="list" size="sm" /> 単語一覧</h2>
        <!-- 件数は見出しの右端。絞り込み中はその左に置く（件数が右端のまま） -->
        <span class="jp-list-head-right">
          <span v-if="isFiltered" class="jp-hint" data-jp-filtered-count>絞り込み中</span>
          <span class="cell-muted" data-jp-count>全 {{ totalElements }} 件</span>
        </span>
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
              <!-- 単語・読み方・品詞を 1 行目、中国語訳を 2 行目に出す（1 列にまとめる。
                   列を分けると中国語訳のぶんだけ横を圧迫するため。利用者の指示 2026-09-26） -->
              <th class="col-jp-word-summary">単語・読み方・品詞・中国語訳</th>
              <!-- 2.0 の英語学習（word.jsp）と同じ「詳細情報件数」 -->
              <th class="col-jp-detail-counts">詳細情報件数</th>
              <th class="col-jp-acquire">取得状態（A・B，C，D，E）</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in words" :key="row.wordId" :data-jp-word-row="row.wordId">
              <!-- 操作はアイコンだけ（意味は title と aria-label で伝える）。
                   田の字（2 行 × 2 列）に並べるのは**中の span**
                   （td 自身の display を変えると表のセルでなくなり行の高さが崩れる） -->
              <td class="row-actions jp-row-actions">
                <span class="jp-row-actions__grid">
                  <button
                    type="button" class="btn btn--icon btn--sm" title="詳細" aria-label="詳細"
                    data-jp-detail @click="openDetail(row)"
                  >
                    <AppIcon name="eye" size="sm" class="icon--view" />
                  </button>
                  <!-- この行の 1 語だけを AI で取得する（窓は右上の【AI 取得】と同じ）。
                       アイコンは 2.0 の word.jsp 初級編の英語単語列に出ていたロボット。
                       色は主色（青緑＝app.css の .icon--ai）で、詳細・修正・削除と見分ける -->
                  <button
                    type="button" class="btn btn--icon btn--sm"
                    :disabled="acquireRunning !== null || !aiLimitsReady"
                    :title="aiLimitsReady ? 'AI 取得' : AI_LIMIT_MISSING_MESSAGE"
                    aria-label="AI 取得"
                    data-jp-row-acquire @click="openRowAcquire(row)"
                  >
                    <AppIcon name="robot" size="sm" class="icon--ai" />
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
                </span>
              </td>
              <td>{{ row.jlptLevel ?? '—' }}</td>
              <td class="cell-muted">{{ row.wordId }}</td>
              <td>{{ row.book ?? '—' }}</td>
              <td>{{ row.category ?? '—' }}</td>
              <!-- 1 行目: 単語（外部の辞書へのリンク）・読み方・品詞 /
                   2 行目: 中国語訳（有効版の詳細の最初の語義の中国語。無ければ「—」）。
                   2 行で切るのは**中の span**（td の display を変えると列幅が壊れる） -->
              <td data-jp-word-summary>
                <span class="jp-word-summary">
                  <span class="jp-word-summary__main">
                    <a
                      class="jp-word jp-word--link" data-jp-word-link
                      :href="youdaoWordUrl(row.word)" target="_blank" rel="noopener noreferrer"
                      :title="`辞書で「${row.word}」を開く（別タブ）`"
                    >{{ row.word }}</a>
                    <span class="jp-word-summary__reading">{{ row.reading === '' ? '—' : row.reading }}</span>
                    <span class="jp-word-summary__part">{{ row.partOfSpeech ?? '—' }}</span>
                  </span>
                  <span
                    class="jp-word-summary__chinese" data-jp-chinese
                    :title="row.chineseMeaning ?? undefined"
                  >{{ row.chineseMeaning ?? '—' }}</span>
                </span>
              </td>
              <!-- 詳細情報件数は有効版の段落の行数（0 も出す＝何が足りないかが分かる）。
                   まだ詳細が無い語は「—」。
                   タグを並べるのは**中の span**（td 自身の display を変えると表のセルでなくなり、
                   行の高さが中身に合わず次の行へ食い込む。実際に起きた） -->
              <td data-jp-detail-counts>
                <span class="jp-detail-counts">
                  <template v-if="row.detailCounts">
                    <span
                      v-for="chip in detailCountChips(row.detailCounts)" :key="chip.key"
                      class="jp-detail-count" :data-jp-detail-count="chip.key" :title="chip.titleOf(chip.count)"
                    >
                      <b>{{ chip.label }}</b><em>{{ chip.count }}</em>
                    </span>
                  </template>
                  <span v-else class="cell-muted">—</span>
                </span>
              </td>
              <!-- 取得状態（A・B／C／D／E）。タグで出し、取得済／失敗／未取得を色で見分ける。
                   **取得済だけ押せる**（押すとその区画の履歴を開き、使う版を選べる） -->
              <td data-jp-acquire :title="ACQUIRE_COLUMN_TITLE">
                <span class="jp-acquire">
                  <button
                    v-for="action in ACQUIRE_ACTIONS" :key="action.key"
                    type="button"
                    class="badge jp-acquire__item"
                    :class="[
                      aiStateBadgeClass(aiStateOf(row, action.key)),
                      { 'is-clickable': isAiStateDone(aiStateOf(row, action.key)) }
                    ]"
                    :data-jp-acquire-state="action.key"
                    :disabled="!isAiStateDone(aiStateOf(row, action.key))"
                    :title="isAiStateDone(aiStateOf(row, action.key))
                      ? `${action.label}の履歴を開く（使う版を選べます）`
                      : ACQUIRE_COLUMN_TITLE"
                    @click="openHistory(row, action.key)"
                  >{{ action.short }} {{ aiStateLabelOf(aiStateOf(row, action.key)) }}</button>
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


    <JapaneseWordEditDialog
      v-if="wordDialogOpen && editingId !== null" :word-id="editingId"
      @close="wordDialogOpen = false" @saved="loadWords"
    />
    <DemoWordNewView v-if="createOpen" :store="registerStore" data-jp-create @close="closeCreate" @saved="afterCreate" />
    <!-- AI 取得の履歴（取得状態の「取得済」タグから開く） -->
    <JpnAiHistoryDialog
      v-if="historyTarget !== null"
      :visible="historyOpen"
      :word-id="historyTarget.wordId"
      :section="historyTarget.section"
      data-jp-history
      @close="closeHistory"
      @activated="afterHistoryChange"
    />
    <!-- AI 取得の窓（右上の 1 つの取得ボタンを押すと出る。
         どの情報を取るかと、取得済みをどうするかをここで選ぶ） -->
    <JpnAiFetchDialog
      v-if="fetchPlans !== null"
      :visible="true"
      :target-label="fetchTargetLabel"
      :plans="fetchPlans"
      @choose="confirmAcquire"
      @close="closeAcquire"
    />
  </div>
</template>

<style scoped>
/* 検索条件の見出し行は、絞り込みの操作（検索・新規・再読み込み）と
   AI 取得のボタンを 1 行にまとめる（区切りは余白だけ。縦罫は置かない＝利用者の指示） */
.jp-head-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-3);
}

/* 一覧の見出しは右端に件数を出す（絞り込み中はその左）。
   .card__header は space-between なので、まとまりを 1 つにして右へ寄せる */
.jp-list-head-right {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  margin-left: auto;
}

/* 操作列のアイコンボタンは折り返さない（5 つ並ぶ） */
.jp-row-actions {
  white-space: nowrap;
}
</style>
