/**
 * 英作文の保管と AI 添削（**本物の API**）。
 *
 * <p>2026-09-27 までは純前端（localStorage ＋ 見本データ）だった。DB（`ENG_*`）と API が
 * そろったので、ここで API に繋ぎ替える。画面はこのモジュールだけを見る
 * （`src/api/english-essay.ts` を直接触るのはこのファイルだけ）。</p>
 *
 * <ul>
 *   <li>作文と画像の保管・添削の**受付**・**同期 OCR** ＝ user-api（**要ログイン**。作文は自分のものだけ）</li>
 *   <li>添削の実行＝ 働き手（画面は受付けてから様子を見る）。admin-api は画面から直接叩かない</li>
 * </ul>
 *
 * <p>2.0 は `STY_英作文情報` に 1 行・添削結果は 1 列（上書き）だった。2.1 では
 * 「**何度も添削して、历次を切り替えて見る**」が要件なので、添削結果は作文ごとの**配列**で持つ。</p>
 *
 * <p>戻りはすべて非同期。一覧は {@link essayList}（`ref`）に載せるので、画面はそれを読む。</p>
 */

import { ref } from 'vue'
import { ApiError } from '@study21/web-shared'
import {
  createEnglishEssay,
  deleteEnglishEssay,
  englishEssayImageUrl,
  fetchEnglishEssay,
  fetchEnglishEssayLimits,
  recognizeEssayImages,
  requestEssayGrading,
  searchEnglishEssays,
  updateEnglishEssay,
  uploadEnglishEssayImage,
  type EnglishEssayDetail,
  type EnglishEssayGrading,
  type EnglishEssayImage,
  type EnglishEssayImageOrder,
  type EnglishEssayLimits,
  type EnglishEssayListItem,
  type EssayImageUpload,
  type EssayOcrImage,
  type EssayOcrResult
} from '@/api/english-essay'
import { countWords, type EssayGrading, type EssayLevel } from './types'
import { normalizeGradingReport } from './report'

/**
 * 一覧で選べる 1 ページの件数（`docs/FRONTEND_GUIDE.md` §3.10 の標準。既定は先頭の 20）。
 *
 * <p>件数は**検索条件ではなくページングの左隣**に置き、選んだ値はそのまま API の `size` に送る。</p>
 */
export const ESSAY_PAGE_SIZES = [20, 50, 100] as const

/** 既定の 1 ページの件数。 */
export const ESSAY_PAGE_SIZE: number = ESSAY_PAGE_SIZES[0]

/** 画像の区分（利用者が 1 枚ずつ指定する）。 */
export type EssayImageCategory = 'question' | 'answer'

/** 作文に添える画像。 */
export interface EssayImage {
  /** 画像 ID（API の `imageId`）。 */
  id: string
  category: EssayImageCategory
  fileName: string
  /**
   * 画面に出す画像。
   *
   * <p>サーバーにある画像は API の URL（`<img :src>` にそのまま使える）、
   * まだ上げていない画像はデータ URL（トリミング直後の内容）。</p>
   */
  dataUrl: string
  /** 並び順（1 から）。 */
  order: number
}

/** 英作文 1 件。 */
export interface Essay {
  id: string
  level: EssayLevel
  /** 題（日本語）。2.0 の `タイトル_日本語`。 */
  title: string
  /** 題（中文）。2.0 の `タイトル_中国語`。未設定は空。 */
  titleZh?: string
  questionText: string
  essayText: string
  wordCount: number
  images: EssayImage[]
  /**
   * **成功した**添削の历次（古い順に積む。レポートの中身を持つ）。
   *
   * <p>回の切り替えは {@link EssayDetail.rounds}（失敗・実行中も含む）を使う。こちらは
   * 成功した回の**中身**だけを持ち、成功した回を数えた位置は API の `round` と一致しない
   * （失敗した回も 1 行として残るため）。</p>
   */
  gradings: EssayGrading[]
  /** A = 有効 / X = 削除（論理削除。2.0 と同じ）。 */
  state: 'A' | 'X'
  createdAt: string
  updatedAt: string
}

