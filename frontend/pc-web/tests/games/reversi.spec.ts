import { afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import ReversiView from '@/views/game/ReversiView.vue'
import {
  REVERSI_SIZE, applyMove, chooseCpuMove, countDiscs, createReversi, flipsFor, isGameOver,
  legalMoves, otherDisc, type Disc
} from '@/features/game/reversi'

/** 画面のテストではルーターを差し替える（ページの階層判定だけを使うため）。 */
vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/student/game/reversi' })
}))

/** 決まった順番で数を返す擬似乱数（テストを再現可能にする）。 */
function seededRng(seed: number): () => number {
  let value = seed
  return () => {
    value = (value * 1664525 + 1013904223) % 4294967296
    return value / 4294967296
  }
}

/** 指定したマスだけに石を置いたテスト用の盤面を作る（他は空き）。 */
function boardWith(discs: Record<number, Disc>): (Disc | null)[] {
  const cells: (Disc | null)[] = Array.from({ length: REVERSI_SIZE * REVERSI_SIZE }, () => null)
  for (const [index, disc] of Object.entries(discs)) cells[Number(index)] = disc
  return cells
}

/** 中央（5行5列 = 36）を空きにし、周囲 8 マスを白・その外側 8 マスを黒で囲んだ盤面。 */
function ringBoard(): (Disc | null)[] {
  return boardWith({
    18: 'black', 20: 'black', 22: 'black',
    34: 'black', 38: 'black',
    50: 'black', 52: 'black', 54: 'black',
    27: 'white', 28: 'white', 29: 'white',
    35: 'white', 37: 'white',
    43: 'white', 44: 'white', 45: 'white'
  })
}

/** 初期盤面（8×8）で黒が置ける 4 箇所。 */
const INITIAL_BLACK_MOVES = [19, 26, 37, 44]
/** 初期盤面（8×8）で白が置ける 4 箇所。 */
const INITIAL_WHITE_MOVES = [20, 29, 34, 43]

describe('リバーシ：盤面の生成', () => {
  it('中央 4 マスに白黒 2 個ずつ置き、残りは空きにする', () => {
    const cells = createReversi()
    expect(cells).toHaveLength(REVERSI_SIZE * REVERSI_SIZE)
    expect(cells[27]).toBe('white')
    expect(cells[28]).toBe('black')
    expect(cells[35]).toBe('black')
    expect(cells[36]).toBe('white')
    expect(countDiscs(cells)).toEqual({ black: 2, white: 2 })
    expect(cells.filter((disc) => disc === null)).toHaveLength(60)
  })

  it('サイズを変えても中央 4 マスに配置する', () => {
    const cells = createReversi(4)
    expect(cells).toHaveLength(16)
    expect(cells[5]).toBe('white')
    expect(cells[6]).toBe('black')
    expect(cells[9]).toBe('black')
    expect(cells[10]).toBe('white')
  })

  it('2×2 未満のサイズは例外にする', () => {
    expect(() => createReversi(1)).toThrow()
    expect(() => createReversi(0)).toThrow()
  })

  it('反対の色を返す', () => {
    expect(otherDisc('black')).toBe('white')
    expect(otherDisc('white')).toBe('black')
  })
})

describe('リバーシ：置けるマスの判定', () => {
  it('初期盤面で黒は 4 箇所に置ける', () => {
    expect(legalMoves(createReversi(), REVERSI_SIZE, 'black')).toEqual(INITIAL_BLACK_MOVES)
  })

  it('初期盤面で白は 4 箇所に置ける', () => {
    expect(legalMoves(createReversi(), REVERSI_SIZE, 'white')).toEqual(INITIAL_WHITE_MOVES)
  })

  it('石が無い盤面では誰も置けず終局扱いになる', () => {
    const cells: (Disc | null)[] = Array.from({ length: 64 }, () => null)
    expect(legalMoves(cells, REVERSI_SIZE, 'black')).toEqual([])
    expect(legalMoves(cells, REVERSI_SIZE, 'white')).toEqual([])
    expect(isGameOver(cells, REVERSI_SIZE)).toBe(true)
  })

  it('挟んだ先が自分の石でなければ置けない', () => {
    // 3行4列（19）の右隣は白だが、その先の 5行4列は空きなので挟めない
    const cells = boardWith({ 27: 'white' })
    expect(flipsFor(cells, REVERSI_SIZE, 19, 'black')).toEqual([])
    expect(legalMoves(cells, REVERSI_SIZE, 'black')).not.toContain(19)
  })

  it('挟んだ先が盤外なら置けない', () => {
    // 5行6〜8列（37〜39）が白で、その先は盤外
    const cells = boardWith({ 37: 'white', 38: 'white', 39: 'white' })
    expect(flipsFor(cells, REVERSI_SIZE, 36, 'black')).toEqual([])
    expect(legalMoves(cells, REVERSI_SIZE, 'black')).not.toContain(36)
  })

  it('石が置かれているマスには置けない', () => {
    const cells = createReversi()
    expect(flipsFor(cells, REVERSI_SIZE, 27, 'black')).toEqual([])
    expect(legalMoves(cells, REVERSI_SIZE, 'black')).not.toContain(27)
  })

  it('盤外の位置は空配列にする', () => {
    const cells = createReversi()
    expect(flipsFor(cells, REVERSI_SIZE, -1, 'black')).toEqual([])
    expect(flipsFor(cells, REVERSI_SIZE, 64, 'black')).toEqual([])
  })
})

