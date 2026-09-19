import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { useToast } from '@study21/web-shared'
import GeometryDrawView from '@/views/geometry/GeometryDrawView.vue'
import type { GeometryFigure } from '@/api/geometry'

/**
 * 図形管理【作図画面】（2.0 の geometry_draw.jsp 相当）。
 *
 * GeoGebra のアプレットは本物を読み込めないので、`window.GGBApplet` を差し替えて
 * 「どんな設定でアプレットを作るか」「アプレットに何を指示したか」「API に何を送るか」を固定する。
 * 実データ（2.0 から移行した図形）が入っている前提の画面。
 *
 * ・`?geometryId=` は編集（保存は更新）、`?copyFrom=` はコピーして開く（保存すると新規作成）
 * ・左の入力欄は折りたためる（`data-gm-draw-collapsed`）
 * ・作図エリアはビューポートの残り高さいっぱい（`[data-gm-draw-frame]`）
 */
const Dummy = defineComponent({ name: 'Dummy', render: () => null })

const CONSTRUCTION = '<construction><element type="point"/></construction>'
/** コピー元の作図データ（読み込んだ XML がアプレットに渡ったことを見分けるため別の値にする）。 */
const COPIED_CONSTRUCTION = '<construction><element type="segment"/></construction>'
const THUMBNAIL = 'iVBORw0KGgoAAAANSUhEUg=='
const THUMBNAIL_URL = `data:image/png;base64,${THUMBNAIL}`

function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function failure(message: string, status = 404): Response {
  return new Response(
    JSON.stringify({ success: false, code: 'NOT_FOUND', message, data: null, timestamp: '' }),
    { status, headers: { 'Content-Type': 'application/json' } }
  )
}

/** 移行済みの幾何図形（サムネイルあり）。 */
function geometryFigure(overrides: Partial<GeometryFigure> = {}): GeometryFigure {
  return {
    figureId: 5,
    figureNo: 'GEO20260402-124745',
    subject: '数学',
    figureType: 'geometry',
    kind: 'saved',
    title: '三角形の重心',
    memo: '中線の交点を作図する。',
    tags: ['三角形', '重心'],
    displayOrder: 1,
    status: 'ACTIVE',
    hasThumbnail: true,
    constructionLength: CONSTRUCTION.length,
    version: 3,
    createdAt: '2026-04-02T12:47:45',
    updatedAt: '2026-09-12T23:02:00',
    ...overrides
  }
}

/** GeoGebra の API の代役（呼ばれた内容を記録する）。 */
class FakeApi {
  xml = CONSTRUCTION
  png: string | null = THUMBNAIL
  evaluated: string[] = []
  newConstructions = 0
  deleted: string[] = []
  objects: string[] = ['A', 'B']
  centered: [number, number][] = []
  sizes: [number, number][] = []
  refreshes = 0
  /** ここに入れたコマンドは失敗する（false を返す）。 */
  failCommands: string[] = []
  /** 座標軸の表示切替（引数を記録する）。 */
  axesCalls: [boolean, boolean][] = []
  /** グリッドの表示切替（引数を記録する）。 */
  gridCalls: boolean[] = []
  /** いま表示しているか（`getVisible` が返す）。 */
  visible: Record<string, boolean> = { xAxis: true, yAxis: true, grid: false }

  getXML(): string {
    // 実機と同じく、作図データには作図エリアの設定（evSettings）が入る。
    // 座標軸・グリッドの表示はここに現れる（getVisible では読めない）。
    const rendered = this.xml.replace('</construction>',
      `</construction><euclidianView><evSettings axes="${this.visible.xAxis}" grid="${this.visible.grid}"/></euclidianView>`)
    // 実機では XML が作図そのものなので、返した作図を控えておく（setXML でその状態へ戻す）
    this.renderedStates.set(rendered, {
      objects: [...this.objects],
      commandStrings: { ...this.commandStrings },
      algorithms: { ...this.algorithms },
      xml: this.xml
    })
    while (this.renderedStates.size > 20) {
      const oldest = this.renderedStates.keys().next().value
      if (oldest === undefined) break
      this.renderedStates.delete(oldest)
    }
    return rendered
  }

  setXML(xml: string): void {
    this.xml = xml
    // 巻き戻しを模す: XML に居ないオブジェクトは作図から消える（ラベルから作り直す）
    const labels = [...xml.matchAll(/label="([^"]+)"/g)].map((match) => match[1])
    if (labels.length > 0) {
      this.objects = [...new Set(labels)]
      return
    }
    // ラベルが無い XML は、その XML を返した時点の作図へ戻す（実機では XML が作図そのもの）
    const known = this.renderedStates.get(xml)
    if (known !== undefined) {
      this.objects = [...known.objects]
      this.commandStrings = { ...known.commandStrings }
      this.algorithms = { ...known.algorithms }
      this.xml = known.xml
      return
    }
    // 全消去の直前の作図（newConstruction を模すときに控えたもの）へ戻す
    if (this.beforeClear !== null) {
      this.objects = [...this.beforeClear.objects]
      this.commandStrings = { ...this.beforeClear.commandStrings }
      this.algorithms = { ...this.beforeClear.algorithms }
      this.xml = this.beforeClear.xml
      this.beforeClear = null
    }
  }

  /** `getXML()` が返した作図（巻き戻しの戻り先。XML ごとに控える）。 */
  private renderedStates = new Map<string, {
    objects: string[]
    commandStrings: Record<string, string>
    algorithms: Record<string, string>
    xml: string
  }>()

  getPNGBase64(): string | null {
    return this.png
  }

  evalCommand(command: string): boolean {
    if (this.failCommands.includes(command)) return false
    this.evaluated.push(command)
    // 実機と同じく「名前 = 式」はオブジェクトを作る（参照チェックのテストで要る）
    const definition = /^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/.exec(command)
    if (definition !== null) {
      if (!this.objects.includes(definition[1])) this.objects.push(definition[1])
      // 実機と同じく、作ったコマンドは `getCommandString` で読み戻せる
      this.commandStrings[definition[1]] = definition[2]
      return true
    }
    // ラベルを書かないコマンド（道具で描いたときと同じ）は、GeoGebra が名前を付ける
    const autoLabel = this.autoLabels[command]
    if (autoLabel !== undefined) {
      if (!this.objects.includes(autoLabel)) this.objects.push(autoLabel)
      this.commandStrings[autoLabel] = command
    }
    return true
  }

  /** ラベルを書かないコマンドに GeoGebra が付ける名前（実機と同じ振る舞いの代役）。 */
  autoLabels: Record<string, string> = {}

  /** 全消去の直前の作図（巻き戻しで戻すため）。 */
  private beforeClear: {
    objects: string[]
    commandStrings: Record<string, string>
    algorithms: Record<string, string>
    xml: string
  } | null = null

  newConstruction(): void {
    this.newConstructions += 1
    // 実機で確かめたこと: newConstruction は作図だけでなく**表示の設定も初期値に戻す**
    // （evSettings の axes / grid が両方 false になり、座標系の倍率も既定へ戻る）
    this.visible = { xAxis: false, yAxis: false, grid: false }
    // 作図は空になる（コマンド欄もそれに従う）
    this.beforeClear = {
      objects: [...this.objects],
      commandStrings: { ...this.commandStrings },
      algorithms: { ...this.algorithms },
      xml: this.xml
    }
    this.objects = []
    this.commandStrings = {}
    this.algorithms = {}
    this.xml = '<construction></construction>'
  }

  /** 消したオブジェクト（実機と同じく、作図の一覧からも消える）。 */
  deleteObject(name: string): void {
    this.deleted.push(name)
    this.objects = this.objects.filter((object) => object !== name)
    delete this.commandStrings[name]
    delete this.algorithms[name]
    // 実機と同じく、全部消えると作図データも空になる
    if (this.objects.length === 0) this.xml = '<construction></construction>'
  }

  getAllObjectNames(): string[] {
    return [...this.objects]
  }

  /**
   * そのオブジェクトを作ったコマンド（`getCommandString`）。
   *
   * 実機では**自由点をドラッグすると空になる**ので、テストでも空を設定できる
   * （コマンド欄が座標から作り直すことを確かめる）。
   */
  commandStrings: Record<string, string> = {}

  getCommandString(name: string): string {
    return this.commandStrings[name] ?? ''
  }

  /** コマンドと入力・出力の XML（`getAlgorithmXML`）。既定は空（実機の自由点と同じ）。 */
  algorithms: Record<string, string> = {}

  getAlgorithmXML(name: string): string {
    return this.algorithms[name] ?? ''
  }

  getSiblingObjectNames(name: string): string[] {
    return [name]
  }

  /** 自由なオブジェクト（ドラッグした自由点の座標を読むときに使う）。 */
  independentObjects: string[] = []

  isIndependent(name: string): boolean {
    return this.independentObjects.includes(name)
  }

  coords: Record<string, [number, number]> = {}

  getXcoord(name: string): number {
    const point = this.coords[name]
    if (point === undefined) throw new Error('not a point')
    return point[0]
  }

  getYcoord(name: string): number {
    const point = this.coords[name]
    if (point === undefined) throw new Error('not a point')
    return point[1]
  }

  /** 作図にあるか（AI の案の参照チェックに使う）。 */
  exists(name: string): boolean {
    return this.objects.includes(name)
  }

  /** 種類（`getObjectType`）。既定は点（無い名前は空文字＝実機と同じ）。 */
  objectTypes: Record<string, string> = {}

  getObjectType(name: string): string {
    return this.objectTypes[name] ?? (this.objects.includes(name) ? 'point' : '')
  }

  /** 定義（`getDefinitionString`）。AI へ渡す一覧に使う。 */
  definitions: Record<string, string> = {}

  getDefinitionString(name: string): string {
    return this.definitions[name] ?? ''
  }

  getValueString(name: string): string {
    return this.definitions[name] ?? ''
  }

  CenterView(x: number, y: number): void {
    this.centered.push([x, y])
  }

  setAxesVisible(xAxis: boolean, yAxis: boolean): void {
    this.axesCalls.push([xAxis, yAxis])
    this.visible.xAxis = xAxis
    this.visible.yAxis = yAxis
  }

  setGridVisible(visible: boolean): void {
    this.gridCalls.push(visible)
    this.visible.grid = visible
  }

  getVisible(name: string): boolean {
    return this.visible[name] ?? false
  }

  /** 大きさの指示を無視する回数（テスト用: 1 回目が効かない状況を作る）。 */
  ignoreSizes = 0
  /** 大きさが効いたときに呼ばれる（テストが描画領域の代役を動かすため）。 */
  onSize: ((width: number, height: number) => void) | null = null
  /** 再描画の停止／再開が呼ばれた回数（テスト用）。 */
  repaintToggles = 0

  setSize(width: number, height: number): void {
    this.sizes.push([width, height])
    if (this.ignoreSizes > 0) {
      this.ignoreSizes -= 1
      return
    }
    this.onSize?.(width, height)
  }

  setWidth(width: number): void {
    if (this.ignoreSizes > 0) return
    this.onSize?.(width, 0)
  }

  setRepaintingActive(active: boolean): void {
    this.repaintToggles += 1
    this.repaintStates.push(active)
  }

  /** 再描画の停止／再開の順番（最後が true なら止まったままになっていない）。 */
  repaintStates: boolean[] = []

  /**
   * `getViewProperties()`（GeoGebra 内部のビューの大きさ）。
   * 画面はこれで「内部ビューが古い幅のまま（＝右側が白い）」を見つける。
   * 0 のときは「読めない」として扱われる（テストの既定）。
   */
  viewWidth = 0

  getViewProperties(): { width: number; height: number; left: number; top: number } {
    return { width: this.viewWidth, height: 1034, left: 293, top: 1 }
  }

  setHeight(): void {
    /* 高さはこのテストでは見ない */
  }

  refreshViews(): void {
    this.refreshes += 1
  }

  /* 画面はサムネイルの自動更新のために更新通知を購読する（テストから発火させる） */
  private updateListeners: (() => void)[] = []

  registerUpdateListener(listener: () => void): void {
    this.updateListeners.push(listener)
  }

  registerClientListener(listener: (event: { type?: string }) => void): void {
    this.updateListeners.push(() => listener({ type: 'update' }))
  }

  /** 作図が変わったことにする（点を動かした・コマンドを実行した等）。 */
  notifyUpdate(): void {
    for (const listener of this.updateListeners) listener()
  }
}

/** GGBApplet の代役（inject されたら appletOnLoad を呼ぶ）。 */
class FakeApplet {
  static instances: FakeApplet[] = []
  /** アプレットの準備（`appletOnLoad`）を遅らせるか（AI の結果が先に返る状況を作る）。 */
  static holdOnLoad = false
  params: Record<string, unknown>
  injectedId = ''
  /** 準備できたときに呼ぶもの（`holdOnLoad` のときは後から呼ぶ）。 */
  private onLoad: ((api: unknown) => void) | null = null

  constructor(params: Record<string, unknown>) {
    this.params = params
    FakeApplet.instances.push(this)
  }

  /** 遅らせていた準備を進める（アプレットが使えるようになる）。 */
  releaseOnLoad(): void {
    const onLoad = this.onLoad
    this.onLoad = null
    onLoad?.(api)
  }

  inject(elementId: string): void {
    this.injectedId = elementId
    // 本物と同じく、マウントの中にアプレット自身の要素を作る（作図画面はこの要素の大きさも合わせる）
    const mount = document.getElementById(elementId)
    if (mount !== null) {
      const element = document.createElement('article')
      element.style.width = `${String(this.params.width)}px`
      element.style.height = `${String(this.params.height)}px`
      mount.appendChild(element)
    }
    const onLoad = this.params.appletOnLoad
    if (typeof onLoad !== 'function') return
    this.onLoad = onLoad as (api: unknown) => void
    if (!FakeApplet.holdOnLoad) this.releaseOnLoad()
  }
}

let api = new FakeApi()

function router(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/:area/geometry', component: Dummy },
      { path: '/:area/geometry-draw', component: Dummy }
    ]
  })
}

type Call = { url: string; method: string; body: Record<string, unknown> | null }

function recorded(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return fetchMock.mock.calls.map((call) => {
    const init = (call[1] ?? {}) as RequestInit
    return {
      url: String(call[0]),
      method: (init.method ?? 'GET').toUpperCase(),
      body: init.body ? (JSON.parse(String(init.body)) as Record<string, unknown>) : null
    }
  })
}

function toastMessages(): string[] {
  return useToast().items.map((item) => item.message)
}

