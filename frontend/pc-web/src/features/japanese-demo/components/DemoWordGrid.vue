<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import '@/features/japanese-demo/japanese-demo.css'

/**
 * Excel 風の入力表（1 列＝単語）。
 *
 * TODO の新規登録にある「子タスク」の表と同じ作りにする:
 *  ・セルは**選ぶ**と**入力する**を分ける（クリックでは入力欄を出さない）
 *  ・ダブルクリック / F2 で入力、Enter で確定して下へ、Esc で取り消し
 *  ・↑↓←→ で選ぶセルを移動（Shift＋方向キーで範囲）
 *  ・ドラッグで範囲、Ctrl+C でタブ区切りのコピー（Excel に貼れる）
 *  ・Excel から貼り付けると、**選んでいるセルを起点に**流し込み、足りない行は自動で増える
 *  ・列の幅は見出しの右端をつまんで変えられる（ダブルクリックで既定に戻す）
 *
 * 値は親が持ち、変更のたびに `update:values` で返す（この部品は表示と操作だけを受け持つ）。
 */

const props = defineProps<{
  /** 表の値（1 行 1 語） */
  values: string[]
  /** 貼り付け・行追加で増やせる行数の上限 */
  maxRows?: number
}>()

const emit = defineEmits<{
  'update:values': [values: string[]]
  /** 取り込んだ行数を知らせる（貼り付け・行追加） */
  notice: [message: string]
}>()

const table = ref<HTMLTableElement | null>(null)
const limit = computed(() => props.maxRows ?? 500)

/* ---------- 選択と編集（Excel と同じ 2 つのモード） ---------- */

const cursor = ref<{ row: number; col: number } | null>(null)
const anchor = ref<{ row: number; col: number } | null>(null)
const dragging = ref(false)
const editing = ref<{ row: number; col: number } | null>(null)
/** 編集を始めたときの値（Esc で取り消すため）。 */
let editOriginal = ''

function isEditing(row: number, col: number): boolean {
  const target = editing.value
  return target !== null && target.row === row && target.col === col
}

/** 選択中の範囲（アンカーとカーソルの矩形）。 */
function range(): { top: number; bottom: number; left: number; right: number } {
  const a = anchor.value ?? cursor.value ?? { row: 0, col: 0 }
  const b = cursor.value ?? a
  return {
    top: Math.min(a.row, b.row),
    bottom: Math.max(a.row, b.row),
    left: Math.min(a.col, b.col),
    right: Math.max(a.col, b.col)
  }
}

/** セルの見た目（Excel 風の緑）。 */
function cellClasses(row: number, col: number): Record<string, boolean> {
  if (cursor.value === null) {
    return {}
  }
  const box = range()
  if (row < box.top || row > box.bottom || col < box.left || col > box.right) {
    return {}
  }
  const single = box.top === box.bottom && box.left === box.right
  return {
    'is-selected': single,
    'is-range': !single,
    'is-range-top': row === box.top,
    'is-range-bottom': row === box.bottom,
    'is-range-left': col === box.left,
    'is-range-right': col === box.right
  }
}

function setCursor(row: number, col: number): void {
  cursor.value = { row, col }
  anchor.value = { row, col }
}

function clearSelection(): void {
  cursor.value = null
  anchor.value = null
}

/** クリックで選び、ドラッグ開始（Shift で範囲を広げる）。 */
function startSelection(row: number, col: number, event: MouseEvent): void {
  if (editing.value !== null) {
    commitEdit()
  }
  dragging.value = true
  cursor.value = { row, col }
  if (!event.shiftKey || anchor.value === null) {
    anchor.value = { row, col }
  }
}

/** ドラッグ中に通り過ぎたセルまで範囲を広げる。 */
function extendSelection(row: number, col: number): void {
  if (dragging.value) {
    cursor.value = { row, col }
  }
}

function endSelection(): void {
  dragging.value = false
}

function focusTable(): void {
  if (editing.value !== null) {
    return // 入力中のクリックでフォーカスを奪わない
  }
  table.value?.focus()
}

/* ---------- 値の書き換え ---------- */

