import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { toDetail } from '@/features/japanese-word/wordMapper'
import type { JpnWordDetail, JpnWordDetailBody } from '@/api/japanese'
import DemoWordStudyView from '@/views/japanese/demo/DemoWordStudyView.vue'
import type { DemoWord } from '@/features/japanese-demo/types'

/**
 * 単語詳細の**規範のキー**（バックエンドが組み立てて返すキー）を、学習画面の形へ移す。
 *
 * 期待値は実装からではなく、バックエンドの組み立てテスト
 * （`JpnWordDetailAssemblerTest`）と同じ値で**手書き**している。
 * `editorContent` は付けない（本番の API は規範のキーだけを返す）。
 */
function canonical(): JpnWordDetail {
  return {
    detailId: 900,
    contentVersion: 3,
    version: 12,
    aiProvider: 'qwen',
    aiModel: 'qwen3.7-plus',
    fetchedAt: '2026-10-01T09:00:00',
    detail: {
      jlptLevel: 'N3',
      partOfSpeech: '動詞',
      conjugation: 'なし',
      transitivity: 'NONE',
      importance: 5,
      chineseMeaning: '爱；喜爱',
      coreMeaning: '爱；喜爱',
      descriptionJa: '人や物を大切に思う気持ち。',
      descriptionZh: '对人或物怀有珍视的感情。',
      manuallyCorrected: false,
      structured: { detail: { jlpt: 'N3' } },
      senses: [
        {
          number: 1,
          japanese: '大切に思う気持ち。',
          chinese: '爱；喜爱',
          context: '家族・友人',
          style: 'NEUTRAL',
          noteJapanese: '対象が広い。',
          noteChinese: '对象很广。'
        }
      ],
      examples: [
        {
          japanese: '親の愛は無条件だ。',
          reading: 'おやのあいはむじょうけんだ。',
          chinese: '父母的爱是无条件的。',
          senseNumber: 1,
          source: '家族',
          level: 'BASIC'
        },
        {
          japanese: '彼は家族を愛している。',
          reading: 'かれはかぞくをあいしている。',
          chinese: '他深爱着家人。',
          senseNumber: null,
          source: null,
          level: 'APPLIED'
        }
      ],
      patterns: [
        {
          pattern: '**を**愛する',
          reading: 'をあいする',
          chinese: '爱…',
          example: '音楽を愛する。',
          exampleChinese: '热爱音乐。'
        }
      ],
      dialogs: [
        {
          scene: '職員室で先生に相談する',
          lines: [
            { speaker: '先生', japanese: '家族を愛していますか。', chinese: '你爱家人吗。' },
            { speaker: '学生', japanese: 'はい、とても愛しています。', chinese: '是的，非常爱。' }
          ]
        }
      ],
      synonyms: [
        {
          heading: '恋',
          reading: 'こい',
          chinese: '恋爱',
          shared: '大切に思う',
          difference: '「恋」は恋愛感情。',
          scene: '恋愛の場面'
        }
      ],
      cautions: [
        {
          kind: 'MEANING',
          title: '「恋」との違い',
          wrong: '文化を恋する。',
          correct: '文化を愛する。',
          reason: '「恋」は恋愛に限る。'
        }
      ],
      conjugations: [
        { form: 'て形', value: '愛して', example: '家族を愛している。' }
      ],
      transitivityPair: {
        intransitive: '愛される',
        transitive: '愛する',
        particleNote: '～を／～に',
        intransitiveExample: '彼は愛されている。',
        transitiveExample: '彼は家族を愛している。'
      },
      pronunciation: {
        reading: 'あい',
        accentType: 1,
        accentNotation: 'あꜜい',
        hint: '「あ」を高く。',
        hasAudioSample: false
      },
      pronunciations: [
        {
          reading: 'あい',
          accentNotation: 'あꜜい',
          accentType: 1,
          moraCount: 2,
          audioUrl: null,
          audioProvider: null
        }
      ],
      collocations: [
        {
          expression: '愛を込める',
          reading: 'あいをこめる',
          chinese: '倾注爱意',
          usage: '手紙で',
          exampleJapanese: '心を込めて愛を伝える。',
          exampleChinese: '用心传达爱意。'
        }
      ],
      relatedWords: [
        { relation: '類義語', heading: '恋', reading: 'こい', chinese: '恋爱' },
        { relation: '対義語', heading: '憎む', reading: 'にくむ', chinese: '憎恨' }
      ],
      usageNotes: [
        { senseNumber: 1, register: 'どちらも', politeness: '普通', audience: '家族に', note: '書き言葉でも使う。' }
      ],
      memoryHint: { hint: '「心」が真ん中にある字。', basis: '漢字の形' },
      practices: [
        {
          kind: 'PARTICLE',
          question: '音楽（ ）愛する。',
          questionChinese: '热爱音乐。',
          choices: ['に', 'を'],
          freeWriting: false,
          answer: 'を',
          explanation: '対象は「を」。'
        },
        {
          kind: 'WRITING',
          question: '「愛する」を使って文を作りましょう。',
          questionChinese: '用「愛する」造句。',
          choices: [],
          freeWriting: true,
          answer: '私は家族を愛している。',
          explanation: '対象は「を」で示す。'
        }
      ]
    }
  }
}

