import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { formatIsoDateTime, useToast } from '@study21/web-shared'
import GeometryAiView from '@/views/geometry/GeometryAiView.vue'
import {
  CROP_ALL,
  CROP_MIN,
  clampCrop,
  cropOriginLabel,
  cropPercentLabel,
  cropPixelSize,
  cropSizeLabel,
  cropStyle,
  isFullCrop,
  moveCrop,
  resizeCrop,
  type CropRect
} from '@/features/geometry/geometry-crop'

/**
 * 図形管理【AI 生図】（画像から作図）。
 *
 * 画面の契約は**送信 → ポーリング**:
 *   1. `POST /api/user/geometry/ai/requests/images` で元画像を上げる
 *   2. `POST /api/user/geometry/ai/requests` で要求を作る（QUEUED）
 *   3. `runPath`（admin-api の入口。batC51 → 52 → 53）を 1 回だけ呼ぶ
 *   4. `GET .../requests/{id}` を 2 秒間隔でポーリングする（AI に 60〜180 秒かかる）
 *
 * 画像は jsdom では読み込まれないので、実寸は「読み取れない」前提の表示になり、
 * 切り抜き画像のプレビュー（canvas）も作られない（画面は案内に切り替わる）。
 * 実機での確認は `tmp/e2e/e2e-geometry.mjs` が行う。
 */
const Dummy = defineComponent({ name: 'Dummy', render: () => null })

function router(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/:area/geometry', component: Dummy },
      { path: '/:area/geometry-draw', component: Dummy },
      { path: '/:area/geometry-ai', component: GeometryAiView }
    ]
  })
}

/** 小数の計算結果を丸める（割合の計算は誤差が出るため）。 */
function roundRect(rect: CropRect): CropRect {
  const at = (value: number): number => Math.round(value * 1000) / 1000
  return { x: at(rect.x), y: at(rect.y), w: at(rect.w), h: at(rect.h) }
}

