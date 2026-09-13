<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ApiError, formatIsoDate, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  changeTodoStatus,
  createTodo,
  deleteTodo,
  fetchTodoCalendar,
  searchTodos,
  updateTodo,
  type TodoPriorityCode,
  type TodoRow,
  type TodoSaveRequest,
  type TodoStatusCode
} from '@/api/todos'
import '@/features/todo/todo.css'

/**
 * TODO（2.0 の todo.jsp 相当を 2.1 向けに作り直した画面）。
 *
 * ・一覧（親タスク＋子タスク）と カレンダー（期限日ごとの件数）の 2 つの見方
 * ・新規／編集ダイアログの子タスクは **Excel 風グリッド**（行追加・×削除・NO 連番・
 *   Excel からの貼り付け）で入力する
 * ・保護者のときは「お子さまの TODO にも登録する」で子どもにも同じ内容を作れる
 */
const toast = useToast()

const STATUS_LABELS: Record<TodoStatusCode, string> = { TODO: '未着手', DOING: '進行中', DONE: '完了' }
const STATUS_BADGE: Record<TodoStatusCode, string> = {
  TODO: 'badge--warning', DOING: 'badge--info', DONE: 'badge--success'
}
const PRIORITY_LABELS: Record<TodoPriorityCode, string> = { HIGH: '高', NORMAL: '中', LOW: '低' }
const WEEKDAYS = ['月', '火', '水', '木', '金', '土', '日']

/* ---------- 画面の状態 ---------- */
const view = ref<'list' | 'calendar'>('list')
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const rows = ref<TodoRow[]>([])
const totalElements = ref(0)
const totalPages = ref(1)
const page = ref(1)
const counts = reactive({ open: 0, doing: 0, done: 0 })
const filters = reactive({ keyword: '', status: '', priority: '', includeDone: false })

/** 親タスクの子タスクを開いているか（行ごと。既定は閉じた状態） */
const expandedIds = ref<Record<number, boolean>>({})

/** 子タスクを持つ行があるか（「すべて展開」を出すかの判定）。 */
const hasExpandableRows = computed(() => rows.value.some((row) => row.children.length > 0))

/** 子タスクを持つ行がすべて開いているか。 */
const allExpanded = computed(() =>
  rows.value.filter((row) => row.children.length > 0).every((row) => isExpanded(row.todoId)))

function isExpanded(todoId: number): boolean {
  return expandedIds.value[todoId] === true
}

function toggleRow(todoId: number): void {
  expandedIds.value = { ...expandedIds.value, [todoId]: !isExpanded(todoId) }
}

/** 表示中の行をまとめて開く / 閉じる。 */
function toggleAllRows(): void {
  const next: Record<number, boolean> = { ...expandedIds.value }
  const open = !allExpanded.value
  for (const row of rows.value) {
    if (row.children.length > 0) {
      next[row.todoId] = open
    }
  }
  expandedIds.value = next
}

/** カレンダー */
const calCursor = ref(new Date())
const calCells = ref<Map<string, { openCount: number; doneCount: number }>>(new Map())

/* ---------- ダイアログ ---------- */
const dialogOpen = ref(false)
const editingId = ref<number | null>(null)
const editingVersion = ref<number | null>(null)
const form = reactive({
  title: '',
  memo: '',
  priority: 'NORMAL' as string,
  dueDate: '',
  alsoForStudent: false
})
/** Excel 風グリッドの 1 行。 */
interface GridRow {
  key: number
  title: string
  memo: string
  priority: TodoPriorityCode
  dueDate: string
}
const gridRows = ref<GridRow[]>([])
let gridKey = 0

/** グリッドの列（タイトル / 優先度 / 期限 / メモ）。並びは Excel からの貼り付け順と同じ。 */
const GRID_COLUMNS = [
  { key: 'title', label: 'タイトル' },
  { key: 'priority', label: '優先度' },
  { key: 'dueDate', label: '期限' },
  { key: 'memo', label: 'メモ' }
] as const
type GridColumnKey = (typeof GRID_COLUMNS)[number]['key']

/**
 * Excel 風グリッドの列の幅（％）。タイトル / 優先度 / 期限 / メモ の 4 列で合計 89%。
 * 残りの 11% は左の ×（5%）と NO（6%）。**全部 ％ で持つ**ので、
 * 幅を変えても表の幅（100%）は変わらない。
 */
const DEFAULT_COLUMN_WIDTHS = [34, 16, 21, 18]
const gridColumnWidths = ref<number[]>([...DEFAULT_COLUMN_WIDTHS])
/** 列の幅は最小 8%（これ以上は狭くできない）。 */
const MIN_COLUMN_WIDTH = 8

/** 列の幅を既定に戻す（つまみのダブルクリック）。 */
function resetColumnWidths(): void {
  gridColumnWidths.value = [...DEFAULT_COLUMN_WIDTHS]
}

/** つまみのキーボード操作（←→ で 1%、Shift で 5%）。 */
function onColumnResizeKeydown(index: number, event: KeyboardEvent): void {
  const step = event.shiftKey ? 5 : 1
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    resizeColumns(index, -step)
  } else if (event.key === 'ArrowRight') {
    event.preventDefault()
    resizeColumns(index, step)
  }
}

/** 左右の列で幅をやり取りする（合計は変えない）。 */
function resizeColumns(index: number, deltaPercent: number): void {
  const next = [...gridColumnWidths.value]
  const left = next[index] + deltaPercent
  const right = next[index + 1] - deltaPercent
  if (left < MIN_COLUMN_WIDTH || right < MIN_COLUMN_WIDTH) {
    return
  }
  next[index] = left
  next[index + 1] = right
  gridColumnWidths.value = next
}

let columnResize: { index: number; startX: number; startWidths: number[]; tableWidth: number } | null = null

/** つまみを掴んだら、マウスの動きに合わせて列の幅を変える。 */
function startColumnResize(index: number, event: MouseEvent): void {
  const table = gridTable.value
  if (table === null) {
    return
  }
  columnResize = {
    index,
    startX: event.clientX,
    startWidths: [...gridColumnWidths.value],
    tableWidth: table.getBoundingClientRect().width
  }
  window.addEventListener('mousemove', moveColumnResize)
  window.addEventListener('mouseup', endColumnResize)
}

