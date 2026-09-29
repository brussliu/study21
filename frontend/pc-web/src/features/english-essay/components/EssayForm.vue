<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ApiError } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import EssayCropDialog from './EssayCropDialog.vue'
import EssayDropzone from './EssayDropzone.vue'
import EssayImagePreview from './EssayImagePreview.vue'
import EssayTempFilePicker from './EssayTempFilePicker.vue'
import type { EssayOcrImage } from '@/api/english-essay'
import {
  addGradingRound,
  createEssay,
  essayLimits,
  loadEssay,
  loadEssayLimits,
  recognizeEssay,
  removeEssay,
  saveEssay,
  uploadEssayImage,
  type Essay,
  type EssayImageCategory
} from '../store'

import { ESSAY_LEVELS, ESSAY_LEVEL_LABELS, countWords, type EssayGrading, type EssayLevel } from '../types'
import '@/features/temp-file/temp-file.css'
import '@/features/english-essay/essay-page.css'

/**
 * 英作文の新規／編集の**中身**（2.0 の `#essayEditorContent` 相当）。
 *
 * <p>ステップは 2.0 と同じ 3 つ: 1 画像アップロード → 2 文字認識・確認 → 3 AI添削。
 * 画像は**必ずトリミング窓を通してから**並べる（2.0 の `addFiles` と同じ）。文字認識は
 * user-api の入口（**同期**）、添削は user-api の**受付**（実行は働き手）を呼び、
 * **詳細画面へ遷移**して結果を見せる。</p>
 *
 * <p>節の並びは 2.0 の新規画面のまま: hero（見出し・説明・英雄の操作）→ stepper（3 ステップ）→
 * 01 画像を追加 → 02 AI文字認識（OCR・設問／手書き作文）→ 03 AI添削レポート
 * （英検級・題の 2 欄と【この内容でAI添削】）。ただし 2.0 と違い、**語数の目安の欄は出さず**、
 * **英検級は 03 に置く**（利用者の指示・2026-09-28）。保存は呼ぶ側（ページ）が
 * 英雄の右に置く＝2.0 の `essay-hero-actions` と同じ位置。編集で開いたときの**前回の得点**も
 * 同じ英雄の右（操作の隣）に出す（利用者の指示で、足元の【キャンセル】は置かない）。</p>
 *
 * <p>保存の順序は「作文を作る → 画像を上げる → 並びと区分を更新」。**保存を押すまで API を
 * 呼ばない**ので、途中で離れれば作文は残らない。途中で失敗したときは、そのとき作った作文を消す。</p>
 */

const props = withDefaults(defineProps<{ essayId?: string | null }>(), { essayId: null })

const emit = defineEmits<{
  saved: [essayId: string]
  graded: [essayId: string]
  notify: [tone: 'success' | 'danger', message: string]
}>()

/** 画面が持つ下書き（「保存」を押すまで API は触らない）。 */
interface Draft {
  id: string | null
  level: EssayLevel
  /** 題（日本語）。2.0 の `essayTitleJa`。 */
  title: string
  /** 題（中文）。2.0 の `essayTitleZh`。 */
  titleZh: string
  questionText: string
  essayText: string
  images: DraftImage[]
}

/**
 * 画面が持つ画像 1 枚。
 *
 * <p>「保存」を押すまで API は触らないので、画像 ID はまだ無い（`imageId` が null）。
 * 画面の中での同一性は `key` で持つ（データ URL は同じ内容なら同じ文字列になるので使えない）。</p>
 */
interface DraftImage {
  /** 画面の中での識別子（並べ替え・区分の切り替えに使う）。 */
  key: string
  /** API の画像 ID（上げたあとに入る）。 */
  imageId: string | null
  category: EssayImageCategory
  fileName: string
  /** まだ上げていない画像はトリミング済みのデータ URL、上げたあとは API の URL。 */
  dataUrl: string
  order: number
}

/** トリミング窓へ渡す画像（データ URL と、元ファイルの大きさ）。 */
interface PendingImage {
  fileName: string
  dataUrl: string
  sizeBytes: number
}

/** 画面が使う認識の結果（信頼度は区分ごと）。 */
interface Recognition {
  questionText: string
  essayText: string
  questionConfidence: number
  essayConfidence: number
}

function emptyDraft(): Draft {
  return { id: null, level: 'PRE1', title: '', titleZh: '', questionText: '', essayText: '', images: [] }
}

function draftOf(essay: Essay): Draft {
  return {
    id: essay.id,
    level: essay.level,
    title: essay.title,
    titleZh: essay.titleZh ?? '',
    questionText: essay.questionText,
    essayText: essay.essayText,
    images: essay.images.map((image) => ({
      key: `saved-${image.id}`,
      imageId: image.id,
      category: image.category,
      fileName: image.fileName,
      dataUrl: image.dataUrl,
      order: image.order
    }))
  }
}