describe('詳細の規範キー → 学習画面の形', () => {
  it('注意の区分・見出し・誤り・正しい・理由を、規範のキーから取る', () => {
    const detail = toDetail(canonical())

    expect(detail?.cautions).toEqual([
      {
        id: 'caution-0',
        kind: 'MEANING',
        title: '「恋」との違い',
        wrong: '文化を恋する。',
        correct: '文化を愛する。',
        reason: '「恋」は恋愛に限る。'
      }
    ])
  })

  it('関連語の関係を、規範のキーからそのまま取る', () => {
    const detail = toDetail(canonical())

    expect(detail?.relatedWords.map((row) => row.relation)).toEqual(['類義語', '対義語'])
    expect(detail?.relatedWords.map((row) => row.heading)).toEqual(['恋', '憎む'])
  })

  it('例文のレベルを、規範のキーから取る（既定に寄せない）', () => {
    const detail = toDetail(canonical())

    expect(detail?.examples.map((row) => row.level)).toEqual(['BASIC', 'APPLIED'])
    expect(detail?.examples.map((row) => row.senseNumber)).toEqual([1, null])
  })

  it('文型を、規範のキーから取る', () => {
    const detail = toDetail(canonical())

    expect(detail?.patterns).toEqual([
      {
        id: 'pattern-0',
        pattern: '**を**愛する',
        reading: 'をあいする',
        chinese: '爱…',
        example: '音楽を愛する。',
        exampleChinese: '热爱音乐。'
      }
    ])
  })

  it('会話を、規範のキーの 2 層（dialogs[].lines[]）から取る', () => {
    const detail = toDetail(canonical())

    expect(detail?.dialogs).toEqual([
      {
        id: 'dialog-0',
        scene: '職員室で先生に相談する',
        lines: [
          { id: 'dialog-0-line-0', speaker: '先生', japanese: '家族を愛していますか。', chinese: '你爱家人吗。' },
          { id: 'dialog-0-line-1', speaker: '学生', japanese: 'はい、とても愛しています。', chinese: '是的，非常爱。' }
        ]
      }
    ])
  })

  it('類義語を、規範のキーから取る', () => {
    const detail = toDetail(canonical())

    expect(detail?.synonyms[0]).toMatchObject({
      id: 'synonym-0',
      heading: '恋',
      reading: 'こい',
      chinese: '恋爱',
      shared: '大切に思う',
      difference: '「恋」は恋愛感情。',
      usage: '恋愛の場面'
    })
  })

  it('活用形を、規範のキーから取る', () => {
    const detail = toDetail(canonical())

    expect(detail?.conjugations).toEqual([
      { id: 'conjugation-0', form: 'て形', value: '愛して', example: '家族を愛している。' }
    ])
  })

  it('自他動詞の対応と記憶のヒントを、規範のキーから取る', () => {
    const detail = toDetail(canonical())

    expect(detail?.transitivityPair).toEqual({
      intransitive: '愛される',
      transitive: '愛する',
      particleNote: '～を／～に',
      intransitiveExample: '彼は愛されている。',
      transitiveExample: '彼は家族を愛している。'
    })
    expect(detail?.memoryHint).toEqual({ hint: '「心」が真ん中にある字。', basis: '漢字の形' })
  })

  it('発音は規範の pronunciation を使う（hint と音声の有無も）', () => {
    const detail = toDetail(canonical())

    expect(detail?.pronunciation).toEqual({
      reading: 'あい',
      accentType: 1,
      accentNotation: 'あꜜい',
      hint: '「あ」を高く。',
      hasAudioSample: false
    })
  })

  it('練習を、規範のキーから取る（選択肢は文字列の配列のまま）', () => {
    const detail = toDetail(canonical())

    expect(detail?.practices[0]).toMatchObject({
      id: 'practice-0',
      kind: 'PARTICLE',
      question: '音楽（ ）愛する。',
      questionChinese: '热爱音乐。',
      choices: ['に', 'を'],
      freeWriting: false,
      answer: 'を',
      explanation: '対象は「を」。'
    })
    expect(detail?.practices[1]?.freeWriting).toBe(true)
  })

  it('使用場面は、語義から作らずに規範の usageNotes を読む', () => {
    const detail = toDetail(canonical())

    expect(detail?.usageNotes).toEqual([
      { id: 'usage-0', senseNumber: 1, register: 'どちらも', politeness: '普通', audience: '家族に', note: '書き言葉でも使う。' }
    ])
    // 語義の context / style はもう混ぜない
    expect(detail?.usageNotes.some((note) => note.audience === '家族・友人')).toBe(false)
  })

  it('語の意味と説明を、規範のキーから取る', () => {
    const detail = toDetail(canonical())

    expect(detail?.coreMeaning).toBe('爱；喜爱')
    expect(detail?.descriptionJa).toBe('人や物を大切に思う気持ち。')
    expect(detail?.descriptionZh).toBe('对人或物怀有珍视的感情。')
    expect(detail?.senses[0]?.japanese).toBe('大切に思う気持ち。')
    expect(detail?.collocations[0]).toEqual({
      id: 'collocation-0',
      expression: '愛を込める',
      reading: 'あいをこめる',
      chinese: '倾注爱意',
      usage: '手紙で'
    })
  })

  it('規範のキーが空の段落は空配列・null のままにする（推測で埋めない）', () => {
    const source = canonical()
    source.detail.patterns = []
    source.detail.dialogs = []
    source.detail.synonyms = []
    source.detail.conjugations = []
    source.detail.practices = []
    source.detail.usageNotes = []
    source.detail.transitivityPair = null
    source.detail.memoryHint = null
    source.detail.pronunciation = undefined
    source.detail.pronunciations = []

    const detail = toDetail(source)

    expect(detail?.patterns).toEqual([])
    expect(detail?.dialogs).toEqual([])
    expect(detail?.synonyms).toEqual([])
    expect(detail?.conjugations).toEqual([])
    expect(detail?.practices).toEqual([])
    expect(detail?.usageNotes).toEqual([])
    expect(detail?.transitivityPair).toBeNull()
    expect(detail?.memoryHint).toBeNull()
    expect(detail?.pronunciation).toBeNull()
  })
})

