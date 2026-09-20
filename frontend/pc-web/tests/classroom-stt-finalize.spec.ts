import { describe, expect, it } from 'vitest'
import { normalizeSttFinalize, summarizeSttFinalize } from '@/features/classroom/stt-finalize'

/**
 * 書き起こしの収尾（finish）の結果の**読み取り**の検証。
 *
 * <p>見張るのは「`error` が無い＝成功」という誤読。後端は**やり直しても直らない終端**を
 * `error=null` / `finalizeCompleted=false` / `notice=理由` で返す（画面が永久に再試行を
 * 出し続けないため）。ここを成功と読むと、書き起こしが欠けたまま「保存しました」と言う。</p>
 */
describe('書き起こしの収尾の読み取り', () => {
  it('error が null でも finalizeCompleted=false / retryable=false なら「不完整な終わり」（成功ではない）', () => {
    const result = normalizeSttFinalize({
      error: null,
      finalizeStatus: 'FAILED',
      finalizeCompleted: false,
      retryable: false,
      notice: '認識の尾部を取り切れませんでした（やり直しても直りません）。'
    }, 'mic')

    expect(result.kind).toBe('INCOMPLETE_UNRECOVERABLE')
    expect(result.complete).toBe(false)
    // **終わってよい**（利用者を永久に待たせない）。ただし完全ではない
    expect(result.canFinish).toBe(true)
    expect(result.retryable).toBe(false)
    expect(result.notice).toContain('やり直しても直りません')
  })

  it('完全に成功した回は COMPLETE（終わってよく、識別も完全）', () => {
    const result = normalizeSttFinalize({
      error: null, finalizeStatus: 'SAVED', finalizeCompleted: true, retryable: false,
      savedCount: 12, pendingCount: 0, notice: null
    }, 'mic')

    expect(result.kind).toBe('COMPLETE')
    expect(result.complete).toBe(true)
    expect(result.canFinish).toBe(true)
    expect(result.savedCount).toBe(12)
  })

  it('やり直せる失敗は再試行でき、終わってはいけない', () => {
    const result = normalizeSttFinalize({
      error: '保存できなかった文があります。', finalizeStatus: 'FAILED',
      finalizeCompleted: false, retryable: true, recovery: 'RESAVE_PENDING', pendingCount: 3
    }, 'shared')

    expect(result.kind).toBe('RETRYABLE_FAILURE')
    expect(result.retryable).toBe(true)
    expect(result.canFinish).toBe(false)
    expect(result.complete).toBe(false)
    expect(result.source).toBe('shared')
  })

  it('まだ収尾の途中（retryable=true・completed=false）は PENDING（失敗ではない）', () => {
    const result = normalizeSttFinalize({
      error: null, finalizeStatus: 'FINALIZING', finalizeCompleted: false, retryable: true
    }, 'mic')

    expect(result.kind).toBe('PENDING')
    expect(result.canFinish).toBe(false)
    expect(result.retryable).toBe(true)
  })

  it('音声なし・発話なしは「正常な終端」として区別する（システムのエラーにしない）', () => {
    const noAudio = normalizeSttFinalize({
      error: null, finalizeStatus: 'NO_AUDIO', finalizeCompleted: true, retryable: false
    }, 'shared')
    const noUtterance = normalizeSttFinalize({
      error: null, finalizeStatus: 'NO_UTTERANCE', finalizeCompleted: true, retryable: false
    }, 'mic')

    expect(noAudio.kind).toBe('NO_AUDIO')
    expect(noAudio.complete).toBe(true)
    expect(noAudio.canFinish).toBe(true)
    expect(noUtterance.kind).toBe('NO_UTTERANCE')
    expect(noUtterance.complete).toBe(true)
  })

  it('旧い後端（finalizeCompleted が無い）は成功と見なさない＝UNKNOWN', () => {
    // 昔の応答（error だけ）。ここを成功と読むと、欠けた書き起こしを「保存しました」と言う
    const old = normalizeSttFinalize({ error: null }, 'mic')

    expect(old.kind).toBe('UNKNOWN')
    expect(old.complete).toBe(false)
    expect(old.canFinish).toBe(false)
    expect(old.retryable).toBe(true)
  })

  it('欄そのものが無い（応答を失った）ときも成功と見なさない', () => {
    const missing = normalizeSttFinalize(null, 'mic')

    expect(missing.kind).toBe('UNKNOWN')
    expect(missing.complete).toBe(false)
    expect(missing.canFinish).toBe(false)
  })

  it('「済んだ」と言いながら段階が途中の矛盾した応答も、成功と読まない', () => {
    const contradict = normalizeSttFinalize({
      error: null, finalizeStatus: 'AUDIO_ACCEPTING', finalizeCompleted: true, retryable: false
    }, 'mic')

    expect(contradict.kind).toBe('UNKNOWN')
    expect(contradict.complete).toBe(false)
  })

  it('空文字の欄は「無し」と同じに扱う（null と空文字で結果が変わらない）', () => {
    const blank = normalizeSttFinalize({
      error: '', finalizeStatus: 'SAVED', finalizeCompleted: true, retryable: false, notice: '  '
    }, 'mic')

    expect(blank.kind).toBe('COMPLETE')
    expect(blank.notice).toBeNull()
  })
})

