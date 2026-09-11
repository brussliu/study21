<script setup lang="ts">
defineProps<{
  title?: string
  description?: string
}>()
</script>

<template>
  <div class="base-empty">
    <p class="base-empty__title">{{ title ?? 'データがありません' }}</p>
    <p v-if="description" class="base-empty__description">{{ description }}</p>
    <div class="base-empty__action">
      <slot name="action"></slot>
    </div>
  </div>
</template>

<style scoped>
.base-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-8) var(--sp-4);
  text-align: center;
  color: var(--color-text-muted);
}
.base-empty__title {
  font-size: var(--fs-lg);
  font-weight: var(--fw-semibold);
  color: var(--color-text);
  margin: 0;
}
.base-empty__description {
  font-size: var(--fs-md);
  margin: 0;
}
.base-empty__action {
  margin-top: var(--sp-2);
}
</style>
