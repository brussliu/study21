<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import StudyDialog from '@/features/english-study/StudyDialog.vue'
import { expectedAnswer, score, testLabels, useEnglishStudyStore, type StudyTest } from '@/features/english-study/store'

const props = defineProps<{ test: StudyTest }>()
const emit = defineEmits<{ close: []; retry: [] }>()
const store = useEnglishStudyStore()
const typeLabels = computed(() => testLabels(props.test.kind))
const index = ref(props.test.answers.length)
const input = ref(''), feedback = ref(false), audioError = ref('')
const word = computed(() => props.test.questions[index.value])
const finished = computed(() => index.value >= props.test.questions.length)
const answer = computed(() => props.test.answers[index.value])
const choices = computed(() => {
  if (!word.value) return []
  const options = [...new Set([expectedAnswer(word.value, props.test.type), ...store.select(props.test.kind, props.test.level).filter(w => w.id !== word.value.id).slice(0, 3).map(w => expectedAnswer(w, props.test.type))])]
  // 正解位置は設問ごとに変える。
  const offset = index.value % options.length
  return [...options.slice(offset), ...options.slice(0, offset)]
})
let timer: ReturnType<typeof setInterval> | undefined
function submit(known = true) { if (feedback.value) return; store.answer(props.test.id, input.value, known); feedback.value = true }
function next() { index.value++; input.value = ''; feedback.value = false; window.speechSynthesis?.cancel() }
function speak() {
  if (!('speechSynthesis' in window)) { audioError.value = '音声再生に対応したブラウザでお試しください。'; return }
  window.speechSynthesis.cancel()
  const speech = new SpeechSynthesisUtterance(word.value.english); speech.lang = 'en-US'; speech.rate = 0.85
  speech.onerror = () => { audioError.value = '音声を再生できません。ブラウザの音声設定を確認してください。' }
  window.speechSynthesis.speak(speech)
}
onMounted(() => { store.start(props.test.id); timer = setInterval(() => { if (!document.hidden) store.tick(props.test.id) }, 1000) })
onUnmounted(() => { clearInterval(timer); window.speechSynthesis?.cancel() })
</script>
<template>
  <StudyDialog :title="`${test.kind === 'word' ? '単語' : '熟語'}テスト · ${typeLabels[test.type]}`" wide @close="emit('close')">
    <div class="eng-toolbar"><span class="eng-muted">{{ test.book }} · {{ test.id }}</span><span class="badge badge--info">{{ Math.min(index + 1, test.questions.length) }} / {{ test.questions.length }}</span></div>
    <progress class="eng-progress" :value="test.answers.length" :max="test.questions.length" aria-label="テスト進捗" />
    <section v-if="!finished && word" class="eng-question">
      <p class="eng-eyebrow">{{ test.type === 'A' ? 'LEARN & REMEMBER' : 'QUESTION ' + (index + 1) }}</p>
      <template v-if="test.type === 'A'"><h2 lang="en">{{ word.english }}</h2><p class="eng-muted">{{ word.pronunciation }}</p><h3>{{ word.japanese }}</h3><p class="eng-muted">{{ word.chinese }}</p><div class="eng-example"><p>{{ word.example }}</p><p class="eng-muted">{{ word.translation }}</p></div><button class="btn btn--secondary" @click="speak">発音を聞く</button></template>
      <template v-else-if="test.type === 'C'"><h2>音声を聞いて、英語を入力</h2><button class="btn btn--primary" @click="speak">▶ 音声を再生</button></template>
      <template v-else-if="test.type === 'E'"><h2>文脈に合う日本語の意味を選択</h2><p class="eng-context" lang="en">{{ word.example }}</p><p class="eng-muted">{{ test.kind === 'phrase' ? '表現' : '単語' }}：{{ word.english }}</p></template>
      <template v-else-if="test.type === 'F'"><h2>日本語に合う例文を入力</h2><p class="eng-context">{{ word.translation }}</p><p class="eng-muted">ヒント：{{ word.english }}</p></template>
      <template v-else><h2>{{ test.type === 'D' ? word.english : word.japanese }}</h2><p class="eng-muted">{{ test.type === 'D' ? '正しい意味を選んでください。' : word.chinese + ' · 英語で答えてください。' }}</p></template>
      <p v-if="audioError" role="alert" class="eng-error">{{ audioError }}</p>
      <form v-if="test.type !== 'A'" @submit.prevent="submit()">
        <div v-if="test.type === 'D' || test.type === 'E'" class="eng-choices"><button v-for="choice in choices" :key="choice" type="button" class="btn btn--secondary" :class="{ 'is-selected': input === choice }" :aria-pressed="input === choice" :disabled="feedback" @click="input = choice">{{ choice }}</button></div>
        <label v-else class="eng-answer-label">回答<input v-model="input" class="input eng-answer" autocomplete="off" spellcheck="false" :disabled="feedback" :placeholder="test.type === 'F' ? '英文を入力してください' : '英語を入力してください'" /></label>
        <button v-if="!feedback" class="btn btn--primary" :disabled="!input.trim()">回答する</button>
      </form>
      <div v-else-if="!feedback" class="eng-actions eng-center"><button class="btn btn--secondary" @click="submit(false)">もう一度復習</button><button class="btn btn--primary" @click="submit(true)">覚えた</button></div>
      <div v-if="feedback" class="eng-feedback" :class="{ 'eng-feedback--wrong': !answer?.correct }" role="status"><strong>{{ answer?.correct ? (test.type === 'A' ? '学習を記録しました' : '正解です！') : '復習リストに追加しました' }}</strong><p v-if="test.type !== 'A'">正解：{{ expectedAnswer(word, test.type) }}</p><button class="btn btn--primary" @click="next">{{ index + 1 === test.questions.length ? '結果を見る' : '次へ' }}</button></div>
    </section>
    <section v-else class="eng-result"><p class="eng-eyebrow">{{ test.type === 'A' ? 'STUDY COMPLETE' : 'TEST COMPLETE' }}</p><h2>{{ test.type === 'A' ? '学習完了' : 'テスト結果' }}</h2><div class="eng-result-score">{{ score(test) }}<small>%</small></div><p>{{ test.type === 'A' ? '覚えた数' : '正解数' }} {{ test.answers.filter(a => a.correct).length }} / {{ test.questions.length }} · 学習時間 {{ Math.floor(test.activeSeconds / 60) }}分{{ test.activeSeconds % 60 }}秒</p><div class="table-wrap"><table class="data-table"><thead><tr><th>{{ test.kind === 'word' ? '単語' : '熟語' }}</th><th>回答</th><th>正解</th><th>判定</th></tr></thead><tbody><tr v-for="(q, i) in test.questions" :key="q.id"><td>{{ q.english }}</td><td>{{ test.answers[i]?.value || '—' }}</td><td>{{ expectedAnswer(q, test.type) }}</td><td>{{ test.answers[i]?.correct ? '○' : '復習' }}</td></tr></tbody></table></div></section>
    <template #footer><button class="btn btn--secondary" @click="emit('close')">{{ test.state === 'COMPLETED' ? '閉じる' : '中断して閉じる' }}</button><button v-if="finished && test.answers.some(a => !a.correct)" class="btn btn--primary" @click="emit('retry')">誤り分で再テスト</button></template>
  </StudyDialog>
</template>
