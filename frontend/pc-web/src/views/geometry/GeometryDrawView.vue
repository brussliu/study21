<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, formatIsoDateTime, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  FIGURE_TYPE_LABELS,
  FIGURE_TYPE_OPTIONS,
  createGeometryFigure,
  fetchGeometryFigure,
  fetchGeometryTags,
  thumbnailDataUrl,
  updateGeometryFigure,
  type GeometryFigure,
  type GeometryFigureType,
  type GeometryTag
} from '@/api/geometry'
import {
  confirmGeometryAiRequest,
  createGeometryAiAssist,
  fetchGeometryAiAssist,
  discardGeometryAiAssist,
  fetchGeometryAiOptions,
  fetchGeometryAiRequest,
  loadGeometryAiAssistHistory,
  markGeometryAiAssistApplied,
  runGeometryAiAssist,
  type GeometryAiAssistHistoryItem,
  type GeometryAiAssistMode,
  type GeometryAiRequestDetail
} from '@/api/geometry-ai'
import '@/features/geometry/geometry.css'
import '@/features/geometry/geometry-ai.css'
import {
  exportConstructionCommands,
  type CommandSkip,
  type CommandSkipReason
} from '@/features/geometry/construction-commands'

/**
 * 図形管理【作図画面】（メニュー「数学勉強」＞「図形管理」＞ 新規作成 / 編集 / コピーして開く）。
 *
 * 2.0 の `geometry_draw.jsp` を作り直した画面。GeoGebra のアプレットを読み込み、
 * 作図した結果（GeoGebraXML）とサムネイル（Base64 PNG）を `GEO_図形情報` に保存する。
 *
 * ・アプレットは 2.0 と同じく公式の `deployggb.js` を使う（作図タイプで geometry / graphing を切り替え）。
 *   GeoGebra 自身のメニュー（作図エリア右上の歯車＝座標軸・グリッドの表示切替）も出す
 * ・`?geometryId=` 付きで開くと既存の図形を読み込んで編集する（保存は更新。無ければ新規作成）
 * ・`?copyFrom=` 付きで開くと既存の図形を読み込んで「コピーして開く」
 *   （すべての情報を画面に出し、保存すると新しい図形になる。`editingId` は持たない）
 * ・図形名・メモ・タグ・コマンド入力（GeoGebra コマンドを貼り付けて実行）
 * ・「GeoGebra コマンド」欄は**いまの作図そのもの**を出す（手動で描いても、AI が反映しても、
 *   取り消しても自動で追う。コマンドの実行結果ではなく**実際の作図**から作る）。
 *   利用者が編集中の内容は自動同期で上書きしない（【作図の内容に戻す】で戻せる）
 * ・保存／全消去／中央表示／一覧へ戻る（サムネイルは作図が変わると自動で取り直す）
 * ・**作図できるかは属性で知らせる**（`[data-gm-draw-frame]` の `data-gm-draw-ready`）。
 *   「作図できます／準備中...」の文言は出さず、読み込めなかったときだけ理由を出す
 * ・読み込みに失敗したら**1 秒待って 1 回だけやり直す**（再読み込みのボタンは置かない）
 * ・左の入力欄は折りたためる（作図エリアを広く使う）
 * ・`?geometryAiRequestId=` 付きで開くと **AI 生図の結果**を読み込み、コマンドを作図に流し込む
 *   （AI は XML を作らないので `setXML` ではなく `evalCommand` を使う。保存は AI 生図の確定になる）
 * ・AI 画図助手は**非モーダルの浮動ウィンドウ**（開いたまま作図できる。位置と大きさは変えられる）。
 *   指示を送ると AI の案が会話ログに積まれ、**そのまま作図に反映される**（押させない）。
 *   反映方法は**追加だけ**（作り直しの選択は置かない。作り直したいときは利用者が【全消去】する）
 *   反映したステップは【戻す】でその時点の作図に戻せる（失敗したステップには出さない）
 * ・窓の位置と大きさ、会話ログは `localStorage` に覚える（図形ごと・直近 20 歩まで）
 * ・作図エリアの高さはビューポートの残りいっぱい（リサイズ・折りたたみで計算し直す）
 */
const route = useRoute()
const router = useRouter()
const toast = useToast()

/** 画面のエリア（/student, /parent, /admin）。 */
const area = computed(() => route.path.split('/')[1] ?? 'student')

/** GeoGebra を載せる要素の id（2.0 と同じ）。 */
const MOUNT_ID = 'geometryGgbElement'
/** GeoGebra の公式スクリプト（2.0 と同じ）。 */
const GEOGEBRA_SCRIPT_URL = 'https://www.geogebra.org/apps/deployggb.js'
const GEOGEBRA_LOAD_ERROR = 'GeoGebra を読み込めませんでした。インターネット接続を確認して、ページを再読み込みしてください。'
/** タグの上限（backend と同じ）。 */
const TAG_MAX_LENGTH = 60
const TAG_MAX_COUNT = 20
/**
 * タグ候補の既定の表示**行数**（利用者の指示: 既定は 2 行だけ出し、残りは【もっと見る】で開く）。
 *
 * 何枚で 2 行になるかは幅で変わるので、件数ではなく**描画後の行の位置**を測って決める。
 */
const TAG_SUGGESTION_ROWS = 2
/** 図形名が空のときの名前（2.0 と同じ）。 */
const UNTITLED = '名称未設定'
const TITLE_MAX = 120
const MEMO_MAX = 500
/**
 * 作図エリアの下に残す余白（px。画面いっぱいに広げすぎないため）。
 *
 * 枠の下には案内文があり、ページ（`.page`）自身の下余白も残るため、
 * 作図画面では `.page` の下余白を 0 にして（`geometry.css`）、この余白ぶんだけ空ける。
 */
const STAGE_BOTTOM_MARGIN = 48
/** 作図エリアの高さの下限（px）。 */
const STAGE_MIN_HEIGHT = 420

/** 助手に渡せる指示の上限（サーバーの検証と同じ値）。 */
const ASSIST_INSTRUCTION_MAX = 500
/**
 * AI の説明を**既定で 2 行まで**にする長さの目安（文字数）。
 * これより長いときだけ「詳細」を出して、押したら全部見せる（利用者の指示: 説明は畳む）。
 */
const ASSIST_DESCRIPTION_CLAMP = 60
/** 助手の窓の最小の大きさ（px。これより小さくはしない）。 */
const ASSIST_MIN_WIDTH = 320
const ASSIST_MIN_HEIGHT = 320
/** 助手の窓の既定の大きさ（px。CSS と合わせる）。 */
const ASSIST_DEFAULT_WIDTH = 380
const ASSIST_DEFAULT_HEIGHT = 560
/** 画面の端から残す余白（px。窓が画面の外へ出ないようにする）。 */
const ASSIST_VIEWPORT_MARGIN = 12
/**
 * 窓の位置と大きさを覚えておくキー（この端末のこのブラウザだけ）。
 *
 * `v2` にした理由: 既定の位置を「右上」から「左の入力欄に重ねる縦長」へ変えたので、
 * 古い記憶（右上）を引きずらず、新しい既定で開くようにする。
 */
const ASSIST_WINDOW_KEY = 'study21.geometry.aiAssist.window.v2'
/**
 * 覚えておく会話ログの上限（ステップ数。**古いものから捨てる**）。
 * localStorage は容量が限られるので、際限なく増やさない。
 */
const ASSIST_LOG_LIMIT = 20
/**
 * 1 ステップの作図（XML）を**この端末に**保存する上限（文字数）。
 * これを超える作図は端末には覚えない（文字だけ残す）。戻り先はサーバーの `指示前XML` を使う。
 */
const ASSIST_SNAPSHOT_MAX = 200_000
/**
 * サーバー（DB の `指示前XML`）が受け取る上限（文字数）。
 *
 * バックエンドの `BEFORE_XML_LIMIT` と同じ値。これを超える作図はサーバー側で切られるので、
 * **【戻す】の戻り先には使えない**（切れた XML を読み込むと作図が壊れるため）。
 */
const ASSIST_SERVER_SNAPSHOT_MAX = 2_000_000
/**
 * 助手の結果を問い合わせる間隔（ミリ秒）。
 * AI は 60〜180 秒かかるので、画面は**ポーリング**で待つ（同期 1 往復にしない）。
 */
const ASSIST_POLL_INTERVAL_MS = 1500
/** これを過ぎたら「時間がかかっています」と伝えて、送り直せるようにする（ミリ秒）。 */
const ASSIST_POLL_TIMEOUT_MS = 180_000
/**
 * 反映の前にアプレットの準備を待つ上限（ミリ秒）。
 * 反映は自動なので、押せない代わりにここで少し待つ（準備前に結果が返ることがある）。
 */
const ASSIST_APPLET_WAIT_MS = 15_000
/** アプレットの準備を待つときの確認間隔（ミリ秒）。 */
const APPLET_WAIT_INTERVAL_MS = 250
/**
 * 座標軸・グリッドの表示を覚えておくキー（この端末のこのブラウザだけ）。
 * 作図タイプの切替や読み込みでアプレットを作り直したときに、選んだ状態へ戻す。
 */
const AXES_GRID_KEY = 'study21.geometry.axesGrid'
/**
 * サムネイルを自動で取り直すまでの待ち時間（ミリ秒）。
 *
 * GeoGebra は作図を動かすたびに更新イベントを出すので、そのまま取り直すと
 * ドラッグ中に何十回も画像を作ることになる。**少し待ってから 1 回だけ**取り直す。
 */
const THUMBNAIL_DEBOUNCE_MS = 900

/** GeoGebra の API（使うものだけ）。 */
interface GeoGebraApi {
  getXML?: () => string
  setXML?: (xml: string) => void
  getPNGBase64?: (scale: number, transparent: boolean) => string | null
  evalCommand?: (command: string) => boolean
  newConstruction?: () => void
  deleteObject?: (name: string) => void
  getAllObjectNames?: () => string[]
  /** その名前のオブジェクトが作図にあるか（AI の案の参照チェックに使う）。 */
  exists?: (name: string) => boolean
  /** オブジェクトの種類（`point` / `conic` / `polygon` など）。 */
  getObjectType?: (name: string) => string
  /** 定義（`Circle((0,0),3)` など。AI に渡すと中心・半径が分かる）。 */
  getDefinitionString?: (name: string) => string
  /** 定義が取れないときの値（`x^2 + y^2 = 9` など）。 */
  getValueString?: (name: string) => string
  /** そのオブジェクトを作ったコマンド（`Circle(A, B)` など。自由な点は座標、無ければ空）。 */
  getCommandString?: (name: string) => string
  /** コマンドと入力・出力の XML（コマンド欄の書き出しに使う）。 */
  getAlgorithmXML?: (name: string) => string
  /** 同じコマンドで作られた仲間（交点の 2 点など）。 */
  getSiblingObjectNames?: (name: string) => string[]
  /** ほかの作図に依存していないか（ドラッグした自由点の座標を読むときに見る）。 */
  isIndependent?: (name: string) => boolean
  /** 点の x 座標・y 座標（コマンド文字列が空の自由点に使う）。 */
  getXcoord?: (name: string) => number
  getYcoord?: (name: string) => number
  CenterView?: (x: number, y: number) => void
  /** アプレットの大きさを変える（作図エリアの高さに追随させる）。 */
  setSize?: (width: number, height: number) => void
  /** 表示を作り直す（大きさを変えたあと、古い大きさのまま残った描画を描き直させる）。 */
  refreshViews?: () => void
  /** アプレットの幅だけを変える（`setSize` が効かないときの保険）。 */
  setWidth?: (width: number) => void
  /** アプレットの高さだけを変える（同上）。 */
  setHeight?: (height: number) => void
  /** 再描画の一時停止／再開（止めて再開すると全体を描き直す）。 */
  setRepaintingActive?: (active: boolean) => void
  /** 作図に変更があったときに呼ばれる（サムネイルの自動更新に使う）。 */
  registerUpdateListener?: (listener: () => void) => void
  /** アプレットの操作イベント（ドラッグ中なども来る。同じく自動更新のきっかけ）。 */
  registerClientListener?: (listener: (event: { type?: string }) => void) => void
  /** 座標軸（x と y をまとめて）の表示切替。 */
  setAxesVisible?: (xAxis: boolean, yAxis: boolean) => void
  /** グリッドの表示切替。 */
  setGridVisible?: (visible: boolean) => void
  /** いま表示しているか（`'xAxis'` `'yAxis'` `'grid'` など）。 */
  getVisible?: (objectName: string) => boolean
}

interface GeoGebraApplet {
  inject: (elementId: string) => void
}

type GeoGebraConstructor = new (params: Record<string, unknown>, html5: boolean) => GeoGebraApplet

declare global {
  interface Window {
    GGBApplet?: GeoGebraConstructor
    ggbApplet?: GeoGebraApi
  }
}

/**
 * deployggb.js は 1 回だけ読み込む（画面を開き直しても増やさない）。
 * 既に `window.GGBApplet` があるときは読み込み済みとして使い回す。
 */
let scriptPromise: Promise<GeoGebraConstructor> | null = null

function ensureGeoGebra(): Promise<GeoGebraConstructor> {
  const loaded = window.GGBApplet
  if (typeof loaded === 'function') return Promise.resolve(loaded)
  if (scriptPromise !== null) return scriptPromise

  scriptPromise = new Promise<GeoGebraConstructor>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = GEOGEBRA_SCRIPT_URL
    script.async = true
    script.dataset.geogebra = 'true'
    script.addEventListener('load', () => {
      const constructor = window.GGBApplet
      if (typeof constructor === 'function') {
        resolve(constructor)
      } else {
        scriptPromise = null
        reject(new Error(GEOGEBRA_LOAD_ERROR))
      }
    })
    script.addEventListener('error', () => {
      scriptPromise = null
      reject(new Error(GEOGEBRA_LOAD_ERROR))
    })
    document.head.appendChild(script)
  })
  return scriptPromise
}

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

/* ---------- 画面の状態 ---------- */

/** 保存先の図形（null なら新規作成。コピーして開いたときも null＝保存すると新規作成）。 */
const editingId = ref<number | null>(null)
/** 利用者に見せる図形番号（編集中のみ）。 */
const figureNo = ref('')
/** コピー元の図形番号（`?copyFrom=` で開いたときのみ）。 */
const copySourceNo = ref('')
/** 楽観的ロック用のバージョン。 */
const version = ref<number | null>(null)
const updatedAt = ref<string | null>(null)
/** 読み込んだ図形が持っていたサムネイル（取得できなかったときの代わりに使う）。 */
const loadedThumbnail = ref<string | null>(null)

const form = reactive({
  title: '',
  figureType: 'geometry' as GeometryFigureType,
  memo: ''
})
const tags = ref<string[]>([])
const tagInput = ref('')
const tagSuggestions = ref<GeometryTag[]>([])
/** タグ候補を全部出しているか（既定は上位 10 件だけ）。 */
const tagExpanded = ref(false)

/* ---------- GeoGebra コマンド欄（いまの作図を表す） ---------- */

/** コマンド欄の内容（未編集のときは**いまの作図**がそのまま入る）。 */
const commands = ref('')
/** 利用者がコマンド欄を編集したか（**自動同期で上書きしない**）。 */
const commandsEdited = ref(false)
/** いまの作図から作ったコマンド（編集中でも「現在の作図」として常に見せる）。 */
const canvasCommands = ref<string[]>([])
/** コマンドにできなかった作図（スライダなど。**黙って落とさず**画面に出す）。 */
const canvasCommandSkips = ref<CommandSkip[]>([])
/**
 * コマンドの自動同期をまとめる時間（ミリ秒）。
 *
 * ドラッグ中は更新通知が何度も来るので、少し待ってから 1 回だけ読む
 * （サムネイルの自動更新＝900ms より短く、作図を止めたらすぐ追いつく）。
 */
const COMMAND_SYNC_DEBOUNCE_MS = 300

/** コマンドにできなかった理由の文言（画面に出す）。 */
const COMMAND_SKIP_LABELS: Record<CommandSkipReason, string> = {
  control: 'スライダ・チェックボックス',
  'no-command': 'コマンドを持たない作図',
  'multi-output': 'まとめて作る作図（名前を戻せない）',
  dependency: '再現できない作図に依存'
}

/**
 * コマンドにできなかった作図の案内。
 *
 * **完全に同期できたように見せない**ための表示（利用者の指示: 黙って落とさない）。
 */
const commandLimitNote = computed(() => {
  const skips = canvasCommandSkips.value
  if (skips.length === 0) return ''
  const counted = new Map<string, number>()
  for (const item of skips) {
    const label = COMMAND_SKIP_LABELS[item.reason]
    counted.set(label, (counted.get(label) ?? 0) + 1)
  }
  const detail = [...counted].map(([label, count]) => `${label} ${count} 件`).join('・')
  const names = skips.slice(0, 3).map((item) => item.name).join('、')
  const rest = skips.length > 3 ? ' ほか' : ''
  return `コマンドにできない作図が ${skips.length} 件あります（${detail}：${names}${rest}）。`
    + 'コマンド欄はその分を含みません。'
})

const loading = ref(true)
const saving = ref(false)
const loadError = ref('')
/** GeoGebra の読み込みに失敗したときの案内。 */
const stageError = ref('')
const appletReady = ref(false)
/** サムネイルのプレビュー（data URL）。 */
const preview = ref('')
const frame = ref<HTMLElement | null>(null)
/** 左の入力欄を隠しているか（画面を開いている間だけ保持する）。 */
const collapsed = ref(false)
/**
 * 座標軸・グリッドを表示しているか（上部の操作行の checkbox）。
 *
 * アプレットの準備ができたら**実際の状態を読んで**入れ、作り直したときは
 * 利用者が最後に選んだ状態（`localStorage`）へ戻す。
 */
const axesVisible = ref(true)
const gridVisible = ref(false)
/** AI 画図助手の窓を開いているか（**開いたまま作図できる**。背景クリックでは閉じない）。 */
const assistDialogOpen = ref(false)
/** AI 画図助手への指示（例: 三角形の垂線を引いて）。 */
const aiAssistInstruction = ref('')

/**
 * 変更案の反映方法（**追加だけ**。利用者の指示）。
 *
 * 作り直し（作図全体を消して置き換える）は**行わない**。作り直したいときは利用者が自分で
 * 【全消去】してから指示する。そのため画面に選択は置かず、依頼にも実行にもこの値だけを使う
 * （古い端末の記憶に「作り直す」が残っていても、実行には使わない）。
 */
const ASSIST_APPLY_MODE = 'append' as const
/** 作図エリアの高さ（ビューポートの残り。測る前は null＝CSS の min-height に任せる）。 */
const frameHeight = ref<number | null>(null)

/* ---------- AI 生図（?geometryAiRequestId=） ---------- */

