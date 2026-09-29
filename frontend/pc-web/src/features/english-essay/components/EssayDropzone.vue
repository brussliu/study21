<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'

/**
 * 英作文の画像ドロップゾーン（2.0 の `#essayDropzone` 相当）。
 *
 * <p>ここでは**受け取って渡すだけ**にする。取り込みの順番・上限・トリミング窓を通す手順は
 * 呼ぶ側（`EssayForm`）が決める（2.0 も同じで、`addFiles` が窓を開いていた）。</p>
 *
 * <p>上限（枚数・1 枚の大きさ）は**サーバーが決める**（`GET /api/user/english-essays/limits`）。
 * まだ取れていないときは `null`（＝「不明」）を渡す＝**何も制限しない**（最終的な判定はサーバーで、
 * 超えていれば日本語の理由が返る）。ここに `8 枚`／`10MB` を書き写すとサーバーの設定とずれる。</p>
 */

const props = withDefaults(defineProps<{
  /** 付けられる枚数。`null` は「不明」＝制限しない。 */
  max: number | null
  /** 1 枚の大きさ（MB）。`null` は「不明」＝制限しない。 */
  maxImageMb: number | null
  disabled: boolean
}>(), {
  max: null,
  maxImageMb: null,
  disabled: false
})

const emit = defineEmits<{ picked: [files: File[]]; rejected: [message: string] }>()

const input = ref<HTMLInputElement | null>(null)
const dragging = ref(false)

/** 見出しに出す上限（取れていない項目は出さない）。 */
const limitLabel = computed<string>(() => {
  const count = props.max === null ? '' : `最大${props.max}枚`
  const size = props.maxImageMb === null ? '' : `1枚${props.maxImageMb}MBまで`
  if (count !== '' && size !== '') {
    return `・${count}（${size}）`
  }
  return count === '' && size === '' ? '' : `・${count}${size}`
})

/** 選ばれたファイルを検査して渡す（画像以外・大きすぎるものは日本語で知らせる）。 */
function accept(files: FileList | null | undefined): void {
  const list = Array.from(files ?? [])
  if (list.length === 0) {
    return
  }
  const images = list.filter((file) => file.type.startsWith('image/'))
  // 上限が不明のときは大きさで弾かない（最終的な判定はサーバー）
  const maxBytes = props.maxImageMb === null ? null : props.maxImageMb * 1024 * 1024
  const accepted = maxBytes === null ? images : images.filter((file) => file.size <= maxBytes)
  const rejected: string[] = []
  if (accepted.length < images.length && props.maxImageMb !== null) {
    rejected.push(`${props.maxImageMb}MB を超える画像は追加できません。`)
  }
  if (images.length < list.length) {
    rejected.push('画像ファイル（JPG・PNG・WEBP）を選んでください。')
  }
  if (accepted.length > 0) {
    emit('picked', accepted)
  }
  if (rejected.length > 0) {
    emit('rejected', rejected.join(' '))
  }
}

function onChange(event: Event): void {
  const target = event.target as HTMLInputElement
  accept(target.files)
  target.value = ''
}

function onDrop(event: DragEvent): void {
  dragging.value = false
  if (props.disabled) {
    return
  }
  accept(event.dataTransfer?.files)
}

function onDragOver(event: DragEvent): void {
  if (props.disabled) {
    return
  }
  dragging.value = true
  // 既定の動作（ブラウザが画像を開く）を止める
  event.preventDefault()
}

function open(): void {
  if (props.disabled) {
    return
  }
  input.value?.click()
}
</script>

<template>
  <div class="ee-dropzone-wrap">
    <div
      class="dropzone ee-dropzone"
      :class="{ 'is-dragover': dragging, 'is-disabled': disabled }"
      role="button"
      tabindex="0"
      data-ee-dropzone
      @click="open"
      @keydown.enter.prevent="open"
      @keydown.space.prevent="open"
      @dragover="onDragOver"
      @dragenter="onDragOver"
      @dragleave="dragging = false"
      @drop="onDrop"
    >
      <input
        ref="input" class="ee-dropzone__input" type="file" accept="image/*" multiple
        data-ee-file-input :disabled="disabled" @change="onChange"
      >
      <AppIcon name="upload" size="lg" />
      <strong>画像をここにドロップ</strong>
      <span>またはクリックしてファイルを選択</span>
      <small>JPG / PNG / WEBP{{ limitLabel }}</small>
    </div>
  </div>
</template>
