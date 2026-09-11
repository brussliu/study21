import { ApiError } from '@study21/web-shared'
import type { DocumentFileInfo } from '@/api/documents'
import type { TestFile } from '@/api/testinfo'

/**
 * テスト情報ファイルの共通ユーティリティ。
 * 資料管理のチップ表示（DocumentFileChips）へ詰め替えるための変換と、
 * 回転・注釈で使う画像操作（2.0 の testinfo_showpic.jsp と同じ考え方）をまとめる。
 */

/** 資料管理のチップ表示で使う型へ詰め替える（contentUrl は API の値をそのまま使う）。 */
export function toDocumentFileInfo(file: TestFile): DocumentFileInfo {
  return {
    branchNo: file.fileId,
    originalFileName: file.originalFileName,
    extension: file.extension,
    comment: file.comment,
    contentUrl: file.contentUrl,
    image: file.image
  }
}

export function messageOf(cause: unknown): string {
  return cause instanceof ApiError || cause instanceof Error ? cause.message : '処理に失敗しました。'
}

/** 楽観ロック競合（409）かどうか。 */
export function isConflict(cause: unknown): boolean {
  return cause instanceof ApiError && cause.status === 409
}

export function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('画像の読み込みに失敗しました。'))
    image.src = src
  })
}

export function fileToDataUrl(file: File | Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(new Error('ファイルの読み込みに失敗しました。'))
    reader.readAsDataURL(file)
  })
}

export function canvasToBlob(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob)
      else reject(new Error('画像の生成に失敗しました。'))
    }, 'image/png')
  })
}

/** data URL / 画像 URL を回転した data URL を返す。 */
export async function rotateDataUrl(src: string, degrees: number): Promise<string> {
  const image = await loadImage(src)
  const normalized = ((degrees % 360) + 360) % 360
  const swap = normalized === 90 || normalized === 270
  const canvas = document.createElement('canvas')
  canvas.width = swap ? image.naturalHeight : image.naturalWidth
  canvas.height = swap ? image.naturalWidth : image.naturalHeight
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('回転用キャンバスの初期化に失敗しました。')
  ctx.translate(canvas.width / 2, canvas.height / 2)
  ctx.rotate((normalized * Math.PI) / 180)
  ctx.drawImage(image, -image.naturalWidth / 2, -image.naturalHeight / 2)
  return canvas.toDataURL('image/png')
}

/** 画像ファイルを回転させた新しい File を返す（未保存ファイルの回転用）。 */
export async function rotateImageFile(file: File, degrees: number): Promise<File> {
  const rotated = await rotateDataUrl(await fileToDataUrl(file), degrees)
  const blob = await (await fetch(rotated)).blob()
  return new File([blob], file.name, { type: blob.type, lastModified: file.lastModified })
}

/** 画像を data URL として読めるかどうか（拡張子ベースの簡易判定）。 */
export function isImageExtension(extension: string | null | undefined): boolean {
  const value = (extension ?? '').replace(/^\./, '').toLowerCase()
  return ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'heic', 'heif'].includes(value)
}

export function formatBytes(bytes: number | null): string {
  if (bytes === null || bytes <= 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

/** yyyy-MM-dd / ISO 文字列を yyyy/MM/dd 表示にする。 */
export function formatExamDate(value: string | null): string {
  if (!value) return '—'
  const trimmed = value.trim()
  if (trimmed === '') return '—'
  const datePart = trimmed.slice(0, 10)
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(datePart)
  if (match) return `${match[1]}/${match[2]}/${match[3]}`
  return trimmed.replace(/-/g, '/')
}
