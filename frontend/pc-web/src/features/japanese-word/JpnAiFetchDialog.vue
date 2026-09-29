<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import type { JpnAiStateKey } from '@/features/japanese-word/aiStateLabel'
import { JPN_ACQUIRE_ACTIONS } from '@/features/japanese-word/acquireActions'
import type { JpnAcquirePlan } from '@/features/japanese-word/acquirePlan'
import '@/features/japanese/japanese.css'

/**
 * **AI 取得の窓**（右上の取得ボタン 1 つを押すと出る）。
 *
 * <p>利用者の指示（2026-09-26）: 4 つ並んでいた取得ボタンは場所を取るので**1 つにまとめ**、
 * 押したら**窓の中でどの情報を取るかを選ぶ**。窓の中では 2 つを決める:</p>
 * <ol>
 *   <li><b>どの情報か</b> … 詳細情報（A・B）／読み問題（C）／文脈問題（D）／漢字問題（E）</li>
 *   <li><b>取得済みをどうするか</b> … 取得済みをスキップ／すべて再取得（2.0 の
 *       `word.jsp` の「取得方法」の窓と同じ 2 択）</li>
 * </ol>
 *
 * <p>件数は選んだ情報のものを出す（画面が区画ごとに `acquirePlan` で数えて渡す）。
 * 2.0 は「語義」単位だったが、2.1 は 1 語 = 1 回の AI 呼び出しなので**単語**単位で数える。</p>
 *
 * <p>**「すべて再取得」は上書きではない**: 2.1 の取得は毎回**新しい版**を作り、古い版は
 * 履歴に残る（一覧の取得状態のタグから切り戻せる）。だから文言もそう書く。</p>
 */

const props = defineProps<{
  /** 出しているか。 */
  visible: boolean
  /**
   * 何が対象かの説明（「表示中の 4 語」「この単語（勉強）」）。
   *
   * <p>窓は**一覧の右上**（表示中の語）と**行の操作**（その 1 語）の両方から開く。対象の件数だけでは
   * どちらか分からないので、説明は開いた側が作って渡す。</p>
   */
  targetLabel: string
  /** 区画ごとの計画（「取得済みをスキップ」と「すべて再取得」の両方。上限も入っている）。 */
  plans: Record<JpnAiStateKey, { skip: JpnAcquirePlan; all: JpnAcquirePlan }>
}>()

const emit = defineEmits<{
  /** どの情報を、取得済みを外して取るか（true＝スキップ）。 */
  choose: [section: JpnAiStateKey, skipAcquired: boolean]
  close: []
}>()

/** 選んでいる情報（窓を開くたびに A・B へ戻す）。 */
const section = ref<JpnAiStateKey>('AB')

watch(() => props.visible, (visible) => {
  if (visible) section.value = 'AB'
})

/** いま選んでいる情報の計画。 */
const plan = computed(() => props.plans[section.value])

/**
 * 2.0 と同じく「対象は何件か」を 1 行で出す（受付ける前に確かめられる）。
 *
 * <p>上限は**設定ページの「1 回の最大単語数」**（計画が持っている。固定値ではない）。
 * 2026-09-27 に**受付（非同期）**へ変えたので、「1 回の上限」ではなく「1 回の受付」と書く
 * （実行はバックグラウンドで進み、画面は結果を待たない）。</p>
 */
const summary = computed(() =>
  `${props.targetLabel}が対象です（うち取得済み ${plan.value.all.acquired} 語）。`
  + `1 回の受付は ${plan.value.all.limit} 語までです。`
)
</script>

<template>
  <div v-if="props.visible" class="overlay jp-fetch-overlay" data-jp-fetch-dialog>
    <!-- 背景（灰色の部分）をクリックしても閉じない（docs/FRONTEND_GUIDE.md §3.9 の利用者指定。
         閉じる手段は ×・閉じる・キャンセルだけ） -->
    <div class="jp-fetch__backdrop" data-jp-fetch-backdrop></div>
    <section class="dialog jp-fetch" role="dialog" aria-modal="true" aria-labelledby="jpFetchTitle">
      <div class="dialog__head">
        <h2 id="jpFetchTitle" class="dialog__title" data-jp-fetch-title>
          <AppIcon name="robot" size="sm" class="icon--ai" /> AI 取得
        </h2>
        <button
          type="button" class="dialog__close" aria-label="閉じる" data-jp-fetch-close
          @click="emit('close')"
        >
          <AppIcon name="x" size="sm" />
        </button>
      </div>

      <div class="dialog__body jp-fetch__body">
        <!-- 1. どの情報を取るか（4 つ。1 つの窓にまとめた） -->
        <fieldset class="jp-fetch__kinds">
          <legend class="jp-fetch__legend">取得する情報</legend>
          <label
            v-for="action in JPN_ACQUIRE_ACTIONS" :key="action.key"
            class="jp-fetch__kind" :class="{ 'is-selected': section === action.key }"
          >
            <input
              v-model="section" type="radio" name="jpFetchKind" :value="action.key"
              class="jp-fetch__kind-input" :data-jp-fetch-kind="action.key"
            >
            <AppIcon :name="action.icon" size="sm" />
            <span class="jp-fetch__kind-label">{{ action.label }}</span>
          </label>
        </fieldset>

        <!-- 2. 取得済みをどうするか（2.0 の「取得方法」の窓と同じ 2 択） -->
        <p class="jp-hint" data-jp-fetch-summary>{{ summary }}</p>
        <p class="jp-fetch__question">すでに取得済みの単語を、今回の取得対象に含めますか？</p>

        <div class="jp-fetch__actions">
          <button
            type="button" class="jp-fetch__choice jp-fetch__choice--skip" data-jp-fetch-skip
            @click="emit('choose', section, true)"
          >
            <AppIcon name="chevron-right" size="sm" />
            <span>
              <strong>取得済みをスキップ</strong>
              <small>
                未取得と失敗の {{ plan.skip.targets }} 語だけ受付けます（取得済みと取得中は呼びません）。
              </small>
            </span>
          </button>
          <button
            type="button" class="jp-fetch__choice jp-fetch__choice--reacquire" data-jp-fetch-reacquire
            @click="emit('choose', section, false)"
          >
            <AppIcon name="rotate" size="sm" />
            <span>
              <strong>すべて再取得</strong>
              <small>
                対象の {{ plan.all.targets }} 語を AI で取り直します
                （取得済みも新しい版として保存し、古い版は履歴に残ります）。
              </small>
            </span>
          </button>
        </div>

        <p class="jp-hint">受付けた分はバックグラウンドで取得します（画面は待ちません）。</p>

        <p v-if="plan.all.overLimit > 0" class="jp-hint" data-jp-fetch-over-limit>
          1 回の受付を超える {{ plan.all.overLimit }} 語は次回に回します。
        </p>

        <button type="button" class="btn btn--secondary jp-fetch__cancel" data-jp-fetch-cancel @click="emit('close')">
          キャンセル
        </button>
      </div>
    </section>
  </div>
</template>
