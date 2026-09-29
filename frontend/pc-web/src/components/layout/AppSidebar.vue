<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { resolveMenu, type AppArea } from '@/config/menuRegistry'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import SidebarMenuItem from './SidebarMenuItem.vue'

const props = defineProps<{
  collapsed?: boolean
  drawerOpen?: boolean
  area: AppArea
}>()

const router = useRouter()
const auth = useAuthStore()
const theme = useThemeStore()

// 読書管理の【書籍管理】はロールで出し分ける（生徒には出さない。決定 Q9）
const menu = computed(() => resolveMenu(props.area, auth.role ?? undefined))
async function onLogout(): Promise<void> {
  const wasAdmin = auth.role === 'ADMIN'
  auth.logout()
  await router.push(wasAdmin ? '/admin/login' : '/login')
}
</script>

<template>
  <aside
    class="sidebar"
    :class="{ 'is-collapsed': collapsed, 'is-open': drawerOpen }"
    aria-label="メインナビゲーション"
  >
    <div class="sidebar__brand">
      <svg class="logo-mark" viewBox="0 0 64 64" aria-hidden="true">
        <defs>
          <linearGradient id="study21-logo-grad" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="#2a8a72" />
            <stop offset="1" stop-color="#4cb39a" />
          </linearGradient>
        </defs>
        <rect x="0" y="0" width="64" height="64" rx="16" fill="url(#study21-logo-grad)" />
        <path d="M16 24 L32 30 L32 48 L16 42 Z" fill="#bce4d8" />
        <path d="M48 24 L32 30 L32 48 L48 42 Z" fill="#e6f4ef" />
        <circle cx="32" cy="22.5" r="5.2" fill="#ffffff" />
      </svg>
      <span class="sidebar__brand-text">STUDY 2.1</span>
    </div>

    <nav class="sidebar__nav">
      <SidebarMenuItem v-for="item in menu" :key="item.id" :item="item" />
    </nav>

    <div class="sidebar__footer">
      <button type="button" class="nav-item" @click="theme.toggle()">
        <svg class="icon" aria-hidden="true">
          <use :href="theme.theme === 'dark' ? '#i-sun' : '#i-moon'" />
        </svg>
        <span class="nav-item__text">{{ theme.theme === 'dark' ? 'ライトモード' : 'ダークモード' }}</span>
      </button>
      <button type="button" class="nav-item" @click="onLogout">
        <svg class="icon" aria-hidden="true"><use href="#i-logout" /></svg>
        <span class="nav-item__text">ログアウト</span>
      </button>
    </div>
  </aside>
</template>
