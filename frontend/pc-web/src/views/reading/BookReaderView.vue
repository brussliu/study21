<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, formatIsoDateTime, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import { paginationItems } from '@/features/pagination/pagination'
import {
  CATEGORY_NONE,
  DIFFICULTY_LABELS,
  MARK_TYPE_COLORS,
  MARK_TYPE_LABELS,
  MARK_TYPE_OPTIONS,
  RECORD_PAGE_SIZES,
  SCOPE_LABELS,
  STATUS_BADGES,
  STATUS_LABELS,
  addReadingBookToShelf,
  createReadingMark,
  deleteAllReadingMarks,
  lookupReadingWord,
  type ReadingLookupResult,
  deleteReadingMark,
  fetchReadingBook,
  listReadingCategories,
  readingCoverUrl,
  readingPdfUrl,
  removeReadingBookFromShelf,
  saveReadingRecord,
  searchReadingBooks,
  searchReadingMarks,
  searchReadingRecords,
  type ReadingBook,
  type ReadingBookDetail,
  type ReadingCategory,
  type ReadingMark,
  type ReadingMarkSave,
  type ReadingMarkType,
  type ReadingRecord,
  type ReadingShelfTotals
} from '@/api/reading'
import {
  PDFJS_WORKER_URL,
  ensurePdfJs,
  isMissingPdfError,
  type PdfDocumentProxy,
  type PdfViewport
} from '@/features/reading/readingPdf'
import ShelfCaseList, { type ShelfSection } from '@/components/reading/ShelfCaseList.vue'
import {
  SHELF_BACKGROUNDS,
  normalizeShelfBg,
  readShelfBg,
  shelfBgLabel,
  writeShelfBg
} from '@/features/reading/shelfBackgrounds'
import { OPENING_MS, toneClass } from '@/features/reading/shelfLook'
import { displayReadPercent } from '@/features/reading/readingProgress'
import {
  TTS_LANG_CODES,
  TTS_LANG_LABELS,
  TTS_RATE_OPTIONS,
  buildTtsSegments,
  bookLangToTtsLang,
  createReadingTtsProvider,
  findVoice,
  missingVoiceNote,
  normalizeTtsRate,
  rateLabel,
  readTtsPrefs,
  resolveVoice,
  segmentIndexAt,
  segmentLangs,
  textOffsetOf,
  textOfItems,
  textRangeOf,
  voiceLabel,
  voicesForLang,
  writeTtsPrefs,
  type TtsLang,
  type TtsPrefs,
  type TtsSegment,
  type TtsVoice
} from '@/features/reading/readingSpeech'
import '@/features/reading/reading-reader.css'
import '@/features/reading/reading-shelf.css'

/**
 * 読書管理【書籍閲覧】。使うのは**生徒**。
 *
 * 2.0 の英語読書（english_reading_reader.jsp ＋ js/english_reading_reader.js）と同じ体験にする:
 * ・本棚（分類＝棚ごと。書籍管理と同じ分類を使う）
 * ・本を開くと**本文 PDF を pdf.js で canvas に描画**して読める（CDN ではなく同梱の pdf.js）
 * ・標記（語彙・ハイライト・下線・メモ・手書き）を PDF に重ねる
 * ・読書記録（今回読んだ範囲・時間・メモ）と読書履歴
 *   （本棚の「読書履歴」タブ＝全書籍／閲覧画面＝この本。日付とページ番号で絞れる）
 * ・音声読み上げ（TTS。phase 1 は**ブラウザ内蔵の音声合成**だけ。`readingSpeech.ts`）。
 *   表示中のページを文ごとに読み、読んでいる文をハイライトする（標記ではないので DB には書かない）。
 *   本文を選んでいれば**その選択の起点を含む文から**読み始める（選択が無ければ先頭から。
 *   標記ツールを選んでいるときは選択を使わない＝先頭から。`ttsStartIndex()`）
 *
 * **置かないもの**: 書籍の登録・修正・削除・アップロード・分類の管理（保護者の書籍管理画面）。
 */

/** 本棚 1 回の取得冊数。 */
const SHELF_PAGE_SIZE = 24
/** 読書履歴 1 ページの件数の既定（選択肢は RECORD_PAGE_SIZES の 20 / 50 / 100 件）。 */
const DEFAULT_HISTORY_PAGE_SIZE = RECORD_PAGE_SIZES[0]
/** 標記一覧でまとめて取る件数。 */
const MARKS_PAGE_SIZE = 200
/** 本文 PDF の表示倍率の下限・上限と 1 回の拡大率。 */
const MIN_SCALE = 0.3
const MAX_SCALE = 4
const ZOOM_STEP = 1.2
/** 「高さに合わせる」で使う表示可能な高さの下限（px）。 */
const MIN_STAGE_HEIGHT = 320
/** 「高さに合わせる」で枠の下端に残す余白（枠の内側の余白と枠線ぶん。px）。 */
const STAGE_BOTTOM_GAP = 40
/** ページ上をクリックして付ける標記の既定サイズ（ページ内の正規化値）。 */
const DEFAULT_MARK_WIDTH = 0.18
const DEFAULT_MARK_HEIGHT = 0.04
/** 手書き（pen）の線の太さ（0〜100 の viewBox 基準）。 */
const PEN_THICKNESS = 1.2
/** PDF の読み込みに失敗したときの文言（未登録の 404 は別扱い）。 */
const PDF_RENDER_ERROR = 'PDF の表示に失敗しました。'

/** ページ内の正規化矩形（0〜1・左上原点）。 */
interface MarkRect { x: number; y: number; width: number; height: number }
/** ページ内の正規化した点（手書きの点列）。 */
interface NormPoint { x: number; y: number }
/** 標記レイヤーに描く 1 標記ぶんの形。 */
interface MarkShape { mark: ReadingMark; rects: MarkRect[]; points: NormPoint[] }

const route = useRoute()
const router = useRouter()
const toast = useToast()

const loading = ref(false)
const error = ref('')
const busy = ref(false)

/** `?bookId=` があれば閲覧、無ければ本棚（契約 §4.2）。 */
const requestedBookId = computed(() => {
  const raw = Number(route.query.bookId ?? '')
  return Number.isInteger(raw) && raw > 0 ? raw : 0
})
const viewing = computed(() => requestedBookId.value > 0)

/* ---------- 本棚モードのタブ（自分の本棚 / 図書館 / 読書履歴） ---------- */

/**
 * ・自分の本棚 … 背表紙が並ぶ木の棚（既定。旧【書籍閲覧2】の本棚タブ）
 * ・図書館     … カードで本の情報を見る一覧（旧「本棚」タブ）
 * ・読書履歴   … 全書籍の読書記録
 */
type ReaderTab = 'self' | 'library' | 'history'

/** `?view=` からタブを決める（未指定は自分の本棚。古い `?view=shelf` は図書館として受ける）。 */
function tabFromQuery(view: unknown): ReaderTab {
  if (view === 'history') return 'history'
  if (view === 'library' || view === 'shelf') return 'library'
  return 'self'
}

// タブの状態は URL クエリ ?view= と同期する（資料管理と同じ。リロード・直リンクでも保たれる）。
const activeTab = ref<ReaderTab>(tabFromQuery(route.query.view))

watch(() => route.query.view, (view) => {
  const next = tabFromQuery(view)
  if (next === activeTab.value) return
  activeTab.value = next
  if (next === 'history') {
    void loadHistoryBooks()
    void searchHistory()
  } else {
    // 【自分の本棚】と【図書館】は取りに行く範囲が違う（shelf=MINE / 全体）
    void reloadShelfForTab()
  }
})

function selectTab(tab: ReaderTab): void {
  if (activeTab.value === tab) return
  activeTab.value = tab
  const query = { ...route.query }
  // 既定（自分の本棚）はクエリに残さない
  if (tab === 'self') delete query.view
  else query.view = tab
  void router.replace({ path: route.path, query })
  // タブを開いたときに最新の履歴を読む（絞り込みは入れたままにする）
  if (tab === 'history') {
    void loadHistoryBooks()
    void searchHistory()
  } else {
    // タブごとに違う本を出すので、切り替えたら取り直す
    // （自分の本棚＝shelf=MINE / 図書館＝見える本すべて）
    void reloadShelfForTab()
  }
}

/**
 * タブに合わせて本を取り直す。
 * 【自分の本棚】は `shelf=MINE`（自分の本棚の行だけ）、【図書館】は見える本すべて。
 * 前のタブの本が一瞬見えないように、いったん空にしてから読む。
 */
async function reloadShelfForTab(): Promise<void> {
  shelfPage.value = 1
  shelfBooks.value = []
  shelfTotal.value = 0
  try {
    await loadShelf()
  } catch (caught) {
    toast.danger(messageOf(caught, '本棚を読み込めませんでした。'))
  }
}

/* ---------- 本棚 ---------- */

const categories = ref<ReadingCategory[]>([])
const activeCategory = ref<string>('all')
const shelfBooks = ref<ReadingBook[]>([])
const shelfTotal = ref(0)
const shelfTotals = ref<ReadingShelfTotals | null>(null)
const shelfPage = ref(1)
const shelfHasMore = computed(() => shelfBooks.value.length < shelfTotal.value)
/** 本棚の取り直し中（タブを切り替えたときのちらつきを防ぐ） */
const shelfLoading = ref(false)

/* ---------- 自分の本棚（背表紙の棚） ---------- */

/**
 * 棚の背景（実写の写真）の番号。既定は 0（もとの写真）。
 * 端末ごとの見た目の好みなので localStorage に覚える（サーバには保存しない）。
 */
const shelfBg = ref(readShelfBg())

/** 棚の背景を切り替える（すぐ反映して、次に開いたときのために覚える）。 */
function selectShelfBg(index: number): void {
  const next = normalizeShelfBg(index)
  shelfBg.value = next
  writeShelfBg(next)
}

/** 棚の本体（背表紙）への参照（閲覧から戻ったときに抜き出しの状態を戻す）。 */
const selfShelf = ref<InstanceType<typeof ShelfCaseList> | null>(null)

/** 抜き出した本の表紙を出している間だけ入る（閲覧へ切り替わるまでのつなぎ）。 */
const openingBook = ref<ReadingBook | null>(null)
let openingTimer: number | null = null

/** `prefers-reduced-motion: reduce` か（演出を切るために見る）。 */
const reduceMotion = ref(matchesReduceMotion())
let motionQuery: MediaQueryList | null = null

/** 棚に並んでいく演出を付けるか（動きを減らす設定では付けない）。 */
const dealing = computed(() => !reduceMotion.value)

