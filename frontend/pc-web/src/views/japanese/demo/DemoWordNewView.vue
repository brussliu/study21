<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import DemoWordGrid from '@/features/japanese-demo/components/DemoWordGrid.vue'
import { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'
import '@/features/japanese-demo/japanese-demo.css'

/**
 * 日本語勉強【単語情報管理】：単語の新規登録（ダイアログ）。
 *
 * 1 ページに「単語の入力（Excel 風の表）」と「書籍・Unit の設定」をまとめて置く。
 *
 * ・入力は Excel からそのまま貼り付けられる（表は `DemoWordGrid`）。
 * ・読みと中国語の意味はここでは入れない（あとで AI から取得する）。
 * ・書籍は既存から選ぶか、**新しい書籍を追加**する（プルダウンの最後の項目）。
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
/** 貼り付けなどの知らせ。 */
const notice = ref('')
const saving = ref(false)

/** 表の値（1 行 1 語）。表を編集すると、そのまま解釈し直す。 */
const gridValues = computed({
  get: () => store.registerHeadings,
  set: (next: string[]) => {
    store.setRegisterHeadings(next)
    notice.value = ''
  }
})

/** 表の部品（列の幅や行の操作は部品が持つ）。 */
const gridRef = ref<InstanceType<typeof DemoWordGrid> | null>(null)

/** 表からの知らせ（取り込んだ件数）。 */
function onGridNotice(message: string): void {
  notice.value = message
}

/** 取り込む語数と、飛ばす数。 */
const counts = computed(() => store.registerCounts)

const canSave = computed(() => {
  if (store.registerWords.length === 0) {
    return false
  }
  if (store.registerBookMode === 'NEW') {
    return store.registerBookName.trim() !== '' && store.registerUnitSize > 0
  }
  return store.registerBookName !== ''
})

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

/** 書籍や容量、重複の設定を変えたら Unit を計算し直す。 */
function recompute(): void {
  store.setRegisterHeadings([...store.registerHeadings])
}

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
  void gridRef.value
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
        <!-- ============ 単語の入力（Excel 風の表） ============ -->
        <div class="jp-demo-section" data-demo-step="1">
          <h3 class="jp-demo-section__title">単語</h3>
          <DemoWordGrid
            ref="gridRef"
            v-model:values="gridValues"
            @notice="onGridNotice"
          />
          <p v-if="notice !== ''" class="jp-demo-meta" data-demo-paste-notice>{{ notice }}</p>
        </div>

        <!-- ============ 書籍・Unit の設定 ============ -->
        <div class="jp-demo-section" data-demo-step="2">
          <h3 class="jp-demo-section__title">書籍と Unit</h3>

          <div class="jp-demo-fieldgrid">
            <label class="filter-item">
              <span class="filter-item__label">書籍</span>
              <select
                v-model="bookChoice" class="select" data-demo-book-select aria-label="書籍"
                @change="chooseBook(bookChoice)"
              >
                <option v-for="name in store.bookNameOptions" :key="name" :value="name">{{ name }}</option>
                <option :value="NEW_BOOK">新しい書籍を追加…</option>
              </select>
            </label>
            <label v-if="store.registerBookMode === 'NEW'" class="filter-item">
              <span class="filter-item__label">新しい書籍の名前</span>
              <input
                v-model="store.registerBookName" class="input" type="text"
                data-demo-book-name placeholder="例: 初中級のことば"
              >
            </label>
            <label class="filter-item">
              <span class="filter-item__label">1 Unit あたりの単語数</span>
              <input
                v-if="store.registerBookMode === 'NEW'" v-model.number="store.registerUnitSizeInput"
                class="input" type="number" min="1" max="500" style="width: 7rem" data-demo-unit-size
              >
              <span v-else class="jp-demo-meta" data-demo-unit-size-auto>
                {{ store.registerUnitSize }} 語（この書籍から自動で計算）
              </span>
            </label>
          </div>

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
        </div>

        <!-- ============ 登録する内容 ============ -->
        <div class="jp-demo-section" data-demo-step="3">
          <h3 class="jp-demo-section__title">登録する内容</h3>
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

          <p v-if="store.registerError !== ''" class="alert alert--danger" data-demo-register-error>
            {{ store.registerError }}
          </p>
        </div>
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
