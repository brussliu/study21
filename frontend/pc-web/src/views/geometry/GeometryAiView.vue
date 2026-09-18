<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  createGeometryAiRequest,
  fetchGeometryAiOptions,
  fetchGeometryAiRequest,
  resubmitGeometryAiRequest,
  uploadGeometryAiImage,
  type GeometryAiOptions,
  type GeometryAiRequestDetail
} from '@/api/geometry-ai'
import { getTempFiles, type TempFileSummary } from '@/api/tempfiles'
import {
  AI_IMAGE_ACCEPT,
  isAcceptedImage,
  toUploadableImage
} from '@/features/geometry/geometry-ai-image'
import {
  CROP_ALL,
  CROP_HANDLES,
  clampCrop,
  cropOriginLabel,
  cropSizeLabel,
  cropStyle,
  isFullCrop,
  moveCrop,
  resizeCrop,
  type CropHandle,
  type CropRect,
  type ImageSize
} from '@/features/geometry/geometry-crop'
import {
  MODE_DEFINITIONS,
  OUTPUT_TYPE_OPTIONS,
  defaultSupplementValues,
  fromSupplementRows,
  modeDefinition,
  modeLabel,
  outputTypeLabel,
  supplementFieldsFor,
  toSupplementPayload,
  type FigureMode,
  type FigureOutputType,
  type SupplementValues
} from '@/features/geometry/geometry-ai-options'
import '@/features/geometry/geometry.css'
import '@/features/geometry/geometry-ai.css'
import '@/features/geometry/geometry-ai-mode.css'

/**
 * 図形管理【AI 生図】（メニュー「数学勉強」＞「図形管理」＞【新規】＞ AI で作図する）。
 *
 * <p>画像から作図を作る。**作図方法（A〜D）**が「入力をどう読むか」を、
 * **作成する図の種類（AUTO / GEOMETRY / GRAPH / MIXED）**が「何を作るか」を決める。
 * 1 回の送信で実行するのは**選んだ 1 つの処理だけ**（A〜D は独立したバッチと出力 DTO を持つ）。</p>
 *
 * <p>流れ（4 ステップ。利用者の指定した名前をそのまま使う）:</p>
 * <ol>
 *   <li>画像を追加 … ファイル選択・ドラッグ・貼り付け（スクリーンショット含む）・臨時ファイルから選ぶ</li>
 *   <li>読み取る範囲を指定 … 図の部分だけを AI に送る（モードごとに残すものの案内を出す）</li>
 *   <li>作図方法を選択 … A〜D と、A・C・D では作成する図の種類＋補充項目</li>
 *   <li>内容を確認して送信 … 内容を確かめて【AI に送る】</li>
 * </ol>
 *
 * <p>送信すると**図形管理の一覧へ戻る**。処理はバックエンドが実行するので、画面を閉じても
 * 更新しても続く（一覧にタスクのカードが出る。ここで待たない）。</p>
 *
 * <p>`?requestId=N` で開くと、**追加入力待ち・失敗したタスク**の内容（画像・選んだ条件・AI からの
 * 質問）を復元して、直して送り直せる。</p>
 */
const route = useRoute()
const router = useRouter()
const toast = useToast()

/** 画面のエリア（/student, /parent, /admin）。 */
const area = computed(() => route.path.split('/')[1] ?? 'student')

/** 画像の受け付け（設定「AI 生図」の既定と同じ）。 */
const IMAGE_ACCEPT = AI_IMAGE_ACCEPT
/** 画像 1 枚の最大サイズの既定（MB）。設定を読めたらその値で上書きする。 */
const DEFAULT_IMAGE_MAX_MB = 10
/** 補足要求の上限（画面の入力欄とサーバーの検証で同じ値）。 */
const NOTE_MAX = 300
/** 臨時ファイルの一覧で出す件数。 */
const TEMP_FILE_LIMIT = 60
/** ステップの名前（利用者の指定）。 */
const STEPS: { id: number; label: string; icon: string }[] = [
  { id: 1, label: '画像を追加', icon: 'upload' },
  { id: 2, label: '読み取る範囲を指定', icon: 'crop' },
  { id: 3, label: '作図方法を選択', icon: 'filter' },
  { id: 4, label: '内容を確認して送信', icon: 'check-square' }
]

/* ---------- 画面の状態 ---------- */

/** いま出しているステップ（1〜4）。 */
const step = ref(1)
/** 選んだ画像（送信するときに使う実体）と、そのファイル名・大きさ・プレビュー。 */
const pickedFile = ref<File | null>(null)
const fileName = ref('')
const fileSize = ref(0)
/** プレビューの URL（**object URL**。差し替え・画面離脱で必ず解放する）。 */
const imageUrl = ref('')
/** 画像の読み込みに失敗したときの案内。 */
const imageError = ref('')
/** 元画像の実寸（読み込めたときだけ分かる。読み取る範囲の px 表示に使う）。 */
const natural = ref<ImageSize | null>(null)
/** 読み取る範囲（元画像に対する割合）。 */
const crop = ref<CropRect>({ ...CROP_ALL })
/** ドラッグ＆ドロップで画像を掴んでいるか。 */
const dropping = ref(false)
/** 範囲のドラッグ中か（枠の移動・大きさの変更）。 */
const cropDragging = ref(false)
/** 読み取る範囲の画像プレビュー（AI に送る画像。作れない環境では空のまま）。 */
const cropPreview = ref('')

/** 作図方法（A〜D）と、作成する図の種類。 */
const mode = ref<FigureMode>('A')
const outputType = ref<FigureOutputType>('AUTO')
/** 補充要求（任意）と、元の名前・ラベルを残すか（既定は残す）。 */
const note = ref('')
const keepLabels = ref(true)
/**
 * モードごとの補充項目の値。
 *
 * <p>**モードを切り替えても入力は残す**（打ち直させない）が、当てはまらない項目は送らない
 * （`toSupplementPayload` が種類ごとに絞る）。</p>
 */
