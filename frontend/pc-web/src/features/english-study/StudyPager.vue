<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ total: number }>()
const page = defineModel<number>('page', { required: true })
const size = defineModel<number>('size', { required: true })
const pages = computed(() => Math.max(1, Math.ceil(props.total / size.value)))
</script>
<template>
  <div v-if="total" class="pagination">
    <span class="pagination__info">全 {{ total }} 件（{{ page }} / {{ pages }} ページ）</span>
    <label class="pagination__size">件数 <select v-model="size" class="select" @change="page = 1"><option v-for="n in [20, 50, 100]" :key="n" :value="n">{{ n }} 件</option></select></label>
    <button class="btn btn--secondary btn--sm" :disabled="page <= 1" aria-label="前のページ" @click="page--">‹</button>
    <span>{{ page }}</span><button class="btn btn--secondary btn--sm" :disabled="page >= pages" aria-label="次のページ" @click="page++">›</button>
  </div>
</template>
