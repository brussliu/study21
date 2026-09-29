<script setup lang="ts">
import { newQuestion } from './mock'
import { answerLabel, type Question } from './types'
const questions = defineModel<Question[]>({ required: true })
defineProps<{ readonly?: boolean }>()
const emit = defineEmits<{ change: [] }>()
function removeOption(q: Question, index: number) {
  q.options.splice(index, 1); q.optionNotes.splice(index, 1)
  for (const key of ['correct', 'student'] as const) if (q[key] !== null) q[key] = q[key] === index ? null : q[key]! > index ? q[key]! - 1 : q[key]
  emit('change')
}
</script>
<template>
  <section class="ep-section">
    <div class="ep-bar ep-bar--between"><h3>設問・OCR結果の確認 <span class="ep-muted">{{ questions.length }} 問</span></h3><button type="button" class="btn btn--secondary" :disabled="readonly" @click="questions.push(newQuestion()); emit('change')">設問を追加</button></div>
    <p v-if="!questions.length" class="ep-empty">OCR結果を取り込むか、設問を追加してください。</p>
    <details v-for="(q, index) in questions" :key="q.id" class="ep-question" :open="index === 0">
      <summary>Q{{ index + 1 }} {{ q.text || '未入力の設問' }}</summary>
      <fieldset class="ep-section" :disabled="readonly" style="border:0;padding:0;margin:0">
        <label>問題文 <textarea v-model="q.text" class="input" @input="emit('change')" /></label>
        <div v-for="(_, i) in q.options" :key="i" class="ep-bar"><label style="flex:1">選択肢 {{ answerLabel(i) }}<input v-model="q.options[i]" class="input" @input="emit('change')" /></label><button type="button" class="ep-icon ep-icon--danger" :disabled="q.options.length <= 2" :aria-label="`選択肢${answerLabel(i)}を削除`" @click="removeOption(q, i)">×</button></div>
        <div class="ep-bar"><button type="button" class="btn btn--secondary btn--sm" :disabled="q.options.length >= 6" @click="q.options.push(''); q.optionNotes.push({ja:'',zh:''}); emit('change')">選択肢を追加</button></div>
        <div class="ep-fields"><label>学生の答案<select v-model="q.student" class="select" @change="emit('change')"><option :value="null">未回答</option><option v-for="(_, i) in q.options" :key="i" :value="i">{{ answerLabel(i) }}</option></select></label><label>正解<select v-model="q.correct" class="select" @change="emit('change')"><option :value="null">未確定</option><option v-for="(_, i) in q.options" :key="i" :value="i">{{ answerLabel(i) }}</option></select></label><label class="ep-wide">知識点<input v-model="q.knowledge" class="input" /></label></div>
        <label>解説（日本語）<textarea v-model="q.explanation.ja" class="input" /></label><label>解説（中国語）<textarea v-model="q.explanation.zh" class="input" /></label>
        <div class="ep-bar"><button type="button" class="btn btn--secondary" @click="questions.splice(index, 1); emit('change')">この設問を削除</button></div>
      </fieldset>
    </details>
  </section>
</template>
