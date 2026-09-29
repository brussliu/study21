/**
 * タブ列（`role="tablist"`）のキーボード操作（←→・Home・End）。
 *
 * <p>リポジトリのタブ列は同じ作法で動かす（英語勉強・精読・英作文詳細の添削の回）。
 * `tablist` を描く要素の `@keydown` に、返り値をそのまま渡す。</p>
 *
 * <p>選んだタブは `click()` して（選択の状態は各画面が持つ）、焦点も移す
 * （`tabindex` は選択中だけ 0 にする＝roving tabindex と対で使う）。</p>
 */
export function useTabKeys(): (event: KeyboardEvent) => void {
  return (event: KeyboardEvent): void => {
    if (!['ArrowRight', 'ArrowLeft', 'Home', 'End'].includes(event.key)) {
      return
    }
    const tabs = Array.from(
      (event.currentTarget as HTMLElement).querySelectorAll<HTMLButtonElement>('[role="tab"]')
    )
    const index = tabs.indexOf(event.target as HTMLButtonElement)
    const next = event.key === 'Home'
      ? 0
      : event.key === 'End'
        ? tabs.length - 1
        : (index + (event.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length
    event.preventDefault()
    tabs[next]?.click()
    tabs[next]?.focus()
  }
}
