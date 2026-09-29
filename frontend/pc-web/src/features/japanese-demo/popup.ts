/**
 * デモ画面（単語情報管理・詳細編集）から別ウィンドウで開くための小さな道具。
 *
 * <p>2.0 の `js/japanese_word.js` は「A. 勉強 詳細」を
 * `window.open('japanese_test_a.jsp?wordId=…&view=detail', 'jpWordA_<id>', …)` で
 * 別ウィンドウに開いていた。2.1 でも同じ見え方にするため、ここにまとめる。</p>
 *
 * <p>ウィンドウのサイズ・位置・名前の作り方は共通の {@link openPopupWindow} が持つ
 * （実画面の学習画面＝自動最大化も同じ道具を使う）。</p>
 */

import { openPopupWindow } from '@/features/japanese/popupWindow'

export { openedPopups } from '@/features/japanese/popupWindow'

export function openDemoPopup(options: {
  /** 開く URL（同一オリジン） */
  url: string
  /** ウィンドウ名（同じ名前にすると二重に開かない） */
  name: string
  width?: number
  height?: number
}): Window | null {
  return openPopupWindow(options)
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
