import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import TodoView from '@/views/todo/TodoView.vue'
import type { TodoRow } from '@/api/todos'

/**
 * TODO（2.0 の todo.jsp を作り直した画面）。
 *
 *  ・一覧（親タスク＋子タスク）／カレンダー（期限日ごとの件数）
 *  ・新規／編集ダイアログの子タスクは Excel 風グリッド（行追加・×削除・貼り付け）
 *  ・保護者のときは「お子さまの TODO にも登録する」
 */
function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function todoRow(overrides: Partial<TodoRow> = {}): TodoRow {
  return {
    todoId: 1,
    parentTodoId: null,
    order: 0,
    title: '数学の宿題',
    memo: 'p.20-22',
    status: 'TODO',
    priority: 'HIGH',
    dueDate: '2026-09-20',
    startedAt: null,
    completedAt: null,
    createdByName: '劉競澤',
    childCount: 1,
    doneChildCount: 0,
    version: 1,
    children: [{
      todoId: 2, parentTodoId: 1, order: 0, title: '問1〜3', memo: null, status: 'TODO',
      priority: 'NORMAL', dueDate: null, startedAt: null, completedAt: null,
      createdByName: '劉競澤', childCount: 0, doneChildCount: 0, version: 1, children: []
    }],
    ...overrides
  }
}

type Call = { url: string; method: string; body: Record<string, unknown> | null }

function recorded(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return fetchMock.mock.calls.map((call) => {
    const init = (call[1] ?? {}) as RequestInit
    return {
      url: String(call[0]),
      method: (init.method ?? 'GET').toUpperCase(),
      body: init.body ? JSON.parse(String(init.body)) as Record<string, unknown> : null
    }
  })
}

beforeEach(() => {
  useToast().items.splice(0)
  window.scrollTo = vi.fn()
  window.sessionStorage.setItem('study21.auth.v2', JSON.stringify({ username: '検証 生徒', role: 'STUDENT' }))
})

async function setup(options: { items?: TodoRow[]; role?: string; attachTo?: boolean } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  if (options.role) {
    window.sessionStorage.setItem('study21.auth.v2', JSON.stringify({ username: '検証', role: options.role }))
  }
  const items = options.items ?? [todoRow()]
  const fetchMock = vi.fn(async (url: string) => {
    const target = String(url)
    if (target.includes('/todos/calendar')) {
      return ok({ year: 2026, month: 9, cells: [{ dueDate: '2026-09-20', openCount: 2, doneCount: 1 }] })
    }
    if (target.includes('/todos')) {
      return ok({
        items, totalElements: items.length, page: 1, size: 20, totalPages: 1,
        openCount: 3, doingCount: 1, doneCount: 5
      })
    }
    return ok({ message: 'TODOを登録しました。', todoId: 99, updatedCount: 1 })
  })
  vi.stubGlobal('fetch', fetchMock)
  // フォーカス（document.activeElement）を見るテストだけ document に載せる
  const wrapper = mount(TodoView, {
    global: { plugins: [pinia] },
    ...(options.attachTo === true ? { attachTo: document.body } : {})
  })
  await flushPromises()
  await flushPromises()
  return { wrapper, fetchMock }
}

/** 新規ダイアログを開く。 */
async function openCreateDialog(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper.get('.todo-head .btn--primary').trigger('click')
  await flushPromises()
}

/** グリッドのセルをダブルクリックして編集状態にし、入力欄を返す。 */
async function editCell(wrapper: ReturnType<typeof mount>, cell: string) {
  await wrapper.get(`[data-cell="${cell}"]`).trigger('dblclick')
  await flushPromises()
  return wrapper.get(`[data-cell="${cell}"] input, [data-cell="${cell}"] select`)
}

/** グリッドのセルに出ている文字（編集中でないときの表示）。 */
function cellText(wrapper: ReturnType<typeof mount>, cell: string): string {
  const values = wrapper.get(`[data-cell="${cell}"]`).findAll('.todo-grid__value')
  return values.length === 0 ? '' : values[0].text()
}

