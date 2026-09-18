import { describe, expect, it } from 'vitest'
import {
  exportConstructionCommands,
  parseAlgorithm,
  type Algorithm,
  type ConstructionReadApi
} from '@/features/geometry/construction-commands'

/**
 * 作図のコマンド書き出し（`construction-commands.ts`）。
 *
 * ここに入れている値は、**実機（GeoGebra 5.4.920.0 / deployggb.js）で実際に返ってきたもの**を
 * そのまま写している（`tmp/e2e/probe-ggb-*.mjs` で採取）。
 * ・`getCommandString` … `Circle(A, B)` / 自由点は `(0, 0)` / ドラッグ後は空
 * ・`getAlgorithmXML` … `<command name="Polygon"><input a0="A" …/><output a0="p" a1="c" …/></command>`
 * ・多角形の辺は `Segment(A, B, p)`（多角形が作るので、別の行にすると実行できない）
 */

interface FakeObject {
  name: string
  type?: string
  command?: string
  algorithm?: Algorithm
  siblings?: string[]
  independent?: boolean
  x?: number
  y?: number
  value?: string
}

/** 実機と同じ形のアルゴリズム XML を作る。 */
function algorithmXml(algorithm: Algorithm): string {
  const inputs = algorithm.inputs.map((name, index) => `a${index}="${name}"`).join(' ')
  const outputs = algorithm.outputs.map((name, index) => `a${index}="${name}"`).join(' ')
  return `<command name="${algorithm.command}"> <input ${inputs}/> <output ${outputs}/> </command> <element type="point"/>`
}

/** GeoGebra の API の代役（実機と同じ形の値を返す）。 */
class FakeGgb implements ConstructionReadApi {
  constructor(private objects: FakeObject[], private xml = '') {}

  private find(name: string): FakeObject | undefined {
    return this.objects.find((object) => object.name === name)
  }

  getAllObjectNames(): string[] {
    return this.objects.map((object) => object.name)
  }

  getCommandString(name: string): string {
    return this.find(name)?.command ?? ''
  }

  getAlgorithmXML(name: string): string {
    const algorithm = this.find(name)?.algorithm
    return algorithm ? algorithmXml(algorithm) : ''
  }

  getSiblingObjectNames(name: string): string[] {
    return this.find(name)?.siblings ?? [name]
  }

  getObjectType(name: string): string {
    return this.find(name)?.type ?? ''
  }

  isIndependent(name: string): boolean {
    return this.find(name)?.independent === true
  }

  getXcoord(name: string): number {
    const x = this.find(name)?.x
    if (x === undefined) throw new Error('not a point')
    return x
  }

  getYcoord(name: string): number {
    const y = this.find(name)?.y
    if (y === undefined) throw new Error('not a point')
    return y
  }

  getValueString(name: string): string {
    return this.find(name)?.value ?? ''
  }

  getXML(): string {
    return this.xml
  }
}

/** 自由点（実機ではアルゴリズム XML が空）。 */
function freePoint(name: string, x: number, y: number): FakeObject {
  return { name, type: 'point', command: `(${x}, ${y})`, independent: true, x, y }
}

describe('parseAlgorithm', () => {
  it('コマンド名・入力・出力を読む（実機の多角形の XML）', () => {
    const xml = '<command name="Polygon"> <input a0="A" a1="B" a2="C"/> <output a0="p" a1="c" a2="a" a3="b"/> </command>'
    expect(parseAlgorithm(xml)).toEqual({
      command: 'Polygon',
      inputs: ['A', 'B', 'C'],
      outputs: ['p', 'c', 'a', 'b']
    })
  })

  it('交点の XML は出力に 2 点が並ぶ（実機と同じ）', () => {
    const xml = '<command name="Intersect"> <input a0="c" a1="f"/> <output a0="X_{1}" a1="X_{2}"/> </command>'
    expect(parseAlgorithm(xml)).toEqual({ command: 'Intersect', inputs: ['c', 'f'], outputs: ['X_{1}', 'X_{2}'] })
  })

  it('XML が無い・空でも落ちない', () => {
    expect(parseAlgorithm('')).toEqual({ command: '', inputs: [], outputs: [] })
    expect(parseAlgorithm(undefined)).toEqual({ command: '', inputs: [], outputs: [] })
  })

  it('名前の実体参照（&amp; など）を戻す', () => {
    expect(parseAlgorithm('<command name="Line"> <input a0="A&amp;B"/> <output a0="f"/> </command>').inputs)
      .toEqual(['A&B'])
  })
})

