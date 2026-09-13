<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import GameHelpCard from '@/views/game/GameHelpCard.vue'
import {
  MINE_PRESETS, boardStatus, chordCell, countFlags, createBoard, minePreset, openCell, remainingMines,
  revealMines, toggleFlag, type MineBoard, type MineCell, type MineDifficulty
} from '@/features/game/minesweeper'
import { useGameTimer } from '@/features/game/useGameTimer'
import '@/features/game/game.css'

/** タブ表示（GameView）ではページ見出しをタブ側が持つため、この見出しは出さない。 */
const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

const route = useRoute()
const toast = useToast()
const timer = useGameTimer()

const difficulty = ref<MineDifficulty>('beginner')
const board = ref<MineBoard>(createBoard(minePreset(difficulty.value)))

const area = computed(() => route.path.split('/')[1] ?? 'student')
const gameHomePath = computed(() => `/${area.value}/game`)
const status = computed(() => boardStatus(board.value))
const flags = computed(() => countFlags(board.value))
const safeTotal = computed(() => board.value.rows * board.value.cols - board.value.mines)

/** マスの大きさ（px）。難易度が変わっても同じ大きさで揃える。 */
const CELL_SIZE_PX = 40

const statusText = computed(() => {
  switch (status.value) {
    case 'ready':
      return { title: '開始前', text: '好きなマスをクリックすると開始します（初手は安全です）。', modifier: 'is-play' }
    case 'playing':
      return { title: 'プレイ中', text: '右クリックで旗、左右同時押しで周囲をまとめて開けます。', modifier: 'is-play' }
    case 'won':
      return { title: 'クリア！', text: `すべての安全なマスを開きました（${timer.clock}）。`, modifier: 'is-win' }
    default:
      return { title: '失敗…', text: '地雷を踏みました。「新しいゲーム」でやり直せます。', modifier: 'is-lose' }
  }
})

/** 左右同時押し（チョード）の直後に飛んでくる click / contextmenu を 1 回だけ無視する。 */
const suppressClick = ref(false)
const suppressContext = ref(false)

/** 新規ゲーム（難易度は選択中のものを使う）。 */
function newGame(): void {
  board.value = createBoard(minePreset(difficulty.value))
  suppressClick.value = false
  suppressContext.value = false
  timer.reset()
}

/** クリア判定。クリアなら残りの地雷に旗を立ててタイマーを止める。 */
function finishIfCleared(current: MineBoard): boolean {
  if (boardStatus(current) !== 'won') return false
  // 残りの地雷に旗を立てて見た目を整える
  current.cells.forEach((target) => {
    if (target.mine) target.flag = true
  })
  timer.stop()
  toast.success('クリアしました！')
  return true
}

function onCellClick(index: number): void {
  const current = board.value
  if (status.value === 'won' || status.value === 'lost') return
  const cell = current.cells[index]
  if (cell.flag || cell.open) return
  if (status.value === 'ready') timer.start()

  const result = openCell(current, index)
  if (result.exploded) {
    revealMines(current)
    timer.stop()
    toast.danger('地雷を踏みました。')
    return
  }
  finishIfCleared(current)
}

function onCellFlag(index: number): void {
  if (status.value === 'won' || status.value === 'lost') return
  toggleFlag(board.value, index)
}

/** イベントの発生元からマスの位置を取り出す（盤面全体で 1 つずつイベントを拾うため）。 */
function cellIndexFrom(event: Event): number | null {
  const target = event.target as HTMLElement | null
  const element = target?.closest?.('.gm-mine-cell') as HTMLElement | null
  if (!element) return null
  const index = Number(element.dataset.index)
  return Number.isInteger(index) ? index : null
}

/**
 * 左右のボタンを同時に押したとき（2.0 と同じ動作）。
 * 開いている数字マスの上で、周囲の旗の数が数字と一致していれば周囲をまとめて開く。
 */
function onBoardMouseDown(event: MouseEvent): void {
  // 左=1 / 右=2。両方押されているときだけ（3）
  if ((event.buttons & 3) !== 3) return
  const index = cellIndexFrom(event)
  if (index === null) return
  if (status.value === 'won' || status.value === 'lost') return

  event.preventDefault()
  suppressClick.value = true
  suppressContext.value = true

  const current = board.value
  const result = chordCell(current, index)
  if (!result.triggered) return

  if (result.exploded) {
    revealMines(current)
    timer.stop()
    toast.danger('旗の位置が違いました。地雷を踏みました。')
    return
  }
  finishIfCleared(current)
}

