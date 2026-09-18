import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import JapaneseWordView from '@/views/japanese/JapaneseWordView.vue'
import type { JpnWord, JpnWordDetail, JpnWordDetailResult, JpnWordTotals } from '@/api/japanese'

/**
 * 日本語勉強【単語情報管理】（2.0 の japanese_word.jsp 相当）。
 * API はモックし、一覧・サマリの表示と、各ボタンが送るリクエストを固定する。
 * 実データ（移行済みの日本語単語 9,847 語）が入っている前提の画面。
 */
function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function failure(message: string, status = 500): Response {
  return new Response(
    JSON.stringify({ success: false, code: 'INTERNAL_ERROR', message, data: null, timestamp: '' }),
    { status, headers: { 'Content-Type': 'application/json' } }
  )
}

function word(overrides: Partial<JpnWord> = {}): JpnWord {
  return {
    wordId: 101,
    word: '勉強',
    reading: 'べんきょう',
    jlptLevel: 'N4',
    partOfSpeech: '名詞',
    stateCode: 'ACTIVE',
    note: null,
    version: 2,
    book: '日本語単語帳①',
    category: 'Unit001',
    level: 'N4',
    wordSeq: 12,
    collectionCount: 1,
    learnState: 'LEARNING',
    mastery: 40,
    answeredCount: 12,
    correctCount: 9,
    favorite: false,
    learned: false,
    lastStudiedAt: '2026-09-12T23:02:00',
    nextReviewAt: '2026-09-20T00:00:00',
    ...overrides
  }
}

/** 収録がなく、まだ一度も学習していない単語。 */
function secondWord(): JpnWord {
  return word({
    wordId: 102,
    word: '図書館',
    reading: 'としょかん',
    jlptLevel: 'N5',
    partOfSpeech: '名詞',
    book: null,
    category: null,
    level: null,
    wordSeq: null,
    collectionCount: 0,
    learnState: 'NOT_STARTED',
    mastery: 0,
    answeredCount: 0,
    correctCount: 0,
    favorite: true,
    learned: true,
    lastStudiedAt: null,
    nextReviewAt: null,
    version: 1
  })
}

const TOTALS: JpnWordTotals = {
  wordCount: 9847,
  learnedCount: 3120,
  favoriteCount: 148,
  averageMastery: 62,
  answeredCount: 45210
}

/**
 * 詳細ダイアログが受け取るデータ。API が返す項目を**すべて**埋めておく
 * （足りない項目は画面に出せないので、テストデータの不足＝表示漏れに気づけるようにする）。
 */
function detailResult(overrides: Partial<JpnWordDetailResult> = {}): JpnWordDetailResult {
  const answers: Partial<JpnWordDetailResult> = {
    word: word(),
    collections: [
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
    ],
    questions: [
      {
        questionId: 5,
        questionType: 'C1_READING',
        questionNo: 1,
        questionText: '「勉強」の読みとして正しいものはどれですか。',
        correctValue: 'べんきょう',
        choiceCount: 4
      },
      {
        questionId: 6,
        questionType: 'C2_KANJI',
        questionNo: 1,
        questionText: '音声を聞いて正しい漢字表記を選んでください。',
        correctValue: '勉強',
        choiceCount: 4
      },
      {
        questionId: 7,
        questionType: 'D_CONTEXT_MEANING',
        questionNo: 1,
        questionText: '下線部の「勉強」の意味として最も適切なものを選びなさい。',
        correctValue: '学习',
        choiceCount: 4
      },
      {
        questionId: 8,
        questionType: 'E_KANJI_USAGE',
        questionNo: 1,
        questionText: '次の文の（ ）に入る最も適切な漢字表記を選んでください。',
        correctValue: '勉強',
        choiceCount: 4
      }
    ],
    detail: {
      detailId: 9,
      contentVersion: 1,
      aiProvider: 'qwen',
      aiModel: 'qwen-max',
      fetchedAt: '2026-09-01T10:00:00',
      senseCount: 2,
      exampleCount: 2,
      pronunciationCount: 1,
      collocationCount: 1,
      relatedWordCount: 1,
      cautionCount: 1,
      detail: {
        jlptLevel: 'N4',
        partOfSpeech: '名詞',
        conjugation: 'サ変',
        transitivity: 'TRANSITIVE',
        importance: 4,
        chineseMeaning: '学习',
        descriptionJa: '学問や技芸を学ぶこと。',
        descriptionZh: '学习学问或技艺。',
        structuredSchemaVersion: 'jp-schema-v1',
        manuallyCorrected: false,
        senses: [
          {
            number: 1,
            japanese: '学問や技芸を学ぶこと。',
            chinese: '学习',
            context: '学校・家庭',
            style: '普通',
            noteJapanese: '名詞としても動詞としても使う。',
            noteChinese: '可作名词也可作动词'
          },
          {
            number: 2,
            japanese: '経験して身につけること。',
            chinese: '经验、体会',
            context: '仕事',
            style: 'やや硬い',
            noteJapanese: null,
            noteChinese: null
          }
        ],
        examples: [
          {
            japanese: '毎日、日本語を勉強します。',
            reading: 'まいにち、にほんごをべんきょうします。',
            chinese: '每天学习日语。',
            contextJapanese: '日常',
            contextChinese: '日常',
            source: '日本語単語帳①',
            senseNumber: 1
          },
          {
            japanese: 'いい勉強になりました。',
            reading: 'いいべんきょうになりました。',
            chinese: '很有收获。',
            contextJapanese: null,
            contextChinese: null,
            source: null,
            senseNumber: 2
          }
        ],
        pronunciations: [
          {
            reading: 'べんきょう',
            accentNotation: '0',
            accentType: 0,
            moraCount: 4,
            audioUrl: 'https://example.test/benkyou.mp3',
            audioProvider: 'Google'
          }
        ],
        collocations: [
          {
            expression: '勉強になる',
            reading: 'べんきょうになる',
            chinese: '有收获',
            exampleJapanese: 'とても勉強になりました。',
            exampleChinese: '很有收获。'
          }
        ],
        relatedWords: [
          {
            relatedWordId: null,
            relationType: 'SYNONYM',
            heading: '学習',
            reading: 'がくしゅう',
            chinese: '学习',
            differenceJapanese: '「勉強」は日常的、「学習」はやや硬い言い方。',
            differenceChinese: '「勉強」较口语，「学習」偏书面。',
            eCandidate: true
          }
        ],
        cautions: [
          {
            noteType: 'USAGE',
            japanese: '中国語の「勉强」とは意味が異なる。',
            chinese: '与中文「勉强」含义不同',
            wrongExample: '無理に勉強する。',
            correctExample: '日本語を勉強する。'
          }
        ]
      }
    }
  }
  return { ...answers, ...overrides } as JpnWordDetailResult
}

