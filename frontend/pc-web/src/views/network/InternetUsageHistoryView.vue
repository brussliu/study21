<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiError, formatIsoDateTime } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import { searchAccessLogs, type AccessLogPage, type AccessLogRow } from '@/api/netAccessLogs'
import { searchTerminals } from '@/api/net'
import BrowserExtensionCodeDialog from '@/features/browserext/BrowserExtensionCodeDialog.vue'
import {
  EVENT_TYPE_LABELS,
  searchBrowsingLogs,
  type BrowsingLogPage,
  type BrowsingLogRow
} from '@/api/webBrowsingLogs'
import { paginationItems } from '@/features/pagination/pagination'
import '@/features/batch/batch.css'
import '@/features/browserext/browserext.css'

/**
 * インターネット利用履歴（メニュー「ネットワーク制御」＞「インターネット利用履歴」）。
 *
 * 2.0 の履歴管理画面（history.jsp）のタブ構成を引き継ぎ、次の 2 つをまとめる:
 *   * サイトアクセス履歴（= 上網履歴）… プロキシの通信履歴（`NET_プロキシ通信履歴情報`）
 *   * Web閲覧履歴（= ブラウザ閲覧履歴）… ブラウザ拡張の閲覧イベント（`NET_Web閲覧履歴情報`）
 *
 * どちらも 2.0 から移行した実データを読む。バッチ実行履歴・AI呼出履歴は「バッチ管理」側に分けた。
 * Web閲覧履歴タブには接続コードの入口（ボタン）だけを置く。コードの発行・再発行は
 * ダイアログ（BrowserExtensionCodeDialog）で行い、接続してきた端末の一覧は
 * 端末コントロールの「ブラウザ端末」タブに置く。拡張のダウンロードは画面右上の
 * プラグインメニュー（AppTopbar の BrowserPluginMenu）。
 */
const activeTab = ref<'access' | 'browsing'>('access')

/** 日付の入力（yyyy-MM-dd）をそのまま API に渡す。 */
function todayIso(): string {
  const now = new Date()
  return `${now.getFullYear()}-${`${now.getMonth() + 1}`.padStart(2, '0')}-${`${now.getDate()}`.padStart(2, '0')}`
}

/* ---------- サイトアクセス履歴 ---------- */
const loading = ref(false)
const error = ref('')
const page = ref(1)
const size = ref(20)
const totalElements = ref(0)
const totalPages = ref(0)
const rows = ref<AccessLogRow[]>([])
const filters = reactive({ host: '', terminalName: '', result: '' })

const sizeOptions = [20, 50, 100]

/**
 * 端末名称の絞り込みは登録済みの端末から選ぶ（アクセス履歴の端末名称は
 * NET_端末コントロール情報 を IP で引いた値なので、選択肢はそこから作る）。
 */
const terminalNameOptions = ref<string[]>([])

async function loadTerminalNames(): Promise<void> {
  try {
    const response = await searchTerminals({ size: 200 })
    const names = response.data.items.map((row) => row.terminalName.trim()).filter((name) => name !== '')
    terminalNameOptions.value = [...new Set(names)].sort((left, right) => left.localeCompare(right, 'ja'))
  } catch {
    // 選べなくても入力での絞り込みはできるようにする（一覧の表示は妨げない）
    terminalNameOptions.value = []
  }
}
const pageItems = computed(() => paginationItems(page.value, Math.max(1, totalPages.value)))

async function load(target = 1): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const response = await searchAccessLogs({
      host: filters.host,
      terminalName: filters.terminalName,
      result: filters.result,
      page: target,
      size: size.value
    })
    const data: AccessLogPage = response.data
    rows.value = data.items
    page.value = data.page
    totalPages.value = data.totalPages
    totalElements.value = data.totalElements
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : 'サイトアクセス履歴を取得できませんでした。'
  } finally {
    loading.value = false
  }
}

function search(): void {
  void load(1)
}

function reset(): void {
  filters.host = ''
  filters.terminalName = ''
  filters.result = ''
  void load(1)
}

function goto(next: number): void {
  if (next < 1 || next > totalPages.value) return
  void load(next)
}

function resultLabel(row: AccessLogRow): string {
  return row.result === 'DENY' ? '拒否' : '許可'
}

/* ---------- Web閲覧履歴 ---------- */
const browsingLoaded = ref(false)
const browsingLoading = ref(false)
const browsingError = ref('')
const browsingPage = ref(1)
const browsingSize = ref(20)
const browsingTotalElements = ref(0)
const browsingTotalPages = ref(0)
const browsingRows = ref<BrowsingLogRow[]>([])
const browsingFilters = reactive({
  terminalId: '',
  terminalName: '',
  domain: '',
  eventType: '',
  keyword: '',
  dateFrom: '',
  dateTo: ''
})

