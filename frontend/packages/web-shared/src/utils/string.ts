/**
 * 文字列ユーティリティ。ビジネス意味を持たない汎用処理のみ。
 */
export function isBlank(value: string | null | undefined): boolean {
  return value === null || value === undefined || value.trim().length === 0
}

export function truncate(value: string, max: number): string {
  if (value.length <= max) {
    return value
  }
  return `${value.slice(0, max)}…`
}

export function capitalize(value: string): string {
  if (value.length === 0) {
    return value
  }
  return value.charAt(0).toUpperCase() + value.slice(1)
}

export function toKebabCase(value: string): string {
  return value
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .replace(/[\s_]+/g, '-')
    .toLowerCase()
}
