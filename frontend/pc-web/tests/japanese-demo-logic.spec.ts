import { describe, expect, it } from 'vitest'
import {
  allocateUnits,
  compareUnit,
  displayFailureReason,
  emptyFilters,
  filterWords,
  hasFilter,
  paginate,
  parsePaste,
  splitReadings,
  summarizeParsed,
  unitIndex,
  unitName
} from '@/features/japanese-demo/logic'
import type { DemoBook, DemoWord } from '@/features/japanese-demo/types'

/**
 * 日本語勉強【単語情報管理】デモの純粋な処理。
 * デモは本番 API を使わないので、ここで業務ルール（貼り付けの解釈・Unit 割り当て・絞り込み）を固定する。
 */

function word(overrides: Partial<DemoWord> = {}): DemoWord {
  return {
    id: 'w-1',
    heading: '図書館',
    reading: 'としょかん',
    alternateReading: null,
    partOfSpeech: '名詞',
    jlpt: 'N5',
    chineseMeaning: '图书馆',
    detailStatus: 'GENERATED',
    failureReason: null,
    manuallyEdited: false,
    collections: [
      {
        id: 'c-1',
        book: 'デモ日本語 初級',
        unit: 'Unit001',
        seq: 3,
        listedWord: '図書館',
        listedReading: 'としょかん',
        listedChinese: '图书馆'
      }
    ],
    detail: null,
    updatedAt: '2026-09-14T10:00:00',
    demoNote: null,
    ...overrides
  }
}

const DISPLAY = { listMode: 'NORMAL' as const, detailMode: 'FULL' as const, saveMode: 'NORMAL' as const }

describe('デモ：貼り付けテキストの解釈', () => {
  it('タブ・カンマ・2 つ以上の空白で区切る（空白 1 つは区切らない）', () => {
    const rows = parsePaste(['図書館\tとしょかん\t图书馆', '相談，そうだん，商量', '準備  じゅんび  准备'].join('\n'))

    expect(rows.map((row) => [row.heading, row.reading, row.chineseMeaning])).toEqual([
      ['図書館', 'としょかん', '图书馆'],
      ['相談', 'そうだん', '商量'],
      ['準備', 'じゅんび', '准备']
    ])
    expect(rows.every((row) => row.state === 'OK')).toBe(true)
  })

  it('空行は飛ばすが、行番号は入力のまま残す', () => {
    const rows = parsePaste('図書館\tとしょかん\n\n\n相談\tそうだん')

    expect(rows.map((row) => row.line)).toEqual([1, 4])
  })

  it('区切りが読み取れない行は形式エラーにする（区切り文字の混在・区切りの無い長い行）', () => {
    // タブとカンマの混在: どちらの区切りか決められない
    const mixed = parsePaste('図書館\tとしょかん,图书馆')
    expect(mixed[0]?.state).toBe('FORMAT_ERROR')
    expect(mixed[0]?.note).toContain('区切り')

    // 区切りがまったく無く、空白だけの行: 見出し語の一部か、意味のつもりか決められない
    const noDelimiter = parsePaste('書類を出す 提出文件')
    expect(noDelimiter[0]?.state).toBe('FORMAT_ERROR')

    // 1 語だけの行は「読みが空」として扱う（見出し語だけの登録は許す）
    const single = parsePaste('書類を出す')
    expect(single[0]?.state).toBe('BLANK_READING')

    expect(summarizeParsed(mixed).errors).toBe(1)
    expect(summarizeParsed(mixed).importable).toBe(0)
  })

  it('見出し語が空・読みが空・区切りが多すぎる行を見分ける', () => {
    const rows = parsePaste(['\tとしょかん\t图书馆', '相談\t\t商量', '準備\tじゅんび\t准备\t多い'].join('\n'))

    expect(rows[0]?.state).toBe('BLANK_WORD')
    expect(rows[1]?.state).toBe('BLANK_READING')
    // 4 つ目以降はタブ区切りを保ったまま中国語意味へ足す（形式エラーにしない）
    expect(rows[2]?.chineseMeaning).toBe('准备 多い')
    expect(rows[2]?.state).toBe('OK')
  })

  it('同じ入力の中の重複を見つけ、先に出た行番号を案内する', () => {
    const rows = parsePaste(['図書館\tとしょかん\n相談\tそうだん\n図書館\tとしょかん'].join('\n'))

    expect(rows[2]?.state).toBe('DUPLICATE')
    expect(rows[2]?.note).toContain('1 行目')
    expect(summarizeParsed(rows).duplicates).toBe(1)
    expect(summarizeParsed(rows).importable).toBe(2)
  })

  it('母表にある語は「収録を追加」として区別する（重複として捨てない）', () => {
    const existing = [{ heading: '図書館', reading: 'としょかん' }]
    const rows = parsePaste('図書館\tとしょかん\t图书馆', existing)

    expect(rows[0]?.state).toBe('DUPLICATE_EXISTING')
    expect(rows[0]?.note).toContain('収録を追加')
    expect(summarizeParsed(rows).existingReuse).toBe(1)
    expect(summarizeParsed(rows).duplicates).toBe(0)
  })

  it('同じ見出し語でも読みが違えば重複にしない（同表記・別読み）', () => {
    const existing = [{ heading: '開く', reading: 'あく' }]
    const rows = parsePaste('開く\tひらく\t开；打开', existing)

    expect(rows[0]?.state).toBe('OK')
  })

  it('読みが複数書かれている行は、選ばせる状態にする（勝手に 1 つに決めない）', () => {
    const rows = parsePaste('開く\tあく・ひらく\t开；打开')

    expect(rows[0]?.state).toBe('MULTI_READING')
    expect(rows[0]?.readingCandidates).toEqual(['あく', 'ひらく'])
    expect(rows[0]?.note).toContain('別の単語')
    expect(splitReadings('あく/ひらく')).toEqual(['あく', 'ひらく'])
  })
})

