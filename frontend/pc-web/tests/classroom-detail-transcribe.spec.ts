import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import ClassroomDetailView from '@/views/classroom/ClassroomDetailView.vue'

/**
 * 詳細画面の**書き起こし（認識）の状態**と**最終まとめの起動**の検証（利用者の指摘 ①-5・②-4）。
 *
 * <p>見張るのは:</p>
 * <ol>
 *   <li>書き起こしが不完全なまま終わった回は、**開き直しても**その旨が出る（一度きりの通知にしない）。</li>
 *   <li>確認できないとき（記録が無い・欄が無い）は**完全と言わない**。</li>
 *   <li>最終まとめが `FAILED` のときだけ【最終まとめを再試行】を出し、受理だけを待つ。</li>
 *   <li>走っている（`GENERATING`）・できている（`READY`）ときは再試行を出さない。</li>
 * </ol>
 */
const Dummy = defineComponent({ name: 'Dummy', render: () => null })

type Call = { url: string; method: string }

function detailRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: Dummy },
      { path: '/login', component: Dummy },
      { path: '/student/home', component: Dummy },
      { path: '/:area/classroom', component: Dummy },
      { path: '/:area/classroom/:id', component: Dummy },
      { path: '/:pathMatch(.*)*', component: Dummy }
    ]
  })
}

/** 最終まとめの行。 */
function finalNote(status: string): Record<string, unknown> {
  return {
    noteId: 91, kind: 'FINAL', phaseNo: null, startSeq: 1, endSeq: 3, status,
    statusLabel: status === 'READY' ? 'できました' : status === 'FAILED' ? '失敗' : '作成中',
    noteJson: null, errorCode: status === 'FAILED' ? 'AI_ERROR' : null,
    errorMessage: status === 'FAILED' ? 'AI が応答しませんでした。' : null,
    createdAt: null, updatedAt: null
  }
}

/** 記録 1 件（書き起こしの状態とノートを差し替えられる）。 */
function detail(options: {
  transcribe?: Record<string, unknown> | null
  notes?: Record<string, unknown>[]
}): Record<string, unknown> {
  return {
    recordId: 12, recordNo: 'CR1', title: '数学 二次関数の復習', subject: '数学',
    languageMode: 'ja', presetId: null, presetName: null, presetText: null,
    status: 'STOPPED', statusLabel: '停止', startTime: null, endTime: null,
    durationSeconds: 120, transcribedChars: 40, summaryJson: null,
    hasAudio: true, audioMime: 'audio/webm', segments: [],
    notes: options.notes ?? [], version: 3, createdAt: null, updatedAt: null,
    lossSeqs: [],
    assembly: {
      state: 'READY', complete: true, storedChunks: 3, durationSeconds: 120,
      missingSeqs: [], reason: null
    },
    transcribe: options.transcribe === undefined ? null : options.transcribe
  }
}

function mockApi(options: Parameters<typeof detail>[0] & {
  /** 再試行の受理の応答。 */
  acceptance?: Record<string, unknown>
  /** 再試行を失敗させる。 */
  retryFails?: boolean
  /** 再試行のあとに読み直すノート（既定は GENERATING になる）。 */
  afterRetry?: Record<string, unknown>[]
  /** 「状態を確認／復旧」の応答。`null` は問い合わせ自体の失敗。 */
  recovery?: Record<string, unknown> | null
  /** 復旧のあとに読み直すノート。 */
  afterRecovery?: Record<string, unknown>[]
}): { calls: Call[] } {
  const calls: Call[] = []
  let retried = false
  let recovered = false
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    const method = (init?.method ?? 'GET').toUpperCase()
    calls.push({ url: String(url), method })
    const ok = (data: unknown): Response => new Response(
      JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }),
      { status: 200, headers: { 'Content-Type': 'application/json' } })
    const target = String(url)
    if (target.includes('/recover')) {
      if (options.recovery === null) {
        return new Response(JSON.stringify({
          success: false, code: 'INTERNAL_ERROR', message: '確認できません', data: null
        }), { status: 500, headers: { 'Content-Type': 'application/json' } })
      }
      if (options.afterRecovery !== undefined) recovered = true
      return ok(options.recovery ?? {
        noteId: 91, status: 'GENERATING', liveness: 'RUNNING', recoverable: false, recovered: false,
        reason: 'この最終まとめは実行中です（このままお待ちください）。'
      })
    }
    if (target.includes('/api/admin/batch/classroom/notes/')) {
      if (options.retryFails === true) {
        return new Response(JSON.stringify({
          success: false, code: 'INTERNAL_ERROR', message: 'AI を開始できませんでした。', data: null
        }), { status: 500, headers: { 'Content-Type': 'application/json' } })
      }
      retried = true
      return ok(options.acceptance ?? {
        noteId: 91, accepted: true, status: 'GENERATING', message: '最終まとめの作成を始めました。'
      })
    }
    if (recovered) {
      return ok(detail({ ...options, notes: options.afterRecovery ?? [finalNote('FAILED')] }))
    }
    return ok(detail(retried
      ? { ...options, notes: options.afterRetry ?? [finalNote('GENERATING')] }
      : options))
  }))
  return { calls }
}

