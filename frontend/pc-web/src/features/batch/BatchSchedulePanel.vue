<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiError, formatIsoDateTime } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import { fetchBatchSchedule, reloadBatchSchedule, type BatchScheduleResult } from '@/api/batch'
import { scheduleStatusBadgeClass } from '@/features/batch/batchSchedule'
import '@/features/batch/batch.css'

/**
 * 実行スケジュール（読み取り専用）— システム設定ページの最上部に出す。
 *
 * 2.1 は batR03 / batR04 / batL02 / batL03 を**1 つのスケジューラ**（30 秒ごとに設定を確認）で
 * 動かし、実行タイミングは `COM_設定情報` から読む（`ScheduleRuleCatalog`）。
 * ここでは「いまメモリで効いている設定」を見せる:
 *
 *   * 時計（zone = Asia/Tokyo）・設定の版・読み込み時刻
 *   * タスクごとの**実行タイミング**（`describe`）と**次回実行時刻**（`nextRunLabel`）
 *   * 実行時間の例（`examplePoints`）と、実行待ち／実行中の本数
 *   * **設定の状態**（有効／未設定／設定不正。`MISSING` / `INVALID` は**自動実行しない**）
 *   * **保存済み・実行設定への反映待ち**（`pendingRefresh`。理由と次の再試行を目立たせる）
 *
 * **Cron は編集させない**（入力欄を出さない）。有効／無効の切替もしない
 * （唯一の入口はバッチ一覧のスイッチ＝`BAT_バッチコントロール情報`）。
 * 【設定を再読み込み】は DB を直接触ったとき（SQL で設定を直したとき）のための入口で、
 * 設定画面の保存と有効／無効の切替はサーバーが自動で反映する。
 */
const schedule = ref<BatchScheduleResult | null>(null)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const reloadResult = ref<{ failed: boolean; message: string } | null>(null)

/** 反映待ちの案内（`pendingMessage` が空でも定型文を出す）。 */
const pendingText = computed(() => {
  const data = schedule.value
  if (!data?.pendingRefresh) return ''
  const message = String(data.pendingMessage ?? '').trim()
  return message === ''
    ? '保存済み・実行設定への反映待ち（理由不明）。自動で再試行します。'
    : message
})

/** 時計と版（例: 「時計 Asia/Tokyo・設定の版 7・読み込み 2026/9/19 12:34:56」）。 */
const clockLabel = computed(() => {
  const data = schedule.value
  if (!data) return ''
  const parts = [`時計 ${data.zone}`, `設定の版 ${data.version}`]
  const loaded = data.loadedAt ? formatIsoDateTime(data.loadedAt) : ''
  parts.push(loaded === '' ? '読み込み 未読込' : `読み込み ${loaded}`)
  return parts.join('・')
})

/** 実行待ち／実行中の本数。 */
const workerLabel = computed(() => {
  const data = schedule.value
  if (!data) return ''
  return `実行中 ${data.runningWorkers} 本／実行待ち ${data.queuedWorkers} 本`
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const response = await fetchBatchSchedule()
    schedule.value = response.data
  } catch (caught) {
    error.value = caught instanceof ApiError
      ? caught.message
      : '実行スケジュールを取得できませんでした。'
  } finally {
    loading.value = false
  }
}