function writeValues(next: string[]): void {
  emit('update:values', next)
}

function setCell(row: number, value: string): void {
  const next = [...props.values]
  while (next.length <= row) {
    next.push('')
  }
  next[row] = value
  writeValues(next)
}

/** ダブルクリック / F2: そのセルを入力できる状態にする。 */
async function startEdit(row: number, col: number): Promise<void> {
  if (row < 0 || col !== 0) {
    return
  }
  editOriginal = props.values[row] ?? ''
  editing.value = { row, col }
  setCursor(row, col)
  await nextTick()
  const editor = table.value?.querySelector<HTMLInputElement>(`td[data-cell="${row}-0"] input`) ?? null
  editor?.focus()
  editor?.select()
}

/** 入力を確定する（値は入力中にすでに入っている）。 */
function commitEdit(): void {
  if (editing.value === null) {
    return
  }
  editing.value = null
}

/** 入力を取り消して、編集を始めたときの値に戻す（Esc）。 */
function cancelEdit(): void {
  const target = editing.value
  if (target === null) {
    return
  }
  setCell(target.row, editOriginal)
  editing.value = null
}

function onEditorKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter') {
    event.preventDefault()
    commitEdit()
    moveCursor(1, 0)
    focusTable()
  } else if (event.key === 'Escape') {
    event.preventDefault()
    cancelEdit()
    focusTable()
  } else if (event.key === 'F2') {
    event.preventDefault()
    commitEdit()
    focusTable()
  }
}

/* ---------- 選択の移動（矢印キー） ---------- */

const ARROWS: Record<string, { row: number; col: number }> = {
  ArrowUp: { row: -1, col: 0 },
  ArrowDown: { row: 1, col: 0 },
  ArrowLeft: { row: 0, col: -1 },
  ArrowRight: { row: 0, col: 1 }
}

function moveCursor(rowDelta: number, colDelta: number): void {
  const current = cursor.value ?? { row: 0, col: 0 }
  const lastRow = Math.max(props.values.length - 1, 0)
  cursor.value = {
    row: Math.min(Math.max(current.row + rowDelta, 0), lastRow),
    col: Math.min(Math.max(current.col + colDelta, 0), 0)
  }
  anchor.value = cursor.value
}

function onKeydown(event: KeyboardEvent): void {
  if (editing.value !== null) {
    return // 入力中のキーは入力欄側で受ける
  }
  if (event.key === 'F2') {
    event.preventDefault()
    const at = cursor.value
    if (at !== null) {
      void startEdit(at.row, at.col)
    }
    return
  }
  if (event.key === 'Escape') {
    clearSelection()
    return
  }
  if (event.key === 'Delete' || event.key === 'Backspace') {
    const at = cursor.value
    if (at !== null && props.values[at.row] !== undefined && props.values[at.row] !== '') {
      event.preventDefault()
      setCell(at.row, '')
    }
    return
  }
  const arrow = ARROWS[event.key]
  if (arrow === undefined) {
    return
  }
  event.preventDefault()
  const current = cursor.value
  if (current === null) {
    setCursor(0, 0)
    return
  }
  if (event.shiftKey) {
    // Shift＋方向キーはアンカーを残して範囲を広げる
    const lastRow = Math.max(props.values.length - 1, 0)
    cursor.value = {
      row: Math.min(Math.max(current.row + arrow.row, 0), lastRow),
      col: Math.min(Math.max(current.col + arrow.col, 0), 0)
    }
    return
  }
  moveCursor(arrow.row, arrow.col)
}

/* ---------- コピー（Excel に貼れる形） ---------- */

function selectionText(): string {
  const box = range()
  const lines: string[] = []
  for (let row = box.top; row <= box.bottom; row += 1) {
    const cells: string[] = []
    for (let col = box.left; col <= box.right; col += 1) {
      cells.push(String(props.values[row] ?? ''))
    }
    lines.push(cells.join('\t'))
  }
  return lines.join('\n')
}

