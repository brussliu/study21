<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'
import {
  CAUTION_KIND_LABELS,
  INTERCHANGEABLE_LABELS,
  PRACTICE_KIND_LABELS
} from '@/features/japanese-demo/logic'
import type { DemoDetailContent, DemoPracticeQuestion, DemoWord } from '@/features/japanese-demo/types'
import '@/features/japanese-demo/japanese-demo.css'

/**
 * 日本語勉強【単語情報管理】：学習画面（A. 勉強）。
 *
 * 参考にした 2.0: `japanese_test_a.jsp` → `part/japanese_test_runner.jsp` → `js/japanese_word_test.js` の
 * `renderA()`（音→読み→意味→例文の順で、5 つのタブに分けて見せる）。
 *
 * 2.0 と同じく**別ウィンドウ**で開く（`window.open` の popup）。
 *
 * 2.0 から変えたところ:
 * ・既定の画面で「単語・読み・発音・一言の意味・よく使う文型・代表例文 2 つ・重要な注意 1 つ」を先に見せ、
 *   残りは 5 つのタブに分ける（開いた瞬間に長文が並ばないように）。
 * ・日本語と中国語の階層を明確にし、中国語訳と読みは表示・非表示を切り替えられる。
 * ・誤りと修正はアイコンと文字の両方で示す（色だけに頼らない）。
 * ・ミニ練習はこの画面の中で完結する（テストを作らない・学習回数を書かない）。
 */

const props = defineProps<{
  /** 表示する単語（保存済みの内容） */
  word: DemoWord
  /** 下書きのプレビューか（未保存の内容を見ているとき） */
  draft?: boolean
}>()

const emit = defineEmits<{ close: []; edit: [] }>()

const store = useJapaneseDemoStore()

/* ---------- 表示の切り替え ---------- */

const showChinese = ref(true)
const showReading = ref(true)
const activeTab = ref('meaning')

const TABS = [
  { key: 'meaning', label: '意味・使い方' },
  { key: 'examples', label: '例文・会話' },
  { key: 'compare', label: '似た言葉との違い' },
  { key: 'form', label: '活用・発音' },
  { key: 'practice', label: 'ミニ練習' }
]

/**
 * 表示する詳細の中身。
 * 「一部を欠けさせる」設定のときは、任意の欄を落として空状態の見え方を確認する。
 */
const detail = computed<DemoDetailContent | null>(() => props.word.detail)

const partial = computed(() => store.display.detailMode === 'PARTIAL')

const senses = computed(() => detail.value?.senses ?? [])
const examples = computed(() => detail.value?.examples ?? [])
const patterns = computed(() => detail.value?.patterns ?? [])
const dialogs = computed(() => (partial.value ? [] : detail.value?.dialogs ?? []))
const synonyms = computed(() => detail.value?.synonyms ?? [])
const cautions = computed(() => detail.value?.cautions ?? [])
const conjugations = computed(() => detail.value?.conjugations ?? [])
const pair = computed(() => detail.value?.transitivityPair ?? null)
const pronunciation = computed(() => detail.value?.pronunciation ?? null)
const collocations = computed(() => detail.value?.collocations ?? [])
const relatedWords = computed(() => detail.value?.relatedWords ?? [])
const usageNotes = computed(() => (partial.value ? [] : detail.value?.usageNotes ?? []))
const memoryHint = computed(() => detail.value?.memoryHint ?? null)
const practices = computed(() => detail.value?.practices ?? [])

/** 代表例文 2 つ（やさしい例文を優先）。 */
const heroExamples = computed(() => {
  const list = examples.value
  const basic = list.filter((example) => example.level === 'BASIC')
  const applied = list.filter((example) => example.level === 'APPLIED')
  return [...basic, ...applied].slice(0, 2)
})

/** 既定の画面に出す文型 1 つ。 */
const heroPattern = computed(() => patterns.value[0] ?? null)
/** 既定の画面に出す重要な注意 1 つ（意味の違いを優先）。 */
const heroCaution = computed(
  () => cautions.value.find((caution) => caution.kind === 'MEANING') ?? cautions.value[0] ?? null
)

