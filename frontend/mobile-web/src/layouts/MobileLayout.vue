<script setup lang="ts">
import { ref } from 'vue'
import type { MobileArea } from '@/router'
import MobileTopbar from '@/components/layout/MobileTopbar.vue'
import MobileBottomNav from '@/components/layout/MobileBottomNav.vue'
import MobileDrawer from '@/components/layout/MobileDrawer.vue'
import { homePathOf } from '@/config/menuRegistry'

const { area } = defineProps<{ area: MobileArea }>()
const drawerOpen = ref(false)
const homePath = homePathOf(area)
</script>

<template>
  <div class="mobile-layout">
    <MobileTopbar @open-drawer="drawerOpen = true" />
    <main class="mobile-layout__content">
      <RouterView />
    </main>
    <MobileBottomNav :home-path="homePath" @open-drawer="drawerOpen = true" />
    <MobileDrawer :open="drawerOpen" :area="area" :home-path="homePath" @close="drawerOpen = false" />
  </div>
</template>

<style scoped>
.mobile-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  min-height: 100dvh;
}
.mobile-layout__content {
  flex: 1;
  padding: var(--sp-4);
  padding-top: calc(var(--sp-4) + env(safe-area-inset-top));
  padding-bottom: calc(var(--sp-8) + env(safe-area-inset-bottom));
}
</style>