function onCopy(event: ClipboardEvent): void {
  if (editing.value !== null) {
    return // 入力中のコピーは入力欄の選択文字に任せる
  }
  if ((window.getSelection()?.toString() ?? '') !== '') {
    return
  }
  const text = cursor.value === null ? '' : selectionText()
  if (text === '') {
    return
  }
  event.clipboardData?.setData('text/plain', text)
  event.preventDefault()
}

/* ---------- 貼り付け（Excel から。選んだセルを起点に流し込む） ---------- */

function onPaste(event: ClipboardEvent): void {
  const text = event.clipboardData?.getData('text/plain') ?? ''
  if (text.trim() === '') {
    return
  }
  // ダイアログの中の他の入力欄に貼るときは邪魔しない
  const target = event.target as HTMLElement | null
  if (target !== null && target.closest('[data-demo-word-grid]') === null && target.tagName !== 'DIV') {
    if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT') {
      return
    }
  }
  event.preventDefault()
  // タブ区切り（Excel のコピー）でも、改行だけの列でも受ける。1 列目だけを使う。
  const rows = text
    .split(/\r?\n/)
    .map((line) => (line.split('\t')[0] ?? '').trim())
    .filter((value) => value !== '')
  if (rows.length === 0) {
    return
  }
  const next = [...props.values]
  const at = cursor.value
  if (at === null) {
    // 選んでいないときは末尾に足す（空の表なら置き換える）
    const onlyEmpty = next.length === 0 || next.every((value) => value.trim() === '')
    const merged = onlyEmpty ? rows : [...next.filter((value) => value.trim() !== ''), ...rows]
    writeValues(merged.slice(0, limit.value))
  } else {
    // 選んでいるセルを左上として流し込み、足りない行は増やす
    rows.forEach((value, offset) => {
      const index = at.row + offset
      while (next.length <= index) {
        next.push('')
      }
      next[index] = value
    })
    writeValues(next.slice(0, limit.value))
    setCursor(at.row + rows.length - 1, 0)
  }
  emit('notice', `${rows.length} 件の単語を取り込みました。`)
}

/* ---------- 行の増減 ---------- */

function addRow(): void {
  if (props.values.length >= limit.value) {
    return
  }
  writeValues([...props.values, ''])
  void nextTick(() => {
    const row = props.values.length - 1
    setCursor(row, 0)
    void startEdit(row, 0)
  })
}

function removeRow(row: number): void {
  if (editing.value !== null && editing.value.row === row) {
    editing.value = null
  }
  writeValues(props.values.filter((_, index) => index !== row))
  const lastRow = Math.max(props.values.length - 2, 0)
  setCursor(Math.min(row, lastRow), 0)
}

/* ---------- 列の幅（見出しの右端をつまんで変える） ---------- */

/**
 * 単語の列の幅（**px**。画面の幅に左右されない）。
 *
 * **× と NO は固定**（TODO の表と同じで、つまみも置かない）。単語だけを px で持ち、
 * 残りは表の幅が吸収するので、単語の幅を変えても × と NO は動かない。
 */
const MIN_WIDTH = 120
const DEFAULT_WIDTH = 520
const width = ref(DEFAULT_WIDTH)

function resetWidth() {
  width.value = DEFAULT_WIDTH
}

/** 単語の列の幅を変える（× と NO は固定なので動かない）。 */
function startResize(event: MouseEvent): void {
  const element = table.value
  if (element === null) {
    return
  }
  const startX = event.clientX
  const startWidth = width.value
  const tableWidth = element.getBoundingClientRect().width
  const move = (moveEvent: MouseEvent): void => {
    // 単語の列だけを広げる／狭める（× と NO は固定なので触らない）
    const max = tableWidth > 0 ? Math.max(MIN_WIDTH, tableWidth - 140) : 2000
    width.value = Math.min(Math.max(startWidth + (moveEvent.clientX - startX), MIN_WIDTH), max)
  }
  const up = (): void => {
    window.removeEventListener('mousemove', move)
    window.removeEventListener('mouseup', up)
  }
  window.addEventListener('mousemove', move)
  window.addEventListener('mouseup', up)
}

