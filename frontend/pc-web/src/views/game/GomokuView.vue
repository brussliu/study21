<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import GameHelpCard from '@/views/game/GameHelpCard.vue'
import { GAME_HELP } from '@/features/game/gameHelp'
import { useHelpLanguage } from '@/features/game/useHelpLanguage'
import {
  GOMOKU_SIZE, WIN_LENGTH, chooseCpuMove, createGomoku, findWinningLine, isBoardFull,
  placeStone, type Stone
} from '@/features/game/gomoku'
import { useGameTimer } from '@/features/game/useGameTimer'
import '@/features/game/game.css'

/** タブ表示（GameView）ではページ見出しをタブ側が持つため、この見出しは出さない。 */
const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

/** 対戦モード。'pvp' は 2 人対戦、'cpu' は人間（黒）対 CPU（白）。 */
type GameMode = 'pvp' | 'cpu'

/** CPU が打つまでの待ち時間（ms）。考えている間を演出する。 */
const CPU_DELAY_MS = 250

/** CPU モードで人間が担当する石（先手の黒）。 */
const HUMAN_STONE: Stone = 'black'
/** CPU モードで CPU が担当する石（後手の白）。 */
const CPU_STONE: Stone = 'white'

const route = useRoute()
const toast = useToast()
const timer = useGameTimer()

const size = GOMOKU_SIZE
const mode = ref<GameMode>('pvp')
const cells = ref<(Stone | null)[]>(createGomoku(size))
/** 着手した順番（待った と 手数 に使う）。 */
const history = ref<number[]>([])
/** 勝利した一線のマス（無ければ空）。 */
const winLine = ref<number[]>([])
const winner = ref<Stone | null>(null)
const drawn = ref(false)
const thinking = ref(false)

/** 盤面の DOM（矢印キーでのフォーカス移動に使う）。 */
const boardEl = ref<HTMLElement | null>(null)
/** CPU の着手予約（画面を離れるときに必ず解除する）。 */
let cpuHandle: ReturnType<typeof setTimeout> | null = null

const area = computed(() => route.path.split('/')[1] ?? 'student')
const gameHomePath = computed(() => `/${area.value}/game`)

// 凡例も「遊び方」カードと同じ言語に合わせる（カード右上の切り替えに追従）。
const help = useHelpLanguage()
const helpExtras = computed(() => GAME_HELP.gomoku[help.language].extras ?? {})
const legendBlack = computed(() => helpExtras.value.legendBlack ?? '黒')
const legendWhite = computed(() =>
  (mode.value === 'cpu' ? helpExtras.value.legendWhiteCpu : helpExtras.value.legendWhite) ?? '白'
)

/** 黒が先手なので、着手数から手番が決まる。 */
const turn = computed<Stone>(() => (history.value.length % 2 === 0 ? 'black' : 'white'))
const finished = computed(() => winner.value !== null || drawn.value)
const lastIndex = computed(() => history.value[history.value.length - 1] ?? null)
const emptyCount = computed(() => size * size - history.value.length)

const statusText = computed(() => {
  if (winner.value !== null) {
    return {
      title: `${stoneLabel(winner.value)}の勝ち！`,
      text: `${WIN_LENGTH} 連ができました（${timer.clock}）。`,
      modifier: 'is-win'
    }
  }
  if (drawn.value) {
    return {
      title: '引き分け',
      text: '盤面が埋まりました。「新しい対局」でやり直せます。',
      modifier: 'is-play'
    }
  }
  if (thinking.value) {
    return { title: 'CPU の手番', text: 'CPU が考えています…', modifier: 'is-play' }
  }
  if (history.value.length === 0) {
    return { title: '対局前', text: '黒（先手）から始めます。空いているマスをクリックしてください。', modifier: 'is-play' }
  }
  return {
    title: `${stoneLabel(turn.value)}の手番`,
    text: '空いているマスをクリックすると石を置きます。',
    modifier: 'is-play'
  }
})

/** 「黒」「白」のラベル。 */
function stoneLabel(stone: Stone): string {
  return stone === 'black' ? '黒' : '白'
}

/** マスの読み上げ用ラベル（例: 8行8列 空き）。 */
function cellLabel(cell: Stone | null, index: number): string {
  const row = Math.floor(index / size) + 1
  const col = (index % size) + 1
  return `${row}行${col}列 ${cell === null ? '空き' : stoneLabel(cell)}`
}

/** CPU の着手予約を解除する。 */
function cancelCpuMove(): void {
  if (cpuHandle !== null) {
    clearTimeout(cpuHandle)
    cpuHandle = null
  }
  thinking.value = false
}

/** 新しい対局を始める（モードは選択中のものを使う）。 */
function newGame(): void {
  cancelCpuMove()
  cells.value = createGomoku(size)
  history.value = []
  winLine.value = []
  winner.value = null
  drawn.value = false
  timer.reset()
}

/** 直前の手を戻す（CPU モードでは人間と CPU の 1 組を戻す）。 */
function undo(): void {
  if (history.value.length === 0) {
    toast.warning('戻せる手がありません。')
    return
  }
  cancelCpuMove()
  const steps = mode.value === 'cpu' ? Math.min(2, history.value.length) : 1
  for (let step = 0; step < steps; step += 1) {
    const index = history.value.pop()
    if (index === undefined) break
    cells.value[index] = null
  }
  winLine.value = []
  winner.value = null
  drawn.value = false
  if (history.value.length === 0) timer.reset()
  else timer.start()
}

