<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiError, formatIsoDateTime, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  fetchBatchTasks,
  searchBatchExecutions,
  type BatchExecutionPage
} from '@/api/batch'
import {
  STATUS_BADGE_CLASSES,
  STATUS_LABELS,
  TYPE_LABELS,
  batchTargetLabel,
  durationLabel,
  optionsOf
} from '@/features/batch/batchLabels'
import { paginationItems } from '@/features/pagination/pagination'
import '@/features/batch/batch.css'

/**
 * バッチ実行履歴（メニュー「バッチ管理」＞「バッチ実行履歴」）。
 *
 * 2.0 の履歴管理画面（history.jsp）の「バッチ実行履歴」タブに当たる。
 * 2.1 は実データ（`BAT_バッチ実行履歴情報`。2.0 から移行した 3,500 件以上）を
 * 検索・ページングで表示する。
 */
const toast = useToast()

const historyLoading = ref(false)
const history = ref<BatchExecutionPage>({ items: [], totalElements: 0, page: 1, size: 15, totalPages: 0 })
const historyFilters = reactive({ batchCode: '', status: '', keyword: '' })
/** 1 ページの件数（ページングの左隣で選ぶ）。 */
const historySize = ref(20)
const sizeOptions = [20, 50, 100]
const batchCodes = ref<string[]>([])

const statusOptions = optionsOf(STATUS_LABELS)
const batchOptions = computed(() => batchCodes.value.map((code) => ({ value: code, label: code })))
const historyPages = computed(() => paginationItems(history.value.page, Math.max(1, history.value.totalPages)))

/** 絞り込み用に、実行履歴に存在するバッチコードを集める（新しい順の先頭 200 件から）。 */
async function loadBatchCodes(): Promise<void> {
  try {
    const response = await searchBatchExecutions({ page: 1, size: 100 })
    const codes = new Set(response.data.items.map((item) => item.batchCode))
    for (const row of (await fetchBatchTasks()).data.rows) {
      codes.add(row.taskCode)
    }
    batchCodes.value = [...codes].sort()
  } catch {
    batchCodes.value = []
  }
}

async function loadHistory(page = 1): Promise<void> {
  historyLoading.value = true
  try {
    const response = await searchBatchExecutions({
      batchCode: historyFilters.batchCode,
      status: historyFilters.status,
      keyword: historyFilters.keyword,
      page,
      size: historySize.value
    })
    history.value = response.data
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '実行履歴を取得できませんでした。')
  } finally {
    historyLoading.value = false
  }
}

function searchHistory(): void {
  void loadHistory(1)
}

/** 1 ページの件数を変える（1 ページ目から取り直す）。 */
function changeSize(): void {
  void loadHistory(1)
}

onMounted(async () => {
  await Promise.all([loadBatchCodes(), loadHistory(1)])
})
</script>

<template>
  <div class="batch-page">
    <div class="card table-section">
      <div class="table-section__head">
        <h3 class="table-section__title"><AppIcon name="list" size="sm" /> 実行履歴</h3>
        <span class="table-section__meta">全 {{ history.totalElements }} 件</span>
        <span class="batch-page__filters">
          <select v-model="historyFilters.batchCode" class="select" aria-label="バッチコード" @change="searchHistory">
            <option value="">すべてのバッチ</option>
            <option v-for="option in batchOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
          <select v-model="historyFilters.status" class="select" aria-label="状態" @change="searchHistory">
            <option value="">すべての状態</option>
            <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
          <input
            v-model="historyFilters.keyword"
            class="input"
            type="search"
            placeholder="メッセージ / エラー"
            aria-label="キーワード"
            @keyup.enter="searchHistory"
          >
          <button type="button" class="btn btn--primary btn--sm" @click="searchHistory">検索</button>
        </span>
      </div>

      <p v-if="historyLoading" class="batch-page__loading">読み込み中...</p>
      <p v-else-if="history.items.length === 0" class="batch-page__empty">実行履歴がありません。</p>

      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>実行ID</th>
              <th>バッチコード</th>
              <th>対象</th>
              <th>種別</th>
              <th>起動</th>
              <th class="align-center">状態</th>
              <th>開始時刻</th>
              <th>終了時刻</th>
              <th>処理時間</th>
              <th>メッセージ</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in history.items" :key="item.executionId" :data-execution-id="item.executionId">
              <td class="cell-muted">{{ item.executionId }}</td>
              <td class="cell-strong">{{ item.batchCode }}</td>
              <!-- 何を処理した行か（バッチコードは再利用されるため。分からない行は「—」） -->
              <td class="cell-muted batch-page__target" data-batch-target>{{ batchTargetLabel(item) }}</td>
              <td>{{ TYPE_LABELS[item.batchType] }}</td>
              <td>{{ TYPE_LABELS[item.triggerType] }}</td>
              <td class="align-center">
                <span
                  class="badge"
                  :class="STATUS_BADGE_CLASSES[item.status]"
                  :title="item.errorDetail ?? undefined"
                >{{ STATUS_LABELS[item.status] }}</span>
              </td>
              <td class="cell-muted">{{ item.startTime ? formatIsoDateTime(item.startTime) : '—' }}</td>
              <td class="cell-muted">{{ item.endTime ? formatIsoDateTime(item.endTime) : '—' }}</td>
              <td class="cell-muted">{{ durationLabel(item.durationMs) }}</td>
              <td>
                <span class="batch-page__message" :title="item.message ?? ''">{{ item.message ?? '—' }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="!historyLoading && history.totalElements > 0" class="pagination">
        <span class="pagination__info">全 {{ history.totalElements }} 件（{{ history.page }} / {{ history.totalPages }} ページ）</span>
        <!-- 件数はページングの左隣に置く（画面共通のルール） -->
        <label class="pagination__size">
          <span>件数</span>
          <select
            v-model.number="historySize" class="select"
            aria-label="1ページの件数" data-page-size @change="changeSize"
          >
            <option v-for="option in sizeOptions" :key="option" :value="option">{{ option }} 件</option>
          </select>
        </label>
        <div class="pagination__pages">
          <button type="button" class="page-btn" :disabled="history.page <= 1" @click="loadHistory(history.page - 1)">‹</button>
          <template v-for="(item, key) in historyPages" :key="key">
            <span v-if="item === 'gap'" class="page-gap">…</span>
            <button
              v-else type="button" class="page-btn" :class="{ 'is-active': item === history.page }"
              @click="loadHistory(item)"
            >
              {{ item }}
            </button>
          </template>
          <button
            type="button" class="page-btn" :disabled="history.page >= history.totalPages"
            @click="loadHistory(history.page + 1)"
          >
            ›
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
