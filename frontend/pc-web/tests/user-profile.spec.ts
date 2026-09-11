import { beforeEach, describe, expect, it } from 'vitest'
import { DOMWrapper, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import AppTopbar from '@/components/layout/AppTopbar.vue'
import { useAuthStore } from '@/stores/auth'

function mountTopbar(): VueWrapper {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().fakeLogin('STUDENT', '山田 太郎')
  // メニューとダイアログを document から探せるように body へ取り付ける。
  return mount(AppTopbar, { global: { plugins: [pinia] }, attachTo: document.body })
}

/** ダイアログは body へ Teleport されるので、document から探す。 */
function dialog(): DOMWrapper<Element> | null {
  const element = document.body.querySelector('.dialog')
  return element ? new DOMWrapper(element) : null
}

function menuItem(label: string): DOMWrapper<Element> {
  const items = [...document.querySelectorAll('.up-menu__item')]
  const found = items.find((item) => item.textContent?.includes(label))
  if (!found) throw new Error(`menu item not found: ${label}`)
  return new DOMWrapper(found)
}

function toastMessages(): string[] {
  return useToast().items.map((item) => item.message)
}

function toastTypes(): string[] {
  return useToast().items.map((item) => item.type)
}

beforeEach(() => {
  useToast().items.splice(0)
  document.body.querySelectorAll('.overlay').forEach((node) => node.remove())
})

describe('右上のユーザーメニュー', () => {
  it('ユーザー名はメニューのボタンになっている（初期は閉じている）', () => {
    const wrapper = mountTopbar()
    const user = wrapper.find('.up-user')
    expect(user.exists()).toBe(true)
    expect(user.text()).toContain('山田 太郎')
    expect(user.attributes('aria-haspopup')).toBe('menu')
    expect(user.attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('.up-menu').exists()).toBe(false)
    wrapper.unmount()
  })

  it('クリックするとメニューが開き、2 つの項目とデモの注記を出す', async () => {
    const wrapper = mountTopbar()
    await wrapper.find('.up-user').trigger('click')

    const menu = wrapper.find('.up-menu')
    expect(menu.exists()).toBe(true)
    expect(menu.attributes('role')).toBe('menu')
    const labels = menu.findAll('[role="menuitem"]').map((item) => item.text())
    expect(labels).toEqual(['ユーザー情報の変更', 'パスワードの変更'])
    expect(menu.text()).toContain('デモ表示（保存されません）')
    expect(wrapper.find('.up-user').attributes('aria-expanded')).toBe('true')
    wrapper.unmount()
  })

  it('メニュー外のクリックと Escape で閉じる', async () => {
    const wrapper = mountTopbar()
    await wrapper.find('.up-user').trigger('click')
    expect(wrapper.find('.up-menu').exists()).toBe(true)

    document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.up-menu').exists()).toBe(false)

    await wrapper.find('.up-user').trigger('click')
    expect(wrapper.find('.up-menu').exists()).toBe(true)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.up-menu').exists()).toBe(false)
    wrapper.unmount()
  })
})

describe('ユーザー情報の変更（デモ画面）', () => {
  it('メニューから開くと、デモ表示であることと現在の情報が出る', async () => {
    const wrapper = mountTopbar()
    await wrapper.find('.up-user').trigger('click')
    await menuItem('ユーザー情報の変更').trigger('click')
    await wrapper.vm.$nextTick()

    const panel = dialog()
    expect(panel).not.toBeNull()
    expect(panel!.text()).toContain('ユーザー情報の変更')
    expect(panel!.text()).toContain('デモ')
    expect(panel!.text()).toContain('この画面はデモ表示です')
    // メニューは閉じている
    expect(wrapper.find('.up-menu').exists()).toBe(false)

    // 表示名はログイン中のユーザー名が入っている
    const name = panel!.find<HTMLInputElement>('#upName')
    expect((name.element as HTMLInputElement).value).toBe('山田 太郎')
    // 権限は読み取り専用のバッジ
    expect(panel!.text()).toContain('学生')
    wrapper.unmount()
  })

  it('表示名を消して保存すると、エラーが出て閉じない', async () => {
    const wrapper = mountTopbar()
    await wrapper.find('.up-user').trigger('click')
    await menuItem('ユーザー情報の変更').trigger('click')
    await wrapper.vm.$nextTick()

    const panel = dialog()!
    const name = panel.find<HTMLInputElement>('#upName')
    await name.setValue('')
    const save = panel.findAll('button').find((button) => button.text().includes('保存'))!
    await save.trigger('click')
    await wrapper.vm.$nextTick()

    expect(dialog()).not.toBeNull()
    expect(dialog()!.find('.field__error').text()).toBe('表示名を入力してください。')
    expect(name.classes()).toContain('is-invalid')
    expect(toastTypes()).toContain('warning')
    expect(toastMessages()).toContain('入力内容を確認してください。')
    wrapper.unmount()
  })

  it('メールアドレスの形式とパスワードの一致もチェックする', async () => {
    const wrapper = mountTopbar()
    await wrapper.find('.up-user').trigger('click')
    await menuItem('パスワードの変更').trigger('click')
    await wrapper.vm.$nextTick()

    const panel = dialog()!
    await panel.find<HTMLInputElement>('#upEmail').setValue('not-an-email')
    await panel.find<HTMLInputElement>('#upNew').setValue('newpass123')
    await panel.find<HTMLInputElement>('#upConfirm').setValue('newpass124')
    const save = panel.findAll('button').find((button) => button.text().includes('保存'))!
    await save.trigger('click')
    await wrapper.vm.$nextTick()

    const errors = dialog()!.findAll('.field__error').map((error) => error.text())
    expect(errors).toContain('メールアドレスの形式が正しくありません。')
    expect(errors).toContain('現在のパスワードを入力してください。')
    expect(errors).toContain('新しいパスワードが一致しません。')
    wrapper.unmount()
  })

  it('「パスワードの変更」から開くとパスワード欄が展開されている', async () => {
    const wrapper = mountTopbar()
    await wrapper.find('.up-user').trigger('click')
    await menuItem('パスワードの変更').trigger('click')
    await wrapper.vm.$nextTick()

    const panel = dialog()!
    const toggle = panel.find<HTMLInputElement>('input[type="checkbox"]')
    expect((toggle.element as HTMLInputElement).checked).toBe(true)
    expect(panel.find('#upCurrent').exists()).toBe(true)
    expect(panel.find('#upNew').exists()).toBe(true)
    expect(panel.find('#upConfirm').exists()).toBe(true)

    // 目のアイコンで表示／非表示を切り替えられる（新しいパスワード欄のボタン）
    const eye = panel.findAll('button')
      .find((button) => button.attributes('aria-label') === '新しいパスワードを表示')!
    expect(eye).toBeTruthy()
    expect((panel.find('#upNew').element as HTMLInputElement).type).toBe('password')
    await eye.trigger('click')
    expect((panel.find('#upNew').element as HTMLInputElement).type).toBe('text')
    wrapper.unmount()
  })

  it('正しく入力して保存すると、デモのため保存しない旨を出して閉じる', async () => {
    const wrapper = mountTopbar()
    await wrapper.find('.up-user').trigger('click')
    await menuItem('ユーザー情報の変更').trigger('click')
    await wrapper.vm.$nextTick()

    const panel = dialog()!
    await panel.find<HTMLInputElement>('#upName').setValue('山田 花子')
    await panel.find<HTMLInputElement>('#upEmail').setValue('hanako@example.com')
    const save = panel.findAll('button').find((button) => button.text().includes('保存'))!
    await save.trigger('click')
    await wrapper.vm.$nextTick()

    expect(toastTypes()).toContain('info')
    expect(toastMessages()).toContain('デモ表示のため、内容は保存していません。')
    expect(dialog()).toBeNull()
    // 保存しても表示名（画面上のセッション）は変わらない
    expect(wrapper.find('.up-user').text()).toContain('山田 太郎')
    wrapper.unmount()
  })

  it('「初期値に戻す」で入力が元に戻る', async () => {
    const wrapper = mountTopbar()
    await wrapper.find('.up-user').trigger('click')
    await menuItem('ユーザー情報の変更').trigger('click')
    await wrapper.vm.$nextTick()

    const panel = dialog()!
    await panel.find<HTMLInputElement>('#upName').setValue('変更中')
    const reset = panel.findAll('button').find((button) => button.text().includes('初期値に戻す'))!
    await reset.trigger('click')
    await wrapper.vm.$nextTick()

    expect((dialog()!.find<HTMLInputElement>('#upName').element as HTMLInputElement).value).toBe('山田 太郎')
    wrapper.unmount()
  })

  it('キャンセルと × と Escape で閉じる', async () => {
    const wrapper = mountTopbar()
    await wrapper.find('.up-user').trigger('click')
    await menuItem('ユーザー情報の変更').trigger('click')
    await wrapper.vm.$nextTick()

    const cancel = dialog()!.findAll('button').find((button) => button.text() === 'キャンセル')!
    await cancel.trigger('click')
    await wrapper.vm.$nextTick()
    expect(dialog()).toBeNull()

    await wrapper.find('.up-user').trigger('click')
    await menuItem('ユーザー情報の変更').trigger('click')
    await wrapper.vm.$nextTick()
    // ダイアログは window の keydown を監視しているので、bubbles を付けて伝播させる
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(dialog()).toBeNull()
    wrapper.unmount()
  })
})