function matchesReduceMotion(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function onMotionChange(event: MediaQueryListEvent): void {
  reduceMotion.value = event.matches
}

/** 抜き出した本の表紙を少しの間見せる（動きを減らす設定では出さない）。 */
function showOpening(item: ReadingBook): void {
  if (reduceMotion.value) {
    openingBook.value = null
    return
  }
  openingBook.value = item
  if (openingTimer !== null) window.clearTimeout(openingTimer)
  openingTimer = window.setTimeout(() => {
    openingBook.value = null
    openingTimer = null
  }, OPENING_MS)
}

/**
 * 分類ごとの棚（自分の本棚）。「すべて」では分類の表示順に並べ、未分類を最後に置く。
 * 図書館タブと同じ絞り込み（`activeCategory`）を使う。
 */
const selfShelfSections = computed<ShelfSection[]>(() => {
  const books = shelfBooks.value
  if (activeCategory.value !== 'all') {
    const key = activeCategory.value === 'none' ? 'none' : `cat-${activeCategory.value}`
    const name = activeCategory.value === 'none'
      ? '未分類'
      : (categories.value.find((item) => String(item.categoryId) === activeCategory.value)?.name ?? '本棚')
    return [{ key, name, books }]
  }
  const sections: ShelfSection[] = []
  for (const category of categories.value) {
    const items = books.filter((item) => item.categoryId === category.categoryId)
    if (items.length > 0) sections.push({ key: `cat-${category.categoryId}`, name: category.name, books: items })
  }
  const uncategorized = books.filter((item) => item.categoryId === null)
  if (uncategorized.length > 0) sections.push({ key: 'none', name: '未分類', books: uncategorized })
  return sections
})

/** 自分の本棚で本を開く（背表紙を抜き出す演出のあとに呼ばれる）。 */
async function openFromShelf(item: ReadingBook): Promise<void> {
  const query: Record<string, string> = {}
  // 読書履歴タブから開いたときは、戻ったときも同じタブに戻す
  if (activeTab.value === 'history') query.view = 'history'
  await router.push({ path: route.path, query: { ...query, bookId: String(item.bookId) } })
}

/* ---------- 閲覧（この本） ---------- */

const detail = ref<ReadingBookDetail | null>(null)
const book = computed<ReadingBook | null>(() => detail.value?.book ?? null)
const currentPage = ref(1)
const pageInput = ref(1)
/** 今回の読書で最初に開いたページ（読書記録の開始ページの初期値）。 */
const sessionFirstPage = ref(1)
const sessionStartedAt = ref(Date.now())

/** この本の標記（一覧用。全ページ）。 */
const bookMarks = ref<ReadingMark[]>([])
/** 表示中のページの標記（PDF に重ねる）。 */
const pageMarks = computed<ReadingMark[]>(() => detail.value?.marks ?? [])
const markedPages = computed(() => new Set(detail.value?.markedPages ?? []))

/* ---------- PDF（pdf.js） ---------- */

const stageEl = ref<HTMLElement | null>(null)
const pageEl = ref<HTMLElement | null>(null)
const canvasEl = ref<HTMLCanvasElement | null>(null)
const textLayerEl = ref<HTMLElement | null>(null)
/** 標記ツールの入れ物（`position: fixed`。位置は placeTools() が入れる） */
const toolsAnchorEl = ref<HTMLElement | null>(null)

/**
 * pdf.js のオブジェクトは **shallowRef** で持つ。
 * pdf.js のクラスは私的なフィールド（#…）を使うため、`ref` の深い反応性で
 * Proxy に包むと「Cannot read private member #d from an object whose class did not
 * declare it」で落ちる（実際に実機で発生した）。入れ替えは丸ごとなので shallowRef で足りる。
 */
const pdfDoc = shallowRef<PdfDocumentProxy | null>(null)
const pdfNumPages = ref(0)
const pdfLoading = ref(false)
const pdfMissing = ref(false)
const pdfError = ref('')
const pageViewport = shallowRef<PdfViewport | null>(null)
/**
 * 表示倍率の合わせ方。
 * ・height … ページ全体が縦に収まる（初期表示。ブラウザの高さに合わせる）
 * ・width  … 幅いっぱい（縦ははみ出してよい＝スクロールで読む）
 * ・manual … 縮小・拡大で決めた手動倍率
 */
type FitMode = 'height' | 'width' | 'manual'
const fitMode = ref<FitMode>('height')
const zoom = ref(1)
/** 自動で決めた最後の倍率（手動へ切り替えるときの基準）。 */
const lastFitScale = ref(1)
/**
 * カードにマウスを乗せた（またはキーボードで入った）ときに出す「悬浮式」のページ一覧。
 * レイアウトを動かさず、本文の上へ左向きに開く。閉じるのは少し遅らせる（一覧へマウスを移せるように）。
 * カードの中の常駐一覧と【開く】【閉じる】のボタンは 2026-09-15 の指示で廃止した。
 */
const railPreviewOpen = ref(false)
let railPreviewTimer: number | null = null
/** マウスが外れてから閉じるまでの待ち（ms）。 */
const RAIL_PREVIEW_CLOSE_DELAY = 200

/** ナビゲーションの上限（契約 §5 の指示どおり pdf.js の総ページ数を優先）。 */
const pageTotal = computed(() => (pdfNumPages.value > 0 ? pdfNumPages.value : (book.value?.totalPages ?? 0)))
/** ページレールに出すページ（2.0 と同じく本の総ページ数を使う）。 */
const railTotal = computed(() => (book.value?.totalPages ?? 0) > 0 ? (book.value?.totalPages ?? 0) : pdfNumPages.value)

const pageNumbers = computed<number[]>(() => {
  const total = railTotal.value
  if (total <= 0) return []
  if (total <= 300) return Array.from({ length: total }, (_, index) => index + 1)
  const from = Math.max(1, currentPage.value - 60)
  const to = Math.min(total, from + 149)
  return Array.from({ length: to - from + 1 }, (_, index) => from + index)
})

/** 読み込み中・未登録・失敗の案内（日本語）。 */
const pdfStatus = computed(() => {
  if (pdfMissing.value) return `PDF 未登録（${book.value?.pdfOriginalName ?? '元ファイル名不明'}）`
  if (pdfError.value !== '') return pdfError.value
  if (pdfLoading.value) return 'PDF を読み込んでいます...'
  return ''
})

/** この本の現在ページまでを「既読」として、ページ一覧の色を変える。 */
const readUpToPage = computed(() => book.value?.currentPage ?? 0)

/** ページ一覧のページに出す説明（既読・未読と標記の有無）。 */
function pageChipTitle(pageNo: number): string {
  const read = pageNo <= readUpToPage.value ? '既読' : '未読'
  const marks = markedPages.value.has(pageNo) ? '・標記あり' : ''
  return `P.${pageNo}（${read}${marks}）`
}

/** カードに入ったら「悬浮式」のページ一覧を出す（待ちの取り消しも行う）。 */
function openRailPreview(): void {
  cancelRailPreviewClose()
  railPreviewOpen.value = true
}

/** カードから出たら少し待ってから閉じる（一覧へマウスを移して選べるように）。 */
function scheduleRailPreviewClose(): void {
  cancelRailPreviewClose()
  railPreviewTimer = window.setTimeout(() => {
    railPreviewTimer = null
    railPreviewOpen.value = false
  }, RAIL_PREVIEW_CLOSE_DELAY)
}

function cancelRailPreviewClose(): void {
  if (railPreviewTimer === null) return
  window.clearTimeout(railPreviewTimer)
  railPreviewTimer = null
}

/* ---------- 標記 ---------- */

/** 標記ツールのアイコン（src/assets/icons/icons.svg の id）。 */
const TOOL_ICONS: Record<ReadingMarkType, string> = {
  vocabulary: 'bookmark',
  highlight: 'highlighter',
  underline: 'underline',
  memo: 'note',
  pen: 'pen'
}

/** 標記の色の候補（10 色。各ツールの既定色は MARK_TYPE_COLORS 側にある）。 */
const MARK_COLOR_PALETTE = [
  '#f5c518', '#f2d04f', '#7a4de8', '#2f7bd6', '#2f9e6f',
  '#e07b39', '#d64545', '#e83e8c', '#6b7280', '#111827'
]

/** ツールごとにいま選んでいる色（既定は MARK_TYPE_COLORS で、パレットから変えられる）。 */
const toolColors = reactive<Record<ReadingMarkType, string>>({ ...MARK_TYPE_COLORS })
/** 色を選ぶ対象のツール（選択中のツール。未選択のときは直前に選んだツール）。 */
const colorTool = ref<ReadingMarkType>('highlight')
const colorPopoverOpen = ref(false)

/** いま色を選んでいるツールの色（色見本と [data-rd-color-value] に出す）。 */
const currentColor = computed(() => toolColors[colorTool.value])
/** パレット（そのツールの既定色を先頭に、全体で 10 色）。 */
const paletteColors = computed(() => {
  const preset = MARK_TYPE_COLORS[colorTool.value]
  return [preset, ...MARK_COLOR_PALETTE.filter((color) => color !== preset)]
})

/** パレットから色を選ぶ（そのツールの色として覚えて、ポップオーバーを閉じる）。 */
function chooseColor(color: string): void {
  toolColors[colorTool.value] = color
  colorPopoverOpen.value = false
}

const activeTool = ref<ReadingMarkType | null>(null)
const selectedMark = ref<ReadingMark | null>(null)
const penDraft = ref<NormPoint[]>([])
const penActive = ref(false)
/** 本文の選択を処理した時刻（直後の click で二重に付けないため）。 */
let selectionHandledAt = 0

const markDialogOpen = ref(false)
const markDialogError = ref('')
const markDialog = reactive({ markType: 'memo' as ReadingMarkType, targetText: '', content: '' })
/** ダイアログで保存するときに使う矩形（選択範囲またはクリック位置）。 */
let pendingRects: MarkRect[] = []

/** 種別ごとにまとめた標記一覧。 */
const markGroups = computed(() => MARK_TYPE_OPTIONS
  .map((type) => ({ type, label: MARK_TYPE_LABELS[type], items: bookMarks.value.filter((mark) => mark.markType === type) }))
  .filter((group) => group.items.length > 0))

/** 標記レイヤーに描く形（2.0 の renderOverlayMarks と同じ考え方）。 */
const markShapes = computed<MarkShape[]>(() => pageMarks.value.map((mark, index) => {
  const drawing = parseDrawing(mark)
  if (drawing?.kind === 'pen' && Array.isArray(drawing.points) && drawing.points.length > 1) {
    return { mark, rects: [], points: drawing.points.map(toPoint).filter((point): point is NormPoint => point !== null) }
  }
  if (drawing?.kind === 'selection' && Array.isArray(drawing.rects) && drawing.rects.length > 0) {
    const rects = drawing.rects.map(toRect).filter((rect): rect is MarkRect => rect !== null)
    if (rects.length > 0) return { mark, rects, points: [] }
  }
  if (mark.positionX !== null && mark.positionY !== null) {
    return {
      mark,
      rects: [{
        x: mark.positionX,
        y: mark.positionY,
        width: mark.width ?? 0.14,
        height: mark.height ?? 0.04
      }],
      points: []
    }
  }
  // 座標を持たない古い標記は、重ならない位置に小さく置く（2.0 の既定値と同じ考え方）
  return { mark, rects: [{ x: 0.12, y: clamp(0.14 + index * 0.06, 0, 0.9), width: 0.14, height: 0.04 }], points: [] }
}))

const popupStyle = computed<Record<string, string>>(() => {
  const keep: Record<string, string> = {}
  const target = selectedMark.value
  if (!target) return keep
  const rect = markShapes.value.find((shape) => shape.mark.markId === target.markId)?.rects[0]
  if (!rect) return keep
  return { left: percent(clamp(rect.x, 0, 0.6)), top: percent(clamp(rect.y, 0.08, 1)) }
})

/* ---------- 読書記録・読書履歴 ---------- */

const recordDialogOpen = ref(false)
const recordErrors = reactive<Record<string, string>>({})
const recordForm = reactive({ pageStart: 1, pageEnd: 1, minutes: 30, memo: '' })

/**
 * 読書履歴は**本棚の「読書履歴」タブ**（全書籍）と**閲覧画面の「この本の読書履歴」**で
 * 同じ絞り込み・件数・ページングを使う（契約 §4.2。閲覧中は bookId を付ける）。
 * ページ番号は type="number" のため、Vue が数値に変換する（空欄は '' のまま）。
 */
const historyFilters = reactive<{ dateFrom: string; dateTo: string; pageNo: number | '' }>({
  dateFrom: '',
  dateTo: '',
  pageNo: ''
})
/** 書籍の絞り込み（'' は「すべての書籍」）。閲覧モードではこの本に固定するので使わない。 */
const historyBookId = ref<number | ''>('')
/** 書籍の選択肢（書名を出すためだけに使う。読めなければ「すべての書籍」だけ出す）。 */
const historyBooks = ref<ReadingBook[]>([])
let historyBooksLoaded = false
const historyItems = ref<ReadingRecord[]>([])
const historyTotal = ref(0)
const historyPage = ref(1)
const historyPages = ref(1)
const historySize = ref(DEFAULT_HISTORY_PAGE_SIZE)
const historyLoading = ref(false)
const historyPagerItems = computed(() => paginationItems(historyPage.value, historyPages.value))

/* ---------- 小物 ---------- */

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value))
}

function round4(value: number): number {
  return Math.round(value * 10000) / 10000
}

function percent(value: number): string {
  return `${Math.round(value * 1000) / 10}%`
}

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

function dateLabel(value: string | null): string {
  return value ? formatIsoDateTime(value) : '—'
}

function minutesLabel(minutes: number): string {
  if (!Number.isFinite(minutes) || minutes <= 0) return '0 分'
  const hours = Math.floor(minutes / 60)
  return hours > 0 ? `${hours} 時間 ${minutes % 60} 分` : `${minutes} 分`
}

function pageRangeLabel(record: ReadingRecord): string {
  if (record.pageStart === null && record.pageEnd === null) return '—'
  return `P.${record.pageStart ?? '-'} - P.${record.pageEnd ?? '-'}`
}

function initial(title: string): string {
  return title.trim().slice(0, 1) || '本'
}

function markColor(mark: ReadingMark): string {
  return mark.color ?? MARK_TYPE_COLORS[mark.markType]
}

/** `#rrggbb` を半透明にする（2.0 の alphaColor と同じ）。 */
function alphaColor(color: string, alpha: number): string {
  const match = /^#([0-9a-f]{6})$/i.exec(color.trim())
  if (!match) return color
  const value = Number.parseInt(match[1], 16)
  return `rgba(${(value >> 16) & 255}, ${(value >> 8) & 255}, ${value & 255}, ${alpha})`
}

/** 標記レイヤーの 1 矩形ぶんの見た目（種別ごとに 2.0 と同じ塗り・線）。 */
function markRectStyle(mark: ReadingMark, rect: MarkRect): Record<string, string> {
  const color = markColor(mark)
  const style: Record<string, string> = {
    left: percent(rect.x),
    top: percent(rect.y),
    width: percent(rect.width),
    height: percent(rect.height)
  }
  if (mark.markType === 'highlight') {
    style.background = alphaColor(color, 0.42)
  } else if (mark.markType === 'underline') {
    style.background = 'transparent'
    style.borderBottom = `3px solid ${alphaColor(color, 0.92)}`
  } else if (mark.markType === 'memo') {
    style.background = alphaColor(color, 0.22)
    style.borderBottom = `2px dashed ${alphaColor(color, 0.95)}`
  } else {
    style.background = alphaColor(color, 0.18)
    style.borderBottom = `2px solid ${alphaColor(color, 0.88)}`
  }
  return style
}

function markLabel(mark: ReadingMark): string {
  return MARK_TYPE_LABELS[mark.markType] ?? mark.markType
}

function markAriaLabel(mark: ReadingMark): string {
  const text = mark.targetText ?? mark.content ?? ''
  return `${markLabel(mark)}${text === '' ? '' : `（${text}）`} の標記`
}

function parseDrawing(mark: ReadingMark): { kind?: string; rects?: unknown[]; points?: unknown[] } | null {
  if (!mark.drawingData) return null
  try {
    const parsed = JSON.parse(mark.drawingData) as unknown
    return parsed !== null && typeof parsed === 'object'
      ? (parsed as { kind?: string; rects?: unknown[]; points?: unknown[] })
      : null
  } catch {
    return null
  }
}

function toRect(value: unknown): MarkRect | null {
  if (value === null || typeof value !== 'object') return null
  const rect = value as Partial<MarkRect>
  const x = Number(rect.x), y = Number(rect.y), width = Number(rect.width), height = Number(rect.height)
  if (![x, y, width, height].every((item) => Number.isFinite(item))) return null
  if (width <= 0 || height <= 0) return null
  return { x: clamp(x, 0, 1), y: clamp(y, 0, 1), width: clamp(width, 0, 1 - x), height: clamp(height, 0, 1 - y) }
}

function toPoint(value: unknown): NormPoint | null {
  if (value === null || typeof value !== 'object') return null
  const point = value as Partial<NormPoint>
  const x = Number(point.x), y = Number(point.y)
  if (!Number.isFinite(x) || !Number.isFinite(y)) return null
  return { x: clamp(x, 0, 1), y: clamp(y, 0, 1) }
}

/** 手書きの点列を SVG のパスにする（2.0 の buildPenSvg と同じ 0〜100 の viewBox）。 */
function penPath(shape: MarkShape): string {
  return shape.points
    .map((point, index) => `${index === 0 ? 'M' : 'L'}${round4(point.x * 100)} ${round4(point.y * 100)}`)
    .join(' ')
}

/* ---------- 読み込み ---------- */

async function loadCategories(): Promise<void> {
  const response = await listReadingCategories()
  categories.value = response.data.items
}

async function loadShelf(append = false): Promise<void> {
  const params: Parameters<typeof searchReadingBooks>[0] = { page: shelfPage.value, size: SHELF_PAGE_SIZE }
  if (activeCategory.value === 'none') params.categoryId = CATEGORY_NONE
  else if (activeCategory.value !== 'all') params.categoryId = Number(activeCategory.value)
  // 【自分の本棚】タブは自分の本棚の行だけを出す（2026-09-14 の決定: 本棚はアカウントごと）
  if (activeTab.value === 'self') params.shelf = 'MINE'
  shelfLoading.value = true
  try {
    const response = await searchReadingBooks(params)
    shelfBooks.value = append ? [...shelfBooks.value, ...response.data.items] : response.data.items
    shelfTotal.value = response.data.totalElements
    shelfTotals.value = response.data.totals ?? null
  } finally {
    shelfLoading.value = false
  }
}

/**
 * 読書履歴を読む。閲覧モード（?bookId=）はその本、本棚モードは選んだ書籍（未指定は全書籍）。
 * 空欄の絞り込みは送らない（契約 §4.2）。
 */
async function loadHistory(page = historyPage.value): Promise<void> {
  const params: Parameters<typeof searchReadingRecords>[0] = { page, size: historySize.value }
  const bookId = viewing.value ? requestedBookId.value : Number(historyBookId.value)
  if (Number.isInteger(bookId) && bookId > 0) params.bookId = bookId
  const dateFrom = historyFilters.dateFrom.trim()
  if (dateFrom !== '') params.dateFrom = dateFrom
  const dateTo = historyFilters.dateTo.trim()
  if (dateTo !== '') params.dateTo = dateTo
  const pageNo = Number(historyFilters.pageNo)
  if (historyFilters.pageNo !== '' && Number.isFinite(pageNo) && pageNo >= 1) params.pageNo = Math.trunc(pageNo)

  historyLoading.value = true
  try {
    const response = await searchReadingRecords(params)
    historyItems.value = response.data.items
    historyTotal.value = response.data.totalElements
    historyPage.value = response.data.page
    historyPages.value = Math.max(1, response.data.totalPages)
  } finally {
    historyLoading.value = false
  }
}

/** 絞り込みを掛けて 1 ページ目から読み直す（件数を変えたときも同じ）。 */
async function searchHistory(): Promise<void> {
  try {
    await loadHistory(1)
  } catch (caught) {
    toast.danger(messageOf(caught, '読書履歴を読み込めませんでした。'))
  }
}

/** 絞り込みを空に戻して読み直す（件数の選択は変えない）。 */
async function resetHistory(): Promise<void> {
  historyBookId.value = ''
  historyFilters.dateFrom = ''
  historyFilters.dateTo = ''
  historyFilters.pageNo = ''
  await searchHistory()
}

