import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  RecordingTimeline,
  TimelineStore,
  TIMELINE_STORAGE_PREFIX
} from '@/features/classroom/timeline'

/**
 * 授業録音：**録音と回放の 1 つの時間軸**（`RecordingTimeline`）の検証。
 *
 * <p>決めた規則（`docs/DECISIONS.md` に理由を書く）:</p>
 * <ul>
 *   <li>時間の基準は**累計のサンプル位置**（16kHz）。`Date.now()` も画面のタイマーも使わない。</li>
 *   <li>**一時停止のあいだは時間軸を進めない**（録音していない時間は録音に入っていないので、
 *       回放の位置と書き起こしの時刻が食い違わない）。</li>
 *   <li>進めるのは**1 つの報告につき 1 回だけ**: 止める・送る・確認のどれかで 2 度進めない
 *       （二重に進めると、音より先へ文がずれる）。</li>
 * </ul>
 */
describe('授業録音：統一の録音回放時間軸', () => {
  it('録音を始めた時点を 0 とし、渡されたサンプルの位置で進む（秒へ直せる）', () => {
    const timeline = new RecordingTimeline()
    timeline.begin()
    expect(timeline.positionSamples()).toBe(0)

    // 呼ぶ側は「入力レートの累計サンプル数」を渡す（時間軸は 16kHz へ直して積む）
    timeline.observeCapture(96_000, 48_000)
    expect(timeline.positionSamples()).toBe(32_000)
    expect(timeline.positionSeconds()).toBe(2)
  })

  it('一時停止のあいだは進めない（停止 → 再開のあと位置が連続する）', () => {
    const timeline = new RecordingTimeline()
    timeline.begin()
    timeline.observeCapture(48_000, 48_000) // 1 秒ぶん録れた
    expect(timeline.positionSeconds()).toBe(1)

    timeline.pause()
    // 停止のあいだは音を採らない（採った報告は来ない）。`AudioContext` の時計だけが進む
    timeline.observeCapture(48_000 + 48_000 * 30, 48_000)
    expect(timeline.positionSeconds()).toBe(1)

    // 再開した時点の時計（31 秒）を基準にする＝止めていた 30 秒は時間軸へ入らない
    timeline.resume(48_000 * 31, 48_000)
    expect(timeline.capturedSamples()).toBe(16_000)
    // 再開して最初の報告（33 秒＝再開から 2 秒）は、再開した時点からの進みぶんだけ積む
    timeline.observeCapture(48_000 * 33, 48_000)
    // 止めていた 30 秒は入らず、録れた 1＋2 秒だけが続く
    expect(timeline.positionSeconds()).toBe(3)
    expect(timeline.capturedSamples()).toBe(48_000)
  })

  it('64 秒録って止め、20 秒続けて録ったら 64 秒から続く（0 へ戻らない・詰めない）', () => {
    const timeline = new RecordingTimeline()
    timeline.begin()
    // 録音の先頭（0 秒）を基準にして、1 秒ぶんずつ 64 秒まで ＝ 64 秒ぶん（48kHz）
    for (let second = 1; second <= 64; second += 1) {
      timeline.observeCapture(48_000 * second, 48_000)
    }
    expect(timeline.positionSeconds()).toBe(64)
    expect(timeline.capturedSamples()).toBe(1_024_000)

    timeline.pause()
    // 停止のあいだに 5 分経った（回放には入っていない）
    timeline.observeCapture(48_000 * (64 + 300), 48_000)
    expect(timeline.positionSeconds()).toBe(64)

    // 続きの録音: 保存済みの 64 秒（16kHz で 1,024,000 サンプル）から続ける。
    // 音源を作り直したので時計は 0 から（`resume` に基準を渡さない＝次の報告が基準になる）
    timeline.restore(1_024_000)
    expect(timeline.positionSeconds()).toBe(64)
    timeline.resume()
    for (let second = 0; second <= 20; second += 1) {
      timeline.observeCapture(48_000 * second, 48_000)
    }
    expect(timeline.positionSeconds()).toBe(84)
    expect(timeline.capturedSamples()).toBe(1_344_000)
  })

  it('送り直し・断線で古い報告が来ても時間は戻らない（前へだけ進む）', () => {
    const timeline = new RecordingTimeline()
    timeline.begin()
    timeline.observeCapture(48_000, 48_000)
    timeline.observeSent(32_000)
    timeline.observeConfirmed(24_000)
    // 「録れている音の位置」は採った音まで（送った・確認の位置は「戻さない」ための控え）
    expect(timeline.capturedSamples()).toBe(16_000)

    // 断線からの復帰で、すでに送った古い区間をもう一度送る（位置は戻さない）
    timeline.observeSent(0)
    timeline.observeCapture(96_000, 48_000) // 断線のあと 1 秒ぶん録れた
    timeline.observeConfirmed(8_000)
    expect(timeline.capturedSamples()).toBe(32_000)
    // 確認できた位置は**戻らない**（前のセッションで 24,000 まで進んでいるので 8,000 では下がらない）
    expect(timeline.confirmedSamples()).toBe(24_000)
    // 送った位置も戻らない（古い区間を送り直しても、投げた位置は前のまま）
    expect(timeline.sentSamples()).toBe(32_000)
  })

  it('採った・送った・確認できたは別々に保つ（送っただけの音を「回放できる」と言わない）', () => {
    const timeline = new RecordingTimeline()
    timeline.begin()
    timeline.observeCapture(160_000, 16_000) // 10 秒ぶん採った
    timeline.observeSent(60_000)
    // 送っただけでは「録れている音の位置」にしない（フレームの位置は採った音までで送る）
    expect(timeline.capturedSamples()).toBe(160_000)
    expect(timeline.positions()).toEqual({
      capturedSamples: 160_000, sentSamples: 60_000, confirmedSamples: 0
    })

    timeline.observeConfirmed(40_000)
    expect(timeline.positions().confirmedSamples).toBe(40_000)
    expect(timeline.capturedSamples()).toBe(160_000)
    // 確認できた位置は、採った音より先へは出ない（送っただけ・確認だけの位置で音を先取りしない）
    expect(timeline.sentSamples()).toBe(60_000)

    // 後端が「保存済みの位置」を返しても、**いまの位置より後ろへは戻らない**
    // （短いほうを採ると、同じ位置の音をもう一度送って「古い」として捨てられる）
    timeline.restore(120_000)
    expect(timeline.positionSamples()).toBe(160_000)
    expect(timeline.capturedSamples()).toBe(160_000)
    // 保存済みの位置を戻しても、確認できた位置は後ろへ戻らない（送り直しで時間が跳ねない）
    expect(timeline.positions().confirmedSamples).toBe(40_000)

    // 先の位置が来たら、そちらへ進む（後端が 200 秒ぶん保存していれば時間軸も 200 秒へ）
    timeline.restore(3_200_000)
    expect(timeline.positionSeconds()).toBe(200)
    expect(timeline.capturedSamples()).toBe(3_200_000)
  })

  it('保存した位置から復帰できる（画面の開き直し・後端の再起動のあと）', () => {
    const timeline = new RecordingTimeline()
    timeline.begin()
    timeline.observeCapture(48_000 * 90, 48_000)
    expect(timeline.positionSeconds()).toBe(90)

    // 新しい時間軸（＝画面の開き直し）へ、保存していた位置を戻す
    const restored = new RecordingTimeline()
    expect(timeline.capturedSamples()).toBe(1_440_000)
    restored.restore(timeline.capturedSamples())
    expect(restored.positionSeconds()).toBe(90)
    restored.begin()
    expect(restored.capturedSamples()).toBe(1_440_000)
  })

  it('音源ごとの採った位置を別に持つ（先に進んだ音源で、遅れた音源の先頭を飛ばさない）', () => {
    const timeline = new RecordingTimeline()
    timeline.begin()
    // マイクは 0 秒から、共有は 2 秒ぶん遅れて繋がる（両方とも**同じ時計**の値で報告する）
    timeline.observeCapture(0, 48_000, 'mic')
    timeline.observeCapture(48_000 * 2, 48_000, 'mic')
    expect(timeline.sourceCapturedSamples('mic')).toBe(32_000)
    // 共有はまだ音を採っていない（時間軸の位置をそのまま使う＝録音の先頭からではない）
    expect(timeline.sourceCapturedSamples('shared')).toBe(32_000)
    timeline.observeCapture(48_000 * 3, 48_000, 'shared')
    /*
     * 時間軸は 1 つ（先へ進んだほうに合わせる＝48,000）だが、**音源ごとの位置は別**:
     * マイクは自分の音を 2 秒ぶん、共有は 1 秒ぶん採った（共有は 2 秒から繋がった）。
     */
    expect(timeline.positionSamples()).toBe(48_000)
    expect(timeline.sourceCapturedSamples('mic')).toBe(32_000)
    expect(timeline.sourceCapturedSamples('shared')).toBe(48_000)
    // 立ち上げた時点では、共有は**まだ採っていない**＝時間軸の位置から始める
    const fresh = new RecordingTimeline()
    fresh.restore(96_000)
    fresh.begin()
    fresh.alignSourceCaptured(['mic', 'shared'])
    expect(fresh.sourceCapturedSamples('mic')).toBe(96_000)
    expect(fresh.sourceCapturedSamples('shared')).toBe(96_000)
  })

  it('音源ごとのフレーム番号を覚えて、続きの番号から送れる（張り直しで 1 へ戻らない）', () => {
    const timeline = new RecordingTimeline()
    timeline.begin()
    timeline.observeCapture(48_000, 48_000)
    timeline.observeSent(16_000, 'mic', 7)
    timeline.observeSent(16_000, 'shared', 3)

    expect(timeline.nextFrameNo('mic')).toBe(8)
    expect(timeline.nextFrameNo('shared')).toBe(4)
    expect(timeline.nextFrameNo('unknown')).toBe(1)
  })
})

