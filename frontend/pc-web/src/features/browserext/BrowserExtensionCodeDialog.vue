<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ApiError } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  fetchBrowserExtensionRegistration,
  issueBrowserExtensionToken,
  reissueBrowserExtensionToken,
  type BrowserExtensionRegistration
} from '@/api/browserExtension'
import { copyToClipboard, formatConnectionToken } from './browserext'
import './browserext.css'

/**
 * 接続コードのダイアログ（インターネット利用履歴 → Web閲覧履歴）。
 *
 * 2.0 は拡張が送ってくる userId をそのまま信じていた（認証なし）。
 * 2.1 はここで発行する接続コードを拡張の設定画面に入れてもらい、
 * そのコードから持ち主を決める。再発行すると古いコードは使えなくなる。
 *
 * 接続してきた端末の一覧は 端末コントロール の「ブラウザ端末」タブに置いてある
 * （このダイアログはコードの発行・確認だけに絞る）。
 */
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; changed: [] }>()

const loading = ref(false)
const error = ref('')
const registration = ref<BrowserExtensionRegistration | null>(null)
const copied = ref(false)
let copiedTimer: number | undefined

const issued = computed(() => registration.value?.issued === true)
const token = computed(() => registration.value?.token ?? '')
const deviceCount = computed(() => registration.value?.deviceCount ?? 0)
const onlineCount = computed(() => registration.value?.onlineCount ?? 0)

/** API の応答を画面がそのまま使える形に整える（欠けた項目があっても描画を止めない）。 */
function normalize(data: BrowserExtensionRegistration | null | undefined): BrowserExtensionRegistration {
  return {
    issued: data?.issued === true,
    token: data?.token ?? null,
    deviceCount: data?.deviceCount ?? 0,
    onlineCount: data?.onlineCount ?? 0,
    devices: data?.devices ?? [],
    message: data?.message ?? null
  }
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const response = await fetchBrowserExtensionRegistration()
    registration.value = normalize(response.data)
  } catch (caught) {
    registration.value = null
    error.value = caught instanceof ApiError ? caught.message : '接続の状態を取得できませんでした。'
  } finally {
    loading.value = false
  }
}

async function issue(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const response = await issueBrowserExtensionToken()
    registration.value = normalize(response.data)
    emit('changed')
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : '接続コードを発行できませんでした。'
  } finally {
    loading.value = false
  }
}

async function reissue(): Promise<void> {
  if (!window.confirm('接続コードを再発行すると、いま設定している拡張機能は使えなくなります。よろしいですか？')) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const response = await reissueBrowserExtensionToken()
    registration.value = normalize(response.data)
    emit('changed')
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : '接続コードを再発行できませんでした。'
  } finally {
    loading.value = false
  }
}

async function copy(): Promise<void> {
  if (token.value === '') return
  copied.value = await copyToClipboard(token.value)
  if (copiedTimer !== undefined) window.clearTimeout(copiedTimer)
  copiedTimer = window.setTimeout(() => {
    copied.value = false
  }, 3000)
}

// 開くたびに最新の状態を読む（発行済みかどうかは変わり得る）
watch(() => props.open, (open) => {
  if (open) {
    copied.value = false
    void load()
  }
}, { immediate: true })
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="overlay">
      <section
        class="dialog dialog--sm" role="dialog" aria-modal="true"
        aria-labelledby="bxCodeDialogTitle" data-ext-code-dialog
      >
        <div class="dialog__head">
          <h2 id="bxCodeDialogTitle" class="dialog__title">
            <AppIcon name="key" size="sm" /> 接続コード
          </h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="emit('close')">
            <AppIcon name="x" size="sm" />
          </button>
        </div>

        <div class="dialog__body">
          <div class="bx-code">
            <p class="bx-code__lead">
              ブラウザ拡張の設定画面に入力するコードです。このコードを知っている拡張機能だけが、
              あなたの Web閲覧履歴 を送れます。
            </p>

            <p v-if="error" class="alert alert--danger">{{ error }}</p>

            <template v-if="issued">
              <div class="bx-code__box">
                <code class="bx-code__value" data-ext-token>{{ formatConnectionToken(token) }}</code>
              </div>
              <div class="bx-code__box">
                <button type="button" class="btn btn--secondary btn--sm" data-ext-copy @click="copy">
                  <AppIcon name="copy" size="sm" /> {{ copied ? 'コピーしました' : 'コピー' }}
                </button>
                <button
                  type="button" class="btn btn--secondary btn--sm" :disabled="loading"
                  data-ext-reissue @click="reissue"
                >
                  <AppIcon name="rotate" size="sm" /> 再発行
                </button>
              </div>
              <div class="bx-code__status">
                <span class="badge" :class="onlineCount > 0 ? 'badge--success' : 'badge--neutral'">
                  {{ onlineCount > 0 ? '接続中' : '未接続' }}
                </span>
                <span data-ext-device-count>接続中 {{ onlineCount }} 台 / 登録 {{ deviceCount }} 台</span>
              </div>
              <p class="bx-code__hint">
                接続してきた端末の一覧は「端末コントロール」の「ブラウザ端末」タブで確認できます。
                コードを入れ直すときは、拡張の「設定」画面で上書きして保存してください。
              </p>
            </template>

            <template v-else>
              <p class="bx-code__hint">
                接続コードはまだ発行されていません。発行すると、このコードを知っている拡張機能だけが履歴を送れます。
              </p>
              <div>
                <button
                  type="button" class="btn btn--primary" :disabled="loading"
                  data-ext-issue @click="issue"
                >
                  <AppIcon name="plus" size="sm" /> 接続コードを発行
                </button>
              </div>
            </template>
          </div>
        </div>

        <div class="dialog__foot">
          <button type="button" class="btn btn--secondary" @click="emit('close')">閉じる</button>
        </div>
      </section>
    </div>
  </Teleport>
</template>
