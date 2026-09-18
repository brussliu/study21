import type { BrowserExtensionDevice } from '@/api/browserExtension'

/**
 * ブラウザ拡張まわりの小物（ダウンロード・接続コードの表示・端末の表示）。
 *
 * 配布パッケージ（zip）は deploy 時に `extension/` から作られ、
 * フロントの `/downloads/`（nginx が配る静的ファイル）に置かれる。
 * 画面はその隣に出来る `.json` を読んで版とサイズを出す
 * （ローカル開発など、まだ作られていない場合は案内だけ出す）。
 */
export interface ExtensionPackage {
  /** manifest.json の version */
  version: string
  /** zip のファイル名 */
  file: string
  /** バイト数 */
  size: number
  /** 中に入っているファイル数 */
  files: number
  /** 作った時刻（ISO 8601） */
  builtAt: string
}

export const EXTENSION_ZIP_PATH = '/downloads/study21-extension.zip'
export const EXTENSION_PACKAGE_PATH = '/downloads/study21-extension.json'

/**
 * このシステムが配るブラウザ拡張（プラグイン）の一覧。
 *
 * いまは Web閲覧履歴を記録する Chrome 拡張だけ。増やすときはここに足す
 * （画面は一覧をそのまま並べる。配布物はビルド時に作る）。
 */
export interface BrowserPlugin {
  code: string
  name: string
  description: string
  /** インストールの手順（1 行 = 1 ステップ） */
  steps: string[]
  /** 配布パッケージ（zip）の場所 */
  zipPath: string
  /** 版・サイズを書いた情報ファイルの場所 */
  packagePath: string
}

export const BROWSER_PLUGINS: BrowserPlugin[] = [
  {
    code: 'browser-extension',
    name: 'ブラウザ記録（Chrome 拡張）',
    description: '見たページを Web閲覧履歴 に記録します。単語帳も使えます。',
    steps: [
      'ダウンロードした zip を解凍する',
      'Chrome で chrome://extensions を開く',
      '右上の「デベロッパー モード」を ON にする',
      '「パッケージ化されていない拡張機能を読み込む」で解凍したフォルダを選ぶ',
      '拡張の「設定」画面に接続コードを貼り付けて保存する（接続コードは Web閲覧履歴 の画面で発行）'
    ],
    zipPath: EXTENSION_ZIP_PATH,
    packagePath: EXTENSION_PACKAGE_PATH
  }
]

/** 接続コードを 4 文字ずつ区切って読みやすくする（値そのものは変わらない）。 */
export function formatConnectionToken(token: string, group = 4): string {
  const trimmed = token.trim()
  if (trimmed === '') return ''
  const chunks: string[] = []
  for (let index = 0; index < trimmed.length; index += group) {
    chunks.push(trimmed.slice(index, index + group))
  }
  return chunks.join(' ')
}

/** バイト数を人が読める形に。 */
export function formatBytes(size: number): string {
  if (!Number.isFinite(size) || size < 0) return '—'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

/**
 * 配布パッケージの情報を読む。まだ作られていなければ null。
 *
 * 静的ファイルなので API クライアント（HttpClient）ではなく fetch を直接使う。
 * 失敗しても画面は動かしたいので例外は投げない。
 */
export async function loadPluginPackage(plugin: BrowserPlugin): Promise<ExtensionPackage | null> {
  try {
    const response = await fetch(plugin.packagePath, { cache: 'no-store' })
    if (!response.ok) return null
    const data = (await response.json()) as Partial<ExtensionPackage>
    if (typeof data.version !== 'string' || data.version === '') return null
    return {
      version: data.version,
      file: typeof data.file === 'string' && data.file !== '' ? data.file : 'study21-extension.zip',
      size: typeof data.size === 'number' ? data.size : 0,
      files: typeof data.files === 'number' ? data.files : 0,
      builtAt: typeof data.builtAt === 'string' ? data.builtAt : ''
    }
  } catch {
    return null
  }
}

/** ダウンロード用の URL（版を付けて、更新後に古いファイルを掴まないようにする）。 */
export function pluginDownloadUrl(plugin: BrowserPlugin, pkg: ExtensionPackage | null): string {
  return pkg ? `${plugin.zipPath}?v=${encodeURIComponent(pkg.version)}` : plugin.zipPath
}

/** 一覧に出す「版 / サイズ / ファイル数」。まだ無ければ案内を返す。 */
export function packageLabel(pkg: ExtensionPackage | null): string {
  if (!pkg) return '配布パッケージはまだありません（デプロイ時に作られます）。'
  const parts = [`v${pkg.version}`]
  if (pkg.size > 0) parts.push(formatBytes(pkg.size))
  if (pkg.files > 0) parts.push(`${pkg.files} ファイル`)
  return parts.join(' / ')
}

/**
 * クリップボードへコピーする。
 * NAS 上の http 配信では navigator.clipboard が使えないため、旧来の方法へフォールバックする
 * （リンククリップの copyText と同じ理由）。
 */
export async function copyToClipboard(text: string): Promise<boolean> {
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

/** 端末一覧の 1 行に出すブラウザの名前（例: 'Chrome 153.0.0.0'）。 */
export function browserLabel(device: BrowserExtensionDevice): string {
  const name = device.browserType ?? '不明'
  return device.browserVersion ? `${name} ${device.browserVersion}` : name
}
