<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  classroomAudioUrl,
  deleteClassroomRecord,
  fetchClassroomRecord,
  retryClassroomAssembly,
  orderClassroomSegments,
  segmentSpeakerOf,
  type ClassroomRecordDetail
} from '@/api/classroom'
import {
  CLASSROOM_QUERY,
  durationLabel,
  formatSegmentTime,
  languageModeLabel,
  noteSections,
  presetName,
  statusBadgeClass
} from '@/features/classroom/classroom'
import { loadRecordAudioMode, type ClassroomAudioMode } from '@/features/classroom/audio-graph'
import '@/features/classroom/classroom.css'

/**
 * 授業録音 / AI 授業記録 — 授業詳細（授業終了後の整理結果）。
 *
 * 最終整理結果（テーマ／学習内容／先生の重点／宿題）・転写全文・元音声（プレーヤー）を出す。
 * データは `GET /api/user/classroom/{id}`、音声は `GET /api/user/classroom/{id}/audio`
 * （`<audio>` がそのまま読む。サーバーは Range/206 に対応）。
 *
 * 最終まとめ（batC62）は終了直後はまだ生成中なので、ノートが PENDING / GENERATING の間は
 * 2 秒間隔で取り直して、できたら表示する（AI 生図と同じ「送信 → ポーリング」の契約）。
 */
const route = useRoute()
const router = useRouter()
const toast = useToast()

const area = computed(() => route.path.split('/')[1] ?? 'student')

/** 授業記録の ID（数値のときだけ API につながる）。 */
const recordId = computed<number | null>(() => {
  const raw = route.params.id
  const value = Array.isArray(raw) ? raw[0] : raw
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
})

const detail = ref<ClassroomRecordDetail | null>(null)
const loading = ref(false)
const error = ref('')
const deleting = ref(false)

/** 新しい授業から渡された内容（API の値が取れたらそちらを優先する）。 */
const queryName = computed(() => {
  const value = route.query[CLASSROOM_QUERY.name]
  return typeof value === 'string' && value !== '' ? value : '（授業名未指定）'
})
const subject = computed(() => {
  const value = route.query[CLASSROOM_QUERY.subject]
  return typeof value === 'string' && value !== '' ? value : '（未指定）'
})
const queryLanguageMode = computed(() => {
  const raw = route.query[CLASSROOM_QUERY.languageMode]
  return languageModeLabel(typeof raw === 'string' ? raw : undefined)
})
const queryPreset = computed(() => {
  const raw = route.query[CLASSROOM_QUERY.preset]
  return presetName(typeof raw === 'string' ? raw : undefined)
})

const lessonName = computed(() => detail.value?.title ?? queryName.value)
const languageMode = computed(() => (
  detail.value === null ? queryLanguageMode.value : languageModeLabel(detail.value.languageMode)
))
const preset = computed(() => {
  const name = detail.value?.presetName
  return name === null || name === undefined || name === '' ? queryPreset.value : name
})
const statusLabel = computed(() => detail.value?.statusLabel ?? '（状態不明）')

/** 最終まとめの JSON（無ければ最後の FINAL ノートの内容を使う）。 */
const summaryJson = computed(() => {
  const current = detail.value
  if (current === null) return null
  if (current.summaryJson !== null && current.summaryJson !== '') return current.summaryJson
  const final = [...current.notes].reverse().find((note) => note.kind === 'FINAL')
  return final?.noteJson ?? null
})

const sections = computed(() => noteSections(summaryJson.value))
const hasSummary = computed(() => sections.value.some((section) => section.body !== ''))
/** まだ AI が作っている途中か。 */
const pending = computed(() => (detail.value?.notes ?? []).some(
  (note) => note.status === 'PENDING' || note.status === 'GENERATING'
))
/**
 * 転写全文（**実際の発言開始時刻**で並べる）。
 *
 * <p>サーバーが返す順（連番＝到着順）ではなく、音声クロックの先頭オフセットで並べる
 * （録音中の画面と同じ規則＝遅れて届いた文も正しい位置に入る）。並べ替えで行の中身
 * （**話者と文**）は入れ替えない。</p>
 */
const segments = computed(() => orderClassroomSegments(detail.value?.segments ?? []))

/**
 * 書き起こしが 1 件も無いまま終了したか。
 * サーバーはこのとき最終まとめ（FINAL ノート）を作らず、終了 API の `notice` で理由を返す
 * （`ClassroomServiceImpl.end()`）。画面を開き直しても分かるように、**データから同じ判断**をして
 * 同じ日本語の案内を出す（詳細・転写・宿題の欄が空の理由を隠さない）。
 */
