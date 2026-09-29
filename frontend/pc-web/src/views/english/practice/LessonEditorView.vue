<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import StudyDialog from '@/features/english-study/StudyDialog.vue'
import ImageWorkbench from '@/features/english-practice-demo/ImageWorkbench.vue'
import QuestionEditor from '@/features/english-practice-demo/QuestionEditor.vue'
import { blankCloze, blankIntensive } from '@/features/english-practice-demo/mock'
import { usePracticeDemoStore } from '@/features/english-practice-demo/store'
import { copy, sourceLabels, stageLabels, type ClozeSet, type IntensiveRecord } from '@/features/english-practice-demo/types'
import '@/features/english-practice-demo/lesson-demo.css'
const props = defineProps<{ kind: 'cloze' | 'intensive' }>()
const route = useRoute(), router = useRouter(), store = usePracticeDemoStore()
const base = computed(() => `/${route.path.split('/')[1]}/${props.kind === 'cloze' ? 'english-cloze' : 'english-reading-intensive'}`)
const cloze = ref<ClozeSet>(blankCloze()), reading = ref<IntensiveRecord>(blankIntensive())
const record = computed(() => props.kind === 'cloze' ? cloze.value : reading.value)
const snapshot = ref(''), missing = ref(false), error = ref(''), notice = ref(''), busy = ref(false)
const pendingStage = ref<number | 'all' | 'cloze'>(), leave = ref(false)
let resolveLeave: ((value: boolean) => void) | undefined
const dirty = computed(() => JSON.stringify(record.value) !== snapshot.value)
function load() {
  const id = String(route.params.recordId || '')
  const existing = props.kind === 'cloze' ? store.clozeSets.find(r => r.id === id) : store.readings.find(r => r.id === id)
  missing.value = !!id && !existing
  if (props.kind === 'cloze') cloze.value = existing ? copy(existing as ClozeSet) : blankCloze()
  else reading.value = existing ? copy(existing as IntensiveRecord) : blankIntensive()
  snapshot.value = JSON.stringify(record.value); error.value = ''; notice.value = ''
}
watch([() => route.params.recordId, () => props.kind], load, { immediate: true })
function guard() { if (busy.value) { error.value = '処理が完了してから移動してください。'; return false }; if (!dirty.value) return true; leave.value = true; return new Promise<boolean>(resolve => { resolveLeave = resolve }) }
function finishLeave(value: boolean) { leave.value = false; resolveLeave?.(value); resolveLeave = undefined }
onBeforeRouteLeave(guard); onBeforeRouteUpdate(guard)
function beforeUnload(e: BeforeUnloadEvent) { if (dirty.value) { e.preventDefault(); e.returnValue = '' } }
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
function imagesChanged() {
  if (props.kind === 'cloze') { cloze.value.ocrState = 'WAITING'; cloze.value.explanationState = 'WAITING' }
  else { reading.value.stages = ['WAITING', 'WAITING', 'WAITING', 'WAITING']; reading.value.stageError = '' }
  notice.value = '原本画像を変更しました。必要に応じてOCRを再実行してください。'
}
function contentChanged() {
  if (props.kind === 'intensive') { reading.value.stages = [reading.value.stages[0], 'WAITING', 'WAITING', 'WAITING']; reading.value.stageError = '' }
  else { cloze.value.explanationState = 'WAITING'; cloze.value.questions.forEach(q => { q.explanationState = 'WAITING' }) }
}
function requestStage(stage: number | 'all' | 'cloze') {
  if ((stage === 'cloze' && cloze.value.questions.length) || ((stage === 0 || stage === 'all') && reading.value.paragraphs.length)) pendingStage.value = stage
  else void run(stage)
}
async function run(stage: number | 'all' | 'cloze') {
  pendingStage.value = undefined; error.value = ''; notice.value = ''; busy.value = true
  try {
    if (stage === 'cloze') { await new Promise(resolve => setTimeout(resolve, 220)); store.scanCloze(cloze.value) }
    else if (stage === 'all') { for (let i = 0; i < 4; i++) await store.runStage(reading.value, i) }
    else await store.runStage(reading.value, stage)
    notice.value = 'サンプル結果を反映しました。内容を確認・修正して保存してください。'
  } catch (e) { error.value = (e as Error).message }
  finally { busy.value = false }
}
function save() {
  try {
    if (props.kind === 'cloze') store.saveCloze(cloze.value); else store.saveReading(reading.value)
    snapshot.value = JSON.stringify(record.value); void router.push(base.value)
  } catch (e) { error.value = (e as Error).message; window.scrollTo({ top: 0, behavior: 'smooth' }) }
}
function generateCloze() { try { store.generateCloze(cloze.value); error.value = ''; notice.value = '解説を確認しました。Demoではサンプル解説を表示します。' } catch (e) { error.value = (e as Error).message } }
const stateLabel = { WAITING: '未実行', RUNNING: '処理中…', COMPLETED: '完了', ERROR: 'エラー' }
</script>
<template>
  <div class="ep">
    <div class="ep-bar ep-bar--between"><RouterLink :to="base" class="btn btn--secondary">← 一覧へ戻る</RouterLink><div class="ep-bar"><span class="ep-muted">{{ route.params.recordId ? '教材を編集' : '新規教材' }}{{ dirty ? ' · 未保存' : '' }}</span><button class="btn btn--primary" :disabled="missing || busy" @click="save">保存して一覧へ</button></div></div>
    <p v-if="missing" class="ep-empty">教材が見つかりません。一覧から選択し直してください。</p>
    <template v-else>
      <p v-if="error" class="ep-error" role="alert">{{ error }}</p><p v-if="notice" class="ep-success" role="status">{{ notice }}</p>
      <section class="ep-card ep-section">
        <h2>出典・基本情報</h2><div class="ep-fields">
          <label v-if="kind === 'intensive'" class="ep-wide">タイトル <span class="ep-muted">必須</span><input v-model="reading.title" class="input" maxlength="200" /></label>
          <label>出典種別<select v-model="record.sourceType" class="select"><option v-for="(label, key) in sourceLabels" :key="key" :value="key">{{ label }}</option></select></label><label>年度<input v-model="record.year" class="input" placeholder="例：2026" maxlength="4" /></label>
          <label class="ep-wide">試験・練習名 {{ kind === 'cloze' ? '（必須）' : '' }}<input v-model="record.sourceName" class="input" placeholder="例：英検 準1級 第1回" maxlength="200" /></label><label class="ep-wide">章・大問<input v-model="record.chapter" class="input" placeholder="例：大問1 / Unit 5" /></label>
          <template v-if="kind === 'intensive'"><label>対象級<select v-model="reading.grade" class="select"><option>1級</option><option>準1級</option><option>2級及び以下</option></select></label><label>難易度<select v-model="reading.difficulty" class="select"><option>低</option><option>中</option><option>高</option></select></label><label class="ep-wide">テーマ<input v-model="reading.theme" class="input" placeholder="環境・科学・社会など" /></label></template>
          <label class="ep-wide">管理メモ<textarea v-model="record.note" class="input" placeholder="教材の出典や補足を記録" /></label>
        </div>
      </section>
      <section class="ep-card ep-section">
        <ImageWorkbench v-model="record.images" :reading="kind === 'intensive'" :disabled="busy" @change="imagesChanged" /><div v-if="kind === 'cloze'" class="ep-bar"><label class="ep-option"><input v-model="cloze.parallel" type="checkbox" />ページごとにOCR結果をまとめる</label><button class="btn btn--primary" :disabled="busy || !record.images.length" @click="requestStage('cloze')">{{ busy ? '読取中…' : 'OCR結果を取り込む（Demo）' }}</button></div><label v-else>読取モード<select v-model="reading.imageMode" class="select" :disabled="busy" @change="imagesChanged"><option value="TEXT">文章のみ</option><option value="TEXT_FIGURE">文章＋図表</option></select></label>
        <p class="ep-note">Demo：画像の読取・解説生成は固定のサンプル結果を返します。実際の画像解析は行いません。保存内容はこのタブ内で共有され、再読み込みで初期状態に戻ります。</p>
      </section>
      <section v-if="kind === 'intensive'" class="ep-card ep-section"><div class="ep-bar ep-bar--between"><h2>教材の作成工程</h2><button class="btn btn--primary" :disabled="busy" @click="requestStage('all')">全工程を実行（Demo）</button></div><p v-if="reading.stageError" class="ep-error" role="alert">{{ reading.stageError }} 画像を確認し、OCRから再実行してください。</p><div class="ep-steps"><div v-for="(label, i) in stageLabels" :key="label" class="ep-step" :class="{'is-done':reading.stages[i] === 'COMPLETED'}"><strong>{{ i + 1 }}. {{ label }}</strong><span :class="reading.stages[i] === 'ERROR' ? 'ep-error' : 'ep-muted'" role="status">{{ stateLabel[reading.stages[i]] }}</span><button class="btn btn--secondary btn--sm" :disabled="busy || (i > 0 && reading.stages.slice(0,i).some(s => s !== 'COMPLETED'))" @click="requestStage(i)">{{ reading.stages[i] === 'COMPLETED' ? '再実行' : reading.stages[i] === 'ERROR' ? '再試行' : '実行' }}</button></div></div></section>
      <section v-if="kind === 'intensive' && reading.paragraphs.length" class="ep-card ep-section"><h2>本文・OCR結果の修正</h2><p class="ep-note">文章・設問を変更した場合は、基本情報から順に解説を再確認してください。</p><div v-for="(p,i) in reading.paragraphs" :key="p.id" class="ep-section"><h3>段落 {{ i + 1 }}</h3><label v-for="(s,j) in p.sentences" :key="s.id">文 {{ j + 1 }}<textarea v-model="s.english" class="input" :disabled="busy" @input="contentChanged" /></label></div></section>
      <section class="ep-card ep-section"><QuestionEditor v-model="record.questions" :readonly="busy" @change="contentChanged" /><div v-if="kind === 'cloze'" class="ep-bar"><button class="btn btn--secondary" :disabled="busy || !cloze.questions.length" @click="generateCloze">解説生成（Demo）</button><span class="ep-muted">{{ cloze.explanationState === 'COMPLETED' ? '解説あり' : '解説未確認' }}</span></div></section>
      <section v-if="kind === 'intensive' && reading.stages[1] === 'COMPLETED'" class="ep-card ep-section"><h2>導読・要点の確認</h2><div class="ep-fields"><label class="ep-wide">導読（日本語）<textarea v-model="reading.guide.ja" class="input" /></label><label class="ep-wide">導読（中国語）<textarea v-model="reading.guide.zh" class="input" /></label><label class="ep-wide">要点（日本語）<textarea v-model="reading.points.ja" class="input" /></label><label class="ep-wide">要点（中国語）<textarea v-model="reading.points.zh" class="input" /></label></div></section>
      <div class="ep-footer"><RouterLink :to="base" class="btn btn--secondary">キャンセル</RouterLink><button class="btn btn--primary" :disabled="busy" @click="save">保存して一覧へ</button></div>
    </template>
    <StudyDialog v-if="pendingStage !== undefined" title="OCR結果の再作成" @close="pendingStage = undefined"><p>修正した本文・設問をサンプル結果で置き換えます。続けますか？</p><template #footer><button class="btn btn--secondary" @click="pendingStage = undefined">キャンセル</button><button class="btn btn--primary" @click="run(pendingStage!)">置き換えて実行</button></template></StudyDialog>
    <StudyDialog v-if="leave" title="未保存の変更" @close="finishLeave(false)"><p>変更内容を保存せずに移動しますか？</p><template #footer><button class="btn btn--secondary" @click="finishLeave(false)">編集を続ける</button><button class="btn btn--primary" @click="finishLeave(true)">破棄して移動</button></template></StudyDialog>
  </div>
</template>