/** 【設定を再読み込み】。失敗しても 200 で「前の設定のまま動きます」が返る。 */
async function reload(): Promise<void> {
  if (busy.value) return
  busy.value = true
  reloadResult.value = null
  try {
    const response = await reloadBatchSchedule()
    const data = response.data
    schedule.value = data
    reloadResult.value = { failed: !data.refreshPublished, message: data.message }
  } catch (caught) {
    reloadResult.value = {
      failed: true,
      message: caught instanceof ApiError ? caught.message : '実行設定を読み直せませんでした。'
    }
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="card batch-schedule" aria-label="実行スケジュール">
    <div class="batch-schedule__head">
      <h3 class="batch-schedule__title"><AppIcon name="clock" size="sm" /> 実行スケジュール（読み取り専用）</h3>
      <button
        id="scheduleReloadBtn"
        type="button"
        class="btn btn--secondary btn--sm"
        data-testid="schedule-reload"
        :disabled="busy || loading"
        title="DB を直接変更したときに、保存済みの設定を実行スケジュールへ読み直します"
        @click="reload"
      >
        <AppIcon name="rotate" size="sm" /> {{ busy ? '読み直し中…' : '設定を再読み込み' }}
      </button>
    </div>

    <p class="batch-schedule__notice">
      実行タイミングは設定から決まります（この画面では編集できません）。有効／無効は
      <strong>バッチ一覧</strong>のスイッチで切り替えます。
    </p>

    <p v-if="error" class="alert alert--danger">{{ error }}</p>
    <p v-else-if="loading && !schedule" class="batch-schedule__loading">読み込み中...</p>

    <template v-if="schedule">
      <dl class="batch-schedule__meta">
        <div>
          <dt>時計・版</dt>
          <dd>{{ clockLabel }}</dd>
        </div>
        <div>
          <dt>実行の状況</dt>
          <dd>{{ workerLabel }}</dd>
        </div>
        <div v-if="schedule.pendingRefresh">
          <dt>反映待ち</dt>
          <dd>設定は保存済みで、実行設定への反映を待っています。</dd>
        </div>
      </dl>

      <!-- 保存済み・実行設定への反映待ち（理由と次の再試行を目立たせる） -->
      <p
        v-if="schedule.pendingRefresh"
        class="alert alert--warning batch-schedule__pending"
        data-testid="schedule-pending"
        role="status"
      >
        <AppIcon name="alert" size="sm" />
        <span>
          {{ pendingText }}
          <span v-if="schedule.lastRefreshError" class="batch-schedule__reason">
            理由: {{ schedule.lastRefreshError }}
          </span>
          <span v-if="schedule.nextRetryAt" class="batch-schedule__reason">
            次の再試行: {{ formatIsoDateTime(schedule.nextRetryAt) }}
          </span>
        </span>
      </p>

      <p
        v-if="reloadResult"
        class="batch-schedule__result"
        :class="{ 'batch-schedule__result--failed': reloadResult.failed }"
        data-testid="schedule-reload-result"
        role="status"
      >
        <AppIcon :name="reloadResult.failed ? 'alert' : 'check-circle'" size="sm" />
        <span>{{ reloadResult.message }}</span>
      </p>

      <div class="table-wrap">
        <table class="data-table batch-schedule__table">
          <thead>
            <tr>
              <th>バッチ</th>
              <th>設定の状態</th>
              <th class="align-center">有効</th>
              <th>実行タイミング</th>
              <th>次回実行</th>
              <th>実行時間の例</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="task in schedule.tasks" :key="task.taskCode" :data-task-code="task.taskCode">
              <td class="cell-strong">{{ task.taskCode }}</td>
              <td>
                <span class="badge" :class="scheduleStatusBadgeClass(task.status)">{{ task.statusLabel }}</span>
              </td>
              <td class="align-center">
                <!-- 表示だけ（スイッチは置かない）。有効／無効の唯一の正は
                     BAT_バッチコントロール情報 で、切り替える入口はバッチ一覧のスイッチ -->
                {{ task.enabled === null ? '—' : task.enabled ? '有効' : '無効' }}
              </td>
              <td>{{ task.describe }}</td>
              <td>
                {{ task.nextRunLabel }}
                <!-- 適用時刻より前の計画実行点は実行しない。設定を変えた直後に
                     「なぜ今回は走らないのか」が分かるように出す（未変更のときは出さない） -->
                <span
                  v-if="task.configEffectiveFrom"
                  class="batch-schedule__effective"
                  data-testid="task-effective-from"
                >
                  設定の適用: {{ task.configEffectiveFrom }} 以降
                </span>
              </td>
              <td class="cell-muted">{{ task.examplePoints.join(' / ') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>
