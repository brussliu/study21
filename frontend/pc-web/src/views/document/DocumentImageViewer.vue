<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fileContentUrl, downloadFile } from '@/features/document-files/document-file'
import type { DocumentFileInfo } from '@/api/documents'

/**
 * 画像の拡大表示ビューア。ファイル一覧ダイアログとフォルダビューの資料カードで共有する。
 * 前後キー（← →）で同じ資料内の画像を送り、Esc で閉じる。
 * 回転（左90°/右90°）は表示だけの操作で、元ファイルは変更しない。
 */
const props = defineProps<{ images: DocumentFileInfo[]; current: DocumentFileInfo }>()
const emit = defineEmits<{ 'update:current': [file: DocumentFileInfo]; close: [] }>()

const scale = ref(1)
/** 表示中の回転角（0/90/180/270）。表示のみで、ファイルは書き換えない。 */
const rotation = ref(0)
const stageEl = ref<HTMLElement | null>(null)
const imgEl = ref<HTMLImageElement | null>(null)

/** 回転アニメーションの長さ。CSS transition ではなく rAF で角度と倍率を同時に補間する。 */
const ROTATION_MS = 340

/** 表示中の画像の位置（images 内の添字） */
const index = computed(() => props.images.findIndex((file) => file.branchNo === props.current.branchNo))

/** 実際に描画している角度（アニメーション中は小数）。rotation は確定値（0/90/180/270）。 */
let displayAngle = 0
let frameId = 0

function resetView(): void {
  cancelAnimationFrame(frameId)
  scale.value = 1
  rotation.value = 0
  displayAngle = 0
  applyTransform(displayAngle)
}

/** 角度に応じて「枠に収める倍率」を求める。斜め（45度付近）で最も小さくなり、回転中のはみ出しを防ぐ。 */
function fitScaleFor(angle: number): number {
  const img = imgEl.value
  const stage = stageEl.value
  if (!img || !stage) return 1
  const boxWidth = img.offsetWidth
  const boxHeight = img.offsetHeight
  if (!boxWidth || !boxHeight) return 1
  const radians = (angle * Math.PI) / 180
  const cos = Math.abs(Math.cos(radians))
  const sin = Math.abs(Math.sin(radians))
  const visualWidth = boxWidth * cos + boxHeight * sin
  const visualHeight = boxWidth * sin + boxHeight * cos
  return Math.min(1, (stage.clientWidth * 0.9) / visualWidth, (stage.clientHeight * 0.9) / visualHeight)
}

/** 現在の角度・ズーム・フィット倍率をまとめて画像へ反映する。 */
function applyTransform(angle: number): void {
  const img = imgEl.value
  if (!img) return
  const total = scale.value * fitScaleFor(angle)
  img.style.transform = `rotate(${angle}deg) scale(${total.toFixed(4)})`
}

function easeInOutCubic(t: number): number {
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2
}

/** 現在の角度から target までアニメーションする（途中で押されたら現在地から繋ぐ）。 */
function animateRotationTo(target: number): void {
  cancelAnimationFrame(frameId)
  const from = displayAngle
  const delta = target - from
  if (delta === 0) { applyTransform(target); return }
  const startedAt = performance.now()
  const step = (now: number): void => {
    const progress = Math.min(1, (now - startedAt) / ROTATION_MS)
    displayAngle = from + delta * easeInOutCubic(progress)
    applyTransform(displayAngle)
    if (progress < 1) {
      frameId = requestAnimationFrame(step)
    } else {
      displayAngle = target
      applyTransform(displayAngle)
    }
  }
  frameId = requestAnimationFrame(step)
}

/**
 * 0 度時のフィットサイズをレイアウト枠として決める（回転は transform だけで行い、
 * レイアウト枠は動かさない＝回転中に再レイアウトが起きず、揺れない）。
 */
function layoutImage(): void {
  const img = imgEl.value
  const stage = stageEl.value
  if (!img || !stage || !img.naturalWidth || !img.naturalHeight) return
  const ratio = Math.min(
    (stage.clientWidth * 0.9) / img.naturalWidth,
    (stage.clientHeight * 0.9) / img.naturalHeight,
    1
  )
  img.style.width = `${Math.round(img.naturalWidth * ratio)}px`
  img.style.height = `${Math.round(img.naturalHeight * ratio)}px`
  applyTransform(displayAngle)
}

function rotateBy(degrees: number): void {
  const target = displayAngle + degrees
  rotation.value = ((Math.round(target) % 360) + 360) % 360
  animateRotationTo(target)
}

