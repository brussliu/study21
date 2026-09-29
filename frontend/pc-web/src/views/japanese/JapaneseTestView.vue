<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError, formatIsoDateTime } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import { TEST_TYPE_LABELS, TEST_STATE_LABELS, fetchJpnTestConditions, searchJpnTestRange, createJpnTests, deleteJpnTest, type JpnTest, type JpnTestCreate, type TestType } from '@/api/japanese'
import { openPopupWindow } from '@/features/japanese/popupWindow'
import '@/features/japanese/test.css'

const route = useRoute()
const filters = reactive({ book: '', categoryFrom: '', categoryTo: '', testType: '', state: '' })
let applied = { ...filters }
const books = ref<string[]>([])
const categories = ref<string[]>([])
const tests = ref<JpnTest[]>([])
const page = ref(1)
const pages = ref(1)
const total = ref(0)
const loading = ref(false)
const error = ref('')
const modal = ref(false)
const saving = ref(false)
const createError = ref('')
const result = ref<Awaited<ReturnType<typeof createJpnTests>>['data'] | null>(null)
type CreateRow = JpnTestCreate & { id: number; categories: string[] }
const rows = ref<CreateRow[]>([])
const types = Object.keys(TEST_TYPE_LABELS) as TestType[]
const counts = [0, 10, 20, 30, 50, 100]
let serial = 0
let searchSequence = 0
let conditionSequence = 0
const cache = new Map<string, string[]>()
function message(e: unknown) { return e instanceof ApiError || e instanceof Error ? e.message : '処理に失敗しました。' }
function invalidRange(from?: string, to?: string) { return !!from && !!to && from > to }
async function getCategories(book: string) {
  if (!cache.has(book)) cache.set(book, (await fetchJpnTestConditions(book)).data.categories)
  return cache.get(book) ?? []
}
async function bookChanged() {
  const seq = ++conditionSequence
  filters.categoryFrom = ''; filters.categoryTo = ''
  try { const data = await getCategories(filters.book); if (seq === conditionSequence) categories.value = data }
  catch (e) { error.value = message(e) }
}
async function load() {
  const seq = ++searchSequence
  loading.value = true; error.value = ''
  try {
    const { data } = await searchJpnTestRange({ ...applied, page: page.value, size: 15 })
    if (seq !== searchSequence) return
    tests.value = data.items; total.value = data.totalElements; pages.value = Math.max(1, data.totalPages)
    if (page.value > pages.value) { page.value = pages.value; await load() }
  } catch (e) { if (seq === searchSequence) error.value = message(e) }
  finally { if (seq === searchSequence) loading.value = false }
}
function search() {
  if (invalidRange(filters.categoryFrom, filters.categoryTo)) { error.value = '分類 From は分類 To 以前を指定してください。'; return }
  applied = { ...filters }; page.value = 1; void load()
}
async function reset() {
  Object.assign(filters, { book: '', categoryFrom: '', categoryTo: '', testType: '', state: '' })
  await bookChanged(); search()
}
function open(row: JpnTest) {
  const area = route.path.split('/')[1] || 'student'
  const popup = openPopupWindow({ url: `/${area}/japanese-test/run?testId=${row.testId}`, name: `jpTest${row.testId}`, width: 1380, height: 900 })
  if (!popup) error.value = 'ポップアップを許可して、もう一度開いてください。'
}
async function remove(row: JpnTest) {
  if (!window.confirm(`テスト「${row.testNo}」を削除しますか？回答履歴も削除されます。`)) return
  try { await deleteJpnTest(row.testId); await load() } catch (e) { error.value = message(e) }
}
async function addRow() {
  if (rows.value.length >= 20 || saving.value) return
  const seed = rows.value[0] ?? filters
  const row: CreateRow = { id: ++serial, book: seed.book, categoryFrom: seed.categoryFrom, categoryTo: seed.categoryTo, questionCount: 0, testType: 'A', categories: [] }
  rows.value.push(row)
  try { const values = await getCategories(row.book ?? ''); const target = rows.value.find(r => r.id === row.id); if (target) target.categories = values }
  catch (e) { createError.value = message(e) }
}
async function rowBookChanged(row: CreateRow) {
  row.categoryFrom = ''; row.categoryTo = ''; const book = row.book ?? ''
  try { const values = await getCategories(book); if (row.book === book) row.categories = values }
  catch (e) { createError.value = message(e) }
}
function openCreate() { rows.value = []; result.value = null; createError.value = ''; modal.value = true; void addRow() }
function closeCreate() { if (!saving.value) modal.value = false }
async function create() {
  if (saving.value || result.value) return
  const invalid = rows.value.findIndex(r => invalidRange(r.categoryFrom, r.categoryTo))
  if (invalid >= 0) { createError.value = `${invalid + 1}行目：分類 From は分類 To 以前を指定してください。`; return }
  saving.value = true; createError.value = ''
  try {
    result.value = (await createJpnTests(rows.value.map(r => ({ book: r.book, categoryFrom: r.categoryFrom, categoryTo: r.categoryTo, questionCount: r.questionCount, testType: r.testType, mode: 'ALL', difficulty: 'NORMAL' })))).data
    page.value = 1; await load()
  } catch (e) { createError.value = message(e) }
  finally { saving.value = false }
}
function refresh() { if (!modal.value) void load() }
function escape(event: KeyboardEvent) { if (event.key === 'Escape') closeCreate() }
function duration(ms: number) { const m = Math.floor(ms / 60000); return m >= 60 ? `${Math.floor(m / 60)}時間 ${m % 60}分` : `${m}分` }
function date(value: string | null) { return value ? formatIsoDateTime(value) : '—' }
onMounted(async () => {
  window.addEventListener('focus', refresh); window.addEventListener('keydown', escape)
  try { const { data } = await fetchJpnTestConditions(); books.value = data.books; categories.value = data.categories; cache.set('', data.categories) }
  catch (e) { error.value = message(e); return }
  await load()
})
onUnmounted(() => { window.removeEventListener('focus', refresh); window.removeEventListener('keydown', escape) })
</script>

