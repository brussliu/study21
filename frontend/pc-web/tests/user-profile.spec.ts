import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DOMWrapper, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import AppTopbar from '@/components/layout/AppTopbar.vue'
import { reloadParentPage } from '@/features/user-profile/reloadPage'
import { createAppRouter } from '@/router'
import { useAuthStore } from '@/stores/auth'

/**
 * 「ユーザー情報の修正」「パスワードの変更」。
 *
 * どちらもページ遷移せず、右上のユーザー名メニューから**別々のダイアログ**で開く。
 * 保存すると親ページ（開いていた画面）を再読み込みして変更を反映する。
 * メールアドレスはログインID を兼ねるため変更できない（参照のみ）。
 */
vi.mock('@/features/user-profile/reloadPage', () => ({
  reloadParentPage: vi.fn()
}))

const PROFILE = {
  accountId: 2,
  email: 'student@example.com',
  sei: '山田',
  mei: '太郎',
  seiKana: 'やまだ',
  meiKana: 'たろう',
  grade: '中学1年生',
  phone: '090-0000-0000',
  mailNotify: true,
  reminderNotify: false,
  accountType: 'STUDENT' as const,
  displayName: '山田 太郎'
}

type Profile = Omit<typeof PROFILE, 'accountType' | 'grade'> & {
  accountType: 'STUDENT' | 'GUARDIAN'
  grade: string | null
}

function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function failure(message: string, status = 400): Response {
  return new Response(
    JSON.stringify({ success: false, code: 'VALIDATION_ERROR', message, data: null, timestamp: '' }),
    { status, headers: { 'Content-Type': 'application/json' } }
  )
}

function toastMessages(): string[] {
  return useToast().items.map((item) => item.message)
}

function methodOf(call: unknown[]): string {
  return ((call[1] as RequestInit).method ?? 'GET').toUpperCase()
}

function urlOf(call: unknown[]): string {
  return String(call[0])
}

function bodyOf(call: unknown[]): Record<string, unknown> {
  return JSON.parse(String((call[1] as RequestInit).body)) as Record<string, unknown>
}

/** ダイアログは body へ Teleport されるので document から探す。 */
function dialog(): DOMWrapper<Element> | null {
  const element = document.body.querySelector('.dialog')
  return element ? new DOMWrapper(element) : null
}

function menuItem(label: string): DOMWrapper<Element> {
  const found = [...document.querySelectorAll('.up-menu__item')].find((item) =>
    item.textContent?.includes(label)
  )
  if (!found) throw new Error(`menu item not found: ${label}`)
  return new DOMWrapper(found)
}

async function mountTopbar(role: 'STUDENT' | 'GUARDIAN' | 'ADMIN', profile: Profile = PROFILE): Promise<{
  wrapper: VueWrapper
  fetchMock: ReturnType<typeof vi.fn>
  router: ReturnType<typeof createAppRouter>
}> {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().fakeLogin(role)
  const router = createAppRouter()
  await router.push('/student/home')
  await router.isReady()

  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const method = (init?.method ?? 'GET').toUpperCase()
    if (url.includes('/profile/password')) return ok(null, 'パスワードを変更しました。')
    if (method === 'PUT') {
      const body = bodyOf([url, init])
      // サーバーは表示名（姓 + 名）を組み立て直して返す
      return ok(
        { ...profile, ...body, displayName: `${String(body.sei)} ${String(body.mei)}` },
        'ユーザー情報を更新しました。'
      )
    }
    return ok(profile)
  })
  vi.stubGlobal('fetch', fetchMock)

  const wrapper = mount(AppTopbar, {
    global: { plugins: [pinia, router] },
    attachTo: document.body
  })
  await wrapper.get('.topbar__user').trigger('click')
  return { wrapper, fetchMock, router }
}

beforeEach(() => {
  useToast().items.splice(0)
  window.sessionStorage.clear()
  document.body.innerHTML = ''
  window.scrollTo = vi.fn()
  vi.mocked(reloadParentPage).mockClear()
})

