import { describe, expect, it } from 'vitest'
import type { JpnWord } from '@/api/japanese'
import {
  AI_STATE_NONE_LABEL,
  aiStateBadgeClass,
  aiStateLabelOf,
  aiStateOf,
  isAiStateDone,
  isAiStateFailed
} from '@/features/japanese-word/aiStateLabel'

/**
 * 一覧の「取得状態」列の表示（純関数）。
 *
 * 一覧 API は `v_jpn_word_ai_state` から 4 区画（A・B／C／D／E）にまとめた状態を返す。
 * ここでは**状態コードを画面の言葉に直す規則**だけを固定する
 * （API が返す状態の作り方は backend のテストが見る）。
 */
function wordWithAiState(aiState: JpnWord['aiState']): JpnWord {
  return { aiState } as JpnWord
}

describe('一覧の取得状態', () => {
  it('状態コードを一覧の言葉に直す（未実行は「未取得」）', () => {
    expect(aiStateLabelOf(null)).toBe(AI_STATE_NONE_LABEL)
    expect(aiStateLabelOf(undefined)).toBe('未取得')
    expect(aiStateLabelOf('')).toBe('未取得')
    expect(aiStateLabelOf('SUCCEEDED')).toBe('取得済')
    expect(aiStateLabelOf('FAILED')).toBe('失敗')
    expect(aiStateLabelOf('RUNNING')).toBe('取得中')
    expect(aiStateLabelOf('QUEUED')).toBe('取得待ち')
    expect(aiStateLabelOf('CANCELED')).toBe('中止')
    // 知らないコードは隠さずそのまま出す（状態を増やしたときに気づける）
    expect(aiStateLabelOf('SOMETHING_NEW')).toBe('SOMETHING_NEW')
  })

  it('4 区画（A・B＝詳細／C＝読み問題／D＝文脈問題／E＝漢字問題）を取り出す', () => {
    const row = wordWithAiState({
      detail: 'SUCCEEDED', reading: 'RUNNING', context: 'FAILED', kanji: null
    })

    expect(aiStateOf(row, 'AB')).toBe('SUCCEEDED')
    expect(aiStateOf(row, 'C')).toBe('RUNNING')
    expect(aiStateOf(row, 'D')).toBe('FAILED')
    expect(aiStateOf(row, 'E')).toBeNull()
  })

  it('API が状態を返していなければ、どの区画も未取得として扱う', () => {
    const row = { } as JpnWord

    expect(aiStateOf(row, 'AB')).toBeNull()
    expect(aiStateLabelOf(aiStateOf(row, 'AB'))).toBe('未取得')
  })

  it('取得済と失敗を区別する（列の見た目を変えるときに使う）', () => {
    expect(isAiStateDone('SUCCEEDED')).toBe(true)
    expect(isAiStateDone('FAILED')).toBe(false)
    expect(isAiStateDone(null)).toBe(false)
    expect(isAiStateFailed('FAILED')).toBe(true)
    expect(isAiStateFailed('SUCCEEDED')).toBe(false)
  })

  it('状態ごとにタグの色を決める（取得済=緑／失敗=赤／処理中=黄／未取得=グレー）', () => {
    // 「取得済」と「未取得」を色で見分けられるようにする（利用者の指示）
    expect(aiStateBadgeClass('SUCCEEDED')).toBe('badge--success')
    expect(aiStateBadgeClass('FAILED')).toBe('badge--danger')
    expect(aiStateBadgeClass('RUNNING')).toBe('badge--warning')
    expect(aiStateBadgeClass('QUEUED')).toBe('badge--warning')
    expect(aiStateBadgeClass('CANCELED')).toBe('badge--neutral')
    expect(aiStateBadgeClass(null)).toBe('badge--neutral')
    expect(aiStateBadgeClass(undefined)).toBe('badge--neutral')
    // 知らないコードも無色にはしない（未取得と同じグレーで出しつつ、言葉はそのまま）
    expect(aiStateBadgeClass('SOMETHING_NEW')).toBe('badge--neutral')
  })
})
