<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import StudyDialog from '@/features/english-study/StudyDialog.vue'
import StudyPager from '@/features/english-study/StudyPager.vue'
import { useEnglishStudyStore, type StudyKind, type StudyLevel, type StudyWord } from '@/features/english-study/store'

const props = defineProps<{ kind: StudyKind; level: StudyLevel; words: StudyWord[] }>()
const store = useEnglishStudyStore()
const label = computed(() => props.kind === 'word' ? '単語' : '熟語')
const page = ref(1), size = ref(20)
const visible = computed(() => props.words.slice((page.value - 1) * size.value, page.value * size.value))
watch(() => props.words.map(w => w.id).join(','), () => { page.value = 1 })
const detail = ref<StudyWord | null>(null)
const draft = ref<(Omit<StudyWord, 'id' | 'skills'> & { id?: string }) | null>(null)
const removeTarget = ref<StudyWord | null>(null)
const error = ref(''), notice = ref('')
function edit(word?: StudyWord) {
  error.value = ''
  draft.value = word ? { ...word } : { kind: props.kind, level: props.level, english: '', japanese: '', chinese: '', pronunciation: '', book: store.select(props.kind, props.level)[0]?.book ?? '', category: 'Day 01', grade: props.level === 'beginner' ? '3級' : '2級', example: '', translation: '', phraseType: '句動詞・動詞構文', pattern: '', favorite: false, mastered: false, detailReady: false }
}
function save() { try { if (draft.value) store.saveWord(draft.value); draft.value = null; notice.value = '保存しました。' } catch (e) { error.value = (e as Error).message } }
function remove() { store.words = store.words.filter(w => w.id !== removeTarget.value?.id); removeTarget.value = null; notice.value = '削除しました。作成済みテストの記録は保持されます。' }
function read(word: StudyWord) {
  if (!('speechSynthesis' in window)) { notice.value = 'このブラウザは音声再生に対応していません。'; return }
  window.speechSynthesis.cancel(); const speech = new SpeechSynthesisUtterance(word.english); speech.lang = 'en-US'; window.speechSynthesis.speak(speech)
}
function acquire(word: StudyWord) { word.detailReady = true; notice.value = 'デモの詳細サンプルを表示しました。AIへの通信は行っていません。' }
</script>
<template>
  <section class="table-section">
    <header class="table-section__head"><div><h2 class="table-section__title">{{ label }}情報一覧</h2><p class="eng-muted">{{ level === 'beginner' ? '基本の意味と例文を確認できます。' : '語義・例文・問題の準備状況をまとめて確認できます。' }}</p></div><button class="btn btn--primary" data-new-word @click="edit()"><AppIcon name="plus" size="sm" />新規</button></header>
    <p v-if="notice" class="eng-notice" role="status">{{ notice }}</p>
    <div class="table-wrap">
      <table class="data-table eng-table">
        <thead><tr><th>操作</th><th>{{ label }}</th><th>書籍 / 分類</th><th>日本語訳 / 中国語訳</th><th v-if="kind === 'phrase'">標準構文 / 種別</th><th v-if="level === 'intermediate'">詳細情報 / 質問</th><th v-else>例文</th><th>学習状態</th></tr></thead>
        <tbody>
          <tr v-for="word in visible" :key="word.id">
            <td><div class="eng-actions eng-row-actions"><button class="btn btn--icon" :aria-label="`${word.english}の詳細`" @click="detail = word"><AppIcon name="eye" class="icon--view" /></button><button class="btn btn--icon" :aria-label="`${word.english}を編集`" @click="edit(word)"><AppIcon name="edit" class="icon--edit" /></button><button class="btn btn--icon is-danger" :aria-label="`${word.english}を削除`" @click="removeTarget = word"><AppIcon name="trash" /></button></div></td>
            <td><button class="eng-word-link" @click="detail = word">{{ word.english }}</button><small>{{ word.pronunciation || '—' }}</small></td><td>{{ word.book }}<small>{{ word.category }} · {{ word.grade }}</small></td><td>{{ word.japanese }}<small>{{ word.chinese || '—' }}</small></td>
            <td v-if="kind === 'phrase'">{{ word.pattern || word.english }}<small>{{ word.phraseType }}</small></td>
            <td v-if="level === 'intermediate'"><span class="badge" :class="word.detailReady ? 'badge--success' : 'badge--neutral'">{{ word.detailReady ? '取得済み' : '未取得' }}</span><small v-if="word.detailReady">語義 1 · 例文 {{ word.example ? 1 : 0 }} · D / E 問題</small><button v-else class="btn btn--secondary btn--sm" @click="acquire(word)">詳細取得（Demo）</button></td>
            <td v-else class="eng-example-cell">{{ word.example || '—' }}<small>{{ word.translation }}</small></td>
            <td><div class="eng-flags"><button class="btn btn--secondary btn--sm" :aria-label="`${word.english}のお気に入り`" :aria-pressed="word.favorite" @click="word.favorite = !word.favorite">{{ word.favorite ? '★' : '☆' }} お気に入り</button><button class="btn btn--secondary btn--sm" :aria-label="`${word.english}の習得状態`" :aria-pressed="word.mastered" @click="word.mastered = !word.mastered">{{ word.mastered ? '✓ 習得済' : '未習得' }}</button></div></td>
          </tr><tr v-if="!words.length"><td :colspan="kind === 'phrase' ? 7 : 6" class="eng-empty">条件に一致する{{ label }}がありません。条件を変更するか、新規登録してください。</td></tr>
        </tbody>
      </table>
    </div><StudyPager v-model:page="page" v-model:size="size" :total="words.length" />
  </section>
  <StudyDialog v-if="detail" :title="`${label}の詳細`" wide @close="detail = null">
    <div class="eng-word-detail"><div class="eng-toolbar"><span class="badge badge--info">{{ detail.book }} / {{ detail.category }}</span><button class="btn btn--secondary" @click="read(detail)"><AppIcon name="speaker" size="sm" />発音</button></div><h2 lang="en">{{ detail.english }}</h2><p class="eng-muted">{{ detail.pronunciation }}</p><div class="eng-detail-grid"><section><h3>意味</h3><p>{{ detail.japanese }}</p><p class="eng-muted">{{ detail.chinese }}</p></section><section v-if="kind === 'phrase'"><h3>構文・使い方</h3><p>{{ detail.pattern || detail.english }}</p><p class="eng-muted">{{ detail.phraseType }}</p></section></div><section class="eng-example"><h3>例文</h3><p lang="en">{{ detail.example || '例文が未登録です。編集から追加できます。' }}</p><p class="eng-muted">{{ detail.translation }}</p></section><p v-if="level === 'intermediate'" class="eng-muted">詳細取得状態：{{ detail.detailReady ? '取得済み（サンプル）' : '未取得' }}</p><div class="eng-actions"><button class="btn btn--secondary" :aria-pressed="detail.favorite" @click="detail.favorite = !detail.favorite">{{ detail.favorite ? '★ お気に入り' : '☆ お気に入りに追加' }}</button><button class="btn btn--primary" :aria-pressed="detail.mastered" @click="detail.mastered = !detail.mastered">{{ detail.mastered ? '習得済を解除' : '習得済にする' }}</button></div></div>
    <template #footer><button class="btn btn--secondary" @click="detail = null">閉じる</button></template>
  </StudyDialog>
  <StudyDialog v-if="draft" :title="`${label}情報${draft.id ? '編集' : '登録'}`" wide @close="draft = null">
    <form id="eng-word-form" class="eng-edit-grid" @submit.prevent="save">
      <label>英語 *<input v-model="draft.english" class="input" required maxlength="150" /></label><label>発音記号<input v-model="draft.pronunciation" class="input" /></label><label>日本語訳 *<input v-model="draft.japanese" class="input" required /></label><label>中国語訳<input v-model="draft.chinese" class="input" /></label><label>書籍 *<input v-model="draft.book" class="input" required /></label><label>分類 *<input v-model="draft.category" class="input" required /></label><label>等級<select v-model="draft.grade" class="select"><option>3級</option><option>2級</option><option>準1級</option></select></label>
      <template v-if="kind === 'phrase'"><label>熟語種別<select v-model="draft.phraseType" class="select"><option>句動詞・動詞構文</option><option>コロケーション</option><option>定型表現</option><option>慣用表現</option><option>前置詞表現</option></select></label><label class="eng-span-2">標準構文<input v-model="draft.pattern" class="input" /></label></template>
      <label class="eng-span-2">例文<textarea v-model="draft.example" class="input" rows="2" /></label><label class="eng-span-2">例文の日本語訳<textarea v-model="draft.translation" class="input" rows="2" /></label><p v-if="error" class="eng-error eng-span-2" role="alert">{{ error }}</p>
    </form><template #footer><button class="btn btn--secondary" @click="draft = null">キャンセル</button><button form="eng-word-form" class="btn btn--primary">保存</button></template>
  </StudyDialog>
  <StudyDialog v-if="removeTarget" title="削除の確認" @close="removeTarget = null"><p>「{{ removeTarget.english }}」をデモ一覧から削除しますか？</p><template #footer><button class="btn btn--secondary" @click="removeTarget = null">キャンセル</button><button class="btn btn--danger" @click="remove">削除</button></template></StudyDialog>
</template>
