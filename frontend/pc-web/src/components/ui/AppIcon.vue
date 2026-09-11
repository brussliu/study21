<script setup lang="ts">
withDefaults(defineProps<{
  name: string
  size?: 'sm' | 'md' | 'lg'
}>(), {
  size: 'md'
})
</script>

<template>
  <svg class="icon" :class="{ 'icon--sm': size === 'sm', 'icon--lg': size === 'lg' }" aria-hidden="true">
    <use :href="`#i-${name}`" />
  </svg>
</template>