/** AI 生図の要求 ID（`?geometryAiRequestId=`。無ければ null）。 */
const aiRequestId = ref<number | null>(null)
/** 読み込んだ AI 生図の要求（状態・コマンド・提案）。 */
const aiRequest = ref<GeometryAiRequestDetail | null>(null)
/** AI 生図の読み込み・確定の案内（日本語）。 */
const aiNotice = ref('')
/**
 * AI 生図のコマンドを**この端末で実行できなかった**理由（実行できていれば null）。
 *
 * <p>サーバーは GeoGebra を持っていないので、作図が本当に成り立つかはここ（ブラウザ）で
 * `evalCommand` を走らせて初めて分かる。失敗したら作図は元に戻してあり、**AI の結果は
 * 手元に反映されていない**。そのまま【保存】すると「生成できた」ことになってしまうので、
 * 保存を止めて理由を見せる（{@link save}）。</p>
 */
const aiCommandFailure = ref<string | null>(null)
/** AI 生図から開いたことを示す案内（画面の上部に出る）。 */
const aiSourceLabel = computed(() => {
  const current = aiRequest.value
  if (current === null) return ''
  return `AI 生図 ${current.requestNo}（${current.statusLabel}）から開いています。`
})

/* ---------- AI 画図助手 ---------- */

/** 助手が使えるか（設定で無効なら入力欄を出さない）。 */
const assistAvailable = ref(false)
/** いま AI が考えている最中か（実行中のステップがあるか）。 */
const assistSending = computed(() => assistLog.value.some((step) => step.status === 'running'))
/** 助手の案内（失敗理由・無効の説明・記録の失敗）。 */
const assistNotice = ref('')

/**
 * 助手の 1 ステップ（利用者の指示 ↔ AI の応答）。
 *
 * `snapshot` は**その指示を出す前の作図（GeoGebra XML）**（【戻す】でこの XML に戻す。
 * 反映は自動なので、押せば**その指示と、そのあとの変更が作図から消える**＝取り消しになる）。
 * 大きすぎる XML はこの端末には保存しない（`snapshot` は null。その場合はサーバーの
 * `指示前XML` を `beforeXmlAvailable` 経由で取りに行く）。
 */
interface AssistStep {
  id: number
  instruction: string
  /**
   * 依頼したときの反映方法（**表示と互換のためだけ**に持つ。実行には使わない）。
   *
   * いまは常に `append`。古い履歴には `replace`（作図全体を作り直す）が残っていることがあるが、
   * **実行はいつも追加**なので、読み直しても画布を消さない。
   */
  mode: GeometryAiAssistMode
  commands: string[]
  description: string | null
  /** DB（GEO_AI画図指示情報）の 画図指示ID */
  assistId: number | null
  status: 'running' | 'pending' | 'applied' | 'discarded' | 'failed'
  error: string | null
  snapshot: string | null
  /** 【戻す】で後ろのステップを畳んだか（戻した時点より後の変更は作図から消える） */
  superseded: boolean
  /**
   * 作図が大きすぎて**この端末には**戻り先を保存していないか。
   * 文字（指示・コマンド）だけは残す。
   */
  snapshotOmitted: boolean
  /**
   * サーバー（DB の `指示前XML`）に戻り先があるか。
   * 別の端末で出した指示でも【戻す】できるように、押したときに 1 件だけ取りに行く。
   */
  beforeXmlAvailable: boolean
  /** 「詳細」で AI の説明を開いているか（画面を開いている間だけ） */
  expanded: boolean
  /**
   * 実行できなかったあとに**自動で修正を頼んだか**（1 指示につき 1 回だけ）。
   * 同じ失敗で無限に AI を呼ばないための印。
   */
  repairTried: boolean
  /** 修正版のステップなら、元になったステップの id（画面に「AI が修正」と出す）。 */
  repairedFrom: number | null
}

/** 会話ログ（利用者の指示 ↔ AI の応答。**セッション中と localStorage に保持する**）。 */
const assistLog = ref<AssistStep[]>([])
/** ステップの連番（`data-gm-ai-assist-restore` に出す）。 */
let assistStepSeq = 0
/** 【戻す前の作図に戻す】の 1 手戻し用（戻す前の作図。セッション中だけ）。 */
const restoreBackup = ref<string | null>(null)

/**
 * 【戻す】を出すか（利用者の指示: **失敗したステップに出さない**）。
 *
 * 戻せるのは「作図を変えたステップ」だけ。失敗・破棄のステップは作図を変えていないので、
 * そこに【戻す】があると「失敗したのに戻す？」と迷わせる（何も起きないので余計に分からない）。
 * 戻り先はこの端末のスナップショット、無ければサーバーの `指示前XML`（別の端末で出した指示）。
 */
function canRestoreStep(step: AssistStep): boolean {
  return step.status === 'applied' && (step.snapshot !== null || step.beforeXmlAvailable)
}
/** 会話ログに出す**短い**状態（長い説明は書かない）。 */
function assistStepLabel(step: AssistStep): string {
  switch (step.status) {
    case 'running': return '考えています…'
    case 'pending': return '未反映'
    case 'applied': return '反映済み'
    case 'discarded': return '破棄済み'
    default: return '失敗'
  }
}
/** AI の説明が長いか（既定は 2 行まで。長いときだけ「詳細」を出す）。 */
function hasLongDescription(step: AssistStep): boolean {
  return step.description !== null && step.description.length > ASSIST_DESCRIPTION_CLAMP
}
/** 「詳細」の開閉。 */
function toggleStepDetail(step: AssistStep): void {
  step.expanded = !step.expanded
}
/**
 * そのステップのコマンドをクリップボードへコピーする。
 *
 * <p>クリップボードが使えない環境（許可されていない・http で開いている等）では、
 * 一時的な textarea で選択して `execCommand('copy')` を試す（ボタンを置いて何も起きない状態にしない）。</p>
 */
async function copyCommands(step: AssistStep): Promise<void> {
  const text = step.commands.join('\n')
  if (text === '') return
  try {
    if (navigator.clipboard?.writeText !== undefined) {
      await navigator.clipboard.writeText(text)
      toast.success('コマンドをコピーしました。')
      return
    }
    throw new Error('clipboard unavailable')
  } catch {
    try {
      const area = document.createElement('textarea')
      area.value = text
      area.setAttribute('readonly', '')
      area.style.position = 'fixed'
      area.style.opacity = '0'
      document.body.appendChild(area)
      area.select()
      const copied = document.execCommand('copy')
      document.body.removeChild(area)
      if (copied) {
        toast.success('コマンドをコピーしました。')
        return
      }
    } catch {
      // 下の案内へ
    }
    toast.danger('コピーできませんでした。コマンドを選んで手動でコピーしてください。')
  }
}

/** 指示を送れるか（助手が有効で、指示があり、送信中でない）。 */
const canSendAssist = computed(() =>
  assistAvailable.value && !assistSending.value && aiAssistInstruction.value.trim() !== ''
)

/* ---- 窓の位置と大きさ ----
 *
 * 大きさは**覚える**（右下の把手で変えた大きさは次に開いても同じ）。
 * 位置は**覚えない**（利用者の指示: どこへ動かしても、次に開くときは必ず左の入力欄＝図形名の位置に出す）。
 * 動かせること自体は残す（開いている間だけ動かせる）。
 */

/** 窓の位置（`null` はまだ決まっていない＝CSS の既定）。 */
const assistPosition = ref<{ left: number; top: number } | null>(null)
/** 窓の大きさ（`null` はまだ決まっていない＝CSS の既定）。 */
const assistSize = ref<{ width: number; height: number } | null>(null)
const assistWindow = ref<HTMLElement | null>(null)
/** 左の入力欄（助手の窓を既定で重ねる相手）。 */
const sidePanel = ref<HTMLElement | null>(null)
/** 移動 1 回分（つまんだ位置と、そのときの窓の位置）。 */
let assistDrag: { offsetX: number; offsetY: number } | null = null
/** 大きさ変更 1 回分（つまみ始めの位置と、そのときの窓の大きさ）。 */
let assistResize: { startX: number; startY: number; width: number; height: number } | null = null

/** 窓の大きさ（決まっていなければ既定）。 */
function assistWindowSize(): { width: number; height: number } {
  return assistSize.value ?? { width: ASSIST_DEFAULT_WIDTH, height: ASSIST_DEFAULT_HEIGHT }
}

/** 位置と大きさを**画面の中に収める**（最小・最大も見る）。 */
function clampAssistWindow(
  left: number, top: number, width: number, height: number
): { left: number; top: number; width: number; height: number } {
  const maxWidth = Math.max(ASSIST_MIN_WIDTH, window.innerWidth - ASSIST_VIEWPORT_MARGIN * 2)
  const maxHeight = Math.max(ASSIST_MIN_HEIGHT, window.innerHeight - ASSIST_VIEWPORT_MARGIN * 2)
  const size = {
    width: Math.min(Math.max(ASSIST_MIN_WIDTH, Math.round(width)), maxWidth),
    height: Math.min(Math.max(ASSIST_MIN_HEIGHT, Math.round(height)), maxHeight)
  }
  return {
    ...size,
    left: Math.min(Math.max(ASSIST_VIEWPORT_MARGIN, Math.round(left)),
      Math.max(ASSIST_VIEWPORT_MARGIN, window.innerWidth - size.width - ASSIST_VIEWPORT_MARGIN)),
    top: Math.min(Math.max(ASSIST_VIEWPORT_MARGIN, Math.round(top)),
      Math.max(ASSIST_VIEWPORT_MARGIN, window.innerHeight - size.height - ASSIST_VIEWPORT_MARGIN))
  }
}

/**
 * 既定の位置と大きさ（**左の入力欄に重ねる縦長**）。
 *
 * 利用者の指示: 開いたら自動でページの左へ出て、左の入力欄（図形名・タグなど）を覆い、
 * 右の作図エリアは隠さない。だから「左の入力欄の幅」と「作図エリアの高さ」に合わせる。
 * 入力欄を隠しているときは、その場所が無いので作図エリアの左端に寄せる。
 */
function defaultAssistWindow(): { left: number; top: number; width: number; height: number } {
  const side = sidePanel.value?.getBoundingClientRect() ?? null
  const stage = frame.value?.getBoundingClientRect() ?? null
  const showSide = side !== null && side.width > 0 && side.height > 0
  const width = Math.round(showSide ? side.width : ASSIST_DEFAULT_WIDTH)
  const left = Math.round(showSide ? side.left : (stage?.left ?? ASSIST_VIEWPORT_MARGIN * 2))
  const top = Math.round(showSide ? side.top : (stage?.top ?? 96))
  const height = Math.round(stage !== null && stage.height > 0 ? stage.height : ASSIST_DEFAULT_HEIGHT)
  return { left, top, width: Math.max(ASSIST_MIN_WIDTH, width), height: Math.max(ASSIST_MIN_HEIGHT, height) }
}

/** 既定の位置だけ（大きさは今のものを使う場面で使う）。 */
function defaultAssistPosition(): { left: number; top: number } {
  const layout = defaultAssistWindow()
  return { left: layout.left, top: layout.top }
}

/** 覚えている大きさ（localStorage の形。位置は覚えない）。 */
interface AssistWindowStore {
  width?: unknown
  height?: unknown
}

/**
 * 開くときの位置と大きさを決める。
 *
 * <p>**位置は必ず既定**（左の入力欄＝図形名の位置）。前にどこへ動かしていても戻す
 * （利用者の指示: 毎回同じ場所に出てほしい）。大きさだけは覚えているものを使う。</p>
 */
function loadAssistWindow(): void {
  let stored: AssistWindowStore | null = null
  try {
    const raw = window.localStorage.getItem(ASSIST_WINDOW_KEY)
    stored = raw === null ? null : JSON.parse(raw) as AssistWindowStore
  } catch {
    // 壊れた値・保存できない環境では既定の大きさで開く
    stored = null
  }
  const width = Number(stored?.width)
  const height = Number(stored?.height)
  const fallback = defaultAssistWindow()
  const size = {
    width: Number.isFinite(width) ? width : fallback.width,
    height: Number.isFinite(height) ? height : fallback.height
  }
  const clamped = clampAssistWindow(fallback.left, fallback.top, size.width, size.height)
  assistSize.value = { width: clamped.width, height: clamped.height }
  assistPosition.value = { left: clamped.left, top: clamped.top }
}

/** 大きさを覚える（位置は覚えない＝次に開くと必ず既定の位置に出る）。 */
function saveAssistWindow(): void {
  const size = assistSize.value
  if (size === null) return
  try {
    window.localStorage.setItem(ASSIST_WINDOW_KEY, JSON.stringify({ width: size.width, height: size.height }))
  } catch {
    // 保存できなくても機能は使える（次に開くと既定の大きさに戻るだけ）
  }
}

/** 窓の style（位置と大きさ）。 */
const assistWindowStyle = computed(() => {
  const style: Record<string, string> = {}
  const size = assistSize.value
  const position = assistPosition.value
  if (size !== null) {
    style.width = `${size.width}px`
    style.height = `${size.height}px`
  }
  if (position !== null) {
    style.left = `${position.left}px`
    style.top = `${position.top}px`
  }
  return Object.keys(style).length === 0 ? undefined : style
})

/** タイトルバーをつまんで動かし始める（**本文やログでは動かない**）。 */
function startAssistDrag(event: PointerEvent): void {
  // タイトルバーの中のボタン（×）は押せるままにする
  const target = event.target as HTMLElement | null
  if (target !== null && target.closest('button') !== null) return
  const size = assistWindowSize()
  const current = assistPosition.value ?? defaultAssistPosition()
  const clamped = clampAssistWindow(current.left, current.top, size.width, size.height)
  assistSize.value = { width: clamped.width, height: clamped.height }
  assistPosition.value = { left: clamped.left, top: clamped.top }
  assistDrag = { offsetX: event.clientX - clamped.left, offsetY: event.clientY - clamped.top }
  ;(event.currentTarget as HTMLElement | null)?.setPointerCapture?.(event.pointerId)
  window.addEventListener('pointermove', onAssistDragMove)
  window.addEventListener('pointerup', endAssistPointer)
  window.addEventListener('pointercancel', endAssistPointer)
}

function onAssistDragMove(event: PointerEvent): void {
  const drag = assistDrag
  const size = assistSize.value
  if (drag === null || size === null) return
  const clamped = clampAssistWindow(
    event.clientX - drag.offsetX, event.clientY - drag.offsetY, size.width, size.height)
  assistPosition.value = { left: clamped.left, top: clamped.top }
}

/** 右下の把手をつまんで大きさを変え始める（**位置は動かさない**）。 */
function startAssistResize(event: PointerEvent): void {
  event.preventDefault()
  event.stopPropagation()
  const size = assistWindowSize()
  const position = assistPosition.value ?? defaultAssistPosition()
  assistResize = {
    startX: event.clientX,
    startY: event.clientY,
    width: assistWindow.value?.offsetWidth || size.width,
    height: assistWindow.value?.offsetHeight || size.height
  }
  if (assistSize.value === null) assistSize.value = size
  assistPosition.value = position
  ;(event.currentTarget as HTMLElement | null)?.setPointerCapture?.(event.pointerId)
  window.addEventListener('pointermove', onAssistResizeMove)
  window.addEventListener('pointerup', endAssistPointer)
  window.addEventListener('pointercancel', endAssistPointer)
}

function onAssistResizeMove(event: PointerEvent): void {
  const resize = assistResize
  const position = assistPosition.value
  if (resize === null || position === null) return
  const clamped = clampAssistWindow(
    position.left, position.top,
    resize.width + (event.clientX - resize.startX),
    resize.height + (event.clientY - resize.startY))
  assistSize.value = { width: clamped.width, height: clamped.height }
  assistPosition.value = { left: clamped.left, top: clamped.top }
}

/** つまむのをやめる（位置と大きさを覚える）。 */
function endAssistPointer(): void {
  if (assistDrag === null && assistResize === null) return
  assistDrag = null
  assistResize = null
  window.removeEventListener('pointermove', onAssistDragMove)
  window.removeEventListener('pointermove', onAssistResizeMove)
  window.removeEventListener('pointerup', endAssistPointer)
  window.removeEventListener('pointercancel', endAssistPointer)
  saveAssistWindow()
}

/** 画面の大きさが変わったら、はみ出さない位置と大きさへ寄せ直す。 */
function onAssistWindowResize(): void {
  if (!assistDialogOpen.value) return
  const size = assistWindowSize()
  const position = assistPosition.value ?? defaultAssistPosition()
  const clamped = clampAssistWindow(position.left, position.top, size.width, size.height)
  assistSize.value = { width: clamped.width, height: clamped.height }
  assistPosition.value = { left: clamped.left, top: clamped.top }
}

/* ---- 会話ログの保存（同じ図形で開き直しても続きから使える） ---- */

/**
 * 図形ごとの保存キー。
 *
 * 編集中は図形 ID、新規作成は `draft`、コピーして開いたときはコピー元の ID
 * （**別の図形の履歴を読まない**ため）。
 */
/**
 * 会話ログを覚えておくキー（**図形ごと**）。覚えないときは null。
 *
 * 利用者の指示: 履歴は図形単位。だから
 * ・編集している図形（`editingId` がある）… その図形のキーで覚える（開き直すと出る）
 * ・新規作成・コピーして開いた途中 … **覚えない**（閉じて【新規】を押すと助手は空白）
 * 保存して図形になった時点で、その図形のキーへ引き継ぐ（`save()` を参照）。
 */
function assistLogKey(): string | null {
  return editingId.value === null ? null : `study21.geometry.aiAssist.log.figure-${editingId.value}`
}

/** 保存する 1 ステップ（**大きすぎる作図は保存しない**）。 */
function toStorableStep(step: AssistStep): Record<string, unknown> {
  const tooLarge = step.snapshot !== null && step.snapshot.length > ASSIST_SNAPSHOT_MAX
  return {
    instruction: step.instruction,
    mode: step.mode,
    commands: step.commands,
    description: step.description,
    assistId: step.assistId,
    status: step.status,
    error: step.error,
    snapshot: tooLarge ? null : step.snapshot,
    superseded: step.superseded,
    snapshotOmitted: step.snapshotOmitted || tooLarge,
    beforeXmlAvailable: step.beforeXmlAvailable,
    repairTried: step.repairTried,
    repairedFrom: step.repairedFrom
  }
}

/**
 * 保存されていた状態を読む。
 *
 * 旧版の履歴には「未反映（pending）」が残っていることがある。反映は自動（利用者の指示）なので、
 * 依頼が残っていれば**実行中として状態を取り直す**（READY なら自動で反映される）。
 * 依頼が残っていない案は、反映できなかったものとして案内する（押しても何も起きない行を作らない）。
 */
