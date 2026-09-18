<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  BROWSER_PLUGINS,
  loadPluginPackage,
  packageLabel,
  pluginDownloadUrl,
  type ExtensionPackage
} from './browserext'
import './browserext.css'

/**
 * 画面右上（学生・保護者の表示のとなり）の「プラグイン」ボタン。
 *
 * クリックすると小さなパネルが開き、このシステムが配っているブラウザ拡張などの
 * ダウンロードとインストール手順を出す。2.0 は拡張を手で配っていた。
 *
 * 配布物は deploy のビルド中に `extension/` から作られる（静的な zip）。
 * まだ作られていない環境では「配布パッケージはまだありません」と出す。
 */
const open = ref(false)
const anchor = ref<HTMLElement | null>(null)
const loading = ref(false)
const loaded = ref(false)
const packages = ref<Record<string, ExtensionPackage | null>>({})

async function load(): Promise<void> {
  loading.value = true
  try {
    const entries = await Promise.all(
      BROWSER_PLUGINS.map(async (plugin) => [plugin.code, await loadPluginPackage(plugin)] as const))
    packages.value = Object.fromEntries(entries)
    loaded.value = true
  } finally {
    loading.value = false
  }
}

function toggle(): void {
  open.value = !open.value
  if (open.value && !loaded.value) {
    void load()
  }
}

/** メニュー外のクリックで閉じる（開くボタン自身のクリックは無視する）。 */
function onDocumentClick(event: MouseEvent): void {
  if (!open.value) return
  if (anchor.value?.contains(event.target as Node)) return
  open.value = false
}

function onDocumentKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && open.value) open.value = false
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onDocumentKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onDocumentKeydown)
})
</script>

<template>
  <div ref="anchor" class="bx-anchor">
    <button
      type="button"
      class="bx-button"
      :class="{ 'is-open': open }"
      aria-haspopup="menu"
      :aria-expanded="open ? 'true' : 'false'"
      title="プラグイン（ブラウザ拡張）のダウンロード"
      data-plugin-menu-button
      @click="toggle"
    >
      <AppIcon name="puzzle" />
    </button>

    <div v-if="open" class="bx-menu" role="menu" aria-label="プラグインのダウンロード" data-plugin-menu>
      <h2 class="bx-menu__title"><AppIcon name="puzzle" size="sm" /> プラグイン</h2>
      <p class="bx-menu__note">
        このシステムで使えるブラウザ拡張です。ダウンロードして Chrome に入れてください。
      </p>

      <div
        v-for="plugin in BROWSER_PLUGINS"
        :key="plugin.code"
        class="bx-plugin"
        :data-plugin="plugin.code"
      >
        <div class="bx-plugin__head">
          <AppIcon name="puzzle" size="sm" />
          <span class="bx-plugin__titles">
            <span class="bx-plugin__name">{{ plugin.name }}</span>
            <span class="bx-plugin__meta" :data-plugin-meta="plugin.code">
              {{ loading ? '読み込み中...' : packageLabel(packages[plugin.code] ?? null) }}
            </span>
          </span>
        </div>
        <p class="bx-plugin__description">{{ plugin.description }}</p>
        <div class="bx-plugin__actions">
          <a
            class="btn btn--primary btn--sm"
            :href="pluginDownloadUrl(plugin, packages[plugin.code] ?? null)"
            :download="plugin.code === 'browser-extension' ? 'study21-extension.zip' : undefined"
            :data-plugin-download="plugin.code"
          >
            <AppIcon name="download" size="sm" /> ダウンロード
          </a>
        </div>
        <details class="bx-steps">
          <summary class="bx-steps__summary">インストール手順を見る</summary>
          <ol class="bx-steps__list">
            <li v-for="(step, index) in plugin.steps" :key="index">{{ step }}</li>
          </ol>
        </details>
      </div>
    </div>
  </div>
</template>