const NO_TRANSCRIPT_NOTICE = '書き起こしが無かったため、最終まとめは作成しませんでした。'
const noTranscript = computed(() => (
  detail.value !== null
  && detail.value.status !== 'RECORDING'
  && segments.value.length === 0
  && !(detail.value.notes ?? []).some((note) => note.kind === 'FINAL')
))

/**
 * **その記録の音源の設定**（録音中の画面が録音を始めたときに残した値。無ければ null）。
 *
 * <p>話者はこれで決める（録音中の画面と同じ規則）。**開いている URL の query は見ない**:
 * query は録音中の画面のもので、一覧・履歴・ブックマークから開いたときには残っていないか、
 * **別の記録の値**になっている（開き方で話者が変わる＝嘘の表示になる）。
 * 記録の設定が分からないときは、サーバーが記録した話者をそのまま出す（分からないことを
 * 勝手に決めない）。</p>
 */
const configuredAudioMode = computed<ClassroomAudioMode | null>(() => {
  const id = recordId.value
  return id === null ? null : loadRecordAudioMode(id)
})

/** 転写 1 行の話者ラベル（録音中の画面と同じ規則＝**その記録の音源の設定**で決める）。 */
function speakerLabelOf(segment: ClassroomRecordDetail['segments'][number]): string {
  return segmentSpeakerOf(configuredAudioMode.value, segment)
}

/** 整理結果がまだ無いときの案内（日本語）。 */
const emptyNote = computed(() => {
  if (noTranscript.value) return NO_TRANSCRIPT_NOTICE
  return pending.value
    ? 'AI が最終まとめを作っています。しばらくすると、ここに出ます。'
    : '（整理結果はまだありません）'
})
/** 段落ごとの表示（まとめが出来ていて、その段落だけ空なら「（なし）」）。 */
function sectionBody(body: string): string {
  if (body !== '') return body
  return hasSummary.value ? '（なし）' : emptyNote.value
}

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

/** 1 回だけ詳細を取りに行く。 */
async function refresh(): Promise<void> {
  const id = recordId.value
  if (id === null) return
  try {
    const response = await fetchClassroomRecord(id)
    detail.value = response.data
    error.value = ''
    if (!(response.data.notes ?? []).some(
      (note) => note.status === 'PENDING' || note.status === 'GENERATING')) {
      stopPolling()
    }
  } catch (caught) {
    error.value = messageOf(caught, '授業の記録を取得できませんでした。')
  }
}

/** 音声の URL（version を付けて、削除・差し替え後に古い音声を使い回さない）。 */
const audioUrl = computed(() => {
  const current = detail.value
  return current === null || !current.hasAudio ? '' : classroomAudioUrl(current.recordId, current.version)
})
/**
 * 音声を読めなかったか。
 * 保存期間を過ぎた（batR02 の掃除）・実体だけ消えた場合、配信 API は 404
 * 「録音が見つかりません。」を返す。`<audio>` のエラーで受け止め、**画面は壊さず**案内に切り替える。
 */
const audioFailed = ref(false)

function onAudioError(): void {
  audioFailed.value = true
}
/** プレーヤーを出すか（実体があると記録が言っていて、まだ読めていないとき）。 */
const showAudioPlayer = computed(() => audioUrl.value !== '' && !audioFailed.value)
/** 結合（再生用の 1 本）の状態（サーバーが判断したもの）。 */
const assembly = computed(() => detail.value?.assembly ?? null)

/**
 * 音声の欄に出す案内（日本語）。
 *
 * <p>**「音声が保存できているか」と「再生用の 1 本ができているか」を分けて**出す:
 * 結合に失敗していても分塊は残っているので、「音は残っている・あとで作り直せる」と言える。</p>
 */
const audioNote = computed(() => {
  if (audioFailed.value) return '録音の音声を読み込めませんでした（保存期間を過ぎた可能性があります）。'
  if (audioUrl.value === '') return 'この授業の音声はありません（保存期間を過ぎると消えます）。'
  const state = assembly.value
  if (state === null || state === undefined) return '録音した音声をそのまま再生できます。'
  if (state.state === 'READY' && state.complete) {
    return '録音した音声をそのまま再生できます（音声は保存されています）。'
  }
  if (state.state === 'FAILED' || state.state === 'INCOMPLETE') {
    return `音声は保存されています（${state.storedChunks} 件）。`
      + '再生用の音声はまだ作れていません（作り直せます）。'
  }
  return `音声は保存されています（${state.storedChunks} 件）。再生用の音声を作成中です。`
})