const draft = ref<Draft>(emptyDraft())
const ocr = ref<Recognition | null>(null)
const reading = ref(false)
/** 保存・添削の実行中（二重送信を防ぐ）。 */
const busy = ref(false)
/** 認識結果を利用者が書き換えたか。 */
const textTouched = ref(false)
/** 題を利用者が打ったか（認識結果の題で上書きしない）。 */
const titleTouched = ref(false)
const pickingTempFiles = ref(false)
/** トリミング待ちの画像（1 枚ずつ窓を出す）。 */
const pending = ref<PendingImage | null>(null)
/** 拡大表示している画像（2.0 の `openImagePreview`。null なら閉じている）。 */
const previewImage = ref<DraftImage | null>(null)
/** トリミング待ちの行列（ファイルとデータ URL で入口が 2 つある）。 */
const fileQueue = ref<File[]>([])
const dataQueue = ref<PendingImage[]>([])
let processing = false
let sequence = 0
/** 窓が閉じたことを待ち合わせる（行列を 1 枚ずつ流すため）。 */
let settleCrop: (() => void) | null = null
/** 読み込みの世代（遅れて返った読み込みを捨てる）。 */
let openToken = 0

/** 読み込みが終わったときの下書き（**未保存かどうか**の比べる元）。 */
const baseline = ref<string>('')
/** 読み込みが終わったか（最初の 1 回が終わるまで「未保存」と見なさない）。 */
const ready = ref(false)

/** 今の下書きを文字にする（未保存の判定に使う）。 */
function snapshot(): string {
  return JSON.stringify({
    level: draft.value.level,
    title: draft.value.title,
    titleZh: draft.value.titleZh,
    questionText: draft.value.questionText,
    essayText: draft.value.essayText,
    images: draft.value.images.map((image) => `${image.category}:${image.imageId ?? image.dataUrl}`)
  })
}

/**
 * **未保存の変更があるか**（利用者に確認するかどうか）。
 *
 * <p>一度も API へ上げていない画像（`imageId` が null）があれば未保存。読み込みが終わる前は
 * 何も言わない（開いた直後に「保存しますか」と聞かない）。</p>
 */
const dirty = computed<boolean>(() => {
  if (!ready.value) {
    return false
  }
  if (draft.value.images.some((image) => image.imageId === null)) {
    return true
  }
  return snapshot() !== baseline.value
})

const step = computed<1 | 2 | 3>(() => {
  if (draft.value.essayText.trim() === '') {
    return draft.value.images.length === 0 ? 1 : 2
  }
  return ocr.value === null ? 2 : 3
})

const canRecognize = computed(() => draft.value.images.length > 0 && !reading.value)
const canGrade = computed(() => draft.value.questionText.trim() !== '' && draft.value.essayText.trim() !== '')
const wordCount = computed(() => countWords(draft.value.essayText))

/** 編集で開いたか（見出しと説明の文言を変える）。 */
const isEdit = computed<boolean>(() => draft.value.id !== null)

/**
 * 画像の上限（**サーバーが決める**。`null` は「不明」＝画面では制限しない）。
 *
 * <p>取れなかったときも操作は止めない（超えていればサーバーが日本語の理由を返す）。</p>
 */
const maxImages = computed<number | null>(() => essayLimits.value?.maxImages ?? null)
const maxImageMb = computed<number | null>(() => essayLimits.value?.maxImageMb ?? null)

/** まだ足せる枚数（上限が不明なら `null` ＝制限しない）。 */
const remainingImages = computed<number | null>(
  () => (maxImages.value === null ? null : Math.max(0, maxImages.value - draft.value.images.length))
)

/** まだ足せるか（上限が不明のときは足せる）。 */
const canAddImages = computed<boolean>(
  () => remainingImages.value === null || remainingImages.value > 0
)

/** 上限に達したときの知らせ（上限が不明なら何も言わない）。 */
function notifyImageLimit(): void {
  if (maxImages.value !== null) {
    notify('danger', `画像は最大${maxImages.value}枚までです。`)
  }
}

/** 区分ごとの画像（参照画像のサムネイルと、信頼度の出し分けに使う）。 */
const questionImages = computed(() => draft.value.images.filter((image) => image.category === 'question'))
const answerImages = computed(() => draft.value.images.filter((image) => image.category === 'answer'))

/**
 * 区分ごとの信頼度（%）。
 *
 * <p>2.0 は設問と作文で別々の信頼度を出していた（`questionConfidence` / `answerConfidence`）。
 * その区分の画像が 1 枚も無ければ、信頼度は出さない（`null`）。</p>
 */
const questionConfidence = computed<number | null>(
  () => (ocr.value === null || questionImages.value.length === 0 ? null : ocr.value.questionConfidence)
)
const essayConfidence = computed<number | null>(
  () => (ocr.value === null || answerImages.value.length === 0 ? null : ocr.value.essayConfidence)
)

/** 信頼度の見出し（認識していない・画像が無い区分は「未認識」）。 */
function confidenceLabel(value: number | null): string {
  return value === null ? '未認識' : `信頼度 ${value}%`
}

/** 編集で開いたときの直前の添削（まだ無ければ null）。 */
const previousGrading = ref<EssayGrading | null>(null)

/** 前回の得点（満点が分からない回は点数だけ。得点が分からない回は何も出さない）。 */
const previousScore = computed<string>(() => {
  const grading = previousGrading.value
  if (grading === null || grading.score === undefined) {
    return ''
  }
  return grading.maxScore === undefined
    ? String(grading.score)
    : `${grading.score} / ${grading.maxScore}`
})