describe('リバーシ：裏返しの計算', () => {
  it('初期盤面の黒の手は 1 枚だけ裏返す', () => {
    const cells = createReversi()
    expect(flipsFor(cells, REVERSI_SIZE, 19, 'black')).toEqual([27])
    expect(flipsFor(cells, REVERSI_SIZE, 26, 'black')).toEqual([27])
    expect(flipsFor(cells, REVERSI_SIZE, 37, 'black')).toEqual([36])
    expect(flipsFor(cells, REVERSI_SIZE, 44, 'black')).toEqual([36])
  })

  it('初期盤面の白の手は 1 枚だけ裏返す', () => {
    const cells = createReversi()
    expect(flipsFor(cells, REVERSI_SIZE, 20, 'white')).toEqual([28])
    expect(flipsFor(cells, REVERSI_SIZE, 29, 'white')).toEqual([28])
    expect(flipsFor(cells, REVERSI_SIZE, 34, 'white')).toEqual([35])
    expect(flipsFor(cells, REVERSI_SIZE, 43, 'white')).toEqual([35])
  })

  it('8 方向すべての裏返しを同時に扱える', () => {
    const cells = ringBoard()
    const flips = flipsFor(cells, REVERSI_SIZE, 36, 'black')
    expect(flips.slice().sort((a, b) => a - b)).toEqual([27, 28, 29, 35, 37, 43, 44, 45])

    const applied = applyMove(cells, REVERSI_SIZE, 36, 'black')
    expect(applied.slice().sort((a, b) => a - b)).toEqual([27, 28, 29, 35, 37, 43, 44, 45])
    expect(cells[36]).toBe('black')
    for (const position of applied) expect(cells[position]).toBe('black')
    // 外側の黒は裏返らない
    expect(cells[18]).toBe('black')
    expect(cells[54]).toBe('black')
  })

  it('白石が続いていても、その先が自分の石でなければ裏返せない', () => {
    // 4行4列（27）から右へ白が 3 枚続くが、その先（4行8列）は空き
    const cells = boardWith({ 28: 'white', 29: 'white', 30: 'white' })
    expect(flipsFor(cells, REVERSI_SIZE, 27, 'black')).toEqual([])
  })
})

describe('リバーシ：石を置く', () => {
  it('置いた石と裏返した石が更新される', () => {
    const cells = createReversi()
    const flips = applyMove(cells, REVERSI_SIZE, 19, 'black')
    expect(flips).toEqual([27])
    expect(cells[19]).toBe('black')
    expect(cells[27]).toBe('black')
    expect(countDiscs(cells)).toEqual({ black: 4, white: 1 })
  })

  it('置けない手では盤面を一切変更しない', () => {
    const cells = createReversi()
    const before = cells.slice()
    expect(applyMove(cells, REVERSI_SIZE, 0, 'black')).toEqual([])
    expect(cells).toEqual(before)
    expect(applyMove(cells, REVERSI_SIZE, 27, 'black')).toEqual([])
    expect(cells).toEqual(before)
  })

  it('石数を数えるときは空きマスを数えない', () => {
    expect(countDiscs(boardWith({ 0: 'black', 1: 'black', 2: 'white' }))).toEqual({
      black: 2,
      white: 1
    })
    expect(countDiscs([])).toEqual({ black: 0, white: 0 })
  })
})

