<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'
import {
  CAUTION_KIND_LABELS,
  DETAIL_STATUS_BADGES,
  DETAIL_STATUS_LABELS,
  PART_OF_SPEECH_OPTIONS,
  PRACTICE_KIND_LABELS,
  collectionLabel,
  findSameHeading
} from '@/features/japanese-demo/logic'
import type { DemoCautionKind, DemoPracticeKind } from '@/features/japanese-demo/types'
import '@/features/japanese-demo/japanese-demo.css'

/**
 * 日本語勉強【単語情報管理】：詳細編集（別ウィンドウ）。
 *
 * 長い内容を 1 ページに積まないよう、左のナビで 11 の欄に分ける。
 * 参考にした 2.0: `japanese_word.jsp` の詳細モーダル（`jpWordDetailContent`）——
 * 2.0 は取得した詳細を読み取り専用で並べるだけだったので、
 * 2.1 では「人が直せる」ことを前提に、配列の追加・並べ替え・削除まで扱う。
 *
 * ここでの編集は**下書き**（ストアのメモリ上）で、保存を押すまで仮データにも反映しない。
 * プレビューは下書きをそのまま学習画面の見た目で見る（保存済みの内容とは混ざらない）。
 */

const store = useJapaneseDemoStore()

const draft = computed(() => store.draft)

const emit = defineEmits<{
  close: []
  preview: [{ draft: boolean }]
}>()

/** 選択中の欄（タブ）。 */
const activeSection = ref('basic')
/** 未保存のまま離れようとしたときの確認。 */
const leavePrompt = ref('')
/** 同じ表記で読みが違う語（同表記・別読みの注意）。 */
const sameHeading = computed(() =>
  draft.value === null ? [] : findSameHeading(store.words, draft.value)
)

/** 左のナビ。配列を持つ欄は件数を出す。 */
const SECTIONS = [
  { key: 'basic', label: '基本情報', count: () => null as number | null },
  { key: 'meaning', label: '語義・説明', count: () => draft.value?.detail?.senses.length ?? 0 },
  { key: 'patterns', label: '文型・助詞', count: () => draft.value?.detail?.patterns.length ?? 0 },
  { key: 'examples', label: '例文・会話', count: () => (draft.value?.detail?.examples.length ?? 0) + (draft.value?.detail?.dialogs.length ?? 0) },
  { key: 'synonyms', label: '類義語・使い分け', count: () => draft.value?.detail?.synonyms.length ?? 0 },
  { key: 'cautions', label: '間違えやすい点', count: () => draft.value?.detail?.cautions.length ?? 0 },
  { key: 'form', label: '活用・自他動詞', count: () => draft.value?.detail?.conjugations.length ?? 0 },
  { key: 'pronunciation', label: '発音・アクセント', count: () => null },
  { key: 'collocations', label: 'コロケーション', count: () => (draft.value?.detail?.collocations.length ?? 0) + (draft.value?.detail?.relatedWords.length ?? 0) + (draft.value?.detail?.usageNotes.length ?? 0) },
  { key: 'memory', label: '記憶のヒント', count: () => null },
  { key: 'practice', label: 'ミニ練習', count: () => draft.value?.detail?.practices.length ?? 0 }
]

function sectionCount(section: (typeof SECTIONS)[number]): number | null {
  return section.count()
}

/** 動詞かどうか（名詞には活用を出さない）。 */
const isVerb = computed(() => draft.value?.partOfSpeech === '動詞' || draft.value?.partOfSpeech === '名詞・動詞')

/** 新しい配列項目の雛形を作る（id はストア側で採番できないのでここで作る）。 */
function tempId(prefix: string): string {
  tempId.counter += 1
  return `${prefix}-new-${tempId.counter}`
}
tempId.counter = 0

function addSense(): void {
  const detail = draft.value?.detail
  const nextNumber = (detail?.senses.length ?? 0) + 1
  store.addListItem('senses', {
    id: tempId('sense'), number: nextNumber, japanese: '', chinese: '', context: '', style: '普通'
  })
}

function addExample(): void {
  store.addListItem('examples', {
    id: tempId('example'), japanese: '', reading: '', chinese: '', senseNumber: 1, source: '', level: 'BASIC'
  })
}

function addPattern(): void {
  store.addListItem('patterns', {
    id: tempId('pattern'), pattern: '', reading: '', chinese: '', example: '', exampleChinese: ''
  })
}

function addDialog(): void {
  store.addListItem('dialogs', {
    id: tempId('dialog'),
    scene: '',
    lines: [{ id: tempId('line'), speaker: '先生', japanese: '', chinese: '' }]
  })
}

function addDialogLine(dialogIndex: number): void {
  const dialog = draft.value?.detail?.dialogs[dialogIndex]
  if (dialog === undefined) {
    return
  }
  dialog.lines.push({ id: tempId('line'), speaker: '学生', japanese: '', chinese: '' })
  // 配列の要素そのものを足したので、保存対象として印を付ける
  store.touchDraft?.()
}

function addSynonym(): void {
  store.addListItem('synonyms', {
    id: tempId('synonym'), heading: '', reading: '', chinese: '', shared: '', difference: '',
    usage: '', interchangeable: 'SOMETIMES', interchangeNote: '', exampleJapanese: '', exampleChinese: ''
  })
}

function addCaution(): void {
  store.addListItem('cautions', {
    id: tempId('caution'), kind: 'PARTICLE', title: '', wrong: '', correct: '', reason: ''
  })
}

function addConjugation(): void {
  store.addListItem('conjugations', { id: tempId('conj'), form: '', value: '', example: '' })
}

function addCollocation(): void {
  store.addListItem('collocations', { id: tempId('coll'), expression: '', reading: '', chinese: '', usage: '' })
}

function addRelated(): void {
  store.addListItem('relatedWords', { id: tempId('rel'), relation: '類義語', heading: '', reading: '', chinese: '' })
}

function addUsageNote(): void {
  store.addListItem('usageNotes', {
    id: tempId('usage'), register: 'どちらも', politeness: '普通', audience: '', note: '', senseNumber: null
  })
}

