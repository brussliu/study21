import { describe, expect, it } from 'vitest'
import { detailStatusOf, toDetail, toStudyWord } from '@/features/japanese-word/wordMapper'
import type { JpnCollection, JpnWordDetail, JpnWordDetailBody } from '@/api/japanese'
import { editorRequest } from '@/features/japanese-word/editorMapper'

/**
 * 詳細の**規範のキー**（バックエンドの産出）を持つ詳細（API が返す形）。
 * 2.0 の列名（`noteType` / `relationType`）は使わない。
 */
function apiDetail(): JpnWordDetail {
  return {
    detailId: 9,
    contentVersion: 1,
    version: 3,
    aiProvider: 'qwen',
    aiModel: 'qwen-max',
    fetchedAt: '2026-09-01T10:00:00',
    detail: {
      jlptLevel: 'N4',
      partOfSpeech: '名詞',
      chineseMeaning: '学习',
      coreMeaning: '学习',
      descriptionJa: '学問や技芸を学ぶこと。',
      descriptionZh: '学习学问或技艺。',
      manuallyCorrected: false,
      senses: [
        { number: 1, japanese: '学問や技芸を学ぶこと。', chinese: '学习', context: '学校・家庭', style: '普通', noteJapanese: null, noteChinese: '可作名词也可作动词' }
      ],
      examples: [
        { japanese: '毎日、日本語を勉強します。', reading: 'まいにち', chinese: '每天学习日语。', source: '日本語単語帳①', senseNumber: 1, level: 'BASIC' },
        { japanese: 'いい勉強になりました。', reading: null, chinese: '很有收获。', source: null, senseNumber: 2, level: 'APPLIED' }
      ],
      pronunciations: [
        { reading: 'べんきょう', accentNotation: '0', accentType: 0, moraCount: 4, audioUrl: null, audioProvider: null }
      ],
      collocations: [
        { expression: '勉強になる', reading: 'べんきょうになる', chinese: '有收获', usage: '会話で', exampleJapanese: 'とても勉強になりました。', exampleChinese: '很有收获。' }
      ],
      relatedWords: [
        { relation: '類義語', heading: '学習', reading: 'がくしゅう', chinese: '学习' }
      ],
      cautions: [
        { kind: 'MEANING', title: '中国語の「勉强」とは意味が異なる。', wrong: '無理に勉強する。', correct: '無理をする。', reason: '与中文含义不同' }
      ]
    }
  }
}

const collections: JpnCollection[] = [
  {
    collectionId: 1,
    level: 'N4',
    book: '日本語単語帳①',
    category: 'Unit001',
    wordSeq: 12,
    listedWord: '勉強',
    listedReading: 'べんきょう',
    listedPartOfSpeech: '名詞',
    chineseMeaning: '学习'
  }
]

