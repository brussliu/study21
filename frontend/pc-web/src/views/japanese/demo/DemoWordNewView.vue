<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'
import type { ParsedRowState } from '@/features/japanese-demo/logic'
import { DEMO_NEW_BOOK_NAME, DEMO_PASTE_SAMPLE } from '@/features/japanese-demo/mock/demoWords'
import '@/features/japanese-demo/japanese-demo.css'

/**
 * 日本語勉強【単語情報管理】デモ：新規登録（3 ステップ）。
 *
 * 「単語を入力」→「書籍・Unit を設定」→「内容を確認」。
 *
 * 参考にした 2.0: `japanese_word.jsp` の「新規」＋ `js/japanese_word.js` の `newWord`/`save`
 * （2.0 は 1 語ずつの登録だったので、まとめて入れて Unit を割り当てる流れは 2.1 の設計）。
 *
 * Unit の割り当ては**入力順と容量だけ**で決める（AI の意味分類ではない）。
 * 保存はストアのメモリ上だけで、実際のシステムには送らない。
 */

const store = useJapaneseDemoStore()

const emit = defineEmits<{ close: []; saved: [] }>()

/** 解析結果の状態ごとの見せ方（色だけでなく記号と文字でも示す）。 */
const ROW_STATE: Record<ParsedRowState, { mark: string; label: string; cls: string }> = {
  OK: { mark: '○', label: '取り込む', cls: 'ok' },
  MULTI_READING: { mark: '!', label: '読みの確認', cls: 'warn' },
  BLANK_READING: { mark: '!', label: '読みが空', cls: 'warn' },
  DUPLICATE_EXISTING: { mark: '↻', label: '収録を追加', cls: 'info' },
  BLANK_WORD: { mark: '×', label: '見出し語が空', cls: 'error' },
  FORMAT_ERROR: { mark: '×', label: '形式エラー', cls: 'error' },
  DUPLICATE: { mark: '×', label: '入力の重複', cls: 'error' }
}

const saving = ref(false)
/** 保存の結果を出す。 */
const saved = ref(false)

/** 読みが複数の行で、どちらを使うか選んだ結果。 */
const multiReadingChoice = ref<Record<number, string>>({})

const importableRows = computed(() =>
  store.parsedRows.filter((row) => row.state !== 'BLANK_WORD' && row.state !== 'FORMAT_ERROR' && row.state !== 'DUPLICATE')
)

/** いまの入力で「次へ」を押せるか。 */
const canGoStep2 = computed(() => store.pasteText.trim() !== '' && store.parsedRows.length > 0)
const canGoStep3 = computed(() => {
  if (store.registerWords.length === 0) {
    return false
  }
  if (store.registerBookMode === 'NEW') {
    return store.registerBookName.trim() !== ''
  }
  return store.registerBookName !== ''
})

function rowValue(line: number, key: 'heading' | 'reading' | 'chineseMeaning', fallback: string): string {
  return store.editedRows[line]?.[key] ?? fallback
}

function onEdit(line: number, key: 'heading' | 'reading' | 'chineseMeaning', value: string): void {
  store.editRow(line, { [key]: value })
}

/** 読みが複数の行で、選んだ読みを反映する。 */
function chooseReading(line: number, reading: string): void {
  multiReadingChoice.value[line] = reading
  store.editRow(line, { reading })
}

function loadSample(): void {
  store.fillSample(DEMO_PASTE_SAMPLE)
}

/** 「新しい書籍を追加」を選んだときに、名前の初期値を入れる。 */
function useNewBookMode(): void {
  store.registerBookMode = 'NEW'
  if (store.registerBookName === '' || store.bookNameOptions.includes(store.registerBookName)) {
    store.registerBookName = DEMO_NEW_BOOK_NAME
  }
  store.registerPlacement = 'NEW_BOOK'
}

function useExistingBookMode(): void {
  store.registerBookMode = 'EXISTING'
  store.registerBookName = store.bookNameOptions[0] ?? ''
  store.registerPlacement = 'CONTINUE'
}

