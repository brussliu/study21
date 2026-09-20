/**
 * ストリーミング書き起こしの**収尾（finish）の結果**を、HTTP と常時接続で**同じ意味**に揃える。
 *
 * <p>後端の収尾は「成功」と「失敗」の 2 つではない。実際に起きるのは 5 通りある:</p>
 * <ol>
 *   <li>まだ収尾の途中（音を受け付けている・確定文を待っている）。</li>
 *   <li>**完全に成功**（必要な結果を全部保存できた）。</li>
 *   <li>音声が無い／認識できる発話が無い（**正常な終端**。失敗ではない）。</li>
 *   <li>**やり直せば直る失敗**（`error` がある。画面は再試行を出す）。</li>
 *   <li>**やり直しても直らない不完整な終わり**（終端）。ここが落とし穴で、
 *       `error` は **null** なのに `finalizeCompleted=false`（＝成功ではない）。</li>
 * </ol>
 *
 * <p>以前の画面は `error` だけを見ていたので、5 を**成功と誤読**していた（書き起こしが欠けたまま
 * 「保存しました」と言う）。ここでは `error` / `finalizeCompleted` / `retryable` / `finalizeStatus` /
 * `notice` を**1 か所で**解釈し、**「終わってよいか」と「識別は完全か」を別々に**持つ結果を返す
 * （終わってよい ≠ 完全に識別できた）。</p>
 *
 * <p>日本語の文面では判断しない（`notice` は**そのまま画面に出す文言**としてだけ使う）。</p>
 */

/** 収尾の結果の種類。 */
export type SourceFinalizeKind =
  /** まだ収尾が済んでいない（途中）。 */
  | 'PENDING'
  /** 完全に成功（必要な結果を全部保存できた）。 */
  | 'COMPLETE'
  /** 音声が送られていない（この音源は使っていない）。 */
  | 'NO_AUDIO'
  /** 音声はあったが、認識できる発話が無かった。 */
  | 'NO_UTTERANCE'
  /** やり直せば直る失敗。 */
  | 'RETRYABLE_FAILURE'
  /** やり直しても直らない不完整な終わり（**終わってよい**が、識別は完全ではない）。 */
  | 'INCOMPLETE_UNRECOVERABLE'
  /** 後端の答えからは判断できない（欄が欠けている・応答を失った）。**成功とは見なさない**。 */
  | 'UNKNOWN'

/** 収尾の結果（音源 1 つぶん）。 */
export interface SourceFinalizeResult {
  /** どの音源か（`mic` / `shared`）。 */
  source: string
  /** 結果の種類。 */
  kind: SourceFinalizeKind
  /** **やり直せるか**（再試行の入口を出すか）。 */
  retryable: boolean
  /**
   * **授業を終えてよいか**。
   *
   * <p>{@link SourceFinalizeResult.complete} とは**別**。やり直しても直らない不完整な終わりは
   * 終わってよい（利用者に永久に待たせない）が、完全に識別できたわけではない。</p>
   */
  canFinish: boolean
  /** **識別が完全か**（収尾が済んで、必要な結果を全部保存できたか）。 */
  complete: boolean
  /** 保存できた文の数（分からなければ 0）。 */
  savedCount: number
  /** 保存待ちで残っている文の数（分からなければ 0）。 */
  pendingCount: number
  /** 後端の段階（`SAVED` / `NO_AUDIO` / `NO_UTTERANCE` / `FAILED` など。分からなければ空）。 */
  status: string
  /** 画面に出す短い理由（日本語。無ければ null）。 */
  notice: string | null
}

/** 後端が返す収尾の欄（HTTP の本文と常時接続のメッセージで同じ形）。 */
export interface SttFinalizePayload {
  error?: string | null
  finalizeStatus?: string | null
  finalizeCompleted?: boolean | null
  retryable?: boolean | null
  recovery?: string | null
  savedCount?: number | null
  pendingCount?: number | null
  notice?: string | null
}

/** 収尾が済んだ段階（`SAVED` と、失敗ではない終端 2 つ）。 */
const COMPLETED_STAGES = new Set(['SAVED', 'NO_AUDIO', 'NO_UTTERANCE'])
/** まだ収尾が済んでいない段階。 */
const PENDING_STAGES = new Set(['NOT_STARTED', 'AUDIO_ACCEPTING', 'FINALIZING', ''])

/** 空文字を null に寄せる（後端は「無し」を null で返すが、空文字が混ざっても同じ扱いにする）。 */
function textOf(value: string | null | undefined): string | null {
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}

