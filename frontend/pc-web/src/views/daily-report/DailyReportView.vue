<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiError, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import StarRating from '@/components/ui/StarRating.vue'
import {
  HOLIDAY_LABELS,
  fetchReportDay,
  fetchReportStatus,
  deleteLessonReport,
  fetchReportWeek,
  isRestDay,
  saveDaySummary,
  saveLessonReport,
  submitDailyReport,
  type DayStatus,
  type HolidayType,
  type StatusResult,
  type WeekDay,
  type WeekLesson,
  type WeekResult
} from '@/api/dailyReports'
import {
  MINOR_SUBJECT_LEGEND,
  SUBJECT_LEGEND,
  isMainSubject,
  subjectClass
} from '@/features/daily-report/subjectColors'
import '@/features/daily-report/daily-report.css'

/**
 * 学習日報（2.0 の daily_report.jsp の機能を、設計は作り直した画面）。
 *
 * 操作の流れ:
 *   1. 月カレンダー … その日の様子がマスだけで分かる（提出状態・時限数・掌握度の平均・
 *      その日の教科を全部）。提出済=緑 / 記載あり=黄 / 未提出=グレー / 祝日・休日=休みの表示
 *   2. 日のマスをクリック … **その行が開き、各マスが縦に伸びて授業が並ぶ**。
 *      記録が無い日は過去の日報から推定した教科で埋める（「推定」と表示する）
 *   3. 授業のカードをクリック … **授業編集ダイアログ**（時限・教科・掌握度・授業内容・
 *      ノート記録（主科のみ）・ノート記載しない理由・学習集中度・学習量・学習態度）
 *   4. 展開したマスの下の【授業追加】【当日まとめ】【提出】
 *      … その日の授業を足し、まとめ（祝日/休日区分・振り返り・今夜の勉強内容）を書き、提出する
 *
 * 土日も平日と同じ機能を使える（土日にも授業があるため）。ただし予定が無い曜日は
 * 「未提出」と表示する（「授業なし」とは出さない）。日曜だけ幅を狭くするのは、
 * 当月に日曜の記録が 1 件も無いときだけ（あるときは 7 日とも同じ幅）。
 * 祝日・休日にした日だけは授業が無い日として扱い、未提出にも提出率の分母にも数えない。
 */
const WEEKDAY_LABELS = ['月', '火', '水', '木', '金', '土', '日']
const PERIOD_OPTIONS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
/** 入力補助（よく使う教科）。押すと教科欄に入る */
const QUICK_SUBJECTS = ['英語', '国語', '数学', '理科', '社会', '音楽', '美術', '体育', '技術', '家庭']

const toast = useToast()

const loading = ref(false)
const error = ref('')

const monthCursor = ref(startOfMonth(new Date()))

const monthStatus = ref<StatusResult | null>(null)
/** 展開している週の月曜（YYYY-MM-DD）。null = 閉じている */
const expandedMonday = ref<string | null>(null)
const expandedWeek = ref<WeekResult | null>(null)
/** 選択中の日（マスを押すと選ばれる。展開したマスのボタンはその日の対象） */
const selectedDate = ref<string>(toYmd(new Date()))

/** 日まとめダイアログ（祝日/休日区分・今日の振り返り・今夜の勉強内容） */
/** 授業の削除などの実行中フラグ */
const busy = ref(false)

const summaryOpen = ref(false)
const summaryBusy = ref(false)
const daySummary = reactive({
  date: '',
  holidayType: 'NORMAL' as HolidayType,
  review: '',
  homework: '',
  /** 祝日・休日にすると消える授業の件数（確認に出す） */
  lessonCount: 0,
  loading: false
})

/** ノート記載しない理由の候補（自由入力＋候補ボタン） */
const NOTE_REASON_SUGGESTIONS = ['時間が無かった', '板書が不要だった', '教科書を忘れた', '先生が指示しなかった']

/** 提出の確認ダイアログ */
const submitOpen = ref(false)
const submitBusy = ref(false)
/** 提出ダイアログに出す、その日の内容（読み込み中は null） */
const submitDetail = ref<import('@/api/dailyReports').DayDetail | null>(null)
const submitDetailLoading = ref(false)

/* ---------- ダイアログ（1 限ぶんの日報入力） ---------- */
const dialogOpen = ref(false)
const dialogBusy = ref(false)
const editing = reactive({
  date: '',
  period: 1,
  subject: '',
  /** 授業内容 */
  content: '',
  /** 掌握度（1〜5） */
  mastery: null as number | null,
  /** ノート記録（主科だけ選べる。ラジオ） */
  noteRecorded: false,
  /** ノートを記録しなかった理由 */
  noteSkippedReason: '',
  /** この授業の自己評価（1〜5） */
  concentration: null as number | null,
  studyVolume: null as number | null,
  attitude: null as number | null,
  /** まだ記録が無い（推定で開いた）かどうか */
  inferred: false,
  /** 【授業追加】から開いたか */
  adding: false
})

/** 掌握度の星（3 → ★★★☆☆）。 */
function stars(value: number | null): string {
  if (value === null || value <= 0) return ''
  return '★'.repeat(value) + '☆'.repeat(Math.max(0, 5 - value))
}

/**
 * 時限の選択肢（最大 10 限）。
 * **その日の日報に合わせて出す**（いま編集中の時限＋まだ使っていない時限）。
 * 例: 1〜6 限が記録済みの日で 1 限を開くと「1 / 7 / 8 / 9 / 10 限」。
 * 記録済みの時限は出さない（選ぶと別の授業を上書きしてしまうため）。
 * 推定（未記録）の時限は上書きにはならないので、選択肢からは外さない。
 */
const periodOptions = computed(() => {
  const day = weekDayOf(editing.date)
  const used = new Set(
    (day?.lessons ?? [])
      .filter((lesson) => lesson.recorded && lesson.period !== editing.period)
      .map((lesson) => lesson.period)
  )
  return PERIOD_OPTIONS.filter((period) => !used.has(period))
})

/** ノート記録を選べるのは主科だけ（副科はノートを取らないため）。 */
const noteSelectable = computed(() => isMainSubject(editing.subject))

/** ノート記録のラジオ（主科以外は「記録しない」に固定する）。 */
const noteValue = computed<'RECORDED' | 'SKIPPED'>({
  get: () => (editing.noteRecorded ? 'RECORDED' : 'SKIPPED'),
  set: (value) => {
    editing.noteRecorded = value === 'RECORDED'
    if (editing.noteRecorded) {
      editing.noteSkippedReason = ''
    }
  }
})