/** 窓が開いているか。 */
const cropping = computed(() => pending.value !== null)

/** 画像の見出しに出す枚数（上限が分かるときだけ「/ 8」を付ける）。 */
const fileCounter = computed<string>(() => (maxImages.value === null
  ? `${draft.value.images.length}枚`
  : `${draft.value.images.length} / ${maxImages.value}枚`))

/** OCR の状態（2.0 の `essayOcrStatus` と同じ 3 つ）。 */
const ocrStatus = computed<{ label: string; tone: string }>(() => {
  if (reading.value) {
    return { label: '文字認識中', tone: 'is-processing' }
  }
  if (draft.value.questionText.trim() !== '' || draft.value.essayText.trim() !== '') {
    return { label: '認識済み', tone: 'is-ready' }
  }
  return { label: '画像待ち', tone: 'is-waiting' }
})

/** 拡大編集している欄（null なら閉じている）。 */
type ZoomField = 'question' | 'essay'
const zoomField = ref<ZoomField | null>(null)
/** 拡大編集の下書き（【内容を反映】を押すまで本文へ書かない）。 */
const zoomText = ref('')

const zoomTitle = computed<string>(() => (zoomField.value === 'essay' ? '手書き作文' : '作文の設問'))
/** 拡大編集の見出し（2.0 の `essayTextModalCategory` と同じ）。 */
const ZOOM_CATEGORY = 'AI文字認識結果'

function openZoom(field: ZoomField): void {
  zoomField.value = field
  zoomText.value = field === 'essay' ? draft.value.essayText : draft.value.questionText
}

function closeZoom(): void {
  zoomField.value = null
  zoomText.value = ''
}

/** 【内容を反映】。拡大編集した本文を元の欄へ写す。 */
function applyZoom(): void {
  if (zoomField.value === 'essay') {
    draft.value.essayText = zoomText.value
  } else if (zoomField.value === 'question') {
    draft.value.questionText = zoomText.value
  }
  closeZoom()
}

watch(() => props.essayId, () => {
  void openDraft()
}, { immediate: true })

/**
 * 下書きを用意する（編集なら API から読み込む）。
 *
 * <p>読み込みは非同期なので、開き直されたときに**古い応答を捨てる**（世代を数える）。</p>
 */
async function openDraft(): Promise<void> {
  openToken += 1
  const token = openToken
  draft.value = emptyDraft()
  ocr.value = null
  textTouched.value = false
  // 保存済みの題は利用者が付けたもの。読み取り直しで上書きしない
  titleTouched.value = false
  previousGrading.value = null
  pending.value = null
  previewImage.value = null
  fileQueue.value = []
  dataQueue.value = []
  pickingTempFiles.value = false
  reading.value = false
  busy.value = false
  ready.value = false
  baseline.value = ''
  closeZoom()
  // 画像の上限は**サーバーから取る**（取れなくても操作は止めない。待たせもしない）
  void loadEssayLimits()
  const essayId = props.essayId
  if (essayId === null || essayId === undefined || essayId === '') {
    ready.value = true
    baseline.value = snapshot()
    return
  }
  try {
    const detail = await loadEssay(essayId)
    if (token !== openToken || detail === null) {
      return
    }
    draft.value = draftOf(detail.essay)
    titleTouched.value = true
    previousGrading.value = detail.essay.gradings[detail.essay.gradings.length - 1] ?? null
    baseline.value = snapshot()
  } catch (caught) {
    if (token === openToken) {
      notify('danger', messageOf(caught, '英作文を読み込めませんでした。'))
    }
  } finally {
    if (token === openToken) {
      ready.value = true
    }
  }
}

/** 認識結果を利用者が直したら「編集済み」にする（結果は消さない）。 */
watch(() => [draft.value.questionText, draft.value.essayText], () => {
  const result = ocr.value
  if (result === null) {
    return
  }
  if (draft.value.questionText !== result.questionText || draft.value.essayText !== result.essayText) {
    textTouched.value = true
  }
})

function notify(tone: 'success' | 'danger', message: string): void {
  emit('notify', tone, message)
}

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError || cause instanceof Error ? cause.message : fallback
}

/** 並び順を 1 から振り直す。 */
function renumber(): void {
  draft.value.images = draft.value.images.map((image, index) => ({ ...image, order: index + 1 }))
}

/** 画像が変わったら認識結果を捨てる（2.0 の `resetRecognition` と同じ）。 */
function resetRecognition(): void {
  ocr.value = null
  textTouched.value = false
  draft.value.questionText = ''
  draft.value.essayText = ''
}

/** File → データ URL（トリミング窓に渡す前に読む）。 */
function toDataUrl(file: File): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(new Error('画像を読み込めませんでした。'))
    reader.readAsDataURL(file)
  })
}

/**
 * 画面の画像を、API へ送る**ファイルの実体**にする。
 *
 * <p>まだ上げていない画像はトリミング済みのデータ URL、編集で読み込んだ画像は API の URL。
 * どちらも `fetch` で読めるので、同じ道でファイルにする（アップロード済みでも、その場の実体を
 * 送ってよい＝利用者の指示 2026-09-27）。</p>
 */
