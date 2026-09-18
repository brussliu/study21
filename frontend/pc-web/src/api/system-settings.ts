import { HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * システム設定 API（/api/admin/setting）。
 * 型とエンドポイントは admin-api の SettingPageController と対応する。
 *
 * 画面（study2SettingRuntime と、そこに組み込まれる各セクション）は
 * 旧 Study2 の setting.jsp と同じ camelCase のフィールドキーで値を受け渡す。
 * フィールドキーと DB の (ページ区分, 設定キー) の変換はサーバー側の
 * `SettingPageFields` が行う（**未定義のキーを送ると 400 になり、他の設定もまとめて保存できない**）。
 */
export interface SettingFieldsResult {
  settings: Record<string, string>
}

/** AI 出力データ構造（DTO）の JSON Schema（設定ページの Data TAB 用）。 */
export interface AiResponseSchemaResult {
  /** バッチコード（batC51-A〜D / batC52 など）。 */
  taskCode: string
  /** DTO のクラス名（例: BatC51AResultDto）。 */
  dto: string
  /** DTO から生成された JSON Schema（サーバーが生成。画面に固定の定義は持たない）。 */
  schema: Record<string, unknown>
}

// パスは他機能と同じ書き方に合わせる（末尾スラッシュ付きの URL は Spring 側で 404 になるため）
const http = new HttpClient({ baseUrl: '/api/admin/setting' })

/** 設定画面の値を一括で読む（画面に対応するキーだけが返る）。 */
export function loadSettingFields(userId = 'setting.jsp'): Promise<ApiResponse<SettingFieldsResult>> {
  return http.post<SettingFieldsResult>('/initSettings', { body: { userId } })
}

/**
 * 設定画面の値を一括で保存する（渡したキーだけを upsert する）。
 *
 * 検証エラー（未定義キー・型不正・範囲外）は 400 になり、**何も保存されない**。
 */
export function saveSettingFields(
  settings: Record<string, string>,
  userId = 'setting.jsp'
): Promise<ApiResponse<SettingFieldsResult>> {
  return http.post<SettingFieldsResult>('/saveSettings', { body: { userId, settings } })
}

/**
 * AI 出力データ構造（DTO）の JSON Schema を取る（設定ページの Data TAB 用）。
 *
 * スキーマはサーバーが **DTO から自動生成**したもの（画面や DB に固定の JSON を持たない）。
 * DTO を直すと、この応答も、プロンプトへ注入する出力形式も同時に変わる。
 */
export function loadAiResponseSchema(taskCode: string): Promise<ApiResponse<AiResponseSchemaResult>> {
  return http.get<AiResponseSchemaResult>('/ai-response-schema', { params: { task: taskCode } })
}
