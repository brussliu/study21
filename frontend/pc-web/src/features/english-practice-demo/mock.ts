import { copy, type Bilingual, type ClozeSet, type IntensiveRecord, type LessonImage, type Question } from './types'
export const bi = (ja: string, zh: string): Bilingual => ({ ja, zh })
let sequence = 0
export function demoId(prefix = 'demo') { return `${prefix}-${Date.now()}-${++sequence}` }
export function sampleImage(title: string, lines: string[], category: LessonImage['category'] = 'QUESTION'): LessonImage {
  const escape = (text: string) => text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="960" height="680"><rect width="960" height="680" fill="#fff"/><rect x="50" y="44" width="860" height="7" fill="#25876f"/><text x="60" y="92" font-family="sans-serif" font-size="18" fill="#667788">STUDY 2.1 / SAMPLE WORKSHEET</text><text x="60" y="148" font-family="sans-serif" font-size="28" fill="#203040">${escape(title)}</text>${lines.map((line, i) => `<text x="60" y="${210 + i * 40}" font-family="sans-serif" font-size="18" fill="#203040">${escape(line)}</text>`).join('')}<text x="60" y="635" font-family="sans-serif" font-size="14" fill="#667788">Sample material for page review</text></svg>`
  return { id: demoId('image'), name: `${title}.svg`, src: `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`, category }
}
export function newQuestion(text = ''): Question {
  return { id: demoId('q'), text, translation: bi('', ''), options: ['', '', '', ''], correct: null, student: null, knowledge: '', explanation: bi('', ''), optionNotes: [], evidence: [], imageId: '' }
}
export function clozeQuestions(): Question[] {
  return [
    { ...newQuestion('Because the bridge was unsafe, the city decided to ____ the opening until repairs were complete.'), options: ['attend', 'postpone', 'predict', 'reward'], correct: 1, student: 0, knowledge: '動詞 / postpone', translation: bi('橋が安全ではなかったため、市は修理が完了するまで開通を延期することにしました。', '由于桥梁不安全，市政府决定推迟开放，直到维修完成。'), explanation: bi('until repairs were complete が「修理が終わるまで延期する」という文脈を示します。postpone は予定を後の日時に延ばす動詞です。', 'until repairs were complete 表示要等维修完成，因此选择表示“推迟”的 postpone。'), optionNotes: [bi('attend は「出席する」。開通を遅らせる意味はありません。', 'attend 意为“出席”，不表示推迟。'), bi('postpone は「延期する」。目的語に予定や行事を取ります。', 'postpone 意为“推迟”，后接计划或活动。'), bi('predict は「予測する」。', 'predict 意为“预测”。'), bi('reward は「報いる」。', 'reward 意为“奖励”。')] },
    { ...newQuestion('If I ____ more time, I would join the volunteer project.'), options: ['have', 'will have', 'had', 'having'], correct: 2, student: 2, knowledge: '文法 / 仮定法過去', translation: bi('もっと時間があれば、そのボランティア活動に参加するのですが。', '如果我有更多时间，我就会参加那个志愿者项目。'), explanation: bi('現在の事実と異なる仮定なので、if 節に過去形 had、主節に would + 動詞の原形を用います。', '这是与现在事实相反的假设，if 从句用 had，主句用 would + 动词原形。'), optionNotes: [bi('現在形はこの仮定法の形に合いません。', '现在时不符合该虚拟语气结构。'), bi('if 節では will have を使いません。', '此处 if 从句不能使用 will have。'), bi('If + 主語 + 過去形、主語 + would + 動詞の原形。', 'If + 主语 + 过去式，主语 + would + 动词原形。'), bi('having だけでは述語になりません。', 'having 不能单独作谓语。')] },
    { ...newQuestion('The new policy will have a positive ____ on local businesses.'), options: ['effect', 'effort', 'event', 'error'], correct: 0, student: null, knowledge: '語法 / have an effect on', translation: bi('新しい政策は地域の企業に良い影響を与えるでしょう。', '新政策将对当地企业产生积极影响。'), explanation: bi('have an effect on は「〜に影響を与える」という表現です。positive はその影響が良いことを表します。', 'have an effect on 表示“对……产生影响”，positive 说明影响是积极的。'), optionNotes: [bi('effect は「影響・効果」。', 'effect 意为“影响、效果”。'), bi('effort は「努力」。', 'effort 意为“努力”。'), bi('event は「出来事」。', 'event 意为“事件”。'), bi('error は「誤り」。', 'error 意为“错误”。')] }
  ]
}
export function blankCloze(): ClozeSet { return { id: demoId('cloze'), createdAt: new Date().toLocaleString('ja-JP'), sourceType: 'EXAM', sourceName: '', year: String(new Date().getFullYear()), chapter: '', note: '', images: [], questions: [], parallel: true, ocrState: 'WAITING', explanationState: 'WAITING' } }
export function clozeSeeds(): ClozeSet[] {
  return [0, 1].map((n) => {
    const questions = clozeQuestions().slice(n ? 0 : 0, n ? 2 : 3)
    const image = sampleImage(n ? 'Grammar Review' : 'Vocabulary and Grammar', questions.flatMap((q, i) => [`${i + 1}. ${q.text.slice(0, 86)}`, q.options.map((o, j) => `${String.fromCharCode(65 + j)}. ${o}`).join('     ')]))
    questions.forEach(q => { q.imageId = image.id })
    return { ...blankCloze(), id: `cloze-${n + 1}`, sourceType: n ? 'PRACTICE' : 'EXAM', sourceName: n ? '文法トレーニング' : '英検 準1級 第1回', chapter: n ? 'Unit 3' : 'Part 1', note: n ? '仮定法と動詞を復習' : '学校の復習プリント', questions, images: [image], ocrState: 'COMPLETED', explanationState: n ? 'WAITING' : 'COMPLETED' }
  })
}
export function blankIntensive(): IntensiveRecord {
  return { id: demoId('reading'), createdAt: new Date().toLocaleString('ja-JP'), title: '', sourceType: 'EXAM', sourceName: '', year: String(new Date().getFullYear()), chapter: '', note: '', grade: '準1級', difficulty: '中', theme: '', images: [], imageMode: 'TEXT', stages: ['WAITING', 'WAITING', 'WAITING', 'WAITING'], stageError: '', paragraphs: [], questions: [], vocabulary: [], guide: bi('', ''), points: bi('', ''), memo: '' }
}
export function readingSample(): IntensiveRecord {
  const record = blankIntensive()
  Object.assign(record, { id: 'reading-1', title: 'Why Cities Need More Trees', sourceName: '英検 準1級 読解練習', chapter: 'Reading Part 2', theme: '環境・都市政策', stages: ['COMPLETED', 'COMPLETED', 'COMPLETED', 'COMPLETED'] })
  record.paragraphs = [
    { id: 'p1', role: bi('都市の木々の価値', '城市树木的价值'), sentences: [
      { id: 's1', english: 'Trees in cities are often treated as decoration, but their value extends far beyond appearance.', translation: bi('都市の木々は装飾として扱われがちですが、その価値は見た目をはるかに超えています。', '城市树木常被当作装饰，但其价值远远超出外观。'), grammar: bi('are treated as は受動態。「AをBとして扱う」は treat A as B。but は逆接です。', 'are treated as 是被动语态，treat A as B 表示“把 A 当作 B”，but 表示转折。') },
      { id: 's2', english: 'During hot summers, they shade streets and lower surrounding temperatures by releasing water vapor.', translation: bi('暑い夏には、木々は街路に日陰を作り、水蒸気を放出することで周囲の気温を下げます。', '炎热的夏天，树木为街道遮阴，并通过释放水蒸气降低周围的温度。'), grammar: bi('shade と lower は並列の動詞です。by + 動名詞は手段を表します。', 'shade 与 lower 为并列动词。by + 动名词表示方式。') }
    ] },
    { id: 'p2', role: bi('環境と健康への効果', '环境和健康效益'), sentences: [
      { id: 's3', english: 'Trees also filter airborne pollutants and absorb carbon dioxide.', translation: bi('木々は空気中の汚染物質をろ過し、二酸化炭素を吸収します。', '树木还可以过滤空气中的污染物并吸收二氧化碳。'), grammar: bi('airborne は pollutants を修飾する形容詞です。also は利益の追加を示します。', 'airborne 是修饰 pollutants 的形容词，also 表示追加另一项益处。') },
      { id: 's4', english: 'Research has found that exposure to green spaces is associated with lower stress levels and better mental health.', translation: bi('研究によると、緑地に触れることはストレスの低下や心の健康の改善と関連しています。', '研究发现，接触绿地与较低的压力水平和更好的心理健康相关。'), grammar: bi('that 以下が found の目的語。be associated with は関連を表し、因果関係を断定しません。', 'that 从句作 found 的宾语。be associated with 表示相关，不等于确定的因果关系。') }
    ] },
    { id: 'p3', role: bi('公平な分配と結論', '公平分配与结论'), sentences: [
      { id: 's5', english: 'Lower-income neighborhoods often have less tree cover, leaving residents more vulnerable to extreme heat and air pollution.', translation: bi('低所得の地域は樹木が少ないことが多く、住民は猛暑や大気汚染の影響を受けやすくなっています。', '低收入社区通常树木覆盖较少，使居民更容易受到极端高温和空气污染的影响。'), grammar: bi('leaving ... は結果を表す分詞構文。vulnerable to + 名詞で「〜に弱い」。', 'leaving ... 为表示结果的分词结构。vulnerable to + 名词表示“易受……影响”。') },
      { id: 's6', english: 'City planners need to treat urban trees as public infrastructure rather than optional beautification.', translation: bi('都市計画者は、木々を任意の美化ではなく公共インフラとして扱う必要があります。', '城市规划者应把城市树木当作公共基础设施，而不是可有可无的美化。'), grammar: bi('rather than は二つの扱い方を対比します。need to が筆者の提言を表しています。', 'rather than 对比两种看法，need to 表达作者的建议。') }
    ] }
  ]
  record.guide = bi('木々の冷却・空気浄化・健康への効果を紹介し、樹木の分配の不平等を都市政策の課題として論じています。', '文章介绍树木的降温、净化空气和健康效益，并将树木分布不均视为城市政策议题。')
  record.points = bi('but / also / rather than に注目し、事実の説明から筆者の提言へ進む構成を確認しましょう。', '注意 but、also 和 rather than，理解文章从事实说明过渡到作者建议的结构。')
  record.questions = [
    { ...newQuestion('How do trees lower surrounding temperatures?'), options: ['By increasing traffic.', 'By releasing water vapor.', 'By removing all buildings.', 'By preventing every heat wave.'], correct: 1, student: 3, evidence: ['s2'], explanation: bi('第1段落の by releasing water vapor が根拠です。every heat wave は本文にない断定です。', '依据第一段的 by releasing water vapor。every heat wave 是原文没有的绝对表述。'), translation: bi('木々はどのように周囲の気温を下げますか。', '树木如何降低周围温度？'), optionNotes: [bi('交通量には言及していません。', '原文没有提到车流量。'), bi('本文の手段をそのまま言い換えています。', '与原文表达的方式相符。'), bi('建物の撤去には言及していません。', '没有提到拆除建筑。'), bi('すべての熱波を防ぐとは述べていません。', '没有说可以阻止所有热浪。')] },
    { ...newQuestion('What problem is described in the third paragraph?'), options: ['Trees always increase stress.', 'All neighborhoods have too many trees.', 'Tree cover is not distributed equally.', 'Air pollution has disappeared.'], correct: 2, student: 2, evidence: ['s5'], explanation: bi('低所得地域に木々が少ないことが分配の不平等を示しています。', '低收入地区树木较少，说明树木覆盖分布不均。'), translation: bi('第3段落で述べられている問題は何ですか。', '第三段描述了什么问题？'), optionNotes: [bi('ストレスへの効果と逆です。', '与原文的减压效果相反。'), bi('低所得地域には木が少ないと述べています。', '原文说低收入地区树木较少。'), bi('第3段落の内容に一致します。', '符合第三段内容。'), bi('汚染は今も問題とされています。', '污染仍然是问题。')] },
    { ...newQuestion('What does the author recommend?'), options: ['Treating trees as public infrastructure.', 'Reducing green spaces.', 'Planting trees only in rich areas.', 'Focusing only on appearance.'], correct: 0, student: 0, evidence: ['s6'], explanation: bi('最後の文に need to treat urban trees as public infrastructure とあります。', '末句明确写道 need to treat urban trees as public infrastructure。'), translation: bi('筆者は何を提言していますか。', '作者提出了什么建议？'), optionNotes: [bi('結論の提言に一致します。', '符合结论建议。'), bi('緑地を減らす提言はありません。', '没有建议减少绿地。'), bi('公平な分配という主旨と反します。', '与公平分配的主旨相反。'), bi('見た目以上の価値を強調しています。', '文章强调超越外观的价值。')] }
  ]
  record.vocabulary = [
    { id: 'v1', term: 'pollutant', pronunciation: '/pəˈluːtənt/', meaning: bi('名詞：汚染物質', '名词：污染物'), context: 'filter airborne pollutants', usage: bi('air / water pollutant。可算名詞です。', 'air / water pollutant，为可数名词。'), book: '' },
    { id: 'v2', term: 'be associated with', pronunciation: '/əˈsəʊsieɪtɪd/', meaning: bi('〜と関連がある', '与……相关'), context: 'is associated with lower stress levels', usage: bi('関連を示す表現で、因果関係の断定ではありません。', '表示相关，不直接表示因果关系。'), book: '' },
    { id: 'v3', term: 'vulnerable', pronunciation: '/ˈvʌlnərəbl/', meaning: bi('形容詞：影響を受けやすい', '形容词：脆弱的；易受影响的'), context: 'vulnerable to extreme heat', usage: bi('be vulnerable to + 名詞。', 'be vulnerable to + 名词。'), book: '' },
    { id: 'v4', term: 'infrastructure', pronunciation: '/ˈɪnfrəstrʌktʃə/', meaning: bi('名詞：社会基盤', '名词：基础设施'), context: 'public infrastructure', usage: bi('通常は不可算名詞。transport infrastructure など。', '通常为不可数名词，如 transport infrastructure。'), book: '' }
  ]
  record.images = [sampleImage(record.title, record.paragraphs.flatMap(p => p.sentences).flatMap(s => s.english.match(/.{1,86}(?:\s|$)/g) ?? [s.english]).slice(0, 10), 'PASSAGE'), sampleImage('Reading Questions', record.questions.flatMap((q, i) => [`Q${i + 1}. ${q.text}`, ...q.options.map((o, j) => `${String.fromCharCode(65 + j)}. ${o}`)]).slice(0, 10))]
  return record
}
export function intensiveSeeds(): IntensiveRecord[] {
  const ready = readingSample()
  const draft = blankIntensive()
  Object.assign(draft, { id: 'reading-2', title: 'How Sleep Supports Memory', sourceType: 'PRACTICE', sourceName: '学校配布プリント', chapter: 'Unit 5', grade: '2級及び以下', difficulty: '低', theme: '健康・脳科学', stageError: '画像の一部が不鮮明です。画像を確認してOCRを再実行してください。', stages: ['ERROR', 'WAITING', 'WAITING', 'WAITING'], images: [sampleImage('How Sleep Supports Memory', ['Sleep supports learning and memory.', 'A sample page awaiting OCR review.'], 'PASSAGE')] })
  return [copy(ready), draft]
}