const supplements = ref<SupplementValues>({
  reproduceFocus: '',
  whenInsufficient: '',
  knownValues: '',
  coordinateRange: '',
  formulaCorrection: '',
  parameters: '',
  domain: '',
  viewRange: '',
  showAuxiliary: '',
  goal: '',
  problemCorrection: '',
  keepObjects: '',
  changeObjects: '',
  textCorrection: '',
  keepLabels: true
})
/** 上級の項目を開いているか（モードごとに 1 つ）。 */
const advancedOpen = ref(false)

/** AI からの質問への回答（追加入力待ちの再開のときだけ使う）。 */
const answers = ref<Record<string, string>>({})

/* ---------- 送信前の確認・再開 ---------- */

/** サーバーの設定（有効／無効・上限・既定値）。読めないときは null。 */
const options = ref<GeometryAiOptions | null>(null)
/** 画像 1 枚の上限（MB）。設定が読めればその値。 */
const imageMaxMb = computed(() => options.value?.maxImageMb ?? DEFAULT_IMAGE_MAX_MB)
/** AI 生図が使えるか（設定が読めないときは使えない扱いにする）。 */
const enabled = computed(() => options.value?.enabled === true)
/** 使えない理由（設定の案内・日次上限）。 */
const optionsNotice = computed(() => options.value?.notice ?? '')
/** 本日の上限に達したか。 */
const limitReached = computed(() => {
  const current = options.value
  return current !== null && current.dailyLimit > 0 && current.usedToday >= current.dailyLimit
})
/** AI 生図を送れるか。 */
const canSend = computed(() => enabled.value && !limitReached.value)

/** 送信中か（画像のアップロード〜受付まで）。 */
const sending = ref(false)
/** 送信の失敗理由（日本語）。 */
const sendError = ref('')

/** 再開している要求（`?requestId=`。無ければ null）。 */
const resumeRequest = ref<GeometryAiRequestDetail | null>(null)
const resuming = ref(false)
/** 再開時の案内（質問があることなど）。 */
const resumeNotice = ref('')

/** 臨時ファイルの選択（小窓）を開いているか・読み込み中・一覧・失敗理由。 */
const tempOpen = ref(false)
const tempLoading = ref(false)
const tempError = ref('')
const tempFiles = ref<TempFileSummary[]>([])
const tempPicking = ref(false)

const fileInput = ref<HTMLInputElement | null>(null)
const cropStage = ref<HTMLElement | null>(null)

/** 範囲のドラッグ 1 回分（開始位置と、そのときの枠）。 */
interface CropDrag {
  handle: CropHandle | 'move'
  startX: number
  startY: number
  width: number
  height: number
  rect: CropRect
}
let cropDrag: CropDrag | null = null

/* ---------- 計算した表示 ---------- */

/** 画像を選んでいないと先へ進めない（すべてのモードで画像が要る）。 */
const hasImage = computed(() => imageUrl.value !== '')
/** いまのステップで【次へ】を押せるか。 */
const canGoNext = computed(() => (step.value === 1 ? hasImage.value : step.value < STEPS.length))
/** 読み取る範囲が画像全体のままか。 */
const fullCrop = computed(() => isFullCrop(crop.value))

/** 画像の大きさの表示（例: 1.2 MB）。 */
const fileSizeLabel = computed(() =>
  fileSize.value >= 1024 * 1024
    ? `${(fileSize.value / 1024 / 1024).toFixed(1)} MB`
    : `${Math.max(1, Math.round(fileSize.value / 1024))} KB`
)

/** 読み取る範囲の大きさ（実寸が分かれば px も出す）。 */
const cropSize = computed(() => cropSizeLabel(crop.value, natural.value))
/** 読み取る範囲の左上の位置。 */
const cropOrigin = computed(() => cropOriginLabel(crop.value, natural.value))

/** いまの作図方法の定義（案内を出す）。 */
const modeDefinitionOfNow = computed(() => modeDefinition(mode.value))
/** 作成する図の種類を選ばせるか（B はグラフ固定）。 */
const asksOutputType = computed(() => modeDefinitionOfNow.value.asksOutputType)
/** 実際に使う種類（B は GRAPH 固定）。 */
const effectiveOutputType = computed<FigureOutputType>(() =>
  asksOutputType.value ? outputType.value : 'GRAPH'
)
/** その種類で使う補充項目（単純な項目が先）。 */
const supplementFields = computed(() => supplementFieldsFor(mode.value, effectiveOutputType.value))
/** 単純な項目と上級の項目。 */
const simpleFields = computed(() => supplementFields.value.filter((field) => field.advanced !== true))
const advancedFields = computed(() => supplementFields.value.filter((field) => field.advanced === true))

/** 補足要求の表示（空なら「なし」）。 */
const noteLabel = computed(() => (note.value.trim() === '' ? 'なし' : note.value.trim()))

/** 補充の指定の表示（送る中身と同じものを作って見せる）。 */
const supplementRows = computed(() => {
  const payload = toSupplementPayload(mode.value, effectiveOutputType.value, {
    ...supplements.value,
    keepLabels: keepLabels.value
  })
  const rows: { label: string; value: string }[] = []
  for (const field of supplementFieldsFor(mode.value, effectiveOutputType.value)) {
    const value = (payload as Record<string, unknown>)[field.key]
    if (value === undefined || value === null || value === '') continue
    if (typeof value === 'boolean') {
      rows.push({ label: field.label, value: value ? 'する' : 'しない' })
      continue
    }
    const label = field.options?.find((option) => option.value === value)?.label ?? String(value)
    rows.push({ label: field.label, value: label })
  }
  rows.push({ label: '元の名前とラベル', value: keepLabels.value ? 'そのまま残す' : '分かりやすく変えてよい' })
  return rows
})

/** 送信前に確認する内容。 */
const summaryRows = computed(() => {
  const rows: { label: string; value: string }[] = [
    { label: '画像ファイル', value: `${fileName.value}（${fileSizeLabel.value}）` },
    { label: '読み取る範囲', value: `${cropOrigin.value}／${cropSize.value}` },
    { label: '作図方法', value: `${mode.value}: ${modeLabel(mode.value)}` },
    { label: '作成する図の種類', value: outputTypeLabel(effectiveOutputType.value) },
    { label: '補足要求', value: noteLabel.value }
  ]
  return rows
})

/* ---------- ステップの移動 ---------- */

function gotoStep(next: number): void {
  if (next < 1 || next > STEPS.length) return
  if (next > 1 && !hasImage.value) return
  step.value = next
}

