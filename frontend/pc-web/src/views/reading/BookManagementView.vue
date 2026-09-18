<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ApiError, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  CATEGORY_NONE,
  COVER_ACCEPT,
  COVER_MAX_BYTES,
  DIFFICULTY_LABELS,
  DIFFICULTY_OPTIONS,
  LANGUAGE_OPTIONS,
  PDF_ACCEPT,
  PDF_MAX_BYTES,
  SCOPE_LABELS,
  createReadingBook,
  createReadingCategory,
  deleteReadingBook,
  listReadingCategories,
  readingCoverUrl,
  readingPdfDownloadUrl,
  searchReadingBooks,
  setReadingBookPinned,
  updateReadingBook,
  uploadReadingCover,
  uploadReadingPdf,
  type ReadingBook,
  type ReadingBookSave,
  type ReadingCategory,
  type ReadingLanguage,
  type ReadingScopeFilter,
  type ReadingShelfTotals,
  type ReadingDifficulty,
  type ReadingStatus
} from '@/api/reading'
import { useAuthStore } from '@/stores/auth'
import type { PdfDocumentProxy } from '@/features/reading/readingPdf'
import '@/features/reading/reading.css'

/**
 * 読書管理【書籍管理】（管理者＝全体書籍 / 保護者＝自分の家庭の本）。
 *
 * 2026-09-14 の決定（Q3）: 管理者と保護者で**同じ画面を使い、区分で絞り込む**。
 * ・管理者 … 区分「全体の本」（`GLOBAL`）。全体書籍だけを登録・修正・削除できる
 * ・保護者 … 区分「家庭の本」（`FAMILY`）。自分の家庭の本だけを登録・修正・削除できる
 *   （全体書籍は【図書館】で読めるが、直せないので操作ボタンを出さない）
 * ・生徒   … この画面をメニューに出さない（API は 403）
 * 新規登録の公開範囲は**サーバが決める**（管理者＝GLOBAL / 保護者＝FAMILY 固定）ので、
 * この画面に公開範囲の選択は出さず、いま何を作るのかを文章で示す。
 *
 * 2.0 の英語読書 `english_reading.jsp`（＋登録モーダル `english_reading_input.jsp`）を
 * 2.1 の設計で作り直したもの。データは `RED_書籍情報` / `RED_書籍分類情報` /
 * `RED_書籍ファイル情報`（2.0 から移行済み）を API で読む。
 *
 * この画面でできること:
 * ・書籍の登録・修正・削除・置頂
 * ・本文 PDF・表紙の添付（登録・差し替え）
 *
 * この画面で**出さない**もの（閲覧画面 `BookReaderView.vue` の役割）:
 * ・読書の進捗（現在ページ・読書ステータス・読書時間・進捗％）
 * ・読書履歴（記録の一覧・登録・削除）
 * ・書籍を開いて読む導線（閲覧画面への移動）
 * ・検索条件カード（書籍は 6 冊と少ないため、分類のタブで棚を切り替える）
 * ・1 冊ごとの進捗（冊数のサマリ「全 N 冊 ・ 読書中 N 冊 ・ 読了 N 冊」は本棚タブと同じく出す）
 *
 * 2.1 での決めごと:
 * ・分類（棚）は**ジャンルだけ**を持つ。本の言語は `言語` フィールドで表す
 *   （「英語 小説」のような言語混じりの分類名は作らない）
 * ・1 冊につき本文 PDF は 1 つ・表紙も 1 つ。選び直しは「差し替え」であって追加ではない
 * ・本文 PDF・表紙は**ドラッグ＆ドロップ**（表紙はスクリーンショットの貼り付けも可）で選ぶ
 * ・分類の追加・改名・削除・並べ替えはこの画面では行わない（タブは実在の分類から自動で作る）。
 *   分類が足りないときだけ、書籍ダイアログの分類セレクトから新しく作れる
 * ・本棚はページングしない（書籍は多くない）。API の上限（100 件）まで 1 回で読む
 * ・見出し・サマリ・分類タブ・棚の分け方は、閲覧画面の【図書館】タブ（`BookReaderView.vue`）に合わせる。
 *   置頂の本だけは「置頂」という別の棚に分けて最上部に置く（管理するときに目印になる本を先に）
 */
const toast = useToast()

/** 本棚を 1 回で読む冊数（API の上限が 100 件なのでそれに合わせる）。 */
const BOOK_PAGE_SIZE = 100

/** タグ入力のよく使う候補（2.0 の候補ボタンと同じ並び）。 */
const TAG_SUGGESTIONS = ['Oxford', 'Classic', 'Dialogue', 'Novel', 'Vocabulary', 'Grammar']

/** 総ページ数が空のまま保存されたときに送る値（PDF を選べば pdf.js が自動で入れる）。 */
const DEFAULT_TOTAL_PAGES = 1

/** 表紙として受け付ける拡張子。 */
const COVER_EXTENSIONS = ['png', 'jpg', 'jpeg', 'webp']

/** 分類セレクトの「＋ 新しい分類を追加…」を表す値（分類ID とぶつからない文字列）。 */
const CATEGORY_NEW = '__new__'

/** 同梱の pdf.js（本文 PDF の総ページ数を数えるときに使う）。 */
const PDFJS_SCRIPT = '/lib/pdfjs/pdf.min.js'
const PDFJS_WORKER = '/lib/pdfjs/pdf.worker.min.js'
/** pdf.js が読み込めない環境では待たずに手入力へ戻す。 */
const PDFJS_LOAD_TIMEOUT_MS = 5000

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

function clearErrors(errors: Record<string, string>): void {
  for (const key of Object.keys(errors)) {
    delete errors[key]
  }
}

/** 落ちてきたファイルの拡張子（小文字。無ければ空文字）。 */
function extensionOf(name: string): string {
  const dot = name.lastIndexOf('.')
  return dot < 0 ? '' : name.slice(dot + 1).toLowerCase()
}

/* ---------- 区分（公開範囲）と棚（分類タブ） ---------- */

const auth = useAuthStore()

/** いま見ている区分の既定値（管理者＝全体の本 / 保護者＝家庭の本）。 */
function defaultScope(role: string | null): ReadingScopeFilter {
  if (role === 'ADMIN') return 'GLOBAL'
  if (role === 'GUARDIAN') return 'FAMILY'
  return 'ALL'
}

const scopeFilter = ref<ReadingScopeFilter>(defaultScope(auth.role))

/** 区分のタブ（ロールで出す選択肢を変える。家庭を持たない管理者に「家庭の本」は出さない）。 */
const scopeOptions = computed<{ value: ReadingScopeFilter; label: string }[]>(() => {
  if (auth.role === 'ADMIN') {
    return [
      { value: 'GLOBAL', label: '全体の本' },
      { value: 'ALL', label: 'すべて' }
    ]
  }
  if (auth.role === 'GUARDIAN') {
    return [
      { value: 'FAMILY', label: '家庭の本' },
      { value: 'ALL', label: 'すべて' }
    ]
  }
  return [{ value: 'ALL', label: 'すべて' }]
})

