<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  classroomAudioUrl,
  deleteClassroomRecord,
  fetchClassroomRecord,
  recoverClassroomNote,
  runClassroomNote,
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
  /*
   * **欠落がある回は「音声は保存されています」と言い切らない**。
   * 一部の区間は残っていないので、残っている数と失った連番を出す。
   */
  if (lostNotice.value !== '') return lostNotice.value
  const state = assembly.value
  if (state === null || state === undefined) return '録音した音声をそのまま再生できます。'
  if (state.state === 'READY' && state.complete) {
    return '録音した音声をそのまま再生できます（音声は保存されています）。'
  }
  if (state.state === 'FAILED') {
    return `音声は保存されています（${state.storedChunks} 件）。`
      + '再生用の音声はまだ作れていません（作り直せます）。'
  }
  if (state.state === 'INCOMPLETE') {
    // 結合しないと決めた回（欠落がある）。**音が欠けていること**を先に言う
    return `音声の一部が残っていません（保存できたのは ${state.storedChunks} 件）。`
      + '再生用の音声は、欠けている区間があるため作りません。'
  }
  if (state.state === 'QUEUED' || state.state === 'PROCESSING') {
    return `音声は保存されています（${state.storedChunks} 件）。再生用の音声を作成中です。`
  }
  // NOT_STARTED: **「作成中」とは言わない**（永久に待つ画面にしない）
  return `音声は保存されています（${state.storedChunks} 件）。`
    + '再生用の音声はまだ作っていません。'
})

/**
 * **不完全なまま終えた回**の案内（日本語。空なら欠落していない）。
 *
 * <p>終了のときに利用者が確認して失った範囲（{@code lossSeqs}）を、詳細画面で**出し続ける**
 * （一度きりの通知にしない。あとから見た人にも「どこが失われたか」が分かる）。</p>
 */
const lostNotice = computed(() => {
  const lost = detail.value?.lossSeqs ?? []
  if (lost.length === 0) return ''
  return `この授業は音声の一部（連番 ${lost.join('、')}）を保存できずに終了しています。`
    + 'その区間の音は残っていません（書き起こしのテキストは残っています）。'
})

/** 再生用の 1 本を作り直せるか（作れなかったときだけ出す）。 */
const canRetryAssembly = computed(() => {
  const state = assembly.value
  if (state === null || state === undefined) return false
  /*
   * `NOT_STARTED` も出す（作る必要があるのに作っていない回＝再起動などで取りこぼした回）。
   * `QUEUED` / `PROCESSING` は作成中なので出さない（押しても同じ結果）。`INCOMPLETE` は
   * 欠落があるので、結合そのものはもう一度試せる（音は消さない）。
   */
  return state.state === 'FAILED' || state.state === 'NOT_STARTED' || state.state === 'INCOMPLETE'
})

/**
 * **書き起こし（認識）の収尾の結果**（音声の欠落とは**別の軸**）。
 *
 * <p>`complete=false` は「識別が不完全」または「確認できない」。画面は
 * 「録音と書き起こしを保存しました」と言い切らない（利用者の指摘 ①-5）。</p>
 */
const transcribe = computed(() => detail.value?.transcribe ?? null)

/** 書き起こしが不完全なまま終わった回の案内（日本語。空なら欠落していない）。 */
const transcribeNotice = computed(() => {
  const status = transcribe.value
  if (status === null || status.complete) return ''
  if (status.status === 'NO_AUDIO') return ''
  return 'この授業は書き起こしの一部を完了できませんでした（録音した音声は保存されています）。'
    + (status.reason === null ? '' : ` ${status.reason}`)
})

/** 音源ごとの内訳（詳細情報の中でだけ出す）。 */
const transcribeSourceNote = computed(() => {
  const status = transcribe.value
  if (status === null || status.sources.length === 0) return ''
  return status.sources
    .map((source) => {
      const state = source.completed ? '済み'
        : source.retryable ? 'やり直せます' : 'やり直しても直りません'
      return `【${source.label}】${source.label === '' ? source.source : ''}${state}`
        + (source.reason === null ? '' : `：${source.reason}`)
    })
    .join(' ')
})

/**
 * **最終まとめの状態**（詳細画面が「生成中／失敗」を正しく出すための材料）。
 *
 * <p>`PENDING` / `GENERATING` を「生成中」、`FAILED` を「作れなかった（再試行できる）」、
 * `READY` を「できました」として扱う。ノートの行そのものが**タスクの記録**なので、
 * 画面のリロードでも状態は変わらない。</p>
 */
const finalNote = computed(() => detail.value?.notes.find((note) => note.kind === 'FINAL') ?? null)