/* ---------- 日付ユーティリティ ---------- */
function toYmd(date: Date): string {
  return `${date.getFullYear()}-${`${date.getMonth() + 1}`.padStart(2, '0')}-${`${date.getDate()}`.padStart(2, '0')}`
}

function parseYmd(ymd: string): Date {
  const [year, month, day] = ymd.split('-').map(Number)
  return new Date(year, month - 1, day)
}

function startOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

function startOfWeek(date: Date): Date {
  const clone = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  clone.setDate(clone.getDate() - ((clone.getDay() + 6) % 7))
  return clone
}

function addDays(date: Date, amount: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + amount)
}

function weekdayOf(ymd: string): number {
  return ((parseYmd(ymd).getDay() + 6) % 7) + 1
}

function todayYmd(): string {
  return toYmd(new Date())
}

/* ---------- カレンダー ---------- */
/**
 * 当月に日曜の授業があるか（記録がある日曜が 1 日でもあれば true）。
 * あるときは 7 日とも同じ幅にする（無いときは日曜を狭くする）。
 * 土日も平日と同じ「授業がある日」として扱うので、未提出の判定には使わない。
 */
const sundayHasClass = computed(() =>
  (monthStatus.value?.days ?? []).some((day) => day.weekday === 7 && day.lessonCount > 0))

const statusByDate = computed(() => {
  const map = new Map<string, DayStatus>()
  for (const day of monthStatus.value?.days ?? []) {
    map.set(day.date, day)
  }
  return map
})

interface CalendarCell {
  ymd: string | null
  dayOfMonth: number | null
  weekday: number
  status: DayStatus | null
}

interface CalendarWeek {
  monday: string
  cells: CalendarCell[]
}

const calendarWeeks = computed<CalendarWeek[]>(() => {
  const first = monthCursor.value
  const gridStart = startOfWeek(first)
  const weeks: CalendarWeek[] = []
  for (let week = 0; week < 6; week += 1) {
    const cells: CalendarCell[] = []
    for (let index = 0; index < 7; index += 1) {
      const date = addDays(gridStart, week * 7 + index)
      const inMonth = date.getMonth() === first.getMonth()
      if (!inMonth) {
        cells.push({ ymd: null, dayOfMonth: null, weekday: index + 1, status: null })
        continue
      }
      const ymd = toYmd(date)
      cells.push({
        ymd,
        dayOfMonth: date.getDate(),
        weekday: index + 1,
        status: statusByDate.value.get(ymd) ?? null
      })
    }
    weeks.push({ monday: toYmd(addDays(gridStart, week * 7)), cells })
  }
  return weeks.filter((week, index) => index < 5 || week.cells.some((cell) => cell.ymd !== null))
})

const monthLabel = computed(() =>
  `${monthCursor.value.getFullYear()}年${monthCursor.value.getMonth() + 1}月`)

/**
 * 月のまとめ。提出率は **提出済の日** だけで数える
 * （保存しただけ＝記載ありの日は「提出した」ことにならない）。
 */
const summary = computed(() => {
  const status = monthStatus.value
  if (!status) {
    return { reportDays: 0, submittedDays: 0, reopenedDays: 0, schoolDays: 0, rate: 0 }
  }
  const rate = status.schoolDays === 0 ? 0 : Math.round((status.submittedDays / status.schoolDays) * 100)
  return {
    reportDays: status.reportDays,
    submittedDays: status.submittedDays,
    reopenedDays: status.reopenedDays,
    schoolDays: status.schoolDays,
    rate
  }
})

/** その日の指定した時限の授業（入力ダイアログの初期値に使う）。 */
function lessonAt(day: WeekDay, period: number) {
  return day.lessons.find((lesson) => lesson.period === period) ?? null
}

/** 展開中の週から、その日付のデータを引く（セルの中に授業を出すのに使う）。 */
function weekDayOf(ymd: string): WeekDay | null {
  return expandedWeek.value?.days.find((day) => day.date === ymd) ?? null
}

/* ---------- マスに出す情報（未展開でも分かるようにする） ---------- */
/** マスの状態（提出済 / 再提出待ち / 記載あり / 未提出 / 祝日・休日）。 */
function cellState(cell: CalendarCell): { code: string; label: string; badge: string } {
  const status = cell.status
  if (status === null) {
    // 記録が無い日は土日も平日と同じく「未提出」（土日にも授業があるため）
    return { code: 'missing', label: '未提出', badge: 'badge--neutral' }
  }
  if (isRestDay(status)) {
    // 祝日・休日は未提出にしない（提出率の分母からも外れている）
    // 祝日・休日は淡い赤（設計システムの badge--danger は薄い赤地に濃い赤文字）
    return { code: 'rest', label: HOLIDAY_LABELS[status.holidayType], badge: 'badge--danger' }
  }
  if (status.submitted) {
    return { code: 'submitted', label: '提出済', badge: 'badge--success' }
  }
  if (status.submittedAt !== null) {
    return { code: 'reopened', label: '再提出待ち', badge: 'badge--warning' }
  }
  return { code: 'draft', label: '記載あり', badge: 'badge--info' }
}

/**
 * マスに出す 1 行のサマリ（「n限 掌握 x.x」）。
 *
 * 記録が無い日（未提出）でも、週を開いていれば推定の時間割が分かるので同じ行を出す。
 * 行の有無で**カードの並びが 1 行ぶん上下にずれる**ため、提出済みの日と高さを揃える。
 * 祝日・休日も「0限」を出す（授業が無いことを件数で示す。ユーザーの指定）。
 */
function dayMeta(cell: CalendarCell): {
  lessonCount: number
  averageMastery: number | null
  noteCount: number
  inferred: boolean
} | null {
  const status = cell.status
  if (status !== null) {
    if (isRestDay(status)) {
      // 祝日・休日は授業が無いので 0 限（掌握度・ノートは出さない）
      return { lessonCount: 0, averageMastery: null, noteCount: 0, inferred: false }
    }
    return {
      lessonCount: status.lessonCount,
      averageMastery: status.averageMastery,
      noteCount: status.noteCount,
      inferred: false
    }
  }
  const ymd = cell.ymd
  if (ymd === null) {
    return null
  }
  const day = weekDayOf(ymd)
  if (weekOf(ymd) !== expandedMonday.value || day === null || day.lessons.length === 0) {
    return null
  }
  return { lessonCount: day.lessons.length, averageMastery: null, noteCount: 0, inferred: true }
}