/**
 * 音源ごとの結果を**1 つのまとめ**にする部分の検証（利用者の指摘 ①-4）。
 *
 * <p>「全部成功」以外を全部失敗にしない: **やり直せる失敗**（終われない）と、
 * **やり直しても直らない不完整な終わり**（終わってよいが完全ではない）と、
 * **音声なし・発話なし**（正常な終端）を分ける。</p>
 */
describe('収尾のまとめ（終わってよいか／識別が完全か）', () => {
  const マイク = { source: 'mic', label: 'マイク' }
  const 共有 = { source: 'shared', label: '共有の音' }

  it('両方とも完全なら、終わってよく識別も完全', () => {
    const summary = summarizeSttFinalize([
      normalizeSttFinalize({ finalizeStatus: 'SAVED', finalizeCompleted: true, retryable: false }, 'mic'),
      normalizeSttFinalize({ finalizeStatus: 'SAVED', finalizeCompleted: true, retryable: false }, 'shared')
    ], [マイク, 共有])

    expect(summary.canFinish).toBe(true)
    expect(summary.complete).toBe(true)
    expect(summary.notice).toBeNull()
  })

  it('一方が「やり直しても直らない不完整な終わり」なら、**終わってよいが識別は不完全**', () => {
    const summary = summarizeSttFinalize([
      normalizeSttFinalize({ finalizeStatus: 'SAVED', finalizeCompleted: true, retryable: false }, 'mic'),
      normalizeSttFinalize({
        error: null, finalizeStatus: 'FAILED', finalizeCompleted: false, retryable: false,
        notice: '尾部を取り切れませんでした。'
      }, 'shared')
    ], [マイク, 共有])

    expect(summary.canFinish).toBe(true)
    expect(summary.complete).toBe(false)
    expect(summary.incompleteSources).toHaveLength(1)
    expect(summary.notice).toContain('共有の音')
    expect(summary.notice).toContain('尾部を取り切れませんでした')
  })

  it('一方が「やり直せる失敗」なら、終われない（再試行に回す）', () => {
    const summary = summarizeSttFinalize([
      normalizeSttFinalize({ finalizeStatus: 'SAVED', finalizeCompleted: true, retryable: false }, 'mic'),
      normalizeSttFinalize({
        error: '保存できなかった文があります。', finalizeStatus: 'FAILED',
        finalizeCompleted: false, retryable: true
      }, 'shared')
    ], [マイク, 共有])

    expect(summary.canFinish).toBe(false)
    expect(summary.complete).toBe(false)
    expect(summary.retryableSources).toHaveLength(1)
  })

  it('確認できない（欄が無い）音源が残っているあいだも、終われない', () => {
    const summary = summarizeSttFinalize([
      normalizeSttFinalize({ finalizeStatus: 'SAVED', finalizeCompleted: true, retryable: false }, 'mic'),
      normalizeSttFinalize({ error: null }, 'shared')
    ], [マイク, 共有])

    expect(summary.canFinish).toBe(false)
    expect(summary.unknownSources).toHaveLength(1)
  })

  it('音声なし・発話なしは「正常な終端」として、失敗に数えない', () => {
    const summary = summarizeSttFinalize([
      normalizeSttFinalize({ finalizeStatus: 'SAVED', finalizeCompleted: true, retryable: false }, 'mic'),
      normalizeSttFinalize({ finalizeStatus: 'NO_AUDIO', finalizeCompleted: true, retryable: false }, 'shared')
    ], [マイク, 共有])

    expect(summary.canFinish).toBe(true)
    // 使った音源が「成功」または「音声なし・発話なし」で説明できている＝識別は完全
    expect(summary.complete).toBe(true)
    expect(summary.quietSources).toHaveLength(1)
    expect(summary.notice).toContain('音声は送られていませんでした')
  })

  it('単音源の回に、使っていない音源の「音声なし」を数えない（渡さなければ失敗しない）', () => {
    // 単音源＝マイクだけを渡す（共有は渡さない）
    const summary = summarizeSttFinalize([
      normalizeSttFinalize({ finalizeStatus: 'SAVED', finalizeCompleted: true, retryable: false }, 'mic')
    ], [マイク, 共有])

    expect(summary.canFinish).toBe(true)
    expect(summary.complete).toBe(true)
    expect(summary.quietSources).toHaveLength(0)
    expect(summary.notice).toBeNull()
  })

  it('無音（音声は届いたが発話が無い）と、本当の認識故障を混同しない', () => {
    // 発話なし＝正常な終端（システムのエラーにしない）
    const quiet = normalizeSttFinalize({
      error: null, finalizeStatus: 'NO_UTTERANCE', finalizeCompleted: true, retryable: false,
      savedCount: 0, pendingCount: 0
    }, 'mic')
    // 認識の故障（やり直しても直らない）＝不完整な終わり
    const broken = normalizeSttFinalize({
      error: null, finalizeStatus: 'FAILED', finalizeCompleted: false, retryable: false,
      notice: '認識サービスに接続できませんでした。'
    }, 'mic')

    expect(quiet.kind).toBe('NO_UTTERANCE')
    expect(quiet.complete).toBe(true)
    expect(broken.kind).toBe('INCOMPLETE_UNRECOVERABLE')
    expect(broken.complete).toBe(false)
  })

  it('一方が成功、もう一方がやり直しても直らない不完整でも、終わってよい（識別は不完全）', () => {
    const summary = summarizeSttFinalize([
      normalizeSttFinalize({
        finalizeStatus: 'SAVED', finalizeCompleted: true, retryable: false, savedCount: 12
      }, 'mic'),
      normalizeSttFinalize({
        error: null, finalizeStatus: 'FAILED', finalizeCompleted: false, retryable: false,
        notice: '認識の尾部を取り切れませんでした。'
      }, 'shared')
    ], [マイク, 共有])

    // 直らないものを永久に再試行させない（終わってよい）が、完全ではない
    expect(summary.canFinish).toBe(true)
    expect(summary.complete).toBe(false)
    expect(summary.completeSources).toHaveLength(1)
    expect(summary.incompleteSources).toHaveLength(1)
  })

  it('使っていない音源を渡さなければ、単音源はそのまま完了として扱う', () => {
    // 単音源（マイクだけ）: 共有は渡さない＝「未使用」で失敗にしない
    const summary = summarizeSttFinalize([
      normalizeSttFinalize({
        finalizeStatus: 'SAVED', finalizeCompleted: true, retryable: false, savedCount: 5
      }, 'mic')
    ], [マイク, 共有])

    expect(summary.canFinish).toBe(true)
    expect(summary.complete).toBe(true)
    expect(summary.unknownSources).toHaveLength(0)
    expect(summary.retryableSources).toHaveLength(0)
  })

  it('片方の音源が完全で、もう片方が発話なしでも、識別は完全として扱う', () => {
    const summary = summarizeSttFinalize([
      normalizeSttFinalize({
        finalizeStatus: 'SAVED', finalizeCompleted: true, retryable: false, savedCount: 8
      }, 'mic'),
      normalizeSttFinalize({ finalizeStatus: 'NO_UTTERANCE', finalizeCompleted: true, retryable: false }, 'shared')
    ], [マイク, 共有])

    expect(summary.complete).toBe(true)
    expect(summary.completeSources).toHaveLength(1)
    expect(summary.quietSources).toHaveLength(1)
  })
})