function storedStatusOf(stored: Record<string, unknown>): AssistStep['status'] {
  if (stored.status === 'running' || stored.status === 'applied'
    || stored.status === 'discarded' || stored.status === 'failed') {
    return stored.status
  }
  return typeof stored.assistId === 'number' ? 'running' : 'failed'
}

/**
 * 覚えている会話ログを読む。
 *
 * **作図は変えない**（ログを出すだけ。【戻す】を押したときだけ戻す）。
 * ただし実行中だった依頼は状態を取り直し、**案が返ればそのまま反映する**（送信と同じ扱い）。
 * 壊れた値は捨てて、画面は普通に使えるようにする。
 */
function loadAssistLog(): void {
  assistLog.value = []
  assistStepSeq = 0
  const key = assistLogKey()
  if (key === null) return
  let raw: string | null = null
  try {
    raw = window.localStorage.getItem(key)
  } catch {
    return
  }
  if (raw === null) return
  try {
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return
    const steps: AssistStep[] = []
    for (const item of parsed) {
      if (item === null || typeof item !== 'object') continue
      const stored = item as Record<string, unknown>
      const instruction = typeof stored.instruction === 'string' ? stored.instruction : ''
      if (instruction === '') continue
      const status = storedStatusOf(stored)
      steps.push({
        id: steps.length + 1,
        instruction,
        // 反映方法は**表示・互換のためだけ**に読む（実行はいつも追加）
        mode: stored.mode === 'replace' ? 'replace' : 'append',
        commands: Array.isArray(stored.commands)
          ? stored.commands.filter((line): line is string => typeof line === 'string')
          : [],
        description: typeof stored.description === 'string' ? stored.description : null,
        assistId: typeof stored.assistId === 'number' ? stored.assistId : null,
        status,
        error: typeof stored.error === 'string'
          ? stored.error
          : (status === 'failed' && stored.status !== 'failed'
              ? 'この案は作図に反映されませんでした。もう一度【送信】してください。'
              : null),
        snapshot: typeof stored.snapshot === 'string' ? stored.snapshot : null,
        superseded: stored.superseded === true,
        snapshotOmitted: stored.snapshotOmitted === true,
        // サーバーの履歴を読むと上書きされる（別の端末で出した指示でも戻せるように）
        beforeXmlAvailable: stored.beforeXmlAvailable === true,
        expanded: false,
        // 自動修正は**この画面を開いている間の 1 回**なので、読み直したら「まだ試していない」に戻す
        // （同じ指示を出し直すときにもう一度だけ直させる）
        repairTried: stored.repairTried === true,
        repairedFrom: typeof stored.repairedFrom === 'number' ? stored.repairedFrom : null
      })
    }
    // 読み込むときも上限を守る（古いものから捨てて、連番を振り直す）
    const kept = steps.length > ASSIST_LOG_LIMIT ? steps.slice(steps.length - ASSIST_LOG_LIMIT) : steps
    kept.forEach((step, index) => { step.id = index + 1 })
    assistLog.value = kept
    assistStepSeq = kept.length
    // 実行中（PENDING）のまま reload されたステップは、状態を取り直す
    // （案が返れば**送信したときと同じように自動で反映する**）
    const running = assistLog.value.find((step) => step.status === 'running' && step.assistId !== null)
    if (running !== undefined) startAssistPolling(running)
  } catch {
    assistLog.value = []
    assistStepSeq = 0
  }
}

/**
 * 会話ログを覚える（**直近の `ASSIST_LOG_LIMIT` 歩だけ**。古いものから捨てる）。
 *
 * localStorage は容量が限られるので、入らないときはスナップショット（作図）を
 * 諦めて文字だけ残す（履歴は読めるが、その時点には戻れない）。
 */
function saveAssistLog(): void {
  const key = assistLogKey()
  if (key === null) return
  const steps = assistLog.value.slice(-ASSIST_LOG_LIMIT).map(toStorableStep)
  try {
    window.localStorage.setItem(key, JSON.stringify(steps))
  } catch {
    try {
      window.localStorage.setItem(
        key,
        JSON.stringify(steps.map((step) => ({ ...step, snapshot: null, snapshotOmitted: true })))
      )
    } catch {
      // 保存できない環境では履歴なしで動かす（画面は使える）
    }
  }
}

/**
 * サーバーの履歴 1 件を画面のステップにする。
 *
 * 反映は自動（利用者の指示）なので、状態は「AI がまだ考えている／作図に入れた／
 * 入れられなかった」の 3 つに落ちる。
 * ・PENDING / GENERATING … 考えています…
 * ・FAILED … AI の呼び出し・検証に失敗（理由は errorMessage）
 * ・READY + APPLIED … 反映済み
 * ・READY + REJECTED（理由つき）… 案は返ったが作図に入れられなかった（失敗として出す）
 * ・READY + REJECTED（理由なし）… 使わなかった（破棄済み。旧版の【破棄】）
 * ・READY + SUGGESTED … 昔の「案を出しただけ」の行（反映されていない）
 */
function historyStatusOf(item: GeometryAiAssistHistoryItem): AssistStep['status'] {
  if (item.status === 'FAILED') return 'failed'
  if (item.status !== 'READY') return 'running'
  if (item.applyKind === 'APPLIED') return 'applied'
  if (item.applyKind === 'REJECTED') {
    return item.errorMessage !== null && item.errorMessage !== '' ? 'failed' : 'discarded'
  }
  return 'discarded'
}

/**
 * サーバーの履歴 1 件をステップへ（**作図データはこの端末の分だけ**引き継ぐ）。
 *
 * `local` は同じ `assistId` のローカル履歴（あれば）。【戻す】用のスナップショットと
 * 「詳細」を開いた状態だけを引き継ぎ、表示する中身（指示・コマンド・状態）はサーバーを正とする。
 */
function historyStep(item: GeometryAiAssistHistoryItem, id: number, local: AssistStep | null): AssistStep {
  const status = historyStatusOf(item)
  return {
    id,
    instruction: item.instruction ?? '',
    // サーバーの history も**表示・互換のためだけ**（古い REPLACE の行もそのまま見せる）
    mode: item.mode === 'REPLACE' ? 'replace' : 'append',
    commands: [...item.commands],
    description: item.description,
    assistId: item.assistId,
    status,
    error: status === 'failed' ? item.errorMessage : null,
    snapshot: local?.snapshot ?? null,
    superseded: local?.superseded ?? false,
    // サーバーに戻り先（指示前XML）があれば「大きすぎて戻せない」とは言わない
    snapshotOmitted: item.beforeXmlAvailable ? false : (local?.snapshotOmitted ?? false),
    beforeXmlAvailable: item.beforeXmlAvailable,
    expanded: local?.expanded ?? false,
    // 自動修正の印は端末側の情報（サーバーは「修正版かどうか」を持たない）
    repairTried: local?.repairTried ?? false,
    repairedFrom: local?.repairedFrom ?? null
  }
}

/**
 * 図形ごとの履歴を**サーバーから**読んで会話ログに混ぜる（端末をまたいで同じ履歴を見せる）。
 *
 * ・表示する中身（指示・コマンド・説明・状態）はサーバーが正
 * ・この端末の localStorage は【戻す】用の作図データ置き場として残す（同じ `assistId` で突き合わせる）
 * ・端末だけで作った行（依頼の作成に失敗した等）は後ろに残す
 * ・取れなかったときはローカルの履歴のまま（画面は普通に使える）
 */
async function loadAssistHistory(): Promise<void> {
  const figureId = editingId.value
  if (figureId === null) return
  let items: GeometryAiAssistHistoryItem[]
  try {
    const response = await loadGeometryAiAssistHistory(figureId, ASSIST_LOG_LIMIT)
    items = Array.isArray(response.data.items) ? response.data.items : []
  } catch {
    return
  }
  const localById = new Map<number, AssistStep>()
  for (const step of assistLog.value) {
    if (step.assistId !== null) localById.set(step.assistId, step)
  }
  const merged: AssistStep[] = items.map(
    (item, index) => historyStep(item, index + 1, localById.get(item.assistId) ?? null)
  )
  const serverIds = new Set(items.map((item) => item.assistId))
  // サーバーが知らない行（依頼の作成に失敗した・端末だけで作った）は捨てずに後ろへ残す
  for (const step of assistLog.value) {
    if (step.assistId === null || !serverIds.has(step.assistId)) merged.push(step)
  }
  const kept = merged.length > ASSIST_LOG_LIMIT ? merged.slice(merged.length - ASSIST_LOG_LIMIT) : merged
  kept.forEach((step, index) => { step.id = index + 1 })
  assistLog.value = kept
  assistStepSeq = kept.length
  // この端末向けに作図データ（戻り先）を覚え直す
  saveAssistLog()
  // まだ AI が考えている行があれば、状態を取り直す（案が返ればそのまま反映する）
  const running = kept.find((step) => step.status === 'running' && step.assistId !== null)
  if (running !== undefined) startAssistPolling(running)
}

/** GeoGebra の API（アプレットの準備ができるまで null）。 */
let ggbApi: GeoGebraApi | null = null

/** サムネイルの自動更新のタイマー（連続する更新をまとめる）。 */
let thumbnailTimer: number | null = null
/** コマンド欄の自動同期のタイマー（同じく連続する更新をまとめる）。 */
let commandSyncTimer: number | null = null
/** アプレットの準備前に読み込んだ作図データ（準備でき次第セットする）。 */
let pendingConstruction: string | null = null
/** アプレットの準備前に読み込んだ AI 生図のコマンド（準備でき次第 1 行ずつ実行する）。 */
let pendingAiCommands: string[] | null = null
/** 今のアプレットの作図タイプ（読み込んだ図形と違うときは作り直す）。 */
let appletType: GeometryFigureType | null = null
/** 画面を離れたか（遅れて返ってくる読み込みで、外した画面を触らないため）。 */
let appletDisposed = false
/** 読み込みに失敗したときに、やり直すまで待つ時間（ミリ秒）。 */
const APPLET_RETRY_DELAY_MS = 1000
/** アプレットの準備を待つ上限（ミリ秒）。過ぎたら失敗として 1 回だけやり直す。 */
const APPLET_LOAD_TIMEOUT_MS = 10_000

/**
 * アプレットの準備ができるまで待つ（時間内にできなければ false）。
 *
 * GeoGebra のスクリプトが読めても、作図エリアが真っ白のままになることがある
 * （社内ネットワーク等で一部の資産だけ取れない場合）。**時間で見切って**
 * やり直しの判断に使う。
 */
function waitAppletReady(timeoutMs: number): Promise<boolean> {
  if (appletReady.value) return Promise.resolve(true)
  return new Promise((resolve) => {
    const startedAt = Date.now()
    const timer = window.setInterval(() => {
      if (appletReady.value || appletDisposed) {
        window.clearInterval(timer)
        resolve(appletReady.value)
        return
      }
      if (Date.now() - startedAt >= timeoutMs) {
        window.clearInterval(timer)
        resolve(false)
      }
    }, 100)
  })
}

const stageStatus = computed(() => {
  if (stageError.value !== '') return stageError.value
  return 'GeoGebra を読み込んでいます...（初回は少し時間がかかります）'
})

/** コピー元の案内（`?copyFrom=` で開いたときのみ）。 */
const copySourceLabel = computed(() =>
  copySourceNo.value === ''
    ? ''
    : `コピー元 ${copySourceNo.value}（保存すると新しい図形になります）`
)

/** 画面のタイトル（新規作成・編集・コピーして作成で変える）。 */
const pageTitle = computed(() => {
  if (copySourceNo.value !== '') return '図形コピー作成'
  return editingId.value === null ? '図形作成' : '図形編集'
})

/** タイトルのアイコン（コピーして作成のときはコピーの絵）。 */
const pageIcon = computed(() => (copySourceNo.value !== '' ? 'copy' : 'edit'))

const stateLabel = computed(() => {
  if (editingId.value === null) {
    return copySourceLabel.value === ''
      ? '新規作成（保存すると新しい図形になります）'
      : copySourceLabel.value
  }
  const time = updatedAt.value === null ? '' : ` ・ 更新 ${formatIsoDateTime(updatedAt.value)}`
  return `編集中 ${figureNo.value}${time}`
})

/** タグ候補の入れ物（2 行ぶんの枚数を測るのに使う）。 */
const suggestionsEl = ref<HTMLElement | null>(null)
/**
 * 2 行に収まる候補の枚数（`null` = 未計測。計測するまでは全部出す）。
 * 幅が変わると測り直す。
 */
const suggestionVisibleCount = ref<number | null>(null)

/**
 * 既定で出すタグ候補（件数の多い順、既定は 2 行ぶん）。
 * 順番は API が返したとおり（件数の多い順）で、切り出すだけで並べ替えない。
 */
const visibleTagSuggestions = computed(() => {
  if (tagExpanded.value || suggestionVisibleCount.value === null) return tagSuggestions.value
  return tagSuggestions.value.slice(0, suggestionVisibleCount.value)
})

/** 「もっと見る」に出す残りの件数（0 ならボタンを出さない）。 */
const hiddenTagCount = computed(() =>
  tagSuggestions.value.length - visibleTagSuggestions.value.length
)

/** 候補を描き直してから、2 行に収まる枚数を測り直す（幅が変わったときも呼ぶ）。 */
async function measureSuggestionRows(): Promise<void> {
  // いったん全部描いて行の位置を測る（隠したままでは測れない）
  suggestionVisibleCount.value = null
  await nextTick()
  const container = suggestionsEl.value
  if (container === null) return
  const chips = Array.from(container.querySelectorAll<HTMLElement>('[data-gm-draw-tag-suggestion]'))
  if (chips.length === 0) return
  const rows: number[] = []
  for (const chip of chips) {
    if (!rows.includes(chip.offsetTop)) rows.push(chip.offsetTop)
  }
  rows.sort((a, b) => a - b)
  if (rows.length <= TAG_SUGGESTION_ROWS) {
    suggestionVisibleCount.value = null
    return
  }
  // 3 行目の先頭より上にあるものだけを出す
  const firstHiddenTop = rows[TAG_SUGGESTION_ROWS]
  suggestionVisibleCount.value = Math.max(1, chips.filter((chip) => chip.offsetTop < firstHiddenTop).length)
}

/* ---------- 作図エリアの大きさ ---------- */

/**
 * 作図エリアをビューポートの残りいっぱいにする。
 *
 * 画面のレイアウト（左メニュー・ヘッダー・ツールバー）に依存しないよう、
 * 枠の上端位置を実測して `window.innerHeight - 枠の top - 余白(48px)` を高さにする。
 * 枠の下の案内文と `.page` の下余白のぶんは、作図画面だけ `.page` の下余白を 0 に
 * して（`geometry.css`）吸収し、ページ全体が縦にスクロールしないようにする。
 * 左の入力欄の折りたたみとウィンドウのリサイズで計算し直す。
 */
function measureFrame(): void {
  const element = frame.value
  if (element === null) return
  const available = window.innerHeight - element.getBoundingClientRect().top - STAGE_BOTTOM_MARGIN
  frameHeight.value = Math.max(STAGE_MIN_HEIGHT, Math.round(available))
  syncAppletSize()
}

/** 大きさが合っているかを見るときの許容差（px。描画領域の右端・内部ビューの幅）。 */
const APPLET_SIZE_TOLERANCE = 8
/**
 * 「大きさが変わった」と見せて描き直させるために、いったん広げる幅（px）。
 *
 * 1px で足りる（実測）。広げたままにはせず、すぐ元の幅に戻す。
 */
const APPLET_SIZE_NUDGE_PX = 1
/** 効いていないときに、もう一度伝える回数と間隔。 */
const APPLET_SIZE_RETRY = 3
const APPLET_SIZE_RETRY_DELAY_MS = 250
/**
 * 再描画を**再開するまで**の待ち（ミリ秒）。
 *
 * 止めてすぐ再開すると、GeoGebra は広がった分を描き直さない（実測: 枠 1665 でも絵は
 * 1578 で止まったまま。300ms 待ってから再開すると右端 1958 まで描かれた）。
 */
const APPLET_REPAINT_RESUME_DELAY_MS = 300
/**
 * 大きさを伝えたあとに「描き直しのサイクル」を回す待ち（ミリ秒）。
 * 1 回では GWT の再レイアウトと重なって効かないので、間隔をあけて数回回す。
 */
const APPLET_REPAINT_CYCLE_DELAYS_MS = [500, 1500, 3000]

/** 直前に GeoGebra へ伝えた大きさ（同じ値を何度も送らない）。 */
let appliedAppletSize = { width: 0, height: 0 }
/** 大きさが届かないときにアプレットを作り直したか（1 つにつき 1 回だけ）。 */
let appletRebuiltForSize = false
/** 再描画の再開予約（重ならないように 1 本だけ持つ）。 */
let repaintResumeTimer: number | null = null
/** 描き直しのサイクルの予約（複数持つ）。 */
let repaintCycleTimers: number[] = []

/**
 * 枠の大きさを GeoGebra に伝える（2.0 の syncAppletSize を今の作りに合わせたもの）。
 *
 * 伝える先は 2 つある。**片方だけでは足りない**:
 * 1. GeoGebra が作ったアプレットの要素 … 作ったときの大きさのまま残るので、枠に合わせて広げる
 *    （`setSize` はここを変えない。放っておくと、左メニューを畳んだり入力欄を隠したりして
 *    枠が広がったときに右側が空白になる）
 * 2. `ggbApplet.setSize` … **内部の描画領域**（ツールバーを除いた作図エリア）を合わせる
 */
function syncAppletSize(force = false): void {
  const element = frame.value
  if (element === null) return
  const width = Math.max(320, Math.ceil(element.clientWidth || 800))
  // 高さは**枠の中身の高さ**（clientHeight）を使う。枠には 1px の枠線があるので、
  // 外側の高さ（frameHeight）を渡すと GeoGebra は中身を縮めて描き、右側が白いまま残る
  // （実測: 1036 を渡すと絵は 1578 で止まり、1034 なら 1958 まで描かれた）。
  const height = Math.max(320,
    Math.round(element.clientHeight || frameHeight.value || STAGE_MIN_HEIGHT))

  // 1. **GeoGebra の入れ物（注入先）そのもの**を枠に合わせる。
  //    GeoGebra は作ったときの大きさを注入先の style に書き込むので、放っておくと
  //    ここが作ったときの幅のまま残り、**中身が右端まで描かれない**（＝右側が白いまま。
  //    実測: 枠 1665 でも入れ物が 1289 のままだと、絵は 1583 で止まっていた）。
  const mount = document.getElementById(MOUNT_ID)
  if (mount instanceof HTMLElement) {
    mount.style.width = `${width}px`
    mount.style.height = `${height}px`
  }

  // 2. GeoGebra が作ったアプレットの要素も枠に合わせる
  const applet = mount?.firstElementChild
  if (applet instanceof HTMLElement) {
    applet.style.width = `${width}px`
    applet.style.height = `${height}px`
  }

  // 3. 内部の描画領域も合わせる（同じ大きさなら送らない）
  const api = window.ggbApplet
  if (api === undefined) return
  if (!force && appliedAppletSize.width === width && appliedAppletSize.height === height) return
  appliedAppletSize = { width, height }
  applyAppletSize(width, height, api)
  // 1 回で効かないことがある（GWT 側の再レイアウトと重なると無視される）ので、届くまで数回だけ試す
  retryAppletSizeIfNeeded(APPLET_SIZE_RETRY)
}