function setScale(next: number): void {
  scale.value = Math.min(4, Math.max(0.25, next))
  applyTransform(displayAngle)
}

function showAt(target: number): void {
  const file = props.images[target]
  if (!file) return
  emit('update:current', file)
  resetView()
}
function showPrev(): void { if (index.value > 0) showAt(index.value - 1) }
function showNext(): void { if (index.value >= 0 && index.value < props.images.length - 1) showAt(index.value + 1) }

function onImageLoad(): void { layoutImage() }

function onResize(): void { layoutImage() }

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') { emit('close'); return }
  if (event.key === 'ArrowLeft') { event.preventDefault(); showPrev(); return }
  if (event.key === 'ArrowRight') { event.preventDefault(); showNext(); return }
  // 'r' で右回転、'R'（Shift+r）で左回転。
  if (event.key === 'r') { event.preventDefault(); rotateBy(90); return }
  if (event.key === 'R') { event.preventDefault(); rotateBy(-90) }
}

// 表示対象が変わったら回転・ズームを初期化する。
// サイズは新しい画像の読み込み完了（@load）で合わせる。ここで layout すると
// 直前の画像の原寸で1フレームだけ誤ったサイズになり、切り替え時にちらつく。
watch(() => props.current.branchNo, () => { resetView() })

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  cancelAnimationFrame(frameId)
  document.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', onResize)
})
</script>

<template>
  <div class="doc-viewer-overlay" @click.self="emit('close')">
    <div class="doc-viewer">
      <header class="doc-viewer__bar">
        <strong>{{ current.originalFileName }}</strong>
        <span class="doc-viewer__nav">
          <button class="btn btn--secondary btn--sm" :disabled="index <= 0" @click="showPrev">‹ 前へ</button>
          <span class="doc-viewer__counter">{{ index + 1 }} / {{ images.length }}</span>
          <button class="btn btn--secondary btn--sm" :disabled="index < 0 || index >= images.length - 1" @click="showNext">次へ ›</button>
        </span>
        <span>
          <button class="btn btn--secondary btn--sm" title="左に90°回転（Shift+R）" @click="rotateBy(-90)">⟲ 左回転</button>
          <button class="btn btn--secondary btn--sm" title="右に90°回転（R）" @click="rotateBy(90)">⟳ 右回転</button>
          <button class="btn btn--secondary btn--sm" :disabled="rotation === 0 && scale === 1" @click="resetView">リセット</button>
          <button class="btn btn--secondary btn--sm" @click="setScale(scale - .25)">－</button>
          <button class="btn btn--secondary btn--sm" @click="setScale(scale + .25)">＋</button>
          <button class="btn btn--secondary btn--sm" @click="downloadFile(current)">ダウンロード</button>
          <button class="btn btn--secondary btn--sm" @click="emit('close')">閉じる</button></span>
      </header>
      <div ref="stageEl" class="doc-viewer__stage">
        <img ref="imgEl" :src="fileContentUrl(current)" :alt="current.originalFileName" @load="onImageLoad" />
      </div>
      <p class="doc-viewer__hint">← → キーで前後の画像、R / Shift+R で右・左に90°回転（Esc で閉じる）</p>
    </div>
  </div>
</template>

<style scoped>
.doc-viewer-overlay{position:fixed;inset:0;z-index:var(--z-overlay);background:rgba(8,13,19,.75);display:flex;align-items:center;justify-content:center;padding:24px}
.doc-viewer{width:100%;height:100%;display:flex;flex-direction:column}
.doc-viewer__bar{display:flex;align-items:center;gap:12px;padding:12px 16px;border-bottom:1px solid var(--color-border);color:white}
.doc-viewer__bar strong{flex:1}
.doc-viewer__nav{display:inline-flex;align-items:center;gap:8px;flex:none}
.doc-viewer__counter{font-size:var(--fs-xs);color:#cbd5e1;font-variant-numeric:tabular-nums;min-width:52px;text-align:center}
.doc-viewer__stage{flex:1;display:flex;align-items:center;justify-content:center;overflow:hidden}
/* 回転は rAF で角度とフィット倍率を同時に補間するため、CSS transition は付けない
   （付けると角度だけが先行してアニメーションし、回転中に画像が揺れて見える）。 */
.doc-viewer__stage img{transition:none;will-change:transform}
.doc-viewer__hint{margin:0;padding:10px;text-align:center;font-size:var(--fs-xs);color:#94a3b8}
</style>
