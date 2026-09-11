import { describe, expect, it } from 'vitest'
import { prototypePageMap, prototypePages } from '@/config/prototypePages.generated'

describe('migrated prototype pages', () => {
  it('旧ログインを除く94画面をVueコンポーネントとして登録する', () => {
    expect(prototypePages).toHaveLength(94)
    expect(prototypePageMap.has('home')).toBe(true)
    expect(prototypePageMap.has('english')).toBe(true)
    expect(prototypePageMap.has('math-wrong')).toBe(true)
    expect(prototypePageMap.has('study-monitor')).toBe(true)
  })

  it('登録された全画面に生成済みVueコンポーネントがある', () => {
    const components = import.meta.glob('@/views/prototype/generated/*.vue')
    expect(Object.keys(components)).toHaveLength(prototypePages.length)
  })
})