/** 大きさを GeoGebra へ伝える（使える API を順に呼ぶ。1 つ失敗しても他を試す）。 */
function applyAppletSize(width: number, height: number, api: GeoGebraApi): void {
  // 1. まず大きさを伝える（このときは再描画を止めない）
  callQuietly(() => api.setWidth?.(width))
  callQuietly(() => api.setHeight?.(height))
  callQuietly(() => api.setSize?.(width, height))
  // 2. 「大きさが変わった」と見せて、作図エリア全体を描き直させる
  nudgeAppletSize(width, height, api)
  // 3. 「描き直しのサイクル」を回す（下の scheduleRepaintCycles）。
  //    1 回で効かないことがあるので、間隔をあけてもう何度か同じことをする
  scheduleRepaintCycles(width, height)
}

/**
 * 大きさを 1px だけ広げてから戻し、**作図エリア全体を描き直させる**。
 *
 * <p>GeoGebra は「大きさが変わっていない」と描き直さない。枠が広がったときに
 * `setSize` を何度送っても（`refreshViews` や再描画の停止→再開を組み合わせても）
 * 絵は古い幅のまま残り、右側が透けて白く見えていた（実測: canvas は 1498 まで
 * 広がったのに、描かれていたのは古い幅 903 の所まで）。1px 広げてから戻すと
 * GeoGebra は大きさが変わったと見て**全域を描き直す**（実測: 同じ状態で絵が
 * 右端の 2544 まで届いた。費用は 40ms ほどで、アプレットを作り直す 1〜2 秒より
 * ずっと安く、作図も取り消し履歴もそのまま残る）。</p>
 */
function nudgeAppletSize(width: number, height: number, api: GeoGebraApi): void {
  callQuietly(() => api.setSize?.(width + APPLET_SIZE_NUDGE_PX, height))
  callQuietly(() => api.setSize?.(width, height))
}

/**
 * 描き直しのサイクルを予約する（前の予約は取り消す）。
 *
 * <p>1 回では効かない。GeoGebra の GWT 側の再レイアウトと重なると、止めている間の
 * `setSize` が無視されて**広がった分が白いまま**残る（実測: 枠を変えた直後の 1 回は効かず、
 * 落ち着いてから同じ手順を回すと描かれた）。なので間隔をあけて数回回す。</p>
 */
function scheduleRepaintCycles(width: number, height: number): void {
  cancelRepaintCycles()
  APPLET_REPAINT_CYCLE_DELAYS_MS.forEach((delay) => {
    repaintCycleTimers.push(window.setTimeout(() => {
      if (appletDisposed) return
      const api = window.ggbApplet
      if (api === undefined) return
      // 止めている間に大きさを伝える＝ GeoGebra は全体を描き直す
      callQuietly(() => api.setRepaintingActive?.(false))
      callQuietly(() => api.setWidth?.(width))
      callQuietly(() => api.setHeight?.(height))
      callQuietly(() => api.setSize?.(width, height))
      scheduleRepaintResume()
    }, delay))
  })
}

/** 予約した描き直しのサイクルを取り消す（画面を離れるとき・新しく予約するとき）。 */
function cancelRepaintCycles(): void {
  for (const timer of repaintCycleTimers) window.clearTimeout(timer)
  repaintCycleTimers = []
}

/** 再描画の再開を予約する（前の予約は取り消す＝いちばん最後の 1 回だけ効かせる）。 */
function scheduleRepaintResume(): void {
  if (repaintResumeTimer !== null) window.clearTimeout(repaintResumeTimer)
  repaintResumeTimer = window.setTimeout(() => {
    repaintResumeTimer = null
    if (appletDisposed) return
    const api = window.ggbApplet
    if (api === undefined) return
    callQuietly(() => api.setRepaintingActive?.(true))
    // 大きさを変えた直後は内部のビューが古い大きさのまま残ることがあるので、作り直させる
    callQuietly(() => api.refreshViews?.())
    // 再開したあとにも「1px ずらし」を入れる。止めている間に送った大きさは無視されることがあり、
    // 落ち着いてから同じことをもう一度やると描かれた（実測）
    const size = appliedAppletSize
    if (size.width > 0 && size.height > 0) nudgeAppletSize(size.width, size.height, api)
  }, APPLET_REPAINT_RESUME_DELAY_MS)
}

function callQuietly(action: () => void): void {
  try {
    action()
  } catch {
    // アプレット側の都合で大きさを変えられなくても、作図は続けられる
  }
}

/**
 * アプレットの大きさが枠に合っているか。
 *
 * 見るのは 2 つ。どちらも「GeoGebra が古い大きさのまま」を意味する:
 * 1. **描画領域の右端** … いちばん大きい canvas の右端がアプレットの右端まで届いているか
 * 2. **内部ビューの幅** … `getViewProperties().width` が canvas の幅と一致しているか。
 *    大きさが合っていても内部ビューが古いままだと、右側だけ白いまま残る
 *    （`getXML()` は作図全体を直列化して重いので、軽い `getViewProperties()` を使う）。
 *
 * 分からないとき（アプレット準備中・API が無い）は true を返し、余計な働きかけはしない。
 */
function appletSizeFits(): boolean {
  const expected = appliedAppletSize.width
  const mount = document.getElementById(MOUNT_ID)
  if (expected > 0 && mount instanceof HTMLElement) {
    // 入れ物の幅が、こちらが入れた値より狭くなっている（＝誰かが書き戻した）。
    // この状態では中身が入れ物の幅で切られて、右側が白いままになる。
    const declared = Number.parseFloat(mount.style.width === '' ? '0' : mount.style.width)
    if (!Number.isFinite(declared) || declared < expected - APPLET_SIZE_TOLERANCE) return false
  }
  const applet = mount?.firstElementChild
  if (!(applet instanceof HTMLElement)) return true
  const widest = widestCanvasOf(applet)
  if (widest === null) return true
  if (applet.getBoundingClientRect().right - widest.getBoundingClientRect().right > APPLET_SIZE_TOLERANCE) {
    return false
  }
  const viewWidth = viewWidthOf()
  if (viewWidth !== null && widest.width - viewWidth > APPLET_SIZE_TOLERANCE) return false
  // 3. **実際に描かれているか** … 大きさが合っていても、GeoGebra が古い大きさの範囲しか
  //    描かないことがある（広がった右側が白いまま。実測）。画素で確かめる。
  return canvasPaintedToRightEdge(widest) !== false
}

/** 画素を読むときに走査する行数。 */
const CANVAS_PAINT_PROBE_ROWS = 5

/**
 * canvas の**右端のほうまで絵**が描かれているか。
 *
 * <p>見るのは**不透明かどうか**。GeoGebra は描いた所を不透明で塗り、描いていない所は
 * 透明のまま残す（実測: 枠を広げた直後、canvas は 1498 まで広がっていたのに
 * 不透明だったのは古い幅 903 の所までで、右側は alpha 0 のままだった。ページの白が
 * 透けて「右側が白い」に見えていた）。</p>
 *
 * <p>「白でなければ描いてある」で判定すると、透明（RGB が 0）を描いてあると誤判定して
 * 見つけられない（実測）。右端の帯に不透明な画素が 1 つも無ければ「まだ描かれていない」。</p>
 *
 * @returns true=描けている / false=右端がまだ描かれていない / null=判断しない（読み戻せない環境）
 */
function canvasPaintedToRightEdge(canvas: HTMLCanvasElement): boolean | null {
  if (canvas.width === 0 || canvas.height === 0) return null
  // GeoGebra の canvas を直接読むと読み戻しの警告が出るので、1 行の作業用 canvas へ写してから読む
  const probe = document.createElement('canvas')
  probe.width = canvas.width
  probe.height = 1
  let context: CanvasRenderingContext2D | null = null
  try {
    context = probe.getContext('2d', { willReadFrequently: true })
  } catch {
    context = null
  }
  if (context === null) return null
  const bandStart = canvas.width - Math.max(24, Math.round(canvas.width * 0.08))
  let opaqueAnywhere = 0
  let opaqueAtRight = 0
  try {
    for (let step = 1; step <= CANVAS_PAINT_PROBE_ROWS; step += 1) {
      const y = Math.round((canvas.height * step) / (CANVAS_PAINT_PROBE_ROWS + 1))
      context.clearRect(0, 0, probe.width, 1)
      context.drawImage(canvas, 0, y, canvas.width, 1, 0, 0, canvas.width, 1)
      const row = context.getImageData(0, 0, canvas.width, 1).data
      for (let x = 0; x < canvas.width; x += 1) {
        const index = x * 4
        // 透明＝まだ描いていない
        if (row[index + 3] === 0) continue
        opaqueAnywhere += 1
        if (x >= bandStart) opaqueAtRight += 1
      }
    }
  } catch {
    return null
  }
  // どこも不透明でない＝読み戻せない環境（WebGL で描いている等）。判断しない
  if (opaqueAnywhere === 0) return null
  return opaqueAtRight > 0
}

/** アプレットの中の描画領域（いちばん大きい canvas）。無ければ null。 */
function widestCanvasOf(applet: HTMLElement): HTMLCanvasElement | null {
  const canvases = [...applet.querySelectorAll('canvas')]
  if (canvases.length === 0) return null
  return canvases.reduce((left, right) =>
    left.getBoundingClientRect().width >= right.getBoundingClientRect().width ? left : right)
}

/**
 * GeoGebra 内部のビュー幅（読めなければ null）。
 *
 * 返すのは「古い幅のまま」を見つけるための値。極端に大きい／小さい値は
 * 別のもの（別ビュー・未初期化）なので、判断材料にしない。
 */
function viewWidthOf(): number | null {
  try {
    const api = window.ggbApplet as (GeoGebraApi & {
      getViewProperties?: () => { width?: number } | null
    }) | undefined
    const width = api?.getViewProperties?.()?.width
    if (typeof width !== 'number' || !Number.isFinite(width) || width <= 0) return null
    return width
  } catch {
    return null
  }
}

/**
 * 大きさが届くまで送り直す（多くても数回）。
 *
 * 1 回で効かないことがある（GWT 側の再レイアウトと重なると無視される）。しかも
 * **送った時点の枠の幅が、アニメーションの途中で古い値**のことがあるので、
 * 毎回そのときの枠を測り直して送る（`syncAppletSize` をもう一度呼ぶ）。
 * それでも届かなければ、最後の手段としてアプレットを作り直す。
 */
function retryAppletSizeIfNeeded(attemptsLeft: number): void {
  window.setTimeout(() => {
    if (appletDisposed) return
    if (window.ggbApplet === undefined) return
    if (appletSizeFits()) return
    if (attemptsLeft <= 0) {
      void rebuildAppletForSize()
      return
    }
    // 枠の幅はアニメーションの途中で変わり得るので、そのときの値を測り直して**必ず**送り直す
    syncAppletSize(true)
    retryAppletSizeIfNeeded(attemptsLeft - 1)
  }, APPLET_SIZE_RETRY_DELAY_MS)
}

/**
 * 何度送っても届かないときの最後の手段（アプレットを作り直す）。
 *
 * GeoGebra が「canvas は広いのに内部ビューは狭い」という食い違った状態に入ることがあり、
 * 実測では `setSize` を何度呼んでも抜け出せない。作り直せば必ず直る（作図は XML で引き継ぎ、
 * 座標軸・グリッドの状態も戻す）。**アプレット 1 つにつき 1 回だけ**にして、
 * 画面が揺れ続けないようにする（取り消し履歴は失われる）。
 */
async function rebuildAppletForSize(force = false): Promise<void> {
  if (appletDisposed) return
  if (!force && appletRebuiltForSize) return
  appletRebuiltForSize = true
  await restartApplet()
}

/**
 * 枠の大きさの変化を見張る。
 *
 * 左メニューの折りたたみ・入力欄の表示切替は**ウィンドウのリサイズではない**ので
 * `resize` イベントでは拾えない（枠だけが広がって GeoGebra が古い大きさのまま残る）。
 * `ResizeObserver` なら、原因が何であっても枠が変わった時点で合わせられる。
 */
let frameObserver: ResizeObserver | null = null
let observedFrameSize = ''

function observeFrameSize(): void {
  const element = frame.value
  if (element === null || typeof ResizeObserver === 'undefined') return
  frameObserver?.disconnect()
  frameObserver = new ResizeObserver((entries) => {
    const entry = entries[0]
    if (entry === undefined) return
    const key = `${Math.round(entry.contentRect.width)}x${Math.round(entry.contentRect.height)}`
    if (key === observedFrameSize) return
    observedFrameSize = key
    // 高さはビューポートの残りから決めるので、幅が変わったときも測り直す
    measureFrame()
  })
  frameObserver.observe(element)
}

/**
 * 左の入力欄の表示／非表示を切り替える（折りたたむと作図エリアが広がる）。
 *
 * **作り直さない**。以前はこの操作でアプレットを作り直しており、1〜2 秒かかるうえ
 * 取り消し履歴が消えて「引っかかる」と感じられた。今は大きさを伝えたあとに
 * 「1px ずらして戻す」（`nudgeAppletSize`）で全域を描き直させるので、待ち時間も
 * ちらつきも無い。それでも右端が描かれないときだけ、送り直しの最後の手段として
 * 作り直す（`retryAppletSizeIfNeeded`）。
 */
function toggleCollapsed(): void {
  collapsed.value = !collapsed.value
  // 表示が変わってから測り直す（作図エリアの幅も変わる）
  void nextTick(() => {
    measureFrame()
  })
}

/** AI 画図助手の浮動ウィンドウを開く／閉じる（見出しの【AI 助手】と × で使う）。 */
function toggleAiAssist(): void {
  assistDialogOpen.value = !assistDialogOpen.value
  if (!assistDialogOpen.value) {
    // 閉じるときは、つまんでいたのを止めて位置と大きさを覚える
    endAssistPointer()
    saveAssistWindow()
    return
  }
  // 覚えている位置と大きさで開く（初めてなら左の入力欄に重なる縦長。画面の外には出さない）
  loadAssistWindow()
  // 中身を描いたあとに測って、はみ出していないか確かめる
  void nextTick(onAssistWindowResize)
  // この図形の会話ログを読み込む（**作図は変えない**。表示するだけ）
  loadAssistLog()
  // 端末をまたいで同じ履歴を見せるため、サーバーの履歴も読んで混ぜる
  void loadAssistHistory()
}

/* ---------- GeoGebra の操作 ---------- */

/** 今の作図データ（GeoGebraXML）。準備前は読み込み待ちの XML を返す。 */
function exportConstruction(): string {
  const api = ggbApi
  if (api === null || typeof api.getXML !== 'function') return pendingConstruction ?? ''
  try {
    const xml = api.getXML()
    return xml === null || xml === undefined ? '' : String(xml)
  } catch {
    return ''
  }
}

/** 今の作図のサムネイル（Base64 PNG）。取れなければ null。 */
function exportThumbnail(): string | null {
  const api = ggbApi
  if (api === null || typeof api.getPNGBase64 !== 'function') return null
  try {
    const base64 = api.getPNGBase64(1.5, false)
    return base64 === null || base64 === '' ? null : base64
  } catch {
    return null
  }
}

/** 作図データをアプレットに反映する（準備前なら覚えておく）。 */
function applyConstruction(xml: string): void {
  const api = ggbApi
  if (api === null || typeof api.setXML !== 'function') {
    pendingConstruction = xml
    return
  }
  try {
    api.setXML(xml)
  } catch {
    toast.danger('作図データを読み込めませんでした。')
  }
  // 読み込んだ作図をコマンド欄にも出す（利用者が編集していれば、その内容は残す）
  syncCommandsFromCanvas()
}

/* ---------- コマンド欄（いまの作図を表す） ---------- */

/**
 * いまの**作図そのもの**からコマンド欄を作り直す。
 *
 * <p>AI が返したコマンドや、利用者が打ち込んだ内容をそのまま並べるのではなく、
 * アプレットの実際の作図から作る（利用者の指示）。</p>
 *
 * <p>アプレットを**読むだけ**なので同期のループにはならない。ドラッグ中でも安全。</p>
 *
 * <p>利用者が編集中（`commandsEdited`）の入力は上書きしない。実行に成功したとき・
 * 作図を読み込んだときは、呼ぶ側で `commandsEdited` を下ろしてから呼ぶ。</p>
 */
function syncCommandsFromCanvas(): void {
  const result = exportConstructionCommands(ggbApi)
  canvasCommands.value = result.lines
  canvasCommandSkips.value = result.skipped
  if (!commandsEdited.value) commands.value = result.lines.join('\n')
}

/**
 * コマンド欄の同期を予約する（**少し待って 1 回だけ**）。
 *
 * 点をドラッグしている間は更新通知が何度も来るので、まとめて 1 回にする
 * （作図を止めてから 300ms で追いつく。ドラッグ中に読み続けないので引っかからない）。
 */
function scheduleCommandSync(): void {
  if (commandSyncTimer !== null) window.clearTimeout(commandSyncTimer)
  commandSyncTimer = window.setTimeout(() => {
    commandSyncTimer = null
    syncCommandsFromCanvas()
  }, COMMAND_SYNC_DEBOUNCE_MS)
}

/** コマンド欄を**いまの作図**に戻す（編集をやめる）。 */
function resetCommandsToCanvas(): void {
  commandsEdited.value = false
  commands.value = canvasCommands.value.join('\n')
}

/** コマンド欄を利用者が編集した（以降の自動同期で上書きしない）。 */
function markCommandsEdited(): void {
  commandsEdited.value = true
}

/** コマンド欄を空にする（利用者の操作。以降の自動同期で勝手に戻さない）。 */
function clearCommandInput(): void {
  commandsEdited.value = true
  commands.value = ''
}

/**
 * サムネイルの取り直しを予約する（**自動更新。案内は出さない**）。
 *
 * `runCommands()` や AI 画図助手の【反映】、GeoGebra 側の操作（点を動かす等）で
 * 作図が変わると呼ばれる。900ms 待ってから 1 回だけ取り直し、失敗しても黙っている
 * （保存の直前に取り直す経路があるので、ここで知らせる必要は無い）。
 */
