/**
 * 添削結果 JSON の正規化（**純関数**）。
 *
 * <p>2.0 の `批改結果JSON` は**形が 2 種類**あり、移行データにはそのまま入っている
 * （2026-09-27 に実データで確認。35 件中 24 件が新形式・11 件が古い形式）。画面はどちらでも
 * 同じように読めなければならないので、ここで 1 つの {@link EssayGrading} にまとめる。</p>
 *
 * <ul>
 *   <li><b>新形式</b>: `status, version, level, score, maxScore, rubricMax, rubricValues,
 *       modelAnswer, wordCount, wordRequirement, taskRequirements, warnings, titleJa, titleZh,
 *       japanese{...}, chinese{...}`。`japanese.rubric` は**観点名の配列**で、点数はトップの
 *       `rubricValues`、講評は `japanese.rubricNotes`、1 観点の満点は `rubricMax`。
 *       `corrections` は**配列の配列**（`[原文, 修正後, 分類, 理由]`）。</li>
 *   <li><b>古い形式</b>: `status` / `version` / `level` と `japanese`・`chinese` の一部だけ。
 *       `rubricValues`・`rubricMax`・`modelAnswer`・`taskRequirements` などが無い。</li>
 *   <li><b>語数の目安と設問の要求は、それぞれ形が 2 つ</b>（2026-09-27 に実データで確認）。
 *       `wordRequirement` は文字列か `{type, minimum, maximum, target}`、`taskRequirements` は
 *       文字列の配列か `{commentJa, commentZh}`。どちらも読んで画面の形に直す。</li>
 *   <li>見本データ（`登録元コード='SAMPLE'`。旧・純前端の見本と同じ形）は、`rubric` が
 *       オブジェクトの配列（`{key, score, maxScore, note}`）になっている。これも読む。</li>
 * </ul>
 *
 * <p><b>足りない項目は空にする</b>（`rubric: []` / `tags: []` / `corrections: []` /
 * `modelAnswer: ''`）。**偽の値をでっち上げない**。画面は「有るものだけ」を出す。</p>
 *
 * <p>回の番号（`round`）は**行の値（API の `round`）をそのまま**使う。失敗した回も 1 行として
 * 残るので、成功した回を数え直すと API とずれる。得点（`score`）と満点（`maxScore`）が
 * 分からない回は**省略する**（`0` を「不明」の印にしない）。</p>
 */

import {
  countWords,
  rubricMaxOf,
  type EssayCorrection,
  type EssayGrading,
  type EssayLevel,
  type EssayReportSide,
  type EssayRubricItem,
  type EssayRubricKey,
  type EssayTaskRequirements
} from './types'

/** 添削の 1 行（API の `gradings[]` の行の値 ＋ `report`）。 */
export interface GradingRowInput {
  gradingId: string
  /** その回の番号（API の `round`。1 から。**失敗した回も含めた**通し番号）。 */
  round?: number | null
  /** 行の級（無ければ `essayLevel`、それも無ければ準 1 級として扱う）。 */
  level?: string | null
  titleJa?: string | null
  titleZh?: string | null
  /** その回の作文の本文（語数を数え直すのに使う）。 */
  essayText?: string | null
  wordCount?: number | null
  score?: number | null
  maxScore?: number | null
  createdAt: string
  /** 2.0 と同形の JSON（文字列で返る実装もあるので、その場合も解析する）。 */
  report: unknown
  /** 作文の級（行にも JSON にも級が無いときの既定）。 */
  essayLevel?: EssayLevel
  /** 作文の題（行にも JSON にも題が無いときの埋め草）。 */
  essayTitle?: string
  essayTitleZh?: string
}

/** レポート JSON の形（無い項目は既定値で埋める）。 */
interface EssayReportJson {
  status?: string
  version?: string
  level?: string
  score?: number
  maxScore?: number
  rubricMax?: number
  rubricValues?: unknown
  modelAnswer?: string
  wordCount?: number
  /** 文字列（`"80〜100語"`）とオブジェクト（`{type, minimum, maximum, target}`）の 2 つの形がある。 */
  wordRequirement?: unknown
  /** 文字列の配列と、`commentJa` / `commentZh` を持つオブジェクトの 2 つの形がある。 */
  taskRequirements?: unknown
  warnings?: unknown
  titleJa?: string
  titleZh?: string
  provider?: string
  model?: string
  japanese?: unknown
  chinese?: unknown
}

