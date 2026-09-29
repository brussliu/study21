<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { ApiError, useToast } from '@study21/web-shared'
import {
  activateJpnDetailVersion,
  activateJpnQuestionVersion,
  fetchJpnDetailVersions,
  fetchJpnQuestionVersions,
  type JpnDetailVersion,
  type JpnQuestionVersion
} from '@/api/japanese'
import type { JpnAiStateKey } from '@/features/japanese-word/aiStateLabel'
import { detailVersionCountLabels, detailVersionOrigin } from '@/features/japanese-word/wordMapper'
import {
  generationStateLabel,
  historyTitleOf,
  questionTypeLabel,
  questionTypesOf,
  sectionUsesQuestionVersions
} from '@/features/japanese-word/aiHistory'
import '@/features/japanese/japanese.css'

/**
 * AI 取得の**履歴**（一覧の「取得状態」のタグから開く）。
 *
 * <p>2.0 の英語学習の単語情報管理（`word.jsp`）の「詳細情報取得履歴」と同じ考え方:
 * **AI の取得 1 回 = 1 行**を出し、そのうちの 1 つを【この版を使う】で指定する。
 * 指定しないときは最新の正常終了を使う（2.1 は新しい版が必ず有効で生まれるので、
 * 何もしなければ最新版が使われている）。</p>
 *
 * <p>見る対象は区画で決まる:</p>
 * <ul>
 *   <li>A・B … 詳細の版（{@code JPN_単語詳細情報}。人が直した版も混ざる）</li>
 *   <li>C・D・E … 問題の版（{@code JPN_AI生成履歴情報} の取得 1 回。C は C1 と C2 の 2 つ）</li>
 * </ul>
 */

const props = defineProps<{
  /** 出しているか。 */
  visible: boolean
  /** 対象の語。 */
  wordId: number
  /** どの区画の履歴か（A・B／C／D／E）。 */
  section: JpnAiStateKey
}>()

const emit = defineEmits<{ close: []; activated: [] }>()

const toast = useToast()
const loading = ref(false)
const error = ref('')
const detailVersions = ref<JpnDetailVersion[]>([])
const questionVersions = ref<JpnQuestionVersion[]>([])
/** 切り替え中の版（二重に押せないようにする）。 */
const activating = ref<string | null>(null)

const title = computed(() => historyTitleOf(props.section))
const usesQuestions = computed(() => sectionUsesQuestionVersions(props.section))

/** その区画の問題の版（内容種別ごとに分ける。C は C1 と C2）。 */
const questionGroups = computed(() =>
  questionTypesOf(props.section).map((type) => ({
    type,
    label: questionTypeLabel(type),
    items: questionVersions.value.filter((item) => item.questionType === type)
  }))
)

async function load(): Promise<void> {
  if (props.wordId <= 0) return
  loading.value = true
  error.value = ''
  try {
    if (usesQuestions.value) {
      const response = await fetchJpnQuestionVersions(props.wordId)
      questionVersions.value = response.data.items
      detailVersions.value = []
    } else {
      const response = await fetchJpnDetailVersions(props.wordId)
      detailVersions.value = response.data.items
      questionVersions.value = []
    }
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : '履歴を読み込めませんでした。'
  } finally {
    loading.value = false
  }
}

watch(() => [props.visible, props.wordId, props.section] as const, () => {
  if (props.visible) void load()
}, { immediate: true })

/** 詳細の版を使う（楽観的ロックは**その版の** バージョン）。 */
async function useDetail(version: JpnDetailVersion): Promise<void> {
  activating.value = `detail-${version.detailId}`
  try {
    await activateJpnDetailVersion(props.wordId, version.detailId, version.version)
    toast.success('使用する版を切り替えました。')
    await load()
    emit('activated')
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '版を切り替えられませんでした。')
  } finally {
    activating.value = null
  }
}

/** 問題の版を使う。 */
async function useQuestion(version: JpnQuestionVersion): Promise<void> {
  activating.value = `${version.questionType}-${version.contentVersion}`
  try {
    await activateJpnQuestionVersion(props.wordId, version.questionType, version.contentVersion)
    toast.success('使用する版を切り替えました。')
    await load()
    emit('activated')
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '版を切り替えられませんでした。')
  } finally {
    activating.value = null
  }
}

