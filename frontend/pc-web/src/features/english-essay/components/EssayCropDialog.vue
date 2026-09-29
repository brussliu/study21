<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { cropSourceRect, fitCrop } from '../cropGeometry'
import { jpegFileName } from '../cropFileName'

/**
 * 画像トリミング（2.0 の `#essayCropModal` 相当）。
 *
 * <p>2.0 は「ファイルを選ぶ → トリミング窓が出る → 【この範囲で保存】で確定」だった。
 * 同じ流れを**自前の Canvas**（`drawImage`）で作る。サーバーへは送らない（画像はデータ URL の
 * まま持つ）。操作方法も 2.0 のまま: 四隅でサイズ変更・枠内をドラッグで移動・画像上を
 * ドラッグで範囲を選び直す・左／右回転。</p>
 *
 * <p><b>座標の基準</b>: 画像と選択枠を同じ枠（`.ee-crop__frame`）の中に置き、選択枠の座標は
 * その枠の左上からの px にする。切り出しの計算は {@link cropSourceRect}（純関数・テスト済み）に任せ、
 * **選んだ範囲だけ**を書き出す。</p>
 */

const props = withDefaults(defineProps<{
  open: boolean
  /** 対象の画像（データ URL。ドロップ直後は FileReader を通したもの）。 */
  src: string
  fileName?: string
}>(), {
  fileName: 'image'
})

const emit = defineEmits<{
  confirm: [result: { dataUrl: string; fileName: string }]
  cancel: []
}>()

/** 表示の枠（トリミング窓の外側。空きサイズを測るのに使う）。 */
const stageEl = ref<HTMLElement | null>(null)
/** 画像と選択枠を載せる枠（**座標の基準**）。 */
const frameEl = ref<HTMLElement | null>(null)
/** 表示中の画像。 */
const imageEl = ref<HTMLImageElement | null>(null)
const loaded = ref(false)
/** 枠の大きさ（回転後の見た目。px）。 */
const frame = ref({ width: 1, height: 1 })
/** `<img>` に指定する大きさ（回転前。px）。 */
const inner = ref({ width: 1, height: 1 })
/** 選択枠（枠の左上からの px）。 */
const selection = ref({ x: 0, y: 0, width: 1, height: 1 })
/** 回転角（0 / 90 / 180 / 270）。 */
const angle = ref(0)
const error = ref('')

/** 元画像の実寸（回転は含まない）。 */
function naturalSize(): { width: number; height: number } {
  return {
    width: imageEl.value?.naturalWidth ?? 0,
    height: imageEl.value?.naturalHeight ?? 0
  }
}

/**
 * 表示の大きさを枠に合わせる（2.0 の `fitCropImage` と同じ）。
 *
 * <p>実寸が 0（読み込み前・テスト環境）のときは何もしない。</p>
 */
function fit(): void {
  const natural = naturalSize()
  if (natural.width <= 0 || natural.height <= 0) {
    return
  }
  const available = {
    width: stageEl.value?.clientWidth ?? natural.width,
    height: stageEl.value?.clientHeight ?? natural.height
  }
  const fitted = fitCrop(natural.width, natural.height, angle.value, available)
  frame.value = fitted.frame
  inner.value = fitted.inner
}

/** 選択枠を初期位置（8% 内側）に戻す。 */
function resetSelection(): void {
  selection.value = {
    x: Math.round(frame.value.width * 0.08),
    y: Math.round(frame.value.height * 0.08),
    width: Math.max(1, Math.round(frame.value.width * 0.84)),
    height: Math.max(1, Math.round(frame.value.height * 0.84))
  }
}

function onImageLoad(): void {
  loaded.value = true
  error.value = ''
  void nextTick(() => {
    fit()
    resetSelection()
  })
}

function onImageError(): void {
  loaded.value = false
  error.value = '画像を読み込めませんでした。'
}

watch(() => [props.open, props.src], () => {
  angle.value = 0
  loaded.value = false
  error.value = ''
  if (!props.open || props.src === '') {
    return
  }
  void nextTick(() => {
    fit()
    resetSelection()
  })
}, { immediate: true })

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

/** 枠の左上を基準にした座標（表示座標）。 */
function pointOf(event: PointerEvent): { x: number; y: number } | null {
  const element = frameEl.value
  if (!element) {
    return null
  }
  const rect = element.getBoundingClientRect()
  return {
    x: clamp(event.clientX - rect.left, 0, frame.value.width),
    y: clamp(event.clientY - rect.top, 0, frame.value.height)
  }
}

type Mode = '' | 'move' | 'resize' | 'draw'

