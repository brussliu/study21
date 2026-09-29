/**
 * 英作文AI添削の**型と純関数**。
 *
 * <p>添削の結果は**本物の AI**（admin-api）が返し、作文は DB（`ENG_*`）に入る。ここには
 * 画面と API が共有する形（{@link EssayGrading} など）と、級から決まる純関数だけを置く。</p>
 *
 * <p>2026-09-27 まで見本データを**純前端で作っていた**ときの生成器（旧 `grading.ts` の
 * `gradeEssay`）・疑似 OCR（旧 `ocr.ts`）・見本の投入（旧 `samples.ts`）は、DB と AI に
 * 載せ替えたときに消した（見本は DB に入っている）。</p>
 *
 * <p>形は 2.0 の添削結果と同一にする: 級・4 観点（内容・構成・語彙・文法）・修正ポイントの
 * 4 項目・改善後の作文例・日本語/中国語の解説。`docs/DECISIONS.md` の
 * 「なぜ英作文AI添削の DB を ENG_* 3 表にしたか」を参照。</p>
 */

/** 英検級（2.0 と同じ 3 つ）。 */
export type EssayLevel = 'GRADE1' | 'PRE1' | 'GRADE2'

export const ESSAY_LEVELS: readonly EssayLevel[] = ['GRADE1', 'PRE1', 'GRADE2']

/** 級の表示名（画面でそのまま使う）。 */
export const ESSAY_LEVEL_LABELS: Record<EssayLevel, string> = {
  GRADE1: '英検1級',
  PRE1: '英検準1級',
  GRADE2: '英検2級'
}

/** 観点（2.0 と同じ 4 つ）。 */
export const ESSAY_RUBRIC_KEYS = ['内容', '構成', '語彙', '文法'] as const
export type EssayRubricKey = (typeof ESSAY_RUBRIC_KEYS)[number]

/** 1 観点ぶんの評価。 */
export interface EssayRubricItem {
  key: EssayRubricKey
  score: number
  maxScore: number
  /** その観点の講評（言語ごと）。 */
  note: string
}

/** 修正ポイント 1 件（原文 → 修正後・分類・理由）。 */
export interface EssayCorrection {
  original: string
  corrected: string
  category: string
  reason: string
}

/** 添削レポートの本文（日本語／中国語で同じ形）。 */
export interface EssayReportSide {
  title: string
  summary: string
  tags: string[]
  rubric: EssayRubricItem[]
  corrections: EssayCorrection[]
  advice: string
  notice: string
}

/**
 * 設問が求めていること（2.0 の `taskRequirements`）。
 *
 * <p>移行データには**形が 2 種類**ある。</p>
 *
 * <ul>
 *   <li>`list`: 文字列の配列（`["設問の問いに答える", …]`）。画面は箇条書きで出す。</li>
 *   <li>`note`: 日本語と中国語の解説を持つオブジェクト（`{"commentJa":"…","commentZh":"…"}`）。
 *       画面は**結果の言語**に合わせて 1 段落で出す。</li>
 * </ul>
 *
 * <p>どちらでもない形は `null`（画面は節ごと出さない）。</p>
 */
export type EssayTaskRequirements =
  | { kind: 'list'; items: string[] }
  | { kind: 'note'; ja: string; zh: string }

/** 添削 1 回ぶんの結果（`gradings` に積む＝何度も添削できる）。 */
export interface EssayGrading {
  /** 添削 1 回ぶんの識別子（画面の `key` と历次の切り替えに使う）。 */
  id: string
  /**
   * 何回目の添削か（**API の `round` をそのまま**。1 から）。
   *
   * <p>失敗した回も 1 行として残るので、回の番号は「成功した回を数えた順」ではない。
   * 画面はここをそのまま「第 N 回」に使う（`normalizeGradingReport` が API の行から入れる）。</p>
   */
  round?: number
  status: 'AI_GRADED'
  /** 結果の形式の版（2.0 の `eiken-ai-v1` を引き継ぐ）。 */
  version: string
  level: EssayLevel
  titleJa: string
  titleZh: string
  wordCount: number
  /**
   * 総合点（**分からない回は省略する**）。
   *
   * <p>`0` を「不明」の印には**しない**（本当に 0 点の回と区別できない）。画面は分かるときだけ
   * 数字を出し、分からないときは `—` を出す。</p>
   */
  score?: number
  /**
   * 満点（**分からない回は省略する**。2.0 の古い形式の移行データには無い）。
   *
   * <p>`0` を「不明」の印には**しない**（満点 0 点と区別できない）。画面は満点が分かるときだけ
   * `26 / 32` を出し、分からないときは点数だけを出す。</p>
   */
  maxScore?: number
  /** 1 観点の満点（GRADE1=8／PRE1・GRADE2=4）。 */
  rubricMax: number
  modelAnswer: string
  /** 語数の目安（例 '200〜240語'）。分からない回は空文字。 */
  wordRequirement: string
  /** 設問が求めていること（採点の観点として画面に出す）。読めない形は null。 */
  taskRequirements: EssayTaskRequirements | null
  warnings: string[]
  createdAt: string
  japanese: EssayReportSide
  chinese: EssayReportSide
}

/** 級ごとの 1 観点の満点（2.0 と同じ。GRADE1 は 8、PRE1・GRADE2 は 4）。 */
export function rubricMaxOf(level: EssayLevel): number {
  return level === 'GRADE1' ? 8 : 4
}

/** 級ごとの語数の目安（英検の目安に合わせる）。 */
export function wordRequirementOf(level: EssayLevel): string {
  if (level === 'GRADE1') {
    return '200〜240語'
  }
  return level === 'PRE1' ? '120〜150語' : '80〜100語'
}

/** 英単語の数（2.0 の wordCount と同じ数え方）。 */
export function countWords(text: string): number {
  return text.split(/\s+/).filter((word) => word.length > 0).length
}