describe('AI 生図の切り抜きの計算（geometry-crop）', () => {
  it('画像全体が既定で、中央を優先すると 70% になる', () => {
    expect(isFullCrop(CROP_ALL)).toBe(true)
    expect(cropPercentLabel({ x: 0.15, y: 0.15, w: 0.7, h: 0.7 })).toBe('横 70% ・ 縦 70%')
    expect(isFullCrop({ x: 0.15, y: 0.15, w: 0.7, h: 0.7 })).toBe(false)
  })

  it('画像の外へはみ出さない（最小 5% を確保する）', () => {
    // はみ出したら内側へ寄せる
    expect(clampCrop({ x: -0.5, y: -0.5, w: 0.5, h: 0.5 })).toEqual({ x: 0, y: 0, w: 0.5, h: 0.5 })
    expect(clampCrop({ x: 0.9, y: 0.9, w: 0.5, h: 0.5 })).toEqual({ x: 0.5, y: 0.5, w: 0.5, h: 0.5 })
    // 小さくしすぎない
    expect(clampCrop({ x: 0.5, y: 0.5, w: 0, h: 0 }).w).toBe(CROP_MIN)
    expect(clampCrop({ x: 0.5, y: 0.5, w: 0, h: 0 }).h).toBe(CROP_MIN)
  })

  it('枠を動かしても大きさは変わらず、端で止まる', () => {
    expect(roundRect(moveCrop({ x: 0.2, y: 0.2, w: 0.5, h: 0.5 }, 0.1, 0.1)))
      .toEqual({ x: 0.3, y: 0.3, w: 0.5, h: 0.5 })
    // 右下の端で止まる（はみ出さない）
    expect(moveCrop({ x: 0.2, y: 0.2, w: 0.5, h: 0.5 }, 1, 1)).toEqual({ x: 0.5, y: 0.5, w: 0.5, h: 0.5 })
    // 左上の端で止まる
    expect(moveCrop({ x: 0.2, y: 0.2, w: 0.5, h: 0.5 }, -1, -1)).toEqual({ x: 0, y: 0, w: 0.5, h: 0.5 })
  })

  it('つまんだ辺だけを動かす（反対側の辺は固定）', () => {
    const base: CropRect = { x: 0.2, y: 0.2, w: 0.6, h: 0.6 }
    expect(resizeCrop(base, 'e', 0.1, 0)).toEqual({ x: 0.2, y: 0.2, w: 0.7, h: 0.6 })
    const west = resizeCrop(base, 'w', 0.1, 0)
    expect(round(west)).toEqual({ x: 0.3, y: 0.2, w: 0.5, h: 0.6 })
    expect(resizeCrop(base, 's', 0, 0.2)).toEqual({ x: 0.2, y: 0.2, w: 0.6, h: 0.8 })
    expect(round(resizeCrop(base, 'n', 0, 0.1))).toEqual({ x: 0.2, y: 0.3, w: 0.6, h: 0.5 })
    expect(round(resizeCrop(base, 'se', 0.1, 0.1))).toEqual({ x: 0.2, y: 0.2, w: 0.7, h: 0.7 })

    function round(rect: CropRect): CropRect {
      return {
        x: Math.round(rect.x * 100) / 100, y: Math.round(rect.y * 100) / 100,
        w: Math.round(rect.w * 100) / 100, h: Math.round(rect.h * 100) / 100
      }
    }
  })

  it('大きさは CSS の % と、実寸が分かるときは px で出せる', () => {
    expect(cropStyle({ x: 0.25, y: 0.5, w: 0.5, h: 0.25 }))
      .toEqual({ left: '25%', top: '50%', width: '50%', height: '25%' })
    // 実寸が分からないときは割合だけ
    expect(cropSizeLabel({ x: 0, y: 0, w: 0.6, h: 0.4 }, null)).toBe('横 60% ・ 縦 40%')
    expect(cropPixelSize({ x: 0, y: 0, w: 0.6, h: 0.4 }, null)).toBeNull()
    // 実寸が分かるときは px を併記する
    expect(cropPixelSize({ x: 0, y: 0, w: 0.6, h: 0.4 }, { width: 1000, height: 800 }))
      .toEqual({ width: 600, height: 320 })
    expect(cropSizeLabel({ x: 0, y: 0, w: 0.6, h: 0.4 }, { width: 1000, height: 800 }))
      .toBe('横 600 × 縦 320 px（横 60% ・ 縦 40%）')
    expect(cropOriginLabel({ x: 0.1, y: 0.25, w: 0.6, h: 0.4 }, null)).toBe('横 10% ・ 縦 25%')
    expect(cropOriginLabel({ x: 0.1, y: 0.25, w: 0.6, h: 0.4 }, { width: 1000, height: 800 }))
      .toBe('横 100 × 縦 200 px から')
  })
})

/* ---------- 画面（送信 → ポーリング） ---------- */

function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

/*
 * 画面（AI 生図の 4 ステップ）のテストは `tests/geometry-ai-mode.spec.ts` にある。
 *
 * 2026-09 の作り直しで契約が変わったため:
 *   ・ステップは「画像を追加 / 読み取る範囲を指定 / 作図方法を選択 / 内容を確認して送信」
 *   ・作図方法（A〜D）と作成する図の種類（AUTO / GEOMETRY / GRAPH / MIXED）を選ぶ
 *   ・送信すると**一覧へ戻る**（実行はバックエンドの働き手。画面はポーリングしない）
 *   ・結果プレビューは画面から外した（一覧のタスクのカードで見る）
 * ここには切り抜きの計算と、一覧の「AI 生図のタスク」だけを残す。
 */

/* ---------- 図形管理の一覧に出す AI 生図のタスク ---------- */

/**
 * 図形管理【一覧画面】の「AI 生図のタスク」。
 *
 * 送信するとすぐ一覧へ戻り、要求は**実際の処理段階**（`statusLabel`）のカードとして出る。
 * 進み具合の％は持たないので、画面にも絶対に出さない（何度も止まって見えるため）。
 * 処理中のカードがある間だけ 3 秒間隔で取り直し、0 件になったら止める。
 */
vi.mock('@/api/geometry-ai', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/geometry-ai')>()),
  // タスクの一覧だけを差し替える（他の API は実物のまま＝`fetch` のスタブを通す）
  fetchGeometryAiTasks: vi.fn()
}))