describe('学習画面：規範のキーの詳細をすべて出す', () => {
  function studyWord(wordId: number): DemoWord {
    const detail = toDetail(canonical())!
    return {
      id: String(wordId),
      heading: '愛する',
      reading: 'あいする',
      alternateReading: null,
      partOfSpeech: '動詞',
      jlpt: 'N3',
      chineseMeaning: '爱；喜爱',
      detailStatus: 'GENERATED',
      failureReason: null,
      manuallyEdited: false,
      collections: [],
      detail,
      updatedAt: '',
      demoNote: null
    }
  }

  function mountStudy(): ReturnType<typeof mount> {
    const pinia = createPinia()
    setActivePinia(pinia)
    return mount(DemoWordStudyView, { props: { word: studyWord(900) }, global: { plugins: [pinia] } })
  }

  it('文型・会話・類義語・練習・活用・自対動詞・使用場面が、それぞれの欄に出る', async () => {
    const wrapper = mountStudy()

    // 文型（コロケーションのタブ）
    await wrapper.get('[data-demo-study-tab="collocations"]').trigger('click')
    expect(wrapper.get('[data-demo-study-pattern]').text()).toContain('を愛する')

    // 会話（例文・会話のタブ）
    await wrapper.get('[data-demo-study-tab="examples"]').trigger('click')
    const dialog = wrapper.get('[data-demo-study-dialog]')
    expect(dialog.text()).toContain('職員室で先生に相談する')
    expect(dialog.text()).toContain('家族を愛していますか。')

    // 類義語・使用場面（関連語・使用注意のタブ）
    await wrapper.get('[data-demo-study-tab="compare"]').trigger('click')
    const compare = wrapper.get('[data-demo-study-panel="compare"]')
    expect(compare.get('[data-demo-study-synonym]').text()).toContain('恋')
    expect(compare.text()).toContain('「恋」は恋愛感情。')
    expect(compare.text()).toContain('書き言葉でも使う。')

    // 活用と自他動詞（発音・アクセントのタブ）
    await wrapper.get('[data-demo-study-tab="form"]').trigger('click')
    expect(wrapper.get('[data-demo-study-conjugation]').text()).toContain('て形')
    expect(wrapper.get('[data-demo-study-transitivity]').text()).toContain('愛される')

    // 練習（練習のタブ）
    await wrapper.get('[data-demo-study-tab="practice"]').trigger('click')
    expect(wrapper.get('[data-demo-study-quiz]').text()).toContain('音楽（ ）愛する。')
  })

  it('記憶のヒントを、語義・説明のタブに出す', async () => {
    const wrapper = mountStudy()

    await wrapper.get('[data-demo-study-tab="meaning"]').trigger('click')

    expect(wrapper.get('[data-demo-study-memory]').text()).toContain('「心」が真ん中にある字。')
  })
})

