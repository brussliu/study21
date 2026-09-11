<script setup lang="ts">
import { ref } from 'vue'

interface BatchRow {
  code: string
  type: string
  desc: string
  active: boolean
  loop: string
  parallel: boolean
  pageCode: string
}

interface BatchGroup {
  title: string
  rows: BatchRow[]
}

// 必須設定を持たない（システム系）タスクは「設定済み」扱い。
// それ以外は設定カタログに未設定値がある間「設定不足」。
const NO_SETTING_TASKS = new Set(['batL01', 'batR01', 'batR02', 'batR03', 'batR04'])

const groups: BatchGroup[] = [
  {
    title: '英語単語',
    rows: [
      { code: 'batC01', type: 'C（呼出）', desc: '英語→中国語翻訳（STY_単語情報）', active: true, loop: '—', parallel: true, pageCode: 'TRANSLATION' },
      { code: 'batC02', type: 'C（呼出）', desc: '英語→日本語翻訳（STY_単語情報）', active: true, loop: '—', parallel: true, pageCode: 'TRANSLATION' },
      { code: 'batC03', type: 'C（呼出）', desc: '英語発音取得（単語/例句 mp3）', active: true, loop: '—', parallel: true, pageCode: 'VOICE' },
      { code: 'batC05', type: 'C（呼出）', desc: 'AI中国語説明取得（STY_単語説明情報）', active: true, loop: '—', parallel: true, pageCode: 'WORD_EXPLANATION' },
      { code: 'batC06', type: 'C（呼出）', desc: 'AI日本語説明取得（STY_単語説明情報）', active: true, loop: '—', parallel: true, pageCode: 'WORD_EXPLANATION' },
      { code: 'batC19', type: 'C（呼出）', desc: '英単語教材取込 AI認識（Qwen-VL-OCR Batch）', active: true, loop: '—', parallel: true, pageCode: 'ENGLISH_WORD_TEXTBOOK_AI' },
      { code: 'batC21', type: 'C（呼出）', desc: '英単語詳細情報AI取得', active: true, loop: '—', parallel: true, pageCode: 'ENGLISH_WORD_DETAIL_AI' }
    ]
  },
  {
    title: '英単語問題生成',
    rows: [
      { code: 'batC04', type: 'C（呼出）', desc: '初級編 英訳中日問題生成（STY_単語質問情報）', active: true, loop: '—', parallel: true, pageCode: 'WORD_QUESTION' },
      { code: 'batC22', type: 'C（呼出）', desc: '中級編D.英訳中日問題データ生成', active: true, loop: '—', parallel: true, pageCode: 'WORD_QUESTION' },
      { code: 'batC23', type: 'C（呼出）', desc: '中級編E.文脈英訳問題データ生成', active: true, loop: '—', parallel: true, pageCode: 'WORD_QUESTION' },
      { code: 'batC10', type: 'C（呼出）', desc: 'AI誤問題下書き補完', active: true, loop: '—', parallel: true, pageCode: 'AI_MODEL' }
    ]
  },
  {
    title: '英熟語',
    rows: [
      { code: 'batC31', type: 'C（呼出）', desc: '英熟語標準化及び分類', active: true, loop: '—', parallel: true, pageCode: 'ENGLISH_PHRASE_DETAIL_AI' },
      { code: 'batC32', type: 'C（呼出）', desc: '英熟語詳細情報AI取得', active: true, loop: '—', parallel: true, pageCode: 'ENGLISH_PHRASE_DETAIL_AI' },
      { code: 'batC33', type: 'C（呼出）', desc: '熟語D.表現意味選択問題データ生成', active: true, loop: '—', parallel: true, pageCode: 'WORD_QUESTION' },
      { code: 'batC34', type: 'C（呼出）', desc: '熟語E.文脈意味選択問題データ生成', active: true, loop: '—', parallel: true, pageCode: 'WORD_QUESTION' }
    ]
  },
  {
    title: '日本語単語',
    rows: [
      { code: 'batC41', type: 'C（呼出）', desc: '日本語単語 詳細情報AI取得（A・B共通）', active: true, loop: '—', parallel: true, pageCode: 'JAPANESE_WORD_AI' },
      { code: 'batC42', type: 'C（呼出）', desc: '日本語単語 C.読み問題AI取得', active: true, loop: '—', parallel: true, pageCode: 'JAPANESE_WORD_AI' },
      { code: 'batC43', type: 'C（呼出）', desc: '日本語単語 D.文脈問題AI取得', active: true, loop: '—', parallel: true, pageCode: 'JAPANESE_WORD_AI' },
      { code: 'batC44', type: 'C（呼出）', desc: '日本語単語 E.漢字問題AI取得', active: true, loop: '—', parallel: true, pageCode: 'JAPANESE_WORD_AI' }
    ]
  },
  {
    title: '英作文',
    rows: [
      { code: 'batC11', type: 'C（呼出）', desc: '英作文 画像分類・OCR・主題タイトル生成', active: true, loop: '—', parallel: true, pageCode: 'ENGLISH_ESSAY' },
      { code: 'batC12', type: 'C（呼出）', desc: '英作文 英検基準AI添削', active: true, loop: '—', parallel: true, pageCode: 'ENGLISH_ESSAY' }
    ]
  },
  {
    title: '英語穴埋め',
    rows: [
      { code: 'batC13', type: 'C（呼出）', desc: '英語穴埋め問題 OCR・構造化', active: true, loop: '—', parallel: true, pageCode: 'ENGLISH_CLOZE' },
      { code: 'batC14', type: 'C（呼出）', desc: '英語穴埋め問題別AI解説生成', active: true, loop: '—', parallel: true, pageCode: 'ENGLISH_CLOZE' }
    ]
  },
  {
    title: '英語長文精読',
    rows: [
      { code: 'batC15', type: 'C（呼出）', desc: '英語長文精読 OCR結果集計・最終整形', active: true, loop: '—', parallel: true, pageCode: 'ENGLISH_READING_INTENSIVE' },
      { code: 'batC15-1', type: 'C（呼出）', desc: '英語長文精読 方式A 設問画像OCR・構造化', active: true, loop: '—', parallel: true, pageCode: 'ENGLISH_READING_INTENSIVE' },
      { code: 'batC15-2', type: 'C（呼出）', desc: '英語長文精読 方式A 設問画像OCR・構造化（一時無効）', active: false, loop: '—', parallel: true, pageCode: 'ENGLISH_READING_INTENSIVE' },
      { code: 'batC15-3', type: 'C（呼出）', desc: '英語長文精読 方式B OCR文字抽出・AI構造化', active: true, loop: '—', parallel: true, pageCode: 'ENGLISH_READING_INTENSIVE' },
      { code: 'batC16', type: 'C（呼出）', desc: '英語長文精読 基本情報・導読抽出', active: true, loop: '—', parallel: true, pageCode: 'ENGLISH_READING_INTENSIVE' },
      { code: 'batC17', type: 'C（呼出）', desc: '英語長文精読 解説・重点語彙生成', active: true, loop: '—', parallel: true, pageCode: 'ENGLISH_READING_INTENSIVE' },
      { code: 'batC18', type: 'C（呼出）', desc: '英語長文精読 設問解析', active: true, loop: '—', parallel: true, pageCode: 'ENGLISH_READING_INTENSIVE' }
    ]
  },
  {
    title: 'AI共通・OCR',
    rows: [
      { code: 'batC07', type: 'C（呼出）', desc: 'AI内容生成（STY_AI内容情報）', active: true, loop: '—', parallel: true, pageCode: 'AI_MODEL' },
      { code: 'batC09', type: 'C（呼出）', desc: 'AI OCR（画像/PDF文字認識）', active: true, loop: '—', parallel: true, pageCode: 'AI_MODEL' },
      { code: 'batC91', type: 'C（呼出）', desc: 'AI OCR（智谱OCR 共通処理）', active: true, loop: '—', parallel: true, pageCode: 'AI_MODEL' }
    ]
  },
  {
    title: '学習モニター',
    rows: [
      { code: 'batL02', type: 'L（循環）', desc: '学習モニター動画取込・スナップショット切出（5分ごと、最大5ファイル）', active: true, loop: '5分間隔', parallel: false, pageCode: 'STUDY_MONITOR' },
      { code: 'batL03', type: 'L（循環）', desc: '学習モニター スナップショットAI分析（5分ごと）', active: true, loop: '5分間隔', parallel: false, pageCode: 'STUDY_MONITOR' }
    ]
  },
  {
    title: 'システム',
    rows: [
      { code: 'batL01', type: 'L（循環）', desc: 'プロキシサービス（6時間ごと: 0/6/12/18時）', active: true, loop: '毎時 00分', parallel: false, pageCode: 'SYSTEM' },
      { code: 'batR01', type: 'R（定時）', desc: '「STY_日次情報」テーブル生成処理', active: true, loop: '—', parallel: false, pageCode: 'SYSTEM' },
      { code: 'batR02', type: 'R（定時）', desc: 'バッチ実行履歴・上網履歴クリーンアップ処理', active: true, loop: '—', parallel: false, pageCode: 'SYSTEM' },
      { code: 'batR03', type: 'R（定時）', desc: 'インターネット利用終了（23:30）', active: true, loop: '—', parallel: false, pageCode: 'SYSTEM' },
      { code: 'batR04', type: 'R（定時）', desc: 'インターネット利用開始（06:30）', active: true, loop: '—', parallel: false, pageCode: 'SYSTEM' },
      { code: 'batR05', type: 'R（定時）', desc: '学習タスク未実施リマインド（21:00）', active: true, loop: '—', parallel: false, pageCode: 'DAILY_REPORT' }
    ]
  }
]

