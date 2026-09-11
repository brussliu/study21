<script setup lang="ts">
import rawSprite from '@/assets/icons/icons.svg?raw'

const sprite = rawSprite.replace(
  'style="display:none"',
  'aria-hidden="true" focusable="false"'
)
</script>

<template>
  <!-- Bundled repository asset; no user-controlled HTML reaches this sink. -->
  <!-- eslint-disable-next-line vue/no-v-html -->
  <div class="app-icon-sprite" aria-hidden="true" v-html="sprite"></div>
</template>

<style>
.app-icon-sprite {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
  pointer-events: none;
}

.app-icon-sprite > svg {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
}
</style>