/**
 * 収尾の欄を**1 つの結果**に揃える（HTTP と常時接続で同じ関数を通す）。
 *
 * <p>判断の順序（後端の意味のまま）:</p>
 * <ol>
 *   <li>`error` がある → **やり直せる失敗**（後端はやり直せるときだけ `error` に載せる）。</li>
 *   <li>`finalizeCompleted=true` → 段階で「音声なし」「発話なし」を分けて**完全な終端**にする。</li>
 *   <li>`finalizeCompleted=false` で `retryable=true` → まだ**途中**（画面は待つ）。</li>
 *   <li>`finalizeCompleted=false` で `retryable=false` → **やり直しても直らない不完整な終わり**
 *       （終わってよいが、完全ではない）。</li>
 *   <li>`finalizeCompleted` が**無い**（旧い後端・応答の欠け） → 成功とは見なさず `UNKNOWN`。</li>
 * </ol>
 *
 * @param payload 後端が返した収尾の欄
 * @param source  どの音源か（`mic` / `shared`）
 */
export function normalizeSttFinalize(
  payload: SttFinalizePayload | null | undefined,
  source: string
): SourceFinalizeResult {
  const raw = payload ?? {}
  const status = textOf(raw.finalizeStatus) ?? ''
  const notice = textOf(raw.notice)
  const error = textOf(raw.error)
  const savedCount = typeof raw.savedCount === 'number' && raw.savedCount > 0 ? raw.savedCount : 0
  const pendingCount = typeof raw.pendingCount === 'number' && raw.pendingCount > 0 ? raw.pendingCount : 0
  const base = { source, savedCount, pendingCount, status }

  // ① やり直せば直る失敗（後端は「やり直せる」ときだけ error に載せる）
  if (error !== null) {
    return {
      ...base, kind: 'RETRYABLE_FAILURE', retryable: true, canFinish: false, complete: false,
      notice: error
    }
  }
  // ⑤ 欄が無い＝確認できない。**欠けた欄を「完全成功」と読まない**
  if (raw.finalizeCompleted === undefined || raw.finalizeCompleted === null) {
    return {
      ...base, kind: 'UNKNOWN', retryable: true, canFinish: false, complete: false,
      notice: notice ?? '書き起こしの収尾の状態を確認できませんでした（もう一度やり直してください）。'
    }
  }
  if (raw.finalizeCompleted === true) {
    // ②③ 成功、または「音声なし」「発話なし」の正常な終端
    if (status === 'NO_AUDIO') {
      return {
        ...base, kind: 'NO_AUDIO', retryable: false, canFinish: true, complete: true,
        notice: notice ?? 'この音源の音声は送られていませんでした。'
      }
    }
    if (status === 'NO_UTTERANCE') {
      return {
        ...base, kind: 'NO_UTTERANCE', retryable: false, canFinish: true, complete: true,
        notice: notice ?? 'この音源では認識できる発話がありませんでした。'
      }
    }
    if (!COMPLETED_STAGES.has(status) && PENDING_STAGES.has(status)) {
      // 「済んだ」と言いながら段階が途中（矛盾）。**成功と読まずに**確認扱いにする
      return {
        ...base, kind: 'UNKNOWN', retryable: true, canFinish: false, complete: false,
        notice: notice ?? '書き起こしの収尾の状態が食い違っています（もう一度やり直してください）。'
      }
    }
    return { ...base, kind: 'COMPLETE', retryable: false, canFinish: true, complete: true, notice }
  }
  // ④ やり直せないなら「不完整な終わり」、やり直せるなら「まだ途中」
  if (raw.retryable === false) {
    return {
      ...base, kind: 'INCOMPLETE_UNRECOVERABLE', retryable: false, canFinish: true, complete: false,
      notice: notice ?? '書き起こしの一部を完了できませんでした（やり直しても直りません）。'
    }
  }
  return {
    ...base, kind: 'PENDING', retryable: true, canFinish: false, complete: false,
    notice: notice ?? '書き起こしの仕上げがまだ済んでいません。'
  }
}

/** 2 つの結果を**同じ意味で**比べる（同じ音源の結果を上書きしてよいかの判断に使う）。 */
export function sameFinalizeResult(
  left: SourceFinalizeResult | null, right: SourceFinalizeResult | null
): boolean {
  if (left === null || right === null) return left === right
  return left.source === right.source && left.kind === right.kind && left.notice === right.notice
    && left.savedCount === right.savedCount && left.pendingCount === right.pendingCount
}

