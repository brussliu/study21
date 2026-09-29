<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { countWords } from '../types'

/**
 * 本文（作文の設問・手書き作文）の拡大表示（2.0 の `#essayTextModal` 相当）。
 *
 * <p>2.0 の詳細は `textarea` で**編集もできた**が、2.1 の詳細は閲覧の画面（保存の導線が無い）なので、
 * **読むだけ**で出す（利用者の選択）。見た目は 2.0 の窓（Georgia・行間 1.9・広い余白）と、
 * 2.1 の拡大編集（`EssayForm.vue` の `.ee-text-zoom`）にそろえる。</p>
 *
 * <p>閉じるのは**右上の ×**・**足元の【閉じる】**・`Esc` の 3 つ
 * （`docs/FRONTEND_GUIDE.md` 3.9。背景クリックでは閉じない）。</p>
 */
const props = withDefaults(defineProps<{
  /** 窓を開いているか。 */
  open: boolean
  /** 見出しに出す名前（「作文の設問」「手書き作文」）。 */
  title: string
  /** 大きく出す本文（保存されている全文）。 */
  text: string
  /** 窓の上の小さな見出し（2.0 の `essayTextModalCategory` 相当）。 */
  category?: string
}>(), { category: '保存データ' })

const emit = defineEmits<{ close: [] }>()

/** 右上の ×（開いたらここへ焦点を移す。画像の拡大と同じ）。 */
const closeButton = ref<HTMLButtonElement | null>(null)

function close(): void {
  emit('close')
}

/** 本文ビューアは Esc でも閉じられる（背景クリックでは閉じない）。 */
function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && props.open) {
    close()
  }
}

watch(() => props.open, async (open) => {
  if (!open) {
    return
  }
  await nextTick()
  closeButton.value?.focus()
})

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div v-if="open" class="overlay" data-ee-text-preview>
    <section
      class="dialog ee-text-preview" role="dialog" aria-modal="true"
      :aria-label="title"
    >
      <header class="dialog__head">
        <div class="ee-text-preview__head-main">
          <span class="ee-text-preview__category">{{ category }}</span>
          <h2 class="dialog__title" data-ee-text-preview-title>{{ title }}</h2>
        </div>
        <button
          ref="closeButton" type="button" class="dialog__close" aria-label="閉じる"
          data-ee-text-preview-close @click="close"
        >
          <AppIcon name="x" size="sm" />
        </button>
      </header>

      <div class="dialog__body ee-text-preview__body">
        <p class="ee-text-preview__text" data-ee-text-preview-text>{{ text }}</p>
      </div>

      <footer class="dialog__foot ee-text-preview__foot">
        <span class="ee-text-preview__count" data-ee-text-preview-count>
          {{ countWords(text) }} words ・ {{ text.length }} 文字
        </span>
        <button type="button" class="btn btn--secondary" data-ee-text-preview-dismiss @click="close">
          閉じる
        </button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
/* 寸法と余白は 2.1 の拡大編集（`EssayForm.vue` の `.ee-text-zoom`）と同じにして、
   同じ本文でも新規・編集と詳細で窓の大きさが変わらないようにする。 */
.ee-text-preview {
  display: flex;
  flex-direction: column;
  width: min(1100px, 94vw);
  height: min(80vh, 760px);
}

.ee-text-preview__category {
  color: var(--color-text-subtle);
  font-size: var(--fs-2xs);
  font-weight: 600;
  letter-spacing: 0.08em;
}

/* 見出しのまとまりを伸ばして、右上の × を右端へ寄せる（設計システムの `.dialog__title { flex: 1 }`
   の意図。包む `<div>` が伸びないと × が題の隣に来てしまう） */
.ee-text-preview__head-main {
  flex: 1;
  min-width: 0;
}

.ee-text-preview__head-main .dialog__title {
  display: block;
}

/* 本文は 2.0 の `#essayTextModalEditor` と同じ書体・大きさ・行間。長い作文は縦に送る */
.ee-text-preview__body {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: var(--sp-4);
}

.ee-text-preview__text {
  flex: 1;
  margin: 0;
  padding: var(--sp-4);
  font-family: Georgia, 'Times New Roman', serif;
  font-size: var(--fs-lg);
  line-height: 1.9;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

/* 足元は語数・文字数と【閉じる】を左右に分ける（`.dialog__foot` の並びに任せない） */
.ee-text-preview__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
}

.ee-text-preview__count {
  color: var(--color-text-subtle);
  font-size: var(--fs-xs);
}
</style>
