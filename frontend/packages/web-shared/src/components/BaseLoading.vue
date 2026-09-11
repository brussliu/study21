<script setup lang="ts">
defineProps<{
  label?: string
}>()
</script>

<template>
  <div class="base-loading" role="status" aria-live="polite">
    <span class="base-loading__spinner" aria-hidden="true"></span>
    <span class="base-loading__label">{{ label ?? '読み込み中…' }}</span>
  </div>
</template>

<style scoped>
.base-loading {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  color: var(--color-text-muted);
  font-size: var(--fs-md);
}
.base-loading__spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: var(--radius-pill);
  animation: base-loading-spin 0.8s linear infinite;
}
@keyframes base-loading-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
