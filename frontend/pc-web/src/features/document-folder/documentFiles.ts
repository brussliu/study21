/**
 * 資料ファイル（DOC_資料詳細情報）のモックデータと判定ヘルパ。
 *
 * 2.0 の「内容」列のファイル一覧 → 画像プレビュー / ダウンロード を
 * 2.1 のプロトタイプ UI で再現するためのフロント側モック。
 * 実 API（admin-api の資料管理）が入ったら、このモジュールを API 呼び出しに差し替える。
 */

export type DocumentFileKind = 'image' | 'file'

export interface DocumentFile {
  /** 枝番号（DOC_資料詳細情報.枝番号） */
  branchNo: number
  fileName: string
  extension: string
  kind: DocumentFileKind
  /** 表示用サイズ（例: "156 KB"） */
  size: string
  /** 画像の場合の表示 URL（data:image/svg+xml 等）。非画像は undefined。 */
  url?: string
}

const IMAGE_EXTENSIONS = new Set(['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg'])

/** 拡張子から画像かどうかを判定する（2.0 の extIconInfo と同趣旨）。 */
export function isImageExtension(extension: string): boolean {
  return IMAGE_EXTENSIONS.has(extension.toLowerCase().replace(/^\./, ''))
}

function escapeXml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')
}

/** 画像ファイル用のプレースホルダ画像（外部通信なしで表示できる SVG データ URI）。 */
function svgDataUri(label: string, background: string, foreground = '#ffffff'): string {
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="720" height="520">` +
    `<rect width="720" height="520" fill="${background}"/>` +
    `<text x="50%" y="44%" fill="${foreground}" font-family="sans-serif" font-size="44" text-anchor="middle">資料画像</text>` +
    `<text x="50%" y="57%" fill="${foreground}" font-family="sans-serif" font-size="30" text-anchor="middle">${escapeXml(label)}</text>` +
    `</svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

interface FileSpec {
  fileName: string
  size: string
  background?: string
}

function buildFiles(specs: FileSpec[]): DocumentFile[] {
  return specs.map((spec, index) => {
    const dot = spec.fileName.lastIndexOf('.')
    const extension = dot >= 0 ? spec.fileName.slice(dot + 1) : ''
    const kind: DocumentFileKind = isImageExtension(extension) ? 'image' : 'file'
    return {
      branchNo: index + 1,
      fileName: spec.fileName,
      extension,
      kind,
      size: spec.size,
      url: kind === 'image' ? svgDataUri(spec.fileName, spec.background ?? '#4c7fbf') : undefined
    }
  })
}

const MOCK_FILES: Record<string, FileSpec[]> = {
  'DOC-0001': [
    { fileName: '英検2級_過去問_問題.jpg', size: '256 KB', background: '#c1516a' },
    { fileName: '英検2級_過去問_解答.jpg', size: '198 KB', background: '#c1516a' },
    { fileName: '英検2級_過去問_解説.pdf', size: '1.2 MB' }
  ],
  'DOC-0002': [
    { fileName: '合同条件_図形.png', size: '120 KB', background: '#4c9f70' },
    { fileName: '合同条件_問題.pdf', size: '640 KB' }
  ],
  'DOC-0003': [
    { fileName: '動詞の例文集.pdf', size: '2.3 MB' },
    { fileName: '動詞リスト.xlsx', size: '48 KB' }
  ],
  'DOC-0004': [
    { fileName: '名詞リスト.pdf', size: '1.1 MB' },
    { fileName: '名詞一覧.xlsx', size: '36 KB' }
  ]
}

/** 資料番号に紐づくファイル一覧（モック）。未知の番号は汎用サンプルを返す。 */
export function filesForDoc(docNo: string): DocumentFile[] {
  const key = docNo.trim().toUpperCase()
  const specs = MOCK_FILES[key] ?? [
    { fileName: '資料_プレビュー1.jpg', size: '88 KB', background: '#4c7fbf' },
    { fileName: '資料_プレビュー2.jpg', size: '72 KB', background: '#4c7fbf' },
    { fileName: '資料本体.pdf', size: '360 KB' }
  ]
  return buildFiles(specs)
}

/** 非画像ファイルのダウンロード用モック本文を生成する。 */
export function mockFileContent(docNo: string, file: DocumentFile): string {
  return [
    'Study 2.1 資料ファイル（モック）',
    `資料番号: ${docNo}`,
    `ファイル名: ${file.fileName}`,
    `枝番号: ${file.branchNo}`,
    `拡張子: ${file.extension || '（なし）'}`,
    '',
    'このファイルは UI 確認用のダミー内容です。',
    '実データは DOC_資料詳細情報（縮略ファイル / パス / ファイル名称）から提供されます。'
  ].join('\n')
}