/**
 * バックエンドの**規範 JSON**（`GET /words/{wordId}` の `detail.detail`。組み立ての産出そのもの）。
 *
 * 期待値は実装から作らず、バックエンドの契約テスト（`JpnWordDetailContractTest` の
 * `assemble` 結果）と同じ値を**手書き**している。すべての段落に 1 件以上入れてある。
 */
const CANONICAL_BODY_JSON = `{
  "coreMeaning":"爱；喜爱","chineseMeaning":"爱；喜爱",
  "descriptionJa":"人や物を大切に思う気持ち。","descriptionZh":"对人或物怀有珍视的感情。",
  "partOfSpeech":"名詞","jlptLevel":"N3","conjugation":"なし","transitivity":"NONE",
  "importance":5,"manuallyCorrected":false,
  "senses":[{"number":1,"japanese":"大切に思う気持ち。","chinese":"爱；喜爱",
             "context":"家族・友人","style":"NEUTRAL","noteJapanese":"対象が広い。","noteChinese":"对象很广。"}],
  "examples":[{"japanese":"親の愛は無条件だ。","reading":"おやのあいはむじょうけんだ。",
               "chinese":"父母的爱是无条件的。","senseNumber":1,"source":"家族","level":"BASIC"},
              {"japanese":"彼は植物を愛している。","reading":"かれはしょくぶつをあいしている。",
               "chinese":"他热爱植物。","senseNumber":1,"source":"","level":"APPLIED"}],
  "patterns":[{"pattern":"**を**愛する","reading":"をあいする","chinese":"爱…",
               "example":"音楽を愛する。","exampleChinese":"热爱音乐。"}],
  "dialogs":[{"scene":"職員室で先生に相談する",
              "lines":[{"speaker":"先生","japanese":"家族を愛していますか。","chinese":"你爱家人吗。"},
                       {"speaker":"学生","japanese":"はい、愛しています。","chinese":"是的，我爱他们。"}]}],
  "synonyms":[{"heading":"恋","reading":"こい","chinese":"恋爱","shared":"大切に思う",
               "difference":"「恋」は恋愛感情。","scene":"恋愛の場面"}],
  "cautions":[{"kind":"MEANING","title":"「恋」との違い","wrong":"文化を恋する。",
               "correct":"文化を愛する。","reason":"「恋」は恋愛に限る。"}],
  "conjugations":[{"form":"て形","value":"愛して","example":"家族を愛している。"}],
  "transitivityPair":{"intransitive":"愛される","transitive":"愛する","particleNote":"～を／～に",
                       "intransitiveExample":"彼は愛されている。","transitiveExample":"彼は家族を愛している。"},
  "pronunciation":{"reading":"あい","accentType":1,"accentNotation":"あꜜい",
                   "hint":"「あ」を高く。","hasAudioSample":false},
  "collocations":[{"expression":"愛を込める","reading":"あいをこめる","chinese":"倾注爱意",
                   "usage":"手紙で","exampleJapanese":"心を込めて愛を伝える。","exampleChinese":"用心传达爱意。"},
                  {"expression":"愛が深い","reading":"あいがふかい","chinese":"爱意深厚",
                   "usage":"家族の話で","exampleJapanese":"母の愛が深い。","exampleChinese":"母爱深厚。"}],
  "relatedWords":[{"relation":"類義語","heading":"恋","reading":"こい","chinese":"恋爱"}],
  "usageNotes":[{"register":"どちらも","politeness":"普通","audience":"家族に",
                 "note":"書き言葉でも使う。","senseNumber":1}],
  "memoryHint":{"hint":"「心」が真ん中にある字。","basis":"漢字の形"},
  "practices":[{"kind":"PARTICLE","question":"音楽（ ）愛する。","questionChinese":"热爱音乐。",
                "choices":["に","を"],"answer":"を","explanation":"対象は「を」。","freeWriting":false}]
}`