describe('右上メニューからのダイアログ表示', () => {
  it('ページ遷移せずにユーザー情報の修正ダイアログを開く', async () => {
    const { router } = await mountTopbar('STUDENT')

    expect(menuItem('ユーザー情報の修正').exists()).toBe(true)
    await menuItem('ユーザー情報の修正').trigger('click')
    await flushPromises()

    const dlg = dialog()
    expect(dlg).not.toBeNull()
    expect(dlg?.text()).toContain('ユーザー情報の修正')
    // 画面遷移はしていない
    expect(router.currentRoute.value.name).toBe('student-home')
    expect(router.currentRoute.value.path).toBe('/student/home')
  })

  it('パスワードの変更は別のダイアログで開く', async () => {
    const { router } = await mountTopbar('STUDENT')

    await menuItem('パスワードの変更').trigger('click')
    await flushPromises()

    const dlg = dialog()
    expect(dlg?.text()).toContain('パスワードの変更')
    expect(dlg?.find('#pwCurrent').exists()).toBe(true)
    // 情報修正の項目は出ない（別ダイアログ）
    expect(dlg?.find('#upSei').exists()).toBe(false)
    expect(router.currentRoute.value.path).toBe('/student/home')
  })

  it('保護者でも同じダイアログを使う', async () => {
    await mountTopbar('GUARDIAN', { ...PROFILE, accountType: 'GUARDIAN', grade: null })

    await menuItem('ユーザー情報の修正').trigger('click')
    await flushPromises()

    expect(dialog()?.text()).toContain('ユーザー情報の修正')
  })

  it('管理者にはこのメニューを出さない（機能の対象外）', async () => {
    await mountTopbar('ADMIN')

    expect(document.querySelectorAll('.up-menu__item')).toHaveLength(0)
  })
})

describe('ユーザー情報の修正ダイアログ', () => {
  async function openProfileDialog(role: 'STUDENT' | 'GUARDIAN' = 'STUDENT', profile: Profile = PROFILE) {
    const mounted = await mountTopbar(role, profile)
    await menuItem('ユーザー情報の修正').trigger('click')
    await flushPromises()
    return mounted
  }

  it('保存済みの内容を表示し、メールアドレスは変更できない', async () => {
    await openProfileDialog()

    const dlg = dialog()
    expect(dlg?.get('#upSei').element).toHaveProperty('value', '山田')
    expect(dlg?.get('[data-testid="profile-email"]').text()).toBe(PROFILE.email)
    const emailInputs = (dlg?.findAll('input') ?? []).filter(
      (input) => (input.element as HTMLInputElement).value === PROFILE.email
    )
    expect(emailInputs).toHaveLength(0)
    expect(dlg?.text()).toContain('変更できません')
  })

  it('生徒は学年を編集でき、保護者には出さない', async () => {
    await openProfileDialog()
    expect(dialog()?.find('#upGrade').exists()).toBe(true)

    document.body.innerHTML = ''
    await openProfileDialog('GUARDIAN', { ...PROFILE, accountType: 'GUARDIAN', grade: null })
    expect(dialog()?.find('#upGrade').exists()).toBe(false)
  })

  it('必須項目が空だと保存せずにエラーを出す', async () => {
    const { fetchMock } = await openProfileDialog()

    await dialog()!.get('#upSei').setValue('   ')
    await dialog()!.get('.up-foot .btn--primary').trigger('click')
    await flushPromises()

    expect(dialog()?.text()).toContain('姓を入力してください。')
    expect(fetchMock.mock.calls.some((call) => methodOf(call) === 'PUT')).toBe(false)
    expect(toastMessages()).toContain('入力内容を確認してください。')
  })

  it('保存すると更新し、ダイアログを閉じて親ページを再読み込みする', async () => {
    const { fetchMock } = await openProfileDialog()

    await dialog()!.get('#upSei').setValue('鈴木')
    await dialog()!.get('#upMei').setValue('花子')
    await dialog()!.get('#upPhone').setValue('090-1111-2222')
    await dialog()!.get('#upGrade').setValue('中学2年生')
    const checks = dialog()!.findAll('.up-check-list input[type="checkbox"]')
    await checks[0].setValue(false)
    await checks[1].setValue(true)
    await dialog()!.get('.up-foot .btn--primary').trigger('click')
    await flushPromises()

    const put = fetchMock.mock.calls.find((call) => methodOf(call) === 'PUT')
    expect(urlOf(put as unknown[])).toBe('/api/user/profile')
    expect(bodyOf(put as unknown[])).toMatchObject({
      sei: '鈴木',
      mei: '花子',
      grade: '中学2年生',
      phone: '090-1111-2222',
      mailNotify: false,
      reminderNotify: true
    })
    expect(bodyOf(put as unknown[])).not.toHaveProperty('email')
    expect(useAuthStore().currentUser).toBe('鈴木 花子')
    expect(toastMessages()).toContain('ユーザー情報を更新しました。')
    // ダイアログは閉じ、親ページは再読み込みされる
    expect(dialog()).toBeNull()
    expect(reloadParentPage).toHaveBeenCalledTimes(1)
  })

  it('キャンセルでは保存も再読み込みもしない', async () => {
    const { fetchMock } = await openProfileDialog()

    await dialog()!.get('#upSei').setValue('変更')
    await dialog()!.get('.up-foot .btn--secondary').trigger('click')
    await flushPromises()

    expect(dialog()).toBeNull()
    expect(fetchMock.mock.calls.some((call) => methodOf(call) === 'PUT')).toBe(false)
    expect(reloadParentPage).not.toHaveBeenCalled()
  })

  it('サーバーがエラーを返したらその文言を表示し、ダイアログは開いたまま', async () => {
    const { fetchMock } = await openProfileDialog()
    fetchMock.mockImplementation(async (_url: string, init?: RequestInit) =>
      (init?.method ?? 'GET').toUpperCase() === 'PUT'
        ? failure('ふりがな（せい）を入力してください。')
        : ok(PROFILE)
    )

    await dialog()!.get('.up-foot .btn--primary').trigger('click')
    await flushPromises()

    expect(toastMessages()).toContain('ふりがな（せい）を入力してください。')
    expect(dialog()).not.toBeNull()
    expect(reloadParentPage).not.toHaveBeenCalled()
  })
})

