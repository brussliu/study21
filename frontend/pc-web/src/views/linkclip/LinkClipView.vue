<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import { useAuthStore } from '@/stores/auth'
import LinkClipComposer from '@/views/linkclip/LinkClipComposer.vue'
import LinkClipDetailPane from '@/views/linkclip/LinkClipDetailPane.vue'
import {
  deleteLinkClip, getLinkClips, recordLinkClipView, updateLinkClipFlags,
  type ClipRow, type ClipQuery, type LaneCount, type TagOption
} from '@/api/linkclip'
import {
  FOLDER_LANES, canOpenInBrowser, clipTypeLabel, copyText, folderLabel, formatDate, formatDuration,
  isLocalFile, messageOf, sourceIcon, sourceLabel
} from '@/features/linkclip/linkclip'
import { paginationItems } from '@/features/pagination/pagination'
import '@/features/linkclip/linkclip.css'

/** カードに出すタグの最大数（残りは +N）。 */
const MAX_CARD_TAGS = 4
/** 1 ページの件数。 */
const PAGE_SIZE = 20

const auth = useAuthStore()
const isFamilyUser = computed(() => auth.role === 'STUDENT' || auth.role === 'GUARDIAN')
const toast = useToast()

type LaneKey = 'ALL' | 'INBOX' | 'READ_LATER' | 'LEARNING' | 'REFERENCE' | 'DONE' | 'FAVORITE' | 'ARCHIVED'

const rows = ref<ClipRow[]>([])
const tagOptions = ref<TagOption[]>([])
const lanes = ref<LaneCount[]>([])
const loading = ref(false)
const error = ref('')

const activeLane = ref<LaneKey>('ALL')
const activeTag = ref('')

// 検索条件（入力中）と適用済みを分離し、「検索」で反映する。
const keyword = ref('')
const sourceFilter = ref('')
const clipTypeFilter = ref('')
const appliedKeyword = ref('')

const selected = ref<ClipRow | null>(null)
const editing = ref<ClipRow | null>(null)
/** 新規 / 編集ダイアログの開閉。 */
const composerOpen = ref(false)
const page = ref(1)
const busyId = ref<number | null>(null)

const SOURCE_OPTIONS = ['WEB', 'YOUTUBE', 'GITHUB', 'WIKIPEDIA', 'NEWS', 'LOCAL_FILE', 'OTHER']
const CLIP_TYPE_OPTIONS = ['LINK', 'VIDEO', 'ARTICLE', 'REPOSITORY', 'NEWS', 'FILE']

const totalPages = computed(() => Math.max(1, Math.ceil(rows.value.length / PAGE_SIZE)))
const paged = computed(() => rows.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE))
const pageItems = computed(() => paginationItems(page.value, totalPages.value))

function laneCount(code: LaneKey): number {
  if (code === 'ALL') return lanes.value.reduce((sum, lane) => sum + lane.count, 0)
  if (code === 'FAVORITE' || code === 'ARCHIVED') {
    // 専用の件数は API に無いため、その絞り込みを表示中のときだけ件数を出す。
    return activeLane.value === code ? rows.value.length : 0
  }
  return lanes.value.find((lane) => lane.folderCode === code)?.count ?? 0
}

function isLaneCountVisible(code: LaneKey): boolean {
  if (code === 'FAVORITE' || code === 'ARCHIVED') return activeLane.value === code
  return true
}

function buildQuery(): ClipQuery {
  const query: ClipQuery = {
    keyword: appliedKeyword.value,
    source: sourceFilter.value,
    clipType: clipTypeFilter.value,
    tag: activeTag.value,
    archive: 'active'
  }
  if (activeLane.value === 'FAVORITE') query.favorite = true
  else if (activeLane.value === 'ARCHIVED') query.archive = 'archived'
  else if (activeLane.value !== 'ALL') query.folder = activeLane.value
  return query
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await getLinkClips(buildQuery())
    rows.value = result.data.rows
    tagOptions.value = result.data.tags
    lanes.value = result.data.lanes
    // 選択中のリンクを最新の内容へ差し替える（削除済みなら解除）。
    const current = selected.value
    if (current) selected.value = rows.value.find((row) => row.linkClipId === current.linkClipId) ?? null
  } catch (cause) {
    error.value = messageOf(cause)
    rows.value = []
    tagOptions.value = []
    lanes.value = []
  } finally {
    loading.value = false
  }
}

