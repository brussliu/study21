<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { MenuItem } from '@study21/web-shared'

const props = withDefaults(defineProps<{ item: MenuItem; depth?: number }>(), { depth: 0 })
const route = useRoute()
const open = ref(false)
function containsPath(item: MenuItem): boolean {
  return item.children?.length ? item.children.some(containsPath) : route.path === item.path || !!item.path && route.path.startsWith(`${item.path}/`)
}
const active = computed(() => containsPath(props.item))
watch(() => route.path, () => { if (active.value) open.value = true }, { immediate: true })
</script>

<template>
  <div v-if="item.children?.length" class="nav-group" :class="{ 'is-open': open }" :data-group="item.id">
    <button type="button" :class="depth ? 'nav-subitem' : 'nav-item'" :aria-expanded="open" :aria-controls="`menu-${item.id}`" @click="open = !open">
      <svg class="icon" aria-hidden="true"><use :href="`#i-${item.icon ?? 'circle'}`" /></svg>
      <span class="nav-item__text">{{ item.label }}</span><span class="nav-item__caret" aria-hidden="true">▾</span>
    </button>
    <div :id="`menu-${item.id}`" class="nav-sub" :hidden="!open">
      <SidebarMenuItem v-for="child in item.children" :key="child.id" :item="child" :depth="depth + 1" />
    </div>
  </div>
  <RouterLink v-else :to="item.path ?? '/'" :class="[depth ? 'nav-subitem' : 'nav-item', { 'is-active': active }]" exact-active-class="is-active">
    <svg class="icon" aria-hidden="true"><use :href="`#i-${item.icon ?? 'circle'}`" /></svg>
    <span class="nav-item__text">{{ item.label }}</span>
  </RouterLink>
</template>

<style scoped>
.nav-group > .nav-sub[hidden] { display: none; }
.nav-group > button .nav-item__caret { transform: none; }
.nav-group.is-open > button .nav-item__caret { transform: rotate(180deg); }
.nav-sub .nav-sub { padding-left: var(--sp-3); border-left: 1px solid var(--sidebar-text-muted); margin-left: var(--sp-3); }
</style>
