/**
 * 学習状況モニターのサンプルデータ（仮データ）。
 *
 * 2.0 の study_monitor.jsp は `api/study-monitor/snapshots` から
 * 「その日の動画と、そこから切り出したスナップショット（AI 分析の結果つき）」を取っていた。
 * 2.1 では動画取込（batL02）と AI 分析（batL03）が未実装のため、画面の動作確認用に
 * ここで同じ形の仮データを作る（API ができたら差し替える）。
 */
export type AnalysisResult = '学習中' | '休憩中' | 'ゲーム中' | '離席' | '判定不能'
export type AnalysisState = '分析済み' | '待機中' | 'エラー'

export interface MonitorSnapshot {
  id: number
  capturedTime: string
  analysisResult: AnalysisResult | null
  analysisState: AnalysisState
  confidence: number | null
  reason: string | null
}

export interface MonitorVideo {
  id: number
  fileName: string
  startedAt: string
  durationSeconds: number
  importedCount: number
  snapshots: MonitorSnapshot[]
}

export interface MonitorDay {
  date: string
  videos: MonitorVideo[]
}

export const ANALYSIS_RESULTS: AnalysisResult[] = ['学習中', '休憩中', 'ゲーム中', '離席', '判定不能']

/** 時刻の文字列（HH:MM:SS）を秒に。 */
export function timeToSeconds(value: string): number {
  const [h, m, s] = value.split(':').map(Number)
  return (h || 0) * 3600 + (m || 0) * 60 + (s || 0)
}

/** 秒を HH:MM に。 */
export function shortTime(seconds: number): string {
  const safe = Math.max(0, Math.round(seconds))
  return `${String(Math.floor(safe / 3600)).padStart(2, '0')}:${String(Math.floor((safe % 3600) / 60)).padStart(2, '0')}`
}

function snapshot(id: number, time: string, result: AnalysisResult | null, state: AnalysisState, confidence: number | null, reason: string | null): MonitorSnapshot {
  return { id, capturedTime: time, analysisResult: result, analysisState: state, confidence, reason }
}

/**
 * その日の動画とスナップショット。午前は学習、昼は休憩、夕方はゲームという
 * ありがちな 1 日にしてある（時間帯フィルターの効きが見えるように）。
 */
export function buildMockDay(date: string): MonitorDay {
  const videos: MonitorVideo[] = [
    {
      id: 1,
      fileName: '2026-07-25-1.mp4',
      startedAt: '08:00:00',
      durationSeconds: 4 * 3600,
      importedCount: 12,
      snapshots: [
        snapshot(101, '08:05:00', '学習中', '分析済み', 0.92, null),
        snapshot(102, '08:20:00', '学習中', '分析済み', 0.88, null),
        snapshot(103, '08:35:00', '学習中', '分析済み', 0.9, null),
        snapshot(104, '08:50:00', '学習中', '分析済み', 0.86, null),
        snapshot(105, '09:05:00', '学習中', '分析済み', 0.91, null),
        snapshot(106, '09:20:00', '休憩中', '分析済み', 0.74, '手にペンを持っていない'),
        snapshot(107, '09:35:00', '休憩中', '分析済み', 0.7, '手にペンを持っていない'),
        snapshot(108, '09:50:00', '学習中', '分析済み', 0.89, null),
        snapshot(109, '10:05:00', '学習中', '分析済み', 0.93, null),
        snapshot(110, '10:20:00', '判定不能', 'エラー', null, '画像が暗く判定できませんでした'),
        snapshot(111, '10:35:00', '学習中', '分析済み', 0.87, null),
        snapshot(112, '10:50:00', '学習中', '待機中', null, null)
      ]
    },
    {
      id: 2,
      fileName: '2026-07-25-2.mp4',
      startedAt: '12:00:00',
      durationSeconds: 3 * 3600,
      importedCount: 12,
      snapshots: [
        snapshot(201, '12:05:00', '休憩中', '分析済み', 0.81, null),
        snapshot(202, '12:20:00', '休憩中', '分析済み', 0.77, null),
        snapshot(203, '12:35:00', '離席', '分析済み', 0.95, '椅子に誰も座っていない'),
        snapshot(204, '12:50:00', '離席', '分析済み', 0.93, '椅子に誰も座っていない'),
        snapshot(205, '13:05:00', '学習中', '分析済み', 0.9, null),
        snapshot(206, '13:20:00', '学習中', '分析済み', 0.88, null),
        snapshot(207, '13:35:00', 'ゲーム中', '分析済み', 0.84, 'ゲーム画面を操作している'),
        snapshot(208, '13:50:00', 'ゲーム中', '分析済み', 0.86, 'ゲーム画面を操作している'),
        snapshot(209, '14:05:00', '学習中', '分析済み', 0.85, 'ノートを開いているが視線が定まっていない'),
        snapshot(210, '14:20:00', '学習中', '分析済み', 0.9, null),
        snapshot(211, '14:35:00', '学習中', '待機中', null, null),
        snapshot(212, '14:50:00', '学習中', '待機中', null, null)
      ]
    },
    {
      id: 3,
      fileName: '2026-07-25-3.mp4',
      startedAt: '16:00:00',
      durationSeconds: 2 * 3600,
      importedCount: 12,
      snapshots: [
        snapshot(301, '16:05:00', 'ゲーム中', '分析済み', 0.9, 'ゲーム画面を操作している'),
        snapshot(302, '16:20:00', 'ゲーム中', '分析済み', 0.88, 'ゲーム画面を操作している'),
        snapshot(303, '16:35:00', 'ゲーム中', '分析済み', 0.92, 'ゲーム画面を操作している'),
        snapshot(304, '16:50:00', '休憩中', '分析済み', 0.72, null),
        snapshot(305, '17:05:00', '学習中', '分析済み', 0.86, null),
        snapshot(306, '17:20:00', '学習中', '分析済み', 0.89, null),
        snapshot(307, '17:35:00', '学習中', '分析済み', 0.91, null),
        snapshot(308, '17:50:00', '学習中', '分析済み', 0.87, null),
        snapshot(309, '18:05:00', '離席', '分析済み', 0.94, '椅子に誰も座っていない'),
        snapshot(310, '18:20:00', '学習中', '分析済み', 0.9, null),
        snapshot(311, '18:35:00', '学習中', '分析済み', 0.88, null),
        snapshot(312, '18:50:00', '学習中', '分析済み', 0.85, null)
      ]
    }
  ]
  return { date, videos }
}
