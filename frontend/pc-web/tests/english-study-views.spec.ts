import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import EnglishStudyView from '@/views/english/study/EnglishStudyView.vue'
import TestRunner from '@/views/english/study/TestRunner.vue'
import { useEnglishStudyStore } from '@/features/english-study/store'

beforeEach(() => setActivePinia(createPinia()))
describe('英語勉強の画面', () => {
  it('レベル切り替えと検索を行い、熟語ページに単語を混ぜない', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: EnglishStudyView }] })
    await router.push('/student/phrase')
    const wrapper = mount(EnglishStudyView, { props: { kind: 'phrase', mode: 'manage' }, global: { plugins: [router] } })
    expect(wrapper.text()).toContain('look for')
    await wrapper.get('[data-tab="intermediate"]').trigger('click')
    expect(wrapper.text()).toContain('take advantage of')
    expect(wrapper.text()).not.toContain('look for')
    await wrapper.get('[data-keyword]').setValue('put up')
    await wrapper.get('[data-search]').trigger('submit')
    expect(wrapper.text()).toContain('put up with')
    expect(wrapper.text()).not.toContain('come up with')
    await wrapper.get('[data-reset]').trigger('click')
    expect(wrapper.text()).toContain('come up with')
    wrapper.unmount()
  })
  it.each(['word', 'phrase'] as const)('%s の文脈問題は日本語の意味を選んで回答する', async kind => {
    const store = useEnglishStudyStore()
    const word = store.select(kind, 'intermediate')[0]
    const test = store.createTest({ kind, level: 'intermediate', type: 'E', wordIds: [word.id] })
    const wrapper = mount(TestRunner, { props: { test }, global: { stubs: { Teleport: true } } })
    expect(wrapper.get('.eng-context').text()).toBe(word.example)
    expect(wrapper.get('.eng-question').text()).not.toContain(word.translation)
    await wrapper.findAll('.eng-choices button').find(button => button.text() === word.japanese)!.trigger('click')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('.eng-feedback').text()).toContain('正解です')
    await wrapper.get('.eng-feedback button').trigger('click')
    expect(wrapper.get('.eng-result-score').text()).toBe('100%')
    wrapper.unmount()
  })
})
