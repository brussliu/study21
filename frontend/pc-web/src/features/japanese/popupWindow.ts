/**
 * 別ウィンドウ（popup）で画面を開くための共通の道具。
 *
 * <p>2.0 は `window.open('japanese_test_a.jsp?wordId=…&view=detail', 'jpWordA_<id>', …)` のように
 * 学習画面・編集画面を別ウィンドウで開いていた。2.1 も同じ見え方にするため、
 * ウィンドウのサイズ・位置・名前の作り方をここ 1 か所に置く
 * （日本語勉強のポップアップから使う。デモの一覧・編集、実画面の単語情報管理）。</p>
 *
 * <p><b>ブラウザでは `window.maximize()` を呼べない</b>ので、「最大化」は
 * **画面の利用可能サイズ（`screen.availWidth` / `availHeight`）で左上（0,0）に開く**ことで表す。</p>
 */

/** 開いたウィンドウを覚えておく（テストや後始末で使う）。 */
export const openedPopups = new Map<string, Window>()

/** 既定のウィンドウサイズ（2.0 の学習画面に合わせた大きさ）。 */
const DEFAULT_WIDTH = 1180
const DEFAULT_HEIGHT = 900

/** 画面のサイズ（取れない環境では既定値）。 */
function screenSizeOf(): { width: number; height: number } {
  const width = Math.round(window.screen?.width || 0) || DEFAULT_WIDTH
  const height = Math.round(window.screen?.height || 0) || DEFAULT_HEIGHT
  return { width, height }
}

/** 画面の「利用可能な」サイズ（タスクバー等を除く。最大化のとき使う）。 */
function availableSizeOf(): { width: number; height: number } {
  const fallback = screenSizeOf()
  const width = Math.round(window.screen?.availWidth || 0) || fallback.width
  const height = Math.round(window.screen?.availHeight || 0) || fallback.height
  return { width, height }
}

export function openPopupWindow(options: {
  /** 開く URL（同一オリジン） */
  url: string
  /** ウィンドウ名（同じ名前にすると二重に開かない） */
  name: string
  width?: number
  height?: number
  /**
   * 自動最大化（画面いっぱいで左上に開く）。
   * ブラウザの API では最大化できないので、利用可能な画面サイズを指定して表す。
   */
  maximized?: boolean
}): Window | null {
  const screen = screenSizeOf()
  const available = availableSizeOf()
  const width = options.maximized === true ? available.width : (options.width ?? DEFAULT_WIDTH)
  const height = options.maximized === true ? available.height : (options.height ?? DEFAULT_HEIGHT)
  const left = options.maximized === true ? 0 : Math.max(0, Math.round((screen.width - width) / 2))
  const top = options.maximized === true ? 0 : Math.max(0, Math.round((screen.height - height) / 2))
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

/** 開いたウィンドウに親ウィンドウを前に出させる（閉じる前・close の前に呼ぶ）。 */
export function focusOpener(): void {
  try {
    if (window.opener !== null && !window.opener.closed) {
      window.opener.focus()
    }
  } catch {
    // 参照できないときは何もしない（別オリジンなど）
  }
}