/** その日が入る週の月曜（展開中の週かどうかの判定に使う）。 */
function weekOf(ymd: string | null): string | null {
  return ymd === null ? null : toYmd(startOfWeek(parseYmd(ymd)))
}

/**
 * 展開したマスの「時限の枠」。1 限から**その日の最後の時限**までを必ず並べ、
 * 記録が無い時限は `lesson: null`（空き）にする。
 *
 * 授業を消したときにカードを詰めてしまうと、消した時限より下が上にずれて
 * 「どの時限が空いたか」が分からなくなる（ユーザーの指定で詰めない）。
 */
interface LessonSlot {
  period: number
  lesson: WeekLesson | null
}

function lessonSlots(day: WeekDay | null): LessonSlot[] {
  if (day === null || day.lessons.length === 0) {
    return []
  }
  const byPeriod = new Map(day.lessons.map((lesson) => [lesson.period, lesson]))
  const last = Math.max(...day.lessons.map((lesson) => lesson.period))
  const slots: LessonSlot[] = []
  for (let period = 1; period <= last; period += 1) {
    slots.push({ period, lesson: byPeriod.get(period) ?? null })
  }
  return slots
}

/** その日を含む週を開く（まだ開いていなければ開く）。 */
async function expandWeek(ymd: string): Promise<void> {
  const monday = toYmd(startOfWeek(parseYmd(ymd)))
  if (expandedMonday.value !== monday) {
    expandedMonday.value = monday
  }
  await loadWeek(monday)
}

/** 選択中の日の週を開く。 */
async function expandSelectedWeek(): Promise<void> {
  await expandWeek(selectedDate.value)
}

function selectDay(ymd: string): void {
  selectedDate.value = ymd
  if (parseYmd(ymd).getMonth() !== monthCursor.value.getMonth()) {
    monthCursor.value = startOfMonth(parseYmd(ymd))
  }
}

/* ---------- 読み込み ---------- */
async function loadMonth(): Promise<void> {
  const year = monthCursor.value.getFullYear()
  const month = monthCursor.value.getMonth() + 1
  const last = new Date(year, month, 0).getDate()
  const from = `${year}-${`${month}`.padStart(2, '0')}-01`
  const to = `${year}-${`${month}`.padStart(2, '0')}-${`${last}`.padStart(2, '0')}`
  monthStatus.value = (await fetchReportStatus(from, to)).data
}

async function loadWeek(monday: string): Promise<void> {
  expandedWeek.value = (await fetchReportWeek(monday)).data
}

async function reload(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    await loadMonth()
    if (expandedMonday.value !== null) {
      await loadWeek(expandedMonday.value)
    }
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : '学習日報を取得できませんでした。'
  } finally {
    loading.value = false
  }
}

/** 日のマスをクリック: その行（週）が開いて課表になる。同じ週をもう一度押すと閉じる。 */
async function toggleWeek(ymd: string): Promise<void> {
  const monday = toYmd(startOfWeek(parseYmd(ymd)))
  selectDay(ymd)
  if (expandedMonday.value === monday) {
    expandedMonday.value = null
    expandedWeek.value = null
    return
  }
  loading.value = true
  try {
    expandedMonday.value = monday
    await loadWeek(monday)
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '週の課表を取得できませんでした。')
  } finally {
    loading.value = false
  }
}

function moveMonth(offset: number): void {
  const next = new Date(monthCursor.value.getFullYear(), monthCursor.value.getMonth() + offset, 1)
  monthCursor.value = next
  expandedMonday.value = null
  expandedWeek.value = null
  // 選択中の日は同じ日付のまま（月が変わったら 1 日に寄せる）
  if (parseYmd(selectedDate.value).getMonth() !== next.getMonth()) {
    selectedDate.value = toYmd(next)
  }
  void loadMonth()
}

function goToday(): void {
  const today = new Date()
  monthCursor.value = startOfMonth(today)
  selectedDate.value = toYmd(today)
  void reload()
}

/* ---------- 授業のマスをクリック → 入力ダイアログ ---------- */
async function openLesson(day: WeekDay, period: number): Promise<void> {
  const lesson = lessonAt(day, period)
  editing.date = day.date
  editing.period = period
  editing.subject = lesson?.subject ?? ''
  editing.content = lesson?.content ?? ''
  editing.mastery = lesson?.mastery ?? null
  // 記録済みなら保存されている値、これから書く（推定）なら「記録した」を既定にする
  editing.noteRecorded = lesson?.recorded === true ? lesson.noteRecorded : true
  editing.noteSkippedReason = lesson?.noteSkippedReason ?? ''
  editing.inferred = lesson !== null && !lesson.recorded
  editing.adding = false
  // 授業ごとの自己評価（推定で開いたときは未入力）
  editing.concentration = lesson?.concentration ?? null
  editing.studyVolume = lesson?.studyVolume ?? null
  editing.attitude = lesson?.attitude ?? null
  dialogOpen.value = true

  // 記録済みの授業なら、最新の内容を読み直して初期値にする
  if (day.hasReport) {
    try {
      const detail = (await fetchReportDay(day.date)).data
      const recorded = detail.lessons.find((item) => item.period === period)
      if (recorded) {
        editing.subject = recorded.subject
        editing.content = recorded.content ?? ''
        editing.mastery = recorded.mastery ?? null
        editing.noteRecorded = recorded.noteRecorded
        editing.noteSkippedReason = recorded.noteSkippedReason ?? ''
        editing.concentration = recorded.concentration ?? null
        editing.studyVolume = recorded.studyVolume ?? null
        editing.attitude = recorded.attitude ?? null
        editing.inferred = false
      }
    } catch {
      // 詳細が取れなくても入力は続けられる
    }
  }
}

/* ---------- 日まとめ（祝日/休日区分・今日の振り返り・今夜の勉強内容） ---------- */
async function openSummary(date: string): Promise<void> {
  summaryOpen.value = true
  daySummary.loading = true
  daySummary.date = date
  daySummary.holidayType = 'NORMAL'
  daySummary.review = ''
  daySummary.homework = ''
  daySummary.lessonCount = weekDayOf(date)?.lessons.filter((lesson) => lesson.recorded).length ?? 0
  try {
    const detail = (await fetchReportDay(date)).data
    daySummary.holidayType = detail.holidayType ?? 'NORMAL'
    daySummary.review = detail.review ?? ''
    daySummary.homework = detail.homework ?? ''
    daySummary.lessonCount = detail.lessons.length
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : 'この日の日報を取得できませんでした。')
  } finally {
    daySummary.loading = false
  }
}