/** つまみのキーボード操作（←→ で 1%、Shift で 5%）。 */
function onResizeKeydown(event: KeyboardEvent): void {
  const step = event.shiftKey ? 40 : 10
  const delta = event.key === 'ArrowLeft' ? -step : event.key === 'ArrowRight' ? step : 0
  if (delta === 0) {
    return
  }
  event.preventDefault()
  const tableWidth = table.value?.getBoundingClientRect().width ?? 0
  const max = tableWidth > 0 ? Math.max(MIN_WIDTH, tableWidth - 140) : 2000
  width.value = Math.min(Math.max(width.value + delta, MIN_WIDTH), max)
}


/*
 * 表は常に**1 行以上**にする（全部消しても、空行 1 つを残して入力できるように）。
 */
watch(
  () => props.values.length,
  (count) => {
    if (count === 0) {
      writeValues([''])
    }
  }
)

/** 外からも行を足せるようにする（ダイアログの見出しのボタンから）。 */
function addRowFromOutside(): void {
  addRow()
}

defineExpose({ addRowFromOutside })

onBeforeUnmount(() => {
  // ドラッグ中に閉じても、window の listener を残さない
  endSelection()
})
</script>

<template>
  <table
    ref="table" class="jp-sheet" tabindex="0" aria-label="登録する単語" data-demo-word-grid
    :style="{ width: `calc(7rem + ${width}px)` }"

    @copy="onCopy" @paste="onPaste" @keydown="onKeydown"
  >
    <colgroup>
      <!--
        左の 2 列（× と NO）は**固定**（TODO の表と同じ）。つまみも置かない。
        残りを単語の列が取る（単語の幅を変えても、この 2 列は動かない）。
      -->
      <col style="width: 3.5rem">
      <col style="width: 3.5rem">
      <col :style="{ width: `${width}px` }">
    </colgroup>
    <thead>
      <tr>
        <th class="jp-sheet__no-head" aria-label="削除" />
        <th class="jp-sheet__no-head">NO</th>
        <th data-col="word" style="position: relative">
          単語
          <span
            class="jp-sheet__resizer" role="separator" aria-orientation="vertical" tabindex="0"
            aria-label="単語の幅を変える" data-demo-col-resizer="word"
            title="ドラッグで幅を変えます（ダブルクリックで既定に戻す）"
            @mousedown.stop.prevent="startResize($event)"
            @dblclick.stop="resetWidth()"
            @keydown.stop="onResizeKeydown($event)"
          />
        </th>
      </tr>
    </thead>
    <tbody data-demo-word-grid-body>
      <tr v-for="(value, row) in values" :key="row" :data-demo-word-row-index="row">
        <td class="jp-sheet__remove">
          <button
            type="button" class="btn btn--icon btn--sm" :aria-label="`${row + 1} 行目を削除`"
            :data-demo-remove-row="row" @click="removeRow(row)"
          >
            <AppIcon name="x" size="sm" class="icon--danger" />
          </button>
        </td>
        <td class="jp-sheet__no">{{ row + 1 }}</td>
        <td
          data-col="word" :data-cell="`${row}-0`" :data-demo-word-cell="row" :class="cellClasses(row, 0)"
          @mousedown="startSelection(row, 0, $event)"
          @mouseenter="extendSelection(row, 0)"
          @click="focusTable()"
          @dblclick="startEdit(row, 0)"
        >
          <input
            v-if="isEditing(row, 0)"
            :value="value" type="text" :aria-label="`${row + 1} 行目の単語`"
            :data-demo-word-input="row"
            @input="setCell(row, ($event.target as HTMLInputElement).value)"
            @keydown="onEditorKeydown" @blur="commitEdit"
          >
          <span v-else class="jp-sheet__value" :data-demo-word-value="row">{{ value }}</span>
        </td>
      </tr>
      <tr v-if="values.length === 0">
        <td colspan="3" class="jp-hint" style="padding: var(--sp-3)">
          Excel から単語の列をコピーして、この表に貼り付けてください（1 行に 1 語）。
        </td>
      </tr>
    </tbody>
  </table>

  <!-- 行追加は画面には出さない（Excel の貼り付けで足りるため。動作確認用に残す） -->
  <span hidden data-demo-add-row @click="addRow" />
</template>
