<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'
import {
  DETAIL_STATUS_BADGES,
  DETAIL_STATUS_LABELS,
  PART_OF_SPEECH_OPTIONS,
  collectionLabel
} from '@/features/japanese-demo/logic'
import { openEditPopup, openStudyPopup } from '@/features/japanese-demo/popup'
import DemoDeleteDialog from '@/features/japanese-demo/components/DemoDeleteDialog.vue'
import DemoWordNewView from './DemoWordNewView.vue'
import '@/features/japanese-demo/japanese-demo.css'

/**
 * 日本語勉強【単語情報管理】：一覧。
 *
 * 参考にした 2.0: `japanese_word.jsp` ＋ `js/japanese_word.js`
 * （検索条件で母表を絞り、行ごとに操作を置き、AI 取得の状態を列で見せる）。
 * 2.0 と同じく、**詳細編集と学習画面（A. 勉強）は別ウィンドウ**で開き、
 * 新規登録と削除確認はダイアログで行う。
 *
 * 一覧の絞り込み・ページ・スクロール位置はストアが持つので、
 * 別ウィンドウを閉じて戻っても同じ見え方に戻る。
 */

const store = useJapaneseDemoStore()

/** 二次条件（書籍・Unit・品詞・JLPT・状態）は既定でたたむ。 */
const advancedOpen = ref(false)
/** 収録が多い語を展開している ID（複数書籍の収録を見る）。 */
const expanded = ref<string[]>([])
/** 新規登録のダイアログ。 */
const creating = ref(false)
const deleting = ref(false)

const notice = computed(() => store.listNotice)

/** 二次条件に 1 つでも値が入っていればたたんでいても知らせる。 */
const advancedActive = computed(() =>
  store.filters.book !== '' || store.filters.unitFrom !== '' || store.filters.unitTo !== ''
  || store.filters.partOfSpeech !== '' || store.filters.jlpt !== '' || store.filters.detailStatus !== ''
)

function toggleExpanded(id: string): void {
  expanded.value = expanded.value.includes(id)
    ? expanded.value.filter((entry) => entry !== id)
    : [...expanded.value, id]
}

function collectionsOf(id: string): ReturnType<typeof collectionLabel>[] {
  const word = store.findWord(id)
  return word === null ? [] : word.collections.map((collection) => collectionLabel(collection))
}

/** 詳細編集を別ウィンドウで開く（下書きはストアと控えで共有する）。 */
function openEditor(id: string): void {
  store.rememberScroll(window.scrollY)
  if (store.openEditor(id)) {
    openEditPopup(id)
  }
}

/** 学習画面（A. 勉強）を別ウィンドウで開く。 */
function openStudy(id: string): void {
  store.rememberScroll(window.scrollY)
  openStudyPopup(id)
}

function openCreate(): void {
  creating.value = true
}

function closeCreate(): void {
  creating.value = false
}

function afterCreate(): void {
  creating.value = false
}

function askDelete(id: string): void {
  deleting.value = true
  store.openDelete(id)
}

function closeDelete(): void {
  deleting.value = false
  store.closeDelete()
}

async function confirmDelete(): Promise<void> {
  // 失敗したときは閉じない（理由を出したまま、やり直せるようにする）
  const deleted = await store.confirmDelete()
  if (deleted) {
    deleting.value = false
  }
}

function scrollToListPosition(): void {
  window.scrollTo({ top: store.listScrollTop })
}

onMounted(() => {
  // 別ウィンドウから戻ってきたときは、離れる前のスクロール位置に戻す
  scrollToListPosition()
  // 動作確認で状態を固定したいときだけ（画面には出さない）
  store.applyDisplayFromQuery(window.location.search)
})
</script>