/** ページ番号を指定して読む（ページングから呼ぶ）。 */
async function goHistoryPage(page: number): Promise<void> {
  try {
    await loadHistory(page)
  } catch (caught) {
    toast.danger(messageOf(caught, '読書履歴を読み込めませんでした。'))
  }
}

/**
 * 書籍の絞り込みの選択肢（書名）を 1 回だけ読む。
 * 読めなくても画面は壊さず、選択肢を「すべての書籍」だけにする。
 */
async function loadHistoryBooks(): Promise<void> {
  if (historyBooksLoaded) return
  historyBooksLoaded = true
  try {
    const response = await searchReadingBooks({ page: 1, size: 100 })
    historyBooks.value = response.data.items
  } catch {
    historyBooks.value = []
  }
}

/** この本の標記（一覧・語彙ピックアップ用。全ページ）。 */
async function loadBookMarks(bookId: number): Promise<void> {
  const response = await searchReadingMarks(bookId, { size: MARKS_PAGE_SIZE })
  bookMarks.value = response.data.items
}

async function openShelf(): Promise<void> {
  detail.value = null
  bookMarks.value = []
  resetPdfState()
  activeTool.value = null
  selectedMark.value = null
  shelfPage.value = 1
  // 直リンク（?view=history）で開いたときは、書籍の絞り込みの選択肢も用意する
  if (activeTab.value === 'history') void loadHistoryBooks()
  await loadShelf()
  await loadHistory(1)
}

async function openViewer(bookId: number): Promise<void> {
  const response = await fetchReadingBook(bookId)
  detail.value = response.data
  currentPage.value = response.data.book.currentPage > 0 ? response.data.book.currentPage : 1
  pageInput.value = currentPage.value
  sessionFirstPage.value = currentPage.value
  sessionStartedAt.value = Date.now()
  activeTool.value = null
  selectedMark.value = null
  resetPdfState()
  // 背表紙から開いたときは、切り替わるまでのつなぎに表紙を少しの間見せる
  showOpening(response.data.book)
  // テンプレート（canvas・レイヤー）が出てから PDF を読む
  await nextTick()
  await Promise.all([loadBookMarks(bookId), loadHistory(1)])
  await loadPdf()
}

async function reload(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    if (viewing.value) await openViewer(requestedBookId.value)
    else await openShelf()
  } catch (caught) {
    error.value = messageOf(caught, '書籍情報を取得できませんでした。')
  } finally {
    loading.value = false
  }
}

async function selectCategory(key: string): Promise<void> {
  if (activeCategory.value === key) return
  activeCategory.value = key
  shelfPage.value = 1
  try {
    await loadShelf()
  } catch (caught) {
    toast.danger(messageOf(caught, '本棚を読み込めませんでした。'))
  }
}

async function showMoreBooks(): Promise<void> {
  shelfPage.value += 1
  try {
    await loadShelf(true)
  } catch (caught) {
    toast.danger(messageOf(caught, '本棚を読み込めませんでした。'))
  }
}

function openBook(item: ReadingBook): void {
  // 開いているタブを保ったまま本を開く（戻ったときに同じタブへ戻る）
  const query: Record<string, string> = {}
  if (activeTab.value !== 'self') query.view = activeTab.value
  void router.push({ path: route.path, query: { ...query, bookId: String(item.bookId) } })
}

/** 一覧（自分の本棚／図書館）へ戻る。開いていたタブはそのまま。 */
function backToShelf(): void {
  const query: Record<string, string> = {}
  if (activeTab.value !== 'self') query.view = activeTab.value
  selfShelf.value?.resetPull()
  openingBook.value = null
  void router.push({ path: route.path, query })
}

/**
 * この本の標記をすべて消し、あわせて読書の進捗（現在ページ・ステータス）もリセットする
 * （2.0 の「標記削除」を 2026-09-14 の指示で拡張。確認してから実行する）。
 * 読書履歴（記録）は消さない。
 */
/**
 * この本の標記と読書記録（履歴）をすべて消し、あわせて読書の進捗（現在ページ・ステータス）も
 * リセットする（2.0 の「標記削除」を 2026-09-14 の指示で拡張。確認してから実行する）。
 */
async function clearMarks(item: ReadingBook): Promise<void> {
  if (busy.value) return
  if (!window.confirm(
    `「${item.title}」の標記（${item.markCount} 件）・読書記録・読書の進捗（現在ページ・ステータス）を` +
    'すべてリセットします。よろしいですか？'
  )) return
  busy.value = true
  try {
    const response = await deleteAllReadingMarks(item.bookId)
    toast.success(response.data.message)
    await loadShelf()
  } catch (caught) {
    toast.danger(messageOf(caught, '標記・読書記録と読書の進捗をリセットできませんでした。'))
  } finally {
    busy.value = false
  }
}

/**
 * 【本棚に入れる】/【本棚から外す】（2026-09-14 の決定で追加。本棚はアカウントごと）。
 *
 * <p>サーバは**自分の行だけ**を作る／消すので、押した本人の本棚が変わる（冪等）。
 * 返ってきた 1 冊の `inMyShelf` でボタンを入れ替える（入れ直さずに済む）。</p>
 */
async function toggleShelf(item: ReadingBook, add: boolean): Promise<void> {
  if (busy.value) return
  busy.value = true
  try {
    const response = add
      ? await addReadingBookToShelf(item.bookId)
      : await removeReadingBookFromShelf(item.bookId)
    const updated = response.data.book
    const index = shelfBooks.value.findIndex((book) => book.bookId === updated.bookId)
    if (index >= 0) shelfBooks.value[index] = updated
    // サマリの「自分の本棚 N 冊」も合わせる（増減があったときだけ）
    if (shelfTotals.value && item.inMyShelf !== updated.inMyShelf) {
      shelfTotals.value = {
        ...shelfTotals.value,
        myShelfCount: Math.max(0, shelfTotals.value.myShelfCount + (updated.inMyShelf ? 1 : -1))
      }
    }
    toast.success(response.data.message)
  } catch (caught) {
    toast.danger(messageOf(caught, add ? '本棚に入れられませんでした。' : '本棚から外せませんでした。'))
  } finally {
    busy.value = false
  }
}

/* ---------- PDF の読み込みと描画（2.0 の ensurePdfLoaded / renderPdfPage を移植） ---------- */

function resetPdfState(): void {
  const doc = pdfDoc.value
  pdfDoc.value = null
  pdfNumPages.value = 0
  pdfLoading.value = false
  pdfMissing.value = false
  pdfError.value = ''
  pageViewport.value = null
  penDraft.value = []
  penActive.value = false
  // 本文を捨てるので読み上げも止める（別の本を開いたとき・画面を離れるときに喋り続けないように）
  stopTts()
  ttsPageText.value = ''
  ttsPageNo.value = 0
  if (doc?.destroy) {
    try {
      void doc.destroy()
    } catch {
      // 破棄できなくても表示は続けられる
    }
  }
}

async function loadPdf(): Promise<void> {
  resetPdfState()
  const target = book.value
  if (!target) return
  // 契約 §5: 実体が無い（未移行・アップロード前）本は PDF を取りに行かずに案内する
  if (!target.hasPdf || !target.pdfAvailable) {
    pdfMissing.value = true
    return
  }
  pdfLoading.value = true
  try {
    const lib = await ensurePdfJs()
    lib.GlobalWorkerOptions.workerSrc = PDFJS_WORKER_URL
    const url = readingPdfUrl(target.bookId, target.version)
    const doc = await lib.getDocument({ url, withCredentials: true }).promise
    pdfDoc.value = doc
    pdfNumPages.value = Number(doc.numPages) || 0
    await renderCurrent()
  } catch (caught) {
    if (isMissingPdfError(caught)) pdfMissing.value = true
    else pdfError.value = PDF_RENDER_ERROR
  } finally {
    pdfLoading.value = false
  }
}

function canvasContext(canvas: HTMLCanvasElement): CanvasRenderingContext2D | null {
  try {
    return canvas.getContext('2d')
  } catch {
    return null
  }
}

/** 本文を表示できる高さ（本文の枠の上端を実測し、ブラウザの高さの残りから求める）。 */
function stageHeight(): number {
  const stage = stageEl.value
  // 上端は「スクロールしていないときの位置」で見る（下へスクロール中でも倍率が暴れないように）
  const top = stage ? Math.max(0, stage.getBoundingClientRect().top + window.scrollY) : 0
  return Math.max(MIN_STAGE_HEIGHT, window.innerHeight - top - STAGE_BOTTOM_GAP)
}

/** 本文を表示できる幅（本文の枠の内側の幅。左右の余白は除く）。 */
function stageWidth(): number {
  const stage = stageEl.value
  if (!stage) return 0
  const style = window.getComputedStyle(stage)
  const padding = (Number.parseFloat(style.paddingLeft) || 0) + (Number.parseFloat(style.paddingRight) || 0)
  return Math.max(0, stage.clientWidth - padding)
}

/** いまの合わせ方（height / width / manual）での倍率。 */
function scaleForFit(base: { width: number; height: number }): number {
  if (fitMode.value === 'manual') return clamp(zoom.value, MIN_SCALE, MAX_SCALE)
  if (fitMode.value === 'width') {
    const width = stageWidth()
    return clamp(width > 0 ? width / base.width : 1, MIN_SCALE, MAX_SCALE)
  }
  // height: ページ全体が縦に収まる倍率（初期表示）
  return clamp(stageHeight() / base.height, MIN_SCALE, MAX_SCALE)
}

async function renderPdfPage(): Promise<void> {
  const doc = pdfDoc.value
  if (!doc) return
  // 描き直すとテキスト層も作り直されるので、読み上げは先に止める（設計 §5.3）
  stopTts()
  const page = await doc.getPage(clampPage(currentPage.value))
  const base = page.getViewport({ scale: 1 })
  const scale = scaleForFit(base)
  if (fitMode.value !== 'manual') lastFitScale.value = scale
  const viewport = page.getViewport({ scale })
  pageViewport.value = viewport

  const canvas = canvasEl.value
  const outputScale = window.devicePixelRatio || 1
  const context = canvas ? canvasContext(canvas) : null
  if (canvas) {
    canvas.width = Math.floor(viewport.width * outputScale)
    canvas.height = Math.floor(viewport.height * outputScale)
    canvas.style.width = `${Math.ceil(viewport.width)}px`
    canvas.style.height = `${Math.ceil(viewport.height)}px`
    if (context) {
      context.setTransform(1, 0, 0, 1, 0, 0)
      context.clearRect(0, 0, canvas.width, canvas.height)
    }
    await page.render({
      canvasContext: context as CanvasRenderingContext2D,
      viewport,
      transform: outputScale === 1 ? null : [outputScale, 0, 0, outputScale, 0, 0]
    }).promise
  }

  // 標記の対象文字を選べるようにテキストレイヤーを作る
  const layer = textLayerEl.value
  if (layer) {
    layer.innerHTML = ''
    layer.style.width = `${Math.ceil(viewport.width)}px`
    layer.style.height = `${Math.ceil(viewport.height)}px`
    layer.style.setProperty('--scale-factor', String(viewport.scale))
    const content = await page.getTextContent()
    // 読み上げ（TTS）は本文の文字を使うので、取り出した内容をページ番号と一緒に退避する
    // （2 回目の getTextContent() を避ける。テキスト層は常に表示中の 1 ページ分だけ）
    ttsPageText.value = textOfItems(content.items)
    ttsPageNo.value = clampPage(currentPage.value)
    const task = window.pdfjsLib?.renderTextLayer({
      textContentSource: content,
      container: layer,
      viewport,
      textDivs: []
    })
    if (task?.promise) await task.promise
  }

  // ページを描き直すと本文の大きさが変わるので、標記ツールを貼り付け直す
  scheduleToolsPlace()
}

async function renderCurrent(): Promise<void> {
  try {
    await renderPdfPage()
  } catch {
    pdfError.value = PDF_RENDER_ERROR
  }
}

function clampPage(page: number): number {
  const total = pageTotal.value
  const rounded = Math.round(Number(page))
  if (!Number.isFinite(rounded)) return 1
  return clamp(rounded, 1, total > 0 ? total : 1)
}

/** 表示中のページを変える（標記はそのページのものを取り直す）。 */
async function goToPage(page: number): Promise<void> {
  const target = book.value
  if (!target) return
  const next = clampPage(page)
  pageInput.value = next
  if (next === currentPage.value) return
  currentPage.value = next
  selectedMark.value = null
  await renderCurrent()
  try {
    const response = await fetchReadingBook(target.bookId, { markPage: next })
    detail.value = response.data
  } catch (caught) {
    toast.danger(messageOf(caught, '標記を読み込めませんでした。'))
  }
}

function onPageInput(): void {
  void goToPage(pageInput.value)
}

/* ---------- 拡大・縮小と合わせ方 ---------- */

function zoomIn(): void {
  const base = fitMode.value === 'manual' ? zoom.value : lastFitScale.value
  zoom.value = clamp(base * ZOOM_STEP, MIN_SCALE, MAX_SCALE)
  fitMode.value = 'manual'
  void renderCurrent()
}

function zoomOut(): void {
  const base = fitMode.value === 'manual' ? zoom.value : lastFitScale.value
  zoom.value = clamp(base / ZOOM_STEP, MIN_SCALE, MAX_SCALE)
  fitMode.value = 'manual'
  void renderCurrent()
}

/**
 * ズームは 1 つのトグルボタン。「次に押すとどうなるか」を出し、
 * 押すと高さ合わせ ⇄ 幅合わせを切り替える（手動倍率のときは高さ合わせに戻す）。
 */
function toggleFit(): void {
  fitMode.value = fitMode.value === 'height' ? 'width' : 'height'
  void renderCurrent()
}

/** トグルボタンの表示（高さ基準なら「幅に合わせる」、それ以外は「高さに合わせる」）。 */
const fitToggleLabel = computed(() => (fitMode.value === 'height' ? '幅に合わせる' : '高さに合わせる'))
const fitToggleIcon = computed(() => (fitMode.value === 'height' ? 'eye' : 'monitor'))
const fitToggleTitle = computed(() => (fitMode.value === 'height'
  ? '幅いっぱいに広げる（縦はスクロール）'
  : 'ページ全体を縦に収める'))

/** ブラウザの大きさが変わったら測り直す（自動の合わせ方のときだけ）。 */
function onWindowResize(): void {
  scheduleToolsPlace()
  if (fitMode.value === 'manual' || !pdfDoc.value) return
  void renderCurrent()
}

/* ---------- 標記ツールを常に見える位置へ貼り付ける ---------- */

/** 本文の枠とウィンドウの端から取る余白（px） */
const TOOLS_MARGIN = 8

let toolsCancel: (() => void) | null = null

/**
 * 画面の上に貼り付いている帯（sticky / fixed なアプリのヘッダ。例: `.topbar` の日付・ユーザーの行）の下端。
 *
 * <p>標記ツールは固定配置なので、この帯の**下**に置かないと帯に隠れて見えなくなる
 * （2026-09-15 の不具合報告。帯は不透明で z-index も高い）。</p>
 */
function fixedHeaderBottom(): number {
  let bottom = 0
  for (const element of Array.from(document.querySelectorAll<HTMLElement>('.topbar'))) {
    const style = window.getComputedStyle(element)
    if (style.position !== 'sticky' && style.position !== 'fixed') continue
    const box = element.getBoundingClientRect()
    // いま画面の上に貼り付いている帯だけを見る（上へスクロールし切っていないもの）
    if (box.top <= TOOLS_MARGIN && box.bottom > bottom) bottom = box.bottom
  }
  return Math.max(0, Math.round(bottom))
}

