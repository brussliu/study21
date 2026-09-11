import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ForgotPasswordView from '@/views/login/ForgotPasswordView.vue'
import UserLoginView from '@/views/login/UserLoginView.vue'
import { createAppRouter } from '@/router'
import { useAuthStore } from '@/stores/auth'

describe('password recovery demo', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    window.scrollTo = vi.fn()
  })
  afterEach(() => vi.unstubAllGlobals())

  async function setup(path = '/forgot-password') {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createAppRouter()
    await router.push(path)
    await router.isReady()
    return { router, global: { plugins: [pinia, router] } }
  }

  it('offers recovery instead of an administrator link on user login', async () => {
    const { router, global } = await setup('/login')
    const wrapper = mount(UserLoginView, { global })
    expect(wrapper.find('a[href="/admin/login"]').exists()).toBe(false)
    await wrapper.get('a[href="/forgot-password"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('forgot-password')
    expect(router.currentRoute.value.meta.requiresAuth).toBeFalsy()
    wrapper.unmount()
  })

  it('validates the email and passwords, completes locally, and returns to login', async () => {
    const { router, global } = await setup()
    const fetch = vi.fn()
    vi.stubGlobal('fetch', fetch)
    const wrapper = mount(ForgotPasswordView, { global })
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('#email-error').text()).toContain('有効なメールアドレス')
    await wrapper.get('#recovery-email').setValue('invalid')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.find('#email-error').exists()).toBe(true)
    await wrapper.get('#recovery-email').setValue(' learner@example.com ')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('.recovery__email').text()).toBe('learner@example.com')
    await wrapper.get('.recovery__text-button').trigger('click')
    expect((wrapper.get('#recovery-email').element as HTMLInputElement).value).toBe('learner@example.com')
    await wrapper.get('form').trigger('submit')
    await wrapper.get('.recovery__primary').trigger('click')
    await wrapper.get('#recovery-password').setValue('short')
    await wrapper.get('#recovery-confirmation').setValue('different')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.find('#password-error').exists()).toBe(true)
    expect(wrapper.find('#confirmation-error').exists()).toBe(true)
    await wrapper.get('#recovery-password').setValue('DemoPass123')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.find('#password-error').exists()).toBe(false)
    expect(wrapper.find('#confirmation-error').exists()).toBe(true)
    await wrapper.get('#recovery-confirmation').setValue('DemoPass123')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('h1').text()).toBe('パスワードを再設定しました')
    expect(wrapper.text()).toContain('新しいパスワードでログインしてください。')
    expect(wrapper.find('input[type="password"]').exists()).toBe(false)
    expect(fetch).not.toHaveBeenCalled()
    expect(useAuthStore().isAuthenticated).toBe(false)
    expect(window.sessionStorage.length).toBe(0)
    await wrapper.get('a[href="/login"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/login')
    wrapper.unmount()
  })
})