describe('デモ：Unit の自動割り当て（入力順と容量だけ。AI の意味分類ではない）', () => {
  const book: DemoBook = {
    id: 'b-1',
    name: 'デモ日本語 初級',
    unitSize: 20,
    units: [
      { name: 'Unit001', count: 20, capacity: 20 },
      { name: 'Unit002', count: 18, capacity: 20 }
    ],
    note: ''
  }
  const words = (count: number) =>
    Array.from({ length: count }, (_, index) => ({ heading: `語${index + 1}`, reading: `ご${index + 1}` }))

  it('新規書籍は Unit001 から 20 語ずつ（45 語 → 20 / 20 / 5）', () => {
    const result = allocateUnits({
      words: words(45),
      book: null,
      newBookName: 'デモ日本語 初中級',
      unitSize: 20,
      placement: 'NEW_BOOK',
      currentUnitCount: 0
    })

    expect(result.startUnit).toBe('Unit001')
    expect(result.summaries.map((summary) => [summary.unit, summary.count])).toEqual([
      ['Unit001', 20],
      ['Unit002', 20],
      ['Unit003', 5]
    ])
    expect(result.words[0]).toEqual({ heading: '語1', reading: 'ご1', unit: 'Unit001', seq: 1 })
    expect(result.words[19]).toEqual({ heading: '語20', reading: 'ご20', unit: 'Unit001', seq: 20 })
    expect(result.words[20]).toEqual({ heading: '語21', reading: 'ご21', unit: 'Unit002', seq: 1 })
    expect(result.words[44]).toEqual({ heading: '語45', reading: 'ご45', unit: 'Unit003', seq: 5 })
  })

  it('既存の続きから: 未満の Unit を先に埋める（18 語入っている Unit002 に 5 語 → 2 + 3）', () => {
    const result = allocateUnits({
      words: words(5),
      book,
      newBookName: '',
      unitSize: 20,
      placement: 'CONTINUE',
      currentUnitCount: 18
    })

    expect(result.startUnit).toBe('Unit002')
    expect(result.summaries.map((summary) => [summary.unit, summary.count])).toEqual([
      ['Unit002', 2],
      ['Unit003', 3]
    ])
    // 順番は既存の続きから（19 番目、20 番目、次の Unit の 1 番目）
    expect(result.words.map((entry) => entry.seq)).toEqual([19, 20, 1, 2, 3])
    expect(result.words.map((entry) => entry.unit)).toEqual([
      'Unit002', 'Unit002', 'Unit003', 'Unit003', 'Unit003'
    ])
  })

  it('「新しい Unit から」を選ぶと、いまの Unit を埋めずに次の Unit から始める', () => {
    const result = allocateUnits({
      words: words(3),
      book,
      newBookName: '',
      unitSize: 20,
      placement: 'NEW_UNIT',
      currentUnitCount: 18
    })

    expect(result.startUnit).toBe('Unit003')
    expect(result.summaries.map((summary) => [summary.unit, summary.count])).toEqual([
      ['Unit003', 3]
    ])
    expect(result.words[0]?.seq).toBe(1)
  })

  it('Unit 名は番号で比べる（Unit010 > Unit009）', () => {
    expect(unitName(7)).toBe('Unit007')
    expect(unitIndex('Unit007')).toBe(7)
    expect(unitIndex('第7課')).toBeNull()
    expect(compareUnit('Unit010', 'Unit009')).toBeGreaterThan(0)
    expect(compareUnit('Unit002', 'Unit010')).toBeLessThan(0)
  })
})

