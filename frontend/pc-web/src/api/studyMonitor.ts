import { HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * 学習状況モニター API（/api/user/study-monitor）。
 * user-api の StudyMonitorController と対応する。
 *
 * データは `MON_学習モニター*`（2.0 から移行済み。カメラ 1・動画 2,777・
 * スナップショット 43,326・分析 43,326 件）。取り込み（batL02）と AI 分析（batL03）は
 * 2.1 では未実装のため、ここは照会と手動修正だけ。
 */
export type AnalysisState = 'WAITING' | 'RUNNING' | 'COMPLETED' | 'ERROR'
export type AnalysisResultCode = 'STUDY_NO_PC' | 'STUDY_PC' | 'AWAY' | 'PC_NON_STUDY' | 'OTHER' | 'UNKNOWN'

export interface StudyMonitorVideo {
  videoId: number
  fileName: string
  /** ISO 形式の日時（YYYY-MM-DDTHH:mm:ss） */
  startedAt: string
  endedAt: string
  durationSeconds: number
  importState: 'WAITING' | 'IMPORTED' | 'ERROR'
  snapshotCount: number
  completedCount: number
}

export interface StudyMonitorSnapshot {
  snapshotId: number
  videoId: number
  videoFileName: string
  capturedAt: string
  imagePath: string
  width: number | null
  height: number | null
  analysisState: AnalysisState
  resultCode: AnalysisResultCode | null
  confidence: number | null
  reason: string | null
  manual: boolean
  version: number
}

export interface StudyMonitorSummary {
  videoCount: number
  snapshotCount: number
  completed: number
  waiting: number
  errors: number
  resultCounts: Record<string, number>
}

export interface StudyMonitorSearchResult {
  date: string
  timeFrom: string
  timeTo: string
  videos: StudyMonitorVideo[]
  snapshots: StudyMonitorSnapshot[]
  summary: StudyMonitorSummary
}

export interface StudyMonitorSearchQuery {
  date?: string
  timeFrom?: string
  timeTo?: string
  videoId?: number | null
  analysisState?: string
  result?: string
}

export interface StudyMonitorCorrectionResult {
  message: string
  updatedCount: number
}

const http = new HttpClient({ baseUrl: '/api/user' })

/** 対象日の動画セグメントとスナップショット（最新の分析つき）。 */
export function searchStudyMonitorSnapshots(
  query: StudyMonitorSearchQuery
): Promise<ApiResponse<StudyMonitorSearchResult>> {
  return http.get<StudyMonitorSearchResult>('/study-monitor/snapshots', {
    params: {
      date: query.date?.trim() || undefined,
      timeFrom: query.timeFrom?.trim() || undefined,
      timeTo: query.timeTo?.trim() || undefined,
      videoId: query.videoId ?? undefined,
      analysisState: query.analysisState?.trim() || undefined,
      result: query.result?.trim() || undefined
    }
  })
}

/** 判定結果を一括で修正する（修正理由は必須）。 */
export function correctStudyMonitorSnapshots(
  updates: { snapshotId: number; version: number }[],
  result: AnalysisResultCode,
  reason: string
): Promise<ApiResponse<StudyMonitorCorrectionResult>> {
  return http.patch<StudyMonitorCorrectionResult>('/study-monitor/snapshots', {
    body: { updates, result, reason }
  })
}

/** スナップショット画像の URL（画像が無いときは 404 になるので画面側で代替を出す）。 */
export function studyMonitorImageUrl(snapshotId: number): string {
  return `/api/user/study-monitor/snapshots/${snapshotId}/image`
}
