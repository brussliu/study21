<script setup lang="ts">
import { computed, onBeforeUnmount, nextTick, onMounted, ref } from 'vue'
import { ApiError, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import { loadSettingFields, saveSettingFields } from '@/api/system-settings'
import { modelSlotOptions, withCurrentModelSlot } from '@/features/system-settings/modelSlots'
import {
  AI_TAB_OTHER,
  aiLabelOf,
  aiSliderOf,
  aiIsFullWidth,
  aiTabOf,
  aiTabsOf,
  sortAiFields,
  type AiTab
} from '@/features/system-settings/aiSettingsLayout'
import {
  createClassroomPreset,
  deleteClassroomPreset,
  listClassroomPresets,
  updateClassroomPreset,
  type ClassroomPresetRow
} from '@/api/classroomPresets'
import '@/features/classroom/classroom.css'

/**
 * システム設定【授業録音】分頁 — 授業録音 / AI 授業記録の設定。
 *
 * 画面はこのコンポーネントが描き（設定ランタイムの同カテゴリ `classroom_ai` は external）、
 * 値の読み書きは `/api/admin/setting/initSettings` / `saveSettings` を通す。送るフィールドキーは
 * `SettingPageFields`（サーバー側の対応表）に定義済みの **CLASSROOM_AI の 21 キー**（値は
 * `COM_設定情報` に既存）。未定義キーを送ると 400 になり、画面の他の設定もまとめて保存できなくなる。
 * 言語コード（`CLASSROOM_AI_LANG_*` の 6 キー）は**送らない**: 言語コードはコード側で固定に
 * なったため（日本語=ja-JP／中国語=zh-CN／英語=en-US）。サーバーは後方互換でこれらのキーを
 * 受け付けるが、もう使わない。
 *
 * レイアウトは設定ページの他機能（日本語単語AI）に合わせる:
 *   セクションカード（見出し＋説明）＋ 入力→出力バッジ ＋ フィールド（ラベル＋説明文＋入力）。
 *   **AI を呼ぶ 3 ブロック（STT / batC61 / batC62）は共通レイアウト**に揃える: TAB の名前と並び・
 *   項目の統一名称・数字項目のスライダー（刻みと単位）・TAB 内の並びは、すべて
 *   `aiSettingsLayout.ts`（唯一の定義）が決める。**このコンポーネントは規則を持たない**
 *   （項目の key・入力の形・min/max・説明だけを持つ）。タブの切替は**設定ランタイムの委譲ハンドラ**を
 *   そのまま使う（`data-setting-subsection` と `[data-method-tab]` / `[data-method-panel]`）。
 *   AI を呼ばないブロック（録音と保存・トリガー・プリセット管理）は今までどおり数値入力のまま。
 *   「使用モデル」は「AIモデル」ページのスロットを選ぶドロップダウンにする（生の入力にしない）。
 *   STT は **プロバイダー選択だけ**（接続情報＝モデル・URL・API Key は「AIモデル」ページの専用タブが持つ。
 *   利用者の指示で Google Speech-to-Text / Alibaba Paraformer-Realtime-V2 を AI モデルと同じ扱いにした）。
 *   ブラウザ認識のときは、サーバー側の認識にだけ要る項目（タイムアウト・話者分離）を出さない。
 *
 * **有効／無効（`classroomAiEnabled`）は画面に出さない**（機能は常に有効。サーバーの既定も true）。
 * 値は読み込んだものをそのまま送り返す（読み込めなかったときは**そのキーを送らない**＝
 * 空文字で上書きしない・失わせない）。
 *
 * 画面全体の【設定を保存】にも値を載せるため、`window.__study21SystemSettings.registerSection`
 * に値の取得・反映を登録する（登録が無いと、このセクションの変更が全体保存から漏れる）。
 *
 * 前置詞プリセットの追加・変更・削除はこの画面では保存しない（別の API が要る）。
 */
const toast = useToast()

/**
 * このセクションは設定ページの分類【授業録音】のパネルの中に出す。
 * パネルとマウント点（`[data-external-slot]`）は設定ランタイムが作るので、
 * 現れたら（作り直されたら）そこへ **Teleport** する。
 * 見つからないとき（このコンポーネントだけを載せたテストなど）は、その場に描く。
 */
const SLOT_SELECTOR = '[data-external-slot="classroom_ai"]'
const slot = ref<HTMLElement | null>(null)
/** 設定ページの中に居るか（パネルの置き場があるか）。 */
const inSettingsPage = ref(false)
/** Teleport できているか（できているときはパネルが同じ見出しを出すので、カードの見出しは出さない）。 */
const teleported = computed(() => slot.value !== null)
/** マウント点がまだ無い（読み込み中）ときは、下にぶら下がって見えないように隠す。 */
const waitingForSlot = computed(() => slot.value === null && inSettingsPage.value)

let slotObserver: MutationObserver | null = null

/** マウント点を探し直す（ランタイムがパネルを作り直したら新しい要素に入り直す）。 */
function resolveSlot(): void {
  inSettingsPage.value = document.getElementById('settingPanels') !== null
  const found = document.querySelector(SLOT_SELECTOR)
  const next = found instanceof HTMLElement ? found : null
  if (next !== slot.value) slot.value = next
}

onBeforeUnmount(() => {
  slotObserver?.disconnect()
  slotObserver = null
})

const loading = ref(true)
const saving = ref(false)
/** 読み込み・保存の結果を出す案内（保存できないときは理由を日本語で出す）。 */
const status = ref('')
/**
 * サーバーが返した値（そのまま持ち回るキーと、「使用モデル」のスロット名に使う）。
 * `collect()` はここに入っている値だけを「保存しても失わせない」対象にする。
 */
const loadedSettings = ref<Record<string, string>>({})

/**
 * 画面に編集欄が無いキー（読み込んだ値をそのまま送り返す）。
 * `classroomAiEnabled` は機能スイッチだが、**機能は常に有効**なので画面には出さない。
 */
const PRESERVED_KEYS = ['classroomAiEnabled']

/* ---------- 値（サーバーと読み書きする CLASSROOM_AI のキー） ---------- */

/**
 * 画面の値（画面に出さないキーも含む）。キーは `SettingPageFields` のフィールドキーそのもの。
 * 既定値は `COM_設定情報` の seed と同じ（サーバーが値を返さないときだけ使う）。
 */
const DEFAULT_VALUES: Record<string, string> = {
  classroomAiEnabled: 'true',
  classroomAiSttProvider: 'browser',
  // STT の接続情報（モデル・URL・API Key）は**「AIモデル」ページの専用タブ**が持つ。
  // このセクションは送らない・持たない（値はそのまま残る。利用者の指示で置き場を移した）
  classroomAiSttTimeoutSeconds: '60',
  classroomAiChunkSeconds: '20',
  classroomAiNoteEnabled: 'true',
  classroomAiTriggerIntervalMinutes: '5',
  classroomAiTriggerMinChars: '200',
  classroomAiTriggerCooldownMinutes: '3',
  classroomAiMaxRecordingMinutes: '120',
  classroomAiDailyLimitPerAccount: '0',
  classroomAiRetentionDays: '30',
  classroomAiViewScope: 'family',
  classroomAiNoteProvider: 'deepseek:1',
  classroomAiNoteSystemPrompt: 'あなたは授業の内容を整理する助手です。授業の書き起こしから、'
    + '次の 4 つのキーだけを持つ JSON オブジェクトを返してください。\n'
    + '{"テーマ": "...", "学習内容": "...", "先生の重点": "...", "宿題": "..."}\n'
    + 'それ以外の文字（解説やコードフェンス）は付けないでください。',
  classroomAiNoteUserPrompt: '授業の書き起こし（ここまで）:\n{transcript}',
  classroomAiNoteTimeoutSeconds: '120',
  classroomAiNoteMaxCompletionTokens: '2048',
  classroomAiSummarySystemPrompt: 'あなたは授業の内容を整理する助手です。授業全体の書き起こしから、'
    + '次の 4 つのキーだけを持つ JSON オブジェクトを返してください。\n'
    + '{"テーマ": "...", "学習内容": "...", "先生の重点": "...", "宿題": "..."}\n'
    + '宿題が無ければ空文字にしてください。それ以外の文字は付けないでください。',
  classroomAiSummaryUserPrompt: '授業全体の書き起こし:\n{transcript}'
}

/** 数値のキー（空欄のまま保存されたときに直前に読み込んだ値／seed の既定値で送る）。 */
const NUMBER_SEEDS: Record<string, number> = {
  classroomAiChunkSeconds: 20,
  classroomAiSttTimeoutSeconds: 60,
  classroomAiTriggerIntervalMinutes: 5,
  classroomAiTriggerMinChars: 200,
  classroomAiTriggerCooldownMinutes: 3,
  classroomAiMaxRecordingMinutes: 120,
  classroomAiDailyLimitPerAccount: 0,
  classroomAiRetentionDays: 30,
  classroomAiNoteTimeoutSeconds: 120,
  classroomAiNoteMaxCompletionTokens: 2048
}

const values = ref<Record<string, string>>({ ...DEFAULT_VALUES })
/** 直前に読み込んだ数値（空欄で保存されたときに使う）。 */
const numericLoaded: Record<string, string> = {}
/** トリガーキーワード（カンマ区切りの文字列と相互変換する）。 */
const triggerKeywords = ref<string[]>([])
const keywordInput = ref('')

/* ---------- 選択肢 ---------- */

/**
 * STT の選択肢は**3 つだけ**（利用者の指示）。
 *
 * 接続情報（モデル・URL・API Key）は選んだプロバイダーごとに「AIモデル」ページの専用タブが持つので、
 * ここは「どのエンジンで書き起こすか」だけを選ぶ。旧値（stub / whisper / azure / other）は
 * 保存済みの環境のためにサーバー側では受け付けるが、画面には出さない。
 */
const STT_PROVIDERS = [
  { value: 'browser', label: 'ブラウザ音声認識（Chrome / Edge・キー不要）' },
  { value: 'google', label: 'Google Speech-to-Text' },
  { value: 'alibaba', label: 'Alibaba Paraformer-Realtime-V2' }
]

/** サーバーが認識するプロバイダー（接続情報と認識の設定が要る）。 */
const SERVER_STT_PROVIDERS = ['google', 'alibaba']

/** サーバー側で認識するか（＝接続情報とタイムアウト・話者分離が要る）。 */
const serverStt = computed(() => SERVER_STT_PROVIDERS.includes(values.value.classroomAiSttProvider ?? ''))


/** 閲覧範囲（サーバーは今のところ 生徒＝自分／保護者＝家庭／管理者＝全体 で判定する）。 */
const VIEW_SCOPE_OPTIONS = [
  { value: 'self', label: '自分の記録だけ（家庭で共有しない）' },
  { value: 'family', label: '家庭で共有する（保護者も見られる）' }
]

/** 「使用モデル」の選択肢（「AIモデル」ページのスロット。日本語単語AI と同じ並び・同じラベル）。 */
const modelOptions = computed(() => withCurrentModelSlot(
  modelSlotOptions(loadedSettings.value), values.value.classroomAiNoteProvider ?? ''))

/* ---------- AI を呼ぶ 3 ブロック（STT / batC61 / batC62）の共通レイアウト ---------- */

/**
 * AI を呼ぶブロックの 1 項目。
 *
 * **TAB・統一名称・スライダーの刻みと単位・TAB 内の並びは `aiSettingsLayout.ts`（唯一の定義）が
 * 決めるので、ここには書かない。** ここに書くのは「項目の key・入力の形・数値の範囲（min/max）・
 * 説明文・操作用のフック」だけ。
 *
 * `interface` ではなく `type` にするのは、`aiSettingsLayout.ts` の `AiFieldLike`
 * （インデックス署名つき）へそのまま渡せるようにするため。
 */
type AiField = {
  key: string
  /** 入力の形。数値は `aiSliderOf` が仕様を返すので自動でスライダー（type=range）になる。 */
  type: 'select' | 'text' | 'password' | 'textarea' | 'number' | 'range'
  /** いまのラベル（`aiLabelOf` が統一名称を返すときはそちらを使う＝ここは控え）。 */
  label: string
  /** `aiLabelOf` を使わず、この名称のまま出す（AI モデルの選択ではない項目だけ）。 */
  keepLabel?: true
  /** 数値の範囲＝スライダーの min / max（サーバーが受け付ける範囲に合わせる）。 */
  min?: number
  max?: number
  /** 入力の id。スライダーは設定ランタイムが `setting_<key>` で引くので、必ずその形にする。 */
  id: string
  /** テスト・実機の操作用のフック（data-cr-set-xxx）。 */
  hook: string
  /** 選択肢（select のときだけ）。 */
  options?: () => { value: string; label: string }[]
  /** 入力欄の行数（textarea のときだけ）。 */
  rows?: number
  placeholder?: string
  /** 1 行の幅で出すか（モデル／プロバイダーの選択。`aiIsFullWidth` が決める）。 */
  full?: boolean
  /** 説明（1 段落 = 1 行。段落の数だけ `.setting-help` を出す）。 */
  helpLines: string[]
  /**
   * サーバーが STT を呼ぶときにだけ要る項目（モデル・Endpoint・API Key）。
   * ブラウザ認識（provider=browser）のときは使わないので、入力欄を無効にする。
   */
  needsServerConnection?: true
  /** 決まった TAB・スライダーの刻みと単位（`layoutField` が入れる）。 */
  tab?: AiTab
  step?: number
  suffix?: string
}

/**
 * STT（音声認識）ブロックの項目。
 *
 * `classroomAiSttProvider` だけは `aiLabelOf` が「使用モデル」を返すが、**ここは AI モデルの
 * スロット選択ではなく音声認識プロバイダーの選択**なので、いまの名称のまま出す（`keepLabel`）。
 * STT モデル・Endpoint・API Key は `aiLabelOf` が名称を返さないので、いまのラベルのままになる。
 */
const STT_FIELDS: AiField[] = [
  {
    key: 'classroomAiSttProvider',
    type: 'select',
    label: 'STT プロバイダー',
    keepLabel: true,
    id: 'cr-set-stt-provider',
    hook: 'data-cr-set-stt-provider',
    options: () => STT_PROVIDERS,
    helpLines: [
      'browser（ブラウザ音声認識・推奨）: この画面（Chrome / Edge）が Web Speech API で書き起こします。'
      + 'API Key もモデルも URL も要りません（録音の音声は今までどおりサーバーへ保存され、詳細画面で再生できます）。'
      + '1 つの言語だけを認識するので、混在モード（日本語＋英語など）は主言語で書き起こします。'
      + 'Firefox / Safari は非対応です（書き起こしが空になります）。',
      'google（Google Speech-to-Text）: サーバーが分塊を Google Cloud へ送って書き起こします。'
      + '接続情報（モデル・URL・API Key）は【AIモデル】ページの「Google Speech-to-Text」タブで設定します。'
      + 'モデル＝latest_long（既定）／URL＝https://speech.googleapis.com/v1/speech:recognize（既定）／'
      + 'API Key＝Google Cloud の API キー（Cloud Speech-to-Text API を有効にしたプロジェクトのキー）。',
      'alibaba（Alibaba Paraformer-Realtime-V2）: サーバーが分塊を DashScope の WebSocket へ送って書き起こします。'
      + '接続情報は【AIモデル】ページの「Alibaba Paraformer-Realtime-V2」タブで設定します。'
      + 'モデル＝paraformer-realtime-v2（既定）／URL＝wss://dashscope.aliyuncs.com/api-ws/v1/inference（既定）／'
      + 'API Key＝DashScope（阿里云百炼）の API Key。千問（Qwen）と同じキーを使えます。'
      + '中国語の授業に強いモデルです。',
      'どのプロバイダーも、設定したキーが正しいかは【AIモデル】ページの【接続テスト】で確かめられます。'
    ]
  },
  {
    key: 'classroomAiSttTimeoutSeconds',
    type: 'number',
    label: 'STT のタイムアウト（秒）',
    min: 30,
    max: 600,
    id: 'setting_classroomAiSttTimeoutSeconds',
    hook: 'data-cr-set-stt-timeout',
    needsServerConnection: true,
    helpLines: ['1 つの分塊を音声認識へ送って待つ最大秒数です（5〜300 秒。既定 60 秒）。']
  },
  {
    key: 'classroomAiChunkSeconds',
    type: 'number',
    label: '分塊の長さ（秒）',
    min: 5,
    max: 120,
    id: 'setting_classroomAiChunkSeconds',
    hook: 'data-cr-set-chunk-seconds',
    helpLines: ['録音を何秒ずつサーバーへ送るかです（5〜120 秒。既定 20 秒）。短いほど書き起こしが早く出ます。']
  }
]

/** batC61（授業中のフェーズノート）ブロックの項目。 */
const NOTE_FIELDS: AiField[] = [
  {
    key: 'classroomAiNoteProvider',
    type: 'select',
    label: '使用モデル',
    id: 'cr-set-ai-provider',
    hook: 'data-cr-set-ai-provider',
    options: () => modelOptions.value,
    helpLines: [
      '授業ノートを作るモデルを「AIモデル」ページのスロットから選びます（既定は DeepSeek の '
      + '1 番目）。モデル名・API Key・URL は「AIモデル」ページで設定できます。'
    ]
  },
  {
    key: 'classroomAiNoteTimeoutSeconds',
    type: 'number',
    label: 'リクエストタイムアウト（秒）',
    min: 30,
    max: 600,
    id: 'setting_classroomAiNoteTimeoutSeconds',
    hook: 'data-cr-set-note-timeout',
    helpLines: ['ノート生成の AI 呼び出し 1 回を待つ最大秒数です（30〜600 秒。既定 120 秒）。']
  },
  {
    key: 'classroomAiNoteMaxCompletionTokens',
    type: 'number',
    label: '最大出力 Token 数',
    min: 1024,
    max: 65536,
    id: 'setting_classroomAiNoteMaxCompletionTokens',
    hook: 'data-cr-set-note-max-tokens',
    helpLines: ['1 回の応答で受け取る最大の Token 数です（512〜16384。既定 2048）。']
  },
  {
    key: 'classroomAiNoteSystemPrompt',
    type: 'textarea',
    label: 'System Prompt',
    rows: 6,
    id: 'cr-set-prompt',
    hook: 'data-cr-set-prompt',
    helpLines: [
      '段階ノートを作るときの指示です。テーマ／学習内容／先生の重点／宿題 の 4 つのキーだけを'
      + '持つ JSON を返させます。'
    ]
  },
  {
    key: 'classroomAiNoteUserPrompt',
    type: 'textarea',
    label: 'User Prompt',
    rows: 4,
    id: 'cr-set-note-user-prompt',
    hook: 'data-cr-set-note-user-prompt',
    helpLines: ['{transcript} を、その時点までの書き起こしに置き換えて送ります。']
  }
]

/** batC62（最終まとめ）ブロックの項目（プロンプト 2 つだけ。モデル・タイムアウトは batC61 と共通）。 */
const SUMMARY_FIELDS: AiField[] = [
  {
    key: 'classroomAiSummarySystemPrompt',
    type: 'textarea',
    label: 'System Prompt',
    rows: 6,
    id: 'cr-set-summary-prompt',
    hook: 'data-cr-set-summary-prompt',
    helpLines: [
      '最終まとめを作るときの指示です。ここも 4 つのキーだけを持つ JSON を返させます'
      + '（宿題が無いときは空文字にさせます）。'
    ]
  },
  {
    key: 'classroomAiSummaryUserPrompt',
    type: 'textarea',
    label: 'User Prompt',
    rows: 4,
    id: 'cr-set-summary-user-prompt',
    hook: 'data-cr-set-summary-user-prompt',
    helpLines: ['授業全体の書き起こしに {transcript} を置き換えて送ります。']
  }
]

/**
 * 録音と保存（上限・保存期間）の数字項目。
 *
 * AI を呼ぶブロックではないが、**数字はスライダー**にする（利用者の指示 2026-09-18）。
 * 刻みと単位は AI のブロックの規則（`aiSettingsLayout.ts` の `aiSliderOf`）に合わせる。
 * `layoutField` を通さない（AI のブロックではない）ので、step と suffix はここで決める。
 */
const RECORD_RANGE_FIELDS: AiField[] = [
  {
    key: 'classroomAiMaxRecordingMinutes', type: 'range', label: '録音最大時間（分）',
    min: 1, max: 240, step: 1, suffix: '分',
    id: 'setting_classroomAiMaxRecordingMinutes', hook: 'data-cr-set-max-minutes',
    helpLines: [
      'この時間を超える分塊はサーバーが拒否します（1〜240 分。既定 120 分）。',
      '録音の画面もこの時間で自動的に止まります。'
    ]
  },
  {
    key: 'classroomAiDailyLimitPerAccount', type: 'range', label: '日次の上限（回）',
    min: 0, max: 100, step: 1, suffix: '回',
    id: 'setting_classroomAiDailyLimitPerAccount', hook: 'data-cr-set-daily-limit',
    helpLines: [
      '1 アカウントが 1 日に作れる授業の数です（0〜100。0 は無制限）。上限に達すると作成が拒否されます。'
    ]
  },
  {
    key: 'classroomAiRetentionDays', type: 'range', label: '保存期間（日）',
    min: 1, max: 365, step: 1, suffix: '日',
    id: 'setting_classroomAiRetentionDays', hook: 'data-cr-set-retention',
    helpLines: [
      '録音の音声とノートを残す日数です（1〜365 日。既定 30 日）。過ぎたものは掃除のバッチが消します。'
    ]
  }
]

/** フェーズ分析のトリガーの数字項目（同じくスライダー）。 */
const TRIGGER_RANGE_FIELDS: AiField[] = [
  {
    key: 'classroomAiTriggerIntervalMinutes', type: 'range', label: '間隔トリガー（分）',
    min: 3, max: 20, step: 1, suffix: '分',
    id: 'setting_classroomAiTriggerIntervalMinutes', hook: 'data-cr-set-interval',
    helpLines: ['直近のノートからこの時間が過ぎたらノートを更新します（3〜20 分。既定 5 分）。']
  },
  {
    key: 'classroomAiTriggerMinChars', type: 'range', label: '文字量トリガー（文字）',
    min: 100, max: 10000, step: 100, suffix: '文字',
    id: 'setting_classroomAiTriggerMinChars', hook: 'data-cr-set-char',
    helpLines: ['追記された書き起こしがこの文字数に達したらノートを更新します（100〜10000 文字。既定 200 文字）。']
  },
  {
    key: 'classroomAiTriggerCooldownMinutes', type: 'range', label: 'クールダウン（分）',
    min: 1, max: 30, step: 1, suffix: '分',
    id: 'setting_classroomAiTriggerCooldownMinutes', hook: 'data-cr-set-cooldown',
    helpLines: ['ノートを作った直後は、この時間が過ぎるまで次の更新を抑えます（1〜30 分。既定 3 分）。']
  }
]

/** 1 項目を共通レイアウトへ揃える（TAB と統一名称は `aiSettingsLayout.ts` が決める）。 */
function layoutField(field: AiField): AiField {
  const slider = aiSliderOf(field.key)
  return {
    ...field,
    tab: aiTabOf(field.key),
    // 現在のラベルは「統一名称が無いときの控え」。STT プロバイダーだけは統一名称を当てない。
    label: field.keepLabel === true ? field.label : (aiLabelOf(field.key) ?? field.label),
    // モデル／プロバイダーの選択は 1 行（図形管理・runtime と同じ規則）
    full: field.full ?? aiIsFullWidth(field.key),
    // 数値はスライダー（刻みと単位は aiSliderOf の決め打ち）
    type: slider !== undefined && field.type === 'number' ? 'range' : field.type,
    step: slider?.step,
    suffix: slider?.suffix
  }
}

/**
 * ブラウザ認識（provider=browser）のときは、サーバー側の認識にだけ要る項目を**出さない**
 * （利用者の指示: 設定が要らない項目は隠す）。タイムアウトと話者分離はサーバーが STT を呼ぶときだけ使う。
 */
function fieldHidden(field: AiField): boolean {
  return field.needsServerConnection === true && !serverStt.value
}

/**
 * いま選んでいるサーバー側 STT の接続情報（【AIモデル】ページの値。ここは表示だけ）。
 *
 * 画面は「どこで何を設定するか」を出すために読み、**保存はしない**（送ると 400 の原因になるうえ、
 * 置き場は AIモデルページの 1 か所に決めたいため）。
 */
const sttConnectionInfo = computed(() => {
  const provider = values.value.classroomAiSttProvider ?? ''
  if (provider === 'google') {
    const apiKey = String(loadedSettings.value.googleSttApiKey ?? '')
    return {
      tab: 'Google Speech-to-Text',
      model: String(loadedSettings.value.googleSttModel ?? '') || 'latest_long（既定）',
      url: String(loadedSettings.value.googleSttUrl ?? '') || 'https://speech.googleapis.com/v1/speech:recognize（既定）',
      hasApiKey: apiKey.trim() !== ''
    }
  }
  if (provider === 'alibaba') {
    const apiKey = String(loadedSettings.value.alibabaSttApiKey ?? '')
    return {
      tab: 'Alibaba Paraformer-Realtime-V2',
      model: String(loadedSettings.value.alibabaSttModel ?? '') || 'paraformer-realtime-v2（既定）',
      url: String(loadedSettings.value.alibabaSttUrl ?? '') || 'wss://dashscope.aliyuncs.com/api-ws/v1/inference（既定）',
      hasApiKey: apiKey.trim() !== ''
    }
  }
  return null
})

/** ブロックの項目を TAB ごとに分ける（TAB の並びも項目の順も `aiSettingsLayout.ts` の固定順）。 */
function tabGroupsOf(fields: readonly AiField[]): { tab: AiTab; fields: AiField[] }[] {
  const ordered = sortAiFields(fields.map(layoutField))
  return aiTabsOf(ordered).map((tab) => ({ tab, fields: ordered.filter((field) => field.tab === tab) }))
}

/**
 * STT の「その他」TAB に何が入るかの短い案内（AI 以外の設定がここに来るため）。
 * TAB の綴りは画面側で持たず、`aiSettingsLayout.ts` の定数を使う。
 */
const STT_TAB_NOTICES: Partial<Record<AiTab, string>> = {
  [AI_TAB_OTHER]: 'この TAB は AI の呼び出し方ではなく、音声の送り方（録音をサーバーへ送る単位）の設定です。'
}

const sttGroups = tabGroupsOf(STT_FIELDS)
const noteGroups = tabGroupsOf(NOTE_FIELDS)
const summaryGroups = tabGroupsOf(SUMMARY_FIELDS)

/** スライダーの値（空欄のときは今までどおり直前に読み込んだ値／seed の既定値で埋める）。 */
function rangeValue(field: AiField): string {  const parsed = Number(numberText(field.key))
  const min = field.min ?? 0
  const max = field.max ?? parsed
  if (!Number.isFinite(parsed)) return String(min)
  return String(Math.min(max, Math.max(min, parsed)))
}

/** スライダーの右側に出す値（値＋単位）。単体で描いても正しく見えるようにコンポーネント側で作る。 */
function rangeText(field: AiField): string {
  return rangeValue(field) + (field.suffix ?? '')
}

/** スライダーの塗り（設定ランタイムと同じ CSS 変数を、単体で描いたときも正しく入れる）。 */
function rangeProgress(field: AiField): string {
  const min = Number(field.min ?? 0)
  const max = Number(field.max ?? min)
  const value = Number(rangeValue(field))
  const percent = max > min ? ((value - min) / (max - min)) * 100 : 0
  return `${percent}%`
}

/** テスト・実機の操作用のフック（data-cr-set-xxx）を属性にする。 */
function hookOf(field: AiField): Record<string, string> {
  return { [field.hook]: '' }
}

function addKeyword(): void {
  const value = keywordInput.value.trim()
  if (value === '') return
  if (triggerKeywords.value.includes(value)) return
  triggerKeywords.value.push(value)
  keywordInput.value = ''
}

function removeKeyword(index: number): void {
  triggerKeywords.value.splice(index, 1)
}

/* ---------- 前置詞プリセット管理（admin-api の CRUD。設定の保存とは別の API） ---------- */

const presets = ref<ClassroomPresetRow[]>([])
const presetsLoading = ref(false)
/** 一覧の読み込み・操作の失敗（日本語。空なら問題なし）。 */
const presetsError = ref('')
const presetFormOpen = ref(false)
const presetSaving = ref(false)
/** 編集中のプリセット ID（null なら追加）。 */
const presetEditingId = ref<number | null>(null)
const presetNameInput = ref('')
const presetTextInput = ref('')
const presetOrderInput = ref<number | string>(1)
const presetError = ref('')
/** 削除の確認ダイアログに出すプリセット（null なら閉じている）。 */
const presetDeleteTarget = ref<ClassroomPresetRow | null>(null)

/** 一覧をサーバーから読み直す（追加・更新・削除のあとに必ず呼ぶ）。 */
async function loadPresets(): Promise<void> {
  presetsLoading.value = true
  try {
    presets.value = await listClassroomPresets()
    presetsError.value = ''
  } catch (caught) {
    presets.value = []
    presetsError.value = messageOf(caught, '前置詞プリセットを取得できませんでした。')
  } finally {
    presetsLoading.value = false
  }
}

function openAddPreset(): void {
  presetEditingId.value = null
  presetNameInput.value = ''
  presetTextInput.value = ''
  // 表示順は今の最後の次にする（サーバーは表示順で並べる）
  const last = presets.value.reduce((max, item) => Math.max(max, item.displayOrder), 0)
  presetOrderInput.value = last + 1
  presetError.value = ''
  presetFormOpen.value = true
}

function openEditPreset(preset: ClassroomPresetRow): void {
  presetEditingId.value = preset.presetId
  presetNameInput.value = preset.name
  presetTextInput.value = preset.text
  presetOrderInput.value = preset.displayOrder
  presetError.value = ''
  presetFormOpen.value = true
}

/** 【保存】: 追加（POST）または更新（PUT）してから一覧を取り直す。 */
async function savePreset(): Promise<void> {
  if (presetSaving.value) return
  const name = presetNameInput.value.trim()
  if (name === '') {
    presetError.value = 'プリセット名を入力してください。'
    return
  }
  const order = Number(presetOrderInput.value)
  const body = {
    name,
    text: presetTextInput.value.trim(),
    displayOrder: Number.isFinite(order) ? Math.round(order) : 1
  }
  presetSaving.value = true
  presetError.value = ''
  try {
    if (presetEditingId.value === null) {
      await createClassroomPreset(body)
      toast.success('前置詞プリセットを追加しました。')
    } else {
      await updateClassroomPreset(presetEditingId.value, body)
      toast.success('前置詞プリセットを更新しました。')
    }
    presetFormOpen.value = false
    presetEditingId.value = null
    await loadPresets()
  } catch (caught) {
    presetError.value = messageOf(caught, '前置詞プリセットを保存できませんでした。')
    toast.danger(presetError.value)
  } finally {
    presetSaving.value = false
  }
}

/** 【削除】: 確認のダイアログを開く（背景クリックでは閉じない）。 */
function deletePreset(preset: ClassroomPresetRow): void {
  if (presetSaving.value) return
  presetDeleteTarget.value = preset
}

/** 確認のダイアログから実際に消す（消したら一覧を取り直す）。 */
async function confirmDeletePreset(): Promise<void> {
  const target = presetDeleteTarget.value
  if (target === null || presetSaving.value) return
  presetSaving.value = true
  presetsError.value = ''
  try {
    await deleteClassroomPreset(target.presetId)
    presetDeleteTarget.value = null
    toast.success('前置詞プリセットを削除しました。')
    await loadPresets()
  } catch (caught) {
    presetsError.value = messageOf(caught, '前置詞プリセットを削除できませんでした。')
    toast.danger(presetsError.value)
  } finally {
    presetSaving.value = false
  }
}

/* ---------- 保存（サーバーと読み書きする CLASSROOM_AI のキー） ---------- */

/** 数値欄の値を送る形にする（空なら直前に読み込んだ値、無ければ seed の既定値）。 */
function numberText(key: string): string {
  const raw = String(values.value[key] ?? '').trim()
  const parsed = Number(raw)
  if (raw !== '' && Number.isFinite(parsed)) return String(parsed)
  return numericLoaded[key] ?? String(NUMBER_SEEDS[key] ?? 0)
}

/** いまの画面の値をサーバーへ送る形にする（文字列だけ。型・範囲はサーバーが再検証する）。 */
function collect(): Record<string, string> {
  const result: Record<string, string> = {}
  for (const key of Object.keys(DEFAULT_VALUES)) {
    if (PRESERVED_KEYS.includes(key)) continue
    if (key in NUMBER_SEEDS) {
      result[key] = numberText(key)
    } else {
      result[key] = String(values.value[key] ?? '').trim()
    }
  }
  // 画面に出さないキーは、読み込んだ値をそのまま送り返す（読めなかったときは送らない）
  for (const key of PRESERVED_KEYS) {
    const loaded = loadedSettings.value[key]
    if (loaded !== undefined && loaded !== '') result[key] = loaded
  }
  result.classroomAiTriggerKeywords = triggerKeywords.value.join(',')
  return result
}

/** サーバーから来た値を画面へ反映する（知らないキーは無視する）。 */
function apply(next: Record<string, string> | null | undefined): void {
  if (next === null || next === undefined) return
  loadedSettings.value = { ...loadedSettings.value, ...next }
  const merged = { ...values.value }
  for (const key of Object.keys(DEFAULT_VALUES)) {
    const value = next[key]
    if (value === undefined || value === null || value === '') continue
    if (key in NUMBER_SEEDS) {
      const parsed = Number(value)
      if (!Number.isFinite(parsed) || parsed < 0) continue
      const rounded = String(Math.round(parsed))
      numericLoaded[key] = rounded
      merged[key] = rounded
    } else {
      merged[key] = String(value)
    }
  }
  values.value = merged
  if (next.classroomAiTriggerKeywords !== undefined) {
    triggerKeywords.value = String(next.classroomAiTriggerKeywords)
      .split(',').map((item) => item.trim()).filter((item) => item !== '')
  }
}

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

/** このセクションのキーを保存する。 */
async function save(): Promise<void> {
  if (saving.value) return
  saving.value = true
  status.value = ''
  try {
    const response = await saveSettingFields(collect())
    apply(response.data.settings)
    status.value = '設定を保存しました。'
    toast.success('授業録音の設定を保存しました。')
  } catch (caught) {
    status.value = messageOf(caught, '設定を保存できませんでした。')
    toast.danger(status.value)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await nextTick()
  resolveSlot()
  if (typeof MutationObserver !== 'undefined') {
    // 設定ランタイムは読み込み後と【再読込】のたびにパネルを作り直すので、その都度入り直す
    slotObserver = new MutationObserver(() => { resolveSlot() })
    slotObserver.observe(document.body, { childList: true, subtree: true })
  }
  // 画面全体の【設定を保存】にこのセクションの値を載せる（値はこの画面が持っているため）。
  // フィールドキーはサーバーの SettingPageFields（SettingPageFields.java）と一致させる。
  // 未定義のキーを送ると 400 になり、画面の他の設定もまとめて保存できなくなる。
  window.__study21SystemSettings?.registerSection?.({ getValues: collect, applyValues: apply })
  try {
    const response = await loadSettingFields()
    apply(response.data.settings)
    status.value = ''
  } catch (caught) {
    // 設定 API に届かないときは初期値のまま使えるようにする（保存時に理由が出る）
    status.value = messageOf(caught, '設定を読み込めませんでした。')
  } finally {
    loading.value = false
  }
  // 前置詞プリセットは別 API（admin-api）。設定の保存とは独立して読み書きする
  await loadPresets()
})
</script>

<template>
  <Teleport :to="slot ?? 'body'" :disabled="teleported === false">
    <section
      :class="teleported ? 'cr-set-panel' : 'card cr-set'" :hidden="waitingForSlot" data-cr-set
    >
      <div v-if="!teleported" class="card__header cr-list-head">
        <h2 class="card__title"><AppIcon name="mic" size="sm" /> AI 授業記録（授業録音）</h2>
        <span class="badge badge--neutral" data-cr-set-badge>授業録音</span>
      </div>

      <!-- ===================== 録音と保存 ===================== -->
      <section class="setting-batch-section" data-setting-subsection="classroom-record">
        <header class="setting-batch-section-head">
          <div class="setting-batch-section-icon"><AppIcon name="mic" /></div>
          <div>
            <h4>録音と保存（上限・保存期間）</h4>
            <p>録音できる長さ・1 日の回数・保存期間を決めます。</p>
          </div>
        </header>
        <div class="setting-field-grid">
          <!-- 数字はスライダー（AI のブロックと同じ見た目・同じ刻み。利用者の指示 2026-09-18） -->
          <div
            v-for="field in RECORD_RANGE_FIELDS" :key="field.key"
            class="setting-field setting-range-field"
          >
            <label class="setting-label" :for="field.id">
              <span>{{ field.label }}</span><small class="setting-field-key">{{ field.key }}</small>
            </label>
            <div class="setting-range-row">
              <input
                :id="field.id" v-model="values[field.key]" class="setting-range" type="range"
                :min="field.min" :max="field.max" :step="field.step" :data-suffix="field.suffix"
                :style="{ '--setting-range-progress': rangeProgress(field) }" v-bind="hookOf(field)"
              >
              <output
                :id="`${field.id}_value`" class="setting-range-value" :for="field.id"
                :data-suffix="field.suffix"
              >{{ rangeText(field) }}</output>
            </div>
            <p v-for="(line, index) in field.helpLines" :key="index" class="setting-help">{{ line }}</p>
          </div>

          <div class="setting-field">
            <label class="setting-label" for="cr-set-view-scope">
              <span>閲覧範囲</span><small class="setting-field-key">classroomAiViewScope</small>
            </label>
            <select
              id="cr-set-view-scope" v-model="values.classroomAiViewScope"
              class="setting-control setting-dropdown" data-cr-set-view-scope
            >
              <option v-for="option in VIEW_SCOPE_OPTIONS" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
            <p class="setting-help">
              記録を見られる範囲の予約値です。いまのサーバーはこの値に関わらず
              <strong>生徒＝自分／保護者＝家庭／管理者＝全体</strong> で判定します（切替の実装は別途）。
            </p>
          </div>
        </div>
      </section>

      <!-- ===================== STT（音声認識） ===================== -->
      <section class="setting-batch-section" data-setting-subsection="classroom-stt">
        <header class="setting-batch-section-head">
          <div class="setting-batch-section-icon"><AppIcon name="list" /></div>
          <div>
            <h4>STT（音声認識）</h4>
            <p>
              録音の分塊を音声認識エンジンへ送り、時刻つきの書き起こし（セグメント）を作ります。
              ここでは<strong>どのエンジンで書き起こすか</strong>だけを選びます。
              接続情報（モデル・URL・API Key）は<strong>【AIモデル】ページの専用タブ</strong>で設定します
              （Google Speech-to-Text ／ Alibaba Paraformer-Realtime-V2）。
              言語モードごとの言語コードは<strong>コード側で固定</strong>です（日本語＝ja-JP／中国語＝zh-CN／
              英語＝en-US。中国語＋英語は zh-CN、日本語＋英語は ja-JP）。
            </p>
          </div>
        </header>

        <!-- TAB の名前・並びと、TAB の中の項目は aiSettingsLayout.ts の固定順 -->
        <div class="setting-method-tabs setting-method-tabs-wtb">
          <div class="setting-tabs-buttons">
            <button
              v-for="(group, index) in sttGroups" :key="group.tab" type="button"
              :class="{ active: index === 0 }" :data-method-tab="group.tab"
            >
              {{ group.tab }}
            </button>
          </div>
          <span class="setting-io-badge">
            <AppIcon name="mic" size="sm" /> 授業の音声（分塊）
            <AppIcon name="chevron-right" size="sm" /> 書き起こし（セグメント）
          </span>
        </div>

        <section
          v-for="(group, index) in sttGroups" :key="group.tab" class="setting-method-tab-panel"
          :data-method-panel="group.tab" :hidden="index !== 0"
        >
          <div class="setting-field-grid">
            <p v-if="STT_TAB_NOTICES[group.tab] !== undefined" class="setting-help full">
              {{ STT_TAB_NOTICES[group.tab] }}
            </p>
            <!-- サーバー側で認識するときだけ: どこで接続情報を設定するかと、いまの値を見せる -->
            <p
              v-if="group.tab === '基本設定' && sttConnectionInfo !== null" class="setting-help full"
              data-cr-set-stt-connection
            >
              <AppIcon name="info" size="sm" />
              【AIモデル】ページの「{{ sttConnectionInfo.tab }}」タブで設定します。
              いまの値: モデル＝{{ sttConnectionInfo.model }} ／ URL＝{{ sttConnectionInfo.url }} ／
              API Key＝<strong>{{ sttConnectionInfo.hasApiKey ? '設定済み' : '未設定（設定するまで書き起こしは失敗します）' }}</strong>
            </p>
            <div
              v-for="field in group.fields" v-show="!fieldHidden(field)" :key="field.key" class="setting-field"
              :class="{ full: field.full || field.type === 'textarea', 'setting-range-field': field.type === 'range' }"
            >
              <label class="setting-label" :for="field.id">
                <span>{{ field.label }}</span>
                <small class="setting-field-key">{{ field.key }}</small>
              </label>

              <select
                v-if="field.type === 'select'" :id="field.id" v-model="values[field.key]"
                class="setting-control setting-dropdown" v-bind="hookOf(field)"
              >
                <option v-for="option in (field.options?.() ?? [])" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>

              <textarea
                v-else-if="field.type === 'textarea'" :id="field.id" v-model="values[field.key]"
                class="setting-control" :rows="field.rows ?? 4" spellcheck="false" v-bind="hookOf(field)"
              ></textarea>

              <div v-else-if="field.type === 'range'" class="setting-range-row">
                <input
                  :id="field.id" v-model="values[field.key]" class="setting-range" type="range"
                  :min="field.min" :max="field.max" :step="field.step" :data-suffix="field.suffix"
                  :style="{ '--setting-range-progress': rangeProgress(field) }" v-bind="hookOf(field)"
                >
                <output :id="`${field.id}_value`" class="setting-range-value" :for="field.id" :data-suffix="field.suffix">{{ rangeText(field) }}</output>
              </div>

              <input
                v-else :id="field.id" v-model="values[field.key]" class="setting-control"
                :type="field.type" :placeholder="field.placeholder" autocomplete="off"
                v-bind="hookOf(field)"
              >

              <p v-for="(line, lineIndex) in field.helpLines" :key="lineIndex" class="setting-help">{{ line }}</p>
            </div>
          </div>
        </section>
      </section>

      <!-- ===================== batC61（フェーズノート） ===================== -->
      <section class="setting-batch-section" data-setting-subsection="classroom-note-phase">
        <header class="setting-batch-section-head">
          <div class="setting-batch-section-icon"><AppIcon name="wand" /></div>
          <div>
            <h4>batC61（授業中のフェーズノート）</h4>
            <p>
              録音中に、その時点までの書き起こしから「テーマ／学習内容／先生の重点／宿題」の 4 段落を
              作ります。API Key と URL は「AIモデル」ページの接続設定を共通利用します。
              ノートを更新するタイミング（間隔・文字量・キーワード）は
              「フェーズ分析のトリガー」にまとめています。
            </p>
          </div>
        </header>

        <!-- TAB は 基本設定 / System Prompt / User Prompt（項目の無い TAB は出さない） -->
        <div class="setting-method-tabs setting-method-tabs-wtb">
          <div class="setting-tabs-buttons">
            <button
              v-for="(group, index) in noteGroups" :key="group.tab" type="button"
              :class="{ active: index === 0 }" :data-method-tab="group.tab"
            >
              {{ group.tab }}
            </button>
          </div>
          <span class="setting-io-badge">
            <AppIcon name="list" size="sm" /> 授業の書き起こし（区間）
            <AppIcon name="chevron-right" size="sm" /> 4 段落のノート（JSON）
          </span>
        </div>

        <section
          v-for="(group, index) in noteGroups" :key="group.tab" class="setting-method-tab-panel"
          :data-method-panel="group.tab" :hidden="index !== 0"
        >
          <div class="setting-field-grid">
            <div
              v-for="field in group.fields" :key="field.key" class="setting-field"
              :class="{ full: field.full || field.type === 'textarea', 'setting-range-field': field.type === 'range' }"
            >
              <label class="setting-label" :for="field.id">
                <span>{{ field.label }}</span>
                <small class="setting-field-key">{{ field.key }}</small>
              </label>

              <select
                v-if="field.type === 'select'" :id="field.id" v-model="values[field.key]"
                class="setting-control setting-dropdown" v-bind="hookOf(field)"
              >
                <option v-for="option in (field.options?.() ?? [])" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>

              <textarea
                v-else-if="field.type === 'textarea'" :id="field.id" v-model="values[field.key]"
                class="setting-control" :rows="field.rows ?? 4" spellcheck="false" v-bind="hookOf(field)"
              ></textarea>

              <div v-else-if="field.type === 'range'" class="setting-range-row">
                <input
                  :id="field.id" v-model="values[field.key]" class="setting-range" type="range"
                  :min="field.min" :max="field.max" :step="field.step" :data-suffix="field.suffix"
                  :style="{ '--setting-range-progress': rangeProgress(field) }" v-bind="hookOf(field)"
                >
                <output :id="`${field.id}_value`" class="setting-range-value" :for="field.id" :data-suffix="field.suffix">{{ rangeText(field) }}</output>
              </div>

              <input
                v-else :id="field.id" v-model="values[field.key]" class="setting-control"
                :type="field.type" :placeholder="field.placeholder" autocomplete="off" v-bind="hookOf(field)"
              >

              <p v-for="(line, lineIndex) in field.helpLines" :key="lineIndex" class="setting-help">{{ line }}</p>
            </div>
          </div>
        </section>
      </section>

      <!-- ===================== batC62（最終まとめ） ===================== -->
      <section class="setting-batch-section" data-setting-subsection="classroom-note-final">
        <header class="setting-batch-section-head">
          <div class="setting-batch-section-icon"><AppIcon name="check-circle" /></div>
          <div>
            <h4>batC62（最終まとめ）</h4>
            <p>
              授業を終えたときに、授業全体の書き起こしから最終まとめ（テーマ／学習内容／先生の重点／宿題）を
              作ります。使用モデル・リクエストタイムアウト・最大出力 Token 数は
              <strong>batC61 と共通</strong>です（上の「batC61（授業中のフェーズノート）」の基本設定で
              設定します）。ここで設定するのは最終まとめ用のプロンプトです。
            </p>
          </div>
        </header>

        <!-- TAB は System Prompt / User Prompt の 2 つ（設定項目がプロンプトだけのため） -->
        <div class="setting-method-tabs setting-method-tabs-wtb">
          <div class="setting-tabs-buttons">
            <button
              v-for="(group, index) in summaryGroups" :key="group.tab" type="button"
              :class="{ active: index === 0 }" :data-method-tab="group.tab"
            >
              {{ group.tab }}
            </button>
          </div>
          <span class="setting-io-badge">
            <AppIcon name="list" size="sm" /> 授業全体の書き起こし
            <AppIcon name="chevron-right" size="sm" /> 4 段落のまとめ（JSON）
          </span>
        </div>

        <section
          v-for="(group, index) in summaryGroups" :key="group.tab" class="setting-method-tab-panel"
          :data-method-panel="group.tab" :hidden="index !== 0"
        >
          <div class="setting-field-grid">
            <div
              v-for="field in group.fields" :key="field.key" class="setting-field"
              :class="{ full: field.full || field.type === 'textarea', 'setting-range-field': field.type === 'range' }"
            >
              <label class="setting-label" :for="field.id">
                <span>{{ field.label }}</span>
                <small class="setting-field-key">{{ field.key }}</small>
              </label>

              <select
                v-if="field.type === 'select'" :id="field.id" v-model="values[field.key]"
                class="setting-control setting-dropdown" v-bind="hookOf(field)"
              >
                <option v-for="option in (field.options?.() ?? [])" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>

              <textarea
                v-else-if="field.type === 'textarea'" :id="field.id" v-model="values[field.key]"
                class="setting-control" :rows="field.rows ?? 4" spellcheck="false" v-bind="hookOf(field)"
              ></textarea>

              <div v-else-if="field.type === 'range'" class="setting-range-row">
                <input
                  :id="field.id" v-model="values[field.key]" class="setting-range" type="range"
                  :min="field.min" :max="field.max" :step="field.step" :data-suffix="field.suffix"
                  :style="{ '--setting-range-progress': rangeProgress(field) }" v-bind="hookOf(field)"
                >
                <output :id="`${field.id}_value`" class="setting-range-value" :for="field.id" :data-suffix="field.suffix">{{ rangeText(field) }}</output>
              </div>

              <input
                v-else :id="field.id" v-model="values[field.key]" class="setting-control"
                :type="field.type" :placeholder="field.placeholder" autocomplete="off" v-bind="hookOf(field)"
              >

              <p v-for="(line, lineIndex) in field.helpLines" :key="lineIndex" class="setting-help">{{ line }}</p>
            </div>
          </div>
        </section>
      </section>

      <!-- ===================== フェーズ分析のトリガー ===================== -->
      <section class="setting-batch-section" data-setting-subsection="classroom-trigger">
        <header class="setting-batch-section-head">
          <div class="setting-batch-section-icon"><AppIcon name="clock" /></div>
          <div>
            <h4>フェーズ分析のトリガー</h4>
            <p>
              録音中に、いつノートを更新するかを決めます（間隔・文字量・キーワード）。どれか 1 つでも
              当てはまれば更新し、直後の連発はクールダウンで抑えます。
            </p>
          </div>
        </header>

        <div class="setting-method-tabs setting-method-tabs-wtb">
          <div class="setting-tabs-buttons">
            <button type="button" class="active" data-method-tab="基本設定">基本設定</button>
          </div>
          <span class="setting-io-badge">
            <AppIcon name="list" size="sm" /> 書き起こしの追記
            <AppIcon name="chevron-right" size="sm" /> ノート更新の合図
          </span>
        </div>

        <section class="setting-method-tab-panel" data-method-panel="基本設定">
          <div class="setting-field-grid">
            <div class="setting-field full">
              <label class="setting-label" for="cr-set-note-enabled">
                <span>AI 解析（フェーズノート・最終まとめ）</span>
                <small class="setting-field-key">classroomAiNoteEnabled</small>
              </label>
              <select
                id="cr-set-note-enabled" v-model="values.classroomAiNoteEnabled"
                class="setting-control setting-dropdown" data-cr-set-note-enabled
              >
                <option value="true">使う（batC61 / batC62 を実行する）</option>
                <option value="false">使わない（書き起こしだけ。AI は呼ばない）</option>
              </select>
              <p class="setting-help">
                「使わない」にすると、録音中も終了時もノート（batC61 / batC62）を作りません。
                STT（書き起こし）の精度だけを確かめたいときに使います（既定: 使う）。
              </p>
            </div>

            <!-- 数字はスライダー（利用者の指示 2026-09-18） -->
            <div
              v-for="field in TRIGGER_RANGE_FIELDS" :key="field.key"
              class="setting-field setting-range-field"
            >
              <label class="setting-label" :for="field.id">
                <span>{{ field.label }}</span><small class="setting-field-key">{{ field.key }}</small>
              </label>
              <div class="setting-range-row">
                <input
                  :id="field.id" v-model="values[field.key]" class="setting-range" type="range"
                  :min="field.min" :max="field.max" :step="field.step" :data-suffix="field.suffix"
                  :style="{ '--setting-range-progress': rangeProgress(field) }" v-bind="hookOf(field)"
                >
                <output
                  :id="`${field.id}_value`" class="setting-range-value" :for="field.id"
                  :data-suffix="field.suffix"
                >{{ rangeText(field) }}</output>
              </div>
              <p v-for="(line, index) in field.helpLines" :key="index" class="setting-help">{{ line }}</p>
            </div>

            <div class="setting-field full">
              <!-- 見出しは追加欄の input と結び付ける（span のままだと、追加欄に id も name も無いため
                   Chrome の Issues「A form field element should have an id or name attribute」になる）。 -->
              <label class="setting-label" for="cr-set-keyword-input">
                <span>トリガーキーワード</span>
                <small class="setting-field-key">classroomAiTriggerKeywords</small>
              </label>
              <div class="cr-set__chips" data-cr-set-keywords>
                <span
                  v-for="(keyword, index) in triggerKeywords" :key="keyword" class="cr-chip"
                  data-cr-set-keyword-item
                >
                  {{ keyword }}
                  <button
                    type="button" class="cr-chip__remove" :aria-label="`${keyword} を削除`"
                    :data-cr-set-keyword-remove="index" @click="removeKeyword(index)"
                  >
                    ×
                  </button>
                </span>
                <span v-if="triggerKeywords.length === 0" class="cr-hint">キーワードはまだありません。</span>
              </div>
              <div class="cr-set__add">
                <input
                  id="cr-set-keyword-input" v-model="keywordInput" name="classroomAiTriggerKeywords"
                  class="input" type="text" placeholder="キーワードを入力" data-cr-set-keyword-input
                >
                <button type="button" class="btn btn--secondary btn--sm" data-cr-set-keyword-add @click="addKeyword">
                  <AppIcon name="plus" size="sm" /> 追加
                </button>
              </div>
              <p class="setting-help">
                このキーワードが書き起こしに出たら、間隔を待たずにノートを更新します（カンマ区切りで保存します）。
              </p>
            </div>
          </div>
        </section>
      </section>

      <!-- ===================== 前置詞プリセット管理 ===================== -->
      <section class="setting-batch-section" data-setting-subsection="classroom-presets">
        <header class="setting-batch-section-head">
          <div class="setting-batch-section-icon"><AppIcon name="bookmark" /></div>
          <div>
            <h4>前置詞プリセット管理</h4>
            <p>
              新しい授業で選ぶ「前置詞」（AI に整理の方針を伝える文）の一覧です。ここでの追加・変更・削除は
              <strong>そのまま保存されます</strong>（新しい授業・録音中の画面が読みます）。
            </p>
          </div>
        </header>
        <div class="setting-field-grid">
          <div class="setting-field full">
            <p v-if="presetsLoading" class="setting-help" data-cr-set-presets-loading>
              前置詞プリセットを読み込んでいます...
            </p>
            <p v-if="presetsError !== ''" class="alert alert--danger" data-cr-set-presets-error>
              {{ presetsError }}
            </p>
            <ul class="cr-set__presets" data-cr-set-presets>
              <li
                v-for="item in presets" :key="item.presetId" class="cr-preset-row"
                :data-cr-set-preset-item="item.presetId"
              >
                <div class="cr-preset-row__body">
                  <span class="cr-preset-row__name">
                    <span class="badge badge--outline" data-cr-set-preset-order-label>{{ item.displayOrder }}</span>
                    {{ item.name }}
                  </span>
                  <span class="cr-preset-row__desc">{{ item.text }}</span>
                </div>
                <div class="cr-preset-row__actions">
                  <button
                    type="button" class="btn btn--icon btn--sm" :aria-label="`${item.name} を編集`"
                    :data-cr-set-preset-edit="item.presetId" :disabled="presetSaving" @click="openEditPreset(item)"
                  >
                    <AppIcon name="edit" size="sm" />
                  </button>
                  <button
                    type="button" class="btn btn--icon btn--sm is-danger" :aria-label="`${item.name} を削除`"
                    :data-cr-set-preset-delete="item.presetId" :disabled="presetSaving" @click="deletePreset(item)"
                  >
                    <AppIcon name="trash" size="sm" />
                  </button>
                </div>
              </li>
              <li v-if="!presetsLoading && presets.length === 0" class="cr-hint">プリセットはまだありません。</li>
            </ul>

            <button
              type="button" class="btn btn--secondary btn--sm" :disabled="presetSaving"
              data-cr-set-preset-add @click="openAddPreset"
            >
              <AppIcon name="plus" size="sm" /> プリセットを追加
            </button>

            <div v-if="presetFormOpen" class="cr-form__field" data-cr-set-preset-form>
              <label class="setting-label" for="cr-set-preset-name">プリセット名</label>
              <input
                id="cr-set-preset-name" v-model="presetNameInput" class="setting-control" type="text"
                placeholder="例: 数学の演習" data-cr-set-preset-name
              >
              <label class="setting-label" for="cr-set-preset-text">前置詞のテキスト</label>
              <textarea
                id="cr-set-preset-text" v-model="presetTextInput" class="setting-control" rows="3"
                placeholder="このプリセットで AI が重点的に整理する内容（例: 公式や解法の手順・例題を重点的に整理します。）"
                data-cr-set-preset-desc
              ></textarea>
              <label class="setting-label" for="cr-set-preset-order">表示順</label>
              <input
                id="cr-set-preset-order" v-model="presetOrderInput" class="setting-control" type="number"
                min="0" data-cr-set-preset-order
              >
              <p v-if="presetError !== ''" class="alert alert--danger" data-cr-set-preset-error>{{ presetError }}</p>
              <div class="search-panel__actions">
                <button
                  type="button" class="btn btn--primary btn--sm" :disabled="presetSaving"
                  data-cr-set-preset-save @click="savePreset"
                >
                  <AppIcon name="check" size="sm" /> {{ presetSaving ? '保存中...' : '保存' }}
                </button>
                <button
                  type="button" class="btn btn--secondary btn--sm" :disabled="presetSaving"
                  data-cr-set-preset-cancel @click="presetFormOpen = false"
                >
                  <AppIcon name="x" size="sm" /> キャンセル
                </button>
              </div>
            </div>
            <p class="setting-help">
              表示順の小さい順に、新しい授業の「前置詞」の選択肢に出ます。
            </p>
          </div>
        </div>
      </section>

      <!-- 削除の確認（背景クリックでは閉じない。× と「キャンセル」で閉じる） -->
      <div v-if="presetDeleteTarget !== null" class="overlay">
        <section
          class="dialog" role="dialog" aria-modal="true" aria-labelledby="crPresetDeleteTitle"
          data-cr-set-preset-delete-dialog
        >
          <header class="dialog__head">
            <h2 id="crPresetDeleteTitle" class="dialog__title">
              <AppIcon name="trash" size="sm" /> 前置詞プリセットの削除
            </h2>
            <button
              type="button" class="dialog__close" aria-label="閉じる"
              data-cr-set-preset-delete-close @click="presetDeleteTarget = null"
            >
              <AppIcon name="x" size="sm" />
            </button>
          </header>
          <div class="dialog__body">
            <p data-cr-set-preset-delete-message>
              前置詞プリセット「{{ presetDeleteTarget.name }}」を削除します。よろしいですか？
            </p>
            <p class="setting-help" data-cr-set-preset-delete-note>
              削除すると元に戻せません（新しい授業の「前置詞」の選択肢から消えます）。
              過去の授業記録はそのまま残り、その授業の「前置詞」だけが空になります。
            </p>
          </div>
          <footer class="dialog__foot">
            <button
              type="button" class="btn btn--danger" :disabled="presetSaving"
              data-cr-set-preset-delete-confirm @click="confirmDeletePreset"
            >
              <AppIcon name="trash" size="sm" /> {{ presetSaving ? '削除中...' : '削除する' }}
            </button>
            <button
              type="button" class="btn btn--secondary" :disabled="presetSaving"
              data-cr-set-preset-delete-dismiss @click="presetDeleteTarget = null"
            >
              キャンセル
            </button>
          </footer>
        </section>
      </div>

      <div class="cr-set__foot">
        <button
          type="button" class="btn btn--primary" :disabled="saving || loading" data-cr-set-save @click="save"
        >
          <AppIcon name="check" size="sm" />
          {{ saving ? '保存中...' : 'この設定を保存' }}
        </button>
        <span class="cr-hint" data-cr-set-save-note>
          <template v-if="status !== ''">{{ status }}</template>
          <template v-else>
            このセクションの設定を保存します（上部の【設定を保存】でも保存されます）。
          </template>
        </span>
      </div>
    </section>
  </Teleport>
</template>
