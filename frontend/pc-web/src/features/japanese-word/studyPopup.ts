/**
 * 単語情報管理（実画面）から開く**学習画面（A. 勉強）の別ウィンドウ**。
 *
 * <p>利用者の指示（2026-09-25）: 一覧の【詳細】（目のアイコン）は**アプリ内のダイアログではなく
 * 別ウィンドウ**で開き、**開いたら自動で最大化**する。ブラウザでは `window.maximize()` を
 * 呼べないので、画面の利用可能サイズ＋左上（0,0）で開いて最大化に見せる
 * （{@link openPopupWindow} の `maximized`）。</p>
 *
 * <p>画面の中身は別ルート（`/{area}/japanese-word/study?wordId=…`）が描く。データは
 * その窓が `GET /words/{id}` で取り直す（クッキーと画面セッションは同じオリジンなので使える）。</p>
 *
 * <p>開いた窓は**中身だけ**を出す（利用者の指示: 上部の帯＝見出し・修正・閉じるは置かない。
 * 2.0 の学習画面も中身だけだった）。窓はブラウザの × で閉じ、修正は一覧の【修正】から行う。</p>
 */

import { openPopupWindow } from '@/features/japanese/popupWindow'

/** ウィンドウ名（同じ語を二重に開かない）。 */
export function studyWindowName(wordId: number): string {
  return `jpWordStudy_${wordId}`
}

/**
 * 学習画面の URL。
 *
 * <p>エリア（`/admin` `/student` `/parent`）は**いま開いている画面に合わせる**
 * （管理画面から開いたのに `/student` を開くと、権限で弾かれることがある）。
 */
export function studyUrl(wordId: number, pathname: string): string {
  const area = ['/admin', '/student', '/parent']
    .find((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`)) ?? '/student'
  return `${area}/japanese-word/study?wordId=${encodeURIComponent(String(wordId))}`
}

/** 学習画面を別ウィンドウで開く（自動最大化）。 */
export function openJpnWordStudyPopup(wordId: number): Window | null {
  return openPopupWindow({
    url: studyUrl(wordId, window.location.pathname),
    name: studyWindowName(wordId),
    maximized: true
  })
}