function scheduleThumbnailRefresh(): void {
  if (thumbnailTimer !== null) window.clearTimeout(thumbnailTimer)
  thumbnailTimer = window.setTimeout(() => {
    thumbnailTimer = null
    refreshThumbnailSilently()
  }, THUMBNAIL_DEBOUNCE_MS)
}

/** アプレットの更新通知を購読する（対応していない実装でも落とさない）。 */
function subscribeAppletUpdates(api: GeoGebraApi): void {
  /** 作図の変化を受けて、サムネイル・コマンド欄・座標軸グリッドの表示を追う。 */
  const onUpdate = (): void => {
    scheduleThumbnailRefresh()
    // 手動で描いた・動かした・消した・改名した・取り消した（undo/redo）のどれでも追いつく
    scheduleCommandSync()
    // GeoGebra 自身のメニュー（歯車）で切り替えられても checkbox がずれないように読む
    syncAxesGridFromApplet()
  }
  try {
    api.registerUpdateListener?.(onUpdate)
    api.registerClientListener?.(onUpdate)
  } catch {
    // 購読できない実装でも、コマンド実行・保存時に取り直すので致命的ではない
  }
}

/* ---------- 座標軸・グリッドの表示（上部の操作行の checkbox） ---------- */

/** 覚えている選択を読む（無ければ null＝まだ選んでいない）。 */
function loadAxesGrid(): { axes: boolean; grid: boolean } | null {
  try {
    const raw = window.localStorage.getItem(AXES_GRID_KEY)
    if (raw === null) return null
    const parsed = JSON.parse(raw) as { axes?: unknown; grid?: unknown }
    if (typeof parsed?.axes !== 'boolean' || typeof parsed?.grid !== 'boolean') return null
    return { axes: parsed.axes, grid: parsed.grid }
  } catch {
    return null
  }
}

/** 選んだ状態を覚える（保存できない環境でも操作は続けられる）。 */
function saveAxesGrid(): void {
  try {
    window.localStorage.setItem(AXES_GRID_KEY, JSON.stringify({
      axes: axesVisible.value,
      grid: gridVisible.value
    }))
  } catch {
    // 保存できなくても表示の切替は使える
  }
}

/** 座標軸（x と y を同時に）の表示をアプレットに伝える（対応していない実装でも落とさない）。 */
function applyAxes(axes: boolean): void {
  try {
    ggbApi?.setAxesVisible?.(axes, axes)
  } catch {
    // アプレットの都合で切り替えられなくても、作図は続けられる
  }
}

/** グリッドの表示をアプレットに伝える。 */
function applyGrid(grid: boolean): void {
  try {
    ggbApi?.setGridVisible?.(grid)
  } catch {
    // 同上
  }
}

/**
 * いまの表示（座標軸・グリッド）を**保存される作図データ（XML）から読む**。
 *
 * `getVisible('xAxis')` は「xAxis というオブジェクト」の表示を返すため、作図エリアの設定
 * （`<evSettings axes="…" grid="…">`。歯車メニューの「軸を表示」「グリッドを表示」と同じ）とは
 * **一致しない**。実機で測ったところ `setAxesVisible` / `setGridVisible` の効果は XML に出るのに
 * `getVisible('xAxis')` は変わらなかったので、状態は XML から読む。
 * 読めない実装では**何もしない**（勝手に上書きしない）。
 */
function syncAxesGridFromApplet(): boolean {
  const api = ggbApi
  if (api === null || typeof api.getXML !== 'function') return false
  try {
    const xml = api.getXML()
    const axes = /evSettings[^>]*\baxes="([^"]*)"/.exec(xml)?.[1]
    const grid = /evSettings[^>]*\bgrid="([^"]*)"/.exec(xml)?.[1]
    if (axes === undefined || grid === undefined) return false
    axesGridSyncing = true
    axesVisible.value = axes === 'true'
    gridVisible.value = grid === 'true'
    return true
  } catch {
    return false
  } finally {
    axesGridSyncing = false
  }
}

/**
 * checkbox が変わったらアプレットに伝える（作図の変化なのでサムネイルも取り直す）。
 *
 * `@change` と `v-model` を併用すると、DOM の change と Vue の更新が二重に走ることがあるので、
 * **値の変化（`watch`）だけ**を見る。アプレットから読んだだけのときは伝えない（`axesGridSyncing`）。
 * `flush: 'sync'` にしているのは、読み取り直後の 1 回を guard で確実に飛ばすため
 * （遅延させると、アプレットから読んだだけの値でもアプレットへ送り返してしまう）。
 */
let axesGridSyncing = false
watch(axesVisible, (axes) => {
  if (axesGridSyncing) return
  applyAxes(axes)
  saveAxesGrid()
  scheduleThumbnailRefresh()
}, { flush: 'sync' })
watch(gridVisible, (grid) => {
  if (axesGridSyncing) return
  applyGrid(grid)
  saveAxesGrid()
  scheduleThumbnailRefresh()
}, { flush: 'sync' })

/**
 * アプレットを作る（作図タイプを変えたときは作り直す）。
 *
 * 読み込みに失敗したら **1 秒待って 1 回だけやり直す**（利用者の指示。
 * 【GeoGebra を再読み込み】のボタンは置かない）。それでも駄目なら
 * 作図エリアに理由を出して「ページを再読み込みしてください」と案内する。
 */
async function startApplet(retry = true): Promise<void> {
  stageError.value = ''
  let GeoGebra: GeoGebraConstructor
  try {
    GeoGebra = await ensureGeoGebra()
  } catch (caught) {
    if (appletDisposed) return
    stageError.value = messageOf(caught, GEOGEBRA_LOAD_ERROR)
    await retryAppletOnce(retry)
    return
  }
  if (appletDisposed) return

  const mount = document.getElementById(MOUNT_ID)
  if (mount === null) return
  mount.innerHTML = ''
  ggbApi = null
  appletReady.value = false
  appletType = form.figureType

  const width = Math.max(320, Math.ceil(frame.value?.clientWidth || 800))
  // 高さは**枠の中身の高さ**（枠線の内側）。外側の高さを渡すと GeoGebra が中身を縮めて描き、
  // 右側が白いまま残る（`syncAppletSize` と同じ理由）
  const height = Math.max(320,
    Math.round(frame.value?.clientHeight || frameHeight.value || 620))
  const params: Record<string, unknown> = {
    appName: form.figureType === 'function' ? 'graphing' : 'geometry',
    width,
    height,
    language: 'ja',
    showToolBar: true,
    showMenuBar: true,
    showAlgebraInput: form.figureType === 'function',
    allowStyleBar: true,
    showToolBarHelp: false,
    showResetIcon: true,
    showZoomButtons: true,
    showFullscreenButton: false,
    appletOnLoad: (api: GeoGebraApi) => {
      ggbApi = api
      window.ggbApplet = api
      appletReady.value = true
      // 作り直したアプレットでは、また 1 回だけ「大きさが届かないときの作り直し」を許す
      appletRebuiltForSize = false
      appliedAppletSize = { width: 0, height: 0 }
      // 作図が変わったらサムネイルを自動で取り直す（更新ボタンは置かない）
      subscribeAppletUpdates(api)
      // 座標軸・グリッドは、利用者が最後に選んだ状態があればそこへ戻す。
      // まだ選んでいないときは**いまのアプレットの状態**を読んで checkbox に入れる（勝手に変えない）
      const rememberedAxesGrid = loadAxesGrid()
      if (rememberedAxesGrid === null) {
        syncAxesGridFromApplet()
      } else {
        // watch からの二重適用を避けるため guard を立てて入れる
        axesGridSyncing = true
        axesVisible.value = rememberedAxesGrid.axes
        gridVisible.value = rememberedAxesGrid.grid
        axesGridSyncing = false
        applyAxes(rememberedAxesGrid.axes)
        applyGrid(rememberedAxesGrid.grid)
      }
      if (pendingConstruction !== null) {
        applyConstruction(pendingConstruction)
        pendingConstruction = null
      }
      // 読み込みが済んだら、いまの作図をコマンド欄に出す（AI のコマンドは実行後に作り直す）
      syncCommandsFromCanvas()
      // AI 生図のコマンドは applet の準備後に流し込む（XML ではなく 1 行ずつ実行する）
      if (pendingAiCommands !== null) {
        const lines = pendingAiCommands
        pendingAiCommands = null
        void nextTick(() => {
          const failure = applyCommands(lines, 'replace')
          if (failure === null) {
            aiCommandFailure.value = null
            aiNotice.value = (aiNotice.value === '' ? '' : aiNotice.value + ' ')
              + `AI のコマンドを ${lines.length} 件流し込みました。`
            refreshThumbnailSilently()
          } else {
            // 実行できなかった＝AI の結果は作図に入っていない。**そのまま保存させない**
            aiCommandFailure.value = failure.reason
            aiNotice.value = (aiNotice.value === '' ? '' : aiNotice.value + ' ')
              + 'AI のコマンドをこの画面で実行できませんでした。作図は元の状態に戻してあります。'
          }
        })
      }
    }
  }

  try {
    new GeoGebra(params, true).inject(MOUNT_ID)
  } catch {
    if (appletDisposed) return
    stageError.value = 'GeoGebra の画面を表示できませんでした。ページを再読み込みしてください。'
    await retryAppletOnce(retry)
    return
  }
  // 読み込めても作図エリアが準備できないことがある（真っ白のまま）ので、待って確かめる
  if (await waitAppletReady(APPLET_LOAD_TIMEOUT_MS)) return
  if (appletDisposed) return
  stageError.value = GEOGEBRA_LOAD_ERROR
  await retryAppletOnce(retry)
}

/**
 * 1 回だけやり直す（すでにやり直した後は何もしない）。
 *
 * やり直すのは**読み込みの失敗だけ**（利用者の作図は消さない。アプレットを作り直すだけ）。
 */
async function retryAppletOnce(retry: boolean): Promise<void> {
  if (!retry || appletDisposed) return
  await new Promise((resolve) => window.setTimeout(resolve, APPLET_RETRY_DELAY_MS))
  if (appletDisposed) return
  await startApplet(false)
}

/** 作図タイプを変えたら、その種類のアプレットで作り直す。 */
async function restartApplet(): Promise<void> {
  const current = exportConstruction()
  if (current !== '') pendingConstruction = current
  await startApplet()
}

function changeFigureType(): void {
  void restartApplet()
}

function centerView(): void {
  const api = ggbApi
  if (api === null) return
  try {
    if (typeof api.evalCommand === 'function') api.evalCommand('ZoomIn(-10, -10, 10, 10)')
    if (typeof api.CenterView === 'function') api.CenterView(0, 0)
    toast.success('作図を中央に表示しました。')
  } catch {
    toast.danger('中央に表示できませんでした。')
  }
}

/**
 * 作図を消す（**図形だけ**。座標軸・グリッドの表示は動かさない。利用者の指示）。
 *
 * <p>実機で確かめたこと: `newConstruction()` は作図だけでなく**表示の設定も初期値に戻す**
 * （`evSettings axes="true" grid="true"` → 両方 false になり、座標系の倍率も既定へ戻る）。
 * そのため名前を挙げて 1 つずつ消す（座標軸・グリッドは `getAllObjectNames()` に入らないので残る）。
 * 1 つずつ消せない実装のときだけ `newConstruction()` を使い、消したあとに選んでいた表示へ戻す。</p>
 */
function clearConstruction(silent = false): void {
  const api = ggbApi
  if (api === null) {
    pendingConstruction = null
    canvasCommands.value = []
    canvasCommandSkips.value = []
    if (!commandsEdited.value) commands.value = ''
    if (!silent) toast.success('作図を消去しました。')
    return
  }
  // いま選んでいる表示（消しても変えない）
  const axes = axesVisible.value
  const grid = gridVisible.value
  try {
    if (!deleteAllObjects(api)) {
      if (typeof api.newConstruction !== 'function') throw new Error('作図を消す API がありません。')
      api.newConstruction()
      // newConstruction は表示を初期値へ戻すので、選んでいた状態へ戻す
      restoreDisplay(axes, grid)
    }
    syncCommandsFromCanvas()
    // 1 つずつ消す方法では更新通知が来ない（実測）ので、サムネイルはここで取り直す
    scheduleThumbnailRefresh()
    if (!silent) toast.success('作図を消去しました。')
  } catch {
    if (!silent) toast.danger('作図を消去できませんでした。')
  }
}

/**
 * 作図のオブジェクトを 1 つずつ消す（消せたら true）。
 *
 * 座標軸・グリッドは `getAllObjectNames()` に入らない（実測）ので、表示はそのまま残る。
 */
function deleteAllObjects(api: GeoGebraApi): boolean {
  if (typeof api.getAllObjectNames !== 'function' || typeof api.deleteObject !== 'function') return false
  try {
    for (const name of api.getAllObjectNames()) api.deleteObject(name)
    return true
  } catch {
    return false
  }
}

/** 消去で表示が初期値へ戻ってしまったとき、選んでいた表示へ戻す（checkbox も合わせる）。 */
function restoreDisplay(axes: boolean, grid: boolean): void {
  axesGridSyncing = true
  axesVisible.value = axes
  gridVisible.value = grid
  axesGridSyncing = false
  applyAxes(axes)
  applyGrid(grid)
}

/**
 * コマンド入力の内容を作図に反映する（2.0 のコマンド欄と同じ動き）。
 *
 * 成功したら、打ち込んだ内容ではなく**実際にできた作図**からコマンド欄を作り直す
 * （多角形の辺のように、コマンドには現れない作図があるため）。
 * 失敗して元に戻したときは、**利用者が打ち込んだ内容を残す**（直して実行できるように）。
 */
function runCommands(): void {
  const api = ggbApi
  if (api === null || typeof api.evalCommand !== 'function') {
    toast.danger('GeoGebra の準備ができていません。少し待ってから実行してください。')
    return
  }
  // 欄が作図そのもの（未編集）で、コマンドにできない作図があるときは**実行しない**。
  // 実行は作図を作り直すので、スライダなどが黙って消えてしまう（欄に出せないため）。
  if (!commandsEdited.value && canvasCommandSkips.value.length > 0) {
    toast.danger(
      `いまのコマンド欄は作図と同じ内容です。スライダ・チェックボックスなど ${canvasCommandSkips.value.length} 件は`
      + 'コマンドにできないため、このまま実行すると消えます。欄を編集してから実行してください。'
    )
    return
  }
  const lines = commands.value
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line !== '')
  const backup = exportConstruction()

  try {
    clearConstruction(true)
    const failed: string[] = []
    for (const line of lines) {
      if (api.evalCommand(line) === false) failed.push(line)
    }
    if (failed.length > 0) {
      if (backup !== '') applyConstruction(backup)
      // 実行できなかったので、打ち込んだ内容はそのまま残す（作図は元に戻っている）
      syncCommandsFromCanvas()
      toast.danger(`コマンドを実行できませんでした：${failed[0]}`)
      return
    }
    toast.success(lines.length === 0 ? '作図を消去しました。' : `コマンドを ${lines.length} 件実行しました。`)
    // 実行できたので、コマンド欄はいまの作図に合わせる
    commandsEdited.value = false
    syncCommandsFromCanvas()
    // コマンドで作図が変わったのでサムネイルを取り直す（自動。案内は出さない）
    scheduleThumbnailRefresh()
  } catch {
    if (backup !== '') applyConstruction(backup)
    syncCommandsFromCanvas()
    toast.danger('コマンドを実行できませんでした。')
  }
}

/**
 * コマンド列を作図に反映する（AI 生図・AI 画図助手の共通処理）。
 *
 * `mode` が `replace` のときは作図全体を作り直す（**AI 生図**を開いたときだけ。AI 画図助手は
 * いつも `append` ＝ いまの作図に追加する）。`runCommands()` と同じく、
 * **1 つでも失敗したら元の作図に戻す**（途中の汚い状態を残さない）。
 *
 * @returns 成功したら `null`、失敗したら**理由**（どの行が駄目だったかを含む。画面に残す）
 */
/** コマンドの実行に失敗したときの中身（画面の理由表示と、AI への自動修正の材料）。 */
interface ApplyFailure {
  /** 画面に出す理由（日本語）。 */
  reason: string
  /** 実行できなかった行。 */
  failedLines: string[]
  /** 実行できなかった行が参照していたのに、作図に無かった名前（分かれば）。 */
  missingReferences: string[]
}

/**
 * AI のコマンドを作図へ流し込む。
 *
 * <p>失敗したら元の作図へ戻し、**理由と中身**を返す（null なら成功）。「中身」を返すのは、
 * 失敗のあと**AI へ自動で修正を頼む**ため（実行できなかった行と、作図に無い名前を渡す）。</p>
 */
function applyCommands(lines: string[], mode: 'append' | 'replace'): ApplyFailure | null {
  const api = ggbApi
  if (api === null || typeof api.evalCommand !== 'function') {
    const reason = 'GeoGebra の準備ができていません。少し待ってから実行してください。'
    toast.danger(reason)
    return { reason, failedLines: [], missingReferences: [] }
  }
  const backup = exportConstruction()
  try {
    if (mode === 'replace') clearConstruction(true)
    const failed: string[] = []
    for (const line of lines) {
      const value = line.trim()
      if (value === '' || value.startsWith('#')) continue
      if (api.evalCommand(value) === false) failed.push(value)
    }
    if (failed.length > 0) {
      // **巻き戻す前に**調べる: この案が自分で作った名前（例 `A = Point(c)`）は、
      // 巻き戻すと作図から消えるので「作図に無い」と誤って言ってしまう（実測で誤報が出た）
      const missing = missingReferencesOf(failed)
      if (backup !== '') applyConstruction(backup)
      // 巻き戻したあとの作図をコマンド欄に反映する（AI が返した行は並べない）
      syncCommandsFromCanvas()
      // どこで止まったか分かるように、失敗した行を（多いときは先頭 3 件だけ）出す
      const shown = failed.slice(0, 3).join(' / ')
      const rest = failed.length > 3 ? ` ほか ${failed.length - 3} 件` : ''
      // 「作図に無い名前を参照している」ときはその名前も出す（AI の案が名前を作り話にしている場合）
      const missingNote = missing.length === 0 ? '' : `（${missing.join(', ')} は作図にありません）`
      const reason = `AI のコマンドを実行できませんでした：${shown}${rest}${missingNote}`
      toast.danger(reason)
      return { reason, failedLines: failed, missingReferences: missing }
    }
    // 実行できたので、AI が返した行ではなく**実際にできた作図**からコマンド欄を作る
    commandsEdited.value = false
    syncCommandsFromCanvas()
    return null
  } catch {
    if (backup !== '') applyConstruction(backup)
    syncCommandsFromCanvas()
    const reason = 'AI のコマンドを実行できませんでした。'
    toast.danger(reason)
    return { reason, failedLines: [], missingReferences: [] }
  }
}

