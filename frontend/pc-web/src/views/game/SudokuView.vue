<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import GameHelpCard from '@/views/game/GameHelpCard.vue'
import {
  SUDOKU_PRESETS, SUDOKU_SIZE, findConflicts, generateSudoku, isComplete, isSolved,
  type SudokuDifficulty
} from '@/features/game/sudoku'
import { useGameTimer } from '@/features/game/useGameTimer'
import '@/features/game/game.css'

/** タブ表示（GameView）ではページ見出しをタブ側が持つため、この見出しは出さない。 */
const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

/** 盤面のマスの大きさ（px）。見やすい大きさで全難易度共通。 */
const CELL_SIZE_PX = 56

const route = useRoute()
const toast = useToast()
const timer = useGameTimer()

/** 「元に戻す」ために 1 手分だけ覚えておく、入力前のマスの状態。 */
interface UndoEntry {
  index: number
  value: number
  notes: number[]
}

const difficulty = ref<SudokuDifficulty>('normal')
/** 問題（0 が空きマス）。初期数字かどうかの判定にも使う。 */
const puzzle = ref<number[]>([])
/** 解答（完成盤）。ヒントと正誤判定に使う。 */
const solution = ref<number[]>([])
/** 現在の盤面（puzzle をコピーして始める）。 */
const grid = ref<number[]>([])
/** 各マスのメモ（候補数字の一覧）。 */
const notes = ref<number[][]>([])
const selected = ref(0)
const noteMode = ref(false)
const showWrong = ref(false)
const mistakes = ref(0)
const hintCount = ref(0)
const finished = ref(false)
const undoStack = ref<UndoEntry[]>([])

const area = computed(() => route.path.split('/')[1] ?? 'student')
const gameHomePath = computed(() => `/${area.value}/game`)

/** 初期数字（変更できないマス）かどうか。 */
const givenMask = computed(() => puzzle.value.map((value) => value !== 0))
const givenCount = computed(() => givenMask.value.filter((given) => given).length)
/** 行・列・3×3 ブロックで重複しているマス。 */
const conflicts = computed(() => findConflicts(grid.value))
/** まだ埋まっていないマスの数。 */
const remaining = computed(() => grid.value.filter((value) => value === 0).length)

const statusText = computed(() => {
  if (finished.value) {
    return { title: '完成！', text: `すべてのマスを埋めました（${timer.clock}）。`, modifier: 'is-win' }
  }
  if (conflicts.value.size > 0) {
    return {
      title: 'ミスあり',
      text: '行・列・3×3 ブロックで数字が重複しています。赤いマスを直しましょう。',
      modifier: 'is-lose'
    }
  }
  return {
    title: 'プレイ中',
    text: 'マスを選んで 1〜9 を入力します。メモを ON にすると候補数字を書き込めます。',
    modifier: 'is-play'
  }
})

/** 新しい問題を作る（難易度は選択中のものを使う）。 */
function newGame(): void {
  const generated = generateSudoku(difficulty.value)
  puzzle.value = generated.puzzle
  solution.value = generated.solution
  grid.value = [...generated.puzzle]
  notes.value = generated.puzzle.map((): number[] => [])
  const firstEmpty = generated.puzzle.findIndex((value) => value === 0)
  selected.value = firstEmpty >= 0 ? firstEmpty : 0
  mistakes.value = 0
  hintCount.value = 0
  finished.value = false
  undoStack.value = []
  timer.reset()
}

function onChangeDifficulty(): void {
  newGame()
  toast.info('新しい問題を作りました。')
}

/** 最初の入力でタイマーを動かし始める。 */
function startTimerIfNeeded(): void {
  if (!timer.running) timer.start()
}

/** 入力前のマスの状態を「元に戻す」用に控える。 */
function pushUndo(index: number): void {
  undoStack.value.push({ index, value: grid.value[index], notes: [...notes.value[index]] })
}

