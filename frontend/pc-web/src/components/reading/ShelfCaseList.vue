<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  DIFFICULTY_LABELS,
  STATUS_BADGES,
  STATUS_LABELS,
  readingCoverUrl,
  type ReadingBook
} from '@/api/reading'
import { shelfBgClass, shelfBgUrl } from '@/features/reading/shelfBackgrounds'
import {
  PULL_MS,
  SHELF_HOVER_Z,
  badgeOf,
  initialOf,
  markOf,
  patternClass,
  pdfReady,
  previewOnLeft,
  progressPercent,
  spineLines,
  spineStyle,
  tipOnRight,
  toneClass
} from '@/features/reading/shelfLook'

/** 本棚の 1 段（分類ごと）。 */
export interface ShelfSection {
  key: string
  name: string
  books: ReadingBook[]
}

/**
 * 本棚の 1 段ずつ（背表紙・ホバーの表紙パネル・ツールチップ）を描く。
 *
 * 【書籍閲覧2】の本棚タブを【書籍閲覧】の「自分の本棚」タブへ移したもの。
 * 背表紙の意匠（布目の質感・内枠・リブ・飾りの記号・傾き・光沢・接地の影・ホバーで
 * 手前に起き上がる動き）と、ホバーで出す暗いガラスの表紙パネルは、参照した試作
 * （`tmp/bookshelf_hover_cover_v6/bookshelf.html`）に合わせたもの。
 * 棚（奥板・棚板）は実写の写真（`assets/reading/shelf-bg-1〜6.jpg`。背景の切替で選ぶ）を敷き、本の底が
 * 写真の明るい棚板に乗るように CSS 側で座らせている（`features/reading/reading-shelf.css`）。
 *
 * 状態（ホバー・フォーカス・抜き出し）はこの中で完結し、
 * 本を開くタイミングだけを `open` で親に伝える（親は ?bookId= へ移動する）。
 */

const props = defineProps<{
  sections: ShelfSection[]
  /** 棚に並んでいく演出を付けるか（動きを減らす設定では false）。 */
  dealing: boolean
  /** まだ読んでいない本が残っているか（「もっと見る」を出す）。 */
  hasMore: boolean
  shown: number
  total: number
  /** 本が 1 冊も無いときの案内。 */
  emptyMessage: string
  /** 棚の背景の番号（0＝既定。`features/reading/shelfBackgrounds.ts`）。 */
  background: number
}>()

const emit = defineEmits<{
  /** 背表紙を抜き出す演出が終わった（親はその本の閲覧へ移る）。 */
  open: [book: ReadingBook]
  showMore: []
}>()

/** `prefers-reduced-motion: reduce` か（演出を切るために見る）。 */
const reduceMotion = ref(matchesReduceMotion())
let motionQuery: MediaQueryList | null = null

/** ホバー／フォーカス中の本（ツールチップと手前への移動に使う）。 */
const hoveredId = ref(0)
const focusedId = ref(0)
/** 抜き出し中の本（クリック直後〜親が閲覧へ切り替えるまで）。 */
const pulledId = ref(0)
let pullTimer: number | null = null

