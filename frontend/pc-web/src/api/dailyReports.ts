import { HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * 学習日報 API（/api/user/daily-reports）。対象はログイン中の本人。
 *
 * 画面 `views/daily-report/DailyReportView.vue` は次の見方で使う:
 *   月カレンダー（status）／週の時間割（timetable）／1 日の詳細（day）／記録の保存と提出
 *
 * 記載あり（DRAFT）と提出済（SUBMITTED）を分けて持つ。提出後に編集すると下書きに
 * 戻り（＝再提出待ち）、もう一度【提出】で確定する。
 */
export type SubmitStatusCode = 'DRAFT' | 'SUBMITTED'

/**
 * その日の区分。NORMAL=通常 / HOLIDAY=祝日 / REST=休日。
 * 祝日・休日にするとその日の授業は消え、未提出にも提出率にも数えない。
 */
export type HolidayType = 'NORMAL' | 'HOLIDAY' | 'REST'

export const HOLIDAY_LABELS: Record<HolidayType, string> = {
  NORMAL: '通常',
  HOLIDAY: '祝日',
  REST: '休日'
}

export interface DayStatus {
  date: string
  /** 1=月 … 7=日 */
  weekday: number
  hasReport: boolean
  lessonCount: number
  averageMastery: number | null
  noteCount: number
  /** DRAFT=記載あり（未提出・再提出待ち）/ SUBMITTED=提出済 */
  submitStatus: SubmitStatusCode
  submitted: boolean
  /** 最後に提出した日時（未提出なら null） */
  submittedAt: string | null
  /** その日に記録した教科（時限順。カレンダーの色分けに使う） */
  subjects: string[]
  /** 内容か掌握度が入っている授業の数 */
  detailedLessonCount: number
  /** その日の振り返り（未入力なら null。未展開のマスにも出す） */
  review: string | null
  /** 今夜の勉強内容（未入力なら null。未展開のマスにも出す） */
  homework: string | null
  /** NORMAL / HOLIDAY / REST */
  holidayType: HolidayType
}

/** 祝日・休日か（未提出にも提出率にも数えない）。 */
export function isRestDay(status: Pick<DayStatus, 'holidayType'>): boolean {
  return status.holidayType === 'HOLIDAY' || status.holidayType === 'REST'
}

export interface StatusResult {
  from: string
  to: string
  days: DayStatus[]
  /** 期間内の平日の数（祝日は考慮しない） */
  schoolDays: number
  /** 記録がある日（下書きを含む） */
  reportDays: number
  /** 提出済の日（提出率はこれで数える） */
  submittedDays: number
  /** 提出したことがあるが、編集で下書きに戻った日 */
  reopenedDays: number
  /** 祝日・休日として指定された日（提出率の分母から外す） */
  restDays: number
}

export interface TimetableCell {
  weekday: number
  period: number
  subject: string
  /** その教科が記録された回数 */
  appearances: number
  /** その曜日・時限に記録があった日数 */
  days: number
}

export interface TimetableResult {
  weekdays: number[]
  periods: number[]
  cells: TimetableCell[]
}

export interface DayLesson {
  period: number
  subject: string
  content: string | null
  mastery: number | null
  noteRecorded: boolean
  /** ノートを記録しなかった理由（記録したときは null） */
  noteSkippedReason: string | null
  /** この授業の自己評価（1〜5） */
  concentration: number | null
  studyVolume: number | null
  attitude: number | null
}

export interface DaySubject {
  order: number
  subject: string
  content: string | null
}

export interface DayDetail {
  date: string
  hasReport: boolean
  submitStatus: SubmitStatusCode | null
  submitted: boolean
  submittedAt: string | null
  /** NORMAL / HOLIDAY / REST */
  holidayType: HolidayType
  review: string | null
  concentration: number | null
  understanding: number | null
  studyVolume: number | null
  attitude: number | null
  homework: string | null
  note: string | null
  lessons: DayLesson[]
  subjects: DaySubject[]
}

const http = new HttpClient({ baseUrl: '/api/user' })

export function fetchReportStatus(from: string, to: string): Promise<ApiResponse<StatusResult>> {
  return http.get<StatusResult>('/daily-reports/status', { params: { from, to } })
}

export function fetchTimetable(): Promise<ApiResponse<TimetableResult>> {
  return http.get<TimetableResult>('/daily-reports/timetable')
}

export function fetchReportDay(date: string): Promise<ApiResponse<DayDetail>> {
  return http.get<DayDetail>('/daily-reports/day', { params: { date } })
}

export interface WeekLesson {
  period: number
  subject: string
  content: string | null
  mastery: number | null
  noteRecorded: boolean
  noteSkippedReason: string | null
  concentration: number | null
  studyVolume: number | null
  attitude: number | null
  /** false = 過去の日報から推定しただけ（まだ記録が無い） */
  recorded: boolean
}

export interface WeekDay {
  date: string
  weekday: number
  hasReport: boolean
  /** 記録が無い日は null */
  submitStatus: SubmitStatusCode | null
  submitted: boolean
  /** 最後に提出した日時（未提出なら null） */
  submittedAt: string | null
  review: string | null
  concentration: number | null
  understanding: number | null
  studyVolume: number | null
  attitude: number | null
  homework: string | null
  /** NORMAL / HOLIDAY / REST */
  holidayType: HolidayType
  /** 記録した教科（時限順） */
  subjects: string[]
  lessons: WeekLesson[]
}

export interface WeekResult {
  from: string
  to: string
  days: WeekDay[]
}

/** 1 限ぶんの授業の保存（授業編集ダイアログの 9 項目のうち、日付・時限も含む）。 */
export interface LessonSaveRequest {
  date: string
  period: number
  subject: string
  /** 授業内容 */
  content?: string | null
  /** 掌握度（1〜5） */
  mastery?: number | null
  /** ノート記録（主科だけ選べる。ラジオの値） */
  noteRecorded?: boolean
  /** ノートを記録しなかった理由（noteRecorded=false のときだけ） */
  noteSkippedReason?: string | null
  /** この授業の自己評価（1〜5） */
  concentration?: number | null
  studyVolume?: number | null
  attitude?: number | null
}

/** 1 日のまとめ（日まとめのダイアログ）。 */
export interface DaySummarySaveRequest {
  date: string
  holidayType: HolidayType
  /** 今日の振り返り */
  review?: string | null
  /** 今夜の勉強内容 */
  homework?: string | null
}

export interface DaySummaryResult {
  message: string
  date: string
  holidayType: HolidayType
  /** 祝日・休日にしたとき、消した授業の件数 */
  clearedLessons: number
}

export interface LessonSaveResult {
  message: string
  date: string
  period: number
  created: boolean
  /** 保存で下書きに戻った（＝再提出待ち）か */
  reopened: boolean
}

export interface SubmitResult {
  message: string
  date: string
  lessonCount: number
  submittedAt: string | null
}

/** 週表示（月〜金。記録が無い日は過去の日報から推定した時間割で埋まる）。 */
export function fetchReportWeek(date: string): Promise<ApiResponse<WeekResult>> {
  return http.get<WeekResult>('/daily-reports/week', { params: { date } })
}

/** 1 日のまとめを保存する（祝日・休日にすると、その日の授業は消える）。 */
export function saveDaySummary(body: DaySummarySaveRequest): Promise<ApiResponse<DaySummaryResult>> {
  return http.post<DaySummaryResult>('/daily-reports/day', { body })
}

/** 1 限ぶんの授業を保存する（授業をクリック、または【授業追加】から）。 */
export function saveLessonReport(body: LessonSaveRequest): Promise<ApiResponse<LessonSaveResult>> {
  return http.post<LessonSaveResult>('/daily-reports/lesson', { body })
}

/** 1 限ぶんの授業を消す（消した時限は詰めない＝空いたままにする）。 */
export function deleteLessonReport(date: string, period: number): Promise<ApiResponse<{ message: string, date: string, period: number }>> {
  return http.delete<{ message: string, date: string, period: number }>('/daily-reports/lesson', {
    params: { date, period }
  })
}

/**
 * その日を提出する（提出済にする）。
 * 記録が無い日と、すでに提出済みの日は 400 で返る。
 */
export function submitDailyReport(date: string): Promise<ApiResponse<SubmitResult>> {
  return http.post<SubmitResult>('/daily-reports/submit', { body: { date } })
}
