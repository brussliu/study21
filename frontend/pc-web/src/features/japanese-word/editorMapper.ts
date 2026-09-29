import type {
  JpnCautionKind,
  JpnExampleLevel,
  JpnPracticeKind,
  JpnRelatedRelation,
  JpnWordDetailBody,
  JpnWordDetailResult,
  JpnWordEditorSave
} from '@/api/japanese'
import type { DemoWord, DemoDetailContent } from '@/features/japanese-demo/types'
import { emptyDetail } from './wordMapper'

/**
 * 保存の要求を組み立てる。
 *
 * バックエンドは**規範のキー**（`cautions[].kind`、`relatedWords[].relation` …）だけを読んで、
 * 11 段落から**新しい版**（`登録元コード='APP'`・`手修正フラグ=true`）を作る。
 * ここでは下書き（`DemoDetailContent`）をそのキーへ移し、語レベルの項目も同期する。
 *
 * 古い詳細の補助項目（語義の補足・例文の場面・コロケーションの例文・発音の盛り数）は
 * 画面に編集欄が無いので、**同じ位置の元の値を残す**（保存のたびに消えないようにする）。
 *
 * `contentVersion` は**編集を始めたときの版数**。読み直す前にほかが更新していれば 409 になる。
 */

/** 語義の番号を補う（サーバーの組み立てと同じ規則。例文・使用場面が番号で結び付く）。 */
function senseNumbers(content: DemoDetailContent): number[] {
  let next = 1
  return content.senses.map((sense) => {
    const number = sense.number === null || sense.number === undefined || sense.number < 1 ? next : sense.number
    next = number + 1
    return number
  })
}

function exampleLevel(level: string): JpnExampleLevel {
  return level === 'APPLIED' ? 'APPLIED' : 'BASIC'
}

function cautionKind(kind: string): JpnCautionKind {
  return kind === 'GRAMMAR' || kind === 'MEANING' || kind === 'PARTICLE' ? kind : 'UNNATURAL'
}

function relatedRelation(relation: string): JpnRelatedRelation | string {
  return relation === '対義語' || relation === '間違えやすい' || relation === '同じ読み' ? relation : '類義語'
}

function practiceKind(kind: string): JpnPracticeKind {
  return kind === 'PARTICLE' || kind === 'SYNONYM' || kind === 'WRITING' ? kind : 'SCENE'
}

/** 同じ位置の古い行（画面に編集欄が無い項目を残すため）。 */
function oldAt<T>(rows: T[] | undefined, index: number): T | undefined {
  return rows?.[index]
}

export function editorRequest(
  source: JpnWordDetailResult,
  draft: DemoWord,
  stateCode: string,
  note: string
): JpnWordEditorSave {
  const content = draft.detail ?? emptyDetail()
  const old: JpnWordDetailBody = source.detail?.detail ?? {}
  const numbers = senseNumbers(content)

  return {
    word: {
      word: draft.heading.trim(), reading: draft.reading.trim(), jlptLevel: draft.jlpt ?? undefined,
      partOfSpeech: draft.partOfSpeech, stateCode: stateCode as 'ACTIVE' | 'INACTIVE', note,
      version: source.word.version
    },
    contentVersion: source.detail?.contentVersion ?? 0,
    detail: {
      ...old,
      chineseMeaning: draft.chineseMeaning,
      coreMeaning: content.coreMeaning || draft.chineseMeaning,
      alternateReading: draft.alternateReading,
      descriptionJa: content.descriptionJa, descriptionZh: content.descriptionZh,
      jlptLevel: draft.jlpt, partOfSpeech: draft.partOfSpeech, manuallyCorrected: true,
      // 旧キー（編集した 11 欄の写し）。古い画面・古いデータのために残す
      editorContent: JSON.parse(JSON.stringify(content)) as DemoDetailContent,
      // 11 の段落は規範のキーで送る（サーバーが読むのはこのキーだけ）
      senses: content.senses.map((item, index) => ({
        number: numbers[index] ?? index + 1,
        japanese: item.japanese, chinese: item.chinese, context: item.context, style: item.style,
        // 語義の補足は画面に編集欄が無いので、同じ位置の元の値を残す
        noteJapanese: oldAt(old.senses, index)?.noteJapanese ?? null,
        noteChinese: oldAt(old.senses, index)?.noteChinese ?? null
      })),
      examples: content.examples.map((item) => ({
        japanese: item.japanese, reading: item.reading, chinese: item.chinese,
        senseNumber: item.senseNumber, source: item.source, level: exampleLevel(item.level)
      })),
      patterns: content.patterns.map((item) => ({
        pattern: item.pattern, reading: item.reading, chinese: item.chinese,
        example: item.example, exampleChinese: item.exampleChinese
      })),
      dialogs: content.dialogs.map((dialog) => ({
        scene: dialog.scene,
        lines: dialog.lines.map((line) => ({
          speaker: line.speaker, japanese: line.japanese, chinese: line.chinese
        }))
      })),
      synonyms: content.synonyms.map((item) => ({
        heading: item.heading, reading: item.reading, chinese: item.chinese,
        shared: item.shared, difference: item.difference, scene: item.usage
      })),
      cautions: content.cautions.map((item) => ({
        kind: cautionKind(item.kind), title: item.title,
        wrong: item.wrong, correct: item.correct, reason: item.reason
      })),
      conjugations: content.conjugations.map((item) => ({
        form: item.form, value: item.value, example: item.example
      })),
      transitivityPair: content.transitivityPair,
      pronunciation: content.pronunciation,
      // 旧 API 互換の配列。読みとアクセントは下書き、盛り数・音声は元の値を残す
      pronunciations: content.pronunciation === null
        ? []
        : [{
            ...(oldAt(old.pronunciations, 0) ?? { moraCount: null, audioUrl: null, audioProvider: null }),
            reading: content.pronunciation.reading,
            accentType: content.pronunciation.accentType,
            accentNotation: content.pronunciation.accentNotation
          }],
      collocations: content.collocations.map((item, index) => ({        expression: item.expression, reading: item.reading, chinese: item.chinese,
        usage: item.usage,
        exampleJapanese: oldAt(old.collocations, index)?.exampleJapanese ?? '',
        exampleChinese: oldAt(old.collocations, index)?.exampleChinese ?? ''
      })),
      relatedWords: content.relatedWords.map((item) => ({
        relation: relatedRelation(item.relation), heading: item.heading,
        reading: item.reading, chinese: item.chinese
      })),
      usageNotes: content.usageNotes.map((item) => ({
        senseNumber: item.senseNumber, register: item.register,
        politeness: item.politeness, audience: item.audience, note: item.note
      })),
      memoryHint: content.memoryHint,
      practices: content.practices.map((item) => ({
        kind: practiceKind(item.kind), question: item.question, questionChinese: item.questionChinese,
        choices: item.choices, freeWriting: item.freeWriting, answer: item.answer,
        explanation: item.explanation
      }))
    }
  }
}
