<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  checkLinkClipDuplicates, createLinkClip, previewLinkClip, updateLinkClip,
  type ClipRow, type LinkPreview, type SaveClipRequest
} from '@/api/linkclip'
import {
  FOLDER_CHOICES, formatTags, formatDate, formatDuration, isConflict, messageOf, parseTags,
  sourceIcon, sourceLabel
} from '@/features/linkclip/linkclip'

/**
 * 新規 / 編集ダイアログ（リンクを保存）。
 * URL から Open Graph 情報を取得してプレビューできる。
 * 表示は親が制御する（open が true のときだけダイアログを出す）。
 */
const props = defineProps<{ open: boolean; editing: ClipRow | null }>()
const emit = defineEmits<{ saved: []; close: [] }>()

const toast = useToast()

const saving = ref(false)
const previewing = ref(false)
const duplicateCount = ref(0)

const url = ref('')
const folderCode = ref<string>('INBOX')
const pageTitle = ref('')
const tagsInput = ref('')
const memo = ref('')

const preview = ref<LinkPreview | null>(null)

/** プレビューで取得した補完情報（保存時にそのまま送る）。 */
const meta = ref<Pick<SaveClipRequest, 'siteName' | 'sourceCode' | 'clipType' | 'summary' | 'publisherName'
  | 'publishedAt' | 'videoSeconds' | 'thumbnailUrl'>>({
  siteName: null,
  sourceCode: undefined,
  clipType: undefined,
  summary: null,
  publisherName: null,
  publishedAt: null,
  videoSeconds: null,
  thumbnailUrl: null
})

const isEditing = computed(() => props.editing !== null)
const title = computed(() => (isEditing.value ? 'リンクを編集' : 'リンクを保存'))

function resetForm(): void {
  url.value = ''
  folderCode.value = 'INBOX'
  pageTitle.value = ''
  tagsInput.value = ''
  memo.value = ''
  preview.value = null
  duplicateCount.value = 0
  meta.value = {
    siteName: null, sourceCode: undefined, clipType: undefined, summary: null,
    publisherName: null, publishedAt: null, videoSeconds: null, thumbnailUrl: null
  }
}

/** ダイアログが開くたびに、編集対象を読み込む（新規なら初期化）。 */
watch(() => [props.open, props.editing] as const, ([isOpen, row]) => {
  if (!isOpen) return
  if (row) {
    url.value = row.url
    folderCode.value = row.folderCode ?? 'INBOX'
    pageTitle.value = row.pageTitle
    tagsInput.value = formatTags(row.tags)
    memo.value = row.memo ?? ''
    preview.value = null
    duplicateCount.value = 0
    meta.value = {
      siteName: row.siteName,
      sourceCode: row.sourceCode,
      clipType: row.clipType,
      summary: row.summary,
      publisherName: row.publisherName,
      publishedAt: row.publishedAt,
      videoSeconds: row.videoSeconds,
      thumbnailUrl: row.thumbnailUrl
    }
  } else {
    resetForm()
  }
}, { immediate: true })

function close(): void {
  if (saving.value) return
  emit('close')
}

function onKeydown(event: KeyboardEvent): void {
  if (!props.open || event.key !== 'Escape') return
  close()
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))

/** URL からメタ情報を取得してプレビューに反映する（タイトル未入力なら補完）。 */
async function runPreview(): Promise<boolean> {
  const target = url.value.trim()
  if (!target) { toast.warning('URLを入力してください。'); return false }
  previewing.value = true
  try {
    const result = await previewLinkClip(target)
    preview.value = result.data
    meta.value = {
      siteName: result.data.siteName,
      sourceCode: result.data.sourceCode,
      clipType: result.data.clipType,
      summary: result.data.summary,
      publisherName: result.data.publisherName,
      publishedAt: result.data.publishedAt,
      videoSeconds: result.data.videoSeconds,
      thumbnailUrl: result.data.thumbnailUrl
    }
    if (pageTitle.value.trim() === '' && result.data.pageTitle) pageTitle.value = result.data.pageTitle
    if (result.data.localFile) toast.info('ローカルファイルとして保存します（Web からは開けません）。')
    return true
  } catch (cause) {
    toast.danger(messageOf(cause))
    return false
  } finally {
    previewing.value = false
  }
}

/** 同じ URL の既存クリップがあれば確認する（重複登録自体は許可）。 */
async function confirmDuplicate(target: string): Promise<boolean> {
  try {
    const result = await checkLinkClipDuplicates(target)
    duplicateCount.value = result.data.rows.length
    if (!result.data.duplicated) return true
    const count = result.data.rows.length
    return window.confirm(`同じURLのリンクが既に ${count} 件あります。保存しますか？`)
  } catch {
    // 重複確認に失敗しても保存自体は続行する
    return true
  }
}