/**
 * 標記ツール（`.rrd-tools-anchor` は `position: fixed`）を、**見えている範囲の左上**へ置く。
 *
 * <p>2026-09-15 の指示（利用者報告「スクロールするとツールが見えなくなる」）:</p>
 * <ul>
 *   <li>縦は**視口**を基準にする。スクロールしても上端は「ヘッダの下端 + 8px」より上へは行かない
 *       （本文の枠の上端が画面の外へ出ても、ツールは見える位置に残る）</li>
 *   <li>本文の枠がまだ下にあるときは、その枠の左上（枠の上端 + 8px）に置く＝読む場所から出ない</li>
 *   <li>横は今までどおり本文の列の中（左のナビや他のカードを避ける）</li>
 *   <li>**本文の枠が画面から完全に出たときだけ**隠す</li>
 * </ul>
 */
function placeTools(): void {
  const anchor = toolsAnchorEl.value
  const stage = stageEl.value
  if (!anchor || !stage) return
  const stageBox = stage.getBoundingClientRect()
  const toolsBox = anchor.getBoundingClientRect()
  // ウィンドウの高さが取れない環境（テスト）でも落ちないようにする
  const viewportHeight = window.innerHeight || stageBox.bottom + toolsBox.height + TOOLS_MARGIN * 2
  // ヘッダの下が「視口の左上」。枠がまだ下にあるときは枠の上端に合わせる（＝読む場所の左上）
  const minTop = fixedHeaderBottom() + TOOLS_MARGIN
  const maxTop = Math.max(minTop, viewportHeight - toolsBox.height - TOOLS_MARGIN)
  const top = clamp(Math.max(minTop, stageBox.top + TOOLS_MARGIN), minTop, maxTop)

  const minLeft = Math.max(0, stageBox.left + TOOLS_MARGIN)
  const maxLeft = Math.max(minLeft, Math.min(
    stageBox.right - toolsBox.width - TOOLS_MARGIN,
    (window.innerWidth || stageBox.right) - toolsBox.width - TOOLS_MARGIN
  ))

  anchor.style.top = `${Math.round(top)}px`
  anchor.style.left = `${Math.round(clamp(minLeft, minLeft, maxLeft))}px`
  // 本文が画面から完全に出たときだけ隠す（読んでいる間はいつでも使える）
  const stageVisible = stageBox.bottom > minTop && stageBox.top < viewportHeight
  anchor.style.visibility = stageVisible ? 'visible' : 'hidden'
}

/** 1 フレームに 1 回だけ置き直す（スクロール・大きさの変化で何度も呼ばれるため）。 */
function scheduleToolsPlace(): void {
  if (toolsCancel !== null) return
  const run = (): void => {
    toolsCancel = null
    placeTools()
  }
  if (typeof window.requestAnimationFrame === 'function') {
    const id = window.requestAnimationFrame(run)
    toolsCancel = () => window.cancelAnimationFrame(id)
  } else {
    const id = window.setTimeout(run, 16)
    toolsCancel = () => window.clearTimeout(id)
  }
}

/* ---------- 選択範囲 → 標記（2.0 の extractSelectionRectangles を移植） ---------- */

function normalizeText(value: string | null | undefined): string {
  return String(value ?? '').replace(/\s+/g, ' ').trim()
}

function normalizeRect(rect: { left: number; top: number; width: number; height: number }, box: DOMRect): MarkRect {
  const left = clamp((rect.left - box.left) / box.width, 0, 1)
  const top = clamp((rect.top - box.top) / box.height, 0, 1)
  const width = clamp(rect.width / box.width, 0, 1 - left)
  const height = clamp(rect.height / box.height, 0, 1 - top)
  return { x: round4(left), y: round4(top), width: round4(width), height: round4(height) }
}

/** 同じ行の矩形をつなげる（2.0 の mergeSelectionRects と同じ）。 */
function mergeRects(rects: MarkRect[]): MarkRect[] {
  const sorted = rects
    .filter((rect) => rect.width > 0.002 && rect.height > 0.002)
    .sort((a, b) => (Math.abs(a.y - b.y) > 0.004 ? a.y - b.y : a.x - b.x))
  const merged: MarkRect[] = []
  for (const rect of sorted) {
    const last = merged[merged.length - 1]
    if (last) {
      const sameLine = Math.abs(last.y - rect.y) <= Math.max(last.height, rect.height) * 0.45
      const gap = rect.x - (last.x + last.width)
      if (sameLine && gap <= 0.008) {
        const right = Math.max(last.x + last.width, rect.x + rect.width)
        last.y = Math.min(last.y, rect.y)
        last.height = Math.max(last.height, rect.height)
        last.width = round4(clamp(right - last.x, 0, 1 - last.x))
        continue
      }
    }
    merged.push({ ...rect })
  }
  return merged
}

function boundingBox(rects: MarkRect[]): MarkRect {
  const x = Math.min(...rects.map((rect) => rect.x))
  const y = Math.min(...rects.map((rect) => rect.y))
  const right = Math.max(...rects.map((rect) => rect.x + rect.width))
  const bottom = Math.max(...rects.map((rect) => rect.y + rect.height))
  return { x: round4(x), y: round4(y), width: round4(right - x), height: round4(bottom - y) }
}

function pointsBox(points: NormPoint[]): MarkRect {
  const x = Math.min(...points.map((point) => point.x))
  const y = Math.min(...points.map((point) => point.y))
  const right = Math.max(...points.map((point) => point.x))
  const bottom = Math.max(...points.map((point) => point.y))
  return { x: round4(x), y: round4(y), width: round4(Math.max(right - x, 0.01)), height: round4(Math.max(bottom - y, 0.01)) }
}

/** 本文（テキストレイヤー）の選択範囲をページ内の正規化矩形にする。 */
function extractSelection(): { text: string; rects: MarkRect[] } | null {
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0 || selection.isCollapsed) return null
  const range = selection.getRangeAt(0)
  const layer = textLayerEl.value
  if (!layer || !layer.contains(range.commonAncestorContainer)) return null
  const page = pageEl.value
  if (!page) return null
  const box = page.getBoundingClientRect()
  if (box.width <= 0 || box.height <= 0) return null

  const rects: MarkRect[] = []
  const clientRects = typeof range.getClientRects === 'function' ? Array.from(range.getClientRects()) : []
  for (const item of clientRects) {
    if (item.width <= 0.5 || item.height <= 0.5) continue
    rects.push(normalizeRect(item, box))
  }
  if (rects.length === 0 && typeof range.getBoundingClientRect === 'function') {
    const bounds = range.getBoundingClientRect()
    if (bounds.width > 0.5 && bounds.height > 0.5) rects.push(normalizeRect(bounds, box))
  }
  const merged = mergeRects(rects)
  if (merged.length === 0) return null
  return { text: normalizeText(selection.toString() || range.toString()), rects: merged }
}

function clearSelection(): void {
  window.getSelection()?.removeAllRanges()
}

/* ---------- 音声読み上げ（TTS）。phase 1 はブラウザ内蔵の音声合成だけ（設計 §4） ---------- */

/**
 * 読み上げの実装。phase 1 はブラウザ内蔵（Web Speech API）だけを使い、
 * サーバー TTS は phase 3 でこの受け口（`ReadingTtsProvider`）に足す。
 */
const ttsProvider = createReadingTtsProvider()

/** 表示中のページの本文（`renderPdfPage()` の `getTextContent()` を退避したもの。文分割に使う）。 */
const ttsPageText = ref('')
/** 退避した本文のページ番号（ページ送りと取り違えないための目印）。 */
const ttsPageNo = ref(0)
/** いま使える音声（端末に 1 つも無ければ空）。 */
const ttsVoices = ref<TtsVoice[]>([])
/** 読み上げの状態（`idle` / `playing`）。 */
const ttsState = ref<'idle' | 'playing'>('idle')
/** 読み上げる塊（文ごと。長い文は読点で分割）といま読んでいる位置。 */
const ttsSegments = ref<TtsSegment[]>([])
const ttsIndex = ref(-1)
/** 読み上げ中の文のハイライト（ページ内の正規化矩形。**DB には書かない**）。 */
const ttsHighlight = ref<MarkRect[]>([])
/** 「はやさ／こえ」のポップオーバーを開いているか。 */
const ttsPanelOpen = ref(false)
/** 端末に覚える設定（速さ・声。localStorage だけ。サーバーにも設定画面にも保存しない）。 */
const ttsPrefs = reactive<TtsPrefs>(readTtsPrefs())
/** 読み上げの世代。停止・ページ送りで古い `onend` を捨てるために使う。 */
let ttsRun = 0
/** 音声一覧の変化（`voiceschanged`）の購読をやめる関数。 */
let ttsVoicesOff: (() => void) | null = null

/** 本の言語（既定の言語。文ごとの判定は `buildTtsSegments()` が行う）。 */
const ttsBookLang = computed<TtsLang>(() => bookLangToTtsLang(book.value?.language))
/** 読み上げ中か。 */
const ttsPlaying = computed(() => ttsState.value === 'playing')
/**
 * 主ボタンの表示。**アイコンだけ**のボタンにして、標記ツールと同じ浮いているツールバーに置く
 * （2026-09-15 の指示。読み上げ中は小喇叭 → 停止のアイコンに変わって状態が分かる）。
 */
const ttsIcon = computed(() => (ttsPlaying.value ? 'stop' : 'speaker'))
const ttsButtonTitle = computed(() => (ttsPlaying.value
  ? '読み上げを停止'
  : '本文を読み上げる（選んでいればその文から、選んでいなければこのページの先頭から）'))

/**
 * 読み上げが使えない理由（使えるときは空）。日本語で出す（設計 §5.3・§5.4）。
 * ・音声合成の API が無い（Firefox for Android など）
 * ・端末に音声が 1 つも無い（音声パック未導入の Windows・無頭の Linux など）
 * ・テキスト層が空（スキャンした画像の PDF）
 */
const ttsBlockedReason = computed(() => {
  if (!ttsProvider.isSupported()) return 'このブラウザは音声読み上げ（Web Speech API）に対応していません。'
  // PDF が出ていないときは、既存の .rrd-help（未登録・表示失敗）が理由を出している
  if (pdfMissing.value || pdfError.value !== '' || pdfLoading.value || pdfDoc.value === null) return ''
  if (ttsVoices.value.length === 0) return 'この端末に音声がありません。読み上げはできません。'
  if (ttsPageText.value.trim() === '') {
    return 'この PDF は文字を取り出せません（画像の PDF）。読み上げはできません。'
  }
  return ''
})

/** いまのページに出てくる言語（声の選択を出す対象）。 */
const ttsTargetLangs = computed<TtsLang[]>(() => segmentLangs(buildTtsSegments(ttsPageText.value, ttsBookLang.value)))

/**
 * 読み上げを始められるか。
 * 音声があり、**いま表示しているページ**の文字が取れているときだけ使える
 * （PDF が未登録・読み込み中は、理由は既存の案内（`pdfStatus` / `.rrd-help`）が出すので黙って無効にする）。
 */
const ttsReady = computed(() => ttsBlockedReason.value === ''
  && ttsPageText.value.trim() !== ''
  && ttsPageNo.value === currentPage.value)

/** その言語で選べる声（無ければ空）。 */
function ttsVoiceOptions(lang: TtsLang): TtsVoice[] {
  return voicesForLang(ttsVoices.value, TTS_LANG_CODES[lang])
}

/** 声を覚えるときの印（`voiceURI` を優先。無い実装は名前で覚える）。 */
function voiceKey(voice: TtsVoice): string {
  return voice.voiceURI ?? voice.name
}

/** ハイライトの矩形の位置（ページ枠に対する %）。 */
function ttsHighlightStyle(rect: MarkRect): Record<string, string> {
  return { left: percent(rect.x), top: percent(rect.y), width: percent(rect.width), height: percent(rect.height) }
}

/** 端末に覚えている声（無ければ言語に合う声を自動で選ぶ）。 */
function voiceForSegment(segment: TtsSegment): TtsVoice | null {
  const saved = findVoice(ttsVoices.value, ttsPrefs.voices[segment.lang])
  return saved ?? resolveVoice(ttsVoices.value, segment.langCode)
}

/**
 * 読み上げ中の文の矩形を作る。
 * 本文の選択と同じ `normalizeRect()` / `mergeRects()` を使う（設計 §5.6）。
 */
function highlightRectsOf(text: string): MarkRect[] {
  const layer = textLayerEl.value
  const page = pageEl.value
  if (!layer || !page) return []
  const box = page.getBoundingClientRect()
  if (box.width <= 0 || box.height <= 0) return []
  const range = textRangeOf(layer, text)
  if (range === null) return []
  const rects: MarkRect[] = []
  const clientRects = typeof range.getClientRects === 'function' ? Array.from(range.getClientRects()) : []
  for (const item of clientRects) {
    if (item.width <= 0.5 || item.height <= 0.5) continue
    rects.push(normalizeRect(item, box))
  }
  if (rects.length === 0 && typeof range.getBoundingClientRect === 'function') {
    const bounds = range.getBoundingClientRect()
    if (bounds.width > 0.5 && bounds.height > 0.5) rects.push(normalizeRect(bounds, box))
  }
  return mergeRects(rects)
}

/** いま読んでいる文をハイライトする（見つからなければハイライト無しで読み上げだけ続ける）。 */
function updateTtsHighlight(text: string): void {
  ttsHighlight.value = highlightRectsOf(text)
}

/**
 * 1 つずつ順に読み上げる。
 * 複数の utterance を一度に入れると始まらない不具合があるので、**前の `onend` で次を話す**（設計 §3.1）。
 */
function speakTtsSegment(run: number): void {
  if (run !== ttsRun || ttsState.value !== 'playing') return
  const segment = ttsSegments.value[ttsIndex.value]
  if (!segment) {
    stopTts()
    return
  }
  updateTtsHighlight(segment.text)
  ttsProvider.speak({
    text: segment.text,
    lang: segment.langCode,
    voice: voiceForSegment(segment),
    rate: normalizeTtsRate(ttsPrefs.rate),
    onend: () => {
      if (run !== ttsRun) return
      ttsIndex.value += 1
      speakTtsSegment(run)
    },
    onerror: () => {
      if (run === ttsRun) stopTts()
    }
  })
}

/**
 * 読み始める文の番号。
 *
 * <p>本文（テキスト層）の中で**文字を選んでいれば、その選択の起点を含む文**から読み始める。
 * 選択が無い・空・本文の外（ツールバーやポップオーバーの文字など）のときは先頭から読む。</p>
 *
 * <p>**標記ツールを選んでいるときは選択を使わない**（`selectTool()` で標記を付けようとしている最中で、
 * 選択は標記が消費して消えるため。この場合は先頭から読む）。</p>
 */
function ttsStartIndex(segments: readonly TtsSegment[]): number {
  if (activeTool.value !== null) return 0
  const layer = textLayerEl.value
  if (!layer) return 0
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0 || selection.isCollapsed) return 0
  const range = selection.getRangeAt(0)
  if (!layer.contains(range.startContainer)) return 0
  const offset = textOffsetOf(layer, range.startContainer, range.startOffset)
  if (offset === null) return 0
  return segmentIndexAt(layer, segments, offset)
}

/** このページを読み上げる（本文を選んでいればその文から、選んでいなければ先頭から）。 */
function startTts(): void {
  if (!ttsProvider.isSupported()) return
  const segments = buildTtsSegments(ttsPageText.value, ttsBookLang.value)
  if (segments.length === 0) return
  ttsSegments.value = segments
  ttsIndex.value = ttsStartIndex(segments)
  ttsRun += 1
  ttsState.value = 'playing'
  speakTtsSegment(ttsRun)
}