function matchesReduceMotion(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function onMotionChange(event: MediaQueryListEvent): void {
  reduceMotion.value = event.matches
}

onMounted(() => {
  motionQuery = window.matchMedia?.('(prefers-reduced-motion: reduce)') ?? null
  motionQuery?.addEventListener?.('change', onMotionChange)
})

onBeforeUnmount(() => {
  motionQuery?.removeEventListener?.('change', onMotionChange)
  if (pullTimer !== null) window.clearTimeout(pullTimer)
})

function isLit(bookId: number): boolean {
  return hoveredId.value === bookId || focusedId.value === bookId
}

/** 手前に出る演出のクラス（reduced motion では付けない）。 */
function isHovering(bookId: number): boolean {
  return !reduceMotion.value && isLit(bookId)
}

/** ツールチップを出すか（動きを減らす設定でも情報は出す）。 */
function isTipping(bookId: number): boolean {
  return isLit(bookId)
}

function isPulled(bookId: number): boolean {
  return pulledId.value === bookId
}

/**
 * 本を開く。
 * ・動きを減らす設定のときは演出を付けず、すぐ親に伝える
 * ・それ以外は背表紙を抜き出す演出（`is-pulled`）を見せてから伝える
 */
function open(item: ReadingBook): void {
  if (pulledId.value !== 0) return
  if (reduceMotion.value) {
    emit('open', item)
    return
  }
  pulledId.value = item.bookId
  pullTimer = window.setTimeout(() => {
    pullTimer = null
    emit('open', item)
  }, PULL_MS)
}

/** 閲覧から戻ってきたときに抜き出しの状態を戻す（親から呼ぶ）。 */
function resetPull(): void {
  if (pullTimer !== null) {
    window.clearTimeout(pullTimer)
    pullTimer = null
  }
  pulledId.value = 0
  hoveredId.value = 0
  focusedId.value = 0
}

defineExpose({ resetPull })
</script>

<template>
  <div class="rds-cases">
    <section
      v-for="section in props.sections" :key="section.key" class="rds-case"
      :class="shelfBgClass(props.background)"
      :style="{ '--rds-shelf-photo': `url(${shelfBgUrl(props.background)})` }"
      :data-rd-shelf-section="section.key"
    >
      <header class="rds-case__head">
        <h3 class="rds-case__title">{{ section.name }}</h3>
        <span class="rds-case__count">{{ section.books.length }} 冊</span>
      </header>

      <p v-if="section.books.length === 0" class="rds-empty" data-rd-shelf-empty>
        {{ props.emptyMessage }}
      </p>

      <template v-else>
        <!-- 棚の本体（奥板・棚板・側板）。木は背景画像と CSS で作る -->
        <div class="rds-case__unit">
          <div class="rds-case__board" :class="{ 'is-dealing': props.dealing }">
            <div
              v-for="(item, index) in section.books" :key="item.bookId"
              class="rds-book"
              :class="{
                'is-hover': isHovering(item.bookId), 'is-tip': isTipping(item.bookId),
                'is-pulled': isPulled(item.bookId),
                'is-preview-left': previewOnLeft(index, section.books.length),
                'is-tip-right': tipOnRight(index)
              }"
              :data-rd-spine-book="item.bookId"
              :style="{
                '--rds-index': index,
                // ホバー中の本は棚の中で最上位に持ち上げる（隣の本の下に隠れないように）
                zIndex: isLit(item.bookId) ? SHELF_HOVER_Z : undefined
              }"
              @mouseenter="hoveredId = item.bookId" @mouseleave="hoveredId = 0"
            >
              <!-- 棚板に落ちる楕円の影。背表紙より先に置いて、本の後ろ（足元）に描く -->
              <span class="rds-book__shadow" aria-hidden="true" />

              <button
                type="button" class="rds-spine"
                :class="[
                  toneClass(item), patternClass(item),
                  { 'is-no-pdf': !pdfReady(item), 'is-hover': isHovering(item.bookId), 'is-pulled': isPulled(item.bookId) }
                ]"
                :style="spineStyle(item)" :data-rd-spine="item.bookId" :data-rd-open="item.bookId"
                :data-rd-spine-lines="spineLines(item)"
                :title="`${item.title}（${item.author}）を開く`"
                @focus="focusedId = item.bookId" @blur="focusedId = 0" @click="open(item)"
              >
                <!-- 背表紙の意匠（布目・内枠・リブ・飾りの記号・光沢）。すべて絶対配置の飾りで、
                     読み上げには出さない（書名・作者は下の 2 つが持つ） -->
                <span class="rds-spine__grain" aria-hidden="true" />
                <span class="rds-spine__bevel" aria-hidden="true" />
                <span class="rds-spine__rib rds-spine__rib--head" aria-hidden="true" />
                <span class="rds-spine__rib rds-spine__rib--foot" aria-hidden="true" />

                <span class="rds-spine__title" data-rd-spine-title>{{ item.title }}</span>
                <span class="rds-spine__mark" aria-hidden="true">{{ markOf(item) }}</span>
                <span class="rds-spine__author" data-rd-spine-author>{{ item.author }}</span>
                <span v-if="!pdfReady(item)" class="rds-spine__flag" data-rd-spine-flag="missing">PDF 未登録</span>
                <span class="rds-spine__gloss" aria-hidden="true" />
                <span v-if="badgeOf(item) !== ''" class="rds-spine__badge">{{ badgeOf(item) }}</span>
              </button>

              <!-- ホバーで背表紙の横に浮かぶ「表紙と本の情報」のパネル（暗いガラスの面）。
                   以前は表紙（.rds-preview）とツールチップ（.rds-tip）の 2 枚を出していたが、
                   内容をこの 1 枚にまとめた（利用者の要望。情報は [data-rd-tip] の下に置く）。
                   必要なときだけ描くので、表紙画像はホバーするまで取りに行かない -->
              <div
                v-if="isTipping(item.bookId)"
                class="rds-cover-panel" :data-rd-tip="item.bookId"
              >
                <div class="rds-cover-panel__frame">
                  <img
                    v-if="item.coverAvailable"
                    class="rds-preview" data-rd-cover-preview
                    :src="readingCoverUrl(item.bookId, item.version)" :alt="`${item.title} の表紙`"
                  >
                  <div
                    v-else
                    class="rds-preview rds-preview--fallback" :class="toneClass(item)"
                    data-rd-cover-preview aria-hidden="true"
                  >
                    <span class="rds-preview__initial">{{ initialOf(item.title) }}</span>
                    <span class="rds-preview__title">{{ item.title }}</span>
                    <span class="rds-preview__author">{{ item.author }}</span>
                  </div>
                </div>

                <div class="rds-cover-panel__meta">
                  <span class="rds-cover-panel__label">表紙</span>
                  <span class="rds-cover-panel__title">{{ item.title }}</span>
                  <span class="rds-cover-panel__author">{{ item.author }}</span>
                  <span class="rds-cover-panel__badges">
                    <span class="badge badge--outline">{{ DIFFICULTY_LABELS[item.difficulty] }}</span>
                    <span class="badge badge--neutral">{{ item.language }}</span>
                    <span class="badge" :class="STATUS_BADGES[item.status]">{{ STATUS_LABELS[item.status] }}</span>
                  </span>
                  <span class="rds-cover-panel__progress" data-rd-progress>
                    進捗 {{ progressPercent(item) }}%（{{ item.currentPage }} / {{ item.totalPages }} ページ）
                  </span>
                  <span class="rds-cover-panel__meta-row">標記 {{ item.markCount }} 件</span>
                  <span v-if="!pdfReady(item)" class="rds-cover-panel__note">
                    PDF 未登録（{{ item.pdfOriginalName ?? '元ファイル名不明' }}）
                  </span>
                  <span class="rds-cover-panel__help">クリックで本文を開きます</span>
                </div>
              </div>
            </div>
          </div>
          <div class="rds-case__plank" />
        </div>
      </template>
    </section>

    <p v-if="props.sections.length === 0" class="rds-empty" data-rd-shelf-empty>
      {{ props.emptyMessage }}
    </p>
  </div>

  <p v-if="props.hasMore" class="rds-hint rds-cases__more">
    <button type="button" class="btn btn--secondary btn--sm" data-rd-shelf-more @click="emit('showMore')">
      <AppIcon name="chevron-down" size="sm" /> もっと見る（{{ props.shown }} / {{ props.total }} 冊）
    </button>
  </p>
</template>
