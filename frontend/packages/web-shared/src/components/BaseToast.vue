<script setup lang="ts">
import type { ToastType } from '../composables/useToast'

defineProps<{
  type: ToastType
  message: string
}>()
</script>

<template>
  <div class="base-toast" :class="`base-toast--${type}`" role="status">
    <span class="base-toast__dot" aria-hidden="true"></span>
    <span class="base-toast__message">{{ message }}</span>
  </div>
</template>

<style scoped>
.base-toast {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-3) var(--sp-4);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-md);
  font-size: var(--fs-md);
  color: var(--color-text);
}
.base-toast__dot {
  width: 8px;
  height: 8px;
  border-radius: var(--radius-pill);
  flex: 0 0 auto;
}
.base-toast--success .base-toast__dot {
  background: var(--color-success);
}
.base-toast--info .base-toast__dot {
  background: var(--color-info);
}
.base-toast--warning .base-toast__dot {
  background: var(--color-warning);
}
.base-toast--danger .base-toast__dot {
  background: var(--color-danger);
}
</style>
