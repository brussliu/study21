import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import NotFoundView from '@/views/error/NotFoundView.vue'

describe('mobile 404 page', () => {
  it('404 ページを表示する', () => {
    const wrapper = mount(NotFoundView, {
      global: { stubs: { RouterLink: true } }
    })
    expect(wrapper.text()).toContain('404')
    expect(wrapper.text()).toContain('ページが見つかりません')
  })
})