function nextStep(): void {
  if (!canGoNext.value) return
  gotoStep(step.value + 1)
}

function backStep(): void {
  gotoStep(step.value - 1)
}

/* ---------- 画像の受け取り ---------- */

/** プレビューの object URL を解放する（差し替え・画面離脱で必ず呼ぶ）。 */
function releaseImageUrl(): void {
  if (imageUrl.value !== '') {
    URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = ''
  }
}

/**
 * 画像 1 枚を受け付ける（種類と大きさを確かめてからプレビューに出す）。
 *
 * <p>**1 枚だけ**を受け付ける。複数渡されたときは「1 枚を選んでください」と伝え、
 * いまの画像を**黙って置き換えない**。</p>
 */
function acceptImage(file: File | null | undefined, count = 1): void {
  imageError.value = ''
  if (count > 1) {
    imageError.value = '画像は 1 枚だけ使えます。1 枚を選んでください（いまの画像はそのままです）。'
    toast.danger(imageError.value)
    return
  }
  if (file === null || file === undefined) return
  if (!isAcceptedImage(file)) {
    imageError.value = 'PNG・JPEG・WebP の画像を選んでください。'
    toast.danger(imageError.value)
    return
  }
  if (file.size > imageMaxMb.value * 1024 * 1024) {
    imageError.value = `画像は ${imageMaxMb.value} MB までです。`
    toast.danger(imageError.value)
    return
  }
  // 前の画像を片付けてから入れ替える（object URL を溜めない）
  releaseImageUrl()
  pickedFile.value = file
  fileName.value = file.name
  fileSize.value = file.size
  crop.value = { ...CROP_ALL }
  cropPreview.value = ''
  natural.value = null
  imageUrl.value = URL.createObjectURL(file)
  loadNaturalSize(imageUrl.value)
}

/** 元画像の実寸を読む（読めなくても範囲指定は使える）。 */
function loadNaturalSize(url: string): void {
  const image = new Image()
  image.addEventListener('load', () => {
    natural.value = { width: image.naturalWidth, height: image.naturalHeight }
  })
  image.src = url
}

function pickImage(): void {
  fileInput.value?.click()
}

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = input.files
  acceptImage(files?.[0] ?? null, files?.length ?? 0)
  // 同じ画像をもう一度選べるように、input は毎回空にする
  input.value = ''
}

function onDrop(event: DragEvent): void {
  dropping.value = false
  const files = event.dataTransfer?.files
  acceptImage(files?.[0] ?? null, files?.length ?? 0)
}

/**
 * 貼り付け。
 *
 * <p>**画像があるときだけ**拾う（普通の文字の貼り付けは邪魔しない）。スクリーンショット
 * （Windows の PrintScreen / Snipping Tool）は画像として貼り付けられる。</p>
 */
function onPaste(event: ClipboardEvent): void {
  const items = event.clipboardData?.items
  if (items === undefined || items === null) return
  const images: File[] = []
  for (const item of items) {
    if (item.kind !== 'file') continue
    const file = item.getAsFile()
    if (file !== null && file.type.startsWith('image/')) images.push(file)
  }
  if (images.length === 0) return
  // 画像を貼ったときだけ横取りする（文字の貼り付けはそのまま通す）
  event.preventDefault()
  const named = images.map((file, index) =>
    file.name === '' || file.name === 'image.png'
      ? new File([file], index === 0 ? 'clipboard.png' : `clipboard-${index + 1}.png`, { type: file.type })
      : file
  )
  acceptImage(named[0] ?? null, named.length)
}

/* ---------- 臨時ファイルから選ぶ ---------- */

/** 臨時ファイルの小窓を開く（画像だけを出す）。 */
async function openTempFiles(): Promise<void> {
  tempOpen.value = true
  tempError.value = ''
  tempLoading.value = true
  try {
    const response = await getTempFiles({ type: '画像', limit: TEMP_FILE_LIMIT })
    tempFiles.value = response.data.filter((file) => file.image)
  } catch (cause) {
    tempError.value = messageOf(cause, '臨時ファイルの一覧を取得できませんでした。')
    tempFiles.value = []
  } finally {
    tempLoading.value = false
  }
}

/** 臨時ファイルのサムネイル（base64 なので data URL に直す）。 */
function tempThumbnail(file: TempFileSummary): string | null {
  if (!file.image || file.thumbnail === null || file.thumbnail === '') return null
  return file.thumbnail.startsWith('data:') ? file.thumbnail : `data:image/png;base64,${file.thumbnail}`
}

/** 臨時ファイルを画像として取り込む（実際のファイルとして読む）。 */
async function pickTempFile(file: TempFileSummary): Promise<void> {
  if (tempPicking.value) return
  tempPicking.value = true
  tempError.value = ''
  try {
    const response = await fetch(file.contentUrl)
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    const blob = await response.blob()
    const name = file.originalFileName === '' ? `temp-${file.tempFileId}.png` : file.originalFileName
    acceptImage(new File([blob], name, { type: blob.type === '' ? 'image/png' : blob.type }), 1)
    tempOpen.value = false
    toast.success('臨時ファイルから画像を取り込みました。')
  } catch {
    tempError.value = '臨時ファイルの画像を読み込めませんでした。別のファイルを選んでください。'
  } finally {
    tempPicking.value = false
  }
}

/* ---------- 読み取る範囲の指定 ---------- */

function startCropDrag(event: PointerEvent, handle: CropHandle | 'move'): void {
  const stage = cropStage.value
  if (stage === null || !hasImage.value) return
  const box = stage.getBoundingClientRect()
  if (box.width <= 0 || box.height <= 0) return
  event.preventDefault()
  cropDrag = {
    handle,
    startX: event.clientX,
    startY: event.clientY,
    width: box.width,
    height: box.height,
    rect: { ...crop.value }
  }
  cropDragging.value = true
  window.addEventListener('pointermove', onCropDragMove)
  window.addEventListener('pointerup', endCropDrag)
  window.addEventListener('pointercancel', endCropDrag)
}

