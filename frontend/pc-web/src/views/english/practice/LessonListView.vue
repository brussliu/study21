<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '@/components/ui/AppIcon.vue'
import StudyDialog from '@/features/english-study/StudyDialog.vue'
import StudyPager from '@/features/english-study/StudyPager.vue'
import PracticeDialog from '@/features/english-practice-demo/PracticeDialog.vue'
import { usePracticeDemoStore } from '@/features/english-practice-demo/store'
import { sourceLabels, clozeExplanationReady, judged, recordStatus, wordCount, type Question, type LessonImage, type IntensiveRecord } from '@/features/english-practice-demo/types'
import LessonStudyView from './LessonStudyView.vue'
import '@/features/english-practice-demo/lesson-demo.css'
const props = defineProps<{ kind: 'cloze' | 'intensive' }>()
const route = useRoute(), store = usePracticeDemoStore()
const base = computed(() => `/${route.path.split('/')[1]}/${props.kind === 'cloze' ? 'english-cloze' : 'english-reading-intensive'}`)
const defaults = () => ({ keyword: '', source: '', name: '', year: '', result: '', state: '', grade: '' })
const filters = reactive(defaults()), applied = ref(defaults()), page = ref(1), size = ref(20), descending = ref(true)
const history = ref(false), preview = ref<LessonImage[]>(), notice = ref(''), error = ref('')
const learning = ref<{ setId: string; questionId: string }>()
const removeTarget = ref<{ id: string; questionId?: string; title: string }>()
const practice = ref<{ id: string; questions: Question[]; reading?: IntensiveRecord }>()
const rows = computed(() => store.clozeSets.flatMap(set => set.questions.map((question, index) => ({ set, question, index }))).filter(({ set, question }) => {
  const f = applied.value
  return (!f.keyword || `${set.sourceName} ${question.text} ${question.knowledge} ${set.note}`.toLowerCase().includes(f.keyword.toLowerCase())) && (!f.source || set.sourceType === f.source) && (!f.name || set.sourceName.includes(f.name)) && (!f.year || set.year === f.year) && (!f.result || judged(question) === f.result) && (!f.state || (clozeExplanationReady(set, question) ? 'COMPLETED' : 'WAITING') === f.state)
}).sort((a, b) => (descending.value ? -1 : 1) * a.set.createdAt.localeCompare(b.set.createdAt)))
const readings = computed(() => store.readings.filter(r => {
  const f = applied.value
  return (!f.keyword || `${r.title} ${r.sourceName} ${r.theme} ${r.note}`.toLowerCase().includes(f.keyword.toLowerCase())) && (!f.source || r.sourceType === f.source) && (!f.name || r.sourceName.includes(f.name)) && (!f.year || r.year === f.year) && (!f.grade || r.grade === f.grade) && (!f.state || recordStatus(r) === f.state)
}).sort((a, b) => (descending.value ? -1 : 1) * a.createdAt.localeCompare(b.createdAt)))
const total = computed(() => props.kind === 'cloze' ? rows.value.length : readings.value.length)
const visibleRows = computed(() => rows.value.slice((page.value - 1) * size.value, page.value * size.value))
const visibleReadings = computed(() => readings.value.slice((page.value - 1) * size.value, page.value * size.value))
function search() { applied.value = { ...filters, keyword: filters.keyword.trim() }; page.value = 1 }
function reset() { Object.assign(filters, defaults()); search() }
function remove() {
  const target = removeTarget.value; if (!target) return
  if (target.questionId) { const set = store.clozeSets.find(s => s.id === target.id)!; set.questions = set.questions.filter(q => q.id !== target.questionId); if (!set.questions.length) store.remove('cloze', target.id) }
  else store.remove('intensive', target.id)
  removeTarget.value = undefined; notice.value = '削除しました。'
}
function generate(target?: { setId: string; questionId: string }) {
  error.value = ''; notice.value = ''
  const candidates = target ? rows.value.filter(r => r.set.id === target.setId && r.question.id === target.questionId) : rows.value
  if (!candidates.length) return
  try {
    for (const set of new Set(candidates.map(r => r.set))) store.generateCloze(set, candidates.filter(r => r.set.id === set.id).map(r => r.question.id))
    notice.value = `${candidates.length}問にサンプル解説を反映しました。実際のAI生成は行いません。`
  } catch (e) { error.value = (e as Error).message }
}
function retryCount(id: string, qid?: string) { return store.attemptsFor(props.kind, id, qid).length }
function retryStats(id: string, qid: string) {
  const answers = store.attemptsFor('cloze', id, qid).flatMap(attempt => attempt.answers.filter(answer => answer.questionId === qid))
  return { correct: answers.filter(answer => answer.isCorrect).length, incorrect: answers.filter(answer => !answer.isCorrect).length }
}
watch(() => props.kind, () => { reset(); notice.value = ''; error.value = ''; history.value = false; learning.value = undefined })
watch(total, () => { page.value = Math.min(page.value, Math.max(1, Math.ceil(total.value / size.value))) })
</script>
<template>
  <div class="ep">
    <form class="search-panel" @submit.prevent="search">
      <div class="search-panel__head"><h2 class="search-panel__title"><AppIcon name="search" size="sm" />検索条件</h2><div class="ep-bar"><button class="btn btn--primary"><AppIcon name="search" size="sm" />検索</button><button type="button" class="btn btn--secondary" @click="reset"><AppIcon name="rotate" size="sm" />リセット</button><template v-if="kind === 'cloze'"><RouterLink :to="`${base}/new`" class="btn btn--primary"><AppIcon name="plus" size="sm" />新規作成</RouterLink><button type="button" class="btn btn--secondary" @click="history = true">スキャン履歴</button><button type="button" class="btn btn--secondary" :disabled="!rows.length" @click="generate()"><AppIcon name="robot" size="sm" />AI解説生成</button></template></div></div><div :class="kind === 'cloze' ? 'ep-cloze-filters-wrap' : ''">
        <div class="ep-fields" :class="{'ep-cloze-filters': kind === 'cloze'}" style="padding:16px">
          <label :class="{'ep-wide': kind !== 'cloze'}">キーワード<input v-model="filters.keyword" class="input" :placeholder="kind === 'cloze' ? '問題文・知識点・出典・メモ' : 'タイトル・出典・テーマ・メモ'" /></label>
          <label>出典種別<select v-model="filters.source" class="select"><option value="">すべて</option><option v-for="(label, key) in sourceLabels" :key="key" :value="key">{{ label }}</option></select></label>
          <label>試験・練習名<input v-model="filters.name" class="input" placeholder="出典名の一部" /></label><label>年度<input v-model="filters.year" class="input" placeholder="例：2026" /></label>
          <label v-if="kind === 'cloze'">元の答案<select v-model="filters.result" class="select"><option value="">すべて</option><option>正解</option><option>不正解</option><option>未判定</option></select></label>
          <label v-else>対象級<select v-model="filters.grade" class="select"><option value="">すべて</option><option>1級</option><option>準1級</option><option>2級及び以下</option></select></label>
          <label>処理状態<select v-model="filters.state" class="select"><option value="">すべて</option><template v-if="kind === 'cloze'"><option value="COMPLETED">解説あり</option><option value="WAITING">解説未確認</option></template><template v-else><option v-for="s in ['未完了','OCR処理中','AI生成中','生成済み','エラー']" :key="s">{{ s }}</option></template></select></label>
        </div>
      </div>
    </form>
    <p v-if="notice" class="ep-success" role="status">{{ notice }}</p>
    <p v-if="error" class="ep-error" role="alert">{{ error }}</p>
    <section class="ep-card ep-section">
      <div class="ep-bar ep-bar--between"><h2>{{ kind === 'cloze' ? '問題一覧' : '精読教材一覧' }} <span class="ep-muted">{{ total }} 件</span></h2><RouterLink v-if="kind === 'intensive'" :to="`${base}/new`" class="btn btn--primary"><AppIcon name="plus" size="sm" />新規作成</RouterLink></div>
      <div class="ep-table-wrap">
        <table class="ep-table">
          <thead><tr><th>操作</th><th><button class="ep-link" @click="descending = !descending">登録日 {{ descending ? '↓' : '↑' }}</button></th><th>{{ kind === 'cloze' ? '問題 / 知識点' : 'タイトル / テーマ' }}</th><th>出典</th><th>{{ kind === 'cloze' ? '元の答案' : '対象級 / 難易度' }}</th><th v-if="kind === 'cloze'">再挑戦</th><th>処理状態</th><th v-if="kind === 'intensive'">再挑戦</th></tr></thead>
          <tbody v-if="kind === 'cloze'"><tr v-for="{set, question, index} in visibleRows" :key="question.id"><td><div class="ep-cloze-actions"><button class="ep-icon ep-icon--ai" aria-label="AI解説生成" title="AI解説生成" @click="generate({setId:set.id,questionId:question.id})"><AppIcon name="robot" size="sm" class="icon--ai" /></button><button class="ep-icon" :disabled="question.correct === null" aria-label="再挑戦" title="再挑戦" @click="practice = {id:set.id,questions:[question]}"><AppIcon name="play" size="sm" /></button><button class="ep-icon" aria-label="学習を開く" title="学習" @click="learning = {setId:set.id,questionId:question.id}"><AppIcon name="book-open" size="sm" /></button><button class="ep-icon ep-icon--danger" aria-label="削除" title="削除" @click="removeTarget = {id:set.id,questionId:question.id,title:question.text}"><AppIcon name="trash" size="sm" /></button></div></td><td>{{ set.createdAt }}<div class="ep-muted">Q{{ index + 1 }}</div></td><td class="ep-summary"><button class="ep-link" @click="learning = {setId:set.id,questionId:question.id}">{{ question.text }}</button><div class="ep-muted">{{ question.knowledge || '知識点未登録' }}</div></td><td>{{ sourceLabels[set.sourceType] }} · {{ set.year }}<div>{{ set.sourceName }}</div><small class="ep-muted">{{ set.chapter }} {{ set.note }}</small></td><td><span class="ep-tag" :class="judged(question) === '正解' ? 'ep-tag--good' : judged(question) === '不正解' ? 'ep-tag--bad' : ''">{{ judged(question) }}</span></td><td><span class="ep-retry-stat ep-retry-stat--correct">正解 {{ retryStats(set.id,question.id).correct }}</span><span class="ep-retry-stat ep-retry-stat--incorrect">不正解 {{ retryStats(set.id,question.id).incorrect }}</span></td><td><span class="ep-tag">{{ clozeExplanationReady(set, question) ? '解説あり' : '解説未確認' }}</span></td></tr></tbody>
          <tbody v-else><tr v-for="r in visibleReadings" :key="r.id"><td><div class="ep-bar"><RouterLink class="ep-icon" :to="`${base}/${r.id}`" aria-label="精読を開く"><AppIcon name="eye" size="sm" /></RouterLink><RouterLink class="ep-icon" :to="`${base}/${r.id}/edit`" aria-label="編集"><AppIcon name="edit" size="sm" /></RouterLink><button class="ep-icon ep-icon--danger" aria-label="削除" @click="removeTarget = {id:r.id,title:r.title}"><AppIcon name="trash" size="sm" /></button></div></td><td>{{ r.createdAt }}</td><td class="ep-summary"><RouterLink class="ep-link" :to="`${base}/${r.id}`"><strong>{{ r.title }}</strong></RouterLink><div class="ep-muted">{{ r.theme || 'テーマ未設定' }} · {{ wordCount(r) }} words · {{ r.questions.length }} 問</div></td><td>{{ sourceLabels[r.sourceType] }} · {{ r.year }}<div>{{ r.sourceName || '未設定' }}</div><small class="ep-muted">{{ r.note }}</small></td><td>{{ r.grade }}<div class="ep-muted">{{ r.difficulty }}</div></td><td><span class="ep-tag" :class="recordStatus(r) === 'エラー' ? 'ep-tag--bad' : recordStatus(r) === '生成済み' ? 'ep-tag--good' : ''">{{ recordStatus(r) }}</span><div class="ep-muted">{{ r.stages.filter(s => s === 'COMPLETED').length }} / 4 工程</div></td><td><button class="btn btn--secondary btn--sm" :disabled="r.stages[3] !== 'COMPLETED' || !r.questions.length || r.questions.some(q => q.correct === null)" @click="practice = {id:r.id,questions:r.questions,reading:r}">再挑戦</button><div class="ep-muted">{{ retryCount(r.id) }} 回</div></td></tr></tbody>
        </table>
      </div><div v-if="!total" class="ep-empty">該当するデータがありません。検索条件を変更するか、新規作成してください。</div><StudyPager v-model:page="page" v-model:size="size" :total="total" />
    </section>
    <StudyDialog v-if="removeTarget" title="削除の確認" @close="removeTarget = undefined"><div class="ep"><p>{{ removeTarget.title }}</p><p>{{ kind === 'cloze' ? 'この設問を削除します。最後の設問の場合は教材も削除します。' : 'この教材と再挑戦履歴を削除します。' }}</p></div><template #footer><button class="btn btn--secondary" @click="removeTarget = undefined">キャンセル</button><button class="btn btn--danger" @click="remove">削除</button></template></StudyDialog>
    <StudyDialog v-if="history" title="スキャン履歴" wide @close="history = false"><div class="ep ep-table-wrap"><table class="ep-table"><thead><tr><th>スキャン日時</th><th>出典</th><th>画像</th><th>設問数</th><th>結果</th></tr></thead><tbody><tr v-for="s in store.scans" :key="s.id"><td>{{ s.date }}</td><td>{{ s.source }}</td><td><button class="ep-link" @click="preview = s.images">{{ s.images.length }} 枚を表示</button></td><td>{{ s.count }}</td><td>完了</td></tr></tbody></table></div></StudyDialog>
    <StudyDialog v-if="preview" title="スキャンした原本" wide @close="preview = undefined"><img v-for="img in preview" :key="img.id" class="ep-original" :src="img.src" :alt="img.name" /></StudyDialog>
    <StudyDialog v-if="learning" title="穴埋め問題の学習" wide @close="learning = undefined"><LessonStudyView kind="cloze" :record-id="learning.setId" :question-id="learning.questionId" dialog /></StudyDialog>
    <PracticeDialog v-if="practice" :kind="kind" :parent-id="practice.id" :questions="practice.questions" :reading="practice.reading" @close="practice = undefined" />
  </div>
</template>
