<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import GameHelpCard from '@/views/game/GameHelpCard.vue'
import {
  REVERSI_SIZE, applyMove, chooseCpuMove, countDiscs, createReversi, isGameOver, legalMoves,
  otherDisc, type Disc
} from '@/features/game/reversi'
import GameChallengeDialog from '@/views/game/GameChallengeDialog.vue'
import GameInvitationDialog from '@/views/game/GameInvitationDialog.vue'
import { useGameMatch } from '@/features/game/useGameMatch'
import { resultLabel } from '@/features/game/gameMatchLabels'
import { useGameTimer } from '@/features/game/useGameTimer'
import '@/features/game/game.css'

/** タブ表示（GameView）ではページ見出しをタブ側が持つため、この見出しは出さない。 */
const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

/** 対局モード。duo = 2人対戦、cpu = 白を CPU が打つ（あなたは黒）。 */
/**
 * 対局モード。
 * ・'cpu'   = 人間（黒）対 CPU（白）※既定
 * ・'duo'   = 同じ画面で 2 人（交互に打つ）
 * ・'match' = ネット対戦（申し込みの小窓を開く）
 */
type RevMode = 'duo' | 'cpu' | 'match'

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
const localCells = ref<(Disc | null)[]>(createReversi(size))

/**
 * 遊び方。開いている対戦があれば 'match'（API の盤面）、無ければ 'solo'（ローカルの盤面）。
 * 対戦は申し込みの小窓 → 相手の応戦 → 盤面が開く、という流れで始まる。
 */
const matchState = useGameMatch('REVERSI')
const playMode = computed<'solo' | 'match'>(() => (matchState.match.value === null ? 'solo' : 'match'))

/** 終わった対局の結果（自分の立場から見た勝ち負け）。 */
const resultLabelOfMatch = computed(() => {
  const current = matchState.match.value
  return current === null ? '' : resultLabel(current)
})

/** 盤面に出すマス。対戦中は API の盤面、それ以外はローカルの盤面。 */
const cells = computed<(Disc | null)[]>(() => {
  if (playMode.value !== 'match') return localCells.value
  const board = matchState.match.value?.board
  if (!board) return createReversi(size)
  return board.flat().map((cell) => (cell === null ? null : (cell.toLowerCase() as Disc)))
})

/** 対戦中に強調するマス（直前の手・返した石・成立ラインは無い）。 */
const matchLast = computed(() => {
  const moves = matchState.match.value?.moves ?? []
  const last = moves[moves.length - 1]
  if (playMode.value !== 'match' || last === undefined || last.row === null || last.col === null) return -1
  return last.row * size + last.col
})
const matchFlipped = computed(() => {
  const moves = matchState.match.value?.moves ?? []
  const last = moves[moves.length - 1]
  if (playMode.value !== 'match' || last === undefined) return [] as number[]
  return last.flipped.map(([row, col]) => row * size + col)
})

/** 対戦中の状態（相手の手番は待ちであることをはっきり出す）。 */
const matchStatus = computed(() => {
  const match = matchState.match.value
  if (match === null) return ''
  if (match.status === 'WAITING') return match.myTurn ? '相手の返事を待っています。' : '申し込みが届いています。'
  if (match.status === 'FINISHED') return resultLabel(match)
  return match.myTurn ? 'あなたの手番です。' : '相手の手番です（相手が指すまでお待ちください）。'
})
const turn = ref<Disc>('black')
/** 盤面のマスの大きさ（px）。石が置きやすい大きさにする。 */
const CELL_SIZE_PX = 64

/** 選択欄のモード。対局に効くのは次の【新しい対局】から。既定は CPU（ユーザーの指定 ①）。 */
const mode = ref<RevMode>('cpu')
/** 対局中のモード（新しい対局を始めたときに選択欄の値へ更新する）。 */
const appliedMode = ref<'duo' | 'cpu'>(mode.value === 'match' ? 'cpu' : mode.value)
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
const legalIndexes = computed(() => {
  if (over.value) return []
  if (playMode.value === 'match') return []   // 対戦はサーバーが合法手を判定する
  return legalMoves(cells.value, size, turn.value)
})
const hintIndexes = computed(() => (showHints.value ? legalIndexes.value : []))

/** 盤面カードの補足表示（終局後は手番ではなく終了を出す）。 */
const boardSub = computed(() => {
  const head = `${size} × ${size}`
  if (playMode.value === 'match') {
    const match = matchState.match.value
    if (match === null) return `${head} ／ 対戦`
    if (match.status === 'FINISHED') return `${head} ／ 対局終了`
    return `${head} ／ 手番 ${match.myTurn ? 'あなた' : '相手'}`
  }
  return over.value ? `${head} ／ 対局終了` : `${head} ／ 手番 ${discName(turn.value)}`
})

