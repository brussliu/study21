<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import GameHelpCard from '@/views/game/GameHelpCard.vue'
import {
  NONOGRAM_PRESETS, createPuzzle, hintIndex, isSolved, lineProgress, mistakes,
  type LineProgress, type NonoState, type NonogramPuzzle
} from '@/features/game/nonogram'
import { useGameTimer } from '@/features/game/useGameTimer'
import '@/features/game/game.css'

/** タブ表示（GameView）ではページ見出しをタブ側が持つため、この見出しは出さない。 */
const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

const route = useRoute()
const toast = useToast()
const timer = useGameTimer()

/** 左クリックで適用する操作（右クリックは逆の操作になる）。 */
type Mode = 'fill' | 'cross'

/** 選択中のサイズ（プリセットの key）。 */
const presetKey = ref<string>(NONOGRAM_PRESETS[0].key)
const puzzle = ref<NonogramPuzzle>(createPuzzle(presetKey.value))
const states = ref<NonoState[]>(freshStates())
const mode = ref<Mode>('fill')
/** ヒントで確定したマス（黄色い枠で示す）。 */
const hinted = ref<number[]>([])
const hintCount = ref(0)
const showWrong = ref(false)
/** 1 手でも打ったか（開始前 / プレイ中の表示に使う）。 */
const started = ref(false)
/** ドラッグ中に適用する状態（null ならドラッグしていない）。 */
const dragTarget = ref<NonoState | null>(null)

const area = computed(() => route.path.split('/')[1] ?? 'student')
const gameHomePath = computed(() => `/${area.value}/game`)

const solved = computed(() => isSolved(states.value, puzzle.value.solution))
const mistakeCount = computed(() => mistakes(states.value, puzzle.value.solution).length)
const filledCount = computed(() => states.value.filter((state) => state === 'filled').length)
const solutionTotal = computed(() => puzzle.value.solution.filter(Boolean).length)

const statusText = computed(() => {
  if (solved.value) {
    return {
      title: 'クリア！',
      text: `${puzzle.value.label} を完成させました（${timer.clock}）。`,
      modifier: 'is-win'
    }
  }
  if (started.value) {
    return { title: 'プレイ中', text: 'ヒント数字を見ながらマスを塗りつぶしましょう。', modifier: 'is-play' }
  }
  return { title: '開始前', text: 'マスをクリックすると塗りつぶし（または ×印）を描けます。', modifier: 'is-play' }
})

/** すべて未操作の盤面を作る。 */
function freshStates(): NonoState[] {
  return puzzle.value.solution.map((): NonoState => 'empty')
}

/** 新しい問題を出題する（サイズは選択中のものを使う）。 */
function newPuzzle(): void {
  puzzle.value = createPuzzle(presetKey.value)
  states.value = freshStates()
  hinted.value = []
  hintCount.value = 0
  showWrong.value = false
  started.value = false
  dragTarget.value = null
  timer.reset()
}

/** 盤面をすべて未操作に戻す（時間とヒント回数はそのまま）。 */
function clearBoard(): void {
  if (solved.value) return
  states.value = states.value.map((): NonoState => 'empty')
  hinted.value = []
  dragTarget.value = null
  toast.info('盤面をすべて消しました。')
}

/** 完成したらタイマーを止めて通知する。 */
function checkSolved(): void {
  if (!isSolved(states.value, puzzle.value.solution)) return
  timer.stop()
  toast.success('完成しました！')
}

/** 盤面を 1 マス更新する（同じ状態を重ねても何もしない）。 */
function applyState(index: number, value: NonoState): void {
  if (solved.value) return
  if (states.value[index] === value) return
  states.value[index] = value
  hinted.value = hinted.value.filter((item) => item !== index)
  if (!started.value) {
    started.value = true
    timer.start()
  }
  checkSolved()
}

/** マスを押したときの操作（同じ状態なら未操作に戻す）。 */
function toggleCell(index: number, value: NonoState): void {
  if (solved.value) return
  applyState(index, states.value[index] === value ? 'empty' : value)
}

/** 右クリックは選択中のモードと逆の操作にする。 */
function onCellRight(index: number): void {
  toggleCell(index, mode.value === 'fill' ? 'crossed' : 'filled')
}