<template>
  <div class="jpt-page">
    <form class="card jpt-panel" data-test-search @submit.prevent="search">
      <header class="jpt-toolbar">
        <h2><AppIcon name="search" /> テスト検索</h2><div class="jpt-actions">
          <button type="button" class="btn btn--primary" data-test-multiple @click="openCreate"><AppIcon name="plus" size="sm" /> 複数作成</button>
          <button class="btn btn--primary" :disabled="loading">検索</button><button type="button" class="btn btn--secondary" @click="reset">リセット</button>
        </div>
      </header>
      <div class="jpt-filters">
        <label>書籍<select v-model="filters.book" class="select" data-test-filter="book" @change="bookChanged"><option value="">すべて</option><option v-for="b in books" :key="b">{{ b }}</option></select></label>
        <label>分類 From<select v-model="filters.categoryFrom" class="select" data-test-filter="from"><option value="">すべて</option><option v-for="c in categories" :key="c">{{ c }}</option></select></label>
        <label>分類 To<select v-model="filters.categoryTo" class="select" data-test-filter="to"><option value="">すべて</option><option v-for="c in categories" :key="c">{{ c }}</option></select></label>
        <label>テスト種別<select v-model="filters.testType" class="select"><option value="">すべて</option><option v-for="t in types" :key="t" :value="t">{{ TEST_TYPE_LABELS[t] }}</option></select></label>
        <label>状態<select v-model="filters.state" class="select"><option value="">すべて</option><option v-for="(label, value) in TEST_STATE_LABELS" :key="value" :value="value">{{ label }}</option></select></label>
      </div>
    </form>
    <p v-if="error" role="alert" class="alert alert--danger">{{ error }}</p>
    <section class="card jpt-panel" :aria-busy="loading">
      <header class="jpt-toolbar"><h2><AppIcon name="list" size="sm" /> テスト情報一覧</h2><span>{{ loading ? '読み込み中…' : `全${total}件` }}</span></header>
      <div class="jpt-table-scroll">
        <table class="data-table jpt-table">
          <thead><tr><th>操作</th><th>テスト番号</th><th>レベル</th><th>書籍</th><th>分類</th><th>状態</th><th>方法</th><th>種別</th><th>期間</th><th>進捗</th><th>正解率</th><th>勉強時間</th></tr></thead>
          <tbody>
            <tr v-for="row in tests" :key="row.testId">
              <td><div class="jpt-actions"><button class="btn btn--secondary btn--sm" :aria-label="`${row.testNo}を開く`" @click="open(row)">{{ row.state === 'COMPLETED' ? '結果' : row.state === 'CREATED' ? '開始' : '再開' }}</button><button class="btn btn--icon btn--sm" :aria-label="`${row.testNo}を削除`" @click="remove(row)"><AppIcon name="trash" size="sm" /></button></div></td>
              <td>{{ row.testNo }}</td><td>{{ row.level || '—' }}</td><td>{{ row.book || 'すべて' }}</td><td>{{ row.categoryFrom || 'すべて' }} ～ {{ row.categoryTo || 'すべて' }}</td><td><span class="badge">{{ TEST_STATE_LABELS[row.state] }}</span></td><td>{{ row.mode === 'ALL' ? '順番' : row.mode === 'RANDOM' ? 'ランダム' : row.mode }}</td><td>{{ TEST_TYPE_LABELS[row.testType] }}</td><td>{{ date(row.startedAt) }}<br>{{ date(row.finishedAt) }}</td><td>{{ row.doneCount }} / {{ row.questionCount }}</td><td>{{ row.doneCount ? `${row.scorePercent}%` : '—' }}</td><td>{{ duration(row.activeMs) }}</td>
            </tr><tr v-if="!loading && !tests.length"><td colspan="12" class="jpt-empty">テストがありません。複数作成からテストを作成してください。</td></tr>
          </tbody>
        </table>
      </div>
      <nav v-if="pages > 1" class="jpt-pagination" aria-label="ページ切替"><button class="btn btn--secondary" :disabled="page <= 1 || loading" @click="page--; load()">前へ</button><span>{{ page }} / {{ pages }} ページ</span><button class="btn btn--secondary" :disabled="page >= pages || loading" @click="page++; load()">次へ</button></nav>
    </section>
    <div v-if="modal" class="jpt-backdrop" @click.self="closeCreate">
      <section class="card jpt-dialog" role="dialog" aria-modal="true" aria-labelledby="jpt-create-title">
        <header class="jpt-toolbar"><div><h2 id="jpt-create-title">日本語テスト複数作成</h2><p>条件の異なるテストをまとめて作成できます（最大20行）。</p></div><button class="btn btn--secondary" :disabled="saving" aria-label="閉じる" @click="closeCreate">×</button></header>
        <form data-test-create-form @submit.prevent="create">
          <fieldset :disabled="saving || !!result" class="jpt-fieldset">
            <div class="jpt-table-scroll">
              <table class="data-table jpt-table">
                <thead><tr><th>操作</th><th>書籍</th><th>分類 From</th><th>分類 To</th><th>数量</th><th>テスト種別</th></tr></thead><tbody>
                  <tr v-for="(row, i) in rows" :key="row.id"><td><button type="button" class="btn btn--icon btn--sm is-danger" data-test-row-delete :title="`${i + 1}行目を削除`" :aria-label="`${i + 1}行目を削除`" :disabled="rows.length <= 1" @click="rows.splice(i, 1)"><AppIcon name="trash" size="sm" /></button></td><td><select v-model="row.book" class="select" :aria-label="`${i + 1}行目 書籍`" @change="rowBookChanged(row)"><option value="">すべて</option><option v-for="b in books" :key="b">{{ b }}</option></select></td><td><select v-model="row.categoryFrom" class="select" :aria-label="`${i + 1}行目 分類 From`"><option value="">すべて</option><option v-for="c in row.categories" :key="c">{{ c }}</option></select></td><td><select v-model="row.categoryTo" class="select" :aria-label="`${i + 1}行目 分類 To`"><option value="">すべて</option><option v-for="c in row.categories" :key="c">{{ c }}</option></select></td><td><select v-model.number="row.questionCount" class="select" data-test-row-count :aria-label="`${i + 1}行目 数量`"><option v-for="c in counts" :key="c" :value="c">{{ c || '全部' }}</option></select></td><td><select v-model="row.testType" class="select" data-test-row-type :aria-label="`${i + 1}行目 テスト種別`"><option v-for="t in types" :key="t" :value="t">{{ TEST_TYPE_LABELS[t] }}</option></select></td></tr>
                </tbody>
              </table>
            </div>
          </fieldset>
          <p v-if="createError" role="alert" class="alert alert--danger">{{ createError }}</p>
          <div v-if="result" role="status" class="jpt-result"><strong>作成完了：{{ result.createdCount }}件 / スキップ：{{ result.skippedCount }}件</strong><p v-for="r in result.results.filter(r => r.skipped)" :key="r.rowNo">{{ r.rowNo }}行目：{{ r.message }}</p></div>
          <footer class="jpt-toolbar"><button type="button" class="btn btn--secondary" data-test-add-row :disabled="saving || !!result || rows.length >= 20" @click="addRow">＋ 行を追加</button><div class="jpt-actions"><button type="button" class="btn btn--secondary" :disabled="saving" @click="closeCreate">{{ result ? '閉じる' : 'キャンセル' }}</button><button v-if="!result" class="btn btn--primary" :disabled="saving">{{ saving ? '作成中…' : '作成' }}</button></div></footer>
        </form>
      </section>
    </div>
  </div>
</template>
