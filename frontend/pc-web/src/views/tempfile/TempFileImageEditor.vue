<script setup lang="ts">
import { ref, watch } from 'vue'
import { ApiError, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import { replaceTempFileImage, saveTempFileImageAs, type TempFileSummary } from '@/api/tempfiles'

const props = defineProps<{ file: TempFileSummary | null; open: boolean }>()
const emit = defineEmits<{ close: []; changed: [] }>()

const toast = useToast()
const working = ref('')
const name = ref('')
const comment = ref('')
const enhance = ref(0)
const busy = ref(false)
const selection = ref<{ x: number; y: number; w: number; h: number } | null>(null)
const dragging = ref(false)
const dragStart = ref<{ x: number; y: number }>({ x: 0, y: 0 })
const stageEl = ref<HTMLElement | null>(null)
const imgEl = ref<HTMLImageElement | null>(null)
const compressedSize = ref<number | null>(null)
const preview = ref('')
let enhanceTimer = 0

// 文書鮮明化は「保存時」だけでなく、スライダー操作に追従してプレビューにも
// リアルタイム反映する。working（生画像）はそのままに、表示用の preview だけ更新。
async function syncPreview(): Promise<void> {
  preview.value = enhance.value <= 0 ? working.value : await enhanceImage(working.value, enhance.value)
}

watch(enhance, () => {
  if (enhanceTimer) clearTimeout(enhanceTimer)
  enhanceTimer = window.setTimeout(() => void syncPreview(), 120)
})

watch(() => [props.open, props.file?.tempFileId] as const, () => {
  if (!props.open || !props.file) return
  working.value = props.file.contentUrl
  preview.value = props.file.contentUrl
  name.value = props.file.originalFileName
  comment.value = props.file.comment ?? ''
  enhance.value = 0
  selection.value = null
  compressedSize.value = null
}, { immediate: true })

function messageOf(cause: unknown): string {
  return cause instanceof ApiError || cause instanceof Error ? cause.message : '処理に失敗しました。'
}

function clamp(v: number, min: number, max: number): number { return Math.min(max, Math.max(min, v)) }

function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('画像の読み込みに失敗しました。'))
    img.src = url
  })
}

function canvasOf(img: HTMLImageElement, w: number, h: number): HTMLCanvasElement {
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')!
  ctx.drawImage(img, 0, 0, w, h)
  return canvas
}

function dataUrlToBlob(dataUrl: string): Promise<Blob> {
  return fetch(dataUrl).then((r) => r.blob())
}

async function rotate(dataUrl: string, degrees: number): Promise<string> {
  const img = await loadImage(dataUrl)
  const rad = ((degrees % 360) + 360) % 360
  const swap = rad === 90 || rad === 270
  const w = swap ? img.naturalHeight : img.naturalWidth
  const h = swap ? img.naturalWidth : img.naturalHeight
  const canvas = canvasOf(img, 0, 0)
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')!
  ctx.translate(w / 2, h / 2)
  ctx.rotate((rad * Math.PI) / 180)
  ctx.drawImage(img, -img.naturalWidth / 2, -img.naturalHeight / 2)
  return canvas.toDataURL('image/png')
}

async function enhanceImage(dataUrl: string, level: number): Promise<string> {
  const strength = clamp(level, 0, 100) / 100
  if (strength <= 0) return dataUrl
  const img = await loadImage(dataUrl)
  const canvas = canvasOf(img, img.naturalWidth, img.naturalHeight)
  const ctx = canvas.getContext('2d')!
  const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height)
  const data = imageData.data
  for (let i = 0; i < data.length; i += 4) {
    const gray = 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2]
    const value = clamp((gray - 128) * (1 + 0.6 * strength) + 128 + 14 * strength, 0, 255)
    data[i] = value
    data[i + 1] = value
    data[i + 2] = value
  }
  ctx.putImageData(imageData, 0, 0)
  return canvas.toDataURL('image/png')
}

async function cropImage(dataUrl: string, sx: number, sy: number, sw: number, sh: number): Promise<string> {
  const img = await loadImage(dataUrl)
  const canvas = document.createElement('canvas')
  canvas.width = sw
  canvas.height = sh
  const ctx = canvas.getContext('2d')!
  ctx.drawImage(img, sx, sy, sw, sh, 0, 0, sw, sh)
  return canvas.toDataURL('image/png')
}