function addPractice(): void {
  store.addListItem('practices', {
    id: tempId('practice'), kind: 'PARTICLE', question: '', questionChinese: '', choices: ['', '', ''],
    answer: '', explanation: '', freeWriting: false, demoFeedback: null
  })
}

/** 発音の欄を作る／消す（アクセントは未確認のままにできる）。 */
function enablePronunciation(): void {
  store.setDetailPart({
    pronunciation: { reading: draft.value?.reading ?? '', accentType: null, accentNotation: null, hint: '', hasAudioSample: false }
  })
}

function enableMemoryHint(): void {
  store.setDetailPart({ memoryHint: { hint: '', basis: '' } })
}

function enableTransitivityPair(): void {
  store.setDetailPart({
    transitivityPair: { intransitive: '', transitive: '', particleNote: '', intransitiveExample: '', transitiveExample: '' }
  })
}

/* ---------- 保存とプレビュー ---------- */

const saving = computed(() => store.saveState === 'SAVING')

async function save(): Promise<void> {
  await store.saveDraft()
}

/** 一覧へ戻る（未保存なら確認する）。 */
function close(): void {
  if (store.draftDirty) {
    leavePrompt.value = '保存していない変更があります。破棄して一覧へ戻りますか？'
    return
  }
  store.closeEditor()
  emit('close')
}

function discardAndClose(): void {
  leavePrompt.value = ''
  store.closeEditor()
  emit('close')
}

function cancelLeave(): void {
  leavePrompt.value = ''
}

/** 学習画面の見た目でプレビューする（下書きのまま）。 */
function preview(): void {
  emit('preview', { draft: true })
}

/** 競合のときに、自分の入力を残したまま相手の版を確認する。 */
function keepMine(): void {
  store.resolveConflictAsMine()
}

function collectionsText(): string[] {
  return draft.value === null ? [] : draft.value.collections.map((collection) => collectionLabel(collection))
}
</script>

