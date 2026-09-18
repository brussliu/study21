<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { formatIsoDateTime } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import { FIGURE_TYPE_LABELS, geometryThumbnailUrl, type GeometryFigure } from '@/api/geometry'
import '@/features/geometry/geometry.css'

/**
 * 図形の**印刷プレビューの小窓**（一覧のカードの【印刷】で開く）。
 *
 * 1 件だけを紙に出す。画面では操作の行（【印刷する】【閉じる】）を出し、
 * 印刷するときは `@media print` でアプリの画面（サイドバー・上部バー・検索条件・一覧）と
 * 操作の行を消して、**この紙面（`[data-gm-print-sheet]`）だけ**を刷る。
 *
 * ・既定は**図だけ**（大きなサムネイル。教材として貼るだけの使い方を既定にする）
 * ・【詳細情報も印刷する】を入れると、図形名・番号・種別・区分・タグ・更新日・メモも出す
 * ・切り替えは画面にも紙にもすぐ反映する（`@media print` も同じ状態で出す）
 *
 * 開いている間は `<html>` に `gm-print-open` を付ける（画面を消す切り替えに使う）。
 */
const props = defineProps<{ figure: GeometryFigure }>()
const emit = defineEmits<{ close: [] }>()

/** 詳細情報（図形名・番号・種別・区分・タグ・更新日・メモ）も出すか。既定は図だけ。 */
const showDetail = ref(false)

/** 更新日（無ければ作成日、どちらも無ければ —）。一覧と同じ書き方。 */
function updatedLabel(): string {
  const value = props.figure.updatedAt ?? props.figure.createdAt
  return value === null || value === undefined || value === '' ? '—' : formatIsoDateTime(value)
}

function kindLabel(): string {
  return props.figure.kind === 'demo' ? '初期データ' : '作成した図形'
}

function tagsLabel(): string {
  return props.figure.tags.length === 0 ? 'タグなし' : props.figure.tags.join(' / ')
}

/** メモは有るときだけ出す。 */
const memo = computed(() => (props.figure.memo ?? '').trim())

function print(): void {
  window.print()
}

// 印刷するときは紙面だけを出す（@media print の切り替え）
onMounted(() => { document.documentElement.classList.add('gm-print-open') })
onBeforeUnmount(() => { document.documentElement.classList.remove('gm-print-open') })
</script>

<template>
  <Teleport to="body">
    <div class="overlay gm-print-overlay">
      <section
        class="dialog dialog--lg gm-print-dialog" role="dialog" aria-modal="true"
        :aria-label="`${figure.title} の印刷プレビュー`" data-gm-print-dialog
      >
        <header class="dialog__head">
          <h2 class="dialog__title"><AppIcon name="print" size="sm" /> 印刷プレビュー</h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="emit('close')">
            <AppIcon name="x" size="sm" />
          </button>
        </header>

        <div class="dialog__body">
          <!-- 紙面（印刷するのはこの中だけ）。
               既定は図だけ。詳細を入れると図形名・番号・各種情報も出す。 -->
          <article
            class="gm-print-sheet"
            :class="showDetail ? 'gm-print-sheet--detail' : 'gm-print-sheet--figure'"
            data-gm-print-sheet
          >
            <template v-if="showDetail">
              <h3 class="gm-print-sheet__title" data-gm-print-title>{{ figure.title }}</h3>
              <p class="gm-print-sheet__no" data-gm-print-no>{{ figure.figureNo }}</p>
            </template>

            <div class="gm-print-sheet__thumb">
              <img
                v-if="figure.hasThumbnail" :src="geometryThumbnailUrl(figure.figureId, figure.version)"
                :alt="figure.title" data-gm-print-thumb
              >
              <div v-else class="gm-print-sheet__thumb-empty" data-gm-print-thumb-empty>
                <AppIcon name="image" size="lg" />
                <span>サムネイルなし</span>
              </div>
            </div>

            <dl v-if="showDetail" class="gm-print-sheet__fields" data-gm-print-fields>
              <div class="gm-print-sheet__row">
                <dt>種別</dt>
                <dd data-gm-print-type>{{ FIGURE_TYPE_LABELS[figure.figureType] }}</dd>
              </div>
              <div class="gm-print-sheet__row">
                <dt>区分</dt>
                <dd data-gm-print-kind>{{ kindLabel() }}</dd>
              </div>
              <div class="gm-print-sheet__row">
                <dt>タグ</dt>
                <dd data-gm-print-tags>{{ tagsLabel() }}</dd>
              </div>
              <div class="gm-print-sheet__row">
                <dt>更新</dt>
                <dd data-gm-print-updated>{{ updatedLabel() }}</dd>
              </div>
              <div v-if="memo !== ''" class="gm-print-sheet__row">
                <dt>メモ</dt>
                <dd data-gm-print-memo>{{ memo }}</dd>
              </div>
            </dl>
          </article>
        </div>

        <!-- 操作の行（紙には出さない） -->
        <footer class="dialog__foot dialog__foot--between gm-print-dialog__foot">
          <label class="gm-print-dialog__option">
            <input v-model="showDetail" type="checkbox" data-gm-print-detail>
            <span>詳細情報も印刷する</span>
          </label>
          <div class="search-panel__actions">
            <button type="button" class="btn btn--primary" data-gm-print-run @click="print">
              <AppIcon name="print" size="sm" /> 印刷する
            </button>
            <button type="button" class="btn btn--secondary" data-gm-print-close @click="emit('close')">
              <AppIcon name="x" size="sm" /> 閉じる
            </button>
          </div>
        </footer>
      </section>
    </div>
  </Teleport>
</template>