/** 参照チェックで「名前ではない」とみなす語（定数・キーワード）。 */
const REFERENCE_KEYWORDS = new Set(['true', 'false', 'pi', 'PI', 'π', '∞', 'NaN', 'Infinity', 'e'])

/**
 * 実行できなかった行が参照しているのに、**作図に無い名前**を返す（分かった分だけ）。
 *
 * <p>AI は存在しない点を参照することがある（実測: 円だけ描いてある状態で `Polygon(A, B, C, 3)` を返し、
 * A・B・C も未定義だった）。GeoGebra のエラーは引数の数の話をするので、名前が無いことは伝わらない。
 * ここで拾って理由に足し、AI への修正依頼にも渡す。</p>
 *
 * <p>誤検出より**見逃しの方がまし**なので、確実に名前と分かるものだけを対象にする
 * （コマンド名＝直後が `(` か `[` の語、`f(x) = …` の引数、文字列の中身、数値は対象外）。</p>
 */
function missingReferencesOf(lines: string[]): string[] {
  const api = ggbApi
  if (api === null || typeof api.exists !== 'function') return []
  const known = new Set<string>()
  try {
    for (const name of api.getAllObjectNames?.() ?? []) known.add(name)
  } catch {
    return []
  }
  const missing: string[] = []
  for (const line of lines) {
    // 文字列の中身（"red" など）は名前ではない
    const source = line.replace(/"[^"]*"/g, '""')
    // この行で定義する名前（`A = …` / `f(x) = …` の f。引数 x はこの行の中だけで使える）
    const definition = /^\s*([A-Za-z_][A-Za-z0-9_]*)\s*(?:\(([^)]*)\))?\s*=/.exec(source)
    const localNames = new Set<string>()
    if (definition !== null) {
      localNames.add(definition[1])
      for (const parameter of (definition[2] ?? '').split(',')) {
        const name = parameter.trim()
        if (/^[A-Za-z_][A-Za-z0-9_]*$/.test(name)) localNames.add(name)
      }
    }
    const tokens = source.matchAll(/[A-Za-z_][A-Za-z0-9_]*/g)
    for (const token of tokens) {
      const name = token[0]
      const rest = source.slice((token.index ?? 0) + name.length)
      // 直後が ( か [ ならコマンド名・関数名（オブジェクト名ではない）
      if (/^\s*[([]/.test(rest)) continue
      if (REFERENCE_KEYWORDS.has(name) || localNames.has(name)) continue
      if (known.has(name)) continue
      if (definition !== null && definition[1] === name && (token.index ?? 0) === source.indexOf(name)) continue
      let exists = false
      try {
        exists = api.exists(name) === true
      } catch {
        exists = false
      }
      if (!exists && !missing.includes(name)) missing.push(name)
    }
  }
  return missing
}

/**
 * アプレット（GeoGebra）が使えるようになるまで待つ。
 *
 * AI の結果はアプレットの準備より先に返ることがある（画面を開いた直後の指示）。
 * 反映は自動なので、ここで待たないと**案が無駄になる**（利用者は押せない）。
 */
async function waitAppletReadyForApply(timeoutMs: number): Promise<boolean> {
  const deadline = Date.now() + timeoutMs
  while (ggbApi === null || typeof ggbApi.evalCommand !== 'function') {
    if (Date.now() >= deadline) return false
    await new Promise((resolve) => window.setTimeout(resolve, APPLET_WAIT_INTERVAL_MS))
  }
  return true
}

/**
 * サムネイルを取り直してプレビューに出す（**案内は出さない**）。
 *
 * 自動更新（作図の変更・AI のコマンド流し込み）と保存の直前で使う。
 * 取れなかったときは何もしない＝直前のプレビュー（または読み込んだ図形のサムネイル）を残す。
 *
 * @returns 取り直せたか
 */
function refreshThumbnailSilently(): boolean {
  const base64 = exportThumbnail()
  if (base64 === null) return false
  loadedThumbnail.value = base64
  preview.value = thumbnailDataUrl(base64)
  return true
}

/* ---------- タグ ---------- */

function addTag(): void {
  const value = tagInput.value.trim()
  if (value === '') return
  if (value.includes('|')) {
    toast.danger('タグに「|」は使えません。')
    return
  }
  if (value.length > TAG_MAX_LENGTH) {
    toast.danger(`タグは${TAG_MAX_LENGTH}文字以内で入力してください。`)
    return
  }
  if (tags.value.includes(value)) {
    toast.danger('同じタグが既に付いています。')
    return
  }
  if (tags.value.length >= TAG_MAX_COUNT) {
    toast.danger(`タグは${TAG_MAX_COUNT}個までです。`)
    return
  }
  tags.value = [...tags.value, value]
  tagInput.value = ''
}

function removeTag(tag: string): void {
  tags.value = tags.value.filter((value) => value !== tag)
}

function toggleSuggestion(tag: string): void {
  if (tags.value.includes(tag)) {
    removeTag(tag)
    return
  }
  tagInput.value = tag
  addTag()
}

async function loadSuggestions(): Promise<void> {
  try {
    const response = await fetchGeometryTags()
    tagSuggestions.value = response.data.items
    await measureSuggestionRows()
  } catch {
    tagSuggestions.value = []
  }
}

/** タグ候補の「もっと見る」／「閉じる」。 */
function toggleTagSuggestions(): void {
  tagExpanded.value = !tagExpanded.value
}

/* ---------- 図形の読み込み（編集・コピーして開く） ---------- */

/** URL のクエリから図形 ID を読む（数字だけ。無ければ null）。 */
function queryFigureId(name: string): number | null {
  const raw = route.query[name]
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value !== 'string' || !/^\d+$/.test(value)) return null
  return Number(value)
}

/**
 * 読み込んだ図形を画面に反映する。
 *
 * `edit` は編集（保存すると更新）、`copy` はコピーして開く（保存すると新規作成）で、
 * 後者は `editingId` / `version` を持たない（＝図形名は元のままだが、保存で新しい図形になる）。
 */
function applyFigure(figure: GeometryFigure, mode: 'edit' | 'copy'): void {
  if (mode === 'edit') {
    editingId.value = figure.figureId
    figureNo.value = figure.figureNo
    version.value = figure.version
    updatedAt.value = figure.updatedAt
    copySourceNo.value = ''
  } else {
    editingId.value = null
    figureNo.value = ''
    version.value = null
    updatedAt.value = null
    copySourceNo.value = figure.figureNo
  }
  form.title = figure.title
  form.figureType = figure.figureType
  form.memo = figure.memo === null ? '' : figure.memo
  tags.value = [...figure.tags]
}

async function loadFigure(figureId: number, mode: 'edit' | 'copy'): Promise<void> {
  try {
    const response = await fetchGeometryFigure(figureId)
    applyFigure(response.data.figure, mode)
    loadedThumbnail.value = response.data.thumbnail
    preview.value = thumbnailDataUrl(response.data.thumbnail)
    // 別の図形を開いたら、コマンド欄はその図形の作図に合わせる（前の編集は持ち越さない）
    commandsEdited.value = false
    if (response.data.construction !== '') applyConstruction(response.data.construction)
    // 読み込んだ作図タイプが今のアプレットと違うときは、その種類で作り直す
    if (appletType !== null && appletType !== form.figureType) await restartApplet()
  } catch (caught) {
    loadError.value = messageOf(caught, '図形を読み込めませんでした。')
    // 読み込めなかったときは新規作成として続けられるようにする
    editingId.value = null
    figureNo.value = ''
    copySourceNo.value = ''
    version.value = null
    updatedAt.value = null
  }
}

/* ---------- 保存 ---------- */

/**
 * 保存する。
 *
 * `editingId` が無いとき（新規作成・コピーして開いたとき）は新規作成、
 * あるとき（編集）はバージョン付きで更新する。`asNew` は【名前を付けて保存】。
 */
async function save(asNew: boolean): Promise<void> {
  if (saving.value) return
  // AI 生図のコマンドがこの画面で実行できていないときは保存させない。
  // 保存できてしまうと「AI が作った図形」として、**AI の結果が入っていない作図**が登録される
  // （利用者から見ると成功に見える）。理由を出して、まず作り直すか手で直してもらう。
  const ai = aiRequest.value
  const failure = aiCommandFailure.value
  if (failure !== null && ai !== null && ai.status === 'READY') {
    toast.danger('AI のコマンドを実行できなかったため、この結果は AI 生図として保存できません。'
      + 'もう一度生成するか、コマンド欄から手で直してください。')
    aiNotice.value = 'AI のコマンドを実行できなかったため、保存できません。'
      + 'もう一度生成するか、コマンド欄から手で直してください。'
    return
  }
  saving.value = true
  const target = asNew ? null : editingId.value
  try {
    // 保存の直前にサムネイルを取り直す（自動更新は 900ms 待つので、
    // 直前の変更がまだ反映されていないことがある。空のまま保存しない）
    let thumbnail = exportThumbnail()
    if (thumbnail === null && appletReady.value) {
      // 準備できているのに取れないときは 1 度だけ待って再挑戦する
      await new Promise((resolve) => window.setTimeout(resolve, 150))
      thumbnail = exportThumbnail()
    }
    if (thumbnail === null) {
      // それでも取れないときは、読み込んだ図形が持っていたサムネイルを使う（従来どおり）
      thumbnail = loadedThumbnail.value
    } else {
      // 保存する画像を画面にも出す（プレビューと保存内容を食い違わせない）
      loadedThumbnail.value = thumbnail
      preview.value = thumbnailDataUrl(thumbnail)
    }

    const body = {
      title: form.title.trim() === '' ? UNTITLED : form.title.trim(),
      figureType: form.figureType,
      memo: form.memo.trim() === '' ? undefined : form.memo.trim(),
      tags: [...tags.value],
      construction: exportConstruction(),
      thumbnail: thumbnail ?? undefined
    }

    // AI 生図から開いていて、まだ保存していないときは**確定**（登録元コード = AI）にする
    if (!asNew && ai !== null && ai.status === 'READY') {
      const confirmed = await confirmGeometryAiRequest(ai.requestId, {
        title: body.title,
        figureType: body.figureType,
        memo: body.memo,
        tags: body.tags,
        construction: body.construction,
        thumbnail: body.thumbnail,
        version: ai.version
      })
      const created = await fetchGeometryFigure(confirmed.data.figure.figureId)
      applyFigure(created.data.figure, 'edit')
      // 図形になったので、この図形の履歴として覚える（次に編集で開くと出る）
      saveAssistLog()
      loadedThumbnail.value = created.data.thumbnail
      preview.value = thumbnailDataUrl(created.data.thumbnail)
      // 正式に保存できたので「保存完了」にする（READY の間は**まだ保存されていない**）
      aiRequest.value = { ...ai, status: 'REGISTERED', statusLabel: '保存完了（図形として保存済み）', figureId: created.data.figure.figureId }
      aiNotice.value = confirmed.data.message
      toast.success(confirmed.data.message)
      return
    }

    const response = target === null
      ? await createGeometryFigure(body)
      : await updateGeometryFigure(target, { ...body, version: version.value ?? undefined })

    // 保存できたら、その図形の編集として続ける（コピー元の表示は消える）
    applyFigure(response.data.figure, 'edit')
    // 新規作成のあいだ貯めていた履歴を、この図形の履歴として引き継ぐ（編集で開くと出る）
    saveAssistLog()
    const savedThumbnail = body.thumbnail ?? null
    loadedThumbnail.value = savedThumbnail
    preview.value = thumbnailDataUrl(savedThumbnail)
    toast.success(asNew ? `${response.data.message}（別名で保存しました）` : response.data.message)
  } catch (caught) {
    toast.danger(messageOf(caught, '図形を保存できませんでした。'))
  } finally {
    saving.value = false
  }
}

function backToList(): void {
  void router.push({ path: `/${area.value}/geometry` })
}

/* ---------- AI 生図（?geometryAiRequestId=） ---------- */

/** URL のクエリから AI 生図の要求 ID を読む（数字だけ。無ければ null）。 */
function queryAiRequestId(): number | null {
  // 画面の契約は ?geometryAiRequestId=。設計時の ?aiRequest= も受け付ける
  return queryFigureId('geometryAiRequestId') ?? queryFigureId('aiRequest')
}

/**
 * AI 生図の結果を読み込んで作図に流し込む。
 *
 * AI は **GeoGebra のコマンド列**しか作らない（XML とサムネイルはこの画面の applet が作る）ので、
 * `setXML` ではなく `evalCommand` を使う。作図タイプ（Geometry / Graphing）は
 * 要求の `figureType` で先に決める（後から変えると XML が壊れやすい）。
 */
async function loadAiRequest(requestId: number): Promise<void> {
  aiRequestId.value = requestId
  aiCommandFailure.value = null
  try {
    const response = await fetchGeometryAiRequest(requestId)
    aiRequest.value = response.data
    if (response.data.status !== 'READY' && response.data.status !== 'REGISTERED') {
      aiNotice.value = `AI 生図 ${response.data.requestNo} はまだ作図に使える状態ではありません（${response.data.statusLabel}）。`
      return
    }
    form.figureType = response.data.figureType as GeometryFigureType
    const proposal = response.data.proposal
    if (proposal !== null) {
      // 提案があれば初期値として入れておく（利用者が上書きできる）
      if (proposal.title !== null && proposal.title !== '') form.title = proposal.title
      if (proposal.memo !== null && proposal.memo !== '') form.memo = proposal.memo
      if (proposal.tags.length > 0) tags.value = [...proposal.tags]
    }
    aiNotice.value = `AI 生図 ${response.data.requestNo} の作図を読み込みました。`
      + ' 内容を確認・調整してから【保存】を押すと、AI 生図の図形として登録されます。'
  } catch (caught) {
    aiNotice.value = messageOf(caught, 'AI 生図の結果を読み込めませんでした。')
    toast.danger(aiNotice.value)
  }
}

/* ---------- AI 画図助手 ---------- */

/** 助手が使えるかを設定から読む（無効なら入力欄を出さない）。 */
async function loadAssistOptions(): Promise<void> {
  try {
    const response = await fetchGeometryAiOptions()
    assistAvailable.value = response.data.enabled && response.data.assistEnabled
    if (!assistAvailable.value) {
      assistNotice.value = 'AI 画図助手は現在ご利用いただけません（システム設定で無効になっています）。'
    }
  } catch {
    assistAvailable.value = false
    assistNotice.value = 'AI 画図助手の設定を読み込めませんでした。'
  }
}

/**
 * 【送信】: 指示を送る（**依頼を作る → 実行を 1 回起動 → 状態をポーリング**）。
 *
 * AI は 60〜180 秒かかるので、画面は結果を待ち続けない。返ってきた変更案は
 * **会話ログに 1 ステップとして積み、そのまま作図へ反映する**（利用者の指示。押させない）。
 * 失敗の理由も**そのステップの中**に出す。
 *
 * 反映方法は**追加だけ**（作り直さない）。作図を消すかどうかは利用者が決める（【全消去】）。
 */
async function sendAssist(): Promise<void> {
  if (!canSendAssist.value) return
  const instruction = aiAssistInstruction.value.trim()
  // 反映方法は追加だけ（画面に選択は置かない）
  const mode = ASSIST_APPLY_MODE
  // この指示を出す前の作図を控える（【戻す】で戻る先）
  const snapshot = exportConstruction()
  const kept = keepSnapshot(snapshot)
  assistNotice.value = ''
  try {
    // ① 依頼を作る（この時点では AI は動いていない）
    const created = await createGeometryAiAssist({
      figureId: editingId.value,
      construction: snapshot,
      instruction,
      mode,
      // 画面が作った一覧（「名前 = 定義（型）」）。円の中心・半径などを AI が推測しなくて済む
      objects: objectsSummaryOf()
    })
    const request = created.data
    const step: AssistStep = {
      id: ++assistStepSeq,
      instruction,
      mode,
      commands: [],
      description: null,
      assistId: request.assistId,
      status: 'running',
      error: null,
      snapshot: kept.snapshot,
      superseded: false,
      snapshotOmitted: kept.snapshotOmitted,
      // サーバーにも同じ作図が入る（端末に覚えられなくても、別の端末から【戻す】できる）。
      // ただしサーバーの上限を超える作図は切られてしまうので、そのときは戻り先にしない
      beforeXmlAvailable: snapshot !== '' && snapshot.length <= ASSIST_SERVER_SNAPSHOT_MAX,
      expanded: false,
      repairTried: false,
      repairedFrom: null
    }
    assistLog.value = pushAssistStep(step)
    aiAssistInstruction.value = ''
    saveAssistLog()
    // ② 実行を 1 回だけ起動（同じ assistId は二度走らせない）→ ③ 状態をポーリング
    void runAssistOnce(request.assistId, request.runPath)
    startAssistPolling(step)
  } catch (caught) {
    assistLog.value = pushAssistStep({
      id: ++assistStepSeq,
      instruction,
      mode,
      commands: [],
      description: null,
      assistId: null,
      status: 'failed',
      error: messageOf(caught, 'AI に接続できませんでした。時間をおいて試してください。'),
      snapshot: null,
      beforeXmlAvailable: false,
      superseded: false,
      snapshotOmitted: false,
      expanded: false,
      repairTried: true,
      repairedFrom: null
    })
    saveAssistLog()
  }
}

/* ---------- AI へ渡す作図の材料 ---------- */

/** 一覧に出すオブジェクトの上限（プロンプトを太らせない）。 */
const OBJECTS_SUMMARY_MAX = 60
/** 一覧から外す補助的なオブジェクト（座標軸・グリッド）。 */
const OBJECTS_SUMMARY_SKIP = new Set(['xAxis', 'yAxis', 'zAxis', 'grid'])

/** GeoGebra の種類 → 日本語（AI に読ませるので一般的な語にする）。 */
const OBJECT_TYPE_LABELS: Record<string, string> = {
  point: '点',
  segment: '線分',
  line: '直線',
  ray: '半直線',
  vector: 'ベクトル',
  polygon: '多角形',
  conic: '円・円錐曲線',
  circle: '円',
  arc: '弧',
  angle: '角',
  numeric: '数値',
  text: 'テキスト',
  function: '関数',
  curve: '曲線',
  locus: '軌跡',
  list: 'リスト'
}

/**
 * AI へ渡す作図の一覧（「名前 = 定義（型）」の改行区切り）。
 *
 * <p>XML には**コマンドで作った図形の定義が残らない**ことがある（実測: 円は `type="conic"` と
 * 行列だけで、中心も半径も読めない）。AI は「c は円らしい」と推測するしかなく、円の中心を使う
 * 作図（内接する正三角形など）で間違えやすかった。画面は applet から定義文字列を取れるので、
 * それを渡す（例: `c = Circle((0, 0), 3)（円・円錐曲線）`）。</p>
 *
 * @returns 一覧（作図が空・API が無いときは null＝サーバーは XML の名前で代用する）
 */