type Call = { url: string; method: string; body: Record<string, unknown> | null }

function recorded(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return fetchMock.mock.calls.map((call) => {
    const init = (call[1] ?? {}) as RequestInit
    return {
      url: String(call[0]),
      method: (init.method ?? 'GET').toUpperCase(),
      body: init.body ? (JSON.parse(String(init.body)) as Record<string, unknown>) : null
    }
  })
}

function toastMessages(): string[] {
  return useToast().items.map((item) => item.message)
}

/** 更新系の既定の応答（画面は data.message をトーストに出す）。 */
function mutationResponse(url: string, method: string): Response {
  if (method === 'POST' && url === '/api/user/japanese/words') {
    return ok({ word: word({ wordId: 500 }), message: '単語を登録しました。' })
  }
  if (method === 'PUT') {
    return ok({ word: word(), message: '単語を更新しました。' })
  }
  if (method === 'PATCH' && url.endsWith('/favorite')) {
    return ok({ word: word({ favorite: true }), message: 'お気に入りに登録しました。' })
  }
  if (method === 'PATCH' && url.endsWith('/learned')) {
    return ok({ word: word({ learned: true }), message: '習得済にしました。' })
  }
  if (method === 'DELETE') {
    return ok({ count: 1, message: '単語を削除しました。' })
  }
  return ok({ count: 1, message: 'OK' })
}