/** 一覧の絞り込み（2.0 の検索条件と同じ 3 つ）。 */
export interface EssayFilter {
  keyword?: string
  level?: EssayLevel | ''
  dateFrom?: string
  dateTo?: string
}

/** 新規作成の入力（画像は作成後に 1 枚ずつ上げる）。 */
export interface EssayInput {
  level: EssayLevel
  title: string
  /** 題（中文）。未指定は空。 */
  titleZh?: string
  questionText?: string
  essayText?: string
}

/** 更新の入力（本文と、**残す画像**の一覧＝並びと区分。消した画像は含めない）。 */
export interface EssaySaveInput {
  level: EssayLevel
  title: string
  titleZh: string
  questionText: string
  essayText: string
  images: EnglishEssayImageOrder[]
}

/** 一覧の 1 行（一覧 API の形のまま。画像の実体は返らないので件数だけ持つ）。 */
export interface EssayListItem {
  id: string
  level: EssayLevel
  title: string
  titleZh: string
  questionText: string
  essayText: string
  wordCount: number
  imageCount: number
  questionImageCount: number
  answerImageCount: number
  createdAt: string
  updatedAt: string
  latestGrading: EssayGradingSummary | null
}

/** 一覧の 1 行に付く最新の添削（中身は返らない。状態と得点だけ）。 */
export interface EssayGradingSummary {
  gradingId: string
  round: number
  statusCode: string
  score: number | null
  maxScore: number | null
  createdAt: string
}

/** 一覧の状態（得点／状態の列の出し分けに使う）。 */
export type EssayListState = 'NONE' | 'PENDING' | 'SUCCEEDED' | 'FAILED'

/** 添削 1 回ぶんの状態（成功・実行中・失敗のすべてを含む）。 */
export interface EssayGradingRound {
  gradingId: string
  round: number
  statusCode: string
  score: number | null
  maxScore: number | null
  failureReason: string | null
  createdAt: string
  /** **成功した回だけ**、画面が使うレポート。未完了・失敗は null。 */
  report: EssayGrading | null
}

/** 詳細（作文 ＋ 添削の回の状態）。 */
export interface EssayDetail {
  essay: Essay
  /** 添削の回（古い順。すべての状態を含む）。 */
  rounds: EssayGradingRound[]
}

/* ---------- 一覧のキャッシュ（画面はこれを読む） ---------- */

/** いまの一覧（読み直すと入れ替わる）。 */
export const essayList = ref<EssayListItem[]>([])

/** 絞り込みに一致する総件数（サーバーが数える）。 */
export const essayTotal = ref(0)

/** 総ページ数（サーバーが数える）。 */
export const essayTotalPages = ref(1)

/**
 * 一覧を読む（絞り込み・ページングはサーバー側）。
 *
 * <p>空の条件は送らない（URL に `keyword=` が並ぶと、サーバー側の「未指定」と区別できない）。</p>
 */
export async function loadEssays(
  filter: EssayFilter = {},
  page = 1,
  size = ESSAY_PAGE_SIZE
): Promise<void> {
  const response = await searchEnglishEssays({
    keyword: textOrUndefined(filter.keyword),
    level: filter.level ? filter.level : undefined,
    dateFrom: textOrUndefined(filter.dateFrom),
    dateTo: textOrUndefined(filter.dateTo),
    page,
    size
  })
  essayList.value = (response.data.items ?? []).map(listItemOf)
  essayTotal.value = response.data.total
  essayTotalPages.value = Math.max(1, response.data.totalPages)
}

/** 一覧のキャッシュを空にする（読み込みに失敗したとき・ログアウトしたとき）。 */
export function clearEssayList(): void {
  essayList.value = []
  essayTotal.value = 0
  essayTotalPages.value = 1
}

