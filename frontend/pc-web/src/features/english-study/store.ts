import { defineStore } from 'pinia'
import { ref, toRaw } from 'vue'

export type StudyKind = 'word' | 'phrase'
export type StudyLevel = 'beginner' | 'intermediate'
export type TestType = 'A' | 'B' | 'C' | 'D' | 'E' | 'F'
export const levels = { beginner: '初級編', intermediate: '中級編' } as const
export const testTypes = { A: 'A. 勉強', B: 'B. 中日訳英', C: 'C. 音訳英', D: 'D. 英訳中日', E: 'E. 文脈英訳', F: 'F. 例文入力' } as const
export function testLabels(kind: StudyKind): Record<TestType, string> {
  return kind === 'phrase' ? { A: 'A. 勉強', B: 'B. 意味 → 表現', C: 'C. 音声 → 表現', D: 'D. 表現 → 意味', E: 'E. 文脈 → 意味', F: 'F. 例文入力' } : testTypes
}
export const states = { CREATED: '未勉強', STARTED: '勉強中', COMPLETED: '勉強済' } as const
export interface StudyWord {
  id: string; kind: StudyKind; level: StudyLevel; book: string; category: string; grade: string
  english: string; japanese: string; chinese: string; pronunciation: string; example: string; translation: string
  phraseType: string; pattern: string; favorite: boolean; mastered: boolean; detailReady: boolean
  skills: Record<TestType, { attempts: number; correct: number }>
}
export interface StudyTest {
  id: string; kind: StudyKind; level: StudyLevel; type: TestType; book: string; category: string
  wordIds: string[]; questions: StudyWord[]; state: keyof typeof states
  answers: { wordId: string; value: string; correct: boolean }[]; createdAt: string; activeSeconds: number
}
type Seed = [string, string, string, string, string, string]
const vocabulary: Record<StudyKind, Record<StudyLevel, Seed[]>> = {
  word: {
    beginner: [
      ['achieve', '達成する', '实现', '/əˈtʃiːv/', 'You can achieve your goal.', 'あなたは目標を達成できます。'],
      ['borrow', '借りる', '借入', '/ˈbɒrəʊ/', 'May I borrow your pen?', 'ペンを借りてもいいですか。'],
      ['improve', '改善する', '改善', '/ɪmˈpruːv/', 'Practice will improve your English.', '練習すれば英語が上達します。'],
      ['discover', '発見する', '发现', '/dɪˈskʌvə/', 'We discover something new every day.', '私たちは毎日新しいことを発見します。'],
      ['support', '支える', '支持', '/səˈpɔːt/', 'My friends support me.', '友達が私を支えてくれます。'],
      ['environment', '環境', '环境', '/ɪnˈvaɪrənmənt/', 'We should protect the environment.', '私たちは環境を守るべきです。']
    ],
    intermediate: [
      ['abandon', '捨てる', '放弃', '/əˈbændən/', 'Do not abandon your dream.', '夢を捨てないでください。'],
      ['significant', '重要な', '重要的', '/sɪɡˈnɪfɪkənt/', 'This is a significant change.', 'これは重要な変化です。'],
      ['perspective', '観点', '观点', '/pəˈspektɪv/', 'Try a different perspective.', '違う観点を試してみてください。'],
      ['contribute', '貢献する', '贡献', '/kənˈtrɪbjuːt/', 'We contribute to the community.', '私たちは地域社会に貢献しています。'],
      ['sustainable', '持続可能な', '可持续的', '/səˈsteɪnəbl/', 'We need sustainable energy.', '持続可能なエネルギーが必要です。'],
      ['negotiate', '交渉する', '谈判', '/nɪˈɡəʊʃieɪt/', 'They negotiate a fair price.', '彼らは公正な価格を交渉します。']
    ]
  },
  phrase: {
    beginner: [
      ['look for', '探す', '寻找', '/lʊk fɔːr/', 'I look for my keys every morning.', '毎朝鍵を探します。'],
      ['take care of', '世話をする', '照顾', '/teɪk keər əv/', 'I take care of my sister.', '私は妹の世話をします。'],
      ['be good at', '得意である', '擅长', '/bi ɡʊd æt/', 'I want to be good at English.', '英語が得意になりたいです。'],
      ['get along with', '仲良くする', '和睦相处', '/ɡet əˈlɒŋ wɪð/', 'I get along with my classmates.', 'クラスメートと仲良くしています。'],
      ['a lot of', 'たくさんの', '许多', '/ə lɒt əv/', 'I have a lot of books.', '本をたくさん持っています。'],
      ['at first', '最初は', '起初', '/æt fɜːst/', 'It was difficult at first.', '最初は難しかったです。']
    ],
    intermediate: [
      ['take advantage of', '活用する', '利用', '/teɪk ədˈvɑːntɪdʒ əv/', 'We take advantage of this opportunity.', 'この機会を活用します。'],
      ['put up with', '我慢する', '忍受', '/pʊt ʌp wɪð/', 'I cannot put up with the noise.', 'その騒音には我慢できません。'],
      ['come up with', '思いつく', '想出', '/kʌm ʌp wɪð/', 'Can you come up with a solution?', '解決策を思いつきますか。'],
      ['in terms of', 'の観点から', '就…而言', '/ɪn tɜːmz əv/', 'Think in terms of quality.', '品質の観点から考えてください。'],
      ['on behalf of', 'を代表して', '代表', '/ɒn bɪˈhɑːf əv/', 'I speak on behalf of the team.', 'チームを代表して話します。'],
      ['as a result of', 'の結果として', '由于', '/æz ə rɪˈzʌlt əv/', 'She improved as a result of practice.', '練習の結果、彼女は上達しました。']
    ]
  }
}
function freshSkills(): StudyWord['skills'] {
  return Object.fromEntries(Object.keys(testTypes).map(key => [key, { attempts: 0, correct: 0 }])) as StudyWord['skills']
}
function seedWords(): StudyWord[] {
  return (['word', 'phrase'] as const).flatMap(kind => (['beginner', 'intermediate'] as const).flatMap(level => vocabulary[kind][level].map((row, i) => ({
    id: `${kind}-${level}-${i + 1}`, kind, level, book: `${level === 'beginner' ? '英検3級' : '英検2級'} ${kind === 'word' ? '単語' : '熟語'}トレーニング`,
    category: `Day ${String(Math.floor(i / 3) + 1).padStart(2, '0')}`, grade: level === 'beginner' ? '3級' : '2級',
    english: row[0], japanese: row[1], chinese: row[2], pronunciation: row[3], example: row[4], translation: row[5],
    phraseType: kind === 'phrase' ? (i < 3 ? '句動詞・動詞構文' : '定型表現') : '—', pattern: kind === 'phrase' ? `${row[0]} …` : '',
    favorite: i === 0, mastered: i === 5, detailReady: i !== 4, skills: freshSkills()
  }))))
}
export function normalizeAnswer(value: string) { return value.normalize('NFKC').toLowerCase().trim().replace(/[.!?。！？]+$/u, '').replace(/\s+/g, ' ') }
export function expectedAnswer(word: StudyWord, type: TestType) { return type === 'F' ? word.example : type === 'D' || type === 'E' ? word.japanese : word.english }
export function score(test: StudyTest) { return test.answers.length ? Math.round(test.answers.filter(a => a.correct).length / test.answers.length * 100) : 0 }

