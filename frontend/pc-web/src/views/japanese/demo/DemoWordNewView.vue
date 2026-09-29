<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import DemoWordGrid from '@/features/japanese-demo/components/DemoWordGrid.vue'
import { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'
import type { RegisterContract } from '@/features/japanese-word/registerContract'
import '@/features/japanese-demo/japanese-demo.css'

/**
 * 日本語勉強【単語情報管理】：単語の新規登録（ダイアログ）。
 *
 * 1 ページに「書籍の設定」「単語の入力（Excel 風の表）」「オプション」をまとめて置く。
 *
 * ・入力は Excel からそのまま貼り付けられる（表は `DemoWordGrid`）。
 * ・読みと中国語の意味はここでは入れない（あとで AI から取得する）。
 * ・書籍は既存から選ぶか、**新しい書籍を追加**する（プルダウンの最後の項目）。
 * ・1 Unit あたりの語数は、既存の書籍なら**その本から自動で決める**。
 * ・取り込んだ順に Unit を割り当てる（AI の意味分類ではない）。
 *
 * 状態は `store` からもらう（デモは仮データ、本番は実 API）。
 * 画面が使うものは `features/japanese-word/registerContract.ts` の契約だけ。
 */

const props = defineProps<{
  /** 登録の状態（デモのストア、または本番のストア）。 */
  store?: RegisterContract
}>()

const demoStore = useJapaneseDemoStore()
/** 状態の取り出し口（渡されなければデモのストアを使う）。 */
const store = computed<RegisterContract>(() => props.store ?? (demoStore as unknown as RegisterContract))

const emit = defineEmits<{ close: []; saved: [] }>()

/** 新しい書籍を追加する選択肢（プルダウンの最後に置く）。 */
const NEW_BOOK = '__NEW_BOOK__'
/** 書籍のプルダウンで選んでいる値（新規追加を選ぶと NEW_BOOK になる）。 */
const bookChoice = ref<string>('')
/** 貼り付けなどの知らせ。 */
const notice = ref('')
const saving = ref(false)

/** 表の値（1 行 1 語）。表を編集すると、そのまま解釈し直す。 */
const gridValues = computed({
  get: () => store.value.registerHeadings,
  set: (next: string[]) => {
    store.value.setRegisterHeadings(next)
    notice.value = ''
  }
})

/** 表の部品（列の幅や行の操作は部品が持つ）。 */
const gridRef = ref<InstanceType<typeof DemoWordGrid> | null>(null)

const registerBookMode = computed(() => store.value.registerBookMode)
const registerBookName = computed({
  get: () => store.value.registerBookName,
  set: (value: string) => {
    store.value.registerBookName = value
  }
})
const registerUnitSize = computed(() => store.value.registerUnitSize)
const registerUnitSizeInput = computed({
  get: () => store.value.registerUnitSizeInput,
  set: (value: number | null) => {
    store.value.registerUnitSizeInput = value
  }
})
const registerPlacement = computed(() => store.value.registerPlacement)
const registerDuplicateMode = computed({
  get: () => store.value.registerDuplicateMode,
  set: (value: typeof store.value.registerDuplicateMode) => {
    store.value.registerDuplicateMode = value
  }
})
const allocation = computed(() => store.value.allocation)
const registerError = computed(() => store.value.registerError)
const bookNameOptions = computed(() => store.value.bookNameOptions)

/** 表からの知らせ（取り込んだ件数）。 */
function onGridNotice(message: string): void {
  notice.value = message
}

/** 取り込む語数と、飛ばす数。 */
const counts = computed(() => store.value.registerCounts)

const canSave = computed(() => {
  if (store.value.registerWords.length === 0) {
    return false
  }
  if (store.value.registerBookMode === 'NEW') {
    return store.value.registerBookName.trim() !== '' && store.value.registerUnitSize > 0
  }
  return store.value.registerBookName !== ''
})

/**
 * 書籍のプルダウンの選択。
 * 「新しい書籍を追加…」を選ぶと名前の入力欄が出る（モードを切り替える）。
 */
function chooseBook(value: string): void {
  if (value === NEW_BOOK) {
    store.value.registerBookMode = 'NEW'
    store.value.registerPlacement = 'NEW_BOOK'
    if (store.value.registerBookName === '' || store.value.bookNameOptions.includes(store.value.registerBookName)) {
      store.value.registerBookName = ''
    }
    store.value.registerUnitSizeInput = store.value.registerUnitSizeInput ?? 20
    return
  }
  store.value.registerBookMode = 'EXISTING'
  store.value.registerPlacement = 'CONTINUE'
  store.value.registerBookName = value
  // 書籍を選び直したら、1 Unit の語数はその本の値に戻す（プルダウンの表示も揃える）
  store.value.registerUnitSizeInput = store.value.bookUnitSizeOf(value)
}

/** 書籍や容量、重複の設定を変えたら Unit を計算し直す。 */
function recompute(): void {
  store.value.setRegisterHeadings([...store.value.registerHeadings])
}

async function save(): Promise<void> {
  saving.value = true
  const ok = await store.value.saveRegistration()
  saving.value = false
  if (ok) {
    store.value.registerSaveState = 'IDLE'
    emit('saved')
  }
}

function cancel(): void {
  store.value.resetRegister()
  emit('close')
}

onMounted(() => {
  store.value.resetRegister()
  bookChoice.value = store.value.registerBookName
  void gridRef.value
})
</script>

<template>
  <div class="overlay jp-demo-new-overlay">
    <section
      class="dialog dialog--lg dialog--wide" role="dialog" aria-modal="true"
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

      <div class="dialog__body jp-demo-dialog-body jp-demo-register">
        <!-- 1. 書籍（何より先に決める） -->
        <div class="jp-demo-section" data-demo-step="1">
          <h3 class="jp-demo-section__title">書籍</h3>

          <!--
            1 行に「書籍」「新しい書籍の名前」「1 Unit あたりの単語数」を並べる。
            名前の欄は新しい書籍のときだけ使うが、既存の書籍のときも**枠は残す**
            （枠ごと消すと、語数のプルダウンが左へ寄って位置が食い違う）。
          -->
          <div class="jp-demo-fieldgrid jp-demo-register__book">
            <label class="filter-item">
              <span class="filter-item__label">書籍</span>
              <select
                v-model="bookChoice" class="select" data-demo-book-select aria-label="書籍"
                @change="chooseBook(bookChoice)"
              >
                <option v-for="name in bookNameOptions" :key="name" :value="name">{{ name }}</option>
                <option :value="NEW_BOOK">新しい書籍を追加…</option>
              </select>
            </label>
            <label
              class="filter-item" data-demo-book-name-field
              :class="{ 'is-placeholder': registerBookMode !== 'NEW' }"
            >
              <span class="filter-item__label">新しい書籍の名前</span>
              <input
                v-model="registerBookName" class="input" type="text"
                data-demo-book-name placeholder="例: 初中級のことば"
                :disabled="registerBookMode !== 'NEW'"
                :aria-hidden="registerBookMode !== 'NEW'"
              >
            </label>
            <label class="filter-item">
              <span class="filter-item__label">1 Unit あたりの単語数</span>
              <!--
                既存・新規どちらも同じ形（プルダウン）で、同じ位置に出す。
                既定はその書籍から自動で決めた値。選び直したらその値を使う。
              -->
              <select
                v-model.number="registerUnitSizeInput" class="select" data-demo-unit-size
                aria-label="1 Unit あたりの単語数"
              >
                <option :value="10">10 語</option>
                <option :value="15">15 語</option>
                <option :value="20">20 語</option>
                <option :value="30">30 語</option>
                <option :value="50">50 語</option>
              </select>
            </label>
          </div>
        </div>

        <!-- 2. 単語の入力（Excel 風の表） -->
        <div class="jp-demo-section" data-demo-step="2">
          <!-- 行の追加は、TODO の新規（子タスク）と同じく見出しの右に置く -->
          <div class="jp-demo-register__wordhead">
            <h3 class="jp-demo-section__title">単語</h3>
            <button
              type="button" class="btn btn--secondary btn--sm" data-demo-add-row-button
              @click="gridRef?.addRowFromOutside()"
            >
              <AppIcon name="plus" size="sm" /> 行追加
            </button>
          </div>
          <DemoWordGrid
            ref="gridRef"
            v-model:values="gridValues"
            @notice="onGridNotice"
          />
          <p v-if="notice !== ''" class="jp-demo-meta" data-demo-paste-notice>{{ notice }}</p>
        </div>

        <!-- 3. オプション（登録開始 Unit と重複判定の範囲を左右に並べる） -->
        <div class="jp-demo-register__aside" data-demo-register-aside>
          <div class="jp-demo-section" data-demo-step="3">
            <h3 class="jp-demo-section__title">オプション</h3>

            <div class="jp-demo-options__pair">
              <!-- どの Unit から登録を始めるか（新しい書籍のときは聞かない） -->
              <div v-if="registerBookMode === 'EXISTING'" class="jp-demo-options" data-demo-options-group="placement">
                <p class="jp-demo-options__legend">登録開始Unit</p>
                <div class="jp-demo-options__grid">
                  <label class="jp-demo-option">
                    <input
                      type="radio" name="demoPlacement" value="CONTINUE" data-demo-placement-continue
                      :checked="registerPlacement === 'CONTINUE'"
                      @change="registerPlacement = 'CONTINUE'; recompute()"
                    >
                    <span>最後の Unit の続きから</span>
                  </label>
                  <label class="jp-demo-option">
                    <input
                      type="radio" name="demoPlacement" value="NEW_UNIT" data-demo-placement-new-unit
                      :checked="registerPlacement === 'NEW_UNIT'"
                      @change="registerPlacement = 'NEW_UNIT'; recompute()"
                    >
                    <span>新しい Unit から</span>
                  </label>
                </div>
                <!-- 選んだ始め方の意味を、この列の下に出す -->
                <p class="jp-demo-section__hint" data-demo-placement-note>
                  {{ registerPlacement === 'CONTINUE'
                    ? '最後の Unit が未満なら、そこを埋めてから次へ進みます。'
                    : 'いまの Unit は触らず、次の Unit から並べます。' }}
                </p>
              </div>

              <!-- 重複をどの範囲で見るか -->
              <div class="jp-demo-options" data-demo-options-group="duplicate">
                <p class="jp-demo-options__legend">重複判定範囲</p>
                <div class="jp-demo-options__grid">
                  <label class="jp-demo-option">
                    <input
                      v-model="registerDuplicateMode" type="radio" value="BOOK" data-demo-duplicate-book
                      @change="recompute()"
                    >
                    <span>登録対象書籍</span>
                  </label>
                  <label class="jp-demo-option">
                    <input
                      v-model="registerDuplicateMode" type="radio" value="ALL" data-demo-duplicate-all
                      @change="recompute()"
                    >
                    <span>すべて書籍</span>
                  </label>
                </div>
                <!-- 選んだ範囲の意味は、「すべて書籍」の下に出す -->
                <p class="jp-demo-section__hint" data-demo-duplicate-note>
                  {{ registerDuplicateMode === 'ALL'
                    ? 'ほかの書籍に入っている単語も飛ばします。'
                    : 'この書籍に同じ単語があるときだけ飛ばします。' }}
                </p>
              </div>
            </div>
          </div>
        </div>

        <!-- 4. 登録する内容（最後に確認する） -->
        <div class="jp-demo-section jp-demo-register__summary" data-demo-step="4">
          <h3 class="jp-demo-section__title">登録する内容</h3>
          <div class="jp-demo-tiles" data-demo-confirm-summary>
            <div class="jp-demo-tile">
              <span class="jp-demo-tile__label">登録する単語</span>
              <span class="jp-demo-tile__value" data-demo-count-fresh>{{ counts.fresh }}</span>
            </div>
            <div class="jp-demo-tile" :class="{ 'is-warn': counts.skipped > 0 }">
              <span class="jp-demo-tile__label">重複で飛ばす</span>
              <span class="jp-demo-tile__value" data-demo-count-skipped>{{ counts.skipped }}</span>
            </div>
            <div class="jp-demo-tile">
              <span class="jp-demo-tile__label">登録先の書籍</span>
              <span class="jp-demo-tile__value" style="font-size: var(--fs-sm)">{{ registerBookName || '—' }}</span>
            </div>
            <div class="jp-demo-tile">
              <span class="jp-demo-tile__label">1 Unit あたり</span>
              <span class="jp-demo-tile__value" style="font-size: var(--fs-sm)">{{ registerUnitSize }} 語</span>
            </div>
          </div>
        </div>

        <!-- 4b. 割り当ての内訳（どの Unit に何語入るか） -->
        <div v-if="allocation.summaries.length > 0" class="jp-demo-section jp-demo-register__units">
          <h3 class="jp-demo-section__title">Unit ごとの割り当て</h3>
          <div class="jp-demo-tiles" data-demo-confirm-units>
            <div v-for="summary in allocation.summaries" :key="summary.unit" class="jp-demo-tile">
              <span class="jp-demo-tile__label">{{ summary.unit }}</span>
              <span class="jp-demo-tile__value">{{ summary.count }} 語</span>
            </div>
          </div>
        </div>

        <!-- 登録に失敗した理由は、割り当てが無いときでも必ず出す -->
        <p v-if="registerError !== ''" class="alert alert--danger" data-demo-register-error>
          {{ registerError }}
        </p>
      </div>

      <div class="dialog__foot">
        <button type="button" class="btn btn--secondary" data-demo-cancel @click="cancel">キャンセル</button>
        <button
          type="button" class="btn btn--primary" data-demo-save
          :disabled="saving || !canSave" @click="save"
        >
          <AppIcon name="check" size="sm" /> {{ saving ? '登録しています…' : '登録する' }}
        </button>
      </div>
    </section>
  </div>
</template>