/** ドラッグ中の状態（画面ごとに 1 つでよい）。 */
const drag = ref<{
  mode: Mode
  handle: string
  startX: number
  startY: number
  start: { x: number; y: number; width: number; height: number }
} | null>(null)

/** 四隅のハンドル（左上から時計回り）。 */
const HANDLES = ['nw', 'ne', 'se', 'sw'] as const

function onPointerDown(event: PointerEvent): void {
  const point = pointOf(event)
  if (!point) {
    return
  }
  const target = event.target as HTMLElement
  const corner = target.dataset.handle ?? ''
  let next: Mode = 'draw'
  if (corner !== '') {
    next = 'resize'
  } else if (target.closest('[data-ee-crop-selection]')) {
    next = 'move'
  }
  drag.value = {
    mode: next,
    handle: corner,
    startX: point.x,
    startY: point.y,
    start: { ...selection.value }
  }
  if (next === 'draw') {
    selection.value = { x: point.x, y: point.y, width: 1, height: 1 }
  }
  ;(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId)
  event.preventDefault()
}

function onPointerMove(event: PointerEvent): void {
  const current = drag.value
  if (!current || current.mode === '') {
    return
  }
  const point = pointOf(event)
  if (!point) {
    return
  }
  const minSize = 32
  const maxWidth = frame.value.width
  const maxHeight = frame.value.height
  const dx = point.x - current.startX
  const dy = point.y - current.startY
  const next = { ...current.start }
  if (current.mode === 'move') {
    next.x = clamp(next.x + dx, 0, Math.max(0, maxWidth - next.width))
    next.y = clamp(next.y + dy, 0, Math.max(0, maxHeight - next.height))
  } else if (current.mode === 'draw') {
    next.x = clamp(Math.min(current.startX, point.x), 0, Math.max(0, maxWidth - minSize))
    next.y = clamp(Math.min(current.startY, point.y), 0, Math.max(0, maxHeight - minSize))
    next.width = clamp(Math.abs(dx), minSize, maxWidth - next.x)
    next.height = clamp(Math.abs(dy), minSize, maxHeight - next.y)
  } else {
    const corner = current.handle
    if (corner.includes('n')) {
      const top = clamp(next.y + dy, 0, next.y + next.height - minSize)
      next.height += next.y - top
      next.y = top
    }
    if (corner.includes('s')) {
      next.height = clamp(next.height + dy, minSize, maxHeight - next.y)
    }
    if (corner.includes('w')) {
      const left = clamp(next.x + dx, 0, next.x + next.width - minSize)
      next.width += next.x - left
      next.x = left
    }
    if (corner.includes('e')) {
      next.width = clamp(next.width + dx, minSize, maxWidth - next.x)
    }
  }
  selection.value = next
}

function onPointerUp(): void {
  drag.value = null
}

function rotate(degrees: number): void {
  angle.value = ((angle.value + degrees) % 360 + 360) % 360
  void nextTick(() => {
    fit()
    resetSelection()
  })
}

/** 元画像（回転後）の切り出し範囲（実寸 px）。 */
function sourceRect(): { sx: number; sy: number; sw: number; sh: number } {
  const natural = naturalSize()
  return cropSourceRect(selection.value, frame.value, natural.width, natural.height, angle.value)
}

const metaSize = computed(() => {
  const rect = sourceRect()
  return `サイズ: ${rect.sw} x ${rect.sh}px`
})

const metaPosition = computed(() => {
  const rect = sourceRect()
  return `位置: (${rect.sx}, ${rect.sy})`
})

const targetLabel = computed(() => `${props.fileName} の範囲を調整します。`)

/**
 * 選択範囲を Canvas へ描いてデータ URL にする。
 *
 * <p>手順: ① 元画像を**回転させた**大きさの Canvas に描く（見た目と同じ向き）→
 * ② その中から**選んだ範囲だけ**を切り出して書き出す。Canvas が使えない環境
 * （実寸が取れていない・テスト環境）では元のデータ URL を返す（操作を無かったことにしない）。
 * そのときは `jpeg` を false にし、**元の名前のまま**にする。</p>
 */
