/**
 * 教科ごとの色（学習日報のカレンダー・授業カードで、教科を見分けるため）。
 *
 * **どの教科にも色を付ける**（ユーザーの指定）。主科・副科の区別は色ではなく
 * **枠の線**で示す（主科＝実線 / 副科＝破線）。教科名そのものは必ずラベルに出す
 * （色だけで意味を伝えない）。
 *
 * 色は `tokens.css` の変数だけを使う（暗色テーマにも自動で追随させる）。
 * 英語・国語・数学は専用の変数（`--subject-*`）、それ以外は用途が近い既存の変数へ寄せる。
 */

export type SubjectTone =
  | 'english'
  | 'japanese'
  | 'math'
  | 'science'
  | 'social'
  | 'music'
  | 'art'
  | 'pe'
  | 'tech'
  | 'other'

/** 主科（実線で見せる教科）。色はどの教科にも付く。 */
export const MAIN_SUBJECT_TONES: SubjectTone[] = ['english', 'japanese', 'math', 'science', 'social']

/** 教科名（表記ゆれを含む）→ 色の分類。 */
const KEYWORDS: Array<{ tone: SubjectTone; words: string[] }> = [
  { tone: 'english', words: ['英語', '英会話', 'english', '外国語'] },
  { tone: 'japanese', words: ['国語', '日本語', '現代文', '古文', '漢文', 'japanese'] },
  { tone: 'math', words: ['数学', '算数', 'math'] },
  { tone: 'science', words: ['理科', '科学', '物理', '化学', '生物', '地学'] },
  { tone: 'social', words: ['社会', '地理', '歴史', '公民', '世界史', '日本史', '政経', '倫理'] },
  { tone: 'music', words: ['音楽', '器楽', '合唱', 'music'] },
  { tone: 'art', words: ['美術', '図工', '書道', '芸術'] },
  { tone: 'pe', words: ['体育', '保健', 'スポーツ', '水泳'] },
  { tone: 'tech', words: ['技術', '家庭', '情報', 'プログラミング', 'パソコン'] }
]

/**
 * 教科名から色の分類を決める。
 * 例: 「英語」→ english、「数学I」→ math、「道徳」→ other（グレー）。
 */
export function subjectTone(subject: string | null | undefined): SubjectTone {
  const text = (subject ?? '').trim().toLowerCase()
  if (text === '') {
    return 'other'
  }
  for (const entry of KEYWORDS) {
    if (entry.words.some((word) => text.includes(word.toLowerCase()))) {
      return entry.tone
    }
  }
  return 'other'
}

/**
 * 主科かどうか（英語・国語・数学・理科・社会）。
 * 主科は実線、副科は破線で見せる（色はどちらにも付く）。
 */
export function isMainSubject(subject: string | null | undefined): boolean {
  return MAIN_SUBJECT_TONES.includes(subjectTone(subject))
}

/**
 * CSS クラス名。教科の色（`.dr-subject--math` など）に、
 * 副科のときは破線の印（`.dr-subject--minor`）を足す。
 */
export function subjectClass(subject: string | null | undefined): string {
  const tone = `dr-subject--${subjectTone(subject)}`
  return isMainSubject(subject) ? tone : `${tone} dr-subject--minor`
}

/** 凡例に出す主科（実線）。 */
export const SUBJECT_LEGEND: Array<{ tone: SubjectTone; label: string }> = [
  { tone: 'english', label: '英語' },
  { tone: 'japanese', label: '国語' },
  { tone: 'math', label: '数学' },
  { tone: 'science', label: '理科' },
  { tone: 'social', label: '社会' }
]

/** 凡例に出す副科（破線）。教科ごとに色が違うことも見せる。 */
export const MINOR_SUBJECT_LEGEND: Array<{ tone: SubjectTone; label: string }> = [
  { tone: 'music', label: '音楽' },
  { tone: 'art', label: '美術' },
  { tone: 'pe', label: '体育' },
  { tone: 'tech', label: '技術・家庭' },
  { tone: 'other', label: 'その他' }
]