/**
 * 最終まとめの**作成/やり直しの入口を出すか**。
 *
 * <p>`PENDING` は **user-api が行を作っただけ**で、admin-api の実行が受理された証拠ではない
 * （起動が届かなかった回・サーバーが落ちた回）。だから `PENDING` にも入口を出す
 * （以前は `FAILED` だけだったので、**未着手のまま永久に「作成中」**に見えていた）。</p>
 *
 * <p>`GENERATING`（受理済み・実行中）と `READY`（完成）では出さない。</p>
 */
const canRetryFinalNote = computed(() => {
  const status = finalNote.value?.status
  return status === 'PENDING' || status === 'FAILED'
})

/** 入口の文言（未着手と失敗で分ける）。 */
const finalNoteActionLabel = computed(() => (
  finalNote.value?.status === 'PENDING' ? '最終まとめを作成' : '最終まとめを再試行'
))

/** 最終まとめの状態の表示（日本語。PENDING を「作成中」と言わない）。 */
const finalNoteStateText = computed(() => {
  const note = finalNote.value
  if (note === null) return ''
  if (note.status === 'READY') return '最終まとめができました。'
  if (note.status === 'FAILED') {
    return '録音は保存しました。最終まとめの生成に失敗しました（'
      + `${note.errorMessage ?? note.errorCode ?? '理由は記録されています'}）。`
  }
  if (note.status === 'GENERATING') {
    return '最終まとめを作成しています（この画面を開いたままでも閉じても進みます）。'
  }
  // PENDING: **まだ始まっていない**（「作成中」と読ませない）
  return '最終まとめはまだ作成していません（下のボタンから作成できます）。'
})

/**
 * まとめの操作が失敗した理由（日本語）。
 *
 * <p>認証切れ（401）と権限不足（403）は**原因が違う**: 前者は入り直せば直る、後者はそもそも
 * その操作が許されていない。ネットワークの失敗と混ぜない（利用者が次に何をすればよいか
 * 分からなくなる）。**どちらも無限に再試行しない**（文言で入り直し／権限を伝える）。</p>
 */
function noteFailureReason(caught: unknown): string {
  const code = caught instanceof ApiError ? caught.code : undefined
  if (code === 'UNAUTHENTICATED') {
    return 'ログインの有効期限が切れています。もう一度ログインしてからお試しください。'
  }
  if (code === 'FORBIDDEN') {
    return 'この授業の最終まとめを操作する権限がありません。'
  }
  if (code === 'NOT_FOUND') {
    return '最終まとめが見つかりませんでした（画面を開き直してください）。'
  }
  return '最終まとめの生成を開始できませんでした（'
    + messageOf(caught, '通信に失敗しました') + '）。'
}

/** 最終まとめの起動を頼んでいる最中か（連打で二重に走らせない）。 */
const retryingFinalNote = ref(false)

/** 状態の確認／復旧を頼んでいる最中か。 */
const checkingFinalNote = ref(false)

/**
 * **状態を確認／復旧**の入口を出すか。
 *
 * <p>`GENERATING` のときだけ出す（実行中かどうかは**後端が実行記録で確かめる**。画面は
 * 「失联」と断言しない）。押しても**実行中なら何も起きない**ので、二重起動の害は無い。</p>
 */
const canCheckFinalNote = computed(() => finalNote.value?.status === 'GENERATING')

/**
 * **まとめの実行の状態を確かめ、失联していれば回復してもらう**。
 *
 * <p>サーバーが再起動して実行が消えた回は、これで**やり直せる失敗**に戻り、
 * 【最終まとめを再試行】が出る。実行中ならそのまま「作成しています」を出し続ける。</p>
 */
async function checkFinalNote(): Promise<void> {
  const noteId = finalNote.value?.noteId ?? null
  if (noteId === null || checkingFinalNote.value) return
  checkingFinalNote.value = true
  finalNoteNotice.value = ''
  try {
    const recordIdForCall = detail.value?.recordId ?? recordId.value
    if (recordIdForCall === null) return
    const response = await recoverClassroomNote(recordIdForCall, noteId)
    finalNoteNotice.value = response.data.reason
    // 状態を取り直す（回復していれば FAILED → 再試行の入口が出る）
    await refresh()
    if (finalNote.value?.status === 'GENERATING') startPolling()
  } catch (caught) {
    // **分からない**ことを「失敗」と言い切らない（確かめられなかった、と伝える）
    finalNoteNotice.value = '最終まとめの状態を確認できませんでした（'
      + noteFailureReason(caught) + '）少し待ってからもう一度お試しください。'
  } finally {
    checkingFinalNote.value = false
  }
}

/** 最終まとめの起動の結果（日本語。空なら出さない）。 */
const finalNoteNotice = ref('')

/**
 * **最終まとめの生成をもう一度頼む**（受理だけを待つ。AI の完了は待たない）。
 *
 * <p>同じ要求を何度送っても、admin-api 側の条件つき更新が**2 つ目のタスクを作らない**。
 * 受理できたら状態を取り直し、`GENERATING` として「作成しています」を出す。</p>
 */