/** 動作確認用の 2 つのシナリオ（新しい書籍／既存の Unit への追加）。 */
function loadScenarioNewBook(): void {
  store.resetRegister()
  // 45 語を新規書籍へ（20 語ずつ → 20 / 20 / 5）
  const lines = Array.from({ length: 45 }, (_, index) => {
    const number = index + 1
    return `追加単語${String(number).padStart(2, '0')}\tついかたんご${String(number).padStart(2, '0')}\t追加单词 ${number}`
  })
  store.pasteText = lines.join('\n')
  store.parseRegisterText()
  useNewBookMode()
  store.registerUnitSize = 20
  store.gotoRegisterStep(3)
}

function loadScenarioAppend(): void {
  store.resetRegister()
  // 既存の Unit に 5 語を追加（未満の Unit を先に埋める）
  const book = store.bookNameOptions[1] ?? store.bookNameOptions[0] ?? ''
  const lines = Array.from({ length: 5 }, (_, index) => {
    const number = index + 1
    return `追加単語${number}\tついかたんご${number}\t追加单词 ${number}`
  })
  store.pasteText = lines.join('\n')
  store.parseRegisterText()
  store.registerBookMode = 'EXISTING'
  store.registerBookName = book
  store.registerUnitSize = 20
  store.registerPlacement = 'CONTINUE'
  store.gotoRegisterStep(3)
}

async function save(): Promise<void> {
  saving.value = true
  saved.value = false
  const ok = await store.saveRegistration()
  saving.value = false
  if (ok) {
    store.registerSaveState = 'IDLE'
    emit('saved')
  }
}

function cancel(): void {
  store.resetRegister()
  emit('close')
}

// 開くたびに、前に入れた内容を残さない
onMounted(() => {
  store.resetRegister()
})
</script>

