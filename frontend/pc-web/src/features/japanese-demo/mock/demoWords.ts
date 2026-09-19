/**
 * 日本語勉強【単語情報管理】デモ（画面確認用の試作）のモックデータ。
 *
 * **このファイルはデモ専用です。**実際の教材・辞書・AI の出力ではありません。
 * - 本番の API・DB・AI・TTS には一切つながりません（`mock/` と `store/` だけで完結する）。
 * - 語の意味・例文・会話・練習問題は、画面を確認するために書き下ろしたサンプルで、
 *   特定の教材からの転載ではありません。教材の紙面じょうの表記（`listedWord` など）は
 *   「教材によって表記が少し違う」ことを見せるための**見本**です。
 * - `jlpt` は**例示値**です。権威ある判定ではないので、画面では「例」と明示してください。
 * - 発音のアクセント型は、確実に言えるものだけを入れ、迷うものは `null` にしています。
 * - 語源・語頻度・歴史的な由来は書いていません（推測で作らないため）。
 *   `memoryHint.basis` は「漢字の形のイメージ」「場面の連想」だけを書いています。
 * - `hasAudioSample` は全語 `false`（デモ用の音声ファイルは存在しない）。
 *
 * 状態（詳細情報）のばらつきは意図的です。未生成・生成中・失敗・手で修正・
 * 一部の項目が空、を全部入れてあるので、一覧と詳細の全パターンを確認できます。
 */

import type { DemoBook, DemoWord } from '../types'

/** デモの書籍 3 冊 */
export const DEMO_BOOKS: DemoBook[] = [
  {
    id: 'book-demo-basic',
    name: 'デモ日本語 初級',
    unitSize: 20,
    units: [
      { name: 'Unit001', count: 20, capacity: 20 },
      { name: 'Unit002', count: 20, capacity: 20 },
      { name: 'Unit003', count: 20, capacity: 20 },
      { name: 'Unit004', count: 18, capacity: 20 }
    ],
    note: '標準の Unit サイズ（20 語）。いちばん新しい Unit004 だけ 18 語で、上限に届いていない。'
  },
  {
    id: 'book-school-life',
    name: '学校生活のことば',
    unitSize: 20,
    units: [
      { name: 'Unit001', count: 20, capacity: 20 },
      { name: 'Unit002', count: 14, capacity: 20 },
      { name: 'Unit003', count: 8, capacity: 20 }
    ],
    note: '学校の場面のことばを集めた副教材。Unit003 は入力の途中で、まだ 8 語しか入っていない。'
  },
  {
    id: 'book-daily-conversation',
    name: '日常会話ステップアップ',
    unitSize: 20,
    units: [
      { name: 'Unit001', count: 20, capacity: 20 },
      { name: 'Unit002', count: 5, capacity: 20 }
    ],
    note: '会話中心の教材。いちばん新しい Unit002 は 5 語だけ入った状態。'
  }
]

