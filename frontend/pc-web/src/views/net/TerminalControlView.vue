<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiError, formatIsoDateTime, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  changeTerminalModes,
  createTerminal,
  deleteTerminal,
  searchTerminals,
  updateTerminal,
  type TerminalModeCode,
  type TerminalRow,
  type TerminalSaveRequest
} from '@/api/net'
import {
  fetchBrowserExtensionRegistration,
  type BrowserExtensionDevice
} from '@/api/browserExtension'
import { browserLabel } from '@/features/browserext/browserext'
import { MODE_BADGE_CLASSES, MODE_LABELS, STATUS_LABELS, optionsOf } from '@/features/net/netLabels'
import '@/features/net/net.css'
import '@/features/browserext/browserext.css'

/**
 * 端末コントロール（2.0 の terminal_control.jsp 相当）。
 * 2.0 と同じく**保護者のみ**利用できる（サーバー側でも 403 で拒否する）。
 *
 * ・一覧（端末 5 台程度なので検索条件は置かない）
 * ・【新規】で端末を登録、【操作】列の鉛筆アイコンで編集
 * ・モードの切替は **右上の【一括適用】**（選択した端末にまとめて適用）だけ
 *
 * タブは 2 つ:
 *   * 端末コントロール … IP で管理する端末（プロキシが使う。2.0 から）
 *   * ブラウザ端末 … ブラウザ拡張が接続コードで登録した端末（`NET_ブラウザ端末情報`）
 *
 * 2.0 はモード変更後に batL01（プロキシ再起動）を起動していたが、2.1 の batL01 は
 * 未実装のため画面側からは何も起動しない（DB 更新のみ）。
 */
const toast = useToast()

const loading = ref(false)
const busy = ref(false)
const error = ref('')
const forbidden = ref(false)
const rows = ref<TerminalRow[]>([])
const selectedIds = ref<number[]>([])
const bulkMode = ref<TerminalModeCode>('T')

const modeOptions = optionsOf(MODE_LABELS)
const statusOptions = optionsOf(STATUS_LABELS)

const allSelected = computed(() => rows.value.length > 0 && selectedIds.value.length === rows.value.length)
const hasSelection = computed(() => selectedIds.value.length > 0)

/* ---------- 新規／編集ダイアログ ---------- */
const dialogOpen = ref(false)
/** 編集中の端末（null なら新規）。 */
const editingId = ref<number | null>(null)
const editingVersion = ref<number | null>(null)
const form = reactive({
  ipAddress: '',
  terminalName: '',
  terminalMode: 'T' as TerminalModeCode,
  status: '1' as '0' | '1',
  note: ''
})
const fieldErrors = reactive<Record<string, string>>({})

/**
 * 端末を削除する（物理削除）。
 * 2.0 に無かった操作なので、サイト管理と同じく確認してから消す。
 */
async function remove(row: TerminalRow): Promise<void> {
  if (busy.value) return
  if (!window.confirm(`端末「${row.terminalName}」（${row.ipAddress}）を削除します。よろしいですか？`)) return
  busy.value = true
  try {
    const response = await deleteTerminal(row.terminalId)
    toast.success(response.data.message)
    await load()
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '削除できませんでした。')
  } finally {
    busy.value = false
  }
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    // 端末は台数が少ないので、絞り込みはせず全件（最大 200 台）を出す
    const response = await searchTerminals({ size: 200 })
    rows.value = response.data.items
    selectedIds.value = []
    forbidden.value = false
  } catch (caught) {
    if (caught instanceof ApiError && caught.status === 403) {
      forbidden.value = true
      error.value = caught.message || '端末コントロールは保護者のみ利用できます。'
    } else {
      forbidden.value = false
      error.value = caught instanceof ApiError ? caught.message : '端末一覧を取得できませんでした。'
    }
  } finally {
    loading.value = false
  }
}

function toggleAll(): void {
  selectedIds.value = allSelected.value ? [] : rows.value.map((row) => row.terminalId)
}

function toggleRow(terminalId: number): void {
  selectedIds.value = selectedIds.value.includes(terminalId)
    ? selectedIds.value.filter((id) => id !== terminalId)
    : [...selectedIds.value, terminalId]
}

