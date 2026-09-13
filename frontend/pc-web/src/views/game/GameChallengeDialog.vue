<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import type { GameMatchState } from '@/features/game/useGameMatch'
import type { ApiStone } from '@/api/games'
import '@/features/game/game-match.css'

/**
 * 対戦の申し込みの小窓（2人対戦で【新しい対局】を押すと開く）。
 *
 * ・相手と先手／後手を選んで【対戦を申し込む】（ユーザーの指定 ②）。
 * ・申し込んでもこの小窓は閉じない。返事を待っていることを出し、応戦されたら自動で閉じる（⑥）。
 * ・相手がオフラインのときはその旨を出す（③）。
 * ・応戦しなかったときはその旨を出す（⑤）。
 */
const props = defineProps<{
  state: GameMatchState
  /** ゲーム名（五目並べ／黑白棋）。 */
  gameLabel: string
}>()

const opponentId = ref(0)
const challengerStone = ref<ApiStone>('BLACK')

onMounted(() => {
  void props.state.loadOpponents()
})

// 相手が読み込まれたら最初の 1 人を選んでおく（申し込みを 1 クリックで済ませる）
watch(() => props.state.opponents.value, (rows) => {
  if (opponentId.value === 0 && rows.length > 0) {
    opponentId.value = rows[0]?.accountId ?? 0
  }
}, { immediate: true })

const opponents = computed(() => props.state.opponents.value)
const waiting = computed(() => props.state.challengeWaiting.value)
const message = computed(() => props.state.challengeMessage.value)

function onlineLabel(online: boolean): string {
  return online ? 'オンライン' : 'オフライン'
}

function submit(): void {
  void props.state.invite(opponentId.value, challengerStone.value)
}
</script>

<template>
  <div class="overlay">
    <section
      class="dialog dialog--sm gm-challenge" role="dialog" aria-modal="true"
      aria-labelledby="gmChallengeTitle" data-challenge-dialog
    >
      <header class="dialog__head">
        <h2 id="gmChallengeTitle" class="dialog__title">
          <AppIcon name="user" size="sm" /> 対戦の申し込み
        </h2>
        <button type="button" class="dialog__close" aria-label="閉じる" @click="state.closeChallenge()">
          <AppIcon name="x" size="sm" />
        </button>
      </header>

      <div class="dialog__body">
        <p class="gm-challenge__game">{{ gameLabel }}で対戦します。</p>

        <!-- ③⑤ 相手の状態・返事 -->
        <p v-if="message !== ''" class="alert alert--danger" data-challenge-message>{{ message }}</p>

        <!-- ② 申し込みの形（返事待ちの間は隠す） -->
        <div v-if="!waiting" class="gm-challenge__form">
          <label class="field">
            <span class="field__label">対戦する相手</span>
            <select v-model.number="opponentId" class="select" aria-label="対戦する相手" data-challenge-opponent>
              <option v-if="opponents.length === 0" :value="0">対戦できる相手がいません</option>
              <option v-for="row in opponents" :key="row.accountId" :value="row.accountId">
                {{ row.displayName }}（{{ onlineLabel(row.online) }}）
              </option>
            </select>
          </label>

          <label class="field">
            <span class="field__label">先手・後手</span>
            <select v-model="challengerStone" class="select" aria-label="先手・後手" data-challenge-stone>
              <option value="BLACK">先手（黒）</option>
              <option value="WHITE">後手（白）</option>
            </select>
          </label>

          <p class="gm-challenge__note">
            相手がゲーム画面を開いていないと申し込めません（そのときは相手の名前の横が「オフライン」です）。
          </p>
        </div>

        <!-- ② 返事待ち（この小窓は閉じない） -->
        <div v-else class="gm-challenge__waiting" data-challenge-waiting>
          <p class="gm-challenge__waiting-text">
            <AppIcon name="clock" size="sm" /> 相手の返事を待っています…
          </p>
          <p class="cell-muted">
            相手の画面に「応戦する／しない」が出ています。応戦されるとこの画面は閉じて対戦が始まります。
          </p>
        </div>
      </div>

      <footer class="dialog__foot">
        <button v-if="waiting" type="button" class="btn btn--secondary" @click="state.closeChallenge()">
          <AppIcon name="x" size="sm" /> 申し込みを取り消す
        </button>
        <template v-else>
          <button type="button" class="btn btn--secondary" @click="state.closeChallenge()">閉じる</button>
          <button
            type="button" class="btn btn--primary" :disabled="state.busy.value || opponents.length === 0"
            data-challenge-submit @click="submit"
          >
            <AppIcon name="user" size="sm" /> 対戦を申し込む
          </button>
        </template>
      </footer>
    </section>
  </div>
</template>
