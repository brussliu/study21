<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import DemoWordGrid from '@/features/japanese-demo/components/DemoWordGrid.vue'
import { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'
import type { ParsedRowState } from '@/features/japanese-demo/logic'
import { DEMO_PASTE_SAMPLE } from '@/features/japanese-demo/mock/demoWords'
import '@/features/japanese-demo/japanese-demo.css'

/**
 * 日本語勉強【単語情報管理】：単語の新規登録（ダイアログ）。
 *
 * 「単語を入力」→「書籍・Unit を設定」→「内容を確認」の 3 ステップ。
 *
 * ・入力は **Excel からそのまま貼り付け**られる表（1 列だけ）。
 *   読みと中国語の意味はここでは入れない（あとで AI から取得する）。
 * ・書籍は**既存から選ぶ**か、**新しい書籍を追加**する（プルダウンの最後の項目）。
 * ・1 Unit あたりの語数は、既存の書籍なら**その本から自動で決める**。
 *   新しい書籍は決められないので手で入れる。
 * ・取り込んだ順に Unit を割り当てる（AI の意味分類ではない）。
 */

const store = useJapaneseDemoStore()

const emit = defineEmits<{ close: []; saved: [] }>()

/** 新しい書籍を追加する選択肢（プルダウンの最後に置く）。 */
const NEW_BOOK = '__NEW_BOOK__'
/** 書籍のプルダウンで選んでいる値（新規追加を選ぶと NEW_BOOK になる）。 */
const bookChoice = ref<string>(store.registerBookName)
/** 貼り付けの知らせ（何行取り込んだか）。 */
const pasteNotice = ref('')

/** 解釈の結果の見せ方（色だけでなく記号と文字でも示す）。 */
const ROW_STATE: Record<ParsedRowState, { mark: string; label: string; cls: string }> = {
  OK: { mark: '○', label: '取り込む', cls: 'ok' },
  DUPLICATE_EXISTING: { mark: '−', label: '重複のため飛ばす', cls: 'info' },
  DUPLICATE: { mark: '−', label: '入力の重複', cls: 'error' },
  BLANK_WORD: { mark: '×', label: '単語が空', cls: 'error' },
  BLANK_READING: { mark: '−', label: '読みは後で取得', cls: 'info' },
  MULTI_READING: { mark: '−', label: '読みは後で取得', cls: 'info' },
  FORMAT_ERROR: { mark: '×', label: '読み取れません', cls: 'error' }
}

/** 表の行（単語だけ。読みと意味は入れない）。 */
const rows = computed(() => store.registerHeadings)

/** 取り込む語（空行は落とす）。 */
const importable = computed(() => rows.value.filter((heading) => heading.trim() !== '').length)

/** 取り込んだ結果の内訳。 */
const counts = computed(() => store.registerCounts)

const saving = ref(false)

/** 表の値（1 行 1 語）。表を編集すると、そのまま解釈し直す。 */
const gridValues = computed({
  get: () => store.registerHeadings,
  set: (next: string[]) => {
    store.setRegisterHeadings(next)
    pasteNotice.value = ''
  }
})

/** 表からの知らせ（取り込んだ件数）。 */
function onGridNotice(message: string): void {
  pasteNotice.value = message
}

/** 表の部品（列の幅や行の操作は部品が持つ）。 */
const gridRef = ref<InstanceType<typeof DemoWordGrid> | null>(null)

/** 見出しの【行追加】は、表の部品に任せる。 */
function addRow(): void {
  gridRef.value?.addRowFromOutside()
}

function loadSample(): void {
  store.setRegisterHeadings(DEMO_PASTE_SAMPLE.split('\n').map((line) => line.trim()).filter((line) => line !== ''))
  pasteNotice.value = `${store.registerHeadings.length} 件の入力例を入れました。`
}

/** 動作確認用: 新しい書籍に 45 語を入れる（20 語ずつ → 3 Unit）。 */
function loadScenarioNewBook(): void {
  store.resetRegister()
  const lines = Array.from({ length: 45 }, (_, index) => `追加単語${String(index + 1).padStart(2, '0')}`)
  store.setRegisterHeadings(lines)
  chooseBook(NEW_BOOK)
  store.registerBookName = '追加用の書籍'
  store.registerUnitSizeInput = 20
  bookChoice.value = NEW_BOOK
  store.gotoRegisterStep(3)
}

/** 動作確認用: 既存の Unit に 5 語を足す。 */
function loadScenarioAppend(): void {
  store.resetRegister()
  const lines = Array.from({ length: 5 }, (_, index) => `追加語${index + 1}`)
  store.setRegisterHeadings(lines)
  const book = store.bookNameOptions[1] ?? store.bookNameOptions[0] ?? ''
  bookChoice.value = book
  chooseBook(book)
  store.gotoRegisterStep(3)
}

/**
 * 書籍のプルダウンの選択。
 * 「新しい書籍を追加…」を選ぶと入力欄が出る（モードを切り替える）。
 */
function chooseBook(value: string): void {
  if (value === NEW_BOOK) {
    store.registerBookMode = 'NEW'
    store.registerPlacement = 'NEW_BOOK'
    if (store.registerBookName === '' || store.bookNameOptions.includes(store.registerBookName)) {
      store.registerBookName = ''
    }
    store.registerUnitSizeInput = store.registerUnitSizeInput ?? 20
    return
  }
  store.registerBookMode = 'EXISTING'
  store.registerPlacement = 'CONTINUE'
  store.registerBookName = value
}

/** もう一度 Unit を計算し直す（書籍や容量を変えたとき）。 */
function recompute(): void {
  store.parseRegisterText()
}

const canGoStep2 = computed(() => importable.value > 0)
const canGoStep3 = computed(() => {
  if (importable.value === 0) {
    return false
  }
  if (store.registerBookMode === 'NEW') {
    return store.registerBookName.trim() !== '' && (store.registerUnitSizeInput ?? 0) > 0
  }
  return store.registerBookName !== ''
})

async function save(): Promise<void> {
  saving.value = true
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

onMounted(() => {
  store.resetRegister()
  bookChoice.value = store.registerBookName
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
          <div class="jp-demo-array-actions">
            <h3 class="jp-demo-section__title" style="margin: 0">単語</h3>
            <span class="jp-demo-meta">
              単語だけを入力します（読みと中国語の意味は、あとで AI から取得します）。
            </span>
            <span style="margin-left: auto" class="jp-demo-array-actions">
              <button type="button" class="btn btn--secondary btn--sm" data-demo-sample @click="loadSample">
                <AppIcon name="copy" size="sm" /> 入力例を入れる
              </button>
              <button type="button" class="btn btn--secondary btn--sm" data-demo-add-row @click="addRow">
                <AppIcon name="plus" size="sm" /> 行追加
              </button>
            </span>
          </div>

          <!--
            Excel 風の入力表（TODO の新規登録にある「子タスク」の表と同じ作り）。
            セルを選んで貼り付け、ダブルクリック / F2 で入力、↑↓←→ で移動。
          -->
          <DemoWordGrid
            ref="gridRef"
            v-model:values="gridValues"
            @notice="onGridNotice"
          />

          <p v-if="pasteNotice !== ''" class="alert alert--info" data-demo-paste-notice>{{ pasteNotice }}</p>

          <div class="jp-demo-array-actions">
            <button type="button" class="btn btn--secondary btn--sm" data-demo-scenario-new @click="loadScenarioNewBook">
              確認用: 新しい書籍に 45 語（20 語ずつ）
            </button>
            <button type="button" class="btn btn--secondary btn--sm" data-demo-scenario-append @click="loadScenarioAppend">
              確認用: 既存の Unit に 5 語を追加
            </button>
          </div>
        </div>

        <!-- 解析結果（手順 1 の続き） -->
        <div v-if="store.registerStep === 1 && store.parsedRows.length > 0" class="jp-demo-section" data-demo-parsed>
          <div class="jp-demo-tiles" data-demo-parsed-summary>
            <div class="jp-demo-tile">
              <span class="jp-demo-tile__label">取り込む</span>
              <span class="jp-demo-tile__value">{{ importable }}</span>
            </div>
            <div class="jp-demo-tile" :class="{ 'is-warn': counts.skipped > 0 }">
              <span class="jp-demo-tile__label">重複で飛ばす</span>
              <span class="jp-demo-tile__value">{{ counts.skipped }}</span>
            </div>
            <div class="jp-demo-tile">
              <span class="jp-demo-tile__label">空の行</span>
              <span class="jp-demo-tile__value">{{ store.registerSummary.blank }}</span>
            </div>
          </div>

          <div class="table-wrap">
            <table class="data-table" data-demo-parsed-table>
              <thead>
                <tr>
                  <th class="col-narrow">NO</th>
                  <th>単語</th>
                  <th>取り込み</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(row, index) in store.parsedRows" :key="row.line"
                  :data-demo-parsed-row="row.line"
                  :class="{ 'jp-demo-tr--error': ROW_STATE[row.state].cls === 'error' }"
                >
                  <td class="cell-muted">{{ index + 1 }}</td>
                  <td>{{ row.heading || '—' }}</td>
                  <td>
                    <span
                      class="jp-demo-rowstate" :class="`jp-demo-rowstate--${ROW_STATE[row.state].cls}`"
                      :data-demo-row-state="row.state"
                    >
                      <span aria-hidden="true">{{ ROW_STATE[row.state].mark }}</span>
                      {{ ROW_STATE[row.state].label }}
                    </span>
                    <span v-if="row.note" class="jp-demo-meta">{{ row.note }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- ============ 手順 2: 書籍・Unit ============ -->
        <div v-if="store.registerStep === 2" class="jp-demo-section" data-demo-step="2">
          <h3 class="jp-demo-section__title">書籍と Unit の設定</h3>

          <div class="jp-demo-fieldgrid">
            <label class="filter-item">
              <span class="filter-item__label">書籍</span>
              <select v-model="bookChoice" class="select" data-demo-book-select aria-label="書籍" @change="chooseBook(bookChoice)">
                <option v-for="name in store.bookNameOptions" :key="name" :value="name">{{ name }}</option>
                <option :value="NEW_BOOK">新しい書籍を追加…</option>
              </select>
            </label>
            <label v-if="store.registerBookMode === 'NEW'" class="filter-item">
              <span class="filter-item__label">新しい書籍の名前</span>
              <input v-model="store.registerBookName" class="input" type="text" data-demo-book-name placeholder="例: 初中級のことば">
            </label>
            <label class="filter-item">
              <span class="filter-item__label">1 Unit あたりの単語数</span>
              <input
                v-if="store.registerBookMode === 'NEW'" v-model.number="store.registerUnitSizeInput"
                class="input" type="number" min="1" max="500" style="width: 7rem" data-demo-unit-size
              >
              <span v-else class="jp-demo-meta" data-demo-unit-size-auto>
                {{ store.registerUnitSize }} 語（この書籍から自動で計算しました）
              </span>
            </label>
          </div>

          <p class="jp-demo-section__hint">
            既存の書籍は<strong>その本から 1 Unit の語数を計算</strong>します（教材ごとに違うため）。
            新しい書籍は決められないので、入力してください。
            割り当ては<strong>入力順と容量だけ</strong>で決めます（AI による意味の分類ではありません）。
          </p>

          <template v-if="store.registerBookMode === 'EXISTING'">
            <label class="filter-item">
              <input
                type="radio" name="demoPlacement" value="CONTINUE" data-demo-placement-continue
                :checked="store.registerPlacement === 'CONTINUE'"
                @change="store.registerPlacement = 'CONTINUE'; recompute()"
              >
              <span>最後の Unit の続きから（未満の Unit を先に埋める）</span>
            </label>
            <label class="filter-item">
              <input
                type="radio" name="demoPlacement" value="NEW_UNIT" data-demo-placement-new-unit
                :checked="store.registerPlacement === 'NEW_UNIT'"
                @change="store.registerPlacement = 'NEW_UNIT'; recompute()"
              >
              <span>新しい Unit から始める（いまの Unit は触らない）</span>
            </label>
          </template>

          <!-- 重複した単語の扱い（既定は「この書籍の中」で飛ばす） -->
          <h3 class="jp-demo-section__title">重複した単語の扱い</h3>
          <label class="filter-item">
            <input
              v-model="store.registerDuplicateMode" type="radio" value="BOOK" data-demo-duplicate-book
              @change="recompute()"
            >
            <span>この書籍の中で重複した単語を飛ばす（既定）</span>
          </label>
          <label class="filter-item">
            <input
              v-model="store.registerDuplicateMode" type="radio" value="ALL" data-demo-duplicate-all
              @change="recompute()"
            >
            <span>すべての書籍と重複した単語を飛ばす</span>
          </label>
          <p class="jp-demo-section__hint" data-demo-duplicate-note>
            {{ store.registerDuplicateMode === 'ALL'
              ? 'ほかの書籍に入っている単語も飛ばします（同じ単語を別の本に重ねて登録しません）。'
              : 'この書籍に同じ単語があるときだけ飛ばします（別の書籍には登録できます）。' }}
            飛ばした単語は Unit の位置を使いません。
          </p>

          <div class="jp-demo-tiles" data-demo-allocation-summary>
            <div class="jp-demo-tile" :class="{ 'is-warn': counts.skipped > 0 }">
              <span class="jp-demo-tile__label">重複で飛ばす</span>
              <span class="jp-demo-tile__value" data-demo-count-skipped>{{ counts.skipped }}</span>
            </div>
            <div v-for="summary in store.allocation.summaries" :key="summary.unit" class="jp-demo-tile">
              <span class="jp-demo-tile__label">{{ summary.unit }}</span>
              <span class="jp-demo-tile__value">{{ summary.count }} 語</span>
              <span class="jp-demo-meta">#{{ summary.fromSeq }} 〜 #{{ summary.toSeq }}（容量 {{ summary.capacity }}）</span>
            </div>
          </div>
          <p class="jp-demo-section__hint">
            自動で決めた開始 Unit：<strong data-demo-start-unit>{{ store.allocation.startUnit }}</strong>。
            容量を変えても、すでに入っている語の並びは変わりません。
          </p>
        </div>

        <!-- ============ 手順 3: 内容を確認 ============ -->
        <div v-if="store.registerStep === 3" class="jp-demo-section" data-demo-step="3">
          <h3 class="jp-demo-section__title">登録する内容の確認</h3>

          <div class="jp-demo-tiles" data-demo-confirm-summary>
            <div class="jp-demo-tile">
              <span class="jp-demo-tile__label">登録する単語</span>
              <span class="jp-demo-tile__value" data-demo-count-fresh>{{ store.registerWords.length }}</span>
            </div>
            <div class="jp-demo-tile" :class="{ 'is-warn': counts.skipped > 0 }">
              <span class="jp-demo-tile__label">重複で飛ばす</span>
              <span class="jp-demo-tile__value" data-demo-count-skipped>{{ counts.skipped }}</span>
            </div>
            <div class="jp-demo-tile">
              <span class="jp-demo-tile__label">登録先の書籍</span>
              <span class="jp-demo-tile__value" style="font-size: var(--fs-sm)">{{ store.registerBookName || '—' }}</span>
            </div>
            <div class="jp-demo-tile">
              <span class="jp-demo-tile__label">1 Unit あたり</span>
              <span class="jp-demo-tile__value" style="font-size: var(--fs-sm)">{{ store.registerUnitSize }} 語</span>
            </div>
          </div>

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
                  <th class="col-narrow">NO</th>
                  <th>単語</th>
                  <th>書籍</th>
                  <th class="col-narrow">Unit</th>
                  <th class="col-narrow">Unit 内</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(word, index) in store.registerWords" :key="word.line" :data-demo-confirm-row="index">
                  <td class="cell-muted">{{ index + 1 }}</td>
                  <td><span class="jp-demo-word">{{ word.heading }}</span></td>
                  <td>{{ store.registerBookName }}</td>
                  <td>{{ store.allocation.words[index]?.unit ?? '—' }}</td>
                  <td class="cell-muted">#{{ store.allocation.words[index]?.seq ?? '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <p class="jp-demo-section__hint">
            読みと中国語の意味は、この時点では登録しません。登録後に「詳細情報を生成」で AI から取得します。
          </p>

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