describe('リバーシ：終局の判定', () => {
  it('初期盤面は終局していない', () => {
    expect(isGameOver(createReversi(), REVERSI_SIZE)).toBe(false)
  })

  it('盤面が埋まったら終局する', () => {
    const cells: (Disc | null)[] = Array.from({ length: 64 }, (_, index) =>
      index % 2 === 0 ? 'black' : 'white'
    )
    expect(isGameOver(cells, REVERSI_SIZE)).toBe(true)
  })

  it('片方だけが打てる場合は終局しない', () => {
    // 1行1列（0）だけが空きで、そこは黒しか置けない局面
    const cells: (Disc | null)[] = Array.from({ length: 64 }, () => 'white')
    cells[0] = null
    cells[2] = 'black'
    expect(legalMoves(cells, REVERSI_SIZE, 'black')).toEqual([0])
    expect(legalMoves(cells, REVERSI_SIZE, 'white')).toEqual([])
    expect(isGameOver(cells, REVERSI_SIZE)).toBe(false)
  })

  it('CPU 同士の対局が必ず終局する', () => {
    const cells = createReversi()
    let disc: Disc = 'black'
    let passes = 0
    let moves = 0

    while (moves < 200) {
      const move = chooseCpuMove(cells, REVERSI_SIZE, disc, seededRng(moves + 1))
      if (move < 0) {
        passes += 1
        disc = otherDisc(disc)
        if (passes >= 2) break
        continue
      }
      passes = 0
      expect(applyMove(cells, REVERSI_SIZE, move, disc).length).toBeGreaterThan(0)
      moves += 1
      disc = otherDisc(disc)
    }

    expect(moves).toBeLessThan(200)
    expect(isGameOver(cells, REVERSI_SIZE)).toBe(true)
    const result = countDiscs(cells)
    expect(result.black + result.white).toBeGreaterThan(4)
  })
})

describe('リバーシ：CPU の手の選択', () => {
  it('打てる手が無ければ -1 を返す', () => {
    const cells: (Disc | null)[] = Array.from({ length: 64 }, () => null)
    expect(chooseCpuMove(cells, REVERSI_SIZE, 'black')).toBe(-1)
    expect(chooseCpuMove(cells, REVERSI_SIZE, 'white')).toBe(-1)
  })

  it('角が取れるなら角を選ぶ', () => {
    // 1行1列（0）は 1 枚、4行2列（25）は 2 枚裏返せるが、角を優先する
    const cells = boardWith({ 1: 'white', 2: 'black', 26: 'white', 27: 'white', 28: 'black' })
    expect(legalMoves(cells, REVERSI_SIZE, 'black')).toEqual([0, 25])
    expect(flipsFor(cells, REVERSI_SIZE, 25, 'black')).toEqual([26, 27])
    expect(chooseCpuMove(cells, REVERSI_SIZE, 'black', seededRng(1))).toBe(0)
  })

  it('角の斜め隣（X 打ち）を避ける', () => {
    // 2行2列（9）は 1行1列の X 打ち、6行2列（41）は角から離れている。裏返せる数は同じ
    const cells = boardWith({ 10: 'white', 11: 'black', 42: 'white', 43: 'black' })
    expect(legalMoves(cells, REVERSI_SIZE, 'black')).toEqual([9, 41])
    expect(flipsFor(cells, REVERSI_SIZE, 9, 'black')).toHaveLength(1)
    expect(chooseCpuMove(cells, REVERSI_SIZE, 'black', seededRng(1))).toBe(41)
  })

  it('裏返せる数が多い手を選ぶ', () => {
    // 4行3列（26）は 1 枚、6行5列（44）は 3 枚裏返せる
    const cells = boardWith({ 24: 'black', 25: 'white', 40: 'black', 41: 'white', 42: 'white', 43: 'white' })
    expect(legalMoves(cells, REVERSI_SIZE, 'black')).toEqual([26, 44])
    expect(flipsFor(cells, REVERSI_SIZE, 26, 'black')).toEqual([25])
    expect(flipsFor(cells, REVERSI_SIZE, 44, 'black')).toEqual([43, 42, 41])
    expect(chooseCpuMove(cells, REVERSI_SIZE, 'black', seededRng(1))).toBe(44)
  })

  it('同点の手は乱数で選ぶ（同じ乱数なら同じ手）', () => {
    const cells = createReversi()
    // 初期盤面の黒の 4 手はすべて 1 枚裏返しで評価が同じ
    expect(chooseCpuMove(cells, REVERSI_SIZE, 'black', () => 0)).toBe(INITIAL_BLACK_MOVES[0])
    expect(chooseCpuMove(cells, REVERSI_SIZE, 'black', () => 0.5)).toBe(INITIAL_BLACK_MOVES[2])
    expect(chooseCpuMove(cells, REVERSI_SIZE, 'black', () => 0.99)).toBe(INITIAL_BLACK_MOVES[3])
    // 乱数が 1 を返しても範囲外にならない
    expect(INITIAL_BLACK_MOVES).toContain(chooseCpuMove(cells, REVERSI_SIZE, 'black', () => 1))
  })
})