/** デモ専用。同じタブ内の画面遷移で共有し、再読み込みで初期化する。 */
export const useEnglishStudyStore = defineStore('english-study-demo', () => {
  const words = ref<StudyWord[]>(seedWords())
  const tests = ref<StudyTest[]>([])
  let serial = 0
  function select(kind: StudyKind, level?: StudyLevel) { return words.value.filter(w => w.kind === kind && (!level || w.level === level)) }
  function createTest(input: { kind: StudyKind; level: StudyLevel; type: TestType; wordIds: string[] }) {
    const selected = select(input.kind, input.level).filter(w => input.wordIds.includes(w.id))
    if (!selected.length) throw new Error('出題できる語がありません。検索条件を変更してください。')
    const test: StudyTest = { ...input, id: `ENG-${String(++serial).padStart(4, '0')}`, book: [...new Set(selected.map(w => w.book))].join(' / '), category: [...new Set(selected.map(w => w.category))].join(' ～ '), wordIds: selected.map(w => w.id), questions: selected.map(w => structuredClone(toRaw(w))), state: 'CREATED', answers: [], createdAt: new Date().toLocaleDateString('ja-JP'), activeSeconds: 0 }
    tests.value.unshift(test)
    return tests.value[0]
  }
  function answer(id: string, value: string, known = true) {
    const test = tests.value.find(t => t.id === id)
    if (!test || test.state === 'COMPLETED') return
    const word = test.questions[test.answers.length]
    if (!word) return
    const correct = test.type === 'A' ? known : normalizeAnswer(value) === normalizeAnswer(expectedAnswer(word, test.type)) || (test.type === 'D' && normalizeAnswer(value) === normalizeAnswer(word.chinese))
    test.answers.push({ wordId: word.id, value, correct })
    test.state = test.answers.length === test.wordIds.length ? 'COMPLETED' : 'STARTED'
    const liveWord = words.value.find(w => w.id === word.id)
    if (liveWord) { liveWord.skills[test.type].attempts++; if (correct) liveWord.skills[test.type].correct++ }
  }
  function retry(id: string) {
    const test = tests.value.find(t => t.id === id)!
    return createTest({ ...test, wordIds: test.answers.filter(a => !a.correct).map(a => a.wordId) })
  }
  function start(id: string) { const test = tests.value.find(t => t.id === id); if (test?.state === 'CREATED') test.state = 'STARTED' }
  function tick(id: string) { const test = tests.value.find(t => t.id === id); if (test?.state === 'STARTED') test.activeSeconds++ }
  function saveWord(word: Omit<StudyWord, 'id' | 'skills'> & { id?: string }) {
    if (!word.english.trim() || !word.japanese.trim() || !word.book.trim() || !word.category.trim()) throw new Error('英語・日本語訳・書籍・分類を入力してください。')
    if (words.value.some(w => w.id !== word.id && w.kind === word.kind && w.level === word.level && w.book === word.book && w.category === word.category && normalizeAnswer(w.english) === normalizeAnswer(word.english))) throw new Error('同じ書籍・分類に登録済みです。')
    const existing = words.value.find(w => w.id === word.id)
    if (existing) Object.assign(existing, word)
    else words.value.push({ ...word, id: `custom-${++serial}`, skills: freshSkills() })
  }
  for (const kind of ['word', 'phrase'] as const) for (const level of ['beginner', 'intermediate'] as const) {
    const selected = select(kind, level)
    const done = createTest({ kind, level, type: 'B', wordIds: selected.slice(0, 3).map(w => w.id) })
    selected.slice(0, 3).forEach((w, i) => answer(done.id, i === 1 ? '—' : w.english))
    done.activeSeconds = 135
    createTest({ kind, level, type: 'D', wordIds: selected.slice(3).map(w => w.id) })
    const learning = createTest({ kind, level, type: 'A', wordIds: selected.map(w => w.id) })
    answer(learning.id, '', true); learning.activeSeconds = 45
  }
  return { words, tests, select, createTest, answer, retry, saveWord, start, tick }
})