/** 盤面がすべて埋まり、解答と一致したら完成。 */
function checkFinished(): void {
  if (finished.value) return
  if (!isComplete(grid.value) || !isSolved(grid.value, solution.value)) return
  finished.value = true
  timer.stop()
  toast.success('完成しました！')
}

function onCellClick(index: number): void {
  selected.value = index
}

/** 選択中のマスに数字を入れる（メモ中は候補数字の追加・削除）。 */
function inputNumber(value: number): void {
  if (finished.value) return
  const index = selected.value
  if (index < 0 || index >= grid.value.length) return
  if (givenMask.value[index]) {
    toast.warning('初期数字は変更できません。')
    return
  }

  if (noteMode.value) {
    if (grid.value[index] !== 0) {
      toast.warning('メモは空きマスにだけ書けます。')
      return
    }
    startTimerIfNeeded()
    pushUndo(index)
    toggleNote(index, value)
    return
  }

  startTimerIfNeeded()
  pushUndo(index)
  if (grid.value[index] === value) {
    grid.value[index] = 0 // 同じ数字をもう一度入れたら消す
  } else {
    grid.value[index] = value
    notes.value[index] = [] // 数字を確定したのでメモは不要になる
  }
  if (grid.value[index] !== 0 && conflicts.value.has(index)) mistakes.value += 1
  checkFinished()
}

/** メモ（候補数字）を付け外しする。 */
function toggleNote(index: number, value: number): void {
  const list = notes.value[index]
  const at = list.indexOf(value)
  if (at >= 0) list.splice(at, 1)
  else list.push(value)
  list.sort((a, b) => a - b)
}

/** 選択中のマスを消す（メモも一緒に消す）。 */
function eraseCell(): void {
  if (finished.value) return
  const index = selected.value
  if (givenMask.value[index]) {
    toast.warning('初期数字は消せません。')
    return
  }
  if (grid.value[index] === 0 && notes.value[index].length === 0) return
  pushUndo(index)
  grid.value[index] = 0
  notes.value[index] = []
}

/** 直前の入力を 1 手戻す。 */
function undo(): void {
  if (finished.value) return
  const entry = undoStack.value.pop()
  if (!entry) {
    toast.warning('戻せる入力がありません。')
    return
  }
  grid.value[entry.index] = entry.value
  notes.value[entry.index] = entry.notes
  selected.value = entry.index
  toast.info('1 手戻しました。')
}

/** 空きマスを 1 つ、解答の数字で確定する。 */
function useHint(): void {
  if (finished.value) return
  const empties: number[] = []
  for (let index = 0; index < grid.value.length; index += 1) {
    if (grid.value[index] === 0) empties.push(index)
  }
  if (empties.length === 0) {
    toast.info('空きマスがありません。')
    return
  }
  // 選択中の空きマスを優先し、そうでなければ最初の空きマスを使う
  const index = empties.includes(selected.value) ? selected.value : empties[0]
  startTimerIfNeeded()
  pushUndo(index)
  grid.value[index] = solution.value[index]
  notes.value[index] = []
  hintCount.value += 1
  selected.value = index
  toast.info(`${Math.floor(index / SUDOKU_SIZE) + 1}行${(index % SUDOKU_SIZE) + 1}列に ${solution.value[index]} を入れました。`)
  checkFinished()
}

function toggleNoteMode(): void {
  noteMode.value = !noteMode.value
  toast.info(noteMode.value ? 'メモを ON にしました（候補数字を書き込めます）。' : 'メモを OFF にしました。')
}

function toggleShowWrong(): void {
  showWrong.value = !showWrong.value
  toast.info(showWrong.value ? '間違いを表示します。' : '間違いの表示をやめました。')
}