describe('TODO', () => {
  it('一覧にカウンタと親子タスク・優先度・期限・状態が出る', async () => {
    const { wrapper } = await setup()

    expect(wrapper.text()).toContain('未着手')
    expect(wrapper.text()).toContain('進行中')
    expect(wrapper.text()).toContain('完了')
    const row = wrapper.get('tbody tr[data-todo-id="1"]')
    expect(row.text()).toContain('数学の宿題')
    expect(row.text()).toContain('高')           // 優先度
    expect(row.text()).toContain('2026/9/20')    // 期限（formatIsoDate は 0 埋めしない）
    expect(row.text()).toContain('p.20-22')      // メモ列
    // 子タスクは既定で折りたたまれている（タイトルの横に 完了/全体 を出す）
    expect(row.text()).toContain('数学の宿題')
    expect(row.text()).toContain('0/1')
    expect(row.text()).not.toContain('子タスク')
    expect(wrapper.find('tbody tr[data-child-id="2"]').exists()).toBe(false)
    // 列の並び: 操作が最初、そのあとタイトル
    const headers = wrapper.findAll('thead th').map((th) => th.text())
    expect(headers[0]).toBe('操作')
    expect(headers).toContain('メモ')
    expect(headers).not.toContain('作成者')
    // 選択用チェックボックスは置かない
    expect(wrapper.findAll('tbody input[type="checkbox"]').length).toBe(0)
  })

  it('子タスクは既定で閉じていて、開閉ボタンで展開・折りたたみできる', async () => {
    const { wrapper } = await setup()

    // 既定は閉じている
    expect(wrapper.find('tbody tr[data-child-id="2"]').exists()).toBe(false)

    const toggle = wrapper.get('[data-toggle="1"]')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    // 表示は「タイトル 完了/全体」（例: ハリポタ 0/17）
    expect(toggle.get('.todo-toggle__title').text()).toBe('数学の宿題')
    expect(toggle.get('.todo-toggle__count').text()).toBe('0/1')

    // 展開すると子タスクが表の行として出る（親と同じ列に並ぶ）
    await toggle.trigger('click')
    const child = wrapper.get('tbody tr[data-child-id="2"]')
    expect(child.attributes('data-parent-id')).toBe('1')
    expect(child.text()).toContain('問1〜3')
    // 子タスクにも優先度・期限・メモ・状態の列がある（6 列）
    expect(child.findAll('td').length).toBe(6)
    // ツリー記号（├ └）は使わず、タイトルは 2 文字ぶん下げる（CSS で指定）
    expect(child.text()).not.toContain('├')
    expect(child.text()).not.toContain('└')
    expect(child.find('.todo-child-row__title').exists()).toBe(true)
    expect(wrapper.get('[data-toggle="1"]').attributes('aria-expanded')).toBe('true')

    // もう一度押すと閉じる
    await wrapper.get('[data-toggle="1"]').trigger('click')
    expect(wrapper.find('tbody tr[data-child-id="2"]').exists()).toBe(false)
  })

  it('子タスクを持たない行には開閉ボタンを出さない', async () => {
    const { wrapper } = await setup({ items: [
      todoRow({ todoId: 5, title: '読書', childCount: 0, doneChildCount: 0, children: [] })
    ] })

    expect(wrapper.find('[data-toggle="5"]').exists()).toBe(false)
    expect(wrapper.find('[data-action="toggle-all-children"]').exists()).toBe(false)
  })

  it('【すべて展開】【すべて折りたたむ】で表示中の行をまとめて開閉する', async () => {
    const { wrapper } = await setup({ items: [
      todoRow({
        todoId: 1,
        title: '数学の宿題',
        children: [
          { todoId: 2, parentTodoId: 1, order: 0, title: '問1〜3', memo: null, status: 'TODO',
            priority: 'NORMAL', dueDate: null, startedAt: null, completedAt: null,
            createdByName: '劉競澤', childCount: 0, doneChildCount: 0, version: 1, children: [] }
        ]
      }),
      todoRow({
        todoId: 3,
        title: '英語の宿題',
        children: [
          { todoId: 4, parentTodoId: 3, order: 0, title: 'p.30', memo: null, status: 'TODO',
            priority: 'NORMAL', dueDate: null, startedAt: null, completedAt: null,
            createdByName: '劉競澤', childCount: 0, doneChildCount: 0, version: 1, children: [] }
        ]
      })
    ] })

    const all = wrapper.get('[data-action="toggle-all-children"]')
    expect(all.text()).toContain('すべて展開')

    await all.trigger('click')
    expect(wrapper.findAll('tbody tr[data-child-id]').length).toBe(2)
    expect(wrapper.get('[data-action="toggle-all-children"]').text()).toContain('すべて折りたたむ')

    await wrapper.get('[data-action="toggle-all-children"]').trigger('click')
    expect(wrapper.findAll('tbody tr[data-child-id]').length).toBe(0)
    expect(wrapper.get('[data-action="toggle-all-children"]').text()).toContain('すべて展開')
  })

  it('検索条件は見出し行にまとまっている（バッチ実行履歴と同じ形）', async () => {
    const { wrapper } = await setup()

    // 一覧は 1 枚のカードで、その見出し行に検索条件が入っている
    const cards = wrapper.findAll('.card')
    expect(cards.length).toBe(1)

    const head = wrapper.get('.table-section__head')
    expect(head.find('.todo-filters').exists()).toBe(true)
    expect(head.get('.table-section__title').text()).toBe('TODO一覧')
    expect(head.get('.table-section__meta').text()).toContain('全')

    // ラベルは置かず、aria-label と placeholder で意味を持たせる
    expect(head.find('input[aria-label="キーワード"]').attributes('placeholder')).toBe('タイトル / メモ')
    expect(head.find('select[aria-label="状態"]').exists()).toBe(true)
    expect(head.find('select[aria-label="優先度"]').exists()).toBe(true)
    expect(head.find('input[aria-label="完了も表示"]').exists()).toBe(true)
    expect(head.findAll('.filter-item__label').length).toBe(0)

    // 検索・リセットは見出し行の小さめのボタン（§9.1 の見た目ルール）
    const search = head.findAll('.todo-filters .btn').find((button) => button.text().includes('検索'))
    expect(search?.classes()).toContain('btn--primary')
    expect(search?.classes()).toContain('btn--sm')
    const reset = head.findAll('.todo-filters .btn').find((button) => button.text().includes('リセット'))
    expect(reset?.classes()).toContain('btn--secondary')
    expect(reset?.classes()).toContain('btn--sm')
  })

  it('状態・優先度の選択は変更した時点で検索する', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('select[aria-label="状態"]').setValue('DOING')
    await flushPromises()
    let call = recorded(fetchMock).reverse().find((entry) => entry.url.includes('/todos?'))
    expect(call?.url).toContain('status=DOING')

    await wrapper.get('select[aria-label="優先度"]').setValue('HIGH')
    await flushPromises()
    call = recorded(fetchMock).reverse().find((entry) => entry.url.includes('/todos?'))
    expect(call?.url).toContain('priority=HIGH')

    await wrapper.get('input[aria-label="完了も表示"]').setValue(true)
    await flushPromises()
    call = recorded(fetchMock).reverse().find((entry) => entry.url.includes('/todos?'))
    expect(call?.url).toContain('includeDone=true')
  })

  it('一覧から子タスクの状態を進められる', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-toggle="1"]').trigger('click')
    const advance = wrapper.get('[data-child-advance="2"]')
    expect(advance.attributes('title')).toContain('子タスク')
    expect(advance.attributes('title')).toContain('進行中にする')

    await advance.trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).find((entry) => entry.url === '/api/user/todos/2/status')
    expect(call?.method).toBe('POST')
    // 一覧が持っているバージョンを渡す（楽観ロック）
    expect(call?.body).toEqual({ status: 'DOING', version: 1 })
  })

  it('検索条件をクエリで送る／リセットで消える', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('input[aria-label="キーワード"]').setValue('数学')
    await wrapper.findAll('.todo-filters .btn').find((button) => button.text().includes('検索'))?.trigger('click')
    await flushPromises()
    let call = recorded(fetchMock).reverse().find((entry) => entry.url.includes('/todos?'))
    expect(call?.url).toContain('keyword=%E6%95%B0%E5%AD%A6')

    const reset = wrapper.findAll('.todo-filters .btn').find((button) => button.text().includes('リセット'))
    await reset?.trigger('click')
    await flushPromises()
    call = recorded(fetchMock).reverse().find((entry) => entry.url.includes('/todos?'))
    expect(call?.url).not.toContain('keyword=%E6%95%B0%E5%AD%A6')
  })

  it('状態のボタンで進行中→完了へ進む', async () => {
    const { wrapper, fetchMock } = await setup()

    const action = wrapper.get('tbody tr[data-todo-id="1"] .row-actions .btn')
    expect(action.attributes('title')).toBe('進行中にする')
    expect(action.find('svg.icon use').attributes('href')).toBe('#i-play')

    await action.trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).find((entry) => entry.url.includes('/todos/1/status'))
    expect(call?.method).toBe('POST')
    expect(call?.body).toEqual({ status: 'DOING', version: 1 })
  })

  it('カレンダーに期限日ごとの件数が出て、日をクリックすると新規が開く', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-tab="calendar"]').trigger('click')
    await flushPromises()

    const day = wrapper.get('[data-due-date="2026-09-20"]')
    expect(day.text()).toContain('2')  // 未完了
    expect(day.text()).toContain('1')  // 完了

    await day.trigger('click')
    await flushPromises()
    expect(wrapper.find('#todoDialogTitle').text()).toContain('新規登録')
    expect((wrapper.get('input[type="date"]').element as HTMLInputElement).value).toBe('2026-09-20')
  })

  it('新規ダイアログの子タスクは Excel 風グリッドで行を増やせる', async () => {
    const { wrapper } = await setup()

    await wrapper.get('.todo-head .btn--primary').trigger('click')  // 新規
    await flushPromises()

    const grid = wrapper.get('[data-testid="todo-grid-body"]')
    expect(grid.findAll('tr').length).toBe(1)
    expect(grid.get('td.todo-grid__no').text()).toBe('1')

    const addButton = wrapper.findAll('.dialog__body .btn').find((button) => button.text().includes('行追加'))
    await addButton?.trigger('click')
    expect(grid.findAll('tr').length).toBe(2)
    expect(grid.findAll('td.todo-grid__no').map((td) => td.text())).toEqual(['1', '2'])

    // × で行を消す
    await grid.findAll('tbody .btn')[0].trigger('click')
    expect(grid.findAll('tr').length).toBe(1)
  })

  it('「子タスク」の見出しは上の項目と同じ左端に置く（余白を付けない）', async () => {
    const { wrapper } = await setup()

    await openCreateDialog(wrapper)

    // `.table-section__head` は左右に余白を持つので、ダイアログの見出しには使わない
    const head = wrapper.get('.todo-grid__head')
    expect(head.classes()).not.toContain('table-section__head')
    expect(head.get('h3').text()).toBe('子タスク')
    // 行追加ボタンは見出しの右端
    expect(head.text()).toContain('行追加')
  })

  it('行削除の × は赤い', async () => {
    const { wrapper } = await setup()

    await openCreateDialog(wrapper)

    const icon = wrapper.get('[data-testid="todo-grid-body"] .todo-grid__remove .icon')
    expect(icon.classes()).toContain('icon--danger')
  })

  it('Excel 風グリッドの列幅は、つまみをドラッグ / キーで変えられる', async () => {
    const { wrapper } = await setup()

    await openCreateDialog(wrapper)

    // 4 列ぶんの col と、3 つのつまみ（タイトル・優先度・期限の右端）がある
    const widths = (): string[] => wrapper.findAll('.todo-grid colgroup col')
      .slice(2)
      .map((col) => (col.element as HTMLElement).style.width)
    expect(widths()).toEqual(['34%', '16%', '21%', '18%'])
    expect(wrapper.findAll('[data-col-resizer]').map((el) => el.attributes('data-col-resizer')))
      .toEqual(['title', 'priority', 'dueDate'])

    // ←→ キーで幅をやり取りする（合計は変わらない）
    await wrapper.get('[data-col-resizer="title"]').trigger('keydown', { key: 'ArrowRight' })
    expect(widths()).toEqual(['35%', '15%', '21%', '18%'])
    await wrapper.get('[data-col-resizer="title"]').trigger('keydown', { key: 'ArrowLeft', shiftKey: true })
    expect(widths()).toEqual(['30%', '20%', '21%', '18%'])

    // つまみをダブルクリックすると既定に戻る
    await wrapper.get('[data-col-resizer="title"]').trigger('dblclick')
    expect(widths()).toEqual(['34%', '16%', '21%', '18%'])

    // 狭くしすぎない（最小 8%）
    for (let index = 0; index < 30; index += 1) {
      await wrapper.get('[data-col-resizer="title"]').trigger('keydown', { key: 'ArrowLeft' })
    }
    expect(widths()).toEqual(['8%', '42%', '21%', '18%'])
  })

  it('グリッドのセルをクリックすると選ばれる（Excel 風）', async () => {
    const { wrapper } = await setup()

    await openCreateDialog(wrapper)

    expect(wrapper.get('[data-cell="0-0"]').classes()).not.toContain('is-selected')
    await wrapper.get('[data-cell="0-0"]').trigger('mousedown')
    expect(wrapper.get('[data-cell="0-0"]').classes()).toContain('is-selected')

    // ドラッグすると範囲選択になる
    await wrapper.get('[data-cell="0-2"]').trigger('mouseenter')
    expect(wrapper.get('[data-cell="0-0"]').classes()).toContain('is-range')
    expect(wrapper.get('[data-cell="0-2"]').classes()).toContain('is-range-right')
    window.dispatchEvent(new MouseEvent('mouseup'))
  })

  it('選択したセルをタブ区切りでコピーできる（Excel に貼り付けられる）', async () => {
    const { wrapper } = await setup()

    await openCreateDialog(wrapper)
    // 1 行目に入れてから、その行を選ぶ
    await (await editCell(wrapper, '0-0')).setValue('問1〜3')
    await (await editCell(wrapper, '0-1')).setValue('HIGH')
    await (await editCell(wrapper, '0-2')).setValue('2026-09-21')
    const last = await editCell(wrapper, '0-3')
    await last.setValue('p.20')
    // 入力中の Ctrl+C は入力欄の文字に任せる仕様なので、確定してから範囲をコピーする
    await last.trigger('blur')
    await flushPromises()

    await wrapper.get('[data-cell="0-0"]').trigger('mousedown')
    await wrapper.get('[data-cell="0-3"]').trigger('mouseenter')

    const clipboard = { data: {} as Record<string, string>, setData: (type: string, value: string) => { clipboard.data[type] = value } }
    const event = new Event('copy', { bubbles: true, cancelable: true })
    Object.defineProperty(event, 'clipboardData', { value: clipboard })
    wrapper.get('.todo-grid').element.dispatchEvent(event)

    // 優先度は「高」、期限は入力したままの形でコピーされる
    expect(clipboard.data['text/plain']).toBe('問1〜3\t高\t2026-09-21\tp.20')
    window.dispatchEvent(new MouseEvent('mouseup'))
  })

  it('Excel からの貼り付け（タブ区切り）をグリッドに取り込む', async () => {
    const { wrapper } = await setup()

    await openCreateDialog(wrapper)

    const grid = wrapper.get('[data-testid="todo-grid-body"]')
    const tsv = '問1\t高\t2026/09/21\t1ページ目\n問2\t低\t2026-09-22\t2ページ目'
    await grid.trigger('paste', {
      clipboardData: { getData: () => tsv }
    })
    await flushPromises()

    expect(grid.findAll('tr').length).toBe(2)
    // 取り込んだ値はセルの表示に出る（入力欄は編集のときだけ）
    expect(cellText(wrapper, '0-0')).toBe('問1')
    expect(cellText(wrapper, '0-1')).toBe('高')
    expect(cellText(wrapper, '0-2')).toBe('2026-09-21')
    expect(cellText(wrapper, '0-3')).toBe('1ページ目')
    expect(cellText(wrapper, '1-0')).toBe('問2')
    expect(cellText(wrapper, '1-1')).toBe('低')
  })


  it('セルはクリックでは編集に入らず、選ばれるだけ', async () => {
    const { wrapper } = await setup({ attachTo: true })

    await openCreateDialog(wrapper)

    // 入力欄はどこにも出ていない（値はテキストとして出る）
    expect(wrapper.findAll('.todo-grid td[data-col] input').length).toBe(0)
    expect(wrapper.findAll('.todo-grid td[data-col] select').length).toBe(0)

    const cell = wrapper.get('[data-cell="0-0"]')
    await cell.trigger('mousedown')
    await cell.trigger('click')
    expect(cell.classes()).toContain('is-selected')
    expect(cell.find('input').exists()).toBe(false)
    expect(cell.attributes('data-editing')).toBe('false')
    // 方向キーを効かせるため、表そのものにフォーカスが移る
    expect(document.activeElement).toBe(wrapper.get('.todo-grid').element)
    wrapper.unmount()
  })

  it('ダブルクリックで編集状態になり、そのセルが選ばれる', async () => {
    const { wrapper } = await setup({ attachTo: true })

    await openCreateDialog(wrapper)
    const input = await editCell(wrapper, '0-0')

    expect(wrapper.get('[data-cell="0-0"]').attributes('data-editing')).toBe('true')
    expect(wrapper.get('[data-cell="0-0"]').classes()).toContain('is-selected')
    expect(document.activeElement).toBe(input.element)
    // 入力するとその場でモデルに入り、他のセルをクリックすると確定する
    await input.setValue('問1〜3')
    expect(wrapper.get('[data-cell="0-0"]').find('input').exists()).toBe(true)
    await input.trigger('blur')
    await flushPromises()
    expect(wrapper.get('[data-cell="0-0"]').find('input').exists()).toBe(false)
    expect(cellText(wrapper, '0-0')).toBe('問1〜3')
    wrapper.unmount()
  })

  it('F2 で選択中のセルを編集できる（優先度は選択欄）', async () => {
    const { wrapper } = await setup({ attachTo: true })

    await openCreateDialog(wrapper)
    await wrapper.get('[data-cell="0-1"]').trigger('mousedown')
    await wrapper.get('.todo-grid').trigger('keydown', { key: 'F2' })
    await flushPromises()

    const select = wrapper.get('[data-cell="0-1"] select')
    expect(document.activeElement).toBe(select.element)
    await select.setValue('HIGH')
    await select.trigger('blur')
    await flushPromises()
    expect(cellText(wrapper, '0-1')).toBe('高')
    wrapper.unmount()
  })

  it('Enter で確定して下のセルへ移る', async () => {
    const { wrapper } = await setup()

    await openCreateDialog(wrapper)
    // 2 行にしておく（Enter は 1 つ下へ移る）
    const addButton = wrapper.findAll('.dialog__body .btn').find((button) => button.text().includes('行追加'))
    await addButton?.trigger('click')

    const input = await editCell(wrapper, '0-0')
    await input.setValue('問1')
    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()

    expect(cellText(wrapper, '0-0')).toBe('問1')
    expect(wrapper.get('[data-cell="0-0"]').find('input').exists()).toBe(false)
    expect(wrapper.get('[data-cell="1-0"]').classes()).toContain('is-selected')
  })

  it('Esc で編集を取り消すと元の値に戻る', async () => {
    const { wrapper } = await setup()

    await openCreateDialog(wrapper)
    const input = await editCell(wrapper, '0-0')
    await input.setValue('問1')
    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()

    const again = await editCell(wrapper, '0-0')
    await again.setValue('書きかけ')
    await again.trigger('keydown', { key: 'Escape' })
    await flushPromises()

    expect(cellText(wrapper, '0-0')).toBe('問1')
    expect(wrapper.get('[data-cell="0-0"]').find('input').exists()).toBe(false)
  })

  it('方向キーで選択中のセルが動き、端では止まる', async () => {
    const { wrapper } = await setup()

    await openCreateDialog(wrapper)
    const addButton = wrapper.findAll('.dialog__body .btn').find((button) => button.text().includes('行追加'))
    await addButton?.trigger('click')
    await addButton?.trigger('click')   // 3 行

    const grid = wrapper.get('.todo-grid')
    const selected = (): string[] =>
      wrapper.findAll('.todo-grid td.is-selected').map((td) => td.attributes('data-cell') ?? '')

    await wrapper.get('[data-cell="0-0"]').trigger('mousedown')
    await wrapper.get('[data-cell="0-0"]').trigger('click')
    expect(selected()).toEqual(['0-0'])

    await grid.trigger('keydown', { key: 'ArrowRight' })
    expect(selected()).toEqual(['0-1'])
    await grid.trigger('keydown', { key: 'ArrowDown' })
    expect(selected()).toEqual(['1-1'])
    await grid.trigger('keydown', { key: 'ArrowLeft' })
    expect(selected()).toEqual(['1-0'])
    await grid.trigger('keydown', { key: 'ArrowUp' })
    expect(selected()).toEqual(['0-0'])

    // 端では動かない（上・左）
    await grid.trigger('keydown', { key: 'ArrowUp' })
    await grid.trigger('keydown', { key: 'ArrowLeft' })
    expect(selected()).toEqual(['0-0'])
    // 端では動かない（下・右）
    await wrapper.get('[data-cell="2-3"]').trigger('mousedown')
    await grid.trigger('keydown', { key: 'ArrowDown' })
    await grid.trigger('keydown', { key: 'ArrowRight' })
    expect(selected()).toEqual(['2-3'])
  })

  it('何も選んでいないときに方向キーを押すと、左上のセルが選ばれる', async () => {
    const { wrapper } = await setup()

    await openCreateDialog(wrapper)
    await wrapper.get('.todo-grid').trigger('keydown', { key: 'ArrowDown' })

    expect(wrapper.get('[data-cell="0-0"]').classes()).toContain('is-selected')
  })

  it('上部のフォームは、タイトルとメモが 1 行まるごと・優先度と期限が同じ行', async () => {
    const { wrapper } = await setup()

    await openCreateDialog(wrapper)
    const fields = wrapper.findAll('.todo-form .todo-field')

    expect(fields.length).toBe(4)
    expect(fields[0].classes()).toContain('todo-field--full')   // タイトル
    expect(fields[1].classes()).toContain('todo-field--full')   // メモ
    expect(fields[2].classes()).not.toContain('todo-field--full') // 優先度
    expect(fields[3].classes()).not.toContain('todo-field--full') // 期限
    expect(fields[2].text()).toContain('優先度')
    expect(fields[3].text()).toContain('期限')
  })

  it('保存で親タスクと子タスクをまとめて送る', async () => {
    const { wrapper, fetchMock } = await setup()

    await openCreateDialog(wrapper)

    await wrapper.get('#todoTitle').setValue('英語の宿題')
    await (await editCell(wrapper, '0-0')).setValue('ワーク p.10')
    await wrapper.get('.dialog__foot .btn--primary').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).find((entry) => entry.method === 'POST' && entry.url === '/api/user/todos')
    expect(call?.body).toMatchObject({
      title: '英語の宿題',
      priority: 'NORMAL',
      children: [{ title: 'ワーク p.10', priority: 'NORMAL' }]
    })
  })

  it('保護者には「お子さまの TODO にも登録する」が出て、alsoForStudent を送る', async () => {
    const { wrapper, fetchMock } = await setup({ role: 'GUARDIAN' })

    await wrapper.get('.todo-head .btn--primary').trigger('click')
    await flushPromises()

    const checkbox = wrapper.findAll('input[type="checkbox"]').find((input) =>
      input.element.parentElement?.textContent?.includes('お子さま'))
    expect(checkbox).toBeTruthy()
    await checkbox?.setValue(true)

    await wrapper.get('#todoTitle').setValue('塾の準備')
    await wrapper.get('.dialog__foot .btn--primary').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).find((entry) => entry.method === 'POST' && entry.url === '/api/user/todos')
    expect(call?.body).toMatchObject({ title: '塾の準備', alsoForStudent: true })
  })

  it('生徒には「お子さま…」を出さない', async () => {
    const { wrapper } = await setup({ role: 'STUDENT' })

    await wrapper.get('.todo-head .btn--primary').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('お子さまの TODO にも登録する')
  })
})
