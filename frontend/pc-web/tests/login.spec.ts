import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import UserLoginView from '@/views/login/UserLoginView.vue'
import AdminLoginView from '@/views/login/AdminLoginView.vue'
import { createAppRouter } from '@/router'
import { useAuthStore } from '@/stores/auth'
import { loginAccount } from '@/api/register'

vi.mock('@/api/register', () => ({
  loginAccount: vi.fn()
}))

describe('login pages', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    window.sessionStorage.clear()
    window.scrollTo = vi.fn()
  })

  it('一般ユーザーは認証失敗時にホーム画面へ遷移しない', async () => {
    vi.mocked(loginAccount).mockRejectedValueOnce(new Error('認証失敗'))
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createAppRouter()
    await router.push('/login')
    await router.isReady()
    const wrapper = mount(UserLoginView, { global: { plugins: [pinia, router] } })

    await wrapper.find('input[type="email"]').setValue('unknown@example.com')
    await wrapper.find('input[type="password"]').setValue('wrong-password')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(loginAccount).toHaveBeenCalledWith({
      loginId: 'unknown@example.com',
      password: 'wrong-password'
    })
    expect(useAuthStore().isAuthenticated).toBe(false)
    expect(router.currentRoute.value.path).toBe('/login')
    expect(wrapper.get('[role="alert"]').text()).toBe('認証失敗')
  })

  it.each([
    { accountType: 'STUDENT' as const, expectedPath: '/student/home' },
    { accountType: 'GUARDIAN' as const, expectedPath: '/parent/home' }
  ])('一般ユーザーは$accountType認証成功後に対応するホーム画面へ進む', async ({ accountType, expectedPath }) => {
    vi.mocked(loginAccount).mockResolvedValueOnce({
      success: true,
      code: 'OK',
      message: 'OK',
      data: {
        accountId: 1,
        loginId: 'user@example.com',
        displayName: 'テスト ユーザー',
        accountType,
        expiryDate: '2026-10-09'
      },
      traceId: 'test-trace',
      timestamp: '2026-09-09T00:00:00Z'
    })
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createAppRouter()
    await router.push('/login')
    await router.isReady()
    const wrapper = mount(UserLoginView, { global: { plugins: [pinia, router] } })

    await wrapper.find('input[type="email"]').setValue('user@example.com')
    await wrapper.find('input[type="password"]').setValue('correct-password')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(useAuthStore().role).toBe(accountType)
    expect(useAuthStore().currentUser).toBe('テスト ユーザー')
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe(expectedPath))
  })

  it('管理者は仮認証で管理者画面へ進める', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createAppRouter()
    await router.push('/admin/login')
    await router.isReady()
    const wrapper = mount(AdminLoginView, { global: { plugins: [pinia, router] } })

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(useAuthStore().role).toBe('ADMIN')
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/admin/home'))
  })
})
