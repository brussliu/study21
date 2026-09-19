/**
 * 編集中の下書きの控え（`localStorage`）。
 *
 * 詳細編集と学習画面は 2.0 と同じく**別ウィンドウ**で開くので、
 * 同じ画面の中の状態（メモリ）だけでは下書きを渡せない。
 * 同じブラウザーの同じオリジンなので、下書きだけを控えとして置き、別ウィンドウから読む。
 *
 * **保存済みの内容はここに入れない**（仮データ側が持つ）。下書きだけを置くので、
 * 保存していない内容が別ウィンドウに混ざることはない。
 */

import type { DemoWord } from '../types'

export const DRAFT_KEY = 'study21.japaneseDemo.draft'

export function readStoredDraft(): DemoWord | null {
  try {
    const raw = window.localStorage.getItem(DRAFT_KEY)
    return raw === null ? null : (JSON.parse(raw) as DemoWord)
  } catch {
    return null
  }
}

export function writeStoredDraft(word: DemoWord | null): void {
  try {
    if (word === null) {
      window.localStorage.removeItem(DRAFT_KEY)
    } else {
      window.localStorage.setItem(DRAFT_KEY, JSON.stringify(word))
    }
  } catch {
    // 保存できない環境（プライベートモード等）では控えを使わない
  }
}