/** 選択中の棚。'all' = すべて、'none' = 未分類、数値 = 分類ID。 */
type CategoryKey = 'all' | 'none' | number

const selectedCategory = ref<CategoryKey>('all')

/* ---------- 一覧の状態 ---------- */

const loading = ref(false)
const error = ref('')
const books = ref<ReadingBook[]>([])

/** タブは実在する分類から自動で作る（分類の管理はこの画面ではしない）。 */
const categories = ref<ReadingCategory[]>([])
const categoryError = ref('')
/** 本棚全体の集計（API の `totals`。分類で絞っても全冊ぶんが返る）。 */
const totals = ref<ReadingShelfTotals>({
  bookCount: 0,
  readingCount: 0,
  finishedCount: 0,
  totalMinutes: 0,
  markCount: 0,
  pdfCount: 0,
  uncategorizedCount: 0,
  myShelfCount: 0
})

/** 更新系の処理中（二重送信を防ぐ）。 */
const busy = ref(false)

/** 表紙の実体が無い（404）本は、画像をやめて代替表示に切り替える。 */
const brokenCovers = ref<number[]>([])

/** 本棚の区切り。置頂は分類ではなくフラグなので、専用の棚として先頭に出す。 */
interface ShelfSection {
  key: string
  name: string
  count: number
  books: ReadingBook[]
}

/** 「置頂」をまとめる棚のキー（分類 ID とぶつからない文字列）。 */
const PINNED_SECTION_KEY = 'pinned'

/**
 * 棚の分け方（2026-09-14 の指示で、分類で絞ったときも置頂を分けるようにした）:
 * ・「すべて」… 最上部に「置頂」（置頂フラグの本だけ）、その下に分類の表示順（未分類は最後）。
 * ・分類を選んだとき … その分類の本のうち、置頂の本は上と同じく「置頂」の棚に分けて最上部に出す。
 *   残りがその分類の棚。置頂が 1 冊も無ければ「置頂」の棚は出さない。
 * ・置頂に移した本は分類の棚には出さない（同じ本が 2 回並ばないように）
 * ・本が 1 冊も無い棚は見出しごと出さない
 */
const sections = computed<ShelfSection[]>(() => {
  const selected = selectedCategory.value
  if (selected !== 'all') {
    const category = categories.value.find((item) => item.categoryId === selected)
    const pinned = books.value.filter((book) => book.pinned)
    const rest = books.value.filter((book) => !book.pinned)
    const groups: ShelfSection[] = []
    if (pinned.length > 0) {
      groups.push({ key: PINNED_SECTION_KEY, name: '置頂', count: pinned.length, books: pinned })
    }
    groups.push({
      key: selected === 'none' ? 'none' : `cat-${selected}`,
      name: selected === 'none' ? '未分類' : category?.name ?? '分類',
      count: rest.length,
      books: rest
    })
    // 絞り込んだ棚は、置頂に移したぶんが全部でも見出しを残す（何冊あるか分かるように）
    return groups
  }

  const groups: ShelfSection[] = []
  const index = new Map<string, ShelfSection>()
  const push = (section: ShelfSection): void => {
    index.set(section.key, section)
    groups.push(section)
  }
  push({ key: PINNED_SECTION_KEY, name: '置頂', count: 0, books: [] })
  for (const category of categories.value) {
    push({ key: `cat-${category.categoryId}`, name: category.name, count: 0, books: [] })
  }
  push({ key: 'none', name: '未分類', count: 0, books: [] })

  for (const book of books.value) {
    // 置頂の本は「置頂」の棚だけに出す
    const key = book.pinned
      ? PINNED_SECTION_KEY
      : book.categoryId === null ? 'none' : `cat-${book.categoryId}`
    let section = index.get(key)
    if (section === undefined) {
      section = { key, name: book.categoryName ?? '未分類', count: 0, books: [] }
      push(section)
    }
    section.books.push(book)
  }
  // 冊数は実際に並べたカードの数（分類の bookCount は置頂を含むので使わない）
  for (const section of groups) {
    section.count = section.books.length
  }
  return groups.filter((section) => section.books.length > 0)
})

/** 区分を切り替える（1 ページ目から読み直す）。 */
function selectScope(value: ReadingScopeFilter): void {
  if (scopeFilter.value === value) return
  scopeFilter.value = value
  void loadBooks()
}

/**
 * その本をこの画面で**操作してよいか**。
 * 管理者＝全体書籍だけ / 保護者＝自分の家庭の本だけ（見えるだけの本はボタンを出さない）。
 */
function canEditBook(book: ReadingBook): boolean {
  if (auth.role === 'ADMIN') return book.scope === 'GLOBAL'
  if (auth.role === 'GUARDIAN') return book.scope === 'FAMILY'
  return false
}

/** 新規登録・修正ができるロールか（生徒はこの画面を使わない）。 */
const canManage = computed(() => auth.role === 'ADMIN' || auth.role === 'GUARDIAN')

/** いま登録する本の公開範囲の説明（サーバが決める値なので、画面は文章で示すだけ）。 */
const createScopeLabel = computed(() => (auth.role === 'ADMIN'
  ? '全体の本（すべての家庭の【図書館】に出ます）'
  : `${SCOPE_LABELS.FAMILY}（自分の家庭だけに出ます）`))

/** 絞り込みに渡す分類ID（「すべて」は指定なし、未分類は 0）。 */
function categoryParam(): number | undefined {
  if (selectedCategory.value === 'all') return undefined
  if (selectedCategory.value === 'none') return CATEGORY_NONE
  return selectedCategory.value
}

async function loadCategories(): Promise<void> {
  categoryError.value = ''
  try {
    const response = await listReadingCategories()
    categories.value = response.data.items
  } catch (caught) {
    // 分類が取れなくても本棚は出す（すべて未分類として並ぶ）
    categories.value = []
    categoryError.value = messageOf(caught, '分類を取得できませんでした。')
  }
}

async function loadBooks(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    // ページングはしない（書籍は多くない）。分類で絞ったときも同じように全件を 1 回で読む。
    // API の 1 回の上限は 100 件なので、100 冊を超えると 100 冊までしか出ない
    // （「全 N 冊」のサマリは API の totals.bookCount なので、冊数そのものは正しく出る）。
    const response = await searchReadingBooks({
      categoryId: categoryParam(),
      scope: scopeFilter.value,
      page: 1,
      size: BOOK_PAGE_SIZE
    })
    books.value = response.data.items
    totals.value = response.data.totals
  } catch (caught) {
    error.value = messageOf(caught, '書籍の一覧を取得できませんでした。')
    books.value = []
  } finally {
    loading.value = false
  }
}

async function reload(): Promise<void> {
  await Promise.all([loadCategories(), loadBooks()])
}

