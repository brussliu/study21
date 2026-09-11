/**
 * 日付ユーティリティ。ISO-8601 を標準とする。
 */
export function nowIso(): string {
  return new Date().toISOString()
}

/**
 * ISO 文字列をローカル表示用の文字列へ整形する。
 * 不正な値は空文字を返す。
 */
export function formatIsoDateTime(iso: string, locale = 'ja-JP'): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return date.toLocaleString(locale)
}

export function formatIsoDate(iso: string, locale = 'ja-JP'): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return date.toLocaleDateString(locale)
}
