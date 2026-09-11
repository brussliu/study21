import type { DocumentFolder } from '@/api/documents'

/**
 * 資料の分類はフォルダ階層そのもの。
 * 大分類〜細分類の 4 列は、フォルダ階層の深さ 1〜4 に対応する（サーバ側で導出）。
 */
export const CATEGORY_LABELS = ['大分類', '中分類', '小分類', '細分類'] as const

/** フォルダ階層を根から順に並べた名称（未分類は空配列）。 */
export function folderPathNames(folders: DocumentFolder[], folderId: number | null): string[] {
  const byId = new Map(folders.map((folder) => [folder.folderId, folder]))
  const names: string[] = []
  const visited = new Set<number>()
  let current = folderId === null ? undefined : byId.get(folderId)
  while (current && !visited.has(current.folderId)) {
    visited.add(current.folderId)
    names.unshift(current.folderName)
    current = current.parentFolderId === null ? undefined : byId.get(current.parentFolderId)
  }
  return names
}

/** フォルダ階層から導出した分類（大分類〜細分類の 4 要素。未設定は空文字）。 */
export function folderCategories(folders: DocumentFolder[], folderId: number | null): string[] {
  const path = folderPathNames(folders, folderId)
  return CATEGORY_LABELS.map((_, index) => path[index] ?? '')
}

/** フォルダ選択のラベル（例: "02.勉強 / 03.国語"）。同名フォルダの判別にも使う。 */
export function folderPathLabel(folders: DocumentFolder[], folderId: number | null): string {
  return folderPathNames(folders, folderId).join(' / ')
}

/** フォルダ選択用の選択肢（階層パスの昇順）。 */
export function folderOptions(folders: DocumentFolder[]): Array<{ folderId: number; label: string }> {
  return folders
    .map((folder) => ({ folderId: folder.folderId, label: folderPathLabel(folders, folder.folderId) }))
    .sort((a, b) => a.label.localeCompare(b.label, 'ja', { numeric: true }))
}
