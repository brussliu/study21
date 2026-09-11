<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { prototypePageMap } from '@/config/prototypePages.generated'
import type { AppArea } from '@/config/menuRegistry'

const route = useRoute()

const area = computed<AppArea>(() => {
  const first = String(route.path.split('/')[1] || 'student')
  return first === 'admin' || first === 'parent' ? first : 'student'
})

const screenSlug = computed(() => String(route.params.screen ?? 'home'))

const currentTitle = computed(() => {
  const meta = prototypePageMap.get(screenSlug.value)
  if (meta) return meta.title
  return (route.meta.title as string | undefined) ?? screenSlug.value
})
</script>

<template>
  <nav class="breadcrumb" aria-label="パンくず">
    <span class="breadcrumb__item"><RouterLink :to="`/${area}/home`">ホーム</RouterLink></span>
    <span class="breadcrumb__sep">/</span>
    <span class="breadcrumb__item is-current">{{ currentTitle }}</span>
  </nav>
</template>