/** 行の値と JSON を 1 つの添削結果にまとめる（**行の値を優先**）。 */
export function normalizeGradingReport(row: GradingRowInput): EssayGrading {
  const json = parseReport(row.report)
  const level = levelOf(row.level ?? text(json.level), row.essayLevel ?? 'PRE1')
  const rubricMax = numberOr(json.rubricMax, rubricMaxOf(level))
  return {
    // 回ごとの一意な id（画面の `key` と历次の切り替えに使う）
    id: `grading-${row.gradingId}`,
    // 何回目かは**行の値（API の round）をそのまま**使う（成功した回を数え直さない）
    round: numberOr(row.round, undefined),
    status: 'AI_GRADED',
    // 結果の形式の版（2.0 の `eiken-ai-v1` を引き継ぐ。「demo」は使わない）
    version: text(json.version) ?? 'eiken-ai-v1',
    level,
    titleJa: text(row.titleJa) ?? text(json.titleJa) ?? row.essayTitle ?? '',
    titleZh: text(row.titleZh) ?? text(json.titleZh) ?? row.essayTitleZh ?? '',
    wordCount: numberOr(row.wordCount ?? json.wordCount, countWords(row.essayText ?? '')),
    // 得点が分からない回は**省略する**（`0` を「不明」の印にしない。画面は `0 / 16` を出さない）
    score: numberOr(row.score ?? json.score, undefined),
    // 満点が分からないときは**省略する**（`0` を「不明」の印にしない。画面は点数だけを出す）
    maxScore: numberOr(row.maxScore ?? json.maxScore, undefined),
    rubricMax,
    modelAnswer: text(json.modelAnswer) ?? '',
    // 語数の目安は級ごとの 2.0 の表を**埋め草にはしない**（無ければ画面が出さない）
    wordRequirement: wordRequirementText(json.wordRequirement),
    taskRequirements: taskRequirementsOf(json.taskRequirements),
    warnings: stringArray(json.warnings),
    createdAt: row.createdAt,
    japanese: sideOf(json.japanese, json, rubricMax),
    chinese: sideOf(json.chinese, json, rubricMax)
  }
}

/**
 * `report` を読む（文字列なら解析する）。
 *
 * <p>壊れていても画面が空にならないように、読めなければ**空のレポート**にする。</p>
 */
function parseReport(value: unknown): EssayReportJson {
  if (typeof value === 'string') {
    try {
      return parseReport(JSON.parse(value) as unknown)
    } catch {
      return {}
    }
  }
  if (typeof value !== 'object' || value === null) {
    return {}
  }
  // 形はバックエンドの産出が規範。ここでは画面が使う項目だけを拾う
  return value as EssayReportJson
}

/** レポートの片側（日本語／中国語）。欠けている項目は空にする。 */
function sideOf(value: unknown, json: EssayReportJson, rubricMax: number): EssayReportSide {
  const side = objectOf(value)
  return {
    title: text(side.title) ?? '',
    summary: text(side.summary) ?? '',
    tags: stringArray(side.tags),
    rubric: rubricOf(side.rubric, json.rubricValues, side.rubricNotes, rubricMax),
    corrections: correctionsOf(side.corrections),
    advice: text(side.advice) ?? '',
    notice: text(side.notice) ?? ''
  }
}

/**
 * 4 観点を組み立てる。2 つの形に対応する。
 *
 * <ol>
 *   <li>観点が**オブジェクト**の配列（`{key, score, maxScore, note}`。見本データの形）</li>
 *   <li>観点が**名前**の配列 ＋ トップの `rubricValues`（点数）＋ `rubricNotes`（講評）＋ `rubricMax`
 *       （1 観点の満点。2.0 の移行データの新形式）</li>
 * </ol>
 *
 * <p>2 では**そろっている分だけ**作る（`rubricValues` が短ければその分は作らない）。</p>
 */
function rubricOf(keys: unknown, values: unknown, notes: unknown, rubricMax: number): EssayRubricItem[] {
  const keyList = Array.isArray(keys) ? keys : []
  if (keyList.some((item) => typeof item === 'object' && item !== null)) {
    const items: EssayRubricItem[] = []
    for (const entry of keyList) {
      const item = objectOf(entry)
      const key = text(item.key)
      if (key === null) {
        continue
      }
      items.push({
        key: key as EssayRubricKey,
        score: numberOr(item.score, 0),
        maxScore: numberOr(item.maxScore, rubricMax),
        note: text(item.note) ?? ''
      })
    }
    return items
  }

  const valueList = Array.isArray(values) ? values : []
  const noteList = Array.isArray(notes) ? notes : []
  const items: EssayRubricItem[] = []
  const count = Math.min(keyList.length, valueList.length)
  for (let index = 0; index < count; index += 1) {
    const key = text(keyList[index])
    const score = numberOr(valueList[index], null)
    if (key === null || score === null) {
      continue
    }
    items.push({
      key: key as EssayRubricKey,
      score,
      maxScore: rubricMax,
      // 講評が無い回もある（古い形式）。無ければ空のままにする
      note: text(noteList[index]) ?? ''
    })
  }
  return items
}