function moveColumnResize(event: MouseEvent): void {
  const state = columnResize
  if (state === null || state.tableWidth <= 0) {
    return
  }
  // 動かした距離を％に直して、左右の列でやり取りする
  const delta = ((event.clientX - state.startX) / state.tableWidth) * 100
  const left = state.startWidths[state.index] + delta
  const right = state.startWidths[state.index + 1] - delta
  if (left < MIN_COLUMN_WIDTH || right < MIN_COLUMN_WIDTH) {
    return
  }
  const next = [...gridColumnWidths.value]
  next[state.index] = left
  next[state.index + 1] = right
  gridColumnWidths.value = next
}

function endColumnResize(): void {
  columnResize = null
  window.removeEventListener('mousemove', moveColumnResize)
  window.removeEventListener('mouseup', endColumnResize)
}

/** 選択中のセルと範囲（表計算のように扱う）。 */
const gridTable = ref<HTMLTableElement | null>(null)
const gridCursor = ref<{ row: number; col: number } | null>(null)
const gridAnchor = ref<{ row: number; col: number } | null>(null)
const gridDragging = ref(false)

/**
 * いま入力欄を出しているセル（Excel の「編集モード」）。
 * クリックでは入らず、**ダブルクリックか F2** で入る。
 * 編集中は ↑↓←→ が入力欄の操作になるので、セルの移動は選択モードのときだけ。
 */
const gridEditing = ref<{ row: number; col: number } | null>(null)
/** 編集を始めたときの値（Esc で取り消すため）。 */
let gridEditOriginal: string | null = null

/** セルの選択・範囲の見た目（Excel 風）。 */
function gridCellClasses(row: number, col: number): Record<string, boolean> {
  const cursor = gridCursor.value
  if (cursor === null) {
    return {}
  }
  const range = gridRange()
  const inRange = row >= range.top && row <= range.bottom && col >= range.left && col <= range.right
  if (!inRange) {
    return {}
  }
  const single = range.top === range.bottom && range.left === range.right
  return {
    'is-selected': single,
    'is-range': !single,
    'is-range-top': row === range.top,
    'is-range-bottom': row === range.bottom,
    'is-range-left': col === range.left,
    'is-range-right': col === range.right
  }
}

/** 選択中の範囲（アンカーとカーソルの矩形）。 */
function gridRange(): { top: number; bottom: number; left: number; right: number } {
  const a = gridAnchor.value ?? gridCursor.value ?? { row: 0, col: 0 }
  const b = gridCursor.value ?? a
  return {
    top: Math.min(a.row, b.row),
    bottom: Math.max(a.row, b.row),
    left: Math.min(a.col, b.col),
    right: Math.max(a.col, b.col)
  }
}

/** クリックしたセルを選び、ドラッグ開始（Shift で範囲を拡張）。 */
function startGridSelection(row: number, col: number, event: MouseEvent): void {
  gridDragging.value = true
  gridCursor.value = { row, col }
  if (!event.shiftKey || gridAnchor.value === null) {
    gridAnchor.value = { row, col }
  }
}

/** そのセル 1 つを選ぶ（範囲は解除）。 */
function setGridCursor(row: number, col: number): void {
  gridCursor.value = { row, col }
  gridAnchor.value = { row, col }
}

/** 編集モードのセルか。 */
function isGridEditing(row: number, col: number): boolean {
  const editing = gridEditing.value
  return editing !== null && editing.row === row && editing.col === col
}

/** セルの値（画面に出す前の形）。 */
function gridCellRawValue(row: GridRow, key: GridColumnKey): string {
  if (key === 'title') return row.title
  if (key === 'priority') return row.priority
  if (key === 'dueDate') return row.dueDate
  return row.memo
}

/** セルの値（画面に出す前の形）を書き戻す。 */
function setGridCellRawValue(row: GridRow, key: GridColumnKey, value: string): void {
  if (key === 'title') row.title = value
  else if (key === 'priority') row.priority = value as TodoPriorityCode
  else if (key === 'dueDate') row.dueDate = value
  else row.memo = value
}

/**
 * ダブルクリック / F2: そのセルを入力できる状態にする。
 * 入力欄はそのセルにだけ出し、フォーカスを当てる（文字は全選択＝そのまま打ち替えられる）。
 */
async function startGridEdit(row: number, col: number): Promise<void> {
  const target = gridRows.value[row]
  const column: { key: GridColumnKey } | undefined = GRID_COLUMNS[col]
  if (target === undefined || column === undefined) {
    return
  }
  gridEditOriginal = gridCellRawValue(target, column.key)
  gridEditing.value = { row, col }
  setGridCursor(row, col)
  await nextTick()
  const editor = gridTable.value
    ?.querySelector<HTMLElement>(`td[data-cell="${row}-${col}"] input, td[data-cell="${row}-${col}"] select`) ?? null
  editor?.focus()
  if (editor instanceof HTMLInputElement && editor.type === 'text') {
    editor.select()
  }
}

/**
 * 入力を確定する（値は入力中にすでに入っている）。
 * 同じセルをクリックし直したときや、別のセルへ移ったときにも呼ばれる。
 */
function commitGridEdit(): void {
  if (gridEditing.value === null) {
    return
  }
  gridEditing.value = null
  gridEditOriginal = null
}

/** 入力を取り消して、編集を始めたときの値に戻す（Esc）。 */
function cancelGridEdit(): void {
  const editing = gridEditing.value
  if (editing === null) {
    return
  }
  const target = gridRows.value[editing.row]
  const column: { key: GridColumnKey } | undefined = GRID_COLUMNS[editing.col]
  if (target !== undefined && column !== undefined && gridEditOriginal !== null) {
    setGridCellRawValue(target, column.key, gridEditOriginal)
  }
  gridEditing.value = null
  gridEditOriginal = null
}

/** 入力中のキー操作（Enter で確定して下へ、Esc で取り消し、F2 で確定）。 */
function onGridEditorKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter') {
    event.preventDefault()
    commitGridEdit()
    moveGridCursor(1, 0)
    focusGridTable()
  } else if (event.key === 'Escape') {
    event.preventDefault()
    cancelGridEdit()
    focusGridTable()
  } else if (event.key === 'F2') {
    event.preventDefault()
    commitGridEdit()
    focusGridTable()
  }
}