/* ---------- 画像の上限（サーバーが決める） ---------- */

/**
 * 画像の上限（枚数・1 枚の大きさ）。まだ取れていない・取れなかったときは `null`。
 *
 * <p>`null` は「不明」の意味で、画面は**何も制限しない**（超えていればサーバーが日本語の理由を
 * 返す）。`8` や `10MB` を画面に書き写すと、サーバーの設定を変えたときにずれる。</p>
 */
export const essayLimits = ref<EnglishEssayLimits | null>(null)

/**
 * 画像の上限を読む（**取れなくても操作は止めない**）。
 *
 * <p>取れなかったときは `null` にして、例外は投げない（添削そのものは続けられる）。</p>
 */
export async function loadEssayLimits(): Promise<EnglishEssayLimits | null> {
  try {
    const response = await fetchEnglishEssayLimits()
    essayLimits.value = limitsOf(response.data)
  } catch {
    essayLimits.value = null
  }
  return essayLimits.value
}

/** API の値を検める（欠け・0 以下は「不明」として扱い、画面で制限しない）。 */
function limitsOf(data: EnglishEssayLimits | null | undefined): EnglishEssayLimits | null {
  const maxImages = data?.maxImages
  const maxImageMb = data?.maxImageMb
  if (!isPositiveNumber(maxImages) || !isPositiveNumber(maxImageMb)) {
    return null
  }
  return { maxImages, maxImageMb }
}

function isPositiveNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value > 0
}

/** 一覧の 1 行の状態（未添削／添削中／添削済み／失敗）。 */
export function listStateOf(item: EssayListItem): EssayListState {
  const latest = item.latestGrading
  if (latest === null) {
    return 'NONE'
  }
  if (latest.statusCode === 'SUCCEEDED') {
    return 'SUCCEEDED'
  }
  if (latest.statusCode === 'QUEUED' || latest.statusCode === 'RUNNING') {
    return 'PENDING'
  }
  return 'FAILED'
}

/** 一覧の 1 行の最新の添削（まだ無ければ null）。 */
export function latestGrading(item: EssayListItem): EssayGradingSummary | null {
  return item.latestGrading
}

/* ---------- 詳細 ---------- */

/** 直前に返した詳細（静かな読み直しで**同じオブジェクト**を返すために取っておく）。 */
let cachedDetail: EssayDetail | null = null
let cachedDetailJson = ''

/**
 * 1 件の詳細を読む（削除済み・不明は null）。
 *
 * <p>**同じ中身なら同じオブジェクトを返す**。3 秒ごとの様子見（静かな読み直し）で毎回別の
 * オブジェクトを返すと、中身が同じでも画面が描き直されてちらつく
 * （`docs/DECISIONS.md` の「静かな読み直し」）。</p>
 */
export async function loadEssay(essayId: string): Promise<EssayDetail | null> {
  let detail: EssayDetail
  try {
    const response = await fetchEnglishEssay(essayId)
    detail = detailOf(response.data)
  } catch (caught) {
    if (caught instanceof ApiError && caught.status === 404) {
      return null
    }
    throw caught
  }
  const json = JSON.stringify(detail)
  if (cachedDetail !== null && cachedDetail.essay.id === detail.essay.id && json === cachedDetailJson) {
    return cachedDetail
  }
  cachedDetail = detail
  cachedDetailJson = json
  return detail
}

/** 未完了の回（`QUEUED` / `RUNNING`）があるか。あるうちは画面が様子を見る。 */
export function hasPendingRounds(rounds: readonly EssayGradingRound[]): boolean {
  return rounds.some((round) => round.statusCode === 'QUEUED' || round.statusCode === 'RUNNING')
}

/* ---------- 作成・更新・削除 ---------- */