/**
 * 修正ポイント。
 *
 * <p>新形式は**配列の配列**（`[原文, 修正後, 分類, 理由]`）、見本はオブジェクトの配列。
 * どちらも読む。</p>
 */
function correctionsOf(value: unknown): EssayCorrection[] {
  if (!Array.isArray(value)) {
    return []
  }
  const items: EssayCorrection[] = []
  for (const entry of value) {
    if (Array.isArray(entry)) {
      const [original, corrected, category, reason] = entry
      items.push({
        original: text(original) ?? '',
        corrected: text(corrected) ?? '',
        category: text(category) ?? '',
        reason: text(reason) ?? ''
      })
      continue
    }
    const item = objectOf(entry)
    if (item.original === undefined && item.corrected === undefined) {
      continue
    }
    items.push({
      original: text(item.original) ?? '',
      corrected: text(item.corrected) ?? '',
      category: text(item.category) ?? '',
      reason: text(item.reason) ?? ''
    })
  }
  return items
}

function objectOf(value: unknown): Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {}
}

/** 空でない文字列だけを返す（空文字・空白だけ・文字列以外は null）。前後の空白は落とす。 */
function text(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null
  }
  const trimmed = value.trim()
  return trimmed.length === 0 ? null : trimmed
}

/**
 * 語数の目安（2.0 の移行データは**形が 2 種類**ある）。
 *
 * <ul>
 *   <li><b>文字列</b>: `"80〜100語"` → そのまま出す。</li>
 *   <li><b>オブジェクト</b>: `{"type":"range","target":null,"minimum":80,"maximum":100}` →
 *       `target` があればそれを、無ければ `minimum`／`maximum` から日本語で組み立てる。</li>
 * </ul>
 *
 * <p>読めない形は空文字（画面は目安を出さない）。</p>
 */
function wordRequirementText(value: unknown): string {
  const asText = text(value)
  if (asText !== null) {
    return asText
  }
  const item = objectOf(value)
  const target = numberOr(item.target, null)
  if (target !== null) {
    return `${target}語`
  }
  const minimum = numberOr(item.minimum, null)
  const maximum = numberOr(item.maximum, null)
  if (minimum !== null && maximum !== null) {
    return `${minimum}〜${maximum}語`
  }
  if (minimum !== null) {
    return `${minimum}語以上`
  }
  if (maximum !== null) {
    return `${maximum}語以内`
  }
  return ''
}

/**
 * 設問が求めていること（2.0 の移行データは**形が 2 種類**ある）。
 *
 * <ul>
 *   <li><b>配列</b>: 文字列の配列（画面は箇条書きで出す）。</li>
 *   <li><b>オブジェクト</b>: `commentJa` / `commentZh` の解説（`comment` だけの行もある）。
 *       画面は結果の言語に合わせて 1 段落で出す。</li>
 * </ul>
 *
 * <p>読めない形（空の配列・文字列の無いオブジェクト）は `null`（画面は節ごと出さない）。</p>
 */
function taskRequirementsOf(value: unknown): EssayTaskRequirements | null {
  if (Array.isArray(value)) {
    const items = value
      .map((item) => text(item))
      .filter((item): item is string => item !== null)
    return items.length === 0 ? null : { kind: 'list', items }
  }
  const item = objectOf(value)
  const shared = text(item.comment)
  const ja = text(item.commentJa) ?? shared
  const zh = text(item.commentZh) ?? shared
  if (ja === null && zh === null) {
    return null
  }
  return { kind: 'note', ja: ja ?? zh ?? '', zh: zh ?? ja ?? '' }
}

function numberOr(value: unknown, fallback: number): number
function numberOr(value: unknown, fallback: null): number | null
function numberOr(value: unknown, fallback: undefined): number | undefined
function numberOr(value: unknown, fallback: number | null | undefined): number | null | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

/** 級の値（未知の値は既定の級として扱う。画面が級名を出せなくなるのを避ける）。 */
function levelOf(value: string | null | undefined, fallback: EssayLevel): EssayLevel {
  return value === 'GRADE1' || value === 'PRE1' || value === 'GRADE2' ? value : fallback
}
