<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <div class="page-head">
    <div>
      <h1 class="page-head__title">{{ title }}</h1>
      <p v-if="subtitle" class="page-head__sub">{{ subtitle }}</p>
    </div>
    <div class="page-head__actions">
      <slot name="actions"></slot>
    </div>
  </div>
</template>