/** 選択した端末のモードを一括で変更する（切替はこのボタンだけ）。 */
async function applyBulk(): Promise<void> {
  if (busy.value) return
  if (!hasSelection.value) {
    toast.warning('更新対象の端末を選択してください。')
    return
  }
  busy.value = true
  try {
    const response = await changeTerminalModes([...selectedIds.value], bulkMode.value)
    toast.success(`${response.data.message}（${response.data.updatedCount} 件）`)
    await load()
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '端末ステータスを一括更新できませんでした。')
  } finally {
    busy.value = false
  }
}

/* ---------- 新規／編集 ---------- */

function clearFieldErrors(): void {
  for (const key of Object.keys(fieldErrors)) {
    delete fieldErrors[key]
  }
}

function openCreate(): void {
  editingId.value = null
  editingVersion.value = null
  form.ipAddress = ''
  form.terminalName = ''
  form.terminalMode = 'T'
  form.status = '1'
  form.note = ''
  clearFieldErrors()
  dialogOpen.value = true
}

function openEdit(row: TerminalRow): void {
  editingId.value = row.terminalId
  editingVersion.value = row.version
  form.ipAddress = row.ipAddress
  form.terminalName = row.terminalName
  form.terminalMode = row.terminalMode
  form.status = row.status
  form.note = row.note ?? ''
  clearFieldErrors()
  dialogOpen.value = true
}

function payload(): TerminalSaveRequest {
  return {
    ipAddress: form.ipAddress.trim(),
    terminalName: form.terminalName.trim(),
    terminalMode: form.terminalMode,
    status: form.status,
    note: form.note.trim() === '' ? null : form.note.trim(),
    version: editingVersion.value
  }
}

async function save(): Promise<void> {
  if (busy.value) return
  clearFieldErrors()
  if (form.ipAddress.trim() === '') {
    fieldErrors.ipAddress = 'IPアドレスを入力してください。'
    return
  }
  if (form.terminalName.trim() === '') {
    fieldErrors.terminalName = '端末名称を入力してください。'
    return
  }
  busy.value = true
  try {
    const body = payload()
    const response = editingId.value === null
      ? await createTerminal(body)
      : await updateTerminal(editingId.value, body)
    toast.success(response.data.message)
    dialogOpen.value = false
    await load()
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '端末を保存できませんでした。')
  } finally {
    busy.value = false
  }
}

/** 更新者（アカウント名。エージェント等の場合はコード）を表示する。 */
function updatedByText(row: TerminalRow): string {
  return row.updatedByName ?? row.updatedByCode ?? '—'
}

/* ---------- ブラウザ端末（ブラウザ拡張が接続コードで登録した端末）---------- */
const activeTab = ref<'terminal' | 'browser'>('terminal')
const browserLoaded = ref(false)
const browserLoading = ref(false)
const browserError = ref('')
const browserDevices = ref<BrowserExtensionDevice[]>([])

/**
 * ブラウザ拡張の端末一覧を読む（タブを開いたときだけ）。
 * IP で管理する端末コントロールとは別物（拡張が採番した端末識別子で管理する）。
 */
async function loadBrowserDevices(): Promise<void> {
  browserLoading.value = true
  browserError.value = ''
  try {
    const response = await fetchBrowserExtensionRegistration()
    browserDevices.value = response.data?.devices ?? []
    browserLoaded.value = true
  } catch (caught) {
    browserError.value = caught instanceof ApiError ? caught.message : 'ブラウザ端末の一覧を取得できませんでした。'
  } finally {
    browserLoading.value = false
  }
}

function switchTab(tab: 'terminal' | 'browser'): void {
  activeTab.value = tab
  if (tab === 'browser' && !browserLoaded.value) {
    void loadBrowserDevices()
  }
}

onMounted(load)
</script>

