<script setup lang="ts">
import AppSidebar from '@/components/layout/AppSidebar.vue'
import AppTopbar from '@/components/layout/AppTopbar.vue'
import { useSidebar } from '@/composables/useSidebar'

const { collapsed, drawerOpen, toggle, closeDrawer } = useSidebar()
</script>

<template>
  <div class="app app--admin">
    <AppSidebar :collapsed="collapsed" :drawer-open="drawerOpen" area="admin" />
    <div class="sidebar-backdrop" :class="{ 'is-visible': drawerOpen }" @click="closeDrawer"></div>
    <div class="app-main">
      <AppTopbar @toggle-sidebar="toggle" />
      <main class="page">
        <RouterView />
      </main>
    </div>
  </div>
</template>
