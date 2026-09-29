import { beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import DemoWordListView from '@/views/japanese/demo/DemoWordListView.vue'
import DemoWordEditView from '@/views/japanese/demo/DemoWordEditView.vue'
import DemoWordStudyView from '@/views/japanese/demo/DemoWordStudyView.vue'
import { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'
import { DRAFT_KEY, readStoredDraft, writeStoredDraft } from '@/features/japanese-demo/store/draftStorage'

/**
 * 日本語勉強【単語情報管理】の画面。
 *
 * 確かめること:
 * ・本番 API（fetch）を一度も呼ばない
 * ・2.0 と同じ形（詳細編集と学習画面は**別ウィンドウ**、新規登録と削除確認は**ダイアログ**）
 * ・画面に「デモ」の表示を出さない（本物の画面と同じ見た目にする）
 * ・一覧の状態ごとに行の操作が変わる
 * ・新規登録の 3 ステップ、削除確認、編集の保存（失敗・衝突）、学習画面のタブと練習
 */

function makeRouter() {
  return createRouter({
    history: createWebHashHistory(),
    routes: [
      { path: '/', name: 'student-japanese-demo', component: { template: '<div />' } },
      { path: '/edit', name: 'student-japanese-demo-edit', component: { template: '<div />' } },
      { path: '/study', name: 'student-japanese-demo-study', component: { template: '<div />' } }
    ]
  })
}

/** 別ウィンドウを開く呼び出しを捕まえる（テストでは本当のウィンドウは開けない）。 */
function stubWindowOpen(): { calls: { url: string; name: string; features: string }[]; restore: () => void } {
  const calls: { url: string; name: string; features: string }[] = []
  const original = window.open
  window.open = ((url?: string, name?: string, features?: string) => {
    calls.push({ url: String(url), name: String(name), features: String(features) })
    return { focus: () => undefined, closed: false } as unknown as Window
  }) as typeof window.open
  return {
    calls,
    restore: () => {
      window.open = original
    }
  }
}

async function mountList() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useJapaneseDemoStore()
  const router = makeRouter()
  await router.push('/')
  await router.isReady()
  const wrapper = mount(DemoWordListView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, store }
}

describe('単語情報管理：画面', () => {
  let fetchSpy: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchSpy = vi.fn(() => {
      throw new Error('画面が fetch を呼びました（本番 API につながってはいけません）')
    })
    vi.stubGlobal('fetch', fetchSpy)
    window.localStorage.clear()
    // jsdom には無いもの（一覧は「今の位置へスクロール」を呼ぶ）。
    // スタブしないと unhandled error になり、テストが落ちていなくても vitest が非 0 で終わる
    window.scrollTo = vi.fn()
  })

  it('一覧は本物の画面と同じ見た目で描画され、本番 API を呼ばない', async () => {
    const { wrapper } = await mountList()

    expect(wrapper.find('[data-demo-words]').exists()).toBe(true)
    expect(wrapper.findAll('[data-demo-word-row]').length).toBeGreaterThan(0)
    // 「デモ」の表示・注意書き・演示表示設定は画面に出さない
    expect(wrapper.find('[data-demo-badge]').exists()).toBe(false)
    expect(wrapper.find('[data-demo-notice]').exists()).toBe(false)
    expect(wrapper.find('[data-demo-status-panel]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('デモ')
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('検索条件は既定でたたまれ、キーワードで絞り込める', async () => {
    const { wrapper, store } = await mountList()

    expect(wrapper.find('[data-demo-advanced]').exists()).toBe(false)
    await wrapper.get('[data-demo-advanced-toggle]').trigger('click')
    expect(wrapper.find('[data-demo-advanced]').exists()).toBe(true)

    const before = wrapper.findAll('[data-demo-word-row]').length
    await wrapper.get('[data-demo-filter-keyword]').setValue('図書館')
    expect(store.filters.keyword).toBe('図書館')
    expect(wrapper.findAll('[data-demo-word-row]').length).toBeLessThan(before)
    expect(wrapper.find('[data-demo-word-row="w-toshokan"]').exists()).toBe(true)
  })

  it('「該当なし」の状態では案内を出す（URL のパラメータで状態を固定できる）', async () => {
    const { wrapper, store } = await mountList()
    // 動作確認用: 画面には出さず、URL で状態を固定する
    store.applyDisplayFromQuery('?list=NO_RESULT')
    await flushPromises()

    expect(wrapper.get('[data-demo-empty]').text()).toContain('条件に一致する単語がありません')
    expect(wrapper.find('[data-demo-words]').exists()).toBe(false)
  })

  it('未生成の語には生成の入口、生成失敗の語には理由と再試行を出す', async () => {
    const { wrapper, store } = await mountList()
    store.setPageSize(50)
    await flushPromises()

    const notGenerated = store.words.find((word) => word.detailStatus === 'NOT_GENERATED')!
    const failed = store.words.find((word) => word.detailStatus === 'FAILED')!

    const notGeneratedRow = wrapper.get(`[data-demo-word-row="${notGenerated.id}"]`)
    expect(notGeneratedRow.get('[data-demo-status="NOT_GENERATED"]').text()).toContain('未生成')
    expect(notGeneratedRow.find('[data-demo-generate]').exists()).toBe(true)
    expect(notGeneratedRow.find('[data-demo-retry]').exists()).toBe(false)

    const failedRow = wrapper.get(`[data-demo-word-row="${failed.id}"]`)
    expect(failedRow.get('[data-demo-status="FAILED"]').text()).toContain('生成失敗')
    expect(failedRow.get('[data-demo-failure]').text()).toContain(failed.failureReason ?? '')
    expect(failedRow.find('[data-demo-retry]').exists()).toBe(true)
  })

  it('「詳細編集」と「学習画面」は別ウィンドウで開く（2.0 と同じ形）', async () => {
    const { wrapper, store } = await mountList()

    const opener = stubWindowOpen()
    try {
      const row = wrapper.get('[data-demo-word-row="w-toshokan"]')

      await row.get('[data-demo-edit]').trigger('click')
      await flushPromises()
      expect(opener.calls.at(-1)?.url).toBe('/student/japanese-demo/edit?wordId=w-toshokan')
      expect(opener.calls.at(-1)?.features).toContain('popup=yes')
      // 下書きは別ウィンドウへ渡すために控えへ置く
      expect(store.draft?.id).toBe('w-toshokan')
      expect(readStoredDraft()?.id).toBe('w-toshokan')

      await row.get('[data-demo-study]').trigger('click')
      await flushPromises()
      expect(opener.calls.at(-1)?.url).toBe('/student/japanese-demo/study?wordId=w-toshokan')
      expect(opener.calls.at(-1)?.name).toBe('jpWordStudy_w-toshokan')

      // 一覧はそのまま残る（画面が切り替わらない）
      expect(wrapper.find('[data-demo-words]').exists()).toBe(true)
    } finally {
      opener.restore()
    }
  })

  it('新規登録は 1 ページのダイアログで、入力と書籍・Unit と確認が並ぶ', async () => {
    const { wrapper, store } = await mountList()

    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-demo-new-dialog]').exists()).toBe(true)
    // 画面の高さに収める（ページに縦スクロールを出さない）
    expect(wrapper.find('.jp-demo-new-overlay').exists()).toBe(true)
    // 手順を分けず、1 ページにまとめて出す
    expect(wrapper.find('[data-demo-step="1"]').exists()).toBe(true)
    expect(wrapper.find('[data-demo-step="2"]').exists()).toBe(true)
    expect(wrapper.find('[data-demo-step="3"]').exists()).toBe(true)
    // 説明・入力例・シナリオ・操作のヒントは置かない
    expect(wrapper.text()).not.toContain('単語だけを入力します')
    expect(wrapper.text()).not.toContain('セルをクリックすると選ばれ')
    expect(wrapper.find('[data-demo-sample]').exists()).toBe(false)
    expect(wrapper.find('[data-demo-scenario-new]').exists()).toBe(false)
    expect(wrapper.find('[data-demo-scenario-append]').exists()).toBe(false)
    // 開いた直後は空行 1 つ
    expect(store.registerHeadings).toEqual([''])
    expect(wrapper.findAll('[data-demo-word-row-index]').length).toBe(1)
    expect(wrapper.text()).not.toContain('デモ')

    // 表に入れると、確認と Unit の割り当てに出る
    store.setRegisterHeadings(['りんご', 'みかん', 'りんご'])
    await flushPromises()
    expect(wrapper.get('[data-demo-count-fresh]').text()).toBe('2')
    expect(wrapper.get('[data-demo-count-skipped]').text()).toBe('1')
    expect(wrapper.get('[data-demo-confirm-units]').text()).toContain('Unit')

    await wrapper.get('[data-demo-save]').trigger('click')
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.find('[data-demo-new-dialog]').exists()).toBe(false))
    expect(wrapper.get('[data-demo-notice-bar]').text()).toContain('登録しました')
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('新規登録の並びは「書籍 → 単語 → オプション」の順で、選択はオプションにまとめる', async () => {
    const { wrapper, store } = await mountList()

    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()
    store.setRegisterHeadings(['りんご', 'みかん'])
    await flushPromises()

    // 上から順に: 1 書籍、2 単語、3 オプション（最後に登録する内容）
    const dialog = wrapper.get('[data-demo-new-dialog]')
    const order = dialog
      .findAll('[data-demo-step]')
      .map((section) => section.attributes('data-demo-step'))
    expect(order.slice(0, 3)).toEqual(['1', '2', '3'])
    expect(dialog.get('[data-demo-step="1"]').text()).toContain('書籍')
    expect(dialog.get('[data-demo-step="1"]').text()).not.toContain('書籍と Unit')
    expect(dialog.get('[data-demo-step="1"]').find('[data-demo-word-grid]').exists()).toBe(false)
    expect(dialog.get('[data-demo-step="2"]').find('[data-demo-word-grid]').exists()).toBe(true)

    // 重複の扱いと登録位置は、まとめてオプションの中に置く
    const options = dialog.get('[data-demo-step="3"]')
    expect(options.find('[data-demo-book-select]').exists()).toBe(false)
    for (const selector of [
      '[data-demo-duplicate-book]',
      '[data-demo-duplicate-all]',
      '[data-demo-placement-continue]',
      '[data-demo-placement-new-unit]',
      '[data-demo-duplicate-note]'
    ]) {
      expect(options.find(selector).exists(), selector).toBe(true)
    }

    // まとめ（登録する内容）はオプションより後ろ
    const summary = dialog.get('[data-demo-confirm-summary]').element
    const optionsElement = options.element
    expect(optionsElement.compareDocumentPosition(summary) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('オプションは単語の表の下に、見出し・枠なしで「登録開始Unit」と「重複判定範囲」を左右に並べる', async () => {
    const { wrapper, store } = await mountList()

    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()
    store.setRegisterHeadings(['りんご', 'みかん'])
    await flushPromises()

    const dialog = wrapper.get('[data-demo-new-dialog]')
    const wordSection = dialog.get('[data-demo-step="2"]').element
    const options = dialog.get('[data-demo-step="3"]')

    // 並びは「単語の表 → オプション」（右には置かない）
    expect(wordSection.compareDocumentPosition(options.element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()

    // 「オプション」の子見出しを置く（囲みの枠は付けない）
    expect(options.get('.jp-demo-section__title').text()).toBe('オプション')

    // 2 つの組が左右に並ぶ（それぞれ見出しの下に選択肢）
    const groups = options.findAll('.jp-demo-options')
    expect(groups.length).toBe(2)
    expect(groups[0]?.element.compareDocumentPosition(groups[1]!.element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()

    const nameOf = (selector: string) => options.get(selector).element.closest('label')?.textContent?.trim() ?? ''
    const group = (index: number) => options.findAll('.jp-demo-options')[index]!
    expect(group(0).text()).toContain('登録開始Unit')
    expect(nameOf('[data-demo-placement-continue]')).toBe('最後の Unit の続きから')
    expect(nameOf('[data-demo-placement-new-unit]')).toBe('新しい Unit から')
    expect(group(1).text()).toContain('重複判定範囲')
    expect(nameOf('[data-demo-duplicate-book]')).toBe('登録対象書籍')
    expect(nameOf('[data-demo-duplicate-all]')).toBe('すべて書籍')

    // 説明は「登録開始 Unit」と「重複判定範囲」の、それぞれの列の下に出す
    expect(group(0).find('[data-demo-placement-note]').exists()).toBe(true)
    expect(group(1).find('[data-demo-duplicate-note]').exists()).toBe(true)
    expect(group(0).find('[data-demo-duplicate-note]').exists()).toBe(false)

    // 組の中の 2 つは上下に並べる（左右ではない）
    for (const each of groups) {
      expect(each.findAll('.jp-demo-option').length).toBe(2)
    }

    // 選ぶものは 4 つ。1 つずつは短い見出しだけで、説明文を付けない
    const choices = options.findAll('.jp-demo-option')
    expect(choices.length).toBe(4)
    for (const choice of choices) {
      expect(choice.findAll('small').length).toBe(0)
      expect(choice.text().length).toBeLessThanOrEqual(42)
    }
  })

  it('1 Unit あたりの単語数は、既存・新規どちらも同じ位置のプルダウンで選ぶ', async () => {
    const { wrapper, store } = await mountList()
    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()

    // 既存の書籍: その本の語数（20）が選ばれた状態で出る
    const unitSelect = wrapper.get('[data-demo-unit-size]')
    expect(unitSelect.element.tagName).toBe('SELECT')
    const options = unitSelect.findAll('option').map((option) => option.text())
    expect(options).toEqual(['10 語', '15 語', '20 語', '30 語', '50 語'])
    expect((unitSelect.element as HTMLSelectElement).value).toBe('20')
    expect(store.registerUnitSize).toBe(20)

    // 選び直すと、その値で割り当てを計算する
    await unitSelect.setValue('15')
    await flushPromises()
    expect(store.registerUnitSize).toBe(15)

    // 新しい書籍に切り替えても、同じ場所に同じ形で出る（自動計算の文字は置かない）
    await wrapper.get('[data-demo-book-select]').setValue('__NEW_BOOK__')
    await flushPromises()
    expect(wrapper.get('[data-demo-unit-size]').element.tagName).toBe('SELECT')
    expect(wrapper.find('[data-demo-unit-size-auto]').exists()).toBe(false)

    // 3 つは同じ行に並ぶ。既存の書籍のときは、名前の欄は枠だけ残して隠す
    // （枠ごと消すと語数のプルダウンが左へ寄り、位置が食い違うため）
    const bookRow = wrapper.get('.jp-demo-register__book')
    expect(bookRow.findAll('.filter-item').length).toBe(3)
    const nameField = bookRow.get('[data-demo-book-name-field]')
    expect(nameField.find('[data-demo-book-name]').exists()).toBe(true)

    await wrapper.get('[data-demo-book-select]').setValue(store.bookNameOptions[0]!)
    await flushPromises()
    expect(nameField.classes()).toContain('is-placeholder')

    await wrapper.get('[data-demo-book-select]').setValue('__NEW_BOOK__')
    await flushPromises()
    expect(wrapper.get('[data-demo-book-name-field]').classes()).not.toContain('is-placeholder')
  })

  it('行を足すボタンは「単語」の見出しの右にあり、押すと表の行が増える（消すと減る）', async () => {
    const { wrapper, store } = await mountList()
    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('[data-demo-word-row-index]').length).toBe(1)
    const button = wrapper.get('[data-demo-step="2"] [data-demo-add-row-button]')
    expect(button.element.closest('.jp-demo-register__wordhead')).not.toBe(null)
    await button.trigger('click')
    await flushPromises()
    expect(store.registerHeadings.length).toBe(2)
    expect(wrapper.findAll('[data-demo-word-row-index]').length).toBe(2)

    store.setRegisterHeadings(['あ', 'い', 'う'])
    await flushPromises()
    expect(wrapper.findAll('[data-demo-word-row-index]').length).toBe(3)

    await wrapper.get('[data-demo-remove-row="1"]').trigger('click')
    await flushPromises()
    expect(store.registerHeadings).toEqual(['あ', 'う'])
  })

  it('単語の表は決め打ちの幅にせず、置かれた幅いっぱいを使う（はみ出しを作らない）', async () => {
    const { wrapper } = await mountList()
    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()

    // 幅は px で決め打ちしない（列の幅をつまむまでは、置かれた幅いっぱいを使う）。
    // 100% ちょうどだと枠の端数で 1px はみ出し、初期状態で横スクロールバーが出るため 1px 引く。
    const table = wrapper.get('[data-demo-word-grid]')
    expect(table.attributes('style') ?? '').toBe('width: calc(100% - 1px);')
    expect(table.findAll('col').at(-1)?.attributes('style') ?? '').not.toContain('width')
  })

  it('単語の表の枠は高さを固定し、下の欄に重ならないようにする', async () => {
    // 枠を伸び縮みさせると、画面が低いときに枠が潰れて
    // 中の表が「オプション」に重なり、マウスが表に当たらなくなる。
    const css = readFileSync(resolve(__dirname, '../src/features/japanese-demo/japanese-demo.css'), 'utf8')
    const rule = css.match(/\.jp-demo-register \.jp-sheet-scroll \{([^}]*)\}/)?.[1] ?? ''
    expect(rule).toContain('height: 24rem')
    expect(rule).toContain('overflow: auto')
    expect(rule).not.toContain('flex: 1')
  })

  it('Excel 風の表: 入力中のセルは緑の罫線 1 本で示す（内側の枠は足さない）', async () => {
    const { wrapper, store } = await mountList()
    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()
    store.setRegisterHeadings(['りんご', 'みかん'])
    await flushPromises()

    await wrapper.get('[data-demo-word-cell="0"]').trigger('mousedown')
    await wrapper.get('[data-demo-word-grid]').trigger('keydown', { key: 'F2' })
    await flushPromises()

    // 入力中の印がセルに付く（以前は付いておらず、選択の枠と二重に見えていた）
    const editing = wrapper.get('[data-demo-word-cell="0"]')
    expect(editing.classes()).toContain('is-editing')
    expect(editing.find('[data-demo-word-input]').exists()).toBe(true)

    // 入力中のセルには選択の印を重ねない
    expect(editing.classes()).not.toContain('is-selected')
    expect(editing.classes()).not.toContain('is-range')
  })

  it('Excel 風の表: クリックでは入力欄が出ず、ダブルクリック / F2 で入力する', async () => {
    const { wrapper, store } = await mountList()
    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()
    store.setRegisterHeadings(['りんご', 'みかん', 'ぶどう'])
    await flushPromises()

    // 値はテキストで出ていて、入力欄は出ていない（Excel と同じ）
    expect(wrapper.findAll('[data-demo-word-value]').length).toBeGreaterThan(0)
    expect(wrapper.findAll('[data-demo-word-input]').length).toBe(0)

    // セルをクリック＝選ぶだけ（入力欄は出ない・選択の印が付く）
    const firstCell = wrapper.get('[data-demo-word-cell="0"]')
    await firstCell.trigger('mousedown')
    expect(firstCell.classes()).toContain('is-selected')
    expect(wrapper.findAll('[data-demo-word-input]').length).toBe(0)

    // ダブルクリックで入力できる
    await firstCell.trigger('dblclick')
    await flushPromises()
    expect(wrapper.findAll('[data-demo-word-input]').length).toBe(1)
    const input = wrapper.get('[data-demo-word-input="0"]')
    expect((input.element as HTMLInputElement).value).toBe(store.registerHeadings[0])

    // 入力すると、その場で表と解釈の両方に反映される
    await input.setValue('書き換えた語')
    await flushPromises()
    expect(store.registerHeadings[0]).toBe('書き換えた語')
    expect(store.parsedRows[0]?.heading).toBe('書き換えた語')

    // Enter で確定すると、入力欄が閉じて次の行へ移る
    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(wrapper.findAll('[data-demo-word-input]').length).toBe(0)
    expect(wrapper.get('[data-demo-word-cell="1"]').classes()).toContain('is-selected')
  })

  it('Excel 風の表: F2 で入力、Esc で取り消し、Delete で消せる', async () => {
    const { wrapper, store } = await mountList()
    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()
    store.setRegisterHeadings(['りんご', 'みかん'])
    await flushPromises()

    // F2 で入力モード（クリックだけでは入らない）
    await wrapper.get('[data-demo-word-cell="0"]').trigger('mousedown')
    await wrapper.get('[data-demo-word-grid]').trigger('keydown', { key: 'F2' })
    await flushPromises()
    expect(wrapper.findAll('[data-demo-word-input]').length).toBe(1)

    // 書き換えてから Esc で取り消すと、元の値に戻る
    const input = wrapper.get('[data-demo-word-input="0"]')
    await input.setValue('りんご（書きかけ）')
    await input.trigger('keydown', { key: 'Escape' })
    await flushPromises()
    expect(store.registerHeadings[0]).toBe('りんご')

    // セルを選んで Delete で消せる
    await wrapper.get('[data-demo-word-cell="1"]').trigger('mousedown')
    await wrapper.get('[data-demo-word-grid]').trigger('keydown', { key: 'Delete' })
    await flushPromises()
    expect(store.registerHeadings[1]).toBe('')
    expect(store.registerWords.map((word) => word.heading)).toEqual(['りんご'])
  })

  it('Excel 風の表: 選んだセルを起点に貼り付け、足りない行は増える', async () => {
    const { wrapper, store } = await mountList()
    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()
    store.setRegisterHeadings(['既存の語'])
    await flushPromises()

    // 1 行目を選んでから、Excel のコピー（タブ区切り）を貼る
    await wrapper.get('[data-demo-word-cell="0"]').trigger('mousedown')
    const paste = (text: string) => wrapper.get('[data-demo-word-grid]').trigger('paste', {
      clipboardData: { getData: () => text }
    })
    await paste('りんご\tapple\tリンゴ\nみかん\tmandarin\tミカン')
    await flushPromises()

    // 1 列目だけを使い、選んだセルから下へ流し込む（余計な列は無視する）
    expect(store.registerHeadings).toEqual(['りんご', 'みかん'])
    expect(wrapper.find('[data-demo-paste-notice]').exists()).toBe(true)

    // 行が足りなければ自動で増える
    await wrapper.get('[data-demo-word-cell="1"]').trigger('mousedown')
    await paste('ぶどう\nもも\nなし')
    await flushPromises()
    expect(store.registerHeadings).toEqual(['りんご', 'ぶどう', 'もも', 'なし'])
  })

  it('書籍はプルダウンで選び、最後の「新しい書籍を追加…」で入力欄が出る', async () => {
    const { wrapper, store } = await mountList()

    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()
    store.setRegisterHeadings(['りんご', 'みかん'])
    await flushPromises()

    // 既存の書籍: 1 Unit の語数は本から自動で決まり、その値が選ばれた状態で出る
    const options = wrapper.get('[data-demo-book-select]').findAll('option').map((option) => option.text())
    expect(options.at(-1)).toBe('新しい書籍を追加…')
    expect(options.length).toBe(store.bookNameOptions.length + 1)
    expect((wrapper.get('[data-demo-unit-size]').element as HTMLSelectElement).value).toBe('20')

    // 「新しい書籍を追加…」を選ぶと、名前の入力欄が出る（語数のプルダウンは同じ場所のまま）
    await wrapper.get('[data-demo-book-select]').setValue('__NEW_BOOK__')
    await flushPromises()
    expect(wrapper.find('[data-demo-book-name]').exists()).toBe(true)
    expect(wrapper.find('[data-demo-unit-size]').exists()).toBe(true)
    expect(store.registerBookMode).toBe('NEW')
  })

  it('重複した単語の扱いをラジオで選べる（既定はこの書籍の中）', async () => {
    const { wrapper, store } = await mountList()

    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()
    // 既定は「この書籍の中で重複した単語を飛ばす」
    expect(store.registerDuplicateMode).toBe('BOOK')
    expect((wrapper.get('[data-demo-duplicate-book]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.get('[data-demo-duplicate-all]').element as HTMLInputElement).checked).toBe(false)

    // 切り替えると、説明も「すべての書籍」に変わる
    await wrapper.get('[data-demo-duplicate-all]').setValue(true)
    await flushPromises()
    expect(store.registerDuplicateMode).toBe('ALL')
    expect(wrapper.get('[data-demo-duplicate-note]').text()).toContain('ほかの書籍')
  })

  it('登録に失敗したらダイアログを閉じず、理由を出して入力を残す', async () => {
    vi.useFakeTimers()
    const { wrapper, store } = await mountList()
    store.setDisplay({ saveMode: 'SAVE_FAILED' })

    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()
    store.setRegisterHeadings(['失敗確認用の語'])
    await flushPromises()
    const before = store.words.length

    await wrapper.get('[data-demo-save]').trigger('click')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    expect(store.words.length).toBe(before)
    expect(wrapper.find('[data-demo-new-dialog]').exists(), '失敗したら閉じない').toBe(true)
    expect(wrapper.get('[data-demo-register-error]').text()).toContain('登録できませんでした')
    // 入力（表の中身）は残す（やり直せる）
    expect(store.registerHeadings.length).toBeGreaterThan(0)
    vi.useRealTimers()
  })

  it('削除確認はダイアログで出し、失敗したら閉じずに理由を出す', async () => {
    vi.useFakeTimers()
    const { wrapper, store } = await mountList()

    const count = store.words.length
    store.setDisplay({ saveMode: 'SAVE_FAILED' })
    await wrapper.get('[data-demo-delete]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-demo-delete-dialog]').exists()).toBe(true)

    await wrapper.get('[data-demo-delete-confirm]').trigger('click')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(wrapper.find('[data-demo-delete-dialog]').exists(), '失敗したら閉じない').toBe(true)
    expect(wrapper.get('[data-demo-delete-error]').text()).toContain('削除できませんでした')
    expect(store.words.length).toBe(count)

    store.setDisplay({ saveMode: 'NORMAL' })
    await wrapper.get('[data-demo-delete-confirm]').trigger('click')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(wrapper.find('[data-demo-delete-dialog]').exists()).toBe(false)
    expect(store.words.length).toBe(count - 1)
    vi.useRealTimers()
  })

  it('詳細編集は保存の失敗・衝突を再現し、入力を残す', async () => {
    vi.useFakeTimers()
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useJapaneseDemoStore()
    const target = store.words.find((word) => word.detailStatus === 'GENERATED')!
    store.openEditor(target.id)
    const router = makeRouter()
    await router.push('/edit')
    await router.isReady()

    const wrapper = mount(DemoWordEditView, { global: { plugins: [pinia, router] } })
    await flushPromises()

    expect(wrapper.text()).not.toContain('デモ')
    expect(wrapper.findAll('[data-demo-editor-nav-item]').length).toBe(11)

    store.setDisplay({ saveMode: 'SAVE_FAILED' })
    await wrapper.get('[data-demo-edit-chinese]').setValue('変更した意味')
    await wrapper.get('[data-demo-editor-save]').trigger('click')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(wrapper.get('[data-demo-save-failed]').text()).toContain('保存できませんでした')
    expect(store.draft?.chineseMeaning).toBe('変更した意味')

    store.setDisplay({ saveMode: 'CONFLICT' })
    await wrapper.get('[data-demo-editor-save]').trigger('click')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(wrapper.find('[data-demo-save-conflict]').exists()).toBe(true)
    expect(store.draft?.chineseMeaning).toBe('変更した意味')

    store.setDisplay({ saveMode: 'NORMAL' })
    await wrapper.get('[data-demo-editor-save]').trigger('click')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(wrapper.find('[data-demo-save-saved]').exists()).toBe(true)
    vi.useRealTimers()
  })

  it('未保存のまま閉じようとすると確認が出る', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useJapaneseDemoStore()
    const target = store.words.find((word) => word.detailStatus === 'GENERATED')!
    store.openEditor(target.id)
    const router = makeRouter()
    await router.push('/edit')
    await router.isReady()

    const wrapper = mount(DemoWordEditView, { global: { plugins: [pinia, router] } })
    await flushPromises()

    await wrapper.get('[data-demo-edit-heading]').setValue('変更した見出し')
    await wrapper.get('[data-demo-editor-cancel]').trigger('click')
    expect(wrapper.find('[data-demo-leave-prompt]').exists()).toBe(true)
    expect(store.draft?.heading).toBe('変更した見出し')
  })

  it('学習画面は日本語と中国語を分けて見せ、見出し・発音・タブの順に組む（2.0 と同じ）', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useJapaneseDemoStore()
    const word = store.findWord('w-toshokan')!

    const wrapper = mount(DemoWordStudyView, { props: { word }, global: { plugins: [pinia] } })
    await flushPromises()

    // 見出しの帯に、語・読み・品詞・JLPT と収録（書籍／Unit）をまとめる
    const hero = wrapper.get('[data-demo-study-hero]')
    expect(hero.get('[data-demo-study-word]').text()).toBe('図書館')
    expect(hero.get('[data-demo-study-reading]').text()).toBe('としょかん')
    expect(hero.text()).toContain('名詞')
    expect(hero.text()).toContain('N5')
    expect(wrapper.get('[data-demo-study-head]').get('[data-demo-study-collection]').text()).toContain('Unit001')

    // 中国語の意味は、日本語と混ぜずに独立した帯で見せる
    expect(wrapper.get('[data-demo-study-primary-text]').text()).toBe('图书馆')
    expect(wrapper.find('[data-demo-study-primary]').element.getAttribute('lang')).toBe(null)
    expect(wrapper.get('[data-demo-study-primary-text]').element.getAttribute('lang')).toBe('zh-CN')
    expect(wrapper.find('[data-demo-study-lang="zh"]').exists()).toBe(true)

    // 例文と会話は 1 文ずつ、日本語と中国語を分けて出す
    await wrapper.get('[data-demo-study-tab="examples"]').trigger('click')
    const firstExample = wrapper.get('[data-demo-study-example]')
    expect(firstExample.find('[data-demo-study-lang="ja"]').exists()).toBe(true)
    expect(firstExample.find('[data-demo-study-lang="zh"]').exists()).toBe(true)

    await wrapper.get('[data-demo-study-tab="examples"]').trigger('click')
    await flushPromises()
    const firstLine = wrapper.get('[data-demo-dialog-line]')
    expect(firstLine.find('[data-demo-study-lang="ja"]').exists()).toBe(true)
    expect(firstLine.find('[data-demo-study-lang="zh"]').exists()).toBe(true)

    // タブは 2.0 の A 学習と同じ並び（語義・説明 / 発音 / コロケーション / 例文 / 関連語・使用注意 / 練習）
    const tabs = wrapper.findAll('[data-demo-study-tab]').map((tab) => tab.attributes('data-demo-study-tab'))
    expect(tabs.slice(0, 5)).toEqual(['meaning', 'form', 'collocations', 'examples', 'compare'])

    // 両言語は常に表示する。
    expect(wrapper.findAll('[data-demo-study-lang="zh"]').length).toBeGreaterThan(0)
    expect(wrapper.findAll('[data-demo-study-lang="ja"]').length).toBeGreaterThan(0)
  })

  it('学習内容は選択中のタブだけに出し、切り替え入口の前に積み上げない', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useJapaneseDemoStore()
    const word = store.findWord('w-toshokan')!

    const wrapper = mount(DemoWordStudyView, { props: { word }, global: { plugins: [pinia] } })
    await flushPromises()

    expect(wrapper.get('[data-demo-study-word]').text()).toBe('図書館')
    expect(wrapper.get('[data-demo-study-core]').text()).not.toBe('')
    expect(wrapper.find('[data-demo-study-example]').exists()).toBe(false)
    expect(wrapper.find('[data-demo-study-caution]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('まず覚えること')
    expect(wrapper.findAll('[role="tabpanel"]').length).toBe(1)
    await wrapper.get('[data-demo-study-tab="collocations"]').trigger('click')
    expect(wrapper.find('[data-demo-study-pattern]').exists()).toBe(true)
    await wrapper.get('[data-demo-study-tab="examples"]').trigger('click')
    expect(wrapper.findAll('[data-demo-study-example]').length).toBeGreaterThanOrEqual(2)
    await wrapper.get('[data-demo-study-tab="compare"]').trigger('click')
    expect(wrapper.find('[data-demo-study-caution]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('デモ')

    const tabs = wrapper.findAll('[data-demo-study-tab]').map((tab) => tab.attributes('data-demo-study-tab'))
    expect(tabs).toEqual(['meaning', 'form', 'collocations', 'examples', 'compare', 'practice'])

    // ミニ練習はこの画面の中で完結する
    await wrapper.get('[data-demo-study-tab="practice"]').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[data-demo-quiz-choice]').length).toBeGreaterThan(0)
    await wrapper.get('[data-demo-quiz-choice]').trigger('click')
    expect(wrapper.find('[data-demo-quiz-result]').exists()).toBe(true)
  })

  it('学習画面は読みと中国語訳を常に表示し、音声ボタンをスピーカーに統一する', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useJapaneseDemoStore()
    const word = store.findWord('w-toshokan')!

    const wrapper = mount(DemoWordStudyView, { props: { word }, global: { plugins: [pinia] } })
    await flushPromises()

    expect(wrapper.find('[data-demo-study-reading]').exists()).toBe(true)
    expect(wrapper.find('[data-demo-toggle-reading]').exists()).toBe(false)
    expect(wrapper.find('[data-demo-toggle-chinese]').exists()).toBe(false)
    expect(wrapper.find('[data-demo-study-primary]').exists()).toBe(true)
    expect(wrapper.find('[data-demo-study-core]').exists()).toBe(true)
    // 日本語は残る
    expect(wrapper.get('[data-demo-study-word]').text()).toBe('図書館')
    for (const tab of ['meaning', 'form', 'examples']) {
      await wrapper.get(`[data-demo-study-tab="${tab}"]`).trigger('click')
      const buttons = wrapper.findAll('button.jp-study-sound')
      expect(buttons.length).toBeGreaterThan(0)
      for (const button of buttons) {
        expect(button.get('use').attributes('href')).toBe('#i-speaker')
        expect(button.text()).toBe('')
        expect(button.attributes('aria-label')).toBeTruthy()
      }
    }
    await wrapper.get('[data-demo-dialog-play]').trigger('click')
    expect(wrapper.get('[data-demo-study-dialog]').text()).toContain('再生中')
  })

  it('下書きを表示するときは、未保存であることを示す', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useJapaneseDemoStore()
    const target = store.words.find((word) => word.detailStatus === 'GENERATED')!
    store.openEditor(target.id)

    const wrapper = mount(DemoWordStudyView, {
      props: { word: store.draft!, draft: true },
      global: { plugins: [pinia] }
    })
    await flushPromises()

    expect(wrapper.find('[data-demo-draft-band]').exists()).toBe(true)
    expect(wrapper.get('[data-demo-draft-band]').text()).toContain('未保存')
  })
})

/** 下書きの控え（別ウィンドウへ渡す仕組み）。 */
describe('下書きの控え', () => {
  it('書いて読み直せる（別ウィンドウはこれで下書きを受け取る）', () => {
    window.localStorage.clear()
    const word = { id: 'w-x', heading: '試験' } as never
    writeStoredDraft(word)
    expect(JSON.parse(window.localStorage.getItem(DRAFT_KEY) ?? 'null')).toMatchObject({ id: 'w-x' })
    expect(readStoredDraft()?.id).toBe('w-x')
    writeStoredDraft(null)
    expect(window.localStorage.getItem(DRAFT_KEY)).toBeNull()
  })
})