async function fileOf(image: DraftImage): Promise<File> {
  const response = await fetch(image.dataUrl)
  if (!response.ok) {
    throw new Error('画像を読み込めませんでした。')
  }
  const blob = await response.blob()
  return new File([blob], image.fileName, { type: blob.type || 'image/png' })
}

/** 行列を 1 枚ずつトリミング窓へ流す。 */
async function drainQueue(): Promise<void> {
  if (processing) {
    return
  }
  processing = true
  try {
    while (fileQueue.value.length > 0 || dataQueue.value.length > 0) {
      if (remainingImages.value === 0) {
        notifyImageLimit()
        fileQueue.value = []
        dataQueue.value = []
        break
      }
      const file = fileQueue.value.shift()
      if (file) {
        try {
          // 窓を開く前にデータ URL へ直す（行列の途中で読み込みに失敗しても窓が残らない）
          const dataUrl = await toDataUrl(file)
          pending.value = { fileName: file.name, dataUrl, sizeBytes: file.size }
        } catch {
          notify('danger', '画像を読み込めませんでした。')
          continue
        }
      } else {
        const data = dataQueue.value.shift()
        if (!data) {
          break
        }
        pending.value = data
      }
      await nextTick()
      // 窓が confirm / cancel で閉じるまで待つ
      await new Promise<void>((resolve) => {
        settleCrop = resolve
      })
    }
  } finally {
    processing = false
  }
}

function settle(): void {
  const resolve = settleCrop
  settleCrop = null
  resolve?.()
}

/** ドロップ／クリック選択で受け取ったファイル。 */
function onPicked(files: File[]): void {
  fileQueue.value.push(...files)
  void drainQueue()
}

function onRejected(message: string): void {
  notify('danger', message)
}

/** 臨時ファイルのピッカーで選ばれた画像（すでにデータ URL）。 */
function onTempFilesPicked(files: PendingImage[]): void {
  pickingTempFiles.value = false
  if (files.length === 0) {
    return
  }
  const room = remainingImages.value
  if (room === 0) {
    notifyImageLimit()
    return
  }
  dataQueue.value.push(...(room === null ? files : files.slice(0, room)))
  void drainQueue()
}

/** 【この範囲で保存】（トリミング窓）。まだ上げていない画像として並べる。 */
function onCropConfirm(result: { dataUrl: string; fileName: string }): void {
  const target = pending.value
  pending.value = null
  settle()
  if (target === null) {
    return
  }
  sequence += 1
  draft.value.images.push({
    key: `draft-${sequence}`,
    // 画像 ID は「保存」で上げたときに決まる
    imageId: null,
    category: 'question',
    fileName: result.fileName || target.fileName,
    dataUrl: result.dataUrl,
    order: draft.value.images.length + 1
  })
  renumber()
  resetRecognition()
}

function onCropCancel(): void {
  pending.value = null
  settle()
}

/** 画像を 1 枚外す。 */
function removeImage(key: string): void {
  draft.value.images = draft.value.images.filter((image) => image.key !== key)
  renumber()
  resetRecognition()
}

/** 画像の区分を変える。 */
function setCategory(key: string, category: EssayImageCategory): void {
  draft.value.images = draft.value.images.map((image) => (image.key === key ? { ...image, category } : image))
  resetRecognition()
}

/** 【AIで画像を読み取る】（user-api の入口。**同期**なので数十秒かかることがある）。 */
async function recognize(): Promise<void> {
  if (!canRecognize.value) {
    return
  }
  reading.value = true
  try {
    const files: EssayOcrImage[] = []
    for (const image of draft.value.images) {
      files.push({ file: await fileOf(image), category: image.category })
    }
    const result = await recognizeEssay(draft.value.level, files)
    ocr.value = {
      questionText: result.questionText,
      essayText: result.essayText,
      questionConfidence: result.questionConfidence,
      essayConfidence: result.essayConfidence
    }
    draft.value.questionText = result.questionText
    draft.value.essayText = result.essayText
    if (!titleTouched.value) {
      const source = result.questionText.trim() !== '' ? result.questionText : result.essayText
      draft.value.title = source.trim().split(/\n/)[0]?.slice(0, 40) ?? ''
    }
    textTouched.value = false
    notify('success', '画像を読み取りました。内容を確認してください。')
  } catch (caught) {
    notify('danger', messageOf(caught, '画像を読み取れませんでした。'))
  } finally {
    reading.value = false
  }
}

/** 画像の一覧を、API へ送る「残す画像」の形にする（並びは 1 から振り直す）。 */
function imageOrdersOf(images: readonly DraftImage[]): { imageId: string; category: EssayImageCategory; order: number }[] {
  return images.map((image, index) => {
    if (image.imageId === null) {
      throw new Error('画像のアップロードが終わっていません。')
    }
    return { imageId: image.imageId, category: image.category, order: index + 1 }
  })
}