const totalCount = groups.reduce((sum, group) => sum + group.rows.length, 0)

const allRows: BatchRow[] = groups.flatMap((group) => group.rows)

const tabs = [
  { key: 'all', title: 'すべて', rows: allRows },
  ...groups.map((group) => ({ key: group.title, title: group.title, rows: group.rows }))
]

const activeTab = ref('all')

function isSettingsComplete(row: BatchRow): boolean {
  return NO_SETTING_TASKS.has(row.code)
}
</script>

<template>
  <div class="prototype-screen prototype-screen--batch">
    <div class="search-panel">
      <div class="search-panel__head">
        <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>バッチ検索条件</h3>
        <div class="search-panel__actions">
          <button class="btn btn--primary" id="bSearch"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索</button>
          <button class="btn btn--secondary" id="bReload">再読込</button>
          <button class="btn btn--secondary" id="bLine">LINE送信テスト</button>
          <button class="btn btn--secondary" id="bReset">リセット</button>
        </div>
      </div>
      <div class="filters"><div class="filters__row">
        <span class="filter-item"><span class="filter-item__label">種別：</span><select class="select"><option>すべて</option><option>R（定時）</option><option>L（循環）</option><option>C（呼出）</option></select></span>
        <span class="filter-item"><span class="filter-item__label">状態：</span><select class="select"><option>すべて</option><option>待機中</option><option>実行中</option><option>正常終了</option><option>異常終了</option><option>スキップ</option></select></span>
        <span class="filter-item filter-item--grow"><span class="filter-item__label">キーワード：</span><input class="input" placeholder="タスクコード / 説明" style="flex:1"></span>
      </div></div>
    </div>

    <div class="tabs" role="tablist">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="tabs__tab"
        :class="{ 'is-active': activeTab === tab.key }"
        role="tab"
        :aria-selected="activeTab === tab.key"
        @click="activeTab = tab.key"
      >{{ tab.title }}<span class="badge badge--neutral">{{ tab.rows.length }}</span></button>
    </div>

    <section
      v-for="tab in tabs"
      :key="tab.key"
      class="tabs__panel"
      :class="{ 'is-active': activeTab === tab.key }"
      role="tabpanel"
    >
      <div class="table-section">
        <div class="table-section__head">
          <h3 class="table-section__title">バッチタスク一覧（{{ tab.title }}）</h3>
          <span class="table-section__meta">{{ tab.rows.length }} 件</span>
        </div>
        <div class="table-wrap"><table class="data-table">
          <thead><tr><th>タスクコード</th><th>種別</th><th>説明</th><th>有効</th><th>ループ設定</th><th>並列</th><th>設定ページ</th><th>設定</th><th>最新状態</th><th>最新開始時刻</th><th>最新終了時刻</th><th class="col-actions">操作</th></tr></thead>
          <tbody>
            <tr v-for="row in tab.rows" :key="row.code">
              <td class="cell-strong">{{ row.code }}</td>
              <td>{{ row.type }}</td>
              <td>{{ row.desc }}</td>
              <td class="align-center"><label class="switch"><input type="checkbox" :checked="row.active"><span class="switch__track"></span><span class="switch__thumb"></span></label></td>
              <td :class="{ 'cell-muted': row.loop === '—' }">{{ row.loop }}</td>
              <td class="align-center" :class="{ 'cell-muted': !row.parallel }">{{ row.parallel ? 'ON' : 'OFF' }}</td>
              <td class="cell-muted">{{ row.pageCode }}</td>
              <td>
                <span v-if="isSettingsComplete(row)" class="badge badge--success">設定済み</span>
                <span v-else class="badge badge--danger" :title="`${row.pageCode} の必須設定が未設定です`">設定不足</span>
              </td>
              <td><span class="badge badge--neutral">—</span></td>
              <td class="cell-muted">—</td>
              <td class="cell-muted">—</td>
              <td><button class="btn btn--secondary btn--sm" :data-rerun="row.code">再実行</button></td>
            </tr>
          </tbody>
        </table></div>
      </div>
    </section>

    <div class="batch-total cell-muted">全 {{ totalCount }} 件（10 グループ）</div>
  </div>
</template>

<style scoped>
.batch-total {
  font-size: var(--fs-sm);
  padding: var(--sp-2) var(--sp-1);
  text-align: right;
}
</style>
