<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import GameHelpCard from '@/views/game/GameHelpCard.vue'
import {
  REVERSI_SIZE, applyMove, chooseCpuMove, countDiscs, createReversi, isGameOver, legalMoves,
  otherDisc, type Disc
} from '@/features/game/reversi'
import { useGameTimer } from '@/features/game/useGameTimer'
import '@/features/game/game.css'

/** タブ表示（GameView）ではページ見出しをタブ側が持つため、この見出しは出さない。 */
const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

/** 対局モード。duo = 2人対戦、cpu = 白を CPU が打つ（あなたは黒）。 */
type RevMode = 'duo' | 'cpu'

/** CPU が打つまでの待ち時間（ms）。 */
const CPU_DELAY_MS = 250
/** 裏返りアニメーション（--duration-base = 200ms）が見える程度に残す時間（ms）。 */
const FLIP_ANIMATION_MS = 300

/** 矢印キー 1 回あたりの移動量（行・列）。 */
const KEY_DELTAS: Partial<Record<string, readonly [number, number]>> = {
  ArrowUp: [-1, 0],
  ArrowDown: [1, 0],
  ArrowLeft: [0, -1],
  ArrowRight: [0, 1]
}

/** 1 手分の記録（「待った」で盤面を戻すために使う）。 */
interface RevHistoryEntry {
  /** 置いたマス。 */
  index: number
  /** 置いた色。 */
  disc: Disc
  /** その手で裏返ったマス。 */
  flips: number[]
}

const route = useRoute()
const toast = useToast()
const timer = useGameTimer()

const size = REVERSI_SIZE
const cells = ref<(Disc | null)[]>(createReversi(size))
const turn = ref<Disc>('black')
const mode = ref<RevMode>('duo')
/** 対局が終わったか。 */
const over = ref(false)
/** 合法手のヒントを表示するか（クリック自体は OFF でも合法手のみ受け付ける）。 */
const showHints = ref(true)
const history = ref<RevHistoryEntry[]>([])
/** 直前の操作で裏返ったマス（アニメーション用。すぐ外す）。 */
const flipping = ref<number[]>([])
const boardRef = ref<HTMLElement | null>(null)

/** CPU の着手予約と裏返りアニメーションの後始末用（画面を閉じるときに破棄する）。 */
let cpuHandle: ReturnType<typeof setTimeout> | null = null
let flipHandle: ReturnType<typeof setTimeout> | null = null

const area = computed(() => route.path.split('/')[1] ?? 'student')
const gameHomePath = computed(() => `/${area.value}/game`)

const counts = computed(() => countDiscs(cells.value))
const total = computed(() => counts.value.black + counts.value.white)
const legalIndexes = computed(() => (over.value ? [] : legalMoves(cells.value, size, turn.value)))
const hintIndexes = computed(() => (showHints.value ? legalIndexes.value : []))

/** 盤面カードの補足表示（終局後は手番ではなく終了を出す）。 */
const boardSub = computed(() => {
  const head = `${size} × ${size}`
  return over.value ? `${head} ／ 対局終了` : `${head} ／ 手番 ${discName(turn.value)}`
})

const statusText = computed(() => {
  const result = counts.value
  if (over.value) {
    if (result.black === result.white) {
      return {
        title: '引き分け',
        text: `石数が同じでした（黒 ${result.black} 対 白 ${result.white}）。`,
        modifier: ''
      }
    }
    const blackWins = result.black > result.white
    const title = mode.value === 'cpu'
      ? (blackWins ? 'あなたの勝ち！' : 'CPU の勝ち')
      : `${blackWins ? '黒' : '白'}の勝ち！`
    return {
      title,
      text: `黒 ${result.black} 対 白 ${result.white}（全 ${total.value} マス・${timer.clock}）。`,
      modifier: blackWins ? 'is-win' : 'is-lose'
    }
  }
  return {
    title: mode.value === 'cpu'
      ? (turn.value === 'black' ? 'あなたの番（黒）' : 'CPU の番（白）')
      : `${discName(turn.value)}の番`,
    text: `置けるのは ${legalIndexes.value.length} 箇所です。`,
    modifier: 'is-play'
  }
})

