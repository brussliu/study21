import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@study21/web-shared'
import {
  classroomAudioUrl,
  createClassroomRecord,
  deleteClassroomRecord,
  endClassroomRecord,
  fetchClassroomOptions,
  fetchClassroomPresets,
  fetchClassroomRecord,
  fetchClassroomSegments,
  mergeClassroomSegments,
  orderClassroomSegments,
  runClassroomNote,
  searchClassroomRecords,
  segmentSpeakerOf,
  startClassroomRecord,
  uploadClassroomChunk,
  type ClassroomSegment
} from '@/api/classroom'

/**
 * 授業録音 / AI 授業記録の API（`/api/user/classroom` ＋ admin-api の薄い入口）。
 *
 * 契約は user-api の `ClassroomController` と 1 対 1:
 *   GET    /options                      … 画面が使う設定（API Key は返らない）
 *   GET    /presets                      … 前置詞プリセット（GLOBAL + 自分のスコープ）
 *   POST   /classroom                    … 作成（RECORDING）
 *   POST   /classroom/{id}/start         … 開始
 *   POST   /classroom/{id}/chunks?seq=N  … 分塊アップロード（multipart）
 *   GET    /classroom/{id}/segments?afterSeq=N … 追記セグメント（ポーリング）
 *   GET    /classroom/{id}               … 詳細（転写全文・ノート・前置詞）
 *   GET    /classroom?status=&page=&size= … 一覧
 *   POST   /classroom/{id}/end           … 終了（finalNoteId と runPath）
 *   GET    /classroom/{id}/audio         … 元音声（Range/206。`<audio>` がそのまま読む）
 *   DELETE /classroom/{id}               … 削除
 *   POST   /api/admin/batch/classroom/notes/{noteId}/run … AI ノート生成（admin-api）
 *
 * ここでは URL・メソッド・body（クエリ）・エラーの扱いを固定する。
 * 画面（4 画面）がこのどれを呼ぶかは `classroom-ai.spec.ts` が見張る。
 */
function ok(data: unknown): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

type Call = { url: string; method: string; json: Record<string, unknown> | null; body: unknown }

function recorded(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return fetchMock.mock.calls.map((call) => {
    const init = (call[1] ?? {}) as RequestInit
    const body = init.body
    return {
      url: String(call[0]),
      method: (init.method ?? 'GET').toUpperCase(),
      json: typeof body === 'string' ? (JSON.parse(body) as Record<string, unknown>) : null,
      body
    }
  })
}

/** 作成・開始の応答（サーバーの RecordStatus と同じ形）。 */
function status(recordId = 12): unknown {
  return { recordId, recordNo: `CR20260914-${recordId}`, status: 'RECORDING', statusLabel: '録音中', version: 1 }
}