<template>
  <div class="net-page">
    <div v-if="forbidden" class="alert alert--danger">
      <AppIcon name="info" size="sm" />
      <div class="alert__body">
        <div class="alert__title">アクセス権限がありません</div>
        {{ error }}
      </div>
    </div>

    <template v-else>
      <div class="tabs" role="tablist">
        <button
          type="button" class="tabs__tab" :class="{ 'is-active': activeTab === 'terminal' }"
          role="tab" :aria-selected="activeTab === 'terminal'" data-tab="terminal"
          @click="switchTab('terminal')"
        >
          端末コントロール
        </button>
        <button
          type="button" class="tabs__tab" :class="{ 'is-active': activeTab === 'browser' }"
          role="tab" :aria-selected="activeTab === 'browser'" data-tab="browser"
          @click="switchTab('browser')"
        >
          ブラウザ端末
        </button>
      </div>

      <div v-if="activeTab === 'terminal'" class="card">
        <div class="card__header">
          <h2 class="card__title"><AppIcon name="list" size="sm" /> 端末コントロール一覧</h2>
          <div class="net-toolbar">
            <!--
              モードの切替はこの【一括適用】だけ（行ごとの切替は置かない）。
              行ごとに変えたいときは 1 台だけ選んで適用する。
            -->
            <span class="cell-muted">選択端末を一括切替：</span>
            <select v-model="bulkMode" class="select net-toolbar__mode" aria-label="適用する端末ステータス">
              <option v-for="option in modeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <button
              type="button" class="btn btn--secondary btn--sm" data-action="bulk-apply"
              :disabled="busy" @click="applyBulk"
            >
              <AppIcon name="check" size="sm" /> 一括適用
            </button>
            <button type="button" class="btn btn--primary btn--sm" data-action="create-terminal" @click="openCreate">
              <AppIcon name="plus" size="sm" /> 新規
            </button>
          </div>
        </div>

        <div class="card__body">
          <p v-if="error" class="alert alert--danger">{{ error }}</p>
          <p v-else-if="loading" class="net-page__loading">読み込んでいます...</p>

          <div v-else class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th class="col-check">
                    <input
                      type="checkbox" :checked="allSelected" aria-label="すべて選択"
                      @change="toggleAll"
                    />
                  </th>
                  <th class="col-actions">操作</th>
                  <th>IPアドレス</th>
                  <th>端末名称</th>
                  <th>端末ステータス</th>
                  <th>状態</th>
                  <th>備考</th>
                  <th>最終更新日時</th>
                  <th>更新者</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="rows.length === 0">
                  <td colspan="9" class="net-page__empty">端末が登録されていません。</td>
                </tr>
                <tr v-for="row in rows" :key="row.terminalId" :data-terminal-id="row.terminalId">
                  <td class="col-check align-center">
                    <input
                      type="checkbox" :checked="selectedIds.includes(row.terminalId)"
                      :aria-label="`${row.terminalName} を選択`"
                      @change="toggleRow(row.terminalId)"
                    />
                  </td>
                  <td class="row-actions">
                    <button
                      type="button" class="btn btn--icon btn--sm" title="編集" aria-label="編集"
                      :data-edit-terminal="row.terminalId" @click="openEdit(row)"
                    >
                      <AppIcon name="edit" size="sm" class="icon--edit" />
                    </button>
                    <button
                      type="button" class="btn btn--icon btn--sm" title="削除" aria-label="削除"
                      :disabled="busy" :data-delete-terminal="row.terminalId" @click="remove(row)"
                    >
                      <AppIcon name="trash" size="sm" class="icon--danger" />
                    </button>
                  </td>
                  <td class="cell-muted">{{ row.ipAddress }}</td>
                  <td class="cell-strong">{{ row.terminalName }}</td>
                  <td>
                    <span class="badge" :class="MODE_BADGE_CLASSES[row.terminalMode]">
                      {{ MODE_LABELS[row.terminalMode] }}
                    </span>
                  </td>
                  <!-- 状態（有効 / 無効）。無効にすると IP を別の端末で再利用できる -->
                  <td>
                    <span
                      class="badge" :class="row.status === '1' ? 'badge--success' : 'badge--danger'"
                      :data-terminal-status="row.status"
                    >{{ STATUS_LABELS[row.status] }}</span>
                  </td>
                  <td class="cell-muted">{{ row.note ?? '—' }}</td>
                  <td class="cell-muted">{{ formatIsoDateTime(row.updatedAt ?? '') }}</td>
                  <td class="cell-muted">{{ updatedByText(row) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- ブラウザ拡張が登録した端末（接続コードで認証したブラウザ） -->
      <div v-else class="card" data-browser-devices>
        <div class="card__header">
          <h2 class="card__title"><AppIcon name="list" size="sm" /> ブラウザ端末一覧</h2>
        </div>
        <div class="card__body">
          <p v-if="browserError" class="alert alert--danger">{{ browserError }}</p>
          <p v-else-if="browserLoading" class="net-page__loading">読み込んでいます...</p>

          <div v-else class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th>端末名称</th>
                  <th>端末ID</th>
                  <th>ブラウザ</th>
                  <th>拡張</th>
                  <th>最終心拍</th>
                  <th>最終送信</th>
                  <th class="align-center">状態</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="browserDevices.length === 0">
                  <td colspan="7" class="net-page__empty">
                    接続している端末はありません。ブラウザ拡張に接続コードを設定すると、ここに表示されます。
                  </td>
                </tr>
                <tr v-for="device in browserDevices" :key="device.registrationId" :data-browser-device="device.deviceId">
                  <td class="bx-device-name">{{ device.deviceName ?? '（名称未設定）' }}</td>
                  <td class="bx-device-id">{{ device.deviceId }}</td>
                  <td>{{ browserLabel(device) }}</td>
                  <td class="cell-muted">{{ device.extensionVersion ?? '—' }}</td>
                  <td class="cell-muted">{{ device.lastHeartbeatAt ? formatIsoDateTime(device.lastHeartbeatAt) : '—' }}</td>
                  <td class="cell-muted">{{ device.lastSentAt ? formatIsoDateTime(device.lastSentAt) : '—' }}</td>
                  <td class="align-center">
                    <span class="badge" :class="device.online ? 'badge--success' : 'badge--neutral'">
                      {{ device.online ? '接続中' : 'オフライン' }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <p class="bx-device-note">
            ブラウザ拡張が接続コードで登録した端末です（IP で管理する端末コントロールとは別）。
            心拍が 10 分以内なら「接続中」。接続コードの発行・再発行は
            インターネット利用履歴の「Web閲覧履歴」から行えます。
          </p>
        </div>
      </div>
    </template>

    <!-- 新規／編集ダイアログ -->
    <div v-if="dialogOpen" class="overlay">
      <section class="dialog dialog--md" role="dialog" aria-modal="true" aria-labelledby="netTerminalDialogTitle">
        <div class="dialog__head">
          <h2 id="netTerminalDialogTitle" class="dialog__title" data-testid="terminal-dialog-title">
            <AppIcon name="monitor" size="sm" /> {{ editingId === null ? '端末新規登録' : '端末編集' }}
          </h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="dialogOpen = false">
            <AppIcon name="x" size="sm" />
          </button>
        </div>
        <div class="dialog__body">
          <div class="form-grid">
            <div class="field">
              <label class="field__label" for="netTerminalIp">IPアドレス<span class="net-required">必須</span></label>
              <input
                id="netTerminalIp" v-model="form.ipAddress" class="input" type="text" maxlength="45"
                placeholder="例：192.168.0.10" :class="{ 'is-invalid': fieldErrors.ipAddress !== undefined }"
              />
              <p v-if="fieldErrors.ipAddress" class="field__error">{{ fieldErrors.ipAddress }}</p>
            </div>
            <div class="field">
              <label class="field__label" for="netTerminalName">端末名称<span class="net-required">必須</span></label>
              <input
                id="netTerminalName" v-model="form.terminalName" class="input" type="text" maxlength="100"
                placeholder="例：リビングのPC" :class="{ 'is-invalid': fieldErrors.terminalName !== undefined }"
              />
              <p v-if="fieldErrors.terminalName" class="field__error">{{ fieldErrors.terminalName }}</p>
            </div>
            <div class="field">
              <label class="field__label" for="netTerminalMode">端末ステータス</label>
              <select id="netTerminalMode" v-model="form.terminalMode" class="select">
                <option v-for="option in modeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </div>
            <div class="field">
              <label class="field__label" for="netTerminalStatus">状態</label>
              <select id="netTerminalStatus" v-model="form.status" class="select">
                <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </div>
            <div class="field field--wide">
              <label class="field__label" for="netTerminalNote">備考</label>
              <textarea id="netTerminalNote" v-model="form.note" class="textarea" rows="2" maxlength="200"></textarea>
            </div>
          </div>
          <p class="field__hint">
            有効（{{ STATUS_LABELS['1'] }}）な端末の IP アドレスは重複できません。
            同じ IP を別の端末で使うときは、先に古い端末を「{{ STATUS_LABELS['0'] }}」にしてください。
          </p>
        </div>
        <div class="dialog__foot">
          <button type="button" class="btn btn--secondary" @click="dialogOpen = false">キャンセル</button>
          <button
            type="button" class="btn btn--primary" :disabled="busy" data-testid="terminal-save" @click="save"
          >
            <AppIcon name="check" size="sm" /> {{ busy ? '保存中...' : '保存' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>