function onCropDragMove(event: PointerEvent): void {
  const drag = cropDrag
  if (drag === null) return
  const dx = (event.clientX - drag.startX) / drag.width
  const dy = (event.clientY - drag.startY) / drag.height
  crop.value = drag.handle === 'move' ? moveCrop(drag.rect, dx, dy) : resizeCrop(drag.rect, drag.handle, dx, dy)
}

function endCropDrag(): void {
  cropDrag = null
  cropDragging.value = false
  window.removeEventListener('pointermove', onCropDragMove)
  window.removeEventListener('pointerup', endCropDrag)
  window.removeEventListener('pointercancel', endCropDrag)
}

/** 範囲を画像全体へ戻す。 */
function resetCrop(): void {
  crop.value = { ...CROP_ALL }
  cropPreview.value = ''
}

/** 中央を優先した範囲（図が中央にある問題集でよく使う形）。 */
function centerCrop(): void {
  crop.value = clampCrop({ x: 0.15, y: 0.15, w: 0.7, h: 0.7 })
  cropPreview.value = ''
}

/**
 * AI に送る画像（読み取る範囲）のサムネイルを作る。
 *
 * <p>canvas が使えない環境（テストなど）では作らずに空のままにする。</p>
 */
function buildCropPreview(): void {
  const url = imageUrl.value
  if (url === '' || natural.value === null) {
    cropPreview.value = ''
    return
  }
  const size = natural.value
  const image = new Image()
  image.addEventListener('load', () => {
    try {
      const canvas = document.createElement('canvas')
      const sx = Math.round(crop.value.x * size.width)
      const sy = Math.round(crop.value.y * size.height)
      const sw = Math.max(1, Math.round(crop.value.w * size.width))
      const sh = Math.max(1, Math.round(crop.value.h * size.height))
      canvas.width = sw
      canvas.height = sh
      const context = canvas.getContext('2d')
      if (context === null) {
        cropPreview.value = ''
        return
      }
      context.drawImage(image, sx, sy, sw, sh, 0, 0, sw, sh)
      cropPreview.value = canvas.toDataURL('image/png')
    } catch {
      cropPreview.value = ''
    }
  })
  image.src = url
}

// 確認のステップへ来たら、読み取る範囲のプレビューを用意する
watch(step, (value) => {
  if (value === 4) buildCropPreview()
})

// モードを切り替えたら、上級の項目は畳み直す（項目の並びが変わるため）
watch(mode, () => {
  advancedOpen.value = false
})

/* ---------- 送信 ---------- */

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

/** 質問への回答を補足要求に足す文を作る（追加入力待ちの再開で使う）。 */
function answersText(): string {
  const current = resumeRequest.value
  if (current === null || current.questions.length === 0) return ''
  const lines: string[] = []
  for (const question of current.questions) {
    const answer = (answers.value[question.id ?? question.question] ?? '').trim()
    if (answer !== '') {
      lines.push(`- ${question.question} → ${answer}`)
    }
  }
  return lines.length === 0 ? '' : `AI からの質問への回答:\n${lines.join('\n')}`
}

/** 送る補足要求（質問への回答があれば先に付ける）。 */
function noteForSend(): string {
  const answer = answersText()
  const base = note.value.trim()
  const combined = [answer, base].filter((part) => part !== '').join('\n')
  return combined.slice(0, NOTE_MAX)
}

/** 【AI に送る】: 画像を上げて要求を作る（処理はバックエンドが実行する）。 */
async function send(): Promise<void> {
  const file = pickedFile.value
  if (sending.value) return
  if (file === null) {
    // 送り直しのときに元画像を読み直せなかった場合もここへ来る（黙って何もしないでほしくない）
    sendError.value = '画像が読み込まれていません。画像を選び直してください。'
    toast.danger(sendError.value)
    return
  }
  if (!canSend.value) {
    sendError.value = optionsNotice.value === ''
      ? 'AI 生図は現在ご利用いただけません。' : optionsNotice.value
    toast.danger(sendError.value)
    return
  }
  sending.value = true
  sendError.value = ''
  try {
    const cropValues = {
      x: Number(crop.value.x.toFixed(5)),
      y: Number(crop.value.y.toFixed(5)),
      w: Number(crop.value.w.toFixed(5)),
      h: Number(crop.value.h.toFixed(5))
    }
    // 当てはまらない項目は入れない（モードを切り替えたときの残りを送らない）
    const supplementValues = toSupplementPayload(mode.value, effectiveOutputType.value, {
      ...supplements.value,
      keepLabels: keepLabels.value
    })
    const resuming = resumeRequest.value
    if (resuming !== null) {
      // 送り直し: **行を増やさない**（画像はもうサーバーにある。条件だけ更新して実行待ちに戻す）
      const resent = await resubmitGeometryAiRequest(resuming.requestId, {
        crop: cropValues,
        mode: mode.value,
        resultType: effectiveOutputType.value,
        supplements: supplementValues,
        note: noteForSend(),
        version: resuming.version
      })
      toast.success(resent.message === '' ? '内容を直して送り直しました。' : resent.message)
      await router.push({ path: `/${area.value}/geometry` })
      return
    }
    // WebP はここで PNG に変換する（サーバーが読めない形式のため）
    const uploadable = await toUploadableImage(file)
    const uploaded = await uploadGeometryAiImage(uploadable)
    const created = await createGeometryAiRequest({
      imageToken: uploaded.data.imageToken,
      crop: cropValues,
      mode: mode.value,
      resultType: effectiveOutputType.value,
      supplements: supplementValues,
      note: noteForSend()
    })
    toast.success(created.message === '' ? 'AI 生図を受け付けました。' : created.message)
    // **この画面では待たない**。処理はバックエンドが進め、一覧にタスクのカードが出る
    await router.push({ path: `/${area.value}/geometry` })
  } catch (cause) {
    const message = messageOf(cause, 'AI 生図を送信できませんでした。')
    sendError.value = message
    toast.danger(message)
    sending.value = false
  }
}

/* ---------- 送り直し（追加入力待ち・失敗）の復元 ---------- */

/** `?requestId=` の値（数字だけ。無ければ null）。 */
function resumeRequestId(): number | null {
  const raw = route.query.requestId
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value !== 'string' || value.trim() === '') return null
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