describe('授業録音 API: user-api（/api/user/classroom）', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn(async () => ok(status()))
    vi.stubGlobal('fetch', fetchMock)
  })

  it('設定（options）と前置詞プリセットを GET で取る', async () => {
    fetchMock.mockImplementation(async (url: string) => {
      if (String(url).includes('/presets')) {
        return ok([{ presetId: 1, scope: 'GLOBAL', name: '通常の授業', text: '一般的な授業。', displayOrder: 1 }])
      }
      return ok({
        enabled: true, chunkSeconds: 20, maxRecordingMinutes: 120, retentionDays: 30,
        dailyLimit: 0, usedToday: 0, notice: ''
      })
    })

    const options = await fetchClassroomOptions()
    expect(options.data.chunkSeconds).toBe(20)
    expect(recorded(fetchMock).at(-1)).toMatchObject({
      url: '/api/user/classroom/options',
      method: 'GET'
    })

    const presets = await fetchClassroomPresets()
    expect(presets.data[0]?.name).toBe('通常の授業')
    expect(recorded(fetchMock).at(-1)).toMatchObject({
      url: '/api/user/classroom/presets',
      method: 'GET'
    })
  })

  it('作成は POST /api/user/classroom に授業名・言語モード・前置詞 ID を送る', async () => {
    const response = await createClassroomRecord({ title: '数学 二次関数', languageMode: 'zh-en', presetId: 3 })

    expect(response.data.recordId).toBe(12)
    expect(recorded(fetchMock).at(-1)).toMatchObject({
      url: '/api/user/classroom',
      method: 'POST',
      json: { title: '数学 二次関数', languageMode: 'zh-en', presetId: 3 }
    })
  })

  it('開始は POST /api/user/classroom/{id}/start（body なし）', async () => {
    await startClassroomRecord(12)

    expect(recorded(fetchMock).at(-1)).toMatchObject({
      url: '/api/user/classroom/12/start',
      method: 'POST'
    })
  })

  it('分塊は multipart で seq をクエリに載せ、Content-Type は自分で付けない', async () => {
    fetchMock.mockImplementation(async () => ok({
      recordId: 12, seq: 3, nextSeq: 4, appendedSegments: [], pendingNoteId: null,
      triggered: false, status: 'RECORDING', runPath: null
    }))

    const blob = new Blob([new Uint8Array([1, 2, 3])], { type: 'audio/webm' })
    const response = await uploadClassroomChunk(12, 3, blob)

    expect(response.data.nextSeq).toBe(4)
    const call = recorded(fetchMock).at(-1)
    expect(call).toMatchObject({ url: '/api/user/classroom/12/chunks?seq=3', method: 'POST' })
    expect(call?.body).toBeInstanceOf(FormData)
    const form = call?.body as FormData
    expect(form.get('seq')).toBe('3')
    expect(form.get('file')).toBeInstanceOf(Blob)
    // 書き起こし用の PCM を渡さないときは stt パートを付けない（サーバーは保存した分塊で認識する）
    expect(form.get('stt')).toBeNull()
  })

  it('書き起こし用の PCM を渡すと stt パート（audio/L16）として一緒に送る', async () => {
    fetchMock.mockImplementation(async () => ok({
      recordId: 12, seq: 3, nextSeq: 4, appendedSegments: [], pendingNoteId: null,
      triggered: false, status: 'RECORDING', runPath: null
    }))

    const blob = new Blob([new Uint8Array([1, 2, 3])], { type: 'audio/webm' })
    const pcm = new Blob([new Int16Array([0, 100, -100])], { type: 'audio/L16' })
    await uploadClassroomChunk(12, 3, blob, pcm)

    const form = recorded(fetchMock).at(-1)?.body as FormData
    const sentPcm = form.get('stt') as File
    expect(sentPcm).toBeInstanceOf(File)
    // FormData に入れるとブラウザは MIME を小文字にする（サーバーも小文字で比較する）
    expect(sentPcm.type).toBe('audio/l16')
    expect(sentPcm.size).toBe(6)
    // 再生用の音声は今までどおり file パート
    expect(form.get('file')).toBeInstanceOf(Blob)
  })

  it('セグメントは afterSeq つきで GET する（既定 0）', async () => {
    fetchMock.mockImplementation(async () => ok({ items: [], nextSeq: 0 }))

    await fetchClassroomSegments(12)
    expect(recorded(fetchMock).at(-1)).toMatchObject({
      url: '/api/user/classroom/12/segments?afterSeq=0',
      method: 'GET'
    })

    await fetchClassroomSegments(12, 7)
    expect(recorded(fetchMock).at(-1)).toMatchObject({
      url: '/api/user/classroom/12/segments?afterSeq=7',
      method: 'GET'
    })
  })

  it('詳細は GET /api/user/classroom/{id}（一覧は status/page/size をクエリに載せる）', async () => {
    fetchMock.mockImplementation(async () => ok({ recordId: 12, notes: [], segments: [] }))
    await fetchClassroomRecord(12)
    expect(recorded(fetchMock).at(-1)).toMatchObject({
      url: '/api/user/classroom/12',
      method: 'GET'
    })

    fetchMock.mockImplementation(async () => ok({ items: [], totalElements: 0, page: 2, size: 20, totalPages: 0 }))
    await searchClassroomRecords({ page: 2, size: 20 })
    expect(recorded(fetchMock).at(-1)).toMatchObject({
      url: '/api/user/classroom?page=2&size=20',
      method: 'GET'
    })

    await searchClassroomRecords({ status: 'COMPLETED', page: 1, size: 20 })
    expect(recorded(fetchMock).at(-1)?.url).toBe('/api/user/classroom?status=COMPLETED&page=1&size=20')
  })

  it('終了は POST /api/user/classroom/{id}/end で、finalNoteId と runPath が返る', async () => {
    fetchMock.mockImplementation(async () => ok({
      recordId: 12, status: 'STOPPED', statusLabel: '停止（まとめ作成待ち）',
      finalNoteId: 91, runPath: '/api/admin/batch/classroom/notes/91/run'
    }))

    const response = await endClassroomRecord(12)

    expect(response.data.finalNoteId).toBe(91)
    expect(response.data.runPath).toBe('/api/admin/batch/classroom/notes/91/run')
    expect(recorded(fetchMock).at(-1)).toMatchObject({
      url: '/api/user/classroom/12/end',
      method: 'POST'
    })
  })

  it('削除は DELETE /api/user/classroom/{id}', async () => {
    fetchMock.mockImplementation(async () => ok({ recordId: 12, deletedAudio: true }))

    const response = await deleteClassroomRecord(12)

    expect(response.data.deletedAudio).toBe(true)
    expect(recorded(fetchMock).at(-1)).toMatchObject({
      url: '/api/user/classroom/12',
      method: 'DELETE'
    })
  })

  it('音声の URL は /api/user/classroom/{id}/audio（version つき）', () => {
    expect(classroomAudioUrl(12)).toBe('/api/user/classroom/12/audio?v=0')
    expect(classroomAudioUrl(12, 3)).toBe('/api/user/classroom/12/audio?v=3')
  })
})