/** 再生用の 1 本を作り直せるか（作れなかったときだけ出す）。 */
const canRetryAssembly = computed(() => {
  const state = assembly.value
  return state !== null && state !== undefined
    && (state.state === 'FAILED' || state.state === 'INCOMPLETE')
})

/** 作り直しの実行中か（連打で二重に走らせない）。 */
const retryingAssembly = ref(false)

/** 作り直しの結果（日本語。空なら出さない）。 */
const assemblyNotice = ref('')

/** 再生用の 1 本を作り直す（分塊は消さない）。 */
async function retryAssembly(): Promise<void> {
  const id = detail.value?.recordId ?? recordId.value
  if (id === null || retryingAssembly.value) return
  retryingAssembly.value = true
  assemblyNotice.value = ''
  try {
    const response = await retryClassroomAssembly(id)
    assemblyNotice.value = response.data.complete
      ? '再生用の音声を作り直しました。'
      : '再生用の音声を作り直しましたが、欠けている音声があります（'
        + (response.data.reason ?? '理由は記録されています') + '）。'
    // 状態を取り直す（作れた 1 本をすぐ再生できるように）
    await refresh()
  } catch (caught) {
    assemblyNotice.value = messageOf(caught, '再生用の音声を作り直せませんでした。')
  } finally {
    retryingAssembly.value = false
  }
}

let pollTimer: number | null = null
/** ポーリングの間隔（AI 生図と同じ 2 秒）。 */
const POLL_INTERVAL_MS = 2000

