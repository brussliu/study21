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
    canManualRerun: false,
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
  it('2.0 と同じタブ名と順番を持ち、2.1 で足した「図形管理」「授業録音」が入る', () => {
    expect(BATCH_GROUP_ORDER).toEqual([
      '英語単語', '英単語問題生成', '英熟語', '日本語単語', '英作文',
      '英語穴埋め', '英語長文精読', '図形管理', '授業録音', 'AI共通・OCR', '学習モニター', 'システム'
    ])
  })

  it('AI生図のバッチは batC51-A〜D（AI 生成）と batC52（AI 画図助手）が「図形管理」に入る', () => {
    for (const taskCode of ['batC51-A', 'batC51-B', 'batC51-C', 'batC51-D', 'batC52']) {
      expect(groupTitleOf(row({ taskCode, pageCode: 'GEOMETRY_AI' }))).toBe('図形管理')
    }
    // 設定ページからの推定も効く（タスクコード表に無い将来のバッチの保険）
    expect(groupTitleOf(row({ taskCode: 'batC59', pageCode: 'GEOMETRY_AI' }))).toBe('図形管理')
  })

  it('廃止した batC53 と裸の batC51 はタスク表に無い（構成変更: AI 生成はモード別の 4 バッチ）', () => {
    // batC51-A〜D（AI 生成）と batC52（AI 画図助手）は表にある（batC52 は番号を再利用。消さない）
    expect(groupTitleOf(row({ taskCode: 'batC51-A', pageCode: 'GEOMETRY_AI' }))).toBe('図形管理')
    expect(groupTitleOf(row({ taskCode: 'batC52', pageCode: 'GEOMETRY_AI' }))).toBe('図形管理')
    // モードが無い時代の裸の batC51 は 2026-09-19 に削除した（表からも外す）
    expect(groupTitleOf(row({ taskCode: 'batC51', pageCode: null }))).toBe('その他')
    // 表から外した batC53 も、設定ページが分かれば「図形管理」に落ちる＝「その他」に行かない
    expect(groupTitleOf(row({ taskCode: 'batC53', pageCode: 'GEOMETRY_AI' }))).toBe('図形管理')
    // 設定ページが無い古い行でも落ちない（「その他」に入れて表示する）
    expect(groupTitleOf(row({ taskCode: 'batC53', pageCode: null }))).toBe('その他')
    // batC54 は使わない（表にも無い）
    expect(groupTitleOf(row({ taskCode: 'batC54', pageCode: null }))).toBe('その他')
  })

  it('古い実行履歴（batC53）が混ざっていてもタブ分けが落ちない', () => {
    const tabs = buildBatchTabs([
      row({ taskCode: 'batC51-A', pageCode: 'GEOMETRY_AI' }),
      row({ taskCode: 'batC52', pageCode: 'GEOMETRY_AI' }),
      row({ taskCode: 'batC53', pageCode: null })
    ])

    // 「すべて」に 3 件そのまま出る（コードは画面がそのまま表示する）
    expect(tabs.find((tab) => tab.key === 'all')?.rows.map((item) => item.taskCode))
      .toEqual(['batC51-A', 'batC52', 'batC53'])
    // 「図形管理」タブに 2 件、「その他」に 1 件（設定ページが分からない古い batC53）
    expect(tabs.find((tab) => tab.key === '図形管理')?.rows.map((item) => item.taskCode))
      .toEqual(['batC51-A', 'batC52'])
    expect(tabs.find((tab) => tab.key === 'その他')?.rows.map((item) => item.taskCode)).toEqual(['batC53'])
  })

  it('授業ノートの 2 バッチ（batC61/62）は「授業録音」に入る（「その他」に落ちない）', () => {
    for (const taskCode of ['batC61', 'batC62']) {
      expect(groupTitleOf(row({ taskCode, pageCode: 'CLASSROOM_AI' }))).toBe('授業録音')
    }
    // 設定ページからの推定も効く（タスクコード表に無い将来のバッチの保険）
    expect(groupTitleOf(row({ taskCode: 'batC69', pageCode: 'CLASSROOM_AI' }))).toBe('授業録音')
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
