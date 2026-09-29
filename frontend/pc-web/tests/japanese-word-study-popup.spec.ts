import { afterEach, describe, expect, it } from 'vitest'
import {
  openJpnWordStudyPopup,
  studyUrl,
  studyWindowName
} from '@/features/japanese-word/studyPopup'

/**
 * 学習画面（単語情報管理の【詳細】）を**別ウィンドウで最大化して開く**約束。
 *
 * <p>利用者の指示（2026-09-25）: アプリ内のダイアログではなく**別ウィンドウ**で開き、
 * **開いたら自動で最大化**する。ブラウザでは `window.maximize()` を呼べないので、
 * 「画面の利用可能サイズ＋左上（0,0）」で開くことで最大化を表す。</p>
 */

/** `window.open` を捕まえる（テストでは本当のウィンドウは開けない）。 */
function stubWindowOpen(): { calls: { url: string; name: string; features: string }[]; restore: () => void } {
  const calls: { url: string; name: string; features: string }[] = []
  const original = window.open
  window.open = ((url?: string, name?: string, features?: string) => {
    calls.push({ url: String(url), name: String(name), features: String(features) })
    return { focus: () => undefined, closed: false } as unknown as Window
  }) as typeof window.open
  return {
    calls,
    restore: () => {
      window.open = original
    }
  }
}

const stubs: (() => void)[] = []

/** jsdom の screen はサイズを持たない（0）ので、実機と同じ値を入れて確かめる。 */
function stubScreen(): void {
  const original = Object.getOwnPropertyDescriptor(window, 'screen')
  Object.defineProperty(window, 'screen', {
    configurable: true,
    value: { width: 1920, height: 1080, availWidth: 1920, availHeight: 1040 }
  })
  stubs.push(() => {
    if (original) Object.defineProperty(window, 'screen', original)
  })
}

afterEach(() => {
  while (stubs.length > 0) stubs.pop()?.()
})

describe('学習画面の別ウィンドウ', () => {
  it('エリアは開いている画面に合わせる（管理画面から開いたら /admin）', () => {
    expect(studyUrl(101, '/admin/japanese-word')).toBe('/admin/japanese-word/study?wordId=101')
    expect(studyUrl(101, '/student/japanese-word')).toBe('/student/japanese-word/study?wordId=101')
    expect(studyUrl(101, '/parent/japanese-word')).toBe('/parent/japanese-word/study?wordId=101')
    // エリアが分からないときは /student（既定）
    expect(studyUrl(101, '/')).toBe('/student/japanese-word/study?wordId=101')
  })

  it('ウィンドウ名は語ごと（同じ語を二重に開かない）', () => {
    expect(studyWindowName(101)).toBe('jpWordStudy_101')
    expect(studyWindowName(102)).not.toBe(studyWindowName(101))
  })

  it('画面の利用可能サイズで左上に開く（＝自動最大化）', () => {
    stubScreen()
    const popup = stubWindowOpen()
    stubs.push(popup.restore)

    openJpnWordStudyPopup(101)

    expect(popup.calls).toHaveLength(1)
    expect(popup.calls[0].url).toBe('/student/japanese-word/study?wordId=101')
    expect(popup.calls[0].name).toBe('jpWordStudy_101')
    // 中央に小さく開くのではなく、利用可能な画面いっぱい＋左上（タスクバーを除いた 1040px）
    expect(popup.calls[0].features).toContain('width=1920')
    expect(popup.calls[0].features).toContain('height=1040')
    expect(popup.calls[0].features).toContain('left=0')
    expect(popup.calls[0].features).toContain('top=0')
    // ポップアップとして開く（タブではなく別ウィンドウ）
    expect(popup.calls[0].features).toContain('popup=yes')
  })
})