function stopPolling(): void {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

/** 最終まとめができるまで 2 秒間隔で取り直す。 */
function startPolling(): void {
  if (pollTimer !== null) return
  pollTimer = window.setInterval(() => { void refresh() }, POLL_INTERVAL_MS)
}

/** 授業の記録を削除する（確認してから。音声の実体も消える）。 */
async function removeRecord(): Promise<void> {
  const current = detail.value
  if (current === null || deleting.value) return
  if (!window.confirm(`「${current.title ?? '（授業名なし）'}」を削除します。元音声も消えます。よろしいですか？`)) {
    return
  }
  deleting.value = true
  try {
    await deleteClassroomRecord(current.recordId)
    toast.success('授業の記録を削除しました。')
    void router.push({ path: `/${area.value}/classroom` })
  } catch (caught) {
    error.value = messageOf(caught, '授業の記録を削除できませんでした。')
    toast.danger(error.value)
  } finally {
    deleting.value = false
  }
}

function backToList(): void {
  void router.push({ path: `/${area.value}/classroom` })
}

onMounted(async () => {
  if (recordId.value === null) return
  loading.value = true
  await refresh()
  loading.value = false
  if (pending.value || (detail.value !== null && detail.value.status === 'STOPPED')) {
    // 終了直後は最終まとめを作っている途中なので、できるまで取り直す
    startPolling()
  }
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<template>
  <div class="cr-page" data-cr-detail>
    <p class="cr-notice">
      <AppIcon name="info" size="sm" />
      <span>
        授業終了後の整理結果です。AI が作った最終まとめ（テーマ／学習内容／先生の重点／宿題）・
        転写全文・元音声をここで確認できます。
      </span>
    </p>

    <section class="card">
      <div class="card__header cr-list-head">
        <h2 class="card__title"><AppIcon name="mic" size="sm" /> {{ lessonName }}</h2>
        <span class="badge badge--neutral">{{ subject }}</span>
        <span class="badge badge--outline">言語モード：{{ languageMode }}</span>
        <span class="badge badge--outline">前置詞：{{ preset }}</span>
        <span class="badge" :class="statusBadgeClass(detail?.status ?? '')" data-cr-detail-status>{{ statusLabel }}</span>
        <span class="badge badge--neutral">録音時間：{{ durationLabel(detail?.durationSeconds) }}</span>
        <div class="cr-list-head__spacer"></div>
        <div class="search-panel__actions">
          <button type="button" class="btn btn--secondary" data-cr-back-list @click="backToList">
            <AppIcon name="chevron-left" size="sm" /> 一覧へ戻る
          </button>
          <button
            v-if="detail !== null" type="button" class="btn btn--icon is-danger" :disabled="deleting"
            aria-label="この授業の記録を削除" data-cr-detail-delete @click="removeRecord"
          >
            <AppIcon name="trash" size="sm" />
          </button>
        </div>
      </div>

      <p v-if="error !== ''" class="alert alert--danger" data-cr-detail-error>{{ error }}</p>
      <p v-else-if="loading" class="cr-page__loading" data-cr-detail-loading>読み込んでいます...</p>

      <div class="cr-detail__body">
        <!-- 最終整理結果 -->
        <section>
          <h3 class="cr-column__title"><AppIcon name="wand" size="sm" /> 最終整理結果</h3>
          <div class="cr-detail__grid" data-cr-detail-result>
            <div
              v-for="section in sections" :key="section.key" class="cr-detail__block"
              :data-cr-detail-section="section.key"
            >
              <span class="cr-detail__block-title"><AppIcon :name="section.icon" size="sm" /> {{ section.title }}</span>
              <span class="cr-detail__block-body">
                {{ sectionBody(section.body) }}
              </span>
            </div>
          </div>
        </section>

        <!-- 転写全文 -->
        <section class="cr-column" data-cr-transcript-full>
          <div class="cr-column__head">
            <h3 class="cr-column__title"><AppIcon name="list" size="sm" /> 転写全文</h3>
            <span class="cr-hint">{{ segments.length }} 行</span>
          </div>
          <div class="cr-column__body">
            <template v-if="segments.length > 0">
              <p
                v-for="segment in segments" :key="segment.seq" class="cr-transcript__line"
                :data-cr-transcript-line="segment.seq"
                :data-cr-transcript-speaker="speakerLabelOf(segment)"
              >
                <span class="cr-transcript__time">[{{ formatSegmentTime(segment.startOffsetSeconds) }}]</span>
                <span class="cr-transcript__speaker">{{ speakerLabelOf(segment) }}：</span>
                <span class="cr-transcript__text">{{ segment.text }}</span>
              </p>
            </template>
            <p v-else class="cr-column__placeholder">
              録音の書き起こし全文がここに表示されます（時刻・話者つき）。
              まだ書き起こしがありません。
            </p>
          </div>
        </section>

        <!-- 宿題 -->
        <section class="cr-column" data-cr-homework>
          <div class="cr-column__head">
            <h3 class="cr-column__title"><AppIcon name="check-square" size="sm" /> 宿題</h3>
          </div>
          <div class="cr-column__body">
            <p class="cr-column__placeholder" data-cr-homework-body>
              {{ sectionBody(sections.find((section) => section.key === 'homework')?.body ?? '') }}
            </p>
          </div>
        </section>

        <!-- 元音声（Range/206 の配信を <audio> がそのまま読む） -->
        <section>
          <h3 class="cr-column__title"><AppIcon name="file-audio" size="sm" /> 元音声</h3>
          <div class="cr-audio" data-cr-audio>
            <AppIcon name="file-audio" size="lg" class="cr-audio__icon" />
            <div class="cr-audio__body">
              <span class="cr-audio__title">録音の再生</span>
              <span class="cr-audio__note" data-cr-audio-note>{{ audioNote }}</span>
              <!--
                再生用の 1 本を作れなかったときだけ出す入口。
                **分塊（元の音）は残っている**ので、ここから作り直せる。
              -->
              <span v-if="assemblyNotice !== ''" class="cr-audio__note" data-cr-assembly-notice>
                {{ assemblyNotice }}
              </span>
              <button
                v-if="canRetryAssembly" type="button" class="btn btn--secondary btn--sm"
                :disabled="retryingAssembly" data-cr-assembly-retry @click="retryAssembly"
              >
                {{ retryingAssembly ? '作り直しています...' : '再生用の音声を作り直す' }}
              </button>
              <div class="cr-audio__player" aria-label="音声プレーヤー" data-cr-audio-player>
                <audio
                  v-if="showAudioPlayer" :src="audioUrl" controls preload="none"
                  data-cr-audio-element @error="onAudioError"
                ></audio>
                <template v-else>
                  <button
                    type="button" class="btn btn--icon btn--sm" disabled
                    :title="audioFailed ? '音声を読み込めませんでした' : '音声はありません'"
                  >
                    <AppIcon name="play" size="sm" />
                  </button>
                  <span class="cr-audio__bar" aria-hidden="true"></span>
                  <span class="cr-audio__time">00:00 / 00:00</span>
                </template>
              </div>
            </div>
          </div>
        </section>
      </div>
    </section>
  </div>
</template>
