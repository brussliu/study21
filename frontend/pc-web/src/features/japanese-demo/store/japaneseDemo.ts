/**
 * 日本語勉強【単語情報管理】デモの状態（**メモリ上だけ**）。
 *
 * 重要（デモの約束）:
 * ・本番 API・DB・AI・TTS を一切呼ばない。ここにあるのは `mock/demoWords.ts` の仮データだけ。
 * ・追加・編集・削除・生成はすべてこのストアの中だけで起き、リロードで元に戻る
 *   （「デモをリセット」で初期サンプルへ戻せる）。
 * ・一覧の絞り込み／ページ／スクロール位置は**画面を離れても保持する**（§3）。
 * ・編集中の下書き（draft）と保存済みの内容は別に持ち、混ざらないようにする（§7-4）。
 */

import { computed, reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import { DEMO_BOOKS, DEMO_WORDS } from '../mock/demoWords'
import { readStoredDraft, writeStoredDraft } from './draftStorage'
import type {
  DemoBook,
  DemoDetailCaution,
  DemoDetailCollocation,
  DemoDetailConjugation,
  DemoDetailContent,
  DemoDetailDialog,
  DemoDetailExample,
  DemoDetailPattern,
  DemoDetailRelatedWord,
  DemoDetailSense,
  DemoDetailSynonym,
  DemoDetailUsageNote,
  DemoDisplayState,
  DemoJob,
  DemoPracticeQuestion,
  DemoWord
} from '../types'
import {
  allocateUnits,
  displayFailureReason,
  displayStatus,
  emptyFilters,
  filterWords,
  hasAnyDetail,
  paginate,
  detectUnitSize,
  normalizeHeading,
  parseWords,
  summarizeParsed,
  type AllocationSummary,
  type AllocatedWord,
  type DemoFilters,
  type DuplicateMode,
  type ParsedRow,
  type UnitPlacement
} from '../logic'

/** 疑似 AI の待ち時間（画面に「生成中」を見せるための演出。通信はしない）。 */
const MOCK_DELAY_MS = 1400
/** 保存の待ち時間。 */
const MOCK_SAVE_DELAY_MS = 700

function nextId(prefix: string): string {
  nextId.counter += 1
  return `${prefix}-${nextId.counter}`
}
nextId.counter = 0

/** 深いコピー（下書きが保存済みの内容を書き換えないようにする）。 */
function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

/** 詳細の中身が無いときの空の器。 */
function emptyDetail(): DemoDetailContent {
  return {
    coreMeaning: '',
    descriptionJa: '',
    descriptionZh: '',
    senses: [],
    examples: [],
    patterns: [],
    dialogs: [],
    synonyms: [],
    cautions: [],
    conjugations: [],
    transitivityPair: null,
    pronunciation: null,
    collocations: [],
    relatedWords: [],
    usageNotes: [],
    memoryHint: null,
    practices: []
  }
}

export const useJapaneseDemoStore = defineStore('japaneseDemo', () => {
  /* ---------------------------------------------------------------- */
  /* 仮データ本体（メモリ上。リセットで初期値に戻る）                    */
  /* ---------------------------------------------------------------- */

  const words = ref<DemoWord[]>(clone(DEMO_WORDS))
  const books = ref<DemoBook[]>(clone(DEMO_BOOKS))

  /** デモの非同期処理（生成の模擬）の世代。古い結果で新しい内容を上書きしないために使う。 */
  let jobGeneration = 0
  const runningJobs = ref<DemoJob[]>([])

  /* ---------------------------------------------------------------- */
  /* デモ表示設定（§8。既定はたたんでおく設定フライアウト側で持つ）      */
  /* ---------------------------------------------------------------- */

  /**
   * いま生成中の語（デモの演出）。
   * 表示設定の上書きより**こちらを優先**する（生成中は一覧の状態としても「生成中」）。
   */
  const runningWordIds = computed(() => {
    const ids = new Set<string>()
    for (const job of runningJobs.value) {
      ids.add(job.wordId)
    }
    return ids
  })

  const display = reactive<DemoDisplayState>({
    listMode: 'NORMAL',
    detailMode: 'FULL',
    saveMode: 'NORMAL'
  })

  function setDisplay(next: Partial<DemoDisplayState>): void {
    Object.assign(display, next)
  }

  /**
   * URL のパラメータから表示の状態を決める（画面には出さない）。
   *
   * 例: `/student/japanese-demo?list=FAILED&save=SAVE_FAILED`
   * 動作確認のために状態を固定したいときだけ使う（通常は何も付けない）。
   */
  function applyDisplayFromQuery(query: string): void {
    const params = new URLSearchParams(query)
    const list = params.get('list')
    const detail = params.get('detail')
    const save = params.get('save')
    const listValues = ['NORMAL', 'NO_RESULT', 'NOT_GENERATED', 'GENERATING', 'FAILED']
    const detailValues = ['FULL', 'PARTIAL']
    const saveValues = ['NORMAL', 'SAVE_FAILED', 'CONFLICT']
    if (list !== null && listValues.includes(list)) {
      display.listMode = list as DemoDisplayState['listMode']
    }
    if (detail !== null && detailValues.includes(detail)) {
      display.detailMode = detail as DemoDisplayState['detailMode']
    }
    if (save !== null && saveValues.includes(save)) {
      display.saveMode = save as DemoDisplayState['saveMode']
    }
  }

  /* ---------------------------------------------------------------- */
  /* 一覧（絞り込み・ページ・スクロール位置を保持する）                  */
  /* ---------------------------------------------------------------- */

  const filters = reactive<DemoFilters>(emptyFilters())
  const listPage = ref(1)
  const listSize = ref(10)
  /** 一覧のスクロール位置（戻ったときに復元する）。 */
  const listScrollTop = ref(0)
  /** 詳細から戻ったときに知らせる 1 行（「デモデータに登録しました」など）。 */
  const listNotice = ref('')

  /**
   * 一覧に出す形（デモ表示設定の上書きを当てる）。
   *
   * 生成中の語は **RUNNING のまま**見せる（生成の途中経過を上書きで隠さない）。
   */
  function toListRow(word: DemoWord): DemoWord {
    if (runningWordIds.value.has(word.id)) {
      return { ...word, detailStatus: 'RUNNING', failureReason: null }
    }
    return {
      ...word,
      detailStatus: displayStatus(word, display),
      failureReason: displayFailureReason(word, display)
    }
  }

  const visibleWords = computed(() => words.value.map((word) => toListRow(word)))

  const filteredWords = computed(() => filterWords(words.value, filters, display))

  const pageResult = computed(() => paginate(filteredWords.value, listPage.value, listSize.value))

  /** 一覧に出す 1 ページぶん。 */
  const pageWords = computed(() => pageResult.value.items.map((word) => toListRow(word)))

  const totalWords = computed(() => words.value.length)

  /** 絞り込みの選択肢（仮データから作る）。 */
  const bookNameOptions = computed(() => books.value.map((book) => book.name))
  const unitOptions = computed(() => {
    const names = new Set<string>()
    for (const word of words.value) {
      for (const collection of word.collections) {
        names.add(collection.unit)
      }
    }
    return [...names].sort((left, right) => left.localeCompare(right, 'ja'))
  })
  const jlptOptions = computed(() => {
    const values = new Set<string>()
    for (const word of words.value) {
      if (word.jlpt !== null && word.jlpt !== '') {
        values.add(word.jlpt)
      }
    }
    return [...values].sort()
  })

  function setFilter<K extends keyof DemoFilters>(key: K, value: DemoFilters[K]): void {
    filters[key] = value
    listPage.value = 1
  }

  function resetFilters(): void {
    Object.assign(filters, emptyFilters())
    listPage.value = 1
  }

  function gotoPage(page: number): void {
    listPage.value = Math.min(Math.max(1, page), pageResult.value.totalPages)
  }

  function setPageSize(size: number): void {
    listSize.value = size
    listPage.value = 1
  }

  function rememberScroll(top: number): void {
    listScrollTop.value = top
  }

  function findWord(id: string): DemoWord | null {
    return words.value.find((word) => word.id === id) ?? null
  }

  /* ---------------------------------------------------------------- */
  /* 詳細編集の下書き（保存済みと混ぜない）                              */
  /* ---------------------------------------------------------------- */

  const draft = ref<DemoWord | null>(null)
  /** 保存済みの内容から変更されているか。 */
  const draftDirty = ref(false)
  /** 保存の結果（成功・失敗・衝突）を画面に伝える。 */
  const saveState = ref<'IDLE' | 'SAVING' | 'SAVED' | 'FAILED' | 'CONFLICT'>('IDLE')
  const saveMessage = ref('')
  /** 競合のときに残しておく「相手の版」（デモでは固定の文言）。 */
  const conflictNote = ref('')

  /** 編集を開く（下書きを作る）。 */
  function openEditor(id: string): boolean {
    const word = findWord(id)
    if (word === null) {
      return false
    }
    draft.value = clone(word)
    draftDirty.value = false
    saveState.value = 'IDLE'
    saveMessage.value = ''
    conflictNote.value = ''
    writeStoredDraft(draft.value)
    return true
  }

  /** 別ウィンドウ（編集・学習）が置いた下書きの控えを読み込む。 */
  function loadStoredDraft(): boolean {
    const stored = readStoredDraft()
    if (stored === null) {
      return false
    }
    draft.value = stored
    return true
  }

  function closeEditor(): void {
    draft.value = null
    draftDirty.value = false
    saveState.value = 'IDLE'
    saveMessage.value = ''
    writeStoredDraft(null)
  }

  /** 下書きの詳細の中身（無ければ空の器を作る）。 */
  function draftDetail(): DemoDetailContent | null {
    if (draft.value === null) {
      return null
    }
    if (draft.value.detail === null) {
      draft.value.detail = emptyDetail()
    }
    return draft.value.detail
  }

  function touchDraft(): void {
    draftDirty.value = true
    writeStoredDraft(draft.value)
    if (saveState.value === 'SAVED' || saveState.value === 'FAILED' || saveState.value === 'CONFLICT') {
      saveState.value = 'IDLE'
      saveMessage.value = ''
    }
  }

  /** 下書きの基本情報を更新する。 */
  function updateDraftBasic(patch: Partial<Pick<DemoWord,
    'heading' | 'reading' | 'partOfSpeech' | 'jlpt' | 'chineseMeaning' | 'alternateReading'>>): void {
    if (draft.value === null) {
      return
    }
    Object.assign(draft.value, patch)
    touchDraft()
  }

  /** 下書きの詳細（説明文）を更新する。 */
  function updateDraftDescription(patch: Partial<Pick<DemoDetailContent,
    'coreMeaning' | 'descriptionJa' | 'descriptionZh'>>): void {
    const detail = draftDetail()
    if (detail === null) {
      return
    }
    Object.assign(detail, patch)
    touchDraft()
  }

  /** 配列の項目を操作するためのキー。 */
  type ListKey =
    | 'senses' | 'examples' | 'patterns' | 'dialogs' | 'synonyms' | 'cautions'
    | 'conjugations' | 'collocations' | 'relatedWords' | 'usageNotes' | 'practices'

  /** 配列に 1 件足す。 */
  function addListItem(key: ListKey, item: unknown): void {
    const detail = draftDetail()
    if (detail === null) {
      return
    }
    ;(detail[key] as unknown[]).push(item)
    touchDraft()
  }

  /** 配列の 1 件を差し替える。 */
  function updateListItem(key: ListKey, index: number, patch: Record<string, unknown>): void {
    const detail = draftDetail()
    if (detail === null) {
      return
    }
    const list = detail[key] as unknown as Record<string, unknown>[]
    const current = list[index]
    if (current === undefined) {
      return
    }
    list[index] = { ...current, ...patch }
    touchDraft()
  }

  /** 配列の 1 件を消す。 */
  function removeListItem(key: ListKey, index: number): void {
    const detail = draftDetail()
    if (detail === null) {
      return
    }
    const list = detail[key] as unknown[]
    if (index < 0 || index >= list.length) {
      return
    }
    list.splice(index, 1)
    touchDraft()
  }

  /** 配列の順番を入れ替える（-1 で前へ、+1 で後ろへ）。 */
  function moveListItem(key: ListKey, index: number, direction: -1 | 1): void {
    const detail = draftDetail()
    if (detail === null) {
      return
    }
    const list = detail[key] as unknown[]
    const target = index + direction
    if (index < 0 || index >= list.length || target < 0 || target >= list.length) {
      return
    }
    const [moved] = list.splice(index, 1)
    list.splice(target, 0, moved)
    touchDraft()
  }

  /** 詳細の中身そのもの（発音・記憶のヒント・自他動詞）を差し替える。 */
  function setDetailPart(patch: Partial<Pick<DemoDetailContent,
    'pronunciation' | 'memoryHint' | 'transitivityPair'>>): void {
    const detail = draftDetail()
    if (detail === null) {
      return
    }
    Object.assign(detail, patch)
    touchDraft()
  }

  /** 下書きを保存する（デモ表示設定に応じて失敗・衝突を再現する）。 */
  async function saveDraft(): Promise<boolean> {
    if (draft.value === null) {
      return false
    }
    saveState.value = 'SAVING'
    saveMessage.value = '保存しています…'
    await new Promise((resolve) => setTimeout(resolve, MOCK_SAVE_DELAY_MS))

    if (display.saveMode === 'SAVE_FAILED') {
      saveState.value = 'FAILED'
      saveMessage.value = '保存できませんでした。入力は残しています。もう一度お試しください。'
      return false
    }
    if (display.saveMode === 'CONFLICT') {
      saveState.value = 'CONFLICT'
      saveMessage.value = 'ほかの画面で同じ単語が更新されています。いまの入力は残しています。'
      conflictNote.value = '保存済みの内容: 語義が 2 件 → 3 件に更新されています。'
      return false
    }

    const index = words.value.findIndex((word) => word.id === draft.value?.id)
    if (index < 0) {
      saveState.value = 'FAILED'
      saveMessage.value = '単語が見つかりませんでした。'
      return false
    }
    const saved: DemoWord = clone(draft.value)
    saved.manuallyEdited = true
    saved.detailStatus = hasAnyDetail(saved.detail) ? 'EDITED' : saved.detailStatus
    saved.updatedAt = new Date().toISOString()
    words.value[index] = saved
    draft.value = clone(saved)
    writeStoredDraft(draft.value)
    draftDirty.value = false
    saveState.value = 'SAVED'
    saveMessage.value = '保存しました。'

    // 一覧へ戻ったときに知らせる
    listNotice.value = `「${saved.heading}」を保存しました。`
    return true
  }

  /** 競合を承知のうえで上書き保存する（デモの摩擦を減らす）。 */
  function resolveConflictAsMine(): void {
    if (display.saveMode === 'CONFLICT') {
      display.saveMode = 'NORMAL'
    }
    saveState.value = 'IDLE'
    saveMessage.value = ''
  }

  /* ---------------------------------------------------------------- */
  /* 詳細情報の生成（疑似 AI。通信はしない）                            */
  /* ---------------------------------------------------------------- */

  /**
   * 生成中の状態にして、遅れて仮データを入れる。
   * 画面を離れても世代で判定するので、古い結果が新しい内容を上書きしない。
   */
  function startGeneration(wordId: string): void {
    const word = findWord(wordId)
    if (word === null || word.detailStatus === 'RUNNING') {
      return
    }
    jobGeneration += 1
    const generation = jobGeneration
    word.detailStatus = 'RUNNING'
    word.failureReason = null
    runningJobs.value = [...runningJobs.value, { wordId, kind: 'GENERATE', startedAt: Date.now(), generation }]

    void (async () => {
      await new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS))
      const job = runningJobs.value.find((entry) => entry.generation === generation)
      runningJobs.value = runningJobs.value.filter((entry) => entry.generation !== generation)
      if (job === undefined) {
        // 画面を離れた／リセットされた後なので、結果は捨てる
        return
      }
      const target = findWord(wordId)
      if (target === null || target.detailStatus !== 'RUNNING') {
        // すでに別の操作（手動編集など）が入っているときは上書きしない
        return
      }
      if (display.listMode === 'FAILED') {
        target.detailStatus = 'FAILED'
        target.failureReason = '生成サービスが応答しませんでした。時間をおいて再試行してください。'
        return
      }
      target.detail = buildDemoDetail(target)
      target.detailStatus = 'GENERATED'
      target.updatedAt = new Date().toISOString()
      target.demoNote = null
    })()
  }

  /** 生成を中断する（画面を離れるとき・リセットするとき）。 */
  function cancelJobs(): void {
    runningJobs.value = []
    jobGeneration += 1
  }

  /** 「AI で補足案を作る」の結果（採用するかどうかは人が決める）。 */
  function buildSupplementSuggestions(word: DemoWord): { label: string; value: string }[] {
    const detail = word.detail
    const suggestions: { label: string; value: string }[] = []
    if (detail === null || detail.patterns.length === 0) {
      suggestions.push({ label: '文型・助詞', value: `「${word.heading}」を使う文型を 1 つ提案します。` })
    }
    if (detail === null || detail.memoryHint === null) {
      suggestions.push({ label: '記憶のヒント', value: '漢字の形と場面を結びつけた覚え方の案（語源ではありません）。' })
    }
    if (detail === null || detail.usageNotes.length === 0) {
      suggestions.push({ label: '使用場面', value: '話し言葉か書き言葉か、使う相手についての案。' })
    }
    if (suggestions.length === 0) {
      suggestions.push({ label: '例文の追加', value: '日常の場面の例文をもう 1 つ足す案。' })
    }
    return suggestions
  }

  /**
   * 詳細を作り直す（デモの仮データ）。
   * **いま編集中の入力は自動で上書きしない**（呼ぶ側が比較して選ばせる）。
   */
  function regenerateDetail(wordId: string): DemoWord | null {
    const word = findWord(wordId)
    if (word === null) {
      return null
    }
    return { ...clone(word), detail: buildDemoDetail(word), detailStatus: 'GENERATED' }
  }

  /** 仮の詳細を作る（既にある内容は残し、足りない項目だけ補う）。 */
  function buildDemoDetail(word: DemoWord): DemoDetailContent {
    const base = word.detail === null ? emptyDetail() : clone(word.detail)
    if (base.coreMeaning === '') {
      base.coreMeaning = word.chineseMeaning
    }
    if (base.descriptionJa === '') {
      base.descriptionJa = `「${word.heading}」は${word.partOfSpeech}として使う言葉です。`
    }
    if (base.descriptionZh === '') {
      base.descriptionZh = `「${word.heading}」的用法说明。`
    }
    if (base.senses.length === 0) {
      base.senses = [
        {
          id: nextId('sense'),
          number: 1,
          japanese: `${word.chineseMeaning}という意味です。`,
          chinese: word.chineseMeaning,
          context: '日常',
          style: '普通'
        }
      ]
    }
    if (base.examples.length === 0) {
      base.examples = [
        {
          id: nextId('example'),
          japanese: `${word.heading}についての例文です。`,
          reading: '',
          chinese: `关于「${word.heading}」的例句。`,
          senseNumber: 1,
          source: 'デモ',
          level: 'BASIC'
        }
      ]
    }
    return base
  }

  /* ---------------------------------------------------------------- */
  /* 新規登録（3 ステップ）                                             */
  /* ---------------------------------------------------------------- */

  const registerStep = ref(1)
  const pasteText = ref('')
  const parsedRows = ref<ParsedRow[]>([])
  /** 取り込む単語（1 行 1 語。読みと意味はここでは入れない）。 */
  const registerHeadings = ref<string[]>([])
  const registerBookMode = ref<'EXISTING' | 'NEW'>('EXISTING')
  /** 既定は先頭の書籍（未選択のまま行き止まりにしない）。 */
  const registerBookName = ref(DEMO_BOOKS[0]?.name ?? '')
  /** 新しい書籍のときだけ使う（既存の書籍は自動で決まる）。 */
  const registerUnitSizeInput = ref<number | null>(null)
  const registerPlacement = ref<UnitPlacement>('CONTINUE')
  /** 重複した単語の扱い（既定は「選んだ書籍の中の重複を飛ばす」）。 */
  const registerDuplicateMode = ref<DuplicateMode>('BOOK')
  const registerSaveState = ref<'IDLE' | 'SAVING' | 'SAVED' | 'FAILED'>('IDLE')
  const registerError = ref('')

  const registerBook = computed<DemoBook | null>(() =>
    registerBookMode.value === 'EXISTING'
      ? books.value.find((book) => book.name === registerBookName.value) ?? null
      : null
  )

  /**
   * 1 Unit あたりの語数。
   *
   * 既存の書籍は**その本から自動で決める**（教材ごとに違うため）。
   * 新しい書籍は決められないので、入力してもらう。
   */
  const detectedUnitSize = computed(() => detectUnitSize(registerBook.value))
  const registerUnitSize = computed(() =>
    registerBookMode.value === 'NEW'
      ? Math.max(1, Math.floor(registerUnitSizeInput.value ?? 20))
      : detectedUnitSize.value
  )

  /**
   * 取り込む単語（表記だけ）。
   *
   * **飛ばすと決めた行は入れない**（重複した語は Unit の位置を使わない）。
   * 解釈結果（`parsedRows`）があるときはそれに従い、無ければ表の内容をそのまま使う。
   */
  const registerWords = computed(() => {
    if (parsedRows.value.length > 0) {
      return parsedRows.value
        .filter((row) => row.state === 'OK')
        .map((row) => ({ heading: row.heading, reading: '', chineseMeaning: '', line: row.line }))
    }
    return registerHeadings.value
      .map((heading, index) => ({ heading: heading.trim(), reading: '', chineseMeaning: '', line: index + 1 }))
      .filter((word) => word.heading !== '')
  })

  const registerSummary = computed(() => summarizeParsed(parsedRows.value))

  /** 入力順と容量で割り当てた Unit（AI の意味分類ではない）。 */
  const allocation = computed(() => {
    const book = registerBook.value
    const currentUnitCount = book === null || book.units.length === 0
      ? 0
      : book.units[book.units.length - 1]!.count
    return allocateUnits({
      words: registerWords.value.map((word) => ({ heading: word.heading, reading: word.reading })),
      book,
      newBookName: registerBookName.value,
      unitSize: registerUnitSize.value,
      placement: registerBookMode.value === 'NEW' ? 'NEW_BOOK' : registerPlacement.value,
      currentUnitCount
    })
  })

  /** 確認画面のまとめ（新規・再利用・重複・エラー）。 */
  const registerCounts = computed(() => {
    const existing = new Set(
      words.value.map((word) => `${normalizeHeading(word.heading)}\u0000${word.reading}`)
    )
    let reuse = 0
    let fresh = 0
    for (const word of registerWords.value) {
      if (existing.has(`${word.heading}\u0000${word.reading}`)) {
        reuse += 1
      } else {
        fresh += 1
      }
    }
    return {
      fresh,
      reuse,
      /* 入力の中で重複した行＋（すべての書籍で重複を飛ばす設定のとき）既存と重複した行 */
      skipped: registerSummary.value.duplicates + registerSummary.value.existingReuse,
      duplicatesInInput: registerSummary.value.duplicates,
      duplicatesInOtherBooks: registerSummary.value.existingReuse,
      errors: registerSummary.value.errors,
      blank: registerSummary.value.blank
    }
  })

  /** いま選んでいる書籍に入っている語（重複の判定に使う）。 */
  function headingsInSelectedBook(): string[] {
    const book = registerBook.value
    if (book === null) {
      return []
    }
    return words.value
      .filter((word) => word.collections.some((collection) => collection.book === book.name))
      .map((word) => word.heading)
  }

  /** すべての書籍に入っている語（重複の判定に使う）。 */
  function headingsInAllBooks(): string[] {
    return words.value.map((word) => word.heading)
  }

  /**
   * 取り込む単語を解釈して、確認できる形にする（「取り込む」を押したとき）。
   *
   * 重複の判定は画面のラジオで選んだ方を使う:
   *  ・BOOK … 選んだ書籍の中の重複だけを飛ばす
   *  ・ALL  … すべての書籍の語と重複するものを飛ばす
   */
  function parseRegisterText(): void {
    const existing = registerDuplicateMode.value === 'ALL'
      ? headingsInAllBooks()
      : headingsInSelectedBook()
    parsedRows.value = parseWords(registerHeadings.value.join('\n'), existing, registerDuplicateMode.value)
    registerError.value = ''
  }

  /** 入力例を入れる（すぐ試せるように）。 */
  function fillSample(text: string): void {
    pasteText.value = text
    parseRegisterText()
  }

  /** グリッドの操作（Excel 風の表は部品が持ち、ここは値を受けるだけ）。 */

  /** 表の値を丸ごと入れ替えて、解釈し直す。 */
  function setRegisterHeadings(values: string[]): void {
    registerHeadings.value = [...values]
    parseRegisterText()
  }




  function gotoRegisterStep(step: number): void {
    registerStep.value = Math.min(Math.max(1, step), 3)
    registerError.value = ''
  }

  function resetRegister(): void {
    registerStep.value = 1
    pasteText.value = ''
    parsedRows.value = []
    registerHeadings.value = []
    registerBookMode.value = 'EXISTING'
    registerBookName.value = books.value[0]?.name ?? ''
    registerUnitSizeInput.value = null
    registerPlacement.value = 'CONTINUE'
    registerDuplicateMode.value = 'BOOK'
    registerSaveState.value = 'IDLE'
    registerError.value = ''
  }

  /** 割り当てのプレビューで 1 語の Unit を人が変える（容量は自動のまま）。 */
  function overrideAllocation(): void {
    // デモでは「自動割り当てのまま」と「Unit 容量を変える」の 2 つだけを提供する。
    // 個別の並べ替えは正式開発で扱う（§9 の申し送り）。
  }

  /**
   * 保存する（**ローカルの仮データだけ**）。
   * 表示設定で「保存失敗」を選んでいるときは失敗を再現し、入力は残す。
   */
  async function saveRegistration(): Promise<boolean> {
    if (registerWords.value.length === 0) {
      registerError.value = '取り込む単語がありません。'
      return false
    }
    registerSaveState.value = 'SAVING'
    registerError.value = ''
    await new Promise((resolve) => setTimeout(resolve, MOCK_SAVE_DELAY_MS))

    if (display.saveMode === 'SAVE_FAILED') {
      registerSaveState.value = 'FAILED'
      registerError.value = '登録できませんでした。入力はそのまま残しています。'
      return false
    }

    const bookName = registerBookMode.value === 'NEW' ? registerBookName.value.trim() : registerBookName.value
    if (bookName === '') {
      registerSaveState.value = 'FAILED'
      registerError.value = '書籍名を入力してください。'
      return false
    }

    let book = books.value.find((entry) => entry.name === bookName) ?? null
    if (book === null) {
      book = { id: nextId('book'), name: bookName, unitSize: registerUnitSize.value, units: [], note: '' }
      books.value = [...books.value, book]
    }

    const allocated: AllocatedWord[] = allocation.value.words
    const createdAt = new Date().toISOString()

    registerWords.value.forEach((entry, index) => {
      const place = allocated[index]
      if (place === undefined) {
        return
      }
      const existing = words.value.find(
        (word) => word.heading === place.heading && word.reading === place.reading
      )
      const collection = {
        id: nextId('collection'),
        book: bookName,
        unit: place.unit,
        seq: place.seq,
        listedWord: entry.heading,
        listedReading: entry.reading,
        listedChinese: entry.chineseMeaning
      }
      if (existing !== undefined) {
        // 同じ見出し語＋読みなので、語は増やさず収録だけ足す
        existing.collections = [...existing.collections, collection]
        existing.updatedAt = createdAt
        return
      }
      words.value = [
        ...words.value,
        {
          id: nextId('word'),
          heading: entry.heading,
          reading: entry.reading,
          alternateReading: null,
          partOfSpeech: '名詞',
          jlpt: null,
          chineseMeaning: entry.chineseMeaning,
          detailStatus: 'NOT_GENERATED',
          failureReason: null,
          manuallyEdited: false,
          collections: [collection],
          detail: null,
          updatedAt: createdAt,
          demoNote: null
        }
      ]
    })

    // 書籍の Unit 語数を足す
    const bookIndex = books.value.findIndex((entry) => entry.id === book?.id)
    if (bookIndex >= 0) {
      const target = books.value[bookIndex]!
      const units = [...target.units]
      for (const summary of allocation.value.summaries) {
        const unitIndexInBook = units.findIndex((unit) => unit.name === summary.unit)
        if (unitIndexInBook >= 0) {
          const unit = units[unitIndexInBook]!
          units[unitIndexInBook] = { ...unit, count: unit.count + summary.count }
        } else {
          units.push({ name: summary.unit, count: summary.count, capacity: registerUnitSize.value })
        }
      }
      books.value = books.value.map((entry, index) =>
        index === bookIndex ? { ...entry, units, unitSize: registerUnitSize.value } : entry
      )
    }

    registerSaveState.value = 'SAVED'
    listNotice.value = `${registerWords.value.length} 語を登録しました。`
    resetRegister()
    return true
  }

  /* ---------------------------------------------------------------- */
  /* 削除                                                              */
  /* ---------------------------------------------------------------- */

  const deleteTargetId = ref<string | null>(null)
  const deleteState = ref<'IDLE' | 'DELETING' | 'FAILED'>('IDLE')
  const deleteMessage = ref('')

  const deleteTarget = computed<DemoWord | null>(() =>
    deleteTargetId.value === null ? null : findWord(deleteTargetId.value)
  )

  function openDelete(id: string): void {
    deleteTargetId.value = id
    deleteState.value = 'IDLE'
    deleteMessage.value = ''
  }

  function closeDelete(): void {
    deleteTargetId.value = null
    deleteState.value = 'IDLE'
    deleteMessage.value = ''
  }

  /**
   * 削除する（**一覧から消えるだけ。学習の記録は残る扱い**にする）。
   * 表示設定で「保存失敗」を選んでいるときは失敗を再現する。
   */
  async function confirmDelete(): Promise<boolean> {
    const target = deleteTarget.value
    if (target === null) {
      return false
    }
    deleteState.value = 'DELETING'
    await new Promise((resolve) => setTimeout(resolve, MOCK_SAVE_DELAY_MS))
    if (display.saveMode === 'SAVE_FAILED') {
      deleteState.value = 'FAILED'
      deleteMessage.value = '削除できませんでした。対象はそのまま残っています。'
      return false
    }
    words.value = words.value.filter((word) => word.id !== target.id)
    listNotice.value = `「${target.heading}」を削除しました。学習の記録は残ります。`
    closeDelete()
    return true
  }

  /* ---------------------------------------------------------------- */
  /* リセット                                                          */
  /* ---------------------------------------------------------------- */

  function resetAll(): void {
    cancelJobs()
    // 生成中にリセットしても、途中の「生成中」を初期サンプルへ持ち越さない
    words.value = clone(DEMO_WORDS).filter((word) => word.detailStatus !== 'RUNNING')
    books.value = clone(DEMO_BOOKS)
    Object.assign(display, { listMode: 'NORMAL', detailMode: 'FULL', saveMode: 'NORMAL' })
    Object.assign(filters, emptyFilters())
    listPage.value = 1
    listSize.value = 10
    listScrollTop.value = 0
    listNotice.value = ''
    resetRegister()
    closeDelete()
    closeEditor()
  }

  return {
    // 仮データ
    words,
    books,
    // 表示設定
    display,
    setDisplay,
    applyDisplayFromQuery,
    // 一覧
    filters,
    filteredWords,
    pageWords,
    pageResult,
    listPage,
    listSize,
    listScrollTop,
    listNotice,
    totalWords,
    visibleWords,
    bookNameOptions,
    unitOptions,
    jlptOptions,
    setFilter,
    resetFilters,
    gotoPage,
    setPageSize,
    rememberScroll,
    findWord,
    // 詳細編集
    draft,
    draftDirty,
    saveState,
    saveMessage,
    conflictNote,
    openEditor,
    loadStoredDraft,
    closeEditor,
    touchDraft,
    updateDraftBasic,
    updateDraftDescription,
    addListItem,
    updateListItem,
    removeListItem,
    moveListItem,
    setDetailPart,
    saveDraft,
    resolveConflictAsMine,
    // 生成（疑似 AI）
    runningJobs,
    startGeneration,
    cancelJobs,
    buildSupplementSuggestions,
    regenerateDetail,
    // 新規登録
    registerStep,
    pasteText,
    parsedRows,
    registerHeadings,
    registerBookMode,
    registerBookName,
    registerUnitSize,
    registerUnitSizeInput,
    detectedUnitSize,
    registerDuplicateMode,
    registerPlacement,
    registerSaveState,
    registerError,
    registerWords,
    registerSummary,
    registerCounts,
    allocation,
    parseRegisterText,
    setRegisterHeadings,
    fillSample,
    gotoRegisterStep,
    resetRegister,
    overrideAllocation,
    saveRegistration,
    // 削除
    deleteTargetId,
    deleteTarget,
    deleteState,
    deleteMessage,
    openDelete,
    closeDelete,
    confirmDelete,
    // リセット
    resetAll
  }
})

/** 型の再輸出（画面が `store` だけを import して済むように）。 */
export type { AllocationSummary, AllocatedWord, DemoFilters, ParsedRow }
export type {
  DemoDetailCaution,
  DemoDetailCollocation,
  DemoDetailConjugation,
  DemoDetailDialog,
  DemoDetailExample,
  DemoDetailPattern,
  DemoDetailRelatedWord,
  DemoDetailSense,
  DemoDetailSynonym,
  DemoDetailUsageNote,
  DemoPracticeQuestion
}