describe('本番 API の形 → 新画面（学習画面）の形', () => {
  it('詳細の追加項目と旧補助項目を、並べ替えて繰り返し保存しても保持する', () => {
    const source = {
      word: { wordId: 101, word: '勉強', reading: 'べんきょう', jlptLevel: 'N4', partOfSpeech: '名詞',
        chineseMeaning: '学习；用功',
        stateCode: 'ACTIVE' as const, note: null, version: 2, book: null, category: null, level: null,
        wordSeq: null, collectionCount: 0, learnState: 'NOT_STARTED' as const, mastery: 0,
        answeredCount: 0, correctCount: 0, favorite: false, learned: false, lastStudiedAt: null, nextReviewAt: null,
        aiState: { detail: null, reading: null, context: null, kanji: null },
        detailCounts: null },
      collections, questions: [], detail: apiDetail()
    }
    const draft = toStudyWord({ ...source.word, collections, detail: source.detail })
    draft.detail!.examples.reverse()
    draft.detail!.patterns.push({ id: 'p-new', pattern: '勉強する', reading: '', chinese: '学习', example: '', exampleChinese: '' })
    draft.detail!.memoryHint = { hint: '覚えるヒント', basis: '记忆提示' }
    const first = editorRequest(source, draft, 'ACTIVE', '備考')
    expect(first.word.version).toBe(2)
    expect(first.contentVersion).toBe(1)
    // 例文のレベルは規範のキーに載る（並べ替えたので APPLIED が先）
    expect(first.detail.examples?.map(row => row.level)).toEqual(['APPLIED', 'BASIC'])
    expect(first.detail.examples?.[0]?.japanese).toBe('いい勉強になりました。')
    // 文型と記憶のヒントも規範のキーに載る
    expect(first.detail.patterns?.map(row => row.pattern)).toEqual(['勉強する'])
    expect(first.detail.memoryHint).toEqual({ hint: '覚えるヒント', basis: '记忆提示' })
    const saved = { ...source, detail: { ...source.detail, contentVersion: 2, version: 5, detail: first.detail } }
    const reloaded = toStudyWord({ ...saved.word, collections, detail: saved.detail })
    // 画面の id は番号から作り直すので、**中身**が往復することを見る
    expect(reloaded.detail!.examples.map(row => ({ ...row, id: '' })))
      .toEqual(draft.detail!.examples.map(row => ({ ...row, id: '' })))
    expect(reloaded.detail!.patterns.map(row => ({ ...row, id: '' })))
      .toEqual(draft.detail!.patterns.map(row => ({ ...row, id: '' })))
    expect(reloaded.detail!.memoryHint).toEqual(draft.detail!.memoryHint)
    reloaded.detail!.examples.reverse()
    const second = editorRequest(saved, reloaded, 'ACTIVE', '')
    expect(second.detail.examples?.map(row => row.japanese)).toEqual([
      '毎日、日本語を勉強します。',
      'いい勉強になりました。'
    ])
    expect(second.detail.senses?.[0]?.noteChinese).toBe('可作名词也可作动词')
    expect(second.detail.collocations?.[0]?.usage).toBe('会話で')
    expect(second.detail.manuallyCorrected).toBe(true)
    expect(second.detail.editorContent?.patterns).toHaveLength(1)
  })
  it('規範のキーの段落を、学習画面の分類へ 1 つずつ移す', () => {
    const detail = toDetail(apiDetail())

    expect(detail?.coreMeaning).toBe('学习')
    expect(detail?.descriptionJa).toBe('学問や技芸を学ぶこと。')
    expect(detail?.descriptionZh).toBe('学习学问或技艺。')
    expect(detail?.senses.map((sense) => sense.japanese)).toEqual(['学問や技芸を学ぶこと。'])
    expect(detail?.examples.map((example) => example.japanese)).toEqual([
      '毎日、日本語を勉強します。',
      'いい勉強になりました。'
    ])
    expect(detail?.pronunciation?.reading).toBe('べんきょう')
    expect(detail?.pronunciation?.accentType).toBe(0)
    expect(detail?.collocations.map((entry) => entry.expression)).toEqual(['勉強になる'])
    expect(detail?.relatedWords.map((entry) => entry.heading)).toEqual(['学習'])
    expect(detail?.relatedWords[0]?.relation).toBe('類義語')
    expect(detail?.cautions[0]?.kind).toBe('MEANING')
    expect(detail?.cautions[0]?.wrong).toBe('無理に勉強する。')
    // 使用場面は語義から作らず、規範の usageNotes を読む
    expect(detail?.usageNotes).toEqual([])
  })

  it('規範のキーに無い段落は空のままにする（推測で埋めない）', () => {
    const detail = toDetail(apiDetail())
    expect(detail?.dialogs).toEqual([])
    expect(detail?.conjugations).toEqual([])
    expect(detail?.memoryHint).toBeNull()
    expect(detail?.practices).toEqual([])
    expect(detail?.transitivityPair).toBeNull()
  })

  it('単語・収録・詳細を 1 つの語にまとめる', () => {
    const word = toStudyWord({
      wordId: 101,
      word: '勉強',
      reading: 'べんきょう',
      jlptLevel: 'N4',
      partOfSpeech: '名詞',
      collections,
      detail: apiDetail()
    })

    expect(word.heading).toBe('勉強')
    expect(word.reading).toBe('べんきょう')
    expect(word.jlpt).toBe('N4')
    expect(word.chineseMeaning).toBe('学习')
    expect(word.collections[0]).toMatchObject({
      book: '日本語単語帳①',
      unit: 'Unit001',
      seq: 12,
      listedChinese: '学习'
    })
    expect(word.detailStatus).toBe('GENERATED')
  })

  it('手で直した詳細は「編集済み」として扱い、詳細が無ければ「未生成」', () => {
    const edited = apiDetail()
    edited.detail.manuallyCorrected = true
    expect(detailStatusOf(edited)).toBe('EDITED')
    expect(detailStatusOf(null)).toBe('NOT_GENERATED')

    const word = toStudyWord({
      wordId: 1,
      word: '勉強',
      reading: 'べんきょう',
      jlptLevel: null,
      partOfSpeech: null,
      collections: [],
      detail: null
    })
    expect(word.detail).toBeNull()
    expect(word.manuallyEdited).toBe(false)
  })

  it('発音は規範の pronunciation を優先し、無ければ pronunciations から作る', () => {
    const body: JpnWordDetailBody = {
      pronunciation: { reading: 'あい', accentType: 1, accentNotation: 'あꜜい', hint: '「あ」を高く。', hasAudioSample: true },
      pronunciations: [{ reading: 'こい', accentNotation: '1', accentType: 1, moraCount: 2, audioUrl: null, audioProvider: null }]
    }
    expect(toDetail({ ...apiDetail(), detail: body })?.pronunciation).toEqual({
      reading: 'あい', accentType: 1, accentNotation: 'あꜜい', hint: '「あ」を高く。', hasAudioSample: true
    })

    const fallback = toDetail({ ...apiDetail(), detail: { pronunciations: body.pronunciations } })
    expect(fallback?.pronunciation).toMatchObject({ reading: 'こい', hint: '', hasAudioSample: false })
  })
})
