import { ApiError } from '@study21/web-shared'
import type { ClipRow, ClipType, FolderCode, SourceCode } from '@/api/linkclip'

/**
 * リンククリップ画面の表示ロジック（ラベル・整形・タグ解析）。
 * UI から独立させて単体テストできるようにここへ置く。
 */

export interface FolderOption {
  code: FolderCode | 'ALL' | 'FAVORITE' | 'ARCHIVED'
  label: string
  description: string
  icon: string
}

/** 左ペイン「仕分け」の並び（2.0 のレーン + お気に入り / アーカイブ）。 */
export const FOLDER_LANES: FolderOption[] = [
  { code: 'ALL', label: 'すべて', description: '保存したリンクをすべて表示', icon: 'grid' },
  { code: 'INBOX', label: '未整理', description: 'まず投げ込む一時置き場', icon: 'upload' },
  { code: 'READ_LATER', label: 'あとで見る', description: '動画や長文記事の後回し箱', icon: 'clock' },
  { code: 'LEARNING', label: '学習素材', description: '英語・数学などに直接使う素材', icon: 'book' },
  { code: 'REFERENCE', label: '参考資料', description: '実装・アイデア・調査メモ', icon: 'clipboard' },
  { code: 'DONE', label: '完了', description: '読み終わり・整理済のリンク', icon: 'check-circle' },
  { code: 'FAVORITE', label: 'お気に入り', description: 'あとで見返したいリンク', icon: 'bookmark' },
  { code: 'ARCHIVED', label: 'アーカイブ', description: '一覧から外したリンク', icon: 'file-archive' }
]

/** 保存先（コンポーザの select 用。未整理〜完了のみ）。 */
export const FOLDER_CHOICES: Array<{ code: FolderCode; label: string }> = FOLDER_LANES
  .filter((lane) => !['ALL', 'FAVORITE', 'ARCHIVED'].includes(lane.code))
  .map((lane) => ({ code: lane.code as FolderCode, label: lane.label }))

export const FOLDER_LABELS: Record<string, string> = Object.fromEntries(
  FOLDER_LANES.map((lane) => [lane.code, lane.label])
)

export const SOURCE_LABELS: Record<string, string> = {
  WEB: 'Web',
  YOUTUBE: 'YouTube',
  GITHUB: 'GitHub',
  WIKIPEDIA: 'Wikipedia',
  NEWS: 'News',
  LOCAL_FILE: 'Local File',
  OTHER: 'Other'
}

/** ソース別のアイコン（スプライトの i-* 名）。 */
export const SOURCE_ICONS: Record<string, string> = {
  WEB: 'globe',
  YOUTUBE: 'video',
  GITHUB: 'grid',
  WIKIPEDIA: 'book-open',
  NEWS: 'alert',
  LOCAL_FILE: 'file',
  OTHER: 'bookmark'
}

export const CLIP_TYPE_LABELS: Record<string, string> = {
  LINK: 'リンク',
  VIDEO: '動画',
  ARTICLE: '記事',
  REPOSITORY: 'リポジトリ',
  NEWS: 'ニュース',
  FILE: 'ファイル'
}

export function folderLabel(code: string | null | undefined): string {
  return FOLDER_LABELS[code ?? ''] ?? '未整理'
}

export function sourceLabel(code: string | null | undefined): string {
  return SOURCE_LABELS[code ?? ''] ?? 'Other'
}

export function sourceIcon(code: string | null | undefined): string {
  return SOURCE_ICONS[code ?? ''] ?? 'bookmark'
}

export function clipTypeLabel(code: string | null | undefined): string {
  return CLIP_TYPE_LABELS[code ?? ''] ?? 'リンク'
}

/** ローカルファイル（NAS 上の Web からは開けないのでパスをコピーさせる）。 */
export function isLocalFile(row: Pick<ClipRow, 'sourceCode' | 'url'>): boolean {
  if (row.sourceCode === 'LOCAL_FILE') return true
  const url = (row.url ?? '').trim().toLowerCase()
  return url.startsWith('file://') || /^[a-z]:[\\/]/.test(url)
}

/**
 * タグ入力を配列へ。カンマ（全角も）区切り・前後空白除去・空要素除去・
 * 大文字小文字を無視した重複排除・1 件 100 文字まで（サーバ側と同じ規則）。
 */
export function parseTags(input: string | null | undefined): string[] {
  if (!input) return []
  const seen = new Set<string>()
  const tags: string[] = []
  for (const raw of input.split(/[,、]/)) {
    const tag = raw.trim().replace(/\s+/g, ' ')
    if (tag === '' || tag.length > 100) continue
    const key = tag.toLowerCase()
    if (seen.has(key)) continue
    seen.add(key)
    tags.push(tag)
  }
  return tags
}

/** タグ配列を入力欄の表示（カンマ区切り）へ。 */
export function formatTags(tags: string[] | null | undefined): string {
  return (tags ?? []).join(', ')
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** ISO 文字列・数値どちらでも受け取れる日時整形。 */
export function formatDateTime(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function formatDate(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())}`
}

/** 動画の長さ（秒）を m:ss / h:mm:ss へ。 */
export function formatDuration(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined || seconds < 0) return '—'
  const total = Math.floor(seconds)
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const rest = total % 60
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(rest)}` : `${minutes}:${pad(rest)}`
}

export function messageOf(cause: unknown): string {
  return cause instanceof ApiError || cause instanceof Error ? cause.message : '処理に失敗しました。'
}

/** 楽観的ロックの競合（409）。 */
export function isConflict(cause: unknown): boolean {
  return cause instanceof ApiError && (cause.status === 409 || cause.code === 'CONFLICT')
}

/**
 * クリップボードへコピーする。
 * NAS 上の http 配信では navigator.clipboard が使えないため、旧来の方法へフォールバックする。
 */
export async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    // フォールバックへ
  }
  try {
    const area = document.createElement('textarea')
    area.value = text
    area.setAttribute('readonly', 'readonly')
    area.style.position = 'fixed'
    area.style.top = '-1000px'
    document.body.appendChild(area)
    area.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(area)
    return ok
  } catch {
    return false
  }
}

/** カード・詳細で共通に使う「リンクを開く」判定（ローカルファイルは開かない）。 */
export function canOpenInBrowser(row: Pick<ClipRow, 'sourceCode' | 'url'>): boolean {
  return !isLocalFile(row)
}

export type { ClipType, FolderCode, SourceCode }
