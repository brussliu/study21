import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AI_RUN_TIMEOUT_MS, runJpnWordAi } from '@/api/japanese'

/**
 * AI 取得の受付（`POST /api/admin/batch/japanese-word-ai/run`）の HTTP 待ち時間。
 *
 * <p>2026-09-27 に**受付**（{@code QUEUED} を積んで即返る）へ変えたので、実際は数十ミリ秒で返る。
 * それでも待ち時間を 60 秒にしてあるのは、<b>nginx の {@code proxy_read_timeout 60s} と同じ値に
 * 揃えておく</b>ため（利用者の指示 2026-09-27。片方だけ変えると短いほうで切れる）。</p>
 *
 * <p>確かめる接縫: <b>受付の呼び出しが 10 秒では切らず、60 秒で切る</b>こと。</p>
 */
describe('日本語単語 AI 取得の待ち時間', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  /** abort されたら reject する fetch（＝応答が返らない状態を再現する）。 */
  function stubNeverRespondingFetch(): { aborted: string[] } {
    const aborted: string[] = []
    vi.stubGlobal('fetch', vi.fn((_url: string, init?: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => {
          aborted.push('abort')
          reject(new DOMException('aborted', 'AbortError'))
        })
      })
    ))
    return { aborted }
  }

  it('上限は 60 秒（nginx の proxy_read_timeout と同じ値）', () => {
    expect(AI_RUN_TIMEOUT_MS).toBe(60_000)
  })

  it('10 秒では切らず、60 秒まで待つ', async () => {
    const { aborted } = stubNeverRespondingFetch()

    const promise = runJpnWordAi('DETAIL', [101, 102])
    const handled = promise.catch(() => undefined)

    // 共通クライアントの既定値（10 秒）では切らない
    await vi.advanceTimersByTimeAsync(10_000)
    expect(aborted).toHaveLength(0)

    // 60 秒で切れる（画面には「リクエストがタイムアウトしました」が出る）
    await vi.advanceTimersByTimeAsync(50_000)
    await expect(promise).rejects.toMatchObject({ code: 'TIMEOUT' })
    expect(aborted).toHaveLength(1)

    await handled
  })
})