async function compressToMax(dataUrl: string, maxBytes: number): Promise<{ dataUrl: string; size: number }> {
  const blob = await dataUrlToBlob(dataUrl)
  if (blob.size <= maxBytes) return { dataUrl, size: blob.size }
  const img = await loadImage(dataUrl)
  const scales = [1, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.25]
  const qualities = [0.9, 0.8, 0.7, 0.6, 0.5, 0.4]
  for (const scale of scales) {
    for (const quality of qualities) {
      const w = Math.max(1, Math.round(img.naturalWidth * scale))
      const h = Math.max(1, Math.round(img.naturalHeight * scale))
      const canvas = document.createElement('canvas')
      canvas.width = w
      canvas.height = h
      const ctx = canvas.getContext('2d')!
      ctx.fillStyle = '#ffffff'
      ctx.fillRect(0, 0, w, h)
      ctx.drawImage(img, 0, 0, w, h)
      const out = canvas.toDataURL('image/jpeg', quality)
      const outBlob = await dataUrlToBlob(out)
      if (outBlob.size <= maxBytes) return { dataUrl: out, size: outBlob.size }
    }
  }
  return { dataUrl, size: blob.size }
}

function ensureExtension(fileName: string, mimeType: string): string {
  if (fileName.includes('.')) return fileName
  return mimeType === 'image/png' ? `${fileName}.png` : `${fileName}.jpg`
}

async function buildFinalFile(): Promise<{ file: File; name: string }> {
  let dataUrl = working.value
  const source = await loadImage(dataUrl)
  const naturalW = source.naturalWidth
  const naturalH = source.naturalHeight
  const sel = selection.value
  if (sel && sel.w > 0.02 && sel.h > 0.02) {
    const sx = clamp(Math.round(sel.x * naturalW), 0, naturalW - 1)
    const sy = clamp(Math.round(sel.y * naturalH), 0, naturalH - 1)
    const sw = Math.max(1, Math.round(sel.w * naturalW))
    const sh = Math.max(1, Math.round(sel.h * naturalH))
    dataUrl = await cropImage(dataUrl, sx, sy, sw, sh)
  }
  dataUrl = await enhanceImage(dataUrl, enhance.value)
  const compressed = await compressToMax(dataUrl, 8 * 1024 * 1024)
  const blob = await dataUrlToBlob(compressed.dataUrl)
  const finalName = ensureExtension(name.value.trim() || props.file!.originalFileName, blob.type)
  return { file: new File([blob], finalName, { type: blob.type }), name: finalName }
}

async function save(): Promise<void> {
  if (!props.file) return
  busy.value = true
  try {
    const built = await buildFinalFile()
    await replaceTempFileImage(props.file.tempFileId, built.file, built.name, comment.value)
    toast.success('画像の編集内容を保存しました。')
    emit('changed')
    emit('close')
  } catch (cause) { toast.danger(messageOf(cause)) }
  finally { busy.value = false }
}

async function saveAs(): Promise<void> {
  if (!props.file) return
  busy.value = true
  try {
    const built = await buildFinalFile()
    await saveTempFileImageAs(props.file.tempFileId, built.file, built.name, comment.value)
    toast.success('別名保存しました。')
    emit('changed')
    emit('close')
  } catch (cause) { toast.danger(messageOf(cause)) }
  finally { busy.value = false }
}

async function rotateLeft(): Promise<void> {
  working.value = await rotate(working.value, -90)
  selection.value = null
  await syncPreview()
}
async function rotateRight(): Promise<void> {
  working.value = await rotate(working.value, 90)
  selection.value = null
  await syncPreview()
}

async function compress(): Promise<void> {
  try {
    const result = await compressToMax(working.value, 8 * 1024 * 1024)
    if (result.dataUrl === working.value) { toast.info('すでに 8MB 以下です。'); return }
    working.value = result.dataUrl
    compressedSize.value = result.size
    await syncPreview()
    toast.success('8MB 以下に圧縮しました。')
  } catch (cause) { toast.danger(messageOf(cause)) }
}

function reset(): void {
  if (!props.file) return
  working.value = props.file.contentUrl
  preview.value = props.file.contentUrl
  enhance.value = 0
  selection.value = null
  compressedSize.value = null
}

