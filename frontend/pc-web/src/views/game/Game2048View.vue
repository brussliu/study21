<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import GameHelpCard from '@/views/game/GameHelpCard.vue'
import {
  BOARD_2048_SIZE, TARGET_2048, cloneBoard, createBoard2048, maxTile, move,
  type Board2048, type Direction2048, type MoveResult
} from '@/features/game/game2048'
import { useGameTimer } from '@/features/game/useGameTimer'
import '@/features/game/game.css'

/** 盤面のマスの大きさ（px）。数字が読みやすい大きさにする。 */
const CELL_SIZE_PX = 100

/** タブ表示（GameView）ではページ見出しをタブ側が持つため、この見出しは出さない。 */
const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

const route = useRoute()
const toast = useToast()
const timer = useGameTimer()

/** 出現・合成のアニメーションを付けている時間（ミリ秒）。 */
const HIGHLIGHT_MS = 220

/** キー入力と方向の対応（小文字にした event.key で引く）。 */
const KEY_DIRECTIONS: Record<string, Direction2048> = {
  arrowup: 'up',
  arrowdown: 'down',
  arrowleft: 'left',
  arrowright: 'right',
  w: 'up',
  s: 'down',
  a: 'left',
  d: 'right'
}

const board = ref<Board2048>(createBoard2048())
/** 「元に戻す」用の 1 手前までの盤面（古い順）。 */
const history = ref<Board2048[]>([])
/** このセッション（画面を開いている間）のベストスコア。 */
const best = ref(0)
const newIndex = ref<number | null>(null)
const mergedIndexes = ref<number[]>([])
let highlightHandle: ReturnType<typeof setTimeout> | null = null

const area = computed(() => route.path.split('/')[1] ?? 'student')
const gameHomePath = computed(() => `/${area.value}/game`)
const topTile = computed(() => maxTile(board.value.cells))
const canUndo = computed(() => history.value.length > 0)

const statusText = computed(() => {
  if (board.value.over) {
    return {
      title: 'ゲームオーバー',
      text: 'これ以上動かせません。「新しいゲーム」でやり直せます。',
      modifier: 'is-lose'
    }
  }
  if (board.value.reached) {
    return {
      title: '2048 達成！',
      text: 'そのまま続けて、より大きなタイルを目指せます。',
      modifier: 'is-win'
    }
  }
  return { title: 'プレイ中', text: '矢印キーまたは WASD でタイルを動かします。', modifier: 'is-play' }
})

/** 盤面全体の説明（スクリーンリーダー用）。 */
const boardLabel = computed(() => `2048 の盤面。スコア ${board.value.score}`)

/** 出現・合体のアニメーション用クラスを外す。 */
function clearHighlight(): void {
  if (highlightHandle !== null) {
    clearTimeout(highlightHandle)
    highlightHandle = null
  }
  newIndex.value = null
  mergedIndexes.value = []
}

/**
 * 出現・合体したマスにアニメーション用クラスを 1 描画だけ付ける
 * （CSS アニメーションを毎回再生させるため、すぐ外す）。
 */
function showHighlight(result: MoveResult): void {
  clearHighlight()
  mergedIndexes.value = result.merged
  newIndex.value = result.spawned
  highlightHandle = setTimeout(() => {
    highlightHandle = null
    newIndex.value = null
    mergedIndexes.value = []
  }, HIGHLIGHT_MS)
}

/** タイルを動かす。動いたときだけ手数・スコア・タイマーを進める。 */
function playMove(direction: Direction2048): void {
  const current = board.value
  if (current.over) return

  const before = cloneBoard(current)
  const wasReached = current.reached
  const result = move(current, direction)
  if (!result.moved) return

  history.value.push(before)
  best.value = Math.max(best.value, current.score)
  showHighlight(result)

  if (current.moves === 1) timer.start()
  if (current.reached && !wasReached) toast.success('2048 に到達しました！')
  if (current.over) {
    timer.stop()
    toast.danger('これ以上動かせません。')
  }
}