async function retryFinalNote(): Promise<void> {
  const id = detail.value?.recordId ?? recordId.value
  const noteId = finalNote.value?.noteId ?? null
  if (id === null || noteId === null || retryingFinalNote.value) return
  retryingFinalNote.value = true
  finalNoteNotice.value = ''
  try {
    // **user-api の保護された入口**を呼ぶ（所有権と noteId の帰属は user-api が確かめる）
    const response = await runClassroomNote(id, noteId)
    // 状態を取り直して、**受理されたかを記録で確かめる**（応答の文面を信じない）
    await refresh()
    if (finalNote.value?.status === 'GENERATING' || finalNote.value?.status === 'READY') {
      finalNoteNotice.value = response.data.message ?? '最終まとめの作成を始めました。'
      // 走っているあいだは状態を取り続ける（「作成中」のまま放置しない）
      startPolling()
    } else if (finalNote.value?.status === 'FAILED') {
      finalNoteNotice.value = '録音は保存しました。最終まとめの生成に失敗しました（'
        + `${finalNote.value.errorMessage ?? finalNote.value.errorCode ?? '理由は記録されています'}）。`
      startPolling()
    } else {
      // **まだ始まっていない**（応答は受理と言ったが、記録は PENDING）。入口を残す
      finalNoteNotice.value = '最終まとめの作成はまだ始まっていません。もう一度お試しください。'
    }
  } catch (caught) {
    /*
     * 応答を失ったかもしれない。**同じノートの状態**を確かめてから伝える
     * （受理されていたら「開始できませんでした」とは言わない）。
     */
    await refresh()
    if (finalNote.value?.status === 'GENERATING' || finalNote.value?.status === 'READY') {
      finalNoteNotice.value = '最終まとめの作成を始めました。'
      startPolling()
    } else {
      /*
       * **失敗を隠さない**（録音の保存は成功していることも一緒に伝える）。
       * 認証切れ（401）と権限不足（403）は原因が違うので区別した文言にする
       * （ネットワーク失敗に紛れさせない・無限に再試行しない）。
       */
      finalNoteNotice.value = `録音は保存しました。${noteFailureReason(caught)}`
    }
  } finally {
    retryingFinalNote.value = false
  }
}

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

        <!--
          **書き起こしが不完全なまま終わった回**の案内（利用者の指摘 ①-5）。
          音声の欠落（無音の区間）とは**別**の欄にして、混ぜない。
        -->
        <p v-if="transcribeNotice !== ''" class="alert alert--warning" data-cr-transcribe-notice>
          <AppIcon name="alert" size="sm" />
          <span>{{ transcribeNotice }}</span>
        </p>
        <!-- 音源ごとの内訳（短い案内の中身。詳しく知りたい人だけ開く） -->
        <details v-if="transcribeSourceNote !== ''" class="cr-details" data-cr-transcribe-sources>
          <summary>書き起こしの内訳（音源ごと）</summary>
          <p class="cr-details__missing">{{ transcribeSourceNote }}</p>
        </details>

        <!--
          **最終まとめの状態**（生成中・失敗・できました）。
          失敗したときだけ【最終まとめを再試行】を出す（走っている・できているときは出さない）。
        -->
        <section v-if="finalNote !== null" class="card" data-cr-final-note>
          <div class="cr-list-head">
            <h3 class="cr-column__title"><AppIcon name="wand" size="sm" /> 最終まとめ</h3>
            <span class="badge" :class="statusBadgeClass(finalNote.status)">
              {{ finalNote.statusLabel }}
            </span>
          </div>
          <p class="cr-hint" data-cr-final-note-state>{{ finalNoteStateText }}</p>
          <span v-if="finalNoteNotice !== ''" class="cr-hint" data-cr-final-note-notice>
            {{ finalNoteNotice }}
          </span>
          <button
            v-if="canRetryFinalNote" type="button" class="btn btn--secondary btn--sm"
            :disabled="retryingFinalNote" data-cr-final-note-retry @click="retryFinalNote"
          >
            {{ retryingFinalNote ? '頼んでいます...' : finalNoteActionLabel }}
          </button>
          <!--
            **状態を確認／復旧**（利用者の指摘 ②）。実行中かどうかは**後端が実行記録で確かめる**
            （前端は「失联」と断言しない）。サーバーが再起動して実行が消えた回は、これで
            やり直せる状態に戻る（実行中なら何も起きない）。
          -->
          <button
            v-if="canCheckFinalNote" type="button" class="btn btn--ghost btn--sm"
            :disabled="checkingFinalNote" data-cr-final-note-recover @click="checkFinalNote"
          >
            {{ checkingFinalNote ? '確認しています...' : '状態を確認／復旧' }}
          </button>
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
