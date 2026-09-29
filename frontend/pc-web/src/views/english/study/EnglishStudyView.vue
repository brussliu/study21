<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { useTabKeys } from '@/composables/useTabKeys'
import { levels, states, testLabels, useEnglishStudyStore, type StudyKind, type StudyLevel } from '@/features/english-study/store'
import WordManagement from './WordManagement.vue'
import TestManagement from './TestManagement.vue'
import StudyStatus from './StudyStatus.vue'
import '@/features/english-study/english-study.css'

const props = defineProps<{ kind: StudyKind; mode: 'test' | 'manage' | 'status' }>()
const store = useEnglishStudyStore()
const typeLabels = computed(() => testLabels(props.kind))
const level = ref<StudyLevel>('beginner')
const statusTab = ref<'book' | 'word'>('book')
const emptyFilters = () => ({ book: '', from: '', to: '', keyword: '', grade: '', state: '', type: '', mastery: '', favorite: false, detail: '' })
const filters = reactive(emptyFilters())
const applied = ref(emptyFilters())
const error = ref('')
const source = computed(() => store.select(props.kind, props.mode === 'status' ? undefined : level.value))
const books = computed(() => [...new Set(source.value.map(w => w.book))])
const grades = computed(() => [...new Set(source.value.map(w => w.grade))])
const categories = computed(() => [...new Set(source.value.filter(w => !filters.book || w.book === filters.book).map(w => w.category))].sort())
const words = computed(() => source.value.filter(w => {
  const f = applied.value
  return (!f.book || w.book === f.book) && (!f.from || w.category >= f.from) && (!f.to || w.category <= f.to)
    && (!f.grade || w.grade === f.grade) && (!f.keyword || `${w.english} ${w.japanese} ${w.chinese}`.toLowerCase().includes(f.keyword.toLowerCase()))
    && (!f.mastery || w.mastered === (f.mastery === 'yes')) && (!f.favorite || w.favorite)
    && (!f.detail || w.detailReady === (f.detail === 'yes'))
}))
const tests = computed(() => store.tests.filter(t => {
  const f = applied.value
  return t.kind === props.kind && t.level === level.value && (!f.state || t.state === f.state) && (!f.type || t.type === f.type)
    && t.questions.some(w => (!f.book || w.book === f.book) && (!f.grade || w.grade === f.grade) && (!f.from || w.category >= f.from) && (!f.to || w.category <= f.to))
}))
function search() {
  if (filters.from && filters.to && filters.from > filters.to) { error.value = '分類 From は分類 To 以前を指定してください。'; return }
  error.value = ''; applied.value = { ...filters, keyword: filters.keyword.trim() }
}
function reset() { Object.assign(filters, emptyFilters()); search() }
/** タブのキーボード操作（←→・Home・End）。作法はリポジトリ共通の `useTabKeys` */
const tabKey = useTabKeys()
watch([level, () => props.kind, () => props.mode], reset)
watch(() => filters.book, () => { filters.from = ''; filters.to = '' })
</script>

<template>
  <div class="eng-study">
    <div v-if="mode !== 'status'" class="tabs" role="tablist" aria-label="学習レベル" @keydown="tabKey">
      <button v-for="(text, key) in levels" :id="`eng-tab-${key}`" :key="key" class="tabs__tab" :class="{ 'is-active': level === key }" role="tab" :aria-selected="level === key" :tabindex="level === key ? 0 : -1" aria-controls="eng-content" :data-tab="key" @click="level = key">{{ text }}</button>
    </div>
    <div v-else class="tabs" role="tablist" aria-label="集計方法" @keydown="tabKey">
      <button v-for="tab in [{ id: 'book', label: '書籍別' }, { id: 'word', label: '単語別' }] as const" :id="`eng-tab-${tab.id}`" :key="tab.id" class="tabs__tab" :class="{ 'is-active': statusTab === tab.id }" role="tab" :aria-selected="statusTab === tab.id" :tabindex="statusTab === tab.id ? 0 : -1" aria-controls="eng-content" :data-tab="tab.id" @click="statusTab = tab.id">{{ tab.label }}</button>
    </div>
    <section id="eng-content" role="tabpanel" :aria-labelledby="`eng-tab-${mode === 'status' ? statusTab : level}`">
      <form class="search-panel" data-search @submit.prevent="search">
        <div class="search-panel__head"><h2 class="search-panel__title"><AppIcon name="search" size="sm" /> 検索条件</h2><div class="eng-actions"><button class="btn btn--primary"><AppIcon name="search" size="sm" />検索</button><button type="button" class="btn btn--secondary" data-reset @click="reset"><AppIcon name="rotate" size="sm" />リセット</button></div></div>
        <div class="eng-filters">
          <label>等級<select v-model="filters.grade" class="select"><option value="">すべて</option><option v-for="grade in grades" :key="grade">{{ grade }}</option></select></label>
          <label class="eng-filter-book">書籍<select v-model="filters.book" class="select"><option value="">すべての書籍</option><option v-for="book in books" :key="book">{{ book }}</option></select></label>
          <label>分類 From<select v-model="filters.from" class="select"><option value="">すべて</option><option v-for="c in categories" :key="c">{{ c }}</option></select></label>
          <label>分類 To<select v-model="filters.to" class="select"><option value="">すべて</option><option v-for="c in categories" :key="c">{{ c }}</option></select></label>
          <template v-if="mode === 'test'"><label>テスト種別<select v-model="filters.type" class="select"><option value="">すべて</option><option v-for="(text, key) in typeLabels" :key="key" :value="key">{{ text }}</option></select></label><label>状態<select v-model="filters.state" class="select"><option value="">すべて</option><option v-for="(text, key) in states" :key="key" :value="key">{{ text }}</option></select></label></template>
          <template v-else><label class="eng-filter-keyword">キーワード<input v-model="filters.keyword" class="input" data-keyword placeholder="英語・日本語・中国語" /></label><label>習得状態<select v-model="filters.mastery" class="select"><option value="">すべて</option><option value="yes">習得済</option><option value="no">未習得</option></select></label><label v-if="mode === 'manage' && level === 'intermediate'">詳細取得状態<select v-model="filters.detail" class="select"><option value="">すべて</option><option value="yes">取得済み</option><option value="no">未取得</option></select></label><label class="eng-checkbox"><input v-model="filters.favorite" type="checkbox" />お気に入りのみ</label></template>
        </div><p v-if="error" class="eng-error" role="alert">{{ error }}</p>
      </form>
      <WordManagement v-if="mode === 'manage'" :key="`${kind}-${level}`" :kind="kind" :level="level" :words="words" />
      <TestManagement v-else-if="mode === 'test'" :key="`${kind}-${level}`" :kind="kind" :level="level" :words="words" :tests="tests" />
      <StudyStatus v-else :key="kind" :kind="kind" :words="words" :tab="statusTab" />
    </section>
  </div>
</template>
