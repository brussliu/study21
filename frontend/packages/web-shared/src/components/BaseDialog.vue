<script setup lang="ts">
defineProps<{
  open: boolean
  title: string
}>()

const emit = defineEmits<{
  close: []
}>()
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="dialog-overlay" @click.self="emit('close')">
      <div class="dialog" role="dialog" aria-modal="true" :aria-label="title">
        <header class="dialog__header">
          <h2 class="dialog__title">{{ title }}</h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="emit('close')">
            ×
          </button>
        </header>
        <div class="dialog__body">
          <slot></slot>
        </div>
        <footer v-if="$slots.footer" class="dialog__footer">
          <slot name="footer"></slot>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-overlay);
  background: rgba(10, 18, 28, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-4);
}
.dialog {
  z-index: var(--z-dialog);
  width: 100%;
  max-width: 480px;
  max-height: 90vh;
  overflow: auto;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-dialog);
}
.dialog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sp-4);
  border-bottom: 1px solid var(--color-border);
}
.dialog__title {
  margin: 0;
  font-size: var(--fs-xl);
  font-weight: var(--fw-semibold);
}
.dialog__close {
  border: 0;
  background: transparent;
  font-size: var(--fs-xl);
  line-height: 1;
  cursor: pointer;
  color: var(--color-text-muted);
}
.dialog__body {
  padding: var(--sp-4);
  color: var(--color-text);
}
.dialog__footer {
  padding: var(--sp-4);
  border-top: 1px solid var(--color-border);
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
}
</style>