describe('授業録音 API: 転写の表示（並び・統合・話者）', () => {
  /** 転写 1 行（`source` を付けると「画面へ直接届いた行」になる）。 */
  function segment(
    overrides: Partial<ClassroomSegment> & { segmentId: number; seq: number; text: string }
  ): ClassroomSegment {
    return {
      startOffsetSeconds: 0, endOffsetSeconds: 1,
      speaker: '講義', language: 'ja-JP', createdAt: null, ...overrides
    }
  }

  /**
   * 並びは**実際の発言開始時刻**（音声クロックの先頭オフセット）。同時刻は連番で安定させ
   * （連番は記録の中で一意＝どちらの画面でも同じ順になる）、時刻が無い行は末尾へ置く。
   */
  it('並びは実際の発言開始時刻（同時刻は連番・時刻が無い行は末尾）', () => {
    const rows = [
      segment({ segmentId: 3, seq: 3, startOffsetSeconds: 1, text: 'あとの文' }),
      segment({ segmentId: 1, seq: 1, startOffsetSeconds: null, text: '時刻が無い文' }),
      segment({ segmentId: 2, seq: 2, startOffsetSeconds: 1, text: '同時刻で先の連番' })
    ]

    expect(orderClassroomSegments(rows).map((row) => row.text))
      .toEqual(['同時刻で先の連番', 'あとの文', '時刻が無い文'])
  })

  /** 同じ発話（同じ行）に新しい文が届いたら、**新しい文が勝つ**（連番では捨てない）。 */
  it('同じ発話の新しい文は捨てず、新しい文で置き換える', () => {
    const first = segment({ segmentId: 601, seq: 2, source: 'mic', text: '最初の文です。' })
    const fixed = segment({ segmentId: 601, seq: 2, source: 'mic', text: '書き直した文です。' })

    const merged = mergeClassroomSegments([first], [fixed])

    expect(merged).toHaveLength(1)
    expect(merged[0]?.text).toBe('書き直した文です。')
  })

  /**
   * 取得した行（音源が付かない＝ポーリング・詳細の行）で、**画面へ直接届いた行を巻き戻さない**。
   * 逆の順（取得 → 直接届いた行）では、直接届いた行が勝つ。
   */
  it('取得した行で、画面へ直接届いた行を巻き戻さない（逆の順では新しい文が勝つ）', () => {
    const live = segment({ segmentId: 601, seq: 2, source: 'mic', text: '書き直した文です。' })
    const fetched = segment({ segmentId: 601, seq: 2, text: '最初の文です。' })

    // 取得（古い）→ 画面へ直接届いた行（新しい）: 新しい方が勝つ
    expect(mergeClassroomSegments([fetched], [live])[0]?.text).toBe('書き直した文です。')
    // 画面へ直接届いた行（新しい）→ 古い取得の行: **巻き戻さない**（音源も残る）
    const kept = mergeClassroomSegments([live], [fetched])[0]
    expect(kept?.text).toBe('書き直した文です。')
    expect(kept?.source).toBe('mic')
  })

  /** 同じ行が無ければ足す（別のタブ・再読み込みで保存された行も画面へ出す）。 */
  it('同じ行が無い取得の行は足す（取りこぼさない）', () => {
    const kept = segment({ segmentId: 601, seq: 2, source: 'mic', text: '画面に届いた文' })
    const other = segment({ segmentId: 602, seq: 3, text: '別のタブで保存された文' })

    expect(mergeClassroomSegments([kept], [other]).map((row) => row.text))
      .toEqual(['画面に届いた文', '別のタブで保存された文'])
  })

  /**
   * 話者は**その記録の音源の設定**で決める（二音源＝マイク学生・共有先生／単一音源＝講義）。
   * 設定が分からないときはサーバーの値をそのまま使う（分からないことを勝手に決めない）。
   */
  it('話者は記録の音源の設定で決める（分からないときはサーバーの値のまま）', () => {
    const mic = segment({ segmentId: 1, seq: 1, source: 'mic', speaker: '講義', text: '学生の文' })
    const shared = segment({ segmentId: 2, seq: 2, source: 'shared', speaker: '講義', text: '先生の文' })
    const unknown = segment({ segmentId: 3, seq: 3, speaker: '先生', text: '古い記録の文' })

    // 二音源の設定
    expect(segmentSpeakerOf('mic-pc', mic)).toBe('学生')
    expect(segmentSpeakerOf('mic-pc', shared)).toBe('先生')
    // 単一音源の設定（サーバーが「学生」と返しても講義に丸める）
    expect(segmentSpeakerOf('mic', segment({ ...mic, speaker: '学生' }))).toBe('講義')
    // 設定が分からないときはサーバーの値（空なら講義）
    expect(segmentSpeakerOf(null, unknown)).toBe('先生')
    expect(segmentSpeakerOf(null, segment({ ...unknown, speaker: null }))).toBe('講義')
  })
})

