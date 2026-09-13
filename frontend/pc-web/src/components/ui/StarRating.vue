<script setup lang="ts">
import { ref } from 'vue'

/**
 * 1〜5 の星評価（マウスで選ぶ入力）。
 *
 * ・星を押すとその値になる（例: 4 つ目を押すと 4）
 * ・**同じ星をもう一度押すと未入力に戻す**（未入力にできる項目のため）
 * ・マウスを重ねているあいだは、その値をプレビュー表示する
 * ・キーボードでも使える（星は button。Tab で移動、Enter / Space で決定）
 */
const props = defineProps<{
  modelValue: number | null
  label: string
  testid?: string
  max?: number
}>()

const emit = defineEmits<{ 'update:modelValue': [number | null] }>()

const hovered = ref<number | null>(null)
const max = (): number => props.max ?? 5
const shown = (): number => hovered.value ?? props.modelValue ?? 0

function pick(value: number): void {
  emit('update:modelValue', props.modelValue === value ? null : value)
}
</script>

<template>
  <span class="star-rating" :data-testid="testid" :data-value="modelValue ?? ''">
    <button
      v-for="value in max()" :key="value" type="button"
      class="star-rating__star" :class="{ 'is-on': value <= shown() }"
      :aria-label="`${label} ${value}`" :aria-pressed="modelValue === value"
      :data-star="value"
      :title="modelValue === value ? `${value} をもう一度押すと未入力に戻します` : `${label}を ${value} にする`"
      @click="pick(value)"
      @mouseenter="hovered = value"
      @mouseleave="hovered = null"
      @focus="hovered = value"
      @blur="hovered = null"
    >{{ value <= shown() ? '★' : '☆' }}</button>
    <span class="star-rating__value">{{ modelValue === null ? '未入力' : `${modelValue}/5` }}</span>
  </span>
</template>

<style scoped>
/* 色は tokens.css の変数のみ */
.star-rating {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.star-rating__star {
  padding: 0 1px;
  border: 0;
  background: transparent;
  color: var(--color-border-strong);
  font-size: var(--fs-lg);
  line-height: 1;
  cursor: pointer;
}

/* 選ばれている星は評価の色（オレンジ） */
.star-rating__star.is-on {
  color: var(--color-warning);
}

.star-rating__star:hover,
.star-rating__star:focus-visible {
  color: var(--color-warning);
  outline: none;
  transform: scale(1.1);
}

.star-rating__value {
  margin-left: var(--sp-1);
  font-size: var(--fs-sm);
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}
</style>
