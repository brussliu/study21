<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import AppTopbar from '@/components/layout/AppTopbar.vue'
import { useSidebar } from '@/composables/useSidebar'
import type { AppArea } from '@/config/menuRegistry'

const route = useRoute()
const { collapsed, drawerOpen, toggle, closeDrawer } = useSidebar()
const area = computed<AppArea>(() => (route.path.startsWith('/parent') ? 'parent' : 'student'))
</script>

<template>
  <div class="app app--user">
    <AppSidebar :collapsed="collapsed" :drawer-open="drawerOpen" :area="area" />
    <div class="sidebar-backdrop" :class="{ 'is-visible': drawerOpen }" @click="closeDrawer"></div>
    <div class="app-main">
      <AppTopbar @toggle-sidebar="toggle" />
      <main class="page">
        <RouterView />
      </main>
    </div>
  </div>
</template>