/** 祝日・休日を選ぶと、その日の授業が消えることを伝える。 */
const summaryClearsLessons = computed(() =>
  daySummary.holidayType !== 'NORMAL' && daySummary.lessonCount > 0)

async function saveSummary(): Promise<void> {
  if (summaryBusy.value) return
  summaryBusy.value = true
  try {
    const response = await saveDaySummary({
      date: daySummary.date,
      holidayType: daySummary.holidayType,
      review: daySummary.review.trim() === '' ? null : daySummary.review.trim(),
      homework: daySummary.homework.trim() === '' ? null : daySummary.homework.trim()
    })
    toast.success(response.data.message)
    summaryOpen.value = false
    selectedDate.value = daySummary.date
    await expandSelectedWeek()
    await reload()
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '当日まとめを保存できませんでした。')
  } finally {
    summaryBusy.value = false
  }
}

/** 授業を 1 つ消す（消した時限は詰めず、そのまま空きにする）。 */
async function removeLesson(date: string, period: number, subject: string): Promise<void> {
  if (busy.value) {
    return
  }
  if (!window.confirm(`${period}限「${subject}」を削除します。よろしいですか？`)) {
    return
  }
  busy.value = true
  try {
    const response = await deleteLessonReport(date, period)
    toast.success(response.data.message)
    selectedDate.value = date
    await expandWeek(date)
    await reload()
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '授業を削除できませんでした。')
  } finally {
    busy.value = false
  }
}

/* ---------- 展開したマスの中の操作（授業の追加・まとめ・提出） ---------- */
/**
 * 【授業追加】: その日の、まだ記録が無い時限を初期値にして開く。
 * 推定の時間割があれば教科も埋めておく。
 */
async function addLesson(date: string): Promise<void> {
  const weekDay = weekDayOf(date)
  let period = 1
  let subject = ''
  if (weekDay !== null) {
    const used = new Set(weekDay.lessons.filter((lesson) => lesson.recorded).map((lesson) => lesson.period))
    const nextFree = weekDay.lessons.find((lesson) => !used.has(lesson.period))
    if (nextFree !== undefined) {
      period = nextFree.period
      subject = nextFree.subject
    } else {
      period = Math.min(10, Math.max(...used, 0) + 1)
    }
  }
  editing.date = date
  editing.period = period
  editing.subject = subject
  editing.content = ''
  editing.mastery = null
  // 既定は「記録した」。記録しないときだけ理由を書く
  editing.noteRecorded = true
  editing.noteSkippedReason = ''
  editing.inferred = false
  editing.concentration = null
  editing.studyVolume = null
  editing.attitude = null
  editing.adding = true
  dialogOpen.value = true
}

/** 【提出】: 確認ダイアログを開く（その日の内容を読み込んで見せる）。 */
async function openSubmit(date: string): Promise<void> {
  selectedDate.value = date
  submitOpen.value = true
  submitDetailLoading.value = true
  submitDetail.value = null
  try {
    submitDetail.value = (await fetchReportDay(date)).data
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : 'この日の日報を取得できませんでした。')
    submitOpen.value = false
  } finally {
    submitDetailLoading.value = false
  }
}

async function confirmSubmit(): Promise<void> {
  if (submitBusy.value) return
  submitBusy.value = true
  try {
    const response = await submitDailyReport(selectedDate.value)
    toast.success(response.data.message)
    submitOpen.value = false
    await expandWeek(selectedDate.value)
    await reload()
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '提出できませんでした。')
  } finally {
    submitBusy.value = false
  }
}

async function saveLesson(): Promise<void> {
  if (dialogBusy.value) return
  if (editing.subject.trim() === '') {
    toast.warning('教科名を入力してください。')
    return
  }
  dialogBusy.value = true
  try {
    const response = await saveLessonReport({
      date: editing.date,
      period: editing.period,
      subject: editing.subject.trim(),
      content: editing.content.trim() === '' ? null : editing.content.trim(),
      mastery: editing.mastery,
      // ノート記録は主科だけ。副科は「記録しない」で固定する
      noteRecorded: noteSelectable.value ? editing.noteRecorded : false,
      noteSkippedReason: noteSelectable.value && !editing.noteRecorded
        ? (editing.noteSkippedReason.trim() === '' ? null : editing.noteSkippedReason.trim())
        : null,
      concentration: editing.concentration,
      studyVolume: editing.studyVolume,
      attitude: editing.attitude
    })
    toast.success(response.data.message)
    dialogOpen.value = false
    // 保存した日が見えるように、その週を開いてから読み直す
    await expandSelectedWeek()
    await reload()
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '日報を保存できませんでした。')
  } finally {
    dialogBusy.value = false
  }
}

onMounted(() => {
  void reload()
})
</script>

