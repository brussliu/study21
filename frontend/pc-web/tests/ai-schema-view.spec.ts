import { describe, expect, it } from 'vitest'
import {
  fieldsOfSchema,
  flattenSchemaFields,
  type SchemaField
} from '@/features/system-settings/aiSchemaView'

/**
 * DTO から生成された JSON Schema を、Data TAB の「項目構造」表示へ変換する。
 *
 * 表示するのは フィールド名 / 型 / 説明（@Schema の description）/ 必須 / Enum の値 / 入れ子。
 * スキーマはサーバーが DTO から生成したものをそのまま使う（画面側に固定の定義を持たない）。
 */
const schemaOf = (value: unknown): SchemaField[] => fieldsOfSchema(value as Record<string, unknown>)

describe('AI 出力スキーマの項目構造', () => {
  it('フィールド名・型・説明・必須を順番どおりに取り出す', () => {
    const fields = schemaOf({
      type: 'object',
      properties: {
        コマンド: { type: 'array', items: { type: 'string' }, description: 'GeoGebra のコマンド' },
        分類: { type: 'string', enum: ['FIGURE', 'FUNCTION'], description: '作図の分類' },
        図形名: { type: 'string', description: '図形名' }
      },
      required: ['コマンド', '分類']
    })

    expect(fields.map((field) => field.name)).toEqual(['コマンド', '分類', '図形名'])
    expect(fields[0].type).toBe('array<string>')
    expect(fields[0].description).toBe('GeoGebra のコマンド')
    expect(fields[0].required).toBe(true)
    expect(fields[2].required).toBe(false)
  })

  it('Enum の値は型の表示に含める（選べる値が分かる）', () => {
    const fields = schemaOf({
      type: 'object',
      properties: { 分類: { type: 'string', enum: ['FIGURE', 'FUNCTION', 'MIXED', 'UNKNOWN'] } }
    })

    expect(fields[0].enumValues).toEqual(['FIGURE', 'FUNCTION', 'MIXED', 'UNKNOWN'])
    expect(fields[0].type).toContain('FIGURE')
    expect(fields[0].type).toContain('UNKNOWN')
  })

  it('入れ子の object は children に展開する（その場に展開された形）', () => {
    const fields = schemaOf({
      type: 'object',
      properties: {
        入れ子: {
          type: 'object',
          description: '入れ子のオブジェクト',
          properties: { 名前: { type: 'string', description: '名前' } },
          required: ['名前']
        }
      }
    })

    expect(fields[0].type).toBe('object')
    expect(fields[0].children.map((child) => child.name)).toEqual(['名前'])
    expect(fields[0].children[0].required).toBe(true)
  })

  it('配列の要素が object なら children に展開する', () => {
    const fields = schemaOf({
      type: 'object',
      properties: {
        一覧: { type: 'array', items: { type: 'object', properties: { 名前: { type: 'string' } } } }
      }
    })

    expect(fields[0].type).toBe('array<object>')
    expect(fields[0].children.map((child) => child.name)).toEqual(['名前'])
  })

  it('$ref は $defs を解決して展開する（繰り返し使う型）', () => {
    const fields = schemaOf({
      type: 'object',
      properties: {
        入れ子: { $ref: '#/$defs/Inner' },
        一覧: { type: 'array', items: { $ref: '#/$defs/Inner' } }
      },
      $defs: {
        Inner: {
          type: 'object',
          properties: { 名前: { type: 'string', description: '名前' } },
          required: ['名前']
        }
      }
    })

    expect(fields[0].type).toBe('object')
    expect(fields[0].children.map((child) => child.name)).toEqual(['名前'])
    expect(fields[0].children[0].required).toBe(true)
    expect(fields[1].children.map((child) => child.name)).toEqual(['名前'])
  })

  it('入れ子の行は開いているときだけ出す（深さ付き）', () => {
    const fields = schemaOf({
      type: 'object',
      properties: {
        入れ子: {
          type: 'object',
          properties: { 名前: { type: 'string' } }
        },
        名前2: { type: 'string' }
      }
    })

    const closed = flattenSchemaFields(fields, () => false)
    expect(closed.map((row) => row.name)).toEqual(['入れ子', '名前2'])
    expect(closed[0].hasChildren).toBe(true)
    expect(closed[1].hasChildren).toBe(false)

    const opened = flattenSchemaFields(fields, () => true)
    expect(opened.map((row) => row.name)).toEqual(['入れ子', '名前', '名前2'])
    expect(opened.map((row) => row.depth)).toEqual([0, 1, 0])
    expect(opened[0].path).toBe('入れ子')
    expect(opened[1].path).toBe('入れ子.名前')
  })

  it('壊れたスキーマでも落ちない', () => {
    expect(schemaOf(null)).toEqual([])
    expect(schemaOf({})).toEqual([])
    expect(schemaOf({ properties: { x: null } })[0].type).toBe('unknown')
  })
})
