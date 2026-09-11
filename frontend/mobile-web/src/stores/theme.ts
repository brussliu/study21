import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ThemeMode } from '@study21/web-shared'

const STORAGE_KEY = 'study21-theme'

function readInitialTheme(): ThemeMode {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'dark' || stored === 'light') {
    return stored
  }
  return 'light'
}

export const useThemeStore = defineStore('theme', () => {
  const theme = ref<ThemeMode>(readInitialTheme())

  function apply(): void {
    if (theme.value === 'dark') {
      document.documentElement.dataset.theme = 'dark'
    } else {
      delete document.documentElement.dataset.theme
    }
  }

  function setTheme(value: ThemeMode): void {
    theme.value = value
    localStorage.setItem(STORAGE_KEY, value)
    apply()
  }

  function toggle(): void {
    setTheme(theme.value === 'dark' ? 'light' : 'dark')
  }

  apply()

  return { theme, setTheme, toggle }
})
