<script setup lang="ts">
import { ref } from 'vue'
import StudyDialog from '@/features/english-study/StudyDialog.vue'
import EssayCropDialog from '@/features/english-essay/components/EssayCropDialog.vue'
import { demoId, sampleImage } from './mock'
import type { LessonImage } from './types'
const images = defineModel<LessonImage[]>({ required: true })
const props = defineProps<{ reading?: boolean; disabled?: boolean }>()
const emit = defineEmits<{ change: [] }>()
const input = ref<HTMLInputElement>()
const error = ref(''), busy = ref(false), temporary = ref(false)
const preview = ref<LessonImage>(), crop = ref<LessonImage>()
const selected = ref<string[]>([])
const samples = [sampleImage('Reading worksheet', ['Cities plant trees for many reasons.', 'Read the passage and answer the questions.'], 'PASSAGE'), sampleImage('Cloze worksheet', ['The city decided to (____) the opening.', 'A. attend   B. postpone   C. predict   D. reward'])]
async function addFiles(files: File[]) {
  error.value = ''
  if (busy.value || props.disabled) return
  if (images.value.length + files.length > 20) { error.value = '画像は20枚まで追加できます。'; return }
  busy.value = true
  try {
    for (const file of files) {
      if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type) || file.size > 15 * 1024 * 1024) { error.value = 'PNG・JPEG・WebP（1枚15MB以内）を選択してください。'; continue }
      const src = await new Promise<string>((resolve, reject) => { const r = new FileReader(); r.onload = () => resolve(String(r.result)); r.onerror = reject; r.readAsDataURL(file) })
      images.value.push({ id: demoId('image'), name: file.name, src, category: props.reading ? 'PASSAGE' : 'QUESTION' }); emit('change')
    }
  } catch { error.value = '画像を読み込めませんでした。別の画像をお試しください。' }
  finally { busy.value = false; if (input.value) input.value.value = '' }
}
function move(index: number, step: number) { const next = index + step; if (next < 0 || next >= images.value.length) return; const [item] = images.value.splice(index, 1); images.value.splice(next, 0, item); emit('change') }
function addSamples() {
  if (props.disabled) return
  if (images.value.length + selected.value.length > 20) { error.value = '画像は20枚まで追加できます。'; return }
  for (const sample of samples.filter(s => selected.value.includes(s.id))) images.value.push({ ...sample, id: demoId('image'), category: props.reading ? sample.category : 'QUESTION' })
  emit('change'); temporary.value = false; selected.value = []
}
function applyCrop(result: { dataUrl: string; fileName: string }) { if (crop.value) { crop.value.src = result.dataUrl; crop.value.name = result.fileName; emit('change') }; crop.value = undefined }
</script>
<template>
  <fieldset class="ep-section" aria-label="画像入力" :disabled="disabled" style="border:0;padding:0;margin:0;min-width:0">
    <div class="ep-drop" tabindex="0" @dragover.prevent @drop.prevent="addFiles(Array.from($event.dataTransfer?.files ?? []))" @paste="addFiles(Array.from($event.clipboardData?.files ?? []))">
      <div class="ep-bar ep-bar--between"><strong>原本画像 <span class="ep-muted">{{ images.length }} / 20 枚</span></strong><div class="ep-bar"><button type="button" class="btn btn--secondary" :disabled="busy" @click="input?.click()">画像を追加</button><button type="button" class="btn btn--secondary" @click="temporary = true">一時ファイルから選択</button></div></div>
      <p class="ep-note">ここに画像をドラッグ、または貼り付け（Ctrl + V）。PNG・JPEG・WebP、1枚15MBまで。画像はこの画面内でのみ使用します。</p>
      <input ref="input" type="file" accept="image/png,image/jpeg,image/webp" multiple hidden @change="addFiles(Array.from(input?.files ?? []))" />
    </div>
    <p v-if="error" class="ep-error" role="alert">{{ error }}</p>
    <div v-if="images.length" class="ep-images">
      <article v-for="(img, index) in images" :key="img.id" class="ep-image">
        <button type="button" class="ep-link" :aria-label="`${img.name}を拡大`" @click="preview = img"><img :src="img.src" :alt="img.name" /></button><small>{{ index + 1 }}. {{ img.name }}</small>
        <select v-if="reading" v-model="img.category" class="select" :aria-label="`画像${index + 1}の分類`" @change="emit('change')"><option value="PASSAGE">文章</option><option value="QUESTION">設問</option><option value="OTHER">その他・解答</option></select>
        <div class="ep-bar"><button type="button" class="btn btn--secondary btn--sm" @click="crop = img">切り抜き・回転</button><button type="button" class="ep-icon" :disabled="index === 0" aria-label="前へ移動" @click="move(index, -1)">↑</button><button type="button" class="ep-icon" :disabled="index === images.length - 1" aria-label="後へ移動" @click="move(index, 1)">↓</button><button type="button" class="ep-icon ep-icon--danger" aria-label="画像を削除" @click="images.splice(index, 1); emit('change')">×</button></div>
      </article>
    </div>
    <StudyDialog v-if="temporary" title="一時ファイルから選択" @close="temporary = false"><div class="ep"><p class="ep-note">画面確認用のサンプル画像です。</p><label v-for="s in samples" :key="s.id" class="ep-option"><input v-model="selected" type="checkbox" :value="s.id" />{{ s.name }}</label></div><template #footer><button type="button" class="btn btn--secondary" @click="temporary = false">キャンセル</button><button type="button" class="btn btn--primary" :disabled="!selected.length" @click="addSamples">選択した画像を追加</button></template></StudyDialog>
    <StudyDialog v-if="preview" :title="preview.name" wide @close="preview = undefined"><img class="ep-original" :src="preview.src" :alt="preview.name" /></StudyDialog>
    <EssayCropDialog v-if="crop" :open="true" :src="crop.src" :file-name="crop.name" @confirm="applyCrop" @cancel="crop = undefined" />
  </fieldset>
</template>
