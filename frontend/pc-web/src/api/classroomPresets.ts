import { HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * 授業の「前置詞」プリセットの管理 API（admin-api `/api/admin/classroom-presets`）。
 *
 * 前置詞は授業の最初に選ぶ「AI に整理の方針を伝える文」で、設定テーブル（`COM_設定情報`）ではなく
 * **専用テーブル `CR_前置詞プリセット情報`** に複数行で持つ（設計 `tmp/classroom-ai-design.md` §3.4）。
 * そのため設定ページの「設定を保存」ではなく、この CRUD で管理する。
 *
 *   GET    /api/admin/classroom-presets        … 一覧（GLOBAL のみ・表示順）
 *   POST   /api/admin/classroom-presets        … 追加   req {name, text, displayOrder}
 *   PUT    /api/admin/classroom-presets/{id}   … 更新   req {name, text, displayOrder}
 *   DELETE /api/admin/classroom-presets/{id}   … 削除
 *
 * 授業の画面（新しい授業・録音中）が読むのは user-api の `GET /api/user/classroom/presets` で、
 * こちらは**管理者向けの編集**（同じテーブルの GLOBAL 行）。
 *
 * 注意: レスポンスの形はバックエンドの実装で確定する。ここでは
 * ・一覧は**配列**でも `{ items: [...] }` でも受ける（`presetRowsOf`）
 * ・追加・更新の戻りは使わない（呼び出し後に一覧を取り直す）
 * としてあるので、形が違っても画面側の変更は最小で済む。
 */

/** プリセット 1 件（`CR_前置詞プリセット情報` の GLOBAL 行）。 */
export interface ClassroomPresetRow {
  presetId: number
  name: string
  /** 前置詞のテキスト（AI に渡す方針） */
  text: string
  displayOrder: number
}

/** 追加・更新で送る形。 */
export interface ClassroomPresetSave {
  name: string
  text: string
  displayOrder: number
}

// パスは他機能と同じ書き方に合わせる（末尾スラッシュ付きの URL は Spring 側で 404 になるため）
const http = new HttpClient({ baseUrl: '/api/admin' })
/** 前置詞プリセットの API の前置き。 */
const BASE = '/classroom-presets'

/** 一覧のレスポンスを配列にする（配列でも `{items:[…]}` でも受ける）。 */
export function presetRowsOf(data: unknown): ClassroomPresetRow[] {
  if (Array.isArray(data)) return data as ClassroomPresetRow[]
  if (data !== null && typeof data === 'object') {
    const items = (data as { items?: unknown }).items
    if (Array.isArray(items)) return items as ClassroomPresetRow[]
    const presets = (data as { presets?: unknown }).presets
    if (Array.isArray(presets)) return presets as ClassroomPresetRow[]
  }
  return []
}

/** 一覧（GLOBAL のみ・表示順）。 */
export async function listClassroomPresets(): Promise<ClassroomPresetRow[]> {
  const response = await http.get<unknown>(BASE)
  return presetRowsOf(response.data)
}

/** 追加する。 */
export function createClassroomPreset(body: ClassroomPresetSave): Promise<ApiResponse<unknown>> {
  return http.post<unknown>(BASE, { body })
}

/** 更新する（ID は GLOBAL のプリセット）。 */
export function updateClassroomPreset(
  presetId: number, body: ClassroomPresetSave
): Promise<ApiResponse<unknown>> {
  return http.put<unknown>(`${BASE}/${presetId}`, { body })
}

/** 削除する。 */
export function deleteClassroomPreset(presetId: number): Promise<ApiResponse<unknown>> {
  return http.delete<unknown>(`${BASE}/${presetId}`)
}
