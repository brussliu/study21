/**
 * いまの作図（GeoGebra の実際の状態）を**コマンド列**として書き出す。
 *
 * <p>作図画面の「GeoGebra コマンド」欄に出すための処理。2.0 では入力欄に打ち込むだけだったが、
 * ここでは**作図そのものを表す**（手動で描いても、AI が反映しても、いつも同じ形になる）。</p>
 *
 * <h4>実機（GeoGebra 5.4.920.0 / deployggb.js）で確かめたこと</h4>
 * <ul>
 *   <li>`getDefinitionString()` は**日本語の説明文**（例「中心 A で, 点 B を通る円」）を返すので
 *       コマンドには使えない。`getCommandString()` が返す `Circle(A, B)` を使う。</li>
 *   <li>`getAlgorithmXML()` は `<command name="…"><input …/><output …/></command>` を返す。
 *       どのコマンドがどのオブジェクトを作るかが正確に分かる（多角形が作る辺など）。</li>
 *   <li>`getSiblingObjectNames()` は同じコマンドで作られた仲間（交点の 2 点など）を返す。</li>
 *   <li>自由点をドラッグすると `getCommandString()` が**空になる**（座標だけが残る）。
 *       その場合は座標から `A = (1.1, -0.8)` を作る。</li>
 *   <li>関数（`f(x) = x^2`）もコマンド文字列を持たない。値の文字列（`f(x) = x²`）が定義になる。</li>
 *   <li>スライダ・チェックボックスはコマンド文字列を持たない（XML にも増分が無い）ので再現できない。
 *       **黙って落とさず** `skipped` に入れて画面に出す。</li>
 * </ul>
 */

/** 読み取りに使う GeoGebra の API（使うものだけ。無い実装でも落とさない）。 */
export interface ConstructionReadApi {
  /** 作図の全オブジェクト名（**作図した順**＝依存の順）。 */
  getAllObjectNames?: () => string[]
  /** オブジェクトを作ったコマンド（`Circle(A, B)` など。無ければ空文字）。 */
  getCommandString?: (name: string) => string
  /** コマンドと入力・出力の XML（`getAlgorithmXML`）。 */
  getAlgorithmXML?: (name: string) => string
  /** 同じコマンドで作られた仲間の名前。 */
  getSiblingObjectNames?: (name: string) => string[]
  /** 種類（`point` / `conic` / `polygon` など）。 */
  getObjectType?: (name: string) => string
  /** 自由なオブジェクトか（ほかの作図に依存していないか）。 */
  isIndependent?: (name: string) => boolean
  getXcoord?: (name: string) => number
  getYcoord?: (name: string) => number
  /** 値の文字列（`f(x) = x²` など。関数はコマンド文字列を持たないのでここから取る）。 */
  getValueString?: (name: string) => string
  /** 作図データ（スライダ・チェックボックスを見分けるのに使う）。 */
  getXML?: () => string
}

/** コマンドにできなかった理由（画面で日本語にする）。 */
export type CommandSkipReason =
  /** スライダ・チェックボックスなど、GeoGebra がコマンドを持たない操作部品。 */
  | 'control'
  /** コマンドも値も定義として使えない（画像など）。 */
  | 'no-command'
  /** 1 つのコマンドで複数作る作図で、名前を正確に戻せない。 */
  | 'multi-output'
  /** コマンドにできない作図に依存している（組み直すと欠ける）。 */
  | 'dependency'

export interface CommandSkip {
  /** オブジェクト名。 */
  name: string
  /** 種類（`numeric` / `image` など）。 */
  type: string
  reason: CommandSkipReason
}

export interface ConstructionCommands {
  /** 依存の順に並べたコマンド（`A = (1, 2)` の形）。 */
  lines: string[]
  /** コマンドにできなかった作図（**画面に理由を出す**。黙って落とさない）。 */
  skipped: CommandSkip[]
}

