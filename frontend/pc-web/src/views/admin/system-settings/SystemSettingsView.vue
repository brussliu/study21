<script setup lang="ts">
import { onMounted } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import '@/features/system-settings/system-settings.css'
import '@/features/system-settings/study2SettingRuntime'

declare global {
  interface Window {
    __study21SystemSettings?: {
      mount: () => void
    }
  }
}

onMounted(() => {
  window.__study21SystemSettings?.mount()
})
</script>

<template>
  <div class="setting-page">
    <div class="system-settings-toolbar" aria-label="システム設定操作">
      <button type="button" class="btn btn--secondary" id="settingReloadBtn">
        <AppIcon name="rotate" />
        再読込
      </button>
      <button type="button" class="btn btn--primary" id="settingSaveBtn">
        <AppIcon name="check" />
        設定を保存
      </button>
    </div>

    <section class="setting-workspace card">
      <aside class="setting-category-nav" id="settingCategoryNav" aria-label="設定カテゴリ"></aside>
      <div class="setting-content-area">
        <div class="setting-loading" id="settingLoading">
          設定を読み込んでいます...
        </div>
        <div id="settingPanels" hidden></div>
      </div>
    </section>
    <div class="setting-toast" id="settingToast" role="status" aria-live="polite"></div>
  </div>
</template>

<style scoped>
.system-settings-toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
