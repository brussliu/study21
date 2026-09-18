<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { resolveMenu, type AppArea } from '@/config/menuRegistry'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import type { MenuItem } from '@study21/web-shared'

const props = defineProps<{
  collapsed?: boolean
  drawerOpen?: boolean
  area: AppArea
}>()

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const theme = useThemeStore()

// 読書管理の【書籍管理】はロールで出し分ける（生徒には出さない。決定 Q9）
const menu = computed(() => resolveMenu(props.area, auth.role ?? undefined))
const openGroups = ref<string[]>([])
const currentPath = computed(() => route.path)

function iconHref(icon?: string): string {
  return `#i-${icon ?? 'circle'}`
}

function isActive(item: MenuItem): boolean {
  return currentPath.value === item.path
}

function isGroupOpen(item: MenuItem): boolean {
  const childActive = item.children?.some((child) => isActive(child)) ?? false
  return openGroups.value.includes(item.id) || childActive
}

function toggleGroup(item: MenuItem): void {
  const index = openGroups.value.indexOf(item.id)
  if (index >= 0) {
    openGroups.value.splice(index, 1)
  } else {
    openGroups.value.push(item.id)
  }
}

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
      <template v-for="item in menu" :key="item.id">
        <div
          v-if="item.children?.length"
          class="nav-group"
          :class="{ 'is-open': isGroupOpen(item) }"
          :data-group="item.id"
        >
          <button type="button" class="nav-item" @click="toggleGroup(item)">
            <svg class="icon" aria-hidden="true"><use :href="iconHref(item.icon)" /></svg>
            <span class="nav-item__text">{{ item.label }}</span>
            <span class="nav-item__caret" aria-hidden="true">▾</span>
          </button>
          <div class="nav-sub">
            <RouterLink
              v-for="child in item.children"
              :key="child.id"
              :to="child.path ?? '/'"
              class="nav-subitem"
              active-class="is-active"
            >
              <svg class="icon" aria-hidden="true"><use :href="iconHref(child.icon)" /></svg>
              <span class="nav-item__text">{{ child.label }}</span>
            </RouterLink>
          </div>
        </div>
        <RouterLink
          v-else
          :to="item.path ?? '/'"
          class="nav-item"
          active-class="is-active"
        >
          <svg class="icon" aria-hidden="true"><use :href="iconHref(item.icon)" /></svg>
          <span class="nav-item__text">{{ item.label }}</span>
        </RouterLink>
      </template>
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
