<script setup lang="ts">
import { onMounted } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import GeometryAiSettingsSection from '@/views/admin/system-settings/GeometryAiSettingsSection.vue'
import ClassroomAiSettingsSection from '@/views/admin/system-settings/ClassroomAiSettingsSection.vue'
import '@/features/system-settings/system-settings.css'
import '@/features/system-settings/study2SettingRuntime'

declare global {
  interface Window {
    __study21SystemSettings?: {
      mount: () => void
      /**
       * 別コンポーネントが描く設定項目（AI 生図の設定セクションなど）を登録する。
       * 登録すると、画面全体の【設定を保存】と再読込にその値を載せられる。
       */
      registerSection?: (section: {
        getValues: () => Record<string, string>
        applyValues?: (values: Record<string, string>) => void
      }) => void
      collectValues?: () => Record<string, string>
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

    <!-- AI 生図（図形管理）の設定。項目の描画はこのコンポーネントが行い（設定ランタイムの
         同カテゴリは hidden）、値は registerSection で画面全体の【設定を保存】にも載せる -->
    <GeometryAiSettingsSection />

    <!-- AI 授業記録（授業録音）の設定。項目の描画はこのコンポーネントが行う。
         バックエンドのキーが未定義のため、値はローカルのみ（保存ボタンは占位）。
         未定義キーを全体保存に載せると 400 になるため registerSection には登録しない -->
    <ClassroomAiSettingsSection />

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