/** 作図ではないもの（座標軸・グリッド）。コマンド欄には出さない。 */
const VIEW_OBJECT_NAMES = new Set(['xAxis', 'yAxis', 'zAxis', 'grid'])

/**
 * 「1 つのコマンドで複数のオブジェクトを作る」コマンドのうち、**添字で 1 つずつ作れる**もの。
 *
 * <p>実機で確認: `A = Intersect(f, c, 1)` と `B = Intersect(f, c, 2)` で、名前も関係もそのまま戻せる。
 * ここに無いコマンドは名前を正確に戻せないので `multi-output` として画面に出す（推測で埋めない）。</p>
 */
const INDEXABLE_COMMANDS = new Set(['Intersect'])

/** コマンド文字列の中の文字列リテラル（`"…"`）を消す（`=` の判定を誤らないため）。 */
function withoutStringLiterals(source: string): string {
  return source.replace(/"[^"]*"/g, '""')
}

/** XML の属性値を戻す（`&amp;` など）。 */
function unescapeXml(value: string): string {
  return value
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, '&')
}

export interface Algorithm {
  /** コマンド名（`Polygon` など。自由な作図では空）。 */
  command: string
  /** 入力（親）の名前。 */
  inputs: string[]
  /** 出力（このコマンドが作る）の名前。 */
  outputs: string[]
}

/** `getAlgorithmXML()` の XML からコマンド名・入力・出力を読む。 */
export function parseAlgorithm(xml: string | undefined): Algorithm {
  const source = typeof xml === 'string' ? xml : ''
  const command = /<command\s+name="([^"]*)"/.exec(source)?.[1] ?? ''
  const attributes = (tag: string): string[] => {
    const body = new RegExp(`<${tag}\\b([^>]*)/?>`).exec(source)?.[1] ?? ''
    return [...body.matchAll(/a\d+="([^"]*)"/g)].map((match) => unescapeXml(match[1]))
  }
  return { command: unescapeXml(command), inputs: attributes('input'), outputs: attributes('output') }
}

/**
 * 座標をコマンドに書ける形にする。
 *
 * ドラッグした点の座標は 16 桁になる（`1.135057471264367`）ので**小数 6 桁**で丸める。
 * 丸めているのは**コマンド欄の見やすさのため**で、元の値そのものではない（作図そのものは変わらない）。
 */
function formatNumber(value: number): string {
  if (!Number.isFinite(value)) return '0'
  const rounded = Math.round(value * 1_000_000) / 1_000_000
  return String(rounded)
}

/** そのオブジェクトが操作部品（スライダ・チェックボックス）か。XML から見分ける。 */
function isControlObject(xml: string, name: string): boolean {
  const at = xml.indexOf(`label="${name}"`)
  if (at < 0) return false
  // そのラベルの要素 1 つ分（次の </element> まで）に slider / checkbox があるか
  const elementEnd = xml.indexOf('</element>', at)
  const element = xml.slice(at, elementEnd < 0 ? undefined : elementEnd)
  return element.includes('<slider') || element.includes('<checkbox')
}

/** その文字列が「自分で定義を書く形」（`x = 1` `f(x) = x^2`）か。 */
function hasOwnDefinition(source: string): boolean {
  return withoutStringLiterals(source).includes('=')
}

/** 1 つのオブジェクトについて読み取った内容。 */
interface ObjectInfo {
  name: string
  index: number
  type: string
  command: string
  algorithm: Algorithm
  independent: boolean
  /** 同じ実行で作られた仲間を見分ける署名（コマンド名・入力・出力。無ければ空）。 */
  signature: string
}

/** 書き出す 1 行分。 */
interface CommandEntry {
  /** コマンド欄に出す行。 */
  line: string
  /** この行が作る名前（決められないときは空）。 */
  produces: string[]
  /** 並べ替えの基準（もとの作図順）。 */
  index: number
}