// ---- 切り抜き（ドラッグで範囲選択、正規化座標 0..1） ----
function pointOf(event: PointerEvent): { x: number; y: number } {
  const img = imgEl.value
  if (!img) return { x: 0, y: 0 }
  const rect = img.getBoundingClientRect()
  return {
    x: clamp((event.clientX - rect.left) / rect.width, 0, 1),
    y: clamp((event.clientY - rect.top) / rect.height, 0, 1)
  }
}
function onPointerDown(event: PointerEvent): void {
  if (event.button !== 0) return
  dragging.value = true
  dragStart.value = pointOf(event)
  selection.value = { x: dragStart.value.x, y: dragStart.value.y, w: 0, h: 0 }
  stageEl.value?.setPointerCapture(event.pointerId)
}
function onPointerMove(event: PointerEvent): void {
  if (!dragging.value) return
  const p = pointOf(event)
  selection.value = {
    x: Math.min(dragStart.value.x, p.x),
    y: Math.min(dragStart.value.y, p.y),
    w: Math.abs(p.x - dragStart.value.x),
    h: Math.abs(p.y - dragStart.value.y)
  }
}
function onPointerUp(): void {
  dragging.value = false
  if (selection.value && (selection.value.w < 0.02 || selection.value.h < 0.02)) selection.value = null
}

const cropStyle = (): Record<string, string> => {
  const sel = selection.value
  if (!sel) return { display: 'none' }
  return {
    display: 'block',
    left: `${sel.x * 100}%`,
    top: `${sel.y * 100}%`,
    width: `${sel.w * 100}%`,
    height: `${sel.h * 100}%`
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open && file" class="tf-editor-overlay">
      <section class="tf-editor" role="dialog" aria-modal="true" :aria-label="`画像編集 ${file.originalFileName}`">
        <header class="tf-editor__head">
          <strong><AppIcon name="image" size="sm" /> 画像編集</strong>
          <span class="tf-editor__sub">{{ name }}</span>
          <button type="button" class="btn btn--ghost btn--sm" @click="emit('close')">閉じる</button>
        </header>

        <div class="tf-editor__body">
          <div class="tf-editor__stage" ref="stageEl"
               @pointerdown="onPointerDown" @pointermove="onPointerMove" @pointerup="onPointerUp" @pointercancel="onPointerUp">
            <img ref="imgEl" :src="preview" :alt="name" draggable="false" />
            <div class="tf-editor__crop" :style="cropStyle()"></div>
          </div>
          <div class="tf-editor__side">
            <label class="tf-editor__field">
              <span>ファイル名</span>
              <input v-model="name" class="input" type="text" maxlength="255" />
            </label>
            <label class="tf-editor__field">
              <span>コメント</span>
              <textarea v-model="comment" class="input" rows="3" placeholder="コメントを入力してください"></textarea>
            </label>
            <label class="tf-editor__field">
              <span>文書鮮明化 {{ enhance }}%</span>
              <input v-model.number="enhance" type="range" min="0" max="100" step="5" />
            </label>
            <p class="tf-editor__meta">元サイズ {{ (file.fileSize / 1024).toFixed(1) }} KB<template v-if="compressedSize != null"> ／ 圧縮後 {{ (compressedSize / 1024).toFixed(1) }} KB</template></p>
            <p class="tf-editor__hint">プレビュー上をドラッグすると切り抜き範囲を指定できます。</p>
          </div>
        </div>

        <footer class="tf-editor__foot">
          <button type="button" class="btn btn--secondary btn--sm" :disabled="busy" @click="rotateLeft">左回転</button>
          <button type="button" class="btn btn--secondary btn--sm" :disabled="busy" @click="rotateRight">右回転</button>
          <button type="button" class="btn btn--secondary btn--sm" :disabled="busy" @click="compress">8MB以下に圧縮</button>
          <button type="button" class="btn btn--secondary btn--sm" :disabled="busy" @click="reset">リセット</button>
          <span class="tf-editor__spacer"></span>
          <button type="button" class="btn btn--secondary btn--sm" :disabled="busy" @click="emit('close')">キャンセル</button>
          <button type="button" class="btn btn--secondary btn--sm" :disabled="busy" @click="saveAs">
            <AppIcon name="copy" size="sm" /> 別名保存
          </button>
          <button type="button" class="btn btn--primary btn--sm" :disabled="busy" @click="save">
            <AppIcon name="check" size="sm" /> 保存
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>
