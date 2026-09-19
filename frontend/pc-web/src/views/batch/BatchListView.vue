<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiError, formatIsoDateTime, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  fetchBatchSchedule,
  fetchBatchTasks,
  rerunBatchTask,
  updateBatchActive,
  type BatchScheduleResult,
  type BatchScheduleTask,
  type BatchTaskRow
} from '@/api/batch'
import { STATUS_BADGE_CLASSES, STATUS_LABELS, TYPE_LABELS, timingLabel } from '@/features/batch/batchLabels'
import { pendingRefreshMessage, scheduleByTaskCode } from '@/features/batch/batchSchedule'
import { buildBatchTabs } from '@/features/batch/batchGroups'
import '@/features/batch/batch.css'

/**
 * バッチ一覧（メニュー「バッチ管理」＞「バッチ一覧」）。
 *
 * 2.0 のバッチ管理画面（batch.jsp）のタスク一覧に当たる。実行の履歴は
 * 「バッチ実行履歴」に分けた（`BatchHistoryView.vue`）。
 * 2.1 で自動実行するのは統合スケジューラが扱う 4 タスク（batR03 / batR04 / batL02 / batL03）で、
 * 実行のきっかけは admin-api の起動時 1 回と、この画面の【再実行】、そしてスケジューラの 3 つ。
 * 種別 C（呼出）は他の処理が工程として呼ぶバッチなので、【再実行】ボタンは出さない
 * （`canManualRerun=false`。判定はバックエンドの `BatchTaskDefinition#canManualRerun`）。
 *
 * **実行タイミングの列**は、2.1 から `GET /api/admin/batch/schedule` の実際の設定
 * （`describe` と `nextRunLabel`）を出す。以前は種別 R が「定時」としか出ず、何時に動くのか
 * 分からなかった（設定で時刻が変わるようになったため）。スケジュール API が取れないときは
 * 定義のタイミング（`timingLabel`）へ戻す（表は壊さない）。
 */
const toast = useToast()

const loading = ref(false)
const busy = ref(false)
const error = ref('')
const rows = ref<BatchTaskRow[]>([])
const schedule = ref<BatchScheduleResult | null>(null)
const result = ref<{ failed: boolean; message: string; detail?: string | null } | null>(null)
const activeTab = ref('all')

/** タスクコード → 実行スケジュール（1 回読んで突き合わせる）。 */
const scheduleTasks = computed<Map<string, BatchScheduleTask>>(() =>
  scheduleByTaskCode(schedule.value?.tasks ?? [])
)
/** 反映待ち（保存済みだが実行設定へ未反映）の控えめな注意表示。 */
const pendingText = computed(() => {
  const data = schedule.value
  if (!data?.pendingRefresh) return ''
  const message = pendingRefreshMessage(data.pendingMessage)
  return message ?? '保存済み・実行設定への反映待ちです（自動で再試行します）。'
})

interface BatchRowView {
  row: BatchTaskRow
  schedule: BatchScheduleTask | null
}

/** タブ（2.0 のバッチ管理画面のグルーピングを引き継ぐ）。 */
const tabs = computed(() => buildBatchTabs(rows.value))
/** 選択中のタブ。読み込みでタブが消えたら「すべて」に戻す。 */
const activeTabKey = computed(() =>
  tabs.value.some((tab) => tab.key === activeTab.value) ? activeTab.value : 'all'
)
const activeTabTitle = computed(() => tabs.value.find((tab) => tab.key === activeTabKey.value)?.title ?? 'すべて')
const visibleRows = computed<BatchRowView[]>(() =>
  (tabs.value.find((tab) => tab.key === activeTabKey.value)?.rows ?? rows.value).map((row) => ({
    row,
    schedule: scheduleTasks.value.get(row.taskCode) ?? null
  }))
)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    // 一覧と実行スケジュールは別の API。片方が落ちても表は出す（スケジュールは定義表示へ戻す）
    const [taskResponse, scheduleResponse] = await Promise.allSettled([fetchBatchTasks(), fetchBatchSchedule()])
    if (taskResponse.status === 'fulfilled') rows.value = taskResponse.value.data.rows
    else error.value = taskResponse.reason instanceof ApiError
      ? taskResponse.reason.message
      : 'バッチ一覧を取得できませんでした。'
    schedule.value = scheduleResponse.status === 'fulfilled' ? scheduleResponse.value.data : null
  } finally {
    loading.value = false
  }
}

/** 有効／無効を切り替える。 */
async function toggleActive(row: BatchTaskRow): Promise<void> {
  if (busy.value) return
  busy.value = true
  result.value = null
  try {
    const response = await updateBatchActive(row.taskCode, !row.active)
    toast.success(response.data.message)
    await load()
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '有効設定を更新できませんでした。')
  } finally {
    busy.value = false
  }
}