/** 矢印キーで選択マスを動かす（盤外には出ない）。 */
function moveSelection(key: string): void {
  const row = Math.floor(selected.value / SUDOKU_SIZE)
  const col = selected.value % SUDOKU_SIZE
  let nextRow = row
  let nextCol = col
  if (key === 'ArrowUp') nextRow = Math.max(0, row - 1)
  else if (key === 'ArrowDown') nextRow = Math.min(SUDOKU_SIZE - 1, row + 1)
  else if (key === 'ArrowLeft') nextCol = Math.max(0, col - 1)
  else if (key === 'ArrowRight') nextCol = Math.min(SUDOKU_SIZE - 1, col + 1)
  selected.value = nextRow * SUDOKU_SIZE + nextCol
}

/** 盤面のコンテナで受けるキーボード操作（document では受け取らない）。 */
function onKeydown(event: KeyboardEvent): void {
  const key = event.key
  if (key >= '1' && key <= '9') {
    event.preventDefault()
    inputNumber(Number(key))
    return
  }
  if (key === 'Backspace' || key === 'Delete' || key === '0') {
    event.preventDefault()
    eraseCell()
    return
  }
  if (key.startsWith('Arrow')) {
    event.preventDefault()
    moveSelection(key)
  }
}

/** メモを表示するマスか（空きマスで、候補が 1 つ以上ある）。 */
function showsNotes(index: number): boolean {
  return grid.value[index] === 0 && notes.value[index].length > 0
}

function hasNote(index: number, value: number): boolean {
  return notes.value[index].includes(value)
}

/** 選択中のマスと同じ数字か（強調表示に使う）。 */
function isSameValue(index: number): boolean {
  if (index === selected.value) return false
  const value = grid.value[index]
  if (value === 0) return false
  return value === grid.value[selected.value]
}

function cellClasses(index: number): Record<string, boolean> {
  const row = Math.floor(index / SUDOKU_SIZE)
  const col = index % SUDOKU_SIZE
  const value = grid.value[index]
  return {
    'is-given': givenMask.value[index],
    'is-selected': selected.value === index,
    'is-same': isSameValue(index),
    'is-conflict': conflicts.value.has(index),
    'is-wrong': showWrong.value && !givenMask.value[index] && value !== 0 && value !== solution.value[index],
    'is-box-right': col === 2 || col === 5,
    'is-box-bottom': row === 2 || row === 5,
    'is-last-col': col === SUDOKU_SIZE - 1,
    'is-last-row': row === SUDOKU_SIZE - 1
  }
}

/** 読み上げ用のマスの説明（例：3行5列 入力済み 7）。 */
function cellLabel(index: number): string {
  const row = Math.floor(index / SUDOKU_SIZE) + 1
  const col = (index % SUDOKU_SIZE) + 1
  const base = `${row}行${col}列`
  const suffix = selected.value === index ? ' 選択中' : ''
  const value = grid.value[index]
  if (value === 0) {
    const list = notes.value[index]
    return list.length > 0 ? `${base} 空き メモ ${list.join('')}${suffix}` : `${base} 空き${suffix}`
  }
  const kind = givenMask.value[index] ? '初期数字' : '入力済み'
  return `${base} ${kind} ${value}${suffix}`
}

newGame()
</script>