/** 選択中のセルを動かす（端では止まる）。 */
function moveGridCursor(rowDelta: number, colDelta: number): void {
  const cursor = gridCursor.value ?? { row: 0, col: 0 }
  const lastRow = Math.max(gridRows.value.length - 1, 0)
  gridCursor.value = {
    row: Math.min(Math.max(cursor.row + rowDelta, 0), lastRow),
    col: Math.min(Math.max(cursor.col + colDelta, 0), GRID_COLUMNS.length - 1)
  }
  gridAnchor.value = gridCursor.value
}

/** 方向キー（Excel と同じ向き）。 */
const GRID_ARROWS: Record<string, { row: number; col: number }> = {
  ArrowUp: { row: -1, col: 0 },
  ArrowDown: { row: 1, col: 0 },
  ArrowLeft: { row: 0, col: -1 },
  ArrowRight: { row: 0, col: 1 }
}

/**
 * グリッドのキー操作。
 * 編集中のキーは入力欄側（onGridEditorKeydown）で受けるので、ここでは選択モードのときだけ扱う。
 */
function onGridKeydown(event: KeyboardEvent): void {
  if (gridEditing.value !== null) {
    return
  }
  if (event.key === 'F2') {
    event.preventDefault()
    const cursor = gridCursor.value
    if (cursor !== null) {
      void startGridEdit(cursor.row, cursor.col)
    }
    return
  }
  if (event.key === 'Escape') {
    clearGridSelection()
    return
  }
  const arrow = GRID_ARROWS[event.key]
  if (arrow === undefined) {
    return
  }
  event.preventDefault()
  const cursor = gridCursor.value
  if (cursor === null) {
    setGridCursor(0, 0)
    return
  }
  if (event.shiftKey) {
    // Shift＋方向キーはアンカーを残して範囲を広げる
    const lastRow = Math.max(gridRows.value.length - 1, 0)
    gridCursor.value = {
      row: Math.min(Math.max(cursor.row + arrow.row, 0), lastRow),
      col: Math.min(Math.max(cursor.col + arrow.col, 0), GRID_COLUMNS.length - 1)
    }
    return
  }
  moveGridCursor(arrow.row, arrow.col)
}

/** セルをクリックしたら表にフォーカスを移す（そのまま方向キーで動かせるように）。 */
function focusGridTable(): void {
  if (gridEditing.value !== null) {
    return // 入力中のクリック（キャレット移動）でフォーカスを奪わない
  }
  gridTable.value?.focus()
}

/** ドラッグ中に通り過ぎたセルまで範囲を広げる。 */
function extendGridSelection(row: number, col: number): void {
  if (!gridDragging.value) {
    return
  }
  gridCursor.value = { row, col }
}

function clearGridSelection(): void {
  gridCursor.value = null
  gridAnchor.value = null
}

/** ダイアログを開いたときに、選択と編集モードを初期状態へ戻す。 */
function resetGridState(): void {
  clearGridSelection()
  gridEditing.value = null
  gridEditOriginal = null
  gridDragging.value = false
}

/** マウスを離したらドラッグ終了（表の外で離しても終わるように window で受ける）。 */
function endGridSelection(): void {
  gridDragging.value = false
}

/** セル 1 つの値（コピー用の文字列）。 */
function gridCellText(row: GridRow, key: GridColumnKey): string {
  switch (key) {
    case 'title':
      return row.title ?? ''
    case 'priority':
      return PRIORITY_LABELS[row.priority] ?? row.priority
    case 'dueDate':
      return row.dueDate ?? ''
    default:
      return row.memo ?? ''
  }
}

/** 選択中の範囲をタブ区切りのテキストにする（Excel に貼り付けられる形式）。 */
function gridSelectionText(): string | null {
  const cursor = gridCursor.value
  if (cursor === null) {
    return null
  }
  const range = gridRange()
  const lines: string[] = []
  for (let row = range.top; row <= range.bottom; row += 1) {
    const values: string[] = []
    for (let col = range.left; col <= range.right; col += 1) {
      const target = gridRows.value[row]
      const column: { key: GridColumnKey; label: string } | undefined = GRID_COLUMNS[col]
      values.push(target === undefined || column === undefined ? '' : gridCellText(target, column.key))
    }
    lines.push(values.join('\t'))
  }
  return lines.join('\n')
}

/**
 * Ctrl+C。セルを選んでいるときだけ横取りする
 * （入力中の文字を選んでコピーするときは、ブラウザの既定のコピーに任せる）。
 */
function copyGrid(event: ClipboardEvent): void {
  if (gridEditing.value !== null) {
    return // 入力中のコピーは入力欄の選択文字に任せる
  }
  const selection = window.getSelection()?.toString() ?? ''
  if (selection !== '') {
    return
  }
  const text = gridSelectionText()
  if (text === null || text === '') {
    return
  }
  event.clipboardData?.setData('text/plain', text)
  event.preventDefault()
  toast.success('選択したセルをコピーしました。Excel に貼り付けられます。')
}

onMounted(() => {
  window.addEventListener('mouseup', endGridSelection)
})
onBeforeUnmount(() => {
  window.removeEventListener('mouseup', endGridSelection)
  endColumnResize()
})

const isGuardian = computed(() => {
  try {
    const raw = window.sessionStorage.getItem('study21.auth.v2')
    return raw ? JSON.parse(raw).role === 'GUARDIAN' : false
  } catch {
    return false
  }
})

function todayYmd(): string {
  const now = new Date()
  return `${now.getFullYear()}-${`${now.getMonth() + 1}`.padStart(2, '0')}-${`${now.getDate()}`.padStart(2, '0')}`
}

/* ---------- 一覧 ---------- */
async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const response = await searchTodos({
      keyword: filters.keyword,
      status: filters.status,
      priority: filters.priority,
      includeDone: filters.includeDone,
      page: page.value,
      size: 20
    })
    rows.value = response.data.items
    totalElements.value = response.data.totalElements
    totalPages.value = Math.max(1, response.data.totalPages)
    counts.open = response.data.openCount
    counts.doing = response.data.doingCount
    counts.done = response.data.doneCount
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : 'TODOを取得できませんでした。'
  } finally {
    loading.value = false
  }
}