/**
 * 保存（`localStorage`）。**後端の分塊の状態（`GET /chunks`）だけに頼らない**:
 * 分塊は「送れた音声」の位置しか持たないので、まだ送れていない音の位置は画面が残す。
 */
describe('授業録音：時間軸の保存（画面を離れても残る）', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('記録ごとに位置とフレーム番号を保存し、読み戻せる', () => {
    const store = new TimelineStore(31)
    store.save({ playedSamples: 512_000, nextFrameNo: { mic: 12 } })

    const loaded = new TimelineStore(31).load()
    expect(loaded?.playedSamples).toBe(512_000)
    expect(loaded?.nextFrameNo.mic).toBe(12)
    expect(localStorage.getItem(`${TIMELINE_STORAGE_PREFIX}31`)).not.toBeNull()
  })

  it('別の記録の値を混ぜない', () => {
    new TimelineStore(31).save({ playedSamples: 1000, nextFrameNo: {} })
    expect(new TimelineStore(32).load()).toBeNull()
  })

  it('壊れた保存内容は無かったことにする（落ちない）', () => {
    localStorage.setItem(`${TIMELINE_STORAGE_PREFIX}31`, '{壊れている')
    expect(new TimelineStore(31).load()).toBeNull()
    localStorage.setItem(`${TIMELINE_STORAGE_PREFIX}31`, JSON.stringify({ playedSamples: 'x' }))
    expect(new TimelineStore(31).load()).toBeNull()
  })

  it('消したときは保存も消す（録音を消したのに位置が残らない）', () => {
    const store = new TimelineStore(31)
    store.save({ playedSamples: 1000, nextFrameNo: {} })
    store.clear()
    expect(store.load()).toBeNull()
  })

  it('保存できない環境（容量・無効化）でも録音は続く', () => {
    const store = new TimelineStore(31, {
      getItem: () => { throw new Error('読めません') },
      setItem: () => { throw new Error('書けません') },
      removeItem: () => { throw new Error('消せません') }
    })
    expect(() => store.save({ playedSamples: 1, nextFrameNo: {} })).not.toThrow()
    expect(store.load()).toBeNull()
    expect(() => store.clear()).not.toThrow()
  })

  it('ページを離れるときも保存する（開き直して続きを録れる）', () => {
    const setItem = vi.fn()
    const store = new TimelineStore(31, { getItem: () => null, setItem, removeItem: () => undefined })
    store.save({ playedSamples: 96_000, nextFrameNo: { shared: 4 } })
    expect(setItem).toHaveBeenCalledTimes(1)
    expect(String(setItem.mock.calls[0]?.[1])).toContain('"playedSamples":96000')
  })
})
