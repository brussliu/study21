<script setup lang="ts">
defineProps<{
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  type?: 'button' | 'submit' | 'reset'
  disabled?: boolean
}>()
</script>

<template>
  <button
    class="base-button"
    :class="[`base-button--${variant ?? 'primary'}`, { 'base-button--disabled': disabled }]"
    :type="type ?? 'button'"
    :disabled="disabled"
  >
    <slot></slot>
  </button>
</template>

<style scoped>
.base-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-2);
  min-height: var(--control-md);
  padding: 0 var(--sp-4);
  border-radius: var(--radius-md);
  border: 1px solid transparent;
  font-size: var(--fs-sm);
  font-weight: var(--fw-medium);
  cursor: pointer;
}
.base-button--primary {
  background: var(--color-primary);
  color: var(--color-on-primary);
}
.base-button--primary:hover {
  background: var(--color-primary-hover);
}
.base-button--secondary {
  background: var(--color-surface);
  color: var(--color-text);
  border-color: var(--color-border-strong);
}
.base-button--ghost {
  background: transparent;
  color: var(--color-text-muted);
}
.base-button--danger {
  background: var(--color-danger);
  color: #ffffff;
}
.base-button--disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
