/**
 * ホーム画面のサンプルデータ（仮データ）。
 *
 * 2.0 の english.jsp（英語勉強）が出していた内容をホームにまとめるにあたり、
 * 実データの API がまだ無いため、見た目を確認できるようにここへ固定値で置いている。
 * 実装時は `home.ts`（API クライアント）に置き換える。
 */
export interface HomeKpi {
  id: string
  label: string
  value: string
  sub: string
  icon: string
  tone: 'primary' | 'info' | 'success' | 'warning'
}

export interface RadarAxis {
  axis: string
  /** 現在値（0〜100） */
  value: number
  /** 目標値（0〜100） */
  target: number
}

export interface StudyList {
  id: string
  title: string
  icon: string
  /** 主な数字（大きく出す） */
  main: string
  sub: string
  /** 進捗（0〜100、無ければ null） */
  progress: number | null
}

export const HOME_KPIS: HomeKpi[] = [
  { id: 'total', label: '総学習時間', value: '128時間30分', sub: '今月 +12時間20分', icon: 'clock', tone: 'primary' },
  { id: 'level', label: '学習レベル', value: 'Lv.7', sub: '次のレベルまで 320 pt', icon: 'sigma', tone: 'info' },
  { id: 'today', label: '今日の学習時間', value: '1時間15分', sub: '前日比 +25分', icon: 'play', tone: 'success' },
  { id: 'reading', label: '今日の読書時間', value: '22分', sub: '今週 合計 2時間5分', icon: 'book-open', tone: 'warning' },
  { id: 'streak', label: '連続学習日数', value: '14日', sub: '最長記録 21日', icon: 'check-circle', tone: 'success' }
]

/** レーダーチャート（初級・中級）の軸。2.0 と同じ 5 領域。 */
export const BEGINNER_RADAR: RadarAxis[] = [
  { axis: '単語', value: 82, target: 90 },
  { axis: '熟語', value: 64, target: 85 },
  { axis: '文法', value: 71, target: 85 },
  { axis: '読解', value: 58, target: 80 },
  { axis: 'リスニング', value: 46, target: 75 }
]

export const INTERMEDIATE_RADAR: RadarAxis[] = [
  { axis: '単語', value: 68, target: 85 },
  { axis: '熟語', value: 45, target: 80 },
  { axis: '文法', value: 62, target: 85 },
  { axis: '読解', value: 51, target: 80 },
  { axis: 'リスニング', value: 38, target: 70 }
]

export const HOME_LISTS: StudyList[] = [
  { id: 'word', title: '単語帳', icon: 'book', main: '1,240 語', sub: '覚えた 830 語', progress: 67 },
  { id: 'phrase', title: '熟語帳', icon: 'clipboard', main: '320 語', sub: '覚えた 210 語', progress: 66 },
  { id: 'word-test', title: '単語テスト', icon: 'check-square', main: '平均 82 点', sub: '受験 24 回 ・ 最高 96 点', progress: 82 },
  { id: 'phrase-test', title: '熟語テスト', icon: 'clipboard', main: '平均 78 点', sub: '受験 12 回 ・ 最高 92 点', progress: 78 },
  { id: 'status', title: '単語の学習状況', icon: 'grid', main: '710 / 1,240 語', sub: '未学習 410 ・ 学習中 120', progress: 57 },
  { id: 'reading', title: '長文読解', icon: 'book-open', main: '18 本', sub: '今月 4 本 ・ 平均正答 76%', progress: 76 }
]

/* ============================================================================
 * 直近単語テスト情報統計（2.0 english.jsp / javascripts の「直近単語テスト情報統計」）
 *
 * 2.0 は単語テストの履歴（日ごとの 単語数・テスト時間・中断回数）を 30 日ぶんの
 * 複合グラフ（棒＋折れ線＋バブル＋最低時間ライン）で描いていた。
 * 2.1 は単語テストのテーブルがまだ無いため、見た目の確認用に同じ形の仮データを作る。
 * ========================================================================== */
export interface WordTestDay {
  /** yyyy-MM-dd */
  date: string
  /** MM/dd */
  label: string
  /** 曜日（1 文字） */
  weekday: string
  /** 単語数（棒） */
  wordCount: number
  /** テスト時間（分。折れ線・右軸） */
  minutes: number
  /** 中断回数（バブル） */
  interruptions: number
  weekend: boolean
}

/** 2.0 の「最低時間」ライン（分）。 */
export const WORD_TEST_MINIMUM_MINUTES = 25

/** 直近 30 日ぶんの単語テスト（仮データ。日付から決まる固定値）。 */
export function buildWordTestDays(days = 30, today: Date = new Date()): WordTestDay[] {
  const weekdays = ['日', '月', '火', '水', '木', '金', '土']
  const result: WordTestDay[] = []
  for (let offset = days - 1; offset >= 0; offset -= 1) {
    const date = new Date(today.getFullYear(), today.getMonth(), today.getDate() - offset)
    const weekend = date.getDay() === 0 || date.getDay() === 6
    // 日曜は休み（0 件）、それ以外は日付から決まる 150〜420 語
    const seed = date.getDate()
    const wordCount = weekend && date.getDay() === 0 ? 0 : 150 + ((seed * 37) % 270)
    const minutes = wordCount === 0 ? 0 : Math.max(8, Math.round(wordCount / 12))
    const interruptions = wordCount === 0 ? 0 : 1 + (seed % 2)
    result.push({
      date: `${date.getFullYear()}-${`${date.getMonth() + 1}`.padStart(2, '0')}-${`${date.getDate()}`.padStart(2, '0')}`,
      label: `${`${date.getMonth() + 1}`.padStart(2, '0')}/${`${date.getDate()}`.padStart(2, '0')}`,
      weekday: weekdays[date.getDay()] ?? '',
      wordCount,
      minutes,
      interruptions,
      weekend
    })
  }
  return result
}