/** 内容が無いときの案内（キーになる内容が欠けている場合）。 */
const missingCore = computed(() => detail.value === null || (senses.value.length === 0 && examples.value.length === 0))

/* ---------- タブごとの件数（中身が無いタブは出さない） ---------- */

const visibleTabs = computed(() =>
  TABS.filter((tab) => {
    switch (tab.key) {
      case 'meaning':
        return true
      case 'examples':
        return examples.value.length > 0 || dialogs.value.length > 0
      case 'compare':
        return synonyms.value.length > 0 || cautions.value.length > 0
      case 'form':
        return conjugations.value.length > 0 || pair.value !== null || pronunciation.value !== null || collocations.value.length > 0 || relatedWords.value.length > 0 || usageNotes.value.length > 0
      case 'practice':
        return practices.value.length > 0
      default:
        return false
    }
  })
)
// 中身が無いタブを選んでいたら、先頭へ戻す
const effectiveTab = computed(() =>
  visibleTabs.value.some((tab) => tab.key === activeTab.value) ? activeTab.value : 'meaning'
)

/* ---------- 音声（音声ファイルが無いので、再生の状態だけを見せる） ---------- */

/** いま「再生中」に見せている対象のキー。 */
const playingKey = ref('')
let playTimer = 0

/**
 * 音声の再生を**演示だけ**する。
 * 実際の音声ファイルも TTS も無いので、状態を一定時間見せて終わる。
 */
function playDemo(key: string): void {
  window.clearTimeout(playTimer)
  playingKey.value = key
  playTimer = window.setTimeout(() => {
    playingKey.value = ''
  }, 1200)
}

function isPlaying(key: string): boolean {
  return playingKey.value === key
}

/** せりふを 1 つずつ再生する演示（役ごと）。 */
function playDialogLine(dialogId: string, lineId: string): void {
  playDemo(`${dialogId}:${lineId}`)
}

function playDialogAll(dialogId: string): void {
  playDemo(`${dialogId}:all`)
}

onBeforeUnmount(() => {
  window.clearTimeout(playTimer)
})

/* ---------- ミニ練習（この画面の中で完結） ---------- */

/** 選んだ答え（問題 ID → 選んだ値）。 */
const answers = ref<Record<string, string>>({})
/** 記述式の入力。 */
const writings = ref<Record<string, string>>({})
/** 記述式の参考フィードバックを出した問題 ID。 */
const feedbackShown = ref<string[]>([])

function choose(question: DemoPracticeQuestion, value: string): void {
  if (answers.value[question.id] !== undefined) {
    return
  }
  answers.value = { ...answers.value, [question.id]: value }
}

function showFeedback(question: DemoPracticeQuestion): void {
  feedbackShown.value = [...feedbackShown.value, question.id]
}

function isCorrect(question: DemoPracticeQuestion): boolean {
  return answers.value[question.id] === question.answer
}

/**
 * 文型の助詞（`**` で囲んだ部分）を、強調する部分と普通の部分に分ける。
 *
 * `v-html` を使わずに要素として組み立てる（仮データでも文字列を HTML として
 * 解釈させない）。
 */
function patternParts(pattern: string): { text: string; emphasis: boolean }[] {
  const parts: { text: string; emphasis: boolean }[] = []
  const pattern2 = /\*\*(.+?)\*\*/g
  let lastIndex = 0
  let match = pattern2.exec(pattern)
  while (match !== null) {
    if (match.index > lastIndex) {
      parts.push({ text: pattern.slice(lastIndex, match.index), emphasis: false })
    }
    parts.push({ text: match[1] ?? '', emphasis: true })
    lastIndex = match.index + match[0].length
    match = pattern2.exec(pattern)
  }
  if (lastIndex < pattern.length) {
    parts.push({ text: pattern.slice(lastIndex), emphasis: false })
  }
  return parts.length > 0 ? parts : [{ text: pattern, emphasis: false }]
}
</script>