/** 日時は「2026/10/01 09:00」の形にする（秒は出さない）。 */
function formatAt(value: string | null): string {
  if (value === null || value === '') return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (part: number): string => String(part).padStart(2, '0')
  return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
</script>

<template>
  <div v-if="props.visible" class="overlay jp-history-overlay" data-jp-history-dialog>
    <section class="dialog dialog--wide jp-history" role="dialog" aria-modal="true" aria-labelledby="jpHistoryTitle">
      <div class="dialog__head">
        <h2 id="jpHistoryTitle" class="dialog__title">
          <AppIcon name="clock" size="sm" /> {{ title }}
        </h2>
        <button
          type="button" class="dialog__close" aria-label="閉じる" data-jp-history-close
          @click="emit('close')"
        >
          <AppIcon name="x" size="sm" />
        </button>
      </div>

      <div class="dialog__body jp-history__body">
        <p class="jp-hint">
          取得 1 回 = 1 版です。【この版を使う】で、その版に切り替えられます
          （指定しないときは最新の版を使います）。
        </p>

        <p v-if="loading" class="jp-hint" data-jp-history-loading>履歴を読み込んでいます…</p>
        <p v-else-if="error !== ''" class="alert alert--danger" data-jp-history-error>{{ error }}</p>

        <!-- A・B: 詳細の版（人が直した版も混ざる） -->
        <template v-else-if="!usesQuestions">
          <p v-if="detailVersions.length === 0" class="jp-hint" data-jp-history-empty>
            まだ取得の履歴がありません。
          </p>
          <ol v-else class="jp-version__list">
            <li
              v-for="version in detailVersions" :key="version.detailId"
              class="jp-version__item" :class="{ 'is-current': version.active }" data-jp-history-item
            >
              <div class="jp-version__row">
                <span class="jp-version__no" data-jp-history-version>版 {{ version.contentVersion }}</span>
                <span v-if="version.active" class="badge badge--success" data-jp-history-active>使用中</span>
                <span class="badge badge--neutral">{{ version.manual ? '人が作成' : 'AI が作成' }}</span>
                <span class="jp-version__origin">{{ detailVersionOrigin(version) }}</span>
                <span class="jp-version__at">取得 {{ formatAt(version.fetchedAt) }}</span>
                <button
                  v-if="!version.active" type="button" class="btn btn--secondary btn--sm jp-version__use"
                  :disabled="activating !== null" data-jp-history-use
                  @click="useDetail(version)"
                >
                  この版を使う
                </button>
              </div>
              <ul class="jp-version__counts">
                <li v-for="entry in detailVersionCountLabels(version)" :key="entry.label">
                  <span class="jp-version__count-label">{{ entry.label }}</span>
                  <span class="jp-version__count-value">{{ entry.count }}</span>
                </li>
              </ul>
            </li>
          </ol>
        </template>

        <!-- C/D/E: 問題の版（AI の取得 1 回 = 1 版。C は C1 と C2 の 2 つ） -->
        <template v-else>
          <section
            v-for="group in questionGroups" :key="group.type" class="jp-history__group"
            :data-jp-history-group="group.type"
          >
            <h3 class="jp-history__group-title">{{ group.label }}</h3>
            <p v-if="group.items.length === 0" class="jp-hint" data-jp-history-empty>
              まだ取得の履歴がありません。
            </p>
            <table v-else class="data-table jp-history__table">
              <thead>
                <tr>
                  <th>版</th>
                  <th>状態</th>
                  <th>モデル</th>
                  <th>問題数</th>
                  <th>取得日時</th>
                  <th class="col-actions">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in group.items" :key="`${item.questionType}-${item.contentVersion}`" data-jp-history-item>
                  <td class="cell-strong" data-jp-history-version>版 {{ item.contentVersion }}</td>
                  <td>
                    <span v-if="item.active" class="badge badge--success" data-jp-history-active>使用中</span>
                    <span v-else class="badge badge--neutral">{{ generationStateLabel(item.generationState) }}</span>
                  </td>
                  <td class="cell-muted">{{ item.aiModel ?? '—' }}</td>
                  <td>{{ item.questionCount }}</td>
                  <td class="cell-muted">{{ formatAt(item.startedAt) }}</td>
                  <td class="row-actions">
                    <button
                      v-if="!item.active" type="button" class="btn btn--secondary btn--sm"
                      :disabled="activating !== null || item.questionCount === 0"
                      :title="item.questionCount === 0 ? 'この版には問題がありません（取得に失敗した版）' : 'この版を使う'"
                      data-jp-history-use @click="useQuestion(item)"
                    >
                      この版を使う
                    </button>
                    <span v-else class="cell-muted">—</span>
                  </td>
                </tr>
              </tbody>
            </table>
            <!-- 失敗した取得は理由も出す（2.0 の履歴と同じ） -->
            <p
              v-for="item in group.items.filter((row) => row.errorMessage)"
              :key="`error-${item.contentVersion}`" class="jp-hint" data-jp-history-failure
            >
              版 {{ item.contentVersion }}: {{ item.errorMessage }}
            </p>
          </section>
        </template>
      </div>
    </section>
  </div>
</template>