/**
 * 前の要求を読み込んで、画像と選んだ条件を復元する。
 *
 * <p>画像は**保存済みの元画像**を読み直す（もう一度選ばせない）。読み取る範囲・作図方法・
 * 作成する図の種類・補充・補足要求も戻す。AI からの質問は画面に出し、回答を書けるようにする。</p>
 */
async function loadResume(requestId: number): Promise<void> {
  resuming.value = true
  resumeNotice.value = ''
  try {
    const response = await fetchGeometryAiRequest(requestId)
    const detail = response.data
    resumeRequest.value = detail
    mode.value = (detail.mode ?? 'A') as FigureMode
    outputType.value = (detail.requestedOutputType ?? 'AUTO') as FigureOutputType
    note.value = detail.note ?? ''
    const stored = fromSupplementRows(detail.supplements ?? {})
    supplements.value = {
      ...supplements.value,
      ...defaultSupplementValues(mode.value, effectiveOutputType.value),
      ...stored,
      keepLabels: true
    }
    keepLabels.value = stored.keepLabels !== false
    if (detail.crop !== null && detail.crop !== undefined) {
      crop.value = clampCrop({
        x: detail.crop.x, y: detail.crop.y, w: detail.crop.w, h: detail.crop.h
      })
    }
    // 元画像を実際のファイルとして読み直す（アップロードし直せる形にする）
    const imageResponse = await fetch(
      `/api/user/geometry/ai/requests/${requestId}/image?kind=original&v=${detail.version}`
    )
    if (!imageResponse.ok) {
      throw new Error(`HTTP ${imageResponse.status}`)
    }
    const blob = await imageResponse.blob()
    acceptImage(new File([blob], `ai-request-${requestId}.png`, { type: blob.type || 'image/png' }), 1)
    // 読み取る範囲は保存してあった値に戻す（acceptImage が全体に戻すため、あとで上書きする）
    if (detail.crop !== null && detail.crop !== undefined) {
      crop.value = clampCrop({
        x: detail.crop.x, y: detail.crop.y, w: detail.crop.w, h: detail.crop.h
      })
    }
    resumeNotice.value = detail.questions.length > 0
      ? `AI から ${detail.questions.length} 件の質問があります。回答や条件を直して、もう一度【AI に送る】を押してください。`
      : `${detail.requestNo} の内容を復元しました。条件を直して、もう一度【AI に送る】を押してください。`
  } catch (cause) {
    resumeNotice.value = messageOf(cause, '前の内容を読み込めませんでした。画像を選び直してください。')
    toast.danger(resumeNotice.value)
  } finally {
    resuming.value = false
  }
}

/* ---------- 画面遷移 ---------- */

function backToList(): void {
  void router.push({ path: `/${area.value}/geometry` })
}

onBeforeUnmount(() => {
  endCropDrag()
  releaseImageUrl()
})

onMounted(async () => {
  window.addEventListener('paste', onPaste)
  try {
    const response = await fetchGeometryAiOptions()
    options.value = response.data
    // 設定の既定（読み取る範囲・作図方法）を画面の初期値にする
    if (response.data.defaultCrop === 'center') crop.value = clampCrop({ x: 0.15, y: 0.15, w: 0.7, h: 0.7 })
    supplements.value = { ...supplements.value, ...defaultSupplementValues(mode.value, outputType.value) }
  } catch (cause) {
    // 設定が読めないときは「使えない」扱い（存在しない API を叩き続けない）
    options.value = null
    sendError.value = messageOf(cause, 'AI 生図の設定を読み込めませんでした。')
  }
  const requestId = resumeRequestId()
  if (requestId !== null) {
    await loadResume(requestId)
  }
})
</script>