/**
 * 保存する（新規は作る・編集は更新）。
 *
 * <p>順序は「作文を作る（新規）→ **既存の画像だけを送って枠を空ける** → まだ上げていない画像を
 * 上げる → 残す画像の並びと区分を更新」。サーバーは**同じ表示順**と**上限枚数**を弾くので、
 * 消した画像の枠を先に空けておかないと、後ろに足す画像の表示順が重なる（画像が上限まで
 * 入っている作文では、消しても行が残っているぶん枚数でも弾かれる）。</p>
 *
 * <p>新規で途中に失敗したときは、**作った作文を消す**（中途半端な作文を残さない）。</p>
 */
async function persist(): Promise<string | null> {
  const isNew = draft.value.id === null
  let essayId = draft.value.id
  try {
    if (essayId === null) {
      const created = await createEssay({
        level: draft.value.level,
        title: draft.value.title,
        titleZh: draft.value.titleZh,
        questionText: draft.value.questionText,
        essayText: draft.value.essayText
      })
      essayId = created.id
    }
    const kept = draft.value.images.filter((image) => image.imageId !== null)
    const pendingImages = draft.value.images.filter((image) => image.imageId === null)
    if (!isNew && pendingImages.length > 0) {
      // 先に既存の画像だけを送る（消した画像の行が消え、表示順が 1..k にそろう）
      await saveEssay(essayId, {
        level: draft.value.level,
        title: draft.value.title,
        titleZh: draft.value.titleZh,
        questionText: draft.value.questionText,
        essayText: draft.value.essayText,
        images: imageOrdersOf(kept)
      })
    }
    // まだ上げていない画像を上げる（表示順は空いている後ろへ。最後の PUT で並べ直す）
    let nextOrder = kept.length + 1
    for (const image of pendingImages) {
      const uploaded = await uploadEssayImage(essayId, {
        file: await fileOf(image),
        category: image.category,
        order: nextOrder
      })
      // 覚えておく（もう一度「保存」を押しても上げ直さない）
      image.imageId = uploaded.id
      nextOrder += 1
    }
    const saved = await saveEssay(essayId, {
      level: draft.value.level,
      title: draft.value.title,
      titleZh: draft.value.titleZh,
      questionText: draft.value.questionText,
      essayText: draft.value.essayText,
      images: imageOrdersOf(draft.value.images)
    })
    draft.value.id = saved.id
    // サーバーが返した並び・区分・ID を下書きへ戻す（応答に画像が無ければ手元のまま）
    if (saved.images.length > 0) {
      draft.value.images = saved.images.map((image, index) => ({
        key: `saved-${image.id}`,
        imageId: image.id,
        category: image.category,
        fileName: image.fileName,
        dataUrl: image.dataUrl,
        order: index + 1
      }))
    }
    // 保存できたので、ここから先は「未保存」ではない
    baseline.value = snapshot()
    return saved.id
  } catch (caught) {
    if (isNew && essayId !== null) {
      // 作った作文が中途半端に残らないように消す（消せなくても画面は知らせる）
      await removeEssay(essayId).catch(() => undefined)
    }
    notify('danger', messageOf(caught, '英作文を保存できませんでした。'))
    return null
  }
}

/** 【この内容でAI添削】。保存 → 添削を**受付ける** → 詳細へ（結果は詳細が様子を見る）。 */
async function grade(): Promise<void> {
  if (!canGrade.value || busy.value) {
    return
  }
  busy.value = true
  try {
    const id = await persist()
    if (id === null) {
      return
    }
    const accepted = await addGradingRound(id)
    notify('success', `AI添削を受付けました（第 ${accepted.round} 回）。`)
    emit('graded', id)
  } catch (caught) {
    notify('danger', messageOf(caught, 'AI添削を受付けられませんでした。'))
  } finally {
    busy.value = false
  }
}

/** 【保存】（添削はしない）。 */
async function save(): Promise<void> {
  if (busy.value) {
    return
  }
  busy.value = true
  try {
    const id = await persist()
    if (id === null) {
      return
    }
    notify('success', '英作文を保存しました。')
    emit('saved', id)
  } finally {
    busy.value = false
  }
}

/** 拡大編集は Esc でも閉じる（背景クリックでは閉じない。`docs/FRONTEND_GUIDE.md` 3.9）。 */
function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && zoomField.value !== null) {
    closeZoom()
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))

defineExpose({ save, grade, dirty, busy })
</script>