<template>
  <div class="jp-demo jp-demo-study">
    <!-- 下書きのプレビューであることを必ず示す -->
    <p v-if="props.draft" class="jp-demo-draftband" data-demo-draft-band>
      <AppIcon name="info" size="sm" />
      <span>
        <strong>未保存の内容を表示しています。</strong>
        この画面を閉じると編集画面に戻り、入力はそのまま残ります。
      </span>
    </p>

    <div class="jp-demo__head">
      <div class="jp-demo-study__title">
        <h1 class="page-title">A. 勉強</h1>
        <p class="page-sub">
          {{ props.word.heading }}（{{ props.word.reading }}）
          <template v-if="props.word.jlpt"> ／ {{ props.word.jlpt }}</template>
        </p>
      </div>
      <div class="jp-demo__actions">
        <!-- 補充・非表示の切り替え（読みやすさの確認用） -->
        <label class="filter-item">
          <input v-model="showReading" type="checkbox" data-demo-toggle-reading>
          <span class="filter-item__label">読みを表示</span>
        </label>
        <label class="filter-item">
          <input v-model="showChinese" type="checkbox" data-demo-toggle-chinese>
          <span class="filter-item__label">中国語訳を表示</span>
        </label>
        <button type="button" class="btn btn--secondary btn--sm" data-demo-study-edit @click="emit('edit')">
          <AppIcon name="edit" size="sm" /> 詳細編集へ
        </button>
        <button type="button" class="btn btn--secondary btn--sm" data-demo-study-close @click="emit('close')">
          <AppIcon name="x" size="sm" /> 閉じる
        </button>
      </div>
    </div>

    <!-- ============ 既定の画面（まず見せる内容） ============ -->
    <section class="card">
      <div class="jp-demo-study__hero">
        <div style="min-width: 0">
          <h1 class="jp-demo-study__word" data-demo-study-word>{{ props.word.heading }}</h1>
          <div class="jp-demo-study__meta">
            <span v-if="showReading" class="jp-demo-reading" data-demo-study-reading>{{ props.word.reading }}</span>
            <span v-if="props.word.alternateReading" class="jp-demo-reading">
              ／{{ props.word.alternateReading }}（同じ表記の別の読み。別の単語として登録）
            </span>
            <span class="badge badge--outline">{{ props.word.partOfSpeech }}</span>
            <span v-if="props.word.jlpt" class="badge badge--neutral">{{ props.word.jlpt }}（例）</span>
          </div>
          <p v-if="showChinese" class="jp-demo-study__core" data-demo-study-core style="margin-top: var(--sp-2)">
            {{ detail?.coreMeaning || props.word.chineseMeaning }}
          </p>
        </div>

        <!-- 発音（音声ファイルが無いので、状態だけを見せる） -->
        <div class="jp-demo-study__sound">
          <span class="jp-demo-audio">
            <button
              type="button" class="btn btn--secondary btn--sm" data-demo-study-sound
              :aria-label="`${props.word.heading} の発音を再生`" @click="playDemo('word')"
            >
              <AppIcon name="play" size="sm" /> 発音
            </button>
            <span
              class="jp-demo-audio__state" :class="{ 'is-playing': isPlaying('word') }"
              data-demo-study-sound-state
            >
              {{ isPlaying('word') ? '再生中…' : '音声サンプルなし' }}
            </span>
          </span>
        </div>
      </div>

      <!-- キーになる内容が欠けているときは短く伝える -->
      <p v-if="missingCore" class="alert alert--warning" data-demo-study-missing>
        この単語の詳細情報はまだ十分にありません。詳細編集で内容を入れるか、一覧から生成してください。
      </p>

      <div class="jp-demo-tiles" style="margin-top: var(--sp-3)">
        <div class="jp-demo-tile">
          <span class="jp-demo-tile__label">語義</span>
          <span class="jp-demo-tile__value">{{ senses.length }}</span>
        </div>
        <div class="jp-demo-tile">
          <span class="jp-demo-tile__label">例文</span>
          <span class="jp-demo-tile__value">{{ examples.length }}</span>
        </div>
        <div class="jp-demo-tile">
          <span class="jp-demo-tile__label">文型</span>
          <span class="jp-demo-tile__value">{{ patterns.length }}</span>
        </div>
        <div class="jp-demo-tile">
          <span class="jp-demo-tile__label">易錯点</span>
          <span class="jp-demo-tile__value">{{ cautions.length }}</span>
        </div>
      </div>
    </section>

    <!-- 一言の意味・よく使う文型・代表例文 2 つ・重要な注意 1 つ -->
    <section class="card">
      <h2 class="jp-demo-section__title">まず覚えること</h2>

      <div class="jp-demo-pairs">
        <article class="jp-demo-pair" data-demo-study-core-meaning>
          <span class="jp-demo-pair__label">一言の意味</span>
          <p class="jp-demo-ja">{{ senses[0]?.japanese ?? detail?.descriptionJa ?? '—' }}</p>
          <p v-if="showChinese" class="jp-demo-zh">
            {{ senses[0]?.chinese ?? detail?.descriptionZh ?? props.word.chineseMeaning }}
          </p>
        </article>

        <article v-if="heroPattern" class="jp-demo-pair" data-demo-study-pattern>
          <span class="jp-demo-pair__label">よく使う文型</span>
          <!-- 助詞を強調する -->
          <p class="jp-demo-pattern">
            <template v-for="(part, index) in patternParts(heroPattern.pattern)" :key="index">
              <mark v-if="part.emphasis">{{ part.text }}</mark>
              <template v-else>{{ part.text }}</template>
            </template>
          </p>
          <p v-if="showReading" class="jp-demo-reading">{{ heroPattern.reading }}</p>
          <p class="jp-demo-ja">{{ heroPattern.example }}</p>
          <p v-if="showChinese" class="jp-demo-zh">{{ heroPattern.chinese }}／{{ heroPattern.exampleChinese }}</p>
        </article>

        <article
          v-for="example in heroExamples" :key="example.id"
          class="jp-demo-pair" data-demo-study-example
        >
          <div class="jp-demo-pair__head">
            <span class="jp-demo-pair__label">{{ example.level === 'BASIC' ? 'やさしい例文' : '応用例文' }}</span>
            <span v-if="example.source" class="jp-demo-meta">{{ example.source }}</span>
          </div>
          <p class="jp-demo-ja">{{ example.japanese }}</p>
          <p v-if="showReading && example.reading" class="jp-demo-reading">{{ example.reading }}</p>
          <p v-if="showChinese" class="jp-demo-zh">{{ example.chinese }}</p>
        </article>

        <article v-if="heroCaution" class="jp-demo-pair" data-demo-study-caution>
          <div class="jp-demo-pair__head">
            <span class="jp-demo-pair__label">重要な注意</span>
            <span class="badge badge--warning">{{ CAUTION_KIND_LABELS[heroCaution.kind] ?? heroCaution.kind }}</span>
          </div>
          <p class="jp-demo-ja">{{ heroCaution.title }}</p>
          <!-- 誤りと修正はアイコン＋文字の両方で示す -->
          <p class="jp-demo-fix jp-demo-fix--wrong">
            <span class="jp-demo-fix__icon" aria-hidden="true">×</span>
            <span><strong>誤り：</strong>{{ heroCaution.wrong }}</span>
          </p>
          <p class="jp-demo-fix jp-demo-fix--right">
            <span class="jp-demo-fix__icon" aria-hidden="true">○</span>
            <span><strong>自然な言い方：</strong>{{ heroCaution.correct }}</span>
          </p>
          <p v-if="showChinese" class="jp-demo-zh">{{ heroCaution.reason }}</p>
        </article>
      </div>
    </section>

    <!-- ============ 残りはタブに分ける ============ -->
    <section class="card">
      <div class="jp-demo-tabs" role="tablist" aria-label="単語の学習情報">
        <button
          v-for="tab in visibleTabs" :key="tab.key"
          type="button" class="jp-demo-tab" :class="{ 'is-active': effectiveTab === tab.key }"
          role="tab" :aria-selected="effectiveTab === tab.key" :data-demo-study-tab="tab.key"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>

      <!-- 意味・使い方 -->
      <div v-if="effectiveTab === 'meaning'" class="jp-demo-tabpanel" data-demo-study-panel="meaning" role="tabpanel">
        <div class="jp-demo-pairs">
          <article v-if="detail?.descriptionJa" class="jp-demo-pair">
            <span class="jp-demo-pair__label">単語の説明</span>
            <p class="jp-demo-ja">{{ detail.descriptionJa }}</p>
            <p v-if="showChinese" class="jp-demo-zh">{{ detail.descriptionZh }}</p>
          </article>

          <article v-for="sense in senses" :key="sense.id" class="jp-demo-pair" data-demo-study-sense>
            <div class="jp-demo-pair__head">
              <span class="jp-demo-item__no">{{ sense.number }}</span>
              <span class="jp-demo-pair__label">{{ sense.context }}／{{ sense.style }}</span>
            </div>
            <p class="jp-demo-ja">{{ sense.japanese }}</p>
            <p v-if="showChinese" class="jp-demo-zh">{{ sense.chinese }}</p>
          </article>

          <p v-if="senses.length === 0 && !detail?.descriptionJa" class="jp-hint">
            まだ意味の情報がありません。
          </p>

          <article v-if="usageNotes.length > 0" class="jp-demo-pair">
            <span class="jp-demo-pair__label">使う場面・語感</span>
            <ul class="jp-demo-zh" style="margin: 0; padding-left: 1.2rem">
              <li v-for="note in usageNotes" :key="note.id">
                {{ note.register }}／{{ note.politeness }}／{{ note.audience }}
                <span v-if="note.senseNumber !== null">（語義 {{ note.senseNumber }}）</span>
                <template v-if="showChinese">：{{ note.note }}</template>
              </li>
            </ul>
          </article>

          <!-- 記憶のヒント（語源ではないと明示する） -->
          <article v-if="memoryHint" class="jp-demo-pair" data-demo-study-memory>
            <div class="jp-demo-pair__head">
              <span class="jp-demo-pair__label">記憶のヒント</span>
              <span class="badge badge--neutral">語源ではありません</span>
            </div>
            <p class="jp-demo-ja">{{ memoryHint.hint }}</p>
            <p class="jp-demo-zh">{{ memoryHint.basis }}</p>
          </article>
        </div>
      </div>

      <!-- 例文・会話 -->
      <div v-else-if="effectiveTab === 'examples'" class="jp-demo-tabpanel" data-demo-study-panel="examples" role="tabpanel">
        <div class="jp-demo-pairs">
          <article v-for="example in examples" :key="example.id" class="jp-demo-pair" data-demo-study-example>
            <div class="jp-demo-pair__head">
              <span class="jp-demo-pair__label">{{ example.level === 'BASIC' ? 'やさしい例文' : '応用例文' }}</span>
              <span v-if="example.senseNumber !== null" class="badge badge--outline">語義 {{ example.senseNumber }}</span>
              <span v-if="example.source" class="jp-demo-meta">{{ example.source }}</span>
            </div>
            <p class="jp-demo-ja">{{ example.japanese }}</p>
            <p v-if="showReading && example.reading" class="jp-demo-reading">{{ example.reading }}</p>
            <p v-if="showChinese" class="jp-demo-zh">{{ example.chinese }}</p>
          </article>
          <p v-if="examples.length === 0" class="jp-hint">例文はまだありません。</p>

          <!-- 会話（役ごとに見せる。1 文ずつ／通しの再生つき） -->
          <article v-for="dialog in dialogs" :key="dialog.id" class="jp-demo-pair" data-demo-study-dialog>
            <div class="jp-demo-pair__head">
              <span class="jp-demo-pair__label">会話：{{ dialog.scene }}</span>
              <button type="button" class="jp-demo-linkbtn" data-demo-dialog-play @click="playDialogAll(dialog.id)">
                <AppIcon name="play" size="sm" /> 通しで再生
              </button>
              <span v-if="isPlaying(`${dialog.id}:all`)" class="jp-demo-audio__state is-playing">再生中…</span>
            </div>
            <div class="jp-demo-dialog">
              <div v-for="line in dialog.lines" :key="line.id" class="jp-demo-dialog__line" data-demo-dialog-line>
                <span class="jp-demo-dialog__speaker">{{ line.speaker }}</span>
                <div class="jp-demo-dialog__body">
                  <p class="jp-demo-ja">{{ line.japanese }}</p>
                  <p v-if="showChinese" class="jp-demo-zh">{{ line.chinese }}</p>
                </div>
                <span class="jp-demo-array-actions">
                  <button
                    type="button" class="jp-demo-linkbtn" :aria-label="`${line.speaker} のせりふを再生`"
                    @click="playDialogLine(dialog.id, line.id)"
                  >
                    <AppIcon name="play" size="sm" />
                  </button>
                  <span v-if="isPlaying(`${dialog.id}:${line.id}`)" class="jp-demo-audio__state is-playing">再生中</span>
                </span>
              </div>
            </div>
          </article>
          <p v-if="dialogs.length === 0 && !partial" class="jp-hint">会話はまだありません。</p>
          <p v-else-if="partial" class="jp-hint">（一部の項目は未登録です）</p>
        </div>
      </div>

      <!-- 似た言葉との違い -->
      <div v-else-if="effectiveTab === 'compare'" class="jp-demo-tabpanel" data-demo-study-panel="compare" role="tabpanel">
        <div class="jp-demo-compare">
          <article v-for="synonym in synonyms" :key="synonym.id" class="jp-demo-compare__card" data-demo-study-synonym>
            <div class="jp-demo-pair__head">
              <span class="jp-demo-ja is-large">{{ synonym.heading }}</span>
              <span class="jp-demo-reading">{{ synonym.reading }}</span>
            </div>
            <span
              class="jp-demo-compare__verdict"
              :class="`jp-demo-compare__verdict--${synonym.interchangeable.toLowerCase()}`"
            >
              {{ INTERCHANGEABLE_LABELS[synonym.interchangeable] }}
            </span>
            <p v-if="showChinese" class="jp-demo-zh">{{ synonym.chinese }}</p>
            <p class="jp-demo-zh">同じところ：{{ synonym.shared }}</p>
            <p class="jp-demo-ja">違うところ：{{ synonym.difference }}</p>
            <p class="jp-demo-zh">使う場面：{{ synonym.usage }}</p>
            <p class="jp-demo-zh">{{ synonym.interchangeNote }}</p>
            <div class="jp-demo-pair">
              <p class="jp-demo-ja">{{ synonym.exampleJapanese }}</p>
              <p v-if="showChinese" class="jp-demo-zh">{{ synonym.exampleChinese }}</p>
            </div>
          </article>
        </div>

        <div v-if="cautions.length > 0" class="jp-demo-pairs" style="margin-top: var(--sp-3)">
          <h3 class="jp-demo-section__title">間違えやすいところ</h3>
          <article v-for="caution in cautions" :key="caution.id" class="jp-demo-pair" data-demo-study-caution>
            <div class="jp-demo-pair__head">
              <span class="badge badge--warning">{{ CAUTION_KIND_LABELS[caution.kind] ?? caution.kind }}</span>
              <span class="jp-demo-ja">{{ caution.title }}</span>
            </div>
            <p class="jp-demo-fix jp-demo-fix--wrong">
              <span class="jp-demo-fix__icon" aria-hidden="true">×</span>
              <span>{{ caution.wrong }}</span>
            </p>
            <p class="jp-demo-fix jp-demo-fix--right">
              <span class="jp-demo-fix__icon" aria-hidden="true">○</span>
              <span>{{ caution.correct }}</span>
            </p>
            <p v-if="showChinese" class="jp-demo-zh">{{ caution.reason }}</p>
          </article>
        </div>
        <p v-if="synonyms.length === 0 && cautions.length === 0" class="jp-hint">
          似た言葉の情報はまだありません。
        </p>
      </div>

      <!-- 活用・発音 -->
      <div v-else-if="effectiveTab === 'form'" class="jp-demo-tabpanel" data-demo-study-panel="form" role="tabpanel">
        <div class="jp-demo-pairs">
          <!-- 名詞には活用を出さない（無理に動詞の活用を見せない） -->
          <article v-if="conjugations.length > 0" class="jp-demo-pair" data-demo-study-conjugation>
            <span class="jp-demo-pair__label">活用（{{ props.word.partOfSpeech }}）</span>
            <div class="jp-demo-fieldgrid">
              <div v-for="conjugation in conjugations" :key="conjugation.id" class="jp-demo-pair">
                <span class="jp-demo-pair__label">{{ conjugation.form }}</span>
                <p class="jp-demo-ja">{{ conjugation.value }}</p>
                <p class="jp-demo-zh">{{ conjugation.example }}</p>
              </div>
            </div>
          </article>
          <article v-else class="jp-demo-pair">
            <span class="jp-demo-pair__label">活用</span>
            <p class="jp-demo-zh">
              「{{ props.word.partOfSpeech }}」なので、動詞の活用はありません。
            </p>
          </article>

          <article v-if="pair" class="jp-demo-pair" data-demo-study-transitivity>
            <span class="jp-demo-pair__label">自他動詞の対応</span>
            <div class="jp-demo-fieldgrid">
              <div class="jp-demo-pair">
                <span class="jp-demo-pair__label">自動詞</span>
                <p class="jp-demo-ja">{{ pair.intransitive }}</p>
                <p class="jp-demo-zh">{{ pair.intransitiveExample }}</p>
              </div>
              <div class="jp-demo-pair">
                <span class="jp-demo-pair__label">他動詞</span>
                <p class="jp-demo-ja">{{ pair.transitive }}</p>
                <p class="jp-demo-zh">{{ pair.transitiveExample }}</p>
              </div>
            </div>
            <p class="jp-demo-zh">助詞の違い：{{ pair.particleNote }}</p>
          </article>

          <article v-if="pronunciation" class="jp-demo-pair" data-demo-study-pronunciation>
            <span class="jp-demo-pair__label">発音・アクセント</span>
            <p class="jp-demo-ja">
              {{ pronunciation.reading }}
              <span class="jp-demo-array-actions">
                <button type="button" class="jp-demo-linkbtn" data-demo-study-sound @click="playDemo('pron')">
                  <AppIcon name="play" size="sm" /> 再生
                </button>
                <span v-if="isPlaying('pron')" class="jp-demo-audio__state is-playing">再生中…</span>
              </span>
            </p>
            <!-- アクセントは確認できたものだけ出す。未確認は「未確認」と書く -->
            <p class="jp-demo-zh">
              アクセント：
              <template v-if="pronunciation.accentType !== null">
                {{ pronunciation.accentType }} 型<template v-if="pronunciation.accentNotation">（{{ pronunciation.accentNotation }}）</template>
              </template>
              <template v-else>
                <span class="badge badge--neutral">未確認</span>
                この語のアクセントは確認できていないため、表示していません。
              </template>
            </p>
            <p v-if="pronunciation.hint" class="jp-demo-zh">{{ pronunciation.hint }}</p>
          </article>

          <p v-if="partial" class="jp-hint">（一部の項目は未登録です）</p>

          <article v-if="collocations.length > 0" class="jp-demo-pair">
            <span class="jp-demo-pair__label">よく使う言い回し</span>
            <ul style="margin: 0; padding-left: 1.2rem">
              <li v-for="collocation in collocations" :key="collocation.id" class="jp-demo-zh">
                <span class="jp-demo-ja">{{ collocation.expression }}</span>
                （{{ collocation.reading }}）<template v-if="showChinese">：{{ collocation.chinese }}／{{ collocation.usage }}</template>
              </li>
            </ul>
          </article>

          <article v-if="relatedWords.length > 0" class="jp-demo-pair">
            <span class="jp-demo-pair__label">関連語</span>
            <ul style="margin: 0; padding-left: 1.2rem">
              <li v-for="related in relatedWords" :key="related.id" class="jp-demo-zh">
                <span class="badge badge--outline">{{ related.relation }}</span>
                <span class="jp-demo-ja">{{ related.heading }}</span>
                （{{ related.reading }}）<template v-if="showChinese">：{{ related.chinese }}</template>
              </li>
            </ul>
          </article>
        </div>
      </div>

      <!-- ミニ練習 -->
      <div v-else class="jp-demo-tabpanel" data-demo-study-panel="practice" role="tabpanel">
        <div class="jp-demo-pairs">
          <article v-for="question in practices" :key="question.id" class="jp-demo-quiz" data-demo-study-quiz>
            <div class="jp-demo-pair__head">
              <span class="badge badge--neutral">{{ PRACTICE_KIND_LABELS[question.kind] ?? question.kind }}</span>
              <span class="jp-demo-ja">{{ question.question }}</span>
            </div>
            <p v-if="showChinese" class="jp-demo-zh">{{ question.questionChinese }}</p>

            <!-- 選択式 -->
            <template v-if="!question.freeWriting">
              <button
                v-for="choice in question.choices" :key="choice"
                type="button" class="jp-demo-choice"
                :class="{
                  'is-correct': answers[question.id] !== undefined && choice === question.answer,
                  'is-wrong': answers[question.id] === choice && choice !== question.answer
                }"
                :disabled="answers[question.id] !== undefined"
                :data-demo-quiz-choice="choice" @click="choose(question, choice)"
              >
                <span class="jp-demo-choice__mark" aria-hidden="true">
                  {{ answers[question.id] === undefined ? '・' : choice === question.answer ? '○' : answers[question.id] === choice ? '×' : '・' }}
                </span>
                <span>{{ choice }}</span>
              </button>
              <p v-if="answers[question.id] !== undefined" class="jp-demo-zh" data-demo-quiz-result>
                <strong>{{ isCorrect(question) ? '正解です。' : `もう一度確認しましょう。正解は「${question.answer}」です。` }}</strong>
                {{ question.explanation }}
              </p>
            </template>

            <!-- 記述式（自由造句）。模擬のフィードバックであることを明示する -->
            <template v-else>
              <label class="filter-item" style="display: block">
                <span class="filter-item__label">自分の文を作ってみる</span>
                <input
                  v-model="writings[question.id]" class="input" type="text"
                  :data-demo-quiz-writing="question.id" :placeholder="question.answer"
                >
              </label>
              <div class="jp-demo-array-actions">
                <button
                  type="button" class="jp-demo-linkbtn" data-demo-quiz-feedback
                  :disabled="feedbackShown.includes(question.id)" @click="showFeedback(question)"
                >
                  フィードバックを見る
                </button>
                <span class="jp-demo-meta">参考として、文法・自然さ・別の言い方を示します</span>
              </div>
              <div v-if="feedbackShown.includes(question.id) && question.demoFeedback" class="jp-demo-pair" data-demo-quiz-feedback-panel>
                <p class="jp-demo-zh"><strong>文法：</strong>{{ question.demoFeedback.grammar }}</p>
                <p class="jp-demo-zh"><strong>自然さ：</strong>{{ question.demoFeedback.naturalness }}</p>
                <p class="jp-demo-zh"><strong>参考表現：</strong>{{ question.demoFeedback.reference }}</p>
              </div>
            </template>
          </article>
          <p v-if="practices.length === 0" class="jp-hint">この単語にはまだ練習問題がありません。</p>
        </div>
      </div>
    </section>
  </div>
</template>
