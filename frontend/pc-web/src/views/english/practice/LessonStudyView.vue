<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useTabKeys } from '@/composables/useTabKeys'
import StudyDialog from '@/features/english-study/StudyDialog.vue'
import PracticeDialog from '@/features/english-practice-demo/PracticeDialog.vue'
import { useEnglishStudyStore, type StudyKind, type StudyLevel } from '@/features/english-study/store'
import { usePracticeDemoStore } from '@/features/english-practice-demo/store'
import { answerLabel, clozeExplanationReady, judged, sourceLabels, wordCount, type Language, type Vocabulary } from '@/features/english-practice-demo/types'
import '@/features/english-practice-demo/lesson-demo.css'
const props = defineProps<{ kind: 'cloze' | 'intensive'; recordId?: string; questionId?: string; dialog?: boolean }>()
const route = useRoute(), store = usePracticeDemoStore(), wordStore = useEnglishStudyStore()
const base = computed(() => `/${route.path.split('/')[1]}/${props.kind === 'cloze' ? 'english-cloze' : 'english-reading-intensive'}`)
const activeRecordId = computed(() => props.recordId ?? String(route.params.recordId ?? ''))
const activeQuestionId = computed(() => props.questionId ?? String(route.params.questionId ?? ''))
const cloze = computed(() => store.clozeSets.find(s => s.id === activeRecordId.value))
const reading = computed(() => store.readings.find(s => s.id === activeRecordId.value))
const record = computed(() => props.kind === 'cloze' ? cloze.value : reading.value)
const questionIndex = ref(0), language = ref<Language>('ja'), translations = ref(false), tab = ref('passage'), sentenceId = ref('')
const question = computed(() => record.value?.questions[questionIndex.value])
const sentences = computed(() => reading.value?.paragraphs.flatMap(p => p.sentences) ?? [])
const sentence = computed(() => sentences.value.find(s => s.id === sentenceId.value) ?? sentences.value[0])
const analysisReady = computed(() => props.kind === 'cloze' ? !!cloze.value && !!question.value && clozeExplanationReady(cloze.value, question.value) : reading.value?.stages[3] === 'COMPLETED')
const fontSize = ref(18), practice = ref(false), history = ref(false), originals = ref(false), editing = ref(false), notice = ref(''), error = ref(''), memo = ref('')
const correction = ref<{ student: number | null; correct: number | null }>({ student: null, correct: null })
const attempts = computed(() => store.attemptsFor(props.kind, activeRecordId.value, props.kind === 'cloze' ? question.value?.id : undefined))
const vocabulary = ref<Vocabulary>(), book = ref(''), bookKind = ref<StudyKind>('word'), bookLevel = ref<StudyLevel>('intermediate')
const books = computed(() => [...new Set(wordStore.select(bookKind.value, bookLevel.value).map(w => w.book))])
const speaking = ref(false)
watch([activeRecordId, activeQuestionId, () => props.kind], () => {
  questionIndex.value = props.kind === 'cloze' ? cloze.value?.questions.findIndex(q => q.id === activeQuestionId.value) ?? -1 : 0
  tab.value = 'passage'; sentenceId.value = ''; editing.value = false; notice.value = ''; error.value = ''; memo.value = reading.value?.memo ?? ''; stop()
}, { immediate: true })
function editAnswers() { if (question.value) correction.value = { student: question.value.student, correct: question.value.correct }; editing.value = true }
function saveAnswers() { if (question.value) Object.assign(question.value, correction.value); editing.value = false; notice.value = '元の答案を修正しました。過去の再挑戦履歴は変更されません。' }
async function evidence(id: string) { tab.value = 'passage'; sentenceId.value = id; await nextTick(); document.getElementById(`sentence-${id}`)?.scrollIntoView({ block: 'center', behavior: 'smooth' }) }
function stop() { if ('speechSynthesis' in window) window.speechSynthesis.cancel(); speaking.value = false }
function speak(text: string) {
  stop(); if (!('speechSynthesis' in window)) { error.value = 'このブラウザでは読み上げを利用できません。'; return }
  const utterance = new SpeechSynthesisUtterance(text); utterance.lang = 'en-US'; utterance.rate = .85
  utterance.onend = () => { speaking.value = false }; utterance.onerror = () => { speaking.value = false; error.value = '読み上げを開始できませんでした。音声設定をご確認ください。' }
  speaking.value = true; window.speechSynthesis.speak(utterance)
}
onBeforeUnmount(stop)
function openVocabulary(v: Vocabulary) { vocabulary.value = v; book.value = v.book || '精読で出会った語彙'; bookKind.value = v.term.includes(' ') ? 'phrase' : 'word'; error.value = '' }
function addVocabulary() {
  const v = vocabulary.value, r = reading.value; if (!v || !r) return
  try {
    wordStore.saveWord({ kind: bookKind.value, level: bookLevel.value, book: book.value.trim(), category: r.title, grade: r.grade, english: v.term, japanese: v.meaning.ja, chinese: v.meaning.zh, pronunciation: v.pronunciation, example: v.context, translation: sentences.value.find(s => s.english === v.context)?.translation.ja ?? '', phraseType: bookKind.value === 'phrase' ? '精読で学んだ表現' : '—', pattern: '', favorite: false, mastered: false, detailReady: true })
    v.book = book.value.trim(); vocabulary.value = undefined; notice.value = '語彙を登録しました。単語情報管理・熟語情報管理から確認できます。'
  } catch (e) { error.value = (e as Error).message }
}
function selectQuestion(i: number) { questionIndex.value = i; editing.value = false }
/** タブのキーボード操作（←→・Home・End）。作法はリポジトリ共通の `useTabKeys` */
const tabKey = useTabKeys()
</script>
<template>
  <div class="ep">
    <div class="ep-bar ep-bar--between"><RouterLink v-if="!dialog" :to="base" class="btn btn--secondary">← 一覧へ戻る</RouterLink><div v-if="record" class="ep-bar"><button class="btn btn--secondary" @click="originals = true">原本画像</button><button class="btn btn--secondary" @click="history = true">再挑戦履歴（{{ attempts.length }}）</button><RouterLink class="btn btn--secondary" :to="`${base}/${record.id}/edit`">教材を編集</RouterLink><button class="btn btn--primary" :disabled="!record.questions.length || (kind === 'cloze' ? !question || question.correct === null : !analysisReady || record.questions.some(q => q.correct === null))" @click="practice = true">再挑戦</button></div></div>
    <p v-if="!record || (kind === 'cloze' && !question)" class="ep-empty">教材・設問が見つかりません。一覧から選択し直してください。</p>
    <template v-else>
      <p v-if="notice" class="ep-success" role="status">{{ notice }}</p><p v-if="error && !vocabulary" class="ep-error" role="alert">{{ error }}</p>
      <div class="ep-bar ep-bar--between"><div class="ep-bar"><span class="ep-tag">{{ sourceLabels[record.sourceType] }}</span><span>{{ record.sourceName }} · {{ record.year }} {{ record.chapter }}</span></div><div class="ep-bar"><label>解説言語<select v-model="language" class="select"><option value="ja">日本語</option><option value="zh">中文</option></select></label><label class="ep-option"><input v-model="translations" type="checkbox" />訳文を表示</label></div></div>
      <template v-if="kind === 'intensive' && reading">
        <div class="ep-tabs" role="tablist" aria-label="精読の表示" @keydown="tabKey"><button v-for="t in [{id:'passage',label:'原文精読'},{id:'questions',label:'設問解析'},{id:'vocabulary',label:'重点語彙'}]" :id="`tab-${t.id}`" :key="t.id" role="tab" :aria-selected="tab === t.id" :tabindex="tab === t.id ? 0 : -1" :class="{'is-active':tab === t.id}" aria-controls="study-content" @click="tab = t.id">{{ t.label }}</button></div>
        <section v-if="reading.stages[0] !== 'COMPLETED'" class="ep-card ep-empty"><p>{{ reading.stageError || 'OCR結果がまだありません。教材を編集してOCRを実行してください。' }}</p><RouterLink :to="`${base}/${reading.id}/edit`" class="btn btn--primary">作成工程を開く</RouterLink></section>
        <div v-else id="study-content" role="tabpanel" :aria-labelledby="`tab-${tab}`">
          <div v-if="tab === 'passage'" class="ep-split">
            <article class="ep-card ep-section">
              <div class="ep-bar ep-bar--between"><div><h2>{{ reading.title }}</h2><span class="ep-muted">{{ reading.grade }} · {{ reading.difficulty }} · {{ wordCount(reading) }} words</span></div><div class="ep-bar"><button class="btn btn--secondary btn--sm" :disabled="fontSize <= 14" aria-label="文字を小さく" @click="fontSize -= 2">A−</button><button class="btn btn--secondary btn--sm" :disabled="fontSize >= 26" aria-label="文字を大きく" @click="fontSize += 2">A＋</button><button class="btn btn--secondary btn--sm" @click="speaking ? stop() : speak(sentences.map(s => s.english).join(' '))">{{ speaking ? '読上げ停止' : '本文を読み上げ' }}</button></div></div>
              <div class="ep-passage" :style="{fontSize:`${fontSize}px`}"><section v-for="(p,i) in reading.paragraphs" :key="p.id" style="margin-bottom:24px"><p class="ep-muted">PARAGRAPH {{ i + 1 }} <span v-if="reading.stages[1] === 'COMPLETED'">· {{ p.role[language] }}</span></p><template v-for="s in p.sentences" :key="s.id"><button :id="`sentence-${s.id}`" class="ep-sentence" :class="{'is-active':sentence?.id === s.id}" @click="sentenceId = s.id">{{ s.english }}</button>{{ ' ' }}<span v-if="translations && reading.stages[2] === 'COMPLETED'" class="ep-translation">{{ s.translation[language] }}</span></template></section></div>
              <details v-if="reading.stages[1] === 'COMPLETED'" open class="ep-question"><summary>導読・文章の要点</summary><p>{{ reading.guide[language] }}</p><p class="ep-analysis">{{ reading.points[language] }}</p></details>
            </article><aside class="ep-section ep-sticky">
              <section v-if="sentence" class="ep-card ep-section"><div class="ep-bar ep-bar--between"><h3>一文ずつ理解する</h3><button class="btn btn--secondary btn--sm" @click="speak(sentence.english)">読上げ</button></div><p class="ep-question-text">{{ sentence.english }}</p><template v-if="reading.stages[2] === 'COMPLETED'"><p>{{ sentence.translation[language] }}</p><div class="ep-analysis">{{ sentence.grammar[language] }}</div><div class="ep-bar"><button v-for="v in reading.vocabulary.filter(v => sentence?.english === v.context || sentence?.english.toLowerCase().includes(v.term.toLowerCase()))" :key="v.id" class="ep-tag ep-link" @click="openVocabulary(v)">{{ v.term }} ↗</button></div></template><p v-else class="ep-note">「解説・重点語彙」の工程を完了すると、訳と文法解説が表示されます。</p></section>
              <section class="ep-card ep-section"><h3>学習メモ</h3><textarea v-model="memo" class="input" aria-label="学習メモ" placeholder="気づいた表現や次回確認したいこと" /><button class="btn btn--secondary" @click="reading.memo = memo; notice = '学習メモを保存しました。'">メモを保存</button></section>
            </aside>
          </div>
          <section v-else-if="tab === 'vocabulary'" class="ep-card ep-section"><div class="ep-bar ep-bar--between"><h2>重点語彙</h2><span class="ep-muted">{{ reading.vocabulary.length }} 語・表現</span></div><p v-if="reading.stages[2] !== 'COMPLETED'" class="ep-empty">「解説・重点語彙」の工程を完了してください。</p><div v-else class="ep-table-wrap"><table class="ep-table"><thead><tr><th>語彙・表現</th><th>意味</th><th>本文中の用例</th><th>単語帳</th></tr></thead><tbody><tr v-for="v in reading.vocabulary" :key="v.id"><td><button class="ep-link" @click="openVocabulary(v)"><strong>{{ v.term }}</strong></button><div class="ep-muted">{{ v.pronunciation }}</div><button class="ep-link" @click="speak(v.term)">発音</button></td><td>{{ v.meaning[language] }}</td><td class="ep-summary">{{ v.context }}</td><td><span v-if="v.book" class="ep-tag ep-tag--good">{{ v.book }}</span><button v-else class="btn btn--secondary btn--sm" @click="openVocabulary(v)">単語帳に追加</button></td></tr></tbody></table></div></section>
        </div>
      </template>
      <section v-if="kind === 'cloze' || (tab === 'questions' && reading?.stages[0] === 'COMPLETED')" class="ep-section">
        <div class="ep-bar"><template v-if="kind === 'cloze' && !dialog"><RouterLink v-for="(q,i) in record.questions" :key="q.id" class="btn btn--sm" :class="questionIndex === i ? 'btn--primary' : 'btn--secondary'" :to="`${base}/${record.id}/${q.id}`">Q{{ i + 1 }}</RouterLink></template><template v-else><button v-for="(q,i) in record.questions" :key="q.id" class="btn btn--sm" :class="questionIndex === i ? 'btn--primary' : 'btn--secondary'" @click="selectQuestion(i)">Q{{ i + 1 }}</button></template></div>
        <div v-if="question" class="ep-split">
          <section class="ep-card ep-section">
            <div class="ep-bar ep-bar--between"><h2>Q{{ questionIndex + 1 }} <span class="ep-muted">/ {{ record.questions.length }}</span></h2><span class="ep-tag">{{ judged(question) }}</span></div><p class="ep-question-text">{{ question.text }}</p><p v-if="translations" class="ep-note">{{ question.translation[language] }}</p><div class="ep-options"><div v-for="(option,i) in question.options" :key="i" class="ep-option" :class="{'is-correct':i === question.correct,'is-wrong':i === question.student && i !== question.correct}"><strong>{{ answerLabel(i) }}.</strong>{{ option }}<span v-if="i === question.correct" class="ep-tag ep-tag--good">正解</span><span v-if="i === question.student" class="ep-tag">元の答案</span></div></div>
            <div class="ep-bar ep-bar--between"><span class="ep-muted">学生答案：{{ answerLabel(question.student) }} / 正解：{{ answerLabel(question.correct) }}</span><button v-if="!editing" class="btn btn--secondary btn--sm" @click="editAnswers">ロック解除・答案修正</button></div><div v-if="editing" class="ep-section"><div class="ep-fields"><label class="ep-wide">学生答案<select v-model="correction.student" class="select"><option :value="null">未回答</option><option v-for="(_,i) in question.options" :key="i" :value="i">{{ answerLabel(i) }}</option></select></label><label class="ep-wide">正解<select v-model="correction.correct" class="select"><option :value="null">未確定</option><option v-for="(_,i) in question.options" :key="i" :value="i">{{ answerLabel(i) }}</option></select></label></div><div class="ep-bar"><button class="btn btn--primary" @click="saveAnswers">答案を保存</button><button class="btn btn--secondary" @click="editing = false">キャンセル</button></div></div>
          </section><aside class="ep-card ep-section"><h2>解説と根拠</h2><template v-if="analysisReady"><span v-if="question.knowledge" class="ep-tag">{{ question.knowledge }}</span><div class="ep-analysis">{{ question.explanation[language] || '解説は未登録です。教材の編集から入力してください。' }}</div><template v-if="question.student !== null && question.student !== question.correct"><h3>元の答案を振り返る</h3><p>{{ question.optionNotes[question.student]?.[language] || '選択肢と本文の根拠を照らし合わせてみましょう。' }}</p></template><details><summary>選択肢ごとの解説</summary><p v-for="(option,i) in question.options" :key="i" class="ep-note"><strong>{{ answerLabel(i) }}. {{ option }}</strong><br />{{ question.optionNotes[i]?.[language] || '未登録' }}</p></details><div v-if="kind === 'intensive'" class="ep-section"><h3>本文の根拠</h3><button v-for="id in question.evidence" :key="id" class="ep-link ep-analysis" @click="evidence(id)">{{ sentences.find(s => s.id === id)?.english }} → 本文で確認</button></div></template><template v-else><p class="ep-note">解説はまだ確認されていません。教材の編集から解説を確認してください。</p><RouterLink :to="`${base}/${record.id}/edit`" class="btn btn--secondary">教材を編集</RouterLink></template></aside>
        </div><p v-else class="ep-empty">設問がありません。教材の編集から追加してください。</p>
      </section>
    </template>
    <StudyDialog v-if="originals" title="原本画像" wide @close="originals = false"><div class="ep"><p v-if="!record?.images.length" class="ep-empty">原本画像はありません。</p><figure v-for="img in record?.images" :key="img.id"><img class="ep-original" :src="img.src" :alt="img.name" /><figcaption class="ep-note">{{ img.name }}</figcaption></figure></div></StudyDialog>
    <StudyDialog v-if="history" title="再挑戦履歴" wide @close="history = false"><div class="ep"><p v-if="!attempts.length" class="ep-empty">まだ再挑戦の記録がありません。</p><details v-for="a in attempts" :key="a.id" class="ep-question"><summary>{{ a.date }} {{ a.correctCount }} / {{ a.answers.length }} 正解</summary><div v-for="(answer,i) in a.answers" :key="answer.questionId"><p>Q{{ i + 1 }}. {{ answer.text }}</p><p :class="answer.isCorrect ? 'ep-success' : 'ep-error'">あなたの回答：{{ answerLabel(answer.answer) }}. {{ answer.options[answer.answer] }} / 正解：{{ answerLabel(answer.correct) }}. {{ answer.options[answer.correct] }}</p></div></details></div></StudyDialog>
    <StudyDialog v-if="vocabulary" :title="vocabulary.term" @close="vocabulary = undefined"><div class="ep"><div class="ep-bar"><span>{{ vocabulary.pronunciation }}</span><button class="btn btn--secondary btn--sm" @click="speak(vocabulary.term)">発音</button></div><p>{{ vocabulary.meaning[language] }}</p><p class="ep-question-text">{{ vocabulary.context }}</p><p class="ep-analysis">{{ vocabulary.usage[language] }}</p><div class="ep-fields"><label class="ep-wide">登録先<select v-model="bookKind" class="select"><option value="word">単語情報管理</option><option value="phrase">熟語情報管理</option></select></label><label class="ep-wide">レベル<select v-model="bookLevel" class="select"><option value="beginner">初級編</option><option value="intermediate">中級編</option></select></label><label class="ep-wide">書籍<input v-model="book" class="input" list="vocabulary-books" /><datalist id="vocabulary-books"><option v-for="b in books" :key="b" :value="b" /></datalist></label></div><p v-if="error" class="ep-error" role="alert">{{ error }}</p></div><template #footer><button class="btn btn--secondary" @click="vocabulary = undefined">閉じる</button><button class="btn btn--primary" :disabled="!book.trim()" @click="addVocabulary">単語帳に追加</button></template></StudyDialog>
    <PracticeDialog v-if="practice && record && (kind !== 'cloze' || question)" :kind="kind" :parent-id="record.id" :questions="kind === 'cloze' ? [question!] : record.questions" :reading="kind === 'intensive' ? reading : undefined" @close="practice = false" />
  </div>
</template>