import GeometryView from '@/views/geometry/GeometryView.vue'
import { fetchGeometryAiTasks } from '@/api/geometry-ai'

describe('図形管理の一覧に出す AI 生図のタスク', () => {
  const FIGURE = {
    figureId: 1,
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
    constructionLength: 1234,
    version: 3,
    createdAt: '2026-04-02T12:47:45',
    updatedAt: '2026-09-12T23:02:00'
  }

  /** タスク 1 件（API の応答形のまま。既定は処理中の「生成中」）。 */
  function task(overrides: Record<string, unknown> = {}): Record<string, unknown> {
    return {
      requestId: 31,
      requestNo: 'AIG202609141200001234',
      status: 'GENERATING',
      statusLabel: 'AI が生成中',
      cardStatus: 'GENERATING',
      cardStatusLabel: '生成中',
      mode: 'A',
      modeLabel: '画像をもとに再現',
      requestedOutputType: 'AUTO',
      resolvedOutputType: null,
      title: '三角形と外接円',
      description: null,
      questionCount: 0,
      figureId: null,
      failedStage: null,
      errorCode: null,
      errorMessage: null,
      hasCroppedImage: true,
      retryCount: 0,
      version: 4,
      createdAt: '2026-09-14T12:00:00',
      updatedAt: '2026-09-14T12:05:00',
      ...overrides
    }
  }

  function taskList(items: Record<string, unknown>[]): Record<string, unknown> {
    return { items, totalElements: items.length, limit: 20 }
  }

  /** タスク API の応答（`ApiResponse<GeometryAiTaskList>` の形。モックは http を通さない）。 */
  function taskResponse(items: Record<string, unknown>[]): Record<string, unknown> {
    return { success: true, code: 'OK', message: 'OK', data: taskList(items), timestamp: '' }
  }

  let tasksHandler: () => Promise<unknown>
  let fetchMock: ReturnType<typeof vi.fn>

  async function setup(options: {
    tasks?: Record<string, unknown>[]
    tasksFailure?: boolean
  } = {}): Promise<{ wrapper: VueWrapper; appRouter: Router }> {
    const pinia = createPinia()
    setActivePinia(pinia)
    const items = options.tasks ?? [task()]
    // モックは ApiResponse をそのまま返す（http クライアントを通らないため）
    tasksHandler = options.tasksFailure === true
      ? async () => { throw new Error('AI 生図のタスクを取得できませんでした。') }
      : async () => taskResponse(items)

    fetchMock = vi.fn(async (url: string) => {
      const target = String(url)
      if (target.startsWith('/api/user/geometry/figures?')) {
        return ok({ items: [FIGURE], totalElements: 1, page: 1, size: 24, totalPages: 1,
          totals: { figureCount: 1, geometryCount: 1, functionCount: 0, deletedCount: 0 } })
      }
      if (target.startsWith('/api/user/geometry/tags')) return ok({ items: [] })
      // AI 生図が使えるかの設定（一覧の【新規】の導線が変わる）
      if (target.endsWith('/geometry/ai/options')) {
        return ok({ enabled: true, assistEnabled: true, maxImageMb: 10, maxImagePixels: 1536,
          defaultCrop: 'manual', defaultKind: 'figure', approval: 'manual',
          dailyLimit: 20, usedToday: 0, notice: '' })
      }
      return ok({ count: 1, message: 'OK' })
    })
    vi.stubGlobal('fetch', fetchMock)
    vi.mocked(fetchGeometryAiTasks).mockImplementation(
      (() => tasksHandler()) as unknown as typeof fetchGeometryAiTasks
    )

    const appRouter = router()
    await appRouter.push('/student/geometry')
    await appRouter.isReady()
    const wrapper = mount(GeometryView, { global: { plugins: [pinia, appRouter] } })
    await flushPromises()
    return { wrapper, appRouter }
  }

  function tasksArea(wrapper: VueWrapper): string {
    return wrapper.get('[data-gm-ai-tasks]').text()
  }

  it('カードの【削除】は確認してから消し、一覧を取り直す', async () => {
    // window.confirm は引数を 1 つ取る（型は () => boolean なので、呼び出しの記録だけ見る）
    const confirmMock = vi.fn((_message?: string) => true)
    vi.stubGlobal('confirm', confirmMock)
    const deleted: string[] = []
    const { wrapper } = await setup({
      tasks: [
        task({ requestId: 31, status: 'READY', statusLabel: '生成済み・確認待ち', cardStatus: 'READY', cardStatusLabel: '生成済み・確認待ち', version: 7 }),
        task({ requestId: 32, requestNo: 'AIG202609141200005678', status: 'FAILED', statusLabel: '失敗', cardStatus: 'FAILED', cardStatusLabel: '失敗', version: 3 })
      ]
    })
    // 削除の API 呼び出しを記録する（http クライアントは fetch を通る）
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
      const target = String(url)
      if (target.includes('/geometry/ai/requests/') && target.endsWith('/discard')) {
        deleted.push(`${target} ${String(init?.body)}`)
        return ok({ requestId: 31, requestNo: 'AIG202609141200001234', status: 'CANCELLED', statusLabel: '取消', version: 8, runPath: '' }, 'AI 生図のタスクを一覧から消しました。')
      }
      return ok({ count: 0, message: 'OK' })
    })

    const removeButton = wrapper.get('[data-gm-ai-task="31"] [data-gm-ai-task-delete]')
    // アイコンだけ（意味は title と aria-label で伝える）
    expect(removeButton.text()).toBe('')
    expect(removeButton.attributes('title')).toContain('削除')
    expect(removeButton.attributes('aria-label')).toContain('AIG202609141200001234')

    await removeButton.trigger('click')
    await flushPromises()

    // 確認してから消す（図形の削除と同じ）
    expect(confirmMock).toHaveBeenCalledTimes(1)
    expect(String(confirmMock.mock.calls[0][0])).toContain('AIG202609141200001234')
    // 版数を付けて消す（楽観的ロック）
    expect(deleted).toHaveLength(1)
    expect(deleted[0]).toContain('/geometry/ai/requests/31/discard')
    expect(deleted[0]).toContain('"version":7')
    expect(useToast().items.map((item) => item.message).join(' ')).toContain('一覧から消しました')
  })

  it('確認をやめたら消さない', async () => {
    const confirmMock = vi.fn((_message?: string) => false)
    vi.stubGlobal('confirm', confirmMock)
    const deleted: string[] = []
    const { wrapper } = await setup({ tasks: [task({ status: 'READY', cardStatus: 'READY' })] })
    fetchMock.mockImplementation(async (url: string) => {
      if (String(url).endsWith('/discard')) deleted.push(String(url))
      return ok({ count: 0, message: 'OK' })
    })

    await wrapper.get('[data-gm-ai-task-delete]').trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalledTimes(1)
    expect(deleted).toHaveLength(0)
  })

  it('処理中のカードにも【削除】は出す（片付けられる）', async () => {
    const { wrapper } = await setup({ tasks: [task({ status: 'GENERATING', cardStatus: 'GENERATING' })] })

    expect(wrapper.find('[data-gm-ai-task="31"] [data-gm-ai-task-open]').exists()).toBe(false)
    expect(wrapper.find('[data-gm-ai-task="31"] [data-gm-ai-task-delete]').exists()).toBe(true)
  })

  beforeEach(() => {
    useToast().items.splice(0)
    vi.restoreAllMocks()
    // どのテストも setup で差し替えるが、`vi.mock` の既定は undefined なので
    // 先に空の一覧を返しておく（`data` が無い応答で画面を壊さない）
    vi.mocked(fetchGeometryAiTasks).mockResolvedValue(
      taskResponse([]) as unknown as Awaited<ReturnType<typeof fetchGeometryAiTasks>>
    )
  })

  it('実際の処理段階のカードを出し、作図方法・作図の種類・要求番号・更新日時を日本語で出す', async () => {
    const { wrapper } = await setup({
      tasks: [
        task({
          status: 'PREPROCESSING',
          statusLabel: '読み取り中',
          cardStatus: 'READING',
          cardStatusLabel: '読み取り中',
          mode: 'B',
          modeLabel: '数式からグラフを作成',
          requestedOutputType: 'GRAPH',
          resolvedOutputType: 'GRAPH',
          title: '一次関数 y = 2x + 1'
        }),
        task({
          status: 'VALIDATING',
          statusLabel: '検証中（コマンドの規則を確認しています）',
          cardStatus: 'VALIDATING',
          cardStatusLabel: '検証中',
          mode: null,
          modeLabel: null,
          requestedOutputType: 'MIXED',
          resolvedOutputType: null
        })
      ]
    })

    const cards = wrapper.findAll('[data-gm-ai-task]')
    expect(cards).toHaveLength(2)

    // 1 件目: 読み取り中（実際の処理段階をそのまま出す）
    const first = cards[0]
    expect(first.attributes('data-gm-ai-task')).toBe('31')
    expect(first.get('[data-gm-ai-task-status-label]').text()).toBe('読み取り中')
    expect(first.get('[data-gm-ai-task-status-label]').classes()).toContain('badge--info')
    expect(first.get('[data-gm-ai-task-mode]').text()).toBe('数式からグラフを作成')
    expect(first.get('[data-gm-ai-task-output]').text()).toBe('関数・方程式のグラフ')
    // 実際に作る種類が分かっていれば併記する
    expect(first.get('[data-gm-ai-task-resolved]').text()).toContain('関数・方程式のグラフ')
    expect(first.get('[data-gm-ai-task-no]').text()).toBe('AIG202609141200001234')
    expect(first.get('[data-gm-ai-task-title]').text()).toBe('一次関数 y = 2x + 1')
    expect(first.get('[data-gm-ai-task-stage]').text()).toBe('読み取り中')
    expect(first.get('[data-gm-ai-task-updated]').text()).toBe(`更新 ${formatIsoDateTime('2026-09-14T12:05:00')}`)
    // 処理中はボタンを出さず、終わったら確認できることを案内する
    expect(first.find('[data-gm-ai-task-open]').exists()).toBe(false)
    expect(first.find('[data-gm-ai-task-resume]').exists()).toBe(false)
    expect(first.text()).toContain('処理が終わるとここから確認できます。')

    // 2 件目: 検証中（モードが無い古い要求は「（旧）画像から作図」）
    // 検証は**保存しない**（コマンドの規則を確かめているだけ）ので「保存」とは書かない
    const second = cards[1]
    expect(second.get('[data-gm-ai-task-status-label]').text()).toBe('検証中')
    expect(second.get('[data-gm-ai-task-status-label]').text()).not.toContain('保存')
    expect(second.get('[data-gm-ai-task-status-label]').classes()).toContain('badge--warning')
    expect(second.get('[data-gm-ai-task-mode]').text()).toBe('（旧）画像から作図')
    expect(second.get('[data-gm-ai-task-output]').text()).toBe('図形とグラフの組み合わせ')
    // 作る種類が決まっていないので併記しない
    expect(second.find('[data-gm-ai-task-resolved]').exists()).toBe(false)
    expect(second.get('[data-gm-ai-task-stage]').text()).toBe('検証中（コマンドの規則を確認しています）')
  })

  it('タスクのエリアには％（進み具合）を 1 文字も出さない', async () => {
    const { wrapper } = await setup({
      tasks: [
        task({ status: 'QUEUED', statusLabel: '受付済（AI の順番待ち）', cardStatus: 'WAITING', cardStatusLabel: '待機中' }),
        task({ requestId: 32, status: 'PREPROCESSED', statusLabel: '画像の読み取り済み', cardStatus: 'WAITING', cardStatusLabel: '待機中' }),
        task({ requestId: 33, status: 'READY', statusLabel: '生成済み・確認待ち', cardStatus: 'READY', cardStatusLabel: '生成済み・確認待ち', resolvedOutputType: 'GEOMETRY', requestedOutputType: 'GEOMETRY' })
      ]
    })

    const text = tasksArea(wrapper)
    expect(text).not.toContain('%')
    expect(text).not.toContain('％')
    // 段階は実際の日本語をそのまま出す
    expect(text).toContain('受付済（AI の順番待ち）')
    expect(text).toContain('画像の読み取り済み')
    // 「生成できた」と「保存できた」は別（READY はまだ図形になっていない）
    expect(wrapper.get('[data-gm-ai-task="33"] [data-gm-ai-task-status-label]').text()).toBe('生成済み・確認待ち')
    expect(wrapper.get('[data-gm-ai-task="33"] [data-gm-ai-task-status-label]').text()).not.toContain('保存')
  })

  it('追加入力待ちは質問の件数を出し、失敗は理由を隠さず、【内容を直して送り直す】で AI 生図の画面へ戻る', async () => {
    const { wrapper, appRouter } = await setup({
      tasks: [
        task({
          requestId: 41,
          status: 'NEEDS_INPUT',
          statusLabel: '追加入力待ち（AI からの質問）',
          cardStatus: 'NEEDS_INPUT',
          cardStatusLabel: '追加入力待ち',
          questionCount: 2
        }),
        task({
          requestId: 42,
          status: 'FAILED',
          statusLabel: '失敗',
          cardStatus: 'FAILED',
          cardStatusLabel: '失敗',
          failedStage: 'GENERATE',
          errorCode: 'TIMEOUT',
          errorMessage: 'AI が時間内に返りませんでした。もう一度お試しください。'
        })
      ]
    })

    const needsInput = wrapper.get('[data-gm-ai-task="41"]')
    expect(needsInput.get('[data-gm-ai-task-status-label]').text()).toBe('追加入力待ち')
    expect(needsInput.get('[data-gm-ai-task-status-label]').classes()).toContain('badge--warning')
    expect(needsInput.get('[data-gm-ai-task-questions]').text()).toContain('質問 2 件')
    expect(needsInput.get('[data-gm-ai-task-stage]').text()).toBe('追加入力待ち（AI からの質問）')

    const failed = wrapper.get('[data-gm-ai-task="42"]')
    expect(failed.get('[data-gm-ai-task-status-label]').classes()).toContain('badge--danger')
    // 失敗の理由（サーバーの日本語）をそのまま出す
    expect(failed.get('[data-gm-ai-task-error]').text())
      .toBe('AI が時間内に返りませんでした。もう一度お試しください。')

    // 【内容を直して送り直す】は要求 ID つきで AI 生図の画面を開く
    await needsInput.get('[data-gm-ai-task-resume]').trigger('click')
    await flushPromises()
    expect(appRouter.currentRoute.value.path).toBe('/student/geometry-ai')
    expect(appRouter.currentRoute.value.query.requestId).toBe('41')

    // 失敗したカードからも同じ導線を出す
    await wrapper.get('[data-gm-ai-task="42"] [data-gm-ai-task-resume]').trigger('click')
    await flushPromises()
    expect(appRouter.currentRoute.value.query.requestId).toBe('42')
  })

  it('生成できたカードは【作図を確認する】アイコンで作図画面を要求 ID つきで開く', async () => {
    const { wrapper, appRouter } = await setup({
      tasks: [task({
        status: 'READY',
        statusLabel: '生成済み・確認待ち',
        cardStatus: 'READY',
        cardStatusLabel: '生成済み・確認待ち',
        requestedOutputType: 'AUTO',
        resolvedOutputType: 'GEOMETRY'
      })]
    })

    // カードの操作はアイコンだけ（意味は title と aria-label で伝える）
    const open = wrapper.get('[data-gm-ai-task-open]')
    expect(open.text()).toBe('')
    expect(open.attributes('title')).toBe('作図を確認する')
    expect(open.attributes('aria-label')).toBe('作図を確認する')
    // 保存完了ではないので success（緑）にはしない
    expect(wrapper.get('[data-gm-ai-task="31"] [data-gm-ai-task-status-label]').classes())
      .toContain('badge--info')
    // 実際に作る種類が決まったので、指定（おまかせ）と併記する
    expect(wrapper.get('[data-gm-ai-task-output]').text()).toBe('おまかせ（AI が判別）')
    expect(wrapper.get('[data-gm-ai-task-resolved]').text()).toContain('幾何図形')

    await open.trigger('click')
    await flushPromises()
    expect(appRouter.currentRoute.value.path).toBe('/student/geometry-draw')
    expect(appRouter.currentRoute.value.query.geometryAiRequestId).toBe('31')
  })

  it('処理中のカードがある間だけ 3 秒間隔で取り直し、処理中が無くなると止める', async () => {
    // ポーリングのタイマーは画面を開いたときに作られるので、先に偽のタイマーへ差し替える
    vi.useFakeTimers()
    try {
      const { wrapper } = await setup({ tasks: [task()] })
      // 初回の読み込みは 1 回だけ（上限は既定の 20 件）
      expect(vi.mocked(fetchGeometryAiTasks)).toHaveBeenCalledTimes(1)
      expect(vi.mocked(fetchGeometryAiTasks).mock.calls[0][0]).toBe(20)

      // 3 秒ごとに取り直す
      await vi.advanceTimersByTimeAsync(3_000)
      await flushPromises()
      expect(vi.mocked(fetchGeometryAiTasks)).toHaveBeenCalledTimes(2)

      // まだ処理中なので続ける（多重起動しない）
      await vi.advanceTimersByTimeAsync(3_000)
      await flushPromises()
      expect(vi.mocked(fetchGeometryAiTasks)).toHaveBeenCalledTimes(3)

      // 処理中が 0 件になったら止まる
      tasksHandler = async () => taskResponse([task({
        status: 'READY', statusLabel: '生成済み・確認待ち', cardStatus: 'READY', cardStatusLabel: '生成済み・確認待ち'
      })])
      await vi.advanceTimersByTimeAsync(3_000)
      await flushPromises()
      expect(vi.mocked(fetchGeometryAiTasks)).toHaveBeenCalledTimes(4)
      expect(wrapper.get('[data-gm-ai-task-stage]').text()).toBe('生成済み・確認待ち')

      await vi.advanceTimersByTimeAsync(9_000)
      await flushPromises()
      expect(vi.mocked(fetchGeometryAiTasks)).toHaveBeenCalledTimes(4)

      // 画面を離れたら止まる
      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('画面を離れるとポーリングを止める', async () => {
    const { wrapper } = await setup({ tasks: [task()] })
    wrapper.unmount()

    vi.useFakeTimers()
    try {
      await vi.advanceTimersByTimeAsync(9_000)
      await flushPromises()
      // 初回の 1 回だけ（離脱後に裏で叩き続けない）
      expect(vi.mocked(fetchGeometryAiTasks)).toHaveBeenCalledTimes(1)
    } finally {
      vi.useRealTimers()
    }
  })

  it('タスクを取得できないときは小さな案内だけ出し、図形一覧は今までどおり使える', async () => {
    const { wrapper } = await setup({ tasksFailure: true })

    // タスクのセクションは出さない（余計な空カードを出さない）
    expect(wrapper.find('[data-gm-ai-tasks]').exists()).toBe(false)
    expect(wrapper.find('[data-gm-ai-task]').exists()).toBe(false)
    expect(wrapper.get('[data-gm-ai-tasks-error]').text()).toContain('AI 生図のタスクを取得できませんでした')
    expect(wrapper.get('[data-gm-ai-tasks-error]').text()).toContain('図形一覧はそのまま')

    // 図形一覧は壊れない
    expect(wrapper.get('[data-gm-card="1"] [data-gm-title]').text()).toBe('三角形の重心')
    expect(wrapper.get('[data-gm-summary]').text()).toContain('全 1 件')
    expect(wrapper.find('.alert.alert--danger').exists()).toBe(false)
  })

  it('タスクが 0 件のときはセクションを出さない', async () => {
    const { wrapper } = await setup({ tasks: [] })

    expect(wrapper.find('[data-gm-ai-tasks]').exists()).toBe(false)
    expect(wrapper.find('[data-gm-ai-tasks-error]').exists()).toBe(false)
    // 一覧は今までどおり出る
    expect(wrapper.get('[data-gm-card="1"]').text()).toContain('三角形の重心')
  })
})