function selectCategory(key: CategoryKey): void {
  if (selectedCategory.value === key) return
  selectedCategory.value = key
  void loadBooks()
}

/* ---------- 表示の小物 ---------- */

function initialOf(title: string): string {
  const trimmed = title.trim()
  return trimmed === '' ? '?' : trimmed.slice(0, 1)
}

/**
 * 本文 PDF の状態を日本語にする。
 * ・実体があるとき … **ファイル名だけ**（「PDF あり」は言わなくても分かるので出さない）
 * ・実体が無いとき … 「PDF 未登録」＋元ファイル名（未登録だと分かるように残す）
 */
function pdfStateLabel(book: ReadingBook): string {
  const name = book.pdfOriginalName === null ? '' : book.pdfOriginalName.trim()
  if (book.hasPdf && book.pdfAvailable) {
    return name === '' ? 'PDF（ファイル名不明）' : name
  }
  return name === '' ? 'PDF 未登録' : `PDF 未登録（${name}）`
}

function isPdfReady(book: ReadingBook): boolean {
  return book.hasPdf && book.pdfAvailable
}

/** 本文 PDF のダウンロード URL（サーバが `attachment` で返す）。 */
function pdfDownloadUrl(book: ReadingBook): string {
  return readingPdfDownloadUrl(book.bookId, book.version)
}

function hasCoverImage(book: ReadingBook): boolean {
  return book.hasCover && book.coverAvailable && !brokenCovers.value.includes(book.bookId)
}

function coverSrc(book: ReadingBook): string {
  return readingCoverUrl(book.bookId, book.version)
}

/** 表紙の実体が無い（404）ときは代替表示に切り替える。 */
function markCoverBroken(bookId: number): void {
  if (!brokenCovers.value.includes(bookId)) {
    brokenCovers.value.push(bookId)
  }
}

/* ---------- ファイルの検証 ---------- */

function pdfRejection(file: File): string | null {
  if (!file.name.toLowerCase().endsWith('.pdf')) {
    return 'PDF ファイル（.pdf）を選んでください。'
  }
  if (file.size > PDF_MAX_BYTES) {
    return '本文 PDF は 200MB 以下にしてください。'
  }
  return null
}

function coverRejection(file: File): string | null {
  const extension = extensionOf(file.name)
  // スクリーンショットの貼り付けは拡張子が無い（'image.png'）ため、MIME 型も見る
  if (!COVER_EXTENSIONS.includes(extension) && !file.type.startsWith('image/')) {
    return '表紙は PNG・JPEG・WebP の画像を選んでください。'
  }
  if (file.size > COVER_MAX_BYTES) {
    return '表紙は 5MB 以下にしてください。'
  }
  return null
}

/* ---------- pdf.js（総ページ数の自動入力） ---------- */

/** pdf.js の `getDocument`。読む元は URL ではなく ArrayBuffer（手元のファイル）なので自前で型を置く。 */
interface PdfJsLike {
  getDocument: (source: { data: ArrayBuffer }) => { promise: Promise<PdfDocumentProxy> }
  GlobalWorkerOptions?: { workerSrc: string }
}

/** 読み込み済みの pdf.js（無ければ undefined）。 */
function pdfJsLib(): PdfJsLike | undefined {
  return (window as unknown as { pdfjsLib?: PdfJsLike }).pdfjsLib
}

let pdfjsRequest: Promise<PdfJsLike | null> | null = null

/** `/lib/pdfjs/pdf.min.js` を必要なときだけ読み込む。 */
function loadPdfJs(): Promise<PdfJsLike | null> {
  const loaded = pdfJsLib()
  if (loaded !== undefined) return Promise.resolve(loaded)
  if (pdfjsRequest !== null) return pdfjsRequest

  const request = new Promise<PdfJsLike | null>((resolve) => {
    if (typeof document === 'undefined') {
      resolve(null)
      return
    }
    let settled = false
    const finish = (lib: PdfJsLike | null): void => {
      if (settled) return
      settled = true
      window.clearTimeout(timer)
      if (lib === null) pdfjsRequest = null
      resolve(lib)
    }
    const timer = window.setTimeout(() => finish(null), PDFJS_LOAD_TIMEOUT_MS)
    const script = document.createElement('script')
    script.src = PDFJS_SCRIPT
    script.async = true
    script.addEventListener('load', () => {
      const lib = pdfJsLib()
      if (lib === undefined) {
        finish(null)
        return
      }
      if (lib.GlobalWorkerOptions !== undefined) {
        lib.GlobalWorkerOptions.workerSrc = PDFJS_WORKER
      }
      finish(lib)
    })
    script.addEventListener('error', () => finish(null))
    document.head.appendChild(script)
  })
  pdfjsRequest = request
  return request
}

/** ブラウザの FileReader で読む（`File.arrayBuffer` は環境によって無いため）。 */
function readFileBuffer(file: File): Promise<ArrayBuffer | null> {
  return new Promise((resolve) => {
    const reader = new FileReader()
    reader.addEventListener('load', () => {
      resolve(reader.result instanceof ArrayBuffer ? reader.result : null)
    })
    reader.addEventListener('error', () => resolve(null))
    try {
      reader.readAsArrayBuffer(file)
    } catch {
      resolve(null)
    }
  })
}

/** pdf.js で本文 PDF の総ページ数を数える（数えられないときは null）。 */
async function readPdfPageCount(file: File): Promise<number | null> {
  const lib = await loadPdfJs()
  if (lib === null) return null
  try {
    const data = await readFileBuffer(file)
    if (data === null) return null
    const document_ = await lib.getDocument({ data }).promise
    const count = document_.numPages
    return Number.isFinite(count) && count > 0 ? count : null
  } catch {
    return null
  }
}

/* ---------- 置頂・削除 ---------- */

async function togglePin(book: ReadingBook): Promise<void> {
  if (busy.value) return
  const label = book.pinned ? '置頂を解除' : '置頂'
  if (!window.confirm(`「${book.title}」を${label}します。よろしいですか？`)) return
  busy.value = true
  try {
    const response = await setReadingBookPinned(book.bookId, !book.pinned, book.version)
    toast.success(response.data.message)
    await reload()
  } catch (caught) {
    toast.danger(messageOf(caught, '置頂を更新できませんでした。'))
  } finally {
    busy.value = false
  }
}

async function removeBook(book: ReadingBook): Promise<void> {
  if (busy.value) return
  if (!window.confirm(
    `「${book.title}」を削除します。本文 PDF・表紙・読書記録・標記も一緒に削除されます。よろしいですか？`
  )) return
  busy.value = true
  try {
    const response = await deleteReadingBook(book.bookId)
    toast.success(response.data.message)
    await reload()
  } catch (caught) {
    toast.danger(messageOf(caught, '書籍を削除できませんでした。'))
  } finally {
    busy.value = false
  }
}