function search(): void {
  page.value = 1
  void load()
}

function reset(): void {
  filters.keyword = ''
  filters.status = ''
  filters.priority = ''
  filters.includeDone = false
  search()
}

function goto(next: number): void {
  if (next < 1 || next > totalPages.value) return
  page.value = next
  void load()
}

/* ---------- カレンダー ---------- */
async function loadCalendar(): Promise<void> {
  try {
    const response = await fetchTodoCalendar(calCursor.value.getFullYear(), calCursor.value.getMonth() + 1)
    const map = new Map<string, { openCount: number; doneCount: number }>()
    for (const cell of response.data.cells) {
      map.set(cell.dueDate, { openCount: cell.openCount, doneCount: cell.doneCount })
    }
    calCells.value = map
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : 'カレンダーを取得できませんでした。')
  }
}

interface CalendarCell {
  ymd: string | null
  dayOfMonth: number | null
  openCount: number
  doneCount: number
}

const calendarWeeks = computed<CalendarCell[][]>(() => {
  const first = new Date(calCursor.value.getFullYear(), calCursor.value.getMonth(), 1)
  const offset = (first.getDay() + 6) % 7
  const start = new Date(first.getFullYear(), first.getMonth(), 1 - offset)
  const weeks: CalendarCell[][] = []
  for (let week = 0; week < 6; week += 1) {
    const cells: CalendarCell[] = []
    for (let index = 0; index < 7; index += 1) {
      const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + week * 7 + index)
      const inMonth = date.getMonth() === first.getMonth()
      const ymd = inMonth
        ? `${date.getFullYear()}-${`${date.getMonth() + 1}`.padStart(2, '0')}-${`${date.getDate()}`.padStart(2, '0')}`
        : null
      const cell = ymd ? calCells.value.get(ymd) : undefined
      cells.push({
        ymd,
        dayOfMonth: inMonth ? date.getDate() : null,
        openCount: cell?.openCount ?? 0,
        doneCount: cell?.doneCount ?? 0
      })
    }
    weeks.push(cells)
  }
  return weeks.filter((cells, index) => index < 5 || cells.some((cell) => cell.ymd !== null))
})

const calendarLabel = computed(() =>
  `${calCursor.value.getFullYear()}年${calCursor.value.getMonth() + 1}月`)

function moveCalendar(offset: number): void {
  calCursor.value = new Date(calCursor.value.getFullYear(), calCursor.value.getMonth() + offset, 1)
  void loadCalendar()
}

function switchView(next: 'list' | 'calendar'): void {
  view.value = next
  if (next === 'calendar') {
    calCursor.value = new Date()
    void loadCalendar()
  }
}

/** カレンダーの日付クリック＝その日を期限にした新規作成。 */
function createOn(ymd: string): void {
  openCreate(ymd)
}

/* ---------- ダイアログ ---------- */
function newGridRow(): GridRow {
  gridKey += 1
  return { key: gridKey, title: '', memo: '', priority: 'NORMAL', dueDate: '' }
}

function openCreate(dueDate = ''): void {
  editingId.value = null
  editingVersion.value = null
  form.title = ''
  form.memo = ''
  form.priority = 'NORMAL'
  form.dueDate = dueDate
  form.alsoForStudent = false
  gridRows.value = [newGridRow()]
  resetGridState()
  dialogOpen.value = true
}

function openEdit(row: TodoRow): void {
  editingId.value = row.todoId
  editingVersion.value = row.version
  form.title = row.title
  form.memo = row.memo ?? ''
  form.priority = row.priority
  form.dueDate = row.dueDate ?? ''
  form.alsoForStudent = false
  gridRows.value = row.children.length > 0
    ? row.children.map((child) => {
        gridKey += 1
        return {
          key: gridKey,
          title: child.title,
          memo: child.memo ?? '',
          priority: child.priority,
          dueDate: child.dueDate ?? ''
        }
      })
    : [newGridRow()]
  resetGridState()
  dialogOpen.value = true
}

function addGridRow(): void {
  gridRows.value = [...gridRows.value, newGridRow()]
}

function removeGridRow(index: number): void {
  gridRows.value = gridRows.value.filter((_, rowIndex) => rowIndex !== index)
}

/**
 * Excel からの貼り付け。タブ区切りのテキストをそのままグリッドに流し込む
 * （列の順番は タイトル / 優先度 / 期限 / メモ）。
 */
/**
 * Excel からの貼り付け（タブ区切り）。
 * セルを選んでいれば **そのセルを起点に** 流し込み、足りない行は自動で増やす。
 * 選んでいなければ末尾に行として足す（1 行だけの表なら置き換える）。
 */
function pasteGrid(event: ClipboardEvent): void {
  const text = event.clipboardData?.getData('text/plain') ?? ''
  if (!text.includes('\t') && !text.includes('\n')) {
    return // 単一セルは通常の入力に任せる
  }
  event.preventDefault()
  const matrix: string[][] = []
  for (const line of text.split(/\r?\n/)) {
    if (line.trim() === '') continue
    matrix.push(line.split('\t'))
  }
  if (matrix.length === 0) {
    return
  }

  const cursor = gridCursor.value
  if (cursor === null) {
    // 行として足す（これまでどおり）
    const parsed: GridRow[] = matrix.map((cells) => {
      gridKey += 1
      return {
        key: gridKey,
        title: (cells[0] ?? '').trim(),
        priority: normalizePriority(cells[1]),
        dueDate: normalizeDate(cells[2]),
        memo: (cells[3] ?? '').trim()
      }
    })
    const isOnlyEmptyRow = gridRows.value.length === 1 && gridRows.value[0].title.trim() === ''
    gridRows.value = isOnlyEmptyRow ? parsed : [...gridRows.value, ...parsed]
    toast.success(`${parsed.length} 行を取り込みました。`)
    return
  }

  // 選択中のセルを左上として流し込む
  const rows = [...gridRows.value]
  matrix.forEach((cells, rowOffset) => {
    const targetIndex = cursor.row + rowOffset
    while (rows.length <= targetIndex) {
      gridKey += 1
      rows.push({ key: gridKey, title: '', memo: '', priority: 'NORMAL', dueDate: '' })
    }
    const target = rows[targetIndex]
    cells.forEach((value, colOffset) => {
      const column = GRID_COLUMNS[cursor.col + colOffset]
      if (column === undefined) {
        return
      }
      if (column.key === 'title') target.title = value.trim()
      else if (column.key === 'priority') target.priority = normalizePriority(value)
      else if (column.key === 'dueDate') target.dueDate = normalizeDate(value)
      else target.memo = value.trim()
    })
  })
  gridRows.value = rows
  toast.success(`${matrix.length} 行 × ${matrix[0]?.length ?? 0} 列を取り込みました。`)
}