function objectsSummaryOf(): string | null {
  const api = ggbApi
  if (api === null || typeof api.getAllObjectNames !== 'function') return null
  let names: string[] = []
  try {
    names = api.getAllObjectNames() ?? []
  } catch {
    return null
  }
  const lines: string[] = []
  for (const name of names) {
    if (name === '' || OBJECTS_SUMMARY_SKIP.has(name)) continue
    let definition: string | null = null
    try {
      const value = api.getDefinitionString?.(name)
      definition = typeof value === 'string' && value.trim() !== '' ? value.trim() : null
      if (definition === null) {
        const fallback = api.getValueString?.(name)
        definition = typeof fallback === 'string' && fallback.trim() !== '' ? fallback.trim() : null
      }
    } catch {
      definition = null
    }
    let type: string | null = null
    try {
      const value = api.getObjectType?.(name)
      type = typeof value === 'string' && value.trim() !== '' ? value.trim() : null
    } catch {
      type = null
    }
    const label = definition === null ? name : `${name} = ${definition}`
    const typeLabel = type === null ? null : (OBJECT_TYPE_LABELS[type] ?? type)
    lines.push(typeLabel === null ? label : `${label}（${typeLabel}）`)
    if (lines.length >= OBJECTS_SUMMARY_MAX) break
  }
  return lines.length === 0 ? null : lines.join('\n')
}

/** 実行できなかった内容を、AI へ渡す 1 つの文にする（自動修正の材料）。 */
function failureDetailOf(failure: ApplyFailure): string {
  const lines: string[] = ['実行できなかったコマンド:']
  for (const command of failure.failedLines) lines.push(`  ${command}`)
  if (failure.missingReferences.length > 0) {
    lines.push(`作図に無い名前を参照しています: ${failure.missingReferences.join(', ')}`)
  }
  lines.push('GeoGebra がこの行を受け付けませんでした（引数の数・コマンドの形を確認してください）。')
  return lines.join('\n')
}

/* ---- 実行の起動とポーリング ---- */

/** 実行を起動した依頼（**同じ `assistId` を二度走らせない**）。 */
const assistRunStarted = new Set<number>()
/** ポーリングのタイマーと、始めた時刻・対象のステップ。 */
let assistPollTimer: number | null = null
let assistPollStartedAt = 0
let assistPollStepId: number | null = null
/** 続けて失敗した回数（3 回続いたら理由を画面に出す。黙って回り続けない）。 */
let assistPollFailures = 0

/** 実行を 1 回だけ起動する（失敗しても状態はポーリングで取れる）。 */
async function runAssistOnce(assistId: number, runPath: string): Promise<void> {
  if (assistRunStarted.has(assistId)) return
  assistRunStarted.add(assistId)
  try {
    await runGeometryAiAssist(runPath)
  } catch {
    // 起動に失敗しても、状態のポーリングが真の状態を返す（ここでは何も出さない）
  }
}

/** ポーリングを始める（すぐ 1 回問い合わせてから、一定間隔で続ける）。 */
function startAssistPolling(step: AssistStep): void {
  stopAssistPolling()
  assistPollStepId = step.id
  assistPollStartedAt = Date.now()
  assistPollFailures = 0
  void pollAssistOnce(step)
  assistPollTimer = window.setInterval(() => { void pollAssistOnce(step) }, ASSIST_POLL_INTERVAL_MS)
}

/** ポーリングを止める（画面を離れたとき・結果が出たとき）。 */
function stopAssistPolling(): void {
  if (assistPollTimer !== null) window.clearInterval(assistPollTimer)
  assistPollTimer = null
  assistPollStepId = null
}

/** 1 回だけ状態を問い合わせて、結果をステップに反映する。 */
async function pollAssistOnce(step: AssistStep): Promise<void> {
  if (step.assistId === null || assistPollStepId !== step.id) return
  const target = currentStep(step)
  if (target === null) return
  if (Date.now() - assistPollStartedAt > ASSIST_POLL_TIMEOUT_MS) {
    stopAssistPolling()
    // 時間切れは「失敗」ではなく**待ちすぎ**（もう一度送れば作り直せる）
    target.status = 'failed'
    target.error = '時間がかかっています。もう一度【送信】してください。'
    saveAssistLog()
    return
  }
  const assistId = step.assistId
  try {
    const response = await fetchGeometryAiAssist(assistId)
    assistPollFailures = 0
    const status = response.data
    if (status.status === 'READY') {
      stopAssistPolling()
      target.status = 'pending'
      target.commands = [...status.commands]
      target.description = status.description
      saveAssistLog()
      // 案が返ったら**そのまま作図へ反映する**（利用者の指示: 押させない）
      void applyAssistStep(target)
      return
    }
    if (status.status === 'FAILED') {
      stopAssistPolling()
      target.status = 'failed'
      target.error = status.errorMessage ?? 'AI の生成に失敗しました。'
      saveAssistLog()
    }
  } catch (caught) {
    // 依頼そのものが無い（404/410）ときは**やり直しても結果は来ない**ので、すぐ失敗にする
    // （前に开いた画面の履歴が残っていて、依頼が消えている場合がこれにあたる）
    if (caught instanceof ApiError && (caught.status === 404 || caught.status === 410)) {
      stopAssistPolling()
      target.status = 'failed'
      target.error = '依頼が見つかりません（AI の実行が取り消された可能性があります）。'
        + 'もう一度【送信】してください。'
      saveAssistLog()
      return
    }
    // 通信エラー・時間切れは一時的なことがあるので、続けて 3 回失敗したら理由を出す
    assistPollFailures += 1
    if (assistPollFailures >= 3) {
      stopAssistPolling()
      target.status = 'failed'
      target.error = messageOf(caught, '結果を確認できませんでした。もう一度【送信】してください。')
      saveAssistLog()
    }
  }
}

/**
 * その時点の作図を覚える（**大きすぎるときは覚えない**）。
 *
 * localStorage は容量が限られ、巨大な XML を入れると他の設定も保存できなくなる。
 * 覚えられないときは `snapshotOmitted` を立てて、画面で「戻せない」と案内する。
 */
function keepSnapshot(xml: string): { snapshot: string | null; snapshotOmitted: boolean } {
  if (xml === '') return { snapshot: null, snapshotOmitted: false }
  if (xml.length > ASSIST_SNAPSHOT_MAX) return { snapshot: null, snapshotOmitted: true }
  return { snapshot: xml, snapshotOmitted: false }
}

/**
 * 会話ログから**リアクティブな**ステップを取り直す。
 *
 * `sendAssist` などが持っているのは配列に入れる前の生のオブジェクトなので、
 * それを直接書き換えても画面は描き直されない（Vue の追跡は proxy の書き換えでしか働かない）。
 */
function currentStep(step: AssistStep): AssistStep | null {
  return assistLog.value.find((current) => current.id === step.id) ?? null
}

/** 会話ログに 1 ステップ足す（上限を超えたら古いものから捨てる）。 */
function pushAssistStep(step: AssistStep): AssistStep[] {
  const next = [...assistLog.value, step]
  return next.length > ASSIST_LOG_LIMIT ? next.slice(next.length - ASSIST_LOG_LIMIT) : next
}

/**
 * 【反映】変更案を**いまの作図に追加**して、記録を残す（作り直しはしない）。
 *
 * **利用者は押さない**（指示を送れば AI の案はそのまま作図へ入る。利用者の指示）。
 * 呼ぶのは「AI が案を返した直後」だけ。押させない代わりに、適用したあとは
 * そのステップの【戻す】で 1 手ずつ戻せるようにしてある。
 *
 * `step.mode` は見ない（古い履歴の `replace` でも**追加**で反映する）。作図を消したいときは、
 * 利用者が【全消去】してから指示する。
 */
async function applyAssistStep(step: AssistStep): Promise<void> {
  if (step.status !== 'pending') return
  // アプレットの準備前に AI の結果が返ることがある（読み込み直後の指示）。
  // 準備できるまで少し待つ（押させないので、ここで待たないと案が捨てられる）。
  if (!await waitAppletReadyForApply(ASSIST_APPLET_WAIT_MS)) {
    await failAssistStep(
      step,
      'GeoGebra の準備ができていないため反映できませんでした。少し待ってから、もう一度【送信】してください。'
    )
    return
  }
  const failure = applyCommands(step.commands, 'append')
  if (failure !== null) {
    // applyCommands が元の作図に戻して理由を出している（失敗として残す）
    await failAssistStep(step, failure.reason)
    // 実行できなかった中身が分かるときは、**AI に 1 回だけ直させる**
    // （利用者が言い直さなくて済む。同じ失敗で無限に呼ばないよう印をつける）
    await repairAssistStep(step, failure)
    return
  }
  toast.success('変更案を作図に追加しました。')
  // 作図が変わったのでサムネイルを自動で取り直す（案内は出さない）
  scheduleThumbnailRefresh()
  step.status = 'applied'
  // `snapshot` は**この指示を出す前の作図**のまま残す（【戻す】＝この指示を出す前へ戻す。
  // 反映が自動になったので、押したときに本当に戻る＝取り消せる必要がある）
  saveAssistLog()
  if (step.assistId !== null) {
    try {
      // 記録も「追加」で残す（古いステップでも、実際の反映は追加なので）
      await markGeometryAiAssistApplied(step.assistId, ASSIST_APPLY_MODE)
    } catch (caught) {
      // 記録に失敗しても作図は変わっている（記録は追跡用なので理由だけ出す）
      assistNotice.value = messageOf(caught, '適用の記録を残せませんでした。')
    }
  }
}

/**
 * 案を**作図に反映できなかった**ときの後始末（作図は元のまま）。
 *
 * 失敗の理由をそのステップに残し、DB にも「この案は使わなかった」と**理由つきで**記録する
 * （依頼を作ったまま放置すると、いつまでも実行中のままに見える。理由を残すと別の端末の
 * 履歴でも「どの行で失敗したか」が分かる）。
 * 記録に失敗しても**画面は止めない**（記録は補助）。
 */
async function failAssistStep(step: AssistStep, reason: string): Promise<void> {
  step.status = 'failed'
  step.error = reason
  saveAssistLog()
  if (step.assistId === null) return
  try {
    await discardGeometryAiAssist(step.assistId, reason)
  } catch {
    // 記録できなくても画面は普通に使える
  }
}

/**
 * 実行できなかった案を、AI に**1 回だけ**直させる。
 *
 * <p>実測: 「再画一个正三角形，三个顶点，落在这个圆上。」に対して AI が
 * `Polygon(A, B, C, 3)` を返し、GeoGebra が引数の数の間違いで拒否した。従来はそこで終わり
 * （AI は自分の失敗を知らないまま）、利用者が言い直すしかなかった。</p>
 *
 * <p>失敗したときは作図が**この指示を出す前に戻っている**ので、同じ指示＋「実行できなかった行」
 * ＋「いまの作図の一覧」を付けて、もう一度だけ依頼する。修正案は**新しいステップ**として
 * 会話ログに積む（元の失敗も残るので、何が起きたか分かる）。</p>
 */
async function repairAssistStep(step: AssistStep, failure: ApplyFailure): Promise<void> {
  if (step.repairTried || failure.failedLines.length === 0) return
  if (!assistAvailable.value) return
  step.repairTried = true
  saveAssistLog()
  const snapshot = exportConstruction()
  const kept = keepSnapshot(snapshot)
  try {
    const created = await createGeometryAiAssist({
      figureId: editingId.value,
      construction: snapshot,
      instruction: step.instruction,
      // 自動修正も**追加**（古いステップが `replace` でも作り直さない）
      mode: ASSIST_APPLY_MODE,
      objects: objectsSummaryOf(),
      failure: failureDetailOf(failure)
    })
    const request = created.data
    const repair: AssistStep = {
      id: ++assistStepSeq,
      instruction: step.instruction,
      mode: ASSIST_APPLY_MODE,
      commands: [],
      description: null,
      assistId: request.assistId,
      status: 'running',
      error: null,
      snapshot: kept.snapshot,
      superseded: false,
      snapshotOmitted: kept.snapshotOmitted,
      beforeXmlAvailable: snapshot !== '' && snapshot.length <= ASSIST_SERVER_SNAPSHOT_MAX,
      expanded: false,
      repairTried: true,
      repairedFrom: step.id
    }
    assistLog.value = pushAssistStep(repair)
    assistNotice.value = 'AI の案が実行できなかったので、AI に修正を依頼しました（下の「AI が修正」）。'
    saveAssistLog()
    void runAssistOnce(request.assistId, request.runPath)
    startAssistPolling(repair)
  } catch (caught) {
    // 依頼を作れなくても、失敗した理由はもう画面に出ている（画面は止めない）
    assistNotice.value = messageOf(caught, 'AI による自動修正を依頼できませんでした。')
  }
}

/**
 * 【戻す】の戻り先（その指示を出す前の作図）を取る。
 *
 * この端末のスナップショットを先に見て、無ければサーバーの `指示前XML` を 1 件だけ取る
 * （別の端末で出した指示でも戻せるように）。どちらも無ければ null。
 */
async function restoreTargetOf(step: AssistStep): Promise<string | null> {
  if (step.snapshot !== null) return step.snapshot
  if (step.assistId === null || !step.beforeXmlAvailable) return null
  try {
    const response = await fetchGeometryAiAssist(step.assistId)
    return response.data.beforeXml ?? null
  } catch {
    return null
  }
}

/**
 * 【戻す】: **その指示を出す前の作図に戻す**（＝その指示と、そのあとの変更を取り消す）。
 *
 * 反映は自動（利用者の指示）なので、ここへ来る時点で未反映の案は残っていない
 * （案は返った直後にそのまま反映されるか、失敗として記録される）。
 * 反映済みのステップだけに出し、**失敗したステップには出さない**（作図を変えていないため）。
 * 作図を戻すだけなので、DB の記録（適用済み）はそのまま残す。
 */
async function restoreStep(step: AssistStep): Promise<void> {
  const snapshot = await restoreTargetOf(step)
  if (snapshot === null) {
    assistNotice.value = 'この指示を出す前の作図を読み込めませんでした。'
      + 'ページを再読み込みして、もう一度お試しください。'
    return
  }
  // 取ってきた作図はこの端末にも覚えておく（次に押すときはサーバーへ行かない）
  step.snapshot = snapshot
  restoreBackup.value = exportConstruction()
  applyConstruction(snapshot)
  // 戻した時点より後ろの変更は作図から消えているので、辿れるように印を付ける
  for (const later of assistLog.value) {
    if (later.id > step.id) later.superseded = true
  }
  assistNotice.value = `${step.id} 番目の指示を出す前の作図に戻しました。`
  saveAssistLog()
  scheduleThumbnailRefresh()
}

/** 【戻す前の作図に戻す】: 直前に戻す操作を 1 手だけ取り消す。 */
function undoRestore(): void {
  const backup = restoreBackup.value
  if (backup === null) return
  applyConstruction(backup)
  restoreBackup.value = null
  assistNotice.value = '戻す前の作図に戻しました。'
  scheduleThumbnailRefresh()
}

onMounted(async () => {
  void loadSuggestions()
  window.addEventListener('resize', onWindowResize)
  // 画面の大きさが変わったら、助手の窓もはみ出さない位置と大きさへ寄せ直す
  window.addEventListener('resize', onAssistWindowResize)
  void loadAssistOptions()
  // AI 生図は**アプレットを作る前に**読む（作図タイプで appName を決めるため）
  const aiId = queryAiRequestId()
  if (aiId !== null) {
    await loadAiRequest(aiId)
    // コマンドは applet の準備後に流し込む（appletOnLoad で実行する）
    pendingAiCommands = aiRequest.value !== null && aiRequest.value.commands.length > 0
      ? [...aiRequest.value.commands] : null
  }
  const starting = startApplet()
  // ?copyFrom= は「コピーして開く」。?geometryId= は編集。両方あるときは copyFrom を優先する
  const copyFrom = queryFigureId('copyFrom')
  const target = copyFrom ?? queryFigureId('geometryId')
  if (target !== null) await loadFigure(target, copyFrom === null ? 'edit' : 'copy')
  loading.value = false
  window.addEventListener('resize', measureFrame)
  measureFrame()
  observeFrameSize()
  await starting
  measureFrame()
})

onBeforeUnmount(() => {
  appletDisposed = true
  frameObserver?.disconnect()
  frameObserver = null
  // 画面を離れたらポーリングを止める（結果は localStorage に残るので、戻ってきたら取り直す）
  stopAssistPolling()
  endAssistPointer()
  window.removeEventListener('resize', measureFrame)
  window.removeEventListener('resize', onWindowResize)
  window.removeEventListener('resize', onAssistWindowResize)
  if (thumbnailTimer !== null) window.clearTimeout(thumbnailTimer)
  if (commandSyncTimer !== null) window.clearTimeout(commandSyncTimer)
  if (repaintResumeTimer !== null) window.clearTimeout(repaintResumeTimer)
  cancelRepaintCycles()
})

/** 幅が変わるとタグ候補の 2 行に収まる枚数も変わるので、測り直す。 */
function onWindowResize(): void {
  void measureSuggestionRows()
}
</script>