async function save(): Promise<void> {
  const target = url.value.trim()
  if (!target) { toast.warning('URLを入力してください。'); return }
  // タイトルはサーバ側でも必須。未入力ならプレビューで補完を試みる。
  if (pageTitle.value.trim() === '') {
    await runPreview()
    if (pageTitle.value.trim() === '') { toast.warning('タイトルを入力してください。'); return }
  }
  if (!(await confirmDuplicate(target))) return

  const body: SaveClipRequest = {
    url: target,
    pageTitle: pageTitle.value.trim(),
    siteName: meta.value.siteName ?? null,
    folderCode: folderCode.value,
    sourceCode: meta.value.sourceCode,
    clipType: meta.value.clipType,
    summary: meta.value.summary ?? null,
    memo: memo.value.trim() === '' ? null : memo.value.trim(),
    publisherName: meta.value.publisherName ?? null,
    publishedAt: meta.value.publishedAt ?? null,
    videoSeconds: meta.value.videoSeconds ?? null,
    thumbnailUrl: meta.value.thumbnailUrl ?? null,
    tags: parseTags(tagsInput.value)
  }

  saving.value = true
  try {
    if (props.editing) {
      await updateLinkClip(props.editing.linkClipId, { ...body, version: props.editing.version })
      toast.success('リンクを更新しました。')
    } else {
      await createLinkClip(body)
      toast.success('リンクを保存しました。')
    }
    // 閉じるのは親に任せる（emit('saved') で親がダイアログを閉じて一覧を再読込する）。
    emit('saved')
  } catch (cause) {
    toast.danger(isConflict(cause) ? '他の端末で更新されています。再読込してください。' : messageOf(cause))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div v-if="open" class="overlay" @click.self="close">
    <section class="dialog dialog--lg lc-composer" role="dialog" aria-modal="true" aria-labelledby="lcComposerTitle">
      <div class="dialog__head">
        <h2 id="lcComposerTitle" class="dialog__title"><AppIcon name="bookmark" size="sm" /> {{ title }}</h2>
        <span class="lc-composer__status">
          <template v-if="isEditing">「{{ editing?.pageTitle }}」を編集中</template>
          <template v-else>URL を貼り付けて保存できます。</template>
        </span>
        <button type="button" class="dialog__close" aria-label="閉じる" @click="close">
          <AppIcon name="x" size="sm" />
        </button>
      </div>

      <div class="dialog__body">
        <div class="lc-form-grid">
          <div class="field field--wide">
            <label class="field__label">URL</label>
            <input v-model="url" class="input" type="url" placeholder="https://..." />
          </div>
          <div class="field">
            <label class="field__label">保存先</label>
            <select v-model="folderCode" class="select">
              <option v-for="choice in FOLDER_CHOICES" :key="choice.code" :value="choice.code">{{ choice.label }}</option>
            </select>
          </div>
          <div class="field">
            <label class="field__label">タイトル補足</label>
            <input v-model="pageTitle" class="input" maxlength="300" placeholder="タイトル（空なら自動取得）" />
          </div>
          <div class="field">
            <label class="field__label">タグ</label>
            <input v-model="tagsInput" class="input" placeholder="英語, listening, youtube" />
            <p class="field__hint">カンマ区切り。保存時に重複（大文字小文字を無視）は自動で除外されます。</p>
          </div>
          <div class="field field--wide">
            <label class="field__label">メモ</label>
            <textarea v-model="memo" class="textarea" rows="2" placeholder="あとで見返すための一言メモ" />
          </div>
        </div>

        <div v-if="preview" class="lc-preview">
          <span class="lc-preview__thumb">
            <img v-if="preview.thumbnailUrl" :src="preview.thumbnailUrl" alt="" />
            <AppIcon v-else :name="sourceIcon(preview.sourceCode)" />
          </span>
          <div class="lc-preview__body">
            <span class="lc-preview__meta">
              {{ preview.siteName || sourceLabel(preview.sourceCode) }}
              <template v-if="preview.publisherName"> ・ {{ preview.publisherName }}</template>
              <template v-if="preview.publishedAt"> ・ {{ formatDate(preview.publishedAt) }}</template>
              <template v-if="preview.videoSeconds"> ・ {{ formatDuration(preview.videoSeconds) }}</template>
              <template v-if="preview.localFile"> ・ ローカルファイル</template>
            </span>
            <span class="lc-preview__title">{{ preview.pageTitle || '（タイトルを取得できませんでした）' }}</span>
            <span v-if="preview.summary" class="lc-preview__meta">{{ preview.summary }}</span>
            <span v-if="duplicateCount > 0" class="lc-preview__meta">同じ URL のリンクが {{ duplicateCount }} 件あります。</span>
          </div>
        </div>
      </div>

      <div class="dialog__foot">
        <button type="button" class="btn btn--primary" :disabled="saving" @click="save">
          <AppIcon name="check" size="sm" /> {{ isEditing ? '更新して保存' : '保存' }}
        </button>
        <button type="button" class="btn btn--secondary" :disabled="previewing" @click="runPreview">
          <AppIcon name="eye" size="sm" class="icon--view" /> {{ previewing ? '取得中...' : 'プレビュー' }}
        </button>
        <span class="lc-spacer"></span>
        <button type="button" class="btn btn--secondary" :disabled="saving" @click="close">キャンセル</button>
      </div>
    </section>
  </div>
</template>