/** 新規作成（添削はまだ無い。画像は {@link uploadEssayImage} で 1 枚ずつ上げる）。 */
export async function createEssay(input: EssayInput): Promise<Essay> {
  const response = await createEnglishEssay({
    level: input.level,
    // 題が空のまま保存されると一覧で行が見分けられないので、2.0 と同じ既定の題にする
    title: input.title.trim() || '無題の英作文',
    titleZh: input.titleZh ?? '',
    questionText: input.questionText ?? '',
    essayText: input.essayText ?? ''
  })
  return detailOf(response.data).essay
}

/** 本文と画像の並び・区分を更新する（PUT は全項目を置き換えるので、呼ぶ側が全部そろえる）。 */
export async function saveEssay(essayId: string, input: EssaySaveInput): Promise<Essay> {
  const response = await updateEnglishEssay(essayId, {
    level: input.level,
    title: input.title.trim() || '無題の英作文',
    titleZh: input.titleZh,
    questionText: input.questionText,
    essayText: input.essayText,
    images: input.images
  })
  return detailOf(response.data).essay
}

/** 画像を 1 枚上げる（multipart）。 */
export async function uploadEssayImage(
  essayId: string,
  input: EssayImageUpload
): Promise<EssayImage> {
  const response = await uploadEnglishEssayImage(essayId, input)
  return imageOf(essayId, response.data)
}

/**
 * 画像を文字にする（user-api の入口。**同期**）。
 *
 * <p>送るのは**画像の実体**（ファイル）。アップロード済みの画像でも、画面が持っている
 * データ URL／URL から作ったファイルをそのまま渡す（利用者の指示 2026-09-27）。</p>
 */
export async function recognizeEssay(
  level: EssayLevel,
  images: readonly EssayOcrImage[]
): Promise<EssayOcrResult> {
  const response = await recognizeEssayImages(level, images)
  const data = response.data
  return {
    questionText: data.questionText ?? '',
    essayText: data.essayText ?? '',
    pages: data.pages ?? [],
    questionConfidence: data.questionConfidence ?? 0,
    essayConfidence: data.essayConfidence ?? 0
  }
}

/** 削除（論理削除。2.0 と同じで行は残す）。 */
export async function removeEssay(essayId: string): Promise<void> {
  await deleteEnglishEssay(essayId)
  if (cachedDetail !== null && cachedDetail.essay.id === essayId) {
    cachedDetail = null
    cachedDetailJson = ''
  }
}

/**
 * 添削を**受付ける**（1 回ぶん。上書きしない。历次として積む）。
 *
 * <p>入口は **user-api**（`POST /api/user/english-essays/{essayId}/gradings`。要ログイン）。
 * 実行はバックエンドの働き手なので**すぐ返る**。結果は詳細を読み直して見る
 * （`QUEUED` / `RUNNING` のうちは 3 秒ごとに様子を見る）。</p>
 *
 * <p>`round` は**渡さない**のが既定（次の回はサーバーが決める。画面で数えると、受付が重なった
 * ときにずれる）。削除済み・他人の作文は 404 なので、そのまま出さず画面向けの日本語にする。</p>
 */
export async function addGradingRound(
  essayId: string,
  round?: number
): Promise<{ gradingId: string; round: number; message: string }> {
  try {
    const response = await requestEssayGrading(essayId, round)
    return response.data
  } catch (caught) {
    if (caught instanceof ApiError && caught.status === 404) {
      throw new ApiError({
        code: 'NOT_FOUND',
        status: 404,
        message: 'この英作文は見つかりませんでした。削除された可能性があります。',
        traceId: caught.traceId,
        cause: caught
      })
    }
    throw caught
  }
}

/** 画面の状態を捨てる（テストと、アカウントを切り替えたとき）。 */
export function resetEssays(): void {
  clearEssayList()
  essayLimits.value = null
  cachedDetail = null
  cachedDetailJson = ''
}

/* ---------- API の形 → 画面の形 ---------- */