describe('図形管理【作図画面】', () => {
  let wrapper: VueWrapper | null = null

  interface SetupOptions {
    figureId?: number
    /** コピー元（`?copyFrom=`）。指定すると「コピーして開く」で開く。 */
    copyFromId?: number
    /** AI 生図の要求 ID（`?geometryAiRequestId=`）。作図画面で AI の結果を開く。 */
    aiRequestId?: number
    /** AI 生図の要求の詳細（既定は READY・三角形のコマンド 3 件）。 */
    aiRequest?: Record<string, unknown>
    /** タグ候補（既定は 2 件）。 */
    suggestions?: { tag: string; count: number }[]
    detail?: Response
    handlers?: (url: string, method: string, body: Record<string, unknown> | null) => Response | null
  }

  /** AI 生図の要求（READY。作図画面が読み込む形）。 */
  function aiRequestDetail(overrides: Record<string, unknown> = {}): Record<string, unknown> {
    return {
      requestId: 31,
      requestNo: 'AIG202609141200001234',
      status: 'READY',
      statusLabel: '生成済み・確認待ち',
      userKind: 'FIGURE',
      userSubKind: 'TRIANGLE',
      aiKind: 'FIGURE',
      figureType: 'geometry',
      note: '垂線も入れてください',
      commands: ['A = (0, 0)', 'B = (5, 0)', 'Polygon(A, B, C)'],
      proposal: { title: '三角形ABC', tags: ['三角形'], memo: 'AI 生図から', recognized: 'AB=5' },
      error: null,
      validationError: null,
      executionIds: { preprocess: 1, ai: 2, validate: 3 },
      figureId: null,
      retryCount: 0,
      version: 4,
      hasOriginalImage: true,
      hasCroppedImage: true,
      retryable: false,
      cancellable: false,
      createdAt: '2026-09-14T12:00:00',
      updatedAt: '2026-09-14T12:01:00',
      ...overrides
    }
  }

  /** AI 生図の設定（既定は有効・助手も有効）。 */
  function aiOptions(overrides: Record<string, unknown> = {}): Record<string, unknown> {
    return {
      enabled: true,
      assistEnabled: true,
      maxImageMb: 10,
      maxImagePixels: 1536,
      defaultCrop: 'manual',
      defaultKind: 'figure',
      approval: 'manual',
      dailyLimit: 20,
      usedToday: 0,
      notice: '',
      ...overrides
    }
  }

  async function setup(options: SetupOptions = {}) {
    const suggestions = options.suggestions ?? [{ tag: '三角形', count: 12 }, { tag: '重心', count: 4 }]
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const target = String(url)
      const method = (init?.method ?? 'GET').toUpperCase()
      const body = init?.body ? (JSON.parse(String(init.body)) as Record<string, unknown>) : null
      const handled = options.handlers?.(target, method, body)
      if (handled) return handled
      if (method === 'GET' && target.endsWith('/geometry/ai/options')) {
        return ok(aiOptions())
      }
      if (method === 'GET' && /\/geometry\/ai\/requests\/\d+$/.test(target)) {
        return ok(options.aiRequest ?? aiRequestDetail())
      }
      if (method === 'POST' && /\/geometry\/ai\/requests\/\d+\/confirm$/.test(target)) {
        return ok({
          request: { requestId: 31, status: 'REGISTERED', statusLabel: '図形として保存済' },
          figure: { figureId: 9, figureNo: 'GEO20260914130000000', title: String(body?.title ?? '') },
          message: 'AI 生図から図形を保存しました。（GEO20260914130000000）'
        })
      }
      // 助手は「依頼作成 → run（admin-api）→ 状態のポーリング」の 3 段階
      if (method === 'POST' && target.endsWith('/geometry/ai/assist')) {
        return ok({
          assistId: 12,
          status: 'PENDING',
          statusLabel: '依頼受付（AI 実行待ち）',
          runPath: '/api/admin/batch/geometry-assist/12/run'
        })
      }
      if (method === 'POST' && target === '/api/admin/batch/geometry-assist/12/run') {
        return ok({ assistId: 12, status: 'READY', step: { taskCode: 'batC52' } })
      }
      // 図形ごとの履歴（端末をまたぐ）。既定は「まだ履歴なし」
      if (method === 'GET' && target.includes('/geometry/ai/assist?')) {
        return ok({ items: [], count: 0, limit: 20 })
      }
      if (method === 'GET' && /\/geometry\/ai\/assist\/12$/.test(target)) {
        return ok({
          assistId: 12,
          status: 'READY',
          statusLabel: 'できました',
          commands: ['D = (0, 0)', 'Segment(C, D)'],
          description: '垂線を引きます。',
          errorCode: null,
          errorMessage: null,
          commandCount: 2
        })
      }
      if (method === 'POST' && /\/geometry\/ai\/assist\/\d+\/(applied|discarded)$/.test(target)) {
        return ok({ assistId: 12, commands: [], description: null, callId: 555 })
      }
      if (method === 'GET' && target.startsWith('/api/user/geometry/figures/')) {
        return options.detail ?? ok({ figure: geometryFigure(), construction: CONSTRUCTION, thumbnail: THUMBNAIL })
      }
      if (method === 'GET' && target.startsWith('/api/user/geometry/tags')) {
        return ok({ items: suggestions })
      }
      if (method === 'PUT' && /\/figures\/\d+$/.test(target)) {
        return ok({
          figure: geometryFigure({ figureId: 5, version: 4, title: String(body?.title ?? ''), tags: [] }),
          message: '図形を更新しました。'
        })
      }
      if (method === 'POST' && target.endsWith('/figures')) {
        return ok({
          figure: geometryFigure({
            figureId: 9,
            figureNo: 'GEO20260913120000000',
            title: String(body?.title ?? ''),
            version: 1
          }),
          message: '図形を保存しました。'
        })
      }
      return ok({ count: 1, message: 'OK' })
    })

    vi.stubGlobal('fetch', fetchMock)
    const pinia = createPinia()
    setActivePinia(pinia)
    const appRouter = router()
    const query: Record<string, string> = {}
    if (options.figureId !== undefined) query.geometryId = String(options.figureId)
    if (options.copyFromId !== undefined) query.copyFrom = String(options.copyFromId)
    if (options.aiRequestId !== undefined) query.geometryAiRequestId = String(options.aiRequestId)
    await appRouter.push({ path: '/student/geometry-draw', query })
    await appRouter.isReady()

    const view = mount(GeometryDrawView, {
      attachTo: document.body,
      global: { plugins: [pinia, appRouter] }
    })
    wrapper = view
    await flushPromises()
    return { wrapper: view, fetchMock, appRouter }
  }

  /** 最後に作られたアプレット。 */
  function applet(): FakeApplet {
    const last = FakeApplet.instances[FakeApplet.instances.length - 1]
    if (last === undefined) throw new Error('アプレットが作られていません。')
    return last
  }

  function params(): Record<string, unknown> {
    return applet().params
  }

  /** v-model は input イベントで更新されるので、値を入れて発火させる。 */
  /**
   * ダイアログ（`Teleport to="body"`）の中は wrapper からは見えないので、
   * document から引く（既存の小窓のテストと同じ作法）。
   */
  function dialog(selector: string): HTMLElement {
    const element = document.querySelector<HTMLElement>(selector)
    if (element === null) throw new Error(`ダイアログの要素が見つかりません: ${selector}`)
    return element
  }

  async function dialogType(selector: string, value: string): Promise<void> {
    const element = dialog(selector) as HTMLInputElement | HTMLTextAreaElement
    element.value = value
    element.dispatchEvent(new Event('input', { bubbles: true }))
    await flushPromises()
  }

  /**
   * 助手の往復（依頼作成 → run → ポーリング 1 回目）が終わるまで待つ。
   * 画面は結果を待ち続けないので、テスト側で数回フラッシュする。
   */
  async function settleAssist(): Promise<void> {
    for (let index = 0; index < 8; index += 1) await flushPromises()
  }

  async function dialogClick(selector: string): Promise<void> {
    dialog(selector).click()
    await flushPromises()
  }

  /**
   * ポインタ操作（つまんで動かす）。jsdom に `PointerEvent` が無いので `MouseEvent` で代用する
   * （画面は `clientX` / `clientY` と `pointermove` / `pointerup` しか見ていない）。
   */
  function pointer(type: string, target: EventTarget, clientX = 0, clientY = 0): void {
    target.dispatchEvent(new MouseEvent(type, { clientX, clientY, bubbles: true }))
  }

  async function type(wrapper: VueWrapper, selector: string, value: string): Promise<void> {
    const element = wrapper.get(selector).element as HTMLInputElement | HTMLTextAreaElement
    element.value = value
    element.dispatchEvent(new Event('input'))
    await flushPromises()
  }

  beforeEach(() => {
    useToast().items.splice(0)
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    wrapper?.unmount()
    wrapper = null
    api = new FakeApi()
    FakeApplet.instances = []
    FakeApplet.holdOnLoad = false
    document.body.innerHTML = ''
    // 助手の窓の位置・大きさと会話ログは localStorage に残るので、テストごとに消す
    window.localStorage.clear()
    // 本物の GeoGebra は読み込まない（アプレットの代役を入れる）
    window.GGBApplet = FakeApplet as unknown as Window['GGBApplet']
    delete window.ggbApplet
  })

  it('新規作成では幾何図形のアプレットを作り、保存の準備をする', async () => {
    const { wrapper: view } = await setup()

    expect(FakeApplet.instances).toHaveLength(1)
    expect(applet().injectedId).toBe('geometryGgbElement')
    expect(params().appName).toBe('geometry')
    expect(params().showAlgebraInput).toBe(false)
    expect(params().language).toBe('ja')
    expect(params().showToolBar).toBe(true)

    // アプレットの準備ができると案内が消え、作図できる状態になる。
    // 「作図できます／準備中...」の**文言は出さない**（利用者の指示）。状態は属性で知らせる。
    expect(view.find('[data-gm-draw-status]').exists()).toBe(false)
    const frame = view.get('[data-gm-draw-frame]')
    expect(frame.attributes('data-gm-draw-ready')).toBe('true')
    expect(frame.text()).not.toContain('作図できます')
    expect(view.get('[data-gm-draw-state]').text()).toContain('新規作成')
  })

  it('作図タイプを関数グラフに変えると Graphing のアプレットで作り直す', async () => {
    const { wrapper: view } = await setup()
    const before = api.xml

    // 作図タイプはラジオボタン（関数グラフを選ぶ）
    await view.get('[data-gm-draw-type-option="function"]').setValue()
    await flushPromises()

    expect(FakeApplet.instances).toHaveLength(2)
    expect(params().appName).toBe('graphing')
    expect(params().showAlgebraInput).toBe(true)
    // 作り直しても今の作図は消えない（XML を引き継ぐ。実機同様 evSettings が付くので含むで見る）
    expect(api.xml).toContain('<element type="point"/>')
    expect(before).toContain('<element type="point"/>')
  })

  it('?geometryId= 付きで開くと図形を読み込んで編集できる', async () => {
    const { wrapper: view, fetchMock } = await setup({ figureId: 5 })

    expect(recorded(fetchMock).some((call) => call.url === '/api/user/geometry/figures/5')).toBe(true)
    expect((view.get('[data-gm-draw-title]').element as HTMLInputElement).value).toBe('三角形の重心')
    expect((view.get('[data-gm-draw-type-option="geometry"]').element as HTMLInputElement).checked).toBe(true)
    expect((view.get('[data-gm-draw-memo]').element as HTMLTextAreaElement).value).toBe('中線の交点を作図する。')
    // 読み込んだ作図データをアプレットへ渡している
    expect(api.xml).toBe(CONSTRUCTION)
    // タグ・図形番号・サムネイル
    expect(view.get('[data-gm-draw-tag-list]').text()).toContain('三角形')
    expect(view.get('[data-gm-draw-tag-list]').text()).toContain('重心')
    const state = view.get('[data-gm-draw-state]').text()
    expect(state).toContain('編集中')
    expect(state).toContain('GEO20260402-124745')
    expect(view.get('[data-gm-draw-preview-img]').attributes('src')).toBe(THUMBNAIL_URL)
  })

  it('?copyFrom= で開くとコピー元の内容を全部読み込む（複製はしない）', async () => {
    const { wrapper: view, fetchMock } = await setup({
      copyFromId: 5,
      detail: ok({ figure: geometryFigure(), construction: COPIED_CONSTRUCTION, thumbnail: THUMBNAIL })
    })

    // コピー元は GET で読むだけ（複製の API は呼ばない）
    expect(recorded(fetchMock).some(
      (call) => call.method === 'GET' && call.url === '/api/user/geometry/figures/5'
    )).toBe(true)
    expect(recorded(fetchMock).some((call) => call.method === 'POST')).toBe(false)
    // 図形名・作図タイプ・メモ・タグ・作図データ・サムネイルを画面に出す
    expect((view.get('[data-gm-draw-title]').element as HTMLInputElement).value).toBe('三角形の重心')
    expect((view.get('[data-gm-draw-type-option="geometry"]').element as HTMLInputElement).checked).toBe(true)
    expect((view.get('[data-gm-draw-memo]').element as HTMLTextAreaElement).value).toBe('中線の交点を作図する。')
    expect(view.get('[data-gm-draw-tag-list]').text()).toContain('三角形')
    expect(view.get('[data-gm-draw-tag-list]').text()).toContain('重心')
    expect(api.xml).toBe(COPIED_CONSTRUCTION)
    expect(view.get('[data-gm-draw-preview-img]').attributes('src')).toBe(THUMBNAIL_URL)
    // コピー元であることと、保存すると新規作成になることを知らせる
    const state = view.get('[data-gm-draw-state]').text()
    expect(state).toContain('コピー元')
    expect(state).toContain('GEO20260402-124745')
    expect(state).toContain('保存すると新しい図形になります')
    expect(state).not.toContain('編集中')
    expect(view.get('[data-gm-draw-source]').text()).toBe(state)
  })

  it('copyFrom と geometryId が同時にあるときは copyFrom を優先する', async () => {
    const { fetchMock } = await setup({ figureId: 9, copyFromId: 5 })

    const details = recorded(fetchMock).filter(
      (call) => call.method === 'GET' && /\/figures\/\d+$/.test(call.url)
    )
    expect(details.map((call) => call.url)).toEqual(['/api/user/geometry/figures/5'])
  })

  it('コピーして開いたときの保存は新規作成（POST・version なし）になる', async () => {
    const { wrapper: view, fetchMock } = await setup({ copyFromId: 5 })

    await type(view, '[data-gm-draw-title]', '三角形の重心（コピー）')
    await view.get('[data-gm-draw-save]').trigger('click')
    await flushPromises()

    const post = recorded(fetchMock).find((call) => call.method === 'POST' && call.url === '/api/user/geometry/figures')
    expect(post).toBeDefined()
    expect(post?.body).toMatchObject({ title: '三角形の重心（コピー）' })
    expect(String(post?.body?.construction)).toContain(CONSTRUCTION)
    expect(post?.body).not.toHaveProperty('version')
    expect(recorded(fetchMock).some((call) => call.method === 'PUT')).toBe(false)
    expect(toastMessages()).toContain('図形を保存しました。')
    // 保存したあとは、その図形の編集になる
    expect(view.get('[data-gm-draw-state]').text()).toContain('編集中')
  })

  it('折りたたみで左の入力欄を隠し、もう一度押すと戻る', async () => {
    const { wrapper: view } = await setup()

    expect(view.get('[data-gm-draw-collapsed]').attributes('data-gm-draw-collapsed')).toBe('false')
    expect(view.get('[data-gm-draw-side]').isVisible()).toBe(true)
    expect(view.get('[data-gm-draw-collapse]').attributes('aria-expanded')).toBe('true')
    expect(view.get('[data-gm-draw-collapse]').attributes('title')).toBe('入力欄を隠す')

    await view.get('[data-gm-draw-collapse]').trigger('click')
    await flushPromises()

    expect(view.get('[data-gm-draw-collapsed]').attributes('data-gm-draw-collapsed')).toBe('true')
    expect(view.get('[data-gm-draw-side]').isVisible()).toBe(false)
    // 折りたたんでも戻せるようにボタンは残る
    expect(view.get('[data-gm-draw-collapse]').isVisible()).toBe(true)
    expect(view.get('[data-gm-draw-collapse]').attributes('aria-expanded')).toBe('false')
    expect(view.get('[data-gm-draw-collapse]').attributes('title')).toBe('入力欄を表示')

    await view.get('[data-gm-draw-collapse]').trigger('click')
    await flushPromises()

    expect(view.get('[data-gm-draw-collapsed]').attributes('data-gm-draw-collapsed')).toBe('false')
    expect(view.get('[data-gm-draw-side]').isVisible()).toBe(true)
  })

  it('枠の幅が変わったら GeoGebra の大きさも合わせる（左メニュー・入力欄の開閉で広がっても空白を残さない）', async () => {
    // jsdom に ResizeObserver が無いので、コールバックを test から発火できる代役を入れる
    type ResizeEntry = { contentRect: { width: number; height: number } }
    const callbacks: ((entries: ResizeEntry[]) => void)[] = []
    let observed: Element | null = null
    class FakeResizeObserver {
      constructor(callback: (entries: ResizeEntry[]) => void) { callbacks.push(callback) }
      observe(element: Element): void { observed = element }
      disconnect(): void { /* 何もしない */ }
    }
    vi.stubGlobal('ResizeObserver', FakeResizeObserver)

    const { wrapper: view } = await setup()
    const frame = view.get('[data-gm-draw-frame]').element as HTMLElement
    expect(observed).toBe(frame)

    // 初期は作ったときの幅（jsdom では clientWidth が 0 なので 800）
    const appletElement = document.querySelector('[data-gm-draw-applet]')?.firstElementChild as HTMLElement
    expect(appletElement.style.width).toBe('800px')

    // 左メニューを畳んだ／入力欄を隠した後と同じように、枠だけが広がった状態を作る
    Object.defineProperty(frame, 'clientWidth', { value: 1146, configurable: true })
    for (const callback of callbacks) callback([{ contentRect: { width: 1146, height: 792 } }])
    await flushPromises()

    // 1. アプレットの要素そのものを枠に合わせる（これをしないと右側が空白になる）
    expect(appletElement.style.width).toBe('1146px')
    // 2. GeoGebra の内部の描画領域にも伝え、広がった部分を描き直させる
    expect(api.sizes.at(-1)?.[0]).toBe(1146)
    // 3. 注入先（GeoGebra の入れ物）の幅も枠に合わせる
    //    （GeoGebra は作ったときの大きさを書き込むので、放っておくと中身が右端まで描かれない）
    expect((document.getElementById('geometryGgbElement') as HTMLElement).style.width).toBe('1146px')
    // 4. 再描画は「止めて**少し待ってから**再開」する（すぐ戻すと広がった分が描き直されない）。
    //    描き直しのサイクルは間隔をあけて数回回す（1 回では GWT の再レイアウトと重なって効かない）。
    await new Promise((resolve) => window.setTimeout(resolve, 600))
    expect(api.repaintStates.at(-1)).toBe(false)
    await new Promise((resolve) => window.setTimeout(resolve, 400))
    expect(api.refreshes).toBeGreaterThan(0)
    expect(api.repaintStates.at(-1)).toBe(true)
    // 同じ大きさのうちは何度も送らない
    const sentCount = api.sizes.length
    for (const callback of callbacks) callback([{ contentRect: { width: 1146, height: 792 } }])
    await flushPromises()
    expect(api.sizes.length).toBe(sentCount)
  })

  /**
   * GeoGebra は**大きさが変わっていない**と描き直さない（実測: 枠 1884 で
   * `setSize(1884, 1034)` を何度呼んでも絵は古い幅 1953 で止まり、
   * `setWidth` / `setHeight` / `refreshViews` / 再描画の停止→再開でも直らなかった）。
   * 1px だけ広げてから戻すと「変わった」と見て**全域を描き直す**
   * （実測: 同じ状態で絵が 2544 まで届いた。費用は 40ms ほど）。
   */
  it('枠が広がったら、大きさを 1px ずらして戻す（大きくても描き直させる）', async () => {
    type ResizeEntry = { contentRect: { width: number; height: number } }
    const callbacks: ((entries: ResizeEntry[]) => void)[] = []
    class FakeResizeObserver {
      constructor(callback: (entries: ResizeEntry[]) => void) { callbacks.push(callback) }
      observe(): void { /* 何もしない */ }
      disconnect(): void { /* 何もしない */ }
    }
    vi.stubGlobal('ResizeObserver', FakeResizeObserver)

    const { wrapper: view } = await setup()
    const frame = view.get('[data-gm-draw-frame]').element as HTMLElement

    Object.defineProperty(frame, 'clientWidth', { value: 1146, configurable: true })
    for (const callback of callbacks) callback([{ contentRect: { width: 1146, height: 792 } }])
    await flushPromises()

    // 枠の幅（1146）を伝えたあと、1px 広げて（1147）から戻す（1146）
    expect(api.sizes.slice(-3).map(([width]) => width)).toEqual([1146, 1147, 1146])
  })

  /**
   * 【入力欄を隠す】のような大きなレイアウト変更でも、アプレットは作り直さない。
   * 作り直すと 1〜2 秒かかるうえ取り消し履歴が消えるので、1px ずらしの描き直しで済ませる
   * （それでも直らないときだけ、送り直しの最後の手段として作り直す）。
   */
  it('入力欄の出し入れでは、アプレットを作り直さない（1px ずらしで描き直す）', async () => {
    vi.useFakeTimers()
    try {
      api = new FakeApi()
      const { wrapper: view } = await setup()
      const frame = view.get('[data-gm-draw-frame]').element as HTMLElement
      // 入力欄を隠すと枠が 1289 → 1665 に広がる
      Object.defineProperty(frame, 'clientWidth', {
        configurable: true,
        get: () => (view.get('[data-gm-draw-collapsed]').attributes('data-gm-draw-collapsed') === 'true'
          ? 1665 : 1289)
      })
      expect(FakeApplet.instances).toHaveLength(1)

      await view.get('[data-gm-draw-collapse]').trigger('click')
      await flushPromises()
      await vi.advanceTimersByTimeAsync(3500)
      await flushPromises()

      // 作り直していない（作図も取り消し履歴もそのまま）
      expect(FakeApplet.instances).toHaveLength(1)
      expect(api.xml).toContain(CONSTRUCTION)
      // 枠の幅（1665）を伝えて、1px ずらしも送っている
      expect(api.sizes.map(([width]) => width)).toContain(1665)
      expect(api.sizes.map(([width]) => width)).toContain(1666)
      expect(api.sizes.at(-1)?.[0]).toBe(1665)
      // 落ち着いてからも何度か回す（1 回で効かないことがあるため）
      expect(api.sizes.filter(([width]) => width === 1666).length).toBeGreaterThanOrEqual(2)
    } finally {
      vi.useRealTimers()
    }
  })

  it('枠の幅がわずかに変わったときも、作り直さずに描き直させる', async () => {
    vi.useFakeTimers()
    try {
      api = new FakeApi()
      const { wrapper: view } = await setup()
      const frame = view.get('[data-gm-draw-frame]').element as HTMLElement
      // 10px しか変わらない（スクロールバーの出入り程度）
      Object.defineProperty(frame, 'clientWidth', {
        configurable: true,
        get: () => (view.get('[data-gm-draw-collapsed]').attributes('data-gm-draw-collapsed') === 'true'
          ? 1299 : 1289)
      })

      await view.get('[data-gm-draw-collapse]').trigger('click')
      await flushPromises()
      await vi.advanceTimersByTimeAsync(3500)
      await flushPromises()

      expect(FakeApplet.instances).toHaveLength(1)
      expect(api.sizes.map(([width]) => width)).toContain(1300)
    } finally {
      vi.useRealTimers()
    }
  })

  it('大きさの指示が 1 回で効かなくても、描画領域が届くまで伝え直す', async () => {
    type ResizeEntry = { contentRect: { width: number; height: number } }
    const callbacks: ((entries: ResizeEntry[]) => void)[] = []
    class FakeResizeObserver {
      constructor(callback: (entries: ResizeEntry[]) => void) { callbacks.push(callback) }
      observe(): void { /* 何もしない */ }
      disconnect(): void { /* 何もしない */ }
    }
    vi.stubGlobal('ResizeObserver', FakeResizeObserver)

    const { wrapper: view } = await setup()
    const frame = view.get('[data-gm-draw-frame]').element as HTMLElement
    const applet = document.querySelector('[data-gm-draw-applet]')?.firstElementChild as HTMLElement
    const canvas = document.createElement('canvas')
    applet.appendChild(canvas)

    // GeoGebra の作図領域は「アプレット − ツールバー(293px)」で、右端はアプレットの右端と一致する
    const rect = (left: number, right: number): DOMRect => ({
      left, right, top: 0, bottom: 792, width: right - left, height: 792, x: left, y: 0,
      toJSON: () => ({})
    }) as DOMRect
    Object.defineProperty(applet, 'getBoundingClientRect', { value: () => rect(0, 1146) })
    Object.defineProperty(canvas, 'getBoundingClientRect', {
      value: () => rect(293, 293 + Number.parseFloat(canvas.style.width === '' ? '0' : canvas.style.width))
    })
    const setCanvasWidth = (width: number): void => { canvas.style.width = `${width - 293}px` }
    api.onSize = setCanvasWidth
    // 1 回目の指示は効かない（GWT の再レイアウトと重なったときと同じ状況）。
    // 大きさを伝える 3 回（枠の幅・1px 広げ・元に戻す）がまとめて無視される
    api.ignoreSizes = 3

    Object.defineProperty(frame, 'clientWidth', { value: 1146, configurable: true })
    for (const callback of callbacks) callback([{ contentRect: { width: 1146, height: 792 } }])
    await flushPromises()
    // 1 回目は効いていない（描画領域の幅はまだ入っていない）
    expect(canvas.style.width).toBe('')

    // 少し待つと伝え直され、描画領域が枠の右端まで届く
    await new Promise((resolve) => window.setTimeout(resolve, 400))
    expect(api.sizes.filter(([width]) => width === 1146).length).toBeGreaterThanOrEqual(2)
    expect(canvas.style.width).toBe('853px')
  })

  it('内部ビューが古い幅のままだと（右側が白い）、描き直させる', async () => {
    type ResizeEntry = { contentRect: { width: number; height: number } }
    const callbacks: ((entries: ResizeEntry[]) => void)[] = []
    class FakeResizeObserver {
      constructor(callback: (entries: ResizeEntry[]) => void) { callbacks.push(callback) }
      observe(): void { /* 何もしない */ }
      disconnect(): void { /* 何もしない */ }
    }
    vi.stubGlobal('ResizeObserver', FakeResizeObserver)

    const { wrapper: view } = await setup()
    const frame = view.get('[data-gm-draw-frame]').element as HTMLElement
    const applet = document.querySelector('[data-gm-draw-applet]')?.firstElementChild as HTMLElement
    const canvas = document.createElement('canvas')
    applet.appendChild(canvas)

    const rect = (left: number, right: number): DOMRect => ({
      left, right, top: 0, bottom: 792, width: right - left, height: 792, x: left, y: 0,
      toJSON: () => ({})
    }) as DOMRect
    // canvas の右端はアプレットの右端と一致している（要素の大きさは合っている）
    Object.defineProperty(applet, 'getBoundingClientRect', { value: () => rect(0, 1146) })
    Object.defineProperty(canvas, 'getBoundingClientRect', { value: () => rect(293, 1146) })
    Object.defineProperty(canvas, 'width', { value: 853, configurable: true })

    // GeoGebra の内部ビューは古い幅（600）のまま＝右側 253px が白い
    api.viewWidth = 600
    // 伝え直すと追随する（実機の再レイアウトに相当）
    api.onSize = () => { api.viewWidth = 853 }

    Object.defineProperty(frame, 'clientWidth', { value: 1146, configurable: true })
    for (const callback of callbacks) callback([{ contentRect: { width: 1146, height: 792 } }])
    await flushPromises()
    await new Promise((resolve) => window.setTimeout(resolve, 400))

    // 送り直しで内部ビューが追随した（＝右側まで描かれる）
    await new Promise((resolve) => window.setTimeout(resolve, 400))
    expect(api.viewWidth).toBe(853)
    expect(api.sizes.filter(([width]) => width === 1146).length).toBeGreaterThanOrEqual(2)
    // 送り直しで直ったので、作り直しはしない
    expect(FakeApplet.instances).toHaveLength(1)
  })

  /**
   * 大きさが合っていても、GeoGebra が**古い大きさの範囲しか描かない**ことがある
   * （実測: canvas は 1498 まで広がったのに、絵は古い幅 903 の所までしか描かれず、
   * 右側は**透明のまま**残ってページの白が透けて見えていた）。
   * 画素で見つけて、送り直しで直らなければアプレットを作り直す。
   */
  it('右端がまだ描かれていない（古い大きさの範囲しか描かない）ときは、アプレットを作り直す', async () => {
    type ResizeEntry = { contentRect: { width: number; height: number } }
    const callbacks: ((entries: ResizeEntry[]) => void)[] = []
    class FakeResizeObserver {
      constructor(callback: (entries: ResizeEntry[]) => void) { callbacks.push(callback) }
      observe(): void { /* 何もしない */ }
      disconnect(): void { /* 何もしない */ }
    }
    vi.stubGlobal('ResizeObserver', FakeResizeObserver)

    const { wrapper: view } = await setup()
    const frame = view.get('[data-gm-draw-frame]').element as HTMLElement
    const applet = document.querySelector('[data-gm-draw-applet]')?.firstElementChild as HTMLElement
    const canvas = document.createElement('canvas')
    applet.appendChild(canvas)

    const rect = (left: number, right: number): DOMRect => ({
      left, right, top: 0, bottom: 792, width: right - left, height: 792, x: left, y: 0,
      toJSON: () => ({})
    }) as DOMRect
    // 大きさはすべて合っている（canvas の右端＝アプレットの右端、内部ビューも一致）
    Object.defineProperty(applet, 'getBoundingClientRect', { value: () => rect(0, 1146) })
    Object.defineProperty(canvas, 'getBoundingClientRect', { value: () => rect(293, 1146) })
    Object.defineProperty(canvas, 'width', { value: 853, configurable: true })
    Object.defineProperty(canvas, 'height', { value: 700, configurable: true })
    api.viewWidth = 853

    // 画素の代役: 左半分（古い幅）だけ白く描かれていて、右半分は**透明**のまま
    // （透明な画素は RGB が 0 になるので、「白でなければ描いてある」判定では見つけられない）
    const fakeContext = {
      clearRect: () => undefined,
      drawImage: () => undefined,
      getImageData: (_x: number, _y: number, width: number) => {
        const data = new Uint8ClampedArray(width * 4)
        for (let x = 0; x < Math.floor(width / 2); x += 1) {
          data[x * 4] = 255
          data[x * 4 + 1] = 255
          data[x * 4 + 2] = 255
          data[x * 4 + 3] = 255
        }
        return { data, width, height: 1 }
      }
    }
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext')
      .mockReturnValue(fakeContext as unknown as CanvasRenderingContext2D)

    Object.defineProperty(frame, 'clientWidth', { value: 1146, configurable: true })
    for (const callback of callbacks) callback([{ contentRect: { width: 1146, height: 792 } }])
    await flushPromises()
    // 再試行の間隔（250ms）× 回数ぶん待つと、作り直しに進む
    await new Promise((resolve) => window.setTimeout(resolve, 1600))

    expect(FakeApplet.instances).toHaveLength(2)
    // 作図は XML で引き継ぐ
    expect(api.xml).toContain(CONSTRUCTION)
  })

  it('何度送っても内部ビューが追随しないときは、アプレットを作り直す（作図は残す）', async () => {
    type ResizeEntry = { contentRect: { width: number; height: number } }
    const callbacks: ((entries: ResizeEntry[]) => void)[] = []
    class FakeResizeObserver {
      constructor(callback: (entries: ResizeEntry[]) => void) { callbacks.push(callback) }
      observe(): void { /* 何もしない */ }
      disconnect(): void { /* 何もしない */ }
    }
    vi.stubGlobal('ResizeObserver', FakeResizeObserver)

    const { wrapper: view } = await setup()
    const frame = view.get('[data-gm-draw-frame]').element as HTMLElement
    const applet = document.querySelector('[data-gm-draw-applet]')?.firstElementChild as HTMLElement
    const canvas = document.createElement('canvas')
    applet.appendChild(canvas)

    const rect = (left: number, right: number): DOMRect => ({
      left, right, top: 0, bottom: 792, width: right - left, height: 792, x: left, y: 0,
      toJSON: () => ({})
    }) as DOMRect
    Object.defineProperty(applet, 'getBoundingClientRect', { value: () => rect(0, 1146) })
    Object.defineProperty(canvas, 'getBoundingClientRect', { value: () => rect(293, 1146) })
    Object.defineProperty(canvas, 'width', { value: 853, configurable: true })
    // 実測で setSize では抜け出せない状態（内部ビューが永久に古いまま）
    api.viewWidth = 600

    Object.defineProperty(frame, 'clientWidth', { value: 1146, configurable: true })
    for (const callback of callbacks) callback([{ contentRect: { width: 1146, height: 792 } }])
    await flushPromises()
    // 再試行の間隔（250ms）× 回数ぶん待つ
    await new Promise((resolve) => window.setTimeout(resolve, 1600))

    // アプレットを作り直した（＝新しい大きさで作り直すので必ず直る）
    expect(FakeApplet.instances).toHaveLength(2)
    expect(FakeApplet.instances[1].params.width).toBe(1146)
    // 作図は XML で引き継ぐ（消えない）
    expect(api.xml).toContain(CONSTRUCTION)

    // 作り直しは 1 つにつき 1 回だけ（画面が揺れ続けない）
    await new Promise((resolve) => window.setTimeout(resolve, 800))
    expect(FakeApplet.instances).toHaveLength(2)
  })

  it('作図エリアの高さはビューポートの残りから計算し、リサイズに追随する', async () => {
    vi.stubGlobal('innerHeight', 900)
    const { wrapper: view } = await setup()

    // jsdom では枠の上端が 0 なので 900 - 余白 48 = 852px
    const frame = view.get('[data-gm-draw-frame]').element as HTMLElement
    expect(frame.style.height).toBe('852px')

    vi.stubGlobal('innerHeight', 700)
    window.dispatchEvent(new Event('resize'))
    await flushPromises()

    expect(frame.style.height).toBe('652px')
    // GeoGebra にも新しい大きさを伝える
    expect(api.sizes.at(-1)?.[1]).toBe(652)

    // 狭いときは下限（420px）を下回らない
    vi.stubGlobal('innerHeight', 400)
    window.dispatchEvent(new Event('resize'))
    await flushPromises()

    expect(frame.style.height).toBe('420px')

    // 画面を離れたらリサイズを追わない（リスナーを外している）
    view.unmount()
    wrapper = null
    vi.stubGlobal('innerHeight', 1200)
    window.dispatchEvent(new Event('resize'))

    expect(frame.style.height).toBe('420px')
  })

  it('保存・作図の操作は【一覧へ戻る】と同じ見出し行にまとめる（左の入力欄には置かない）', async () => {
    const { wrapper: view } = await setup()

    // 作図エリアのツールバーは廃止し、見出し行にまとめた
    expect(view.find('.gm-stage__toolbar').exists()).toBe(false)
    const head = view.get('.gm-list-head')
    for (const hook of ['data-gm-draw-collapse', 'data-gm-draw-center', 'data-gm-draw-clear',
      'data-gm-ai-assist-toggle', 'data-gm-draw-save', 'data-gm-draw-back']) {
      expect(head.find(`[${hook}]`).exists(), hook).toBe(true)
    }
    // 並びは 作図の操作 → AI 助手 → 保存 → 一覧へ戻る
    const order = (hook: string): number => head.html().indexOf(hook)
    expect(order('data-gm-draw-center')).toBeGreaterThan(-1)
    expect(order('data-gm-draw-center')).toBeLessThan(order('data-gm-ai-assist-toggle'))
    expect(order('data-gm-ai-assist-toggle')).toBeLessThan(order('data-gm-draw-save'))
    expect(order('data-gm-draw-save')).toBeLessThan(order('data-gm-draw-back'))

    // 左の入力欄には保存を置かない
    expect(view.get('[data-gm-draw-side]').find('[data-gm-draw-save]').exists()).toBe(false)

    // 【名前を付けて保存】と【GeoGebra を再読み込み】は置かない（利用者の指示）
    expect(view.find('[data-gm-draw-save-as]').exists()).toBe(false)
    expect(view.find('[data-gm-draw-reload]').exists()).toBe(false)
    // 「作図できます／準備中...」の文言も置かない（状態は [data-gm-draw-frame] の属性）
    expect(view.text()).not.toContain('作図できます')
    expect(view.text()).not.toContain('準備中')
    expect(view.get('[data-gm-draw-frame]').attributes('data-gm-draw-ready')).toBe('true')
  })

  it('タイトルは 新規＝【図形作成】／編集＝【図形編集】／コピー＝【図形コピー作成】', async () => {
    const created = await setup()
    expect(created.wrapper.get('[data-gm-draw-title-text]').text()).toBe('図形作成')

    const edited = await setup({ figureId: 1 })
    expect(edited.wrapper.get('[data-gm-draw-title-text]').text()).toBe('図形編集')

    const copied = await setup({ copyFromId: 1 })
    expect(copied.wrapper.get('[data-gm-draw-title-text]').text()).toBe('図形コピー作成')

    // 左の入力欄に余白を付ける（枠に貼り付かない）
    const css = readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'src/features/geometry/geometry.css'), 'utf8')
    const draw = /\.gm-draw\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(draw).toMatch(/padding:\s*var\(--sp-4\) var\(--sp-5\)/)
  })

  it('入力欄を折りたたんでも保存できる', async () => {
    const { wrapper: view, fetchMock } = await setup()

    await view.get('[data-gm-draw-collapse]').trigger('click')
    await flushPromises()

    // 左の入力欄は隠れても、保存はツールバーにあるので押せる
    expect(view.get('[data-gm-draw-side]').isVisible()).toBe(false)
    expect(view.get('[data-gm-draw-save]').isVisible()).toBe(true)

    await view.get('[data-gm-draw-save]').trigger('click')
    await flushPromises()

    expect(recorded(fetchMock).some(
      (call) => call.method === 'POST' && call.url === '/api/user/geometry/figures'
    )).toBe(true)
  })

  /**
   * タグ候補は**既定で 2 行**だけ出す（利用者の指示）。何枚で 2 行になるかは幅で変わるので、
   * 画面は描画後の行の位置（`offsetTop`）を測って枚数を決める。
   * jsdom は `offsetTop` を常に 0 で返すため測れない＝**全部出す**（黙って減らさない）。
   */
  it('タグ候補は既定で 2 行まで（測れない環境では全部出す）', async () => {
    const suggestions = Array.from({ length: 13 }, (_, index) => ({
      tag: `タグ${index + 1}`,
      count: 20 - index
    }))
    const { wrapper: view } = await setup({ suggestions })

    const shown = () => view.findAll('[data-gm-draw-tag-suggestion]').map((chip) => chip.text())
    // 行の位置を測れない（jsdom）ときは切り出さない
    expect(shown()).toHaveLength(13)
    expect(view.find('[data-gm-tag-more]').exists()).toBe(false)
  })

  it('幅があって 2 行を超えるときは 2 行だけ出し、「もっと見る」で開閉する', async () => {
    const suggestions = Array.from({ length: 13 }, (_, index) => ({
      tag: `タグ${index + 1}`,
      count: 20 - index
    }))
    const { wrapper: view } = await setup({ suggestions })

    /**
     * 候補の「行」を jsdom でも測れるようにする。
     * jsdom の `offsetTop` は常に 0 なので、**1 行に何枚入るか**を模した値を返す
     * （`perRow` 枚ごとに 30px 下の行へ）。画面はこの値から 2 行ぶんの枚数を決める。
     */
    const stubRows = (perRow: number): void => {
      Object.defineProperty(window.HTMLElement.prototype, 'offsetTop', {
        configurable: true,
        get(this: HTMLElement) {
          const parent = this.parentElement
          if (parent === null) return 0
          const chips = Array.from(parent.querySelectorAll('[data-gm-draw-tag-suggestion]'))
          const index = chips.indexOf(this)
          return index < 0 ? 0 : Math.floor(index / perRow) * 30
        }
      })
    }
    const shown = (): string[] => view.findAll('[data-gm-draw-tag-suggestion]').map((chip) => chip.text())

    try {
      // 1 行 5 枚（= 13 枚で 3 行）→ 2 行（10 枚）まで出して残りは【もっと見る】
      stubRows(5)
      window.dispatchEvent(new Event('resize'))
      await flushPromises()

      expect(shown()).toHaveLength(10)
      expect(shown()[0]).toContain('タグ1')
      expect(shown()).not.toContain('タグ11（10）')
      expect(view.get('[data-gm-tag-more]').text()).toBe('もっと見る（残り 3 件）')

      // 開くと全部出る（このときは切り出さない）
      await view.get('[data-gm-tag-more]').trigger('click')
      expect(shown()).toHaveLength(13)
      expect(shown()).toContain('タグ13（8）')

      // 閉じて、幅が狭くなった想定（1 行 1 枚）で測り直す → 2 枚だけ残る
      await view.get('[data-gm-tag-more]').trigger('click')
      stubRows(1)
      window.dispatchEvent(new Event('resize'))
      await flushPromises()

      expect(shown()).toHaveLength(2)
      expect(view.get('[data-gm-tag-more]').text()).toBe('もっと見る（残り 11 件）')
      expect(view.get('[data-gm-tag-more]').attributes('aria-expanded')).toBe('false')
    } finally {
      // 後続のテストに影響させない（jsdom の既定に戻す）
      delete (window.HTMLElement.prototype as unknown as Record<string, unknown>).offsetTop
    }
  })

  it('タグが無いときは「タグはまだありません。」を出さない', async () => {
    const { wrapper: view } = await setup()

    expect(view.find('[data-gm-draw-tag-empty]').exists()).toBe(false)
    expect(view.get('[data-gm-draw-tag-list]').text()).toBe('')
  })

  it('保存は作図データとサムネイルを付けて送る（新規）', async () => {
    const { wrapper: view, fetchMock } = await setup()

    await type(view, '[data-gm-draw-title]', '新しい円')
    await type(view, '[data-gm-draw-memo]', '中心と半径を決める。')
    await type(view, '[data-gm-draw-tag-input]', '円')
    await view.get('[data-gm-draw-tag-add]').trigger('click')
    await view.get('[data-gm-draw-save]').trigger('click')
    await flushPromises()

    const post = recorded(fetchMock).find((call) => call.method === 'POST' && call.url === '/api/user/geometry/figures')
    expect(post).toBeDefined()
    expect(post?.body).toMatchObject({
      title: '新しい円',
      figureType: 'geometry',
      memo: '中心と半径を決める。',
      tags: ['円'],
      thumbnail: THUMBNAIL
    })
    expect(String(post?.body?.construction)).toContain(CONSTRUCTION)
    // 新規なので version は送らない
    expect(post?.body).not.toHaveProperty('version')
    expect(toastMessages()).toContain('図形を保存しました。')
    // 保存後は同じ図形を更新する（編集状態になる）
    expect(view.get('[data-gm-draw-state]').text()).toContain('編集中')
  })

  it('図形名が空なら「名称未設定」で保存する（2.0 と同じ）', async () => {
    const { wrapper: view, fetchMock } = await setup()

    await view.get('[data-gm-draw-save]').trigger('click')
    await flushPromises()

    const post = recorded(fetchMock).find((call) => call.method === 'POST' && call.url === '/api/user/geometry/figures')
    expect(post?.body?.title).toBe('名称未設定')
  })

  it('編集の保存は楽観的ロック（version）付きで更新する', async () => {
    const { wrapper: view, fetchMock } = await setup({ figureId: 5 })

    await type(view, '[data-gm-draw-title]', '三角形の重心（改）')
    await type(view, '[data-gm-draw-tag-input]', '証明')
    await view.get('[data-gm-draw-tag-add]').trigger('click')
    await view.get('[data-gm-draw-save]').trigger('click')
    await flushPromises()

    const put = recorded(fetchMock).find((call) => call.method === 'PUT')
    expect(put?.url).toBe('/api/user/geometry/figures/5')
    expect(put?.body).toMatchObject({
      title: '三角形の重心（改）',
      tags: ['三角形', '重心', '証明'],
      version: 3
    })
    expect(toastMessages()).toContain('図形を更新しました。')
  })

  /**
   * 【名前を付けて保存】は画面から外した（利用者の指示）。編集中の保存は**更新**だけ。
   * 新しい図形を作る導線は一覧の【コピー】（`?copyFrom=`）と【新規】に残っている。
   */
  it('編集中に保存できるのは更新だけ（名前を付けて保存は置かない）', async () => {
    const { wrapper: view, fetchMock } = await setup({ figureId: 5 })

    expect(view.find('[data-gm-draw-save-as]').exists()).toBe(false)

    await view.get('[data-gm-draw-save]').trigger('click')
    await flushPromises()

    expect(recorded(fetchMock).some((call) => call.method === 'PUT')).toBe(true)
    expect(recorded(fetchMock).some(
      (call) => call.method === 'POST' && call.url === '/api/user/geometry/figures'
    )).toBe(false)
  })

  it('保存に失敗したら理由を知らせる', async () => {
    const { wrapper: view } = await setup({
      handlers: (url, method) =>
        method === 'POST' && url.endsWith('/figures') ? failure('作図データが大きすぎます。', 500) : null
    })

    await view.get('[data-gm-draw-save]').trigger('click')
    await flushPromises()

    expect(toastMessages()).toContain('作図データが大きすぎます。')
  })

  it('コマンド入力は 1 行ずつ実行し、作図をその内容に置き換える', async () => {
    api = new FakeApi()
    api.objects = ['A', 'B']
    const { wrapper: view } = await setup()

    await type(view, '[data-gm-draw-command]', 'A = (0, 4)\n\n  Segment(A, B)  ')
    await view.get('[data-gm-draw-command-run]').trigger('click')
    await flushPromises()

    // 前の作図は消す（1 つずつ。表示を初期値に戻す newConstruction は使わない）
    expect(api.deleted).toEqual(['A', 'B'])
    expect(api.newConstructions).toBe(0)
    expect(api.evaluated).toEqual(['A = (0, 4)', 'Segment(A, B)'])
    expect(toastMessages()).toContain('コマンドを 2 件実行しました。')
  })

  it('コマンドが失敗したら元の作図に戻す', async () => {
    api.failCommands = ['Circle((0, 0), -1)']
    const { wrapper: view } = await setup()

    await type(view, '[data-gm-draw-command]', 'Circle((0, 0), -1)')
    await view.get('[data-gm-draw-command-run]').trigger('click')
    await flushPromises()

    expect(api.xml).toContain(CONSTRUCTION)
    expect(toastMessages().join(' ')).toContain('Circle((0, 0), -1)')
  })

  /**
   * 「GeoGebra コマンド」欄は**いまの作図そのもの**を出す（利用者の指示）。
   *
   * 手動で描いても、動かしても、消しても、取り消しても、AI が反映しても、
   * アプレットの実際の作図から作り直す（AI が返した行や打ち込んだ行を並べるのではない）。
   * 実機で確かめた値の形（自由点は座標、交点は 1 行、多角形が作る辺は行にしない）を使う。
   */
  describe('コマンド欄（いまの作図を表す）', () => {
    /** 実機の `getAlgorithmXML` と同じ形の XML。 */
    function algorithmXml(command: string, inputs: string[], outputs: string[]): string {
      const input = inputs.map((name, index) => `a${index}="${name}"`).join(' ')
      const output = outputs.map((name, index) => `a${index}="${name}"`).join(' ')
      return `<command name="${command}"> <input ${input}/> <output ${output}/> </command>`
    }

    function commandText(view: VueWrapper): string {
      return (view.get('[data-gm-draw-command]').element as HTMLTextAreaElement).value
    }

    function stateText(view: VueWrapper): string {
      return view.get('[data-gm-draw-command-state]').text()
    }

    /** 手で描いた作図（点・円・円上の点・中点）をアプレットに持たせる。 */
    function drawnConstruction(): void {
      api.objects = ['A', 'B', 'c', 'D', 'M']
      api.commandStrings = {
        A: '(0, 0)',
        B: '(4, 0)',
        c: 'Circle(A, B)',
        D: 'Point(c)',
        M: 'Midpoint(A, B)'
      }
      api.algorithms = {
        c: algorithmXml('Circle', ['A', 'B'], ['c']),
        D: algorithmXml('Point', ['c'], ['D']),
        M: algorithmXml('Midpoint', ['A', 'B'], ['M'])
      }
    }

    const DRAWN = ['A = (0, 0)', 'B = (4, 0)', 'c = Circle(A, B)', 'D = Point(c)', 'M = Midpoint(A, B)'].join('\n')

    it('読み込んだ図形の作図をコマンド欄に出す（関係を座標に置き換えない）', async () => {
      drawnConstruction()
      const { wrapper: view } = await setup({ figureId: 5 })

      expect(commandText(view)).toBe(DRAWN)
      expect(stateText(view)).toContain('5 コマンド')
    })

    it('手で描いても動かしても、作図が変われば追いつく（更新通知で自動同期）', async () => {
      vi.useFakeTimers()
      try {
        const { wrapper: view } = await setup()
        expect(commandText(view)).toBe('')

        // 手で描いた（円の上に点を取った）
        drawnConstruction()
        api.notifyUpdate()
        await vi.advanceTimersByTimeAsync(300)
        await flushPromises()
        expect(commandText(view)).toBe(DRAWN)

        // 点をドラッグした（実機ではコマンド文字列が空になるので座標から作る）
        api.commandStrings = { ...api.commandStrings, A: '' }
        api.independentObjects = ['A']
        api.coords = { A: [1.135057471264367, -0.8241758241758241] }
        api.notifyUpdate()
        await vi.advanceTimersByTimeAsync(300)
        await flushPromises()
        expect(commandText(view)).toContain('A = (1.135057, -0.824176)')
        expect(commandText(view)).toContain('D = Point(c)')

        // 取り消した（点が消えて、前の状態に戻る）
        api.objects = ['A', 'B', 'c']
        api.notifyUpdate()
        await vi.advanceTimersByTimeAsync(300)
        await flushPromises()
        expect(commandText(view)).not.toContain('D = Point(c)')
      } finally {
        vi.useRealTimers()
      }
    })

    it('ドラッグ中は何度も読みに行かず、まとめて 1 回だけ読む', async () => {
      vi.useFakeTimers()
      try {
        const { wrapper: view } = await setup()
        drawnConstruction()
        const spy = vi.spyOn(api, 'getCommandString')

        // ドラッグ中は更新通知が何度も来る
        for (let index = 0; index < 8; index += 1) api.notifyUpdate()
        // まだ読まない（描いている途中で読まない）
        expect(spy).not.toHaveBeenCalled()

        await vi.advanceTimersByTimeAsync(300)
        await flushPromises()
        // 5 個の作図を 1 回ずつ読むだけ（8 回 × 5 個にはならない）
        expect(spy).toHaveBeenCalledTimes(5)
        expect(view.find('[data-gm-draw-command-state]').exists()).toBe(true)
      } finally {
        vi.useRealTimers()
      }
    })

    it('編集中の内容は自動同期で上書きしない（現在の作図は常に見える）', async () => {
      vi.useFakeTimers()
      try {
        drawnConstruction()
        const { wrapper: view } = await setup({ figureId: 5 })
        await type(view, '[data-gm-draw-command]', 'A = (9, 9)')

        // 編集中に作図が変わっても、打ち込んだ内容はそのまま
        api.commandStrings = { ...api.commandStrings, B: '(8, 8)' }
        api.notifyUpdate()
        await vi.advanceTimersByTimeAsync(300)
        await flushPromises()
        expect(commandText(view)).toBe('A = (9, 9)')
        // 編集中であることと、いまの作図のコマンド数は分かる
        expect(view.get('[data-gm-draw-command-editing]').text()).toContain('編集中')
        expect(stateText(view)).toContain('現在の作図は 5 コマンド')

        // 【作図の内容に戻す】でいまの作図に戻せる
        await view.get('[data-gm-draw-command-reset]').trigger('click')
        await flushPromises()
        expect(commandText(view)).toBe(
          ['A = (0, 0)', 'B = (8, 8)', 'c = Circle(A, B)', 'D = Point(c)', 'M = Midpoint(A, B)'].join('\n')
        )
        expect(view.find('[data-gm-draw-command-editing]').exists()).toBe(false)
      } finally {
        vi.useRealTimers()
      }
    })

    it('実行できたら、打ち込んだ行ではなく実際の作図から作り直す', async () => {
      // ラベルを書かない `Midpoint(A, B)` に、実機と同じく GeoGebra が名前（C）を付ける
      api.autoLabels = { 'Midpoint(A, B)': 'C' }
      const { wrapper: view } = await setup()

      await type(view, '[data-gm-draw-command]', 'A = (0, 0)\nB = (3, 0)\nMidpoint(A, B)')
      await view.get('[data-gm-draw-command-run]').trigger('click')
      await flushPromises()

      // 打ち込んだ 3 行目そのままではなく、アプレットにある作図（C = Midpoint(A, B)）が出る
      expect(commandText(view)).toBe(['A = (0, 0)', 'B = (3, 0)', 'C = Midpoint(A, B)'].join('\n'))
      expect(view.find('[data-gm-draw-command-editing]').exists()).toBe(false)
    })

    it('実行に失敗して戻したら、元の作図のコマンドのままにする（打ち込んだ内容は残す）', async () => {
      drawnConstruction()
      api.failCommands = ['Circle((9, 9), -1)']
      const { wrapper: view } = await setup({ figureId: 5 })
      expect(commandText(view)).toBe(DRAWN)

      await type(view, '[data-gm-draw-command]', 'Circle((9, 9), -1)')
      await view.get('[data-gm-draw-command-run]').trigger('click')
      await flushPromises()

      // 作図は元に戻っていて、いまの作図は 5 コマンドのまま（打ち込んだ内容は消さない）
      expect(api.xml).toContain(CONSTRUCTION)
      expect(stateText(view)).toContain('現在の作図は 5 コマンド')
      expect(commandText(view)).toBe('Circle((9, 9), -1)')

      // 【作図の内容に戻す】で元の作図に戻せる
      await view.get('[data-gm-draw-command-reset]').trigger('click')
      await flushPromises()
      expect(commandText(view)).toBe(DRAWN)
    })

    it('全消去したらコマンド欄も空になる', async () => {
      drawnConstruction()
      const { wrapper: view } = await setup({ figureId: 5 })
      expect(commandText(view)).toBe(DRAWN)

      await view.get('[data-gm-draw-clear]').trigger('click')
      await flushPromises()

      expect(commandText(view)).toBe('')
      expect(stateText(view)).toContain('0 コマンド')
    })

    it('AI の反映でも、返ってきた行ではなく実際の作図から作り直す', async () => {
      drawnConstruction()
      const { wrapper: view } = await setup({ figureId: 5 })
      expect(commandText(view)).toBe(DRAWN)

      // 助手は D = (0, 0) と Segment(C, D) を返す（反映するとアプレットにも入る）
      await view.get('[data-gm-ai-assist-toggle]').trigger('click')
      await flushPromises()
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      // AI が返した 2 行だけでなく、もとからある作図もそのまま出る
      expect(commandText(view)).toContain('D = (0, 0)')
      expect(commandText(view)).toContain('c = Circle(A, B)')
      expect(commandText(view)).toContain('M = Midpoint(A, B)')
    })

    it('コマンドにできない作図（スライダなど）があるときは、その旨を出す', async () => {
      api.objects = ['A', 'n']
      api.commandStrings = { A: '(0, 0)' }
      // スライダはコマンド文字列を持たない（実機と同じ）
      vi.spyOn(api, 'getXML').mockReturnValue(
        '<construction><element type="numeric" label="n"> <value val="0"/> <slider min="-5" max="5"/> </element></construction>'
      )
      const { wrapper: view } = await setup()

      expect(commandText(view)).toBe('A = (0, 0)')
      const note = view.get('[data-gm-draw-command-limit]').text()
      expect(note).toContain('コマンドにできない作図が 1 件あります')
      expect(note).toContain('スライダ・チェックボックス')
      expect(note).toContain('n')
    })

    /**
     * 実行は作図を**作り直す**ので、欄に出せない作図（スライダなど）は消えてしまう。
     * 未編集のまま押されたときは実行せず、理由を出す（黙って消さない）。
     */
    it('コマンドにできない作図があるときは、未編集のまま実行しない', async () => {
      api.objects = ['A', 'n']
      api.commandStrings = { A: '(0, 0)' }
      vi.spyOn(api, 'getXML').mockReturnValue(
        '<construction><element type="numeric" label="n"> <value val="0"/> <slider min="-5" max="5"/> </element></construction>'
      )
      const { wrapper: view } = await setup()

      await view.get('[data-gm-draw-command-run]').trigger('click')
      await flushPromises()

      expect(api.newConstructions).toBe(0)
      expect(toastMessages().join(' ')).toContain('このまま実行すると消えます')
    })
  })

  it('全消去と中央表示がアプレットに伝わる', async () => {
    api = new FakeApi()
    api.objects = ['A', 'B']
    const { wrapper: view } = await setup()

    await view.get('[data-gm-draw-clear]').trigger('click')
    // 作図は 1 つずつ消す（**表示を初期値に戻す newConstruction は使わない**）
    expect(api.objects).toEqual([])
    expect(api.deleted).toEqual(['A', 'B'])
    expect(api.newConstructions).toBe(0)

    await view.get('[data-gm-draw-center]').trigger('click')
    expect(api.evaluated).toContain('ZoomIn(-10, -10, 10, 10)')
    expect(api.centered).toEqual([[0, 0]])
  })

  /**
   * 【全消去】は**図形だけ**を消す（利用者の指示）。
   *
   * 実機で確かめたこと: `newConstruction()` は作図だけでなく**表示の設定も初期値に戻す**
   * （`evSettings axes="true" grid="true"` → 両方 false、座標系の倍率も既定へ）。
   * そのため 1 つずつ消す（座標軸・グリッド＝利用者が選んだ表示は動かさない）。
   */
  describe('全消去は図形だけを消す', () => {
    /** 座標軸を表示・グリッドを出した状態にする（利用者が選んだ表示）。 */
    async function showGrid(view: VueWrapper): Promise<void> {
      await view.get('[data-gm-draw-grid]').setValue(true)
      api.axesCalls.length = 0
      api.gridCalls.length = 0
    }

    it('座標軸・グリッドの表示を変えない（checkbox もアプレットもそのまま）', async () => {
      api = new FakeApi()
      api.objects = ['A', 'B', 'c']
      api.commandStrings = { A: '(0, 0)', B: '(4, 0)', c: 'Circle(A, B)' }
      const { wrapper: view } = await setup()
      await showGrid(view)

      await view.get('[data-gm-draw-clear]').trigger('click')
      await flushPromises()

      // 図形は消える
      expect(api.objects).toEqual([])
      expect(api.newConstructions).toBe(0)
      // 表示は動かさない（アプレットへ送り直さない・状態も変わらない）
      expect(api.axesCalls).toEqual([])
      expect(api.gridCalls).toEqual([])
      expect(api.visible).toEqual({ xAxis: true, yAxis: true, grid: true })
      expect((view.get('[data-gm-draw-axes]').element as HTMLInputElement).checked).toBe(true)
      expect((view.get('[data-gm-draw-grid]').element as HTMLInputElement).checked).toBe(true)
      // コマンド欄も空になる
      expect((view.get('[data-gm-draw-command]').element as HTMLTextAreaElement).value).toBe('')
    })

    it('コマンドの実行（置き換え）でも表示を変えない', async () => {
      api = new FakeApi()
      api.objects = ['A']
      api.commandStrings = { A: '(0, 0)' }
      const { wrapper: view } = await setup()
      await showGrid(view)

      await type(view, '[data-gm-draw-command]', 'B = (1, 1)')
      await view.get('[data-gm-draw-command-run]').trigger('click')
      await flushPromises()

      // 前の作図は消して、打ち込んだ行だけを実行する（表示は触らない）
      expect(api.deleted).toEqual(['A'])
      expect(api.evaluated).toEqual(['B = (1, 1)'])
      expect(api.newConstructions).toBe(0)
      expect(api.axesCalls).toEqual([])
      expect(api.gridCalls).toEqual([])
      expect(api.visible.grid).toBe(true)
    })

    it('1 つずつ消せない実装では、消したあとに選んでいた表示へ戻す', async () => {
      api = new FakeApi()
      api.objects = ['A']
      api.commandStrings = { A: '(0, 0)' }
      // 古い GeoGebra（deleteObject が無い）を模す
      ;(api as unknown as { deleteObject?: unknown }).deleteObject = undefined
      const { wrapper: view } = await setup()
      await showGrid(view)

      await view.get('[data-gm-draw-clear]').trigger('click')
      await flushPromises()

      // 作図は新しい作図として消える（表示も初期値に戻ってしまう）
      expect(api.newConstructions).toBe(1)
      // だから、選んでいた表示（軸あり・グリッドあり）へ戻す
      expect(api.axesCalls).toEqual([[true, true]])
      expect(api.gridCalls).toEqual([true])
      expect(api.visible).toEqual({ xAxis: true, yAxis: true, grid: true })
      expect((view.get('[data-gm-draw-grid]').element as HTMLInputElement).checked).toBe(true)
    })

    it('消したあとのサムネイルを取り直す（1 つずつ消すと更新通知が来ないため）', async () => {
      vi.useFakeTimers()
      try {
        api = new FakeApi()
        api.objects = ['A']
        api.commandStrings = { A: '(0, 0)' }
        const { wrapper: view } = await setup()
        expect(view.find('[data-gm-draw-preview-img]').exists()).toBe(false)

        // 作図が変わったという通知は来ない（実機の deleteObject は通知を出さない）
        api.png = 'AFTERCLEAR'
        await view.get('[data-gm-draw-clear]').trigger('click')
        await vi.advanceTimersByTimeAsync(900)
        await flushPromises()

        expect(view.get('[data-gm-draw-preview-img]').attributes('src'))
          .toBe('data:image/png;base64,AFTERCLEAR')
      } finally {
        vi.useRealTimers()
      }
    })
  })

  /**
   * サムネイルは**自動で更新する**（利用者の指示で【サムネイル更新】ボタンは廃止）。
   * GeoGebra の更新通知（update / client listener）を受けて 900ms 待ってから 1 回だけ取り直す。
   */
  it('作図が変わるとサムネイルを自動で取り直す（案内は出さない）', async () => {
    vi.useFakeTimers()
    try {
      const { wrapper: view } = await setup()
      expect(view.find('[data-gm-draw-preview-img]').exists()).toBe(false)
      // 更新ボタンは置かない
      expect(view.find('[data-gm-draw-thumbnail]').exists()).toBe(false)

      // GeoGebra 側で作図が変わった（点を動かした等）
      api.png = 'AUTOTHUMB'
      api.notifyUpdate()

      // すぐには取り直さない（ドラッグ中に何度も作らないように待つ）
      await vi.advanceTimersByTimeAsync(300)
      expect(view.find('[data-gm-draw-preview-img]').exists()).toBe(false)

      // 900ms 経つと 1 回だけ取り直す
      await vi.advanceTimersByTimeAsync(700)
      await flushPromises()
      expect(view.get('[data-gm-draw-preview-img]').attributes('src'))
        .toBe('data:image/png;base64,AUTOTHUMB')
      // **自動なのでトーストは出さない**
      expect(toastMessages()).toEqual([])
    } finally {
      vi.useRealTimers()
    }
  })

  /**
   * 座標軸・グリッドの表示は**上部の操作行の checkbox**で切り替える（利用者の指示）。
   * 実際の切替は GeoGebra の `setAxesVisible` / `setGridVisible` に渡す。
   */
  it('座標軸・グリッドの checkbox がアプレットに正しい引数を渡す', async () => {
    api = new FakeApi()
    const { wrapper: view } = await setup()

    // 既定はいまのアプレットの状態を読むだけ（勝手に切り替えない）
    expect(api.axesCalls).toEqual([])
    expect(api.gridCalls).toEqual([])
    expect((view.get('[data-gm-draw-axes]').element as HTMLInputElement).checked).toBe(true)
    expect((view.get('[data-gm-draw-grid]').element as HTMLInputElement).checked).toBe(false)

    // グリッドを出す（x と y を同時に、が座標軸の約束）
    await view.get('[data-gm-draw-grid]').setValue(true)
    expect(api.gridCalls).toEqual([true])
    expect(api.axesCalls).toEqual([])

    // 座標軸を消す
    await view.get('[data-gm-draw-axes]').setValue(false)
    expect(api.axesCalls).toEqual([[false, false]])

    // 覚える（この端末だけ）
    expect(JSON.parse(window.localStorage.getItem('study21.geometry.axesGrid') ?? '{}'))
      .toEqual({ axes: false, grid: true })
  })

  it('覚えている座標軸・グリッドの状態を復元して、アプレットにも適用する', async () => {
    window.localStorage.setItem('study21.geometry.axesGrid', JSON.stringify({ axes: false, grid: true }))
    api = new FakeApi()
    const { wrapper: view } = await setup()

    expect((view.get('[data-gm-draw-axes]').element as HTMLInputElement).checked).toBe(false)
    expect((view.get('[data-gm-draw-grid]').element as HTMLInputElement).checked).toBe(true)
    // アプレットにも反映する（読み込み直しても選んだ表示のまま）
    expect(api.axesCalls).toEqual([[false, false]])
    expect(api.gridCalls).toEqual([true])
  })

  it('作図タイプを変えてアプレットを作り直しても、選んだ表示を再適用する', async () => {
    api = new FakeApi()
    const { wrapper: view } = await setup()

    await view.get('[data-gm-draw-grid]').setValue(true)
    expect(api.gridCalls).toEqual([true])

    // 関数グラフに変えるとアプレットを作り直す（そのあとにもう一度適用される）
    await view.get('[data-gm-draw-type-option="function"]').setValue()
    await flushPromises()

    expect(FakeApplet.instances).toHaveLength(2)
    expect(api.gridCalls).toEqual([true, true])
    expect((view.get('[data-gm-draw-grid]').element as HTMLInputElement).checked).toBe(true)
  })

  /** GeoGebra 自身のメニュー（歯車）で切り替えられても、checkbox がずれない。 */
  it('GeoGebra 側で表示を変えたら checkbox が追従する', async () => {
    api = new FakeApi()
    const { wrapper: view } = await setup()

    // アプレット側で変わったことにする（歯車メニューでの操作と同じ）
    api.visible.grid = true
    api.visible.xAxis = false
    api.visible.yAxis = false
    api.notifyUpdate()
    await flushPromises()

    expect((view.get('[data-gm-draw-grid]').element as HTMLInputElement).checked).toBe(true)
    expect((view.get('[data-gm-draw-axes]').element as HTMLInputElement).checked).toBe(false)
  })

  /** 表示の切替も「作図が変わった」ので、サムネイルは自動で取り直す。 */
  it('座標軸・グリッドを切り替えるとサムネイルを自動で取り直す', async () => {
    vi.useFakeTimers()
    try {
      api = new FakeApi()
      const { wrapper: view } = await setup()
      api.png = 'AXESGRID'
      await view.get('[data-gm-draw-grid]').setValue(true)
      await vi.advanceTimersByTimeAsync(900)
      await flushPromises()

      expect(view.get('[data-gm-draw-preview-img]').attributes('src'))
        .toBe('data:image/png;base64,AXESGRID')
    } finally {
      vi.useRealTimers()
    }
  })

  it('コマンドを実行するとサムネイルを自動で取り直す', async () => {
    vi.useFakeTimers()
    try {
      const { wrapper: view } = await setup()
      api.png = 'AFTERCOMMAND'
      await type(view, '[data-gm-draw-command]', 'A = (0, 0)')
      await view.get('[data-gm-draw-command-run]').trigger('click')
      await vi.advanceTimersByTimeAsync(900)
      await flushPromises()

      expect(view.get('[data-gm-draw-preview-img]').attributes('src'))
        .toBe('data:image/png;base64,AFTERCOMMAND')
      expect(toastMessages()).not.toContain('サムネイルを更新しました。')
    } finally {
      vi.useRealTimers()
    }
  })

  it('保存の直前にサムネイルを取り直す（空のまま保存しない）', async () => {
    const { wrapper: view, fetchMock } = await setup()
    api.png = 'ATSAVE'

    await type(view, '[data-gm-draw-title]', '自動更新の確認')
    await view.get('[data-gm-draw-save]').trigger('click')
    await flushPromises()

    const created = recorded(fetchMock).find(
      (call) => call.method === 'POST' && call.url === '/api/user/geometry/figures')
    expect(created?.body?.thumbnail).toBe('ATSAVE')
    // 保存した画像を画面にも出す
    expect(view.get('[data-gm-draw-preview-img]').attributes('src')).toBe('data:image/png;base64,ATSAVE')
  })

  it('サムネイルが取れないときは、読み込んだ図形のサムネイルで保存する', async () => {
    const { wrapper: view, fetchMock } = await setup({ figureId: 5 })
    // GeoGebra が画像を返せない状態にする
    api.png = null

    await view.get('[data-gm-draw-save]').trigger('click')
    // 画面は取れなかったときに 150ms 待って 1 度だけ取り直すので、その分待つ
    await new Promise((resolve) => setTimeout(resolve, 300))
    await flushPromises()

    const updated = recorded(fetchMock).find(
      (call) => call.method === 'PUT' && call.url === '/api/user/geometry/figures/5')
    expect(updated?.body?.thumbnail).toBe(THUMBNAIL)
  })

  it('タグは追加・候補からの追加・外すができる', async () => {
    const { wrapper: view } = await setup()

    await type(view, '[data-gm-draw-tag-input]', '作図')
    await view.get('[data-gm-draw-tag-add]').trigger('click')
    expect(view.get('[data-gm-draw-tag-list]').text()).toContain('作図')

    // 同じタグは増えない
    await type(view, '[data-gm-draw-tag-input]', '作図')
    await view.get('[data-gm-draw-tag-add]').trigger('click')
    expect(view.findAll('[data-gm-draw-tag="作図"]')).toHaveLength(1)
    expect(toastMessages()).toContain('同じタグが既に付いています。')

    // タグ候補のクリックで追加、もう一度押すと外れる
    await view.get('[data-gm-draw-tag-suggestion="三角形"]').trigger('click')
    expect(view.get('[data-gm-draw-tag-list]').text()).toContain('三角形')
    await view.get('[data-gm-draw-tag-suggestion="三角形"]').trigger('click')
    expect(view.find('[data-gm-draw-tag="三角形"]').exists()).toBe(false)

    // 「|」は使えない
    await type(view, '[data-gm-draw-tag-input]', '三角|形')
    await view.get('[data-gm-draw-tag-add]').trigger('click')
    expect(toastMessages()).toContain('タグに「|」は使えません。')

    // ×で外す
    await view.get('[data-gm-draw-tag-remove="作図"]').trigger('click')
    expect(view.find('[data-gm-draw-tag="作図"]').exists()).toBe(false)
  })

  it('一覧へ戻るボタンで図形管理の一覧に戻る', async () => {
    const { wrapper: view, appRouter } = await setup()

    await view.get('[data-gm-draw-back]').trigger('click')
    await flushPromises()

    expect(appRouter.currentRoute.value.path).toBe('/student/geometry')
  })

  it('図形を読み込めなかったら知らせて、新規として保存できる', async () => {
    const { wrapper: view, fetchMock } = await setup({
      figureId: 404,
      detail: failure('図形が見つかりません。')
    })

    expect(view.get('[data-gm-draw-load-error]').text()).toContain('図形が見つかりません。')
    expect(view.get('[data-gm-draw-state]').text()).toContain('新規作成')

    await view.get('[data-gm-draw-save]').trigger('click')
    await flushPromises()
    expect(recorded(fetchMock).some((call) => call.method === 'POST')).toBe(true)
  })

  /**
   * AI 生図の結果を開く（`?geometryAiRequestId=`）。
   *
   * AI は **GeoGebra のコマンド列**しか作らない（XML とサムネイルはこの画面の applet が作る）。
   * そのため `setXML` ではなく `evalCommand` で 1 行ずつ流し込み、図形の種類から
   * appName（Geometry / Graphing）を先に決める。保存は AI 生図の**確定**（登録元コード = AI）。
   */
  describe('AI 生図の結果を開く（?geometryAiRequestId=）', () => {
    it('作図タイプを要求から決めて、コマンドを 1 行ずつ流し込む', async () => {
      api = new FakeApi()
      const { wrapper: view } = await setup({ aiRequestId: 31 })

      // 要求の figureType で appName が決まる
      const applet = FakeApplet.instances[0]
      expect(applet.params.appName).toBe('geometry')
      // コマンドは evalCommand で 1 行ずつ（XML の差し替えではない）
      expect(api.evaluated).toEqual(['A = (0, 0)', 'B = (5, 0)', 'Polygon(A, B, C)'])
      // 提案（図形名・タグ・メモ）が初期値として入る
      expect((view.get('[data-gm-draw-title]').element as HTMLInputElement).value).toBe('三角形ABC')
      expect(view.get('[data-gm-draw-tag-list]').text()).toContain('三角形')
      // どの要求から開いたかを日本語で案内する（notice）
      const notice = view.get('[data-gm-draw-ai-notice]').text().replace(/\s+/g, ' ')
      expect(notice).toContain('AIG202609141200001234')
      expect(notice).toContain('AI 生図の図形')
    })

    it('関数グラフの要求なら Graphing のアプレットで開く', async () => {
      api = new FakeApi()
      await setup({
        aiRequestId: 32,
        aiRequest: {
          ...aiRequestDetail(),
          requestId: 32,
          figureType: 'function',
          commands: ['f(x) = x^2']
        }
      })

      expect(FakeApplet.instances[0].params.appName).toBe('graphing')
      expect(api.evaluated).toEqual(['f(x) = x^2'])
    })

    it('保存は AI 生図の確定（POST /requests/{id}/confirm）になる', async () => {
      api = new FakeApi()
      const { wrapper: view, fetchMock } = await setup({ aiRequestId: 31 })

      await view.get('[data-gm-draw-save]').trigger('click')
      await flushPromises()

      const calls = recorded(fetchMock)
      const confirm = calls.find((call) => call.url.endsWith('/geometry/ai/requests/31/confirm'))
      expect(confirm).toBeDefined()
      expect(confirm?.method).toBe('POST')
      // 作図データとサムネイル、図形名を付けて確定する（XML はこの画面が作ったもの）
      expect(String(confirm?.body?.construction)).toContain('construction')
      expect(String(confirm?.body?.title)).toBe('三角形ABC')
      expect(confirm?.body?.version).toBe(4)
      // 通常の図形作成（POST /figures）は呼ばない
      expect(calls.some((call) => call.method === 'POST' && call.url.endsWith('/geometry/figures'))).toBe(false)
      expect(toastMessages().join(' ')).toContain('AI 生図から図形を保存しました')
    })

    it('コマンドを実行できなかったら、理由を出して【保存】させない', async () => {
      api = new FakeApi()
      // GeoGebra が受け付けない行（実機で起きる: 引数の数が違う等）
      api.failCommands = ['Polygon(A, B, C)']
      const { wrapper: view, fetchMock } = await setup({ aiRequestId: 31 })

      // 実行できなかったことをはっきり出す（黙って空の作図を残さない）
      const failure = view.get('[data-gm-draw-ai-failure]').text().replace(/\s+/g, ' ')
      expect(failure).toContain('Polygon(A, B, C)')
      expect(failure).toContain('保存できません')

      // 【保存】は AI 生図の確定を呼ばない（AI の結果が入っていない作図を成功にしない）
      await view.get('[data-gm-draw-save]').trigger('click')
      await flushPromises()
      const calls = recorded(fetchMock)
      expect(calls.some((call) => call.url.endsWith('/geometry/ai/requests/31/confirm'))).toBe(false)
      expect(calls.some((call) => call.method === 'POST' && call.url.endsWith('/geometry/figures'))).toBe(false)
      expect(toastMessages().join(' ')).toContain('保存できません')
    })

    it('コマンド欄で直して【実行】できたら、そこから【保存】できる', async () => {
      api = new FakeApi()
      api.failCommands = ['Polygon(A, B, C)']
      const { wrapper: view, fetchMock } = await setup({ aiRequestId: 31 })
      // まずは AI の流し込みに失敗している（保存は止まる）
      expect(view.find('[data-gm-draw-ai-failure]').exists()).toBe(true)

      // 利用者がコマンド欄を直して【実行】する（失敗する行を消す）
      await view.get('[data-gm-draw-command]').setValue('A = (0, 0)\nB = (5, 0)')
      api.failCommands = []
      await view.get('[data-gm-draw-command-run]').trigger('click')
      await flushPromises()

      // 印が消え、案内が変わる
      expect(view.find('[data-gm-draw-ai-failure]').exists()).toBe(false)
      expect(view.get('[data-gm-draw-ai-message]').text()).toContain('手で直して実行しました')

      const before = recorded(fetchMock).length
      await view.get('[data-gm-draw-save]').trigger('click')
      await flushPromises()
      const confirm = recorded(fetchMock).slice(before)
        .find((call) => call.url.endsWith('/geometry/ai/requests/31/confirm'))
      expect(confirm).toBeDefined()
    })

    it('保存が終わったら「保存完了」にする（READY の間は保存済みと言わない）', async () => {
      api = new FakeApi()
      const { wrapper: view } = await setup({ aiRequestId: 31 })

      // 開いた直後は**まだ保存されていない**
      const before = view.get('[data-gm-draw-ai-notice]').text().replace(/\s+/g, ' ')
      expect(before).toContain('まだ保存されていません')
      expect(before).not.toContain('既に図形として保存されています')

      await view.get('[data-gm-draw-save]').trigger('click')
      await flushPromises()

      // 正式に保存できたので「保存完了」になる
      const after = view.get('[data-gm-draw-ai-notice]').text().replace(/\s+/g, ' ')
      expect(after).toContain('保存完了')
      expect(after).toContain('既に図形として保存されています')
    })

    it('READY でない要求は読み込まず、理由を案内する', async () => {
      api = new FakeApi()
      const { wrapper: view } = await setup({
        aiRequestId: 31,
        aiRequest: aiRequestDetail({
          status: 'GENERATING',
          statusLabel: 'AI が生成中',
          commands: []
        })
      })

      expect(api.evaluated).toEqual([])
      expect(view.get('[data-gm-draw-ai-message]').text()).toContain('まだ作図に使える状態ではありません')
      // どの要求を見ているかは分かるように出す（状態つき）。「保存すると AI 生図の図形になる」は出さない
      const notice = view.get('[data-gm-draw-ai-notice]').text().replace(/\s+/g, ' ')
      expect(notice).toContain('AIG202609141200001234')
      expect(notice).toContain('AI が生成中')
      expect(notice).not.toContain('AI 生図の図形')
    })
  })

  /**
   * AI 画図助手（ヘッダーの【AI 助手】→ ダイアログ）。
   * 左パネルを縦に伸ばさないよう、内容はダイアログ（`Teleport`）に置いてある。
   * 指示を送ると AI が GeoGebra コマンドの変更案を返し、【反映】で作図に適用し
   * 【破棄】で捨てる（追加／全体置き換えを選べる）。
   */
  describe('AI 画図助手', () => {
    it('【AI 助手】は【保存】と同じ行にあり、既定ではダイアログを出さない', async () => {
      const { wrapper: view } = await setup()

      const toggle = view.get('[data-gm-ai-assist-toggle]')
      expect(toggle.attributes('aria-expanded')).toBe('false')
      expect(toggle.attributes('title')).toContain('AI 画図助手を開く')
      expect(toggle.text()).toContain('AI 助手')
      // 保存と同じ行（見出しの操作列の中で、縦の位置が揃っている）
      const save = view.get('[data-gm-draw-save]').element as HTMLElement
      const assist = toggle.element as HTMLElement
      expect(Math.abs(save.getBoundingClientRect().top - assist.getBoundingClientRect().top)).toBeLessThan(6)
      // 既定は閉じている（ダイアログの要素は出さない）
      expect(document.querySelector('[data-gm-ai-assist]')).toBeNull()
      expect(document.querySelector('[data-gm-ai-assist-body]')).toBeNull()
    })

    it('設定で無効なら理由を出し、指示を送れない', async () => {
      const { wrapper: view } = await setup({
        handlers: (url: string) => (url.endsWith('/geometry/ai/options')
          ? ok(aiOptions({ enabled: false, assistEnabled: false }))
          : null)
      })
      await view.get('[data-gm-ai-assist-toggle]').trigger('click')
      await flushPromises()

      expect(dialog('[data-gm-ai-assist-badge]').textContent?.trim()).toBe('無効')
      expect((dialog('[data-gm-ai-assist-send]') as HTMLButtonElement).disabled).toBe(true)
      expect(dialog('[data-gm-ai-assist-message]').textContent).toContain('ご利用いただけません')
    })

    it('指示を送ると変更案が出て、【反映】で作図に追加する', async () => {
      api = new FakeApi()
      const { wrapper: view, fetchMock } = await setup()
      await view.get('[data-gm-ai-assist-toggle]').trigger('click')
      await flushPromises()

      // 指示が空のうちは送れない
      expect((dialog('[data-gm-ai-assist-send]') as HTMLButtonElement).disabled).toBe(true)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      expect((dialog('[data-gm-ai-assist-send]') as HTMLButtonElement).disabled).toBe(false)

      const before = api.evaluated.length
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      // 会話ログに 1 ステップ（指示・コマンド・短い状態）が積まれる
      const step = dialog('[data-gm-ai-assist-step="1"]')
      expect(step.querySelector('[data-gm-ai-assist-step-instruction="1"]')?.textContent)
        .toContain('垂線を引いて')
      const diff = step.querySelector('[data-gm-ai-assist-diff]')?.textContent ?? ''
      expect(diff).toContain('D = (0, 0)')
      expect(diff).toContain('Segment(C, D)')
      expect(step.textContent).toContain('垂線を引きます。')

      // **押さなくても**そのまま作図に入る（今の作図に追加する。全消去しない）
      expect(api.evaluated.slice(before)).toEqual(['D = (0, 0)', 'Segment(C, D)'])
      expect(api.newConstructions).toBe(0)
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('反映済み')
      // 適用の記録を残す（POST /assist/{id}/applied）
      expect(recorded(fetchMock).some((call) => call.url.endsWith('/ai/assist/12/applied'))).toBe(true)
      // 押させるボタン（反映・破棄）は置かない。押さない代わりに【戻す】で戻せる
      expect(document.querySelector('[data-gm-ai-assist-apply]')).toBeNull()
      expect(document.querySelector('[data-gm-ai-assist-discard]')).toBeNull()
      expect(document.querySelector('[data-gm-ai-assist-restore="1"]')).not.toBeNull()
    })

    /**
     * 反映は**いつも追加**（利用者の指示: 作り直したいときは自分で【全消去】する）。
     * 反映方法を選ぶ手段は画面に無く、古い記憶が残っていても作り直しは起きない。
     */
    it('指示を送っても作図を全消去しない（いつも追加で反映する）', async () => {
      api = new FakeApi()
      api.objects = ['A', 'B']
      api.commandStrings = { A: '(0, 0)', B: '(4, 0)' }
      const { wrapper: view } = await setup()
      await view.get('[data-gm-ai-assist-toggle]').trigger('click')
      await flushPromises()
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      // 押させない（案はそのまま作図へ入る）が、**作り直しはしない**
      expect(api.newConstructions).toBe(0)
      expect(api.evaluated).toEqual(['D = (0, 0)', 'Segment(C, D)'])
      expect(api.objects).toEqual(expect.arrayContaining(['A', 'B']))
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('反映済み')
    })

    /**
     * コマンドが失敗したら元の作図に戻し、**どの行が駄目だったか**をそのステップに出す。
     * 作図を変えていないので【戻す】は出さない（利用者の指示）。
     */
    it('コマンドが失敗したら元の作図に戻して理由を出し、【戻す】は出さない', async () => {
      api = new FakeApi()
      api.failCommands = ['Segment(C, D)']
      const { wrapper: view, fetchMock } = await setup()
      await view.get('[data-gm-ai-assist-toggle]').trigger('click')
      await flushPromises()
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      // 元の作図に戻す（setXML が呼ばれる）
      expect(api.xml).toContain(CONSTRUCTION)
      expect(toastMessages().join(' ')).toContain('実行できませんでした')
      // 失敗した理由（どの行か）がそのステップに出る
      const error = dialog('[data-gm-ai-assist-step-error="1"]').textContent ?? ''
      expect(error).toContain('Segment(C, D)')
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('失敗')
      // 作図を変えていないので【戻す】は出さない（押しても何も起きないボタンを置かない）
      expect(document.querySelector('[data-gm-ai-assist-restore]')).toBeNull()
      // 反映できなかった案は DB にも「使わなかった」と残す（実行中のまま放置しない）
      expect(recorded(fetchMock).some((call) => call.url.endsWith('/ai/assist/12/discarded'))).toBe(true)
      expect(recorded(fetchMock).some((call) => call.url.endsWith('/ai/assist/12/applied'))).toBe(false)
    })

    it('窓の送信先の案内を出し、反映方法の選択は置かない', async () => {
      const { wrapper: view } = await setup()
      await view.get('[data-gm-ai-assist-toggle]').trigger('click')
      await flushPromises()

      // モーダルではなく**非モーダルの窓**（開いたまま作図できる）
      expect(dialog('[data-gm-ai-assist]').getAttribute('role')).toBe('region')
      expect(dialog('[data-gm-ai-assist]').getAttribute('aria-modal')).toBeNull()
      expect(dialog('[data-gm-ai-assist-toggle]').getAttribute('aria-expanded')).toBe('true')
      // 反映方法は**追加だけ**になったので、選ぶ欄も見出しも置かない（利用者の指示）
      expect(document.querySelector('[data-gm-ai-assist-mode]')).toBeNull()
      expect(document.querySelector('.gm-assist-compose__field')).toBeNull()
      expect(dialog('[data-gm-ai-assist-body]').textContent).not.toContain('変更案の反映方法')
      // 送信先の案内は残す
      expect(dialog('[data-gm-ai-assist-note]').textContent).toContain('指示は外部 AI へ送信されます')
      // 作図タイプは API（AssistRequest）が受け取らないので画面から外した（死んだ入力は置かない）
      expect(document.querySelector('[data-gm-ai-assist-type]')).toBeNull()
      // 会話ログの外に「変更案の差分」の枠は置かない（反映は自動なので、常に空の枠は不要）
      expect(dialog('[data-gm-ai-assist-body]').textContent).not.toContain('変更案（GeoGebra コマンドの差分）')
      expect(dialog('[data-gm-ai-assist-body]').querySelector('.field__label')).toBeNull()
      // 送信先の案内は 1 行だけ（詳しい説明は title に置く）
      const note = dialog('[data-gm-ai-assist-note]')
      expect(note.textContent?.trim()).toBe('指示は外部 AI へ送信されます')
      expect(note.getAttribute('title')).toContain('外部の AI サービスへ送ります')

      // 閉じる（× と もう一度【AI 助手】）
      await dialogClick('[data-gm-ai-assist-close]')
      expect(document.querySelector('[data-gm-ai-assist]')).toBeNull()
      await view.get('[data-gm-ai-assist-toggle]').trigger('click')
      await flushPromises()
      expect(document.querySelector('[data-gm-ai-assist]')).not.toBeNull()
      await dialogClick('[data-gm-ai-assist-close]')
      expect(document.querySelector('[data-gm-ai-assist]')).toBeNull()
    })

    /** 窓の位置と大きさ（style。jsdom は実寸を返さないので style を見る）。 */
    function assistWindowBox(): { left: number; top: number; width: number; height: number } {
      const element = dialog('[data-gm-ai-assist]')
      return {
        left: parseFloat(element.style.left),
        top: parseFloat(element.style.top),
        width: parseFloat(element.style.width),
        height: parseFloat(element.style.height)
      }
    }

    /**
     * 画面のレイアウトを決めておく（jsdom は実寸を返さないので style を測れない）。
     * 左の入力欄 240..600（幅 360）、作図エリア 620..1520（高さ 700）という想定。
     */
    function stubDrawLayout(): void {
      // 窓の高さ（700）が画面に収まるように、画面の高さも決めておく
      vi.stubGlobal('innerHeight', 900)
      const rect = (left: number, top: number, width: number, height: number): DOMRect => ({
        left, top, right: left + width, bottom: top + height, width, height, x: left, y: top,
        toJSON: () => ({})
      }) as DOMRect
      const side = document.querySelector('[data-gm-draw-side]') as HTMLElement
      const frame = document.querySelector('[data-gm-draw-frame]') as HTMLElement
      Object.defineProperty(side, 'getBoundingClientRect', { value: () => rect(240, 80, 360, 700) })
      Object.defineProperty(frame, 'getBoundingClientRect', { value: () => rect(620, 80, 900, 700) })
    }

    /** 窓を開く（見出しの【AI 助手】）。 */
    async function openAssist(view: VueWrapper): Promise<void> {
      await view.get('[data-gm-ai-assist-toggle]').trigger('click')
      await flushPromises()
    }

    /**
     * モーダルではないので、窓を開いたままでも**作図の操作ができる**。
     * 背景を覆う要素（遮罩）も置かない。閉じるのは × と【AI 助手】だけ。
     */
    it('窓は背景を覆わず、開いたまま作図できる（背景クリックでは閉じない）', async () => {
      api = new FakeApi()
      const { wrapper: view } = await setup()
      await openAssist(view)

      // 遮罩（モーダルの背景）を置かない。CSS も固定配置（fixed）で、重なり順はダイアログより下
      expect(document.querySelector('.overlay')).toBeNull()
      expect(document.querySelector('[data-gm-ai-assist-overlay]')).toBeNull()
      const css = readFileSync(
        path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'src/features/geometry/geometry-ai.css'),
        'utf8'
      )
      const windowRule = /\.gm-assist-window\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
      expect(windowRule).toContain('position: fixed')
      expect(windowRule).toMatch(/z-index:\s*var\(--z-overlay\)/)

      // 窓を開いたままコマンドを実行できる
      await type(view, '[data-gm-draw-command]', 'A = (0, 4)')
      await view.get('[data-gm-draw-command-run]').trigger('click')
      await flushPromises()
      expect(api.evaluated).toContain('A = (0, 4)')
      expect(document.querySelector('[data-gm-ai-assist]')).not.toBeNull()

      // 背景（作図エリア・入力欄）を押しても閉じない
      await view.get('[data-gm-draw-frame]').trigger('click')
      await view.get('[data-gm-draw-side]').trigger('click')
      await flushPromises()
      expect(document.querySelector('[data-gm-ai-assist]')).not.toBeNull()
    })

    it('既定では左の入力欄に重なる縦長で出る（作図エリアは隠さない）', async () => {
      const { wrapper: view } = await setup()
      stubDrawLayout()
      await openAssist(view)

      // 左の入力欄の位置と幅、作図エリアの高さに合わせる（利用者の指示）
      expect(assistWindowBox()).toEqual({ left: 240, top: 80, width: 360, height: 700 })
      // 右の作図エリア（620 から）には掛からない
      const box = assistWindowBox()
      expect(box.left + box.width).toBeLessThanOrEqual(620)
    })

    /**
     * 動かせるが、**その位置は覚えない**。
     * 利用者の指示: どこへ動かして閉じても、次に開くときは左の入力欄（図形名）の位置に出す。
     */
    it('タイトルバーだけをつまんで動かせる（本文では動かない・位置は覚えない）', async () => {
      const { wrapper: view } = await setup()
      stubDrawLayout()
      await openAssist(view)
      const before = assistWindowBox()
      expect(before).toEqual({ left: 240, top: 80, width: 360, height: 700 })

      // 本文（会話ログ・入力欄）で押しても動かない
      pointer('pointerdown', dialog('[data-gm-ai-assist-body]'), 700, 300)
      pointer('pointermove', window, 500, 400)
      pointer('pointerup', window, 500, 400)
      await flushPromises()
      expect(assistWindowBox()).toEqual(before)

      // タイトルバーをつまむと動く
      pointer('pointerdown', dialog('[data-gm-ai-assist-drag]'), 700, 300)
      pointer('pointermove', window, 600, 250)
      await flushPromises()
      expect(assistWindowBox()).toEqual({ left: 140, top: 30, width: 360, height: 700 })

      // 画面の外へは出さない（内側へ寄せる）
      pointer('pointermove', window, 6000, 250)
      pointer('pointerup', window, 6000, 250)
      await flushPromises()
      expect(assistWindowBox().left).toBeLessThanOrEqual(window.innerWidth - 360 - 12)

      // **位置は覚えない**（保存するのは大きさだけ）
      const stored = JSON.parse(window.localStorage.getItem('study21.geometry.aiAssist.window.v2') ?? '{}')
      expect(stored).toEqual({ width: 360, height: 700 })

      // 開き直すと**必ず既定の位置**（左の入力欄＝図形名の位置）に出る
      await dialogClick('[data-gm-ai-assist-close]')
      expect(document.querySelector('[data-gm-ai-assist]')).toBeNull()
      await openAssist(view)
      expect(assistWindowBox()).toEqual({ left: 240, top: 80, width: 360, height: 700 })
    })

    it('古い保存値（位置つき）や壊れた値でも、既定の位置と画面内の大きさで出す', async () => {
      // 位置は覚えないので、保存に位置が入っていても**無視**する（大きさだけ使う）
      window.localStorage.setItem('study21.geometry.aiAssist.window.v2',
        JSON.stringify({ left: 5000, top: 5000, width: 4000, height: 4000 }))
      const { wrapper: view } = await setup()
      stubDrawLayout()
      await openAssist(view)

      const box = assistWindowBox()
      expect(box.width).toBe(window.innerWidth - 24)
      expect(box.height).toBe(window.innerHeight - 24)
      // 位置は既定（左の入力欄＝図形名）へ。大きさが画面いっぱいなので左端/上端に寄る
      expect(box.left).toBe(12)
      expect(box.top).toBe(12)

      // 壊れた値でも既定の位置と大きさで開く（窓が出ないことが無いように）
      await dialogClick('[data-gm-ai-assist-close]')
      window.localStorage.setItem('study21.geometry.aiAssist.window.v2', 'こわれた値')
      await openAssist(view)
      expect(assistWindowBox()).toEqual({ left: 240, top: 80, width: 360, height: 700 })
    })

    /**
     * 窓の大きさを変える（右下の把手）。最小・最大を守り、**位置は動かさない**。
     * 大きさは位置と一緒に localStorage へ覚える（開き直すと同じ大きさで出る）。
     */
    it('右下の把手で大きさを変えられる（最小・最大・記憶）', async () => {
      // 大きさだけ覚えている状態から開く（位置は既定＝左の入力欄。端に寄っているとはみ出さないようずれる）
      window.localStorage.setItem('study21.geometry.aiAssist.window.v2',
        JSON.stringify({ width: 380, height: 400 }))
      const { wrapper: view } = await setup()
      stubDrawLayout()
      await openAssist(view)
      expect(assistWindowBox()).toEqual({ left: 240, top: 80, width: 380, height: 400 })

      // 右下の把手をつまんで広げる（画面に収まる範囲では**位置を動かさない**）
      pointer('pointerdown', dialog('[data-gm-ai-assist-resize]'), 900, 600)
      pointer('pointermove', window, 1020, 680)
      await flushPromises()
      expect(assistWindowBox()).toEqual({ left: 240, top: 80, width: 500, height: 480 })

      // 大きさも覚える
      pointer('pointerup', window, 1020, 680)
      await flushPromises()
      expect(JSON.parse(window.localStorage.getItem('study21.geometry.aiAssist.window.v2') ?? '{}'))
        .toEqual({ width: 500, height: 480 })

      // 開き直すと**大きさは同じ・位置は既定**（左の入力欄＝図形名）
      await dialogClick('[data-gm-ai-assist-close]')
      await openAssist(view)
      expect(assistWindowBox()).toEqual({ left: 240, top: 80, width: 500, height: 480 })

      // 小さくしすぎても最小（320×320）で止まる
      pointer('pointerdown', dialog('[data-gm-ai-assist-resize]'), 900, 600)
      pointer('pointermove', window, -100, -100)
      await flushPromises()
      expect(assistWindowBox()).toMatchObject({ width: 320, height: 320 })

      // 大きくしすぎても画面の中まで（視口からはみ出さない）
      pointer('pointermove', window, 9000, 9000)
      pointer('pointerup', window, 9000, 9000)
      await flushPromises()
      const box = assistWindowBox()
      expect(box.width).toBe(window.innerWidth - 24)
      expect(box.height).toBe(window.innerHeight - 24)
      expect(box.left).toBe(12)
      expect(box.top).toBe(12)
    })

    it('画面の大きさが変わっても、窓ははみ出さない', async () => {
      const { wrapper: view } = await setup()
      await openAssist(view)
      // 大きく広げてから、画面を小さくする
      pointer('pointerdown', dialog('[data-gm-ai-assist-resize]'), 900, 600)
      pointer('pointermove', window, 1400, 1100)
      pointer('pointerup', window, 1400, 1100)
      await flushPromises()

      vi.stubGlobal('innerWidth', 500)
      vi.stubGlobal('innerHeight', 400)
      window.dispatchEvent(new Event('resize'))
      await flushPromises()

      const box = assistWindowBox()
      expect(box.width).toBeLessThanOrEqual(500 - 24)
      expect(box.height).toBeLessThanOrEqual(400 - 24)
      expect(box.left).toBeGreaterThanOrEqual(12)
      expect(box.top).toBeGreaterThanOrEqual(12)
      expect(box.left + box.width).toBeLessThanOrEqual(500 - 12 + 1)
      expect(box.top + box.height).toBeLessThanOrEqual(400 - 12 + 1)
    })

    it('実行中は「考えています…」を出し、送れないようにする', async () => {
      const { wrapper: view } = await setup()
      await openAssist(view)
      // 状態が返らない状態にする（実行中の見た目を確かめる）
      vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => {})))
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await flushPromises()

      // 依頼は作れない（fetch が返らない）ので、実行中のステップはできない。
      // ここでは「送信中は押せない」ことだけを確かめる。
      expect((dialog('[data-gm-ai-assist-send]') as HTMLButtonElement).disabled).toBe(false)

      // 依頼作成だけ通して、ポーリングが返らない状態にする
      vi.unstubAllGlobals()
      const pending = vi.fn(async (url: string, init?: RequestInit) => {
        const target = String(url)
        const method = (init?.method ?? 'GET').toUpperCase()
        if (method === 'POST' && target.endsWith('/geometry/ai/assist')) {
          return ok({
            assistId: 12, status: 'PENDING', statusLabel: '依頼受付（AI 実行待ち）',
            runPath: '/api/admin/batch/geometry-assist/12/run'
          })
        }
        if (method === 'GET' && target.endsWith('/geometry/ai/assist/12')) {
          // ずっと実行中のまま（AI が考えている状態）
          return ok({
            assistId: 12, status: 'PENDING', statusLabel: 'AI 実行中', commands: [],
            description: null, errorCode: null, errorMessage: null, commandCount: 0
          })
        }
        return ok({})
      })
      vi.stubGlobal('fetch', pending)

      dialog('[data-gm-ai-assist-send]').click()
      await settleAssist()

      const sending = dialog('[data-gm-ai-assist-sending]')
      expect(sending.textContent).toContain('垂線を引いて')
      expect(sending.textContent).toContain('考えています…')
      expect(dialog('[data-gm-ai-assist-send]').textContent).toContain('考えています…')
      expect((dialog('[data-gm-ai-assist-send]') as HTMLButtonElement).disabled).toBe(true)
      // 実行中のステップは 1 つだけ（コマンドはまだ無い）
      expect(document.querySelectorAll('[data-gm-ai-assist-step]')).toHaveLength(1)
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('考えています…')
      expect(document.querySelector('[data-gm-ai-assist-apply]')).toBeNull()
    })

    it('失敗したら理由をそのステップに出し、指示は残す', async () => {
      const { wrapper: view } = await setup({
        handlers: (url: string) => (url.endsWith('/geometry/ai/assist')
          ? failure('AI に接続できませんでした。時間をおいて試してください。', 500)
          : null)
      })
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      expect(dialog('[data-gm-ai-assist-step-instruction="1"]').textContent).toContain('垂線を引いて')
      expect(dialog('[data-gm-ai-assist-step-error="1"]').textContent).toContain('AI に接続できませんでした')
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('失敗')
      // 失敗したステップには【反映】も【破棄】も出さない
      expect(document.querySelector('[data-gm-ai-assist-apply]')).toBeNull()
      expect(document.querySelector('[data-gm-ai-assist-discard]')).toBeNull()
      // 指示は入力欄に残す（押し直せるように）
      expect((dialog('[data-gm-ai-assist-instruction]') as HTMLTextAreaElement).value).toBe('垂線を引いて')
    })

    /** AI の説明は既定で 2 行に畳む（長いときだけ「詳細」で開く。利用者の指示）。 */
    it('AI の説明は既定で畳み、「詳細」で開ける', async () => {
      const long = 'この指示では、まず点 E を作り、次に線分 EF を引きます。'
        + 'さらに補助線として直線 AB に垂直な線を足し、交点にラベルを付けます。'
        + '座標は自動で合わせるので、必要ならあとで動かしてください。'
      const { wrapper: view } = await setup({
        handlers: (url: string, method: string) => (method === 'GET' && url.endsWith('/geometry/ai/assist/12')
          ? ok({
              assistId: 12, status: 'READY', statusLabel: 'できました',
              commands: ['D = (0, 0)'], description: long,
              errorCode: null, errorMessage: null, commandCount: 1
            })
          : null)
      })
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      const description = dialog('.gm-assist-step__description')
      expect(description.textContent).toContain('補助線')
      // 既定は畳んだ状態（CSS で 2 行に切る）
      expect(description.classList.contains('is-clamped')).toBe(true)
      const detail = dialog('[data-gm-ai-assist-detail="1"]')
      expect(detail.getAttribute('aria-expanded')).toBe('false')

      await dialogClick('[data-gm-ai-assist-detail="1"]')
      expect(dialog('.gm-assist-step__description').classList.contains('is-clamped')).toBe(false)
      expect(dialog('[data-gm-ai-assist-detail="1"]').getAttribute('aria-expanded')).toBe('true')

      await dialogClick('[data-gm-ai-assist-detail="1"]')
      expect(dialog('.gm-assist-step__description').classList.contains('is-clamped')).toBe(true)

      // 畳む CSS は tokens の変数だけを使う（生の色を書かない）
      const css = readFileSync(
        path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'src/features/geometry/geometry-ai.css'),
        'utf8'
      )
      expect(css).toMatch(/\.gm-assist-step__description\.is-clamped\s*\{[^}]*-webkit-line-clamp:\s*2/)
    })

    it('説明が短いときは「詳細」を出さない（余計な操作を増やさない）', async () => {
      const { wrapper: view } = await setup()
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      expect(dialog('.gm-assist-step__description').textContent).toContain('垂線を引きます。')
      expect(document.querySelector('[data-gm-ai-assist-detail]')).toBeNull()
    })

    /**
     * AI の実行が失敗したら、理由をそのステップに出す（依頼は作れている）。
     * 状態は `GET /assist/{id}` の `errorMessage` を使う。
     */
    it('AI の実行が失敗したら、その理由をステップに出す', async () => {
      api = new FakeApi()
      const { wrapper: view } = await setup({
        handlers: (url: string, method: string) => (method === 'GET' && url.endsWith('/geometry/ai/assist/12')
          ? ok({
              assistId: 12, status: 'FAILED', statusLabel: '失敗しました', commands: [],
              description: null, errorCode: 'AI_TIMEOUT',
              errorMessage: 'AI の応答が時間内に返りませんでした。', commandCount: 0
            })
          : null)
      })
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      expect(dialog('[data-gm-ai-assist-step-error="1"]').textContent)
        .toContain('AI の応答が時間内に返りませんでした')
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('失敗')
      expect(document.querySelector('[data-gm-ai-assist-apply]')).toBeNull()
      // 作図は変わっていない
      expect(api.evaluated).toEqual([])
    })

    /**
     * 実行（admin-api の run）は**依頼ごとに 1 回だけ**。
     * ポーリングが何度走っても、同じ `assistId` を二度走らせない。
     */
    it('同じ依頼の実行を二度走らせない（ポーリングが何回でも 1 回）', async () => {
      vi.useFakeTimers()
      try {
      api = new FakeApi()
      let polls = 0
      const { wrapper: view, fetchMock } = await setup({
        handlers: (url: string, method: string) => {
          if (method === 'GET' && url.endsWith('/geometry/ai/assist/12')) {
            polls += 1
            // 最初の 2 回は実行中、3 回目で READY（＝ポーリングが複数回走る状況を作る）
            return polls < 3
              ? ok({
                  assistId: 12, status: 'PENDING', statusLabel: 'AI 実行中', commands: [],
                  description: null, errorCode: null, errorMessage: null, commandCount: 0
                })
              : ok({
                  assistId: 12, status: 'READY', statusLabel: 'できました',
                  commands: ['D = (0, 0)'], description: '垂線を引きます。',
                  errorCode: null, errorMessage: null, commandCount: 1
                })
          }
          return null
        }
      })
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      // 実行中は「考えています…」
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('考えています…')

      // ポーリングの間隔を進めて READY にする（間隔は fake timers で作っている）
      await vi.advanceTimersByTimeAsync(2000)
      await flushPromises()
      await vi.advanceTimersByTimeAsync(2000)
      await flushPromises()
      await settleAssist()

      // 案が返ったらそのまま反映する（押させない）
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('反映済み')
      expect(api.evaluated).toContain('D = (0, 0)')
      const runs = recorded(fetchMock).filter(
        (call) => call.method === 'POST' && call.url === '/api/admin/batch/geometry-assist/12/run')
      expect(runs).toHaveLength(1)
      expect(polls).toBeGreaterThanOrEqual(3)
      } finally {
        vi.useRealTimers()
      }
    })

    /** 3 分たっても結果が出なければ「時間がかかっています」と出し、送り直せる。 */
    it('時間がかかりすぎたら案内を出して、送り直せるようにする', async () => {
      // Date も含めて偽物にする（時間切れの判定に Date.now() を使っている）
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'Date'] })
      try {
        api = new FakeApi()
        const { wrapper: view } = await setup({
          handlers: (url: string, method: string) => (method === 'GET' && url.endsWith('/geometry/ai/assist/12')
            ? ok({
                assistId: 12, status: 'PENDING', statusLabel: 'AI 実行中', commands: [],
                description: null, errorCode: null, errorMessage: null, commandCount: 0
              })
            : null)
        })
        // 間隔・タイムアウトは fake timers で作る（下で進める）
        await openAssist(view)
        await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
        await dialogClick('[data-gm-ai-assist-send]')
        await settleAssist()

        // ポーリングの間隔ごとに進める（まとめて進めると、途中のマイクロタスクが
        // 処理されないまま時計だけ進むことがある）
        for (let index = 0; index < 130; index += 1) {
          await vi.advanceTimersByTimeAsync(1500)
          await flushPromises()
        }
        await settleAssist()

        expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('失敗')
        expect(dialog('[data-gm-ai-assist-step-error="1"]').textContent).toContain('時間がかかっています')
        // 送り直せる（入力は残っている）
        await dialogType('[data-gm-ai-assist-instruction]', 'もう一度')
        expect((dialog('[data-gm-ai-assist-send]') as HTMLButtonElement).disabled).toBe(false)
      } finally {
        vi.useRealTimers()
      }
    })

    /**
     * 依頼が消えている（404）ときは、**やり直さずにすぐ失敗**にする。
     * 前に开いた画面の履歴が残っていて、依頼が消えている場合がこれにあたる。
     */
    it('依頼が消えていたら（404）すぐ失敗にして、ポーリングを止める', async () => {
      api = new FakeApi()
      let polls = 0
      const { wrapper: view, fetchMock } = await setup({
        handlers: (url: string, method: string) => {
          if (method === 'GET' && url.endsWith('/geometry/ai/assist/12')) {
            polls += 1
            return failure('依頼が見つかりません。', 404)
          }
          return null
        }
      })
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      // 1 回の問い合わせで失敗が確定する（3 回リトライしない）
      expect(polls).toBe(1)
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('失敗')
      expect(dialog('[data-gm-ai-assist-step-error="1"]').textContent).toContain('依頼が見つかりません')

      // そのあと間隔が来ても問い合わせない（ポーリングが止まっている）
      vi.useFakeTimers()
      try {
        await vi.advanceTimersByTimeAsync(5000)
        await flushPromises()
      } finally {
        vi.useRealTimers()
      }
      expect(polls).toBe(1)
      expect(recorded(fetchMock).filter(
        (call) => call.method === 'GET' && call.url.endsWith('/geometry/ai/assist/12'))).toHaveLength(1)
    })

    /** 通信エラーは一時的なことがあるので、3 回続けて失敗したら案内を出す。 */
    it('通信エラーが続いたら 3 回で案内を出す', async () => {
      // ポーリングの間隔を fake timers で作る（下で進める）
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'Date'] })
      try {
      api = new FakeApi()
      let polls = 0
      const { wrapper: view } = await setup({
        handlers: (url: string, method: string) => {
          if (method === 'GET' && url.endsWith('/geometry/ai/assist/12')) {
            polls += 1
            return failure('サーバーに接続できません。', 500)
          }
          return null
        }
      })
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()
      expect(polls).toBe(1)
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('考えています…')

      await vi.advanceTimersByTimeAsync(1600)
      await flushPromises()
      await vi.advanceTimersByTimeAsync(1600)
      await flushPromises()
      await settleAssist()

      expect(polls).toBeGreaterThanOrEqual(3)
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('失敗')
      expect(dialog('[data-gm-ai-assist-step-error="1"]').textContent).toContain('サーバーに接続できません')
      } finally {
        vi.useRealTimers()
      }
    })

    /**
     * リロード後に実行中（PENDING）だった依頼は、状態を取り直す。
     * READY になったらコマンドが出て【反映】できる（**作図は勝手に変えない**）。
     */
    it('リロード後に実行中だった依頼は状態を取り直す（図形の履歴として残る）', async () => {
      let ready = false
      api = new FakeApi()
      const { wrapper: view } = await setup({
        figureId: 5,
        handlers: (url: string, method: string) => (method === 'GET' && url.endsWith('/geometry/ai/assist/12')
          ? (ready
              ? ok({
                  assistId: 12, status: 'READY', statusLabel: 'できました',
                  commands: ['D = (0, 0)'], description: '垂線を引きます。',
                  errorCode: null, errorMessage: null, commandCount: 1
                })
              : ok({
                  assistId: 12, status: 'PENDING', statusLabel: 'AI 実行中', commands: [],
                  description: null, errorCode: null, errorMessage: null, commandCount: 0
                }))
          : null)
      })
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('考えています…')

      // 実行中のまま画面を開き直す（図形の履歴として localStorage に残っている）
      view.unmount()
      ready = true
      const second = await setup({
        figureId: 5,
        handlers: (url: string, method: string) => (method === 'GET' && url.endsWith('/geometry/ai/assist/12')
          ? ok({
              assistId: 12, status: 'READY', statusLabel: 'できました',
              commands: ['D = (0, 0)'], description: '垂線を引きます。',
              errorCode: null, errorMessage: null, commandCount: 1
            })
          : null)
      })
      await openAssist(second.wrapper)
      await settleAssist()

      // 取り直して、案が返れば**送信したときと同じように自動で反映する**
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('反映済み')
      expect(dialog('[data-gm-ai-assist-diff]').textContent).toContain('D = (0, 0)')
      expect(api.evaluated).toEqual(['D = (0, 0)'])
      // 実行は二度走らせない（同じ assistId）
      const runs = recorded(second.fetchMock).filter(
        (call) => call.method === 'POST' && call.url === '/api/admin/batch/geometry-assist/12/run')
      expect(runs).toHaveLength(0)
    })

    /** 【戻す】はその指示を出す前の作図に戻す（反映が自動なので、押すと本当に取り消せる）。 */
    it('【戻す】でその指示を出す前の作図に戻り、1 手だけ取り消せる', async () => {
      api = new FakeApi()
      const { wrapper: view } = await setup()
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()
      // 反映したあとに作図が進んだことにする
      const applied = '<construction><element type="line"/><element type="point"/></construction>'
      api.xml = applied

      await dialogClick('[data-gm-ai-assist-restore="1"]')

      // 「垂線を引いて」を出す前の作図に戻る
      expect(api.xml).toContain(CONSTRUCTION)
      expect(api.xml).not.toContain(applied)
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('反映済み')

      // 戻す前の作図にも戻せる（1 手だけ）
      await dialogClick('[data-gm-ai-assist-unrestore]')
      expect(api.xml).toContain(applied)
      expect(document.querySelector('[data-gm-ai-assist-unrestore]')).toBeNull()
    })

    /**
     * 指示は毎回**そのまま**反映される（押させない。利用者の指示）。
     * 【戻す】はその指示を出す前へ戻す＝**その指示と、そのあとの変更が作図から消える**。
     */
    it('2 つ目の指示も自動で反映され、【戻す】で前の作図に戻せる', async () => {
      api = new FakeApi()
      const { wrapper: view, fetchMock } = await setup()
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()
      expect(api.evaluated).toEqual(['D = (0, 0)', 'Segment(C, D)'])
      // 反映されて作図が変わったことにする（実機の applet はそうなる）
      const afterFirst = '<construction><element type="line"/></construction>'
      api.xml = afterFirst

      // 2 つ目も押さずに反映される
      await dialogType('[data-gm-ai-assist-instruction]', 'もう一本引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()
      expect(api.evaluated).toHaveLength(4)
      expect(dialog('[data-gm-ai-assist-step-status="2"]').textContent).toContain('反映済み')

      // 2 つ目の【戻す】＝ 2 つ目の指示を出す前（1 つ目を反映したあと）へ戻す
      await dialogClick('[data-gm-ai-assist-restore="2"]')
      expect(api.xml).toContain(afterFirst)

      // 1 つ目の【戻す】＝ 1 つ目の指示を出す前へ戻す（1 つ目も取り消し）
      await dialogClick('[data-gm-ai-assist-restore="1"]')
      expect(api.xml).toContain(CONSTRUCTION)
      expect(api.xml).not.toContain(afterFirst)
      // 戻した時点より後ろのステップは「畳んだ」印が付く
      expect(dialog('[data-gm-ai-assist-step="2"]').className).toContain('is-superseded')
      // 戻す操作だけで DB の記録は動かさない（反映済みのまま）
      expect(recorded(fetchMock).filter((call) => call.url.endsWith('/discarded'))).toHaveLength(0)
    })

    /**
     * 古い履歴に残っていた「未反映」も、状態を取り直して**自動で反映する**
     * （押せないままの案を残さない）。
     */
    it('古い履歴の「未反映」も状態を取り直して自動で反映する', async () => {
      window.localStorage.setItem('study21.geometry.aiAssist.log.figure-5', JSON.stringify([
        {
          instruction: '前の指示', mode: 'append', commands: ['D = (0, 0)'], description: null,
          assistId: 12, status: 'pending', error: null, snapshot: CONSTRUCTION, superseded: false
        }
      ]))
      api = new FakeApi()
      const { wrapper: view } = await setup({ figureId: 5 })
      await openAssist(view)
      await settleAssist()

      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('反映済み')
      expect(api.evaluated).toContain('D = (0, 0)')
    })

    /** 依頼そのものが残っていない古い「未反映」は、反映できなかったと案内する（押せないボタンは置かない）。 */
    it('依頼が残っていない古い「未反映」は、もう一度送るように案内する', async () => {
      window.localStorage.setItem('study21.geometry.aiAssist.log.figure-5', JSON.stringify([
        {
          instruction: '前の指示', mode: 'append', commands: ['D = (0, 0)'], description: null,
          assistId: null, status: 'pending', error: null, snapshot: CONSTRUCTION, superseded: false
        }
      ]))
      api = new FakeApi()
      const { wrapper: view } = await setup({ figureId: 5 })
      await openAssist(view)

      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('失敗')
      expect(dialog('[data-gm-ai-assist-step-error="1"]').textContent).toContain('もう一度【送信】')
      expect(document.querySelector('[data-gm-ai-assist-restore]')).toBeNull()
      expect(document.querySelector('[data-gm-ai-assist-apply]')).toBeNull()
      // 作図は変えない
      expect(api.evaluated).toEqual([])
    })

    /** 失敗の記録（破棄）に失敗しても、画面は普通に使える（記録は補助）。 */
    it('失敗の記録に失敗しても、画面は使える', async () => {
      api = new FakeApi()
      api.failCommands = ['Segment(C, D)']
      const { wrapper: view } = await setup({
        handlers: (url: string, method: string) =>
          (method === 'POST' && url.endsWith('/ai/assist/12/discarded')
            ? failure('記録できませんでした。', 500)
            : null)
      })
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('失敗')
      // 指示を送り直せる（画面は止まらない）
      await dialogType('[data-gm-ai-assist-instruction]', 'もう一度')
      expect((dialog('[data-gm-ai-assist-send]') as HTMLButtonElement).disabled).toBe(false)
    })

    /**
     * AI の結果が**アプレットの準備より先**に返ることがある（画面を開いた直後の指示）。
     * 反映は自動なので押せない。準備できるまで待ってから反映する（案を捨てない）。
     */
    it('アプレットの準備前に案が返っても、準備を待って反映する', async () => {
      vi.useFakeTimers()
      try {
        api = new FakeApi()
        FakeApplet.holdOnLoad = true
        const { wrapper: view } = await setup()
        await openAssist(view)
        await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
        await dialogClick('[data-gm-ai-assist-send]')
        await settleAssist()

        // まだアプレットが無いので反映できない（待っている。押させるボタンは出さない）
        expect(api.evaluated).toEqual([])
        expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('未反映')
        expect(document.querySelector('[data-gm-ai-assist-apply]')).toBeNull()

        // 準備ができたら、待っていた案を反映する
        FakeApplet.holdOnLoad = false
        applet().releaseOnLoad()
        await vi.advanceTimersByTimeAsync(300)
        await flushPromises()

        expect(api.evaluated).toEqual(['D = (0, 0)', 'Segment(C, D)'])
        expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('反映済み')
      } finally {
        FakeApplet.holdOnLoad = false
        vi.useRealTimers()
      }
    })

    /**
     * 履歴は**図形ごと**（利用者の指示）。
     * ・編集で開いた図形 … その図形の履歴として残り、開き直しても読める
     * ・読み込んだだけでは**作図を変えない**（【戻す】を押したときだけ戻す）
     */
    it('編集で開いた図形の履歴は残り、開き直しても読める（作図は変えない）', async () => {
      api = new FakeApi()
      const first = await setup({ figureId: 5 })
      await openAssist(first.wrapper)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()
      const stored = window.localStorage.getItem('study21.geometry.aiAssist.log.figure-5') ?? ''
      expect(stored).toContain('垂線を引いて')

      // 画面を開き直す（同じ localStorage のまま、新しいマウント）
      first.wrapper.unmount()
      api = new FakeApi()
      const second = await setup({ figureId: 5 })
      await openAssist(second.wrapper)

      // 前のやり取りが残っている
      expect(dialog('[data-gm-ai-assist-step-instruction="1"]').textContent).toContain('垂線を引いて')
      expect(dialog('[data-gm-ai-assist-diff]').textContent).toContain('D = (0, 0)')
      // 読み込んだだけでは作図を変えない
      expect(api.evaluated).toEqual([])
      // 読み込んだステップから【戻す】で作図を戻せる
      await dialogClick('[data-gm-ai-assist-restore="1"]')
      expect(api.xml).toContain(CONSTRUCTION)
    })

    /**
     * 新規作成のあいだは履歴を覚えない（利用者の指示: 図形を閉じて【新規】を押したら助手は空白）。
     * 保存して図形になった時点で、その図形の履歴として引き継ぐ（次に編集で開くと出る）。
     */
    it('新規作成では履歴を覚えず、保存するとその図形の履歴になる', async () => {
      api = new FakeApi()
      const first = await setup()
      await openAssist(first.wrapper)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      // 覚えない（どこにも保存しない）
      expect(Object.keys(window.localStorage).filter((key) => key.startsWith('study21.geometry.aiAssist.log.')))
        .toEqual([])

      // 【保存】で図形になる（モックは figureId 9 を返す）→ その図形の履歴として残る
      await first.wrapper.get('[data-gm-draw-save]').trigger('click')
      await flushPromises()
      const stored = window.localStorage.getItem('study21.geometry.aiAssist.log.figure-9') ?? ''
      expect(stored).toContain('垂線を引いて')

      // 図形を閉じて【新規】を押したのと同じ状態（新しいマウント）では空白
      first.wrapper.unmount()
      api = new FakeApi()
      const second = await setup()
      await openAssist(second.wrapper)
      expect(document.querySelector('[data-gm-ai-assist-step]')).toBeNull()
    })

    it('履歴は直近 20 歩まで（古いものから捨てる）', async () => {
      const steps = Array.from({ length: 25 }, (_, index) => ({
        instruction: `指示${index + 1}`,
        mode: 'append',
        commands: ['D = (0, 0)'],
        description: null,
        assistId: null,
        status: 'applied',
        error: null,
        snapshot: CONSTRUCTION,
        superseded: false
      }))
      window.localStorage.setItem('study21.geometry.aiAssist.log.figure-5', JSON.stringify(steps))
      const { wrapper: view } = await setup({ figureId: 5 })
      await openAssist(view)

      const shown = [...document.querySelectorAll('[data-gm-ai-assist-step-instruction]')]
        .map((element) => element.textContent?.trim() ?? '')
      expect(shown).toHaveLength(20)
      expect(shown).not.toContain('指示1')
      expect(shown).not.toContain('指示5')
      expect(shown[0]).toBe('指示6')
      expect(shown[19]).toBe('指示25')

      // さらに送ると、いちばん古いものが落ちて 20 歩のまま
      await dialogType('[data-gm-ai-assist-instruction]', '新しい指示')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()
      const after = JSON.parse(window.localStorage.getItem('study21.geometry.aiAssist.log.figure-5') ?? '[]')
      expect(after).toHaveLength(20)
      expect(after[19].instruction).toBe('新しい指示')
      expect(after[0].instruction).toBe('指示7')
    })

    /**
     * 端末に覚えられない大きさの作図でも、**サーバーに戻り先があれば【戻す】できる**。
     * 端末には保存しない（文字だけ残す）が、押したときにサーバーから 1 件だけ取ってくる。
     */
    it('大きすぎる作図は端末に覚えず、サーバーの指示前XMLで戻せる', async () => {
      api = new FakeApi()
      const huge = `<construction>${'x'.repeat(200_001)}</construction>`
      const { wrapper: view, fetchMock } = await setup({
        figureId: 5,
        handlers: (url: string, method: string) => (method === 'GET' && url.endsWith('/geometry/ai/assist/12')
          ? ok({
              assistId: 12, status: 'READY', statusLabel: 'できました',
              commands: ['D = (0, 0)', 'Segment(C, D)'], description: '垂線を引きます。',
              errorCode: null, errorMessage: null, commandCount: 2, beforeXml: huge
            })
          : null)
      })
      // 図形を読み込んだ**あと**に巨大な作図へ差し替える（読み込みで上書きされないように）
      api.xml = huge
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      const stored = JSON.parse(window.localStorage.getItem('study21.geometry.aiAssist.log.figure-5') ?? '[]')
      // 端末には覚えない（容量が限られるため）。文字（指示・コマンド）は残る
      expect(stored[0].snapshot).toBeNull()
      expect(stored[0].snapshotOmitted).toBe(true)
      expect(stored[0].instruction).toBe('垂線を引いて')
      expect(stored[0].commands).toEqual(['D = (0, 0)', 'Segment(C, D)'])
      // 「戻せない」とは案内しない（サーバーに戻り先がある）
      expect(document.querySelector('[data-gm-ai-assist-step-no-snapshot]')).toBeNull()
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('反映済み')

      // 【戻す】はサーバーの指示前XMLを取ってくる
      await dialogClick('[data-gm-ai-assist-restore="1"]')
      await settleAssist()
      expect(recorded(fetchMock).some(
        (call) => call.method === 'GET' && call.url.endsWith('/geometry/ai/assist/12'))).toBe(true)
      expect(api.xml).toContain(huge.slice(0, 50))
    })

    /** サーバーの上限すら超える作図は戻り先にできない（切られた XML を読み込むと作図が壊れる）。 */
    it('サーバーの上限を超える作図は戻せないと案内する', async () => {
      api = new FakeApi()
      const { wrapper: view } = await setup({ figureId: 5 })
      api.xml = `<construction>${'x'.repeat(2_000_001)}</construction>`
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      expect(document.querySelector('[data-gm-ai-assist-restore]')).toBeNull()
      expect(dialog('[data-gm-ai-assist-step-no-snapshot="1"]').textContent)
        .toContain('大きすぎるため戻せません')
    })

    it('壊れた履歴でも画面は使える（機能を止めない）', async () => {
      window.localStorage.setItem('study21.geometry.aiAssist.log.figure-5', '{壊れた JSON')
      const { wrapper: view } = await setup({ figureId: 5 })
      await openAssist(view)

      // 履歴は空として扱い、送信は普通にできる
      expect(document.querySelector('[data-gm-ai-assist-step]')).toBeNull()
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('反映済み')

      // 配列でない値・関係ない値でも落ちない
      await dialogClick('[data-gm-ai-assist-close]')
      window.localStorage.setItem('study21.geometry.aiAssist.log.figure-5', '{"a":1}')
      await openAssist(view)
      expect(document.querySelector('[data-gm-ai-assist-step]')).toBeNull()
    })

    /** サーバーの履歴 1 件（既定は「反映済み」）。 */
    function historyItem(overrides: Record<string, unknown> = {}): Record<string, unknown> {
      return {
        assistId: 12,
        instruction: '三角形を書いて',
        mode: 'APPEND',
        status: 'READY',
        statusLabel: 'できました',
        applyKind: 'APPLIED',
        commands: ['A = (0, 0)'],
        description: '三角形を書きます。',
        errorCode: null,
        errorMessage: null,
        commandCount: 1,
        beforeXmlAvailable: true,
        createdAt: '2026-09-16T10:00:00',
        updatedAt: '2026-09-16T10:01:00',
        ...overrides
      }
    }

    /** サーバーの履歴を返すハンドラ（図形ごとの履歴の GET）。 */
    function historyHandler(items: Record<string, unknown>[]): (url: string, method: string) => Response | null {
      return (url: string, method: string) => (method === 'GET' && url.includes('/geometry/ai/assist?')
        ? ok({ items, count: items.length, limit: 20 })
        : null)
    }

    /**
     * 履歴はサーバー（DB）から読む（利用者の指示: 端末をまたいで見えるように）。
     * この端末に何も残っていなくても、別の端末で出した指示がそのまま出る。
     */
    it('別の端末でも履歴が見える（サーバーの履歴を会話ログに出す）', async () => {
      api = new FakeApi()
      const { wrapper: view, fetchMock } = await setup({
        figureId: 5,
        handlers: historyHandler([
          historyItem(),
          historyItem({
            assistId: 14,
            instruction: '垂線を引いて',
            applyKind: 'REJECTED',
            commands: ['線分(A, B)'],
            description: null,
            errorMessage: 'AI のコマンドを実行できませんでした：線分(A, B)'
          })
        ])
      })
      await openAssist(view)
      await settleAssist()

      // 図形の履歴として 1 回だけ読む（古い順に並べる）
      expect(recorded(fetchMock).filter(
        (call) => call.method === 'GET' && call.url.includes('/geometry/ai/assist?'))).toHaveLength(1)
      expect(dialog('[data-gm-ai-assist-step-instruction="1"]').textContent).toContain('三角形を書いて')
      expect(dialog('[data-gm-ai-assist-step-instruction="2"]').textContent).toContain('垂線を引いて')
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('反映済み')
      // 反映できなかった行は理由つきの失敗として出る（端末をまたいでも同じ理由が見える）
      expect(dialog('[data-gm-ai-assist-step-status="2"]').textContent).toContain('失敗')
      expect(dialog('[data-gm-ai-assist-step-error="2"]').textContent).toContain('線分(A, B)')
      // 履歴を出すだけ（作図は勝手に変えない）
      expect(api.evaluated).toEqual([])
    })

    /**
     * 端末に作図データが無くても【戻す】できる（サーバーの `指示前XML` を 1 件だけ取る）。
     * 別の端末で出した指示でも取り消せるようにするため。
     */
    it('別の端末で出した指示でも【戻す】できる（サーバーの指示前XMLを使う）', async () => {
      api = new FakeApi()
      const remote = '<construction><element type="segment"/></construction>'
      const { wrapper: view, fetchMock } = await setup({
        figureId: 5,
        handlers: (url: string, method: string) => {
          if (method === 'GET' && url.includes('/geometry/ai/assist?')) {
            return ok({ items: [historyItem()], count: 1, limit: 20 })
          }
          if (method === 'GET' && url.endsWith('/geometry/ai/assist/12')) {
            return ok({
              assistId: 12, status: 'READY', statusLabel: 'できました',
              commands: ['A = (0, 0)'], description: '三角形を書きます。',
              errorCode: null, errorMessage: null, commandCount: 1, beforeXml: remote
            })
          }
          return null
        }
      })
      await openAssist(view)
      await settleAssist()

      // 戻り先はサーバーにある（この端末には無い）
      await dialogClick('[data-gm-ai-assist-restore="1"]')
      await settleAssist()

      expect(recorded(fetchMock).some(
        (call) => call.method === 'GET' && call.url.endsWith('/geometry/ai/assist/12'))).toBe(true)
      expect(api.xml).toContain(remote)
      // 戻す前の作図にも戻せる（1 手だけ）
      await dialogClick('[data-gm-ai-assist-unrestore]')
      expect(api.xml).toContain(CONSTRUCTION)
    })

    /** 端末にスナップショットがあれば、サーバーへは取りに行かない（速い方を使う）。 */
    it('端末に作図データがあれば、サーバーへ取りに行かない', async () => {
      api = new FakeApi()
      window.localStorage.setItem('study21.geometry.aiAssist.log.figure-5', JSON.stringify([
        {
          instruction: '三角形を書いて', mode: 'append', commands: ['A = (0, 0)'], description: null,
          assistId: 12, status: 'applied', error: null, snapshot: CONSTRUCTION, superseded: false
        }
      ]))
      const { wrapper: view, fetchMock } = await setup({
        figureId: 5,
        handlers: historyHandler([historyItem()])
      })
      await openAssist(view)
      await settleAssist()

      await dialogClick('[data-gm-ai-assist-restore="1"]')
      await settleAssist()

      expect(recorded(fetchMock).some(
        (call) => call.method === 'GET' && call.url.endsWith('/geometry/ai/assist/12'))).toBe(false)
      expect(api.xml).toContain(CONSTRUCTION)
    })

    /** サーバーの履歴が取れなくても、端末の履歴で動く（画面は止めない）。 */
    it('サーバーの履歴が取れなくても、端末の履歴で動く', async () => {
      api = new FakeApi()
      window.localStorage.setItem('study21.geometry.aiAssist.log.figure-5', JSON.stringify([
        {
          instruction: '端末の指示', mode: 'append', commands: ['A = (0, 0)'], description: null,
          assistId: 12, status: 'applied', error: null, snapshot: CONSTRUCTION, superseded: false
        }
      ]))
      const { wrapper: view } = await setup({
        figureId: 5,
        handlers: (url: string, method: string) => (method === 'GET' && url.includes('/geometry/ai/assist?')
          ? failure('サーバーに接続できません。', 500)
          : null)
      })
      await openAssist(view)
      await settleAssist()

      expect(dialog('[data-gm-ai-assist-step-instruction="1"]').textContent).toContain('端末の指示')
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('反映済み')
    })

    /** 新規作成（まだ図形になっていない）は図形の履歴を引かない＝助手は空白。 */
    it('新規作成ではサーバーの履歴を読まない', async () => {
      const { wrapper: view, fetchMock } = await setup()
      await openAssist(view)
      await settleAssist()

      expect(recorded(fetchMock).some(
        (call) => call.method === 'GET' && call.url.includes('/geometry/ai/assist?'))).toBe(false)
      expect(document.querySelector('[data-gm-ai-assist-step]')).toBeNull()
    })

    /** 反映できなかった理由はサーバーにも残す（別の端末の履歴でも理由が見える）。 */
    it('反映できなかった理由はサーバーにも残す', async () => {
      api = new FakeApi()
      api.failCommands = ['Segment(C, D)']
      const { wrapper: view, fetchMock } = await setup({ figureId: 5 })
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      const discarded = recorded(fetchMock).find((call) => call.url.endsWith('/ai/assist/12/discarded'))
      // 作図に無い名前（この案は D を作っているので C だけ）も理由に足す
      // （GeoGebra のエラーだけでは「名前が無い」ことが分からないため）
      expect(discarded?.body).toEqual({
        reason: 'AI のコマンドを実行できませんでした：Segment(C, D)（C は作図にありません）'
      })
    })

    /** いまの作図の一覧（「名前 = 定義（型）」）を AI へ渡す（XML には定義が残らないため）。 */
    it('送信時に、作図のオブジェクト一覧（名前 = 定義）を AI へ渡す', async () => {
      api = new FakeApi()
      api.objects = ['c']
      api.definitions = { c: 'Circle((0, 0), 3)' }
      api.objectTypes = { c: 'conic' }
      const { wrapper: view, fetchMock } = await setup()
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '円を描いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      const created = recorded(fetchMock).find(
        (call) => call.method === 'POST' && call.url.endsWith('/geometry/ai/assist'))
      // AI は「c は円らしい」ではなく「c = Circle((0, 0), 3)（円・円錐曲線）」まで分かる
      expect(created?.body?.objects).toBe('c = Circle((0, 0), 3)（円・円錐曲線）')
    })

    /**
     * 実行できなかったら、**AI に 1 回だけ直させる**（利用者が言い直さなくて済む）。
     * 実測では `Polygon(A, B, C, 3)` のように引数の数を間違えた案が返り、そのまま終わっていた。
     */
    it('実行できなかったら、AI に 1 回だけ修正を頼む（AI が修正）', async () => {
      api = new FakeApi()
      api.objects = ['A', 'B', 'C']
      api.failCommands = ['Polygon(A, B, C, 3)']
      let assistSeq = 0
      const { wrapper: view, fetchMock } = await setup({
        handlers: (url: string, method: string) => {
          if (method === 'POST' && url.endsWith('/geometry/ai/assist')) {
            assistSeq += 1
            const assistId = 12 + assistSeq - 1
            return ok({
              assistId,
              status: 'PENDING',
              statusLabel: '依頼受付（AI 実行待ち）',
              runPath: `/api/admin/batch/geometry-assist/${assistId}/run`
            })
          }
          if (method === 'POST' && url === '/api/admin/batch/geometry-assist/13/run') {
            return ok({ assistId: 13, status: 'READY', step: { taskCode: 'batC52' } })
          }
          if (method === 'POST' && url === '/api/admin/batch/geometry-assist/12/run') {
            return ok({ assistId: 12, status: 'READY', step: { taskCode: 'batC52' } })
          }
          if (method === 'GET' && url.endsWith('/geometry/ai/assist/12')) {
            // 1 回目は引数の数を間違えた案（実測と同じ失敗）
            return ok({
              assistId: 12,
              status: 'READY',
              statusLabel: 'できました',
              commands: ['Polygon(A, B, C, 3)'],
              description: '円上に 3 点を取って正三角形を作ります。',
              errorCode: null,
              errorMessage: null,
              commandCount: 1
            })
          }
          if (method === 'GET' && url.endsWith('/geometry/ai/assist/13')) {
            return ok({
              assistId: 13,
              status: 'READY',
              statusLabel: 'できました',
              commands: ['Polygon(A, B, C)'],
              description: '引数の数を直しました。',
              errorCode: null,
              errorMessage: null,
              commandCount: 1
            })
          }
          return null
        }
      })
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '正三角形を描いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      // 1 回目は失敗として残る（理由つき）
      expect(dialog('[data-gm-ai-assist-step-error="1"]').textContent).toContain('Polygon(A, B, C, 3)')
      // 2 回目は AI の修正版として積まれ、印が出る
      const repaired = dialog('[data-gm-ai-assist-step="2"]')
      expect(repaired.querySelector('[data-gm-ai-assist-step-repaired="2"]')?.textContent).toContain('AI が修正')
      expect(repaired.textContent).toContain('Polygon(A, B, C)')
      // 修正版はそのまま作図へ入る
      expect(api.evaluated).toContain('Polygon(A, B, C)')
      expect(dialog('[data-gm-ai-assist-step-status="2"]').textContent).toContain('反映済み')

      // 依頼は 2 回だけ（同じ失敗で何度も AI を呼ばない）
      const assistCalls = recorded(fetchMock).filter(
        (call) => call.method === 'POST' && call.url.endsWith('/geometry/ai/assist'))
      expect(assistCalls).toHaveLength(2)
      // 2 回目の依頼には「実行できなかった行」が入っている（AI がそれを見て直す）
      expect(String(assistCalls[1]?.body?.failure)).toContain('Polygon(A, B, C, 3)')
      expect(String(assistCalls[1]?.body?.instruction)).toBe('正三角形を描いて')
      // 自動修正の依頼も**追加**（作り直さない）。作図は消えていない
      expect(assistCalls.map((call) => call.body?.mode)).toEqual(['append', 'append'])
      expect(api.newConstructions).toBe(0)
    })

    /**
     * 反映方法は**追加だけ**（利用者の指示: 作り直したいときは自分で【全消去】する）。
     * 画面に選択を置かず、依頼にも実行にも使わない。
     */
    it('反映方法の選択（追加／作り直す）は置かない', async () => {
      const { wrapper: view } = await setup()
      await openAssist(view)

      expect(document.querySelector('[data-gm-ai-assist-mode]')).toBeNull()
      const body = dialog('[data-gm-ai-assist-body]').textContent ?? ''
      expect(body).not.toContain('変更案の反映方法')
      expect(body).not.toContain('作り直')
    })

    it('送信はいつも「追加」で依頼する', async () => {
      const { wrapper: view, fetchMock } = await setup()
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      const created = recorded(fetchMock).find(
        (call) => call.method === 'POST' && call.url.endsWith('/geometry/ai/assist'))
      expect(created?.body?.mode).toBe('append')
    })

    /** 既にある作図は消さない（追加）。コマンドは**今の作図の上に**実行する。 */
    it('既にある図形に指示しても作図を消さず、追加で反映する', async () => {
      api = new FakeApi()
      api.objects = ['A', 'B', 'C']
      api.commandStrings = { A: '(0, 0)', B: '(4, 0)', C: 'Midpoint(A, B)' }
      const { wrapper: view } = await setup({ figureId: 5 })
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '垂線を引いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      // 作り直していない（全消去を呼んでいない）し、前の作図も残っている
      expect(api.newConstructions).toBe(0)
      expect(api.xml).toContain(CONSTRUCTION)
      expect(api.objects).toEqual(expect.arrayContaining(['A', 'B', 'C']))
      // 返ってきた 2 行は今の作図の上に実行される
      expect(api.evaluated).toEqual(['D = (0, 0)', 'Segment(C, D)'])
    })

    /** 古い端末の記憶（作り直す）が残っていても、実行は**追加**（画布を消さない）。 */
    it('古いローカル状態の「作り直す」でも、読み直したら追加で反映する', async () => {
      window.localStorage.setItem('study21.geometry.aiAssist.log.figure-5', JSON.stringify([
        {
          instruction: '前の指示', mode: 'replace', commands: ['D = (0, 0)'], description: null,
          assistId: 12, status: 'pending', error: null, snapshot: CONSTRUCTION, superseded: false
        }
      ]))
      api = new FakeApi()
      api.objects = ['A', 'B']
      api.commandStrings = { A: '(0, 0)', B: '(4, 0)' }
      const { wrapper: view } = await setup({ figureId: 5 })
      await openAssist(view)
      await settleAssist()

      expect(api.newConstructions).toBe(0)
      expect(api.objects).toEqual(expect.arrayContaining(['A', 'B']))
      expect(api.evaluated).toContain('D = (0, 0)')
      expect(dialog('[data-gm-ai-assist-step-status="1"]').textContent).toContain('反映済み')
    })

    /**
     * 【全消去】のあとの指示は、**消したあとの実際の作図**（空）を AI に渡す。
     * 前の作図のオブジェクトを渡すと、もう無い名前を参照した案が返ってくる。
     */
    it('全消去したあとの指示は、空の作図を AI に渡す', async () => {
      api = new FakeApi()
      api.objects = ['A', 'B']
      api.commandStrings = { A: '(0, 0)', B: '(4, 0)' }
      const { wrapper: view, fetchMock } = await setup({ figureId: 5 })
      await view.get('[data-gm-draw-clear]').trigger('click')
      await flushPromises()
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '円を描いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      const created = recorded(fetchMock).find(
        (call) => call.method === 'POST' && call.url.endsWith('/geometry/ai/assist'))
      // オブジェクト一覧は無し（＝空）。作図データにも前の要素は入っていない
      expect(created?.body?.objects).toBeNull()
      expect(String(created?.body?.construction)).not.toContain('<element')
    })

    /**
     * 案が自分で作った名前を「作図に無い」と言わない。
     *
     * 失敗すると作図は**指示前へ巻き戻る**ので、巻き戻したあとに参照チェックをすると
     * 「この案が作った A/B/C は作図にありません」と誤って出てしまう（実測で発生）。
     * 巻き戻す**前**に調べることを固定する。
     */
    it('案が自分で作った名前を「作図に無い」と誤って言わない', async () => {
      api = new FakeApi()
      api.failCommands = ['Triangle(C, D, E)']
      const { wrapper: view } = await setup({
        handlers: (url: string, method: string) => (method === 'GET' && url.endsWith('/geometry/ai/assist/12')
          ? ok({
              assistId: 12, status: 'READY', statusLabel: 'できました',
              commands: ['C = (3, 0)', 'D = (0, 3)', 'E = (-3, 0)', 'Triangle(C, D, E)'],
              description: '円の上に 3 点を取って三角形を作ります。',
              errorCode: null, errorMessage: null, commandCount: 4
            })
          : null)
      })
      await openAssist(view)
      await dialogType('[data-gm-ai-assist-instruction]', '正三角形を描いて')
      await dialogClick('[data-gm-ai-assist-send]')
      await settleAssist()

      const error = dialog('[data-gm-ai-assist-step-error="1"]').textContent ?? ''
      expect(error).toContain('Triangle(C, D, E)')
      // C・D・E はこの案が作った（巻き戻る前は存在した）ので「作図に無い」とは言わない
      expect(error).not.toContain('は作図にありません')
    })

    it('履歴は図形ごとに分ける（他の図形の履歴を読まない）', async () => {
      window.localStorage.setItem('study21.geometry.aiAssist.log.figure-5', JSON.stringify([
        { instruction: '図形5の指示', mode: 'append', commands: [], status: 'applied' }
      ]))
      window.localStorage.setItem('study21.geometry.aiAssist.log.figure-9', JSON.stringify([
        { instruction: '図形9の指示', mode: 'append', commands: [], status: 'applied' }
      ]))

      const five = await setup({ figureId: 5 })
      await openAssist(five.wrapper)
      expect(dialog('[data-gm-ai-assist-step-instruction="1"]').textContent).toContain('図形5の指示')
      five.wrapper.unmount()

      const nine = await setup({
        figureId: 9,
        detail: ok({
          figure: geometryFigure({ figureId: 9, figureNo: 'GEO20260402-999999' }),
          construction: CONSTRUCTION,
          thumbnail: THUMBNAIL
        })
      })
      await openAssist(nine.wrapper)
      expect(dialog('[data-gm-ai-assist-step-instruction="1"]').textContent).toContain('図形9の指示')
      nine.wrapper.unmount()

      // コピーして開いたときはコピー元の履歴（新規作成は draft）
      const copied = await setup({ copyFromId: 5 })
      await openAssist(copied.wrapper)
      expect(document.querySelector('[data-gm-ai-assist-step]')).toBeNull()
      expect(window.localStorage.getItem('study21.geometry.aiAssist.log.copy-GEO20260402-124745')).toBeNull()
    })

    /** サムネイルの枠線（利用者の指示: 浅色の枠線。色は tokens の変数だけ）。 */
    it('サムネイルの枠に 1px の枠線がある（色は tokens の変数）', async () => {
      const css = readFileSync(
        path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'src/features/geometry/geometry.css'),
        'utf8'
      )
      const rule = /\.gm-card__thumb\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
      expect(rule).toMatch(/border:\s*1px solid var\(--color-border\)/)
      // 生の色を書かない（作図画面のサムネイルは同じ class を使う）
      expect(rule).not.toMatch(/#[0-9a-fA-F]{3,8}\b/)
      const drawRule = /\.gm-draw__thumb\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
      expect(drawRule).not.toMatch(/border[^;]*#[0-9a-fA-F]{3,8}/)
    })

    it('作図の道具立て（保存・コマンド）は今までどおり使える', async () => {
      const { wrapper: view } = await setup()

      expect(view.find('[data-gm-draw-save]').exists()).toBe(true)
      expect(view.find('[data-gm-draw-command-run]').exists()).toBe(true)
      // コマンドの 2 つのボタンはアイコンで、見出しと同じ行にある
      const run = view.get('[data-gm-draw-command-run]').element as HTMLElement
      const clear = view.get('[data-gm-draw-command-clear]').element as HTMLElement
      expect(run.querySelector('svg')).not.toBeNull()
      expect(clear.querySelector('svg')).not.toBeNull()
      expect(run.textContent?.trim()).toBe('')
      expect(run.getAttribute('title')).toBe('コマンドを実行')
      expect(clear.getAttribute('title')).toBe('コマンド欄をクリア')
      const label = view.get('.gm-command__head .field__label').element as HTMLElement
      expect(Math.abs(label.getBoundingClientRect().top - run.getBoundingClientRect().top)).toBeLessThan(20)
    })
  })

  it('GeoGebra を読み込めなかったら案内を出し、1 秒後に 1 回だけやり直す', async () => {
    vi.useFakeTimers()
    try {
      // deployggb.js が無い状態にする
      delete (window as { GGBApplet?: unknown }).GGBApplet
      const { wrapper: view } = await setup()

      const script = document.querySelector('script[data-geogebra]')
      expect(script).not.toBeNull()
      script?.dispatchEvent(new Event('error'))
      await flushPromises()

      // 作図できないことは文言ではなく属性で知らせ、理由と「ページを再読み込みしてください」を出す
      expect(view.get('[data-gm-draw-frame]').attributes('data-gm-draw-ready')).toBe('false')
      const status = view.get('[data-gm-draw-status]').text()
      expect(status).toContain('GeoGebra を読み込めませんでした')
      expect(status).toContain('ページを再読み込みしてください')

      const scripts = (): number => document.querySelectorAll('script[data-geogebra]').length
      expect(scripts()).toBe(1)

      // 1 秒待つと 1 回だけ読み込み直す
      await vi.advanceTimersByTimeAsync(1000)
      await flushPromises()
      expect(scripts()).toBe(2)

      // もう一度失敗しても、やり直すのは 1 回だけ（無限に繰り返さない）
      document.querySelectorAll('script[data-geogebra]')[1]?.dispatchEvent(new Event('error'))
      await flushPromises()
      await vi.advanceTimersByTimeAsync(5000)
      await flushPromises()
      expect(scripts()).toBe(2)
      expect(view.get('[data-gm-draw-frame]').attributes('data-gm-draw-ready')).toBe('false')
    } finally {
      vi.useRealTimers()
    }
  })
})