/* ---------- 書籍の登録・修正ダイアログ ---------- */

const bookDialogOpen = ref(false)
const dialogRef = ref<HTMLElement | null>(null)
const editingId = ref<number | null>(null)
const editingVersion = ref<number | null>(null)
/** 修正中の書籍（既存の本文 PDF・表紙の状態を出すために持つ）。 */
const editingBook = ref<ReadingBook | null>(null)
const tagInput = ref('')

/** 保存時に送る本文 PDF（選び直したときだけ入る。1 冊 1 PDF なので必ず置き換える）。 */
const pendingPdf = ref<File | null>(null)
const pendingPdfName = ref('')
/** 選び直した表紙画像（1 冊 1 表紙なので必ず置き換える）。 */
const pendingCover = ref<File | null>(null)
const pendingCoverName = ref('')
/** 選んだ表紙の見本（貼り付けたスクリーンショットでも中身が見えるように）。 */
const pendingCoverPreview = ref('')
/** 総ページ数の自動入力の状況を出す一言。 */
const pdfPageNote = ref('')

const dialogPdfInput = ref<HTMLInputElement | null>(null)
const dialogCoverInput = ref<HTMLInputElement | null>(null)

/** 本文 PDF のドロップゾーンがドラッグ中か。 */
const pdfDragging = ref(false)
/** 表紙のドロップゾーンがドラッグ中か。 */
const coverDragging = ref(false)

const bookForm = reactive({
  title: '',
  author: '',
  language: '英語' as ReadingLanguage,
  difficulty: 'Elementary' as ReadingDifficulty,
  /** 読書ステータスは画面に出さない。修正時は今の値のまま送る。 */
  status: '未着手' as ReadingStatus,
  totalPages: null as number | null,
  pinned: false,
  categoryId: null as number | null,
  tags: [] as string[],
  summary: '',
  note: ''
})

const fieldErrors = reactive<Record<string, string>>({})

/** 分類セレクトの選択値（分類ID の文字列・未分類は空値・新規追加は `CATEGORY_NEW`）。 */
const categoryValue = ref('')
/** 「＋ 新しい分類を追加…」を選んでいるときに作る分類名。 */
const newCategoryName = ref('')
/** 分類セレクトで「新しい分類を追加…」を選んでいるか。 */
const addingCategory = computed(() => categoryValue.value === CATEGORY_NEW)

function onCategorySelected(): void {
  if (categoryValue.value === CATEGORY_NEW) {
    // 新しい分類を作る経路。分類ID は保存時に決まるので、ここでは選ばない
    bookForm.categoryId = null
    newCategoryName.value = ''
    return
  }
  bookForm.categoryId = categoryValue.value === '' ? null : Number(categoryValue.value)
}

/** 分類セレクトの表示をフォームの値に合わせる（ダイアログを開くたびに呼ぶ）。 */
function syncCategoryValue(): void {
  categoryValue.value = bookForm.categoryId === null ? '' : String(bookForm.categoryId)
  newCategoryName.value = ''
}

/**
 * ダイアログに出す本文 PDF の状態。
 * ファイル名は目立たせたいので、前後の文と分けて返す（画面では `data-rd-pdf-name` を強調する）。
 */
const dialogPdfState = computed<{ lead: string; name: string; note: string }>(() => {
  if (pendingPdf.value !== null) {
    return { lead: '選択中: ', name: pendingPdfName.value, note: '（保存すると差し替えます）' }
  }
  const book = editingBook.value
  if (book === null) {
    return { lead: '本文 PDF はまだ登録されていません。', name: '', note: '' }
  }
  const original = book.pdfOriginalName === null ? '' : book.pdfOriginalName.trim()
  if (book.hasPdf && book.pdfAvailable) {
    return {
      lead: '現在: ',
      name: original === '' ? 'PDF（ファイル名不明）' : original,
      note: '（選び直すと差し替えます）'
    }
  }
  // 行はあるが実体が無い（2.0 から移行したぶん）。元のファイル名は残しておく
  // （ファイル名だけを強調したいので、括弧は前後の文に分けて持たせる）
  if (original === '') {
    return { lead: '現在: PDF 未登録', name: '', note: '（選び直すと差し替えます）' }
  }
  return {
    lead: '現在: PDF 未登録（',
    name: original,
    note: '）（選び直すと差し替えます）'
  }
})

/**
 * 表紙の見本。選び直したときはその画像、まだ選んでいなければ**登録済みの表紙**を出す
 * （2026-09-14 の指示。修正のとき、いまの表紙がダイアログで見えるように）。
 */
const coverPreviewSrc = computed(() => {
  if (pendingCoverPreview.value !== '') return pendingCoverPreview.value
  const book = editingBook.value
  if (book !== null && hasCoverImage(book)) return readingCoverUrl(book.bookId, book.version)
  return ''
})

/** 見本がいま選んだものか、登録済みのものか（説明文に使う）。 */
const coverPreviewIsPending = computed(() => pendingCoverPreview.value !== '')

/** ダイアログに出す表紙の状態。 */
const dialogCoverState = computed(() => {
  if (pendingCover.value !== null) {
    return `選択中: ${pendingCoverName.value}（保存すると差し替えます）`
  }
  const book = editingBook.value
  if (book === null) return '表紙はまだ登録されていません。'
  if (book.hasCover && book.coverAvailable) return '現在: 表紙あり（選び直すと差し替えます）'
  return '現在: 表紙なし'
})

/** 表紙の見本に使うオブジェクトURLを解放する。 */
function releaseCoverPreview(): void {
  if (pendingCoverPreview.value !== '') {
    URL.revokeObjectURL(pendingCoverPreview.value)
    pendingCoverPreview.value = ''
  }
}

/**
 * 選んだ表紙の見本を作る。
 * 見本は補助なので、作れない環境（jsdom など）では出さないだけにして、選択自体は止めない。
 */
function previewCover(file: File): void {
  if (typeof URL.createObjectURL !== 'function') return
  pendingCoverPreview.value = URL.createObjectURL(file)
}

/** フォームに初期値を入れる（book が null なら新規。言語は既定 英語）。 */
function fillForm(book: ReadingBook | null): void {
  bookForm.title = book?.title ?? ''
  bookForm.author = book?.author ?? ''
  bookForm.language = book?.language ?? '英語'
  bookForm.difficulty = book?.difficulty ?? 'Elementary'
  bookForm.status = book?.status ?? '未着手'
  bookForm.totalPages = book?.totalPages ?? null
  bookForm.pinned = book?.pinned ?? false
  bookForm.categoryId = book?.categoryId ?? null
  bookForm.tags = [...(book?.tags ?? [])]
  bookForm.summary = book?.summary ?? ''
  bookForm.note = book?.note ?? ''
  tagInput.value = ''
  pendingPdf.value = null
  pendingPdfName.value = ''
  releaseCoverPreview()
  pendingCover.value = null
  pendingCoverName.value = ''
  pdfPageNote.value = ''
  pdfDragging.value = false
  coverDragging.value = false
  if (dialogPdfInput.value !== null) dialogPdfInput.value.value = ''
  if (dialogCoverInput.value !== null) dialogCoverInput.value.value = ''
  syncCategoryValue()
}