describe('exportConstructionCommands', () => {
  it('GeoGebra の API が無いときは空（作図前・代役でも落ちない）', () => {
    expect(exportConstructionCommands(null)).toEqual({ lines: [], skipped: [] })
    expect(exportConstructionCommands({})).toEqual({ lines: [], skipped: [] })
  })

  it('作図が空なら空', () => {
    expect(exportConstructionCommands(new FakeGgb([]))).toEqual({ lines: [], skipped: [] })
  })

  it('手動で描いた作図を、関係を保ったまま作図順に出す', () => {
    const api = new FakeGgb([
      freePoint('A', 0, 0),
      freePoint('B', 4, 0),
      { name: 'C', type: 'point', command: '(3, 6)', independent: true, x: 3, y: 6 },
      { name: 'c', type: 'circle', command: 'Circle(A, B)', algorithm: { command: 'Circle', inputs: ['A', 'B'], outputs: ['c'] } },
      { name: 'D', type: 'point', command: 'Point(c)', algorithm: { command: 'Point', inputs: ['c'], outputs: ['D'] } },
      { name: 'E', type: 'point', command: 'Midpoint(A, B)', algorithm: { command: 'Midpoint', inputs: ['A', 'B'], outputs: ['E'] } },
      { name: 'f', type: 'line', command: 'Line(A, B)', algorithm: { command: 'Line', inputs: ['A', 'B'], outputs: ['f'] } },
      { name: 'perp', type: 'line', command: 'PerpendicularLine(C, f)', algorithm: { command: 'PerpendicularLine', inputs: ['C', 'f'], outputs: ['perp'] } }
    ])

    expect(exportConstructionCommands(api)).toEqual({
      lines: [
        'A = (0, 0)',
        'B = (4, 0)',
        'C = (3, 6)',
        'c = Circle(A, B)',
        'D = Point(c)',
        'E = Midpoint(A, B)',
        'f = Line(A, B)',
        'perp = PerpendicularLine(C, f)'
      ],
      skipped: []
    })
  })

  it('多角形は 1 行だけにし、辺の名前はその行が作るものとして扱う', () => {
    const api = new FakeGgb([
      freePoint('A', 0, 0),
      freePoint('B', 4, 0),
      { name: 'C', type: 'point', command: '(2, 4)', independent: true, x: 2, y: 4 },
      {
        name: 'p',
        type: 'polygon',
        command: 'Polygon(A, B, C)',
        algorithm: { command: 'Polygon', inputs: ['A', 'B', 'C'], outputs: ['p', 'c', 'a', 'b'] }
      },
      // 実機では辺にもコマンド文字列が付くが、実行すると失敗する（多角形が作るため）
      { name: 'c', type: 'segment', command: 'Segment(A, B, p)', algorithm: { command: 'Segment', inputs: ['A', 'B', 'p'], outputs: ['c'] } },
      { name: 'a', type: 'segment', command: 'Segment(B, C, p)', algorithm: { command: 'Segment', inputs: ['B', 'C', 'p'], outputs: ['a'] } },
      { name: 'b', type: 'segment', command: 'Segment(C, A, p)', algorithm: { command: 'Segment', inputs: ['C', 'A', 'p'], outputs: ['b'] } },
      // 辺の上に取った点（辺の名前は多角形の行が作るので、この行は残る）
      { name: 'T', type: 'point', command: 'Point(c)', algorithm: { command: 'Point', inputs: ['c'], outputs: ['T'] } }
    ])

    const result = exportConstructionCommands(api)
    expect(result.lines).toEqual([
      'A = (0, 0)',
      'B = (4, 0)',
      'C = (2, 4)',
      'p = Polygon(A, B, C)',
      'T = Point(c)'
    ])
    expect(result.skipped).toEqual([])
  })

  it('交点（X_{1}, X_{2}）は 1 行にまとめる', () => {
    const api = new FakeGgb([
      freePoint('A', 0, 0),
      freePoint('B', 4, 0),
      { name: 'c', type: 'circle', command: 'Circle(A, B)', algorithm: { command: 'Circle', inputs: ['A', 'B'], outputs: ['c'] } },
      { name: 'f', type: 'line', command: 'Line(A, B)', algorithm: { command: 'Line', inputs: ['A', 'B'], outputs: ['f'] } },
      {
        name: 'X_{1}',
        type: 'point',
        command: 'Intersect(c, f)',
        algorithm: { command: 'Intersect', inputs: ['c', 'f'], outputs: ['X_{1}', 'X_{2}'] }
      },
      {
        name: 'X_{2}',
        type: 'point',
        command: 'Intersect(c, f)',
        algorithm: { command: 'Intersect', inputs: ['c', 'f'], outputs: ['X_{1}', 'X_{2}'] }
      },
      { name: 'M', type: 'point', command: 'Midpoint(X_{1}, X_{2})', algorithm: { command: 'Midpoint', inputs: ['X_{1}', 'X_{2}'], outputs: ['M'] } }
    ])

    const result = exportConstructionCommands(api)
    expect(result.lines).toEqual([
      'A = (0, 0)',
      'B = (4, 0)',
      'c = Circle(A, B)',
      'f = Line(A, B)',
      'X_{1} = Intersect(c, f)',
      'M = Midpoint(X_{1}, X_{2})'
    ])
    expect(result.skipped).toEqual([])
  })

  it('交点（A, B のように名前が続き番号でない）は添字つきのコマンドで 1 行ずつ出す', () => {
    const api = new FakeGgb([
      freePoint('P', 1, 2),
      freePoint('Q', 5, 2),
      { name: 'c', type: 'circle', command: 'Circle(P, Q)', algorithm: { command: 'Circle', inputs: ['P', 'Q'], outputs: ['c'] } },
      { name: 'f', type: 'line', command: 'Line(P, Q)', algorithm: { command: 'Line', inputs: ['P', 'Q'], outputs: ['f'] } },
      { name: 'A', type: 'point', command: 'Intersect(c, f)', algorithm: { command: 'Intersect', inputs: ['c', 'f'], outputs: ['A', 'B'] } },
      { name: 'B', type: 'point', command: 'Intersect(c, f)', algorithm: { command: 'Intersect', inputs: ['c', 'f'], outputs: ['A', 'B'] } }
    ])

    const result = exportConstructionCommands(api)
    expect(result.lines).toEqual([
      'P = (1, 2)',
      'Q = (5, 2)',
      'c = Circle(P, Q)',
      'f = Line(P, Q)',
      'A = Intersect(c, f, 1)',
      'B = Intersect(c, f, 2)'
    ])
    expect(result.skipped).toEqual([])
  })

  it('添字つきで作られた交点は、そのまま 1 行ずつ出す', () => {
    const api = new FakeGgb([
      freePoint('P', 1, 2),
      freePoint('Q', 5, 2),
      { name: 'c', type: 'circle', command: 'Circle(P, Q)', algorithm: { command: 'Circle', inputs: ['P', 'Q'], outputs: ['c'] } },
      { name: 'f', type: 'line', command: 'Line(P, Q)', algorithm: { command: 'Line', inputs: ['P', 'Q'], outputs: ['f'] } },
      { name: 'A', type: 'point', command: 'Intersect(c, f, 1)', algorithm: { command: 'Intersect', inputs: ['c', 'f'], outputs: ['A', 'B'] } },
      { name: 'B', type: 'point', command: 'Intersect(c, f, 2)', algorithm: { command: 'Intersect', inputs: ['c', 'f'], outputs: ['A', 'B'] } }
    ])

    expect(exportConstructionCommands(api).lines).toEqual([
      'P = (1, 2)',
      'Q = (5, 2)',
      'c = Circle(P, Q)',
      'f = Line(P, Q)',
      'A = Intersect(c, f, 1)',
      'B = Intersect(c, f, 2)'
    ])
  })

  it('名前を正確に戻せない複数出力は、作らずに理由を出す（推測で埋めない）', () => {
    const api = new FakeGgb([
      freePoint('P', 0, 0),
      { name: 'A', type: 'point', command: 'Tangent(P)', algorithm: { command: 'Tangent', inputs: ['P'], outputs: ['A', 'B'] } },
      { name: 'B', type: 'point', command: 'Tangent(P)', algorithm: { command: 'Tangent', inputs: ['P'], outputs: ['A', 'B'] } }
    ])

    const result = exportConstructionCommands(api)
    expect(result.lines).toEqual(['P = (0, 0)'])
    expect(result.skipped).toEqual([
      { name: 'A', type: 'point', reason: 'multi-output' },
      { name: 'B', type: 'point', reason: 'multi-output' }
    ])
  })

  it('ドラッグした自由点は座標から作る（実機ではコマンド文字列が空になる）', () => {
    const api = new FakeGgb([
      { name: 'A', type: 'point', command: '', independent: true, x: 1.135057471264367, y: -0.8241758241758241 }
    ])

    expect(exportConstructionCommands(api)).toEqual({
      lines: ['A = (1.135057, -0.824176)'],
      skipped: []
    })
  })

  it('スライダ・チェックボックスは「コマンドにできない」と伝える', () => {
    const xml = [
      '<construction>',
      '<element type="numeric" label="n"> <value val="0"/> <slider min="-5" max="5"/> </element>',
      '<element type="boolean" label="cb"> <value val="true"/> <checkbox/> </element>',
      '</construction>'
    ].join('')
    const api = new FakeGgb([
      { name: 'n', type: 'numeric', command: '', independent: true, value: 'n = 0' },
      { name: 'cb', type: 'boolean', command: '', independent: true, value: 'cb = true' }
    ], xml)

    const result = exportConstructionCommands(api)
    expect(result.lines).toEqual([])
    expect(result.skipped).toEqual([
      { name: 'n', type: 'numeric', reason: 'control' },
      { name: 'cb', type: 'boolean', reason: 'control' }
    ])
  })

  it('コマンドにできない作図に依存する行は出さない（組み直すと欠けるため）', () => {
    const xml = '<element type="numeric" label="n"> <value val="0"/> <slider min="-5" max="5"/> </element>'
    const api = new FakeGgb([
      { name: 'n', type: 'numeric', command: '', independent: true, value: 'n = 0' },
      { name: 'A', type: 'point', command: '(n, 0)', algorithm: { command: '', inputs: [], outputs: ['A'] } }
    ], xml)

    const result = exportConstructionCommands(api)
    expect(result.lines).toEqual([])
    expect(result.skipped).toEqual([
      { name: 'n', type: 'numeric', reason: 'control' },
      { name: 'A', type: 'point', reason: 'dependency' }
    ])
  })

  it('定義そのものを書く作図（x = 1）はラベルを付けずに出し、依存する行は出さない', () => {
    const api = new FakeGgb([
      { name: 'eq1', type: 'line', command: 'x = 1', independent: true },
      freePoint('O', 0, 0),
      { name: 'c', type: 'circle', command: 'Circle(O, 2)', algorithm: { command: 'Circle', inputs: ['O'], outputs: ['c'] } },
      { name: 'A', type: 'point', command: 'Intersect(eq1, c)', algorithm: { command: 'Intersect', inputs: ['eq1', 'c'], outputs: ['A', 'B'] } },
      { name: 'B', type: 'point', command: 'Intersect(eq1, c)', algorithm: { command: 'Intersect', inputs: ['eq1', 'c'], outputs: ['A', 'B'] } }
    ])

    const result = exportConstructionCommands(api)
    expect(result.lines).toEqual(['x = 1', 'O = (0, 0)', 'c = Circle(O, 2)'])
    expect(result.skipped).toEqual([
      { name: 'A', type: 'point', reason: 'dependency' },
      { name: 'B', type: 'point', reason: 'dependency' }
    ])
  })

  it('関数はコマンド文字列を持たないので、値の文字列を定義として出す', () => {
    const api = new FakeGgb([
      { name: 'f', type: 'function', command: '', independent: true, value: 'f(x) = x²' },
      { name: 'g', type: 'function', command: 'NDerivative(f)', algorithm: { command: 'NDerivative', inputs: ['f'], outputs: ['g'] } }
    ])

    expect(exportConstructionCommands(api)).toEqual({
      lines: ['f(x) = x²', 'g = NDerivative(f)'],
      skipped: []
    })
  })

  it('依存の順に並べ替える（アプレットの並びが逆でも親を先に出す）', () => {
    const api = new FakeGgb([
      { name: 'M', type: 'point', command: 'Midpoint(A, B)', algorithm: { command: 'Midpoint', inputs: ['A', 'B'], outputs: ['M'] } },
      freePoint('A', 0, 0),
      freePoint('B', 4, 0)
    ])

    expect(exportConstructionCommands(api).lines).toEqual(['A = (0, 0)', 'B = (4, 0)', 'M = Midpoint(A, B)'])
  })

  it('座標軸・グリッドは出さない', () => {
    const api = new FakeGgb([
      { name: 'xAxis', type: 'line', command: '' },
      { name: 'yAxis', type: 'line', command: '' },
      { name: 'grid', type: 'grid', command: '' },
      freePoint('A', 1, 2)
    ])

    expect(exportConstructionCommands(api)).toEqual({ lines: ['A = (1, 2)'], skipped: [] })
  })

  it('1 つの作図の読み取りが失敗しても、ほかは出せる', () => {
    const api = new FakeGgb([freePoint('A', 1, 2), { name: 'B', type: 'point', command: '(3, 4)', independent: true }])
    const broken: ConstructionReadApi = {
      getAllObjectNames: () => api.getAllObjectNames(),
      getCommandString: (name: string) => {
        if (name === 'B') throw new Error('壊れた作図')
        return api.getCommandString(name)
      },
      getAlgorithmXML: (name: string) => api.getAlgorithmXML(name),
      getSiblingObjectNames: (name: string) => api.getSiblingObjectNames(name),
      getObjectType: (name: string) => api.getObjectType(name),
      isIndependent: (name: string) => api.isIndependent(name),
      getXcoord: (name: string) => api.getXcoord(name),
      getYcoord: (name: string) => api.getYcoord(name),
      getValueString: (name: string) => api.getValueString(name),
      getXML: () => api.getXML()
    }

    expect(exportConstructionCommands(broken).lines).toEqual(['A = (1, 2)'])
    expect(exportConstructionCommands(broken).skipped).toEqual([
      { name: 'B', type: 'point', reason: 'no-command' }
    ])
  })

  it('文字列の中の = を「定義そのもの」と取り違えない', () => {
    const api = new FakeGgb([
      { name: 'A', type: 'point', command: '(0, 0)', independent: true, x: 0, y: 0 },
      { name: 't', type: 'text', command: 'Text("a=b", A)', algorithm: { command: 'Text', inputs: ['A'], outputs: ['t'] } }
    ])

    expect(exportConstructionCommands(api).lines).toEqual(['A = (0, 0)', 't = Text("a=b", A)'])
  })
})
