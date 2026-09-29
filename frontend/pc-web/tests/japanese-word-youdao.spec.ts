import { describe, expect, it } from 'vitest'
import { youdaoWordUrl } from '@/features/japanese-word/youdao'

/**
 * 単語の意味を**外部の辞書（有道）**で引くリンク。
 *
 * <p>利用者の指示（2026-09-26）: 単語一覧の「単語」をリンクにし、押すと有道の日本語辞書を
 * 開く。形は利用者が示した例のとおり
 * （`https://www.youdao.com/result?word=えくぼ&lang=ja`）。</p>
 *
 * <p>確かめる接縫: <b>単語から URL が決まる</b>こと（区切りと記号の扱い）。</p>
 */
describe('単語の辞書リンク', () => {
  it('利用者が示した例と同じ URL を作る（word と lang=ja）', () => {
    const url = new URL(youdaoWordUrl('えくぼ'))

    expect(`${url.origin}${url.pathname}`).toBe('https://www.youdao.com/result')
    expect(url.searchParams.get('word')).toBe('えくぼ')
    expect(url.searchParams.get('lang')).toBe('ja')
  })

  it('記号を含む語でもパラメータが壊れない（& や空白をそのまま繋げない）', () => {
    const url = new URL(youdaoWordUrl('a & b'))

    expect(url.searchParams.get('word')).toBe('a & b')
    expect(url.searchParams.get('lang')).toBe('ja')
    // `&` を生で繋ぐと word の途中でパラメータが切れる
    expect(youdaoWordUrl('a & b')).toContain('word=a%20%26%20b')
  })

  it('前後の空白は落とす（一覧の見出し語に紛れても引ける）', () => {
    expect(new URL(youdaoWordUrl('  勉強 ')).searchParams.get('word')).toBe('勉強')
  })
})
