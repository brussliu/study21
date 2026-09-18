<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  CLASSROOM_MAX_SIZE,
  createClassroomRecord,
  deleteClassroomRecord,
  endClassroomRecord,
  fetchClassroomOptions,
  fetchClassroomPresets,
  importClassroomSource,
  searchClassroomRecords,
  startClassroomRecord,
  type ClassroomLanguageMode,
  type ClassroomRecordRow
} from '@/api/classroom'
import {
  CLASSROOM_QUERY,
  DEFAULT_PRESETS,
  LANGUAGE_MODES,
  SUBJECTS,
  durationLabel,
  formatDateTime,
  languageModeLabel,
  presetOptionName,
  presetOptions,
  statusBadgeClass,
  type ClassroomPresetOption
} from '@/features/classroom/classroom'
import {
  AUDIO_MODE_LABELS,
  loadAudioMode,
  saveAudioMode,
  saveRecordAudioMode,
  type ClassroomAudioMode
} from '@/features/classroom/audio-graph'
import '@/features/classroom/classroom.css'

/**
 * 授業録音 / AI 授業記録 — 授業一覧。
 *
 * 録音した授業（授業名・言語モード・状態・録音時間・書き起こしの有無・元音声の有無）を
 * カードで並べる画面。データは user-api の `GET /api/user/classroom` から読む
 * （見える範囲はサーバーが決める＝生徒＝自分／保護者＝家庭／管理者＝全体）。
 *
 * ページングは置かない（書籍管理と同じ方針。API には 1 ページの上限 100 件で読む）。
 * 1 件を選ぶと詳細（文字起こし・ノート・宿題・元音声）へ進む。
 */
const route = useRoute()
const router = useRouter()

/** 画面のエリア（/student, /parent, /admin）。 */
const area = computed(() => route.path.split('/')[1] ?? 'student')

const records = ref<ClassroomRecordRow[]>([])
const loading = ref(false)
const error = ref('')

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

/** 一覧を読む（1 ページの上限まで。並びはサーバーの既定＝新しい順）。 */
async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const response = await searchClassroomRecords({ page: 1, size: CLASSROOM_MAX_SIZE })
    records.value = response.data.items
  } catch (caught) {
    records.value = []
    error.value = messageOf(caught, '授業の一覧を取得できませんでした。')
  } finally {
    loading.value = false
  }
}

/* ---------- 【新しい授業】の dialog ---------- */

/** 授業の始め方（利用者の指示: 録音／音声ファイル／文字起こしの貼り付け）。 */
type NewLessonMode = 'record' | 'upload' | 'paste'

const newDialogOpen = ref(false)
const newMode = ref<NewLessonMode>('record')
const newTitle = ref('')
const newSubject = ref<string>(SUBJECTS[0])
const newLanguageMode = ref<ClassroomLanguageMode>('ja')
/** 前置詞（シナリオプリセット）。AI ノートの整理方針になるので授業を作る前に選ぶ。 */
const newPreset = ref<string>(DEFAULT_PRESETS[0]?.id ?? 'standard')
/** 前置詞の選択肢（API が返るまでは画面の初期値を使う）。 */
const presetList = ref<ClassroomPresetOption[]>(
  DEFAULT_PRESETS.map((item) => ({ id: item.id, name: item.name, description: item.description }))
)
/** 前置詞を取れなかったときの理由（日本語。空なら問題なし）。 */
const presetNotice = ref('')
/** 録音する音源（**録音するときだけ**選ぶ。オンライン授業は PC が再生している先生の声も録る）。 */
const newAudioMode = ref<ClassroomAudioMode>(loadAudioMode())
/** 書き起こしを誰が行うか（設定から読む。読めないときは null＝案内を出さない）。 */
const sttMode = ref<'BROWSER' | 'SERVER' | null>(null)
const newFile = ref<File | null>(null)
const newText = ref('')
const creating = ref(false)
const newError = ref('')

/** 取り込みの上限（サーバーと同じ値。画面でも先に知らせる）。 */
const UPLOAD_MAX_MINUTES = 90
const PASTE_MAX_CHARS = 500_000

/** いま選んでいる前置詞の説明。 */
const newPresetDescription = computed(() => {
  const found = presetList.value.find((item) => item.id === newPreset.value)
  return found?.description ?? ''
})

/** いま選んでいる前置詞の名前（録音画面へ渡す。知らない ID はそのまま出す）。 */
const newPresetName = computed(
  () => presetOptionName(presetList.value, newPreset.value) ?? newPreset.value
)