/** 読み上げを止める（待っている分も捨てる）。 */
function stopTts(): void {
  ttsRun += 1
  ttsState.value = 'idle'
  ttsIndex.value = -1
  ttsSegments.value = []
  ttsHighlight.value = []
  if (ttsProvider.isSupported()) ttsProvider.stop()
}

/** 主ボタンの操作（停止中は読み始め、読み上げ中は止める）。 */
function toggleTts(): void {
  if (ttsPlaying.value) stopTts()
  else startTts()
}

/** 端末の声を取り直す（最初の `getVoices()` は空のことがあるので `voiceschanged` でも呼ぶ）。 */
function refreshTtsVoices(): void {
  ttsVoices.value = ttsProvider.listVoices()
}

/** 速さを選ぶ（端末に覚える。サーバーには保存しない）。 */
function selectTtsRate(event: Event): void {
  ttsPrefs.rate = normalizeTtsRate((event.target as HTMLSelectElement).value)
  writeTtsPrefs({ rate: ttsPrefs.rate, voices: { ...ttsPrefs.voices } })
}

/** 声を選ぶ（空は「自動で選ぶ」に戻す）。 */
function selectTtsVoice(lang: TtsLang, event: Event): void {
  const value = (event.target as HTMLSelectElement).value
  const voices = { ...ttsPrefs.voices }
  if (value === '') delete voices[lang]
  else voices[lang] = value
  ttsPrefs.voices = voices
  writeTtsPrefs({ rate: ttsPrefs.rate, voices })
}

/** ページ内の正規化した点（クリック位置・手書きの点）。 */
function pointOf(event: { clientX: number; clientY: number }): NormPoint | null {
  const page = pageEl.value
  if (!page) return null
  const box = page.getBoundingClientRect()
  if (box.width <= 0 || box.height <= 0) return null
  return {
    x: clamp((event.clientX - box.left) / box.width, 0, 1),
    y: clamp((event.clientY - box.top) / box.height, 0, 1)
  }
}

/* ---------- 標記の登録・削除 ---------- */

function pushMark(mark: ReadingMark): void {
  const current = detail.value
  if (!current) return
  if (current.marks.some((item) => item.markId === mark.markId)) return
  detail.value = { ...current, marks: [...current.marks, mark] }
  bookMarks.value = [...bookMarks.value.filter((item) => item.markId !== mark.markId), mark]
}

async function createMark(input: {
  markType: ReadingMarkType
  targetText?: string
  content?: string
  rects?: MarkRect[]
  points?: NormPoint[]
}): Promise<void> {
  const target = book.value
  if (!target || busy.value) return
  const body: ReadingMarkSave = {
    pageNo: currentPage.value,
    markType: input.markType,
    // 色はツールごとに選んだ色（既定は MARK_TYPE_COLORS。パレットで変えられる）
    color: toolColors[input.markType]
  }
  if (input.markType === 'pen') {
    const points = input.points ?? []
    if (points.length < 2) return
    const box = pointsBox(points)
    body.positionX = box.x
    body.positionY = box.y
    body.width = box.width
    body.height = box.height
    body.drawingData = JSON.stringify({ kind: 'pen', points, thickness: PEN_THICKNESS, color: body.color })
  } else {
    const rects = input.rects ?? []
    if (rects.length === 0) return
    const box = boundingBox(rects)
    body.positionX = box.x
    body.positionY = box.y
    body.width = box.width
    body.height = box.height
    // 2.0 と同じ形（選択範囲を矩形に分けて保存）。移行済みの標記も同じ形。
    body.drawingData = JSON.stringify({ kind: 'selection', rects, note: input.content ?? '' })
  }
  if (input.targetText) body.targetText = input.targetText
  if (input.content) body.content = input.content

  busy.value = true
  try {
    const response = await createReadingMark(target.bookId, body)
    toast.success(response.data.message)
    pushMark(response.data.mark)
    await loadBookMarks(target.bookId)
  } catch (caught) {
    toast.danger(messageOf(caught, '標記を追加できませんでした。'))
  } finally {
    busy.value = false
  }
}

/** ツールに応じて標記を作る（語彙・メモは内容を入れるダイアログを開く）。 */
function applyTool(tool: ReadingMarkType, source: { text: string; rects: MarkRect[] }): void {
  if (tool === 'vocabulary' || tool === 'memo') {
    openMarkDialog(tool, source)
    return
  }
  void createMark({ markType: tool, targetText: source.text, rects: source.rects })
}

function openMarkDialog(tool: ReadingMarkType, source: { text: string; rects: MarkRect[] }): void {
  pendingRects = source.rects
  markDialog.markType = tool
  markDialog.targetText = source.text
  markDialog.content = ''
  markDialogError.value = ''
  markDialogOpen.value = true
  // 語彙（中国語の本では「読み方」）は、選んだ語の意味を自動で引く
  if (tool === 'vocabulary') void lookupWord(source.text)
}

/* ---------- 言葉の引き当て（語彙・読み方。旧【書籍閲覧2】から移した） ---------- */

/** 本の言語でツールが変わる（中国語の本は「語彙」ではなく「読み方」）。 */
const vocabularyLabel = computed(() => (book.value?.language === '中国語' ? '読み方' : '語彙'))

/** いま使えるツール。日本語の本は語彙の引き当てを使わないので語彙を出さない。 */
const toolOptions = computed<ReadingMarkType[]>(() => (book.value?.language === '日本語'
  ? MARK_TYPE_OPTIONS.filter((tool) => tool !== 'vocabulary')
  : MARK_TYPE_OPTIONS))

function toolLabel(tool: ReadingMarkType): string {
  return tool === 'vocabulary' ? vocabularyLabel.value : MARK_TYPE_LABELS[tool]
}

const lookupResult = ref<ReadingLookupResult | null>(null)
const lookupLoading = ref(false)
const lookupMessage = ref('')

/** 引き当てで何か取れたか（取れなかったときは message だけ出す）。 */
const lookupHasMeaning = computed(() => {
  const result = lookupResult.value
  if (result === null) return false
  return [result.japanese, result.chinese, result.pinyin, result.explanation]
    .some((value) => typeof value === 'string' && value.trim() !== '')
})

/** 引き当てのようす（取得元・キャッシュ・取れなかった理由）。 */
const lookupStatus = computed(() => {
  if (lookupLoading.value) return '意味を調べています...'
  if (lookupResult.value === null) return ''
  if (!lookupHasMeaning.value) return ''
  return `取得元: ${lookupResult.value.source ?? '辞書'}${lookupResult.value.cached ? '（キャッシュ）' : ''}`
})

/** 引き当てた意味を内容の欄に入れる 1 つの文にする。 */
function composeLookupContent(result: ReadingLookupResult): string {
  const parts: string[] = []
  if (result.japanese !== null && result.japanese !== undefined && result.japanese.trim() !== '') {
    parts.push(result.japanese.trim())
  }
  if (result.chinese !== null && result.chinese !== undefined && result.chinese.trim() !== '') {
    parts.push(`中国語: ${result.chinese.trim()}`)
  }
  if (result.pinyin !== null && result.pinyin !== undefined && result.pinyin.trim() !== '') {
    parts.push(`ピンイン: ${result.pinyin.trim()}`)
  }
  if (result.explanation !== null && result.explanation !== undefined && result.explanation.trim() !== '') {
    parts.push(result.explanation.trim())
  }
  return parts.join(' / ')
}

/** 語の意味を引く（日本語の本では引かない）。取れなくても 200 で返るので例外にしない。 */
async function lookupWord(text: string): Promise<void> {
  const language = book.value?.language
  lookupResult.value = null
  lookupMessage.value = ''
  const word = text.trim()
  if (language === undefined || language === '日本語' || word === '') return
  lookupLoading.value = true
  try {
    const response = await lookupReadingWord(word.slice(0, 100), language)
    lookupResult.value = response.data
    lookupMessage.value = response.data.message ?? ''
  } catch (caught) {
    lookupMessage.value = messageOf(caught, '意味を取得できませんでした。')
  } finally {
    lookupLoading.value = false
  }
}

/** 引き当てた意味を内容の欄に入れる（編集できるようにする）。 */
function applyLookup(): void {
  const result = lookupResult.value
  if (result === null) return
  markDialog.content = composeLookupContent(result)
}

/** ダイアログを閉じたら引き当ての状態も戻す。 */
function resetLookup(): void {
  lookupResult.value = null
  lookupMessage.value = ''
  lookupLoading.value = false
}

/** ツールの選択（本文を選択してから押す順でも付けられるようにする）。 */
function selectTool(tool: ReadingMarkType): void {
  // 標記を付けるときは読み上げを止める（選択が消えて文の区切りが分からなくなるため。設計 §5.5）
  if (ttsPlaying.value) stopTts()
  // 色はツールごとに覚える。色のボタンは、いま選んだツールの色を出す
  colorTool.value = tool
  colorPopoverOpen.value = false
  const selection = tool === 'pen' ? null : extractSelection()
  if (selection) {
    clearSelection()
    applyTool(tool, selection)
    activeTool.value = null
    return
  }
  if (activeTool.value === tool) {
    activeTool.value = null
    return
  }
  activeTool.value = tool
  selectedMark.value = null
}

/** 本文の選択を終えたときに標記を付ける（2.0 の handleTextSelection）。 */
function onDocumentMouseUp(event: MouseEvent): void {
  const tool = activeTool.value
  if (tool === null || tool === 'pen') return
  // ツールバーなど PDF の外で離したときは対象にしない（本文の選択だけを見る）
  const target = event.target as Node | null
  if (target === null || pageEl.value === null || !pageEl.value.contains(target)) return
  const selection = extractSelection()
  if (!selection) return
  selectionHandledAt = Date.now()
  clearSelection()
  applyTool(tool, selection)
}

/** ツールを選んだ状態で PDF ページをクリックしたときは、その位置に既定サイズの標記を作る。 */
function onPageClick(event: MouseEvent): void {
  const tool = activeTool.value
  if (tool === null || tool === 'pen' || markDialogOpen.value) return
  if (Date.now() - selectionHandledAt < 500) return
  const selection = window.getSelection()
  if (selection && selection.rangeCount > 0 && !selection.isCollapsed) return
  const point = pointOf(event)
  if (!point) return
  const rect: MarkRect = {
    x: round4(point.x),
    y: round4(point.y),
    width: round4(Math.min(DEFAULT_MARK_WIDTH, 1 - point.x)),
    height: round4(Math.min(DEFAULT_MARK_HEIGHT, 1 - point.y))
  }
  applyTool(tool, { text: '', rects: [rect] })
}

async function saveMarkDialog(): Promise<void> {
  markDialogError.value = ''
  const target = markDialog.targetText.trim()
  const content = markDialog.content.trim()
  if (target === '' && content === '') {
    markDialogError.value = '標記の対象文字か内容を入力してください。'
    return
  }
  markDialogOpen.value = false
  await createMark({
    markType: markDialog.markType,
    targetText: target === '' ? undefined : target,
    content: content === '' ? undefined : content,
    rects: pendingRects
  })
  activeTool.value = null
}

function openMarkPopup(mark: ReadingMark): void {
  selectedMark.value = selectedMark.value?.markId === mark.markId ? null : mark
}

/** 標記の一覧から、そのページへ移動して内容を出す。 */
async function jumpToMark(mark: ReadingMark): Promise<void> {
  selectedMark.value = mark
  await goToPage(mark.pageNo)
}

async function removeMark(mark: ReadingMark): Promise<void> {
  const target = book.value
  if (!target || busy.value) return
  busy.value = true
  try {
    const response = await deleteReadingMark(mark.markId)
    toast.success(response.data.message)
    selectedMark.value = null
    if (detail.value) {
      detail.value = { ...detail.value, marks: detail.value.marks.filter((item) => item.markId !== mark.markId) }
    }
    await loadBookMarks(target.bookId)
  } catch (caught) {
    toast.danger(messageOf(caught, '標記を削除できませんでした。'))
  } finally {
    busy.value = false
  }
}

/* ---------- 手書き（pen） ---------- */

function onPenDown(event: PointerEvent): void {
  if (activeTool.value !== 'pen') return
  const point = pointOf(event)
  if (!point) return
  penActive.value = true
  penDraft.value = [point]
}

function onPenMove(event: PointerEvent): void {
  if (!penActive.value) return
  const point = pointOf(event)
  if (point) penDraft.value = [...penDraft.value, point]
}

function onPenUp(): void {
  if (!penActive.value) return
  penActive.value = false
  const points = penDraft.value
  penDraft.value = []
  if (points.length < 2) return
  void createMark({ markType: 'pen', points })
}

/* ---------- 読書記録 ---------- */

function openRecordDialog(): void {
  if (!book.value) return
  const start = Math.min(sessionFirstPage.value, currentPage.value)
  const end = Math.max(sessionFirstPage.value, currentPage.value)
  recordForm.pageStart = start
  recordForm.pageEnd = end
  // 読書時間は空（0）から始める。目安として、この画面を開いてからの経過分を下に出す。
  recordForm.minutes = 0
  recordForm.memo = ''
  Object.keys(recordErrors).forEach((key) => delete recordErrors[key])
  recordDialogOpen.value = true
}

/** この画面を開いてからの経過（分）。読書時間を入れる目安。 */
const elapsedMinutes = computed(() => Math.max(1, Math.round((Date.now() - sessionStartedAt.value) / 60000)))

async function saveRecord(): Promise<void> {
  const target = book.value
  if (!target || busy.value) return
  Object.keys(recordErrors).forEach((key) => delete recordErrors[key])
  if (recordForm.pageStart < 1) recordErrors.pageStart = '開始ページは1以上で入力してください。'
  if (recordForm.pageEnd < recordForm.pageStart) {
    recordErrors.pageEnd = '終了ページは開始ページ以上で入力してください。'
  }
  if (recordForm.minutes < 0) recordErrors.minutes = '読書時間は0以上で入力してください。'
  if (Object.keys(recordErrors).length > 0) return

  busy.value = true
  try {
    const response = await saveReadingRecord(target.bookId, {
      pageStart: recordForm.pageStart,
      pageEnd: recordForm.pageEnd,
      minutes: recordForm.minutes,
      markCount: pageMarks.value.length,
      memo: recordForm.memo.trim() === '' ? undefined : recordForm.memo.trim()
    })
    toast.success(response.data.message)
    recordDialogOpen.value = false
    const updated = response.data.book
    if (updated) {
      detail.value = detail.value ? { ...detail.value, book: updated } : detail.value
    }
    // 保存すると進捗（現在ページ・ステータス・累計時間）も変わるので、本棚と履歴を取り直す
    await loadShelf()
    const refreshed = await fetchReadingBook(target.bookId, { markPage: currentPage.value })
    detail.value = refreshed.data
    await loadHistory(1)
  } catch (caught) {
    toast.danger(messageOf(caught, '読書記録を保存できませんでした。'))
  } finally {
    busy.value = false
  }
}

/* ---------- ライフサイクル ---------- */

const closeMarkDialog = (): void => {
  markDialogOpen.value = false
  resetLookup()
}

onMounted(async () => {
  document.addEventListener('mouseup', onDocumentMouseUp)
  // ブラウザの大きさが変わったら本文の倍率を測り直す（「高さに合わせる」「幅に合わせる」のとき）
  window.addEventListener('resize', onWindowResize)
  // スクロールしても標記ツールを使えるように、位置を置き直す（1 フレームに 1 回）
  window.addEventListener('scroll', scheduleToolsPlace, { passive: true })
  try {
    await loadCategories()
  } catch {
    categories.value = []
  }
  await reload()
})

