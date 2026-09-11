/* eslint-disable vue/one-component-per-file */
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { defineComponent, h } from 'vue'
import MobileTopbar from '@/components/layout/MobileTopbar.vue'
import MobileBottomNav from '@/components/layout/MobileBottomNav.vue'
import MobileDrawer from '@/components/layout/MobileDrawer.vue'

const RouterLinkStub = defineComponent({
  name: 'RouterLinkStub',
  setup(_, { slots }) {
    return () => h('a', slots.default ? slots.default() : [])
  }
})

const TeleportStub = defineComponent({
  name: 'TeleportStub',
  setup(_, { slots }) {
    return () => h('div', slots.default ? slots.default() : [])
  }
})

describe('mobile layout components', () => {
  it('MobileTopbar はタイトルを表示する', () => {
    const wrapper = mount(MobileTopbar, {
      global: { plugins: [createPinia()], stubs: { RouterLink: RouterLinkStub } }
    })
    expect(wrapper.text()).toContain('Study 2.1')
  })

  it('MobileBottomNav はホームとメニューを表示する', () => {
    const wrapper = mount(MobileBottomNav, {
      props: { homePath: '/student/home' },
      global: { stubs: { RouterLink: RouterLinkStub } }
    })
    expect(wrapper.text()).toContain('ホーム')
    expect(wrapper.text()).toContain('メニュー')
  })

  it('MobileDrawer は未実装表示を持つ', () => {
    const wrapper = mount(MobileDrawer, {
      props: { open: true, area: 'admin', homePath: '/admin/home' },
      global: { stubs: { RouterLink: RouterLinkStub, Teleport: TeleportStub } }
    })
    expect(wrapper.text()).toContain('業務メニューは未実装')
  })
})
