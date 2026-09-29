/**
 * 単語情報管理（本番）の「新規登録」＝新画面の登録画面で使う状態。
 *
 * 新画面（`views/japanese/demo/DemoWordNewView.vue`）は、デモでも本番でも同じ見た目で動く。
 * 画面が使うものは `registerContract.ts` に書いた契約だけなので、ここでは
 * **実 API（`POST /api/user/japanese/words/register`）を 1 回だけ呼ぶ**実装を提供する。
 *
 * 登録は語と収録（書籍・分類・SEQ）をまとめて送る。語だけを送ると一覧の書籍・分類が
 * 空になるので、この画面は必ず収録まで送る（`saveRegistration`）。
 * 送る項目は 見出し語・書籍・分類・SEQ と、教材の表記（掲載見出し語）。
 * **読みと中国語意味は送らない**（読みはあとで AI 詳細取得が埋める）。
 */

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError } from '@study21/web-shared'
import { registerJpnWords, type JpnRegisterWord } from '@/api/japanese'
import type { DemoBook } from '@/features/japanese-demo/types'
import {
  allocateUnits,
  detectUnitSize,
  normalizeHeading,
  type DuplicateMode,
  type UnitPlacement
} from '@/features/japanese-demo/logic'
import type { RegisterCounts, RegisterSaveState, RegisterWord } from './registerContract'

/** 登録画面に渡す材料（一覧が持っているもの）。 */
export interface RegisterSource {
  /** 途中で分かった書籍（母表に書籍の列は無いので、一覧の収録から作る）。 */
  books: DemoBook[]
  /** 既に登録されている見出し語（重複の判定に使う）。 */
  knownHeadings: string[]
}

/** 空の書籍（まだ何も無いときの器）。 */
function emptyBook(name: string): DemoBook {
  return { id: `book-${name}`, name, unitSize: 20, units: [], note: '' }
}

