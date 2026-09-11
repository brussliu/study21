<script setup lang="ts">
import SystemStatus from '@/components/SystemStatus.vue'
import { BaseEmpty } from '@study21/web-shared'
</script>

<template>
  <div class="page">
    <SystemStatus />
    <BaseEmpty title="この機能は未実装です" description="業務データは本段階では表示しません。" />
  </div>
</template>
