<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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
import GameChallengeDialog from '@/views/game/GameChallengeDialog.vue'
import GameInvitationDialog from '@/views/game/GameInvitationDialog.vue'
import { useGameMatch } from '@/features/game/useGameMatch'
import { resultLabel } from '@/features/game/gameMatchLabels'
import { useGameTimer } from '@/features/game/useGameTimer'
import '@/features/game/game.css'

/** タブ表示（GameView）ではページ見出しをタブ側が持つため、この見出しは出さない。 */
const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

/**
 * 対局モード。
 * ・'cpu'   = 人間（黒）対 CPU（白）※既定
 * ・'local' = 同じ画面で 2 人（交互に打つ）
 * ・'match' = ネット対戦（申し込みの小窓を開く）
 */
type SoloMode = 'cpu' | 'local'
type GameMode = SoloMode | 'match'

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
/** 盤面のマスの大きさ（px）。石が置きやすい大きさにする。 */
const CELL_SIZE_PX = 40

/** 選択欄のモード。対局に効くのは次の【新しい対局】から。既定は CPU（ユーザーの指定 ①）。 */
const mode = ref<GameMode>('cpu')
/** 対局中のモード（新しい対局を始めたときに選択欄の値へ更新する）。 */
const appliedMode = ref<SoloMode>(mode.value === 'match' ? 'cpu' : mode.value)
const localCells = ref<(Stone | null)[]>(createGomoku(size))
/** 着手した順番（待った と 手数 に使う）。 */
const history = ref<number[]>([])
/** 勝利した一線のマス（無ければ空）。 */
const winLine = ref<number[]>([])
const winner = ref<Stone | null>(null)
const drawn = ref(false)
const thinking = ref(false)

/**
 * 遊び方。開いている対戦があれば 'match'（API の盤面）、無ければ 'solo'（ローカルの盤面）。
 * 対戦は申し込みの小窓 → 相手の応戦 → 盤面が開く、という流れで始まる。
 */
const matchState = useGameMatch('GOMOKU')
const playMode = computed<'solo' | 'match'>(() => (matchState.match.value === null ? 'solo' : 'match'))

/** 終わった対局の結果（自分の立場から見た勝ち負け）。 */
const resultLabelOfMatch = computed(() => {
  const current = matchState.match.value
  return current === null ? '' : resultLabel(current)
})

/** 盤面に出すマス。対戦中は API の盤面、それ以外はローカルの盤面。 */
const cells = computed<(Stone | null)[]>(() => {
  if (playMode.value !== 'match') return localCells.value
  const board = matchState.match.value?.board
  if (!board) return createGomoku(size)
  return board.flat().map((cell) => (cell === null ? null : (cell.toLowerCase() as Stone)))
})

/** 対戦中に強調するマス（直前の手・成立ライン）。 */
const matchLast = computed(() => {
  const moves = matchState.match.value?.moves ?? []
  const last = moves[moves.length - 1]
  if (playMode.value !== 'match' || last === undefined || last.row === null || last.col === null) return -1
  return last.row * size + last.col
})
const matchLine = computed(() => {
  const moves = matchState.match.value?.moves ?? []
  const last = moves[moves.length - 1]
  if (playMode.value !== 'match' || last === undefined) return [] as number[]
  return last.line.map(([row, col]) => row * size + col)
})

/** 対戦中の状態（相手の手番は待ちであることをはっきり出す）。 */
const matchStatus = computed(() => {
  const match = matchState.match.value
  if (match === null) return ''
  if (match.status === 'WAITING') return match.myTurn ? '相手の返事を待っています。' : '申し込みが届いています。'
  if (match.status === 'FINISHED') return resultLabel(match)
  return match.myTurn ? 'あなたの手番です。' : '相手の手番です（相手が指すまでお待ちください）。'
})

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
  (appliedMode.value === 'cpu' ? helpExtras.value.legendWhiteCpu : helpExtras.value.legendWhite) ?? '白'
)

/** 黒が先手なので、着手数から手番が決まる。 */
const turn = computed<Stone>(() => (history.value.length % 2 === 0 ? 'black' : 'white'))
const finished = computed(() => playMode.value === 'match'
  ? matchState.match.value?.status === 'FINISHED'
  : winner.value !== null || drawn.value)
