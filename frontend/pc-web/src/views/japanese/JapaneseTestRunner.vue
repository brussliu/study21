<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError } from '@study21/web-shared'
import { answerJpnTest, startJpnTest, fetchJpnTest, fetchJpnWord, TEST_TYPE_LABELS, type JpnTestDetail, type JpnWordDetailResult, type JpnTestQuestionView } from '@/api/japanese'
import DemoWordStudyView from '@/views/japanese/demo/DemoWordStudyView.vue'
import { toStudyWord } from '@/features/japanese-word/wordMapper'
import type { DemoWord } from '@/features/japanese-demo/types'
import '@/features/japanese/test.css'

const route = useRoute()
const detail = ref<JpnTestDetail | null>(null)
const index = ref(0)
const busy = ref(false)
const error = ref('')
const summary = ref(false)
const selected = ref<number | undefined>()
const heading = ref('')
const reading = ref('')
const feedback = ref('')
const studyWord = ref<DemoWord | null>(null)
const studyLoading = ref(false)
const test = computed(() => detail.value?.test)
const current = computed(() => detail.value?.questions[index.value])
const snapshot = computed(() => current.value?.snapshot ?? {})
const answered = computed(() => current.value?.question.state === 'ANSWERED' || current.value?.question.state === 'SKIPPED')
const type = computed(() => test.value?.testType)
const code = computed(() => String(snapshot.value.questionType ?? current.value?.question.questionType ?? ''))
const word = computed(() => String(snapshot.value.word ?? snapshot.value.heading ?? current.value?.question.word ?? ''))
const kana = computed(() => String(snapshot.value.reading ?? current.value?.question.reading ?? ''))
const meaning = computed(() => String(snapshot.value.meaning ?? snapshot.value.chineseMeaning ?? snapshot.value.sourceChinese ?? ''))
const prompt = computed(() => String(snapshot.value.questionText ?? current.value?.question.questionText ?? ''))
const canSubmit = computed(() => !busy.value && !answered.value && !studyLoading.value && ((type.value === 'A' && studyWord.value !== null) || (type.value === 'B' ? !!heading.value.trim() && !!reading.value.trim() : selected.value !== undefined)))
let startedAt = 0
let activeMs = 0
let viewSequence = 0
function pause() { if (startedAt) { activeMs += Date.now() - startedAt; startedAt = 0 } }
function resume() { if (!startedAt && !document.hidden && !answered.value && !summary.value && !busy.value) startedAt = Date.now() }
function visibility() { if (document.hidden) pause(); else resume() }
function message(e: unknown) { return e instanceof ApiError || e instanceof Error ? e.message : '処理に失敗しました。' }
function savedAnswer(item: JpnTestQuestionView) {
  const last = item.history?.at(-1)
  if (!last) return item.question.judgment ? '（移行した記録には回答内容がありません）' : '未回答'
  return last.answer || item.choices.find(c => (c.choiceKey ?? c.choiceId ?? c.orderNo) === last.choiceId)?.value || '—'
}
async function show(target: number) {
  if (busy.value || !detail.value || target < 0 || target >= detail.value.questions.length) return
  pause(); activeMs = 0; index.value = target; summary.value = false; feedback.value = ''; error.value = ''
  const seq = ++viewSequence
  const item = current.value!
  const last = item.history?.at(-1)
  heading.value = last?.answer ?? ''; reading.value = last?.reading ?? ''; selected.value = last?.choiceId
  studyWord.value = null
  if (type.value === 'A') {
    studyLoading.value = true
    try {
      const data = (item.snapshot?.wordDetail as JpnWordDetailResult | undefined) ?? (await fetchJpnWord(item.question.wordId)).data
      if (seq === viewSequence) studyWord.value = toStudyWord({ wordId: data.word.wordId, word: data.word.word, reading: data.word.reading, jlptLevel: data.word.jlptLevel, partOfSpeech: data.word.partOfSpeech, collections: data.collections, detail: data.detail, updatedAt: '' })
    } catch (e) { if (seq === viewSequence) error.value = message(e) }
    finally { if (seq === viewSequence) studyLoading.value = false }
  }
  if (seq === viewSequence) resume()
}
async function load() {
  const id = Number(route.query.testId)
  if (!Number.isSafeInteger(id) || id <= 0) { error.value = 'テストが指定されていません。'; return }
  busy.value = true; error.value = ''
  try {
    detail.value = (await startJpnTest(id)).data
    const pending = detail.value.questions.findIndex(q => q.question.state === 'PENDING')
    summary.value = test.value?.state === 'COMPLETED' || pending < 0
    index.value = Math.max(0, pending)
  } catch (e) { error.value = message(e) }
  finally { busy.value = false }
  if (!summary.value && detail.value) await show(index.value)
}
async function submit(choiceId?: number) {
  if (choiceId !== undefined) selected.value = choiceId
  if (!canSubmit.value || !current.value || !test.value) return
  pause(); const elapsedMs = Math.min(activeMs, 1800000)
  busy.value = true; error.value = ''
  try {
    const { data } = await answerJpnTest(test.value.testId, { orderNo: current.value.question.orderNo, elapsedMs,
      ...(type.value === 'A' ? { answerText: '学習完了' } : type.value === 'B' ? { answerText: heading.value.trim(), readingText: reading.value.trim() } : { choiceId: selected.value }) })
    // 保存が成功した時点で二重回答を防ぐ。読み直しだけが失敗しても送信済み回答は残す。
    const item = current.value
    item.question.answerCount++
    const done = data.answered ?? (type.value !== 'B' || data.correct || item.question.answerCount >= 3)
    item.question.state = done ? 'ANSWERED' : 'PENDING'
    item.question.judgment = done ? data.judgment : null
    item.question.correctValue = data.correctValue
    item.question.explanation = data.explanation
    item.history = [...(item.history ?? []), { answer: type.value === 'A' ? '学習完了' : heading.value, reading: reading.value, choiceId: selected.value, correct: data.correct, judgment: data.judgment, at: new Date().toISOString() }]
    detail.value!.test = data.test
    try { detail.value = (await fetchJpnTest(data.test.testId)).data }
    catch { error.value = '回答は保存しました。最新情報の再取得に失敗しました。' }
    activeMs = 0
    feedback.value = `${data.judgment === 'CONFIRMED' ? '学習を記録しました。' : data.correct ? '正解です。' : data.judgment === 'MIXED' ? '表記・読みの一方が不正解です。' : '不正解です。'}${answered.value ? `\n${data.correctValue ?? ''}\n${data.explanation ?? ''}` : `\n修正してもう一度回答してください（残り${3 - (current.value?.question.answerCount ?? 0)}回）。`}`
  } catch (e) { error.value = message(e) }
  finally { busy.value = false; resume() }
}
function next() {
  if (index.value + 1 < (detail.value?.questions.length ?? 0)) void show(index.value + 1)
  else { pause(); summary.value = true }
}
function speak() {
  if (code.value === 'C1_READING' && !answered.value) return
  const text = type.value === 'D' ? String(snapshot.value.example ?? prompt.value) : kana.value || word.value
  if (!window.speechSynthesis) { error.value = 'このブラウザは音声再生に対応していません。'; return }
  window.speechSynthesis.cancel(); const utterance = new SpeechSynthesisUtterance(text); utterance.lang = 'ja-JP'; utterance.rate = 0.88; window.speechSynthesis.speak(utterance)
}
function close() { pause(); window.close() }
function keydown(e: KeyboardEvent) {
  if (e.isComposing || e.repeat || busy.value || summary.value || e.ctrlKey || e.altKey || e.metaKey) return
  if (type.value === 'A' && e.target instanceof HTMLElement && ['INPUT', 'TEXTAREA', 'SELECT', 'BUTTON'].includes(e.target.tagName)) return
  if (e.key === 'Enter') { e.preventDefault(); if (answered.value) next(); else void submit(); return }
  if (e.target instanceof HTMLElement && ['INPUT', 'TEXTAREA', 'SELECT'].includes(e.target.tagName)) return
  if (/^[1-4]$/.test(e.key) && !answered.value && type.value !== 'A' && type.value !== 'B') {
    const choice = current.value?.choices[Number(e.key) - 1]; if (choice) { e.preventDefault(); void submit(choice.choiceKey ?? choice.choiceId ?? choice.orderNo) }
  }
  if (e.key === 'ArrowLeft') { e.preventDefault(); void show(index.value - 1) }
  if (e.key === 'ArrowRight' && answered.value) { e.preventDefault(); next() }
  if (e.key === ' ') { e.preventDefault(); speak() }
}
onMounted(() => { void load(); document.addEventListener('visibilitychange', visibility); window.addEventListener('blur', pause); window.addEventListener('focus', resume); window.addEventListener('keydown', keydown) })
onUnmounted(() => { pause(); viewSequence++; window.speechSynthesis?.cancel(); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('blur', pause); window.removeEventListener('focus', resume); window.removeEventListener('keydown', keydown) })
</script>