async function open(options: Parameters<typeof mockApi>[0]): Promise<{
  wrapper: VueWrapper
  calls: Call[]
}> {
  const { calls } = mockApi(options)
  const router = detailRouter()
  await router.push('/student/classroom/12')
  await router.isReady()
  const wrapper = mount(ClassroomDetailView as Component, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, calls }
}

beforeEach(() => {
  localStorage.clear()
  vi.unstubAllGlobals()
})

describe('授業詳細：書き起こしの状態を開き直しても出す', () => {
  it('識別が不完全なまま終わった回は、その旨と理由を出す（音声は保存されている）', async () => {
    const { wrapper } = await open({
      transcribe: {
        status: 'INCOMPLETE', complete: false, retryable: false,
        reason: '認識の尾部を取り切れませんでした。',
        sources: [{
          source: 'shared', label: '共有の音', status: 'FAILED', completed: false,
          retryable: false, savedCount: 3, pendingCount: 0,
          reason: '認識の尾部を取り切れませんでした。'
        }]
      }
    })

    const notice = wrapper.get('[data-cr-transcribe-notice]')
    expect(notice.text()).toContain('書き起こしの一部を完了できませんでした')
    expect(notice.text()).toContain('認識の尾部を取り切れませんでした')
    // **音声の欠落とは別の案内**（録音は保存できている）
    expect(notice.text()).toContain('録音した音声は保存されています')
    // 音源ごとの内訳は畳んで出す（主画面は短く）
    expect(wrapper.get('[data-cr-transcribe-sources]').text()).toContain('共有の音')
  })

  it('確認できないとき（記録に残っていない）は、完全と言わない', async () => {
    const { wrapper } = await open({
      transcribe: {
        status: 'UNKNOWN', complete: false, retryable: false,
        reason: '書き起こしの収尾の結果が残っていません（確認できません）。',
        sources: []
      }
    })

    expect(wrapper.get('[data-cr-transcribe-notice]').text())
      .toContain('書き起こしの一部を完了できませんでした')
  })

  it('完全に済んだ回は、書き起こしの警告を出さない', async () => {
    const { wrapper } = await open({
      transcribe: {
        status: 'COMPLETE', complete: true, retryable: false, reason: null, sources: []
      }
    })

    expect(wrapper.find('[data-cr-transcribe-notice]').exists()).toBe(false)
  })

  it('音声が送られていない回（音声なし）は、警告ではなく「音声なし」として扱う', async () => {
    const { wrapper } = await open({
      transcribe: {
        status: 'NO_AUDIO', complete: false, retryable: false, reason: null, sources: []
      }
    })

    expect(wrapper.find('[data-cr-transcribe-notice]').exists()).toBe(false)
  })
})