/**
 * 書き起こしの設定（誰が認識するか）を読む。
 *
 * <p>二音源（マイク＋スピーカー）をブラウザ認識で録ると、**スピーカーの音は文字にならない**
 * （ブラウザの認識はマイクからしか音を取れない）。授業を始める前に気づけるように、選んだときに出す。</p>
 */
async function loadSttMode(): Promise<void> {
  try {
    sttMode.value = (await fetchClassroomOptions()).data.sttMode
  } catch {
    // 設定が読めないときは案内を出さない（録音そのものは今までどおりできる）
    sttMode.value = null
  }
}

/** 前置詞の選択肢を読む（読めないときは画面の初期値のまま、理由を出す）。 */
async function loadPresets(): Promise<void> {
  presetNotice.value = ''
  try {
    const response = await fetchClassroomPresets()
    const mapped = presetOptions(response.data)
    if (mapped.length > 0) presetList.value = mapped
  } catch (caught) {
    presetNotice.value = messageOf(caught, '前置詞の一覧を取得できませんでした。')
  }
  // いま選んでいる前置詞が無くなったときは先頭に寄せる（空の選択を残さない）
  if (!presetList.value.some((item) => item.id === newPreset.value)) {
    newPreset.value = presetList.value[0]?.id ?? ''
  }
}

function openNewDialog(): void {
  newDialogOpen.value = true
  newMode.value = 'record'
  newTitle.value = ''
  newSubject.value = SUBJECTS[0]
  newLanguageMode.value = 'ja'
  newPreset.value = presetList.value[0]?.id ?? newPreset.value
  // 前回選んだ音源を覚えている（オンライン授業が続く週に選び直させない）
  newAudioMode.value = loadAudioMode()
  newFile.value = null
  newText.value = ''
  newError.value = ''
  void loadPresets()
  void loadSttMode()
}

/** 選んだ音声ファイルの長さ（秒）。分からないときは null。 */
async function durationOf(file: File): Promise<number | null> {
  return await new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const audio = new Audio()
    audio.preload = 'metadata'
    audio.onloadedmetadata = () => {
      URL.revokeObjectURL(url)
      resolve(Number.isFinite(audio.duration) ? audio.duration : null)
    }
    audio.onerror = () => {
      URL.revokeObjectURL(url)
      resolve(null)
    }
    audio.src = url
  })
}

/** 画面での事前チェック（mp3・90 分・50 万字）。 */
async function validateNewLesson(): Promise<string> {
  if (newMode.value === 'upload') {
    const file = newFile.value
    if (file === null) return '音声ファイル（mp3）を選んでください。'
    const isMp3 = file.type.includes('mpeg') || file.type.includes('mp3') || file.name.toLowerCase().endsWith('.mp3')
    if (!isMp3) return '音声ファイルは mp3 を選んでください。'
    const seconds = await durationOf(file)
    // 長さが読めないファイルは**音声ではない**（拡張子だけ mp3 のファイルを通さない）
    if (seconds === null) {
      return 'この音声ファイルを読み取れませんでした（mp3 か確かめてください）。'
    }
    if (seconds > UPLOAD_MAX_MINUTES * 60) {
      return `音声ファイルは ${UPLOAD_MAX_MINUTES} 分までです（この音声は約 ${Math.round(seconds / 60)} 分）。`
    }
    return ''
  }
  if (newMode.value === 'paste') {
    if (newText.value.trim() === '') return '文字起こしを貼り付けてください。'
    if (newText.value.length > PASTE_MAX_CHARS) {
      return `文字起こしは ${PASTE_MAX_CHARS} 文字までです。`
    }
  }
  return ''
}

/**
 * 【録音を開始】: 授業を作って、選んだやり方で始める。
 *
 * ・録音 … 録音画面へ移り、**そのまま録音を始める**（`autostart=1`）
 * ・音声ファイル … 取り込んで書き起こし、終了してから詳細へ
 * ・貼り付け … そのまま書き起こしにし、終了してから詳細へ
 */