const lastIndex = computed(() => history.value[history.value.length - 1] ?? null)
const emptyCount = computed(() => size * size - history.value.length)

const statusText = computed(() => {
  if (playMode.value === 'match') {
    const match = matchState.match.value
    return {
      title: match === null ? '対戦' : (match.myTurn ? 'あなたの手番' : '相手の手番'),
      text: matchStatus.value === '' ? '下の対戦パネルで相手を選んで申し込んでください。' : matchStatus.value,
      modifier: match?.status === 'FINISHED' ? 'is-win' : 'is-play'
    }
  }
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

/**
 * 新しい対局。【新しい対局】の動き（ユーザーの指定 ①）。
 * ・CPU: いままでどおりその場で対局を始める
 * ・2人対戦: 申し込みの小窓を開く（ネット対戦の申し込み）
 */
function newGame(): void {
  cancelCpuMove()
  const selected = mode.value
  if (selected === 'match') {
    // ネット対戦: 申し込みの小窓を開く（対局は相手が応戦してから始まる）
    matchState.openChallenge()
    return
  }
  appliedMode.value = selected
  localCells.value = createGomoku(size)
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
  const steps = appliedMode.value === 'cpu' ? Math.min(2, history.value.length) : 1
  for (let step = 0; step < steps; step += 1) {
    const index = history.value.pop()
    if (index === undefined) break
    localCells.value[index] = null
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
  if (!placeStone(localCells.value, size, index, stone)) return
  history.value.push(index)
  if (history.value.length === 1) timer.start()

  const line = findWinningLine(localCells.value, size, index, stone)
  if (line.length >= WIN_LENGTH) {
    winLine.value = line
    winner.value = stone
    timer.stop()
    toast.success(`${stoneLabel(stone)}の勝ちです！`)
    return
  }
  if (isBoardFull(localCells.value)) {
    drawn.value = true
    timer.stop()
    toast.info('盤面が埋まりました。引き分けです。')
    return
  }
  // CPU モードでは、人間（黒）が打ったあとに少し間を置いて CPU が打つ
  if (appliedMode.value === 'cpu' && stone === HUMAN_STONE) scheduleCpuMove()
}

/** CPU の着手を予約する（画面を離れるときに解除する）。 */
function scheduleCpuMove(): void {
  cancelCpuMove()
  thinking.value = true
  cpuHandle = setTimeout(() => {
    cpuHandle = null
    thinking.value = false
    if (finished.value || appliedMode.value !== 'cpu') return
    const index = chooseCpuMove(localCells.value, size, CPU_STONE)
    if (index < 0) return
    const row = Math.floor(index / size) + 1
    const col = (index % size) + 1
    toast.info(`CPU が ${row}行${col}列 に打ちました。`)
    applyMove(index, CPU_STONE)
  }, CPU_DELAY_MS)
}

function onCellClick(index: number): void {
  // 対戦モードはサーバーに着手を送る（勝敗の判定もサーバー側）
  if (playMode.value === 'match') {
    const match = matchState.match.value
    if (match === null) {
      toast.warning('対戦を選ぶか、申し込んでください。')
      return
    }
    if (match.status !== 'PLAYING') return
    if (!match.myTurn) {
      toast.warning('相手の手番です。')
      return
    }
    const cell = cells.value[index]
    if (cell !== null && cell !== undefined) return
    void matchState.playMove(Math.floor(index / size), index % size)
    return
  }
  if (finished.value) return
  if (appliedMode.value === 'cpu' && turn.value !== HUMAN_STONE) return // CPU の番はクリックできない
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

// 画面を開いたら一覧・相手・SSE を用意する（申し込みと応戦の通知を受けるため）
onMounted(() => {
  void matchState.initialize()
})

// 盤面が入れ替わったら（対戦が始まった・閉じた）CPU の予約と時計を整える
watch(playMode, (value) => {
  cancelCpuMove()
  if (value === 'solo') timer.reset()
})
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
          <!-- 対戦中は手番・結果を盤面の上にも出す -->
          <p v-if="playMode === 'match'" class="gm-match-board__turn">
            <strong>{{ statusText.title }}</strong><span>{{ statusText.text }}</span>
          </p>
          <div
            ref="boardEl"
            class="gm-gomoku"
            :style="{
              gridTemplateColumns: `repeat(${size}, var(--gm-cell))`,
              '--gm-cell': `${CELL_SIZE_PX}px`
            }"
            :aria-label="`五目並べの盤面 ${size}×${size}`"
          >
            <button
              v-for="(cell, index) in cells" :key="index"
              type="button" class="gm-gomoku-cell"
              :class="{
                'is-taken': cell !== null,
                'is-last': index === lastIndex,
                'is-win': winLine.includes(index),
                'is-match-last': index === matchLast,
                'is-match-line': matchLine.includes(index)
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
            <div
              class="gm-status" :class="statusText.modifier" role="status" aria-live="polite"
              :data-match-current="playMode === 'match' && matchState.match.value ? '' : undefined"
            >
              <span class="gm-status__title">{{ statusText.title }}</span>
              <span class="gm-status__text" data-match-board-status>{{ statusText.text }}</span>
            </div>

            <!-- 対戦中は相手の状態を出す（相手の接続が切れているときは赤で示す） -->
            <p
              v-if="playMode === 'match' && matchState.match.value"
              class="gm-opponent"
              :class="{ 'is-offline': !matchState.match.value.opponentOnline }"
              data-opponent-status
              aria-live="polite"
            >
              <AppIcon name="user" size="sm" />
              相手（{{ matchState.match.value.opponentName }}）：
              {{ matchState.match.value.opponentOnline ? 'オンライン' : 'オフライン' }}
            </p>

            <!-- 終わった対局は結果を出す -->
            <p
              v-if="playMode === 'match' && matchState.match.value?.status === 'FINISHED'"
              class="gm-match-result" data-match-result
            >
              {{ resultLabelOfMatch }}
            </p>

            <!-- 対局中の操作（投了／相手が不在なら終了／終わったら閉じる） -->
            <div v-if="playMode === 'match' && matchState.match.value" class="gm-actions gm-match-actions">
              <button
                v-if="matchState.match.value.status === 'PLAYING'"
                type="button" class="btn btn--secondary" :disabled="matchState.busy.value"
                data-match-resign @click="matchState.act('resign', matchState.match.value.matchId)"
              >
                <AppIcon name="x" size="sm" /> 投了
              </button>
              <button
                v-if="matchState.match.value.status === 'PLAYING' && !matchState.match.value.opponentOnline"
                type="button" class="btn btn--danger" :disabled="matchState.busy.value"
                data-match-timeout @click="matchState.timeout(matchState.match.value.matchId)"
              >
                <AppIcon name="check" size="sm" /> 終了
              </button>
              <button
                v-if="matchState.match.value.status === 'FINISHED'"
                type="button" class="btn btn--secondary"
                data-match-close @click="matchState.closeMatch()"
              >
                <AppIcon name="chevron-left" size="sm" /> 盤面を閉じる
              </button>
            </div>

            <dl class="gm-metrics">
              <div class="gm-metric">
                <dt class="gm-metric__label">手数</dt>
                <dd class="gm-metric__value">
                  {{ playMode === 'match' ? (matchState.match.value?.moveCount ?? 0) : history.length }}
                </dd>
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
              <div v-if="playMode === 'solo'" class="gm-toolbar">
                <label class="field field--inline">
                  <span class="field__label">モード</span>
                  <!-- 選んだモードは次の【新しい対局】から使う（対局中は変わらない）。 -->
                  <select v-model="mode" class="select" data-solo-mode>
                    <option value="cpu">CPU（あなた=黒）</option>
                    <option value="match">2人対戦（ネット対戦）</option>
                    <option value="local">2人対戦（同じ画面）</option>
                  </select>
                </label>
              </div>

              <div class="gm-actions">
                <button
                  v-if="playMode === 'solo'" type="button" class="btn btn--primary"
                  data-new-game @click="newGame"
                >
                  <AppIcon name="plus" size="sm" /> 新しい対局
                </button>
                <button
                  type="button" class="btn btn--secondary"
                  :disabled="history.length === 0 || playMode === 'match'" @click="undo"
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

    <!-- 申し込みの小窓（2人対戦で【新しい対局】を押したとき） -->
    <GameChallengeDialog v-if="matchState.challengeOpen.value" :state="matchState" game-label="五目並べ" />

    <!-- 相手からの申し込み（応戦ダイアログ） -->
    <GameInvitationDialog :state="matchState" game-label="五目並べ" />
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
