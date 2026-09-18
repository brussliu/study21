/**
 * 「使用モデル」のスロット選択（設定ページ共通）。
 *
 * 設定ページの各機能（AI 生図・授業録音など）は、**AI の接続情報を「AIモデル」ページと共通利用**する。
 * 「AIモデル」ページは プロバイダーごとに何本かのスロット（`qwen:1`〜`qwen:5` / `doubao:1`〜`2` /
 * `deepseek:1`〜`2` / `chatgpt:1`〜`2`）を持ち、それぞれにモデル名・API Key・URL を設定する。
 * 機能側は**スロットの ID**（例 `deepseek:1`）だけを持ち、接続はそのスロットから解決される。
 *
 * ここは `study2SettingRuntime.ts` の `aiModelOptions()` と**同じ並び・同じラベル**を作る
 * （外部コンポーネントが描く分頁＝AI生図 / AI授業記録 の「使用モデル」を、ランタイムが描く
 * 日本語単語AI などと同じ見た目・同じ選択肢にするため）。
 */

/** スロットを持つプロバイダー（ランタイムの `ESSAY_MODEL_PROVIDERS` と同じ並び）。 */
export const MODEL_SLOT_PROVIDERS: { key: string; label: string; prefix: string; slots: number[] }[] = [
  { key: 'qwen', label: '千問', prefix: 'qwen', slots: [1, 2, 3, 4, 5] },
  { key: 'doubao', label: '豆包', prefix: 'doubao', slots: [1, 2] },
  { key: 'deepseek', label: 'DeepSeek', prefix: 'deepseek', slots: [1, 2] },
  { key: 'chatgpt', label: 'OpenAI', prefix: 'chatgpt', slots: [1, 2] }
]

export interface ModelSlotOption {
  value: string
  label: string
}

/**
 * スロットの選択肢（`value` は `qwen:1` など、`label` は「千問 / モデル名」）。
 * `settings` は設定 API（`initSettings`）が返した値（`qwenModel`・`qwenModel2`… を含む）。
 * モデル名が未設定のスロットも「モデルN未設定」として出す（どこに設定するか分かるように）。
 */
export function modelSlotOptions(
  settings: Record<string, string> | null | undefined,
  includeBigModel = false
): ModelSlotOption[] {
  const values = settings ?? {}
  const options: ModelSlotOption[] = []
  for (const provider of MODEL_SLOT_PROVIDERS) {
    for (const slot of provider.slots) {
      const suffix = slot === 1 ? '' : String(slot)
      const model = values[`${provider.prefix}Model${suffix}`]
      options.push({
        value: `${provider.key}:${slot}`,
        label: `${provider.label} / ${model === undefined || model === '' ? `モデル${slot}未設定` : model}`
      })
    }
  }
  if (includeBigModel) {
    const model = values.bigmodelOcrModel
    options.push({
      value: 'bigmodel:1',
      label: `BigModel / 智譜 OCR / ${model === undefined || model === '' ? 'glm-ocr' : model}`
    })
  }
  return options
}

/**
 * 選択肢に**いまの値**が無ければ足す（DB に別スロットが入っていても選択が空にならないように）。
 * 足した選択肢は値そのものをラベルにする。
 */
export function withCurrentModelSlot(
  options: ModelSlotOption[], current: string
): ModelSlotOption[] {
  if (current === '' || options.some((option) => option.value === current)) return options
  return [...options, { value: current, label: `${current}（保存済み）` }]
}