describe('デモ：一覧の絞り込みとページング', () => {
  const words: DemoWord[] = [
    word({ id: 'w-1', heading: '図書館', chineseMeaning: '图书馆' }),
    word({
      id: 'w-2',
      heading: '開く',
      reading: 'あく',
      partOfSpeech: '動詞',
      jlpt: null,
      chineseMeaning: '开；打开',
      collections: [
        { id: 'c-2', book: '学校生活のことば', unit: 'Unit002', seq: 1, listedWord: '開く', listedReading: 'あく', listedChinese: '开' }
      ]
    }),
    word({
      id: 'w-3',
      heading: '相談',
      reading: 'そうだん',
      detailStatus: 'FAILED',
      failureReason: '生成サービスが応答しませんでした。',
      chineseMeaning: '商量；咨询',
      collections: [
        { id: 'c-3a', book: 'デモ日本語 初級', unit: 'Unit003', seq: 5, listedWord: '相談', listedReading: 'そうだん', listedChinese: '商量' },
        { id: 'c-3b', book: '学校生活のことば', unit: 'Unit001', seq: 2, listedWord: '相談', listedReading: 'そうだん', listedChinese: '商量' }
      ]
    })
  ]

  it('キーワードは見出し語・読み・中国語意味・品詞に当たる', () => {
    expect(filterWords(words, { ...emptyFilters(), keyword: 'としょかん' }, DISPLAY).map((w) => w.id)).toEqual(['w-1'])
    expect(filterWords(words, { ...emptyFilters(), keyword: '图书馆' }, DISPLAY).map((w) => w.id)).toEqual(['w-1'])
    expect(filterWords(words, { ...emptyFilters(), keyword: '動詞' }, DISPLAY).map((w) => w.id)).toEqual(['w-2'])
    expect(hasFilter({ ...emptyFilters(), keyword: 'x' })).toBe(true)
    expect(hasFilter(emptyFilters())).toBe(false)
  })

  it('詳細の状態で絞り込める（失敗と未生成は別の状態）', () => {
    expect(filterWords(words, { ...emptyFilters(), detailStatus: 'FAILED' }, DISPLAY).map((w) => w.id)).toEqual(['w-3'])
    expect(filterWords(words, { ...emptyFilters(), detailStatus: 'NOT_GENERATED' }, DISPLAY)).toEqual([])
  })

  it('「生成失敗」に切り替えたときは、理由が出る（空欄の行を作らない）', () => {
    const failedMode = { ...DISPLAY, listMode: 'FAILED' as const }
    const overridden = filterWords(words, emptyFilters(), failedMode)

    expect(overridden.length).toBe(3)
    for (const word of overridden) {
      expect(displayFailureReason(word, failedMode), word.id).toBeTruthy()
    }
    // 元から失敗している語は、その理由をそのまま出す
    expect(displayFailureReason(words[2]!, failedMode)).toContain('生成サービス')
    // 失敗以外の状態では理由を出さない（正常な語）
    expect(displayFailureReason(words[0]!, DISPLAY)).toBeNull()
  })

  it('デモ表示設定の「内容未生成／生成中／生成失敗」は全語の状態として見せる', () => {
    const notGenerated = { ...DISPLAY, listMode: 'NOT_GENERATED' as const }
    expect(filterWords(words, { ...emptyFilters(), detailStatus: 'NOT_GENERATED' }, notGenerated).length).toBe(3)

    const generating = { ...DISPLAY, listMode: 'GENERATING' as const }
    expect(filterWords(words, { ...emptyFilters(), detailStatus: 'RUNNING' }, generating).length).toBe(3)

    // 「該当なし」は一覧そのものを空にする
    const noResult = { ...DISPLAY, listMode: 'NO_RESULT' as const }
    expect(filterWords(words, emptyFilters(), noResult)).toEqual([])
  })

  it('書籍で絞ると、その書籍の中の Unit の範囲で判定する', () => {
    const inBook = filterWords(words, { ...emptyFilters(), book: '学校生活のことば' }, DISPLAY)
    expect(inBook.map((w) => w.id)).toEqual(['w-2', 'w-3'])

    const unitRange = filterWords(
      words,
      { ...emptyFilters(), book: '学校生活のことば', unitFrom: 'Unit002', unitTo: 'Unit003' },
      DISPLAY
    )
    expect(unitRange.map((w) => w.id)).toEqual(['w-2'])
  })

  it('複数の書籍に載っている語は、どの書籍で絞っても出る', () => {
    expect(filterWords(words, { ...emptyFilters(), book: 'デモ日本語 初級' }, DISPLAY).map((w) => w.id))
      .toEqual(['w-1', 'w-3'])
  })

  it('ページングは範囲外のページを丸める', () => {
    const items = Array.from({ length: 25 }, (_, index) => index + 1)
    expect(paginate(items, 1, 20)).toMatchObject({ page: 1, totalPages: 2, total: 25 })
    expect(paginate(items, 1, 20).items.length).toBe(20)
    expect(paginate(items, 9, 20).page).toBe(2)
    expect(paginate(items, 2, 20).items.length).toBe(5)
    expect(paginate([], 1, 20)).toMatchObject({ page: 1, totalPages: 1, total: 0 })
  })
})