/** 1 手戻す（盤面とスコアを直前の状態に戻す）。 */
function undo(): void {
  const previous = history.value.pop()
  if (!previous) return

  board.value = cloneBoard(previous)
  clearHighlight()
  // ゲームオーバーから戻したときは計測を再開する
  if (!board.value.over && board.value.moves > 0 && !timer.running) timer.start()
}

/** 新しいゲームを始める（ベストスコアはこの画面を開いている間は残る）。 */
function newGame(): void {
  board.value = createBoard2048()
  history.value = []
  clearHighlight()
  timer.reset()
}

/** 矢印キー・WASD で操作する（スクロールを止めるため preventDefault）。 */
function onKeydown(event: KeyboardEvent): void {
  if (event.ctrlKey || event.altKey || event.metaKey) return
  const target = event.target
  if (target instanceof HTMLElement && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) {
    return
  }
  const direction = KEY_DIRECTIONS[event.key.toLowerCase()]
  if (!direction) return

  event.preventDefault()
  playMove(direction)
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  clearHighlight()
})
</script>

<template>
  <div class="gm-page">
    <div v-if="!props.embedded" class="gm-head">
      <RouterLink class="btn btn--ghost btn--sm" :to="gameHomePath">
        <AppIcon name="chevron-left" size="sm" /> ゲーム一覧
      </RouterLink>
      <h2 class="gm-head__title"><AppIcon name="sigma" /> 2048</h2>
      <p class="gm-head__desc">
        同じ数字のタイルをぶつけて合体させ、2048 のタイルを作るパズルです。
        矢印キーまたは WASD でタイルを動かします。
      </p>
    </div>

    <div class="gm-layout">
      <section class="card">
        <div class="card__header">
          <h3 class="card__title">盤面</h3>
          <span class="card__sub">{{ BOARD_2048_SIZE }} × {{ BOARD_2048_SIZE }} ／ 目標 {{ TARGET_2048 }}</span>
        </div>
        <div class="card__body gm-board-body">
          <div
            class="gm-g2048" role="group"
            :style="{ '--gm-cell': `${CELL_SIZE_PX}px` }"
            :aria-label="boardLabel"
          >
            <div
              v-for="(value, index) in board.cells" :key="index"
              class="gm-g2048-cell"
              :class="{ 'is-new': newIndex === index, 'is-merged': mergedIndexes.includes(index) }"
              :data-value="value > 0 ? value : undefined"
            >
              {{ value > 0 ? value : '' }}
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
                <dt class="gm-metric__label">スコア</dt>
                <dd class="gm-metric__value">{{ board.score }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">ベスト</dt>
                <dd class="gm-metric__value">{{ best }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">最大タイル</dt>
                <dd class="gm-metric__value">{{ topTile }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">手数</dt>
                <dd class="gm-metric__value">{{ board.moves }}</dd>
              </div>
              <div class="gm-metric">
                <dt class="gm-metric__label">経過時間</dt>
                <dd class="gm-metric__value">{{ timer.clock }}</dd>
              </div>
            </dl>

            <div class="gm-controls">
              <div class="gm-toolbar">
                <button
                  type="button" class="btn btn--secondary" aria-label="上に動かす"
                  :disabled="board.over" @click="playMove('up')"
                >
                  ↑
                </button>
                <button
                  type="button" class="btn btn--secondary" aria-label="下に動かす"
                  :disabled="board.over" @click="playMove('down')"
                >
                  ↓
                </button>
                <button
                  type="button" class="btn btn--secondary" aria-label="左に動かす"
                  :disabled="board.over" @click="playMove('left')"
                >
                  ←
                </button>
                <button
                  type="button" class="btn btn--secondary" aria-label="右に動かす"
                  :disabled="board.over" @click="playMove('right')"
                >
                  →
                </button>
              </div>

              <div class="gm-actions">
                <button type="button" class="btn btn--primary" @click="newGame">
                  <AppIcon name="plus" size="sm" /> 新しいゲーム
                </button>
                <button type="button" class="btn btn--secondary" :disabled="!canUndo" @click="undo">
                  <AppIcon name="rotate" size="sm" /> 元に戻す
                </button>
              </div>
            </div>
          </div>
        </div>

        <GameHelpCard slug="2048" />
      </aside>
    </div>
  </div>
</template>