<template>
  <div
    class="gm-page gm-draw-page"
    :data-gm-draw-collapsed="collapsed ? 'true' : 'false'"
    :style="frameHeight === null ? undefined : { '--gm-draw-height': `${frameHeight}px` }"
  >
    <section class="card">
      <div class="card__header gm-list-head">
        <h2 class="card__title">
          <AppIcon :name="pageIcon" size="sm" /> <span data-gm-draw-title-text>{{ pageTitle }}</span>
        </h2>
        <span class="gm-card__meta" data-gm-draw-state>{{ stateLabel }}</span>
        <div class="gm-list-head__spacer"></div>

        <!-- 作図の操作は【一覧へ戻る】と同じ行にまとめる（利用者の指示） -->
        <div class="search-panel__actions gm-draw__actions">
          <button
            type="button" class="btn btn--secondary btn--sm" data-gm-draw-collapse
            :title="collapsed ? '入力欄を表示' : '入力欄を隠す'"
            :aria-label="collapsed ? '入力欄を表示' : '入力欄を隠す'"
            :aria-expanded="collapsed ? 'false' : 'true'"
            @click="toggleCollapsed"
          >
            <AppIcon :name="collapsed ? 'chevron-right' : 'chevron-left'" size="sm" />
            {{ collapsed ? '入力欄を表示' : '入力欄を隠す' }}
          </button>
          <button type="button" class="btn btn--secondary btn--sm" data-gm-draw-center @click="centerView">
            <AppIcon name="zoom-in" size="sm" /> 中央表示
          </button>
          <button type="button" class="btn btn--secondary btn--sm" data-gm-draw-clear @click="clearConstruction()">
            <AppIcon name="eraser" size="sm" /> 全消去
          </button>
          <!-- 表示の切替（座標軸・グリッド）。作図の見え方を変えるだけなので、作図データは変えない -->
          <label class="gm-draw__toggle" title="座標軸（x 軸・y 軸）を表示する">
            <input v-model="axesVisible" type="checkbox" data-gm-draw-axes>
            <span>座標軸</span>
          </label>
          <label class="gm-draw__toggle" title="グリッド（方眼）を表示する">
            <input v-model="gridVisible" type="checkbox" data-gm-draw-grid>
            <span>グリッド</span>
          </label>
          <!-- 作図できるかは**文言ではなく属性**で知らせる（利用者の指示）。
               見出しの行ではなく `[data-gm-draw-frame]` の `data-gm-draw-ready` を見る。
               読み込めなかったときだけ作図エリア（`data-gm-draw-status`）に理由を出す。
               【名前を付けて保存】は画面から外した（`save(true)` は残してあるが UI は無い。
               新しい図形を作るのは一覧の【コピー】（`?copyFrom=`）と【新規】だけ）。 -->
          <!-- AI 画図助手（窓を開く）。保存と同じ行に置く（利用者の指示） -->
          <button
            type="button" class="btn btn--secondary btn--sm" data-gm-ai-assist-toggle
            :aria-expanded="assistDialogOpen ? 'true' : 'false'"
            :title="assistDialogOpen ? 'AI 画図助手を閉じる' : 'AI 画図助手を開く（指示から作図を直す）'"
            aria-label="AI 画図助手" @click="toggleAiAssist"
          >
            <AppIcon name="wand" size="sm" /> AI 助手
          </button>
          <button type="button" class="btn btn--primary btn--sm" :disabled="saving" data-gm-draw-save @click="save(false)">
            <AppIcon name="check" size="sm" /> {{ saving ? '保存中...' : '保存' }}
          </button>
          <button type="button" class="btn btn--secondary btn--sm" data-gm-draw-back @click="backToList">
            <AppIcon name="chevron-left" size="sm" /> 一覧へ戻る
          </button>
        </div>
      </div>

      <p v-if="loadError" class="alert alert--danger" data-gm-draw-load-error>
        {{ loadError }} 新しい図形として作成できます。
      </p>

      <!-- AI 生図から開いたときの案内（どの要求の作図か・保存すると何になるか） -->
      <p v-if="aiSourceLabel !== ''" class="gm-ai-notice gm-ai-notice--inline" data-gm-draw-ai-notice>
        <AppIcon name="wand" size="sm" />
        <span>
          {{ aiSourceLabel }}
          <template v-if="aiRequest !== null && aiRequest.status === 'READY'">
            この作図は<strong>まだ保存されていません</strong>。【保存】を押すと
            <strong>AI 生図の図形</strong>として登録され、保存完了になります。
          </template>
          <template v-else-if="aiRequest !== null && aiRequest.status === 'REGISTERED'">
            この作図は既に図形として保存されています（保存すると通常の更新になります）。
          </template>
        </span>
      </p>

      <!-- 実行できなかったコマンドの案内（**この状態では保存させない**） -->
      <p
        v-if="aiCommandFailure !== null" class="alert alert--danger" data-gm-draw-ai-failure
      >
        {{ aiCommandFailure }} 作図は元の状態に戻してあります。この結果はまだ AI 生図として
        保存できません。もう一度生成するか、コマンド欄から手で直してください。
      </p>
      <p v-if="aiNotice !== ''" class="gm-hint" data-gm-draw-ai-message>{{ aiNotice }}</p>

      <!-- コピーして開いたときの案内（状態表示と同じ内容） -->
      <p v-if="copySourceLabel !== ''" class="gm-hint gm-draw__source" data-gm-draw-source>
        {{ copySourceLabel }}
      </p>

      <div class="gm-draw">
        <!-- 左側：図形の情報とコマンド入力（折りたためる） -->
        <div v-show="!collapsed" ref="sidePanel" class="gm-draw__side" data-gm-draw-side>
          <div class="gm-form">
            <div class="gm-form__row">
              <label class="field__label" for="gm-draw-title">図形名<span class="gm-required">必須</span></label>
              <input
                id="gm-draw-title" v-model="form.title" class="input" type="text" :maxlength="TITLE_MAX"
                placeholder="例: 三角形の垂線" data-gm-draw-title
              >
            </div>

            <div class="gm-form__row">
              <span id="gm-draw-type-label" class="field__label">作図タイプ</span>
              <!-- 選択肢は 2 つだけなので、select ではなくラジオで出す（利用者の指示） -->
              <div
                class="gm-draw__types" role="radiogroup" aria-labelledby="gm-draw-type-label"
                data-gm-draw-type
              >
                <label v-for="option in FIGURE_TYPE_OPTIONS" :key="option" class="gm-draw__radio">
                  <input
                    v-model="form.figureType" type="radio" name="gm-draw-type" :value="option"
                    :data-gm-draw-type-option="option" @change="changeFigureType"
                  >
                  <span>{{ FIGURE_TYPE_LABELS[option] }}</span>
                </label>
              </div>
            </div>

            <div class="gm-form__row">
              <label class="field__label" for="gm-draw-memo">メモ</label>
              <textarea
                id="gm-draw-memo" v-model="form.memo" class="input" rows="3" :maxlength="MEMO_MAX"
                placeholder="作図のポイント" data-gm-draw-memo
              ></textarea>
            </div>

            <!-- タグ -->
            <div class="gm-tag-input">
              <span class="field__label">タグ</span>
              <div class="gm-tag-input__list" data-gm-draw-tag-list>
                <template v-if="tags.length > 0">
                  <span v-for="tag in tags" :key="tag" class="gm-tag-input__chip" :data-gm-draw-tag="tag">
                    {{ tag }}
                    <button
                      type="button" class="gm-tag-input__remove" :aria-label="`${tag} を外す`"
                      :data-gm-draw-tag-remove="tag" @click="removeTag(tag)"
                    >×</button>
                  </span>
                </template>
              </div>
              <div class="gm-form__row gm-tag-input__row">
                <input
                  v-model="tagInput" class="input" type="text" :maxlength="TAG_MAX_LENGTH"
                  placeholder="タグを入力して Enter" data-gm-draw-tag-input @keyup.enter="addTag"
                >
                <button type="button" class="btn btn--secondary btn--sm" data-gm-draw-tag-add @click="addTag">
                  <AppIcon name="plus" size="sm" /> 追加
                </button>
              </div>
              <!-- 候補は**既定で 2 行**だけ出す（件数の多い順のまま）。残りは「もっと見る」で開く -->
              <div ref="suggestionsEl" class="gm-tag-suggestions" data-gm-draw-tag-suggestions>
                <template v-if="tagSuggestions.length > 0">
                  <button
                    v-for="suggestion in visibleTagSuggestions" :key="suggestion.tag" type="button"
                    class="gm-tag-suggestion" :data-gm-draw-tag-suggestion="suggestion.tag"
                    @click="toggleSuggestion(suggestion.tag)"
                  >
                    {{ suggestion.tag }}（{{ suggestion.count }}）
                  </button>
                  <button
                    v-if="tagExpanded || hiddenTagCount > 0" type="button" class="gm-tag-more" data-gm-tag-more
                    :aria-expanded="tagExpanded ? 'true' : 'false'" @click="toggleTagSuggestions"
                  >
                    {{ tagExpanded ? '閉じる' : `もっと見る（残り ${hiddenTagCount} 件）` }}
                  </button>
                </template>
                <span v-else class="gm-hint">よく使うタグはまだありません。</span>
              </div>
            </div>

            <!-- コマンド入力（**いまの作図そのもの**を出し、実行もできる） -->
            <div class="gm-command">
              <!-- 見出しと操作を 1 行にまとめる（縦を詰める。利用者の指示） -->
              <div class="gm-command__head">
                <label class="field__label" for="gm-draw-command">GeoGebra コマンド</label>
                <div class="gm-command__actions">
                  <button
                    type="button" class="btn btn--icon btn--sm is-primary"
                    title="コマンドを実行" aria-label="コマンドを実行"
                    data-gm-draw-command-run @click="runCommands"
                  >
                    <AppIcon name="play" size="sm" />
                  </button>
                  <button
                    type="button" class="btn btn--icon btn--sm"
                    title="コマンド欄をクリア" aria-label="コマンド欄をクリア"
                    data-gm-draw-command-clear @click="clearCommandInput"
                  >
                    <AppIcon name="eraser" size="sm" />
                  </button>
                </div>
              </div>
              <textarea
                id="gm-draw-command" v-model="commands" class="input" rows="4" spellcheck="false"
                placeholder="A = (0, 4)&#10;B = (3, 0)&#10;Segment(A, B)" data-gm-draw-command
                @input="markCommandsEdited"
              ></textarea>
              <!-- いまの作図の状態（編集中は上書きしないので、いつでもここで分かる） -->
              <p
                class="gm-command__state" :class="{ 'is-editing': commandsEdited }"
                data-gm-draw-command-state
              >
                <template v-if="commandsEdited">
                  <AppIcon name="edit" size="sm" />
                  <span data-gm-draw-command-editing>
                    編集中：この内容はまだ作図に反映していません（現在の作図は {{ canvasCommands.length }} コマンド）。
                  </span>
                  <button
                    type="button" class="gm-command__reset" data-gm-draw-command-reset
                    @click="resetCommandsToCanvas"
                  >
                    作図の内容に戻す
                  </button>
                </template>
                <template v-else>
                  <AppIcon name="check" size="sm" />
                  <span data-gm-draw-command-synced>
                    現在の作図：{{ canvasCommands.length }} コマンド（作図に合わせて自動で更新します）
                  </span>
                </template>
              </p>
              <!-- コマンドにできない作図（黙って落とさずに知らせる） -->
              <p v-if="commandLimitNote !== ''" class="gm-command__limit" data-gm-draw-command-limit>
                <AppIcon name="alert" size="sm" />
                <span>{{ commandLimitNote }}</span>
              </p>
            </div>

            <!-- AI 画図助手はヘッダーの【AI 助手】から開くダイアログに移した（左パネルを縦に伸ばさない） -->
          </div>

          <!-- サムネイル（左欄と同じ幅。**作図を変えると自動で更新する**ので更新ボタンは置かない） -->
          <div class="gm-form__row">
            <span class="field__label">サムネイル</span>
            <div class="gm-card__thumb gm-draw__thumb" data-gm-draw-preview>
              <img v-if="preview !== ''" :src="preview" alt="作図のサムネイル" data-gm-draw-preview-img>
              <div v-else class="gm-card__thumb-empty" data-gm-draw-preview-empty>
                <AppIcon name="image" size="lg" />
                <span>作図すると、ここにサムネイルが出ます</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 右側：GeoGebra の作図エリア -->
        <div class="gm-stage">
          <!-- 高さはビューポートの残りいっぱい（measureFrame が入れる） -->
          <div
            ref="frame" class="gm-stage__frame" data-gm-draw-frame
            :data-gm-draw-ready="appletReady ? 'true' : 'false'"
            :style="frameHeight === null ? undefined : { height: `${frameHeight}px` }"
          >
            <div :id="MOUNT_ID" class="gm-stage__mount" data-gm-draw-applet></div>
            <div v-if="!appletReady" class="gm-stage__status" data-gm-draw-status>
              <AppIcon name="sigma" size="lg" />
              <span>{{ stageStatus }}</span>
            </div>
          </div>

          <p class="gm-hint gm-stage__hint">
            GeoGebra のツールバーで作図し、図形名とタグを付けて【保存】を押してください。
            作図データとサムネイルは図形一覧に保存され、家族で共有できます。
          </p>
        </div>
      </div>
    </section>

    <!-- AI 画図助手: **非モーダルの浮動ウィンドウ**（開いたまま作図できる。利用者の指示）。
         背景を覆わない（遮罩を置かず、背景クリックでも閉じない）。閉じるのは × だけ。
         タイトルバーをつまむと動き、右下の把手で大きさを変えられる。
         位置と大きさは localStorage に覚える（`study21.geometry.aiAssist.window`）。 -->
    <section
      v-if="assistDialogOpen" ref="assistWindow" class="gm-assist-window" role="region"
      aria-label="AI 画図助手" data-gm-ai-assist :style="assistWindowStyle"
    >
      <header class="gm-assist-window__head" data-gm-ai-assist-drag @pointerdown="startAssistDrag">
        <h2 class="dialog__title"><AppIcon name="wand" size="sm" /> AI 助手</h2>
        <span
          class="badge" :class="assistAvailable ? 'badge--success' : 'badge--neutral'"
          data-gm-ai-assist-badge
        >{{ assistAvailable ? '使えます' : '無効' }}</span>
        <div class="gm-assist-window__spacer"></div>
        <button type="button" class="dialog__close" aria-label="閉じる" data-gm-ai-assist-close @click="toggleAiAssist">
          <AppIcon name="x" size="sm" />
        </button>
      </header>

      <div class="gm-assist-window__body" data-gm-ai-assist-body>
        <!-- 指示の送信先（外部 AI へ送ることだけ 1 行で出す。詳しい説明は title に置く） -->
        <p
          class="gm-assist-window__note" data-gm-ai-assist-note
          title="指示と、いまの作図のオブジェクト名だけを外部の AI サービスへ送ります。作図データそのものは送りません。"
        >
          指示は外部 AI へ送信されます
        </p>

        <!-- 会話ログ（利用者の指示 ↔ AI の応答）。
             1 ステップ = 指示・コマンド・短い状態だけ。説明文は「詳細」を押したときだけ出す。 -->
        <ol v-if="assistLog.length > 0 || assistSending" class="gm-assist-log" data-gm-ai-assist-log>
          <li
            v-for="step in assistLog" :key="step.id" class="gm-assist-step"
            :class="[`is-${step.status}`, { 'is-superseded': step.superseded }]"
            :data-gm-ai-assist-step="step.id"
            :data-gm-ai-assist-sending="step.status === 'running' ? '' : null"
          >
            <p class="gm-assist-step__user" :data-gm-ai-assist-step-instruction="step.id">
              {{ step.instruction }}
              <!-- 実行できなかった案のあとに AI が直した案（利用者の指示ではなく AI の再提案） -->
              <span
                v-if="step.repairedFrom !== null" class="gm-assist-step__badge"
                :data-gm-ai-assist-step-repaired="step.id"
              >AI が修正</span>
            </p>
            <div class="gm-assist-step__ai">
              <p
                v-if="step.error !== null" class="gm-assist-step__error"
                :data-gm-ai-assist-step-error="step.id" :title="step.error"
              >
                {{ step.error }}
              </p>
              <template v-else>
                <div v-if="step.commands.length > 0" class="gm-assist-step__commands-wrap">
                  <pre
                    class="gm-assist-step__commands"
                    :data-gm-ai-assist-diff="step.id"
                  >{{ step.commands.join('\n') }}</pre>
                  <!-- コマンドをコピー（コマンドだけを改行区切りで。開いている間に何度でも） -->
                  <button
                    type="button" class="gm-assist-step__copy" :data-gm-ai-assist-copy="step.id"
                    title="コマンドをコピー" aria-label="コマンドをコピー" @click="copyCommands(step)"
                  >
                    <AppIcon name="copy" size="sm" />
                  </button>
                </div>
                <p
                  v-if="step.description !== null" class="gm-assist-step__description"
                  :class="{ 'is-clamped': !step.expanded }"
                >
                  {{ step.description }}
                </p>
                <button
                  v-if="hasLongDescription(step)" type="button" class="gm-assist-step__detail"
                  :data-gm-ai-assist-detail="step.id"
                  :aria-expanded="step.expanded ? 'true' : 'false'"
                  @click="toggleStepDetail(step)"
                >
                  詳細
                </button>
              </template>
              <div class="gm-assist-step__foot">
                <p class="gm-assist-step__status" :data-gm-ai-assist-step-status="step.id">
                  {{ assistStepLabel(step) }}
                </p>
                <!-- 【戻す】は**作図を変えたステップ**だけ。状態と同じ行の右端にアイコンで置く（利用者の指示） -->
                <button
                  v-if="canRestoreStep(step)" type="button" class="gm-assist-step__restore"
                  :data-gm-ai-assist-restore="step.id" title="この指示を出す前の作図に戻す（取り消し）"
                  aria-label="この指示を出す前の作図に戻す" @click="restoreStep(step)"
                >
                  <AppIcon name="rotate" size="sm" />
                </button>
              </div>
              <!-- 大きすぎて保存できなかった作図は戻せない（理由は title に置く） -->
              <p
                v-if="step.snapshotOmitted && !step.beforeXmlAvailable" class="gm-assist-step__status"
                :data-gm-ai-assist-step-no-snapshot="step.id"
                title="この指示を出す前には戻せません（作図が大きすぎるため保存していません）"
              >
                大きすぎるため戻せません
              </p>
            </div>
          </li>
        </ol>
        <p v-else class="gm-assist-log__empty" data-gm-ai-assist-log-empty></p>

        <p v-if="assistNotice !== ''" class="gm-assist-window__notice" data-gm-ai-assist-message>{{ assistNotice }}</p>
        <div v-if="restoreBackup !== null" class="gm-assist-window__notice">
          <button
            type="button" class="btn btn--secondary btn--sm" data-gm-ai-assist-unrestore @click="undoRestore"
          >
            戻す前の作図に戻す
          </button>
        </div>
      </div>

      <!-- 入力（窓の下に固定。本文だけがスクロールするので、送信はいつでも押せる） -->
      <div class="gm-assist-compose">
        <textarea
          id="gm-ai-assist-instruction" v-model="aiAssistInstruction" class="input" rows="2"
          :maxlength="ASSIST_INSTRUCTION_MAX" placeholder="指示を入力"
          data-gm-ai-assist-instruction @keydown.enter.exact.prevent="sendAssist"
        ></textarea>
        <div class="gm-assist-compose__row">
          <!-- 反映方法の選択は置かない（**いつも今の作図に追加**。作り直したいときは【全消去】） -->
          <button
            type="button" class="btn btn--primary btn--sm" :disabled="!canSendAssist"
            data-gm-ai-assist-send @click="sendAssist"
          >
            <AppIcon name="wand" size="sm" />
            {{ assistSending ? '考えています…' : '送信' }}
          </button>
        </div>
      </div>

      <!-- 大きさを変える把手（右下。つまんで動かすと窓の大きさが変わる） -->
      <div
        class="gm-assist-window__resize" data-gm-ai-assist-resize
        title="つまんで大きさを変える" @pointerdown="startAssistResize"
      ></div>
    </section>
  </div>
</template>