function onBoardClick(event: MouseEvent): void {
  if (suppressClick.value) {
    suppressClick.value = false
    return
  }
  const index = cellIndexFrom(event)
  if (index !== null) onCellClick(index)
}

/** 右クリックは旗を立てる／外す。 */
function onBoardContextMenu(event: MouseEvent): void {
  const index = cellIndexFrom(event)
  if (index === null) return
  event.preventDefault()
  if (suppressContext.value) {
    suppressContext.value = false
    return
  }
  onCellFlag(index)
}

function cellText(cell: MineCell): string {
  if (cell.wrong) return '✕'
  if (cell.flag) return '⚑'
  if (!cell.open) return ''
  if (cell.mine) return '✹'
  return cell.adjacent > 0 ? String(cell.adjacent) : ''
}

function cellLabel(cell: MineCell, index: number): string {
  const row = Math.floor(index / board.value.cols) + 1
  const col = (index % board.value.cols) + 1
  if (cell.flag) return `${row}行${col}列 旗`
  if (!cell.open) return `${row}行${col}列 未開封`
  if (cell.mine) return `${row}行${col}列 地雷`
  return `${row}行${col}列 開封済み ${cell.adjacent}`
}
</script>

<template>
  <div class="gm-page">
    <div v-if="!props.embedded" class="gm-head">
      <RouterLink class="btn btn--ghost btn--sm" :to="gameHomePath">
        <AppIcon name="chevron-left" size="sm" /> ゲーム一覧
      </RouterLink>
      <h2 class="gm-head__title"><AppIcon name="alert" /> マインスイーパー</h2>
      <p class="gm-head__desc">
        地雷のないマスをすべて開くとクリアです。数字はそのマスに隣接する地雷の数です。
      </p>
    </div>

    <div class="gm-layout">
      <section class="card">
        <div class="card__header">
          <h3 class="card__title">盤面</h3>
          <span class="card__sub">{{ board.rows }} × {{ board.cols }} ／ 地雷 {{ board.mines }}</span>
        </div>
        <div class="card__body gm-board-body">
          <div
            class="gm-mine-board"
            :style="{ gridTemplateColumns: `repeat(${board.cols}, var(--gm-cell))`, '--gm-cell': `${CELL_SIZE_PX}px` }"
            :aria-label="`マインスイーパーの盤面 ${board.rows}×${board.cols}`"
            @mousedown="onBoardMouseDown"
            @click="onBoardClick"
            @contextmenu="onBoardContextMenu"
          >
            <button
              v-for="(cell, index) in board.cells" :key="index"
              type="button" class="gm-mine-cell"
              :class="{
                'is-open': cell.open,
                'is-flag': cell.flag && !cell.wrong,
                'is-mine': cell.open && cell.mine && board.exploded !== index,
                'is-exploded': board.exploded === index,
                'is-wrong': cell.wrong
              }"
              :data-index="index"
              :data-count="cell.open && !cell.mine ? cell.adjacent : undefined"
              :aria-label="cellLabel(cell, index)"
              :disabled="status === 'won' || status === 'lost'"
            >
              {{ cellText(cell) }}
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
                <dt class="gm-metric__label">残りの地雷</dt>
                <dd class="gm-metric__value">{{ remainingMines(board) }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">経過時間</dt>
                <dd class="gm-metric__value">{{ timer.clock }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">開いたマス</dt>
                <dd class="gm-metric__value">{{ board.opened }} / {{ safeTotal }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">立てた旗</dt>
                <dd class="gm-metric__value">{{ flags }}</dd>
              </div>
            </dl>

            <div class="gm-controls">
              <div class="gm-toolbar">
                <label class="field field--inline">
                  <span class="field__label">難易度</span>
                  <!-- 選んだ難易度は次の【新しいゲーム】から使う（対局中は変わらない）。 -->
                  <select v-model="difficulty" class="select">
                    <option v-for="preset in MINE_PRESETS" :key="preset.key" :value="preset.key">
                      {{ preset.label }}
                    </option>
                  </select>
                </label>
              </div>

              <div class="gm-actions">
                <button type="button" class="btn btn--primary" @click="newGame">
                  <AppIcon name="plus" size="sm" /> 新しいゲーム
                </button>
              </div>
            </div>
          </div>
        </div>

        <GameHelpCard slug="minesweeper" />
      </aside>
    </div>
  </div>
</template>
