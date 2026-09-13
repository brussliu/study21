<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiError, formatIsoDateTime, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  fetchAiCallDetail,
  fetchAiCallFilters,
  searchAiCalls,
  type AiCallDetail,
  type AiCallFilterValues,
  type AiCallPage,
  type AiCallRow
} from '@/api/batch'
import { durationLabel } from '@/features/batch/batchLabels'
import { paginationItems } from '@/features/pagination/pagination'
import '@/features/batch/batch.css'

/**
 * AI呼出履歴（メニュー「バッチ管理」＞「AI呼出履歴」）。
 *
 * 2.0 の履歴管理画面（history.jsp）の「AI呼出履歴」タブに当たる。
 * 2.0 の `BAT_AI呼出履歴情報`（66,800 件・約 400MB。プロンプト・レスポンスの本文を含む）を
 * 全件移行したものを読む。
 *
 * 一覧では本文を返さない（1 行が 200KB 超になるため）。【詳細】で 1 件だけ本文を取る。
 */
const toast = useToast()

const loading = ref(false)
const page = ref<AiCallPage>({ items: [], totalElements: 0, page: 1, size: 20, totalPages: 0 })
const filters = reactive({
  batchCode: '',
  aiType: '',
  result: '',
  modelName: '',
  keyword: '',
  startFrom: '',
  startTo: ''
})
const filterValues = ref<AiCallFilterValues>({ aiTypes: [], batchCodes: [], models: [], results: [] })

const sizeOptions = [20, 50, 100]
const pageItems = computed(() => paginationItems(page.value.page, Math.max(1, page.value.totalPages)))

/** 詳細ダイアログ。 */
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<AiCallDetail | null>(null)

async function loadFilters(): Promise<void> {
  try {
    filterValues.value = (await fetchAiCallFilters()).data
  } catch {
    filterValues.value = { aiTypes: [], batchCodes: [], models: [], results: [] }
  }
}

async function load(target = 1): Promise<void> {
  loading.value = true
  try {
    // モデル名を選んだときはキーワード欄と同じ扱いで渡す（サーバ側の仕様に合わせる）
    const keyword = filters.modelName === '' ? filters.keyword : filters.modelName
    const response = await searchAiCalls({
      batchCode: filters.batchCode,
      aiType: filters.aiType,
      result: filters.result,
      keyword,
      startFrom: filters.startFrom === '' ? undefined : `${filters.startFrom}:00`,
      startTo: filters.startTo === '' ? undefined : `${filters.startTo}:59`,
      page: target,
      size: page.value.size
    })
    page.value = response.data
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : 'AI呼出履歴を取得できませんでした。')
  } finally {
    loading.value = false
  }
}

function search(): void {
  void load(1)
}

function reset(): void {
  filters.batchCode = ''
  filters.aiType = ''
  filters.result = ''
  filters.modelName = ''
  filters.keyword = ''
  filters.startFrom = ''
  filters.startTo = ''
  void load(1)
}

function goto(next: number): void {
  if (next < 1 || next > page.value.totalPages) return
  void load(next)
}

async function openDetail(row: AiCallRow): Promise<void> {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = (await fetchAiCallDetail(row.callId)).data
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : 'AI呼出履歴の詳細を取得できませんでした。')
    detailOpen.value = false
  } finally {
    detailLoading.value = false
  }
}

/** トークン数は 3 つを 1 セルにまとめる（入力 / 出力 / 合計）。 */
function tokenLabel(row: AiCallRow): string {
  if (row.inputTokens === null && row.outputTokens === null && row.totalTokens === null) return '—'
  return `${row.inputTokens ?? '—'} / ${row.outputTokens ?? '—'} / ${row.totalTokens ?? '—'}`
}

function resultBadgeClass(row: AiCallRow): string {
  return row.result === 'SUCCESS' ? 'badge--success' : 'badge--danger'
}

function errorSummary(row: AiCallRow): string {
  if (row.errorMessage !== null && row.errorMessage !== '') return row.errorMessage
  if (row.errorCode !== null && row.errorCode !== '') return row.errorCode
  return '—'
}

/** 本文が無い呼び出しもある（画像だけを送った等）。 */
function bodyOf(value: string | null): string {
  return value === null || value === '' ? '(本文なし)' : value
}

onMounted(async () => {
  await Promise.all([loadFilters(), load(1)])
})
</script>