/** 左ボタンを押したマスでドラッグを開始し、そのままなぞって連続で塗れるようにする。 */
function onCellDown(index: number, event: MouseEvent): void {
  if (solved.value) return
  if (event.button === 2) {
    onCellRight(index)
    return
  }
  if (event.button !== 0) return

  // 最初に押したマスで「塗る / 消す」を決めて、なぞったマスへ同じ状態を適用する
  const value: NonoState = mode.value === 'fill' ? 'filled' : 'crossed'
  dragTarget.value = states.value[index] === value ? 'empty' : value
  applyState(index, dragTarget.value)
}

function onCellEnter(index: number): void {
  if (dragTarget.value === null) return
  applyState(index, dragTarget.value)
}

function endStroke(): void {
  dragTarget.value = null
}

/**
 * ボタンの click。マウス操作は mousedown / mouseenter で処理済みなので、
 * event.detail が 0 のとき（キーボードの Enter / Space）だけ操作する。
 */
function onCellClick(index: number, event: MouseEvent): void {
  if (event.detail > 0) return
  toggleCell(index, mode.value === 'fill' ? 'filled' : 'crossed')
}

/** まだ塗られていない解答マスを 1 つ確定する。 */
function useHint(): void {
  if (solved.value) return
  const index = hintIndex(states.value, puzzle.value.solution)
  if (index < 0) {
    toast.info('塗るマスはもう残っていません。')
    return
  }

  const size = puzzle.value.size
  const row = Math.floor(index / size) + 1
  const col = (index % size) + 1
  states.value[index] = 'filled'
  if (!hinted.value.includes(index)) hinted.value = [...hinted.value, index]
  hintCount.value += 1
  if (!started.value) {
    started.value = true
    timer.start()
  }
  toast.warning(`ヒント: ${row}行${col}列を塗りました（${hintCount.value} 回目）。`)
  checkSolved()
}

/** 行の状態を切り出す。 */
function rowStates(row: number): NonoState[] {
  const size = puzzle.value.size
  return states.value.slice(row * size, (row + 1) * size)
}

/** 列の状態を切り出す。 */
function colStates(col: number): NonoState[] {
  const size = puzzle.value.size
  const line: NonoState[] = []
  for (let row = 0; row < size; row += 1) line.push(states.value[row * size + col])
  return line
}

/** ヒント数字の色分け（満たした / 行き過ぎ）。 */
function progressClass(progress: LineProgress): Record<string, boolean> {
  return { 'is-satisfied': progress === 'satisfied', 'is-over': progress === 'over' }
}

function rowClueClass(row: number): Record<string, boolean> {
  return progressClass(lineProgress(puzzle.value.rowClues[row], rowStates(row)))
}

function colClueClass(col: number): Record<string, boolean> {
  return progressClass(lineProgress(puzzle.value.colClues[col], colStates(col)))
}

const STATE_LABELS: Record<NonoState, string> = { empty: '未操作', filled: '塗りつぶし', crossed: '×印' }

function cellClass(index: number): Record<string, boolean> {
  const state = states.value[index]
  return {
    'is-filled': state === 'filled',
    'is-crossed': state === 'crossed',
    'is-hint': hinted.value.includes(index),
    'is-wrong': showWrong.value && state === 'filled' && !puzzle.value.solution[index]
  }
}

function cellLabel(state: NonoState, index: number): string {
  const size = puzzle.value.size
  const row = Math.floor(index / size) + 1
  const col = (index % size) + 1
  return `${row}行${col}列 ${STATE_LABELS[state]}`
}
</script>