const statusText = computed(() => {
  if (playMode.value === 'match') {
    const match = matchState.match.value
    return {
      title: match === null ? '対戦' : (match.myTurn ? 'あなたの手番' : '相手の手番'),
      text: matchStatus.value === '' ? '下の対戦パネルで相手を選んで申し込んでください。' : matchStatus.value,
      modifier: match?.status === 'FINISHED' ? 'is-win' : 'is-play'
    }
  }
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
    const title = appliedMode.value === 'cpu'
      ? (blackWins ? 'あなたの勝ち！' : 'CPU の勝ち')
      : `${blackWins ? '黒' : '白'}の勝ち！`
    return {
      title,
      text: `黒 ${result.black} 対 白 ${result.white}（全 ${total.value} マス・${timer.clock}）。`,
      modifier: blackWins ? 'is-win' : 'is-lose'
    }
  }
  return {
    title: appliedMode.value === 'cpu'
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
  const flips = applyMove(localCells.value, size, index, disc)
  if (flips.length === 0) return
  if (history.value.length === 0) timer.start()
  history.value = [...history.value, { index, disc, flips }]
  flashFlips(flips)
  advanceTurn(disc)
}

/** 手番を進める。相手が打てなければパス、両者とも打てなければ終局。 */
function advanceTurn(disc: Disc): void {
  const next = otherDisc(disc)
  if (isGameOver(localCells.value, size)) {
    finish()
    return
  }
  if (legalMoves(localCells.value, size, next).length > 0) {
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
  const result = countDiscs(localCells.value)
  if (result.black === result.white) {
    toast.info('引き分けです。')
  } else if (appliedMode.value === 'cpu') {
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
  if (appliedMode.value !== 'cpu' || over.value || turn.value !== 'white') return
  cpuHandle = setTimeout(() => {
    cpuHandle = null
    if (over.value || turn.value !== 'white') return
    const move = chooseCpuMove(localCells.value, size, 'white')
    if (move >= 0) play(move, 'white')
  }, CPU_DELAY_MS)
}

function onCellClick(index: number): void {
  // 対戦モードはサーバーに着手を送る（返し・パス・勝敗の判定もサーバー側）
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
    if (cells.value[index] !== null) return
    void matchState.playMove(Math.floor(index / size), index % size)
    return
  }
  if (over.value) return
  // CPU の番（白）の間は人間の操作を受け付けない
  if (appliedMode.value === 'cpu' && turn.value === 'white') return
  if (!legalIndexes.value.includes(index)) return
  play(index, turn.value)
}

/**
 * 新しい対局。【新しい対局】の動き（ユーザーの指定 ①）。
 * ・CPU: いままでどおりその場で対局を始める
 * ・2人対戦: 申し込みの小窓を開く（ネット対戦の申し込み）
 */
function newGame(): void {
  const selected = mode.value
  if (selected === 'match') {
    // ネット対戦: 申し込みの小窓を開く（対局は相手が応戦してから始まる）
    matchState.openChallenge()
    return
  }
  appliedMode.value = selected
  clearCpu()
  localCells.value = createReversi(size)
  turn.value = 'black'
  history.value = []
  flipping.value = []
  over.value = false
  timer.reset()
}

/** モードを切り替えたら新しい対局を始める。 */

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
  const steps = appliedMode.value === 'cpu' ? 2 : 1
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

// 画面を開いたら一覧・相手・SSE を用意する（申し込みと応戦の通知を受けるため）
onMounted(() => {
  void matchState.initialize()
})

// 盤面が入れ替わったら（対戦が始まった・閉じた）CPU の予約と時計を整える
watch(playMode, (value) => {
  clearCpu()
  if (value === 'solo') timer.reset()
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
          <!-- 対戦中は手番・結果を盤面の上にも出す -->
          <p v-if="playMode === 'match'" class="gm-match-board__turn">
            <strong>{{ statusText.title }}</strong><span>{{ statusText.text }}</span>
          </p>
          <div
            ref="boardRef"
            class="gm-rev"
            role="group"
            :style="{
              gridTemplateColumns: `repeat(${size}, var(--gm-cell))`,
              '--gm-cell': `${CELL_SIZE_PX}px`
            }"
            :aria-label="`リバーシの盤面 ${size}行${size}列`"
          >
            <button
              v-for="(disc, index) in cells" :key="index"
              type="button" class="gm-rev-cell"
              :class="{
                'is-legal': hintIndexes.includes(index),
                'is-match-last': index === matchLast,
                'is-match-flipped': matchFlipped.includes(index)
              }"
              :aria-label="cellLabel(disc, index)"
              :disabled="playMode === 'solo' && over"
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
                <dt class="gm-metric__label">黒</dt>
                <dd class="gm-metric__value">{{ counts.black }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">白</dt>
                <dd class="gm-metric__value">{{ counts.white }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">手番</dt>
                <dd class="gm-metric__value">
                  {{ playMode === 'match'
                    ? (matchState.match.value?.myTurn ? 'あなた' : '相手')
                    : (over ? '—' : discName(turn)) }}
                </dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">経過時間</dt>
                <dd class="gm-metric__value">{{ timer.clock }}</dd>
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
                    <option value="duo">2人対戦（同じ画面）</option>
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
                <button v-if="playMode === 'solo'" type="button" class="btn btn--secondary" @click="undo">
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

    <!-- 対戦（一人で遊ぶ / 対戦 の切替・申し込み・対戦一覧） -->
    <!-- 申し込みの小窓（2人対戦で【新しい対局】を押したとき） -->
    <GameChallengeDialog v-if="matchState.challengeOpen.value" :state="matchState" game-label="黒白棋" />

    <!-- 相手からの申し込み（応戦ダイアログ） -->
    <GameInvitationDialog :state="matchState" game-label="黒白棋" />
  </div>
</template>
