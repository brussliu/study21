<script setup lang="ts">
import { onMounted, onUnmounted, ref, useId } from 'vue'
defineProps<{ title: string; wide?: boolean }>()
const emit = defineEmits<{ close: [] }>()
const panel = ref<HTMLElement>()
const titleId = useId()
let previous: HTMLElement | null = null
let alreadyLocked = false
function keydown(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.stopPropagation(); emit('close') }
  if (event.key !== 'Tab') return
  const nodes = Array.from(panel.value?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href]') ?? []).filter(el => el.getClientRects().length)
  const first = nodes[0], last = nodes[nodes.length - 1]
  if (event.shiftKey && (document.activeElement === first || document.activeElement === panel.value)) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
onMounted(() => { previous = document.activeElement as HTMLElement; alreadyLocked = document.body.classList.contains('is-locked'); document.body.classList.add('is-locked'); panel.value?.focus() })
onUnmounted(() => { if (!alreadyLocked) document.body.classList.remove('is-locked'); previous?.focus() })
</script>
<template>
  <Teleport to="body">
    <div class="overlay eng-overlay" @keydown="keydown">
      <section ref="panel" class="dialog eng-dialog" :class="{ 'eng-dialog--wide': wide }" role="dialog" aria-modal="true" :aria-labelledby="titleId" tabindex="-1">
        <header class="dialog__head"><h2 :id="titleId" class="dialog__title">{{ title }}</h2><button class="dialog__close" aria-label="閉じる" @click="emit('close')">×</button></header>
        <div class="dialog__body"><slot /></div>
        <footer v-if="$slots.footer" class="dialog__foot"><slot name="footer" /></footer>
      </section>
    </div>
  </Teleport>
</template>
