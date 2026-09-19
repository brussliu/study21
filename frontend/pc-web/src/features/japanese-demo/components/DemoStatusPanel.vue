<script setup lang="ts">
import { ref } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'

/**
 * 「デモ表示設定」（§8）。
 *
 * 確認したい状態へ切り替えるための道具で、**業務の操作とは分けて**既定はたたんでおく。
 * 切り替えはストアのメモリ上の値だけを変え、本番のデータには触れない。
 */

const store = useJapaneseDemoStore()
const open = ref(false)

/** 各項目の説明（何が確認できるか）。 */
const LIST_MODES: { value: string; label: string; note: string }[] = [
  { value: 'NORMAL', label: '正常な一覧', note: '仮データの状態をそのまま表示します。' },
  { value: 'NO_RESULT', label: '検索結果なし', note: '条件に一致しないときの案内を確認します。' },
  { value: 'NOT_GENERATED', label: '内容未生成', note: '詳細情報がまだ無い状態（生成の入口が出ます）。' },
  { value: 'GENERATING', label: 'AI 生成中', note: '生成中の表示と、完了までの見え方を確認します。' },
  { value: 'FAILED', label: 'AI 生成失敗', note: '失敗の理由と「再試行」が出ます（未生成とは別の状態）。' },
  { value: 'FULL', label: '完整な詳細', note: '詳細編集・学習画面に全項目がある状態。' },
  { value: 'PARTIAL', label: '一部が欠けた詳細', note: '単語ごとの欠けを見ます（この設定では仮データのまま）。' }
]

const DETAIL_MODES: { value: string; label: string; note: string }[] = [
  { value: 'FULL', label: 'すべて表示', note: '保存済みの詳細をそのまま見せます。' },
  { value: 'PARTIAL', label: '一部を欠けさせる', note: '任意の欄を隠し、空状態の案内を確認します。' }
]

const SAVE_MODES: { value: string; label: string; note: string }[] = [
  { value: 'NORMAL', label: '正常に保存', note: '保存は成功します（デモデータの中だけ）。' },
  { value: 'SAVE_FAILED', label: '保存に失敗', note: '保存・削除・登録が失敗し、入力が残ることを確認します。' },
  { value: 'CONFLICT', label: '編集の衝突', note: '保存時に版の衝突を再現し、入力を残したまま確認を促します。' }
]
</script>

<template>
  <section class="jp-demo-panel" data-demo-status-panel>
    <button
      type="button" class="jp-demo-panel__head" data-demo-status-toggle
      :aria-expanded="open" @click="open = !open"
    >
      <AppIcon name="sliders" size="sm" />
      デモ表示設定（確認したい状態に切り替える）
      <span class="jp-demo__badge">デモ専用</span>
      <AppIcon :name="open ? 'chevron-down' : 'chevron-right'" size="sm" />
    </button>

    <div v-show="open" class="jp-demo-panel__body">
      <label class="jp-demo-panel__field">
        <span class="jp-demo-panel__label">一覧の状態</span>
        <select
          class="select" data-demo-display-list
          :value="store.display.listMode"
          @change="store.setDisplay({ listMode: ($event.target as HTMLSelectElement).value as typeof store.display.listMode })"
        >
          <option v-for="mode in LIST_MODES" :key="mode.value" :value="mode.value">{{ mode.label }}</option>
        </select>
      </label>

      <label class="jp-demo-panel__field">
        <span class="jp-demo-panel__label">詳細の状態</span>
        <select
          class="select" data-demo-display-detail
          :value="store.display.detailMode"
          @change="store.setDisplay({ detailMode: ($event.target as HTMLSelectElement).value as typeof store.display.detailMode })"
        >
          <option v-for="mode in DETAIL_MODES" :key="mode.value" :value="mode.value">{{ mode.label }}</option>
        </select>
      </label>

      <label class="jp-demo-panel__field">
        <span class="jp-demo-panel__label">保存の状態</span>
        <select
          class="select" data-demo-display-save
          :value="store.display.saveMode"
          @change="store.setDisplay({ saveMode: ($event.target as HTMLSelectElement).value as typeof store.display.saveMode })"
        >
          <option v-for="mode in SAVE_MODES" :key="mode.value" :value="mode.value">{{ mode.label }}</option>
        </select>
      </label>

      <p class="jp-demo-panel__hint" data-demo-display-note>
        {{ LIST_MODES.find((mode) => mode.value === store.display.listMode)?.note }}
        {{ SAVE_MODES.find((mode) => mode.value === store.display.saveMode)?.note }}
        この設定は画面の確認のためだけのもので、実際のデータや生成処理には影響しません。
      </p>
    </div>
  </section>
</template>
