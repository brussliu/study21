/**
 * 別ウィンドウ（popup）で画面を開くための小さな道具。
 *
 * 2.0 の `js/japanese_word.js` は「A. 勉強 詳細」を
 * `window.open('japanese_test_a.jsp?wordId=…&view=detail', 'jpWordA_<id>', …)` で
 * 別ウィンドウに開いていた。2.1 でも同じ見え方にするため、ここにまとめる。
 *
 * ウィンドウ名に語 ID を入れてあるので、同じ語を二重に開かない（2 つ目は既存を前に出す）。
 */

/** 開いたウィンドウを覚えておく（テストや後始末で使う）。 */
export const openedPopups = new Map<string, Window>()

export function openDemoPopup(options: {
  /** 開く URL（同一オリジン） */
  url: string
  /** ウィンドウ名（同じ名前にすると二重に開かない） */
  name: string
  width?: number
  height?: number
}): Window | null {
  const width = options.width ?? 1180
  const height = options.height ?? 900
  const left = Math.max(0, Math.round((window.screen.width - width) / 2))
  const top = Math.max(0, Math.round((window.screen.height - height) / 2))
  const features = [
    'popup=yes',
    `width=${width}`,
    `height=${height}`,
    `left=${left}`,
    `top=${top}`,
    'resizable=yes',
    'scrollbars=yes'
  ].join(',')

  const popup = window.open(options.url, options.name, features)
  if (popup !== null) {
    openedPopups.set(options.name, popup)
    popup.focus()
  }
  return popup
}

/** 学習画面（A. 勉強）を別ウィンドウで開く。 */
export function openStudyPopup(wordId: string, options: { draft?: boolean } = {}): Window | null {
  const query = new URLSearchParams({ wordId })
  if (options.draft === true) {
    query.set('draft', '1')
  }
  return openDemoPopup({
    url: `/student/japanese-demo/study?${query.toString()}`,
    name: `jpWordStudy_${wordId}${options.draft === true ? '_draft' : ''}`
  })
}

/** 詳細編集を別ウィンドウで開く。 */
export function openEditPopup(wordId: string): Window | null {
  return openDemoPopup({
    url: `/student/japanese-demo/edit?wordId=${encodeURIComponent(wordId)}`,
    name: `jpWordEdit_${wordId}`,
    width: 1320,
    height: 940
  })
}
