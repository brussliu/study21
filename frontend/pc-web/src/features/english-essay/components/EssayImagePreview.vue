<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'

/**
 * 画像の拡大表示（2.0 の `#essayImagePreviewModal` 相当）。
 *
 * <p>2.0 と同じく、**押した画像をそのまま大きく**見せる（元の実寸を超えて引き伸ばさない）。
 * 見出しは画像の名前で、分からないときは「登録画像」（2.0 の `openImagePreview` と同じ）。</p>
 *
 * <p>閉じるのは**右上の ×**・**足元の【閉じる】**・`Esc` の 3 つ
 * （`docs/FRONTEND_GUIDE.md` 3.9。背景クリックでは閉じない）。</p>
 *
 * <p>使う側は `open` / `src` / `title` を渡し、`close` を受ける。窓の骨格（暗幕・パネル・
 * 画像の入れ物）はこの部品が持ち、寸法と余白は 2.0 の `english_essay.css` から写す。</p>
 */
const props = withDefaults(defineProps<{
  /** 窓を開いているか。 */
  open: boolean
  /** 大きく出す画像（API の URL か、まだ上げていない画像のデータ URL）。 */
  src: string
  /** 見出しに出す名前（2.0 はファイル名。分からないときは「登録画像」）。 */
  title?: string
}>(), { title: '登録画像' })

const emit = defineEmits<{ close: [] }>()

/** 右上の ×（開いたらここへ焦点を移す。2.0 の `essayImagePreviewClose.focus()` と同じ）。 */
const closeButton = ref<HTMLButtonElement | null>(null)

function close(): void {
  emit('close')
}

/** 画像ビューアは Esc でも閉じられる（背景クリックでは閉じない）。 */
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
  <div v-if="open" class="overlay ee-image-preview" data-ee-image-preview>
    <section
      class="dialog ee-image-preview__panel" role="dialog" aria-modal="true"
      :aria-label="title"
    >
      <header class="dialog__head ee-image-preview__head">
        <h3 class="dialog__title ee-image-preview__title" data-ee-image-preview-title>{{ title }}</h3>
        <button
          ref="closeButton" type="button" class="dialog__close" aria-label="閉じる"
          data-ee-image-preview-close @click="close"
        >
          <AppIcon name="x" size="sm" />
        </button>
      </header>

      <div class="dialog__body ee-image-preview__body">
        <img :src="src" :alt="title" data-ee-image-preview-image>
      </div>

      <footer class="dialog__foot">
        <button type="button" class="btn btn--secondary" data-ee-image-preview-dismiss @click="close">
          閉じる
        </button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
/* 2.0 の `.essay-image-preview-*` を写す（パネルの寸法・余白・背景の暗さ・画像の最大サイズ）。
   色は tokens.css の変数だけを `color-mix()` で混ぜて作る（生の色は書かない）。 */

/* 2.0 の背景は不透明度 .82 の濃い暗幕 ＋ ぼかし 3px。設計システムの `.overlay`（.5）より濃い */
.ee-image-preview {
  background: color-mix(in srgb, var(--color-text-strong) 82%, transparent);
  backdrop-filter: blur(3px);
}

/* パネルは 2.0 の `width: min(1500px, 96vw)` / `height: min(940px, 94vh)` */
.dialog.ee-image-preview__panel {
  width: min(1500px, 96vw);
  height: min(940px, 94vh);
  max-width: none;
}

/* 見出しは 2.0 の `padding: 13px 16px` ＋ 淡い背景。長い名前は 1 行で省略する */
.ee-image-preview__head {
  padding: 13px 16px;
  background: var(--color-surface-alt);
}

.ee-image-preview__title {
  display: block;
  overflow: hidden;
  font-size: var(--fs-lg);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 画像の入れ物は 2.0 の `padding: 18px` ＋ 淡い背景。中央に置き、はみ出す分はスクロール。
   升（`grid-template-*`）を**確定**させるのが肝: `auto` の升では下の `max-height: 100%` が
   「基準の高さが無い」となって無視され、大きな画像が縦にはみ出す（実機の Chrome で確認） */
.ee-image-preview__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  min-height: 0;
  place-items: center;
  overflow: auto;
  padding: 18px;
  background: var(--color-surface-alt);
}

/* 元の実寸を超えて引き伸ばさない（`contain` で全体を見せる） */
.ee-image-preview__body img {
  display: block;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
}
</style>
