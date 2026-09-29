<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import StudyDialog from '@/features/english-study/StudyDialog.vue'
import StudyPager from '@/features/english-study/StudyPager.vue'
import { levels, score, states, testLabels, useEnglishStudyStore, type StudyKind, type StudyLevel, type StudyTest, type StudyWord, type TestType } from '@/features/english-study/store'
import TestRunner from './TestRunner.vue'
const props = defineProps<{ kind: StudyKind; level: StudyLevel; words: StudyWord[]; tests: StudyTest[] }>()
const store = useEnglishStudyStore()
const typeLabels = computed(() => testLabels(props.kind))
const page = ref(1), size = ref(20)
const visible = computed(() => props.tests.slice((page.value - 1) * size.value, page.value * size.value))
watch(() => props.tests.map(t => t.id).join(','), () => { page.value = 1 })
const creating = ref(false), error = ref(''), notice = ref('')
const running = ref<StudyTest | null>(null), list = ref<StudyTest | null>(null), deleting = ref<StudyTest | null>(null)
const wrongOnly = ref(false)
let serial = 0
type Row = { id: number; book: string; from: string; to: string; count: number; type: TestType; exclude: boolean }
const rows = ref<Row[]>([])
const types = computed(() => Object.entries(typeLabels.value).filter(([key]) => props.level === 'intermediate' || !['E', 'F'].includes(key)))
const books = computed(() => [...new Set(props.words.map(w => w.book))])
function categories(book: string) { return [...new Set(props.words.filter(w => !book || w.book === book).map(w => w.category))].sort() }
function addRow() { if (rows.value.length < 20) rows.value.push({ id: ++serial, book: books.value.length === 1 ? books.value[0] : '', from: '', to: '', count: 0, type: 'A', exclude: false }) }
function openCreate() { error.value = ''; rows.value = []; addRow(); creating.value = true }
function targets(row: Row) { return props.words.filter(w => (!row.book || w.book === row.book) && (!row.from || w.category >= row.from) && (!row.to || w.category <= row.to) && (!row.exclude || !w.mastered) && (!['E', 'F'].includes(row.type) || !!w.example && !!w.translation && (row.type !== 'E' || w.example.toLowerCase().includes(w.english.toLowerCase())))).slice(0, row.count || undefined) }
function create() {
  const invalid = rows.value.findIndex(row => row.from && row.to && row.from > row.to || !targets(row).length)
  if (invalid >= 0) { error.value = `${invalid + 1}行目：分類の範囲と出題できる語を確認してください。`; return }
  rows.value.forEach(row => store.createTest({ kind: props.kind, level: props.level, type: row.type, wordIds: targets(row).map(w => w.id) }))
  notice.value = `${rows.value.length}件のテストを作成しました。検索条件によっては表示されない場合があります。`; creating.value = false
}
function retry() { try { if (running.value) running.value = store.retry(running.value.id) } catch (e) { notice.value = (e as Error).message; running.value = null } }
function remove() { store.tests = store.tests.filter(t => t.id !== deleting.value?.id); deleting.value = null }
const listWords = computed(() => list.value?.questions.filter(w => !wrongOnly.value || list.value?.answers.some(a => a.wordId === w.id && !a.correct)) ?? [])
</script>
<template>
  <section class="table-section">
    <header class="table-section__head"><div><h2 class="table-section__title">テスト情報一覧</h2><p class="eng-muted">学習・入力・選択式から、自分に合う方法で。</p></div><button class="btn btn--primary" data-create-tests @click="openCreate"><AppIcon name="plus" size="sm" />複数作成</button></header>
    <p v-if="notice" class="eng-notice" role="status">{{ notice }}</p>
    <div class="table-wrap"><table class="data-table eng-table"><thead><tr><th>操作</th><th>テスト番号 / レベル</th><th>書籍 / 分類</th><th>種別 / 方法</th><th>状態</th><th>進捗</th><th>正解率</th><th>勉強時間</th></tr></thead><tbody><tr v-for="test in visible" :key="test.id"><td><div class="eng-actions eng-row-actions"><button class="btn btn--secondary btn--sm" :aria-label="`${test.id}を開く`" @click="running = test">{{ test.state === 'COMPLETED' ? '結果' : test.state === 'STARTED' ? '再開' : '開始' }}</button><button class="btn btn--icon" :aria-label="`${test.id}の出題一覧`" @click="list = test; wrongOnly = false"><AppIcon name="eye" class="icon--view" /></button><button class="btn btn--icon is-danger" :aria-label="`${test.id}を削除`" @click="deleting = test"><AppIcon name="trash" /></button></div></td><td>{{ test.id }}<small>{{ levels[test.level] }} · {{ test.createdAt }}</small></td><td>{{ test.book }}<small>{{ test.category }}</small></td><td>{{ typeLabels[test.type] }}<small>{{ test.type === 'A' ? '学習カード' : ['D', 'E'].includes(test.type) ? '選択' : '入力' }}</small></td><td><span class="badge" :class="test.state === 'COMPLETED' ? 'badge--success' : test.state === 'STARTED' ? 'badge--warning' : 'badge--neutral'">{{ states[test.state] }}</span></td><td>{{ test.answers.length }} / {{ test.wordIds.length }}<progress class="eng-progress" :value="test.answers.length" :max="test.wordIds.length" aria-label="進捗" /></td><td>{{ test.answers.length ? `${score(test)}%` : '—' }}</td><td>{{ Math.floor(test.activeSeconds / 60) }}分{{ test.activeSeconds % 60 }}秒</td></tr><tr v-if="!tests.length"><td colspan="8" class="eng-empty">テストがありません。条件を変更するか、複数作成から追加してください。</td></tr></tbody></table></div>
    <StudyPager v-model:page="page" v-model:size="size" :total="tests.length" />
  </section>
  <StudyDialog v-if="creating" title="テストを複数作成" wide @close="creating = false"><p class="eng-muted">検索済みの {{ words.length }} 語から作成します。{{ levels[level] }} · {{ kind === 'word' ? '単語' : '熟語' }}</p><p v-if="error" role="alert" class="eng-error">{{ error }}</p><div v-for="(row, i) in rows" :key="row.id" class="eng-create-row"><div class="eng-toolbar"><strong>テスト {{ i + 1 }}</strong><button class="btn btn--icon is-danger" :disabled="rows.length === 1" :aria-label="`${i + 1}行目を削除`" @click="rows.splice(i, 1)"><AppIcon name="trash" size="sm" /></button></div><div class="eng-filters"><label class="eng-filter-book">書籍<select v-model="row.book" class="select" @change="row.from = ''; row.to = ''"><option value="">すべて</option><option v-for="book in books" :key="book">{{ book }}</option></select></label><label>分類 From<select v-model="row.from" class="select"><option value="">すべて</option><option v-for="c in categories(row.book)" :key="c">{{ c }}</option></select></label><label>分類 To<select v-model="row.to" class="select"><option value="">すべて</option><option v-for="c in categories(row.book)" :key="c">{{ c }}</option></select></label><label>テスト種別<select v-model="row.type" class="select"><option v-for="[key, text] in types" :key="key" :value="key">{{ text }}</option></select></label><label>出題数<select v-model="row.count" class="select"><option :value="0">全部</option><option v-for="n in [5, 10, 20, 50]" :key="n" :value="n">{{ n }} 語</option></select></label><label class="eng-checkbox"><input v-model="row.exclude" type="checkbox" />習得済を除外</label></div><small class="eng-muted">出題対象 {{ targets(row).length }} 語{{ ['E', 'F'].includes(row.type) ? '（例文が登録されている語）' : '' }}</small></div><button class="btn btn--secondary" :disabled="rows.length >= 20" @click="addRow"><AppIcon name="plus" size="sm" />追加行</button><template #footer><button class="btn btn--secondary" @click="creating = false">キャンセル</button><button class="btn btn--primary" @click="create">作成</button></template></StudyDialog>
  <TestRunner v-if="running" :key="running.id" :test="running" @close="running = null" @retry="retry" />
  <StudyDialog v-if="list" title="出題一覧" wide @close="list = null"><label class="eng-checkbox"><input v-model="wrongOnly" type="checkbox" />誤りのみ</label><div class="table-wrap"><table class="data-table"><thead><tr><th>英語</th><th>日本語訳</th><th>中国語訳</th><th>結果</th></tr></thead><tbody><tr v-for="w in listWords" :key="w.id"><td>{{ w.english }}</td><td>{{ w.japanese }}</td><td>{{ w.chinese }}</td><td>{{ list.answers.find(a => a.wordId === w.id)?.correct === true ? '○' : list.answers.some(a => a.wordId === w.id) ? '復習' : '未回答' }}</td></tr><tr v-if="!listWords.length"><td colspan="4" class="eng-empty">該当する語はありません。</td></tr></tbody></table></div><template #footer><button class="btn btn--secondary" @click="list = null">閉じる</button></template></StudyDialog>
  <StudyDialog v-if="deleting" title="テスト削除" @close="deleting = null"><p>{{ deleting.id }} を削除しますか？学習状況の累計は保持されます。</p><template #footer><button class="btn btn--secondary" @click="deleting = null">キャンセル</button><button class="btn btn--danger" @click="remove">削除</button></template></StudyDialog>
</template>