async function openCreate(): Promise<void> {
  editingId.value = null
  editingVersion.value = null
  editingBook.value = null
  fillForm(null)
  clearErrors(fieldErrors)
  bookDialogOpen.value = true
  await nextTick()
  // 表紙の貼り付け（Ctrl+V）を受け取れるようにする
  dialogRef.value?.focus()
}

async function openEdit(book: ReadingBook): Promise<void> {
  editingId.value = book.bookId
  editingVersion.value = book.version
  editingBook.value = book
  fillForm(book)
  clearErrors(fieldErrors)
  bookDialogOpen.value = true
  await nextTick()
  dialogRef.value?.focus()
}

function closeBookDialog(): void {
  bookDialogOpen.value = false
  pdfDragging.value = false
  coverDragging.value = false
}

function addTag(): void {
  const value = tagInput.value.trim()
  if (value === '') return
  addTagValue(value)
  tagInput.value = ''
}

function addTagValue(tag: string): void {
  if (!bookForm.tags.includes(tag)) {
    bookForm.tags.push(tag)
  }
}

function removeTag(tag: string): void {
  bookForm.tags = bookForm.tags.filter((item) => item !== tag)
}

/* ---------- 本文 PDF の選択（クリック・ドラッグ＆ドロップ） ---------- */

/** 選ばれた本文 PDF を受け取る（1 冊 1 PDF なので必ず置き換える）。 */
function acceptPdf(file: File): void {
  const rejection = pdfRejection(file)
  if (rejection !== null) {
    toast.warning(rejection)
    if (dialogPdfInput.value !== null) dialogPdfInput.value.value = ''
    return
  }
  pendingPdf.value = file
  pendingPdfName.value = file.name
  pdfPageNote.value = '総ページ数を数えています...'
  // 数え直すまで前の値は消しておく（別の PDF を選んだのに前のページ数が残らないように）
  bookForm.totalPages = null
  void applyPdfPageCount(file)
}

function onDialogPdfSelected(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  input.value = ''
  if (file === null) return
  acceptPdf(file)
}

function onPdfDrop(event: DragEvent): void {
  pdfDragging.value = false
  const file = event.dataTransfer?.files?.[0] ?? null
  if (file === null) return
  acceptPdf(file)
}

/**
 * pdf.js で数えたページ数をフォームに入れる（画面には目立つ表示で出す）。
 * 数えられないときは、その旨だけ案内する（入力欄は無い）。
 */
async function applyPdfPageCount(file: File): Promise<void> {
  const count = await readPdfPageCount(file)
  if (pendingPdf.value !== file) return
  if (count === null) {
    pdfPageNote.value = '総ページ数を数えられませんでした。'
    return
  }
  bookForm.totalPages = count
  pdfPageNote.value = ''
}

/* ---------- 表紙の選択（クリック・ドラッグ＆ドロップ・貼り付け） ---------- */

/** 選ばれた表紙画像を受け取る（1 冊 1 表紙なので必ず置き換える）。 */
function acceptCover(file: File): void {
  const rejection = coverRejection(file)
  if (rejection !== null) {
    toast.warning(rejection)
    if (dialogCoverInput.value !== null) dialogCoverInput.value.value = ''
    return
  }
  releaseCoverPreview()
  pendingCover.value = file
  pendingCoverName.value = file.name
  previewCover(file)
}

function onDialogCoverSelected(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  input.value = ''
  if (file === null) return
  acceptCover(file)
}

function onCoverDrop(event: DragEvent): void {
  coverDragging.value = false
  const file = event.dataTransfer?.files?.[0] ?? null
  if (file === null) return
  acceptCover(file)
}

/**
 * スクリーンショットの貼り付け（Ctrl+V）で表紙を選ぶ。
 * `clipboardData.items` の画像だけを取り込み、文字などの貼り付けは無視する
 * （入力欄への文字の貼り付けを横取りしないよう、入力欄の上では何もしない）。
 */
function onCoverPaste(event: Event): void {
  const target = event.target as HTMLElement | null
  if (target !== null && /^(INPUT|TEXTAREA)$/.test(target.tagName)) return
  const data = (event as ClipboardEvent).clipboardData
  if (data === null || data === undefined) return
  const item = Array.from(data.items ?? []).find(
    (entry) => entry.kind === 'file' && entry.type.startsWith('image/')
  )
  if (item === undefined) return
  const file = item.getAsFile()
  if (file === null) return
  // 貼り付けた画像はファイル名を持たないので、表紙と分かる名前を付ける
  const named = file.name.trim() === '' || !file.name.includes('.') ? renameCover(file) : file
  acceptCover(named)
}

/** 名前の無い貼り付け画像に、拡張子付きの名前を付ける。 */
function renameCover(file: File): File {
  const extension = file.type === 'image/jpeg' ? 'jpg' : file.type.replace('image/', '') || 'png'
  return new File([file], `表紙.${extension}`, { type: file.type })
}

/* ---------- 保存 ---------- */

async function saveBook(): Promise<void> {
  if (busy.value) return
  clearErrors(fieldErrors)

  const title = bookForm.title.trim()
  const author = bookForm.author.trim()
  // 総ページ数は必須にしない（PDF を選ぶと pdf.js が自動で入れる）。
  // 空のまま保存されたときは 1 で登録する。0 や負の値は今までどおりエラー
  const pagesInput = bookForm.totalPages
  const pagesEmpty = pagesInput === null || `${pagesInput}`.trim() === ''
  const totalPages = pagesEmpty ? DEFAULT_TOTAL_PAGES : Number(pagesInput)
  const newCategory = newCategoryName.value.trim()

  if (title === '') {
    fieldErrors.title = '書籍名を入力してください。'
  }
  if (author === '') {
    fieldErrors.author = '作者を入力してください。'
  }
  if (!pagesEmpty && (!Number.isFinite(totalPages) || totalPages < 1)) {
    fieldErrors.totalPages = '総ページ数は1以上で入力してください。'
  }
  if (addingCategory.value && newCategory === '') {
    fieldErrors.newCategory = '新しい分類名を入力してください。'
  }
  if (Object.keys(fieldErrors).length > 0) {
    toast.warning('入力内容を確認してください。')
    return
  }

  busy.value = true
  try {
    // 「＋ 新しい分類を追加…」を選んでいるときは、先に分類を作ってその ID を使う
    let categoryId = bookForm.categoryId
    if (addingCategory.value) {
      const created = await createReadingCategory({ name: newCategory })
      categoryId = created.data.category.categoryId
    }

    const body: ReadingBookSave = {
      title,
      author,
      language: bookForm.language,
      difficulty: bookForm.difficulty,
      status: bookForm.status,
      totalPages,
      pinned: bookForm.pinned,
      categoryId,
      tags: [...bookForm.tags],
      summary: bookForm.summary.trim() === '' ? undefined : bookForm.summary.trim(),
      note: bookForm.note.trim() === '' ? undefined : bookForm.note.trim(),
      version: editingVersion.value ?? undefined
    }

    let bookId: number
    let message: string
    if (editingId.value === null) {
      const response = await createReadingBook(body)
      bookId = response.data.book.bookId
      message = response.data.message
    } else {
      bookId = editingId.value
      const response = await updateReadingBook(bookId, body)
      message = response.data.message
    }

    // 本文 PDF → 表紙 の順に送る（1 冊 1 ファイルなので選び直しは差し替えになる）
    if (pendingPdf.value !== null) {
      // 総ページ数は画面に入っている値（pdf.js の数えた値を手で直せる）を使う
      await uploadReadingPdf(bookId, pendingPdf.value, totalPages)
    }
    if (pendingCover.value !== null) {
      await uploadReadingCover(bookId, pendingCover.value)
    }

    toast.success(message)
    releaseCoverPreview()
    bookDialogOpen.value = false
    await reload()
  } catch (caught) {
    toast.danger(messageOf(caught, '書籍を保存できませんでした。'))
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  void reload()
})
</script>