// 標記ツールの大きさが変わったとき（ツールの出し分け・折り返し）も置き直す
onMounted(() => {
  motionQuery = window.matchMedia?.('(prefers-reduced-motion: reduce)') ?? null
  motionQuery?.addEventListener?.('change', onMotionChange)
})

// 読み上げの声を読む（端末に無ければボタンを無効にして理由を出す）。
// 最初の getVoices() は空のことがあるので `voiceschanged` でも取り直す（設計 §3.1）
onMounted(() => {
  refreshTtsVoices()
  ttsVoicesOff = ttsProvider.onVoicesChanged(refreshTtsVoices)
})

// 標記ツールが現れたら（PDF を開いたとき）、大きさの変化を見ながら位置を置き直す
watch(toolsAnchorEl, (element, _previous, onCleanup) => {
  if (element === null) return
  scheduleToolsPlace()
  if (typeof ResizeObserver === 'undefined') return
  const observer = new ResizeObserver(() => scheduleToolsPlace())
  observer.observe(element)
  onCleanup(() => observer.disconnect())
})

onBeforeUnmount(() => {
  motionQuery?.removeEventListener?.('change', onMotionChange)
  if (openingTimer !== null) window.clearTimeout(openingTimer)
  if (railPreviewTimer !== null) window.clearTimeout(railPreviewTimer)
  toolsCancel?.()
  toolsCancel = null
  document.removeEventListener('mouseup', onDocumentMouseUp)
  window.removeEventListener('resize', onWindowResize)
  window.removeEventListener('scroll', scheduleToolsPlace)
  // 画面を離れたら必ず読み上げを止める（タブを閉じたあとも喋り続ける事故を防ぐ。設計 §5.3）
  stopTts()
  ttsVoicesOff?.()
  ttsVoicesOff = null
  resetPdfState()
})

// 「読む」「本棚へ戻る」や、メニュー・書籍管理から別の本を開いたときに追従する
watch(requestedBookId, () => {
  void reload()
})

watch(currentPage, (page) => {
  pageInput.value = page
})
</script>

