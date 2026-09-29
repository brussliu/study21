<script setup lang="ts">
import { computed, ref } from 'vue'
import StudyDialog from '@/features/english-study/StudyDialog.vue'
import { usePracticeDemoStore } from './store'
import { answerLabel, copy, type Question, type IntensiveRecord, type PracticeAttempt } from './types'
const props = defineProps<{ kind: 'cloze' | 'intensive'; parentId: string; questions: Question[]; reading?: IntensiveRecord }>()
const emit = defineEmits<{ close: [] }>()
const store = usePracticeDemoStore()
const questions = copy(props.questions)
const answers = ref<Record<string, number>>({}), index = ref(0), error = ref(''), original = ref(false), confirmClose = ref(false)
const result = ref<PracticeAttempt>()
const q = computed(() => questions[index.value])
const answered = computed(() => Object.keys(answers.value).length)
function submit() { try { result.value = store.recordAttempt(props.kind, props.parentId, questions, answers.value); error.value = '' } catch (e) { error.value = (e as Error).message } }
function close() { if (!result.value && answered.value) confirmClose.value = true; else emit('close') }
</script>
<template>
  <StudyDialog title="再挑戦" wide @close="close">
    <div class="ep">
      <div class="ep-bar ep-bar--between"><strong v-if="!result">回答済み {{ answered }} / {{ questions.length }}</strong><strong v-else class="ep-count">{{ result.correctCount }} / {{ questions.length }} 正解</strong><span class="ep-muted">元の答案は変更されません</span></div>
      <p v-if="result" class="ep-success" role="status">学習履歴に記録しました。設問番号を選ぶと、答案と解説を確認できます。</p>
      <div class="ep-bar"><button v-for="(item, i) in questions" :key="item.id" type="button" class="btn btn--sm" :class="index === i ? 'btn--primary' : 'btn--secondary'" :aria-label="`設問${i + 1}${answers[item.id] !== undefined ? ' 回答済み' : ' 未回答'}`" @click="index = i">{{ i + 1 }} {{ result ? (result.answers[i].isCorrect ? '○' : '×') : answers[item.id] !== undefined ? '✓' : '' }}</button></div>
      <div :class="reading ? 'ep-split' : 'ep-section'">
        <section v-if="reading" class="ep-card"><div class="ep-bar ep-bar--between"><h3>{{ reading.title }}</h3><button class="btn btn--secondary btn--sm" @click="original = !original">{{ original ? '本文を表示' : '原本を表示' }}</button></div><template v-if="original"><img v-for="img in reading.images" :key="img.id" class="ep-original" :src="img.src" :alt="img.name" /></template><div v-else class="ep-passage"><p v-for="p in reading.paragraphs" :key="p.id">{{ p.sentences.map(s => s.english).join(' ') }}</p></div></section>
        <section v-if="q" class="ep-section"><p class="ep-question-text">Q{{ index + 1 }}. {{ q.text }}</p><div class="ep-options"><label v-for="(option, i) in q.options" :key="i" class="ep-option" :class="{ 'is-selected': answers[q.id] === i, 'is-correct': !!result && q.correct === i, 'is-wrong': !!result && answers[q.id] === i && q.correct !== i }"><input v-model="answers[q.id]" type="radio" :name="`retry-${q.id}`" :value="i" :disabled="!!result" />{{ answerLabel(i) }}. {{ option }} <span v-if="result && q.correct === i" class="ep-tag ep-tag--good">正解</span></label></div><div v-if="result" class="ep-analysis">{{ q.explanation.ja || '解説は未登録です。' }}</div></section>
      </div><p v-if="error" class="ep-error" role="alert">{{ error }}</p>
      <div v-if="confirmClose" class="ep-error">回答内容を破棄して閉じますか？<div class="ep-bar"><button class="btn btn--secondary" @click="confirmClose = false">続ける</button><button class="btn btn--secondary" @click="emit('close')">破棄して閉じる</button></div></div>
    </div><template #footer><button class="btn btn--secondary" :disabled="index === 0" @click="index--">前の問題</button><button class="btn btn--secondary" :disabled="index === questions.length - 1" @click="index++">次の問題</button><button v-if="!result" class="btn btn--primary" @click="submit">採点して記録</button><button v-else class="btn btn--primary" @click="emit('close')">結果を閉じる</button></template>
  </StudyDialog>
</template>