describe('パスワードの変更ダイアログ', () => {
  async function openPasswordDialog() {
    const mounted = await mountTopbar('STUDENT')
    await menuItem('パスワードの変更').trigger('click')
    await flushPromises()
    return mounted
  }

  it('英字と数字を含む8文字以上でないと変更できない', async () => {
    const { fetchMock } = await openPasswordDialog()

    await dialog()!.get('#pwCurrent').setValue('secret123')
    await dialog()!.get('#pwNew').setValue('abcdefgh')
    await dialog()!.get('#pwConfirm').setValue('abcdefgh')
    await dialog()!.get('.up-foot .btn--primary').trigger('click')
    await flushPromises()

    expect(dialog()?.text()).toContain('英字と数字をそれぞれ1文字以上')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('確認用が一致しないと変更できない', async () => {
    const { fetchMock } = await openPasswordDialog()

    await dialog()!.get('#pwCurrent').setValue('secret123')
    await dialog()!.get('#pwNew').setValue('newpass123')
    await dialog()!.get('#pwConfirm').setValue('newpass124')
    await dialog()!.get('.up-foot .btn--primary').trigger('click')
    await flushPromises()

    expect(dialog()?.text()).toContain('新しいパスワードが一致しません。')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('変更できたら API を呼び、ダイアログを閉じて親ページを再読み込みする', async () => {
    const { fetchMock } = await openPasswordDialog()

    await dialog()!.get('#pwCurrent').setValue('secret123')
    await dialog()!.get('#pwNew').setValue('newpass123')
    await dialog()!.get('#pwConfirm').setValue('newpass123')
    await dialog()!.get('.up-foot .btn--primary').trigger('click')
    await flushPromises()

    const call = fetchMock.mock.calls.find((entry) => urlOf(entry as unknown[]).includes('/password'))
    expect(urlOf(call as unknown[])).toBe('/api/user/profile/password')
    expect(methodOf(call as unknown[])).toBe('POST')
    expect(bodyOf(call as unknown[])).toEqual({ currentPassword: 'secret123', newPassword: 'newpass123' })
    expect(toastMessages()).toContain('パスワードを変更しました。')
    expect(dialog()).toBeNull()
    expect(reloadParentPage).toHaveBeenCalledTimes(1)
    // ログインは継続する
    expect(useAuthStore().isAuthenticated).toBe(true)
  })

  it('現在のパスワードが違うときはサーバーの文言を表示し、再読み込みしない', async () => {
    const { fetchMock } = await openPasswordDialog()
    fetchMock.mockImplementation(async () => failure('現在のパスワードが正しくありません。'))

    await dialog()!.get('#pwCurrent').setValue('wrongpass1')
    await dialog()!.get('#pwNew').setValue('newpass123')
    await dialog()!.get('#pwConfirm').setValue('newpass123')
    await dialog()!.get('.up-foot .btn--primary').trigger('click')
    await flushPromises()

    expect(toastMessages()).toContain('現在のパスワードが正しくありません。')
    expect(dialog()).not.toBeNull()
    expect(reloadParentPage).not.toHaveBeenCalled()
  })

  it('開き直すと入力内容は残さない', async () => {
    const { wrapper } = await openPasswordDialog()

    await dialog()!.get('#pwCurrent').setValue('secret123')
    await dialog()!.get('.up-foot .btn--secondary').trigger('click')
    await flushPromises()
    expect(dialog()).toBeNull()

    // メニューは閉じているので開き直してから選ぶ
    await wrapper.get('.topbar__user').trigger('click')
    await menuItem('パスワードの変更').trigger('click')
    await flushPromises()

    expect(dialog()?.get('#pwCurrent').element).toHaveProperty('value', '')
    expect(dialog()?.get('#pwNew').element).toHaveProperty('value', '')
  })
})