<template>
  <div class="batch-page">
    <!-- 検索条件（一覧とは別のカード。ボタンは見出しの右端に置く＝他の一覧画面と同じ） -->
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
        <!--
          項目の幅は内容に任せない（選択肢が長いと勝手に広がって後ろの項目が次の行へ落ちる）。
          設計システムは .filter-item .select/.input に min-width: 150px を持つので、
          指定する値は 150px 以上にする（9.375rem 未満は無視されて幅がそろわない）。
          1 行目の合計は 999px（10+10+12+10rem ＋ ラベル ＋ 間隔）で、
          サイドメニューぶんを引いた 1366px 画面の幅（約 1030px）でも折り返さない。
        -->
        <div class="filters__row">
          <span class="filter-item">
            <span class="filter-item__label">バッチコード：</span>
            <select v-model="filters.batchCode" class="select" style="width: 10rem" aria-label="バッチコード">
              <option value="">すべて</option>
              <option v-for="code in filterValues.batchCodes" :key="code" :value="code">{{ code }}</option>
            </select>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">AI区分：</span>
            <select v-model="filters.aiType" class="select" style="width: 10rem" aria-label="AI区分">
              <option value="">すべて</option>
              <option v-for="aiType in filterValues.aiTypes" :key="aiType" :value="aiType">{{ aiType }}</option>
            </select>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">モデル名：</span>
            <select v-model="filters.modelName" class="select" style="width: 12rem" aria-label="モデル名">
              <option value="">すべて</option>
              <option v-for="model in filterValues.models" :key="model" :value="model">{{ model }}</option>
            </select>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">結果：</span>
            <select v-model="filters.result" class="select" style="width: 10rem" aria-label="結果">
              <option value="">すべて</option>
              <option value="SUCCESS">成功</option>
              <option value="FAILURE">失敗</option>
            </select>
          </span>
        </div>
        <!-- 2 行目: 残り幅いっぱいのキーワード ＋ 期間（From / To は同じ行に同じ幅で並べる） -->
        <div class="filters__row">
          <span class="filter-item filter-item--grow">
            <span class="filter-item__label">キーワード：</span>
            <input
              v-model="filters.keyword" class="input" type="search"
              placeholder="処理キー / モデル / URL / エラー" aria-label="キーワード"
              @keyup.enter="search"
            >
          </span>
          <span class="filter-item">
            <span class="filter-item__label">開始日時 From：</span>
            <input
              v-model="filters.startFrom" class="input" type="datetime-local"
              style="width: 13rem" aria-label="開始日時From"
            >
          </span>
          <span class="filter-item">
            <span class="filter-item__label">To：</span>
            <input
              v-model="filters.startTo" class="input" type="datetime-local"
              style="width: 13rem" aria-label="開始日時To"
            >
          </span>
        </div>
      </div>
    </div>

    <!-- 一覧 -->
    <div class="card table-section">
      <div class="table-section__head">
        <h3 class="table-section__title">AI呼出履歴</h3>
        <span class="table-section__meta">全 {{ page.totalElements }} 件</span>
      </div>

      <p v-if="loading" class="batch-page__loading">読み込み中...</p>
      <p v-else-if="page.items.length === 0" class="batch-page__empty">該当する AI 呼び出し履歴がありません。</p>

      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th class="col-actions">操作</th>
              <th>ID</th>
              <th>実行ID</th>
              <th>バッチコード</th>
              <th>言語</th>
              <th>AI区分</th>
              <th>モデル名</th>
              <th class="align-center">結果</th>
              <th class="align-center">HTTP</th>
              <th>開始日時</th>
              <th>終了日時</th>
              <th class="align-right">処理時間</th>
              <th class="align-right" title="入力 / 出力 / 合計">トークン</th>
              <th>処理キー</th>
              <th>エラー概要</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in page.items" :key="row.callId" :data-call-id="row.callId">
              <td class="row-actions">
                <button
                  type="button" class="btn btn--icon btn--sm" title="詳細"
                  :aria-label="`${row.callId} の詳細`" :data-detail="row.callId" @click="openDetail(row)"
                >
                  <AppIcon name="eye" size="sm" />
                </button>
              </td>
              <td class="cell-muted">{{ row.callId }}</td>
              <td class="cell-muted">{{ row.executionId ?? '—' }}</td>
              <td class="cell-strong">{{ row.batchCode }}</td>
              <td>{{ row.language ?? '—' }}</td>
              <td>{{ row.aiType ?? '—' }}</td>
              <td>{{ row.modelName ?? '—' }}</td>
              <td class="align-center">
                <span class="badge" :class="resultBadgeClass(row)">{{ row.resultLabel }}</span>
              </td>
              <td class="align-center">{{ row.httpStatus ?? '—' }}</td>
              <td class="cell-muted">{{ row.startTime ? formatIsoDateTime(row.startTime) : '—' }}</td>
              <td class="cell-muted">{{ row.endTime ? formatIsoDateTime(row.endTime) : '—' }}</td>
              <td class="align-right cell-muted">{{ durationLabel(row.durationMs) }}</td>
              <td class="align-right cell-muted">{{ tokenLabel(row) }}</td>
              <td class="cell-muted">{{ row.processKey ?? '—' }}</td>
              <td>
                <span class="batch-page__message" :title="errorSummary(row)">{{ errorSummary(row) }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="!loading && page.totalElements > 0" class="pagination">
        <span class="pagination__info">全 {{ page.totalElements }} 件（{{ page.page }} / {{ page.totalPages }} ページ）</span>
        <!-- 件数はページングの左隣に置く（画面共通のルール） -->
        <label class="pagination__size">
          <span>件数</span>
          <select v-model.number="page.size" class="select" aria-label="1ページの件数" data-page-size @change="search">
            <option v-for="option in sizeOptions" :key="option" :value="option">{{ option }} 件</option>
          </select>
        </label>
        <div class="pagination__pages">
          <button type="button" class="page-btn" :disabled="page.page <= 1" @click="goto(page.page - 1)">‹</button>
          <template v-for="(item, key) in pageItems" :key="key">
            <span v-if="item === 'gap'" class="page-gap">…</span>
            <button
              v-else type="button" class="page-btn" :class="{ 'is-active': item === page.page }"
              @click="goto(item)"
            >
              {{ item }}
            </button>
          </template>
          <button type="button" class="page-btn" :disabled="page.page >= page.totalPages" @click="goto(page.page + 1)">›</button>
        </div>
      </div>
    </div>

    <!-- 詳細（プロンプト・レスポンスの本文つき。2.0 の AI呼出履歴詳細ダイアログに相当） -->
    <div v-if="detailOpen" class="overlay">
      <section class="dialog dialog--lg" role="dialog" aria-modal="true" aria-labelledby="aiDetailTitle">
        <header class="dialog__head">
          <h2 id="aiDetailTitle" class="dialog__title">AI呼出履歴の詳細</h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="detailOpen = false">
            <AppIcon name="x" size="sm" />
          </button>
        </header>

        <div class="dialog__body">
          <p v-if="detailLoading" class="batch-page__loading">読み込んでいます...</p>

          <template v-else-if="detail">
            <dl class="ai-call-detail" data-testid="ai-call-detail">
              <div><dt>ID</dt><dd>{{ detail.callId }}</dd></div>
              <div><dt>実行ID</dt><dd>{{ detail.executionId ?? '—' }}</dd></div>
              <div><dt>バッチコード</dt><dd>{{ detail.batchCode }}</dd></div>
              <div><dt>処理キー</dt><dd>{{ detail.processKey ?? '—' }}</dd></div>
              <div><dt>言語</dt><dd>{{ detail.language ?? '—' }}</dd></div>
              <div><dt>AI区分</dt><dd>{{ detail.aiType ?? '—' }}</dd></div>
              <div><dt>モデル名</dt><dd>{{ detail.modelName ?? '—' }}</dd></div>
              <div><dt>結果</dt><dd>{{ detail.resultLabel }}</dd></div>
              <div><dt>HTTP</dt><dd>{{ detail.httpStatus ?? '—' }}</dd></div>
              <div><dt>開始日時</dt><dd>{{ detail.startTime ? formatIsoDateTime(detail.startTime) : '—' }}</dd></div>
              <div><dt>終了日時</dt><dd>{{ detail.endTime ? formatIsoDateTime(detail.endTime) : '—' }}</dd></div>
              <div><dt>処理時間</dt><dd>{{ durationLabel(detail.durationMs) }}</dd></div>
              <div><dt>トークン</dt><dd>{{ tokenLabel(detail) }}</dd></div>
              <div><dt>エラー</dt><dd>{{ errorSummary(detail) }}</dd></div>
              <div class="ai-call-detail__wide"><dt>呼出URL</dt><dd>{{ detail.callUrl ?? '—' }}</dd></div>
            </dl>

            <p v-if="detail.bodyTruncated" class="batch-page__note">
              本文が長いため {{ detail.bodyLimit.toLocaleString() }} 文字で切って表示しています。
            </p>

            <h3 class="ai-call-detail__heading">プロンプト</h3>
            <pre class="ai-call-detail__body" data-testid="ai-call-prompt">{{ bodyOf(detail.prompt) }}</pre>

            <h3 class="ai-call-detail__heading">AIレスポンス</h3>
            <pre class="ai-call-detail__body" data-testid="ai-call-response">{{ bodyOf(detail.response) }}</pre>
          </template>
        </div>

        <footer class="dialog__foot">
          <button type="button" class="btn btn--secondary" @click="detailOpen = false">閉じる</button>
        </footer>
      </section>
    </div>
  </div>
</template>
