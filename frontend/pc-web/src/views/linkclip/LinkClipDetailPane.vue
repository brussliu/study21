<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import { deleteLinkClip, recordLinkClipView, updateLinkClipFlags, type ClipRow } from '@/api/linkclip'
import {
  canOpenInBrowser, clipTypeLabel, copyText, folderLabel, formatDateTime, formatDuration,
  isLocalFile, messageOf, sourceIcon, sourceLabel
} from '@/features/linkclip/linkclip'

/**
 * 右からスライドして出るリンク詳細ドロワー。
 * フラグ切替・閲覧記録・削除はここで実行し、変更を changed / removed で親へ伝える。
 */
const props = defineProps<{ row: ClipRow | null }>()
const emit = defineEmits<{
  edit: []
  changed: []
  removed: []
  selectTag: [string]
  close: []
}>()

const toast = useToast()
const busy = ref(false)

function close(): void {
  if (busy.value) return
  emit('close')
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') close()
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))

async function run(action: () => Promise<void>): Promise<void> {
  if (busy.value) return
  busy.value = true
  try {
    await action()
  } catch (cause) {
    toast.danger(messageOf(cause))
  } finally {
    busy.value = false
  }
}

/** 元ページを開く（閲覧回数を記録する）。ローカルファイルは開かない。 */
function openPage(): void {
  const row = props.row
  if (!row) return
  if (!canOpenInBrowser(row)) { copyPath(); return }
  void run(async () => {
    await recordLinkClipView(row.linkClipId)
    window.open(row.url, '_blank', 'noopener')
    emit('changed')
  })
}

async function copyPath(): Promise<void> {
  const row = props.row
  if (!row) return
  const target = isLocalFile(row) ? row.url : row.normalizedUrl || row.url
  const ok = await copyText(target)
  if (ok) toast.success('パスをコピーしました。')
  else toast.danger('コピーできませんでした。手動で選択してください。')
}

function toggleFavorite(): void {
  const row = props.row
  if (!row) return
  void run(async () => {
    await updateLinkClipFlags(row.linkClipId, { favorite: !row.favorite })
    toast.success(row.favorite ? 'お気に入りを解除しました。' : 'お気に入りに追加しました。')
    emit('changed')
  })
}

function toggleRead(): void {
  const row = props.row
  if (!row) return
  void run(async () => {
    await updateLinkClipFlags(row.linkClipId, { read: !row.read })
    toast.success(row.read ? '未読に戻しました。' : '既読にしました。')
    emit('changed')
  })
}

function toggleArchive(): void {
  const row = props.row
  if (!row) return
  void run(async () => {
    await updateLinkClipFlags(row.linkClipId, { archived: !row.archived })
    toast.success(row.archived ? 'アーカイブを解除しました。' : 'アーカイブしました。')
    emit('changed')
  })
}

function remove(): void {
  const row = props.row
  if (!row) return
  if (!window.confirm(`「${row.pageTitle}」を削除しますか？`)) return
  void run(async () => {
    await deleteLinkClip(row.linkClipId)
    toast.success('リンクを削除しました。')
    emit('removed')
  })
}
</script>

