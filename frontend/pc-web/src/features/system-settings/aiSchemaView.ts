/**
 * DTO から生成された **JSON Schema** を、Data TAB の「項目構造」表示へ変換する（純関数）。
 *
 * サーバー（admin-api の `AiResponseSchemaService`）が DTO から生成したスキーマをそのまま受け取り、
 * 画面はここで表示用の形に組み替える。**画面側に固定の定義は持たない**
 * （DTO を直せば、プロンプトへ注入する出力形式と、この表示の両方が自動で変わる）。
 *
 * 対応する形:
 * - `properties` + `required`（必須）
 * - `description`（`@Schema(description=...)` の説明）
 * - `enum`（選べる値）
 * - 入れ子の `object` と `array`（その場に展開された形）
 * - `$ref` / `$defs`（同じ型を 2 回以上使うと victools がこの形にする）
 */

/** 表示用の 1 項目。 */
export interface SchemaField {
  /** フィールド名（JSON のキー）。 */
  name: string
  /** 表示用の型（例: `string` / `array<string>` / `object`）。Enum は値を含める。 */
  type: string
  /** `@Schema(description)` の説明（無ければ空文字）。 */
  description: string
  /** 必須かどうか。 */
  required: boolean
  /** Enum の許容値（無ければ空配列）。 */
  enumValues: string[]
  /** 入れ子の項目（object / array<object> のときだけ）。 */
  children: SchemaField[]
}

/** 表示用の 1 行（入れ子を平坦化したもの）。 */
export interface SchemaRow extends SchemaField {
  /** ツリー上の位置（例: `入れ子.名前`）。開閉の判定に使う。 */
  path: string
  /** 入れ子の深さ（0 が最上位）。 */
  depth: number
  /** 子を持つか（展開ボタンを出すか）。 */
  hasChildren: boolean
  /** いま開いているか。 */
  expanded: boolean
}

type SchemaNode = Record<string, unknown>

/** JSON Schema を項目の一覧へ変換する。 */
export function fieldsOfSchema(schema: unknown): SchemaField[] {
  const root = asNode(schema)
  if (!root) {
    return []
  }
  return toFields(root, root, [])
}

/**
 * ツリーを行の一覧へ平坦化する（開いている行の子だけを出す）。
 *
 * @param isExpanded その行（{@link SchemaRow.path}）を開いているか
 */
export function flattenSchemaFields(
  fields: SchemaField[],
  isExpanded: (path: string) => boolean
): SchemaRow[] {
  const rows: SchemaRow[] = []
  const walk = (items: SchemaField[], depth: number, prefix: string): void => {
    for (const field of items) {
      const path = prefix === '' ? field.name : `${prefix}.${field.name}`
      const hasChildren = field.children.length > 0
      const expanded = hasChildren && isExpanded(path)
      rows.push({ ...field, path, depth, hasChildren, expanded })
      if (expanded) {
        walk(field.children, depth + 1, path)
      }
    }
  }
  walk(fields, 0, '')
  return rows
}

/** `properties` を表示用の項目へ変換する（順序はスキーマの定義順）。 */
function toFields(node: SchemaNode, root: SchemaNode, seen: string[]): SchemaField[] {
  const properties = asNode(node.properties)
  if (!properties) {
    return []
  }
  const required = new Set(Array.isArray(node.required) ? node.required.map((key) => String(key)) : [])
  return Object.entries(properties).map(([name, value]) => {
    const resolved = resolve(value, root, seen)
    return toField(name, resolved, required.has(name), root, seen)
  })
}

/** 1 項目を表示用に変換する。 */
function toField(
  name: string,
  node: SchemaNode,
  required: boolean,
  root: SchemaNode,
  seen: string[]
): SchemaField {
  const enumValues = Array.isArray(node.enum) ? node.enum.map((value) => String(value)) : []
  const description = typeof node.description === 'string' ? node.description : ''
  const kind = typeof node.type === 'string' ? node.type : ''
  const hasProperties = asNode(node.properties) !== null

  let type = kind || (enumValues.length > 0 ? 'string' : 'unknown')
  let children: SchemaField[] = []

  if (kind === 'array') {
    const items = asNode(node.items)
    const item = items ? resolve(items, root, seen) : null
    const itemType = item && typeof item.type === 'string' ? item.type : item ? 'object' : 'unknown'
    type = `array<${itemType}>`
    children = item ? toFields(item, root, seen) : []
  } else if (kind === 'object' || hasProperties) {
    type = 'object'
    children = toFields(node, root, seen)
  }
  if (enumValues.length > 0) {
    type = `${type}（${enumValues.join(' / ')}）`
  }
  return { name, type, description, required, enumValues, children }
}

/** `$ref` を `$defs` / `definitions` で解決する（循環参照では展開を止める）。 */
function resolve(value: unknown, root: SchemaNode, seen: string[]): SchemaNode {
  const node = asNode(value)
  if (!node) {
    return {}
  }
  const ref = typeof node.$ref === 'string' ? node.$ref : ''
  if (ref === '' || seen.includes(ref)) {
    // 循環している型はその場で打ち切る（無限に展開しない）
    const { $ref, ...rest } = node
    void $ref
    return rest
  }
  const name = ref.replace(/^#\/(\$defs|definitions)\//, '')
  const definitions = asNode(root.$defs) ?? asNode(root.definitions)
  const target = definitions ? asNode(definitions[name]) : null
  if (!target) {
    const { $ref: ignored, ...rest } = node
    void ignored
    return rest
  }
  // 参照先の説明が無いときは、参照側の説明（フィールドの @Schema）を残す
  const merged: SchemaNode = { ...target }
  if (typeof node.description === 'string' && typeof merged.description !== 'string') {
    merged.description = node.description
  }
  return merged
}

function asNode(value: unknown): SchemaNode | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? (value as SchemaNode)
    : null
}
