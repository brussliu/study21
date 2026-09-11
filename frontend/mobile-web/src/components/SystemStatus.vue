<script setup lang="ts">
import { onMounted } from 'vue'
import { useSystemStore } from '@/stores/system'
import { BaseError, BaseLoading } from '@study21/web-shared'

const system = useSystemStore()

onMounted(() => {
  void system.checkHealth()
})
</script>

<template>
  <section class="system-status" aria-label="システム状態">
    <h2 class="system-status__title">システム状態</h2>
    <BaseLoading v-if="system.loading" label="状態を確認中…" />
    <div v-else class="system-status__list">
      <div class="system-status__item">
        <span class="system-status__label">admin-api</span>
        <span class="system-status__value" :class="`system-status__value--${system.adminHealth.toLowerCase()}`">
          {{ system.adminHealth }}
        </span>
      </div>
      <div class="system-status__item">
        <span class="system-status__label">user-api</span>
        <span class="system-status__value" :class="`system-status__value--${system.userHealth.toLowerCase()}`">
          {{ system.userHealth }}
        </span>
      </div>
    </div>
    <BaseError
      v-if="!system.loading && (system.adminHealth === 'DOWN' || system.userHealth === 'DOWN')"
      title="API に接続できません"
    />
  </section>
</template>

<style scoped>
.system-status {
  padding: var(--sp-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  margin-bottom: var(--sp-4);
}
.system-status__title {
  margin: 0 0 var(--sp-3);
  font-size: var(--fs-lg);
  font-weight: var(--fw-semibold);
}
.system-status__list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}
.system-status__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sp-2) var(--sp-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}
.system-status__label {
  color: var(--color-text-muted);
  font-size: var(--fs-sm);
}
.system-status__value {
  font-weight: var(--fw-semibold);
  font-size: var(--fs-sm);
}
.system-status__value--up {
  color: var(--color-success);
}
.system-status__value--down {
  color: var(--color-danger);
}
.system-status__value--unknown {
  color: var(--color-warning);
}
</style>