function normalizePriority(value?: string): TodoPriorityCode {
  const text = (value ?? '').trim().toUpperCase()
  if (text === 'HIGH' || text === 'NORMAL' || text === 'LOW') return text
  if (text === '高' || text === 'H') return 'HIGH'
  if (text === '低' || text === 'L' || text === 'LOW') return 'LOW'
  if (text === '中' || text === 'M') return 'NORMAL'
  return 'NORMAL'
}

function normalizeDate(value?: string): string {
  const text = (value ?? '').trim()
  if (text === '') return ''
  // 2026/09/10 や 2026-9-10 も受け付ける
  const match = text.match(/^(\d{4})[/-](\d{1,2})[/-](\d{1,2})$/)
  if (match === null) return ''
  return `${match[1]}-${match[2].padStart(2, '0')}-${match[3].padStart(2, '0')}`
}

function payload(): TodoSaveRequest {
  return {
    title: form.title.trim(),
    memo: form.memo.trim() === '' ? null : form.memo.trim(),
    priority: form.priority,
    dueDate: form.dueDate === '' ? null : form.dueDate,
    children: gridRows.value
      .filter((row) => row.title.trim() !== '')
      .map((row) => ({
        title: row.title.trim(),
        memo: row.memo && row.memo.trim() !== '' ? row.memo.trim() : null,
        priority: row.priority,
        dueDate: row.dueDate && row.dueDate !== '' ? row.dueDate : null
      })),
    alsoForStudent: isGuardian.value ? form.alsoForStudent : null,
    version: editingVersion.value
  }
}

async function save(): Promise<void> {
  if (busy.value) return
  if (form.title.trim() === '') {
    toast.warning('タイトルを入力してください。')
    return
  }
  busy.value = true
  try {
    const body = payload()
    const response = editingId.value === null
      ? await createTodo(body)
      : await updateTodo(editingId.value, body)
    toast.success(response.data.message)
    dialogOpen.value = false
    await load()
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : 'TODOを保存できませんでした。')
  } finally {
    busy.value = false
  }
}

/* ---------- 行の操作 ---------- */
async function advance(row: TodoRow): Promise<void> {
  if (busy.value) return
  const next: TodoStatusCode = row.status === 'TODO' ? 'DOING' : row.status === 'DOING' ? 'DONE' : 'TODO'
  busy.value = true
  try {
    // 一覧が持っているバージョンを渡す（別の画面で先に変わっていたら弾く）
    const response = await changeTodoStatus(row.todoId, next, row.version)
    toast.success(response.data.message)
    await load()
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '状態を変更できませんでした。')
  } finally {
    busy.value = false
  }
}

async function remove(row: TodoRow): Promise<void> {
  if (busy.value) return
  if (!window.confirm(`「${row.title}」を削除します。よろしいですか？`)) return
  busy.value = true
  try {
    const response = await deleteTodo(row.todoId)
    toast.success(response.data.message)
    await load()
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : 'TODOを削除できませんでした。')
  } finally {
    busy.value = false
  }
}

/** 期限の色（親タスク・子タスク共通）。 */
function dueClassOf(dueDate: string | null, status: TodoStatusCode): string {
  if (dueDate === null || status === 'DONE') return ''
  const today = todayYmd()
  if (dueDate < today) return 'todo-due--overdue'
  if (dueDate === today) return 'todo-due--today'
  return ''
}

function dueClass(row: TodoRow): string {
  return dueClassOf(row.dueDate, row.status)
}

function advanceLabel(row: TodoRow): string {
  return statusAdvanceLabel(row.status)
}

/** 子タスクの状態ボタンの説明（子タスクであることが分かるようにする）。 */
function childAdvanceLabel(child: TodoRow): string {
  return `子タスク：${statusAdvanceLabel(child.status)}`
}

function statusAdvanceLabel(status: TodoStatusCode): string {
  if (status === 'TODO') return '進行中にする'
  if (status === 'DOING') return '完了にする'
  return '未着手に戻す'
}

/** 状態を進めるアイコン（未着手→進行中は再生、進行中→完了はチェック、完了→未着手は戻す）。 */
function advanceIcon(row: TodoRow): string {
  return advanceIconOf(row.status)
}

function advanceIconOf(status: TodoStatusCode): string {
  if (status === 'TODO') return 'play'
  if (status === 'DOING') return 'check-circle'
  return 'rotate'
}

function advanceIconClass(row: TodoRow): string {
  return advanceIconClassOf(row.status)
}

function advanceIconClassOf(status: TodoStatusCode): string {
  if (status === 'TODO') return ''
  if (status === 'DOING') return 'icon--success'
  return 'icon--reject'
}