/** 音源 1 つの結果に付ける表示名（画面に出す短文の組み立てに使う）。 */
export interface FinalizeSourceLabel {
  source: string
  label: string
}

/** 収尾の**まとめ**（音源ごとの結果を 1 つに畳んだもの）。 */
export interface SttFinalizeSummary {
  /**
   * **授業を終えてよいか**。
   *
   * <p>やり直せる失敗が 1 つでも残っていれば `false`（画面に残って【続きをやり直す】を出す）。
   * やり直しても直らない不完整な終わりは**止めない**（利用者を永久に待たせない）。</p>
   */
  canFinish: boolean
  /** **識別が完全か**（全部の音源が完全に成功、または音声なし・発話なしで済んだか）。 */
  complete: boolean
  /** 完全に済んだ音源。 */
  completeSources: SourceFinalizeResult[]
  /** **識別が不完全**なまま終わってよい音源（やり直しても直らない終端）。 */
  incompleteSources: SourceFinalizeResult[]
  /** もう一度やり直せば直る音源（終われない原因）。 */
  retryableSources: SourceFinalizeResult[]
  /** 音声も発話も無かった音源（失敗ではない）。 */
  quietSources: SourceFinalizeResult[]
  /** 状態を確認できなかった音源（**成功とは見なさない**ため、やり直しに回す）。 */
  unknownSources: SourceFinalizeResult[]
  /** 画面に出す短い理由（日本語。無ければ null）。 */
  notice: string | null
}

/**
 * 音源ごとの結果を**1 つのまとめ**にする（画面はこの 1 つを見て、終えるか残るかを決める）。
 *
 * <p>「全部成功」以外を全部失敗にしない: **やり直せる失敗**（終われない）と、
 * **やり直しても直らない不完整な終わり**（終わってよいが完全ではない）と、
 * **音声なし・発話なし**（正常な終端）を分ける。分けないと、直らないものを永久に再試行させたり、
 * 欠けた書き起こしを「完全に保存しました」と言ったりする。</p>
 *
 * @param results 音源ごとの結果（**実際に使った音源だけ**を渡す。単音源の回に使っていない
 *                音源を混ぜると、その音源の「音声なし」で必ず失敗と判定してしまう）
 * @param labels  音源の表示名（例: `mic` → 「マイク」）
 */
export function summarizeSttFinalize(
  results: Array<SourceFinalizeResult | null>,
  labels: FinalizeSourceLabel[] = []
): SttFinalizeSummary {
  const known = results.filter((result): result is SourceFinalizeResult => result !== null)
  const labelOf = (source: string): string =>
    labels.find((entry) => entry.source === source)?.label ?? source

  const completeSources = known.filter((result) => result.kind === 'COMPLETE')
  const quietSources = known.filter(
    (result) => result.kind === 'NO_AUDIO' || result.kind === 'NO_UTTERANCE')
  const incompleteSources = known.filter((result) => result.kind === 'INCOMPLETE_UNRECOVERABLE')
  const retryableSources = known.filter(
    (result) => result.kind === 'RETRYABLE_FAILURE' || result.kind === 'PENDING')
  const unknownSources = known.filter((result) => result.kind === 'UNKNOWN')

  // やり直せるもの（失敗・途中・確認できない）が残っていれば、終われない
  const canFinish = retryableSources.length === 0 && unknownSources.length === 0
  // 「識別が完全」＝ 完全に済んだ音源と、正常な終端（音声なし・発話なし）だけ
  const complete = canFinish && incompleteSources.length === 0
    && known.length > 0 && completeSources.length + quietSources.length === known.length

  const parts: string[] = []
  for (const result of [...retryableSources, ...unknownSources]) {
    parts.push(`【${labelOf(result.source)}】${result.notice ?? '書き起こしの仕上げが済んでいません。'}`)
  }
  for (const result of incompleteSources) {
    parts.push(`【${labelOf(result.source)}】${result.notice ?? '書き起こしの一部を完了できませんでした。'}`)
  }
  for (const result of quietSources) {
    const kind = result.kind === 'NO_AUDIO'
      ? 'この音源の音声は送られていませんでした。'
      : 'この音源では認識できる発話がありませんでした。'
    parts.push(`【${labelOf(result.source)}】${kind}`)
  }
  return {
    canFinish,
    complete,
    completeSources,
    incompleteSources,
    retryableSources,
    quietSources,
    unknownSources,
    notice: parts.length === 0 ? null : parts.join(' ')
  }
}