async function submitNewLesson(): Promise<void> {
  if (creating.value) return
  newError.value = await validateNewLesson()
  if (newError.value !== '') return
  creating.value = true
  try {
    // 前置詞は数値の ID で送る（`standard` などの画面の初期値は送らない）
    const presetId = /^\d+$/.test(newPreset.value) ? Number(newPreset.value) : null
    const created = await createClassroomRecord({
      // 授業名は任意（空ならサーバーが「授業名未設定」を入れる）
      title: newTitle.value.trim(),
      subject: newSubject.value,
      languageMode: newLanguageMode.value,
      presetId
    })
    const recordId = created.data.recordId
    if (newMode.value === 'record') {
      /*
       * **この記録の音源**を残す（利用者の指示 7 節）。
       *
       * 話者は「その記録がどの音源で録られたか」で決まる（二音源＝マイク学生・共有先生／
       * 単一音源＝講義）。録音中の画面・詳細画面のどちらから読んでも同じ話者になるよう、
       * **記録を作った時点＝利用者が選んだ時点**で残す（一覧から開き直しても分かる）。
       * 取り込み（音声ファイル・貼り付け）には音源の選択が無いので残さない
       * （残すと、音源の分け方が無い記録に**選んでいない設定**を付けることになる）。
       */
      saveRecordAudioMode(recordId, newAudioMode.value)
      // 選んだ音源を覚えてから、録音画面へ渡す（画面はその音源で録音を始める）
      saveAudioMode(newAudioMode.value)
      await startClassroomRecord(recordId)
      newDialogOpen.value = false
      await router.push({
        path: `/${area.value}/classroom/${recordId}/live`,
        query: {
          [CLASSROOM_QUERY.name]: newTitle.value.trim(),
          [CLASSROOM_QUERY.subject]: newSubject.value,
          [CLASSROOM_QUERY.languageMode]: newLanguageMode.value,
          [CLASSROOM_QUERY.preset]: newPresetName.value,
          [CLASSROOM_QUERY.audioMode]: newAudioMode.value,
          [CLASSROOM_QUERY.autostart]: '1'
        }
      })
      return
    }
    // 取り込み（音声ファイル or 貼り付け）→ 終了して詳細へ
    const file = newMode.value === 'upload' ? newFile.value : null
    const seconds = file === null ? null : await durationOf(file)
    await importClassroomSource(recordId, {
      file,
      text: newMode.value === 'paste' ? newText.value : null,
      durationSeconds: seconds
    })
    await endClassroomRecord(recordId)
    newDialogOpen.value = false
    await load()
    await router.push({ path: `/${area.value}/classroom/${recordId}` })
  } catch (caught) {
    newError.value = messageOf(caught, '授業を始められませんでした。')
  } finally {
    creating.value = false
  }
}

/** カードを選んで詳細へ進む。 */
function openRecord(recordId: number): void {
  void router.push({ path: `/${area.value}/classroom/${recordId}` })
}

/* ---------- 削除（カードごと） ---------- */

/** 削除の確認に出すカード（null なら閉じている）。 */
const deleteTarget = ref<ClassroomRecordRow | null>(null)
const deleting = ref(false)

function askDelete(record: ClassroomRecordRow): void {
  deleteTarget.value = record
}