<template>
  <div class="gm-page">
    <div v-if="!props.embedded" class="gm-head">
      <RouterLink class="btn btn--ghost btn--sm" :to="gameHomePath">
        <AppIcon name="chevron-left" size="sm" /> ゲーム一覧
      </RouterLink>
      <h2 class="gm-head__title"><AppIcon name="grid" /> 数独</h2>
      <p class="gm-head__desc">
        9×9 の盤面に 1〜9 の数字を 1 つずつ入れます。同じ行・列・3×3 ブロックで数字が重複しないように埋めましょう。
      </p>
    </div>

    <div class="gm-layout">
      <section class="card">
        <div class="card__header">
          <h3 class="card__title">盤面</h3>
          <span class="card__sub">{{ SUDOKU_SIZE }} × {{ SUDOKU_SIZE }} ／ 初期数字 {{ givenCount }}</span>
        </div>
        <div class="card__body gm-board-body">
          <div
            class="gm-sudoku"
            role="group"
            tabindex="0"
            :style="{ '--gm-cell': `${CELL_SIZE_PX}px` }"
            :aria-label="`数独の盤面 ${SUDOKU_SIZE}行${SUDOKU_SIZE}列`"
            @keydown="onKeydown"
          >
            <button
              v-for="(value, index) in grid" :key="index"
              type="button" class="gm-sudoku-cell"
              :class="cellClasses(index)"
              :aria-label="cellLabel(index)"
              @click="onCellClick(index)"
            >
              <template v-if="showsNotes(index)">
                <span class="gm-sudoku-notes">
                  <span v-for="note in SUDOKU_SIZE" :key="note">{{ hasNote(index, note) ? note : '' }}</span>
                </span>
              </template>
              <template v-else>{{ value === 0 ? '' : value }}</template>
            </button>
          </div>
        </div>
      </section>

      <aside class="gm-side">
        <div class="card">
          <div class="card__header">
            <h3 class="card__title">ステータス</h3>
          </div>
          <div class="card__body gm-status-body">
            <div class="gm-status" :class="statusText.modifier" role="status" aria-live="polite">
              <span class="gm-status__title">{{ statusText.title }}</span>
              <span class="gm-status__text">{{ statusText.text }}</span>
            </div>

            <dl class="gm-metrics">
              <div class="gm-metric">
                <dt class="gm-metric__label">経過時間</dt>
                <dd class="gm-metric__value">{{ timer.clock }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">残りマス</dt>
                <dd class="gm-metric__value">{{ remaining }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">ミス数</dt>
                <dd class="gm-metric__value">{{ mistakes }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">ヒント使用回数</dt>
                <dd class="gm-metric__value">{{ hintCount }}</dd>
              </div>
            </dl>

            <div class="gm-controls">
              <div class="gm-toolbar">
                <label class="field field--inline">
                  <span class="field__label">難易度</span>
                  <select v-model="difficulty" class="select" @change="onChangeDifficulty">
                    <option v-for="preset in SUDOKU_PRESETS" :key="preset.key" :value="preset.key">
                      {{ preset.label }}
                    </option>
                  </select>
                </label>
              </div>

              <div class="gm-actions gm-actions--grid">
                <button type="button" class="btn btn--primary gm-actions__wide" @click="newGame">
                  <AppIcon name="plus" size="sm" /> 新しい問題
                </button>
                <button type="button" class="btn btn--secondary" @click="undo">
                  <AppIcon name="rotate" size="sm" /> 元に戻す
                </button>
                <button type="button" class="btn btn--secondary" @click="useHint">
                  <AppIcon name="info" size="sm" /> ヒント
                </button>
                <button
                  type="button" class="btn btn--secondary"
                  :aria-pressed="noteMode"
                  @click="toggleNoteMode"
                >
                  <AppIcon name="edit" size="sm" /> メモ
                </button>
                <button
                  type="button" class="btn btn--secondary"
                  :aria-pressed="showWrong"
                  @click="toggleShowWrong"
                >
                  <AppIcon name="eye" size="sm" /> 間違いを表示
                </button>
              </div>

              <div class="gm-numpad">
                <button
                  v-for="num in SUDOKU_SIZE" :key="num"
                  type="button" class="btn btn--secondary"
                  :aria-label="`${num} を入力`"
                  @click="inputNumber(num)"
                >
                  {{ num }}
                </button>
                <button
                  type="button" class="btn btn--secondary gm-numpad__clear"
                  aria-label="マスを消す" @click="eraseCell"
                >
                  消す
                </button>
              </div>
            </div>
          </div>
        </div>

        <GameHelpCard slug="sudoku" />
      </aside>
    </div>
  </div>
</template>

<style scoped>
/* トグル（メモ / 間違いを表示）が ON のときに見分けやすい見た目にする */
.gm-toolbar .btn[aria-pressed='true'],
.gm-actions .btn[aria-pressed='true'] {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  color: var(--color-primary);
}
</style>
