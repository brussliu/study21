<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { getTempFiles, type TempFileSummary } from '@/api/tempfiles'
import { messageOf } from '@/features/testinfo/testinfo-file'
import '@/features/temp-file/temp-file.css'

/**
 * 臨時ファイルから試験用紙を取り込むピッカー。
 * 残り枠（max）まで複数選択でき、選択後は親が 1 件ずつ取り込み API を呼ぶ。
 */
const props = defineProps<{ open: boolean; max: number }>()
const emit = defineEmits<{ close: []; picked: [tempFileIds: number[]] }>()

const items = ref<TempFileSummary[]>([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const typeFilter = ref<'すべて' | '画像' | 'その他'>('すべて')
const selected = ref<number[]>([])

const remaining = computed(() => Math.max(0, props.max))
const selectedCount = computed(() => selected.value.length)

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
    items.value = (await getTempFiles({
      keyword: keyword.value,
      type: typeFilter.value === 'すべて' ? undefined : typeFilter.value
    })).data
  } catch (cause) { error.value = messageOf(cause); items.value = [] }
  finally { loading.value = false }
}

watch(() => props.open, (open) => {
  if (!open) return
  selected.value = []
  keyword.value = ''
  typeFilter.value = 'すべて'
  void load()
}, { immediate: true })

function toggle(id: number): void {
  const next = selected.value.filter((value) => value !== id)
  if (next.length === selected.value.length) {
    if (next.length >= remaining.value) return
    next.push(id)
  }
  selected.value = next
}

function confirm(): void {
  if (selected.value.length === 0) return
  emit('picked', [...selected.value])
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="ti-picker-overlay" @click.self="emit('close')">
      <section class="ti-picker" role="dialog" aria-modal="true" aria-label="臨時ファイルから選択">
        <header class="ti-picker__head">
          <strong><AppIcon name="folder" size="sm" /> 臨時ファイルから選択</strong>
          <span class="ti-picker__count">選択 {{ selectedCount }} / {{ remaining }} 件</span>
          <button type="button" class="btn btn--ghost btn--sm" @click="emit('close')">閉じる</button>
        </header>

        <div class="ti-picker__filters">
          <span class="filter-item">
            <span class="filter-item__label">タイプ：</span>
            <select v-model="typeFilter" class="select">
              <option>すべて</option><option>画像</option><option>その他</option>
            </select>
          </span>
          <span class="filter-item filter-item--grow">
            <span class="filter-item__label">キーワード：</span>
            <input
              v-model="keyword" class="input" placeholder="ファイル名・コメント" style="flex: 1"
              @keydown.enter.prevent="load"
            />
          </span>
          <button type="button" class="btn btn--primary" @click="load"><AppIcon name="search" size="sm" /> 検索</button>
        </div>

        <div class="ti-picker__body">
          <p v-if="error" class="alert alert--danger">{{ error }}</p>
          <p v-if="loading" class="tf-muted">読込中...</p>
          <p v-else-if="items.length === 0" class="tf-muted">該当するファイルがありません。</p>
          <div v-else class="tf-grid">
            <article
              v-for="file in items" :key="file.tempFileId" class="tf-card"
              :class="{ 'is-selected': selected.includes(file.tempFileId) }"
            >
              <label class="tf-card__check" title="選択" @click.stop>
                <input
                  type="checkbox" :checked="selected.includes(file.tempFileId)"
                  :disabled="!selected.includes(file.tempFileId) && selectedCount >= remaining"
                  @change="toggle(file.tempFileId)"
                />
                <span class="tf-card__checkbox"><AppIcon name="check" size="sm" /></span>
              </label>
              <div v-if="file.image" class="tf-card__thumb">
                <img v-if="thumbUrl(file)" :src="thumbUrl(file)!" :alt="file.originalFileName" loading="lazy" />
                <AppIcon v-else name="image" />
              </div>
              <div v-else class="tf-card__thumb"><AppIcon name="file" /></div>
              <div class="tf-card__name" :title="file.originalFileName">{{ file.originalFileName }}</div>
              <div class="tf-card__meta">{{ file.image ? '画像' : 'その他' }} ・ {{ formatBytes(file.fileSize) }}</div>
            </article>
          </div>
        </div>

        <footer class="ti-picker__foot">
          <span class="ti-picker__note">残り {{ remaining }} 件まで選択できます。</span>
          <span class="ti-picker__spacer"></span>
          <button type="button" class="btn btn--secondary" @click="emit('close')">キャンセル</button>
          <button type="button" class="btn btn--primary" :disabled="selectedCount === 0" @click="confirm">
            <AppIcon name="check" size="sm" /> 選択した {{ selectedCount }} 件を取り込む
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.ti-picker-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-dialog);
  background: rgba(8, 13, 19, .68);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.ti-picker {
  width: min(980px, 100%);
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.ti-picker__head,
.ti-picker__filters,
.ti-picker__foot {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--color-border);
}

.ti-picker__head strong {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
}

.ti-picker__count {
  flex: 1;
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}

.ti-picker__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: var(--sp-4);
}

.ti-picker__foot {
  border-bottom: none;
  border-top: 1px solid var(--color-border);
}

.ti-picker__note {
  font-size: var(--fs-xs);
  color: var(--color-text-subtle);
}

.ti-picker__spacer {
  flex: 1;
}
</style>