function search(): void {
  appliedKeyword.value = keyword.value.trim()
  page.value = 1
  void load()
}

function reset(): void {
  keyword.value = ''
  sourceFilter.value = ''
  clipTypeFilter.value = ''
  appliedKeyword.value = ''
  activeTag.value = ''
  activeLane.value = 'ALL'
  page.value = 1
  void load()
}

function selectLane(code: LaneKey): void {
  activeLane.value = code
  page.value = 1
  selected.value = null
  void load()
}

function selectTag(tag: string): void {
  activeTag.value = activeTag.value === tag ? '' : tag
  page.value = 1
  void load()
}

function selectRow(row: ClipRow): void {
  selected.value = row
}

/** 新規はダイアログで入力する（画面上部には出さない）。 */
function newClip(): void {
  editing.value = null
  composerOpen.value = true
}

function closeComposer(): void {
  composerOpen.value = false
  editing.value = null
}

function goto(target: number): void {
  page.value = Math.min(Math.max(1, target), totalPages.value)
}

function cardTags(row: ClipRow): { visible: string[]; hidden: number } {
  return { visible: row.tags.slice(0, MAX_CARD_TAGS), hidden: Math.max(0, row.tags.length - MAX_CARD_TAGS) }
}

function thumbStyle(row: ClipRow): Record<string, string> | undefined {
  return row.thumbnailUrl ? { backgroundImage: `url(${row.thumbnailUrl})` } : undefined
}

/** カードからの「元ページを開く」（閲覧記録 +1）。ローカルファイルはパスをコピーする。 */
async function openFromCard(row: ClipRow): Promise<void> {
  if (!canOpenInBrowser(row)) { await copyPath(row); return }
  busyId.value = row.linkClipId
  try {
    await recordLinkClipView(row.linkClipId)
    window.open(row.url, '_blank', 'noopener')
    await load()
  } catch (cause) {
    toast.danger(messageOf(cause))
  } finally {
    busyId.value = null
  }
}

async function copyPath(row: ClipRow): Promise<void> {
  const ok = await copyText(row.url)
  if (ok) toast.success('パスをコピーしました。')
  else toast.danger('コピーできませんでした。手動で選択してください。')
}

async function toggleFavorite(row: ClipRow): Promise<void> {
  busyId.value = row.linkClipId
  try {
    await updateLinkClipFlags(row.linkClipId, { favorite: !row.favorite })
    await load()
  } catch (cause) {
    toast.danger(messageOf(cause))
  } finally {
    busyId.value = null
  }
}

/** カードの操作から直接削除する（詳細ドロワーの削除と同じ確認・通知）。 */
async function removeFromCard(row: ClipRow): Promise<void> {
  if (!window.confirm(`「${row.pageTitle}」を削除しますか？`)) return
  busyId.value = row.linkClipId
  try {
    await deleteLinkClip(row.linkClipId)
    toast.success('リンクを削除しました。')
    if (selected.value?.linkClipId === row.linkClipId) selected.value = null
    await load()
  } catch (cause) {
    toast.danger(messageOf(cause))
  } finally {
    busyId.value = null
  }
}

function editRow(row: ClipRow): void {
  editing.value = row
  selected.value = null
  composerOpen.value = true
}

function onSaved(): void {
  composerOpen.value = false
  editing.value = null
  page.value = 1
  void load()
}

function onRemoved(): void {
  selected.value = null
  void load()
}

watch(rows, () => { if (page.value > totalPages.value) page.value = 1 })

onMounted(() => { if (isFamilyUser.value) void load() })
</script>

