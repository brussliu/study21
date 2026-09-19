import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'
import { DEMO_WORDS } from '@/features/japanese-demo/mock/demoWords'

/**
 * 日本語勉強【単語情報管理】デモの状態。
 *
 * デモの約束（本番 API を呼ばない・仮データだけ・リセットで戻る）を機械で見張る。
 * fetch を握りつぶして、**1 度も呼ばれない**ことも確かめる。
 */

function useStore() {
  setActivePinia(createPinia())
  return useJapaneseDemoStore()
}

describe('単語情報管理デモ：ストア', () => {
  let fetchSpy: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchSpy = vi.fn(() => {
      throw new Error('デモで fetch を呼んではいけません')
    })
    vi.stubGlobal('fetch', fetchSpy)
    vi.useRealTimers()
  })

  it('仮データを読み込み、本番 API を一度も呼ばない', () => {
    const store = useStore()

    expect(store.words.length).toBe(DEMO_WORDS.length)
    expect(store.books.length).toBe(3)
    // 新規登録は既定の書籍が選ばれている（未選択のまま行き止まりにしない）
    expect(store.registerBookName).toBe(store.bookNameOptions[0])
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('絞り込み・ページ・スクロール位置を保持する（詳細から戻っても同じ見え方）', () => {
    const store = useStore()
    const total = store.pageResult.total

    store.setFilter('keyword', '図書館')
    expect(store.pageResult.total).toBeLessThan(total)
    expect(store.listPage).toBe(1)

    store.rememberScroll(420)
    expect(store.listScrollTop).toBe(420)

    // 絞り込みは状態として残る（画面を離れても消えない）
    expect(store.filters.keyword).toBe('図書館')

    store.resetFilters()
    expect(store.pageResult.total).toBe(total)
    expect(store.filters.keyword).toBe('')
  })

  it('デモ表示設定で一覧の状態を切り替えられる（該当なしは空になる）', () => {
    const store = useStore()

    store.setDisplay({ listMode: 'NO_RESULT' })
    expect(store.pageWords.length).toBe(0)

    store.setDisplay({ listMode: 'NOT_GENERATED' })
    expect(store.pageWords.every((word) => word.detailStatus === 'NOT_GENERATED')).toBe(true)

    store.setDisplay({ listMode: 'NORMAL' })
    expect(store.pageWords.length).toBeGreaterThan(0)
  })

  it('詳細の生成は遅れて入り、完了すると生成済みになる（通信はしない）', async () => {
    vi.useFakeTimers()
    const store = useStore()
    const target = store.words.find((word) => word.detailStatus === 'NOT_GENERATED')
    expect(target, '未生成の語が仮データに必要').toBeDefined()
    const id = target!.id

    store.startGeneration(id)
    expect(store.findWord(id)?.detailStatus).toBe('RUNNING')

    await vi.advanceTimersByTimeAsync(2000)
    expect(store.findWord(id)?.detailStatus).toBe('GENERATED')
    expect(store.findWord(id)?.detail).not.toBeNull()
    expect(fetchSpy).not.toHaveBeenCalled()
    vi.useRealTimers()
  })

  it('生成中はデモ表示設定の上書きより優先して「生成中」に見える', () => {
    vi.useFakeTimers()
    const store = useStore()
    store.setDisplay({ listMode: 'NOT_GENERATED' })
    const target = store.words.find((word) => word.detailStatus === 'NOT_GENERATED')!

    store.startGeneration(target.id)

    const row = store.visibleWords.find((word) => word.id === target.id)
    expect(row?.detailStatus, '生成の途中経過を上書きで隠さない').toBe('RUNNING')

    // 生成が終われば、表示設定の上書き（未生成）に戻る
    return vi.advanceTimersByTimeAsync(2000).then(() => {
      const settled = store.visibleWords.find((word) => word.id === target.id)
      expect(settled?.detailStatus).toBe('NOT_GENERATED')
    })
    vi.useRealTimers()
  })

  it('生成中にリセットすると、古い結果があとから入らない', async () => {
    vi.useFakeTimers()
    const store = useStore()
    const target = store.words.find((word) => word.detailStatus === 'NOT_GENERATED')!
    // 先に初期状態を控える（リセットで配列ごと作り直されるため）
    const initial = { status: target.detailStatus, detail: target.detail }
    store.startGeneration(target.id)
    expect(store.findWord(target.id)?.detailStatus).toBe('RUNNING')

    // 画面を離れた／リセットした扱い
    store.resetAll()
    await vi.advanceTimersByTimeAsync(3000)

    // 元の仮データのまま（生成結果が混ざらない）
    expect(store.findWord(target.id)?.detailStatus).toBe(initial.status)
    expect(store.findWord(target.id)?.detail).toBe(initial.detail)
    vi.useRealTimers()
  })

  it('保存はデモデータだけを更新し、失敗・衝突の状態も再現できる', async () => {
    vi.useFakeTimers()
    const store = useStore()
    const target = store.words.find((word) => word.detailStatus === 'GENERATED')!

    // 正常
    expect(store.openEditor(target.id)).toBe(true)
    store.updateDraftBasic({ chineseMeaning: '変更後の意味' })
    expect(store.draftDirty).toBe(true)
    const saving = store.saveDraft()
    await vi.advanceTimersByTimeAsync(1000)
    expect(await saving).toBe(true)
    expect(store.saveState).toBe('SAVED')
    expect(store.findWord(target.id)?.chineseMeaning).toBe('変更後の意味')
    expect(store.findWord(target.id)?.detailStatus).toBe('EDITED')

    // 保存失敗（入力は残る）
    store.setDisplay({ saveMode: 'SAVE_FAILED' })
    store.updateDraftBasic({ chineseMeaning: '失敗する変更' })
    const failed = store.saveDraft()
    await vi.advanceTimersByTimeAsync(1000)
    expect(await failed).toBe(false)
    expect(store.saveState).toBe('FAILED')
    expect(store.draft?.chineseMeaning).toBe('失敗する変更')
    expect(store.findWord(target.id)?.chineseMeaning).toBe('変更後の意味')

    // 衝突（入力は残す）
    store.setDisplay({ saveMode: 'CONFLICT' })
    const conflicted = store.saveDraft()
    await vi.advanceTimersByTimeAsync(1000)
    expect(await conflicted).toBe(false)
    expect(store.saveState).toBe('CONFLICT')
    expect(store.draft?.chineseMeaning).toBe('失敗する変更')
    vi.useRealTimers()
  })

  it('配列の項目を足す・並べ替える・消せる（語義の例）', () => {
    const store = useStore()
    const target = store.words.find((word) => (word.detail?.senses.length ?? 0) >= 2)!
    store.openEditor(target.id)

    const before = store.draft!.detail!.senses.length
    store.addListItem('senses', { id: 'new-sense', number: before + 1, japanese: '追加', chinese: '', context: '', style: '' })
    expect(store.draft!.detail!.senses.length).toBe(before + 1)

    store.moveListItem('senses', before, -1)
    expect(store.draft!.detail!.senses[before - 1]?.id).toBe('new-sense')

    store.removeListItem('senses', before - 1)
    expect(store.draft!.detail!.senses.length).toBe(before)
    expect(store.draftDirty).toBe(true)
  })

  it('削除は一覧から消すだけで、失敗の再現もできる', async () => {
    vi.useFakeTimers()
    const store = useStore()
    const target = store.words[0]!
    const count = store.words.length

    store.setDisplay({ saveMode: 'SAVE_FAILED' })
    store.openDelete(target.id)
    const failed = store.confirmDelete()
    await vi.advanceTimersByTimeAsync(1000)
    expect(await failed).toBe(false)
    expect(store.deleteState).toBe('FAILED')
    expect(store.words.length).toBe(count)

    store.setDisplay({ saveMode: 'NORMAL' })
    const deleted = store.confirmDelete()
    await vi.advanceTimersByTimeAsync(1000)
    expect(await deleted).toBe(true)
    expect(store.words.length).toBe(count - 1)
    expect(store.findWord(target.id)).toBeNull()
    vi.useRealTimers()
  })

  it('新規登録は入力順に Unit を割り当て、既存語には収録だけ足す', async () => {
    vi.useFakeTimers()
    const store = useStore()
    const existing = store.words[0]!

    // 既存語 1 語＋新しい語 2 語。既存語は「収録を追加」になる
    store.pasteText = [
      `${existing.heading}\t${existing.reading}\t${existing.chineseMeaning}`,
      'デモ新語一\tでもしんごいち\t演示新词一',
      'デモ新語二\tでもしんごに\t演示新词二'
    ].join('\n')
    store.parseRegisterText()

    expect(store.registerSummary.existingReuse).toBe(1)
    expect(store.registerCounts.reuse).toBe(1)
    expect(store.registerCounts.fresh).toBe(2)

    store.registerBookMode = 'NEW'
    store.registerBookName = 'デモ日本語 初中級'
    store.registerUnitSize = 2
    store.gotoRegisterStep(3)

    expect(store.allocation.startUnit).toBe('Unit001')
    expect(store.allocation.summaries.map((summary) => [summary.unit, summary.count])).toEqual([
      ['Unit001', 2],
      ['Unit002', 1]
    ])

    const wordsBefore = store.words.length
    const collectionsBefore = existing.collections.length
    const saving = store.saveRegistration()
    await vi.advanceTimersByTimeAsync(1000)
    expect(await saving).toBe(true)

    // 既存語は増えず、収録だけ増える
    expect(store.words.length).toBe(wordsBefore + 2)
    expect(store.findWord(existing.id)?.collections.length).toBe(collectionsBefore + 1)
    expect(fetchSpy).not.toHaveBeenCalled()
    vi.useRealTimers()
  })

  it('同じ見出し語でも読みが違えば別の単語、同じ見出し語＋読みなら収録の追加', () => {
    const store = useStore()
    const existing = store.words.find((word) => word.heading === '図書館')!

    // 見出し語は同じで読みが違う → 別の単語として取り込む
    store.pasteText = '図書館\tとしょかん２\t图书馆（別読み）'
    store.parseRegisterText()
    expect(store.parsedRows[0]?.state).toBe('OK')
    expect(store.registerCounts.reuse).toBe(0)
    expect(store.registerCounts.fresh).toBe(1)

    // 同じ見出し語＋同じ読み → 語は増やさず「収録を追加」にする
    store.pasteText = `図書館\t${existing.reading}\t图书馆`
    store.parseRegisterText()
    expect(store.parsedRows[0]?.state).toBe('DUPLICATE_EXISTING')
    expect(store.registerCounts.reuse).toBe(1)
    expect(store.registerCounts.fresh).toBe(0)
  })

  it('デモをリセットすると、初期の仮データに戻る', async () => {
    vi.useFakeTimers()
    const store = useStore()
    // リセット後は「生成中」を持ち越さないので、比べる基準からも外しておく
    const initialIds = store.words
      .filter((word) => word.detailStatus !== 'RUNNING')
      .map((word) => word.id)
      .sort()

    store.pasteText = 'デモ語\tでもご\t演示词'
    store.parseRegisterText()
    store.registerBookMode = 'NEW'
    store.registerBookName = 'リセット用'
    store.gotoRegisterStep(3)
    const saving = store.saveRegistration()
    await vi.advanceTimersByTimeAsync(1000)
    expect(await saving).toBe(true)
    expect(store.words.length).toBeGreaterThan(initialIds.length)
    expect(store.words.some((word) => word.heading === 'デモ語')).toBe(true)

    store.setFilter('keyword', 'デモ')
    store.rememberScroll(500)
    store.setDisplay({ listMode: 'FAILED', saveMode: 'SAVE_FAILED' })

    store.resetAll()

    // 初期サンプルに戻る（追加した語は消え、仮データの語がそろう）
    expect(store.words.some((word) => word.heading === 'デモ語')).toBe(false)
    expect(store.words.map((word) => word.id).sort()).toEqual(initialIds)

    // 「生成中」は演示の途中経過なので、リセット後は 1 語も残さない
    expect(store.words.every((word) => word.detailStatus !== 'RUNNING')).toBe(true)
    expect(store.filters.keyword).toBe('')
    expect(store.listScrollTop).toBe(0)
    expect(store.display.listMode).toBe('NORMAL')
    expect(store.display.saveMode).toBe('NORMAL')
    vi.useRealTimers()
  })
})
