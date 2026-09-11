import { describe, expect, it } from 'vitest'
import { capitalize, isBlank, toKebabCase, truncate } from '../src/utils/string'
import { formatIsoDate, nowIso } from '../src/utils/date'

describe('string utils', () => {
  it('isBlank は null/undefined/空白を true と判定する', () => {
    expect(isBlank(null)).toBe(true)
    expect(isBlank(undefined)).toBe(true)
    expect(isBlank('   ')).toBe(true)
    expect(isBlank('a')).toBe(false)
  })

  it('truncate は上限を超えた文字列を省略する', () => {
    expect(truncate('abcdef', 3)).toBe('abc…')
    expect(truncate('ab', 3)).toBe('ab')
  })

  it('capitalize は先頭を大文字化する', () => {
    expect(capitalize('hello')).toBe('Hello')
    expect(capitalize('')).toBe('')
  })

  it('toKebabCase はキャメルケースをケバブケース化する', () => {
    expect(toKebabCase('HelloWorld')).toBe('hello-world')
  })
})

describe('date utils', () => {
  it('nowIso は ISO-8601 形式を返す', () => {
    const iso = nowIso()
    expect(iso).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/)
    expect(Number.isNaN(new Date(iso).getTime())).toBe(false)
  })

  it('formatIsoDate は不正な値に空文字を返す', () => {
    expect(formatIsoDate('invalid')).toBe('')
  })
})