<template>
  <div v-if="isFamilyUser" class="lc-page">
    <p v-if="error" class="alert alert--danger">{{ error }}</p>

    <div class="lc-body">
      <!-- 左: 仕分け -->
      <aside class="lc-lanes" aria-label="仕分け">
        <button
          v-for="lane in FOLDER_LANES" :key="lane.code" type="button"
          class="lc-lane" :class="{ 'is-active': activeLane === lane.code }"
          @click="selectLane(lane.code as LaneKey)"
        >
          <AppIcon :name="lane.icon" size="sm" class="lc-lane__icon" />
          <span class="lc-lane__body">
            <span class="lc-lane__label">{{ lane.label }}</span>
            <span class="lc-lane__desc">{{ lane.description }}</span>
          </span>
          <span v-if="isLaneCountVisible(lane.code as LaneKey)" class="lc-lane__count">{{ laneCount(lane.code as LaneKey) }}</span>
        </button>

        <template v-if="tagOptions.length > 0">
          <div class="lc-card__meta" style="padding: 8px 12px 0">
            <span>タグ</span>
            <button v-if="activeTag" type="button" class="lc-tag" @click="selectTag(activeTag)">解除</button>
          </div>
          <div class="lc-card__tags" style="padding: 4px 12px 8px">
            <button
              v-for="tag in tagOptions" :key="tag.name" type="button"
              class="lc-tag" :class="{ 'lc-tag--active': activeTag === tag.name }"
              :title="`${tag.count} 件`" @click="selectTag(tag.name)"
            >
              {{ tag.name }}
            </button>
          </div>
        </template>
      </aside>

      <!-- 中: 保存リンク一覧 -->
      <section class="lc-list-pane">
        <div class="search-panel">
          <div class="search-panel__head">
            <h3 class="search-panel__title"><AppIcon name="search" size="sm" /> 検索条件</h3>
            <div class="search-panel__actions">
              <button type="button" class="btn btn--secondary" @click="newClip"><AppIcon name="plus" size="sm" /> 新規</button>
              <button type="button" class="btn btn--primary" @click="search"><AppIcon name="search" size="sm" /> 検索</button>
              <button type="button" class="btn btn--secondary" @click="reset"><AppIcon name="rotate" size="sm" /> リセット</button>
            </div>
          </div>
          <div class="filters">
            <div class="filters__row">
              <span class="filter-item filter-item--grow">
                <span class="filter-item__label">キーワード：</span>
                <input
                  v-model="keyword" class="input" type="search" placeholder="タイトル・サイト名・URL・概要・メモ・タグ"
                  style="flex: 1" @keydown.enter.prevent="search"
                />
              </span>
              <span class="filter-item">
                <span class="filter-item__label">ソース：</span>
                <select v-model="sourceFilter" class="select" @change="search">
                  <option value="">すべて</option>
                  <option v-for="option in SOURCE_OPTIONS" :key="option" :value="option">{{ sourceLabel(option) }}</option>
                </select>
              </span>
              <span class="filter-item">
                <span class="filter-item__label">種別：</span>
                <select v-model="clipTypeFilter" class="select" @change="search">
                  <option value="">すべて</option>
                  <option v-for="option in CLIP_TYPE_OPTIONS" :key="option" :value="option">{{ clipTypeLabel(option) }}</option>
                </select>
              </span>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card__header">
            <h2 class="card__title">
              <AppIcon name="bookmark" /> 保存リンク一覧
            </h2>
            <span class="card__sub">
              {{ FOLDER_LANES.find((lane) => lane.code === activeLane)?.label }} ・ 全 {{ rows.length }} 件
              <template v-if="activeTag"> ・ タグ「{{ activeTag }}」</template>
            </span>
          </div>
          <div class="card__body">
            <p v-if="loading" class="lc-state">読込中...</p>
            <p v-else-if="rows.length === 0" class="lc-state">該当するリンクがありません。</p>

            <div v-else class="lc-list">
              <article
                v-for="row in paged" :key="row.linkClipId"
                class="lc-card" :class="{ 'is-selected': selected?.linkClipId === row.linkClipId }"
                @click="selectRow(row)"
              >
                <span class="lc-card__thumb" :style="thumbStyle(row)">
                  <AppIcon v-if="!row.thumbnailUrl" :name="sourceIcon(row.sourceCode)" />
                </span>

                <span class="lc-card__body">
                  <span class="lc-card__meta">
                    <span>{{ sourceLabel(row.sourceCode) }}</span>
                    <span>{{ formatDate(row.createdAt) }}</span>
                    <span v-if="row.videoSeconds" class="badge badge--neutral">{{ formatDuration(row.videoSeconds) }}</span>
                  </span>
                  <span class="lc-card__title">{{ row.pageTitle }}</span>
                  <span class="lc-card__summary">{{ row.summary || row.memo || '—' }}</span>
                  <span class="lc-card__meta">
                    <span class="badge badge--primary">{{ folderLabel(row.folderCode) }}</span>
                    <span v-if="row.favorite" class="badge badge--warning">お気に入り</span>
                    <span v-if="row.read" class="badge badge--success">既読</span>
                    <span v-if="row.archived" class="badge badge--neutral">アーカイブ</span>
                    <span v-if="isLocalFile(row)" class="badge badge--outline">ローカルファイル</span>
                  </span>
                  <span v-if="row.tags.length > 0" class="lc-card__tags">
                    <button
                      v-for="tag in cardTags(row).visible" :key="tag" type="button" class="lc-tag"
                      @click.stop="selectTag(tag)"
                    >{{ tag }}</button>
                    <span v-if="cardTags(row).hidden > 0" class="lc-tag lc-tag--static">+{{ cardTags(row).hidden }}</span>
                  </span>
                </span>

                <span class="lc-card__actions" @click.stop>
                  <button
                    type="button" class="btn btn--icon btn--sm" :class="{ 'is-favorite': row.favorite }"
                    :title="row.favorite ? 'お気に入りを解除' : 'お気に入りに追加'"
                    :disabled="busyId === row.linkClipId" @click="toggleFavorite(row)"
                  >
                    <AppIcon name="bookmark" size="sm" />
                  </button>
                  <button
                    type="button" class="btn btn--icon btn--sm"
                    :title="canOpenInBrowser(row) ? '元ページを開く' : 'パスをコピー'"
                    :disabled="busyId === row.linkClipId" @click="openFromCard(row)"
                  >
                    <AppIcon :name="canOpenInBrowser(row) ? 'globe' : 'copy'" size="sm" />
                  </button>
                  <button type="button" class="btn btn--icon btn--sm" title="修正" @click="editRow(row)">
                    <AppIcon name="edit" size="sm" class="icon--edit" />
                  </button>
                  <button
                    type="button" class="btn btn--icon btn--sm is-danger" title="削除"
                    :disabled="busyId === row.linkClipId" @click="removeFromCard(row)"
                  >
                    <AppIcon name="trash" size="sm" />
                  </button>
                </span>
              </article>
            </div>

            <div v-if="!loading && totalPages > 1" class="pagination">
              <span class="pagination__info">全 {{ rows.length }} 件（{{ page }} / {{ totalPages }} ページ）</span>
              <div class="pagination__pages">
                <button type="button" class="page-btn" :disabled="page <= 1" @click="goto(page - 1)">‹</button>
                <template v-for="(item, key) in pageItems" :key="key">
                  <span v-if="item === 'gap'" class="page-gap">…</span>
                  <button
                    v-else type="button" class="page-btn"
                    :class="{ 'is-active': item === page }" @click="goto(item)"
                  >
                    {{ item }}
                  </button>
                </template>
                <button type="button" class="page-btn" :disabled="page >= totalPages" @click="goto(page + 1)">›</button>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- 新規 / 編集はダイアログ -->
    <LinkClipComposer
      :open="composerOpen" :editing="editing"
      @close="closeComposer" @saved="onSaved"
    />

    <!-- 詳細は右からスライドして表示（左の一覧は隠さない） -->
    <LinkClipDetailPane
      v-if="selected"
      :row="selected"
      @close="selected = null"
      @edit="editRow(selected)"
      @changed="load"
      @removed="onRemoved"
      @select-tag="selectTag"
    />
  </div>
  <section v-else class="card page-body">
    <h2>リンククリップ</h2>
    <p>保存したリンクは、生徒または保護者としてログインした場合のみ表示できます。</p>
  </section>
</template>
