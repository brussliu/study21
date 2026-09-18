import { HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * ブラウザ拡張の接続設定 API（/api/user/browser-extension）。
 * user-api の BrowserExtensionController と対応する。
 *
 * ここで発行する**接続コード**を拡張機能の設定画面に入れると、そのブラウザの
 * 閲覧イベントが `NET_Web閲覧履歴情報` に記録される（拡張側は
 * `extension/background.js`、受信は BrowserExtensionIngestController）。
 * 2.0 は認証が無く、拡張が送ってくる userId をそのまま信じていた。
 */
export interface BrowserExtensionDevice {
  registrationId: number
  /** 拡張が採番した端末識別子（'chrome-xxxx'）。Web閲覧履歴の端末IDと同じ */
  deviceId: string
  deviceName: string | null
  osType: string | null
  browserType: string | null
  browserVersion: string | null
  extensionVersion: string | null
  profileId: string | null
  status: string
  /** 最終心拍から 10 分以内か（拡張は 5 分ごとに心拍を送る） */
  online: boolean
  lastStartedAt: string | null
  lastSentAt: string | null
  lastHeartbeatAt: string | null
  createdAt: string | null
}

export interface BrowserExtensionRegistration {
  /** 接続コードを発行済みか */
  issued: boolean
  /** 接続コード（未発行なら null） */
  token: string | null
  deviceCount: number
  onlineCount: number
  devices: BrowserExtensionDevice[]
  message: string | null
}

// パスは他機能と同じ書き方に合わせる（末尾スラッシュ付きの URL は Spring 側で 404 になるため）
const http = new HttpClient({ baseUrl: '/api/user' })

/** 接続コードと接続中の端末（読み取りのみ）。 */
export function fetchBrowserExtensionRegistration(): Promise<ApiResponse<BrowserExtensionRegistration>> {
  return http.get<BrowserExtensionRegistration>('/browser-extension/registration')
}

/** 接続コードを発行する（発行済みならそのまま返る）。 */
export function issueBrowserExtensionToken(): Promise<ApiResponse<BrowserExtensionRegistration>> {
  return http.post<BrowserExtensionRegistration>('/browser-extension/registration')
}

/** 接続コードを再発行する（以前のコードは使えなくなる）。 */
export function reissueBrowserExtensionToken(): Promise<ApiResponse<BrowserExtensionRegistration>> {
  return http.post<BrowserExtensionRegistration>('/browser-extension/registration/reissue')
}