<template>
  <div class="gm-page gm-ai-page" :data-gm-ai-step="step" :data-gm-ai-mode="mode">
    <!-- 使えないときの案内（設定で無効・設定が未投入・日次上限） -->
    <p v-if="optionsNotice !== ''" class="gm-ai-notice" data-gm-ai-notice>
      <AppIcon name="info" size="sm" />
      <span>{{ optionsNotice }}</span>
    </p>
    <p v-else class="gm-ai-notice" data-gm-ai-notice>
      <AppIcon name="wand" size="sm" />
      <span>
        画像から <strong>GeoGebra の作図</strong> を作ります。AI が作ったコマンドは
        <strong>作図画面で確認・調整してから</strong> 保存します（画像は外部の AI サービスへ送信されます。
        氏名や学校名などが写らないようにしてください）。
      </span>
    </p>

    <!-- 追加入力待ち・失敗の再開 -->
    <section v-if="resumeRequest !== null || resuming" class="gm-ai-resume" data-gm-ai-resume>
      <p class="gm-ai-resume__head">
        <AppIcon name="rotate" size="sm" />
        <span>{{ resuming ? '前の内容を読み込んでいます...' : `前の AI 生図（${resumeRequest?.requestNo}）を送り直します` }}</span>
      </p>
      <p v-if="resumeNotice !== ''" class="gm-hint" data-gm-ai-resume-note>{{ resumeNotice }}</p>
      <div v-if="resumeRequest !== null && resumeRequest.questions.length > 0" class="gm-ai-questions" data-gm-ai-questions>
        <h4 class="gm-ai-questions__title">AI からの質問</h4>
        <div v-for="(question, index) in resumeRequest.questions" :key="question.id ?? index" class="gm-ai-question">
          <p class="gm-ai-question__text" :data-gm-ai-question="question.id ?? index">
            <span class="gm-ai-question__no">Q{{ index + 1 }}</span>{{ question.question }}
          </p>
          <select
            v-if="question.options.length > 0"
            v-model="answers[question.id ?? question.question]" class="select"
            :data-gm-ai-answer="question.id ?? index"
          >
            <option value="">（選んでください）</option>
            <option v-for="option in question.options" :key="option" :value="option">{{ option }}</option>
          </select>
          <input
            v-else
            v-model="answers[question.id ?? question.question]" class="input" type="text"
            placeholder="回答を入力してください" :data-gm-ai-answer="question.id ?? index"
          >
        </div>
        <p class="gm-hint">回答は補足要求としてまとめて AI へ送られます。</p>
      </div>
    </section>

    <section class="card">
      <div class="card__header gm-list-head">
        <h2 class="card__title"><AppIcon name="wand" size="sm" /> AI 生図（画像から作図）</h2>
        <span class="badge badge--neutral" data-gm-ai-mode-badge>{{ mode }}: {{ modeLabel(mode) }}</span>
        <div class="gm-list-head__spacer"></div>
        <div class="search-panel__actions">
          <button type="button" class="btn btn--secondary" data-gm-ai-back-list @click="backToList">
            <AppIcon name="chevron-left" size="sm" /> 一覧へ戻る
          </button>
        </div>
      </div>

      <!-- ステップの案内（1〜4）。1 に戻らないと画像を選び直せない -->
      <ol class="gm-ai-steps" data-gm-ai-steps>
        <li v-for="item in STEPS" :key="item.id" class="gm-ai-steps__item">
          <button
            type="button" class="gm-ai-step" :class="{ 'is-active': step === item.id, 'is-done': step > item.id }"
            :data-gm-ai-step-link="item.id" :disabled="item.id > 1 && !hasImage"
            :aria-current="step === item.id ? 'step' : undefined" @click="gotoStep(item.id)"
          >
            <span class="gm-ai-step__no">{{ item.id }}</span>
            <AppIcon :name="item.icon" size="sm" />
            <span class="gm-ai-step__label">{{ item.label }}</span>
          </button>
        </li>
      </ol>

      <div class="gm-ai-body">
        <!-- 1. 画像を追加 -->
        <section v-if="step === 1" class="gm-ai-panel" data-gm-ai-panel="upload">
          <h3 class="gm-ai-panel__title"><AppIcon name="upload" size="sm" /> 画像を追加</h3>
          <p class="gm-hint">
            作図したい図・式・問題文が写っている画像を 1 枚だけ追加します（PNG・JPEG・WebP、{{
              imageMaxMb }} MB まで）。
            ファイルを選ぶ・ドラッグする・貼り付ける（スクリーンショットも可）・臨時ファイルから選ぶ、の
            どれでも使えます。WebP は送信前に PNG へ変換します。
          </p>

          <!-- 枠のどこを押しても画像を選べる（書籍管理のドラッグ＆ドロップと同じ作り） -->
          <div
            class="dropzone gm-ai-drop" :class="{ 'is-dragging': dropping }" data-gm-ai-drop
            @click="pickImage" @dragover.prevent="dropping = true" @dragleave="dropping = false"
            @drop.prevent="onDrop"
          >
            <input
              ref="fileInput" type="file" :accept="IMAGE_ACCEPT" class="gm-ai-drop__input"
              aria-label="画像を選ぶ" data-gm-ai-file @change="onFileChange"
            >
            <AppIcon name="image" size="lg" />
            <p class="gm-ai-drop__title">ここに画像をドラッグ＆ドロップ</p>
            <p class="gm-ai-drop__note">
              または、この枠をクリックして画像を選びます。画像をコピーして <kbd>Ctrl</kbd>+<kbd>V</kbd> でも
              貼り付けられます。
            </p>
            <div class="gm-ai-drop__actions">
              <button type="button" class="btn btn--secondary btn--sm" data-gm-ai-pick @click.stop="pickImage">
                <AppIcon name="folder" size="sm" /> 画像を選ぶ
              </button>
              <button
                type="button" class="btn btn--secondary btn--sm" data-gm-ai-temp-open
                @click.stop="openTempFiles"
              >
                <AppIcon name="file" size="sm" /> 臨時ファイルから選ぶ
              </button>
            </div>
          </div>

          <p v-if="imageError !== ''" class="alert alert--danger" data-gm-ai-file-error>{{ imageError }}</p>

          <div v-if="hasImage" class="gm-ai-picked" data-gm-ai-picked>
            <img class="gm-ai-picked__thumb" :src="imageUrl" alt="選んだ画像" data-gm-ai-picked-img>
            <div class="gm-ai-picked__info">
              <p class="gm-ai-picked__name" data-gm-ai-file-name>{{ fileName }}</p>
              <p class="gm-hint" data-gm-ai-file-size>大きさ {{ fileSizeLabel }}</p>
              <p class="gm-hint" data-gm-ai-file-natural>
                <template v-if="natural !== null">
                  元画像 {{ natural.width }} × {{ natural.height }} px
                </template>
                <template v-else>元画像の大きさを読み取れませんでした（範囲は割合で指定します）。</template>
              </p>
            </div>
          </div>
        </section>

        <!-- 2. 読み取る範囲を指定 -->
        <section v-else-if="step === 2" class="gm-ai-panel" data-gm-ai-panel="crop">
          <h3 class="gm-ai-panel__title"><AppIcon name="crop" size="sm" /> 読み取る範囲を指定</h3>
          <p class="gm-hint">
            読み取らせたい部分だけを AI に送ります。枠の内側をドラッグすると移動、8 つの白い点を
            ドラッグすると大きさが変わります。柱や余白、別の問題が写っている部分は外してください。
          </p>

          <div class="gm-ai-crop">
            <div class="gm-ai-crop__stage" :class="{ 'is-dragging': cropDragging }" data-gm-ai-crop-stage>
              <!-- 範囲の枠は「画像の大きさ」を基準に置く（余白のぶんずれないよう内側の枠で包む） -->
              <div ref="cropStage" class="gm-ai-crop__canvas" data-gm-ai-crop-canvas>
                <img class="gm-ai-crop__image" :src="imageUrl" alt="範囲指定前の画像" data-gm-ai-crop-img>
                <div
                  class="gm-ai-crop__box" :style="cropStyle(crop)" data-gm-ai-crop-box
                  @pointerdown="startCropDrag($event, 'move')"
                >
                  <span
                    v-for="handle in CROP_HANDLES" :key="handle" class="gm-ai-crop__handle"
                    :class="`gm-ai-crop__handle--${handle}`" :data-gm-ai-crop-handle="handle"
                    @pointerdown.stop="startCropDrag($event, handle)"
                  ></span>
                </div>
              </div>
            </div>

            <div class="gm-ai-crop__side">
              <dl class="gm-ai-rows">
                <div class="gm-ai-rows__row">
                  <dt>読み取る範囲の大きさ</dt>
                  <dd data-gm-ai-crop-size>{{ cropSize }}</dd>
                </div>
                <div class="gm-ai-rows__row">
                  <dt>読み取る範囲の位置</dt>
                  <dd data-gm-ai-crop-origin>{{ cropOrigin }}</dd>
                </div>
              </dl>
              <div class="search-panel__actions">
                <button
                  type="button" class="btn btn--secondary btn--sm" :disabled="fullCrop"
                  data-gm-ai-crop-all @click="resetCrop"
                >
                  <AppIcon name="image" size="sm" /> 全体を使う
                </button>
                <button type="button" class="btn btn--secondary btn--sm" data-gm-ai-crop-center @click="centerCrop">
                  <AppIcon name="crop" size="sm" /> 中央を優先
                </button>
              </div>
              <!-- モードごとに「何を残すか」を案内する（切り抜きの失敗がいちばん多いため） -->
              <p class="gm-ai-crop__hint" data-gm-ai-crop-hint>
                <AppIcon name="info" size="sm" />
                <span><strong>{{ mode }}: {{ modeLabel(mode) }}</strong> では {{ modeDefinitionOfNow.cropHint }}</span>
              </p>
              <p class="gm-hint">
                読み取る範囲の部分だけが AI に送られます（元画像はサーバーに保存され、30 日後に自動で削除されます）。
              </p>
            </div>
          </div>
        </section>

        <!-- 3. 作図方法を選択 -->
        <section v-else-if="step === 3" class="gm-ai-panel" data-gm-ai-panel="mode">
          <h3 class="gm-ai-panel__title"><AppIcon name="filter" size="sm" /> 作図方法を選択</h3>
          <p class="gm-hint">
            作図方法は「入力をどう読むか」、作成する図の種類は「何を作るか」を決めます。
            1 回の送信で実行するのは、選んだ 1 つの処理だけです。
          </p>

          <fieldset class="gm-ai-fieldset">
            <legend class="field__label">作図方法（必須）</legend>
            <div class="gm-ai-modes" data-gm-ai-modes>
              <label
                v-for="definition in MODE_DEFINITIONS" :key="definition.mode" class="gm-ai-mode"
                :class="{ 'is-active': mode === definition.mode }" :data-gm-ai-mode-option="definition.mode"
              >
                <input v-model="mode" type="radio" name="gm-ai-mode" :value="definition.mode">
                <span class="gm-ai-mode__body">
                  <span class="gm-ai-mode__head">
                    <span class="gm-ai-mode__badge">{{ definition.mode }}</span>
                    <span class="gm-ai-mode__label">{{ definition.label }}</span>
                  </span>
                  <span class="gm-ai-mode__help">{{ definition.description }}</span>
                </span>
              </label>
            </div>
          </fieldset>

          <!-- 作成する図の種類（A・C・D は必須。B は GRAPH 固定なので出さない） -->
          <fieldset v-if="asksOutputType" class="gm-ai-fieldset" data-gm-ai-output-type>
            <legend class="field__label">作成する図の種類（必須）</legend>
            <label
              v-for="option in OUTPUT_TYPE_OPTIONS" :key="option.value" class="gm-ai-radio"
              :data-gm-ai-output-type-option="option.value"
            >
              <input
                v-model="outputType" type="radio" name="gm-ai-output-type" :value="option.value"
              >
              <span class="gm-ai-radio__body">
                <span class="gm-ai-radio__label">{{ option.label }}</span>
                <span class="gm-ai-radio__help">{{ option.description }}</span>
              </span>
            </label>
            <p class="gm-hint" data-gm-ai-output-type-help>
              指定した種類に合わない内容だったときは、勝手に種類を変えずに確認を求めます。
            </p>
          </fieldset>
          <p v-else class="gm-ai-fixed-type" data-gm-ai-output-type-fixed>
            <AppIcon name="info" size="sm" />
            <span>「数式からグラフを作成」は<strong>関数・方程式のグラフ</strong>に固定です（種類は選べません）。</span>
          </p>

          <!-- モードごとの補充（単純な項目が先・上級は畳む） -->
          <div class="gm-ai-form">
            <div v-for="field in simpleFields" :key="field.key" class="gm-form__row">
              <span class="field__label">{{ field.label }}</span>
              <div v-if="field.type === 'radio'" class="gm-ai-radio-group">
                <label
                  v-for="option in field.options ?? []" :key="option.value" class="gm-ai-radio gm-ai-radio--inline"
                  :data-gm-ai-supplement-option="`${field.key}:${option.value}`"
                >
                  <input v-model="supplements[field.key]" type="radio" :name="`gm-ai-${field.key}`" :value="option.value">
                  <span class="gm-ai-radio__body"><span class="gm-ai-radio__label">{{ option.label }}</span></span>
                </label>
              </div>
              <input
                v-else-if="field.type === 'text'" v-model="supplements[field.key]" class="input" type="text"
                :data-gm-ai-supplement="field.key"
              >
              <textarea
                v-else v-model="supplements[field.key]" class="input" rows="2"
                :data-gm-ai-supplement="field.key"
              ></textarea>
              <p v-if="field.help !== undefined" class="gm-hint">{{ field.help }}</p>
            </div>

            <!-- 上級の項目（必要な人だけ開く） -->
            <div v-if="advancedFields.length > 0" class="gm-ai-advanced" data-gm-ai-advanced>
              <button
                type="button" class="gm-ai-advanced__toggle" data-gm-ai-advanced-toggle
                :aria-expanded="advancedOpen" @click="advancedOpen = !advancedOpen"
              >
                <AppIcon :name="advancedOpen ? 'chevron-down' : 'chevron-right'" size="sm" />
                上級の指定（{{ advancedFields.length }} 件・任意）
              </button>
              <div v-if="advancedOpen" class="gm-ai-advanced__body">
                <div v-for="field in advancedFields" :key="field.key" class="gm-form__row">
                  <span class="field__label">{{ field.label }}</span>
                  <input
                    v-if="field.type === 'text'" v-model="supplements[field.key]" class="input" type="text"
                    :data-gm-ai-supplement="field.key"
                  >
                  <textarea
                    v-else v-model="supplements[field.key]" class="input" rows="2"
                    :data-gm-ai-supplement="field.key"
                  ></textarea>
                  <p v-if="field.help !== undefined" class="gm-hint">{{ field.help }}</p>
                </div>
              </div>
            </div>

            <div class="gm-form__row">
              <label class="field__label" for="gm-ai-note">補足要求（任意）</label>
              <textarea
                id="gm-ai-note" v-model="note" class="input" rows="3" :maxlength="NOTE_MAX"
                placeholder="例: 点の名前は A・B・C のままにしてください。"
                data-gm-ai-note
              ></textarea>
              <p class="gm-hint">
                {{ NOTE_MAX }} 文字まで。すべてを埋める必要はありません（空欄は既定の動きになります）。
              </p>
            </div>

            <div class="gm-form__row">
              <span class="field__label">元の名前とラベル</span>
              <label class="gm-ai-check" data-gm-ai-keep-labels>
                <input v-model="keepLabels" type="checkbox">
                <span>元の名前・ラベルをそのまま残す（既定）</span>
              </label>
              <p class="gm-hint">外すと、分かりやすい名前に変えてよいことになります。</p>
            </div>
          </div>
        </section>

        <!-- 4. 内容を確認して送信 -->
        <section v-else class="gm-ai-panel" data-gm-ai-panel="confirm">
          <h3 class="gm-ai-panel__title"><AppIcon name="check-square" size="sm" /> 内容を確認して送信</h3>
          <p class="gm-hint">
            この内容で AI に送ります。【AI に送る】を押すと図形管理の一覧へ戻り、
            処理の進み具合がタスクとして表示されます（画面を閉じても処理は続きます）。
          </p>

          <div class="gm-ai-confirm">
            <div class="gm-ai-confirm__preview">
              <img
                v-if="cropPreview !== ''" class="gm-ai-confirm__thumb" :src="cropPreview"
                alt="AI に送る画像（読み取る範囲）" data-gm-ai-crop-preview
              >
              <div v-else class="gm-ai-confirm__empty" data-gm-ai-crop-preview-empty>
                <AppIcon name="crop" size="lg" />
                <span>読み取る範囲のプレビューはここに出ます。</span>
              </div>
            </div>

            <div class="gm-ai-confirm__side">
              <dl class="gm-ai-rows" data-gm-ai-summary>
                <div v-for="row in summaryRows" :key="row.label" class="gm-ai-rows__row">
                  <dt>{{ row.label }}</dt>
                  <dd :data-gm-ai-summary-row="row.label">{{ row.value }}</dd>
                </div>
              </dl>
              <h4 class="gm-ai-confirm__subtitle">補充の指定（AI へ渡す内容）</h4>
              <dl class="gm-ai-rows" data-gm-ai-supplements>
                <div v-for="row in supplementRows" :key="row.label" class="gm-ai-rows__row">
                  <dt>{{ row.label }}</dt>
                  <dd>{{ row.value }}</dd>
                </div>
              </dl>
              <p class="gm-hint">
                コマンド（GeoGebra の命令）は普通の利用者が入力する必要はありません。設定と AI が作り、
                作図画面で確認できます。
              </p>
            </div>
          </div>

          <p v-if="sendError !== ''" class="alert alert--danger" data-gm-ai-send-error>{{ sendError }}</p>

          <div class="search-panel__actions">
            <button
              type="button" class="btn btn--primary" :disabled="sending || !canSend" data-gm-ai-send @click="send"
            >
              <AppIcon name="wand" size="sm" /> {{ sending ? '送信しています...' : 'AI に送る' }}
            </button>
            <button type="button" class="btn btn--secondary" :disabled="sending" data-gm-ai-back-step @click="backStep">
              <AppIcon name="chevron-left" size="sm" /> 戻る
            </button>
          </div>
        </section>
      </div>

      <!-- ステップの移動（確認のステップでは【AI に送る】の隣に戻るを出す） -->
      <div v-if="step < 4" class="gm-ai-foot">
        <button type="button" class="btn btn--secondary" :disabled="step === 1" data-gm-ai-prev @click="backStep">
          <AppIcon name="chevron-left" size="sm" /> 戻る
        </button>
        <button type="button" class="btn btn--primary" :disabled="!canGoNext" data-gm-ai-next @click="nextStep">
          次へ <AppIcon name="chevron-right" size="sm" />
        </button>
      </div>
    </section>

    <!-- 臨時ファイルから選ぶ（小窓） -->
    <Teleport to="body">
      <div v-if="tempOpen" class="overlay gm-ai-temp-overlay" data-gm-ai-temp-dialog>
        <section class="dialog dialog--lg gm-ai-temp" role="dialog" aria-modal="true" aria-label="臨時ファイルから選ぶ">
          <header class="dialog__head">
            <h2 class="dialog__title"><AppIcon name="file" size="sm" /> 臨時ファイルから選ぶ</h2>
            <button type="button" class="dialog__close" aria-label="閉じる" data-gm-ai-temp-close @click="tempOpen = false">
              <AppIcon name="x" size="sm" />
            </button>
          </header>
          <div class="dialog__body">
            <p class="gm-hint">臨時ファイルにある画像だけを出しています（1 枚だけ選べます）。</p>
            <p v-if="tempLoading" class="gm-page__loading">読み込んでいます...</p>
            <p v-else-if="tempError !== ''" class="alert alert--danger" data-gm-ai-temp-error>{{ tempError }}</p>
            <p v-else-if="tempFiles.length === 0" class="gm-page__empty" data-gm-ai-temp-empty>
              画像の臨時ファイルがありません。
            </p>
            <div v-else class="gm-ai-temp__grid">
              <button
                v-for="file in tempFiles" :key="file.tempFileId" type="button" class="gm-ai-temp__item"
                :data-gm-ai-temp-item="file.tempFileId" :disabled="tempPicking" @click="pickTempFile(file)"
              >
                <img
                  v-if="tempThumbnail(file) !== null" :src="tempThumbnail(file) ?? ''"
                  :alt="file.originalFileName"
                >
                <span v-else class="gm-ai-temp__noimg"><AppIcon name="image" size="lg" /></span>
                <span class="gm-ai-temp__name">{{ file.originalFileName }}</span>
              </button>
            </div>
          </div>
        </section>
      </div>
    </Teleport>
  </div>
</template>