function cropImage(): { dataUrl: string; jpeg: boolean } {
  const image = imageEl.value
  const natural = naturalSize()
  if (!image || !loaded.value || natural.width <= 0 || natural.height <= 0) {
    return { dataUrl: props.src, jpeg: false }
  }
  const turn = angle.value === 90 || angle.value === 270
    ? { width: natural.height, height: natural.width }
    : { width: natural.width, height: natural.height }

  const stage = document.createElement('canvas')
  stage.width = turn.width
  stage.height = turn.height
  const stageContext = stage.getContext('2d')
  if (!stageContext) {
    return { dataUrl: props.src, jpeg: false }
  }
  stageContext.translate(turn.width / 2, turn.height / 2)
  stageContext.rotate((angle.value * Math.PI) / 180)
  stageContext.drawImage(image, -natural.width / 2, -natural.height / 2, natural.width, natural.height)

  const rect = sourceRect()
  const out = document.createElement('canvas')
  out.width = rect.sw
  out.height = rect.sh
  const outContext = out.getContext('2d')
  if (!outContext) {
    return { dataUrl: props.src, jpeg: false }
  }
  outContext.drawImage(stage, rect.sx, rect.sy, rect.sw, rect.sh, 0, 0, rect.sw, rect.sh)
  return { dataUrl: out.toDataURL('image/jpeg', 0.92), jpeg: true }
}

/** 【この範囲で保存】。 */
function apply(): void {
  try {
    const cropped = cropImage()
    emit('confirm', {
      dataUrl: cropped.dataUrl,
      // 書き出しは JPEG なので、名前の拡張子も実体に合わせる（`.png` のままにしない）
      fileName: cropped.jpeg ? jpegFileName(props.fileName) : props.fileName
    })
  } catch {
    error.value = '画像を保存できませんでした。もう一度お試しください。'
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="overlay" data-ee-crop>
      <section class="dialog dialog--lg ee-crop__dialog" role="dialog" aria-modal="true" aria-label="画像トリミング">
        <header class="dialog__head">
          <h2 class="dialog__title"><AppIcon name="crop" size="sm" /> 画像トリミング</h2>
          <span class="dialog__subtitle">{{ targetLabel }}</span>
          <button type="button" class="dialog__close" data-ee-crop-cancel aria-label="閉じる" @click="emit('cancel')">
            <AppIcon name="x" size="sm" />
          </button>
        </header>

        <div class="dialog__body ee-crop__body">
          <div
            ref="stageEl" class="ee-crop__stage"
            @pointerdown="onPointerDown"
            @pointermove="onPointerMove"
            @pointerup="onPointerUp"
            @pointercancel="onPointerUp"
          >
            <!-- 画像と選択枠を同じ枠に載せる（座標の基準をそろえる） -->
            <div
              ref="frameEl" class="ee-crop__frame" data-ee-crop-frame
              :style="{ width: `${frame.width}px`, height: `${frame.height}px` }"
            >
              <img
                ref="imageEl" class="ee-crop__image" :src="src" :alt="fileName"
                :style="{
                  width: `${inner.width}px`,
                  height: `${inner.height}px`,
                  transform: `translate(-50%, -50%) rotate(${angle}deg)`
                }"
                @load="onImageLoad" @error="onImageError"
              >
              <div
                class="ee-crop__selection"
                data-ee-crop-selection
                :style="{
                  left: `${selection.x}px`,
                  top: `${selection.y}px`,
                  width: `${selection.width}px`,
                  height: `${selection.height}px`
                }"
              >
                <span
                  v-for="corner in HANDLES" :key="corner" class="ee-crop__handle"
                  :class="`ee-crop__handle--${corner}`" :data-handle="corner"
                ></span>
              </div>
            </div>
          </div>

          <aside class="ee-crop__side">
            <div class="ee-crop__side-card">
              <strong>操作方法</strong>
              <ul class="ee-crop__hints">
                <li>枠内をドラッグして移動</li>
                <li>四隅をドラッグしてサイズ変更</li>
                <li>画像上をドラッグして範囲を選択</li>
              </ul>
            </div>
            <div class="ee-crop__side-card">
              <strong>選択中</strong>
              <p class="ee-crop__meta" data-ee-crop-size>{{ metaSize }}</p>
              <p class="ee-crop__meta" data-ee-crop-position>{{ metaPosition }}</p>
            </div>
            <p v-if="error" class="alert alert--danger">{{ error }}</p>
          </aside>
        </div>

        <footer class="dialog__foot ee-crop__foot">
          <button type="button" class="btn btn--secondary" data-ee-crop-rotate-left @click="rotate(-90)">
            <AppIcon name="rotate" size="sm" /> 左回転
          </button>
          <button type="button" class="btn btn--secondary" data-ee-crop-rotate-right @click="rotate(90)">
            <AppIcon name="rotate" size="sm" /> 右回転
          </button>
          <span class="ee-crop__spacer"></span>
          <button type="button" class="btn btn--secondary" data-ee-crop-cancel-foot @click="emit('cancel')">キャンセル</button>
          <button type="button" class="btn btn--primary" data-ee-crop-apply @click="apply">
            <AppIcon name="check" size="sm" /> この範囲で保存
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>
