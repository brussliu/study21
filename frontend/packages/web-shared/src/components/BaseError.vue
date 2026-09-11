<script setup lang="ts">
defineProps<{
  title?: string
  message?: string
  code?: string
}>()
</script>

<template>
  <div class="base-error" role="alert">
    <p class="base-error__title">{{ title ?? 'エラーが発生しました' }}</p>
    <p class="base-error__message">{{ message ?? 'しばらくしてから再度お試しください。' }}</p>
    <p v-if="code" class="base-error__code">{{ code }}</p>
    <div class="base-error__action">
      <slot name="action"></slot>
    </div>
  </div>
</template>

<style scoped>
.base-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-8) var(--sp-4);
  text-align: center;
}
.base-error__title {
  font-size: var(--fs-lg);
  font-weight: var(--fw-semibold);
  color: var(--color-danger);
  margin: 0;
}
.base-error__message {
  font-size: var(--fs-md);
  color: var(--color-text-muted);
  margin: 0;
}
.base-error__code {
  font-family: monospace;
  font-size: var(--fs-xs);
  color: var(--color-text-subtle);
  margin: 0;
}
.base-error__action {
  margin-top: var(--sp-2);
}
</style>
