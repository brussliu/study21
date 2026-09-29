/**
 * 単語の新規登録（新画面）が使う状態の契約。
 *
 * 新画面の登録画面（`views/japanese/demo/DemoWordNewView.vue`）は、
 * デモ（仮データ）でも本番（実 API）でも同じ見た目で動くように作ってある。
 * 違いはこの契約を満たすストアだけなので、**画面はここに書いたものしか使わない**。
 *
 * デモ:  `features/japanese-demo/store/japaneseDemo.ts`（メモリ内だけ）
 * 本番:  `features/japanese-word/registerStore.ts`（`POST /api/user/japanese/words/register` を 1 回）
 */

import type { AllocationSummary, AllocatedWord, DuplicateMode, ParsedRow, UnitPlacement } from '@/features/japanese-demo/logic'

/** 登録する 1 語（表から読み取ったもの）。 */
export interface RegisterWord {
  heading: string
  reading: string
  chineseMeaning: string
  line: number
}

/** 取り込みの内訳（新規・飛ばす）。 */
export interface RegisterCounts {
  fresh: number
  skipped: number
  reuse: number
}

/** 登録の保存状態。 */
export type RegisterSaveState = 'IDLE' | 'SAVING' | 'SAVED' | 'FAILED'

export interface RegisterContract {
  /* ---- 表 ---- */
  /** 表の値（1 行 1 語）。 */
  registerHeadings: string[]
  setRegisterHeadings: (values: string[]) => void

  /* ---- 書籍と Unit ---- */
  bookNameOptions: string[]
  registerBookMode: 'EXISTING' | 'NEW'
  registerBookName: string
  registerUnitSize: number
  registerUnitSizeInput: number | null
  /** その書籍から自動で決まる 1 Unit の語数（書籍を選び直したときに使う）。 */
  bookUnitSizeOf: (name: string) => number

  /* ---- オプション ---- */
  registerPlacement: UnitPlacement
  registerDuplicateMode: DuplicateMode

  /* ---- 取り込む内容 ---- */
  registerWords: RegisterWord[]
  registerCounts: RegisterCounts
  allocation: { words: AllocatedWord[]; summaries: AllocationSummary[]; overflow: number; startUnit: string }
  /** 取り込んだ行の解釈（重複の印を出す）。 */
  parsedRows: ParsedRow[]

  /* ---- 保存 ---- */
  registerSaveState: RegisterSaveState
  registerError: string
  saveRegistration: () => Promise<boolean>
  resetRegister: () => void
}
