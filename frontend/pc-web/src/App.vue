<script setup lang="ts">
import { RouterView } from 'vue-router'
import { ToastHost } from '@study21/web-shared'
import AppIconSprite from '@/components/ui/AppIconSprite.vue'
</script>

<template>
  <AppIconSprite />
  <RouterView />
  <ToastHost />
</template>