const browsingPageItems = computed(() =>
  paginationItems(browsingPage.value, Math.max(1, browsingTotalPages.value)))

async function loadBrowsing(target = 1): Promise<void> {
  browsingLoading.value = true
  browsingError.value = ''
  try {
    const response = await searchBrowsingLogs({
      terminalId: browsingFilters.terminalId,
      terminalName: browsingFilters.terminalName,
      domain: browsingFilters.domain,
      eventType: browsingFilters.eventType,
      keyword: browsingFilters.keyword,
      dateFrom: browsingFilters.dateFrom,
      dateTo: browsingFilters.dateTo,
      page: target,
      size: browsingSize.value
    })
    const data: BrowsingLogPage = response.data
    browsingRows.value = data.items
    browsingPage.value = data.page
    browsingTotalPages.value = data.totalPages
    browsingTotalElements.value = data.totalElements
    browsingLoaded.value = true
  } catch (caught) {
    browsingError.value = caught instanceof ApiError ? caught.message : 'Web閲覧履歴を取得できませんでした。'
  } finally {
    browsingLoading.value = false
  }
}

function searchBrowsing(): void {
  void loadBrowsing(1)
}

function resetBrowsing(): void {
  browsingFilters.terminalId = ''
  browsingFilters.terminalName = ''
  browsingFilters.domain = ''
  browsingFilters.eventType = ''
  browsingFilters.keyword = ''
  browsingFilters.dateFrom = ''
  browsingFilters.dateTo = ''
  void loadBrowsing(1)
}

function gotoBrowsing(next: number): void {
  if (next < 1 || next > browsingTotalPages.value) return
  void loadBrowsing(next)
}

function eventLabel(row: BrowsingLogRow): string {
  return EVENT_TYPE_LABELS[row.eventType] ?? row.eventType
}

/* ---------- 接続コード（ブラウザ拡張に設定するコード）---------- */
/**
 * 接続コードの発行・再発行は専用のダイアログで行う。
 * 画面には入口のボタンだけを置き、接続してきた端末の一覧は
 * 端末コントロール（「ブラウザ端末」タブ）に置く。
 */
const codeDialogOpen = ref(false)

/** タブを開いたときだけ読む（2.0 と同じ「そのタブの検索条件で検索」）。 */
function switchTab(tab: 'access' | 'browsing'): void {
  activeTab.value = tab
  if (tab === 'browsing' && !browsingLoaded.value) {
    void loadBrowsing(1)
  }
}

function staySecondsLabel(value: number | null): string {
  if (value === null) return '—'
  if (value < 60) return `${value} 秒`
  const minutes = Math.floor(value / 60)
  if (minutes < 60) return `${minutes} 分`
  return `${Math.floor(minutes / 60)} 時間 ${minutes % 60} 分`
}

onMounted(() => {
  void load(1)
  void loadTerminalNames()
  // 「今日」の初期値は今日の日付（2.0 の履歴画面は空だったが、件数が多いため既定で絞る）
  const today = todayIso()
  browsingFilters.dateFrom = today
  browsingFilters.dateTo = today
})
</script>