/** 【再実行】。実行履歴に記録し、その場で業務処理を動かす。 */
async function rerun(row: BatchTaskRow): Promise<void> {
  if (busy.value) return
  busy.value = true
  result.value = null
  try {
    const response = await rerunBatchTask(row.taskCode)
    const data = response.data
    result.value = { failed: !data.success, message: data.message, detail: data.errorDetail }
    if (data.success) {
      toast.success(`${row.taskCode}: ${data.message}`)
    } else {
      toast.danger(`${row.taskCode}: ${data.message}`)
    }
    await load()
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '再実行できませんでした。')
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="batch-page">
    <p v-if="error" class="alert alert--danger">{{ error }}</p>

    <!-- 反映待ち（保存済みだが実行設定へ未反映）の控えめな注意表示。
         自動で再試行されるので、操作を促す警告ではなく状態の案内として出す -->
    <p v-if="pendingText" class="batch-page__notice" data-testid="batch-pending" role="status">
      <AppIcon name="alert" size="sm" />
      <span>{{ pendingText }}</span>
    </p>

    <!-- グルーピング（2.0 のバッチ管理画面と同じタブ。件数はタブごとに出す） -->
    <div class="tabs" role="tablist">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="tabs__tab"
        :class="{ 'is-active': tab.key === activeTabKey }"
        role="tab"
        :aria-selected="tab.key === activeTabKey"
        @click="activeTab = tab.key"
      >
        {{ tab.title }}<span class="badge badge--neutral">{{ tab.rows.length }}</span>
      </button>
    </div>

    <div class="card table-section">
      <div class="table-section__head">
        <h3 class="table-section__title"><AppIcon name="list" size="sm" /> バッチ一覧（{{ activeTabTitle }}）</h3>
        <span class="table-section__meta">全 {{ visibleRows.length }} 件</span>
      </div>

      <p v-if="loading" class="batch-page__loading">読み込み中...</p>
      <p v-else-if="visibleRows.length === 0" class="batch-page__empty">このグループにバッチはありません。</p>

      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>バッチコード</th>
              <th>種別</th>
              <th>説明</th>
              <th class="align-center">有効</th>
              <th>実行タイミング</th>
              <th>次回実行</th>
              <th>設定ページ</th>
              <th class="align-center">設定</th>
              <th class="align-center">最新状態</th>
              <th>最新実行</th>
              <th class="col-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="entry in visibleRows" :key="entry.row.taskCode" :data-batch-code="entry.row.taskCode">
              <td class="cell-strong">{{ entry.row.taskCode }}</td>
              <td>{{ TYPE_LABELS[entry.row.taskType] }}</td>
              <td>{{ entry.row.description }}</td>
              <td class="align-center">
                <label class="switch" :title="entry.row.canToggleActive ? '有効／無効を切り替える' : '切り替えできません（呼出バッチ）'">
                  <input
                    type="checkbox"
                    :checked="entry.row.active"
                    :disabled="busy || !entry.row.canToggleActive"
                    :aria-label="`${entry.row.taskCode} を有効にする`"
                    @change="toggleActive(entry.row)"
                  >
                  <span class="switch__track" />
                  <span class="switch__thumb" />
                </label>
              </td>
              <td>
                <!-- 設定に基づく**実際の**実行タイミング。スケジュールを取れないときは定義の値へ戻す -->
                <template v-if="entry.schedule">
                  {{ entry.schedule.describe }}
                  <span
                    v-if="entry.schedule.intervalMinutes !== null && entry.schedule.offsetMinutes !== null"
                    class="batch-page__schedule"
                  >ずらし {{ entry.schedule.offsetMinutes }} 分</span>
                </template>
                <template v-else>{{ timingLabel(entry.row) }}</template>
              </td>
              <td class="cell-muted batch-page__next-run">{{ entry.schedule?.nextRunLabel ?? '—' }}</td>
              <td class="cell-muted">{{ entry.row.pageCode ?? '—' }}</td>
              <td class="align-center">
                <span v-if="entry.row.settingsComplete" class="badge badge--success">設定済み</span>
                <span
                  v-else
                  class="badge badge--danger"
                  :title="`${entry.row.pageCode ?? ''} の必須設定が未設定です: ${entry.row.missingSettings.join(', ')}`"
                >設定不足</span>
              </td>
              <td class="align-center">
                <span
                  v-if="entry.row.latestStatus"
                  class="badge"
                  :class="STATUS_BADGE_CLASSES[entry.row.latestStatus]"
                >{{ STATUS_LABELS[entry.row.latestStatus] }}</span>
                <span v-else class="badge badge--neutral">—</span>
              </td>
              <td class="cell-muted">{{ entry.row.latestStartTime ? formatIsoDateTime(entry.row.latestStartTime) : '—' }}</td>
              <td>
                <!-- 種別 C（呼出）は他の処理から呼ばれるバッチなので、ボタンごと出さない
                     （canManualRerun=false。押せない灰色のボタンも残さない） -->
                <button
                  v-if="entry.row.canManualRerun"
                  type="button"
                  class="btn btn--secondary btn--sm"
                  :disabled="busy || !entry.row.canRerun"
                  :title="entry.row.canRerun ? '今すぐ実行する' : '2.1 では未実装です'"
                  @click="rerun(entry.row)"
                >
                  <AppIcon name="play" size="sm" /> 再実行
                </button>
                <span v-else class="cell-muted">—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <p v-if="result" class="batch-page__result" :class="{ 'batch-page__result--failed': result.failed }">
      <AppIcon :name="result.failed ? 'alert' : 'check-circle'" size="sm" />
      <span>
        {{ result.failed ? '再実行に失敗しました。' : '再実行しました。' }}{{ result.message }}
        <span v-if="result.detail" class="batch-page__message" :title="result.detail">{{ result.detail }}</span>
      </span>
    </p>
  </div>
</template>
