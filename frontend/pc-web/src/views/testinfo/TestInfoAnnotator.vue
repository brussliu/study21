<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import { canvasToBlob, messageOf, rotateDataUrl } from '@/features/testinfo/testinfo-file'

/**
 * 試験用紙画像のビューア + 手書き注釈（2.0 の testinfo_showpic.jsp 相当）。
 * 画像の上に同じ大きさの canvas を重ね、ペンで注釈を描いて「保存」で
 * 画像に焼き込む（回転も焼き込む）。保存処理そのものは親が担当する。
 */
const props = defineProps<{
  open: boolean
  src: string
  fileName: string
  /** 焼き込んだ PNG の保存処理（既存ファイルはサーバ保存、未保存ファイルは差し替え）。 */
  saveHandler: (blob: Blob) => Promise<void>
}>()
const emit = defineEmits<{ close: []; saved: [] }>()

const toast = useToast()

/** 2.0 と同じ 3 色。 */
const PEN_COLORS = [
  { value: '#ff0000', label: '赤' },
  { value: '#0000ff', label: '青' },
  { value: '#000000', label: '黒' }
] as const
/** 2.0 と同じ 4 段階（細/中/太/極太）。 */
const PEN_SIZES = [2, 5, 10, 15] as const

const canvasEl = ref<HTMLCanvasElement | null>(null)
const imageEl = ref<HTMLImageElement | null>(null)
const workingSrc = ref('')
const penColor = ref<string>(PEN_COLORS[0].value)
const penSize = ref<number>(5)
const drawing = ref(false)
const saving = ref(false)
const hasStroke = ref(false)
const lastPoint = ref<{ x: number; y: number } | null>(null)

const canSave = computed(() => !saving.value)

/** 画像を読み込むたびに canvas を原寸へ合わせ、注釈はクリアする（回転時は焼き込み済み）。 */
function resetSurface(): void {
  const canvas = canvasEl.value
  const image = imageEl.value
  if (!canvas || !image) return
  canvas.width = image.naturalWidth || 1
  canvas.height = image.naturalHeight || 1
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  hasStroke.value = false
}

function onImageLoad(): void {
  resetSurface()
}

watch(() => [props.open, props.src] as const, () => {
  if (!props.open) return
  workingSrc.value = props.src
  penSize.value = 5
  hasStroke.value = false
}, { immediate: true })

// ---- 描画 ----
function canvasPointOf(event: PointerEvent): { x: number; y: number } {
  const canvas = canvasEl.value
  if (!canvas) return { x: 0, y: 0 }
  const rect = canvas.getBoundingClientRect()
  const scaleX = rect.width > 0 ? canvas.width / rect.width : 1
  const scaleY = rect.height > 0 ? canvas.height / rect.height : 1
  return { x: (event.clientX - rect.left) * scaleX, y: (event.clientY - rect.top) * scaleY }
}

function applyPenStyle(ctx: CanvasRenderingContext2D): void {
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.strokeStyle = penColor.value
  ctx.lineWidth = penSize.value
}

function onPointerDown(event: PointerEvent): void {
  if (event.button !== 0 || saving.value) return
  const canvas = canvasEl.value
  const ctx = canvas?.getContext('2d')
  if (!canvas || !ctx) return
  drawing.value = true
  applyPenStyle(ctx)
  const point = canvasPointOf(event)
  lastPoint.value = point
  ctx.beginPath()
  ctx.moveTo(point.x, point.y)
  ctx.lineTo(point.x + 0.01, point.y + 0.01)
  ctx.stroke()
  hasStroke.value = true
  canvas.setPointerCapture(event.pointerId)
  event.preventDefault()
}

function onPointerMove(event: PointerEvent): void {
  if (!drawing.value) return
  const ctx = canvasEl.value?.getContext('2d')
  if (!ctx) return
  const point = canvasPointOf(event)
  lastPoint.value = point
  ctx.lineTo(point.x, point.y)
  ctx.stroke()
  event.preventDefault()
}

function onPointerUp(event: PointerEvent): void {
  if (!drawing.value) return
  const ctx = canvasEl.value?.getContext('2d')
  const point = lastPoint.value
  if (ctx && point) { ctx.lineTo(point.x, point.y); ctx.stroke(); ctx.closePath() }
  drawing.value = false
  lastPoint.value = null
  canvasEl.value?.releasePointerCapture?.(event.pointerId)
}

/** 消去: 描いた注釈だけを消す。 */
function clearStrokes(): void {
  resetSurface()
}

/** 初期化: 注釈も回転も元に戻す。 */
function resetAll(): void {
  workingSrc.value = props.src
  hasStroke.value = false
}

// ---- 保存・回転（いずれも画像へ焼き込む） ----
async function mergedCanvas(): Promise<HTMLCanvasElement> {
  const image = imageEl.value
  const canvas = canvasEl.value
  if (!image || !canvas || !image.naturalWidth) throw new Error('画像が読み込まれていません。')
  const merged = document.createElement('canvas')
  merged.width = image.naturalWidth
  merged.height = image.naturalHeight
  const ctx = merged.getContext('2d')
  if (!ctx) throw new Error('画像の合成に失敗しました。')
  ctx.drawImage(image, 0, 0, merged.width, merged.height)
  ctx.drawImage(canvas, 0, 0, merged.width, merged.height)
  return merged
}

