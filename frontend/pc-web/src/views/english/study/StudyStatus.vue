<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import StudyDialog from '@/features/english-study/StudyDialog.vue'
import StudyPager from '@/features/english-study/StudyPager.vue'
import { levels, testLabels, useEnglishStudyStore, type StudyKind, type StudyWord, type TestType, type StudyTest } from '@/features/english-study/store'
import TestRunner from './TestRunner.vue'

const props = defineProps<{ kind: StudyKind; words: StudyWord[]; tab: 'book' | 'word' }>()
const store = useEnglishStudyStore()
const typeLabels = computed(() => testLabels(props.kind))
const studied = (word: StudyWord) => Object.values(word.skills).some(s => s.attempts > 0)
function rate(words: StudyWord[], type?: TestType) {
  const skills = words.flatMap(w => type ? [w.skills[type]] : Object.entries(w.skills).filter(([key]) => key !== 'A').map(([, skill]) => skill))
  const attempts = skills.reduce((n, s) => n + s.attempts, 0)
  return attempts ? `${Math.round(skills.reduce((n, s) => n + s.correct, 0) / attempts * 100)}%` : '—'
}
const groups = computed(() => {
  const map = new Map<string, { key: string; book: string; level: StudyWord['level']; words: StudyWord[] }>()
  props.words.forEach(w => { const key = `${w.level}:${w.book}`; if (!map.has(key)) map.set(key, { key, book: w.book, level: w.level, words: [] }); map.get(key)!.words.push(w) })
  return [...map.values()]
})
const page = ref(1), size = ref(20)
watch([() => props.tab, () => props.words.map(w => w.id).join(',')], () => { page.value = 1 })
const visibleWords = computed(() => props.words.slice((page.value - 1) * size.value, page.value * size.value))
const visibleGroups = computed(() => groups.value.slice((page.value - 1) * size.value, page.value * size.value))
const selected = ref<StudyWord[]>([]), detail = ref<StudyWord | null>(null)
const type = ref<TestType>('B'), exclude = ref(true), error = ref('')
const running = ref<StudyTest | null>(null)
const types = computed(() => Object.entries(typeLabels.value).filter(([key]) => selected.value[0]?.level === 'intermediate' || !['E', 'F'].includes(key)))
function openCreate(words: StudyWord[]) { selected.value = words; type.value = 'B'; exclude.value = true; error.value = '' }
function create() {
  const words = selected.value.filter(w => (!exclude.value || !w.mastered) && (!['E', 'F'].includes(type.value) || w.example && w.translation && (type.value !== 'E' || w.example.toLowerCase().includes(w.english.toLowerCase()))))
  try { running.value = store.createTest({ kind: props.kind, level: selected.value[0].level, type: type.value, wordIds: words.map(w => w.id) }); selected.value = [] } catch (e) { error.value = (e as Error).message }
}
function retry() { try { if (running.value) running.value = store.retry(running.value.id) } catch (e) { error.value = (e as Error).message; running.value = null } }
</script>
<template>
  <div class="eng-summary"><section class="card"><span>総{{ kind === 'word' ? '単語' : '熟語' }}数</span><strong>{{ words.length }}<small>語</small></strong></section><section class="card"><span>学習した語</span><strong>{{ words.filter(studied).length }}<small>語</small></strong></section><section class="card"><span>習得済</span><strong>{{ words.filter(w => w.mastered).length }}<small>語</small></strong></section><section class="card"><span>正解率（B〜F）</span><strong>{{ rate(words) }}</strong></section></div>
  <section class="table-section">
    <header class="table-section__head"><div><h2 class="table-section__title">{{ kind === 'word' ? '単語' : '熟語' }}勉強状況一覧</h2><p class="eng-muted">{{ tab === 'book' ? '書籍ごとの習得状況から、復習テストを作成できます。' : 'A〜F の学習記録と習得状態を確認できます。' }}</p></div><span class="badge badge--neutral">{{ tab === 'book' ? '書籍別' : '単語別' }}</span></header>
    <p v-if="error && !selected.length" role="alert" class="eng-error">{{ error }}</p>
    <div class="table-wrap">
      <table v-if="tab === 'book'" class="data-table eng-table"><thead><tr><th>操作</th><th>書籍</th><th>レベル / 分類</th><th>語数</th><th>学習済</th><th>習得状況</th><th>正解率</th></tr></thead><tbody><tr v-for="group in visibleGroups" :key="group.key"><td><button class="btn btn--secondary btn--sm" @click="openCreate(group.words)"><AppIcon name="play" size="sm" />テスト作成</button></td><td class="cell-strong">{{ group.book }}</td><td>{{ levels[group.level] }}<small>{{ [...new Set(group.words.map(w => w.category))].join(' / ') }}</small></td><td>{{ group.words.length }}</td><td>{{ group.words.filter(studied).length }}</td><td><span>{{ group.words.filter(w => w.mastered).length }} / {{ group.words.length }} 語</span><progress class="eng-progress" :value="group.words.filter(w => w.mastered).length" :max="group.words.length" aria-label="習得率" /></td><td>{{ rate(group.words) }}</td></tr><tr v-if="!groups.length"><td colspan="7" class="eng-empty">条件に一致する学習記録がありません。</td></tr></tbody></table>
      <table v-else class="data-table eng-table"><thead><tr><th>操作</th><th>{{ kind === 'word' ? '単語' : '熟語' }} / 意味</th><th>書籍 / レベル</th><th>習得状態</th><th v-for="(text, key) in typeLabels" :key="key" :title="text">{{ text }}</th></tr></thead><tbody><tr v-for="word in visibleWords" :key="word.id"><td><div class="eng-actions eng-row-actions"><button class="btn btn--icon" :aria-label="`${word.english}の学習記録`" @click="detail = word"><AppIcon name="eye" class="icon--view" /></button><button class="btn btn--icon" :aria-label="`${word.english}を復習`" @click="openCreate([word])"><AppIcon name="play" /></button></div></td><td><strong lang="en">{{ word.english }}</strong><small>{{ word.japanese }}</small></td><td>{{ word.book }}<small>{{ levels[word.level] }} · {{ word.category }}</small></td><td><button class="btn btn--secondary btn--sm" :aria-pressed="word.mastered" @click="word.mastered = !word.mastered">{{ word.mastered ? '✓ 習得済' : '未習得' }}</button></td><td v-for="(_, key) in typeLabels" :key="key">{{ key === 'A' ? `${word.skills.A.attempts}回` : rate([word], key) }}<small v-if="key !== 'A'">{{ word.skills[key].correct }} / {{ word.skills[key].attempts }}</small></td></tr><tr v-if="!words.length"><td colspan="10" class="eng-empty">条件に一致する語がありません。</td></tr></tbody></table>
    </div>
    <StudyPager v-model:page="page" v-model:size="size" :total="tab === 'book' ? groups.length : words.length" />
  </section>
  <StudyDialog v-if="selected.length" title="復習テストを作成" @close="selected = []"><div class="eng-edit-grid"><p class="eng-span-2">{{ selected[0].book }} · {{ selected.length }} 語</p><label>テスト種別<select v-model="type" class="select"><option v-for="[key, text] in types" :key="key" :value="key">{{ text }}</option></select></label><label class="eng-checkbox"><input v-model="exclude" type="checkbox" />習得済を除外</label><p class="eng-muted eng-span-2">E・F は例文が登録されている語を出題します。</p><p v-if="error" class="eng-error eng-span-2" role="alert">{{ error }}</p></div><template #footer><button class="btn btn--secondary" @click="selected = []">キャンセル</button><button class="btn btn--primary" @click="create">作成して開始</button></template></StudyDialog>
  <StudyDialog v-if="detail" :title="`${detail.english} の学習記録`" @close="detail = null"><p>{{ detail.japanese }} / {{ detail.chinese }}</p><table class="data-table"><thead><tr><th>テスト種別</th><th>正解 / 回数</th><th>正解率</th></tr></thead><tbody><tr v-for="(text, key) in typeLabels" :key="key"><td>{{ text }}</td><td>{{ detail.skills[key].correct }} / {{ detail.skills[key].attempts }}</td><td>{{ rate([detail], key) }}</td></tr></tbody></table><template #footer><button class="btn btn--secondary" @click="detail = null">閉じる</button></template></StudyDialog>
  <TestRunner v-if="running" :key="running.id" :test="running" @close="running = null" @retry="retry" />
</template>
