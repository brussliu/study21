<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { getTempFiles, type TempFileSummary } from '@/api/tempfiles'

/**
 * 臨時ファイルから英作文の画像を取り込むピッカー（2.0 の `temp_file_picker.jsp` 相当）。
 *
 * <p>既存の `TestInfoTempFilePicker.vue` と同じ作り（サムネイルはデータ URL に直して渡す）。
 * 違うのは**画像だけ**を対象にすることと、選んだ画像を 1 枚ずつ
 * （ファイル名・データ URL・大きさ）の形にして返すこと。取り込んだあとは呼ぶ側が
 * トリミング窓を通す。</p>
 *
 * <p>選べる枚数は呼ぶ側が渡す（上限はサーバーが決める）。`null` のとき（上限が不明）は
 * **枚数で止めない**。</p>
 */

const props = withDefaults(defineProps<{ open: boolean; max: number | null }>(), { max: null })
const emit = defineEmits<{
  close: []
  picked: [files: { fileName: string; dataUrl: string; sizeBytes: number }[]]
}>()

const items = ref<TempFileSummary[]>([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const selected = ref<number[]>([])

/** まだ選べる枚数（上限が不明なら無限）。 */
const remaining = computed(() => (props.max === null ? Number.POSITIVE_INFINITY : Math.max(0, props.max)))

/** 残りの案内（上限が分からないときは枚数を出さない）。 */
const remainingNote = computed(() => (props.max === null
  ? '選んだ画像をまとめて取り込めます。'
  : `残り ${remaining.value} 件まで選択できます。`))

/** 見出しの「選択 n / m 件」（上限が分からないときは分母を出さない）。 */
const selectionLabel = computed(
  () => (props.max === null ? `選択 ${selected.value.length} 件` : `選択 ${selected.value.length} / ${remaining.value} 件`)
)

function thumbUrl(file: TempFileSummary): string | null {
  // 旧 2.0 移行データは data: URL、2.1 の新規アップロードは裸の base64 のため両方を吸収する。
  if (!file.image || !file.thumbnail) return null
  return file.thumbnail.startsWith('data:') ? file.thumbnail : `data:image/png;base64,${file.thumbnail}`
}

function formatBytes(bytes: number): string {
  if (bytes <= 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    // 画像だけを対象にする（英作文の画像は答案・設問の写真しかない）
    items.value = (await getTempFiles({ keyword: keyword.value, type: '画像' })).data
  } catch {
    error.value = '臨時ファイルを読み込めませんでした。'
    items.value = []
  } finally {
    loading.value = false
  }
}

watch(() => props.open, (open) => {
  if (!open) {
    return
  }
  selected.value = []
  keyword.value = ''
  void load()
}, { immediate: true })

function toggle(id: number): void {
  const next = selected.value.filter((value) => value !== id)
  if (next.length === selected.value.length) {
    if (next.length >= remaining.value) {
      return
    }
    next.push(id)
  }
  selected.value = next
}

/** 選んだ画像をデータ URL にして返す（アップロード先が無いので画面が持つ）。 */
async function toDataUrl(file: TempFileSummary): Promise<string> {
  const response = await fetch(file.contentUrl)
  if (!response.ok) {
    throw new Error('臨時画像を取得できませんでした。')
  }
  const blob = await response.blob()
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(new Error('臨時画像を読み込めませんでした。'))
    reader.readAsDataURL(blob)
  })
}

async function confirm(): Promise<void> {
  const targets = items.value.filter((file) => selected.value.includes(file.tempFileId))
  if (targets.length === 0) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const picked = []
    for (const file of targets) {
      picked.push({
        fileName: file.originalFileName,
        dataUrl: await toDataUrl(file),
        sizeBytes: file.fileSize
      })
    }
    emit('picked', picked)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '臨時画像の追加に失敗しました。'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="overlay" data-ee-tempfile-picker>
      <section class="dialog dialog--lg" role="dialog" aria-modal="true" aria-label="臨時ファイルから選択">
        <header class="dialog__head">
          <h2 class="dialog__title"><AppIcon name="folder" size="sm" /> 臨時ファイルから選択</h2>
          <span class="dialog__subtitle">{{ selectionLabel }}</span>
          <button type="button" class="dialog__close" data-ee-tempfile-close aria-label="閉じる" @click="emit('close')">
            <AppIcon name="x" size="sm" />
          </button>
        </header>

        <div class="dialog__body">
          <div class="ee-picker__filters">
            <span class="filter-item filter-item--grow">
              <span class="filter-item__label">キーワード：</span>
              <input
                v-model="keyword" class="input" placeholder="ファイル名・コメント"
                data-ee-tempfile-keyword @keydown.enter.prevent="load"
              >
            </span>
            <button type="button" class="btn btn--primary" data-ee-tempfile-search @click="load">
              <AppIcon name="search" size="sm" /> 検索
            </button>
          </div>

          <p v-if="error" class="alert alert--danger">{{ error }}</p>
          <p v-if="loading" class="ee-picker__muted">読込中...</p>
          <p v-else-if="items.length === 0" class="ee-picker__muted">該当する画像がありません。</p>
          <div v-else class="tf-grid ee-picker__grid">
            <article
              v-for="file in items" :key="file.tempFileId" class="tf-card"
              :class="{ 'is-selected': selected.includes(file.tempFileId) }"
            >
              <label class="tf-card__check" title="選択" @click.stop>
                <input
                  type="checkbox" :checked="selected.includes(file.tempFileId)"
                  :disabled="!selected.includes(file.tempFileId) && selected.length >= remaining"
                  @change="toggle(file.tempFileId)"
                >
                <span class="tf-card__checkbox"><AppIcon name="check" size="sm" /></span>
              </label>
              <div class="tf-card__thumb">
                <img v-if="thumbUrl(file)" :src="thumbUrl(file)!" :alt="file.originalFileName" loading="lazy">
                <AppIcon v-else name="image" />
              </div>
              <div class="tf-card__name" :title="file.originalFileName">{{ file.originalFileName }}</div>
              <div class="tf-card__meta">画像 ・ {{ formatBytes(file.fileSize) }}</div>
            </article>
          </div>
        </div>

        <footer class="dialog__foot">
          <span class="ee-picker__note">{{ remainingNote }}</span>
          <span class="ee-picker__spacer"></span>
          <button type="button" class="btn btn--secondary" @click="emit('close')">キャンセル</button>
          <button type="button" class="btn btn--primary" data-ee-tempfile-pick :disabled="selected.length === 0" @click="confirm">
            <AppIcon name="check" size="sm" /> 選択した {{ selected.length }} 件を取り込む
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>