describe('授業詳細：最終まとめの状態と再試行', () => {
  it('失敗した回は、失敗の理由と【最終まとめを再試行】を出す（録音は保存済みと伝える）', async () => {
    const { wrapper } = await open({ notes: [finalNote('FAILED')] })

    const block = wrapper.get('[data-cr-final-note]')
    expect(block.text()).toContain('録音は保存しました')
    expect(block.text()).toContain('最終まとめの生成に失敗しました')
    expect(wrapper.get('[data-cr-final-note-retry]').text()).toContain('再試行')
  })

  it('【最終まとめを再試行】は受理だけを待ち、状態を取り直して「作成中」にする', async () => {
    const { wrapper, calls } = await open({ notes: [finalNote('FAILED')] })

    await wrapper.get('[data-cr-final-note-retry]').trigger('click')
    await flushPromises()
    await vi.waitFor(() => {
      expect(calls.some((call) => call.url.includes('/api/admin/batch/classroom/notes/91/run')))
        .toBe(true)
    }, { timeout: 3000 })
    await flushPromises()

    // 受理できたので「作成中」に変わり、**再試行の入口は消える**（連打できない）
    expect(wrapper.get('[data-cr-final-note]').text()).toContain('作成しています')
    expect(wrapper.find('[data-cr-final-note-retry]').exists()).toBe(false)
  })

  it('走っている・できているときは、再試行の入口を出さない', async () => {
    const running = await open({ notes: [finalNote('GENERATING')] })
    expect(running.wrapper.find('[data-cr-final-note-retry]').exists()).toBe(false)
    expect(running.wrapper.get('[data-cr-final-note]').text()).toContain('作成しています')

    const ready = await open({ notes: [finalNote('READY')] })
    expect(ready.wrapper.find('[data-cr-final-note-retry]').exists()).toBe(false)
    expect(ready.wrapper.get('[data-cr-final-note]').text()).toContain('できました')
  })

  it('再試行が失敗したら、失敗を隠さず（録音は保存済みと伝えて）再試行を出したままにする', async () => {
    const { wrapper } = await open({ notes: [finalNote('FAILED')], retryFails: true })

    await wrapper.get('[data-cr-final-note-retry]').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(wrapper.get('[data-cr-final-note-notice]').text())
      .toContain('最終まとめの生成を開始できませんでした')
    expect(wrapper.get('[data-cr-final-note-notice]').text()).toContain('録音は保存しました')
    // もう一度押せる（あきらめさせない）
    expect(wrapper.find('[data-cr-final-note-retry]').exists()).toBe(true)
  })

  it('PENDING（まだ始まっていない）は「作成中」と言わず、【最終まとめを作成】を出す', async () => {
    const { wrapper } = await open({ notes: [finalNote('PENDING')] })

    const block = wrapper.get('[data-cr-final-note]')
    // **「作成中」と読ませない**（誰も実行していないのに待たせない）
    expect(block.text()).toContain('まだ作成していません')
    expect(block.text()).not.toContain('作成しています')
    expect(wrapper.get('[data-cr-final-note-retry]').text()).toContain('最終まとめを作成')
  })

  it('PENDING から作成を頼むと、受理を確認して「作成中」になる（同じノートを見る）', async () => {
    const { wrapper, calls } = await open({
      notes: [finalNote('PENDING')],
      // 受理のあとは GENERATING になる（記録の状態を読み直す）
      afterRetry: [finalNote('GENERATING')]
    })

    await wrapper.get('[data-cr-final-note-retry]').trigger('click')
    await flushPromises()
    await vi.waitFor(() => {
      expect(calls.some((call) => call.url.includes('/api/admin/batch/classroom/notes/91/run')))
        .toBe(true)
    }, { timeout: 3000 })
    await flushPromises()

    expect(wrapper.get('[data-cr-final-note]').text()).toContain('作成しています')
    expect(wrapper.find('[data-cr-final-note-retry]').exists()).toBe(false)
  })

  it('作成を頼んでも記録が PENDING のままなら、受理されたと言わず入口を残す', async () => {
    const { wrapper } = await open({
      notes: [finalNote('PENDING')],
      // 応答は受理と言うが、記録は PENDING のまま（実行が始まっていない）
      afterRetry: [finalNote('PENDING')]
    })

    await wrapper.get('[data-cr-final-note-retry]').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(wrapper.get('[data-cr-final-note-notice]').text())
      .toContain('まだ始まっていません')
    // もう一度押せる
    expect(wrapper.find('[data-cr-final-note-retry]').exists()).toBe(true)
  })

  it('再試行は録音の終了を呼ばない（まとめの操作だけを行う）', async () => {
    const { wrapper, calls } = await open({ notes: [finalNote('FAILED')] })

    await wrapper.get('[data-cr-final-note-retry]').trigger('click')
    await flushPromises()
    await flushPromises()

    // **録音の終了（/end）を二度呼ばない**（音の保存は済んでいる）
    expect(calls.filter((call) => call.url.includes('/classroom/12/end'))).toHaveLength(0)
    // 収尾（STT の finish）も呼ばない
    expect(calls.filter((call) => call.url.includes('/stt/stream/finish'))).toHaveLength(0)
  })

  it('最終まとめの行が無い回（作らなかった回）は、この欄を出さない', async () => {
    const { wrapper } = await open({ notes: [] })

    expect(wrapper.find('[data-cr-final-note]').exists()).toBe(false)
  })

  it('実行中のまとめには「状態を確認／復旧」を出す（完了・失敗には出さない）', async () => {
    const running = await open({ notes: [finalNote('GENERATING')] })
    expect(running.wrapper.find('[data-cr-final-note-recover]').exists()).toBe(true)
    // **重複起動**の入口は出さない（実行中）
    expect(running.wrapper.find('[data-cr-final-note-retry]').exists()).toBe(false)

    const ready = await open({ notes: [finalNote('READY')] })
    expect(ready.wrapper.find('[data-cr-final-note-recover]').exists()).toBe(false)

    const failed = await open({ notes: [finalNote('FAILED')] })
    expect(failed.wrapper.find('[data-cr-final-note-recover]').exists()).toBe(false)
  })

  it('「状態を確認／復旧」は後端の判定をそのまま出す（実行中なら何も変えない）', async () => {
    const { wrapper, calls } = await open({ notes: [finalNote('GENERATING')] })

    await wrapper.get('[data-cr-final-note-recover]').trigger('click')
    await flushPromises()
    await vi.waitFor(() => {
      expect(calls.some((call) => call.url.includes('/notes/91/recover'))).toBe(true)
    }, { timeout: 3000 })
    await flushPromises()

    // 実行中なので状態は変わらない（「作成しています」のまま・再試行も出ない）
    expect(wrapper.get('[data-cr-final-note-notice]').text()).toContain('実行中')
    expect(wrapper.get('[data-cr-final-note]').text()).toContain('作成しています')
    expect(wrapper.find('[data-cr-final-note-retry]').exists()).toBe(false)
  })

  it('失联していたときは、回復して【最終まとめを再試行】が出る', async () => {
    const { wrapper } = await open({
      notes: [finalNote('GENERATING')],
      recovery: {
        noteId: 91, status: 'FAILED', liveness: 'LOST', recoverable: true, recovered: true,
        reason: '実行が失われていたため、やり直せる状態に戻しました。'
      },
      afterRecovery: [finalNote('FAILED')]
    })

    await wrapper.get('[data-cr-final-note-recover]').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(wrapper.get('[data-cr-final-note-notice]').text()).toContain('やり直せる状態に戻しました')
    // 回復後は**やり直しの入口**が出る（＝ユーザーが再開できる）
    expect(wrapper.get('[data-cr-final-note-retry]').text()).toContain('再試行')
    expect(wrapper.find('[data-cr-final-note-recover]').exists()).toBe(false)
  })

  it('状態の問い合わせが失敗したら「確認できない」と出す（失联と断言しない）', async () => {
    const { wrapper } = await open({ notes: [finalNote('GENERATING')], recovery: null })

    await wrapper.get('[data-cr-final-note-recover]').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(wrapper.get('[data-cr-final-note-notice]').text()).toContain('確認できませんでした')
    // 実行中のまま（勝手に失敗にしない）
    expect(wrapper.get('[data-cr-final-note]').text()).toContain('作成しています')
  })

  it('「状態を確認／復旧」は録音の終了や STT の収尾を呼ばない', async () => {
    const { wrapper, calls } = await open({ notes: [finalNote('GENERATING')] })

    await wrapper.get('[data-cr-final-note-recover]').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(calls.filter((call) => call.url.includes('/classroom/12/end'))).toHaveLength(0)
    expect(calls.filter((call) => call.url.includes('/stt/stream/finish'))).toHaveLength(0)
  })
})
