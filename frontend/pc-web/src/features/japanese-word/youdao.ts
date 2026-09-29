/**
 * 単語の意味を**外部の辞書**で引くリンク。
 *
 * <p>利用者の指示（2026-09-26）: 単語一覧の「単語」をリンクにし、押すと有道（Youdao）の
 * **日本語辞書**を別タブで開く。形は利用者が示した例のとおり:</p>
 *
 * <pre>https://www.youdao.com/result?word=えくぼ&amp;lang=ja</pre>
 *
 * <p>語は `encodeURIComponent` で繋ぐ（`&` や空白を含む語でもパラメータが壊れないように）。</p>
 */

/** 有道の検索結果（言語は `ja` 固定＝日本語辞書）。 */
const YOUDAO_RESULT = 'https://www.youdao.com/result'

/** その語の辞書ページの URL。 */
export function youdaoWordUrl(word: string): string {
  return `${YOUDAO_RESULT}?word=${encodeURIComponent(word.trim())}&lang=ja`
}
