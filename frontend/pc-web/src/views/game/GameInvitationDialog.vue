<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import type { GameMatchState } from '@/features/game/useGameMatch'
import '@/features/game/game-match.css'

/**
 * 応戦ダイアログ（相手から申し込みが届いたときに相手の画面へ出る。ユーザーの指定 ④）。
 *
 * ・「応戦する」で対戦開始（⑥ 相手側もそのまま盤面が開く）。
 * ・「応戦しない」で断りを送る（⑤ 申請側に「応戦しませんでした」が出る）。
 * ・閉じても一覧には残るので、あとから受けることもできる。
 */
const props = defineProps<{
  state: GameMatchState
  /** ゲーム名（五目並べ／黑白棋）。 */
  gameLabel: string
}>()

const invitation = computed(() => props.state.incomingInvitation.value)

/** こちらが後手（白）か。申し込んだ人が先手（黒）なので、白なら後手になる。 */
const iAmSecond = computed(() => invitation.value?.myStone === 'WHITE')

function accept(): void {
  const row = invitation.value
  if (row === null) return
  void props.state.act('accept', row.matchId)
}

function decline(): void {
  const row = invitation.value
  if (row === null) return
  void props.state.act('decline', row.matchId)
}
</script>

<template>
  <div v-if="invitation !== null" class="overlay">
    <section
      class="dialog dialog--sm gm-challenge" role="dialog" aria-modal="true"
      aria-labelledby="gmInvitationTitle" data-invitation-dialog
    >
      <header class="dialog__head">
        <h2 id="gmInvitationTitle" class="dialog__title">
          <AppIcon name="user" size="sm" /> 対戦の申し込みが届きました
        </h2>
        <!-- あとで決めたいときは閉じられる（申し込みは一覧に残るので、そこから受けられる） -->
        <button
          type="button" class="dialog__close" aria-label="閉じる" data-invitation-close
          @click="state.dismissInvitation(invitation.matchId)"
        >
          <AppIcon name="x" size="sm" />
        </button>
      </header>

      <div class="dialog__body">
        <p class="gm-challenge__game">
          <strong>{{ invitation.opponentName }}</strong> さんから{{ gameLabel }}の対戦の申し込みです。
        </p>
        <p class="gm-challenge__note">
          あなたは{{ iAmSecond ? '後手（白）' : '先手（黒）' }}です。
          応戦するとすぐに対戦が始まります。
        </p>
      </div>

      <footer class="dialog__foot">
        <button
          type="button" class="btn btn--secondary" :disabled="state.busy.value"
          data-invitation-decline @click="decline"
        >
          <AppIcon name="x" size="sm" /> 応戦しない
        </button>
        <button
          type="button" class="btn btn--primary" :disabled="state.busy.value"
          data-invitation-accept @click="accept"
        >
          <AppIcon name="check" size="sm" /> 応戦する
        </button>
      </footer>
    </section>
  </div>
</template>
