/**
 * 設定ページの **Data TAB**（AI 出力データ構造）を、素の DOM で描く。
 *
 * <p><b>なぜ Vue コンポーネントを載せないか</b>: 設定ページ（2.0 由来）は素の HTML を組み立てて
 * 描く。その中に 2 つ目の Vue アプリを載せると、設定ページの再描画と片付けがぶつかって
 * Vue の内部で落ちる（実際になった）。同じ見た目の部品を**素の DOM で描けば、Vue の
 * 寿命と無関係**になり、安全になる。</p>
 *
 * <p>見た目は {@code AiDataSchemaPanel.vue} と同じ（`.ai-data-schema*`）を使う。
 * 規則は {@code aiDataSchema.css}（1 か所）にあり、**Vue 部品もこのファイルも同じ CSS を読む**
 * （片方だけ規則が無いと、そこだけ見た目が崩れる。以前はこの経路が無装飾で、
 * 日本語単語AI の Data TAB だけ JSON Schema が長々と伸びていた）。
 * スキーマの変換（純関数）も同じ {@code aiSchemaView.ts} を使うので、
 * **DTO を直せばこの表示も自動で変わる**。</p>
 */

import { loadAiResponseSchema } from '@/api/system-settings'
import { fieldsOfSchema, flattenSchemaFields } from './aiSchemaView'
import '@/features/system-settings/aiDataSchema.css'
import '@/features/system-settings/system-settings.css'

/** 開いている行（`入れ子.名前`）。Data TAB ごとに覚える。 */
const expandedByTask = new Map<string, string[]>()

/** Data TAB ごとの取得結果（再描画で取り直さない）。 */
const schemaByTask = new Map<string, Record<string, unknown> | null>()

/**
 * いま動いている再試行の世代。新しい呼び出し（または {@link cancelAiDataSchemaRender}）で
 * 古い世代は止まる。**画面を離れた後にタイマーが動かない**ようにするための番号。
 */
let renderGeneration = 0

/** 予約済みの再試行タイマー（無ければ null）。 */
let pendingTimer: number | null = null

/** HTML へ埋め込む文字列を安全にする。 */
function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/** 1 行ぶんの HTML。 */
function rowHtml(row: {
  path: string; depth: number; name: string; type: string; description: string
  required: boolean; hasChildren: boolean; expanded: boolean
}): string {
  const toggle = row.hasChildren
    ? `<button type="button" class="ai-data-schema__toggle" aria-expanded="${row.expanded}"`
      + ` data-ai-data-toggle="${escapeHtml(row.path)}">${row.expanded ? '▼' : '▶'}</button>`
    : '<span class="ai-data-schema__toggle-space"></span>'
  const required = row.required
    ? `<span class="ai-data-schema__required" data-ai-data-required="${escapeHtml(row.path)}">必須</span>`
    : ''
  return `<tr class="ai-data-schema__row" data-ai-data-field="${escapeHtml(row.path)}"`
    + ` data-ai-data-depth="${row.depth}">`
    + `<td class="ai-data-schema__name">`
    + `<span class="ai-data-schema__indent" style="width:${row.depth * 16}px"></span>`
    + `${toggle}<code>${escapeHtml(row.name)}</code>${required}</td>`
    + `<td class="ai-data-schema__type"><code>${escapeHtml(row.type)}</code></td>`
    + `<td class="ai-data-schema__description">${escapeHtml(row.description)}</td>`
    + '</tr>'
}

/** 読み込んだスキーマから中身を組み立てる。 */
function bodyHtml(task: string, schema: Record<string, unknown>): string {
  const expanded = expandedByTask.get(task) ?? []
  const rows = flattenSchemaFields(fieldsOfSchema(schema), (path) => expanded.includes(path))
  return `<section class="ai-data-schema__block" data-ai-data-structure>`
    + '<h5 class="ai-data-schema__title">項目構造</h5>'
    + '<table class="ai-data-schema__table"><thead><tr>'
    + '<th scope="col">フィールド名</th><th scope="col">型</th><th scope="col">説明</th>'
    + '</tr></thead><tbody>'
    + rows.map(rowHtml).join('')
    + '</tbody></table></section>'
    + '<section class="ai-data-schema__block" data-ai-data-json>'
    + '<h5 class="ai-data-schema__title">JSON Schema</h5>'
    // 高さは aiDataSchema.css が抑え、枠の中を縦に送る。縦に送れる枠は
    // キーボードでも送れること（tabindex。Chrome DevTools の
    // 「Scrollable region must have keyboard access」を出さない）
    + `<pre class="ai-data-schema__json" tabindex="0" role="region" aria-label="JSON Schema">`
    + `${escapeHtml(JSON.stringify(schema, null, 2))}</pre>`
    + '</section>'
}

