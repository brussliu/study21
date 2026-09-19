import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import ClassroomDetailView from '@/views/classroom/ClassroomDetailView.vue'

/**
 * 授業録音の**結合（再生用の 1 本）の状態**を画面に出す検証（利用者の指摘③）。
 *
 * <p>見張るのは:</p>
 * <ol>
 *   <li>「音声が保存できている」と「再生用の 1 本ができている」を**分けて**出す。</li>
 *   <li>結合に失敗していても**分塊は残っている**と言える（音を失ったと言わない）。</li>
 *   <li>作れなかったときの**やり直し**の入口を出し、実際にサーバーを呼ぶ。</li>
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

/** 記録 1 件（結合の状態を差し替えられる）。 */
function detail(assembly: Record<string, unknown>): Record<string, unknown> {
  return {
    recordId: 12, recordNo: 'CR1', title: '数学 二次関数の復習', subject: '数学',
    languageMode: 'ja', presetId: null, presetName: null, presetText: null,
    status: 'STOPPED', statusLabel: '停止', startTime: null, endTime: null,
    durationSeconds: 120, transcribedChars: 40, summaryJson: null,
    hasAudio: true, audioMime: 'audio/webm',
    segments: [], notes: [], version: 3, createdAt: null, updatedAt: null,
    assembly
  }
}

function mockApi(options: {
  assembly: Record<string, unknown>
  retry?: Record<string, unknown>
}): { calls: Call[] } {
  const calls: Call[] = []
  let retried = false
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    const method = (init?.method ?? 'GET').toUpperCase()
    calls.push({ url: String(url), method })
    const ok = (data: unknown): Response => new Response(
      JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }),
      { status: 200, headers: { 'Content-Type': 'application/json' } })
    const target = String(url)
    if (target.includes('/assembly/retry')) {
      retried = true
      return ok(options.retry ?? {
        state: 'READY', complete: true, storedChunks: 3, durationSeconds: 120,
        missingSeqs: [], reason: null
      })
    }
    return ok(detail(retried && options.retry !== undefined ? options.retry : options.assembly))
  }))
  return { calls }
}

async function open(options: Parameters<typeof mockApi>[0]): Promise<{ wrapper: VueWrapper; calls: Call[] }> {
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
  window.sessionStorage.clear()
  vi.unstubAllGlobals()
})

describe('授業録音：再生用の音声（結合）の状態', () => {
  it('結合が済んでいれば「音声は保存されています」と出す', async () => {
    const { wrapper } = await open({
      assembly: {
        state: 'READY', complete: true, storedChunks: 3, durationSeconds: 120,
        missingSeqs: [], reason: null
      }
    })

    const note = wrapper.get('[data-cr-audio-note]').text()
    expect(note).toContain('音声は保存されています')
    expect(note).toContain('再生できます')
    // 作り直しの入口は出さない（正常にできているので）
    expect(wrapper.find('[data-cr-assembly-retry]').exists()).toBe(false)
  })

  it('結合に失敗していても「音声は保存されています」と言い、作り直しの入口を出す', async () => {
    const { wrapper } = await open({
      assembly: {
        state: 'FAILED', complete: false, storedChunks: 3, durationSeconds: null,
        missingSeqs: [], reason: '続録の音声を 1 本に結合できませんでした。'
      }
    })

    const note = wrapper.get('[data-cr-audio-note]').text()
    // **音を失ったと言わない**（分塊は残っている）
    expect(note).toContain('音声は保存されています')
    expect(note).toContain('3 件')
    expect(note).toContain('再生用の音声はまだ作れていません')
    expect(wrapper.get('[data-cr-assembly-retry]').text()).toContain('作り直す')
  })

  it('まだ作っていないあいだは「作成中」と出す（成功とも失敗とも言わない）', async () => {
    const { wrapper } = await open({
      assembly: {
        state: 'NONE', complete: false, storedChunks: 3, durationSeconds: null,
        missingSeqs: [], reason: null
      }
    })

    const note = wrapper.get('[data-cr-audio-note]').text()
    expect(note).toContain('作成中')
    expect(note).not.toContain('失敗')
  })

  it('【作り直す】を押すとサーバーへ頼み、結果を出す', async () => {
    const { wrapper, calls } = await open({
      assembly: {
        state: 'FAILED', complete: false, storedChunks: 3, durationSeconds: null,
        missingSeqs: [], reason: '結合できませんでした。'
      },
      retry: {
        state: 'READY', complete: true, storedChunks: 3, durationSeconds: 120,
        missingSeqs: [], reason: null
      }
    })

    await wrapper.get('[data-cr-assembly-retry]').trigger('click')
    await flushPromises()

    expect(calls.some((call) => call.url.includes('/assembly/retry') && call.method === 'POST')).toBe(true)
    expect(wrapper.get('[data-cr-assembly-notice]').text()).toContain('作り直しました')
  })
})
