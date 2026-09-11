import type { DocumentFileInfo } from '@/api/documents'

/**
 * ファイル表示の共通ロジック（種別・ラベル・クリック時の動作）。
 * 2.0 の document.jsp（内容列）に合わせ、拡張子ごとに色分けしたチップで表示する。
 */

// 拡張子 → 表示種別（2.0 の extIconInfo と同じ分類）
const WORD_EXTENSIONS = ['doc', 'docx', 'rtf', 'odt', 'pages']
const EXCEL_EXTENSIONS = ['xls', 'xlsx', 'csv', 'ods', 'numbers']
const IMAGE_EXTENSIONS = ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'heic', 'heif', 'svg']
const ARCHIVE_EXTENSIONS = ['zip', '7z', 'rar', 'tar', 'gz', 'tgz', 'bz2']

export type FileTone = 'image' | 'pdf' | 'word' | 'excel' | 'zip' | 'other'

/** チップの色分け用の種別。 */
export function fileTone(file: DocumentFileInfo): FileTone {
  const extension = (file.extension ?? '').toLowerCase()
  if (file.image || IMAGE_EXTENSIONS.includes(extension)) return 'image'
  if (extension === 'pdf') return 'pdf'
  if (WORD_EXTENSIONS.includes(extension)) return 'word'
  if (EXCEL_EXTENSIONS.includes(extension)) return 'excel'
  if (ARCHIVE_EXTENSIONS.includes(extension)) return 'zip'
  return 'other'
}

/** チップに出すラベル（拡張子の大文字。無ければ FILE）。 */
export function fileLabel(file: DocumentFileInfo): string {
  const extension = (file.extension ?? '').replace(/^\./, '').toUpperCase()
  return extension === '' ? 'FILE' : extension
}

const TONE_ICON: Record<FileTone, string> = {
  image: 'file-image',
  pdf: 'file-pdf',
  word: 'file-word',
  excel: 'file-excel',
  zip: 'file-archive',
  other: 'file-plain'
}

/** チップのアイコン名（スプライトのシンボル名）。 */
export function fileIcon(file: DocumentFileInfo): string {
  return TONE_ICON[fileTone(file)]
}

/** チップのクラス（種別ごとの配色）。 */
export function fileChipClass(file: DocumentFileInfo): string {
  return `doc-file-chip doc-file-chip--${fileTone(file)}`
}

/** ファイル一覧ダイアログなどで使うアイコン単体のクラス。 */
export function fileIconClass(file: DocumentFileInfo): string {
  return `doc-files-icon doc-files-icon--${fileTone(file)}`
}

export function isImageFile(file: DocumentFileInfo): boolean {
  return file.image
}

export function isPdfFile(file: DocumentFileInfo): boolean {
  return (file.extension ?? '').toLowerCase() === 'pdf'
}

/** クリックしたときの動作の説明（ツールチップ用）。 */
export function fileActionHint(file: DocumentFileInfo): string {
  if (isImageFile(file)) return '拡大表示'
  if (isPdfFile(file)) return '別タブで表示'
  return 'ダウンロード'
}

export function fileContentUrl(file: DocumentFileInfo, download = false): string {
  return `${file.contentUrl}${download ? '?download=true' : ''}`
}

export function downloadFile(file: DocumentFileInfo): void {
  const anchor = document.createElement('a')
  anchor.href = fileContentUrl(file, true)
  anchor.download = file.originalFileName
  anchor.click()
}

/**
 * ファイルを開く共通動作: 画像=拡大表示（showImage に委譲）/ PDF=別タブで表示 / その他=ダウンロード。
 * 画像の拡大表示は呼び出し側の UI（ビューア）が担当する。
 */
export function openFile(file: DocumentFileInfo, showImage: (file: DocumentFileInfo) => void): void {
  if (isImageFile(file)) { showImage(file); return }
  if (isPdfFile(file)) { window.open(fileContentUrl(file), '_blank', 'noopener'); return }
  downloadFile(file)
}