describe('リバーシ：画面（ReversiView）', () => {
  /** マウントした画面はテストごとに破棄する（タイマーを残さない）。 */
  const mountedViews: { unmount: () => void }[] = []

  afterEach(() => {
    while (mountedViews.length > 0) mountedViews.pop()?.unmount()
  })

  function mountView(attach = false) {
    const wrapper = mount(ReversiView, {
      attachTo: attach ? document.body : undefined,
      global: { stubs: { RouterLink: true } }
    })
    mountedViews.push(wrapper)
    return wrapper
  }

  /** 石数を [黒, 白, 手番, 経過時間] の表示から読む。 */
  function metrics(wrapper: ReturnType<typeof mountView>): string[] {
    return wrapper.findAll('.gm-metric__value').map((node) => node.text())
  }

  /** 同じ画面で 2 人打つモードにする（既定は CPU なので、明示的に切り替えてから始める）。 */
  async function startSameScreenGame(target: ReturnType<typeof mountView>): Promise<void> {
    await target.get('[data-solo-mode]').setValue('duo')
    await clickNewGame(target)
  }

  /** 【新しい対局】ボタンを押す（モードの変更が反映されるのはここだけ）。 */
  async function clickNewGame(target: ReturnType<typeof mountView>): Promise<void> {
    const button = target.findAll('.gm-actions .btn').find((item) => item.text().includes('新しい対局'))
    expect(button).toBeTruthy()
    await button!.trigger('click')
  }

  it('8×8 の盤面と初期の石・ヒントを描画する', () => {
    const wrapper = mountView()
    const cells = wrapper.findAll('.gm-rev-cell')
    expect(cells).toHaveLength(64)
    expect(wrapper.findAll('.gm-rev-disc--black')).toHaveLength(2)
    expect(wrapper.findAll('.gm-rev-disc--white')).toHaveLength(2)
    // 初期盤面では黒の 4 箇所が合法手（ヒント ON なので印が出る）
    expect(wrapper.findAll('.gm-rev-cell.is-legal')).toHaveLength(4)
    expect(wrapper.findAll('.gm-rev-hint')).toHaveLength(4)
    // a11y：盤面とマスのラベル
    expect(wrapper.find('.gm-rev').attributes('aria-label')).toBe('リバーシの盤面 8行8列')
    expect(cells[19].attributes('aria-label')).toBe('3行4列 空き 打てます')
    expect(cells[27].attributes('aria-label')).toBe('4行4列 白石')
    expect(cells[0].attributes('aria-label')).toBe('1行1列 空き')
    expect(wrapper.find('.gm-status').classes()).toContain('is-play')
    expect(metrics(wrapper)[2]).toBe('黒')
  })

  it('盤面のマスの大きさは --gm-cell で指定する（64px）', () => {
    const wrapper = mountView()
    const board = wrapper.find('.gm-rev').element as HTMLElement
    expect(board.style.getPropertyValue('--gm-cell').trim()).toBe('64px')
  })

  it('合法手をクリックすると石を置き、挟んだ石を裏返す', async () => {
    const wrapper = mountView()
    await wrapper.findAll('.gm-rev-cell')[19].trigger('click')
    expect(wrapper.findAll('.gm-rev-disc--black')).toHaveLength(4)
    expect(wrapper.findAll('.gm-rev-disc--white')).toHaveLength(1)
    expect(metrics(wrapper)).toEqual(['4', '1', '白', '00:00'])
    // 裏返った石にはアニメーション用のクラスが付く
    const flipped = wrapper.findAll('.gm-rev-cell')[27].find('.gm-rev-disc')
    expect(flipped.classes()).toContain('gm-rev-disc--black')
    expect(flipped.classes()).toContain('is-flipping')
  })

  it('合法手でないマスをクリックしても何も起きない', async () => {
    const wrapper = mountView()
    await wrapper.findAll('.gm-rev-cell')[0].trigger('click')
    // 相手の石のマスをクリックしても置けない
    await wrapper.findAll('.gm-rev-cell')[27].trigger('click')
    expect(wrapper.findAll('.gm-rev-disc')).toHaveLength(4)
    expect(metrics(wrapper)[2]).toBe('黒')
  })

  it('ヒント表示を OFF にすると印は消えるが、合法手は置ける', async () => {
    const wrapper = mountView()
    await wrapper.findAll('.gm-actions .btn')[2].trigger('click')
    expect(wrapper.findAll('.gm-rev-cell.is-legal')).toHaveLength(0)
    expect(wrapper.findAll('.gm-rev-hint')).toHaveLength(0)
    await wrapper.findAll('.gm-rev-cell')[19].trigger('click')
    expect(wrapper.findAll('.gm-rev-disc--black')).toHaveLength(4)
  })

  it('「待った」で直前の 1 手を戻す', async () => {
    const wrapper = mountView()
    await wrapper.findAll('.gm-rev-cell')[19].trigger('click')
    await wrapper.findAll('.gm-actions .btn')[1].trigger('click')
    expect(wrapper.findAll('.gm-rev-disc')).toHaveLength(4)
    expect(wrapper.findAll('.gm-rev-cell')[19].find('.gm-rev-disc').exists()).toBe(false)
    expect(wrapper.findAll('.gm-rev-cell')[27].find('.gm-rev-disc--white').exists()).toBe(true)
    expect(metrics(wrapper)[2]).toBe('黒')
  })

  it('「待った」は戻せる手が無いときは盤面を変えない', async () => {
    const wrapper = mountView()
    await wrapper.findAll('.gm-actions .btn')[1].trigger('click')
    expect(wrapper.findAll('.gm-rev-disc')).toHaveLength(4)
  })

  it('モードは CPU が先頭で、既定は CPU（2人対戦はネットと 同じ画面）', async () => {
    const wrapper = mountView()

    const select = wrapper.get('[data-solo-mode]')
    expect(select.findAll('option').map((option) => option.attributes('value'))).toEqual(['cpu', 'match', 'duo'])
    expect((select.element as HTMLSelectElement).value).toBe('cpu')
  })

  it('モードを変えても対局はやり直さず、新しい対局で反映する', async () => {
    const wrapper = mountView()
    await startSameScreenGame(wrapper)
    await wrapper.findAll('.gm-rev-cell')[19].trigger('click')
    const discs = wrapper.findAll('.gm-rev-disc').length
    expect(discs).toBeGreaterThan(4)
    const turnBefore = metrics(wrapper)[2]

    await wrapper.get('[data-solo-mode]').setValue('cpu')

    // 盤面も手番もそのまま
    expect(wrapper.findAll('.gm-rev-disc')).toHaveLength(discs)
    expect(metrics(wrapper)[2]).toBe(turnBefore)

    // モードは変わっていないので、CPU は打たない
    await new Promise((resolve) => setTimeout(resolve, 400))
    await nextTick()
    expect(wrapper.findAll('.gm-rev-disc')).toHaveLength(discs)

    // 【新しい対局】を押したときだけ反映される
    await clickNewGame(wrapper)
    expect(wrapper.findAll('.gm-rev-disc')).toHaveLength(4)

    await wrapper.findAll('.gm-rev-cell')[19].trigger('click')
    await new Promise((resolve) => setTimeout(resolve, 400))
    await nextTick()
    const after = metrics(wrapper)
    expect(Number(after[0]) + Number(after[1])).toBeGreaterThanOrEqual(5)
    expect(after[2]).toBe('黒')
  })

  it('CPU モードでは黒の着手後に白が自動で打つ', async () => {
    const wrapper = mountView()
    await wrapper.get('[data-solo-mode]').setValue('cpu')
    await clickNewGame(wrapper)
    expect(wrapper.findAll('.gm-rev-disc')).toHaveLength(4)

    await wrapper.findAll('.gm-rev-cell')[19].trigger('click')
    expect(metrics(wrapper)[1]).toBe('1')

    // CPU は 250ms 待ってから打つ
    await new Promise((resolve) => setTimeout(resolve, 400))
    await nextTick()

    const after = metrics(wrapper)
    expect(Number(after[0]) + Number(after[1])).toBeGreaterThanOrEqual(5)
    expect(after[2]).toBe('黒')
  })

  it('矢印キーでマス間を移動できる', async () => {
    const wrapper = mountView(true)
    const cells = wrapper.findAll('.gm-rev-cell')
    await cells[19].trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(cells[27].element)
    await cells[27].trigger('keydown', { key: 'ArrowRight' })
    expect(document.activeElement).toBe(cells[28].element)
    // 盤外へは移動しない
    await cells[28].trigger('keydown', { key: 'ArrowUp' })
    expect(document.activeElement).toBe(cells[20].element)
    // 1行目まで来たら、さらに上へは移動しない
    await cells[20].trigger('keydown', { key: 'ArrowUp' })
    expect(document.activeElement).toBe(cells[12].element)
    await cells[12].trigger('keydown', { key: 'ArrowUp' })
    expect(document.activeElement).toBe(cells[4].element)
    await cells[4].trigger('keydown', { key: 'ArrowUp' })
    expect(document.activeElement).toBe(cells[4].element)
  })
})