function discName(disc: Disc): string {
  return disc === 'black' ? '黒' : '白'
}

/** 盤面に石を置く（合法手のみ）。手番の移動・パス・終局判定まで面倒を見る。 */
function play(index: number, disc: Disc): void {
  const flips = applyMove(cells.value, size, index, disc)
  if (flips.length === 0) return
  if (history.value.length === 0) timer.start()
  history.value = [...history.value, { index, disc, flips }]
  flashFlips(flips)
  advanceTurn(disc)
}

/** 手番を進める。相手が打てなければパス、両者とも打てなければ終局。 */
function advanceTurn(disc: Disc): void {
  const next = otherDisc(disc)
  if (isGameOver(cells.value, size)) {
    finish()
    return
  }
  if (legalMoves(cells.value, size, next).length > 0) {
    turn.value = next
  } else {
    // 打てる手がない側はパス（両者とも打てない場合は上で終局している）
    toast.info(`${discName(next)}は打てる手がないためパスしました。`)
    turn.value = disc
  }
  maybeScheduleCpu()
}

/** 終局。石数で勝敗を通知する（同数は引き分け）。 */
function finish(): void {
  over.value = true
  timer.stop()
  const result = countDiscs(cells.value)
  if (result.black === result.white) {
    toast.info('引き分けです。')
  } else if (mode.value === 'cpu') {
    if (result.black > result.white) toast.success('あなたの勝ちです！')
    else toast.warning('CPU の勝ちです。')
  } else {
    toast.success(`${result.black > result.white ? '黒' : '白'}の勝ちです！`)
  }
}

/** 直前の手で裏返った石にアニメーションを付け、少し経ったら外す。 */
function flashFlips(flips: number[]): void {
  if (flipHandle !== null) clearTimeout(flipHandle)
  flipping.value = [...flips]
  flipHandle = setTimeout(() => {
    flipHandle = null
    flipping.value = []
  }, FLIP_ANIMATION_MS)
}

function clearCpu(): void {
  if (cpuHandle !== null) {
    clearTimeout(cpuHandle)
    cpuHandle = null
  }
}

/** CPU モードで白の手番なら、少し待ってから自動で打つ。 */
function maybeScheduleCpu(): void {
  clearCpu()
  if (mode.value !== 'cpu' || over.value || turn.value !== 'white') return
  cpuHandle = setTimeout(() => {
    cpuHandle = null
    if (over.value || turn.value !== 'white') return
    const move = chooseCpuMove(cells.value, size, 'white')
    if (move >= 0) play(move, 'white')
  }, CPU_DELAY_MS)
}

function onCellClick(index: number): void {
  if (over.value) return
  // CPU の番（白）の間は人間の操作を受け付けない
  if (mode.value === 'cpu' && turn.value === 'white') return
  if (!legalIndexes.value.includes(index)) return
  play(index, turn.value)
}

function newGame(): void {
  clearCpu()
  cells.value = createReversi(size)
  turn.value = 'black'
  history.value = []
  flipping.value = []
  over.value = false
  timer.reset()
}

/** モードを切り替えたら新しい対局を始める。 */
function onChangeMode(): void {
  newGame()
}

/** 1 手分（CPU モードでは 2 手分）戻す。 */
function undoOne(): boolean {
  const last = history.value[history.value.length - 1]
  if (!last) return false
  cells.value[last.index] = null
  for (const position of last.flips) cells.value[position] = otherDisc(last.disc)
  history.value = history.value.slice(0, -1)
  turn.value = last.disc
  return true
}

function undo(): void {
  if (history.value.length === 0) {
    toast.warning('戻せる手がありません。')
    return
  }
  clearCpu()
  const steps = mode.value === 'cpu' ? 2 : 1
  for (let step = 0; step < steps; step += 1) {
    if (!undoOne()) break
  }
  over.value = false
  flipping.value = []
  if (history.value.length === 0) timer.reset()
  else timer.start()
  // 戻した結果が CPU の番なら、そのまま打ってもらう
  maybeScheduleCpu()
}

function toggleHints(): void {
  showHints.value = !showHints.value
}

/** マスをフォーカスする（矢印キーでの移動に使う）。 */
function focusCell(index: number): void {
  const buttons = boardRef.value?.querySelectorAll<HTMLButtonElement>('.gm-rev-cell')
  buttons?.[index]?.focus()
}