/** デモの単語（識別しやすいよう、状態ごとに並べる） */
export const DEMO_WORDS: DemoWord[] = [
  // ── 名詞 ────────────────────────────────────────────────────────────────
  {
    id: 'w-toshokan',
    heading: '図書館',
    reading: 'としょかん',
    alternateReading: null,
    partOfSpeech: '名詞',
    jlpt: 'N5',
    chineseMeaning: '图书馆',
    detailStatus: 'GENERATED',
    failureReason: null,
    manuallyEdited: false,
    collections: [
      { id: 'w-toshokan-col-1', book: 'デモ日本語 初級', unit: 'Unit001', seq: 5, listedWord: '図書館', listedReading: 'としょかん', listedChinese: '图书馆' },
      { id: 'w-toshokan-col-2', book: '学校生活のことば', unit: 'Unit001', seq: 12, listedWord: '図書館', listedReading: 'としょかん', listedChinese: '图书馆；（学校的）图书室' }
    ],
    detail: {
      coreMeaning: '本や資料を集めて、だれでも読めるようにしている公共の施設。',
      descriptionJa:
        '「図書館」は、本や資料を集めて、だれでも利用できるようにしている施設です。市や区が運営している大きなものを指すことが多く、学校の中にある小さめの部屋は「図書室」と呼ぶのが普通です。',
      descriptionZh:
        '「図書館」指收藏图书和资料、面向公众开放的设施，多指市、区运营的公共图书馆；学校内部的小型图书室通常叫「図書室」。日语里说「図書館で勉強する」（在图书馆学习），场所用助词「で」表示，这是中文母语者最容易出错的地方之一。',
      senses: [
        { id: 'w-toshokan-sense-1', number: 1, japanese: '本や資料を集めて、だれでも利用できるようにした施設。', chinese: '图书馆：收藏图书资料、面向公众开放的设施。', context: '公共施設の話、道案内', style: '普通' },
        { id: 'w-toshokan-sense-2', number: 2, japanese: '勉強や調べ物をする「場所」としての図書館。', chinese: '（作为学习、查资料之场所的）图书馆。', context: '学校生活・試験勉強', style: '普通' }
      ],
      examples: [
        { id: 'w-toshokan-ex-1', japanese: '駅の前に新しい図書館ができました。', reading: 'えきの まえに あたらしい としょかんが できました。', chinese: '车站前面建了一座新的图书馆。', senseNumber: 1, source: '道案内の会話', level: 'BASIC' },
        { id: 'w-toshokan-ex-2', japanese: '図書館で日本の歴史について調べました。', reading: 'としょかんで にほんの れきしについて しらべました。', chinese: '我在图书馆查了关于日本历史的资料。', senseNumber: 2, source: '学校の課題', level: 'BASIC' },
        { id: 'w-toshokan-ex-3', japanese: 'テストが近いので、週末はずっと市の図書館にこもって、配布された資料と過去問を交互に読みながら復習するつもりです。', reading: 'テストが ちかいので、しゅうまつは ずっと しの としょかんに こもって、はいふされた しりょうと かこもんを こうごに よみながら ふくしゅうする つもりです。', chinese: '因为快考试了，我打算周末一直待在市图书馆里，把发下来的资料和历年真题交替着读，一边读一边复习。', senseNumber: 2, source: '学生の会話（応用）', level: 'APPLIED' }
      ],
      patterns: [
        { id: 'w-toshokan-pat-1', pattern: '図書館**で**勉強する', reading: 'としょかんで べんきょうする', chinese: '在图书馆学习（「で」表示动作进行的场所）', example: '毎日、図書館で勉強しています。', exampleChinese: '我每天都在图书馆学习。' },
        { id: 'w-toshokan-pat-2', pattern: '図書館**に**行く／図書館**から**本を借りる', reading: 'としょかんに いく／としょかんから ほんを かりる', chinese: '去图书馆／从图书馆借书（「に」是方向，「から」是出处）', example: '図書館から本を三冊借りました。', exampleChinese: '我从图书馆借了三本书。' }
      ],
      dialogs: [
        {
          id: 'w-toshokan-dlg-1',
          scene: '学校の廊下で、友だちと週末の予定を話す',
          lines: [
            { id: 'w-toshokan-dlg-1-l1', speaker: '学生A', japanese: '週末、どこか静かなところで勉強したいんだけど、いい場所ある？', chinese: '周末我想找个安静的地方学习，有什么好地方吗？' },
            { id: 'w-toshokan-dlg-1-l2', speaker: '学生B', japanese: 'それなら市の図書館がいいよ。自習室があって、無料で使えるんだ。', chinese: '那市立图书馆不错。里面有自习室，可以免费使用。' },
            { id: 'w-toshokan-dlg-1-l3', speaker: '学生A', japanese: 'いいね。何時ごろまで開いてるの？', chinese: '不错啊。开到几点？' },
            { id: 'w-toshokan-dlg-1-l4', speaker: '学生B', japanese: '平日は八時までだけど、土曜日は六時までだよ。', chinese: '平日开到八点，周六到六点。' }
          ]
        }
      ],
      synonyms: [
        {
          id: 'w-toshokan-syn-1',
          heading: '図書室',
          reading: 'としょしつ',
          chinese: '图书室',
          shared: '本を読んだり借りたりする施設・部屋という点。',
          difference:
            '「図書室」は学校や会社の中にある小さめの部屋を指すことが多く、市や区の大きな施設は「図書館」と呼ぶのが普通です。',
          usage: '学校の中の施設を言うとき',
          interchangeable: 'SOMETIMES',
          interchangeNote:
            '学校の中の施設なら「図書室」が自然ですが、地域の公共施設を「図書室」と呼ぶと不自然に聞こえます。逆に、大学の大きな施設は「図書館」と呼ぶこともあります。',
          exampleJapanese: '学校の図書室で本を借りました。',
          exampleChinese: '我在学校图书室借了书。'
        }
      ],
      cautions: [
        { id: 'w-toshokan-cau-1', kind: 'PARTICLE', title: '「図書館に勉強します」は助詞が違う', wrong: '図書館に勉強します。', correct: '図書館で勉強します。', reason: '「勉強する」は動作なので、その場所は「で」で示します。「に」は行き先（図書館に行く）に使います。中国語の“在图书馆学习”の「在」をそのまま「に」にしないよう注意してください。' },
        { id: 'w-toshokan-cau-2', kind: 'UNNATURAL', title: '「図書館を使う」は場面によって不自然', wrong: '今日は図書館を使います。', correct: '今日は図書館を利用します。／今日は図書館で勉強します。', reason: 'カジュアルな会話では「図書館を使う」も通じますが、掲示物や案内文では「利用する」のほうが自然です。公共施設には「利用する」が合います。' }
      ],
      conjugations: [],
      transitivityPair: null,
      pronunciation: { reading: 'としょかん', accentType: 4, accentNotation: 'としょかꜜん', hint: '「と」は低く始まり、「か」のあとで下がります（としょかꜜん）。中国語の「图书馆」のように一音ずつ区切らず、「としょかん」をひとまとまりで発音します。', hasAudioSample: false },
      collocations: [
        { id: 'w-toshokan-coll-1', expression: '図書館に行く', reading: 'としょかんに いく', chinese: '去图书馆', usage: '休日の予定を話すとき' },
        { id: 'w-toshokan-coll-2', expression: '図書館で本を借りる', reading: 'としょかんで ほんを かりる', chinese: '在图书馆借书', usage: '図書館の利用について話すとき' }
      ],
      relatedWords: [
        { id: 'w-toshokan-rel-1', relation: '間違えやすい', heading: '図書室', reading: 'としょしつ', chinese: '图书室' },
        { id: 'w-toshokan-rel-2', relation: '間違えやすい', heading: '本屋', reading: 'ほんや', chinese: '书店（买书的地方，不是借书的地方）' }
      ],
      usageNotes: [
        { id: 'w-toshokan-use-1', register: 'どちらも', politeness: '普通', audience: '友だち・同僚', note: '会話でも文章でも同じように使えます。', senseNumber: null },
        { id: 'w-toshokan-use-2', register: '書き言葉', politeness: '丁寧', audience: '学校の案内・掲示', note: '「図書館を利用する」のように動詞と組み合わせると、案内文らしい硬い表現になります。', senseNumber: 2 }
      ],
      memoryHint: { hint: '「図書館＝テスト前にこもる静かな場所」と場面ごと覚える。', basis: '場面の連想（テスト前に通う場所）と、「館」という大きな建物の字の形を結びつけた覚え方。' },
      practices: [
        { id: 'w-toshokan-pr-1', kind: 'PARTICLE', question: '図書館（　）勉強します。', questionChinese: '选择正确的助词填空。', choices: ['に', 'で', 'を', 'へ'], answer: 'で', explanation: '「勉強する」という動作をする場所なので「で」を使います。「に」「へ」は行き先に使うので、ここでは合いません。', freeWriting: false, demoFeedback: null },
        { id: 'w-toshokan-pr-2', kind: 'SCENE', question: '友だちに「週末はどこで勉強するの？」と聞かれて、市の図書館で勉強すると答えたい。いちばん自然なのはどれですか。', questionChinese: '朋友问“周末在哪儿学习？”，你想回答“打算在市图书馆学习”。哪个最自然？', choices: [ '市の図書館で勉強するつもり。', '市の図書館に勉強するつもり。', '市の図書館へ勉強しますつもり。' ], answer: '市の図書館で勉強するつもり。', explanation: '「場所＋で＋動作」の形にします。「に」「へ」は行き先に使うので、動作の場所には合いません。', freeWriting: false, demoFeedback: null },
        {
          id: 'w-toshokan-pr-3',
          kind: 'WRITING',
          question: 'あなたがよく行く場所について、「〜で〜する」を使って一文書いてみましょう。',
          questionChinese: '用「〜で〜する」的句型，写一句关于你常去的地方的话。',
          choices: [],
          answer: 'テストの前は、いつも市の図書館で勉強します。',
          explanation: '「場所＋で＋動作」がそろっていれば大丈夫です。',
          freeWriting: true,
          demoFeedback: { grammar: '「場所＋で＋動作」の形が正しく使えています。', naturalness: '「いつも」を入れると習慣らしさが出て、より自然に聞こえます。', reference: 'テストの前は、いつも市の図書館で勉強しています。' }
        }
      ]
    },
    updatedAt: '2026-09-14T10:20:00',
    demoNote: null
  },
  {
    id: 'w-soudan',
    heading: '相談',
    reading: 'そうだん',
    alternateReading: null,
    partOfSpeech: '名詞',
    jlpt: 'N4',
    chineseMeaning: '商量；咨询',
    detailStatus: 'GENERATED',
    failureReason: null,
    manuallyEdited: false,
    collections: [
      { id: 'w-soudan-col-1', book: 'デモ日本語 初級', unit: 'Unit002', seq: 4, listedWord: '相談', listedReading: 'そうだん', listedChinese: '商量' }
    ],
    detail: {
      coreMeaning: '困ったことや決められないことを人に話して、意見や助けを求めること。',
      descriptionJa:
        '「相談」は、自分だけでは決められないことや困っていることを、人に話して意見を聞くことです。名詞ですが、「相談する」の形でもよく使います。',
      descriptionZh:
        '「相談」指把自己难以决定或正在烦恼的事情说给别人听、征求对方的意见。它本身是名词，但也经常以「相談する」的形式使用。',
      senses: [
        { id: 'w-soudan-sense-1', number: 1, japanese: '決められないことについて、人の意見を聞くこと。', chinese: '商量、征求意见：就难以决定的事听取别人的意见。', context: '進路・予定・買い物を決めるとき', style: '普通' },
        { id: 'w-soudan-sense-2', number: 2, japanese: '困りごとを、専門の窓口や担当者に持ち込むこと。', chinese: '咨询：把困难反映给专门的窗口或负责人。', context: '学校の窓口・市役所・会社', style: 'やや硬い' }
      ],
      examples: [
        { id: 'w-soudan-ex-1', japanese: '進路のことは、両親と相談して決めました。', reading: 'しんろの ことは、りょうしんと そうだんして きめました。', chinese: '升学方向的事，我是和父母商量之后决定的。', senseNumber: 1, source: '学校生活', level: 'BASIC' },
        { id: 'w-soudan-ex-2', japanese: '奨学金のことは、学生課の窓口で相談できます。', reading: 'しょうがくきんの ことは、がくせいかの まどぐちで そうだんできます。', chinese: '奖学金的事可以在学生科的窗口咨询。', senseNumber: 2, source: '大学の案内', level: 'BASIC' },
        { id: 'w-soudan-ex-3', japanese: 'アルバイトと研究の両立で悩んでいたので、指導教員に相談してみたところ、無理のない予定の組み方を教えてもらえました。', reading: 'アルバイトと けんきゅうの りょうりつで なやんでいたので、しどうきょういんに そうだんしてみたところ、むりの ない よていの くみかたを おしえてもらえました。', chinese: '我正为如何兼顾打工和研究而发愁，找导师商量了一下，他教给了我一整套不会太勉强的日程安排方法。', senseNumber: 1, source: '大学院生の会話（応用）', level: 'APPLIED' }
      ],
      patterns: [
        { id: 'w-soudan-pat-1', pattern: '人**に**相談する', reading: 'ひとに そうだんする', chinese: '向某人商量／咨询（商量的对象用「に」）', example: '友だちに相談してみたら？', exampleChinese: '要不要找朋友商量一下？' },
        { id: 'w-soudan-pat-2', pattern: '相談**に**乗る', reading: 'そうだんに のる', chinese: '接受商量、帮人出主意（惯用说法）', example: '課長が相談に乗ってくれました。', exampleChinese: '科长帮我出了主意。' }
      ],
      dialogs: [
        {
          id: 'w-soudan-dlg-1',
          scene: '大学の学生課の窓口で',
          lines: [
            { id: 'w-soudan-dlg-1-l1', speaker: '学生', japanese: 'すみません、来学期の履修のことで相談したいんですが、今よろしいですか。', chinese: '不好意思，我想咨询一下下学期选课的事，现在方便吗？' },
            { id: 'w-soudan-dlg-1-l2', speaker: '職員', japanese: 'はい、どうぞ。どの科目でお悩みですか。', chinese: '好的，请说。您对哪门课有疑问呢？' },
            { id: 'w-soudan-dlg-1-l3', speaker: '学生', japanese: '専門の科目と語学の授業が重なっていて、どちらを選ぶか決められなくて。', chinese: '专业课和语言课时间冲突了，我定不下来选哪个。' },
            { id: 'w-soudan-dlg-1-l4', speaker: '職員', japanese: 'では、卒業に必要な単位を一緒に確認しましょう。', chinese: '那我们一起确认一下毕业所需的学分吧。' }
          ]
        }
      ],
      synonyms: [
        {
          id: 'w-soudan-syn-1',
          heading: '話し合う',
          reading: 'はなしあう',
          chinese: '商谈、一起讨论',
          shared: '複数の人で話すという点。',
          difference:
            '「相談する」は意見を聞きたい相手がいて、こちらが判断を委ねる感じがあります。「話し合う」は立場が対等で、結論を一緒に作っていく感じです。',
          usage: '予定や方針を決めるとき',
          interchangeable: 'SOMETIMES',
          interchangeNote:
            '相手の意見を聞きたいときは「相談する」、対等に議論したいときは「話し合う」が合います。どちらでも通じる場面は多いですが、目上の人には「相談する」のほうが自然です。',
          exampleJapanese: 'クラス全員で話し合って、文化祭の出し物を決めました。',
          exampleChinese: '全班一起讨论，决定了文化节的节目。'
        }
      ],
      cautions: [
        { id: 'w-soudan-cau-1', kind: 'PARTICLE', title: '「先生を相談する」は助詞が違う', wrong: '先生を相談する。', correct: '先生に相談する。', reason: '相談する相手は「に」で示します。中国語の“跟老师商量”の「跟」をそのまま「を」にしないでください。いっしょに考える場合は「先生と相談する」も使えます。' }
      ],
      conjugations: [],
      transitivityPair: null,
      pronunciation: { reading: 'そうだん', accentType: null, accentNotation: null, hint: '「そうだん」は「そ・う・だ・ん」の4拍です。アクセントの型は資料によって扱いが分かれるため、このデモでは表示していません。単語だけで覚えず、「そうだんする」の形でリズムを取ると安定します。', hasAudioSample: false },
      collocations: [
        { id: 'w-soudan-coll-1', expression: '相談に乗る', reading: 'そうだんに のる', chinese: '接受商量、帮忙出主意', usage: '人が悩んでいるとき' },
        { id: 'w-soudan-coll-2', expression: '相談がまとまる', reading: 'そうだんが まとまる', chinese: '商量出结果、谈妥', usage: '打ち合わせや交渉のあと' }
      ],
      relatedWords: [
        { id: 'w-soudan-rel-1', relation: '類義語', heading: '助言', reading: 'じょげん', chinese: '建议、忠告（偏书面语）' },
        { id: 'w-soudan-rel-2', relation: '間違えやすい', heading: '打ち合わせ', reading: 'うちあわせ', chinese: '碰头商谈、磋商（为了推进事情而事先碰面）' }
      ],
      usageNotes: [
        { id: 'w-soudan-use-1', register: 'どちらも', politeness: '普通', audience: '友だち・家族', note: '「ちょっと相談があるんだけど」は、話を切り出すときの決まり文句です。', senseNumber: 1 }
      ],
      memoryHint: { hint: '「相」＝向かい合う、「談」＝話す。向かい合って話す場面を思い出す。', basis: '漢字の形のイメージ（向かい合って話す）と、学生課の窓口で相談している場面の連想。' },
      practices: [
        { id: 'w-soudan-pr-1', kind: 'PARTICLE', question: '進路のことは、両親（　）相談して決めました。', questionChinese: '选择正确的助词填空。', choices: ['に', 'を', 'が', 'へ'], answer: 'に', explanation: '相談する相手は「に」で示します。「と」も使えますが、この文は両親の意見を聞いて決めたので「に」が合います。', freeWriting: false, demoFeedback: null },
        { id: 'w-soudan-pr-2', kind: 'SYNONYM', question: '授業の取り方について、先生と（　）たいです。いちばん自然なのはどれですか。', questionChinese: '想跟老师商量选课的事，哪个词最自然？', choices: ['相談し', '話し合い', '討論し'], answer: '相談し', explanation: '自分の判断を助けてもらう場面なので「相談する」が自然です。「話し合う」は対等な関係、「討論する」は意見が対立する場面で使います。', freeWriting: false, demoFeedback: null }
      ]
    },
    updatedAt: '2026-09-14T11:05:00',
    demoNote: null
  },
  {
    id: 'w-junbi',
    heading: '準備',
    reading: 'じゅんび',
    alternateReading: null,
    partOfSpeech: '名詞',
    jlpt: 'N4',
    chineseMeaning: '准备',
    detailStatus: 'RUNNING',
    failureReason: null,
    manuallyEdited: false,
    collections: [
      { id: 'w-junbi-col-1', book: 'デモ日本語 初級', unit: 'Unit002', seq: 11, listedWord: '準備', listedReading: 'じゅんび', listedChinese: '准备' },
      { id: 'w-junbi-col-2', book: '学校生活のことば', unit: 'Unit003', seq: 2, listedWord: '準備', listedReading: 'じゅんび', listedChinese: '准备（上课前）' }
    ],
    detail: null,
    updatedAt: '2026-09-14T09:58:00',
    demoNote: '生成中（デモの演出用。しばらくすると生成済みになる）'
  },
  {
    id: 'w-shukudai',
    heading: '宿題',
    reading: 'しゅくだい',
    alternateReading: null,
    partOfSpeech: '名詞',
    jlpt: 'N5',
    chineseMeaning: '作业；待解决的课题',
    detailStatus: 'GENERATED',
    failureReason: null,
    manuallyEdited: false,
    collections: [
      { id: 'w-shukudai-col-1', book: 'デモ日本語 初級', unit: 'Unit001', seq: 3, listedWord: '宿題', listedReading: 'しゅくだい', listedChinese: '作业' }
    ],
    detail: {
      coreMeaning: '学校で出されて、家でやってくる課題。',
      descriptionJa:
        '「宿題」は、学校や塾で先生から出されて、家でやってくる課題のことです。「宿題をする」「宿題を出す」のように、動詞と組み合わせて使います。',
      descriptionZh:
        '「宿題」指学校或补习班老师布置、让学生在家完成的作业。日语里常说「宿題をする」（做作业）、「宿題を出す」（交作业）。另外，它还可以比喻“留待解决的课题”。',
      senses: [
        { id: 'w-shukudai-sense-1', number: 1, japanese: '学校で出されて、家でやってくる課題。', chinese: '作业：学校布置、在家完成的功课。', context: '学校生活・家庭学習', style: '普通' },
        { id: 'w-shukudai-sense-2', number: 2, japanese: 'まだ解決していない、これからの課題。', chinese: '（比喻）还没解决、留待今后处理的课题。', context: '記事・会議の資料', style: 'やや硬い' }
      ],
      examples: [
        { id: 'w-shukudai-ex-1', japanese: '今日の宿題は、漢字の練習と読解の問題です。', reading: 'きょうの しゅくだいは、かんじの れんしゅうと どっかいの もんだいです。', chinese: '今天的作业是汉字练习和阅读理解题。', senseNumber: 1, source: '学校生活', level: 'BASIC' },
        { id: 'w-shukudai-ex-2', japanese: '宿題を忘れたので、明日までに出してもいいか先生に聞きました。', reading: 'しゅくだいを わすれたので、あしたまでに だしても いいか せんせいに ききました。', chinese: '我忘了写作业，就问老师能不能明天之前交。', senseNumber: 1, source: '教室での会話', level: 'BASIC' },
        { id: 'w-shukudai-ex-3', japanese: '外国語を仕事で使えるようにするのは、私たちのチームの次の宿題です。', reading: 'がいこくごを しごとで つかえるようにするのは、わたしたちの チームの つぎの しゅくだいです。', chinese: '如何让外语在工作中派上用场，是我们团队接下来要解决的课题。', senseNumber: 2, source: '会議の資料（応用）', level: 'APPLIED' }
      ],
      patterns: [
        { id: 'w-shukudai-pat-1', pattern: '宿題**を**する／宿題**を**やる', reading: 'しゅくだいを する／しゅくだいを やる', chinese: '做作业（口语里常用「やる」）', example: '宿題はもうやった？', exampleChinese: '作业做了吗？' },
        { id: 'w-shukudai-pat-2', pattern: '宿題**を**出す', reading: 'しゅくだいを だす', chinese: '交作业（「出す」是提交的意思）', example: '宿題は明日の朝、出してください。', exampleChinese: '作业请明天早上交。' }
      ],
      dialogs: [],
      synonyms: [
        { id: 'w-shukudai-syn-1', heading: '課題', reading: 'かだい', chinese: '课题、任务', shared: '家や授業の外で取り組むものという点。', difference: '「宿題」は先生から出されるものですが、「課題」は自分で設定するものにも使え、レポートや研究のテーマにも使えます。', usage: '学校・仕事の文章', interchangeable: 'SOMETIMES', interchangeNote: '先生から出されたものなら「宿題」が自然ですが、大学のレポート課題などは「課題」が普通です。', exampleJapanese: '来週までにレポートの課題を出してください。', exampleChinese: '请在下周之前提交报告作业。' }
      ],
      cautions: [
        { id: 'w-shukudai-cau-1', kind: 'PARTICLE', title: '「宿題がします」は助詞が違う', wrong: '宿題がします。', correct: '宿題をします。', reason: '「する」の対象は「を」で示します。中国語の“做作业”は動詞＋目的語の語順ですが、日本語では「宿題をする」のように助詞が必要です。' }
      ],
      conjugations: [],
      transitivityPair: null,
      pronunciation: { reading: 'しゅくだい', accentType: null, accentNotation: null, hint: '「しゅくだい」は「しゅ・く・だ・い」の4拍です。小さい「ゅ」は単独では拍にならないので、「し・ゅ・く・だ・い」と5拍にしないように注意してください。', hasAudioSample: false },
      collocations: [
        { id: 'w-shukudai-coll-1', expression: '宿題をする', reading: 'しゅくだいを する', chinese: '做作业', usage: '家庭学習の話' },
        { id: 'w-shukudai-coll-2', expression: '宿題がたまる', reading: 'しゅくだいが たまる', chinese: '作业堆积起来（没做完）', usage: '忙しくて手が回らないとき' }
      ],
      relatedWords: [
        { id: 'w-shukudai-rel-1', relation: '類義語', heading: '課題', reading: 'かだい', chinese: '课题、任务' },
        { id: 'w-shukudai-rel-2', relation: '間違えやすい', heading: '復習', reading: 'ふくしゅう', chinese: '复习（自己为了巩固而做，不是老师布置的）' }
      ],
      usageNotes: [
        { id: 'w-shukudai-use-1', register: 'どちらも', politeness: '普通', audience: '友だち・先生', note: '「宿題を出す」は「提出する」の意味です。「出す」だけで通じます。', senseNumber: 1 }
      ],
      memoryHint: { hint: '「宿」＝家に持ち帰る、「題」＝問題。家に持って帰る問題、と覚える。', basis: '漢字の字面のイメージ（家に持ち帰る問題）と、放課後に宿題をやる場面の連想。' },
      practices: [
        { id: 'w-shukudai-pr-1', kind: 'PARTICLE', question: '宿題（　）明日までに出してください。', questionChinese: '选择正确的助词填空。', choices: ['を', 'が', 'に', 'で'], answer: 'を', explanation: '「出す」の対象なので「を」を使います。期限は「までに」で示します。', freeWriting: false, demoFeedback: null },
        { id: 'w-shukudai-pr-2', kind: 'SCENE', question: '先生に「宿題はもう出しましたか」と聞かれて、まだだと伝えたい。いちばん自然なのはどれですか。', questionChinese: '老师问“作业交了吗？”，你想说还没交。哪个最自然？', choices: [ 'すみません、まだやっていません。', 'すみません、宿題がしませんでした。', 'すみません、宿題を忘れましたです。' ], answer: 'すみません、まだやっていません。', explanation: 'まだ終わっていないことは「まだ〜ていません」で表します。「忘れましたです」のように「です」を重ねる形は誤りです。', freeWriting: false, demoFeedback: null }
      ]
    },
    updatedAt: '2026-09-14T13:15:00',
    demoNote: null
  },
  {
    id: 'w-benkyou',
    heading: '勉強',
    reading: 'べんきょう',
    alternateReading: null,
    partOfSpeech: '名詞・動詞',
    jlpt: 'N5',
    chineseMeaning: '学习；用功（中文的“勉强”是另一个意思）',
    detailStatus: 'GENERATED',
    failureReason: null,
    manuallyEdited: false,
    collections: [
      { id: 'w-benkyou-col-1', book: 'デモ日本語 初級', unit: 'Unit002', seq: 3, listedWord: '勉強', listedReading: 'べんきょう', listedChinese: '学习' },
      { id: 'w-benkyou-col-2', book: '学校生活のことば', unit: 'Unit001', seq: 1, listedWord: '勉強する', listedReading: 'べんきょうする', listedChinese: '学习；用功' },
      { id: 'w-benkyou-col-3', book: '日常会話ステップアップ', unit: 'Unit002', seq: 2, listedWord: '勉強', listedReading: 'べんきょう', listedChinese: '学习' }
    ],
    detail: {
      coreMeaning: '知識や技術を身につけようと、練習や努力を重ねること。',
      descriptionJa:
        '「勉強」は、知識や技術を身につけるために、繰り返し練習したり覚えたりすることです。名詞としても、動詞「勉強する」としても使います。中国語の「勉强」とは意味が違うので注意してください。',
      descriptionZh:
        '「勉強」指为了掌握知识或技能而反复练习、记忆。它既能作名词，也能作动词「勉強する」。请注意：中文的“勉强”是“不情愿、将就”的意思，和日语的「勉強＝学习」完全不同。此外「勉強になる」是“长见识、有参考价值”，也和“勉强”毫无关系。',
      senses: [
        { id: 'w-benkyou-sense-1', number: 1, japanese: '知識や技術を身につけるために、練習したり覚えたりすること。', chinese: '学习：为掌握知识或技能而练习、记忆。', context: '学校・試験・独学', style: '普通' },
        { id: 'w-benkyou-sense-2', number: 2, japanese: '経験や人の話から、ためになることを得ること（「勉強になる」の形で）。', chinese: '（以「勉強になる」的形式）长见识、有收获。', context: '感想・会話', style: '話し言葉' }
      ],
      examples: [
        { id: 'w-benkyou-ex-1', japanese: '毎晩一時間、日本語を勉強しています。', reading: 'まいばん いちじかん、にほんごを べんきょうしています。', chinese: '我每天晚上学习一个小时日语。', senseNumber: 1, source: '学生の生活', level: 'BASIC' },
        { id: 'w-benkyou-ex-2', japanese: '難しい本を一冊読み切ったので、いい勉強になりました。', reading: 'むずかしい ほんを いっさつ よみきったので、いい べんきょうに なりました。', chinese: '我把一本很难的书读完了，觉得很有收获。', senseNumber: 2, source: '読書の感想', level: 'BASIC' },
        { id: 'w-benkyou-ex-3', japanese: '試験のためだけの勉強ではなく、社会に出てからも使える力を身につけたいと思っています。', reading: 'しけんの ためだけの べんきょうでは なく、しゃかいに でてからも つかえる ちからを みにつけたいと おもっています。', chinese: '我不想只为考试而学习，而是希望掌握走上社会之后也能用得上的能力。', senseNumber: 1, source: '面接の受け答え（応用）', level: 'APPLIED' }
      ],
      patterns: [
        { id: 'w-benkyou-pat-1', pattern: '〜**を**勉強する', reading: '〜を べんきょうする', chinese: '学习某个科目或内容（对象用「を」）', example: '来年から中国語を勉強します。', exampleChinese: '我从明年开始学中文。' },
        { id: 'w-benkyou-pat-2', pattern: '〜**のために**勉強する', reading: '〜のために べんきょうする', chinese: '为了……而学习（目的用「のために」）', example: '留学のために勉強しています。', exampleChinese: '我正在为留学而学习。' }
      ],
      dialogs: [
        {
          id: 'w-benkyou-dlg-1',
          scene: '放課後、教室で先生に勉強のやり方を聞く',
          lines: [
            { id: 'w-benkyou-dlg-1-l1', speaker: '学生', japanese: '先生、漢字がなかなか覚えられないんです。どうやって勉強したらいいですか。', chinese: '老师，汉字我怎么也记不住。该怎么学才好呢？' },
            { id: 'w-benkyou-dlg-1-l2', speaker: '先生', japanese: '一度にたくさん覚えようとしないで、毎日少しずつ書くといいですよ。', chinese: '不要想一次记住很多，每天一点点地写比较好。' },
            { id: 'w-benkyou-dlg-1-l3', speaker: '学生', japanese: 'なるほど。じゃあ、寝る前に十分だけ復習してみます。', chinese: '原来如此。那我试着睡前只复习十分钟。' },
            { id: 'w-benkyou-dlg-1-l4', speaker: '先生', japanese: 'それがいいですね。続けることがいちばんの勉強ですから。', chinese: '这样很好。因为坚持下去才是最重要的学习。' }
          ]
        }
      ],
      synonyms: [
        { id: 'w-benkyou-syn-1', heading: '習う', reading: 'ならう', chinese: '（跟人）学', shared: '知識や技術を身につけるという点。', difference: '「勉強する」は先生がいなくても使えますが、「習う」は教えてくれる人がいる場合に使います。', usage: '教室・習い事', interchangeable: 'SOMETIMES', interchangeNote: '先生がいる場面ではどちらも使えますが、「ピアノを勉強する」より「ピアノを習う」のほうが自然です。独学には「習う」を使えません。', exampleJapanese: '妹はピアノを習っています。', exampleChinese: '我妹妹在学钢琴。' },
        { id: 'w-benkyou-syn-2', heading: '学ぶ', reading: 'まなぶ', chinese: '学习、学到', shared: '身につけるという点。', difference: '「学ぶ」はやや硬く、書き言葉や改まった場面で使いやすいです。自分から進んで身につける感じが強く、先生の存在は必要ありません。', usage: '文章・発表', interchangeable: 'SOMETIMES', interchangeNote: '会話では「勉強する」のほうが自然ですが、文章では「学ぶ」のほうが落ち着いて見えます。', exampleJapanese: '歴史から学ぶことは多い。', exampleChinese: '从历史中可以学到很多东西。' }
      ],
      cautions: [
        { id: 'w-benkyou-cau-1', kind: 'MEANING', title: '中国語の「勉强」と意味が違う', wrong: 'いやだったが、勉強してこの仕事を引き受けた。', correct: 'いやだったが、しぶしぶこの仕事を引き受けた。', reason: '中文的“勉强”是“不情愿、将就、凑合”的意思，日语的「勉強」是“学习”。想表达“勉强接受”时要用「いやいや」「しぶしぶ」。反过来，日语的「勉強になる」也不是“勉强”，而是“长见识、有收获”。' },
        { id: 'w-benkyou-cau-2', kind: 'PARTICLE', title: '「日本語が勉強します」は助詞が違う', wrong: '毎日、日本語が勉強します。', correct: '毎日、日本語を勉強します。', reason: '「勉強する」の対象は「を」で示します。「が」を使うと「日本語が（自分で）勉強する」という意味になってしまいます。' }
      ],
      conjugations: [
        { id: 'w-benkyou-conj-1', form: 'ます形', value: '勉強します', example: '毎日、日本語を勉強します。' },
        { id: 'w-benkyou-conj-2', form: 'て形', value: '勉強して', example: '図書館で勉強してから帰ります。' },
        { id: 'w-benkyou-conj-3', form: 'た形', value: '勉強した', example: 'きのうは三時間勉強した。' },
        { id: 'w-benkyou-conj-4', form: 'ない形', value: '勉強しない', example: 'きょうは勉強しないで早く寝ます。' },
        { id: 'w-benkyou-conj-5', form: '可能形', value: '勉強できる', example: '静かな場所なら集中して勉強できます。' }
      ],
      transitivityPair: null,
      pronunciation: { reading: 'べんきょう', accentType: null, accentNotation: null, hint: '「べんきょう」は「べ・ん・きょ・う」の4拍です。小さい「ょ」は前の「き」と合わせて1拍、「う」も1拍として数えます。アクセントの型はこのデモでは未確認のため表示していません。', hasAudioSample: false },
      collocations: [
        { id: 'w-benkyou-coll-1', expression: '勉強になる', reading: 'べんきょうに なる', chinese: '有收获、长见识', usage: '人の話や経験について感想を言うとき' },
        { id: 'w-benkyou-coll-2', expression: '勉強がはかどる', reading: 'べんきょうが はかどる', chinese: '学习进展顺利', usage: '集中して進んだとき' }
      ],
      relatedWords: [
        { id: 'w-benkyou-rel-1', relation: '類義語', heading: '学習', reading: 'がくしゅう', chinese: '学习（较书面）' },
        { id: 'w-benkyou-rel-2', relation: '間違えやすい', heading: '練習', reading: 'れんしゅう', chinese: '练习（反复练技能，不等于学知识）' }
      ],
      usageNotes: [
        { id: 'w-benkyou-use-1', register: 'どちらも', politeness: '普通', audience: '友だち・同僚', note: '名詞として単独でも使えます（例：「勉強が足りない」）。', senseNumber: null },
        { id: 'w-benkyou-use-2', register: '話し言葉', politeness: '普通', audience: '先生・目上の人', note: '「いい勉強になりました」は、教えてもらったことへのお礼としても使えます。', senseNumber: 2 }
      ],
      memoryHint: { hint: '「べんきょう」と聞いたら「学习」。「勉强」と訳さない。', basis: '中国語との意味の対応を一文で言い切って覚える（漢字の字面ではなく、意味の対応の連想）。' },
      practices: [
        { id: 'w-benkyou-pr-1', kind: 'PARTICLE', question: '毎日、日本語（　）勉強しています。', questionChinese: '选择正确的助词填空。', choices: ['を', 'が', 'に', 'で'], answer: 'を', explanation: '「勉強する」の対象は「を」で示します。学ぶ科目が目的語になります。', freeWriting: false, demoFeedback: null },
        { id: 'w-benkyou-pr-2', kind: 'SYNONYM', question: '妹はピアノを（　）います。いちばん自然なのはどれですか。', questionChinese: '“我妹妹在学钢琴”，哪个词最自然？', choices: ['習って', '勉強して', '学んで'], answer: '習って', explanation: '教えてくれる先生がいる場面なので「習う」が自然です。「勉強して」も誤りではありませんが、技術を教わる場面では「習う」が普通です。', freeWriting: false, demoFeedback: null },
        {
          id: 'w-benkyou-pr-3',
          kind: 'WRITING',
          question: 'あなたの勉強の習慣について、時間や場所を入れて一文書いてみましょう。',
          questionChinese: '用一句话写写你的学习习惯，可以带上时间和地点。',
          choices: [],
          answer: '私は毎朝、電車の中で十分だけ日本語を勉強しています。',
          explanation: '時間＋場所＋「を勉強する」がそろっていると分かりやすい文になります。',
          freeWriting: true,
          demoFeedback: { grammar: '「時間＋場所＋で＋対象＋を勉強する」の順が正しく組み立てられています。', naturalness: '「十分だけ」のように範囲を限定すると、続けている感じが伝わります。', reference: '私は毎朝、電車の中で十分だけ日本語の単語を勉強しています。' }
        }
      ]
    },
    updatedAt: '2026-09-14T12:40:00',
    demoNote: '日中同形異義語（名詞と動詞の両方）'
  },
  {
    id: 'w-aku',
    heading: '開く',
    reading: 'あく',
    alternateReading: 'ひらく',
    partOfSpeech: '動詞',
    jlpt: 'N4',
    chineseMeaning: '（门窗、盖子等）开、打开（自动词：原来关着的东西开了）',
    detailStatus: 'GENERATED',
    failureReason: null,
    manuallyEdited: false,
    collections: [
      { id: 'w-aku-col-1', book: 'デモ日本語 初級', unit: 'Unit003', seq: 6, listedWord: '開く', listedReading: 'あく', listedChinese: '开（门、窗）' },
      { id: 'w-aku-col-2', book: '学校生活のことば', unit: 'Unit002', seq: 5, listedWord: '開く', listedReading: 'あく・ひらく', listedChinese: '开；打开（同一表记有两种读音）' }
    ],
    detail: {
      coreMeaning: '閉まっていたものが、開いた状態になる（自動詞）。',
      descriptionJa:
        '「開く（あく）」は、閉まっていた窓やドア、ふたなどが開いた状態になる自動詞です。開いたものは「が」で示します。同じ「開く」でも「ひらく」と読む場合は、本や傘、店、会などに使います。',
      descriptionZh:
        '「開く（あく）」是自动词，表示原本关着的窗户、门、盖子等变成打开的状态，打开的东西用「が」表示。同样写「開く」，读「ひらく」时多用于书、伞、店铺、会议等，请把两个读音当作不同的词来记。',
      senses: [
        { id: 'w-aku-sense-1', number: 1, japanese: '窓・ドア・ふたなどが、閉まっている状態から開いた状態になる。', chinese: '（门窗、盖子等）开了：从关着的状态变成打开的状态。', context: '日常生活・教室', style: '普通' },
        { id: 'w-aku-sense-2', number: 2, japanese: 'かばん・口など、閉じていた部分が広がって中が見える状態になる。', chinese: '（包、嘴等）张开、敞开着。', context: '日常生活', style: '普通' }
      ],
      examples: [
        { id: 'w-aku-ex-1', japanese: '風が強くて、窓がひとりでに開きました。', reading: 'かぜが つよくて、まどが ひとりでに あきました。', chinese: '风很大，窗户自己开了。', senseNumber: 1, source: '日常生活', level: 'BASIC' },
        { id: 'w-aku-ex-2', japanese: 'かばんのふたが開いていたので、中身が少し見えました。', reading: 'かばんの ふたが あいていたので、なかみが すこし みえました。', chinese: '包的盖子开着，里面的东西露出了一点。', senseNumber: 2, source: '日常生活', level: 'BASIC' },
        { id: 'w-aku-ex-3', japanese: '非常口のドアが急に開いたので、近くにいた全員が一斉に振り返りました。', reading: 'ひじょうぐちの ドアが きゅうに あいたので、ちかくに いた ぜんいんが いっせいに ふりかえりました。', chinese: '安全出口的门突然开了，附近的同学全都一下子回过头去。', senseNumber: 1, source: '避難訓練のとき（応用）', level: 'APPLIED' }
      ],
      patterns: [
        { id: 'w-aku-pat-1', pattern: 'ドア**が**開く（あく）', reading: 'ドアが あく', chinese: '门开了（自动词，用「が」表示开了的东西）', example: '風でドアが開きました。', exampleChinese: '门被风吹开了。' },
        { id: 'w-aku-pat-2', pattern: 'ふた**が**開く（あく）', reading: 'ふたが あく', chinese: '盖子开了（自动词，重点在“变成开着的样子”）', example: 'びんのふたが開いていますよ。', exampleChinese: '瓶盖开着呢。' }
      ],
      dialogs: [
        {
          id: 'w-aku-dlg-1',
          scene: '寒い教室で、窓が開いていることに気づく',
          lines: [
            { id: 'w-aku-dlg-1-l1', speaker: '学生A', japanese: '寒いね。窓が開いてるよ。', chinese: '好冷啊。窗户开着呢。' },
            { id: 'w-aku-dlg-1-l2', speaker: '学生B', japanese: 'ほんとだ。風で開いちゃったのかな。', chinese: '真的。是被风吹开的吧。' },
            { id: 'w-aku-dlg-1-l3', speaker: '学生A', japanese: '閉めてもいい？', chinese: '我可以关上吗？' },
            { id: 'w-aku-dlg-1-l4', speaker: '学生B', japanese: 'うん、お願い。', chinese: '嗯，麻烦了。' }
          ]
        }
      ],
      synonyms: [
        { id: 'w-aku-syn-1', heading: '開く（ひらく）', reading: 'ひらく', chinese: '打开、翻开、开办', shared: '閉じたものが開くという点。', difference: '「あく」は窓・ドア・ふたなど、ふさがっていたものが開くときに使います。「ひらく」は本・傘・店・会など、広げたり始めたりする場合に使います。', usage: '「開く」のもう一つの読み', interchangeable: 'SOMETIMES', interchangeNote: 'ドアや窓では「あく」が普通ですが、「ひらく」も使える場合があります。本・傘・店・会には「あく」を使うと不自然です。', exampleJapanese: '本を開いて、最初のページを読みました。', exampleChinese: '我翻开书，读了第一页。' }
      ],
      cautions: [
        { id: 'w-aku-cau-1', kind: 'PARTICLE', title: '自動詞「あく」は「が」で示す', wrong: 'ドアを開きました（あきました）。', correct: 'ドアが開きました（あきました）。', reason: '「あく」は自動詞なので、開いたものは「が」で示します。「を」を使うと他動詞「あける」「ひらく」の形になります（ドアをあけました）。' },
        { id: 'w-aku-cau-2', kind: 'UNNATURAL', title: '「本があく」は不自然（読みが違う）', wrong: '本が開きました（あきました）。', correct: '本を開きました（ひらきました）。', reason: '「あく」は窓・ドア・ふたなど、閉まっていたものが開くときに使います。本・傘・店・会などは「ひらく」を使うのが自然です。同じ「開く」でも読み方が変わります。' }
      ],
      conjugations: [
        { id: 'w-aku-conj-1', form: 'ます形', value: '開きます（あきます）', example: '窓が開きます。' },
        { id: 'w-aku-conj-2', form: 'て形', value: '開いて（あいて）', example: 'ドアが開いています。' },
        { id: 'w-aku-conj-3', form: 'た形', value: '開いた（あいた）', example: '風で窓が開いた。' },
        { id: 'w-aku-conj-4', form: 'ない形', value: '開かない（あかない）', example: 'このふたは固くて開かない。' },
        { id: 'w-aku-conj-5', form: 'ば形', value: '開けば（あけば）', example: '窓が開けば、少し涼しくなります。' }
      ],
      transitivityPair: { intransitive: '開く（あく）', transitive: '開ける', particleNote: '自動詞は「窓が開く（あく）」のように「が」、他動詞は「窓を開ける」のように「を」で示します。同じ場面でも、どちらを使うかで助詞が入れ替わります。', intransitiveExample: '風が強くて、窓が開きました。', transitiveExample: '暑いので、窓を開けました。' },
      pronunciation: { reading: 'あく', accentType: 2, accentNotation: 'あꜜく', hint: '2拍の動詞で、下がり目は語の最後にあります（あꜜく）。「ひらく」と読みが違うので、意味だけでなく音でも区別しましょう。', hasAudioSample: false },
      collocations: [
        { id: 'w-aku-coll-1', expression: 'ドアが開く', reading: 'ドアが あく', chinese: '门开了', usage: '音や風で気づいたとき' },
        { id: 'w-aku-coll-2', expression: 'ふたが開く', reading: 'ふたが あく', chinese: '盖子开了', usage: '容器や箱の状態を言うとき' }
      ],
      relatedWords: [
        { id: 'w-aku-rel-1', relation: '間違えやすい', heading: '開く（ひらく）', reading: 'ひらく', chinese: '同一表记、不同读音（用于书、伞、店等）' },
        { id: 'w-aku-rel-2', relation: '対義語', heading: '閉まる', reading: 'しまる', chinese: '关、关闭（自动词：ドアが閉まる）' }
      ],
      usageNotes: [
        { id: 'w-aku-use-1', register: 'どちらも', politeness: '普通', audience: '友だち・家族', note: '自動詞なので「自然にそうなった」という感じが出ます。', senseNumber: 1 },
        { id: 'w-aku-use-2', register: 'どちらも', politeness: '普通', audience: '同僚・店の人', note: '同じ「開く」でも、読みによって使う対象が変わります。迷ったら「何が開くのか」を確かめましょう。', senseNumber: null }
      ],
      memoryHint: { hint: '「あく」は「が」とセット、「あける」は「を」とセット。', basis: '助詞の組み合わせ（が／を）を呪文のように唱えて覚える覚え方。' },
      practices: [
        { id: 'w-aku-pr-1', kind: 'PARTICLE', question: '風が強くて、窓（　）開きました。', questionChinese: '选择正确的助词填空。', choices: ['が', 'を', 'に', 'で'], answer: 'が', explanation: '「開く（あく）」は自動詞なので、開いたものは「が」で示します。「を」を使うなら「窓を開けました」のように他動詞にします。', freeWriting: false, demoFeedback: null },
        { id: 'w-aku-pr-2', kind: 'SYNONYM', question: '本を（　）ください。いちばん自然なのはどれですか。', questionChinese: '“请翻开书”，哪个说法最自然？', choices: ['開いて', 'あいて', '開けて'], answer: '開いて', explanation: '本は「ひらく」を使うので、ここでは「開いて（ひらいて）」が自然です。「あいて」は自動詞なので「本を」と組み合わせられません。「開けて」は窓やふたに使います。', freeWriting: false, demoFeedback: null },
        { id: 'w-aku-pr-3', kind: 'SCENE', question: '教室で、寒いので窓が開いているのを閉めたい。先生に何と言いますか。いちばん自然なのはどれですか。', questionChinese: '教室里很冷，你想把开着的窗户关上，该怎么跟老师说？', choices: [ '寒いので、窓を閉めてもいいですか。', '寒いので、窓が閉めてもいいですか。', '寒いので、窓に閉めてもいいですか。' ], answer: '寒いので、窓を閉めてもいいですか。', explanation: '自分が何かをする許可を求めるときは「〜てもいいですか」を使い、対象は「を」で示します。', freeWriting: false, demoFeedback: null }
      ]
    },
    updatedAt: '2026-09-14T14:02:00',
    demoNote: '同表記・別読み（あく）／自他動詞の相手'
  },
  {
    id: 'w-hiraku',
    heading: '開く',
    reading: 'ひらく',
    alternateReading: 'あく',
    partOfSpeech: '動詞',
    jlpt: 'N4',
    chineseMeaning: '打开、翻开；开办、举行（会议等）',
    detailStatus: 'GENERATED',
    failureReason: null,
    manuallyEdited: false,
    collections: [
      { id: 'w-hiraku-col-1', book: 'デモ日本語 初級', unit: 'Unit004', seq: 2, listedWord: '開く', listedReading: 'ひらく', listedChinese: '翻开；举行' }
    ],
    detail: {
      coreMeaning: '閉じているものを広げたり、会や店などを始めたりすること。',
      descriptionJa:
        '「開く（ひらく）」は、本や傘のように閉じているものを広げるとき、また会や店を始めるときに使います。「あく」と書く字は同じでも、使う対象と読み方が違います。',
      descriptionZh:
        '「開く（ひらく）」用于把书、伞等合着的东西展开，也用于开办会议、店铺等。和读「あく」的「開く」写法相同，但既可作他动词（如「本を開く」），也可作自动词（如「店が開く」），请注意搭配的对象不同。',
      senses: [
        { id: 'w-hiraku-sense-1', number: 1, japanese: '閉じているものを広げる（本・傘・手のひらなど）。', chinese: '打开、翻开（书、伞、手掌等）。', context: '読書・日常生活', style: '普通' },
        { id: 'w-hiraku-sense-2', number: 2, japanese: '会や店などを始める・催す。', chinese: '开办、举行（会议、店铺等）。', context: '会社・学校の行事', style: 'やや硬い' }
      ],
      examples: [
        { id: 'w-hiraku-ex-1', japanese: '教科書の二十ページを開いてください。', reading: 'きょうかしょの にじゅうページを ひらいてください。', chinese: '请翻开教科书第二十页。', senseNumber: 1, source: '授業中の指示', level: 'BASIC' },
        { id: 'w-hiraku-ex-2', japanese: '駅の前に小さなパン屋さんが開きました。', reading: 'えきの まえに ちいさな パンやさんが ひらきました。', chinese: '车站前面新开了一家小面包店。', senseNumber: 2, source: '街の話', level: 'BASIC' },
        { id: 'w-hiraku-ex-3', japanese: '来月、留学生向けの進学説明会を開くことになりました。', reading: 'らいげつ、りゅうがくせいむけの しんがくせつめいかいを ひらくことに なりました。', chinese: '下个月决定为留学生举办升学说明会。', senseNumber: 2, source: '学校のお知らせ（応用）', level: 'APPLIED' }
      ],
      patterns: [
        { id: 'w-hiraku-pat-1', pattern: '本**を**開く（ひらく）', reading: 'ほんを ひらく', chinese: '翻开书（对象用「を」）', example: '静かに本を開きました。', exampleChinese: '我轻轻地翻开了书。' },
        { id: 'w-hiraku-pat-2', pattern: '会**を**開く（ひらく）', reading: 'かいを ひらく', chinese: '举行会议（「催す」的意思）', example: '来週、説明会を開きます。', exampleChinese: '下周举行说明会。' }
      ],
      dialogs: [],
      synonyms: [
        { id: 'w-hiraku-syn-1', heading: '開く（あく）', reading: 'あく', chinese: '（门窗、盖子）开着', shared: '閉じたものが開くという点。', difference: '「あく」は窓・ドア・ふたなど、ふさがっていたものが開くときに使う自動詞です。「ひらく」は本・傘・店・会などに使い、自動詞にも他動詞にもなります。', usage: '「開く」のもう一つの読み', interchangeable: 'SOMETIMES', interchangeNote: '窓やドアは「あく」が普通ですが、「ひらく」も使える場合があります。本・傘・店・会には「あく」を使うと不自然です。', exampleJapanese: '窓が風で開きました（あきました）。', exampleChinese: '窗户被风吹开了。' }
      ],
      cautions: [
        { id: 'w-hiraku-cau-1', kind: 'PARTICLE', title: '「会が開きます」より「会を開きます」', wrong: '来週、説明会が開きます（ひらきます）。', correct: '来週、説明会を開きます（ひらきます）。', reason: '「会を開く」の「開く」は他動詞で、催す側を主語にして「を」を使います。会を主語にしたいときは受身にして「説明会が開かれます」とするのが普通です。' },
        { id: 'w-hiraku-cau-2', kind: 'GRAMMAR', title: '他動詞の形と読みの組み合わせ', wrong: '本をあいてください。', correct: '本をひらいてください。', reason: '「あく」は自動詞なので「本をあいてください」とは言えません。本のように「広げる」ものは他動詞「ひらく」を使い、読みも「ひらいて」になります。' }
      ],
      conjugations: [
        { id: 'w-hiraku-conj-1', form: 'ます形', value: '開きます（ひらきます）', example: '毎週、勉強会を開きます。' },
        { id: 'w-hiraku-conj-2', form: 'て形', value: '開いて（ひらいて）', example: '本を開いて、声に出して読みます。' },
        { id: 'w-hiraku-conj-3', form: 'た形', value: '開いた（ひらいた）', example: '駅前に新しい店が開いた。' },
        { id: 'w-hiraku-conj-4', form: 'ない形', value: '開かない（ひらかない）', example: 'この傘は壊れていて開かない。' },
        { id: 'w-hiraku-conj-5', form: '可能形', value: '開ける（ひらける）', example: 'この傘は片手では開けません（ひらけません）。' }
      ],
      transitivityPair: null,
      pronunciation: { reading: 'ひらく', accentType: null, accentNotation: null, hint: '「ひらく」は3拍、「あく」は2拍です。拍の数が違うので、聞き取りの手がかりになります。アクセントの型はこのデモでは未確認のため表示していません。', hasAudioSample: false },
      collocations: [
        { id: 'w-hiraku-coll-1', expression: '本を開く', reading: 'ほんを ひらく', chinese: '翻开书', usage: '読書や授業の場面' },
        { id: 'w-hiraku-coll-2', expression: '店を開く', reading: 'みせを ひらく', chinese: '开店、开张', usage: '商売の話' }
      ],
      relatedWords: [
        { id: 'w-hiraku-rel-1', relation: '間違えやすい', heading: '開く（あく）', reading: 'あく', chinese: '同一表记、不同读音（用于窗户、盖子等的自动词）' },
        { id: 'w-hiraku-rel-2', relation: '類義語', heading: '開ける', reading: 'あける', chinese: '打开（他動詞，门窗、盖子）' }
      ],
      usageNotes: [
        { id: 'w-hiraku-use-1', register: 'どちらも', politeness: '普通', audience: '同僚・目上の人', note: '「会を開く」は改まった場面でも使える表現です。日常の窓やドアには「あく」「あける」を使うのが普通です。', senseNumber: 2 }
      ],
      memoryHint: { hint: '「ひらく」は「広げる・始める」イメージ。', basis: '漢字の字面（「開」の中の「廾」が両手で広げる形に見えること）と、本を広げる場面の連想。' },
      practices: [
        { id: 'w-hiraku-pr-1', kind: 'PARTICLE', question: '来週、留学生向けの説明会（　）開きます。', questionChinese: '选择正确的助词填空。', choices: ['を', 'が', 'に', 'で'], answer: 'を', explanation: '「会を開く」の「開く」は他動詞なので「を」を使います。会を主語にするなら「説明会が開かれます」と受身にします。', freeWriting: false, demoFeedback: null },
        { id: 'w-hiraku-pr-2', kind: 'SYNONYM', question: '教科書の十ページを（　）ください。いちばん自然なのはどれですか。', questionChinese: '“请翻开教科书第十页”，哪个说法最自然？', choices: ['開いて', 'あいて', '開けて'], answer: '開いて', explanation: '本は「ひらく」を使います。「あいて」は自動詞なので「を」と組み合わせられません。「開けて」は窓やふたに使います。', freeWriting: false, demoFeedback: null }
      ]
    },
    updatedAt: '2026-09-14T14:25:00',
    demoNote: '同表記・別読み（ひらく）'
  },
  {
    id: 'w-akeru',
    heading: '開ける',
    reading: 'あける',
    alternateReading: null,
    partOfSpeech: '動詞',
    jlpt: 'N4',
    chineseMeaning: '打开（门窗、盖子、箱子等。他动词）',
    detailStatus: 'GENERATED',
    failureReason: null,
    manuallyEdited: false,
    collections: [
      { id: 'w-akeru-col-1', book: 'デモ日本語 初級', unit: 'Unit002', seq: 17, listedWord: '開ける', listedReading: 'あける', listedChinese: '打开' },
      { id: 'w-akeru-col-2', book: '学校生活のことば', unit: 'Unit002', seq: 3, listedWord: '開ける', listedReading: 'あける', listedChinese: '打开（窗、门）' }
    ],
    detail: {
      coreMeaning: '閉じているものを、開いた状態にする（他動詞）。',
      descriptionJa:
        '「開ける」は、閉じているものを開いた状態にする他動詞です。「窓を開ける」「ふたを開ける」のように「を」と一緒に使います。自動詞は「開く（あく）」です。',
      descriptionZh:
        '「開ける」是把原本关着的东西打开的他动词，用「を」表示对象，如「窓を開ける」（开窗）、「ふたを開ける」（打开盖子）。它对应的自动词是「開く（あく）」。中文的“打开”范围很广，日语要按对象分开：电器用「つける」，书和伞用「ひらく」，门窗和盖子用「あける」。',
      senses: [
        { id: 'w-akeru-sense-1', number: 1, japanese: '窓・ドア・ふた・箱などを開いた状態にする。', chinese: '打开（门窗、盖子、箱子等）。', context: '日常生活・教室', style: '普通' },
        { id: 'w-akeru-sense-2', number: 2, japanese: '封を切って、中身を使えるようにする。', chinese: '开封、打开（信封、瓶子等）。', context: '台所・事務', style: '普通' }
      ],
      examples: [
        { id: 'w-akeru-ex-1', japanese: '暑いので、窓を開けてもいいですか。', reading: 'あついので、まどを あけても いいですか。', chinese: '有点热，我可以开窗吗？', senseNumber: 1, source: '教室での会話', level: 'BASIC' },
        { id: 'w-akeru-ex-2', japanese: 'びんのふたが固くて、なかなか開けられませんでした。', reading: 'びんの ふたが かたくて、なかなか あけられませんでした。', chinese: '瓶盖太紧，我怎么也打不开。', senseNumber: 2, source: '日常生活', level: 'BASIC' },
        { id: 'w-akeru-ex-3', japanese: '換気のために一時間ごとに窓を開けて、空気を入れ替えるようにしています。', reading: 'かんきの ために いちじかんごとに まどを あけて、くうきを いれかえるように しています。', chinese: '为了通风，我尽量每小时开一次窗，让室内空气换一换。', senseNumber: 1, source: '生活の習慣（応用）', level: 'APPLIED' }
      ],
      patterns: [
        { id: 'w-akeru-pat-1', pattern: '窓**を**開ける', reading: 'まどを あける', chinese: '把窗户打开（对象用「を」）', example: '暑いから、窓を開けましょう。', exampleChinese: '因为热，我们把窗户打开吧。' },
        { id: 'w-akeru-pat-2', pattern: '〜**を**開けておく', reading: '〜を あけておく', chinese: '把……开着（保持打开的状态）', example: '窓を開けておいてください。', exampleChinese: '请把窗户开着。' }
      ],
      dialogs: [
        {
          id: 'w-akeru-dlg-1',
          scene: '事務室で、暑いので窓を開けたい',
          lines: [
            { id: 'w-akeru-dlg-1-l1', speaker: '学生', japanese: 'すみません、少し暑いので窓を開けてもいいですか。', chinese: '不好意思，有点热，可以开窗吗？' },
            { id: 'w-akeru-dlg-1-l2', speaker: '職員', japanese: 'ええ、どうぞ。ただ、風が強いので半分だけにしてください。', chinese: '可以，请。不过风很大，只开一半吧。' },
            { id: 'w-akeru-dlg-1-l3', speaker: '学生', japanese: 'わかりました。あ、となりの部屋のドアも開けておきますか。', chinese: '好的。啊，旁边房间的门也先打开吗？' },
            { id: 'w-akeru-dlg-1-l4', speaker: '職員', japanese: 'それは閉めておいてください。荷物を運び込みますから。', chinese: '那扇门请关着，因为要往里搬东西。' }
          ]
        }
      ],
      synonyms: [
        {
          id: 'w-akeru-syn-1',
          heading: '開く（ひらく）',
          reading: 'ひらく',
          chinese: '打开、翻开、开办',
          shared: '閉じているものを開いた状態にするという点。',
          difference:
            '「あける」は窓・ドア・ふた・箱など、ふさがっているものをどける感じです。「ひらく」は本・傘・店・会など、広げたり始めたりする場合に使います。',
          usage: '「開く」のもう一つの読み',
          interchangeable: 'SOMETIMES',
          interchangeNote:
            'ドアや窓は「あける」が普通ですが、「ひらく」も使える場合があります。本・傘・店・会に「あける」を使うと不自然です。',
          exampleJapanese: '教科書を開いて（ひらいて）、例文を読みました。',
          exampleChinese: '我翻开教科书，读了例句。'
        }
      ],
      cautions: [
        { id: 'w-akeru-cau-1', kind: 'PARTICLE', title: '「窓が開けました」は助詞が違う', wrong: '暑いので、窓が開けました。', correct: '暑いので、窓を開けました。', reason: '「開ける」は他動詞なので、開ける対象は「を」で示します。「が」を使うと自動詞「開く（あく）」の形（窓が開きました）になります。自動詞と他動詞は助詞とセットで覚えましょう。' },
        { id: 'w-akeru-cau-2', kind: 'UNNATURAL', title: '「テレビを開ける」は日本語では使わない', wrong: 'テレビを開けてください。', correct: 'テレビをつけてください。', reason: '中文的“打开”范围很宽，日语要按对象分开：电器は「つける」、门窗・ふたは「あける」、本・傘は「ひらく」など。文法的な形が同じでも、組み合わせが違うと通じにくくなります。' }
      ],
      conjugations: [
        { id: 'w-akeru-conj-1', form: 'ます形', value: '開けます', example: '毎朝、窓を開けます。' },
        { id: 'w-akeru-conj-2', form: 'て形', value: '開けて', example: '窓を開けて、空気を入れ替えます。' },
        { id: 'w-akeru-conj-3', form: 'た形', value: '開けた', example: '寒かったので、窓を開けたままにしませんでした。' },
        { id: 'w-akeru-conj-4', form: 'ない形', value: '開けない', example: '雨の日は窓を開けないでください。' },
        { id: 'w-akeru-conj-5', form: '可能形', value: '開けられる', example: 'このふたは力があれば開けられます。' }
      ],
      transitivityPair: { intransitive: '開く（あく）', transitive: '開ける', particleNote: '自動詞「あく」は「窓が開く」のように「が」、他動詞「あける」は「窓を開ける」のように「を」で示します。同じ場面をどちらで言うかで、助詞が入れ替わります。', intransitiveExample: '風が強くて、窓が開きました。', transitiveExample: '暑いので、窓を開けました。' },
      pronunciation: { reading: 'あける', accentType: null, accentNotation: null, hint: '「あける」は3拍、「あく」は2拍です。拍の数も一緒に覚えると、自動詞と他動詞を聞き分けやすくなります。アクセントの型はこのデモでは未確認のため表示していません。', hasAudioSample: false },
      collocations: [
        { id: 'w-akeru-coll-1', expression: 'ふたを開ける', reading: 'ふたを あける', chinese: '打开盖子', usage: '台所や食事の場面' },
        { id: 'w-akeru-coll-2', expression: '窓を開けておく', reading: 'まどを あけておく', chinese: '把窗户开着', usage: '換気や暑さ対策の話' }
      ],
      relatedWords: [
        { id: 'w-akeru-rel-1', relation: '間違えやすい', heading: '開く（あく）', reading: 'あく', chinese: '自动词（窗户自己开）' },
        { id: 'w-akeru-rel-2', relation: '対義語', heading: '閉める', reading: 'しめる', chinese: '关上（他动词）' }
      ],
      usageNotes: [
        { id: 'w-akeru-use-1', register: 'どちらも', politeness: '普通', audience: '友だち・家族', note: '「開けておく」は「開けた状態を続ける」という意味になります。', senseNumber: 1 },
        { id: 'w-akeru-use-2', register: 'どちらも', politeness: '丁寧', audience: '職場・店', note: '許可を求めるときは「〜てもいいですか」と組み合わせるのが一般的です。', senseNumber: null }
      ],
      memoryHint: { hint: '「あける」は自分が手を出して開ける感じ。', basis: '場面の連想（暑くて自分で窓を開ける動作）と、「あく／あける」の助詞の組み合わせ。' },
      practices: [
        { id: 'w-akeru-pr-1', kind: 'PARTICLE', question: '暑いので、窓（　）開けました。', questionChinese: '选择正确的助词填空。', choices: ['を', 'が', 'に', 'と'], answer: 'を', explanation: '「開ける」は他動詞なので、対象は「を」で示します。「が」を使うと自動詞「窓が開きました」の形になります。', freeWriting: false, demoFeedback: null },
        { id: 'w-akeru-pr-2', kind: 'SYNONYM', question: '（部屋が暑いので、エアコンを）いちばん自然なのはどれですか。', questionChinese: '房间里很热，想开空调，哪个说法最自然？', choices: ['エアコンをつけてください。', 'エアコンを開けてください。', 'エアコンをひらいてください。'], answer: 'エアコンをつけてください。', explanation: '电器は「つける」を使います。「開ける」「ひらく」は電器には使いません。中文的“打开”在日语里要按对象分别选择动词。', freeWriting: false, demoFeedback: null },
        { id: 'w-akeru-pr-3', kind: 'SCENE', question: '職員室で、暑いので窓を開けたい。許可を求めるとき、いちばん自然なのはどれですか。', questionChinese: '在办公室想开窗，请求许可时哪句最自然？', choices: [ '窓を開けてもいいですか。', '窓を開けなければなりませんか。', '窓が開けたいですか。' ], answer: '窓を開けてもいいですか。', explanation: '自分の行為の許可は「〜てもいいですか」です。「〜たいですか」は相手の希望を聞く形なので、自分には使えません。', freeWriting: false, demoFeedback: null }
      ]
    },
    updatedAt: '2026-09-14T15:30:00',
    demoNote: '自他動詞の相手（あく⇔あける）'
  },
  {
    id: 'w-narau',
    heading: '習う',
    reading: 'ならう',
    alternateReading: null,
    partOfSpeech: '動詞',
    jlpt: 'N4',
    chineseMeaning: '（跟老师）学、学习（有教的人）',
    detailStatus: 'GENERATED',
    failureReason: null,
    manuallyEdited: false,
    collections: [
      { id: 'w-narau-col-1', book: 'デモ日本語 初級', unit: 'Unit003', seq: 7, listedWord: '習う', listedReading: 'ならう', listedChinese: '学（跟人学）' }
    ],
    detail: {
      coreMeaning: '教えてくれる人から、やり方や知識を身につける。',
      descriptionJa:
        '「習う」は、先生や先輩など教えてくれる人から、やり方や知識を教わって身につけることです。教える人がいる場面に使うので、独学には使いません。',
      descriptionZh:
        '「習う」指从老师、前辈等会教的人那里学到方法或知识。因为它强调“有教的人”，所以自学时不能用「習う」，要用「勉強する」或「学ぶ」。',
      senses: [
        { id: 'w-narau-sense-1', number: 1, japanese: '教えてくれる人から、やり方や知識を教わる。', chinese: '（跟人）学：从教的人那里学到方法或知识。', context: '教室・部活・仕事', style: '普通' },
        { id: 'w-narau-sense-2', number: 2, japanese: '教わったことを繰り返し練習して、自分のものにする。', chinese: '学习（并反复练习掌握），如学钢琴、学书法。', context: '習い事・趣味', style: '普通' }
      ],
      examples: [
        { id: 'w-narau-ex-1', japanese: '日本語は大学に入ってから習いました。', reading: 'にほんごは だいがくに はいってから ならいました。', chinese: '日语我是上大学以后才开始学的。', senseNumber: 1, source: '自己紹介', level: 'BASIC' },
        { id: 'w-narau-ex-2', japanese: '妹は三歳からピアノを習っています。', reading: 'いもうとは さんさいから ピアノを ならっています。', chinese: '我妹妹从三岁开始学钢琴。', senseNumber: 2, source: '家族の話', level: 'BASIC' },
        { id: 'w-narau-ex-3', japanese: 'アルバイト先の先輩に、丁寧な電話の受け答えを一から習いました。', reading: 'アルバイトさきの せんぱいに、ていねいな でんわの うけこたえを いちから ならいました。', chinese: '我在打工的地方，从前辈那里从头学了礼貌的接电话应对。', senseNumber: 1, source: 'アルバイトの場面（応用）', level: 'APPLIED' }
      ],
      patterns: [
        { id: 'w-narau-pat-1', pattern: '人**に**〜を習う', reading: 'ひとに 〜を ならう', chinese: '跟某人学某样东西（教的人用「に」，学的内容用「を」）', example: '先生に発音を習いました。', exampleChinese: '我跟老师学了发音。' },
        { id: 'w-narau-pat-2', pattern: '〜**を**習い始める', reading: '〜を ならいはじめる', chinese: '开始学……（表示开始的时间点）', example: '今年から書道を習い始めました。', exampleChinese: '我从今年开始学书法。' }
      ],
      dialogs: [
        {
          id: 'w-narau-dlg-1',
          scene: '昼休みに、習い事の話をする',
          lines: [
            { id: 'w-narau-dlg-1-l1', speaker: '学生A', japanese: '週末はいつも何をしてるの？', chinese: '你周末一般做什么？' },
            { id: 'w-narau-dlg-1-l2', speaker: '学生B', japanese: '日曜日に、駅の近くの教室で書道を習ってるんだ。', chinese: '我星期天在车站附近的教室里学书法。' },
            { id: 'w-narau-dlg-1-l3', speaker: '学生A', japanese: 'へえ、独学？', chinese: '哦？是自学吗？' },
            { id: 'w-narau-dlg-1-l4', speaker: '学生B', japanese: 'ううん、先生に一から習ってるよ。字が変わってきたって言われる。', chinese: '不是，我是跟老师从头学的。别人说我的字变好了。' }
          ]
        }
      ],
      synonyms: [
        { id: 'w-narau-syn-1', heading: '学ぶ', reading: 'まなぶ', chinese: '学习、学到', shared: '知識や技術を身につけるという点。', difference: '「学ぶ」は教える人がいなくても使え、やや硬い言い方です。「習う」は、教えてくれる人がいる場合に使います。', usage: '文章・発表', interchangeable: 'SOMETIMES', interchangeNote: '先生がいる場面ではどちらも使えますが、会話では「習う」のほうが自然です。独学や経験から得たことは「学ぶ」を使います。', exampleJapanese: '失敗から学ぶことも多い。', exampleChinese: '从失败中也能学到很多东西。' },
        { id: 'w-narau-syn-2', heading: '勉強する', reading: 'べんきょうする', chinese: '学习、用功', shared: '身につけるために努力するという点。', difference: '「勉強する」は先生がいなくても使え、試験や暗記のイメージが強いです。「習う」は教わる相手が前提になります。', usage: '試験・独学', interchangeable: 'SOMETIMES', interchangeNote: '「日本語を習う」は先生がいる場合、「日本語を勉強する」は独学でも使えます。ピアノのように技術を教わる場合は「習う」が普通です。', exampleJapanese: '毎日一時間、日本語を勉強しています。', exampleChinese: '我每天学习一个小时日语。' }
      ],
      cautions: [
        { id: 'w-narau-cau-1', kind: 'PARTICLE', title: '「先生を習う」は助詞が違う', wrong: '先生を習いました。', correct: '先生に習いました。／先生に発音を習いました。', reason: '教えてくれる人は「に」で示します。「を」は学ぶ内容につけます（発音を習う）。中国語の“跟老师学”の「跟」をそのまま「を」にしないよう注意してください。' },
        { id: 'w-narau-cau-2', kind: 'UNNATURAL', title: '独学には「習う」を使わない', wrong: '本を読んで、一人で日本語を習いました。', correct: '本を読んで、一人で日本語を勉強しました。／独学で日本語を学びました。', reason: '「習う」は教えてくれる人がいる場合に使います。自分ひとりで身につけた場合は「勉強する」「学ぶ」が自然です。' }
      ],
      conjugations: [
        { id: 'w-narau-conj-1', form: 'ます形', value: '習います', example: '週に一度、書道を習います。' },
        { id: 'w-narau-conj-2', form: 'て形', value: '習って', example: '先生に習ったとおりに書いてみます。' },
        { id: 'w-narau-conj-3', form: 'た形', value: '習った', example: 'きのう習った文法を復習しました。' },
        { id: 'w-narau-conj-4', form: 'ない形', value: '習わない', example: '今年は新しいことは習わないつもりです。' },
        { id: 'w-narau-conj-5', form: '可能形', value: '習える', example: 'この教室では中国語も習えます。' }
      ],
      transitivityPair: null,
      pronunciation: { reading: 'ならう', accentType: null, accentNotation: null, hint: '「ならう」は3拍です。「ならいます」のように活用しても拍の区切りは変わりません。アクセントの型はこのデモでは未確認のため表示していません。', hasAudioSample: false },
      collocations: [
        { id: 'w-narau-coll-1', expression: 'ピアノを習う', reading: 'ピアノを ならう', chinese: '学钢琴', usage: '習い事の話' },
        { id: 'w-narau-coll-2', expression: '習ったことを復習する', reading: 'ならった ことを ふくしゅうする', chinese: '复习学过的东西', usage: '学習の習慣を話すとき' }
      ],
      relatedWords: [
        { id: 'w-narau-rel-1', relation: '類義語', heading: '学ぶ', reading: 'まなぶ', chinese: '学习、学到（较书面，可用于自学）' },
        { id: 'w-narau-rel-2', relation: '間違えやすい', heading: '教わる', reading: 'おそわる', chinese: '受教、学到（强调从对方那里得到教导）' }
      ],
      usageNotes: [
        { id: 'w-narau-use-1', register: 'どちらも', politeness: '普通', audience: '友だち・先生', note: '「習っています」の形で、続けている習い事を表すことが多いです。', senseNumber: 2 },
        { id: 'w-narau-use-2', register: 'どちらも', politeness: '丁寧', audience: '目上の人', note: '教わった相手に感謝を伝えるときは「教えていただきました」のほうが自然な場合もあります。', senseNumber: 1 }
      ],
      memoryHint: { hint: '「習う」は「先生がいる」が合図。', basis: '場面の連想（教室で先生の手元を見ながら真似する場面）と、教える人がいるかどうか。' },
      practices: [
        { id: 'w-narau-pr-1', kind: 'PARTICLE', question: '大学で先生（　）発音を習いました。', questionChinese: '选择正确的助词填空。', choices: ['に', 'を', 'が', 'で'], answer: 'に', explanation: '教えてくれる人は「に」、学ぶ内容は「を」で示します。「に」と「を」を両方使う文だと意識すると分かりやすいです。', freeWriting: false, demoFeedback: null },
        { id: 'w-narau-pr-2', kind: 'SYNONYM', question: '本を読んで一人で勉強しました。このことを「習う」を使って言うと、どうなりますか。', questionChinese: '“我一个人看书学习”，如果用「習う」来表达，会怎样？', choices: [ '「習う」は使えない（教えてくれる人がいないため）。', '「一人で習いました」と言える。', '「習われました」と言える。' ], answer: '「習う」は使えない（教えてくれる人がいないため）。', explanation: '「習う」は教えてくれる人がいる場合に使います。独学なら「勉強する」「学ぶ」を使います。', freeWriting: false, demoFeedback: null },
        { id: 'w-narau-pr-3', kind: 'SCENE', question: '習い事について友だちに説明するとき、いちばん自然なのはどれですか。', questionChinese: '向朋友介绍自己在学的才艺，哪句最自然？', choices: [ '日曜日に書道を習っています。', '日曜日に書道が習います。', '日曜日に書道を習わされています。' ], answer: '日曜日に書道を習っています。', explanation: '続けていることは「〜を習っています」と言います。「習わされています」は使役受身で「無理に習わされている」という意味になり、ここでは合いません。', freeWriting: false, demoFeedback: null }
      ]
    },
    updatedAt: '2026-09-14T16:10:00',
    demoNote: null
  },
  {
    id: 'w-manabu',
    heading: '学ぶ',
    reading: 'まなぶ',
    alternateReading: null,
    partOfSpeech: '動詞',
    jlpt: 'N3',
    chineseMeaning: '学习、学到（较书面，自学也可以用）',
    detailStatus: 'GENERATED',
    failureReason: null,
    manuallyEdited: false,
    collections: [
      { id: 'w-manabu-col-1', book: 'デモ日本語 初級', unit: 'Unit004', seq: 5, listedWord: '学ぶ', listedReading: 'まなぶ', listedChinese: '学习（学到）' }
    ],
    detail: {
      coreMeaning: '勉強や経験を通して、知識や考え方を身につける。',
      descriptionJa:
        '「学ぶ」は、勉強や経験を通して知識や考え方を身につけることです。やや硬い言い方で、文章や改まった場面でよく使います。教えてくれる人がいなくても使えます。',
      descriptionZh:
        '「学ぶ」指通过学习或经验掌握知识、想法。语气偏书面，常用于文章或正式场合。它不需要有老师在场，自学、从经验中吸取教训都可以用「学ぶ」。',
      senses: [
        { id: 'w-manabu-sense-1', number: 1, japanese: '学校や本、経験を通して知識や技術を身につける。', chinese: '学习、学到：通过学校、书本或经验掌握知识技能。', context: '文章・発表', style: 'やや硬い' },
        { id: 'w-manabu-sense-2', number: 2, japanese: '人の態度や失敗から、ためになることを得る。', chinese: '从别人的态度或失败中汲取有益的东西。', context: '感想・教訓', style: 'やや硬い' }
      ],
      examples: [
        { id: 'w-manabu-ex-1', japanese: '大学では経済について学びました。', reading: 'だいがくでは けいざいについて まなびました。', chinese: '我在大学学了经济。', senseNumber: 1, source: '自己紹介', level: 'BASIC' },
        { id: 'w-manabu-ex-2', japanese: '留学生と話すことで、いろいろな考え方を学びました。', reading: 'りゅうがくせいと はなすことで、いろいろな かんがえかたを まなびました。', chinese: '通过和留学生聊天，我学到了各种不同的想法。', senseNumber: 1, source: '学生生活', level: 'BASIC' },
        { id: 'w-manabu-ex-3', japanese: '失敗から学んだことを、次の発表に生かしたいと思います。', reading: 'しっぱいから まなんだ ことを、つぎの はっぴょうに いかしたいと おもいます。', chinese: '我想把从失败中学到的东西，用到下一次发表中。', senseNumber: 2, source: '発表の振り返り（応用）', level: 'APPLIED' }
      ],
      patterns: [
        { id: 'w-manabu-pat-1', pattern: '〜**を**学ぶ', reading: '〜を まなぶ', chinese: '学习某领域或某内容（对象用「を」）', example: '大学で日本文学を学びました。', exampleChinese: '我在大学学了日本文学。' },
        { id: 'w-manabu-pat-2', pattern: '〜**から**学ぶ', reading: '〜から まなぶ', chinese: '从……中学到（来源用「から」）', example: '失敗から学ぶことは多いです。', exampleChinese: '从失败中可以学到很多东西。' }
      ],
      dialogs: [],
      synonyms: [
        { id: 'w-manabu-syn-1', heading: '習う', reading: 'ならう', chinese: '（跟人）学', shared: '知識や技術を身につけるという点。', difference: '「習う」は教えてくれる人がいる場合に使います。「学ぶ」は教える人がいなくても使え、より広い意味で使えます。', usage: '教室・独学', interchangeable: 'SOMETIMES', interchangeNote: '先生がいる場面ではどちらも使えますが、独学には「習う」を使えません。会話では「習う」、文章では「学ぶ」が選ばれやすいです。', exampleJapanese: '先生に発音を習いました。', exampleChinese: '我跟老师学了发音。' },
        { id: 'w-manabu-syn-2', heading: '勉強する', reading: 'べんきょうする', chinese: '学习、用功', shared: '知識を身につけるという点。', difference: '「勉強する」は試験や暗記のイメージが強く、会話でよく使います。「学ぶ」はもっと広く、経験から得ることにも使えます。', usage: '試験・独学・文章', interchangeable: 'SOMETIMES', interchangeNote: '試験のための努力には「勉強する」が自然で、「歴史から学ぶ」のような場合は「勉強する」に置き換えにくいです。', exampleJapanese: '試験のために毎日勉強しています。', exampleChinese: '为了考试，我每天都在学习。' }
      ],
      cautions: [
        { id: 'w-manabu-cau-1', kind: 'UNNATURAL', title: '友だちとの軽い会話では硬すぎる', wrong: 'ラーメンの作り方を学ぼうぜ。', correct: 'ラーメンの作り方を覚えようよ。／習おうよ。', reason: '「学ぶ」は文章や改まった場面で使いやすい言葉です。友だちとの軽い会話では「覚える」「習う」のほうが自然に聞こえます。' }
      ],
      conjugations: [
        { id: 'w-manabu-conj-1', form: 'ます形', value: '学びます', example: '大学で経済を学びます。' },
        { id: 'w-manabu-conj-2', form: 'て形', value: '学んで', example: '留学生と話して、いろいろな考え方を学んでいます。' },
        { id: 'w-manabu-conj-3', form: 'た形', value: '学んだ', example: '失敗から多くを学んだ。' },
        { id: 'w-manabu-conj-4', form: 'ない形', value: '学ばない', example: '人の話を聞かないと、何も学ばない。' },
        { id: 'w-manabu-conj-5', form: '可能形', value: '学べる', example: 'この授業では実際の事例から学べます。' }
      ],
      transitivityPair: null,
      pronunciation: { reading: 'まなぶ', accentType: null, accentNotation: null, hint: '「まなぶ」は3拍です。「まなびます」と活用しても拍の区切りは変わりません。アクセントの型はこのデモでは未確認のため表示していません。', hasAudioSample: false },
      collocations: [
        { id: 'w-manabu-coll-1', expression: '失敗から学ぶ', reading: 'しっぱいから まなぶ', chinese: '从失败中学习', usage: '振り返りや反省の場面' },
        { id: 'w-manabu-coll-2', expression: '学び直す', reading: 'まなびなおす', chinese: '重新学习、再学一遍', usage: '社会人になってから勉強するとき' }
      ],
      relatedWords: [
        { id: 'w-manabu-rel-1', relation: '類義語', heading: '習う', reading: 'ならう', chinese: '（跟人）学（需要教的人）' },
        { id: 'w-manabu-rel-2', relation: '間違えやすい', heading: '覚える', reading: 'おぼえる', chinese: '记住、背下来（重点在记忆，不在理解）' }
      ],
      usageNotes: [
        { id: 'w-manabu-use-1', register: '書き言葉', politeness: '普通', audience: 'レポート・発表', note: '文章では「学ぶ」、会話では「勉強する」「習う」が選ばれやすいです。', senseNumber: null }
      ],
      memoryHint: { hint: '「学ぶ」は「経験からも学べる」と広く構える。', basis: '場面の連想（失敗したあとに振り返って次に生かす場面）と、教える人がいなくても使えるという使い分け。' },
      practices: [
        { id: 'w-manabu-pr-1', kind: 'SYNONYM', question: 'この授業では、実際の事例（　）多くを学べます。', questionChinese: '选择最自然的形式填空。', choices: ['から', 'を', 'に', 'で'], answer: 'から', explanation: '学びの来源は「から」で示します。「を」は学ぶ内容そのものにつけます（事例を学ぶ）。', freeWriting: false, demoFeedback: null },
        { id: 'w-manabu-pr-2', kind: 'SCENE', question: '友だちと軽い会話をしています。「ピアノを習っている」と同じ内容を「学ぶ」で言うと、どんな感じになりますか。', questionChinese: '在轻松的聊天里，把“在学钢琴”换成「学ぶ」会是什么感觉？', choices: [ '意味は通じるが、少し硬く聞こえる。', '意味がまったく違う。', '「学ぶ」は使えない。' ], answer: '意味は通じるが、少し硬く聞こえる。', explanation: '「学ぶ」は誤りではありませんが、軽い会話では硬く聞こえます。「習う」「やっている」のほうが自然です。', freeWriting: false, demoFeedback: null }
      ]
    },
    updatedAt: '2026-09-14T16:35:00',
    demoNote: null
  },
  {
    id: 'w-soudan-suru',
    heading: '相談する',
    reading: 'そうだんする',
    alternateReading: null,
    partOfSpeech: '動詞',
    jlpt: 'N4',
    chineseMeaning: '商量；咨询（向对方征求意见）',
    detailStatus: 'GENERATED',
    failureReason: null,
    manuallyEdited: false,
    collections: [
      { id: 'w-soudan-suru-col-1', book: 'デモ日本語 初級', unit: 'Unit003', seq: 9, listedWord: '相談する', listedReading: 'そうだんする', listedChinese: '商量' },
      { id: 'w-soudan-suru-col-2', book: '日常会話ステップアップ', unit: 'Unit002', seq: 1, listedWord: '相談する', listedReading: 'そうだんする', listedChinese: '商量一下；咨询' }
    ],
    detail: {
      coreMeaning: '困っていることや決められないことを、人に話して意見を求める。',
      descriptionJa:
        '「相談する」は、自分では決められないことや困っていることを、人に話して意見を聞くという意味の動詞です。名詞は「相談」で、同じように使います。',
      descriptionZh:
        '「相談する」是动词，指把难以决定或正在烦恼的事情说给别人听、征求对方的意见。名词形式是「相談」，用法基本相同。商量的对象用「に」表示，这一点和中文的“跟……商量”不同。',
      senses: [
        { id: 'w-soudan-suru-sense-1', number: 1, japanese: '決められないことについて、人の意見を聞く。', chinese: '商量：就难以决定的事听取别人的意见。', context: '進路・生活・買い物', style: '普通' },
        { id: 'w-soudan-suru-sense-2', number: 2, japanese: '困りごとを、担当者や専門の窓口に持ち込む。', chinese: '咨询：把困难反映给负责人或专业窗口。', context: '学校・役所・会社', style: 'やや硬い' }
      ],
      examples: [
        { id: 'w-soudan-suru-ex-1', japanese: '留学するかどうか、家族に相談しました。', reading: 'りゅうがくするか どうか、かぞくに そうだんしました。', chinese: '要不要去留学，我跟家人商量了。', senseNumber: 1, source: '進路の話', level: 'BASIC' },
        { id: 'w-soudan-suru-ex-2', japanese: '困ったときは、一人で抱えずに先生に相談してください。', reading: 'こまった ときは、ひとりで かかえずに せんせいに そうだんしてください。', chinese: '有困难的时候，不要一个人扛着，请找老师商量。', senseNumber: 1, source: '学校のお知らせ', level: 'BASIC' },
        { id: 'w-soudan-suru-ex-3', japanese: '指導教員に相談したうえで、研究のテーマを少し狭めることにしました。', reading: 'しどうきょういんに そうだんした うえで、けんきゅうの テーマを すこし せばめることに しました。', chinese: '和导师商量之后，我决定把研究题目稍微缩小一些。', senseNumber: 1, source: '大学院の研究（応用）', level: 'APPLIED' }
      ],
      patterns: [
        { id: 'w-soudan-suru-pat-1', pattern: '人**に**相談する', reading: 'ひとに そうだんする', chinese: '向某人商量（对象用「に」）', example: 'まずご家族に相談してみてください。', exampleChinese: '请先和家人商量一下。' },
        { id: 'w-soudan-suru-pat-2', pattern: '相談**してから**決める', reading: 'そうだんしてから きめる', chinese: '商量之后再决定（顺序用「てから」）', example: 'よく相談してから決めましょう。', exampleChinese: '我们好好商量之后再决定吧。' }
      ],
      dialogs: [
        {
          id: 'w-soudan-suru-dlg-1',
          scene: '研究室で、今後の進路について先生に相談する',
          lines: [
            { id: 'w-soudan-suru-dlg-1-l1', speaker: '学生', japanese: '先生、今後の進路について相談したいことがあるんですが、お時間ありますか。', chinese: '老师，我有些关于今后出路的事想跟您商量，您现在有时间吗？' },
            { id: 'w-soudan-suru-dlg-1-l2', speaker: '先生', japanese: 'いいですよ。大学院に進むか、就職するかで迷っているんですね。', chinese: '可以啊。你是在纠结继续读研还是就业吧。' },
            { id: 'w-soudan-suru-dlg-1-l3', speaker: '学生', japanese: 'はい。研究は楽しいのですが、お金のこともあって決められなくて。', chinese: '是的。研究虽然很有意思，但还有费用的问题，我下不了决心。' },
            { id: 'w-soudan-suru-dlg-1-l4', speaker: '先生', japanese: 'では、来週、締め切りまでの予定を一緒に書き出してみましょう。', chinese: '那下周我们一起把到截止日期前的日程写出来看看吧。' }
          ]
        }
      ],
      synonyms: [
        {
          id: 'w-soudan-suru-syn-1',
          heading: '話し合う',
          reading: 'はなしあう',
          chinese: '商谈、一起讨论',
          shared: '複数の人で話すという点。',
          difference:
            '「相談する」は意見を聞きたい相手がいて、こちらが判断を委ねる感じがあります。「話し合う」は立場が対等で、結論を一緒に作っていく感じです。',
          usage: '予定や方針を決めるとき',
          interchangeable: 'SOMETIMES',
          interchangeNote:
            '目上の人に意見を求めるときは「相談する」が自然です。対等な相手と結論を出す場面では「話し合う」が合います。',
          exampleJapanese: 'クラス全員で話し合って、文化祭の出し物を決めました。',
          exampleChinese: '全班一起讨论，决定了文化节的节目。'
        }
      ],
      cautions: [
        { id: 'w-soudan-suru-cau-1', kind: 'PARTICLE', title: '「友達を相談する」は助詞が違う', wrong: '友達を相談しました。', correct: '友達に相談しました。', reason: '相談する相手は「に」で示します。「を」は使いません。中国語の“跟朋友商量”の「跟」をそのまま「を」にしないよう注意してください。' },
        { id: 'w-soudan-suru-cau-2', kind: 'UNNATURAL', title: 'いきなり「相談します」はぶっきらぼう', wrong: '先生、相談します。', correct: '先生、相談したいことがあるんですが、今よろしいですか。', reason: '「相談する」は相手の時間をもらう行為なので、前置きを置くほうが自然です。中国語の“我要咨询”をそのまま訳した言い方は、日本語ではぶっきらぼうに聞こえることがあります。' }
      ],
      conjugations: [
        { id: 'w-soudan-suru-conj-1', form: 'ます形', value: '相談します', example: 'まず家族に相談します。' },
        { id: 'w-soudan-suru-conj-2', form: 'て形', value: '相談して', example: '先生に相談してから決めます。' },
        { id: 'w-soudan-suru-conj-3', form: 'た形', value: '相談した', example: '進路について家族に相談した。' },
        { id: 'w-soudan-suru-conj-4', form: 'ない形', value: '相談しない', example: '一人で決めないで、相談しないと後で困りますよ。' },
        { id: 'w-soudan-suru-conj-5', form: '可能形', value: '相談できる', example: 'この窓口では英語でも相談できます。' }
      ],
      transitivityPair: null,
      pronunciation: { reading: 'そうだんする', accentType: null, accentNotation: null, hint: '「そうだんする」は6拍です。「そうだん」＋「する」と区切って読むと安定します。アクセントの型はこのデモでは未確認のため表示していません。', hasAudioSample: false },
      collocations: [
        { id: 'w-soudan-suru-coll-1', expression: '先生に相談する', reading: 'せんせいに そうだんする', chinese: '找老师商量', usage: '学校生活' },
        { id: 'w-soudan-suru-coll-2', expression: '相談してから決める', reading: 'そうだんしてから きめる', chinese: '商量之后再决定', usage: '予定や進路を決めるとき' }
      ],
      relatedWords: [
        { id: 'w-soudan-suru-rel-1', relation: '類義語', heading: '話し合う', reading: 'はなしあう', chinese: '商谈、一起讨论（立场对等）' },
        { id: 'w-soudan-suru-rel-2', relation: '間違えやすい', heading: '報告する', reading: 'ほうこくする', chinese: '汇报（只说事实，不征求对方意见）' }
      ],
      usageNotes: [
        { id: 'w-soudan-suru-use-1', register: 'どちらも', politeness: '普通', audience: '友だち・家族', note: '「ちょっと相談があるんだけど」は、話を切り出すときの決まり文句です。', senseNumber: 1 },
        { id: 'w-soudan-suru-use-2', register: '書き言葉', politeness: '丁寧', audience: '学校・会社の案内', note: '窓口の案内では「〜について相談できます」のように可能の形で書くことが多いです。', senseNumber: 2 }
      ],
      memoryHint: { hint: '「相談する」は「相手＋に」。助詞までセットで覚える。', basis: '助詞の組み合わせ（人＋に＋相談する）と、学生課の窓口で相談している場面の連想。' },
      practices: [
        { id: 'w-soudan-suru-pr-1', kind: 'PARTICLE', question: '留学するかどうか、家族（　）相談しました。', questionChinese: '选择正确的助词填空。', choices: ['に', 'を', 'が', 'で'], answer: 'に', explanation: '相談する相手は「に」で示します。「と」も使えますが、この文は家族の意見を聞いたので「に」が合います。', freeWriting: false, demoFeedback: null },
        { id: 'w-soudan-suru-pr-2', kind: 'SCENE', question: '先生の研究室を訪ねて、進路の相談をしたい。最初の一言として、いちばん自然なのはどれですか。', questionChinese: '你去老师的研究室，想谈今后的出路，第一句话哪句最自然？', choices: [ 'お時間ありますか。進路について相談したいんですが。', '相談します。今いいですか。', '相談してもいいですか。今から。' ], answer: 'お時間ありますか。進路について相談したいんですが。', explanation: '相手の時間を確かめてから、内容を「〜たいんですが」でやわらかく伝えるのが自然です。', freeWriting: false, demoFeedback: null },
        {
          id: 'w-soudan-suru-pr-3',
          kind: 'WRITING',
          question: '最近だれかに相談したこと、または相談したいことを一文書いてみましょう。',
          questionChinese: '用一句话写写你最近跟谁商量过什么，或者想跟谁商量什么。',
          choices: [],
          answer: 'アルバイトを増やすかどうか、母に相談してみるつもりです。',
          explanation: '「人＋に＋〜について相談する」の形がそろうと分かりやすいです。',
          freeWriting: true,
          demoFeedback: { grammar: '「人＋に＋相談する」の助詞が正しく使えています。', naturalness: '「〜かどうか」を入れると、迷っている内容がはっきり伝わります。', reference: 'アルバイトを増やすかどうか、週末に母に相談してみるつもりです。' }
        }
      ]
    },
    updatedAt: '2026-09-14T17:05:00',
    demoNote: null
  },
  {
    id: 'w-maniau',
    heading: '間に合う',
    reading: 'まにあう',
    alternateReading: null,
    partOfSpeech: '動詞',
    jlpt: null,
    chineseMeaning: '赶得上；来得及',
    detailStatus: 'FAILED',
    failureReason: '生成サービスが応答しませんでした。時間をおいて再試行してください。',
    manuallyEdited: false,
    collections: [
      { id: 'w-maniau-col-1', book: 'デモ日本語 初級', unit: 'Unit003', seq: 14, listedWord: '間に合う', listedReading: 'まにあう', listedChinese: '赶得上；来得及' },
      { id: 'w-maniau-col-2', book: '日常会話ステップアップ', unit: 'Unit001', seq: 6, listedWord: '間にあう', listedReading: 'まにあう', listedChinese: '来得及' }
    ],
    detail: null,
    updatedAt: '2026-09-14T17:20:00',
    demoNote: '生成失敗（再試行のデモ用）'
  },
  // ── い形容詞 ───────────────────────────────────────────────────────────
  {
    id: 'w-isogashii',
    heading: '忙しい',
    reading: 'いそがしい',
    alternateReading: null,
    partOfSpeech: 'い形容詞',
    jlpt: 'N4',
    chineseMeaning: '忙、忙碌',
    detailStatus: 'GENERATED',
    failureReason: null,
    manuallyEdited: false,
    collections: [
      { id: 'w-isogashii-col-1', book: 'デモ日本語 初級', unit: 'Unit001', seq: 10, listedWord: '忙しい', listedReading: 'いそがしい', listedChinese: '忙' }
    ],
    detail: {
      coreMeaning: 'やることが多くて、時間や心の余裕がない。',
      descriptionJa:
        '「忙しい」は、やることが多くて時間がない状態を表すい形容詞です。人について使うことが多いですが、「忙しい時期」のように時期や様子にも使えます。',
      descriptionZh:
        '「忙しい」是い形容词，表示要做的事很多、没有时间。多用于人，也可以说「忙しい時期」（忙碌的时期）。注意它是い形容词，修饰名词时直接接名词，不能加「な」。',
      senses: [
        { id: 'w-isogashii-sense-1', number: 1, japanese: 'やることが多くて、時間の余裕がない。', chinese: '忙：要做的事很多，时间不够用。', context: '仕事・勉強・家事', style: '普通' },
        { id: 'w-isogashii-sense-2', number: 2, japanese: '動きが慌ただしく、落ち着かない（時期や様子について）。', chinese: '（时期、情形）忙碌、匆忙。', context: '時期・街や店の様子', style: '普通' }
      ],
      examples: [
        { id: 'w-isogashii-ex-1', japanese: '今週はレポートが三つあって、とても忙しいです。', reading: 'こんしゅうは レポートが みっつ あって、とても いそがしいです。', chinese: '这周有三篇报告，非常忙。', senseNumber: 1, source: '学生生活', level: 'BASIC' },
        { id: 'w-isogashii-ex-2', japanese: '年末は、どの店も忙しい時期です。', reading: 'ねんまつは、どの みせも いそがしい じきです。', chinese: '年底是各家店都很忙的时期。', senseNumber: 2, source: '季節の話', level: 'BASIC' },
        { id: 'w-isogashii-ex-3', japanese: '忙しいときこそ、食事と睡眠の時間を削らないようにしています。', reading: 'いそがしい ときこそ、しょくじと すいみんの じかんを けずらないように しています。', chinese: '正因为忙，我才尽量不削减吃饭和睡觉的时间。', senseNumber: 1, source: '生活の工夫（応用）', level: 'APPLIED' }
      ],
      patterns: [
        { id: 'w-isogashii-pat-1', pattern: '人**は**〜で忙しい', reading: 'ひとは 〜で いそがしい', chinese: '某人因为……而忙（原因用「で」）', example: '今週は仕事で忙しいです。', exampleChinese: '这周因为工作很忙。' },
        { id: 'w-isogashii-pat-2', pattern: '忙しくて〜できない', reading: 'いそがしくて 〜できない', chinese: '太忙了，所以做不了……（い形容词的て形表示原因）', example: '忙しくて、本を読む時間がありません。', exampleChinese: '太忙了，没有看书的时间。' }
      ],
      dialogs: [
        {
          id: 'w-isogashii-dlg-1',
          scene: '友だちを誘われて、やわらかく断る',
          lines: [
            { id: 'w-isogashii-dlg-1-l1', speaker: '友だち', japanese: '今週末、映画を見に行かない？', chinese: '这周末去看电影吗？' },
            { id: 'w-isogashii-dlg-1-l2', speaker: '学生', japanese: '行きたいんだけど、今週はちょっと忙しくて。', chinese: '我很想去，但这周有点忙。' },
            { id: 'w-isogashii-dlg-1-l3', speaker: '友だち', japanese: 'そっか。レポート？', chinese: '这样啊。是报告吗？' },
            { id: 'w-isogashii-dlg-1-l4', speaker: '学生', japanese: 'うん、締め切りが二つ重なってるんだ。来週なら大丈夫。', chinese: '嗯，两个截止日期撞在一起了。下周的话没问题。' }
          ]
        }
      ],
      synonyms: [
        { id: 'w-isogashii-syn-1', heading: '多忙', reading: 'たぼう', chinese: '繁忙（书面语）', shared: 'やることが多くて時間がないという点。', difference: '「多忙」はな形容詞で、書き言葉です。目上の人の状態を改まって述べるときによく使います。', usage: '案内文・スピーチ・目上の人について', interchangeable: 'NO', interchangeNote: '自分について「私は多忙です」と言うと不自然に聞こえるので、自分には「忙しい」を使います。目上の人について述べる場面では「多忙」が合います。', exampleJapanese: '田中先生は、ご多忙のようです。', exampleChinese: '田中老师好像很忙。' }
      ],
      cautions: [
        { id: 'w-isogashii-cau-1', kind: 'GRAMMAR', title: 'い形容詞に「な」をつけない', wrong: '忙しいな毎日を送っています。', correct: '忙しい毎日を送っています。', reason: '「忙しい」はい形容詞なので、名詞を修飾するときはそのままつなげます。「な」をつけるのはな形容詞（大切な、大変な）です。' },
        { id: 'w-isogashii-cau-2', kind: 'UNNATURAL', title: '断るときに「行きません」は強い', wrong: '今週は忙しいので、行きません。', correct: '今週はちょっと忙しいので、また今度にしてもいい？', reason: '日本語では、断るときに「忙しい」を理由にしてやわらかく伝えることが多いです。「〜ので、行きません」は強い断り方に聞こえることがあります。' }
      ],
      conjugations: [],
      transitivityPair: null,
      pronunciation: { reading: 'いそがしい', accentType: 4, accentNotation: 'いそがしꜜい', hint: '「いそがしꜜい」は「し」のあとで下がります。中国語の「忙」のように一音節ではなく、「い・そ・が・し・い」の5拍で発音します。', hasAudioSample: false },
      collocations: [
        { id: 'w-isogashii-coll-1', expression: '仕事で忙しい', reading: 'しごとで いそがしい', chinese: '因为工作很忙', usage: '断りの理由を言うとき' },
        { id: 'w-isogashii-coll-2', expression: '忙しい中', reading: 'いそがしい なか', chinese: '在百忙之中', usage: 'お礼や挨拶の文' }
      ],
      relatedWords: [
        { id: 'w-isogashii-rel-1', relation: '対義語', heading: '暇', reading: 'ひま', chinese: '空闲（な形容词、名词）' },
        { id: 'w-isogashii-rel-2', relation: '類義語', heading: '慌ただしい', reading: 'あわただしい', chinese: '慌忙、匆忙（强调动作和气氛）' }
      ],
      usageNotes: [
        { id: 'w-isogashii-use-1', register: 'どちらも', politeness: '普通', audience: '友だち・同僚', note: '「ちょっと忙しくて」は、断りの前置きとしてよく使います。', senseNumber: 1 },
        { id: 'w-isogashii-use-2', register: '書き言葉', politeness: '丁寧', audience: 'メール・挨拶文', note: '「ご多忙のところ恐れ入りますが」のように、目上の人には別の言い方を使います。', senseNumber: null }
      ],
      memoryHint: { hint: '「いそがしい」は「心が忙しい」と覚える。', basis: '漢字の字面（「忙」に「心」が入っていること）と、締め切りが重なって落ち着かない場面の連想。' },
      practices: [
        { id: 'w-isogashii-pr-1', kind: 'PARTICLE', question: '今週は仕事（　）忙しいです。', questionChinese: '选择正确的助词填空。', choices: ['で', 'に', 'を', 'が'], answer: 'で', explanation: '原因や理由を表す「で」を使います。「仕事で忙しい」は決まった言い方です。', freeWriting: false, demoFeedback: null },
        { id: 'w-isogashii-pr-2', kind: 'SYNONYM', question: '（目上の人について）田中先生は今週はご（　）のようです。', questionChinese: '谈到长辈时，用哪个词最合适？', choices: ['多忙', '忙しい', '暇'], answer: '多忙', explanation: '目上の人の状態を改まって言うときは「多忙」を使うことが多いです。自分には「忙しい」を使います。', freeWriting: false, demoFeedback: null },
        {
          id: 'w-isogashii-pr-3',
          kind: 'WRITING',
          question: 'あなたが忙しいときにしている工夫を、一文で書いてみましょう。',
          questionChinese: '用一句话写写你忙的时候会怎么做。',
          choices: [],
          answer: '忙しい日は、朝のうちにその日の予定を書き出して、優先順位をつけるようにしています。',
          explanation: '「〜ようにしています」を使うと、習慣として続けている感じが出ます。',
          freeWriting: true,
          demoFeedback: { grammar: '「〜ようにしています」の形が正しく使えています。', naturalness: '「朝のうちに」を入れると、いつやるのかがはっきりして自然です。', reference: '忙しい日は、朝のうちにその日の予定を書き出して、優先順位をつけています。' }
        }
      ]
    },
    updatedAt: '2026-09-14T18:00:00',
    demoNote: null
  },
  {
    id: 'w-yasashii',
    heading: '優しい',
    reading: 'やさしい',
    alternateReading: null,
    partOfSpeech: 'い形容詞',
    jlpt: 'N4',
    chineseMeaning: '温柔、和蔼；（声音、味道）柔和',
    detailStatus: 'GENERATED',
    failureReason: null,
    manuallyEdited: false,
    collections: [
      { id: 'w-yasashii-col-1', book: 'デモ日本語 初級', unit: 'Unit001', seq: 14, listedWord: '優しい', listedReading: 'やさしい', listedChinese: '温柔' }
    ],
    detail: {
      coreMeaning: '相手を思いやって、穏やかに接する様子。',
      descriptionJa:
        '「優しい」は、相手の気持ちを考えて、穏やかに接する様子を表すい形容詞です。人にも、声や色、味などにも使えます。',
      descriptionZh:
        '「優しい」是い形容词，表示体谅对方、态度温和。既可用于人，也可以形容声音、颜色、味道等，如「優しい味」（口味很柔和）。读音相同的「易しい」是“简单、容易”的意思，汉字不同，意思也不同。',
      senses: [
        { id: 'w-yasashii-sense-1', number: 1, japanese: '相手を思いやって、穏やかに接する。', chinese: '温柔、和蔼：体贴对方，态度温和。', context: '人柄・先生や友だちの態度', style: '普通' },
        { id: 'w-yasashii-sense-2', number: 2, japanese: '刺激が少なく、穏やかで心地よい。', chinese: '（声音、颜色、味道）柔和、不刺激。', context: '食べ物・色・声', style: '普通' }
      ],
      examples: [
        { id: 'w-yasashii-ex-1', japanese: '田中先生は、いつも優しく教えてくれます。', reading: 'たなかせんせいは、いつも やさしく おしえてくれます。', chinese: '田中老师总是很温和地教我们。', senseNumber: 1, source: '学校生活', level: 'BASIC' },
        { id: 'w-yasashii-ex-2', japanese: 'このスープは味が優しいですね。', reading: 'この スープは あじが やさしいですね。', chinese: '这个汤的味道很柔和。', senseNumber: 2, source: '食事の場面', level: 'BASIC' },
        { id: 'w-yasashii-ex-3', japanese: '忙しいときでも、優しい言い方を心がけたいです。', reading: 'いそがしい ときでも、やさしい いいかたを こころがけたいです。', chinese: '即使很忙，我也想尽量注意说话方式要温和。', senseNumber: 1, source: '振り返り（応用）', level: 'APPLIED' }
      ],
      patterns: [
        { id: 'w-yasashii-pat-1', pattern: '人**に**優しい', reading: 'ひとに やさしい', chinese: '对某人温柔（对象用「に」）', example: '彼はだれにでも優しいです。', exampleChinese: '他对谁都很温柔。' },
        { id: 'w-yasashii-pat-2', pattern: '優しい**声で**話す', reading: 'やさしい こえで はなす', chinese: '用温和的声音说（い形容词直接修饰名词）', example: '子どもに優しい声で話しかけました。', exampleChinese: '我用温和的声音跟孩子说话。' }
      ],
      dialogs: [],
      synonyms: [
        { id: 'w-yasashii-syn-1', heading: '親切', reading: 'しんせつ', chinese: '亲切、热心', shared: '相手によくしてあげるという点。', difference: '「優しい」は性格や態度の温かさ、「親切」は実際の行為や助けに重点があります。', usage: '人柄・行為', interchangeable: 'SOMETIMES', interchangeNote: '「優しい人」は性格の話、「親切な人」はよく助けてくれる人の話です。行為をほめるときは「親切」、話し方や雰囲気には「優しい」が合います。', exampleJapanese: '道を教えてくれて、とても親切でした。', exampleChinese: '他给我指了路，非常热心。' }
      ],
      cautions: [
        { id: 'w-yasashii-cau-1', kind: 'PARTICLE', title: '「人を優しい」は助詞が違う', wrong: '彼は友達を優しいです。', correct: '彼は友達に優しいです。', reason: '「優しい」は相手を「に」で示します。「を」は使いません。中国語の“对朋友很温柔”の「对」は「に」にあたります。' },
        { id: 'w-yasashii-cau-2', kind: 'MEANING', title: '同じ「やさしい」でも「易しい」は別の意味', wrong: 'この問題は優しいです。（「簡単だ」の意味で使う）', correct: 'この問題は易しいです。／この問題は簡単です。', reason: '「易しい」は「簡単だ」という意味で、読みは同じ「やさしい」です。人柄について言うときは「優しい」を使います。漢字を間違えると意味が変わります。' }
      ],
      conjugations: [],
      transitivityPair: null,
      pronunciation: { reading: 'やさしい', accentType: null, accentNotation: null, hint: '「やさしい」は4拍です。アクセントの型は資料によって扱いが分かれるため、このデモでは表示していません。「優しい」と「易しい」は同じ読みなので、文脈で区別します。', hasAudioSample: false },
      collocations: [
        { id: 'w-yasashii-coll-1', expression: '人に優しい', reading: 'ひとに やさしい', chinese: '待人温柔', usage: '人物の紹介' },
        { id: 'w-yasashii-coll-2', expression: '優しい味', reading: 'やさしい あじ', chinese: '味道柔和、不刺激', usage: '料理の感想' }
      ],
      relatedWords: [
        { id: 'w-yasashii-rel-1', relation: '類義語', heading: '親切', reading: 'しんせつ', chinese: '亲切、热心（强调行为）' },
        { id: 'w-yasashii-rel-2', relation: '対義語', heading: '厳しい', reading: 'きびしい', chinese: '严格、严厉' }
      ],
      usageNotes: [
        { id: 'w-yasashii-use-1', register: 'どちらも', politeness: '普通', audience: '友だち・先生', note: '「優しい」は人物の評価なので、目上の人について直接使うときは「とても穏やかな方です」など別の言い方を選ぶこともあります。', senseNumber: 1 }
      ],
      memoryHint: { hint: '「優」の字には「人」が入っている。人の気持ちを考える字、と覚える。', basis: '漢字の形（「優」に「亻」が入っていること）と、先生が穏やかに話す場面の連想。' },
      practices: [
        { id: 'w-yasashii-pr-1', kind: 'PARTICLE', question: '彼はだれ（　）でも優しいです。', questionChinese: '选择正确的助词填空。', choices: ['に', 'を', 'が', 'で'], answer: 'に', explanation: '「優しい」は相手を「に」で示します。「だれにでも」で「对谁都是」的意思。', freeWriting: false, demoFeedback: null },
        { id: 'w-yasashii-pr-2', kind: 'SYNONYM', question: '（友だちが荷物を運ぶのを手伝ってくれた）「彼は（　）人です。」', questionChinese: '朋友帮你搬了行李，用哪个词更合适？', choices: ['親切な', '優しいな', '易しいな'], answer: '親切な', explanation: '実際に助けてくれた行為をほめるときは「親切」が合います。「優しいな人」は語形が誤り（い形容詞に「な」はつきません）。', freeWriting: false, demoFeedback: null }
      ]
    },
    updatedAt: '2026-09-14T18:40:00',
    demoNote: null
  },
  // ── な形容詞 ───────────────────────────────────────────────────────────
  {
    id: 'w-daijoubu',
    heading: '大丈夫',
    reading: 'だいじょうぶ',
    alternateReading: null,
    partOfSpeech: 'な形容詞',
    jlpt: 'N5',
    chineseMeaning: '没问题、不要紧（中文的“大丈夫”是另一个意思）',
    detailStatus: 'EDITED',
    failureReason: null,
    manuallyEdited: true,
    collections: [
      { id: 'w-daijoubu-col-1', book: '学校生活のことば', unit: 'Unit002', seq: 7, listedWord: '大丈夫', listedReading: 'だいじょうぶ', listedChinese: '没关系' },
      { id: 'w-daijoubu-col-2', book: '日常会話ステップアップ', unit: 'Unit001', seq: 15, listedWord: '大丈夫', listedReading: 'だいじょうぶ', listedChinese: '不要紧；没问题' }
    ],
    detail: {
      coreMeaning: '問題がない、心配しなくてよいという様子。',
      descriptionJa:
        '「大丈夫」は、問題がない、心配しなくていいという意味のな形容詞です。「大丈夫ですか」と相手の様子を確かめたり、「大丈夫です」と申し出を断ったりするのにも使います。中国語の「大丈夫」とは意味が違います。',
      descriptionZh:
        '「大丈夫」是な形容词，表示“没问题、不用担心”。它既可以用来询问对方的状态（大丈夫ですか），也可以用来婉拒对方的提议（大丈夫です）。请注意：中文的“大丈夫”指“有气概的男子汉”，和日语的意思完全不同。',
      senses: [
        { id: 'w-daijoubu-sense-1', number: 1, japanese: '問題がなく、心配しなくてよい様子。', chinese: '没问题、不要紧：不必担心。', context: '体調・予定の確認', style: '普通' },
        { id: 'w-daijoubu-sense-2', number: 2, japanese: '相手の申し出をやわらかく断る言い方。', chinese: '（婉拒时说的）不用了、没关系。', context: '店・目上の人からの申し出', style: '話し言葉' }
      ],
      examples: [
        { id: 'w-daijoubu-ex-1', japanese: '転んだけど、大丈夫です。けがはしていません。', reading: 'ころんだけど、だいじょうぶです。けがは していません。', chinese: '我摔了一跤，但没事，没有受伤。', senseNumber: 1, source: '学校生活', level: 'BASIC' },
        { id: 'w-daijoubu-ex-2', japanese: '明日の朝までに出せば、大丈夫ですか。', reading: 'あしたの あさまでに だせば、だいじょうぶですか。', chinese: '明天早上之前交的话，就没问题吗？', senseNumber: 1, source: '教室での確認', level: 'BASIC' },
        { id: 'w-daijoubu-ex-3', japanese: '「荷物を持ちましょうか」と聞かれて、「大丈夫です、自分で持てます」と答えました。', reading: '「にもつを もちましょうか」と きかれて、「だいじょうぶです、じぶんで もてます」と こたえました。', chinese: '有人问“要我帮你拿行李吗”，我回答“不用了，我自己能拿”。', senseNumber: 2, source: '電車の中（応用）', level: 'APPLIED' }
      ],
      patterns: [
        { id: 'w-daijoubu-pat-1', pattern: '〜**は**大丈夫です', reading: '〜は だいじょうぶです', chinese: '……没问题（用「は」提出话题）', example: 'この日程は大丈夫です。', exampleChinese: '这个日程没问题。' },
        { id: 'w-daijoubu-pat-2', pattern: '〜**ても**大丈夫ですか', reading: '〜ても だいじょうぶですか', chinese: '做……也没关系吗（请求许可）', example: 'ここに座っても大丈夫ですか。', exampleChinese: '我可以坐这里吗？' }
      ],
      dialogs: [
        {
          id: 'w-daijoubu-dlg-1',
          scene: '病院の待合室で、看護師に体調を聞かれる',
          lines: [
            { id: 'w-daijoubu-dlg-1-l1', speaker: '看護師', japanese: 'お名前をお呼びするまで、こちらでお待ちください。気分は大丈夫ですか。', chinese: '在叫到您的名字之前，请在这里等候。身体感觉还好吗？' },
            { id: 'w-daijoubu-dlg-1-l2', speaker: '学生', japanese: 'はい、大丈夫です。少し頭が痛いだけです。', chinese: '是的，没问题。只是有点头痛。' },
            { id: 'w-daijoubu-dlg-1-l3', speaker: '看護師', japanese: '寒くないですか。毛布をお持ちしましょうか。', chinese: '不冷吗？要我拿条毯子来吗？' },
            { id: 'w-daijoubu-dlg-1-l4', speaker: '学生', japanese: '大丈夫です。ありがとうございます。', chinese: '不用了，谢谢。' }
          ]
        }
      ],
      synonyms: [
        { id: 'w-daijoubu-syn-1', heading: '問題ない', reading: 'もんだいない', chinese: '没问题（较直接）', shared: '心配いらないという点。', difference: '「問題ない」はやや事務的で、確認や報告で使いやすいです。「大丈夫」は体調や相手の様子にも使えます。', usage: '仕事の確認・報告', interchangeable: 'SOMETIMES', interchangeNote: '報告ではどちらも使えますが、「お体は大丈夫ですか」を「問題ないですか」にすると、相手の体調を気づかう感じが弱くなります。', exampleJapanese: 'この日程で問題ないですか。', exampleChinese: '这个日程没问题吗？' },
        {
          id: 'w-daijoubu-syn-2',
          heading: '結構です',
          reading: 'けっこうです',
          chinese: '不用了、可以了',
          shared: '申し出を断るという点。',
          difference:
            '「結構です」は丁寧ですが、言い方によっては強く聞こえることがあります。「大丈夫です」はもう少しやわらかい断り方です。',
          usage: '店や目上の人からの申し出を断るとき',
          interchangeable: 'SOMETIMES',
          interchangeNote:
            'どちらも断りになりますが、目上の人には「大丈夫です」のほうが角が立ちにくいことが多いです。場面によって選びましょう。',
          exampleJapanese: 'コーヒーのおかわりはいかがですか。／結構です。',
          exampleChinese: '要不要再来一杯咖啡？／不用了。'
        }
      ],
      cautions: [
        { id: 'w-daijoubu-cau-1', kind: 'MEANING', title: '中国語の「大丈夫」と意味が違う', wrong: '彼は大丈夫な男です。（“他是个男子汉”の意味で使う）', correct: '彼は頼りになる男です。／彼はしっかりした男です。', reason: '中文的“大丈夫”指“有气概的男子汉”，日语的「大丈夫」是“没问题、不要紧”。想说“靠得住的男人”要用「頼りになる」「しっかりした」等。' },
        { id: 'w-daijoubu-cau-2', kind: 'UNNATURAL', title: 'すすめる場面で「大丈夫ですか」は使いにくい', wrong: '（先生に）コーヒー、大丈夫ですか。', correct: '（先生に）コーヒーはいかがですか。／コーヒーをお持ちしましょうか。', reason: '「大丈夫ですか」は相手の状態を確かめるときの言い方で、何かをすすめる場面では使いにくいことがあります。目上の人には「いかがですか」が無難です。' }
      ],
      conjugations: [],
      transitivityPair: null,
      pronunciation: { reading: 'だいじょうぶ', accentType: 3, accentNotation: 'だいじょꜜうぶ', hint: '「だいじょꜜうぶ」は「じょ」のあとで下がります。のばす音（じょ＋う）を2拍として数えるので、全体は5拍です。', hasAudioSample: false },
      collocations: [
        { id: 'w-daijoubu-coll-1', expression: '大丈夫ですか', reading: 'だいじょうぶですか', chinese: '你没事吧？／不要紧吗？', usage: '相手の様子を確かめるとき' },
        { id: 'w-daijoubu-coll-2', expression: '大丈夫です（断り）', reading: 'だいじょうぶです', chinese: '不用了（婉拒）', usage: '店や目上の人の申し出を断るとき' }
      ],
      relatedWords: [
        { id: 'w-daijoubu-rel-1', relation: '間違えやすい', heading: '丈夫', reading: 'じょうぶ', chinese: '结实、耐用（な形容词，与「大丈夫」是不同的词）' },
        { id: 'w-daijoubu-rel-2', relation: '類義語', heading: '問題ない', reading: 'もんだいない', chinese: '没问题（较直接）' }
      ],
      usageNotes: [
        { id: 'w-daijoubu-use-1', register: '話し言葉', politeness: '普通', audience: '友だち・同僚', note: '「大丈夫？」だけでも通じます。目上の人には「大丈夫ですか」にします。', senseNumber: 1 },
        { id: 'w-daijoubu-use-2', register: 'どちらも', politeness: '丁寧', audience: '店・目上の人', note: '断りの「大丈夫です」は便利ですが、相手によっては「結構です」のほうがはっきり伝わることもあります。', senseNumber: 2 }
      ],
      memoryHint: { hint: '「大丈夫」＝「OK」。男子漢の意味ではない。', basis: '中国語との意味の対応を一文で言い切って覚える（漢字の字面ではなく、意味の対応の連想）。' },
      practices: [
        { id: 'w-daijoubu-pr-1', kind: 'SCENE', question: '店で「袋はご入用ですか」と聞かれ、断りたい。いちばん自然なのはどれですか。', questionChinese: '店员问“需要袋子吗？”，你想拒绝，哪句最自然？', choices: ['大丈夫です。', 'だめです。', 'いりませんでしょう。'], answer: '大丈夫です。', explanation: 'やわらかい断りには「大丈夫です」がよく使われます。「だめです」は強い言い方、「〜でしょう」は確認の形なので、ここでは合いません。', freeWriting: false, demoFeedback: null },
        { id: 'w-daijoubu-pr-2', kind: 'SYNONYM', question: '（体調を気づかう場面で）「お体は（　）ですか。」いちばん自然なのはどれですか。', questionChinese: '关心对方身体状况时，哪个说法最自然？', choices: ['大丈夫', '問題ない', '丈夫'], answer: '大丈夫', explanation: '体調を気づかうときは「大丈夫ですか」が自然です。「問題ないですか」は事務的な確認に聞こえ、「丈夫」は“结实、耐用”という別の意味になります。', freeWriting: false, demoFeedback: null },
        {
          id: 'w-daijoubu-pr-3',
          kind: 'WRITING',
          question:
            '友だちが心配して「大丈夫？」と聞いてきました。あなたならどう答えますか。一文書いてみましょう。',
          questionChinese: '朋友担心地问你“没事吧？”，你会怎么回答？写一句话试试。',
          choices: [],
          answer: '大丈夫、少し疲れただけだから、少し休めば平気だよ。',
          explanation: '「大丈夫」＋理由＋今後の見通しの順にすると、相手が安心します。',
          freeWriting: true,
          demoFeedback: { grammar: '「大丈夫」を文頭に置き、理由を「〜だけだから」でつなぐ形が正しく使えています。', naturalness: '「少し休めば平気だよ」と付け加えると、相手が安心できる自然な答えになります。', reference: '大丈夫。ちょっと疲れただけだから、少し休めば元気になるよ。' }
        }
      ]
    },
    updatedAt: '2026-09-14T19:10:00',
    demoNote: '日中同形異義語／手で修正した例（manuallyEdited）'
  },
  {
    id: 'w-taisetsu',
    heading: '大切',
    reading: 'たいせつ',
    alternateReading: null,
    partOfSpeech: 'な形容詞',
    jlpt: 'N4',
    chineseMeaning: '重要、珍贵（中文里没有这个词，别按字面理解）',
    detailStatus: 'GENERATED',
    failureReason: null,
    manuallyEdited: false,
    collections: [
      { id: 'w-taisetsu-col-1', book: 'デモ日本語 初級', unit: 'Unit002', seq: 8, listedWord: '大切', listedReading: 'たいせつ', listedChinese: '重要' }
    ],
    detail: {
      coreMeaning: '価値が高く、いいかげんに扱えない様子。',
      descriptionJa:
        '「大切」は、価値があって、いいかげんに扱えないという意味のな形容詞です。「大切にする」の形で、丁寧に扱うという意味でもよく使います。',
      descriptionZh:
        '「大切」是な形容词，表示有价值、不能随便对待，常用「大切にする」表示“珍惜、爱惜”。中文里没有「大切」这个词，容易按汉字字面理解成“切得很厉害”；它的意思是“重要、珍贵”，和中文的「亲切」也不是一回事。',
      senses: [
        { id: 'w-taisetsu-sense-1', number: 1, japanese: '価値があって、いいかげんに扱えない。', chinese: '重要、珍贵：有价值，不能随便对待。', context: '物・時間・経験', style: '普通' },
        { id: 'w-taisetsu-sense-2', number: 2, japanese: '人にとって、かけがえがない。', chinese: '（对人来说）重要的、不可替代的。', context: '人との関係', style: '普通' }
      ],
      examples: [
        { id: 'w-taisetsu-ex-1', japanese: '家族との時間をいちばん大切にしています。', reading: 'かぞくとの じかんを いちばん たいせつに しています。', chinese: '我最珍惜和家人在一起的时间。', senseNumber: 2, source: '自己紹介', level: 'BASIC' },
        { id: 'w-taisetsu-ex-2', japanese: '毎日の積み重ねが大切だと教わりました。', reading: 'まいにちの つみかさねが たいせつだと おそわりました。', chinese: '老师告诉我们，每天的积累很重要。', senseNumber: 1, source: '授業', level: 'BASIC' },
        { id: 'w-taisetsu-ex-3', japanese: 'この資料はあとで使うので、大切にしまっておいてください。', reading: 'この しりょうは あとで つかうので、たいせつに しまっておいてください。', chinese: '这份资料之后还要用，请小心收好。', senseNumber: 1, source: '事務室での依頼（応用）', level: 'APPLIED' }
      ],
      patterns: [
        { id: 'w-taisetsu-pat-1', pattern: '〜**が**大切だ', reading: '〜が たいせつだ', chinese: '……很重要（用「が」提出重要的内容）', example: '毎日の練習が大切です。', exampleChinese: '每天的练习很重要。' },
        { id: 'w-taisetsu-pat-2', pattern: '〜**を**大切にする', reading: '〜を たいせつに する', chinese: '珍惜、爱惜……（对象用「を」）', example: '友だちを大切にしたいです。', exampleChinese: '我想珍惜朋友。' }
      ],
      dialogs: [],
      synonyms: [
        { id: 'w-taisetsu-syn-1', heading: '大事', reading: 'だいじ', chinese: '重要、要紧', shared: '価値があって、いいかげんに扱えないという点。', difference: '「大事」は会話でよく使うややカジュアルな言い方で、「大切」は少し丁寧で文章にも使います。', usage: '会話・文章の両方', interchangeable: 'SOMETIMES', interchangeNote: '「大事な書類」と「大切な書類」はどちらも通じますが、改まった文章やスピーチでは「大切」のほうが落ち着いて見えます。', exampleJapanese: 'これは大事な書類ですから、なくさないでください。', exampleChinese: '这是重要文件，请别弄丢。' }
      ],
      cautions: [
        { id: 'w-taisetsu-cau-1', kind: 'MEANING', title: '「大切」を字面どおりに取らない', wrong: 'この包丁は大切です。（「よく切れる」の意味で使う）', correct: 'この包丁はよく切れます。／この包丁は大切な道具です。', reason: '中文里没有「大切」这个词，按字面理解成“切得很厉害”是错的。日语的「大切」是“重要、珍贵”的意思。另外，中文的「亲切」对应日语的「親切」，字形相似但意思不同，也要注意。' }
      ],
      conjugations: [],
      transitivityPair: null,
      pronunciation: { reading: 'たいせつ', accentType: null, accentNotation: null, hint: '「たいせつ」は4拍です。アクセントの型は資料によって扱いが分かれるため、このデモでは表示していません。', hasAudioSample: false },
      collocations: [
        { id: 'w-taisetsu-coll-1', expression: '大切にする', reading: 'たいせつに する', chinese: '珍惜、爱惜', usage: '物や人を大事に扱うとき' },
        { id: 'w-taisetsu-coll-2', expression: '大切な人', reading: 'たいせつな ひと', chinese: '重要的人、珍视的人', usage: '気持ちを伝えるとき' }
      ],
      relatedWords: [
        { id: 'w-taisetsu-rel-1', relation: '類義語', heading: '大事', reading: 'だいじ', chinese: '重要（会话里更常用）' },
        { id: 'w-taisetsu-rel-2', relation: '間違えやすい', heading: '親切', reading: 'しんせつ', chinese: '亲切（字形相似，意思不同）' }
      ],
      usageNotes: [
        { id: 'w-taisetsu-use-1', register: 'どちらも', politeness: '普通', audience: '友だち・先生', note: '「大切にする」は、人にも物にも経験にも使える便利な形です。', senseNumber: null }
      ],
      memoryHint: { hint: '「大切」＝「大事」。切ることとは関係ない。', basis: '中国語の「亲切」との字形の似かよりではなく、意味の対応（大切＝大事）を言い切って覚える覚え方。' },
      practices: [
        { id: 'w-taisetsu-pr-1', kind: 'PARTICLE', question: '家族（　）大切にしています。', questionChinese: '选择正确的助词填空。', choices: ['を', 'が', 'に', 'で'], answer: 'を', explanation: '「大切にする」の対象は「を」で示します。「が」を使うと「家族が大切だ」のように評価の文になります。', freeWriting: false, demoFeedback: null },
        { id: 'w-taisetsu-pr-2', kind: 'SYNONYM', question: '（改まったスピーチで）「学生の皆さん、毎日の積み重ねが（　）です。」', questionChinese: '在正式致辞中，哪个词更合适？', choices: ['大切', '大事', '親切'], answer: '大切', explanation: '「大事」も誤りではありませんが、改まった場面では「大切」のほうが落ち着いて聞こえます。「親切」は別の意味です。', freeWriting: false, demoFeedback: null }
      ]
    },
    updatedAt: '2026-09-14T19:35:00',
    demoNote: '日中同形異義語'
  },
  // ── 副詞 ───────────────────────────────────────────────────────────────
  {
    id: 'w-tabun',
    heading: 'たぶん',
    reading: 'たぶん',
    alternateReading: null,
    partOfSpeech: '副詞',
    jlpt: 'N4',
    chineseMeaning: '大概、可能',
    detailStatus: 'GENERATED',
    failureReason: null,
    manuallyEdited: false,
    collections: [
      { id: 'w-tabun-col-1', book: '日常会話ステップアップ', unit: 'Unit001', seq: 4, listedWord: 'たぶん', listedReading: 'たぶん', listedChinese: '大概' }
    ],
    detail: {
      coreMeaning: 'はっきりは分からないが、そうだと思う気持ちを表す。',
      descriptionJa:
        '「たぶん」は、確かではないけれど、そうだと思う気持ちを表す副詞です。後ろに「〜でしょう」「〜と思います」のような推量の表現を伴うことが多いです。',
      descriptionZh:
        '「たぶん」是副词，表示虽然不确定，但自己觉得大概是这样。后面常跟「〜でしょう」「〜と思います」等表示推测的说法。',
      senses: [
        { id: 'w-tabun-sense-1', number: 1, japanese: '確かではないが、そうだと思う様子。', chinese: '大概、可能：虽然不确定，但觉得应该是这样。', context: '会話・予定の話', style: '話し言葉' }
      ],
      examples: [
        { id: 'w-tabun-ex-1', japanese: '彼はたぶん来ないでしょう。', reading: 'かれは たぶん こないでしょう。', chinese: '他大概不会来吧。', senseNumber: 1, source: '友だちとの会話', level: 'BASIC' },
        { id: 'w-tabun-ex-2', japanese: 'たぶん明日は雨が降ると思います。', reading: 'たぶん あしたは あめが ふると おもいます。', chinese: '我想明天大概会下雨。', senseNumber: 1, source: '天気の話', level: 'BASIC' }
      ],
      patterns: [
        { id: 'w-tabun-pat-1', pattern: 'たぶん〜でしょう', reading: 'たぶん 〜でしょう', chinese: '大概会……吧（「でしょう」表示推测）', example: 'たぶん間に合うでしょう。', exampleChinese: '大概赶得上吧。' },
        { id: 'w-tabun-pat-2', pattern: 'たぶん〜と思います', reading: 'たぶん 〜と おもいます', chinese: '我觉得大概……', example: 'たぶん彼も来ると思います。', exampleChinese: '我觉得他大概也会来。' }
      ],
      dialogs: [],
      synonyms: [],
      cautions: [
        { id: 'w-tabun-cau-1', kind: 'UNNATURAL', title: '「たぶん」＋言い切りは不自然', wrong: 'たぶん行きます。', correct: 'たぶん行くでしょう。／おそらく行きます。', reason: '「たぶん」は確かでない気持ちを表すので、後ろは「〜でしょう」「〜と思います」のような推量の形にするのが自然です。会話で「たぶん行きます」と聞くこともありますが、丁寧な場面では避けたほうが無難です。' }
      ],
      conjugations: [],
      transitivityPair: null,
      pronunciation: { reading: 'たぶん', accentType: null, accentNotation: null, hint: '「たぶん」は3拍です。「た」を強くしすぎず、最後の「ん」まで短くまとめます。アクセントの型はこのデモでは未確認のため表示していません。', hasAudioSample: false },
      collocations: [],
      relatedWords: [],
      usageNotes: [
        { id: 'w-tabun-use-1', register: '話し言葉', politeness: '普通', audience: '友だち・同僚', note: '書き言葉では「おそらく」のほうがよく使われます。', senseNumber: 1 }
      ],
      memoryHint: null,
      practices: []
    },
    updatedAt: '2026-09-14T19:50:00',
    demoNote: '詳細は部分的な例（会話・類義語・練習がまだ空）'
  },
  {
    id: 'w-shikkari',
    heading: 'しっかり',
    reading: 'しっかり',
    alternateReading: null,
    partOfSpeech: '副詞',
    jlpt: null,
    chineseMeaning: '好好地、扎实地；可靠',
    detailStatus: 'GENERATED',
    failureReason: null,
    manuallyEdited: false,
    collections: [
      { id: 'w-shikkari-col-1', book: '学校生活のことば', unit: 'Unit003', seq: 3, listedWord: 'しっかり', listedReading: 'しっかり', listedChinese: '好好地；扎实' }
    ],
    detail: {
      coreMeaning: '確実で、ゆるがない様子。',
      descriptionJa:
        '「しっかり」は、確実でゆるがない様子を表す副詞です。「しっかり勉強する」「しっかり食べる」のように、後ろの動詞を強めるように使います。',
      descriptionZh:
        '「しっかり」是副词，表示扎实、牢靠、不松懈的样子，常用来加强后面的动词，如「しっかり勉強する」（好好学习）、「しっかり食べる」（好好吃饭）。修饰名词时要用「しっかりした＋名词」的形式。',
      senses: [
        { id: 'w-shikkari-sense-1', number: 1, japanese: '確実で、ゆるがない様子。', chinese: '好好地、扎实地：不松懈、牢靠。', context: '勉強・生活習慣', style: '普通' },
        { id: 'w-shikkari-sense-2', number: 2, japanese: '考え方や態度が確かな様子（「しっかりした人」の形で）。', chinese: '（以「しっかりした人」的形式）可靠、稳重。', context: '人柄の評価', style: '普通' }
      ],
      examples: [
        { id: 'w-shikkari-ex-1', japanese: '試験の前は、毎日しっかり復習しています。', reading: 'しけんの まえは、まいにち しっかり ふくしゅうしています。', chinese: '考试前，我每天都好好复习。', senseNumber: 1, source: '学生生活', level: 'BASIC' },
        { id: 'w-shikkari-ex-2', japanese: '彼は年下ですが、とてもしっかりした人です。', reading: 'かれは とししたですが、とても しっかりした ひとです。', chinese: '他虽然比我小，但是个非常可靠的人。', senseNumber: 2, source: '人柄の話', level: 'APPLIED' }
      ],
      patterns: [
        { id: 'w-shikkari-pat-1', pattern: 'しっかり＋動詞', reading: 'しっかり ＋ どうし', chinese: '好好地做……（副词直接修饰动词）', example: '朝ごはんをしっかり食べてから出かけます。', exampleChinese: '我好好吃完早饭再出门。' },
        { id: 'w-shikkari-pat-2', pattern: 'しっかり**した**＋名詞', reading: 'しっかりした ＋ めいし', chinese: '可靠的……（修饰名词时要加「した」）', example: '彼女はしっかりした考えを持っています。', exampleChinese: '她有很踏实的想法。' }
      ],
      dialogs: [],
      synonyms: [],
      cautions: [
        { id: 'w-shikkari-cau-1', kind: 'GRAMMAR', title: '名詞を修飾するときは「しっかりした」', wrong: '彼はしっかりな人です。', correct: '彼はしっかりした人です。', reason: '「しっかり」は副詞なので、名詞を修飾するときは「しっかりした＋名詞」の形にします。「な」をつけることはできません（「な」をつけるのはな形容詞です）。' }
      ],
      conjugations: [],
      transitivityPair: null,
      pronunciation: { reading: 'しっかり', accentType: null, accentNotation: null, hint: '「しっかり」は4拍で、途中に小さい「っ」があります。小さい「っ」は1拍として数えるので、「し・か・っ・り」と区切って練習すると安定します。アクセントの型はこのデモでは未確認のため表示していません。', hasAudioSample: false },
      collocations: [
        { id: 'w-shikkari-coll-1', expression: 'しっかり勉強する', reading: 'しっかり べんきょうする', chinese: '好好学习', usage: '励ますとき・習慣の話' }
      ],
      relatedWords: [
        { id: 'w-shikkari-rel-1', relation: '類義語', heading: 'きちんと', reading: 'きちんと', chinese: '整整齐齐地、规规矩矩地（重点在正确、整齐）' }
      ],
      usageNotes: [
        { id: 'w-shikkari-use-1', register: 'どちらも', politeness: '普通', audience: '友だち・家族', note: '「しっかりして」は励ましにも、少し強い注意にもなります。言い方に注意しましょう。', senseNumber: null }
      ],
      memoryHint: null,
      practices: [
        { id: 'w-shikkari-pr-1', kind: 'SCENE', question: '友だちが試験前に不安がっています。はげます言い方として、いちばん自然なのはどれですか。', questionChinese: '朋友考试前很不安，鼓励他时哪句最自然？', choices: [ '大丈夫、しっかり復習すれば間に合うよ。', '大丈夫、しっかりな復習をすれば間に合うよ。', '大丈夫、しっかりを復習すれば間に合うよ。' ], answer: '大丈夫、しっかり復習すれば間に合うよ。', explanation: '「しっかり」は副詞なので、そのまま動詞を修飾します。「しっかりな」も「しっかりを」も形が誤りです。', freeWriting: false, demoFeedback: null }
      ]
    },
    updatedAt: '2026-09-14T20:05:00',
    demoNote: '詳細は部分的な例（会話・類義語がまだ空）／JLPT は未設定'
  },
  // ── 未生成（新規登録の直後） ───────────────────────────────────────────
  {
    id: 'w-machiawaseru',
    heading: '待ち合わせる',
    reading: 'まちあわせる',
    alternateReading: null,
    partOfSpeech: '動詞',
    jlpt: null,
    chineseMeaning: '（约好时间和地点）碰头、会合',
    detailStatus: 'NOT_GENERATED',
    failureReason: null,
    manuallyEdited: false,
    collections: [
      { id: 'w-machiawaseru-col-1', book: 'デモ日本語 初級', unit: 'Unit004', seq: 18, listedWord: '待ち合わせる', listedReading: 'まちあわせる', listedChinese: '碰头' },
      { id: 'w-machiawaseru-col-2', book: '日常会話ステップアップ', unit: 'Unit002', seq: 5, listedWord: '待ち合わせる', listedReading: 'まちあわせる', listedChinese: '（约好）碰头、会合' }
    ],
    detail: null,
    updatedAt: '2026-09-14T20:20:00',
    demoNote: '新規登録の直後（未生成）／長い見出し語の確認用'
  }
]

/** 「新規登録」のデモで貼り付ける入力例（複数行テキスト） */
export const DEMO_PASTE_SAMPLE = `引っ越し\tひっこし\t搬家
手続き\tてつづき\t手续
履修登録\tりしゅうとうろく\t选课登记
開く\tあく・ひらく\t开；打开
大切\tたいせつ\t重要
大切\tたいせつ\t重要
申し込む\t\t申请
\tもうしこむ\t申请
確認\tかくにん\t确认
打ち合わせ\tうちあわせ\t碰头商谈；磋商
これはタブ区切りではありません`

/** 「新規登録」のデモで使う新規書籍名の初期値 */
export const DEMO_NEW_BOOK_NAME = 'デモ日本語 初中級'
