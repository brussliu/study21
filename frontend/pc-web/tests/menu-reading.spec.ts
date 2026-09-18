import { describe, expect, it } from 'vitest'
import { resolveMenu } from '@/config/menuRegistry'

/**
 * 読書管理のメニュー（2026-09-14 の決定 Q9）。
 *
 * 【書籍管理】は管理者（全体書籍）と保護者（自分の家庭の本）の画面なので、
 * **生徒のメニューには出さない**（ルートは残るが API は 403）。
 * 【書籍閲覧】は 3 エリアとも出す（読むことと【自分の本棚】は生徒も使う）。
 */

function readingChildren(area: 'admin' | 'student' | 'parent', role?: 'ADMIN' | 'STUDENT' | 'GUARDIAN'): string[] {
  const menu = resolveMenu(area, role)
  const reading = menu.find((item) => item.id === 'reading')
  return (reading?.children ?? []).map((child) => String(child.id))
}

describe('メニュー: 読書管理の出し分け', () => {
  it('生徒には【書籍閲覧】だけを出す（【書籍管理】は出さない）', () => {
    expect(readingChildren('student', 'STUDENT')).toEqual(['reading-reader'])
  })

  it('保護者と管理者には【書籍管理】と【書籍閲覧】を出す', () => {
    expect(readingChildren('parent', 'GUARDIAN')).toEqual(['reading-books', 'reading-reader'])
    expect(readingChildren('admin', 'ADMIN')).toEqual(['reading-books', 'reading-reader'])
  })

  it('ロールが分からないときは両方出す（API 側が 403 で守る）', () => {
    expect(readingChildren('student', undefined)).toEqual(['reading-books', 'reading-reader'])
  })
})