describe('日本語勉強【単語情報管理】', () => {
  async function setup(options: {
    items?: JpnWord[]
    totalElements?: number
    detail?: JpnWordDetailResult
    handlers?: (url: string, method: string, body: Record<string, unknown> | null) => Response | null
  } = {}) {
    const pinia = createPinia()
    setActivePinia(pinia)
    const items = options.items ?? [word(), secondWord()]
    const detail = options.detail ?? detailResult()

    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const target = String(url)
      const method = (init?.method ?? 'GET').toUpperCase()
      const body = init?.body ? (JSON.parse(String(init.body)) as Record<string, unknown>) : null
      const handled = options.handlers?.(target, method, body)
      if (handled) return handled
      if (method === 'GET' && target.startsWith('/api/user/japanese/words?')) {
        const params = new URL(target, 'http://localhost').searchParams
        const size = Number(params.get('size') ?? 20)
        const total = options.totalElements ?? items.length
        return ok({
          items,
          totalElements: total,
          page: Number(params.get('page') ?? 1),
          size,
          totalPages: Math.max(1, Math.ceil(total / size)),
          totals: TOTALS
        })
      }
      if (method === 'GET' && /^\/api\/user\/japanese\/words\/\d+$/.test(target)) {
        return ok(detail)
      }
      return mutationResponse(target, method)
    })

    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(JapaneseWordView, { global: { plugins: [pinia] } })
    await flushPromises()
    return { wrapper, fetchMock }
  }

  beforeEach(() => {
    useToast().items.splice(0)
    vi.restoreAllMocks()
  })

  /* ---------- 詳細ダイアログの小道具 ---------- */

  type DialogWrapper = ReturnType<typeof mount>

  /** wrapper.get() が返す要素（DOMWrapper）。項目の値を引くときに使う。 */
  type ElementWrapper = ReturnType<DialogWrapper['get']>

  /** 一覧の「詳細」からダイアログを開き、指定のタブを選ぶ（既定は先頭の「収録」）。 */
  async function openDetailTab(
    wrapper: DialogWrapper, tab = 'collections'
  ): Promise<void> {
    await wrapper.get('[data-jp-word-row="101"] [data-jp-detail]').trigger('click')
    await flushPromises()
    await selectDetailTab(wrapper, tab)
  }

  async function selectDetailTab(wrapper: DialogWrapper, tab: string): Promise<void> {
    await wrapper.get(`[data-jp-detail-tab="${tab}"]`).trigger('click')
  }

  /**
   * 項目（ラベルと値）の値を取る。
   * 画面は 2.0 の列名をラベルにした項目を並べるので、テストはラベルで引く。
   */
  function fieldText(scope: ElementWrapper, label: string): string {
    return scope.get(`[data-jp-detail-field="${label}"] dd`).text()
  }

  /** そのタブが空のときの案内（件数 0 件つき）を確かめる（詳細はあるが配列が空）。 */
  async function assertEmptyPanel(tab: string, label: string): Promise<void> {
    const { wrapper } = await setup({
      detail: detailResult({
        collections: [],
        questions: [],
        detail: {
          detailId: 9,
          contentVersion: 1,
          aiProvider: 'qwen',
          aiModel: 'qwen-max',
          fetchedAt: '2026-09-01T10:00:00',
          detail: {} as JpnWordDetail['detail']
        }
      })
    })
    await openDetailTab(wrapper, tab)

    const panel = wrapper.get(`[data-jp-detail-panel="${tab}"]`)
    expect(panel.text(), label).toContain('まだ登録されていません。')
    expect(panel.get('.jp-section__count').text(), label).toContain('0 件')
  }

  it('単語の一覧を新しい列順で日本語に描画する', async () => {
    const { wrapper } = await setup()

    expect(wrapper.find('[data-jp-words]').exists()).toBe(true)
    // 列は左から 操作 / JLPTレベル / 単語ID / 書籍 / 分類 / 単語 / 読み方 / 品詞 / 中国語訳 / 取得状態
    expect(wrapper.findAll('[data-jp-words] thead th').map((cell) => cell.text())).toEqual([
      '操作', 'JLPTレベル', '単語ID', '書籍', '分類', '単語', '読み方', '品詞', '中国語訳',
      '取得状態（A・B，C，D，E）'
    ])

    const row = wrapper.get('[data-jp-word-row="101"]')
    const cells = row.findAll('td').map((cell) => cell.text())
    // 操作はアイコンだけなので文字を持たない（左端の列）
    expect(cells[0]).toBe('')
    expect(cells[1]).toBe('N4')
    expect(cells[2]).toBe('101')
    expect(cells[3]).toBe('日本語単語帳①')
    expect(cells[4]).toBe('Unit001')
    expect(row.get('.jp-word').text()).toBe('勉強')
    expect(cells[6]).toBe('べんきょう')
    expect(cells[7]).toBe('名詞')
    // 中国語訳と取得状態は一覧 API が返さないため、プレースホルダを出す
    expect(row.get('[data-jp-chinese]').text()).toBe('—')
    expect(row.get('[data-jp-acquire]').text()).toBe('A・B未取得 C未取得 D未取得 E未取得')

    // 収録が無い単語は書籍・分類とも「—」
    const second = wrapper.get('[data-jp-word-row="102"]')
    const secondCells = second.findAll('td').map((cell) => cell.text())
    expect(secondCells[3]).toBe('—')
    expect(secondCells[4]).toBe('—')
    expect(second.get('.jp-word').text()).toBe('図書館')
    expect(second.get('[data-jp-chinese]').text()).toBe('—')

    // 検索条件と一覧の間にあった統計情報（サマリ）のブロックは置かない
    expect(wrapper.find('[data-jp-summary]').exists()).toBe(false)

    // 件数は一覧のヘッダーとページングに出す
    expect(wrapper.get('[data-jp-count]').text()).toContain('全 2 件')
    expect(wrapper.get('.pagination__info').text()).toContain('全 2 件（1 / 1 ページ）')
  })


  it('初期表示は 1 ページ目・20 件で検索する', async () => {
    const { fetchMock } = await setup()

    const call = recorded(fetchMock).at(-1)
    expect(call?.method).toBe('GET')
    expect(call?.url).toBe('/api/user/japanese/words?page=1&size=20')
  })

  it('検索条件をクエリで送り、リセットで消す', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-filter="keyword"]').setValue('勉強')
    await wrapper.get('[data-jp-filter="jlpt"]').setValue('N4')
    await wrapper.get('[data-jp-filter="part"]').setValue('名詞')
    await wrapper.get('[data-jp-filter="book"]').setValue('日本語単語帳①')
    await wrapper.get('[data-jp-filter="categoryFrom"]').setValue('Unit001')
    await wrapper.get('[data-jp-filter="categoryTo"]').setValue('Unit001')
    await wrapper.get('[data-jp-search]').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).at(-1)
    expect(call?.method).toBe('GET')
    expect(call?.url).toContain('/api/user/japanese/words?')
    expect(call?.url).toContain(`keyword=${encodeURIComponent('勉強')}`)
    expect(call?.url).toContain('jlpt=N4')
    expect(call?.url).toContain(`part=${encodeURIComponent('名詞')}`)
    expect(call?.url).toContain(`book=${encodeURIComponent('日本語単語帳①')}`)
    // API が受け取る分類は 1 つだけなので、送るのは From（To は送らない）
    expect(call?.url).toContain('category=Unit001')
    expect(call?.url).not.toContain('categoryTo')
    expect(call?.url.match(/category=/g)?.length).toBe(1)
    expect(call?.url).toContain('page=1')
    // 読み・学習状態の絞り込みは画面から外した（コントロールも送信も無い）
    expect(wrapper.find('[data-jp-filter="reading"]').exists()).toBe(false)
    expect(wrapper.find('[data-jp-filter="learnState"]').exists()).toBe(false)
    expect(call?.url).not.toContain('reading=')
    expect(call?.url).not.toContain('learnState=')
    // 絞り込み中はそのことが分かる
    expect(wrapper.find('[data-jp-filtered-count]').exists()).toBe(true)

    await wrapper.get('[data-jp-reset]').trigger('click')
    await flushPromises()

    const reset = recorded(fetchMock).at(-1)
    expect(reset?.url).toBe('/api/user/japanese/words?page=1&size=20')
    expect((wrapper.get('[data-jp-filter="keyword"]').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('[data-jp-filter="book"]').element as HTMLSelectElement).value).toBe('')
    expect((wrapper.get('[data-jp-filter="categoryFrom"]').element as HTMLSelectElement).value).toBe('')
    expect((wrapper.get('[data-jp-filter="categoryTo"]').element as HTMLSelectElement).value).toBe('')
    expect(wrapper.find('[data-jp-filtered-count]').exists()).toBe(false)
  })

  it('書籍と分類の選択肢は、読み込んだ一覧の値から作る（（すべて）つき）', async () => {
    const { wrapper } = await setup()

    // 選択肢一覧を返す API が無いので、読み込んだ単語の値だけが並ぶ
    expect(wrapper.get('[data-jp-filter="book"]').findAll('option').map((option) => option.text()))
      .toEqual(['（すべて）', '日本語単語帳①'])
    expect(wrapper.get('[data-jp-filter="categoryFrom"]').findAll('option').map((option) => option.text()))
      .toEqual(['（すべて）', 'Unit001'])
    // 分類の From と To は同じ選択肢を使う
    expect(wrapper.get('[data-jp-filter="categoryTo"]').findAll('option').map((option) => option.text()))
      .toEqual(['（すべて）', 'Unit001'])
    // 分類（To）が効かない理由は、選択欄の title と画面の注記に出す
    expect(wrapper.get('[data-jp-filter="categoryTo"]').attributes('title')).toContain('未対応')
    expect(wrapper.get('[data-jp-filter-note]').text()).toContain('分類（From）')
  })

  it('右上の A〜E 取得ボタンは、取得用の API が無いため押せない', async () => {
    const { wrapper, fetchMock } = await setup()
    const before = recorded(fetchMock).length

    const group = wrapper.get('[data-jp-acquire-actions]')
    const buttons = group.findAll('button')
    expect(buttons.map((button) => button.text())).toEqual([
      '詳細情報取得（A・B）', '読み問題取得（C）', '文脈問題取得（D）', '漢字問題取得（E）'
    ])
    for (const button of buttons) {
      // 押せないこと・押せない理由を出す（取得用の API はまだ無い）
      expect(button.attributes('disabled')).toBeDefined()
      expect(button.attributes('title')).toBe('取得用の API はまだありません。')
      await button.trigger('click')
    }
    await flushPromises()
    // 押しても API を呼ばない（呼び先が無いので何もしない）
    expect(recorded(fetchMock).length).toBe(before)
  })

  it('【新規】は【検索】の右隣にあり、登録ダイアログを開く', async () => {
    const { wrapper } = await setup()

    const head = wrapper.get('.search-panel__head')
    expect(head.findAll('.search-panel__actions')[0].findAll('button').map((button) => button.text().trim()))
      .toEqual(['検索', '新規', 'リセット'])
    expect(head.find('[data-jp-add]').exists()).toBe(true)

    await wrapper.get('[data-jp-add]').trigger('click')
    expect(wrapper.find('[data-jp-word-dialog]').exists()).toBe(true)
  })

  it('操作列は左端のアイコンボタンで、状態は色で示す', async () => {
    const { wrapper } = await setup()

    const row = wrapper.get('[data-jp-word-row="101"]')
    // 先頭のセル（左端）が操作列
    const actionCell = row.get('td')
    expect(actionCell.classes()).toContain('row-actions')
    const buttons = actionCell.findAll('button')
    expect(buttons.map((button) => button.attributes('title')))
      .toEqual(['詳細', '修正', 'お気に入りに登録', '習得済にする', '削除'])
    for (const button of buttons) {
      // アイコンだけ（文字を持たない）が、意味は aria-label で伝える
      expect(button.text()).toBe('')
      expect(button.find('.icon').exists()).toBe(true)
      expect(button.attributes('aria-label')).toBeTruthy()
    }

    // お気に入り中・習得済みは色（is-favorite / is-learned）で示す
    const favoriteRow = wrapper.get('[data-jp-word-row="102"]')
    expect(favoriteRow.get('[data-jp-favorite]').classes()).toContain('is-favorite')
    expect(favoriteRow.get('[data-jp-favorite]').attributes('title')).toBe('お気に入りを解除')
    expect(favoriteRow.get('[data-jp-learned]').classes()).toContain('is-learned')
    expect(favoriteRow.get('[data-jp-learned]').attributes('title')).toBe('習得済を解除')
  })

  it('該当がなければその案内を出す', async () => {
    const { wrapper } = await setup({ items: [], totalElements: 0 })

    expect(wrapper.get('[data-jp-empty]').text()).toContain('該当する単語がありません。')
    expect(wrapper.find('[data-jp-word-row="101"]').exists()).toBe(false)
  })

  it('読み込みに失敗したらエラーを alert に出す', async () => {
    const { wrapper } = await setup({
      handlers: (url, method) =>
        method === 'GET' && url.startsWith('/api/user/japanese/words?')
          ? failure('サーバーでエラーが発生しました。')
          : null
    })

    expect(wrapper.get('.alert.alert--danger').text()).toContain('サーバーでエラーが発生しました。')
  })

  it('単語の追加ダイアログを開き、必須が空なら送信しない', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-add]').trigger('click')
    expect(wrapper.find('[data-jp-word-dialog]').exists()).toBe(true)
    expect(wrapper.find('#jpWord').exists()).toBe(true)
    expect(wrapper.find('#jpReading').exists()).toBe(true)
    expect(wrapper.find('#jpJlpt').exists()).toBe(true)
    expect(wrapper.find('#jpPart').exists()).toBe(true)
    expect(wrapper.find('#jpState').exists()).toBe(true)
    expect(wrapper.find('#jpNote').exists()).toBe(true)

    await wrapper.get('[data-jp-word-save]').trigger('click')
    await flushPromises()

    expect(wrapper.get('#jpWord').classes()).toContain('is-invalid')
    expect(wrapper.get('.field__error').text()).toContain('見出し語を入力してください。')
    expect(recorded(fetchMock).some((entry) => entry.method === 'POST')).toBe(false)
    expect(toastMessages()).toContain('入力内容を確認してください。')
  })

  it('登録すると POST の body に入力内容が載る（version は送らない）', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-add]').trigger('click')
    await wrapper.get('#jpWord').setValue('図書館')
    await wrapper.get('#jpReading').setValue('としょかん')
    await wrapper.get('#jpJlpt').setValue('N3')
    await wrapper.get('#jpPart').setValue('名詞')
    await wrapper.get('#jpState').setValue('INACTIVE')
    await wrapper.get('#jpNote').setValue('要確認')
    await wrapper.get('[data-jp-word-save]').trigger('click')
    await flushPromises()

    const post = recorded(fetchMock).find(
      (entry) => entry.method === 'POST' && entry.url === '/api/user/japanese/words'
    )
    expect(post?.body).toMatchObject({
      word: '図書館',
      reading: 'としょかん',
      jlptLevel: 'N3',
      partOfSpeech: '名詞',
      stateCode: 'INACTIVE',
      note: '要確認'
    })
    // 新規登録は version を送らない
    expect(post?.body?.version).toBeUndefined()
    expect(toastMessages()).toContain('単語を登録しました。')
    expect(wrapper.find('[data-jp-word-dialog]').exists()).toBe(false)
    // 保存後は一覧を取り直す
    expect(recorded(fetchMock).filter(
      (entry) => entry.method === 'GET' && entry.url.startsWith('/api/user/japanese/words?')
    ).length).toBeGreaterThan(1)
  })

  it('修正すると表示していた version を PUT で送る', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-word-row="101"] [data-jp-edit]').trigger('click')
    expect((wrapper.get('#jpWord').element as HTMLInputElement).value).toBe('勉強')
    expect((wrapper.get('#jpReading').element as HTMLInputElement).value).toBe('べんきょう')
    expect((wrapper.get('#jpJlpt').element as HTMLSelectElement).value).toBe('N4')
    expect((wrapper.get('#jpState').element as HTMLSelectElement).value).toBe('ACTIVE')

    await wrapper.get('#jpWord').setValue('勉強（改）')
    await wrapper.get('[data-jp-word-save]').trigger('click')
    await flushPromises()

    const put = recorded(fetchMock).find((entry) => entry.method === 'PUT')
    expect(put?.url).toBe('/api/user/japanese/words/101')
    expect(put?.body).toMatchObject({ word: '勉強（改）', version: 2 })
    expect(toastMessages()).toContain('単語を更新しました。')
    expect(wrapper.find('[data-jp-word-dialog]').exists()).toBe(false)
  })

  it('追加ダイアログのキャンセルは送信しない', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-add]').trigger('click')
    await wrapper.get('#jpWord').setValue('書きかけ')
    await wrapper.get('[data-jp-word-cancel]').trigger('click')

    expect(wrapper.find('[data-jp-word-dialog]').exists()).toBe(false)
    expect(recorded(fetchMock).some((entry) => entry.method === 'POST' || entry.method === 'PUT')).toBe(false)
  })

  it('削除は確認してから DELETE を呼び、キャンセルなら呼ばない', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-word-row="101"] [data-jp-delete]').trigger('click')
    await flushPromises()
    expect(recorded(fetchMock).some((entry) => entry.method === 'DELETE')).toBe(false)

    confirmSpy.mockReturnValue(true)
    await wrapper.get('[data-jp-word-row="101"] [data-jp-delete]').trigger('click')
    await flushPromises()

    expect(recorded(fetchMock).some(
      (entry) => entry.method === 'DELETE' && entry.url === '/api/user/japanese/words/101'
    )).toBe(true)
    expect(toastMessages()).toContain('単語を削除しました。')
    confirmSpy.mockRestore()
  })

  it('お気に入りは PATCH で切り替える', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-word-row="101"] [data-jp-favorite]').trigger('click')
    await flushPromises()

    const patch = recorded(fetchMock).find(
      (entry) => entry.method === 'PATCH' && entry.url.endsWith('/favorite')
    )
    expect(patch?.url).toBe('/api/user/japanese/words/101/favorite')
    expect(patch?.body).toEqual({ favorite: true })
    expect(toastMessages()).toContain('お気に入りに登録しました。')
  })

  it('習得済は PATCH で切り替える', async () => {
    const { wrapper, fetchMock } = await setup()

    // 未習得の単語は「習得済にする」
    expect(wrapper.get('[data-jp-word-row="101"] [data-jp-learned]').attributes('title')).toBe('習得済にする')
    // 習得済みの単語は「習得済を解除」
    expect(wrapper.get('[data-jp-word-row="102"] [data-jp-learned]').attributes('title')).toBe('習得済を解除')

    await wrapper.get('[data-jp-word-row="101"] [data-jp-learned]').trigger('click')
    await flushPromises()

    const patch = recorded(fetchMock).find(
      (entry) => entry.method === 'PATCH' && entry.url.endsWith('/learned')
    )
    expect(patch?.url).toBe('/api/user/japanese/words/101/learned')
    expect(patch?.body).toEqual({ learned: true })
    expect(toastMessages()).toContain('習得済にしました。')
  })

  it('詳細ダイアログに収録・語義・例文・問題を出す', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-word-row="101"] [data-jp-detail]').trigger('click')
    await flushPromises()

    expect(recorded(fetchMock).some(
      (entry) => entry.method === 'GET' && entry.url === '/api/user/japanese/words/101'
    )).toBe(true)

    const dialog = wrapper.get('[data-jp-detail-dialog]')
    expect(dialog.get('.jp-detail__word').text()).toBe('勉強')
    expect(dialog.get('.jp-detail__reading').text()).toBe('べんきょう')
    expect(dialog.text()).toContain('N4')
    expect(dialog.text()).toContain('名詞')
    expect(dialog.text()).toContain('学習中')

    const collections = dialog.get('[data-jp-detail-section="collections"]')
    expect(collections.text()).toContain('日本語単語帳①')
    expect(collections.text()).toContain('Unit001')
    expect(collections.text()).toContain('掲載見出し語')
    expect(collections.text()).toContain('学习')

    const senses = dialog.get('[data-jp-detail-section="senses"]')
    expect(senses.text()).toContain('学問や技芸を学ぶこと。')
    expect(senses.text()).toContain('使用場面')
    expect(senses.text()).toContain('学校・家庭')

    expect(dialog.get('[data-jp-detail-section="examples"]').text()).toContain('毎日、日本語を勉強します。')
    // 発音は 2.0 の列（読み・アクセント表記・型・モーラ数）をそのまま出す
    expect(fieldText(dialog.get('[data-jp-detail-section="pronunciations"]'), 'モーラ数')).toBe('4')
    expect(dialog.get('[data-jp-detail-section="collocations"]').text()).toContain('勉強になる')
    expect(dialog.get('[data-jp-detail-section="relatedWords"]').text()).toContain('学習')
    // 関係種別は日本語ラベルにする
    expect(dialog.get('[data-jp-detail-section="relatedWords"]').text()).toContain('類義語')
    expect(dialog.get('[data-jp-detail-section="cautions"]').text()).toContain('意味が異なる')
    expect(dialog.get('[data-jp-detail-section="cautions"]').text()).toContain('無理に勉強する。')

    const questions = dialog.get('[data-jp-detail-section="questions"]')
    expect(questions.text()).toContain('C：漢字と読み')
    expect(questions.text()).toContain('「勉強」の読みとして正しいものはどれですか。')
    expect(questions.text()).toContain('べんきょう')
    expect(fieldText(questions, '選択肢')).toBe('4 件')

    await wrapper.get('[data-jp-detail-close]').trigger('click')
    expect(wrapper.find('[data-jp-detail-dialog]').exists()).toBe(false)
  })

  it('詳細ダイアログの各タブは件数つきで並び、開いた直後は収録を選ぶ', async () => {
    const { wrapper } = await setup()
    await openDetailTab(wrapper)

    const dialog = wrapper.get('[data-jp-detail-dialog]')
    expect(dialog.get('[data-jp-detail-tabs]').findAll('[role="tab"]').map((tab) => tab.text())).toEqual([
      '収録 1', '基本情報', '語義 2', '例文 2', '発音 1', 'コロケーション 1', '関連語 1', '使用注意 1', '問題 4'
    ])

    // 開いた直後は先頭の「収録」が選ばれ、そのパネルだけが見えている
    expect(dialog.get('[data-jp-detail-tab="collections"]').attributes('aria-selected')).toBe('true')
    expect(dialog.findAll('[data-jp-detail-panel]').length).toBe(9)
    expect(dialog.get('[data-jp-detail-panel="collections"]').isVisible()).toBe(true)
    expect(dialog.get('[data-jp-detail-panel="senses"]').isVisible()).toBe(false)

    // 件数の無いタブ（基本情報）には数のバッジを付けない
    // 件数のバッジは「収録・語義・例文・発音・コロケーション・関連語・使用注意・問題」
    // （基本情報には付けない）。問題は C1 / C2 / D / E の 4 種別ぶん
    expect(dialog.findAll('[data-jp-detail-count]').map((badge) => badge.text())).toEqual([
      '1', '2', '2', '1', '1', '1', '1', '4'
    ])
  })

  it('タブと中身は aria で 1 対 1 に結び付いている', async () => {
    const { wrapper } = await setup()
    await openDetailTab(wrapper)

    const dialog = wrapper.get('[data-jp-detail-dialog]')
    const tabs = dialog.findAll('[data-jp-detail-tabs] [role="tab"]')
    expect(tabs.length).toBe(9)

    for (const tab of tabs) {
      const controls = tab.attributes('aria-controls')
      const id = tab.attributes('id')
      expect(controls, tab.text()).toBeTruthy()
      expect(id, tab.text()).toBeTruthy()
      // タブが指す中身が実在し、その中身がタブを指し返している（読み上げの対応付け）
      const panel = dialog.get(`#${controls}`)
      expect(panel.attributes('role'), controls).toBe('tabpanel')
      expect(panel.attributes('aria-labelledby'), controls).toBe(id)
      expect(panel.attributes('data-jp-detail-panel'), controls).toBeDefined()
    }
  })

  it('「収録」は 2.0 の収録情報の項目をすべて出し、無ければその案内を出す', async () => {
    const { wrapper } = await setup()
    await openDetailTab(wrapper, 'collections')

    const dialog = wrapper.get('[data-jp-detail-dialog]')
    const collection = dialog.get('[data-jp-detail-panel="collections"] [data-jp-collection="1"]')
    expect(fieldText(collection, '書籍')).toBe('日本語単語帳①')
    expect(fieldText(collection, '分類')).toBe('Unit001')
    expect(fieldText(collection, 'レベル')).toBe('N4')
    expect(fieldText(collection, '単語SEQ')).toBe('12')
    expect(fieldText(collection, '掲載見出し語')).toBe('勉強')
    expect(fieldText(collection, '掲載読み')).toBe('べんきょう')
    expect(fieldText(collection, '掲載品詞')).toBe('名詞')
    expect(fieldText(collection, '掲載中国語意味')).toBe('学习')

    // 収録が無い語（AI 詳細だけ取った語）はその案内を出す
    const { wrapper: empty } = await setup({
      detail: detailResult({ collections: [], detail: null })
    })
    await openDetailTab(empty, 'collections')
    expect(empty.get('[data-jp-detail-panel="collections"]').text()).toContain('まだ登録されていません。')
  })

  it('「基本情報」は単語の項目・学習状況・詳細の取得情報をすべて出す', async () => {
    const { wrapper } = await setup()
    await openDetailTab(wrapper, 'basic')

    const panel = wrapper.get('[data-jp-detail-panel="basic"]')
    for (const [label, expected] of [
      ['見出し語', '勉強'],
      ['読み', 'べんきょう'],
      ['JLPTレベル', 'N4'],
      ['品詞', '名詞'],
      ['状態', '有効'],
      ['備考', '—'],
      ['バージョン', '2'],
      ['学習状態', '学習中'],
      ['習得度', '40%'],
      ['回答数', '12'],
      ['正解数', '9'],
      ['お気に入り', 'いいえ'],
      ['習得済', 'いいえ'],
      ['詳細ID', '9'],
      ['内容版数', '1'],
      ['AIプロバイダ', 'qwen'],
      ['AIモデル', 'qwen-max'],
      ['取得日時', '2026/9/1 10:00:00'],
      ['構造化スキーマ版', 'jp-schema-v1'],
      ['手動修正済', 'いいえ']
    ] as const) {
      expect(fieldText(panel, label), label).toBe(expected)
    }
    // 日時は画面の他の箇所と同じ書式（formatIsoDateTime）で出す
    expect(fieldText(panel, '最終学習')).toBe('2026/9/12 23:02:00')

    // 詳細本体（AI 詳細のトップレベル）の項目も出す
    expect(fieldText(panel, '詳細のJLPTレベル')).toBe('N4')
    expect(fieldText(panel, '詳細の品詞')).toBe('名詞')
    expect(fieldText(panel, '活用種類')).toBe('サ変')
    expect(fieldText(panel, '自他区分')).toBe('TRANSITIVE')
    expect(fieldText(panel, '重要度')).toBe('4')
    expect(fieldText(panel, '代表中国語意味')).toBe('学习')
    expect(fieldText(panel, '日本語説明')).toBe('学問や技芸を学ぶこと。')
    expect(fieldText(panel, '中国語説明')).toBe('学习学问或技艺。')
  })

  it('「語義」は 2.0 の語義の項目を出し、無ければその案内を出す', async () => {
    const { wrapper } = await setup()
    await openDetailTab(wrapper, 'senses')

    const panel = wrapper.get('[data-jp-detail-panel="senses"]')
    const first = panel.findAll('.jp-sense')[0]
    expect(fieldText(first, '意味（日本語）')).toBe('学問や技芸を学ぶこと。')
    expect(fieldText(first, '意味（中国語）')).toBe('学习')
    expect(fieldText(first, '使用場面')).toBe('学校・家庭')
    expect(fieldText(first, '文体')).toBe('普通')
    expect(fieldText(first, '補足説明（日本語）')).toBe('名詞としても動詞としても使う。')
    expect(fieldText(first, '補足説明（中国語）')).toBe('可作名词也可作动词')
    // 見出しの番号は 2.0 の 語義番号
    expect(first.get('.jp-sense__number').text()).toBe('1')
    expect(panel.findAll('.jp-sense')[1].get('.jp-sense__number').text()).toBe('2')


    await assertEmptyPanel('senses', '語義')
  })

  it('「例文」は 2.0 の例文の項目を出し、無ければその案内を出す', async () => {
    const { wrapper } = await setup()
    await openDetailTab(wrapper, 'examples')

    const panel = wrapper.get('[data-jp-detail-panel="examples"]')
    const first = panel.findAll('.jp-sense')[0]
    expect(fieldText(first, '例文（日本語）')).toBe('毎日、日本語を勉強します。')
    expect(fieldText(first, '例文読み')).toBe('まいにち、にほんごをべんきょうします。')
    expect(fieldText(first, '例文（中国語）')).toBe('每天学习日语。')
    expect(fieldText(first, '文脈意味（日本語）')).toBe('日常')
    expect(fieldText(first, '文脈意味（中国語）')).toBe('日常')
    expect(fieldText(first, '出典')).toBe('日本語単語帳①')
    // どの語義の例文かが分かる（2.0 は語義ID、2.1 は語義番号）
    expect(fieldText(first, '語義番号')).toBe('1')


    await assertEmptyPanel('examples', '例文')
  })

  it('「発音」は 2.0 の発音の項目を出し、無ければその案内を出す', async () => {
    const { wrapper } = await setup()
    await openDetailTab(wrapper, 'pronunciations')

    const panel = wrapper.get('[data-jp-detail-panel="pronunciations"]')
    const first = panel.findAll('.jp-sense')[0]
    expect(fieldText(first, '読み')).toBe('べんきょう')
    expect(fieldText(first, 'アクセント表記')).toBe('0')
    expect(fieldText(first, 'アクセント型')).toBe('0')
    expect(fieldText(first, 'モーラ数')).toBe('4')
    expect(fieldText(first, '音声URL')).toBe('https://example.test/benkyou.mp3')
    expect(fieldText(first, '音声プロバイダ')).toBe('Google')


    await assertEmptyPanel('pronunciations', '発音')
  })

  it('「コロケーション」は 2.0 のコロケーションの項目を出し、無ければその案内を出す', async () => {
    const { wrapper } = await setup()
    await openDetailTab(wrapper, 'collocations')

    const first = wrapper.get('[data-jp-detail-panel="collocations"]').findAll('.jp-sense')[0]
    expect(fieldText(first, '表現')).toBe('勉強になる')
    expect(fieldText(first, '読み')).toBe('べんきょうになる')
    expect(fieldText(first, '中国語')).toBe('有收获')
    expect(fieldText(first, '例文（日本語）')).toBe('とても勉強になりました。')
    expect(fieldText(first, '例文（中国語）')).toBe('很有收获。')


    await assertEmptyPanel('collocations', 'コロケーション')
  })

  it('「関連語」は 2.0 の関連語の項目を出し、無ければその案内を出す', async () => {
    const { wrapper } = await setup()
    await openDetailTab(wrapper, 'relatedWords')

    const first = wrapper.get('[data-jp-detail-panel="relatedWords"]').findAll('.jp-sense')[0]
    expect(fieldText(first, '見出し')).toBe('学習')
    expect(fieldText(first, '読み')).toBe('がくしゅう')
    expect(fieldText(first, '中国語')).toBe('学习')
    expect(fieldText(first, '違い（日本語）')).toBe('「勉強」は日常的、「学習」はやや硬い言い方。')
    expect(fieldText(first, '違い（中国語）')).toBe('「勉強」较口语，「学習」偏书面。')
    // 関係種別は日本語ラベル、E 問題の候補かどうかも出す
    expect(first.get('.jp-sense__head').text()).toContain('類義語')
    expect(fieldText(first, 'E問題の候補')).toBe('はい')


    await assertEmptyPanel('relatedWords', '関連語')
  })

  it('「使用注意」は 2.0 の使用注意の項目を出し、無ければその案内を出す', async () => {
    const { wrapper } = await setup()
    await openDetailTab(wrapper, 'cautions')

    const first = wrapper.get('[data-jp-detail-panel="cautions"]').findAll('.jp-sense')[0]
    expect(fieldText(first, '注意の種類')).toBe('USAGE')
    expect(fieldText(first, '注意（日本語）')).toBe('中国語の「勉强」とは意味が異なる。')
    expect(fieldText(first, '注意（中国語）')).toBe('与中文「勉强」含义不同')
    expect(fieldText(first, '誤用例')).toBe('無理に勉強する。')
    expect(fieldText(first, '正用例')).toBe('日本語を勉強する。')


    await assertEmptyPanel('cautions', '使用注意')
  })

  it('「問題」は種別・問題番号・本文・正解・選択肢数を出し、無ければその案内を出す', async () => {
    const { wrapper } = await setup()
    await openDetailTab(wrapper, 'questions')

    const panel = wrapper.get('[data-jp-detail-panel="questions"]')
    // 種別コード（C1_READING / C2_KANJI / D_ / E_）は日本語で出す（日本語勉強の C〜E）
    const heads = panel.findAll('.jp-sense__head').map((head) => head.text())
    expect(heads).toEqual([
      '1C：漢字と読み「勉強」の読みとして正しいものはどれですか。',
      '1C：漢字の選択音声を聞いて正しい漢字表記を選んでください。',
      '1D：文脈の意味下線部の「勉強」の意味として最も適切なものを選びなさい。',
      '1E：漢字の使い方次の文の（ ）に入る最も適切な漢字表記を選んでください。'
    ])

    const question = panel.get('[data-jp-question="5"]')
    expect(fieldText(question, '問題番号')).toBe('1')
    expect(fieldText(question, '問題種別')).toBe('C：漢字と読み')
    expect(fieldText(question, '問題文（日本語）')).toBe('「勉強」の読みとして正しいものはどれですか。')
    expect(fieldText(question, '正解値')).toBe('べんきょう')
    expect(fieldText(question, '選択肢')).toBe('4 件')


    await assertEmptyPanel('questions', '問題')
  })

  it('詳細がまだ無い単語は「基本情報」でその案内を出し、他のタブは 0 件になる', async () => {
    const { wrapper } = await setup({
      detail: detailResult({
        collections: [],
        questions: [],
        detail: null
      })
    })
    await openDetailTab(wrapper, 'basic')

    const dialog = wrapper.get('[data-jp-detail-dialog]')
    // 詳細（AI 詳細）が無いので、詳細の項目は案内に置き換える
    expect(wrapper.get('[data-jp-detail-panel="basic"]').text())
      .toContain('この単語の詳細はまだ取得されていません。')
    expect(wrapper.findAll('[data-jp-detail-field="詳細ID"]').length).toBe(0)
    // 単語そのものの情報は詳細が無くても出す
    expect(fieldText(wrapper.get('[data-jp-detail-panel="basic"]'), '見出し語')).toBe('勉強')

    // 詳細が無くても、詳細から作るタブは 0 件として数える（中身は空の案内になる）
    expect(dialog.get('[data-jp-detail-tabs]').findAll('[role="tab"]').map((tab) => tab.text())).toEqual([
      '収録 0', '基本情報', '語義 0', '例文 0', '発音 0', 'コロケーション 0', '関連語 0', '使用注意 0', '問題 0'
    ])
  })


  it('詳細ダイアログの読み込みに失敗したら alert に出す', async () => {
    const { wrapper } = await setup({
      handlers: (url, method) =>
        method === 'GET' && url === '/api/user/japanese/words/101' ? failure('取得できません') : null
    })

    await wrapper.get('[data-jp-word-row="101"] [data-jp-detail]').trigger('click')
    await flushPromises()

    const dialog = wrapper.get('[data-jp-detail-dialog]')
    expect(dialog.get('.alert--danger').text()).toContain('取得できません')
    // 読み込めなかったので、タブも中身も出さない
    expect(dialog.find('[data-jp-detail-tabs]').exists()).toBe(false)
    expect(dialog.find('[data-jp-detail-panel]').exists()).toBe(false)
  })

  it('件数とページを変えるとその条件で検索し直す', async () => {
    const { wrapper, fetchMock } = await setup({ totalElements: 60 })

    expect(wrapper.get('.pagination__info').text()).toContain('全 60 件（1 / 3 ページ）')

    const pages = wrapper.findAll('.pagination__pages .page-btn')
    const third = pages.find((button) => button.text() === '3')
    expect(third).toBeDefined()
    await third?.trigger('click')
    await flushPromises()
    expect(recorded(fetchMock).at(-1)?.url).toBe('/api/user/japanese/words?page=3&size=20')

    await wrapper.get('[data-jp-page-size]').setValue('50')
    await flushPromises()
    expect(recorded(fetchMock).at(-1)?.url).toBe('/api/user/japanese/words?page=1&size=50')
    expect(wrapper.get('.pagination__info').text()).toContain('全 60 件（1 / 2 ページ）')
  })

  it('再読み込みは今の条件のまま取り直す', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-filter="keyword"]').setValue('勉強')
    await wrapper.get('[data-jp-search]').trigger('click')
    await flushPromises()
    const before = recorded(fetchMock).filter(
      (entry) => entry.method === 'GET' && entry.url.startsWith('/api/user/japanese/words?')
    ).length

    await wrapper.get('[data-jp-refresh]').trigger('click')
    await flushPromises()

    const after = recorded(fetchMock).filter(
      (entry) => entry.method === 'GET' && entry.url.startsWith('/api/user/japanese/words?')
    )
    expect(after.length).toBe(before + 1)
    expect(after.at(-1)?.url).toContain(`keyword=${encodeURIComponent('勉強')}`)
  })
})
