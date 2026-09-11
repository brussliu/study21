/* eslint-disable vue/one-component-per-file */
import { describe, expect, it } from 'vitest'
import { shallowMount, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'
import UserLayout from '@/layouts/UserLayout.vue'
import AppSidebar from '@/components/layout/AppSidebar.vue'

const RouterLinkStub = defineComponent({
  name: 'RouterLinkStub',
  setup(_, { slots }) {
    return () => h('a', slots.default ? slots.default() : [])
  }
})

const Dummy = defineComponent({
  name: 'Dummy',
  render: () => null
})

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: Dummy }]
  })
}

describe('layouts', () => {
  it('AdminLayout を読み込める', () => {
    const wrapper = shallowMount(AdminLayout, {
      global: { stubs: { RouterView: true } }
    })
    expect(wrapper.classes()).toContain('app')
    expect(wrapper.classes()).toContain('app--admin')
  })

  it('UserLayout を読み込める', () => {
    const wrapper = shallowMount(UserLayout, {
      global: { plugins: [createTestRouter()], stubs: { RouterView: true } }
    })
    expect(wrapper.classes()).toContain('app')
    expect(wrapper.classes()).toContain('app--user')
  })

  it('AppSidebar は移行対象の業務メニューを表示する', () => {
    const wrapper = mount(AppSidebar, {
      props: { area: 'admin' },
      global: { plugins: [createPinia(), createTestRouter()], stubs: { RouterLink: RouterLinkStub } }
    })
    expect(wrapper.text()).toContain('ホーム')
    expect(wrapper.text()).toContain('英語勉強')
    expect(wrapper.text()).toContain('数学勉強')
    expect(wrapper.text()).toContain('学習状況モニター')
    expect(wrapper.text()).toContain('ログアウト')
  })
})