<template>
  <div class="dr-page">
    <p v-if="error" class="alert alert--danger">{{ error }}</p>

    <div class="dr-toolbar">
      <button type="button" class="btn btn--secondary btn--sm" aria-label="前の月" @click="moveMonth(-1)">
        <AppIcon name="chevron-left" size="sm" />
      </button>
      <span class="dr-toolbar__month">{{ monthLabel }}</span>
      <button type="button" class="btn btn--secondary btn--sm" aria-label="次の月" @click="moveMonth(1)">
        <AppIcon name="chevron-right" size="sm" />
      </button>
      <button type="button" class="btn btn--secondary" @click="goToday">
        <AppIcon name="clock" size="sm" /> 今月
      </button>

      <span class="dr-toolbar__spacer" />
      <span class="dr-summary">
        <span class="dr-summary__rate">{{ summary.rate }}%</span>
        提出 {{ summary.submittedDays }} / 平日 {{ summary.schoolDays }} 日
        <span class="dr-summary__sub">
          記載あり {{ summary.reportDays }} 日
          <template v-if="summary.reopenedDays > 0">・再提出待ち {{ summary.reopenedDays }} 日</template>
        </span>
      </span>
    </div>

    <section class="card">
      <div class="card__body" :class="{ 'dr-calendar--all-equal': sundayHasClass }">
        <div class="dr-calendar__head">
          <span v-for="label in WEEKDAY_LABELS" :key="label">{{ label }}</span>
        </div>

        <template v-for="week in calendarWeeks" :key="week.monday">
          <div class="dr-calendar__week">
            <template v-for="(cell, cellIndex) in week.cells" :key="`${week.monday}-${cellIndex}`">
              <div
                v-if="cell.ymd"
                class="dr-day"
                :class="[
                  `dr-day--${cellState(cell).code}`,
                  {
                    'dr-day--today': cell.ymd === todayYmd(),
                    'dr-day--selected': cell.ymd === selectedDate,
                    'dr-day--expanded': week.monday === expandedMonday
                  }
                ]"
                :data-date="cell.ymd"
                :data-has-report="cell.status !== null ? 'true' : 'false'"
                :data-submitted="cell.status?.submitted === true ? 'true' : 'false'"
                :data-cell-state="cellState(cell).code"
                :aria-expanded="week.monday === expandedMonday"
                role="button"
                tabindex="0"
                @click="toggleWeek(cell.ymd)"
                @keyup.enter="toggleWeek(cell.ymd)"
              >
                <span class="dr-day__head">
                  <span class="dr-day__num">{{ cell.dayOfMonth }}</span>
                  <span class="badge dr-day__state" :class="cellState(cell).badge">{{ cellState(cell).label }}</span>
                </span>

                <!--
                  未展開でもその日の様子が分かるようにする（祝日・休日も 0 限を出す）。
                  記録が無い日も、週を開いていれば推定から同じ行を出してカードの並びを揃える。
                -->
                <span v-if="dayMeta(cell)" class="dr-day__meta">
                  <span class="dr-day__metric">{{ dayMeta(cell)?.lessonCount }}限</span>
                  <span v-if="dayMeta(cell)?.averageMastery !== null" class="dr-day__metric">
                    掌握 {{ dayMeta(cell)?.averageMastery?.toFixed(1) }}
                  </span>
                  <template v-else-if="dayMeta(cell)?.inferred">
                    <span class="dr-day__metric dr-day__metric--inferred">推定</span>
                  </template>
                  <span v-if="(dayMeta(cell)?.noteCount ?? 0) > 0" class="dr-day__metric dr-day__metric--note">
                    <AppIcon name="book-open" size="sm" />{{ dayMeta(cell)?.noteCount }}
                  </span>
                </span>

                <!-- 教科の色チップ（未展開のときだけ。展開中は授業のカードを出す） -->
                <span
                  v-if="cell.status && !isRestDay(cell.status) && week.monday !== expandedMonday"
                  class="dr-day__subjects"
                >
                  <!-- 記録した教科は全部出す（どの教科も色付き。副科は破線） -->
                  <span
                    v-for="subject in cell.status.subjects"
                    :key="subject"
                    class="dr-chip"
                    :class="subjectClass(subject)"
                    :data-subject="subject"
                    :data-subject-tone="isMainSubject(subject) ? 'main' : 'minor'"
                  >{{ subject }}</span>
                </span>
                <!--
                  未展開のマスには、その日の**振り返り**と**今夜の勉強内容**を出す。
                  振り返りは 2 行、今夜の勉強は 1 行で省略する。
                  「内容 n 件 / ノートあり」は情報が細かすぎるので出さない。
                -->
                <template v-if="cell.status && !isRestDay(cell.status) && week.monday !== expandedMonday">
                  <!-- 項目名はアイコンで示す（振り返り＝ペン / 今夜の勉強＝月）。読み上げ用の文字は残す -->
                  <p v-if="cell.status.review" class="dr-day__review" :title="`振り返り：${cell.status.review}`">
                    <AppIcon name="edit" size="sm" class="dr-day__review-icon" />
                    <span class="visually-hidden">振り返り</span>
                    <span class="dr-day__review-text">{{ cell.status.review }}</span>
                  </p>
                  <p v-if="cell.status.homework" class="dr-day__review dr-day__review--homework" :title="`今夜の勉強：${cell.status.homework}`">
                    <AppIcon name="moon" size="sm" class="dr-day__review-icon" />
                    <span class="visually-hidden">今夜の勉強</span>
                    <span class="dr-day__review-text">{{ cell.status.homework }}</span>
                  </p>
                </template>

                <!-- 展開した週は、このマスの中に各授業の詳細を並べる（押すと入力が開く） -->
                <ul
                  v-if="week.monday === expandedMonday && weekDayOf(cell.ymd)"
                  class="dr-day__lessons"
                >
                  <li
                    v-for="slot in lessonSlots(weekDayOf(cell.ymd))"
                    :key="slot.period"
                  >
                    <!-- 消した時限（空き）。下のカードを詰めず、同じ高さで空けておく -->
                    <div
                      v-if="slot.lesson === null"
                      class="dr-lesson-card dr-lesson-card--blank"
                      :data-lesson-blank="`${cell.ymd}-${slot.period}`"
                      :aria-label="`${slot.period}限 空き`"
                    >
                      <span class="dr-lesson-card__head">
                        <span class="dr-lesson-card__period">{{ slot.period }}限</span>
                        <span class="dr-lesson-card__mark dr-lesson-card__mark--inferred">空き</span>
                      </span>
                    </div>
                    <!--
                      カードは div（role=button）。中に削除ボタンを置くため、
                      button の入れ子（不正な HTML）を避けている。
                    -->
                    <div
                      v-else
                      class="dr-lesson-card"
                      :class="[
                        subjectClass(slot.lesson.subject),
                        { 'dr-lesson-card--inferred': !slot.lesson.recorded }
                      ]"
                      role="button"
                      tabindex="0"
                      :data-lesson="`${cell.ymd}-${slot.period}`"
                      :title="`${slot.period}限 ${slot.lesson.subject}${slot.lesson.recorded ? '' : '（推定）'} を入力`"
                      @click.stop="weekDayOf(cell.ymd) && openLesson(weekDayOf(cell.ymd) as WeekDay, slot.period)"
                      @keyup.enter.stop="weekDayOf(cell.ymd) && openLesson(weekDayOf(cell.ymd) as WeekDay, slot.period)"
                    >
                      <span class="dr-lesson-card__head">
                        <span class="dr-lesson-card__period">{{ slot.period }}限</span>
                        <span class="dr-lesson-card__subject">{{ slot.lesson.subject }}</span>
                        <span v-if="slot.lesson.recorded && slot.lesson.mastery !== null" class="dr-lesson-card__mark">
                          <span class="dr-stars" :title="`掌握度 ${slot.lesson.mastery}/5`">{{ stars(slot.lesson.mastery) }}</span>
                        </span>
                        <span v-else-if="!slot.lesson.recorded" class="dr-lesson-card__mark dr-lesson-card__mark--inferred">推定</span>
                        <!-- 記録済みの授業は消せる（消しても他の時限は詰めない） -->
                        <button
                          v-if="slot.lesson.recorded"
                          type="button" class="dr-lesson-card__delete"
                          :title="`${slot.period}限 ${slot.lesson.subject} を削除`"
                          :aria-label="`${slot.period}限 ${slot.lesson.subject} を削除`"
                          :data-lesson-delete="`${cell.ymd}-${slot.period}`"
                          :disabled="busy"
                          @click.stop="removeLesson(cell.ymd, slot.period, slot.lesson.subject)"
                        >
                          <AppIcon name="trash" size="sm" class="icon--danger" />
                        </button>
                      </span>
                      <span v-if="slot.lesson.recorded" class="dr-lesson-card__content">
                        {{ slot.lesson.content && slot.lesson.content !== '' ? slot.lesson.content : '（授業内容は未入力）' }}
                      </span>
                      <span v-if="!slot.lesson.recorded" class="dr-lesson-card__content dr-lesson-card__content--inferred">
                        推定：押すと記録できます
                      </span>
                      <span v-if="slot.lesson.recorded && slot.lesson.noteRecorded" class="dr-lesson-card__note">
                        <AppIcon name="book-open" size="sm" /> ノート記録あり
                      </span>
                      <span v-else-if="slot.lesson.recorded && slot.lesson.noteSkippedReason" class="dr-lesson-card__note dr-lesson-card__note--muted">
                        ノートなし：{{ slot.lesson.noteSkippedReason }}
                      </span>
                      <span v-else-if="slot.lesson.recorded" class="dr-lesson-card__note dr-lesson-card__note--placeholder" aria-hidden="true">—</span>
                    </div>
                  </li>
                </ul>

                <!-- その日のまとめ（4 観点・振り返り・宿題。展開したときだけ） -->
                <dl
                  v-if="week.monday === expandedMonday
                    && (weekDayOf(cell.ymd)?.review || weekDayOf(cell.ymd)?.homework)"
                  class="dr-day__summary"
                >
                  <div v-if="weekDayOf(cell.ymd)?.review" class="dr-day__summary-row dr-day__summary-row--text">
                    <dt class="dr-day__summary-label" title="振り返り">
                      <AppIcon name="edit" size="sm" /><span class="visually-hidden">振り返り</span>
                    </dt>
                    <dd>{{ weekDayOf(cell.ymd)?.review }}</dd>
                  </div>
                  <div v-if="weekDayOf(cell.ymd)?.homework" class="dr-day__summary-row dr-day__summary-row--text">
                    <dt class="dr-day__summary-label" title="今夜の勉強">
                      <AppIcon name="moon" size="sm" /><span class="visually-hidden">今夜の勉強</span>
                    </dt>
                    <dd>{{ weekDayOf(cell.ymd)?.homework }}</dd>
                  </div>
                </dl>

                <!--
                  展開したマスの操作。この日のために、授業を足し・まとめを書き・提出する。
                  提出は「記録がある」かつ「未提出」のときだけ押せる。
                -->
                <div
                  v-if="week.monday === expandedMonday"
                  class="dr-day__actions"
                  :data-day-actions="cell.ymd"
                >
                  <button
                    type="button" class="btn btn--secondary btn--sm"
                    :data-add-lesson="cell.ymd"
                    :title="`${cell.ymd} に授業を追加する`"
                    @click.stop="addLesson(cell.ymd)"
                  >
                    <AppIcon name="plus" size="sm" /> 授業追加
                  </button>
                  <button
                    type="button" class="btn btn--secondary btn--sm"
                    :data-day-summary="cell.ymd"
                    :title="`${cell.ymd} のまとめを書く`"
                    @click.stop="openSummary(cell.ymd)"
                  >
                    <AppIcon name="edit" size="sm" /> 当日まとめ
                  </button>
                  <button
                    type="button" class="btn btn--primary btn--sm"
                    :data-submit-day="cell.ymd"
                    :disabled="cell.status === null || cell.status.submitted"
                    :title="cell.status === null
                      ? 'この日はまだ記録がありません。先に【授業を追加】してください'
                      : (cell.status.submitted
                        ? 'この日は提出済みです'
                        : (isRestDay(cell.status)
                          ? '祝日・休日として提出します（授業の記録はありません）'
                          : 'この日を提出します'))"
                    @click.stop="openSubmit(cell.ymd)"
                  >
                    <AppIcon name="check-circle" size="sm" />
                    {{ cell.status?.submitted ? '提出済み' : '提出' }}
                  </button>
                </div>
              </div>
              <span v-else class="dr-day dr-day--empty" />
            </template>
          </div>
        </template>

        <p class="dr-legend">
          <span><span class="dr-legend__swatch dr-legend__swatch--submitted" />提出済（緑）</span>
          <span><span class="dr-legend__swatch dr-legend__swatch--draft" />記載あり（黄・未提出）</span>
          <span><span class="dr-legend__swatch dr-legend__swatch--missing" />未提出（グレー）</span>
          <span><span class="dr-legend__swatch dr-legend__swatch--rest" />祝日・休日（淡い赤）</span>
          <span>日のマスを押すと、その週の行が開いて各マスに授業の詳細が出ます（もう一度で閉じる）</span>
        </p>

        <!--
          教科の見分け方。どの教科にも色が付き、主科＝実線 / 副科＝破線で区別する。
          色だけで意味を伝えないよう、教科名も並べる。
        -->
        <p class="dr-subject-legend">
          <span class="dr-subject-legend__title">主科</span>
          <span v-for="entry in SUBJECT_LEGEND" :key="entry.tone" class="dr-chip" :class="`dr-subject--${entry.tone}`">
            {{ entry.label }}
          </span>
          <span class="dr-subject-legend__title">副科</span>
          <span
            v-for="entry in MINOR_SUBJECT_LEGEND" :key="entry.tone"
            class="dr-chip dr-subject--minor" :class="`dr-subject--${entry.tone}`"
          >{{ entry.label }}</span>
        </p>

        <!--
          **展開したマスの授業カード**の 4 つの見せ方（主科・副科・推定・空き）。
          「下地の有無」＝主科かどうか、「線が実線か破線か」＝記録済みかどうか。
          未展開のマスに出る教科チップ（上の凡例）とは別物なので、見出しを付けて区別する。
          見本の色は、主科＝数学（紫）／副科＝体育（ネイビー）と、それぞれの分類の教科色にする。
        -->
        <p class="dr-card-legend">
          <span class="dr-subject-legend__title">展開したカード</span>
          <span class="dr-card-legend__item">
            <span class="dr-card-legend__swatch dr-card-legend__swatch--main dr-subject--math" />主科（色の下地＋太い実線）
          </span>
          <span class="dr-card-legend__item">
            <span class="dr-card-legend__swatch dr-card-legend__swatch--minor dr-subject--pe" />副科（白地＋細い実線）
          </span>
          <span class="dr-card-legend__item">
            <span class="dr-card-legend__swatch dr-card-legend__swatch--inferred" />推定（斜線＋灰色の破線）
          </span>
          <span class="dr-card-legend__item">
            <span class="dr-card-legend__swatch dr-card-legend__swatch--blank" />空き（削除した時限）
          </span>
        </p>
      </div>
    </section>

    <!-- 提出の確認 -->
    <div v-if="submitOpen" class="overlay">
      <section class="dialog dialog--md" role="dialog" aria-modal="true" aria-labelledby="drSubmitTitle">
        <header class="dialog__head">
          <h2 id="drSubmitTitle" class="dialog__title" data-testid="dr-submit-title">
            {{ selectedDate }}（{{ WEEKDAY_LABELS[weekdayOf(selectedDate) - 1] }}）の日報を提出
          </h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="submitOpen = false">
            <AppIcon name="x" size="sm" />
          </button>
        </header>

        <div class="dialog__body">
          <p v-if="submitDetailLoading" class="dr-summary">読み込んでいます...</p>
          <template v-else-if="submitDetail">
            <!-- 祝日・休日は授業が無いので、その旨を出す（ユーザーの指定で提出できるようにした） -->
            <p class="dr-summary" data-testid="dr-submit-summary">
              <template v-if="submitDetail.holidayType !== 'NORMAL'">{{ HOLIDAY_LABELS[submitDetail.holidayType] }}として日報を提出します（授業の記録はありません）。</template>
              <template v-else>
                {{ submitDetail.lessons.length }} 限の記録を提出します。
              </template>
            </p>
            <ul class="dr-submit-list">
              <li v-for="lesson in submitDetail.lessons" :key="lesson.period">
                <span class="dr-chip" :class="subjectClass(lesson.subject)">{{ lesson.subject }}</span>
                <span class="dr-submit-list__period">{{ lesson.period }}限</span>
                <span class="dr-submit-list__content">{{ lesson.content ?? '（授業内容は未入力）' }}</span>
                <span v-if="lesson.mastery !== null" class="dr-submit-list__mark">掌握 {{ lesson.mastery }}/5</span>
              </li>
            </ul>
            <dl
              v-if="submitDetail.holidayType !== 'NORMAL' || submitDetail.review || submitDetail.homework"
              class="dr-day__summary"
            >
              <div v-if="submitDetail.holidayType !== 'NORMAL'" class="dr-day__summary-row">
                <dt>区分</dt><dd>{{ HOLIDAY_LABELS[submitDetail.holidayType] }}</dd>
              </div>
              <div v-if="submitDetail.review" class="dr-day__summary-row dr-day__summary-row--text">
                <dt class="dr-day__summary-label" title="振り返り">
                  <AppIcon name="edit" size="sm" /><span class="visually-hidden">振り返り</span>
                </dt>
                <dd>{{ submitDetail.review }}</dd>
              </div>
              <div v-if="submitDetail.homework" class="dr-day__summary-row dr-day__summary-row--text">
                <dt class="dr-day__summary-label" title="今夜の勉強">
                  <AppIcon name="moon" size="sm" /><span class="visually-hidden">今夜の勉強</span>
                </dt>
                <dd>{{ submitDetail.homework }}</dd>
              </div>
            </dl>
            <p class="dr-actions__note">
              提出すると「提出済（緑）」になります。あとから編集すると「再提出待ち」に戻ります。
            </p>
          </template>
        </div>

        <footer class="dialog__foot">
          <button type="button" class="btn btn--secondary" @click="submitOpen = false">キャンセル</button>
          <button
            type="button" class="btn btn--primary" :disabled="submitBusy || submitDetail === null"
            data-testid="dr-submit-confirm" @click="confirmSubmit"
          >
            <AppIcon name="check-circle" size="sm" /> 提出する
          </button>
        </footer>
      </section>
    </div>

    <!-- 日まとめ（祝日/休日区分・今日の振り返り・今夜の勉強内容） -->
    <div v-if="summaryOpen" class="overlay">
      <section class="dialog dialog--md" role="dialog" aria-modal="true" aria-labelledby="drSummaryTitle">
        <header class="dialog__head">
          <h2 id="drSummaryTitle" class="dialog__title" data-testid="dr-summary-title">
            {{ daySummary.date }}（{{ WEEKDAY_LABELS[weekdayOf(daySummary.date) - 1] }}）のまとめ
          </h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="summaryOpen = false">
            <AppIcon name="x" size="sm" />
          </button>
        </header>

        <div class="dialog__body">
          <p v-if="daySummary.loading" class="dr-summary">読み込んでいます...</p>
          <template v-else>
            <!-- ① 祝日/休日区分 -->
            <section class="dr-form__section">
              <h3 class="dr-form__title">祝日／休日区分</h3>
              <div class="dr-form__row">
                <select v-model="daySummary.holidayType" class="select" aria-label="祝日・休日区分" data-testid="dr-holiday-type">
                  <option v-for="(label, code) in HOLIDAY_LABELS" :key="code" :value="code">{{ label }}</option>
                </select>
                <span class="dr-form__hint">祝日・休日にすると、その日の授業の記録は消えます</span>
              </div>
              <p v-if="summaryClearsLessons" class="alert alert--warning" data-testid="dr-summary-warning">
                保存すると、この日の授業 {{ daySummary.lessonCount }} 件の記録が消えます。
              </p>
            </section>

            <!-- ② 今日の振り返り -->
            <section class="dr-form__section">
              <h3 class="dr-form__title">今日の振り返り</h3>
              <textarea
                v-model="daySummary.review" class="input" rows="3" aria-label="今日の振り返り"
                data-testid="dr-summary-review" placeholder="今日の授業全体を通して感じたこと"
              />
            </section>

            <!-- ③ 今夜の勉強内容 -->
            <section class="dr-form__section">
              <h3 class="dr-form__title">今夜の勉強内容</h3>
              <textarea
                v-model="daySummary.homework" class="input" rows="3" aria-label="今夜の勉強内容"
                data-testid="dr-summary-homework" placeholder="例: 英語のワーク p.20-22"
              />
            </section>
          </template>
        </div>

        <footer class="dialog__foot">
          <button type="button" class="btn btn--secondary" @click="summaryOpen = false">キャンセル</button>
          <button
            type="button" class="btn btn--primary" :disabled="summaryBusy || daySummary.loading"
            data-testid="dr-summary-save" @click="saveSummary"
          >
            <AppIcon name="check" size="sm" /> 保存
          </button>
        </footer>
      </section>
    </div>

    <!-- 授業の編集（時限・教科・掌握度・授業内容・ノート記録・ノート記載しない理由・自己評価） -->
    <div v-if="dialogOpen" class="overlay">
      <section class="dialog dialog--lg" role="dialog" aria-modal="true" aria-labelledby="drDialogTitle">
        <header class="dialog__head">
          <h2 id="drDialogTitle" class="dialog__title">
            <template v-if="editing.adding">授業を追加：</template>
            {{ editing.date }}（{{ WEEKDAY_LABELS[weekdayOf(editing.date) - 1] }}）
            <span v-if="editing.subject" class="dr-chip" :class="subjectClass(editing.subject)">{{ editing.subject }}</span>
          </h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="dialogOpen = false">
            <AppIcon name="x" size="sm" />
          </button>
        </header>

        <div class="dialog__body">
          <p v-if="editing.adding" class="dr-summary">
            <span class="badge badge--info">追加</span>
            保存しただけでは未提出のままです。提出は展開したマスの【提出】から行います。
          </p>
          <p v-else-if="editing.inferred" class="dr-summary">
            <span class="badge badge--neutral">推定</span>
            過去の日報から推定した教科です。内容を確認して保存してください。
          </p>

          <!-- ① 時限 ② 教科 ④ 授業内容 -->
          <section class="dr-form__section">
            <h3 class="dr-form__title">授業</h3>
            <div class="dr-form__grid">
              <label class="dr-field">
                <span class="dr-field__label">時限</span>
                <!-- 選択肢はその日の日報に合わせる（記録済みの時限は出さない） -->
                <select v-model.number="editing.period" class="select" aria-label="時限" data-testid="dr-period">
                  <option v-for="period in periodOptions" :key="period" :value="period">{{ period }}限</option>
                </select>
              </label>
              <label class="dr-field dr-field--wide">
                <span class="dr-field__label">教科</span>
                <input
                  id="drSubject" v-model="editing.subject" class="input" type="text"
                  aria-label="教科" placeholder="例: 数学"
                >
              </label>
            </div>
            <div class="dr-form__row">
              <span class="dr-field__label">よく使う教科</span>
              <button
                v-for="subject in QUICK_SUBJECTS" :key="subject" type="button"
                class="dr-chip dr-chip--button" :class="subjectClass(subject)"
                :data-quick-subject="subject" @click="editing.subject = subject"
              >
                {{ subject }}
              </button>
            </div>
            <label class="dr-field dr-field--block">
              <span class="dr-field__label">授業内容</span>
              <textarea
                id="drContent" v-model="editing.content" class="input" rows="3"
                aria-label="授業内容" placeholder="例: 二次関数の最大値・最小値"
              />
            </label>
          </section>

          <!-- ③ 掌握度 -->
          <section class="dr-form__section">
            <h3 class="dr-form__title">掌握度</h3>
            <!-- 星を直接押して選ぶ（同じ星をもう一度押すと未入力に戻る） -->
            <div class="dr-form__row">
              <StarRating v-model="editing.mastery" label="掌握度" testid="dr-mastery" />
            </div>
          </section>

          <!-- ⑤ ノート記録（主科だけ・ラジオ） ⑥ ノート記載しない理由 -->
          <section class="dr-form__section">
            <h3 class="dr-form__title">
              ノート記録
              <span v-if="!noteSelectable" class="dr-form__hint">（主科だけ選べます）</span>
            </h3>
            <div v-if="noteSelectable" class="dr-form__row">
              <label class="dr-radio">
                <input v-model="noteValue" type="radio" value="RECORDED" data-testid="dr-note-recorded"> 記録した
              </label>
              <label class="dr-radio">
                <input v-model="noteValue" type="radio" value="SKIPPED" data-testid="dr-note-skipped"> 記録しない
              </label>
            </div>
            <p v-else class="dr-form__hint" data-testid="dr-note-major-only">
              副科はノート記録の対象外です（「記録しない」で保存します）。
            </p>

            <template v-if="noteSelectable && !editing.noteRecorded">
              <label class="dr-field dr-field--block">
                <span class="dr-field__label">ノート記載しない理由</span>
                <input
                  v-model="editing.noteSkippedReason" class="input" type="text"
                  aria-label="ノート記載しない理由" data-testid="dr-note-reason"
                  placeholder="例: 時間が無かった"
                >
              </label>
              <div class="dr-form__row">
                <span class="dr-field__label">候補</span>
                <button
                  v-for="reason in NOTE_REASON_SUGGESTIONS" :key="reason" type="button"
                  class="dr-chip dr-chip--button" :data-note-reason="reason"
                  @click="editing.noteSkippedReason = reason"
                >
                  {{ reason }}
                </button>
              </div>
            </template>
          </section>

          <!-- ⑦ 学習集中度 ⑧ 学習量 ⑨ 学習態度 -->
          <section class="dr-form__section">
            <h3 class="dr-form__title">
              この授業の自己評価
              <span class="dr-form__hint">（星を押して選びます。入力したものだけ保存します）</span>
            </h3>
            <div class="dr-form__grid dr-form__grid--three">
              <div class="dr-field">
                <span class="dr-field__label">学習集中度</span>
                <StarRating v-model="editing.concentration" label="学習集中度" testid="dr-concentration" />
              </div>
              <div class="dr-field">
                <span class="dr-field__label">学習量</span>
                <StarRating v-model="editing.studyVolume" label="学習量" testid="dr-study-volume" />
              </div>
              <div class="dr-field">
                <span class="dr-field__label">学習態度</span>
                <StarRating v-model="editing.attitude" label="学習態度" testid="dr-attitude" />
              </div>
            </div>
          </section>
        </div>

        <footer class="dialog__foot">
          <button type="button" class="btn btn--secondary" @click="dialogOpen = false">キャンセル</button>
          <button type="button" class="btn btn--primary" :disabled="dialogBusy" @click="saveLesson">
            <AppIcon name="check" size="sm" /> 保存
          </button>
        </footer>
      </section>
    </div>
  </div>
</template>
