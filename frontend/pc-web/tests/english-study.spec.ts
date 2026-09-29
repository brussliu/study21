import { describe, expect, it } from 'vitest'
import { prototypeMenu } from '@/config/menuRegistry'
import { englishStudyRoutes } from '@/features/english-study/routes'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import AppSidebar from '@/components/layout/AppSidebar.vue'

describe('英語勉強の三階層メニュー', () => {
  it.each(['admin', 'student', 'parent'] as const)('%s で六画面を分類する', area => {
    const english = prototypeMenu(area).find(item => item.id === 'english')!
    const words = english.children!.find(item => item.label === '単語勉強')!
    const phrases = english.children!.find(item => item.label === '熟語勉強')!
    expect(words?.children?.map(item => item.path)).toEqual(['testword', 'word', 'word-status'].map(path => `/${area}/${path}`))
    expect(phrases?.children?.map(item => item.path)).toEqual(['phrase-test', 'phrase', 'phrase-status'].map(path => `/${area}/${path}`))
    expect(english.children!.filter(item => ['単語テスト', '熟語テスト', '単語情報管理', '単語勉強状況'].includes(item.label))).toHaveLength(0)
  })
  it('深いリンクから祖先を開き、現在のグループも折りたためる', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes: [...englishStudyRoutes('student').map(r => ({ ...r, path: `/student/${r.path}` })), { path: '/:pathMatch(.*)*', component: { template: '<div />' } }] })
    await router.push('/student/phrase-status')
    const wrapper = mount(AppSidebar, { props: { area: 'student' }, global: { plugins: [createPinia(), router] } })
    expect(wrapper.get('[data-group="english"] > button').attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('[data-group="english-phrases"] > button').attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('a[href="/student/phrase-status"]').classes()).toContain('is-active')
    await wrapper.get('[data-group="english-phrases"] > button').trigger('click')
    expect(wrapper.get('#menu-english-phrases').attributes('hidden')).toBeDefined()
    await router.push('/student/word')
    expect(wrapper.get('[data-group="english-words"] > button').attributes('aria-expanded')).toBe('true')
    wrapper.unmount()
  })
})
