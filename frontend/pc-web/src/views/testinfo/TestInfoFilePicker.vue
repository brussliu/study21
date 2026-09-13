<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { getTestInfos, type TestFile, type TestRow } from '@/api/testinfo'
import { fileIcon, fileIconClass } from '@/features/document-files/document-file'
import { messageOf, toDocumentFileInfo } from '@/features/testinfo/testinfo-file'
import '@/features/document-files/document-files.css'

/**
 * 他のテストに添付済みのファイルから取り込むピッカー。
 * テスト番号ごとにセクション表示し、残り枠まで複数選択できる。
 */
const props = defineProps<{ open: boolean; max: number; excludeTestNo?: string }>()
const emit = defineEmits<{
  close: []
  picked: [items: Array<{ sourceTestNo: string; sourceFileId: number }>]
}>()

const SUBJECT_OPTIONS = ['すべて', '英語', '数学', '国語', '物理', '化学', '生物', '歴史', '地理']
const KIND_OPTIONS = ['すべて', '通常', '模擬', '復習', '練習']

const rows = ref<TestRow[]>([])
const loading = ref(false)
const error = ref('')
const subject = ref('すべて')
const kind = ref('すべて')
const keyword = ref('')
const selected = ref<Array<{ sourceTestNo: string; sourceFileId: number }>>([])

const remaining = computed(() => Math.max(0, props.max))
const selectedCount = computed(() => selected.value.length)

function isSelected(testNo: string, fileId: number): boolean {
  return selected.value.some((item) => item.sourceTestNo === testNo && item.sourceFileId === fileId)
}

function fileKey(file: TestFile): string {
  return `${file.fileId}`
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const response = await getTestInfos({
      subject: subject.value === 'すべて' ? undefined : subject.value,
      kind: kind.value === 'すべて' ? undefined : kind.value,
      keyword: keyword.value
    })
    rows.value = response.data.rows.filter((row) => row.testNo !== props.excludeTestNo && row.files.length > 0)
  } catch (cause) { error.value = messageOf(cause); rows.value = [] }
  finally { loading.value = false }
}

watch(() => props.open, (open) => {
  if (!open) return
  selected.value = []
  subject.value = 'すべて'
  kind.value = 'すべて'
  keyword.value = ''
  void load()
}, { immediate: true })

function toggle(testNo: string, fileId: number): void {
  const next = selected.value.filter((item) => !(item.sourceTestNo === testNo && item.sourceFileId === fileId))
  if (next.length === selected.value.length) {
    if (next.length >= remaining.value) return
    next.push({ sourceTestNo: testNo, sourceFileId: fileId })
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
    <div v-if="open" class="ti-picker-overlay">
      <section class="ti-picker" role="dialog" aria-modal="true" aria-label="テスト情報から選択">
        <header class="ti-picker__head">
          <strong><AppIcon name="clipboard" size="sm" /> テスト情報から選択</strong>
          <span class="ti-picker__count">選択 {{ selectedCount }} / {{ remaining }} 件</span>
          <button type="button" class="btn btn--ghost btn--sm" @click="emit('close')">閉じる</button>
        </header>

        <div class="ti-picker__filters">
          <span class="filter-item">
            <span class="filter-item__label">教科：</span>
            <select v-model="subject" class="select">
              <option v-for="option in SUBJECT_OPTIONS" :key="option" :value="option">{{ option }}</option>
            </select>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">区分：</span>
            <select v-model="kind" class="select">
              <option v-for="option in KIND_OPTIONS" :key="option" :value="option">{{ option }}</option>
            </select>
          </span>
          <span class="filter-item filter-item--grow">
            <span class="filter-item__label">キーワード：</span>
            <input
              v-model="keyword" class="input" placeholder="テスト名・番号" style="flex: 1"
              @keydown.enter.prevent="load"
            />
          </span>
          <button type="button" class="btn btn--primary" @click="load"><AppIcon name="search" size="sm" /> 検索</button>
        </div>

        <div class="ti-picker__body">
          <p v-if="error" class="alert alert--danger">{{ error }}</p>
          <p v-if="loading">読込中...</p>
          <p v-else-if="rows.length === 0">ファイルを持つテストがありません。</p>
          <template v-else>
            <section v-for="row in rows" :key="row.testNo" class="ti-picker__section">
              <h4 class="ti-picker__section-title">
                <span class="cell-strong">{{ row.testNo }}</span>
                <span>{{ row.testName }}</span>
                <small>{{ row.subject }} / {{ row.kind }}</small>
              </h4>
              <div class="ti-picker__files">
                <label
                  v-for="file in row.files" :key="fileKey(file)" class="ti-file-card"
                  :class="{ 'is-selected': isSelected(row.testNo, file.fileId) }"
                >
                  <input
                    type="checkbox" class="ti-file-card__check"
                    :checked="isSelected(row.testNo, file.fileId)"
                    :disabled="!isSelected(row.testNo, file.fileId) && selectedCount >= remaining"
                    @change="toggle(row.testNo, file.fileId)"
                  />
                  <span class="ti-file-card__thumb">
                    <img v-if="file.image && file.previewUrl" :src="file.previewUrl" :alt="file.originalFileName" loading="lazy" />
                    <AppIcon v-else :name="fileIcon(toDocumentFileInfo(file))" size="lg" :class="fileIconClass(toDocumentFileInfo(file))" />
                  </span>
                  <span class="ti-file-card__name" :title="file.originalFileName">{{ file.originalFileName }}</span>
                  <small class="ti-file-card__ext">{{ file.extension.toUpperCase() }}</small>
                </label>
              </div>
            </section>
          </template>
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

.ti-picker__section + .ti-picker__section {
  margin-top: var(--sp-4);
}

.ti-picker__section-title {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-sm);
}

.ti-picker__section-title small {
  color: var(--color-text-subtle);
  font-size: var(--fs-xs);
}

.ti-picker__files {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.ti-file-card {
  position: relative;
  width: 150px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--sp-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.ti-file-card.is-selected {
  border-color: var(--color-primary-strong);
  background: var(--color-primary-soft);
}

.ti-file-card__check {
  position: absolute;
  top: 6px;
  left: 6px;
}

.ti-file-card__thumb {
  height: 74px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-surface-alt);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.ti-file-card__thumb img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.ti-file-card__name {
  font-size: var(--fs-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ti-file-card__ext {
  color: var(--color-text-subtle);
  font-size: var(--fs-2xs);
}
</style>