function listItemOf(row: EnglishEssayListItem): EssayListItem {
  return {
    id: String(row.essayId),
    level: levelOf(row.level),
    title: row.title,
    titleZh: row.titleZh ?? '',
    questionText: row.questionText ?? '',
    essayText: row.essayText ?? '',
    wordCount: row.wordCount ?? countWords(row.essayText ?? ''),
    imageCount: row.imageCount ?? 0,
    questionImageCount: row.questionImageCount ?? 0,
    answerImageCount: row.answerImageCount ?? 0,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
    latestGrading: row.latestGrading === null || row.latestGrading === undefined
      ? null
      : {
          gradingId: String(row.latestGrading.gradingId),
          round: row.latestGrading.round,
          statusCode: row.latestGrading.statusCode,
          score: row.latestGrading.score,
          maxScore: row.latestGrading.maxScore,
          createdAt: row.latestGrading.createdAt
        }
  }
}

function detailOf(data: EnglishEssayDetail): EssayDetail {
  const level = levelOf(data.level)
  const essayText = data.essayText ?? ''
  const title = data.title
  const titleZh = data.titleZh ?? ''
  const rounds = [...(data.gradings ?? [])]
    .sort((left, right) => left.round - right.round)
    .map((grading) => roundOf(grading, { level, title, titleZh, essayText }))
  const gradings: EssayGrading[] = []
  for (const round of rounds) {
    if (round.report !== null) {
      gradings.push(round.report)
    }
  }
  return {
    essay: {
      id: String(data.essayId),
      level,
      title,
      titleZh,
      questionText: data.questionText ?? '',
      essayText,
      wordCount: data.wordCount ?? countWords(essayText),
      images: [...(data.images ?? [])]
        .sort((left, right) => left.order - right.order)
        .map((image) => imageOf(data.essayId, image)),
      gradings,
      state: data.stateCode === 'X' ? 'X' : 'A',
      createdAt: data.createdAt,
      updatedAt: data.updatedAt
    },
    rounds
  }
}

function imageOf(essayId: string, image: EnglishEssayImage): EssayImage {
  return {
    id: String(image.imageId),
    category: image.category === 'answer' ? 'answer' : 'question',
    fileName: image.originalFileName,
    dataUrl: englishEssayImageUrl(essayId, String(image.imageId)),
    order: image.order
  }
}

function roundOf(
  grading: EnglishEssayGrading,
  essay: { level: EssayLevel; title: string; titleZh: string; essayText: string }
): EssayGradingRound {
  return {
    gradingId: String(grading.gradingId),
    round: grading.round,
    statusCode: grading.statusCode,
    score: grading.score,
    maxScore: grading.maxScore,
    failureReason: grading.failureReason,
    createdAt: grading.createdAt,
    // レポートの形は 2 種類ある（2.0 の移行データ）。組み立ては report.ts が受け持つ
    report: grading.statusCode === 'SUCCEEDED'
      ? normalizeGradingReport({
          gradingId: String(grading.gradingId),
          // 回の番号は API の値をそのまま渡す（失敗した回があると数え直しではずれる）
          round: grading.round,
          level: grading.level,
          titleJa: grading.titleJa,
          titleZh: grading.titleZh,
          essayText: grading.essayText ?? essay.essayText,
          wordCount: grading.wordCount,
          score: grading.score,
          maxScore: grading.maxScore,
          createdAt: grading.createdAt,
          report: grading.report,
          essayLevel: essay.level,
          essayTitle: essay.title,
          essayTitleZh: essay.titleZh
        })
      : null
  }
}

/** 級の値（未知の値は既定の級として扱う。画面が級名を出せなくなるのを避ける）。 */
function levelOf(value: string | null | undefined, fallback: EssayLevel = 'PRE1'): EssayLevel {
  return value === 'GRADE1' || value === 'PRE1' || value === 'GRADE2' ? value : fallback
}

/** 空文字は「条件なし」にする（undefined の項目は HttpClient が落とす）。 */
function textOrUndefined(value: string | undefined): string | undefined {
  const text = (value ?? '').trim()
  return text.length === 0 ? undefined : text
}
