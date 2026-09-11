import { describe, expect, it } from 'vitest'
import { paginationItems } from '@/features/pagination/pagination'

/** 'gap' を '…' に置き換えて比較しやすくする。 */
function render(current: number, total: number, siblings = 1): string {
  return paginationItems(current, total, siblings).map((item) => (item === 'gap' ? '…' : item)).join(' ')
}

describe('paginationItems', () => {
  it('ページ数が少ないときは全部表示する', () => {
    expect(render(1, 1)).toBe('1')
    expect(render(2, 5)).toBe('1 2 3 4 5')
    // 7 ページ（以前「1 2 7」と飛んで見えていたケース）は全て表示する
    expect(render(1, 7)).toBe('1 2 3 4 5 6 7')
    expect(render(4, 7)).toBe('1 2 3 4 5 6 7')
  })

  it('先頭付近では後ろ側を省略する', () => {
    expect(render(1, 20)).toBe('1 2 3 4 5 … 20')
    expect(render(3, 20)).toBe('1 2 3 4 5 … 20')
  })

  it('途中では前後を省略する', () => {
    expect(render(10, 20)).toBe('1 … 9 10 11 … 20')
    expect(render(4, 20)).toBe('1 … 3 4 5 … 20')
  })

  it('末尾付近では前側を省略する', () => {
    expect(render(18, 20)).toBe('1 … 16 17 18 19 20')
    expect(render(20, 20)).toBe('1 … 16 17 18 19 20')
  })

  it('範囲外のページ番号は丸める', () => {
    expect(render(0, 20)).toBe('1 2 3 4 5 … 20')
    expect(render(99, 20)).toBe('1 … 16 17 18 19 20')
    expect(render(1, 0)).toBe('1')
  })

  it('現在ページは必ず含まれる', () => {
    for (let current = 1; current <= 30; current += 1) {
      const items = paginationItems(current, 30)
      expect(items).toContain(current)
      expect(items[0]).toBe(1)
      expect(items[items.length - 1]).toBe(30)
      // 省略記号は連続しない
      for (let i = 1; i < items.length; i += 1) {
        expect(items[i] === 'gap' && items[i - 1] === 'gap').toBe(false)
      }
    }
  })

  it('siblings を増やすと表示件数が増える', () => {
    expect(render(10, 50, 2)).toBe('1 … 8 9 10 11 12 … 50')
  })
})