/** 石を置いて勝敗を判定する。 */
function applyMove(index: number, stone: Stone): void {
  if (finished.value) return
  if (!placeStone(cells.value, size, index, stone)) return
  history.value.push(index)
  if (history.value.length === 1) timer.start()

  const line = findWinningLine(cells.value, size, index, stone)
  if (line.length >= WIN_LENGTH) {
    winLine.value = line
    winner.value = stone
    timer.stop()
    toast.success(`${stoneLabel(stone)}の勝ちです！`)
    return
  }
  if (isBoardFull(cells.value)) {
    drawn.value = true
    timer.stop()
    toast.info('盤面が埋まりました。引き分けです。')
    return
  }
  // CPU モードでは、人間（黒）が打ったあとに少し間を置いて CPU が打つ
  if (mode.value === 'cpu' && stone === HUMAN_STONE) scheduleCpuMove()
}

/** CPU の着手を予約する（画面を離れるときに解除する）。 */
function scheduleCpuMove(): void {
  cancelCpuMove()
  thinking.value = true
  cpuHandle = setTimeout(() => {
    cpuHandle = null
    thinking.value = false
    if (finished.value || mode.value !== 'cpu') return
    const index = chooseCpuMove(cells.value, size, CPU_STONE)
    if (index < 0) return
    const row = Math.floor(index / size) + 1
    const col = (index % size) + 1
    toast.info(`CPU が ${row}行${col}列 に打ちました。`)
    applyMove(index, CPU_STONE)
  }, CPU_DELAY_MS)
}

function onCellClick(index: number): void {
  if (finished.value) return
  if (mode.value === 'cpu' && turn.value !== HUMAN_STONE) return // CPU の番はクリックできない
  applyMove(index, turn.value)
}

/** 矢印キーでマス間を移動できるようにする。 */
function onCellKeydown(event: KeyboardEvent, index: number): void {
  const deltas: Record<string, [number, number]> = {
    ArrowUp: [-1, 0],
    ArrowDown: [1, 0],
    ArrowLeft: [0, -1],
    ArrowRight: [0, 1]
  }
  const delta = deltas[event.key]
  if (!delta) return
  event.preventDefault()

  const row = Math.floor(index / size) + delta[0]
  const col = (index % size) + delta[1]
  if (!Number.isInteger(row) || row < 0 || row >= size || col < 0 || col >= size) return

  const buttons = boardEl.value?.querySelectorAll<HTMLButtonElement>('button')
  buttons?.[row * size + col]?.focus()
}

onBeforeUnmount(cancelCpuMove)
</script>

<template>
  <div class="gm-page">
    <div v-if="!props.embedded" class="gm-head">
      <RouterLink class="btn btn--ghost btn--sm" :to="gameHomePath">
        <AppIcon name="chevron-left" size="sm" /> ゲーム一覧
      </RouterLink>
      <h2 class="gm-head__title"><AppIcon name="circle" /> 五目並べ</h2>
      <p class="gm-head__desc">
        縦・横・斜めのいずれかに自分の石を {{ WIN_LENGTH }} つ並べると勝ちです。黒が先手です。
      </p>
    </div>

    <div class="gm-layout">
      <section class="card">
        <div class="card__header">
          <h3 class="card__title">盤面</h3>
          <span class="card__sub">{{ size }} × {{ size }} ／ {{ WIN_LENGTH }} 連で勝利</span>
        </div>
        <div class="card__body gm-board-body">
          <div
            ref="boardEl"
            class="gm-gomoku"
            :style="{ gridTemplateColumns: `repeat(${size}, var(--gm-cell))` }"
            :aria-label="`五目並べの盤面 ${size}×${size}`"
          >
            <button
              v-for="(cell, index) in cells" :key="index"
              type="button" class="gm-gomoku-cell"
              :class="{
                'is-taken': cell !== null,
                'is-last': index === lastIndex,
                'is-win': winLine.includes(index)
              }"
              :aria-label="cellLabel(cell, index)"
              @click="onCellClick(index)"
              @keydown="onCellKeydown($event, index)"
            >
              <span v-if="cell !== null" class="gm-stone" :class="`gm-stone--${cell}`" />
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
                <dt class="gm-metric__label">手数</dt>
                <dd class="gm-metric__value">{{ history.length }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">経過時間</dt>
                <dd class="gm-metric__value">{{ timer.clock }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">手番</dt>
                <dd class="gm-metric__value">{{ thinking ? 'CPU' : stoneLabel(turn) }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">空きマス</dt>
                <dd class="gm-metric__value">{{ emptyCount }}</dd>
              </div>
            </dl>

            <div class="gm-controls">
              <div class="gm-toolbar">
                <label class="field field--inline">
                  <span class="field__label">モード</span>
                  <select v-model="mode" class="select" @change="newGame">
                    <option value="pvp">2人対戦</option>
                    <option value="cpu">CPU（あなた=黒）</option>
                  </select>
                </label>
              </div>

              <div class="gm-actions">
                <button type="button" class="btn btn--primary" @click="newGame">
                  <AppIcon name="plus" size="sm" /> 新しい対局
                </button>
                <button
                  type="button" class="btn btn--secondary"
                  :disabled="history.length === 0" @click="undo"
                >
                  <AppIcon name="rotate" size="sm" /> 待った
                </button>
              </div>
            </div>
          </div>
        </div>

        <GameHelpCard slug="gomoku" />

        <div class="gm-legend">
          <span class="gm-legend__item">
            <span class="gm-rev-disc gm-rev-disc--black gm-gomoku-legend__disc" />
            {{ legendBlack }}
          </span>
          <span class="gm-legend__item">
            <span class="gm-rev-disc gm-rev-disc--white gm-gomoku-legend__disc" />
            {{ legendWhite }}
          </span>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
/* 凡例の丸は game.css の .gm-rev-disc を流用し、大きさだけ小さく固定する */
.gm-gomoku-legend__disc {
  flex: none;
  width: 14px;
  height: 14px;
}
</style>