describe('授業録音 API: admin-api の薄い入口（AI ノート生成）', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn(async () => ok({ noteId: 91, kind: 'FINAL', batchCode: 'batC62', status: 'READY' }))
    vi.stubGlobal('fetch', fetchMock)
  })

  it('runPath を user-api の baseUrl を付けずそのまま POST する（operator つき）', async () => {
    await runClassroomNote('/api/admin/batch/classroom/notes/91/run')

    expect(recorded(fetchMock).at(-1)).toMatchObject({
      url: '/api/admin/batch/classroom/notes/91/run',
      method: 'POST',
      json: { operator: 'classroom-ai-view' }
    })
  })

  it('AI は時間がかかるため、タイムアウトを延ばして呼べる', async () => {
    await runClassroomNote('/api/admin/batch/classroom/notes/91/run', 'classroom-detail', 600_000)

    expect(recorded(fetchMock).at(-1)).toMatchObject({
      url: '/api/admin/batch/classroom/notes/91/run',
      json: { operator: 'classroom-detail' }
    })
  })
})

describe('授業録音 API: エラーの扱い', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn(async () => new Response(
      JSON.stringify({
        success: false, code: 'NOT_FOUND', message: '授業記録が見つかりません。', data: null, traceId: 't-1'
      }),
      { status: 404, headers: { 'Content-Type': 'application/json' } }
    ))
    vi.stubGlobal('fetch', fetchMock)
  })

  it('サーバーの日本語メッセージとコードを ApiError として投げる', async () => {
    await expect(fetchClassroomRecord(999)).rejects.toBeInstanceOf(ApiError)
    await expect(fetchClassroomRecord(999)).rejects.toMatchObject({
      code: 'NOT_FOUND',
      status: 404,
      message: '授業記録が見つかりません。',
      traceId: 't-1'
    })
  })

  it('設定が無効（400）のときも理由をそのまま受け取れる', async () => {
    fetchMock.mockImplementation(async () => new Response(
      JSON.stringify({
        success: false, code: 'VALIDATION_ERROR',
        message: '授業録音は現在ご利用いただけません。', data: null
      }),
      { status: 400, headers: { 'Content-Type': 'application/json' } }
    ))

    await expect(createClassroomRecord({ title: 'テスト', languageMode: 'ja' })).rejects.toMatchObject({
      code: 'VALIDATION_ERROR',
      message: '授業録音は現在ご利用いただけません。'
    })
  })

  it('分塊（multipart）の失敗も ApiError にする（ネットワーク断は NETWORK_ERROR）', async () => {
    fetchMock.mockImplementation(async () => {
      throw new TypeError('Failed to fetch')
    })

    await expect(uploadClassroomChunk(12, 1, new Blob([new Uint8Array([1])])))
      .rejects.toMatchObject({ code: 'NETWORK_ERROR' })
  })
})
