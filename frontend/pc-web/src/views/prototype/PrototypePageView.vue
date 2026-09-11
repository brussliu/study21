<script setup lang="ts">
import { computed, defineAsyncComponent, type AsyncComponentLoader } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from '@study21/web-shared'
import { prototypePageMap } from '@/config/prototypePages.generated'

const props = defineProps<{ screen?: string }>()
const route = useRoute()
const router = useRouter()
const toast = useToast()

const loaders = import.meta.glob('./generated/*.vue')
const screen = computed(() => props.screen ?? String(route.params.screen ?? 'home'))
const page = computed(() => prototypePageMap.get(screen.value))
const pageComponent = computed(() => {
  const componentName = page.value?.component
  const loader = componentName ? loaders[`./generated/${componentName}.vue`] : undefined
  return loader ? defineAsyncComponent(loader as AsyncComponentLoader) : null
})
const area = computed(() => String(route.path.split('/')[1] || 'student'))

function toScreen(value: string): string {
  return value
    .replace(/\.html(?:[?#].*)?$/i, '')
    .replace(/副本/g, 'copy')
    .replace(/[^a-zA-Z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .toLowerCase()
}

async function openScreen(target: string): Promise<void> {
  const next = toScreen(target)
  if (prototypePageMap.has(next)) await router.push(`/${area.value}/${next}`)
  else toast.info('この画面は移行対象を確認中です')
}

function onPageClick(event: MouseEvent): void {
  const origin = event.target
  if (!(origin instanceof Element)) return

  const closeButton = origin.closest('[data-dialog-close]')
  if (closeButton) {
    closeButton.closest('.overlay')?.setAttribute('hidden', '')
    return
  }

  const dialogButton = origin.closest<HTMLElement>('[data-open-dialog]')
  if (dialogButton) {
    const id = dialogButton.dataset.openDialog
    if (id) document.getElementById(id)?.removeAttribute('hidden')
    return
  }

  const navigation = origin.closest<HTMLElement>('[data-route-screen], [data-nav], [data-href]')
  if (navigation) {
    const target = navigation.dataset.routeScreen ?? navigation.dataset.nav ?? navigation.dataset.href
    if (target) {
      event.preventDefault()
      void openScreen(target)
    }
    return
  }

  if (origin.closest('button[type="button"], button:not([type])')) {
    toast.info('UI確認用のMock操作です')
  }
}
</script>

<template>
  <template v-if="page && pageComponent">
    <div class="page-body" @click="onPageClick">
      <component :is="pageComponent" />
    </div>
  </template>
  <section v-else class="prototype-page--missing">
    <h1>画面が見つかりません</h1>
    <p>{{ screen }}</p>
    <RouterLink :to="`/${area}/home`">ホームへ戻る</RouterLink>
  </section>
</template>

<style scoped>
.prototype-page--missing {
  padding: 32px;
  text-align: center;
}
</style>