<template>
  <div class="ee-form" data-ee-form>
    <!--
      2.0 の `essay-editor-content` と同じグリッド。領域の名前もそのまま使う:
      hero（見出し）／steps（3 ステップ）／side（画像アップロード＝右の柱）／
      recognition（AI文字認識）／result（AI添削レポート）。
    -->
    <!-- 見出しと説明（2.0 の essay-hero。操作は呼ぶ側が英雄の右に置く） -->
    <header class="ee-hero" data-ee-region="hero">
      <div class="ee-hero__main">
        <h1 data-ee-form-title>
          <AppIcon name="pen" size="sm" />
          {{ isEdit ? '英作文 編集' : '英作文 新規登録' }}
        </h1>
        <p>{{ isEdit ? '保存した作文の設問・本文・画像を直して保存し直せます。' : '画像を追加し、AI文字認識・英作文添削を行って保存します。' }}</p>
        <div class="ee-hero__meta">
          <span class="ee-pill">{{ isEdit ? '編集中' : '新規登録' }}</span>
          <span><AppIcon name="image" size="sm" /> 複数画像対応</span>
          <span><AppIcon name="edit" size="sm" /> OCR結果を編集可能</span>
        </div>
      </div>
      <div class="ee-hero__actions">
        <!-- 前回の得点（編集で開いたときだけ）。英雄の右にまとめる（足元は置かない） -->
        <span v-if="previousGrading" class="ee-foot-note" data-ee-previous-score>
          前回の得点: {{ previousScore }}
        </span>
        <slot name="actions"></slot>
      </div>
    </header>

    <!-- 3 ステップ（2.0 の essay-stepper） -->
    <ol class="ee-stepper" data-ee-stepper data-ee-region="steps" aria-label="処理ステップ">
      <li :class="{ 'is-active': step === 1, 'is-complete': step > 1 }" data-step="1">
        <span>1</span><div><strong>画像アップロード</strong><small>問題・答案を追加</small></div>
      </li>
      <li :class="{ 'is-active': step === 2, 'is-complete': step > 2 }" data-step="2">
        <span>2</span><div><strong>文字認識・確認</strong><small>OCR結果を修正</small></div>
      </li>
      <li :class="{ 'is-active': step === 3 }" data-step="3">
        <span>3</span><div><strong>AI添削</strong><small>評価と改善案</small></div>
      </li>
    </ol>

    <!-- 01 画像を追加（2.0 の upload-card。**右の柱**＝グリッドの `side` 領域） -->
    <section class="ee-card ee-upload-side" data-ee-card="upload" data-ee-region="side" data-ee-upload-side>
      <div class="ee-card__head">
        <div class="ee-card__heading">
          <span class="section-number">01</span>
          <div>
            <h2>画像を追加</h2>
            <p>問題用紙と答案用紙をまとめて選択できます</p>
          </div>
        </div>
        <div class="ee-card__actions">
          <button
            type="button" class="btn btn--secondary btn--sm" data-ee-tempfiles
            :disabled="!canAddImages" @click="pickingTempFiles = true"
          >
            <AppIcon name="folder" size="sm" /> 臨時ファイル
          </button>
          <span class="ee-file-counter" data-ee-image-count>{{ fileCounter }}</span>
        </div>
      </div>

      <EssayDropzone
        :max="maxImages" :max-image-mb="maxImageMb"
        :disabled="!canAddImages || pending !== null"
        @picked="onPicked" @rejected="onRejected"
      />

      <div v-if="draft.images.length > 0" class="ee-image-grid" data-ee-images>
        <article v-for="image in draft.images" :key="image.key" class="ee-image-item" data-ee-image>
          <!-- サムネイルのクリックは**拡大だけ**（区分の下拉・削除ボタンはmetaの中にある） -->
          <img
            :src="image.dataUrl" :alt="image.fileName" data-ee-image-thumb
            role="button" tabindex="0" title="クリックして拡大表示"
            @click="previewImage = image"
            @keydown.enter.prevent="previewImage = image"
            @keydown.space.prevent="previewImage = image"
          >
          <span class="ee-image-order">{{ image.order }}</span>
          <div class="ee-image-meta">
            <strong :title="image.fileName">{{ image.fileName }}</strong>
            <select
              class="select" :value="image.category" data-ee-image-category
              :aria-label="`${image.fileName} の画像種別`"
              @change="setCategory(image.key, ($event.target as HTMLSelectElement).value as EssayImageCategory)"
            >
              <option value="question">設問画像</option>
              <option value="answer">答案画像</option>
            </select>
            <button
              type="button" class="btn btn--icon btn--sm is-danger" :title="`${image.fileName} を削除`"
              aria-label="画像を削除" @click="removeImage(image.key)"
            >
              <AppIcon name="trash" size="sm" />
            </button>
          </div>
        </article>
      </div>
      <p class="ee-hint">
        <AppIcon name="info" size="sm" />
        文字が正面から鮮明に写るように撮影すると、手書き文字の認識精度が上がります。
      </p>
    </section>

    <!-- 02 AI文字認識 と 03 AI添削レポートは**左の柱**にまとめる（右の柱＝01 画像を追加）。
         右の柱が背高でも、この柱の中の間隔（16px）は変わらない＝カードの間に空白ができない -->
    <div class="ee-form__main" data-ee-main>
      <!-- 02 AI文字認識（2.0 の recognition-card。左の柱の 1 つ目） -->
      <section class="ee-card" data-ee-card="recognition" data-ee-region="recognition">
        <div class="ee-card__head">
          <div class="ee-card__heading">
            <span class="section-number">02</span>
            <div>
              <h2>AI文字認識</h2>
              <p>AIが「作文の設問」と「あなたの作文」を分けて抽出します</p>
            </div>
          </div>
          <div class="ee-card__actions">
            <button
              type="button" class="btn btn--primary btn--sm" data-ee-ocr
              :disabled="!canRecognize" @click="recognize"
            >
              <AppIcon name="wand" size="sm" /> AIで画像を読み取る
            </button>
            <span class="ee-status" :class="ocrStatus.tone" data-ee-ocr-status>{{ ocrStatus.label }}</span>
          </div>
        </div>

        <div class="ee-ocr-grid">
          <article class="ee-ocr-panel">
            <header>
              <span class="ee-ocr-panel__type"><AppIcon name="list" size="sm" /></span>
              <span class="ee-ocr-panel__label">
                <strong>作文の設問</strong>
                <small>Question / Instructions</small>
              </span>
              <span class="ee-ocr-panel__actions">
                <button
                  type="button" class="btn btn--secondary btn--sm" data-ee-zoom-text
                  title="作文の設問を拡大して編集" aria-label="作文の設問を拡大して編集"
                  @click="openZoom('question')"
                >
                  <AppIcon name="zoom-in" size="sm" /> 拡大
                </button>
                <span class="badge badge--outline" data-ee-question-confidence>
                  {{ confidenceLabel(questionConfidence) }}
                </span>
              </span>
            </header>
            <textarea
              v-model="draft.questionText" class="input ee-ocr-text" data-ee-question-text
              placeholder="画像を読み取ると、ここに設問が表示されます。" spellcheck="false"
            ></textarea>
            <footer>
              <span class="ee-source" data-ee-source>
                <template v-if="questionImages.length > 0">
                  <img
                    v-for="image in questionImages" :key="image.key" class="ee-source__thumb"
                    :src="image.dataUrl" :alt="`設問画像 ${image.fileName}`"
                  >
                  <span class="ee-source__count">{{ questionImages.length }}枚</span>
                </template>
                <template v-else>参照画像なし</template>
              </span>
              <span>認識後に編集できます</span>
            </footer>
          </article>

          <article class="ee-ocr-panel">
            <header>
              <span class="ee-ocr-panel__type"><AppIcon name="pen" size="sm" /></span>
              <span class="ee-ocr-panel__label">
                <strong>手書き作文</strong>
                <small>Student's Essay</small>
              </span>
              <span class="ee-ocr-panel__actions">
                <button
                  type="button" class="btn btn--secondary btn--sm" data-ee-zoom-text
                  title="手書き作文を拡大して編集" aria-label="手書き作文を拡大して編集"
                  @click="openZoom('essay')"
                >
                  <AppIcon name="zoom-in" size="sm" /> 拡大
                </button>
                <span class="badge badge--outline" data-ee-essay-confidence>
                  {{ confidenceLabel(essayConfidence) }}
                </span>
              </span>
            </header>
            <textarea
              v-model="draft.essayText" class="input ee-ocr-text" data-ee-essay-text
              placeholder="画像を読み取ると、ここに作文本文が表示されます。" spellcheck="false"
            ></textarea>
            <footer>
              <span class="ee-source" data-ee-source>
                <template v-if="answerImages.length > 0">
                  <img
                    v-for="image in answerImages" :key="image.key" class="ee-source__thumb"
                    :src="image.dataUrl" :alt="`答案画像 ${image.fileName}`"
                  >
                  <span class="ee-source__count">{{ answerImages.length }}枚</span>
                </template>
                <template v-else>参照画像なし</template>
              </span>
              <span data-ee-word-count>{{ wordCount }} words</span>
            </footer>
          </article>
        </div>

        <p class="ee-hint">
          <AppIcon name="info" size="sm" />
          誤認識があれば、添削前にテキストを直接修正してください。
        </p>
        <p v-if="textTouched" class="ee-hint" data-ee-text-edited>本文は編集済みです。この内容で添削します。</p>
      </section>

      <!-- 03 AI添削レポート（2.0 の result-card。左の柱の 2 つ目。英検級・題の 2 欄と【この内容でAI添削】） -->
      <section class="ee-card" data-ee-card="result" data-ee-region="result">
        <div class="ee-card__head">
          <div class="ee-card__heading">
            <span class="section-number">03</span>
            <div>
              <h2>AI添削レポート</h2>
              <p>設問への適合度、内容、構成、語彙・文法を総合評価</p>
            </div>
          </div>
        </div>

        <!-- 英検級は添削の基準（観点の満点・語数の目安）を決めるので、このカードに置く
             （利用者の指示で 02 AI文字認識 から移した）。 -->
        <div class="ee-form-grid">
          <label class="field">
            <span class="field__label">英検級</span>
            <select v-model="draft.level" class="select" data-ee-level aria-label="英検級">
              <option v-for="level in ESSAY_LEVELS" :key="level" :value="level">
                {{ ESSAY_LEVEL_LABELS[level] }}
              </option>
            </select>
          </label>
        </div>

        <div class="ee-title-fields" aria-label="作文タイトル">
          <label class="field" for="ee-title-ja">
            <span class="field__label">タイトル（日本語）</span>
            <input
              id="ee-title-ja" v-model="draft.title" class="input" type="text" maxlength="200"
              placeholder="OCR後にAIが生成します。必要に応じて編集してください。"
              data-ee-title @input="titleTouched = true"
            >
          </label>
          <label class="field" for="ee-title-zh">
            <span class="field__label">标题（中文）</span>
            <input
              id="ee-title-zh" v-model="draft.titleZh" class="input" type="text" maxlength="200"
              placeholder="OCR 后由 AI 生成，可直接修改。" data-ee-title-zh
            >
          </label>
        </div>

        <div class="ee-recognition-foot">
          <p class="ee-hint">
            <AppIcon name="info" size="sm" />
            ここまでの内容を保存し、AI添削を開始します。
          </p>
          <button type="button" class="btn btn--primary" data-ee-grade :disabled="!canGrade || busy" @click="grade">
            <AppIcon name="check-circle" size="sm" /> この内容でAI添削
          </button>
        </div>
      </section>
    </div>

    <!-- 足元は置かない（利用者の指示で【キャンセル】を削除。前回の得点は英雄の右にある）。
         未保存で離れるときの確認は呼ぶ側（ページ）の `onBeforeRouteLeave` が受け持つ -->

    <!-- 拡大編集（2.0 の `essay-text-modal` 相当。背景クリックでは閉じない） -->
    <div v-if="zoomField !== null" class="overlay" data-ee-text-zoom>
      <section class="dialog dialog--lg ee-text-zoom" role="dialog" aria-modal="true" :aria-label="zoomTitle">
        <header class="dialog__head">
          <div>
            <span class="ee-text-zoom__category">{{ ZOOM_CATEGORY }}</span>
            <h2 class="dialog__title">{{ zoomTitle }}を拡大して編集</h2>
          </div>
          <button type="button" class="dialog__close" data-ee-zoom-close aria-label="閉じる" @click="closeZoom">
            <AppIcon name="x" size="sm" />
          </button>
        </header>

        <div class="dialog__body ee-text-zoom__body">
          <textarea
            v-model="zoomText" class="input ee-text-zoom__editor" data-ee-zoom-editor
            spellcheck="false" :aria-label="`${zoomTitle}を拡大して編集`"
          ></textarea>
        </div>

        <footer class="dialog__foot">
          <span class="ee-text-zoom__count">{{ countWords(zoomText) }} words ・ {{ zoomText.length }} 文字</span>
          <span class="ee-form__spacer"></span>
          <button type="button" class="btn btn--secondary" data-ee-zoom-cancel @click="closeZoom">キャンセル</button>
          <button type="button" class="btn btn--primary" data-ee-zoom-apply @click="applyZoom">
            <AppIcon name="check" size="sm" /> 内容を反映
          </button>
        </footer>
      </section>
    </div>
  </div>

  <EssayCropDialog
    :open="cropping" :src="pending?.dataUrl ?? ''" :file-name="pending?.fileName ?? 'image'"
    @confirm="onCropConfirm" @cancel="onCropCancel"
  />

  <!-- 画像の拡大表示（2.0 の `#essayImagePreviewModal`。× 【閉じる】 Esc で閉じる） -->
  <EssayImagePreview
    :open="previewImage !== null" :src="previewImage?.dataUrl ?? ''"
    :title="previewImage?.fileName" @close="previewImage = null"
  />

  <EssayTempFilePicker
    :open="pickingTempFiles" :max="remainingImages"
    @close="pickingTempFiles = false" @picked="onTempFilesPicked"
  />