<template>
  <div class="jp-demo jp-demo-editor">
    <p v-if="draft === null" class="alert alert--danger" data-demo-editor-missing>
      編集する単語が見つかりませんでした。一覧へ戻ってください。
    </p>

    <template v-else>
      <!-- 上部：単語・状態・操作 -->
      <div class="jp-demo-editor__head" data-demo-editor-head>
        <div style="min-width: 0">
          <h1 class="jp-demo-editor__title" data-demo-editor-word>{{ draft.heading }}</h1>
          <div class="jp-demo-editor__sub">
            {{ draft.reading }} ／ {{ draft.partOfSpeech }}
            <template v-if="draft.jlpt"> ／ {{ draft.jlpt }}（例）</template>
          </div>
        </div>
        <span class="jp-demo-status">
          <span class="badge" :class="DETAIL_STATUS_BADGES[draft.detailStatus]">
            {{ DETAIL_STATUS_LABELS[draft.detailStatus] }}
          </span>
          <span v-if="draft.manuallyEdited" class="jp-demo-meta">人が手で直した内容を含みます</span>
        </span>
        <div class="jp-demo__actions">
          <button type="button" class="btn btn--secondary btn--sm" data-demo-preview @click="preview">
            <AppIcon name="eye" size="sm" /> 学習画面で確認
          </button>
          <button type="button" class="btn btn--secondary btn--sm" data-demo-editor-cancel @click="close">
            キャンセル
          </button>
          <button
            type="button" class="btn btn--primary btn--sm" data-demo-editor-save
            :disabled="saving" @click="save"
          >
            <AppIcon name="check" size="sm" /> {{ saving ? '保存しています…' : '保存' }}
          </button>
        </div>
      </div>

      <!-- 保存の結果（成功・失敗・衝突） -->
      <p
        v-if="store.saveState === 'SAVED'" class="alert alert--success" data-demo-save-saved
      >
        {{ store.saveMessage }}
      </p>
      <p
        v-else-if="store.saveState === 'FAILED'" class="alert alert--danger" data-demo-save-failed
      >
        {{ store.saveMessage }}
      </p>
      <div v-else-if="store.saveState === 'CONFLICT'" class="alert alert--warning" data-demo-save-conflict>
        <p style="margin: 0 0 var(--sp-2)">{{ store.saveMessage }}</p>
        <p style="margin: 0 0 var(--sp-2)" class="jp-demo-meta">{{ store.conflictNote }}</p>
        <div class="jp-demo-array-actions">
          <button type="button" class="jp-demo-linkbtn" data-demo-conflict-keep @click="keepMine">
            いまの入力で続ける
          </button>
          <button type="button" class="jp-demo-linkbtn" data-demo-conflict-reload @click="store.openEditor(draft.id)">
            保存済みの内容を読み直す
          </button>
        </div>
      </div>

      <!-- 未保存のまま離れようとしたとき -->
      <div v-if="leavePrompt !== ''" class="alert alert--warning" data-demo-leave-prompt>
        <p style="margin: 0 0 var(--sp-2)">{{ leavePrompt }}</p>
        <div class="jp-demo-array-actions">
          <button type="button" class="jp-demo-linkbtn" data-demo-leave-cancel @click="cancelLeave">編集を続ける</button>
          <button type="button" class="jp-demo-linkbtn is-danger" data-demo-leave-discard @click="discardAndClose">
            破棄して戻る
          </button>
        </div>
      </div>

      <!-- AI の補助（人の入力を自動で上書きしない） -->
      <section class="jp-demo-panel">
        <div class="jp-demo-panel__body" style="padding-top: var(--sp-3)">
          <div class="jp-demo-array-actions">
            <button type="button" class="btn btn--secondary btn--sm" data-demo-supplement @click="store.startGeneration(draft.id)">
              <AppIcon name="copy" size="sm" /> AI で補足案を作る
            </button>
            <button type="button" class="btn btn--secondary btn--sm" data-demo-regenerate @click="store.startGeneration(draft.id)">
              <AppIcon name="rotate" size="sm" /> 詳細情報を再生成
            </button>
            <span class="jp-demo-meta">
              案を採用するかどうかは人が選びます（いまの入力は自動で上書きしません）。
            </span>
          </div>
          <p v-if="store.runningJobs.length > 0" class="jp-demo-meta" data-demo-job-running>
            生成しています…（{{ store.runningJobs.length }} 件）しばらくすると結果が入ります。
          </p>
        </div>
      </section>

      <div class="jp-demo-editor__layout">
        <!-- 左：欄のナビ -->
        <nav class="jp-demo-nav" data-demo-editor-nav aria-label="編集する欄">
          <button
            v-for="section in SECTIONS" :key="section.key"
            type="button" class="jp-demo-nav__item" :class="{ 'is-active': activeSection === section.key }"
            :data-demo-editor-nav-item="section.key" @click="activeSection = section.key"
          >
            {{ section.label }}
            <span v-if="sectionCount(section) !== null" class="jp-demo-nav__count">{{ sectionCount(section) }}</span>
          </button>
        </nav>

        <div class="jp-demo-editor__body">
          <!-- 1. 基本情報 -->
          <section v-if="activeSection === 'basic'" class="jp-demo-section" data-demo-editor-section="basic">
            <h2 class="jp-demo-section__title">基本情報</h2>
            <div class="jp-demo-fieldgrid">
              <label class="filter-item">
                <span class="filter-item__label">単語</span>
                <input
                  class="input" :value="draft.heading" data-demo-edit-heading
                  @input="store.updateDraftBasic({ heading: ($event.target as HTMLInputElement).value })"
                >
              </label>
              <label class="filter-item">
                <span class="filter-item__label">読み</span>
                <input
                  class="input" :value="draft.reading" data-demo-edit-reading
                  @input="store.updateDraftBasic({ reading: ($event.target as HTMLInputElement).value })"
                >
              </label>
              <label class="filter-item">
                <span class="filter-item__label">品詞</span>
                <select
                  class="select" :value="draft.partOfSpeech" data-demo-edit-part
                  @change="store.updateDraftBasic({ partOfSpeech: ($event.target as HTMLSelectElement).value as typeof draft.partOfSpeech })"
                >
                  <option v-for="part in PART_OF_SPEECH_OPTIONS" :key="part" :value="part">{{ part }}</option>
                </select>
              </label>
              <label class="filter-item">
                <span class="filter-item__label">JLPT（例示値）</span>
                <input
                  class="input" :value="draft.jlpt ?? ''" placeholder="未確認なら空" data-demo-edit-jlpt
                  @input="store.updateDraftBasic({ jlpt: ($event.target as HTMLInputElement).value || null })"
                >
              </label>
              <label class="filter-item">
                <span class="filter-item__label">中国語の核心的な意味</span>
                <input
                  class="input" :value="draft.chineseMeaning" data-demo-edit-chinese
                  @input="store.updateDraftBasic({ chineseMeaning: ($event.target as HTMLInputElement).value })"
                >
              </label>
              <label class="filter-item">
                <span class="filter-item__label">一言の核心的な意味（見出しの下に出ます）</span>
                <input
                  class="input" :value="draft.detail?.coreMeaning ?? ''" data-demo-edit-core
                  @input="store.updateDraftDescription({ coreMeaning: ($event.target as HTMLInputElement).value })"
                >
              </label>
            </div>

            <p v-if="sameHeading.length > 0" class="alert alert--warning" data-demo-same-heading>
              同じ表記で読みが違う単語があります（
              <template v-for="(other, index) in sameHeading" :key="other.id">
                {{ index > 0 ? '、' : '' }}{{ other.heading }}（{{ other.reading }}）
              </template>
              ）。読みが違えば<strong>別の単語</strong>なので、まとめずに別々に登録します。
            </p>

            <!-- 収録は学習内容と混ざらないよう別のまとまりにする -->
            <h3 class="jp-demo-section__title">収録（教材のどこに載っているか）</h3>
            <p class="jp-demo-section__hint">この欄は学習の説明ではなく、教材との対応です。</p>
            <ul data-demo-editor-collections>
              <li v-for="collection in collectionsText()" :key="collection">{{ collection }}</li>
            </ul>
          </section>

          <!-- 2. 語義・説明 -->
          <section v-else-if="activeSection === 'meaning'" class="jp-demo-section" data-demo-editor-section="meaning">
            <h2 class="jp-demo-section__title">語義・説明</h2>
            <div class="jp-demo-fieldgrid">
              <label class="filter-item" style="grid-column: 1 / -1">
                <span class="filter-item__label">日本語の説明</span>
                <textarea
                  class="textarea" rows="3" data-demo-edit-description-ja
                  :value="draft.detail?.descriptionJa ?? ''"
                  @input="store.updateDraftDescription({ descriptionJa: ($event.target as HTMLTextAreaElement).value })"
                ></textarea>
              </label>
              <label class="filter-item" style="grid-column: 1 / -1">
                <span class="filter-item__label">中国語の説明</span>
                <textarea
                  class="textarea" rows="3" data-demo-edit-description-zh
                  :value="draft.detail?.descriptionZh ?? ''"
                  @input="store.updateDraftDescription({ descriptionZh: ($event.target as HTMLTextAreaElement).value })"
                ></textarea>
              </label>
            </div>

            <div class="jp-demo-array-actions">
              <span class="jp-demo-section__hint">よく使う意味を上に置きます（番号を振り直せます）。</span>
              <button type="button" class="jp-demo-linkbtn" data-demo-add-sense @click="addSense">＋ 語義を追加</button>
            </div>

            <article
              v-for="(sense, index) in draft.detail?.senses ?? []" :key="sense.id"
              class="jp-demo-item" :data-demo-sense="index"
            >
              <div class="jp-demo-item__head">
                <span class="jp-demo-item__no">{{ sense.number }}</span>
                <span class="jp-demo-pair__label">語義 {{ sense.number }}</span>
                <span class="jp-demo-item__actions">
                  <button type="button" class="jp-demo-linkbtn" :disabled="index === 0" @click="store.moveListItem('senses', index, -1)">↑</button>
                  <button type="button" class="jp-demo-linkbtn" :disabled="index === (draft.detail?.senses.length ?? 0) - 1" @click="store.moveListItem('senses', index, 1)">↓</button>
                  <button type="button" class="jp-demo-linkbtn is-danger" data-demo-remove-sense @click="store.removeListItem('senses', index)">削除</button>
                </span>
              </div>
              <label class="filter-item">
                <span class="filter-item__label">意味（日本語）</span>
                <input class="input" :value="sense.japanese" @input="store.updateListItem('senses', index, { japanese: ($event.target as HTMLInputElement).value })">
              </label>
              <label class="filter-item">
                <span class="filter-item__label">意味（中国語）</span>
                <input class="input" :value="sense.chinese" @input="store.updateListItem('senses', index, { chinese: ($event.target as HTMLInputElement).value })">
              </label>
              <div class="jp-demo-fieldgrid">
                <label class="filter-item">
                  <span class="filter-item__label">使う場面</span>
                  <input class="input" :value="sense.context" @input="store.updateListItem('senses', index, { context: ($event.target as HTMLInputElement).value })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">文体</span>
                  <input class="input" :value="sense.style" @input="store.updateListItem('senses', index, { style: ($event.target as HTMLInputElement).value })">
                </label>
              </div>
            </article>
          </section>

          <!-- 3. 文型・助詞 -->
          <section v-else-if="activeSection === 'patterns'" class="jp-demo-section" data-demo-editor-section="patterns">
            <h2 class="jp-demo-section__title">文型・助詞</h2>
            <p class="jp-demo-section__hint">
              助詞は <code>**</code> で囲むと、学習画面で強調されます（例: <code>人**に**相談する</code>）。
            </p>
            <div class="jp-demo-array-actions">
              <button type="button" class="jp-demo-linkbtn" data-demo-add-pattern @click="addPattern">＋ 文型を追加</button>
            </div>
            <article
              v-for="(pattern, index) in draft.detail?.patterns ?? []" :key="pattern.id"
              class="jp-demo-item" :data-demo-pattern="index"
            >
              <div class="jp-demo-item__head">
                <span class="jp-demo-item__no">{{ index + 1 }}</span>
                <span class="jp-demo-item__actions">
                  <button type="button" class="jp-demo-linkbtn" :disabled="index === 0" @click="store.moveListItem('patterns', index, -1)">↑</button>
                  <button type="button" class="jp-demo-linkbtn" @click="store.moveListItem('patterns', index, 1)">↓</button>
                  <button type="button" class="jp-demo-linkbtn is-danger" @click="store.removeListItem('patterns', index)">削除</button>
                </span>
              </div>
              <label class="filter-item">
                <span class="filter-item__label">文型（助詞を ** で囲む）</span>
                <input class="input" :value="pattern.pattern" @input="store.updateListItem('patterns', index, { pattern: ($event.target as HTMLInputElement).value })">
              </label>
              <div class="jp-demo-fieldgrid">
                <label class="filter-item">
                  <span class="filter-item__label">読み</span>
                  <input class="input" :value="pattern.reading" @input="store.updateListItem('patterns', index, { reading: ($event.target as HTMLInputElement).value })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">中国語での説明</span>
                  <input class="input" :value="pattern.chinese" @input="store.updateListItem('patterns', index, { chinese: ($event.target as HTMLInputElement).value })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">例文</span>
                  <input class="input" :value="pattern.example" @input="store.updateListItem('patterns', index, { example: ($event.target as HTMLInputElement).value })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">例文の中国語訳</span>
                  <input class="input" :value="pattern.exampleChinese" @input="store.updateListItem('patterns', index, { exampleChinese: ($event.target as HTMLInputElement).value })">
                </label>
              </div>
            </article>
          </section>

          <!-- 4. 例文・会話 -->
          <section v-else-if="activeSection === 'examples'" class="jp-demo-section" data-demo-editor-section="examples">
            <h2 class="jp-demo-section__title">例文</h2>
            <div class="jp-demo-array-actions">
              <button type="button" class="jp-demo-linkbtn" data-demo-add-example @click="addExample">＋ 例文を追加</button>
            </div>
            <article
              v-for="(example, index) in draft.detail?.examples ?? []" :key="example.id"
              class="jp-demo-item" :data-demo-example="index"
            >
              <div class="jp-demo-item__head">
                <span class="jp-demo-item__no">{{ index + 1 }}</span>
                <span class="jp-demo-item__actions">
                  <button type="button" class="jp-demo-linkbtn" :disabled="index === 0" @click="store.moveListItem('examples', index, -1)">↑</button>
                  <button type="button" class="jp-demo-linkbtn" @click="store.moveListItem('examples', index, 1)">↓</button>
                  <button type="button" class="jp-demo-linkbtn is-danger" data-demo-remove-example @click="store.removeListItem('examples', index)">削除</button>
                </span>
              </div>
              <label class="filter-item">
                <span class="filter-item__label">例文（日本語）</span>
                <input class="input" :value="example.japanese" @input="store.updateListItem('examples', index, { japanese: ($event.target as HTMLInputElement).value })">
              </label>
              <div class="jp-demo-fieldgrid">
                <label class="filter-item">
                  <span class="filter-item__label">読み</span>
                  <input class="input" :value="example.reading" @input="store.updateListItem('examples', index, { reading: ($event.target as HTMLInputElement).value })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">中国語訳</span>
                  <input class="input" :value="example.chinese" @input="store.updateListItem('examples', index, { chinese: ($event.target as HTMLInputElement).value })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">種類</span>
                  <select class="select" :value="example.level" @change="store.updateListItem('examples', index, { level: ($event.target as HTMLSelectElement).value })">
                    <option value="BASIC">やさしい例文</option>
                    <option value="APPLIED">応用例文</option>
                  </select>
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">対応する語義番号</span>
                  <input
                    class="input" type="number" min="1" :value="example.senseNumber ?? ''"
                    @input="store.updateListItem('examples', index, { senseNumber: Number(($event.target as HTMLInputElement).value) || null })"
                  >
                </label>
              </div>
            </article>

            <h2 class="jp-demo-section__title">会話（2〜4 文）</h2>
            <p class="jp-demo-section__hint">場面と役を書いておくと、学習画面で役ごとに表示されます。</p>
            <div class="jp-demo-array-actions">
              <button type="button" class="jp-demo-linkbtn" data-demo-add-dialog @click="addDialog">＋ 会話を追加</button>
            </div>
            <article
              v-for="(dialog, index) in draft.detail?.dialogs ?? []" :key="dialog.id"
              class="jp-demo-item" :data-demo-dialog="index"
            >
              <div class="jp-demo-item__head">
                <span class="jp-demo-item__no">{{ index + 1 }}</span>
                <span class="jp-demo-item__actions">
                  <button type="button" class="jp-demo-linkbtn" @click="store.removeListItem('dialogs', index)">削除</button>
                </span>
              </div>
              <label class="filter-item">
                <span class="filter-item__label">場面</span>
                <input class="input" :value="dialog.scene" placeholder="例: 職員室で先生に相談する" @input="store.updateListItem('dialogs', index, { scene: ($event.target as HTMLInputElement).value })">
              </label>
              <div v-for="(line, lineIndex) in dialog.lines" :key="line.id" class="jp-demo-fieldgrid">
                <label class="filter-item">
                  <span class="filter-item__label">役</span>
                  <input
                    class="input" :value="line.speaker"
                    @input="store.updateListItem('dialogs', index, { lines: dialog.lines.map((entry, i) => i === lineIndex ? { ...entry, speaker: ($event.target as HTMLInputElement).value } : entry) })"
                  >
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">せりふ</span>
                  <input
                    class="input" :value="line.japanese"
                    @input="store.updateListItem('dialogs', index, { lines: dialog.lines.map((entry, i) => i === lineIndex ? { ...entry, japanese: ($event.target as HTMLInputElement).value } : entry) })"
                  >
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">中国語訳</span>
                  <input
                    class="input" :value="line.chinese"
                    @input="store.updateListItem('dialogs', index, { lines: dialog.lines.map((entry, i) => i === lineIndex ? { ...entry, chinese: ($event.target as HTMLInputElement).value } : entry) })"
                  >
                </label>
              </div>
              <button type="button" class="jp-demo-linkbtn" @click="addDialogLine(index)">＋ せりふを追加</button>
            </article>
          </section>

          <!-- 5. 類義語・使い分け -->
          <section v-else-if="activeSection === 'synonyms'" class="jp-demo-section" data-demo-editor-section="synonyms">
            <h2 class="jp-demo-section__title">類義語・使い分け</h2>
            <p class="jp-demo-section__hint">
              入れ替えられるかどうかは「はい／いいえ」で決めず、どの場合に入れ替えられるかを書きます。
            </p>
            <div class="jp-demo-array-actions">
              <button type="button" class="jp-demo-linkbtn" data-demo-add-synonym @click="addSynonym">＋ 比べる言葉を追加</button>
            </div>
            <article
              v-for="(synonym, index) in draft.detail?.synonyms ?? []" :key="synonym.id"
              class="jp-demo-item" :data-demo-synonym="index"
            >
              <div class="jp-demo-item__head">
                <span class="jp-demo-item__no">{{ index + 1 }}</span>
                <span class="jp-demo-item__actions">
                  <button type="button" class="jp-demo-linkbtn" @click="store.removeListItem('synonyms', index)">削除</button>
                </span>
              </div>
              <div class="jp-demo-fieldgrid">
                <label class="filter-item">
                  <span class="filter-item__label">比べる言葉</span>
                  <input class="input" :value="synonym.heading" @input="store.updateListItem('synonyms', index, { heading: ($event.target as HTMLInputElement).value })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">読み</span>
                  <input class="input" :value="synonym.reading" @input="store.updateListItem('synonyms', index, { reading: ($event.target as HTMLInputElement).value })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">中国語の意味</span>
                  <input class="input" :value="synonym.chinese" @input="store.updateListItem('synonyms', index, { chinese: ($event.target as HTMLInputElement).value })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">入れ替え</span>
                  <select class="select" :value="synonym.interchangeable" @change="store.updateListItem('synonyms', index, { interchangeable: ($event.target as HTMLSelectElement).value })">
                    <option value="YES">入れ替えられる</option>
                    <option value="SOMETIMES">場合による</option>
                    <option value="NO">入れ替えられない</option>
                  </select>
                </label>
              </div>
              <label class="filter-item">
                <span class="filter-item__label">共通する意味</span>
                <input class="input" :value="synonym.shared" @input="store.updateListItem('synonyms', index, { shared: ($event.target as HTMLInputElement).value })">
              </label>
              <label class="filter-item">
                <span class="filter-item__label">違うところ</span>
                <input class="input" :value="synonym.difference" @input="store.updateListItem('synonyms', index, { difference: ($event.target as HTMLInputElement).value })">
              </label>
              <label class="filter-item">
                <span class="filter-item__label">使う場面</span>
                <input class="input" :value="synonym.usage" @input="store.updateListItem('synonyms', index, { usage: ($event.target as HTMLInputElement).value })">
              </label>
              <label class="filter-item">
                <span class="filter-item__label">入れ替えの補足</span>
                <input class="input" :value="synonym.interchangeNote" @input="store.updateListItem('synonyms', index, { interchangeNote: ($event.target as HTMLInputElement).value })">
              </label>
              <div class="jp-demo-fieldgrid">
                <label class="filter-item">
                  <span class="filter-item__label">比べる例文</span>
                  <input class="input" :value="synonym.exampleJapanese" @input="store.updateListItem('synonyms', index, { exampleJapanese: ($event.target as HTMLInputElement).value })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">例文の中国語訳</span>
                  <input class="input" :value="synonym.exampleChinese" @input="store.updateListItem('synonyms', index, { exampleChinese: ($event.target as HTMLInputElement).value })">
                </label>
              </div>
            </article>
          </section>

          <!-- 6. 間違えやすいポイント -->
          <section v-else-if="activeSection === 'cautions'" class="jp-demo-section" data-demo-editor-section="cautions">
            <h2 class="jp-demo-section__title">間違えやすいポイント</h2>
            <p class="jp-demo-section__hint">
              種類を分けて書きます（文法の誤り／この場面では不自然／意味が違う／助詞の使い方）。
              すべてを「誤り」にしないでください。
            </p>
            <div class="jp-demo-array-actions">
              <button type="button" class="jp-demo-linkbtn" data-demo-add-caution @click="addCaution">＋ 注意を追加</button>
            </div>
            <article
              v-for="(caution, index) in draft.detail?.cautions ?? []" :key="caution.id"
              class="jp-demo-item" :data-demo-caution="index"
            >
              <div class="jp-demo-item__head">
                <span class="jp-demo-item__no">{{ index + 1 }}</span>
                <span class="jp-demo-item__actions">
                  <button type="button" class="jp-demo-linkbtn" @click="store.removeListItem('cautions', index)">削除</button>
                </span>
              </div>
              <div class="jp-demo-fieldgrid">
                <label class="filter-item">
                  <span class="filter-item__label">種類</span>
                  <select class="select" :value="caution.kind" @change="store.updateListItem('cautions', index, { kind: ($event.target as HTMLSelectElement).value as DemoCautionKind })">
                    <option v-for="(label, kind) in CAUTION_KIND_LABELS" :key="kind" :value="kind">{{ label }}</option>
                  </select>
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">見出し</span>
                  <input class="input" :value="caution.title" @input="store.updateListItem('cautions', index, { title: ($event.target as HTMLInputElement).value })">
                </label>
              </div>
              <label class="filter-item">
                <span class="filter-item__label">誤った言い方</span>
                <input class="input" :value="caution.wrong" @input="store.updateListItem('cautions', index, { wrong: ($event.target as HTMLInputElement).value })">
              </label>
              <label class="filter-item">
                <span class="filter-item__label">自然な言い方</span>
                <input class="input" :value="caution.correct" @input="store.updateListItem('cautions', index, { correct: ($event.target as HTMLInputElement).value })">
              </label>
              <label class="filter-item">
                <span class="filter-item__label">なぜ違うか</span>
                <input class="input" :value="caution.reason" @input="store.updateListItem('cautions', index, { reason: ($event.target as HTMLInputElement).value })">
              </label>
            </article>
          </section>

          <!-- 7. 活用・自他動詞 -->
          <section v-else-if="activeSection === 'form'" class="jp-demo-section" data-demo-editor-section="form">
            <h2 class="jp-demo-section__title">活用・自他動詞</h2>
            <p class="jp-demo-section__hint">
              {{ isVerb ? '動詞なので活用を入れられます。' : `「${draft.partOfSpeech}」なので、動詞の活用はふつう入れません。` }}
              確かめられない活用は作らず、空のままにしてください。
            </p>

            <div class="jp-demo-array-actions">
              <button type="button" class="jp-demo-linkbtn" data-demo-add-conjugation :disabled="!isVerb" @click="addConjugation">
                ＋ 活用を追加
              </button>
              <button type="button" class="jp-demo-linkbtn" @click="enableTransitivityPair">自他動詞の対応を入れる</button>
            </div>

            <article
              v-for="(conjugation, index) in draft.detail?.conjugations ?? []" :key="conjugation.id"
              class="jp-demo-item" :data-demo-conjugation="index"
            >
              <div class="jp-demo-item__head">
                <span class="jp-demo-item__no">{{ index + 1 }}</span>
                <span class="jp-demo-item__actions">
                  <button type="button" class="jp-demo-linkbtn" @click="store.removeListItem('conjugations', index)">削除</button>
                </span>
              </div>
              <div class="jp-demo-fieldgrid">
                <label class="filter-item">
                  <span class="filter-item__label">形の名前</span>
                  <input class="input" :value="conjugation.form" @input="store.updateListItem('conjugations', index, { form: ($event.target as HTMLInputElement).value })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">活用した形</span>
                  <input class="input" :value="conjugation.value" @input="store.updateListItem('conjugations', index, { value: ($event.target as HTMLInputElement).value })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">短い例文</span>
                  <input class="input" :value="conjugation.example" @input="store.updateListItem('conjugations', index, { example: ($event.target as HTMLInputElement).value })">
                </label>
              </div>
            </article>

            <template v-if="draft.detail?.transitivityPair">
              <h3 class="jp-demo-section__title">自他動詞の対応</h3>
              <div class="jp-demo-fieldgrid" data-demo-transitivity>
                <label class="filter-item">
                  <span class="filter-item__label">自動詞</span>
                  <input class="input" :value="draft.detail.transitivityPair.intransitive" @input="store.setDetailPart({ transitivityPair: { ...draft.detail!.transitivityPair!, intransitive: ($event.target as HTMLInputElement).value } })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">他動詞</span>
                  <input class="input" :value="draft.detail.transitivityPair.transitive" @input="store.setDetailPart({ transitivityPair: { ...draft.detail!.transitivityPair!, transitive: ($event.target as HTMLInputElement).value } })">
                </label>
                <label class="filter-item" style="grid-column: 1 / -1">
                  <span class="filter-item__label">助詞の違い</span>
                  <input class="input" :value="draft.detail.transitivityPair.particleNote" @input="store.setDetailPart({ transitivityPair: { ...draft.detail!.transitivityPair!, particleNote: ($event.target as HTMLInputElement).value } })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">例文（自動詞）</span>
                  <input class="input" :value="draft.detail.transitivityPair.intransitiveExample" @input="store.setDetailPart({ transitivityPair: { ...draft.detail!.transitivityPair!, intransitiveExample: ($event.target as HTMLInputElement).value } })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">例文（他動詞）</span>
                  <input class="input" :value="draft.detail.transitivityPair.transitiveExample" @input="store.setDetailPart({ transitivityPair: { ...draft.detail!.transitivityPair!, transitiveExample: ($event.target as HTMLInputElement).value } })">
                </label>
              </div>
            </template>
          </section>

          <!-- 8. 発音・アクセント -->
          <section v-else-if="activeSection === 'pronunciation'" class="jp-demo-section" data-demo-editor-section="pronunciation">
            <h2 class="jp-demo-section__title">発音・アクセント</h2>
            <p class="jp-demo-section__hint">
              アクセントが<strong>確認できていないときは空のまま</strong>にしてください（推測で作らない）。
              学習画面では「未確認」と表示されます。音声は再生の状態だけを示します。
            </p>

            <div v-if="draft.detail?.pronunciation" class="jp-demo-fieldgrid" data-demo-pronunciation>
              <label class="filter-item">
                <span class="filter-item__label">読み</span>
                <input class="input" :value="draft.detail.pronunciation.reading" @input="store.setDetailPart({ pronunciation: { ...draft.detail!.pronunciation!, reading: ($event.target as HTMLInputElement).value } })">
              </label>
              <label class="filter-item">
                <span class="filter-item__label">アクセント型（未確認なら空）</span>
                <input
                  class="input" type="number" min="0" :value="draft.detail.pronunciation.accentType ?? ''"
                  @input="store.setDetailPart({ pronunciation: { ...draft.detail!.pronunciation!, accentType: ($event.target as HTMLInputElement).value === '' ? null : Number(($event.target as HTMLInputElement).value) } })"
                >
              </label>
              <label class="filter-item">
                <span class="filter-item__label">アクセント表記</span>
                <input class="input" :value="draft.detail.pronunciation.accentNotation ?? ''" @input="store.setDetailPart({ pronunciation: { ...draft.detail!.pronunciation!, accentNotation: ($event.target as HTMLInputElement).value || null } })">
              </label>
              <label class="filter-item" style="grid-column: 1 / -1">
                <span class="filter-item__label">発音のヒント</span>
                <input class="input" :value="draft.detail.pronunciation.hint" @input="store.setDetailPart({ pronunciation: { ...draft.detail!.pronunciation!, hint: ($event.target as HTMLInputElement).value } })">
              </label>
            </div>
            <p v-else class="jp-hint">
              発音の情報はまだありません。
              <button type="button" class="jp-demo-linkbtn" data-demo-add-pronunciation @click="enablePronunciation">
                発音の欄を作る
              </button>
            </p>
          </section>

          <!-- 9. コロケーション・関連語・使用場面 -->
          <section v-else-if="activeSection === 'collocations'" class="jp-demo-section" data-demo-editor-section="collocations">
            <h2 class="jp-demo-section__title">コロケーション（よく使う言い回し）</h2>
            <div class="jp-demo-array-actions">
              <button type="button" class="jp-demo-linkbtn" data-demo-add-collocation @click="addCollocation">＋ 言い回しを追加</button>
            </div>
            <article
              v-for="(collocation, index) in draft.detail?.collocations ?? []" :key="collocation.id"
              class="jp-demo-item" :data-demo-collocation="index"
            >
              <div class="jp-demo-item__head">
                <span class="jp-demo-item__no">{{ index + 1 }}</span>
                <span class="jp-demo-item__actions">
                  <button type="button" class="jp-demo-linkbtn" @click="store.removeListItem('collocations', index)">削除</button>
                </span>
              </div>
              <div class="jp-demo-fieldgrid">
                <label class="filter-item">
                  <span class="filter-item__label">言い回し</span>
                  <input class="input" :value="collocation.expression" @input="store.updateListItem('collocations', index, { expression: ($event.target as HTMLInputElement).value })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">読み</span>
                  <input class="input" :value="collocation.reading" @input="store.updateListItem('collocations', index, { reading: ($event.target as HTMLInputElement).value })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">中国語の意味</span>
                  <input class="input" :value="collocation.chinese" @input="store.updateListItem('collocations', index, { chinese: ($event.target as HTMLInputElement).value })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">使う場面</span>
                  <input class="input" :value="collocation.usage" @input="store.updateListItem('collocations', index, { usage: ($event.target as HTMLInputElement).value })">
                </label>
              </div>
            </article>

            <h2 class="jp-demo-section__title">関連語</h2>
            <div class="jp-demo-array-actions">
              <button type="button" class="jp-demo-linkbtn" data-demo-add-related @click="addRelated">＋ 関連語を追加</button>
            </div>
            <article
              v-for="(related, index) in draft.detail?.relatedWords ?? []" :key="related.id"
              class="jp-demo-item" :data-demo-related="index"
            >
              <div class="jp-demo-item__head">
                <span class="jp-demo-item__no">{{ index + 1 }}</span>
                <span class="jp-demo-item__actions">
                  <button type="button" class="jp-demo-linkbtn" @click="store.removeListItem('relatedWords', index)">削除</button>
                </span>
              </div>
              <div class="jp-demo-fieldgrid">
                <label class="filter-item">
                  <span class="filter-item__label">関係</span>
                  <select class="select" :value="related.relation" @change="store.updateListItem('relatedWords', index, { relation: ($event.target as HTMLSelectElement).value })">
                    <option value="類義語">類義語</option>
                    <option value="対義語">対義語</option>
                    <option value="間違えやすい">間違えやすい</option>
                    <option value="同じ読み">同じ読み</option>
                  </select>
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">見出し</span>
                  <input class="input" :value="related.heading" @input="store.updateListItem('relatedWords', index, { heading: ($event.target as HTMLInputElement).value })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">読み</span>
                  <input class="input" :value="related.reading" @input="store.updateListItem('relatedWords', index, { reading: ($event.target as HTMLInputElement).value })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">中国語の意味</span>
                  <input class="input" :value="related.chinese" @input="store.updateListItem('relatedWords', index, { chinese: ($event.target as HTMLInputElement).value })">
                </label>
              </div>
            </article>

            <h2 class="jp-demo-section__title">使用場面</h2>
            <p class="jp-demo-section__hint">特定の語義について書くときは、語義番号を添えます。</p>
            <div class="jp-demo-array-actions">
              <button type="button" class="jp-demo-linkbtn" data-demo-add-usage @click="addUsageNote">＋ 使用場面を追加</button>
            </div>
            <article
              v-for="(note, index) in draft.detail?.usageNotes ?? []" :key="note.id"
              class="jp-demo-item" :data-demo-usage="index"
            >
              <div class="jp-demo-item__head">
                <span class="jp-demo-item__no">{{ index + 1 }}</span>
                <span class="jp-demo-item__actions">
                  <button type="button" class="jp-demo-linkbtn" @click="store.removeListItem('usageNotes', index)">削除</button>
                </span>
              </div>
              <div class="jp-demo-fieldgrid">
                <label class="filter-item">
                  <span class="filter-item__label">話し言葉／書き言葉</span>
                  <select class="select" :value="note.register" @change="store.updateListItem('usageNotes', index, { register: ($event.target as HTMLSelectElement).value })">
                    <option value="話し言葉">話し言葉</option>
                    <option value="書き言葉">書き言葉</option>
                    <option value="どちらも">どちらも</option>
                  </select>
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">丁寧さ</span>
                  <select class="select" :value="note.politeness" @change="store.updateListItem('usageNotes', index, { politeness: ($event.target as HTMLSelectElement).value })">
                    <option value="カジュアル">カジュアル</option>
                    <option value="普通">普通</option>
                    <option value="丁寧">丁寧</option>
                  </select>
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">使う相手・場面</span>
                  <input class="input" :value="note.audience" @input="store.updateListItem('usageNotes', index, { audience: ($event.target as HTMLInputElement).value })">
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">語義番号（特定しないなら空）</span>
                  <input
                    class="input" type="number" min="1" :value="note.senseNumber ?? ''
                    " @input="store.updateListItem('usageNotes', index, { senseNumber: Number(($event.target as HTMLInputElement).value) || null })"
                  >
                </label>
              </div>
              <label class="filter-item">
                <span class="filter-item__label">補足</span>
                <input class="input" :value="note.note" @input="store.updateListItem('usageNotes', index, { note: ($event.target as HTMLInputElement).value })">
              </label>
            </article>
          </section>

          <!-- 10. 記憶のヒント -->
          <section v-else-if="activeSection === 'memory'" class="jp-demo-section" data-demo-editor-section="memory">
            <h2 class="jp-demo-section__title">記憶のヒント</h2>
            <p class="jp-demo-section__hint">
              任意の一言です。<strong>語源ではありません</strong>（推測の由来は書かないでください）。
              漢字の形のイメージや場面の連想を書きます。
            </p>
            <div v-if="draft.detail?.memoryHint" class="jp-demo-fieldgrid" data-demo-memory>
              <label class="filter-item" style="grid-column: 1 / -1">
                <span class="filter-item__label">一言の覚え方</span>
                <input class="input" :value="draft.detail.memoryHint.hint" @input="store.setDetailPart({ memoryHint: { ...draft.detail!.memoryHint!, hint: ($event.target as HTMLInputElement).value } })">
              </label>
              <label class="filter-item" style="grid-column: 1 / -1">
                <span class="filter-item__label">何に結びつけるか（語源ではなく、形や場面）</span>
                <input class="input" :value="draft.detail.memoryHint.basis" @input="store.setDetailPart({ memoryHint: { ...draft.detail!.memoryHint!, basis: ($event.target as HTMLInputElement).value } })">
              </label>
            </div>
            <p v-else class="jp-hint">
              記憶のヒントはまだありません。
              <button type="button" class="jp-demo-linkbtn" data-demo-add-memory @click="enableMemoryHint">欄を作る</button>
            </p>
          </section>

          <!-- 11. ミニ練習 -->
          <section v-else class="jp-demo-section" data-demo-editor-section="practice">
            <h2 class="jp-demo-section__title">ミニ練習</h2>
            <p class="jp-demo-section__hint">
              種類は「助詞を選ぶ／似た言葉を選ぶ／場面に合う言い方／自由に文を作る」。
              すべての種類を入れる必要はありません。
            </p>
            <div class="jp-demo-array-actions">
              <button type="button" class="jp-demo-linkbtn" data-demo-add-practice @click="addPractice">＋ 問題を追加</button>
            </div>
            <article
              v-for="(question, index) in draft.detail?.practices ?? []" :key="question.id"
              class="jp-demo-item" :data-demo-practice="index"
            >
              <div class="jp-demo-item__head">
                <span class="jp-demo-item__no">{{ index + 1 }}</span>
                <span class="badge badge--neutral">{{ PRACTICE_KIND_LABELS[question.kind] ?? question.kind }}</span>
                <span class="jp-demo-item__actions">
                  <button type="button" class="jp-demo-linkbtn" @click="store.removeListItem('practices', index)">削除</button>
                </span>
              </div>
              <div class="jp-demo-fieldgrid">
                <label class="filter-item">
                  <span class="filter-item__label">種類</span>
                  <select class="select" :value="question.kind" @change="store.updateListItem('practices', index, { kind: ($event.target as HTMLSelectElement).value as DemoPracticeKind })">
                    <option v-for="(label, kind) in PRACTICE_KIND_LABELS" :key="kind" :value="kind">{{ label }}</option>
                  </select>
                </label>
                <label class="filter-item">
                  <span class="filter-item__label">記述式（自由造句）か</span>
                  <select class="select" :value="String(question.freeWriting)" @change="store.updateListItem('practices', index, { freeWriting: ($event.target as HTMLSelectElement).value === 'true' })">
                    <option value="false">選択式</option>
                    <option value="true">記述式</option>
                  </select>
                </label>
              </div>
              <label class="filter-item">
                <span class="filter-item__label">問題文</span>
                <input class="input" :value="question.question" @input="store.updateListItem('practices', index, { question: ($event.target as HTMLInputElement).value })">
              </label>
              <label class="filter-item">
                <span class="filter-item__label">問題の補足（中国語）</span>
                <input class="input" :value="question.questionChinese" @input="store.updateListItem('practices', index, { questionChinese: ($event.target as HTMLInputElement).value })">
              </label>
              <div v-if="!question.freeWriting" class="jp-demo-fieldgrid">
                <label v-for="(choice, choiceIndex) in question.choices" :key="choiceIndex" class="filter-item">
                  <span class="filter-item__label">選択肢 {{ choiceIndex + 1 }}</span>
                  <input
                    class="input" :value="choice"
                    @input="store.updateListItem('practices', index, { choices: question.choices.map((entry, i) => i === choiceIndex ? ($event.target as HTMLInputElement).value : entry) })"
                  >
                </label>
              </div>
              <label class="filter-item">
                <span class="filter-item__label">{{ question.freeWriting ? '模範例文' : '正解' }}</span>
                <input class="input" :value="question.answer" @input="store.updateListItem('practices', index, { answer: ($event.target as HTMLInputElement).value })">
              </label>
              <label class="filter-item">
                <span class="filter-item__label">解説</span>
                <input class="input" :value="question.explanation" @input="store.updateListItem('practices', index, { explanation: ($event.target as HTMLInputElement).value })">
              </label>
            </article>
          </section>
        </div>
      </div>
    </template>
  </div>
</template>