/** 矢印キーで隣のマスへフォーカスを移す。 */
function onCellKeydown(event: KeyboardEvent, index: number): void {
  const delta = KEY_DELTAS[event.key]
  if (!delta) return
  event.preventDefault()
  const row = Math.floor(index / size) + delta[0]
  const col = (index % size) + delta[1]
  if (row < 0 || row >= size || col < 0 || col >= size) return
  focusCell(row * size + col)
}

function cellLabel(disc: Disc | null, index: number): string {
  const row = Math.floor(index / size) + 1
  const col = (index % size) + 1
  const head = `${row}行${col}列`
  if (disc === 'black') return `${head} 黒石`
  if (disc === 'white') return `${head} 白石`
  return legalIndexes.value.includes(index) ? `${head} 空き 打てます` : `${head} 空き`
}

/** 画面を閉じるときに予約したタイマーを破棄する。 */
onBeforeUnmount(() => {
  clearCpu()
  if (flipHandle !== null) clearTimeout(flipHandle)
})
</script>

<template>
  <div class="gm-page">
    <div v-if="!props.embedded" class="gm-head">
      <RouterLink class="btn btn--ghost btn--sm" :to="gameHomePath">
        <AppIcon name="chevron-left" size="sm" /> ゲーム一覧
      </RouterLink>
      <h2 class="gm-head__title"><AppIcon name="check-circle" /> リバーシ</h2>
      <p class="gm-head__desc">
        相手の石を自分の石で挟んで裏返します。最後に石が多い方の勝ちです。
      </p>
    </div>

    <div class="gm-layout">
      <section class="card">
        <div class="card__header">
          <h3 class="card__title">盤面</h3>
          <span class="card__sub">{{ boardSub }}</span>
        </div>
        <div class="card__body gm-board-body">
          <div
            ref="boardRef"
            class="gm-rev"
            role="group"
            :style="{ gridTemplateColumns: `repeat(${size}, var(--gm-cell))` }"
            :aria-label="`リバーシの盤面 ${size}行${size}列`"
          >
            <button
              v-for="(disc, index) in cells" :key="index"
              type="button" class="gm-rev-cell"
              :class="{ 'is-legal': hintIndexes.includes(index) }"
              :aria-label="cellLabel(disc, index)"
              :disabled="over"
              @click="onCellClick(index)"
              @keydown="onCellKeydown($event, index)"
            >
              <span
                v-if="disc"
                class="gm-rev-disc"
                :class="[`gm-rev-disc--${disc}`, { 'is-flipping': flipping.includes(index) }]"
              />
              <span v-else-if="hintIndexes.includes(index)" class="gm-rev-hint" />
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
                <dt class="gm-metric__label">黒</dt>
                <dd class="gm-metric__value">{{ counts.black }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">白</dt>
                <dd class="gm-metric__value">{{ counts.white }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">手番</dt>
                <dd class="gm-metric__value">{{ over ? '—' : discName(turn) }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">経過時間</dt>
                <dd class="gm-metric__value">{{ timer.clock }}</dd>
              </div>
            </dl>

            <div class="gm-controls">
              <div class="gm-toolbar">
                <label class="field field--inline">
                  <span class="field__label">モード</span>
                  <select v-model="mode" class="select" @change="onChangeMode">
                    <option value="duo">2人対戦</option>
                    <option value="cpu">CPU（あなた=黒）</option>
                  </select>
                </label>
              </div>

              <div class="gm-actions">
                <button type="button" class="btn btn--primary" @click="newGame">
                  <AppIcon name="plus" size="sm" /> 新しい対局
                </button>
                <button type="button" class="btn btn--secondary" @click="undo">
                  <AppIcon name="rotate" size="sm" /> 待った
                </button>
                <button
                  type="button" class="btn btn--secondary"
                  :aria-pressed="showHints"
                  @click="toggleHints"
                >
                  <AppIcon name="eye" size="sm" /> ヒント表示 {{ showHints ? 'ON' : 'OFF' }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <GameHelpCard slug="reversi" />
      </aside>
    </div>
  </div>
</template>