/** タブ全体の枠（読み込み中・エラーのときも同じ枠を使う）。 */
function frameHtml(task: string, dto: string, inner: string): string {
  return '<div class="ai-data-schema">'
    + '<p class="setting-help ai-data-schema__note" data-ai-data-note>'
    + `AI が返すデータの形は DTO（<code>${escapeHtml(dto || task)}</code>）が唯一の定義です（見るだけ）。</p>`
    + '<p class="ai-data-schema__lead">'
    + 'ここには DTO から自動生成した JSON Schema を表示します。<strong>ここでは編集できません</strong>。'
    + 'DTO を直すと、この内容と AI へ渡す出力形式（プロンプトへ注入する文）が同時に変わります。</p>'
    + inner
    + '</div>'
}

/** 中身を描き直す（開閉の切替で呼ぶ）。 */
function paint(host: HTMLElement, task: string): void {
  const schema = schemaByTask.get(task)
  if (!schema) {
    return
  }
  const dto = host.getAttribute('data-ai-dto') ?? ''
  host.innerHTML = frameHtml(task, dto, bodyHtml(task, schema))
}

/**
 * マウント点（`data-ai-data-schema-slot`）の中身を描く。
 *
 * <p>同じマウント点に 2 回呼ばれても、**既に描いてあれば何もしない**（取得もやり直さない）。</p>
 *
 * <p>属性名を Vue 側の部品（`AiDataSchemaPanel.vue` の {@code data-ai-data-schema}）と分けているのは、
 * 既にその部品が使われている区画（AI生図など）を**描き直さないため**（実際に衝突して壊れた）。</p>
 */
export async function renderAiDataSchema(host: HTMLElement): Promise<void> {
  const task = host.getAttribute('data-ai-data-schema-slot') ?? ''
  if (task === '') {
    return
  }
  // 開閉のハンドラは 1 回だけ付ける（描き直しで増やさない）
  if (host.dataset.aiDataBound !== '1') {
    host.dataset.aiDataBound = '1'
    host.addEventListener('click', (event) => {
      const target = event.target as HTMLElement | null
      const button = target?.closest('[data-ai-data-toggle]') as HTMLElement | null
      const path = button?.getAttribute('data-ai-data-toggle')
      if (!button || !path) {
        return
      }
      const current = expandedByTask.get(task) ?? []
      expandedByTask.set(task, current.includes(path)
        ? current.filter((item) => item !== path)
        : [...current, path])
      paint(host, task)
    })
  }

  if (schemaByTask.has(task)) {
    if (host.dataset.aiDataRendered !== '1') {
      host.dataset.aiDataRendered = '1'
      paint(host, task)
    }
    return
  }

  host.innerHTML = frameHtml(task, '', '<p class="ai-data-schema__state" data-ai-data-loading>読み込んでいます...</p>')
  try {
    const response = await loadAiResponseSchema(task)
    const data = response.data
    if (!response.success || !data || !data.schema) {
      host.innerHTML = frameHtml(task, '', `<p class="ai-data-schema__state is-error" data-ai-data-error>`
        + `${escapeHtml(response.message || 'AI 出力データ構造を読み込めませんでした。')}</p>`)
      return
    }
    schemaByTask.set(task, data.schema)
    host.setAttribute('data-ai-dto', data.dto ?? '')
    host.dataset.aiDataRendered = '1'
    paint(host, task)
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : 'AI 出力データ構造を読み込めませんでした。'
    host.innerHTML = frameHtml(task, '', '<p class="ai-data-schema__state is-error" data-ai-data-error>'
      + `${escapeHtml(message)}</p>`)
  }
}

/**
 * 設定ページにある Data TAB のマウント点をすべて描く。
 *
 * <p>設定ページの組み立ては非同期（設定の読み込み後）なので、**少しの間だけ繰り返し試す**。
 * 見つからなければ諦める（無限に回さない）。</p>
 *
 * <p>呼び直すと**前の世代は止まる**（再描画のたびにタイマーが増えないように）。
 * 画面を離れるときは {@link cancelAiDataSchemaRender} を呼ぶ。</p>
 */
export function renderAllAiDataSchemas(attempts = 10, intervalMs = 200): void {
  cancelAiDataSchemaRender()
  const generation = renderGeneration
  let left = attempts
  const run = (): void => {
    pendingTimer = null
    // 画面を離れた後（設定ページの片付けの後）にタイマーが動いても落とさない。
    // ここが無いと `document is not defined` でテストのスイートごと落ちる
    if (generation !== renderGeneration || typeof document === 'undefined') {
      return
    }
    const hosts = Array.from(document.querySelectorAll<HTMLElement>('[data-ai-data-schema-slot]'))
    for (const host of hosts) {
      void renderAiDataSchema(host)
    }
    left -= 1
    if (left > 0) {
      pendingTimer = window.setTimeout(run, intervalMs)
    }
  }
  run()
}

/**
 * 予約済みの再試行を止める（設定ページを離れるときに呼ぶ）。
 *
 * <p>描画の途中で画面が消えても、タイマーが残らないようにする。</p>
 */
export function cancelAiDataSchemaRender(): void {
  renderGeneration += 1
  if (pendingTimer !== null) {
    window.clearTimeout(pendingTimer)
    pendingTimer = null
  }
}