<template>
  <div class="overlay">
    <section
      class="dialog dialog--lg" role="dialog" aria-modal="true"
      aria-labelledby="jpNewWordTitle" data-demo-new-dialog
    >
      <div class="dialog__head">
        <h2 id="jpNewWordTitle" class="dialog__title">
          <AppIcon name="plus" size="sm" /> 単語の新規登録
        </h2>
        <button type="button" class="dialog__close" aria-label="閉じる" data-demo-new-close @click="cancel">
          <AppIcon name="x" size="sm" />
        </button>
      </div>

      <div class="dialog__body jp-demo-dialog-body">
        <p class="jp-demo-panel__hint" style="margin: 0 0 var(--sp-2)">
          まとめて入力して、書籍と Unit へ順番に割り当てます。
        </p>

        <!-- 3 ステップの道しるべ -->
        <ol class="jp-demo-steps" data-demo-steps>
          <li class="jp-demo-step" :class="{ 'is-active': store.registerStep === 1, 'is-done': store.registerStep > 1 }">
            <span class="jp-demo-step__no">1</span> 単語を入力
          </li>
          <li class="jp-demo-step" :class="{ 'is-active': store.registerStep === 2, 'is-done': store.registerStep > 2 }">
            <span class="jp-demo-step__no">2</span> 書籍・Unit を設定
          </li>
          <li class="jp-demo-step" :class="{ 'is-active': store.registerStep === 3 }">
            <span class="jp-demo-step__no">3</span> 内容を確認
          </li>
        </ol>

        <!-- ============ 手順 1: 単語を入力 ============ -->
        <div v-if="store.registerStep === 1" class="jp-demo-section" data-demo-step="1">
          <div class="card__header">
            <h2 class="card__title">単語を入力</h2>
            <div class="search-panel__actions">
              <button type="button" class="btn btn--secondary btn--sm" data-demo-sample @click="loadSample">
                <AppIcon name="copy" size="sm" /> 入力例を入れる
              </button>
              <button type="button" class="btn btn--primary btn--sm" data-demo-parse @click="store.parseRegisterText()">
                <AppIcon name="check" size="sm" /> 取り込む
              </button>
            </div>
          </div>

          <div class="jp-demo-paste">
            <!-- 区切り方を書いておく（当てずっぽうで入力させない） -->
            <p class="jp-demo-paste__format">
              対応している貼り付けの形式（1 行に 1 語）:
              <br>
              ・<code>見出し語&#9;読み&#9;中国語の意味</code>（タブ区切り。表計算ソフトからの貼り付けに便利）
              <br>
              ・<code>見出し語,読み,中国語の意味</code>（半角カンマ／全角カンマ）
              <br>
              ・<code>見出し語  読み  中国語の意味</code>（半角スペース 2 つ以上）
              <br>
              見出し語は必須です。読みと中国語の意味は空でも登録できます（あとから詳細編集で入れられます）。
              読みが「あく・ひらく」のように複数あるときは、登録する読みを選んでください
              （同じ表記でも読みが違えば<strong>別の単語</strong>として登録します）。
            </p>

            <label class="filter-item" style="display: block">
              <span class="filter-item__label">貼り付け・入力</span>
              <textarea
                class="jp-demo-textarea" data-demo-paste rows="9"
                :value="store.pasteText"
                placeholder="図書館&#9;としょかん&#9;图书馆"
                @input="store.pasteText = ($event.target as HTMLTextAreaElement).value"
              ></textarea>
            </label>

            <div class="jp-demo-array-actions">
              <button type="button" class="btn btn--secondary btn--sm" data-demo-scenario-new @click="loadScenarioNewBook">
                新しい書籍に 45 語（20 語ずつ）を入れる
              </button>
              <button type="button" class="btn btn--secondary btn--sm" data-demo-scenario-append @click="loadScenarioAppend">
                既存の Unit に 5 語を追加
              </button>
            </div>
          </div>
        </div>

        <!-- 解析結果（手順 1 の続き） -->
        <div v-if="store.registerStep === 1 && store.parsedRows.length > 0" class="jp-demo-section" data-demo-parsed>
          <div class="card__header">
            <h2 class="card__title">入力の確認（{{ store.parsedRows.length }} 行）</h2>
            <span class="cell-muted">
              取り込める {{ store.registerSummary.importable }} 語 ／ 要確認 {{ store.registerSummary.multiReading }} 語
            </span>
          </div>

          <div class="jp-demo-tiles" data-demo-parsed-summary>
            <div class="jp-demo-tile">
              <span class="jp-demo-tile__label">取り込む</span>
              <span class="jp-demo-tile__value">{{ importableRows.length }}</span>
            </div>
            <div class="jp-demo-tile" :class="{ 'is-warn': store.registerSummary.multiReading > 0 }">
              <span class="jp-demo-tile__label">読みの確認</span>
              <span class="jp-demo-tile__value">{{ store.registerSummary.multiReading }}</span>
            </div>
            <div class="jp-demo-tile" :class="{ 'is-warn': store.registerSummary.existingReuse > 0 }">
              <span class="jp-demo-tile__label">既存語に収録を追加</span>
              <span class="jp-demo-tile__value">{{ store.registerSummary.existingReuse }}</span>
            </div>
            <div class="jp-demo-tile" :class="{ 'is-error': store.registerSummary.duplicates + store.registerSummary.errors > 0 }">
              <span class="jp-demo-tile__label">重複・エラー</span>
              <span class="jp-demo-tile__value">{{ store.registerSummary.duplicates + store.registerSummary.errors }}</span>
            </div>
            <div class="jp-demo-tile">
              <span class="jp-demo-tile__label">空白（未入力）</span>
              <span class="jp-demo-tile__value">{{ store.registerSummary.blank }}</span>
            </div>
          </div>

          <div class="table-wrap">
            <table class="data-table" data-demo-parsed-table>
              <thead>
                <tr>
                  <th class="col-narrow">行</th>
                  <th>状態</th>
                  <th>見出し語</th>
                  <th>読み</th>
                  <th class="col-meaning">中国語の意味</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in store.parsedRows" :key="row.line"
                  :data-demo-parsed-row="row.line"
                  :class="{
                    'jp-demo-tr--error': ROW_STATE[row.state].cls === 'error',
                    'jp-demo-tr--warn': ROW_STATE[row.state].cls === 'warn'
                  }"
                >
                  <td class="cell-muted">{{ row.line }}</td>
                  <td>
                    <span class="jp-demo-rowstate" :class="`jp-demo-rowstate--${ROW_STATE[row.state].cls}`" :data-demo-row-state="row.state">
                      <span aria-hidden="true">{{ ROW_STATE[row.state].mark }}</span>
                      {{ ROW_STATE[row.state].label }}
                    </span>
                    <span v-if="row.note" class="jp-demo-meta">{{ row.note }}</span>
                    <!-- 読みが複数のときは、どちらで登録するかを選ばせる -->
                    <span v-if="row.state === 'MULTI_READING'" class="jp-demo-array-actions" data-demo-multi-reading>
                      <button
                        v-for="candidate in row.readingCandidates" :key="candidate"
                        type="button" class="jp-demo-linkbtn"
                        :class="{ 'is-active': rowValue(row.line, 'reading', row.reading) === candidate }"
                        @click="chooseReading(row.line, candidate)"
                      >
                        {{ candidate }} で登録
                      </button>
                    </span>
                  </td>
                  <td>
                    <input
                      class="jp-demo-inline-input" :class="{ 'is-invalid': rowValue(row.line, 'heading', row.heading) === '' }"
                      :value="rowValue(row.line, 'heading', row.heading)" :aria-label="`${row.line} 行目の見出し語`"
                      :disabled="row.state === 'DUPLICATE'" :data-demo-row-heading="row.line"
                      @input="onEdit(row.line, 'heading', ($event.target as HTMLInputElement).value)"
                    >
                  </td>
                  <td>
                    <input
                      class="jp-demo-inline-input" :class="{ 'is-invalid': rowValue(row.line, 'reading', row.reading) === '' }"
                      :value="rowValue(row.line, 'reading', row.reading)" :aria-label="`${row.line} 行目の読み`"
                      :disabled="row.state === 'DUPLICATE'" :data-demo-row-reading="row.line"
                      @input="onEdit(row.line, 'reading', ($event.target as HTMLInputElement).value)"
                    >
                  </td>
                  <td>
                    <input
                      class="jp-demo-inline-input"
                      :value="rowValue(row.line, 'chineseMeaning', row.chineseMeaning)"
                      :aria-label="`${row.line} 行目の中国語の意味`"
                      :disabled="row.state === 'DUPLICATE'" :data-demo-row-chinese="row.line"
                      @input="onEdit(row.line, 'chineseMeaning', ($event.target as HTMLInputElement).value)"
                    >
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="jp-demo-section__hint">
            重複した行は取り込みません（Unit の位置も使いません）。読みが空の行は、あとから詳細編集で入れられます。
          </p>
        </div>

        <!-- ============ 手順 2: 書籍・Unit ============ -->
        <div v-if="store.registerStep === 2" class="jp-demo-section" data-demo-step="2">
          <div class="card__header">
            <h2 class="card__title">書籍と Unit の設定</h2>
            <span class="cell-muted">{{ store.registerWords.length }} 語を割り当てます</span>
          </div>

          <div class="jp-demo-fieldgrid">
            <div class="jp-demo-section">
              <h3 class="jp-demo-section__title">書籍の選び方</h3>
              <label class="filter-item">
                <input
                  type="radio" name="demoBookMode" value="EXISTING" data-demo-book-mode-existing
                  :checked="store.registerBookMode === 'EXISTING'" @change="useExistingBookMode"
                >
                <span>既存の書籍から選ぶ</span>
              </label>
              <label class="filter-item">
                <input
                  type="radio" name="demoBookMode" value="NEW" data-demo-book-mode-new
                  :checked="store.registerBookMode === 'NEW'" @change="useNewBookMode"
                >
                <span>新しい書籍を追加</span>
              </label>

              <label v-if="store.registerBookMode === 'EXISTING'" class="filter-item">
                <span class="filter-item__label">書籍：</span>
                <select v-model="store.registerBookName" class="select" data-demo-book-select aria-label="書籍">
                  <option v-for="name in store.bookNameOptions" :key="name" :value="name">{{ name }}</option>
                </select>
              </label>
              <label v-else class="filter-item">
                <span class="filter-item__label">新しい書籍名：</span>
                <input v-model="store.registerBookName" class="input" type="text" data-demo-book-name>
              </label>

              <label class="filter-item">
                <span class="filter-item__label">1 Unit あたりの単語数：</span>
                <input
                  v-model.number="store.registerUnitSize" class="input" type="number" min="1" max="200"
                  style="width: 6rem" data-demo-unit-size
                >
              </label>
            </div>

            <div class="jp-demo-section">
              <h3 class="jp-demo-section__title">Unit の始め方</h3>
              <p class="jp-demo-section__hint">
                ここの割り当ては<strong>入力順と容量だけ</strong>で決めます（AI による意味の分類ではありません）。
                既定ではすべての Unit が同じ容量です。
              </p>
              <label v-if="store.registerBookMode === 'EXISTING'" class="filter-item">
                <input
                  type="radio" name="demoPlacement" value="CONTINUE" data-demo-placement-continue
                  :checked="store.registerPlacement === 'CONTINUE'"
                  @change="store.registerPlacement = 'CONTINUE'"
                >
                <span>最後の Unit の続きから（未満の Unit を先に埋める）</span>
              </label>
              <label v-if="store.registerBookMode === 'EXISTING'" class="filter-item">
                <input
                  type="radio" name="demoPlacement" value="NEW_UNIT" data-demo-placement-new-unit
                  :checked="store.registerPlacement === 'NEW_UNIT'"
                  @change="store.registerPlacement = 'NEW_UNIT'"
                >
                <span>新しい Unit から始める（いまの Unit は触らない）</span>
              </label>
              <p v-if="store.registerBookMode === 'NEW'" class="jp-demo-section__hint">
                新しい書籍なので <strong>Unit001</strong> から始めます。
              </p>

              <!-- 既存の分類が読み取れないときの例（§4-2） -->
              <p v-if="store.registerBookMode === 'EXISTING'" class="alert alert--warning" data-demo-unknown-unit>
                以前の分類名が <code>Unit001</code> の形でない場合は、開始する Unit を指定してください。
                （いまの書籍はすべて <code>Unit001</code> の形なので、この案内は出ません）
              </p>
            </div>
          </div>

          <div class="jp-demo-tiles" data-demo-allocation-summary>
            <div v-for="summary in store.allocation.summaries" :key="summary.unit" class="jp-demo-tile">
              <span class="jp-demo-tile__label">{{ summary.unit }}</span>
              <span class="jp-demo-tile__value">{{ summary.count }} 語</span>
              <span class="jp-demo-meta">#{{ summary.fromSeq }} 〜 #{{ summary.toSeq }}（容量 {{ summary.capacity }}）</span>
            </div>
          </div>
          <p class="jp-demo-section__hint">
            自動で決めた開始 Unit：<strong data-demo-start-unit>{{ store.allocation.startUnit }}</strong>。
            今回の容量を変えても、すでに入っている語の並びは変わりません。
          </p>
        </div>

        <!-- ============ 手順 3: 内容を確認 ============ -->
        <div v-if="store.registerStep === 3" class="jp-demo-section" data-demo-step="3">
          <div class="card__header">
            <h2 class="card__title">登録する内容の確認</h2>
            <span class="cell-muted">
              新規 {{ store.registerCounts.fresh }} 語 ／ 既存語に収録を追加 {{ store.registerCounts.reuse }} 語
            </span>
          </div>

          <div class="jp-demo-tiles" data-demo-confirm-summary>
            <div class="jp-demo-tile">
              <span class="jp-demo-tile__label">新規の単語</span>
              <span class="jp-demo-tile__value" data-demo-count-fresh>{{ store.registerCounts.fresh }}</span>
            </div>
            <div class="jp-demo-tile">
              <span class="jp-demo-tile__label">既存の単語（収録を追加）</span>
              <span class="jp-demo-tile__value" data-demo-count-reuse>{{ store.registerCounts.reuse }}</span>
            </div>
            <div class="jp-demo-tile" :class="{ 'is-error': store.registerCounts.skipped + store.registerCounts.errors > 0 }">
              <span class="jp-demo-tile__label">重複・エラー（取り込まない）</span>
              <span class="jp-demo-tile__value" data-demo-count-skipped>{{ store.registerCounts.skipped + store.registerCounts.errors }}</span>
            </div>
            <div class="jp-demo-tile">
              <span class="jp-demo-tile__label">登録先の書籍</span>
              <span class="jp-demo-tile__value" style="font-size: var(--fs-sm)">{{ store.registerBookName }}</span>
            </div>
          </div>

          <!-- Unit ごとのまとめ（例: 20 語・20 語・5 語） -->
          <div class="jp-demo-tiles" data-demo-confirm-units>
            <div v-for="summary in store.allocation.summaries" :key="summary.unit" class="jp-demo-tile">
              <span class="jp-demo-tile__label">{{ summary.unit }}</span>
              <span class="jp-demo-tile__value">{{ summary.count }} 語</span>
            </div>
          </div>

          <div class="table-wrap">
            <table class="data-table" data-demo-confirm-table>
              <thead>
                <tr>
                  <th>順番</th>
                  <th>見出し語</th>
                  <th>読み</th>
                  <th class="col-meaning">中国語の意味</th>
                  <th>書籍</th>
                  <th>Unit</th>
                  <th>Unit 内</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(word, index) in store.registerWords" :key="word.line" :data-demo-confirm-row="index">
                  <td class="cell-muted">{{ index + 1 }}</td>
                  <td><span class="jp-demo-word">{{ word.heading }}</span></td>
                  <td class="jp-demo-reading">{{ word.reading || '—' }}</td>
                  <td class="col-meaning">{{ word.chineseMeaning || '—' }}</td>
                  <td>{{ store.registerBookName }}</td>
                  <td>{{ store.allocation.words[index]?.unit ?? '—' }}</td>
                  <td class="cell-muted">#{{ store.allocation.words[index]?.seq ?? '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <p v-if="store.registerError !== ''" class="alert alert--danger" data-demo-register-error>
            {{ store.registerError }}
          </p>
        </div>
      </div>

      <div class="dialog__foot">
        <button type="button" class="btn btn--secondary" data-demo-cancel @click="cancel">キャンセル</button>
        <button
          v-if="store.registerStep > 1" type="button" class="btn btn--secondary"
          data-demo-prev @click="store.gotoRegisterStep(store.registerStep - 1)"
        >
          <AppIcon name="chevron-left" size="sm" /> 戻る
        </button>
        <button
          v-if="store.registerStep === 1" type="button" class="btn btn--primary"
          data-demo-next-1 :disabled="!canGoStep2" @click="store.gotoRegisterStep(2)"
        >
          次へ（書籍・Unit の設定） <AppIcon name="chevron-right" size="sm" />
        </button>
        <button
          v-if="store.registerStep === 2" type="button" class="btn btn--primary"
          data-demo-next-2 :disabled="!canGoStep3" @click="store.gotoRegisterStep(3)"
        >
          次へ（内容の確認） <AppIcon name="chevron-right" size="sm" />
        </button>
        <button
          v-if="store.registerStep === 3" type="button" class="btn btn--primary"
          data-demo-save :disabled="saving || store.registerWords.length === 0" @click="save"
        >
          <AppIcon name="check" size="sm" /> {{ saving ? '登録しています…' : '登録する' }}
        </button>
      </div>
    </section>
  </div>
</template>