<template>
  <main class="jpt-runner" data-test-runner>
    <header class="jpt-toolbar"><div><h2>{{ test ? TEST_TYPE_LABELS[test.testType] : '単語テスト' }}</h2><p v-if="test">{{ test.book || 'すべての書籍' }} · {{ test.categoryFrom || 'すべて' }} ～ {{ test.categoryTo || 'すべて' }} · {{ test.testNo }}</p></div><button class="btn btn--secondary" :disabled="busy" @click="close">{{ test?.state === 'COMPLETED' ? '閉じる' : '中断して閉じる' }}</button></header>
    <p v-if="error" class="alert alert--danger" role="alert">{{ error }} <button v-if="!detail" class="btn btn--secondary" @click="load">再試行</button></p>
    <p v-if="busy && !detail" role="status">テストを読み込んでいます…</p>
    <template v-if="test && detail">
      <progress class="jpt-runner__progress" :value="test.doneCount" :max="Math.max(1, test.questionCount)" aria-label="テスト進捗" />
      <section v-if="summary" class="card jpt-question" data-test-result>
        <h1>{{ test.state === 'COMPLETED' ? 'テスト結果' : '進捗と回答履歴' }}</h1>
        <div class="jpt-summary"><span>完了<strong>{{ test.doneCount }} / {{ test.questionCount }}</strong></span><span>正解率<strong>{{ test.scorePercent }}%</strong></span><span>正解 / 不正解<strong>{{ test.correctCount }} / {{ test.wrongCount }}</strong></span><span>勉強時間<strong>{{ Math.floor(test.activeMs / 60000) }}分</strong></span></div>
        <div class="jpt-table-scroll"><table class="data-table jpt-table"><thead><tr><th>順番</th><th>単語</th><th>回答</th><th>判定</th><th>回答回数</th><th>操作</th></tr></thead><tbody><tr v-for="(item, i) in detail.questions" :key="item.question.entryId"><td>{{ item.question.orderNo }}</td><td>{{ item.snapshot?.word || item.question.word }}</td><td>{{ savedAnswer(item) }} {{ item.history?.at(-1)?.reading }}</td><td>{{ item.question.judgment === 'CORRECT' ? '正解' : item.question.judgment === 'CONFIRMED' ? '学習済み' : item.question.judgment === 'MIXED' ? '一部正解' : item.question.judgment === 'INCORRECT' ? '不正解' : '未回答' }}</td><td>{{ item.question.answerCount }}</td><td><button class="btn btn--secondary btn--sm" @click="show(i)">確認</button></td></tr></tbody></table></div>
      </section>
      <template v-else-if="current">
        <div class="jpt-toolbar"><span>{{ index + 1 }} / {{ detail.questions.length }} 語 · 完了 {{ test.doneCount }} 語</span><button class="btn btn--secondary btn--sm" @click="pause(); summary = true">回答履歴</button></div>
        <section v-if="type === 'A'" data-test-study><p v-if="studyLoading">単語の詳細を読み込んでいます…</p><DemoWordStudyView v-else-if="studyWord" :key="current.question.entryId" :word="studyWord" :live-playback="true" /></section>
        <section v-else class="card jpt-question">
          <div class="jpt-prompt">
            <template v-if="type === 'B'"><p>中国語の意味から、表記と読みを入力してください。</p><h1 lang="zh-CN">{{ meaning || prompt }}</h1></template>
            <template v-else-if="code === 'C1_READING'"><p>漢字を見て、正しい読みを選んでください。</p><h1>{{ word }}</h1></template>
            <template v-else-if="code === 'C2_KANJI'"><p>音声を聞いて、正しい漢字を選んでください。</p></template>
            <template v-else-if="type === 'D'"><p>対象語「{{ word }}」の文脈に合う中国語の意味を選んでください。</p><h1>{{ snapshot.example || prompt }}</h1></template>
            <template v-else><p>読みと中国語の意味から、正しい表記を選んでください。</p><h1>{{ kana }}</h1><p lang="zh-CN">{{ meaning || prompt }}</p></template>
            <button v-if="code !== 'C1_READING' || answered" type="button" class="btn btn--secondary" @click="speak">発音を聞く（Space）</button>
          </div>
          <form v-if="type === 'B'" class="jpt-inputs" @submit.prevent="submit()">
            <label>B1. 日本語表記<input v-model="heading" class="input" data-test-heading lang="ja" autocomplete="off" :disabled="busy || answered" placeholder="日本語を入力"></label>
            <label>B2. 読み（かな）<input v-model="reading" class="input" data-test-reading lang="ja" autocomplete="off" :disabled="busy || answered" placeholder="かなを入力"></label>
          </form>
          <div v-else class="jpt-choices"><button v-for="(choice, i) in current.choices" :key="choice.orderNo" class="jpt-choice" :class="{ 'is-selected': selected === (choice.choiceKey ?? choice.choiceId ?? choice.orderNo), 'is-correct': answered && choice.correct, 'is-wrong': answered && !choice.correct && selected === (choice.choiceKey ?? choice.choiceId ?? choice.orderNo) }" :disabled="busy || answered" @click="submit(choice.choiceKey ?? choice.choiceId ?? choice.orderNo)"><b>{{ i + 1 }}</b><span :lang="type === 'D' ? 'zh-CN' : 'ja'">{{ code === 'C1_READING' ? choice.reading || choice.value : choice.value }}</span></button></div>
          <p v-if="type !== 'B' && !current.choices.length" class="alert alert--danger">この出題の選択肢がありません。テストを作成し直してください。</p>
        </section>
        <div v-if="feedback || answered" class="jpt-feedback" role="status">{{ feedback || `${current.question.judgment === 'CONFIRMED' ? '学習済み' : current.question.judgment === 'CORRECT' ? '正解' : '不正解'}\n${snapshot.correctValue || current.question.correctValue || ''}\n${snapshot.explanation || current.question.explanation || ''}` }}</div>
        <footer class="jpt-toolbar"><button class="btn btn--secondary" :disabled="busy || index === 0" @click="show(index - 1)">前の語</button><div class="jpt-actions"><button v-if="!answered" class="btn btn--primary" data-test-submit :disabled="!canSubmit" @click="submit()">{{ busy ? '保存中…' : type === 'A' ? '学習完了（Enter）' : '判定（Enter）' }}</button><button v-else class="btn btn--primary" data-test-next :disabled="busy" @click="next">{{ index + 1 === detail.questions.length ? '結果を見る' : '次の語（Enter）' }}</button></div></footer>
      </template>
    </template>
  </main>
</template>