<template>
  <div class="rrd-page">
    <p v-if="error" class="alert alert--danger" data-rd-error>{{ error }}</p>

    <!-- 切替タブ（自分の本棚 / 図書館 / 読書履歴）。資料管理と同じく URL クエリ ?view= と同期する。
         既定は自分の本棚（背表紙の棚） -->
    <div v-if="!viewing" class="tabs" role="tablist">
      <button
        type="button" class="tabs__tab" :class="{ 'is-active': activeTab === 'self' }"
        role="tab" :aria-selected="activeTab === 'self'" data-rd-tab="self" @click="selectTab('self')"
      >
        <AppIcon name="book" size="sm" /> 自分の本棚
      </button>
      <button
        type="button" class="tabs__tab" :class="{ 'is-active': activeTab === 'library' }"
        role="tab" :aria-selected="activeTab === 'library'" data-rd-tab="library" @click="selectTab('library')"
      >
        <AppIcon name="book-open" size="sm" /> 図書館
      </button>
      <button
        type="button" class="tabs__tab" :class="{ 'is-active': activeTab === 'history' }"
        role="tab" :aria-selected="activeTab === 'history'" data-rd-tab="history" @click="selectTab('history')"
      >
        <AppIcon name="clock" size="sm" /> 読書履歴
      </button>
    </div>

    <!-- ============ 自分の本棚（背表紙の棚。旧【書籍閲覧2】の本棚タブ） ============ -->
    <template v-if="!viewing">
      <section
        v-if="activeTab === 'self'"
        class="tabs__panel" :class="{ 'is-active': activeTab === 'self' }"
        role="tabpanel" aria-label="自分の本棚"
      >
        <p v-if="(loading || shelfLoading) && shelfBooks.length === 0" class="rrd-loading">読み込んでいます...</p>

        <section v-else class="card rds-shelf" data-rd-self-shelf>
          <div class="card__header">
            <h2 class="card__title">自分の本棚</h2>
            <span v-if="shelfTotals" class="cell-muted" data-rd-shelf-summary>
              全 <span data-rd-shelf-count>{{ shelfTotals.bookCount }}</span> 冊 ・
              読書中 {{ shelfTotals.readingCount }} 冊 ・ 読了 {{ shelfTotals.finishedCount }} 冊
            </span>

            <!-- 棚の背景の切替（写真。選んだ番号は端末に覚える） -->
            <div
              class="rds-bg" data-rd-shelf-bg-picker role="group" aria-label="棚の背景"
            >
              <span class="rds-bg__label">棚の背景</span>
              <button
                v-for="(url, index) in SHELF_BACKGROUNDS" :key="url"
                type="button" class="rds-bg__option" :class="{ 'is-active': shelfBg === index }"
                :style="{ backgroundImage: `url(${url})` }"
                :data-rd-shelf-bg="index" :aria-label="shelfBgLabel(index)" :title="shelfBgLabel(index)"
                :aria-pressed="shelfBg === index"
                @click="selectShelfBg(index)"
              />
            </div>
          </div>

          <div class="rrd-tabs">
            <button
              type="button" class="rrd-tab" :class="{ 'is-active': activeCategory === 'all' }"
              data-rd-category="all" @click="selectCategory('all')"
            >
              すべて
            </button>
            <button
              v-for="category in categories" :key="category.categoryId"
              type="button" class="rrd-tab" :class="{ 'is-active': activeCategory === String(category.categoryId) }"
              :data-rd-category="String(category.categoryId)" @click="selectCategory(String(category.categoryId))"
            >
              {{ category.name }}（{{ category.bookCount }}）
            </button>
            <button
              type="button" class="rrd-tab" :class="{ 'is-active': activeCategory === 'none' }"
              data-rd-category="none" @click="selectCategory('none')"
            >
              未分類
            </button>
          </div>

          <div
            v-if="shelfBooks.length === 0 && activeCategory === 'all'"
            class="rrd-empty rds-shelf__empty" data-rd-my-shelf-empty
          >
            <p>まだ本がありません。図書館から本を追加してください。</p>
            <button
              type="button" class="btn btn--primary btn--sm" data-rd-my-shelf-open-library
              @click="selectTab('library')"
            >
              <AppIcon name="book-open" size="sm" /> 図書館をひらく
            </button>
          </div>

          <!-- 棚の本体（背表紙・木の棚）。本を開く演出とホバーの状態はこの中で完結する -->
          <ShelfCaseList
            v-else
            ref="selfShelf"
            :sections="selfShelfSections"
            :dealing="dealing"
            :has-more="false"
            :shown="shelfBooks.length"
            :total="shelfTotal"
            :background="shelfBg"
            empty-message="この棚には本がありません。別の分類を選んでください。"
            @open="openFromShelf"
          />
        </section>
      </section>

      <!-- ============ 図書館（カードの一覧。旧「本棚」タブ） ============ -->
      <section
        v-if="activeTab === 'library'"
        class="tabs__panel" :class="{ 'is-active': activeTab === 'library' }"
        role="tabpanel" aria-label="図書館"
      >
        <p v-if="(loading || shelfLoading) && shelfBooks.length === 0" class="rrd-loading">読み込んでいます...</p>

        <section v-else class="card" data-rd-shelf>
          <div class="card__header">
            <h2 class="card__title">図書館</h2>
            <span v-if="shelfTotals" class="cell-muted" data-rd-shelf-summary>
              全 {{ shelfTotals.bookCount }} 冊 ・ 読書中 {{ shelfTotals.readingCount }} 冊 ・ 読了 {{ shelfTotals.finishedCount }} 冊 ・
              自分の本棚 <span data-rd-shelf-count>{{ shelfTotals.myShelfCount }}</span> 冊
            </span>
          </div>

          <div class="rrd-tabs">
            <button
              type="button" class="rrd-tab" :class="{ 'is-active': activeCategory === 'all' }"
              data-rd-category="all" @click="selectCategory('all')"
            >
              すべて
            </button>
            <button
              v-for="category in categories" :key="category.categoryId"
              type="button" class="rrd-tab" :class="{ 'is-active': activeCategory === String(category.categoryId) }"
              :data-rd-category="String(category.categoryId)" @click="selectCategory(String(category.categoryId))"
            >
              {{ category.name }}（{{ category.bookCount }}）
            </button>
            <button
              type="button" class="rrd-tab" :class="{ 'is-active': activeCategory === 'none' }"
              data-rd-category="none" @click="selectCategory('none')"
            >
              未分類
            </button>
          </div>

          <p v-if="shelfBooks.length === 0" class="rrd-empty" data-rd-shelf-empty>
            この棚には本がありません。別の分類を選んでください。
          </p>
          <div v-else class="rrd-shelf">
            <article
              v-for="item in shelfBooks" :key="item.bookId" class="rrd-book"
              :class="{ 'is-in-shelf': item.inMyShelf }" :data-rd-shelf-book="item.bookId"
            >
              <div class="rrd-book__cover">
                <img
                  v-if="item.coverAvailable" class="rrd-book__cover-img"
                  :src="readingCoverUrl(item.bookId, item.version)" :alt="`${item.title} の表紙`"
                  data-rd-shelf-cover
                >
                <span v-else class="rrd-book__cover-fallback" data-rd-shelf-cover-fallback>{{ initial(item.title) }}</span>
              </div>

              <div class="rrd-book__main">
                <h3 class="rrd-book__title">{{ item.title }}</h3>
                <p class="rrd-book__author">{{ item.author }}</p>

                <div class="rrd-book__badges">
                  <span class="badge badge--outline">{{ DIFFICULTY_LABELS[item.difficulty] }}</span>
                  <span class="badge" :class="STATUS_BADGES[item.status]">{{ STATUS_LABELS[item.status] }}</span>
                  <span v-if="item.categoryName" class="badge badge--neutral">{{ item.categoryName }}</span>
                  <!-- 全体書籍（管理者の本）はバッジを出さない。家庭の本だけ分かるようにする -->
                  <span v-if="item.scope === 'FAMILY'" class="badge badge--info" data-rd-scope-badge>
                    {{ SCOPE_LABELS.FAMILY }}<template v-if="item.ownerFamilyLabel">（{{ item.ownerFamilyLabel }}）</template>
                  </span>
                  <!-- 【自分の本棚】に入っている本は、カードの色と一緒に分かるようにする（2026-09-15 の指示） -->
                  <span v-if="item.inMyShelf" class="badge badge--primary" data-rd-in-shelf-badge>
                    本棚に追加済み
                  </span>
                </div>

                <div class="rrd-book__tags">
                  <span v-for="tag in item.tags" :key="tag" class="rrd-tag">{{ tag }}</span>
                </div>

                <!-- 進捗は「未着手」の本なら 0%（現在ページは 1 のまま。displayReadPercent に集約） -->
                <div class="rrd-book__progress">
                  <span class="rrd-progress"><span class="rrd-progress__bar" :style="{ width: `${displayReadPercent(item)}%` }" /></span>
                  <span>{{ item.currentPage }} / {{ item.totalPages }} 頁（{{ displayReadPercent(item) }}%）</span>
                </div>

                <!-- PDF の情報は出さない（利用者の指示。読めるかどうかは開いた画面で分かる） -->

                <!-- メタ情報の最後の行の右端に操作のアイコンボタンを並べる（2026-09-15 の指示。
                     操作だけの行を作るとカードの下に空白ができるため、同じ行にまとめる）。
                     アイコンだけなので、読み上げ・ツールチップは title / aria-label で補う。
                     フック（data-rd-open / data-rd-shelf-add / data-rd-shelf-remove / data-rd-mark-clear）は変えない -->
                <div class="rrd-book__stats">
                  <span class="rrd-book__stat">標記 {{ item.markCount }} 件</span>
                  <span class="rrd-book__stat rrd-book__stat--foot">
                    <span>最終読書 {{ dateLabel(item.lastReadAt) }}</span>
                    <span class="rrd-book__actions">
                      <button
                        type="button" class="btn btn--icon btn--sm rrd-book__action--open" data-rd-open
                        :title="`閲覧する（${item.title} の本文を開く）`" :aria-label="`${item.title} を閲覧する`"
                        @click="openBook(item)"
                      >
                        <AppIcon name="book-open" size="sm" />
                      </button>
                      <!-- 【自分の本棚】への出し入れ（2026-09-14 の決定）。押すとサーバが自分の行だけを変える -->
                      <button
                        v-if="!item.inMyShelf"
                        type="button" class="btn btn--icon btn--sm rrd-book__action--shelf" :disabled="busy"
                        title="自分の本棚に入れる" :aria-label="`${item.title} を自分の本棚に入れる`"
                        :data-rd-shelf-add="item.bookId" @click="toggleShelf(item, true)"
                      >
                        <AppIcon name="plus" size="sm" />
                      </button>
                      <button
                        v-else
                        type="button" class="btn btn--icon btn--sm rrd-book__action--shelf" :disabled="busy"
                        title="自分の本棚から外す" :aria-label="`${item.title} を自分の本棚から外す`"
                        :data-rd-shelf-remove="item.bookId" @click="toggleShelf(item, false)"
                      >
                        <AppIcon name="minus" size="sm" />
                      </button>

                      <!-- この本の標記・読書記録をすべて消し、読書の進捗もリセットする（確認してから実行する） -->
                      <button
                        type="button" class="btn btn--icon btn--sm rrd-book__action--clear" :disabled="busy"
                        :title="`標記（${item.markCount} 件）・読書記録・読書の進捗（現在ページ・ステータス）をリセットする`"
                        aria-label="標記クリア（標記・読書記録・読書の進捗をリセット）"
                        data-rd-mark-clear @click="clearMarks(item)"
                      >
                        <AppIcon name="eraser" size="sm" />
                      </button>
                    </span>
                  </span>
                </div>
              </div>
            </article>
          </div>

          <div v-if="shelfHasMore" class="rrd-more">
            <button type="button" class="btn btn--secondary btn--sm" data-rd-shelf-more @click="showMoreBooks">
              もっと見る（{{ shelfBooks.length }} / {{ shelfTotal }} 冊）
            </button>
          </div>
        </section>
      </section>
    </template>

    <!-- ============ 閲覧（?bookId=） ============ -->
    <template v-else>
      <p v-if="loading && !detail" class="rrd-loading">読み込んでいます...</p>

      <template v-else-if="detail">
        <!-- 本文を左カラム（広い列）に置き、読書状況・ページ一覧・標記の一覧は右カラム（320px）にまとめる。
             DOM の順と見た目の順を合わせるため、本文を先に書く（グリッドは reading-reader.css の .rrd-reader） -->
        <div class="rrd-reader">
          <section class="card rrd-viewer-card">
            <div class="card__header">
              <h3 class="card__title">本文</h3>
              <div class="rrd-toolbar">
                <!-- 画面の見出し行を廃止したので、【本棚へ戻る】は本文ツールバーの【前へ】の左に置く -->
                <button type="button" class="btn btn--secondary btn--sm" data-rd-back @click="backToShelf">
                  <AppIcon name="chevron-left" size="sm" /> 本棚へ戻る
                </button>
                <span class="rrd-toolbar__sep" />
                <button type="button" class="btn btn--secondary btn--sm" data-rd-page-prev :disabled="currentPage <= 1" @click="goToPage(currentPage - 1)">
                  <AppIcon name="chevron-left" size="sm" /> 前へ
                </button>
                <input
                  v-model.number="pageInput" class="input rrd-page-input" type="number" min="1" :max="pageTotal || 1"
                  aria-label="ページ番号" data-rd-page-input @change="onPageInput" @keyup.enter="onPageInput"
                >
                <span class="rrd-page-total"><span data-rd-page-count>/ {{ pageTotal }}</span> 頁</span>
                <button type="button" class="btn btn--secondary btn--sm" data-rd-page-next :disabled="currentPage >= pageTotal" @click="goToPage(currentPage + 1)">
                  次へ <AppIcon name="chevron-right" size="sm" />
                </button>
                <span class="rrd-toolbar__gap" />
                <!-- 音声読み上げ（TTS）は標記ツールと同じ「浮いているツールバー」へ移した（2026-09-15 の指示。
                     本文ツールバーは 1280px で折り返さないよう、ページ送りとズーム・記録だけにする） -->
                <button type="button" class="btn btn--secondary btn--sm" title="縮小" data-rd-zoom-out @click="zoomOut">
                  <AppIcon name="zoom-out" size="sm" /> 縮小
                </button>
                <button type="button" class="btn btn--secondary btn--sm" title="拡大" data-rd-zoom-in @click="zoomIn">
                  <AppIcon name="zoom-in" size="sm" /> 拡大
                </button>
                <!-- ズームは 1 つのトグル。「次に押すとどうなるか」を出し、いまのモードを data-rd-fit-mode に出す -->
                <button
                  type="button" class="btn btn--secondary btn--sm" :data-rd-fit-mode="fitMode"
                  :data-rd-zoom-fit="fitMode === 'height' ? '' : undefined"
                  :data-rd-zoom-height="fitMode === 'height' ? undefined : ''"
                  :title="fitToggleTitle" @click="toggleFit"
                >
                  <AppIcon :name="fitToggleIcon" size="sm" /> {{ fitToggleLabel }}
                </button>
                <button type="button" class="btn btn--primary btn--sm" data-rd-log :disabled="busy" @click="openRecordDialog">
                  <AppIcon name="check" size="sm" /> 読書記録を保存
                </button>
              </div>
            </div>

            <div class="rrd-viewer" data-rd-viewer>
              <div ref="stageEl" class="rrd-stage" data-rd-stage>
                <p class="rrd-status" data-rd-pdf-status>{{ pdfStatus }}</p>

                <!-- 読み上げが使えない理由（音声が無い・文字が取れない）。既存の .rrd-help と同じ体裁で出す -->
                <p v-if="ttsBlockedReason !== ''" class="rrd-help rrd-help--tts" data-rd-tts-note>{{ ttsBlockedReason }}</p>

                <div v-if="pdfMissing || pdfError" class="rrd-help" data-rd-pdf-help>
                  <p v-if="pdfMissing" class="rrd-help__title">
                    本文 PDF が登録されていません。読むには次のどちらかが必要です。
                  </p>
                  <p v-else class="rrd-help__title">本文 PDF を表示できませんでした。</p>
                  <ol class="rrd-help__list">
                    <li>保護者の方が「書籍管理」画面からこの本の本文 PDF をアップロードしてください。</li>
                    <li>
                      2.0 のファイル（<code>webapps/file/ENGLISH_READING/</code> 配下）を配備先にコピーし、
                      設定 <code>study21.reading.legacy-storage-root</code> をその場所に向けると、そのまま読めます。
                    </li>
                  </ol>
                  <p class="rrd-help__note">元のファイル名: {{ book?.pdfOriginalName ?? '不明' }}</p>
                </div>

                <div
                  v-else-if="pdfDoc" ref="pageEl" class="rrd-page-box" data-rd-pdf-page
                  :style="pageViewport
                    ? { width: `${Math.ceil(pageViewport.width)}px`, height: `${Math.ceil(pageViewport.height)}px` }
                    : {}"
                  @click="onPageClick"
                  @pointerdown="onPenDown" @pointermove="onPenMove" @pointerup="onPenUp" @pointerleave="onPenUp"
                >
                  <canvas ref="canvasEl" class="rrd-canvas" data-rd-pdf-canvas />
                  <div ref="textLayerEl" class="rrd-text-layer" data-rd-pdf-text-layer />
                  <!-- 読み上げ中の文のハイライト。**標記ではない**（DB には書かず、`markShapes` にも混ぜない）。
                       独立した層にして `pointer-events: none` で、本文の選択や標記の操作を邪魔しない -->
                  <div v-if="ttsHighlight.length > 0" class="rrd-tts-highlight" data-rd-tts-highlight>
                    <span
                      v-for="(rect, index) in ttsHighlight" :key="index"
                      class="rrd-tts-highlight__rect" :style="ttsHighlightStyle(rect)"
                    />
                  </div>
                  <!-- 標記ツールは本文の枠の見えている範囲に貼り付ける（`position: fixed`。位置は placeTools() が入れる）。
                       スクロールしても常に使えるが、本文の枠が画面から出ていくと一緒に隠れる。高さは消費しない。
                       ページ側の操作（クリックで標記・手書き・本文の選択）に伝わらないように止める -->
                  <div ref="toolsAnchorEl" class="rrd-tools-anchor" data-rd-tools-anchor>
                    <div
                      class="rrd-tools" role="toolbar" aria-label="標記ツール" data-rd-tools
                      @click.stop @mousedown.stop @mouseup.stop @pointerdown.stop @pointermove.stop @pointerup.stop
                    >
                      <button
                        v-for="tool in toolOptions" :key="tool"
                        type="button" class="rrd-tool" :class="{ 'is-active': activeTool === tool }"
                        :data-rd-tool="tool" :data-rd-tool-label="toolLabel(tool)"
                        :title="`${toolLabel(tool)}の標記を付ける`"
                        @mousedown.prevent @click="selectTool(tool)"
                      >
                        <AppIcon :name="TOOL_ICONS[tool]" size="sm" /> {{ toolLabel(tool) }}
                      </button>
                      <!-- 色は 1 つのボタンで、いま選んでいるツールの色を出す -->
                      <span class="rrd-color">
                        <button
                          type="button" class="rrd-color__button" data-rd-color-button
                          :title="`${MARK_TYPE_LABELS[colorTool]}の色を選ぶ`"
                          :aria-expanded="colorPopoverOpen ? 'true' : 'false'"
                          @mousedown.prevent @click="colorPopoverOpen = !colorPopoverOpen"
                        >
                          <span class="rrd-color__swatch" :style="{ background: currentColor }" />
                          <span class="rrd-color__value" data-rd-color-value>{{ currentColor }}</span>
                        </button>
                        <span v-if="colorPopoverOpen" class="rrd-color__popover" data-rd-color-popover>
                          <button
                            v-for="color in paletteColors" :key="color"
                            type="button" class="rrd-color__option" :class="{ 'is-active': color === currentColor }"
                            :style="{ background: color }" :data-rd-color="color" :title="color"
                            @mousedown.prevent @click="chooseColor(color)"
                          />
                        </span>
                      </span>

                      <!-- 音声読み上げ（TTS）。標記ツールと同じ浮いているツールバーに置いて、
                           本文をスクロールしても常に使えるようにする（2026-09-15 の指示）。
                           主ボタンは**アイコンだけ**（読み上げ中は停止のアイコンに変わる）。
                           本文を選んでから押しても選択が消えないように `mousedown` を止める -->
                      <span class="rrd-tools__sep" />
                      <span class="rrd-tts">
                        <button
                          type="button" class="rrd-tool rrd-tts__button" data-rd-tts
                          :data-rd-tts-state="ttsState" :title="ttsButtonTitle" :aria-label="ttsButtonTitle"
                          :disabled="!ttsPlaying && !ttsReady" @mousedown.prevent @click="toggleTts"
                        >
                          <AppIcon :name="ttsIcon" size="sm" />
                        </button>
                        <button
                          type="button" class="rrd-tool rrd-tts__settings" data-rd-tts-settings
                          title="読み上げのはやさ・こえを選ぶ" aria-label="読み上げのはやさ・こえを選ぶ"
                          :aria-expanded="ttsPanelOpen ? 'true' : 'false'" @click="ttsPanelOpen = !ttsPanelOpen"
                        >
                          <AppIcon name="sliders" size="sm" />
                        </button>
                        <span v-if="ttsPanelOpen" class="rrd-tts__panel" data-rd-tts-panel>
                          <span class="rrd-tts__head"><AppIcon name="speaker" size="sm" /> 読み上げ</span>
                          <label class="rrd-tts__field">
                            <span class="rrd-tts__label">はやさ</span>
                            <select class="select rrd-tts__select" data-rd-tts-rate :value="String(ttsPrefs.rate)" @change="selectTtsRate">
                              <option v-for="rate in TTS_RATE_OPTIONS" :key="rate" :value="String(rate)">{{ rateLabel(rate) }}</option>
                            </select>
                          </label>
                          <label v-for="lang in ttsTargetLangs" :key="lang" class="rrd-tts__field">
                            <span class="rrd-tts__label">{{ TTS_LANG_LABELS[lang] }}のこえ</span>
                            <select
                              v-if="ttsVoiceOptions(lang).length > 0" class="select rrd-tts__select"
                              :data-rd-tts-voice="lang" :value="ttsPrefs.voices[lang] ?? ''" @change="selectTtsVoice(lang, $event)"
                            >
                              <option value="">自動で選ぶ</option>
                              <option v-for="voice in ttsVoiceOptions(lang)" :key="voiceKey(voice)" :value="voiceKey(voice)">
                                {{ voiceLabel(voice) }}
                              </option>
                            </select>
                            <span v-else class="rrd-tts__warn" :data-rd-tts-voice-note="lang">{{ missingVoiceNote(lang) }}</span>
                          </label>
                        </span>
                      </span>
                    </div>
                  </div>

                  <div class="rrd-mark-layer" :class="{ 'is-marking': activeTool !== null }" data-rd-mark-layer>
                    <template v-for="shape in markShapes" :key="shape.mark.markId">
                      <button
                        v-for="(rect, index) in shape.rects" :key="`${shape.mark.markId}-${index}`"
                        type="button" class="rrd-mark-rect" :class="`is-${shape.mark.markType}`"
                        :style="markRectStyle(shape.mark, rect)" :data-rd-mark-rect="shape.mark.markId"
                        :aria-label="markAriaLabel(shape.mark)" @click.stop="openMarkPopup(shape.mark)"
                      />
                      <svg
                        v-if="shape.points.length > 0" :key="`${shape.mark.markId}-pen`"
                        class="rrd-pen" viewBox="0 0 100 100" preserveAspectRatio="none"
                        :data-rd-mark-rect="shape.mark.markId" @click.stop="openMarkPopup(shape.mark)"
                      >
                        <path :d="penPath(shape)" :stroke="markColor(shape.mark)" :stroke-width="PEN_THICKNESS" fill="none" stroke-linecap="round" stroke-linejoin="round" />
                      </svg>
                    </template>

                    <svg v-if="penDraft.length > 1" class="rrd-pen is-draft" viewBox="0 0 100 100" preserveAspectRatio="none" data-rd-pen-draft>
                      <path
                        :d="penDraft.map((point, index) => `${index === 0 ? 'M' : 'L'}${round4(point.x * 100)} ${round4(point.y * 100)}`).join(' ')"
                        :stroke="MARK_TYPE_COLORS.pen" :stroke-width="PEN_THICKNESS" fill="none" stroke-linecap="round"
                      />
                    </svg>

                    <div v-if="selectedMark" class="rrd-popup" data-rd-mark-popup :style="popupStyle">
                      <div class="rrd-popup__head">
                        <span class="badge badge--neutral">{{ markLabel(selectedMark) }}</span>
                        <span class="rrd-popup__page">P.{{ selectedMark.pageNo }}</span>
                        <button type="button" class="rrd-popup__close" aria-label="閉じる" data-rd-mark-popup-close @click="selectedMark = null">
                          <AppIcon name="x" size="sm" />
                        </button>
                      </div>
                      <p v-if="selectedMark.targetText" class="rrd-popup__word">{{ selectedMark.targetText }}</p>
                      <p v-if="selectedMark.content" class="rrd-popup__content">{{ selectedMark.content }}</p>
                      <div class="rrd-popup__actions">
                        <button
                          type="button" class="btn btn--icon btn--sm is-danger" title="標記を削除"
                          :aria-label="`${markLabel(selectedMark)}の標記を削除`" :data-rd-mark-delete="selectedMark.markId"
                          :disabled="busy" @click="removeMark(selectedMark)"
                        >
                          <AppIcon name="trash" size="sm" />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </section>

          <aside class="rrd-side">
            <!-- 書名・バッジ・進捗は【読書状況】カードにまとめる（保存ボタンは本文のツールバー右端） -->
            <section class="card">
              <div class="card__header">
                <h3 class="card__title">読書状況</h3>
              </div>
              <div class="card__body rrd-status-card">
                <!-- 書名とバッジ（難易度・言語・分類・読書ステータス）。進捗は下の行に 1 か所だけ出す -->
                <h4 class="rrd-status-card__title">{{ book?.title }}</h4>
                <p class="rrd-status-card__meta">
                  <span>{{ book?.author }}</span>
                  <span class="badge badge--outline">{{ book ? DIFFICULTY_LABELS[book.difficulty] : '' }}</span>
                  <span v-if="book" class="badge badge--neutral">{{ book.language }}</span>
                  <span v-if="book?.categoryName" class="badge badge--neutral">{{ book.categoryName }}</span>
                  <span class="badge" :class="STATUS_BADGES[book?.status ?? '未着手']" data-rd-status-badge>
                    {{ STATUS_LABELS[book?.status ?? '未着手'] }}
                  </span>
                </p>

                <div class="rrd-status-list">
                  <div class="rrd-status-row">
                    <span class="rrd-status-row__label">進捗</span>
                    <span class="rrd-status-row__value" data-rd-progress>
                      {{ book?.currentPage }} / {{ book?.totalPages }} 頁（{{ book ? displayReadPercent(book) : 0 }}%）
                    </span>
                  </div>
                  <div class="rrd-status-row">
                    <span class="rrd-status-row__label">累計読書</span>
                    <span class="rrd-status-row__value">{{ minutesLabel(book?.totalMinutes ?? 0) }}</span>
                  </div>
                  <div class="rrd-status-row">
                    <span class="rrd-status-row__label">最終読書</span>
                    <span class="rrd-status-row__value">{{ dateLabel(book?.lastReadAt ?? null) }}</span>
                  </div>
                  <div class="rrd-status-row">
                    <span class="rrd-status-row__label">標記</span>
                    <span class="rrd-status-row__value">{{ book?.markCount ?? 0 }} 件</span>
                  </div>
                </div>
              </div>
            </section>

            <!-- ページ一覧は右カラムのカード。カードにマウスを乗せた（またはキーボードで入った）ときだけ、
                 レイアウトを動かさない「悬浮式」の一覧を本文の上へ左向きに開く（2026-09-14/15 の指示。
                 【開く】【閉じる】のボタンとカードの中の常駐一覧は廃止した）。
                 `data-rd-rail-open` は「悬浮式の一覧が出ているか」を表す -->
            <section
              class="card rrd-rail-card" :data-rd-rail-open="railPreviewOpen ? 'true' : 'false'"
              @mouseenter="openRailPreview" @mouseleave="scheduleRailPreviewClose"
              @focusin="openRailPreview" @focusout="scheduleRailPreviewClose"
            >
              <div class="card__header">
                <h3 class="card__title"><AppIcon name="list" size="sm" /> ページ一覧</h3>
                <div class="rrd-rail-head">
                  <span class="cell-muted" data-rd-rail-summary>P.{{ currentPage }} / {{ railTotal }} 頁</span>
                </div>
              </div>

              <div v-if="railPreviewOpen" class="rrd-rail-popover" data-rd-rail-popover>
                <div class="rrd-rail-popover__head">
                  <span class="rrd-rail-popover__title"><AppIcon name="list" size="sm" /> ページ一覧</span>
                  <span class="rrd-rail-popover__now" data-rd-rail-popover-summary>P.{{ currentPage }} / {{ railTotal }} 頁</span>
                </div>
                <p class="rrd-rail-popover__legend">
                  <span class="rrd-legend"><i class="rrd-legend__dot is-read" aria-hidden="true" />既読</span>
                  <span class="rrd-legend"><i class="rrd-legend__dot is-unread" aria-hidden="true" />未読</span>
                  <span class="rrd-legend"><i class="rrd-legend__dot has-mark" aria-hidden="true" />標記あり</span>
                </p>
                <div class="rrd-rail-popover__grid" data-rd-page-rail>
                  <button
                    v-for="pageNo in pageNumbers" :key="pageNo"
                    type="button" class="rrd-page-chip rrd-rail-popover__chip"
                    :class="{
                      'is-read': pageNo <= readUpToPage,
                      'is-unread': pageNo > readUpToPage,
                      'is-active': pageNo === currentPage,
                      'has-mark': markedPages.has(pageNo)
                    }"
                    :data-rd-page="pageNo" :data-rd-page-state="pageNo <= readUpToPage ? 'read' : 'unread'"
                    :title="pageChipTitle(pageNo)" @click="goToPage(pageNo)"
                  >
                    {{ pageNo }}
                  </button>
                </div>
                <div class="rrd-rail-popover__foot">
                  <span class="rrd-rail-popover__hint">選ぶとそのページへ移動します</span>
                  <button type="button" class="btn btn--secondary btn--sm" data-rd-rail-here @click="goToPage(currentPage)">
                    <AppIcon name="check" size="sm" /> 現在のページへ
                  </button>
                </div>
              </div>
            </section>

            <section class="card">
              <div class="card__header">
                <h3 class="card__title"><AppIcon name="list" size="sm" /> 標記の一覧</h3>
                <span class="cell-muted">{{ bookMarks.length }} 件</span>
              </div>
              <div class="card__body">
                <p v-if="bookMarks.length === 0" class="rrd-empty" data-rd-mark-list-empty>
                  標記はまだありません。本文を選んで標記の種類を押すと付けられます。
                </p>
                <div v-else class="rrd-mark-list" data-rd-mark-list>
                  <div v-for="group in markGroups" :key="group.type" class="rrd-mark-group">
                    <p class="rrd-mark-group__head">
                      <span class="rrd-mark-group__label">{{ group.label }}</span>
                      <span class="rrd-mark-group__count" :data-rd-mark-group-count="group.type">{{ group.items.length }} 件</span>
                    </p>
                    <button
                      v-for="mark in group.items" :key="mark.markId"
                      type="button" class="rrd-mark-item" :class="{ 'is-active': selectedMark?.markId === mark.markId }"
                      :data-rd-mark-item="mark.markId" @click="jumpToMark(mark)"
                    >
                      <span class="rrd-mark-item__text">{{ mark.targetText ?? mark.content ?? '（内容なし）' }}</span>
                      <span class="rrd-mark-item__meta">P.{{ mark.pageNo }}</span>
                    </button>
                  </div>
                </div>
              </div>
            </section>
          </aside>
        </div>
      </template>
    </template>

    <!-- ============ 読書履歴 ============ -->
    <!-- 本棚の「読書履歴」タブ＝全書籍／閲覧画面＝この本の履歴。絞り込み・件数・ページングは共通 -->
    <section
      v-if="viewing || activeTab === 'history'"
      class="tabs__panel"
      :class="{ 'is-active': viewing || activeTab === 'history' }"
      :role="viewing ? undefined : 'tabpanel'"
      :aria-label="viewing ? 'この本の読書履歴' : '読書履歴'"
    >
      <section
        class="card"
        :data-rd-history="viewing ? '' : undefined"
        :data-rd-history-all="viewing ? undefined : ''"
      >
        <div class="card__header">
          <h2 class="card__title">{{ viewing ? 'この本の読書履歴' : '読書履歴' }}</h2>
          <span class="cell-muted" data-rd-history-count>全 {{ historyTotal }} 件</span>
        </div>
        <div class="card__body">
          <div class="filters rrd-history-filters">
            <div class="filters__row">
              <!-- 閲覧モードはその本の履歴なので、書籍の絞り込みは出さない -->
              <span v-if="!viewing" class="filter-item">
                <span class="filter-item__label">書籍：</span>
                <select v-model="historyBookId" class="select" data-rd-history-book @change="searchHistory">
                  <option value="">すべての書籍</option>
                  <option v-for="item in historyBooks" :key="item.bookId" :value="item.bookId" :title="item.title">
                    {{ item.title }}
                  </option>
                </select>
              </span>
              <span class="filter-item">
                <span class="filter-item__label">読書日 From：</span>
                <input v-model="historyFilters.dateFrom" class="input" type="date" data-rd-history-from>
              </span>
              <span class="filter-item">
                <span class="filter-item__label">To：</span>
                <input v-model="historyFilters.dateTo" class="input" type="date" data-rd-history-to>
              </span>
              <span class="filter-item">
                <span class="filter-item__label">ページ番号：</span>
                <input
                  v-model="historyFilters.pageNo" class="input" type="number" min="1"
                  placeholder="例: 12" data-rd-history-page-no @keydown.enter.prevent="searchHistory"
                >
              </span>
              <span class="rrd-history-filters__actions">
                <button type="button" class="btn btn--primary" :disabled="historyLoading" data-rd-history-search @click="searchHistory">
                  <AppIcon name="search" size="sm" /> 検索
                </button>
                <button type="button" class="btn btn--secondary" :disabled="historyLoading" data-rd-history-reset @click="resetHistory">
                  <AppIcon name="rotate" size="sm" /> リセット
                </button>
              </span>
            </div>
          </div>

          <p v-if="historyItems.length === 0" class="rrd-empty" data-rd-history-empty>
            該当する読書履歴がありません。
          </p>
          <div v-else class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th v-if="!viewing">書籍</th><th>日時</th><th>ページ</th><th>時間</th><th>メモ</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="record in historyItems" :key="record.recordId" :data-rd-history-row="record.recordId">
                  <td v-if="!viewing">{{ record.bookTitle ?? '—' }}</td>
                  <td>{{ dateLabel(record.readAt) }}</td>
                  <td>{{ pageRangeLabel(record) }}</td>
                  <td>{{ minutesLabel(record.minutes) }}</td>
                  <td class="rrd-cell-memo">{{ record.memo ?? '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- ページング（左に件数情報・右に件数セレクト＋ページ番号。画面共通のルール） -->
          <div v-if="historyItems.length > 0" class="pagination" data-rd-history-pager>
            <span class="pagination__info">全 {{ historyTotal }} 件（{{ historyPage }} / {{ historyPages }} ページ）</span>
            <!-- 1 ページの件数はページ番号の左隣に置く。変えたら 1 ページ目から読み直す -->
            <label class="pagination__size">
              <span>件数</span>
              <select
                v-model.number="historySize" class="select" aria-label="1ページの件数" data-rd-history-size
                @change="searchHistory"
              >
                <option v-for="option in RECORD_PAGE_SIZES" :key="option" :value="option">{{ option }} 件</option>
              </select>
            </label>
            <div class="pagination__pages">
              <button
                type="button" class="page-btn" :disabled="historyPage <= 1" data-rd-history-prev
                @click="goHistoryPage(historyPage - 1)"
              >
                ‹
              </button>
              <template v-for="(item, key) in historyPagerItems" :key="key">
                <span v-if="item === 'gap'" class="page-dots">…</span>
                <button
                  v-else type="button" class="page-btn" :class="{ 'is-active': item === historyPage }"
                  :data-rd-history-page="item" @click="goHistoryPage(item)"
                >
                  {{ item }}
                </button>
              </template>
              <button
                type="button" class="page-btn" :disabled="historyPage >= historyPages" data-rd-history-next
                @click="goHistoryPage(historyPage + 1)"
              >
                ›
              </button>
            </div>
          </div>
        </div>
      </section>
    </section>

    <!-- 標記の入力（語彙・メモ） -->
    <div v-if="markDialogOpen" class="overlay">
      <section class="dialog dialog--md rrd-mark-dialog" role="dialog" aria-modal="true" aria-labelledby="rrdMarkDialogTitle" data-rd-mark-dialog>
        <div class="dialog__head">
          <h2 id="rrdMarkDialogTitle" class="dialog__title">
            <AppIcon name="bookmark" size="sm" /> {{ toolLabel(markDialog.markType) }}の標記（P.{{ currentPage }}）
          </h2>
          <button type="button" class="dialog__close" aria-label="閉じる" data-rd-mark-close @click="closeMarkDialog">
            <AppIcon name="x" size="sm" />
          </button>
        </div>
        <div class="dialog__body">
          <div class="field">
            <label class="field__label" for="rdMarkTarget">対象文字</label>
            <input id="rdMarkTarget" v-model="markDialog.targetText" class="input" type="text" maxlength="500" placeholder="例: plume">
          </div>
          <!-- 語彙（中国語の本では「読み方」）は、選んだ語の意味を自動で引いて出す -->
          <div v-if="markDialog.markType === 'vocabulary'" class="rrd-lookup" data-rd-lookup>
            <p class="rrd-lookup__status" data-rd-lookup-status>{{ lookupStatus }}</p>
            <template v-if="lookupHasMeaning && lookupResult">
              <dl class="rrd-lookup__list">
                <div v-if="lookupResult.japanese" class="rrd-lookup__row">
                  <dt>日本語</dt>
                  <dd data-rd-lookup-japanese>{{ lookupResult.japanese }}</dd>
                </div>
                <div v-if="lookupResult.chinese" class="rrd-lookup__row">
                  <dt>中国語</dt>
                  <dd data-rd-lookup-chinese>{{ lookupResult.chinese }}</dd>
                </div>
                <div v-if="lookupResult.pinyin" class="rrd-lookup__row rrd-lookup__row--pinyin">
                  <dt>ピンイン</dt>
                  <dd data-rd-lookup-pinyin>{{ lookupResult.pinyin }}</dd>
                </div>
                <div v-if="lookupResult.explanation" class="rrd-lookup__row">
                  <dt>解説</dt>
                  <dd data-rd-lookup-explanation>{{ lookupResult.explanation }}</dd>
                </div>
              </dl>
              <button type="button" class="btn btn--secondary btn--sm" data-rd-lookup-apply @click="applyLookup">
                内容に入れる
              </button>
            </template>
            <p v-else-if="lookupMessage !== ''" class="rrd-lookup__note" data-rd-lookup-note>
              {{ lookupMessage }}
            </p>
          </div>

          <div class="field">
            <label class="field__label" for="rdMarkContent">
              {{ markDialog.markType === 'memo' ? 'メモ' : '意味・内容' }}
            </label>
            <textarea id="rdMarkContent" v-model="markDialog.content" class="textarea" rows="3" maxlength="2000" />
          </div>
          <p v-if="markDialogError" class="field__error" data-rd-mark-dialog-error>{{ markDialogError }}</p>
          <p class="rrd-hint">
            保存すると、いま表示しているページ（P.{{ currentPage }}）に標記が付きます。
          </p>
        </div>
        <div class="dialog__foot">
          <button type="button" class="btn btn--secondary" data-rd-mark-cancel @click="closeMarkDialog">キャンセル</button>
          <button type="button" class="btn btn--primary" data-rd-mark-save :disabled="busy" @click="saveMarkDialog">
            <AppIcon name="check" size="sm" /> 保存
          </button>
        </div>
      </section>
    </div>

    <!-- 読書記録 -->
    <div v-if="recordDialogOpen" class="overlay">
      <section class="dialog dialog--md" role="dialog" aria-modal="true" aria-labelledby="rdRecTitle" data-rd-record-dialog>
        <div class="dialog__head">
          <h2 id="rdRecTitle" class="dialog__title">
            <AppIcon name="check" size="sm" /> 読書記録（{{ book?.title }}）
          </h2>
          <button type="button" class="dialog__close" aria-label="閉じる" data-rd-record-close @click="recordDialogOpen = false">
            <AppIcon name="x" size="sm" />
          </button>
        </div>
        <div class="dialog__body">
          <div class="rrd-dialog-grid">
            <div class="field">
              <label class="field__label" for="rdRecStart">開始ページ<span class="rrd-required">必須</span></label>
              <input
                id="rdRecStart" v-model.number="recordForm.pageStart" class="input" type="number" min="1"
                :max="pageTotal || 1" data-rd-record-start
                :class="{ 'is-invalid': recordErrors.pageStart !== undefined }"
              >
              <p v-if="recordErrors.pageStart" class="field__error">{{ recordErrors.pageStart }}</p>
            </div>
            <div class="field">
              <label class="field__label" for="rdRecEnd">終了ページ<span class="rrd-required">必須</span></label>
              <input
                id="rdRecEnd" v-model.number="recordForm.pageEnd" class="input" type="number" min="1"
                :max="pageTotal || 1" data-rd-record-end
                :class="{ 'is-invalid': recordErrors.pageEnd !== undefined }"
              >
              <p v-if="recordErrors.pageEnd" class="field__error">{{ recordErrors.pageEnd }}</p>
            </div>
            <div class="field">
              <label class="field__label" for="rdRecMinutes">読書時間（分）</label>
              <input
                id="rdRecMinutes" v-model.number="recordForm.minutes" class="input" type="number" min="0"
                max="1440" data-rd-record-minutes :class="{ 'is-invalid': recordErrors.minutes !== undefined }"
              >
              <p v-if="recordErrors.minutes" class="field__error">{{ recordErrors.minutes }}</p>
              <p class="field__hint">この画面を開いてから約 {{ elapsedMinutes }} 分経過しています。</p>
            </div>
            <div class="field field--wide">
              <label class="field__label" for="rdRecMemo">メモ</label>
              <textarea id="rdRecMemo" v-model="recordForm.memo" class="textarea" rows="3" maxlength="2000" data-rd-record-memo />
            </div>
          </div>
          <p class="rrd-hint">
            今回読んだ範囲（P.{{ recordForm.pageStart }} 〜 P.{{ recordForm.pageEnd }}）を保存します。
            保存すると現在ページ・読書ステータス・累計読書時間・最終読書日時が更新されます。
          </p>
        </div>
        <div class="dialog__foot">
          <button type="button" class="btn btn--secondary" data-rd-record-cancel @click="recordDialogOpen = false">キャンセル</button>
          <button type="button" class="btn btn--primary" data-rd-record-save :disabled="busy" @click="saveRecord">
            <AppIcon name="check" size="sm" /> {{ busy ? '保存中...' : '保存' }}
          </button>
        </div>
      </section>
    </div>

    <!-- 背表紙から開いたときのつなぎ（表紙が中央に出てから本文に切り替わる） -->
    <div v-if="openingBook" class="rds-opening" data-rd-opening aria-hidden="true">
      <div class="rds-opening__book" :class="toneClass(openingBook)">
        <img
          v-if="openingBook.coverAvailable" class="rds-opening__cover"
          :src="readingCoverUrl(openingBook.bookId, openingBook.version)" alt=""
        >
        <template v-else>
          <p class="rds-opening__title">{{ openingBook.title }}</p>
          <p class="rds-opening__author">{{ openingBook.author }}</p>
        </template>
      </div>
    </div>
  </div>
</template>