/** 確認のうえで消す（音声の実体も消える。消したら一覧を取り直す）。 */
async function confirmDelete(): Promise<void> {
  const target = deleteTarget.value
  if (target === null || deleting.value) return
  deleting.value = true
  error.value = ''
  try {
    await deleteClassroomRecord(target.recordId)
    deleteTarget.value = null
    await load()
  } catch (caught) {
    deleteTarget.value = null
    error.value = messageOf(caught, '授業を削除できませんでした。')
  } finally {
    deleting.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="cr-page" data-cr-list>
    <section class="card">
      <div class="card__header cr-list-head">
        <h2 class="card__title"><AppIcon name="mic" size="sm" /> 授業録音</h2>
        <span class="badge badge--neutral">AI 授業記録</span>
        <div class="cr-list-head__spacer"></div>
        <div class="search-panel__actions">
          <button type="button" class="btn btn--primary" data-cr-create @click="openNewDialog">
            <AppIcon name="plus" size="sm" /> 新しい授業
          </button>
        </div>
      </div>

      <p v-if="error !== ''" class="alert alert--danger" data-cr-list-error>{{ error }}</p>
      <p v-else-if="loading" class="cr-page__loading" data-cr-list-loading>読み込んでいます...</p>

      <div class="cr-grid" data-cr-history>
        <article
          v-for="record in records" :key="record.recordId" class="cr-card"
          :data-cr-history-item="record.recordId"
        >
          <div class="cr-card__body">
            <h3 class="cr-card__title">{{ record.title === null || record.title === '' ? '（授業名なし）' : record.title }}</h3>
            <div class="cr-card__badges">
              <span class="badge" :class="statusBadgeClass(record.status)">{{ record.statusLabel }}</span>
              <!-- 科目は授業名とは別に出す（未設定なら出さない） -->
              <span
                v-if="record.subject !== null && record.subject !== ''" class="badge badge--primary"
                data-cr-history-subject
              >{{ record.subject }}</span>
              <span class="badge badge--outline">{{ languageModeLabel(record.languageMode) }}</span>
              <span class="badge badge--neutral">{{ record.recordNo }}</span>
            </div>
            <p class="cr-card__meta">
              録音時間：{{ durationLabel(record.durationSeconds) }}
              ／書き起こし：{{ record.transcribedChars === null ? 0 : record.transcribedChars }} 文字
            </p>
            <p class="cr-card__meta" data-cr-history-created-at>
              作成：{{ record.createdAt === null || record.createdAt === '' ? '（不明）' : formatDateTime(record.createdAt) }}
            </p>
          </div>
          <div class="cr-card__foot">
            <span class="cr-card__mark" :class="{ 'is-empty': !record.hasAudio }">
              <AppIcon name="file-audio" size="sm" />
              {{ record.hasAudio ? '元音声あり' : '元音声なし' }}
            </span>
            <button
              type="button" class="btn btn--secondary btn--sm" :data-cr-open="record.recordId"
              @click="openRecord(record.recordId)"
            >
              <AppIcon name="eye" size="sm" /> 開く
            </button>
            <button
              type="button" class="btn btn--danger btn--sm" :data-cr-delete="record.recordId"
              @click="askDelete(record)"
            >
              <AppIcon name="trash" size="sm" /> 削除
            </button>
          </div>
        </article>

        <!-- 【新しい授業】の dialog（ページ遷移しない。利用者の指示） -->
        <div
          v-if="newDialogOpen" class="cr-dialog-backdrop" data-cr-new-dialog
          role="dialog" aria-modal="true" aria-labelledby="cr-new-dialog-title"
        >
          <div class="card cr-dialog cr-dialog--wide">
            <h3 id="cr-new-dialog-title" class="cr-dialog__title">新しい授業</h3>

            <div class="cr-dialog__grid">
              <label class="cr-dialog__field">
                <span>授業名（任意）</span>
                <input
                  v-model="newTitle" class="input" type="text" maxlength="120"
                  placeholder="未入力なら「授業名未設定」" data-cr-new-title
                >
              </label>
              <label class="cr-dialog__field">
                <span>科目</span>
                <select v-model="newSubject" class="select" data-cr-new-subject>
                  <option v-for="item in SUBJECTS" :key="item" :value="item">{{ item }}</option>
                </select>
              </label>
              <label class="cr-dialog__field">
                <span>言語モード</span>
                <select v-model="newLanguageMode" class="select" data-cr-new-language>
                  <option v-for="mode in LANGUAGE_MODES" :key="mode.value" :value="mode.value">
                    {{ mode.label }}
                  </option>
                </select>
              </label>
              <label class="cr-dialog__field">
                <span>前置詞（シナリオプリセット）</span>
                <select v-model="newPreset" class="select" data-cr-new-preset>
                  <option v-for="item in presetList" :key="item.id" :value="item.id">{{ item.name }}</option>
                </select>
              </label>
            </div>

            <p v-if="presetNotice !== ''" class="alert alert--warning" data-cr-new-preset-notice>
              {{ presetNotice }}
            </p>
            <p class="cr-dialog__note" data-cr-new-preset-desc>
              {{ newPresetName }}：{{ newPresetDescription }}
            </p>

            <!-- 始め方の 3 つ（録音／音声ファイル／文字起こしの貼り付け） -->
            <div class="cr-dialog__modes" role="radiogroup" aria-label="授業の始め方">
              <label class="cr-dialog__mode">
                <input v-model="newMode" type="radio" value="record" data-cr-new-mode-record>
                録音する（マイク／オンライン授業）
              </label>
              <label class="cr-dialog__mode">
                <input v-model="newMode" type="radio" value="upload" data-cr-new-mode-upload>
                音声ファイル（mp3・{{ UPLOAD_MAX_MINUTES }} 分まで）
              </label>
              <label class="cr-dialog__mode">
                <input v-model="newMode" type="radio" value="paste" data-cr-new-mode-paste>
                文字起こしを貼り付ける（{{ PASTE_MAX_CHARS.toLocaleString() }} 字まで）
              </label>
            </div>

            <!-- 録音する音源（利用者の指示で録音中の画面から**ここへ移動**した） -->
            <div v-if="newMode === 'record'" class="cr-dialog__field cr-dialog__audio" data-cr-new-audio>
              <span>録音する音源</span>
              <select v-model="newAudioMode" class="select" data-cr-new-audio-mode>
                <option v-for="(label, value) in AUDIO_MODE_LABELS" :key="value" :value="value">
                  {{ label }}
                </option>
              </select>
              <template v-if="newAudioMode !== 'mic'">
                <p class="cr-dialog__note" data-cr-new-audio-guide>
                  録音を始めると共有の選択が出ます。<strong>「タブの音声を共有」</strong>
                  （画面全体を選ぶときは「システム オーディオを共有」）にチェックを入れてください。
                  <strong>映像は保存も送信もしません</strong>（音だけ使います）。
                  <strong>共有しても音は消えません</strong>（今までどおり聞こえます）。
                  スピーカーで聞くとその音がマイクにも入るので、<strong>ヘッドホン</strong>をおすすめします。
                </p>
                <!-- ブラウザ認識はマイクの音しか書き起こせない（始める前に知らせる） -->
                <p v-if="sttMode === 'BROWSER'" class="alert alert--warning" data-cr-new-stt-notice>
                  いまの設定はブラウザ音声認識です。ブラウザ認識は<strong>マイクの音しか書き起こせません</strong>
                  （スピーカーから出る先生の声は文字になりません。音声には両方入ります）。
                  両方を書き起こすには、システム設定「授業録音」の STT プロバイダーを
                  <strong>サーバー認識</strong>（阿里巴巴 など）にしてください。
                </p>
              </template>
            </div>

            <p v-if="newMode === 'upload'" class="cr-dialog__note">
              音声ファイルを選ぶと、サーバーが書き起こしてから授業として保存します（mp3 のみ・
              {{ UPLOAD_MAX_MINUTES }} 分まで）。
            </p>
            <input
              v-if="newMode === 'upload'" type="file" accept="audio/mpeg,.mp3" class="input"
              data-cr-new-file @change="(event) => {
                const input = event.target as HTMLInputElement
                newFile = input.files !== null && input.files.length > 0 ? input.files[0] : null
              }"
            >

            <p v-if="newMode === 'paste'" class="cr-dialog__note">
              書き起こし（文字起こし）を貼り付けてください。行・文ごとに分けて保存します。
            </p>
            <textarea
              v-if="newMode === 'paste'" v-model="newText" class="input" rows="6"
              :maxlength="PASTE_MAX_CHARS" placeholder="授業の文字起こしを貼り付け"
              data-cr-new-text
            ></textarea>

            <p v-if="newError !== ''" class="alert alert--danger" data-cr-new-error>{{ newError }}</p>
            <p class="cr-dialog__note">
              ※ AI まとめ（フェーズノート・最終まとめ）は現在オフです。書き起こしだけを作ります。
            </p>

            <div class="cr-dialog__actions">
              <button
                type="button" class="btn btn--secondary" data-cr-new-cancel
                :disabled="creating" @click="newDialogOpen = false"
              >
                キャンセル
              </button>
              <button
                type="button" class="btn btn--primary" data-cr-new-submit
                :disabled="creating" @click="submitNewLesson"
              >
                <AppIcon name="mic" size="sm" />
                {{ creating ? '準備しています…' : (newMode === 'record' ? '録音を開始' : '取り込む') }}
              </button>
            </div>
          </div>
        </div>

        <!-- 削除の確認（背景クリックでは閉じない。押し間違いで消さない） -->
        <div
          v-if="deleteTarget !== null" class="cr-dialog-backdrop" data-cr-delete-dialog
          role="dialog" aria-modal="true"
        >
          <div class="card cr-dialog">
            <h3 class="cr-dialog__title">この授業を削除しますか？</h3>
            <p class="cr-dialog__body">
              「{{ deleteTarget.title === null || deleteTarget.title === '' ? '（授業名なし）' : deleteTarget.title }}」を削除します。
              書き起こし・AI ノート・<strong>元音声</strong>も一緒に消え、元に戻せません。
            </p>
            <div class="cr-dialog__actions">
              <button
                type="button" class="btn btn--secondary" data-cr-delete-cancel
                :disabled="deleting" @click="deleteTarget = null"
              >
                キャンセル
              </button>
              <button
                type="button" class="btn btn--danger" data-cr-delete-confirm
                :disabled="deleting" @click="confirmDelete"
              >
                <AppIcon name="trash" size="sm" /> {{ deleting ? '削除しています…' : '削除する' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 1 件も無いとき（ダミーは置かない） -->
        <div v-if="!loading && error === '' && records.length === 0" class="cr-empty" data-cr-empty>
          <AppIcon name="file-audio" size="lg" />
          <p class="cr-empty__title">授業の履歴はまだありません</p>
          <p class="cr-empty__note">
            録音した授業がここに並びます（授業名・言語モード・日時・録音時間・元音声の有無）。
            まずは【＋ 新しい授業】から録音を始めてください。
          </p>
        </div>
      </div>
    </section>
  </div>
</template>
