<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, useId } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'

import { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'
import {
  CAUTION_KIND_LABELS,
  INTERCHANGEABLE_LABELS,
  PRACTICE_KIND_LABELS,
  collectionLabel
} from '@/features/japanese-demo/logic'
import type { DemoDetailContent, DemoPracticeQuestion, DemoWord } from '@/features/japanese-demo/types'
import '@/features/japanese-demo/japanese-demo.css'

/** 2.0 の A 学習と同じく、語・発音・中文释义の直後にタブを置く。 */

const props = defineProps<{
  /** 表示する単語（保存済みの内容） */
  word: DemoWord
  /** 本番テストではブラウザの日本語音声を再生する。 */
  livePlayback?: boolean
  /** 下書きのプレビューか（未保存の内容を見ているとき） */
  draft?: boolean
  /**
   * 一部の欄が欠けている状態か（空状態の見え方を確かめるとき）。
   * デモでは表示設定（`display.detailMode`）で切り替え、本番では渡さない。
   */
  partial?: boolean
}>()

defineEmits<{ close: []; edit: [] }>()

const store = useJapaneseDemoStore()

/* ---------- 表示の切り替え ---------- */

const activeTab = ref('meaning')
const tabPrefix = useId()
const tabButtons = ref<HTMLButtonElement[]>([])

/** 矢印・Home・End でもタブを切り替えられる。 */
async function onTabKeydown(event: KeyboardEvent, index: number): Promise<void> {
  let next = index
  if (event.key === 'ArrowRight') next = (index + 1) % TABS.length
  else if (event.key === 'ArrowLeft') next = (index + TABS.length - 1) % TABS.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = TABS.length - 1
  else return
  event.preventDefault()
  activeTab.value = TABS[next]!.key
  await nextTick()
  tabButtons.value[next]?.focus()
}

/**
 * タブ（2.0 の A 学習と同じ並び）。
 * ミニ練習は 2.0 ではタブの外だったが、2.1 は「この画面の中で完結する」ため
 * 最後のタブに置く。
 */
const TABS = [
  { key: 'meaning', label: '語義・説明' },
  { key: 'form', label: '発音・アクセント' },
  { key: 'collocations', label: 'コロケーション' },
  { key: 'examples', label: '例文・会話' },
  { key: 'compare', label: '関連語・使用注意' },
  { key: 'practice', label: '練習' }
]

/**
 * 表示する詳細の中身。
 * 「一部を欠けさせる」設定のときは、任意の欄を落として空状態の見え方を確認する。
 */
const detail = computed<DemoDetailContent | null>(() => props.word.detail)

const partial = computed(() => props.partial === true || store.display.detailMode === 'PARTIAL')

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

/** 内容が無いときの案内（キーになる内容が欠けている場合）。 */
const missingCore = computed(() => detail.value === null || (senses.value.length === 0 && examples.value.length === 0))

/** 収録（教材のどこに載っているか）。代表 1 件だけ帯に出す。 */
const collections = computed(() => props.word.collections.map((collection) => collectionLabel(collection)))
/** 発音の見出し（語そのものの読み）。 */
const soundText = computed(() => props.word.reading || props.word.heading)

/** タブごとの件数（0 件でもタブは出し、パネルの中で「まだありません」と言う）。 */
function countOf(key: string): number | null {
  switch (key) {
    case 'meaning':
      return senses.value.length
    case 'examples':
      return examples.value.length + dialogs.value.length
    case 'compare':
      return synonyms.value.length + cautions.value.length + relatedWords.value.length + usageNotes.value.length
    case 'form':
      return conjugations.value.length + (pronunciation.value === null ? 0 : 1) + (pair.value === null ? 0 : 1)
    case 'collocations':
      return collocations.value.length + patterns.value.length
    case 'practice':
      return practices.value.length
    default:
      return null
  }
}

/* ---------- 音声（音声ファイルが無いので、再生の状態だけを見せる） ---------- */

/** いま「再生中」に見せている対象のキー。 */
const playingKey = ref('')
const playbackError = ref('')
let playTimer = 0

/**
 * 音声の再生を**演示だけ**する。
 * 実際の音声ファイルも TTS も無いので、状態を一定時間見せて終わる。
 */
function playDemo(key: string): void {
  if (props.livePlayback) {
    playbackError.value = ''
    if (!window.speechSynthesis) { playbackError.value = '音声再生に対応していません。'; return }
    window.speechSynthesis.cancel()
    const dialog = dialogs.value.find(item => key.startsWith(item.id + ':'))
    const text = dialog ? (key.endsWith(':all') ? dialog.lines.map(line => line.japanese).join('。') : dialog.lines.find(line => key === dialog.id + ':' + line.id)?.japanese ?? '') : props.word.reading || props.word.heading
    const utterance = new SpeechSynthesisUtterance(text)
    utterance.lang = 'ja-JP'; utterance.rate = 0.88
    playingKey.value = key
    utterance.onend = () => { if (playingKey.value === key) playingKey.value = '' }
    utterance.onerror = event => { if (event.error !== 'canceled' && event.error !== 'interrupted') playbackError.value = '音声を再生できませんでした。'; if (playingKey.value === key) playingKey.value = '' }
    window.speechSynthesis.speak(utterance)
    return
  }
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
  if (props.livePlayback) window.speechSynthesis?.cancel()
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

    <!-- ============ 上：何を・どこまで見ているかと、表示の切り替え ============ -->
    <header class="jp-study-head" data-demo-study-head>
      <div class="jp-study-head__main">
        <span class="jp-study-kicker">A. 勉強</span>
        <div class="jp-study-meta" data-demo-study-collection>
          <span class="jp-study-meta__item">
            <span>書籍／Unit</span>
            <strong>{{ collections[0] ?? '収録なし' }}</strong>
          </span>
          <span v-if="collections.length > 1" class="jp-study-meta__item">
            <span>ほかの収録</span>
            <strong>{{ collections.length - 1 }} 件</strong>
          </span>
          <span class="jp-study-meta__item">
            <span>品詞</span>
            <strong>{{ props.word.partOfSpeech }}</strong>
          </span>
          <span class="jp-study-meta__item">
            <span>{{ props.livePlayback ? 'JLPT' : 'JLPT（例）' }}</span>
            <strong>{{ props.word.jlpt ?? '未確認' }}</strong>
          </span>
          <span v-if="props.word.collections[0]" class="jp-study-meta__item">
            <span>単語SEQ</span>
            <strong>{{ props.word.collections[0].seq }}</strong>
          </span>
        </div>
      </div>
    </header>

    <!-- ============ 語の帯（見出し語・読み・発音・一言の意味） ============ -->
    <section class="jp-study-summary" data-demo-study-hero>
      <div class="jp-study-hero">
        <div class="jp-study-hero__word">
          <h1 class="jp-study-word" lang="ja" data-demo-study-word>{{ props.word.heading }}</h1>
          <p class="jp-study-reading" lang="ja" data-demo-study-reading>{{ props.word.reading }}</p>
          <p v-if="props.word.alternateReading" class="jp-demo-meta">
            同じ表記に「{{ props.word.alternateReading }}」の読みもあります（別の単語として登録）
          </p>
          <div class="jp-study-badges">
            <span class="badge badge--outline">{{ props.word.partOfSpeech }}</span>
            <span v-if="props.word.jlpt" class="badge badge--neutral">{{ props.word.jlpt }}{{ props.livePlayback ? '' : '（例）' }}</span>
            <span v-if="props.word.manuallyEdited" class="jp-study-badge jp-study-badge--teal">人が直した内容</span>
            <span v-if="props.draft" class="jp-study-badge jp-study-badge--warn">未保存</span>
          </div>
        </div>

        <!-- 発音（音声ファイルが無いので、状態だけを見せる） -->
        <div class="jp-study-hero__sound">
          <button
            type="button" class="btn btn--secondary btn--sm jp-study-sound" data-demo-study-sound
            :title="`発音（${soundText}）`"
            :aria-label="`${props.word.heading} の発音を再生`" @click="playDemo('word')"
          >
            <AppIcon name="speaker" size="sm" />
          </button>
          <span
            class="jp-demo-audio__state" :class="{ 'is-playing': isPlaying('word') }"
            data-demo-study-sound-state
          >
            {{ playbackError || (isPlaying('word') ? '再生中…' : props.livePlayback ? '発音を聞く' : '音声サンプルなし') }}
          </span>
        </div>
      </div>

      <!-- 同じカード内で、中国語の意味は別の行に置く -->
      <div class="jp-study-primary" data-demo-study-primary>
        <span class="jp-study-primary__label">中文释义</span>
        <strong class="jp-study-primary__text" lang="zh-CN" data-demo-study-primary-text>
          {{ props.word.chineseMeaning }}
        </strong>
      </div>
    </section>

    <!-- キーになる内容が欠けているときは短く伝える -->
    <p v-if="missingCore" class="alert alert--warning" data-demo-study-missing>
      この単語の詳細情報はまだ十分にありません。詳細編集で内容を入れるか、一覧から生成してください。
    </p>

    <!-- 学習内容は選択中のパネル内だけに出す -->
    <section class="jp-study-card jp-study-card--tabs">
      <div class="jp-demo-tabs" role="tablist" aria-label="単語の学習情報">
        <button
          v-for="(tab, index) in TABS" :id="`${tabPrefix}-tab-${tab.key}`" :key="tab.key"
          ref="tabButtons" type="button" class="jp-demo-tab"
          :class="{ 'is-active': activeTab === tab.key }" role="tab" :aria-selected="activeTab === tab.key"
          :data-demo-study-tab="tab.key" :aria-controls="`${tabPrefix}-panel-${tab.key}`"
          :tabindex="activeTab === tab.key ? 0 : -1"
          @keydown="onTabKeydown($event, index)" @click="activeTab = tab.key"
        >
          {{ tab.label }}
          <span v-if="countOf(tab.key) !== null" class="jp-demo-tab__count">{{ countOf(tab.key) }}</span>
        </button>
      </div>

      <!-- 語義・説明 -->
      <div
        v-if="activeTab === 'meaning'" :id="`${tabPrefix}-panel-${activeTab}`" class="jp-demo-tabpanel" data-demo-study-panel="meaning"
        role="tabpanel" :aria-labelledby="`${tabPrefix}-tab-${activeTab}`" tabindex="0"
      >
        <div class="jp-study-stack">
          <section class="jp-study-block">
            <h3 class="jp-study-block__title">単語説明</h3>
            <!-- 一言の意味は日本語のまま（中国語の意味は上の独立した帯で見せる） -->
            <p class="jp-study-core" lang="ja" data-demo-study-core>
              <span class="jp-study-core__label">一言の意味</span>
              {{ detail?.coreMeaning || senses[0]?.japanese || 'まだありません' }}
            </p>

            <div v-if="detail?.descriptionJa" class="jp-lang jp-lang--ja" lang="ja" data-demo-study-lang="ja">
              <span class="jp-lang__badge">日</span>
              <div class="jp-lang__body">{{ detail.descriptionJa }}</div>
            </div>
            <div v-if="detail?.descriptionZh" class="jp-lang jp-lang--zh" lang="zh-CN" data-demo-study-lang="zh">
              <span class="jp-lang__badge">中</span>
              <div class="jp-lang__body">{{ detail.descriptionZh }}</div>
            </div>
            <p v-if="!detail?.descriptionJa && senses.length === 0" class="jp-hint">まだ意味の情報がありません。</p>
          </section>

          <section class="jp-study-block">
            <h3 class="jp-study-block__title">語義</h3>
            <article v-for="sense in senses" :key="sense.id" class="jp-study-sense" data-demo-study-sense>
              <div class="jp-study-sense__head">
                <span class="jp-study-sense__no">{{ sense.number }}</span>
                <span class="jp-study-row__tag">{{ sense.context }}／{{ sense.style }}</span>
              </div>
              <div class="jp-lang jp-lang--ja" lang="ja" data-demo-study-lang="ja">
                <span class="jp-lang__badge">日</span>
                <div class="jp-lang__body">{{ sense.japanese }}</div>
              </div>
              <div class="jp-lang jp-lang--zh" lang="zh-CN" data-demo-study-lang="zh">
                <span class="jp-lang__badge">中</span>
                <div class="jp-lang__body">{{ sense.chinese }}</div>
              </div>
            </article>
            <p v-if="senses.length === 0" class="jp-hint">語義はまだありません。</p>
          </section>

          <!-- 記憶のヒント（語源ではないと明示する） -->
          <section v-if="memoryHint" class="jp-study-block" data-demo-study-memory>
            <div class="jp-study-block__head">
              <h3 class="jp-study-block__title">記憶のヒント</h3>
            </div>
            <div class="jp-lang jp-lang--ja" lang="ja" data-demo-study-lang="ja">
              <span class="jp-lang__badge">日</span>
              <div class="jp-lang__body">{{ memoryHint.hint }}</div>
            </div>
            <div class="jp-lang jp-lang--zh" lang="zh-CN" data-demo-study-lang="zh">
              <span class="jp-lang__badge">中</span>
              <div class="jp-lang__body">{{ memoryHint.basis }}</div>
            </div>
          </section>
        </div>
      </div>

      <!-- 発音・アクセント（活用・自他動詞もここにまとめる） -->
      <div
        v-else-if="activeTab === 'form'" :id="`${tabPrefix}-panel-${activeTab}`" class="jp-demo-tabpanel" data-demo-study-panel="form"
        role="tabpanel" :aria-labelledby="`${tabPrefix}-tab-${activeTab}`" tabindex="0"
      >
        <div class="jp-study-form">
          <section v-if="pronunciation" class="jp-study-form__section" data-demo-study-pronunciation>
            <h3 class="jp-study-block__title">読み・アクセント</h3>
            <div class="jp-study-form__pronunciation">
              <strong lang="ja">{{ pronunciation.reading }}</strong>
              <button
                type="button" class="btn btn--secondary btn--sm jp-study-sound" data-demo-study-sound
                aria-label="発音を再生" title="発音を再生" @click="playDemo('pron')"
              >
                <AppIcon name="speaker" size="sm" />
              </button>
              <span class="jp-demo-meta">
                アクセント：<template v-if="pronunciation.accentType !== null">{{ pronunciation.accentType }} 型<template v-if="pronunciation.accentNotation">（{{ pronunciation.accentNotation }}）</template></template>
                <template v-else>未確認</template>
              </span>
              <span v-if="isPlaying('pron')" class="jp-demo-audio__state is-playing" role="status">再生中…</span>
            </div>
            <details v-if="pronunciation.hint" class="jp-study-form__hint">
              <summary>発音のヒント</summary>
              <p>{{ pronunciation.hint }}</p>
            </details>
          </section>
          <p v-else class="jp-hint">発音・アクセントの情報はまだありません。</p>

          <section v-if="conjugations.length" class="jp-study-form__section">
            <h3 class="jp-study-block__title">活用</h3>
            <div class="jp-study-form__table-wrap">
              <table class="jp-study-form__table" data-demo-study-conjugation>
                <thead><tr><th scope="col">形</th><th scope="col">活用形</th><th scope="col">例文</th></tr></thead>
                <tbody>
                  <tr v-for="conjugation in conjugations" :key="conjugation.id">
                    <th scope="row">{{ conjugation.form }}</th>
                    <td lang="ja">{{ conjugation.value }}</td>
                    <td lang="ja">{{ conjugation.example }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section v-if="pair" class="jp-study-form__section" data-demo-study-transitivity>
            <h3 class="jp-study-block__title">自動詞・他動詞</h3>
            <div class="jp-study-form__pair">
              <div>
                <span class="jp-demo-meta">自動詞</span>
                <strong lang="ja">{{ pair.intransitive }}</strong>
                <p lang="ja">{{ pair.intransitiveExample }}</p>
              </div>
              <div>
                <span class="jp-demo-meta">他動詞</span>
                <strong lang="ja">{{ pair.transitive }}</strong>
                <p lang="ja">{{ pair.transitiveExample }}</p>
              </div>
            </div>
            <details v-if="pair.particleNote" class="jp-study-form__hint">
              <summary>助詞の使い分け</summary>
              <p>{{ pair.particleNote }}</p>
            </details>
          </section>
          <p v-if="partial" class="jp-hint">（一部の項目は未登録です）</p>
        </div>
      </div>

      <!-- コロケーション -->
      <div
        v-else-if="activeTab === 'collocations'" :id="`${tabPrefix}-panel-${activeTab}`" class="jp-demo-tabpanel" data-demo-study-panel="collocations"
        role="tabpanel" :aria-labelledby="`${tabPrefix}-tab-${activeTab}`" tabindex="0"
      >
        <div class="jp-study-stack">
          <section v-if="patterns.length" class="jp-study-block">
            <div class="jp-study-block__head">
              <h3 class="jp-study-block__title">よく使う文型</h3>
              <span class="jp-demo-meta">助詞を強調</span>
            </div>
            <article v-for="pattern in patterns" :key="pattern.id" class="jp-study-row" data-demo-study-pattern>
              <div class="jp-lang jp-lang--ja" lang="ja" data-demo-study-lang="ja">
                <span class="jp-lang__badge">日</span>
                <div class="jp-lang__body">
                  <p class="jp-demo-pattern">
                    <template v-for="(part, index) in patternParts(pattern.pattern)" :key="index">
                      <mark v-if="part.emphasis">{{ part.text }}</mark>
                      <template v-else>{{ part.text }}</template>
                    </template>
                  </p>
                  <p v-if="pattern.reading" class="jp-lang__reading">{{ pattern.reading }}</p>
                  <p class="jp-lang__line">{{ pattern.example }}</p>
                </div>
              </div>
              <div class="jp-lang jp-lang--zh" lang="zh-CN" data-demo-study-lang="zh">
                <span class="jp-lang__badge">中</span>
                <div class="jp-lang__body">
                  <p class="jp-lang__line">{{ pattern.chinese }}</p>
                  <p class="jp-lang__line">{{ pattern.exampleChinese }}</p>
                </div>
              </div>
            </article>
          </section>
          <section class="jp-study-block">
            <h3 class="jp-study-block__title">よく使う言い回し</h3>
            <article
              v-for="collocation in collocations" :key="collocation.id"
              class="jp-study-row" data-demo-study-collocation
            >
              <div class="jp-lang jp-lang--ja" lang="ja" data-demo-study-lang="ja">
                <span class="jp-lang__badge">日</span>
                <div class="jp-lang__body">
                  <p class="jp-lang__line">{{ collocation.expression }}</p>
                  <p v-if="collocation.reading" class="jp-lang__reading">{{ collocation.reading }}</p>
                </div>
              </div>
              <div class="jp-lang jp-lang--zh" lang="zh-CN" data-demo-study-lang="zh">
                <span class="jp-lang__badge">中</span>
                <div class="jp-lang__body">
                  <p class="jp-lang__line">{{ collocation.chinese }}</p>
                  <p class="jp-lang__line">使う場面：{{ collocation.usage }}</p>
                </div>
              </div>
            </article>
            <p v-if="collocations.length === 0" class="jp-hint">コロケーションはまだありません。</p>
          </section>
        </div>
      </div>

      <!-- 例文・会話 -->
      <div
        v-else-if="activeTab === 'examples'" :id="`${tabPrefix}-panel-${activeTab}`" class="jp-demo-tabpanel" data-demo-study-panel="examples"
        role="tabpanel" :aria-labelledby="`${tabPrefix}-tab-${activeTab}`" tabindex="0"
      >
        <div class="jp-study-stack">
          <section class="jp-study-block">
            <h3 class="jp-study-block__title">例文</h3>
            <article
              v-for="example in examples" :key="example.id"
              class="jp-study-row" data-demo-study-example
            >
              <div class="jp-study-row__head">
                <span class="jp-study-row__label">{{ example.level === 'BASIC' ? 'やさしい例文' : '応用例文' }}</span>
                <span v-if="example.senseNumber !== null" class="jp-study-row__tag">語義 {{ example.senseNumber }}</span>
                <span v-if="example.source" class="jp-study-row__tag">{{ example.source }}</span>
              </div>
              <div class="jp-lang jp-lang--ja" lang="ja" data-demo-study-lang="ja">
                <span class="jp-lang__badge">日</span>
                <div class="jp-lang__body">
                  <p class="jp-lang__line">{{ example.japanese }}</p>
                  <p v-if="example.reading" class="jp-lang__reading">{{ example.reading }}</p>
                </div>
              </div>
              <div class="jp-lang jp-lang--zh" lang="zh-CN" data-demo-study-lang="zh">
                <span class="jp-lang__badge">中</span>
                <div class="jp-lang__body">{{ example.chinese }}</div>
              </div>
            </article>
            <p v-if="examples.length === 0" class="jp-hint">例文はまだありません。</p>
          </section>

          <!-- 会話（役ごとに見せる。1 文ずつ／通しの再生つき） -->
          <section class="jp-study-block">
            <h3 class="jp-study-block__title">会話</h3>
            <article
              v-for="dialog in dialogs" :key="dialog.id"
              class="jp-study-row" data-demo-study-dialog
            >
              <div class="jp-study-row__head">
                <span class="jp-study-row__label">場面：{{ dialog.scene }}</span>
                <button type="button" class="btn btn--secondary btn--sm jp-study-sound" aria-label="会話を通して再生" title="会話を通して再生" data-demo-dialog-play @click="playDialogAll(dialog.id)">
                  <AppIcon name="speaker" size="sm" />
                </button>
                <span v-if="isPlaying(`${dialog.id}:all`)" class="jp-demo-audio__state is-playing">再生中…</span>
              </div>
              <div class="jp-demo-dialog">
                <div v-for="line in dialog.lines" :key="line.id" class="jp-demo-dialog__line" data-demo-dialog-line>
                  <span class="jp-demo-dialog__speaker">{{ line.speaker }}</span>
                  <div class="jp-demo-dialog__body">
                    <div class="jp-lang jp-lang--ja" lang="ja" data-demo-study-lang="ja">
                      <span class="jp-lang__badge">日</span>
                      <div class="jp-lang__body">{{ line.japanese }}</div>
                    </div>
                    <div class="jp-lang jp-lang--zh" lang="zh-CN" data-demo-study-lang="zh">
                      <span class="jp-lang__badge">中</span>
                      <div class="jp-lang__body">{{ line.chinese }}</div>
                    </div>
                  </div>
                  <span class="jp-demo-array-actions">
                    <button
                      type="button" class="btn btn--secondary btn--sm jp-study-sound" :aria-label="`${line.speaker} のせりふを再生`" :title="`${line.speaker} のせりふを再生`"
                      @click="playDialogLine(dialog.id, line.id)"
                    >
                      <AppIcon name="speaker" size="sm" />
                    </button>
                    <span v-if="isPlaying(`${dialog.id}:${line.id}`)" class="jp-demo-audio__state is-playing">再生中</span>
                  </span>
                </div>
              </div>
            </article>
            <p v-if="dialogs.length === 0 && !partial" class="jp-hint">会話はまだありません。</p>
            <p v-else-if="partial" class="jp-hint">（一部の項目は未登録です）</p>
          </section>
        </div>
      </div>

      <!-- 関連語・使用注意（似た言葉との違い・間違えやすいところ） -->
      <div
        v-else-if="activeTab === 'compare'" :id="`${tabPrefix}-panel-${activeTab}`" class="jp-demo-tabpanel" data-demo-study-panel="compare"
        role="tabpanel" :aria-labelledby="`${tabPrefix}-tab-${activeTab}`" tabindex="0"
      >
        <div class="jp-study-stack jp-study-compare-layout">
          <div v-if="synonyms.length || relatedWords.length" class="jp-study-compare-column">
            <section v-if="synonyms.length > 0" class="jp-study-block">
              <h3 class="jp-study-block__title">似た言葉との違い</h3>
              <div class="jp-demo-compare">
                <article v-for="synonym in synonyms" :key="synonym.id" class="jp-demo-compare__card" data-demo-study-synonym>
                  <div class="jp-study-row__head">
                    <span class="jp-study-word" lang="ja">{{ synonym.heading }}</span>
                    <span class="jp-demo-reading" lang="ja">{{ synonym.reading }}</span>
                    <span
                      class="jp-demo-compare__verdict"
                      :class="`jp-demo-compare__verdict--${synonym.interchangeable.toLowerCase()}`"
                    >
                      {{ INTERCHANGEABLE_LABELS[synonym.interchangeable] }}
                    </span>
                  </div>
                  <p class="jp-demo-meta" lang="zh-CN">{{ synonym.chinese }}</p>
                  <p class="jp-study-compare-difference" lang="ja">{{ synonym.difference }}</p>
                  <details class="jp-study-form__hint">
                    <summary>使い分けの補足</summary>
                    <dl class="jp-study-compare-notes">
                      <dt>共通点</dt><dd>{{ synonym.shared }}</dd>
                      <dt>使う場面</dt><dd>{{ synonym.usage }}</dd>
                      <dt>置き換え</dt><dd>{{ synonym.interchangeNote }}</dd>
                    </dl>
                  </details>
                  <div class="jp-lang jp-lang--ja" lang="ja" data-demo-study-lang="ja">
                    <span class="jp-lang__badge">日</span>
                    <div class="jp-lang__body">
                      <p class="jp-lang__line">{{ synonym.exampleJapanese }}</p>
                    </div>
                  </div>
                  <p class="jp-lang jp-lang--zh" lang="zh-CN" data-demo-study-lang="zh">
                    <span class="jp-lang__badge">中</span>
                    <span class="jp-lang__body">{{ synonym.exampleChinese }}</span>
                  </p>
                </article>
              </div>
            </section>

            <section v-if="relatedWords.length > 0" class="jp-study-block">
              <h3 class="jp-study-block__title">関連語</h3>
              <div class="jp-study-grid">
                <div v-for="related in relatedWords" :key="related.id" class="jp-study-cell">
                  <span class="jp-study-row__tag">{{ related.relation }}</span>
                  <p class="jp-lang__line" lang="ja">{{ related.heading }}</p>
                  <p class="jp-demo-reading" lang="ja">{{ related.reading }}</p>
                  <p class="jp-lang__line" lang="zh-CN">{{ related.chinese }}</p>
                </div>
              </div>
            </section>
          </div>
          <div v-if="cautions.length || usageNotes.length" class="jp-study-compare-column">
            <section v-if="cautions.length > 0" class="jp-study-block">
              <h3 class="jp-study-block__title">間違えやすいところ</h3>
              <article v-for="caution in cautions" :key="caution.id" class="jp-study-row" data-demo-study-caution>
                <div class="jp-study-row__head">
                  <span class="badge badge--warning">{{ CAUTION_KIND_LABELS[caution.kind] ?? caution.kind }}</span>
                  <span class="jp-lang__line" lang="ja">{{ caution.title }}</span>
                </div>
                <p class="jp-demo-fix jp-demo-fix--wrong">
                  <span class="jp-demo-fix__icon" aria-hidden="true">×</span>
                  <span lang="ja">{{ caution.wrong }}</span>
                </p>
                <p class="jp-demo-fix jp-demo-fix--right">
                  <span class="jp-demo-fix__icon" aria-hidden="true">○</span>
                  <span lang="ja">{{ caution.correct }}</span>
                </p>
                <div class="jp-lang jp-lang--zh" lang="zh-CN" data-demo-study-lang="zh">
                  <span class="jp-lang__badge">中</span>
                  <div class="jp-lang__body">{{ caution.reason }}</div>
                </div>
              </article>
            </section>

            <section v-if="usageNotes.length > 0" class="jp-study-block">
              <h3 class="jp-study-block__title">使う場面・語感</h3>
              <ul class="jp-study-list">
                <li v-for="note in usageNotes" :key="note.id">
                  <span class="jp-study-row__tag">{{ note.register }}／{{ note.politeness }}</span>
                  <span class="jp-lang__body">{{ note.audience }}</span>
                  <span v-if="note.senseNumber !== null" class="jp-study-row__tag">語義 {{ note.senseNumber }}</span>
                  <p class="jp-lang jp-lang--zh" lang="zh-CN" data-demo-study-lang="zh">
                    <span class="jp-lang__badge">中</span>
                    <span class="jp-lang__body">{{ note.note }}</span>
                  </p>
                </li>
              </ul>
            </section>
          </div>
          <p v-if="synonyms.length === 0 && cautions.length === 0 && relatedWords.length === 0 && usageNotes.length === 0" class="jp-hint">
            似た言葉の情報はまだありません。
          </p>
        </div>
      </div>

      <!-- ミニ練習 -->
      <div
        v-else :id="`${tabPrefix}-panel-${activeTab}`" class="jp-demo-tabpanel" data-demo-study-panel="practice"
        role="tabpanel" :aria-labelledby="`${tabPrefix}-tab-${activeTab}`" tabindex="0"
      >
        <div class="jp-study-stack">
          <article v-for="question in practices" :key="question.id" class="jp-demo-quiz" data-demo-study-quiz>
            <div class="jp-study-row__head">
              <span class="badge badge--neutral">{{ PRACTICE_KIND_LABELS[question.kind] ?? question.kind }}</span>
              <span class="jp-lang__line" lang="ja">{{ question.question }}</span>
            </div>
            <div class="jp-lang jp-lang--zh" lang="zh-CN" data-demo-study-lang="zh">
              <span class="jp-lang__badge">中</span>
              <div class="jp-lang__body">{{ question.questionChinese }}</div>
            </div>

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
                <span lang="ja">{{ choice }}</span>
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
              <div v-if="feedbackShown.includes(question.id) && question.demoFeedback" class="jp-study-row" data-demo-quiz-feedback-panel>
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