<template>
  <div class="gm-page">
    <div v-if="!props.embedded" class="gm-head">
      <RouterLink class="btn btn--ghost btn--sm" :to="gameHomePath">
        <AppIcon name="chevron-left" size="sm" /> ゲーム一覧
      </RouterLink>
      <h2 class="gm-head__title"><AppIcon name="image" /> ノノグラム</h2>
      <p class="gm-head__desc">
        行と列のヒント数字を手がかりに、塗るマスを推理して絵を完成させましょう。
      </p>
    </div>

    <div class="gm-layout">
      <section class="card">
        <div class="card__header">
          <h3 class="card__title">盤面</h3>
          <span class="card__sub">{{ puzzle.size }} × {{ puzzle.size }} ／ {{ puzzle.label }}</span>
        </div>
        <div class="card__body gm-board-body">
          <div class="gm-nono" :class="{ 'is-small': puzzle.size <= 5 }">
            <span />
            <div
              class="gm-nono__colclues"
              :style="{ gridTemplateColumns: `repeat(${puzzle.size}, var(--gm-cell))` }"
            >
              <div
                v-for="(clue, col) in puzzle.colClues" :key="col"
                class="gm-nono-clue gm-nono-clue--col"
                :class="colClueClass(col)"
              >
                <span v-for="(number, i) in clue" :key="i">{{ number }}</span>
              </div>
            </div>
            <div
              class="gm-nono__rowclues"
              :style="{ gridTemplateRows: `repeat(${puzzle.size}, var(--gm-cell))` }"
            >
              <div
                v-for="(clue, row) in puzzle.rowClues" :key="row"
                class="gm-nono-clue gm-nono-clue--row"
                :class="rowClueClass(row)"
              >
                <span v-for="(number, i) in clue" :key="i">{{ number }}</span>
              </div>
            </div>
            <div
              class="gm-nono__board"
              :style="{ gridTemplateColumns: `repeat(${puzzle.size}, var(--gm-cell))` }"
              :aria-label="`ノノグラムの盤面 ${puzzle.size}×${puzzle.size}`"
              @mouseup="endStroke"
              @mouseleave="endStroke"
            >
              <button
                v-for="(state, index) in states" :key="index"
                type="button" class="gm-nono__cell"
                :class="cellClass(index)"
                :aria-label="cellLabel(state, index)"
                :disabled="solved"
                @mousedown="onCellDown(index, $event)"
                @mouseenter="onCellEnter(index)"
                @click="onCellClick(index, $event)"
                @contextmenu.prevent="onCellRight(index)"
              ></button>
            </div>
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
                <dt class="gm-metric__label">ミス</dt>
                <dd class="gm-metric__value">{{ mistakeCount }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">ヒント</dt>
                <dd class="gm-metric__value">{{ hintCount }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">塗ったマス</dt>
                <dd class="gm-metric__value">{{ filledCount }} / {{ solutionTotal }}</dd>
              </div>
            </dl>

            <div class="gm-controls">
              <div class="gm-toolbar">
                <label class="field field--inline">
                  <span class="field__label">サイズ</span>
                  <select v-model="presetKey" class="select" @change="newPuzzle">
                    <option v-for="preset in NONOGRAM_PRESETS" :key="preset.key" :value="preset.key">
                      {{ preset.label }}
                    </option>
                  </select>
                </label>
              </div>

              <div class="gm-actions">
                <button
                  type="button"
                  :class="mode === 'fill' ? 'btn btn--primary' : 'btn btn--secondary'"
                  :aria-pressed="mode === 'fill'"
                  @click="mode = 'fill'"
                >
                  <AppIcon name="edit" size="sm" /> 塗る
                </button>
                <button
                  type="button"
                  :class="mode === 'cross' ? 'btn btn--primary' : 'btn btn--secondary'"
                  :aria-pressed="mode === 'cross'"
                  @click="mode = 'cross'"
                >
                  <AppIcon name="x" size="sm" /> ×印
                </button>
                <button type="button" class="btn btn--secondary" @click="useHint">
                  <AppIcon name="info" size="sm" /> ヒント
                </button>
                <button
                  type="button"
                  :class="showWrong ? 'btn btn--primary' : 'btn btn--secondary'"
                  :aria-pressed="showWrong"
                  @click="showWrong = !showWrong"
                >
                  <AppIcon name="eye" size="sm" /> 間違いを表示
                </button>
                <button type="button" class="btn btn--secondary" @click="clearBoard">
                  <AppIcon name="eraser" size="sm" /> 全部消す
                </button>
                <button type="button" class="btn btn--primary" @click="newPuzzle">
                  <AppIcon name="plus" size="sm" /> 新しい問題
                </button>
              </div>
            </div>
          </div>
        </div>

        <GameHelpCard slug="nonogram" />
      </aside>
    </div>
  </div>
</template>