<template>
  <div class="rd-page">
    <section class="card">
      <!-- 1 行目: 見出し（本棚）と操作（再読み込み・新規） -->
      <div class="card__header">
        <h2 class="card__title">本棚</h2>
        <div class="rd-shelf-actions">
          <button
            type="button" class="btn btn--secondary btn--sm" data-rd-refresh
            :disabled="loading" @click="reload"
          >
            <AppIcon name="rotate" size="sm" /> 再読み込み
          </button>
          <button
            v-if="canManage" type="button" class="btn btn--primary btn--sm" data-rd-add @click="openCreate"
          >
            <AppIcon name="plus" size="sm" /> 新規
          </button>
        </div>
      </div>

      <!-- 区分（公開範囲）。管理者＝全体の本 / 保護者＝家庭の本（2026-09-14 の決定 Q3） -->
      <div class="rd-scopes" role="tablist" aria-label="区分">
        <button
          v-for="option in scopeOptions" :key="option.value" type="button" class="rd-scope"
          role="tab" :aria-selected="scopeFilter === option.value"
          :class="{ 'is-active': scopeFilter === option.value }"
          :data-rd-scope-filter="option.value.toLowerCase()" @click="selectScope(option.value)"
        >
          {{ option.label }}
        </button>
        <span class="cell-muted rd-scopes__note" data-rd-scope-note>{{ createScopeLabel }}</span>
      </div>

      <!-- 3 行目: 分類（閲覧画面の本棚タブと同じ丸いボタン）と、その右に冊数のサマリ -->
      <div class="rd-cats">
        <div class="rd-cats__row">
          <div class="rd-cat-list" role="tablist" aria-label="本棚の分類">
            <button
              type="button" class="rd-cat" role="tab" :aria-selected="selectedCategory === 'all'"
              :class="{ 'is-active': selectedCategory === 'all' }" data-rd-category="all"
              @click="selectCategory('all')"
            >
              すべて
            </button>

            <button
              v-for="category in categories" :key="category.categoryId" type="button"
              class="rd-cat" role="tab" :aria-selected="selectedCategory === category.categoryId"
              :class="{ 'is-active': selectedCategory === category.categoryId }"
              :data-rd-category="category.categoryId" @click="selectCategory(category.categoryId)"
            >
              {{ category.name }}（{{ category.bookCount }}）
            </button>

            <button
              type="button" class="rd-cat" role="tab" :aria-selected="selectedCategory === 'none'"
              :class="{ 'is-active': selectedCategory === 'none' }" data-rd-category="none"
              @click="selectCategory('none')"
            >
              未分類
            </button>
          </div>

          <span class="cell-muted rd-cats__summary" data-rd-summary>
            全 {{ totals.bookCount }} 冊 ・ 読書中 {{ totals.readingCount }} 冊 ・ 読了 {{ totals.finishedCount }} 冊
          </span>
        </div>
        <p v-if="categoryError" class="alert alert--warning" data-rd-category-error>{{ categoryError }}</p>
      </div>

      <p v-if="error" class="alert alert--danger">{{ error }}</p>
      <p v-else-if="loading" class="rd-page__loading">読み込んでいます...</p>
      <p v-else-if="books.length === 0" class="rd-page__empty" data-rd-shelf-empty>
        この棚に書籍がありません。書籍を登録するか、区分・分類を選び直してください。
      </p>

      <template v-else>
        <section
          v-for="section in sections" :key="section.key" class="rd-shelf-section"
          :data-rd-shelf-section="section.key"
        >
          <h3
            class="rd-shelf-section__title"
            :class="{ 'is-pinned': section.key === PINNED_SECTION_KEY }"
          >
            <AppIcon v-if="section.key === PINNED_SECTION_KEY" name="bookmark" size="sm" />
            {{ section.name }}<span class="rd-shelf-section__count">{{ section.count }} 冊</span>
          </h3>
          <div class="rd-shelf" data-rd-shelf>
            <article
              v-for="book in section.books" :key="book.bookId" class="rd-book"
              :class="{ 'is-pinned': book.pinned }" :data-rd-book="book.bookId"
            >
              <div class="rd-book__cover">
                <img
                  v-if="hasCoverImage(book)" class="rd-book__cover-img" :src="coverSrc(book)"
                  :alt="`${book.title} の表紙`" data-rd-cover @error="markCoverBroken(book.bookId)"
                >
                <span v-else class="rd-book__cover-empty" data-rd-cover data-rd-cover-empty>
                  <span class="rd-book__cover-initial">{{ initialOf(book.title) }}</span>
                  <span class="rd-book__cover-note">表紙なし</span>
                </span>
              </div>

              <div class="rd-book__main">
                <div class="rd-book__head">
                  <h3 class="rd-book__title" data-rd-title>{{ book.title }}</h3>
                  <span v-if="book.pinned" class="rd-book__pin" data-rd-pin-badge>
                    <AppIcon name="bookmark" size="sm" /> 置頂
                  </span>
                </div>

                <p class="rd-book__author" data-rd-author>{{ book.author }}</p>

                <!-- 読み手側（本棚タブ）と同じ並び: 難易度 → 言語 → 分類 → ページ数 -->
                <div class="rd-book__badges">
                  <span class="badge badge--outline" data-rd-difficulty>{{ DIFFICULTY_LABELS[book.difficulty] }}</span>
                  <span class="badge badge--outline" data-rd-language>{{ book.language }}</span>
                  <span class="badge badge--neutral" data-rd-category-name>{{ book.categoryName ?? '未分類' }}</span>
                  <!-- 全体書籍（管理者の本）はバッジを出さない。家庭の本だけ分かるようにする -->
                  <span v-if="book.scope === 'FAMILY'" class="badge badge--info" data-rd-scope-badge>
                    {{ SCOPE_LABELS.FAMILY }}<template v-if="book.ownerFamilyLabel">（{{ book.ownerFamilyLabel }}）</template>
                  </span>
                  <span class="rd-book__pages" data-rd-pages>全 {{ book.totalPages }} 頁</span>
                </div>

                <div class="rd-book__tags" data-rd-tags>
                  <template v-if="book.tags.length > 0">
                    <span v-for="tag in book.tags" :key="tag" class="rd-tag">{{ tag }}</span>
                  </template>
                  <span v-else class="rd-tag">タグ未設定</span>
                </div>

                <!-- 最後の行: 左に本文 PDF の状態、右に操作アイコン。
                     操作を別の行に置くとカードの下が間延びするので、メタ情報と同じ行に並べる -->
                <div class="rd-book__foot">
                  <p
                    class="rd-book__pdf" :class="isPdfReady(book) ? 'is-ready' : 'is-missing'"
                    :title="pdfStateLabel(book)" data-rd-pdf-state
                  >
                    <AppIcon name="file-pdf" size="sm" />
                    <!-- 長いファイル名は 1 行に収めて省略記号にする（全文は title で見せる） -->
                    <span class="rd-book__pdf-name">{{ pdfStateLabel(book) }}</span>
                  </p>

                  <!-- 操作はアイコンのみ（ダウンロード・修正・置頂・削除）。PDF の添付は編集ダイアログで行う。
                       直せない本（保護者から見た全体書籍・管理者から見た家庭の本）は操作を出さない -->
                  <div class="rd-book__actions">
                    <a
                      v-if="isPdfReady(book)" class="btn btn--icon"
                      :href="pdfDownloadUrl(book)" :title="`${pdfStateLabel(book)} をダウンロード`"
                      :aria-label="`${book.title} の本文 PDF をダウンロード`" :data-rd-download="book.bookId"
                    >
                      <AppIcon name="download" />
                    </a>
                    <button
                      v-if="canEditBook(book)"
                      type="button" class="btn btn--icon" title="修正" :aria-label="`${book.title} を修正`"
                      :data-rd-edit="book.bookId" @click="openEdit(book)"
                    >
                      <AppIcon name="edit" class="icon--edit" />
                    </button>
                    <button
                      v-if="canEditBook(book)"
                      type="button" class="btn btn--icon" :class="{ 'is-active': book.pinned }"
                      :title="book.pinned ? '置頂を解除' : '置頂'"
                      :aria-label="book.pinned ? `${book.title} の置頂を解除` : `${book.title} を置頂`"
                      :aria-pressed="book.pinned" :disabled="busy"
                      :data-rd-pin="book.bookId" @click="togglePin(book)"
                    >
                      <AppIcon name="bookmark" />
                    </button>
                    <button
                      v-if="canEditBook(book)"
                      type="button" class="btn btn--icon is-danger" title="削除"
                      :aria-label="`${book.title} を削除`" :disabled="busy"
                      :data-rd-delete="book.bookId" @click="removeBook(book)"
                    >
                      <AppIcon name="trash" />
                    </button>
                  </div>
                </div>
              </div>
            </article>
          </div>
        </section>
      </template>
    </section>

    <!-- 書籍の登録・修正 -->
    <div v-if="bookDialogOpen" class="overlay">
      <section
        ref="dialogRef" class="dialog dialog--lg" role="dialog" aria-modal="true" tabindex="-1"
        aria-labelledby="rdDialogTitle" data-rd-dialog @paste="onCoverPaste"
      >
        <div class="dialog__head">
          <h2 id="rdDialogTitle" class="dialog__title">
            <AppIcon name="book" size="sm" /> {{ editingId === null ? '書籍の登録' : '書籍の修正' }}
          </h2>
          <button type="button" class="dialog__close" aria-label="閉じる" data-rd-dialog-close @click="closeBookDialog">
            <AppIcon name="x" size="sm" />
          </button>
        </div>

        <div class="dialog__body">
          <div class="rd-dialog-grid">
            <div class="field">
              <label class="field__label" for="rdTitle">書籍名<span class="rd-required">必須</span></label>
              <input
                id="rdTitle" v-model="bookForm.title" class="input" type="text" maxlength="150"
                placeholder="例: The Secret Garden" :class="{ 'is-invalid': fieldErrors.title !== undefined }"
              >
              <p v-if="fieldErrors.title" class="field__error">{{ fieldErrors.title }}</p>
            </div>
            <div class="field">
              <label class="field__label" for="rdAuthor">作者<span class="rd-required">必須</span></label>
              <input
                id="rdAuthor" v-model="bookForm.author" class="input" type="text" maxlength="120"
                placeholder="例: Frances Hodgson Burnett" :class="{ 'is-invalid': fieldErrors.author !== undefined }"
              >
              <p v-if="fieldErrors.author" class="field__error">{{ fieldErrors.author }}</p>
            </div>
            <div class="field">
              <label class="field__label" for="rdLanguage">言語</label>
              <select id="rdLanguage" v-model="bookForm.language" class="select">
                <option v-for="option in LANGUAGE_OPTIONS" :key="option" :value="option">{{ option }}</option>
              </select>
            </div>
            <div class="field">
              <label class="field__label" for="rdDifficulty">難易度</label>
              <select id="rdDifficulty" v-model="bookForm.difficulty" class="select">
                <option v-for="option in DIFFICULTY_OPTIONS" :key="option" :value="option">
                  {{ DIFFICULTY_LABELS[option] }}
                </option>
              </select>
            </div>
            <div class="field">
              <span class="field__label">公開範囲</span>
              <!-- 公開範囲はサーバが決める（管理者＝GLOBAL / 保護者＝FAMILY 固定）。
                   ここで選ばせないので、いま何を作るのかを文章で示す。 -->
              <p class="rd-hint" data-rd-dialog-scope>{{ createScopeLabel }}</p>
            </div>
            <div class="field">
              <label class="field__label" for="rdCategory">分類（ジャンル）</label>
              <select id="rdCategory" v-model="categoryValue" class="select" @change="onCategorySelected">
                <option value="">未分類</option>
                <option v-for="category in categories" :key="category.categoryId" :value="String(category.categoryId)">
                  {{ category.name }}
                </option>
                <option :value="CATEGORY_NEW">＋ 新しい分類を追加…</option>
              </select>
            </div>
            <div v-if="addingCategory" class="field">
              <label class="field__label" for="rdNewCategory">
                新しい分類名<span class="rd-required">必須</span>
              </label>
              <input
                id="rdNewCategory" v-model="newCategoryName" class="input" type="text" maxlength="50"
                placeholder="例: 小説"
                :class="{ 'is-invalid': fieldErrors.newCategory !== undefined }"
              >
              <p v-if="fieldErrors.newCategory" class="field__error">{{ fieldErrors.newCategory }}</p>
              <p class="rd-hint">保存すると、この分類を作ってから書籍を登録します。</p>
            </div>
            <div class="field">
              <span class="field__label">置頂</span>
              <label class="rd-check">
                <input id="rdPinned" v-model="bookForm.pinned" type="checkbox">
                <span>この書籍を本棚の先頭に置く</span>
              </label>
            </div>
            <div class="field field--wide">
              <label class="field__label" for="rdTags">タグ</label>
              <div class="rd-tag-input">
                <div class="rd-tag-input__list">
                  <span v-for="tag in bookForm.tags" :key="tag" class="rd-tag-input__chip">
                    {{ tag }}
                    <button
                      type="button" class="rd-tag-input__remove" :aria-label="`${tag} を削除`"
                      :data-rd-tag-remove="tag" @click="removeTag(tag)"
                    >
                      <AppIcon name="x" size="sm" />
                    </button>
                  </span>
                  <span v-if="bookForm.tags.length === 0" class="rd-hint">タグは未設定です。</span>
                </div>
                <div class="rd-tag-input__row">
                  <input
                    id="rdTags" v-model="tagInput" class="input" type="text" maxlength="30"
                    placeholder="タグを入力して Enter" @keyup.enter="addTag"
                  >
                  <button type="button" class="btn btn--secondary btn--sm" data-rd-tag-add @click="addTag">
                    <AppIcon name="plus" size="sm" /> 追加
                  </button>
                </div>
                <div class="rd-tag-suggestions">
                  <button
                    v-for="tag in TAG_SUGGESTIONS" :key="tag" type="button" class="rd-tag-suggestion"
                    :data-rd-tag-suggest="tag" @click="addTagValue(tag)"
                  >
                    {{ tag }}
                  </button>
                </div>
              </div>
            </div>

            <!-- 本文 PDF と表紙は同じ行に左右で並べる（ダイアログが縦長にならないように） -->
            <div class="field field--wide rd-upload-row">
              <div class="field">
                <span id="rdPdfLabel" class="field__label">本文 PDF（読むためのファイル・1 冊に 1 つ）</span>
                <!-- 枠全体がファイル選択の入口（ネイティブの [ファイル選択] は見せない） -->
                <label
                  class="dropzone rd-drop" :class="{ 'is-dragging': pdfDragging }" data-rd-pdf-drop
                  aria-labelledby="rdPdfLabel" @dragover.prevent="pdfDragging = true"
                  @dragleave="pdfDragging = false" @drop.prevent="onPdfDrop"
                >
                  <AppIcon name="upload" />
                  <span class="rd-drop__title">ここに PDF をドロップ または クリックして選択</span>
                  <span class="rd-drop__note">200MB まで・選び直すと前の PDF と差し替えます</span>
                  <input
                    id="rdPdf" ref="dialogPdfInput" type="file" :accept="PDF_ACCEPT" class="rd-drop__input"
                    data-rd-pdf-input @change="onDialogPdfSelected"
                  >
                </label>
                <!-- 選んだ PDF のファイル名（目立たせる） -->
                <p class="rd-file-state" data-rd-dialog-pdf-state>
                  <span>{{ dialogPdfState.lead }}</span><span
                    v-if="dialogPdfState.name !== ''" class="rd-file-name" data-rd-pdf-name
                  >{{ dialogPdfState.name }}</span><span>{{ dialogPdfState.note }}</span>
                </p>
                <!-- 総ページ数は入力させず、PDF から数えた値を出す（目立つ色） -->
                <p v-if="bookForm.totalPages !== null" class="rd-pdf-pages" data-rd-pdf-pages>
                  <AppIcon name="file-pdf" size="sm" />
                  総ページ数 <strong>{{ bookForm.totalPages }}</strong> 頁
                </p>
                <p v-if="pdfPageNote !== ''" class="rd-hint" data-rd-pdf-page-note>{{ pdfPageNote }}</p>
              </div>

              <div class="field">
                <span id="rdCoverLabel" class="field__label">
                  表紙画像（PNG・JPEG・WebP・5MB まで・1 冊に 1 枚）
                </span>
                <label
                  class="dropzone rd-drop" :class="{ 'is-dragging': coverDragging }" data-rd-cover-drop
                  aria-labelledby="rdCoverLabel" @dragover.prevent="coverDragging = true"
                  @dragleave="coverDragging = false" @drop.prevent="onCoverDrop"
                >
                  <template v-if="coverPreviewSrc !== ''">
                    <img
                      class="rd-drop__preview" :src="coverPreviewSrc"
                      :alt="coverPreviewIsPending ? '選択中の表紙' : `${bookForm.title} の現在の表紙`"
                    >
                    <span class="rd-drop__note">
                      {{ coverPreviewIsPending ? '選択中の表紙' : '現在の表紙' }}（クリックで選び直し）
                    </span>
                  </template>
                  <template v-else>
                    <AppIcon name="image" />
                    <span class="rd-drop__title">ここに画像をドロップ または クリックして選択</span>
                    <span class="rd-drop__note">スクリーンショットはこのまま Ctrl+V で貼り付けられます</span>
                  </template>
                  <input
                    id="rdCover" ref="dialogCoverInput" type="file" :accept="COVER_ACCEPT" class="rd-drop__input"
                    data-rd-cover-input @change="onDialogCoverSelected"
                  >
                </label>
                <p class="rd-hint" data-rd-dialog-cover-state>{{ dialogCoverState }}</p>
              </div>
            </div>

            <div class="field field--wide">
              <label class="field__label" for="rdSummary">概要</label>
              <textarea
                id="rdSummary" v-model="bookForm.summary" class="textarea" rows="3" maxlength="2000"
                placeholder="この本の紹介、読み方の方針など"
              ></textarea>
            </div>
            <div class="field field--wide">
              <label class="field__label" for="rdNote">備考</label>
              <textarea id="rdNote" v-model="bookForm.note" class="textarea" rows="2" maxlength="2000"></textarea>
            </div>
          </div>
          <p class="rd-hint">
            総ページ数は本文 PDF から自動で数えて表示します（入力は要りません）。
            PDF を選ばずに登録したときは 1 頁として登録し、あとから PDF を入れると頁数が更新されます。
          </p>
        </div>

        <div class="dialog__foot">
          <button type="button" class="btn btn--secondary" data-rd-cancel @click="closeBookDialog">
            キャンセル
          </button>
          <button type="button" class="btn btn--primary" data-rd-save :disabled="busy" @click="saveBook">
            <AppIcon name="check" size="sm" /> {{ busy ? '保存中...' : '保存' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* 置頂チェックのラベル */
.rd-check {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--color-text);
  cursor: pointer;
}
</style>
