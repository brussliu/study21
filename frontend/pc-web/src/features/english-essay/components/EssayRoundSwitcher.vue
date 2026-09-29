<script setup lang="ts">
import { useTabKeys } from '@/composables/useTabKeys'

/**
 * 添削の**历次の切り替え**（2.0 には無い 2.1 の要件）。
 *
 * 2.0 は添削結果を 1 列に上書きしていたので「前の回」を見られなかった。2.1 は
 * `ENG_AI添削履歴情報` の行（古い順）を積むので、ここで何回目を見るかを選ばせる。
 *
 * <p>渡すのは API の `gradings[]` の行（`round`・`statusCode`・`score`・`maxScore`・
 * `createdAt`）。**失敗した回・実行中の回も選択肢に出す**（`ENG_AI添削履歴情報` は失敗した回も
 * 1 行として残るので、抜かすと「第 N 回」が API の `round` とずれる）。番号は `round` を
 * そのまま使い、**成功した回を数え直さない**。</p>
 *
 * 選択肢は「第 N 回（2026/09/26 20:30・得点 26 / 32）」。失敗した回は「・失敗」、実行中は
 * 「・実行中」。満点が分からない回（2.0 の古い形式の移行データ）は点数だけを出す。
 */

/** 選択肢 1 件ぶん（API の `gradings[]` の行のうち、表示に要る分）。 */
interface RoundOption {
  /** その回の番号（API の `round`。1 から）。 */
  round: number
  statusCode: string
  score: number | null
  /** 満点（分からない回は `null`。`0` を「不明」の印にはしない）。 */
  maxScore: number | null
  createdAt: string
}

const props = withDefaults(defineProps<{
  /** 添削の回（古い順。**失敗・実行中も含む**）。 */
  rounds: RoundOption[]
  /** 選択中の回（API の `round`。1 から）。 */
  modelValue: number
  /** 【もう一度AI添削】を出すか（親エリアは閲覧だけなので出さない）。 */
  canRegrade?: boolean
}>(), { canRegrade: true })

const emit = defineEmits<{
  (event: 'update:modelValue', round: number): void
  (event: 'regrade'): void
}>()

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** `YYYY/MM/DD HH:mm`（見ている人の時計で出す）。 */
function formatAt(iso: string): string {
  const at = new Date(iso)
  if (Number.isNaN(at.getTime())) {
    return iso
  }
  return `${at.getFullYear()}/${pad(at.getMonth() + 1)}/${pad(at.getDate())}`
    + ` ${pad(at.getHours())}:${pad(at.getMinutes())}`
}

/**
 * 得点（満点が分かるときだけ `得点 26 / 32`。分からない回は `得点 12` だけ）。
 *
 * <p>`0` を「不明」の印にしない（`maxScore` が無いことは `null` で表す）。</p>
 */
function scoreOf(option: RoundOption): string {
  if (option.score === null) {
    return '得点 —'
  }
  return option.maxScore === null ? `得点 ${option.score}` : `得点 ${option.score} / ${option.maxScore}`
}

/** 得点の代わりに出す状態（まだ結果が無い回。失敗・実行中は見分けられるようにする）。 */
function stateOf(option: RoundOption): string | null {
  if (option.statusCode === 'QUEUED' || option.statusCode === 'RUNNING') {
    return '実行中'
  }
  if (option.statusCode !== 'SUCCEEDED') {
    return '失敗'
  }
  return null
}

/** 「第 N 回（2026/09/26 20:30・得点 26 / 32）」（失敗・実行中は得点の代わりに状態を出す）。 */
function labelOf(option: RoundOption): string {
  const state = stateOf(option)
  return `第 ${option.round} 回（${formatAt(option.createdAt)}・${state ?? scoreOf(option)}）`
}

/** その回を見ているか（タブの選択中）。 */
function isActive(option: RoundOption): boolean {
  return option.round === props.modelValue
}

/**
 * タブのキーボード操作（←→・Home・End）。
 *
 * <p>作法はリポジトリ共通の `useTabKeys` に寄せた（英語勉強・精読と同じ動き）。
 * `tabindex` は**選択中だけ 0**（roving tabindex）にして、Tab 1 回でタブ列を抜けられるようにする。</p>
 */
const tabKey = useTabKeys()
</script>

<template>
  <div class="ee-round" data-ee-round-bar>
    <!-- 2.1 の追加要件（历次の切り替え）。利用者の指示で**タブ**にした（前はセレクト）。
         2.0 には無いので、見た目は 2.0 の言語切替（testworld2 のセグメント）に寄せる。
         キーボードと ARIA は既存のタブ（`EnglishStudyView.vue`）と同じ作法 -->
    <div class="ee-round__tabs" role="tablist" aria-label="添削の回" data-ee-round @keydown="tabKey">
      <button
        v-for="option in props.rounds" :id="`eeRoundTab-${option.round}`" :key="option.round"
        type="button" role="tab" class="ee-round__tab"
        :class="{ 'is-active': isActive(option) }"
        :aria-selected="isActive(option)" :tabindex="isActive(option) ? 0 : -1"
        aria-controls="eeRoundPanel" :data-ee-round-tab="option.round"
        :title="labelOf(option)" :aria-label="labelOf(option)"
        @click="emit('update:modelValue', option.round)"
      >
        第 {{ option.round }} 回<small v-if="stateOf(option) !== null" class="ee-round__state">・{{ stateOf(option) }}</small>
      </button>
    </div>
    <span class="ee-round__count" data-ee-round-count>全 {{ props.rounds.length }} 回</span>
    <button
      v-if="props.canRegrade" type="button" class="btn btn--secondary btn--sm" data-ee-regrade
      @click="emit('regrade')"
    >
      もう一度AI添削
    </button>
  </div>
</template>