<template>
  <div v-if="row" class="lc-drawer-overlay">
    <aside class="lc-detail" role="dialog" aria-modal="true" aria-label="リンク詳細">
      <div class="lc-detail__head">
        <h3 class="lc-detail__title">{{ row.pageTitle }}</h3>
        <button
          type="button" class="btn btn--icon btn--sm" :class="{ 'is-favorite': row.favorite }"
          :title="row.favorite ? 'お気に入りを解除' : 'お気に入りに追加'" :disabled="busy" @click="toggleFavorite"
        >
          <AppIcon name="bookmark" size="sm" />
        </button>
        <button type="button" class="btn btn--icon btn--sm" title="閉じる" aria-label="閉じる" @click="close">
          <AppIcon name="x" size="sm" />
        </button>
      </div>

      <div
        class="lc-detail__thumb"
        :style="row.thumbnailUrl ? { backgroundImage: `url(${row.thumbnailUrl})` } : undefined"
      >
        <AppIcon v-if="!row.thumbnailUrl" :name="sourceIcon(row.sourceCode)" />
      </div>

      <div class="lc-detail__section">
        <span class="lc-detail__section-title">URL</span>
        <span class="lc-detail__url">{{ row.url }}</span>
        <div v-if="isLocalFile(row)" class="lc-path">
          <AppIcon name="file" size="sm" />
          <span>{{ row.url }}</span>
          <button type="button" class="btn btn--secondary btn--sm" @click="copyPath">
            <AppIcon name="copy" size="sm" /> コピー
          </button>
        </div>
      </div>

      <div v-if="row.tags.length > 0" class="lc-detail__section">
        <span class="lc-detail__section-title">タグ</span>
        <div class="lc-card__tags">
          <button v-for="tag in row.tags" :key="tag" type="button" class="lc-tag" @click="emit('selectTag', tag)">{{ tag }}</button>
        </div>
      </div>

      <div v-if="row.summary" class="lc-detail__section">
        <span class="lc-detail__section-title">概要</span>
        <span class="lc-detail__text">{{ row.summary }}</span>
      </div>

      <div v-if="row.aiSummary" class="lc-detail__section">
        <span class="lc-detail__section-title">AI要約</span>
        <span class="lc-detail__text">{{ row.aiSummary }}</span>
      </div>

      <div v-if="row.memo" class="lc-detail__section">
        <span class="lc-detail__section-title">自分メモ</span>
        <span class="lc-detail__text">{{ row.memo }}</span>
      </div>

      <div class="lc-metrics">
        <span class="lc-metrics__label">保存先</span><span>{{ folderLabel(row.folderCode) }}</span>
        <span class="lc-metrics__label">ソース</span><span>{{ sourceLabel(row.sourceCode) }}</span>
        <span class="lc-metrics__label">種別</span><span>{{ clipTypeLabel(row.clipType) }}</span>
        <span class="lc-metrics__label">公開者</span><span>{{ row.publisherName || '—' }}</span>
        <span class="lc-metrics__label">長さ</span><span>{{ formatDuration(row.videoSeconds) }}</span>
        <span class="lc-metrics__label">閲覧回数</span><span>{{ row.viewCount }} 回</span>
        <span class="lc-metrics__label">最終閲覧</span><span>{{ formatDateTime(row.lastViewedAt) }}</span>
        <span class="lc-metrics__label">保存日時</span><span>{{ formatDateTime(row.createdAt) }}</span>
      </div>

      <div class="lc-detail__actions">
        <button v-if="canOpenInBrowser(row)" type="button" class="btn btn--primary btn--sm" :disabled="busy" @click="openPage">
          <AppIcon name="globe" size="sm" /> 元ページを開く
        </button>
        <button v-else type="button" class="btn btn--primary btn--sm" :disabled="busy" @click="copyPath">
          <AppIcon name="copy" size="sm" /> パスをコピー
        </button>
        <button type="button" class="btn btn--secondary btn--sm" :disabled="busy" @click="emit('edit')">
          <AppIcon name="edit" size="sm" class="icon--edit" /> 修正
        </button>
        <button type="button" class="btn btn--secondary btn--sm" :disabled="busy" @click="toggleRead">
          <AppIcon :name="row.read ? 'eye' : 'check'" size="sm" /> {{ row.read ? '未読に戻す' : '既読にする' }}
        </button>
        <button type="button" class="btn btn--secondary btn--sm" :disabled="busy" @click="toggleArchive">
          <AppIcon name="file-archive" size="sm" /> {{ row.archived ? 'アーカイブ解除' : 'アーカイブ' }}
        </button>
        <button type="button" class="btn btn--danger-outline btn--sm" :disabled="busy" @click="remove">
          <AppIcon name="trash" size="sm" /> 削除
        </button>
      </div>
    </aside>
  </div>
</template>