/**
 * いまの作図をコマンド列にする。
 *
 * @param api GeoGebra の API（`window.ggbApplet`）
 * @returns 依存順のコマンドと、コマンドにできなかった作図
 */
export function exportConstructionCommands(api: ConstructionReadApi | null | undefined): ConstructionCommands {
  const empty: ConstructionCommands = { lines: [], skipped: [] }
  if (api === null || api === undefined) return empty
  if (typeof api.getAllObjectNames !== 'function' || typeof api.getCommandString !== 'function') return empty

  let names: string[] = []
  try {
    names = (api.getAllObjectNames() ?? []).filter((name) => typeof name === 'string' && name !== '')
  } catch {
    return empty
  }

  /** 読み取りは失敗しても止めない（1 つの作図のために欄全体を空にしない）。 */
  const safely = <T>(read: () => T, fallback: T): T => {
    try {
      const value = read()
      return value === null || value === undefined ? fallback : value
    } catch {
      return fallback
    }
  }

  const xml = safely(() => (typeof api.getXML === 'function' ? String(api.getXML() ?? '') : ''), '')
  const infos: ObjectInfo[] = []
  names.forEach((name, index) => {
    if (VIEW_OBJECT_NAMES.has(name)) return
    const algorithm = parseAlgorithm(safely(() => api.getAlgorithmXML?.(name), ''))
    infos.push({
      name,
      index,
      type: safely(() => String(api.getObjectType?.(name) ?? ''), ''),
      command: safely(() => String(api.getCommandString?.(name) ?? ''), '').trim(),
      algorithm,
      independent: safely(() => api.isIndependent?.(name) === true, false),
      signature: algorithm.command === ''
        ? ''
        : `${algorithm.command}(${algorithm.inputs.join(',')})=>${algorithm.outputs.join(',')}`
    })
  })
  const byName = new Map(infos.map((info) => [info.name, info]))

  // 名前の参照を調べる正規表現は 1 回だけ作る（作図が多いときの負荷を抑える）
  const referencePattern = new Map<string, RegExp>()
  for (const info of infos) {
    const escaped = info.name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    referencePattern.set(info.name, new RegExp(`(^|[^A-Za-z0-9_])${escaped}($|[^A-Za-z0-9_])`))
  }
  /** その行が参照している「作図のオブジェクト名」（自分のラベルは除く）。同じ行は 1 回だけ調べる。 */
  const referenceCache = new Map<string, string[]>()
  const referencesOf = (line: string): string[] => {
    const cached = referenceCache.get(line)
    if (cached !== undefined) return cached
    const at = line.indexOf('=')
    // 自分で定義を書く形（`x = 1`）は全体が式。ラベル付き（`A = (1, 2)`）は右辺だけを見る
    const body = at < 0 ? line : line.slice(at + 1)
    const own = at < 0 ? '' : line.slice(0, at).trim()
    const found = infos
      .filter((info) => info.name !== own && referencePattern.get(info.name)?.test(body) === true)
      .map((info) => info.name)
    referenceCache.set(line, found)
    return found
  }

  const entries: CommandEntry[] = []
  const skipped: CommandSkip[] = []
  const skip = (info: ObjectInfo, reason: CommandSkipReason): void => {
    skipped.push({ name: info.name, type: info.type, reason })
  }

  /* ---------- 1 つずつ書き出す（コマンド・座標・値） ---------- */

  /** 仲間（同じコマンドで作られた作図）の名前。無ければアルゴリズムの出力、それも無ければ自分だけ。 */
  const siblingsOf = (info: ObjectInfo): string[] => {
    const siblings = safely(() => api.getSiblingObjectNames?.(info.name), [] as string[])
    const known = (Array.isArray(siblings) ? siblings : [])
      .map((name) => String(name))
      .filter((name) => byName.has(name))
    if (known.length > 0) return known
    const outputs = info.algorithm.outputs.filter((name) => byName.has(name))
    return outputs.length > 0 ? outputs : [info.name]
  }

  /**
   * 同じ実行で作られた作図をまとめる。
   *
   * <p>実機で分かった 2 つの形を、**アルゴリズム XML の署名**（コマンド名・入力・出力）で見分ける。</p>
   * <ul>
   *   <li>交点の 2 点のように**同じ署名**なら、1 つのコマンドがまとめて作った仲間。</li>
   *   <li>多角形とその辺のように、辺の署名（`Segment(A, B, p)`）は多角形と違う。
   *       多角形の出力に辺が並ぶので、多角形 1 行で足りる（辺は行にしない）。</li>
   * </ul>
   *
   * <p>アルゴリズム XML が取れない実装では `getSiblingObjectNames()` に頼る
   * （実機では交点でも多角形の辺でも正しい組を返す）。</p>
   */
  const groupOf = (info: ObjectInfo): string[] => {
    if (info.signature !== '') {
      return infos
        .filter((other) => other.signature === info.signature)
        .sort((left, right) => left.index - right.index)
        .map((other) => other.name)
    }
    return siblingsOf(info)
      .filter((name) => (byName.get(name)?.command ?? '') !== '')
      .sort((left, right) => (byName.get(left)?.index ?? 0) - (byName.get(right)?.index ?? 0))
  }

  const planned = new Set<string>()
  for (const info of infos) {
    if (planned.has(info.name)) continue

    if (info.command !== '') {
      // `x = 1` のような「定義そのもの」は、ラベルを付けると通らない（実機で確認: `eq1 = x = 1` は失敗）
      if (hasOwnDefinition(info.command)) {
        // 名前は GeoGebra が付けるので、こちらでは決められない（依存する作図は後で外れる）
        entries.push({ line: info.command, produces: [], index: info.index })
        planned.add(info.name)
        continue
      }

      // 仲間が分からないときは自分だけ（**黙って落とさない**。落とすと欄が不完全になる）
      const found = groupOf(info)
      const group = found.length > 0 ? found : [info.name]
      group.forEach((name) => planned.add(name))

      if (group.length === 1) {
        const only = byName.get(group[0])
        const command = only?.command ?? info.command
        // コマンドが一緒に作るもの（多角形が作る辺など）も、この 1 行でできる
        const outputs = (only?.algorithm.outputs ?? []).filter((name) => byName.has(name))
        if (hasOwnDefinition(command)) {
          entries.push({ line: command, produces: [], index: only?.index ?? info.index })
        } else {
          entries.push({
            line: `${group[0]} = ${command}`,
            produces: outputs.length > 0 ? outputs : [group[0]],
            index: only?.index ?? info.index
          })
          // 辺のように「他のコマンドが作る」ものは、行にしない（組み直しても同じものができる）
          outputs.forEach((name) => planned.add(name))
        }
        continue
      }

      const memberCommands = group.map((name) => byName.get(name)?.command ?? '')
      if (memberCommands.every((command) => command !== '')
        && new Set(memberCommands).size === memberCommands.length) {
        // 実機で確認: `Intersect(c, f, 1)` のように添字つきなら、そのまま 1 行ずつ戻せる
        for (const name of group) {
          const member = byName.get(name)
          entries.push({
            line: `${name} = ${member?.command ?? ''}`,
            produces: [name],
            index: member?.index ?? info.index
          })
        }
        continue
      }

      // ラベルを付けた複数出力は `名前_1, 名前_2 …` になる（実機で確認）
      const first = group[0]
      const base = /^(.*)_(?:\{)?1(?:\})?$/.exec(first)?.[1] ?? ''
      const derived = base !== '' && group.every((name, position) =>
        name === `${base}_${position + 1}` || name === `${base}_{${position + 1}}`)
      if (derived) {
        const member = byName.get(first)
        entries.push({
          line: `${first} = ${member?.command ?? ''}`,
          produces: group,
          index: member?.index ?? info.index
        })
        continue
      }

      // 添字で 1 つずつ作れるコマンドなら、名前をそのまま使って 1 行ずつ作る
      const algorithm = info.algorithm
      if (INDEXABLE_COMMANDS.has(algorithm.command) && algorithm.inputs.length > 0
        && algorithm.inputs.every((name) => byName.has(name))) {
        group.forEach((name, position) => {
          entries.push({
            line: `${name} = ${algorithm.command}(${[...algorithm.inputs, String(position + 1)].join(', ')})`,
            produces: [name],
            index: byName.get(name)?.index ?? info.index
          })
        })
        continue
      }

      // 名前を正確に戻せないので、**推測で書かない**（画面に理由を出す）
      for (const name of group) {
        const member = byName.get(name)
        if (member) skip(member, 'multi-output')
      }
      continue
    }

    planned.add(info.name)

    // スライダ・チェックボックスはコマンドにできない（XML に増分などが残らない）
    if (xml !== '' && isControlObject(xml, info.name)) {
      skip(info, 'control')
      continue
    }
    // ドラッグした自由点は座標だけが残る（実機で確認）
    if (info.type === 'point' && info.independent) {
      const x = safely(() => Number(api.getXcoord?.(info.name)), Number.NaN)
      const y = safely(() => Number(api.getYcoord?.(info.name)), Number.NaN)
      if (Number.isFinite(x) && Number.isFinite(y)) {
        entries.push({
          line: `${info.name} = (${formatNumber(x)}, ${formatNumber(y)})`,
          produces: [info.name],
          index: info.index
        })
        continue
      }
      skip(info, 'no-command')
      continue
    }
    // 自由な関数などは、値の文字列がそのまま定義になる（`f(x) = x²`）
    if (info.independent) {
      const value = safely(() => String(api.getValueString?.(info.name) ?? ''), '').trim()
      if (value.startsWith(`${info.name}`) && hasOwnDefinition(value)) {
        entries.push({ line: value, produces: [info.name], index: info.index })
        continue
      }
    }
    skip(info, 'no-command')
  }

  /* ---------- 依存の順に並べる ---------- */

  const producesOf = new Map<string, CommandEntry>()
  for (const entry of entries) for (const name of entry.produces) producesOf.set(name, entry)

  // 安定なトポロジカルソート（もとの作図順をできるだけ保つ）
  const sorted: CommandEntry[] = []
  const placed = new Set<CommandEntry>()
  const visiting = new Set<CommandEntry>()
  const place = (entry: CommandEntry): void => {
    if (placed.has(entry) || visiting.has(entry)) return
    visiting.add(entry)
    for (const name of referencesOf(entry.line)) {
      const parent = producesOf.get(name)
      if (parent !== undefined && parent !== entry) place(parent)
    }
    visiting.delete(entry)
    placed.add(entry)
    sorted.push(entry)
  }
  for (const entry of entries) place(entry)

  /* ---------- 組み直せない行を外す（黙って欠けさせない） ---------- */

  const buildable = new Set<string>()
  for (const entry of sorted) for (const name of entry.produces) buildable.add(name)
  const dropped = new Set<CommandEntry>()
  let changed = true
  while (changed) {
    changed = false
    for (const entry of sorted) {
      if (dropped.has(entry)) continue
      const missing = referencesOf(entry.line)
        .some((name) => byName.has(name) && !buildable.has(name))
      if (!missing) continue
      dropped.add(entry)
      for (const name of entry.produces) buildable.delete(name)
      changed = true
    }
  }

  const lines: string[] = []
  for (const entry of sorted) {
    if (!dropped.has(entry)) {
      lines.push(entry.line)
      continue
    }
    for (const name of entry.produces) {
      const info = byName.get(name)
      if (info) skip(info, 'dependency')
    }
  }

  skipped.sort((left, right) => (byName.get(left.name)?.index ?? 0) - (byName.get(right.name)?.index ?? 0))
  return { lines, skipped }
}