<template>
  <div class="jp-demo">
    <div class="jp-demo__head">
      <div>
        <h1 class="page-title">単語情報管理</h1>
        <p class="page-sub">日本語の単語（母表）を検索し、詳細情報の状態を見て、編集・学習の確認をします。</p>
      </div>
      <div class="jp-demo__actions">
        <button type="button" class="btn btn--primary btn--sm" data-demo-new @click="openCreate">
          <AppIcon name="plus" size="sm" /> 新規登録
        </button>
      </div>
    </div>

    <p v-if="notice !== ''" class="alert alert--info" data-demo-notice-bar>
      {{ notice }}
      <button type="button" class="jp-demo-linkbtn" @click="store.listNotice = ''">閉じる</button>
    </p>

    <!-- 検索条件 -->
    <section class="search-panel">
      <div class="search-panel__head">
        <h2 class="search-panel__title"><AppIcon name="search" size="sm" /> 検索条件</h2>
        <div class="search-panel__actions">
          <button type="button" class="btn btn--primary btn--sm" data-demo-search @click="store.gotoPage(1)">
            <AppIcon name="search" size="sm" /> 検索
          </button>
          <button type="button" class="btn btn--secondary btn--sm" data-demo-filter-reset @click="store.resetFilters()">
            <AppIcon name="rotate" size="sm" /> リセット
          </button>
        </div>
      </div>

      <div class="filters">
        <div class="filters__row">
          <span class="filter-item filter-item--grow">
            <span class="filter-item__label">キーワード：</span>
            <input
              :value="store.filters.keyword" class="input" type="search" data-demo-filter-keyword
              placeholder="見出し語・読み・中国語意味"
              @input="store.setFilter('keyword', ($event.target as HTMLInputElement).value)"
            >
          </span>
          <button
            type="button" class="btn btn--secondary btn--sm" data-demo-advanced-toggle
            :aria-expanded="advancedOpen" @click="advancedOpen = !advancedOpen"
          >
            <AppIcon name="filter" size="sm" /> 詳しい条件
            <span v-if="advancedActive" class="jp-demo__badge">指定あり</span>
          </button>
        </div>

        <!-- 二次条件はたたんでおく（首屏を混み合わせない） -->
        <div v-if="advancedOpen" class="filters__row" data-demo-advanced>
          <span class="filter-item">
            <span class="filter-item__label">書籍：</span>
            <select
              :value="store.filters.book" class="select" data-demo-filter-book
              aria-label="書籍" @change="store.setFilter('book', ($event.target as HTMLSelectElement).value)"
            >
              <option value="">すべて</option>
              <option v-for="name in store.bookNameOptions" :key="name" :value="name">{{ name }}</option>
            </select>
          </span>
          <span class="filter-item" data-demo-filter-unit>
            <span class="filter-item__label">Unit：</span>
            <span class="range-input">
              <select
                :value="store.filters.unitFrom" class="select" aria-label="Unit（From）"
                data-demo-filter-unit-from
                @change="store.setFilter('unitFrom', ($event.target as HTMLSelectElement).value)"
              >
                <option value="">すべて</option>
                <option v-for="unit in store.unitOptions" :key="unit" :value="unit">{{ unit }}</option>
              </select>
              <span class="range-input__sep" aria-hidden="true">～</span>
              <select
                :value="store.filters.unitTo" class="select" aria-label="Unit（To）"
                data-demo-filter-unit-to
                @change="store.setFilter('unitTo', ($event.target as HTMLSelectElement).value)"
              >
                <option value="">すべて</option>
                <option v-for="unit in store.unitOptions" :key="unit" :value="unit">{{ unit }}</option>
              </select>
            </span>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">品詞：</span>
            <select
              :value="store.filters.partOfSpeech" class="select" data-demo-filter-part
              aria-label="品詞"
              @change="store.setFilter('partOfSpeech', ($event.target as HTMLSelectElement).value)"
            >
              <option value="">すべて</option>
              <option v-for="part in PART_OF_SPEECH_OPTIONS" :key="part" :value="part">{{ part }}</option>
            </select>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">JLPT：</span>
            <select
              :value="store.filters.jlpt" class="select" data-demo-filter-jlpt
              aria-label="JLPTレベル（例示値）"
              @change="store.setFilter('jlpt', ($event.target as HTMLSelectElement).value)"
            >
              <option value="">すべて</option>
              <option v-for="level in store.jlptOptions" :key="level" :value="level">{{ level }}</option>
            </select>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">詳細情報：</span>
            <select
              :value="store.filters.detailStatus" class="select" data-demo-filter-status
              aria-label="詳細情報の状態"
              @change="store.setFilter('detailStatus', ($event.target as HTMLSelectElement).value)"
            >
              <option value="">すべて</option>
              <option value="NOT_GENERATED">未生成</option>
              <option value="RUNNING">生成中</option>
              <option value="GENERATED">生成済み</option>
              <option value="EDITED">編集済み</option>
              <option value="FAILED">生成失敗</option>
            </select>
          </span>
          <p class="jp-demo-panel__hint">
            JLPT レベルは<strong>参考値</strong>です（全語について確認できているものではありません）。未確認の語は空にしています。
          </p>
        </div>
      </div>
    </section>

    <!-- 単語一覧 -->
    <section class="card">
      <div class="card__header">
        <h2 class="card__title"><AppIcon name="grid" size="sm" /> 単語一覧</h2>
        <span class="cell-muted" data-demo-count>
          全 {{ store.pageResult.total }} 件（仮データ {{ store.totalWords }} 語）
        </span>
        <div class="search-panel__actions">
          <label class="pagination__size">
            <span>件数</span>
            <select
              :value="store.listSize" class="select" aria-label="1ページの件数" data-demo-page-size
              @change="store.setPageSize(Number(($event.target as HTMLSelectElement).value))"
            >
              <option :value="10">10 件</option>
              <option :value="20">20 件</option>
              <option :value="50">50 件</option>
            </select>
          </label>
        </div>
      </div>

      <p v-if="store.pageWords.length === 0" class="jp-page__empty" data-demo-empty>
        条件に一致する単語がありません。キーワードを変えるか、「リセット」を押してください。
      </p>

      <div v-else class="table-wrap">
        <table class="data-table jp-demo-table" data-demo-words>
          <thead>
            <tr>
              <th class="col-actions">操作</th>
              <th>単語／読み</th>
              <th class="col-meaning">中国語の意味</th>
              <th class="col-narrow">品詞</th>
              <th class="col-collections">書籍／Unit</th>
              <th class="col-medium">詳細情報</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in store.pageWords" :key="row.id"
              :data-demo-word-row="row.id"
              :class="{ 'jp-demo-tr--error': row.detailStatus === 'FAILED' }"
            >
              <td class="row-actions jp-demo-row-actions">
                <button
                  type="button" class="jp-demo-linkbtn" data-demo-edit
                  :title="`${row.heading} の詳細を編集`" @click="openEditor(row.id)"
                >
                  <AppIcon name="edit" size="sm" /> 詳細編集
                </button>
                <button
                  type="button" class="jp-demo-linkbtn" data-demo-study
                  :title="`${row.heading} の学習画面を確認`" @click="openStudy(row.id)"
                >
                  <AppIcon name="eye" size="sm" /> 学習画面
                </button>
                <button
                  type="button" class="jp-demo-linkbtn is-danger" data-demo-delete
                  :title="`${row.heading} を削除`" @click="askDelete(row.id)"
                >
                  <AppIcon name="trash" size="sm" /> 削除
                </button>
              </td>

              <td>
                <span class="jp-demo-word" data-demo-word>{{ row.heading }}</span>
                <span class="jp-demo-word__reading" data-demo-reading>{{ row.reading }}</span>
                <!-- 同じ表記で読みが違う語があるときは、まとめずに別の語として見せる -->
                <span v-if="row.alternateReading" class="jp-demo-meta" data-demo-alternate>
                  同じ表記に「{{ row.alternateReading }}」の読みもあります（別の単語として登録）
                </span>
              </td>

              <td class="col-meaning" data-demo-meaning>{{ row.chineseMeaning }}</td>

              <td class="col-narrow">
                {{ row.partOfSpeech }}
                <span v-if="row.jlpt" class="jp-demo-meta">／{{ row.jlpt }}（例）</span>
                <span v-else class="jp-demo-meta">／レベル未確認</span>
              </td>

              <td class="col-collections">
                <div class="jp-demo-collections">
                  <span
                    v-for="collection in (expanded.includes(row.id) ? collectionsOf(row.id) : collectionsOf(row.id).slice(0, 2))"
                    :key="collection" class="jp-demo-collection-chip" :title="collection"
                  >
                    {{ collection }}
                  </span>
                </div>
                <button
                  v-if="row.collections.length > 2" type="button" class="jp-demo-linkbtn"
                  :data-demo-collections-toggle="row.id" @click="toggleExpanded(row.id)"
                >
                  {{ expanded.includes(row.id) ? 'たたむ' : `ほか ${row.collections.length - 2} 件` }}
                </button>
                <span v-if="row.collections.length === 0" class="jp-demo-meta">収録なし</span>
              </td>

              <td class="col-medium">
                <span class="jp-demo-status" :data-demo-status="row.detailStatus">
                  <span v-if="row.detailStatus === 'RUNNING'" class="jp-demo-status__spin" aria-hidden="true"></span>
                  <span class="badge" :class="DETAIL_STATUS_BADGES[row.detailStatus]">
                    {{ DETAIL_STATUS_LABELS[row.detailStatus] }}
                  </span>
                </span>
                <span v-if="row.detail !== null" class="jp-demo-meta">
                  語義 {{ row.detail.senses.length }}・例文 {{ row.detail.examples.length }}
                </span>
                <!-- 生成失敗は理由も見せる（「まだ無い」とは別の状態として扱う） -->
                <span v-if="row.detailStatus === 'FAILED' && row.failureReason" class="jp-demo-meta" data-demo-failure>
                  {{ row.failureReason }}
                </span>
                <button
                  v-if="row.detailStatus === 'NOT_GENERATED'" type="button"
                  class="jp-demo-linkbtn" :data-demo-generate="row.id" @click="store.startGeneration(row.id)"
                >
                  <AppIcon name="file" size="sm" /> 詳細情報を生成
                </button>
                <button
                  v-else-if="row.detailStatus === 'FAILED'" type="button"
                  class="jp-demo-linkbtn" :data-demo-retry="row.id" @click="store.startGeneration(row.id)"
                >
                  <AppIcon name="rotate" size="sm" /> 再試行
                </button>
                <span v-else-if="row.detailStatus === 'RUNNING'" class="jp-demo-meta">生成しています…</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="store.pageResult.total > 0" class="pagination">
        <span class="pagination__info" data-demo-page-info>
          全 {{ store.pageResult.total }} 件（{{ store.pageResult.page }} / {{ store.pageResult.totalPages }} ページ）
        </span>
        <div class="pagination__pages">
          <button
            type="button" class="page-btn" :disabled="store.pageResult.page <= 1"
            aria-label="前のページ" @click="store.gotoPage(store.pageResult.page - 1)"
          >
            ‹
          </button>
          <button
            v-for="page in store.pageResult.totalPages" :key="page"
            type="button" class="page-btn" :class="{ 'is-active': page === store.pageResult.page }"
            :data-demo-page="page" @click="store.gotoPage(page)"
          >
            {{ page }}
          </button>
          <button
            type="button" class="page-btn" :disabled="store.pageResult.page >= store.pageResult.totalPages"
            aria-label="次のページ" @click="store.gotoPage(store.pageResult.page + 1)"
          >
            ›
          </button>
        </div>
      </div>
    </section>

    <DemoWordNewView
      v-if="creating"
      @close="closeCreate"
      @saved="afterCreate"
    />

    <DemoDeleteDialog
      v-if="deleting && store.deleteTarget"
      :word="store.deleteTarget"
      :state="store.deleteState"
      :message="store.deleteMessage"
      @cancel="closeDelete"
      @confirm="confirmDelete"
    />
  </div>
</template>