</template>

<style scoped>
/* 設問／手書き作文の見出し（2.0 の `ocr-panel header` の見せ方）。 */
.ee-ocr-panel__type {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-md);
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.ee-ocr-panel__label {
  flex: 1;
  min-width: 0;
}

.ee-ocr-panel__label strong {
  display: block;
  color: var(--color-text-strong);
  font-size: var(--fs-sm);
}

.ee-ocr-panel__label small {
  display: block;
  color: var(--color-text-subtle);
  font-size: var(--fs-2xs);
}

/* 拡大ボタンと信頼度を、設問／作文の見出しの右にまとめる。 */
.ee-ocr-panel__actions {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

/* 参照画像（2.0 の `questionSource` / `answerSource` 相当）。 */
.ee-source {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-1);
  min-width: 0;
  color: var(--color-text-subtle);
}

.ee-source__thumb {
  width: 28px;
  height: 28px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  object-fit: cover;
}

.ee-source__count {
  white-space: nowrap;
}

/* 拡大編集（長い作文を書くための窓）。 */
.ee-text-zoom {
  display: flex;
  flex-direction: column;
  width: min(1100px, 94vw);
  height: min(80vh, 760px);
}

.ee-text-zoom__category {
  color: var(--color-text-subtle);
  font-size: var(--fs-2xs);
  font-weight: 600;
  letter-spacing: 0.08em;
}

.ee-text-zoom__body {
  display: flex;
  flex: 1;
  min-height: 0;
  padding: var(--sp-4);
}

.ee-text-zoom__editor {
  flex: 1;
  min-height: 0;
  padding: var(--sp-4);
  font-family: Georgia, 'Times New Roman', serif;
  font-size: var(--fs-lg);
  line-height: 1.9;
  resize: none;
}

.ee-text-zoom__count {
  color: var(--color-text-subtle);
  font-size: var(--fs-xs);
}
</style>
