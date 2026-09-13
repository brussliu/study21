import { describe, expect, it } from 'vitest'
import { BATCH_GROUP_ORDER, buildBatchTabs, groupTitleOf } from '@/features/batch/batchGroups'
import type { BatchTaskRow } from '@/api/batch'

/**
 * バッチ管理のタブ分け（2.0 のバッチ管理画面から引き継いだグルーピング）。
 *
 * 所属はタスクコードで決める（同じ設定ページでも別のタブに入るタスクがあるため）。
 * 表に無い新しいコードは設定ページから推定し、決まらなければ「その他」に入れる。
 */
function row(overrides: Partial<BatchTaskRow> = {}): BatchTaskRow {
  return {
    taskCode: 'batC01',
    taskType: 'C',
    description: '説明',
    active: false,
    activeVersion: 1,
    lastRunAt: null,
    canToggleActive: false,
    canRerun: false,
    runsOnStartup: false,
    loopEveryMinutes: null,
    minuteOfHour: null,
    pageCode: 'TRANSLATION',
    requiredSettings: [],
    settingsComplete: true,
    missingSettings: [],
    latestStatus: null,
    latestStartTime: null,
    latestEndTime: null,
    latestMessage: null,
    running: false,
    ...overrides
  }
}

describe('バッチのタブ分け', () => {
  it('2.0 と同じタブ名と順番を持つ', () => {
    expect(BATCH_GROUP_ORDER).toEqual([
      '英語単語', '英単語問題生成', '英熟語', '日本語単語', '英作文',
      '英語穴埋め', '英語長文精読', 'AI共通・OCR', '学習モニター', 'システム'
    ])
  })

  it('タスクコードで所属が決まる（原型と同じ）', () => {
    expect(groupTitleOf(row({ taskCode: 'batC01', pageCode: 'TRANSLATION' }))).toBe('英語単語')
    expect(groupTitleOf(row({ taskCode: 'batC10', pageCode: 'AI_MODEL' }))).toBe('英単語問題生成')
    expect(groupTitleOf(row({ taskCode: 'batC31', pageCode: 'ENGLISH_PHRASE_DETAIL_AI' }))).toBe('英熟語')
    expect(groupTitleOf(row({ taskCode: 'batS01', pageCode: 'SYSTEM' }))).toBe('システム')
    // 改名前のコードは使わない（batL01 は batS01 になった）
    expect(groupTitleOf(row({ taskCode: 'batL02', pageCode: 'STUDY_MONITOR' }))).toBe('学習モニター')
  })

  it('表に無いコードは設定ページから推定し、決まらなければ「その他」', () => {
    expect(groupTitleOf(row({ taskCode: 'batC99', pageCode: 'STUDY_MONITOR' }))).toBe('学習モニター')
    expect(groupTitleOf(row({ taskCode: 'batC99', pageCode: 'UNKNOWN_PAGE' }))).toBe('その他')
    expect(groupTitleOf(row({ taskCode: 'batC99', pageCode: null }))).toBe('その他')
  })

  it('「すべて」が先頭で、空のグループは出さない', () => {
    const tabs = buildBatchTabs([
      row({ taskCode: 'batS01', pageCode: 'SYSTEM' }),
      row({ taskCode: 'batR02', pageCode: 'SYSTEM' }),
      row({ taskCode: 'batC04', pageCode: 'WORD_QUESTION' })
    ])

    expect(tabs.map((tab) => tab.title)).toEqual(['すべて', '英単語問題生成', 'システム'])
    expect(tabs[0].rows).toHaveLength(3)
    expect(tabs.find((tab) => tab.title === 'システム')?.rows.map((r) => r.taskCode))
      .toEqual(['batS01', 'batR02'])
  })

  it('未知のグループは「その他」として最後に置く', () => {
    const tabs = buildBatchTabs([
      row({ taskCode: 'batC99', pageCode: 'UNKNOWN_PAGE' }),
      row({ taskCode: 'batS01', pageCode: 'SYSTEM' })
    ])

    expect(tabs.map((tab) => tab.title)).toEqual(['すべて', 'システム', 'その他'])
  })
})
