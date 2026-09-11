import { computed, reactive } from 'vue'

/** 遊び方カードの表示言語。 */
export type HelpLanguage = 'ja' | 'zh'

/**
 * 遊び方カードの言語（アプリ内で共有する画面状態）。
 * DB にも localStorage にも保存しない（再読込で日本語に戻る）。
 * ゲームを切り替えても選んだ言語が続くように、モジュール単位で 1 つだけ持つ。
 */
export interface HelpLanguageState {
  /** 現在の言語。 */
  language: HelpLanguage
  /** 日本語を表示中か。 */
  isJa: boolean
  /** 中国語を表示中か。 */
  isZh: boolean
  /** 言語を指定して切り替える。 */
  setLanguage: (next: HelpLanguage) => void
  /** 日本語 ⇄ 中国語をトグルする。 */
  toggle: () => void
}

const state = reactive<{ language: HelpLanguage }>({ language: 'ja' })
const isJa = computed(() => state.language === 'ja')
const isZh = computed(() => state.language === 'zh')

function setLanguage(next: HelpLanguage): void {
  state.language = next
}

function toggle(): void {
  state.language = state.language === 'ja' ? 'zh' : 'ja'
}

/**
 * 遊び方カードの言語状態を取得する。
 * テンプレートからは `help.language` / `help.isJa` のようにそのまま読める。
 */
export function useHelpLanguage(): HelpLanguageState {
  return {
    get language(): HelpLanguage {
      return state.language
    },
    get isJa(): boolean {
      return isJa.value
    },
    get isZh(): boolean {
      return isZh.value
    },
    setLanguage,
    toggle
  }
}
