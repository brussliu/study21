import { ApiError, HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * 読書管理 API（/api/user/reading）。
 * user-api の ReadingController と対応する。
 *
 * データは `RED_書籍情報` / `RED_書籍分類情報` / `RED_書籍ファイル情報` /
 * `RED_読書記録情報` / `RED_標記情報`（2.0 の英語読書 `TRN_英語読書*` から移行済み）。
 *
 * 2026-09-14 の決定（公開範囲と本棚）:
 * ・本は**公開範囲**を持つ。`GLOBAL`＝管理者が登録した全体書籍（全員の【図書館】に出る）／
 *   `FAMILY`＝保護者が登録した家庭の本（その家庭の【図書館】だけに出る）
 * ・【自分の本棚】は**アカウントごと**（`RED_自分の本棚情報`）。【図書館】から入れて、
 *   要らなくなったら外す。初期状態は空
 *
 * 画面の分担:
 * ・書籍管理（管理者＝全体書籍 / 保護者＝自分の家庭の本）… PDF・表紙のアップロードと
 *   登録・修正・削除・分類。生徒はこの画面を開かない（メニューに出さない。API は 403）
 * ・書籍閲覧（全員）… 【自分の本棚】＋【図書館】＋本文 PDF を読む（pdf.js）＋標記＋読書履歴
 */
/** 本の言語（分類はジャンルだけを持ち、言語はこの値で表す）。 */
export type ReadingLanguage = '中国語' | '英語' | '日本語'

/**
 * 本の公開範囲。
 * ・GLOBAL … 管理者が登録した全体書籍（全家庭の【図書館】に出る）
 * ・FAMILY … 保護者が登録した家庭の本（その家庭だけに出る）
 */
export type ReadingScope = 'GLOBAL' | 'FAMILY'

/** 一覧の絞り込み（`GET /books?scope=`）。ALL＝全体書籍＋自分の家庭（既定）。 */
export type ReadingScopeFilter = 'ALL' | ReadingScope

export const SCOPE_LABELS: Record<ReadingScope, string> = {
  GLOBAL: '全体',
  FAMILY: '家庭の本'
}

export const LANGUAGE_OPTIONS: ReadingLanguage[] = ['中国語', '英語', '日本語']

export type ReadingDifficulty = 'Starter' | 'Elementary' | 'Intermediate' | 'Upper'
export type ReadingStatus = '未着手' | '読書中' | '一時停止' | '読了'
export type ReadingMarkType = 'highlight' | 'underline' | 'memo' | 'vocabulary' | 'pen'

/** 難易度の表示順とラベル。 */
export const DIFFICULTY_OPTIONS: ReadingDifficulty[] = ['Starter', 'Elementary', 'Intermediate', 'Upper']
export const STATUS_OPTIONS: ReadingStatus[] = ['未着手', '読書中', '一時停止', '読了']
export const MARK_TYPE_OPTIONS: ReadingMarkType[] = ['vocabulary', 'highlight', 'underline', 'memo', 'pen']

export const DIFFICULTY_LABELS: Record<ReadingDifficulty, string> = {
  Starter: 'Starter（入門）',
  Elementary: 'Elementary（初級）',
  Intermediate: 'Intermediate（中級）',
  Upper: 'Upper（上級）'
}

export const STATUS_LABELS: Record<ReadingStatus, string> = {
  未着手: '未着手',
  読書中: '読書中',
  一時停止: '一時停止',
  読了: '読了'
}

/** ステータスのバッジ色（tokens.css の badge--* に対応）。 */
export const STATUS_BADGES: Record<ReadingStatus, string> = {
  未着手: 'badge--neutral',
  読書中: 'badge--info',
  一時停止: 'badge--warning',
  読了: 'badge--success'
}

export const MARK_TYPE_LABELS: Record<ReadingMarkType, string> = {
  vocabulary: '語彙',
  highlight: 'ハイライト',
  underline: '下線',
  memo: 'メモ',
  pen: '手書き'
}

/** 標記の既定色（2.0 と同じ配色）。 */
export const MARK_TYPE_COLORS: Record<ReadingMarkType, string> = {
  vocabulary: '#7a4de8',
  highlight: '#f5c518',
  underline: '#2f7bd6',
  memo: '#e07b39',
  pen: '#d64545'
}

/** 本文 PDF と表紙画像の受け入れ条件（サーバ側の検証と同じ値）。 */
export const PDF_MAX_BYTES = 200 * 1024 * 1024
export const COVER_MAX_BYTES = 5 * 1024 * 1024
export const PDF_ACCEPT = 'application/pdf'
export const COVER_ACCEPT = 'image/png,image/jpeg,image/webp'

/** 未分類を表す分類ID（絞り込みで使う）。 */
export const CATEGORY_NONE = 0

export interface ReadingBook {
  bookId: number
  /** 利用者に見せる番号（ER-yyyyMMdd-HHmmss） */
  bookNo: string
  subject: string
  title: string
  author: string
  /** 本の言語（中国語 / 英語 / 日本語） */
  language: ReadingLanguage
  difficulty: ReadingDifficulty
  status: ReadingStatus
  totalPages: number
  currentPage: number
  pinned: boolean
  tags: string[]
  summary: string | null
  note: string | null
  /** 直近 1 回の読書時間（分） */
  recentMinutes: number
  totalMinutes: number
  markCount: number
  lastReadAt: string | null
  readPercent: number
  /** 本棚の分類（null は未分類） */
  categoryId: number | null
  categoryName: string | null
  /** 本文 PDF の行があるか */
  hasPdf: boolean
  /** 元のファイル名（例 '01.Harry Potter and the Sorcerers Stone.pdf'）。無ければ null */
  pdfOriginalName: string | null
  /** 実体ファイルがストレージにあるか（false のときは画面に「PDF 未登録」を出す） */
  pdfAvailable: boolean
  hasCover: boolean
  coverAvailable: boolean
  version: number
  createdAt: string | null
  updatedAt: string | null
  /** 公開範囲（GLOBAL＝管理者の全体書籍 / FAMILY＝家庭の本） */
  scope: ReadingScope
  /** FAMILY のときの持ち主の家庭（生徒のアカウントID）。GLOBAL は null */
  ownerFamilyId: number | null
  /** その家庭の生徒の表示名（「家庭の本」バッジに出す）。GLOBAL は null */
  ownerFamilyLabel: string | null
  /** 自分の【自分の本棚】に入っているか（【本棚に入れる/外す】の出し分け） */
  inMyShelf: boolean
}

export interface ReadingShelfTotals {
  bookCount: number
  readingCount: number
  finishedCount: number
  totalMinutes: number
  markCount: number
  /** 本文 PDF の実体があり、すぐ読める冊数 */
  pdfCount: number
  /** 分類が付いていない冊数（書籍管理画面の「未分類」タブに出す） */
  uncategorizedCount: number
  /** 自分の【自分の本棚】の冊数（図書館タブの「自分の本棚 N 冊」） */
  myShelfCount: number
}

export interface ReadingBookPage {
  items: ReadingBook[]
  totalElements: number
  page: number
  size: number
  totalPages: number
  totals: ReadingShelfTotals
}

export interface ReadingRecord {
  recordId: number
  bookId: number
  bookTitle: string | null
  readAt: string | null
  pageStart: number | null
  pageEnd: number | null
  minutes: number
  markCount: number
  memo: string | null
}

export interface ReadingRecordPage {
  items: ReadingRecord[]
  totalElements: number
  page: number
  size: number
  totalPages: number
}

export interface ReadingMark {
  markId: number
  bookId: number
  pageNo: number
  markType: ReadingMarkType
  targetText: string | null
  content: string | null
  color: string | null
  /** PDF ページ内の正規化座標（0〜1）。左上が原点。無ければ null */
  positionX: number | null
  positionY: number | null
  width: number | null
  height: number | null
  /** 手書き（pen）の点列 JSON。文字列のまま扱う */
  drawingData: string | null
  createdAt: string | null
}

export interface ReadingMarkPage {
  items: ReadingMark[]
  totalElements: number
  page: number
  size: number
  totalPages: number
  vocabularyCount: number
  highlightCount: number
  memoCount: number
  underlineCount: number
}

export interface ReadingBookDetail {
  book: ReadingBook
  records: ReadingRecord[]
  marks: ReadingMark[]
  markedPages: number[]
}

export interface ReadingBookSave {
  title: string
  author: string
  /** 本の言語（未指定は 英語） */
  language?: ReadingLanguage
  difficulty: ReadingDifficulty
  status: ReadingStatus
  totalPages: number
  currentPage?: number
  pinned?: boolean
  /** 本棚の分類（未分類は null） */
  categoryId?: number | null
  tags?: string[]
  summary?: string
  note?: string
  version?: number
  /**
   * 公開範囲。**管理者のときだけ有効**（保護者はサーバが FAMILY に固定するので送っても無視される）。
   * 省略したときは 管理者＝GLOBAL / 保護者＝FAMILY。
   */
  scope?: ReadingScope
}

export interface ReadingRecordSave {
  readAt?: string
  pageStart?: number
  pageEnd?: number
  minutes?: number
  markCount?: number
  memo?: string
}

export interface ReadingMarkSave {
  pageNo: number
  markType: ReadingMarkType
  targetText?: string
  content?: string
  color?: string
  positionX?: number
  positionY?: number
  width?: number
  height?: number
  drawingData?: string
}

export interface ReadingBookMutation {
  book: ReadingBook
  message: string
}

export interface ReadingRecordMutation {
  record: ReadingRecord
  book: ReadingBook
  message: string
}

export interface ReadingMarkMutation {
  mark: ReadingMark
  book: ReadingBook
  message: string
}

/** 本棚の分類（マスタ）。 */
export interface ReadingCategory {
  categoryId: number
  name: string
  displayOrder: number
  description: string | null
  /** その分類に入っている冊数（見える本だけを数えた値） */
  bookCount: number
  /** 分類の持ち主の家庭。null＝全体の分類（管理者が作り全家庭に見える） */
  ownerFamilyId: number | null
}

export interface ReadingCategorySave {
  name: string
  displayOrder?: number
  description?: string
}

export interface ReadingCategoryMutation {
  category: ReadingCategory
  message: string
}

export interface ReadingSimpleResult {
  count: number
  message: string
}

// パスは他機能と同じ書き方に合わせる（末尾スラッシュ付きの URL は Spring 側で 404 になるため）
const http = new HttpClient({ baseUrl: '/api/user/reading' })

/**
 * 本の一覧（絞り込み・ページング・サマリ）。categoryId=0 は「未分類のみ」。
 *
 * @param scope `ALL`（全体書籍＋自分の家庭。既定）/ `GLOBAL`（全体書籍のみ）/ `FAMILY`（自分の家庭のみ）
 * @param shelf `MINE` のとき【自分の本棚】だけ
 */
export function searchReadingBooks(params: {
  keyword?: string
  difficulty?: string
  status?: string
  tag?: string
  pinned?: boolean
  categoryId?: number
  language?: ReadingLanguage
  scope?: ReadingScopeFilter
  shelf?: 'MINE'
  page?: number
  size?: number
}): Promise<ApiResponse<ReadingBookPage>> {
  return http.get<ReadingBookPage>('/books', { params })
}

/* ---------- 【自分の本棚】（アカウントごと） ---------- */

/**
 * 【本棚に入れる】。**冪等**（すでに入っていればそのまま）。
 * 触るのは自分の行だけ（他人のアカウントID は送らない）。
 */
export function addReadingBookToShelf(bookId: number): Promise<ApiResponse<ReadingBookMutation>> {
  return http.put<ReadingBookMutation>(`/books/${bookId}/shelf`, { body: {} })
}

/** 【本棚から外す】。**冪等**（入っていなければ何もしない）。 */
export function removeReadingBookFromShelf(bookId: number): Promise<ApiResponse<ReadingBookMutation>> {
  return http.delete<ReadingBookMutation>(`/books/${bookId}/shelf`)
}

/* ---------- 分類（本棚の区切り。書籍管理画面で管理する） ---------- */

export function listReadingCategories(): Promise<ApiResponse<{ items: ReadingCategory[] }>> {
  return http.get<{ items: ReadingCategory[] }>('/categories')
}

export function createReadingCategory(body: ReadingCategorySave): Promise<ApiResponse<ReadingCategoryMutation>> {
  return http.post<ReadingCategoryMutation>('/categories', { body })
}

export function updateReadingCategory(
  categoryId: number, body: ReadingCategorySave
): Promise<ApiResponse<ReadingCategoryMutation>> {
  return http.put<ReadingCategoryMutation>(`/categories/${categoryId}`, { body })
}

/** 分類を削除する（本は消えず「未分類」に戻る）。 */
export function deleteReadingCategory(categoryId: number): Promise<ApiResponse<ReadingSimpleResult>> {
  return http.delete<ReadingSimpleResult>(`/categories/${categoryId}`)
}

/* ---------- ファイル（本文 PDF・表紙） ---------- */

/**
 * multipart のアップロード（資料管理・臨時ファイルと同じ書き方）。
 * HttpClient は body を JSON にするため、FormData は fetch を直接使う
 * （Content-Type を自分で付けない＝ブラウザに boundary を付けさせる）。
 */
async function fetchMultipart<T>(url: string, body: FormData, method = 'POST'): Promise<ApiResponse<T>> {
  let response: Response
  try {
    response = await fetch(url, { method, body })
  } catch (cause) {
    throw new ApiError({ code: 'NETWORK_ERROR', message: 'ネットワークエラーが発生しました', cause })
  }
  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null
  if (!response.ok || !payload?.success) {
    throw new ApiError({
      code: payload?.code ?? 'INTERNAL_ERROR',
      message: payload?.message ?? `HTTP ${response.status}`,
      status: response.status,
      traceId: payload?.traceId
    })
  }
  return payload
}

function uploadFile<T>(path: string, file: File, extra: Record<string, string> = {}): Promise<ApiResponse<T>> {
  const form = new FormData()
  form.append('file', file)
  for (const [key, value] of Object.entries(extra)) form.append(key, value)
  return fetchMultipart<T>(`/api/user/reading${path}`, form)
}

/** 本文 PDF をアップロードする（差し替え）。totalPages は画面が pdf.js で数えた値。 */
export function uploadReadingPdf(
  bookId: number, file: File, totalPages?: number
): Promise<ApiResponse<ReadingBookMutation>> {
  return uploadFile<ReadingBookMutation>(
    `/books/${bookId}/pdf`, file, totalPages === undefined ? {} : { totalPages: String(totalPages) })
}

export function deleteReadingPdf(bookId: number): Promise<ApiResponse<ReadingSimpleResult>> {
  return http.delete<ReadingSimpleResult>(`/books/${bookId}/pdf`)
}

/** 表紙画像をアップロードする（差し替え）。 */
export function uploadReadingCover(bookId: number, file: File): Promise<ApiResponse<ReadingBookMutation>> {
  return uploadFile<ReadingBookMutation>(`/books/${bookId}/cover`, file)
}

export function deleteReadingCover(bookId: number): Promise<ApiResponse<ReadingSimpleResult>> {
  return http.delete<ReadingSimpleResult>(`/books/${bookId}/cover`)
}

/**
 * 本文 PDF の URL（pdf.js に渡す。img/iframe と違い認証 Cookie は pdf.js が付ける）。
 * version を付けて差し替え後にブラウザが古い PDF を使い回さないようにする。
 */
export function readingPdfUrl(bookId: number, version = 0): string {
  return `/api/user/reading/books/${bookId}/pdf?v=${version}`
}

/** 表紙画像の URL（img src にそのまま使う）。 */
export function readingCoverUrl(bookId: number, version = 0): string {
  return `/api/user/reading/books/${bookId}/cover?v=${version}`
}

/**
 * 本文 PDF をダウンロードする URL（`<a href>` にそのまま使う）。
 * サーバは `download=true` のとき `Content-Disposition: attachment` で返す。
 */
export function readingPdfDownloadUrl(bookId: number, version = 0): string {
  return `/api/user/reading/books/${bookId}/pdf?v=${version}&download=true`
}

/** 閲覧画面用（1 冊＋記録＋標記＋標記のあるページ）。 */
export function fetchReadingBook(
  bookId: number,
  params: { markPage?: number; markType?: string } = {}
): Promise<ApiResponse<ReadingBookDetail>> {
  return http.get<ReadingBookDetail>(`/books/${bookId}`, { params })
}

export function createReadingBook(body: ReadingBookSave): Promise<ApiResponse<ReadingBookMutation>> {
  return http.post<ReadingBookMutation>('/books', { body })
}

export function updateReadingBook(bookId: number, body: ReadingBookSave): Promise<ApiResponse<ReadingBookMutation>> {
  return http.put<ReadingBookMutation>(`/books/${bookId}`, { body })
}

export function setReadingBookPinned(
  bookId: number, pinned: boolean, version?: number
): Promise<ApiResponse<ReadingBookMutation>> {
  return http.patch<ReadingBookMutation>(`/books/${bookId}/pin`, { body: { pinned, version } })
}

export function setReadingBookStatus(
  bookId: number, status: ReadingStatus, version?: number
): Promise<ApiResponse<ReadingBookMutation>> {
  return http.patch<ReadingBookMutation>(`/books/${bookId}/status`, { body: { status, version } })
}

export function deleteReadingBook(bookId: number): Promise<ApiResponse<ReadingSimpleResult>> {
  return http.delete<ReadingSimpleResult>(`/books/${bookId}`)
}

/**
 * 読書履歴（bookId を省略すると全書籍）。
 * ・`dateFrom` / `dateTo`（`yyyy-MM-dd`。読書日時で絞る。片方だけでもよい）
 * ・`pageNo`（そのページを読んだ記録だけ＝ 開始ページ <= pageNo <= 終了ページ）
 */
export function searchReadingRecords(params: {
  bookId?: number
  dateFrom?: string
  dateTo?: string
  pageNo?: number
  page?: number
  size?: number
}): Promise<ApiResponse<ReadingRecordPage>> {
  return http.get<ReadingRecordPage>('/records', { params })
}

/** 読書履歴の 1 ページの件数（画面共通のルール）。 */
export const RECORD_PAGE_SIZES = [20, 50, 100]

/* ---------- 語彙・読み方の引き当て（本文から選んだ語の意味を自動で引く） ---------- */

export interface ReadingLookupResult {
  /** 引いた語（正規化前の入力） */
  text: string
  language: ReadingLanguage
  /** 日本語での意味（英語の本の語彙） */
  japanese: string | null
  /** 中国語での意味（英語の本の語彙） */
  chinese: string | null
  /** ピンイン（中国語の本の読み方） */
  pinyin: string | null
  /** 解説（中国語の本の読み方） */
  explanation: string | null
  /** どこから引いたか（EXCELAPI / YOUDAO / GOOGLE / AI / MANUAL） */
  source: string
  /** キャッシュ（RED_語彙辞書情報）から返したか */
  cached: boolean
  /** 画面に出す一言（取得できなかったときの案内など） */
  message: string
}

/**
 * 語の意味・読みを引く（英語＝日本語訳と中国語訳 / 中国語＝ピンインと解説）。
 * 同じ語は `RED_語彙辞書情報` に貯めてあり、2 回目以降は外部へ問い合わせない。
 */
export function lookupReadingWord(
  text: string, language: ReadingLanguage
): Promise<ApiResponse<ReadingLookupResult>> {
  return http.post<ReadingLookupResult>('/lookup', { body: { text, language } })
}

export function saveReadingRecord(
  bookId: number, body: ReadingRecordSave
): Promise<ApiResponse<ReadingRecordMutation>> {
  return http.post<ReadingRecordMutation>(`/books/${bookId}/records`, { body })
}

export function deleteReadingRecord(recordId: number): Promise<ApiResponse<ReadingSimpleResult>> {
  return http.delete<ReadingSimpleResult>(`/records/${recordId}`)
}

/** 標記（ページ・種別で絞れる）。 */
export function searchReadingMarks(
  bookId: number,
  params: { pageNo?: number; markType?: string; page?: number; size?: number } = {}
): Promise<ApiResponse<ReadingMarkPage>> {
  return http.get<ReadingMarkPage>(`/books/${bookId}/marks`, { params })
}

export function createReadingMark(bookId: number, body: ReadingMarkSave): Promise<ApiResponse<ReadingMarkMutation>> {
  return http.post<ReadingMarkMutation>(`/books/${bookId}/marks`, { body })
}

export function deleteReadingMark(markId: number): Promise<ApiResponse<ReadingBookMutation>> {
  return http.delete<ReadingBookMutation>(`/marks/${markId}`)
}

/**
 * その書籍の標記と読書記録をすべて削除し、あわせて読書の進捗（現在ページ・ステータス・読書時間）も
 * リセットする（画面の【標記クリア】。2.0 の「標記削除」を拡張。パスと戻り値の形は変えない）。
 */
export function deleteAllReadingMarks(bookId: number): Promise<ApiResponse<ReadingBookMutation>> {
  return http.delete<ReadingBookMutation>(`/books/${bookId}/marks`)
}