<template>
  <div class="batch-page">
    <div class="tabs" role="tablist">
      <button
        type="button"
        class="tabs__tab"
        :class="{ 'is-active': activeTab === 'access' }"
        role="tab"
        :aria-selected="activeTab === 'access'"
        data-tab="access"
        @click="switchTab('access')"
      >
        サイトアクセス履歴
      </button>
      <button
        type="button"
        class="tabs__tab"
        :class="{ 'is-active': activeTab === 'browsing' }"
        role="tab"
        :aria-selected="activeTab === 'browsing'"
        data-tab="browsing"
        @click="switchTab('browsing')"
      >
        Web閲覧履歴
      </button>
    </div>

    <!-- サイトアクセス履歴（プロキシの通信履歴。2.0 から移行済み） -->
    <template v-if="activeTab === 'access'">
      <div class="search-panel">
        <div class="search-panel__head">
          <h3 class="search-panel__title"><AppIcon name="search" size="sm" /> 検索条件</h3>
          <div class="search-panel__actions">
            <button type="button" class="btn btn--primary" :disabled="loading" @click="search">
              <AppIcon name="search" size="sm" /> 検索
            </button>
            <button type="button" class="btn btn--secondary" :disabled="loading" @click="reset">
              <AppIcon name="rotate" size="sm" /> リセット
            </button>
          </div>
        </div>
        <div class="filters">
          <div class="filters__row">
            <span class="filter-item">
              <span class="filter-item__label">接続先：</span>
              <input v-model="filters.host" class="input" type="search" placeholder="例: youtube.com" @keyup.enter="search">
            </span>
            <span class="filter-item">
              <span class="filter-item__label">端末名称：</span>
              <select v-model="filters.terminalName" class="select" style="width: 160px">
                <option value="">すべて</option>
                <option v-for="name in terminalNameOptions" :key="name" :value="name">{{ name }}</option>
              </select>
            </span>
            <span class="filter-item">
              <span class="filter-item__label">結果：</span>
              <select v-model="filters.result" class="select">
                <option value="">すべて</option>
                <option value="ALLOW">許可</option>
                <option value="DENY">拒否</option>
              </select>
            </span>
          </div>
        </div>
      </div>

      <div class="card table-section">
        <div class="table-section__head">
          <h3 class="table-section__title"><AppIcon name="list" size="sm" /> サイトアクセス履歴</h3>
          <span class="table-section__meta">全 {{ totalElements }} 件</span>
        </div>

        <p v-if="error" class="alert alert--danger">{{ error }}</p>
        <p v-else-if="loading" class="batch-page__loading">読み込んでいます...</p>
        <p v-else-if="rows.length === 0" class="batch-page__empty">該当するアクセス履歴がありません。</p>

        <div v-else class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>受付日時</th>
                <th>クライアントIP</th>
                <th>端末名称</th>
                <th>Method</th>
                <th>接続先</th>
                <th>URL</th>
                <th class="align-center">応答</th>
                <th class="align-center">結果</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.logId" :data-log-id="row.logId">
                <td class="cell-muted">{{ row.logId }}</td>
                <td>{{ row.receivedAt ? formatIsoDateTime(row.receivedAt) : '—' }}</td>
                <td>{{ row.clientIp ?? '—' }}</td>
                <td>{{ row.terminalName ?? '—' }}</td>
                <td>{{ row.httpMethod ?? '—' }}</td>
                <td class="cell-strong">{{ row.host ?? '—' }}</td>
                <td class="cell-muted">{{ row.url ?? '—' }}</td>
                <td class="align-center">{{ row.statusCode ?? '—' }}</td>
                <td class="align-center">
                  <span
                    class="badge"
                    :class="row.result === 'DENY' ? 'badge--danger' : 'badge--success'"
                    :title="row.errorDetail ?? undefined"
                  >{{ resultLabel(row) }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="!loading && totalElements > 0" class="pagination">
          <span class="pagination__info">全 {{ totalElements }} 件（{{ page }} / {{ totalPages }} ページ）</span>
          <!-- 件数はページングの左隣に置く（画面共通のルール） -->
          <label class="pagination__size">
            <span>件数</span>
            <select v-model.number="size" class="select" aria-label="1ページの件数" data-page-size @change="search">
              <option v-for="option in sizeOptions" :key="option" :value="option">{{ option }} 件</option>
            </select>
          </label>
          <div class="pagination__pages">
            <button type="button" class="page-btn" :disabled="page <= 1" @click="goto(page - 1)">‹</button>
            <template v-for="(item, key) in pageItems" :key="key">
              <span v-if="item === 'gap'" class="page-gap">…</span>
              <button
                v-else type="button" class="page-btn" :class="{ 'is-active': item === page }"
                @click="goto(item)"
              >
                {{ item }}
              </button>
            </template>
            <button type="button" class="page-btn" :disabled="page >= totalPages" @click="goto(page + 1)">›</button>
          </div>
        </div>
      </div>
    </template>

    <!-- Web閲覧履歴（ブラウザ拡張の閲覧イベント。2.0 から移行済み） -->
    <template v-else>
      <div class="search-panel">
        <div class="search-panel__head">
          <h3 class="search-panel__title"><AppIcon name="search" size="sm" /> 検索条件</h3>
          <div class="search-panel__actions">
            <button type="button" class="btn btn--primary" :disabled="browsingLoading" @click="searchBrowsing">
              <AppIcon name="search" size="sm" /> 検索
            </button>
            <button type="button" class="btn btn--secondary" :disabled="browsingLoading" @click="resetBrowsing">
              <AppIcon name="rotate" size="sm" /> リセット
            </button>
          </div>
        </div>
        <div class="filters">
          <div class="filters__row">
            <span class="filter-item">
              <span class="filter-item__label">端末ID：</span>
              <input v-model="browsingFilters.terminalId" class="input" type="search" placeholder="例: chrome-xxxx" @keyup.enter="searchBrowsing">
            </span>
            <span class="filter-item">
              <span class="filter-item__label">端末名称：</span>
              <input v-model="browsingFilters.terminalName" class="input" type="search" placeholder="例: LIU-PC" @keyup.enter="searchBrowsing">
            </span>
            <span class="filter-item">
              <span class="filter-item__label">ドメイン：</span>
              <input v-model="browsingFilters.domain" class="input" type="search" placeholder="例: youtube" @keyup.enter="searchBrowsing">
            </span>
            <span class="filter-item">
              <span class="filter-item__label">イベント：</span>
              <select v-model="browsingFilters.eventType" class="select">
                <option value="">すべて</option>
                <option v-for="(label, code) in EVENT_TYPE_LABELS" :key="code" :value="code">{{ label }}</option>
              </select>
            </span>
            <span class="filter-item filter-item--grow">
              <span class="filter-item__label">キーワード：</span>
              <input v-model="browsingFilters.keyword" class="input" type="search" placeholder="URL / ページタイトル" @keyup.enter="searchBrowsing">
            </span>
          </div>
          <div class="filters__row">
            <span class="filter-item">
              <span class="filter-item__label">アクセス日 From：</span>
              <input v-model="browsingFilters.dateFrom" class="input" type="date">
            </span>
            <span class="filter-item">
              <span class="filter-item__label">To：</span>
              <input v-model="browsingFilters.dateTo" class="input" type="date">
            </span>
          </div>
        </div>
      </div>

      <div class="card table-section">
        <div class="table-section__head">
          <h3 class="table-section__title"><AppIcon name="list" size="sm" /> Web閲覧履歴</h3>
          <!-- 接続コード（拡張の設定に入れる値）はダイアログで発行・再発行する -->
          <div class="bx-head-actions">
            <span class="table-section__meta">全 {{ browsingTotalElements }} 件</span>
            <button
              type="button" class="btn btn--secondary btn--sm"
              data-ext-code @click="codeDialogOpen = true"
            >
              <AppIcon name="key" size="sm" /> 接続コード
            </button>
          </div>
        </div>

        <p v-if="browsingError" class="alert alert--danger">{{ browsingError }}</p>
        <p v-else-if="browsingLoading" class="batch-page__loading">読み込んでいます...</p>
        <p v-else-if="browsingRows.length === 0" class="batch-page__empty">該当する閲覧履歴がありません。</p>

        <div v-else class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>アクセス日時</th>
                <th>端末ID</th>
                <th>端末名称</th>
                <th>イベント</th>
                <th>ドメイン</th>
                <th>URL</th>
                <th>ページタイトル</th>
                <th class="align-center">アクティブ</th>
                <th class="align-right">滞在時間</th>
                <th class="align-center">回数</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in browsingRows" :key="row.logId" :data-browsing-id="row.logId">
                <td class="cell-muted">{{ row.logId }}</td>
                <td>{{ row.accessedAt ? formatIsoDateTime(row.accessedAt) : '—' }}</td>
                <td class="cell-muted">{{ row.terminalId ?? '—' }}</td>
                <td>{{ row.terminalName ?? '—' }}</td>
                <td>{{ eventLabel(row) }}</td>
                <td class="cell-strong">{{ row.domain ?? '—' }}</td>
                <td class="cell-muted cell-url" :title="row.url ?? undefined">{{ row.url ?? '—' }}</td>
                <td>{{ row.pageTitle ?? '—' }}</td>
                <td class="align-center">
                  <span v-if="row.activeFlag === '1'" class="badge badge--success">アクティブ</span>
                  <span v-else class="badge badge--neutral">—</span>
                </td>
                <td class="align-right">{{ staySecondsLabel(row.staySeconds) }}</td>
                <td class="align-center">{{ row.viewCount ?? '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="!browsingLoading && browsingTotalElements > 0" class="pagination">
          <span class="pagination__info">
            全 {{ browsingTotalElements }} 件（{{ browsingPage }} / {{ browsingTotalPages }} ページ）
          </span>
          <!-- 件数はページングの左隣に置く（画面共通のルール） -->
          <label class="pagination__size">
            <span>件数</span>
            <select v-model.number="browsingSize" class="select" aria-label="1ページの件数" data-page-size @change="searchBrowsing">
              <option v-for="option in sizeOptions" :key="option" :value="option">{{ option }} 件</option>
            </select>
          </label>
          <div class="pagination__pages">
            <button type="button" class="page-btn" :disabled="browsingPage <= 1" @click="gotoBrowsing(browsingPage - 1)">‹</button>
            <template v-for="(item, key) in browsingPageItems" :key="key">
              <span v-if="item === 'gap'" class="page-gap">…</span>
              <button
                v-else type="button" class="page-btn" :class="{ 'is-active': item === browsingPage }"
                @click="gotoBrowsing(item)"
              >
                {{ item }}
              </button>
            </template>
            <button
              type="button" class="page-btn"
              :disabled="browsingPage >= browsingTotalPages" @click="gotoBrowsing(browsingPage + 1)"
            >
              ›
            </button>
          </div>
        </div>
      </div>
    </template>

    <!-- 接続コードの発行・再発行・確認 -->
    <BrowserExtensionCodeDialog :open="codeDialogOpen" @close="codeDialogOpen = false" />
  </div>
</template>
