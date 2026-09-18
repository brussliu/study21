import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { Role } from '@study21/web-shared'

// v2 は旧モック認証セッションを引き継がない。実API認証成功後だけ保存する。
const STORAGE_KEY = 'study21.auth.v2'

interface AuthSession {
  username: string
  role: Role
}

function readSession(): AuthSession | null {
  if (typeof window === 'undefined') return null
  try {
    const value = window.sessionStorage.getItem(STORAGE_KEY)
    return value ? (JSON.parse(value) as AuthSession) : null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const initial = readSession()
  const currentUser = ref<string | null>(initial?.username ?? null)
  const role = ref<Role | null>(initial?.role ?? null)
  const isAuthenticated = computed(() => currentUser.value !== null && role.value !== null)

  /** 認証済みユーザーの画面セッションを保存する。パスワードは保存しない。 */
  function login(nextRole: Role, username: string): void {
    const normalizedUsername = username.trim()
    if (normalizedUsername === '') {
      throw new Error('認証済みユーザー名が空です。')
    }
    currentUser.value = normalizedUsername
    role.value = nextRole
    window.sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ username: normalizedUsername, role: nextRole } satisfies AuthSession)
    )
  }

  /**
   * 表示名だけを差し替える（「ユーザー情報の修正」で保存したあと、
   * ヘッダーの名前を再ログインなしで反映するため）。
   */
  function updateDisplayName(name: string): void {
    const normalized = name.trim()
    if (normalized === '' || role.value === null) return
    login(role.value, normalized)
  }

  function logout(): void {
    if (role.value === 'STUDENT' || role.value === 'GUARDIAN') {
      void fetch('/api/user/logout', { method: 'POST' }).catch(() => undefined)
    }
    currentUser.value = null
    role.value = null
    window.sessionStorage.removeItem(STORAGE_KEY)
  }

  return { isAuthenticated, currentUser, role, login, updateDisplayName, logout }
})
