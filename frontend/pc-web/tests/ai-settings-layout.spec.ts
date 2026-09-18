import { describe, expect, it } from 'vitest'
import {
  AI_TABS,
  aiIsFullWidth,
  aiLabelOf,
  aiSliderOf,
  aiTabOf,
  aiTabsOf,
  normalizeAiFields,
  sortAiFields,
  type AiFieldLike
} from '@/features/system-settings/aiSettingsLayout'

/**
 * AI 設定ブロックの共通レイアウト（TAB・名称・並び・スライダー）。
 *
 * 画面の設定項目は「日本語単語AI」を基準に統一する。ここの規則が唯一の定義で、
 * 設定ランタイム（study2SettingRuntime）と、独立コンポーネント
 * （GeometryAiSettingsSection / ClassroomAiSettingsSection）が同じものを使う。
 */
describe('AI 設定の共通レイアウト', () => {
  it('TAB は 基本設定 / System Prompt / User Prompt / その他 の 4 つで順序も固定', () => {
    expect(AI_TABS).toEqual(['基本設定', 'System Prompt', 'User Prompt', 'その他'])
  })

  it('AI 呼び出しに関する設定は「基本設定」へ入る', () => {
    // モデル（スロット選択）
    for (const key of [
      'c04AiModel', 'c05AiModel', 'c042AiProvider', 'wordDetailAiProvider',
      'essayOcrAiProvider', 'intensiveMethodBOcrProvider', 'monitorFirstAiProvider',
      'classroomAiNoteProvider', 'classroomAiSttProvider', 'geometryAiProvider', 'geometryAiAssistProvider'
    ]) {
      expect(aiTabOf(key), key).toBe('基本設定')
    }
    // タイムアウト・Temperature・最大出力Token数・出力形式・コマンド数の上限
    for (const key of [
      'c042RequestTimeoutSeconds', 'intensiveOcrTimeoutSeconds', 'monitorAiTimeoutSeconds',
      'classroomAiNoteTimeoutSeconds', 'classroomAiSttTimeoutSeconds', 'geometryAiAssistTimeoutSeconds',
      'wordDetailAiTemperature', 'geometryAiTemperature',
      'wordDetailAiMaxCompletionTokens', 'classroomAiNoteMaxCompletionTokens',
      'geometryAiOutputFormat'
    ]) {
      expect(aiTabOf(key), key).toBe('基本設定')
    }
  })

  it('プロンプトは System Prompt / User Prompt へ分かれる', () => {
    expect(aiTabOf('c04SystemPromptZh')).toBe('System Prompt')
    expect(aiTabOf('c05PromptZh')).toBe('System Prompt')
    expect(aiTabOf('essayGradingPrompt')).toBe('System Prompt')
    expect(aiTabOf('intensiveOcrMethodAStructureSystemPrompt')).toBe('System Prompt')
    expect(aiTabOf('c04UserPromptZh')).toBe('User Prompt')
    expect(aiTabOf('classroomAiSummaryUserPrompt')).toBe('User Prompt')
    // 名前は UserPrompt を含まないが、AI への指示テンプレート＝User Prompt
    expect(aiTabOf('geometryAiInstructionTemplate')).toBe('User Prompt')
  })

  it('AI 以外の設定は「その他」へ入る', () => {
    for (const key of [
      'c042RetryLimit', 'intensiveOcrMaxRetries', 'clozeExplanationThreads', 'wordDetailAiThreads',
      'geometryAiMaxConcurrency', 'clozeMaxImages', 'essayMaxImageMb', 'intensiveOcrMaxImagePixels',
      'clozeExplanationBatchMax', 'monitorAiBatchLimit', 'monitorAiImageResolution',
      'geometryAiRetryLimit', 'geometryAiDailyLimitPerAccount', 'geometryAiApproval',
      'classroomAiChunkSeconds',
      // コマンド数の上限は「1 回の実行で受け付ける数」＝ AI の設定ではなく実行条件
      'geometryAiAssistMaxCommands'
    ]) {
      expect(aiTabOf(key), key).toBe('その他')
    }
    // 話者分離は AI（STT）のオプションなので基本設定
  })

  it('名称は統一する（旧名のゆらぎを吸収する）', () => {
    expect(aiLabelOf('c05AiModel')).toBe('使用モデル')
    expect(aiLabelOf('intensiveMethodAArticleOcrProvider')).toBe('使用モデル')
    expect(aiLabelOf('geometryAiProvider')).toBe('使用モデル')
    expect(aiLabelOf('c042RequestTimeoutSeconds')).toBe('リクエストタイムアウト')
    expect(aiLabelOf('classroomAiSttTimeoutSeconds')).toBe('リクエストタイムアウト')
    expect(aiLabelOf('classroomAiNoteMaxCompletionTokens')).toBe('最大出力Token数')
    expect(aiLabelOf('c042RetryLimit')).toBe('最大再実行回数')
    expect(aiLabelOf('geometryAiMaxConcurrency')).toBe('スレッド数')
    expect(aiLabelOf('clozeMaxImages')).toBe('最大画像枚数')
    expect(aiLabelOf('geometryAiMaxImageMb')).toBe('画像1枚の最大サイズ')
    expect(aiLabelOf('geometryAiMaxImagePixels')).toBe('AI送信画像の最大辺')
    expect(aiLabelOf('c042BatchSize')).toBe('1回の最大処理数')
    expect(aiLabelOf('clozeExplanationBatchMax')).toBe('1回の最大処理数')
    expect(aiLabelOf('monitorAiBatchLimit')).toBe('1回の最大処理数')
    // 名称を変えない項目は今のラベルのまま
    expect(aiLabelOf('geometryAiMaxCommands')).toBeUndefined()
    expect(aiLabelOf('classroomAiSttApiKey')).toBeUndefined()
  })

  it('モデル／プロバイダーの選択は 1 行（full）で出す', () => {
    // runtime も コンポーネントも同じ規則を使う（長いモデル名でも見切れないように）
    for (const key of [
      'c04AiModel', 'c25AiProvider', 'intensiveMethodAArticleOcrProvider', 'intensiveMethodBOcrProvider',
      'geometryAiProvider', 'geometryAiAssistProvider', 'classroomAiNoteProvider', 'classroomAiSttProvider'
    ]) {
      expect(aiIsFullWidth(key), key).toBe(true)
    }
    for (const key of [
      'c25RequestTimeoutSeconds', 'c25Temperature', 'c25MaxCompletionTokens', 'c25BatchMax',
      'geometryAiOutputFormat', 'classroomAiSttModel', 'classroomAiSttEndpoint', 'classroomAiSttApiKey'
    ]) {
      expect(aiIsFullWidth(key), key).toBe(false)
    }
  })

  it('数字項目はスライダー（step と単位の suffix）にする', () => {
    expect(aiSliderOf('c042RequestTimeoutSeconds')).toEqual({ step: 30, suffix: 's' })
    expect(aiSliderOf('wordDetailAiTemperature')).toEqual({ step: 0.1, suffix: '' })
    expect(aiSliderOf('wordDetailAiMaxCompletionTokens')).toEqual({ step: 512, suffix: '' })
    expect(aiSliderOf('c042RetryLimit')).toEqual({ step: 1, suffix: '回' })
    expect(aiSliderOf('c042Threads')).toEqual({ step: 1, suffix: '' })
    expect(aiSliderOf('clozeMaxImages')).toEqual({ step: 1, suffix: '枚' })
    expect(aiSliderOf('essayMaxImageMb')).toEqual({ step: 1, suffix: 'MB' })
    expect(aiSliderOf('intensiveOcrMaxImagePixels')).toEqual({ step: 512, suffix: 'px' })
    // 単位はブロックごとの数え方に合わせる
    expect(aiSliderOf('c042BatchSize')).toEqual({ step: 10, suffix: '語' })
    expect(aiSliderOf('c23BatchSize')).toEqual({ step: 10, suffix: '語' })
    expect(aiSliderOf('intensiveExplanationBatchSize')).toEqual({ step: 10, suffix: '文' })
    expect(aiSliderOf('clozeExplanationBatchMax')).toEqual({ step: 10, suffix: '問' })
    // 数字ではない項目はそのまま
    expect(aiSliderOf('c042AiProvider')).toBeUndefined()
    expect(aiSliderOf('c042SystemPrompt')).toBeUndefined()
  })

  it('ブロックの項目を TAB・名称・並び・スライダーへ揃える', () => {
    const fields = normalizeAiFields<AiFieldLike>([
      { key: 'c042RetryLimit', label: '最大再実行回数', type: 'number', min: 0, max: 5 },
      { key: 'c042UserPrompt', label: 'User Prompt', type: 'textarea' },
      { key: 'c042Temperature', label: 'Temperature', type: 'range', min: 0, max: 2 },
      { key: 'c042AiProvider', label: 'AIモデル', type: 'ai-model-dropdown' },
      { key: 'c042SystemPrompt', label: 'System Prompt', type: 'textarea' },
      { key: 'c042Threads', label: 'スレッド数', type: 'range', min: 1, max: 20 },
      { key: 'c042RequestTimeoutSeconds', label: 'タイムアウト', type: 'number', min: 30, max: 1800 }
    ])

    // TAB は 基本設定 →（モデル → タイムアウト → Temperature）の順
    expect(fields.map((field) => [field.tab, field.key])).toEqual([
      ['基本設定', 'c042AiProvider'],
      ['基本設定', 'c042RequestTimeoutSeconds'],
      ['基本設定', 'c042Temperature'],
      ['System Prompt', 'c042SystemPrompt'],
      ['User Prompt', 'c042UserPrompt'],
      ['その他', 'c042RetryLimit'],
      ['その他', 'c042Threads']
    ])
    // モデルの選択は 1 行（full）、それ以外は 2 列のまま
    expect(fields[0].full).toBe(true)
    expect(fields[1].full).toBeUndefined()
    // 名称の統一
    expect(fields[0].label).toBe('使用モデル')
    expect(fields[1].label).toBe('リクエストタイムアウト')
    // 数字はスライダー化（number → range、step・単位も付く）
    expect(fields[1].type).toBe('range')
    expect(fields[1].step).toBe(30)
    expect(fields[1].suffix).toBe('s')
    expect(fields[5].type).toBe('range')
    expect(fields[5].suffix).toBe('回')
  })

  it('項目の無い TAB は出さず、並びは固定', () => {
    expect(aiTabsOf([{ key: 'c05AiModel' }, { key: 'c05PromptZh' }])).toEqual(['基本設定', 'System Prompt'])
    expect(aiTabsOf([{ key: 'c042UserPrompt' }, { key: 'c042AiProvider' }])).toEqual(['基本設定', 'User Prompt'])
    expect(aiTabsOf([])).toEqual([])
  })

  it('sortAiFields は TAB と項目の順に並べ替える（TAB や名称は変えない）', () => {
    const fields = [
      { key: 'c042Threads', tab: 'その他' },
      { key: 'c042SystemPrompt', tab: 'System Prompt' },
      { key: 'c042RequestTimeoutSeconds', tab: '基本設定' },
      { key: 'c042AiProvider', tab: '基本設定' },
      { key: 'c042RetryLimit', tab: 'その他' }
    ]

    expect(sortAiFields(fields).map((field) => field.key)).toEqual([
      'c042AiProvider',
      'c042RequestTimeoutSeconds',
      'c042SystemPrompt',
      'c042RetryLimit',
      'c042Threads'
    ])
  })
})