/** 規範 JSON を学習画面の形へ写したもの（10 の段落すべてに中身がある）。 */
function canonicalMapped(): NonNullable<ReturnType<typeof toDetail>> {
  const detail = toDetail({
    detailId: 900,
    contentVersion: 3,
    version: 12,
    aiProvider: 'qwen',
    aiModel: 'qwen3.7-plus',
    fetchedAt: '2026-10-01T09:00:00',
    detail: JSON.parse(CANONICAL_BODY_JSON) as JpnWordDetailBody
  })
  if (detail === null) {
    throw new Error('規範 JSON から詳細を作れませんでした。')
  }
  return detail
}

describe('詳細の規範 JSON → 10 の段落すべてを写す（1 段落ずつ）', () => {
  it('語義（senses）を写す', () => {
    expect(canonicalMapped().senses).toEqual([
      {
        id: 'sense-1',
        number: 1,
        japanese: '大切に思う気持ち。',
        chinese: '爱；喜爱',
        context: '家族・友人',
        style: 'NEUTRAL'
      }
    ])
  })

  it('例文（examples）を写す（レベルと語義番号も）', () => {
    const examples = canonicalMapped().examples
    expect(examples.map((row) => row.japanese)).toEqual(['親の愛は無条件だ。', '彼は植物を愛している。'])
    expect(examples.map((row) => row.reading)).toEqual(['おやのあいはむじょうけんだ。', 'かれはしょくぶつをあいしている。'])
    expect(examples.map((row) => row.chinese)).toEqual(['父母的爱是无条件的。', '他热爱植物。'])
    expect(examples.map((row) => row.level)).toEqual(['BASIC', 'APPLIED'])
    expect(examples.map((row) => row.senseNumber)).toEqual([1, 1])
    expect(examples.map((row) => row.source)).toEqual(['家族', ''])
  })

  it('文型（patterns）を写す', () => {
    expect(canonicalMapped().patterns).toEqual([
      {
        id: 'pattern-0',
        pattern: '**を**愛する',
        reading: 'をあいする',
        chinese: '爱…',
        example: '音楽を愛する。',
        exampleChinese: '热爱音乐。'
      }
    ])
  })

  it('会話（dialogs）を 2 層で写す', () => {
    expect(canonicalMapped().dialogs).toEqual([
      {
        id: 'dialog-0',
        scene: '職員室で先生に相談する',
        lines: [
          { id: 'dialog-0-line-0', speaker: '先生', japanese: '家族を愛していますか。', chinese: '你爱家人吗。' },
          { id: 'dialog-0-line-1', speaker: '学生', japanese: 'はい、愛しています。', chinese: '是的，我爱他们。' }
        ]
      }
    ])
  })

  it('類義語（synonyms）を写す', () => {
    expect(canonicalMapped().synonyms[0]).toMatchObject({
      id: 'synonym-0',
      heading: '恋',
      reading: 'こい',
      chinese: '恋爱',
      shared: '大切に思う',
      difference: '「恋」は恋愛感情。',
      usage: '恋愛の場面'
    })
  })

  it('注意（cautions）を写す（区分・誤り・正しい・理由）', () => {
    expect(canonicalMapped().cautions).toEqual([
      {
        id: 'caution-0',
        kind: 'MEANING',
        title: '「恋」との違い',
        wrong: '文化を恋する。',
        correct: '文化を愛する。',
        reason: '「恋」は恋愛に限る。'
      }
    ])
  })

  it('コロケーション（collocations）を写す', () => {
    const collocations = canonicalMapped().collocations
    expect(collocations.map((row) => row.expression)).toEqual(['愛を込める', '愛が深い'])
    expect(collocations[0]).toEqual({
      id: 'collocation-0',
      expression: '愛を込める',
      reading: 'あいをこめる',
      chinese: '倾注爱意',
      usage: '手紙で'
    })
  })

  it('関連語（relatedWords）を写す（関係はそのまま）', () => {
    expect(canonicalMapped().relatedWords).toEqual([
      { id: 'related-0', relation: '類義語', heading: '恋', reading: 'こい', chinese: '恋爱' }
    ])
  })

  it('使用場面（usageNotes）を写す（語義から合成しない）', () => {
    expect(canonicalMapped().usageNotes).toEqual([
      {
        id: 'usage-0',
        register: 'どちらも',
        politeness: '普通',
        audience: '家族に',
        note: '書き言葉でも使う。',
        senseNumber: 1
      }
    ])
  })

  it('練習（practices）を写す（選択肢は文字列の配列のまま）', () => {
    expect(canonicalMapped().practices).toEqual([
      {
        id: 'practice-0',
        kind: 'PARTICLE',
        question: '音楽（ ）愛する。',
        questionChinese: '热爱音乐。',
        choices: ['に', 'を'],
        answer: 'を',
        explanation: '対象は「を」。',
        freeWriting: false,
        demoFeedback: null
      }
    ])
  })
})