/** 子タスクの状態を 1 つ進める（一覧から直接）。 */
async function advanceChild(child: TodoRow): Promise<void> {
  if (busy.value) return
  const next: TodoStatusCode = child.status === 'TODO' ? 'DOING' : child.status === 'DOING' ? 'DONE' : 'TODO'
  busy.value = true
  try {
    const response = await changeTodoStatus(child.todoId, next, child.version)
    toast.success(response.data.message)
    await load()
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '子タスクの状態を変更できませんでした。')
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="todo-page">
    <p v-if="error" class="alert alert--danger">{{ error }}</p>

    <!-- 上部: タブ（一覧 / カレンダー）＋カウンタ＋新規 -->
    <div class="todo-head">
      <div class="tabs" role="tablist">
        <button
          type="button" class="tabs__tab" :class="{ 'is-active': view === 'list' }"
          role="tab" :aria-selected="view === 'list'" data-tab="list" @click="switchView('list')"
        >
          一覧
        </button>
        <button
          type="button" class="tabs__tab" :class="{ 'is-active': view === 'calendar' }"
          role="tab" :aria-selected="view === 'calendar'" data-tab="calendar" @click="switchView('calendar')"
        >
          カレンダー
        </button>
      </div>

      <span class="todo-head__spacer" />

      <span class="todo-counter todo-counter--open">
        <span class="todo-counter__value">{{ counts.open }}</span>
        <span class="todo-counter__label">未着手</span>
      </span>
      <span class="todo-counter todo-counter--doing">
        <span class="todo-counter__value">{{ counts.doing }}</span>
        <span class="todo-counter__label">進行中</span>
      </span>
      <span class="todo-counter todo-counter--done">
        <span class="todo-counter__value">{{ counts.done }}</span>
        <span class="todo-counter__label">完了</span>
      </span>

      <button type="button" class="btn btn--primary" @click="openCreate()">
        <AppIcon name="plus" size="sm" /> 新規
      </button>
    </div>

    <!-- 一覧 -->
    <div v-if="view === 'list'" class="card table-section">
      <div class="table-section__head">
        <h3 class="table-section__title">TODO一覧</h3>
        <span class="table-section__meta">全 {{ totalElements }} 件</span>

        <!--
          検索条件は見出し行にまとめる（バッチ実行履歴と同じ形）。
          ラベルは置かず、aria-label と placeholder で意味を持たせる。選択は変更で即検索する。
        -->
        <span class="todo-filters">
          <input
            v-model="filters.keyword" class="input" type="search"
            placeholder="タイトル / メモ" aria-label="キーワード" @keyup.enter="search"
          >
          <select v-model="filters.status" class="select" aria-label="状態" @change="search">
            <option value="">すべての状態</option>
            <option value="TODO">未着手</option>
            <option value="DOING">進行中</option>
            <option value="DONE">完了</option>
          </select>
          <select v-model="filters.priority" class="select" aria-label="優先度" @change="search">
            <option value="">すべての優先度</option>
            <option value="HIGH">高</option>
            <option value="NORMAL">中</option>
            <option value="LOW">低</option>
          </select>
          <label class="todo-filters__check">
            <input v-model="filters.includeDone" type="checkbox" aria-label="完了も表示" @change="search"> 完了も表示
          </label>
          <button type="button" class="btn btn--primary btn--sm" :disabled="loading" @click="search">
            <AppIcon name="search" size="sm" /> 検索
          </button>
          <button type="button" class="btn btn--secondary btn--sm" :disabled="loading" @click="reset">
            <AppIcon name="rotate" size="sm" /> リセット
          </button>
        </span>

        <button
          v-if="hasExpandableRows"
          type="button" class="btn btn--secondary btn--sm" data-action="toggle-all-children"
          :aria-expanded="allExpanded" @click="toggleAllRows"
        >
          <AppIcon :name="allExpanded ? 'chevron-down' : 'chevron-right'" size="sm" />
          {{ allExpanded ? 'すべて折りたたむ' : 'すべて展開' }}
        </button>
      </div>

      <p v-if="loading" class="net-page__loading">読み込んでいます...</p>
      <p v-else-if="rows.length === 0" class="net-page__empty">該当するTODOがありません。</p>

      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th class="col-actions">操作</th>
              <th>タイトル</th>
              <th class="align-center">優先度</th>
              <th>期限</th>
              <th>メモ</th>
              <th class="align-center">状態</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="row in rows" :key="row.todoId">
              <tr :data-todo-id="row.todoId" :class="{ 'todo-row--expanded': isExpanded(row.todoId) }">
                <td class="row-actions">
                  <button
                    type="button" class="btn btn--icon btn--sm"
                    :title="advanceLabel(row)" :aria-label="advanceLabel(row)"
                    :data-advance="row.todoId" :disabled="busy" @click="advance(row)"
                  >
                    <AppIcon :name="advanceIcon(row)" size="sm" :class="advanceIconClass(row)" />
                  </button>
                  <button type="button" class="btn btn--icon btn--sm" title="編集" aria-label="編集" @click="openEdit(row)">
                    <AppIcon name="edit" size="sm" class="icon--edit" />
                  </button>
                  <button
                    type="button" class="btn btn--icon btn--sm" title="削除" aria-label="削除"
                    :disabled="busy" @click="remove(row)"
                  >
                    <AppIcon name="trash" size="sm" class="icon--danger" />
                  </button>
                </td>
                <td>
                  <span class="todo-title-cell">
                    <span class="todo-title-line">
                      <!--
                        子タスクがある行は、タイトル自身が開閉ボタンになる。
                        表示は「タイトル 完了/全体」（例: ハリポタ 0/17）。
                      -->
                      <button
                        v-if="row.children.length > 0"
                        type="button" class="todo-toggle"
                        :aria-expanded="isExpanded(row.todoId)"
                        :data-toggle="row.todoId"
                        :title="isExpanded(row.todoId) ? '子タスクを折りたたむ' : '子タスクを展開する'"
                        @click="toggleRow(row.todoId)"
                      >
                        <AppIcon
                          :name="isExpanded(row.todoId) ? 'chevron-down' : 'chevron-right'"
                          size="sm" class="todo-toggle__icon"
                        />
                        <span class="todo-toggle__title">{{ row.title }}</span>
                        <span class="todo-toggle__count">{{ row.doneChildCount }}/{{ row.childCount }}</span>
                      </button>
                      <span v-else class="cell-strong">{{ row.title }}</span>
                    </span>
                  </span>
                </td>
                <td class="align-center">
                  <span class="badge" :class="row.priority === 'HIGH' ? 'badge--danger' : row.priority === 'LOW' ? 'badge--neutral' : 'badge--info'">
                    {{ PRIORITY_LABELS[row.priority] }}
                  </span>
                </td>
                <td class="todo-due" :class="dueClass(row)">{{ row.dueDate ? formatIsoDate(row.dueDate) : '—' }}</td>
                <td class="cell-muted">{{ row.memo ?? '—' }}</td>
                <td class="align-center">
                  <span class="badge" :class="STATUS_BADGE[row.status]">{{ STATUS_LABELS[row.status] }}</span>
                </td>
              </tr>

              <!-- 展開したときだけ、子タスクを同じ列に並べて出す -->
              <template v-if="isExpanded(row.todoId)">
                <tr
                  v-for="child in row.children"
                  :key="child.todoId"
                  class="todo-child-row"
                  :data-child-id="child.todoId"
                  :data-parent-id="row.todoId"
                >
                  <td class="row-actions">
                    <!-- 一覧から子タスクの状態も進められる（親と同じアイコンの動き） -->
                    <button
                      type="button" class="btn btn--icon btn--sm"
                      :title="childAdvanceLabel(child)" :aria-label="childAdvanceLabel(child)"
                      :data-child-advance="child.todoId" :disabled="busy"
                      @click="advanceChild(child)"
                    >
                      <AppIcon :name="advanceIconOf(child.status)" size="sm" :class="advanceIconClassOf(child.status)" />
                    </button>
                  </td>
                  <td class="todo-child-row__title">
                    <span :class="{ 'todo-child__done': child.status === 'DONE' }">{{ child.title }}</span>
                  </td>
                  <td class="align-center">
                    <span
                      class="badge"
                      :class="child.priority === 'HIGH' ? 'badge--danger' : child.priority === 'LOW' ? 'badge--neutral' : 'badge--info'"
                    >{{ PRIORITY_LABELS[child.priority] }}</span>
                  </td>
                  <td class="todo-due" :class="dueClassOf(child.dueDate, child.status)">
                    {{ child.dueDate ? formatIsoDate(child.dueDate) : '—' }}
                  </td>
                  <td class="cell-muted">{{ child.memo ?? '—' }}</td>
                  <td class="align-center">
                    <span class="badge" :class="STATUS_BADGE[child.status]">{{ STATUS_LABELS[child.status] }}</span>
                  </td>
                </tr>
              </template>
            </template>
          </tbody>
        </table>
      </div>

      <div v-if="!loading && totalPages > 1" class="pagination">
        <span class="pagination__info">全 {{ totalElements }} 件（{{ page }} / {{ totalPages }} ページ）</span>
        <div class="pagination__pages">
          <button type="button" class="page-btn" :disabled="page <= 1" @click="goto(page - 1)">‹</button>
          <button type="button" class="page-btn" :disabled="page >= totalPages" @click="goto(page + 1)">›</button>
        </div>
      </div>
    </div>

    <!-- カレンダー（期限日ごとの件数） -->
    <div v-if="view === 'calendar'" class="card table-section">
      <div class="table-section__head">
        <h3 class="table-section__title">期限カレンダー</h3>
        <span class="table-section__meta">{{ calendarLabel }}</span>
        <span class="todo-view-switch">
          <button type="button" class="btn btn--secondary btn--sm" aria-label="前の月" @click="moveCalendar(-1)">
            <AppIcon name="chevron-left" size="sm" />
          </button>
          <button type="button" class="btn btn--secondary btn--sm" aria-label="次の月" @click="moveCalendar(1)">
            <AppIcon name="chevron-right" size="sm" />
          </button>
        </span>
      </div>

      <div class="todo-calendar__head">
        <span v-for="label in WEEKDAYS" :key="label">{{ label }}</span>
      </div>
      <div v-for="(cells, weekIndex) in calendarWeeks" :key="weekIndex" class="todo-calendar__week">
        <template v-for="(cell, cellIndex) in cells" :key="`${weekIndex}-${cellIndex}`">
          <button
            v-if="cell.ymd"
            type="button" class="todo-day" :class="{ 'todo-day--today': cell.ymd === todayYmd() }"
            :data-due-date="cell.ymd" @click="createOn(cell.ymd)"
          >
            <span class="todo-day__num">{{ cell.dayOfMonth }}</span>
            <span class="todo-day__badges">
              <span v-if="cell.openCount > 0" class="badge badge--warning">{{ cell.openCount }}</span>
              <span v-if="cell.doneCount > 0" class="badge badge--success">{{ cell.doneCount }}</span>
            </span>
          </button>
          <span v-else class="todo-day todo-day--empty" />
        </template>
      </div>
      <p class="dr-legend">
        <span>バッジ＝その日が期限の件数（橙＝未完了・緑＝完了）</span>
        <span>日をクリックすると、その日を期限にした TODO を作れます</span>
      </p>
    </div>

    <!-- 新規／編集ダイアログ -->
    <div v-if="dialogOpen" class="overlay">
      <section class="dialog dialog--lg" role="dialog" aria-modal="true" aria-labelledby="todoDialogTitle">
        <header class="dialog__head">
          <h2 id="todoDialogTitle" class="dialog__title">
            {{ editingId === null ? 'TODOの新規登録' : 'TODOの編集' }}
          </h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="dialogOpen = false">
            <AppIcon name="x" size="sm" />
          </button>
        </header>

        <div class="dialog__body">
          <!-- タイトルとメモは 1 行まるごと使う（優先度・期限と横並びにしない） -->
          <div class="todo-form">
            <label class="todo-field todo-field--full">
              <span class="todo-field__label">タイトル</span>
              <input id="todoTitle" v-model="form.title" class="input" type="text" placeholder="例: 数学の宿題">
            </label>
            <label class="todo-field todo-field--full">
              <span class="todo-field__label">メモ</span>
              <input v-model="form.memo" class="input" type="text" placeholder="補足（任意）">
            </label>
            <label class="todo-field">
              <span class="todo-field__label">優先度</span>
              <select v-model="form.priority" class="select">
                <option value="HIGH">高</option>
                <option value="NORMAL">中</option>
                <option value="LOW">低</option>
              </select>
            </label>
            <label class="todo-field">
              <span class="todo-field__label">期限</span>
              <input v-model="form.dueDate" class="input" type="date">
            </label>
          </div>

          <!-- 子タスク: Excel 風グリッド（見出しは上の項目と同じ左端に揃える） -->
          <div class="todo-grid__head">
            <h3 class="table-section__title">子タスク</h3>
            <span class="todo-view-switch">
              <button type="button" class="btn btn--secondary btn--sm" @click="addGridRow">
                <AppIcon name="plus" size="sm" /> 行追加
              </button>
            </span>
          </div>

          <!--
            子タスクは表計算のように入力する（IRJ の functionManagement.jsp の
            「入力項目定義 / 出力項目定義」と同じ作り）:
              ・セルをクリックすると選ばれる（選択中のセルは緑）。クリックでは入力欄は出ない
              ・ダブルクリック / F2 で入力、Enter で確定して下へ、Esc で取り消し
              ・↑↓←→ で選択中のセルを移動（編集中は入力欄の操作）
              ・ドラッグで範囲選択、Ctrl+C でタブ区切りのテキストとしてコピー（Excel に貼れる）
              ・Excel からコピーしたタブ区切りは、選択中のセルを起点に貼り付く（行は自動で増える）
          -->
          <table
            ref="gridTable" class="todo-grid" tabindex="0" aria-label="子タスク"
            @copy="copyGrid" @paste="pasteGrid" @keydown="onGridKeydown"
          >
            <!-- 列の幅（％）。ドラッグで変えられる（合計は 100% のまま） -->
            <colgroup>
              <!-- 左の 2 列（× と NO）は固定。残りを 4 列で分け合う -->
              <col style="width: 5%">
              <col style="width: 6%">
              <col
                v-for="(column, colIndex) in GRID_COLUMNS" :key="column.key"
                :style="{ width: `${gridColumnWidths[colIndex]}%` }"
              >
            </colgroup>
            <thead>
              <tr>
                <th class="todo-grid__remove-head" />
                <th class="todo-grid__no-head">NO</th>
                <th
                  v-for="(column, colIndex) in GRID_COLUMNS" :key="column.key"
                  :data-col="column.key" :style="{ position: 'relative' }"
                >
                  {{ column.label }}
                  <!-- 右端をつまんで列の幅を変える（最後の列は左の境界で変える） -->
                  <span
                    v-if="colIndex < GRID_COLUMNS.length - 1"
                    class="todo-grid__resizer" role="separator" aria-orientation="vertical" tabindex="0"
                    :aria-label="`${column.label} の幅を変える`"
                    :data-col-resizer="column.key"
                    :title="`ドラッグで「${column.label}」の幅を変えます（ダブルクリックで既定に戻す）`"
                    @mousedown.stop.prevent="startColumnResize(colIndex, $event)"
                    @dblclick.stop="resetColumnWidths()"
                    @keydown.stop="onColumnResizeKeydown(colIndex, $event)"
                  />
                </th>
              </tr>
            </thead>
            <tbody data-testid="todo-grid-body">
              <tr v-for="(row, index) in gridRows" :key="row.key">
                <td class="todo-grid__remove">
                  <button type="button" class="btn btn--icon btn--sm" :aria-label="`${index + 1} 行目を削除`" @click="removeGridRow(index)">
                    <AppIcon name="x" size="sm" class="icon--danger" />
                  </button>
                </td>
                <td class="todo-grid__no">{{ index + 1 }}</td>
                <!--
                  セルは「選ぶ」と「入力する」を分ける（Excel と同じ）:
                    ・クリック＝選ぶだけ（入力欄は出ない）
                    ・ダブルクリック / F2＝入力欄が出る
                    ・選択中は ↑↓←→ で隣のセルへ移動
                -->
                <td
                  v-for="(column, colIndex) in GRID_COLUMNS" :key="column.key"
                  :data-col="column.key"
                  :data-cell="`${index}-${colIndex}`"
                  :data-editing="isGridEditing(index, colIndex) ? 'true' : 'false'"
                  :class="[gridCellClasses(index, colIndex), { 'is-editing': isGridEditing(index, colIndex) }]"
                  @mousedown="startGridSelection(index, colIndex, $event)"
                  @mouseenter="extendGridSelection(index, colIndex)"
                  @click="focusGridTable()"
                  @dblclick="startGridEdit(index, colIndex)"
                >
                  <template v-if="isGridEditing(index, colIndex)">
                    <select
                      v-if="column.key === 'priority'" v-model="row.priority"
                      aria-label="子タスク 優先度" @keydown="onGridEditorKeydown" @blur="commitGridEdit"
                    >
                      <option value="HIGH">高</option>
                      <option value="NORMAL">中</option>
                      <option value="LOW">低</option>
                    </select>
                    <input
                      v-else-if="column.key === 'dueDate'" v-model="row.dueDate" type="date"
                      aria-label="子タスク 期限" @keydown="onGridEditorKeydown" @blur="commitGridEdit"
                    >
                    <input
                      v-else-if="column.key === 'memo'" v-model="row.memo" type="text"
                      aria-label="子タスク メモ" @keydown="onGridEditorKeydown" @blur="commitGridEdit"
                    >
                    <input
                      v-else v-model="row.title" type="text"
                      aria-label="子タスク タイトル" @keydown="onGridEditorKeydown" @blur="commitGridEdit"
                    >
                  </template>
                  <span v-else class="todo-grid__value">{{ gridCellText(row, column.key) }}</span>
                </td>
              </tr>
            </tbody>
          </table>
          <p class="todo-grid__hint">
            セルをクリックすると選ばれ、↑↓←→ で隣のセルへ移れます。
            <strong>ダブルクリックか F2</strong> で入力、Enter で確定して下へ、Esc で取り消し。
            Excel から範囲をコピーして、この表にそのまま貼り付けられます
            （列の順番: タイトル / 優先度 / 期限 / メモ。空行は無視します）。
          </p>

          <label v-if="isGuardian" class="filter-item" style="margin-top: var(--sp-4)">
            <input v-model="form.alsoForStudent" type="checkbox">
            <span class="filter-item__label">お子さまの TODO にも登録する</span>
          </label>
        </div>

        <footer class="dialog__foot">
          <button type="button" class="btn btn--secondary" @click="dialogOpen = false">キャンセル</button>
          <button type="button" class="btn btn--primary" :disabled="busy" @click="save">
            <AppIcon name="check" size="sm" /> 保存
          </button>
        </footer>
      </section>
    </div>
  </div>
</template>
