<script setup lang="ts">
import SystemStatus from '@/components/SystemStatus.vue'
import { BaseEmpty } from '@study21/web-shared'
</script>

<template>
  <div class="page">
    <h1 class="page__title">ホーム</h1>
    <SystemStatus />
    <BaseEmpty title="この機能は未実装です" description="業務データは本段階では表示しません。" />
  </div>
</template>

<style scoped>
.page__title {
  margin: 0 0 var(--sp-4);
  font-size: var(--fs-2xl);
  font-weight: var(--fw-semibold);
}
</style>