export const useJpnRegisterStore = defineStore('jpn-register', () => {
  const registerHeadings = ref<string[]>([''])
  const registerBookMode = ref<'EXISTING' | 'NEW'>('EXISTING')
  const registerBookName = ref('')
  const registerUnitSizeInput = ref<number | null>(null)
  const registerPlacement = ref<UnitPlacement>('CONTINUE')
  const registerDuplicateMode = ref<DuplicateMode>('BOOK')
  const registerSaveState = ref<RegisterSaveState>('IDLE')
  const registerError = ref('')
  /** 保存できたときの知らせ（何件入ったか）。 */
  const lastMessage = ref('')

  /** 例外から画面に出す理由を取る（API のメッセージをそのまま使う）。 */
  function messageOf(cause: unknown): string {
    return cause instanceof ApiError
      ? cause.message
      : cause instanceof Error
        ? cause.message
        : '単語を登録できませんでした。'
  }

  const books = ref<DemoBook[]>([])
  const knownHeadings = ref<string[]>([])

  const bookNameOptions = computed(() => books.value.map((book) => book.name))

  const registerBook = computed<DemoBook | null>(() => {
    const name = registerBookName.value
    if (registerBookMode.value !== 'EXISTING' || name === '') {
      return null
    }
    return books.value.find((book) => book.name === name) ?? emptyBook(name)
  })

  const detectedUnitSize = computed(() => detectUnitSize(registerBook.value))
  const registerUnitSize = computed(() =>
    Math.max(1, Math.floor(registerUnitSizeInput.value ?? detectedUnitSize.value))
  )

  /** 表から取り込む語（見出し語だけ。空行は入れない）。 */
  const registerWords = computed<RegisterWord[]>(() =>
    registerHeadings.value
      .map((heading, index) => ({ heading: heading.trim(), reading: '', chineseMeaning: '', line: index + 1 }))
      .filter((word) => word.heading !== '')
  )

  /** 重複の判定（既に登録されている語・表の中の重複）。 */
  const registerCounts = computed<RegisterCounts>(() => {
    const known = new Set(knownHeadings.value.map((heading) => normalizeHeading(heading)))
    const seen = new Set<string>()
    let reuse = 0
    let skipped = 0
    for (const word of registerWords.value) {
      const key = normalizeHeading(word.heading)
      if (known.has(key)) {
        reuse += 1
        continue
      }
      if (seen.has(key)) {
        skipped += 1
        continue
      }
      seen.add(key)
    }
    return { fresh: registerWords.value.length - reuse - skipped, skipped, reuse }
  })

  /** 取り込む語のうち、実際に登録するもの（既存と重複を除く）。 */
  const wordsToSave = computed<RegisterWord[]>(() => {
    const known = new Set(knownHeadings.value.map((heading) => normalizeHeading(heading)))
    const seen = new Set<string>()
    const result: RegisterWord[] = []
    for (const word of registerWords.value) {
      const key = normalizeHeading(word.heading)
      if (known.has(key) || seen.has(key)) {
        continue
      }
      seen.add(key)
      result.push(word)
    }
    return result
  })

  const allocation = computed(() =>
    allocateUnits({
      words: wordsToSave.value.map((word) => ({ heading: word.heading, reading: '' })),
      book: registerBook.value,
      newBookName: registerBookName.value,
      unitSize: registerUnitSize.value,
      placement: registerBookMode.value === 'NEW' ? 'NEW_BOOK' : registerPlacement.value,
      currentUnitCount:
        registerBook.value === null || registerBook.value.units.length === 0
          ? 0
          : registerBook.value.units[registerBook.value.units.length - 1]!.count
    })
  )

  /** 取り込んだ行の解釈（表の「重複」の印に使う）。 */
  const parsedRows = computed(() => {
    const known = new Set(knownHeadings.value.map((heading) => normalizeHeading(heading)))
    const seen = new Set<string>()
    return registerWords.value.map((word) => {
      const key = normalizeHeading(word.heading)
      // 既に登録されている語と、表の中での重複を分けて印を付ける
      const existing = known.has(key)
      const duplicated = seen.has(key)
      seen.add(key)
      const state = existing
        ? ('DUPLICATE_EXISTING' as const)
        : duplicated
          ? ('DUPLICATE' as const)
          : ('OK' as const)
      const note = existing
        ? 'すでに登録されています。'
        : duplicated
          ? 'この入力の中で重複しています。'
          : ''
      return {
        line: word.line,
        raw: word.heading,
        heading: word.heading,
        reading: '',
        chineseMeaning: '',
        // 読みと中国語意味はこの画面では受け取らない（API も受け取らない）
        readingCandidates: [] as string[],
        state,
        note
      }
    })
  })

  /** 一覧から材料をもらう（開くたびに呼ぶ）。 */
  function prepare(source: RegisterSource): void {
    books.value = source.books
    knownHeadings.value = source.knownHeadings
    registerHeadings.value = ['']
    registerBookMode.value = 'EXISTING'
    registerBookName.value = source.books[0]?.name ?? ''
    registerUnitSizeInput.value = detectUnitSize(source.books[0] ?? null)
    registerPlacement.value = 'CONTINUE'
    registerDuplicateMode.value = 'BOOK'
    registerSaveState.value = 'IDLE'
    registerError.value = ''
  }

  function setRegisterHeadings(values: string[]): void {
    registerHeadings.value = [...values]
    registerError.value = ''
  }

  function bookUnitSizeOf(name: string): number {
    const book = books.value.find((entry) => entry.name === name) ?? null
    return detectUnitSize(book)
  }

  function resetRegister(): void {
    prepare({ books: books.value, knownHeadings: knownHeadings.value })
  }

  /** 表の語を、割り当てた書籍・Unit ごと登録する。 */
  async function saveRegistration(): Promise<boolean> {
    const words = wordsToSave.value
    if (words.length === 0) {
      registerError.value = '取り込む単語がありません。'
      registerSaveState.value = 'FAILED'
      return false
    }
    if (allocation.value.words.length === 0) {
      registerError.value = '登録先の書籍と Unit を決めてください。'
      registerSaveState.value = 'FAILED'
      return false
    }
    registerSaveState.value = 'SAVING'
    registerError.value = ''

    // 割り当て（見出し語 → Unit と SEQ）と、入れる書籍の名前をそろえる。
    // 収録には 書籍・分類・SEQ が要る（語だけ入れると一覧の書籍・分類が空になる）
    const bookName = registerBookName.value.trim()
    if (bookName === '') {
      registerError.value = '書籍を選ぶか、新しい書籍の名前を入力してください。'
      registerSaveState.value = 'FAILED'
      return false
    }
    const byHeading = new Map(allocation.value.words.map((allocated) => [allocated.heading, allocated]))
    const payload: JpnRegisterWord[] = []
    for (const word of words) {
      const allocated = byHeading.get(word.heading)
      if (!allocated) {
        continue
      }
      payload.push({
        word: word.heading,
        reading: word.reading === '' ? undefined : word.reading,
        level: 'N1-N5',
        book: bookName,
        category: allocated.unit,
        wordSeq: allocated.seq,
        listedWord: word.heading,
        // 読みと中国語意味はこの画面では受け取らない（あとで AI が入れる）ので、
        // 教材の紙面の値としては見出し語だけを置く
        listedChineseMeaning: word.chineseMeaning === '' ? undefined : word.chineseMeaning
      })
    }

    try {
      const response = await registerJpnWords(payload)
      registerSaveState.value = 'SAVED'
      // 保存できた内容（何件入ったか）は呼び側が知らせる
      lastMessage.value = response.data?.message ?? ''
      resetRegister()
      return true
    } catch (caught) {
      registerSaveState.value = 'FAILED'
      registerError.value = messageOf(caught)
      return false
    }
  }

  return {
    registerHeadings,
    setRegisterHeadings,
    bookNameOptions,
    registerBookMode,
    registerBookName,
    registerUnitSize,
    registerUnitSizeInput,
    bookUnitSizeOf,
    registerPlacement,
    registerDuplicateMode,
    registerWords,
    registerCounts,
    allocation,
    parsedRows,
    registerSaveState,
    registerError,
    lastMessage,
    prepare,
    saveRegistration,
    resetRegister
  }
})