async function rotate(degrees: number): Promise<void> {
  if (saving.value) return
  try {
    const merged = await mergedCanvas()
    workingSrc.value = await rotateDataUrl(merged.toDataURL('image/png'), degrees)
    hasStroke.value = false
  } catch (cause) { toast.danger(messageOf(cause)) }
}

async function save(): Promise<void> {
  if (saving.value) return
  saving.value = true
  try {
    const blob = await canvasToBlob(await mergedCanvas())
    await props.saveHandler(blob)
    toast.success('画像を保存しました。')
    emit('saved')
    emit('close')
  } catch (cause) { toast.danger(messageOf(cause)) }
  finally { saving.value = false }
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && !saving.value) emit('close')
}
onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="ti-annot-overlay">
      <section class="ti-annot" role="dialog" aria-modal="true" :aria-label="`注釈 ${fileName}`">
        <header class="ti-annot__head">
          <strong><AppIcon name="edit" size="sm" class="icon--edit" /> 注釈・回転</strong>
          <span class="ti-annot__name" :title="fileName">{{ fileName }}</span>
          <button type="button" class="btn btn--ghost btn--sm" :disabled="saving" @click="emit('close')">閉じる</button>
        </header>

        <div class="ti-annot__tools">
          <span class="ti-annot__group">
            <span class="ti-annot__label">色</span>
            <button
              v-for="color in PEN_COLORS" :key="color.value" type="button"
              class="ti-annot__swatch" :class="{ 'is-active': penColor === color.value }"
              :style="{ background: color.value }" :title="color.label" :aria-label="`ペン色 ${color.label}`"
              :disabled="saving" @click="penColor = color.value"
            ></button>
          </span>
          <span class="ti-annot__group">
            <span class="ti-annot__label">太さ</span>
            <button
              v-for="size in PEN_SIZES" :key="size" type="button"
              class="btn btn--secondary btn--sm" :class="{ 'is-active': penSize === size }"
              :disabled="saving" @click="penSize = size"
            >{{ size }}</button>
          </span>
          <span class="ti-annot__group">
            <button type="button" class="btn btn--secondary btn--sm" :disabled="saving || !hasStroke" @click="clearStrokes">
              <AppIcon name="eraser" size="sm" /> 消去
            </button>
            <button type="button" class="btn btn--secondary btn--sm" :disabled="saving" @click="resetAll">
              <AppIcon name="rotate" size="sm" /> 初期化
            </button>
            <button type="button" class="btn btn--secondary btn--sm" :disabled="saving" @click="rotate(-90)">
              <AppIcon name="chevron-left" size="sm" /> 左90°
            </button>
            <button type="button" class="btn btn--secondary btn--sm" :disabled="saving" @click="rotate(90)">
              右90° <AppIcon name="chevron-right" size="sm" />
            </button>
          </span>
        </div>

        <div class="ti-annot__stage">
          <div class="ti-annot__surface">
            <img
              ref="imageEl" :src="workingSrc" :alt="fileName"
              draggable="false" @load="onImageLoad"
            />
            <canvas
              ref="canvasEl" class="ti-annot__canvas"
              @pointerdown="onPointerDown" @pointermove="onPointerMove"
              @pointerup="onPointerUp" @pointercancel="onPointerUp"
            ></canvas>
          </div>
        </div>

        <footer class="ti-annot__foot">
          <p class="ti-annot__hint">画像の上をドラッグすると注釈を描けます。「保存」で画像に焼き込みます（Esc で閉じる）。</p>
          <span class="ti-annot__spacer"></span>
          <button type="button" class="btn btn--secondary" :disabled="saving" @click="emit('close')">キャンセル</button>
          <button type="button" class="btn btn--primary" :disabled="!canSave" @click="save">
            <AppIcon name="check" size="sm" /> 保存
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.ti-annot-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-dialog);
  background: rgba(8, 13, 19, .78);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.ti-annot {
  width: min(1100px, 100%);
  max-height: 94vh;
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.ti-annot__head,
.ti-annot__tools,
.ti-annot__foot {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--color-border);
}

.ti-annot__head strong {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
}

.ti-annot__name {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-sm);
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ti-annot__tools {
  flex-wrap: wrap;
  gap: var(--sp-4);
  background: var(--color-surface-alt);
}

.ti-annot__group {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
}

.ti-annot__label {
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}

.ti-annot__swatch {
  width: 22px;
  height: 22px;
  border: 2px solid var(--color-border-strong);
  border-radius: var(--radius-pill);
  cursor: pointer;
}

.ti-annot__swatch.is-active,
.ti-annot__tools .btn.is-active {
  outline: 2px solid var(--color-primary-strong);
  outline-offset: 1px;
}

.ti-annot__stage {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: var(--sp-4);
  background: var(--color-bg);
}

.ti-annot__surface {
  position: relative;
  display: inline-block;
  line-height: 0;
}

.ti-annot__surface img {
  display: block;
  max-width: 100%;
  max-height: 62vh;
  user-select: none;
}

.ti-annot__canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  cursor: crosshair;
  touch-action: none;
}

.ti-annot__foot {
  border-bottom: none;
  border-top: 1px solid var(--color-border);
}

.ti-annot__hint {
  margin: 0;
  font-size: var(--fs-xs);
  color: var(--color-text-subtle);
}

.ti-annot__spacer {
  flex: 1;
}
</style>
