<script setup lang="ts">
import { useToast } from '../composables/useToast'
import BaseToast from './BaseToast.vue'

const { items } = useToast()
</script>

<template>
  <div class="toast-host" aria-live="polite">
    <BaseToast v-for="item in items" :key="item.id" :type="item.type" :message="item.message" />
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  top: var(--sp-4);
  right: var(--sp-4);
  z-index: var(--z-toast);
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  max-width: 360px;
}
</style>
